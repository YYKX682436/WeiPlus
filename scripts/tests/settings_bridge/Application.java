package android.app;
import android.os.Bundle;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
public class Application {
 public interface ActivityLifecycleCallbacks {
  void onActivityCreated(Activity value,Bundle state);void onActivityStarted(Activity value);void onActivityResumed(Activity value);void onActivityPaused(Activity value);void onActivityStopped(Activity value);void onActivitySaveInstanceState(Activity value,Bundle state);void onActivityDestroyed(Activity value);
 }
 public final List<ActivityLifecycleCallbacks> callbacks=new CopyOnWriteArrayList<>();
 public boolean failRegister,failUnregister;
 public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks value){if(failRegister)throw new IllegalStateException("register failed");callbacks.add(value);}
 public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks value){if(failUnregister)throw new IllegalStateException("unregister failed");callbacks.remove(value);}
}
