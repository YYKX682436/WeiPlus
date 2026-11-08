package h.Hchat.hooks.api.contact;

import android.content.Context;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.utils.KavaReflector;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives WeChat 8.0.80's own BatchQuitChatroomProcess. The manager owns the
 * batchquitchatroom scene and native local reconciliation; this adapter only
 * supplies a non-UI callback and suppresses its automatic resubmission path.
 */
public final class NativeBatchQuitChatroomProcess {
    public static final int MAX_BATCH_SIZE = 20;
    private static final long COMPLETION_TIMEOUT_SECONDS = 60L;

    public interface Logger {
        void log(String message);
    }

    public static final class Result {
        public final boolean completed;
        public final boolean requestPrepared;
        public final boolean networkError;
        public final boolean accountDetached;
        public final boolean timedOut;
        public final String error;
        public final Set<String> submittedIds;
        public final Set<String> serverAcceptedIds;
        public final Set<String> failedIds;
        public final Set<String> cleanupFailedIds;
        public final Set<String> retrySuppressedIds;

        private Result(boolean completed,
                       boolean requestPrepared,
                       boolean networkError,
                       boolean accountDetached,
                       boolean timedOut,
                       String error,
                       Collection<String> submittedIds,
                       Collection<String> serverAcceptedIds,
                       Collection<String> failedIds,
                       Collection<String> cleanupFailedIds,
                       Collection<String> retrySuppressedIds) {
            this.completed = completed;
            this.requestPrepared = requestPrepared;
            this.networkError = networkError;
            this.accountDetached = accountDetached;
            this.timedOut = timedOut;
            this.error = error == null ? "" : error;
            this.submittedIds = immutableSet(submittedIds);
            this.serverAcceptedIds = immutableSet(serverAcceptedIds);
            this.failedIds = immutableSet(failedIds);
            this.cleanupFailedIds = immutableSet(cleanupFailedIds);
            this.retrySuppressedIds = immutableSet(retrySuppressedIds);
        }

        private static Set<String> immutableSet(Collection<String> values) {
            if (values == null) return Collections.emptySet();
            synchronized (values) {
                return Collections.unmodifiableSet(new LinkedHashSet<>(values));
            }
        }

        static Result failed(String message) {
            return new Result(false, false, false, false, false, message,
                    null, null, null, null, null);
        }
    }

    private NativeBatchQuitChatroomProcess() {}

    public static Result execute(Context context,
                                 DexFinder dexFinder,
                                 List<String> selectedIds,
                                 Logger logger) {
        List<String> ids = cleanIds(selectedIds);
        if (ids.isEmpty()) return Result.failed("没有可提交的群聊");
        if (ids.size() > MAX_BATCH_SIZE) return Result.failed("微信原生单批最多支持20个群聊");
        if (context == null || dexFinder == null || !dexFinder.resolveBatchQuitChatroomApi()) {
            return Result.failed("微信原生批量退群 API 不可用");
        }

        Class<?> processClass = dexFinder.batchQuitChatroomProcessClass;
        Constructor<?> processCtor = dexFinder.batchQuitChatroomProcessCtor;
        Class<?> callbackType = processCtor.getParameterTypes()[1];
        if (!callbackType.isInterface()) return Result.failed("微信原生退群回调类型不可用");

        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean requestPrepared = new AtomicBoolean();
        AtomicBoolean networkError = new AtomicBoolean();
        AtomicBoolean accountDetached = new AtomicBoolean();
        AtomicBoolean timedOut = new AtomicBoolean();
        AtomicBoolean completionObserved = new AtomicBoolean();
        AtomicBoolean retainHooksForLateCompletion = new AtomicBoolean();
        HookLease hooks = new HookLease();
        LinkedHashSet<String> submittedIds = new LinkedHashSet<>();
        LinkedHashSet<String> acceptedIds = new LinkedHashSet<>();
        LinkedHashSet<String> failedIds = new LinkedHashSet<>();
        LinkedHashSet<String> cleanupFailedIds = new LinkedHashSet<>();
        LinkedHashSet<String> retrySuppressedIds = new LinkedHashSet<>();
        String[] error = {""};
        Object process = null;
        FieldBundle fields;

        try {
            InvocationHandler noUiCallback = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    switch (method.getName()) {
                        case "toString": return "WeiPlusBatchQuitCallback";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == (args != null && args.length > 0 ? args[0] : null);
                    }
                }
                return defaultValue(method.getReturnType());
            };
            Object callback = Proxy.newProxyInstance(
                    callbackType.getClassLoader(), new Class<?>[]{callbackType}, noUiCallback);
            process = KavaReflector.newInstance(processCtor, context, callback);
            if (process == null || !processClass.isInstance(process)) {
                return Result.failed("微信原生退群流程初始化失败");
            }
            fields = new FieldBundle(processClass);
            if (!fields.ready()) return Result.failed("微信原生退群流程状态字段不可用");

