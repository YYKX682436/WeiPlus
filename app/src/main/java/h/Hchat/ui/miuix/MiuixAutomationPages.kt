package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.api.model.ContactLabelBean
import h.Hchat.hooks.items.autoreply.AutoReplyRule
import h.Hchat.hooks.items.autoreply.AutoReplyRuntime
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.autoreply.AutoReplyStep
import h.Hchat.hooks.items.autoreply.AutoReplyZhiliaConfig
import h.Hchat.hooks.items.automessageforward.AutoMessageForwardRule
import h.Hchat.hooks.items.automessageforward.AutoMessageForwardSettings
import h.Hchat.hooks.items.customnotify.CustomNotificationRule
import h.Hchat.hooks.items.customnotify.CustomNotificationSettings
import h.Hchat.hooks.items.messageblock.MessageBlockBinding
import h.Hchat.hooks.items.messageblock.MessageBlockSettings
import h.Hchat.hooks.items.payment.core.RedPacketRuleBinding
import h.Hchat.hooks.items.payment.transfer.TransferRuleBinding
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashSet
import java.util.Locale
import androidx.compose.runtime.MutableState

@Composable
internal fun CustomNotificationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit,
    relatedProviders: List<FeatureSettingsProvider>,
    onOpenProvider: (FeatureSettingsProvider) -> Unit
) {
    val settings = remember { CustomNotificationSettings(context) }
    val sp = remember { HchatStorage.preferences(context, CustomNotificationSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<CustomNotificationRoute>(CustomNotificationRoute.Main) }
    var rules by remember { mutableStateOf(settings.rules()) }
    var defaultPrivateRule by remember { mutableStateOf(settings.defaultPrivateRule()) }
    var defaultGroupRule by remember { mutableStateOf(settings.defaultGroupRule()) }
    var defaultOfficialRule by remember { mutableStateOf(settings.defaultOfficialRule()) }
    val ruleListState = rememberLazyListState()
    var ruleQuery by remember { mutableStateOf("") }
    val ruleEditorListState = rememberLazyListState()
    val defaultEditorListState = rememberLazyListState()

    fun saveRules(next: List<CustomNotificationRule>) {
        val clean = next.distinctBy { it.talker }
        rules = clean
        settings.saveRules(clean)
    }

    fun saveDefaultRule(rule: CustomNotificationRule) {
        when {
            rule.official -> {
                defaultOfficialRule = rule
                settings.saveDefaultOfficialRule(rule)
            }
            rule.group -> {
                defaultGroupRule = rule
                settings.saveDefaultGroupRule(rule)
            }
            else -> {
                defaultPrivateRule = rule
                settings.saveDefaultPrivateRule(rule)
            }
        }
    }

    SettingsRouteTransition(
        targetState = route,
        label = "CustomNotificationRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            CustomNotificationRoute.Main -> CustomNotificationMainPage(
                context = context,
                provider = provider,
                sp = sp,
                rules = rules,
                defaultPrivateRule = defaultPrivateRule,
                defaultGroupRule = defaultGroupRule,
                defaultOfficialRule = defaultOfficialRule,
                onBack = onBack,
                onOpenRules = { route = CustomNotificationRoute.RuleList },
                onEditDefault = { route = CustomNotificationRoute.DefaultEditor(it) },
                relatedProviders = relatedProviders,
                onOpenProvider = onOpenProvider
            )
            is CustomNotificationRoute.DefaultEditor -> {
                val rule = when (currentRoute.kind) {
                    CustomNotificationDefaultKind.PRIVATE -> defaultPrivateRule
                    CustomNotificationDefaultKind.GROUP -> defaultGroupRule
                    CustomNotificationDefaultKind.OFFICIAL -> defaultOfficialRule
                }
                CustomNotificationRuleEditorPage(
                    context = context,
                    initialRule = rule,
                    listState = defaultEditorListState,
                    isDefault = true,
                    onBack = { route = CustomNotificationRoute.Main },
                    onSave = ::saveDefaultRule,
                    onDelete = {},
                    onPickOnlyMembers = {},
                    onPickBlockMembers = {}
                )
            }
            CustomNotificationRoute.RuleList -> CustomNotificationRuleListPage(
                context = context,
                rules = rules,
                query = ruleQuery,
                onQueryChange = { ruleQuery = it },
                listState = ruleListState,
                onBack = { route = CustomNotificationRoute.Main },
                onSaveRules = ::saveRules,
                onEditRule = { route = CustomNotificationRoute.RuleEditor(it) },
                onToggleRule = { rule, enabled ->
                    saveRules(rules.map { if (it.talker == rule.talker) it.copy(enabled = enabled) else it })
                    Toast.makeText(context, if (enabled) "已启用该会话规则" else "已关闭该会话规则", Toast.LENGTH_SHORT).show()
                },
                onBatchEdit = { route = CustomNotificationRoute.BatchEditor },
                onAddRules = {
                    route = CustomNotificationRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "添加通知会话",
                            mode = ContactPickerMode.ALL_CHATS,
                            multiSelect = true,
                            existingValue = "",
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            CustomNotificationRoute.BatchEditor -> CustomNotificationBatchEditorPage(
                ruleCount = rules.size,
                onBack = { route = CustomNotificationRoute.RuleList },
                onApply = { update ->
                    if (!update.hasChanges()) {
                        Toast.makeText(context, "请选择要应用的配置", Toast.LENGTH_SHORT).show()
                    } else {
                        saveRules(rules.map { rule -> update.apply(rule) })
                        Toast.makeText(context, "批量配置已应用", Toast.LENGTH_SHORT).show()
                        route = CustomNotificationRoute.RuleList
                    }
                }
            )
            is CustomNotificationRoute.RuleEditor -> {
                val rule = rules.firstOrNull { it.talker == currentRoute.talker }
                if (rule == null) {
                    CustomNotificationMissingRulePage(
                        onBack = { route = CustomNotificationRoute.RuleList }
                    )
                } else {
                    CustomNotificationRuleEditorPage(
                        context = context,
                        initialRule = rule,
                        listState = ruleEditorListState,
                        onBack = { route = CustomNotificationRoute.RuleList },
                        onSave = { saved ->
                            saveRules(rules.map { if (it.talker == saved.talker) saved else it })
                        },
                        onDelete = {
                            saveRules(rules.filterNot { it.talker == rule.talker })
                            Toast.makeText(context, "规则已删除", Toast.LENGTH_SHORT).show()
                            route = CustomNotificationRoute.RuleList
                        },
                        onPickOnlyMembers = { value ->
                            route = CustomNotificationRoute.GroupMemberPicker(
                                talker = rule.talker,
                                request = GroupMemberPickerRequest(
                                    title = "仅显示成员通知",
                                    existingValue = memberRulesToGroupEntries(rule.talker, value),
                                    onValue = {}
                                )
                            )
                        },
                        onPickBlockMembers = { value ->
                            route = CustomNotificationRoute.GroupMemberPicker(
                                talker = rule.talker,
                                request = GroupMemberPickerRequest(
                                    title = "屏蔽成员通知",
                                    existingValue = memberRulesToGroupEntries(rule.talker, value),
                                    onValue = {}
                                )
                            )
                        }
                    )
                }
            }
            is CustomNotificationRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = CustomNotificationRoute.RuleList },
                onConfirm = { picked ->
                    if (picked.isEmpty()) {
                        Toast.makeText(context, "未选择会话", Toast.LENGTH_SHORT).show()
                        route = CustomNotificationRoute.RuleList
                    } else {
                        val byTalker = rules.associateBy { it.talker }.toMutableMap()
                        var added = 0
                        var updated = 0
                        picked.forEach { option ->
                            val old = byTalker[option.id]
                            if (old == null) {
                                added++
                                byTalker[option.id] = customNotificationRuleFromContact(option)
                            } else {
                                updated++
                                byTalker[option.id] = old.copy(
                                    label = option.label,
                                    group = option.group,
                                    official = option.official
                                )
                            }
                        }
                        saveRules(byTalker.values.sortedWith(compareBy<CustomNotificationRule> { !it.enabled }.thenBy { it.label.lowercase(Locale.US) }))
                        val message = when {
                            added > 0 && updated > 0 -> "已添加 $added 个会话，更新 $updated 个会话"
                            added > 0 -> "已添加 $added 个会话"
                            else -> "已更新 $updated 个会话"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        route = CustomNotificationRoute.RuleList
                    }
                }
            )
            is CustomNotificationRoute.GroupMemberPicker -> GroupMemberPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = CustomNotificationRoute.RuleEditor(currentRoute.talker) },
                onSelectionChange = { entries ->
                    val talker = currentRoute.talker
                    val memberValue = groupEntriesToMemberRules(talker, entries.toList())
                    val title = currentRoute.request.title
                    rules.firstOrNull { it.talker == talker }?.let { old ->
                        val nextRule = if (title.contains("屏蔽")) {
                            old.copy(blockMembers = memberValue)
                        } else {
                            old.copy(onlyMembers = memberValue)
                        }
                        saveRules(rules.map { if (it.talker == talker) nextRule else it })
                    }
                }
            )
        }
    }
}

