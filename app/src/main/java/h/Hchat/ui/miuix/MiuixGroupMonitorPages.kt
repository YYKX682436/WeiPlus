package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.groupleave.GroupLeaveReplyTemplate
import h.Hchat.hooks.items.groupleave.GroupLeaveReplyTemplateBinding
import h.Hchat.hooks.items.groupleave.GroupLeaveMonitorSettings
import h.Hchat.hooks.items.grouprename.GroupRenameReplyTemplate
import h.Hchat.hooks.items.grouprename.GroupRenameMonitorSettings
import h.Hchat.hooks.items.grouprename.GroupRenameTemplateBinding
import h.Hchat.hooks.items.membertitle.MemberTitleStore
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.random.Random

@Composable
internal fun GroupRenameMonitorMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, GroupRenameMonitorSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<GroupRenameRoute>(GroupRenameRoute.Main) }
    var templates by remember {
        mutableStateOf(GroupRenameMonitorSettings.parseTemplates(sp.getString(GroupRenameMonitorSettings.KEY_TEMPLATES, "").orEmpty()))
    }
    var templateBindings by remember {
        mutableStateOf(GroupRenameMonitorSettings.parseBindings(sp.getString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, "").orEmpty()))
    }
    var noticeEnabled by remember { mutableStateOf(sp.getBoolean(GroupRenameMonitorSettings.KEY_NOTICE_ENABLE, GroupRenameMonitorSettings.DEFAULT_NOTICE_ENABLE)) }
    var noticeText by remember {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_NOTICE_TEXT, GroupRenameMonitorSettings.DEFAULT_NOTICE_TEXT) ?: GroupRenameMonitorSettings.DEFAULT_NOTICE_TEXT)
    }
    var noticeScope by remember {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_NOTICE_SCOPE, GroupRenameMonitorSettings.DEFAULT_NOTICE_SCOPE) ?: GroupRenameMonitorSettings.DEFAULT_NOTICE_SCOPE)
    }
    var noticeGroups by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_NOTICE_GROUPS, "").orEmpty()) }
    var sendEnabled by remember { mutableStateOf(sp.getBoolean(GroupRenameMonitorSettings.KEY_SEND_ENABLE, GroupRenameMonitorSettings.DEFAULT_SEND_ENABLE)) }
    var listenGroups by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_LISTEN_GROUPS, "").orEmpty()) }
    var delaySeconds by remember { mutableStateOf(sp.getInt(GroupRenameMonitorSettings.KEY_DELAY_SECONDS, GroupRenameMonitorSettings.DEFAULT_DELAY_SECONDS).toString()) }
    var promptType by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE) ?: GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE) }
    var bothOrder by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_BOTH_ORDER, GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER) ?: GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER) }
    var text by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_TEXT, GroupRenameMonitorSettings.DEFAULT_TEXT) ?: GroupRenameMonitorSettings.DEFAULT_TEXT) }
    var cardTitle by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_CARD_TITLE, GroupRenameMonitorSettings.DEFAULT_CARD_TITLE) ?: GroupRenameMonitorSettings.DEFAULT_CARD_TITLE) }
    var cardDesc by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_CARD_DESC, GroupRenameMonitorSettings.DEFAULT_CARD_DESC) ?: GroupRenameMonitorSettings.DEFAULT_CARD_DESC) }
    var wxidColor by remember {
        mutableStateOf(
            MemberTitleStore.cleanColor(
                sp.getString(GroupRenameMonitorSettings.KEY_WXID_COLOR, GroupRenameMonitorSettings.DEFAULT_WXID_COLOR)
                    ?.substringBefore(',')
            ).ifEmpty { GroupRenameMonitorSettings.DEFAULT_WXID_COLOR }
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    SettingsRouteTransition(
        targetState = route,
        label = "GroupRenameRoute",
        depthOf = {
            when (it) {
                GroupRenameRoute.Main -> 0
                GroupRenameRoute.GroupManager,
                GroupRenameRoute.TemplateManager,
                GroupRenameRoute.BatchTemplateBinding,
                is GroupRenameRoute.ContactPicker -> 1
                is GroupRenameRoute.GroupEditor,
                is GroupRenameRoute.TemplateEditor -> 2
            }
        }
    ) { currentRoute ->
        when (currentRoute) {
            is GroupRenameRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = if (currentRoute.fromMain) GroupRenameRoute.Main else GroupRenameRoute.GroupManager },
                onConfirm = { selected ->
                    currentRoute.request.onValue(formatIds(selected.map { it.id }))
                    route = if (currentRoute.fromMain) GroupRenameRoute.Main else GroupRenameRoute.GroupManager
                }
            )
            GroupRenameRoute.GroupManager -> GroupRenameGroupManagerPage(
                context = context,
                listenGroups = listenGroups,
                templates = templates,
                bindings = templateBindings,
                onPickGroups = {
                    route = GroupRenameRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "选择改名监听群",
                            mode = ContactPickerMode.GROUPS,
                            multiSelect = true,
                            existingValue = listenGroups,
                            onValue = { next ->
                                listenGroups = next
                                sp.edit().putString(GroupRenameMonitorSettings.KEY_LISTEN_GROUPS, next).apply()
                            }
                        )
                    )
                },
                onOpenGroup = { groupId, label -> route = GroupRenameRoute.GroupEditor(groupId, label) },
                onOpenTemplates = { route = GroupRenameRoute.TemplateManager },
                onBatchApplyTemplate = { route = GroupRenameRoute.BatchTemplateBinding },
                onDeleteGroups = { targets ->
                    val targetIds = targets.mapTo(HashSet()) { it.id }
                    val nextListenGroups = formatIds(parseIds(listenGroups) - targetIds)
                    val nextBindings = templateBindings.filterNot { it.groupId in targetIds }
                    targetIds.forEach { groupId ->
                        deleteGroupRenameConfiguration(sp, groupId, nextListenGroups, nextBindings)
                    }
                    listenGroups = nextListenGroups
                    templateBindings = nextBindings
                    Toast.makeText(context, "已删除 ${targets.size} 个监听群", Toast.LENGTH_SHORT).show()
                },
                onBack = { route = GroupRenameRoute.Main }
            )
            is GroupRenameRoute.GroupEditor -> GroupRenameGroupEditorPage(
                context = context,
                sp = sp,
                groupId = currentRoute.groupId,
                groupLabel = currentRoute.label,
                templates = templates,
                bindings = templateBindings,
                onBindingsChanged = { next ->
                    templateBindings = next
                    sp.edit().putString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, GroupRenameMonitorSettings.encodeBindings(next)).apply()
                },
                onDelete = {
                    listenGroups = formatIds(parseIds(listenGroups) - currentRoute.groupId)
                    templateBindings = templateBindings.filterNot { it.groupId == currentRoute.groupId }
                    deleteGroupRenameConfiguration(sp, currentRoute.groupId, listenGroups, templateBindings)
                    Toast.makeText(context, "群配置已删除", Toast.LENGTH_SHORT).show()
                    route = GroupRenameRoute.GroupManager
                },
                onBack = { route = GroupRenameRoute.GroupManager }
            )
            GroupRenameRoute.TemplateManager -> GroupRenameTemplateListPage(
                templates = templates,
                bindings = templateBindings,
                onBack = { route = GroupRenameRoute.GroupManager },
                onOpenTemplate = { index, template -> route = GroupRenameRoute.TemplateEditor(index, template, true) },
                onAddTemplate = {
                    route = GroupRenameRoute.TemplateEditor(
                        templates.size,
                        newGroupRenameTemplate(templates.size + 1, sp),
                        false
                    )
                }
            )
            is GroupRenameRoute.TemplateEditor -> GroupRenameTemplateEditorPage(
                context = context,
                template = currentRoute.template,
                canDelete = currentRoute.canDelete,
                onBack = { route = GroupRenameRoute.TemplateManager },
                onSave = { updated ->
                    templates = if (currentRoute.index in templates.indices) {
                        templates.toMutableList().also { it[currentRoute.index] = updated }
                    } else {
                        templates + updated
                    }
                    sp.edit().putString(GroupRenameMonitorSettings.KEY_TEMPLATES, GroupRenameMonitorSettings.encodeTemplates(templates)).apply()
                    Toast.makeText(context, "改名模板已保存", Toast.LENGTH_SHORT).show()
                    route = GroupRenameRoute.TemplateManager
                },
                onDelete = {
                    if (currentRoute.index in templates.indices) {
                        val templateId = templates[currentRoute.index].id
                        templates = templates.toMutableList().also { it.removeAt(currentRoute.index) }
                        templateBindings = templateBindings.filterNot { it.templateId == templateId }
                        sp.edit()
                            .putString(GroupRenameMonitorSettings.KEY_TEMPLATES, GroupRenameMonitorSettings.encodeTemplates(templates))
                            .putString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, GroupRenameMonitorSettings.encodeBindings(templateBindings))
                            .apply()
                    }
                    route = GroupRenameRoute.TemplateManager
                }
            )
            GroupRenameRoute.BatchTemplateBinding -> GroupRenameBatchTemplateBindingPage(
                context = context,
                listenGroups = listenGroups,
                templates = templates,
                bindings = templateBindings,
                onBack = { route = GroupRenameRoute.GroupManager },
                onSave = { additions ->
                    templateBindings = upsertGroupRenameTemplateBindings(templateBindings, additions)
                    sp.edit().putString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, GroupRenameMonitorSettings.encodeBindings(templateBindings)).apply()
                    Toast.makeText(context, "模板已套用到 ${additions.size} 个群", Toast.LENGTH_SHORT).show()
                    route = GroupRenameRoute.GroupManager
                }
            )
            GroupRenameRoute.Main -> PageScaffold(
                title = provider.title(),
                largeTitle = provider.title(),
                scrollBehavior = scrollBehavior,
                bottomBar = {
                    BottomActionBar(
                        primaryText = "保存设置",
                        onPrimaryClick = {
                            val color = MemberTitleStore.cleanColor(wxidColor.substringBefore(','))
                                .ifEmpty { GroupRenameMonitorSettings.DEFAULT_WXID_COLOR }
                            wxidColor = color
                            sp.edit()
                                .putBoolean(GroupRenameMonitorSettings.KEY_NOTICE_ENABLE, noticeEnabled)
                                .putString(GroupRenameMonitorSettings.KEY_NOTICE_TEXT, noticeText)
                                .putString(GroupRenameMonitorSettings.KEY_NOTICE_SCOPE, noticeScope)
                                .putString(GroupRenameMonitorSettings.KEY_NOTICE_GROUPS, noticeGroups)
                                .putBoolean(GroupRenameMonitorSettings.KEY_SEND_ENABLE, sendEnabled)
                                .putString(GroupRenameMonitorSettings.KEY_LISTEN_GROUPS, listenGroups)
                                .putInt(GroupRenameMonitorSettings.KEY_DELAY_SECONDS, delaySeconds.toIntOrNull()?.coerceIn(0, 600) ?: GroupRenameMonitorSettings.DEFAULT_DELAY_SECONDS)
                                .putString(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, promptType)
                                .putString(GroupRenameMonitorSettings.KEY_BOTH_ORDER, bothOrder)
                                .putString(GroupRenameMonitorSettings.KEY_TEXT, text)
                                .putString(GroupRenameMonitorSettings.KEY_CARD_TITLE, cardTitle)
                                .putString(GroupRenameMonitorSettings.KEY_CARD_DESC, cardDesc)
                                .putString(GroupRenameMonitorSettings.KEY_WXID_COLOR, color)
                                .apply()
                            Toast.makeText(context, "改名监控设置已保存", Toast.LENGTH_SHORT).show()
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
                    item { SmallTitle(text = "提醒方式") }
                    item {
                        SettingsCard {
                            SwitchRow(noticeEnabled, "插入系统消息", "在对应群聊插入可点击成员资料的改名消息", onCheckedChange = { noticeEnabled = it })
                            InsetDivider()
                            SwitchRow(sendEnabled, "发送改名提醒", "向选择的监听群发送文本或卡片", onCheckedChange = { sendEnabled = it })
                            if (noticeEnabled) {
                                InsetDivider()
                                VariableInputRow(
                                    "系统消息模板",
                                    "wxid变量会生成可点击资料链接",
                                    noticeText,
                                    groupRenameNoticeVariables,
                                    minLines = 3
                                ) { noticeText = it }
                                InsetDivider()
                                PopupChoiceRow(
                                    title = "适用群聊",
                                    summary = systemNoticeScopeLabel(noticeScope, GroupRenameMonitorSettings.NOTICE_SCOPE_SPECIFIC),
                                    options = systemNoticeScopeChoices(
                                        GroupRenameMonitorSettings.NOTICE_SCOPE_ALL,
                                        GroupRenameMonitorSettings.NOTICE_SCOPE_SPECIFIC
                                    ),
                                    currentValue = noticeScope,
                                    onValueChanged = { noticeScope = it }
                                )
                                if (noticeScope == GroupRenameMonitorSettings.NOTICE_SCOPE_SPECIFIC) {
                                    InsetDivider()
                                    ActionRow("选择指定群聊", systemNoticeGroupSummary(noticeGroups)) {
                                        route = GroupRenameRoute.ContactPicker(
                                            request = ContactPickerRequest(
                                                title = "选择系统消息群聊",
                                                mode = ContactPickerMode.GROUPS,
                                                multiSelect = true,
                                                existingValue = noticeGroups,
                                                onValue = { noticeGroups = it }
                                            ),
                                            fromMain = true
                                        )
                                    }
                                }
                                InsetDivider()
                                ColorPickerRow(
                                    "wxid颜色",
                                    "系统消息里的 wxid 链接颜色",
                                    wxidColor,
                                    allowGradient = false,
                                    onReset = { wxidColor = GroupRenameMonitorSettings.DEFAULT_WXID_COLOR }
                                ) { wxidColor = it.take(9) }
                            }
                        }
                    }
                    if (sendEnabled) {
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "发送设置") }
                        item {
                            SettingsCard {
                                ActionRow("监听群与专属设置", groupSelectionSummary(listenGroups)) {
                                    route = GroupRenameRoute.GroupManager
                                }
                                InsetDivider()
                                ActionRow(
                                    "提醒模板管理",
                                    if (templates.isEmpty()) "暂无模板，进入后添加批量配置" else "${templates.size} 个模板，进入后修改或删除"
                                ) {
                                    route = GroupRenameRoute.TemplateManager
                                }
                                InsetDivider()
                                ActionRow(
                                    "批量套用模板",
                                    if (templates.isEmpty()) "先新增模板，再批量绑定监听群" else groupRenameBindingSummary(templateBindings, templates)
                                ) {
                                    route = GroupRenameRoute.BatchTemplateBinding
                                }
                                InsetDivider()
                                NumberInputRow("整体延迟", "单位秒，0-600", delaySeconds) { delaySeconds = it }
                                InsetDivider()
                                PopupChoiceRow(
                                    title = "提示类型",
                                    summary = groupRenamePromptTypeLabel(promptType),
                                    options = groupRenamePromptTypeChoices(),
                                    currentValue = promptType,
                                    onValueChanged = { promptType = it }
                                )
                                if (promptType == GroupRenameMonitorSettings.PROMPT_BOTH) {
                                    InsetDivider()
                                    PopupChoiceRow(
                                        title = "文本+卡片顺序",
                                        summary = groupRenameBothOrderLabel(bothOrder),
                                        options = groupRenameBothOrderChoices(),
                                        currentValue = bothOrder,
                                        onValueChanged = { bothOrder = it }
                                    )
                                }
                            }
                        }
                        if (promptType != GroupRenameMonitorSettings.PROMPT_CARD) {
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本模板") }
                            item {
                                SettingsCard {
                                    VariableInputRow("改名提醒文本", "多个模板用 || 分隔随机选择", text, groupRenameTemplateVariables, minLines = 3) { text = it }
                                }
                            }
                        }
                        if (promptType != GroupRenameMonitorSettings.PROMPT_TEXT) {
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "卡片模板") }
                            item {
                                SettingsCard {
                                    VariableInputRow("卡片标题", "多个模板用 || 分隔随机选择", cardTitle, groupRenameTemplateVariables) { cardTitle = it }
                                    InsetDivider()
                                    VariableInputRow("卡片描述", "多个模板用 || 分隔随机选择", cardDesc, groupRenameTemplateVariables, minLines = 3) { cardDesc = it }
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
private fun GroupRenameGroupManagerPage(
    context: Context,
    listenGroups: String,
    templates: List<GroupRenameReplyTemplate>,
    bindings: List<GroupRenameTemplateBinding>,
    onPickGroups: () -> Unit,
    onOpenGroup: (String, String) -> Unit,
    onOpenTemplates: () -> Unit,
    onBatchApplyTemplate: () -> Unit,
    onDeleteGroups: (List<ContactOption>) -> Unit,
    onBack: () -> Unit
) {
    var loading by remember(listenGroups) { mutableStateOf(true) }
    var error by remember(listenGroups) { mutableStateOf("") }
    var groups by remember(listenGroups) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember { mutableStateOf("") }
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedForDelete by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val selectedIds = remember(listenGroups) { parseIds(listenGroups).toList() }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(listenGroups) {
        loadContacts(context, ContactPickerMode.GROUPS) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取群聊失败"
            } else {
                val byId = result.associateBy { it.id }
                groups = selectedIds.map { id ->
                    byId[id] ?: ContactOption(id, id, group = true, avatarUrl = "", avatarBackupUrl = "")
                }
            }
        }
    }
    val lower = query.trim().lowercase(Locale.US)
    val visible = groups.filter { it.matchesSearch(lower) }
    val visibleIds = visible.mapTo(LinkedHashSet()) { it.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedForDelete }
    val selectedGroups = groups.filter { it.id in selectedForDelete }
    PageScaffold(
        title = "改名监听群",
        largeTitle = "改名监听群",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedGroups.size}）",
                    onPrimaryClick = {
                        if (selectedGroups.isEmpty()) {
                            Toast.makeText(context, "请先选择监听群", Toast.LENGTH_SHORT).show()
                        } else {
                            showDeleteConfirm = true
                        }
                    },
                    secondaryText = "取消",
                    onSecondaryClick = {
                        batchDeleteMode = false
                        selectedForDelete = emptySet()
                    },
                    middleText = if (visibleIds.isEmpty()) null else if (allVisibleSelected) "取消全选" else "全选",
                    onMiddleClick = if (visibleIds.isEmpty()) null else {
                        {
                            selectedForDelete = if (allVisibleSelected) {
                                selectedForDelete - visibleIds
                            } else {
                                selectedForDelete + visibleIds
                            }
                        }
                    }
                )
            } else {
                BottomActionBar(
                    primaryText = "选择监听群",
                    onPrimaryClick = onPickGroups,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
                    middleText = if (selectedIds.isEmpty()) null else "批量删除",
                    onMiddleClick = if (selectedIds.isEmpty()) null else {
                        {
                            batchDeleteMode = true
                            selectedForDelete = emptySet()
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
                    InputRow("搜索监听群", "群名称 / 群聊备注 / 群号", query) { query = it }
                }
            }
            if (!batchDeleteMode) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
                item {
                    SettingsCard {
                        ActionRow("提醒模板管理", if (templates.isEmpty()) "暂无模板，进入后添加" else "${templates.size} 个模板") {
                            onOpenTemplates()
                        }
                        InsetDivider()
                        ActionRow(
                            "批量套用模板",
                            if (templates.isEmpty()) "先新增模板，再批量绑定监听群" else groupRenameBindingSummary(bindings, templates)
                        ) {
                            onBatchApplyTemplate()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "已监听群 · ${selectedIds.size} 项") }
            item {
                SettingsCard {
                    when {
                        loading -> EmptyText("正在载入监听群...")
                        error.isNotEmpty() -> EmptyText(error)
                        selectedIds.isEmpty() -> EmptyText("暂无监听群。点击底部“选择监听群”添加。")
                        visible.isEmpty() -> EmptyText("没有匹配结果")
                        else -> visible.forEachIndexed { index, option ->
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = option.label.ifBlank { option.id },
                                        value = index,
                                        summary = groupRenameGroupBindingSummary(option.id, bindings, templates)
                                    ),
                                    selected = option.id in selectedForDelete,
                                    onClick = {
                                        selectedForDelete = if (option.id in selectedForDelete) {
                                            selectedForDelete - option.id
                                        } else {
                                            selectedForDelete + option.id
                                        }
                                    }
                                )
                            } else {
                                SelectRow(
                                    title = option.label.ifBlank { option.id },
                                    summary = groupRenameGroupBindingSummary(option.id, bindings, templates),
                                    onClick = { onOpenGroup(option.id, option.label.ifBlank { option.id }) }
                                )
                            }
                            if (index < visible.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedGroups.size} 个改名监听群及其专属配置，此操作不可撤销。",
        labels = selectedGroups.map { it.label.ifBlank { it.id } },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selectedGroups
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedForDelete = emptySet()
            onDeleteGroups(targets)
        }
    )
}

@Composable
private fun GroupRenameTemplateListPage(
    templates: List<GroupRenameReplyTemplate>,
    bindings: List<GroupRenameTemplateBinding>,
    onBack: () -> Unit,
    onOpenTemplate: (Int, GroupRenameReplyTemplate) -> Unit,
    onAddTemplate: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "改名提醒模板",
        largeTitle = "改名提醒模板",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "新增模板",
                onPrimaryClick = onAddTemplate,
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
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。新增后可批量套用到监听群。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SelectRow(
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = "${describeGroupRenameTemplate(template)} · 已绑定 ${bindings.count { it.templateId == template.id }} 个群",
                                onClick = { onOpenTemplate(index, template) }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRenameTemplateEditorPage(
    context: Context,
    template: GroupRenameReplyTemplate,
    canDelete: Boolean,
    onBack: () -> Unit,
    onSave: (GroupRenameReplyTemplate) -> Unit,
    onDelete: () -> Unit
) {
    var name by rememberSaveable(template.id) { mutableStateOf(template.name) }
    var enabled by rememberSaveable(template.id) { mutableStateOf(template.enabled) }
    var delaySeconds by rememberSaveable(template.id) { mutableStateOf(template.delaySeconds.toString()) }
    var promptType by rememberSaveable(template.id) { mutableStateOf(template.promptType) }
    var bothOrder by rememberSaveable(template.id) { mutableStateOf(template.bothOrder) }
    var text by rememberSaveable(template.id) { mutableStateOf(template.text) }
    var cardTitle by rememberSaveable(template.id) { mutableStateOf(template.cardTitle) }
    var cardDesc by rememberSaveable(template.id) { mutableStateOf(template.cardDesc) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = name.ifBlank { "改名提醒模板" },
        largeTitle = "编辑改名模板",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存模板",
                onPrimaryClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "请输入模板名称", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(
                            template.copy(
                                name = name.trim(),
                                enabled = enabled,
                                delaySeconds = delaySeconds.toIntOrNull()?.coerceIn(0, 600) ?: GroupRenameMonitorSettings.DEFAULT_DELAY_SECONDS,
                                promptType = GroupRenameMonitorSettings.normalizePromptType(promptType),
                                bothOrder = GroupRenameMonitorSettings.normalizeBothOrder(bothOrder),
                                text = text,
                                cardTitle = cardTitle,
                                cardDesc = cardDesc
                            )
                        )
                    }
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
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    InputRow("模板名称", "用于列表和批量套用", name) { name = it }
                    InsetDivider()
                    SwitchRow(enabled, "启用模板", "关闭后已绑定群不会发送改名提醒", onCheckedChange = { enabled = it })
                    InsetDivider()
                    NumberInputRow("整体延迟", "单位秒，0-600", delaySeconds) { delaySeconds = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提示设置") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "提示类型",
                        summary = groupRenamePromptTypeLabel(promptType),
                        options = groupRenamePromptTypeChoices(),
                        currentValue = promptType,
                        onValueChanged = { promptType = it }
                    )
                    if (promptType == GroupRenameMonitorSettings.PROMPT_BOTH) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "文本+卡片顺序",
                            summary = groupRenameBothOrderLabel(bothOrder),
                            options = groupRenameBothOrderChoices(),
                            currentValue = bothOrder,
                            onValueChanged = { bothOrder = it }
                        )
                    }
                }
            }
            if (promptType != GroupRenameMonitorSettings.PROMPT_CARD) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本模板") }
                item {
                    SettingsCard {
                        VariableInputRow("改名提醒文本", "多个模板用 || 分隔随机选择", text, groupRenameTemplateVariables, minLines = 3) { text = it }
                    }
                }
            }
            if (promptType != GroupRenameMonitorSettings.PROMPT_TEXT) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "卡片模板") }
                item {
                    SettingsCard {
                        VariableInputRow("卡片标题", "多个模板用 || 分隔随机选择", cardTitle, groupRenameTemplateVariables) { cardTitle = it }
                        InsetDivider()
                        VariableInputRow("卡片描述", "多个模板用 || 分隔随机选择", cardDesc, groupRenameTemplateVariables, minLines = 3) { cardDesc = it }
                    }
                }
            }
            if (canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除模板", "删除后同时解除所有群绑定") { onDelete() }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRenameBatchTemplateBindingPage(
    context: Context,
    listenGroups: String,
    templates: List<GroupRenameReplyTemplate>,
    bindings: List<GroupRenameTemplateBinding>,
    onBack: () -> Unit,
    onSave: (List<GroupRenameTemplateBinding>) -> Unit
) {
    var loading by remember(listenGroups) { mutableStateOf(true) }
    var error by remember(listenGroups) { mutableStateOf("") }
    var groups by remember(listenGroups) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember { mutableStateOf("") }
    var selectedTemplateId by remember(templates) { mutableStateOf(templates.firstOrNull()?.id.orEmpty()) }
    var selectedGroups by remember(listenGroups) { mutableStateOf(parseIds(listenGroups)) }
    val selectedIds = remember(listenGroups) { parseIds(listenGroups).toList() }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(listenGroups) {
        loadContacts(context, ContactPickerMode.GROUPS) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取群聊失败"
            } else {
                val byId = result.associateBy { it.id }
                groups = selectedIds.map { id ->
                    byId[id] ?: ContactOption(id, id, group = true, avatarUrl = "", avatarBackupUrl = "")
                }
            }
        }
    }
    val lower = query.trim().lowercase(Locale.US)
    val visible = groups.filter { it.matchesSearch(lower) }
    val visibleIds = visible.map { it.id }.toSet()
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedGroups }
    PageScaffold(
        title = "批量套用改名模板",
        largeTitle = "批量套用改名模板",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "套用模板",
                onPrimaryClick = {
                    when {
                        selectedTemplateId.isBlank() -> Toast.makeText(context, "请先选择模板", Toast.LENGTH_SHORT).show()
                        selectedGroups.isEmpty() -> Toast.makeText(context, "请先选择群", Toast.LENGTH_SHORT).show()
                        else -> {
                            val labels = groups.associate { it.id to it.label.ifBlank { it.id } }
                            onSave(selectedGroups.map { groupId ->
                                GroupRenameTemplateBinding(
                                    groupId,
                                    labels[groupId] ?: bindings.firstOrNull { it.groupId == groupId }?.label ?: groupId,
                                    selectedTemplateId
                                )
                            })
                        }
                    }
                },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (visibleIds.isNotEmpty()) if (allVisibleSelected) "取消全选" else "全选" else null,
                onMiddleClick = if (visibleIds.isNotEmpty()) {
                    {
                        selectedGroups = if (allVisibleSelected) selectedGroups - visibleIds else selectedGroups + visibleIds
                    }
                } else null
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
                    PopupChoiceRow(
                        title = "选择模板",
                        summary = templates.firstOrNull { it.id == selectedTemplateId }?.name.orEmpty(),
                        options = templates.map { PopupChoice(it.name.ifBlank { it.id }, it.id) },
                        currentValue = selectedTemplateId,
                        onValueChanged = { selectedTemplateId = it }
                    )
                    InsetDivider()
                    InputRow("搜索监听群", "群名称 / 群聊备注 / 群号", query) { query = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "已选择 ${selectedGroups.size} 个群") }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入监听群...") } }
                error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                else -> visible.forEach { option ->
                    item {
                        ContactListCard(
                            option = option,
                            selected = option.id in selectedGroups,
                            multiSelect = true,
                            onClick = {
                                selectedGroups = if (option.id in selectedGroups) selectedGroups - option.id else selectedGroups + option.id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRenameGroupEditorPage(
    context: Context,
    sp: SharedPreferences,
    groupId: String,
    groupLabel: String,
    templates: List<GroupRenameReplyTemplate>,
    bindings: List<GroupRenameTemplateBinding>,
    onBindingsChanged: (List<GroupRenameTemplateBinding>) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val globalPromptType = sp.getString(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE)
        ?: GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE
    val globalBothOrder = sp.getString(GroupRenameMonitorSettings.KEY_BOTH_ORDER, GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER)
        ?: GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER
    val globalText = sp.getString(GroupRenameMonitorSettings.KEY_TEXT, GroupRenameMonitorSettings.DEFAULT_TEXT)
        ?: GroupRenameMonitorSettings.DEFAULT_TEXT
    val globalCardTitle = sp.getString(GroupRenameMonitorSettings.KEY_CARD_TITLE, GroupRenameMonitorSettings.DEFAULT_CARD_TITLE)
        ?: GroupRenameMonitorSettings.DEFAULT_CARD_TITLE
    val globalCardDesc = sp.getString(GroupRenameMonitorSettings.KEY_CARD_DESC, GroupRenameMonitorSettings.DEFAULT_CARD_DESC)
        ?: GroupRenameMonitorSettings.DEFAULT_CARD_DESC
    var promptType by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, groupId), GroupRenameMonitorSettings.MODE_GLOBAL) ?: GroupRenameMonitorSettings.MODE_GLOBAL)
    }
    var bothOrder by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_BOTH_ORDER, groupId), globalBothOrder) ?: globalBothOrder)
    }
    var text by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_TEXT, groupId), globalText) ?: globalText)
    }
    var cardTitle by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_TITLE, groupId), globalCardTitle) ?: globalCardTitle)
    }
    var cardDesc by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_DESC, groupId), globalCardDesc) ?: globalCardDesc)
    }
    val effectivePromptType = if (promptType == GroupRenameMonitorSettings.MODE_GLOBAL) {
        GroupRenameMonitorSettings.normalizePromptType(globalPromptType)
    } else {
        GroupRenameMonitorSettings.normalizePromptType(promptType)
    }
    val boundTemplate = templates.firstOrNull { template ->
        bindings.firstOrNull { it.groupId == groupId }?.templateId == template.id
    }
    val templateOptions = listOf(PopupChoice("不使用模板", "")) + templates.map {
        PopupChoice(it.name.ifBlank { it.id }, it.id)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = groupLabel.ifBlank { groupId },
        largeTitle = "专属改名设置",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (boundTemplate != null) {
                BottomActionBar("返回", onBack)
            } else {
                BottomActionBar(
                    primaryText = "保存设置",
                    onPrimaryClick = {
                        sp.edit()
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, groupId), promptType)
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_BOTH_ORDER, groupId), bothOrder)
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_TEXT, groupId), text)
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_TITLE, groupId), cardTitle)
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_DESC, groupId), cardDesc)
                            .apply()
                        Toast.makeText(context, "专属改名设置已保存", Toast.LENGTH_SHORT).show()
                    },
                    secondaryText = "返回",
                    onSecondaryClick = onBack
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
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "选择模板",
                        summary = boundTemplate?.let { describeGroupRenameTemplate(it) } ?: "不使用模板，按本群专属设置发送",
                        options = templateOptions,
                        currentValue = boundTemplate?.id.orEmpty(),
                        onValueChanged = { templateId ->
                            val next = if (templateId.isBlank()) {
                                bindings.filterNot { it.groupId == groupId }
                            } else {
                                upsertGroupRenameTemplateBindings(
                                    bindings,
                                    listOf(GroupRenameTemplateBinding(groupId, groupLabel.ifBlank { groupId }, templateId))
                                )
                            }
                            onBindingsChanged(next)
                        }
                    )
                }
            }
            if (boundTemplate == null) {
                item { SmallTitle(text = "提示设置") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "提示类型",
                            summary = groupRenamePromptTypeWithGlobalLabel(promptType),
                            options = groupRenamePromptTypeWithGlobalChoices(),
                            currentValue = promptType,
                            onValueChanged = { promptType = it }
                        )
                        if (promptType == GroupRenameMonitorSettings.PROMPT_BOTH) {
                            InsetDivider()
                            PopupChoiceRow(
                                title = "文本+卡片顺序",
                                summary = groupRenameBothOrderLabel(bothOrder),
                                options = groupRenameBothOrderChoices(),
                                currentValue = bothOrder,
                                onValueChanged = { bothOrder = it }
                            )
                        }
                    }
                }
                if (effectivePromptType != GroupRenameMonitorSettings.PROMPT_CARD) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本模板") }
                    item {
                        SettingsCard {
                            VariableInputRow("改名提醒文本", "本群专属；多个模板用 || 分隔随机选择", text, groupRenameTemplateVariables, minLines = 3) { text = it }
                        }
                    }
                }
                if (effectivePromptType != GroupRenameMonitorSettings.PROMPT_TEXT) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "卡片模板") }
                    item {
                        SettingsCard {
                            VariableInputRow("卡片标题", "本群专属；多个模板用 || 分隔随机选择", cardTitle, groupRenameTemplateVariables) { cardTitle = it }
                            InsetDivider()
                            VariableInputRow("卡片描述", "本群专属；多个模板用 || 分隔随机选择", cardDesc, groupRenameTemplateVariables, minLines = 3) { cardDesc = it }
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    if (boundTemplate == null) {
                        ActionRow("恢复全局内容", "跟随全局类型并覆盖本群模板") {
                            promptType = GroupRenameMonitorSettings.MODE_GLOBAL
                            bothOrder = globalBothOrder
                            text = globalText
                            cardTitle = globalCardTitle
                            cardDesc = globalCardDesc
                            sp.edit()
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, groupId), GroupRenameMonitorSettings.MODE_GLOBAL)
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_BOTH_ORDER, groupId), globalBothOrder)
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_TEXT, groupId), globalText)
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_TITLE, groupId), globalCardTitle)
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_DESC, groupId), globalCardDesc)
                                .apply()
                        }
                        InsetDivider()
                    }
                    ActionRow("删除当前群", "移除监听和本群专属配置") { onDelete() }
                }
            }
        }
    }
}