            final Object targetProcess = process;
            hooks.add(HookRegistry.get().hook(dexFinder.batchQuitChatroomSubmitMethod,
                    new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            if (chain.getThisObject() == targetProcess) {
                                Object[] args = chain.getArgs().toArray();
                                if (args.length > 0 && args[0] instanceof List) {
                                    synchronized (submittedIds) {
                                        submittedIds.addAll(cleanIds((List<?>) args[0]));
                                    }
                                    requestPrepared.set(!submittedIds.isEmpty());
                                }
                            }
                            return chain.proceed();
                        }
                    }));
            hooks.add(HookRegistry.get().hook(dexFinder.batchQuitChatroomRetryMethod,
                    new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            if (chain.getThisObject() != targetProcess) return chain.proceed();
                            Object[] args = chain.getArgs().toArray();
                            if (args.length >= 2 && args[0] instanceof String) {
                                String roomId = (String) args[0];
                                synchronized (retrySuppressedIds) { retrySuppressedIds.add(roomId); }
                                fields.clearUiCallback(targetProcess);
                                Object failure = KavaReflector.invokeOrThrow(
                                        dexFinder.batchQuitChatroomFailureFactoryMethod, targetProcess);
                                KavaReflector.invokeOrThrow(
                                        dexFinder.batchQuitChatroomFailureMethod,
                                        targetProcess, roomId, failure);
                                log(logger, "原生退群返回可重试结果；已禁止重复提交 room=" + roomId);
                            }
                            return null;
                        }
                    }));
            hooks.add(HookRegistry.get().hook(dexFinder.batchQuitChatroomFailureMethod,
                    new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            if (chain.getThisObject() == targetProcess) {
                                fields.clearUiCallback(targetProcess);
                            }
                            return chain.proceed();
                        }
                    }));
            hooks.add(HookRegistry.get().hook(dexFinder.batchQuitChatroomCompleteMethod,
                    new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            if (chain.getThisObject() != targetProcess) return chain.proceed();
                            fields.clearUiCallback(targetProcess);
                            copyStrings(KavaReflector.readField(targetProcess, "j"), acceptedIds);
                            copyMapKeys(KavaReflector.readField(targetProcess, "k"), failedIds);
                            copyMapKeys(KavaReflector.readField(targetProcess, "l"), cleanupFailedIds);
                            networkError.set(Boolean.TRUE.equals(
                                    KavaReflector.readField(targetProcess, "v")));
                            accountDetached.set(Boolean.TRUE.equals(
                                    KavaReflector.readField(targetProcess, "s")));
                            Object result = chain.proceed();
                            completionObserved.set(true);
                            completed.countDown();
                            if (timedOut.get()) hooks.release();
                            return result;
                        }
                    }));

            Object worker = KavaReflector.newInstance(
                    dexFinder.batchQuitChatroomWorkerCtor, process, ids, false);
            if (worker == null || !dexFinder.batchQuitChatroomWorkerClass.isInstance(worker)) {
                return Result.failed("微信原生退群任务初始化失败");
            }
            KavaReflector.invokeOrThrow(dexFinder.batchQuitChatroomWorkerRunMethod, worker);

            boolean finished;
            try {
                finished = completed.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                finished = false;
                error[0] = "等待微信原生退群结果时任务被中断";
            }
            if (!finished) {
                timedOut.set(true);
                if (!completionObserved.get()) retainHooksForLateCompletion.set(true);
                if (error[0].isEmpty()) error[0] = "微信原生退群结果超时；未重试任何可能已生效的请求";
            }
            return new Result(completionObserved.get(), requestPrepared.get(), networkError.get(),
                    accountDetached.get(), timedOut.get(), error[0], submittedIds, acceptedIds,
                    failedIds, cleanupFailedIds, retrySuppressedIds);
        } catch (Throwable e) {
            error[0] = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log(logger, "微信原生批量退群失败: " + error[0]);
            if (requestPrepared.get() && !completionObserved.get()) retainHooksForLateCompletion.set(true);
            return new Result(completionObserved.get(), requestPrepared.get(), networkError.get(),
                    accountDetached.get(), timedOut.get(), error[0], submittedIds, acceptedIds,
                    failedIds, cleanupFailedIds, retrySuppressedIds);
        } finally {
            if (!retainHooksForLateCompletion.get() || completionObserved.get()) hooks.release();
        }
    }

    /** Keeps instance-scoped hooks alive so a late native response can still reconcile local state. */
    private static final class HookLease {
        private final List<XposedInterface.HookHandle> handles = new ArrayList<>();
        private final AtomicBoolean released = new AtomicBoolean();

        synchronized void add(XposedInterface.HookHandle handle) {
            if (released.get()) {
                HookRegistry.get().unhook(handle);
                return;
            }
            handles.add(handle);
        }

        void release() {
            if (!released.compareAndSet(false, true)) return;
            List<XposedInterface.HookHandle> copy;
            synchronized (this) {
                copy = new ArrayList<>(handles);
                handles.clear();
            }
            for (int i = copy.size() - 1; i >= 0; i--) {
                try { HookRegistry.get().unhook(copy.get(i)); } catch (Throwable ignored) {}
            }
        }
    }

    private static final class FieldBundle {
        private final java.lang.reflect.Field callback;

        FieldBundle(Class<?> processClass) {
            callback = KavaReflector.accessible(KavaReflector.findFieldRecursive(processClass, "c"));
        }

        boolean ready() { return callback != null; }

        void clearUiCallback(Object process) {
            if (!KavaReflector.writeField(callback, process, null)) {
                throw new IllegalStateException("无法隔离微信原生批量退群 UI 回调");
            }
        }

    }

    private static List<String> cleanIds(List<?> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (Object value : values) {
            String id = value instanceof String ? ((String) value).trim() : "";
            if (!id.isEmpty() && !result.contains(id)) result.add(id);
        }
        return result;
    }

    private static void copyStrings(Object source, Set<String> destination) {
        if (!(source instanceof Iterable)) return;
        synchronized (destination) {
            for (Object value : (Iterable<?>) source) {
                if (value instanceof String && !((String) value).isEmpty()) destination.add((String) value);
            }
        }
    }

    private static void copyMapKeys(Object source, Set<String> destination) {
        if (!(source instanceof Map)) return;
        synchronized (destination) {
            for (Object value : ((Map<?, ?>) source).keySet()) {
                if (value instanceof String && !((String) value).isEmpty()) destination.add((String) value);
            }
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private static void log(Logger logger, String message) {
        if (logger == null) return;
        try { logger.log(message); } catch (Throwable ignored) {}
    }
}
