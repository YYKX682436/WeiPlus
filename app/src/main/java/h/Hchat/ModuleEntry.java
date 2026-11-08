package h.Hchat;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import h.Hchat.dexkit.DexBridgeHolder;
import h.Hchat.dexkit.DexFinder;
import h.Hchat.event.EventBus;
import h.Hchat.hooks.api.media.WeChatImageApi;
import h.Hchat.hooks.api.model.WeChatVersionInfo;
import h.Hchat.hooks.api.runtime.DexArchiveIdentity;
import h.Hchat.hooks.api.runtime.WeChatVersionApi;
import h.Hchat.hooks.api.runtime.HostRuntimeIdentityDecision;
import h.Hchat.hooks.api.runtime.HostApkPathDecision;
import h.Hchat.hooks.core.DexInstallScheduler;
import h.Hchat.hooks.core.FeatureContext;
import h.Hchat.hooks.core.FeatureManager;
import h.Hchat.hooks.core.FeatureRegistry;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.hooks.core.ModuleStartupState;
import h.Hchat.hooks.core.StartupEntryDispatch;
import h.Hchat.hooks.core.XposedRuntime;
import h.Hchat.hooks.items.conversationtabs.ConversationTabsFeature;
import h.Hchat.hooks.items.custombottombar.CustomBottomBarFeature;
import h.Hchat.hooks.items.fakelocation.FakeLocationFeature;
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutRuntime;
import h.Hchat.hooks.items.hotupdate.DisableHotUpdateFeature;
import h.Hchat.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibFeature;
import h.Hchat.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsFeature;
import h.Hchat.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsFeature;
import h.Hchat.hooks.items.batch.BatchTaskManager;
import h.Hchat.hooks.items.tablet.WeChatTabletFeature;
import h.Hchat.loader.utils.NativeLibraryLoader;
import h.Hchat.loader.utils.SilkLibrary;
import h.Hchat.preferences.ConfigStore;
import h.Hchat.ui.UIRegistry;
import h.Hchat.ui.miuix.FirstUseAgreementRuntime;
import h.Hchat.utils.HLog;
import h.Hchat.utils.KavaReflector;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.luckypray.dexkit.DexKitBridge;

/**
 * 模块入口。
 *
 * <p>启动流程： 1. onPackageReady → 过滤目标包 + 目标进程 2. 小程序子进程安装必要的缓存 Hook 和原生小程序功能 Hook 3. 主进程在 Tinker
 * attach 后优先安装自定义底栏 Hook 4. Application.onCreate 后异步初始化完整模块
 *
 * <p>进程过滤：普通功能只在微信主进程加载；:appbrand* 使用独立的轻量运行时。
 */
public class ModuleEntry extends XposedModule {
  private static final String TAG = "[Hchat:Entry]";
  private static final Object DEXKIT_CREATE_LOCK = new Object();
  private static final Map<String, DexKitBridge> DEXKIT_BRIDGES = new ConcurrentHashMap<>();

  // 全局基础设施实例（模块生命周期内单例）
  private EventBus eventBus;
  private UIRegistry uiRegistry;
  private String processName;
  private boolean packageReady;
  private final HostRuntimeIdentityDecision runtimeIdentity = new HostRuntimeIdentityDecision();
  private final Object runtimeVersionApiLock = new Object();
  private WeChatVersionApi runtimeVersionApi;
  private String runtimeVersionApiPackage = "";
  private String runtimeVersionApiLoader = "";
  private long lastTinkerIdentityRefreshAt;
  private static final long TINKER_IDENTITY_REFRESH_COOLDOWN_MS = 3000L;
  private volatile boolean hostIdentityInvalidated;
  private final java.util.concurrent.atomic.AtomicBoolean appBrandApplicationReady =
      new java.util.concurrent.atomic.AtomicBoolean();
  private final ModuleStartupState startupState = new ModuleStartupState();

  private FeatureManager featureManager;
  private FeatureContext featureContext;

  @Override
  public void onModuleLoaded(ModuleLoadedParam param) {
    XposedRuntime.initialize(this);
    processName = param.getProcessName();
    eventBus = EventBus.get();
    uiRegistry = UIRegistry.get();
    HLog.i(
        TAG
            + " module_loaded api="
            + getApiVersion()
            + " process="
            + processName
            + " framework="
            + getFrameworkName()
            + " "
            + getFrameworkVersion());
  }

  @Override
  public synchronized void onPackageReady(PackageReadyParam param) {
    String host = param.getPackageName();
    if (!WechatPackage.isModuleEntryScope(host, processName)) return;
    boolean prewarm = !packageReady;
    packageReady = true;
    final boolean[] installed = {false};
    StartupEntryDispatch.installBeforeScheduling(
        () -> {
          try {
            installed[0] = installPackageHooks(param);
          } catch (Throwable failure) {
            HLog.e(TAG + " package_ready Hook 安装失败: " + host, failure);
          }
        },
        () -> {
          if (prewarm) {
            try {
              scheduleArchiveIdentityPrewarm(param);
            } catch (Throwable failure) {
              HLog.e(TAG + " APK身份后台预热调度失败", failure);
            }
          }
        });
    if (!installed[0]) schedulePackageHooks(param);
  }