private fun deleteGroupRenameConfiguration(
    sp: SharedPreferences,
    groupId: String,
    listenGroups: String,
    bindings: List<GroupRenameTemplateBinding>
) {
    sp.edit()
        .putString(GroupRenameMonitorSettings.KEY_LISTEN_GROUPS, listenGroups)
        .putString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, GroupRenameMonitorSettings.encodeBindings(bindings))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, groupId))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_BOTH_ORDER, groupId))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_TEXT, groupId))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_TITLE, groupId))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_DESC, groupId))
        .apply()
}

private fun groupRenamePromptTypeLabel(value: String): String = when (value) {
    GroupRenameMonitorSettings.PROMPT_CARD -> "卡片"
    GroupRenameMonitorSettings.PROMPT_BOTH -> "文本+卡片"
    else -> "文本"
}

private fun groupRenamePromptTypeChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("文本", GroupRenameMonitorSettings.PROMPT_TEXT),
    PopupChoice("文本+卡片", GroupRenameMonitorSettings.PROMPT_BOTH),
    PopupChoice("卡片", GroupRenameMonitorSettings.PROMPT_CARD)
)

private fun groupRenamePromptTypeWithGlobalLabel(value: String): String {
    return if (value == GroupRenameMonitorSettings.MODE_GLOBAL) "跟随全局" else groupRenamePromptTypeLabel(value)
}

