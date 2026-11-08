package h.Hchat.hooks.api.contact;

import h.Hchat.hooks.api.net.WeChatNetworkDispatcher;
import h.Hchat.hooks.core.HookRegistry;
import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded response observation for native WeChat network scenes. */
final class NativeSceneDispatch {
    private NativeSceneDispatch() {}

    static ContactRequestDispatch.Result submitAndAwait(
            Object scene,
            WeChatNetworkDispatcher dispatcher,
            ContactRequestDispatch.SubmitGuard guard,
            ContactRequestDispatch.Callback callback) {
        if (scene == null || dispatcher == null || callback == null) {
            return ContactRequestDispatch.notSubmitted();
        }
        Method response = findResponseMethod(scene.getClass());
        if (response == null) return ContactRequestDispatch.notSubmitted();
        AtomicReference<ContactRequestDispatch.Callback> pending = new AtomicReference<>();
        final XposedInterface.HookHandle handle;
        try {
            handle = HookRegistry.get().hook(response, new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object[] args = chain.getArgs().toArray();
                    Object receiver = chain.getThisObject();
                    Object result = chain.proceed();
                    ContactRequestDispatch.Callback target = pending.get();
                    NativeSceneResponse response = NativeSceneResponse.fromArguments(args);
                    if (receiver == scene && target != null && response != null) {
                        target.onResponse(response.errorType, response.errorCode);
                    }
                    return result;
                }
            });
        } catch (Throwable ignored) {
            return ContactRequestDispatch.notSubmitted();
        }
        try {
            return ContactRequestDispatch.submitAndAwait(result -> {
                if (guard != null && !guard.maySubmit()) return null;
                pending.set(result);
                return dispatcher.send(scene) ? scene : null;
            });
        } finally {
            pending.set(null);
            try {
                HookRegistry.get().unhook(handle);
            } catch (Throwable ignored) {}
        }
    }

    private static Method findResponseMethod(Class<?> sceneClass) {
        for (Class<?> type = sceneClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (!"onGYNetEnd".equals(method.getName()) || method.getReturnType() != void.class
                        || Modifier.isAbstract(method.getModifiers()) || params.length != 6
                        || params[0] != int.class || params[1] != int.class || params[2] != int.class
                        || params[3] != String.class || params[5] != byte[].class
                        || !params[4].getName().equals("com.tencent.mm.network.y0")) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }
}
