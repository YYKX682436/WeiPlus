package h.Hchat.ui.miuix

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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
import h.Hchat.hooks.items.batch.BatchTaskManager
import h.Hchat.hooks.items.batch.BatchTaskRequest
import h.Hchat.hooks.items.batch.BatchTaskStatus
import h.Hchat.hooks.items.batch.BatchTaskSubmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle

private enum class BatchScope { CURRENT, ALL }

internal fun batchOperationsFeatureCount(): Int = BatchOperation.entries.size

@Composable
internal fun BatchOperationsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { BatchTaskManager.processInstance() }
    val taskSnapshot by manager.snapshot.collectAsState()
    var phase by remember { mutableStateOf(BatchPage.HOME) }
    var feature by remember { mutableStateOf<BatchOperation?>(null) }
    var enabled by remember { mutableStateOf(true) }
    var scope by remember { mutableStateOf(BatchScope.CURRENT) }
    var labelChoices by remember { mutableStateOf(emptyList<ContactLabelBean>()) }
    var labelsLoaded by remember { mutableStateOf(false) }
    var labelsError by remember { mutableStateOf("") }
    var selectedLabelId by remember { mutableStateOf("") }
    var accountIdentity by remember { mutableStateOf("") }
    var accountReady by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(Unit) {
        accountIdentity = withContext(Dispatchers.IO) {
            runCatching { WeChatApis.account()?.sessionIdentity().orEmpty().trim() }.getOrDefault("")
        }
        accountReady = true
    }
    LaunchedEffect(feature) {
        if (feature == BatchOperation.FRIEND_LABEL && !labelsLoaded) {
            val result = withContext(Dispatchers.IO) {
                runCatching { WeChatApis.contacts()?.getContactLabelList().orEmpty() }
            }
            result.onSuccess { rows ->
                labelChoices = rows.filter { it.labelId.isNotBlank() && it.labelName.isNotBlank() }
                selectedLabelId = labelChoices.firstOrNull()?.labelId.orEmpty()
                labelsError = ""
            }.onFailure {
                labelsError = it.message ?: "读取好友标签失败"
            }
            labelsLoaded = true
        }
    }

    val currentFeature = feature
    val selectedLabelName = labelChoices.firstOrNull { it.labelId == selectedLabelId }?.labelName.orEmpty()
    val latestFeature = rememberUpdatedState(currentFeature)
    val latestScope = rememberUpdatedState(scope)
    val latestEnabled = rememberUpdatedState(enabled)
    val latestLabels = rememberUpdatedState(labelChoices)
    val latestLabelsLoaded = rememberUpdatedState(labelsLoaded)
    val latestLabelsError = rememberUpdatedState(labelsError)
    val latestLabelId = rememberUpdatedState(selectedLabelId)
    val latestLabelName = rememberUpdatedState(selectedLabelName)
    val latestConfirmEnabled = rememberUpdatedState(
        accountReady && accountIdentity.isNotBlank() &&
            (currentFeature != BatchOperation.FRIEND_LABEL ||
                (labelsLoaded && labelsError.isBlank() && selectedLabelName.isNotBlank()))
    )
    val latestCandidateMode = rememberUpdatedState(candidateMode(currentFeature, scope))

    val pickerRequest = remember(currentFeature) {
        ContactPickerRequest(
            title = currentFeature?.title ?: "选择对象",
            mode = when (currentFeature) {
                BatchOperation.FRIEND_LABEL -> ContactPickerMode.FRIENDS
                else -> ContactPickerMode.ALL_CHATS
            },
            multiSelect = true,
            existingValue = "",
            onValue = {},
            enableLabels = false,
            enableGroupLabels = false,
            showSelectedCount = true,
            flatBatchSelection = true,
            hideSearch = true,
            multiSelectConfirmPrefix = if (currentFeature == BatchOperation.HIDE) "确定不显示" else "确定",
            operationHeader = if (currentFeature == BatchOperation.HIDE) null else {
                {
                    BatchPickerControls(
                        feature = latestFeature.value,
                        enabled = latestEnabled.value,
                        onEnabledChange = { enabled = it },
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
            },
            confirmEnabled = { latestConfirmEnabled.value },
            batchCandidateMode = { latestCandidateMode.value }
        )
    }

    fun returnHome() {
        feature = null
        phase = BatchPage.HOME
    }

    fun submitSelection(picked: List<ContactOption>) {
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
        val selectedFeature = currentFeature ?: return
        val result = manager.submit(
            BatchTaskRequest(
                operation = selectedFeature,
                targetIds = picked.map { it.id },
                expectedAccount = accountIdentity,
                enabled = enabled,
                labelId = selectedLabelId
            )
        )
        when (result) {
            BatchTaskSubmitResult.STARTED -> {
                Toast.makeText(context, "已开始后台处理 ${picked.size} 项", Toast.LENGTH_SHORT).show()
                returnHome()
            }
            BatchTaskSubmitResult.BUSY ->
                Toast.makeText(context, "已有批量任务正在执行，请完成后再启动", Toast.LENGTH_SHORT).show()
            BatchTaskSubmitResult.UNAVAILABLE ->
                Toast.makeText(context, "后台任务队列不可用，未启动", Toast.LENGTH_SHORT).show()
            BatchTaskSubmitResult.INVALID ->
                Toast.makeText(context, "选择对象或操作参数无效，未启动", Toast.LENGTH_SHORT).show()
        }
    }

    if (phase == BatchPage.PICKER) {
        ContactPickerPage(
            context = context,
            request = pickerRequest,
            onBack = { returnHome() },
            onConfirm = { submitSelection(it) }
        )
        return
    }

    PageScaffold(
        title = "批量操作",
        largeTitle = "批量操作",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SmallTitle(text = "功能") }
            item {
                SettingsCard {
                    BatchOperation.entries.forEachIndexed { index, operation ->
                        ActionRow(operation.title, operationSummary(operation)) {
                            feature = operation
                            enabled = true
                            scope = BatchScope.CURRENT
                            if (operation == BatchOperation.FRIEND_LABEL && labelsLoaded) {
                                selectedLabelId = labelChoices.firstOrNull()?.labelId.orEmpty()
                            }
                            phase = BatchPage.PICKER
                        }
                        if (index < BatchOperation.entries.lastIndex) InsetDivider()
                    }
                }
            }
            when (taskSnapshot.status) {
                BatchTaskStatus.RUNNING, BatchTaskStatus.STOPPING -> item {
                    SettingsCard {
                        InfoRow(
                            "后台任务",
                            "${taskSnapshot.operation?.title ?: "批量操作"} · ${taskSnapshot.total} 项" +
                                if (taskSnapshot.status == BatchTaskStatus.STOPPING) " · 正在停止" else " · 正在处理"
                        )
                        InsetDivider()
                        ActionRow(
                            title = "停止后续任务",
                            summary = "当前已提交操作完成后停止"
                        ) {
                            if (!manager.stopLaterTasks()) {
                                Toast.makeText(context, "没有可停止的后续任务", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                BatchTaskStatus.COMPLETED, BatchTaskStatus.STOPPED, BatchTaskStatus.FAILED -> {
                    val report = taskSnapshot.report
                    item { SmallTitle(text = "最近一次结果") }
                    item {
                        SettingsCard {
                            InfoRow("操作", taskSnapshot.operation?.title ?: "批量操作")
                            InsetDivider()
                            InfoRow("成功", "${report?.success ?: 0} 项")
                            InsetDivider()
                            InfoRow("失败", "${report?.failed ?: 0} 项")
                            InsetDivider()
                            InfoRow("跳过", "${report?.skipped ?: 0} 项")
                            InsetDivider()
                            InfoRow("已提交／未确认", "${report?.unconfirmed ?: 0} 项")
                            if (taskSnapshot.failure.isNotBlank()) {
                                InsetDivider()
                                InfoRow("任务错误", taskSnapshot.failure)
                            }
                            report?.stopReason?.takeIf { it.isNotBlank() }?.let {
                                InsetDivider()
                                InfoRow("停止原因", it)
                            }
                        }
                    }
                }
                BatchTaskStatus.IDLE -> Unit
            }
        }
    }
}

@Composable
private fun BatchPickerControls(
    feature: BatchOperation?,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
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
            BatchOperation.DO_NOT_DISTURB -> {
                BatchChoiceRow(
                    first = "开启",
                    firstSelected = enabled,
                    second = "关闭",
                    onFirst = { onEnabledChange(true) },
                    onSecond = { onEnabledChange(false) }
                )
                InsetDivider()
                BatchChoiceRow(
                    first = "当前聊天",
                    firstSelected = scope == BatchScope.CURRENT,
                    second = "全部好友与群聊",
                    onFirst = { onScopeChange(BatchScope.CURRENT) },
                    onSecond = { onScopeChange(BatchScope.ALL) }
                )
            }
            BatchOperation.PIN -> {
                BatchChoiceRow(
                    first = "置顶",
                    firstSelected = enabled,
                    second = "取消置顶",
                    onFirst = { onEnabledChange(true) },
                    onSecond = { onEnabledChange(false) }
                )
                InsetDivider()
                BatchChoiceRow(
                    first = "当前聊天",
                    firstSelected = scope == BatchScope.CURRENT,
                    second = "全部好友与群聊",
                    onFirst = { onScopeChange(BatchScope.CURRENT) },
                    onSecond = { onScopeChange(BatchScope.ALL) }
                )
            }
            BatchOperation.FRIEND_LABEL -> {
                when {
                    !labelsLoaded -> EmptyText("正在读取标签…")
                    labelsError.isNotBlank() -> EmptyText(labelsError)
                    labels.isEmpty() -> EmptyText("暂无可用标签")
                    else -> PopupChoiceRow(
                        title = "好友标签",
                        summary = labelName.ifBlank { "选择标签" },
                        options = labels.map { PopupChoice(it.labelName, it.labelId) },
                        currentValue = labelId,
                        onValueChanged = onLabelChange
                    )
                }
                InsetDivider()
                BatchChoiceRow(
                    first = "追加",
                    firstSelected = enabled,
                    second = "移除",
                    onFirst = { onEnabledChange(true) },
                    onSecond = { onEnabledChange(false) }
                )
                InsetDivider()
                BatchChoiceRow(
                    first = "当前聊天中的好友",
                    firstSelected = scope == BatchScope.CURRENT,
                    second = "全部好友",
                    onFirst = { onScopeChange(BatchScope.CURRENT) },
                    onSecond = { onScopeChange(BatchScope.ALL) }
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun BatchChoiceRow(
    first: String,
    firstSelected: Boolean,
    second: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContactFilterChip(first, firstSelected, onFirst)
        ContactFilterChip(second, !firstSelected, onSecond)
    }
}

private fun candidateMode(feature: BatchOperation?, scope: BatchScope): BatchCandidateMode? = when (feature) {
    BatchOperation.HIDE -> BatchCandidateMode.HOME_CONVERSATIONS
    BatchOperation.DO_NOT_DISTURB -> if (scope == BatchScope.CURRENT) {
        BatchCandidateMode.CURRENT_FRIENDS_AND_GROUPS
    } else {
        BatchCandidateMode.ALL_FRIENDS_AND_GROUPS
    }
    BatchOperation.PIN -> if (scope == BatchScope.CURRENT) {
        BatchCandidateMode.HOME_CONVERSATIONS
    } else {
        BatchCandidateMode.PINNABLE_FRIENDS_AND_GROUPS
    }
    BatchOperation.FRIEND_LABEL -> if (scope == BatchScope.CURRENT) {
        BatchCandidateMode.CURRENT_FRIENDS
    } else {
        BatchCandidateMode.ALL_FRIENDS
    }
    null -> null
}

private fun operationSummary(operation: BatchOperation): String = when (operation) {
    BatchOperation.HIDE -> "只显示微信首页当前聊天"
    BatchOperation.DO_NOT_DISTURB -> "开启或关闭当前聊天或全部好友与群聊的免打扰"
    BatchOperation.PIN -> "置顶或取消置顶当前聊天或全部好友与群聊"
    BatchOperation.FRIEND_LABEL -> "为当前聊天中的好友或全部好友追加、移除标签"
}

private enum class BatchPage { HOME, PICKER }
