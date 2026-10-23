package h.Hchat.hooks.core

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.utils.KavaReflector

/** Configuration/page transitions only; never walk a tree on an idle row bind. */
internal object ChatRowRefresh {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val afterTransition = Runnable { request() }

    fun requestAfterTransition() {
        // onEnterBegin runs before the chat view is shown. Coalesce one refresh after its animation.
        mainHandler.removeCallbacks(afterTransition)
        mainHandler.postDelayed(afterTransition, 300L)
    }

    private fun visibleActivity(): Activity? {
        val tracker = WeChatApis.currentActivity()
        tracker?.currentActivity()?.takeIf { it.hasWindowFocus() && !it.isFinishing && !it.isDestroyed }
            ?.let { return it }
        // API installation can follow LauncherUI.onResume; inline chat entry has no Activity resume.
        // Recover only on explicit transitions, and keep using the existing weak activity tracker.
        val thread = KavaReflector.invokeStaticMethod(
            KavaReflector.loadClass("android.app.ActivityThread", null), "currentActivityThread"
        ) ?: return null
        val records = KavaReflector.readField(thread, "mActivities") as? Map<*, *> ?: return null
        return records.values.asSequence().mapNotNull { KavaReflector.readField(it, "activity") as? Activity }
            .firstOrNull { it.hasWindowFocus() && !it.isFinishing && !it.isDestroyed }
            ?.also { tracker?.updateCurrentActivity(it) }
    }

    fun request() {
        mainHandler.post {
            val decor = visibleActivity()?.window?.decorView ?: return@post
            fun refresh(view: View) {
                if (!view.isShown) return
                if (generateSequence(view.javaClass as Class<*>?) { it.superclass }.any {
                        it.name == "androidx.recyclerview.widget.RecyclerView" ||
                            it.name == "android.support.v7.widget.RecyclerView"
                    }) {
                    val adapter = KavaReflector.invokeMethod(view, "getAdapter")
                    if (adapter != null && ChatAdapterRefresh.notifyChanged(adapter)) {
                        view.requestLayout()
                        return
                    }
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) refresh(view.getChildAt(i))
            }
            refresh(decor)
        }
    }
}
