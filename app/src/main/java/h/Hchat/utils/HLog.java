package h.Hchat.utils;

import android.util.Log;
import h.Hchat.hooks.core.XposedRuntime;

/** Framework logging with explicit severity; error logs are reserved for real failures. */
public final class HLog {
  private HLog() {}

  public static void e(String message) {
    XposedRuntime.get().log(Log.ERROR, "Hchat", message);
  }

  public static void e(String message, Throwable throwable) {
    if (throwable == null) {
      e(message);
      return;
    }
    XposedRuntime.get().log(Log.ERROR, "Hchat", message, throwable);
  }

  public static void i(String message) {
    XposedRuntime.get().log(Log.INFO, "Hchat", message);
  }
}
