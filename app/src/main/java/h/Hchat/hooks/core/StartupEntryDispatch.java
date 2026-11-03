package h.Hchat.hooks.core;

import java.util.function.BooleanSupplier;

/** Runs one-time event hooks on the callback thread before queuing background work. */
public final class StartupEntryDispatch {
  private StartupEntryDispatch() {}

  public static void installBeforeScheduling(Runnable installEntries, Runnable scheduleBackgroundWork) {
    try {
      installEntries.run();
    } finally {
      scheduleBackgroundWork.run();
    }
  }

  /** Isolates a retryable optional entry Hook from the rest of package startup. */
  public static boolean installOptional(BooleanSupplier installer, Runnable scheduleRetry) {
    try {
      if (installer.getAsBoolean()) return true;
    } catch (Throwable ignored) {
      // The caller's retry task owns diagnostics and the next attempt.
    }
    scheduleRetry.run();
    return false;
  }
}
