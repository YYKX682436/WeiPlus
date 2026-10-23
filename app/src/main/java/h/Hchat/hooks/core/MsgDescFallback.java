package h.Hchat.hooks.core;

import java.lang.reflect.Method;

/** Only the native summary's own NPE is recoverable. Never invoke or bypass the hook chain. */
public final class MsgDescFallback {
    private MsgDescFallback() {}

    public static boolean isTargetFailure(Method method, Throwable failure) {
        if (method == null || !(failure instanceof NullPointerException)) return false;
        // Confirmed in 8.0.76 pt0.r.l: nullable app-message description -> String.isEmpty().
        String message = failure.getMessage();
        if (message == null || !message.contains("java.lang.String.isEmpty()")) return false;
        StackTraceElement[] trace = failure.getStackTrace();
        return trace.length > 0
                && method.getDeclaringClass().getName().equals(trace[0].getClassName())
                && method.getName().equals(trace[0].getMethodName());
    }
}
