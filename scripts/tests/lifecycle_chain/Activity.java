package android.app;
import android.content.Intent;
public class Activity {
 private Intent intent=new Intent();
 public Intent getIntent(){return intent;}
 public void setIntent(Intent value){intent=value;}
 public void onResume(){}
 public void onPause(){}
 public void onDestroy(){}
}
