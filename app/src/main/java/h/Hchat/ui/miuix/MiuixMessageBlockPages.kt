package h.Hchat.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.messageblock.MessageBlockBinding
import h.Hchat.hooks.items.messageblock.MessageBlockDefaultRule
import h.Hchat.hooks.items.messageblock.MessageBlockSettings
import h.Hchat.hooks.items.messageblock.MessageBlockTemplate
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import java.util.LinkedHashSet
import java.util.Locale

@Composable
internal fun MessageBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MessageBlockSettings.PREFS_NAME) }
    val initialState = remember { initialMessageBlockState(sp) }
    var templates by remember {
        mutableStateOf(initialState.templates)
    }
    var bindings by remember {
        mutableStateOf(initialState.bindings)
    }
    var defaultPrivateRule by remember { mutableStateOf(MessageBlockSettings(context).defaultPrivateRule()) }
    var defaultGroupRule by remember { mutableStateOf(MessageBlockSettings(context).defaultGroupRule()) }
    var defaultOfficialRule by remember { mutableStateOf(MessageBlockSettings(context).defaultOfficialRule()) }
    var templateEditor by remember { mutableStateOf<MessageBlockTemplateEditorRequest?>(null) }
    var bindingEditor by remember { mutableStateOf<MessageBlockBindingEditorRequest?>(null) }
    var batchBindingEditor by remember { mutableStateOf<MessageBlockBatchBindingEditorRequest?>(null) }
    var bindingPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var groupMemberBindingPicker by remember { mutableStateOf<GroupMemberPickerRequest?>(null) }
    var defaultRuleEditor by remember { mutableStateOf<MessageBlockDefaultRuleKind?>(null) }
    var showTemplateManager by remember { mutableStateOf(false) }
    var showListManager by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(Unit) {
        if (initialState.shouldPersist) {
            sp.edit()
                .putString(MessageBlockSettings.KEY_TEMPLATES, MessageBlockSettings.encodeTemplates(templates))
                .putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(bindings))
                .apply()
        }
    }

    val route = when {
        templateEditor != null -> MessageBlockRoute.TemplateEditor(templateEditor!!)
        bindingEditor != null -> MessageBlockRoute.BindingEditor(bindingEditor!!)
        batchBindingEditor != null -> MessageBlockRoute.BatchBindingEditor(batchBindingEditor!!)
        bindingPicker != null -> MessageBlockRoute.ContactPicker(bindingPicker!!)
        groupMemberBindingPicker != null -> MessageBlockRoute.GroupMemberPicker(groupMemberBindingPicker!!)
        defaultRuleEditor != null -> MessageBlockRoute.DefaultRuleEditor(defaultRuleEditor!!)
        showTemplateManager -> MessageBlockRoute.TemplateManager
        showListManager -> MessageBlockRoute.ListManager
        else -> MessageBlockRoute.Main
    }

    SettingsRouteTransition(
        targetState = route,
        label = "MessageBlockRouteTransition",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is MessageBlockRoute.TemplateEditor -> {
                val request = currentRoute.request
                MessageBlockTemplatePage(
                    context = context,
                    request = request,
                    onBack = { templateEditor = null },
                    onSave = { updated ->
                        val next = if (request.index in templates.indices) {
                            templates.toMutableList().also { it[request.index] = MessageBlockSettings.clearTemplateTargets(updated) }
                        } else {
                            templates + MessageBlockSettings.clearTemplateTargets(updated)
                        }
                        templates = next
                        sp.edit().putString(MessageBlockSettings.KEY_TEMPLATES, MessageBlockSettings.encodeTemplates(next)).apply()
                        templateEditor = null
                    },
                    onDelete = {
                        if (request.index in templates.indices) {
                            val deletedId = templates[request.index].id
                            val next = templates.toMutableList().also { it.removeAt(request.index) }
                            val nextBindings = bindings.map { binding ->
                                binding.copy(templateIds = binding.templateIds - deletedId)
                            }
                            fun withoutDeletedTemplate(rule: MessageBlockDefaultRule): MessageBlockDefaultRule {
                                val nextIds = rule.templateIds - deletedId
                                return rule.copy(
                                    enabled = rule.enabled && (rule.customRules || nextIds.isNotEmpty()),
                                    templateIds = nextIds
                                )
                            }
                            val nextDefaultPrivate = withoutDeletedTemplate(defaultPrivateRule)
                            val nextDefaultGroup = withoutDeletedTemplate(defaultGroupRule)
                            val nextDefaultOfficial = withoutDeletedTemplate(defaultOfficialRule)
                            templates = next
                            bindings = nextBindings
                            defaultPrivateRule = nextDefaultPrivate
                            defaultGroupRule = nextDefaultGroup
                            defaultOfficialRule = nextDefaultOfficial
                            sp.edit()
                                .putString(MessageBlockSettings.KEY_TEMPLATES, MessageBlockSettings.encodeTemplates(next))
                                .putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(nextBindings))
                                .putString(
                                    MessageBlockSettings.KEY_DEFAULT_PRIVATE,
                                    MessageBlockSettings.encodeDefaultRule(nextDefaultPrivate, group = false)
                                )
                                .putString(
                                    MessageBlockSettings.KEY_DEFAULT_GROUP,
                                    MessageBlockSettings.encodeDefaultRule(nextDefaultGroup, group = true)
                                )
                                .putString(
                                    MessageBlockSettings.KEY_DEFAULT_OFFICIAL,
                                    MessageBlockSettings.encodeDefaultRule(
                                        nextDefaultOfficial,
                                        group = false,
                                        official = true
                                    )
                                )
                                .apply()
                        }
                        templateEditor = null
                    }
                )
            }
            is MessageBlockRoute.BindingEditor -> {
                val request = currentRoute.request
                MessageBlockBindingPage(
                    context = context,
                    request = request,
                    templates = templates,
                    onBack = { bindingEditor = null },
                    onSave = { updated ->
                        val normalized = normalizedMessageBlockBinding(updated)
                        val base = if (request.index in bindings.indices) {
                            bindings.toMutableList().also { it.removeAt(request.index) }
                        } else {
                            bindings
                        }
                        val next = upsertMessageBlockBindings(base, listOf(normalized))
                        bindings = next
                        sp.edit().putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(next)).apply()
                        bindingEditor = null
                    },
                    onDelete = {
                        if (request.index in bindings.indices) {
                            val next = bindings.toMutableList().also { it.removeAt(request.index) }
                            bindings = next
                            sp.edit().putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(next)).apply()
                        }
                        bindingEditor = null
                    }
                )
            }
            is MessageBlockRoute.BatchBindingEditor -> {
                val request = currentRoute.request
                MessageBlockBatchBindingPage(
                    context = context,
                    request = request,
                    templates = templates,
                    onBack = { batchBindingEditor = null },
                    onSave = { updated ->
                        val next = upsertMessageBlockBindings(bindings, updated)
                        bindings = next
                        sp.edit().putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(next)).apply()
                        Toast.makeText(context, "已保存 ${updated.size} 个名单项", Toast.LENGTH_SHORT).show()
                        batchBindingEditor = null
                    }
                )
            }
            is MessageBlockRoute.DefaultRuleEditor -> {
                val rule = when (currentRoute.kind) {
                    MessageBlockDefaultRuleKind.PRIVATE -> defaultPrivateRule
                    MessageBlockDefaultRuleKind.GROUP -> defaultGroupRule
                    MessageBlockDefaultRuleKind.OFFICIAL -> defaultOfficialRule
                }
                MessageBlockDefaultRulePage(
                    context = context,
                    rule = rule,
                    templates = templates,
                    onBack = { defaultRuleEditor = null },
                    onSave = { updated ->
                        val normalized = when (currentRoute.kind) {
                            MessageBlockDefaultRuleKind.PRIVATE -> updated.copy(group = false, official = false, label = "默认私聊规则")
                            MessageBlockDefaultRuleKind.GROUP -> updated.copy(group = true, official = false, label = "默认群聊规则")
                            MessageBlockDefaultRuleKind.OFFICIAL -> updated.copy(group = false, official = true, label = "默认公众号规则")
                        }
                        when (currentRoute.kind) {
                            MessageBlockDefaultRuleKind.PRIVATE -> {
                                defaultPrivateRule = normalized
                                MessageBlockSettings(context).saveDefaultPrivateRule(normalized)
                            }
                            MessageBlockDefaultRuleKind.GROUP -> {
                                defaultGroupRule = normalized
                                MessageBlockSettings(context).saveDefaultGroupRule(normalized)
                            }
                            MessageBlockDefaultRuleKind.OFFICIAL -> {
                                defaultOfficialRule = normalized
                                MessageBlockSettings(context).saveDefaultOfficialRule(normalized)
                            }
                        }
                        Toast.makeText(context, "默认规则已保存", Toast.LENGTH_SHORT).show()
                        defaultRuleEditor = null
                    }
                )
            }
            is MessageBlockRoute.ContactPicker -> {
                val request = currentRoute.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { bindingPicker = null },
                    onConfirm = { selected ->
                        val additions = selected.map {
                            messageBlockBindingFromContact(
                                option = it,
                                templates = templates,
                                existing = findMessageBlockBinding(bindings, MessageBlockSettings.TARGET_CONTACT, it.id)
                            )
                        }
                        if (additions.size == 1) {
                            val existingIndex = bindings.indexOfFirst {
                                it.targetType == additions.first().targetType &&
                                    it.targetId == additions.first().targetId
                            }
                            bindingEditor = MessageBlockBindingEditorRequest(
                                index = existingIndex.takeIf { it >= 0 } ?: bindings.size,
                                binding = additions.first(),
                                canDelete = existingIndex >= 0
                            )
                        } else if (additions.isNotEmpty()) {
                            batchBindingEditor = MessageBlockBatchBindingEditorRequest(
                                title = "批量添加名单",
                                bindings = additions
                            )
                        }
                        bindingPicker = null
                    }
                )
            }
            is MessageBlockRoute.GroupMemberPicker -> {
                val request = currentRoute.request
                GroupMemberPickerPage(
                    context = context,
                    request = request,
                    onBack = { groupMemberBindingPicker = null },
                    onConfirm = { selected ->
                        val additions = selected.map {
                            messageBlockBindingFromGroupMember(
                                entry = it,
                                templates = templates,
                                existing = findMessageBlockBinding(bindings, MessageBlockSettings.TARGET_GROUP_MEMBER, it)
                            )
                        }
                        if (additions.size == 1) {
                            val binding = additions.first()
                            val existingIndex = bindings.indexOfFirst {
                                it.targetType == binding.targetType &&
                                    it.targetId == binding.targetId
                            }
                            bindingEditor = MessageBlockBindingEditorRequest(
                                index = existingIndex.takeIf { it >= 0 } ?: bindings.size,
                                binding = binding,
                                canDelete = existingIndex >= 0
                            )
                        } else if (additions.isNotEmpty()) {
                            batchBindingEditor = MessageBlockBatchBindingEditorRequest(
                                title = "批量添加群成员",
                                bindings = additions
                            )
                        }
                        groupMemberBindingPicker = null
                    }
                )
            }
            MessageBlockRoute.TemplateManager -> {
                MessageBlockTemplateListPage(
                    templates = templates,
                    onBack = { showTemplateManager = false },
                    onOpenTemplate = { index, template ->
                        templateEditor = MessageBlockTemplateEditorRequest(index, template, canDelete = true)
                    },
                    onAddTemplate = {
                        templateEditor = MessageBlockTemplateEditorRequest(
                            templates.size,
                            newMessageBlockTemplate(templates.size + 1),
                            canDelete = false
                        )
                    }
                )
            }
            MessageBlockRoute.ListManager -> {
                MessageBlockListManagerPage(
                    bindings = bindings,
                    templates = templates,
                    onBack = { showListManager = false },
                    onOpenBinding = { index, binding ->
                        bindingEditor = MessageBlockBindingEditorRequest(index, binding, canDelete = true)
                    },
                    onAddContact = {
                        bindingPicker = ContactPickerRequest(
                            title = "选择名单",
                            mode = ContactPickerMode.ALL_CHATS,
                            multiSelect = true,
                            existingValue = "",
                            onValue = {},
                            enableLabels = true
                        )
                    },
                    onAddGroupMember = {
                        groupMemberBindingPicker = GroupMemberPickerRequest(
                            title = "选择群成员",
                            existingValue = "",
                            onValue = {}
                        )
                    },
                    onDeleteBindings = { targets ->
                        if (targets.isEmpty()) {
                            Toast.makeText(context, "没有可删除的名单", Toast.LENGTH_SHORT).show()
                        } else {
                            val removeKeys = targets.map {
                                MessageBlockSettings.bindingKey(it.targetType, it.targetId)
                            }.toSet()
                            val next = bindings.filterNot {
                                removeKeys.contains(MessageBlockSettings.bindingKey(it.targetType, it.targetId))
                            }
                            bindings = next
                            sp.edit().putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(next)).apply()
                            Toast.makeText(context, "已删除 ${targets.size} 个名单项", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            MessageBlockRoute.Main -> PageScaffold(
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
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageBlockSettings.KEY_ENABLE,
                        "启用屏蔽消息",
                        "总开关开启后，仅命中启用模板的新消息会被拦截",
                        MessageBlockSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "管理") }
            item {
                SettingsCard {
                    SelectRow(
                        title = "默认私聊规则",
                        summary = describeMessageBlockDefaultRule(defaultPrivateRule, templates),
                        onClick = { defaultRuleEditor = MessageBlockDefaultRuleKind.PRIVATE }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "默认群聊规则",
                        summary = describeMessageBlockDefaultRule(defaultGroupRule, templates),
                        onClick = { defaultRuleEditor = MessageBlockDefaultRuleKind.GROUP }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "默认公众号规则",
                        summary = describeMessageBlockDefaultRule(defaultOfficialRule, templates),
                        onClick = { defaultRuleEditor = MessageBlockDefaultRuleKind.OFFICIAL }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "模板管理",
                        summary = if (templates.isEmpty()) "暂无模板，进入后添加屏蔽规则" else "${templates.size} 个模板，进入后添加或修改规则",
                        onClick = { showTemplateManager = true }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "名单管理",
                        summary = if (bindings.isEmpty()) "暂无名单，进入后添加好友、群聊、公众号或群成员" else "${bindings.size} 个名单项，进入后分配模板",
                        onClick = { showListManager = true }
                    )
                }
            }
        }
    }
        }
    }
}

