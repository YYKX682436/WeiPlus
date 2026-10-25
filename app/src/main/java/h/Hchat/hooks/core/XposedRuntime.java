package h.Hchat.hooks.core;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/** Process-local framework identity, attached only after onModuleLoaded. */
public final class XposedRuntime {
  private static volatile XposedInterface framework;

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
    return get()
        .hook(target)
        .setPriority(priority)
        .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
        .intercept(hooker);
  }

  /** The caller owns these handles; a failed batch never leaves a partial installation. */
  public static Set<HookHandle> hookAllMethods(Class<?> type, String name, Hooker hooker) {
    Set<Executable> targets = new LinkedHashSet<>();
    for (Method method : type.getDeclaredMethods()) {
      if (method.getName().equals(name)) targets.add(method);
    }
    return hookBatch(targets, hooker);
  }

  public static Set<HookHandle> hookAllConstructors(Class<?> type, Hooker hooker) {
    Set<Executable> targets = new LinkedHashSet<>();
    java.util.Collections.addAll(targets, type.getDeclaredConstructors());
    return hookBatch(targets, hooker);
  }

  private static Set<HookHandle> hookBatch(Set<Executable> targets, Hooker hooker) {
    Set<HookHandle> handles = new LinkedHashSet<>();
    try {
      for (Executable target : targets) handles.add(hook(target, hooker));
      return handles;
    } catch (Throwable failure) {
      for (HookHandle handle : handles) {
        try {
          handle.unhook();
        } catch (Throwable cleanup) {
          failure.addSuppressed(cleanup);
        }
      }
      throw failure;
    }
  }
}
