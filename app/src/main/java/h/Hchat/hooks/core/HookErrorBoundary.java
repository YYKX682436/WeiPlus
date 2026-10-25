package h.Hchat.hooks.core;

import android.util.Log;

/** Isolates optional module work only. Never place Chain.proceed or host calls in this action. */
public final class HookErrorBoundary {
    private HookErrorBoundary() {}

    public static void run(String message, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            try {
                XposedRuntime.get().log(Log.ERROR, "Hchat", message, error);
            } catch (Throwable ignored) {
                // A broken diagnostic channel must not replace the already completed host result.
            }
        }
    }
}
