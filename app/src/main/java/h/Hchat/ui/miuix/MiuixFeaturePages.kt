package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.autooriginal.AutoOriginalImageSettings
import h.Hchat.hooks.items.autovieworiginal.AutoViewOriginalSettings
import h.Hchat.hooks.items.callmedialimit.CallMediaLimitSettings
import h.Hchat.hooks.items.editmsg.EditMessageSettings
import h.Hchat.hooks.items.emojisave.EmojiSaveSettings
import h.Hchat.hooks.items.emojilimit.EmojiCollectionLimitFeature
import h.Hchat.hooks.items.forwardlimit.RemoveForwardLimitSettings
import h.Hchat.hooks.items.grouplabel.QuickGroupChatLabelSettings
import h.Hchat.hooks.items.hotupdate.DisableHotUpdateSettings
import h.Hchat.hooks.items.inputhint.InputHintSettings
import h.Hchat.hooks.items.inputhint.InputHintStats
import h.Hchat.hooks.items.inputhint.OutgoingMessageStatsRepository
import h.Hchat.hooks.items.messageaffix.MessageAffixSettings
import h.Hchat.hooks.items.messageforward.MessageForwardSettings
import h.Hchat.hooks.items.hchatextra.HchatExtraSettings
import h.Hchat.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibSettings
import h.Hchat.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsSettings
import h.Hchat.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsSettings
import h.Hchat.hooks.items.moments.OriginalMomentsUploadSettings
import h.Hchat.hooks.items.moments.RemoveMomentsAdsSettings
import h.Hchat.hooks.items.multirecall.MultiRecallSettings
import h.Hchat.hooks.items.patblock.PatBlockSettings
import h.Hchat.hooks.items.profileid.ProfileIdSettings
import h.Hchat.hooks.items.quickcontactedit.QuickContactEditSettings
import h.Hchat.hooks.items.quickmoments.QuickMomentsSettings
import h.Hchat.hooks.items.quickterminate.QuickTerminateSettings
import h.Hchat.hooks.items.quoteclear.QuoteDeleteClearSettings
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskContentItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import h.Hchat.hooks.items.selectedmessages.SelectedMessagesRuntimeCoordinator
import h.Hchat.hooks.items.selectedmessages.SelectedMessagesSettings
import h.Hchat.hooks.items.statuslimit.StatusTextLimitSettings
import h.Hchat.hooks.items.shortvideo.FinderMediaDownloadSettings
import h.Hchat.hooks.items.tablet.WeChatTabletSettings
import h.Hchat.hooks.items.typingreport.TypingReportBlockSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.preferences.ConfigStore
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import kotlinx.coroutines.launch


