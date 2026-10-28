package h.Hchat.dexkit

/** 仅按微信包体与补丁身份判断定位缓存是否仍属于同一运行时。 */
object DexCacheIdentity {
    @JvmStatic
    fun sameRuntime(cachedKey: String?, runtimeKey: String?): Boolean {
        if (cachedKey.isNullOrBlank() || runtimeKey.isNullOrBlank()) return false
        val cached = cachedKey.split('|')
        val runtime = runtimeKey.split('|')
        // Ten fields are required, including the APK/DEX and Tinker patch fields added
        // after the old seven-field identity. The final cache-scope marker is part of it.
        // Some feature caches append their own schema suffix after the runtime key.
        // Require the full ten-part runtime identity, then compare the complete key.
        if (cached.size < 10 || runtime.size < 10) return false
        return cachedKey == runtimeKey
    }
}
