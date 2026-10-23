package android.os
class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(looper: Looper) {
    fun post(action: () -> Unit) { queue.add(action) }
    fun postDelayed(action: Runnable, delay: Long) { delayed.add(action) }
    fun removeCallbacks(action: Runnable) { delayed.removeAll { it === action } }
    companion object {
        val queue = ArrayList<() -> Unit>()
        val delayed = ArrayList<Runnable>()
        fun drain() { while(queue.isNotEmpty()) queue.removeAt(0)() }
        fun finishTransition() { val actions = delayed.toList(); delayed.clear(); actions.forEach { it.run() }; drain() }
    }
}
