package h.Hchat.hooks.api.contact;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.utils.KavaReflector;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs WeChat's native batchmodcontacttype unsubscribe scene without its progress-dialog UI. */
public final class NativeBatchOfficialUnfollowProcess {
    public static final int NATIVE_MAX_BATCH_SIZE = 50;
    public static final int CONTACT_TYPE_FOLLOWED_OFFICIAL_ACCOUNT = 1;
    public static final int CONTACT_TYPE_OPERATION_UNSUBSCRIBE = 2;
    private static final int SCENE_TYPE = 0x0f96;
    private static final String BATCH_LIMIT_CONFIG = "MMBatchModContactTypeMaxNumForServer";
    private static final long COMPLETION_TIMEOUT_SECONDS = 60L;

    public interface Logger { void log(String message); }
    public interface SubmissionGuard { boolean mayContinue(); }

    public static final class ItemResult {
        public final String username;
        public final boolean requestObserved;
        public final Integer serverResult;
        public final boolean nativeLocalResult;
        public final boolean nativeCleanupFailed;

        private ItemResult(String username, boolean requestObserved, Integer serverResult,
                           boolean nativeLocalResult, boolean nativeCleanupFailed) {
            this.username = username;
            this.requestObserved = requestObserved;
            this.serverResult = serverResult;
            this.nativeLocalResult = nativeLocalResult;
            this.nativeCleanupFailed = nativeCleanupFailed;
        }
    }

    public static final class Result {
        public final boolean submitted;
        public final boolean completed;
        public final boolean timedOut;
        public final int callbackErrorType;
        public final int callbackErrorCode;
        public final String error;
        public final List<ItemResult> items;

