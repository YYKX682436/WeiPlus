package h.Hchat.hooks.items.hotupdate

/** Resolves this locator only. A failed or partially resolved query is never a negative hit. */
internal object UpgradeResponseResolver {
    fun <T : Any, C> resolve(
        marker: UpgradeResponseEmptyCache.State,
        cached: List<T>,
        invalid: Boolean,
        query: () -> List<C>,
        resolveCandidate: (C) -> T?,
        savePositive: (List<T>) -> Unit
    ): List<T> {
        if (cached.isNotEmpty() && !invalid) {
            marker.markPositive()
            return cached
        }
        if (invalid) marker.invalidate()
        if (!invalid && marker.isEmpty()) return emptyList()
        val candidates = query()
        if (candidates.isEmpty()) {
            if (!invalid) marker.markEmpty()
            return emptyList()
        }
        val resolved = candidates.mapNotNull(resolveCandidate)
        if (resolved.isNotEmpty()) marker.markPositive() else marker.invalidate()
        // A partial result can be hooked now, but is never persisted as a complete snapshot.
        if (resolved.size == candidates.size) savePositive(resolved)
        return resolved
    }
}
