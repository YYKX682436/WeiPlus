package android.app;
import android.content.Intent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
public class Activity {
 public static final int RESULT_OK=-1,RESULT_CANCELED=0;
 public final List<Integer> requestCodes=new CopyOnWriteArrayList<>();
 public boolean failLaunch;
 public Activity getApplicationContext(){return this;}
 public boolean isFinishing(){return false;}
 public boolean isDestroyed(){return false;}
 public void startActivityForResult(Intent intent,int code){if(failLaunch)throw new IllegalStateException("picker unavailable");requestCodes.add(code);}
 public void runOnUiThread(Runnable action){action.run();}
 public void onActivityResult(int code,int result,Intent data){}
 public void onDestroy(){}
}
