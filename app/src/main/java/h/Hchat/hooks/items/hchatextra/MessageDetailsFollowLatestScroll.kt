package h.Hchat.hooks.items.hchatextra

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Keeps the user's tail-follow intent across list content growth. */
internal class MessageDetailsFollowLatestState(initiallyFollowingLatest: Boolean) {
    @Volatile private var followingLatest = initiallyFollowingLatest
    @Volatile private var userMovedIntoHistory = false

    fun onScrolled(verticalDelta: Int, atLatestEdge: Boolean) {
        if (atLatestEdge) {
            followingLatest = true
            userMovedIntoHistory = false
        } else if (verticalDelta < 0) {
            // A negative RecyclerView delta is movement back into message history.
            followingLatest = false
            userMovedIntoHistory = true
        }
    }

    fun onPositionRequested(targetsLatestRow: Boolean) {
        if (!targetsLatestRow) {
            followingLatest = false
        } else if (!userMovedIntoHistory) {
            // Initial chat positioning may happen without a physical scroll callback.
            followingLatest = true
        }
    }

    fun onUserTouch(fingerDeltaY: Float, touchSlop: Int, canScrollTowardHistory: Boolean, atLatestEdge: Boolean) {
        if (atLatestEdge) {
            followingLatest = true
            userMovedIntoHistory = false
        } else if (fingerDeltaY > touchSlop && canScrollTowardHistory) {
            // A downward finger drag moves the chat content toward older messages.
            followingLatest = false
            userMovedIntoHistory = true
        }
    }

    fun isFollowingLatest(): Boolean = followingLatest
}

/** One measured, bounded correction for the newest row after its height changes. */
internal object MessageDetailsFollowLatestScroll {
    fun hasNewerAttachedMessage(
        container: ViewGroup,
        adapterPosition: Int,
        childAdapterPosition: (View) -> Int?,
        isMessageRow: (View) -> Boolean
    ): Boolean {
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            val childPosition = childAdapterPosition(child) ?: continue
            if (childPosition > adapterPosition && isMessageRow(child)) return true
        }
        return false
    }

    fun correctTailOverflow(
        container: View,
        row: View,
        label: View,
        followingLatest: Boolean,
        isLatestRow: Boolean
    ): Int {
        if (!followingLatest || !isLatestRow || label.visibility != View.VISIBLE ||
            label.width <= 0 || label.height <= 0 || !contains(row, label) || !contains(container, row)
        ) return 0

        val labelBounds = Rect(0, 0, label.width, label.height)
        if (container !is ViewGroup) return 0
        runCatching { container.offsetDescendantRectToMyCoords(label, labelBounds) }.getOrElse { return 0 }

        val scrollContainer = container
        val clippedBottomPadding = if (scrollContainer.clipToPadding) scrollContainer.paddingBottom else 0
        var visibleBottom = (container.height - clippedBottomPadding).coerceAtLeast(container.paddingTop)
        val visibleContainer = Rect()
        if (container.getGlobalVisibleRect(visibleContainer)) {
            // Convert the clipped screen-space edge back to the container's local coordinates.
            val location = IntArray(2)
            container.getLocationOnScreen(location)
            visibleBottom = minOf(visibleBottom, visibleContainer.bottom - location[1])
        }
        // ScrollView descendants are reported in content coordinates; its visible viewport
        // therefore ends at scrollY + height. RecyclerView children already move with scrolling.
        visibleBottom += container.scrollY
        val overflow = (labelBounds.bottom - visibleBottom).coerceAtLeast(0)
        if (overflow == 0 || !container.canScrollVertically(1)) return 0

        if (container.javaClass.name.contains("ListView")) {
            val scrollListBy = container.javaClass.methods.firstOrNull { method ->
                method.name == "scrollListBy" && method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            } ?: return 0
            return runCatching {
                scrollListBy.invoke(container, overflow)
                overflow
            }.getOrDefault(0)
        }

        container.scrollBy(0, overflow)
        return overflow
    }

    private fun contains(ancestor: View, descendant: View): Boolean {
        var current: View? = descendant
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }
}

