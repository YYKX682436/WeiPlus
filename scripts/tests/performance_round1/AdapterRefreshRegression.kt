package h.Hchat.hooks.core

private open class WrapperAdapter {
    @JvmField var contentUpdates = 0
    @JvmField var invalidations = 0
    fun getItemCount(): Int = 8
    fun notifyItemRangeChanged(start: Int, size: Int) { check(start == 0 && size == 8); contentUpdates++ }
    fun notifyDataSetChanged() { invalidations++ }
}
private class ChatAdapter : WrapperAdapter()
private class NamedAdapter {
    @JvmField var notifications = 0
    fun notifyDataSetChanged() { notifications++ }
    fun unrelated() { error("Wrong method") }
}
private class Unrelated { fun erase() { error("Unrelated method invoked") } }

fun main() {
    val wrapped = ChatAdapter()
    check(ChatAdapterRefresh.notifyChanged(wrapped) && wrapped.contentUpdates == 1 && wrapped.invalidations == 0)
    println("PASS: inherited content-range update rebinds all current wrapper adapter rows")
    val named = NamedAdapter()
    check(ChatAdapterRefresh.notifyChanged(named) && named.notifications == 1)
    println("PASS: standard full notifier remains the fallback for adapters without the range contract")
    check(!ChatAdapterRefresh.notifyChanged(Unrelated()))
    println("PASS: unrelated zero-argument methods are never invoked")
}
