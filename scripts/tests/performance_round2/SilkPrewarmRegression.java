import android.content.Context;
import h.Hchat.loader.utils.NativeLibraryLoader;
import h.Hchat.loader.utils.SilkLibrary;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
public final class SilkPrewarmRegression {
    static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    static boolean warming() throws Exception {
        Field field=SilkLibrary.class.getDeclaredField("prewarming"); field.setAccessible(true);
        return ((AtomicBoolean)field.get(null)).get();
    }
    static void waitDone() throws Exception {
        long until=System.nanoTime()+3_000_000_000L;
        while (warming() && System.nanoTime()<until) Thread.sleep(2);
        check(!warming(),"prewarm did not finish");
    }
    public static void main(String[] args) throws Exception {
        Context context = new Context();
        SilkLibrary.initialize(context);
        check(NativeLibraryLoader.attempts==0,"initialization loaded native code");
        NativeLibraryLoader.fail=true; SilkLibrary.prewarm(); waitDone();
        check(NativeLibraryLoader.attempts==1 && !NativeLibraryLoader.loaded,"failed prewarm was not recorded");
        check("Hchat-Silk-Prewarm".equals(NativeLibraryLoader.threadName),"prewarm ran on caller thread");
        NativeLibraryLoader.fail=false; SilkLibrary.prewarm(); waitDone();
        check(NativeLibraryLoader.attempts==2 && NativeLibraryLoader.loaded,"failed prewarm did not retry");
        for (int i=0;i<20;i++) SilkLibrary.prewarm();
        check(NativeLibraryLoader.attempts==2 && !warming(),"success spawned repeated prewarm work");
        check(context.getApplicationContext()==context,"test application context released");
        System.out.println("PASS: actual audio demand prewarms off caller thread and retries failures");
    }
}
