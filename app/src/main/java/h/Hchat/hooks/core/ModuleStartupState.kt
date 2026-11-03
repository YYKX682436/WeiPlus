package h.Hchat.hooks.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps Application readiness independent from asynchronous module initialization. */
class ModuleStartupState {
    private val applicationReady = AtomicBoolean(false)
    private val initializationInProgress = AtomicBoolean(false)
    private val initializationSucceeded = AtomicBoolean(false)
    private val earlyEntries = ConcurrentHashMap<String, AtomicBoolean>()

    fun markApplicationReady() {
        applicationReady.set(true)
    }

    fun isApplicationReady(): Boolean = applicationReady.get()

    fun isInitializationInProgress(): Boolean = initializationInProgress.get()

    fun isInitializationSucceeded(): Boolean = initializationSucceeded.get()

    fun shouldScheduleInitialization(): Boolean =
        applicationReady.get() && !initializationSucceeded.get()

    fun beginInitializationAttempt(): Boolean =
        shouldScheduleInitialization() && initializationInProgress.compareAndSet(false, true)

    fun finishInitializationAttempt(succeeded: Boolean) {
        if (succeeded) initializationSucceeded.set(true)
        initializationInProgress.set(false)
    }

    /** A failed early entry remains retryable; successful entries are installed once. */
    fun installEarlyEntryOnce(key: String, installer: () -> Boolean): Boolean {
        if (key.isBlank()) return false
        val installed = earlyEntries.computeIfAbsent(key) { AtomicBoolean(false) }
        if (installed.get()) return true
        synchronized(installed) {
            if (installed.get()) return true
            if (!installer()) return false
            installed.set(true)
            return true
        }
    }
}
