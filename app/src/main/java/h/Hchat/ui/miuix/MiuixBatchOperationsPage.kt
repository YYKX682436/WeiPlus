package h.Hchat.ui.miuix

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.contact.WeChatContactApi
import h.Hchat.hooks.api.model.ContactLabelBean
import h.Hchat.hooks.items.batch.BatchStatePolicy
import h.Hchat.hooks.items.batch.BatchTaskLifecycle
import h.Hchat.hooks.items.batch.BatchTaskRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class BatchFeature(val title: String, val summary: String) {
    HIDE("批量不显示聊天", "隐藏选中的微信会话，保留聊天记录和关系"),
    DO_NOT_DISTURB("批量设置免打扰", "开启或关闭私聊、群聊的免打扰"),
    PIN("批量设置置顶", "置顶或取消置顶微信会话"),
    FRIEND_LABEL("批量管理好友标签", "追加或移除一个标签，保留其他标签")
}

internal fun batchOperationsFeatureCount(): Int = BatchFeature.entries.size

private enum class BatchPage { HOME, PICKER, RUNNING, RESULTS }

@Composable
internal fun BatchOperationsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(BatchPage.HOME) }
    var feature by remember { mutableStateOf<BatchFeature?>(null) }
    var enabled by remember { mutableStateOf(true) }
    var labelChoices by remember { mutableStateOf(emptyList<ContactLabelBean>()) }
    var labelsLoaded by remember { mutableStateOf(false) }
    var labelsError by remember { mutableStateOf("") }
    var selectedLabelId by remember { mutableStateOf("") }
    var selectedContacts by remember { mutableStateOf(emptyList<ContactOption>()) }
    var accountIdentity by remember { mutableStateOf("") }
    var accountReady by remember { mutableStateOf(false) }
    var taskSession by remember { mutableStateOf<BatchTaskLifecycle.Session?>(null) }
    var stopRequested by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<BatchTaskRunner.Report?>(null) }
    var completedCount by remember { mutableStateOf(0) }
    var activeId by remember { mutableStateOf("") }
    var latestItem by remember { mutableStateOf<BatchTaskRunner.TaskResult?>(null) }
    val taskLifecycle = remember { BatchTaskLifecycle() }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        accountIdentity = withContext(Dispatchers.IO) {
            runCatching { WeChatApis.account()?.sessionIdentity().orEmpty().trim() }.getOrDefault("")
        }
        accountReady = true
    }
    LaunchedEffect(feature) {
        if (feature == BatchFeature.FRIEND_LABEL && !labelsLoaded) {
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

    // Capture the exact session owned by this effect. A stale dispose callback cannot stop a new run.
    DisposableEffect(taskSession) {
        val ownedSession = taskSession
        onDispose { taskLifecycle.stop(ownedSession) }
    }

    val currentFeature = feature
    val selectedLabel = labelChoices.firstOrNull { it.labelId == selectedLabelId }
    val selectedLabelName = selectedLabel?.labelName.orEmpty()
    val latestLabelChoices = rememberUpdatedState(labelChoices)
    val latestLabelsLoaded = rememberUpdatedState(labelsLoaded)
    val latestLabelsError = rememberUpdatedState(labelsError)
    val latestSelectedLabelId = rememberUpdatedState(selectedLabelId)
    val latestSelectedLabelName = rememberUpdatedState(selectedLabelName)
    val latestAccountReady = rememberUpdatedState(accountReady)
    val latestAccountAvailable = rememberUpdatedState(accountIdentity.isNotBlank())
    val latestConfirmEnabled = rememberUpdatedState(
        accountReady && accountIdentity.isNotBlank() &&
            (currentFeature != BatchFeature.FRIEND_LABEL ||
                (labelsLoaded && labelsError.isBlank() && selectedLabel != null))
    )
    val operationSummary = when (currentFeature) {
        BatchFeature.HIDE -> "不显示聊天"
        BatchFeature.DO_NOT_DISTURB -> if (enabled) "开启免打扰" else "关闭免打扰"
        BatchFeature.PIN -> if (enabled) "置顶" else "取消置顶"
        BatchFeature.FRIEND_LABEL -> if (enabled) "追加标签：$selectedLabelName" else "移除标签：$selectedLabelName"
        null -> ""
    }

    val pickerRequest = remember(currentFeature) {
        val nativeHomeList = currentFeature == BatchFeature.HIDE ||
            currentFeature == BatchFeature.DO_NOT_DISTURB || currentFeature == BatchFeature.PIN
        val mode = when (currentFeature) {
            BatchFeature.DO_NOT_DISTURB -> ContactPickerMode.BOTH
            BatchFeature.FRIEND_LABEL -> ContactPickerMode.FRIENDS
            else -> ContactPickerMode.ALL_CHATS
        }
        ContactPickerRequest(
            title = currentFeature?.title ?: "选择对象",
            mode = mode,
            multiSelect = true,
            existingValue = "",
            onValue = {},
            enableLabels = currentFeature == BatchFeature.FRIEND_LABEL,
            enableGroupLabels = false,
            onlyExistingConversations = nativeHomeList,
            strictFriendsOnly = currentFeature == BatchFeature.FRIEND_LABEL,
            strictFriendsAndGroupsOnly = currentFeature == BatchFeature.DO_NOT_DISTURB,
            showSelectedCount = true,
            nativeHomeConversationList = nativeHomeList,
            flatBatchSelection = true,
            multiSelectConfirmPrefix = if (currentFeature == BatchFeature.HIDE) "确定不显示" else "确定",
            operationHeader = {
                BatchPickerOperationHeader(
                    feature = currentFeature,
                    enabled = enabled,
                    onEnabledChange = { enabled = it },
                    labels = latestLabelChoices.value,
                    labelsLoaded = latestLabelsLoaded.value,
                    labelsError = latestLabelsError.value,
                    selectedLabelId = latestSelectedLabelId.value,
                    selectedLabelName = latestSelectedLabelName.value,
                    onLabelChange = { selectedLabelId = it },
                    accountReady = latestAccountReady.value,
                    accountIdentityAvailable = latestAccountAvailable.value
                )
            },
            confirmEnabled = { latestConfirmEnabled.value }
        )
    }

    LaunchedEffect(phase, taskSession) {
        if (phase != BatchPage.RUNNING) return@LaunchedEffect
        val session = taskSession ?: return@LaunchedEffect
        if (!taskLifecycle.isCurrent(session)) return@LaunchedEffect
        val targetIds = selectedContacts.map { it.id }
        val operation = currentFeature
        val labelId = selectedLabelId
        val shouldEnable = enabled
        val expectedAccount = accountIdentity
        report = null

        if (operation == BatchFeature.FRIEND_LABEL) {
            val tagExists = withContext(Dispatchers.IO) {
                WeChatApis.contacts()?.hasContactLabelId(labelId)
            }
            if (tagExists != true) {
                taskLifecycle.finish(session)
                labelsError = if (tagExists == false) "所选标签已不存在，请重新选择" else "无法验证所选标签，未提交操作"
                phase = BatchPage.PICKER
                return@LaunchedEffect
            }
        }

        val result = withContext(Dispatchers.IO) {
            BatchTaskRunner.run(
                targetIds,
                expectedAccount,
                { runCatching { WeChatApis.account()?.sessionIdentity().orEmpty().trim() }.getOrDefault("") },
                session.stopSignal(),
                350L,
                { id, _ -> performBatchAction(operation, id, shouldEnable, labelId, expectedAccount) },
                object : BatchTaskRunner.ProgressListener {
                    override fun onStart(index: Int, id: String) {
                        mainHandler.post {
                            if (phase == BatchPage.RUNNING && taskLifecycle.isCurrent(session)) activeId = id
                        }
                    }

                    override fun onResult(index: Int, id: String, itemResult: BatchTaskRunner.TaskResult) {
                        mainHandler.post {
                            if (phase == BatchPage.RUNNING && taskLifecycle.isCurrent(session)) {
                                completedCount = index
                                activeId = ""
                                latestItem = itemResult
                            }
                        }
                    }
                }
            )
        }
        report = result
        taskLifecycle.finish(session)
        stopRequested = false
        activeId = ""
        phase = BatchPage.RESULTS
    }

    fun returnHome() {
        feature = null
        selectedContacts = emptyList()
        report = null
        taskSession = null
        phase = BatchPage.HOME
    }

    fun startTask(picked: List<ContactOption>) {
        if (picked.isEmpty()) {
            Toast.makeText(context, "请至少选择一个对象", Toast.LENGTH_SHORT).show()
            return
        }
        if (!accountReady || accountIdentity.isBlank()) {
            Toast.makeText(context, "当前微信账号信息不可用", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentFeature == BatchFeature.FRIEND_LABEL && selectedLabel == null) {
            Toast.makeText(context, "请先选择一个有效好友标签", Toast.LENGTH_SHORT).show()
            return
        }
        selectedContacts = picked
        stopRequested = false
        completedCount = 0
        activeId = ""
        latestItem = null
        report = null
        taskSession = taskLifecycle.begin()
        phase = BatchPage.RUNNING
    }

    if (phase == BatchPage.PICKER) {
        ContactPickerPage(
            context = context,
            request = pickerRequest,
            onBack = { returnHome() },
            onConfirm = { startTask(it) }
        )
        return
    }

    val pageTitle = when (phase) {
        BatchPage.HOME -> "批量操作"
        BatchPage.PICKER -> "选择对象"
        BatchPage.RUNNING -> "正在执行"
        BatchPage.RESULTS -> "执行结果"
    }
    val handleBack = {
        if (phase == BatchPage.RUNNING) {
            Toast.makeText(context, "任务执行中；如需结束，请点击“停止后续操作”", Toast.LENGTH_SHORT).show()
        } else if (phase == BatchPage.RESULTS) {
            returnHome()
        } else {
            onBack()
        }
    }

    PageScaffold(
        title = pageTitle,
        largeTitle = pageTitle,
        scrollBehavior = scrollBehavior,
        onBack = handleBack,
        bottomBar = {
            when (phase) {
                BatchPage.HOME -> BottomActionBar("返回", onBack)
                BatchPage.RUNNING -> BottomActionBar(
                    primaryText = if (stopRequested) "正在停止…" else "停止后续操作",
                    onPrimaryClick = {
                        stopRequested = true
                        taskLifecycle.stop(taskSession)
                    },
                    primaryEnabled = !stopRequested
                )
                BatchPage.RESULTS -> BottomActionBar(
                    primaryText = "返回批量操作",
                    onPrimaryClick = { returnHome() }
                )
                BatchPage.PICKER -> Unit
            }
        }
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
            when (phase) {
                BatchPage.HOME -> {
                    item { SmallTitle(text = "功能") }
                    item {
                        SettingsCard {
                            BatchFeature.entries.forEachIndexed { index, item ->
                                ActionRow(item.title, item.summary) {
                                    feature = item
                                    enabled = true
                                    selectedContacts = emptyList()
                                    if (item == BatchFeature.FRIEND_LABEL && labelsLoaded) {
                                        selectedLabelId = labelChoices.firstOrNull()?.labelId.orEmpty()
                                    }
                                    phase = BatchPage.PICKER
                                }
                                if (index < BatchFeature.entries.lastIndex) InsetDivider()
                            }
                        }
                    }
                }
                BatchPage.RUNNING -> {
                    val total = selectedContacts.size
                    val completed = completedCount.coerceAtMost(total)
                    item { SmallTitle(text = "执行进度 · $completed / $total") }
                    item {
                        SettingsCard {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                InfoRow("当前操作", operationSummary)
                                if (activeId.isNotBlank()) {
                                    InsetDivider()
                                    val activeName = selectedContacts.firstOrNull { it.id == activeId }?.label.orEmpty()
                                    InfoRow("当前对象", activeName.ifBlank { activeId })
                                }
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MiuixTheme.colorScheme.secondaryVariant)
                                ) {
                                    val fraction = if (total == 0) 0f else completed.toFloat() / total
                                    Box(
                                        modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                                            .height(8.dp)
                                            .background(MiuixTheme.colorScheme.primary)
                                    )
                                }
                                latestItem?.let { itemResult ->
                                    InsetDivider()
                                    InfoRow("上一项", "${outcomeText(itemResult.outcome)} · ${itemResult.message}")
                                }
                            }
                        }
                    }
                    item {
                        EmptyText(
                            if (stopRequested) "停止请求已收到；当前已提交项目会继续完成，之后不再提交新操作。"
                            else "任务正在后台串行执行。"
                        )
                    }
                }
                BatchPage.RESULTS -> {
                    val finalReport = report
                    item { SmallTitle(text = "执行统计") }
                    item {
                        SettingsCard {
                            InfoRow("成功", "${finalReport?.success ?: 0} 项")
                            InsetDivider()
                            InfoRow("失败", "${finalReport?.failed ?: 0} 项")
                            InsetDivider()
                            InfoRow("跳过", "${finalReport?.skipped ?: 0} 项")
                            InsetDivider()
                            InfoRow("已提交／未确认", "${finalReport?.unconfirmed ?: 0} 项")
                            InsetDivider()
                            InfoRow("已处理", "${finalReport?.attempted ?: 0} / ${finalReport?.total ?: 0}")
                            finalReport?.stopReason?.takeIf { it.isNotBlank() }?.let {
                                InsetDivider()
                                InfoRow("停止原因", it)
                            }
                        }
                    }
                    item { SmallTitle(text = "逐项结果") }
                    items(finalReport?.results.orEmpty(), key = { it.id }) { itemResult ->
                        val targetName = selectedContacts.firstOrNull { it.id == itemResult.id }?.label.orEmpty()
                        SettingsCard {
                            InfoRow(
                                targetName.ifBlank { itemResult.id },
                                "${outcomeText(itemResult.outcome)} · ${itemResult.message}"
                            )
                        }
                    }
                }
                BatchPage.PICKER -> Unit
            }
        }
    }
}