@Composable
internal fun SelectedMessagesMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, SelectedMessagesSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<SelectedMessagesRoute>(SelectedMessagesRoute.Main) }
    var draft by remember {
        mutableStateOf(
            ScheduledTaskSettings.newDraft().copy(
                type = ScheduledTaskSettings.TYPE_IMAGE,
                intervalSeconds = SelectedMessagesSettings.sendIntervalSeconds(context),
                mediaIntervalSeconds = SelectedMessagesSettings.sendIntervalSeconds(context)
            )
        )
    }
    val contentSession = remember { ScheduledContentSession(emptyList(), false) }
    var channel by remember { mutableStateOf(SelectedMessagesRuntimeCoordinator.CHANNEL_MODULE) }
    val mainListState = rememberLazyListState()
    val editorListState = rememberLazyListState()

    fun validationError(): String? {
        if (contentSession.entries.any { it.item.value.isBlank() }) return "请完善或移除未完成的发送内容"
        return SelectedMessagesRuntimeCoordinator.validationError(
            channel,
            scheduledTaskEditableItems(draft)
        )
    }

    fun openContactPicker() {
        validationError()?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            return
        }
        route = SelectedMessagesRoute.ContactPicker
    }

    fun startSend() {
        val error = validationError() ?: if (draft.targetIds.isEmpty()) "请选择群发对象" else null
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            Toast.makeText(context, "当前页面无法启动群发", Toast.LENGTH_SHORT).show()
            return
        }
        SelectedMessagesRuntimeCoordinator.sendCustom(
            activity = activity,
            channel = channel,
            items = scheduledTaskEditableItems(draft),
            targetIds = draft.targetIds,
            targetIntervalSeconds = draft.intervalSeconds,
            itemIntervalSeconds = draft.mediaIntervalSeconds
        )
    }

    SettingsRouteTransition(
        targetState = route,
        label = "SelectedMessagesRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            SelectedMessagesRoute.Main -> {
                SelectedMessagesMainPage(
                    provider = provider,
                    sp = sp,
                    listState = mainListState,
                    onBack = onBack,
                    onCompose = { route = SelectedMessagesRoute.Editor }
                )
            }
            SelectedMessagesRoute.Editor -> {
                SelectedMessagesCustomEditorPage(
                    draft = draft,
                    contentSession = contentSession,
                    channel = channel,
                    listState = editorListState,
                    onBack = { route = SelectedMessagesRoute.Main },
                    onDraftChange = { draft = it },
                    onAddContent = { type -> contentSession.add(type); draft = contentSession.applyTo(draft) },
                    onContentChange = { key, item ->
                        if (contentSession.update(key, item)) draft = contentSession.applyTo(draft)
                    },
                    onMoveContent = { key, offset ->
                        if (contentSession.move(key, offset)) draft = contentSession.applyTo(draft)
                    },
                    onRemoveContent = { key ->
                        if (contentSession.remove(key)) draft = contentSession.applyTo(draft)
                    },
                    onChannelChange = { next ->
                        if (next != channel) {
                            channel = next
                            draft = draft.copy(targetIds = emptyList())
                        }
                    },
                    onPickTargets = ::openContactPicker,
                    onPickFiles = { itemKey, itemType ->
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            ScheduledTaskFilePickerBridge.launch(activity, itemType) { paths ->
                                if (contentSession.setFiles(itemKey, itemType, paths)) draft = contentSession.applyTo(draft)
                            }
                        }
                    },
                    onPickFavorite = { itemKey ->
                        contentSession.find(itemKey)?.item?.let { item ->
                            route = SelectedMessagesRoute.FavoritePicker(
                                FavoritePickerRequest(
                                    title = scheduledTaskPickerTitle(item.type),
                                    existingValue = item.value,
                                    onValue = { value ->
                                        contentSession.find(itemKey)?.takeIf { it.item.type == item.type }?.let { current ->
                                            if (contentSession.update(itemKey, current.item.copy(value = value))) draft = contentSession.applyTo(draft)
                                        }
                                    },
                                    multiSelect = false,
                                    delimiter = "|"
                                )
                            )
                        }
                    },
                    validationError = validationError(),
                    onSend = ::startSend
                )
            }
            SelectedMessagesRoute.ContactPicker -> {
                val official = channel == SelectedMessagesRuntimeCoordinator.CHANNEL_OFFICIAL
                ContactPickerPage(
                    context = context,
                    request = ContactPickerRequest(
                        title = if (official) "选择官方群发好友" else "选择群发对象",
                        mode = if (official) ContactPickerMode.FRIENDS else ContactPickerMode.ALL_CHATS,
                        multiSelect = true,
                        existingValue = formatIds(draft.targetIds),
                        onValue = {},
                        enableLabels = true
                    ),
                    onBack = { route = SelectedMessagesRoute.Editor },
                    onConfirm = { selected ->
                        draft = draft.copy(targetIds = selected.map { it.id })
                        route = SelectedMessagesRoute.Editor
                    }
                )
            }
            is SelectedMessagesRoute.FavoritePicker -> {
                FavoritePickerPage(
                    request = currentRoute.request,
                    onBack = { route = SelectedMessagesRoute.Editor }
                )
            }
        }
    }
}