  /** Queue the process-local ZIP metadata walk before Application.onCreate identity approval. */
  private void scheduleArchiveIdentityPrewarm(PackageReadyParam param) {
    ApplicationInfo appInfo = param != null ? param.getApplicationInfo() : null;
    if (appInfo == null || appInfo.sourceDir == null || appInfo.sourceDir.isEmpty()) return;
    DexInstallScheduler.scheduleTask(
        "runtime_identity:apk_archive_prewarm",
        "APK归档身份预热",
        DexInstallScheduler.Stage.EARLY,
        Integer.MIN_VALUE,
        () -> {
          String identity = DexArchiveIdentity.forApplication(appInfo);
          if (identity.isEmpty()) {
            HLog.e(TAG + " APK归档身份后台预热未完成，按有限策略重试");
            return false;
          }
          HLog.i(TAG + " APK归档身份预热完成 source=" + appInfo.sourceDir);
          return true;
        });
  }

  private void schedulePackageHooks(PackageReadyParam param) {
    DexInstallScheduler.scheduleTask(
        "entry:package_hooks:" + processName,
        "入口 Hook 初始化",
        DexInstallScheduler.Stage.EARLY,
        Integer.MIN_VALUE + 10,
        () -> {
          try {
            return installPackageHooks(param);
          } catch (Throwable failure) {
            throw new IllegalStateException("入口 Hook 初始化暂时失败", failure);
          }
        });
  }

  private boolean verifyRuntimeIdentity(Context context, ClassLoader loader, PackageReadyParam param,
                                       String source) {
    if (hostIdentityInvalidated || context == null || param == null
        || !WechatPackage.isTarget(param.getPackageName())
        || !param.getPackageName().equals(context.getPackageName())) return false;
    try {
      PackageManager pm = context.getPackageManager();
      PackageInfo packageInfo = pm.getPackageInfo(param.getPackageName(), 0);
      ApplicationInfo appInfo = packageInfo != null ? packageInfo.applicationInfo : null;
      if (appInfo == null || !param.getPackageName().equals(appInfo.packageName)) {
        HLog.e(TAG + " host_identity=temporarily_incomplete reason=package_metadata_missing source=" + source);
        return false;
      }
      ApplicationInfo frameworkAppInfo = param.getApplicationInfo();
      String frameworkApkPath = frameworkAppInfo != null ? frameworkAppInfo.sourceDir : "";
      String contextApkPath = "";
      try {
        ApplicationInfo contextAppInfo = context.getApplicationInfo();
        contextApkPath = contextAppInfo != null ? contextAppInfo.sourceDir : "";
      } catch (Throwable ignored) {}
      HostApkPathDecision.Result pathDecision = HostApkPathDecision.compare(
          frameworkApkPath, contextApkPath, appInfo.sourceDir);
      if (pathDecision != HostApkPathDecision.Result.MATCH) {
        HLog.e(TAG + " apk_path_diagnostic package=" + param.getPackageName()
            + " process=" + processName + " framework=" + safePath(frameworkApkPath)
            + " context=" + safePath(contextApkPath)
            + " package_manager=" + safePath(appInfo.sourceDir)
            + " result=" + pathDecision);
      }
      // PackageReadyParam is the framework's loaded-generation path. Use that APK identity if
      // PackageManager has already switched to a new install path while this process is attached.
      ApplicationInfo loadedAppInfo = frameworkAppInfo != null && frameworkAppInfo.sourceDir != null
          && !frameworkAppInfo.sourceDir.isEmpty() ? frameworkAppInfo : appInfo;
      PackageInfo loadedVersionInfo = packageInfo;
      if (pathDecision != HostApkPathDecision.Result.MATCH) {
        try {
          PackageInfo archiveInfo = pm.getPackageArchiveInfo(loadedAppInfo.sourceDir, 0);
          if (archiveInfo != null && param.getPackageName().equals(archiveInfo.packageName)) {
            loadedVersionInfo = archiveInfo;
          } else {
            HLog.e(TAG + " apk_version_diagnostic loaded_archive_metadata_unavailable source="
                + safePath(loadedAppInfo.sourceDir));
          }
        } catch (Throwable unavailable) {
          HLog.e(TAG + " apk_version_diagnostic loaded_archive_metadata_unavailable source="
              + safePath(loadedAppInfo.sourceDir), unavailable);
        }
      }
      ClassLoader runtimeLoader = loader != null ? loader : param.getClassLoader();
      WeChatVersionInfo current = currentRuntimeVersionInfo(
          context, param.getPackageName(), loadedVersionInfo, loadedAppInfo, runtimeLoader, source);
      if (!WeChatVersionApi.hasCompleteRuntimeIdentity(current)) {
        HLog.e(TAG + " host_identity=temporarily_incomplete source=" + source
            + " runtime_code=" + current.runtimeCodeIdentity
            + " apk_source=" + safePath(appInfo.sourceDir));
        return false;
      }
      String immutableIdentity = WeChatVersionApi.hostIdentityKey(current);
      HostRuntimeIdentityDecision.Result identity;
      if (!runtimeIdentity.isApproved()) {
        if (!runtimeIdentity.approve(immutableIdentity, current.runtimeCodeIdentity,
            current.classLoaderHash)) {
          HLog.e(TAG + " host_identity=temporarily_incomplete source=" + source);
          return false;
        }
        HLog.i(TAG + " host_identity=approved package=" + current.packageName
            + " version=" + current.versionName + " process=" + processName
            + " source=" + source);
        return true;
      }
      identity = runtimeIdentity.verify(
          immutableIdentity, current.runtimeCodeIdentity, current.classLoaderHash,
          () -> invalidateHostIdentity(source + ":runtime_identity_changed"));
      if (identity == HostRuntimeIdentityDecision.Result.HOST_IDENTITY_CHANGED) {
        invalidateHostIdentity(source + ":runtime_identity_changed");
        return false;
      }
      if (identity == HostRuntimeIdentityDecision.Result.INCOMPLETE) {
        HLog.e(TAG + " host_identity=temporarily_incomplete source=" + source);
        return false;
      }
      if (identity == HostRuntimeIdentityDecision.Result.ALREADY_INVALIDATED) return false;
      if (identity == HostRuntimeIdentityDecision.Result.CLASS_LOADER_GENERATION_CHANGED) {
        HLog.i(TAG + " host_identity=classloader_generation_accepted source=" + source);
        if (startupState.isInitializationSucceeded()) {
          scheduleRuntimeRevalidation(context, param, runtimeLoader, current.runtimeCodeIdentity, source);
        }
      }
      if (identity == HostRuntimeIdentityDecision.Result.RUNTIME_CODE_CHANGED) {
        HLog.i(TAG + " host_identity=runtime_code_changed targeted_revalidation=scheduled source=" + source);
        if (startupState.isInitializationSucceeded()) {
          scheduleRuntimeRevalidation(context, param, runtimeLoader, current.runtimeCodeIdentity, source);
        }
      }
      return true;
    } catch (Throwable failure) {
      HLog.e(TAG + " host_identity=temporarily_unavailable source=" + source, failure);
      return false;
    }
  }

