package android.os
class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(looper: Looper) {
    fun post(action: Runnable): Boolean { queue.add(action); return true }
    fun postDelayed(action: Runnable, delay: Long): Boolean { delayed.add(action); return true }
    fun removeCallbacks(action: Runnable) { queue.removeAll { it === action }; delayed.removeAll { it === action } }
    companion object {
        val queue = ArrayList<Runnable>()
        val delayed = ArrayList<Runnable>()
        fun promoteDelayedToQueue() { queue.addAll(delayed.toList()); delayed.clear() }
        fun drain() { while(queue.isNotEmpty()) queue.removeAt(0).run() }
        fun finishTransition() { promoteDelayedToQueue(); drain() }
    }
}
