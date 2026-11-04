package h.Hchat.hooks.items.hchatextra

import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.ceil

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
        val clippedBottomPadding = if (parent.clipToPadding) parent.paddingBottom else 0
        val visibleContainer = Rect()
        if (!container.getGlobalVisibleRect(visibleContainer)) return 0
        val containerBottom = drawnBottomInWindow(
            container, (container.height - clippedBottomPadding).coerceAtLeast(container.paddingTop)
        )
        val labelBottom = drawnBottomInWindow(label, label.height)
        return (labelBottom - minOf(containerBottom, visibleContainer.bottom)).coerceAtLeast(0)
    }

    /** Matches getGlobalVisibleRect's window coordinates, including host and parent transforms. */
    private fun drawnBottomInWindow(view: View, localBottom: Int): Int {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val origin = floatArrayOf(0f, 0f)
        val bounds = RectF(0f, 0f, view.width.toFloat(), localBottom.toFloat())
        var ancestor: View? = view
        while (ancestor != null) {
            val transform = ancestor.matrix
            if (!transform.isIdentity) {
                transform.mapPoints(origin)
                transform.mapRect(bounds)
            }
            ancestor = ancestor.parent as? View
        }
        return location[1] + ceil(bounds.bottom - origin[1]).toInt()
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
    private const val MAX_SETTLING_PREDRAWS = 90
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

    fun onRecyclerScrollStateChanged(container: View) {
        val entry = synchronized(entries) { entries[container] } ?: return
        entry.onScrollStateChanged()
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
        entry.onPositionRequested(adapterPosition >= 0 && adapterPosition == count - 1)
    }

    fun rememberTailRow(
        container: View,
        row: View,
        label: TextView,
        confirmation: MessageDetailsTailConfirmation,
        alreadyInPreDraw: Boolean = false,
        validator: () -> MessageDetailsTailConfirmation?
    ) {
        val entry = synchronized(entries) { entries[container] } ?: return
        entry.rememberTailRow(row, label, confirmation, validator, alreadyInPreDraw)
    }

    fun forgetTailRow(row: View) {
        val current = synchronized(entries) { entries.values.toList() }
        current.forEach { it.forgetTailRow(row) }
    }

    fun clear() {
        val old = synchronized(entries) { entries.values.toList().also { entries.clear() } }
        old.forEach(Entry::dispose)
    }

    private class Entry(container: View, val state: MessageDetailsFollowLatestState) :
        View.OnLayoutChangeListener,
        View.OnAttachStateChangeListener,
        ViewTreeObserver.OnScrollChangedListener,
        ViewTreeObserver.OnGlobalLayoutListener {
        private val container = WeakReference(container)
        private var observer = WeakReference<ViewTreeObserver>(null)
        private var viewportObserver = WeakReference<ViewTreeObserver>(null)
        private val lastVisibleViewport = Rect()
        private var hasVisibleViewport = false
        private var lastBottomPadding = 0
        private var lastScrollY = container.scrollY
        private var lastAbsFirst = (container as? AbsListView)?.firstVisiblePosition ?: -1
        private var lastAbsLast = (container as? AbsListView)?.lastVisiblePosition ?: -1
        private var row = WeakReference<View>(null)
        private var label = WeakReference<TextView>(null)
        private var expectedPosition = -1
        private var expectedIdentity = WeakReference<MessageDetailsIdentity>(null)
        private var validator: (() -> MessageDetailsTailConfirmation?)? = null
        private var targetGeneration = 0L
        private val preDraw = MessageDetailsPreDrawQueue()
        private var correctionPending = false
        private var deferredFullyHiddenTail = false
        private var settlingPreDraws = 0
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
            validator: () -> MessageDetailsTailConfirmation?,
            alreadyInPreDraw: Boolean
        ) {
            val previousIdentity = expectedIdentity.get()
            val sameTarget = this.row.get() === row && this.label.get() === label &&
                expectedPosition == confirmation.position && previousIdentity?.sameMessage(confirmation.identity) == true
            if (!sameTarget) {
                clearTarget()
                this.row = WeakReference(row)
                this.label = WeakReference(label)
                row.addOnLayoutChangeListener(this)
                if (label !== row) label.addOnLayoutChangeListener(this)
                row.addOnAttachStateChangeListener(this)
            }
            expectedPosition = confirmation.position
            expectedIdentity = WeakReference(confirmation.identity)
            this.validator = validator
            val view = container.get() ?: return
            // The initial candidate is resolved after layout in the row's pre-draw callback.
            // Correct there rather than posting a second task that can reveal a clipped frame.
            if (alreadyInPreDraw) {
                cancelCorrection()
                correctIfCurrent(view, targetGeneration)
            } else scheduleCorrection(view)
        }

        fun forgetTailRow(candidate: View) {
            if (row.get() === candidate) clearTarget()
        }

        fun recordRecyclerScroll(verticalDelta: Int) {
            val view = container.get() ?: return
            state.onScrolled(verticalDelta, !view.canScrollVertically(1))
            if (state.isFollowingLatest()) scheduleCorrection(view)
        }

        fun onPositionRequested(targetsLatestRow: Boolean) {
            state.onPositionRequested(targetsLatestRow)
            if (state.isFollowingLatest()) container.get()?.let(::scheduleCorrection)
        }

        fun onScrollStateChanged() {
            if (state.isFollowingLatest()) container.get()?.let(::scheduleCorrection)
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
            if (state.isFollowingLatest()) scheduleCorrection(view)
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
            val viewport = container.get() ?: return
            if (view === viewport && view is AbsListView) recordAbsListLayout(view)
            if (right != oldRight || bottom != oldBottom || left != oldLeft || top != oldTop) {
                scheduleCorrection(viewport)
            }
        }

        override fun onViewAttachedToWindow(view: View) {
            if (view !== container.get()) {
                if (view === row.get()) container.get()?.let(::scheduleCorrection)
                return
            }
            attached = true
            if (view is AbsListView) {
                lastAbsFirst = view.firstVisiblePosition
                lastAbsLast = view.lastVisiblePosition
            }
            attachScrollObserver(view)
            attachViewportObserver(view)
            scheduleCorrection(view)
        }

        override fun onViewDetachedFromWindow(view: View) {
            if (view !== container.get()) {
                if (view === row.get()) cancelCorrection()
                return
            }
            attached = false
            targetGeneration++
            cancelCorrection()
            removeScrollObserver(view)
            removeViewportObserver(view)
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

        override fun onGlobalLayout() {
            val view = container.get() ?: return
            val bounds = Rect()
            val visible = view.getGlobalVisibleRect(bounds)
            val bottomPadding = if ((view as? ViewGroup)?.clipToPadding == true) view.paddingBottom else 0
            val changed = visible != hasVisibleViewport || bottomPadding != lastBottomPadding ||
                (visible && bounds != lastVisibleViewport)
            hasVisibleViewport = visible
            lastBottomPadding = bottomPadding
            if (visible) lastVisibleViewport.set(bounds) else lastVisibleViewport.setEmpty()
            if (changed) scheduleCorrection(view)
        }

        private fun attachViewportObserver(view: View) {
            removeViewportObserver(view)
            val current = view.viewTreeObserver
            if (!current.isAlive) return
            hasVisibleViewport = view.getGlobalVisibleRect(lastVisibleViewport)
            lastBottomPadding = if ((view as? ViewGroup)?.clipToPadding == true) view.paddingBottom else 0
            current.addOnGlobalLayoutListener(this)
            viewportObserver = WeakReference(current)
        }

        private fun removeViewportObserver(view: View) {
            val saved = viewportObserver.get()
            if (saved?.isAlive == true) saved.removeOnGlobalLayoutListener(this)
            val current = view.viewTreeObserver
            if (current !== saved && current.isAlive) current.removeOnGlobalLayoutListener(this)
            viewportObserver.clear()
            hasVisibleViewport = false
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
            if (!active || !attached || correctionPending || !state.isFollowingLatest() ||
                row.get() == null || label.get() == null || validator == null) return
            val generation = targetGeneration
            correctionPending = true
            if (!preDraw.schedule(view, view) {
                correctionPending = false
                correctIfCurrent(view, generation)
            }) {
                correctionPending = false
            }
        }

        private fun correctIfCurrent(view: View, generation: Long) {
            if (!active || !attached || container.get() !== view || generation != targetGeneration ||
                !state.isFollowingLatest() || !view.isAttachedToWindow) return
            val currentRow = row.get() ?: return
            val currentLabel = label.get() ?: return
            val overflow = MessageDetailsFollowLatestScroll.overflowDistance(view, currentRow, currentLabel)
            if (overflow <= 0) {
                deferredFullyHiddenTail = false
                settlingPreDraws = 0
                return
            }
            // A new row can be entirely below the viewport while WeChat is already moving it
            // into place. Let that native movement proceed, then correct just the entering label.
            // If there is no native movement, the one additional frame prevents a stranded tail.
            if (overflow > currentLabel.height) {
                if (!deferredFullyHiddenTail) {
                    deferredFullyHiddenTail = true
                    scheduleCorrection(view)
                    view.postInvalidateOnAnimation()
                    return
                }
                if (MessageDetailsScrollSupport.isRecyclerSettling(view)) {
                    // Watch only this confirmed tail while the host animation is running.
                    // The bound limits work if a broken host remains in SETTLING forever.
                    if (settlingPreDraws++ < MAX_SETTLING_PREDRAWS) {
                        scheduleCorrection(view)
                        view.postInvalidateOnAnimation()
                    }
                    return
                }
            }
            val expected = expectedIdentity.get() ?: return
            val latest = validator?.invoke() ?: return
            val valid = MessageDetailsScrollSupport.delayedRowStillMatches(
                expectedPosition = expectedPosition,
                actualPosition = latest.position,
                expectedIdentity = expected,
                currentIdentity = latest.identity,
                bindIsCurrent = latest.bindIsCurrent,
                rowIsAttached = latest.rowIsAttached,
                featureEnabled = latest.featureEnabled,
                latestMessageConfirmed = latest.latestMessageConfirmed,
                followingLatest = state.isFollowingLatest()
            )
            if (!valid || !MessageDetailsScrollSupport.isWithin(currentRow, view)) return
            MessageDetailsFollowLatestScroll.correctTailOverflow(
                view, currentRow, currentLabel, state.isFollowingLatest(), isLatestRow = true
            )
        }

        private fun cancelCorrection() {
            container.get()?.let(preDraw::cancel)
            correctionPending = false
        }

        private fun clearTarget() {
            targetGeneration++
            cancelCorrection()
            val previousRow = row.get()
            val previousLabel = label.get()
            previousRow?.removeOnLayoutChangeListener(this)
            if (previousLabel !== previousRow) previousLabel?.removeOnLayoutChangeListener(this)
            previousRow?.removeOnAttachStateChangeListener(this)
            row.clear()
            label.clear()
            expectedPosition = -1
            expectedIdentity.clear()
            validator = null
            deferredFullyHiddenTail = false
            settlingPreDraws = 0
        }

        fun dispose() {
            if (!active) return
            active = false
            clearTarget()
            val view = container.get() ?: return
            removeScrollObserver(view)
            removeViewportObserver(view)
            view.removeOnLayoutChangeListener(this)
            view.removeOnAttachStateChangeListener(this)
            gesturePointerId = MotionEvent.INVALID_POINTER_ID
            gestureStartY = Float.NaN
        }
    }
}