  private void scheduleRuntimeRevalidation(Context context, PackageReadyParam param,
      ClassLoader classLoader, String codeIdentity, String source) {
    if (context == null || param == null || classLoader == null || featureManager == null) return;
    String taskKey = "module:runtime_code:" + Integer.toHexString(codeIdentity.hashCode())
        + ":" + codeIdentity.length();
    DexInstallScheduler.scheduleTask(taskKey, "Tinker 运行代码定位/Hook 复核",
        DexInstallScheduler.Stage.EARLY, Integer.MIN_VALUE + 20, () -> {
          if (hostIdentityInvalidated) return true;
          return revalidateRuntimeCode(context, param, classLoader, source);
        });
  }

  private boolean revalidateRuntimeCode(Context context, PackageReadyParam param,
      ClassLoader classLoader, String source) {
    try {
      NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
      nativeLibraryLoader.loadDexKit(context, getClass().getClassLoader());
      String apkPath = resolveCurrentHostApkPath(context, param);
      DexKitBridge dexKit = createDexKitBridge(classLoader, apkPath);
      DexFinder finder = new DexFinder(dexKit, classLoader, context);
      DexBridgeHolder holder = new DexBridgeHolder(dexKit, finder, classLoader, apkPath);
      FeatureContext old = featureContext;
      ConfigStore configStore = old != null ? old.configStore() : new ConfigStore(context);
      Context moduleContext = old != null ? old.moduleContext() : createModuleContext(context);
      FeatureContext refreshed = new FeatureContext(context, moduleContext != null ? moduleContext : context,
          classLoader, dexKit, finder, eventBus, configStore, holder, uiRegistry);
      boolean ready = featureManager != null
          && featureManager.revalidateRuntime(refreshed, classLoader, true);
      if (ready) {
        featureContext = refreshed;
        installMsgDescTextFallback(finder);
        HLog.i(TAG + " runtime_code_revalidation=complete source=" + source);
      } else {
        HLog.e(TAG + " runtime_code_revalidation=pending source=" + source);
      }
      return ready;
    } catch (Throwable failure) {
      HLog.e(TAG + " runtime_code_revalidation=failed source=" + source, failure);
      return false;
    }
  }

  private WeChatVersionInfo currentRuntimeVersionInfo(Context context, String packageName,
      PackageInfo packageInfo, ApplicationInfo appInfo, ClassLoader loader, String source) {
    String loaderIdentity = WeChatVersionApi.classLoaderIdentity(loader);
    synchronized (runtimeVersionApiLock) {
      if (runtimeVersionApi == null || !runtimeVersionApiPackage.equals(packageName)
          || !runtimeVersionApiLoader.equals(loaderIdentity)) {
        runtimeVersionApi = new WeChatVersionApi(context, loader, null);
        runtimeVersionApiPackage = packageName;
        runtimeVersionApiLoader = loaderIdentity;
        lastTinkerIdentityRefreshAt = 0L;
        return runtimeVersionApi.current(packageInfo, appInfo);
      }
      long now = System.currentTimeMillis();
      boolean tinkerTransitionComplete = "tinker_attach_after".equals(source)
          || source.endsWith("_tinker_attach_after")
          || "appbrand_tinker_attach".equals(source);
      if (tinkerTransitionComplete
          && now - lastTinkerIdentityRefreshAt >= TINKER_IDENTITY_REFRESH_COOLDOWN_MS) {
        lastTinkerIdentityRefreshAt = now;
        return runtimeVersionApi.refresh(packageInfo, appInfo);
      }
      return runtimeVersionApi.current(packageInfo, appInfo);
    }
  }

  private void invalidateHostIdentity(String reason) {
    if (hostIdentityInvalidated) return;
    hostIdentityInvalidated = true;
    runtimeIdentity.invalidate();
    BatchTaskManager.shutdownProcessManagerForHostInvalidation();
    HLog.e(TAG + " host_identity=invalidated reason=" + reason);
    DexInstallScheduler.INSTANCE.cancelAll();
    HookRegistry.get().unhookAll();
  }

