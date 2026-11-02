package h.Hchat.hooks.items.customfriendavatar

object AvatarRetryWakeupRegression {
    private class PageTarget(var token: Long = 1L, var redraws: Int = 0)

    private fun coordinator(scheduler: ManualAvatarRetryScheduler) = AvatarLoadCoordinator<PageTarget>(
        retryDelayMillis = 2_000L,
        maxRetryDelayMillis = 60_000L,
        clock = { scheduler.nowMillis },
        scheduler = scheduler
    )

    fun run() {
        automaticRetryRefreshesAStaticPage()
        sameKeyDrawablesShareOneRetryLoad()
        sustainedFailuresBackOffAndUseOneTimer()
        differentKeysCannotAccumulateTimersWithoutBound()
        cancelAndRebindRejectOldRetries()
        destroyCancelsRetryTasks()
        println("PASS: failed avatar load auto-wakes a static page, refreshes success, backs off, and cancels stale retries")
    }

    private fun automaticRetryRefreshesAStaticPage() {
        val scheduler = ManualAvatarRetryScheduler()
        val coordinator = coordinator(scheduler)
        val page = PageTarget()
        var cached = false
        var loadRequests = 0
        var successfulDraws = 0
        lateinit var draw: () -> Unit
        draw = {
            if (cached) {
                successfulDraws++
            } else {
                val started = coordinator.request(
                    key = "static-avatar",
                    target = page,
                    bindingToken = page.token,
                    isCurrent = { target, token -> target.token == token },
                    invalidate = { target ->
                        target.redraws++
                        draw()
                    },
                    retry = { _, _ -> draw() },
                    nowMillis = scheduler.nowMillis
                )
                if (started) {
                    loadRequests++
                    cached = loadRequests == 2
                    coordinator.complete("static-avatar", cached, scheduler.nowMillis).forEach { it() }
                }
            }
        }

        draw()
        check(loadRequests == 1)
        check(scheduler.pendingCount() == 1) { "A decode failure must create one delayed wake-up" }
        check(page.redraws == 1) { "The immediate failure redraw is attempted inside backoff" }

        // The page stays idle. The only event is the injected clock/scheduler advancing.
        scheduler.advanceBy(1_999L)
        check(loadRequests == 1)
        scheduler.advanceBy(1L)
        check(loadRequests == 2) { "Backoff expiry must retry without another draw or bind" }
        check(successfulDraws == 1) { "A successful retry must invalidate and redraw the still-bound target" }
        check(scheduler.pendingCount() == 0) { "Success must cancel the retry chain" }
    }

