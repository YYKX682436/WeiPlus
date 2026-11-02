package h.Hchat.loader.utils;
import android.content.Context;
import java.util.concurrent.CountDownLatch;
public class NativeLibraryLoader {
  private static final NativeLoadCache CACHE = new NativeLoadCache();
  public static final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
  public boolean loadSilkCodec(Context ctx, ClassLoader loader) {
    return CACHE.load(loader,"silk",()->{
      started.countDown();
      try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
      return true;
    });
  }
}
