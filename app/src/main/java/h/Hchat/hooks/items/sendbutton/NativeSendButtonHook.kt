package h.Hchat.hooks.items.sendbutton

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.Editable
import android.text.TextWatcher
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.OutgoingTextDecoratorRegistry
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.KavaReflector
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object NativeSendButtonHook {
    private const val TAG = "[Hchat:SendButton]"
    private const val CHAT_FOOTER = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter"
    private const val TEXT_DECORATION_EXTRA = "hchat_send_text_decoration"
    private const val INPUT_SESSION_EXTRA = "hchat_send_input_session"
    @Volatile
    private var installedGeneration = -1L
    private val chatFooterFieldCache = mutableMapOf<Class<*>, Field?>()
    private val inputFieldCache = mutableMapOf<Class<*>, Field?>()
    private val noArgMethodCache = ConcurrentHashMap<String, Method>()
    private val oneArgMethodCache = ConcurrentHashMap<String, Method>()
    private val handlers = NativeSendButtonHandlers { id, throwable ->
        h.Hchat.utils.HLog.e("$TAG 发送按钮模块回调失败: $id ${throwable.message}", throwable)
    }
    private val inputWatchers = Collections.synchronizedMap(WeakHashMap<TextView, TextWatcher>())
    private val inputStarts = Collections.synchronizedMap(WeakHashMap<View, InputStart>())
    private val pendingInputStarts = Collections.synchronizedMap(
        WeakHashMap<View, PendingInputStart>()
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var lastChatFooter = WeakReference<Any>(null)

    fun interface SendButtonHandler {
        fun onClick(text: String): Boolean
    }

    class Subscription internal constructor(private val unsubscribeAction: () -> Unit) {
        @Volatile
        private var active = true

        fun unsubscribe() {
            if (!active) return
            active = false
            unsubscribeAction()
        }
    }

    fun registerHandler(id: String, handler: SendButtonHandler): Subscription {
        val unsubscribe = handlers.register(id, handler::onClick)
        return Subscription(unsubscribe)
    }

    @Synchronized
    fun resetForGeneration() {
        val watchers = synchronized(inputWatchers) {
            inputWatchers.entries.map { it.key to it.value }.also { inputWatchers.clear() }
        }
        watchers.forEach { (view, watcher) -> runCatching { view.removeTextChangedListener(watcher) } }
        synchronized(inputStarts) { inputStarts.clear() }
        synchronized(pendingInputStarts) { pendingInputStarts.clear() }
        chatFooterFieldCache.clear()
        inputFieldCache.clear()
        noArgMethodCache.clear()
        oneArgMethodCache.clear()
        handlers.clear()
        lastChatFooter.clear()
        installedGeneration = -1L
    }

    @Synchronized
    fun install(context: FeatureContext): Boolean {
        val generation = HookRegistry.get().installationGeneration()
        if (installedGeneration == generation) return true
        runCatching {
            val finder = context.dexFinder()
            finder.resolveNativeSendButtonApi()
            val method = finder.chatFooterSendClickMethod
            if (method == null) {
                h.Hchat.utils.HLog.e("$TAG 发送按钮Hook失败: 未定位到ChatFooter发送入口")
                return false
            }
                HookRegistry.get().hookOnce("native:send-button:click", method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val footer = findChatFooter(param.thisObject) ?: return
                    lastChatFooter = WeakReference(footer)
                    rememberActivityFromFooter(footer)
                    val input = findInputView(footer)
                    trackInputView(input)
                    val inputView = input as? View
                    val talker = WeChatApis.chatPage()?.currentTalker().orEmpty()
                    val inputStart = inputView?.let { currentInputStart(it, talker) }
                    val inputDuration = inputStart
                        ?.let { (SystemClock.elapsedRealtime() - it.startedAt).coerceAtLeast(0L) }
                        ?: 0L
                    param.setObjectExtra(
                        INPUT_SESSION_EXTRA,
                        InputSendSession(footer, inputView)
                    )
                    val text = readInputText(footer)
                    val handled = OutgoingTextDecoratorRegistry.withInputDuration(inputDuration) {
                        val handledByModule = dispatchHandlers(text)
                        handledByModule
                    }
                    if (!handled) {
                        val currentText = readInputText(footer)
                        runCatching {
                            applyTextDecorators(
                                footer,
                                talker,
                                currentText,
                                inputDuration
                            )
                        }
                            .onFailure {
                                h.Hchat.utils.HLog.e("$TAG 发送文字装饰失败: ${it.message}", it)
                            }
                            .getOrNull()
                            ?.let { param.setObjectExtra(TEXT_DECORATION_EXTRA, it) }
                        return
                    }
                    clearInputText(footer)
                    inputView?.let { view ->
                        rememberPendingInputStart(view, inputStart, talker, text)
                        clearInputStart(view)
                    }
                    param.result = null
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val decoration = param.getObjectExtra(TEXT_DECORATION_EXTRA)
                        as? AppliedTextDecoration
                    if (decoration != null) {
                        runCatching { restoreRejectedDecoration(decoration) }
                            .onFailure {
                                h.Hchat.utils.HLog.e("$TAG 恢复未发送文字失败: ${it.message}", it)
                            }
                    }
                    val session = param.getObjectExtra(INPUT_SESSION_EXTRA) as? InputSendSession
                        ?: return
                    val inputView = session.inputView ?: return
                    if (readInputText(session.footer).isEmpty()) {
                        clearInputStart(inputView)
                    }
                }
                })
            val inputTrackingInstalled = installInputTrackingHook(context.hostClassLoader())
            if (!inputTrackingInstalled) return false
            installedGeneration = generation
        }.onFailure {
            h.Hchat.utils.HLog.e("$TAG 发送按钮Hook异常: ${it.message}", it)
            return false
        }
        return installedGeneration == HookRegistry.get().installationGeneration()
    }

    private fun installInputTrackingHook(classLoader: ClassLoader): Boolean {
        val footerClass = KavaReflector.loadClass(CHAT_FOOTER, classLoader) ?: run {
            h.Hchat.utils.HLog.e("$TAG 输入时长Hook失败: 未找到ChatFooter")
            return false
        }
        val attachMethod = KavaReflector.declaredMethods(footerClass).singleOrNull { candidate ->
            candidate.name == "onAttachedToWindow" &&
                candidate.returnType == Void.TYPE &&
                candidate.parameterTypes.isEmpty()
        } ?: run {
            h.Hchat.utils.HLog.e("$TAG 输入时长Hook失败: 未找到ChatFooter挂载方法")
            return false
        }
        return runCatching {
            HookRegistry.get().hookOnce("native:send-button:input-attach", attachMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    trackInputView(findInputView(param.thisObject))
                }
            })
            true
        }.getOrElse {
            h.Hchat.utils.HLog.e(
                "$TAG 输入时长Hook失败: ${attachMethod.toGenericString()} ${it.message}",
                it
            )
            false
        }
    }

    internal fun trackInputView(input: Any?) {
        val textView = input as? TextView ?: return
        synchronized(inputWatchers) {
            if (inputWatchers.containsKey(textView)) return
            val inputRef = WeakReference(textView)
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(editable: Editable?) {
                    val input = inputRef.get() ?: return
                    if (editable.isNullOrEmpty()) {
                        clearInputStart(input)
                        return
                    }
                    val talker = WeChatApis.chatPage()?.currentTalker().orEmpty()
                    synchronized(inputStarts) {
                        val current = inputStarts[input]
                        if (current == null || current.talker != talker) {
                            inputStarts[input] = InputStart(SystemClock.elapsedRealtime(), talker)
                        }
                    }
                }
            }
            runCatching {
                textView.addTextChangedListener(watcher)
                inputWatchers[textView] = watcher
                if (textView.text.isNotEmpty()) {
                    val talker = WeChatApis.chatPage()?.currentTalker().orEmpty()
                    synchronized(inputStarts) {
                        if (inputStarts[textView]?.talker != talker) {
                            inputStarts[textView] = InputStart(SystemClock.elapsedRealtime(), talker)
                        }
                    }
                }
            }.onFailure {
                h.Hchat.utils.HLog.e("$TAG 输入时长观察器安装失败: ${it.message}", it)
            }
        }
    }

    private fun clearInputStart(view: View) {
        synchronized(inputStarts) { inputStarts.remove(view) }
    }

    private fun currentInputStart(view: View, talker: String): InputStart? =
        synchronized(inputStarts) {
            inputStarts[view]?.takeIf { it.talker == talker }
        }

    private fun rememberPendingInputStart(
        view: View,
        inputStart: InputStart?,
        talker: String,
        text: String
    ) {
        if (inputStart == null || inputStart.talker != talker || text.isEmpty()) return
        synchronized(pendingInputStarts) {
            pendingInputStarts[view] = PendingInputStart(inputStart, talker, text)
        }
    }

    private fun restorePendingInputStart(view: View, talker: String, text: String) {
        val pending = synchronized(pendingInputStarts) {
            pendingInputStarts[view]?.takeIf { it.talker == talker && it.text == text }
                ?.also { pendingInputStarts.remove(view) }
        } ?: return
        synchronized(inputStarts) { inputStarts[view] = pending.inputStart }
    }

    fun restoreInputTextIfEmpty(talker: String, text: String) {
        if (talker.isBlank() || text.isEmpty()) return
        mainHandler.post {
            if (h.Hchat.hooks.api.core.WeChatApis.chatPage()?.currentTalker().orEmpty() != talker) return@post
            val footer = lastChatFooter.get() ?: return@post
            val input = findInputView(footer) ?: return@post
            val view = input as? View ?: footer as? View ?: return@post
            if (!view.isAttachedToWindow || readInputText(footer).isNotEmpty()) return@post
            if (setInputText(input, text)) {
                (input as? View)?.let { restorePendingInputStart(it, talker, text) }
            }
        }
    }

    private fun dispatchHandlers(text: String): Boolean {
        return handlers.dispatch(text)
    }

    private fun applyTextDecorators(
        chatFooter: Any,
        talker: String,
        originalText: String,
        inputDurationMs: Long
    ): AppliedTextDecoration? {
        val decoration = OutgoingTextDecoratorRegistry.decorate(
            talker,
            originalText,
            inputDurationMs
        ) ?: return null
        val combinedPrefix = decoration.prefix
        val combinedSuffix = decoration.suffix
        val currentText = decoration.text

        val input = findInputView(chatFooter)
        val editable = when (input) {
            is TextView -> input.text as? Editable
            null -> null
            else -> invokeNoArg(input, "getText") as? Editable
        }
        if (editable != null) {
            if (combinedSuffix.isNotEmpty()) editable.insert(editable.length, combinedSuffix)
            if (combinedPrefix.isNotEmpty()) editable.insert(0, combinedPrefix)
            return AppliedTextDecoration(
                input = input ?: chatFooter,
                originalText = originalText,
                decoratedText = currentText,
                prefixLength = combinedPrefix.length,
                suffixLength = combinedSuffix.length
            )
        }
        val receiver = input ?: chatFooter
        if (!setInputText(receiver, currentText)) return null
        return AppliedTextDecoration(
            input = receiver,
            originalText = originalText,
            decoratedText = currentText,
            prefixLength = combinedPrefix.length,
            suffixLength = combinedSuffix.length
        )
    }

    private fun restoreRejectedDecoration(decoration: AppliedTextDecoration) {
        val current = when (val input = decoration.input) {
            is TextView -> input.text
            else -> invokeNoArg(input, "getText") as? CharSequence
        } ?: return
        if (current.toString() != decoration.decoratedText) return

        val editable = current as? Editable
        if (editable != null) {
            if (decoration.suffixLength > 0) {
                editable.delete(editable.length - decoration.suffixLength, editable.length)
            }
            if (decoration.prefixLength > 0) {
                editable.delete(0, decoration.prefixLength)
            }
        } else {
            setInputText(decoration.input, decoration.originalText)
        }
    }

    private fun setInputText(input: Any, text: String): Boolean {
        if (input is TextView) {
            input.text = text
            return true
        }
        val method = findOneArgMethod(input.javaClass, "setText", String::class.java)
            ?: return false
        KavaReflector.invoke(method, input, text)
        return true
    }

    private fun findChatFooter(listener: Any?): Any? {
        if (listener == null) return null
        val clazz = listener.javaClass
        val cached = synchronized(chatFooterFieldCache) {
            if (chatFooterFieldCache.containsKey(clazz)) chatFooterFieldCache[clazz] else null
        }
        if (cached != null) return KavaReflector.readField(cached, listener)
        if (synchronized(chatFooterFieldCache) { chatFooterFieldCache.containsKey(clazz) }) return null
        val resolved = allFields(clazz).firstOrNull { it.type.name == CHAT_FOOTER }
            ?.let { KavaReflector.accessible(it) }
        synchronized(chatFooterFieldCache) { chatFooterFieldCache[clazz] = resolved }
        return resolved?.let { KavaReflector.readField(it, listener) }
    }

    private fun rememberActivityFromFooter(chatFooter: Any) {
        val activity = activityFromContext((chatFooter as? View)?.context)
            ?: activityFromContext((findInputView(chatFooter) as? View)?.context)
            ?: return
        h.Hchat.hooks.api.core.WeChatApis.currentActivity()?.updateCurrentActivity(activity)
    }

    private fun activityFromContext(context: Context?): Activity? {
        var current = context
        var depth = 0
        while (current != null && depth < 8) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
            depth++
        }
        return null
    }

    private fun readInputText(chatFooter: Any): String {
        findInputView(chatFooter)?.let { input ->
            invokeNoArg(input, "getText")?.let { return it.toString() }
        }
        invokeNoArg(chatFooter, "getText")?.let { return it.toString() }
        return ""
    }

    private fun clearInputText(chatFooter: Any) {
        findInputView(chatFooter)?.let { input ->
            if (clearTextOnInput(input)) return
        }
        invokeOneArg(chatFooter, "setText", "")
    }

    private fun clearTextOnInput(input: Any): Boolean {
        if (input is TextView) {
            input.text = ""
            return true
        }
        val text = invokeNoArg(input, "getText")
        val clear = findNoArgMethod(text?.javaClass, "clear")
        if (text != null && clear != null) {
            KavaReflector.invoke(clear, text)
            return true
        }
        val setText = findOneArgMethod(input.javaClass, "setText", CharSequence::class.java)
            ?: findOneArgMethod(input.javaClass, "setText", String::class.java)
        if (setText != null) {
            KavaReflector.invoke(setText, input, "")
            return true
        }
        return false
    }

    internal fun findInputView(chatFooter: Any): Any? {
        val clazz = chatFooter.javaClass
        val cached = synchronized(inputFieldCache) {
            if (inputFieldCache.containsKey(clazz)) inputFieldCache[clazz] else null
        }
        if (cached != null) return KavaReflector.readField(cached, chatFooter)
        var best: Field? = null
        var bestScore = 0
        for (field in allFields(clazz)) {
            if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
            KavaReflector.accessible(field)
            val value = KavaReflector.readField(field, chatFooter) ?: continue
            val score = inputScore(value.javaClass)
            if (score > bestScore) {
                best = field
                bestScore = score
            }
        }
        val resolved = if (bestScore >= 3) best else null
        if (resolved != null) {
            synchronized(inputFieldCache) { inputFieldCache[clazz] = resolved }
        }
        return resolved?.let { KavaReflector.readField(it, chatFooter) }
    }

    private fun inputScore(clazz: Class<*>): Int {
        var score = 0
        if (findNoArgMethod(clazz, "getText") != null) score += 3
        if (findNoArgMethod(clazz, "clearComposingText") != null) score += 2
        if (findNoArgMethod(clazz, "getSimilarPasteSeqStr") != null) score += 2
        if (findNoArgMethod(clazz, "getPasterContent") != null) score += 1
        if (View::class.java.isAssignableFrom(clazz)) score += 1
        return score
    }

    private fun invokeNoArg(receiver: Any, name: String): Any? {
        val method = findNoArgMethod(receiver.javaClass, name) ?: return null
        return KavaReflector.invoke(method, receiver)
    }

    private fun invokeOneArg(receiver: Any, name: String, arg: Any): Any? {
        val method = findOneArgMethod(receiver.javaClass, name, arg.javaClass) ?: return null
        return KavaReflector.invoke(method, receiver, arg)
    }

    private fun findNoArgMethod(clazz: Class<*>?, name: String): Method? {
        if (clazz == null) return null
        val key = clazz.name + "#" + name
        noArgMethodCache[key]?.let { return it }
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (method in KavaReflector.declaredMethods(current)) {
                if (method.name == name && method.parameterTypes.isEmpty()) {
                    val accessible = KavaReflector.accessible(method) ?: return null
                    noArgMethodCache[key] = accessible
                    return accessible
                }
            }
            current = current.superclass
        }
        return null
    }

    private fun findOneArgMethod(clazz: Class<*>?, name: String, argType: Class<*>): Method? {
        if (clazz == null) return null
        val key = clazz.name + "#" + name + "#" + argType.name
        oneArgMethodCache[key]?.let { return it }
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (method in KavaReflector.declaredMethods(current)) {
                if (method.name != name || method.parameterTypes.size != 1) continue
                val paramType = method.parameterTypes[0]
                if (wrap(paramType).isAssignableFrom(wrap(argType))) {
                    val accessible = KavaReflector.accessible(method) ?: return null
                    oneArgMethodCache[key] = accessible
                    return accessible
                }
            }
            current = current.superclass
        }
        return null
    }

    private fun wrap(type: Class<*>): Class<*> {
        if (!type.isPrimitive) return type
        return when (type) {
            Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
            Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
            Char::class.javaPrimitiveType -> Char::class.javaObjectType
            Short::class.javaPrimitiveType -> Short::class.javaObjectType
            Int::class.javaPrimitiveType -> Int::class.javaObjectType
            Long::class.javaPrimitiveType -> Long::class.javaObjectType
            Float::class.javaPrimitiveType -> Float::class.javaObjectType
            Double::class.javaPrimitiveType -> Double::class.javaObjectType
            Void.TYPE -> Void::class.java
            else -> type
        }
    }

    private fun allFields(clazz: Class<*>?): List<Field> {
        val result = ArrayList<Field>()
        var current = clazz
        while (current != null && current != Any::class.java) {
            result += KavaReflector.declaredFields(current)
            current = current.superclass
        }
        return result
    }

    private data class AppliedTextDecoration(
        val input: Any,
        val originalText: String,
        val decoratedText: String,
        val prefixLength: Int,
        val suffixLength: Int
    )

    private data class InputStart(
        val startedAt: Long,
        val talker: String
    )

    private data class InputSendSession(
        val footer: Any,
        val inputView: View?
    )

    private data class PendingInputStart(
        val inputStart: InputStart,
        val talker: String,
        val text: String
    )

}