private fun groupRenamePromptTypeWithGlobalChoices(): List<PopupChoice<String>> {
    return listOf(PopupChoice("跟随全局", GroupRenameMonitorSettings.MODE_GLOBAL)) + groupRenamePromptTypeChoices()
}

private fun groupRenameBothOrderLabel(value: String): String {
    return if (value == GroupRenameMonitorSettings.BOTH_CARD_FIRST) "先卡片后文本" else "先文本后卡片"
}

private fun groupRenameBothOrderChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("先文本后卡片", GroupRenameMonitorSettings.BOTH_TEXT_FIRST),
    PopupChoice("先卡片后文本", GroupRenameMonitorSettings.BOTH_CARD_FIRST)
)

private fun newGroupRenameTemplate(index: Int, sp: SharedPreferences): GroupRenameReplyTemplate {
    return GroupRenameReplyTemplate(
        id = "rename_${System.currentTimeMillis()}_$index",
        name = "改名模板 $index",
        enabled = true,
        delaySeconds = sp.getInt(GroupRenameMonitorSettings.KEY_DELAY_SECONDS, GroupRenameMonitorSettings.DEFAULT_DELAY_SECONDS),
        promptType = sp.getString(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE)
            ?: GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE,
        bothOrder = sp.getString(GroupRenameMonitorSettings.KEY_BOTH_ORDER, GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER)
            ?: GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER,
        text = sp.getString(GroupRenameMonitorSettings.KEY_TEXT, GroupRenameMonitorSettings.DEFAULT_TEXT)
            ?: GroupRenameMonitorSettings.DEFAULT_TEXT,
        cardTitle = sp.getString(GroupRenameMonitorSettings.KEY_CARD_TITLE, GroupRenameMonitorSettings.DEFAULT_CARD_TITLE)
            ?: GroupRenameMonitorSettings.DEFAULT_CARD_TITLE,
        cardDesc = sp.getString(GroupRenameMonitorSettings.KEY_CARD_DESC, GroupRenameMonitorSettings.DEFAULT_CARD_DESC)
            ?: GroupRenameMonitorSettings.DEFAULT_CARD_DESC
    )
}

private fun upsertGroupRenameTemplateBindings(
    existing: List<GroupRenameTemplateBinding>,
    additions: List<GroupRenameTemplateBinding>
): List<GroupRenameTemplateBinding> {
    val result = LinkedHashMap<String, GroupRenameTemplateBinding>()
    existing.forEach { binding ->
        if (binding.groupId.isNotBlank() && binding.templateId.isNotBlank()) {
            result[binding.groupId] = binding
        }
    }
    additions.forEach { binding ->
        if (binding.groupId.isNotBlank() && binding.templateId.isNotBlank()) {
            result[binding.groupId] = binding
        }
    }
    return result.values.toList()
}

private fun describeGroupRenameTemplate(template: GroupRenameReplyTemplate): String {
    val type = groupRenamePromptTypeLabel(template.promptType)
    return if (template.enabled) "$type · 延迟 ${template.delaySeconds} 秒" else "已停用"
}

private fun groupRenameBindingSummary(
    bindings: List<GroupRenameTemplateBinding>,
    templates: List<GroupRenameReplyTemplate>
): String {
    val validIds = templates.map { it.id }.toSet()
    val count = bindings.count { it.templateId in validIds }
    return if (count == 0) "暂无群绑定模板" else "$count 个群已绑定模板"
}

private fun groupRenameGroupBindingSummary(
    groupId: String,
    bindings: List<GroupRenameTemplateBinding>,
    templates: List<GroupRenameReplyTemplate>
): String {
    val binding = bindings.firstOrNull { it.groupId == groupId } ?: return groupId
    val template = templates.firstOrNull { it.id == binding.templateId } ?: return "$groupId · 模板已失效"
    return "$groupId · ${template.name.ifBlank { template.id }}"
}

