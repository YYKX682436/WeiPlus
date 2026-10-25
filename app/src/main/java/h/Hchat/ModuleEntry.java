package h.Hchat;

import android.app.Application;
import android.content.Context;
import android.os.Process;
import h.Hchat.dexkit.DexBridgeHolder;
import h.Hchat.dexkit.DexFinder;
import h.Hchat.event.EventBus;
import h.Hchat.hooks.api.media.WeChatImageApi;
import h.Hchat.hooks.core.DexInstallScheduler;
import h.Hchat.hooks.core.FeatureContext;
import h.Hchat.hooks.core.FeatureManager;
import h.Hchat.hooks.core.FeatureRegistry;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.hooks.core.XposedRuntime;
import h.Hchat.hooks.items.conversationtabs.ConversationTabsFeature;
import h.Hchat.hooks.items.custombottombar.CustomBottomBarFeature;
import h.Hchat.hooks.items.fakelocation.FakeLocationFeature;
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutRuntime;
import h.Hchat.hooks.items.hotupdate.DisableHotUpdateFeature;
import h.Hchat.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibFeature;
import h.Hchat.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsFeature;
import h.Hchat.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsFeature;
import h.Hchat.hooks.items.tablet.WeChatTabletFeature;
import h.Hchat.loader.utils.NativeLibraryLoader;
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
  private final java.util.concurrent.atomic.AtomicBoolean applicationReady =
      new java.util.concurrent.atomic.AtomicBoolean();

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
    if (packageReady
        || !isWeChatPackage(host)
        || !(processName.equals(host) || processName.startsWith(host + ":"))) return;
    packageReady = true;
    try {
      installPackageHooks(param);
    } catch (Throwable failure) {
      HLog.e(TAG + " package_ready Hook 安装失败: " + host, failure);
    }
  }

  private void installPackageHooks(PackageReadyParam lpparam) throws Throwable {
    // 1. 包名过滤
    if (!isWeChatPackage(lpparam.getPackageName())) return;
    if (!Process.is64Bit()) {
      HLog.e(TAG + " 当前版本仅支持 ARM64 微信进程，跳过32位进程: " + processName);
      return;
    }

    // 热更新加载发生在 Tinker attach 阶段，必须早于普通功能初始化安装。
    installHotUpdateEarlyHook(lpparam);

    // 平板模式需要在微信所有进程保持一致，避免升级/降级后登录态校验看到不同设备形态。
    installTabletEarlyHook(lpparam);

    // 2. 子进程只安装明确需要的最小 Hook。
    if (!isMainProcess(lpparam)) {
      if (isAppBrandProcess(lpparam)) {
        installAppBrandProcessHook(lpparam);
      }
      return;
    }

    // 3. Mars CDN 管理器可能早于模块 API 初始化创建，先按稳定类名捕获实例。
    WeChatImageApi.installMarsCdnManagerHook(
        lpparam.getClassLoader(), message -> HLog.e("[Hchat:WechatApi] " + message));

    // 底栏在 LauncherUI 首帧前创建，必须早于完整 DexKit 初始化安装 Hook。
    installCustomBottomBarEarlyHook(lpparam);

    // 4. Hook Application.onCreate，等 Application 完全初始化后再启动模块
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
          if (!lpparam.getPackageName().equals(app.getPackageName())
              || !applicationReady.compareAndSet(false, true)) return result;
          try {
            FirstUseAgreementRuntime.install(app);
          } catch (Throwable failure) {
            HLog.e(TAG + " 首次协议运行时安装失败", failure);
          }
          // 标签栏先捕获首页根视图，不能等待后台 DexKit 初始化后再挂载。
          try {
            ConversationTabsFeature.installEarly(resolveHostClassLoader(app, lpparam));
          } catch (Throwable e) {
            HLog.e(TAG + " 标签分组早期安装失败", e);
          }
          // 悬浮入口不依赖 DexKit，先注册生命周期，避免错过首个 Activity。
          try {
            FloatingShortcutRuntime.INSTANCE.install(app);
          } catch (Throwable e) {
            HLog.e(TAG + " 悬浮快捷菜单早期安装失败", e);
          }
          installCustomBottomBarEarly(app, resolveHostClassLoader(app, lpparam));
          new Thread(() -> initModule(app, lpparam), "Hchat-Init").start();

          return result;
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
                || !applicationReady.compareAndSet(false, true)) return result;
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

  private void installHotUpdateEarlyHook(PackageReadyParam lpparam) {
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
            installHotUpdateForAttach(lpparam, chain, "before");

            Object result = chain.proceed();
            installHotUpdateForAttach(lpparam, chain, "after");

            return result;
          });
    } catch (Throwable e) {
      HLog.e(TAG + " 热更新早期入口安装失败: " + e.getMessage(), e);
    }
  }

  private void installHotUpdateForAttach(
      PackageReadyParam lpparam, XposedInterface.Chain chain, String stage) {
    try {
      Context context = (Context) chain.getArg(0);
      if (!DisableHotUpdateFeature.isEnabled(context)) return;
      if ("before".equals(stage)) {
        DisableHotUpdateFeature.installEarly(context, lpparam.getClassLoader());
        return;
      }
      Object tinkerApplication = chain.getThisObject();
      new Thread(
              () -> {
                DexInstallScheduler.runDexKitTask(
                    () -> {
                      try {
                        ClassLoader hostClassLoader = resolveTinkerClassLoader(tinkerApplication);
                        if (hostClassLoader == null) {
                          hostClassLoader = resolveHostClassLoader(context, lpparam);
                        }
                        NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
                        nativeLibraryLoader.loadDexKit(
                            context, ModuleEntry.this.getClass().getClassLoader());
                        DexKitBridge dexKit =
                            createDexKitBridge(
                                hostClassLoader, lpparam.getApplicationInfo().sourceDir);
                        DisableHotUpdateFeature.install(context, hostClassLoader, dexKit);
                      } catch (Throwable e) {
                        HLog.e(TAG + " 热更新后置安装失败: " + e.getMessage(), e);
                      }
                    });
              },
              "Hchat-HotUpdateHook")
          .start();
    } catch (Throwable e) {
      HLog.e(TAG + " 热更新早期Hook失败(" + stage + "): " + e.getMessage(), e);
    }
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
            installCustomBottomBarEarly(context, classLoader);

            return result;
          });
    } catch (Throwable e) {
      HLog.e(TAG + " 自定义底栏早期入口安装失败: " + e.getMessage(), e);
    }
  }

  private void installCustomBottomBarEarly(Context context, ClassLoader classLoader) {
    try {
      CustomBottomBarFeature.installEarly(context, classLoader);
    } catch (Throwable e) {
      HLog.e(TAG + " 自定义底栏早期Hook失败: " + e.getMessage(), e);
    }
  }

  private void installTabletHookForAttach(
      PackageReadyParam lpparam, XposedInterface.Chain chain, String stage) {
    try {
      Context context = (Context) chain.getArg(0);
      if (!WeChatTabletFeature.isEnabled(context)) return;
      ClassLoader resolvedClassLoader = resolveTinkerClassLoader(chain.getThisObject());
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
              DexKitBridge dexKit =
                  createDexKitBridge(
                      lpparam.getClassLoader(), lpparam.getApplicationInfo().sourceDir);
              WeChatTabletFeature.install(context, lpparam.getClassLoader(), dexKit);
              return;
            }
            NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
            nativeLibraryLoader.loadDexKit(context, ModuleEntry.this.getClass().getClassLoader());
            DexKitBridge dexKit =
                createDexKitBridge(hostClassLoader, lpparam.getApplicationInfo().sourceDir);
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

  private void initModule(Context hostContext, PackageReadyParam lpparam) {
    try {
      // 1. 加载 Native 库（DexKit）
      NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
      nativeLibraryLoader.loadDexKit(hostContext, getClass().getClassLoader());
      nativeLibraryLoader.loadSilkCodec(hostContext, getClass().getClassLoader());

      // 2. 创建模块自身 Context
      Context moduleContext = createModuleContext(hostContext);
      ConfigStore configStore = new ConfigStore(hostContext);

      DexInstallScheduler.runDexKitTask(
          () -> {
            // 3. 初始化 DexKit。优先使用 Tinker 之后的运行时 ClassLoader。
            String apkPath = lpparam.getApplicationInfo().sourceDir;
            ClassLoader hostClassLoader = resolveHostClassLoader(hostContext, lpparam);
            DexKitBridge dexKit = createDexKitBridge(hostClassLoader, apkPath);
            DexFinder finder = new DexFinder(dexKit, hostClassLoader, hostContext);
            WeChatImageApi.installMarsCdnManagerHook(
                finder.marsCdnManagerClass, message -> HLog.e("[Hchat:WechatApi] " + message));

            // 4. 创建共享 DexKit 服务
            DexBridgeHolder dexBridgeHolder =
                new DexBridgeHolder(dexKit, finder, hostClassLoader, apkPath);

            // 5. 创建统一上下文
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
            featureManager = FeatureRegistry.createDefaultManager();
            featureManager.installAll(featureContext);

            // 7. 防崩兜底：微信 8.0.76 消息描述文本方法对 null 调 isEmpty() 崩溃（收到特殊小程序电商卡片消息触发）
            installMsgDescTextFallback(finder);
          });

    } catch (Throwable e) {
      HLog.e(TAG + " 初始化失败: " + e, e);
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
          HookRegistry.get()
              .hook(
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
      String key = apkPath != null ? apkPath : "";
      DexKitBridge cached = DEXKIT_BRIDGES.get(key);
      if (cached != null) return cached;
      // 仅缓存轻量入口；实际查询才加载，连续空闲 30 秒后释放 native 分析资源。
      DexKitBridge bridge = DexKitBridge.createManaged(apkPath, 30_000L);
      DEXKIT_BRIDGES.put(key, bridge);
      return bridge;
    }
  }

  private boolean isTinkerClassLoader(ClassLoader classLoader) {
    if (classLoader == null) return false;
    String text = String.valueOf(classLoader);
    return text.contains("/tinker/") || text.contains("DelegateLastClassLoader");
  }
}