@Composable
private fun SelectedMessagesMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    listState: LazyListState,
    onBack: () -> Unit,
    onCompose: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    var officialIntervalMinutes by remember {
        mutableStateOf(
            sp.getInt(
                SelectedMessagesSettings.KEY_OFFICIAL_INTERVAL_MINUTES,
                SelectedMessagesSettings.DEFAULT_OFFICIAL_INTERVAL_MINUTES
            ).coerceIn(0, SelectedMessagesSettings.MAX_OFFICIAL_INTERVAL_MINUTES).toString()
        )
    }
    var sendIntervalSeconds by remember {
        mutableStateOf(
            sp.getInt(
                SelectedMessagesSettings.KEY_SEND_INTERVAL_SECONDS,
                SelectedMessagesSettings.DEFAULT_SEND_INTERVAL_SECONDS
            ).coerceIn(0, SelectedMessagesSettings.MAX_SEND_INTERVAL_SECONDS).toString()
        )
    }
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "多选消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SelectedMessagesSettings.KEY_ENABLE,
                        "群发助手",
                        "在多选消息菜单中显示群发助手[H]和定时转发[H]",
                        SelectedMessagesSettings.DEFAULT_ENABLE
                    )
                    SwitchRow(
                        sp,
                        SelectedMessagesSettings.KEY_BACKGROUND_SILENT_SEND,
                        "后台静默发送",
                        "群发时不显示发送进度窗口",
                        SelectedMessagesSettings.DEFAULT_BACKGROUND_SILENT_SEND
                    )
                }
            }
            item { SettingsSectionTitle(text = "发送节奏") }
            item {
                SettingsCard {
                    NumberInputRow(
                        "群发助手间隔延迟",
                        "单位分钟，多个微信原生群发批次之间的等待时间",
                        officialIntervalMinutes
                    ) { value ->
                        officialIntervalMinutes = value
                        value.toIntOrNull()?.takeIf { it in 0..SelectedMessagesSettings.MAX_OFFICIAL_INTERVAL_MINUTES }?.let { parsed ->
                            sp.edit().putInt(
                                SelectedMessagesSettings.KEY_OFFICIAL_INTERVAL_MINUTES,
                                parsed
                            ).apply()
                        }
                    }
                    InsetDivider()
                    NumberInputRow(
                        "群发间隔延迟",
                        "单位秒，群发多条内容或多个目标之间的等待时间",
                        sendIntervalSeconds
                    ) { value ->
                        sendIntervalSeconds = value
                        value.toIntOrNull()?.takeIf { it in 0..SelectedMessagesSettings.MAX_SEND_INTERVAL_SECONDS }?.let { parsed ->
                            sp.edit().putInt(
                                SelectedMessagesSettings.KEY_SEND_INTERVAL_SECONDS,
                                parsed
                            ).apply()
                        }
                    }
                }
            }
            item { SettingsSectionTitle(text = "内容群发") }
            item {
                SettingsCard {
                    ActionRow("自定义群发", "编辑内容并选择模块或微信原生通道") {
                        onCompose()
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedMessagesCustomEditorPage(
    draft: ScheduledTaskItem,
    contentSession: ScheduledContentSession,
    channel: Int,
    listState: LazyListState,
    onBack: () -> Unit,
    onDraftChange: (ScheduledTaskItem) -> Unit,
    onAddContent: (Int) -> Unit,
    onContentChange: (String, ScheduledTaskContentItem) -> Unit,
    onMoveContent: (String, Int) -> Unit,
    onRemoveContent: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onPickTargets: () -> Unit,
    onPickFiles: (String, Int) -> Unit,
    onPickFavorite: (String) -> Unit,
    validationError: String?,
    onSend: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val official = channel == SelectedMessagesRuntimeCoordinator.CHANNEL_OFFICIAL
    val items = scheduledTaskEditableItems(draft)
    var intervalText by remember(draft.id) { mutableStateOf(draft.intervalSeconds.toString()) }
    var mediaIntervalText by remember(draft.id) { mutableStateOf(draft.mediaIntervalSeconds.toString()) }
    val intervalValid = intervalText.toIntOrNull()?.let { it in 0..3600 } == true
    val mediaIntervalValid = mediaIntervalText.toIntOrNull()?.let { it in 0..3600 } == true
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollOffset = with(LocalDensity.current) { 120.dp.roundToPx() }
    LaunchedEffect(contentSession.scrollRequest) {
        val key = contentSession.scrollRequest ?: return@LaunchedEffect
        val index = contentSession.entries.indexOfFirst { it.key == key }
        if (index >= 0) {
            withFrameNanos { }
            listState.animateScrollToItem(3 + index, -scrollOffset)
        }
        contentSession.consumeScrollRequest(key)
    }
    PageScaffold(
        title = "自定义群发",
        largeTitle = "自定义群发",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "发送目标") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "发送通道",
                        summary = selectedMessagesChannelLabel(channel),
                        options = selectedMessagesChannelChoices(),
                        currentValue = channel.toString(),
                        onValueChanged = { value ->
                            onChannelChange(
                                value.toIntOrNull()
                                    ?: SelectedMessagesRuntimeCoordinator.CHANNEL_MODULE
                            )
                        }
                    )
                    InsetDivider()
                    ActionRow(
                        if (official) "选择好友" else "选择聊天",
                        scheduledTaskTargetSummary(draft.targetIds)
                    ) {
                        onPickTargets()
                    }
                }
            }
            scheduledContentSection(
                context = context,
                session = contentSession,
                massSendOnly = official,
                onAdd = onAddContent,
                onChange = onContentChange,
                onCommit = {},
                onMove = onMoveContent,
                onRemove = onRemoveContent,
                onPickFiles = onPickFiles,
                onPickFavorite = onPickFavorite
            )
            if (!official) {
                item { SettingsSectionTitle(text = "发送节奏") }
                item {
                    SettingsCard {
                        NumberInputRow(
                            "聊天间隔",
                            if (intervalValid) "单位秒，多个聊天之间的等待时间" else "请输入 0–3600",
                            intervalText
                        ) {
                            intervalText = it
                            it.toIntOrNull()?.takeIf { value -> value in 0..3600 }?.let { value -> onDraftChange(draft.copy(intervalSeconds = value)) }
                        }
                        if (items.size > 1) {
                            InsetDivider()
                            NumberInputRow(
                                scheduledTaskItemIntervalTitle(),
                                if (mediaIntervalValid) "单位秒，同一聊天连续发送的等待时间" else "请输入 0–3600",
                                mediaIntervalText
                            ) {
                                mediaIntervalText = it
                                it.toIntOrNull()?.takeIf { value -> value in 0..3600 }?.let { value -> onDraftChange(draft.copy(mediaIntervalSeconds = value)) }
                            }
                        }
                    }
                }
            }

            item {
                val error = when {
                    !official && (!intervalValid || (items.size > 1 && !mediaIntervalValid)) -> "请填写有效的发送间隔"
                    validationError != null -> validationError
                    draft.targetIds.isEmpty() -> "请选择群发对象"
                    else -> null
                }
                PrimaryFormAction("开始群发", error, onSend)
            }
        }
    }
}