@Composable
internal fun GroupLeaveMonitorMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, GroupLeaveMonitorSettings.PREFS_NAME) }
    var groupPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    var showGroupManager by remember { mutableStateOf(false) }
    var showTemplateManager by remember { mutableStateOf(false) }
    var showBatchTemplateBinding by remember { mutableStateOf(false) }
    var groupEditor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var templateEditor by remember { mutableStateOf<GroupLeaveTemplateEditorRequest?>(null) }
    var replyTemplates by remember {
        mutableStateOf(GroupLeaveMonitorSettings.parseTemplates(sp.getString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATES, "").orEmpty()))
    }
    var replyTemplateBindings by remember {
        mutableStateOf(GroupLeaveMonitorSettings.parseBindings(sp.getString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS, "").orEmpty()))
    }
    var leaveNoticeEnabled by remember {
        mutableStateOf(sp.getBoolean(GroupLeaveMonitorSettings.KEY_ENABLE, GroupLeaveMonitorSettings.DEFAULT_ENABLE))
    }
    var leaveNoticeText by remember {
        mutableStateOf(
            sp.getString(
                GroupLeaveMonitorSettings.KEY_LEAVE_NOTICE_TEXT,
                GroupLeaveMonitorSettings.DEFAULT_LEAVE_NOTICE_TEXT
            ) ?: GroupLeaveMonitorSettings.DEFAULT_LEAVE_NOTICE_TEXT
        )
    }
    var inviteNoticeEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                GroupLeaveMonitorSettings.KEY_INVITE_DETAIL_ENABLE,
                GroupLeaveMonitorSettings.DEFAULT_INVITE_DETAIL_ENABLE
            )
        )
    }
    var inviteNoticeText by remember {
        mutableStateOf(
            sp.getString(
                GroupLeaveMonitorSettings.KEY_INVITE_NOTICE_TEXT,
                GroupLeaveMonitorSettings.DEFAULT_INVITE_NOTICE_TEXT
            ) ?: GroupLeaveMonitorSettings.DEFAULT_INVITE_NOTICE_TEXT
        )
    }
    var noticeScope by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_NOTICE_SCOPE, GroupLeaveMonitorSettings.DEFAULT_NOTICE_SCOPE) ?: GroupLeaveMonitorSettings.DEFAULT_NOTICE_SCOPE)
    }
    var noticeGroups by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_NOTICE_GROUPS, "").orEmpty()) }
    var wxidColor by remember {
        mutableStateOf(
            MemberTitleStore.cleanColor(
                sp.getString(
                    GroupLeaveMonitorSettings.KEY_WXID_COLOR,
                    GroupLeaveMonitorSettings.DEFAULT_WXID_COLOR
                )?.substringBefore(',')
            ).ifEmpty { GroupLeaveMonitorSettings.DEFAULT_WXID_COLOR }
        )
    }
    var listenGroups by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LISTEN_GROUPS, "").orEmpty())
    }
    var promptType by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, GroupLeaveMonitorSettings.DEFAULT_PROMPT_TYPE) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_TYPE)
    }
    var bothOrder by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER)
    }
    var delaySeconds by remember {
        mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_DELAY_SECONDS, GroupLeaveMonitorSettings.DEFAULT_DELAY_SECONDS).toString())
    }
    var joinText by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_TEXT, GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT)
    }
    var leftText by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_TEXT, GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT)
    }
    var joinCardTitle by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE)
    }
    var joinCardDesc by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC)
    }
    var leftCardTitle by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE)
    }
    var leftCardDesc by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC)
    }
    var mediaOrder by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER)
    }
    var mediaSequence by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE)
    }
    var joinImages by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, "").orEmpty()) }
    var leftImages by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, "").orEmpty()) }
    var joinVoices by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, "").orEmpty()) }
    var leftVoices by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, "").orEmpty()) }
    var joinEmojis by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, "").orEmpty()) }
    var leftEmojis by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, "").orEmpty()) }
    var joinVideos by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, "").orEmpty()) }
    var leftVideos by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, "").orEmpty()) }
    var joinFiles by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, "").orEmpty()) }
    var leftFiles by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, "").orEmpty()) }
    var joinFavorites by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, "").orEmpty()) }
    var leftFavorites by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, "").orEmpty()) }
    var promptDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS).toString()) }
    var imageDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var voiceDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var emojiDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var videoDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var fileDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var favoriteDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    val listState = rememberLazyListState()
    val groupEditorListStates = remember { mutableMapOf<String, LazyListState>() }
    val scrollBehavior = MiuixScrollBehavior()

    val route = when {
        templateEditor != null -> GroupLeaveRoute.TemplateEditor(templateEditor!!)
        groupEditor != null -> GroupLeaveRoute.GroupEditor(groupEditor!!.first, groupEditor!!.second)
        showBatchTemplateBinding -> GroupLeaveRoute.BatchTemplateBinding
        showTemplateManager -> GroupLeaveRoute.TemplateManager
        favoritePicker != null -> GroupLeaveRoute.FavoritePicker(favoritePicker!!)
        groupPicker != null -> GroupLeaveRoute.ContactPicker(groupPicker!!)
        showGroupManager -> GroupLeaveRoute.GroupManager
        else -> GroupLeaveRoute.Main
    }

    SettingsRouteTransition(
        targetState = route,
        label = "GroupLeaveRouteTransition",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is GroupLeaveRoute.ContactPicker -> {
                val request = currentRoute.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { groupPicker = null },
                    onConfirm = { selected ->
                        request.onValue(formatIds(selected.map { it.id }))
                        groupPicker = null
                    }
                )
            }
            is GroupLeaveRoute.FavoritePicker -> {
                FavoritePickerPage(
                    request = currentRoute.request,
                    onBack = { favoritePicker = null }
                )
            }
            GroupLeaveRoute.GroupManager -> {
                GroupMemberGroupManagerPage(
                    context = context,
                    listenGroups = listenGroups,
                    templates = replyTemplates,
                    templateBindings = replyTemplateBindings,
                    onBack = { showGroupManager = false },
                    onOpenTemplates = { showTemplateManager = true },
                    onBatchApplyTemplate = { showBatchTemplateBinding = true },
                    onDeleteGroups = { targets ->
                        val targetIds = targets.mapTo(HashSet()) { it.id }
                        val nextListenGroups = formatIds(parseIds(listenGroups) - targetIds)
                        val nextBindings = replyTemplateBindings.filterNot { it.groupId in targetIds }
                        targetIds.forEach { groupId ->
                            deleteGroupMemberConfiguration(sp, groupId, nextListenGroups, nextBindings)
                            groupEditorListStates.remove(groupId)
                        }
                        listenGroups = nextListenGroups
                        replyTemplateBindings = nextBindings
                        Toast.makeText(context, "已删除 ${targets.size} 个监听群", Toast.LENGTH_SHORT).show()
                    },
                    onPickGroups = {
                        groupPicker = ContactPickerRequest(
                            title = "选择监听群",
                            mode = ContactPickerMode.GROUPS,
                            multiSelect = true,
                            existingValue = listenGroups,
                            onValue = { next ->
                                listenGroups = next
                                sp.edit().putString(GroupLeaveMonitorSettings.KEY_LISTEN_GROUPS, next).apply()
                            }
                        )
                    },
                    onOpenGroup = { id, label -> groupEditor = id to label }
                )
            }
            is GroupLeaveRoute.GroupEditor -> {
                GroupMemberGroupEditorPage(
                    context = context,
                    sp = sp,
                    groupId = currentRoute.groupId,
                    groupLabel = currentRoute.label,
                    templates = replyTemplates,
                    templateBindings = replyTemplateBindings,
                    listState = groupEditorListStates.getOrPut(currentRoute.groupId) { LazyListState() },
                    onTemplateBindingsChanged = { next ->
                        replyTemplateBindings = next
                        sp.edit()
                            .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS, GroupLeaveMonitorSettings.encodeBindings(next))
                            .apply()
                    },
                    onDelete = {
                        val nextListenGroups = formatIds(parseIds(listenGroups) - currentRoute.groupId)
                        val nextBindings = replyTemplateBindings.filterNot { it.groupId == currentRoute.groupId }
                        deleteGroupMemberConfiguration(
                            sp,
                            currentRoute.groupId,
                            nextListenGroups,
                            nextBindings
                        )
                        listenGroups = nextListenGroups
                        replyTemplateBindings = nextBindings
                        groupEditorListStates.remove(currentRoute.groupId)
                        Toast.makeText(context, "群配置已删除", Toast.LENGTH_SHORT).show()
                        groupEditor = null
                    },
                    onBack = { groupEditor = null }
                )
            }
            GroupLeaveRoute.TemplateManager -> {
                GroupLeaveTemplateListPage(
                    templates = replyTemplates,
                    bindings = replyTemplateBindings,
                    onBack = { showTemplateManager = false },
                    onOpenTemplate = { index, template ->
                        templateEditor = GroupLeaveTemplateEditorRequest(index, template, canDelete = true)
                    },
                    onAddTemplate = {
                        templateEditor = GroupLeaveTemplateEditorRequest(
                            replyTemplates.size,
                            newGroupLeaveTemplate(replyTemplates.size + 1, sp),
                            canDelete = false
                        )
                    }
                )
            }
            GroupLeaveRoute.BatchTemplateBinding -> {
                GroupLeaveBatchTemplateBindingPage(
                    context = context,
                    listenGroups = listenGroups,
                    templates = replyTemplates,
                    bindings = replyTemplateBindings,
                    onBack = { showBatchTemplateBinding = false },
                    onSave = { additions ->
                        val next = upsertGroupLeaveTemplateBindings(replyTemplateBindings, additions)
                        replyTemplateBindings = next
                        sp.edit()
                            .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS, GroupLeaveMonitorSettings.encodeBindings(next))
                            .apply()
                        Toast.makeText(context, "模板已套用到 ${additions.size} 个群", Toast.LENGTH_SHORT).show()
                        showBatchTemplateBinding = false
                    }
                )
            }
            is GroupLeaveRoute.TemplateEditor -> {
                val request = currentRoute.request
                GroupLeaveTemplateEditorPage(
                    context = context,
                    request = request,
                    onBack = { templateEditor = null },
                    onSave = { updated ->
                        val next = if (request.index in replyTemplates.indices) {
                            replyTemplates.toMutableList().also { it[request.index] = updated }
                        } else {
                            replyTemplates + updated
                        }
                        replyTemplates = next
                        sp.edit()
                            .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATES, GroupLeaveMonitorSettings.encodeTemplates(next))
                            .apply()
                        Toast.makeText(context, "模板已保存", Toast.LENGTH_SHORT).show()
                        templateEditor = null
                    },
                    onDelete = {
                        if (request.index in replyTemplates.indices) {
                            val deletedId = replyTemplates[request.index].id
                            val nextTemplates = replyTemplates.toMutableList().also { it.removeAt(request.index) }
                            val nextBindings = replyTemplateBindings.filterNot { it.templateId == deletedId }
                            replyTemplates = nextTemplates
                            replyTemplateBindings = nextBindings
                            sp.edit()
                                .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATES, GroupLeaveMonitorSettings.encodeTemplates(nextTemplates))
                                .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS, GroupLeaveMonitorSettings.encodeBindings(nextBindings))
                                .apply()
                            Toast.makeText(context, "模板已删除", Toast.LENGTH_SHORT).show()
                        }
                        templateEditor = null
                    }
                )
            }
            GroupLeaveRoute.Main -> PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = {
                    val cleaned = MemberTitleStore.cleanColor(wxidColor.substringBefore(','))
                        .ifEmpty { GroupLeaveMonitorSettings.DEFAULT_WXID_COLOR }
                    wxidColor = cleaned
                    sp.edit()
                        .putBoolean(GroupLeaveMonitorSettings.KEY_ENABLE, leaveNoticeEnabled)
                        .putString(GroupLeaveMonitorSettings.KEY_LEAVE_NOTICE_TEXT, leaveNoticeText)
                        .putBoolean(GroupLeaveMonitorSettings.KEY_INVITE_DETAIL_ENABLE, inviteNoticeEnabled)
                        .putString(GroupLeaveMonitorSettings.KEY_INVITE_NOTICE_TEXT, inviteNoticeText)
                        .putString(GroupLeaveMonitorSettings.KEY_NOTICE_SCOPE, noticeScope)
                        .putString(GroupLeaveMonitorSettings.KEY_NOTICE_GROUPS, noticeGroups)
                        .putString(GroupLeaveMonitorSettings.KEY_WXID_COLOR, cleaned)
                        .putString(GroupLeaveMonitorSettings.KEY_LISTEN_GROUPS, listenGroups)
                        .putInt(GroupLeaveMonitorSettings.KEY_DELAY_SECONDS, delaySeconds.toIntOrNull()?.coerceIn(0, 600) ?: GroupLeaveMonitorSettings.DEFAULT_DELAY_SECONDS)
                        .putString(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, promptType)
                        .putString(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, bothOrder)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_TEXT, joinText)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_TEXT, leftText)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, joinCardTitle)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, joinCardDesc)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, leftCardTitle)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, leftCardDesc)
                        .putString(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, mediaOrder)
                        .putString(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, mediaSequence)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, joinImages)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, leftImages)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, joinVoices)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, leftVoices)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, joinEmojis)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, leftEmojis)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, joinVideos)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, leftVideos)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, joinFiles)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, leftFiles)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, joinFavorites)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, leftFavorites)
                        .putInt(GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, promptDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, imageDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, voiceDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, emojiDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, videoDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, fileDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, favoriteDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
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
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "系统消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        leaveNoticeEnabled,
                        "退群系统消息",
                        "保留现有退群监控逻辑，检测到成员退出后插入可点击资料页的系统消息",
                        onCheckedChange = {
                            leaveNoticeEnabled = it
                            sp.edit().putBoolean(GroupLeaveMonitorSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    if (leaveNoticeEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            "退群系统消息模板",
                            "wxid变量会生成可点击资料链接",
                            leaveNoticeText,
                            groupLeaveNoticeVariables,
                            minLines = 3
                        ) { leaveNoticeText = it }
                    }
                    InsetDivider()
                    SwitchRow(
                        inviteNoticeEnabled,
                        "邀请详情",
                        "检测到邀请进群后插入邀请者、被邀请者和累计邀请次数",
                        onCheckedChange = {
                            inviteNoticeEnabled = it
                            sp.edit().putBoolean(GroupLeaveMonitorSettings.KEY_INVITE_DETAIL_ENABLE, it).apply()
                        }
                    )
                    if (inviteNoticeEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            "邀请详情系统消息模板",
                            "邀请者和被邀请者wxid会生成可点击资料链接",
                            inviteNoticeText,
                            groupInviteNoticeVariables,
                            minLines = 4
                        ) { inviteNoticeText = it }
                    }
                    if (leaveNoticeEnabled || inviteNoticeEnabled) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "适用群聊",
                            summary = systemNoticeScopeLabel(noticeScope, GroupLeaveMonitorSettings.NOTICE_SCOPE_SPECIFIC),
                            options = systemNoticeScopeChoices(
                                GroupLeaveMonitorSettings.NOTICE_SCOPE_ALL,
                                GroupLeaveMonitorSettings.NOTICE_SCOPE_SPECIFIC
                            ),
                            currentValue = noticeScope,
                            onValueChanged = { noticeScope = it }
                        )
                        if (noticeScope == GroupLeaveMonitorSettings.NOTICE_SCOPE_SPECIFIC) {
                            InsetDivider()
                            ActionRow("选择指定群聊", systemNoticeGroupSummary(noticeGroups)) {
                                groupPicker = ContactPickerRequest(
                                    title = "选择系统消息群聊",
                                    mode = ContactPickerMode.GROUPS,
                                    multiSelect = true,
                                    existingValue = noticeGroups,
                                    onValue = { noticeGroups = it }
                                )
                            }
                        }
                    }
                    InsetDivider()
                    ColorPickerRow(
                        "wxid颜色",
                        "退群和邀请详情里的 wxid 链接颜色",
                        wxidColor,
                        allowGradient = false,
                        onReset = { wxidColor = GroupLeaveMonitorSettings.DEFAULT_WXID_COLOR }
                    ) {
                        wxidColor = it.take(9)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "进退群回复") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        GroupLeaveMonitorSettings.KEY_REPLY_ENABLE,
                        "启用自动回复",
                        "只对下方监听群生效，默认关闭",
                        GroupLeaveMonitorSettings.DEFAULT_REPLY_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        GroupLeaveMonitorSettings.KEY_JOIN_REPLY_ENABLE,
                        "进群回复",
                        "检测到新成员进群后发送欢迎内容",
                        GroupLeaveMonitorSettings.DEFAULT_JOIN_REPLY_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        GroupLeaveMonitorSettings.KEY_LEFT_REPLY_ENABLE,
                        "退群回复",
                        "检测到成员退群后发送退群内容",
                        GroupLeaveMonitorSettings.DEFAULT_LEFT_REPLY_ENABLE
                    )
                    InsetDivider()
                    ActionRow("监听群与专属设置", groupSelectionSummary(listenGroups)) {
                        showGroupManager = true
                    }
                    InsetDivider()
                    ActionRow(
                        "回复模板管理",
                        if (replyTemplates.isEmpty()) "暂无模板，进入后添加批量配置" else "${replyTemplates.size} 个模板，进入后修改或删除"
                    ) {
                        showTemplateManager = true
                    }
                    InsetDivider()
                    ActionRow(
                        "批量套用模板",
                        if (replyTemplates.isEmpty()) "先新增模板，再批量绑定监听群" else groupLeaveTemplateBindingSummary(replyTemplateBindings, replyTemplates)
                    ) {
                        showBatchTemplateBinding = true
                    }
                    InsetDivider()
                    NumberInputRow("整体延迟", "单位秒，0-600", delaySeconds) { delaySeconds = it }
                    InsetDivider()
                    PopupChoiceRow(
                        title = "提示类型",
                        summary = groupPromptTypeLabel(promptType),
                        options = groupPromptTypeChoices(),
                        currentValue = promptType,
                        onValueChanged = {
                            promptType = it
                        }
                    )
                    if (promptType == GroupLeaveMonitorSettings.PROMPT_BOTH) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "文本+卡片顺序",
                            summary = groupBothOrderLabel(bothOrder),
                            options = groupBothOrderChoices(),
                            currentValue = bothOrder,
                            onValueChanged = {
                                bothOrder = it
                            }
                        )
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本模板") }
            item {
                SettingsCard {
                    VariableInputRow(
                        title = "进群文本",
                        summary = "多个模板用 || 分隔随机选择",
                        value = joinText,
                        variables = groupMemberTemplateVariables,
                        minLines = 4,
                        onValueChange = { joinText = it }
                    )
                    InsetDivider()
                    VariableInputRow(
                        title = "退群文本",
                        summary = "多个模板用 || 分隔随机选择",
                        value = leftText,
                        variables = groupMemberTemplateVariables,
                        minLines = 4,
                        onValueChange = { leftText = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "卡片模板") }
            item {
                SettingsCard {
                    VariableInputRow("进群卡片标题", "支持变量", joinCardTitle, groupMemberTemplateVariables) { joinCardTitle = it }
                    InsetDivider()
                    VariableInputRow("进群卡片描述", "支持变量", joinCardDesc, groupMemberTemplateVariables, minLines = 3) { joinCardDesc = it }
                    InsetDivider()
                    VariableInputRow("退群卡片标题", "支持变量", leftCardTitle, groupMemberTemplateVariables) { leftCardTitle = it }
                    InsetDivider()
                    VariableInputRow("退群卡片描述", "支持变量", leftCardDesc, groupMemberTemplateVariables, minLines = 3) { leftCardDesc = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "媒体") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "媒体顺序",
                        summary = groupMediaOrderLabel(mediaOrder),
                        options = groupMediaOrderChoices(),
                        currentValue = mediaOrder,
                        onValueChanged = {
                            mediaOrder = it
                        }
                    )
                    InsetDivider()
                    InputRow("媒体类型顺序", "英文逗号分隔：image,voice,emoji,video,file,favorite", mediaSequence) { mediaSequence = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群图片", joinImages, RedPacketRuleConfig.REPLY_IMAGE) { joinImages = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群图片", leftImages, RedPacketRuleConfig.REPLY_IMAGE) { leftImages = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群语音", joinVoices, RedPacketRuleConfig.REPLY_VOICE) { joinVoices = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群语音", leftVoices, RedPacketRuleConfig.REPLY_VOICE) { leftVoices = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群表情", joinEmojis, RedPacketRuleConfig.REPLY_EMOJI) { joinEmojis = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群表情", leftEmojis, RedPacketRuleConfig.REPLY_EMOJI) { leftEmojis = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群视频", joinVideos, RedPacketRuleConfig.REPLY_VIDEO) { joinVideos = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群视频", leftVideos, RedPacketRuleConfig.REPLY_VIDEO) { leftVideos = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群文件", joinFiles, RedPacketRuleConfig.REPLY_FILE) { joinFiles = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群文件", leftFiles, RedPacketRuleConfig.REPLY_FILE) { leftFiles = it }
                    InsetDivider()
                    GroupMemberMediaRow(
                        context,
                        "进群收藏",
                        joinFavorites,
                        RedPacketRuleConfig.REPLY_FAVORITE,
                        onValueChange = { joinFavorites = it },
                        onFavoritePicker = { favoritePicker = it }
                    )
                    InsetDivider()
                    GroupMemberMediaRow(
                        context,
                        "退群收藏",
                        leftFavorites,
                        RedPacketRuleConfig.REPLY_FAVORITE,
                        onValueChange = { leftFavorites = it },
                        onFavoritePicker = { favoritePicker = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "精细延迟") }
            item {
                SettingsCard {
                    NumberInputRow("提示延迟", "单位 ms", promptDelay) { promptDelay = it }
                    InsetDivider()
                    NumberInputRow("图片延迟", "单位 ms", imageDelay) { imageDelay = it }
                    InsetDivider()
                    NumberInputRow("语音延迟", "单位 ms", voiceDelay) { voiceDelay = it }
                    InsetDivider()
                    NumberInputRow("表情延迟", "单位 ms", emojiDelay) { emojiDelay = it }
                    InsetDivider()
                    NumberInputRow("视频延迟", "单位 ms", videoDelay) { videoDelay = it }
                    InsetDivider()
                    NumberInputRow("文件延迟", "单位 ms", fileDelay) { fileDelay = it }
                    InsetDivider()
                    NumberInputRow("收藏延迟", "单位 ms", favoriteDelay) { favoriteDelay = it }
                }
            }
        }
    }
        }
    }
}

