package h.Hchat.hooks.items.customfriendavatar

object AvatarLoadCoordinatorRegression {
    private class Target(var bindingToken: Long, var invalidations: Int = 0)

    fun run() {
        val scheduler = ManualAvatarRetryScheduler()
        val coordinator = AvatarLoadCoordinator<Target>(
            retryDelayMillis = 1_000L,
            clock = { scheduler.nowMillis },
            scheduler = scheduler
        )

        val evictable = Target(bindingToken = 1L)
        check(coordinator.request("avatar-a", evictable, 1L, { target, token ->
            target.bindingToken == token
        }, { it.invalidations++ }, { _, _ -> }, nowMillis = 0L))
        coordinator.complete("avatar-a", succeeded = true, nowMillis = 0L).forEach { it() }
        check(evictable.invalidations == 1)
        // The owning LRU can evict this successful bitmap; a later cache miss must start I/O again.
        check(coordinator.request("avatar-a", evictable, 1L, { target, token ->
            target.bindingToken == token
        }, { it.invalidations++ }, { _, _ -> }, nowMillis = 1L))
        coordinator.complete("avatar-a", succeeded = true, nowMillis = 1L)
        println("PASS: avatar cache eviction can reload a previously successful key")

        val failed = Target(bindingToken = 2L)
        check(coordinator.request("avatar-fail", failed, 2L, { target, token ->
            target.bindingToken == token
        }, { it.invalidations++ }, { _, _ -> }, nowMillis = 100L))
        coordinator.complete("avatar-fail", succeeded = false, nowMillis = 100L)
        check(!coordinator.request("avatar-fail", failed, 2L, { target, token ->
            target.bindingToken == token
        }, { it.invalidations++ }, { _, _ -> }, nowMillis = 500L)) { "A failed decode may use a short retry backoff" }
        check(coordinator.request("avatar-fail", failed, 2L, { target, token ->
            target.bindingToken == token
        }, { it.invalidations++ }, { _, _ -> }, nowMillis = 1_100L)) { "A failed decode must recover after backoff" }
        coordinator.complete("avatar-fail", succeeded = true, nowMillis = 1_100L)
        println("PASS: avatar decode failures are bounded and retryable")

        val first = Target(bindingToken = 3L)
        val second = Target(bindingToken = 4L)
        check(coordinator.request("avatar-shared", first, 3L, { target, token ->
            target.bindingToken == token
        }, { it.invalidations++ }, { _, _ -> }, nowMillis = 2_000L))
        check(!coordinator.request("avatar-shared", second, 4L, { target, token ->
            target.bindingToken == token
        }, { it.invalidations++ }, { _, _ -> }, nowMillis = 2_000L))
        val redraws = coordinator.complete("avatar-shared", succeeded = true, nowMillis = 2_000L)
        check(redraws.size == 2) { "One shared load must notify all live Drawables" }
        redraws.forEach { it() }
        check(first.invalidations == 1 && second.invalidations == 1)
        println("PASS: one avatar load refreshes every live Drawable waiter")

        val reused = Target(bindingToken = 5L)
        check(coordinator.request("avatar-reused", reused, 5L, { target, token ->
            target.bindingToken == token
        }, { it.invalidations++ }, { _, _ -> }, nowMillis = 3_000L))
        reused.bindingToken = 6L
        check(!coordinator.request("avatar-reused", reused, 6L, { target, token ->
            target.bindingToken == token
        }, { it.invalidations++ }, { _, _ -> }, nowMillis = 3_000L))
        coordinator.complete("avatar-reused", succeeded = true, nowMillis = 3_000L).forEach { it() }
        check(reused.invalidations == 1) { "A reused Drawable must receive only its current binding refresh" }

        val changedWxid = Target(bindingToken = 7L)
        check(coordinator.request("avatar-old-wxid", changedWxid, 7L, { _, _ -> false },
            { it.invalidations++ }, { _, _ -> }, nowMillis = 4_000L))
        coordinator.complete("avatar-old-wxid", succeeded = true, nowMillis = 4_000L).forEach { it() }
        check(changedWxid.invalidations == 0) { "A reused row must ignore an obsolete avatar binding" }
        println("PASS: stale avatar waiters are rejected after row binding reuse")
    }
}
