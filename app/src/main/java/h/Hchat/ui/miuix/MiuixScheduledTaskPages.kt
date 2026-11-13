package h.Hchat.ui.miuix

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.fakescancamera.FakeScanCameraSettings
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskContentItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskRuntimeCoordinator
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import h.Hchat.hooks.items.selectedmessages.SelectedMessageSnapshot
import h.Hchat.hooks.items.selectedmessages.SelectedMessagesRuntimeCoordinator
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

@Composable
internal fun ScheduledTaskMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { ScheduledTaskSettings(context) }
    val sp = remember { HchatStorage.preferences(context, ScheduledTaskSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<ScheduledTaskRoute>(ScheduledTaskRoute.Main) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                ScheduledTaskSettings.KEY_ENABLE,
                ScheduledTaskSettings.DEFAULT_ENABLE
            )
        )
    }
    var tasks by remember { mutableStateOf(settings.tasks()) }
    var draft by remember { mutableStateOf(ScheduledTaskSettings.newDraft()) }
    var contentSession by remember { mutableStateOf(ScheduledContentSession(emptyList(), false)) }
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var lastSavedDraft by remember { mutableStateOf<ScheduledTaskItem?>(null) }
    var draftError by remember { mutableStateOf<String?>(null) }
    var showDiscardPending by remember { mutableStateOf(false) }
    val mainListState = rememberLazyListState()
    val editorListState = rememberLazyListState()
    val editorScope = rememberCoroutineScope()

    fun reloadTasks() {
        tasks = settings.tasks()
    }

    fun saveDraft(closeAfterSave: Boolean = true, showError: Boolean = true): Boolean {
        var target = ScheduledTaskSettings.normalizeForSave(draft)
        val now = System.currentTimeMillis()
        val oldDraft = lastSavedDraft
        val scheduleChanged = scheduledTaskScheduleChanged(oldDraft, draft)
        val planTimes = ScheduledTaskSettings.normalizedPlanTimes(target)
        val error = scheduledTaskValidationError(draft, oldDraft, now)
        if (error != null) {
            draftError = error
            if (showError) Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            return false
        }
        if (scheduleChanged && target.repeatType != ScheduledTaskSettings.REPEAT_NONE) {
            val nextPlanTimes = planTimes.map { planTime ->
                ScheduledTaskSettings.resolveNextPlanTime(
                    planTime,
                    target.repeatType,
                    target.repeatDays,
                    now
                )
            }.filter { it > 0L }
            if (nextPlanTimes.isEmpty()) {
                draftError = "无法计算下次执行时间"
                if (showError) Toast.makeText(context, draftError, Toast.LENGTH_SHORT).show()
                return false
            }
            target = updateScheduledTaskPlanTimes(target, nextPlanTimes)
        }
        val latestTasks = settings.tasks()
        val latest = latestTasks.firstOrNull { it.id == target.id }
        if (editingTaskId != null && latest == null) {
            draftError = "任务已不存在"
            if (showError) Toast.makeText(context, draftError, Toast.LENGTH_SHORT).show()
            return false
        }
        if (latest != null) {
            target = target.copy(
                planTime = if (scheduleChanged) target.planTime else latest.planTime,
                planTimes = if (scheduleChanged) target.planTimes else latest.planTimes,
                status = latest.status,
                lastExecutedTime = latest.lastExecutedTime,
                lastSuccessCount = latest.lastSuccessCount,
                lastFailCount = latest.lastFailCount
            )
        }
        settings.saveTasks(latestTasks.filterNot { it.id == target.id } + target)
        reloadTasks()
        if (scheduleChanged) ScheduledTaskRuntimeCoordinator.refreshTask(target.id)
        lastSavedDraft = draft
        draftError = null
        if (closeAfterSave) {
            route = ScheduledTaskRoute.Main
        }
        return true
    }

    fun updateDraft(next: ScheduledTaskItem) {
        draft = next
        if (editingTaskId != null && !saveDraft(closeAfterSave = false, showError = false)) {
            val previous = lastSavedDraft ?: return
            val independent = scheduledTaskIndependentEdits(previous, next)
            if (independent != previous) {
                val error = draftError
                draft = independent
                saveDraft(closeAfterSave = false, showError = false)
                draft = next
                draftError = error
            }
        }
    }

    fun updateContent(key: String, item: ScheduledTaskContentItem) {
        if (!contentSession.update(key, item)) return
        if (contentSession.find(key)?.pending == false) updateDraft(contentSession.applyTo(draft))
    }

    fun commitContent(key: String) {
        val entry = contentSession.find(key) ?: return
        if (entry.item.value.isBlank()) {
            Toast.makeText(context, "请先填写或选择内容", Toast.LENGTH_SHORT).show()
            return
        }
        val previous = draft
        draft = contentSession.applyTo(draft, includePendingKey = key)
        if (saveDraft(closeAfterSave = false, showError = true)) {
            contentSession.markCommitted(key)
        } else {
            draft = previous
        }
    }

    SettingsRouteTransition(
        targetState = route,
        label = "ScheduledTaskRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            ScheduledTaskRoute.Main -> {
                ScheduledTaskMainPage(
                    provider = provider,
                    enabled = enabled,
                    tasks = tasks,
                    listState = mainListState,
                    onBack = onBack,
                    onEnabledChange = { checked ->
                        enabled = checked
                        sp.edit().putBoolean(ScheduledTaskSettings.KEY_ENABLE, checked).apply()
                        ScheduledTaskRuntimeCoordinator.reload()
                    },
                    onAdd = {
                        editorScope.launch { editorListState.scrollToItem(0) }
                        editingTaskId = null
                        draft = ScheduledTaskSettings.newDraft()
                        contentSession = ScheduledContentSession(emptyList(), false)
                        lastSavedDraft = null
                        draftError = null
                        route = ScheduledTaskRoute.Editor
                    },
                    onEdit = { task ->
                        editorScope.launch { editorListState.scrollToItem(0) }
                        editingTaskId = task.id
                        draft = task
                        contentSession = ScheduledContentSession(scheduledTaskEditableItems(task), true)
                        lastSavedDraft = task
                        draftError = null
                        route = ScheduledTaskRoute.Editor
                    }
                )
            }
            ScheduledTaskRoute.Editor -> {
                ScheduledTaskEditorPage(
                    context = context,
                    task = draft,
                    contentSession = contentSession,
                    isEditing = editingTaskId != null,
                    listState = editorListState,
                    onBack = {
                        if (contentSession.entries.any { it.pending }) showDiscardPending = true
                        else route = ScheduledTaskRoute.Main
                    },
                    onTaskChange = { next ->
                        if (next.targetType != draft.targetType && next.targetType == ScheduledTaskSettings.TARGET_CHAT) {
                            contentSession = ScheduledContentSession(scheduledTaskEditableItems(next), editingTaskId != null)
                        }
                        updateDraft(next)
                    },
                    onAddContent = { type ->
                        contentSession.add(type)
                        if (editingTaskId == null) updateDraft(contentSession.applyTo(draft))
                    },
                    onContentChange = ::updateContent,
                    onCommitContent = ::commitContent,
                    onMoveContent = { key, offset ->
                        if (contentSession.move(key, offset)) {
                            if (editingTaskId == null) {
                                updateDraft(contentSession.applyTo(draft))
                            } else {
                                val previous = draft
                                draft = contentSession.applyTo(draft)
                                if (!saveDraft(closeAfterSave = false, showError = true)) {
                                    contentSession.move(key, -offset)
                                    draft = previous
                                }
                            }
                        }
                    },
                    onRemoveContent = { key ->
                        val entry = contentSession.find(key)
                        if (entry != null && !entry.pending && editingTaskId != null && contentSession.entries.count { !it.pending } <= 1) {
                            Toast.makeText(context, "已有任务需要保留至少一条内容；可删除整个任务", Toast.LENGTH_LONG).show()
                        } else if (entry != null && !entry.pending && editingTaskId != null) {
                            val previous = draft
                            draft = contentSession.applyTo(draft, excludeKey = key)
                            if (saveDraft(closeAfterSave = false, showError = true)) contentSession.remove(key) else draft = previous
                        } else if (contentSession.remove(key) && entry?.pending == false) {
                            updateDraft(contentSession.applyTo(draft))
                        }
                    },
                    validationError = draftError,
                    onPickTargets = { route = ScheduledTaskRoute.ContactPicker },
                    onPickFiles = { itemKey, itemType ->
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            ScheduledTaskFilePickerBridge.launch(activity, itemType) { paths ->
                                val wasPending = contentSession.find(itemKey)?.pending
                                if (contentSession.setFiles(itemKey, itemType, paths) && wasPending == false) {
                                    updateDraft(contentSession.applyTo(draft))
                                }
                            }
                        }
                    },
                    onPickMomentsMedia = { itemType ->
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            ScheduledTaskFilePickerBridge.launch(activity, itemType) { paths ->
                                if (itemType == ScheduledTaskSettings.TYPE_IMAGE && paths.size > 9) {
                                    Toast.makeText(context, "朋友圈最多保留前 9 张图片", Toast.LENGTH_SHORT).show()
                                } else if (itemType == ScheduledTaskSettings.TYPE_VIDEO && paths.size > 1) {
                                    Toast.makeText(context, "朋友圈只保留第 1 个视频", Toast.LENGTH_SHORT).show()
                                }
                                updateDraft(updateScheduledTaskMomentsMedia(draft, itemType, paths))
                            }
                        }
                    },
                    onPickFavorite = { itemKey ->
                        contentSession.find(itemKey)?.item?.let { item ->
                            route = ScheduledTaskRoute.FavoritePicker(
                                0,
                                FavoritePickerRequest(
                                    title = scheduledTaskPickerTitle(item.type),
                                    existingValue = item.value,
                                    onValue = { value ->
                                        contentSession.find(itemKey)?.takeIf { it.item.type == item.type }?.let { current ->
                                            updateContent(itemKey, current.item.copy(value = value))
                                        }
                                    },
                                    multiSelect = false,
                                    delimiter = "|"
                                )
                            )
                        }
                    },
                    onSave = { saveDraft() },
                    onDelete = if (editingTaskId == null) {
                        null
                    } else {
                        {
                            settings.saveTasks(settings.tasks().filterNot { it.id == draft.id })
                            reloadTasks()
                            ScheduledTaskRuntimeCoordinator.refreshTask(draft.id)
                            route = ScheduledTaskRoute.Main
                            Toast.makeText(context, "任务已删除", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExecuteNow = if (editingTaskId == null) {
                        null
                    } else {
                        {
                            if (saveDraft(closeAfterSave = false)) {
                                if (ScheduledTaskRuntimeCoordinator.executeNow(draft.id)) {
                                    Toast.makeText(context, "任务已加入立即执行队列", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "立即执行失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
            ScheduledTaskRoute.ContactPicker -> {
                val official = draft.sendChannel == ScheduledTaskSettings.SEND_CHANNEL_OFFICIAL
                ContactPickerPage(
                    context = context,
                    request = ContactPickerRequest(
                        title = if (official) "选择原生群发好友" else "选择发送对象",
                        mode = if (official) ContactPickerMode.FRIENDS else ContactPickerMode.ALL_CHATS,
                        multiSelect = true,
                        existingValue = formatIds(draft.targetIds),
                        onValue = {},
                        enableLabels = true
                    ),
                    onBack = { route = ScheduledTaskRoute.Editor },
                    onSelectionChange = { selected ->
                        updateDraft(draft.copy(targetIds = selected.toList()))
                    }
                )
            }
            is ScheduledTaskRoute.FavoritePicker -> {
                FavoritePickerPage(
                    request = currentRoute.request,
                    onBack = { route = ScheduledTaskRoute.Editor }
                )
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDiscardPending,
        title = "放弃尚未加入的内容？",
        message = "返回会放弃尚未加入的内容草稿。",
        labels = emptyList(),
        confirmText = "放弃并返回",
        onDismiss = { showDiscardPending = false },
        onConfirm = { showDiscardPending = false; route = ScheduledTaskRoute.Main }
    )
}

internal fun scheduledTaskScheduleChanged(previous: ScheduledTaskItem?, current: ScheduledTaskItem): Boolean =
    previous == null ||
        ScheduledTaskSettings.normalizedPlanTimes(previous) != ScheduledTaskSettings.normalizedPlanTimes(current) ||
        previous.repeatType != current.repeatType || previous.repeatDays != current.repeatDays

internal fun scheduledTaskValidationError(draft: ScheduledTaskItem, previous: ScheduledTaskItem?, now: Long): String? {
    val target = ScheduledTaskSettings.normalizeForSave(draft)
    val items = ScheduledTaskSettings.normalizedItems(target)
    val channelError = if (target.targetType == ScheduledTaskSettings.TARGET_CHAT &&
        target.sendChannel == ScheduledTaskSettings.SEND_CHANNEL_OFFICIAL
    ) {
        if (items.all { it.type == ScheduledTaskSettings.TYPE_SELECTED_MESSAGE }) {
            val snapshots = items.mapNotNull { SelectedMessageSnapshot.decode(it.value) }
            if (snapshots.size != items.size) "定时转发消息快照无效"
            else SelectedMessagesRuntimeCoordinator.snapshotValidationError(target.sendChannel, snapshots)
        } else SelectedMessagesRuntimeCoordinator.validationError(target.sendChannel, items)
    } else null
    val planTimes = ScheduledTaskSettings.normalizedPlanTimes(target)
    return when {
        target.targetType == ScheduledTaskSettings.TARGET_CHAT && target.targetIds.isEmpty() -> "请选择发送对象"
        scheduledTaskHasIncompleteContent(draft) -> "请填写或移除未完成的发送内容；保留上次有效配置"
        ScheduledTaskSettings.momentsValidationError(target) != null -> ScheduledTaskSettings.momentsValidationError(target)
        items.isEmpty() -> "请配置发送内容"
        channelError != null -> channelError
        planTimes.isEmpty() -> "请添加计划时间"
        target.repeatType == ScheduledTaskSettings.REPEAT_WEEKLY && target.repeatDays.isEmpty() -> "请选择每周重复日期"
        scheduledTaskScheduleChanged(previous, draft) && target.repeatType == ScheduledTaskSettings.REPEAT_NONE && planTimes.any { it <= now } -> "计划时间需要晚于当前时间"
        else -> null
    }
}

internal fun scheduledTaskHasIncompleteContent(task: ScheduledTaskItem): Boolean =
    task.targetType == ScheduledTaskSettings.TARGET_CHAT && task.items.any { it.value.isBlank() }

internal fun scheduledTaskIndependentEdits(previous: ScheduledTaskItem, current: ScheduledTaskItem): ScheduledTaskItem =
    previous.copy(
        remark = current.remark,
        intervalSeconds = current.intervalSeconds,
        mediaIntervalSeconds = current.mediaIntervalSeconds,
        sendOnTimeout = current.sendOnTimeout
    )

@Composable
private fun ScheduledTaskMainPage(
    provider: FeatureSettingsProvider,
    enabled: Boolean,
    tasks: List<ScheduledTaskItem>,
    listState: LazyListState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ScheduledTaskItem) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "发送计划") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "定时任务",
                        summary = "开启后按计划时间发送聊天消息或发布朋友圈"
                    ) { onEnabledChange(it) }
                }
            }
            item { SettingsSectionTitle(text = "任务列表") }
            item {
                SettingsCard {
                    tasks.forEach { task ->
                        ActionRow(scheduledTaskTitle(task), scheduledTaskSummary(task)) { onEdit(task) }
                        InsetDivider()
                    }
                    AddActionRow("新增任务", onAdd)
                }
            }
        }
    }
}