  private boolean installPackageHooks(PackageReadyParam lpparam) throws Throwable {
    // 1. 包名过滤
    if (!isWeChatPackage(lpparam.getPackageName())) return true;
    if (!Process.is64Bit()) {
      HLog.e(TAG + " 当前版本仅支持 ARM64 微信进程，跳过32位进程: " + processName);
      return true;
    }

    // 热更新加载发生在 Tinker attach 阶段，必须早于普通功能初始化安装。
    StartupEntryDispatch.installOptional(
        () -> installHotUpdateEarlyHook(lpparam),
        () -> scheduleHotUpdateEntryHookRetry(lpparam));

    // 平板模式需要在微信所有进程保持一致，避免升级/降级后登录态校验看到不同设备形态。
    installTabletEarlyHook(lpparam);

    // 2. 子进程只安装明确需要的最小 Hook。
    if (!isMainProcess(lpparam)) {
      if (isAppBrandProcess(lpparam)) {
        installAppBrandProcessHook(lpparam);
      }
      return true;
    }

    // Register the one-shot Application callback before unrelated best-effort hooks. A failure
    // in one optional entry must not consume the only synchronous registration opportunity.
    boolean applicationEntryInstalled = installApplicationOnCreateHook(lpparam);
    if (!applicationEntryInstalled) {
      // Retry once on this callback thread in case the framework rejected a transient request.
      applicationEntryInstalled = installApplicationOnCreateHook(lpparam);
    }

    // 3. Mars CDN 管理器可能早于模块 API 初始化创建，先按稳定类名捕获实例。
    WeChatImageApi.installMarsCdnManagerHook(
        lpparam.getClassLoader(), message -> HLog.e("[Hchat:WechatApi] " + message));

    // 底栏在 LauncherUI 首帧前创建，必须早于完整 DexKit 初始化安装 Hook。
    installCustomBottomBarEarlyHook(lpparam);

    return applicationEntryInstalled;
  }

  private boolean installApplicationOnCreateHook(PackageReadyParam lpparam) {
    try {
      hookEntryOnce(
        "application-on-create",
        lpparam,
        Application.class,
        null,
        null,
        "onCreate",
        new Class<?>[0],
        chain -> {
          Object result = chain.proceed();
          Application app = (Application) chain.getThisObject();
          if (!lpparam.getPackageName().equals(app.getPackageName())) return result;
          startupState.markApplicationReady();
          if (!verifyRuntimeIdentity(app, app.getClassLoader(), lpparam,
              "application_on_create")) {
            scheduleApplicationEarlyRetry(app, lpparam, "application_on_create_identity_retry");
            scheduleModuleInitialization(app, lpparam, "application_on_create_identity_retry");
            return result;
          }
          if (!installApplicationEarlyFeatures(app, lpparam)) {
            scheduleApplicationEarlyRetry(app, lpparam, "application_on_create_early_feature_retry");
          }
          scheduleModuleInitialization(app, lpparam, "application_on_create");

          return result;
        });
      return true;
    } catch (Throwable failure) {
      HLog.e(TAG + " Application.onCreate 入口 Hook 安装失败，保留同步重试", failure);
      return false;
    }
  }

  private void scheduleModuleInitialization(Context hostContext, PackageReadyParam param, String source) {
    if (hostIdentityInvalidated || !startupState.shouldScheduleInitialization()) return;
    DexInstallScheduler.scheduleTask(
        "module:init:" + processName,
        "微信模块初始化",
        DexInstallScheduler.Stage.EARLY,
        Integer.MIN_VALUE + 30,
        () -> {
          if (hostIdentityInvalidated) return true;
          if (!startupState.isApplicationReady()) return false;
          ClassLoader loader = resolveHostClassLoader(hostContext, param);
          if (!verifyRuntimeIdentity(hostContext, loader, param, "module_init:" + source)) {
            return hostIdentityInvalidated;
          }
          if (!startupState.beginInitializationAttempt()) {
            return startupState.isInitializationSucceeded();
          }
          boolean initialized = false;
          try {
            installApplicationEarlyFeatures(hostContext, param);
            initialized = initModule(hostContext, param);
            return initialized;
          } finally {
            startupState.finishInitializationAttempt(initialized);
          }
        });
  }

  private boolean installApplicationEarlyFeatures(Context context, PackageReadyParam param) {
    boolean complete = true;
    ClassLoader loader = resolveHostClassLoader(context, param);
    Application application = context instanceof Application
        ? (Application) context : (Application) context.getApplicationContext();
    if (application == null) return false;
    try {
      complete &= startupState.installEarlyEntryOnce("first_use_agreement",
          () -> FirstUseAgreementRuntime.install(application));
    } catch (Throwable failure) {
      HLog.e(TAG + " 首次协议运行时安装失败", failure);
      complete = false;
    }
    try {
      complete &= startupState.installEarlyEntryOnce("conversation_tabs",
          () -> ConversationTabsFeature.installEarly(loader));
    } catch (Throwable failure) {
      HLog.e(TAG + " 标签分组早期安装失败", failure);
      complete = false;
    }
    try {
      complete &= startupState.installEarlyEntryOnce("floating_shortcut",
          () -> FloatingShortcutRuntime.INSTANCE.install(context));
    } catch (Throwable failure) {
      HLog.e(TAG + " 悬浮快捷菜单早期安装失败", failure);
      complete = false;
    }
    try {
      complete &= startupState.installEarlyEntryOnce("custom_bottom_bar",
          () -> installCustomBottomBarEarly(context, loader));
    } catch (Throwable failure) {
      HLog.e(TAG + " 自定义底栏早期Hook失败", failure);
      complete = false;
    }
    return complete;
  }

  private void scheduleApplicationEarlyRetry(Context context, PackageReadyParam param, String source) {
    DexInstallScheduler.scheduleTask("module:app_early:" + processName,
        "Application 早期入口补装", DexInstallScheduler.Stage.EARLY,
        Integer.MIN_VALUE + 25, () -> {
          if (hostIdentityInvalidated) return true;
          if (!startupState.isApplicationReady()) return false;
          ClassLoader loader = resolveHostClassLoader(context, param);
          if (!verifyRuntimeIdentity(context, loader, param, "app_early:" + source)) return false;
          return installApplicationEarlyFeatures(context, param);
        });
  }

