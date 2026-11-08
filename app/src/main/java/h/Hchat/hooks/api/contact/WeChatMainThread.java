package h.Hchat.hooks.api.contact;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Runs short native storage entry points on WeChat's UI looper while callers wait off-main. */
public final class WeChatMainThread {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private WeChatMainThread() {}

    public static <T> T call(Callable<T> action) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) return action.call();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        if (!MAIN.post(() -> {
            try {
                value.set(action.call());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        })) {
            throw new IllegalStateException("微信主线程不可用");
        }
        try {
            completed.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
        Throwable error = failure.get();
        if (error instanceof Exception) throw (Exception) error;
        if (error != null) throw new RuntimeException(error);
        return value.get();
    }
}
