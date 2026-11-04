package h.Hchat.hooks.items.sportstep

import android.os.Handler
import android.os.Looper
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.core.DexInstallScheduler
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/** Owns one manual request and a callback hook for the current feature lifecycle. */
internal class SportStepRuntime(private val context: FeatureContext) {
    private val handler = Handler(Looper.getMainLooper())
    private val state = StepRequestState()
    private val settings = SportStepSettings(context.hostContext())
    private var hook: XposedInterface.HookHandle? = null
    private var timeout: Runnable? = null
    private var listener: ((StepRequestState.Status) -> Unit)? = null
    @Volatile private var active = true
    @Volatile private var dayStart: Method? = null

    init {
        DexInstallScheduler.schedule("sport_step:day_start", "运动步数日期定位", DexInstallScheduler.Stage.BRIDGE) {
            val found = context.dexFinder().sportDayStartMethod()
            if (active) dayStart = found
            found != null
        }
    }

    fun listen(callback: (StepRequestState.Status) -> Unit) {
        listener = callback
        state.updateSession(context.hostContext().packageName, identity())
        callback(state.status())
    }

    fun stopListening(callback: (StepRequestState.Status) -> Unit) {
        if (listener === callback) listener = null
    }

    fun submit(step: Int): String? {
        if (!active || step <= 0) return "输入无效"
        val account = identity()
        val host = context.hostContext().packageName
        state.updateSession(host, account)
        if (account.isBlank()) {
            timeout?.let(handler::removeCallbacks)
            timeout = null
            publish()
            return "当前账号未就绪，未提交"
        }
        if (state.status() == StepRequestState.Status.ENQUEUED || state.status() == StepRequestState.Status.SUBMITTING) {
            return "上一请求仍在处理中"
        }
        val api = WeChatApis.messages() ?: return "微信接口未就绪，未提交"
        if (!ensureHook()) return "网络回调不可用，未提交"
        val day = runCatching { (dayStart?.invoke(null) as? Long)?.takeIf { it > 0L } }.getOrNull()
            ?: return "微信日期尚未就绪，未提交"
        if (identity() != account || !settings.recordManualDay(account, day.toString())) {
            return "账号或设置存储不可用，未提交"
        }
        var scene: Any? = null
        var prepared = false
        val queued = api.uploadDeviceStep(step.toLong()) { request ->
            scene = request
            if (!state.begin(host, account, request)) false
            else if (identity() != account) {
                state.invalidate()
                false
            } else {
                prepared = true
                true
            }
        }
        val request = scene
        if (request == null || !prepared) return "请求未入队，请检查账号和设置存储后重试"
        val currentAccount = identity()
        if (currentAccount != account) {
            state.invalidate()
            publish()
            return "账号已切换，结果未确认"
        }
        state.enqueueResult(host, account, request, queued)
        if (queued && state.status() == StepRequestState.Status.ENQUEUED) {
            val expiry = Runnable {
                if (active) {
                    state.updateSession(host, identity())
                    state.expire(host, account, request)
                    publish()
                }
            }
            timeout?.let(handler::removeCallbacks)
            timeout = expiry
            handler.postDelayed(expiry, 30_000L)
        }
        publish()
        return null
    }

    private fun identity(): String = WeChatApis.account()?.sessionIdentity().orEmpty()

    private fun ensureHook(): Boolean {
        if (hook?.let { HookRegistry.get().isActive(it) } == true) return true
        val method = context.dexFinder().uploadDeviceStepResponseMethod() ?: return false
        return runCatching {
            hook = HookRegistry.get().withFeature(SportStepFeature.ID) {
                HookRegistry.get().hook(method, XposedInterface.Hooker { chain ->
                    val receiver = chain.thisObject
                    val errorType = (chain.args.getOrNull(1) as? Int)
                    val errorCode = (chain.args.getOrNull(2) as? Int)
                    val result = chain.proceed()
                    if (receiver != null && errorType != null && errorCode != null) {
                        handler.post {
                            if (active) {
                                val host = context.hostContext().packageName
                                val account = identity()
                                state.updateSession(host, account)
                                state.networkResult(host, account, receiver, errorType, errorCode)
                                if (state.status() == StepRequestState.Status.NETWORK_SUCCESS ||
                                    state.status() == StepRequestState.Status.NETWORK_FAILURE) {
                                    timeout?.let(handler::removeCallbacks)
                                    timeout = null
                                }
                                publish()
                            }
                        }
                    }
                    result
                })
            }
            true
        }.getOrDefault(false)
    }

    private fun publish() { listener?.invoke(state.status()) }

    fun destroy() {
        active = false
        DexInstallScheduler.cancel("sport_step:day_start")
        dayStart = null
        timeout?.let(handler::removeCallbacks)
        timeout = null
        listener = null
        state.invalidate()
        hook?.let { runCatching { HookRegistry.get().unhook(it) } }
        hook = null
    }
}
