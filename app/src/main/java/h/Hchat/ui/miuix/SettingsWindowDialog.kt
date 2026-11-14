package h.Hchat.ui.miuix

import android.view.KeyEvent
import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import top.yukonga.miuix.kmp.window.WindowDialog as MiuixWindowDialog

/** A dialog has its own window; route its ordinary completed Back action to this dialog. */
@Composable
internal fun SettingsWindowDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    maxWidth: Dp? = null,
    largeScreen: Boolean? = null,
    cornerRadius: Dp? = null,
    content: @Composable () -> Unit
) {
    val dialogContent: @Composable () -> Unit = {
        val view = LocalView.current
        val owner = remember(view) {
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = NavigationEventDispatcher()
            }
        }
        val directInput = remember(owner) { DirectNavigationEventInput() }
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val currentImeVisible = rememberUpdatedState(imeVisible)
        val keyboardController = rememberUpdatedState(LocalSoftwareKeyboardController.current)
        DisposableEffect(view, owner, directInput) {
            owner.navigationEventDispatcher.addInput(directInput)
            var installed = false
            var dialogWindow: Window? = null
            var originalCallback: Window.Callback? = null
            var localCallback: Window.Callback? = null
            var backCallbackCleanup: (() -> Unit)? = null
            fun install(v: View) {
                if (installed) return
                val window = (v.parent as? DialogWindowProvider)?.window
                val original = window?.callback
                if (window != null && original != null) {
                    backCallbackCleanup = attachCompletedBackCallback(window.decorView) {
                        if (currentImeVisible.value) keyboardController.value?.hide()
                        else completeStandardBack(directInput)
                    }
                    val callback = object : Window.Callback by original {
                        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                            if (event.keyCode == KeyEvent.KEYCODE_BACK && !currentImeVisible.value) {
                                if (event.action == KeyEvent.ACTION_UP) completeStandardBack(directInput)
                                return true
                            }
                            return original.dispatchKeyEvent(event)
                        }
                    }
                    dialogWindow = window
                    originalCallback = original
                    localCallback = callback
                    window.callback = callback
                }
                installed = true
            }
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = install(v)
                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            if (view.isAttachedToWindow) install(view) else view.addOnAttachStateChangeListener(attachListener)
            onDispose {
                view.removeOnAttachStateChangeListener(attachListener)
                backCallbackCleanup?.invoke()
                if (dialogWindow?.callback === localCallback) dialogWindow?.callback = originalCallback
                owner.navigationEventDispatcher.dispose()
            }
        }
        SideEffect { owner.navigationEventDispatcher.isEnabled = true }
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
            val navigationState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
            NavigationBackHandler(
                state = navigationState,
                isBackEnabled = true,
                onBackCompleted = onDismissRequest
            )
            content()
        }
    }
    if (maxWidth == null) {
        MiuixWindowDialog(show = show, title = title, onDismissRequest = onDismissRequest, content = dialogContent)
    } else {
        MiuixWindowDialog(
            show = show,
            title = title,
            onDismissRequest = onDismissRequest,
            maxWidth = maxWidth,
            largeScreen = largeScreen,
            cornerRadius = cornerRadius,
            content = dialogContent
        )
    }
}
