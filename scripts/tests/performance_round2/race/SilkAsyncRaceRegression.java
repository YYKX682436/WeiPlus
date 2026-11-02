import h.Hchat.loader.utils.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class SilkAsyncRaceRegression {
    public static void main(String[] args) throws Exception {
        android.content.Context context=new android.content.Context(); SilkLibrary.initialize(context);
        SilkLibrary.prewarm();
        if (!NativeLibraryLoader.started.await(3,TimeUnit.SECONDS)) throw new AssertionError("prewarm did not begin");
        CountDownLatch callback=new CountDownLatch(1); AtomicBoolean result=new AtomicBoolean();
        Thread ui=new Thread(() -> SilkLibrary.whenReady(ok -> { result.set(ok); callback.countDown(); }),"simulated-main");
        ui.start(); ui.join(500);
        if (ui.isAlive()) throw new AssertionError("main caller waited for in-flight native load");
        if (callback.getCount()!=1) throw new AssertionError("callback arrived before native load completed");
        NativeLibraryLoader.release.countDown();
        if (!callback.await(3,TimeUnit.SECONDS) || !result.get()) throw new AssertionError("completion was lost");
        if (context.getApplicationContext()!=context) throw new AssertionError("test context expired");
        System.out.println("PASS: first main-thread readiness request returns while prewarm holds the native cache lock");
    }
}
