package h.Hchat.hooks.api.ui;
import android.content.Intent;
import java.lang.reflect.Method;
public final class WeChatActivityStartApi {
 public interface Listener {void onStart(Intent intent,Object caller,Method method);}
 public void subscribe(Listener listener){}
}