@Composable
private fun GroupMemberGroupManagerPage(
    context: Context,
    listenGroups: String,
    templates: List<GroupLeaveReplyTemplate>,
    templateBindings: List<GroupLeaveReplyTemplateBinding>,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onBatchApplyTemplate: () -> Unit,
    onDeleteGroups: (List<ContactOption>) -> Unit,
    onPickGroups: () -> Unit,
    onOpenGroup: (String, String) -> Unit
) {
    var loading by remember(listenGroups) { mutableStateOf(true) }
    var error by remember(listenGroups) { mutableStateOf("") }
    var groups by remember(listenGroups) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember { mutableStateOf("") }
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedForDelete by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val selectedIds = remember(listenGroups) { parseIds(listenGroups).toList() }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(listenGroups) {
        loadContacts(context, ContactPickerMode.GROUPS) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取群聊失败"
            } else {
                val byId = result.associateBy { it.id }
                groups = selectedIds.map { id ->
                    byId[id] ?: ContactOption(id, id, group = true, avatarUrl = "", avatarBackupUrl = "")
                }
            }
        }
    }

    val lower = query.trim().lowercase(Locale.US)
    val visible = groups.filter { it.matchesSearch(lower) }
    val visibleIds = visible.mapTo(LinkedHashSet()) { it.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedForDelete }
    val selectedGroups = groups.filter { it.id in selectedForDelete }
    PageScaffold(
        title = "监听群",
        largeTitle = "监听群",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedGroups.size}）",
                    onPrimaryClick = {
                        if (selectedGroups.isEmpty()) {
                            Toast.makeText(context, "请先选择监听群", Toast.LENGTH_SHORT).show()
                        } else {
                            showDeleteConfirm = true
                        }
                    },
                    secondaryText = "取消",
                    onSecondaryClick = {
                        batchDeleteMode = false
                        selectedForDelete = emptySet()
                    },
                    middleText = if (visibleIds.isEmpty()) null else if (allVisibleSelected) "取消全选" else "全选",
                    onMiddleClick = if (visibleIds.isEmpty()) null else {
                        {
                            selectedForDelete = if (allVisibleSelected) {
                                selectedForDelete - visibleIds
                            } else {
                                selectedForDelete + visibleIds
                            }
                        }
                    }
                )
            } else {
                BottomActionBar(
                    primaryText = "选择监听群",
                    onPrimaryClick = onPickGroups,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
                    middleText = if (selectedIds.isEmpty()) null else "批量删除",
                    onMiddleClick = if (selectedIds.isEmpty()) null else {
                        {
                            batchDeleteMode = true
                            selectedForDelete = emptySet()
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
                    InputRow("搜索监听群", "群名称 / 群聊备注 / 群号", query) { query = it }
                }
            }
            if (!batchDeleteMode) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
                item {
                    SettingsCard {
                        ActionRow(
                            "回复模板管理",
                            if (templates.isEmpty()) "暂无模板，进入后添加" else "${templates.size} 个模板"
                        ) {
                            onOpenTemplates()
                        }
                        InsetDivider()
                        ActionRow(
                            "批量套用模板",
                            if (templates.isEmpty()) "先新增模板，再批量绑定监听群" else groupLeaveTemplateBindingSummary(templateBindings, templates)
                        ) {
                            onBatchApplyTemplate()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "已监听群 · ${selectedIds.size} 项") }
            item {
                SettingsCard {
                    when {
                        loading -> EmptyText("正在载入监听群...")
                        error.isNotEmpty() -> EmptyText(error)
                        selectedIds.isEmpty() -> EmptyText("暂无监听群。点击底部“选择监听群”添加。")
                        visible.isEmpty() -> EmptyText("没有匹配结果")
                        else -> visible.forEachIndexed { index, option ->
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = option.label.ifBlank { option.id },
                                        value = index,
                                        summary = groupLeaveGroupBindingSummary(option.id, option.id, templateBindings, templates)
                                    ),
                                    selected = option.id in selectedForDelete,
                                    onClick = {
                                        selectedForDelete = if (option.id in selectedForDelete) {
                                            selectedForDelete - option.id
                                        } else {
                                            selectedForDelete + option.id
                                        }
                                    }
                                )
                            } else {
                                SelectRow(
                                    title = option.label.ifBlank { option.id },
                                    summary = groupLeaveGroupBindingSummary(option.id, option.id, templateBindings, templates),
                                    onClick = { onOpenGroup(option.id, option.label.ifBlank { option.id }) }
                                )
                            }
                            if (index < visible.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedGroups.size} 个进退群监听群及其专属配置，此操作不可撤销。",
        labels = selectedGroups.map { it.label.ifBlank { it.id } },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selectedGroups
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedForDelete = emptySet()
            onDeleteGroups(targets)
        }
    )
}

@Composable
private fun GroupLeaveTemplateListPage(
    templates: List<GroupLeaveReplyTemplate>,
    bindings: List<GroupLeaveReplyTemplateBinding>,
    onBack: () -> Unit,
    onOpenTemplate: (Int, GroupLeaveReplyTemplate) -> Unit,
    onAddTemplate: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "回复模板",
        largeTitle = "回复模板",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "新增模板",
                onPrimaryClick = onAddTemplate,
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
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。模板保存一整套进退群回复配置，再批量套用到监听群。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            val count = bindings.count { it.templateId == template.id }
                            SelectRow(
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = "${describeGroupLeaveTemplate(template)} · 已绑定 $count 个群",
                                onClick = { onOpenTemplate(index, template) }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLeaveBatchTemplateBindingPage(
    context: Context,
    listenGroups: String,
    templates: List<GroupLeaveReplyTemplate>,
    bindings: List<GroupLeaveReplyTemplateBinding>,
    onBack: () -> Unit,
    onSave: (List<GroupLeaveReplyTemplateBinding>) -> Unit
) {
    var loading by remember(listenGroups) { mutableStateOf(true) }
    var error by remember(listenGroups) { mutableStateOf("") }
    var groups by remember(listenGroups) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember { mutableStateOf("") }
    var selectedTemplateId by remember(templates) { mutableStateOf(templates.firstOrNull()?.id.orEmpty()) }
    var selectedGroups by remember(listenGroups) { mutableStateOf(parseIds(listenGroups)) }
    val selectedIds = remember(listenGroups) { parseIds(listenGroups).toList() }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(listenGroups) {
        loadContacts(context, ContactPickerMode.GROUPS) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取群聊失败"
            } else {
                val byId = result.associateBy { it.id }
                groups = selectedIds.map { id ->
                    byId[id] ?: ContactOption(id, id, group = true, avatarUrl = "", avatarBackupUrl = "")
                }
            }
        }
    }

    val templateOptions = templates.map { template ->
        PopupChoice(template.name.ifBlank { template.id }, template.id)
    }
    val lower = query.trim().lowercase(Locale.US)
    val visible = groups.filter { it.matchesSearch(lower) }
    val visibleIds = visible.map { it.id }.toSet()
    val visibleAllSelected = visibleIds.isNotEmpty() && visibleIds.all { selectedGroups.contains(it) }
    PageScaffold(
        title = "批量套用模板",
        largeTitle = "批量套用模板",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "套用模板",
                onPrimaryClick = {
                    if (templates.isEmpty()) {
                        Toast.makeText(context, "请先新增模板", Toast.LENGTH_SHORT).show()
                    } else if (selectedTemplateId.isBlank()) {
                        Toast.makeText(context, "请先选择模板", Toast.LENGTH_SHORT).show()
                    } else if (selectedGroups.isEmpty()) {
                        Toast.makeText(context, "请先选择群", Toast.LENGTH_SHORT).show()
                    } else {
                        val labels = groups.associate { it.id to it.label.ifBlank { it.id } }
                        onSave(
                            selectedGroups.map { groupId ->
                                GroupLeaveReplyTemplateBinding(
                                    groupId = groupId,
                                    label = labels[groupId] ?: bindings.firstOrNull { it.groupId == groupId }?.label ?: groupId,
                                    templateId = selectedTemplateId
                                )
                            }
                        )
                    }
                },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (visibleIds.isNotEmpty()) {
                    if (visibleAllSelected) "取消全选" else "全选"
                } else {
                    null
                },
                onMiddleClick = if (visibleIds.isNotEmpty()) {
                    {
                        selectedGroups = if (visibleAllSelected) {
                            selectedGroups - visibleIds
                        } else {
                            selectedGroups + visibleIds
                        }
                    }
                } else {
                    null
                }
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
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。先进入“回复模板管理”新增模板。")
                    } else {
                        PopupChoiceRow(
                            title = "选择模板",
                            summary = templates.firstOrNull { it.id == selectedTemplateId }?.let { describeGroupLeaveTemplate(it) } ?: "未选择",
                            options = templateOptions,
                            currentValue = selectedTemplateId,
                            onValueChanged = { selectedTemplateId = it }
                        )
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "监听群") }
            item {
                SettingsCard {
                    InputRow("搜索监听群", "群名称 / 群聊备注 / 群号", query) { query = it }
                }
            }
            item {
                SettingsCard {
                    when {
                        loading -> EmptyText("正在载入监听群...")
                        error.isNotEmpty() -> EmptyText(error)
                        selectedIds.isEmpty() -> EmptyText("暂无监听群。先选择需要监听的群。")
                        visible.isEmpty() -> EmptyText("没有匹配结果")
                        else -> visible.forEachIndexed { index, option ->
                            SwitchRow(
                                checked = selectedGroups.contains(option.id),
                                title = option.label.ifBlank { option.id },
                                summary = groupLeaveGroupBindingSummary(option.id, option.id, bindings, templates),
                                onCheckedChange = { checked ->
                                    selectedGroups = if (checked) selectedGroups + option.id else selectedGroups - option.id
                                }
                            )
                            if (index < visible.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLeaveTemplateEditorPage(
    context: Context,
    request: GroupLeaveTemplateEditorRequest,
    onBack: () -> Unit,
    onSave: (GroupLeaveReplyTemplate) -> Unit,
    onDelete: () -> Unit
) {
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    var name by remember(request) { mutableStateOf(request.template.name) }
    var enabled by remember(request) { mutableStateOf(request.template.enabled) }
    var joinEnabled by remember(request) { mutableStateOf(request.template.joinEnabled) }
    var leftEnabled by remember(request) { mutableStateOf(request.template.leftEnabled) }
    var promptType by remember(request) { mutableStateOf(request.template.promptType) }
    var bothOrder by remember(request) { mutableStateOf(request.template.bothOrder) }
    var joinText by remember(request) { mutableStateOf(request.template.joinText) }
    var leftText by remember(request) { mutableStateOf(request.template.leftText) }
    var joinCardTitle by remember(request) { mutableStateOf(request.template.joinCardTitle) }
    var joinCardDesc by remember(request) { mutableStateOf(request.template.joinCardDesc) }
    var leftCardTitle by remember(request) { mutableStateOf(request.template.leftCardTitle) }
    var leftCardDesc by remember(request) { mutableStateOf(request.template.leftCardDesc) }
    var mediaMode by remember(request) { mutableStateOf(request.template.mediaMode) }
    var mediaOrder by remember(request) { mutableStateOf(request.template.mediaOrder) }
    var mediaSequence by remember(request) { mutableStateOf(request.template.mediaSequence) }
    var joinImages by remember(request) { mutableStateOf(request.template.joinImages) }
    var leftImages by remember(request) { mutableStateOf(request.template.leftImages) }
    var joinVoices by remember(request) { mutableStateOf(request.template.joinVoices) }
    var leftVoices by remember(request) { mutableStateOf(request.template.leftVoices) }
    var joinEmojis by remember(request) { mutableStateOf(request.template.joinEmojis) }
    var leftEmojis by remember(request) { mutableStateOf(request.template.leftEmojis) }
    var joinVideos by remember(request) { mutableStateOf(request.template.joinVideos) }
    var leftVideos by remember(request) { mutableStateOf(request.template.leftVideos) }
    var joinFiles by remember(request) { mutableStateOf(request.template.joinFiles) }
    var leftFiles by remember(request) { mutableStateOf(request.template.leftFiles) }
    var joinFavorites by remember(request) { mutableStateOf(request.template.joinFavorites) }
    var leftFavorites by remember(request) { mutableStateOf(request.template.leftFavorites) }
    var delayMode by remember(request) { mutableStateOf(request.template.delayMode) }
    var promptDelay by remember(request) { mutableStateOf(request.template.promptDelayMs.coerceAtLeast(0).toString()) }
    var imageDelay by remember(request) { mutableStateOf(request.template.imageDelayMs.coerceAtLeast(0).toString()) }
    var voiceDelay by remember(request) { mutableStateOf(request.template.voiceDelayMs.coerceAtLeast(0).toString()) }
    var emojiDelay by remember(request) { mutableStateOf(request.template.emojiDelayMs.coerceAtLeast(0).toString()) }
    var videoDelay by remember(request) { mutableStateOf(request.template.videoDelayMs.coerceAtLeast(0).toString()) }
    var fileDelay by remember(request) { mutableStateOf(request.template.fileDelayMs.coerceAtLeast(0).toString()) }
    var favoriteDelay by remember(request) { mutableStateOf(request.template.favoriteDelayMs.coerceAtLeast(0).toString()) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    SettingsRouteTransition(
        targetState = favoritePicker,
        label = "GroupLeaveTemplateFavoriteRoute",
        depthOf = { if (it == null) 0 else 1 }
    ) { currentFavoritePicker ->
        if (currentFavoritePicker != null) {
            FavoritePickerPage(
                request = currentFavoritePicker,
                onBack = { favoritePicker = null }
            )
        } else PageScaffold(
            title = name.ifBlank { "回复模板" },
            largeTitle = name.ifBlank { "回复模板" },
            scrollBehavior = scrollBehavior,
            bottomBar = {
                BottomActionBar(
                    primaryText = "保存模板",
                    onPrimaryClick = {
                        onSave(
                            GroupLeaveReplyTemplate(
                                id = request.template.id,
                                name = name.trim().ifBlank { "模板 ${request.index + 1}" },
                                enabled = enabled,
                                joinEnabled = joinEnabled,
                                leftEnabled = leftEnabled,
                                promptType = GroupLeaveMonitorSettings.normalizePromptType(promptType),
                                bothOrder = GroupLeaveMonitorSettings.normalizeBothOrder(bothOrder),
                                joinText = joinText,
                                leftText = leftText,
                                joinCardTitle = joinCardTitle,
                                joinCardDesc = joinCardDesc,
                                leftCardTitle = leftCardTitle,
                                leftCardDesc = leftCardDesc,
                                mediaMode = GroupLeaveMonitorSettings.normalizeMediaMode(mediaMode),
                                mediaOrder = GroupLeaveMonitorSettings.normalizeMediaOrder(mediaOrder),
                                mediaSequence = mediaSequence,
                                joinImages = joinImages,
                                leftImages = leftImages,
                                joinVoices = joinVoices,
                                leftVoices = leftVoices,
                                joinEmojis = joinEmojis,
                                leftEmojis = leftEmojis,
                                joinVideos = joinVideos,
                                leftVideos = leftVideos,
                                joinFiles = joinFiles,
                                leftFiles = leftFiles,
                                joinFavorites = joinFavorites,
                                leftFavorites = leftFavorites,
                                delayMode = GroupLeaveMonitorSettings.normalizeMode(delayMode),
                                promptDelayMs = promptDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS,
                                imageDelayMs = imageDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                voiceDelayMs = voiceDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                emojiDelayMs = emojiDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                videoDelayMs = videoDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                fileDelayMs = fileDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                favoriteDelayMs = favoriteDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS
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
                item { SmallTitle(text = "模板") }
                item {
                    SettingsCard {
                        InputRow("模板名称", "用于区分不同群回复配置", name) { name = it }
                        InsetDivider()
                        SwitchRow(enabled, "启用模板", "关闭后绑定此模板的群不会按模板回复", onCheckedChange = { enabled = it })
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "事件") }
                item {
                    SettingsCard {
                        SwitchRow(joinEnabled, "进群回复", "绑定群检测到成员进群时发送", onCheckedChange = { joinEnabled = it })
                        InsetDivider()
                        SwitchRow(leftEnabled, "退群回复", "绑定群检测到成员退群时发送", onCheckedChange = { leftEnabled = it })
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提示模板") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "提示设置",
                            summary = groupPromptTypeWithGlobalLabel(promptType),
                            options = groupPromptTypeWithGlobalChoices(),
                            currentValue = promptType,
                            onValueChanged = { promptType = it }
                        )
                        if (promptType != GroupLeaveMonitorSettings.MODE_GLOBAL) {
                            if (promptType == GroupLeaveMonitorSettings.PROMPT_BOTH) {
                                InsetDivider()
                                PopupChoiceRow(
                                    title = "文本+卡片顺序",
                                    summary = groupBothOrderLabel(bothOrder),
                                    options = groupBothOrderChoices(),
                                    currentValue = bothOrder,
                                    onValueChanged = { bothOrder = it }
                                )
                            }
                            InsetDivider()
                            VariableInputRow("进群文本", "多个模板用 || 分隔随机选择", joinText, groupMemberTemplateVariables, minLines = 4) { joinText = it }
                            InsetDivider()
                            VariableInputRow("退群文本", "多个模板用 || 分隔随机选择", leftText, groupMemberTemplateVariables, minLines = 4) { leftText = it }
                            InsetDivider()
                            VariableInputRow("进群卡片标题", "支持变量", joinCardTitle, groupMemberTemplateVariables) { joinCardTitle = it }
                            InsetDivider()
                            VariableInputRow("进群卡片描述", "支持变量", joinCardDesc, groupMemberTemplateVariables, minLines = 3) { joinCardDesc = it }
                            InsetDivider()
                            VariableInputRow("退群卡片标题", "支持变量", leftCardTitle, groupMemberTemplateVariables) { leftCardTitle = it }
                            InsetDivider()
                            VariableInputRow("退群卡片描述", "支持变量", leftCardDesc, groupMemberTemplateVariables, minLines = 3) { leftCardDesc = it }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "媒体") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "媒体设置",
                            summary = groupMediaModeLabel(mediaMode),
                            options = groupMediaModeChoices(),
                            currentValue = mediaMode,
                            onValueChanged = { mediaMode = it }
                        )
                        if (mediaMode == GroupLeaveMonitorSettings.MODE_CUSTOM) {
                            InsetDivider()
                            PopupChoiceRow(
                                title = "媒体顺序",
                                summary = groupMediaOrderLabel(mediaOrder),
                                options = groupMediaOrderChoices(),
                                currentValue = mediaOrder,
                                onValueChanged = { mediaOrder = it }
                            )
                            InsetDivider()
                            InputRow("媒体类型顺序", "英文逗号分隔：image,voice,emoji,video,file,favorite", mediaSequence) { mediaSequence = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "进群图片", joinImages, RedPacketRuleConfig.REPLY_IMAGE) { joinImages = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "退群图片", leftImages, RedPacketRuleConfig.REPLY_IMAGE) { leftImages = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "进群语音", joinVoices, RedPacketRuleConfig.REPLY_VOICE) { joinVoices = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "退群语音", leftVoices, RedPacketRuleConfig.REPLY_VOICE) { leftVoices = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "进群表情", joinEmojis, RedPacketRuleConfig.REPLY_EMOJI) { joinEmojis = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "退群表情", leftEmojis, RedPacketRuleConfig.REPLY_EMOJI) { leftEmojis = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "进群视频", joinVideos, RedPacketRuleConfig.REPLY_VIDEO) { joinVideos = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "退群视频", leftVideos, RedPacketRuleConfig.REPLY_VIDEO) { leftVideos = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "进群文件", joinFiles, RedPacketRuleConfig.REPLY_FILE) { joinFiles = it }
                            InsetDivider()
                            GroupMemberMediaRow(context, "退群文件", leftFiles, RedPacketRuleConfig.REPLY_FILE) { leftFiles = it }
                            InsetDivider()
                            GroupMemberMediaRow(
                                context,
                                "进群收藏",
                                joinFavorites,
                                RedPacketRuleConfig.REPLY_FAVORITE,
                                onValueChange = { joinFavorites = it },
                                onFavoritePicker = { favoritePicker = it }
                            )
                            InsetDivider()
                            GroupMemberMediaRow(
                                context,
                                "退群收藏",
                                leftFavorites,
                                RedPacketRuleConfig.REPLY_FAVORITE,
                                onValueChange = { leftFavorites = it },
                                onFavoritePicker = { favoritePicker = it }
                            )
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "延迟") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "精细延迟",
                            summary = groupModeLabel(delayMode),
                            options = groupModeChoices(),
                            currentValue = delayMode,
                            onValueChanged = { delayMode = it }
                        )
                        if (delayMode == GroupLeaveMonitorSettings.MODE_CUSTOM) {
                            InsetDivider()
                            NumberInputRow("提示延迟", "单位 ms", promptDelay) { promptDelay = it }
                            InsetDivider()
                            NumberInputRow("图片延迟", "单位 ms", imageDelay) { imageDelay = it }
                            InsetDivider()
                            NumberInputRow("语音延迟", "单位 ms", voiceDelay) { voiceDelay = it }
                            InsetDivider()
                            NumberInputRow("表情延迟", "单位 ms", emojiDelay) { emojiDelay = it }
                            InsetDivider()
                            NumberInputRow("视频延迟", "单位 ms", videoDelay) { videoDelay = it }
                            InsetDivider()
                            NumberInputRow("文件延迟", "单位 ms", fileDelay) { fileDelay = it }
                            InsetDivider()
                            NumberInputRow("收藏延迟", "单位 ms", favoriteDelay) { favoriteDelay = it }
                        }
                    }
                }
                if (request.canDelete) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                    item {
                        SettingsCard {
                            ActionRow("删除模板", "删除后绑定关系也会移除") {
                                onDelete()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMemberGroupEditorPage(
    context: Context,
    sp: SharedPreferences,
    groupId: String,
    groupLabel: String,
    templates: List<GroupLeaveReplyTemplate>,
    templateBindings: List<GroupLeaveReplyTemplateBinding>,
    listState: LazyListState,
    onTemplateBindingsChanged: (List<GroupLeaveReplyTemplateBinding>) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val title = groupLabel.ifBlank { groupId }
    val boundBinding = templateBindings.firstOrNull { it.groupId == groupId }
    val boundTemplate = templates.firstOrNull { it.id == boundBinding?.templateId }
    val templateOptions = listOf(PopupChoice("不使用模板", "")) + templates.map { template ->
        PopupChoice(template.name.ifBlank { template.id }, template.id)
    }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    var joinEnabled by rememberSaveable(groupId) {
        mutableStateOf(!parseIds(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, "").orEmpty()).contains(groupId))
    }
    var leftEnabled by rememberSaveable(groupId) {
        mutableStateOf(!parseIds(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, "").orEmpty()).contains(groupId))
    }
    val globalJoinText = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_JOIN_TEXT, GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT)
    val globalLeftText = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_LEFT_TEXT, GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT)
    val globalJoinCardTitle = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE)
    val globalJoinCardDesc = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC)
    val globalLeftCardTitle = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE)
    val globalLeftCardDesc = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC)
    var promptType by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, groupId), GroupLeaveMonitorSettings.MODE_GLOBAL) ?: GroupLeaveMonitorSettings.MODE_GLOBAL)
    }
    var bothOrder by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, groupId), groupBothOrderGlobal(sp)) ?: groupBothOrderGlobal(sp))
    }
    var joinText by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_JOIN_TEXT, groupId, globalJoinText))
    }
    var leftText by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_LEFT_TEXT, groupId, globalLeftText))
    }
    var joinCardTitle by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, groupId, globalJoinCardTitle))
    }
    var joinCardDesc by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, groupId, globalJoinCardDesc))
    }
    var leftCardTitle by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, groupId, globalLeftCardTitle))
    }
    var leftCardDesc by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, groupId, globalLeftCardDesc))
    }
    var mediaMode by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(groupMemberMediaModeKey(groupId), GroupLeaveMonitorSettings.MODE_GLOBAL) ?: GroupLeaveMonitorSettings.MODE_GLOBAL)
    }
    var mediaOrder by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, groupId, sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER))
    }
    var mediaSequence by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, groupId, sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE))
    }
    var joinImages by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, groupId), "").orEmpty()) }
    var leftImages by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, groupId), "").orEmpty()) }
    var joinVoices by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, groupId), "").orEmpty()) }
    var leftVoices by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, groupId), "").orEmpty()) }
    var joinEmojis by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, groupId), "").orEmpty()) }
    var leftEmojis by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, groupId), "").orEmpty()) }
    var joinVideos by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, groupId), "").orEmpty()) }
    var leftVideos by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, groupId), "").orEmpty()) }
    var joinFiles by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, groupId), "").orEmpty()) }
    var leftFiles by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, groupId), "").orEmpty()) }
    var joinFavorites by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, groupId), "").orEmpty()) }
    var leftFavorites by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, groupId), "").orEmpty()) }
    var delayMode by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(groupMemberDelayModeKey(groupId), GroupLeaveMonitorSettings.MODE_GLOBAL) ?: GroupLeaveMonitorSettings.MODE_GLOBAL)
    }
    var promptDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS)) }
    var imageDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var voiceDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var emojiDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var videoDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var fileDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var favoriteDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    val scrollBehavior = MiuixScrollBehavior()

    SettingsRouteTransition(
        targetState = favoritePicker,
        label = "GroupLeaveFavoriteRoute",
        depthOf = { if (it == null) 0 else 1 }
    ) { currentFavoritePicker ->
        if (currentFavoritePicker != null) {
            FavoritePickerPage(
                request = currentFavoritePicker,
                onBack = { favoritePicker = null }
            )
        } else PageScaffold(
            title = title,
            largeTitle = "群专属设置",
            scrollBehavior = scrollBehavior,
            bottomBar = {
                if (boundTemplate != null) {
                    BottomActionBar("返回", onBack)
                } else {
                    BottomActionBar(
                        primaryText = "保存设置",
                        onPrimaryClick = {
                            sp.edit()
                                .putString(GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, formatIds(updateGroupToggle(sp, GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, groupId, joinEnabled)))
                                .putString(GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, formatIds(updateGroupToggle(sp, GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, groupId, leftEnabled)))
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, groupId), promptType)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, groupId), bothOrder)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_TEXT, groupId), joinText)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_TEXT, groupId), leftText)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, groupId), joinCardTitle)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, groupId), joinCardDesc)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, groupId), leftCardTitle)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, groupId), leftCardDesc)
                                .putString(groupMemberMediaModeKey(groupId), mediaMode)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, groupId), mediaOrder)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, groupId), mediaSequence)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, groupId), joinImages)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, groupId), leftImages)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, groupId), joinVoices)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, groupId), leftVoices)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, groupId), joinEmojis)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, groupId), leftEmojis)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, groupId), joinVideos)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, groupId), leftVideos)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, groupId), joinFiles)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, groupId), leftFiles)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, groupId), joinFavorites)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, groupId), leftFavorites)
                                .putString(groupMemberDelayModeKey(groupId), delayMode)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, groupId), promptDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, groupId), imageDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, groupId), voiceDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, groupId), emojiDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, groupId), videoDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, groupId), fileDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, groupId), favoriteDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .apply()
                            Toast.makeText(context, "群专属设置已保存", Toast.LENGTH_SHORT).show()
                        },
                        secondaryText = "返回",
                        onSecondaryClick = onBack
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
                item { SmallTitle(text = "模板") }
                item {
                    SettingsCard {
                        if (templates.isEmpty()) {
                            EmptyText("暂无模板。先返回回复模板管理新增模板。")
                        } else {
                            PopupChoiceRow(
                                title = "选择模板",
                                summary = boundTemplate?.let { describeGroupLeaveTemplate(it) }
                                    ?: "不使用模板，配置本群专属设置",
                                options = templateOptions,
                                currentValue = boundTemplate?.id.orEmpty(),
                                onValueChanged = { templateId ->
                                    val next = if (templateId.isBlank()) {
                                        templateBindings.filterNot { it.groupId == groupId }
                                    } else {
                                        upsertGroupLeaveTemplateBindings(
                                            templateBindings,
                                            listOf(GroupLeaveReplyTemplateBinding(groupId, title, templateId))
                                        )
                                    }
                                    onTemplateBindingsChanged(next)
                                    Toast.makeText(
                                        context,
                                        if (templateId.isBlank()) "已取消模板绑定" else "模板已绑定",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
                if (boundTemplate != null) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "群号") }
                    item {
                        SettingsCard {
                            SelectRow(title = title, summary = groupId, onClick = {})
                        }
                    }
                } else {
                    item { SmallTitle(text = "事件") }
            item {
                SettingsCard {
                    SwitchRow(joinEnabled, "进群回复", "该群检测到成员进群时发送", onCheckedChange = { joinEnabled = it })
                    InsetDivider()
                    SwitchRow(leftEnabled, "退群回复", "该群检测到成员退群时发送", onCheckedChange = { leftEnabled = it })
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提示模板") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "提示设置",
                        summary = groupPromptTypeWithGlobalLabel(promptType),
                        options = groupPromptTypeWithGlobalChoices(),
                        currentValue = promptType,
                        onValueChanged = {
                            promptType = it
                            sp.edit().putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, groupId), it).apply()
                        }
                    )
                    if (promptType != GroupLeaveMonitorSettings.MODE_GLOBAL) {
                        if (promptType == GroupLeaveMonitorSettings.PROMPT_BOTH) {
                            InsetDivider()
                            PopupChoiceRow(
                                title = "文本+卡片顺序",
                                summary = groupBothOrderLabel(bothOrder),
                                options = groupBothOrderChoices(),
                                currentValue = bothOrder,
                                onValueChanged = {
                                    bothOrder = it
                                    sp.edit().putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, groupId), it).apply()
                                }
                            )
                        }
                        InsetDivider()
                        VariableInputRow("进群文本", "多个模板用 || 分隔随机选择", joinText, groupMemberTemplateVariables, minLines = 4) { joinText = it }
                        InsetDivider()
                        VariableInputRow("退群文本", "多个模板用 || 分隔随机选择", leftText, groupMemberTemplateVariables, minLines = 4) { leftText = it }
                        InsetDivider()
                        ActionRow("随机填充文本", "随机生成本群进群和退群文本") {
                            joinText = groupRandomJoinTexts[Random.nextInt(groupRandomJoinTexts.size)]
                            leftText = groupRandomLeftTexts[Random.nextInt(groupRandomLeftTexts.size)]
                            Toast.makeText(context, "已随机填充本群文本", Toast.LENGTH_SHORT).show()
                        }
                        InsetDivider()
                        ActionRow("恢复全局文本", "用当前全局文本覆盖本群文本") {
                            joinText = globalJoinText
                            leftText = globalLeftText
                            Toast.makeText(context, "已恢复为全局文本", Toast.LENGTH_SHORT).show()
                        }
                        InsetDivider()
                        VariableInputRow("进群卡片标题", "支持变量", joinCardTitle, groupMemberTemplateVariables) { joinCardTitle = it }
                        InsetDivider()
                        VariableInputRow("进群卡片描述", "支持变量", joinCardDesc, groupMemberTemplateVariables, minLines = 3) { joinCardDesc = it }
                        InsetDivider()
                        VariableInputRow("退群卡片标题", "支持变量", leftCardTitle, groupMemberTemplateVariables) { leftCardTitle = it }
                        InsetDivider()
                        VariableInputRow("退群卡片描述", "支持变量", leftCardDesc, groupMemberTemplateVariables, minLines = 3) { leftCardDesc = it }
                        InsetDivider()
                        ActionRow("随机填充卡片", "随机生成本群进群和退群卡片") {
                            joinCardTitle = groupRandomJoinCardTitles[Random.nextInt(groupRandomJoinCardTitles.size)]
                            joinCardDesc = groupRandomJoinCardDescs[Random.nextInt(groupRandomJoinCardDescs.size)]
                            leftCardTitle = groupRandomLeftCardTitles[Random.nextInt(groupRandomLeftCardTitles.size)]
                            leftCardDesc = groupRandomLeftCardDescs[Random.nextInt(groupRandomLeftCardDescs.size)]
                            Toast.makeText(context, "已随机填充本群卡片", Toast.LENGTH_SHORT).show()
                        }
                        InsetDivider()
                        ActionRow("恢复全局卡片", "用当前全局卡片覆盖本群卡片") {
                            joinCardTitle = globalJoinCardTitle
                            joinCardDesc = globalJoinCardDesc
                            leftCardTitle = globalLeftCardTitle
                            leftCardDesc = globalLeftCardDesc
                            Toast.makeText(context, "已恢复为全局卡片", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "媒体") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "媒体设置",
                        summary = groupMediaModeLabel(mediaMode),
                        options = groupMediaModeChoices(),
                        currentValue = mediaMode,
                        onValueChanged = {
                            mediaMode = it
                            sp.edit().putString(groupMemberMediaModeKey(groupId), it).apply()
                        }
                    )
                    if (mediaMode == GroupLeaveMonitorSettings.MODE_CUSTOM) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "媒体顺序",
                            summary = groupMediaOrderLabel(mediaOrder),
                            options = groupMediaOrderChoices(),
                            currentValue = mediaOrder,
                            onValueChanged = {
                                mediaOrder = it
                                sp.edit().putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, groupId), it).apply()
                            }
                        )
                        InsetDivider()
                        InputRow("媒体类型顺序", "英文逗号分隔：image,voice,emoji,video,file,favorite", mediaSequence) { mediaSequence = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "进群图片", joinImages, RedPacketRuleConfig.REPLY_IMAGE) { joinImages = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "退群图片", leftImages, RedPacketRuleConfig.REPLY_IMAGE) { leftImages = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "进群语音", joinVoices, RedPacketRuleConfig.REPLY_VOICE) { joinVoices = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "退群语音", leftVoices, RedPacketRuleConfig.REPLY_VOICE) { leftVoices = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "进群表情", joinEmojis, RedPacketRuleConfig.REPLY_EMOJI) { joinEmojis = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "退群表情", leftEmojis, RedPacketRuleConfig.REPLY_EMOJI) { leftEmojis = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "进群视频", joinVideos, RedPacketRuleConfig.REPLY_VIDEO) { joinVideos = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "退群视频", leftVideos, RedPacketRuleConfig.REPLY_VIDEO) { leftVideos = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "进群文件", joinFiles, RedPacketRuleConfig.REPLY_FILE) { joinFiles = it }
                        InsetDivider()
                        GroupMemberMediaRow(context, "退群文件", leftFiles, RedPacketRuleConfig.REPLY_FILE) { leftFiles = it }
                        InsetDivider()
                        GroupMemberMediaRow(
                            context,
                            "进群收藏",
                            joinFavorites,
                            RedPacketRuleConfig.REPLY_FAVORITE,
                            onValueChange = { joinFavorites = it },
                            onFavoritePicker = { favoritePicker = it }
                        )
                        InsetDivider()
                        GroupMemberMediaRow(
                            context,
                            "退群收藏",
                            leftFavorites,
                            RedPacketRuleConfig.REPLY_FAVORITE,
                            onValueChange = { leftFavorites = it },
                            onFavoritePicker = { favoritePicker = it }
                        )
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "延迟") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "精细延迟",
                        summary = groupModeLabel(delayMode),
                        options = groupModeChoices(),
                        currentValue = delayMode,
                        onValueChanged = {
                            delayMode = it
                            sp.edit().putString(groupMemberDelayModeKey(groupId), it).apply()
                        }
                    )
                    if (delayMode == GroupLeaveMonitorSettings.MODE_CUSTOM) {
                        InsetDivider()
                        NumberInputRow("提示延迟", "单位 ms", promptDelay) { promptDelay = it }
                        InsetDivider()
                        NumberInputRow("图片延迟", "单位 ms", imageDelay) { imageDelay = it }
                        InsetDivider()
                        NumberInputRow("语音延迟", "单位 ms", voiceDelay) { voiceDelay = it }
                        InsetDivider()
                        NumberInputRow("表情延迟", "单位 ms", emojiDelay) { emojiDelay = it }
                        InsetDivider()
                        NumberInputRow("视频延迟", "单位 ms", videoDelay) { videoDelay = it }
                        InsetDivider()
                        NumberInputRow("文件延迟", "单位 ms", fileDelay) { fileDelay = it }
                        InsetDivider()
                        NumberInputRow("收藏延迟", "单位 ms", favoriteDelay) { favoriteDelay = it }
                    }
                }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("删除当前群", "移除监听、模板绑定和本群专属设置") {
                        onDelete()
                    }
                }
            }
        }
    }
}
}

