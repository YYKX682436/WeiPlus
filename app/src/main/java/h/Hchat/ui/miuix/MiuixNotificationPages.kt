package h.Hchat.ui.miuix

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.chattime.ChatTimeStyleSettings
import h.Hchat.hooks.items.customnotify.CustomNotificationRule
import h.Hchat.hooks.items.customnotify.CustomNotificationRuntime
import h.Hchat.hooks.items.customfriendavatar.CustomFriendAvatarPicker
import h.Hchat.hooks.items.customfriendavatar.CustomFriendAvatarSettings
import h.Hchat.hooks.items.customfriendavatar.CustomFriendAvatarStore
import h.Hchat.hooks.items.customnotify.CustomNotificationSettings
import h.Hchat.hooks.items.emojisave.EmojiSaveSettings
import h.Hchat.hooks.items.fakelocation.FakeLocationSettings
import h.Hchat.hooks.items.fakevoiceduration.FakeVoiceDurationSettings
import h.Hchat.hooks.items.gameemoji.GameEmojiSettings
import h.Hchat.hooks.items.hideavatar.HideChatAvatarSettings
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationRuntime
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationSettings
import h.Hchat.hooks.items.keywordnotify.KeywordRule
import h.Hchat.hooks.items.zombiecheck.ZombieCheckController
import h.Hchat.hooks.items.zombiecheck.ZombieCheckActionResult
import h.Hchat.hooks.items.zombiecheck.ZombieCheckResultType
import h.Hchat.hooks.items.zombiecheck.ZombieCheckSettings
import h.Hchat.hooks.items.messagebubble.MessageBubblePickResult
import h.Hchat.hooks.items.messagebubble.MessageBubblePicker
import h.Hchat.hooks.items.messagebubble.MessageBubbleSettings
import h.Hchat.hooks.items.messagebubble.MessageBubbleSlot
import h.Hchat.hooks.items.messagebubble.MessageBubbleStore
import h.Hchat.hooks.items.messagebubble.MessageBubbleKind
import h.Hchat.hooks.items.messagetextcolor.MessageTextColorSettings
import h.Hchat.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibSettings
import h.Hchat.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsSettings
import h.Hchat.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsSettings
import h.Hchat.hooks.items.musicorder.QQMusicOrderSettings
import h.Hchat.hooks.items.payment.core.RedPacketSettings
import h.Hchat.hooks.items.quoteclear.QuoteDeleteClearSettings
import h.Hchat.hooks.items.roundavatar.RoundAvatarSettings
import h.Hchat.hooks.items.swipequote.SwipeQuoteSettings
import h.Hchat.hooks.items.textspeech.TextSpeechEngineCatalog
import h.Hchat.hooks.items.textspeech.TextSpeechSettings
import h.Hchat.hooks.items.textspeech.TextSpeechVoiceCatalog
import h.Hchat.hooks.items.voicepreview.VoicePreviewSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedHashSet
import java.util.Locale

