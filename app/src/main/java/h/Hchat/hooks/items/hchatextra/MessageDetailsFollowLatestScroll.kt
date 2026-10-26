package h.Hchat.hooks.items.hchatextra

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Keeps the user's tail-follow intent across native list growth and layout changes. */
internal class MessageDetailsFollowLatestState(initiallyFollowingLatest: Boolean) {
    @Volatile private var followingLatest = initiallyFollowingLatest
    @Volatile private var userMovedIntoHistory = false
    @Volatile private var userRequestedLatest = false

    fun onScrolled(verticalDelta: Int, atLatestEdge: Boolean) {
        if (atLatestEdge) {
            if (!userMovedIntoHistory || userRequestedLatest) {
                followingLatest = true
                userMovedIntoHistory = false
                userRequestedLatest = false
            } else {
                followingLatest = false
            }
        } else if (verticalDelta < 0) {
            followingLatest = false
            userMovedIntoHistory = true
            userRequestedLatest = false
        }
    }

    fun onPositionRequested(targetsLatestRow: Boolean) {
        if (!targetsLatestRow) {
            followingLatest = false
        } else if (!userMovedIntoHistory) {
            followingLatest = true
        }
    }

    fun onUserTouch(
        fingerDeltaY: Float,
        touchSlop: Int,
        canScrollTowardHistory: Boolean,
        atLatestEdge: Boolean
    ) {
        if (atLatestEdge) {
            followingLatest = true
            userMovedIntoHistory = false
            userRequestedLatest = false
        } else if (fingerDeltaY > touchSlop && canScrollTowardHistory) {
            followingLatest = false
            userMovedIntoHistory = true
            userRequestedLatest = false
        } else if (fingerDeltaY < -touchSlop && canScrollTowardHistory) {
            userRequestedLatest = true
        }
    }

    fun onAbsListVisiblePositions(
        previousFirst: Int,
        previousLast: Int,
        firstVisible: Int,
        lastVisible: Int,
        atLatestEdge: Boolean
    ) {
        val movedTowardHistory = firstVisible < previousFirst || lastVisible < previousLast
        val movedTowardLatest = firstVisible > previousFirst || lastVisible > previousLast
        onScrolled(
            verticalDelta = when {
                movedTowardHistory -> -1
                movedTowardLatest -> 1
                else -> 0
            },
            atLatestEdge = atLatestEdge
        )
    }

    fun isFollowingLatest(): Boolean = followingLatest
}

internal data class MessageDetailsTailConfirmation(
    val position: Int,
    val identity: MessageDetailsIdentity,
    val bindIsCurrent: Boolean,
    val rowIsAttached: Boolean,
    val featureEnabled: Boolean,
    val latestMessageConfirmed: Boolean
)

/** One measured, bounded correction for a confirmed latest row after viewport or row growth. */
internal object MessageDetailsFollowLatestScroll {
    fun overflowDistance(container: View, row: View, label: View): Int {
        val kind = MessageDetailsScrollSupport.containerKind(container)
        if (kind == MessageDetailsScrollSupport.ContainerKind.UNKNOWN ||
            label.visibility != View.VISIBLE || label.width <= 0 || label.height <= 0 ||
            !MessageDetailsScrollSupport.isWithin(label, row) ||
            !MessageDetailsScrollSupport.isWithin(row, container)
        ) return 0
        val parent = container as? android.view.ViewGroup ?: return 0
        val labelBounds = Rect(0, 0, label.width, label.height)
        runCatching { parent.offsetDescendantRectToMyCoords(label, labelBounds) }.getOrElse { return 0 }

        val clippedBottomPadding = if (parent.clipToPadding) parent.paddingBottom else 0
        var visibleBottom = (container.height - clippedBottomPadding).coerceAtLeast(container.paddingTop)
        val visibleContainer = Rect()
        if (container.getGlobalVisibleRect(visibleContainer)) {
            val location = IntArray(2)
            container.getLocationOnScreen(location)
            visibleBottom = minOf(visibleBottom, visibleContainer.bottom - location[1])
        }
        // ScrollView descendants keep content coordinates. RecyclerView and AbsListView children
        // are already expressed in viewport coordinates and must not be adjusted by scrollY.
        if (container is ScrollView) visibleBottom += container.scrollY
        return (labelBounds.bottom - visibleBottom).coerceAtLeast(0)
    }