private fun deleteGroupMemberConfiguration(
    sp: SharedPreferences,
    groupId: String,
    nextListenGroups: String,
    nextBindings: List<GroupLeaveReplyTemplateBinding>
) {
    val groupKeys = listOf(
        GroupLeaveMonitorSettings.KEY_PROMPT_TYPE,
        GroupLeaveMonitorSettings.KEY_BOTH_ORDER,
        GroupLeaveMonitorSettings.KEY_JOIN_TEXT,
        GroupLeaveMonitorSettings.KEY_LEFT_TEXT,
        GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE,
        GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC,
        GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE,
        GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC,
        GroupLeaveMonitorSettings.KEY_MEDIA_ORDER,
        GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE,
        GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS,
        GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS
    )
    val joinDisabled = formatIds(
        parseIds(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, "").orEmpty()) - groupId
    )
    val leftDisabled = formatIds(
        parseIds(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, "").orEmpty()) - groupId
    )
    val editor = sp.edit()
        .putString(GroupLeaveMonitorSettings.KEY_LISTEN_GROUPS, nextListenGroups)
        .putString(GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, joinDisabled)
        .putString(GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, leftDisabled)
        .putString(
            GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS,
            GroupLeaveMonitorSettings.encodeBindings(nextBindings)
        )
        .remove(groupMemberMediaModeKey(groupId))
        .remove(groupMemberDelayModeKey(groupId))
    groupKeys.forEach { key -> editor.remove(groupMemberGroupKey(key, groupId)) }
    sp.all.keys
        .filter { it.startsWith(GroupLeaveMonitorSettings.KEY_INVITE_COUNT_PREFIX + groupId + "|") }
        .forEach { key -> editor.remove(key) }
    editor.apply()
}

