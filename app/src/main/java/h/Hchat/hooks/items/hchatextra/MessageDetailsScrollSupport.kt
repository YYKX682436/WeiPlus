package h.Hchat.hooks.items.hchatextra

import android.view.View
import android.widget.AbsListView
import android.widget.HeaderViewListAdapter
import android.widget.ListView
import android.widget.ScrollView
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

/** Runtime type and reflection helpers for supported vertical chat viewports. */
internal object MessageDetailsScrollSupport {
    enum class ContainerKind { RECYCLER, ABS_LIST, SCROLL, UNKNOWN }

    data class ItemProbe(val available: Boolean, val value: Any?)
    data class CacheStats(val containerTypeResolutions: Long, val methodResolutions: Long)

    private data class MethodKey(val name: String, val parameterTypes: List<Class<*>>)
    private data class CachedMethod(val method: Method?)
    private val methods = ConcurrentHashMap<Class<*>, ConcurrentHashMap<MethodKey, CachedMethod>>()
    private val containerTypeResolutions = AtomicLong()
    private val methodResolutions = AtomicLong()
    private val containerKinds = ConcurrentHashMap<Class<*>, ContainerKind>()

    private fun resolveContainerKind(type: Class<*>): ContainerKind {
        containerTypeResolutions.incrementAndGet()
        return when {
            AbsListView::class.java.isAssignableFrom(type) -> ContainerKind.ABS_LIST
            ScrollView::class.java.isAssignableFrom(type) -> ContainerKind.SCROLL
            recyclerViewType(type) -> ContainerKind.RECYCLER
            else -> ContainerKind.UNKNOWN
        }
    }

    fun containerKind(view: View): ContainerKind = containerKinds.computeIfAbsent(view.javaClass, ::resolveContainerKind)

    fun isRecyclerView(view: View): Boolean = containerKind(view) == ContainerKind.RECYCLER

    /** Ordinary ScrollView has geometry, but no adapter identity/latest-message contract. */
    fun supportsAutomaticTailCorrection(view: View): Boolean = when (containerKind(view)) {
        ContainerKind.RECYCLER, ContainerKind.ABS_LIST -> true
        ContainerKind.SCROLL, ContainerKind.UNKNOWN -> false
    }

    fun hostMethod(view: View, name: String, vararg parameterTypes: Class<*>): Method? =
        findCompatibleMethod(view.javaClass, name, parameterTypes.toList())

    fun cacheStats(): CacheStats = CacheStats(
        containerTypeResolutions.get(), methodResolutions.get()
    )

    fun adapter(container: View): Any? = when (containerKind(container)) {
        ContainerKind.RECYCLER -> invokeNoArg(container, "getAdapter")
        ContainerKind.ABS_LIST -> (container as AbsListView).adapter
        else -> null
    }

    fun adapterItemCount(container: View): Int? = when (containerKind(container)) {
        ContainerKind.RECYCLER -> (adapter(container)?.let { invokeNoArg(it, "getItemCount") } as? Number)?.toInt()
        ContainerKind.ABS_LIST -> (container as AbsListView).adapter?.count
        else -> null
    }

    /** Positive framework metadata only. RecyclerView has no standard footer contract. */
    fun isExplicitFooter(container: View, adapter: Any, position: Int): Boolean {
        val list = container as? ListView ?: return false
        val fixed = adapter as? HeaderViewListAdapter ?: return false
        if (list.adapter !== fixed || list.footerViewsCount <= 0) return false
        val contentCount = fixed.wrappedAdapter?.count ?: 0
        val footerStart = fixed.headersCount + contentCount
        if (footerStart + list.footerViewsCount != fixed.count) return false
        return position in footerStart until fixed.count
    }

