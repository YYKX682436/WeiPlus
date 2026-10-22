package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.api.model.ContactLabelBean
import h.Hchat.hooks.items.autoreply.AutoReplyRule
import h.Hchat.hooks.items.autoreply.AutoReplyRuntime
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.autoreply.AutoReplyStep
import h.Hchat.hooks.items.autoreply.XiaozhiMcpStatus
import h.Hchat.hooks.items.autoreply.AutoReplyXiaozhiConfig
import h.Hchat.hooks.items.autoreply.AutoReplyZhiliaConfig
import h.Hchat.hooks.items.autoreply.XiaozhiAgentOption
import h.Hchat.hooks.items.autoreply.XiaozhiConsoleApi
import h.Hchat.hooks.items.autoreply.XiaozhiModelOption
import h.Hchat.hooks.items.autoreply.XiaozhiVoiceOption
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
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashSet
import java.util.Locale

@Composable
internal fun CustomNotificationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
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
                onEditDefault = { route = CustomNotificationRoute.DefaultEditor(it) }
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
                    onSave = { saved ->
                        saveDefaultRule(saved)
                        Toast.makeText(context, "默认规则已保存", Toast.LENGTH_SHORT).show()
                        route = CustomNotificationRoute.Main
                    },
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
                            Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
                            route = CustomNotificationRoute.RuleList
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
                onConfirm = { entries ->
                    val talker = currentRoute.talker
                    val memberValue = groupEntriesToMemberRules(talker, entries)
                    val title = currentRoute.request.title
                    rules.firstOrNull { it.talker == talker }?.let { old ->
                        val nextRule = if (title.contains("屏蔽")) {
                            old.copy(blockMembers = memberValue)
                        } else {
                            old.copy(onlyMembers = memberValue)
                        }
                        saveRules(rules.map { if (it.talker == talker) nextRule else it })
                        Toast.makeText(context, "成员规则已保存", Toast.LENGTH_SHORT).show()
                    }
                    route = CustomNotificationRoute.RuleEditor(talker)
                }
            )
        }
    }
}

