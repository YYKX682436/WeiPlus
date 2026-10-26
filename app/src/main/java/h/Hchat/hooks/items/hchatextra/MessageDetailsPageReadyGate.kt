package h.Hchat.hooks.items.hchatextra

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference

/**
 * One page-scoped, event-driven backfill gate. A pending request only wakes on attach,
 * window-focus, or real global-layout events; callers cancel it on page/config generation changes.
 */
internal class MessageDetailsPageReadyGate {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var entry: Entry? = null

    fun schedule(decor: View, isCurrent: () -> Boolean, tryBackfill: () -> Boolean) {
        val next = Entry(decor, isCurrent, tryBackfill)
        val previous = entry
        entry = next
        runOnMain {
            previous?.dispose()
            if (entry !== next || !next.isCurrentEntry()) {
                next.dispose()
            } else {
                next.install()
            }
        }
    }

    fun cancel() {
        val previous = entry
        entry = null
        runOnMain { previous?.dispose() }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() === Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private inner class Entry(
        decor: View,
        private var isCurrent: () -> Boolean,
        private var tryBackfill: () -> Boolean
    ) : View.OnAttachStateChangeListener,
        ViewTreeObserver.OnGlobalLayoutListener,
        ViewTreeObserver.OnWindowFocusChangeListener {
        private val root = WeakReference(decor)
        private var observer = WeakReference<ViewTreeObserver>(null)
        private var active = true
        private var pendingAttempt: Runnable? = null

        fun isCurrentEntry(): Boolean = active && entry === this &&
            runCatching(isCurrent).getOrDefault(false)

        fun install() {
            if (!isCurrentEntry()) {
                dispose()
                return
            }
            val view = root.get() ?: run { dispose(); return }
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) onViewAttachedToWindow(view)
        }

        override fun onViewAttachedToWindow(view: View) {
            if (!isCurrentEntry() || root.get() !== view) return
            observe(view.viewTreeObserver)
            attempt()
        }

        override fun onViewDetachedFromWindow(view: View) {
            if (root.get() === view) {
                removeObserver(view)
                cancelPendingAttempt()
            }
        }

        override fun onGlobalLayout() = attempt()

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            if (hasFocus) attempt()
        }

        private fun observe(current: ViewTreeObserver) {
            val saved = observer.get()
            if (saved === current && saved?.isAlive == true) return
            removeObserver(root.get())
            if (!current.isAlive) return
            current.addOnGlobalLayoutListener(this)
            current.addOnWindowFocusChangeListener(this)
            observer = WeakReference(current)
        }

        private fun attempt() {
            val view = root.get() ?: run { dispose(); return }
            if (!isCurrentEntry()) {
                dispose()
                return
            }
            if (!view.isAttachedToWindow) return
            if (pendingAttempt != null) return
            val pending = Runnable {
                pendingAttempt = null
                val currentRoot = root.get() ?: run { dispose(); return@Runnable }
                if (!isCurrentEntry()) {
                    dispose()
                    return@Runnable
                }
                if (!currentRoot.isAttachedToWindow) return@Runnable
                if (runCatching(tryBackfill).getOrDefault(false)) dispose()
            }
            pendingAttempt = pending
            mainHandler.post(pending)
        }

        private fun cancelPendingAttempt() {
            pendingAttempt?.let(mainHandler::removeCallbacks)
            pendingAttempt = null
        }

        private fun removeObserver(view: View?) {
            val saved = observer.get()
            if (saved?.isAlive == true) {
                saved.removeOnGlobalLayoutListener(this)
                saved.removeOnWindowFocusChangeListener(this)
            }
            val current = view?.viewTreeObserver
            if (current !== saved && current?.isAlive == true) {
                current.removeOnGlobalLayoutListener(this)
                current.removeOnWindowFocusChangeListener(this)
            }
            observer.clear()
        }

        fun dispose() {
            if (!active) return
            active = false
            cancelPendingAttempt()
            root.get()?.removeOnAttachStateChangeListener(this)
            removeObserver(root.get())
            isCurrent = { false }
            tryBackfill = { false }
            if (entry === this) entry = null
        }
    }
}
