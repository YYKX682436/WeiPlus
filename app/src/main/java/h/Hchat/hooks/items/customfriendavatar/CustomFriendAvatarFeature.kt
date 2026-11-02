package h.Hchat.hooks.items.customfriendavatar

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import h.Hchat.dexkit.DexMethodCache
import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.ConversationMenuExtension
import h.Hchat.hooks.core.ConversationMenuExtensionRegistry
import h.Hchat.hooks.core.ConversationMenuExtensionTarget
import h.Hchat.hooks.core.ChatRowRefresh
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.items.conversationgroup.ConversationGroupRuntime
import h.Hchat.hooks.items.conversationgroup.ConversationGroupStore
import h.Hchat.hooks.items.customnotify.CustomNotificationRuntime
import h.Hchat.hooks.items.notification.NotificationAvatarBitmapCache
import h.Hchat.hooks.items.quickcontactedit.ConversationMenuLocator
import h.Hchat.ui.miuix.VoiceForwardMiuixDialog
import h.Hchat.utils.KavaReflector
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.LinkedHashMap
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

class CustomFriendAvatarFeature : BaseFeature() {
    private var runtime: CustomFriendAvatarRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "自定义头像"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(CustomFriendAvatarSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        runtime =
            CustomFriendAvatarRuntime(context, ::logFeatureError).also {
                it.installNotificationHooks()
            }
        scheduleInstall()
        subscribe(Events.DexReady::class.java) { scheduleInstall() }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    private fun scheduleInstall() {
        DexInstallScheduler.schedule(ID, name()) { runtime?.install() == true }
        DexInstallScheduler.schedule(DESKTOP_SHORTCUT_TASK_ID, "自定义头像桌面快捷方式") {
            runtime?.installDesktopShortcutHook() == true
        }
    }

    companion object {
        const val ID = "custom_friend_avatar"
        private const val DESKTOP_SHORTCUT_TASK_ID = "custom_friend_avatar_desktop_shortcut"
    }
}

private class CustomFriendAvatarRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit,
) {
    private val hookedMembers = ConcurrentHashMap.newKeySet<Executable>()
    private val drawableBindings = Collections.synchronizedMap(WeakHashMap<Any, DrawableAvatarBinding>())
    private val avatarPrefs = CustomFriendAvatarSettings.preferences(context.hostContext())
    private val roundAvatarPrefs = h.Hchat.preferences.HchatStorage.preferences(
        context.hostContext(), h.Hchat.hooks.items.roundavatar.RoundAvatarSettings.PREFS_NAME
    )
    @Volatile private var avatarSettings = readAvatarSettings()
    @Volatile private var configuredAvatarIds = CustomFriendAvatarStore.configuredFriends(context.hostContext())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingAvatarMainTasks = ConcurrentHashMap.newKeySet<Runnable>()
    private val avatarLoadExecutor = ThreadPoolExecutor(
        1, 1, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(64),
        { runnable -> Thread(runnable, "Hchat-CustomAvatarLoad").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    ).apply { allowCoreThreadTimeOut(true) }
    private val avatarRetryScheduler = AvatarRetryScheduler { delayMillis, action ->
        val retry = Runnable(action)
        mainHandler.postDelayed(retry, delayMillis.coerceAtLeast(0L))
        AvatarRetryHandle { mainHandler.removeCallbacks(retry) }
    }
    private val avatarLoadCoordinator = AvatarLoadCoordinator<Drawable>(scheduler = avatarRetryScheduler)
    private val avatarBindingRevision = AtomicLong(0L)
    private val avatarSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in AVATAR_SETTING_KEYS) refreshAvatarSettings()
    }
    private val roundAvatarSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == h.Hchat.hooks.items.roundavatar.RoundAvatarSettings.KEY_ENABLE ||
            key == h.Hchat.hooks.items.roundavatar.RoundAvatarSettings.KEY_RADIUS_FACTOR
        ) refreshAvatarSettings()
    }
    private val avatarStoreListener = CustomFriendAvatarStore.ChangeListener { _, wxid ->
        postAvatarTask { onAvatarChanged(wxid) }
    }
    private val avatarStoreListenerToken = CustomFriendAvatarStore.addChangeListener(avatarStoreListener)
    private val conversationBindings =
        Collections.synchronizedMap(WeakHashMap<MenuItem, ConversationBinding>())
    private val conversationGroupAvatarCache =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, CachedGroupAvatar>(16, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, CachedGroupAvatar>
                ): Boolean {
                    return size > GROUP_CACHE_MAX_ENTRIES
                }
            }
        )
    private val conversationMenuExtension =
        object : ConversationMenuExtension {
            override val itemId: Int = CONVERSATION_MENU_ID
            override val order: Int = 120

            override fun title(target: ConversationMenuExtensionTarget): String = MENU_TITLE

            override fun isVisible(target: ConversationMenuExtensionTarget): Boolean {
                return CustomFriendAvatarSettings.conversationMenuEnabled(context.hostContext()) &&
                    isAvatarTarget(target.talker)
            }

            override fun onClick(target: ConversationMenuExtensionTarget) {
                showAvatarActions(target.activity, target.talker)
            }
        }
    private var installed = false
    private var desktopShortcutInstalled = false
    @Volatile private var destroyed = false

    init {
        avatarPrefs.registerOnSharedPreferenceChangeListener(avatarSettingsListener)
        roundAvatarPrefs.registerOnSharedPreferenceChangeListener(roundAvatarSettingsListener)
    }

    fun installNotificationHooks() {
        if (destroyed) return
        NotificationManager::class
            .java
            .declaredMethods
            .asSequence()
            .filter { method ->
                val types = method.parameterTypes
                method.name == "notify" &&
                    types.isNotEmpty() &&
                    types.last() == Notification::class.java
            }
            .forEach { method ->
                hook(
                    method,
                    Hooker { chain ->
                        if (destroyed) return@Hooker chain.proceed()
                        if (!CustomFriendAvatarSettings.notificationsEnabled(context.hostContext()))
                            return@Hooker chain.proceed()
                        val notification =
                            chain.args.lastOrNull() as? Notification
                                ?: return@Hooker chain.proceed()
                        val talker = CustomNotificationRuntime.notificationTalker(notification)
                        val virtualAvatar = hasVirtualAvatar(talker)
                        if (!isAvatarConfigured(talker, virtualAvatar)) return@Hooker chain.proceed()
                        val source =
                            CustomFriendAvatarStore.loadNotificationBitmap(context.hostContext(), talker)
                                ?: return@Hooker chain.proceed()
                        replaceNotificationLargeIcon(
                            notification,
                            processBitmap(source, NATIVE_RADIUS_FACTOR),
                        )
                        chain.proceed()
                    },
                )
            }
    }

    @Synchronized
    fun installDesktopShortcutHook(): Boolean {
        if (destroyed) return false
        if (desktopShortcutInstalled) return true
        val method = locateDesktopShortcutMethod() ?: return false
        desktopShortcutInstalled =
            hook(
                method,
                Hooker { chain ->
                    val result = chain.proceed()
                    runCatching { replaceDesktopShortcutIcon(chain, result) }
                        .onFailure { logger("替换桌面快捷方式头像失败", it) }
                    result
                },
            )
        return desktopShortcutInstalled
    }

    private fun replaceDesktopShortcutIcon(chain: Chain, result: Any?) {
        if (!CustomFriendAvatarSettings.desktopShortcutEnabled(context.hostContext())) return
        if (chain.args.getOrNull(2) as? Boolean != true) return
        val wxid = (chain.args.getOrNull(1) as? String)?.trim().orEmpty()
        if (wxid.isEmpty()) return
        val intent = result as? Intent ?: return
        @Suppress("DEPRECATION")
        val original = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON) as? Bitmap ?: return
        val source = CustomFriendAvatarStore.loadBitmap(context.hostContext(), wxid) ?: return
        val replacement =
            if (source.width == original.width && source.height == original.height) {
                source
            } else {
                Bitmap.createScaledBitmap(source, original.width, original.height, true)
            }
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, replacement)
    }

    @Synchronized
    fun install(): Boolean {
        if (destroyed) return false
        val avatarMembers = locateAvatarMembers() ?: return false
        val legacy =
            hook(
                avatarMembers.legacyLoad,
                Hooker { chain ->
                    val requestedWxid = chain.args.getOrNull(1) as? String
                    val args = chain.args.toTypedArray()
                    if (requestedWxid != null && !conversationGroupHasCustomAvatar(requestedWxid)) {
                        automaticGroupAvatarSource(requestedWxid)?.let { args[1] = it }
                    }
                    val result = chain.proceed(args)
                    // The host receives the source avatar ID; post-processing owns the original
                    // group ID.
                    val image = chain.args.getOrNull(0) as? ImageView ?: return@Hooker result
                    val wxid = requestedWxid ?: return@Hooker result
                    val surface = AvatarSurfaceClassifier.forView(image)
                    val virtualAvatar = hasVirtualAvatar(wxid)
                    if (avatarScopeEnabled(wxid, surface, virtualAvatar) &&
                        isAvatarConfigured(wxid, virtualAvatar)
                    ) {
                        CustomFriendAvatarStore.loadBitmap(context.hostContext(), wxid)?.let {
                            bitmap ->
                            val radius = (chain.args.getOrNull(2) as? Number)?.toFloat()
                            image.setImageBitmap(processBitmap(bitmap, radius))
                        }
                    }
                    result
                },
            )
        val constructor =
            hook(
                avatarMembers.workerConstructor,
                Hooker { chain ->
                    val requestedWxid = chain.args.getOrNull(1) as? String
                    val args = chain.args.toTypedArray()
                    if (requestedWxid != null && !conversationGroupHasCustomAvatar(requestedWxid)) {
                        automaticGroupAvatarSource(requestedWxid)?.let { args[1] = it }
                    }
                    val result = chain.proceed(args)
                    // Constructor proceed returns null; the receiver is the newly initialized
                    // object.
                    val drawable = chain.thisObject
                    if (!requestedWxid.isNullOrBlank() && drawable != null) {
                        bindDrawable(
                            drawable,
                            requestedWxid,
                            AvatarSurfaceClassifier.forObject(chain.args.getOrNull(0)),
                            effectiveRadiusFactor(args, 2, 5)
                        )
                    }
                    result
                },
            )
        val modify =
            avatarMembers.workerModify?.let { method ->
                hook(
                    method,
                    Hooker { chain ->
                        val target = chain.args.getOrNull(0) ?: return@Hooker chain.proceed()
                        val requestedWxid =
                            chain.args.getOrNull(2) as? String ?: return@Hooker chain.proceed()
                        val args = chain.args.toTypedArray()
                        if (!conversationGroupHasCustomAvatar(requestedWxid)) {
                            automaticGroupAvatarSource(requestedWxid)?.let { args[2] = it }
                        }
                        val result = chain.proceed(args)
                        if (requestedWxid.isNotBlank()) {
                            bindDrawable(
                                target,
                                requestedWxid,
                                AvatarSurfaceClassifier.forObject(chain.args.getOrNull(1)),
                                effectiveRadiusFactor(args, 3, 6)
                            )
                        }
                        result
                    },
                )
            } ?: true
        val draw =
            hook(
                avatarMembers.draw,
                Hooker { chain ->
                    if (destroyed || !avatarSettings.enabled) return@Hooker chain.proceed()
                    val drawable = chain.thisObject as? Drawable ?: return@Hooker chain.proceed()
                    val binding = drawableBindings[drawable] ?: return@Hooker chain.proceed()
                    if (!avatarScopeEnabled(binding.wxid, binding.surface, binding.hasVirtualAvatar)) {
                        return@Hooker chain.proceed()
                    }
                    if (!isAvatarConfigured(binding.wxid, binding.hasVirtualAvatar)) {
                        return@Hooker chain.proceed()
                    }
                    val bitmap =
                        CustomFriendAvatarStore.cachedBitmap(context.hostContext(), binding.wxid)
                    if (bitmap == null) {
                        requestAvatarLoad(drawable, binding)
                        return@Hooker chain.proceed()
                    }
                    val canvas = chain.args.getOrNull(0) as? Canvas ?: return@Hooker chain.proceed()
                    val drawState = DRAW_STATE.get() ?: return@Hooker chain.proceed()
                    drawState.bounds.set(drawable.bounds)
                    val settings = avatarSettings
                    val radiusFactor = if (settings.roundAvatarEnabled) settings.roundRadiusFactor
                        else binding.nativeRadiusFactor?.takeIf { it > 0f }?.coerceAtMost(MAX_RADIUS_FACTOR)
                            ?: NATIVE_RADIUS_FACTOR
                    val radius = minOf(drawState.bounds.width(), drawState.bounds.height()) * radiusFactor
                    drawState.path.reset()
                    drawState.path.addRoundRect(
                        drawState.bounds, radius, radius, Path.Direction.CW
                    )
                    val save = canvas.save()
                    canvas.clipPath(drawState.path)
                    canvas.drawBitmap(bitmap, null, drawState.bounds, DRAW_PAINT)
                    canvas.restoreToCount(save)
                    null
                },
            )
        if (legacy && constructor && modify && draw) installed = true
        installConversationMenu()
        return installed
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        ConversationMenuExtensionRegistry.unregister(conversationMenuExtension)
        avatarPrefs.unregisterOnSharedPreferenceChangeListener(avatarSettingsListener)
        roundAvatarPrefs.unregisterOnSharedPreferenceChangeListener(roundAvatarSettingsListener)
        runCatching { avatarStoreListenerToken.close() }
        pendingAvatarMainTasks.forEach(mainHandler::removeCallbacks)
        pendingAvatarMainTasks.clear()
        avatarLoadExecutor.shutdownNow()
        avatarLoadCoordinator.clear()
        drawableBindings.clear()
        conversationBindings.clear()
        conversationGroupAvatarCache.clear()
    }

    private fun installConversationMenu() {
        ConversationMenuExtensionRegistry.register(conversationMenuExtension)
        val method = ConversationMenuLocator.menuCreateMethod(context, logger) ?: return
        hook(
            method,
            Hooker { chain ->
                val result = chain.proceed()
                if (!CustomFriendAvatarSettings.conversationMenuEnabled(context.hostContext()))
                    return@Hooker result
                val menu = chain.args.getOrNull(0) as? ContextMenu ?: return@Hooker result
                val target = resolveAvatarTarget(chain.thisObject) ?: return@Hooker result
                menu.removeItem(CONVERSATION_MENU_ID)
                val groupId = runCatching { menu.getItem(0).groupId }.getOrDefault(0)
                val item = menu.add(groupId, CONVERSATION_MENU_ID, 0, MENU_TITLE)
                moveMenuItemToFront(menu, item)
                val activity = resolveActivity(chain.thisObject) ?: return@Hooker result
                conversationBindings[item] = ConversationBinding(WeakReference(activity), target)
                installConversationClick(chain.thisObject)
                result
            },
        )
    }

    private fun installConversationClick(listener: Any?) {
        listener ?: return
        val method =
            KavaReflector.declaredFields(listener.javaClass)
                .asSequence()
                .filterNot { Modifier.isStatic(it.modifiers) }
                .mapNotNull { field -> KavaReflector.readField(field, listener) }
                .mapNotNull { callback ->
                    KavaReflector.findMethod(
                        callback.javaClass,
                        "onMMMenuItemSelected",
                        MenuItem::class.java,
                        Integer.TYPE,
                    )
                }
                .firstOrNull {
                    it.returnType == Void.TYPE &&
                        it.declaringClass.name.startsWith("com.tencent.mm.ui.conversation.")
                } ?: return
        hook(
            method,
            Hooker { chain ->
                val clicked = chain.args.getOrNull(0) as? MenuItem ?: return@Hooker chain.proceed()
                if (clicked.itemId != CONVERSATION_MENU_ID) return@Hooker chain.proceed()
                val target = conversationBindings.remove(clicked) ?: return@Hooker chain.proceed()
                val activity = target.activity.get() ?: return@Hooker chain.proceed()
                showAvatarActions(activity, target.wxid)
                null
            },
        )
    }

    private fun showAvatarActions(activity: Activity?, wxid: String) {
        if (activity == null || !CustomFriendAvatarSettings.enabled(context.hostContext())) return
        val hasAvatar = CustomFriendAvatarStore.hasAvatar(context.hostContext(), wxid)
        val summary =
            runCatching { WeChatApis.contacts()?.getContact(wxid)?.displayName() }
                .getOrNull()
                .orEmpty()
                .ifBlank { wxid }
        VoiceForwardMiuixDialog.showChoices(
            activity = activity,
            title = MENU_TITLE,
            summary = summary,
            choices =
                buildList {
                    add("设置或更换头像" to "从系统相册或文件中选择图片")
                    if (hasAvatar) add("恢复微信头像" to "移除本地自定义头像")
                },
            onSelected = { index ->
                if (index == 0) {
                    CustomFriendAvatarPicker.launch(activity, wxid) { success ->
                        toast(activity, if (success) "自定义头像已保存" else "头像设置失败")
                    }
                } else if (hasAvatar) {
                    CustomFriendAvatarStore.remove(context.hostContext(), wxid)
                    toast(activity, "已恢复微信头像")
                }
            },
            onDismiss = {},
        )
    }

    private fun resolveAvatarTarget(receiver: Any?): String? {
        receiver ?: return null
        val contacts = WeChatApis.contacts() ?: return null
        return KavaReflector.declaredFields(receiver.javaClass)
            .asSequence()
            .filter { it.type == String::class.java }
            .mapNotNull { KavaReflector.readField(it, receiver) as? String }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { value ->
                runCatching {
                        contacts.isFriend(value) ||
                            (contacts.isGroup(value) &&
                                contacts.getContact(value)?.isGroup() == true)
                    }
                    .getOrDefault(false)
            }
    }

    private fun isAvatarTarget(talker: String): Boolean {
        val wxId = talker.trim().takeIf { it.isNotEmpty() } ?: return false
        val contacts = WeChatApis.contacts() ?: return false
        return runCatching {
                contacts.isFriend(wxId) ||
                    (contacts.isGroup(wxId) && contacts.getContact(wxId)?.isGroup() == true)
            }
            .getOrDefault(false)
    }

    private fun resolveActivity(receiver: Any?): Activity? {
        (KavaReflector.invokeMethod(receiver, "getActivity") as? Activity)?.let {
            return it
        }
        (KavaReflector.invokeMethod(receiver, "getContext") as? Activity)?.let {
            return it
        }
        var current: Any? = receiver
        repeat(4) {
            if (current is Activity) return current
            if (current is ContextWrapper) {
                current = current.baseContext
                return@repeat
            }
            val values =
                hierarchyFields(current?.javaClass).mapNotNull {
                    KavaReflector.readField(it, current)
                }
            current = values.firstOrNull { it is Activity || it is ContextWrapper }
        }
        return null
    }

    private fun hook(member: Executable, callback: Hooker): Boolean {
        if (
            member is Method &&
                (Modifier.isAbstract(member.modifiers) || member.declaringClass.isInterface)
        )
            return false
        if (!hookedMembers.add(member)) return true
        return runCatching {
                HookRegistry.get().hook(member, callback)
                true
            }
            .getOrElse {
                hookedMembers.remove(member)
                logger("自定义头像 Hook 安装失败: $member", it)
                false
            }
    }

    private fun moveMenuItemToFront(menu: ContextMenu, item: MenuItem) {
        var current: Class<*>? = menu.javaClass
        while (current != null && current != Any::class.java) {
            for (field in KavaReflector.declaredFields(current)) {
                if (!java.util.List::class.java.isAssignableFrom(field.type)) continue
                val items = KavaReflector.readField(field, menu) as? MutableList<Any?> ?: continue
                val index =
                    items.indexOfFirst { it === item || (it as? MenuItem)?.itemId == item.itemId }
                if (index > 0) runCatching { items.add(0, items.removeAt(index)) }
                if (index >= 0) return
            }
            current = current.superclass
        }
    }

    private fun avatarScopeEnabled(
        wxid: String,
        surface: AvatarSurface,
        hasVirtualAvatar: Boolean
    ): Boolean {
        val settings = avatarSettings
        if (!settings.enabled) return false
        if (ConversationGroupRuntime.isVirtualTalker(wxid)) return hasVirtualAvatar
        return settings.scopeEnabled(surface)
    }

    private fun hasVirtualAvatar(wxid: String): Boolean {
        return ConversationGroupRuntime.isVirtualTalker(wxid) &&
            conversationGroupAvatar(wxid)?.avatarPath?.isNotBlank() == true
    }

    private fun isAvatarConfigured(wxid: String, hasVirtualAvatar: Boolean): Boolean {
        return if (ConversationGroupRuntime.isVirtualTalker(wxid)) {
            hasVirtualAvatar
        } else {
            configuredAvatarIds.contains(wxid)
        }
    }

    private fun bindDrawable(
        target: Any,
        wxid: String,
        surface: AvatarSurface,
        nativeRadiusFactor: Float?
    ) {
        if (destroyed) return
        val binding = DrawableAvatarBinding(
            wxid = wxid,
            surface = surface,
            nativeRadiusFactor = nativeRadiusFactor,
            hasVirtualAvatar = hasVirtualAvatar(wxid),
            revision = CustomFriendAvatarStore.revision(context.hostContext(), wxid),
            token = avatarBindingRevision.incrementAndGet()
        )
        (target as? Drawable)?.let(avatarLoadCoordinator::cancelTarget)
        drawableBindings[target] = binding
        (target as? Drawable)?.let { requestAvatarLoad(it, binding) }
    }

    private fun requestAvatarLoad(drawable: Drawable, binding: DrawableAvatarBinding) {
        if (destroyed || !avatarScopeEnabled(binding.wxid, binding.surface, binding.hasVirtualAvatar) ||
            !isAvatarConfigured(binding.wxid, binding.hasVirtualAvatar)
        ) return
        val context = context.hostContext()
        val existing = CustomFriendAvatarStore.cachedBitmap(context, binding.wxid)
        if (existing != null) {
            avatarLoadCoordinator.cancelTarget(drawable)
            if (!destroyed &&
                drawableBindings[drawable] === binding &&
                avatarScopeEnabled(binding.wxid, binding.surface, binding.hasVirtualAvatar) &&
                isAvatarConfigured(binding.wxid, binding.hasVirtualAvatar)
            ) {
                drawable.invalidateSelf()
            }
            return
        }
        val revision = CustomFriendAvatarStore.revision(context, binding.wxid)
        val loadKey = "${context.packageName}\n${binding.wxid}\n$revision"
        val shouldLoad = avatarLoadCoordinator.request(
            key = loadKey,
            target = drawable,
            bindingToken = binding.token,
            isCurrent = { target, token ->
                val current = drawableBindings[target]
                !destroyed &&
                    current != null &&
                    current.token == token &&
                    current.wxid == binding.wxid &&
                    avatarScopeEnabled(binding.wxid, binding.surface, binding.hasVirtualAvatar) &&
                    isAvatarConfigured(binding.wxid, binding.hasVirtualAvatar) &&
                    CustomFriendAvatarStore.revision(context, binding.wxid) == revision
            },
            invalidate = Drawable::invalidateSelf,
            retry = { target, token ->
                val current = drawableBindings[target]
                if (current != null && current.token == token) requestAvatarLoad(target, current)
            }
        )
        if (!shouldLoad) return
        try {
            avatarLoadExecutor.execute {
                val loaded = runCatching {
                    CustomFriendAvatarStore.loadBitmap(context, binding.wxid)
                }.getOrNull()
                val succeeded = loaded != null &&
                    CustomFriendAvatarStore.revision(context, binding.wxid) == revision
                avatarLoadCoordinator.complete(loadKey, succeeded).forEach(::postAvatarTask)
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            avatarLoadCoordinator.complete(loadKey, succeeded = false).forEach(::postAvatarTask)
        }
    }

    private fun onAvatarChanged(wxid: String) {
        if (destroyed) return
        // The store revision is process-wide, so cancel stale waiters for every key.
        avatarLoadCoordinator.clear()
        conversationGroupAvatarCache.remove(wxid)
        configuredAvatarIds = CustomFriendAvatarStore.configuredFriends(context.hostContext())
        val targets = synchronized(drawableBindings) {
            drawableBindings.entries
                .filter { it.value.wxid == wxid }
                .mapNotNull { entry -> (entry.key as? Drawable)?.let { it to entry.value } }
        }
        targets.forEach { (drawable, binding) ->
            if (isAvatarConfigured(wxid, binding.hasVirtualAvatar)) requestAvatarLoad(drawable, binding)
            else drawable.invalidateSelf()
        }
        ChatRowRefresh.request()
    }

    private fun refreshAvatarSettings() {
        if (destroyed) return
        avatarSettings = readAvatarSettings()
        configuredAvatarIds = CustomFriendAvatarStore.configuredFriends(context.hostContext())
        if (!avatarSettings.enabled) avatarLoadCoordinator.clear()
        if (!avatarSettings.enabled || !avatarSettings.notificationsEnabled) {
            NotificationAvatarBitmapCache.clearOwner("custom_friend_avatar_notification")
        }
        val targets = synchronized(drawableBindings) {
            drawableBindings.entries.mapNotNull { entry ->
                (entry.key as? Drawable)?.let { it to entry.value }
            }
        }
        targets.forEach { (drawable, binding) ->
            if (avatarScopeEnabled(binding.wxid, binding.surface, binding.hasVirtualAvatar) &&
                isAvatarConfigured(binding.wxid, binding.hasVirtualAvatar)
            ) {
                requestAvatarLoad(drawable, binding)
            } else {
                avatarLoadCoordinator.cancelTarget(drawable)
            }
        }
        postAvatarTask(ChatRowRefresh::request)
    }

    private fun postAvatarTask(action: () -> Unit) {
        lateinit var task: Runnable
        task = Runnable {
            pendingAvatarMainTasks.remove(task)
            if (!destroyed) action()
        }
        pendingAvatarMainTasks += task
        mainHandler.post(task)
    }

    private fun readAvatarSettings(): AvatarSettingsSnapshot {
        val prefs = avatarPrefs
        val roundPrefs = roundAvatarPrefs
        return AvatarSettingsSnapshot(
            enabled = prefs.getBoolean(CustomFriendAvatarSettings.KEY_ENABLE, CustomFriendAvatarSettings.DEFAULT_ENABLE),
            chat = prefs.getBoolean(CustomFriendAvatarSettings.KEY_CHAT, CustomFriendAvatarSettings.DEFAULT_SCOPE),
            conversation = prefs.getBoolean(CustomFriendAvatarSettings.KEY_CONVERSATION, CustomFriendAvatarSettings.DEFAULT_SCOPE),
            contacts = prefs.getBoolean(CustomFriendAvatarSettings.KEY_CONTACTS, CustomFriendAvatarSettings.DEFAULT_SCOPE),
            profile = prefs.getBoolean(CustomFriendAvatarSettings.KEY_PROFILE, CustomFriendAvatarSettings.DEFAULT_SCOPE),
            moments = prefs.getBoolean(CustomFriendAvatarSettings.KEY_MOMENTS, CustomFriendAvatarSettings.DEFAULT_SCOPE),
            other = prefs.getBoolean(CustomFriendAvatarSettings.KEY_OTHER_UI, CustomFriendAvatarSettings.DEFAULT_SCOPE),
            notificationsEnabled = prefs.getBoolean(CustomFriendAvatarSettings.KEY_NOTIFICATIONS, CustomFriendAvatarSettings.DEFAULT_SCOPE),
            roundAvatarEnabled = roundPrefs.getBoolean(
                h.Hchat.hooks.items.roundavatar.RoundAvatarSettings.KEY_ENABLE,
                h.Hchat.hooks.items.roundavatar.RoundAvatarSettings.DEFAULT_ENABLE
            ),
            roundRadiusFactor = h.Hchat.hooks.items.roundavatar.RoundAvatarSettings.normalizeRadiusFactor(
                roundPrefs.getFloat(
                    h.Hchat.hooks.items.roundavatar.RoundAvatarSettings.KEY_RADIUS_FACTOR,
                    h.Hchat.hooks.items.roundavatar.RoundAvatarSettings.DEFAULT_RADIUS_FACTOR
                )
            )
        )
    }

    private fun automaticGroupAvatarSource(wxid: String): String? {
        return ConversationGroupRuntime.automaticAvatarSource(wxid)
    }

    private fun conversationGroupHasCustomAvatar(talker: String): Boolean {
        if (!ConversationGroupRuntime.isVirtualTalker(talker)) return false
        return conversationGroupAvatar(talker)?.avatarPath?.isNotBlank() == true
    }

    private fun processBitmap(bitmap: Bitmap, nativeRadiusFactor: Float?): Bitmap {
        val settings = avatarSettings
        val factor = if (settings.roundAvatarEnabled) settings.roundRadiusFactor else {
                nativeRadiusFactor?.takeIf { it > 0f }?.coerceAtMost(MAX_RADIUS_FACTOR)
                    ?: NATIVE_RADIUS_FACTOR
            }
        return CustomFriendAvatarBitmapProcessor.round(bitmap, factor)
    }

    private fun conversationGroupAvatar(
        talker: String
    ): h.Hchat.hooks.items.conversationgroup.ConversationGroup? {
        val now = System.currentTimeMillis()
        conversationGroupAvatarCache[talker]
            ?.takeIf { now - it.loadedAt <= GROUP_CACHE_MS }
            ?.let {
                return it.group
            }
        val group =
            ConversationGroupStore.load(context.hostContext()).firstOrNull {
                ConversationGroupRuntime.virtualTalker(it.id) == talker
            }
        conversationGroupAvatarCache[talker] = CachedGroupAvatar(now, group)
        return group
    }

    private fun replaceNotificationLargeIcon(notification: Notification, bitmap: Bitmap) {
        @Suppress("DEPRECATION") runCatching { notification.largeIcon = bitmap }
        val icon = Icon.createWithBitmap(bitmap)
        KavaReflector.writeField(notification, "mLargeIcon", icon)
        if (notification.extras == null) notification.extras = Bundle()
        notification.extras.putParcelable(Notification.EXTRA_LARGE_ICON, icon)
    }

    private fun effectiveRadiusFactor(args: Array<Any?>, radiusIndex: Int, maskIndex: Int): Float {
        val mask = (args.getOrNull(maskIndex) as? Number)?.toInt() ?: 0
        if ((mask and DEFAULT_RADIUS_MASK) != 0) return NATIVE_RADIUS_FACTOR
        return (args.getOrNull(radiusIndex) as? Number)
            ?.toFloat()
            ?.takeIf { it > 0f }
            ?.coerceAtMost(MAX_RADIUS_FACTOR) ?: NATIVE_RADIUS_FACTOR
    }

    private fun toast(activity: Activity?, text: String) {
        activity ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        val AVATAR_SETTING_KEYS = setOf(
            CustomFriendAvatarSettings.KEY_ENABLE,
            CustomFriendAvatarSettings.KEY_CHAT,
            CustomFriendAvatarSettings.KEY_CONVERSATION,
            CustomFriendAvatarSettings.KEY_CONTACTS,
            CustomFriendAvatarSettings.KEY_PROFILE,
            CustomFriendAvatarSettings.KEY_MOMENTS,
            CustomFriendAvatarSettings.KEY_OTHER_UI,
            CustomFriendAvatarSettings.KEY_NOTIFICATIONS,
            CustomFriendAvatarSettings.KEY_CONFIGURED_FRIENDS
        )
        const val CONVERSATION_MENU_ID = 0x7A1001
        const val MENU_TITLE = "设置自定义头像"
        const val CACHE_PREFS = "Hchat_custom_friend_avatar_method_cache"
        const val CACHE_SCHEMA = "custom_friend_avatar_v1"
        const val CACHE_LEGACY = "legacy_load"
        const val CACHE_WORKER = "worker_constructor"
        const val CACHE_MODIFY = "worker_modify"
        const val CACHE_DRAW = "worker_draw"
        const val CACHE_DESKTOP_SHORTCUT = "desktop_shortcut"
        const val LEGACY_TAG = "MicroMsg.AvatarDrawable"
        const val WORKER_SCOPE = "workerScope"
        const val USERNAME = "username"
        val DESKTOP_SHORTCUT_ANCHORS =
            arrayOf(
                "MicroMsg.ShortcutManager",
                "getScaledBitmap fail, bmp is null",
                "com.tencent.qlauncher.extra.EXTRA_PUSH_ITEM_UNIQUE_ID",
            )
        const val DEFAULT_RADIUS_MASK = 1 shl 2
        const val NATIVE_RADIUS_FACTOR = 0.1f
        const val MAX_RADIUS_FACTOR = 0.5f
        const val GROUP_CACHE_MS = 1000L
        const val GROUP_CACHE_MAX_ENTRIES = 256
        val DRAW_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val DRAW_STATE = ThreadLocal.withInitial { AvatarDrawState() }
    }

    private data class AvatarMembers(
        val legacyLoad: Method,
        val workerConstructor: Constructor<*>,
        val workerModify: Method?,
        val draw: Method,
    )

    private data class CachedGroupAvatar(
        val loadedAt: Long,
        val group: h.Hchat.hooks.items.conversationgroup.ConversationGroup?,
    )

    private data class AvatarSettingsSnapshot(
        val enabled: Boolean,
        val chat: Boolean,
        val conversation: Boolean,
        val contacts: Boolean,
        val profile: Boolean,
        val moments: Boolean,
        val other: Boolean,
        val notificationsEnabled: Boolean,
        val roundAvatarEnabled: Boolean,
        val roundRadiusFactor: Float
    ) {
        fun scopeEnabled(surface: AvatarSurface): Boolean = when (surface) {
            AvatarSurface.CHAT -> chat
            AvatarSurface.CONVERSATION -> conversation
            AvatarSurface.CONTACTS -> contacts
            AvatarSurface.PROFILE -> profile
            AvatarSurface.MOMENTS -> moments
            AvatarSurface.OTHER -> other
        }
    }

    private data class DrawableAvatarBinding(
        val wxid: String,
        val surface: AvatarSurface,
        val nativeRadiusFactor: Float?,
        val hasVirtualAvatar: Boolean,
        val revision: Long,
        val token: Long
    )

    private class AvatarDrawState {
        val bounds = RectF()
        val path = Path()
    }

    private data class ConversationBinding(val activity: WeakReference<Activity>, val wxid: String)

    private fun locateAvatarMembers(): AvatarMembers? {
        val prefs = DexMethodCache.prefs(context.hostContext(), CACHE_PREFS)
        val key =
            DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader()) +
                "|" +
                CACHE_SCHEMA
        val legacy =
            DexMethodCache.load(prefs, key, context.hostClassLoader(), CACHE_LEGACY)
                ?.takeIf(::isLegacy)
        val constructor =
            DexMethodCache.loadConstructor(prefs, key, context.hostClassLoader(), CACHE_WORKER)
                ?.takeIf(::isWorkerConstructor)
        if (legacy != null && constructor != null) {
            val modify = DexMethodCache.load(prefs, key, context.hostClassLoader(), CACHE_MODIFY)
            val draw = DexMethodCache.load(prefs, key, context.hostClassLoader(), CACHE_DRAW)
            if (draw != null && draw.declaringClass == constructor.declaringClass) {
                return AvatarMembers(legacy, constructor, modify, draw)
            }
        }
        val legacyCandidates =
            findMembersByStrings(LEGACY_TAG)
                .filterIsInstance<Method>()
                .filter(::isLegacy)
                .distinctBy { it.toGenericString() }
        val workerCandidates = findMembersByStrings(WORKER_SCOPE, USERNAME)
        val constructors =
            workerCandidates
                .filterIsInstance<Constructor<*>>()
                .filter(::isWorkerConstructor)
                .distinctBy { it.toGenericString() }
        val load = legacyCandidates.singleOrNull() ?: return null
        val ctor = constructors.singleOrNull() ?: return null
        val modify =
            workerCandidates.filterIsInstance<Method>().singleOrNull {
                isWorkerModify(it, ctor.declaringClass)
            }
        val draw =
            KavaReflector.findDeclaredMethod(ctor.declaringClass, "draw", Canvas::class.java)
                ?.takeIf { it.returnType == Void.TYPE } ?: return null
        DexMethodCache.save(prefs, key, CACHE_LEGACY, load)
        DexMethodCache.saveConstructor(prefs, key, CACHE_WORKER, ctor)
        if (modify != null) DexMethodCache.save(prefs, key, CACHE_MODIFY, modify)
        DexMethodCache.save(prefs, key, CACHE_DRAW, draw)
        return AvatarMembers(load, ctor, modify, draw)
    }

    private fun locateDesktopShortcutMethod(): Method? {
        val prefs = DexMethodCache.prefs(context.hostContext(), CACHE_PREFS)
        val key =
            DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader()) +
                "|" +
                CACHE_SCHEMA
        DexMethodCache.load(prefs, key, context.hostClassLoader(), CACHE_DESKTOP_SHORTCUT)
            ?.takeIf(::isDesktopShortcutMethod)
            ?.let {
                return it
            }
        val method =
            findMembersByStrings(*DESKTOP_SHORTCUT_ANCHORS)
                .filterIsInstance<Method>()
                .filter(::isDesktopShortcutMethod)
                .distinctBy { it.toGenericString() }
                .singleOrNull()
        if (method == null) {
            DexMethodCache.clear(prefs, key, CACHE_DESKTOP_SHORTCUT)
            logger("未定位桌面快捷方式头像入口", null)
            return null
        }
        DexMethodCache.save(prefs, key, CACHE_DESKTOP_SHORTCUT, method)
        return method
    }

    private fun findMembersByStrings(vararg strings: String): List<Member> {
        return runCatching {
                context
                    .dexKitBridge()
                    .findMethod(
                        FindMethod().apply {
                            matcher(MethodMatcher().apply { usingEqStrings(*strings) })
                        }
                    )
                    .mapNotNull { data ->
                        runCatching {
                                if (data.isConstructor)
                                    data.getConstructorInstance(context.hostClassLoader())
                                else data.getMethodInstance(context.hostClassLoader())
                            }
                            .getOrNull()
                    }
            }
            .getOrElse {
                logger("定位头像入口失败", it)
                emptyList()
            }
    }

    private fun isLegacy(method: Method): Boolean {
        return Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(
                arrayOf(
                    ImageView::class.java,
                    String::class.java,
                    java.lang.Float.TYPE,
                    java.lang.Boolean.TYPE,
                )
            )
    }

    private fun isWorkerConstructor(constructor: Constructor<*>): Boolean {
        val types = constructor.parameterTypes
        return types.size == 7 &&
            types[0].name == "com.tencent.mm.sdk.coroutines.LifecycleScope" &&
            types[1] == String::class.java &&
            types[2] == java.lang.Float.TYPE &&
            types.any { it == Integer.TYPE }
    }

    private fun isWorkerModify(method: Method, owner: Class<*>): Boolean {
        val types = method.parameterTypes
        return Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.declaringClass == owner &&
            types.size == 8 &&
            types[0] == owner &&
            types[1].name == "com.tencent.mm.sdk.coroutines.LifecycleScope" &&
            types[2] == String::class.java &&
            types[3] == java.lang.Float.TYPE &&
            types.any { it == Integer.TYPE }
    }

    private fun isDesktopShortcutMethod(method: Method): Boolean {
        return Modifier.isStatic(method.modifiers) &&
            method.returnType == Intent::class.java &&
            method.parameterTypes.contentEquals(
                arrayOf(
                    Context::class.java,
                    String::class.java,
                    java.lang.Boolean.TYPE,
                    String::class.java,
                )
            )
    }

    private fun hierarchyFields(start: Class<*>?): Sequence<java.lang.reflect.Field> = sequence {
        var current = start
        while (current != null && current != Any::class.java) {
            yieldAll(KavaReflector.declaredFields(current))
            current = current.superclass
        }
    }
}

