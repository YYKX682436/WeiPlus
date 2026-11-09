package h.Hchat.hooks.items.batch

internal object BatchOfficialUnfollowFinalStateVerifier {
    const val DEFAULT_MAX_ATTEMPTS = 41
    const val DEFAULT_POLL_INTERVAL_MS = 250L

    data class Read(
        val success: Boolean,
        val followedUsernames: Set<String>,
        val error: String = ""
    )

    data class Result(
        val lastRead: Read?,
        val stillFollowed: Set<String>,
        val attempts: Int,
        val accountCurrent: Boolean,
        val stopped: Boolean
    )

    fun awaitRemoval(
        targets: Set<String>,
        shouldContinue: () -> Boolean,
        isAccountCurrent: () -> Boolean,
        read: () -> Read,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MS,
        pause: (Long) -> Unit = { Thread.sleep(it) }
    ): Result {
        require(maxAttempts > 0)
        require(pollIntervalMillis >= 0)

        var attempts = 0
        var lastRead: Read? = null
        var stillFollowed = emptySet<String>()
        var stopped = false
        var accountCurrent = true

        while (attempts < maxAttempts) {
            accountCurrent = isAccountCurrent()
            if (!accountCurrent) break
            if (!shouldContinue()) {
                stopped = true
                break
            }

            attempts++
            lastRead = try {
                read()
            } catch (failure: Throwable) {
                Read(
                    success = false,
                    followedUsernames = emptySet(),
                    error = failure.message?.takeIf { it.isNotBlank() }
                        ?: failure.javaClass.simpleName
                )
            }
            stillFollowed = if (lastRead.success) {
                lastRead.followedUsernames.intersect(targets)
            } else {
                emptySet()
            }

            if (lastRead.success && stillFollowed.isEmpty()) break
            if (attempts >= maxAttempts) break

            if (!isAccountCurrent()) {
                accountCurrent = false
                break
            }
            if (!shouldContinue()) {
                stopped = true
                break
            }
            try {
                pause(pollIntervalMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                stopped = true
                break
            }
        }

        accountCurrent = isAccountCurrent()
        if (!shouldContinue()) stopped = true
        return Result(
            lastRead = lastRead,
            stillFollowed = stillFollowed,
            attempts = attempts,
            accountCurrent = accountCurrent,
            stopped = stopped
        )
    }
}