@Composable
internal fun AutoReplyMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit,
    initialScene: AutoReplyInitialScene,
    relatedProviders: List<FeatureSettingsProvider>,
    onOpenProvider: (FeatureSettingsProvider) -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME) }
    val settings = remember { AutoReplySettings(context) }
    var rules by remember { mutableStateOf(settings.rules()) }
    var route by remember(initialScene) { mutableStateOf(initialScene.route) }
    val sceneStateHolder = rememberSaveableStateHolder()
    var editingRuleId by remember { mutableStateOf<String?>(null) }
    var editingRuleDraft by remember { mutableStateOf<AutoReplyRule?>(null) }
    var ruleSequence by remember { mutableStateOf(AutoReplySequenceSession(emptyList(), false)) }
    var editingRuleExplicitlyEmpty by remember { mutableStateOf(false) }
    var showDiscardRuleDraft by remember { mutableStateOf(false) }
    val autoAcceptSequence = remember { AutoReplySequenceSession(settings.autoAcceptSteps(), true) }
    val greetAcceptedSequence = remember { AutoReplySequenceSession(settings.greetAcceptedSteps(), true) }
    val ruleListState = rememberLazyListState()
    var ruleQuery by remember { mutableStateOf("") }
    val ruleEditorListState = rememberLazyListState()
    val ruleEditorScope = rememberCoroutineScope()
    val autoAcceptListState = rememberLazyListState()
    val greetAcceptedListState = rememberLazyListState()
    val autoAcceptLabelListState = rememberLazyListState()
    val greetAcceptedLabelListState = rememberLazyListState()
    val autoAcceptAutomationKeys = remember {
        AutoReplyFriendAutomationKeys(
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_NEW_FRIEND_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_DATE_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_DATE_FORMAT,
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_EXISTING_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_SELECTED_NAMES,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_NEW_FRIEND_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_NICKNAME_SUFFIX_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_DATE_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_DATE_FORMAT,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_CUSTOM_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_CUSTOM_TEXT
        )
    }
    val greetAcceptedAutomationKeys = remember {
        AutoReplyFriendAutomationKeys(
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_NEW_FRIEND_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_DATE_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_DATE_FORMAT,
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_EXISTING_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_SELECTED_NAMES,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_NEW_FRIEND_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_NICKNAME_SUFFIX_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_DATE_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_DATE_FORMAT,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_CUSTOM_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_CUSTOM_TEXT
        )
    }

    fun saveRules(next: List<AutoReplyRule>, explicitlyEmptyRuleId: String? = null) {
        rules = next
        settings.saveRules(next, explicitlyEmptyRuleId)
    }

    fun updateRuleDraft(changed: AutoReplyRule, explicitEmpty: Boolean? = null) {
        if (explicitEmpty != null) editingRuleExplicitlyEmpty = explicitEmpty
        editingRuleDraft = changed
        val previous = rules.firstOrNull { it.id == changed.id } ?: return
        val validKeyword = (changed.matchType == AutoReplySettings.MATCH_ANY || changed.patTrigger == AutoReplySettings.PAT_ME || changed.keyword.isNotBlank()) &&
            (changed.matchType != AutoReplySettings.MATCH_REGEX || (changed.keyword.isNotBlank() && runCatching { java.util.regex.Pattern.compile(changed.keyword) }.isSuccess))
        val validTarget = changed.targetMode != AutoReplySettings.TARGET_SPECIFIC || changed.targetIds.isNotEmpty() || changed.includedGroupMembers.isNotEmpty()
        val safe = changed.copy(
            name = changed.name.takeIf { it.isNotBlank() } ?: previous.name,
            keyword = if (validKeyword) changed.keyword else previous.keyword,
            matchType = if (validKeyword) changed.matchType else previous.matchType,
            targetMode = if (validTarget) changed.targetMode else previous.targetMode,
            targetIds = if (validTarget) changed.targetIds else previous.targetIds,
            includedGroupMembers = if (validTarget) changed.includedGroupMembers else previous.includedGroupMembers
        )
        saveRules(rules.map { if (it.id == changed.id) safe else it }, if (editingRuleExplicitlyEmpty) changed.id else null)
    }

    SettingsRouteTransition(
        targetState = route,
        label = "AutoReplyRouteTransition",
        depthOf = { autoReplyRouteDepth(it) }
    ) { currentRoute ->
        RetainFriendDraft(sceneStateHolder, currentRoute) {
        when (currentRoute) {
        "rules", "main" -> AutoReplyRuleListPage(
            provider = provider,
            sp = sp,
            rules = rules,
            query = ruleQuery,
            onQueryChange = { ruleQuery = it },
            listState = ruleListState,
            onBack = onBack,
            onOpenAi = { route = "ai" },
            relatedProviders = relatedProviders,
            onOpenProvider = onOpenProvider,
            onAdd = {
                ruleEditorScope.launch { ruleEditorListState.scrollToItem(0) }
                val rule = AutoReplyRule(
                    id = System.currentTimeMillis().toString(),
                    name = "规则 ${rules.size + 1}",
                    steps = emptyList()
                )
                editingRuleId = rule.id
                editingRuleDraft = rule
                ruleSequence = AutoReplySequenceSession(emptyList(), false)
                editingRuleExplicitlyEmpty = false
                route = "ruleEditor"
            },
            onEdit = {
                ruleEditorScope.launch { ruleEditorListState.scrollToItem(0) }
                editingRuleId = it.id
                editingRuleDraft = it
                ruleSequence = AutoReplySequenceSession(it.steps, true)
                editingRuleExplicitlyEmpty = settings.isRuleExplicitlyEmpty(it.id)
                route = "ruleEditor"
            },
            onToggle = { rule, enabled -> saveRules(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }) },
            onDelete = { rule ->
                saveRules(rules.filterNot { it.id == rule.id })
                Toast.makeText(context, "规则已删除", Toast.LENGTH_SHORT).show()
            },
            onBatchDelete = { targets ->
                val targetIds = targets.mapTo(HashSet()) { it.id }
                saveRules(rules.filterNot { it.id in targetIds })
                Toast.makeText(context, "已删除 ${targets.size} 条规则", Toast.LENGTH_SHORT).show()
            }
        )
        "ruleEditor" -> {
            val rule = editingRuleDraft ?: rules.firstOrNull { it.id == editingRuleId }
            if (rule == null) {
                editingRuleDraft = null
                route = "main"
            } else {
                AutoReplyRuleEditorPage(
                    rule = rule,
                    sequence = ruleSequence,
                    isNew = rules.none { it.id == rule.id },
                    listState = ruleEditorListState,
                    onBack = {
                        if (ruleSequence.entries.any { it.pending }) showDiscardRuleDraft = true
                        else { editingRuleDraft = null; route = "main" }
                    },
                    onSave = { saved ->
                        val error = when {
                            saved.name.isBlank() -> "请填写规则名称"
                            saved.matchType != AutoReplySettings.MATCH_ANY && saved.keyword.isBlank() && saved.patTrigger != AutoReplySettings.PAT_ME -> "请填写关键词"
                            saved.matchType == AutoReplySettings.MATCH_REGEX && runCatching { java.util.regex.Pattern.compile(saved.keyword) }.isFailure -> "正则表达式无效"
                            saved.targetMode == AutoReplySettings.TARGET_SPECIFIC && saved.targetIds.isEmpty() && saved.includedGroupMembers.isEmpty() -> "请选择指定对象"
                            !ruleSequence.isComplete() -> "请添加并完善回复步骤"
                            else -> null
                        }
                        if (error != null) {
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        } else {
                            saveRules(
                                if (rules.any { it.id == saved.id }) rules.map { if (it.id == saved.id) saved else it } else rules + saved,
                                if (editingRuleExplicitlyEmpty) saved.id else null
                            )
                            editingRuleDraft = null
                            route = "main"
                        }
                    },
                    onDraftChange = { changed -> updateRuleDraft(changed) },
                    onStepsChange = { changed, explicitlyEmpty -> updateRuleDraft(changed, explicitlyEmpty) }
                )
            }
        }
        "autoAccept" -> AutoReplyFriendPage(
            context = context,
            title = "收到好友申请",
            sp = sp,
            enabledKey = AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE,
            delayKey = AutoReplySettings.KEY_AUTO_ACCEPT_DELAY_MS,
            tagEnabledKey = AutoReplySettings.KEY_AUTO_ACCEPT_TAG_ENABLE,
            tagNameKey = AutoReplySettings.KEY_AUTO_ACCEPT_TAG_NAME,
            sequence = autoAcceptSequence,
            listState = autoAcceptListState,
            onBack = { if (initialScene == AutoReplyInitialScene.MAIN) route = "main" else onBack() },
            automationKeys = autoAcceptAutomationKeys,
            onSelectLabels = { route = "autoAcceptLabels" },
            onStepsChange = { steps, explicitlyEmpty -> settings.saveAutoAcceptSteps(steps, explicitlyEmpty) }
        )
        "autoAcceptLabels" -> AutoReplyLabelPickerPage(
            context = context,
            sp = sp,
            selectedKey = AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_SELECTED_NAMES,
            listState = autoAcceptLabelListState,
            onBack = { route = "autoAccept" }
        )
        "greetAccepted" -> AutoReplyFriendPage(
            context = context,
            title = "对方通过验证",
            sp = sp,
            enabledKey = AutoReplySettings.KEY_GREET_ACCEPTED_ENABLE,
            delayKey = AutoReplySettings.KEY_GREET_ACCEPTED_DELAY_MS,
            tagEnabledKey = AutoReplySettings.KEY_GREET_ACCEPTED_TAG_ENABLE,
            tagNameKey = AutoReplySettings.KEY_GREET_ACCEPTED_TAG_NAME,
            sequence = greetAcceptedSequence,
            listState = greetAcceptedListState,
            onBack = { if (initialScene == AutoReplyInitialScene.MAIN) route = "main" else onBack() },
            automationKeys = greetAcceptedAutomationKeys,
            dependencyHint = "沿用自动回复总开关及排除名单",
            onSelectLabels = { route = "greetAcceptedLabels" },
            onStepsChange = { steps, explicitlyEmpty -> settings.saveGreetAcceptedSteps(steps, explicitlyEmpty) }
        )
        "greetAcceptedLabels" -> AutoReplyLabelPickerPage(
            context = context,
            sp = sp,
            selectedKey = AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_SELECTED_NAMES,
            listState = greetAcceptedLabelListState,
            onBack = { route = "greetAccepted" }
        )
        "ai" -> AutoReplyAiPage(context, sp, onBack = { route = "main" })
        else -> Unit
        }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDiscardRuleDraft,
        title = "放弃尚未加入的回复？",
        message = "返回会放弃尚未加入的回复草稿。",
        labels = emptyList(),
        confirmText = "放弃并返回",
        onDismiss = { showDiscardRuleDraft = false },
        onConfirm = { showDiscardRuleDraft = false; editingRuleDraft = null; route = "main" }
    )
}

@Composable
private fun RetainFriendDraft(holder: SaveableStateHolder, route: String, content: @Composable () -> Unit) {
    if (route == "autoAccept" || route == "greetAccepted") {
        holder.SaveableStateProvider(route, content)
    } else {
        content()
    }
}

@Composable
internal fun AutoMessageForwardMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    AutoMessageForwardSettingsUi.Page(context, provider, onBack)
}

