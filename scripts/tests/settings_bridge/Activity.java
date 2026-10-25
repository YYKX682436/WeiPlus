package android.app;
import android.content.Intent;
import java.util.List;
import java.util.ArrayList;
public class Activity {
 public static final int RESULT_OK=-1,RESULT_CANCELED=0;
 private final Application application=new Application();
 public boolean failLaunch;
 public final List<Integer> requests=new ArrayList<>();
 public Application getApplication(){return application;}
 public void startActivityForResult(Intent value,int requestCode){if(failLaunch)throw new IllegalStateException("start picker failed");requests.add(requestCode);}
 public void onActivityResult(int requestCode,int resultCode,Intent data){}
}
