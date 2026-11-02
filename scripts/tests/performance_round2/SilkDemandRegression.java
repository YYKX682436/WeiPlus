import android.content.Context;
import h.Hchat.loader.utils.NativeLibraryLoader;
import h.Hchat.loader.utils.SilkLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import me.yun.silk.SilkCodec;
public final class SilkDemandRegression {
    static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        Context context = new Context();
        SilkLibrary.initialize(context);
        check(NativeLibraryLoader.attempts == 0, "initialization without demand loaded Silk");
        NativeLibraryLoader.fail=true;
        boolean failed=false;
        try { new SilkCodec(); } catch (IllegalStateException expected) { failed=true; }
        check(failed && NativeLibraryLoader.attempts==1, "first failure was hidden or not attempted");
        NativeLibraryLoader.fail=false;
        ExecutorService pool=Executors.newFixedThreadPool(12);
        CountDownLatch gate=new CountDownLatch(1);
        List<Future<?>> futures=new ArrayList<>();
        try {
            for (int i=0;i<80;i++) futures.add(pool.submit(() -> { gate.await(); return new SilkCodec(); }));
            gate.countDown();
            for (Future<?> future:futures) check(future.get(5,TimeUnit.SECONDS)!=null, "constructor failed");
        } finally { pool.shutdownNow(); }
        check(NativeLibraryLoader.attempts==2, "failed load was cached or concurrent loads duplicated");
        check(NativeLibraryLoader.seenLoader==SilkCodec.class.getClassLoader(), "incorrect module class loader");
        check(context.getApplicationContext()==context, "test application context released");
        System.out.println("PASS: no demand is idle; failed first demand retries; concurrent demand shares loader cache");
    }
}