@Composable
private fun GroupMemberMediaRow(
    context: Context,
    title: String,
    value: String,
    replyMode: Int,
    onFavoritePicker: ((FavoritePickerRequest) -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    ActionRow(title, redPacketReplyContentSummary(replyMode, value)) {
        if (replyMode == RedPacketRuleConfig.REPLY_FAVORITE) {
            onFavoritePicker?.invoke(
                FavoritePickerRequest(
                    title = title,
                    existingValue = value,
                    onValue = onValueChange,
                    multiSelect = true,
                    delimiter = "|"
                )
            )
            return@ActionRow
        }
        val activity = context as? Activity
        if (activity == null) {
            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
            return@ActionRow
        }
        RedPacketReplyFilePickerBridge.launch(activity, replyMode) { paths ->
            if (paths.isNotEmpty()) {
                onValueChange(paths.joinToString("|"))
                Toast.makeText(context, "已选择 ${paths.size} 个文件", Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (value.isNotBlank()) {
        InsetDivider()
        ActionRow(
            "清空$title",
            if (replyMode == RedPacketRuleConfig.REPLY_FAVORITE) "移除已选择收藏" else "移除已选择文件"
        ) {
            onValueChange("")
        }
    }
}

private fun groupSelectionSummary(value: String): String {
    val count = parseIds(value).size
    return if (count == 0) "未选择监听群" else "已选择 $count 个群"
}

private fun systemNoticeScopeChoices(allValue: String, specificValue: String): List<PopupChoice<String>> = listOf(
    PopupChoice("全部群聊", allValue),
    PopupChoice("指定群聊", specificValue)
)

private fun systemNoticeScopeLabel(value: String, specificValue: String): String {
    return if (value == specificValue) "指定群聊" else "全部群聊"
}

private fun systemNoticeGroupSummary(value: String): String {
    val count = parseIds(value).size
    return if (count == 0) "未选择群聊" else "已选择 $count 个群"
}

private fun describeGroupLeaveTemplate(template: GroupLeaveReplyTemplate): String {
    val join = if (template.joinEnabled) "进群" else ""
    val left = if (template.leftEnabled) "退群" else ""
    val scope = listOf(join, left).filter { it.isNotBlank() }.joinToString("、").ifBlank { "未启用事件" }
    val media = when (template.mediaMode) {
        GroupLeaveMonitorSettings.MEDIA_NONE -> "不发媒体"
        GroupLeaveMonitorSettings.MODE_CUSTOM -> "自定义媒体"
        else -> "跟随全局媒体"
    }
    val delay = when (template.delayMode) {
        GroupLeaveMonitorSettings.MODE_CUSTOM -> "自定义延迟"
        else -> "跟随全局延迟"
    }
    return "$scope · ${groupPromptTypeWithGlobalLabel(template.promptType)} · $media · $delay"
}

private fun groupLeaveTemplateBindingSummary(
    bindings: List<GroupLeaveReplyTemplateBinding>,
    templates: List<GroupLeaveReplyTemplate>
): String {
    if (templates.isEmpty()) return "暂无模板"
    val count = bindings.size
    return if (count == 0) "暂无群已绑定模板" else "已绑定 $count 个群"
}

private fun groupLeaveGroupBindingSummary(
    groupId: String,
    defaultLabel: String,
    bindings: List<GroupLeaveReplyTemplateBinding>,
    templates: List<GroupLeaveReplyTemplate>
): String {
    val binding = bindings.firstOrNull { it.groupId == groupId } ?: return "未绑定模板"
    val template = templates.firstOrNull { it.id == binding.templateId } ?: return "模板已不存在"
    return "模板：${template.name.ifBlank { binding.label.ifBlank { defaultLabel } }}"
}

private fun newGroupLeaveTemplate(index: Int, sp: SharedPreferences): GroupLeaveReplyTemplate {
    return GroupLeaveReplyTemplate(
        id = System.currentTimeMillis().toString() + "_" + index,
        name = "模板 $index",
        enabled = true,
        joinEnabled = sp.getBoolean(GroupLeaveMonitorSettings.KEY_JOIN_REPLY_ENABLE, GroupLeaveMonitorSettings.DEFAULT_JOIN_REPLY_ENABLE),
        leftEnabled = sp.getBoolean(GroupLeaveMonitorSettings.KEY_LEFT_REPLY_ENABLE, GroupLeaveMonitorSettings.DEFAULT_LEFT_REPLY_ENABLE),
        promptType = sp.getString(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, GroupLeaveMonitorSettings.DEFAULT_PROMPT_TYPE) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_TYPE,
        bothOrder = sp.getString(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER,
        joinText = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_TEXT, GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT,
        leftText = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_TEXT, GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT,
        joinCardTitle = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE,
        joinCardDesc = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC,
        leftCardTitle = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE,
        leftCardDesc = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC,
        mediaMode = GroupLeaveMonitorSettings.MODE_CUSTOM,
        mediaOrder = sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER,
        mediaSequence = sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE,
        joinImages = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, "").orEmpty(),
        leftImages = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, "").orEmpty(),
        joinVoices = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, "").orEmpty(),
        leftVoices = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, "").orEmpty(),
        joinEmojis = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, "").orEmpty(),
        leftEmojis = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, "").orEmpty(),
        joinVideos = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, "").orEmpty(),
        leftVideos = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, "").orEmpty(),
        joinFiles = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, "").orEmpty(),
        leftFiles = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, "").orEmpty(),
        joinFavorites = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, "").orEmpty(),
        leftFavorites = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, "").orEmpty(),
        delayMode = GroupLeaveMonitorSettings.MODE_CUSTOM,
        promptDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS),
        imageDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        voiceDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        emojiDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        videoDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        fileDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        favoriteDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
    )
}

