package h.Hchat.ui.miuix

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import h.Hchat.preferences.TermsGate
import java.util.WeakHashMap

/** Shows the first-use agreement over LauncherUI without replacing its native conversation list. */
object FirstUseAgreementRuntime : Application.ActivityLifecycleCallbacks {
    private const val LAUNCHER_ACTIVITY = "com.tencent.mm.ui.LauncherUI"
    private const val STARTUP_PROMPT_DELAY_MS = 650L

    private val lock = Any()
    private var application: Application? = null
    private val promptedActivities = WeakHashMap<Activity, Boolean>()
    private val overlays = WeakHashMap<Activity, AgreementOverlay>()

    @JvmStatic
    fun install(application: Application) {
        if (TermsGate.isAccepted(application)) return
        synchronized(lock) {
            if (this.application === application || this.application != null) return
            this.application = application
        }
        application.registerActivityLifecycleCallbacks(this)
    }

    /** Called after TermsGate has synchronously saved the user's acceptance. */
    @JvmStatic
    fun onAgreementAccepted(activity: Activity) {
        dismiss(activity)
        val registeredApplication = synchronized(lock) {
            application.also { application = null }
        }
        runCatching { registeredApplication?.unregisterActivityLifecycleCallbacks(this) }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.javaClass.name != LAUNCHER_ACTIVITY) return
        if (TermsGate.isAccepted(activity)) {
            onAgreementAccepted(activity)
            return
        }
        synchronized(lock) {
            if (promptedActivities.put(activity, true) != null) return
        }

        val decor = activity.window?.decorView as? ViewGroup ?: run {
            synchronized(lock) { promptedActivities.remove(activity) }
            return
        }
        decor.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) {
                synchronized(lock) { promptedActivities.remove(activity) }
                return@postDelayed
            }
            if (TermsGate.isAccepted(activity)) {
                onAgreementAccepted(activity)
                return@postDelayed
            }
            // An explicit launch into WeiPlus settings owns its own agreement surface.
            if (MiuixSettingsPage.isShowing(activity)) {
                synchronized(lock) { promptedActivities.remove(activity) }
                return@postDelayed
            }
            show(activity)
        }, STARTUP_PROMPT_DELAY_MS)
    }

    override fun onActivityDestroyed(activity: Activity) {
        dismiss(activity)
        synchronized(lock) { promptedActivities.remove(activity) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun show(activity: Activity) {
        if (TermsGate.isAccepted(activity)) return
        val decor = activity.window?.decorView as? ViewGroup ?: return
        if (MiuixSettingsPage.isShowing(activity)) return

        val owner = EmbeddedComposeOwner()
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            isClickable = false
            isFocusable = false
            tag = OVERLAY_TAG
        }
        owner.install(composeView)
        owner.attach()
        owner.installComposition(composeView)
        val overlay = AgreementOverlay(composeView, owner)
        synchronized(lock) {
            if (overlays.containsKey(activity) || TermsGate.isAccepted(activity)) {
                owner.destroy()
                return
            }
            overlays[activity] = overlay
        }

        composeView.setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner
            ) {
                HchatMiuixTheme(activity) {
                    FirstUseAgreementDialog(
                        context = activity,
                        onCancel = {
                            dismiss(activity)
                            activity.finishAffinity()
                        },
                        onAccepted = { onAgreementAccepted(activity) }
                    )
                }
            }
        }
        try {
            decor.addView(
                composeView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            composeView.createComposition()
        } catch (error: Throwable) {
            dismiss(activity)
            h.Hchat.utils.HLog.e("[Hchat:TermsGate] 首次协议提示安装失败", error)
        }
    }

    private fun dismiss(activity: Activity) {
        val overlay = synchronized(lock) { overlays.remove(activity) } ?: return
        runCatching { overlay.composeView.disposeComposition() }
        val decor = activity.window?.decorView as? ViewGroup
        runCatching {
            if (overlay.composeView.parent === decor) decor.removeView(overlay.composeView)
        }
        runCatching { overlay.owner.clear(overlay.composeView) }
        runCatching { overlay.owner.destroy() }
    }

    private data class AgreementOverlay(
        val composeView: ComposeView,
        val owner: EmbeddedComposeOwner
    )

    private const val OVERLAY_TAG = "Hchat:FirstUseAgreement"
}
