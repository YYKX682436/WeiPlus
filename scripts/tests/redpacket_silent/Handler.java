package android.os;

import java.util.IdentityHashMap;
import java.util.Map;

public final class Handler {
    private static final h.Hchat.hooks.api.runtime.WeChatTaskApi tasks = new h.Hchat.hooks.api.runtime.WeChatTaskApi();
    private static final Map<Runnable, String> keys = new IdentityHashMap<>();
    private static boolean uiClock;
    private static long next;
    public Handler(Looper looper) {}
    public static void reset() { uiClock = true; tasks.advanceBy(100000); keys.clear(); next = 0; }
    public static void advanceBy(long elapsed) { tasks.advanceBy(elapsed); }
    public boolean postDelayed(Runnable task, long delay) {
        if (!uiClock) throw new AssertionError("Tests must use the controlled WeChatTaskApi clock");
        String key = "ui:" + next++;
        keys.put(task, key);
        tasks.runOnMainDelayed(key, delay, () -> {
            keys.remove(task);
            task.run();
        });
        return true;
    }
    public void removeCallbacks(Runnable task) {
        String key = keys.remove(task);
        if (key != null) tasks.cancel(key);
    }
}
