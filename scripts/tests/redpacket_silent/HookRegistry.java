package h.Hchat.hooks.core;

import io.github.libxposed.api.XposedInterface;
import h.Hchat.tests.api102.ChainFixture;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public final class HookRegistry {
    private static final HookRegistry INSTANCE = new HookRegistry();
    private final Map<Executable, List<XposedInterface.Hooker>> hooks = new HashMap<>();
    public static HookRegistry get() { return INSTANCE; }
    public void clear() { hooks.clear(); }
    public XposedInterface.HookHandle hook(Executable method, XposedInterface.Hooker hook) {
        hooks.computeIfAbsent(method, ignored -> new ArrayList<>()).add(hook);
        return new XposedInterface.HookHandle() {
            public Executable getExecutable(){return method;}
            public String getId(){return null;}
            public void unhook(){hooks.get(method).remove(hook);}
            public XposedInterface.HookHandle replaceHook(XposedInterface.Hooker next){throw new UnsupportedOperationException();}
        };
    }
    public void fire(Object request, int error, String message, Object json) throws Throwable {
        Method method=request.getClass().getMethod("onGYNetEnd", int.class, String.class, JSONObject.class);
        List<XposedInterface.Hooker> callbacks=hooks.get(method);
        if(callbacks==null)throw new AssertionError("Callback was not hooked: "+method);
        fireAt(callbacks,0,method,request,new Object[]{error,message,json});
    }
    private Object fireAt(List<XposedInterface.Hooker> callbacks,int index,Method method,Object owner,Object[] args)throws Throwable {
        if(index==callbacks.size()) {
            try{return method.invoke(owner,args);}catch(InvocationTargetException failure){throw failure.getCause();}
        }
        return new ChainFixture(method,owner,args,(receiver,parameters)->fireAt(callbacks,index+1,method,receiver,parameters)).run(callbacks.get(index));
    }
}
