package h.Hchat.hooks.core

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedInterface.HookHandle
import java.lang.reflect.Executable
import java.util.concurrent.CancellationException
import java.util.LinkedHashSet

/**
 * 统一保存 Xposed hook 句柄，便于防御性卸载。
 */
class HookRegistry private constructor() {
    private val lock = Any()
    private val unhooks = ArrayList<HookHandle>()
    private val keyedHooks = HashMap<String, KeyedHook>()
    private val generationCleanups = HashMap<Long, MutableSet<GenerationCleanup>>()
    private val installationLease = ThreadLocal<InstallationLease>()
    @Volatile private var generation = 0L

    fun installationGeneration(): Long = installationLease.get()?.generation ?: generation

    fun isInstallationGenerationActive(generation: Long): Boolean = this.generation == generation

    /** Register process-local state that must be released when this hook generation is revoked. */
    fun registerGenerationCleanup(action: Runnable): AutoCloseable = synchronized(lock) {
        val current = installationGeneration()
        if (!isInstallationGenerationActive(current) || !isInstallationLeaseActive(installationLease.get())) {
            throw CancellationException("安装代次已失效")
        }
        val cleanup = GenerationCleanup(this, current, action)
        generationCleanups.getOrPut(current) { LinkedHashSet() }.add(cleanup)
        cleanup
    }

    private fun removeGenerationCleanup(cleanup: GenerationCleanup) = synchronized(lock) {
        generationCleanups[cleanup.generation]?.let { registrations ->
            registrations.remove(cleanup)
            if (registrations.isEmpty()) generationCleanups.remove(cleanup.generation)
        }
    }

    fun <T> withInstallationGeneration(
        generation: Long,
        isLeaseActive: () -> Boolean = { true },
        action: () -> T
    ): T {
        val previous = installationLease.get()
        if (!isInstallationGenerationActive(generation) || previous != null && previous.generation != generation ||
            previous != null && !isInstallationLeaseActive(previous) || !runCatching { isLeaseActive() }.getOrDefault(false)
        ) {
            throw CancellationException("安装代次已失效")
        }
        val lease = previous ?: InstallationLease(generation, isLeaseActive)
        val ownsLease = previous == null
        installationLease.set(lease)
        var completed = false
        try {
            return action().also { completed = true }
        }
        finally {
            if (ownsLease) {
                val active = isInstallationLeaseActive(lease)
                installationLease.remove()
                if (!completed || !active) {
                    // A partially completed install must not leave Hooks behind after failure or cancellation.
                    if (isInstallationGenerationActive(generation)) {
                        lease.installedHooks.asReversed().forEach { runCatching { unhook(it) } }
                    }
                }
            } else {
                installationLease.set(previous)
            }
        }
    }

    private fun isInstallationLeaseActive(lease: InstallationLease?): Boolean =
        lease == null || isInstallationGenerationActive(lease.generation) &&
            runCatching { lease.isActive() }.getOrDefault(false)

    @JvmOverloads
    fun hook(method: Executable, callback: Hooker, priority: Int = XposedInterface.PRIORITY_DEFAULT): HookHandle = synchronized(lock) {
        if (!isInstallationGenerationActive(installationGeneration()) || !isInstallationLeaseActive(installationLease.get())) {
            throw CancellationException("安装代次已失效")
        }
        val unhook = XposedRuntime.hook(method, priority, callback)
        unhooks.add(unhook)
        installationLease.get()?.installedHooks?.add(unhook)
        unhook
    }

    /** Install a shared hook at most once per key and installation generation. */
    @JvmOverloads
    fun hookOnce(key: String, method: Executable, callback: Hooker, priority: Int = XposedInterface.PRIORITY_DEFAULT): HookHandle =
        synchronized(lock) {
            require(key.isNotBlank()) { "Hook key must not be blank" }
            if (!isInstallationGenerationActive(installationGeneration()) || !isInstallationLeaseActive(installationLease.get())) {
                throw CancellationException("安装代次已失效")
            }
            keyedHooks[key]?.let { existing ->
                check(existing.generation == installationGeneration()) {
                    "Hook key '$key' is still owned by an earlier generation"
                }
                check(existing.member == method) { "Hook key '$key' is already assigned to ${existing.member}" }
                if (XposedRuntime.isHookActive(existing.unhook)) return@synchronized existing.unhook
                // Keep the old handle in unhooks / XposedRuntime's failed-removal set for explicit
                // cleanup, but stop treating its inert or physically removed callback as installed.
                // Identity-check the map entry so stale cleanup cannot erase a later replacement.
                if (keyedHooks[key] === existing) keyedHooks.remove(key)
            }
            val unhook = XposedRuntime.hook(method, priority, callback)
            unhooks.add(unhook)
            installationLease.get()?.installedHooks?.add(unhook)
            keyedHooks[key] = KeyedHook(installationGeneration(), method, unhook)
            unhook
        }

    fun add(unhook: HookHandle?) {
        if (unhook == null) return
        synchronized(lock) {
            if (isInstallationGenerationActive(installationGeneration()) && isInstallationLeaseActive(installationLease.get())) {
                if (unhooks.any { it === unhook }) return
                unhooks.add(unhook)
                installationLease.get()?.installedHooks?.add(unhook)
                return
            }
        }
        unhook.unhook()
        throw CancellationException("安装代次已失效")
    }

    fun size(): Int = synchronized(lock) { unhooks.size }

    fun isActive(handle: HookHandle?): Boolean = handle != null && XposedRuntime.isHookActive(handle)

    fun unhook(unhook: HookHandle?) {
        if (unhook == null) return
        unhook.unhook()
        // Release the module's handle after Xposed has removed the callback.
        synchronized(lock) {
            unhooks.remove(unhook)
            keyedHooks.entries.removeAll { it.value.unhook === unhook }
            installationLease.get()?.installedHooks?.remove(unhook)
        }
    }

    fun unhookAll() {
        XposedRuntime.retryFailedUnhooks()
        val (previous, cleanups) = synchronized(lock) {
            generation++
            val invalidated = generationCleanups.values.flatten()
            generationCleanups.clear()
            unhooks.toList() to invalidated
        }
        cleanups.forEach { it.invalidate() }
        for (unhook in previous) {
            runCatching { this.unhook(unhook) }
        }
    }

    companion object {
        private val INSTANCE = HookRegistry()

        @JvmStatic
        fun get(): HookRegistry = INSTANCE
    }

    private data class KeyedHook(
        val generation: Long,
        val member: Executable,
        val unhook: HookHandle
    )

    private data class InstallationLease(
        val generation: Long,
        val isActive: () -> Boolean,
        val installedHooks: MutableList<HookHandle> = ArrayList()
    )

    private class GenerationCleanup(
        private val owner: HookRegistry,
        val generation: Long,
        action: Runnable
    ) : AutoCloseable {
        private var action: Runnable? = action

        override fun close() {
            synchronized(this) { action = null }
            owner.removeGenerationCleanup(this)
        }

        fun invalidate() {
            val cleanup = synchronized(this) {
                action.also { action = null }
            }
            runCatching { cleanup?.run() }
        }
    }
}
