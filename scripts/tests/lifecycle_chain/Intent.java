package android.content;
import java.util.HashMap;
public class Intent {
 private final HashMap<String,String> values=new HashMap<>();
 public Intent putExtra(String key,String value){values.put(key,value);return this;}
 public String getStringExtra(String key){return values.get(key);}
 public ComponentName getComponent(){return null;}
}
