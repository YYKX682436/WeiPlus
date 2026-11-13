package h.Hchat.ui.miuix

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import top.yukonga.miuix.kmp.window.WindowDialog as MiuixWindowDialog

/** A dialog owns a separate window, so it needs its own local input and dispatcher. */
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
        DisposableEffect(view, owner, directInput) {
            owner.navigationEventDispatcher.addInput(directInput)
            var installed = false
            var dialogWindow: Window? = null
            var originalCallback: Window.Callback? = null
            var localCallback: Window.Callback? = null
            fun install(v: View) {
                if (!installed) {
                    attachEmbeddedBackInput(v, owner.navigationEventDispatcher)
                    val window = (v.parent as? DialogWindowProvider)?.window
                    val original = window?.callback
                    if (window != null && original != null) {
                        val callback = object : Window.Callback by original {
                            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                                if (event.keyCode == KeyEvent.KEYCODE_BACK && !currentImeVisible.value) {
                                    if (event.action == KeyEvent.ACTION_UP) directInput.backCompleted()
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
            }
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    install(v)
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            if (view.isAttachedToWindow) install(view)
            else view.addOnAttachStateChangeListener(attachListener)
            onDispose {
                view.removeOnAttachStateChangeListener(attachListener)
                if (dialogWindow?.callback === localCallback) dialogWindow?.callback = originalCallback
                owner.navigationEventDispatcher.dispose()
            }
        }
        SideEffect { owner.navigationEventDispatcher.isEnabled = !imeVisible }
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
            show = show, title = title, onDismissRequest = onDismissRequest,
            maxWidth = maxWidth, largeScreen = largeScreen, cornerRadius = cornerRadius,
            content = dialogContent
        )
    }
}

/** Installs only this overlay's callback on the attached host window. */
internal fun attachEmbeddedBackInput(view: View, dispatcher: NavigationEventDispatcher) {
    if (Build.VERSION.SDK_INT >= 33) EmbeddedBackInputApi33.attach(view, dispatcher)
}

@RequiresApi(33)
private object EmbeddedBackInputApi33 {
    fun attach(view: View, dispatcher: NavigationEventDispatcher) {
        val window = view.findOnBackInvokedDispatcher() ?: run {
            HLog.e("[Hchat:MiuixSettings] 无法取得覆盖层窗口返回分发器")
            return
        }
        val enabled = runCatching {
            KavaReflector.findMethodRecursive(window.javaClass, "isOnBackInvokedCallbackEnabled")
                ?.invoke(window) as? Boolean
        }.getOrNull()
        val unchecked = if (enabled != true) {
            KavaReflector.findMethodRecursive(
                window.javaClass,
                "registerOnBackInvokedCallbackUnchecked",
                OnBackInvokedCallback::class.java,
                Int::class.javaPrimitiveType!!
            )
        } else null
        val localWindow = if (unchecked != null) object : OnBackInvokedDispatcher {
            override fun registerOnBackInvokedCallback(priority: Int, callback: OnBackInvokedCallback) {
                try {
                    unchecked.invoke(window, callback, priority)
                } catch (error: Throwable) {
                    HLog.e("[Hchat:MiuixSettings] 局部预测返回注册失败，尝试公开接口", error)
                    window.registerOnBackInvokedCallback(priority, callback)
                }
            }

            override fun unregisterOnBackInvokedCallback(callback: OnBackInvokedCallback) {
                window.unregisterOnBackInvokedCallback(callback)
            }
        } else window
        HLog.i("[Hchat:MiuixSettings] 返回窗口=${window.javaClass.name}, 平台启用=$enabled, 局部适配=${unchecked != null}")
        dispatcher.addInput(OnBackInvokedDefaultInput(localWindow))
    }
}