private fun selectedMessagesChannelChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("模块通道", SelectedMessagesRuntimeCoordinator.CHANNEL_MODULE.toString()),
    PopupChoice("微信原生群发助手", SelectedMessagesRuntimeCoordinator.CHANNEL_OFFICIAL.toString())
)

private fun selectedMessagesChannelLabel(channel: Int): String {
    return if (channel == SelectedMessagesRuntimeCoordinator.CHANNEL_OFFICIAL) {
        "微信原生群发助手"
    } else {
        "模块通道"
    }
}

/** The same storage bindings are used by legacy pages and the composite pages. */
@Composable
internal fun InlineSettingSwitch(context: Context, id: String) {
    when (id) {
        "block_typing_report" -> SwitchRow(remember { HchatStorage.preferences(context, TypingReportBlockSettings.PREFS_NAME) }, TypingReportBlockSettings.KEY_ENABLE, "拦截正在输入上报", "输入文字时不向对方显示正在输入状态", TypingReportBlockSettings.DEFAULT_ENABLE)
        "disable_pat" -> SwitchRow(remember { HchatStorage.preferences(context, PatBlockSettings.PREFS_NAME) }, PatBlockSettings.KEY_ENABLE, "禁止拍一拍", "双击聊天头像时不发送拍一拍", PatBlockSettings.DEFAULT_ENABLE)
        "auto_original_image" -> SwitchRow(remember { HchatStorage.preferences(context, AutoOriginalImageSettings.PREFS_NAME) }, AutoOriginalImageSettings.KEY_ENABLE, "自动勾选原图", "进入聊天图片发送界面时自动选择原图", AutoOriginalImageSettings.DEFAULT_ENABLE)
        "auto_view_original" -> SwitchRow(remember { HchatStorage.preferences(context, AutoViewOriginalSettings.PREFS_NAME) }, AutoViewOriginalSettings.KEY_ENABLE, "自动查看原图", "打开聊天图片或视频时自动查看原图或原视频", AutoViewOriginalSettings.DEFAULT_ENABLE)
        "quote_delete_clear" -> SwitchRow(remember { HchatStorage.preferences(context, QuoteDeleteClearSettings.PREFS_NAME) }, QuoteDeleteClearSettings.KEY_ENABLE, "删除键清引用", "输入框为空且已引用消息时，按输入法删除键直接取消引用", QuoteDeleteClearSettings.DEFAULT_ENABLE)
        "emoji_save" -> SwitchRow(remember { HchatStorage.preferences(context, EmojiSaveSettings.PREFS_NAME) }, EmojiSaveSettings.KEY_ENABLE, "保存表情", "长按聊天表情后显示保存入口", EmojiSaveSettings.DEFAULT_ENABLE)
        "multi_recall" -> SwitchRow(remember { HchatStorage.preferences(context, MultiRecallSettings.PREFS_NAME) }, MultiRecallSettings.KEY_ENABLE, "多选撤回", "在多选消息的分享菜单中显示批量撤回", MultiRecallSettings.DEFAULT_ENABLE)
        "original_moments_upload" -> SwitchRow(remember { HchatStorage.preferences(context, OriginalMomentsUploadSettings.PREFS_NAME) }, OriginalMomentsUploadSettings.KEY_ENABLE, "原图上传", "发布朋友圈图片和视频时尽量跳过微信压缩", OriginalMomentsUploadSettings.DEFAULT_ENABLE)
        "remove_moments_ads" -> SwitchRow(remember { HchatStorage.preferences(context, RemoveMomentsAdsSettings.PREFS_NAME) }, RemoveMomentsAdsSettings.KEY_ENABLE, "去除朋友圈广告", "阻止朋友圈广告信息解析和展示", RemoveMomentsAdsSettings.DEFAULT_ENABLE)
        "wechat_tablet" -> SwitchRow(remember { HchatStorage.preferences(context, WeChatTabletSettings.PREFS_NAME) }, WeChatTabletSettings.KEY_ENABLE, "平板模式", "启动时安装设备识别 Hook；开启后需重启微信并重新登录", WeChatTabletSettings.DEFAULT_ENABLE)
        "disable_hot_update" -> SwitchRow(remember { HchatStorage.preferences(context, DisableHotUpdateSettings.PREFS_NAME) }, DisableHotUpdateSettings.KEY_ENABLE, "屏蔽热更新", "阻止微信加载和应用热更新补丁，重启微信后生效", DisableHotUpdateSettings.DEFAULT_ENABLE)
        "fake_mini_program_base_lib" -> SwitchRow(remember { HchatStorage.preferences(context, FakeMiniProgramBaseLibSettings.PREFS_NAME) }, FakeMiniProgramBaseLibSettings.KEY_ENABLE, "兼容低版本小程序", "主进程即时生效；小程序进程需重启微信后生效", FakeMiniProgramBaseLibSettings.DEFAULT_ENABLE)
        "skip_mini_program_video_ads" -> SwitchRow(remember { HchatStorage.preferences(context, SkipMiniProgramVideoAdsSettings.PREFS_NAME) }, SkipMiniProgramVideoAdsSettings.KEY_ENABLE, "跳过小程序视频广告", "修改后重启微信，使小程序进程重新加载设置", SkipMiniProgramVideoAdsSettings.DEFAULT_ENABLE)
        "skip_global_mini_program_splash_ads" -> SwitchRow(remember { HchatStorage.preferences(context, SkipGlobalMiniProgramSplashAdsSettings.PREFS_NAME) }, SkipGlobalMiniProgramSplashAdsSettings.KEY_ENABLE, "跳过全局小程序开屏广告", "阻止所有小程序展示启动开屏广告，修改后需重启微信", SkipGlobalMiniProgramSplashAdsSettings.DEFAULT_ENABLE)
        "finder_media_download" -> SwitchRow(remember { HchatStorage.preferences(context, FinderMediaDownloadSettings.PREFS_NAME) }, FinderMediaDownloadSettings.KEY_ENABLE, "视频号媒体下载", "在视频号分享菜单增加复制链接和下载入口，媒体保存到 Hchat/Finder", FinderMediaDownloadSettings.DEFAULT_ENABLE)
        "profile_id" -> SwitchRow(remember { HchatStorage.preferences(context, ProfileIdSettings.PREFS_NAME) }, ProfileIdSettings.KEY_ENABLE, "显示好友/群聊ID", "在好友和群聊资料页显示可点击复制的 ID", ProfileIdSettings.DEFAULT_ENABLE)
        "emoji_collection_limit" -> SwitchRow(remember { ConfigStore(context).getFeaturePrefs(EmojiCollectionLimitFeature.ID) }, EmojiCollectionLimitFeature.KEY_ENABLED, "扩展表情收藏上限", "将本机收藏上限提高到 5000。仅支持微信 8.0.80（3220），云同步仍受微信限制。", EmojiCollectionLimitFeature.DEFAULT_ENABLED)
        "hchat_group_member_history" -> SwitchRow(remember { HchatStorage.preferences(context, HchatExtraSettings.PREFS_NAME) }, HchatExtraSettings.KEY_GROUP_MEMBER_HISTORY, "历史发言记录", "在群成员资料页添加历史发言记录入口", HchatExtraSettings.DEFAULT_GROUP_MEMBER_HISTORY)
        "hchat_red_packet_details" -> SwitchRow(remember { HchatStorage.preferences(context, HchatExtraSettings.PREFS_NAME) }, HchatExtraSettings.KEY_RED_PACKET_DETAILS, "红包显示详情", "红包详情页显示金额、个数和领取时间", HchatExtraSettings.DEFAULT_RED_PACKET_DETAILS)
        "hchat_skip_web_risk" -> SwitchRow(remember { HchatStorage.preferences(context, HchatExtraSettings.PREFS_NAME) }, HchatExtraSettings.KEY_SKIP_WEB_RISK, "跳过网页风险", "跳过微信 WebView 高风险网页拦截提示", HchatExtraSettings.DEFAULT_SKIP_WEB_RISK)
        "forward.menu" -> {
            val sp = remember { HchatStorage.preferences(context, MessageForwardSettings.PREFS_NAME) }
            SwitchRow(
                sp,
                MessageForwardSettings.KEY_ENABLE,
                "转发菜单",
                "在消息长按菜单中显示转发[H]",
                MessageForwardSettings.DEFAULT_ENABLE
            )
        }
        "forward.multi" -> {
            val sp = remember { HchatStorage.preferences(context, MessageForwardSettings.PREFS_NAME) }
            SwitchRow(
                sp,
                MessageForwardSettings.KEY_MULTI_MOMENTS_ENABLE,
                "多选转发到朋友圈",
                "在多选消息菜单中显示转发到朋友圈[H]",
                MessageForwardSettings.DEFAULT_ENABLE
            )
        }
        "forward.favorite" -> {
            val sp = remember { HchatStorage.preferences(context, MessageForwardSettings.PREFS_NAME) }
            SwitchRow(
                sp,
                MessageForwardSettings.KEY_FAVORITE_FORWARD_ENABLE,
                "转发收藏",
                "长按收藏后显示转发[H]",
                MessageForwardSettings.DEFAULT_ENABLE
            )
        }
        "forward.moments" -> {
            val sp = remember { HchatStorage.preferences(context, MessageForwardSettings.PREFS_NAME) }
            SwitchRow(
                sp,
                MessageForwardSettings.KEY_SNS_FORWARD_ENABLE,
                "朋友圈转发",
                "在发现页或好友个人主页长按朋友圈后显示转发[H]",
                MessageForwardSettings.DEFAULT_ENABLE
            )
        }
        "remove_forward_limit" -> {
            val sp = remember { HchatStorage.preferences(context, RemoveForwardLimitSettings.PREFS_NAME) }
            SwitchRow(
                sp,
                RemoveForwardLimitSettings.KEY_ENABLE,
                "移除转发限制",
                "允许微信原生转发选择超过 9 个会话；大量目标仍受微信发送能力限制",
                RemoveForwardLimitSettings.DEFAULT_ENABLE
            )
        }
        "quick_moments" -> {
            val sp = remember { HchatStorage.preferences(context, QuickMomentsSettings.PREFS_NAME) }
            SwitchRow(
                sp,
                QuickMomentsSettings.KEY_ENABLE,
                "快捷查看朋友圈",
                "长按本人、好友或企业微信联系人会话时显示朋友圈入口，聊天分组内同样生效",
                QuickMomentsSettings.DEFAULT_ENABLE
            )
        }
        "quick_terminate" -> {
            val sp = remember { HchatStorage.preferences(context, QuickTerminateSettings.PREFS_NAME) }
            SwitchRow(
                sp,
                QuickTerminateSettings.KEY_ENABLE,
                "快捷终止",
                "在微信右上角加号菜单中添加快捷终止，重启微信后生效",
                QuickTerminateSettings.DEFAULT_ENABLE
            )
        }
        "quick_group_chat_label" -> {
            val sp = remember { HchatStorage.preferences(context, QuickGroupChatLabelSettings.PREFS_NAME) }
            SwitchRow(
                sp,
                QuickGroupChatLabelSettings.KEY_ENABLE,
                "快捷设置群聊标签",
                "长按群聊会话时显示群聊标签入口，聊天分组内同样生效",
                QuickGroupChatLabelSettings.DEFAULT_ENABLE
            )
        }
        "quick_contact_edit" -> {
            val sp = remember { HchatStorage.preferences(context, QuickContactEditSettings.PREFS_NAME) }
            SwitchRow(
                sp,
                QuickContactEditSettings.KEY_ENABLE,
                "快捷设置备注和标签",
                "长按好友、群聊会话或朋友圈头像时显示设置入口，聊天分组内同样生效",
                QuickContactEditSettings.DEFAULT_ENABLE
            )
        }
    }
}