private object AutoMessageForwardSettingsUi {
@Composable
fun Page(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { AutoMessageForwardSettings }
    var enabled by remember { mutableStateOf(settings.isEnabled(context)) }
    var rules by remember { mutableStateOf(settings.rules(context)) }
    var route by remember { mutableStateOf(AutoMessageForwardRoute.MAIN) }
    var draft by remember { mutableStateOf<AutoMessageForwardRule?>(null) }
    var pickerField by remember { mutableStateOf<AutoMessageForwardContactField?>(null) }
    var ruleQuery by remember { mutableStateOf("") }
    val ruleListState = rememberLazyListState()
    val editorListState = rememberLazyListState()

    fun saveRules(next: List<AutoMessageForwardRule>) {
        rules = next
        settings.saveRules(context, next)
    }

    fun updateDraft(next: AutoMessageForwardRule) {
        draft = next
        val previous = rules.firstOrNull { it.id == next.id } ?: return
        val sourceIds = next.sourceIds.ifEmpty { previous.sourceIds }
        val clean = next.copy(
            name = next.name.trim().ifBlank { previous.name },
            sourceIds = sourceIds,
            targetIds = next.targetIds.ifEmpty { previous.targetIds },
            messageKinds = next.messageKinds.ifEmpty { previous.messageKinds },
            sourceMemberIds = if (next.sourceIds.isEmpty()) previous.sourceMemberIds
                else retainAutoMessageForwardSourceMembers(next.sourceMemberIds, sourceIds)
        )
        if (clean != previous && clean.name.isNotBlank() && clean.sourceIds.isNotEmpty() && clean.targetIds.isNotEmpty() && clean.messageKinds.isNotEmpty()) {
            saveRules(rules.map { if (it.id == clean.id) clean else it })
        }
    }

    SettingsRouteTransition(
        targetState = route,
        label = "AutoMessageForwardRoute",
        depthOf = {
            when (it) {
                AutoMessageForwardRoute.MAIN, AutoMessageForwardRoute.RULES -> 0
                AutoMessageForwardRoute.EDITOR -> 1
                AutoMessageForwardRoute.CONTACTS,
                AutoMessageForwardRoute.MEMBERS,
                AutoMessageForwardRoute.REPLACEMENTS -> 2
            }
        }
    ) { currentRoute ->
        when (currentRoute) {
            AutoMessageForwardRoute.MAIN, AutoMessageForwardRoute.RULES -> AutoMessageForwardRuleListPage(
                provider = provider,
                enabled = enabled,
                onEnabledChange = { enabled = it; settings.setEnabled(context, it) },
                rules = rules,
                listState = ruleListState,
                query = ruleQuery,
                onQueryChange = { ruleQuery = it },
                onBack = onBack,
                onAdd = {
                    draft = settings.newRule(rules.size + 1)
                    route = AutoMessageForwardRoute.EDITOR
                },
                onEdit = {
                    draft = it
                    route = AutoMessageForwardRoute.EDITOR
                },
                onToggle = { rule, checked ->
                    saveRules(rules.map { if (it.id == rule.id) rule.copy(enabled = checked) else it })
                },
                onDelete = { rule ->
                    saveRules(rules.filterNot { it.id == rule.id })
                    Toast.makeText(context, "规则已删除", Toast.LENGTH_SHORT).show()
                },
                onBatchDelete = { selected ->
                    val ids = selected.mapTo(HashSet()) { it.id }
                    saveRules(rules.filterNot { it.id in ids })
                    Toast.makeText(context, "已删除 ${selected.size} 条规则", Toast.LENGTH_SHORT).show()
                }
            )
            AutoMessageForwardRoute.EDITOR -> {
                val current = draft
                if (current == null) {
                    route = AutoMessageForwardRoute.MAIN
                } else {
                    AutoMessageForwardRuleEditorPage(
                        rule = current,
                        isNew = rules.none { it.id == current.id },
                        listState = editorListState,
                        onBack = { route = AutoMessageForwardRoute.MAIN },
                        onDraftChange = ::updateDraft,
                        onPickContacts = { next, field ->
                            draft = next
                            pickerField = field
                            route = AutoMessageForwardRoute.CONTACTS
                        },
                        onPickMembers = { next ->
                            draft = next
                            route = AutoMessageForwardRoute.MEMBERS
                        },
                        onPickReplacementRules = { next ->
                            draft = next
                            route = AutoMessageForwardRoute.REPLACEMENTS
                        },
                        onSave = { saved ->
                            val clean = saved.copy(
                                name = saved.name.trim(),
                                sourceMemberIds = retainAutoMessageForwardSourceMembers(
                                    saved.sourceMemberIds,
                                    saved.sourceIds
                                )
                            )
                            when {
                                clean.name.isBlank() -> Toast.makeText(context, "请输入规则名称", Toast.LENGTH_SHORT).show()
                                clean.sourceIds.isEmpty() -> Toast.makeText(context, "请选择监听会话", Toast.LENGTH_SHORT).show()
                                clean.targetIds.isEmpty() -> Toast.makeText(context, "请选择转发会话", Toast.LENGTH_SHORT).show()
                                clean.messageKinds.isEmpty() -> Toast.makeText(context, "至少选择一种消息类型", Toast.LENGTH_SHORT).show()
                                else -> {
                                    saveRules(
                                        if (rules.any { it.id == clean.id }) {
                                            rules.map { if (it.id == clean.id) clean else it }
                                        } else {
                                            rules + clean
                                        }
                                    )
                                    draft = null
                                    Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
                                    route = AutoMessageForwardRoute.MAIN
                                }
                            }
                        }
                    )
                }
            }
            AutoMessageForwardRoute.CONTACTS -> {
                val current = draft
                val field = pickerField
                if (current == null || field == null) {
                    route = AutoMessageForwardRoute.EDITOR
                } else {
                    val sources = field == AutoMessageForwardContactField.SOURCES
                    val contactRequest = remember(current.id, field) {
                        ContactPickerRequest(
                            title = if (sources) "选择监听会话" else "选择转发会话",
                            mode = if (sources) ContactPickerMode.ALL_CHATS else ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = formatIds(if (sources) current.sourceIds else current.targetIds),
                            onValue = {},
                            enableLabels = true
                        )
                    }
                    ContactPickerPage(
                        context = context,
                        request = contactRequest,
                        onBack = {
                            pickerField = null
                            route = AutoMessageForwardRoute.EDITOR
                        },
                        onSelectionChange = { ids ->
                            val latest = draft ?: current
                            updateDraft(if (sources) latest.copy(
                                sourceIds = ids,
                                sourceMemberIds = retainAutoMessageForwardSourceMembers(latest.sourceMemberIds, ids)
                            )
                            else latest.copy(targetIds = ids))
                        }
                    )
                }
            }
            AutoMessageForwardRoute.MEMBERS -> {
                val current = draft
                val sourceGroups = current?.sourceIds.orEmpty().filterTo(linkedSetOf(), ::isAutoMessageForwardGroupId)
                if (current == null || sourceGroups.isEmpty()) {
                    route = AutoMessageForwardRoute.EDITOR
                } else {
                    val request = remember(current.id, sourceGroups) {
                        GroupMemberPickerRequest(
                            title = "选择监听群成员",
                            existingValue = formatGroupMemberEntries(current.sourceMemberIds),
                            onValue = {},
                            allowedGroupIds = sourceGroups
                        )
                    }
                    GroupMemberPickerPage(
                        context = context,
                        request = request,
                        onBack = { route = AutoMessageForwardRoute.EDITOR },
                        onSelectionChange = { entries -> updateDraft((draft ?: current).copy(sourceMemberIds = entries.toSet())) }
                    )
                }
            }
            AutoMessageForwardRoute.REPLACEMENTS -> {
                val current = draft
                if (current == null) {
                    route = AutoMessageForwardRoute.EDITOR
                } else {
                    KeywordReplacementRulesPage(
                        context = context,
                        initialRules = current.keywordReplacements,
                        onBack = { route = AutoMessageForwardRoute.EDITOR },
                        onSave = { updated ->
                            updateDraft((draft ?: current).copy(keywordReplacements = updated))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoMessageForwardRuleListPage(
    provider: FeatureSettingsProvider,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    rules: List<AutoMessageForwardRule>,
    listState: LazyListState,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (AutoMessageForwardRule) -> Unit,
    onToggle: (AutoMessageForwardRule, Boolean) -> Unit,
    onDelete: (AutoMessageForwardRule) -> Unit,
    onBatchDelete: (List<AutoMessageForwardRule>) -> Unit
) {
    val context = LocalContext.current
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val visible = rules.filter { rule ->
        val lower = query.trim().lowercase(Locale.US)
        lower.isBlank() ||
            rule.name.lowercase(Locale.US).contains(lower) ||
            rule.sourceIds.any { it.lowercase(Locale.US).contains(lower) } ||
            rule.sourceMemberIds.any { it.lowercase(Locale.US).contains(lower) } ||
            rule.targetIds.any { it.lowercase(Locale.US).contains(lower) } ||
            autoMessageForwardRuleSummary(rule).lowercase(Locale.US).contains(lower)
    }
    val visibleIds = visible.mapTo(LinkedHashSet()) { it.id }
    val selected = rules.filter { it.id in selectedIds }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val scrollBehavior = MiuixScrollBehavior()
    val selectionBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = selectionBackState, isBackEnabled = batchDeleteMode && LocalSettingsBackRegistrationEnabled.current, onBackCompleted = {
        batchDeleteMode = false
        selectedIds = emptySet()
    })
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = {
            if (batchDeleteMode) { batchDeleteMode = false; selectedIds = emptySet() } else onBack()
        },
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        ) {
            item { SettingsSectionTitle(text = "自动转发") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "消息自动转发", "默认不转发自己发送的消息，可在每条规则中单独开启", onCheckedChange = onEnabledChange)
                }
            }
            item { SettingsSectionTitle(text = "转发规则 · ${rules.size} 条") }
            item {
                SettingsCard(modifier = Modifier.padding(top = 12.dp)) {
                    InputRow("搜索", "按规则名、会话、群成员或消息类型筛选", query, onValueChange = onQueryChange)
                }
            }
            item {
                SettingsCard {
                    if (rules.isEmpty()) {
                        EmptyText("暂无规则")
                        InsetDivider()
                    }
                    if (batchDeleteMode) {
                        if (visibleIds.isNotEmpty()) ContentActionRow(if (allVisibleSelected) "取消全选" else "全选") {
                            selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
                        }
                        ContentActionRow("删除（${selected.size}）", emphasis = ContentActionEmphasis.DANGER) {
                            if (selected.isEmpty()) Toast.makeText(context, "请先选择规则", Toast.LENGTH_SHORT).show()
                            else showDeleteConfirm = true
                        }
                    } else {
                        if (rules.isNotEmpty()) ContentActionRow("批量删除") { batchDeleteMode = true; selectedIds = emptySet() }
                        AddActionRow("新增规则", onClick = onAdd)
                    }
                }
            }
            if (visible.isEmpty()) {
                if (rules.isNotEmpty()) item { SettingsCard { EmptyText("没有匹配的规则") } }
            } else {
                visible.forEachIndexed { index, rule ->
                    item {
                        SettingsSectionTitle(
                            text = "规则 ${index + 1}"
                        )
                    }


                    item {
                        SettingsCard {
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(rule.name.ifBlank { "未命名规则" }, index, autoMessageForwardRuleSummary(rule)),
                                    selected = rule.id in selectedIds,
                                    onClick = {
                                        selectedIds = if (rule.id in selectedIds) selectedIds - rule.id else selectedIds + rule.id
                                    }
                                )
                            } else {
                                SwitchRow(
                                    checked = rule.enabled,
                                    title = rule.name.ifBlank { "未命名规则" },
                                    summary = autoMessageForwardRuleSummary(rule),
                                    onCheckedChange = { onToggle(rule, it) }
                                )
                                InsetDivider()
                                ActionRow("编辑", "修改监听会话、转发会话和消息类型") { onEdit(rule) }
                                InsetDivider()
                                SecondaryActionRow("删除", "移除此规则") { onDelete(rule) }
                            }
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selected.size} 条转发规则，此操作不可撤销。",
        labels = selected.map { it.name.ifBlank { "未命名规则" } },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selected
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedIds = emptySet()
            onBatchDelete(targets)
        }
    )
}

@Composable
private fun AutoMessageForwardRuleEditorPage(
    rule: AutoMessageForwardRule,
    isNew: Boolean,
    listState: LazyListState,
    onBack: () -> Unit,
    onDraftChange: (AutoMessageForwardRule) -> Unit,
    onPickContacts: (AutoMessageForwardRule, AutoMessageForwardContactField) -> Unit,
    onPickMembers: (AutoMessageForwardRule) -> Unit,
    onPickReplacementRules: (AutoMessageForwardRule) -> Unit,
    onSave: (AutoMessageForwardRule) -> Unit
) {
    val context = LocalContext.current
    val current = rule
    var delayInput by remember(rule.id) { mutableStateOf(rule.delaySeconds.toString()) }
    var typesExpanded by remember(rule.id) { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = if (isNew) "新增转发规则" else "编辑转发规则",
        largeTitle = if (isNew) "新增转发规则" else "编辑转发规则",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        ) {
            item { SettingsSectionTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(current.enabled, "规则", "关闭后保留配置但不触发") { onDraftChange(current.copy(enabled = it)) }
                    InsetDivider()
                    SwitchRow(current.forwardOwnMessages, "转发自己发送的消息", "默认关闭；模块自动转发的消息不会再次触发") {
                        onDraftChange(current.copy(forwardOwnMessages = it))
                    }
                    InsetDivider()
                    SwitchRow(current.followSourceRecall, "跟随原消息撤回", "原消息撤回后，撤回该规则自动转发的消息") {
                        onDraftChange(current.copy(followSourceRecall = it))
                    }
                    InsetDivider()
                    InputRow("规则名称", if (current.name.isBlank()) "请输入规则名称，保留上次有效配置" else "用于列表里识别规则", current.name) { onDraftChange(current.copy(name = it)) }
                    InsetDivider()
                    SwitchRow(current.delayEnabled, "延迟发送", "开启后按设置时间等待再转发") {
                        onDraftChange(current.copy(delayEnabled = it))
                    }
                    InsetDivider()
                    NumberInputRow("延迟时间", if (delayInput.isNotEmpty() && delayInput.toLongOrNull() == null) "请输入有效的延迟时间" else "单位秒，0 表示立即发送", delayInput) {
                        delayInput = it
                        it.toLongOrNull()?.takeIf { seconds -> seconds >= 0 }?.let { seconds -> onDraftChange(current.copy(delaySeconds = seconds)) }
                    }
                }
            }
            item { SettingsSectionTitle(text = "会话") }
            item {
                SettingsCard {
                    ActionRow("监听会话", autoMessageForwardIdSummary(current.sourceIds, "未选择")) {
                        onPickContacts(current, AutoMessageForwardContactField.SOURCES)
                    }
                    InsetDivider()
                    ActionRow(
                        "群成员范围",
                        if (current.sourceMemberIds.isEmpty()) "不限群成员" else "已选择 ${current.sourceMemberIds.size} 个群成员"
                    ) {
                        if (current.sourceIds.none(::isAutoMessageForwardGroupId)) {
                            Toast.makeText(context, "请先选择监听群聊", Toast.LENGTH_SHORT).show()
                        } else {
                            onPickMembers(current)
                        }
                    }
                    InsetDivider()
                    ActionRow("转发会话", autoMessageForwardIdSummary(current.targetIds, "未选择")) {
                        onPickContacts(current, AutoMessageForwardContactField.TARGETS)
                    }
                }
            }
            item { SettingsSectionTitle(text = "消息类型") }
            item {
                SequenceItemCard(
                    title = "选择消息类型",
                    summary = autoMessageForwardTypeSummary(current.messageKinds),
                    expanded = typesExpanded,
                    onToggle = { typesExpanded = !typesExpanded }
                ) {
                    val options = autoMessageForwardTypeOptions()
                    ContentActionRow(if (current.messageKinds.size == options.size) "取消全选" else "全选") {
                        onDraftChange(current.copy(messageKinds = if (current.messageKinds.size == options.size) emptySet() else options.mapTo(LinkedHashSet()) { it.first }))
                    }
                    options.forEach { (kind, label, description) ->
                        InsetDivider()
                        SwitchRow(kind in current.messageKinds, label, description) { checked ->
                            onDraftChange(current.copy(messageKinds = if (checked) current.messageKinds + kind else current.messageKinds - kind))
                        }
                    }
                }
            }
            item { SettingsSectionTitle(text = "文本处理") }
            item {
                SettingsCard {
                    SwitchRow(current.includeKeywordsEnabled, "包含关键词", "开启后仅转发命中关键词的消息") {
                        onDraftChange(current.copy(includeKeywordsEnabled = it))
                    }
                    InsetDivider()
                    InputRow("包含关键词内容", "多个关键词用 |、逗号或换行分隔", current.includeKeywords, minLines = 2) {
                        onDraftChange(current.copy(includeKeywords = it))
                    }
                    InsetDivider()
                    SwitchRow(current.excludeKeywordsEnabled, "排除关键词", "开启后不转发命中关键词的消息") {
                        onDraftChange(current.copy(excludeKeywordsEnabled = it))
                    }
                    InsetDivider()
                    InputRow("排除关键词内容", "多个关键词用 |、逗号或换行分隔", current.excludeKeywords, minLines = 2) {
                        onDraftChange(current.copy(excludeKeywords = it))
                    }
                    InsetDivider()
                    SwitchRow(current.replaceKeywordsEnabled, "替换关键词", "转发普通文字前按多条规则替换正文") {
                        onDraftChange(current.copy(replaceKeywordsEnabled = it))
                    }
                    InsetDivider()
                    ActionRow(
                        "替换规则",
                        if (current.keywordReplacements.isEmpty()) {
                            "暂无规则"
                        } else {
                            "已设置 ${current.keywordReplacements.size} 条规则"
                        }
                    ) {
                        onPickReplacementRules(current)
                    }
                }
            }

            if (isNew) item {
                val error = when {
                    current.name.isBlank() -> "请输入规则名称"
                    current.sourceIds.isEmpty() -> "请选择监听会话"
                    current.targetIds.isEmpty() -> "请选择转发会话"
                    current.messageKinds.isEmpty() -> "至少选择一种消息类型"
                    delayInput.toLongOrNull()?.let { it >= 0 } != true -> "请输入有效的延迟时间"
                    else -> null
                }
                PrimaryFormAction("创建规则", error) { onSave(current) }
            }
        }
    }
}

private fun autoMessageForwardRuleSummary(rule: AutoMessageForwardRule): String {
    return buildString {
        append("监听 ${rule.sourceIds.size} 个会话 → 转发到 ${rule.targetIds.size} 个会话 · ${autoMessageForwardTypeSummary(rule.messageKinds)}")
        if (rule.sourceMemberIds.isNotEmpty()) append(" · 指定 ${rule.sourceMemberIds.size} 个群成员")
        if (rule.forwardOwnMessages) append(" · 含自己发送")
        if (rule.followSourceRecall) append(" · 跟随撤回")
        if (rule.delayEnabled && rule.delaySeconds > 0L) append(" · 延迟 ${rule.delaySeconds} 秒")
        if ((rule.includeKeywordsEnabled && rule.includeKeywords.isNotBlank()) ||
            (rule.excludeKeywordsEnabled && rule.excludeKeywords.isNotBlank()) ||
            (rule.replaceKeywordsEnabled && rule.keywordReplacements.isNotEmpty())
        ) {
            append(" · 已设置关键词")
        }
    }
}

private fun autoMessageForwardIdSummary(ids: Set<String>, empty: String): String {
    return if (ids.isEmpty()) empty else "已选择 ${ids.size} 个会话"
}

private fun retainAutoMessageForwardSourceMembers(
    memberIds: Set<String>,
    sourceIds: Set<String>
): Set<String> = memberIds.filterTo(linkedSetOf()) { it.substringBefore('/') in sourceIds }

private fun isAutoMessageForwardGroupId(value: String): Boolean =
    value.endsWith("@chatroom") || value.endsWith("@im.chatroom")

private fun autoMessageForwardTypeSummary(kinds: Set<String>): String {
    val options = autoMessageForwardTypeOptions().associate { it.first to it.second }
    val labels = kinds.mapNotNull { options[it] }
    return when {
        labels.isEmpty() -> "未选择"
        labels.size == options.size -> "全部支持类型"
        labels.size <= 3 -> labels.joinToString("、")
        else -> "${labels.take(3).joinToString("、")}等 ${labels.size} 类"
    }
}

private fun autoMessageForwardTypeOptions(): List<Triple<String, String, String>> {
    return listOf(
        Triple(WeChatMessageObserveApi.Kind.TEXT, "文字", "普通文字消息"),
        Triple(WeChatMessageObserveApi.Kind.IMAGE, "图片", "图片消息"),
        Triple(WeChatMessageObserveApi.Kind.VOICE, "语音", "语音消息"),
        Triple(WeChatMessageObserveApi.Kind.VIDEO, "视频", "视频消息"),
        Triple(WeChatMessageObserveApi.Kind.EMOJI, "表情", "动画表情消息"),
        Triple(WeChatMessageObserveApi.Kind.QUOTE, "引用", "引用消息"),
        Triple(WeChatMessageObserveApi.Kind.FILE, "文件", "已下载到本地的文件"),
        Triple(WeChatMessageObserveApi.Kind.LINK, "链接", "网页链接卡片"),
        Triple(WeChatMessageObserveApi.Kind.MUSIC, "音乐", "音乐卡片"),
        Triple(WeChatMessageObserveApi.Kind.APP, "小程序及卡片", "其它可转发的应用消息"),
        Triple(WeChatMessageObserveApi.Kind.LOCATION, "位置", "位置消息"),
        Triple(WeChatMessageObserveApi.Kind.SHARE_CARD, "名片", "联系人名片"),
        Triple(WeChatMessageObserveApi.Kind.NOTE, "聊天记录", "聊天记录等笔记消息"),
        Triple(WeChatMessageObserveApi.Kind.VIDEO_NUMBER_VIDEO, "视频号", "视频号消息")
    )
}
}

@Composable
private fun AutoReplyRuleListPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    rules: List<AutoReplyRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onBack: () -> Unit,
    onOpenAi: () -> Unit,
    relatedProviders: List<FeatureSettingsProvider>,
    onOpenProvider: (FeatureSettingsProvider) -> Unit,
    onAdd: () -> Unit,
    onEdit: (AutoReplyRule) -> Unit,
    onToggle: (AutoReplyRule, Boolean) -> Unit,
    onDelete: (AutoReplyRule) -> Unit,
    onBatchDelete: (List<AutoReplyRule>) -> Unit
) {
    val context = LocalContext.current
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var excludeExpanded by remember { mutableStateOf(false) }
    var excludedTalkersEnabled by remember { mutableStateOf(sp.getBoolean(AutoReplySettings.KEY_EXCLUDED_TALKERS_ENABLE, false)) }
    var excludedTalkers by remember { mutableStateOf(sp.getString(AutoReplySettings.KEY_EXCLUDED_TALKERS, "").orEmpty()) }
    val visible = rules.filter {
        val lower = query.trim().lowercase(Locale.US)
        lower.isBlank() ||
            it.name.lowercase(Locale.US).contains(lower) ||
            it.keyword.lowercase(Locale.US).contains(lower) ||
            it.excludedKeywords.lowercase(Locale.US).contains(lower)
    }
    val visibleIds = visible.mapTo(LinkedHashSet()) { it.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val selectedRules = rules.filter { it.id in selectedIds }
    val scrollBehavior = MiuixScrollBehavior()
    val selectionBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = selectionBackState, isBackEnabled = batchDeleteMode && LocalSettingsBackRegistrationEnabled.current, onBackCompleted = {
        batchDeleteMode = false
        selectedIds = emptySet()
    })
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = {
            if (batchDeleteMode) {
                batchDeleteMode = false
                selectedIds = emptySet()
            } else {
                onBack()
            }
        },
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        ) {
            item { SettingsSectionTitle(text = "自动回复") }
            item {
                SettingsCard {
                    SwitchRow(sp, AutoReplySettings.KEY_ENABLE, "自动回复", "开启后按规则回复收到的消息", AutoReplySettings.DEFAULT_ENABLE)
                    InsetDivider()
                    ActionRow("AI 配置", "配置智聊 AI，可切换多个模型", onOpenAi)
                }
            }
            item {
                SequenceItemCard(
                    title = "全局排除",
                    summary = (if (excludedTalkersEnabled) "已开启" else "未开启") + " · ${parseIds(excludedTalkers).size} 个会话",
                    expanded = excludeExpanded,
                    modifier = Modifier.padding(top = 12.dp),
                    onToggle = { excludeExpanded = !excludeExpanded }
                ) {
                    SwitchRow(excludedTalkersEnabled, "排除指定会话", "开启后不回复指定会话的消息") {
                        excludedTalkersEnabled = it
                        sp.edit().putBoolean(AutoReplySettings.KEY_EXCLUDED_TALKERS_ENABLE, it).apply()
                    }
                    InsetDivider()
                    InputRow("排除会话 ID", "多个 ID 用逗号、分号或换行分隔", excludedTalkers, minLines = 2) {
                        excludedTalkers = it
                        sp.edit().putString(AutoReplySettings.KEY_EXCLUDED_TALKERS, it).apply()
                    }
                }
            }
            if (relatedProviders.isNotEmpty()) {
                item { SettingsSectionTitle(text = "相关功能") }
                item { FeatureListCard(relatedProviders, "", onOpenProvider) }
            }
            item { SettingsSectionTitle(text = "回复规则 · ${rules.size} 条") }
            item {
                SettingsCard {
                    InputRow("搜索", "按规则名或关键词筛选", query, onValueChange = onQueryChange)
                }
            }
            item {
                SettingsCard(modifier = Modifier.padding(top = 12.dp)) {
                    if (rules.isEmpty()) {
                        EmptyText("暂无规则")
                        InsetDivider()
                    }
                    if (batchDeleteMode) {
                        if (visibleIds.isNotEmpty()) ContentActionRow(if (allVisibleSelected) "取消全选" else "全选") {
                            selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
                        }
                        ContentActionRow("删除（${selectedRules.size}）", emphasis = ContentActionEmphasis.DANGER) {
                            if (selectedRules.isEmpty()) Toast.makeText(context, "请先选择规则", Toast.LENGTH_SHORT).show()
                            else showDeleteConfirm = true
                        }
                    } else {
                        if (rules.isNotEmpty()) ContentActionRow("批量删除") {
                            batchDeleteMode = true
                            selectedIds = emptySet()
                        }
                        AddActionRow("新增规则", onClick = onAdd)
                    }
                }
            }
            if (visible.isEmpty()) {
                if (rules.isNotEmpty()) item { SettingsCard { EmptyText("没有匹配的规则") } }
            } else {
                visible.forEachIndexed { index, rule ->
                    item {
                        SettingsSectionTitle(
                            text = "规则 ${index + 1}"
                        )
                    }


                    item {
                        SettingsCard {
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = rule.name.ifBlank { "未命名规则" },
                                        value = index,
                                        summary = describeAutoReplyRule(rule)
                                    ),
                                    selected = rule.id in selectedIds,
                                    onClick = {
                                        selectedIds = if (rule.id in selectedIds) {
                                            selectedIds - rule.id
                                        } else {
                                            selectedIds + rule.id
                                        }
                                    }
                                )
                            } else {
                                SwitchRow(
                                    checked = rule.enabled,
                                    title = rule.name.ifBlank { "未命名规则" },
                                    summary = describeAutoReplyRule(rule),
                                    onCheckedChange = { onToggle(rule, it) }
                                )
                                InsetDivider()
                                ActionRow("编辑", "修改触发条件和回复步骤") { onEdit(rule) }
                                InsetDivider()
                                SecondaryActionRow("删除", "移除此规则") { onDelete(rule) }
                            }
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedRules.size} 条回复规则，此操作不可撤销。",
        labels = selectedRules.map { it.name.ifBlank { "未命名规则" } },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selectedRules
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedIds = emptySet()
            onBatchDelete(targets)
        }
    )
}

