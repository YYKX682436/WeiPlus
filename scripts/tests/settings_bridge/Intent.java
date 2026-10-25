package android.content;
import android.net.Uri;
public class Intent {
 public static final String ACTION_CREATE_DOCUMENT="create",ACTION_OPEN_DOCUMENT="open",ACTION_GET_CONTENT="get",CATEGORY_OPENABLE="openable",EXTRA_TITLE="title";
 public static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_GRANT_WRITE_URI_PERMISSION=2;
 private String type;
 private Uri data;
 public Intent(){}
 public Intent(String action){}
 public String getType(){return type;}
 public void setType(String value){type=value;}
 public Uri getData(){return data;}
 public void setData(Uri value){data=value;}
 public Intent addCategory(String value){return this;}
 public Intent addFlags(int value){return this;}
 public Intent putExtra(String key,String value){return this;}
 public static Intent createChooser(Intent value,String title){return value;}
}
