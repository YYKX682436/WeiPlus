package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.antirecall.AntiRecallSettings
import h.Hchat.hooks.items.customnotify.CustomNotificationRuntime
import h.Hchat.hooks.items.payment.core.PaymentTemplateTimeFormatter
import h.Hchat.hooks.items.payment.core.RedPacketRuleBinding
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import h.Hchat.hooks.items.payment.core.RedPacketReplyStep
import h.Hchat.hooks.items.payment.core.RedPacketRuleTemplate
import h.Hchat.hooks.items.payment.core.RedPacketSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale

@Composable
internal fun RedPacketMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RedPacketSettings.PREFS_NAME) }
    var templates by remember {
        mutableStateOf(
            RedPacketRuleConfig.parseTemplates(sp.getString(RedPacketRuleConfig.KEY_TEMPLATES, "") ?: "")
        )
    }
    var bindings by remember {
        mutableStateOf(
            RedPacketRuleConfig.parseBindings(sp.getString(RedPacketRuleConfig.KEY_BINDINGS, "") ?: "")
        )
    }
    var defaultTemplateId by remember {
        mutableStateOf(sp.getString(RedPacketRuleConfig.KEY_DEFAULT_TEMPLATE_ID, "") ?: "")
    }
    var keywords by remember { mutableStateOf(sp.getString(RedPacketSettings.KEY_KEYWORDS, "") ?: "") }
    var keywordMode by remember { mutableStateOf(sp.getInt(RedPacketSettings.KEY_KW_MODE, 0)) }
    var whitelist by remember { mutableStateOf(sp.getString(RedPacketSettings.KEY_WHITELIST, "") ?: "") }
    var blacklist by remember { mutableStateOf(sp.getString(RedPacketSettings.KEY_BLACKLIST, "") ?: "") }
    var listMode by remember { mutableStateOf(sp.getInt(RedPacketSettings.KEY_MODE, 0)) }
    var filterExpanded by remember { mutableStateOf(false) }
    var processingExpanded by remember { mutableStateOf(false) }
    var wishText by remember { mutableStateOf(sp.getString(RedPacketSettings.KEY_WISH_TEXT, "谢谢老板") ?: "谢谢老板") }
    var privateReplySteps by remember { mutableStateOf(loadGlobalRedPacketReplySteps(sp, includeDisabledLegacy = true)) }
    var groupReplySteps by remember { mutableStateOf(loadGlobalGroupRedPacketReplySteps(sp, includeDisabledLegacy = true)) }
    var notifyText by remember { mutableStateOf(sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "") }
    var notifyToastText by remember {
        mutableStateOf(
            sp.getString(
                RedPacketSettings.KEY_NOTIFY_TOAST_TEXT,
                sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
            ) ?: ""
        )
    }
    var failText by remember { mutableStateOf(sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "") }
    var failToastText by remember {
        mutableStateOf(
            sp.getString(
                RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_TEXT,
                sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "未抢到红包"
            ) ?: ""
        )
    }
    var announceText by remember { mutableStateOf(sp.getString(RedPacketSettings.KEY_ANNOUNCE_TEXT, "抢到红包 {amount} 元") ?: "") }
    var timeFormat by remember {
        mutableStateOf(
            PaymentTemplateTimeFormatter.normalizePattern(
                sp.getString(RedPacketSettings.KEY_TIME_FORMAT, RedPacketSettings.DEFAULT_TIME_FORMAT)
            )
        )
    }
    var wishEnabled by remember { mutableStateOf(sp.getBoolean(RedPacketSettings.KEY_WISH_ENABLE, false)) }
    var replyEnabled by remember { mutableStateOf(sp.getBoolean(RedPacketSettings.KEY_REPLY_ENABLE, false)) }
    var notifySystemEnabled by remember { mutableStateOf(sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SYSTEM_ENABLE, false)) }
    var notifyToastEnabled by remember { mutableStateOf(sp.getBoolean(RedPacketSettings.KEY_NOTIFY_TOAST_ENABLE, false)) }
    var notifySoundEnabled by remember { mutableStateOf(sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SOUND_ENABLE, false)) }
    var announceEnabled by remember { mutableStateOf(sp.getBoolean(RedPacketSettings.KEY_ANNOUNCE_ENABLE, false)) }
    var notifyFailedSystemEnabled by remember { mutableStateOf(sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_SYSTEM_ENABLE, false)) }
    var notifyFailedToastEnabled by remember { mutableStateOf(sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_ENABLE, false)) }
    var notifySoundUri by remember { mutableStateOf(sp.getString(RedPacketSettings.KEY_NOTIFY_SOUND_URI, "") ?: "") }
    var notifySoundMode by remember {
        mutableStateOf(sp.getInt(
            RedPacketSettings.KEY_NOTIFY_SOUND_MODE,
            RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM
        ))
    }
    var grabDelay by remember { mutableStateOf(sp.getInt(RedPacketSettings.KEY_DELAY_VALUE, 0).coerceAtLeast(0).toString()) }
    var grabRandomMinDelay by remember {
        mutableStateOf(sp.getInt(RedPacketSettings.KEY_DELAY_RANDOM_MIN, 500).coerceAtLeast(0).toString())
    }
    var grabRandomMaxDelay by remember {
        mutableStateOf(sp.getInt(RedPacketSettings.KEY_DELAY_RANDOM_MAX, 3000).coerceAtLeast(0).toString())
    }
    var grabDelayMode by remember {
        mutableStateOf(redPacketDelayModeFromPrefs(sp))
    }
    var picker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var bindingPicker by remember { mutableStateOf<MessageBlockContactPickerRequest?>(null) }
    var optionPicker by remember { mutableStateOf<OptionPickerRequest?>(null) }
    var templateEditor by remember { mutableStateOf<RedPacketTemplateEditorRequest?>(null) }
    var bindingEditor by remember { mutableStateOf<RedPacketBindingEditorRequest?>(null) }
    var showTemplateManager by remember { mutableStateOf(false) }
    var showListManager by remember { mutableStateOf(false) }
    var editingReplyStepsForGroup by remember { mutableStateOf<Boolean?>(null) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    fun persistRules(
        nextTemplates: List<RedPacketRuleTemplate> = templates,
        nextBindings: List<RedPacketRuleBinding> = bindings,
        nextDefaultTemplateId: String = defaultTemplateId
    ) {
        sp.edit()
            .putString(RedPacketRuleConfig.KEY_TEMPLATES, RedPacketRuleConfig.encodeTemplates(nextTemplates))
            .putString(RedPacketRuleConfig.KEY_BINDINGS, RedPacketRuleConfig.encodeBindings(nextBindings))
            .putString(RedPacketRuleConfig.KEY_DEFAULT_TEMPLATE_ID, nextDefaultTemplateId)
            .commit()
    }

    fun persistReplySteps(group: Boolean, steps: List<RedPacketReplyStep>) {
        val editor = sp.edit()
        if (group) {
            editor.putString(RedPacketSettings.KEY_REPLY_GROUP_ITEMS, RedPacketRuleConfig.encodeReplySteps(steps))
        } else {
            val first = steps.firstOrNull()
            val mode = first?.mode ?: RedPacketRuleConfig.REPLY_OFF
            val content = first?.content.orEmpty()
            val delay = first?.delayMs?.coerceAtLeast(0L) ?: 0L
            editor.putString(RedPacketSettings.KEY_REPLY_ITEMS, RedPacketRuleConfig.encodeReplySteps(steps))
                .putInt(RedPacketSettings.KEY_REPLY_TYPE, mode)
                .putString(RedPacketSettings.KEY_REPLY_TEMPLATES, if (redPacketReplyUsesText(mode)) content else "")
                .putString(RedPacketSettings.KEY_REPLY_TEXT, if (redPacketReplyUsesText(mode)) firstTemplate(content) else "")
                .putString(RedPacketSettings.KEY_REPLY_MEDIA_PATHS, if (mode != RedPacketRuleConfig.REPLY_OFF && !redPacketReplyUsesText(mode)) content.trim() else "")
                .putInt(RedPacketSettings.KEY_REPLY_DELAY_VALUE, (delay / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .putInt(RedPacketSettings.KEY_REPLY_DELAY_UNIT, 1)
                .putBoolean(RedPacketSettings.KEY_REPLY_CUSTOM_ENABLE, delay > 0L)
                .putBoolean(RedPacketSettings.KEY_REPLY_RANDOM, first?.random ?: false)
        }
        editor.apply()
    }

    fun persistDelay() {
        val editor = sp.edit()
        when (grabDelayMode) {
            RedPacketRuleConfig.DELAY_CUSTOM -> {
                val delay = grabDelay.toIntOrNull()?.takeIf { it in 0..10000 } ?: return
                editor.putInt(RedPacketSettings.KEY_DELAY_VALUE, delay)
            }
            RedPacketRuleConfig.DELAY_RANDOM -> {
                val min = grabRandomMinDelay.toIntOrNull()?.takeIf { it in 0..600000 } ?: return
                val max = grabRandomMaxDelay.toIntOrNull()?.takeIf { it in min..600000 } ?: return
                editor.putInt(RedPacketSettings.KEY_DELAY_VALUE, 0)
                    .putInt(RedPacketSettings.KEY_DELAY_RANDOM_MIN, min)
                    .putInt(RedPacketSettings.KEY_DELAY_RANDOM_MAX, max)
            }
            else -> editor.putInt(RedPacketSettings.KEY_DELAY_VALUE, 0)
        }
        editor.putInt(RedPacketSettings.KEY_DELAY_MODE, grabDelayMode).apply()
    }

    fun persistText(key: String, value: String, finished: Boolean = false) {
        if (value.isNotBlank() || finished) sp.edit().putString(key, value).apply()
    }

    val route = when {
        templateEditor != null -> RedPacketRoute.TemplateEditor(templateEditor!!)
        bindingEditor != null -> RedPacketRoute.BindingEditor(bindingEditor!!)
        bindingPicker != null -> RedPacketRoute.RuleContactPicker(bindingPicker!!)
        picker != null -> RedPacketRoute.ContactPicker(picker!!)
        optionPicker != null -> RedPacketRoute.OptionPicker(optionPicker!!)
        editingReplyStepsForGroup != null -> RedPacketRoute.ReplySteps
        showTemplateManager -> RedPacketRoute.TemplateManager
        showListManager -> RedPacketRoute.ListManager
        else -> RedPacketRoute.Main
    }

    SettingsRouteTransition(
        targetState = route,
        label = "RedPacketRouteTransition",
        depthOf = { it.depth() },
        horizontalOnSameDepth = false
    ) { currentRoute ->
        when (currentRoute) {
            is RedPacketRoute.TemplateEditor -> {
                val request = currentRoute.request
                RedPacketTemplatePage(
                    context = context,
                    request = request,
                    onBack = { templateEditor = null },
                    onSave = { updated ->
                        val next = if (request.index in templates.indices) {
                            templates.toMutableList().also { it[request.index] = updated }
                        } else {
                            templates + updated
                        }
                        templates = next
                        persistRules(nextTemplates = next)
                        if (!request.canDelete) templateEditor = null
                    },
                    onDelete = {
                        if (request.index in templates.indices) {
                            val deletedId = templates[request.index].id
                            val next = templates.toMutableList().also { it.removeAt(request.index) }
                            val nextBindings = bindings.map { binding ->
                                if (binding.templateId == deletedId) binding.copy(templateId = "") else binding
                            }
                            val nextDefault = defaultTemplateId
                                .takeIf { id -> id != deletedId && next.any { it.id == id } }
                                ?: next.firstOrNull()?.id.orEmpty()
                            templates = next
                            bindings = nextBindings
                            defaultTemplateId = nextDefault
                            persistRules(nextTemplates = next, nextBindings = nextBindings, nextDefaultTemplateId = nextDefault)
                        }
                        templateEditor = null
                    }
                )
            }
            is RedPacketRoute.BindingEditor -> {
                val request = currentRoute.request
                RedPacketBindingPage(
                    context = context,
                    request = request,
                    templates = templates,
                    onBack = { bindingEditor = null },
                    onSave = { updated ->
                        val normalized = normalizedRedPacketBinding(updated)
                        val index = bindings.indexOfFirst { it.targetId == request.binding.targetId }
                        val next = if (index >= 0) bindings.toMutableList().also { it[index] = normalized }
                            else bindings + normalized
                        bindings = next
                        persistRules(nextBindings = next)
                        if (!request.canDelete) bindingEditor = null
                    },
                    onDelete = {
                        val index = bindings.indexOfFirst { it.targetId == request.binding.targetId }
                        if (index >= 0) {
                            val next = bindings.toMutableList().also { it.removeAt(index) }
                            bindings = next
                            persistRules(nextBindings = next)
                        }
                        bindingEditor = null
                    }
                )
            }
            is RedPacketRoute.RuleContactPicker -> {
                val request = currentRoute.request
                MessageBlockContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { bindingPicker = null },
                    onConfirm = { selected ->
                        val additions = selected.map {
                            redPacketBindingFromContact(
                                row = it,
                                templates = templates,
                                existing = findRedPacketBinding(bindings, it.contact.id)
                            )
                        }
                        if (additions.size == 1) {
                            val binding = additions.first()
                            val existingIndex = bindings.indexOfFirst { it.targetId == binding.targetId }
                            bindingEditor = RedPacketBindingEditorRequest(
                                index = existingIndex.takeIf { it >= 0 } ?: bindings.size,
                                binding = binding,
                                canDelete = existingIndex >= 0
                            )
                        } else if (additions.isNotEmpty()) {
                            val next = upsertRedPacketBindings(bindings, additions)
                            bindings = next
                            persistRules(nextBindings = next)
                            Toast.makeText(context, "已添加 ${additions.size} 个适用聊天", Toast.LENGTH_SHORT).show()
                        }
                        bindingPicker = null
                    }
                )
            }
            RedPacketRoute.TemplateManager -> {
                RedPacketTemplateListPage(
                    templates = templates,
                    onBack = { showTemplateManager = false },
                    onOpenTemplate = { index, template ->
                        templateEditor = RedPacketTemplateEditorRequest(index, template, canDelete = true)
                    },
                    onAddTemplate = {
                        templateEditor = RedPacketTemplateEditorRequest(
                            templates.size,
                            newRedPacketTemplate(templates.size + 1, sp),
                            canDelete = false
                        )
                    }
                )
            }
            RedPacketRoute.ListManager -> {
                RedPacketBindingListPage(
                    bindings = bindings,
                    templates = templates,
                    onBack = { showListManager = false },
                    onOpenBinding = { index, binding ->
                        bindingEditor = RedPacketBindingEditorRequest(index, binding, canDelete = true)
                    },
                    onAddBinding = {
                        bindingPicker = MessageBlockContactPickerRequest(
                            title = "选择适用聊天",
                            existingValue = "",
                            onValue = {},
                            allowOfficialAccounts = false
                        )
                    },
                    onDeleteBindings = { targets ->
                        val removeIds = targets.mapTo(HashSet()) { it.id }
                        val next = bindings.filterNot { it.id in removeIds }
                        bindings = next
                        persistRules(nextBindings = next)
                        Toast.makeText(context, "已删除 ${targets.size} 个适用聊天", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            is RedPacketRoute.ContactPicker -> {
                val request = currentRoute.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { picker = null },
                    onSelectionChange = { selected -> request.onValue(formatIds(selected)) }
                )
            }
            is RedPacketRoute.OptionPicker -> {
                val request = currentRoute.request
                OptionPickerPage(
                    request = request,
                    onBack = { optionPicker = null },
                    onSelected = { selected ->
                        request.onSelected(selected)
                    }
                )
            }
            RedPacketRoute.ReplySteps -> {
                val group = editingReplyStepsForGroup == true
                RedPacketReplyStepsPage(
                    context = context,
                    title = if (group) "群红包回复" else "私聊红包回复",
                    initialSteps = if (group) groupReplySteps else privateReplySteps,
                    onBack = { editingReplyStepsForGroup = null },
                    onSave = { steps ->
                        if (group) {
                            groupReplySteps = steps
                        } else {
                            privateReplySteps = steps
                            if (!sp.contains(RedPacketSettings.KEY_REPLY_GROUP_ITEMS)) groupReplySteps = steps
                        }
                        persistReplySteps(group, steps)
                    }
                )
            }
            RedPacketRoute.Main -> PageScaffold(
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
            item { SettingsSectionTitle(text = "核心功能") }
            item {
                SettingsCard {
                    SwitchRow(sp, RedPacketSettings.KEY_ENABLE, "自动抢红包", "开启后自动识别并抢红包", false)
                    InsetDivider()
                    OptionRow(
                        sp = sp,
                        key = RedPacketSettings.KEY_GRAB_MODE,
                        title = "抢包模式",
                        options = optionItems("打开红包页面" to 0, "静默抢包（后台）" to 1),
                        defaultValue = RedPacketSettings.DEFAULT_GRAB_MODE
                    )
                    InsetDivider()
                    SwitchRow(sp, RedPacketSettings.KEY_SKIP_SELF, "跳过自己的红包", "不会抢自己发出的红包", false)
                    InsetDivider()
                    SwitchRow(
                        sp,
                        RedPacketSettings.KEY_BLOCK_NEW_GROUP_ENABLE,
                        "自动屏蔽新进群",
                        "新群自动加入适用聊天，并默认关闭该群抢红包",
                        false
                    )
                    InsetDivider()
                    SwitchRow(sp, RedPacketSettings.KEY_AUTO_CLOSE, "自动关闭页面", "抢完或失败后自动收起红包页", false)

                }
            }
            item { SettingsSectionTitle(text = "规则管理") }
            item {
                SettingsCard {
                    SelectRow(
                        title = "红包模板",
                        summary = if (templates.isEmpty()) "暂无模板，进入后添加不同群的抢包策略" else "${templates.size} 个模板，进入后新增或修改",
                        onClick = { showTemplateManager = true }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "适用聊天",
                        summary = if (bindings.isEmpty()) "暂无适用聊天，未命中时走默认规则" else "${bindings.size} 个聊天，进入后分配模板",
                        onClick = { showListManager = true }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "默认规则",
                        summary = describeRedPacketDefaultTemplate(defaultTemplateId, templates),
                        onClick = {
                            val options = listOf(
                                OptionItem(
                                    label = "旧版全局设置",
                                    value = -1,
                                    summary = "未命中适用聊天时继续使用下方全局设置"
                                )
                            ) + templates.mapIndexed { index, template ->
                                OptionItem(
                                    label = template.name.ifBlank { "模板 ${index + 1}" },
                                    value = index,
                                    summary = describeRedPacketTemplate(template)
                                )
                            }
                            val current = templates.indexOfFirst { it.id == defaultTemplateId }
                            optionPicker = OptionPickerRequest(
                                title = "默认规则",
                                options = options,
                                currentValue = current.takeIf { it >= 0 } ?: -1,
                                onSelected = { selected ->
                                    val nextDefault = if (selected.value in templates.indices) {
                                        templates[selected.value].id
                                    } else {
                                        ""
                                    }
                                    defaultTemplateId = nextDefault
                                    persistRules(nextDefaultTemplateId = nextDefault)
                                    optionPicker = null
                                }
                            )
                        }
                    )
                }
            }
            item {
                SequenceItemCard(
                    title = "过滤与延迟",
                    summary = "${if (listMode == 1) "白名单" else if (listMode == 2) "黑名单" else "全部红包"} · ${redPacketDelayModeLabel(grabDelayMode)}",
                    expanded = filterExpanded,
                    modifier = Modifier.padding(top = 24.dp),
                    onToggle = {
                        val invalidDelay = when (grabDelayMode) {
                            RedPacketRuleConfig.DELAY_CUSTOM -> grabDelay.toIntOrNull()?.let { it in 0..10000 } != true
                            RedPacketRuleConfig.DELAY_RANDOM -> grabRandomMinDelay.toIntOrNull()?.let { min -> min in 0..600000 && grabRandomMaxDelay.toIntOrNull()?.let { it in min..600000 } == true } != true
                            else -> false
                        }
                        if (filterExpanded && invalidDelay) Toast.makeText(context, "请先修正延迟", Toast.LENGTH_SHORT).show()
                        else filterExpanded = !filterExpanded
                    }
                ) {
                    PopupOptionRow(
                        title = "抢包延迟",
                        summary = redPacketDelayModeLabel(grabDelayMode),
                        options = redPacketDelayModeOptions(includeRandom = true),
                        currentValue = grabDelayMode,
                        onValueChanged = { mode ->
                            grabDelayMode = mode
                            if (mode == RedPacketRuleConfig.DELAY_FIXED) grabDelay = "0"
                            persistDelay()
                        }
                    )
                    if (grabDelayMode == RedPacketRuleConfig.DELAY_CUSTOM) {
                        InsetDivider()
                        NumberInputRow("自定义延迟", if (grabDelay.toIntOrNull()?.let { it in 0..10000 } != true) "请输入 0–10000，保留上次有效配置" else "单位 ms", grabDelay) { grabDelay = it; persistDelay() }
                    } else if (grabDelayMode == RedPacketRuleConfig.DELAY_RANDOM) {
                        InsetDivider()
                        NumberInputRow("最小延迟", "单位 ms，不能大于最大延迟", grabRandomMinDelay) { grabRandomMinDelay = it; persistDelay() }
                        InsetDivider()
                        NumberInputRow("最大延迟", "单位 ms，不能小于最小延迟", grabRandomMaxDelay) { grabRandomMaxDelay = it; persistDelay() }
                        if (grabRandomMinDelay.toIntOrNull()?.let { min -> min in 0..600000 && grabRandomMaxDelay.toIntOrNull()?.let { it in min..600000 } == true } != true) {
                            InsetDivider(); InfoRow("延迟范围", "请输入有效范围，保留上次有效配置")
                        }
                    }
                    InsetDivider()
                    OptionRow(
                        sp = sp,
                        key = RedPacketSettings.KEY_MODE,
                        title = "过滤模式",
                        options = optionItems("全部抢" to 0, "白名单（只抢指定人）" to 1, "黑名单（不抢指定人）" to 2),
                        defaultValue = 0,
                        onValueChanged = { listMode = it }
                    )
                    InsetDivider()
                    OptionRow(
                        sp = sp,
                        key = RedPacketSettings.KEY_KW_MODE,
                        title = "关键词过滤",
                        options = optionItems("无限制" to 0, "只抢含关键词的" to 1, "屏蔽含关键词的" to 2),
                        defaultValue = 0,
                        onValueChanged = { keywordMode = it }
                    )
                    if (keywordMode != 0) {
                        InsetDivider()
                        InputRow("关键词列表", "多个关键词用 | 分隔", keywords,
                            onEditingFinished = { if (filterExpanded) persistText(RedPacketSettings.KEY_KEYWORDS, it, true) }) { keywords = it; persistText(RedPacketSettings.KEY_KEYWORDS, it) }
                    }
                    if (listMode == 1) {
                        InsetDivider()
                        ActionRow("白名单", autoReplySelectedIdSummary(whitelist)) {
                            picker = ContactPickerRequest(
                                title = "选择白名单",
                                mode = ContactPickerMode.BOTH,
                                multiSelect = true,
                                existingValue = whitelist,
                                onValue = { whitelist = it; sp.edit().putString(RedPacketSettings.KEY_WHITELIST, it).apply() },
                                enableLabels = true
                            )
                        }
                    } else if (listMode == 2) {
                        InsetDivider()
                        ActionRow("黑名单", autoReplySelectedIdSummary(blacklist)) {
                            picker = ContactPickerRequest(
                                title = "选择黑名单",
                                mode = ContactPickerMode.BOTH,
                                multiSelect = true,
                                existingValue = blacklist,
                                onValue = { blacklist = it; sp.edit().putString(RedPacketSettings.KEY_BLACKLIST, it).apply() },
                                enableLabels = true
                            )
                        }
                    }
                }
            }
            item {
                SettingsDisclosure(
                    title = "抢到后处理",
                    summary = "${if (replyEnabled) "自动回复已开" else "自动回复关闭"} · ${if (notifySystemEnabled || notifyToastEnabled || notifySoundEnabled || announceEnabled || notifyFailedSystemEnabled || notifyFailedToastEnabled) "提醒已开" else "提醒关闭"}",
                    expanded = processingExpanded,
                    onToggle = {
                        if (processingExpanded && timeFormat.isNotBlank() && !PaymentTemplateTimeFormatter.isValidPattern(timeFormat))
                            Toast.makeText(context, "请先修正时间格式", Toast.LENGTH_SHORT).show()
                        else processingExpanded = !processingExpanded
                    }
                )
            }
            if (processingExpanded) {
            item { SettingsSectionTitle(text = "模板变量") }
            item {
                SettingsCard {
                    InputRow(
                        "时间变量格式",
                        if (timeFormat.isNotBlank() && !PaymentTemplateTimeFormatter.isValidPattern(timeFormat)) "时间格式无效，保留上次有效配置" else "用于 {time}，例如 yyyy-MM-dd HH:mm:ss",
                        timeFormat,
                        onEditingFinished = { if (processingExpanded && it.isBlank()) sp.edit().putString(RedPacketSettings.KEY_TIME_FORMAT, PaymentTemplateTimeFormatter.normalizePattern(it)).apply() }
                    ) { timeFormat = it; if (it.isNotBlank() && PaymentTemplateTimeFormatter.isValidPattern(it)) sp.edit().putString(RedPacketSettings.KEY_TIME_FORMAT, PaymentTemplateTimeFormatter.normalizePattern(it)).apply() }
                }
            }
            item { SettingsSectionTitle(text = "自动祝福") }
            item {
                SettingsCard {
                    SwitchRow(wishEnabled, "自动发送祝福语", "抢到红包后发送祝福语") {
                        wishEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_WISH_ENABLE, it).apply()
                    }
                    InsetDivider()
                    InputRow("祝福语内容", "抢到红包后发送的文字", wishText, onEditingFinished = { if (processingExpanded) persistText(RedPacketSettings.KEY_WISH_TEXT, it, true) }) { wishText = it; persistText(RedPacketSettings.KEY_WISH_TEXT, it) }
                    InsetDivider()
                    SwitchRow(sp, RedPacketSettings.KEY_WISH_RANDOM, "随机祝福语", "从模板中随机选择", false)

                }
            }
            item { SettingsSectionTitle(text = "自动回复") }
            item {
                SettingsCard {
                    SwitchRow(replyEnabled, "抢到后自动回复", "发送到红包所在会话") {
                        replyEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_REPLY_ENABLE, it).apply()
                    }
                    InsetDivider()
                    SelectRow(
                        title = "私聊红包回复",
                        summary = describeRedPacketReplySteps(privateReplySteps),
                        onClick = { editingReplyStepsForGroup = false }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "群红包回复",
                        summary = describeRedPacketReplySteps(groupReplySteps),
                        onClick = { editingReplyStepsForGroup = true }
                    )

                }
            }
            item { SettingsSectionTitle(text = "通知提醒") }
            item {
                SettingsCard {
                    SwitchRow(notifySystemEnabled, "通知栏提醒", "抢到红包时提醒") {
                        notifySystemEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_SYSTEM_ENABLE, it).apply()
                    }
                    InsetDivider()
                    VariableInputRow(
                        title = "通知栏文案",
                        summary = "支持下方变量",
                        value = notifyText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (processingExpanded) persistText(RedPacketSettings.KEY_NOTIFY_TEXT, it, true) },
                        onValueChange = { notifyText = it; persistText(RedPacketSettings.KEY_NOTIFY_TEXT, it) }
                    )

                    InsetDivider()
                    SwitchRow(notifyToastEnabled, "浮窗提醒", "抢到红包时短暂提示") {
                        notifyToastEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_TOAST_ENABLE, it).apply()
                    }
                    InsetDivider()
                    VariableInputRow(
                        title = "浮窗文案",
                        summary = "支持下方变量",
                        value = notifyToastText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (processingExpanded) persistText(RedPacketSettings.KEY_NOTIFY_TOAST_TEXT, it, true) },
                        onValueChange = { notifyToastText = it; persistText(RedPacketSettings.KEY_NOTIFY_TOAST_TEXT, it) }
                    )

                    InsetDivider()
                    SwitchRow(notifySoundEnabled, "通知铃声", "开启后播放通知铃声") {
                        notifySoundEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_SOUND_ENABLE, it).apply()
                    }
                    InsetDivider()
                    OptionRow(
                        sp = sp,
                        key = RedPacketSettings.KEY_NOTIFY_SOUND_MODE,
                        title = "铃声模式",
                        options = optionItems(
                            "选择系统铃声" to RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM,
                            "从文件选择铃声" to RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM
                        ),
                        defaultValue = RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM,
                        onValueChanged = { mode ->
                            notifySoundMode = mode
                            notifySoundUri = ""
                            sp.edit()
                                .putInt(RedPacketSettings.KEY_NOTIFY_SOUND_MODE, mode)
                                .putString(RedPacketSettings.KEY_NOTIFY_SOUND_URI, "")
                                .apply()
                            optionPicker = null
                            val activity = context as? Activity
                            if (activity == null) {
                                Toast.makeText(context, "当前页面无法打开铃声选择器", Toast.LENGTH_SHORT).show()
                            } else {
                                val onPicked: (String) -> Unit = { uri ->
                                    if (uri.isNotBlank()) {
                                        val storedUri = if (mode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                                            CustomNotificationRuntime.freezeRingtoneUri(context, uri)
                                        } else {
                                            uri
                                        }
                                        notifySoundUri = storedUri
                                        sp.edit()
                                            .putInt(RedPacketSettings.KEY_NOTIFY_SOUND_MODE, mode)
                                            .putString(RedPacketSettings.KEY_NOTIFY_SOUND_URI, storedUri)
                                            .apply()
                                    }
                                }
                                if (mode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                                    RingtonePickerBridge.launchFile(activity, onPicked)
                                } else {
                                    RingtonePickerBridge.launchSystem(activity, notifySoundUri, onPicked)
                                }
                            }
                        }
                    )
                    InsetDivider()
                    InfoRow("当前铃声", ringtoneDisplayName(context, notifySoundUri, notifySoundMode))

                    InsetDivider()
                    SwitchRow(sp, RedPacketSettings.KEY_NOTIFY_VIBRATE_ENABLE, "通知震动", "开启后触发通知震动", false)
                    InsetDivider()
                    SwitchRow(announceEnabled, "抢到红包播报", "抢到后用系统语音播报") {
                        announceEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_ANNOUNCE_ENABLE, it).apply()
                    }
                    InsetDivider()
                    VariableInputRow(
                        title = "播报文案",
                        summary = "支持下方变量",
                        value = announceText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (processingExpanded) persistText(RedPacketSettings.KEY_ANNOUNCE_TEXT, it, true) },
                        onValueChange = { announceText = it; persistText(RedPacketSettings.KEY_ANNOUNCE_TEXT, it) }
                    )

                    InsetDivider()
                    SwitchRow(notifyFailedSystemEnabled, "未抢到提醒", "未抢到红包时通知") {
                        notifyFailedSystemEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_SYSTEM_ENABLE, it).apply()
                    }
                    InsetDivider()
                    VariableInputRow(
                        title = "未抢到通知栏文案",
                        summary = "支持下方变量",
                        value = failText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (processingExpanded) persistText(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, it, true) },
                        onValueChange = { failText = it; persistText(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, it) }
                    )

                    InsetDivider()
                    SwitchRow(notifyFailedToastEnabled, "未抢到浮窗", "未抢到红包时短暂提示") {
                        notifyFailedToastEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_ENABLE, it).apply()
                    }
                    InsetDivider()
                    VariableInputRow(
                        title = "未抢到浮窗文案",
                        summary = "支持下方变量",
                        value = failToastText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (processingExpanded) persistText(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_TEXT, it, true) },
                        onValueChange = { failToastText = it; persistText(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_TEXT, it) }
                    )

                }
            }
            }
            item { SettingsSectionTitle(text = "假红包兼容") }
            item {
                SettingsCard {
                    SwitchRow(sp, RedPacketSettings.KEY_FAKE_PACKET_ENABLE, "开启假红包", "发送假红包时修正请求和响应", false)
                    InsetDivider()
                    SwitchRow(sp, RedPacketSettings.KEY_FAKE_PACKET_RECEIVE_ENABLE, "领取假红包", "收到异常群 ID 红包时尝试修正", false)
                }
            }
            item { StatsCard(sp) }
        }
    }
        }
    }
}

