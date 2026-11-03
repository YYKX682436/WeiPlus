package h.Hchat.tests.api102;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Records registrations only. ART installation is validated on device, not simulated here. */
public final class FrameworkFixture {
    private static final Map<XposedInterface, FrameworkFixture> INSTANCES = new IdentityHashMap<>();
    public static final class LogEntry {
        public final int priority; public final String message; public final Throwable error;
        LogEntry(int p,String m,Throwable t) { priority=p;message=m;error=t; }
    }
    public static final class Handle implements XposedInterface.HookHandle {
        private final Executable executable;
        public final int priority;
        public XposedInterface.Hooker hooker;
        public boolean active=true, fail=false;
        Handle(Executable e,int p,XposedInterface.Hooker h) {executable=e;priority=p;hooker=h;}
        public Executable getExecutable() {return executable;}
        public String getId() {return null;}
        public void unhook() {if(fail)throw new IllegalStateException("simulated framework failure");active=false;}
        public XposedInterface.HookHandle replaceHook(XposedInterface.Hooker next) {hooker=next;return this;}
    }
    public final List<Handle> handles = new ArrayList<>();
    public final List<LogEntry> logs = new ArrayList<>();
    public int calls;
    public Executable failOn;
    public Runnable afterRegistration;
    public XposedInterface create() {
        XposedInterface proxy = (XposedInterface)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.class},(proxyObject,method,args)-> {
            switch(method.getName()) {
                case "hook": {
                    Executable executable=(Executable)args[0];
                    int[] priority={XposedInterface.PRIORITY_DEFAULT};
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{XposedInterface.HookBuilder.class},(builder,operation,parameters)-> {
                        switch(operation.getName()) {
                            case "setPriority":priority[0]=(int)parameters[0];return builder;
                            case "setId":case "setExceptionMode":return builder;
                            case "intercept":
                                calls++;
                                if(executable.equals(failOn))throw new IllegalStateException("simulated install failure");
                                Handle handle=new Handle(executable,priority[0],(XposedInterface.Hooker)parameters[0]);
                                handles.add(handle);if(afterRegistration!=null)afterRegistration.run();return handle;
                            default:throw new UnsupportedOperationException(operation.getName());
                        }
                    });
                }
                case "log":logs.add(new LogEntry((int)args[0],(String)args[2],args.length==4?(Throwable)args[3]:null));return null;
                case "getApiVersion":return 102;
                case "getFrameworkName":return "API102 JVM registration fixture";
                case "getFrameworkVersion":return "102.0.0";
                case "getFrameworkVersionCode":case "getFrameworkProperties":return 0L;
                default:throw new UnsupportedOperationException(method.getName());
            }
        });
        synchronized (INSTANCES) { INSTANCES.put(proxy, this); }
        return proxy;
    }
    public static FrameworkFixture forRuntime(XposedInterface runtime) {
        synchronized (INSTANCES) { return INSTANCES.get(runtime); }
    }
    public static Handle handle(XposedInterface.HookHandle value) {
        if (value instanceof Handle) return (Handle)value;
        try {
            java.lang.reflect.Field field=value.getClass().getDeclaredField("delegate");
            field.setAccessible(true);
            return handle((XposedInterface.HookHandle)field.get(value));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