@Composable
private fun MessageBlockTemplateListPage(
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onOpenTemplate: (Int, MessageBlockTemplate) -> Unit,
    onAddTemplate: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "模板管理",
        largeTitle = "模板管理",
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
                        EmptyText("暂无模板。模板只配置消息类型和关键词，名单在“名单管理”里分配模板。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SelectRow(
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeMessageBlockTemplate(template),
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
private fun MessageBlockListManagerPage(
    bindings: List<MessageBlockBinding>,
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onOpenBinding: (Int, MessageBlockBinding) -> Unit,
    onAddContact: () -> Unit,
    onAddGroupMember: () -> Unit,
    onDeleteBindings: (List<MessageBlockBinding>) -> Unit
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf(ConversationRuleCategory.ALL) }
    var query by remember { mutableStateOf("") }
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val lower = query.trim().lowercase(Locale.US)
    val visibleBindings = bindings.mapIndexed { index, binding -> index to binding }
        .filter { (_, binding) ->
            conversationRuleCategoryMatches(binding, category) &&
                (lower.isEmpty() || messageBlockBindingMatchesQuery(binding, templates, lower))
        }
    val listTitle = when {
        bindings.isEmpty() -> "名单"
        lower.isEmpty() -> "名单 · ${bindings.size} 项"
        else -> "名单 · ${visibleBindings.size}/${bindings.size} 项"
    }
    val visibleIds = visibleBindings.mapTo(LinkedHashSet()) { it.second.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val selectedBindings = bindings.filter { it.id in selectedIds }
    PageScaffold(
        title = "名单管理",
        largeTitle = "名单管理",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedBindings.size}）",
                    onPrimaryClick = {
                        if (selectedBindings.isEmpty()) {
                            Toast.makeText(context, "请先选择名单", Toast.LENGTH_SHORT).show()
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
                    primaryText = "返回",
                    onPrimaryClick = onBack,
                    middleText = if (bindings.isEmpty()) null else "批量删除",
                    onMiddleClick = if (bindings.isEmpty()) null else {
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
                    InputRow(
                        title = "搜索名单",
                        summary = "昵称 / wxid / 群号 / 模板名",
                        value = query,
                        onValueChange = { query = it }
                    )
                }
            }
            if (!batchDeleteMode) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "添加") }
                item {
                    SettingsCard {
                        ActionRow("添加好友/群聊/公众号", "批量添加后可一次选择模板") {
                            onAddContact()
                        }
                        InsetDivider()
                        ActionRow("添加群成员", "批量添加后可一次选择模板") {
                            onAddGroupMember()
                        }
                    }
                }
            }
            item { SmallTitle(text = listTitle) }
            item {
                SettingsCard {
                    if (bindings.isEmpty()) {
                        EmptyText("暂无名单。添加好友、群聊、公众号或群成员后，再给名单分配模板。")
                    } else if (visibleBindings.isEmpty()) {
                        EmptyText("没有匹配名单。可按昵称、ID 或模板名搜索。")
                    } else {
                        visibleBindings.forEachIndexed { rowIndex, (index, binding) ->
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = binding.label.ifBlank { binding.targetId },
                                        value = index,
                                        summary = describeMessageBlockBinding(binding, templates)
                                    ),
                                    selected = binding.id in selectedIds,
                                    onClick = {
                                        selectedIds = if (binding.id in selectedIds) {
                                            selectedIds - binding.id
                                        } else {
                                            selectedIds + binding.id
                                        }
                                    }
                                )
                            } else {
                                SelectRow(
                                    title = binding.label.ifBlank { binding.targetId },
                                    summary = describeMessageBlockBinding(binding, templates),
                                    onClick = { onOpenBinding(index, binding) }
                                )
                            }
                            if (rowIndex < visibleBindings.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedBindings.size} 个名单项，此操作不可撤销。",
        labels = selectedBindings.map { it.label.ifBlank { it.targetId } },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selectedBindings
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedIds = emptySet()
            onDeleteBindings(targets)
        }
    )
}