@Composable
private fun AutoReplyRuleEditorPage(
    rule: AutoReplyRule,
    sequence: AutoReplySequenceSession,
    isNew: Boolean,
    listState: LazyListState,
    onBack: () -> Unit,
    onSave: (AutoReplyRule) -> Unit,
    onDraftChange: (AutoReplyRule) -> Unit,
    onStepsChange: (AutoReplyRule, Boolean) -> Unit
) {
    val context = LocalContext.current
    var current by remember(rule.id) { mutableStateOf(rule) }
    LaunchedEffect(rule) { if (current != rule) current = rule }
    var optionPicker by remember { mutableStateOf<OptionPickerRequest?>(null) }
    var contactPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var groupMemberPicker by remember { mutableStateOf<GroupMemberPickerRequest?>(null) }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    val editorRoute = when {
        optionPicker != null -> AutoReplyEditorRoute.OptionPicker(optionPicker!!)
        contactPicker != null -> AutoReplyEditorRoute.ContactPicker(contactPicker!!)
        groupMemberPicker != null -> AutoReplyEditorRoute.GroupMemberPicker(groupMemberPicker!!)
        favoritePicker != null -> AutoReplyEditorRoute.FavoritePicker(favoritePicker!!)
        else -> AutoReplyEditorRoute.Main
    }
    SettingsRouteTransition(
        targetState = editorRoute,
        label = "AutoReplyEditorRoute",
        depthOf = { it.depth() }
    ) { route ->
        when (route) {
            is AutoReplyEditorRoute.OptionPicker -> {
                val request = route.request
                OptionPickerPage(
                    request = request,
                    onBack = { optionPicker = null },
                    onSelected = { request.onSelected(it); optionPicker = null }
                )
            }
            is AutoReplyEditorRoute.ContactPicker -> {
                val request = route.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { contactPicker = null },
                    onSelectionChange = { selected -> request.onValue(formatIds(selected)) }
                )
            }
            is AutoReplyEditorRoute.GroupMemberPicker -> {
                val request = route.request
                GroupMemberPickerPage(
                    context = context,
                    request = request,
                    onBack = { groupMemberPicker = null },
                    onSelectionChange = { entries -> request.onValue(formatIds(entries)) }
                )
            }
            is AutoReplyEditorRoute.FavoritePicker -> FavoritePickerPage(
                request = route.request,
                onBack = { favoritePicker = null }
            )
            AutoReplyEditorRoute.Main -> AutoReplyRuleEditorContent(
                rule = current,
                sequence = sequence,
                isNew = isNew,
                listState = listState,
                onRuleChange = {
                    current = it
                    onDraftChange(it)
                },
                onBack = onBack,
                onSave = { onSave(current) },
                onStepsChange = { steps, explicitlyEmpty ->
                    current = current.copy(steps = steps)
                    onStepsChange(current, explicitlyEmpty)
                },
                onOptionPicker = { optionPicker = it },
                onContactPicker = { contactPicker = it },
                onGroupMemberPicker = { groupMemberPicker = it },
                onFavoritePicker = { favoritePicker = it }
            )
        }
    }
}

@Composable
private fun AutoReplyRuleEditorContent(
    rule: AutoReplyRule,
    sequence: AutoReplySequenceSession,
    isNew: Boolean,
    listState: LazyListState,
    onRuleChange: (AutoReplyRule) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onStepsChange: (List<AutoReplyStep>, Boolean) -> Unit,
    onOptionPicker: (OptionPickerRequest) -> Unit,
    onContactPicker: (ContactPickerRequest) -> Unit,
    onGroupMemberPicker: (GroupMemberPickerRequest) -> Unit,
    onFavoritePicker: (FavoritePickerRequest) -> Unit
) {
    val current = rule
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    var maxCountText by remember(current.id) { mutableStateOf(current.maxReplyCount.toString()) }
    var cooldownText by remember(current.id) { mutableStateOf(current.cooldownSeconds.toString()) }
    AutoReplySequenceAutoScroll(sequence, listState, firstItemIndex = 9)
    PageScaffold(
        title = if (isNew) "新增规则" else "编辑规则",
        largeTitle = if (isNew) "新增规则" else "编辑规则",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        ) {
            item { SettingsSectionTitle(text = "触发条件") }
            item {
                SettingsCard {
                    SwitchRow(current.enabled, "规则", "关闭后保留配置但不触发") { onRuleChange(current.copy(enabled = it)) }
                    InsetDivider()
                    InputRow("规则名称", "用于列表里识别规则", current.name) { onRuleChange(current.copy(name = it)) }
                    InsetDivider()
                    PopupOptionRow(
                        title = "匹配方式",
                        summary = autoReplyMatchLabel(current.matchType),
                        options = autoReplyMatchOptions(),
                        currentValue = current.matchType,
                        onValueChanged = {
                            onRuleChange(
                                current.copy(
                                    matchType = it,
                                    keyword = if (it == AutoReplySettings.MATCH_ANY) "" else current.keyword
                                )
                            )
                        }
                    )
                    if (current.matchType != AutoReplySettings.MATCH_ANY) {
                        InsetDivider()
                        InputRow("关键词", if (current.matchType == AutoReplySettings.MATCH_REGEX && runCatching { java.util.regex.Pattern.compile(current.keyword) }.isFailure) "正则表达式无效；保留上次保存的值" else "多个关键词用 |、逗号或换行分隔", current.keyword, minLines = 2) {
                            onRuleChange(current.copy(keyword = it))
                        }
                    } else {
                        InsetDivider()
                        InputRow("排除关键词", "包含任一关键词时不触发，多个用 |、逗号或换行分隔", current.excludedKeywords, minLines = 2) {
                            onRuleChange(current.copy(excludedKeywords = it))
                        }
                    }
                }
            }
            item { SettingsSectionTitle(text = "范围") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "生效范围",
                        summary = autoReplyTargetLabel(current.targetMode),
                        options = autoReplyTargetOptions(),
                        currentValue = current.targetMode,
                        onValueChanged = { onRuleChange(current.copy(targetMode = it)) }
                    )
                    if (current.targetMode == AutoReplySettings.TARGET_SPECIFIC) {
                        InsetDivider()
                        ActionRow("选择指定好友", autoReplySelectedIdSummary(formatIds(current.targetIds.filter { !isAutoReplyGroupId(it) }))) {
                            onContactPicker(ContactPickerRequest(
                                title = "选择指定好友",
                                mode = ContactPickerMode.FRIENDS,
                                multiSelect = true,
                                existingValue = formatIds(current.targetIds.filter { !isAutoReplyGroupId(it) }),
                                onValue = { onRuleChange(current.copy(targetIds = replaceAutoReplyTypedIds(current.targetIds, parseIds(it), group = false))) },
                                enableLabels = true
                            ))
                        }
                        InsetDivider()
                        ActionRow("选择指定群聊", autoReplySelectedIdSummary(formatIds(current.targetIds.filter { isAutoReplyGroupId(it) }))) {
                            onContactPicker(ContactPickerRequest(
                                title = "选择指定群聊",
                                mode = ContactPickerMode.GROUPS,
                                multiSelect = true,
                                existingValue = formatIds(current.targetIds.filter { isAutoReplyGroupId(it) }),
                                onValue = { onRuleChange(current.copy(targetIds = replaceAutoReplyTypedIds(current.targetIds, parseIds(it), group = true))) }
                            ))
                        }
                        InsetDivider()
                        ActionRow("选择指定群成员", autoReplySelectedIdSummary(formatIds(current.includedGroupMembers))) {
                            onGroupMemberPicker(GroupMemberPickerRequest(
                                title = "选择指定群成员",
                                existingValue = formatIds(current.includedGroupMembers),
                                onValue = { onRuleChange(current.copy(includedGroupMembers = parseIds(it))) }
                            ))
                        }
                    } else {
                        InsetDivider()
                        ActionRow("选择排除好友", autoReplySelectedIdSummary(formatIds(current.excludedIds.filter { !isAutoReplyGroupId(it) }))) {
                            onContactPicker(ContactPickerRequest(
                                title = "选择排除好友",
                                mode = ContactPickerMode.FRIENDS,
                                multiSelect = true,
                                existingValue = formatIds(current.excludedIds.filter { !isAutoReplyGroupId(it) }),
                                onValue = { onRuleChange(current.copy(excludedIds = replaceAutoReplyTypedIds(current.excludedIds, parseIds(it), group = false))) },
                                enableLabels = true
                            ))
                        }
                        InsetDivider()
                        ActionRow("选择排除群聊", autoReplySelectedIdSummary(formatIds(current.excludedIds.filter { isAutoReplyGroupId(it) }))) {
                            onContactPicker(ContactPickerRequest(
                                title = "选择排除群聊",
                                mode = ContactPickerMode.GROUPS,
                                multiSelect = true,
                                existingValue = formatIds(current.excludedIds.filter { isAutoReplyGroupId(it) }),
                                onValue = { onRuleChange(current.copy(excludedIds = replaceAutoReplyTypedIds(current.excludedIds, parseIds(it), group = true))) }
                            ))
                        }
                        InsetDivider()
                        ActionRow("选择排除群成员", autoReplySelectedIdSummary(formatIds(current.excludedGroupMembers))) {
                            onGroupMemberPicker(GroupMemberPickerRequest(
                                title = "选择排除群成员",
                                existingValue = formatIds(current.excludedGroupMembers),
                                onValue = { onRuleChange(current.copy(excludedGroupMembers = parseIds(it))) }
                            ))
                        }
                    }
                }
            }
            item { SettingsSectionTitle(text = "触发") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "@ 触发",
                        summary = autoReplyAtLabel(current.atTrigger),
                        options = autoReplyAtOptions(),
                        currentValue = current.atTrigger,
                        onValueChanged = { onRuleChange(current.copy(atTrigger = it)) }
                    )
                    InsetDivider()
                    PopupOptionRow(
                        title = "拍一拍",
                        summary = autoReplyPatLabel(current.patTrigger),
                        options = autoReplyPatOptions(),
                        currentValue = current.patTrigger,
                        onValueChanged = { onRuleChange(current.copy(patTrigger = it)) }
                    )
                    InsetDivider()
                    TimeOfDayPickerRow("开始时间", current.startTime, allowEmpty = true) {
                        onRuleChange(current.copy(startTime = it))
                    }
                    InsetDivider()
                    TimeOfDayPickerRow("结束时间", current.endTime, allowEmpty = true) {
                        onRuleChange(current.copy(endTime = it))
                    }
                    InsetDivider()
                    NumberInputRow("最大回复次数", if (maxCountText.toIntOrNull() == null) "请输入非负整数；保留上次保存的值" else "0 表示不限制，按规则/会话/发送者统计", maxCountText) {
                        maxCountText = it
                        it.toIntOrNull()?.takeIf { value -> value >= 0 }?.let { value -> onRuleChange(current.copy(maxReplyCount = value)) }
                    }
                    InsetDivider()
                    NumberInputRow("回复冷却时间", if (cooldownText.toLongOrNull() == null) "请输入非负整数；保留上次保存的值" else "单位秒，0 表示不限制；同一规则在同一会话内冷却", cooldownText) {
                        cooldownText = it
                        it.toLongOrNull()?.takeIf { value -> value >= 0 }?.let { value -> onRuleChange(current.copy(cooldownSeconds = value)) }
                    }
                }
            }
            item { SettingsSectionTitle(text = "回复") }
            item {
                SettingsCard {
                    SwitchRow(current.replyAsQuote, "引用回复", "文本和 AI 可引用原消息回复") { onRuleChange(current.copy(replyAsQuote = it)) }
                }
            }
            autoReplySequenceSection(
                context = context,
                session = sequence,
                isNew = isNew,
                onStepsChange = onStepsChange,
                onContactPicker = onContactPicker,
                onFavoritePicker = onFavoritePicker
            )

            if (isNew) item {
                val error = when {
                    current.name.isBlank() -> "请填写规则名称"
                    current.matchType != AutoReplySettings.MATCH_ANY && current.keyword.isBlank() && current.patTrigger != AutoReplySettings.PAT_ME -> "请填写关键词"
                    current.matchType == AutoReplySettings.MATCH_REGEX && runCatching { java.util.regex.Pattern.compile(current.keyword) }.isFailure -> "正则表达式无效"
                    current.targetMode == AutoReplySettings.TARGET_SPECIFIC && current.targetIds.isEmpty() && current.includedGroupMembers.isEmpty() -> "请选择指定对象"
                    maxCountText.toIntOrNull()?.let { it >= 0 } != true || cooldownText.toLongOrNull()?.let { it >= 0 } != true -> "请填写有效的回复次数和冷却时间"
                    !sequence.isComplete() -> "请添加并完善回复步骤"
                    else -> null
                }
                PrimaryFormAction("创建规则", error, onSave)
            }
        }
    }
}

