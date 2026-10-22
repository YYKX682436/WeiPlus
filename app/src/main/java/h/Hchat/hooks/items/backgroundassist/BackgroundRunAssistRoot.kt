package h.Hchat.hooks.items.backgroundassist

import h.Hchat.WechatPackage
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

internal data class RootRunOptions(val dozeWhitelist: Boolean, val appOps: Boolean)

internal data class RootCommandResult(
    val output: String?,
    val exitCode: Int?,
    val timedOut: Boolean = false
) {
    val succeeded: Boolean get() = !timedOut && exitCode == 0 && output != null
}

internal fun interface RootCommandRunner {
    fun execute(command: String): RootCommandResult
}

internal fun interface RootProcessFactory {
    fun start(command: String): Process
}

/** Production runner is bounded; tests inject a local shell process without requiring Root. */
internal class RootShellCommandRunner(
    private val processFactory: RootProcessFactory = RootProcessFactory { command ->
        ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
    },
    private val timeoutMillis: Long = ROOT_COMMAND_TIMEOUT_MILLIS
) : RootCommandRunner {
    override fun execute(command: String): RootCommandResult {
        var process: Process? = null
        return try {
            process = processFactory.start(command)
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                runCatching { process.waitFor(1, TimeUnit.SECONDS) }
                runCatching { process.inputStream.close() }
                RootCommandResult(null, null, timedOut = true)
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                RootCommandResult(output, process.exitValue())
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            runCatching { process?.destroyForcibly() }
            RootCommandResult(null, null, timedOut = true)
        } catch (_: Throwable) {
            runCatching { process?.destroyForcibly() }
            RootCommandResult(null, null)
        }
    }

    companion object {
        const val ROOT_COMMAND_TIMEOUT_MILLIS = 10_000L
    }
}

internal interface RootStateStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, fallback: Boolean = false): Boolean
    fun getString(key: String): String?
    fun commit(changes: Map<String, Any?>): Boolean
}

/** Applies only explicitly enabled options and retains snapshots until a verified restore completes. */
internal class RootSettingsController(private val commands: RootCommandRunner) {
    fun reconcile(
        hostPackage: String,
        options: RootRunOptions,
        state: RootStateStore,
        isCurrent: () -> Boolean = { true }
    ): Boolean {
        if (!WechatPackage.isTarget(hostPackage)) return false
        var success = true
        val dozeKey = "doze_original_$hostPackage"
        val appOpsSnapshotKey = "appops_snapshot_$hostPackage"

        if (options.dozeWhitelist || state.contains(dozeKey)) {
            if (!reconcileDoze(hostPackage, options.dozeWhitelist, state, isCurrent)) success = false
        }
        if (options.appOps || state.getBoolean(appOpsSnapshotKey)) {
            if (!reconcileAppOps(hostPackage, options.appOps, state, isCurrent)) success = false
        }
        return success
    }

    private fun reconcileDoze(
        hostPackage: String,
        enabled: Boolean,
        state: RootStateStore,
        isCurrent: () -> Boolean
    ): Boolean {
        val key = "doze_original_$hostPackage"
        if (!isCurrent()) return false
        val current = queryDoze(hostPackage) ?: return false
        if (enabled) {
            if (!state.contains(key) && !state.commit(mapOf(key to current))) return false
            val originallyAllowed = state.getBoolean(key)
            if (!originallyAllowed && !current) {
                if (!isCurrent() || !commands.execute("cmd deviceidle whitelist +$hostPackage").succeeded) return false
                if (queryDoze(hostPackage) != true) return false
            }
            return true
        }

        if (!state.contains(key)) return true
        val originallyAllowed = state.getBoolean(key)
        if (!originallyAllowed && current) {
            if (!isCurrent() || !commands.execute("cmd deviceidle whitelist -$hostPackage").succeeded) return false
            if (queryDoze(hostPackage) != false) return false
        }
        if (!isCurrent()) return false
        return state.commit(mapOf(key to null))
    }

