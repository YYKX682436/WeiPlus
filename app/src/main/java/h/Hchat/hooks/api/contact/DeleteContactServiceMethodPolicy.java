package h.Hchat.hooks.api.contact;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Exact reflection signatures for the obfuscated native DeleteContactService methods. */
public final class DeleteContactServiceMethodPolicy {
    private DeleteContactServiceMethodPolicy() {}

    /** The caller must also match the official, method-local DeleteContactService string anchors. */
    public static boolean isSubmitMethod(Method method) {
        if (method == null || Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) {
            return false;
        }
        Class<?>[] parameters = method.getParameterTypes();
        return parameters.length == 2
                && parameters[0] == String.class
                && parameters[1] == boolean.class;
    }

    /** Validates the exact server-result handler associated with the located service class. */
    public static boolean isResponseMethod(Method method, Class<?> serviceClass) {
        if (method == null || serviceClass == null || method.getDeclaringClass() != serviceClass
                || Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) {
            return false;
        }
        Class<?>[] parameters = method.getParameterTypes();
        return parameters.length == 3
                && parameters[0] == String.class
                && parameters[1] == int.class
                && parameters[2] == boolean.class;
    }
}