@Composable
internal fun CallMediaLimitMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, CallMediaLimitSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "音视频通话") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        CallMediaLimitSettings.KEY_ENABLE,
                        "移除通话媒体限制",
                        "通话时允许播放语音和视频，并打开聊天拍摄",
                        CallMediaLimitSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun CallRingtoneBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, CallMediaLimitSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "通话铃声") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        CallMediaLimitSettings.KEY_BLOCK_INCOMING_RINGTONE,
                        "屏蔽通话呼入铃声",
                        "收到微信语音或视频通话时不播放来电铃声",
                        CallMediaLimitSettings.DEFAULT_BLOCK_INCOMING_RINGTONE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        CallMediaLimitSettings.KEY_BLOCK_OUTGOING_RINGTONE,
                        "屏蔽通话呼出铃声",
                        "拨打微信语音或视频通话时不播放等待铃声",
                        CallMediaLimitSettings.DEFAULT_BLOCK_OUTGOING_RINGTONE
                    )
                }
            }
        }
    }
}

@Composable
internal fun StatusTextLimitMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, StatusTextLimitSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "个人状态") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        StatusTextLimitSettings.KEY_ENABLE,
                        "解除状态词长度限制",
                        "开启后个人状态词可超过微信默认 10 字限制",
                        StatusTextLimitSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun EditMessageMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, EditMessageSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "聊天记录") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        EditMessageSettings.KEY_ENABLE,
                        "修改聊天记录",
                        "长按文字、引用或转账消息后可修改本地记录",
                        EditMessageSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}