  // ============ 包名与进程判断 ============

  /** 判断是否为目标微信包。 只允许官方微信与明确支持的修改版包名。 */
  private boolean isWeChatPackage(String packageName) {
    return WechatPackage.isTarget(packageName);
  }

  /** 判断是否为主进程。 微信主进程的 processName 等于 packageName。 */
  private boolean isMainProcess(PackageReadyParam lpparam) {
    return WechatPackage.isMainProcess(lpparam.getPackageName(), processName);
  }

  private boolean isAppBrandProcess(PackageReadyParam lpparam) {
    return WechatPackage.isAppBrandProcess(lpparam.getPackageName(), processName);
  }

  private void installAppBrandProcessHook(PackageReadyParam lpparam) {
    try {
      hookEntryOnce(
          "appbrand-tinker-attach",
          lpparam,
          null,
          lpparam.getClassLoader(),
          "com.tencent.tinker.loader.app.TinkerApplication",
          "onBaseContextAttached",
          new Class<?>[] {Context.class, long.class, long.class},
          chain -> {
            Object result = chain.proceed();
            Context context = (Context) chain.getArg(0);
            ClassLoader loader = resolveTinkerClassLoader(chain.getThisObject());
            if (loader == null) {
              loader = resolveHostClassLoader(context, lpparam);
            }
            if (!verifyRuntimeIdentity(context, loader, lpparam, "appbrand_tinker_attach")) {
              return result;
            }
            installAppBrandProcessHook(context, loader, lpparam, false);

            return result;
          });
    } catch (Throwable e) {
      HLog.e(TAG + " 小程序进程早期入口安装失败: " + e.getMessage(), e);
    }
    try {
      hookEntryOnce(
          "appbrand-application-on-create",
          lpparam,
          Application.class,
          null,
          null,
          "onCreate",
          new Class<?>[0],
          chain -> {
            Object result = chain.proceed();
            Application app = (Application) chain.getThisObject();
            if (!lpparam.getPackageName().equals(app.getPackageName())
                || !verifyRuntimeIdentity(app, app.getClassLoader(), lpparam,
                    "appbrand_application_on_create")
                || !appBrandApplicationReady.compareAndSet(false, true)) return result;
            ClassLoader loader = resolveHostClassLoader(app, lpparam);
            installAppBrandProcessHook(app, loader, lpparam, true);

            return result;
          });
    } catch (Throwable e) {
      HLog.e(TAG + " 小程序进程兜底入口安装失败: " + e.getMessage(), e);
    }
  }

  private void installAppBrandProcessHook(
      Context context,
      ClassLoader classLoader,
      PackageReadyParam lpparam,
      boolean applicationReady) {
    if (!verifyRuntimeIdentity(context, classLoader, lpparam, "appbrand_hook_install")) return;
    boolean failed = false;
    if (FakeMiniProgramBaseLibFeature.isEnabled(context)
        && !FakeMiniProgramBaseLibFeature.installAppBrandProcessHook(context, classLoader)) {
      failed = true;
    }
    if (SkipMiniProgramVideoAdsFeature.isEnabled(context)
        && !SkipMiniProgramVideoAdsFeature.install(context, classLoader)) {
      failed = true;
    }
    if (SkipGlobalMiniProgramSplashAdsFeature.isEnabled(context)) {
      SkipGlobalMiniProgramSplashAdsFeature.scheduleAppBrandProcessHook(context, classLoader);
    }
    if (FakeLocationFeature.isEnabled(context)
        && !FakeLocationFeature.installAppBrandProcessHook(context, classLoader)) {
      failed = true;
    }
    if (failed && applicationReady) {
      HLog.e(TAG + " 小程序进程Hook安装失败: " + processName);
    }
  }

  // ============ 模块初始化 ============

  private boolean installHotUpdateEarlyHook(PackageReadyParam lpparam) {
    try {
      hookEntryOnce(
          "hot-update-tinker-attach",
          lpparam,
          null,
          lpparam.getClassLoader(),
          "com.tencent.tinker.loader.app.TinkerApplication",
          "onBaseContextAttached",
          new Class<?>[] {Context.class, long.class, long.class},
          chain -> {
            if (hostIdentityInvalidated) return chain.proceed();
            Context context = (Context) chain.getArg(0);
            // The package/process scope is already verified. Install the pre-attach blocker before
            // identity metadata can settle so a transient read cannot miss the early Tinker gate.
            installHotUpdateForAttach(lpparam, chain, "before");
            verifyRuntimeIdentity(context, lpparam.getClassLoader(), lpparam, "tinker_attach_before");
            Object result = chain.proceed();
            ClassLoader runtimeLoader = resolveTinkerClassLoader(chain.getThisObject());
            if (runtimeLoader == null) runtimeLoader = resolveHostClassLoader(context, lpparam);
            if (verifyRuntimeIdentity(context, runtimeLoader, lpparam, "tinker_attach_after")) {
              installHotUpdateForAttach(lpparam, chain, "after");
              if (startupState.shouldScheduleInitialization() && isMainProcess(lpparam)) {
                scheduleModuleInitialization(context, lpparam, "tinker_attach_after");
              }
            }

            return result;
          });
      return true;
    } catch (Throwable failure) {
      HLog.e(TAG + " 热更新早期入口 Hook 安装失败，保留独立补装: " + failure.getMessage(), failure);
      return false;
    }
  }

  private void scheduleHotUpdateEntryHookRetry(PackageReadyParam param) {
    DexInstallScheduler.scheduleTask("hotupdate:attach_entry:" + processName,
        "Tinker attach 入口 Hook 补装", DexInstallScheduler.Stage.EARLY,
        Integer.MIN_VALUE + 3, () -> installHotUpdateEarlyHook(param));
  }

