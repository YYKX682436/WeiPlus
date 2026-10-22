package h.Hchat.hooks.core

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member
import java.util.concurrent.CancellationException

/**
 * 统一保存 Xposed hook 句柄，便于防御性卸载。
 */
class HookRegistry private constructor() {
    private val lock = Any()
    private val unhooks = ArrayList<XC_MethodHook.Unhook>()
    private val keyedHooks = HashMap<String, KeyedHook>()
    private val installationLease = ThreadLocal<InstallationLease>()
    @Volatile private var generation = 0L

    fun installationGeneration(): Long = installationLease.get()?.generation ?: generation

    fun isInstallationGenerationActive(generation: Long): Boolean = this.generation == generation

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

    fun hook(method: Member, callback: XC_MethodHook): XC_MethodHook.Unhook = synchronized(lock) {
        if (!isInstallationGenerationActive(installationGeneration()) || !isInstallationLeaseActive(installationLease.get())) {
            throw CancellationException("安装代次已失效")
        }
        val unhook = XposedBridge.hookMethod(method, callback)
        unhooks.add(unhook)
        installationLease.get()?.installedHooks?.add(unhook)
        unhook
    }

    /** Install a shared hook at most once per key and installation generation. */
    fun hookOnce(key: String, method: Member, callback: XC_MethodHook): XC_MethodHook.Unhook =
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
                return@synchronized existing.unhook
            }
            val unhook = XposedBridge.hookMethod(method, callback)
            unhooks.add(unhook)
            installationLease.get()?.installedHooks?.add(unhook)
            keyedHooks[key] = KeyedHook(installationGeneration(), method, unhook)
            unhook
        }

    fun add(unhook: XC_MethodHook.Unhook?) {
        if (unhook == null) return
        synchronized(lock) {
            if (isInstallationGenerationActive(installationGeneration()) && isInstallationLeaseActive(installationLease.get())) {
                unhooks.add(unhook)
                installationLease.get()?.installedHooks?.add(unhook)
                return
            }
        }
        unhook.unhook()
        throw CancellationException("安装代次已失效")
    }

    fun size(): Int = synchronized(lock) { unhooks.size }

    fun unhook(unhook: XC_MethodHook.Unhook?) {
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
        val previous = synchronized(lock) {
            generation++
            unhooks.toList()
        }
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
        val member: Member,
        val unhook: XC_MethodHook.Unhook
    )

    private data class InstallationLease(
        val generation: Long,
        val isActive: () -> Boolean,
        val installedHooks: MutableList<XC_MethodHook.Unhook> = ArrayList()
    )
}