        private Result(boolean submitted, boolean completed, boolean timedOut,
                       int callbackErrorType, int callbackErrorCode, String error,
                       List<ItemResult> items) {
            this.submitted = submitted;
            this.completed = completed;
            this.timedOut = timedOut;
            this.callbackErrorType = callbackErrorType;
            this.callbackErrorCode = callbackErrorCode;
            this.error = error == null ? "" : error;
            this.items = items == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    private NativeBatchOfficialUnfollowProcess() {}

    /** Returns the live native server limit, bounded by the verified 8.0.80 default of 50. */
    public static int configuredBatchLimit(DexFinder dexFinder) {
        if (dexFinder == null || !dexFinder.resolveBatchOfficialUnfollowApi()) return 0;
        ClassLoader loader = dexFinder.batchOfficialUnfollowSceneClass.getClassLoader();
        try {
            Class<?> serviceKey = KavaReflector.loadClass("ra5.e", loader);
            Class<?> serviceLocator = KavaReflector.loadClass("fi5.n0", loader);
            Class<?> serviceApi = KavaReflector.loadClass("com.tencent.mm.plugin.zero.a", loader);
            Class<?> configApi = KavaReflector.loadClass("rp.j", loader);
            if (serviceKey == null || serviceLocator == null || serviceApi == null || configApi == null) return 0;
            Method resolve = KavaReflector.findMethodRecursive(serviceLocator, "c", Class.class);
            if (resolve == null || !Modifier.isStatic(resolve.getModifiers())) return 0;
            Object service = KavaReflector.invokeOrThrow(resolve, null, serviceKey);
            Method getConfig = KavaReflector.findMethodRecursive(serviceApi, "b");
            if (service == null || getConfig == null) return 0;
            Object config = KavaReflector.invokeOrThrow(getConfig, service);
            Method getInt = KavaReflector.findMethodRecursive(configApi, "c", String.class, int.class);
            if (config == null || getInt == null) return 0;
            Object raw = KavaReflector.invokeOrThrow(getInt, config, BATCH_LIMIT_CONFIG,
                    NATIVE_MAX_BATCH_SIZE);
            if (!(raw instanceof Number)) return 0;
            int configured = ((Number) raw).intValue();
            return configured > 0 ? Math.min(configured, NATIVE_MAX_BATCH_SIZE) : 0;
        } catch (Throwable e) {
            log(null, "无法读取微信批量取消关注上限: " + e.getClass().getSimpleName());
            return 0;
        }
    }

    public static Result execute(DexFinder dexFinder, List<String> selectedUsernames,
                                 SubmissionGuard guard, Logger logger) {
        List<String> usernames = clean(selectedUsernames);
        if (usernames.isEmpty()) return failed("没有可提交的公众号");
        if (dexFinder == null || !dexFinder.resolveBatchOfficialUnfollowApi()) {
            return failed("微信原生批量取消关注 API 不可用");
        }
        int batchLimit = configuredBatchLimit(dexFinder);
        if (batchLimit <= 0 || usernames.size() > batchLimit) {
            return failed("微信原生批量取消关注上限不可用或对象数量超限");
        }
        if (guard != null && !guard.mayContinue()) return failed("提交前已停止");

        Class<?> sceneClass = dexFinder.batchOfficialUnfollowSceneClass;
        ClassLoader loader = sceneClass.getClassLoader();
        NativeQueue queue = NativeQueue.resolve(loader, sceneClass);
        if (queue == null) return failed("微信原生网络回调队列不可用");

        Object scene = KavaReflector.newInstance(dexFinder.batchOfficialUnfollowSceneCtor,
                new java.util.LinkedList<>(usernames), CONTACT_TYPE_FOLLOWED_OFFICIAL_ACCOUNT,
                CONTACT_TYPE_OPERATION_UNSUBSCRIBE, batchLimit);
        if (scene == null || !sceneClass.isInstance(scene)) return failed("微信批量取消关注场景初始化失败");

        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean submitted = new AtomicBoolean();
        AtomicBoolean callbackObserved = new AtomicBoolean();
        AtomicBoolean timedOut = new AtomicBoolean();
        AtomicInteger callbackErrorType = new AtomicInteger(Integer.MIN_VALUE);
        AtomicInteger callbackErrorCode = new AtomicInteger(Integer.MIN_VALUE);
        LinkedHashSet<String> requestObserved = new LinkedHashSet<>();
        LinkedHashSet<String> locallyConfirmed = new LinkedHashSet<>();
        LinkedHashSet<String> localCleanupFailed = new LinkedHashSet<>();
        Map<String, Integer> serverResults = new LinkedHashMap<>();
        Set<String> ambiguousResponses = new HashSet<>();
        String[] error = {""};
        Object[] callbackBox = new Object[1];
        AtomicBoolean callbackRegistrationAttempted = new AtomicBoolean();
        AtomicBoolean callbackRemoved = new AtomicBoolean();

        try {
            Object callback = Proxy.newProxyInstance(queue.callbackType.getClassLoader(),
                    new Class<?>[]{queue.callbackType}, (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("toString".equals(method.getName())) return "WeiPlusBatchOfficialUnfollowCallback";
                            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                            if ("equals".equals(method.getName())) return proxy == (args != null && args.length > 0 ? args[0] : null);
                        }
                        if ("onSceneEnd".equals(method.getName()) && args != null && args.length == 4
                                && args[3] == scene) {
                            if (number(args[0]) == 0 && number(args[1]) == 0) {
                                runObserverSafely(() -> collectServerResults(scene, usernames, requestObserved,
                                        serverResults, ambiguousResponses), logger, "服务端逐项结果");
                            }
                            runObserverSafely(() -> {
                                copyStrings(KavaReflector.readField(scene, "e"), locallyConfirmed);
                                copyStrings(KavaReflector.readField(scene, "q"), localCleanupFailed);
                            }, logger, "微信本地联系人结果");
                            if (number(args[0]) != 0 || number(args[1]) != 0) {
                                error[0] = "微信批量取消关注网络回调失败";
                            }
                            if (recordQueueCallback(scene, method.getName(), args, completed,
                                    callbackObserved, callbackErrorType, callbackErrorCode)) {
                                log(logger, "公众号批量取消关注原生回调已到达: errType="
                                        + callbackErrorType.get() + " errCode=" + callbackErrorCode.get()
                                        + " responseItems=" + serverResults.size() + "/" + usernames.size());
                            }
                            removeQueueCallback(queue, proxy, callbackRemoved, logger);
                        }
                        return defaultValue(method.getReturnType());
                    });
            callbackBox[0] = callback;
            callbackRegistrationAttempted.set(true);
            KavaReflector.invokeOrThrow(queue.addCallback, queue.manager, SCENE_TYPE, callback);
            try {
                Object accepted = KavaReflector.invokeOrThrow(queue.submitScene, queue.manager, scene);
                if (!(accepted instanceof Boolean) || !((Boolean) accepted)) {
                    error[0] = "微信网络队列未接受公众号批量请求";
                    try { KavaReflector.invokeOrThrow(queue.removeCallback, queue.manager, SCENE_TYPE, callback); }
                    catch (Throwable ignored) {}
                    return new Result(false, false, false, Integer.MIN_VALUE, Integer.MIN_VALUE,
                            error[0], resultItems(usernames, requestObserved, serverResults,
                                    locallyConfirmed, localCleanupFailed, ambiguousResponses));
                }
                submitted.set(true);
            } catch (Throwable e) {
                // The queue call can throw after enqueue; preserve the observation hook and never retry.
                submitted.set(true);
                error[0] = "微信网络队列提交结果未知";
                log(logger, error[0] + ": " + e.getClass().getSimpleName());
            }

            boolean finished;
            try {
                finished = completed.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                finished = false;
                error[0] = "等待微信公众号批量结果时任务被中断";
            }
            if (!finished) {
                timedOut.set(true);
                if (!callbackObserved.get()) error[0] = "微信公众号批量请求结果超时；未重试";
            }
            return new Result(submitted.get(), callbackObserved.get() && finished,
                    timedOut.get(), callbackErrorType.get(), callbackErrorCode.get(), error[0],
                    resultItems(usernames, requestObserved, serverResults,
                            locallyConfirmed, localCleanupFailed, ambiguousResponses));
        } catch (Throwable e) {
            error[0] = e.getClass().getSimpleName();
            log(logger, "微信原生公众号批量取消关注失败: " + error[0]);
            return new Result(submitted.get(), callbackObserved.get(), timedOut.get(),
                    callbackErrorType.get(), callbackErrorCode.get(), error[0],
                    resultItems(usernames, requestObserved, serverResults,
                            locallyConfirmed, localCleanupFailed, ambiguousResponses));
        } finally {
            if (callbackRegistrationAttempted.get()) {
                removeQueueCallback(queue, callbackBox[0], callbackRemoved, logger);
            }
        }
    }

