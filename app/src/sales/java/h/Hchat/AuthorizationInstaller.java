package h.Hchat;

import android.app.Application;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.util.Log;
import com.mykey.license.AuthorizationRuntimeConfig;
import com.mykey.license.GateLogger;
import com.mykey.license.LicenseGateManager;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Installs MyKey's existing LSPosed gate from WeiPlus's sole module entry. */
final class AuthorizationInstaller {
  private static final String TAG = "MyKey";
  private static final Map<String, ProcessGate> PROCESS_GATES = new ConcurrentHashMap<>();

  private AuthorizationInstaller() {}

  static void install(ModuleEntry module, PackageReadyParam param, String processName) {
    String packageName = param.getPackageName();
    if (!WechatPackage.isModuleEntryScope(packageName, processName)) return;
    ProcessGate gate = PROCESS_GATES.computeIfAbsent(processName, ProcessGate::new);
    synchronized (gate) {
      if (gate.installed) return;
      if (gate.packageName != null && !gate.packageName.equals(packageName)) return;
      gate.packageName = packageName;
      try {
        ClassLoader loader = param.getClassLoader();
        Class<?> instrumentation = Class.forName(Instrumentation.class.getName(), false, loader);
        Method onCreate = instrumentation.getDeclaredMethod("callApplicationOnCreate", Application.class);
        onCreate.setAccessible(true);
        module.installAuthorizationHook(onCreate, "mykey.application_on_create", chain -> {
          Object argument = chain.getArg(0);
          if (argument instanceof Application) registerApplication(gate, (Application) argument);
          return chain.proceed();
        });
        try {
          Class<?> dialogClass = Class.forName(Dialog.class.getName(), false, loader);
          Method show = dialogClass.getDeclaredMethod("show");
          module.installAuthorizationHook(show, "mykey.authorization_gate_dialog", chain -> {
            Object instance = chain.getThisObject();
            if (instance instanceof Dialog) {
              Dialog dialog = (Dialog) instance;
              LicenseGateManager manager = gate.managers.get(packageName);
              if (manager != null && manager.shouldSuppressDialog(dialog.getContext(), dialog)) {
                return null;
              }
            }
            return chain.proceed();
          });
        } catch (Throwable error) {
          Log.w(TAG, "event=install_hook result=failed target=authorization_gate_dialog", error);
        }
        gate.installed = true;
        Log.i(TAG, "event=install_hook result=ok target=Instrumentation.callApplicationOnCreate"
            + " package=" + packageName + " process=" + processName);
      } catch (Throwable error) {
        gate.packageName = null;
        PROCESS_GATES.remove(processName, gate);
        Log.e(TAG, "event=install_hook result=failed target=Instrumentation.callApplicationOnCreate"
            + " package=" + packageName + " process=" + processName, error);
      }
    }
  }

  private static void registerApplication(ProcessGate gate, Application application) {
    try {
      Context context = application.getApplicationContext();
      String packageName = context.getPackageName();
      if (!packageName.equals(gate.packageName)) return;
      LicenseGateManager manager = gate.managers.computeIfAbsent(packageName,
          key -> new LicenseGateManager(key, gate.processName, LOGGER,
              AuthorizationRuntimeConfig.forModuleBuild(
                  BuildConfig.MYKEY_API_BASE_URL, BuildConfig.DEBUG)));
      manager.attach(application);
    } catch (Throwable error) {
      Log.e(TAG, "event=application_register result=failed process=" + gate.processName, error);
    }
  }

  private static final GateLogger LOGGER = new GateLogger() {
    @Override public void info(String message) { Log.i(TAG, message); }
    @Override public void warn(String message, Throwable error) { Log.w(TAG, message, error); }
    @Override public void error(String message, Throwable error) { Log.e(TAG, message, error); }
  };

  private static final class ProcessGate {
    final String processName;
    final Map<String, LicenseGateManager> managers = new ConcurrentHashMap<>();
    String packageName;
    boolean installed;

    ProcessGate(String processName) { this.processName = processName; }
  }
}