@Composable
private fun AutoReplyFriendPage(
    context: Context,
    title: String,
    sp: SharedPreferences,
    enabledKey: String,
    delayKey: String,
    tagEnabledKey: String,
    tagNameKey: String,
    sequence: AutoReplySequenceSession,
    listState: LazyListState,
    onBack: () -> Unit,
    automationKeys: AutoReplyFriendAutomationKeys?,
    dependencyHint: String? = null,
    onSelectLabels: (() -> Unit)?,
    onStepsChange: (List<AutoReplyStep>, Boolean) -> Unit
) {
    var contactPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    val pageRoute = when {
        contactPicker != null -> AutoReplyStepsRoute.ContactPicker(contactPicker!!)
        favoritePicker != null -> AutoReplyStepsRoute.FavoritePicker(favoritePicker!!)
        else -> AutoReplyStepsRoute.Main
    }
    SettingsRouteTransition(targetState = pageRoute, label = "AutoReplyFriendRoute", depthOf = { it.depth() }) { route ->
        when (route) {
            AutoReplyStepsRoute.Main -> AutoReplyFriendContent(
                context, title, sp, enabledKey, delayKey, tagEnabledKey, tagNameKey, sequence,
                listState, { if (sequence.entries.any { it.pending }) showDiscard = true else onBack() }, automationKeys, dependencyHint, onSelectLabels, onStepsChange,
                onContactPicker = { contactPicker = it },
                onFavoritePicker = { favoritePicker = it }
            )
            is AutoReplyStepsRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = route.request,
                onBack = { contactPicker = null },
                onSelectionChange = { selected -> route.request.onValue(formatIds(selected)) }
            )
            is AutoReplyStepsRoute.FavoritePicker -> FavoritePickerPage(route.request, onBack = { favoritePicker = null })
            is AutoReplyStepsRoute.OptionPicker -> Unit
        }
    }
    BatchDeleteConfirmDialog(
        show = showDiscard,
        title = "放弃尚未加入的回复？",
        message = "返回会放弃尚未加入的回复草稿。",
        labels = emptyList(),
        confirmText = "放弃并返回",
        onDismiss = { showDiscard = false },
        onConfirm = {
            sequence.entries.filter { it.pending }.forEach { sequence.remove(it.step.id) }
            showDiscard = false
            onBack()
        }
    )
}

