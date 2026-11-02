package h.Hchat.hooks.core

/** Thread-safe single-instance holder for feature runtimes reconciled by config and DexReady. */
internal class FeatureRuntimeSlot<T : Any> {
    private var value: T? = null

    @Synchronized
    fun current(): T? = value

    @Synchronized
    fun getOrCreate(create: () -> T): T = value ?: create().also { value = it }

    fun clear(close: (T) -> Unit) {
        val previous = synchronized(this) {
            value.also { value = null }
        }
        previous?.let(close)
    }
}