@Composable
private fun MessageBlockBatchBindingPage(
    context: Context,
    request: MessageBlockBatchBindingEditorRequest,
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onSave: (List<MessageBlockBinding>) -> Unit
) {
    var enabled by remember(request) { mutableStateOf(true) }
    var action by remember(request) { mutableStateOf(MessageBlockSettings.ACTION_BLOCK) }
    var templateIds by remember(request) { mutableStateOf(defaultMessageBlockTemplateIds(templates)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = request.title,
        largeTitle = request.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存名单",
                onPrimaryClick = {
                    if (templateIds.isEmpty()) {
                        Toast.makeText(context, "请先选择模板", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(
                            request.bindings.map {
                                it.copy(
                                    enabled = enabled,
                                    action = action,
                                    templateIds = templateIds,
                                    customRules = false,
                                    typeAll = false,
                                    types = emptySet(),
                                    textKeywords = ""
                                )
                            }
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
            item { SmallTitle(text = "批量配置") }
            item {
                SettingsCard {
                    SelectRow(
                        title = "已选择",
                        summary = "${request.bindings.size} 个名单项",
                        onClick = {}
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用名单",
                        summary = "关闭后保留名单但不参与匹配",
                        onCheckedChange = { enabled = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = action == MessageBlockSettings.ACTION_EXCLUDE,
                        title = "排除名单",
                        summary = "开启后这些名单命中时跳过屏蔽",
                        onCheckedChange = {
                            action = if (it) MessageBlockSettings.ACTION_EXCLUDE else MessageBlockSettings.ACTION_BLOCK
                        }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。先新增模板，再回来批量添加名单。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SwitchRow(
                                checked = templateIds.contains(template.id),
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeMessageBlockTemplate(template),
                                onCheckedChange = { checked ->
                                    templateIds = if (checked) {
                                        templateIds + template.id
                                    } else {
                                        templateIds - template.id
                                    }
                                }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "名单") }
            item {
                SettingsCard {
                    request.bindings.take(30).forEachIndexed { index, binding ->
                        SelectRow(
                            title = binding.label.ifBlank { binding.targetId },
                            summary = messageBlockBindingTargetSummary(binding),
                            onClick = {}
                        )
                        if (index < minOf(request.bindings.size, 30) - 1) InsetDivider()
                    }
                    if (request.bindings.size > 30) {
                        InsetDivider()
                        EmptyText("还有 ${request.bindings.size - 30} 个名单项未展开显示")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBlockDefaultRulePage(
    context: Context,
    rule: MessageBlockDefaultRule,
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onSave: (MessageBlockDefaultRule) -> Unit
) {
    var enabled by remember(rule) { mutableStateOf(rule.enabled) }
    var templateIds by remember(rule) { mutableStateOf(rule.templateIds) }
    var customRules by remember(rule) { mutableStateOf(rule.customRules) }
    var typeAll by remember(rule) { mutableStateOf(rule.typeAll) }
    var types by remember(rule) { mutableStateOf(rule.types) }
    var textKeywords by remember(rule) { mutableStateOf(rule.textKeywords) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val title = when {
        rule.official -> "默认公众号规则"
        rule.group -> "默认群聊规则"
        else -> "默认私聊规则"
    }
    val scopeSummary = when {
        rule.official -> "未配置名单的公众号按此规则屏蔽"
        rule.group -> "未配置名单的群聊按此规则屏蔽"
        else -> "未配置名单的私聊按此规则屏蔽"
    }
    fun copySelectedTemplateRules() {
        val rules = messageBlockRuleStateFromTemplates(templateIds, templates)
        typeAll = rules.typeAll
        types = rules.types
        textKeywords = rules.textKeywords
    }

    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存规则",
                onPrimaryClick = {
                    if (enabled && !customRules && templateIds.isEmpty()) {
                        Toast.makeText(context, "请先选择模板", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(
                            rule.copy(
                                enabled = enabled,
                                templateIds = templateIds,
                                customRules = customRules,
                                typeAll = typeAll,
                                types = types,
                                textKeywords = if (customRules && !typeAll && types.contains(MessageBlockSettings.TYPE_TEXT)) textKeywords else ""
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
            item { SmallTitle(text = "默认规则") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用默认规则",
                        summary = scopeSummary,
                        onCheckedChange = { enabled = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。先新增模板，或开启下方专属规则。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SwitchRow(
                                checked = templateIds.contains(template.id),
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeMessageBlockTemplate(template),
                                onCheckedChange = { checked ->
                                    templateIds = if (checked) {
                                        templateIds + template.id
                                    } else {
                                        templateIds - template.id
                                    }
                                    if (!customRules) copySelectedTemplateRules()
                                }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "规则") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = customRules,
                        title = "默认专属规则",
                        summary = "开启后不依赖模板，直接按下方类型和关键词屏蔽",
                        onCheckedChange = { checked ->
                            customRules = checked
                            if (checked && !typeAll && types.isEmpty() && textKeywords.isBlank()) {
                                copySelectedTemplateRules()
                            }
                        }
                    )
                    if (customRules) {
                        InsetDivider()
                        ActionRow("套用已选模板规则", "把上方模板的类型和关键词复制到这里") {
                            copySelectedTemplateRules()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = typeAll,
                            title = "所有消息",
                            summary = "开启后默认范围内全部类型生效",
                            onCheckedChange = {
                                typeAll = it
                                if (it) textKeywords = ""
                            }
                        )
                        if (!typeAll) {
                            messageBlockTypeOptions().forEach { option ->
                                InsetDivider()
                                SwitchRow(
                                    checked = types.contains(option.key),
                                    title = option.title,
                                    summary = option.summary,
                                    onCheckedChange = { checked ->
                                        types = if (checked) {
                                            types + option.key
                                        } else {
                                            if (option.key == MessageBlockSettings.TYPE_TEXT) textKeywords = ""
                                            types - option.key
                                        }
                                    }
                                )
                            }
                            if (types.contains(MessageBlockSettings.TYPE_TEXT)) {
                                InsetDivider()
                                InputRow(
                                    "文字关键词",
                                    "文字和文字引用消息生效；多个用 |、逗号或换行分隔，留空则全部命中",
                                    textKeywords,
                                    minLines = 2
                                ) { textKeywords = it }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBlockTemplatePage(
    context: Context,
    request: MessageBlockTemplateEditorRequest,
    onBack: () -> Unit,
    onSave: (MessageBlockTemplate) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(request) { mutableStateOf(request.template.name) }
    var enabled by remember(request) { mutableStateOf(request.template.enabled) }
    var typeAll by remember(request) { mutableStateOf(request.template.typeAll) }
    var types by remember(request) { mutableStateOf(request.template.types) }
    var textKeywords by remember(request) { mutableStateOf(request.template.textKeywords) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = name.ifBlank { "模板" },
        largeTitle = name.ifBlank { "模板" },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存模板",
                onPrimaryClick = {
                    onSave(
                        MessageBlockTemplate(
                            id = request.template.id,
                            name = name.trim().ifBlank { "模板 ${request.index + 1}" },
                            enabled = enabled,
                            mode = MessageBlockSettings.MODE_TARGETS,
                            targets = "",
                            targetGroupMembers = "",
                            excludes = "",
                            excludeGroupMembers = "",
                            typeAll = typeAll,
                            types = types,
                            textKeywords = if (!typeAll && types.contains(MessageBlockSettings.TYPE_TEXT)) textKeywords else ""
                        )
                    )
                    Toast.makeText(context, "模板已保存", Toast.LENGTH_SHORT).show()
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
                    InputRow("模板名称", "用于区分不同屏蔽模板", name) { name = it }
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用模板",
                        summary = "关闭后使用该模板的名单不会按此规则屏蔽",
                        onCheckedChange = { enabled = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "类型") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = typeAll,
                        title = "所有消息",
                        summary = "开启后该模板命中范围内全部类型生效",
                        onCheckedChange = {
                            typeAll = it
                            if (it) textKeywords = ""
                        }
                    )
                    if (!typeAll) {
                        messageBlockTypeOptions().forEach { option ->
                            InsetDivider()
                            SwitchRow(
                                checked = types.contains(option.key),
                                title = option.title,
                                summary = option.summary,
                                onCheckedChange = { checked ->
                                    types = if (checked) {
                                        types + option.key
                                    } else {
                                        if (option.key == MessageBlockSettings.TYPE_TEXT) textKeywords = ""
                                        types - option.key
                                    }
                                }
                            )
                        }
                        if (types.contains(MessageBlockSettings.TYPE_TEXT)) {
                            InsetDivider()
                            InputRow(
                                "文字关键词",
                                "文字和文字引用消息生效；多个用 |、逗号或换行分隔，留空则全部命中",
                                textKeywords,
                                minLines = 2
                            ) { textKeywords = it }
                        }
                    }
                }
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除模板", "删除后立即从模板列表移除") {
                            onDelete()
                            Toast.makeText(context, "模板已删除", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBlockBindingPage(
    context: Context,
    request: MessageBlockBindingEditorRequest,
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onSave: (MessageBlockBinding) -> Unit,
    onDelete: () -> Unit
) {
    var enabled by remember(request) { mutableStateOf(request.binding.enabled) }
    var action by remember(request) { mutableStateOf(request.binding.action) }
    var templateIds by remember(request) { mutableStateOf(request.binding.templateIds) }
    var customRules by remember(request) { mutableStateOf(request.binding.customRules) }
    var typeAll by remember(request) { mutableStateOf(request.binding.typeAll) }
    var types by remember(request) { mutableStateOf(request.binding.types) }
    var textKeywords by remember(request) { mutableStateOf(request.binding.textKeywords) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val targetTitle = request.binding.label.ifBlank { request.binding.targetId }
    fun copySelectedTemplateRules() {
        val rules = messageBlockRuleStateFromTemplates(templateIds, templates)
        typeAll = rules.typeAll
        types = rules.types
        textKeywords = rules.textKeywords
    }

    PageScaffold(
        title = targetTitle,
        largeTitle = targetTitle,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存名单",
                onPrimaryClick = {
                    onSave(
                        request.binding.copy(
                            enabled = enabled,
                            action = action,
                            templateIds = templateIds,
                            customRules = customRules,
                            typeAll = typeAll,
                            types = types,
                            textKeywords = if (customRules && !typeAll && types.contains(MessageBlockSettings.TYPE_TEXT)) textKeywords else ""
                        )
                    )
                    Toast.makeText(context, "名单已保存", Toast.LENGTH_SHORT).show()
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
            item { SmallTitle(text = "名单信息") }
            item {
                SettingsCard {
                    SelectRow(
                        title = targetTitle,
                        summary = messageBlockBindingTargetSummary(request.binding),
                        onClick = {}
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用名单",
                        summary = "关闭后该名单不参与屏蔽消息匹配",
                        onCheckedChange = { enabled = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = action == MessageBlockSettings.ACTION_EXCLUDE,
                        title = "排除名单",
                        summary = "开启后命中该名单时跳过屏蔽；关闭时按下方模板屏蔽",
                        onCheckedChange = {
                            action = if (it) MessageBlockSettings.ACTION_EXCLUDE else MessageBlockSettings.ACTION_BLOCK
                        }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。先新增模板，再回到这里选择。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SwitchRow(
                                checked = templateIds.contains(template.id),
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeMessageBlockTemplate(template),
                                onCheckedChange = { checked ->
                                    templateIds = if (checked) {
                                        templateIds + template.id
                                    } else {
                                        templateIds - template.id
                                    }
                                    if (!customRules) copySelectedTemplateRules()
                                }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "规则") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = customRules,
                        title = "本名单专属规则",
                        summary = if (customRules) {
                            "开启后只按下方勾选屏蔽，不改动模板本身"
                        } else {
                            "关闭时跟随上方模板；开启后可直接给这个名单勾选文字、红包等类型"
                        },
                        onCheckedChange = { checked ->
                            customRules = checked
                            if (checked && !typeAll && types.isEmpty() && textKeywords.isBlank()) {
                                copySelectedTemplateRules()
                            }
                        }
                    )
                    if (customRules) {
                        InsetDivider()
                        ActionRow("套用已选模板规则", "把上方模板的类型和关键词复制到这里") {
                            copySelectedTemplateRules()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = typeAll,
                            title = "所有消息",
                            summary = "开启后该名单命中范围内全部类型生效",
                            onCheckedChange = {
                                typeAll = it
                                if (it) textKeywords = ""
                            }
                        )
                        if (!typeAll) {
                            messageBlockTypeOptions().forEach { option ->
                                InsetDivider()
                                SwitchRow(
                                    checked = types.contains(option.key),
                                    title = option.title,
                                    summary = option.summary,
                                    onCheckedChange = { checked ->
                                        types = if (checked) {
                                            types + option.key
                                        } else {
                                            if (option.key == MessageBlockSettings.TYPE_TEXT) textKeywords = ""
                                            types - option.key
                                        }
                                    }
                                )
                            }
                            if (types.contains(MessageBlockSettings.TYPE_TEXT)) {
                                InsetDivider()
                                InputRow(
                                    "文字关键词",
                                    "文字和文字引用消息生效；多个用 |、逗号或换行分隔，留空则全部命中",
                                    textKeywords,
                                    minLines = 2
                                ) { textKeywords = it }
                            }
                        }
                    }
                }
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除名单", "删除后该名单不再套用模板") {
                            onDelete()
                            Toast.makeText(context, "名单已删除", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
