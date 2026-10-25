package android.widget;
import android.app.Activity;
public class Toast {
 public static final int LENGTH_SHORT=0,LENGTH_LONG=1;
 public static Toast makeText(Activity activity,String text,int duration){return new Toast();}
 public void show(){}
}