private object AvatarSurfaceClassifier {
    fun forView(view: View): AvatarSurface {
        val names = ArrayList<String>()
        names += view.javaClass.name
        var parent = view.parent
        repeat(8) {
            if (parent == null) return@repeat
            names += parent.javaClass.name
            parent = parent.parent
        }
        names += view.context.javaClass.name
        return classify(names)
    }

    fun forObject(value: Any?): AvatarSurface {
        value ?: return AvatarSurface.OTHER
        val names = ArrayList<String>()
        names += value.javaClass.name
        var current: Class<*>? = value.javaClass
        var depth = 0
        while (current != null && current != Any::class.java && depth < 3) {
            KavaReflector.declaredFields(current).forEach { field ->
                names += field.type.name
                KavaReflector.readField(field, value)?.javaClass?.name?.let(names::add)
            }
            current = current.superclass
            depth++
        }
        return classify(names)
    }

    private fun classify(names: List<String>): AvatarSurface {
        val text = names.joinToString(" ").lowercase()
        return when {
            "chatting" in text -> AvatarSurface.CHAT
            "conversation" in text -> AvatarSurface.CONVERSATION
            "addressui" in text || "contact" in text -> AvatarSurface.CONTACTS
            "profile" in text -> AvatarSurface.PROFILE
            "sns" in text || "moments" in text -> AvatarSurface.MOMENTS
            else -> AvatarSurface.OTHER
        }
    }
}

private object CustomFriendAvatarBitmapProcessor {
    private data class Entry(val factor: Float, val bitmap: java.lang.ref.WeakReference<Bitmap>)

    private val cache = WeakHashMap<Bitmap, Entry>()

    @Synchronized
    fun round(source: Bitmap, factor: Float): Bitmap {
        cache[source]
            ?.takeIf { it.factor == factor }
            ?.bitmap
            ?.get()
            ?.takeUnless { it.isRecycled }
            ?.let {
                return it
            }
        return runCatching {
                val output =
                    Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        shader =
                            android.graphics.BitmapShader(
                                source,
                                android.graphics.Shader.TileMode.CLAMP,
                                android.graphics.Shader.TileMode.CLAMP,
                            )
                    }
                val radius = minOf(source.width, source.height) * factor
                Canvas(output)
                    .drawRoundRect(
                        0f,
                        0f,
                        source.width.toFloat(),
                        source.height.toFloat(),
                        radius,
                        radius,
                        paint,
                    )
                cache[source] = Entry(factor, java.lang.ref.WeakReference(output))
                output
            }
            .getOrDefault(source)
    }
}
