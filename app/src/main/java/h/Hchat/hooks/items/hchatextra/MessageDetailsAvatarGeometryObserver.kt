package h.Hchat.hooks.items.hchatextra

import android.view.View
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Repositions an avatar label only after one of its actual layout anchors changes bounds.
 * Listeners are additive View listeners and never replace host listeners or scan the view tree.
 */
internal class MessageDetailsAvatarGeometryObserver {
    private val entries = WeakHashMap<View, Entry>()

    fun observe(
        root: View,
        label: TextView,
        anchors: Collection<View>,
        action: (View, TextView) -> Unit
    ) {
        val existing = entries[root]
        if (existing != null && existing.label.get() === label) {
            existing.action = action
            existing.setAnchors(anchors)
            return
        }
        cancel(root)
        Entry(root, label, anchors, action).also { entry ->
            entries[root] = entry
            root.addOnAttachStateChangeListener(entry)
            if (root.isAttachedToWindow) entry.attach()
        }
    }

    fun cancel(root: View) {
        entries.remove(root)?.dispose()
    }

    fun clear() {
        val old = entries.values.toList()
        entries.clear()
        old.forEach(Entry::dispose)
    }

    private inner class Entry(
        root: View,
        label: TextView,
        anchors: Collection<View>,
        var action: (View, TextView) -> Unit
    ) : View.OnAttachStateChangeListener, View.OnLayoutChangeListener {
        val root = WeakReference(root)
        val label = WeakReference(label)
        private var active = true
        private var pending: Runnable? = null
        private var generation = 0L
        private var observed = emptyList<WeakReference<View>>()

        init {
            setAnchors(anchors)
        }

        fun setAnchors(anchors: Collection<View>) {
            val next = anchors.asSequence()
                .filter { it !== label.get() }
                .distinct()
                .map(::WeakReference)
                .toList()
            if (sameAnchors(observed, next)) return
            detachAnchors()
            observed = next
            if (root.get()?.isAttachedToWindow == true) attachAnchors()
        }

        override fun onViewAttachedToWindow(view: View) {
            if (active && root.get() === view && entries[view] === this) attach()
        }

        override fun onViewDetachedFromWindow(view: View) {
            if (root.get() === view) {
                detachAnchors()
                cancelPending()
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
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) return
            schedule()
        }

        fun attach() {
            val current = root.get() ?: run { dispose(); return }
            if (!active || entries[current] !== this || !current.isAttachedToWindow) return
            attachAnchors()
        }

        private fun attachAnchors() {
            observed.forEach { reference -> reference.get()?.addOnLayoutChangeListener(this) }
        }

        private fun detachAnchors() {
            observed.forEach { reference -> reference.get()?.removeOnLayoutChangeListener(this) }
        }

        private fun schedule() {
            val current = root.get() ?: run { dispose(); return }
            if (!active || entries[current] !== this || !current.isAttachedToWindow || pending != null) return
            val scheduledGeneration = generation
            val callback = Runnable {
                if (generation != scheduledGeneration) return@Runnable
                pending = null
                val currentRoot = root.get() ?: run { dispose(); return@Runnable }
                val currentLabel = label.get()
                if (!active || entries[currentRoot] !== this || !currentRoot.isAttachedToWindow ||
                    currentLabel == null || !isDescendant(currentLabel, currentRoot)
                ) {
                    if (currentLabel == null || !isDescendant(currentLabel, currentRoot)) cancel(currentRoot)
                    return@Runnable
                }
                action(currentRoot, currentLabel)
            }
            pending = callback
            if (!current.post(callback)) pending = null
        }

        private fun cancelPending() {
            generation++
            pending?.let { root.get()?.removeCallbacks(it) }
            pending = null
        }

        fun dispose() {
            if (!active) return
            active = false
            cancelPending()
            detachAnchors()
            root.get()?.removeOnAttachStateChangeListener(this)
            action = { _, _ -> }
            observed = emptyList()
        }
    }

    private fun sameAnchors(left: List<WeakReference<View>>, right: List<WeakReference<View>>): Boolean =
        left.size == right.size && left.indices.all { left[it].get() === right[it].get() }

    private fun isDescendant(view: View, root: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === root) return true
            current = current.parent as? View
        }
        return false
    }
}
