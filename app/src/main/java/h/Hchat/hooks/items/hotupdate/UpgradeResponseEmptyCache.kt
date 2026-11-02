package h.Hchat.hooks.items.hotupdate

import h.Hchat.dexkit.DexCacheIdentity
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** A separate, identity-bound negative result; never edits the shared method cache. */
internal class UpgradeResponseEmptyCache(private val directory: File) {
    class State internal constructor(private val marker: File?, private val positive: File?, private val unsafe: File?) {
        fun isEmpty(): Boolean = runCatching {
            marker?.isFile == true && positive?.exists() != true && unsafe?.exists() != true
        }.getOrDefault(false)

        fun markEmpty() {
            runCatching { if (positive?.exists() != true && unsafe?.exists() != true) marker?.createNewFile() }
                .onFailure { disable() }
        }

        fun markPositive() {
            runCatching {
                if (positive != null && !positive.isFile && !positive.createNewFile()) error("positive marker unavailable")
                invalidateOrThrow()
            }.onFailure { disable() }
        }

        fun invalidate() { runCatching { invalidateOrThrow() }.onFailure { disable() } }

        private fun invalidateOrThrow() {
            if (marker != null && marker.exists() && !marker.delete()) error("Cannot invalidate upgrade empty result")
        }

        private fun disable() {
            // A sticky shared guard forces later processes to re-query after a storage failure.
            runCatching { unsafe?.createNewFile() }
            runCatching { marker?.delete() }
        }
    }

    fun <T> withIdentity(identity: String, rule: String, action: (State) -> T): T {
        if (!DexCacheIdentity.sameRuntime(identity, identity) || rule.isBlank()) return action(State(null, null, null))
        val name = MessageDigest.getInstance("SHA-256")
            .digest((rule + "\u0000" + identity).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        synchronized(monitor) {
            val file = try {
                if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) return action(State(null, null, null))
                RandomAccessFile(File(directory, "upgrade-response.lock"), "rw")
            } catch (_: Exception) {
                runCatching { File(directory, "upgrade-response-unsafe").createNewFile() }
                runCatching { File(directory, "upgrade-response-$name.empty").delete() }
                return action(State(null, null, null))
            }
            file.use {
                val lock = try { it.channel.lock() } catch (_: Exception) {
                    runCatching { File(directory, "upgrade-response-unsafe").createNewFile() }
                    runCatching { File(directory, "upgrade-response-$name.empty").delete() }
                    return action(State(null, null, null))
                }
                lock.use { return action(State(File(directory, "upgrade-response-$name.empty"), File(directory, "upgrade-response-$name.positive"), File(directory, "upgrade-response-unsafe"))) }
            }
        }
    }

    companion object { private val monitor = Any() }
}
