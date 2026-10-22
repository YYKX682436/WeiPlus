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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import top.yukonga.miuix.kmp.basic.SmallTitle
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
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    val mainListState = rememberLazyListState()
    val editorListState = rememberLazyListState()

    fun reloadTasks() {
        tasks = settings.tasks()
    }

    fun persistTasks(next: List<ScheduledTaskItem>) {
        settings.saveTasks(next)
        reloadTasks()
        ScheduledTaskRuntimeCoordinator.reload()
    }

    fun saveDraft(closeAfterSave: Boolean = true): Boolean {
        var target = ScheduledTaskSettings.normalizeForSave(draft)
        val now = System.currentTimeMillis()
        val momentsError = ScheduledTaskSettings.momentsValidationError(target)
        val normalizedItems = ScheduledTaskSettings.normalizedItems(target)
        val channelError = if (
            target.targetType == ScheduledTaskSettings.TARGET_CHAT &&
            target.sendChannel == ScheduledTaskSettings.SEND_CHANNEL_OFFICIAL
        ) {
            if (normalizedItems.all { it.type == ScheduledTaskSettings.TYPE_SELECTED_MESSAGE }) {
                val snapshots = normalizedItems.mapNotNull { SelectedMessageSnapshot.decode(it.value) }
                if (snapshots.size != normalizedItems.size) {
                    "定时转发消息快照无效"
                } else {
                    SelectedMessagesRuntimeCoordinator.snapshotValidationError(
                        target.sendChannel,
                        snapshots
                    )
                }
            } else {
                SelectedMessagesRuntimeCoordinator.validationError(target.sendChannel, normalizedItems)
            }
        } else {
            null
        }
        val planTimes = ScheduledTaskSettings.normalizedPlanTimes(target)
        val error = when {
            target.targetType == ScheduledTaskSettings.TARGET_CHAT && target.targetIds.isEmpty() -> "请选择发送对象"
            momentsError != null -> momentsError
            normalizedItems.isEmpty() -> "请配置发送内容"
            channelError != null -> channelError
            planTimes.isEmpty() -> "请添加计划时间"
            target.repeatType == ScheduledTaskSettings.REPEAT_WEEKLY && target.repeatDays.isEmpty() -> "请选择每周重复日期"
            target.repeatType == ScheduledTaskSettings.REPEAT_NONE && planTimes.any { it <= now } -> "计划时间需要晚于当前时间"
            else -> null
        }
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            return false
        }
        if (target.repeatType != ScheduledTaskSettings.REPEAT_NONE) {
            val nextPlanTimes = planTimes.map { planTime ->
                ScheduledTaskSettings.resolveNextPlanTime(
                    planTime,
                    target.repeatType,
                    target.repeatDays,
                    now
                )
            }.filter { it > 0L }
            if (nextPlanTimes.isEmpty()) {
                Toast.makeText(context, "无法计算下次执行时间", Toast.LENGTH_SHORT).show()
                return false
            }
            target = updateScheduledTaskPlanTimes(target, nextPlanTimes)
        }
        val nextTasks = tasks.filterNot { it.id == target.id } + target
        persistTasks(nextTasks)
        draft = target
        if (closeAfterSave) {
            route = ScheduledTaskRoute.Main
        }
        Toast.makeText(context, "任务已保存", Toast.LENGTH_SHORT).show()
        return true
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
                        editingTaskId = null
                        draft = ScheduledTaskSettings.newDraft()
                        route = ScheduledTaskRoute.Editor
                    },
                    onEdit = { task ->
                        editingTaskId = task.id
                        draft = task
                        route = ScheduledTaskRoute.Editor
                    }
                )
            }
            ScheduledTaskRoute.Editor -> {
                ScheduledTaskEditorPage(
                    context = context,
                    task = draft,
                    isEditing = editingTaskId != null,
                    listState = editorListState,
                    onBack = { route = ScheduledTaskRoute.Main },
                    onTaskChange = { draft = it },
                    onPickTargets = { route = ScheduledTaskRoute.ContactPicker },
                    onPickFiles = { itemIndex, itemType ->
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            ScheduledTaskFilePickerBridge.launch(activity, itemType) { paths ->
                                draft = updateScheduledTaskMediaItem(draft, itemIndex, itemType, paths)
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
                                draft = updateScheduledTaskMomentsMedia(draft, itemType, paths)
                            }
                        }
                    },
                    onPickFavorite = { itemIndex ->
                        scheduledTaskEditableItems(draft).getOrNull(itemIndex)?.let { item ->
                            route = ScheduledTaskRoute.FavoritePicker(
                                itemIndex,
                                FavoritePickerRequest(
                                    title = scheduledTaskPickerTitle(item.type),
                                    existingValue = item.value,
                                    onValue = { value ->
                                        draft = updateScheduledTaskContentItemValue(draft, itemIndex, value)
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
                            persistTasks(tasks.filterNot { it.id == draft.id })
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
                    onConfirm = { selected ->
                        draft = draft.copy(targetIds = selected.map { it.id })
                        route = ScheduledTaskRoute.Editor
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
}

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
        bottomBar = {
            BottomActionBar(
                primaryText = "新增任务",
                onPrimaryClick = onAdd,
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
            item { SmallTitle(text = "发送计划") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用定时任务",
                        summary = "开启后按计划时间发送聊天消息或发布朋友圈"
                    ) { onEnabledChange(it) }
                    InsetDivider()
                    InfoRow("当前任务", if (tasks.isEmpty()) "暂无任务" else "${tasks.size} 个")
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "任务列表") }
            when {
                tasks.isEmpty() -> item {
                    SettingsCard {
                        EmptyText("还没有定时任务，点击底部“新增任务”。")
                    }
                }
                else -> tasks.forEach { task ->
                    item {
                        SettingsCard {
                            ActionRow(
                                scheduledTaskTitle(task),
                                scheduledTaskSummary(task)
                            ) {
                                onEdit(task)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledTaskEditorPage(
    context: Context,
    task: ScheduledTaskItem,
    isEditing: Boolean,
    listState: LazyListState,
    onBack: () -> Unit,
    onTaskChange: (ScheduledTaskItem) -> Unit,
    onPickTargets: () -> Unit,
    onPickFiles: (Int, Int) -> Unit,
    onPickMomentsMedia: (Int) -> Unit,
    onPickFavorite: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onExecuteNow: (() -> Unit)?
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = if (isEditing) "编辑任务" else "新增任务",
        largeTitle = if (isEditing) "编辑任务" else "新增任务",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存任务",
                onPrimaryClick = onSave,
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
            item { SmallTitle(text = "备注") }
            item {
                SettingsCard {
                    InputRow(
                        title = "任务备注",
                        summary = "用于在任务列表区分任务，可留空",
                        value = task.remark,
                        onValueChange = { onTaskChange(task.copy(remark = it)) }
                    )
                }
            }
            item { SmallTitle(text = "任务目标") }
            item {
                SettingsCard {
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
            item {
                SmallTitle(
                    modifier = Modifier.padding(top = 10.dp),
                    text = if (task.targetType == ScheduledTaskSettings.TARGET_MOMENTS) "朋友圈内容" else "发送内容"
                )
            }
            item {
                SettingsCard {
                    if (task.targetType == ScheduledTaskSettings.TARGET_MOMENTS) {
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
                                ActionRow("清空媒体", "移除已选择的朋友圈媒体") {
                                    onTaskChange(updateScheduledTaskMomentsMedia(task, mediaType, emptyList()))
                                }
                            }
                        }
                    } else {
                        ScheduledTaskChatContentRows(
                            task = task,
                            onTaskChange = onTaskChange,
                            onPickFiles = onPickFiles,
                            onPickFavorite = onPickFavorite,
                            massSendOnly = task.sendChannel == ScheduledTaskSettings.SEND_CHANNEL_OFFICIAL
                        )
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "执行计划") }
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
                    ActionRow("新增计划时间", "同一任务可在多个时间分别执行") {
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开时间选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            val initialTime = planTimes.lastOrNull()
                                ?.let { it + 5 * 60 * 1000L }
                                ?: (System.currentTimeMillis() + 5 * 60 * 1000L)
                            showScheduledTaskDateTimePicker(activity, initialTime) { picked ->
                                onTaskChange(updateScheduledTaskPlanTimes(task, planTimes + picked))
                            }
                        }
                    }
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
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "发送节奏") }
                item {
                    SettingsCard {
                        NumberInputRow("聊天间隔", "单位秒，多个聊天之间的等待时间", task.intervalSeconds.toString()) {
                            onTaskChange(task.copy(intervalSeconds = it.toIntOrNull()?.coerceIn(0, 3600) ?: 0))
                        }
                        if (scheduledTaskHasOrderedItems(task)) {
                            InsetDivider()
                            NumberInputRow(scheduledTaskItemIntervalTitle(), "单位秒，同一聊天连续发送的等待时间", task.mediaIntervalSeconds.toString()) {
                                onTaskChange(task.copy(mediaIntervalSeconds = it.toIntOrNull()?.coerceIn(0, 3600) ?: 0))
                            }
                        }
                    }
                }
            }
            if (onExecuteNow != null || onDelete != null) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        if (onExecuteNow != null) {
                            ActionRow("立即执行", "保存当前内容后立刻发送一次") {
                                onExecuteNow()
                            }
                        }
                        if (onExecuteNow != null && onDelete != null) {
                            InsetDivider()
                        }
                        if (onDelete != null) {
                            ActionRow("删除任务", "移除当前定时任务") {
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
internal fun ScheduledTaskChatContentRows(
    task: ScheduledTaskItem,
    onTaskChange: (ScheduledTaskItem) -> Unit,
    onPickFiles: (Int, Int) -> Unit,
    onPickFavorite: (Int) -> Unit,
    massSendOnly: Boolean = false
) {
    val items = scheduledTaskEditableItems(task)
    items.forEachIndexed { index, item ->
        if (index > 0) InsetDivider()
        if (item.type == ScheduledTaskSettings.TYPE_SELECTED_MESSAGE) {
            InfoRow(
                label = if (items.size == 1) "发送类型" else "发送类型 ${index + 1}",
                value = scheduledTaskTypeLabel(item.type)
            )
            InsetDivider()
            InfoRow(
                label = "消息",
                value = SelectedMessageSnapshot.decode(item.value)?.label() ?: "不可用"
            )
        } else {
            PopupChoiceRow(
                title = if (items.size == 1) "发送类型" else "发送类型 ${index + 1}",
                summary = if (massSendOnly) {
                    selectedMessagesContentTypeLabel(item.type)
                } else {
                    scheduledTaskTypeLabel(item.type)
                },
                options = if (massSendOnly) selectedMessagesContentTypeChoices() else scheduledTaskTypeChoices(),
                currentValue = item.type.toString(),
                onValueChanged = { value ->
                    val type = value.toIntOrNull() ?: if (massSendOnly) {
                        ScheduledTaskSettings.TYPE_TEXT
                    } else {
                        ScheduledTaskSettings.TYPE_TEXT
                    }
                    onTaskChange(updateScheduledTaskContentItemType(task, index, type))
                }
            )
            InsetDivider()
        }
        if (item.type != ScheduledTaskSettings.TYPE_SELECTED_MESSAGE && scheduledTaskUsesText(item.type)) {
            VariableInputRow(
                title = if (massSendOnly && item.type == ScheduledTaskSettings.TYPE_XML) {
                    "视频号内容"
                } else {
                    scheduledTaskContentTitle(item.type)
                },
                summary = if (item.type == ScheduledTaskSettings.TYPE_XML) {
                    if (massSendOnly) "输入视频号分享消息 XML" else "输入 <msg><appmsg>...</appmsg></msg>"
                } else {
                    "输入要发送的内容"
                },
                value = item.value,
                variables = if (massSendOnly) emptyList() else scheduledTaskTemplateVariables,
                minLines = if (item.type == ScheduledTaskSettings.TYPE_XML) 4 else 2
            ) {
                onTaskChange(updateScheduledTaskContentItemValue(task, index, it))
            }
        } else if (item.type != ScheduledTaskSettings.TYPE_SELECTED_MESSAGE &&
            item.type == ScheduledTaskSettings.TYPE_FAVORITE
        ) {
            ActionRow(scheduledTaskPickerTitle(item.type), favoriteSummary(item.value)) {
                onPickFavorite(index)
            }
            if (item.value.isNotBlank()) {
                InsetDivider()
                ActionRow("清空收藏", "移除当前收藏") {
                    onTaskChange(updateScheduledTaskContentItemValue(task, index, ""))
                }
            }
        } else if (item.type != ScheduledTaskSettings.TYPE_SELECTED_MESSAGE) {
            ActionRow("选择文件", scheduledTaskMediaSummary(item.type, listOf(item.value))) {
                onPickFiles(index, item.type)
            }
            if (item.value.isNotBlank()) {
                InsetDivider()
                ActionRow("清空文件", "移除当前文件") {
                    onTaskChange(updateScheduledTaskContentItemValue(task, index, ""))
                }
            }
        }
        if (items.size > 1) {
            InsetDivider()
            ActionRow("删除本条", "移除第 ${index + 1} 条内容") {
                onTaskChange(removeScheduledTaskContentItem(task, index))
            }
        }
    }
    if (items.none { it.type == ScheduledTaskSettings.TYPE_SELECTED_MESSAGE }) {
        InsetDivider()
        ActionRow("新增内容", "添加一条按顺序发送的内容") {
            onTaskChange(
                appendScheduledTaskContentItem(
                    task,
                    ScheduledTaskSettings.TYPE_TEXT
                )
            )
        }
    }
}

private fun scheduledTaskUsesText(type: Int): Boolean {
    return ScheduledTaskSettings.scheduledTypeUsesText(type)
}

internal fun scheduledTaskEditableItems(task: ScheduledTaskItem): List<ScheduledTaskContentItem> {
    return task.items.ifEmpty {
        if (scheduledTaskUsesText(task.type)) {
            val contents = task.contentItems.ifEmpty { listOf(task.content) }
            contents.map { ScheduledTaskContentItem(task.type, it) }
        } else {
            task.mediaPaths.map { ScheduledTaskContentItem(task.type, it) }
        }
    }.ifEmpty {
        listOf(ScheduledTaskContentItem(ScheduledTaskSettings.TYPE_TEXT, ""))
    }.map {
        val type = it.type.takeIf { value -> ScheduledTaskSettings.scheduledTypeIsValid(value) }
            ?: ScheduledTaskSettings.TYPE_TEXT
        ScheduledTaskContentItem(type, it.value)
    }
}

private fun updateScheduledTaskContentItemType(
    task: ScheduledTaskItem,
    index: Int,
    type: Int
): ScheduledTaskItem {
    val items = scheduledTaskEditableItems(task).toMutableList()
    if (index !in items.indices) return task
    val previous = items[index]
    val keepValue = previous.type == type ||
        (
            scheduledTaskUsesText(previous.type) == scheduledTaskUsesText(type) &&
                previous.type != ScheduledTaskSettings.TYPE_FAVORITE &&
                type != ScheduledTaskSettings.TYPE_FAVORITE
            )
    items[index] = ScheduledTaskContentItem(type, if (keepValue) previous.value else "")
    return task.copy(items = items)
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

internal fun updateScheduledTaskMediaItem(
    task: ScheduledTaskItem,
    index: Int,
    type: Int,
    paths: List<String>
): ScheduledTaskItem {
    val nextPaths = paths.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (nextPaths.isEmpty()) return task
    val items = scheduledTaskEditableItems(task).toMutableList()
    if (index !in items.indices) return task
    items[index] = ScheduledTaskContentItem(type, nextPaths.first())
    nextPaths.drop(1).forEachIndexed { offset, path ->
        items.add(index + 1 + offset, ScheduledTaskContentItem(type, path))
    }
    return task.copy(items = items)
}

private fun appendScheduledTaskContentItem(
    task: ScheduledTaskItem,
    defaultType: Int = ScheduledTaskSettings.TYPE_TEXT
): ScheduledTaskItem {
    val items = scheduledTaskEditableItems(task) + ScheduledTaskContentItem(defaultType, "")
    return task.copy(items = items)
}

private fun removeScheduledTaskContentItem(task: ScheduledTaskItem, index: Int): ScheduledTaskItem {
    val items = scheduledTaskEditableItems(task).toMutableList()
    if (items.size <= 1 || index !in items.indices) return task
    items.removeAt(index)
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
            item { SmallTitle(text = "扫码") }
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