    fun childAdapterPosition(container: View, row: View): Int? {
        return when (containerKind(container)) {
            ContainerKind.RECYCLER -> {
                var current: View? = row
                var layoutManager: Any? = null
                var layoutManagerResolved = false
                while (current != null && current !== container) {
                    val position = sequenceOf(
                        invoke(container, "getChildAdapterPosition", current),
                        invoke(container, "getChildLayoutPosition", current)
                    ).mapNotNull { (it as? Number)?.toInt()?.takeIf { value -> value >= 0 } }
                        .firstOrNull()
                    position?.let { return it }
                    if (!layoutManagerResolved) {
                        layoutManager = invokeNoArg(container, "getLayoutManager")
                        layoutManagerResolved = true
                    }
                    val managerPosition = layoutManager?.let { manager ->
                        (invoke(manager, "getPosition", current) as? Number)?.toInt()?.takeIf { it >= 0 }
                    }
                    managerPosition?.let { return it }
                    val holder = invoke(container, "getChildViewHolder", current)
                    val holderPosition = sequenceOf(
                        holder?.let { invokeNoArg(it, "getBindingAdapterPosition") },
                        holder?.let { invokeNoArg(it, "getAbsoluteAdapterPosition") },
                        holder?.let { invokeNoArg(it, "getAdapterPosition") },
                        holder?.let { invokeNoArg(it, "getLayoutPosition") }
                    ).mapNotNull { (it as? Number)?.toInt()?.takeIf { value -> value >= 0 } }
                        .firstOrNull()
                    holderPosition?.let { return it }
                    current = current.parent as? View
                }
                null
            }
            ContainerKind.ABS_LIST -> (container as AbsListView).getPositionForView(row)
                .takeIf { it >= 0 }
            else -> null
        }
    }

    /** Reads AndroidX class identity from the view's own loader, avoiding module/host loader copies. */
    private fun recyclerViewType(type: Class<*>): Boolean {
        val loader = type.classLoader
        val recyclerClass = runCatching {
            Class.forName("androidx.recyclerview.widget.RecyclerView", false, loader)
        }.getOrNull() ?: runCatching {
            Class.forName("androidx.recyclerview.widget.RecyclerView", false, MessageDetailsScrollSupport::class.java.classLoader)
        }.getOrNull()
        return recyclerClass?.isAssignableFrom(type) == true
    }

    fun invokeNoArg(target: Any, name: String): Any? = invokeMethod(target, name, emptyArray())

    fun invoke(target: Any, name: String, vararg args: Any): Any? = invokeMethod(target, name, args)

    private fun invokeMethod(target: Any, name: String, args: Array<out Any>): Any? {
        val method = findCompatibleMethod(target.javaClass, name, args.map { it.javaClass }) ?: return null
        return runCatching { method.invoke(target, *args) }.getOrNull()
    }

    private fun findCompatibleMethod(type: Class<*>, name: String, argumentTypes: List<Class<*>>): Method? {
        val cacheKey = MethodKey(name, argumentTypes)
        return methods.computeIfAbsent(type) { ConcurrentHashMap() }.computeIfAbsent(cacheKey) {
            methodResolutions.incrementAndGet()
            CachedMethod(findMethod(type, name, argumentTypes))
        }.method
    }

    private fun findMethod(type: Class<*>, name: String, parameterTypes: List<Class<*>>): Method? {
        generateSequence(type as Class<*>?) { it.superclass }.forEach { current ->
            val found = current.declaredMethods.firstOrNull { method ->
                method.name == name && method.parameterTypes.size == parameterTypes.size &&
                    method.parameterTypes.zip(parameterTypes).all { (parameter, argument) ->
                        isCompatible(parameter, argument)
                    }
            }
            if (found != null) return runCatching { found.apply { isAccessible = true } }.getOrNull() ?: found
        }
        return null
    }

    private fun isCompatible(parameter: Class<*>, argument: Class<*>): Boolean {
        if (parameter.isAssignableFrom(argument)) return true
        if (!parameter.isPrimitive) return false
        return when (parameter) {
            java.lang.Integer.TYPE -> argument == java.lang.Integer::class.java
            java.lang.Boolean.TYPE -> argument == java.lang.Boolean::class.java
            java.lang.Long.TYPE -> argument == java.lang.Long::class.java
            java.lang.Float.TYPE -> argument == java.lang.Float::class.java
            java.lang.Double.TYPE -> argument == java.lang.Double::class.java
            java.lang.Short.TYPE -> argument == java.lang.Short::class.java
            java.lang.Byte.TYPE -> argument == java.lang.Byte::class.java
            java.lang.Character.TYPE -> argument == java.lang.Character::class.java
            else -> false
        }
    }

