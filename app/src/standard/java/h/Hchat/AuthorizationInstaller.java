package h.Hchat;

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/** Empty entry for the public, authorization-free build. */
final class AuthorizationInstaller {
  private AuthorizationInstaller() {}

  static void install(ModuleEntry module, PackageReadyParam param, String processName) {}
}
