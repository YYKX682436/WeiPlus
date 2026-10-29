package h.Hchat.dexkit

import h.Hchat.WechatPackage

/** 仅按微信包体与补丁身份判断定位缓存是否仍属于同一运行时。 */
object DexCacheIdentity {
    @JvmStatic
    fun sameRuntime(cachedKey: String?, runtimeKey: String?): Boolean {
        if (cachedKey.isNullOrBlank() || runtimeKey.isNullOrBlank()) return false
        val cached = cachedKey.split('|')
        val runtime = runtimeKey.split('|')
        // Ten fields are required, including APK/DEX and patch-file metadata added after
        // the old seven-field identity. Some caches append a private schema suffix.
        if (cached.size < 10 || runtime.size < 10) return false
        if (!isCompleteRuntimeIdentity(cached) || !isCompleteRuntimeIdentity(runtime)) return false
        return cachedKey == runtimeKey
    }

    private fun isCompleteRuntimeIdentity(parts: List<String>): Boolean {
        if (!WechatPackage.isTarget(parts[0])) return false
        // Version fields are cache diagnostics; they never act as a module admission gate.
        if (parts[2].toLongOrNull() == null || parts[6].toLongOrNull() == null) return false
        if (parts[7].isBlank() || parts[8].isBlank() || parts[9].isBlank()) return false
        return true
    }
}
