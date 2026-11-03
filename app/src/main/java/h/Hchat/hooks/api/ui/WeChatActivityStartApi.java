package h.Hchat.hooks.api.ui;

import android.app.Activity;
import android.content.ContextWrapper;
import android.content.Intent;

import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.utils.KavaReflector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import io.github.libxposed.api.XposedInterface.HookHandle;


/**
 * 微信 Activity 启动监听 API。
 */
public final class WeChatActivityStartApi {
    public interface Logger {
        void log(String message);
    }

    public interface Listener {
        void onStartActivity(Intent intent, Object caller, Method method);
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
    private final Map<Method, HookHandle> hooks = new LinkedHashMap<>();
    private volatile boolean installed;
    private volatile int hookedMethodCount;

    public WeChatActivityStartApi(Logger logger) {
        this.logger = logger;
    }

    public synchronized boolean isAvailable() {
        refreshInstalledState(discoverStartMethods());
        return installed;
    }

    public synchronized int hookedMethodCount() {
        refreshInstalledState(discoverStartMethods());
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
        List<Method> methods = discoverStartMethods();
        HookRegistry registry = HookRegistry.get();
        for (Method method : methods) {
            HookHandle existing = hooks.get(method);
            if (registry.isActive(existing)) continue;
            try {
                HookHandle handle = registry.hook(method, chain -> {
                    Intent intent = findIntent(chain.getArgs().toArray());
                    if (intent != null) dispatch(intent, chain.getThisObject(), method);
                    return chain.proceed();
                });
                hooks.put(method, handle);
            } catch (Throwable failure) {
                log("Activity启动监听Hook失败: " + method + " " + failure.getMessage());
            }
        }
        refreshInstalledState(methods);
        log("Activity启动监听Hook: methods=" + hookedMethodCount + "/" + methods.size());
    }

    private List<Method> discoverStartMethods() {
        LinkedHashSet<Method> methods = new LinkedHashSet<>();
        for (Class<?> clazz : new Class<?>[] {Activity.class, ContextWrapper.class}) {
            try {
                for (Method method : KavaReflector.declaredMethods(clazz)) {
                    if (isStartMethod(method)) methods.add(method);
                }
            } catch (Throwable failure) {
                log("Activity启动监听方法枚举失败: " + clazz.getName() + " " + failure.getMessage());
            }
        }
        return new ArrayList<>(methods);
    }

    private void refreshInstalledState(List<Method> methods) {
        HookRegistry registry = HookRegistry.get();
        hookedMethodCount = 0;
        for (Method method : methods) if (registry.isActive(hooks.get(method))) hookedMethodCount++;
        installed = !methods.isEmpty() && hookedMethodCount == methods.size();
    }

    private boolean isStartMethod(Method method) {
        if (method == null) return false;
        String name = method.getName();
        if (!"startActivity".equals(name) && !"startActivityForResult".equals(name)) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        if (params == null || params.length == 0) return false;
        for (Class<?> param : params) {
            if (param == Intent.class) return true;
        }
        return false;
    }

    private Intent findIntent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Intent) return (Intent) arg;
        }
        return null;
    }

    private void dispatch(Intent intent, Object caller, Method method) {
        for (Listener listener : listeners) {
            try {
                listener.onStartActivity(intent, caller, method);
            } catch (Throwable e) {
                log("Activity启动监听回调失败: " + e.getMessage());
            }
        }
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatActivityStartApi] " + message);
    }
}
