package h.Hchat.hooks.items.customfriendavatar

internal class ManualAvatarRetryScheduler(
    var nowMillis: Long = 0L
) : AvatarRetryScheduler {
    private data class Task(
        val dueAt: Long,
        val delayMillis: Long,
        val action: () -> Unit,
        var cancelled: Boolean = false
    )

    private val tasks = mutableListOf<Task>()
    val scheduledDelays = mutableListOf<Long>()
    var maxOutstandingTasks = 0
        private set

    override fun schedule(delayMillis: Long, action: () -> Unit): AvatarRetryHandle {
        val task = Task(nowMillis + delayMillis, delayMillis, action)
        tasks += task
        scheduledDelays += delayMillis
        maxOutstandingTasks = maxOf(maxOutstandingTasks, pendingCount())
        return AvatarRetryHandle { task.cancelled = true }
    }

    fun advanceBy(delayMillis: Long) {
        nowMillis += delayMillis
        while (true) {
            val next = tasks.filter { !it.cancelled && it.dueAt <= nowMillis }
                .minByOrNull { it.dueAt } ?: break
            tasks.remove(next)
            if (!next.cancelled) next.action()
        }
        tasks.removeAll { it.cancelled }
    }

    fun pendingCount(): Int = tasks.count { !it.cancelled }
}
