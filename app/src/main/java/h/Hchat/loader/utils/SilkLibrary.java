package h.Hchat.loader.utils;

import android.content.Context;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import me.yun.silk.SilkCodec;

/** Provides the host context without loading native code until an audio operation needs it. */
public final class SilkLibrary {
    private static volatile WeakReference<Context> hostContext = new WeakReference<>(null);
    private static volatile boolean ready;
    private static final AtomicBoolean prewarming = new AtomicBoolean();
    private static final List<Consumer<Boolean>> waiters = new ArrayList<>();

    private SilkLibrary() {}

    public static void initialize(Context context) {
        Context application = context.getApplicationContext();
        hostContext = new WeakReference<>(application != null ? application : context);
    }

    public static void ensureLoaded() {
        if (ready) return;
        Context context = hostContext.get();
        if (context == null || !new NativeLibraryLoader().loadSilkCodec(
                context, SilkCodec.class.getClassLoader())) {
            throw new IllegalStateException("Silk 音频库加载失败");
        }
        ready = true;
    }

    public static boolean isReady() { return ready; }

    /** Called only after a real audio request is known; failures remain retryable. */
    public static void prewarm() { whenReady(null); }

    /** Notify callers without blocking their thread while an existing load is in progress. */
    public static void whenReady(Consumer<Boolean> callback) {
        boolean alreadyReady;
        boolean start = false;
        synchronized (prewarming) {
            alreadyReady = ready;
            if (!alreadyReady) {
                if (callback != null) waiters.add(callback);
                if (!prewarming.get()) {
                    prewarming.set(true);
                    start = true;
                }
            }
        }
        if (alreadyReady) {
            notifyWaiter(callback, true);
            return;
        }
        if (!start) return;
        try {
            Thread thread = new Thread(() -> {
                boolean success = false;
                try { ensureLoaded(); success = true; }
                catch (Throwable ignored) { /* a later audio request can retry */ }
                finishPrewarm(success);
            }, "Hchat-Silk-Prewarm");
            thread.setDaemon(true);
            thread.start();
        } catch (Throwable failure) {
            finishPrewarm(false);
        }
    }

    private static void finishPrewarm(boolean success) {
        List<Consumer<Boolean>> callbacks;
        synchronized (prewarming) {
            prewarming.set(false);
            callbacks = new ArrayList<>(waiters);
            waiters.clear();
        }
        for (Consumer<Boolean> callback : callbacks) notifyWaiter(callback, success);
    }

    private static void notifyWaiter(Consumer<Boolean> callback, boolean success) {
        if (callback == null) return;
        try { callback.accept(success); } catch (Throwable ignored) { /* isolate callers */ }
    }
}