@Composable
private fun ScheduledTaskEditorPage(
    context: Context,
    task: ScheduledTaskItem,
    contentSession: ScheduledContentSession,
    isEditing: Boolean,
    listState: LazyListState,
    onBack: () -> Unit,
    onTaskChange: (ScheduledTaskItem) -> Unit,
    onAddContent: (Int) -> Unit,
    onContentChange: (String, ScheduledTaskContentItem) -> Unit,
    onCommitContent: (String) -> Unit,
    onMoveContent: (String, Int) -> Unit,
    onRemoveContent: (String) -> Unit,
    validationError: String?,
    onPickTargets: () -> Unit,
    onPickFiles: (String, Int) -> Unit,
    onPickMomentsMedia: (Int) -> Unit,
    onPickFavorite: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onExecuteNow: (() -> Unit)?
) {
    val scrollBehavior = MiuixScrollBehavior()
    var intervalText by remember(task.id) { mutableStateOf(task.intervalSeconds.toString()) }
    var mediaIntervalText by remember(task.id) { mutableStateOf(task.mediaIntervalSeconds.toString()) }
    val intervalsValid = intervalText.toIntOrNull()?.let { it in 0..3600 } == true &&
        mediaIntervalText.toIntOrNull()?.let { it in 0..3600 } == true
    val scrollOffset = with(LocalDensity.current) { 120.dp.roundToPx() }
    LaunchedEffect(contentSession.scrollRequest) {
        val key = contentSession.scrollRequest ?: return@LaunchedEffect
        val index = contentSession.entries.indexOfFirst { it.key == key }
        if (index >= 0 && task.targetType == ScheduledTaskSettings.TARGET_CHAT) {
            withFrameNanos { }
            listState.animateScrollToItem((if (validationError == null) 0 else 1) + 3 + index, -scrollOffset)
        }
        contentSession.consumeScrollRequest(key)
    }
    fun addPlanTime() {
        val activity = context as? Activity
        if (activity == null) {
            Toast.makeText(context, "当前页面无法打开时间选择器", Toast.LENGTH_SHORT).show()
            return
        }
        val planTimes = ScheduledTaskSettings.normalizedPlanTimes(task)
        val initialTime = planTimes.lastOrNull()?.let { it + 5 * 60 * 1000L }
            ?: (System.currentTimeMillis() + 5 * 60 * 1000L)
        showScheduledTaskDateTimePicker(activity, initialTime) { picked ->
            onTaskChange(updateScheduledTaskPlanTimes(task, planTimes + picked))
        }
    }
    PageScaffold(
        title = if (isEditing) "编辑任务" else "新增任务",
        largeTitle = if (isEditing) "编辑任务" else "新增任务",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            if (validationError != null) item {
                SettingsCard { InfoRow("待完善", validationError) }
            }
            item { SettingsSectionTitle(text = "发送对象") }
            item {
                SettingsCard {
                    InputRow(
                        title = "任务备注",
                        summary = "用于在任务列表区分任务，可留空",
                        value = task.remark,
                        onValueChange = { onTaskChange(task.copy(remark = it)) }
                    )
                    InsetDivider()
                    if (scheduledTaskEditableItems(task).any { it.type == ScheduledTaskSettings.TYPE_SELECTED_MESSAGE }) {
                        InfoRow(label = "发送到", value = "聊天")
                    } else {
                        PopupChoiceRow(
                            title = "发送到",
                            summary = scheduledTaskTargetTypeLabel(task.targetType),
                            options = scheduledTaskTargetTypeChoices(),
                            currentValue = task.targetType.toString(),
                            onValueChanged = { value ->
                                val targetType = value.toIntOrNull() ?: ScheduledTaskSettings.TARGET_CHAT
                                onTaskChange(updateScheduledTaskTargetType(task, targetType))
                            }
                        )
                    }
                    if (task.targetType == ScheduledTaskSettings.TARGET_CHAT) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "发送通道",
                            summary = scheduledTaskSendChannelLabel(task.sendChannel),
                            options = scheduledTaskSendChannelChoices(),
                            currentValue = task.sendChannel.toString(),
                            onValueChanged = { value ->
                                val channel = value.toIntOrNull()
                                    ?.takeIf { ScheduledTaskSettings.sendChannelIsValid(it) }
                                    ?: ScheduledTaskSettings.SEND_CHANNEL_MODULE
                                onTaskChange(
                                    task.copy(
                                        sendChannel = channel,
                                        targetIds = if (channel == task.sendChannel) {
                                            task.targetIds
                                        } else {
                                            emptyList()
                                        }
                                    )
                                )
                            }
                        )
                        InsetDivider()
                        ActionRow(
                            if (task.sendChannel == ScheduledTaskSettings.SEND_CHANNEL_OFFICIAL) {
                                "选择好友"
                            } else {
                                "选择聊天"
                            },
                            scheduledTaskTargetSummary(task.targetIds)
                        ) {
                            onPickTargets()
                        }
                    }
                }
            }
            if (task.targetType == ScheduledTaskSettings.TARGET_MOMENTS) {
                item { SettingsSectionTitle(text = "朋友圈内容") }
                item { SettingsCard {
                        PopupChoiceRow(
                            title = "朋友圈类型",
                            summary = scheduledTaskMomentsTypeLabel(task.momentsType),
                            options = scheduledTaskMomentsTypeChoices(),
                            currentValue = task.momentsType.toString(),
                            onValueChanged = { value ->
                                val type = value.toIntOrNull() ?: ScheduledTaskSettings.MOMENTS_TEXT
                                onTaskChange(updateScheduledTaskMomentsType(task, type))
                            }
                        )
                        if (scheduledTaskMomentsUsesText(task.momentsType)) {
                            InsetDivider()
                            VariableInputRow(
                                title = "朋友圈文字",
                                summary = "输入要发布的朋友圈文案",
                                value = scheduledTaskMomentsText(task),
                                variables = emptyList(),
                                minLines = 3
                            ) {
                                onTaskChange(updateScheduledTaskMomentsText(task, it))
                            }
                        }
                        val mediaType = scheduledTaskMomentsMediaType(task.momentsType)
                        if (mediaType != null) {
                            val paths = scheduledTaskMomentsMediaPaths(task, mediaType)
                            InsetDivider()
                            ActionRow(
                                if (mediaType == ScheduledTaskSettings.TYPE_IMAGE) "选择图片" else "选择视频",
                                scheduledTaskMediaSummary(mediaType, paths)
                            ) {
                                onPickMomentsMedia(mediaType)
                            }
                            if (paths.isNotEmpty()) {
                                InsetDivider()
                                SecondaryActionRow("清空媒体", "移除已选择的朋友圈媒体") {
                                    onTaskChange(updateScheduledTaskMomentsMedia(task, mediaType, emptyList()))
                                }
                            }
                        }
                } }
            } else {
                scheduledContentSection(
                    context = context,
                    session = contentSession,
                    massSendOnly = task.sendChannel == ScheduledTaskSettings.SEND_CHANNEL_OFFICIAL,
                    onAdd = onAddContent,
                    onChange = onContentChange,
                    onCommit = onCommitContent,
                    onMove = onMoveContent,
                    onRemove = onRemoveContent,
                    onPickFiles = onPickFiles,
                    onPickFavorite = onPickFavorite
                )
            }
            item { SettingsSectionTitle(text = "执行计划") }
            item {
                SettingsCard {
                    val planTimes = ScheduledTaskSettings.normalizedPlanTimes(task)
                    planTimes.forEachIndexed { index, planTime ->
                        if (index > 0) InsetDivider()
                        ActionRow("计划时间 ${index + 1}", scheduledTaskTimeText(planTime)) {
                            val activity = context as? Activity
                            if (activity == null) {
                                Toast.makeText(context, "当前页面无法打开时间选择器", Toast.LENGTH_SHORT).show()
                            } else {
                                showScheduledTaskTimeActionPicker(
                                    activity = activity,
                                    canDelete = planTimes.size > 1,
                                    onEdit = {
                                        showScheduledTaskDateTimePicker(activity, planTime) { picked ->
                                            val next = planTimes.toMutableList().apply { set(index, picked) }
                                            onTaskChange(updateScheduledTaskPlanTimes(task, next))
                                        }
                                    },
                                    onDelete = {
                                        onTaskChange(
                                            updateScheduledTaskPlanTimes(
                                                task,
                                                planTimes.filterIndexed { currentIndex, _ -> currentIndex != index }
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    InsetDivider()
                    AddActionRow("新增计划时间", ::addPlanTime)
                    InsetDivider()
                    PopupChoiceRow(
                        title = "重复方式",
                        summary = scheduledTaskRepeatLabel(task.repeatType),
                        options = scheduledTaskRepeatChoices(),
                        currentValue = task.repeatType.toString(),
                        onValueChanged = { value ->
                            val repeatType = value.toIntOrNull() ?: ScheduledTaskSettings.REPEAT_NONE
                            onTaskChange(
                                task.copy(
                                    repeatType = repeatType,
                                    repeatDays = if (repeatType == ScheduledTaskSettings.REPEAT_WEEKLY) {
                                        task.repeatDays.ifEmpty { setOf(Calendar.MONDAY) }
                                    } else {
                                        emptySet()
                                    }
                                )
                            )
                        }
                    )
                    if (task.repeatType == ScheduledTaskSettings.REPEAT_WEEKLY) {
                        InsetDivider()
                        ScheduledTaskWeekdayRow(task.repeatDays) { day ->
                            val next = if (task.repeatDays.contains(day)) {
                                task.repeatDays - day
                            } else {
                                task.repeatDays + day
                            }
                            onTaskChange(task.copy(repeatDays = next))
                        }
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = task.sendOnTimeout,
                        title = "超时补发",
                        summary = "微信进程错过执行时间后，恢复时自动补发"
                    ) {
                        onTaskChange(task.copy(sendOnTimeout = it))
                    }
                }
            }
            if (
                task.targetType == ScheduledTaskSettings.TARGET_CHAT &&
                task.sendChannel == ScheduledTaskSettings.SEND_CHANNEL_MODULE
            ) {
                item { SettingsSectionTitle(text = "发送节奏") }
                item {
                    SettingsCard {
                        NumberInputRow("聊天间隔", if (intervalText.toIntOrNull()?.let { it in 0..3600 } != true) "请输入 0-3600 秒；保留上次保存的值" else "单位秒，多个聊天之间的等待时间", intervalText) {
                            intervalText = it
                            it.toIntOrNull()?.takeIf { value -> value in 0..3600 }?.let { value -> onTaskChange(task.copy(intervalSeconds = value)) }
                        }
                        if (scheduledTaskHasOrderedItems(task)) {
                            InsetDivider()
                            NumberInputRow(scheduledTaskItemIntervalTitle(), if (mediaIntervalText.toIntOrNull()?.let { it in 0..3600 } != true) "请输入 0-3600 秒；保留上次保存的值" else "单位秒，同一聊天连续发送的等待时间", mediaIntervalText) {
                                mediaIntervalText = it
                                it.toIntOrNull()?.takeIf { value -> value in 0..3600 }?.let { value -> onTaskChange(task.copy(mediaIntervalSeconds = value)) }
                            }
                        }
                    }
                }
            }
            if (!isEditing) item {
                val error = when {
                    !intervalsValid -> "请填写有效的发送间隔"
                    task.targetType == ScheduledTaskSettings.TARGET_CHAT && task.targetIds.isEmpty() -> "请选择发送对象"
                    task.targetType == ScheduledTaskSettings.TARGET_CHAT && contentSession.entries.isEmpty() -> "请添加发送内容"
                    else -> scheduledTaskValidationError(task, previous = null, now = System.currentTimeMillis())
                }
                PrimaryFormAction("创建任务", error, onSave)
            }
            if (isEditing) item {
                SettingsCard(modifier = Modifier.padding(top = 24.dp)) {
                    if (onExecuteNow != null) ContentActionRow("立即执行", enabled = intervalsValid, onClick = onExecuteNow)
                    if (onExecuteNow != null && onDelete != null) InsetDivider()
                    if (onDelete != null) ContentActionRow("删除任务", onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun ScheduledTaskWeekdayRow(
    selectedDays: Set<Int>,
    onToggle: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        scheduledTaskWeekdayOptions().forEach { (day, label) ->
            ContactFilterChip(
                text = label,
                selected = selectedDays.contains(day),
                onClick = { onToggle(day) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ScheduledContentCard(
    entry: ScheduledContentEntry,
    index: Int,
    count: Int,
    session: ScheduledContentSession,
    massSendOnly: Boolean,
    onChange: (String, ScheduledTaskContentItem) -> Unit,
    onCommit: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onPickFiles: (String, Int) -> Unit,
    onPickFavorite: (String) -> Unit
) {
    val item = entry.item
    val label = if (massSendOnly) selectedMessagesContentTypeLabel(item.type) else scheduledTaskTypeLabel(item.type)
    val preview = when {
        item.value.isBlank() -> "未完成"
        item.type == ScheduledTaskSettings.TYPE_SELECTED_MESSAGE -> SelectedMessageSnapshot.decode(item.value)?.label() ?: "消息不可用"
        scheduledTaskUsesText(item.type) -> item.value
        item.type == ScheduledTaskSettings.TYPE_FAVORITE -> favoriteSummary(item.value)
        else -> scheduledTaskMediaSummary(item.type, listOf(item.value))
    }
    SequenceItemCard(
        title = "第 ${index + 1} 条 · $label",
        summary = if (entry.pending) "$preview · 尚未加入" else preview,
        expanded = session.expandedKey == entry.key,
        modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
        onToggle = { session.expandedKey = if (session.expandedKey == entry.key) null else entry.key }
    ) {
        if (item.type == ScheduledTaskSettings.TYPE_SELECTED_MESSAGE) {
            InfoRow("消息", SelectedMessageSnapshot.decode(item.value)?.label() ?: "不可用")
        } else {
            PopupChoiceRow(
                title = "内容类型",
                summary = label,
                options = if (massSendOnly) selectedMessagesContentTypeChoices() else scheduledTaskTypeChoices(),
                currentValue = item.type.toString(),
                onValueChanged = { value ->
                    val type = value.toIntOrNull() ?: ScheduledTaskSettings.TYPE_TEXT
                    val keepValue = item.type == type || (
                        scheduledTaskUsesText(item.type) == scheduledTaskUsesText(type) &&
                            item.type != ScheduledTaskSettings.TYPE_FAVORITE && type != ScheduledTaskSettings.TYPE_FAVORITE
                        )
                    onChange(entry.key, ScheduledTaskContentItem(type, if (keepValue) item.value else ""))
                }
            )
            InsetDivider()
            when {
                scheduledTaskUsesText(item.type) -> VariableInputRow(
                    title = if (massSendOnly && item.type == ScheduledTaskSettings.TYPE_XML) "视频号内容" else scheduledTaskContentTitle(item.type),
                    summary = if (item.type == ScheduledTaskSettings.TYPE_XML) "输入分享消息 XML" else "输入要发送的内容",
                    value = item.value,
                    variables = if (massSendOnly) emptyList() else scheduledTaskTemplateVariables,
                    minLines = if (item.type == ScheduledTaskSettings.TYPE_XML) 4 else 2,
                    fieldState = session.textState(entry.key)
                ) { value ->
                    session.find(entry.key)?.let { onChange(entry.key, it.item.copy(value = value)) }
                }
                item.type == ScheduledTaskSettings.TYPE_FAVORITE -> {
                    ActionRow(scheduledTaskPickerTitle(item.type), favoriteSummary(item.value)) { onPickFavorite(entry.key) }
                    if (item.value.isNotBlank()) {
                        InsetDivider()
                        ContentActionRow("清空收藏") { onChange(entry.key, item.copy(value = "")) }
                    }
                }
                else -> {
                    ActionRow("选择文件", scheduledTaskMediaSummary(item.type, listOf(item.value))) { onPickFiles(entry.key, item.type) }
                    if (item.value.isNotBlank()) {
                        InsetDivider()
                        ContentActionRow("清空文件") { onChange(entry.key, item.copy(value = "")) }
                    }
                }
            }
        }
        InsetDivider()
        if (entry.pending) {
            ContentActionRow("添加此条") { onCommit(entry.key) }
            ContentActionRow("放弃新增") { onRemove(entry.key) }
        } else {
            SequenceItemActions(
                canMoveUp = index > 0,
                canMoveDown = index < count - 1,
                onMoveUp = { onMove(entry.key, -1) },
                onMoveDown = { onMove(entry.key, 1) },
                onDelete = { onRemove(entry.key) }
            )
        }
    }
}

internal fun LazyListScope.scheduledContentSection(
    context: Context,
    session: ScheduledContentSession,
    massSendOnly: Boolean,
    onAdd: (Int) -> Unit,
    onChange: (String, ScheduledTaskContentItem) -> Unit,
    onCommit: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onPickFiles: (String, Int) -> Unit,
    onPickFavorite: (String) -> Unit
) {
    val entries = session.entries
    val canAdd = entries.none { it.item.type == ScheduledTaskSettings.TYPE_SELECTED_MESSAGE }
    item(key = "scheduled-content-heading") {
        var showPicker by remember { mutableStateOf(false) }
        val add = { showPicker = true }
        SequenceSectionHeader("发送内容", entries.size, if (entries.isNotEmpty() && canAdd) add else null)
        if (entries.isEmpty()) SequenceEmptyCard("尚未添加内容", "添加第一条内容", add)
        val options = if (massSendOnly) selectedMessagesContentTypeChoices() else scheduledTaskTypeChoices()
        SequenceTypePickerDialog(
            show = showPicker,
            title = "选择内容类型",
            options = options.mapNotNull { choice -> choice.value.toIntOrNull()?.let { it to choice.label } },
            onDismiss = { showPicker = false },
            onSelect = onAdd
        )
    }
    if (entries.isNotEmpty()) {
        entries.forEachIndexed { index, entry ->
            item(key = "scheduled-content-${entry.key}") {
                ScheduledContentCard(entry, index, entries.size, session, massSendOnly, onChange, onCommit, onMove, onRemove, onPickFiles, onPickFavorite)
            }
        }
    }
}

private fun scheduledTaskUsesText(type: Int): Boolean {
    return ScheduledTaskSettings.scheduledTypeUsesText(type)
}

internal fun scheduledTaskEditableItems(task: ScheduledTaskItem): List<ScheduledTaskContentItem> {
    return task.items.ifEmpty {
        if (scheduledTaskUsesText(task.type)) {
            val contents = task.contentItems.ifEmpty { listOfNotNull(task.content.takeIf { it.isNotBlank() }) }
            contents.map { ScheduledTaskContentItem(task.type, it) }
        } else {
            task.mediaPaths.map { ScheduledTaskContentItem(task.type, it) }
        }
    }.map {
        val type = it.type.takeIf { value -> ScheduledTaskSettings.scheduledTypeIsValid(value) }
            ?: ScheduledTaskSettings.TYPE_TEXT
        ScheduledTaskContentItem(type, it.value)
    }
}

internal fun updateScheduledTaskContentItemValue(
    task: ScheduledTaskItem,
    index: Int,
    value: String
): ScheduledTaskItem {
    val items = scheduledTaskEditableItems(task).toMutableList()
    if (index !in items.indices) return task
    items[index] = items[index].copy(value = value)
    return task.copy(items = items)
}

private fun scheduledTaskContentTitle(type: Int): String {
    return if (type == ScheduledTaskSettings.TYPE_XML) "XML 内容" else "发送内容"
}

private fun scheduledTaskHasOrderedItems(task: ScheduledTaskItem): Boolean {
    return scheduledTaskEditableItems(task).size > 1
}

internal fun scheduledTaskItemIntervalTitle(): String {
    return "多条间隔"
}

private fun scheduledTaskTargetTypeChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("聊天", ScheduledTaskSettings.TARGET_CHAT.toString()),
    PopupChoice("朋友圈", ScheduledTaskSettings.TARGET_MOMENTS.toString())
)

private fun scheduledTaskSendChannelChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("模块通道", ScheduledTaskSettings.SEND_CHANNEL_MODULE.toString()),
    PopupChoice("微信原生群发助手", ScheduledTaskSettings.SEND_CHANNEL_OFFICIAL.toString())
)

private fun scheduledTaskSendChannelLabel(channel: Int): String {
    return if (channel == ScheduledTaskSettings.SEND_CHANNEL_OFFICIAL) {
        "微信原生群发助手"
    } else {
        "模块通道"
    }
}

private fun scheduledTaskTargetTypeLabel(type: Int): String = when (type) {
    ScheduledTaskSettings.TARGET_MOMENTS -> "朋友圈"
    else -> "聊天"
}

private fun scheduledTaskMomentsTypeChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("文字", ScheduledTaskSettings.MOMENTS_TEXT.toString()),
    PopupChoice("图文", ScheduledTaskSettings.MOMENTS_TEXT_IMAGE.toString()),
    PopupChoice("视文", ScheduledTaskSettings.MOMENTS_TEXT_VIDEO.toString()),
    PopupChoice("图片", ScheduledTaskSettings.MOMENTS_IMAGE.toString()),
    PopupChoice("视频", ScheduledTaskSettings.MOMENTS_VIDEO.toString())
)

private fun scheduledTaskMomentsTypeLabel(type: Int): String = when (type) {
    ScheduledTaskSettings.MOMENTS_TEXT_IMAGE -> "图文"
    ScheduledTaskSettings.MOMENTS_TEXT_VIDEO -> "视文"
    ScheduledTaskSettings.MOMENTS_IMAGE -> "图片"
    ScheduledTaskSettings.MOMENTS_VIDEO -> "视频"
    else -> "文字"
}

private fun scheduledTaskMomentsUsesText(type: Int): Boolean {
    return type == ScheduledTaskSettings.MOMENTS_TEXT ||
        type == ScheduledTaskSettings.MOMENTS_TEXT_IMAGE ||
        type == ScheduledTaskSettings.MOMENTS_TEXT_VIDEO
}

private fun scheduledTaskMomentsMediaType(type: Int): Int? = when (type) {
    ScheduledTaskSettings.MOMENTS_TEXT_IMAGE,
    ScheduledTaskSettings.MOMENTS_IMAGE -> ScheduledTaskSettings.TYPE_IMAGE
    ScheduledTaskSettings.MOMENTS_TEXT_VIDEO,
    ScheduledTaskSettings.MOMENTS_VIDEO -> ScheduledTaskSettings.TYPE_VIDEO
    else -> null
}

private fun scheduledTaskMomentsText(task: ScheduledTaskItem): String {
    return task.items.firstOrNull { it.type == ScheduledTaskSettings.TYPE_TEXT }?.value
        ?: task.content.takeIf { task.type == ScheduledTaskSettings.TYPE_TEXT }.orEmpty()
}

private fun scheduledTaskMomentsMediaPaths(task: ScheduledTaskItem, mediaType: Int): List<String> {
    val itemPaths = task.items.asSequence()
        .filter { it.type == mediaType }
        .map { it.value.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    if (itemPaths.isNotEmpty()) return itemPaths
    return if (task.type == mediaType) task.mediaPaths else emptyList()
}

private fun updateScheduledTaskTargetType(task: ScheduledTaskItem, targetType: Int): ScheduledTaskItem {
    val safeTarget = targetType.takeIf { ScheduledTaskSettings.targetTypeIsValid(it) }
        ?: ScheduledTaskSettings.TARGET_CHAT
    if (safeTarget == task.targetType) return task
    if (safeTarget == ScheduledTaskSettings.TARGET_CHAT) {
        return task.copy(targetType = safeTarget)
    }
    val hasText = scheduledTaskMomentsText(task).isNotBlank()
    val hasImages = scheduledTaskMomentsMediaPaths(task, ScheduledTaskSettings.TYPE_IMAGE).isNotEmpty()
    val hasVideo = scheduledTaskMomentsMediaPaths(task, ScheduledTaskSettings.TYPE_VIDEO).isNotEmpty()
    val momentsType = when {
        hasText && hasImages -> ScheduledTaskSettings.MOMENTS_TEXT_IMAGE
        hasText && hasVideo -> ScheduledTaskSettings.MOMENTS_TEXT_VIDEO
        hasImages -> ScheduledTaskSettings.MOMENTS_IMAGE
        hasVideo -> ScheduledTaskSettings.MOMENTS_VIDEO
        else -> ScheduledTaskSettings.MOMENTS_TEXT
    }
    return task.copy(
        targetType = safeTarget,
        momentsType = momentsType,
        sendChannel = ScheduledTaskSettings.SEND_CHANNEL_MODULE,
        targetIds = emptyList()
    )
}

private fun updateScheduledTaskMomentsType(task: ScheduledTaskItem, momentsType: Int): ScheduledTaskItem {
    val safeType = momentsType.takeIf { ScheduledTaskSettings.momentsTypeIsValid(it) }
        ?: ScheduledTaskSettings.MOMENTS_TEXT
    return task.copy(momentsType = safeType)
}

private fun updateScheduledTaskMomentsText(task: ScheduledTaskItem, value: String): ScheduledTaskItem {
    val items = task.items.filterNot { it.type == ScheduledTaskSettings.TYPE_TEXT }.toMutableList()
    items.add(0, ScheduledTaskContentItem(ScheduledTaskSettings.TYPE_TEXT, value))
    return task.copy(items = items)
}

private fun updateScheduledTaskMomentsMedia(
    task: ScheduledTaskItem,
    mediaType: Int,
    paths: List<String>
): ScheduledTaskItem {
    val nextPaths = paths.asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .let { values ->
            if (mediaType == ScheduledTaskSettings.TYPE_IMAGE) values.take(9) else values.take(1)
        }
        .toList()
    val items = task.items.filterNot { it.type == mediaType }.toMutableList()
    items += nextPaths.map { ScheduledTaskContentItem(mediaType, it) }
    return task.copy(items = items)
}

private fun scheduledTaskTypeChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("文本", ScheduledTaskSettings.TYPE_TEXT.toString()),
    PopupChoice("图片", ScheduledTaskSettings.TYPE_IMAGE.toString()),
    PopupChoice("视频", ScheduledTaskSettings.TYPE_VIDEO.toString()),
    PopupChoice("文件", ScheduledTaskSettings.TYPE_FILE.toString()),
    PopupChoice("表情", ScheduledTaskSettings.TYPE_EMOJI.toString()),
    PopupChoice("语音", ScheduledTaskSettings.TYPE_VOICE.toString()),
    PopupChoice("XML", ScheduledTaskSettings.TYPE_XML.toString()),
    PopupChoice("收藏", ScheduledTaskSettings.TYPE_FAVORITE.toString())
)

private fun selectedMessagesContentTypeChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("文字", ScheduledTaskSettings.TYPE_TEXT.toString()),
    PopupChoice("图片", ScheduledTaskSettings.TYPE_IMAGE.toString()),
    PopupChoice("视频", ScheduledTaskSettings.TYPE_VIDEO.toString()),
    PopupChoice("语音", ScheduledTaskSettings.TYPE_VOICE.toString()),
    PopupChoice("表情", ScheduledTaskSettings.TYPE_EMOJI.toString()),
    PopupChoice("视频号", ScheduledTaskSettings.TYPE_XML.toString())
)

private fun selectedMessagesContentTypeLabel(type: Int): String = when (type) {
    ScheduledTaskSettings.TYPE_TEXT -> "文字"
    ScheduledTaskSettings.TYPE_IMAGE -> "图片"
    ScheduledTaskSettings.TYPE_VIDEO -> "视频"
    ScheduledTaskSettings.TYPE_VOICE -> "语音"
    ScheduledTaskSettings.TYPE_EMOJI -> "表情"
    ScheduledTaskSettings.TYPE_XML -> "视频号"
    else -> scheduledTaskTypeLabel(type)
}

private fun scheduledTaskRepeatChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("单次", ScheduledTaskSettings.REPEAT_NONE.toString()),
    PopupChoice("每天", ScheduledTaskSettings.REPEAT_DAILY.toString()),
    PopupChoice("每周", ScheduledTaskSettings.REPEAT_WEEKLY.toString())
)

private fun scheduledTaskTypeLabel(type: Int): String = when (type) {
    ScheduledTaskSettings.TYPE_TEXT -> "文本"
    ScheduledTaskSettings.TYPE_IMAGE -> "图片"
    ScheduledTaskSettings.TYPE_VIDEO -> "视频"
    ScheduledTaskSettings.TYPE_FILE -> "文件"
    ScheduledTaskSettings.TYPE_EMOJI -> "表情"
    ScheduledTaskSettings.TYPE_VOICE -> "语音"
    ScheduledTaskSettings.TYPE_XML -> "XML"
    ScheduledTaskSettings.TYPE_FAVORITE -> "收藏"
    ScheduledTaskSettings.TYPE_SELECTED_MESSAGE -> "聊天记录"
    else -> "未知"
}

private fun scheduledTaskRepeatLabel(repeatType: Int): String = when (repeatType) {
    ScheduledTaskSettings.REPEAT_DAILY -> "每天"
    ScheduledTaskSettings.REPEAT_WEEKLY -> "每周"
    else -> "单次"
}

internal fun scheduledTaskTargetSummary(targetIds: List<String>): String {
    return if (targetIds.isEmpty()) "未选择" else "已选 ${targetIds.distinct().size} 个聊天"
}

internal fun scheduledTaskTargetSummary(task: ScheduledTaskItem): String {
    return if (task.targetType == ScheduledTaskSettings.TARGET_MOMENTS) {
        "朋友圈"
    } else {
        scheduledTaskTargetSummary(task.targetIds)
    }
}

private fun scheduledTaskMediaSummary(type: Int, paths: List<String>): String {
    if (type == ScheduledTaskSettings.TYPE_FAVORITE) {
        return favoriteSummary(paths.joinToString("|"))
    }
    val values = paths.map { it.trim() }.filter { it.isNotBlank() }
    if (values.isEmpty()) return "未选择"
    val first = File(values.first()).name.ifBlank { "文件" }
    return if (values.size == 1) first else "$first 等 ${values.size} 个文件"
}

private fun scheduledTaskTitle(task: ScheduledTaskItem): String {
    task.remark.trim().takeIf { it.isNotBlank() }?.let { return it }
    if (task.targetType == ScheduledTaskSettings.TARGET_MOMENTS) {
        return "朋友圈 · ${scheduledTaskMomentsTypeLabel(task.momentsType)}"
    }
    val countText = scheduledTaskItemCountText(task)
    return "${scheduledTaskTypeSummary(task)}$countText · ${scheduledTaskTargetSummary(task)}"
}

private fun scheduledTaskItemCountText(task: ScheduledTaskItem): String {
    if (task.targetType == ScheduledTaskSettings.TARGET_MOMENTS) return ""
    val count = ScheduledTaskSettings.normalizedItems(task).size
    if (count <= 1) return ""
    return "(${count}条)"
}

private fun scheduledTaskSummary(task: ScheduledTaskItem): String {
    val repeat = when (task.repeatType) {
        ScheduledTaskSettings.REPEAT_DAILY -> "每天"
        ScheduledTaskSettings.REPEAT_WEEKLY -> scheduledTaskRepeatDaysText(task.repeatDays)
        else -> "单次"
    }
    val result = if (task.lastExecutedTime > 0L) {
        " · 上次 ${task.lastSuccessCount}/${task.lastFailCount}"
    } else {
        ""
    }
    val channel = if (task.targetType == ScheduledTaskSettings.TARGET_CHAT) {
        scheduledTaskSendChannelLabel(task.sendChannel) + " / "
    } else {
        ""
    }
    val planTimes = ScheduledTaskSettings.normalizedPlanTimes(task)
    val timeSummary = if (planTimes.size <= 1) {
        scheduledTaskTimeText(task.planTime)
    } else {
        "${scheduledTaskTimeText(task.planTime)} 等 ${planTimes.size} 个时间"
    }
    val schedule = "$channel$timeSummary / $repeat$result"
    return if (task.remark.isBlank()) {
        schedule
    } else {
        val countText = scheduledTaskItemCountText(task)
        "${scheduledTaskTypeSummary(task)}$countText · ${scheduledTaskTargetSummary(task)} / $schedule"
    }
}

private fun scheduledTaskTypeSummary(task: ScheduledTaskItem): String {
    if (task.targetType == ScheduledTaskSettings.TARGET_MOMENTS) {
        return scheduledTaskMomentsTypeLabel(task.momentsType)
    }
    val items = ScheduledTaskSettings.normalizedItems(task)
    if (items.isEmpty()) return scheduledTaskTypeLabel(task.type)
    val types = items.map { it.type }.distinct()
    return if (types.size == 1) scheduledTaskTypeLabel(types.first()) else "混合"
}

private fun scheduledTaskWeekdayOptions(): List<Pair<Int, String>> = listOf(
    Calendar.MONDAY to "一",
    Calendar.TUESDAY to "二",
    Calendar.WEDNESDAY to "三",
    Calendar.THURSDAY to "四",
    Calendar.FRIDAY to "五",
    Calendar.SATURDAY to "六",
    Calendar.SUNDAY to "日"
)

private fun scheduledTaskRepeatDaysText(days: Set<Int>): String {
    if (days.isEmpty()) return "每周"
    val labels = scheduledTaskWeekdayOptions()
        .filter { days.contains(it.first) }
        .joinToString("") { "周${it.second}" }
    return labels.ifBlank { "每周" }
}

private fun scheduledTaskTimeText(timeMillis: Long): String {
    if (timeMillis <= 0L) return "未设置"
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(timeMillis))
    }.getOrDefault("未设置")
}

private fun showScheduledTaskDateTimePicker(
    activity: Activity,
    currentTimeMillis: Long,
    onPicked: (Long) -> Unit
) {
    val base = Calendar.getInstance().apply {
        timeInMillis = if (currentTimeMillis > 0L) currentTimeMillis else System.currentTimeMillis()
    }
    DatePickerDialog(
        activity,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            TimePickerDialog(
                activity,
                { _: TimePicker, hourOfDay: Int, minute: Int ->
                    showScheduledTaskSecondPicker(activity, base.get(Calendar.SECOND)) { second ->
                        val next = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, second)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onPicked(next.timeInMillis)
                    }
                },
                base.get(Calendar.HOUR_OF_DAY),
                base.get(Calendar.MINUTE),
                true
            ).show()
        },
        base.get(Calendar.YEAR),
        base.get(Calendar.MONTH),
        base.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun showScheduledTaskTimeActionPicker(
    activity: Activity,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val choices = buildList {
        add("修改时间" to "重新选择该计划时间")
        if (canDelete) add("删除时间" to "从当前任务移除该计划时间")
    }
    VoiceForwardMiuixDialog.showChoices(
        activity = activity,
        title = "计划时间",
        summary = "",
        choices = choices,
        onSelected = { index ->
            if (index == 0) onEdit() else onDelete()
        },
        onDismiss = {}
    )
}

private fun updateScheduledTaskPlanTimes(
    task: ScheduledTaskItem,
    values: List<Long>
): ScheduledTaskItem {
    val planTimes = values.filter { it > 0L }.distinct().sorted()
    return task.copy(
        planTime = planTimes.firstOrNull() ?: 0L,
        planTimes = planTimes
    )
}

private fun showScheduledTaskSecondPicker(
    activity: Activity,
    initialSecond: Int,
    onPicked: (Int) -> Unit
) {
    val showDialog: () -> Unit = {
        VoiceForwardMiuixDialog.showNumberInput(
            activity = activity,
            title = "设置秒数",
            initialValue = initialSecond,
            minValue = 0,
            maxValue = 59,
            onConfirm = onPicked,
            onDismiss = {}
        )
    }
    activity.window?.decorView?.postOnAnimation {
        if (!activity.isFinishing && !activity.isDestroyed) showDialog()
    } ?: showDialog()
}

@Composable
internal fun FakeScanCameraMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FakeScanCameraSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "扫码") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        FakeScanCameraSettings.KEY_ENABLE,
                        "模拟相机扫码",
                        "让相册识别二维码按相机扫码来源处理",
                        FakeScanCameraSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}