@Composable
private fun RedPacketTemplateListPage(
    templates: List<RedPacketRuleTemplate>,
    onBack: () -> Unit,
    onOpenTemplate: (Int, RedPacketRuleTemplate) -> Unit,
    onAddTemplate: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "红包模板",
        largeTitle = "红包模板",
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
            item { SettingsSectionTitle(text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。模板只配置抢包策略，适用聊天在“适用聊天”里分配。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SelectRow(
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeRedPacketTemplate(template),
                                onClick = { onOpenTemplate(index, template) }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }

                    InsetDivider()
                    AddActionRow("新增模板", onClick = onAddTemplate)
                }
            }
        }
    }
}

@Composable
private fun RedPacketBindingListPage(
    bindings: List<RedPacketRuleBinding>,
    templates: List<RedPacketRuleTemplate>,
    onBack: () -> Unit,
    onOpenBinding: (Int, RedPacketRuleBinding) -> Unit,
    onAddBinding: () -> Unit,
    onDeleteBindings: (List<RedPacketRuleBinding>) -> Unit
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
                (lower.isEmpty() || redPacketBindingMatchesQuery(binding, templates, lower))
        }
    val listTitle = when {
        bindings.isEmpty() -> "适用聊天"
        lower.isEmpty() -> "适用聊天 · ${bindings.size} 项"
        else -> "适用聊天 · ${visibleBindings.size}/${bindings.size} 项"
    }
    val visibleIds = visibleBindings.mapTo(LinkedHashSet()) { it.second.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val selectedBindings = bindings.filter { it.id in selectedIds }
    val selectionBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = selectionBackState, isBackEnabled = batchDeleteMode && LocalSettingsBackRegistrationEnabled.current, onBackCompleted = {
        batchDeleteMode = false
        selectedIds = emptySet()
    })
    PageScaffold(
        title = "适用聊天",
        largeTitle = "适用聊天",
        scrollBehavior = scrollBehavior,
        onBack = { if (batchDeleteMode) { batchDeleteMode = false; selectedIds = emptySet() } else onBack() },
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
            item {
                SettingsCard {
                    ConversationRuleCategoryTabs(
                        selected = category,
                        onSelected = { category = it },
                        includeOfficial = false
                    )
                }
            }
            item {
                SettingsCard {
                    InputRow(
                        title = "搜索聊天",
                        summary = "昵称 / wxid / 群号 / 模板名",
                        value = query,
                        onValueChange = { query = it }
                    )
                }
            }
            item { SettingsSectionTitle(text = listTitle) }


            item {
                SettingsCard {
                    if (batchDeleteMode) {
                        if (visibleIds.isNotEmpty()) ContentActionRow(if (allVisibleSelected) "取消全选" else "全选") {
                            selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
                        }
                        ContentActionRow("删除（${selectedBindings.size}）") {
                            if (selectedBindings.isEmpty()) Toast.makeText(context, "请先选择适用聊天", Toast.LENGTH_SHORT).show()
                            else showDeleteConfirm = true
                        }
                    } else {
                        if (bindings.isNotEmpty()) ContentActionRow("批量删除") { batchDeleteMode = true; selectedIds = emptySet() }
                        AddActionRow("添加聊天", onClick = onAddBinding)
                    }

                    InsetDivider()
                    if (bindings.isEmpty()) {
                        EmptyText("暂无适用聊天。添加群聊或好友后，再给它分配红包模板。")
                    } else if (visibleBindings.isEmpty()) {
                        EmptyText("没有匹配结果。可按昵称、ID 或模板名搜索。")
                    } else {
                        visibleBindings.forEachIndexed { rowIndex, (index, binding) ->
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = binding.label.ifBlank { binding.targetId },
                                        value = index,
                                        summary = describeRedPacketBinding(binding, templates)
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
                                    summary = describeRedPacketBinding(binding, templates),
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
    if (showDeleteConfirm) {
        SettingsWindowDialog(
            show = true,
            title = "确认批量删除",
            onDismissRequest = { showDeleteConfirm = false },
            content = {
                Column {
                    Text(
                        text = buildString {
                            append("将删除已选的 ${selectedBindings.size} 个适用聊天，此操作不可撤销。")
                            val names = selectedBindings.take(6).joinToString("、") {
                                it.label.ifBlank { it.targetId }
                            }
                            if (names.isNotBlank()) append("\n\n$names")
                            if (selectedBindings.size > 6) append(" 等")
                        },
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        TextButton(
                            text = "确认删除",
                            onClick = {
                                val targets = selectedBindings
                                showDeleteConfirm = false
                                batchDeleteMode = false
                                selectedIds = emptySet()
                                onDeleteBindings(targets)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun RedPacketTemplatePage(
    context: Context,
    request: RedPacketTemplateEditorRequest,
    onBack: () -> Unit,
    onSave: (RedPacketRuleTemplate) -> Unit,
    onDelete: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RedPacketSettings.PREFS_NAME) }
    var name by remember(request) { mutableStateOf(request.template.name) }
    var enabled by remember(request) { mutableStateOf(request.template.enabled) }
    var grabMode by remember(request) { mutableStateOf(request.template.grabMode) }
    var delayMode by remember(request) { mutableStateOf(normalizeRedPacketDelayModeForUi(request.template)) }
    var delayMs by remember(request) { mutableStateOf(request.template.delayMs.coerceAtLeast(0L).toString()) }
    var randomMinMs by remember(request) { mutableStateOf(request.template.randomMinMs.coerceAtLeast(0L).toString()) }
    var randomMaxMs by remember(request) { mutableStateOf(request.template.randomMaxMs.coerceAtLeast(0L).toString()) }
    var skipSelf by remember(request) { mutableStateOf(request.template.skipSelf) }
    var keywordMode by remember(request) { mutableStateOf(request.template.keywordMode) }
    var keywords by remember(request) { mutableStateOf(request.template.keywords) }
    var quietEnabled by remember(request) { mutableStateOf(request.template.quietEnabled) }
    var quietStart by remember(request) { mutableStateOf(formatRedPacketSecond(request.template.quietStartSecond)) }
    var quietEnd by remember(request) { mutableStateOf(formatRedPacketSecond(request.template.quietEndSecond)) }
    val initialPrivateReplySteps = remember(request) {
        normalizeRedPacketReplyStepsForUi(
            request.template.replySteps,
            request.template.replyMode,
            request.template.replyText,
            request.template.replyDelayMs,
            request.template.replyRandom
        )
    }
    var privateReplySteps by remember(request) {
        mutableStateOf(initialPrivateReplySteps)
    }
    var groupReplySteps by remember(request) {
        mutableStateOf(
            request.template.groupReplySteps
                ?.map(::normalizeRedPacketReplyStepForUi)
                ?: initialPrivateReplySteps
        )
    }
    var notifySystemEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifySystemEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SYSTEM_ENABLE, false)
            }
        )
    }
    var notifyToastEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyToastEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_TOAST_ENABLE, false)
            }
        )
    }
    var notifySoundEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifySoundEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SOUND_ENABLE, false)
            }
        )
    }
    var notifySoundMode by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifySoundMode
            } else {
                sp.getInt(RedPacketSettings.KEY_NOTIFY_SOUND_MODE, RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM)
            }
        )
    }
    var notifyVibrateEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyVibrateEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_VIBRATE_ENABLE, false)
            }
        )
    }
    var notifySoundUri by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifySoundUri
            } else {
                sp.getString(RedPacketSettings.KEY_NOTIFY_SOUND_URI, "") ?: ""
            }
        )
    }
    var notifyText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyText
            } else {
                sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
            }
        )
    }
    var notifyToastText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyToastText
            } else {
                sp.getString(
                    RedPacketSettings.KEY_NOTIFY_TOAST_TEXT,
                    sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
                ) ?: "抢到红包 {amount} 元"
            }
        )
    }
    var notifyFailedSystemEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyFailedSystemEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_SYSTEM_ENABLE, false)
            }
        )
    }
    var notifyFailedToastEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyFailedToastEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_ENABLE, false)
            }
        )
    }
    var notifyFailedText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyFailedText
            } else {
                sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "未抢到红包"
            }
        )
    }
    var notifyFailedToastText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyFailedToastText
            } else {
                sp.getString(
                    RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_TEXT,
                    sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "未抢到红包"
                ) ?: "未抢到红包"
            }
        )
    }
    var announceEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.announceEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_ANNOUNCE_ENABLE, false)
            }
        )
    }
    var announceText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.announceText
            } else {
                sp.getString(RedPacketSettings.KEY_ANNOUNCE_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
            }
        )
    }
    var editingReplyStepsForGroup by remember(request) { mutableStateOf<Boolean?>(null) }
    var lastSaved by remember(request) { mutableStateOf(request.template) }
    var groupReplyEdited by remember(request) { mutableStateOf(false) }
    var privateReplyEdited by remember(request) { mutableStateOf(false) }
    var notificationsEdited by remember(request) { mutableStateOf(false) }
    var validKeywords by remember(request) { mutableStateOf(keywords) }
    var validNotifyText by remember(request) { mutableStateOf(notifyText) }
    var validNotifyToastText by remember(request) { mutableStateOf(notifyToastText) }
    var validFailedText by remember(request) { mutableStateOf(notifyFailedText) }
    var validFailedToastText by remember(request) { mutableStateOf(notifyFailedToastText) }
    var validAnnounceText by remember(request) { mutableStateOf(announceText) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun persist(create: Boolean = false) {
        if (!create && !request.canDelete) return
        if (create && (privateReplySteps + groupReplySteps).any { it.content.isBlank() }) {
            Toast.makeText(context, "请完善或移除未完成的回复", Toast.LENGTH_SHORT).show()
            return
        }
        val parsedDelay = delayMs.toLongOrNull()?.takeIf { it in 0L..600000L }
        val minDelay = randomMinMs.toLongOrNull()?.takeIf { it in 0L..600000L }
        val maxDelay = randomMaxMs.toLongOrNull()?.takeIf { minDelay != null && it in minDelay..600000L }
        val delayValid = when (delayMode) {
            RedPacketRuleConfig.DELAY_CUSTOM -> parsedDelay != null
            RedPacketRuleConfig.DELAY_RANDOM -> minDelay != null && maxDelay != null
            else -> true
        }
        if (create && !delayValid) {
            Toast.makeText(context, "请输入有效的延迟或延迟范围", Toast.LENGTH_SHORT).show()
            return
        }
        val first = privateReplySteps.firstOrNull()
        val saveNotification = lastSaved.notificationConfigured || notificationsEdited
        val savePrivateReply = privateReplyEdited || create
        val next = lastSaved.copy(
            name = name.trim().ifBlank { if (create) "模板 ${request.index + 1}" else lastSaved.name },
            enabled = enabled,
            grabMode = grabMode,
            delayMode = if (delayValid) delayMode else lastSaved.delayMode,
            delayMs = if (!delayValid) lastSaved.delayMs else if (delayMode == RedPacketRuleConfig.DELAY_CUSTOM) parsedDelay!! else 0L,
            randomMinMs = if (minDelay != null && maxDelay != null) minDelay else lastSaved.randomMinMs,
            randomMaxMs = if (minDelay != null && maxDelay != null) maxDelay else lastSaved.randomMaxMs,
            skipSelf = skipSelf,
            keywordMode = keywordMode,
            keywords = validKeywords.trim(),
            quietEnabled = quietEnabled,
            quietStartSecond = parseRedPacketSecond(quietStart, lastSaved.quietStartSecond),
            quietEndSecond = parseRedPacketSecond(quietEnd, lastSaved.quietEndSecond),
            replyMode = if (savePrivateReply) first?.mode ?: RedPacketRuleConfig.REPLY_OFF else lastSaved.replyMode,
            replyText = if (savePrivateReply) first?.content.orEmpty() else lastSaved.replyText,
            replyDelayMs = if (savePrivateReply) first?.delayMs ?: 0L else lastSaved.replyDelayMs,
            replyRandom = if (savePrivateReply) first?.random ?: false else lastSaved.replyRandom,
            replySteps = if (savePrivateReply) privateReplySteps else lastSaved.replySteps,
            groupReplySteps = if (lastSaved.groupReplySteps != null || groupReplyEdited || create) groupReplySteps else null,
            notificationConfigured = saveNotification,
            notifySystemEnabled = if (saveNotification) notifySystemEnabled else lastSaved.notifySystemEnabled,
            notifyToastEnabled = if (saveNotification) notifyToastEnabled else lastSaved.notifyToastEnabled,
            notifySoundEnabled = if (saveNotification) notifySoundEnabled else lastSaved.notifySoundEnabled,
            notifySoundMode = if (saveNotification) notifySoundMode else lastSaved.notifySoundMode,
            notifyVibrateEnabled = if (saveNotification) notifyVibrateEnabled else lastSaved.notifyVibrateEnabled,
            notifySoundUri = if (saveNotification) notifySoundUri.trim() else lastSaved.notifySoundUri,
            notifyText = if (saveNotification) validNotifyText else lastSaved.notifyText,
            notifyToastText = if (saveNotification) validNotifyToastText else lastSaved.notifyToastText,
            notifyFailedSystemEnabled = if (saveNotification) notifyFailedSystemEnabled else lastSaved.notifyFailedSystemEnabled,
            notifyFailedToastEnabled = if (saveNotification) notifyFailedToastEnabled else lastSaved.notifyFailedToastEnabled,
            notifyFailedText = if (saveNotification) validFailedText else lastSaved.notifyFailedText,
            notifyFailedToastText = if (saveNotification) validFailedToastText else lastSaved.notifyFailedToastText,
            announceEnabled = if (saveNotification) announceEnabled else lastSaved.announceEnabled,
            announceText = if (saveNotification) validAnnounceText else lastSaved.announceText
        )
        lastSaved = next
        onSave(next)
    }

    SettingsRouteTransition(
        targetState = editingReplyStepsForGroup,
        label = "RedPacketTemplateReplyRoute",
        depthOf = { if (it == null) 0 else 1 }
    ) { group ->
        if (group != null) {
        RedPacketReplyStepsPage(
            context = context,
            title = if (group) "群红包回复" else "私聊红包回复",
            initialSteps = if (group) groupReplySteps else privateReplySteps,
            draftOnly = !request.canDelete,
            onBack = { editingReplyStepsForGroup = null },
            onSave = { steps ->
                if (group) {
                    groupReplyEdited = true
                    groupReplySteps = steps
                } else {
                    privateReplyEdited = true
                    privateReplySteps = steps
                    if (lastSaved.groupReplySteps == null && !groupReplyEdited) groupReplySteps = steps
                }
                persist()
            }
        )
        } else PageScaffold(
        title = name.ifBlank { "红包模板" },
        largeTitle = name.ifBlank { "红包模板" },
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
            item { SettingsSectionTitle(text = "模板") }
            item {
                SettingsCard {
                    InputRow("模板名称", if (name.isBlank() && request.canDelete) "请输入名称，保留上次有效配置" else "例如：慢抢红包群、只抢口令红包", name) { name = it; persist() }
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "模板",
                        summary = "关闭后使用该模板的聊天不会自动抢",
                        onCheckedChange = { enabled = it; persist() }
                    )
                }
            }
            item { SettingsSectionTitle(text = "抢包") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "抢包模式",
                        summary = if (grabMode == 1) "静默抢包" else "打开红包页面",
                        options = optionItems("打开红包页面" to 0, "静默抢包" to 1),
                        currentValue = grabMode,
                        onValueChanged = { grabMode = it; persist() }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = skipSelf,
                        title = "跳过自己的红包",
                        summary = "自己发出的红包不会抢",
                        onCheckedChange = { skipSelf = it; persist() }
                    )
                }
            }
            item { SettingsSectionTitle(text = "延迟") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "抢包延迟",
                        summary = redPacketDelayModeLabel(delayMode),
                        options = redPacketDelayModeOptions(includeRandom = true),
                        currentValue = delayMode,
                        onValueChanged = { delayMode = it; persist() }
                    )
                    if (delayMode == RedPacketRuleConfig.DELAY_CUSTOM) {
                        InsetDivider()
                        NumberInputRow("自定义延迟", if (delayMs.toLongOrNull()?.let { it in 0L..600000L } != true) "请输入 0–600000，保留上次有效配置" else "单位 ms，0 表示不延迟", delayMs) { delayMs = it; persist() }
                    } else if (delayMode == RedPacketRuleConfig.DELAY_RANDOM) {
                        InsetDivider()
                        NumberInputRow("最小延迟", "单位 ms", randomMinMs) { randomMinMs = it; persist() }
                        InsetDivider()
                        NumberInputRow("最大延迟", "单位 ms，不能小于最小延迟", randomMaxMs) { randomMaxMs = it; persist() }
                        if (randomMinMs.toLongOrNull()?.let { min -> min in 0L..600000L && randomMaxMs.toLongOrNull()?.let { it in min..600000L } == true } != true) {
                            InsetDivider(); InfoRow("延迟范围", "请输入有效范围，保留上次有效配置")
                        }
                    }
                }
            }
            item { SettingsSectionTitle(text = "过滤") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "关键词过滤",
                        summary = when (keywordMode) {
                            1 -> "只抢含关键词"
                            2 -> "屏蔽含关键词"
                            else -> "不限关键词"
                        },
                        options = optionItems("不限关键词" to 0, "只抢含关键词" to 1, "屏蔽含关键词" to 2),
                        currentValue = keywordMode,
                        onValueChanged = {
                            keywordMode = it
                            persist()
                        }
                    )
                    if (keywordMode != 0) {
                        InsetDivider()
                        InputRow(
                            title = "关键词",
                            summary = "多个用 |、逗号或换行分隔",
                            value = keywords,
                            minLines = 2,
                            onEditingFinished = { if (it.isBlank()) { validKeywords = ""; persist() } },
                            onValueChange = { keywords = it; if (it.isNotBlank()) { validKeywords = it; persist() } }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = quietEnabled,
                        title = "禁抢时段",
                        summary = "在指定时间段内不抢红包",
                        onCheckedChange = { quietEnabled = it; persist() }
                    )
                    InsetDivider()
                    TimeOfDayPickerRow("开始时间", quietStart) { quietStart = it; persist() }
                    InsetDivider()
                    TimeOfDayPickerRow("结束时间", quietEnd) { quietEnd = it; persist() }

                }
            }
            item { SettingsSectionTitle(text = "抢后回复") }
            item {
                SettingsCard {
                    SelectRow(
                        title = "私聊红包回复",
                        summary = describeRedPacketReplySteps(privateReplySteps),
                        onClick = { editingReplyStepsForGroup = false }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "群红包回复",
                        summary = describeRedPacketReplySteps(groupReplySteps),
                        onClick = { editingReplyStepsForGroup = true }
                    )
                }
            }
            item { SettingsSectionTitle(text = "通知提醒") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = notifySystemEnabled,
                        title = "通知栏提醒",
                        summary = "抢到红包时提醒",
                        onCheckedChange = { notifySystemEnabled = it; notificationsEdited = true; persist() }
                    )
                    InsetDivider()
                    VariableInputRow(
                        title = "通知栏文案",
                        summary = "支持下方变量",
                        value = notifyText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (it.isBlank()) { validNotifyText = ""; notificationsEdited = true; persist() } },
                        onValueChange = { notifyText = it; if (it.isNotBlank()) { validNotifyText = it; notificationsEdited = true; persist() } }
                    )

                    InsetDivider()
                    SwitchRow(
                        checked = notifyToastEnabled,
                        title = "浮窗提醒",
                        summary = "抢到红包时短暂提示",
                        onCheckedChange = { notifyToastEnabled = it; notificationsEdited = true; persist() }
                    )
                    InsetDivider()
                    VariableInputRow(
                        title = "浮窗文案",
                        summary = "支持下方变量",
                        value = notifyToastText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (it.isBlank()) { validNotifyToastText = ""; notificationsEdited = true; persist() } },
                        onValueChange = { notifyToastText = it; if (it.isNotBlank()) { validNotifyToastText = it; notificationsEdited = true; persist() } }
                    )

                    InsetDivider()
                    SwitchRow(
                        checked = notifySoundEnabled,
                        title = "通知铃声",
                        summary = "开启后播放通知铃声",
                        onCheckedChange = { notifySoundEnabled = it; notificationsEdited = true; persist() }
                    )
                    InsetDivider()
                    PopupOptionRow(
                        title = "铃声模式",
                        summary = if (notifySoundMode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) "从文件选择铃声" else "选择系统铃声",
                        options = optionItems(
                            "选择系统铃声" to RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM,
                            "从文件选择铃声" to RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM
                        ),
                        currentValue = notifySoundMode,
                        onValueChanged = {
                            notifySoundMode = it
                            notifySoundUri = ""
                            notificationsEdited = true
                            persist()
                        }
                    )
                    InsetDivider()
                    ActionRow("选择铃声", ringtoneDisplayName(context, notifySoundUri, notifySoundMode)) {
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开铃声选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            val onPicked: (String) -> Unit = { uri ->
                                if (uri.isNotBlank()) {
                                    notifySoundUri = if (notifySoundMode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                                        CustomNotificationRuntime.freezeRingtoneUri(context, uri)
                                    } else {
                                        uri
                                    }
                                    notificationsEdited = true
                                    persist()
                                }
                            }
                            if (notifySoundMode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                                RingtonePickerBridge.launchFile(activity, onPicked)
                            } else {
                                RingtonePickerBridge.launchSystem(activity, notifySoundUri, onPicked)
                            }
                        }
                    }

                    InsetDivider()
                    SwitchRow(
                        checked = notifyVibrateEnabled,
                        title = "通知震动",
                        summary = "开启后触发通知震动",
                        onCheckedChange = { notifyVibrateEnabled = it; notificationsEdited = true; persist() }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = announceEnabled,
                        title = "抢到红包播报",
                        summary = "抢到后用系统语音播报",
                        onCheckedChange = { announceEnabled = it; notificationsEdited = true; persist() }
                    )
                    InsetDivider()
                    VariableInputRow(
                        title = "播报文案",
                        summary = "支持下方变量",
                        value = announceText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (it.isBlank()) { validAnnounceText = ""; notificationsEdited = true; persist() } },
                        onValueChange = { announceText = it; if (it.isNotBlank()) { validAnnounceText = it; notificationsEdited = true; persist() } }
                    )

                    InsetDivider()
                    SwitchRow(
                        checked = notifyFailedSystemEnabled,
                        title = "未抢到提醒",
                        summary = "未抢到红包时通知",
                        onCheckedChange = { notifyFailedSystemEnabled = it; notificationsEdited = true; persist() }
                    )
                    InsetDivider()
                    VariableInputRow(
                        title = "未抢到通知栏文案",
                        summary = "支持下方变量",
                        value = notifyFailedText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (it.isBlank()) { validFailedText = ""; notificationsEdited = true; persist() } },
                        onValueChange = { notifyFailedText = it; if (it.isNotBlank()) { validFailedText = it; notificationsEdited = true; persist() } }
                    )

                    InsetDivider()
                    SwitchRow(
                        checked = notifyFailedToastEnabled,
                        title = "未抢到浮窗",
                        summary = "未抢到红包时短暂提示",
                        onCheckedChange = { notifyFailedToastEnabled = it; notificationsEdited = true; persist() }
                    )
                    InsetDivider()
                    VariableInputRow(
                        title = "未抢到浮窗文案",
                        summary = "支持下方变量",
                        value = notifyFailedToastText,
                        variables = redPacketTemplateVariables,
                        onEditingFinished = { if (it.isBlank()) { validFailedToastText = ""; notificationsEdited = true; persist() } },
                        onValueChange = { notifyFailedToastText = it; if (it.isNotBlank()) { validFailedToastText = it; notificationsEdited = true; persist() } }
                    )

                }
            }

            item { SettingsSectionTitle(text = "操作") }
            item {
                SettingsCard {
                    if (!request.canDelete) ContentActionRow("创建") { persist(create = true) }
                    else ContentActionRow("删除") {
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
private fun RedPacketBindingPage(
    context: Context,
    request: RedPacketBindingEditorRequest,
    templates: List<RedPacketRuleTemplate>,
    onBack: () -> Unit,
    onSave: (RedPacketRuleBinding) -> Unit,
    onDelete: () -> Unit
) {
    var enabled by remember(request) { mutableStateOf(request.binding.enabled) }
    var templateId by remember(request) { mutableStateOf(request.binding.templateId) }
    var customRules by remember(request) { mutableStateOf(request.binding.customRules) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val targetTitle = request.binding.label.ifBlank { request.binding.targetId }
    fun persist(create: Boolean = false) {
        if (request.canDelete || create) onSave(request.binding.copy(enabled = enabled, templateId = templateId, customRules = customRules))
    }

    PageScaffold(
        title = targetTitle,
        largeTitle = targetTitle,
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
            item { SettingsSectionTitle(text = "聊天") }
            item {
                SettingsCard {
                    InfoRow("名称", targetTitle)
                    InsetDivider()
                    InfoRow("ID", request.binding.targetId)
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "自动抢红包",
                        summary = "关闭后这个聊天不会按模板自动抢红包",
                        onCheckedChange = { enabled = it; persist() }
                    )
                }
            }
            item { SettingsSectionTitle(text = "模板") }
            item {
                SettingsCard {
                    if (customRules) {
                        InfoRow("当前规则", "沿用原有专属规则；选择下方模板后改为使用模板")
                        InsetDivider()
                    }
                    OptionChoiceRow(
                        item = OptionItem("跟随默认规则", -1, "启用后按默认规则或旧版全局设置抢红包"),
                        selected = !customRules && templateId.isBlank(),
                        onClick = { templateId = ""; customRules = false; persist() }
                    )
                    if (templates.isNotEmpty()) InsetDivider()
                    templates.forEachIndexed { index, template ->
                        OptionChoiceRow(
                            item = OptionItem(
                                label = template.name.ifBlank { "模板 ${index + 1}" },
                                value = index,
                                summary = describeRedPacketTemplate(template)
                            ),
                            selected = !customRules && templateId == template.id,
                            onClick = { templateId = template.id; customRules = false; persist() }
                        )
                        if (index < templates.lastIndex) InsetDivider()
                    }
                }
            }

            item { SettingsSectionTitle(text = "操作") }
            item {
                SettingsCard {
                    if (!request.canDelete) ContentActionRow("创建") { persist(create = true) }
                    else ContentActionRow("移除") {
                        onDelete()
                        Toast.makeText(context, "适用聊天已移除", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
internal fun AntiRecallMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AntiRecallSettings.PREFS_NAME) }
    var noticeText by remember {
        mutableStateOf(
            normalizeAntiRecallNoticeText(sp.getString(
                AntiRecallSettings.KEY_NOTICE_TEXT,
                AntiRecallSettings.DEFAULT_NOTICE_TEXT
            ) ?: AntiRecallSettings.DEFAULT_NOTICE_TEXT)
        )
    }
    var noticeTimeFormat by remember {
        mutableStateOf(
            sp.getString(
                AntiRecallSettings.KEY_NOTICE_TIME_FORMAT,
                AntiRecallSettings.DEFAULT_NOTICE_TIME_FORMAT
            ) ?: AntiRecallSettings.DEFAULT_NOTICE_TIME_FORMAT
        )
    }
    val usesTimeVariable = noticeText.contains(AntiRecallSettings.VAR_SEND_TIME) ||
        noticeText.contains(AntiRecallSettings.VAR_RECALL_TIME)
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
                bottom = padding.calculateBottomPadding() + 20.dp
            )
        ) {
            item { SettingsSectionTitle(text = "防撤回") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        AntiRecallSettings.KEY_ENABLE,
                        "防撤回",
                        "保留被撤回的消息，并在下方插入提示",
                        AntiRecallSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        AntiRecallSettings.KEY_KEEP_SELF_RECALL,
                        "保留自己撤回",
                        "开启后自己撤回的消息也会保留",
                        AntiRecallSettings.DEFAULT_KEEP_SELF_RECALL
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        AntiRecallSettings.KEY_SHOW_NOTICE,
                        "显示撤回提示",
                        "在被撤回消息下方插入提示",
                        AntiRecallSettings.DEFAULT_SHOW_NOTICE
                    )
                    InsetDivider()
                    VariableInputRow(
                        title = "提示文案",
                        summary = "点击下方变量插入到光标位置",
                        value = noticeText,
                        variables = antiRecallTemplateVariables,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(AntiRecallSettings.KEY_NOTICE_TEXT, AntiRecallSettings.DEFAULT_NOTICE_TEXT).apply() },
                        onValueChange = { noticeText = it; if (it.isNotBlank()) sp.edit().putString(AntiRecallSettings.KEY_NOTICE_TEXT, it).apply() }
                    )
                    if (usesTimeVariable) {
                        InsetDivider()
                        InputRow(
                            "时间格式",
                            if (noticeTimeFormat.isNotBlank() && runCatching { SimpleDateFormat(noticeTimeFormat, Locale.getDefault()).format(Date()) }.isFailure) "时间格式无效，保留上次有效配置" else "使用日期格式，例如 yyyy-MM-dd HH:mm:ss",
                            noticeTimeFormat,
                            onEditingFinished = { if (it.isBlank()) sp.edit().putString(AntiRecallSettings.KEY_NOTICE_TIME_FORMAT, AntiRecallSettings.DEFAULT_NOTICE_TIME_FORMAT).apply() }
                        ) { noticeTimeFormat = it; if (it.isNotBlank() && runCatching { SimpleDateFormat(it, Locale.getDefault()).format(Date()) }.isSuccess) sp.edit().putString(AntiRecallSettings.KEY_NOTICE_TIME_FORMAT, it.trim()).apply() }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ClickHintTag() {
    Text(
        text = "单击",
        color = MiuixTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
