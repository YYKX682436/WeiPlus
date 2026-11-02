package h.Hchat.hooks.items.customfriendavatar

object AvatarRevisionIsolationRegression {
    private class BoundTarget(val wxid: String, var token: Long = 1L)

    fun run() {
        failedRetryForBIsCancelledByAChange()
        decodingBIsCancelledByADeletion()
        println("PASS: changing avatar A preserves B's delayed retry and recovers a B decode invalidated by the global revision")
    }

    private fun failedRetryForBIsCancelledByAChange() {
        val scheduler = ManualAvatarRetryScheduler()
        val coordinator = coordinator(scheduler)
        var globalRevision = 1L
        var loads = 0
        var bitmapReady = false
        val targetB = BoundTarget("B")
        val refreshedAtRevisions = mutableListOf<Long>()
        lateinit var requestB: () -> Unit
        requestB = {
            val capturedRevision = globalRevision
            val key = avatarKey("B", capturedRevision)
            val started = coordinator.request(
                key = key,
                target = targetB,
                bindingToken = targetB.token,
                isCurrent = { current, token -> current.token == token && current.wxid == "B" },
                invalidate = { if (bitmapReady) refreshedAtRevisions += globalRevision },
                retry = { _, _ -> requestB() },
                nowMillis = scheduler.nowMillis
            )
            if (started) {
                loads++
                bitmapReady = loads > 1
                coordinator.complete(key, succeeded = bitmapReady, nowMillis = scheduler.nowMillis).forEach { it() }
            }
        }

        requestB()
        failOwnerA(coordinator, scheduler)
        check(scheduler.pendingCount() == 2) { "A and B should each own a pending retry before A changes" }
        globalRevision++ // Saving/deleting avatar A increments the store-wide revision.
        coordinator.cancelKeysWithPrefix(ownerPrefix("A"))
        check(scheduler.pendingCount() == 1) { "Changing A must cancel A's retry but preserve B's" }
        scheduler.advanceBy(2_000L)
        check(loads == 2) { "B's delayed retry must run while the page is otherwise idle" }
        check(refreshedAtRevisions == listOf(2L)) { "B must reload from the current global generation and refresh" }
        check(scheduler.pendingCount() == 0) { "Successful B recovery must clear its retry timer" }
    }

    private fun decodingBIsCancelledByADeletion() {
        val scheduler = ManualAvatarRetryScheduler()
        val coordinator = coordinator(scheduler)
        var globalRevision = 1L
        var loads = 0
        var bitmapReady = false
        val targetB = BoundTarget("B")
        var firstKey = ""
        var capturedRevision = 0L
        val refreshedAtRevisions = mutableListOf<Long>()
        lateinit var requestB: () -> Unit
        requestB = {
            capturedRevision = globalRevision
            val key = avatarKey("B", capturedRevision)
            val started = coordinator.request(
                key = key,
                target = targetB,
                bindingToken = targetB.token,
                isCurrent = { current, token -> current.token == token && current.wxid == "B" },
                invalidate = { if (bitmapReady) refreshedAtRevisions += globalRevision },
                retry = { _, _ -> requestB() },
                nowMillis = scheduler.nowMillis
            )
            if (started) {
                loads++
                if (firstKey.isEmpty()) firstKey = key
                else {
                    bitmapReady = true
                    coordinator.complete(key, succeeded = true, nowMillis = scheduler.nowMillis).forEach { it() }
                }
            }
        }

        requestB() // Start an asynchronous B decode, but do not complete it yet.
        failOwnerA(coordinator, scheduler)
        check(scheduler.pendingCount() == 1) { "A's retry should be pending beside B's in-flight decode" }
        globalRevision++ // Deleting A invalidates B's in-flight global-generation check.
        coordinator.cancelKeysWithPrefix(ownerPrefix("A"))
        val decodedSuccessfully = capturedRevision == globalRevision
        check(!decodedSuccessfully) { "The store-wide revision fence must still reject B's stale decode" }
        coordinator.complete(firstKey, succeeded = decodedSuccessfully, nowMillis = scheduler.nowMillis).forEach { it() }
        check(scheduler.pendingCount() == 1) { "Invalidated B decode must schedule a replacement retry" }
        scheduler.advanceBy(2_000L)
        check(loads == 2) { "The B decode invalidated by A's deletion must be retried" }
        check(refreshedAtRevisions == listOf(2L)) { "Recovered B load must use and refresh the current generation" }
        check(scheduler.pendingCount() == 0)
    }

    private fun failOwnerA(coordinator: AvatarLoadCoordinator<BoundTarget>, scheduler: ManualAvatarRetryScheduler) {
        val target = BoundTarget("A")
        val key = avatarKey("A", 1L)
        check(coordinator.request(
            key = key,
            target = target,
            bindingToken = target.token,
            isCurrent = { current, token -> current.token == token && current.wxid == "A" },
            invalidate = {},
            retry = { _, _ -> error("A's pending retry must be cancelled by its own change") },
            nowMillis = scheduler.nowMillis
        ))
        coordinator.complete(key, succeeded = false, nowMillis = scheduler.nowMillis)
    }

    private fun coordinator(scheduler: ManualAvatarRetryScheduler) = AvatarLoadCoordinator<BoundTarget>(
        retryDelayMillis = 2_000L,
        clock = { scheduler.nowMillis },
        scheduler = scheduler
    )

    private fun avatarKey(wxid: String, revision: Long): String = "test.pkg\n$wxid\n$revision"
    private fun ownerPrefix(wxid: String): String = "test.pkg\n$wxid\n"
}
