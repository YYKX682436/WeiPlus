package h.Hchat.hooks.core;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/** Process-local framework identity and ownership of failed framework removals. */
public final class XposedRuntime {
    private static volatile XposedInterface framework;
    private static final Set<ManagedHook> failedUnhooks =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private XposedRuntime() {}

    public static synchronized void initialize(XposedInterface value) {
        if (value == null) throw new NullPointerException("framework");
        if (framework != null && framework != value) {
            throw new IllegalStateException("Framework already initialized in this process");
        }
        framework = value;
    }

    public static XposedInterface get() {
        XposedInterface value = framework;
        if (value == null) throw new IllegalStateException("onModuleLoaded has not run");
        return value;
    }

    public static HookHandle hook(Executable target, Hooker hooker) {
        return hook(target, XposedInterface.PRIORITY_DEFAULT, hooker);
    }

    public static HookHandle hook(Executable target, int priority, Hooker hooker) {
        return install(target, priority, hooker, new BatchGate(true));
    }

    private static ManagedHook install(Executable target, int priority, Hooker hooker, BatchGate gate) {
        ManagedHook owned = new ManagedHook(target, hooker, gate);
        // Side-effecting hooks must never fall through after taking over a send or mutation.
        // Optional observers isolate their module work locally with HookErrorBoundary; host failures
        // remain outside that boundary. module.prop deliberately has the same fail-closed policy.
        owned.delegate = get().hook(target).setPriority(priority)
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                .intercept(owned::intercept);
        return owned;
    }

    public static Set<HookHandle> hookAllMethods(Class<?> type, String name, Hooker hooker) {
        Set<Executable> targets = new LinkedHashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) targets.add(method);
        }
        return hookBatch(targets, hooker);
    }

    public static Set<HookHandle> hookAllConstructors(Class<?> type, Hooker hooker) {
        Set<Executable> targets = new LinkedHashSet<>();
        Collections.addAll(targets, type.getDeclaredConstructors());
        return hookBatch(targets, hooker);
    }

    private static Set<HookHandle> hookBatch(Set<Executable> targets, Hooker hooker) {
        retryFailedUnhooks();
        BatchGate gate = new BatchGate(false);
        Set<HookHandle> handles = new LinkedHashSet<>();
        try {
            for (Executable target : targets) {
                // The batch owns every returned registration before attempting the next one.
                handles.add(install(target, XposedInterface.PRIORITY_DEFAULT, hooker, gate));
            }
            gate.active = true;
            return handles;
        } catch (Throwable failure) {
            gate.active = false;
            for (HookHandle handle : handles) {
                try {
                    handle.unhook();
                } catch (Throwable cleanup) {
                    // ManagedHook retains ownership before throwing. No residual callback can act.
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    /** Explicit lifecycle cleanup only: no background polling, no permanent ownership of live hooks. */
    public static void retryFailedUnhooks() {
        ManagedHook[] pending;
        synchronized (failedUnhooks) {
            pending = failedUnhooks.toArray(new ManagedHook[0]);
        }
        for (ManagedHook handle : pending) {
            try {
                handle.unhook();
            } catch (Throwable ignored) {
                // Still owned, inert and available for the next explicit cleanup.
            }
        }
    }

    public static int failedUnhookCount() {
        synchronized (failedUnhooks) {
            return failedUnhooks.size();
        }
    }

    private static final class BatchGate {
        volatile boolean active;
        BatchGate(boolean active) { this.active = active; }
    }

    /** An official handle with a revocable module callback; it does not emulate a hook chain. */
    private static final class ManagedHook implements HookHandle {
        private final Executable executable;
        private final BatchGate gate;
        private volatile Hooker callback;
        private HookHandle delegate;
        private boolean removed;

        ManagedHook(Executable executable, Hooker callback, BatchGate gate) {
            if (callback == null) throw new NullPointerException("hooker");
            this.executable = executable;
            this.callback = callback;
            this.gate = gate;
        }

        Object intercept(XposedInterface.Chain chain) throws Throwable {
            Hooker action = callback;
            if (!gate.active || action == null) return chain.proceed();
            return action.intercept(chain);
        }

        public Executable getExecutable() { return executable; }
        public String getId() { return delegate.getId(); }

        public synchronized void unhook() {
            // Invalidate first, even when the framework refuses to remove its callback. Dropping the
            // action also releases its Activity/request captures while only an inert handle is kept.
            if (removed) return;
            callback = null;
            try {
                delegate.unhook();
                removed = true;
                synchronized (failedUnhooks) { failedUnhooks.remove(this); }
            } catch (Throwable failure) {
                synchronized (failedUnhooks) { failedUnhooks.add(this); }
                throw failure;
            }
        }

        public synchronized HookHandle replaceHook(Hooker next) {
            if (removed || callback == null || !gate.active) {
                throw new IllegalStateException("Hook is no longer active");
            }
            ManagedHook replacement = new ManagedHook(executable, next, gate);
            replacement.delegate = delegate.replaceHook(replacement::intercept);
            // Preserve the old action for calls whose framework snapshot already contains it.
            removed = true;
            return replacement;
        }
    }
}
