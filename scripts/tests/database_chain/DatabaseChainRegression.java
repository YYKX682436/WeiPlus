import android.content.ContentValues;
import android.database.Cursor;
import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.api.runtime.WeChatDatabaseListenerApi;
import h.Hchat.hooks.api.model.DatabaseChange;
import h.Hchat.hooks.core.HookRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseChainRegression {
 public static final class Database {
  public long insert(String table,String empty,ContentValues values){throw new AssertionError("Host body provided by invocation boundary");}
  public int update(String table,ContentValues values,String where,String[] args){throw new AssertionError();}
  public Cursor rawQuery(String sql,String[] args){throw new AssertionError();}
 }
 static final Database owner=new Database();
 static final HookRegistry registry=HookRegistry.get();
 static final Method insert,update,query;
 static {try{
  insert=Database.class.getMethod("insert",String.class,String.class,ContentValues.class);
  update=Database.class.getMethod("update",String.class,ContentValues.class,String.class,String[].class);
  query=Database.class.getMethod("rawQuery",String.class,String[].class);
 }catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
 static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 static Object[] insertArgs(){return new Object[]{"message",null,new ContentValues()};}
 static void depths(WeChatDatabaseListenerApi api)throws Exception {
  for(String name:List.of("queryDepth","mutationDepth")){
   Field field=WeChatDatabaseListenerApi.class.getDeclaredField(name);field.setAccessible(true);
   check(((ThreadLocal<?>)field.get(api)).get().equals(0),name+" leaked into next invocation");
  }
 }
 public static void main(String[] args)throws Throwable {
  DexFinder finder=new DexFinder();finder.sqliteDbWrapperClass=Database.class;
  List<String> logs=new ArrayList<>();
  WeChatDatabaseListenerApi api=new WeChatDatabaseListenerApi(finder,logs::add);
  List<DatabaseChange> changes=new ArrayList<>();api.subscribe(changes::add);
  int[] rewrites={0},originals={0};
  api.subscribeQuery(sql->{rewrites[0]++;return sql+" /* filtered */";});
  api.install();api.install();check(api.isOperational(),"Wrapper insert was not installed");
  check(api.hookedWrapperMutationMethodCount()==2,"Mutation method coverage changed");
  registry.invoke(insert,owner,insertArgs(),(receiver,parameters)->{originals[0]++;return 7L;});
  check(originals[0]==1&&changes.size()==1&&changes.get(0).result==7,"Successful insert not dispatched once");depths(api);
  IllegalStateException failure=new IllegalStateException("host write failed");
  try{registry.invoke(insert,owner,insertArgs(),(receiver,parameters)->{originals[0]++;throw failure;});throw new AssertionError();}
  catch(IllegalStateException actual){check(actual==failure,"Host mutation exception identity changed");}
  check(originals[0]==2&&changes.size()==1,"Failed insert retried or dispatched");depths(api);
  Object[] updateArgs={"message",new ContentValues(),"msgId=?",new String[]{"7"}};
  registry.invoke(update,owner,updateArgs,(receiver,parameters)->{
   originals[0]++;
   registry.invoke(insert,owner,insertArgs(),(r,p)->{originals[0]++;return 9L;});
   return 1;
  });
  check(originals[0]==4&&changes.size()==2&&changes.get(1).operation.equals(DatabaseChange.UPDATE),"Nested mutation dispatched twice");depths(api);
  registry.invoke(update,owner,updateArgs,(receiver,parameters)->{
   try{registry.invoke(insert,owner,insertArgs(),(r,p)->{throw failure;});throw new AssertionError();}
   catch(IllegalStateException actual){check(actual==failure,"Inner mutation exception changed");}
   return 1;
  });
  check(changes.size()==3,"Handled inner failure poisoned its outer mutation");depths(api);
  String[] sqls={null,null};
  Object[] originalQuery={"SELECT outer",new String[0]};
  registry.invoke(query,owner,originalQuery,(receiver,parameters)->{
   sqls[0]=(String)parameters[0];
   registry.invoke(query,owner,new Object[]{"SELECT inner",new String[0]},(r,p)->{sqls[1]=(String)p[0];return null;});
   return null;
  });
  check(rewrites[0]==1&&sqls[0].endsWith("/* filtered */")&&sqls[1].equals("SELECT inner"),"Nested queries were transformed twice");
  check(originalQuery[0].equals("SELECT outer"),"Production modified immutable arguments instead of copied array");depths(api);
  try{registry.invoke(query,owner,originalQuery,(r,p)->{throw failure;});throw new AssertionError();}
  catch(IllegalStateException actual){check(actual==failure,"Query exception identity changed");}depths(api);
  registry.invoke(query,owner,originalQuery,(r,p)->null);
  check(rewrites[0]==3,"Failed query depth prevented later outer rewrite");depths(api);
  registry.invoke(insert,owner,insertArgs(),(r,p)->-1L);
  registry.invoke(update,owner,updateArgs,(r,p)->0);
  check(changes.size()==3,"Unsuccessful mutation emitted a change");
  System.out.println("PASS: production database interceptors continue once, copy query arguments, preserve failures, unwind nested query/mutation state and dispatch only successful outer mutations");
 }
}
