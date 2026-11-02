package h.Hchat.loader.utils;
import android.content.Context;
public class NativeLibraryLoader {
    public static volatile int attempts;
    public static volatile boolean fail, loaded;
    public static volatile String threadName;
    public static ClassLoader seenLoader;
    public synchronized boolean loadSilkCodec(Context context, ClassLoader loader) {
        synchronized (NativeLibraryLoader.class) {
            if (loaded) return true;
            attempts++; seenLoader=loader; threadName=Thread.currentThread().getName();
            if (fail) return false;
            loaded=true; return true;
        }
    }
}