@Composable
internal fun CustomNotificationMainPage(
    context: Context,
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    rules: List<CustomNotificationRule>,
    defaultPrivateRule: CustomNotificationRule,
    defaultGroupRule: CustomNotificationRule,
    defaultOfficialRule: CustomNotificationRule,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onEditDefault: (CustomNotificationDefaultKind) -> Unit
) {
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
            item { SmallTitle(text = "自定义通知") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        CustomNotificationSettings.KEY_ENABLE,
                        "启用自定义通知",
                        "开启后按会话专属规则或默认规则接管微信通知",
                        CustomNotificationSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    ActionRow(
                        "默认私聊通知",
                        customNotificationRuleSummary(context, defaultPrivateRule)
                    ) {
                        onEditDefault(CustomNotificationDefaultKind.PRIVATE)
                    }
                    InsetDivider()
                    ActionRow(
                        "默认群聊通知",
                        customNotificationRuleSummary(context, defaultGroupRule)
                    ) {
                        onEditDefault(CustomNotificationDefaultKind.GROUP)
                    }
                    InsetDivider()
                    ActionRow(
                        "默认公众号通知",
                        customNotificationRuleSummary(context, defaultOfficialRule)
                    ) {
                        onEditDefault(CustomNotificationDefaultKind.OFFICIAL)
                    }
                    InsetDivider()
                    ActionRow(
                        "会话规则",
                        if (rules.isEmpty()) "未配置会话" else "${rules.size} 个会话，${rules.count { it.enabled }} 个启用"
                    ) {
                        onOpenRules()
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomNotificationRuleListPage(
    context: Context,
    rules: List<CustomNotificationRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onBack: () -> Unit,
    onSaveRules: (List<CustomNotificationRule>) -> Unit,
    onEditRule: (String) -> Unit,
    onToggleRule: (CustomNotificationRule, Boolean) -> Unit,
    onBatchEdit: () -> Unit,
    onAddRules: () -> Unit
) {
    var category by remember { mutableStateOf(ConversationRuleCategory.ALL) }
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedTalkers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val lower = query.trim().lowercase(Locale.US)
    val visible = rules.filter {
        conversationRuleCategoryMatches(it, category) && (
            lower.isBlank() ||
                it.label.lowercase(Locale.US).contains(lower) ||
                it.talker.lowercase(Locale.US).contains(lower)
            )
    }.sortedWith(compareBy<CustomNotificationRule> { !it.enabled }.thenBy { it.group }.thenBy { it.label.lowercase(Locale.US) })
    val visibleTalkers = visible.mapTo(LinkedHashSet()) { it.talker }
    val allVisibleSelected = visibleTalkers.isNotEmpty() && visibleTalkers.all { it in selectedTalkers }
    val selectedRules = rules.filter { it.talker in selectedTalkers }
    PageScaffold(
        title = "会话规则",
        largeTitle = "会话规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedRules.size}）",
                    onPrimaryClick = {
                        if (selectedRules.isEmpty()) {
                            Toast.makeText(context, "请先选择会话规则", Toast.LENGTH_SHORT).show()
                        } else {
                            showDeleteConfirm = true
                        }
                    },
                    secondaryText = "取消",
                    onSecondaryClick = {
                        batchDeleteMode = false
                        selectedTalkers = emptySet()
                    },
                    middleText = if (visibleTalkers.isEmpty()) null else if (allVisibleSelected) "取消全选" else "全选",
                    onMiddleClick = if (visibleTalkers.isEmpty()) null else {
                        {
                            selectedTalkers = if (allVisibleSelected) {
                                selectedTalkers - visibleTalkers
                            } else {
                                selectedTalkers + visibleTalkers
                            }
                        }
                    }
                )
            } else {
                BottomActionBar(
                    primaryText = "添加会话",
                    onPrimaryClick = onAddRules,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
                    middleText = if (rules.isEmpty()) null else "批量删除",
                    onMiddleClick = if (rules.isEmpty()) null else {
                        {
                            batchDeleteMode = true
                            selectedTalkers = emptySet()
                        }
                    }
                )
            }
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
            item {
                SettingsCard {
                    ConversationRuleCategoryTabs(
                        selected = category,
                        onSelected = { category = it }
                    )
                }
            }
            item {
                SettingsCard {
                    InputRow("搜索", "昵称 / wxid / 群号", query, onValueChange = onQueryChange)
                }
            }
            if (rules.isNotEmpty() && !batchDeleteMode) {
                item {
                    SettingsCard {
                        ActionRow("批量配置", "统一修改全部 ${rules.size} 个会话规则", onBatchEdit)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = if (visible.isEmpty()) "规则" else "规则 · ${visible.size} 项") }
            when {
                rules.isEmpty() -> item { SettingsCard { EmptyText("暂无会话规则，点击底部“添加会话”。") } }
                visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                else -> visible.forEach { rule ->
                    item {
                        SettingsCard(modifier = Modifier.padding(bottom = 8.dp)) {
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = customNotificationRuleTitle(rule),
                                        value = 0,
                                        summary = customNotificationRuleSummary(context, rule)
                                    ),
                                    selected = rule.talker in selectedTalkers,
                                    onClick = {
                                        selectedTalkers = if (rule.talker in selectedTalkers) {
                                            selectedTalkers - rule.talker
                                        } else {
                                            selectedTalkers + rule.talker
                                        }
                                    }
                                )
                            } else {
                                ActionRow(
                                    title = customNotificationRuleTitle(rule),
                                    summary = customNotificationRuleSummary(context, rule)
                                ) {
                                    onEditRule(rule.talker)
                                }
                                InsetDivider()
                                SwitchRow(
                                    checked = rule.enabled,
                                    title = "启用此规则",
                                    summary = if (rule.enabled) "当前会话已接管通知" else "当前会话不接管通知"
                                ) {
                                    onToggleRule(rule, it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedRules.size} 个会话规则，此操作不可撤销。",
        labels = selectedRules.map { customNotificationRuleTitle(it) },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selectedRules
            val targetTalkers = targets.mapTo(HashSet()) { it.talker }
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedTalkers = emptySet()
            onSaveRules(rules.filterNot { it.talker in targetTalkers })
            Toast.makeText(context, "已删除 ${targets.size} 个会话规则", Toast.LENGTH_SHORT).show()
        }
    )
}

@Composable
internal fun CustomNotificationBatchEditorPage(
    ruleCount: Int,
    onBack: () -> Unit,
    onApply: (CustomNotificationBatchUpdate) -> Unit
) {
    var applyEnabled by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var applyMode by remember { mutableStateOf(false) }
    var dnd by remember { mutableStateOf(false) }
    var applyVibrate by remember { mutableStateOf(false) }
    var vibrate by remember { mutableStateOf(true) }
    var applySound by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(true) }
    var applyMarkRead by remember { mutableStateOf(false) }
    var markRead by remember { mutableStateOf(true) }
    var applyQuickReply by remember { mutableStateOf(false) }
    var quickReply by remember { mutableStateOf(false) }
    var applyQuoteQuickReply by remember { mutableStateOf(false) }
    var quoteQuickReply by remember { mutableStateOf(false) }
    var applyMergeByTalker by remember { mutableStateOf(false) }
    var mergeByTalker by remember { mutableStateOf(false) }
    var applyShowDetail by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(true) }
    var applyIgnoreWechatDnd by remember { mutableStateOf(false) }
    var ignoreWechatDnd by remember { mutableStateOf(false) }
    var applyMute by remember { mutableStateOf(false) }
    var muteEnable by remember { mutableStateOf(false) }
    var muteStart by remember { mutableStateOf(CustomNotificationSettings.DEFAULT_MUTE_START) }
    var muteEnd by remember { mutableStateOf(CustomNotificationSettings.DEFAULT_MUTE_END) }
    var applyAtRules by remember { mutableStateOf(false) }
    var blockAtAll by remember { mutableStateOf(false) }
    var blockAtMe by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "批量配置",
        largeTitle = "批量配置",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "应用到 $ruleCount 项",
                onPrimaryClick = {
                    onApply(
                        CustomNotificationBatchUpdate(
                            applyEnabled = applyEnabled,
                            enabled = enabled,
                            applyMode = applyMode,
                            dnd = dnd,
                            applyVibrate = applyVibrate,
                            vibrate = vibrate,
                            applySound = applySound,
                            sound = sound,
                            applyMarkRead = applyMarkRead,
                            markRead = markRead,
                            applyQuickReply = applyQuickReply,
                            quickReply = quickReply,
                            applyQuoteQuickReply = applyQuoteQuickReply,
                            quoteQuickReply = quoteQuickReply,
                            applyMergeByTalker = applyMergeByTalker,
                            mergeByTalker = mergeByTalker,
                            applyShowDetail = applyShowDetail,
                            showDetail = showDetail,
                            applyIgnoreWechatDnd = applyIgnoreWechatDnd,
                            ignoreWechatDnd = ignoreWechatDnd,
                            applyMute = applyMute,
                            muteEnable = muteEnable,
                            muteStart = muteStart,
                            muteEnd = muteEnd,
                            applyAtRules = applyAtRules,
                            blockAtAll = blockAtAll,
                            blockAtMe = blockAtMe
                        )
                    )
                },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "应用范围") }
            item { SettingsCard { EmptyText("只会修改已勾选“应用”的项目，未勾选的设置保持原样。群聊专属的 @ 规则只影响群聊规则。") } }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(applyEnabled, "应用启用状态", "批量开关会话规则") { applyEnabled = it }
                    if (applyEnabled) {
                        InsetDivider()
                        SwitchRow(enabled, "启用规则", "关闭后不再接管对应会话通知") { enabled = it }
                    }
                    InsetDivider()
                    SwitchRow(applyMode, "应用免打扰", "批量设置是否弹自定义通知") { applyMode = it }
                    if (applyMode) {
                        InsetDivider()
                        SwitchRow(dnd, "免打扰", "开启后不弹自定义通知，只拦截原生通知") { dnd = it }
                    }
                    InsetDivider()
                    SwitchRow(applyIgnoreWechatDnd, "应用微信免打扰策略", "批量设置是否忽略微信原生会话免打扰") {
                        applyIgnoreWechatDnd = it
                    }
                    if (applyIgnoreWechatDnd) {
                        InsetDivider()
                        SwitchRow(
                            ignoreWechatDnd,
                            "忽略微信自带的消息免打扰",
                            "开启后，对应会话在微信中设置免打扰仍会弹出自定义通知"
                        ) { ignoreWechatDnd = it }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提醒") }
            item {
                SettingsCard {
                    SwitchRow(applyVibrate, "应用震动", "批量修改震动状态") { applyVibrate = it }
                    if (applyVibrate) {
                        InsetDivider()
                        SwitchRow(vibrate, "震动", "通知时震动") { vibrate = it }
                    }
                    InsetDivider()
                    SwitchRow(applySound, "应用铃声", "批量修改铃声状态，不修改每个会话已选铃声文件") { applySound = it }
                    if (applySound) {
                        InsetDivider()
                        SwitchRow(sound, "铃声", "通知时播放铃声") { sound = it }
                    }
                    InsetDivider()
                    SwitchRow(applyMarkRead, "应用已读按钮", "批量修改通知栏已读按钮") { applyMarkRead = it }
                    if (applyMarkRead) {
                        InsetDivider()
                        SwitchRow(markRead, "已读按钮", "通知栏直接标记当前会话已读") { markRead = it }
                    }
                    InsetDivider()
                    SwitchRow(applyQuickReply, "应用快捷回复", "批量修改通知栏快捷回复") { applyQuickReply = it }
                    if (applyQuickReply) {
                        InsetDivider()
                        SwitchRow(quickReply, "快捷回复", "通知栏直接回复文本消息") { quickReply = it }
                    }
                    InsetDivider()
                    SwitchRow(applyQuoteQuickReply, "应用引用消息回复", "批量修改快捷回复是否引用原消息") {
                        applyQuoteQuickReply = it
                    }
                    if (applyQuoteQuickReply) {
                        InsetDivider()
                        SwitchRow(quoteQuickReply, "引用消息回复", "快捷回复时引用触发通知的原消息") {
                            quoteQuickReply = it
                        }
                    }
                    InsetDivider()
                    SwitchRow(applyMergeByTalker, "应用通知展示方式", "批量设置同会话通知合并或分散") { applyMergeByTalker = it }
                    if (applyMergeByTalker) {
                        InsetDivider()
                        SwitchRow(mergeByTalker, "合并同会话通知", "同一 wxid 的新消息更新到一条通知") { mergeByTalker = it }
                    }
                    InsetDivider()
                    SwitchRow(applyShowDetail, "应用消息详情", "批量修改通知内容是否显示详情") { applyShowDetail = it }
                    if (applyShowDetail) {
                        InsetDivider()
                        SwitchRow(showDetail, "显示消息详情", "关闭后只显示收到一条新消息") { showDetail = it }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "静默与群聊") }
            item {
                SettingsCard {
                    SwitchRow(applyMute, "应用静默时段", "批量修改时段静默") { applyMute = it }
                    if (applyMute) {
                        InsetDivider()
                        SwitchRow(muteEnable, "开启时段静默", "指定时间内不弹通知") { muteEnable = it }
                        if (muteEnable) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", muteStart) { muteStart = it }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", muteEnd) { muteEnd = it }
                        }
                    }
                    InsetDivider()
                    SwitchRow(applyAtRules, "应用群聊 @ 规则", "只对群聊规则生效") { applyAtRules = it }
                    if (applyAtRules) {
                        InsetDivider()
                        SwitchRow(blockAtAll, "屏蔽@所有人", "命中 @所有人 时不弹通知") { blockAtAll = it }
                        InsetDivider()
                        SwitchRow(blockAtMe, "屏蔽@我", "命中 @我 时不弹通知") { blockAtMe = it }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomNotificationMissingRulePage(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "会话规则",
        largeTitle = "会话规则",
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SettingsCard { EmptyText("该规则已不存在") } }
        }
    }
}

@Composable
internal fun CustomNotificationRuleEditorPage(
    context: Context,
    initialRule: CustomNotificationRule,
    listState: LazyListState,
    isDefault: Boolean = false,
    onBack: () -> Unit,
    onSave: (CustomNotificationRule) -> Unit,
    onDelete: () -> Unit,
    onPickOnlyMembers: (String) -> Unit,
    onPickBlockMembers: (String) -> Unit
) {
    var enabled by remember(initialRule) { mutableStateOf(initialRule.enabled) }
    var dnd by remember(initialRule) { mutableStateOf(initialRule.mode == CustomNotificationSettings.MODE_DND) }
    var vibrate by remember(initialRule) { mutableStateOf(initialRule.vibrate) }
    var sound by remember(initialRule) { mutableStateOf(initialRule.sound) }
    var markRead by remember(initialRule) { mutableStateOf(initialRule.markRead) }
    var quickReply by remember(initialRule) { mutableStateOf(initialRule.quickReply) }
    var quoteQuickReply by remember(initialRule) { mutableStateOf(initialRule.quoteQuickReply) }
    var mergeByTalker by remember(initialRule) { mutableStateOf(initialRule.mergeByTalker) }
    var showDetail by remember(initialRule) { mutableStateOf(initialRule.showDetail) }
    var ignoreWechatDnd by remember(initialRule) { mutableStateOf(initialRule.ignoreWechatDnd) }
    var muteEnable by remember(initialRule) { mutableStateOf(initialRule.muteEnable) }
    var muteStart by remember(initialRule) { mutableStateOf(initialRule.muteStart) }
    var muteEnd by remember(initialRule) { mutableStateOf(initialRule.muteEnd) }
    var ringtone by remember(initialRule) { mutableStateOf(initialRule.ringtone) }
    var blockAtAll by remember(initialRule) { mutableStateOf(initialRule.blockAtAll) }
    var blockAtMe by remember(initialRule) { mutableStateOf(initialRule.blockAtMe) }
    var onlyMembers by remember(initialRule) { mutableStateOf(initialRule.onlyMembers) }
    var blockMembers by remember(initialRule) { mutableStateOf(initialRule.blockMembers) }
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = when {
            isDefault && initialRule.official -> "默认公众号通知"
            isDefault && initialRule.group -> "默认群聊通知"
            isDefault -> "默认私聊通知"
            initialRule.group -> "群聊通知配置"
            initialRule.official -> "公众号通知配置"
            else -> "私聊通知配置"
        },
        largeTitle = initialRule.label.ifBlank { initialRule.talker },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    onSave(
                        initialRule.copy(
                            enabled = enabled,
                            mode = if (dnd) CustomNotificationSettings.MODE_DND else CustomNotificationSettings.MODE_NOTIFY,
                            vibrate = vibrate,
                            sound = sound,
                            markRead = markRead,
                            quickReply = quickReply,
                            quoteQuickReply = quoteQuickReply,
                            mergeByTalker = mergeByTalker,
                            showDetail = showDetail,
                            ignoreWechatDnd = ignoreWechatDnd,
                            muteEnable = muteEnable,
                            muteStart = CustomNotificationSettings.normalizeTime(muteStart, CustomNotificationSettings.DEFAULT_MUTE_START),
                            muteEnd = CustomNotificationSettings.normalizeTime(muteEnd, CustomNotificationSettings.DEFAULT_MUTE_END),
                            ringtone = ringtone,
                            blockAtAll = blockAtAll,
                            blockAtMe = blockAtMe,
                            onlyMembers = if (isDefault) "" else CustomNotificationSettings.normalizeMemberRules(onlyMembers),
                            blockMembers = if (isDefault) "" else CustomNotificationSettings.normalizeMemberRules(blockMembers)
                        )
                    )
                },
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
            item { SmallTitle(text = "会话") }
            item {
                SettingsCard {
                    if (isDefault) {
                        InfoRow(
                            "类型",
                            when {
                                initialRule.official -> "未单独配置的公众号"
                                initialRule.group -> "未单独配置的群聊"
                                else -> "未单独配置的私聊"
                            }
                        )
                    } else {
                        InfoRow("名称", initialRule.label.ifBlank { initialRule.talker })
                        InsetDivider()
                        InfoRow("ID", initialRule.talker)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "通知") }
            item {
                SettingsCard {
                    SwitchRow(
                        enabled,
                        if (isDefault) "启用默认规则" else "启用此会话规则",
                        if (isDefault) "开启后接管未单独配置的对应会话通知" else "关闭后不再接管该会话通知"
                    ) { enabled = it }
                    InsetDivider()
                    SwitchRow(dnd, "免打扰", "开启后不弹自定义通知，也会拦截原生通知") { dnd = it }
                    InsetDivider()
                    SwitchRow(
                        ignoreWechatDnd,
                        "忽略微信自带的消息免打扰",
                        "开启后，该会话在微信中设置免打扰仍会弹出自定义通知"
                    ) { ignoreWechatDnd = it }
                    InsetDivider()
                    SwitchRow(vibrate, "震动", "通知时震动") { vibrate = it }
                    InsetDivider()
                    SwitchRow(sound, "铃声", "通知时播放系统或自定义铃声") { sound = it }
                    InsetDivider()
                    SwitchRow(markRead, "已读按钮", "通知栏直接标记当前会话已读") { markRead = it }
                    InsetDivider()
                    SwitchRow(quickReply, "快捷回复", "通知栏直接回复文本消息") { quickReply = it }
                    if (quickReply) {
                        InsetDivider()
                        SwitchRow(quoteQuickReply, "引用消息回复", "快捷回复时引用触发通知的原消息") {
                            quoteQuickReply = it
                        }
                    }
                    InsetDivider()
                    SwitchRow(mergeByTalker, "合并同会话通知", "同一 wxid 的新消息更新到一条通知") { mergeByTalker = it }
                    InsetDivider()
                    SwitchRow(showDetail, "通知显示消息详情", "关闭后只显示收到一条新消息") { showDetail = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "铃声") }
            item {
                SettingsCard {
                    ActionRow("选择系统铃声", ringtoneDisplayName(context, ringtone, RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM)) {
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开铃声选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            RingtonePickerBridge.launchSystem(activity, ringtone) { picked ->
                                ringtone = CustomNotificationRuntime.freezeRingtoneUri(context, picked)
                                Toast.makeText(context, "铃声已选择，保存后生效", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    InsetDivider()
                    ActionRow("从文件选择铃声", if (ringtone.isBlank()) "未选择" else ringtoneDisplayName(context, ringtone, RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM)) {
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            RingtonePickerBridge.launchFile(activity) { picked ->
                                ringtone = CustomNotificationRuntime.freezeRingtoneUri(context, picked)
                                Toast.makeText(context, "铃声已选择，保存后生效", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    if (ringtone.isNotBlank()) {
                        InsetDivider()
                        ActionRow("清空铃声", "恢复跟随系统") {
                            ringtone = ""
                            Toast.makeText(context, "铃声已清空，保存后生效", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "静默时段") }
            item {
                SettingsCard {
                    SwitchRow(muteEnable, "开启时段静默", "指定时间内不弹通知") { muteEnable = it }
                    if (muteEnable) {
                        InsetDivider()
                        TimeOfDayPickerRow("开始时间", muteStart) { muteStart = it }
                        InsetDivider()
                        TimeOfDayPickerRow("结束时间", muteEnd) { muteEnd = it }
                    }
                }
            }
            if (initialRule.group) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "群聊过滤") }
                item {
                    SettingsCard {
                        if (!isDefault) {
                            ActionRow("仅显示成员通知", memberRulesSummary(onlyMembers)) {
                                onPickOnlyMembers(onlyMembers)
                            }
                            InsetDivider()
                            ActionRow("屏蔽成员通知", memberRulesSummary(blockMembers)) {
                                onPickBlockMembers(blockMembers)
                            }
                            InsetDivider()
                        }
                        SwitchRow(blockAtAll, "屏蔽@所有人", "命中 @所有人 时不弹通知") { blockAtAll = it }
                        InsetDivider()
                        SwitchRow(blockAtMe, "屏蔽@我", "命中 @我 时不弹通知") { blockAtMe = it }
                    }
                }
            }
            if (!isDefault) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除规则", "移除该会话自定义通知配置") {
                            onDelete()
                        }
                    }
                }
            }
        }
    }
}

internal fun customNotificationRuleFromContact(option: ContactOption): CustomNotificationRule {
    return CustomNotificationRule(
        id = option.id,
        talker = option.id,
        label = option.label.ifBlank { option.id },
        group = option.group,
        official = option.official
    )
}

private fun customNotificationRuleTitle(rule: CustomNotificationRule): String {
    val prefix = when {
        rule.group -> "群聊"
        rule.official -> "公众号"
        else -> "私聊"
    }
    val state = if (rule.enabled) "" else "（关闭）"
    return "$prefix · ${rule.label.ifBlank { rule.talker }}$state"
}

private fun customNotificationRuleSummary(context: Context, rule: CustomNotificationRule): String {
    val items = ArrayList<String>()
    if (!rule.enabled) items += "未启用"
    if (rule.mode == CustomNotificationSettings.MODE_DND) items += "免打扰"
    if (rule.sound) items += if (rule.ringtone.isBlank()) "铃声" else ringtoneDisplayName(context, rule.ringtone, RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM)
    if (rule.vibrate) items += "震动"
    if (rule.markRead) items += "已读按钮"
    if (rule.quickReply) items += if (rule.quoteQuickReply) "引用快捷回复" else "快捷回复"
    if (rule.mergeByTalker) items += "合并通知"
    if (rule.ignoreWechatDnd) items += "忽略微信免打扰"
    if (rule.muteEnable) items += "${rule.muteStart}-${rule.muteEnd}静默"
    if (rule.group) {
        if (rule.onlyMembers.isNotBlank()) items += "仅${CustomNotificationSettings.splitMemberRules(rule.onlyMembers).size}成员"
        if (rule.blockMembers.isNotBlank()) items += "屏蔽${CustomNotificationSettings.splitMemberRules(rule.blockMembers).size}成员"
        if (rule.blockAtAll) items += "屏蔽@所有人"
        if (rule.blockAtMe) items += "屏蔽@我"
    }
    return items.ifEmpty { listOf("点击配置通知规则") }.joinToString(" / ")
}

private fun memberRulesSummary(value: String): String {
    val count = CustomNotificationSettings.splitMemberRules(value).size
    return if (count == 0) "未设置" else "已设置 $count 项"
}

internal fun memberRulesToGroupEntries(groupId: String, value: String): String {
    return CustomNotificationSettings.splitMemberRules(value)
        .map { member -> groupMemberEntry(groupId, member) }
        .joinToString(",")
}

internal fun groupEntriesToMemberRules(groupId: String, entries: List<String>): String {
    val members = entries.mapNotNull { entry ->
        val normalized = entry.trim()
        val separators = charArrayOf('/', '#', ':', '：')
        val index = separators.map { normalized.indexOf(it) }
            .filter { it > 0 }
            .minOrNull()
            ?: return@mapNotNull normalized.takeIf { it.isNotBlank() }
        val group = normalized.substring(0, index).trim()
        val member = normalized.substring(index + 1).trim()
        if (group == groupId && member.isNotBlank()) member else null
    }
    return members.distinct().joinToString(",")
}

internal data class CustomNotificationBatchUpdate(
    val applyEnabled: Boolean,
    val enabled: Boolean,
    val applyMode: Boolean,
    val dnd: Boolean,
    val applyVibrate: Boolean,
    val vibrate: Boolean,
    val applySound: Boolean,
    val sound: Boolean,
    val applyMarkRead: Boolean,
    val markRead: Boolean,
    val applyQuickReply: Boolean,
    val quickReply: Boolean,
    val applyQuoteQuickReply: Boolean,
    val quoteQuickReply: Boolean,
    val applyMergeByTalker: Boolean,
    val mergeByTalker: Boolean,
    val applyShowDetail: Boolean,
    val showDetail: Boolean,
    val applyIgnoreWechatDnd: Boolean,
    val ignoreWechatDnd: Boolean,
    val applyMute: Boolean,
    val muteEnable: Boolean,
    val muteStart: String,
    val muteEnd: String,
    val applyAtRules: Boolean,
    val blockAtAll: Boolean,
    val blockAtMe: Boolean
) {
    fun hasChanges(): Boolean {
        return applyEnabled || applyMode || applyVibrate || applySound || applyMarkRead || applyQuickReply || applyQuoteQuickReply || applyMergeByTalker ||
            applyShowDetail || applyIgnoreWechatDnd || applyMute || applyAtRules
    }

    fun apply(rule: CustomNotificationRule): CustomNotificationRule {
        var next = rule
        if (applyEnabled) next = next.copy(enabled = enabled)
        if (applyMode) next = next.copy(mode = if (dnd) CustomNotificationSettings.MODE_DND else CustomNotificationSettings.MODE_NOTIFY)
        if (applyVibrate) next = next.copy(vibrate = vibrate)
        if (applySound) next = next.copy(sound = sound)
        if (applyMarkRead) next = next.copy(markRead = markRead)
        if (applyQuickReply) next = next.copy(quickReply = quickReply)
        if (applyQuoteQuickReply) next = next.copy(quoteQuickReply = quoteQuickReply)
        if (applyMergeByTalker) next = next.copy(mergeByTalker = mergeByTalker)
        if (applyShowDetail) next = next.copy(showDetail = showDetail)
        if (applyIgnoreWechatDnd) next = next.copy(ignoreWechatDnd = ignoreWechatDnd)
        if (applyMute) {
            next = next.copy(
                muteEnable = muteEnable,
                muteStart = CustomNotificationSettings.normalizeTime(muteStart, CustomNotificationSettings.DEFAULT_MUTE_START),
                muteEnd = CustomNotificationSettings.normalizeTime(muteEnd, CustomNotificationSettings.DEFAULT_MUTE_END)
            )
        }
        if (applyAtRules && next.group) {
            next = next.copy(blockAtAll = blockAtAll, blockAtMe = blockAtMe)
        }
        return next
    }
}

@Composable
internal fun KeywordNotificationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { KeywordNotificationSettings(context) }
    val sp = remember { HchatStorage.preferences(context, KeywordNotificationSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<KeywordNotificationRoute>(KeywordNotificationRoute.Main) }
    var keywords by remember { mutableStateOf(settings.keywords()) }
    var excludeContacts by remember { mutableStateOf(settings.excludeContacts()) }
    var includeContacts by remember { mutableStateOf(settings.includeContacts()) }
    val keywordMainListState = rememberLazyListState()
    val keywordListState = rememberLazyListState()
    var keywordQuery by remember { mutableStateOf("") }

    fun saveKeywords(next: List<KeywordRule>) {
        val clean = next.map { it.copy(keyword = it.keyword.trim()) }
            .filter { it.keyword.isNotBlank() }
            .distinctBy { it.keyword }
        keywords = clean
        settings.saveKeywords(clean)
    }

    fun saveContacts(includeMode: Boolean, ids: Set<String>) {
        if (includeMode) {
            includeContacts = ids
            settings.saveIncludeContacts(ids)
        } else {
            excludeContacts = ids
            settings.saveExcludeContacts(ids)
        }
    }

    SettingsRouteTransition(
        targetState = route,
        label = "KeywordNotificationRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            KeywordNotificationRoute.Main -> KeywordNotificationMainPage(
                context = context,
                provider = provider,
                sp = sp,
                keywords = keywords,
                excludeContacts = excludeContacts,
                includeContacts = includeContacts,
                listState = keywordMainListState,
                onBack = onBack,
                onOpenKeywords = { route = KeywordNotificationRoute.KeywordList },
                onOpenTemplates = { route = KeywordNotificationRoute.TemplateEditor },
                onPickContacts = { includeMode ->
                    val current = if (includeMode) includeContacts else excludeContacts
                    route = KeywordNotificationRoute.ContactPicker(
                        request = ContactPickerRequest(
                            title = if (includeMode) "选择仅生效聊天" else "选择排除聊天",
                            mode = ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = current.joinToString(","),
                            onValue = {},
                            enableLabels = true
                        ),
                        includeMode = includeMode
                    )
                },
                onClearContacts = { includeMode ->
                    saveContacts(includeMode, emptySet())
                    Toast.makeText(context, if (includeMode) "已清空仅生效名单" else "已清空排除名单", Toast.LENGTH_SHORT).show()
                }
            )
            KeywordNotificationRoute.KeywordList -> KeywordNotificationKeywordListPage(
                keywords = keywords,
                query = keywordQuery,
                onQueryChange = { keywordQuery = it },
                listState = keywordListState,
                onBack = { route = KeywordNotificationRoute.Main },
                onAdd = { route = KeywordNotificationRoute.KeywordEditor(null) },
                onEdit = { route = KeywordNotificationRoute.KeywordEditor(it.keyword) },
                onClear = {
                    saveKeywords(emptyList())
                    Toast.makeText(context, "已清空关键词", Toast.LENGTH_SHORT).show()
                }
            )
            is KeywordNotificationRoute.KeywordEditor -> {
                val old = currentRoute.oldKeyword?.let { oldKeyword -> keywords.firstOrNull { it.keyword == oldKeyword } }
                KeywordNotificationKeywordEditorPage(
                    initial = old,
                    onBack = { route = KeywordNotificationRoute.KeywordList },
                    onSave = { saved ->
                        if (saved.keyword.isBlank()) {
                            Toast.makeText(context, "关键词不能为空", Toast.LENGTH_SHORT).show()
                        } else if (currentRoute.oldKeyword != saved.keyword && keywords.any { it.keyword == saved.keyword }) {
                            Toast.makeText(context, "该关键词已存在", Toast.LENGTH_SHORT).show()
                        } else {
                            val next = if (currentRoute.oldKeyword == null) {
                                keywords + saved
                            } else {
                                keywords.map { if (it.keyword == currentRoute.oldKeyword) saved else it }
                            }
                            saveKeywords(next)
                            Toast.makeText(context, "关键词已保存", Toast.LENGTH_SHORT).show()
                            route = KeywordNotificationRoute.KeywordList
                        }
                    },
                    onDelete = if (old == null) {
                        null
                    } else {
                        {
                            saveKeywords(keywords.filterNot { it.keyword == old.keyword })
                            Toast.makeText(context, "关键词已删除", Toast.LENGTH_SHORT).show()
                            route = KeywordNotificationRoute.KeywordList
                        }
                    }
                )
            }
            KeywordNotificationRoute.TemplateEditor -> KeywordNotificationTemplatePage(
                context = context,
                sp = sp,
                onBack = { route = KeywordNotificationRoute.Main }
            )
            is KeywordNotificationRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = KeywordNotificationRoute.Main },
                onConfirm = { picked ->
                    val ids = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    saveContacts(currentRoute.includeMode, ids)
                    Toast.makeText(context, "名单已保存", Toast.LENGTH_SHORT).show()
                    route = KeywordNotificationRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun TextSpeechMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { TextSpeechSettings(context) }
    val sp = remember { HchatStorage.preferences(context, TextSpeechSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<TextSpeechRoute>(TextSpeechRoute.Main) }
    var allowedContacts by remember { mutableStateOf(settings.allowedContacts()) }
    val mainListState = rememberLazyListState()

    fun saveAllowedContacts(ids: Set<String>) {
        allowedContacts = ids
        settings.saveAllowedContacts(ids)
    }

    SettingsRouteTransition(
        targetState = route,
        label = "TextSpeechRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            TextSpeechRoute.Main -> TextSpeechMainPage(
                provider = provider,
                sp = sp,
                allowedContacts = allowedContacts,
                listState = mainListState,
                onBack = onBack,
                onPickContacts = {
                    route = TextSpeechRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "设置允许名单",
                            mode = ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = allowedContacts.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is TextSpeechRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = TextSpeechRoute.Main },
                onConfirm = { picked ->
                    saveAllowedContacts(
                        picked.map { it.id }
                            .filter { it.isNotBlank() }
                            .toSet()
                    )
                    Toast.makeText(context, "允许名单已保存", Toast.LENGTH_SHORT).show()
                    route = TextSpeechRoute.Main
                }
            )
        }
    }
}

@Composable
private fun TextSpeechMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    allowedContacts: Set<String>,
    listState: LazyListState,
    onBack: () -> Unit,
    onPickContacts: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(sp.getBoolean(TextSpeechSettings.KEY_ENABLE, TextSpeechSettings.DEFAULT_ENABLE))
    }
    var ttsEngine by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_TTS_ENGINE, TextSpeechSettings.DEFAULT_TTS_ENGINE)
                ?: TextSpeechSettings.DEFAULT_TTS_ENGINE
        )
    }
    var ttsVoice by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_TTS_VOICE, TextSpeechSettings.DEFAULT_TTS_VOICE)
                ?: TextSpeechSettings.DEFAULT_TTS_VOICE
        )
    }
    var ttsVoiceChoices by remember { mutableStateOf<List<PopupChoice<String>>>(emptyList()) }
    var ttsVoiceLoading by remember { mutableStateOf(true) }
    var ttsVoiceError by remember { mutableStateOf("") }
    var ttsFallbackEnginePackage by remember { mutableStateOf("") }
    val installedTtsEngines = remember(context) { TextSpeechEngineCatalog.installed(context) }
    val ttsFallbackEngineLabel = remember(installedTtsEngines, ttsFallbackEnginePackage) {
        installedTtsEngines.firstOrNull { it.packageName == ttsFallbackEnginePackage }
            ?.label
            ?: ttsFallbackEnginePackage
    }
    val ttsEngineOptions = remember(installedTtsEngines, ttsEngine) {
        buildList {
            add(PopupChoice("跟随系统默认", TextSpeechSettings.DEFAULT_TTS_ENGINE))
            installedTtsEngines.forEach { add(PopupChoice(it.label, it.packageName)) }
            if (ttsEngine.isNotBlank() && installedTtsEngines.none { it.packageName == ttsEngine }) {
                add(PopupChoice("已不可用（$ttsEngine）", ttsEngine))
            }
        }
    }
    val ttsVoiceOptions = remember(ttsVoiceChoices, ttsVoice) {
        buildList {
            add(PopupChoice("跟随引擎默认", TextSpeechSettings.DEFAULT_TTS_VOICE))
            addAll(ttsVoiceChoices)
            if (ttsVoice.isNotBlank() && ttsVoiceChoices.none { it.value == ttsVoice }) {
                add(PopupChoice("已不可用（$ttsVoice）", ttsVoice))
            }
        }
    }
    DisposableEffect(context, ttsEngine) {
        ttsVoiceLoading = true
        ttsVoiceError = ""
        ttsFallbackEnginePackage = ""
        ttsVoiceChoices = emptyList()
        val handle = TextSpeechVoiceCatalog.load(context, ttsEngine) { result ->
            ttsVoiceChoices = result.options.map { PopupChoice(it.label, it.name) }
            ttsVoiceError = result.error
            ttsFallbackEnginePackage = if (result.usedFallback) {
                result.activeEnginePackage.ifBlank { "其它可用引擎" }
            } else {
                ""
            }
            ttsVoiceLoading = false
        }
        onDispose { handle.cancel() }
    }
    var volumeControl by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_VOLUME_CONTROL,
                TextSpeechSettings.DEFAULT_VOLUME_CONTROL
            )
        )
    }
    var playVoiceMessages by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_PLAY_VOICE_MESSAGES,
                TextSpeechSettings.DEFAULT_PLAY_VOICE_MESSAGES
            )
        )
    }
    var customAnnouncement by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_ANNOUNCE_SENDER,
                TextSpeechSettings.DEFAULT_ANNOUNCE_SENDER
            )
        )
    }
    var announcementTemplate by remember {
        mutableStateOf(
            sp.getString(
                TextSpeechSettings.KEY_ANNOUNCEMENT_TEMPLATE,
                TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
            ) ?: TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_TIME_FORMAT, TextSpeechSettings.DEFAULT_TIME_FORMAT)
                ?: TextSpeechSettings.DEFAULT_TIME_FORMAT
        )
    }
    var respectWechatDnd by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_RESPECT_WECHAT_DND,
                TextSpeechSettings.DEFAULT_RESPECT_WECHAT_DND
            )
        )
    }
    var quietEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_QUIET_ENABLE,
                TextSpeechSettings.DEFAULT_QUIET_ENABLE
            )
        )
    }
    var quietStart by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_QUIET_START, TextSpeechSettings.DEFAULT_QUIET_START)
                ?: TextSpeechSettings.DEFAULT_QUIET_START
        )
    }
    var quietEnd by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_QUIET_END, TextSpeechSettings.DEFAULT_QUIET_END)
                ?: TextSpeechSettings.DEFAULT_QUIET_END
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
            item { SmallTitle(text = "播报") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "自动播放语音",
                        summary = "播报允许名单内收到的消息"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(TextSpeechSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "语音引擎",
                            summary = "选择 TTS 播报使用的语音引擎",
                            options = ttsEngineOptions,
                            currentValue = ttsEngine,
                            onValueChanged = {
                                ttsEngine = it
                                ttsVoice = TextSpeechSettings.DEFAULT_TTS_VOICE
                                sp.edit()
                                    .putString(TextSpeechSettings.KEY_TTS_ENGINE, it)
                                    .putString(
                                        TextSpeechSettings.KEY_TTS_VOICE,
                                        TextSpeechSettings.DEFAULT_TTS_VOICE
                                    )
                                    .apply()
                            }
                        )
                        InsetDivider()
                        PopupChoiceRow(
                            title = "播报角色",
                            summary = when {
                                ttsVoiceLoading -> "正在读取所选引擎的播报角色"
                                ttsVoiceError.isNotBlank() -> "读取失败：$ttsVoiceError"
                                ttsFallbackEnginePackage.isNotBlank() ->
                                    "系统默认不可用，已临时使用 $ttsFallbackEngineLabel"
                                ttsVoiceChoices.isEmpty() -> "所选引擎未提供可选角色，将跟随引擎默认"
                                else -> "选择所选引擎提供的播报角色"
                            },
                            options = ttsVoiceOptions,
                            currentValue = ttsVoice,
                            enabled = !ttsVoiceLoading,
                            onValueChanged = {
                                ttsVoice = it
                                sp.edit().putString(TextSpeechSettings.KEY_TTS_VOICE, it).apply()
                            }
                        )
                        InsetDivider()
                        SwitchRow(
                            checked = playVoiceMessages,
                            title = "播放语音消息",
                            summary = "收到语音时直接播放原语音，不使用 TTS"
                        ) {
                            playVoiceMessages = it
                            sp.edit()
                                .putBoolean(TextSpeechSettings.KEY_PLAY_VOICE_MESSAGES, it)
                                .apply()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = volumeControl,
                            title = "音键控制",
                            summary = "音量减：暂停\n音量加：继续\n暂停时再按音量减：跳过"
                        ) {
                            volumeControl = it
                            sp.edit().putBoolean(TextSpeechSettings.KEY_VOLUME_CONTROL, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = customAnnouncement,
                            title = "自定义播报内容",
                            summary = "分别使用消息对应的昵称、群聊和正文变量"
                        ) {
                            customAnnouncement = it
                            sp.edit().putBoolean(TextSpeechSettings.KEY_ANNOUNCE_SENDER, it).apply()
                        }
                        if (customAnnouncement) {
                            InsetDivider()
                            VariableInputRow(
                                title = "播报内容格式",
                                summary = "点击下方中文变量插入到光标位置",
                                value = announcementTemplate,
                                variables = textSpeechTemplateVariables,
                                minLines = 3
                            ) {
                                announcementTemplate = it
                                sp.edit()
                                    .putString(TextSpeechSettings.KEY_ANNOUNCEMENT_TEMPLATE, it)
                                    .apply()
                            }
                            if (announcementTemplate.contains(TextSpeechSettings.VAR_MESSAGE_TIME)) {
                                InsetDivider()
                                InputRow(
                                    title = "消息时间格式",
                                    summary = if (TextSpeechSettings.isValidTimeFormat(timeFormat)) {
                                        "例如 HH:mm:ss 或 yyyy-MM-dd HH:mm:ss"
                                    } else {
                                        "格式无效，当前输入不会保存"
                                    },
                                    value = timeFormat
                                ) {
                                    timeFormat = it
                                    if (TextSpeechSettings.isValidTimeFormat(it)) {
                                        sp.edit().putString(
                                            TextSpeechSettings.KEY_TIME_FORMAT,
                                            TextSpeechSettings.normalizeTimeFormat(it)
                                        ).apply()
                                    }
                                }
                            }
                            InsetDivider()
                            ActionRow(
                                title = "恢复默认格式",
                                summary = TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
                            ) {
                                announcementTemplate = TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
                                timeFormat = TextSpeechSettings.DEFAULT_TIME_FORMAT
                                sp.edit()
                                    .putString(
                                        TextSpeechSettings.KEY_ANNOUNCEMENT_TEMPLATE,
                                        TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
                                    )
                                    .putString(
                                        TextSpeechSettings.KEY_TIME_FORMAT,
                                        TextSpeechSettings.DEFAULT_TIME_FORMAT
                                    )
                                    .apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = respectWechatDnd,
                            title = "免打扰不播报",
                            summary = "私聊或群聊开启微信消息免打扰后停止播报"
                        ) {
                            respectWechatDnd = it
                            sp.edit()
                                .putBoolean(TextSpeechSettings.KEY_RESPECT_WECHAT_DND, it)
                                .apply()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = quietEnabled,
                            title = "定时免打扰",
                            summary = "指定时段内暂停自动播报"
                        ) {
                            quietEnabled = it
                            sp.edit().putBoolean(TextSpeechSettings.KEY_QUIET_ENABLE, it).apply()
                        }
                        if (quietEnabled) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", quietStart) {
                                quietStart = it
                                sp.edit().putString(TextSpeechSettings.KEY_QUIET_START, it).apply()
                            }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", quietEnd) {
                                quietEnd = it
                                sp.edit().putString(TextSpeechSettings.KEY_QUIET_END, it).apply()
                            }
                        }
                        InsetDivider()
                        ActionRow(
                            title = "设置允许名单",
                            summary = if (allowedContacts.isEmpty()) {
                                "未设置"
                            } else {
                                "已选择 ${allowedContacts.size} 个聊天"
                            },
                            onClick = onPickContacts
                        )
                    }
                }
            }
        }
    }
}

