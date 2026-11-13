package h.Hchat.ui.miuix

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.model.ContactLabelBean
import h.Hchat.hooks.items.batch.BatchOperation
import h.Hchat.hooks.items.batch.BatchFriendAccessChoice
import h.Hchat.hooks.items.batch.BatchFriendPermissionSelection
import h.Hchat.hooks.items.batch.BatchPermissionToggle
import h.Hchat.hooks.items.batch.BatchTaskManager
import h.Hchat.hooks.items.batch.BatchTaskRequest
import h.Hchat.hooks.items.batch.BatchTaskStatus
import h.Hchat.hooks.items.batch.BatchTaskSubmitResult
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle

internal enum class BatchScope(val label: String) {
    CURRENT("当前聊天"),
    FRIENDS("全部好友"),
    GROUPS("全部群聊"),
    ALL("全部好友与群聊")
}

private val batchScopeChoices = BatchScope.entries.map { PopupChoice(it.label, it.name) }

private val operationsWithPickerControls = setOf(
    BatchOperation.DO_NOT_DISTURB, BatchOperation.PIN, BatchOperation.FRIEND_LABEL,
    BatchOperation.STAR_FRIEND, BatchOperation.FRIEND_PERMISSION, BatchOperation.GROUP_SAVE_TO_CONTACTS,
    BatchOperation.GROUP_NICKNAME, BatchOperation.DELETE_FRIENDS
)

private fun BatchOperation?.destructiveActionLabel(): String? = when (this) {
    BatchOperation.DELETE_CHATS, BatchOperation.DELETE_FRIENDS, BatchOperation.DELETE_INVALID_GROUPS -> "删除"
    BatchOperation.UNFOLLOW_OFFICIAL_ACCOUNTS -> "取消关注"
    BatchOperation.GROUP_LEAVE -> "退出"
    else -> null
}

internal fun batchOperationsFeatureCount(): Int = BatchOperation.entries.size