private fun upsertGroupLeaveTemplateBindings(
    current: List<GroupLeaveReplyTemplateBinding>,
    additions: List<GroupLeaveReplyTemplateBinding>
): List<GroupLeaveReplyTemplateBinding> {
    val result = linkedMapOf<String, GroupLeaveReplyTemplateBinding>()
    current.forEach { binding ->
        if (binding.groupId.isNotBlank() && binding.templateId.isNotBlank()) {
            result[binding.groupId] = binding.copy(
                groupId = binding.groupId.trim(),
                label = binding.label.trim().ifBlank { binding.groupId.trim() },
                templateId = binding.templateId.trim()
            )
        }
    }
    additions.forEach { binding ->
        if (binding.groupId.isNotBlank() && binding.templateId.isNotBlank()) {
            result[binding.groupId.trim()] = binding.copy(
                groupId = binding.groupId.trim(),
                label = binding.label.trim().ifBlank { binding.groupId.trim() },
                templateId = binding.templateId.trim()
            )
        }
    }
    return result.values.toList()
}

private fun groupPromptTypeLabel(value: String): String {
    return when (value) {
        GroupLeaveMonitorSettings.PROMPT_CARD -> "卡片"
        GroupLeaveMonitorSettings.PROMPT_BOTH -> "文本+卡片"
        else -> "文本"
    }
}

private fun groupPromptTypeChoices(): List<PopupChoice<String>> {
    return listOf(
        PopupChoice("文本", GroupLeaveMonitorSettings.PROMPT_TEXT),
        PopupChoice("文本+卡片", GroupLeaveMonitorSettings.PROMPT_BOTH),
        PopupChoice("卡片", GroupLeaveMonitorSettings.PROMPT_CARD)
    )
}

private fun groupBothOrderLabel(value: String): String {
    return if (value == GroupLeaveMonitorSettings.BOTH_CARD_FIRST) "先卡片后文本" else "先文本后卡片"
}

private fun groupBothOrderChoices(): List<PopupChoice<String>> {
    return listOf(
        PopupChoice("先文本后卡片", GroupLeaveMonitorSettings.BOTH_TEXT_FIRST),
        PopupChoice("先卡片后文本", GroupLeaveMonitorSettings.BOTH_CARD_FIRST)
    )
}

private fun groupMediaOrderLabel(value: String): String {
    return when (value) {
        GroupLeaveMonitorSettings.MEDIA_BEFORE -> "先媒体后提示"
        GroupLeaveMonitorSettings.MEDIA_AFTER -> "先提示后媒体"
        else -> "不发送媒体"
    }
}

private fun groupMediaOrderChoices(): List<PopupChoice<String>> {
    return listOf(
        PopupChoice("不发送媒体", GroupLeaveMonitorSettings.MEDIA_NONE),
        PopupChoice("先提示后媒体", GroupLeaveMonitorSettings.MEDIA_AFTER),
        PopupChoice("先媒体后提示", GroupLeaveMonitorSettings.MEDIA_BEFORE)
    )
}

private fun groupPromptTypeWithGlobalLabel(value: String): String {
    return when (value) {
        GroupLeaveMonitorSettings.MODE_GLOBAL -> "跟随全局"
        GroupLeaveMonitorSettings.PROMPT_CARD -> "卡片"
        GroupLeaveMonitorSettings.PROMPT_BOTH -> "文本+卡片"
        else -> "文本"
    }
}

private fun groupPromptTypeWithGlobalChoices(): List<PopupChoice<String>> {
    return listOf(PopupChoice("跟随全局", GroupLeaveMonitorSettings.MODE_GLOBAL)) + groupPromptTypeChoices()
}

private fun groupModeLabel(value: String): String {
    return if (value == GroupLeaveMonitorSettings.MODE_CUSTOM) "单独设置" else "跟随全局"
}

private fun groupModeChoices(): List<PopupChoice<String>> {
    return listOf(
        PopupChoice("跟随全局", GroupLeaveMonitorSettings.MODE_GLOBAL),
        PopupChoice("单独设置", GroupLeaveMonitorSettings.MODE_CUSTOM)
    )
}

private fun groupMediaModeLabel(value: String): String {
    return when (value) {
        GroupLeaveMonitorSettings.MODE_CUSTOM -> "单独设置"
        GroupLeaveMonitorSettings.MEDIA_NONE -> "不发媒体"
        else -> "跟随全局"
    }
}

private fun groupMediaModeChoices(): List<PopupChoice<String>> {
    return listOf(
        PopupChoice("跟随全局", GroupLeaveMonitorSettings.MODE_GLOBAL),
        PopupChoice("单独设置", GroupLeaveMonitorSettings.MODE_CUSTOM),
        PopupChoice("不发媒体", GroupLeaveMonitorSettings.MEDIA_NONE)
    )
}

private fun groupMemberGroupKey(key: String, groupId: String): String = "${key}_${groupId}"

private fun groupMemberMediaModeKey(groupId: String): String = GroupLeaveMonitorSettings.KEY_MEDIA_MODE_PREFIX + groupId

private fun groupMemberDelayModeKey(groupId: String): String = GroupLeaveMonitorSettings.KEY_DELAY_MODE_PREFIX + groupId

private fun groupBothOrderGlobal(sp: SharedPreferences): String {
    return sp.getString(
        GroupLeaveMonitorSettings.KEY_BOTH_ORDER,
        GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER
    ) ?: GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER
}

private fun groupMemberGlobalString(
    sp: SharedPreferences,
    key: String,
    fallback: String
): String {
    return sp.getString(key, fallback) ?: fallback
}

private fun groupMemberString(
    sp: SharedPreferences,
    key: String,
    groupId: String,
    fallback: String
): String {
    return sp.getString(groupMemberGroupKey(key, groupId), null) ?: fallback
}

private fun groupMemberIntString(
    sp: SharedPreferences,
    key: String,
    groupId: String,
    fallback: Int
): String {
    val global = sp.getInt(key, fallback)
    return sp.getInt(groupMemberGroupKey(key, groupId), global).toString()
}

private fun updateGroupToggle(
    sp: SharedPreferences,
    key: String,
    groupId: String,
    enabled: Boolean
): Set<String> {
    val disabled = parseIds(sp.getString(key, "").orEmpty()).toMutableSet()
    if (enabled) {
        disabled.remove(groupId)
    } else {
        disabled.add(groupId)
    }
    return disabled
}

internal sealed class TextVoiceRoute {
    object Main : TextVoiceRoute()
    object VoicePicker : TextVoiceRoute()
}