private enum class ZombieCheckSelection {
    TARGETS,
    EXCLUDED,
    DELETE
}

private data class ZombieCheckDeleteTarget(val wxid: String, val name: String)

private sealed class ZombieCheckRoute {
    object Main : ZombieCheckRoute()
    data class ContactPicker(
        val selection: ZombieCheckSelection,
        val request: ContactPickerRequest
    ) : ZombieCheckRoute()
}

@Composable
internal fun ZombieCheckMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { ZombieCheckSettings(context) }
    var route by remember { mutableStateOf<ZombieCheckRoute>(ZombieCheckRoute.Main) }
    var targetIds by remember { mutableStateOf(settings.targetIds()) }
    var excludedIds by remember { mutableStateOf(settings.excludedIds()) }
    var pendingDeleteTargets by remember { mutableStateOf(emptyList<ZombieCheckDeleteTarget>()) }

    SettingsRouteTransition(
        targetState = route,
        label = "ZombieCheckRoute",
        depthOf = { if (it is ZombieCheckRoute.Main) 0 else 1 }
    ) { currentRoute ->
        when (currentRoute) {
            ZombieCheckRoute.Main -> ZombieCheckMainPage(
                context = context,
                provider = provider,
                targetIds = targetIds,
                excludedIds = excludedIds,
                pendingDeleteTargets = pendingDeleteTargets,
                onBack = onBack,
                onPickTargets = {
                    route = ZombieCheckRoute.ContactPicker(
                        ZombieCheckSelection.TARGETS,
                        ContactPickerRequest(
                            title = "选择检测好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = targetIds.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                },
                onPickExcluded = {
                    route = ZombieCheckRoute.ContactPicker(
                        ZombieCheckSelection.EXCLUDED,
                        ContactPickerRequest(
                            title = "选择排除好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = excludedIds.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                },
                onPickDelete = {
                    val detectedIds = ZombieCheckController.snapshot().results
                        .filter { it.type == ZombieCheckResultType.DEAD }
                        .map { it.wxid }
                        .toSet()
                    route = ZombieCheckRoute.ContactPicker(
                        ZombieCheckSelection.DELETE,
                        ContactPickerRequest(
                            title = "批量删除好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = detectedIds.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                },
                onDismissDelete = { pendingDeleteTargets = emptyList() },
                onConfirmDelete = { clearRecord, delaySeconds ->
                    val result = ZombieCheckController.deleteFriends(
                        pendingDeleteTargets.map { it.wxid },
                        clearRecord,
                        delaySeconds
                    )
                    pendingDeleteTargets = emptyList()
                    result
                }
            )
            is ZombieCheckRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = ZombieCheckRoute.Main },
                onConfirm = { picked ->
                    val ids = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    when (currentRoute.selection) {
                        ZombieCheckSelection.TARGETS -> {
                            targetIds = ids
                            settings.saveTargetIds(ids)
                        }
                        ZombieCheckSelection.EXCLUDED -> {
                            excludedIds = ids
                            settings.saveExcludedIds(ids)
                        }
                        ZombieCheckSelection.DELETE -> {
                            pendingDeleteTargets = picked.map {
                                ZombieCheckDeleteTarget(it.id, it.label.ifBlank { it.id })
                            }
                        }
                    }
                    route = ZombieCheckRoute.Main
                }
            )
        }
    }
}

@Composable
private fun ZombieCheckMainPage(
    context: Context,
    provider: FeatureSettingsProvider,
    targetIds: Set<String>,
    excludedIds: Set<String>,
    pendingDeleteTargets: List<ZombieCheckDeleteTarget>,
    onBack: () -> Unit,
    onPickTargets: () -> Unit,
    onPickExcluded: () -> Unit,
    onPickDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (Boolean, Int) -> ZombieCheckActionResult
) {
    val sp = remember { HchatStorage.preferences(context, ZombieCheckSettings.PREFS_NAME) }
    var snapshot by remember { mutableStateOf(ZombieCheckController.snapshot()) }
    var enabled by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_ENABLE, ZombieCheckSettings.DEFAULT_ENABLE)) }
    var autoTag by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_AUTO_TAG, ZombieCheckSettings.DEFAULT_AUTO_TAG)) }
    var labelName by remember { mutableStateOf(sp.getString(ZombieCheckSettings.KEY_LABEL_NAME, ZombieCheckSettings.DEFAULT_LABEL_NAME).orEmpty()) }
    var autoDelete by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_AUTO_DELETE, ZombieCheckSettings.DEFAULT_AUTO_DELETE)) }
    var clearRecord by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_CLEAR_RECORD, ZombieCheckSettings.DEFAULT_CLEAR_RECORD)) }
    var keepAwake by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_KEEP_AWAKE, ZombieCheckSettings.DEFAULT_KEEP_AWAKE)) }
    var minDelay by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_MIN_DELAY_SECONDS, ZombieCheckSettings.DEFAULT_MIN_DELAY_SECONDS).toString()) }
    var maxDelay by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_MAX_DELAY_SECONDS, ZombieCheckSettings.DEFAULT_MAX_DELAY_SECONDS).toString()) }
    var timeout by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_TIMEOUT_SECONDS, ZombieCheckSettings.DEFAULT_TIMEOUT_SECONDS).toString()) }
    var retries by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_MAX_RETRIES, ZombieCheckSettings.DEFAULT_MAX_RETRIES).toString()) }
    var deleteDelay by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_DELETE_DELAY_SECONDS, ZombieCheckSettings.DEFAULT_DELETE_DELAY_SECONDS).toString()) }
    var confirmAutoDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = ZombieCheckController.snapshot()
            delay(500L)
        }
    }

    fun saveInt(key: String, value: String, range: IntRange) {
        value.toIntOrNull()?.coerceIn(range.first, range.last)?.let {
            sp.edit().putInt(key, it).apply()
        }
    }

    fun toastResult(result: ZombieCheckActionResult) {
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        snapshot = ZombieCheckController.snapshot()
    }

    val notableResults = snapshot.results.filter { it.type != ZombieCheckResultType.NORMAL }.asReversed()

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
            item { SmallTitle(text = "检测") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用僵尸粉检测",
                        summary = "使用微信转账下单接口核验好友关系，不会确认付款",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_ENABLE, it).apply()
                            if (!it && (snapshot.running || snapshot.deleting)) {
                                toastResult(ZombieCheckController.pause())
                            }
                        }
                    )
                    InsetDivider()
                    InfoRow("状态", if (snapshot.ready) snapshot.status else "检测接口定位中")
                    InsetDivider()
                    InfoRow(
                        "进度",
                        "已检测 ${snapshot.checkedCount}/${snapshot.totalCount}，异常 ${snapshot.deadCount}，失败 ${snapshot.unknownCount}"
                    )
                    if (snapshot.deleting || snapshot.deleteTotalCount > 0) {
                        InsetDivider()
                        InfoRow(
                            "删除进度",
                            "已处理 ${snapshot.deleteCompletedCount}/${snapshot.deleteTotalCount}，已提交 ${snapshot.deleteSuccessCount}，失败 ${snapshot.deleteFailureCount}"
                        )
                    }
                    if (snapshot.currentName.isNotBlank()) {
                        InsetDivider()
                        InfoRow("当前好友", snapshot.currentName)
                    }
                    InsetDivider()
                    ActionRow("检测范围", if (targetIds.isEmpty()) "全部好友" else "已选择 ${targetIds.size} 位好友", onPickTargets)
                    InsetDivider()
                    ActionRow("排除好友", if (excludedIds.isEmpty()) "未排除" else "已排除 ${excludedIds.size} 位好友", onPickExcluded)
                    InsetDivider()
                    ActionRow(
                        if (snapshot.running || snapshot.deleting) "停止当前任务" else "开始 / 继续检测",
                        when {
                            snapshot.deleting -> "停止后不再删除剩余好友"
                            snapshot.running -> "暂停后保留当前队列"
                            else -> "按当前范围继续未完成进度"
                        }
                    ) {
                        toastResult(
                            if (snapshot.running || snapshot.deleting) {
                                ZombieCheckController.pause()
                            } else {
                                ZombieCheckController.start()
                            }
                        )
                    }
                    InsetDivider()
                    ActionRow("重置检测进度", "清空断点、结果和运行日志") {
                        toastResult(ZombieCheckController.reset())
                    }
                }
            }

            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "异常处理") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = autoTag,
                        title = "自动追加标签",
                        summary = "保留好友现有标签后追加指定标签",
                        onCheckedChange = {
                            autoTag = it
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_AUTO_TAG, it).apply()
                        }
                    )
                    if (autoTag) {
                        InsetDivider()
                        InputRow("标签名称", "检测到好友关系异常时追加", labelName) {
                            labelName = it
                            sp.edit().putString(ZombieCheckSettings.KEY_LABEL_NAME, it.trim()).apply()
                        }
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = autoDelete,
                        title = "自动删除异常好友",
                        summary = "高风险操作，默认关闭；暂停检测会取消尚未执行的删除",
                        onCheckedChange = {
                            if (it) {
                                confirmAutoDelete = true
                            } else {
                                autoDelete = false
                                sp.edit().putBoolean(ZombieCheckSettings.KEY_AUTO_DELETE, false).apply()
                            }
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = clearRecord,
                        title = "同时清理聊天记录",
                        summary = "同时用于自动删除和批量删除",
                        onCheckedChange = {
                            clearRecord = it
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_CLEAR_RECORD, it).apply()
                        }
                    )
                    InsetDivider()
                    NumberInputRow("删除间隔", "自动删除和批量删除的等待秒数，0-300", deleteDelay) {
                        deleteDelay = it
                        saveInt(ZombieCheckSettings.KEY_DELETE_DELAY_SECONDS, it, 0..300)
                    }
                    InsetDivider()
                    ActionRow(
                        "批量删除好友",
                        when {
                            snapshot.deleting -> "正在处理 ${snapshot.deleteCompletedCount}/${snapshot.deleteTotalCount}"
                            snapshot.deadCount > 0 -> "可按标签筛选，默认选中 ${snapshot.deadCount} 位异常好友"
                            else -> "从好友或微信标签中多选后删除"
                        }
                    ) {
                        if (snapshot.running || snapshot.deleting) {
                            Toast.makeText(context, "请先停止当前任务", Toast.LENGTH_SHORT).show()
                        } else {
                            onPickDelete()
                        }
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = keepAwake,
                        title = "检测期间保持 CPU 运行",
                        summary = "使用 WakeLock，长时间检测会增加耗电",
                        onCheckedChange = {
                            keepAwake = it
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_KEEP_AWAKE, it).apply()
                        }
                    )
                }
            }

            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "请求参数") }
            item {
                SettingsCard {
                    NumberInputRow("最小间隔", "单位秒，0-60", minDelay) {
                        minDelay = it
                        saveInt(ZombieCheckSettings.KEY_MIN_DELAY_SECONDS, it, 0..60)
                    }
                    InsetDivider()
                    NumberInputRow("最大间隔", "单位秒，0-120", maxDelay) {
                        maxDelay = it
                        saveInt(ZombieCheckSettings.KEY_MAX_DELAY_SECONDS, it, 0..120)
                    }
                    InsetDivider()
                    NumberInputRow("请求超时", "单位秒，5-60", timeout) {
                        timeout = it
                        saveInt(ZombieCheckSettings.KEY_TIMEOUT_SECONDS, it, 5..60)
                    }
                    InsetDivider()
                    NumberInputRow("超时重试", "每位好友最多重试 0-5 次", retries) {
                        retries = it
                        saveInt(ZombieCheckSettings.KEY_MAX_RETRIES, it, 0..5)
                    }
                }
            }

            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "异常结果 · ${notableResults.size}") }
            item {
                SettingsCard {
                    if (notableResults.isEmpty()) {
                        InfoRow("暂无异常结果", "正常好友不会逐项显示")
                    } else {
                        notableResults.take(50).forEachIndexed { index, result ->
                            if (index > 0) InsetDivider()
                            val resultType = if (result.type == ZombieCheckResultType.DEAD) "好友关系异常" else "检测失败"
                            val resultSummary = if (result.message.isBlank()) resultType else "$resultType · ${result.message}"
                            ZombieCheckResultRow(
                                result.name,
                                resultSummary
                            )
                        }
                    }
                }
            }

            if (snapshot.logs.isNotEmpty()) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行日志") }
                item {
                    SettingsCard {
                        snapshot.logs.takeLast(12).asReversed().forEachIndexed { index, log ->
                            if (index > 0) InsetDivider()
                            InfoRow("记录", log)
                        }
                    }
                }
            }
        }
    }

    if (confirmAutoDelete) {
        WindowDialog(
            show = true,
            title = "启用自动删除",
            onDismissRequest = { confirmAutoDelete = false },
            content = {
                Column {
                    Text(
                        text = "检测依据来自微信支付接口返回文案。支付风控或服务端文案变化可能产生检测失败，请先小范围验证结果。",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    TextButton(
                        text = "确认启用",
                        onClick = {
                            autoDelete = true
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_AUTO_DELETE, true).apply()
                            confirmAutoDelete = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "取消",
                        onClick = { confirmAutoDelete = false },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        )
    }

    if (pendingDeleteTargets.isNotEmpty()) {
        WindowDialog(
            show = true,
            title = "确认批量删除",
            onDismissRequest = onDismissDelete,
            content = {
                Column {
                    Text(
                        text = buildString {
                            append("将删除已选的 ${pendingDeleteTargets.size} 位好友")
                            if (clearRecord) append("，并同时清理聊天记录")
                            append("。此操作不可撤销。")
                            val names = pendingDeleteTargets.take(6).joinToString("、") { it.name }
                            if (names.isNotBlank()) append("\n\n$names")
                            if (pendingDeleteTargets.size > 6) append(" 等")
                        },
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    TextButton(
                        text = "确认删除",
                        onClick = {
                            val result = onConfirmDelete(
                                clearRecord,
                                deleteDelay.toIntOrNull()?.coerceIn(0, 300)
                                    ?: ZombieCheckSettings.DEFAULT_DELETE_DELAY_SECONDS
                            )
                            toastResult(result)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "取消",
                        onClick = onDismissDelete,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        )
    }
}

@Composable
private fun ZombieCheckResultRow(name: String, summary: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summary,
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

private sealed class QQMusicOrderRoute {
    object Main : QQMusicOrderRoute()
    data class ContactPicker(val request: ContactPickerRequest) : QQMusicOrderRoute()
}

@Composable
internal fun QQMusicOrderMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { QQMusicOrderSettings(context) }
    val sp = remember { HchatStorage.preferences(context, QQMusicOrderSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<QQMusicOrderRoute>(QQMusicOrderRoute.Main) }
    var allowedTalkers by remember { mutableStateOf(settings.allowedTalkers()) }

    SettingsRouteTransition(
        targetState = route,
        label = "QQMusicOrderRoute",
        depthOf = { if (it is QQMusicOrderRoute.Main) 0 else 1 }
    ) { currentRoute ->
        when (currentRoute) {
            QQMusicOrderRoute.Main -> QQMusicOrderMainPage(
                provider = provider,
                sp = sp,
                allowedTalkers = allowedTalkers,
                onBack = onBack,
                onPickTalkers = {
                    route = QQMusicOrderRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "允许他人点歌的聊天",
                            mode = ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = allowedTalkers.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is QQMusicOrderRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = QQMusicOrderRoute.Main },
                onConfirm = { picked ->
                    allowedTalkers = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    settings.saveAllowedTalkers(allowedTalkers)
                    Toast.makeText(context, "点歌范围已保存", Toast.LENGTH_SHORT).show()
                    route = QQMusicOrderRoute.Main
                }
            )
        }
    }
}

@Composable
private fun QQMusicOrderMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    allowedTalkers: Set<String>,
    onBack: () -> Unit,
    onPickTalkers: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var customSingerEnabled by remember {
        mutableStateOf(sp.getBoolean(QQMusicOrderSettings.KEY_CUSTOM_SINGER, QQMusicOrderSettings.DEFAULT_CUSTOM_SINGER))
    }
    var triggers by remember {
        mutableStateOf(sp.getString(QQMusicOrderSettings.KEY_TRIGGERS, QQMusicOrderSettings.DEFAULT_TRIGGERS).orEmpty())
    }
    var defaultSinger by remember {
        mutableStateOf(sp.getString(QQMusicOrderSettings.KEY_DEFAULT_SINGER, QQMusicOrderSettings.DEFAULT_SINGER).orEmpty())
    }
    var appId by remember {
        mutableStateOf(sp.getString(QQMusicOrderSettings.KEY_APP_ID, QQMusicOrderSettings.DEFAULT_APP_ID).orEmpty())
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
            item { SmallTitle(text = "点歌") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_ENABLE,
                        "启用 QQ 点歌",
                        "在聊天中响应点歌指令",
                        QQMusicOrderSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_INTERCEPT_OWN_COMMAND,
                        "拦截自己的点歌指令",
                        "点击发送后不发送原指令，直接执行点歌",
                        QQMusicOrderSettings.DEFAULT_INTERCEPT_OWN_COMMAND
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_SEND_AS_CARD,
                        "点歌发送卡片",
                        "开启后发送音乐卡片，可与歌曲语音同时发送",
                        QQMusicOrderSettings.DEFAULT_SEND_AS_CARD
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_SEND_AS_VOICE,
                        "点歌发送语音",
                        "开启后发送歌曲语音，可与音乐卡片同时发送",
                        QQMusicOrderSettings.DEFAULT_SEND_AS_VOICE
                    )
                    InsetDivider()
                    ActionRow(
                        "允许他人点歌的聊天",
                        if (allowedTalkers.isEmpty()) "未选择" else "已选 ${allowedTalkers.size} 个聊天",
                        onPickTalkers
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "指令") }
            item {
                SettingsCard {
                    InputRow("触发词", "多个触发词用逗号分隔", triggers) {
                        triggers = it
                        sp.edit().putString(QQMusicOrderSettings.KEY_TRIGGERS, it).commit()
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "音乐卡片") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_REPLACE_COVER_WITH_AVATAR,
                        "封面使用点歌人头像",
                        "头像不可用时保留歌曲封面",
                        QQMusicOrderSettings.DEFAULT_REPLACE_COVER_WITH_AVATAR
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_REPLACE_SINGER_WITH_NICKNAME,
                        "歌手使用点歌人昵称",
                        "昵称不可用时保留原歌手",
                        QQMusicOrderSettings.DEFAULT_REPLACE_SINGER_WITH_NICKNAME
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = customSingerEnabled,
                        title = "自定义 singer",
                        summary = "允许通过 & 指定歌手显示名称",
                        onCheckedChange = {
                            customSingerEnabled = it
                            sp.edit().putBoolean(QQMusicOrderSettings.KEY_CUSTOM_SINGER, it).commit()
                        }
                    )
                    if (customSingerEnabled) {
                        InsetDivider()
                        InputRow("默认 singer", "留空时使用原歌手或点歌人昵称", defaultSinger) {
                            defaultSinger = it
                            sp.edit().putString(QQMusicOrderSettings.KEY_DEFAULT_SINGER, it).commit()
                        }
                    }
                    InsetDivider()
                    InputRow("AppID", "留空时使用 QQ 音乐默认 AppID", appId) {
                        appId = it
                        sp.edit().putString(QQMusicOrderSettings.KEY_APP_ID, it).commit()
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordNotificationMainPage(
    context: Context,
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    keywords: List<KeywordRule>,
    excludeContacts: Set<String>,
    includeContacts: Set<String>,
    listState: LazyListState,
    onBack: () -> Unit,
    onOpenKeywords: () -> Unit,
    onOpenTemplates: () -> Unit,
    onPickContacts: (Boolean) -> Unit,
    onClearContacts: (Boolean) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    var filterMode by remember { mutableStateOf(sp.getBoolean(KeywordNotificationSettings.KEY_FILTER_MODE, KeywordNotificationSettings.DEFAULT_FILTER_MODE)) }
    var quietEnabled by remember {
        mutableStateOf(sp.getBoolean(KeywordNotificationSettings.KEY_QUIET, KeywordNotificationSettings.DEFAULT_QUIET))
    }
    var quietStart by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_QUIET_START, KeywordNotificationSettings.DEFAULT_QUIET_START) ?: KeywordNotificationSettings.DEFAULT_QUIET_START) }
    var quietEnd by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_QUIET_END, KeywordNotificationSettings.DEFAULT_QUIET_END) ?: KeywordNotificationSettings.DEFAULT_QUIET_END) }
    val lastTime = sp.getLong(KeywordNotificationSettings.KEY_LAST_TIME, 0L)
    val lastKeyword = sp.getString(KeywordNotificationSettings.KEY_LAST_KEYWORD, "").orEmpty()
    val activeContacts = if (filterMode) includeContacts else excludeContacts
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
            item { SmallTitle(text = "监控") }
            item {
                SettingsCard {
                    SwitchRow(sp, KeywordNotificationSettings.KEY_ENABLE, "启用关键词通知", "收到匹配消息时提醒", KeywordNotificationSettings.DEFAULT_ENABLE)
                    InsetDivider()
                    InfoRow("关键词", "${keywords.size} 个")
                    InsetDivider()
                    InfoRow("上次匹配", if (lastTime > 0) "${KeywordNotificationRuntime.formatLastTime(lastTime)} · $lastKeyword" else "暂无匹配记录")
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "关键词") }
            item {
                SettingsCard {
                    ActionRow("关键词管理", if (keywords.isEmpty()) "未添加关键词" else "${keywords.size} 个关键词") {
                        onOpenKeywords()
                    }
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_ANY_GROUP, "任意关键词-群聊通知", "群聊文字或引用消息都触发", KeywordNotificationSettings.DEFAULT_ANY_GROUP)
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_ANY_PRIVATE, "任意关键词-私聊通知", "私聊文字或引用消息都触发", KeywordNotificationSettings.DEFAULT_ANY_PRIVATE)
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "通知") }
            item {
                SettingsCard {
                    SwitchRow(sp, KeywordNotificationSettings.KEY_NOTIFY, "系统通知", "在通知栏显示提醒", KeywordNotificationSettings.DEFAULT_NOTIFY)
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_TOAST, "Toast 提示", "短暂弹出提示", KeywordNotificationSettings.DEFAULT_TOAST)
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_AT_ME, "@我通知", "群聊有人 @ 我时提醒", KeywordNotificationSettings.DEFAULT_AT_ME)
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_AT_ALL, "@所有人/群公告通知", "命中 @所有人 或群公告时提醒", KeywordNotificationSettings.DEFAULT_AT_ALL)
                    InsetDivider()
                    ActionRow("通知/Toast模板", "留空时使用内置默认模板") {
                        onOpenTemplates()
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "关键词通知") }
            item {
                KeywordNotificationAlertCard(
                    context = context,
                    sp = sp,
                    soundKey = KeywordNotificationSettings.KEY_KEYWORD_NOTIFY_SOUND,
                    vibrateKey = KeywordNotificationSettings.KEY_KEYWORD_NOTIFY_VIBRATE,
                    ringtoneKey = KeywordNotificationSettings.KEY_KEYWORD_NOTIFY_RINGTONE
                )
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "@我通知") }
            item {
                KeywordNotificationAlertCard(
                    context = context,
                    sp = sp,
                    soundKey = KeywordNotificationSettings.KEY_AT_ME_NOTIFY_SOUND,
                    vibrateKey = KeywordNotificationSettings.KEY_AT_ME_NOTIFY_VIBRATE,
                    ringtoneKey = KeywordNotificationSettings.KEY_AT_ME_NOTIFY_RINGTONE
                )
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "@所有人/群公告") }
            item {
                KeywordNotificationAlertCard(
                    context = context,
                    sp = sp,
                    soundKey = KeywordNotificationSettings.KEY_AT_ALL_NOTIFY_SOUND,
                    vibrateKey = KeywordNotificationSettings.KEY_AT_ALL_NOTIFY_VIBRATE,
                    ringtoneKey = KeywordNotificationSettings.KEY_AT_ALL_NOTIFY_RINGTONE
                )
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "生效范围") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = filterMode,
                        title = if (filterMode) "仅生效模式" else "排除模式",
                        summary = if (filterMode) "只处理名单里的聊天" else "不处理名单里的聊天"
                    ) {
                        filterMode = it
                        sp.edit().putBoolean(KeywordNotificationSettings.KEY_FILTER_MODE, it).apply()
                    }
                    InsetDivider()
                    ActionRow(
                        if (filterMode) "仅生效名单" else "排除名单",
                        if (activeContacts.isEmpty()) "未设置" else "${activeContacts.size} 个聊天"
                    ) {
                        onPickContacts(filterMode)
                    }
                    if (activeContacts.isNotEmpty()) {
                        InsetDivider()
                        ActionRow("清空当前名单", if (filterMode) "清空仅生效名单" else "清空排除名单") {
                            onClearContacts(filterMode)
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "免打扰") }
            item {
                SettingsCard {
                    SwitchRow(quietEnabled, "启用免打扰", "指定时间内不提醒") {
                        quietEnabled = it
                        sp.edit().putBoolean(KeywordNotificationSettings.KEY_QUIET, it).apply()
                    }
                    if (quietEnabled) {
                        InsetDivider()
                        TimeOfDayPickerRow("开始时间", quietStart) {
                            quietStart = it
                            sp.edit().putString(KeywordNotificationSettings.KEY_QUIET_START, quietStart).apply()
                        }
                        InsetDivider()
                        TimeOfDayPickerRow("结束时间", quietEnd) {
                            quietEnd = it
                            sp.edit().putString(KeywordNotificationSettings.KEY_QUIET_END, quietEnd).apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordNotificationAlertCard(
    context: Context,
    sp: SharedPreferences,
    soundKey: String,
    vibrateKey: String,
    ringtoneKey: String
) {
    var notifySound by remember(soundKey) {
        mutableStateOf(sp.getBoolean(soundKey, sp.getBoolean(KeywordNotificationSettings.KEY_NOTIFY_SOUND, KeywordNotificationSettings.DEFAULT_NOTIFY_SOUND)))
    }
    var notifyVibrate by remember(vibrateKey) {
        mutableStateOf(sp.getBoolean(vibrateKey, sp.getBoolean(KeywordNotificationSettings.KEY_NOTIFY_VIBRATE, KeywordNotificationSettings.DEFAULT_NOTIFY_VIBRATE)))
    }
    var notifyRingtone by remember(ringtoneKey) {
        mutableStateOf(sp.getString(ringtoneKey, sp.getString(KeywordNotificationSettings.KEY_NOTIFY_RINGTONE, "") ?: "").orEmpty())
    }
    SettingsCard {
        SwitchRow(
            checked = notifyVibrate,
            title = "通知震动",
            summary = "触发该类型通知时震动"
        ) {
            notifyVibrate = it
            sp.edit().putBoolean(vibrateKey, it).apply()
        }
        InsetDivider()
        SwitchRow(
            checked = notifySound,
            title = "通知铃声",
            summary = "触发该类型通知时播放系统或自定义铃声"
        ) {
            notifySound = it
            sp.edit().putBoolean(soundKey, it).apply()
        }
        if (notifySound) {
            InsetDivider()
            ActionRow("选择系统铃声", ringtoneDisplayName(context, notifyRingtone, RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM)) {
                val activity = context as? Activity
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开铃声选择器", Toast.LENGTH_SHORT).show()
                } else {
                    RingtonePickerBridge.launchSystem(activity, notifyRingtone) { picked ->
                        notifyRingtone = CustomNotificationRuntime.freezeRingtoneUri(context, picked)
                        sp.edit().putString(ringtoneKey, notifyRingtone).apply()
                        Toast.makeText(context, "铃声已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            InsetDivider()
            ActionRow("从文件选择铃声", if (notifyRingtone.isBlank()) "未选择" else ringtoneDisplayName(context, notifyRingtone, RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM)) {
                val activity = context as? Activity
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                } else {
                    RingtonePickerBridge.launchFile(activity) { picked ->
                        notifyRingtone = CustomNotificationRuntime.freezeRingtoneUri(context, picked)
                        sp.edit().putString(ringtoneKey, notifyRingtone).apply()
                        Toast.makeText(context, "铃声已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (notifyRingtone.isNotBlank()) {
                InsetDivider()
                ActionRow("清空铃声", "恢复跟随系统") {
                    notifyRingtone = ""
                    sp.edit().putString(ringtoneKey, "").apply()
                    Toast.makeText(context, "铃声已清空", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun KeywordNotificationKeywordListPage(
    keywords: List<KeywordRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (KeywordRule) -> Unit,
    onClear: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lower = query.trim().lowercase(Locale.US)
    val visible = keywords.filter {
        lower.isBlank() || it.keyword.lowercase(Locale.US).contains(lower)
    }
    PageScaffold(
        title = "关键词管理",
        largeTitle = "关键词管理",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "添加关键词",
                onPrimaryClick = onAdd,
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
            item {
                SettingsCard {
                    InputRow("搜索", "关键词", query, onValueChange = onQueryChange)
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = if (visible.isEmpty()) "关键词" else "关键词 · ${visible.size} 个") }
            when {
                keywords.isEmpty() -> item { SettingsCard { EmptyText("暂无关键词，点击底部“添加关键词”。") } }
                visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                else -> visible.forEach { rule ->
                    item {
                        SettingsCard {
                            ActionRow(rule.keyword, if (rule.wholeWord) "全字匹配" else "模糊匹配") {
                                onEdit(rule)
                            }
                        }
                    }
                }
            }
            if (keywords.isNotEmpty()) {
                item {
                    SettingsCard {
                        ActionRow("清空所有关键词", "移除全部关键词") {
                            onClear()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordNotificationKeywordEditorPage(
    initial: KeywordRule?,
    onBack: () -> Unit,
    onSave: (KeywordRule) -> Unit,
    onDelete: (() -> Unit)?
) {
    var keyword by remember(initial) { mutableStateOf(initial?.keyword.orEmpty()) }
    var wholeWord by remember(initial) { mutableStateOf(initial?.wholeWord ?: false) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = if (initial == null) "添加关键词" else "编辑关键词",
        largeTitle = if (initial == null) "添加关键词" else "编辑关键词",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = { onSave(KeywordRule(keyword.trim(), wholeWord)) },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "关键词") }
            item {
                SettingsCard {
                    InputRow("关键词", "输入要监控的关键词", keyword) { keyword = it }
                    InsetDivider()
                    SwitchRow(wholeWord, "全字匹配", "关闭为模糊匹配，包含关键词即可触发") { wholeWord = it }
                }
            }
            if (onDelete != null) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除关键词", "移除该关键词") {
                            onDelete()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordNotificationTemplatePage(
    context: Context,
    sp: SharedPreferences,
    onBack: () -> Unit
) {
    var keywordTitle by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_KEYWORD_TITLE, "").orEmpty()) }
    var keywordContent by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_KEYWORD_CONTENT, "").orEmpty()) }
    var keywordToast by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_KEYWORD_TOAST, "").orEmpty()) }
    var atMeTitle by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ME_TITLE, "").orEmpty()) }
    var atMeContent by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ME_CONTENT, "").orEmpty()) }
    var atMeToast by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ME_TOAST, "").orEmpty()) }
    var atAllTitle by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ALL_TITLE, "").orEmpty()) }
    var atAllContent by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ALL_CONTENT, "").orEmpty()) }
    var atAllToast by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ALL_TOAST, "").orEmpty()) }
    val scrollBehavior = MiuixScrollBehavior()
    fun save() {
        sp.edit()
            .putString(KeywordNotificationSettings.KEY_KEYWORD_TITLE, keywordTitle)
            .putString(KeywordNotificationSettings.KEY_KEYWORD_CONTENT, keywordContent)
            .putString(KeywordNotificationSettings.KEY_KEYWORD_TOAST, keywordToast)
            .putString(KeywordNotificationSettings.KEY_AT_ME_TITLE, atMeTitle)
            .putString(KeywordNotificationSettings.KEY_AT_ME_CONTENT, atMeContent)
            .putString(KeywordNotificationSettings.KEY_AT_ME_TOAST, atMeToast)
            .putString(KeywordNotificationSettings.KEY_AT_ALL_TITLE, atAllTitle)
            .putString(KeywordNotificationSettings.KEY_AT_ALL_CONTENT, atAllContent)
            .putString(KeywordNotificationSettings.KEY_AT_ALL_TOAST, atAllToast)
            .apply()
        Toast.makeText(context, "模板已保存", Toast.LENGTH_SHORT).show()
        onBack()
    }
    PageScaffold(
        title = "自定义文字",
        largeTitle = "自定义文字",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = ::save,
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = "恢复默认",
                onMiddleClick = {
                    keywordTitle = ""
                    keywordContent = ""
                    keywordToast = ""
                    atMeTitle = ""
                    atMeContent = ""
                    atMeToast = ""
                    atAllTitle = ""
                    atAllContent = ""
                    atAllToast = ""
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "关键词通知") }
            item {
                SettingsCard {
                    VariableInputRow("通知标题模板", "默认：${KeywordNotificationSettings.DEFAULT_KEYWORD_TITLE}", keywordTitle, keywordNotificationTemplateVariables) { keywordTitle = it }
                    InsetDivider()
                    VariableInputRow("通知内容模板", "默认：${KeywordNotificationSettings.DEFAULT_KEYWORD_CONTENT}", keywordContent, keywordNotificationTemplateVariables, minLines = 3) { keywordContent = it }
                    InsetDivider()
                    VariableInputRow("Toast 文字模板", "默认：${KeywordNotificationSettings.DEFAULT_KEYWORD_TOAST}", keywordToast, keywordNotificationTemplateVariables) { keywordToast = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "@我通知") }
            item {
                SettingsCard {
                    VariableInputRow("通知标题模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ME_TITLE}", atMeTitle, keywordNotificationTemplateVariables) { atMeTitle = it }
                    InsetDivider()
                    VariableInputRow("通知内容模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ME_CONTENT}", atMeContent, keywordNotificationTemplateVariables, minLines = 3) { atMeContent = it }
                    InsetDivider()
                    VariableInputRow("Toast 文字模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ME_TOAST}", atMeToast, keywordNotificationTemplateVariables) { atMeToast = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "@所有人/群公告通知") }
            item {
                SettingsCard {
                    VariableInputRow("通知标题模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ALL_TITLE}", atAllTitle, keywordNotificationTemplateVariables) { atAllTitle = it }
                    InsetDivider()
                    VariableInputRow("通知内容模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ALL_CONTENT}", atAllContent, keywordNotificationTemplateVariables, minLines = 3) { atAllContent = it }
                    InsetDivider()
                    VariableInputRow("Toast 文字模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ALL_TOAST}", atAllToast, keywordNotificationTemplateVariables) { atAllToast = it }
                }
            }
        }
    }
}

