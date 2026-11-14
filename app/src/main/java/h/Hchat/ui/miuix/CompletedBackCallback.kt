package h.Hchat.ui.miuix

import android.os.Build
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent

/** Routes the completed system Back action without observing gesture progress or animating content. */
internal fun attachCompletedBackCallback(view: View, onBack: () -> Unit): (() -> Unit)? {
    if (Build.VERSION.SDK_INT < 33) return null
    val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
    val callback = OnBackInvokedCallback {
        onBack()
    }
    return runCatching {
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        val unregister: () -> Unit = { dispatcher.unregisterOnBackInvokedCallback(callback) }
        unregister
    }.getOrNull()
}

/** Delivers one completed Back action to NavigationEvent handlers without any gesture progress. */
internal fun completeStandardBack(input: DirectNavigationEventInput) {
    input.backStarted(NavigationEvent())
    input.backCompleted()
}
