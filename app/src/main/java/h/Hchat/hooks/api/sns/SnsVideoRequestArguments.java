package h.Hchat.hooks.api.sns;

import java.lang.reflect.Method;

/** Argument builder for the verified legacy and fifth-fingerprint SNS video helpers. */
public final class SnsVideoRequestArguments {
    private SnsVideoRequestArguments() {}

    public static Object[] create(Method method, String path, String thumbPath,
                                  String description, String fileMd5) {
        if (method == null || method.getReturnType() != boolean.class) return null;
        Class<?>[] types = method.getParameterTypes();
        if (types.length != 4 && types.length != 5) return null;
        for (Class<?> type : types) if (type != String.class) return null;
        Object[] args = new Object[]{path, thumbPath, description, fileMd5};
        // The standard WeChat SightWidget caller supplies an empty rawFingerPrintForXiaoWei.
        if (types.length == 5) return new Object[]{path, thumbPath, description, fileMd5, ""};
        return args;
    }
}
