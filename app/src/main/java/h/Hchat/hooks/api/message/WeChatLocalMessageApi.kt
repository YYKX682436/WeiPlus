package h.Hchat.hooks.api.message

import android.text.TextUtils
import io.github.libxposed.api.XposedInterface
import h.Hchat.dexkit.DexFinder
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.api.media.WeChatInternalServices
import h.Hchat.utils.HLog
import h.Hchat.hooks.api.model.WeChatMessageTypes
import h.Hchat.utils.KavaReflector
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 微信本地消息 API。
 *
 * 系统提示优先走微信自身的 PocketMoneyNewXMLListener 插入链路，避免直接写 message 表。
 */
class WeChatLocalMessageApi(
    private val dexFinder: DexFinder?,
    private val logger: Logger?
) {
    @Volatile
    private var createTimeHookInstalled = false

    fun interface Logger {
        fun log(message: String)
    }

    fun isAvailable(): Boolean {
        return dexFinder?.hasLocalMessageApi() == true
    }

    fun ensureReady() {
        val finder = dexFinder ?: return
        if (!finder.hasLocalMessageApi() || finder.localMessageCreateTimeMethod == null ||
            finder.localSystemMessageMethod != null && finder.localSystemMessageStorageInsertMethod == null
        ) {
            finder.resolveLocalMessageApi()
        }
        installCreateTimeHook()
    }

    fun installCreateTimeHook(): Boolean {
        if (createTimeHookInstalled) return true
        val finder = dexFinder ?: return false
        if (finder.localMessageCreateTimeMethod == null) {
            finder.resolveLocalMessageApi()
        }
        val method = finder.localMessageCreateTimeMethod ?: return false
        synchronized(this) {
            if (createTimeHookInstalled) return true
            HookRegistry.get().hookOnce(CREATE_TIME_HOOK_KEY, method, XposedInterface.Hooker { chain ->
                val pending = PENDING_CREATE_TIME.get() ?: return@Hooker chain.proceed()
                val args = chain.args
                val talker = args.getOrNull(0) as? String ?: return@Hooker chain.proceed()
                if (talker != pending.talker) return@Hooker chain.proceed()
                return@Hooker pending.createTime
            })
            createTimeHookInstalled = true
        }
        return true
    }

    fun insertSystemMessage(talker: String?, content: String?, createTime: Long = System.currentTimeMillis()): Long {
        return insertSystemMessageResult(talker, content, createTime, true).messageId
    }

    fun insertSystemMessageAt(talker: String?, content: String?, createTime: Long): Long {
        return insertSystemMessageResult(talker, content, createTime, false).messageId
    }

    internal fun insertSystemMessageAtWithOutcome(talker: String?, content: String?, createTime: Long) =
        insertSystemMessageResult(talker, content, createTime, false)

    private fun insertSystemMessageResult(
        talker: String?,
        content: String?,
        createTime: Long,
        useWechatCreateTime: Boolean
    ): SystemMessageInsertResult {
        if (TextUtils.isEmpty(talker) || TextUtils.isEmpty(content)) {
            log("插入系统消息失败: talker/content 为空")
            return SystemMessageInsertResult(NativeInsertOutcome.FAILED)
        }
        val finder = dexFinder
        if (finder == null || !finder.hasLocalMessageApi()) {
            log("插入系统消息失败: 本地消息API未就绪")
            return SystemMessageInsertResult(NativeInsertOutcome.FAILED)
        }
        if (!useWechatCreateTime && !installCreateTimeHook()) {
            log("插入系统消息失败: createTime hook 未就绪")
            return SystemMessageInsertResult(NativeInsertOutcome.FAILED)
        }
        return runCatching {
            val expectedCreateTime = if (useWechatCreateTime) null else normalizeCreateTimeMillis(createTime)
            val nativeAttempt = insertViaWechatSystemMessageMethod(
                finder,
                talker.orEmpty(),
                content.orEmpty(),
                expectedCreateTime
            )
            when {
                nativeAttempt?.outcome == NativeInsertOutcome.SUCCESS ->
                    return@runCatching SystemMessageInsertResult(NativeInsertOutcome.SUCCESS, nativeAttempt.messageId)
                NativeInsertFallbackPolicy.mayUseDirectStorage(nativeAttempt?.outcome) -> Unit
                // The native listener may have written through storage without the receipt hook
                // observing it. A direct retry here can create a second visible system message.
                else -> return@runCatching SystemMessageInsertResult(NativeInsertOutcome.UNKNOWN)
            }
            val msg = newMessage(finder, talker.orEmpty())
                ?: throw IllegalStateException("消息对象创建失败")
            fillSystemMessage(msg, talker.orEmpty(), content.orEmpty(), createTime, useWechatCreateTime)
            val result = insertMessageDirect(finder, msg)
            val insertedId = (result as? Number)?.toLong() ?: 0L
            if (isStoredSystemMessage(insertedId, talker.orEmpty(), content.orEmpty(), expectedCreateTime)) {
                SystemMessageInsertResult(NativeInsertOutcome.SUCCESS, insertedId)
            } else {
                log("插入系统消息失败: 本地存储未确认")
                SystemMessageInsertResult(NativeInsertOutcome.UNKNOWN)
            }
        }.onFailure {
            log("插入系统消息失败: ${it.message}")
        }.getOrDefault(SystemMessageInsertResult(NativeInsertOutcome.UNKNOWN))
    }

    private fun insertViaWechatSystemMessageMethod(
        finder: DexFinder,
        talker: String,
        content: String,
        createTime: Long?
    ): NativeSystemInsertAttempt? {
        val method = finder.localSystemMessageMethod ?: return null
        if (!installLocalMessageInsertReceiptHook(finder)) {
            log("原生系统消息插入结果无法观察，转本地消息存储API")
            return null
        }
        val owner = newSystemMessageOwner(finder, method)
            ?: return NativeSystemInsertAttempt(NativeInsertOutcome.FAILED)
        val receipt = NativeSystemMessageInsertReceipt(talker, createTime)
        val previousCreateTime = PENDING_CREATE_TIME.get()
        val previousReceipt = PENDING_SYSTEM_INSERT_RECEIPT.get()
        if (createTime != null) {
            PENDING_CREATE_TIME.set(TimedInsert(talker, createTime))
        }
        PENDING_SYSTEM_INSERT_RECEIPT.set(receipt)
        return try {
            KavaReflector.invoke(method, owner, talker, content, "")
            val inserted = receipt.insertedMessage()
            if (inserted == null) {
                NativeSystemInsertAttempt(NativeInsertOutcome.UNKNOWN)
            } else if (isStoredSystemMessage(inserted.messageId, talker, inserted.content, createTime)) {
                NativeSystemInsertAttempt(NativeInsertOutcome.SUCCESS, inserted.messageId)
            } else {
                log("原生系统消息调用已写入但存储回读未确认")
                NativeSystemInsertAttempt(NativeInsertOutcome.UNKNOWN)
            }
        } catch (error: Throwable) {
            // Invocation may have crossed the write boundary before throwing.
            NativeSystemInsertAttempt(NativeInsertOutcome.UNKNOWN)
        } finally {
            if (previousCreateTime == null) PENDING_CREATE_TIME.remove() else PENDING_CREATE_TIME.set(previousCreateTime)
            if (previousReceipt == null) PENDING_SYSTEM_INSERT_RECEIPT.remove() else PENDING_SYSTEM_INSERT_RECEIPT.set(previousReceipt)
        }
    }

    private fun installLocalMessageInsertReceiptHook(finder: DexFinder): Boolean {
        val method = if (finder.localSystemMessageMethod != null) {
            finder.localSystemMessageStorageInsertMethod
        } else {
            finder.localMessageInsertMethod
        } ?: return false
        return runCatching {
            HookRegistry.get().hookOnce(LOCAL_INSERT_HOOK_KEY, method, XposedInterface.Hooker { chain ->
                val message = chain.args.getOrNull(0)
                val result = chain.proceed()
                val receipt = PENDING_SYSTEM_INSERT_RECEIPT.get()
                if (receipt != null && message != null) {
                    receipt.record(
                        actualTalker = stringProperty(message, "getTalker", "field_talker", "talker"),
                        actualContent = stringProperty(message, "getContent", "field_content", "content"),
                        actualType = numberProperty(message, "getType", "field_type", "type").toInt(),
                        actualCreateTime = numberProperty(message, "getCreateTime", "field_createTime", "createTime"),
                        insertedMessageId = (result as? Number)?.toLong() ?: 0L
                    )
                }
                result
            })
            true
        }.onFailure {
            log("本地消息插入结果Hook失败: ${it.message}")
        }.getOrDefault(false)
    }

    private fun stringProperty(target: Any, getter: String, vararg fields: String): String? {
        val method = KavaReflector.findMethod(target.javaClass, getter)
        val fromMethod = runCatching { KavaReflector.invoke(method, target) as? String }.getOrNull()
        if (fromMethod != null) return fromMethod
        return fields.firstNotNullOfOrNull { field -> KavaReflector.readField(target, field) as? String }
    }

    private fun numberProperty(target: Any, getter: String, vararg fields: String): Long {
        val method = KavaReflector.findMethod(target.javaClass, getter)
        val fromMethod = runCatching { KavaReflector.invoke(method, target) as? Number }.getOrNull()
        if (fromMethod != null) return fromMethod.toLong()
        return fields.firstNotNullOfOrNull { field -> (KavaReflector.readField(target, field) as? Number)?.toLong() } ?: 0L
    }

    private fun isStoredSystemMessage(
        messageId: Long,
        talker: String,
        content: String,
        expectedCreateTime: Long?
    ): Boolean {
        if (messageId <= 0L) return false
        val stored = WeChatApis.messageStore()?.getMessageById(messageId) ?: return false
        return stored.msgId == messageId &&
            stored.talker == talker &&
            stored.content == content &&
            stored.type == WeChatMessageTypes.SYSTEM &&
            (expectedCreateTime == null || normalizeCreateTimeMillis(stored.createTime) == expectedCreateTime)
    }

    private fun insertMessageDirect(finder: DexFinder, message: Any): Any? {
        finder.localMessageInsertMethod?.let { method ->
            return KavaReflector.invoke(method, null, message)
        }
        val method = finder.localSystemMessageStorageInsertMethod
            ?: throw IllegalStateException("消息存储插入方法未就绪")
        val ownerClass = method.declaringClass
        val owner = WeChatInternalServices.getService(finder, ownerClass)
            ?: KavaReflector.staticInstance(ownerClass)
            ?: staticOwnerInstance(ownerClass)
            ?: throw IllegalStateException("消息存储服务实例未就绪")
        return KavaReflector.invoke(method, owner, message)
    }

    private fun newSystemMessageOwner(finder: DexFinder, method: Method): Any? {
        val ownerClass = method.declaringClass ?: return null
        WeChatInternalServices.getService(finder, ownerClass)?.let { return it }
        KavaReflector.staticInstance(ownerClass)?.let { return it }
        staticOwnerInstance(ownerClass)?.let { return it }

        val errors = ArrayList<String>()
        KavaReflector.newInstanceByArgs(ownerClass, emptyArray())?.let { return it }
        runCatching {
            KavaReflector.findConstructor(ownerClass)?.let { ctor ->
                KavaReflector.newInstance(ctor)?.let { return it }
            }
        }.onFailure {
            errors.add("KavaCtor=${it.javaClass.simpleName}:${it.message}")
        }

        val unsafe = unsafeAllocate(ownerClass).also {
            if (it == null) errors.add("UnsafeAllocate=null")
        }
        if (unsafe != null) return unsafe

        log("系统消息API实例创建失败: owner=${ownerClass.name} service=false static=false ${errors.joinToString(";")}")
        return null
    }

    private fun unsafeAllocate(ownerClass: Class<*>): Any? {
        for (unsafeClassName in arrayOf("sun.misc.Unsafe", "jdk.internal.misc.Unsafe")) {
            val result = runCatching {
                val unsafeClass = KavaReflector.loadClass(unsafeClassName, ownerClass.classLoader) ?: return@runCatching null
                val unsafe = KavaReflector.declaredFields(unsafeClass).firstNotNullOfOrNull { field ->
                    if (!KavaReflector.isStatic(field) || !unsafeClass.isAssignableFrom(field.type)) {
                        null
                    } else {
                        KavaReflector.readField(field, null as Any?)
                    }
                } ?: return@runCatching null
                val allocate = KavaReflector.findDeclaredMethod(unsafeClass, "allocateInstance", Class::class.java)
                KavaReflector.invoke(allocate, unsafe, ownerClass)
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun staticOwnerInstance(ownerClass: Class<*>): Any? {
        for (field in KavaReflector.declaredFields(ownerClass)) {
            if (!KavaReflector.isStatic(field)) continue
            if (!ownerClass.isAssignableFrom(field.type)) continue
            val value = KavaReflector.readField(field, null as Any?)
            if (value != null) return value
        }
        return null
    }

    private fun newMessage(finder: DexFinder, talker: String): Any? {
        val ctor = finder.localMessageCtor ?: return null
        return if (ctor.parameterTypes.isEmpty()) {
            KavaReflector.newInstance(ctor)
        } else {
            KavaReflector.newInstance(ctor, talker)
        }
    }

    private fun fillSystemMessage(
        msg: Any,
        talker: String,
        content: String,
        createTime: Long,
        useWechatCreateTime: Boolean
    ) {
        val wechatCreateTime = if (useWechatCreateTime) {
            computeWechatCreateTime(talker, createTime)
        } else {
            normalizeCreateTimeMillis(createTime)
        }
        callIntSetter(msg, 0, "U0", "k1", "j1")
            || setIntField(msg, 0, "field_isSend", "isSend")
        callStringSetter(msg, talker, "i1", "A1", "H1", "C1", "u1")
            || setStringField(msg, talker, "field_talker", "talker")
        callIntSetter(msg, 3, "h1", "z1", "E1", "y1", "t1", "r1")
            || setIntField(msg, 3, "field_status", "status")
        callStringSetter(msg, content, "J0", "Y0", "d1", "c1")
            || setStringField(msg, content, "field_content", "content")
        callLongSetter(msg, wechatCreateTime, "K0", "a1", "f1", "e1", "d1")
            || setLongField(msg, wechatCreateTime, "field_createTime", "createTime")
        callIntSetter(msg, WeChatMessageTypes.SYSTEM, "setType")
            || setIntField(msg, WeChatMessageTypes.SYSTEM, "field_type", "type")
        setLongField(msg, 0L, "field_msgSvrId", "msgSvrId")
        setStringField(msg, "", "field_imgPath", "imgPath")
        setStringField(msg, "", "field_reserved", "reserved")
        setStringField(msg, "", "field_transContent", "transContent")
        setStringField(msg, "", "field_msgSource", "msgSource")
    }

    private fun normalizeCreateTimeMillis(createTime: Long): Long {
        return if (createTime > 0L && createTime < 10_000_000_000L) createTime * 1000L else createTime
    }

    private fun computeWechatCreateTime(talker: String, createTime: Long): Long {
        val method = dexFinder?.localMessageCreateTimeMethod ?: return createTime
        val seconds = if (createTime > 10_000_000_000L) createTime / 1000L else createTime
        val value = KavaReflector.invoke(method, null, talker, seconds)
        return (value as? Number)?.toLong() ?: createTime
    }

    private fun callStringSetter(target: Any, value: String, vararg preferredNames: String): Boolean {
        return callSetter(target, String::class.java, value, *preferredNames)
    }

    private fun callIntSetter(target: Any, value: Int, vararg names: String): Boolean {
        return callSetter(target, Integer.TYPE, value, *names)
            || callSetter(target, Integer::class.java, value, *names)
    }

    private fun callLongSetter(target: Any, value: Long, vararg names: String): Boolean {
        return callSetter(target, java.lang.Long.TYPE, value, *names)
            || callSetter(target, java.lang.Long::class.java, value, *names)
    }

    private fun callSetter(target: Any, parameterType: Class<*>, value: Any, vararg preferredNames: String): Boolean {
        for (name in preferredNames.distinct()) {
            val method = KavaReflector.findMethodRecursive(target.javaClass, name, parameterType) ?: continue
            if (invokeSetter(method, target, value)) return true
        }
        return false
    }

    private fun invokeSetter(method: Method, target: Any, value: Any): Boolean {
        return KavaReflector.invokeSuccessfully(method, target, value)
    }

    private fun setStringField(target: Any, value: String, vararg names: String): Boolean {
        for (name in names) {
            val field = KavaReflector.findFieldRecursive(target.javaClass, name) ?: continue
            if (setField(field, target, value)) return true
        }
        return false
    }

    private fun setIntField(target: Any, value: Int, vararg names: String): Boolean {
        for (name in names) {
            val field = KavaReflector.findFieldRecursive(target.javaClass, name) ?: continue
            if (setField(field, target, value)) return true
        }
        return false
    }

    private fun setLongField(target: Any, value: Long, vararg names: String): Boolean {
        for (name in names) {
            val field = KavaReflector.findFieldRecursive(target.javaClass, name) ?: continue
            if (setField(field, target, value)) return true
        }
        return false
    }

    private fun setField(field: Field, target: Any, value: Any): Boolean {
        return KavaReflector.writeField(field, target, value)
    }

    private fun log(message: String) {
        HLog.e("$TAG $message")
        logger?.log("[WeChatLocalMessageApi] $message")
    }

    companion object {
        private const val TAG = "[Hchat:LocalMessage]"
        private const val CREATE_TIME_HOOK_KEY = "wechat_local_message_create_time"
        private const val LOCAL_INSERT_HOOK_KEY = "wechat_local_message_insert_receipt"
        private val PENDING_CREATE_TIME = ThreadLocal<TimedInsert?>()
        private val PENDING_SYSTEM_INSERT_RECEIPT = ThreadLocal<NativeSystemMessageInsertReceipt?>()
    }

    private data class NativeSystemInsertAttempt(
        val outcome: NativeInsertOutcome,
        val messageId: Long = 0L
    )

    private data class TimedInsert(
        val talker: String,
        val createTime: Long
    )
}

internal enum class NativeInsertOutcome { SUCCESS, FAILED, UNKNOWN }

internal data class SystemMessageInsertResult(
    val outcome: NativeInsertOutcome,
    val messageId: Long = 0L
)

internal object NativeInsertFallbackPolicy {
    fun mayUseDirectStorage(outcome: NativeInsertOutcome?): Boolean =
        outcome == null || outcome == NativeInsertOutcome.FAILED
}