@Composable
internal fun MessageAffixMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { MessageAffixSettings.preferences(context) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(MessageAffixSettings.KEY_ENABLE, MessageAffixSettings.DEFAULT_ENABLE))
    }
    var textFormat by remember {
        mutableStateOf(MessageAffixSettings.textFormat(sp))
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                MessageAffixSettings.KEY_TIME_FORMAT,
                MessageAffixSettings.DEFAULT_TIME_FORMAT
            ).orEmpty()
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "聊天消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "发送文本格式",
                        summary = "将聊天发送的文字按自定义格式处理"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(MessageAffixSettings.KEY_ENABLE, it).apply()
                    }
                        InsetDivider()
                        VariableInputRow(
                            title = "文本格式",
                            summary = if (textFormat.isNotBlank() && MessageAffixSettings.originalTextVariableCount(textFormat) != 1)
                                "必须且只能包含一个原消息变量；保留上次保存的值" else "点击下方中文变量插入到光标位置",
                            value = textFormat,
                            variables = messageAffixTemplateVariables,
                            minLines = 2,
                            onEditingFinished = {
                                if (it.isBlank()) sp.edit().putString(MessageAffixSettings.KEY_TEXT_FORMAT, MessageAffixSettings.DEFAULT_TEXT_FORMAT).apply()
                            }
                        ) {
                            textFormat = it
                            if (MessageAffixSettings.originalTextVariableCount(it) == 1) {
                                sp.edit().putString(MessageAffixSettings.KEY_TEXT_FORMAT, it).apply()
                            }
                        }
                        InsetDivider()
                        InputRow("时间格式", if (timeFormat.isNotBlank() && !MessageAffixSettings.isValidTimeFormat(timeFormat))
                            "时间格式无效；保留上次保存的值" else "例如 HH:mm:ss 或 yyyy-MM-dd HH:mm:ss", timeFormat,
                            onEditingFinished = {
                                if (it.isBlank()) sp.edit().putString(MessageAffixSettings.KEY_TIME_FORMAT, MessageAffixSettings.DEFAULT_TIME_FORMAT).apply()
                            }) {
                            timeFormat = it
                            if (it.isNotBlank() && MessageAffixSettings.isValidTimeFormat(it)) {
                                sp.edit().putString(MessageAffixSettings.KEY_TIME_FORMAT, MessageAffixSettings.normalizeTimeFormat(it)).apply()
                            }
                        }
                }
            }

            item { SettingsSectionTitle(text = "操作") }
            item {
                SettingsCard {
                    ContentActionRow("重置") {
                        textFormat = MessageAffixSettings.DEFAULT_TEXT_FORMAT
                        timeFormat = MessageAffixSettings.DEFAULT_TIME_FORMAT
                        sp.edit()
                            .putString(MessageAffixSettings.KEY_TEXT_FORMAT, textFormat)
                            .putString(MessageAffixSettings.KEY_TIME_FORMAT, timeFormat)
                            .apply()
                    }
                }
            }
        }
    }
}

