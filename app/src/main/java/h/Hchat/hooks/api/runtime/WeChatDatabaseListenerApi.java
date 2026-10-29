package h.Hchat.hooks.api.runtime;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.api.model.DatabaseChange;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.utils.KavaReflector;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 微信 SqliteDB wrapper 变更与查询监听 API。
 */
public final class WeChatDatabaseListenerApi {
    public interface Logger {
        void log(String message);
    }

    public interface Listener {
        void onDatabaseChanged(DatabaseChange change);
    }

    public interface QueryInterceptor {
        String onQuery(String sql);
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

    public final class QuerySubscription {
        private final QueryInterceptor interceptor;
        private volatile boolean active = true;

        private QuerySubscription(QueryInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        public void unsubscribe() {
            if (!active) return;
            active = false;
            queryInterceptors.remove(interceptor);
        }

        public boolean isActive() {
            return active;
        }
    }

    private final DexFinder dexFinder;
    private final ClassLoader classLoader;
    private final Logger logger;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<QueryInterceptor> queryInterceptors = new CopyOnWriteArrayList<>();
    private final Map<Method, HookRegistration> hookRegistrations = new ConcurrentHashMap<>();
    private final Set<Method> hookedWrapperMutationMethods = ConcurrentHashMap.newKeySet();
    private final Set<Method> hookedWrapperInsertMethods = ConcurrentHashMap.newKeySet();
    // One logical operation can traverse wrapper, WCDB and Android SQLite hooks.
    private final ThreadLocal<Integer> mutationDepth = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<Integer> queryDepth = ThreadLocal.withInitial(() -> 0);
    private volatile boolean installed;
    private volatile int hookedMethodCount;
    private volatile Class<?> hookedWrapperClass;

    public WeChatDatabaseListenerApi(DexFinder dexFinder, Logger logger) {
        this(dexFinder, null, logger);
    }

    public WeChatDatabaseListenerApi(DexFinder dexFinder, ClassLoader classLoader, Logger logger) {
        this.dexFinder = dexFinder;
        this.classLoader = classLoader;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return (dexFinder != null && dexFinder.sqliteDbWrapperClass != null) || classLoader != null;
    }

    public boolean isInstalled() {
        return isOperational();
    }

    public int hookedMethodCount() {
        refreshHookState();
        return hookedMethodCount;
    }

    public int hookedWrapperMutationMethodCount() {
        refreshHookState();
        return hookedWrapperMutationMethods.size();
    }

    public int hookedWrapperInsertMethodCount() {
        refreshHookState();
        return hookedWrapperInsertMethods.size();
    }

    public synchronized boolean isOperational() {
        refreshHookState();
        Class<?> wrapperClass = dexFinder != null ? dexFinder.sqliteDbWrapperClass : null;
        return wrapperClass != null
                && WeChatSqliteWrapperLocator.supportsMessageMutations(wrapperClass)
                && wrapperClass == hookedWrapperClass
                && hasActiveWrapperOperation(DatabaseChange.INSERT)
                && hasActiveWrapperOperation(DatabaseChange.UPDATE)
                && hasActiveWrapperOperation(DatabaseChange.DELETE);
    }

    public synchronized String operationalDiagnostics() {
        refreshHookState();
        Class<?> wrapperClass = dexFinder != null ? dexFinder.sqliteDbWrapperClass : null;
        return "wrapper=" + (wrapperClass != null ? wrapperClass.getName() : "null")
                + " shape=" + WeChatSqliteWrapperLocator.supportsMessageMutations(wrapperClass)
                + " insertHooks=" + hookedWrapperOperationCount(DatabaseChange.INSERT)
                + " updateHooks=" + hookedWrapperOperationCount(DatabaseChange.UPDATE)
                + " deleteHooks=" + hookedWrapperOperationCount(DatabaseChange.DELETE)
                + " hookGeneration=" + HookRegistry.get().installationGeneration()
                + " operational=" + isOperationalWithoutRefresh(wrapperClass);
    }

    public Subscription subscribe(Listener listener) {
        if (listener == null) return null;
        listeners.addIfAbsent(listener);
        return new Subscription(listener);
    }