@Composable
internal fun BatchOperationsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { BatchTaskManager.processInstance() }
    val taskSnapshot by manager.snapshot.collectAsState()
    var phase by remember { mutableStateOf(BatchPage.HOME) }
    var feature by remember { mutableStateOf<BatchOperation?>(null) }
    var enabled by remember { mutableStateOf(true) }
    var deleteFriendClearHistory by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(BatchScope.CURRENT) }
    var friendPermissionSelection by remember { mutableStateOf(BatchFriendPermissionSelection()) }
    var groupNickname by remember { mutableStateOf("") }
    var labelChoices by remember { mutableStateOf(emptyList<ContactLabelBean>()) }
    var labelsLoaded by remember { mutableStateOf(false) }
    var labelsError by remember { mutableStateOf("") }
    var selectedLabelId by remember { mutableStateOf("") }
    var pendingDeleteTargets by remember { mutableStateOf<List<ContactOption>?>(null) }
    var pendingDeleteClearHistory by remember { mutableStateOf(false) }
    var accountIdentity by remember { mutableStateOf("") }
    var accountReady by remember { mutableStateOf(false) }
    val labelLoadGeneration = remember { AtomicLong() }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    DisposableEffect(labelLoadGeneration) {
        onDispose { labelLoadGeneration.incrementAndGet() }
    }

    LaunchedEffect(Unit) {
        accountIdentity = withContext(Dispatchers.IO) {
            runCatching { WeChatApis.account()?.sessionIdentity().orEmpty().trim() }.getOrDefault("")
        }
        accountReady = true
    }
    LaunchedEffect(feature) {
        if (feature == BatchOperation.FRIEND_LABEL && (!labelsLoaded || labelsError.isNotBlank())) {
            val generation = labelLoadGeneration.incrementAndGet()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    WeChatApis.contacts()?.getContactLabelListResult()
                        ?: throw IllegalStateException("好友标签 API 不可用")
                }
            }
            if (generation == labelLoadGeneration.get() && feature == BatchOperation.FRIEND_LABEL) {
                result.fold(
                    onSuccess = { snapshot ->
                        if (!snapshot.success) {
                            labelsError = snapshot.error.ifBlank { "读取好友标签失败" }
                        } else {
                            labelChoices = snapshot.labels.filter {
                                it.labelId.isNotBlank() && it.labelName.isNotBlank()
                            }
                            if (labelChoices.none { it.labelId == selectedLabelId }) {
                                selectedLabelId = labelChoices.firstOrNull()?.labelId.orEmpty()
                            }
                            labelsError = ""
                        }
                    },
                    onFailure = {
                        labelsError = it.message ?: "读取好友标签失败"
                    }
                )
                labelsLoaded = true
            }
        }
    }

    val currentFeature = feature
    val selectedLabelName = labelChoices.firstOrNull { it.labelId == selectedLabelId }?.labelName.orEmpty()
    val latestFeature = rememberUpdatedState(currentFeature)
    val latestScope = rememberUpdatedState(scope)
    val latestEnabled = rememberUpdatedState(enabled)
    val latestDeleteFriendClearHistory = rememberUpdatedState(deleteFriendClearHistory)
    val latestFriendPermissionSelection = rememberUpdatedState(friendPermissionSelection)
    val latestGroupNickname = rememberUpdatedState(groupNickname)
    val latestLabels = rememberUpdatedState(labelChoices)
    val latestLabelsLoaded = rememberUpdatedState(labelsLoaded)
    val latestLabelsError = rememberUpdatedState(labelsError)
    val latestLabelId = rememberUpdatedState(selectedLabelId)
    val latestLabelName = rememberUpdatedState(selectedLabelName)
    val latestConfirmEnabled = rememberUpdatedState(
        accountReady && accountIdentity.isNotBlank() &&
            (currentFeature != BatchOperation.FRIEND_LABEL ||
                (labelsLoaded && labelsError.isBlank() && selectedLabelName.isNotBlank())) &&
            (currentFeature != BatchOperation.FRIEND_PERMISSION ||
                friendPermissionSelection.actions().isNotEmpty()) &&
            (currentFeature != BatchOperation.GROUP_NICKNAME || validGroupNickname(groupNickname))
    )
    var momentsFriend by remember { mutableStateOf<ContactOption?>(null) }
    val momentsFriendPicker = remember {
        ContactPickerRequest(
            title = BatchOperation.LIKE_FRIEND_MOMENTS.pickerTitle,
            mode = ContactPickerMode.FRIENDS,
            multiSelect = false,
            existingValue = "",
            onValue = {},
            enableGroupLabels = false,
            strictFriendsOnly = true,
            singleConfirmText = "查看朋友圈",
            flatBatchSelection = true,
            batchCandidateMode = { BatchCandidateMode.ALL_FRIENDS }
        )
    }

    val pickerRequest = remember(currentFeature) {
        ContactPickerRequest(
            title = currentFeature?.pickerTitle ?: "选择对象",
            mode = when (currentFeature) {
                BatchOperation.FRIEND_LABEL, BatchOperation.DELETE_FRIENDS -> ContactPickerMode.FRIENDS
                BatchOperation.UNFOLLOW_OFFICIAL_ACCOUNTS -> ContactPickerMode.FRIENDS
                BatchOperation.GROUP_LEAVE, BatchOperation.DELETE_INVALID_GROUPS -> ContactPickerMode.GROUPS
                else -> ContactPickerMode.ALL_CHATS
            },
            multiSelect = true,
            existingValue = "",
            onValue = {},
            enableLabels = false,
            enableGroupLabels = false,
            showSelectedCount = true,
            flatBatchSelection = true,
            multiSelectConfirmPrefix = currentFeature.destructiveActionLabel()
                ?: if (currentFeature == BatchOperation.HIDE) "确定不显示" else "确定",
            operationHeader = if (currentFeature in operationsWithPickerControls) {
                {
                    BatchPickerControls(
                        feature = latestFeature.value,
                        enabled = latestEnabled.value,
                        onEnabledChange = { enabled = it },
                        deleteFriendClearHistory = latestDeleteFriendClearHistory.value,
                        onDeleteFriendClearHistoryChange = { deleteFriendClearHistory = it },
                        friendPermissionSelection = latestFriendPermissionSelection.value,
                        onFriendPermissionSelectionChange = { friendPermissionSelection = it },
                        groupNickname = latestGroupNickname.value,
                        onGroupNicknameChange = { groupNickname = it },
                        scope = latestScope.value,
                        onScopeChange = { scope = it },
                        labels = latestLabels.value,
                        labelsLoaded = latestLabelsLoaded.value,
                        labelsError = latestLabelsError.value,
                        labelId = latestLabelId.value,
                        labelName = latestLabelName.value,
                        onLabelChange = { selectedLabelId = it }
                    )
                }
            } else null,
            confirmEnabled = { latestConfirmEnabled.value },
            batchCandidateMode = { candidateMode(feature, scope, enabled) },
            batchLabelFilterId = { selectedLabelId.takeIf { feature == BatchOperation.FRIEND_LABEL && !enabled } },
            batchSelectionResetKey = {
                when (feature) {
                    BatchOperation.DO_NOT_DISTURB, BatchOperation.PIN -> "${scope.name}:$enabled"
                    BatchOperation.FRIEND_LABEL -> "$enabled:$selectedLabelId"
                    BatchOperation.STAR_FRIEND -> enabled.toString()
                    else -> null
                }
            }
        )
    }

    fun returnHome() {
        pendingDeleteTargets = null
        pendingDeleteClearHistory = false
        feature = null
        momentsFriend = null
        phase = BatchPage.HOME
    }

    fun startSelectionTask(picked: List<ContactOption>, clearFriendHistory: Boolean = false) {
        if (picked.isEmpty()) {
            Toast.makeText(context, "请至少选择一个对象", Toast.LENGTH_SHORT).show()
            return
        }
        if (!accountReady || accountIdentity.isBlank()) {
            Toast.makeText(context, "无法确认当前微信账号，任务未启动", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentFeature == BatchOperation.FRIEND_LABEL && selectedLabelId.isBlank()) {
            Toast.makeText(context, "请先选择有效好友标签", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentFeature == BatchOperation.FRIEND_PERMISSION &&
            friendPermissionSelection.actions().isEmpty()
        ) {
            Toast.makeText(context, "请至少选择一项朋友权限修改", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentFeature == BatchOperation.GROUP_NICKNAME && !validGroupNickname(groupNickname)) {
            Toast.makeText(context, "请输入不超过16个汉字的群聊昵称", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedFeature = currentFeature ?: return
        val result = manager.submit(
            BatchTaskRequest(
                operation = selectedFeature,
                targetIds = picked.map { it.id },
                expectedAccount = accountIdentity,
                enabled = enabled,
                labelId = selectedLabelId,
                friendPermissionSelection = friendPermissionSelection,
                groupNickname = groupNickname,
                deleteFriendClearHistory = selectedFeature == BatchOperation.DELETE_FRIENDS && clearFriendHistory,
                groupCleanupReasons = if (selectedFeature == BatchOperation.DELETE_INVALID_GROUPS) {
                    picked.associate { it.id to it.groupCleanupReason }
                } else {
                    emptyMap()
                }
            )
        )
        when (result) {
            BatchTaskSubmitResult.STARTED -> returnHome()
            BatchTaskSubmitResult.BUSY ->
                Toast.makeText(context, "已有批量任务正在执行，请完成后再启动", Toast.LENGTH_SHORT).show()
            BatchTaskSubmitResult.UNAVAILABLE ->
                Toast.makeText(context, "后台任务队列不可用，未启动", Toast.LENGTH_SHORT).show()
            BatchTaskSubmitResult.INVALID ->
                Toast.makeText(context, "选择对象或操作参数无效，未启动", Toast.LENGTH_SHORT).show()
        }
    }

    val destructiveAction = currentFeature.destructiveActionLabel()
    fun submitSelection(picked: List<ContactOption>) {
        if (destructiveAction != null) {
            pendingDeleteTargets = picked.toList()
            pendingDeleteClearHistory = currentFeature == BatchOperation.DELETE_FRIENDS && deleteFriendClearHistory
        } else {
            startSelectionTask(picked)
        }
    }

    BatchDeleteConfirmDialog(
        show = pendingDeleteTargets != null,
        message = when (currentFeature) {
            BatchOperation.DELETE_CHATS -> "将删除所选 ${pendingDeleteTargets?.size ?: 0} 个聊天，并清理对应聊天记录。好友关系与群成员关系会保留。此操作不可恢复。"
            BatchOperation.DELETE_FRIENDS -> if (pendingDeleteClearHistory) {
                "将删除所选 ${pendingDeleteTargets?.size ?: 0} 位好友，并同时清空对应聊天记录。此操作不可恢复。"
            } else {
                "将删除所选 ${pendingDeleteTargets?.size ?: 0} 位好友，不额外清空聊天记录。此操作不可恢复。"
            }
            BatchOperation.UNFOLLOW_OFFICIAL_ACCOUNTS -> "将取消关注所选 ${pendingDeleteTargets?.size ?: 0} 个公众号。以后可重新关注。"
            BatchOperation.GROUP_LEAVE -> "将退出所选 ${pendingDeleteTargets?.size ?: 0} 个群聊。此操作不可恢复。"
            BatchOperation.DELETE_INVALID_GROUPS -> "将移除所选 ${pendingDeleteTargets?.size ?: 0} 个失效群聊的资料与会话，不额外清空聊天记录。此操作不可恢复。"
            else -> ""
        },
        labels = pendingDeleteTargets.orEmpty().map { it.label },
        title = "确认${currentFeature?.pickerTitle.orEmpty()}",
        confirmText = "确认${destructiveAction.orEmpty()}",
        onDismiss = {
            pendingDeleteTargets = null
            pendingDeleteClearHistory = false
        },
        onConfirm = {
            val confirmed = pendingDeleteTargets
            val clearFriendHistory = pendingDeleteClearHistory
            pendingDeleteTargets = null
            pendingDeleteClearHistory = false
            if (!confirmed.isNullOrEmpty()) startSelectionTask(confirmed, clearFriendHistory)
        }
    )

    SettingsRouteTransition(
        targetState = phase,
        label = "BatchOperationsRoute",
        depthOf = { when (it) { BatchPage.HOME -> 0; BatchPage.PICKER -> 1; BatchPage.MOMENTS_PICKER -> 2 } }
    ) { currentPhase ->
    if (currentPhase == BatchPage.MOMENTS_PICKER) {
        BatchMomentsPickerPage(
            accountIdentity = accountIdentity,
            manager = manager,
            friend = momentsFriend,
            onBack = {
                if (currentFeature == BatchOperation.LIKE_FRIEND_MOMENTS) {
                    momentsFriend = null
                    phase = BatchPage.PICKER
                } else returnHome()
            },
            onSubmitted = { returnHome() }
        )
    } else if (currentPhase == BatchPage.PICKER) {
        ContactPickerPage(
            context = context,
            request = if (currentFeature == BatchOperation.LIKE_FRIEND_MOMENTS) momentsFriendPicker else pickerRequest,
            onBack = { returnHome() },
            onConfirm = { picked ->
                if (currentFeature == BatchOperation.LIKE_FRIEND_MOMENTS) {
                    val friend = picked.singleOrNull()
                    if (friend != null && !friend.group && !friend.official && accountIdentity.isNotBlank()) {
                        momentsFriend = friend
                        phase = BatchPage.MOMENTS_PICKER
                    }
                } else submitSelection(picked)
            }
        )
    } else {
    PageScaffold(
        title = "批量操作",
        largeTitle = "批量操作",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = true
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SmallTitle(text = "功能") }
            item {
                SettingsCard {
                    BatchOperation.entries.forEachIndexed { index, operation ->
                        ActionRow(operation.pickerTitle, operationSummary(operation)) {
                            feature = operation
                            enabled = true
                            deleteFriendClearHistory = false
                            scope = BatchScope.CURRENT
                            friendPermissionSelection = BatchFriendPermissionSelection()
                            groupNickname = ""
                            if (operation == BatchOperation.FRIEND_LABEL && labelsLoaded) {
                                selectedLabelId = labelChoices.firstOrNull()?.labelId.orEmpty()
                            }
                            phase = if (operation == BatchOperation.DELETE_OWN_MOMENTS) {
                                BatchPage.MOMENTS_PICKER
                            } else {
                                BatchPage.PICKER
                            }
                        }
                        if (index < BatchOperation.entries.lastIndex) InsetDivider()
                    }
                }
            }
            when (taskSnapshot.status) {
                BatchTaskStatus.RUNNING, BatchTaskStatus.STOPPING -> item {
                    SettingsCard {
                        ActionRow(
                            title = "停止后续任务",
                            summary = if (taskSnapshot.status == BatchTaskStatus.STOPPING) {
                                "已请求停止；已提交操作会完成"
                            } else {
                                "已提交操作会完成，之后停止"
                            }
                        ) {
                            if (!manager.stopLaterTasks()) {
                                Toast.makeText(context, "没有可停止的后续任务", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
    }
    }
}

@Composable
private fun BatchPickerControls(
    feature: BatchOperation?,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    deleteFriendClearHistory: Boolean,
    onDeleteFriendClearHistoryChange: (Boolean) -> Unit,
    friendPermissionSelection: BatchFriendPermissionSelection,
    onFriendPermissionSelectionChange: (BatchFriendPermissionSelection) -> Unit,
    groupNickname: String,
    onGroupNicknameChange: (String) -> Unit,
    scope: BatchScope,
    onScopeChange: (BatchScope) -> Unit,
    labels: List<ContactLabelBean>,
    labelsLoaded: Boolean,
    labelsError: String,
    labelId: String,
    labelName: String,
    onLabelChange: (String) -> Unit
) {
    SettingsCard {
        when (feature) {
            BatchOperation.DELETE_FRIENDS -> PopupChoiceRow(
                title = "同时清空聊天记录",
                summary = if (deleteFriendClearHistory) "开启" else "关闭",
                options = listOf(PopupChoice("开启", "true"), PopupChoice("关闭", "false")),
                currentValue = deleteFriendClearHistory.toString(),
                onValueChanged = { onDeleteFriendClearHistoryChange(it.toBooleanStrictOrNull() ?: deleteFriendClearHistory) }
            )
            BatchOperation.DO_NOT_DISTURB, BatchOperation.PIN -> {
                val pin = feature == BatchOperation.PIN
                val enableLabel = if (pin) "置顶" else "开启"
                val disableLabel = if (pin) "取消置顶" else "关闭"
                PopupChoiceRow(
                    title = if (pin) "置顶操作" else "免打扰",
                    summary = if (enabled) enableLabel else disableLabel,
                    options = listOf(PopupChoice(enableLabel, "true"), PopupChoice(disableLabel, "false")),
                    currentValue = enabled.toString(),
                    onValueChanged = { onEnabledChange(it.toBooleanStrictOrNull() ?: enabled) }
                )
                InsetDivider()
                PopupChoiceRow(
                    title = "选择范围",
                    summary = scope.label,
                    options = batchScopeChoices,
                    currentValue = scope.name,
                    onValueChanged = { value ->
                        onScopeChange(BatchScope.entries.firstOrNull { it.name == value } ?: scope)
                    }
                )
            }
            BatchOperation.FRIEND_LABEL -> {
                when {
                    !labelsLoaded -> EmptyText("正在读取标签…")
                    labelsError.isNotBlank() -> EmptyText(labelsError)
                    labels.isEmpty() -> EmptyText("暂无可用标签")
                    else -> PopupChoiceRow(
                        title = "目标标签",
                        summary = labelName.ifBlank { "选择标签" },
                        options = labels.map { PopupChoice(it.labelName, it.labelId) },
                        currentValue = labelId,
                        onValueChanged = onLabelChange
                    )
                }
                InsetDivider()
                PopupChoiceRow(
                    title = "标签操作",
                    summary = if (enabled) "追加" else "移除",
                    options = listOf(PopupChoice("追加", "true"), PopupChoice("移除", "false")),
                    currentValue = enabled.toString(),
                    onValueChanged = { onEnabledChange(it.toBooleanStrictOrNull() ?: enabled) }
                )
            }
            BatchOperation.STAR_FRIEND -> PopupChoiceRow(
                title = "星标操作",
                summary = if (enabled) "设为星标朋友" else "取消星标朋友",
                options = listOf(PopupChoice("设为星标朋友", "true"), PopupChoice("取消星标朋友", "false")),
                currentValue = enabled.toString(),
                onValueChanged = { onEnabledChange(it.toBooleanStrictOrNull() ?: enabled) }
            )
            BatchOperation.FRIEND_PERMISSION -> {
                PopupChoiceRow(
                    title = "设置朋友权限",
                    summary = friendPermissionSelection.access.label,
                    options = BatchFriendAccessChoice.entries.map { PopupChoice(it.label, it.name) },
                    currentValue = friendPermissionSelection.access.name,
                    onValueChanged = { value ->
                        BatchFriendAccessChoice.entries.firstOrNull { it.name == value }?.let { selected ->
                            onFriendPermissionSelectionChange(friendPermissionSelection.copy(access = selected))
                        }
                    }
                )
                InsetDivider()
                PopupChoiceRow(
                    title = "不让他（她）看",
                    summary = friendPermissionSelection.dontLetSee.label,
                    options = BatchPermissionToggle.entries.map { PopupChoice(it.label, it.name) },
                    currentValue = friendPermissionSelection.dontLetSee.name,
                    onValueChanged = { value ->
                        BatchPermissionToggle.entries.firstOrNull { it.name == value }?.let { selected ->
                            onFriendPermissionSelectionChange(friendPermissionSelection.copy(dontLetSee = selected))
                        }
                    }
                )
                InsetDivider()
                PopupChoiceRow(
                    title = "不看他（她）",
                    summary = friendPermissionSelection.dontSee.label,
                    options = BatchPermissionToggle.entries.map { PopupChoice(it.label, it.name) },
                    currentValue = friendPermissionSelection.dontSee.name,
                    onValueChanged = { value ->
                        BatchPermissionToggle.entries.firstOrNull { it.name == value }?.let { selected ->
                            onFriendPermissionSelectionChange(friendPermissionSelection.copy(dontSee = selected))
                        }
                    }
                )
            }
            BatchOperation.GROUP_SAVE_TO_CONTACTS -> PopupChoiceRow(
                title = "通讯录操作",
                summary = if (enabled) "保存到通讯录" else "取消保存",
                options = listOf(PopupChoice("保存", "true"), PopupChoice("取消保存", "false")),
                currentValue = enabled.toString(),
                onValueChanged = { onEnabledChange(it.toBooleanStrictOrNull() ?: enabled) }
            )
            BatchOperation.GROUP_NICKNAME -> InputRow(
                title = "我在群里的昵称",
                summary = "最多16个汉字",
                value = groupNickname,
                onValueChange = onGroupNicknameChange
            )
            else -> Unit
        }
    }
}

internal fun candidateMode(
    feature: BatchOperation?,
    scope: BatchScope,
    enabled: Boolean
): BatchCandidateMode? = when (feature) {
    BatchOperation.HIDE -> BatchCandidateMode.HOME_CONVERSATIONS
    BatchOperation.DO_NOT_DISTURB -> when (scope) {
        BatchScope.CURRENT -> BatchCandidateMode.CURRENT_FRIENDS_AND_GROUPS
        BatchScope.FRIENDS -> BatchCandidateMode.ALL_FRIENDS
        BatchScope.GROUPS -> BatchCandidateMode.ALL_GROUPS
        BatchScope.ALL -> BatchCandidateMode.ALL_FRIENDS_AND_GROUPS
    }
    BatchOperation.PIN -> when (scope) {
        BatchScope.CURRENT -> BatchCandidateMode.CURRENT_PINNABLE_CONVERSATIONS
        BatchScope.FRIENDS -> BatchCandidateMode.PINNABLE_FRIENDS
        BatchScope.GROUPS -> BatchCandidateMode.PINNABLE_GROUPS
        BatchScope.ALL -> BatchCandidateMode.PINNABLE_FRIENDS_AND_GROUPS
    }
    BatchOperation.FRIEND_LABEL -> if (enabled) {
        BatchCandidateMode.ALL_FRIENDS
    } else {
        BatchCandidateMode.ALL_FRIENDS_WITH_LABEL
    }
    BatchOperation.STAR_FRIEND -> if (enabled) {
        BatchCandidateMode.ALL_FRIENDS
    } else {
        BatchCandidateMode.STARRED_FRIENDS
    }
    BatchOperation.FRIEND_PERMISSION -> BatchCandidateMode.ALL_FRIENDS
    BatchOperation.GROUP_SAVE_TO_CONTACTS, BatchOperation.GROUP_NICKNAME -> BatchCandidateMode.ALL_GROUPS
    BatchOperation.GROUP_LEAVE -> BatchCandidateMode.JOINED_GROUPS
    BatchOperation.DELETE_INVALID_GROUPS -> BatchCandidateMode.INVALID_GROUPS
    BatchOperation.DELETE_CHATS -> BatchCandidateMode.HOME_CONVERSATIONS
    BatchOperation.DELETE_FRIENDS -> BatchCandidateMode.ALL_FRIENDS
    BatchOperation.UNFOLLOW_OFFICIAL_ACCOUNTS -> BatchCandidateMode.FOLLOWED_OFFICIAL_ACCOUNTS
    BatchOperation.DELETE_OWN_MOMENTS, BatchOperation.LIKE_FRIEND_MOMENTS -> null
    null -> null
}

private fun operationSummary(operation: BatchOperation): String = when (operation) {
    BatchOperation.HIDE -> "从微信首页隐藏所选聊天"
    BatchOperation.DO_NOT_DISTURB -> "开启或关闭免打扰"
    BatchOperation.PIN -> "置顶或取消置顶已有聊天"
    BatchOperation.FRIEND_LABEL -> "为所选好友添加或移除标签"
    BatchOperation.STAR_FRIEND -> "为好友设置或取消星标"
    BatchOperation.FRIEND_PERMISSION -> "修改仅聊天和朋友圈权限"
    BatchOperation.GROUP_SAVE_TO_CONTACTS -> "保存群聊到通讯录或取消保存"
    BatchOperation.GROUP_NICKNAME -> "为所选群聊设置我在群里的昵称"
    BatchOperation.GROUP_LEAVE -> "退出所选群聊"
    BatchOperation.DELETE_INVALID_GROUPS -> "清理已退出、被移出或解散的群聊"
    BatchOperation.DELETE_CHATS -> "删除所选聊天及对应记录"
    BatchOperation.DELETE_FRIENDS -> "删除所选好友，可同时清空聊天记录"
    BatchOperation.UNFOLLOW_OFFICIAL_ACCOUNTS -> "取消关注所选公众号"
    BatchOperation.DELETE_OWN_MOMENTS -> "按时间和内容筛选并删除自己的朋友圈"
    BatchOperation.LIKE_FRIEND_MOMENTS -> "选择一位好友，筛选并点赞所选的可见动态"
}

private fun validGroupNickname(value: String): Boolean =
    value.isNotBlank() && value.trim().length <= 16

private enum class BatchPage { HOME, PICKER, MOMENTS_PICKER }