@Composable
private fun BatchPickerOperationHeader(
    feature: BatchFeature?,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    labels: List<ContactLabelBean>,
    labelsLoaded: Boolean,
    labelsError: String,
    selectedLabelId: String,
    selectedLabelName: String,
    onLabelChange: (String) -> Unit,
    accountReady: Boolean,
    accountIdentityAvailable: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsCard {
            when (feature) {
                BatchFeature.HIDE -> {
                    InfoRow("当前操作", "不显示聊天")
                    InsetDivider()
                    InfoRow("聊天记录", "保留")
                }
                BatchFeature.DO_NOT_DISTURB -> {
                    BatchOperationChoices(
                        first = "开启",
                        firstSelected = enabled,
                        second = "关闭",
                        onFirst = { onEnabledChange(true) },
                        onSecond = { onEnabledChange(false) }
                    )
                    InsetDivider()
                    InfoRow("当前操作", if (enabled) "开启免打扰" else "关闭免打扰")
                }
                BatchFeature.PIN -> {
                    BatchOperationChoices(
                        first = "置顶",
                        firstSelected = enabled,
                        second = "取消置顶",
                        onFirst = { onEnabledChange(true) },
                        onSecond = { onEnabledChange(false) }
                    )
                    InsetDivider()
                    InfoRow("当前操作", if (enabled) "置顶" else "取消置顶")
                }
                BatchFeature.FRIEND_LABEL -> {
                    when {
                        !labelsLoaded -> EmptyText("正在读取好友标签…")
                        labelsError.isNotBlank() -> EmptyText(labelsError)
                        labels.isEmpty() -> EmptyText("没有可用的好友标签")
                        else -> PopupChoiceRow(
                            title = "好友标签",
                            summary = selectedLabelName.ifBlank { "选择一个标签" },
                            options = labels.map { PopupChoice(it.labelName, it.labelId) },
                            currentValue = selectedLabelId,
                            onValueChanged = onLabelChange
                        )
                    }
                    InsetDivider()
                    BatchOperationChoices(
                        first = "追加",
                        firstSelected = enabled,
                        second = "移除",
                        onFirst = { onEnabledChange(true) },
                        onSecond = { onEnabledChange(false) }
                    )
                    InsetDivider()
                    InfoRow("当前标签", selectedLabelName.ifBlank { "未选择" })
                    InsetDivider()
                    InfoRow(
                        "当前操作",
                        when {
                            selectedLabelName.isBlank() -> "请选择标签"
                            enabled -> "追加标签：$selectedLabelName"
                            else -> "移除标签：$selectedLabelName"
                        }
                    )
                }
                null -> EmptyText("选择批量功能")
            }
        }
        SettingsCard {
            InfoRow(
                "当前微信账号",
                when {
                    !accountReady -> "正在校验"
                    accountIdentityAvailable -> "已就绪"
                    else -> "不可用"
                }
            )
        }
    }
}

