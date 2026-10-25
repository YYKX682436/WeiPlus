package android.content;
import android.net.Uri;
public class Intent {
 public static final String ACTION_OPEN_DOCUMENT="open",ACTION_GET_CONTENT="get",CATEGORY_OPENABLE="openable";
 public static final int FLAG_GRANT_READ_URI_PERMISSION=1;
 private String type;
 private Uri data;
 public Intent(String action){}
 public Intent addCategory(String category){return this;}
 public Intent addFlags(int flags){return this;}
 public String getType(){return type;}
 public void setType(String value){type=value;}
 public Uri getData(){return data;}
 public static Intent createChooser(Intent value,String title){return value;}
}