@Composable
internal fun AutoReplyMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME) }
    val settings = remember { AutoReplySettings(context) }
    var rules by remember { mutableStateOf(settings.rules()) }
    var route by remember { mutableStateOf("main") }
    var editingRuleId by remember { mutableStateOf<String?>(null) }
    var editingRuleDraft by remember { mutableStateOf<AutoReplyRule?>(null) }
    var editingSteps by remember { mutableStateOf<Pair<String, List<AutoReplyStep>>?>(null) }
    val ruleListState = rememberLazyListState()
    var ruleQuery by remember { mutableStateOf("") }
    val ruleEditorListState = rememberLazyListState()
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

    fun saveRules(next: List<AutoReplyRule>) {
        rules = next
        settings.saveRules(next)
    }

    val autoReplyRoute = if (editingSteps != null) "steps" else route
    SettingsRouteTransition(
        targetState = autoReplyRoute,
        label = "AutoReplyRouteTransition",
        depthOf = { autoReplyRouteDepth(it) }
    ) { currentRoute ->
        when (currentRoute) {
            "steps" -> {
                val request = editingSteps ?: return@SettingsRouteTransition
            AutoReplyStepsPage(
                title = request.first,
                initialSteps = request.second,
                onBack = { editingSteps = null },
                onSave = { steps ->
                    when (route) {
                        "autoAccept" -> settings.saveAutoAcceptSteps(steps)
                        "greetAccepted" -> settings.saveGreetAcceptedSteps(steps)
                        else -> {
                            val id = editingRuleId
                            if (id != null) {
                                val updated = (editingRuleDraft ?: rules.firstOrNull { it.id == id })
                                    ?.copy(steps = steps)
                                if (updated != null) {
                                    editingRuleDraft = updated
                                    saveRules(rules.map { if (it.id == id) updated else it })
                                }
                            }
                        }
                    }
                    Toast.makeText(context, "回复步骤已保存", Toast.LENGTH_SHORT).show()
                    editingSteps = null
                }
            )
            }
        "rules" -> AutoReplyRuleListPage(
            rules = rules,
            query = ruleQuery,
            onQueryChange = { ruleQuery = it },
            listState = ruleListState,
            onBack = { route = "main" },
            onAdd = {
                val rule = AutoReplyRule(
                    id = System.currentTimeMillis().toString(),
                    name = "规则 ${rules.size + 1}"
                )
                saveRules(rules + rule)
                editingRuleId = rule.id
                editingRuleDraft = rule
                route = "ruleEditor"
            },
            onEdit = {
                editingRuleId = it.id
                editingRuleDraft = it
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
                route = "rules"
            } else {
                AutoReplyRuleEditorPage(
                    rule = rule,
                    listState = ruleEditorListState,
                    onBack = {
                        editingRuleDraft = null
                        route = "rules"
                    },
                    onSave = { saved ->
                        saveRules(rules.map { if (it.id == saved.id) saved else it })
                        editingRuleDraft = null
                        Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
                        route = "rules"
                    },
                    onDraftChange = { editingRuleDraft = it },
                    onEditSteps = { draft ->
                        editingRuleDraft = draft
                        editingSteps = "规则回复" to draft.steps
                    }
                )
            }
        }
        "autoAccept" -> AutoReplyFriendPage(
            context = context,
            title = "好友请求处理",
            sp = sp,
            enabledKey = AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE,
            delayKey = AutoReplySettings.KEY_AUTO_ACCEPT_DELAY_MS,
            tagEnabledKey = AutoReplySettings.KEY_AUTO_ACCEPT_TAG_ENABLE,
            tagNameKey = AutoReplySettings.KEY_AUTO_ACCEPT_TAG_NAME,
            steps = settings.autoAcceptSteps(),
            listState = autoAcceptListState,
            onBack = { route = "main" },
            automationKeys = autoAcceptAutomationKeys,
            onSelectLabels = { route = "autoAcceptLabels" },
            onEditSteps = { editingSteps = "好友请求回复" to settings.autoAcceptSteps() }
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
            title = "通过后欢迎语",
            sp = sp,
            enabledKey = AutoReplySettings.KEY_GREET_ACCEPTED_ENABLE,
            delayKey = AutoReplySettings.KEY_GREET_ACCEPTED_DELAY_MS,
            tagEnabledKey = AutoReplySettings.KEY_GREET_ACCEPTED_TAG_ENABLE,
            tagNameKey = AutoReplySettings.KEY_GREET_ACCEPTED_TAG_NAME,
            steps = settings.greetAcceptedSteps(),
            listState = greetAcceptedListState,
            onBack = { route = "main" },
            automationKeys = greetAcceptedAutomationKeys,
            onSelectLabels = { route = "greetAcceptedLabels" },
            onEditSteps = { editingSteps = "通过后欢迎语" to settings.greetAcceptedSteps() }
        )
        "greetAcceptedLabels" -> AutoReplyLabelPickerPage(
            context = context,
            sp = sp,
            selectedKey = AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_SELECTED_NAMES,
            listState = greetAcceptedLabelListState,
            onBack = { route = "greetAccepted" }
        )
        "ai" -> AutoReplyAiPage(context, sp, onBack = { route = "main" })
        else -> AutoReplyMainPage(
            provider = provider,
            sp = sp,
            rules = rules,
            onBack = onBack,
            onOpenRules = { route = "rules" },
            onOpenAutoAccept = { route = "autoAccept" },
            onOpenGreetAccepted = { route = "greetAccepted" },
            onOpenAi = { route = "ai" }
        )
        }
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

    SettingsRouteTransition(
        targetState = route,
        label = "AutoMessageForwardRoute",
        depthOf = {
            when (it) {
                AutoMessageForwardRoute.MAIN -> 0
                AutoMessageForwardRoute.RULES -> 1
                AutoMessageForwardRoute.EDITOR -> 2
                AutoMessageForwardRoute.CONTACTS,
                AutoMessageForwardRoute.MEMBERS,
                AutoMessageForwardRoute.TYPES,
                AutoMessageForwardRoute.REPLACEMENTS -> 3
            }
        }
    ) { currentRoute ->
        when (currentRoute) {
            AutoMessageForwardRoute.MAIN -> AutoMessageForwardMainPage(
                provider = provider,
                enabled = enabled,
                rules = rules,
                onBack = onBack,
                onEnabledChange = {
                    enabled = it
                    settings.setEnabled(context, it)
                },
                onOpenRules = { route = AutoMessageForwardRoute.RULES }
            )
            AutoMessageForwardRoute.RULES -> AutoMessageForwardRuleListPage(
                rules = rules,
                listState = ruleListState,
                query = ruleQuery,
                onQueryChange = { ruleQuery = it },
                onBack = { route = AutoMessageForwardRoute.MAIN },
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
                    route = AutoMessageForwardRoute.RULES
                } else {
                    AutoMessageForwardRuleEditorPage(
                        rule = current,
                        listState = editorListState,
                        onBack = { route = AutoMessageForwardRoute.RULES },
                        onDraftChange = { draft = it },
                        onPickContacts = { next, field ->
                            draft = next
                            pickerField = field
                            route = AutoMessageForwardRoute.CONTACTS
                        },
                        onPickMembers = { next ->
                            draft = next
                            route = AutoMessageForwardRoute.MEMBERS
                        },
                        onPickTypes = { next ->
                            draft = next
                            route = AutoMessageForwardRoute.TYPES
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
                                    route = AutoMessageForwardRoute.RULES
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
                    val contactRequest = remember(current.id, field, current.sourceIds, current.targetIds) {
                        ContactPickerRequest(
                            title = if (sources) "选择监听会话" else "选择转发会话",
                            mode = if (sources) ContactPickerMode.ALL_CHATS else ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = formatIds(if (sources) current.sourceIds else current.targetIds),
                            onValue = { value ->
                                val selected = parseIds(value)
                                draft = if (sources) current.copy(
                                    sourceIds = selected,
                                    sourceMemberIds = retainAutoMessageForwardSourceMembers(current.sourceMemberIds, selected)
                                )
                                else current.copy(targetIds = selected)
                                pickerField = null
                                route = AutoMessageForwardRoute.EDITOR
                            },
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
                        onConfirm = { selected ->
                            val value = formatIds(selected.map { it.id })
                            val ids = parseIds(value)
                            draft = if (sources) current.copy(
                                sourceIds = ids,
                                sourceMemberIds = retainAutoMessageForwardSourceMembers(current.sourceMemberIds, ids)
                            )
                            else current.copy(targetIds = ids)
                            pickerField = null
                            route = AutoMessageForwardRoute.EDITOR
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
                    val request = remember(current.id, current.sourceMemberIds, sourceGroups) {
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
                        onConfirm = { entries ->
                            draft = current.copy(sourceMemberIds = entries.toSet())
                            Toast.makeText(context, "已选择 ${entries.size} 个群成员", Toast.LENGTH_SHORT).show()
                            route = AutoMessageForwardRoute.EDITOR
                        }
                    )
                }
            }
            AutoMessageForwardRoute.TYPES -> {
                val current = draft
                if (current == null) {
                    route = AutoMessageForwardRoute.EDITOR
                } else {
                    AutoMessageForwardTypePickerPage(
                        selectedKinds = current.messageKinds,
                        onBack = { route = AutoMessageForwardRoute.EDITOR },
                        onConfirm = {
                            draft = current.copy(messageKinds = it)
                            route = AutoMessageForwardRoute.EDITOR
                        }
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
                            draft = current.copy(keywordReplacements = updated)
                            Toast.makeText(context, "替换规则已保存", Toast.LENGTH_SHORT).show()
                            route = AutoMessageForwardRoute.EDITOR
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoMessageForwardMainPage(
    provider: FeatureSettingsProvider,
    enabled: Boolean,
    rules: List<AutoMessageForwardRule>,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenRules: () -> Unit
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
            item { SmallTitle(text = "消息自动转发") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用消息自动转发",
                        summary = "默认不转发自己发送的消息，可在每条规则中单独开启",
                        onCheckedChange = onEnabledChange
                    )
                    InsetDivider()
                    ActionRow(
                        title = "转发规则",
                        summary = if (rules.isEmpty()) "暂无规则" else "${rules.size} 条规则，${rules.count { it.enabled }} 条启用",
                        onClick = onOpenRules
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoMessageForwardRuleListPage(
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
    PageScaffold(
        title = "转发规则",
        largeTitle = "转发规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selected.size}）",
                    onPrimaryClick = {
                        if (selected.isEmpty()) Toast.makeText(context, "请先选择规则", Toast.LENGTH_SHORT).show()
                        else showDeleteConfirm = true
                    },
                    secondaryText = "取消",
                    onSecondaryClick = {
                        batchDeleteMode = false
                        selectedIds = emptySet()
                    },
                    middleText = if (visibleIds.isEmpty()) null else if (allVisibleSelected) "取消全选" else "全选",
                    onMiddleClick = if (visibleIds.isEmpty()) null else {
                        {
                            selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
                        }
                    }
                )
            } else {
                BottomActionBar(
                    primaryText = "新增规则",
                    onPrimaryClick = onAdd,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
                    middleText = if (rules.isEmpty()) null else "批量删除",
                    onMiddleClick = if (rules.isEmpty()) null else {
                        {
                            batchDeleteMode = true
                            selectedIds = emptySet()
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
        ) {
            item {
                SettingsCard {
                    InputRow("搜索", "按规则名、会话、群成员或消息类型筛选", query, onValueChange = onQueryChange)
                }
            }
            if (visible.isEmpty()) {
                item { SettingsCard { EmptyText("暂无规则") } }
            } else {
                visible.forEachIndexed { index, rule ->
                    item {
                        SmallTitle(
                            modifier = Modifier.padding(top = if (index == 0) 10.dp else 18.dp),
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
                                ActionRow("删除", "移除此规则") { onDelete(rule) }
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
    listState: LazyListState,
    onBack: () -> Unit,
    onDraftChange: (AutoMessageForwardRule) -> Unit,
    onPickContacts: (AutoMessageForwardRule, AutoMessageForwardContactField) -> Unit,
    onPickMembers: (AutoMessageForwardRule) -> Unit,
    onPickTypes: (AutoMessageForwardRule) -> Unit,
    onPickReplacementRules: (AutoMessageForwardRule) -> Unit,
    onSave: (AutoMessageForwardRule) -> Unit
) {
    val context = LocalContext.current
    val current = rule
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "编辑转发规则",
        largeTitle = "编辑转发规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = { onSave(current) },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(current.enabled, "启用规则", "关闭后保留配置但不触发") { onDraftChange(current.copy(enabled = it)) }
                    InsetDivider()
                    SwitchRow(current.forwardOwnMessages, "转发自己发送的消息", "默认关闭；模块自动转发的消息不会再次触发") {
                        onDraftChange(current.copy(forwardOwnMessages = it))
                    }
                    InsetDivider()
                    SwitchRow(current.followSourceRecall, "跟随原消息撤回", "原消息撤回后，撤回该规则自动转发的消息") {
                        onDraftChange(current.copy(followSourceRecall = it))
                    }
                    InsetDivider()
                    InputRow("规则名称", "用于列表里识别规则", current.name) { onDraftChange(current.copy(name = it)) }
                    InsetDivider()
                    SwitchRow(current.delayEnabled, "延迟发送", "开启后按设置时间等待再转发") {
                        onDraftChange(current.copy(delayEnabled = it))
                    }
                    if (current.delayEnabled) {
                        InsetDivider()
                        NumberInputRow("延迟时间", "单位秒，0 表示立即发送", current.delaySeconds.toString()) {
                            onDraftChange(current.copy(delaySeconds = it.toLongOrNull()?.coerceAtLeast(0L) ?: 0L))
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "会话") }
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "消息类型") }
            item {
                SettingsCard {
                    ActionRow("选择消息类型", autoMessageForwardTypeSummary(current.messageKinds)) {
                        onPickTypes(current)
                    }
                    InsetDivider()
                    SwitchRow(current.includeKeywordsEnabled, "包含关键词", "开启后仅转发命中关键词的消息") {
                        onDraftChange(current.copy(includeKeywordsEnabled = it))
                    }
                    if (current.includeKeywordsEnabled) {
                        InsetDivider()
                        InputRow("包含关键词内容", "多个关键词用 |、逗号或换行分隔", current.includeKeywords, minLines = 2) {
                            onDraftChange(current.copy(includeKeywords = it))
                        }
                    }
                    InsetDivider()
                    SwitchRow(current.excludeKeywordsEnabled, "排除关键词", "开启后不转发命中关键词的消息") {
                        onDraftChange(current.copy(excludeKeywordsEnabled = it))
                    }
                    if (current.excludeKeywordsEnabled) {
                        InsetDivider()
                        InputRow("排除关键词内容", "多个关键词用 |、逗号或换行分隔", current.excludeKeywords, minLines = 2) {
                            onDraftChange(current.copy(excludeKeywords = it))
                        }
                    }
                    InsetDivider()
                    SwitchRow(current.replaceKeywordsEnabled, "替换关键词", "转发普通文字前按多条规则替换正文") {
                        onDraftChange(current.copy(replaceKeywordsEnabled = it))
                    }
                    if (current.replaceKeywordsEnabled) {
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
            }
        }
    }
}

@Composable
private fun AutoMessageForwardTypePickerPage(
    selectedKinds: Set<String>,
    onBack: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selected by remember(selectedKinds) { mutableStateOf(selectedKinds) }
    val options = autoMessageForwardTypeOptions()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "选择消息类型",
        largeTitle = "选择消息类型",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = { onConfirm(selected) },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (selected.size == options.size) "取消全选" else "全选",
                onMiddleClick = {
                    selected = if (selected.size == options.size) emptySet() else options.mapTo(LinkedHashSet()) { it.first }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
        ) {
            item {
                SettingsCard {
                    options.forEachIndexed { index, option ->
                        if (index > 0) InsetDivider()
                        SwitchRow(
                            checked = option.first in selected,
                            title = option.second,
                            summary = option.third
                        ) {
                            selected = if (it) selected + option.first else selected - option.first
                        }
                    }
                }
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
private fun AutoReplyMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    rules: List<AutoReplyRule>,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAutoAccept: () -> Unit,
    onOpenGreetAccepted: () -> Unit,
    onOpenAi: () -> Unit
) {
    var excludedTalkersEnabled by remember {
        mutableStateOf(sp.getBoolean(AutoReplySettings.KEY_EXCLUDED_TALKERS_ENABLE, false))
    }
    var excludedTalkers by remember {
        mutableStateOf(sp.getString(AutoReplySettings.KEY_EXCLUDED_TALKERS, "").orEmpty())
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
            item { SmallTitle(text = "聊天自动回复") }
            item {
                SettingsCard {
                    SwitchRow(sp, AutoReplySettings.KEY_ENABLE, "启用自动回复", "开启后按规则回复收到的消息", AutoReplySettings.DEFAULT_ENABLE)
                    InsetDivider()
                    SwitchRow(excludedTalkersEnabled, "排除指定会话", "开启后不回复指定会话的消息") {
                        excludedTalkersEnabled = it
                        sp.edit().putBoolean(AutoReplySettings.KEY_EXCLUDED_TALKERS_ENABLE, it).apply()
                    }
                    if (excludedTalkersEnabled) {
                        InsetDivider()
                        InputRow("排除会话 ID", "多个 ID 用逗号、分号或换行分隔", excludedTalkers, minLines = 2) {
                            excludedTalkers = it
                            sp.edit().putString(AutoReplySettings.KEY_EXCLUDED_TALKERS, it).apply()
                        }
                    }
                    InsetDivider()
                    ActionRow("回复规则", if (rules.isEmpty()) "暂无规则" else "${rules.size} 条规则，${rules.count { it.enabled }} 条启用", onOpenRules)
                    InsetDivider()
                    ActionRow("好友请求处理", "自动同意好友申请并按步骤发送欢迎内容", onOpenAutoAccept)
                    InsetDivider()
                    ActionRow("通过后欢迎语", "对方通过你的好友请求后发送欢迎内容", onOpenGreetAccepted)
                    InsetDivider()
                    ActionRow("AI 配置", "配置小智AI和智聊AI，多智聊模型可切换", onOpenAi)
                }
            }
        }
    }
}

@Composable
private fun AutoReplyRuleListPage(
    rules: List<AutoReplyRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onBack: () -> Unit,
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
    PageScaffold(
        title = "回复规则",
        largeTitle = "回复规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedRules.size}）",
                    onPrimaryClick = {
                        if (selectedRules.isEmpty()) {
                            Toast.makeText(context, "请先选择规则", Toast.LENGTH_SHORT).show()
                        } else {
                            showDeleteConfirm = true
                        }
                    },
                    secondaryText = "取消",
                    onSecondaryClick = {
                        batchDeleteMode = false
                        selectedIds = emptySet()
                    },
                    middleText = if (visibleIds.isEmpty()) null else if (allVisibleSelected) "取消全选" else "全选",
                    onMiddleClick = if (visibleIds.isEmpty()) null else {
                        {
                            selectedIds = if (allVisibleSelected) {
                                selectedIds - visibleIds
                            } else {
                                selectedIds + visibleIds
                            }
                        }
                    }
                )
            } else {
                BottomActionBar(
                    primaryText = "新增规则",
                    onPrimaryClick = onAdd,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
                    middleText = if (rules.isEmpty()) null else "批量删除",
                    onMiddleClick = if (rules.isEmpty()) null else {
                        {
                            batchDeleteMode = true
                            selectedIds = emptySet()
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
        ) {
            item {
                SettingsCard {
                    InputRow("搜索", "按规则名或关键词筛选", query, onValueChange = onQueryChange)
                }
            }
            if (visible.isEmpty()) {
                item { SettingsCard { EmptyText("暂无规则") } }
            } else {
                visible.forEachIndexed { index, rule ->
                    item {
                        SmallTitle(
                            modifier = Modifier.padding(top = if (index == 0) 10.dp else 18.dp),
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
                                ActionRow("删除", "移除此规则") { onDelete(rule) }
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
    listState: LazyListState,
    onBack: () -> Unit,
    onSave: (AutoReplyRule) -> Unit,
    onDraftChange: (AutoReplyRule) -> Unit,
    onEditSteps: (AutoReplyRule) -> Unit
) {
    val context = LocalContext.current
    var current by remember(rule) { mutableStateOf(rule) }
    var optionPicker by remember { mutableStateOf<OptionPickerRequest?>(null) }
    var contactPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var groupMemberPicker by remember { mutableStateOf<GroupMemberPickerRequest?>(null) }
    val editorRoute = when {
        optionPicker != null -> AutoReplyEditorRoute.OptionPicker(optionPicker!!)
        contactPicker != null -> AutoReplyEditorRoute.ContactPicker(contactPicker!!)
        groupMemberPicker != null -> AutoReplyEditorRoute.GroupMemberPicker(groupMemberPicker!!)
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
                    onConfirm = { selected ->
                        request.onValue(formatIds(selected.map { it.id }))
                        Toast.makeText(context, "已选择 ${selected.size} 项", Toast.LENGTH_SHORT).show()
                        contactPicker = null
                    }
                )
            }
            is AutoReplyEditorRoute.GroupMemberPicker -> {
                val request = route.request
                GroupMemberPickerPage(
                    context = context,
                    request = request,
                    onBack = { groupMemberPicker = null },
                    onConfirm = { entries ->
                        request.onValue(formatIds(entries))
                        Toast.makeText(context, "已选择 ${entries.size} 个群成员", Toast.LENGTH_SHORT).show()
                        groupMemberPicker = null
                    }
                )
            }
            AutoReplyEditorRoute.Main -> AutoReplyRuleEditorContent(
                rule = current,
                listState = listState,
                onRuleChange = {
                    current = it
                    onDraftChange(it)
                },
                onBack = onBack,
                onSave = { onSave(current) },
                onEditSteps = { onEditSteps(current) },
                onOptionPicker = { optionPicker = it },
                onContactPicker = { contactPicker = it },
                onGroupMemberPicker = { groupMemberPicker = it }
            )
        }
    }
}

@Composable
private fun AutoReplyRuleEditorContent(
    rule: AutoReplyRule,
    listState: LazyListState,
    onRuleChange: (AutoReplyRule) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onEditSteps: () -> Unit,
    onOptionPicker: (OptionPickerRequest) -> Unit,
    onContactPicker: (ContactPickerRequest) -> Unit,
    onGroupMemberPicker: (GroupMemberPickerRequest) -> Unit
) {
    val current = rule
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "编辑规则",
        largeTitle = "编辑规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = onSave,
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(current.enabled, "启用规则", "关闭后保留配置但不触发") { onRuleChange(current.copy(enabled = it)) }
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
                        InputRow("关键词", "多个关键词用 |、逗号或换行分隔", current.keyword, minLines = 2) {
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "范围") }
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "触发") }
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
                    NumberInputRow("最大回复次数", "0 表示不限制，按规则/会话/发送者统计", current.maxReplyCount.toString()) {
                        onRuleChange(current.copy(maxReplyCount = it.toIntOrNull()?.coerceAtLeast(0) ?: 0))
                    }
                    InsetDivider()
                    NumberInputRow("回复冷却时间", "单位秒，0 表示不限制；同一规则在同一会话内冷却", current.cooldownSeconds.toString()) {
                        onRuleChange(current.copy(cooldownSeconds = it.toLongOrNull()?.coerceAtLeast(0L) ?: 0L))
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "回复") }
            item {
                SettingsCard {
                    SwitchRow(current.replyAsQuote, "引用回复", "文本和 AI 可引用原消息回复") { onRuleChange(current.copy(replyAsQuote = it)) }
                    InsetDivider()
                    ActionRow("回复步骤", describeAutoReplySteps(current.steps), onEditSteps)
                }
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
    steps: List<AutoReplyStep>,
    listState: LazyListState,
    onBack: () -> Unit,
    automationKeys: AutoReplyFriendAutomationKeys?,
    onSelectLabels: (() -> Unit)?,
    onEditSteps: () -> Unit
) {
    var delayMs by remember { mutableStateOf(sp.getLong(delayKey, 2000L).coerceAtLeast(0L).toString()) }
    var tagName by remember { mutableStateOf(sp.getString(tagNameKey, "") ?: "") }
    var labelDateFormat by remember {
        mutableStateOf(sp.getString(automationKeys?.labelDateFormat ?: "", "yyyy-MM-dd") ?: "yyyy-MM-dd")
    }
    val selectedLabelNames = sp.getString(automationKeys?.labelSelectedNames ?: "", "") ?: ""
    var remarkDateFormat by remember {
        mutableStateOf(sp.getString(automationKeys?.remarkDateFormat ?: "", "yyMMdd") ?: "yyMMdd")
    }
    var remarkCustomText by remember {
        mutableStateOf(sp.getString(automationKeys?.remarkCustomText ?: "", "") ?: "")
    }
    val hasAutomation = automationKeys != null && onSelectLabels != null
    var labelDateEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.labelDateEnable, false) } ?: false) }
    var labelExistingEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.labelExistingEnable, false) } ?: false) }
    var remarkDateEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.remarkDateEnable, false) } ?: false) }
    var remarkCustomEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.remarkCustomEnable, false) } ?: false) }
    var tagEnabled by remember { mutableStateOf(sp.getBoolean(tagEnabledKey, false)) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    sp.edit()
                        .putLong(delayKey, delayMs.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L)
                        .putString(tagNameKey, tagName.trim())
                        .also { editor ->
                            automationKeys?.let { keys ->
                                editor.putString(keys.labelDateFormat, labelDateFormat.trim().ifBlank { "yyyy-MM-dd" })
                                editor.putString(keys.remarkDateFormat, remarkDateFormat.trim().ifBlank { "yyMMdd" })
                                editor.putString(keys.remarkCustomText, remarkCustomText.trim())
                            }
                        }
                        .apply()
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
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
        ) {
            item {
                SettingsCard {
                    SwitchRow(sp, enabledKey, "启用", "开启后自动处理这个场景", false)
                    InsetDivider()
                    NumberInputRow("延迟发送", "单位 ms，通过好友后等待再发欢迎内容", delayMs) { delayMs = it }
                    if (hasAutomation) {
                        val keys = automationKeys!!
                        InsetDivider()
                        SwitchRow(sp, keys.labelNewFriendEnable, "自动标签新加好友", "给好友添加“新加好友”标签", false)
                        InsetDivider()
                        SwitchRow(labelDateEnabled, "自动标签日期", "按日期格式生成一个标签") {
                            labelDateEnabled = it
                            sp.edit().putBoolean(keys.labelDateEnable, it).apply()
                        }
                        if (labelDateEnabled) {
                            InsetDivider()
                            InputRow("标签日期格式", "例如 yyyy-MM-dd", labelDateFormat) { labelDateFormat = it }
                        }
                        InsetDivider()
                        SwitchRow(labelExistingEnabled, "自动标签已有标签", "把好友加入已选择的微信标签") {
                            labelExistingEnabled = it
                            sp.edit().putBoolean(keys.labelExistingEnable, it).apply()
                        }
                        if (labelExistingEnabled) {
                            InsetDivider()
                            ActionRow("选择标签", autoReplyLabelSummary(selectedLabelNames), onSelectLabels!!)
                        }
                        InsetDivider()
                        SwitchRow(sp, keys.remarkNewFriendEnable, "自动备注新加好友", "备注里加入“新加好友”", false)
                        InsetDivider()
                        SwitchRow(sp, keys.remarkNicknameSuffixEnable, "加昵称后面", "备注格式为微信昵称 + 备注内容", false)
                        InsetDivider()
                        SwitchRow(remarkDateEnabled, "自动备注日期", "备注里加入日期") {
                            remarkDateEnabled = it
                            sp.edit().putBoolean(keys.remarkDateEnable, it).apply()
                        }
                        if (remarkDateEnabled) {
                            InsetDivider()
                            InputRow("备注日期格式", "例如 yyMMdd", remarkDateFormat) { remarkDateFormat = it }
                        }
                        InsetDivider()
                        SwitchRow(remarkCustomEnabled, "自动备注自定义文本", "备注里加入自定义内容") {
                            remarkCustomEnabled = it
                            sp.edit().putBoolean(keys.remarkCustomEnable, it).apply()
                        }
                        if (remarkCustomEnabled) {
                            InsetDivider()
                            InputRow("备注自定义文本", "例如 渠道A", remarkCustomText) { remarkCustomText = it }
                        }
                    } else {
                        InsetDivider()
                        SwitchRow(tagEnabled, "自动打标签", "处理后把好友加入指定标签") {
                            tagEnabled = it
                            sp.edit().putBoolean(tagEnabledKey, it).apply()
                        }
                        if (tagEnabled) {
                            InsetDivider()
                            InputRow("标签名称", "标签需要微信里已存在", tagName) { tagName = it }
                        }
                    }
                    InsetDivider()
                    ActionRow("回复步骤", describeAutoReplySteps(steps), onEditSteps)
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
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    sp.edit()
                        .putString(selectedKey, selected.joinToString(";;;"))
                        .apply()
                    Toast.makeText(context, "标签已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
        ) {
            item { SmallTitle(text = "搜索") }
            item {
                SettingsCard {
                    InputRow("关键词", "按标签名筛选", query) { query = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "标签") }
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

@Composable
private fun XiaozhiCaptchaSvgView(svg: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        },
        update = { view ->
            view.loadDataWithBaseURL(null, svg, "image/svg+xml", "UTF-8", null)
        }
    )
}

private fun xiaozhiAgentSummary(agentId: String, agents: List<XiaozhiAgentOption>): String {
    if (agentId.isBlank()) return "未选择，点击拉取"
    val agent = agents.firstOrNull { it.id == agentId }
    return if (agent == null) "已保存 ID: $agentId" else "${agent.name} / ${agent.assistantName.ifBlank { agent.id }}"
}

private fun xiaozhiModelSummary(model: String, models: List<XiaozhiModelOption>): String {
    if (model.isBlank()) return "未选择，点击拉取"
    val item = models.firstOrNull { it.id == model }
    return if (item == null) "已保存: $model" else "${item.name} / ${item.id}"
}

private fun xiaozhiVoiceSummary(voice: String, voices: List<XiaozhiVoiceOption>): String {
    if (voice.isBlank()) return "未选择，留空使用控制台当前角色"
    val item = voices.firstOrNull { it.id == voice }
    return if (item == null) "已保存: $voice" else "${item.name} / ${item.id}"
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
    var xiaozhiServe by remember { mutableStateOf(settings.xiaozhiConfig().serveUrl) }
    var xiaozhiOta by remember { mutableStateOf(settings.xiaozhiConfig().otaUrl) }
    var xiaozhiConsole by remember { mutableStateOf(settings.xiaozhiConfig().consoleUrl) }
    var xiaozhiConsolePhone by remember { mutableStateOf(settings.xiaozhiConfig().consolePhone) }
    var xiaozhiConsoleToken by remember { mutableStateOf(settings.xiaozhiConfig().consoleToken) }
    var xiaozhiConsoleAgentId by remember { mutableStateOf(settings.xiaozhiConfig().consoleAgentId) }
    var xiaozhiConsoleModel by remember { mutableStateOf(settings.xiaozhiConfig().consoleModel) }
    var xiaozhiVoiceRole by remember { mutableStateOf(settings.xiaozhiConfig().voiceRole) }
    var xiaozhiMusicMcp by remember { mutableStateOf(settings.xiaozhiConfig().musicMcpEnabled) }
    var xiaozhiMcpBridge by remember { mutableStateOf(settings.xiaozhiConfig().mcpBridgeEnabled) }
    var xiaozhiMcpEndpoint by remember { mutableStateOf(settings.xiaozhiConfig().mcpEndpointUrl) }
    var xiaozhiMcpReadySeconds by remember { mutableStateOf(settings.xiaozhiConfig().mcpReadySeconds.toString()) }
    var xiaozhiMcpIdleSeconds by remember { mutableStateOf(settings.xiaozhiConfig().mcpIdleSeconds.toString()) }
    var xiaozhiMcpStatus by remember { mutableStateOf(XiaozhiMcpStatus(false, "未查询", "点击刷新查询小智控制台")) }
    var configs by remember { mutableStateOf(settings.zhiliaConfigs()) }
    var activeName by remember { mutableStateOf(settings.activeZhiliaName()) }
    val activeConfig = configs.firstOrNull { it.name == activeName } ?: configs.firstOrNull() ?: settings.activeZhiliaConfig()
    var cfgName by remember(activeName, configs) { mutableStateOf(activeConfig.name) }
    var apiKey by remember(activeName, configs) { mutableStateOf(activeConfig.apiKey) }
    var apiBase by remember(activeName, configs) { mutableStateOf(activeConfig.apiBaseUrl) }
    var apiPath by remember(activeName, configs) { mutableStateOf(activeConfig.apiPath) }
    var model by remember(activeName, configs) { mutableStateOf(activeConfig.model) }
    var prompt by remember(activeName, configs) { mutableStateOf(activeConfig.systemPrompt) }
    var contextLimit by remember(activeName, configs) { mutableStateOf(activeConfig.contextLimit.toString()) }
    var modelQuery by remember { mutableStateOf("") }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var favoriteModels by remember(apiBase) { mutableStateOf(settings.favoriteModels(apiBase)) }
    var loadingText by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }
    var xiaozhiBindResult by remember { mutableStateOf("") }
    var xiaozhiCaptchaSvg by remember { mutableStateOf("") }
    var xiaozhiCaptchaCode by remember { mutableStateOf("") }
    var xiaozhiSmsCode by remember { mutableStateOf("") }
    var xiaozhiConsoleResult by remember { mutableStateOf("") }
    var xiaozhiAgents by remember { mutableStateOf<List<XiaozhiAgentOption>>(emptyList()) }
    var xiaozhiModels by remember { mutableStateOf<List<XiaozhiModelOption>>(emptyList()) }
    var xiaozhiVoices by remember { mutableStateOf<List<XiaozhiVoiceOption>>(emptyList()) }
    var route by remember { mutableStateOf<AutoReplyAiRoute>(AutoReplyAiRoute.Main) }
    var zhiliaPendingName by remember { mutableStateOf(activeName) }
    var pendingModel by remember { mutableStateOf(model) }
    val mainListState = rememberLazyListState()
    val xiaozhiListState = rememberLazyListState()
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

    fun currentXiaozhiConfig(): AutoReplyXiaozhiConfig {
        return AutoReplyXiaozhiConfig(
            serveUrl = xiaozhiServe,
            otaUrl = xiaozhiOta,
            consoleUrl = xiaozhiConsole,
            consolePhone = xiaozhiConsolePhone,
            consoleToken = xiaozhiConsoleToken,
            consoleAgentId = xiaozhiConsoleAgentId,
            consoleModel = xiaozhiConsoleModel,
            voiceRole = xiaozhiVoiceRole,
            musicMcpEnabled = xiaozhiMusicMcp,
            mcpBridgeEnabled = xiaozhiMcpBridge,
            mcpEndpointUrl = xiaozhiMcpEndpoint,
            mcpReadySeconds = xiaozhiMcpReadySeconds.toIntOrNull()?.coerceIn(1, 30)
                ?: AutoReplySettings.DEFAULT_XIAOZHI_MCP_READY_SECONDS,
            mcpIdleSeconds = xiaozhiMcpIdleSeconds.toIntOrNull()?.coerceIn(10, 600)
                ?: AutoReplySettings.DEFAULT_XIAOZHI_MCP_IDLE_SECONDS
        )
    }

    fun saveXiaozhi(showToast: Boolean) {
        settings.saveXiaozhiConfig(currentXiaozhiConfig())
        if (showToast) Toast.makeText(context, "小智AI配置已保存", Toast.LENGTH_SHORT).show()
    }

    fun xiaozhiNeedToken(): Boolean {
        if (xiaozhiConsoleToken.trim().isBlank()) {
            xiaozhiConsoleResult = "请先登录小智控制台"
            Toast.makeText(context, "请先登录小智控制台", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    fun refreshXiaozhiCaptcha(updateStatus: Boolean = true) {
        if (updateStatus) xiaozhiConsoleResult = "正在刷新图形验证码..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.fetchCaptchaSvg() }
            }
            result.onSuccess {
                xiaozhiCaptchaSvg = it
                xiaozhiCaptchaCode = ""
                if (updateStatus) xiaozhiConsoleResult = "请输入图形验证码"
            }.onFailure {
                xiaozhiConsoleResult = "刷新图形验证码失败: ${it.message}"
            }
        }
    }

    fun sendXiaozhiSmsCode() {
        val phone = xiaozhiConsolePhone.trim()
        val captcha = xiaozhiCaptchaCode.trim()
        if (phone.isBlank() || captcha.isBlank()) {
            Toast.makeText(context, "请填写手机号和图形验证码", Toast.LENGTH_SHORT).show()
            return
        }
        settings.saveXiaozhiConfig(currentXiaozhiConfig())
        xiaozhiConsoleResult = "正在发送短信验证码..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.sendSmsCode(phone, captcha) }
            }
            result.onSuccess {
                xiaozhiConsoleResult = it
                xiaozhiCaptchaCode = ""
                refreshXiaozhiCaptcha(updateStatus = false)
            }.onFailure {
                xiaozhiConsoleResult = "发送短信验证码失败: ${it.message}"
                xiaozhiCaptchaCode = ""
                refreshXiaozhiCaptcha(updateStatus = false)
            }
            Toast.makeText(context, xiaozhiConsoleResult, Toast.LENGTH_SHORT).show()
        }
    }

    fun loginXiaozhiConsole() {
        val phone = xiaozhiConsolePhone.trim()
        val sms = xiaozhiSmsCode.trim()
        if (phone.isBlank() || sms.isBlank()) {
            Toast.makeText(context, "请填写手机号和短信验证码", Toast.LENGTH_SHORT).show()
            return
        }
        xiaozhiConsoleResult = "正在登录小智控制台..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.phoneLogin(phone, sms) }
            }
            result.onSuccess { token ->
                xiaozhiConsoleToken = token
                xiaozhiSmsCode = ""
                xiaozhiCaptchaCode = ""
                settings.saveXiaozhiConfig(currentXiaozhiConfig())
                xiaozhiConsoleResult = "登录成功，token 已保存"
                Toast.makeText(context, "小智控制台登录成功", Toast.LENGTH_SHORT).show()
                val agentsResult = withContext(Dispatchers.IO) {
                    runCatching { XiaozhiConsoleApi.fetchAgents(token) }
                }
                agentsResult.onSuccess { agents ->
                    xiaozhiAgents = agents
                    agents.firstOrNull()?.let { agent ->
                        if (xiaozhiConsoleAgentId.isBlank()) xiaozhiConsoleAgentId = agent.id
                        if (xiaozhiConsoleModel.isBlank()) xiaozhiConsoleModel = agent.model
                        if (xiaozhiVoiceRole.isBlank()) xiaozhiVoiceRole = agent.voice
                        settings.saveXiaozhiConfig(currentXiaozhiConfig())
                    }
                    xiaozhiConsoleResult = "登录成功，已拉取 ${agents.size} 个智能体"
                }.onFailure {
                    xiaozhiConsoleResult = "登录成功，拉取智能体失败: ${it.message}"
                }
            }.onFailure {
                xiaozhiConsoleResult = "登录失败: ${it.message}"
            }
        }
    }

    fun fetchXiaozhiAgents(openPicker: Boolean) {
        if (xiaozhiNeedToken()) return
        xiaozhiConsoleResult = "正在拉取智能体..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.fetchAgents(xiaozhiConsoleToken) }
            }
            result.onSuccess { agents ->
                xiaozhiAgents = agents
                xiaozhiConsoleResult = "已拉取 ${agents.size} 个智能体"
                if (openPicker && agents.isNotEmpty()) {
                    val currentIndex = agents.indexOfFirst { it.id == xiaozhiConsoleAgentId }.coerceAtLeast(0)
                    route = AutoReplyAiRoute.OptionPicker(
                        OptionPickerRequest(
                            title = "选择小智智能体",
                            options = agents.mapIndexed { index, agent ->
                                OptionItem(
                                    label = agent.name,
                                    value = index,
                                    summary = listOf(agent.assistantName, agent.model, agent.voice).filter { it.isNotBlank() }.joinToString(" / ")
                                )
                            },
                            currentValue = currentIndex,
                            onSelected = { selected ->
                                agents.getOrNull(selected.value)?.let { agent ->
                                    xiaozhiConsoleAgentId = agent.id
                                    xiaozhiConsoleModel = agent.model
                                    xiaozhiVoiceRole = agent.voice
                                    settings.saveXiaozhiConfig(currentXiaozhiConfig())
                                }
                                route = AutoReplyAiRoute.Xiaozhi
                            }
                        )
                    )
                }
            }.onFailure {
                xiaozhiConsoleResult = "拉取智能体失败: ${it.message}"
            }
        }
    }

    fun fetchXiaozhiModels(openPicker: Boolean) {
        if (xiaozhiNeedToken()) return
        xiaozhiConsoleResult = "正在拉取模型列表..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.fetchModels(xiaozhiConsoleToken) }
            }
            result.onSuccess { models ->
                xiaozhiModels = models
                xiaozhiConsoleResult = "已拉取 ${models.size} 个模型"
                if (openPicker && models.isNotEmpty()) {
                    val currentIndex = models.indexOfFirst { it.id == xiaozhiConsoleModel }.coerceAtLeast(0)
                    route = AutoReplyAiRoute.OptionPicker(
                        OptionPickerRequest(
                            title = "选择小智模型",
                            options = models.mapIndexed { index, item -> OptionItem(item.name, index, item.id) },
                            currentValue = currentIndex,
                            onSelected = { selected ->
                                models.getOrNull(selected.value)?.let { item ->
                                    xiaozhiConsoleModel = item.id
                                    settings.saveXiaozhiConfig(currentXiaozhiConfig())
                                }
                                route = AutoReplyAiRoute.Xiaozhi
                            }
                        )
                    )
                }
            }.onFailure {
                xiaozhiConsoleResult = "拉取模型失败: ${it.message}"
            }
        }
    }

    fun fetchXiaozhiVoices(openPicker: Boolean) {
        if (xiaozhiNeedToken()) return
        xiaozhiConsoleResult = "正在拉取语音角色..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.fetchVoices(xiaozhiConsoleToken) }
            }
            result.onSuccess { voices ->
                xiaozhiVoices = voices
                xiaozhiConsoleResult = "已拉取 ${voices.size} 个语音角色"
                if (openPicker && voices.isNotEmpty()) {
                    val currentIndex = voices.indexOfFirst { it.id == xiaozhiVoiceRole }.coerceAtLeast(0)
                    route = AutoReplyAiRoute.OptionPicker(
                        OptionPickerRequest(
                            title = "选择语音角色",
                            options = voices.mapIndexed { index, item ->
                                OptionItem(item.name, index, listOf(item.id, item.languages.joinToString(",")).filter { it.isNotBlank() }.joinToString(" / "))
                            },
                            currentValue = currentIndex,
                            onSelected = { selected ->
                                voices.getOrNull(selected.value)?.let { item ->
                                    xiaozhiVoiceRole = item.id
                                    settings.saveXiaozhiConfig(currentXiaozhiConfig())
                                }
                                route = AutoReplyAiRoute.Xiaozhi
                            }
                        )
                    )
                }
            }.onFailure {
                xiaozhiConsoleResult = "拉取语音角色失败: ${it.message}"
            }
        }
    }

    fun saveXiaozhiConsoleAgentConfig() {
        if (xiaozhiNeedToken()) return
        val agentId = xiaozhiConsoleAgentId.trim()
        if (agentId.isBlank()) {
            Toast.makeText(context, "请先选择智能体", Toast.LENGTH_SHORT).show()
            return
        }
        settings.saveXiaozhiConfig(currentXiaozhiConfig())
        xiaozhiConsoleResult = "正在保存到小智控制台..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    XiaozhiConsoleApi.saveAgentConfig(
                        token = xiaozhiConsoleToken,
                        agentId = agentId,
                        model = xiaozhiConsoleModel,
                        voice = xiaozhiVoiceRole
                    )
                }
            }
            result.onSuccess { agent ->
                xiaozhiConsoleAgentId = agent.id
                xiaozhiConsoleModel = agent.model
                xiaozhiVoiceRole = agent.voice
                settings.saveXiaozhiConfig(currentXiaozhiConfig())
                xiaozhiConsoleResult = "已保存到控制台: ${agent.name}"
                Toast.makeText(context, "小智控制台配置已保存", Toast.LENGTH_SHORT).show()
            }.onFailure {
                xiaozhiConsoleResult = "保存到控制台失败: ${it.message}"
            }
        }
    }

    fun saveZhilia(showToast: Boolean) {
        val saved = currentZhiliaConfig()
        val next = configs.filterNot { it.name == activeName || it.name == saved.name } + saved
        settings.saveZhiliaConfigs(next, saved.name)
        configs = settings.zhiliaConfigs()
        activeName = saved.name
        if (sp.getBoolean(AutoReplySettings.KEY_AI_CLEAR_CONTEXT_ON_SAVE, AutoReplySettings.DEFAULT_AI_CLEAR_CONTEXT_ON_SAVE)) {
            AutoReplyRuntime.clearAiHistories()
        }
        if (showToast) Toast.makeText(context, "智聊AI配置已保存", Toast.LENGTH_SHORT).show()
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
        saveZhilia(showToast = false)
        settings.saveZhiliaConfigs(settings.zhiliaConfigs(), name)
        configs = settings.zhiliaConfigs()
        activeName = settings.activeZhiliaName()
        zhiliaPendingName = activeName
        Toast.makeText(context, "已切换到 $activeName", Toast.LENGTH_SHORT).show()
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
            AutoReplyAiRoute.Main -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "AI 配置",
                    largeTitle = "AI 配置",
                    scrollBehavior = scrollBehavior,
                    bottomBar = { BottomActionBar("返回", onBack) }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = mainListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
                    ) {
                        item { SmallTitle(text = "配置分组") }
                        item {
                            SettingsCard {
                                ActionRow("小智AI配置", xiaozhiServe.ifBlank { "未设置 WebSocket 地址" }) {
                                    route = AutoReplyAiRoute.Xiaozhi
                                }
                                InsetDivider()
                                ActionRow("智聊AI配置", "当前启用：$activeName") {
                                    route = AutoReplyAiRoute.Zhilia
                                }
                            }
                        }
                    }
                }
            }
            AutoReplyAiRoute.Xiaozhi -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "小智AI配置",
                    largeTitle = "小智AI配置",
                    scrollBehavior = scrollBehavior,
                    bottomBar = {
                        BottomActionBar(
                            primaryText = "保存小智",
                            onPrimaryClick = { saveXiaozhi(showToast = true) },
                            secondaryText = "返回",
                            onSecondaryClick = { route = AutoReplyAiRoute.Main }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = xiaozhiListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
                    ) {
                        item { SmallTitle(text = "连接地址") }
                        item {
                            SettingsCard {
                                InputRow("WebSocket 地址", "小智服务地址", xiaozhiServe) { xiaozhiServe = it }
                                InsetDivider()
                                InputRow("OTA 地址", "小智 OTA 地址", xiaozhiOta) { xiaozhiOta = it }
                                InsetDivider()
                                InputRow("控制台地址", "用于查看或绑定设备", xiaozhiConsole) { xiaozhiConsole = it }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "设备") }
                        item {
                            SettingsCard {
                                InfoRow("UUID", AutoReplyRuntime.xiaozhiDeviceUuid(context))
                                InsetDivider()
                                InfoRow("MAC 地址", AutoReplyRuntime.xiaozhiDeviceMac(context))
                                InsetDivider()
                                ActionRow("绑定设备", xiaozhiBindResult.ifBlank { "请求 OTA 获取验证码或绑定状态" }) {
                                    xiaozhiBindResult = "正在请求..."
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            AutoReplyRuntime.bindXiaozhiDevice(context, currentXiaozhiConfig())
                                        }
                                        xiaozhiBindResult = result
                                        Toast.makeText(context, result.lineSequence().firstOrNull().orEmpty(), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                val xiaozhiBindLink = remember(xiaozhiBindResult) { extractFirstHttpUrl(xiaozhiBindResult) }
                                if (xiaozhiBindLink != null) {
                                    InsetDivider()
                                    InfoRow("绑定链接", xiaozhiBindLink) {
                                        openExternalLink(context, xiaozhiBindLink)
                                    }
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "控制台登录") }
                        item {
                            SettingsCard {
                                InfoRow("登录状态", if (xiaozhiConsoleToken.isBlank()) "未登录" else "已保存 token")
                                InsetDivider()
                                InputRow("手机号", "登录成功后会记住手机号", xiaozhiConsolePhone) { xiaozhiConsolePhone = it }
                                InsetDivider()
                                ActionRow("刷新图形验证码", if (xiaozhiCaptchaSvg.isBlank()) "先刷新，再输入图片上的字符" else "已刷新，输入下方图形验证码") {
                                    refreshXiaozhiCaptcha()
                                }
                                if (xiaozhiCaptchaSvg.isNotBlank()) {
                                    InsetDivider()
                                    XiaozhiCaptchaSvgView(xiaozhiCaptchaSvg)
                                }
                                InsetDivider()
                                InputRow("图形验证码", "输入上方图片里的字符", xiaozhiCaptchaCode) { xiaozhiCaptchaCode = it }
                                InsetDivider()
                                ActionRow("发送短信验证码", "需要先填写手机号和图形验证码") {
                                    sendXiaozhiSmsCode()
                                }
                                InsetDivider()
                                InputRow("短信验证码", "输入手机收到的验证码", xiaozhiSmsCode) { xiaozhiSmsCode = it }
                                InsetDivider()
                                ActionRow("登录并保存 token", "登录成功后自动保存 token 并拉取智能体") {
                                    loginXiaozhiConsole()
                                }
                                if (xiaozhiConsoleResult.isNotBlank()) {
                                    InsetDivider()
                                    InfoRow("控制台状态", xiaozhiConsoleResult)
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "控制台配置") }
                        item {
                            SettingsCard {
                                ActionRow("选择智能体", xiaozhiAgentSummary(xiaozhiConsoleAgentId, xiaozhiAgents)) {
                                    fetchXiaozhiAgents(openPicker = true)
                                }
                                InsetDivider()
                                InfoRow("Agent ID", xiaozhiConsoleAgentId.ifBlank { "未选择" })
                                InsetDivider()
                                ActionRow("选择模型", xiaozhiModelSummary(xiaozhiConsoleModel, xiaozhiModels)) {
                                    fetchXiaozhiModels(openPicker = true)
                                }
                                InsetDivider()
                                ActionRow("选择语音角色", xiaozhiVoiceSummary(xiaozhiVoiceRole, xiaozhiVoices)) {
                                    fetchXiaozhiVoices(openPicker = true)
                                }
                                InsetDivider()
                                ActionRow("保存到小智控制台", "读取当前智能体配置后只替换模型和语音角色") {
                                    saveXiaozhiConsoleAgentConfig()
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "回复") }
                        item {
                            SettingsCard {
                                InputRow("语音角色ID", "留空使用控制台当前角色", xiaozhiVoiceRole) { xiaozhiVoiceRole = it }
                                InsetDivider()
                                SwitchRow(
                                    checked = xiaozhiMusicMcp,
                                    title = "提示官方 Music 工具",
                                    summary = "点歌类问题会提示小智优先使用控制台启用的官方 Music MCP",
                                    onCheckedChange = { xiaozhiMusicMcp = it }
                                )
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "MCP桥接") }
                        item {
                            SettingsCard {
                                SwitchRow(
                                    checked = xiaozhiMcpBridge,
                                    title = "启用MCP桥接实验",
                                    summary = "把 Hchat 能力暴露给小智调用；端点地址包含 token，请勿外传",
                                    onCheckedChange = { xiaozhiMcpBridge = it }
                                )
                                InsetDivider()
                                InputRow(
                                    "MCP Endpoint",
                                    "wss://api.xiaozhi.me/mcp/?token=...；留空不连接",
                                    xiaozhiMcpEndpoint
                                ) { xiaozhiMcpEndpoint = it }
                                InsetDivider()
                                XiaozhiMcpStatusRow(xiaozhiMcpStatus)
                                InsetDivider()
                                ActionRow("刷新连接状态", xiaozhiMcpStatus.detail) {
                                    if (xiaozhiConsoleToken.trim().isBlank() || xiaozhiConsoleAgentId.trim().isBlank()) {
                                        Toast.makeText(context, "请先登录并选择智能体", Toast.LENGTH_SHORT).show()
                                    } else {
                                        xiaozhiMcpStatus = XiaozhiMcpStatus(false, "查询中", "正在查询小智控制台...")
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    XiaozhiConsoleApi.fetchMcpEndpointStatus(
                                                        xiaozhiConsoleToken,
                                                        xiaozhiConsoleAgentId
                                                    )
                                                }
                                            }
                                            result.onSuccess {
                                                xiaozhiMcpStatus = it
                                            }.onFailure {
                                                xiaozhiMcpStatus = XiaozhiMcpStatus(false, "查询失败", it.message.orEmpty())
                                            }
                                        }
                                    }
                                }
                                InsetDivider()
                                InputRow(
                                    "就绪等待时长",
                                    "断开后首次触发等待 MCP ready 的秒数，默认 5，可填 1-30",
                                    xiaozhiMcpReadySeconds
                                ) { xiaozhiMcpReadySeconds = it.filter { ch -> ch.isDigit() }.take(2) }
                                InsetDivider()
                                InputRow(
                                    "自动断开时长",
                                    "空闲多少秒后断开 MCP，默认 90，可填 10-600",
                                    xiaozhiMcpIdleSeconds
                                ) { xiaozhiMcpIdleSeconds = it.filter { ch -> ch.isDigit() }.take(3) }

                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行状态") }
                        item {
                            SettingsCard {
                                ActionRow("立即清空上下文", "清除当前运行中的小智对话记忆") {
                                    AutoReplyRuntime.clearAiHistories()
                                    Toast.makeText(context, "小智上下文已清空", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            AutoReplyAiRoute.Zhilia -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "智聊AI配置",
                    largeTitle = "智聊AI配置",
                    scrollBehavior = scrollBehavior,
                    bottomBar = {
                        BottomActionBar(
                            primaryText = "保存智聊",
                            onPrimaryClick = { saveZhilia(showToast = true) },
                            secondaryText = "返回",
                            onSecondaryClick = { route = AutoReplyAiRoute.Main }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = zhiliaListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
                    ) {
                        item { SmallTitle(text = "当前配置") }
                        item {
                            SettingsCard {
                                InfoRow("当前启用", activeName)
                                InsetDivider()
                                InputRow("配置名称", "例如 DeepSeek 主账号", cfgName) { cfgName = it }
                                InsetDivider()
                                InputRow("API Key", "OpenAI 兼容接口密钥", apiKey) { apiKey = it }
                                InsetDivider()
                                InputRow("API 地址", "例如 https://api.xxx.com/v1", apiBase) { apiBase = it }
                                InsetDivider()
                                InputRow("API 路径", "默认 /chat/completions", apiPath) { apiPath = it }
                                InsetDivider()
                                InputRow("模型", "例如 deepseek-ai/DeepSeek-V3", model) { model = it }
                                InsetDivider()
                                InputRow("系统指令", "AI 角色设定和回复要求", prompt, minLines = 4) { prompt = it }
                                InsetDivider()
                                NumberInputRow("上下文轮数", "0 表示不保留上下文", contextLimit) { contextLimit = it }
                                InsetDivider()
                                SwitchRow(sp, AutoReplySettings.KEY_AI_STREAM, "流式请求", "失败时会自动尝试非流式", AutoReplySettings.DEFAULT_AI_STREAM)
                                InsetDivider()
                                SwitchRow(sp, AutoReplySettings.KEY_AI_CLEAR_CONTEXT_ON_SAVE, "保存后清空上下文", "修改人设或模型后避免继续沿用旧对话", AutoReplySettings.DEFAULT_AI_CLEAR_CONTEXT_ON_SAVE)
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "配置管理") }
                        item {
                            SettingsCard {
                                ActionRow("配置列表", "${configs.size} 个配置，当前启用：$activeName") {
                                    zhiliaPendingName = activeName
                                    route = AutoReplyAiRoute.ZhiliaConfigs
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模型工具") }
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
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行状态") }
                        item {
                            SettingsCard {
                                ActionRow("立即清空上下文", "清除当前运行中的 AI 对话记忆") {
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
                    bottomBar = {
                        BottomActionBar(
                            primaryText = "切换到所选",
                            onPrimaryClick = { switchZhiliaTo(zhiliaPendingName) },
                            secondaryText = "返回",
                            onSecondaryClick = { route = AutoReplyAiRoute.Zhilia }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = zhiliaConfigListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
                    ) {
                        item { SmallTitle(text = "配置") }
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
                                        zhiliaPendingName = config.name
                                        Toast.makeText(context, "已选择 ${config.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "管理") }
                        item {
                            SettingsCard {
                                ActionRow("新增空配置", "创建后留在此页，可再手动切换") {
                                    val newName = uniqueConfigName("新配置")
                                    val next = configs + AutoReplyZhiliaConfig(newName, "", AutoReplySettings.DEFAULT_AI_API_BASE, AutoReplySettings.DEFAULT_AI_API_PATH, AutoReplySettings.DEFAULT_AI_MODEL, "", AutoReplySettings.DEFAULT_AI_CONTEXT_LIMIT)
                                    settings.saveZhiliaConfigs(next, activeName)
                                    configs = settings.zhiliaConfigs()
                                    zhiliaPendingName = newName
                                    Toast.makeText(context, "已新增 $newName", Toast.LENGTH_SHORT).show()
                                }
                                InsetDivider()
                                ActionRow("复制当前输入", "按智聊编辑页当前输入复制配置") {
                                    val newName = uniqueConfigName("${cfgName.ifBlank { activeName }}_副本")
                                    val next = configs + currentZhiliaConfig().copy(name = newName)
                                    settings.saveZhiliaConfigs(next, activeName)
                                    configs = settings.zhiliaConfigs()
                                    zhiliaPendingName = newName
                                    Toast.makeText(context, "已复制 $newName", Toast.LENGTH_SHORT).show()
                                }
                                if (configs.size > 1) {
                                    InsetDivider()
                                    ActionRow("删除当前配置", "删除正在编辑的配置，至少保留一个") {
                                        val next = configs.filterNot { it.name == activeName || it.name == cfgName }
                                        val nextActive = next.firstOrNull()?.name ?: AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME
                                        settings.saveZhiliaConfigs(next, nextActive)
                                        configs = settings.zhiliaConfigs()
                                        activeName = settings.activeZhiliaName()
                                        zhiliaPendingName = activeName
                                        Toast.makeText(context, "已删除当前配置", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AutoReplyAiRoute.ZhiliaModels -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "智聊模型选择",
                    largeTitle = "智聊模型选择",
                    scrollBehavior = scrollBehavior,
                    bottomBar = {
                        BottomActionBar(
                            primaryText = "使用所选模型",
                            onPrimaryClick = {
                                model = pendingModel
                                Toast.makeText(context, "已选择模型: $pendingModel", Toast.LENGTH_SHORT).show()
                            },
                            secondaryText = "返回",
                            onSecondaryClick = { route = AutoReplyAiRoute.Zhilia }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = zhiliaModelListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
                    ) {
                        item { SmallTitle(text = "拉取模型") }
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
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模型列表 · ${visibleModels.size}") }
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
                                            Toast.makeText(context, "已选择 $itemModel", Toast.LENGTH_SHORT).show()
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
            is AutoReplyAiRoute.OptionPicker -> {
                OptionPickerPage(
                    request = currentRoute.request,
                    onBack = { route = AutoReplyAiRoute.Xiaozhi },
                    onSelected = { currentRoute.request.onSelected(it) }
                )
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
private fun AutoReplyStepsPage(
    title: String,
    initialSteps: List<AutoReplyStep>,
    onBack: () -> Unit,
    onSave: (List<AutoReplyStep>) -> Unit
) {
    val context = LocalContext.current
    var steps by remember(initialSteps) { mutableStateOf(initialSteps.ifEmpty { listOf(AutoReplyStep()) }) }
    var optionPicker by remember { mutableStateOf<OptionPickerRequest?>(null) }
    var contactPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    fun update(index: Int, step: AutoReplyStep) {
        steps = steps.toMutableList().also { if (index in it.indices) it[index] = step }
    }
    val stepsRoute = when {
        favoritePicker != null -> AutoReplyStepsRoute.FavoritePicker(favoritePicker!!)
        optionPicker != null -> AutoReplyStepsRoute.OptionPicker(optionPicker!!)
        contactPicker != null -> AutoReplyStepsRoute.ContactPicker(contactPicker!!)
        else -> AutoReplyStepsRoute.Main
    }
    SettingsRouteTransition(
        targetState = stepsRoute,
        label = "AutoReplyStepsRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is AutoReplyStepsRoute.OptionPicker -> {
                val request = currentRoute.request
                OptionPickerPage(
                    request = request,
                    onBack = { optionPicker = null },
                    onSelected = { request.onSelected(it); optionPicker = null }
                )
            }
            is AutoReplyStepsRoute.ContactPicker -> {
                val request = currentRoute.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { contactPicker = null },
                    onConfirm = { selected ->
                        request.onValue(formatIds(selected.map { it.id }))
                        Toast.makeText(context, "已选择 ${selected.size} 项", Toast.LENGTH_SHORT).show()
                        contactPicker = null
                    }
                )
            }
            is AutoReplyStepsRoute.FavoritePicker -> {
                FavoritePickerPage(
                    request = currentRoute.request,
                    onBack = { favoritePicker = null }
                )
            }
            AutoReplyStepsRoute.Main -> PageScaffold(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                bottomBar = {
                    BottomActionBar(
                        primaryText = "保存回复",
                        onPrimaryClick = { onSave(steps.filter { AutoReplySettings.isAiReplyMode(it.mode) || it.content.isNotBlank() }) },
                        secondaryText = "返回",
                        onSecondaryClick = onBack
                    )
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        item { SmallTitle(modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp), text = "第 ${index + 1} 条") }
                        item {
                            SettingsCard {
                                PopupOptionRow(
                                    title = "回复类型",
                                    summary = autoReplyStepLabel(step.mode),
                                    options = autoReplyStepOptions(),
                                    currentValue = step.mode,
                                    onValueChanged = {
                                        update(
                                            index,
                                            step.copy(
                                                mode = it,
                                                content = when {
                                                    AutoReplySettings.isAiReplyMode(it) -> ""
                                                    it == step.mode -> step.content
                                                    it == AutoReplySettings.REPLY_FAVORITE ||
                                                        step.mode == AutoReplySettings.REPLY_FAVORITE -> ""
                                                    else -> step.content
                                                }
                                            )
                                        )
                                    }
                                )
                                InsetDivider()
                                if (!AutoReplySettings.isAiReplyMode(step.mode)) {
                                    AutoReplyStepContentEditor(
                                        context = context,
                                        step = step,
                                        onStepChange = { update(index, it) },
                                        onContactPicker = { contactPicker = it },
                                        onFavoritePicker = { favoritePicker = it }
                                    )
                                    InsetDivider()
                                }
                                NumberInputRow("发送前延迟", "单位 ms", step.delayMs.toString()) {
                                    update(index, step.copy(delayMs = it.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L))
                                }
                                InsetDivider()
                                SwitchRow(step.randomDelay, "随机追加延迟", "发送前额外随机等待 0-2 秒") {
                                    update(index, step.copy(randomDelay = it))
                                }
                                InsetDivider()
                                RedPacketReplyStepActions(
                                    canMoveUp = index > 0,
                                    canMoveDown = index < steps.lastIndex,
                                    onMoveUp = {
                                        steps = steps.toMutableList().also {
                                            val item = it.removeAt(index)
                                            it.add(index - 1, item)
                                        }
                                    },
                                    onMoveDown = {
                                        steps = steps.toMutableList().also {
                                            val item = it.removeAt(index)
                                            it.add(index + 1, item)
                                        }
                                    },
                                    onDelete = { steps = steps.toMutableList().also { it.removeAt(index) }.ifEmpty { listOf(AutoReplyStep()) } }
                                )
                            }
                        }
                    }
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                    item {
                        SettingsCard {
                            ActionRow("新增回复", "添加一条按顺序发送的回复") {
                                steps = steps + AutoReplyStep(id = System.currentTimeMillis().toString(), content = "你好")
                            }
                        }
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
    onContactPicker: (ContactPickerRequest) -> Unit,
    onFavoritePicker: (FavoritePickerRequest) -> Unit
) {
    when {
        step.mode == AutoReplySettings.REPLY_TEXT || step.mode == AutoReplySettings.REPLY_XML -> {
            VariableInputRow(
                title = if (step.mode == AutoReplySettings.REPLY_XML) "XML 内容" else "回复内容",
                summary = autoReplyStepContentHint(step.mode),
                value = step.content,
                variables = autoReplyTemplateVariables,
                minLines = 4
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
                        onValue = { onStepChange(step.copy(content = it)) },
                        enableLabels = step.mode == AutoReplySettings.REPLY_CARD
                    )
                )
            }
            if (step.content.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选", "移除当前已选择内容") {
                    onStepChange(step.copy(content = ""))
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
                        onValue = { onStepChange(step.copy(content = it)) },
                        multiSelect = true,
                        delimiter = ";;;"
                    )
                )
            }
            if (step.content.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选收藏", "移除当前回复类型的收藏") {
                    onStepChange(step.copy(content = ""))
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
                    val merged = (autoReplySplitContent(step.content) + paths)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    if (merged.isNotEmpty()) {
                        onStepChange(step.copy(content = merged.joinToString(";;;")))
                        Toast.makeText(context, "已选择 ${paths.size} 个文件", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (step.content.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选文件", "移除当前回复类型的文件") {
                    onStepChange(step.copy(content = ""))
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
    OptionItem("智聊AI回复", AutoReplySettings.REPLY_ZHILIA_AI, "使用智聊/OpenAI 兼容配置生成回复"),
    OptionItem("小智AI回复", AutoReplySettings.REPLY_XIAOZHI_AI, "使用小智 WebSocket 生成文字回复"),
    OptionItem("小智语音回复", AutoReplySettings.REPLY_XIAOZHI_VOICE, "使用小智 TTS 生成语音回复")
)

private fun autoReplyStepLabel(value: Int): String = autoReplyStepOptions().firstOrNull { it.value == value }?.label ?: "发送文字"

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