@Composable
private fun BatchOperationChoices(
    first: String,
    firstSelected: Boolean,
    second: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ContactFilterChip(text = first, selected = firstSelected, onClick = onFirst)
        ContactFilterChip(text = second, selected = !firstSelected, onClick = onSecond)
    }
}

private fun performBatchAction(
    feature: BatchFeature?,
    id: String,
    enabled: Boolean,
    labelId: String,
    expectedAccount: String
): BatchTaskRunner.TaskResult {
    val conversations = WeChatApis.conversations()
        ?: return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "微信会话 API 不可用")
    val contacts = WeChatApis.contacts()
    when (feature) {
        BatchFeature.HIDE -> {
            if (!conversations.isBatchOperableConversation(id)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "会话不存在或不可操作")
            }
            if (conversations.getConversationHomeHiddenState(id) == true) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "会话已经隐藏")
            }
            if (!isBatchAccountCurrent(expectedAccount)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "账号已切换，未提交")
            }
            if (!conversations.hideConversationFromHome(id)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "微信原生隐藏入口未提交")
            }
            return when (conversations.getConversationHomeHiddenState(id)) {
                true -> BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "会话已隐藏，聊天记录保留")
                false -> BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "隐藏状态未生效")
                null -> BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "已提交／未确认")
            }
        }
        BatchFeature.DO_NOT_DISTURB -> {
            val isGroup = conversations.isGroupConversation(id)
            if (!isGroup && contacts?.isFriend(id) != true) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "不是有效私聊或群聊")
            }
            if (BatchStatePolicy.alreadySatisfied(conversations.getWechatDoNotDisturbState(id), enabled)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "免打扰已符合目标状态")
            }
            if (!isBatchAccountCurrent(expectedAccount)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "账号已切换，未提交")
            }
            if (!conversations.setWechatDoNotDisturb(id, enabled)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "微信原生免打扰入口未提交")
            }
            waitForStatePropagation(300L)
            return if (BatchStatePolicy.verified(conversations.getWechatDoNotDisturbState(id), enabled)) {
                BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "免打扰状态已生效")
            } else {
                BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "已提交／未确认")
            }
        }
        BatchFeature.PIN -> {
            if (!conversations.isBatchOperableConversation(id)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "会话不存在或不可操作")
            }
            if (BatchStatePolicy.alreadySatisfied(conversations.getWechatPlacedTopState(id), enabled)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "置顶状态已符合目标")
            }
            if (!isBatchAccountCurrent(expectedAccount)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "账号已切换，未提交")
            }
            if (!conversations.setWechatPlacedTop(id, enabled)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "微信原生置顶入口未提交")
            }
            waitForStatePropagation(200L)
            return if (BatchStatePolicy.verified(conversations.getWechatPlacedTopState(id), enabled)) {
                BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "置顶状态已生效")
            } else {
                BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "已提交／未确认")
            }
        }
        BatchFeature.FRIEND_LABEL -> {
            if (contacts?.isFriend(id) != true) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "好友关系已失效")
            }
            if (!isBatchAccountCurrent(expectedAccount)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "账号已切换，未提交")
            }
            return when (contacts.setContactLabelMembershipById(id, labelId, enabled)) {
                WeChatContactApi.ContactLabelMutationResult.ALREADY_SATISFIED ->
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "好友标签已符合目标")
                WeChatContactApi.ContactLabelMutationResult.CONFIRMED ->
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "服务端已接受，标签状态已确认")
                WeChatContactApi.ContactLabelMutationResult.SERVER_ACCEPTED ->
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "服务端已接受，最终状态未确认")
                WeChatContactApi.ContactLabelMutationResult.SUBMITTED_UNCONFIRMED,
                WeChatContactApi.ContactLabelMutationResult.SUBMITTED ->
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "请求已提交，服务端及最终状态未确认")
                WeChatContactApi.ContactLabelMutationResult.SERVER_REJECTED ->
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "服务端拒绝好友标签请求")
                WeChatContactApi.ContactLabelMutationResult.NOT_SUBMITTED ->
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "好友标签请求未提交")
            }
        }
        null -> return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "未选择批量功能")
    }
}

private fun waitForStatePropagation(delayMillis: Long) {
    try {
        Thread.sleep(delayMillis.coerceAtLeast(0L))
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

private fun isBatchAccountCurrent(expectedAccount: String): Boolean {
    if (expectedAccount.isBlank()) return false
    return runCatching {
        WeChatApis.account()?.sessionIdentity().orEmpty().trim() == expectedAccount
    }.getOrDefault(false)
}

private fun outcomeText(outcome: BatchTaskRunner.Outcome): String = when (outcome) {
    BatchTaskRunner.Outcome.SUCCESS -> "成功"
    BatchTaskRunner.Outcome.FAILED -> "失败"
    BatchTaskRunner.Outcome.SKIPPED -> "跳过"
    BatchTaskRunner.Outcome.UNCONFIRMED -> "已提交／未确认"
}
