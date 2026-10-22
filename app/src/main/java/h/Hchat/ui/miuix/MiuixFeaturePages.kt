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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.autooriginal.AutoOriginalImageSettings
import h.Hchat.hooks.items.autovieworiginal.AutoViewOriginalSettings
import h.Hchat.hooks.items.atallnotify.AtAllNotificationBlockSettings
import h.Hchat.hooks.items.callmedialimit.CallMediaLimitSettings
import h.Hchat.hooks.items.editmsg.EditMessageSettings
import h.Hchat.hooks.items.forwardlimit.RemoveForwardLimitSettings
import h.Hchat.hooks.items.grouplabel.QuickGroupChatLabelSettings
import h.Hchat.hooks.items.hotupdate.DisableHotUpdateSettings
import h.Hchat.hooks.items.inputhint.InputHintSettings
import h.Hchat.hooks.items.inputhint.InputHintStats
import h.Hchat.hooks.items.inputhint.OutgoingMessageStatsRepository
import h.Hchat.hooks.items.messageaffix.MessageAffixSettings
import h.Hchat.hooks.items.messageforward.MessageForwardSettings
import h.Hchat.hooks.items.multirecall.MultiRecallSettings
import h.Hchat.hooks.items.patblock.PatBlockSettings
import h.Hchat.hooks.items.quickcontactedit.QuickContactEditSettings
import h.Hchat.hooks.items.quickmoments.QuickMomentsSettings
import h.Hchat.hooks.items.quickterminate.QuickTerminateSettings
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskContentItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import h.Hchat.hooks.items.selectedmessages.SelectedMessagesRuntimeCoordinator
import h.Hchat.hooks.items.selectedmessages.SelectedMessagesSettings
import h.Hchat.hooks.items.statuslimit.StatusTextLimitSettings
import h.Hchat.hooks.items.typingreport.TypingReportBlockSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import kotlinx.coroutines.launch