    fun correctTailOverflow(
        container: View,
        row: View,
        label: View,
        followingLatest: Boolean,
        isLatestRow: Boolean
    ): Int {
        if (!followingLatest || !isLatestRow) return 0
        val overflow = overflowDistance(container, row, label)
        return if (MessageDetailsScrollSupport.correctBy(container, overflow)) overflow else 0
    }
}

/** Weak, lifecycle-aware tail state for the active chat's actual scroll viewport. */
internal object MessageDetailsFollowLatestRegistry {
    private val entries = WeakHashMap<View, Entry>()

    fun observe(container: View) {
        synchronized(entries) {
            if (entries.containsKey(container)) return
            entries[container] = Entry(container, MessageDetailsFollowLatestState(!container.canScrollVertically(1))).also {
                it.attach()
            }
        }
    }

    fun isFollowingLatest(container: View): Boolean = synchronized(entries) {
        entries[container]?.state?.isFollowingLatest() == true
    }

    fun onRecyclerScrolled(container: View, verticalDelta: Int) {
        val entry = synchronized(entries) { entries[container] } ?: return
        entry.recordRecyclerScroll(verticalDelta)
    }

    fun onAbsListLaidOut(container: View) {
        val list = container as? AbsListView ?: return
        val entry = synchronized(entries) { entries[list] } ?: return
        entry.onAbsListLaidOut(list)
    }

    fun onRecyclerTouch(container: View, event: MotionEvent) {
        val entry = synchronized(entries) { entries[container] } ?: return
        entry.onTouch(event)
    }

    fun onPositionRequested(container: View, adapterPosition: Int) {
        val entry = synchronized(entries) { entries[container] } ?: return
        val count = MessageDetailsScrollSupport.adapterItemCount(container) ?: return
        entry.state.onPositionRequested(adapterPosition >= 0 && adapterPosition == count - 1)
    }

    fun rememberTailRow(
        container: View,
        row: View,
        label: TextView,
        confirmation: MessageDetailsTailConfirmation,
        validator: () -> MessageDetailsTailConfirmation?
    ) {
        val entry = synchronized(entries) { entries[container] } ?: return
        entry.rememberTailRow(row, label, confirmation, validator)
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
        private var lastAbsFirst = (container as? AbsListView)?.firstVisiblePosition ?: -1
        private var lastAbsLast = (container as? AbsListView)?.lastVisiblePosition ?: -1
        private var row = WeakReference<View>(null)
        private var label = WeakReference<TextView>(null)
        private var expectedPosition = -1
        private var expectedIdentity = WeakReference<MessageDetailsIdentity>(null)
        private var validator: (() -> MessageDetailsTailConfirmation?)? = null
        private var targetGeneration = 0L
        private var pendingCorrection: Runnable? = null
        private var active = true
        private var attached = false
        private var gesturePointerId = MotionEvent.INVALID_POINTER_ID
        private var gestureStartY = Float.NaN

        fun attach() {
            val view = container.get() ?: return
            view.addOnLayoutChangeListener(this)
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) onViewAttachedToWindow(view)
        }

        fun rememberTailRow(
            row: View,
            label: TextView,
            confirmation: MessageDetailsTailConfirmation,
            validator: () -> MessageDetailsTailConfirmation?
        ) {
            this.row = WeakReference(row)
            this.label = WeakReference(label)
            expectedPosition = confirmation.position
            expectedIdentity = WeakReference(confirmation.identity)
            this.validator = validator
            targetGeneration++
            val view = container.get() ?: return
            pendingCorrection?.let(view::removeCallbacks)
            pendingCorrection = null
            scheduleCorrection(view)
        }

        fun recordRecyclerScroll(verticalDelta: Int) {
            val view = container.get() ?: return
            state.onScrolled(verticalDelta, !view.canScrollVertically(1))
        }

        fun recordAbsListLayout(view: AbsListView) {
            val first = view.firstVisiblePosition
            val last = view.lastVisiblePosition
            val previousFirst = lastAbsFirst
            val previousLast = lastAbsLast
            lastAbsFirst = first
            lastAbsLast = last
            if (previousFirst < 0 || previousLast < previousFirst) return
            state.onAbsListVisiblePositions(
                previousFirst, previousLast, first, last, atLatestEdge = !view.canScrollVertically(1)
            )
        }