@Composable
private fun AutoReplyFriendContent(
    context: Context,
    title: String,
    sp: SharedPreferences,
    enabledKey: String,
    delayKey: String,
    tagEnabledKey: String,
    tagNameKey: String,
    sequence: AutoReplySequenceSession,
    listState: LazyListState,
    onBack: () -> Unit,
    automationKeys: AutoReplyFriendAutomationKeys?,
    dependencyHint: String? = null,
    onSelectLabels: (() -> Unit)?,
    onStepsChange: (List<AutoReplyStep>, Boolean) -> Unit,
    onContactPicker: (ContactPickerRequest) -> Unit,
    onFavoritePicker: (FavoritePickerRequest) -> Unit
) {
    var delayMs by rememberSaveable { mutableStateOf(sp.getLong(delayKey, 2000L).coerceAtLeast(0L).toString()) }
    var tagName by rememberSaveable { mutableStateOf(sp.getString(tagNameKey, "") ?: "") }
    var labelDateFormat by rememberSaveable {
        mutableStateOf(sp.getString(automationKeys?.labelDateFormat ?: "", "yyyy-MM-dd") ?: "yyyy-MM-dd")
    }
    val selectedLabelNames = sp.getString(automationKeys?.labelSelectedNames ?: "", "") ?: ""
    var remarkDateFormat by rememberSaveable {
        mutableStateOf(sp.getString(automationKeys?.remarkDateFormat ?: "", "yyMMdd") ?: "yyMMdd")
    }
    var remarkCustomText by rememberSaveable {
        mutableStateOf(sp.getString(automationKeys?.remarkCustomText ?: "", "") ?: "")
    }
    val hasAutomation = automationKeys != null && onSelectLabels != null
    var labelDateEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.labelDateEnable, false) } ?: false) }
    var labelExistingEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.labelExistingEnable, false) } ?: false) }
    var remarkDateEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.remarkDateEnable, false) } ?: false) }
    var remarkCustomEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.remarkCustomEnable, false) } ?: false) }
    var tagEnabled by remember { mutableStateOf(sp.getBoolean(tagEnabledKey, false)) }
    var advancedExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    AutoReplySequenceAutoScroll(sequence, listState, firstItemIndex = 2)
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        ) {
            item {
                SettingsCard {
                    SwitchRow(sp, enabledKey, title, listOfNotNull("开启后自动处理这个场景", dependencyHint).joinToString("；"), false)
                    InsetDivider()
                    NumberInputRow("延迟发送", if (delayMs.toLongOrNull()?.let { it in 0L..600000L } != true) "请输入 0-600000；保留上次保存的值" else "单位 ms，通过好友后等待再发欢迎内容", delayMs) {
                        delayMs = it
                        it.toLongOrNull()?.takeIf { value -> value in 0L..600000L }?.let { value -> sp.edit().putLong(delayKey, value).apply() }
                    }

                }
            }
            autoReplySequenceSection(
                context = context,
                session = sequence,
                isNew = false,
                onStepsChange = onStepsChange,
                onContactPicker = onContactPicker,
                onFavoritePicker = onFavoritePicker
            )
            item {
                SettingsDisclosure(
                    title = if (hasAutomation) "标签与备注" else "标签设置",
                    summary = if (hasAutomation) "自动标签、备注及日期格式" else "处理后添加标签",
                    expanded = advancedExpanded,
                    onToggle = {
                        val invalidDate = (labelDateFormat.isNotBlank() && runCatching { java.text.SimpleDateFormat(labelDateFormat, Locale.getDefault()) }.isFailure) ||
                            (remarkDateFormat.isNotBlank() && runCatching { java.text.SimpleDateFormat(remarkDateFormat, Locale.getDefault()) }.isFailure)
                        if (advancedExpanded && invalidDate) Toast.makeText(context, "请先修正日期格式", Toast.LENGTH_SHORT).show()
                        else advancedExpanded = !advancedExpanded
                    }
                )
            }
            if (advancedExpanded) item {
                SettingsCard(modifier = Modifier.padding(top = 12.dp)) {
                    if (hasAutomation) {
                        val keys = automationKeys!!
                        SwitchRow(sp, keys.labelNewFriendEnable, "自动标签新加好友", "给好友添加“新加好友”标签", false)
                        InsetDivider()
                        SwitchRow(labelDateEnabled, "自动标签日期", "按日期格式生成一个标签") {
                            labelDateEnabled = it
                            sp.edit().putBoolean(keys.labelDateEnable, it).apply()
                        }
                        InsetDivider()
                        InputRow("标签日期格式", if (labelDateFormat.isNotBlank() && runCatching { java.text.SimpleDateFormat(labelDateFormat, Locale.getDefault()) }.isFailure) "日期格式无效；保留上次保存的值" else "例如 yyyy-MM-dd", labelDateFormat,
                            onEditingFinished = { if (advancedExpanded && it.isBlank()) sp.edit().putString(keys.labelDateFormat, "yyyy-MM-dd").apply() }) {
                            labelDateFormat = it
                            if (it.isNotBlank() && runCatching { java.text.SimpleDateFormat(it, Locale.getDefault()) }.isSuccess) sp.edit().putString(keys.labelDateFormat, it.trim()).apply()
                        }
                        InsetDivider()
                        SwitchRow(labelExistingEnabled, "自动标签已有标签", "把好友加入已选择的微信标签") {
                            labelExistingEnabled = it
                            sp.edit().putBoolean(keys.labelExistingEnable, it).apply()
                        }
                        InsetDivider()
                        ActionRow("选择标签", autoReplyLabelSummary(selectedLabelNames), onSelectLabels!!)
                        InsetDivider()
                        SwitchRow(sp, keys.remarkNewFriendEnable, "自动备注新加好友", "备注里加入“新加好友”", false)
                        InsetDivider()
                        SwitchRow(sp, keys.remarkNicknameSuffixEnable, "加昵称后面", "备注格式为微信昵称 + 备注内容", false)
                        InsetDivider()
                        SwitchRow(remarkDateEnabled, "自动备注日期", "备注里加入日期") {
                            remarkDateEnabled = it
                            sp.edit().putBoolean(keys.remarkDateEnable, it).apply()
                        }
                        InsetDivider()
                        InputRow("备注日期格式", if (remarkDateFormat.isNotBlank() && runCatching { java.text.SimpleDateFormat(remarkDateFormat, Locale.getDefault()) }.isFailure) "日期格式无效；保留上次保存的值" else "例如 yyMMdd", remarkDateFormat,
                            onEditingFinished = { if (advancedExpanded && it.isBlank()) sp.edit().putString(keys.remarkDateFormat, "yyMMdd").apply() }) {
                            remarkDateFormat = it
                            if (it.isNotBlank() && runCatching { java.text.SimpleDateFormat(it, Locale.getDefault()) }.isSuccess) sp.edit().putString(keys.remarkDateFormat, it.trim()).apply()
                        }
                        InsetDivider()
                        SwitchRow(remarkCustomEnabled, "自动备注自定义文本", "备注里加入自定义内容") {
                            remarkCustomEnabled = it
                            sp.edit().putBoolean(keys.remarkCustomEnable, it).apply()
                        }
                        InsetDivider()
                        InputRow("备注自定义文本", "例如 渠道A", remarkCustomText,
                            onEditingFinished = { if (advancedExpanded && it.isBlank()) sp.edit().putString(keys.remarkCustomText, "").apply() }) {
                            remarkCustomText = it
                            if (it.isNotBlank()) sp.edit().putString(keys.remarkCustomText, it.trim()).apply()
                        }
                    } else {
                        SwitchRow(tagEnabled, "自动打标签", "处理后把好友加入指定标签") {
                            tagEnabled = it
                            sp.edit().putBoolean(tagEnabledKey, it).apply()
                        }
                        InsetDivider()
                        InputRow("标签名称", "标签需要微信里已存在", tagName,
                            onEditingFinished = { if (advancedExpanded && it.isBlank()) sp.edit().putString(tagNameKey, "").apply() }) {
                            tagName = it
                            if (it.isNotBlank()) sp.edit().putString(tagNameKey, it.trim()).apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoReplyLabelPickerPage(
    context: Context,
    sp: SharedPreferences,
    selectedKey: String,
    listState: LazyListState,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf<List<ContactLabelBean>>(emptyList()) }
    var selected by remember {
        mutableStateOf(splitAutoReplyNames(sp.getString(selectedKey, "") ?: "").toSet())
    }
    LaunchedEffect(Unit) {
        loading = true
        error = ""
        val api = WeChatApis.contact().contacts()
        if (api == null || !api.isAvailable) {
            labels = emptyList()
            error = "联系人标签不可用"
            loading = false
        } else {
            Thread({
                val result = runCatching { api.getContactLabelList() }
                Handler(Looper.getMainLooper()).post {
                    labels = result.getOrDefault(emptyList())
                        .filter { it.labelName.isNotBlank() || it.labelId.isNotBlank() }
                        .sortedBy { it.labelName.ifBlank { it.labelId }.lowercase(Locale.US) }
                    error = result.exceptionOrNull()?.message.orEmpty()
                    loading = false
                }
            }, "HchatAutoReplyLabels").start()
        }
    }
    val visible = labels.filter { label ->
        val name = label.labelName.ifBlank { label.labelId }
        val key = query.trim().lowercase(Locale.US)
        key.isBlank() || name.lowercase(Locale.US).contains(key)
    }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "选择标签",
        largeTitle = "选择标签",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        ) {
            item { SettingsSectionTitle(text = "搜索") }
            item {
                SettingsCard {
                    InputRow("关键词", "按标签名筛选", query) { query = it }
                }
            }
            item { SettingsSectionTitle(text = "标签") }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入标签...") } }
                error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配标签") } }
                else -> visible.forEach { label ->
                    val name = label.labelName.ifBlank { label.labelId }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                            cornerRadius = 14.dp
                        ) {
                            AutoReplyLabelRow(
                                name = name,
                                summary = "${label.userNameList.size} 人",
                                selected = selected.contains(name),
                                onClick = {
                                    selected = if (selected.contains(name)) selected - name else selected + name
                                    sp.edit().putString(selectedKey, selected.joinToString(";;;")).apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoReplyLabelRow(
    name: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).ifEmpty { "签" },
                color = MiuixTheme.colorScheme.onSecondaryVariant,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = name, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        SelectionMark(selected = selected, multiSelect = true)
    }
}

private fun autoReplyLabelSummary(value: String): String {
    val names = splitAutoReplyNames(value)
    return if (names.isEmpty()) "未选择" else names.take(3).joinToString("、") + if (names.size > 3) " 等 ${names.size} 个" else ""
}

private fun splitAutoReplyNames(value: String): List<String> =
    value.split(";;;", "|", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

@Composable
private fun AutoReplyAiPage(context: Context, sp: SharedPreferences, onBack: () -> Unit) {
    val settings = remember { AutoReplySettings(context) }
    val scope = rememberCoroutineScope()
    var configs by remember { mutableStateOf(settings.zhiliaConfigs()) }
    var activeName by remember {
        mutableStateOf(sp.getString(AutoReplySettings.KEY_ZHILIA_ACTIVE_CONFIG, AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME).orEmpty().ifBlank { AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME })
    }
    val activeConfig = configs.firstOrNull { it.name == activeName } ?: configs.first()
    var cfgName by remember(activeName) { mutableStateOf(activeConfig.name) }
    var apiKey by remember(activeName) { mutableStateOf(activeConfig.apiKey) }
    var apiBase by remember(activeName) { mutableStateOf(activeConfig.apiBaseUrl) }
    var apiPath by remember(activeName) { mutableStateOf(activeConfig.apiPath) }
    var model by remember(activeName) { mutableStateOf(activeConfig.model) }
    var prompt by remember(activeName) { mutableStateOf(activeConfig.systemPrompt) }
    var contextLimit by remember(activeName) { mutableStateOf(activeConfig.contextLimit.toString()) }
    var streamEnabled by remember { mutableStateOf(sp.getBoolean(AutoReplySettings.KEY_AI_STREAM, AutoReplySettings.DEFAULT_AI_STREAM)) }
    var edited by remember { mutableStateOf(false) }
    var modelQuery by remember { mutableStateOf("") }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var favoriteModels by remember(apiBase) { mutableStateOf(settings.favoriteModels(apiBase)) }
    var loadingText by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }
    var route by remember { mutableStateOf<AutoReplyAiRoute>(AutoReplyAiRoute.Zhilia) }
    var zhiliaPendingName by remember { mutableStateOf(activeName) }
    var pendingModel by remember { mutableStateOf(model) }
    val zhiliaListState = rememberLazyListState()
    val zhiliaConfigListState = rememberLazyListState()
    val zhiliaModelListState = rememberLazyListState()

    fun currentZhiliaConfig(): AutoReplyZhiliaConfig {
        return AutoReplyZhiliaConfig(
            name = cfgName,
            apiKey = apiKey.trim(),
            apiBaseUrl = apiBase.trim(),
            apiPath = apiPath.trim(),
            model = model.trim(),
            systemPrompt = prompt,
            contextLimit = contextLimit.toIntOrNull() ?: AutoReplySettings.DEFAULT_AI_CONTEXT_LIMIT
        ).normalized()
    }

    fun finishZhiliaEdits() {
        if (edited && sp.getBoolean(AutoReplySettings.KEY_AI_CLEAR_CONTEXT_ON_SAVE, AutoReplySettings.DEFAULT_AI_CLEAR_CONTEXT_ON_SAVE)) {
            AutoReplyRuntime.clearAiHistories()
        }
        edited = false
    }

    DisposableEffect(Unit) {
        onDispose { finishZhiliaEdits() }
    }

    fun updateZhilia(change: (AutoReplyZhiliaConfig) -> AutoReplyZhiliaConfig) {
        val currentConfigs = settings.zhiliaConfigs()
        val current = currentConfigs.firstOrNull { it.name == activeName } ?: currentConfigs.first()
        val nextConfig = change(current).normalized()
        if (nextConfig == current || currentConfigs.any { it.name == nextConfig.name && it.name != current.name }) return
        settings.saveZhiliaConfigs(currentConfigs.map { if (it.name == current.name) nextConfig else it }, nextConfig.name)
        configs = settings.zhiliaConfigs()
        activeName = nextConfig.name
        edited = true
    }

    fun switchZhiliaTo(name: String) {
        if (name.isBlank()) {
            Toast.makeText(context, "请选择配置", Toast.LENGTH_SHORT).show()
            return
        }
        if (name == activeName) {
            zhiliaPendingName = activeName
            Toast.makeText(context, "已是当前配置", Toast.LENGTH_SHORT).show()
            return
        }
        settings.saveZhiliaConfigs(settings.zhiliaConfigs(), name)
        configs = settings.zhiliaConfigs()
        activeName = settings.activeZhiliaName()
        zhiliaPendingName = activeName
        edited = true
        finishZhiliaEdits()
    }

    fun uniqueConfigName(base: String): String {
        val names = configs.map { it.name }.toSet()
        if (base !in names) return base
        var index = 2
        while ("$base$index" in names) index++
        return "$base$index"
    }

    val visibleModels = remember(fetchedModels, modelQuery, favoriteModels) {
        val keyword = modelQuery.trim().lowercase(Locale.US)
        fetchedModels
            .filter { keyword.isBlank() || it.lowercase(Locale.US).contains(keyword) }
            .sortedWith(compareByDescending<String> { it in favoriteModels }.thenBy { it.lowercase(Locale.US) })
    }

    SettingsRouteTransition(
        targetState = route,
        label = "AutoReplyAiRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            AutoReplyAiRoute.Zhilia -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "智聊AI配置",
                    largeTitle = "智聊AI配置",
                    scrollBehavior = scrollBehavior,
                    onBack = onBack,
                    showBackIcon = false
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = zhiliaListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
                    ) {
                        item { SettingsSectionTitle(text = "当前配置") }
                        item {
                            SettingsCard {
                                InfoRow("当前启用", activeName)
                                InsetDivider()
                                InputRow("配置名称", if (cfgName.isBlank() || configs.any { it.name == cfgName.trim() && it.name != activeName }) "名称不能为空或与其他配置重复" else "例如 DeepSeek 主账号", cfgName,
                                    onEditingFinished = { value -> if (value.isNotBlank()) updateZhilia { it.copy(name = value.trim()) } }) { cfgName = it }
                                InsetDivider()
                                InputRow("API Key", "OpenAI 兼容接口密钥", apiKey, onEditingFinished = { value -> if (value.isBlank()) updateZhilia { it.copy(apiKey = "") } }) {
                                    apiKey = it
                                    if (it.isNotBlank()) updateZhilia { config -> config.copy(apiKey = it.trim()) }
                                }
                                InsetDivider()
                                InputRow("API 地址", "例如 https://api.xxx.com/v1", apiBase, onEditingFinished = { value -> if (value.isBlank()) updateZhilia { it.copy(apiBaseUrl = AutoReplySettings.DEFAULT_AI_API_BASE) } }) {
                                    apiBase = it
                                    if (it.isNotBlank()) updateZhilia { config -> config.copy(apiBaseUrl = it.trim()) }
                                }
                                InsetDivider()
                                InputRow("API 路径", "默认 /chat/completions", apiPath, onEditingFinished = { value -> if (value.isBlank()) updateZhilia { it.copy(apiPath = AutoReplySettings.DEFAULT_AI_API_PATH) } }) {
                                    apiPath = it
                                    if (it.isNotBlank()) updateZhilia { config -> config.copy(apiPath = it.trim()) }
                                }
                                InsetDivider()
                                InputRow("模型", "例如 deepseek-ai/DeepSeek-V3", model, onEditingFinished = { value -> if (value.isBlank()) updateZhilia { it.copy(model = AutoReplySettings.DEFAULT_AI_MODEL) } }) {
                                    model = it
                                    if (it.isNotBlank()) updateZhilia { config -> config.copy(model = it.trim()) }
                                }
                                InsetDivider()
                                InputRow("系统指令", "AI 角色设定和回复要求", prompt, minLines = 4, onEditingFinished = { value -> if (value.isBlank()) updateZhilia { it.copy(systemPrompt = value) } }) {
                                    prompt = it
                                    if (it.isNotBlank()) updateZhilia { config -> config.copy(systemPrompt = it) }
                                }
                                InsetDivider()
                                NumberInputRow("上下文轮数", if (contextLimit.toIntOrNull()?.let { it in 0..50 } != true) "请输入 0-50；保留上次保存的值" else "0 表示不保留上下文", contextLimit) {
                                    contextLimit = it
                                    it.toIntOrNull()?.takeIf { value -> value in 0..50 }?.let { value -> updateZhilia { config -> config.copy(contextLimit = value) } }
                                }
                                InsetDivider()
                                SwitchRow(streamEnabled, "流式请求", "失败时会自动尝试非流式") {
                                    streamEnabled = it
                                    sp.edit().putBoolean(AutoReplySettings.KEY_AI_STREAM, it).apply()
                                    edited = true
                                }
                                InsetDivider()
                                SwitchRow(sp, AutoReplySettings.KEY_AI_CLEAR_CONTEXT_ON_SAVE, "编辑完成后清空上下文", "修改配置后避免继续沿用旧对话", AutoReplySettings.DEFAULT_AI_CLEAR_CONTEXT_ON_SAVE)
                            }
                        }
                        item { SettingsSectionTitle(text = "配置管理") }
                        item {
                            SettingsCard {
                                ActionRow("配置列表", "${configs.size} 个配置，当前启用：$activeName") {
                                    zhiliaPendingName = activeName
                                    route = AutoReplyAiRoute.ZhiliaConfigs
                                }
                            }
                        }
                        item { SettingsSectionTitle(text = "模型工具") }
                        item {
                            SettingsCard {
                                ActionRow("模型选择", model.ifBlank { "未设置模型" }) {
                                    pendingModel = model
                                    route = AutoReplyAiRoute.ZhiliaModels
                                }
                                InsetDivider()
                                ActionRow("测试连通性", testResult.ifBlank { "测试当前模型的流式和非流式请求" }) {
                                    val config = currentZhiliaConfig().toAiConfig(sp.getBoolean(AutoReplySettings.KEY_AI_STREAM, AutoReplySettings.DEFAULT_AI_STREAM))
                                    testResult = "正在测试..."
                                    scope.launch {
                                        testResult = withContext(Dispatchers.IO) {
                                            AutoReplyRuntime.testAiConnectivity(config)
                                        }
                                        Toast.makeText(context, testResult, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        item { SettingsSectionTitle(text = "运行状态") }
                        item {
                            SettingsCard {
                                SecondaryActionRow("立即清空上下文", "清除当前运行中的 AI 对话记忆") {
                                    AutoReplyRuntime.clearAiHistories()
                                    Toast.makeText(context, "AI 上下文已清空", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            AutoReplyAiRoute.ZhiliaConfigs -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "智聊配置列表",
                    largeTitle = "智聊配置列表",
                    scrollBehavior = scrollBehavior,
                    onBack = { route = AutoReplyAiRoute.Zhilia },
                    showBackIcon = false
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = zhiliaConfigListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
                    ) {
                        item { SettingsSectionTitle(text = "配置") }
                        configs.forEach { config ->
                            item {
                                SettingsCard(modifier = Modifier.padding(bottom = 8.dp)) {
                                    ActionRow(
                                        title = config.name,
                                        summary = buildString {
                                            append(config.model.ifBlank { "未设置模型" })
                                            if (config.name == activeName) append(" · 当前")
                                            if (config.name == zhiliaPendingName && config.name != activeName) append(" · 已选择")
                                        }
                                    ) {
                                        switchZhiliaTo(config.name)
                                    }
                                }
                            }
                        }

                        item { SettingsSectionTitle(text = "操作") }
                        item {
                            SettingsCard {
                                ContentActionRow("复制") {
                                    val source = settings.zhiliaConfigs().firstOrNull { it.name == activeName } ?: activeConfig
                                    val newName = uniqueConfigName("${source.name}_副本")
                                    settings.saveZhiliaConfigs(configs + source.copy(name = newName), activeName)
                                    configs = settings.zhiliaConfigs()
                                    zhiliaPendingName = newName
                                }
                                if (configs.size > 1) ContentActionRow("删除", emphasis = ContentActionEmphasis.DANGER) {
                                    val next = configs.filterNot { it.name == activeName }
                                    val nextActive = next.firstOrNull()?.name ?: AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME
                                    settings.saveZhiliaConfigs(next, nextActive)
                                    configs = settings.zhiliaConfigs()
                                    activeName = settings.activeZhiliaName()
                                    zhiliaPendingName = activeName
                                    edited = true
                                }
                                AddActionRow("新增配置") { route = AutoReplyAiRoute.ZhiliaNew }
                            }
                        }
                    }
                }
            }
            AutoReplyAiRoute.ZhiliaNew -> NewZhiliaConfigPage(
                existingNames = configs.mapTo(hashSetOf()) { it.name },
                onBack = { route = AutoReplyAiRoute.ZhiliaConfigs },
                onCreate = { config ->
                    settings.saveZhiliaConfigs(configs + config, activeName)
                    configs = settings.zhiliaConfigs()
                    zhiliaPendingName = config.name
                    route = AutoReplyAiRoute.ZhiliaConfigs
                }
            )
            AutoReplyAiRoute.ZhiliaModels -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "智聊模型选择",
                    largeTitle = "智聊模型选择",
                    scrollBehavior = scrollBehavior,
                    onBack = { route = AutoReplyAiRoute.Zhilia },
                    showBackIcon = false
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = zhiliaModelListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
                    ) {
                        item { SettingsSectionTitle(text = "拉取模型") }
                        item {
                            SettingsCard {
                                ActionRow("拉取模型列表", if (loadingText.isBlank()) "从当前 API 地址获取 /models" else loadingText) {
                                    val base = apiBase
                                    val key = apiKey
                                    loadingText = "正在拉取模型..."
                                    scope.launch {
                                        val models = withContext(Dispatchers.IO) {
                                            AutoReplyRuntime.fetchAiModels(base, key)
                                        }
                                        fetchedModels = models
                                        loadingText = ""
                                        Toast.makeText(context, if (models.isEmpty()) "未获取到模型" else "已获取 ${models.size} 个模型", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        if (visibleModels.isNotEmpty()) {
                            item { SettingsSectionTitle(text = "模型列表 · ${visibleModels.size}") }
                            item {
                                SettingsCard(modifier = Modifier.padding(bottom = 8.dp)) {
                                    InputRow("搜索", "输入模型名过滤", modelQuery) { modelQuery = it }
                                }
                            }
                            visibleModels.take(80).forEach { itemModel ->
                                item {
                                    ZhiliaModelSelectCard(
                                        modelName = itemModel,
                                        currentModel = model,
                                        pendingModel = pendingModel,
                                        favorite = itemModel in favoriteModels,
                                        onSelect = {
                                            pendingModel = itemModel
                                            model = itemModel
                                            updateZhilia { it.copy(model = itemModel) }
                                        },
                                        onToggleFavorite = {
                                            val next = if (itemModel in favoriteModels) favoriteModels - itemModel else favoriteModels + itemModel
                                            favoriteModels = next
                                            settings.saveFavoriteModels(apiBase, next)
                                            Toast.makeText(context, if (itemModel in next) "已收藏模型" else "已取消收藏", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        } else {
                            item { SettingsCard { EmptyText("暂无模型列表，先点击“拉取模型列表”。") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZhiliaModelSelectCard(
    modelName: String,
    currentModel: String,
    pendingModel: String,
    favorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val selected = modelName == pendingModel
    val current = modelName == currentModel
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 14.dp
    ) {
        var pressed by remember { mutableStateOf(false) }
        val pressFeedbackColor = rememberPressFeedbackColor(pressed)
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(pressFeedbackColor)
                .responsiveTap(
                    onClick = onSelect,
                    onPressedChange = { pressed = it }
                )
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = modelName, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    val state = buildString {
                        if (current) append("当前")
                        if (selected && !current) {
                            if (isNotEmpty()) append(" · ")
                            append("已选择")
                        }
                        if (favorite) {
                            if (isNotEmpty()) append(" · ")
                            append("已收藏")
                        }
                        if (isEmpty()) append("点击选择")
                    }
                    Text(text = state, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                }
                Text(
                    text = if (selected) "已选" else "选择",
                    color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            InsetDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggleFavorite() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    text = if (favorite) "取消收藏" else "收藏",
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun NewZhiliaConfigPage(
    existingNames: Set<String>,
    onBack: () -> Unit,
    onCreate: (AutoReplyZhiliaConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var apiBase by remember { mutableStateOf(AutoReplySettings.DEFAULT_AI_API_BASE) }
    var apiPath by remember { mutableStateOf(AutoReplySettings.DEFAULT_AI_API_PATH) }
    var model by remember { mutableStateOf(AutoReplySettings.DEFAULT_AI_MODEL) }
    var prompt by remember { mutableStateOf("") }
    var contextLimit by remember { mutableStateOf(AutoReplySettings.DEFAULT_AI_CONTEXT_LIMIT.toString()) }
    val valid = name.isNotBlank() && name.trim() !in existingNames && apiBase.isNotBlank() && apiPath.isNotBlank() && model.isNotBlank() && contextLimit.toIntOrNull()?.let { it in 0..50 } == true
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "新增智聊配置",
        largeTitle = "新增智聊配置",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        ) {
            item { SettingsSectionTitle(text = "配置") }
            item {
                SettingsCard {
                    InputRow("配置名称", if (name.trim() in existingNames) "名称已存在" else "用于区分智聊配置", name) { name = it }
                    InsetDivider(); InputRow("API Key", "OpenAI 兼容接口密钥", apiKey) { apiKey = it }
                    InsetDivider(); InputRow("API 地址", "例如 https://api.xxx.com/v1", apiBase) { apiBase = it }
                    InsetDivider(); InputRow("API 路径", "默认 /chat/completions", apiPath) { apiPath = it }
                    InsetDivider(); InputRow("模型", "例如 deepseek-ai/DeepSeek-V3", model) { model = it }
                    InsetDivider(); InputRow("系统指令", "AI 角色设定和回复要求", prompt, minLines = 4) { prompt = it }
                    InsetDivider(); NumberInputRow("上下文轮数", if (contextLimit.toIntOrNull()?.let { it in 0..50 } != true) "请输入 0–50" else "0 表示不保留上下文", contextLimit) { contextLimit = it }
                }
            }

            item { SettingsSectionTitle(text = "操作") }
            item {
                SettingsCard {
                    ContentActionRow("创建", enabled = valid, emphasis = ContentActionEmphasis.PRIMARY) {
                        onCreate(AutoReplyZhiliaConfig(name.trim(), apiKey.trim(), apiBase.trim(), apiPath.trim(), model.trim(), prompt, contextLimit.toInt()))
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoReplySequenceAutoScroll(session: AutoReplySequenceSession, listState: LazyListState, firstItemIndex: Int) {
    val scrollOffset = with(LocalDensity.current) { 80.dp.roundToPx() }
    LaunchedEffect(session.scrollRequest) {
        val id = session.scrollRequest ?: return@LaunchedEffect
        val index = session.entries.indexOfFirst { it.step.id == id }
        if (index >= 0) {
            withFrameNanos { }
            listState.animateScrollToItem(firstItemIndex + index, -scrollOffset)
        }
        session.consumeScrollRequest(id)
    }
}

private fun LazyListScope.autoReplySequenceSection(
    context: Context,
    session: AutoReplySequenceSession,
    isNew: Boolean,
    onStepsChange: (List<AutoReplyStep>, Boolean) -> Unit,
    onContactPicker: (ContactPickerRequest) -> Unit,
    onFavoritePicker: (FavoritePickerRequest) -> Unit
) {
    val entries = session.entries
    fun publish() {
        val steps = session.stepsForDraft()
        onStepsChange(steps, !isNew && steps.isEmpty())
    }
    item(key = "reply-sequence-heading") {
        var showPicker by remember { mutableStateOf(false) }
        val add = { showPicker = true }
        SequenceSectionHeader("回复步骤", entries.size, if (entries.isNotEmpty()) add else null)
        if (entries.isEmpty()) SequenceEmptyCard("尚未添加回复", "添加第一条回复", add)
        SequenceTypePickerDialog(
            show = showPicker,
            title = "选择回复类型",
            options = autoReplyStepOptions().map { it.value to it.label },
            onDismiss = { showPicker = false },
            onSelect = { mode -> session.add(mode); if (isNew) publish() }
        )
    }
    if (entries.isNotEmpty()) {
        entries.forEachIndexed { index, entry ->
            val step = entry.step
            item(key = "reply-sequence-${step.id}") {
                val preview = when {
                    AutoReplySettings.isRemovedReplyMode(step.mode) -> "已移除的类型，不会执行"
                    AutoReplySettings.isAiReplyMode(step.mode) -> "使用 AI 生成回复"
                    step.content.isBlank() -> "未完成"
                    autoReplyStepUsesFilePicker(step.mode) -> autoReplyFileSummary(step.content)
                    step.mode == AutoReplySettings.REPLY_FAVORITE -> favoriteSummary(step.content)
                    autoReplyStepUsesContactPicker(step.mode) -> autoReplySelectedIdSummary(step.content)
                    else -> step.content
                }
                val delay = "延迟 ${step.delayMs} ms" + if (step.randomDelay) "，随机追加 0–2 秒" else ""
                val status = if (entry.pending) " · 尚未加入" else ""
                SequenceItemCard(
                    title = "第 ${index + 1} 条 · ${autoReplyStepLabel(step.mode).removePrefix("发送")}",
                    summary = "$preview · $delay$status",
                    expanded = session.expandedId == step.id,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
                    onToggle = { session.expandedId = if (session.expandedId == step.id) null else step.id }
                ) {
                    PopupOptionRow(
                        title = "回复类型",
                        summary = autoReplyStepLabel(step.mode),
                        options = if (AutoReplySettings.isRemovedReplyMode(step.mode)) {
                            listOf(OptionItem("已移除的回复类型", step.mode, "")) + autoReplyStepOptions()
                        } else autoReplyStepOptions(),
                        currentValue = step.mode,
                        onValueChanged = { mode ->
                            session.update(step.id) { current ->
                                current.copy(mode = mode, content = when {
                                    AutoReplySettings.isAiReplyMode(mode) -> ""
                                    current.mode == mode -> current.content
                                    current.mode == AutoReplySettings.REPLY_FAVORITE || mode == AutoReplySettings.REPLY_FAVORITE -> ""
                                    else -> current.content
                                })
                            }
                            if (!entry.pending) publish()
                        }
                    )
                    InsetDivider()
                    if (AutoReplySettings.isRemovedReplyMode(step.mode)) {
                        InfoRow("已移除的回复类型", "此步骤不会执行，可手动删除或更换回复类型")
                        InsetDivider()
                    } else if (!AutoReplySettings.isAiReplyMode(step.mode)) {
                        AutoReplyStepContentEditor(
                            context = context,
                            step = step,
                            onStepChange = { changed ->
                                if (session.update(step.id) { it.copy(content = changed.content) } && !entry.pending) publish()
                            },
                            onClear = {
                                if (session.update(step.id) { it.copy(content = "") } && !entry.pending) publish()
                            },
                            onContactPicker = onContactPicker,
                            onFavoritePicker = onFavoritePicker,
                            fieldState = session.textState(step.id),
                            currentStep = { session.find(step.id)?.step }
                        )
                        InsetDivider()
                    }
                    val delayState = session.delayState(step.id)
                    NumberInputRow("发送前延迟", if (session.delayValid(step.id)) "单位 ms" else "请输入 0–600000，保留上次有效配置", delayState.value) { value ->
                        delayState.value = value
                        value.toLongOrNull()?.takeIf { it in 0L..600000L }?.let { delayMs ->
                            if (session.update(step.id) { it.copy(delayMs = delayMs) } && !entry.pending) publish()
                        }
                    }
                    InsetDivider()
                    SwitchRow(step.randomDelay, "随机追加延迟", "发送前额外随机等待 0–2 秒") { value ->
                        if (session.update(step.id) { it.copy(randomDelay = value) } && !entry.pending) publish()
                    }
                    InsetDivider()
                    if (entry.pending) {
                        ContentActionRow("添加此条", emphasis = ContentActionEmphasis.PRIMARY) {
                            if (session.commit(step.id)) publish()
                            else Toast.makeText(context, "请填写有效的回复内容和延迟", Toast.LENGTH_SHORT).show()
                        }
                        ContentActionRow("放弃新增") { session.remove(step.id) }
                    } else {
                        SequenceItemActions(
                            canMoveUp = index > 0,
                            canMoveDown = index < entries.lastIndex,
                            onMoveUp = { if (session.move(step.id, -1)) publish() },
                            onMoveDown = { if (session.move(step.id, 1)) publish() },
                            onDelete = { if (session.remove(step.id)) publish() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoReplyStepContentEditor(
    context: Context,
    step: AutoReplyStep,
    onStepChange: (AutoReplyStep) -> Unit,
    onClear: () -> Unit,
    onContactPicker: (ContactPickerRequest) -> Unit,
    onFavoritePicker: (FavoritePickerRequest) -> Unit,
    fieldState: MutableState<TextFieldValue>? = null,
    currentStep: () -> AutoReplyStep? = { step }
) {
    when {
        step.mode == AutoReplySettings.REPLY_TEXT || step.mode == AutoReplySettings.REPLY_XML -> {
            VariableInputRow(
                title = if (step.mode == AutoReplySettings.REPLY_XML) "XML 内容" else "回复内容",
                summary = autoReplyStepContentHint(step.mode),
                value = step.content,
                variables = autoReplyTemplateVariables,
                minLines = 4,
                fieldState = fieldState
            ) {
                onStepChange(step.copy(content = it))
            }
        }
        autoReplyStepUsesContactPicker(step.mode) -> {
            val mode = if (step.mode == AutoReplySettings.REPLY_CARD) ContactPickerMode.FRIENDS else ContactPickerMode.GROUPS
            ActionRow(
                title = autoReplyPickerTitle(step.mode),
                summary = autoReplySelectedIdSummary(step.content)
            ) {
                onContactPicker(
                    ContactPickerRequest(
                        title = autoReplyPickerTitle(step.mode),
                        mode = mode,
                        multiSelect = true,
                        existingValue = formatIds(autoReplySplitContent(step.content)),
                        onValue = { value ->
                            currentStep()?.takeIf { it.mode == step.mode }?.let { current ->
                                if (value.isEmpty()) onClear() else onStepChange(current.copy(content = value))
                            }
                        },
                        enableLabels = step.mode == AutoReplySettings.REPLY_CARD
                    )
                )
            }
            if (step.content.isNotBlank()) {
                InsetDivider()
                SecondaryActionRow("清空已选", "移除当前已选择内容") {
                    onClear()
                }
            }
        }
        step.mode == AutoReplySettings.REPLY_FAVORITE -> {
            ActionRow(
                title = autoReplyPickerTitle(step.mode),
                summary = favoriteSummary(step.content)
            ) {
                onFavoritePicker(
                    FavoritePickerRequest(
                        title = autoReplyPickerTitle(step.mode),
                        existingValue = step.content,
                        onValue = { value ->
                            currentStep()?.takeIf { it.mode == step.mode }?.let { current ->
                                if (value.isEmpty()) onClear() else onStepChange(current.copy(content = value))
                            }
                        },
                        multiSelect = true,
                        delimiter = ";;;"
                    )
                )
            }
            if (step.content.isNotBlank()) {
                InsetDivider()
                SecondaryActionRow("清空已选收藏", "移除当前回复类型的收藏") {
                    onClear()
                }
            }
        }
        autoReplyStepUsesFilePicker(step.mode) -> {
            ActionRow(
                title = autoReplyPickerTitle(step.mode),
                summary = autoReplyFileSummary(step.content)
            ) {
                val activity = context as? Activity
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                    return@ActionRow
                }
                AutoReplyFilePickerBridge.launch(activity, step.mode) { paths ->
                    val current = currentStep()?.takeIf { it.mode == step.mode } ?: return@launch
                    val merged = (autoReplySplitContent(current.content) + paths)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    if (merged.isNotEmpty()) {
                        onStepChange(current.copy(content = merged.joinToString(";;;")))
                        Toast.makeText(context, "已选择 ${paths.size} 个文件", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (step.content.isNotBlank()) {
                InsetDivider()
                SecondaryActionRow("清空已选文件", "移除当前回复类型的文件") {
                    onClear()
                }
            }
        }
        else -> {
            InputRow("回复内容", autoReplyStepContentHint(step.mode), step.content, minLines = 2) {
                onStepChange(step.copy(content = it))
            }
        }
    }
}

private fun describeAutoReplyRule(rule: AutoReplyRule): String {
    val state = if (rule.enabled) "启用" else "关闭"
    val keyword = if (rule.matchType == AutoReplySettings.MATCH_ANY) "任意消息" else rule.keyword.ifBlank { "未填关键词" }
    return buildList {
        add(state)
        add(autoReplyMatchLabel(rule.matchType))
        add(keyword)
        add(autoReplyTargetLabel(rule.targetMode))
        if (rule.cooldownSeconds > 0L) add("冷却 ${rule.cooldownSeconds} 秒")
        add(describeAutoReplySteps(rule.steps))
    }.joinToString(" · ")
}

private fun describeAutoReplySteps(steps: List<AutoReplyStep>): String {
    if (steps.isEmpty()) return "暂无回复"
    return steps.take(3).joinToString("、") { autoReplyStepLabel(it.mode) } +
        if (steps.size > 3) " 等 ${steps.size} 条" else "，共 ${steps.size} 条"
}

private fun replaceAutoReplyTypedIds(old: Set<String>, picked: Set<String>, group: Boolean): Set<String> {
    return (old.filter { isAutoReplyGroupId(it) != group } + picked)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

private fun isAutoReplyGroupId(value: String): Boolean =
    value.endsWith("@chatroom") || value.endsWith("@im.chatroom")

private fun conversationRuleCategoryForId(value: String): ConversationRuleCategory {
    val normalized = value.trim().lowercase(Locale.US)
    return when {
        normalized.startsWith("gh_") || normalized.endsWith("@app") || normalized == "newsapp" -> {
            ConversationRuleCategory.OFFICIAL
        }
        normalized.endsWith("@chatroom") || normalized.endsWith("@im.chatroom") -> {
            ConversationRuleCategory.GROUP
        }
        else -> ConversationRuleCategory.FRIEND
    }
}

private fun conversationRuleCategoryForTarget(value: String): ConversationRuleCategory {
    return conversationRuleCategoryForId(value.substringBefore('/'))
}

internal fun conversationRuleCategoryMatches(
    rule: CustomNotificationRule,
    category: ConversationRuleCategory
): Boolean {
    if (category == ConversationRuleCategory.ALL) return true
    val actual = when {
        rule.official -> ConversationRuleCategory.OFFICIAL
        rule.group -> ConversationRuleCategory.GROUP
        else -> ConversationRuleCategory.FRIEND
    }
    return actual == category
}

internal fun conversationRuleCategoryMatches(
    binding: MessageBlockBinding,
    category: ConversationRuleCategory
): Boolean {
    if (category == ConversationRuleCategory.ALL) return true
    val actual = if (binding.targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        ConversationRuleCategory.GROUP
    } else {
        conversationRuleCategoryForTarget(binding.targetId)
    }
    return actual == category
}

internal fun conversationRuleCategoryMatches(
    binding: RedPacketRuleBinding,
    category: ConversationRuleCategory
): Boolean {
    return category == ConversationRuleCategory.ALL ||
        conversationRuleCategoryForId(binding.targetId) == category
}

internal fun conversationRuleCategoryMatches(
    binding: TransferRuleBinding,
    category: ConversationRuleCategory
): Boolean {
    return category == ConversationRuleCategory.ALL ||
        conversationRuleCategoryForId(binding.targetId) == category
}

private fun autoReplyMatchOptions(): List<OptionItem> = listOf(
    OptionItem("模糊匹配", AutoReplySettings.MATCH_FUZZY, "内容包含关键词就触发"),
    OptionItem("全字匹配", AutoReplySettings.MATCH_EXACT, "消息内容与关键词完全相同"),
    OptionItem("正则匹配", AutoReplySettings.MATCH_REGEX, "关键词按正则表达式处理"),
    OptionItem("任意消息", AutoReplySettings.MATCH_ANY, "收到符合范围的消息就触发")
)

private fun autoReplyMatchLabel(value: Int): String = autoReplyMatchOptions().firstOrNull { it.value == value }?.label ?: "模糊匹配"

private fun autoReplyTargetOptions(): List<OptionItem> = listOf(
    OptionItem("全部聊天", AutoReplySettings.TARGET_ALL, "好友、群聊和公众号都可触发"),
    OptionItem("仅私聊", AutoReplySettings.TARGET_PRIVATE, "只回复好友私聊"),
    OptionItem("仅群聊", AutoReplySettings.TARGET_GROUP, "只回复群消息"),
    OptionItem("指定名单", AutoReplySettings.TARGET_SPECIFIC, "只回复指定 wxid、群号或群成员"),
    OptionItem("仅公众号", AutoReplySettings.TARGET_OFFICIAL, "只回复公众号消息")
)

private fun autoReplyTargetLabel(value: Int): String = autoReplyTargetOptions().firstOrNull { it.value == value }?.label ?: "全部聊天"

private fun autoReplyAtOptions(): List<OptionItem> = listOf(
    OptionItem("不限", AutoReplySettings.AT_NONE),
    OptionItem("@我触发", AutoReplySettings.AT_ME),
    OptionItem("@全体触发", AutoReplySettings.AT_ALL)
)

private fun autoReplyAtLabel(value: Int): String = autoReplyAtOptions().firstOrNull { it.value == value }?.label ?: "不限"

private fun autoReplyPatOptions(): List<OptionItem> = listOf(
    OptionItem("不限", AutoReplySettings.PAT_NONE),
    OptionItem("被拍一拍触发", AutoReplySettings.PAT_ME)
)

private fun autoReplyPatLabel(value: Int): String = autoReplyPatOptions().firstOrNull { it.value == value }?.label ?: "不限"

private fun autoReplyStepOptions(): List<OptionItem> = listOf(
    OptionItem("发送文字", AutoReplySettings.REPLY_TEXT, "支持变量和 | 分隔多条"),
    OptionItem("发送图片", AutoReplySettings.REPLY_IMAGE, "从系统文件管理器选择"),
    OptionItem("发送语音", AutoReplySettings.REPLY_VOICE, "从系统文件管理器选择"),
    OptionItem("随机语音", AutoReplySettings.REPLY_VOICE_RANDOM_FOLDER, "从已选语音随机发送"),
    OptionItem("发送表情", AutoReplySettings.REPLY_EMOJI, "从系统文件管理器选择"),
    OptionItem("发送视频", AutoReplySettings.REPLY_VIDEO, "从系统文件管理器选择"),
    OptionItem("发送名片", AutoReplySettings.REPLY_CARD, "从好友列表选择"),
    OptionItem("发送文件", AutoReplySettings.REPLY_FILE, "从系统文件管理器选择"),
    OptionItem("发送收藏", AutoReplySettings.REPLY_FAVORITE, "从最近收藏选择"),
    OptionItem("邀请进群", AutoReplySettings.REPLY_INVITE_GROUP, "从群聊列表选择"),
    OptionItem("发送 XML", AutoReplySettings.REPLY_XML, "AppMsg/XML 原文"),
    OptionItem("智聊AI回复", AutoReplySettings.REPLY_ZHILIA_AI, "使用智聊/OpenAI 兼容配置生成回复")
)

private fun autoReplyStepLabel(value: Int): String =
    if (AutoReplySettings.isRemovedReplyMode(value)) "已移除的回复类型"
    else autoReplyStepOptions().firstOrNull { it.value == value }?.label ?: "未知回复类型"

private fun autoReplyStepContentHint(value: Int): String = when (value) {
    AutoReplySettings.REPLY_TEXT -> "多条文本用 | 分隔；下方变量可点击插入"
    AutoReplySettings.REPLY_XML -> "填写 XML 原文；下方变量可点击插入"
    AutoReplySettings.REPLY_CARD -> "选择要分享的好友名片"
    AutoReplySettings.REPLY_INVITE_GROUP -> "选择要邀请对方加入的群聊"
    AutoReplySettings.REPLY_FAVORITE -> "从最近收藏选择，可多选后按顺序发送"
    AutoReplySettings.REPLY_VOICE_RANDOM_FOLDER -> "选择多个语音文件后随机发送一个"
    else -> "填写文件绝对路径，每行一个或用 ;;; 分隔"
}

private fun autoReplyStepUsesFilePicker(mode: Int): Boolean = mode in setOf(
    AutoReplySettings.REPLY_IMAGE,
    AutoReplySettings.REPLY_VOICE,
    AutoReplySettings.REPLY_VOICE_RANDOM_FOLDER,
    AutoReplySettings.REPLY_EMOJI,
    AutoReplySettings.REPLY_VIDEO,
    AutoReplySettings.REPLY_FILE
)

private fun autoReplyStepUsesContactPicker(mode: Int): Boolean =
    mode == AutoReplySettings.REPLY_CARD || mode == AutoReplySettings.REPLY_INVITE_GROUP

internal fun autoReplyPickerTitle(mode: Int): String = when (mode) {
    AutoReplySettings.REPLY_IMAGE -> "选择图片"
    AutoReplySettings.REPLY_VOICE -> "选择语音"
    AutoReplySettings.REPLY_VOICE_RANDOM_FOLDER -> "选择随机语音"
    AutoReplySettings.REPLY_EMOJI -> "选择表情文件"
    AutoReplySettings.REPLY_VIDEO -> "选择视频"
    AutoReplySettings.REPLY_CARD -> "选择名片好友"
    AutoReplySettings.REPLY_FILE -> "选择文件"
    AutoReplySettings.REPLY_FAVORITE -> "选择收藏"
    AutoReplySettings.REPLY_INVITE_GROUP -> "选择群聊"
    else -> "选择文件"
}

internal fun scheduledTaskPickerTitle(taskType: Int): String = when (taskType) {
    ScheduledTaskSettings.TYPE_IMAGE -> "选择图片"
    ScheduledTaskSettings.TYPE_VIDEO -> "选择视频"
    ScheduledTaskSettings.TYPE_VOICE -> "选择语音文件"
    ScheduledTaskSettings.TYPE_EMOJI -> "选择表情文件"
    ScheduledTaskSettings.TYPE_FILE -> "选择文件"
    ScheduledTaskSettings.TYPE_FAVORITE -> "选择收藏"
    else -> "选择文件"
}

private fun autoReplyFileSummary(value: String): String {
    val items = autoReplySplitContent(value)
    if (items.isEmpty()) return "未选择"
    val names = items.take(3).map { File(it).name.ifBlank { it } }
    return names.joinToString("、") + if (items.size > 3) " 等 ${items.size} 个文件" else "，共 ${items.size} 个文件"
}

internal fun autoReplySelectedIdSummary(value: String): String {
    val count = autoReplySplitContent(value).size
    return if (count == 0) "未选择" else "已选择 $count 项"
}

private fun autoReplySplitContent(value: String): List<String> {
    return value.split(";;;")
        .flatMap { it.split('\n') }
        .flatMap { it.split('|') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