    public void unsubscribe(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    public QuerySubscription subscribeQuery(QueryInterceptor interceptor) {
        if (interceptor == null) return null;
        queryInterceptors.addIfAbsent(interceptor);
        return new QuerySubscription(interceptor);
    }

    public void unsubscribeQuery(QueryInterceptor interceptor) {
        if (interceptor != null) queryInterceptors.remove(interceptor);
    }

    public synchronized void install() {
        Class<?> wrapperClass = dexFinder != null ? dexFinder.sqliteDbWrapperClass : null;
        refreshHookState();
        if (wrapperClass == null
                || !WeChatSqliteWrapperLocator.supportsMessageMutations(wrapperClass)) {
            if (hookedWrapperClass != null) {
                removeWrapperHooks();
                hookedWrapperClass = null;
            }
            HookRegistry registry = HookRegistry.get();
            long generation = registry.installationGeneration();
            int count = 0;
            if (registry.isInstallationGenerationActive(generation)) {
                count += hookNamedDatabaseClass("com.tencent.wcdb.database.SQLiteDatabase", generation);
                count += hookNamedDatabaseClass("com.tencent.wcdb.compat.SQLiteDatabase", generation);
                count += hookNamedDatabaseClass("android.database.sqlite.SQLiteDatabase", generation);
            }
            refreshHookState();
            installed = false;
            log("数据库变更监听等待 wrapper 定位: wrapper="
                    + (wrapperClass != null ? wrapperClass.getName() : "null")
                    + " added=" + count + " genericHooks=" + hookedMethodCount
                    + " operational=false");
            return;
        }
        if (isOperational()) return;
        if (wrapperClass != hookedWrapperClass) {
            removeWrapperHooks();
            hookedWrapperClass = wrapperClass;
        }
        HookRegistry registry = HookRegistry.get();
        long generation = registry.installationGeneration();
        int count = 0;
        Class<?> cur = wrapperClass;
        while (cur != null && cur != Object.class) {
            if (!registry.isInstallationGenerationActive(generation)) break;
            count += hookDatabaseClass(cur, true, generation);
            cur = cur.getSuperclass();
        }
        if (registry.isInstallationGenerationActive(generation)) {
            count += hookNamedDatabaseClass("com.tencent.wcdb.database.SQLiteDatabase", generation);
            count += hookNamedDatabaseClass("com.tencent.wcdb.compat.SQLiteDatabase", generation);
            count += hookNamedDatabaseClass("android.database.sqlite.SQLiteDatabase", generation);
        }
        refreshHookState();
        installed = isOperational();
        log("数据库变更监听Hook: wrapper=" + (wrapperClass != null ? wrapperClass.getName() : "null")
                + " added=" + count + " total=" + hookedMethodCount
                + " wrapperMutations=" + hookedWrapperMutationMethods.size()
                + " wrapperInserts=" + hookedWrapperInsertMethods.size()
                + " operational=" + installed);
    }

    public int hookedWrapperUpdateMethodCount() {
        refreshHookState();
        return hookedWrapperOperationCount(DatabaseChange.UPDATE);
    }

    public int hookedWrapperDeleteMethodCount() {
        refreshHookState();
        return hookedWrapperOperationCount(DatabaseChange.DELETE);
    }

    private synchronized void refreshHookState() {
        for (Map.Entry<Method, HookRegistration> entry : hookRegistrations.entrySet()) {
            if (!isRegistrationActive(entry.getValue())) {
                hookRegistrations.remove(entry.getKey(), entry.getValue());
            }
        }
        hookedWrapperMutationMethods.clear();
        hookedWrapperInsertMethods.clear();
        for (Map.Entry<Method, HookRegistration> entry : hookRegistrations.entrySet()) {
            HookRegistration registration = entry.getValue();
            if (!isRegistrationActive(registration) || !registration.wrapperMethod
                    || registration.operation == null) continue;
            hookedWrapperMutationMethods.add(entry.getKey());
            if (DatabaseChange.INSERT.equals(registration.operation)) {
                hookedWrapperInsertMethods.add(entry.getKey());
            }
        }
        hookedMethodCount = hookRegistrations.size();
        Class<?> wrapperClass = dexFinder != null ? dexFinder.sqliteDbWrapperClass : null;
        installed = isOperationalWithoutRefresh(wrapperClass);
    }

    private boolean isOperationalWithoutRefresh(Class<?> wrapperClass) {
        return wrapperClass != null
                && wrapperClass == hookedWrapperClass
                && WeChatSqliteWrapperLocator.supportsMessageMutations(wrapperClass)
                && hasActiveWrapperOperation(DatabaseChange.INSERT)
                && hasActiveWrapperOperation(DatabaseChange.UPDATE)
                && hasActiveWrapperOperation(DatabaseChange.DELETE);
    }

    private boolean hasActiveWrapperOperation(String operation) {
        return hookedWrapperOperationCount(operation) > 0;
    }

    private int hookedWrapperOperationCount(String operation) {
        int count = 0;
        for (HookRegistration registration : hookRegistrations.values()) {
            if (registration.wrapperMethod
                    && operation.equals(registration.operation)
                    && isRegistrationActive(registration)) count++;
        }
        return count;
    }

    private boolean isRegistrationActive(HookRegistration registration) {
        return registration != null
                && HookRegistry.get().isInstallationGenerationActive(registration.generation)
                && HookRegistry.get().isActive(registration.handle);
    }

    private void removeWrapperHooks() {
        HookRegistry registry = HookRegistry.get();
        for (Map.Entry<Method, HookRegistration> entry : hookRegistrations.entrySet()) {
            HookRegistration registration = entry.getValue();
            if (!registration.wrapperMethod) continue;
            hookRegistrations.remove(entry.getKey(), registration);
            try {
                registry.unhook(registration.handle);
            } catch (Throwable ignored) {}
        }
        hookedWrapperMutationMethods.clear();
        hookedWrapperInsertMethods.clear();
    }

    private static final class HookRegistration {
        final HookHandle handle;
        final long generation;
        final String operation;
        final boolean wrapperMethod;

        HookRegistration(HookHandle handle, long generation, String operation, boolean wrapperMethod) {
            this.handle = handle;
            this.generation = generation;
            this.operation = operation;
            this.wrapperMethod = wrapperMethod;
        }
    }

    private int hookNamedDatabaseClass(String className, long generation) {
        if (classLoader == null) return 0;
        try {
            return hookDatabaseClass(KavaReflector.loadClass(className, classLoader), false, generation);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int hookDatabaseClass(Class<?> dbClass, boolean allowObfuscatedWrapperMethod,
                                  long generation) {
        if (dbClass == null) return 0;
        int count = 0;
        for (Method method : KavaReflector.declaredMethods(dbClass)) {
            if (!HookRegistry.get().isInstallationGenerationActive(generation)) break;
            String operation = WeChatSqliteWrapperLocator.operationOf(method, allowObfuscatedWrapperMethod);
            boolean query = isRawQueryMethod(method);
            if (operation == null && !query) continue;
            HookRegistration existing = hookRegistrations.get(method);
            if (isRegistrationActive(existing)) {
                if (allowObfuscatedWrapperMethod && operation != null && !existing.wrapperMethod) {
                    hookRegistrations.replace(method, existing,
                            new HookRegistration(existing.handle, existing.generation, operation, true));
                }
                continue;
            }
            if (existing != null) hookRegistrations.remove(method, existing);
            try {
                HookHandle handle = HookRegistry.get().hook(
                                method,
                                chain -> {
                                    Object[] args = chain.getArgs().toArray();
                                    if (query) {
                                        boolean outerQuery = enterQuery();
                                        try {
                                            if (outerQuery) transformQuery(args);
                                            return chain.proceed(args);
                                        } finally {
                                            leaveQuery();
                                        }
                                    }
                                    enterMutation();
                                    Object result;
                                    boolean outerMutation;
                                    try {
                                        result = chain.proceed(args);
                                    } finally {
                                        outerMutation = leaveMutation();
                                    }
                                    // Only a completed outer mutation may publish changes. Host
                                    // failures propagate.
                                    if (outerMutation)
                                        dispatchIfSuccessful(operation, method, args, result);
                                    return result;
                                },
                                XposedInterface.PRIORITY_LOWEST);
                HookRegistration registration =
                        new HookRegistration(handle, generation, operation, allowObfuscatedWrapperMethod);
                if (isRegistrationActive(registration)) {
                    hookRegistrations.put(method, registration);
                    count++;
                } else {
                    try {
                        HookRegistry.get().unhook(handle);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable error) {
                log("数据库方法Hook失败: " + method + " " + error.getMessage());
            }
        }
        return count;
    }

    private boolean isRawQueryMethod(Method method) {
        if (method == null || !Cursor.class.isAssignableFrom(method.getReturnType())) return false;
        String name = method.getName();
        if (!"rawQuery".equals(name) && !"rawQueryWithFactory".equals(name)) return false;
        for (Class<?> type : method.getParameterTypes()) {
            if (type == String.class) return true;
        }
        return false;
    }

    private boolean enterQuery() {
        int depth = queryDepth.get();
        queryDepth.set(depth + 1);
        return depth == 0;
    }

    private void leaveQuery() {
        int depth = queryDepth.get();
        if (depth <= 1) {
            queryDepth.remove();
        } else {
            queryDepth.set(depth - 1);
        }
    }

    private void transformQuery(Object[] args) {
        if (args == null || queryInterceptors.isEmpty()) return;
        int sqlIndex = -1;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String) {
                sqlIndex = i;
                break;
            }
        }
        if (sqlIndex < 0) return;
        String sql = (String) args[sqlIndex];
        if (TextUtils.isEmpty(sql)) return;
        String rewritten = sql;
        for (QueryInterceptor interceptor : queryInterceptors) {
            try {
                String next = interceptor.onQuery(rewritten);
                if (next != null) rewritten = next;
            } catch (Throwable error) {
                log("数据库查询拦截器异常: " + error.getMessage());
            }
        }
        if (!rewritten.equals(sql)) args[sqlIndex] = rewritten;
    }

    private void enterMutation() {
        mutationDepth.set(mutationDepth.get() + 1);
    }

    private boolean leaveMutation() {
        int depth = mutationDepth.get();
        if (depth <= 1) {
            mutationDepth.remove();
            return true;
        }
        mutationDepth.set(depth - 1);
        return false;
    }

    private void dispatchIfSuccessful(String operation, Method method, Object[] args, Object result) {
        if (listeners.isEmpty() || args == null) return;
        long resultValue = resultToLong(result);
        if (DatabaseChange.INSERT.equals(operation)) {
            if (resultValue < 0) return;
            dispatch(new DatabaseChange(
                    operation,
                    tableArg(args),
                    nullColumnHackArg(args),
                    contentValuesArg(args),
                    null,
                    null,
                    resultValue,
                    method.getName()));
            return;
        }
        if (DatabaseChange.UPDATE.equals(operation)) {
            if (resultValue <= 0) return;
            dispatch(new DatabaseChange(
                    operation,
                    tableArg(args),
                    null,
                    contentValuesArg(args),
                    whereClauseArg(args),
                    stringArrayArg(args),
                    resultValue,
                    method.getName()));
            return;
        }
        if (DatabaseChange.DELETE.equals(operation)) {
            if (resultValue <= 0 || args.length < 3) return;
            dispatch(new DatabaseChange(
                    operation,
                    tableArg(args),
                    null,
                    null,
                    whereClauseArgForDelete(args),
                    stringArrayArg(args),
                    resultValue,
                    method.getName()));
        }
    }

    private void dispatch(DatabaseChange change) {
        for (Listener listener : listeners) {
            try {
                listener.onDatabaseChanged(change);
            } catch (Throwable e) {
                log("数据库变更监听回调失败: " + e.getMessage());
            }
        }
    }

    private long resultToLong(Object result) {
        return result instanceof Number ? ((Number) result).longValue() : 0;
    }

    private String tableArg(Object[] args) {
        if (args == null || args.length == 0) return "";
        if (args[0] instanceof String) return (String) args[0];
        for (Object arg : args) {
            if (!(arg instanceof String)) continue;
            String value = (String) arg;
            if ("message".equalsIgnoreCase(value) || value.toLowerCase().contains("message"))
                return value;
        }
        return "";
    }

    private String nullColumnHackArg(Object[] args) {
        if (args == null) return "";
        for (int i = 1; i < args.length; i++) {
            Object value = args[i];
            if (value instanceof String) return (String) value;
            if (value instanceof ContentValues) return "";
        }
        return "";
    }

    private ContentValues contentValuesArg(Object[] args) {
        if (args == null) return null;
        for (Object value : args) {
            if (value instanceof ContentValues) return (ContentValues) value;
        }
        return null;
    }

    private String whereClauseArg(Object[] args) {
        if (args == null) return "";
        boolean afterValues = false;
        for (Object value : args) {
            if (value instanceof ContentValues) {
                afterValues = true;
                continue;
            }
            if (afterValues && value instanceof String) return (String) value;
        }
        return "";
    }

    private String whereClauseArgForDelete(Object[] args) {
        if (args == null) return "";
        for (int i = 1; i < args.length; i++) {
            Object value = args[i];
            if (value instanceof String) return (String) value;
        }
        return "";
    }

    private String[] stringArrayArg(Object[] args) {
        if (args == null) return null;
        Object value = null;
        for (Object arg : args) {
            if (arg instanceof String[]) {
                value = arg;
                break;
            }
        }
        return value instanceof String[] ? (String[]) value : null;
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatDatabaseListenerApi] " + message);
    }
}
