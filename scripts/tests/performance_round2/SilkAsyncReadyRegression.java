import android.content.Context;
import h.Hchat.loader.utils.NativeLibraryLoader;
import h.Hchat.loader.utils.SilkLibrary;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class SilkAsyncReadyRegression {
    static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Context context=new Context(); SilkLibrary.initialize(context);
        check(!SilkLibrary.isReady() && NativeLibraryLoader.attempts==0,"initialization loaded audio");
        NativeLibraryLoader.fail=true;
        CountDownLatch failure=new CountDownLatch(1); AtomicInteger errors=new AtomicInteger();
        SilkLibrary.whenReady(ok -> { if (!ok) errors.incrementAndGet(); failure.countDown(); });
        check(failure.await(3,TimeUnit.SECONDS) && errors.get()==1,"failed async load did not complete normally");
        check(!SilkLibrary.isReady(),"failed load was cached");
        NativeLibraryLoader.fail=false;
        CountDownLatch success=new CountDownLatch(50); AtomicInteger successes=new AtomicInteger();
        for (int i=0;i<50;i++) SilkLibrary.whenReady(ok -> { if (ok) successes.incrementAndGet(); success.countDown(); });
        check(success.await(3,TimeUnit.SECONDS) && successes.get()==50,"async waiters did not all complete");
        check(SilkLibrary.isReady() && NativeLibraryLoader.attempts==2,"async retry duplicated or failed");
        AtomicInteger immediate=new AtomicInteger(); SilkLibrary.whenReady(ok -> { if(ok) immediate.incrementAndGet(); });
        check(immediate.get()==1,"ready callback was unnecessarily delayed");
        check(context.getApplicationContext()==context,"keep test application context alive");
        System.out.println("PASS: asynchronous readiness delivers failure, retries once, and completes concurrent waiters");
    }
}