/** Weak, lifecycle-aware tail state for the active chat's actual scroll viewport. */
internal object MessageDetailsFollowLatestRegistry {
    private val entries = WeakHashMap<View, Entry>()

    fun observe(container: View) {
        synchronized(entries) {
            if (entries.containsKey(container)) return
            val entry = Entry(container, MessageDetailsFollowLatestState(!container.canScrollVertically(1)))
            entries[container] = entry
            entry.attach()
        }
    }

    fun isFollowingLatest(container: View): Boolean = synchronized(entries) {
        entries[container]?.state?.isFollowingLatest() == true
    }

    fun onRecyclerScrolled(container: View, verticalDelta: Int) {
        if (verticalDelta == 0) return
        val entry = synchronized(entries) { entries[container] } ?: return
        entry.recordScroll(verticalDelta)
    }

    fun onRecyclerTouch(container: View, event: MotionEvent) {
        val entry = synchronized(entries) { entries[container] } ?: return
        entry.onTouch(event)
    }

    fun onRecyclerPositionRequested(container: View, adapterPosition: Int) {
        val entry = synchronized(entries) { entries[container] } ?: return
        val count = entry.adapterCount(container) ?: return
        entry.state.onPositionRequested(adapterPosition >= 0 && adapterPosition == count - 1)
    }

    fun rememberTailRow(container: View, row: View, label: TextView, adapterPosition: Int) {
        val entry = synchronized(entries) { entries[container] } ?: return
        entry.rememberTailRow(row, label, adapterPosition)
    }

    fun clear() {
        val old = synchronized(entries) { entries.values.toList().also { entries.clear() } }
        old.forEach(Entry::dispose)
    }

    private class Entry(container: View, val state: MessageDetailsFollowLatestState) :
        View.OnLayoutChangeListener,
        View.OnAttachStateChangeListener,
        ViewTreeObserver.OnScrollChangedListener {
        private val container = WeakReference(container)
        private var observer = WeakReference<ViewTreeObserver>(null)
        private var lastScrollY = container.scrollY
        private var row = WeakReference<View>(null)
        private var label = WeakReference<TextView>(null)
        private var adapterPosition = -1
        private var targetGeneration = 0L
        private var pendingCorrection: Runnable? = null
        private var active = true
        private var gesturePointerId = MotionEvent.INVALID_POINTER_ID
        private var gestureStartY = Float.NaN

        fun attach() {
            val view = container.get() ?: return
            view.addOnLayoutChangeListener(this)
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) attachScrollObserver(view)
        }

        fun rememberTailRow(row: View, label: TextView, adapterPosition: Int) {
            this.row = WeakReference(row)
            this.label = WeakReference(label)
            this.adapterPosition = adapterPosition
            targetGeneration++
        }

        fun recordScroll(verticalDelta: Int) {
            val view = container.get() ?: return
            state.onScrolled(verticalDelta, !view.canScrollVertically(1))
        }

        fun onTouch(event: MotionEvent) {
            val view = container.get() ?: return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gesturePointerId = event.getPointerId(0)
                    gestureStartY = event.getY(0)
                    state.onUserTouch(
                        fingerDeltaY = 0f,
                        touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop,
                        canScrollTowardHistory = view.canScrollVertically(-1),
                        atLatestEdge = !view.canScrollVertically(1)
                    )
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val liftedIndex = event.actionIndex
                    if (event.getPointerId(liftedIndex) == gesturePointerId) {
                        val replacement = (0 until event.pointerCount).firstOrNull { it != liftedIndex }
                        if (replacement == null) {
                            gesturePointerId = MotionEvent.INVALID_POINTER_ID
                            gestureStartY = Float.NaN
                        } else {
                            gesturePointerId = event.getPointerId(replacement)
                            gestureStartY = event.getY(replacement)
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(gesturePointerId)
                    if (index >= 0 && !gestureStartY.isNaN()) {
                        state.onUserTouch(
                            fingerDeltaY = event.getY(index) - gestureStartY,
                            touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop,
                            canScrollTowardHistory = view.canScrollVertically(-1),
                            atLatestEdge = !view.canScrollVertically(1)
                        )
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gesturePointerId = MotionEvent.INVALID_POINTER_ID
                    gestureStartY = Float.NaN
                    if (!view.canScrollVertically(1)) state.onUserTouch(0f, 0, false, atLatestEdge = true)
                }
            }
        }

        override fun onLayoutChange(
            view: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                scheduleCorrection(view)
            }
        }