  private void installHotUpdateForAttach(
      PackageReadyParam lpparam, XposedInterface.Chain chain, String stage) {
    try {
      Context context = (Context) chain.getArg(0);
      if (!DisableHotUpdateFeature.isEnabled(context)) return;
      Object tinkerApplication = chain.getThisObject();
      ClassLoader hostClassLoader = "before".equals(stage)
          ? lpparam.getClassLoader() : resolveTinkerClassLoader(tinkerApplication);
      if (hostClassLoader == null) hostClassLoader = lpparam.getClassLoader();
      final ClassLoader targetLoader = hostClassLoader;
      if ("before".equals(stage)) {
        if (!DisableHotUpdateFeature.installEarly(context, targetLoader)) {
          scheduleHotUpdateEarlyRetry(context, targetLoader);
        }
        return;
      }
      String loaderToken = WeChatVersionApi.classLoaderIdentity(targetLoader);
      String key = "hotupdate:late:" + processName + ":" + loaderToken;
      DexInstallScheduler.scheduleTask(key, "热更新后置 Hook 安装",
          DexInstallScheduler.Stage.EARLY, Integer.MIN_VALUE + 5, () -> {
            if (hostIdentityInvalidated || !DisableHotUpdateFeature.isEnabled(context)) return true;
            ClassLoader activeLoader = resolveTinkerClassLoader(tinkerApplication);
            if (activeLoader != null && activeLoader != targetLoader) {
              HLog.i(TAG + " hotupdate_late_install=stale_loader_skipped");
              return true;
            }
            NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
            nativeLibraryLoader.loadDexKit(context, ModuleEntry.this.getClass().getClassLoader());
            String apkPath = resolveCurrentHostApkPath(context, lpparam);
            DexKitBridge dexKit = createDexKitBridge(targetLoader, apkPath);
            return DisableHotUpdateFeature.install(context, targetLoader, dexKit);
          });
    } catch (Throwable e) {
      HLog.e(TAG + " 热更新早期Hook失败(" + stage + "): " + e.getMessage(), e);
    }
  }

  private void scheduleHotUpdateEarlyRetry(Context context, ClassLoader classLoader) {
    String loaderToken = WeChatVersionApi.classLoaderIdentity(classLoader);
    DexInstallScheduler.scheduleTask("hotupdate:early:" + processName + ":" + loaderToken,
        "热更新早期 Hook 补装", DexInstallScheduler.Stage.EARLY, Integer.MIN_VALUE + 4,
        () -> DisableHotUpdateFeature.installEarly(context, classLoader));
  }

  private void installTabletEarlyHook(PackageReadyParam lpparam) {
    try {
      hookEntryOnce(
          "tablet-tinker-attach",
          lpparam,
          null,
          lpparam.getClassLoader(),
          "com.tencent.tinker.loader.app.TinkerApplication",
          "onBaseContextAttached",
          new Class<?>[] {Context.class, long.class, long.class},
          chain -> {
            installTabletHookForAttach(lpparam, chain, "before");

            Object result = chain.proceed();
            installTabletHookForAttach(lpparam, chain, "after");

            return result;
          });
    } catch (Throwable e) {
      HLog.e(TAG + " 平板模式早期入口安装失败: " + e.getMessage(), e);
    }
  }

  private void installCustomBottomBarEarlyHook(PackageReadyParam lpparam) {
    try {
      hookEntryOnce(
          "custom-bottom-bar-tinker-attach",
          lpparam,
          null,
          lpparam.getClassLoader(),
          "com.tencent.tinker.loader.app.TinkerApplication",
          "onBaseContextAttached",
          new Class<?>[] {Context.class, long.class, long.class},
          chain -> {
            Object result = chain.proceed();
            Context context = (Context) chain.getArg(0);
            ClassLoader classLoader = resolveTinkerClassLoader(chain.getThisObject());
            if (classLoader == null) {
              classLoader = resolveHostClassLoader(context, lpparam);
            }
            if (!verifyRuntimeIdentity(context, classLoader, lpparam,
                "custom_bottom_bar_tinker_attach")) return result;
            installCustomBottomBarEarly(context, classLoader);

            return result;
          });
    } catch (Throwable e) {
      HLog.e(TAG + " 自定义底栏早期入口安装失败: " + e.getMessage(), e);
    }
  }

  private boolean installCustomBottomBarEarly(Context context, ClassLoader classLoader) {
    if (hostIdentityInvalidated) return false;
    try {
      return CustomBottomBarFeature.installEarly(context, classLoader);
    } catch (Throwable e) {
      HLog.e(TAG + " 自定义底栏早期Hook失败: " + e.getMessage(), e);
      return false;
    }
  }

