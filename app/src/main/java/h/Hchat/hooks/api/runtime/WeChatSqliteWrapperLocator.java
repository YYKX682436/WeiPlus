package h.Hchat.hooks.api.runtime;

import android.content.ContentValues;

import h.Hchat.utils.KavaReflector;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural validation for the host's SqliteDB mutation wrapper.
 *
 * <p>Dex strings are only candidate anchors. A candidate is usable only when the same class
 * contains the insert, update, and delete parameter/return shapes consumed by the listener.</p>
 */
public final class WeChatSqliteWrapperLocator {
    public static final String INSERT = "insert";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";

    private WeChatSqliteWrapperLocator() {}

    public static String operationOf(Method method, boolean allowObfuscatedWrapperMethod) {
        if (method == null) return null;
        Class<?>[] params = method.getParameterTypes();
        Class<?> returnType = method.getReturnType();
        String name = method.getName();
        if (returnType == long.class && hasContentValuesArg(params)
                && ("insert".equals(name)
                || "insertWithOnConflict".equals(name)
                || "replace".equals(name)
                || "replaceOrThrow".equals(name))) {
            return INSERT;
        }
        if (returnType == int.class && hasContentValuesArg(params)
                && ("update".equals(name) || "updateWithOnConflict".equals(name))) {
            return UPDATE;
        }
        if ("delete".equals(name)
                && returnType == int.class
                && params.length == 3
                && params[0] == String.class
                && params[1] == String.class
                && params[2] == String[].class) {
            return DELETE;
        }
        if (!allowObfuscatedWrapperMethod) return null;
        if (returnType == long.class && isWrapperInsertSignature(params)) return INSERT;
        if (returnType == int.class && isWrapperUpdateSignature(params)) return UPDATE;
        if (returnType == int.class && isWrapperDeleteSignature(params)) return DELETE;
        return null;
    }

    public static boolean supportsMessageMutations(Class<?> candidate) {
        if (candidate == null) return false;
        boolean insert = false;
        boolean update = false;
        boolean delete = false;
        for (Class<?> current = candidate; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method method : KavaReflector.declaredMethods(current)) {
                String operation = operationOf(method, true);
                insert |= INSERT.equals(operation);
                update |= UPDATE.equals(operation);
                delete |= DELETE.equals(operation);
            }
        }
        return insert && update && delete;
    }

    /** Preserve the first query's ordering while requiring membership in every anchor query. */
    public static Set<String> intersectCandidateNames(List<String> first, List<String> second) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (first == null || second == null) return result;
        Set<String> secondNames = new LinkedHashSet<>(second);
        for (String name : first) {
            if (name != null && secondNames.contains(name)) result.add(name);
        }
        return result;
    }

    /** Select only an unambiguous structurally complete wrapper. */
    public static Class<?> selectUniqueSupportedCandidate(Iterable<Class<?>> candidates) {
        if (candidates == null) return null;
        Class<?> selected = null;
        for (Class<?> candidate : candidates) {
            if (!supportsMessageMutations(candidate)) continue;
            if (selected != null && selected != candidate) return null;
            selected = candidate;
        }
        return selected;
    }

    private static boolean isWrapperInsertSignature(Class<?>[] params) {
        return params != null
                && params.length == 3
                && params[0] == String.class
                && params[1] == String.class
                && ContentValues.class.isAssignableFrom(params[2]);
    }

    private static boolean isWrapperUpdateSignature(Class<?>[] params) {
        return params != null
                && params.length == 4
                && params[0] == String.class
                && ContentValues.class.isAssignableFrom(params[1])
                && params[2] == String.class
                && params[3] == String[].class;
    }

    private static boolean isWrapperDeleteSignature(Class<?>[] params) {
        return params != null
                && params.length == 3
                && params[0] == String.class
                && params[1] == String.class
                && params[2] == String[].class;
    }

    private static boolean hasContentValuesArg(Class<?>[] params) {
        if (params == null) return false;
        for (Class<?> param : params) {
            if (ContentValues.class.isAssignableFrom(param)) return true;
        }
        return false;
    }
}
