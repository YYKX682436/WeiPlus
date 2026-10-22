package android.os

class Looper {
    companion object { fun getMainLooper() = Looper() }
}

class Handler(looper: Looper) {
    companion object {
        private val pending = mutableListOf<Pair<Handler, Runnable>>()
        fun runNextForTests(): Boolean {
            val task = synchronized(pending) { if (pending.isEmpty()) null else pending.removeAt(0) } ?: return false
            task.second.run()
            return true
        }
        fun pendingForTests(): Int = synchronized(pending) { pending.size }
    }

    fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
        synchronized(pending) { pending += this to runnable }
        return true
    }

    fun removeCallbacks(runnable: Runnable) {
        synchronized(pending) { pending.removeAll { it.first === this && it.second === runnable } }
    }
}