    /**
     * Confirms the newest message using current adapter data. Null adapter items may be structural
     * rows (for example ListView footers). Every suffix row needs explicit structural evidence;
     * null or unknown data alone is never treated as proof of a footer.
     */
    fun isConfirmedLatestMessage(
        position: Int,
        itemCount: Int,
        itemAt: (Int) -> ItemProbe,
        isMessageItem: (Any) -> Boolean,
        candidateMatchesIdentity: (Any) -> Boolean,
        isKnownStructuralRow: (Int, Any?) -> Boolean = { _, _ -> false }
    ): Boolean {
        if (position < 0 || position >= itemCount) return false
        for (nextPosition in position + 1 until itemCount) {
            val next = itemAt(nextPosition)
            if (!next.available) return false
            val item = next.value
            if (item != null && isMessageItem(item)) return false
            if (!isKnownStructuralRow(nextPosition, item)) return false
        }
        val candidate = itemAt(position)
        return candidate.available && candidate.value != null && candidateMatchesIdentity(candidate.value)
    }

    fun delayedRowStillMatches(
        expectedPosition: Int,
        actualPosition: Int,
        expectedIdentity: MessageDetailsIdentity?,
        currentIdentity: MessageDetailsIdentity?,
        bindIsCurrent: Boolean,
        rowIsAttached: Boolean,
        featureEnabled: Boolean,
        latestMessageConfirmed: Boolean,
        followingLatest: Boolean
    ): Boolean = expectedPosition >= 0 && expectedPosition == actualPosition &&
        expectedIdentity != null && currentIdentity != null && expectedIdentity.sameMessage(currentIdentity) &&
        bindIsCurrent && rowIsAttached && featureEnabled && latestMessageConfirmed && followingLatest

    fun isWithin(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    fun visibleAdapterRange(container: View): IntRange? = when (containerKind(container)) {
        ContainerKind.ABS_LIST -> {
            val list = container as AbsListView
            val first = list.firstVisiblePosition
            val last = list.lastVisiblePosition
            (first..last).takeIf { first >= 0 && last >= first }
        }
        ContainerKind.RECYCLER -> {
            val manager = invokeNoArg(container, "getLayoutManager") ?: return null
            val first = (invokeNoArg(manager, "findFirstVisibleItemPosition") as? Number)?.toInt() ?: return null
            val last = (invokeNoArg(manager, "findLastVisibleItemPosition") as? Number)?.toInt() ?: return null
            val count = adapterItemCount(container) ?: return null
            (first..last).takeIf { first >= 0 && last >= first && last < count }
        }
        ContainerKind.SCROLL, ContainerKind.UNKNOWN -> null
    }

    fun canScrollTowardHistory(container: View): Boolean = when (container) {
        is AbsListView -> container.firstVisiblePosition > 0 ||
            (container.getChildAt(0)?.top ?: container.paddingTop) < container.paddingTop
        else -> container.canScrollVertically(-1)
    }

    /** Only observe the native animation; never stop or replace it. */
    fun isRecyclerSettling(container: View): Boolean =
        containerKind(container) == ContainerKind.RECYCLER &&
            (invokeNoArg(container, "getScrollState") as? Number)?.toInt() == 2

    fun correctBy(container: View, overflow: Int): Boolean {
        if (overflow <= 0 || !container.canScrollVertically(1)) return false
        return when (containerKind(container)) {
            ContainerKind.RECYCLER -> {
                container.scrollBy(0, overflow)
                true
            }
            ContainerKind.ABS_LIST -> {
                (container as AbsListView).scrollListBy(overflow)
                true
            }
            ContainerKind.SCROLL -> {
                container.scrollBy(0, overflow)
                true
            }
            ContainerKind.UNKNOWN -> false
        }
    }
}