    private fun reconcileAppOps(
        hostPackage: String,
        enabled: Boolean,
        state: RootStateStore,
        isCurrent: () -> Boolean
    ): Boolean {
        val snapshotKey = "appops_snapshot_$hostPackage"
        val modeKeys = ALL_APP_OPS.associateWith { op -> "appops_original_${hostPackage}_$op" }
        if (enabled) {
            if (!state.getBoolean(snapshotKey)) {
                val originals = LinkedHashMap<String, String>()
                for (op in BACKGROUND_APP_OPS) {
                    if (!isCurrent()) return false
                    originals[op] = queryAppOp(hostPackage, op) ?: return false
                }
                val changes = linkedMapOf<String, Any?>(snapshotKey to true)
                originals.forEach { (op, mode) -> changes[modeKeys.getValue(op)] = mode }
                if (!state.commit(changes)) return false
            }

            // Restore the two broader AppOps changed by the previous implementation, when its
            // snapshot proves they were managed. New configurations never change these operations.
            for (op in LEGACY_APP_OPS) {
                val key = modeKeys.getValue(op)
                if (!state.contains(key)) continue
                if (!restoreSavedAppOp(hostPackage, op, state.getString(key) ?: return false, isCurrent)) return false
            }
            for (op in BACKGROUND_APP_OPS) {
                if (!isCurrent()) return false
                if (queryAppOp(hostPackage, op) != "allow") {
                    if (!commands.execute("cmd appops set $hostPackage $op allow").succeeded) return false
                    if (queryAppOp(hostPackage, op) != "allow") return false
                }
            }
            return true
        }

        if (!state.getBoolean(snapshotKey)) return true
        val storedOperations = ALL_APP_OPS.filter { state.contains(modeKeys.getValue(it)) }
        if (!storedOperations.containsAll(BACKGROUND_APP_OPS)) return false
        for (op in storedOperations) {
            if (!isCurrent()) return false
            val original = state.getString(modeKeys.getValue(op)) ?: return false
            val current = queryAppOp(hostPackage, op) ?: return false
            // If another actor changed the mode away from our applied value, leave it untouched.
            val restore = RootStatePolicy.appOpModeToRestore(current, original)
            if (restore != null) {
                if (!commands.execute("cmd appops set $hostPackage $op $restore").succeeded) return false
                if (queryAppOp(hostPackage, op) != restore) return false
            }
        }
        if (!isCurrent()) return false
        val changes = linkedMapOf<String, Any?>(snapshotKey to null)
        storedOperations.forEach { changes[modeKeys.getValue(it)] = null }
        return state.commit(changes)
    }

    private fun restoreSavedAppOp(
        hostPackage: String,
        operation: String,
        original: String,
        isCurrent: () -> Boolean
    ): Boolean {
        if (!isCurrent()) return false
        val current = queryAppOp(hostPackage, operation) ?: return false
        val restore = RootStatePolicy.appOpModeToRestore(current, original) ?: return true
        if (!commands.execute("cmd appops set $hostPackage $operation $restore").succeeded) return false
        return queryAppOp(hostPackage, operation) == restore
    }

    private fun queryDoze(hostPackage: String): Boolean? {
        val result = commands.execute("cmd deviceidle whitelist")
        if (!result.succeeded) return null
        return RootStatePolicy.isPackageWhitelisted(result.output.orEmpty(), hostPackage)
    }

    private fun queryAppOp(hostPackage: String, operation: String): String? {
        val result = commands.execute("cmd appops get $hostPackage $operation")
        if (!result.succeeded) return null
        return RootStatePolicy.parseAppOpMode(result.output.orEmpty(), operation)
    }

    companion object {
        private val BACKGROUND_APP_OPS = listOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND")
        private val LEGACY_APP_OPS = listOf("WAKE_LOCK", "START_FOREGROUND")
        private val ALL_APP_OPS = BACKGROUND_APP_OPS + LEGACY_APP_OPS
    }
}