  private void installTabletHookForAttach(
      PackageReadyParam lpparam, XposedInterface.Chain chain, String stage) {
    try {
      if (hostIdentityInvalidated) return;
      Context context = (Context) chain.getArg(0);
      ClassLoader runtimeLoader = resolveTinkerClassLoader(chain.getThisObject());
      if (runtimeLoader == null) runtimeLoader = resolveHostClassLoader(context, lpparam);
      if (!verifyRuntimeIdentity(context, runtimeLoader, lpparam,
          "tablet_tinker_attach_" + stage)) return;
      if (!WeChatTabletFeature.isEnabled(context)) return;
      ClassLoader resolvedClassLoader = runtimeLoader;
      if (resolvedClassLoader == null) {
        resolvedClassLoader = resolveHostClassLoader(context, lpparam);
      }
      final ClassLoader hostClassLoader = resolvedClassLoader;
      // 缓存安装不使用 DexKit，避免主线程等待后台预热持有的串行门。
      if (WeChatTabletFeature.installCached(context, hostClassLoader)) return;
      DexInstallScheduler.runDexKitTask(
          () -> {
            if (WeChatTabletFeature.installCached(context, hostClassLoader)) return;
            if (!"after".equals(stage)) {
              if (!DisableHotUpdateFeature.isEnabled(context)) return;
              NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
              nativeLibraryLoader.loadDexKit(context, ModuleEntry.this.getClass().getClassLoader());
              String apkPath = resolveCurrentHostApkPath(context, lpparam);
              DexKitBridge dexKit =
                  createDexKitBridge(lpparam.getClassLoader(), apkPath);
              WeChatTabletFeature.install(context, lpparam.getClassLoader(), dexKit);
              return;
            }
            NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
            nativeLibraryLoader.loadDexKit(context, ModuleEntry.this.getClass().getClassLoader());
            String apkPath = resolveCurrentHostApkPath(context, lpparam);
            DexKitBridge dexKit =
                createDexKitBridge(hostClassLoader, apkPath);
            WeChatTabletFeature.install(context, hostClassLoader, dexKit);
          });
    } catch (Throwable e) {
      HLog.e(TAG + " 平板模式早期Hook失败(" + stage + "): " + e.getMessage(), e);
    }
  }

