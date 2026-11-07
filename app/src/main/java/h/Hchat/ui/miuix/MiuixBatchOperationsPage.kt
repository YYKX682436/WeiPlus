package h.Hchat.ui.miuix

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.contact.WeChatContactApi
import h.Hchat.hooks.api.model.ContactLabelBean
import h.Hchat.hooks.items.batch.BatchStatePolicy
import h.Hchat.hooks.items.batch.BatchTaskRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class BatchFeature(val title: String, val summary: String) {
    HIDE("批量不显示聊天", "使用微信原生隐藏会话入口；聊天记录、联系人和群成员关系保留"),
    DO_NOT_DISTURB("批量设置免打扰", "对有效私聊和群聊调用微信原生免打扰入口"),
    PIN("批量设置置顶", "调用微信会话存储的原生置顶或取消置顶方法"),
    FRIEND_LABEL("批量管理好友标签", "为有效好友追加或移除一个标签，并保留其他标签")
}

private enum class BatchPage { HOME, CONFIGURE, PICKER, CONFIRM, RUNNING, RESULTS }

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
    var stopSignal by remember { mutableStateOf<BatchTaskRunner.StopSignal?>(null) }
    var stopRequested by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<BatchTaskRunner.Report?>(null) }
    var progress by remember { mutableStateOf(0 to "") }
    var latestItem by remember { mutableStateOf<BatchTaskRunner.TaskResult?>(null) }
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
    DisposableEffect(stopSignal) {
        onDispose { stopSignal?.stop() }
    }

    val currentFeature = feature
    val label = labelChoices.firstOrNull { it.labelId == selectedLabelId }
    val selectedLabelName = label?.labelName.orEmpty()
    val desiredSummary = when (currentFeature) {
        BatchFeature.DO_NOT_DISTURB -> if (enabled) "开启免打扰" else "关闭免打扰"
        BatchFeature.PIN -> if (enabled) "置顶聊天" else "取消置顶"
        BatchFeature.FRIEND_LABEL -> if (enabled) "追加标签：$selectedLabelName" else "移除标签：$selectedLabelName"
        else -> "不显示会话"
    }
    val operationSummary = when (currentFeature) {
        BatchFeature.HIDE -> "不显示聊天"
        BatchFeature.DO_NOT_DISTURB -> desiredSummary
        BatchFeature.PIN -> desiredSummary
        BatchFeature.FRIEND_LABEL -> desiredSummary
        null -> ""
    }
    val pickerRequest = remember(currentFeature, accountIdentity, selectedContacts) {
        val mode = when (currentFeature) {
            BatchFeature.DO_NOT_DISTURB -> ContactPickerMode.BOTH
            BatchFeature.FRIEND_LABEL -> ContactPickerMode.FRIENDS
            else -> ContactPickerMode.ALL_CHATS
        }
        ContactPickerRequest(
            title = if (currentFeature == BatchFeature.FRIEND_LABEL) "选择好友" else "选择会话",
            mode = mode,
            multiSelect = true,
            existingValue = formatIds(selectedContacts.map { it.id }),
            onValue = {},
            enableLabels = currentFeature == BatchFeature.FRIEND_LABEL,
            enableGroupLabels = false,
            onlyExistingConversations = currentFeature != BatchFeature.FRIEND_LABEL,
            strictFriendsOnly = currentFeature == BatchFeature.FRIEND_LABEL,
            strictFriendsAndGroupsOnly = currentFeature == BatchFeature.DO_NOT_DISTURB,
            showSelectedCount = true
        )
    }

    LaunchedEffect(phase, stopSignal) {
        if (phase != BatchPage.RUNNING) return@LaunchedEffect
        val signal = stopSignal ?: return@LaunchedEffect
        val targetIds = selectedContacts.map { it.id }
        val operation = currentFeature
        val labelName = selectedLabelName
        val shouldEnable = enabled
        val expectedAccount = accountIdentity
        report = null
        val result = withContext(Dispatchers.IO) {
            BatchTaskRunner.run(
                targetIds,
                expectedAccount,
                { runCatching { WeChatApis.account()?.sessionIdentity().orEmpty().trim() }.getOrDefault("") },
                signal,
                350L,
                { id, stop -> performBatchAction(operation, id, shouldEnable, labelName, expectedAccount, stop) },
                object : BatchTaskRunner.ProgressListener {
                    override fun onStart(index: Int, id: String) {
                        mainHandler.post {
                            if (phase == BatchPage.RUNNING) progress = index to id
                        }
                    }

                    override fun onResult(index: Int, id: String, result: BatchTaskRunner.TaskResult) {
                        mainHandler.post {
                            if (phase == BatchPage.RUNNING) {
                                progress = index to id
                                latestItem = result
                            }
                        }
                    }
                }
            )
        }
        report = result
        stopRequested = false
        phase = BatchPage.RESULTS
    }

    if (phase == BatchPage.PICKER) {
        ContactPickerPage(
            context = context,
            request = pickerRequest,
            onBack = { phase = BatchPage.CONFIGURE },
            onConfirm = {
                selectedContacts = it
                phase = BatchPage.CONFIRM
            }
        )
        return
    }

    val pageTitle = when (phase) {
        BatchPage.HOME -> "批量操作"
        BatchPage.CONFIGURE -> currentFeature?.title ?: "批量操作"
        BatchPage.CONFIRM -> "确认批量操作"
        BatchPage.RUNNING -> "正在执行"
        BatchPage.RESULTS -> "执行结果"
        BatchPage.PICKER -> "选择对象"
    }
    PageScaffold(
        title = pageTitle,
        largeTitle = pageTitle,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            when (phase) {
                BatchPage.HOME -> BottomActionBar("返回", onBack)
                BatchPage.CONFIGURE -> BottomActionBar(
                    primaryText = "选择对象",
                    onPrimaryClick = {
                        if (!accountReady || accountIdentity.isBlank()) {
                            Toast.makeText(context, "当前微信账号信息不可用", Toast.LENGTH_SHORT).show()
                        } else if (currentFeature == BatchFeature.FRIEND_LABEL && label == null) {
                            Toast.makeText(context, "请先选择一个有效好友标签", Toast.LENGTH_SHORT).show()
                        } else {
                            phase = BatchPage.PICKER
                        }
                    },
                    secondaryText = "返回",
                    onSecondaryClick = {
                        feature = null
                        phase = BatchPage.HOME
                    }
                )
                BatchPage.CONFIRM -> BottomActionBar(
                    primaryText = "确认并执行",
                    onPrimaryClick = {
                        if (selectedContacts.isEmpty()) {
                            Toast.makeText(context, "请至少选择一个对象", Toast.LENGTH_SHORT).show()
                        } else {
                            val signal = BatchTaskRunner.StopSignal()
                            stopSignal = signal
                            stopRequested = false
                            progress = 0 to ""
                            latestItem = null
                            phase = BatchPage.RUNNING
                        }
                    },
                    secondaryText = "重新选择",
                    onSecondaryClick = { phase = BatchPage.PICKER }
                )
                BatchPage.RUNNING -> BottomActionBar(
                    primaryText = if (stopRequested) "正在停止…" else "停止后续操作",
                    onPrimaryClick = {
                        stopRequested = true
                        stopSignal?.stop()
                    }
                )
                BatchPage.RESULTS -> BottomActionBar(
                    primaryText = "返回批量操作",
                    onPrimaryClick = {
                        stopSignal = null
                        report = null
                        selectedContacts = emptyList()
                        feature = null
                        phase = BatchPage.HOME
                    }
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
                                    phase = BatchPage.CONFIGURE
                                }
                                if (index < BatchFeature.entries.lastIndex) InsetDivider()
                            }
                        }
                    }
                }
                BatchPage.CONFIGURE -> {
                    item { SmallTitle(text = "操作") }
                    item {
                        SettingsCard {
                            when (currentFeature) {
                                BatchFeature.DO_NOT_DISTURB -> PopupChoiceRow(
                                    title = "免打扰状态",
                                    summary = "只选择有效好友和群聊会话",
                                    options = listOf(PopupChoice("开启", "on"), PopupChoice("关闭", "off")),
                                    currentValue = if (enabled) "on" else "off",
                                    onValueChanged = { enabled = it == "on" }
                                )
                                BatchFeature.PIN -> PopupChoiceRow(
                                    title = "置顶状态",
                                    summary = "使用微信原生会话置顶入口",
                                    options = listOf(PopupChoice("置顶", "on"), PopupChoice("取消置顶", "off")),
                                    currentValue = if (enabled) "on" else "off",
                                    onValueChanged = { enabled = it == "on" }
                                )
                                BatchFeature.FRIEND_LABEL -> {
                                    PopupChoiceRow(
                                        title = "标签操作",
                                        summary = "只改动所选标签，其他好友标签保留",
                                        options = listOf(PopupChoice("追加", "on"), PopupChoice("移除", "off")),
                                        currentValue = if (enabled) "on" else "off",
                                        onValueChanged = { enabled = it == "on" }
                                    )
                                    InsetDivider()
                                    when {
                                        !labelsLoaded -> EmptyText("正在读取好友标签…")
                                        labelsError.isNotBlank() -> EmptyText(labelsError)
                                        labelChoices.isEmpty() -> EmptyText("没有可用的好友标签")
                                        else -> PopupChoiceRow(
                                            title = "好友标签",
                                            summary = "选择要追加或移除的标签",
                                            options = labelChoices.map { PopupChoice(it.labelName, it.labelId) },
                                            currentValue = selectedLabelId,
                                            onValueChanged = { selectedLabelId = it }
                                        )
                                    }
                                }
                                else -> EmptyText("该操作不需要额外设置")
                            }
                        }
                    }
                    item {
                        SettingsCard {
                            InfoRow("执行对象范围", when (currentFeature) {
                                BatchFeature.FRIEND_LABEL -> "有效好友"
                                BatchFeature.DO_NOT_DISTURB -> "当前存在的私聊和群聊"
                                else -> "当前存在的可操作会话"
                            })
                            InsetDivider()
                            InfoRow("当前账号", when {
                                !accountReady -> "正在读取"
                                accountIdentity.isBlank() -> "不可用"
                                else -> "已就绪"
                            })
                        }
                    }
                }
                BatchPage.CONFIRM -> {
                    item { SmallTitle(text = "任务确认") }
                    item {
                        SettingsCard {
                            InfoRow("操作", operationSummary)
                            InsetDivider()
                            InfoRow("对象数量", "${selectedContacts.size} 项")
                            InsetDivider()
                            InfoRow("执行方式", "逐项串行；可停止后续任务")
                            if (currentFeature == BatchFeature.FRIEND_LABEL) {
                                InsetDivider()
                                InfoRow("结果说明", "请求提交与最终同步分开统计；该操作会显示已提交／未确认")
                            }
                        }
                    }
                    item { SmallTitle(text = "选中对象") }
                    items(selectedContacts, key = { it.id }) { option ->
                        SettingsCard {
                            InfoRow(option.label, option.id)
                        }
                    }
                }
                BatchPage.RUNNING -> {
                    val completed = progress.first.coerceAtMost(selectedContacts.size)
                    item { SmallTitle(text = "进度 · $completed / ${selectedContacts.size}") }
                    item {
                        SettingsCard {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                InfoRow("操作", operationSummary)
                                val activeId = progress.second
                                val activeName = selectedContacts.firstOrNull { it.id == activeId }?.label.orEmpty()
                                if (activeId.isNotBlank()) {
                                    InsetDivider()
                                    InfoRow("当前对象", activeName.ifBlank { activeId })
                                }
                                val fraction = if (selectedContacts.isEmpty()) 0f else completed.toFloat() / selectedContacts.size
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MiuixTheme.colorScheme.secondaryVariant)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                                            .height(8.dp)
                                            .background(MiuixTheme.colorScheme.primary)
                                    )
                                }
                                latestItem?.let { itemResult ->
                                    InsetDivider()
                                    InfoRow("上一项", outcomeText(itemResult.outcome) + " · " + itemResult.message)
                                }
                            }
                        }
                    }
                    item { EmptyText(if (stopRequested) "停止请求已收到，当前项目结束后将停止后续提交。" else "任务正在后台串行执行。") }
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

