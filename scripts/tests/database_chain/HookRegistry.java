package h.Hchat.hooks.core;
import h.Hchat.tests.api102.ChainFixture;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.util.HashMap;
import java.util.Map;
public final class HookRegistry {
 private static final HookRegistry INSTANCE=new HookRegistry();
 private final Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();
 public static HookRegistry get(){return INSTANCE;}
 public void hook(Executable executable,XposedInterface.Hooker hooker){
  if(hooks.putIfAbsent(executable,hooker)!=null)throw new AssertionError("Duplicate install");
 }
 public void hook(Executable executable,XposedInterface.Hooker hooker,int priority){
  if(priority!=XposedInterface.PRIORITY_LOWEST)throw new AssertionError("Database priority changed");
  if(hooks.putIfAbsent(executable,hooker)!=null)throw new AssertionError("Duplicate install");
 }
 public Object invoke(Executable executable,Object owner,Object[] args,ChainFixture.Original original)throws Throwable {
  XposedInterface.Hooker hook=hooks.get(executable);
  if(hook==null)throw new AssertionError("Production interceptor missing: "+executable);
  ChainFixture chain=new ChainFixture(executable,owner,args,original);
  try{return chain.run(hook);}finally{if(chain.proceeds!=1)throw new AssertionError("Database continued "+chain.proceeds+" times");}
 }
}