        fun onAbsListLaidOut(view: AbsListView) {
            recordAbsListLayout(view)
            scheduleCorrection(view)
        }

        fun onTouch(event: MotionEvent) {
            val view = container.get() ?: return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gesturePointerId = event.getPointerId(0)
                    gestureStartY = event.getY(0)
                    recordTouchIntent(view, 0f)
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
                        recordTouchIntent(view, event.getY(index) - gestureStartY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gesturePointerId = MotionEvent.INVALID_POINTER_ID
                    gestureStartY = Float.NaN
                    if (!view.canScrollVertically(1)) {
                        state.onUserTouch(0f, 0, false, atLatestEdge = true)
                    }
                }
            }
        }

        private fun recordTouchIntent(view: View, fingerDeltaY: Float) {
            state.onUserTouch(
                fingerDeltaY = fingerDeltaY,
                touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop,
                canScrollTowardHistory = MessageDetailsScrollSupport.canScrollTowardHistory(view),
                atLatestEdge = !view.canScrollVertically(1)
            )
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
            if (view is AbsListView) recordAbsListLayout(view)
            if (right != oldRight || bottom != oldBottom || left != oldLeft || top != oldTop) {
                scheduleCorrection(view)
            }
        }

        override fun onViewAttachedToWindow(view: View) {
            attached = true
            if (view is AbsListView) {
                lastAbsFirst = view.firstVisiblePosition
                lastAbsLast = view.lastVisiblePosition
            }
            attachScrollObserver(view)
            scheduleCorrection(view)
        }

        override fun onViewDetachedFromWindow(view: View) {
            attached = false
            targetGeneration++
            pendingCorrection?.let(view::removeCallbacks)
            pendingCorrection = null
            removeScrollObserver(view)
        }

        override fun onScrollChanged() {
            val view = container.get() ?: return
            if (view is AbsListView) {
                recordAbsListLayout(view)
                return
            }
            val nextY = view.scrollY
            val delta = nextY - lastScrollY
            lastScrollY = nextY
            if (delta != 0) recordRecyclerScroll(delta)
        }

        private fun attachScrollObserver(view: View) {
            removeScrollObserver(view)
            lastScrollY = view.scrollY
            if (MessageDetailsScrollSupport.containerKind(view) != MessageDetailsScrollSupport.ContainerKind.SCROLL) return
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
            if (!active || !attached || pendingCorrection != null || !state.isFollowingLatest()) return
            val rowReference = row
            val labelReference = label
            val expectedPosition = this.expectedPosition
            val expectedIdentity = this.expectedIdentity.get() ?: return
            val validate = validator ?: return
            val generation = targetGeneration
            val callback = Runnable {
                pendingCorrection = null
                if (!active || !attached || container.get() !== view || generation != targetGeneration ||
                    !state.isFollowingLatest() || !view.isAttachedToWindow
                ) return@Runnable
                val currentRow = rowReference.get() ?: return@Runnable
                val currentLabel = labelReference.get() ?: return@Runnable
                if (MessageDetailsFollowLatestScroll.overflowDistance(view, currentRow, currentLabel) <= 0) {
                    return@Runnable
                }
                val latest = validate() ?: return@Runnable
                val valid = MessageDetailsScrollSupport.delayedRowStillMatches(
                    expectedPosition = expectedPosition,
                    actualPosition = latest.position,
                    expectedIdentity = expectedIdentity,
                    currentIdentity = latest.identity,
                    bindIsCurrent = latest.bindIsCurrent,
                    rowIsAttached = latest.rowIsAttached,
                    featureEnabled = latest.featureEnabled,
                    latestMessageConfirmed = latest.latestMessageConfirmed,
                    followingLatest = state.isFollowingLatest()
                )
                if (!valid || !MessageDetailsScrollSupport.isWithin(currentRow, view)) return@Runnable
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
            targetGeneration++
            val view = container.get() ?: return
            pendingCorrection?.let(view::removeCallbacks)
            pendingCorrection = null
            removeScrollObserver(view)
            view.removeOnLayoutChangeListener(this)
            view.removeOnAttachStateChangeListener(this)
            row.clear()
            label.clear()
            expectedPosition = -1
            expectedIdentity.clear()
            validator = null
            gesturePointerId = MotionEvent.INVALID_POINTER_ID
            gestureStartY = Float.NaN
        }
    }
}