    private fun sustainedFailuresBackOffAndUseOneTimer() {
        val scheduler = ManualAvatarRetryScheduler()
        val coordinator = coordinator(scheduler)
        val page = PageTarget()
        var loadRequests = 0
        lateinit var draw: () -> Unit
        draw = {
            val started = coordinator.request(
                key = "persistent-failure",
                target = page,
                bindingToken = page.token,
                isCurrent = { target, token -> target.token == token },
                invalidate = { target ->
                    target.redraws++
                    draw()
                },
                retry = { _, _ -> draw() },
                nowMillis = scheduler.nowMillis
            )
            if (started) {
                loadRequests++
                coordinator.complete("persistent-failure", succeeded = false, nowMillis = scheduler.nowMillis)
                    .forEach { it() }
            }
        }

        draw()
        val expectedDelays = listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L)
        repeat(expectedDelays.size - 1) { retryIndex ->
            check(scheduler.pendingCount() == 1) { "There must be at most one timer for a failing key" }
            val delay = scheduler.scheduledDelays.last()
            check(delay == expectedDelays[retryIndex]) { "Unexpected retry delay: $delay" }
            scheduler.advanceBy(delay)
            check(loadRequests == retryIndex + 2) { "Each timer should start exactly one retry" }
        }
        check(scheduler.scheduledDelays == expectedDelays)
        check(scheduler.maxOutstandingTasks == 1) { "Repeated failures must not accumulate retry timers" }
        check(scheduler.pendingCount() == 1)
        coordinator.clear()
        check(scheduler.pendingCount() == 0)
    }

    private fun sameKeyDrawablesShareOneRetryLoad() {
        val scheduler = ManualAvatarRetryScheduler()
        val coordinator = coordinator(scheduler)
        val first = PageTarget(token = 1L)
        val second = PageTarget(token = 2L)
        var loadStarts = 0
        var cached = false
        val refreshed = mutableMapOf<PageTarget, Int>()
        fun invalidate(target: PageTarget) {
            refreshed[target] = refreshed.getOrDefault(target, 0) + 1
        }
        fun request(target: PageTarget): Boolean {
            if (cached) {
                coordinator.cancelTarget(target)
                invalidate(target)
                return false
            }
            val started = coordinator.request(
                key = "shared-retry-key",
                target = target,
                bindingToken = target.token,
                isCurrent = { page, token -> page.token == token },
                invalidate = ::invalidate,
                retry = { page, _ -> request(page) },
                nowMillis = scheduler.nowMillis
            )
            if (started) {
                loadStarts++
                cached = loadStarts == 2
                coordinator.complete("shared-retry-key", succeeded = cached, nowMillis = scheduler.nowMillis)
                    .forEach { it() }
            }
            return started
        }

        check(request(first))
        check(scheduler.pendingCount() == 1)
        check(!request(second))
        check(scheduler.pendingCount() == 1) { "A shared avatar key must own one retry timer" }
        scheduler.advanceBy(2_000L)
        check(loadStarts == 2) { "All waiters for one key should merge into a single retry load" }
        check(refreshed.getOrDefault(first, 0) > 0 && refreshed.getOrDefault(second, 0) > 0) {
            "A fast retry success must refresh waiters even if the cache fills before their callback runs"
        }
    }

    private fun cancelAndRebindRejectOldRetries() {
        val scheduler = ManualAvatarRetryScheduler()
        val coordinator = coordinator(scheduler)
        val page = PageTarget(token = 10L)
        var loads = 0
        var currentRefreshes = 0
        var successReady = false
        val key = "avatar-rebind"
        fun request(token: Long, succeed: Boolean) {
            val started = coordinator.request(
                key = key,
                target = page,
                bindingToken = token,
                isCurrent = { target, expected -> target.token == expected },
                invalidate = { target ->
                    target.redraws++
                    if (successReady) currentRefreshes++
                },
                retry = { _, expected -> request(expected, succeed = false) },
                nowMillis = scheduler.nowMillis
            )
            if (started) {
                loads++
                successReady = succeed
                coordinator.complete(key, succeed, scheduler.nowMillis).forEach { it() }
            }
        }

        request(token = 10L, succeed = false)
        check(scheduler.pendingCount() == 1)
        page.token = 11L
        coordinator.cancelTarget(page)
        check(scheduler.pendingCount() == 0) { "Rebinding must cancel the old target's only retry" }
        request(token = 11L, succeed = true)
        check(loads == 2 && currentRefreshes == 1) { "The new binding should load and refresh normally" }
        scheduler.advanceBy(120_000L)
        check(loads == 2 && currentRefreshes == 1) { "No old token may retry or refresh after rebinding" }

        val deletedKey = "avatar-owner\nwxid-delete\n1"
        val deleted = PageTarget(token = 20L)
        check(coordinator.request(
            deletedKey, deleted, 20L,
            { target, token -> target.token == token },
            { it.redraws++ },
            { _, _ -> error("Deleted avatar must not retry") },
            nowMillis = scheduler.nowMillis
        ))
        coordinator.complete(deletedKey, succeeded = false, nowMillis = scheduler.nowMillis)
        coordinator.cancelKeysWithPrefix("avatar-owner\nwxid-delete\n")
        scheduler.advanceBy(120_000L)
        check(deleted.redraws == 0) { "Deleting an avatar must cancel its pending refresh" }
    }

    private fun differentKeysCannotAccumulateTimersWithoutBound() {
        val scheduler = ManualAvatarRetryScheduler()
        val coordinator = AvatarLoadCoordinator<PageTarget>(
            retryDelayMillis = 2_000L,
            maxPendingRetries = 2,
            clock = { scheduler.nowMillis },
            scheduler = scheduler
        )
        val retryCalls = mutableListOf<String>()
        repeat(3) { index ->
            val key = "avatar-$index"
            val target = PageTarget(token = index.toLong())
            check(coordinator.request(
                key, target, target.token,
                { page, token -> page.token == token },
                { it.redraws++ },
                { _, _ -> retryCalls += key },
                nowMillis = scheduler.nowMillis
            ))
            coordinator.complete(key, succeeded = false, nowMillis = scheduler.nowMillis)
        }
        check(scheduler.pendingCount() == 2 && scheduler.maxOutstandingTasks == 2) {
            "The per-key retry timer set must stay within its configured cap"
        }
        scheduler.advanceBy(2_000L)
        check(retryCalls == listOf("avatar-1", "avatar-2")) {
            "The oldest retry should be cancelled when the bounded retry table is full"
        }
        check(scheduler.pendingCount() == 0)
    }

    private fun destroyCancelsRetryTasks() {
        val scheduler = ManualAvatarRetryScheduler()
        val coordinator = coordinator(scheduler)
        val page = PageTarget(token = 30L)
        var retries = 0
        check(coordinator.request(
            "destroyed-avatar", page, page.token,
            { target, token -> target.token == token },
            { it.redraws++ },
            { _, _ -> retries++ },
            nowMillis = scheduler.nowMillis
        ))
        coordinator.complete("destroyed-avatar", succeeded = false, nowMillis = scheduler.nowMillis)
        coordinator.clear()
        scheduler.advanceBy(120_000L)
        check(retries == 0 && scheduler.pendingCount() == 0) { "Destroy/disable must cancel queued retries" }
    }
}
