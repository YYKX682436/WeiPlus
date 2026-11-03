package h.Hchat.hooks.api.ui;

import android.app.Activity;

import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.utils.KavaReflector;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;
import io.github.libxposed.api.XposedInterface.HookHandle;


/**
 * 微信 Activity 生命周期监听 API。
 */
public final class WeChatLifecycleApi {
    public interface Logger {
        void log(String message);
    }

    public interface Listener {
        void onActivityEvent(ActivityEvent event);
    }

    public static final class ActivityEvent {
        public static final String RESUME = "resume";
        public static final String PAUSE = "pause";
        public static final String DESTROY = "destroy";

        public final String event;
        public final Activity activity;
        public final String activityName;

        private ActivityEvent(String event, Activity activity) {
            this.event = event;
            this.activity = activity;
            this.activityName = activity != null ? activity.getClass().getName() : "";
        }

        public boolean isResume() {
            return RESUME.equals(event);
        }

        public boolean isPause() {
            return PAUSE.equals(event);
        }

        public boolean isDestroy() {
            return DESTROY.equals(event);
        }

        public boolean isChatting() {
            return activityName.contains(".ui.chatting.");
        }
    }

    public final class Subscription {
        private final Listener listener;
        private volatile boolean active = true;

        private Subscription(Listener listener) {
            this.listener = listener;
        }

        public void unsubscribe() {
            if (!active) return;
            active = false;
            listeners.remove(listener);
        }

        public boolean isActive() {
            return active;
        }
    }

    private final Logger logger;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean installed;
    private volatile int hookedMethodCount;
    private HookHandle resumeHook;
    private HookHandle pauseHook;
    private HookHandle destroyHook;

    public WeChatLifecycleApi(Logger logger) {
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

    public Subscription subscribe(Listener listener) {
        if (listener == null) return null;
        listeners.addIfAbsent(listener);
        return new Subscription(listener);
    }

    public void unsubscribe(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    public synchronized void install() {
        if (isAvailable()) return;
        resumeHook = hookLifecycleMethod("onResume", ActivityEvent.RESUME, resumeHook);
        pauseHook = hookLifecycleMethod("onPause", ActivityEvent.PAUSE, pauseHook);
        destroyHook = hookLifecycleMethod("onDestroy", ActivityEvent.DESTROY, destroyHook);
        refreshInstalledState();
        log("生命周期监听Hook: methods=" + hookedMethodCount + "/3");
    }

    private HookHandle hookLifecycleMethod(String methodName, String event, HookHandle existing) {
        HookRegistry registry = HookRegistry.get();
        if (registry.isActive(existing)) return existing;
        try {
            Method method = KavaReflector.findDeclaredMethod(Activity.class, methodName);
            return registry.hook(method, chain -> {
                boolean cleanup = ActivityEvent.PAUSE.equals(event) || ActivityEvent.DESTROY.equals(event);
                Object result;
                try {
                    result = chain.proceed();
                } finally {
                    if (cleanup) dispatchLifecycleEvent(event, chain.getThisObject());
                }
                if (!cleanup) dispatchLifecycleEvent(event, chain.getThisObject());
                return result;
            });
        } catch (Throwable e) {
            log("生命周期Hook失败: " + methodName + " " + e.getMessage());
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

    private void dispatchLifecycleEvent(String event, Object receiver) {
        if (!(receiver instanceof Activity)) return;
        try {
            dispatch(new ActivityEvent(event, (Activity) receiver));
        } catch (Throwable error) {
            // This observer must never replace a result or exception from the host callback.
            try {
                h.Hchat.utils.HLog.e("[WeChatLifecycleApi] 生命周期派发失败: " + event, error);
            } catch (Throwable ignored) {
            }
        }
    }

    private void dispatch(ActivityEvent event) {
        for (Listener listener : listeners) {
            try {
                listener.onActivityEvent(event);
            } catch (Throwable e) {
                try {
                    log("生命周期回调失败: " + e.getMessage());
                } catch (Throwable logFailure) {
                    // A broken logger cannot prevent the remaining cleanup subscribers.
                    try {
                        h.Hchat.utils.HLog.e("[WeChatLifecycleApi] 回调错误日志失败", logFailure);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatLifecycleApi] " + message);
    }
}