  private ClassLoader resolveTinkerClassLoader(Object tinkerApplication) {
    try {
      Object value = KavaReflector.invokeMethod(tinkerApplication, "getClassLoader");
      return value instanceof ClassLoader ? (ClassLoader) value : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private boolean initModule(Context hostContext, PackageReadyParam lpparam) {
    try {
      // 1. 加载 Native 库（DexKit）
      NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
      nativeLibraryLoader.loadDexKit(hostContext, getClass().getClassLoader());
      SilkLibrary.initialize(hostContext);

      // 2. 创建模块自身 Context
      boolean[] initialized = {false};
      DexInstallScheduler.runDexKitTask(
          () -> {
            // 3. 初始化 DexKit。优先使用 Tinker 之后的运行时 ClassLoader。
            ClassLoader hostClassLoader = resolveHostClassLoader(hostContext, lpparam);
            String apkPath;
            try {
              apkPath = resolveCurrentHostApkPath(hostContext, lpparam);
            } catch (Exception failure) {
              throw new IllegalStateException("微信 APK 路径在初始化期间暂不一致", failure);
            }
            DexKitBridge dexKit = createDexKitBridge(hostClassLoader, apkPath);
            DexFinder finder = new DexFinder(dexKit, hostClassLoader, hostContext);
            WeChatImageApi.installMarsCdnManagerHook(
                finder.marsCdnManagerClass, message -> HLog.e("[Hchat:WechatApi] " + message));

            // 4. 创建共享 DexKit 服务
            DexBridgeHolder dexBridgeHolder =
                new DexBridgeHolder(dexKit, finder, hostClassLoader, apkPath);

            // 5. 创建统一上下文
            FeatureContext previousContext = featureContext;
            Context moduleContext = previousContext != null
                ? previousContext.moduleContext() : createModuleContext(hostContext);
            ConfigStore configStore = previousContext != null
                ? previousContext.configStore() : new ConfigStore(hostContext);
            featureContext =
                new FeatureContext(
                    hostContext,
                    moduleContext != null ? moduleContext : hostContext,
                    hostClassLoader,
                    dexKit,
                    finder,
                    eventBus,
                    configStore,
                    dexBridgeHolder,
                    uiRegistry);

            // 6. 注册并安装所有功能模块
            if (featureManager == null) featureManager = FeatureRegistry.createDefaultManager();
            if (!featureManager.installAll(featureContext)) {
              throw new IllegalStateException("核心功能初始化未完成，等待异步重试");
            }

            // 7. 防崩兜底：微信 8.0.76 消息描述文本方法对 null 调 isEmpty() 崩溃（收到特殊小程序电商卡片消息触发）
            installMsgDescTextFallback(finder);
            initialized[0] = true;
          });
      return initialized[0];
    } catch (Throwable e) {
      HLog.e(TAG + " 初始化失败: " + e, e);
      return false;
    }
  }

  /**
   * 防崩兜底：微信 8.0.76 的 pt0.r.l(Context, boolean) 存在未初始化局部变量， 收到特殊小程序电商卡片消息（type 44 + ecsInfo）时
   * String.isEmpty() 对 null 调用导致崩溃。 原调用链正常执行一次，仅在 after 中兜底已确认的 String.isEmpty() 空指针。
   */
  private void installMsgDescTextFallback(DexFinder finder) {
    try {
      finder.resolveMsgDescTextApi();
      if (finder.msgDescTextMethods.isEmpty()) {
        HLog.e("[Hchat:WechatApi] 消息描述防崩兜底未定位到方法");
        return;
      }
      for (Method m : finder.msgDescTextMethods) {
        if (m == null) continue;
        try {
          String key = "runtime:msg-desc-fallback:"
              + WeChatVersionApi.classLoaderIdentity(m.getDeclaringClass().getClassLoader())
              + ":" + m.toGenericString();
          HookRegistry.get()
              .hookOnce(
                  key,
                  m,
                  chain -> {
                    try {
                      return chain.proceed();
                    } catch (Throwable failure) {
                      if (h.Hchat.hooks.core.MsgDescFallback.isTargetFailure(m, failure)) return "";
                      throw failure;
                    }
                  });
        } catch (Throwable e) {
          HLog.e("[Hchat:WechatApi] 消息描述防崩兜底安装失败: " + m, e);
        }
      }
    } catch (Throwable e) {
      HLog.e(TAG + " 消息描述防崩兜底安装失败: " + e.getMessage(), e);
    }
  }

  /** 创建模块自身的 Context。 通过 createPackageContext 获取模块自身的 Context， 以便访问模块资源。 */
  private Context createModuleContext(Context hostContext) {
    try {
      return hostContext.createPackageContext(
          "h.Hchat", Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
    } catch (Throwable e) {
      return null;
    }
  }

  private void hookEntryOnce(
      String hookName,
      PackageReadyParam lpparam,
      Class<?> targetClass,
      ClassLoader classLoader,
      String targetClassName,
      String methodName,
      Class<?>[] parameterTypes,
      XposedInterface.Hooker callback)
      throws Throwable {
    Class<?> clazz = targetClass != null ? targetClass : classLoader.loadClass(targetClassName);
    Method method = findMethodInHierarchy(clazz, methodName, parameterTypes);
    method.setAccessible(true);

    String key = "entry:" + hookName + ":" + processName;
    if (classLoader != null) {
      key += ":" + Integer.toHexString(System.identityHashCode(classLoader));
    }
    HookRegistry.get().hookOnce(key, method, callback);
  }

  private Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>[] parameterTypes)
      throws NoSuchMethodException {
    for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
      try {
        return current.getDeclaredMethod(methodName, parameterTypes);
      } catch (NoSuchMethodException ignored) {
      }
    }
    throw new NoSuchMethodException(clazz.getName() + "." + methodName);
  }

  private ClassLoader resolveHostClassLoader(Context hostContext, PackageReadyParam lpparam) {
    ClassLoader lpClassLoader = lpparam.getClassLoader();
    ClassLoader contextClassLoader = null;
    ClassLoader threadClassLoader = null;
    try {
      contextClassLoader = hostContext.getClassLoader();
    } catch (Throwable ignored) {
    }
    try {
      threadClassLoader = Thread.currentThread().getContextClassLoader();
    } catch (Throwable ignored) {
    }

    if (isTinkerClassLoader(contextClassLoader)) return contextClassLoader;
    if (isTinkerClassLoader(threadClassLoader)) return threadClassLoader;
    if (contextClassLoader != null) return contextClassLoader;
    if (threadClassLoader != null) return threadClassLoader;
    return lpClassLoader;
  }

  private DexKitBridge createDexKitBridge(ClassLoader classLoader, String apkPath) {
    synchronized (DEXKIT_CREATE_LOCK) {
      if (apkPath == null || apkPath.isEmpty()) {
        throw new IllegalStateException("缺少已核对的微信 APK 路径");
      }
      String archiveIdentity = DexArchiveIdentity.fingerprint(apkPath);
      if (archiveIdentity.isEmpty()) {
        throw new IllegalStateException("微信 APK 归档身份不可用: " + apkPath);
      }
      String key;
      try {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(archiveIdentity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder token = new StringBuilder(digest.length * 2);
        for (byte value : digest) token.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        key = new java.io.File(apkPath).getCanonicalPath() + "|" + token;
      } catch (Throwable unavailable) {
        key = apkPath + "|" + Integer.toHexString(archiveIdentity.hashCode());
      }
      DexKitBridge cached = DEXKIT_BRIDGES.get(key);
      if (cached != null) return cached;
      // 仅缓存轻量入口；实际查询才加载，连续空闲 30 秒后释放 native 分析资源。
      DexKitBridge bridge = DexKitBridge.createManaged(apkPath, 30_000L);
      DEXKIT_BRIDGES.put(key, bridge);
      return bridge;
    }
  }

  private String resolveCurrentHostApkPath(Context context, PackageReadyParam param) {
    ApplicationInfo frameworkInfo = param != null ? param.getApplicationInfo() : null;
    ApplicationInfo contextInfo = context != null ? context.getApplicationInfo() : null;
    PackageInfo packageInfo;
    try {
      packageInfo = context != null
          ? context.getPackageManager().getPackageInfo(context.getPackageName(), 0) : null;
    } catch (Throwable failure) {
      throw new IllegalStateException("无法读取已安装微信 APK 元数据", failure);
    }
    ApplicationInfo packageManagerInfo = packageInfo != null ? packageInfo.applicationInfo : null;
    String frameworkPath = frameworkInfo != null ? frameworkInfo.sourceDir : "";
    String contextPath = contextInfo != null ? contextInfo.sourceDir : "";
    String packageManagerPath = packageManagerInfo != null ? packageManagerInfo.sourceDir : "";
    HostApkPathDecision.Result pathDecision = HostApkPathDecision.compare(
        frameworkPath, contextPath, packageManagerPath);
    if (pathDecision != HostApkPathDecision.Result.MATCH) {
      HLog.e(TAG + " apk_path_diagnostic upgrade_or_stale_path framework=" + safePath(frameworkPath)
          + " context=" + safePath(contextPath)
          + " package_manager=" + safePath(packageManagerPath)
          + " result=" + pathDecision);
      HLog.i(TAG + " apk_path_source=framework_loaded_generation");
    }
    java.io.File apk = new java.io.File(frameworkPath);
    if (!apk.isFile()) throw new IllegalStateException("微信 APK 路径不可读: " + safePath(frameworkPath));
    try {
      return apk.getCanonicalPath();
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("无法规范化微信 APK 路径: " + safePath(frameworkPath), failure);
    }
  }

  private static String safePath(String path) {
    return path == null ? "<empty>" : path;
  }

  private boolean isTinkerClassLoader(ClassLoader classLoader) {
    if (classLoader == null) return false;
    String text = String.valueOf(classLoader);
    return text.contains("/tinker/") || text.contains("DelegateLastClassLoader");
  }
}
