package h.Hchat.hooks.core;
import h.Hchat.tests.api102.ChainFixture;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;
import java.lang.reflect.Executable;
import java.util.HashMap;
import java.util.Map;
public final class HookRegistry {
 private static final HookRegistry INSTANCE=new HookRegistry();
 private final Map<Executable,TestHandle> hooks=new HashMap<>();
 private long generation;
 public static HookRegistry get(){return INSTANCE;}
 public HookHandle hook(Executable executable,XposedInterface.Hooker hooker){
  return hook(executable,hooker,XposedInterface.PRIORITY_DEFAULT);
 }
 public HookHandle hook(Executable executable,XposedInterface.Hooker hooker,int priority){
  if(priority!=XposedInterface.PRIORITY_LOWEST&&priority!=XposedInterface.PRIORITY_DEFAULT)
   throw new AssertionError("Unexpected hook priority: "+priority);
  if(hooks.containsKey(executable))throw new AssertionError("Duplicate install");
  TestHandle handle=new TestHandle(executable,hooker);
  hooks.put(executable,handle);
  return handle;
 }
 public long installationGeneration(){return generation;}
 public boolean isInstallationGenerationActive(long value){return generation==value;}
 public boolean isActive(HookHandle handle){return handle instanceof TestHandle && ((TestHandle)handle).active;}
 public void unhook(HookHandle handle){if(handle instanceof TestHandle)((TestHandle)handle).unhook();}
 public void unhookAll(){generation++;for(TestHandle handle:hooks.values())handle.unhook();hooks.clear();}
 public int size(){return hooks.size();}
 public Object invoke(Executable executable,Object owner,Object[] args,ChainFixture.Original original)throws Throwable {
  TestHandle handle=hooks.get(executable);
  if(handle==null||!handle.active)throw new AssertionError("Production interceptor missing: "+executable);
  ChainFixture chain=new ChainFixture(executable,owner,args,original);
  try{return chain.run(handle.callback);}finally{if(chain.proceeds!=1)throw new AssertionError("Database continued "+chain.proceeds+" times");}
 }
 private final class TestHandle implements HookHandle {
  final Executable executable;final XposedInterface.Hooker callback;boolean active=true;
  TestHandle(Executable executable,XposedInterface.Hooker callback){this.executable=executable;this.callback=callback;}
  public Executable getExecutable(){return executable;}
  public void unhook(){active=false;if(hooks.get(executable)==this)hooks.remove(executable);}
  public String getId(){return executable.toString();}
  public HookHandle replaceHook(XposedInterface.Hooker replacement){throw new UnsupportedOperationException();}
 }
}
