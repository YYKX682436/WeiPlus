package h.Hchat.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.items.conversationgroup.ConversationGroupPickerSupport
import h.Hchat.hooks.items.grouplabel.GroupChatLabelStore
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun MessageBlockContactPickerPage(
    context: Context,
    request: MessageBlockContactPickerRequest,
    onBack: () -> Unit,
    onConfirm: (List<MessageBlockContactOption>) -> Unit
) {
    var loading by remember(request) { mutableStateOf(true) }
    var error by remember(request) { mutableStateOf("") }
    var data by remember(request) { mutableStateOf(MessageBlockContactData()) }
    var query by remember(request) { mutableStateOf("") }
    val initialSelectedIds = remember(request) { parseIds(request.existingValue) }
    var selectedIds by remember(request) { mutableStateOf(initialSelectedIds) }
    var contactFilter by remember(request) { mutableStateOf(MessageBlockContactFilter.ALL) }
    var selectedLabel by remember(request) { mutableStateOf<MessageBlockLabelOption?>(null) }
    var selectedConversationGroupId by remember(request) { mutableStateOf("") }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(request) {
        loadMessageBlockContacts(context) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取联系人失败"
            } else {
                data = result
            }
        }
    }
    LaunchedEffect(contactFilter) {
        if (contactFilter != MessageBlockContactFilter.LABELS) {
            selectedLabel = null
        }
        if (!request.allowOfficialAccounts && contactFilter == MessageBlockContactFilter.OFFICIALS) {
            contactFilter = MessageBlockContactFilter.ALL
        }
    }

    val currentLabel = selectedLabel
    val pageTitle = currentLabel?.let { "标签：${it.name}" } ?: request.title
    val availableContacts = data.contacts.filter {
        request.allowOfficialAccounts || it.kind != MessageBlockContactKind.OFFICIAL
    }
    val conversationGroupFilters = remember(availableContacts) {
        ConversationGroupPickerSupport.filters(context, availableContacts.map { it.contact.id })
    }
    val conversationGroupChoices = remember(conversationGroupFilters) {
        listOf(PopupChoice("全部会话", "")) + conversationGroupFilters.map {
            PopupChoice(it.name, it.id)
        }
    }
    LaunchedEffect(conversationGroupFilters) {
        if (selectedConversationGroupId.isNotBlank() &&
            conversationGroupFilters.none { it.id == selectedConversationGroupId }
        ) {
            selectedConversationGroupId = ""
        }
    }
    val selectedConversationIds = remember(conversationGroupFilters, selectedConversationGroupId) {
        conversationGroupFilters.firstOrNull { it.id == selectedConversationGroupId }?.conversationIds
    }
    val scopedContacts = remember(availableContacts, selectedConversationIds) {
        selectedConversationIds?.let { ids ->
            availableContacts.filter { it.contact.id in ids }
        } ?: availableContacts
    }
    val availableContactIds = scopedContacts.map { it.contact.id }.toSet()
    val bottomLower = query.trim().lowercase(Locale.US)
    val bottomVisibleContacts = scopedContacts.filter { row ->
        val contact = row.contact
        val typeMatched = when (contactFilter) {
            MessageBlockContactFilter.FRIENDS -> row.kind == MessageBlockContactKind.FRIEND
            MessageBlockContactFilter.GROUPS -> row.kind == MessageBlockContactKind.GROUP
            MessageBlockContactFilter.OFFICIALS -> row.kind == MessageBlockContactKind.OFFICIAL
            MessageBlockContactFilter.LABELS -> currentLabel?.contactIds?.contains(contact.id) == true
            MessageBlockContactFilter.ALL -> true
        }
        typeMatched && (
            contact.matchesSearch(bottomLower) ||
                row.labelNames.any { it.lowercase(Locale.US).contains(bottomLower) }
        )
    }
    val bottomShowingLabelList = contactFilter == MessageBlockContactFilter.LABELS && currentLabel == null
    val bottomVisibleContactIds = bottomVisibleContacts.map { it.contact.id }.toSet()
    val bottomVisibleAllSelected = bottomVisibleContactIds.isNotEmpty() && bottomVisibleContactIds.all { selectedIds.contains(it) }
    PageScaffold(
        title = pageTitle,
        largeTitle = pageTitle,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存选择",
                onPrimaryClick = {
                    val picked = availableContacts.filter { selectedIds.contains(it.contact.id) }
                    onConfirm(picked)
                },
                secondaryText = if (currentLabel == null) "返回" else "返回标签",
                onSecondaryClick = {
                    if (currentLabel == null) {
                        onBack()
                    } else {
                        selectedLabel = null
                        query = ""
                    }
                },
                middleText = if (!bottomShowingLabelList && bottomVisibleContactIds.isNotEmpty()) {
                    if (bottomVisibleAllSelected) "取消全选" else "全选"
                } else {
                    null
                },
                onMiddleClick = if (!bottomShowingLabelList && bottomVisibleContactIds.isNotEmpty()) {
                    {
                        selectedIds = if (bottomVisibleAllSelected) {
                            selectedIds - bottomVisibleContactIds
                        } else {
                            selectedIds + bottomVisibleContactIds
                        }
                    }
                } else {
                    null
                }
            )
        }
    ) { padding ->
        val listRoute = if (contactFilter == MessageBlockContactFilter.LABELS && currentLabel == null) {
            MessageBlockContactPickerRoute.Labels
        } else {
            MessageBlockContactPickerRoute.Contacts(contactFilter, currentLabel)
        }
        SettingsRouteTransition(
            targetState = listRoute,
            label = "MessageBlockContactPickerTransition",
            depthOf = { it.depth() },
            horizontalOnSameDepth = false
        ) { route ->
            val lower = query.trim().lowercase(Locale.US)
            val showingLabelList = route is MessageBlockContactPickerRoute.Labels
            val routeFilter = (route as? MessageBlockContactPickerRoute.Contacts)?.filter
                ?: MessageBlockContactFilter.LABELS
            val routeLabel = (route as? MessageBlockContactPickerRoute.Contacts)?.label
            val visibleLabels = data.labels.filter { label ->
                label.contactIds.any { availableContactIds.contains(it) } && (
                    lower.isEmpty() ||
                        label.name.lowercase(Locale.US).contains(lower) ||
                        label.id.lowercase(Locale.US).contains(lower)
                    )
            }.selectedFirst { label -> label.contactIds.any { initialSelectedIds.contains(it) } }
            val visibleContacts = scopedContacts.filter { row ->
                val contact = row.contact
                val typeMatched = when (routeFilter) {
                    MessageBlockContactFilter.FRIENDS -> row.kind == MessageBlockContactKind.FRIEND
                    MessageBlockContactFilter.GROUPS -> row.kind == MessageBlockContactKind.GROUP
                    MessageBlockContactFilter.OFFICIALS -> row.kind == MessageBlockContactKind.OFFICIAL
                    MessageBlockContactFilter.LABELS -> routeLabel?.contactIds?.contains(contact.id) == true
                    MessageBlockContactFilter.ALL -> true
                }
                typeMatched && (
                    contact.matchesSearch(lower) ||
                        row.labelNames.any { it.lowercase(Locale.US).contains(lower) }
                )
            }.selectedFirst { initialSelectedIds.contains(it.contact.id) }
            val visibleContactIds = visibleContacts.map { it.contact.id }.toSet()
            val sectionTitle = when {
                showingLabelList -> "标签"
                routeFilter == MessageBlockContactFilter.LABELS -> "标签成员"
                else -> routeFilter.title
            }
            val sectionCount = if (showingLabelList) visibleLabels.size else visibleContactIds.size
            val routeListState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                state = routeListState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 84.dp
                )
            ) {
                item {
                    SettingsCard {
                        MessageBlockContactFilterRow(
                            selected = contactFilter,
                            enableOfficials = request.allowOfficialAccounts,
                            onSelected = {
                                contactFilter = it
                                if (it == MessageBlockContactFilter.LABELS) {
                                    selectedLabel = null
                                }
                                query = ""
                            }
                        )
                    }
                }
                if (conversationGroupChoices.size > 1) {
                    item {
                        SettingsCard {
                            PopupChoiceRow(
                                title = "聊天分组",
                                summary = "只显示所选分组及其子分组中的会话",
                                options = conversationGroupChoices,
                                currentValue = selectedConversationGroupId,
                                onValueChanged = {
                                    selectedConversationGroupId = it
                                    query = ""
                                }
                            )
                        }
                    }
                }
                item {
                    SettingsCard {
                        InputRow(
                            title = "搜索",
                            summary = if (showingLabelList) "标签名称" else "昵称 / 群聊备注 / wxid",
                            value = query,
                            onValueChange = { query = it }
                        )
                    }
                }
                item {
                    PickerSectionHeader(
                        text = if (sectionCount > 0) "$sectionTitle · $sectionCount 项" else sectionTitle
                    )
                }
                when {
                    loading -> item { SettingsCard { EmptyText("正在载入列表...") } }
                    error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                    showingLabelList -> {
                        if (visibleLabels.isEmpty()) {
                            item { SettingsCard { EmptyText("没有匹配标签") } }
                        } else {
                            visibleLabels.forEach { label ->
                                item {
                                    MessageBlockLabelListCard(
                                        label = label,
                                        onClick = {
                                            selectedLabel = label
                                            query = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                    visibleContacts.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                    else -> visibleContacts.forEach { row ->
                        item {
                            val option = row.contact
                            MessageBlockContactListCard(
                                row = row,
                                selected = selectedIds.contains(option.id),
                                onClick = {
                                    selectedIds = if (selectedIds.contains(option.id)) {
                                        selectedIds - option.id
                                    } else {
                                        selectedIds + option.id
                                    }
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
internal fun ContactPickerPage(
    context: Context,
    request: ContactPickerRequest,
    onBack: () -> Unit,
    onConfirm: (List<ContactOption>) -> Unit
) {
    var loading by remember(request) { mutableStateOf(true) }
    var error by remember(request) { mutableStateOf("") }
    var contacts by remember(request) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember(request) { mutableStateOf("") }
    val initialSelectedIds = remember(request) { parseIds(request.existingValue) }
    var selectedIds by remember(request) { mutableStateOf(initialSelectedIds) }
    val scopeSelectionEnabled = request.enableScopeSelection &&
        request.multiSelect &&
        request.mode == ContactPickerMode.FRIENDS
    var scopeTab by remember(request) { mutableStateOf(ContactPickerScopeTab.FRIENDS) }
    var selectedConversationGroupId by remember(request) { mutableStateOf("") }
    var contactFilter by remember(request) {
        mutableStateOf(
            if (request.mode == ContactPickerMode.FRIENDS && request.enableLabels) {
                ContactPickerFilter.FRIENDS
            } else {
                ContactPickerFilter.ALL
            }
        )
    }
    var selectedLabel by remember(request) { mutableStateOf("") }
    val groupLabels = remember(request) {
        if (request.multiSelect && request.enableGroupLabels && request.mode.supportsGroups()) {
            GroupChatLabelStore.load(context)
        } else {
            emptyList()
        }
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(request) {
        loadContacts(context, request.mode, request.enableLabels) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取联系人失败"
            } else {
                contacts = result
            }
        }
    }
    val conversationGroupFilters = remember(contacts) {
        ConversationGroupPickerSupport.filters(context, contacts.map { it.id })
    }
    val conversationGroupChoices = remember(conversationGroupFilters) {
        listOf(PopupChoice("全部会话", "")) + conversationGroupFilters.map {
            PopupChoice(it.name, it.id)
        }
    }
    LaunchedEffect(conversationGroupFilters) {
        if (selectedConversationGroupId.isNotBlank() &&
            conversationGroupFilters.none { it.id == selectedConversationGroupId }
        ) {
            selectedConversationGroupId = ""
        }
    }
    val selectedConversationIds = remember(conversationGroupFilters, selectedConversationGroupId) {
        conversationGroupFilters.firstOrNull { it.id == selectedConversationGroupId }?.conversationIds
    }
    val scopedContacts = remember(contacts, selectedConversationIds) {
        selectedConversationIds?.let { ids -> contacts.filter { it.id in ids } } ?: contacts
    }
    val labels = remember(scopedContacts) {
        scopedContacts.flatMap { it.labels }
            .distinct()
            .sortedWith(compareBy { it.lowercase(Locale.US) })
    }
    val contactLabelScopes = remember(contacts) {
        contacts.asSequence()
            .filter { !it.group && !it.official }
            .flatMap { contact -> contact.labels.asSequence().map { it to contact.id } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .map { (name, ids) -> ContactPickerScope(name, ids.toSet()) }
            .sortedBy { it.name.lowercase(Locale.US) }
    }
    val availableGroupIds = remember(scopedContacts) {
        scopedContacts.filter { it.group }.map { it.id }.toSet()
    }
    val selectableGroupLabels = remember(groupLabels, availableGroupIds) {
        groupLabels.mapNotNull { label ->
            val ids = label.groupIds.intersect(availableGroupIds)
            label.copy(groupIds = ids).takeIf { ids.isNotEmpty() }
        }
    }
    val showGroupLabels = request.mode == ContactPickerMode.GROUPS ||
        contactFilter == ContactPickerFilter.GROUPS
    LaunchedEffect(contactFilter, labels) {
        if (contactFilter == ContactPickerFilter.LABELS) {
            if (selectedLabel.isBlank() || !labels.contains(selectedLabel)) {
                selectedLabel = labels.firstOrNull().orEmpty()
            }
        } else if (selectedLabel.isNotBlank()) {
            selectedLabel = ""
        }
    }
    val lower = query.trim().lowercase(Locale.US)
    val visibleContacts = remember(
        scopedContacts,
        contactFilter,
        selectedLabel,
        request.enableLabels,
        lower,
        initialSelectedIds
    ) {
        scopedContacts.asSequence().filter {
            when (contactFilter) {
                ContactPickerFilter.FRIENDS -> !it.group && !it.official
                ContactPickerFilter.GROUPS -> it.group
                ContactPickerFilter.OFFICIALS -> it.official
                ContactPickerFilter.LABELS -> request.enableLabels &&
                    selectedLabel.isNotBlank() &&
                    !it.group &&
                    !it.official &&
                    it.labels.contains(selectedLabel)
                ContactPickerFilter.ALL -> true
            }
        }.filter { it.matchesSearch(lower) }
            .toList()
            .selectedFirst { initialSelectedIds.contains(it.id) }
    }
    val visibleLabelScopes = remember(contactLabelScopes, lower, initialSelectedIds) {
        contactLabelScopes.filter {
            lower.isEmpty() || it.name.lowercase(Locale.US).contains(lower)
        }.selectedFirst { scope -> scope.memberIds.any(initialSelectedIds::contains) }
    }
    val visibleConversationGroupScopes = remember(conversationGroupFilters, lower, initialSelectedIds) {
        conversationGroupFilters.filter {
            lower.isEmpty() || it.name.lowercase(Locale.US).contains(lower)
        }.selectedFirst { scope -> scope.conversationIds.any(initialSelectedIds::contains) }
    }
    val visibleContactIds = remember(visibleContacts) {
        visibleContacts.mapTo(linkedSetOf()) { it.id }
    }
    val bottomVisibleIds = remember(
        scopeSelectionEnabled,
        scopeTab,
        visibleContactIds,
        visibleLabelScopes,
        visibleConversationGroupScopes
    ) {
        when {
            !scopeSelectionEnabled || scopeTab == ContactPickerScopeTab.FRIENDS -> visibleContactIds
            scopeTab == ContactPickerScopeTab.LABELS -> {
                visibleLabelScopes.flatMapTo(linkedSetOf()) { it.memberIds }
            }
            else -> {
                visibleConversationGroupScopes.flatMapTo(linkedSetOf()) { it.conversationIds }
            }
        }
    }
    val bottomVisibleAllSelected = bottomVisibleIds.isNotEmpty() && bottomVisibleIds.all { selectedIds.contains(it) }

    PageScaffold(
        title = request.title,
        largeTitle = request.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = if (request.multiSelect) "保存选择" else request.singleConfirmText,
                onPrimaryClick = {
                    val picked = contacts.filter { selectedIds.contains(it.id) }
                    if (picked.isEmpty() && !request.multiSelect) {
                        Toast.makeText(context, "请选择联系人", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(picked)
                    }
                },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (request.multiSelect && bottomVisibleIds.isNotEmpty()) {
                    if (bottomVisibleAllSelected) "取消全选" else "全选"
                } else {
                    null
                },
                onMiddleClick = if (request.multiSelect && bottomVisibleIds.isNotEmpty()) {
                    {
                        selectedIds = if (bottomVisibleAllSelected) {
                            selectedIds - bottomVisibleIds
                        } else {
                            selectedIds + bottomVisibleIds
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
            if (scopeSelectionEnabled) {
                item {
                    SettingsCard {
                        ContactPickerScopeFilterRow(
                            selected = scopeTab,
                            onSelected = {
                                scopeTab = it
                                contactFilter = ContactPickerFilter.FRIENDS
                                query = ""
                            }
                        )
                    }
                }
            } else if (request.mode == ContactPickerMode.FRIENDS && request.enableLabels) {
                item {
                    SettingsCard {
                        FriendContactFilterRow(
                            selected = contactFilter,
                            onSelected = {
                                contactFilter = it
                                query = ""
                            }
                        )
                    }
                }
            } else if (request.mode == ContactPickerMode.BOTH || request.mode == ContactPickerMode.ALL_CHATS) {
                item {
                    SettingsCard {
                        ContactFilterRow(
                            selected = contactFilter,
                            enableLabels = request.enableLabels,
                            enableOfficials = request.mode == ContactPickerMode.ALL_CHATS,
                            onSelected = {
                                contactFilter = it
                                query = ""
                            }
                        )
                    }
                }
            }
            if (!scopeSelectionEnabled && request.enableLabels && contactFilter == ContactPickerFilter.LABELS) {
                item {
                    SettingsCard {
                        ContactLabelFilterRow(
                            labels = labels,
                            selected = selectedLabel,
                            onSelected = {
                                selectedLabel = it
                                query = ""
                            }
                        )
                    }
                }
            }
            if (!scopeSelectionEnabled && conversationGroupChoices.size > 1) {
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "聊天分组",
                            summary = "只显示所选分组及其子分组中的会话",
                            options = conversationGroupChoices,
                            currentValue = selectedConversationGroupId,
                            onValueChanged = {
                                selectedConversationGroupId = it
                                query = ""
                            }
                        )
                    }
                }
            }
            if (showGroupLabels && selectableGroupLabels.isNotEmpty()) {
                item { PickerSectionHeader(text = "群聊标签") }
                item {
                    SettingsCard {
                        selectableGroupLabels.forEachIndexed { index, label ->
                            val selectedCount = label.groupIds.count { selectedIds.contains(it) }
                            GroupChatLabelPickerRow(
                                label = label,
                                selectedCount = selectedCount,
                                onClick = {
                                    selectedIds = if (selectedCount == label.groupIds.size) {
                                        selectedIds - label.groupIds
                                    } else {
                                        selectedIds + label.groupIds
                                    }
                                }
                            )
                            if (index < selectableGroupLabels.lastIndex) InsetDivider()
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    InputRow(
                        title = "搜索",
                        summary = when {
                            scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.LABELS -> "好友标签名称"
                            scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.CONVERSATION_GROUPS -> "聊天分组名称"
                            else -> "昵称 / 群聊备注 / wxid"
                        },
                        value = query,
                        onValueChange = { query = it }
                    )
                }
            }
            item {
                PickerSectionHeader(
                    text = when {
                        scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.LABELS -> {
                            if (visibleLabelScopes.isEmpty()) "好友标签" else "好友标签 · ${visibleLabelScopes.size} 项"
                        }
                        scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.CONVERSATION_GROUPS -> {
                            if (visibleConversationGroupScopes.isEmpty()) "聊天分组" else "聊天分组 · ${visibleConversationGroupScopes.size} 项"
                        }
                        visibleContacts.isNotEmpty() -> "${contactFilter.title} · ${visibleContacts.size} 项"
                        else -> contactFilter.title
                    }
                )
            }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入列表...") } }
                error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.LABELS -> {
                    if (visibleLabelScopes.isEmpty()) {
                        item { SettingsCard { EmptyText("没有匹配的好友标签") } }
                    } else {
                        item {
                            SettingsCard {
                                visibleLabelScopes.forEachIndexed { index, scope ->
                                    val selectedCount = scope.memberIds.count(selectedIds::contains)
                                    ContactPickerScopeRow(
                                        title = scope.name,
                                        memberCount = scope.memberIds.size,
                                        selectedCount = selectedCount,
                                        memberUnit = "位好友",
                                        onClick = {
                                            selectedIds = if (selectedCount == scope.memberIds.size) {
                                                selectedIds - scope.memberIds
                                            } else {
                                                selectedIds + scope.memberIds
                                            }
                                        }
                                    )
                                    if (index < visibleLabelScopes.lastIndex) InsetDivider()
                                }
                            }
                        }
                    }
                }
                scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.CONVERSATION_GROUPS -> {
                    if (visibleConversationGroupScopes.isEmpty()) {
                        item { SettingsCard { EmptyText("没有匹配的聊天分组") } }
                    } else {
                        item {
                            SettingsCard {
                                visibleConversationGroupScopes.forEachIndexed { index, scope ->
                                    val selectedCount = scope.conversationIds.count(selectedIds::contains)
                                    ContactPickerScopeRow(
                                        title = scope.name,
                                        memberCount = scope.conversationIds.size,
                                        selectedCount = selectedCount,
                                        memberUnit = "位好友",
                                        onClick = {
                                            selectedIds = if (selectedCount == scope.conversationIds.size) {
                                                selectedIds - scope.conversationIds
                                            } else {
                                                selectedIds + scope.conversationIds
                                            }
                                        }
                                    )
                                    if (index < visibleConversationGroupScopes.lastIndex) InsetDivider()
                                }
                            }
                        }
                    }
                }
                visibleContacts.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                else -> items(items = visibleContacts, key = { it.id }) { option ->
                    ContactListCard(
                        option = option,
                        selected = selectedIds.contains(option.id),
                        multiSelect = request.multiSelect,
                        onClick = {
                            selectedIds = if (request.multiSelect) {
                                if (selectedIds.contains(option.id)) {
                                    selectedIds - option.id
                                } else {
                                    selectedIds + option.id
                                }
                            } else {
                                setOf(option.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FavoritePickerPage(
    request: FavoritePickerRequest,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val favoriteApi = WeChatApis.media()?.favorites()
    val initialCache = remember(request) { favoriteApi?.cachedList() }
    var loading by remember(request) { mutableStateOf(initialCache?.items.isNullOrEmpty()) }
    var error by remember(request) { mutableStateOf("") }
    var favorites by remember(request) { mutableStateOf(initialCache?.items.orEmpty()) }
    var backgroundLoading by remember(request) { mutableStateOf(false) }
    var query by remember(request) { mutableStateOf("") }
    var filter by remember(request) { mutableStateOf(FavoritePickerFilter.ALL) }
    var preview by remember(request) { mutableStateOf<FavoriteMediaPreview?>(null) }
    val initialSelectedIds = remember(request) { parseFavoriteIds(request.existingValue) }
    var selectedIds by remember(request) { mutableStateOf(initialSelectedIds) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(request) {
        loading = favorites.isEmpty()
        error = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val api = favoriteApi
                    ?: throw IllegalStateException("收藏 API 未就绪")
                if (!api.canList()) {
                    throw IllegalStateException("收藏列表不可用")
                }
                var page = h.Hchat.hooks.api.media.WeChatFavoriteApi.FavoritePage(emptyList(), false)
                for (attempt in 0..2) {
                    page = api.openCachedList()
                    if (page.items.isNotEmpty() || attempt == 2) break
                    delay(350L)
                }
                page
            }
        }
        loading = false
        result.onSuccess {
            favorites = it.items
            if (it.hasMore) {
                backgroundLoading = true
                runCatching {
                    while (true) {
                        val page = withContext(Dispatchers.IO) {
                            favoriteApi?.loadNextPage()
                                ?: throw IllegalStateException("收藏 API 未就绪")
                        }
                        favorites = page.items
                        if (!page.hasMore) break
                        delay(FAVORITE_BACKGROUND_BATCH_DELAY_MS)
                    }
                }.onFailure {
                    error = it.message ?: "后台读取收藏失败"
                }
                backgroundLoading = false
            }
        }
            .onFailure {
                error = it.message ?: "读取收藏失败"
                if (favorites.isEmpty()) favorites = emptyList()
                backgroundLoading = false
            }
    }

    val lower = query.trim().lowercase(Locale.US)
    val visible = favorites.filter { item ->
        filter.matches(item.type) && (
            lower.isEmpty() ||
                item.displayTitle().lowercase(Locale.US).contains(lower) ||
                item.displaySummary().lowercase(Locale.US).contains(lower) ||
                item.tags.any { it.lowercase(Locale.US).contains(lower) }
            )
    }.selectedFirst { initialSelectedIds.contains(it.localId.toString()) }
    val visibleIds = visible.map { it.localId.toString() }.toSet()
    val visibleAllSelected = visibleIds.isNotEmpty() && visibleIds.all { selectedIds.contains(it) }
    PageScaffold(
        title = request.title,
        largeTitle = request.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存选择",
                onPrimaryClick = {
                    if (!request.multiSelect && selectedIds.isEmpty()) {
                        Toast.makeText(context, "请选择收藏", Toast.LENGTH_SHORT).show()
                    } else {
                        request.onValue(formatFavoriteIds(selectedIds, request.delimiter, request.multiSelect))
                        onBack()
                    }
                },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (request.multiSelect && visibleIds.isNotEmpty()) {
                    if (visibleAllSelected) "取消全选" else "全选"
                } else {
                    null
                },
                onMiddleClick = if (request.multiSelect && visibleIds.isNotEmpty()) {
                    {
                        selectedIds = if (visibleAllSelected) {
                            selectedIds - visibleIds
                        } else {
                            selectedIds + visibleIds
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
            item {
                SettingsCard {
                    InputRow("搜索", "标题 / 来源 / 标签", query) { query = it }
                    FavoritePickerFilterRow(selected = filter, onSelected = { filter = it })
                }
            }
            item {
                PickerSectionHeader(
                    text = if (backgroundLoading) {
                        "${filter.label}收藏 · ${visible.size} 项 · 后台加载中（${favorites.size}）"
                    } else {
                        "${filter.label}收藏 · ${visible.size} / ${favorites.size} 项"
                    }
                )
            }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入收藏...") } }
                error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                visible.isEmpty() && !backgroundLoading -> item {
                    SettingsCard {
                        EmptyText(if (query.isBlank()) "没有可选收藏" else "没有匹配收藏")
                    }
                }
                visible.isEmpty() -> item { SettingsCard { EmptyText("正在后台查找收藏...") } }
                else -> visible.forEach { favorite ->
                    item {
                        val id = favorite.localId.toString()
                        FavoriteListCard(
                            item = favorite,
                            selected = selectedIds.contains(id),
                            multiSelect = request.multiSelect,
                            onPreview = { path -> preview = FavoriteMediaPreview(favorite, path) },
                            onClick = {
                                selectedIds = if (request.multiSelect) {
                                    if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
                                } else {
                                    setOf(id)
                                }
                            }
                        )
                    }
                }
            }
            if (backgroundLoading && visible.isNotEmpty()) {
                item { SettingsCard { EmptyText("正在后台加载更多收藏...") } }
            }
        }
    }
    preview?.let { media ->
        FavoriteMediaPreviewDialog(media = media, onClose = { preview = null })
    }
}

@Composable
internal fun GroupMemberPickerPage(
    context: Context,
    request: GroupMemberPickerRequest,
    onBack: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var groupLoading by remember(request) { mutableStateOf(true) }
    var groupError by remember(request) { mutableStateOf("") }
    var groups by remember(request) { mutableStateOf(emptyList<ContactOption>()) }
    var selectedGroup by remember(request) { mutableStateOf<ContactOption?>(null) }
    var memberLoading by remember(request) { mutableStateOf(false) }
    var memberError by remember(request) { mutableStateOf("") }
    var members by remember(request) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember(request) { mutableStateOf("") }
    val initialSelectedEntries = remember(request) {
        parseGroupMemberEntries(request.existingValue).filterTo(linkedSetOf()) { entry ->
            request.allowedGroupIds?.let { entry.substringBefore('/') in it } != false
        }
    }
    var selectedEntries by remember(request) { mutableStateOf<Set<String>>(initialSelectedEntries) }
    var selectionEdited by remember(request) { mutableStateOf(initialSelectedEntries.isNotEmpty()) }
    val groupListState = rememberLazyListState()
    val memberListState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(request) {
        loadContacts(context, ContactPickerMode.GROUPS) { result, throwable ->
            groupLoading = false
            if (throwable != null) {
                groupError = throwable.message ?: "读取群聊失败"
            } else {
                groups = request.allowedGroupIds?.let { allowed ->
                    result.filter { it.id in allowed }
                } ?: result
            }
        }
    }
    LaunchedEffect(selectedGroup?.id) {
        val group = selectedGroup
        if (group == null) {
            members = emptyList()
            memberError = ""
            memberLoading = false
            return@LaunchedEffect
        }
        memberLoading = true
        memberError = ""
        members = emptyList()
        query = ""
        memberListState.scrollToItem(0)
        loadGroupMembers(context, group) { result, throwable ->
            memberLoading = false
            if (throwable != null) {
                memberError = throwable.message ?: "读取群成员失败"
            } else {
                members = result
            }
        }
    }

    val group = selectedGroup
    PageScaffold(
        title = group?.label ?: request.title,
        largeTitle = group?.label ?: request.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = if (group == null && selectedEntries.isEmpty() && !selectionEdited) "选择群聊" else "保存选择",
                onPrimaryClick = {
                    if (selectedGroup == null && selectedEntries.isEmpty() && !selectionEdited) {
                        Toast.makeText(context, "请先选择群聊", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(selectedEntries.toList())
                    }
                },
                secondaryText = if (group == null) "返回" else "重新选群",
                onSecondaryClick = {
                    if (selectedGroup == null) {
                        onBack()
                    } else {
                        selectedGroup = null
                        query = ""
                    }
                },
                middleText = if (selectedEntries.isEmpty()) null else "清空选择",
                onMiddleClick = if (selectedEntries.isEmpty()) null else {
                    {
                        selectedEntries = emptySet()
                        selectionEdited = true
                    }
                }
            )
        }
    ) { padding ->
        val listRoute = group?.let { GroupMemberPickerRoute.Members(it) } ?: GroupMemberPickerRoute.Groups
        SettingsRouteTransition(
            targetState = listRoute,
            label = "GroupMemberPickerTransition",
            depthOf = { it.depth() }
        ) { route ->
            val routeGroup = (route as? GroupMemberPickerRoute.Members)?.group
            val lower = query.trim().lowercase(Locale.US)
            val rows = if (routeGroup == null) groups else members
            val visible = rows.filter {
                it.matchesSearch(lower)
            }.selectedFirst { option ->
                if (routeGroup == null) {
                    initialSelectedEntries.any { it.substringBefore('/') == option.id }
                } else {
                    initialSelectedEntries.contains(groupMemberEntry(routeGroup.id, option.id))
                }
            }
            val routeListState = if (routeGroup == null) groupListState else memberListState
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                state = routeListState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 84.dp
                )
            ) {
                item {
                    SettingsCard {
                        InputRow(
                            title = "搜索",
                            summary = if (routeGroup == null) "群名称 / 群聊备注 / 群号" else "群昵称 / wxid",
                            value = query,
                            onValueChange = { query = it }
                        )
                    }
                }
                item {
                    SmallTitle(
                        modifier = Modifier.padding(top = 10.dp),
                        text = if (routeGroup == null) "群聊" else "群成员"
                    )
                }
                when {
                    routeGroup == null && groupLoading -> item { SettingsCard { EmptyText("正在载入群聊...") } }
                    routeGroup == null && groupError.isNotEmpty() -> item { SettingsCard { EmptyText(groupError) } }
                    routeGroup != null && memberLoading -> item { SettingsCard { EmptyText("正在载入群成员...") } }
                    routeGroup != null && memberError.isNotEmpty() -> item { SettingsCard { EmptyText(memberError) } }
                    visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                    else -> visible.forEach { option ->
                        item {
                            val entry = routeGroup?.let { groupMemberEntry(it.id, option.id) }.orEmpty()
                            ContactListCard(
                                option = option,
                                selected = if (routeGroup == null) false else selectedEntries.contains(entry),
                                multiSelect = routeGroup != null,
                                onClick = {
                                    if (routeGroup == null) {
                                        selectedGroup = option
                                    } else if (selectedEntries.contains(entry)) {
                                        selectedEntries = selectedEntries - entry
                                        selectionEdited = true
                                    } else {
                                        selectedEntries = selectedEntries + entry
                                        selectionEdited = true
                                    }
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
internal fun OptionPickerPage(
    request: OptionPickerRequest,
    onBack: () -> Unit,
    onSelected: (OptionItem) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var selectedValue by remember(request) { mutableStateOf(request.currentValue) }
    PageScaffold(
        title = request.title,
        largeTitle = request.title,
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
            item { SmallTitle(text = "选项") }
            item {
                SettingsCard {
                    request.options.forEachIndexed { index, item ->
                        OptionChoiceRow(
                            item = item,
                            selected = item.value == selectedValue,
                            onClick = {
                                selectedValue = item.value
                                onSelected(item)
                            }
                        )
                        if (index < request.options.lastIndex) InsetDivider()
                    }
                }
            }
        }
    }
}