@Composable
internal fun MultiRecallMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MultiRecallSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MultiRecallSettings.KEY_ENABLE,
                        "多选撤回",
                        "在多选消息的分享菜单中显示批量撤回",
                        MultiRecallSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

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
                items = listOf(ScheduledTaskContentItem(ScheduledTaskSettings.TYPE_IMAGE, "")),
                intervalSeconds = SelectedMessagesSettings.sendIntervalSeconds(context),
                mediaIntervalSeconds = SelectedMessagesSettings.sendIntervalSeconds(context)
            )
        )
    }
    var channel by remember { mutableStateOf(SelectedMessagesRuntimeCoordinator.CHANNEL_MODULE) }
    val mainListState = rememberLazyListState()
    val editorListState = rememberLazyListState()

    fun validationError(): String? {
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
                    channel = channel,
                    listState = editorListState,
                    onBack = { route = SelectedMessagesRoute.Main },
                    onDraftChange = { draft = it },
                    onChannelChange = { next ->
                        if (next != channel) {
                            channel = next
                            draft = draft.copy(targetIds = emptyList())
                        }
                    },
                    onPickTargets = ::openContactPicker,
                    onPickFiles = { itemIndex, itemType ->
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            ScheduledTaskFilePickerBridge.launch(activity, itemType) { paths ->
                                draft = updateScheduledTaskMediaItem(draft, itemIndex, itemType, paths)
                            }
                        }
                    },
                    onPickFavorite = { itemIndex ->
                        scheduledTaskEditableItems(draft).getOrNull(itemIndex)?.let { item ->
                            route = SelectedMessagesRoute.FavoritePicker(
                                FavoritePickerRequest(
                                    title = scheduledTaskPickerTitle(item.type),
                                    existingValue = item.value,
                                    onValue = { value ->
                                        draft = updateScheduledTaskContentItemValue(draft, itemIndex, value)
                                    },
                                    multiSelect = false,
                                    delimiter = "|"
                                )
                            )
                        }
                    },
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
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "多选消息") }
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "发送节奏") }
            item {
                SettingsCard {
                    NumberInputRow(
                        "群发助手间隔延迟",
                        "单位分钟，多个微信原生群发批次之间的等待时间",
                        officialIntervalMinutes
                    ) { value ->
                        officialIntervalMinutes = value
                        value.toIntOrNull()?.let { parsed ->
                            sp.edit().putInt(
                                SelectedMessagesSettings.KEY_OFFICIAL_INTERVAL_MINUTES,
                                parsed.coerceIn(0, SelectedMessagesSettings.MAX_OFFICIAL_INTERVAL_MINUTES)
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
                        value.toIntOrNull()?.let { parsed ->
                            sp.edit().putInt(
                                SelectedMessagesSettings.KEY_SEND_INTERVAL_SECONDS,
                                parsed.coerceIn(0, SelectedMessagesSettings.MAX_SEND_INTERVAL_SECONDS)
                            ).apply()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容群发") }
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
    channel: Int,
    listState: LazyListState,
    onBack: () -> Unit,
    onDraftChange: (ScheduledTaskItem) -> Unit,
    onChannelChange: (Int) -> Unit,
    onPickTargets: () -> Unit,
    onPickFiles: (Int, Int) -> Unit,
    onPickFavorite: (Int) -> Unit,
    onSend: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val official = channel == SelectedMessagesRuntimeCoordinator.CHANNEL_OFFICIAL
    val items = scheduledTaskEditableItems(draft)
    PageScaffold(
        title = "自定义群发",
        largeTitle = "自定义群发",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "开始群发",
                onPrimaryClick = onSend,
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "发送目标") }
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "发送内容") }
            item {
                SettingsCard {
                    ScheduledTaskChatContentRows(
                        task = draft,
                        onTaskChange = onDraftChange,
                        onPickFiles = onPickFiles,
                        onPickFavorite = onPickFavorite,
                        massSendOnly = official
                    )
                }
            }
            if (!official) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "发送节奏") }
                item {
                    SettingsCard {
                        NumberInputRow(
                            "聊天间隔",
                            "单位秒，多个聊天之间的等待时间",
                            draft.intervalSeconds.toString()
                        ) {
                            onDraftChange(
                                draft.copy(intervalSeconds = it.toIntOrNull()?.coerceIn(0, 3600) ?: 0)
                            )
                        }
                        if (items.size > 1) {
                            InsetDivider()
                            NumberInputRow(
                                scheduledTaskItemIntervalTitle(),
                                "单位秒，同一聊天连续发送的等待时间",
                                draft.mediaIntervalSeconds.toString()
                            ) {
                                onDraftChange(
                                    draft.copy(
                                        mediaIntervalSeconds = it.toIntOrNull()?.coerceIn(0, 3600) ?: 0
                                    )
                                )
                            }
                        }
                    }
                }
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

@Composable
internal fun MessageForwardMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MessageForwardSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageForwardSettings.KEY_ENABLE,
                        "转发菜单",
                        "在消息长按菜单中显示转发[H]",
                        MessageForwardSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        MessageForwardSettings.KEY_MULTI_MOMENTS_ENABLE,
                        "多选转发到朋友圈",
                        "在多选消息菜单中显示转发到朋友圈[H]",
                        MessageForwardSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "收藏") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageForwardSettings.KEY_FAVORITE_FORWARD_ENABLE,
                        "转发收藏",
                        "长按收藏后显示转发[H]",
                        MessageForwardSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageForwardSettings.KEY_SNS_FORWARD_ENABLE,
                        "朋友圈转发",
                        "在发现页或好友个人主页长按朋友圈后显示转发[H]",
                        MessageForwardSettings.DEFAULT_ENABLE
                    )
                }
            }
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
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "音视频通话") }
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
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "通话铃声") }
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
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "个人状态") }
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
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天记录") }
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
internal fun QuickContactEditMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickContactEditSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "会话列表") }
            item {
                SettingsCard {
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
    }
}