private fun performBatchAction(
    feature: BatchFeature?,
    id: String,
    enabled: Boolean,
    labelName: String,
    expectedAccount: String,
    stopSignal: BatchTaskRunner.StopSignal
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
            if (!stopSignal.awaitNext(300L)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "已提交／未确认；停止请求期间未复核")
            }
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
            if (!stopSignal.awaitNext(200L)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "已提交／未确认；停止请求期间未复核")
            }
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
            val currentMembership = contacts.getContactLabelMembershipState(id, labelName)
                ?: return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "好友标签状态不可读取")
            if (BatchStatePolicy.alreadySatisfied(currentMembership, enabled)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "好友标签已符合目标")
            }
            if (!isBatchAccountCurrent(expectedAccount)) {
                return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "账号已切换，未提交")
            }
            return when (contacts.setContactLabelMembership(id, labelName, enabled)) {
                WeChatContactApi.ContactLabelMutationResult.ALREADY_SATISFIED ->
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SKIPPED, "好友标签已符合目标")
                WeChatContactApi.ContactLabelMutationResult.SUBMITTED ->
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "已提交／未确认")
                WeChatContactApi.ContactLabelMutationResult.NOT_SUBMITTED ->
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "原生好友标签请求未提交")
            }
        }
        null -> return BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "未选择批量功能")
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