@Composable
internal fun QuoteDeleteClearMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuoteDeleteClearSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天输入框") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuoteDeleteClearSettings.KEY_ENABLE,
                        "删除键清引用",
                        "输入框为空且已引用消息时，按输入法删除键直接取消引用",
                        QuoteDeleteClearSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmojiSaveMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, EmojiSaveSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天表情") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        EmojiSaveSettings.KEY_ENABLE,
                        "保存表情",
                        "长按聊天表情后显示保存入口",
                        EmojiSaveSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun SwipeQuoteMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, SwipeQuoteSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "引用") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SwipeQuoteSettings.KEY_ENABLE,
                        "左滑引用回复",
                        "左滑消息后调用微信原生引用入口",
                        SwipeQuoteSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "复读触发方式") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SwipeQuoteSettings.KEY_REPEAT_ENABLE,
                        "右滑复读",
                        "右滑消息后复读到当前聊天",
                        SwipeQuoteSettings.DEFAULT_REPEAT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        SwipeQuoteSettings.KEY_REPEAT_MENU_ENABLE,
                        "长按菜单复读",
                        "长按消息后点击 +1 复读到当前聊天",
                        SwipeQuoteSettings.DEFAULT_REPEAT_MENU_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun FakeVoiceDurationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FakeVoiceDurationSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                FakeVoiceDurationSettings.KEY_ENABLE,
                FakeVoiceDurationSettings.DEFAULT_ENABLE
            )
        )
    }
    var durationSeconds by remember {
        mutableStateOf(
            sp.getInt(
                FakeVoiceDurationSettings.KEY_DURATION_SECONDS,
                FakeVoiceDurationSettings.DEFAULT_DURATION_SECONDS
            ).toString()
        )
    }

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
            item { SmallTitle(text = "聊天语音") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "伪造语音时长",
                        summary = "发送语音时使用自定义显示时长",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(FakeVoiceDurationSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    if (enabled) {
                        InsetDivider()
                        NumberInputRow("显示时长", "单位秒，1-60", durationSeconds) { next ->
                            val value = next.toIntOrNull()
                            durationSeconds = value?.coerceIn(
                                FakeVoiceDurationSettings.MIN_DURATION_SECONDS,
                                FakeVoiceDurationSettings.MAX_DURATION_SECONDS
                            )?.toString() ?: next
                            if (value == null) return@NumberInputRow
                            sp.edit().putInt(
                                FakeVoiceDurationSettings.KEY_DURATION_SECONDS,
                                value.coerceIn(
                                    FakeVoiceDurationSettings.MIN_DURATION_SECONDS,
                                    FakeVoiceDurationSettings.MAX_DURATION_SECONDS
                                )
                            ).apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VoicePreviewMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, VoicePreviewSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天语音") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        VoicePreviewSettings.KEY_ENABLE,
                        "语音消息预览",
                        "长按聊天语音后查看时长并播放",
                        VoicePreviewSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun FakeMiniProgramBaseLibMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FakeMiniProgramBaseLibSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                FakeMiniProgramBaseLibSettings.KEY_ENABLE,
                FakeMiniProgramBaseLibSettings.DEFAULT_ENABLE
            )
        )
    }
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
            item { SmallTitle(text = "小程序") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "兼容低版本小程序",
                        summary = "主进程即时生效；小程序进程需重启微信后生效",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(FakeMiniProgramBaseLibSettings.KEY_ENABLE, it).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SkipMiniProgramVideoAdsMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember {
        HchatStorage.preferences(context, SkipMiniProgramVideoAdsSettings.PREFS_NAME)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                SkipMiniProgramVideoAdsSettings.KEY_ENABLE,
                SkipMiniProgramVideoAdsSettings.DEFAULT_ENABLE
            )
        )
    }
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
            item { SmallTitle(text = "小程序") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "跳过小程序视频广告",
                        summary = "修改后重启微信，使小程序进程重新加载设置",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(
                                SkipMiniProgramVideoAdsSettings.KEY_ENABLE,
                                it
                            ).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SkipGlobalMiniProgramSplashAdsMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember {
        HchatStorage.preferences(context, SkipGlobalMiniProgramSplashAdsSettings.PREFS_NAME)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                SkipGlobalMiniProgramSplashAdsSettings.KEY_ENABLE,
                SkipGlobalMiniProgramSplashAdsSettings.DEFAULT_ENABLE
            )
        )
    }
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
            item { SmallTitle(text = "小程序") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "跳过全局小程序开屏广告",
                        summary = "阻止所有小程序展示启动开屏广告，修改后需重启微信",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(
                                SkipGlobalMiniProgramSplashAdsSettings.KEY_ENABLE,
                                it
                            ).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FakeLocationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FakeLocationSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(FakeLocationSettings.KEY_ENABLE, FakeLocationSettings.DEFAULT_ENABLE))
    }
    var latitude by remember {
        mutableStateOf(sp.getString(FakeLocationSettings.KEY_LATITUDE, FakeLocationSettings.DEFAULT_LATITUDE).orEmpty())
    }
    var longitude by remember {
        mutableStateOf(sp.getString(FakeLocationSettings.KEY_LONGITUDE, FakeLocationSettings.DEFAULT_LONGITUDE).orEmpty())
    }
    var showManualInput by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun saveLocation(lat: Double, lon: Double) {
        latitude = formatFakeLocationCoordinate(lat)
        longitude = formatFakeLocationCoordinate(lon)
        sp.edit()
            .putString(FakeLocationSettings.KEY_LATITUDE, latitude)
            .putString(FakeLocationSettings.KEY_LONGITUDE, longitude)
            .apply()
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
            item { SmallTitle(text = "定位") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用虚拟定位",
                        summary = "微信主进程即时生效；小程序进程需重启微信后生效",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(FakeLocationSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    InsetDivider()
                    ActionRow("搜索并选择位置", "打开微信选择位置页，可搜索地点") {
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开微信地图", Toast.LENGTH_SHORT).show()
                        } else {
                            NativeFakeLocationPickerBridge.launch(activity) { lat, lon ->
                                saveLocation(lat, lon)
                                Toast.makeText(context, "已保存虚拟定位", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    InsetDivider()
                    ActionRow("手动输入经纬度", "纬度 -90~90，经度 -180~180") {
                        showManualInput = true
                    }
                    InsetDivider()
                    InfoRow("当前纬度", latitude) {
                        copyFakeLocationCoordinate(context, "Hchat 纬度", latitude)
                    }
                    InsetDivider()
                    InfoRow("当前经度", longitude) {
                        copyFakeLocationCoordinate(context, "Hchat 经度", longitude)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "说明") }
            item {
                SettingsCard {
                    InfoRow("生效范围", "微信和小程序定位")
                }
            }
        }
    }

    if (showManualInput) {
        FakeLocationManualInputDialog(
            initialLatitude = latitude,
            initialLongitude = longitude,
            onDismiss = { showManualInput = false },
            onConfirm = { lat, lon ->
                saveLocation(lat, lon)
                showManualInput = false
                Toast.makeText(context, "已保存虚拟定位", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
internal fun GameEmojiMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, GameEmojiSettings.PREFS_NAME) }
    var fixedResult by remember {
        mutableStateOf(
            sp.getBoolean(
                GameEmojiSettings.KEY_FIXED_RESULT,
                GameEmojiSettings.DEFAULT_FIXED_RESULT
            )
        )
    }
    var pickBeforeSend by remember {
        mutableStateOf(
            sp.getBoolean(
                GameEmojiSettings.KEY_PICK_BEFORE_SEND,
                GameEmojiSettings.DEFAULT_PICK_BEFORE_SEND
            )
        )
    }
    var diceResult by remember {
        mutableStateOf(
            sp.getInt(GameEmojiSettings.KEY_DICE_RESULT, GameEmojiSettings.DEFAULT_DICE_RESULT)
                .coerceIn(1, 6)
        )
    }
    var rpsResult by remember {
        mutableStateOf(
            sp.getInt(GameEmojiSettings.KEY_RPS_RESULT, GameEmojiSettings.DEFAULT_RPS_RESULT)
                .coerceIn(GameEmojiSettings.RPS_SCISSORS, GameEmojiSettings.RPS_PAPER)
        )
    }
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
            item { SmallTitle(text = "发送方式") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = fixedResult,
                        title = "使用固定结果",
                        summary = "直接发送下方设置的骰子点数或猜拳结果",
                        onCheckedChange = { checked ->
                            fixedResult = checked
                            if (checked) pickBeforeSend = false
                            sp.edit()
                                .putBoolean(GameEmojiSettings.KEY_FIXED_RESULT, checked)
                                .putBoolean(
                                    GameEmojiSettings.KEY_PICK_BEFORE_SEND,
                                    if (checked) false else pickBeforeSend
                                )
                                .apply()
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = pickBeforeSend,
                        title = "发送时选择",
                        summary = "每次发送骰子或猜拳前弹出结果选择",
                        onCheckedChange = { checked ->
                            pickBeforeSend = checked
                            if (checked) fixedResult = false
                            sp.edit()
                                .putBoolean(GameEmojiSettings.KEY_PICK_BEFORE_SEND, checked)
                                .putBoolean(
                                    GameEmojiSettings.KEY_FIXED_RESULT,
                                    if (checked) false else fixedResult
                                )
                                .apply()
                        }
                    )
                }
            }
            if (fixedResult) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "固定结果") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "骰子点数",
                            summary = "$diceResult 点",
                            options = (1..6).map { OptionItem("$it 点", it, "") },
                            currentValue = diceResult,
                            onValueChanged = { value ->
                                diceResult = value
                                sp.edit().putInt(GameEmojiSettings.KEY_DICE_RESULT, value).apply()
                            }
                        )
                        InsetDivider()
                        PopupOptionRow(
                            title = "猜拳结果",
                            summary = GameEmojiSettings.rpsLabel(rpsResult),
                            options = listOf(
                                OptionItem("剪刀", GameEmojiSettings.RPS_SCISSORS, ""),
                                OptionItem("石头", GameEmojiSettings.RPS_ROCK, ""),
                                OptionItem("布", GameEmojiSettings.RPS_PAPER, "")
                            ),
                            currentValue = rpsResult,
                            onValueChanged = { value ->
                                rpsResult = value
                                sp.edit().putInt(GameEmojiSettings.KEY_RPS_RESULT, value).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FakeLocationManualInputDialog(
    initialLatitude: String,
    initialLongitude: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var latitude by remember(initialLatitude) { mutableStateOf(initialLatitude) }
    var longitude by remember(initialLongitude) { mutableStateOf(initialLongitude) }
    val parsed = validFakeLocation(latitude.toDoubleOrNull(), longitude.toDoubleOrNull())
    WindowDialog(
        show = true,
        title = "手动输入经纬度",
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                InputRow("纬度", "范围 -90 到 90", latitude) { latitude = cleanCoordinateInput(it) }
                InputRow("经度", "范围 -180 到 180", longitude) { longitude = cleanCoordinateInput(it) }
                if (parsed == null) {
                    Text(
                        text = "请输入有效的纬度和经度",
                        color = Color(0xFFD93025),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "保存",
                        onClick = { parsed?.let { onConfirm(it.first, it.second) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

private fun cleanCoordinateInput(value: String): String {
    return value.filterIndexed { index, char ->
        char.isDigit() || char == '.' || (char == '-' && index == 0)
    }
}

internal fun validFakeLocation(latitude: Double?, longitude: Double?): Pair<Double, Double>? {
    if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return latitude to longitude
}

internal fun parseNativeFakeLocation(text: String): Pair<Double, Double>? {
    val latitude = Regex("""(?:lat|latitude)\s*[:= ]\s*([-+]?[0-9]*\.?[0-9]+)""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    val longitude = Regex("""(?:lng|lon|longitude)\s*[:= ]\s*([-+]?[0-9]*\.?[0-9]+)""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    return validFakeLocation(latitude, longitude)
}

private fun formatFakeLocationCoordinate(value: Double): String {
    return String.format(Locale.US, "%.6f", value)
}

private fun copyFakeLocationCoordinate(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun MessageBubbleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MessageBubbleSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(MessageBubbleSettings.KEY_ENABLE, MessageBubbleSettings.DEFAULT_ENABLE))
    }
    var separateDarkMode by remember {
        mutableStateOf(
            sp.getBoolean(
                MessageBubbleSettings.KEY_SEPARATE_DARK_MODE,
                MessageBubbleSettings.DEFAULT_SEPARATE_DARK_MODE
            )
        )
    }
    var revision by remember { mutableStateOf(0) }
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
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用消息气泡",
                        summary = "使用本地图片替换微信原生消息气泡",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(MessageBubbleSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    if (enabled) {
                        InsetDivider()
                        SwitchRow(
                            checked = separateDarkMode,
                            title = "深色模式单独设置",
                            summary = "未设置深色气泡时自动沿用对应浅色气泡",
                            onCheckedChange = {
                                separateDarkMode = it
                                sp.edit()
                                    .putBoolean(MessageBubbleSettings.KEY_SEPARATE_DARK_MODE, it)
                                    .apply()
                            }
                        )
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "浅色模式") }
                item {
                    SettingsCard {
                        MessageBubbleAssetRow(context, MessageBubbleSlot.LEFT_LIGHT, revision) { revision++ }
                        InsetDivider()
                        MessageBubbleAssetRow(context, MessageBubbleSlot.RIGHT_LIGHT, revision) { revision++ }
                    }
                }
                if (separateDarkMode) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "深色模式") }
                    item {
                        SettingsCard {
                            MessageBubbleAssetRow(context, MessageBubbleSlot.LEFT_DARK, revision) { revision++ }
                            InsetDivider()
                            MessageBubbleAssetRow(context, MessageBubbleSlot.RIGHT_DARK, revision) { revision++ }
                        }
                    }
                }
                MessageBubbleKind.values()
                    .filter { it != MessageBubbleKind.GENERAL }
                    .forEach { kind ->
                        item {
                            SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "${kind.displayName}气泡")
                        }
                        item {
                            SettingsCard {
                                if (kind == MessageBubbleKind.SYSTEM) {
                                    MessageBubbleAssetRow(
                                        context,
                                        MessageBubbleSlot.resolve(kind, false, false),
                                        revision
                                    ) { revision++ }
                                } else {
                                    MessageBubbleAssetRow(
                                        context,
                                        MessageBubbleSlot.resolve(kind, false, false),
                                        revision
                                    ) { revision++ }
                                    InsetDivider()
                                    MessageBubbleAssetRow(
                                        context,
                                        MessageBubbleSlot.resolve(kind, true, false),
                                        revision
                                    ) { revision++ }
                                }
                            }
                        }
                        if (separateDarkMode) {
                            item {
                                SettingsCard {
                                    if (kind == MessageBubbleKind.SYSTEM) {
                                        MessageBubbleAssetRow(
                                            context,
                                            MessageBubbleSlot.resolve(kind, false, true),
                                            revision
                                        ) { revision++ }
                                    } else {
                                        MessageBubbleAssetRow(
                                            context,
                                            MessageBubbleSlot.resolve(kind, false, true),
                                            revision
                                        ) { revision++ }
                                        InsetDivider()
                                        MessageBubbleAssetRow(
                                            context,
                                            MessageBubbleSlot.resolve(kind, true, true),
                                            revision
                                        ) { revision++ }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun MessageBubbleAssetRow(
    context: Context,
    slot: MessageBubbleSlot,
    revision: Int,
    onChanged: () -> Unit
) {
    val selected = remember(slot, revision) { MessageBubbleStore.hasAsset(context, slot) }
    val emptySummary = when {
        slot.kind == MessageBubbleKind.GENERAL && slot.darkMode -> "未选择，沿用对应浅色气泡"
        slot.kind == MessageBubbleKind.GENERAL -> "未选择，保持微信原生气泡"
        slot.darkMode -> "未选择，沿用同类浅色气泡"
        else -> "未选择，保持微信原生气泡"
    }
    ActionRow(
        title = slot.displayName,
        summary = if (selected) "已选择，点击更换或恢复" else emptySummary
    ) {
        val activity = context as? Activity ?: return@ActionRow
        val pickAsset = {
            MessageBubblePicker.launch(activity, slot) { result ->
                when (result) {
                    MessageBubblePickResult.SAVED -> {
                        onChanged()
                        Toast.makeText(context, "${slot.displayName}已更新", Toast.LENGTH_SHORT).show()
                    }
                    MessageBubblePickResult.FAILED -> {
                        Toast.makeText(context, "气泡图片无效或读取失败", Toast.LENGTH_SHORT).show()
                    }
                    MessageBubblePickResult.CANCELLED -> Unit
                }
            }
        }
        if (!selected) {
            pickAsset()
        } else {
            VoiceForwardMiuixDialog.showChoices(
                activity = activity,
                title = slot.displayName,
                summary = "管理当前气泡图片",
                choices = listOf(
                    "更换气泡" to "重新选择一张本地图片",
                    "恢复微信气泡" to "删除当前自定义气泡"
                ),
                onSelected = { choice ->
                    if (choice == 0) {
                        pickAsset()
                    } else if (MessageBubbleStore.remove(context, slot)) {
                        onChanged()
                        Toast.makeText(context, "已恢复微信气泡", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {}
            )
        }
    }
}

@Composable
internal fun MessageTextColorMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MessageTextColorSettings.PREFS_NAME) }
    var leftLightColor by remember {
        mutableStateOf(
            sp.getString(
                MessageTextColorSettings.KEY_LEFT_LIGHT_COLOR,
                MessageTextColorSettings.DEFAULT_LEFT_LIGHT_COLOR
            ) ?: MessageTextColorSettings.DEFAULT_LEFT_LIGHT_COLOR
        )
    }
    var rightLightColor by remember {
        mutableStateOf(
            sp.getString(
                MessageTextColorSettings.KEY_RIGHT_LIGHT_COLOR,
                MessageTextColorSettings.DEFAULT_RIGHT_LIGHT_COLOR
            ) ?: MessageTextColorSettings.DEFAULT_RIGHT_LIGHT_COLOR
        )
    }
    var leftDarkColor by remember {
        mutableStateOf(
            sp.getString(
                MessageTextColorSettings.KEY_LEFT_DARK_COLOR,
                MessageTextColorSettings.DEFAULT_LEFT_DARK_COLOR
            ) ?: MessageTextColorSettings.DEFAULT_LEFT_DARK_COLOR
        )
    }
    var rightDarkColor by remember {
        mutableStateOf(
            sp.getString(
                MessageTextColorSettings.KEY_RIGHT_DARK_COLOR,
                MessageTextColorSettings.DEFAULT_RIGHT_DARK_COLOR
            ) ?: MessageTextColorSettings.DEFAULT_RIGHT_DARK_COLOR
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
                primaryText = "保存设置",
                onPrimaryClick = {
                    val leftLight = MessageTextColorSettings.cleanColorSpec(leftLightColor)
                        .ifEmpty { MessageTextColorSettings.DEFAULT_LEFT_LIGHT_COLOR }
                    val rightLight = MessageTextColorSettings.cleanColorSpec(rightLightColor)
                        .ifEmpty { MessageTextColorSettings.DEFAULT_RIGHT_LIGHT_COLOR }
                    val leftDark = MessageTextColorSettings.cleanColorSpec(leftDarkColor)
                        .ifEmpty { MessageTextColorSettings.DEFAULT_LEFT_DARK_COLOR }
                    val rightDark = MessageTextColorSettings.cleanColorSpec(rightDarkColor)
                        .ifEmpty { MessageTextColorSettings.DEFAULT_RIGHT_DARK_COLOR }
                    sp.edit()
                        .putString(MessageTextColorSettings.KEY_LEFT_LIGHT_COLOR, leftLight)
                        .putString(MessageTextColorSettings.KEY_RIGHT_LIGHT_COLOR, rightLight)
                        .putString(MessageTextColorSettings.KEY_LEFT_DARK_COLOR, leftDark)
                        .putString(MessageTextColorSettings.KEY_RIGHT_DARK_COLOR, rightDark)
                        .apply()
                    leftLightColor = leftLight
                    rightLightColor = rightLight
                    leftDarkColor = leftDark
                    rightDarkColor = rightDark
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                },
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
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageTextColorSettings.KEY_ENABLE,
                        "启用消息文本颜色",
                        "处理聊天里的文本消息和引用消息正文",
                        MessageTextColorSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "颜色") }
            item {
                SettingsCard {
                    ColorPickerRow(
                        "左侧浅色",
                        "对方文本消息，浅色模式",
                        leftLightColor,
                        onReset = { leftLightColor = MessageTextColorSettings.DEFAULT_LEFT_LIGHT_COLOR }
                    ) { leftLightColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow(
                        "右侧浅色",
                        "自己发送文本消息，浅色模式",
                        rightLightColor,
                        onReset = { rightLightColor = MessageTextColorSettings.DEFAULT_RIGHT_LIGHT_COLOR }
                    ) { rightLightColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow(
                        "左侧深色",
                        "对方文本消息，深色模式",
                        leftDarkColor,
                        onReset = { leftDarkColor = MessageTextColorSettings.DEFAULT_LEFT_DARK_COLOR }
                    ) { leftDarkColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow(
                        "右侧深色",
                        "自己发送文本消息，深色模式",
                        rightDarkColor,
                        onReset = { rightDarkColor = MessageTextColorSettings.DEFAULT_RIGHT_DARK_COLOR }
                    ) { rightDarkColor = it.take(19) }
                }
            }
        }
    }
}

@Composable
internal fun ChatTimeStyleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, ChatTimeStyleSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                ChatTimeStyleSettings.KEY_ENABLE,
                ChatTimeStyleSettings.DEFAULT_ENABLE
            )
        )
    }
    var mode by remember {
        mutableStateOf(
            ChatTimeStyleSettings.normalizeMode(
                sp.getString(ChatTimeStyleSettings.KEY_MODE, ChatTimeStyleSettings.DEFAULT_MODE)
            )
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                ChatTimeStyleSettings.KEY_TIME_FORMAT,
                ChatTimeStyleSettings.DEFAULT_TIME_FORMAT
            ).orEmpty().ifBlank { ChatTimeStyleSettings.DEFAULT_TIME_FORMAT }
        )
    }
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
            item { SmallTitle(text = "聊天时间") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用会话时间样式",
                        summary = "关闭后完全交给微信原生处理",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(ChatTimeStyleSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    if (enabled) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "显示方式",
                            summary = chatTimeModeLabel(mode),
                            options = chatTimeModeChoices(),
                            currentValue = mode,
                            onValueChanged = {
                                mode = ChatTimeStyleSettings.normalizeMode(it)
                                sp.edit().putString(ChatTimeStyleSettings.KEY_MODE, mode).apply()
                            }
                        )
                        if (mode == ChatTimeStyleSettings.MODE_CUSTOM ||
                            mode == ChatTimeStyleSettings.MODE_EVERY
                        ) {
                            InsetDivider()
                            InputRow(
                                title = "时间格式",
                                summary = "例如 yyyy-MM-dd HH:mm:ss",
                                value = timeFormat,
                                onValueChange = {
                                    timeFormat = it
                                    sp.edit()
                                        .putString(ChatTimeStyleSettings.KEY_TIME_FORMAT, it)
                                        .apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun chatTimeModeChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("微信原样", ChatTimeStyleSettings.MODE_ORIGINAL),
    PopupChoice("自定义", ChatTimeStyleSettings.MODE_CUSTOM),
    PopupChoice("每条消息都显示", ChatTimeStyleSettings.MODE_EVERY),
    PopupChoice("隐藏", ChatTimeStyleSettings.MODE_HIDDEN)
)

private fun chatTimeModeLabel(mode: String): String = when (mode) {
    ChatTimeStyleSettings.MODE_CUSTOM -> "自定义格式（微信原生间隔）"
    ChatTimeStyleSettings.MODE_EVERY -> "每条消息都显示时间"
    ChatTimeStyleSettings.MODE_HIDDEN -> "隐藏微信原生聊天时间"
    else -> "保持微信原样（间隔显示）"
}

@Composable
internal fun HideChatAvatarMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, HideChatAvatarSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天头像") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        HideChatAvatarSettings.KEY_HIDE_SELF,
                        "隐藏自己的头像",
                        "在群聊和私聊中隐藏自己发送消息的头像",
                        HideChatAvatarSettings.DEFAULT_HIDE_SELF
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        HideChatAvatarSettings.KEY_HIDE_OTHER,
                        "隐藏对方的头像",
                        "在群聊和私聊中隐藏对方发送消息的头像",
                        HideChatAvatarSettings.DEFAULT_HIDE_OTHER
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoundAvatarMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RoundAvatarSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(RoundAvatarSettings.KEY_ENABLE, RoundAvatarSettings.DEFAULT_ENABLE))
    }
    var radiusFactor by remember {
        mutableStateOf(
            RoundAvatarSettings.normalizeRadiusFactor(
                sp.getFloat(RoundAvatarSettings.KEY_RADIUS_FACTOR, RoundAvatarSettings.DEFAULT_RADIUS_FACTOR)
            )
        )
    }
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
            item { SmallTitle(text = "全局头像") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用圆角头像",
                        summary = "统一应用到微信界面和通知头像",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(RoundAvatarSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    if (enabled) {
                        InsetDivider()
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                text = "圆角弧度 ${RoundAvatarSettings.displayPercent(radiusFactor)}%",
                                color = MiuixTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = radiusFactor,
                                onValueChange = {
                                    radiusFactor = RoundAvatarSettings.normalizeRadiusFactor(it)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                valueRange = RoundAvatarSettings.MIN_RADIUS_FACTOR..RoundAvatarSettings.MAX_RADIUS_FACTOR,
                                steps = 39,
                                onValueChangeFinished = {
                                    sp.edit()
                                        .putFloat(
                                            RoundAvatarSettings.KEY_RADIUS_FACTOR,
                                            RoundAvatarSettings.normalizeRadiusFactor(radiusFactor)
                                        )
                                        .apply()
                                },
                                showKeyPoints = true,
                                keyPoints = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class CustomFriendAvatarRoute {
    MAIN,
    CONTACT_PICKER
}

@Composable
internal fun CustomFriendAvatarMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, CustomFriendAvatarSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(CustomFriendAvatarSettings.KEY_ENABLE, CustomFriendAvatarSettings.DEFAULT_ENABLE))
    }
    var route by remember { mutableStateOf(CustomFriendAvatarRoute.MAIN) }
    var selectedWxid by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(emptyList<ContactOption>()) }
    var revision by remember { mutableStateOf(0) }

    LaunchedEffect(revision) {
        loadContacts(context, ContactPickerMode.BOTH, false) { result, _ -> contacts = result }
    }

    SettingsRouteTransition(
        targetState = route,
        label = "custom_friend_avatar_route",
        depthOf = { if (it == CustomFriendAvatarRoute.MAIN) 0 else 1 }
    ) { current ->
        when (current) {
            CustomFriendAvatarRoute.MAIN -> {
                val selected = contacts.firstOrNull { it.id == selectedWxid }
                val configuredIds = remember(revision) { CustomFriendAvatarStore.configuredFriends(context) }
                val configured = contacts.filter { it.id in configuredIds }
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
                        item { SmallTitle(text = "自定义头像") }
                        item {
                            SettingsCard {
                                SwitchRow(
                                    checked = enabled,
                                    title = "启用自定义头像",
                                    summary = "仅修改本机显示，不会更改微信中的原头像",
                                    onCheckedChange = {
                                        enabled = it
                                        sp.edit().putBoolean(CustomFriendAvatarSettings.KEY_ENABLE, it).apply()
                                    }
                                )
                                if (enabled) {
                                    InsetDivider()
                                    ActionRow(
                                        title = "选择好友或群聊",
                                        summary = selected?.label ?: "选择要设置头像的好友或群聊",
                                        onClick = { route = CustomFriendAvatarRoute.CONTACT_PICKER }
                                    )
                                    if (selected != null) {
                                        InsetDivider()
                                        ActionRow(
                                            title = if (CustomFriendAvatarStore.hasAvatar(context, selected.id)) "更换头像" else "设置头像",
                                            summary = "从系统相册或文件中选择图片",
                                            onClick = {
                                                val activity = context as? Activity
                                                if (activity == null) {
                                                    Toast.makeText(context, "当前页面无法打开图片选择器", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    CustomFriendAvatarPicker.launch(activity, selected.id) { success ->
                                                        Toast.makeText(
                                                            context,
                                                            if (success) "自定义头像已保存" else "头像设置失败",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        if (success) revision++
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (enabled) {
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "应用范围") }
                            item {
                                SettingsCard {
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_CHAT, "聊天消息", "替换群聊和私聊消息头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_CONVERSATION, "会话列表", "替换微信首页的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_CONTACTS, "通讯录", "替换通讯录中的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_PROFILE, "资料页", "替换好友和群聊资料页中的头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_MOMENTS, "朋友圈", "替换朋友圈中的好友头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_OTHER_UI, "其他微信界面", "替换收藏、搜索等界面中的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_DESKTOP_SHORTCUT, "桌面快捷方式", "替换添加到桌面的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_NOTIFICATIONS, "消息通知", "替换微信原生通知和 Hchat 通知中的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_MOMENTS_NOTIFICATIONS, "朋友圈通知", "替换朋友圈发布通知中的好友头像", true)
                                }
                            }
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "快捷入口") }
                            item {
                                SettingsCard {
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_CONVERSATION_MENU, "会话列表长按菜单", "长按好友或群聊会话时显示设置头像入口，聊天分组内同样生效", true)
                                }
                            }
                            if (configured.isNotEmpty()) {
                                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "已设置头像") }
                                item {
                                    SettingsCard {
                                        configured.forEachIndexed { index, option ->
                                            CustomFriendAvatarRow(context, option, revision) {
                                                selectedWxid = option.id
                                                val activity = context as? Activity
                                                if (activity != null) {
                                                    VoiceForwardMiuixDialog.showChoices(
                                                        activity = activity,
                                                        title = option.label,
                                                        summary = "管理自定义头像",
                                                        choices = listOf(
                                                            "更换头像" to "重新选择一张本地图片",
                                                            "恢复微信头像" to "删除当前自定义头像"
                                                        ),
                                                        onSelected = { choice ->
                                                            if (choice == 0) {
                                                                CustomFriendAvatarPicker.launch(activity, option.id) { success ->
                                                                    if (success) revision++
                                                                }
                                                            } else {
                                                                CustomFriendAvatarStore.remove(context, option.id)
                                                                revision++
                                                            }
                                                        },
                                                        onDismiss = {}
                                                    )
                                                }
                                            }
                                            if (index != configured.lastIndex) InsetDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            CustomFriendAvatarRoute.CONTACT_PICKER -> ContactPickerPage(
                context = context,
                request = ContactPickerRequest(
                    title = "选择好友或群聊",
                    mode = ContactPickerMode.BOTH,
                    multiSelect = false,
                    existingValue = selectedWxid,
                    onValue = {},
                    enableLabels = false,
                    enableGroupLabels = false,
                    singleConfirmText = "确定"
                ),
                onBack = { route = CustomFriendAvatarRoute.MAIN },
                onConfirm = { selected ->
                    selected.firstOrNull()?.let { selectedWxid = it.id }
                    route = CustomFriendAvatarRoute.MAIN
                }
            )
        }
    }
}

@Composable
private fun CustomFriendAvatarRow(
    context: Context,
    option: ContactOption,
    revision: Int,
    onClick: () -> Unit
) {
    val bitmap = remember(option.id, revision) {
        CustomFriendAvatarStore.loadBitmap(context, option.id)?.asImageBitmap()
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(if (option.group) "群" else option.label.take(1).ifEmpty { "友" })
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(option.label, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text("点击更换或恢复微信头像", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
    }
}
