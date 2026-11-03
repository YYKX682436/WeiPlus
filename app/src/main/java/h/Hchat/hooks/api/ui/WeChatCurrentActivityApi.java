package h.Hchat.hooks.api.ui;

import android.app.Activity;

import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.utils.KavaReflector;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;

/**
 * 当前微信 Activity 跟踪 API。
 */
public final class WeChatCurrentActivityApi {
    public interface Logger {
        void log(String message);
    }

    private final Logger logger;
    private volatile WeakReference<Activity> currentActivity =
            new WeakReference<>(null);
    private volatile boolean installed;
    private volatile int hookedMethodCount;
    private HookHandle resumeHook;
    private HookHandle pauseHook;
    private HookHandle destroyHook;

    public WeChatCurrentActivityApi(Logger logger) {
        this.logger = logger;
    }

    public synchronized boolean isAvailable() {
        refreshInstalledState();
        return installed;
    }

    public synchronized int hookedMethodCount() {
        refreshInstalledState();
        return hookedMethodCount;
    }

    public synchronized void install() {
        if (isAvailable()) return;
        resumeHook = installResumeHook(resumeHook);
        pauseHook = installClearHook("onPause", pauseHook);
        destroyHook = installClearHook("onDestroy", destroyHook);
        refreshInstalledState();
        log("当前Activity Hook: methods=" + hookedMethodCount + "/3");
    }

    private HookHandle installResumeHook(HookHandle existing) {
        HookRegistry registry = HookRegistry.get();
        if (registry.isActive(existing)) return existing;
        try {
            Method onResume = KavaReflector.findDeclaredMethod(Activity.class, "onResume");
            return registry.hook(onResume, chain -> {
                Object hookResult = chain.proceed();
                Object receiver = chain.getThisObject();
                if (receiver instanceof Activity) currentActivity = new WeakReference<>((Activity) receiver);
                return hookResult;
            });
        } catch (Throwable error) {
            log("当前Activity Hook失败: onResume " + error.getMessage());
            return null;
        }
    }

    private HookHandle installClearHook(String methodName, HookHandle existing) {
        HookRegistry registry = HookRegistry.get();
        if (registry.isActive(existing)) return existing;
        try {
            Method method = KavaReflector.findDeclaredMethod(Activity.class, methodName);
            XposedInterface.Hooker clearHook = chain -> {
                try {
                    return chain.proceed();
                } finally {
                    Activity current = currentActivity();
                    if (current != null && current == chain.getThisObject())
                        currentActivity = new WeakReference<>(null);
                }
            };
            return registry.hook(method, clearHook);
        } catch (Throwable error) {
            log("当前Activity Hook失败: " + methodName + " " + error.getMessage());
            return null;
        }
    }

    private void refreshInstalledState() {
        HookRegistry registry = HookRegistry.get();
        hookedMethodCount = (registry.isActive(resumeHook) ? 1 : 0)
                + (registry.isActive(pauseHook) ? 1 : 0)
                + (registry.isActive(destroyHook) ? 1 : 0);
        installed = hookedMethodCount == 3;
    }

    public Activity currentActivity() {
        WeakReference<Activity> ref = currentActivity;
        return ref != null ? ref.get() : null;
    }

    public void updateCurrentActivity(Activity activity) {
        if (activity != null && !activity.isFinishing()) {
            currentActivity = new WeakReference<>(activity);
        }
    }

    public String currentActivityName() {
        Activity activity = currentActivity();
        return activity != null ? activity.getClass().getName() : "";
    }

    public boolean isCurrentActivity(String className) {
        String currentName = currentActivityName();
        return className != null && className.equals(currentName);
    }

    public boolean isCurrentActivityContains(String classNamePart) {
        String currentName = currentActivityName();
        return classNamePart != null && currentName.contains(classNamePart);
    }

    public boolean isInChatting() {
        return isCurrentActivityContains(".ui.chatting.");
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatCurrentActivityApi] " + message);
    }
}
