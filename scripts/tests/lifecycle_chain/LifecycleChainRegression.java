import android.app.Activity;
import android.content.Intent;
import com.tencent.mm.ui.chatting.ChattingUI;
import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.api.ui.WeChatLifecycleApi;
import h.Hchat.hooks.api.ui.WeChatChatPageApi;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.hooks.core.XposedRuntime;
import h.Hchat.tests.api102.FrameworkFixture;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class LifecycleChainRegression {
 public static final class Fragment {public void exit(){}}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Object call(Activity owner,String name,Throwable failure)throws Throwable {
  Method method=Activity.class.getDeclaredMethod(name);
  return HookRegistry.get().invoke(method,owner,new Object[0],(r,a)->{if(failure!=null)throw failure;return null;});
 }
 public static void main(String[] args)throws Throwable {
  FrameworkFixture framework=new FrameworkFixture();XposedRuntime.initialize(framework.create());
  WeChatLifecycleApi lifecycle=new WeChatLifecycleApi(message->{if(message.contains("生命周期回调失败"))throw new IllegalStateException("logger failed");});
  DexFinder finder=new DexFinder();finder.chatPageFragmentExitMethod=Fragment.class.getMethod("exit");
  WeChatChatPageApi pages=new WeChatChatPageApi(finder,null,lifecycle,null,null,null);
  List<WeChatChatPageApi.ChatPageEvent> events=new ArrayList<>();pages.subscribe(events::add);pages.install();
  lifecycle.subscribe(event->{throw new IllegalArgumentException("module observer failed");});
  lifecycle.install();check(lifecycle.hookedMethodCount()==3,"Lifecycle methods missing");
  ChattingUI activity=new ChattingUI();activity.setIntent(new Intent().putExtra("Chat_User","peer"));
  for(String event:List.of("onPause","onDestroy")){
   call(activity,"onResume",null);check(pages.currentTalker().equals("peer"),"Resume did not publish chat identity");
   IllegalStateException failure=new IllegalStateException("native "+event+" failed");
   try{call(activity,event,failure);throw new AssertionError();}catch(IllegalStateException actual){check(actual==failure,"Cleanup replaced host exception");}
   check(pages.currentTalker().isEmpty()&&events.get(events.size()-1).isExit(),"Failed native lifecycle retained current talker");
  }
  int count=events.size();IllegalStateException failedResume=new IllegalStateException("native resume failed");
  try{call(activity,"onResume",failedResume);throw new AssertionError();}catch(IllegalStateException actual){check(actual==failedResume,"Resume failure changed");}
  check(events.size()==count&&pages.currentTalker().isEmpty(),"Failed resume emitted success event");
  call(activity,"onResume",null);
  IllegalStateException failedExit=new IllegalStateException("fragment exit failed");
  try{HookRegistry.get().invoke(finder.chatPageFragmentExitMethod,new Fragment(),new Object[0],(r,a)->{throw failedExit;});throw new AssertionError();}
  catch(IllegalStateException actual){check(actual==failedExit,"Fragment cleanup replaced host exception");}
  check(pages.currentTalker().isEmpty()&&events.get(events.size()-1).isExit(),"Failed fragment exit retained current talker");
  check(!framework.logs.isEmpty(),"Observer/logger failure lost diagnostic");
  System.out.println("PASS: production lifecycle pause/destroy and fragment exit clear real chat-page identity despite host failures, resume is success-only and observer/logger errors cannot replace host exception");
 }
}