    /** The queue dispatches u0 only after WeChat's native scene has processed the response. */
    static boolean recordQueueCallback(Object expectedScene, String methodName, Object[] args,
                                       CountDownLatch completed, AtomicBoolean callbackObserved,
                                       AtomicInteger callbackErrorType, AtomicInteger callbackErrorCode) {
        if (expectedScene == null || !"onSceneEnd".equals(methodName) || args == null
                || args.length != 4 || args[3] != expectedScene) {
            return false;
        }
        callbackErrorType.set(number(args[0]));
        callbackErrorCode.set(number(args[1]));
        callbackObserved.set(true);
        completed.countDown();
        return true;
    }

    static boolean runObserverSafely(Runnable observer, Logger logger, String phase) {
        try {
            observer.run();
            return true;
        } catch (Throwable e) {
            log(logger, "读取公众号批量取消关注" + phase + "失败: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static void removeQueueCallback(NativeQueue queue, Object callback,
                                            AtomicBoolean callbackRemoved, Logger logger) {
        if (callback == null || !callbackRemoved.compareAndSet(false, true)) return;
        try {
            KavaReflector.invokeOrThrow(queue.removeCallback, queue.manager, SCENE_TYPE, callback);
        } catch (Throwable e) {
            log(logger, "注销公众号批量回调失败: " + e.getClass().getSimpleName());
        }
    }

    private static List<ItemResult> resultItems(List<String> usernames, Set<String> requestObserved,
                                                 Map<String, Integer> serverResults,
                                                 Set<String> locallyConfirmed,
                                                 Set<String> localCleanupFailed,
                                                 Set<String> ambiguousResponses) {
        List<ItemResult> items = new ArrayList<>();
        for (String username : usernames) {
            Integer serverResult = ambiguousResponses.contains(username) ? null : serverResults.get(username);
            items.add(new ItemResult(username, requestObserved.contains(username), serverResult,
                    locallyConfirmed.contains(username), localCleanupFailed.contains(username)));
        }
        return items;
    }

    private static void collectServerResults(Object scene, List<String> requested,
                                             Set<String> requestObserved,
                                             Map<String, Integer> serverResults,
                                             Set<String> ambiguous) {
        Object requestContainer = KavaReflector.readField(scene, "i");
        Object requestEnvelope = KavaReflector.readField(requestContainer, "a");
        Object request = KavaReflector.readField(requestEnvelope, "a");
        Object responseEnvelope = KavaReflector.readField(requestContainer, "b");
        Object response = KavaReflector.readField(responseEnvelope, "a");
        List<?> requestItems = asList(KavaReflector.readField(request, "e"));
        List<?> responseItems = asList(KavaReflector.readField(response, "e"));
        Map<String, ResponseItem> byUsername = new HashMap<>();
        Set<String> duplicateResponse = new HashSet<>();
        for (Object item : responseItems) {
            Object nested = KavaReflector.readField(item, "e");
            String username = string(KavaReflector.readField(nested, "d"));
            if (username.isEmpty()) continue;
            ResponseItem previous = byUsername.put(username,
                    new ResponseItem(integer(KavaReflector.readField(item, "d")),
                            integer(KavaReflector.readField(nested, "e")),
                            integer(KavaReflector.readField(nested, "f"))));
            if (previous != null) duplicateResponse.add(username);
        }
        Set<String> duplicateRequests = new HashSet<>();
        for (Object item : requestItems) {
            String username = string(KavaReflector.readField(item, "d"));
            if (username.isEmpty() || !requested.contains(username)) continue;
            int type = integer(KavaReflector.readField(item, "e"));
            int operation = integer(KavaReflector.readField(item, "f"));
            if (type != CONTACT_TYPE_FOLLOWED_OFFICIAL_ACCOUNT
                    || operation != CONTACT_TYPE_OPERATION_UNSUBSCRIBE) continue;
            if (!requestObserved.add(username)) duplicateRequests.add(username);
            ResponseItem responseItem = byUsername.get(username);
            if (responseItem == null || duplicateResponse.contains(username)
                    || responseItem.type != type || responseItem.operation != operation) {
                ambiguous.add(username);
            } else if (!ambiguous.contains(username)) {
                serverResults.put(username, responseItem.result);
            }
        }
        ambiguous.addAll(duplicateRequests);
    }

    private static final class ResponseItem {
        final int result, type, operation;
        ResponseItem(int result, int type, int operation) {
            this.result = result;
            this.type = type;
            this.operation = operation;
        }
    }

    private static final class NativeQueue {
        final Object manager;
        final Class<?> callbackType;
        final Method addCallback, removeCallback, submitScene;

        NativeQueue(Object manager, Class<?> callbackType, Method addCallback,
                    Method removeCallback, Method submitScene) {
            this.manager = manager;
            this.callbackType = callbackType;
            this.addCallback = addCallback;
            this.removeCallback = removeCallback;
            this.submitScene = submitScene;
        }

        static NativeQueue resolve(ClassLoader loader, Class<?> sceneClass) {
            try {
                Class<?> callbackType = KavaReflector.loadClass("com.tencent.mm.modelbase.u0", loader);
                Class<?> managerEntry = KavaReflector.loadClass("zp0.j1", loader);
                if (callbackType == null || !callbackType.isInterface() || managerEntry == null) return null;
                Method getter = findMethod(managerEntry, "e", method ->
                        Modifier.isStatic(method.getModifiers()) && method.getParameterTypes().length == 0
                                && !method.getReturnType().isPrimitive());
                Object manager = KavaReflector.invokeOrThrow(getter, null);
                if (manager == null) return null;
                Method add = findMethod(manager.getClass(), "a", method ->
                        !Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                                && callbackRegistrationSignature(method, callbackType));
                Method remove = findMethod(manager.getClass(), "q", method ->
                        !Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                                && callbackRegistrationSignature(method, callbackType));
                Method submit = findMethod(manager.getClass(), "g", method ->
                        !Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class
                                && method.getParameterTypes().length == 1
                                && method.getParameterTypes()[0].isAssignableFrom(sceneClass));
                if (add == null || remove == null || submit == null) return null;
                return new NativeQueue(manager, callbackType, add, remove, submit);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static boolean callbackRegistrationSignature(Method method, Class<?> callbackType) {
            Class<?>[] p = method.getParameterTypes();
            return p.length == 2 && p[0] == int.class && p[1] == callbackType;
        }
    }

    private interface MethodCheck { boolean matches(Method method); }

    private static Method findMethod(Class<?> owner, String name, MethodCheck check) {
        if (owner == null) return null;
        for (Method method : KavaReflector.declaredMethods(owner)) {
            if (name.equals(method.getName()) && check.matches(method)) return method;
        }
        return null;
    }

    private static List<String> clean(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) {
            String username = value == null ? "" : value.trim();
            if (!username.isEmpty() && !result.contains(username)) result.add(username);
        }
        return result;
    }

    private static List<?> asList(Object value) { return value instanceof List ? (List<?>) value : Collections.emptyList(); }

    private static int integer(Object value) { return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE; }
    private static int number(Object value) { return integer(value); }
    private static String string(Object value) { return value instanceof String ? ((String) value).trim() : ""; }

    private static void copyStrings(Object source, Set<String> destination) {
        if (!(source instanceof Iterable)) return;
        synchronized (destination) {
            for (Object value : (Iterable<?>) source) {
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

    private static Result failed(String message) {
        return new Result(false, false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, message,
                Collections.emptyList());
    }

    private static void log(Logger logger, String message) {
        if (logger == null) return;
        try { logger.log(message); } catch (Throwable ignored) {}
    }
}