@Composable
internal fun QuickGroupChatLabelMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickGroupChatLabelSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "会话列表") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuickGroupChatLabelSettings.KEY_ENABLE,
                        "快捷设置群聊标签",
                        "长按群聊会话时显示群聊标签入口，聊天分组内同样生效",
                        QuickGroupChatLabelSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuickMomentsMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickMomentsSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "会话列表") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuickMomentsSettings.KEY_ENABLE,
                        "快捷查看朋友圈",
                        "长按本人、好友或企业微信联系人会话时显示朋友圈入口，聊天分组内同样生效",
                        QuickMomentsSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuickTerminateMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickTerminateSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "加号菜单") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuickTerminateSettings.KEY_ENABLE,
                        "快捷终止",
                        "在微信右上角加号菜单中添加快捷终止，重启微信后生效",
                        QuickTerminateSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun TypingReportBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, TypingReportBlockSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "输入状态") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        TypingReportBlockSettings.KEY_ENABLE,
                        "拦截正在输入上报",
                        "输入文字时不向对方显示正在输入状态",
                        TypingReportBlockSettings.DEFAULT_ENABLE
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
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    when {
                        enabled && MessageAffixSettings.originalTextVariableCount(textFormat) != 1 -> {
                            Toast.makeText(
                                context,
                                "文本格式必须且只能包含一个原消息变量",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        enabled && !MessageAffixSettings.isValidTimeFormat(timeFormat) -> {
                            Toast.makeText(context, "时间格式无效", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            textFormat = MessageAffixSettings.normalizeTextFormat(textFormat)
                            timeFormat = MessageAffixSettings.normalizeTimeFormat(timeFormat)
                            sp.edit()
                                .putBoolean(MessageAffixSettings.KEY_ENABLE, enabled)
                                .putString(MessageAffixSettings.KEY_TEXT_FORMAT, textFormat)
                                .putString(MessageAffixSettings.KEY_TIME_FORMAT, timeFormat)
                                .apply()
                            Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                secondaryText = "重置",
                onSecondaryClick = {
                    textFormat = MessageAffixSettings.DEFAULT_TEXT_FORMAT
                    timeFormat = MessageAffixSettings.DEFAULT_TIME_FORMAT
                },
                middleText = "取消",
                onMiddleClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "发送文本格式",
                        summary = "将聊天发送的文字按自定义格式处理"
                    ) {
                        enabled = it
                    }
                    if (enabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "文本格式",
                            summary = "点击下方中文变量插入到光标位置",
                            value = textFormat,
                            variables = messageAffixTemplateVariables,
                            minLines = 2
                        ) {
                            textFormat = it
                        }
                        InsetDivider()
                        InputRow("时间格式", "例如 HH:mm:ss 或 yyyy-MM-dd HH:mm:ss", timeFormat) {
                            timeFormat = it
                        }
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
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    template = InputHintSettings.normalizeTemplate(template)
                    sp.edit()
                        .putBoolean(InputHintSettings.KEY_ENABLE, enabled)
                        .putBoolean(InputHintSettings.KEY_STATISTICS_ENABLE, statisticsEnabled)
                        .putString(InputHintSettings.KEY_TEMPLATE, template)
                        .apply()
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                },
                secondaryText = "重置",
                onSecondaryClick = {
                    enabled = InputHintSettings.DEFAULT_ENABLE
                    statisticsEnabled = InputHintSettings.DEFAULT_STATISTICS_ENABLE
                    template = InputHintSettings.DEFAULT_TEMPLATE
                },
                middleText = "取消",
                onMiddleClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天输入框") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "输入框提示",
                        summary = "自定义聊天输入框没有文字时显示的内容"
                    ) {
                        enabled = it
                    }
                    if (enabled) {
                        InsetDivider()
                        SwitchRow(
                            checked = statisticsEnabled,
                            title = "统计今日发送数量",
                            summary = "统计今天自己实际发送的消息"
                        ) {
                            statisticsEnabled = it
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
                            minLines = 2
                        ) {
                            template = it
                        }
                        InsetDivider()
                        InfoRow("当前提示", preview)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PatBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, PatBlockSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天头像") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        PatBlockSettings.KEY_ENABLE,
                        "禁止拍一拍",
                        "双击聊天头像时不发送拍一拍",
                        PatBlockSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun AutoOriginalImageMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AutoOriginalImageSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天图片") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        AutoOriginalImageSettings.KEY_ENABLE,
                        "自动勾选原图",
                        "进入聊天图片发送界面时自动选择原图",
                        AutoOriginalImageSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun AutoViewOriginalMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AutoViewOriginalSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天媒体") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        AutoViewOriginalSettings.KEY_ENABLE,
                        "自动查看原图",
                        "打开聊天图片或视频时自动查看原图或原视频",
                        AutoViewOriginalSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun RemoveForwardLimitMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RemoveForwardLimitSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "消息转发") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        RemoveForwardLimitSettings.KEY_ENABLE,
                        "移除转发限制",
                        "允许微信原生转发选择超过 9 个会话；大量目标仍受微信发送能力限制",
                        RemoveForwardLimitSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun AtAllNotificationBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AtAllNotificationBlockSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(AtAllNotificationBlockSettings.KEY_ENABLE, AtAllNotificationBlockSettings.DEFAULT_ENABLE))
    }
    var selectedGroups by remember {
        mutableStateOf(AtAllNotificationBlockSettings.parseGroups(sp.getString(AtAllNotificationBlockSettings.KEY_GROUPS, "")))
    }
    var legacyAllGroups by remember { mutableStateOf(enabled && !sp.contains(AtAllNotificationBlockSettings.KEY_GROUPS)) }
    var route by remember { mutableStateOf<AtAllNotificationBlockRoute>(AtAllNotificationBlockRoute.Main) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    SettingsRouteTransition(
        targetState = route,
        label = "AtAllNotificationBlockRoute",
        depthOf = { if (it is AtAllNotificationBlockRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            AtAllNotificationBlockRoute.Main -> PageScaffold(
                title = provider.title(),
                largeTitle = provider.title(),
                scrollBehavior = scrollBehavior,
                bottomBar = { BottomActionBar("返回", onBack) }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 84.dp
                    )
                ) {
                    item { SmallTitle(text = "群聊通知") }
                    item {
                        SettingsCard {
                            SwitchRow(
                                checked = enabled,
                                title = "屏蔽艾特所有人",
                                summary = "仅拦截所选群聊的艾特所有人通知",
                                onCheckedChange = {
                                    enabled = it
                                    val editor = sp.edit().putBoolean(AtAllNotificationBlockSettings.KEY_ENABLE, it)
                                    if (it && !sp.contains(AtAllNotificationBlockSettings.KEY_GROUPS)) {
                                        editor.putString(AtAllNotificationBlockSettings.KEY_GROUPS, "")
                                        legacyAllGroups = false
                                    }
                                    editor.apply()
                                }
                            )
                            if (enabled) {
                                InsetDivider()
                                ActionRow(
                                    "选择屏蔽群聊",
                                    when {
                                        legacyAllGroups -> "当前屏蔽全部群聊，重新选择后按选择生效"
                                        selectedGroups.isEmpty() -> "未选择群聊，不会屏蔽通知"
                                        else -> "已选择 ${selectedGroups.size} 个群聊"
                                    }
                                ) {
                                    route = AtAllNotificationBlockRoute.GroupPicker(
                                        ContactPickerRequest(
                                            title = "选择屏蔽群聊",
                                            mode = ContactPickerMode.GROUPS,
                                            multiSelect = true,
                                            existingValue = formatIds(selectedGroups),
                                            onValue = {}
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is AtAllNotificationBlockRoute.GroupPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = AtAllNotificationBlockRoute.Main },
                onConfirm = { picked ->
                    selectedGroups = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    legacyAllGroups = false
                    sp.edit()
                        .putString(AtAllNotificationBlockSettings.KEY_GROUPS, formatIds(selectedGroups))
                        .apply()
                    route = AtAllNotificationBlockRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun DisableHotUpdateMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, DisableHotUpdateSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "热更新") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        DisableHotUpdateSettings.KEY_ENABLE,
                        "屏蔽热更新",
                        "阻止微信加载和应用热更新补丁，重启微信后生效",
                        DisableHotUpdateSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}