/** A single queued drain per supported package coalesces rapid changes to the newest options. */
internal class LatestRootOptionsQueue(
    private val executor: Executor,
    private val controller: RootSettingsController,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> }
) {
    private data class Desired(val options: RootRunOptions, val state: () -> RootStateStore)

    private class Slot {
        var generation = 0L
        var desired: Desired? = null
        var scheduled = false
    }

    private val slots = mutableMapOf<String, Slot>()

    fun submit(hostPackage: String, generation: Long, options: RootRunOptions, state: () -> RootStateStore): Boolean {
        if (!WechatPackage.isTarget(hostPackage)) return false
        val slot: Slot
        val shouldSchedule: Boolean
        synchronized(slots) {
            slot = slots.getOrPut(hostPackage, ::Slot)
            synchronized(slot) {
                if (generation <= slot.generation) return true
                slot.generation = generation
                slot.desired = Desired(options, state)
                shouldSchedule = !slot.scheduled
                if (shouldSchedule) slot.scheduled = true
            }
        }
        if (!shouldSchedule) return true
        return try {
            executor.execute { drain(hostPackage, slot) }
            true
        } catch (failure: Throwable) {
            synchronized(slot) { slot.scheduled = false }
            onFailure(hostPackage, failure)
            false
        }
    }

    private fun drain(hostPackage: String, slot: Slot) {
        while (true) {
            val generation: Long
            val desired: Desired
            synchronized(slot) {
                val next = slot.desired
                if (next == null) {
                    slot.scheduled = false
                    return
                }
                desired = next
                generation = slot.generation
                slot.desired = null
            }
            val current = { synchronized(slot) { slot.generation == generation } }
            runCatching {
                val applied = controller.reconcile(hostPackage, desired.options, desired.state(), current)
                if (!applied && current()) throw IllegalStateException("Root state could not be applied or verified")
            }.onFailure { onFailure(hostPackage, it) }
            synchronized(slot) {
                if (slot.generation == generation) {
                    slot.scheduled = false
                    return
                }
            }
        }
    }
}

internal object RootStatePolicy {
    private val appOpModes = setOf("allow", "ignore", "default", "foreground", "errored", "ask", "deny")

    fun isPackageWhitelisted(output: String, hostPackage: String): Boolean =
        output.split(Regex("[,\\s]+")).any { it.trim() == hostPackage }

    fun parseAppOpMode(output: String, operation: String): String? {
        if (output.contains("No operations.", ignoreCase = true)) return "default"
        val lines = output.lineSequence().map(String::trim)
        if (lines.any { it.startsWith("Default mode:", ignoreCase = true) }) return "default"
        val line = output.lineSequence().map(String::trim).firstOrNull { it.startsWith("$operation:") } ?: return null
        val mode = line.substringAfter(':').trim().substringBefore(';').substringBefore(' ').lowercase()
        return mode.takeIf { it in appOpModes }
    }

    fun appOpModeToRestore(currentMode: String, originalMode: String): String? =
        originalMode.takeIf { currentMode == "allow" && originalMode != "allow" }
}

internal interface TimedWakeLockPort {
    val isHeld: Boolean
    fun acquire(timeoutMillis: Long)
    fun release()
}

/** Owns one system-timed lock per host; Android releases it even if the injected process dies. */
internal class WakeLockLeaseRegistry {
    private val locks = mutableMapOf<String, TimedWakeLockPort>()

    @Synchronized
    fun setDesired(
        hostPackage: String,
        generation: Long,
        enabled: Boolean,
        create: (() -> TimedWakeLockPort)? = null
    ): Boolean {
        if (!WechatPackage.isTarget(hostPackage)) return false
        val previousGeneration = generations[hostPackage] ?: Long.MIN_VALUE
        if (generation <= previousGeneration) return true
        generations[hostPackage] = generation
        if (!enabled) {
            releaseLocked(hostPackage)
            return true
        }
        val previous = locks[hostPackage]
        if (runCatching { previous?.isHeld == true }.getOrDefault(false)) return true
        locks.remove(hostPackage)
        runCatching { previous?.release() }
        val lock = runCatching { create?.invoke() }.getOrNull() ?: return false
        return try {
            lock.acquire(MAX_WAKE_LOCK_MILLIS)
            if (lock.isHeld) {
                locks[hostPackage] = lock
                true
            } else {
                runCatching { lock.release() }
                false
            }
        } catch (_: Throwable) {
            runCatching { lock.release() }
            false
        }
    }

    private fun releaseLocked(hostPackage: String) {
        val lock = locks.remove(hostPackage) ?: return
        runCatching { lock.release() }
    }

    @Synchronized
    fun heldFor(hostPackage: String): Boolean =
        WechatPackage.isTarget(hostPackage) && runCatching { locks[hostPackage]?.isHeld == true }.getOrDefault(false)

    companion object {
        const val MAX_WAKE_LOCK_MILLIS = 10 * 60 * 1_000L
    }

    private val generations = mutableMapOf<String, Long>()
}
