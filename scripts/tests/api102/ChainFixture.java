package h.Hchat.tests.api102;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A single invocation boundary. It deliberately enforces immutable arguments and one proceed. */
public final class ChainFixture implements XposedInterface.Chain {
    @FunctionalInterface public interface Original { Object call(Object receiver, Object[] args) throws Throwable; }
    private final Executable executable;
    private final Object receiver;
    private final Object[] arguments;
    private final Original original;
    private boolean open = true;
    public int proceeds;
    public ChainFixture(Executable executable, Object receiver, Object[] args, Original original) {
        this.executable=executable; this.receiver=receiver; this.arguments=args.clone(); this.original=original;
    }
    public Object run(XposedInterface.Hooker hooker) throws Throwable {
        try { return hooker.intercept(this); } finally { open=false; }
    }
    public Executable getExecutable() { return executable; }
    public Object getThisObject() { return receiver; }
    public List<Object> getArgs() { return Collections.unmodifiableList(Arrays.asList(arguments.clone())); }
    public Object getArg(int index) { return arguments[index]; }
    public Object proceed() throws Throwable { return proceedWith(receiver,arguments); }
    public Object proceed(Object[] args) throws Throwable { return proceedWith(receiver,args); }
    public Object proceedWith(Object owner) throws Throwable { return proceedWith(owner,arguments); }
    public Object proceedWith(Object owner,Object[] args) throws Throwable {
        if (!open) throw new IllegalStateException("Chain used after interceptor returned");
        if (++proceeds != 1) throw new AssertionError("Host invocation attempted more than once");
        return original.call(owner,args.clone());
    }
}