        override fun onViewAttachedToWindow(view: View) = attachScrollObserver(view)

        override fun onViewDetachedFromWindow(view: View) = removeScrollObserver(view)

        override fun onScrollChanged() {
            val view = container.get() ?: return
            val nextY = view.scrollY
            val delta = nextY - lastScrollY
            lastScrollY = nextY
            if (delta != 0) recordScroll(delta)
        }

        private fun attachScrollObserver(view: View) {
            removeScrollObserver(view)
            lastScrollY = view.scrollY
            if (view.javaClass.name.contains("RecyclerView")) return
            val current = view.viewTreeObserver
            if (!current.isAlive) return
            current.addOnScrollChangedListener(this)
            observer = WeakReference(current)
        }

        private fun removeScrollObserver(view: View) {
            val saved = observer.get()
            if (saved?.isAlive == true) saved.removeOnScrollChangedListener(this)
            val current = view.viewTreeObserver
            if (current !== saved && current.isAlive) current.removeOnScrollChangedListener(this)
            observer.clear()
        }

        private fun scheduleCorrection(view: View) {
            if (!active || pendingCorrection != null || !state.isFollowingLatest()) return
            val generationRow = row
            val generationLabel = label
            val generationPosition = adapterPosition
            val generation = targetGeneration
            val callback = Runnable {
                pendingCorrection = null
                if (!active || container.get() !== view || generation != targetGeneration ||
                    !state.isFollowingLatest()
                ) return@Runnable
                val currentRow = generationRow.get() ?: return@Runnable
                val currentLabel = generationLabel.get() ?: return@Runnable
                if (generationPosition < 0 || adapterPosition(view, currentRow) != generationPosition) return@Runnable
                MessageDetailsFollowLatestScroll.correctTailOverflow(
                    view, currentRow, currentLabel, state.isFollowingLatest(), isLatestRow = true
                )
            }
            pendingCorrection = callback
            if (!view.post(callback)) pendingCorrection = null
        }

        fun dispose() {
            if (!active) return
            active = false
            val view = container.get() ?: return
            pendingCorrection?.let(view::removeCallbacks)
            pendingCorrection = null
            removeScrollObserver(view)
            view.removeOnLayoutChangeListener(this)
            view.removeOnAttachStateChangeListener(this)
            row.clear()
            label.clear()
            adapterPosition = -1
            gesturePointerId = MotionEvent.INVALID_POINTER_ID
            gestureStartY = Float.NaN
        }

        fun adapterCount(view: View): Int? = runCatching {
            val adapter = view.javaClass.methods.firstOrNull {
                it.name == "getAdapter" && it.parameterTypes.isEmpty()
            }?.invoke(view) ?: return null
            val countMethod = adapter.javaClass.methods.firstOrNull {
                (it.name == "getItemCount" || it.name == "getCount") && it.parameterTypes.isEmpty()
            } ?: return null
            (countMethod.invoke(adapter) as? Number)?.toInt()
        }.getOrNull()

        private fun adapterPosition(container: View, row: View): Int? {
            var current: View? = row
            while (current != null && current !== container) {
                val position = invokeAdapterPosition(container, "getChildAdapterPosition", current)
                    ?: invokeAdapterPosition(container, "getPositionForView", current)
                if (position != null && position >= 0) return position
                current = current.parent as? View
            }
            return null
        }

        private fun invokeAdapterPosition(container: View, name: String, child: View): Int? = runCatching {
            val method = container.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].isAssignableFrom(child.javaClass)
            } ?: return null
            (method.invoke(container, child) as? Number)?.toInt()
        }.getOrNull()
    }
}
