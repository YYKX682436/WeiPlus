package android.content;
import java.util.HashMap;
public final class ContentValues extends HashMap<String,Object> {
 public ContentValues(){}
 public ContentValues(ContentValues value){super(value);}
 public String getAsString(String key){Object v=get(key);return v==null?null:v.toString();}
 public byte[] getAsByteArray(String key){return (byte[])get(key);}
}