@Composable
internal fun InputHintMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { InputHintSettings.preferences(context) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(InputHintSettings.KEY_ENABLE, InputHintSettings.DEFAULT_ENABLE))
    }
    var restoreLegacyInput by remember {
        mutableStateOf(
            sp.getBoolean(
                InputHintSettings.KEY_RESTORE_LEGACY_INPUT,
                InputHintSettings.DEFAULT_RESTORE_LEGACY_INPUT
            )
        )
    }
    var statisticsEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                InputHintSettings.KEY_STATISTICS_ENABLE,
                InputHintSettings.DEFAULT_STATISTICS_ENABLE
            )
        )
    }
    var template by remember { mutableStateOf(InputHintSettings.template(sp)) }
    var stats by remember { mutableStateOf(InputHintStats()) }
    DisposableEffect(statisticsEnabled) {
        val subscription = if (statisticsEnabled) {
            stats = OutgoingMessageStatsRepository.current()
            OutgoingMessageStatsRepository.subscribe {
                stats = OutgoingMessageStatsRepository.current()
            }
        } else {
            stats = InputHintStats()
            null
        }
        onDispose { subscription?.unsubscribe() }
    }
    val preview = InputHintSettings.renderTemplate(template, stats)
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "聊天输入框") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "输入框提示",
                        summary = "自定义聊天输入框没有文字时显示的内容"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(InputHintSettings.KEY_ENABLE, it).apply()
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = restoreLegacyInput,
                        title = "恢复旧版输入框",
                        summary = "关闭输入栏长按语音输入，恢复旧版样式；重启微信后生效"
                    ) {
                        restoreLegacyInput = it
                        sp.edit().putBoolean(InputHintSettings.KEY_RESTORE_LEGACY_INPUT, it).apply()
                    }
                        InsetDivider()
                        SwitchRow(
                            checked = statisticsEnabled,
                            title = "统计今日发送数量",
                            summary = "统计今天自己实际发送的消息"
                        ) {
                            statisticsEnabled = it
                            sp.edit().putBoolean(InputHintSettings.KEY_STATISTICS_ENABLE, it).apply()
                        }
                        InsetDivider()
                        VariableInputRow(
                            title = "提示文本",
                            summary = if (statisticsEnabled) {
                                "点击下方中文变量插入到光标位置"
                            } else {
                                "输入自定义提示内容"
                            },
                            value = template,
                            variables = if (statisticsEnabled) inputHintTemplateVariables else emptyList(),
                            minLines = 2,
                            onEditingFinished = {
                                if (it.isBlank()) sp.edit().putString(InputHintSettings.KEY_TEMPLATE, InputHintSettings.DEFAULT_TEMPLATE).apply()
                            }
                        ) {
                            template = it
                            if (it.isNotBlank()) sp.edit().putString(InputHintSettings.KEY_TEMPLATE, it).apply()
                        }
                        InsetDivider()
                        InfoRow("当前提示", preview)
                }
            }

            item { SettingsSectionTitle(text = "操作") }
            item {
                SettingsCard {
                    ContentActionRow("重置") {
                        enabled = InputHintSettings.DEFAULT_ENABLE
                        statisticsEnabled = InputHintSettings.DEFAULT_STATISTICS_ENABLE
                        restoreLegacyInput = InputHintSettings.DEFAULT_RESTORE_LEGACY_INPUT
                        template = InputHintSettings.DEFAULT_TEMPLATE
                        sp.edit()
                            .putBoolean(InputHintSettings.KEY_ENABLE, enabled)
                            .putBoolean(InputHintSettings.KEY_STATISTICS_ENABLE, statisticsEnabled)
                            .putBoolean(InputHintSettings.KEY_RESTORE_LEGACY_INPUT, restoreLegacyInput)
                            .putString(InputHintSettings.KEY_TEMPLATE, template)
                            .apply()
                    }
                }
            }
        }
    }
}
