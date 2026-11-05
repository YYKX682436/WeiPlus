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
    private val afterChatTransition = Runnable { requestChatMessageRows() }

    fun requestAfterTransition() {
        // onEnterBegin runs before the chat view is shown. Coalesce one refresh after its animation.
        mainHandler.removeCallbacks(afterTransition)
        mainHandler.postDelayed(afterTransition, 300L)
    }

    /** Message-bubble refreshes are limited to RecyclerViews whose rows are native chat items. */
    fun requestChatMessageRowsAfterTransition() {
        mainHandler.removeCallbacks(afterChatTransition)
        mainHandler.postDelayed(afterChatTransition, 300L)
    }

    fun cancelChatMessageRowsAfterTransition() {
        mainHandler.removeCallbacks(afterChatTransition)
    }

    /** Current Activity without requiring focus; readiness observers wait for focus themselves. */
    fun activityForReadiness(): Activity? {
        val tracker = WeChatApis.currentActivity()
        val tracked = tracker?.currentActivity()
            ?.takeIf { !it.isFinishing && !it.isDestroyed }
        if (tracked?.hasWindowFocus() == true) return tracked
        val thread = KavaReflector.invokeStaticMethod(
            KavaReflector.loadClass("android.app.ActivityThread", null), "currentActivityThread"
        )
        val records = KavaReflector.readField(thread, "mActivities") as? Map<*, *>
        val activities = records?.values.orEmpty().mapNotNull {
            (KavaReflector.readField(it, "activity") as? Activity)
                ?.takeIf { activity -> !activity.isFinishing && !activity.isDestroyed }
        }
        val active = activities.firstOrNull { it.hasWindowFocus() } ?: tracked ?: activities.firstOrNull()
        if (active != null) tracker?.updateCurrentActivity(active)
        return active
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
        refreshVisibleRecyclerViews(chatMessagesOnly = false)
    }

    fun requestChatMessageRows() {
        mainHandler.removeCallbacks(afterChatTransition)
        refreshVisibleRecyclerViews(chatMessagesOnly = true)
    }

    private fun refreshVisibleRecyclerViews(chatMessagesOnly: Boolean) {
        mainHandler.post {
            val decor = visibleActivity()?.window?.decorView ?: return@post
            fun refresh(view: View) {
                if (!view.isShown) return
                if (isRecyclerView(view) &&
                    (!chatMessagesOnly || hasChatMessageRows(view))
                ) {
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

    private fun isRecyclerView(view: View): Boolean =
        generateSequence(view.javaClass as Class<*>?) { it.superclass }.any {
            it.name == "androidx.recyclerview.widget.RecyclerView" ||
                it.name == "android.support.v7.widget.RecyclerView"
        }

    private fun hasChatMessageRows(view: View): Boolean {
        if (view !is ViewGroup) return false
        return hasChatMessageRows((0 until view.childCount).asSequence()
            .map { index -> view.getChildAt(index) }
            .filter { it.isShown }
            .map { it.tag?.javaClass?.name }
            .asIterable())
    }

    internal fun hasChatMessageRows(holderClassNames: Iterable<String?>): Boolean =
        holderClassNames.any(::isChatMessageHolderClassName)

    internal fun isChatMessageHolderClassName(className: String?): Boolean =
        className?.startsWith(CHAT_MESSAGE_HOLDER_PACKAGE_PREFIX) == true

    private const val CHAT_MESSAGE_HOLDER_PACKAGE_PREFIX =
        "com.tencent.mm.ui.chatting.viewitems."
}
