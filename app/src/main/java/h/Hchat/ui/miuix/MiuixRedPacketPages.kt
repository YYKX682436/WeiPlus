package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import h.Hchat.crash.CrashReportRuntime
import h.Hchat.crash.CrashReportSettings
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
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.antirecall.AntiRecallSettings
import h.Hchat.hooks.items.customnotify.CustomNotificationRuntime
import h.Hchat.hooks.items.payment.core.PaymentTemplateTimeFormatter
import h.Hchat.hooks.items.payment.core.RedPacketRuleBinding
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import h.Hchat.hooks.items.payment.core.RedPacketRuleTemplate
import h.Hchat.hooks.items.payment.core.RedPacketSettings
import h.Hchat.hooks.items.protobuf.ProtobufPacketRuntime
import h.Hchat.hooks.items.protobuf.ProtobufPacketSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
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
    var wishText by remember { mutableStateOf(sp.getString(RedPacketSettings.KEY_WISH_TEXT, "谢谢老板") ?: "谢谢老板") }
    var privateReplySteps by remember { mutableStateOf(loadGlobalRedPacketReplySteps(sp)) }
    var groupReplySteps by remember { mutableStateOf(loadGlobalGroupRedPacketReplySteps(sp)) }
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
        depthOf = { it.depth() }
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
                        val nextDefault = defaultTemplateId
                            .takeIf { id -> next.any { it.id == id } }
                            ?: next.firstOrNull()?.id.orEmpty()
                        templates = next
                        defaultTemplateId = nextDefault
                        persistRules(nextTemplates = next, nextDefaultTemplateId = nextDefault)
                        templateEditor = null
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
                        val base = if (request.index in bindings.indices) {
                            bindings.toMutableList().also { it.removeAt(request.index) }
                        } else {
                            bindings
                        }
                        val next = upsertRedPacketBindings(base, listOf(normalized))
                        bindings = next
                        persistRules(nextBindings = next)
                        bindingEditor = null
                    },
                    onDelete = {
                        if (request.index in bindings.indices) {
                            val next = bindings.toMutableList().also { it.removeAt(request.index) }
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
                    onConfirm = { selected ->
                        request.onValue(formatIds(selected.map { it.id }))
                        picker = null
                    }
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
                        if (group) groupReplySteps = steps else privateReplySteps = steps
                        editingReplyStepsForGroup = null
                        Toast.makeText(context, "回复步骤已更新", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            RedPacketRoute.Main -> PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = save@{
                    if (!PaymentTemplateTimeFormatter.isValidPattern(timeFormat)) {
                        Toast.makeText(context, "时间格式无效", Toast.LENGTH_SHORT).show()
                        return@save
                    }
                    val normalizedTimeFormat = PaymentTemplateTimeFormatter.normalizePattern(timeFormat)
                    val cleanPrivateReplySteps = cleanRedPacketReplySteps(privateReplySteps)
                    val cleanGroupReplySteps = cleanRedPacketReplySteps(groupReplySteps)
                    val firstReply = cleanPrivateReplySteps.firstOrNull()
                    val firstReplyText = firstReply?.content.orEmpty()
                    val firstReplyMode = firstReply?.mode ?: RedPacketRuleConfig.REPLY_OFF
                    val firstReplyDelayMs = firstReply?.delayMs?.coerceAtLeast(0L) ?: 0L
                    val randomMinDelay = grabRandomMinDelay.toIntOrNull()?.coerceIn(0, 600000) ?: 0
                    val randomMaxDelay = grabRandomMaxDelay.toIntOrNull()?.coerceIn(randomMinDelay, 600000) ?: randomMinDelay
                    sp.edit()
                        .putString(RedPacketSettings.KEY_KEYWORDS, if (keywordMode == 0) "" else keywords)
                        .putString(RedPacketSettings.KEY_WHITELIST, whitelist)
                        .putString(RedPacketSettings.KEY_BLACKLIST, blacklist)
                        .putString(RedPacketSettings.KEY_WISH_TEXT, wishText)
                        .putInt(RedPacketSettings.KEY_REPLY_TYPE, firstReplyMode)
                        .putString(RedPacketSettings.KEY_REPLY_TEMPLATES, if (redPacketReplyUsesText(firstReplyMode)) firstReplyText else "")
                        .putString(RedPacketSettings.KEY_REPLY_TEXT, if (redPacketReplyUsesText(firstReplyMode)) firstTemplate(firstReplyText) else "")
                        .putString(
                            RedPacketSettings.KEY_REPLY_MEDIA_PATHS,
                            if (firstReplyMode != RedPacketRuleConfig.REPLY_OFF && !redPacketReplyUsesText(firstReplyMode)) {
                                firstReplyText.trim()
                            } else {
                                ""
                            }
                        )
                        .putString(RedPacketSettings.KEY_REPLY_ITEMS, RedPacketRuleConfig.encodeReplySteps(cleanPrivateReplySteps))
                        .putString(RedPacketSettings.KEY_REPLY_GROUP_ITEMS, RedPacketRuleConfig.encodeReplySteps(cleanGroupReplySteps))
                        .putString(RedPacketSettings.KEY_NOTIFY_TEXT, notifyText)
                        .putString(RedPacketSettings.KEY_NOTIFY_TOAST_TEXT, notifyToastText)
                        .putString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, failText)
                        .putString(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_TEXT, failToastText)
                        .putString(RedPacketSettings.KEY_ANNOUNCE_TEXT, announceText)
                        .putString(RedPacketSettings.KEY_TIME_FORMAT, normalizedTimeFormat)
                        .putString(RedPacketSettings.KEY_NOTIFY_SOUND_URI, notifySoundUri.trim())
                        .putInt(RedPacketSettings.KEY_DELAY_MODE, grabDelayMode)
                        .putInt(
                            RedPacketSettings.KEY_DELAY_VALUE,
                            if (grabDelayMode == RedPacketRuleConfig.DELAY_CUSTOM) {
                                grabDelay.toIntOrNull()?.coerceIn(0, 10000) ?: 0
                            } else {
                                0
                            }
                        )
                        .putInt(RedPacketSettings.KEY_DELAY_RANDOM_MIN, randomMinDelay)
                        .putInt(RedPacketSettings.KEY_DELAY_RANDOM_MAX, randomMaxDelay)
                        .putInt(RedPacketSettings.KEY_REPLY_DELAY_VALUE, (firstReplyDelayMs / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        .putInt(RedPacketSettings.KEY_REPLY_DELAY_UNIT, 1)
                        .putBoolean(RedPacketSettings.KEY_REPLY_CUSTOM_ENABLE, firstReplyDelayMs > 0L)
                        .putBoolean(RedPacketSettings.KEY_REPLY_RANDOM, firstReply?.random ?: false)
                        .apply()
                    privateReplySteps = cleanPrivateReplySteps
                    groupReplySteps = cleanGroupReplySteps
                    timeFormat = normalizedTimeFormat
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
            item { SmallTitle(text = "核心功能") }
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
                    InsetDivider()
                    PopupOptionRow(
                        title = "抢包延迟",
                        summary = redPacketDelayModeLabel(grabDelayMode),
                        options = redPacketDelayModeOptions(includeRandom = true),
                        currentValue = grabDelayMode,
                        onValueChanged = { mode ->
                            grabDelayMode = mode
                            if (mode == RedPacketRuleConfig.DELAY_FIXED) grabDelay = "0"
                        }
                    )
                    if (grabDelayMode == RedPacketRuleConfig.DELAY_CUSTOM) {
                        InsetDivider()
                        NumberInputRow("自定义延迟", "单位 ms，保存后生效", grabDelay) { grabDelay = it }
                    } else if (grabDelayMode == RedPacketRuleConfig.DELAY_RANDOM) {
                        InsetDivider()
                        NumberInputRow("最小延迟", "单位 ms，保存后生效", grabRandomMinDelay) { grabRandomMinDelay = it }
                        InsetDivider()
                        NumberInputRow("最大延迟", "单位 ms，不能小于最小延迟", grabRandomMaxDelay) { grabRandomMaxDelay = it }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "规则管理") }
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "过滤规则") }
            item {
                SettingsCard {
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
                        onValueChanged = {
                            keywordMode = it
                            if (it == 0) keywords = ""
                        }
                    )
                    if (keywordMode != 0) {
                        InsetDivider()
                        InputRow("关键词列表", "多个关键词用 | 分隔", keywords) { keywords = it }
                    }
                    if (listMode == 1) {
                        InsetDivider()
                        ActionRow("白名单", autoReplySelectedIdSummary(whitelist)) {
                            picker = ContactPickerRequest(
                                title = "选择白名单",
                                mode = ContactPickerMode.BOTH,
                                multiSelect = true,
                                existingValue = whitelist,
                                onValue = { whitelist = it },
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
                                onValue = { blacklist = it },
                                enableLabels = true
                            )
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板变量") }
            item {
                SettingsCard {
                    InputRow(
                        "时间变量格式",
                        "用于 {time}，例如 yyyy-MM-dd HH:mm:ss",
                        timeFormat
                    ) { timeFormat = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "自动祝福") }
            item {
                SettingsCard {
                    SwitchRow(wishEnabled, "自动发送祝福语", "抢到红包后发送祝福语") {
                        wishEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_WISH_ENABLE, it).apply()
                    }
                    if (wishEnabled) {
                        InsetDivider()
                        InputRow("祝福语内容", "抢到红包后发送的文字", wishText) { wishText = it }
                        InsetDivider()
                        SwitchRow(sp, RedPacketSettings.KEY_WISH_RANDOM, "随机祝福语", "从模板中随机选择", false)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "自动回复") }
            item {
                SettingsCard {
                    SwitchRow(replyEnabled, "抢到后自动回复", "发送到红包所在会话") {
                        replyEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_REPLY_ENABLE, it).apply()
                    }
                    if (replyEnabled) {
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
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "通知提醒") }
            item {
                SettingsCard {
                    SwitchRow(notifySystemEnabled, "通知栏提醒", "抢到红包时提醒") {
                        notifySystemEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_SYSTEM_ENABLE, it).apply()
                    }
                    if (notifySystemEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "通知栏文案",
                            summary = "支持下方变量",
                            value = notifyText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(notifyToastEnabled, "浮窗提醒", "抢到红包时短暂提示") {
                        notifyToastEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_TOAST_ENABLE, it).apply()
                    }
                    if (notifyToastEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "浮窗文案",
                            summary = "支持下方变量",
                            value = notifyToastText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyToastText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(notifySoundEnabled, "通知铃声", "开启后播放通知铃声") {
                        notifySoundEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_SOUND_ENABLE, it).apply()
                    }
                    if (notifySoundEnabled) {
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
                    }
                    InsetDivider()
                    SwitchRow(sp, RedPacketSettings.KEY_NOTIFY_VIBRATE_ENABLE, "通知震动", "开启后触发通知震动", false)
                    InsetDivider()
                    SwitchRow(announceEnabled, "抢到红包播报", "抢到后用系统语音播报") {
                        announceEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_ANNOUNCE_ENABLE, it).apply()
                    }
                    if (announceEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "播报文案",
                            summary = "支持下方变量",
                            value = announceText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { announceText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(notifyFailedSystemEnabled, "未抢到提醒", "未抢到红包时通知") {
                        notifyFailedSystemEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_SYSTEM_ENABLE, it).apply()
                    }
                    if (notifyFailedSystemEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "未抢到通知栏文案",
                            summary = "支持下方变量",
                            value = failText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { failText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(notifyFailedToastEnabled, "未抢到浮窗", "未抢到红包时短暂提示") {
                        notifyFailedToastEnabled = it
                        sp.edit().putBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_ENABLE, it).apply()
                    }
                    if (notifyFailedToastEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "未抢到浮窗文案",
                            summary = "支持下方变量",
                            value = failToastText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { failToastText = it }
                        )
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "假红包兼容") }
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
    PageScaffold(
        title = "适用聊天",
        largeTitle = "适用聊天",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedBindings.size}）",
                    onPrimaryClick = {
                        if (selectedBindings.isEmpty()) {
                            Toast.makeText(context, "请先选择适用聊天", Toast.LENGTH_SHORT).show()
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
                    primaryText = "添加聊天",
                    onPrimaryClick = onAddBinding,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = listTitle) }
            item {
                SettingsCard {
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
        WindowDialog(
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
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    editingReplyStepsForGroup?.let { group ->
        RedPacketReplyStepsPage(
            context = context,
            title = if (group) "群红包回复" else "私聊红包回复",
            initialSteps = if (group) groupReplySteps else privateReplySteps,
            onBack = { editingReplyStepsForGroup = null },
            onSave = { steps ->
                if (group) groupReplySteps = steps else privateReplySteps = steps
                editingReplyStepsForGroup = null
                Toast.makeText(context, "回复步骤已更新", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    PageScaffold(
        title = name.ifBlank { "红包模板" },
        largeTitle = name.ifBlank { "红包模板" },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存模板",
                onPrimaryClick = {
                    val customDelay = if (delayMode == RedPacketRuleConfig.DELAY_CUSTOM) {
                        delayMs.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L
                    } else {
                        0L
                    }
                    val minDelay = randomMinMs.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L
                    val maxDelay = randomMaxMs.toLongOrNull()?.coerceIn(minDelay, 600000L) ?: minDelay
                    val cleanPrivateReplySteps = cleanRedPacketReplySteps(privateReplySteps)
                    val cleanGroupReplySteps = cleanRedPacketReplySteps(groupReplySteps)
                    val firstReply = cleanPrivateReplySteps.firstOrNull()
                    onSave(
                        RedPacketRuleTemplate(
                            id = request.template.id,
                            name = name.trim().ifBlank { "模板 ${request.index + 1}" },
                            enabled = enabled,
                            grabMode = grabMode,
                            delayMode = delayMode,
                            delayMs = customDelay,
                            randomMinMs = minDelay,
                            randomMaxMs = maxDelay,
                            skipSelf = skipSelf,
                            listMode = request.template.listMode,
                            whitelist = request.template.whitelist,
                            blacklist = request.template.blacklist,
                            keywordMode = keywordMode,
                            keywords = if (keywordMode == 0) "" else keywords.trim(),
                            quietEnabled = quietEnabled,
                            quietStartSecond = parseRedPacketSecond(quietStart, request.template.quietStartSecond),
                            quietEndSecond = parseRedPacketSecond(quietEnd, request.template.quietEndSecond),
                            replyMode = firstReply?.mode ?: RedPacketRuleConfig.REPLY_OFF,
                            replyText = firstReply?.content.orEmpty(),
                            replyDelayMs = firstReply?.delayMs ?: 0L,
                            replyRandom = firstReply?.random ?: false,
                            replySteps = cleanPrivateReplySteps,
                            groupReplySteps = cleanGroupReplySteps,
                            notificationConfigured = true,
                            notifySystemEnabled = notifySystemEnabled,
                            notifyToastEnabled = notifyToastEnabled,
                            notifySoundEnabled = notifySoundEnabled,
                            notifySoundMode = notifySoundMode,
                            notifyVibrateEnabled = notifyVibrateEnabled,
                            notifySoundUri = notifySoundUri.trim(),
                            notifyText = notifyText,
                            notifyToastText = notifyToastText,
                            notifyFailedSystemEnabled = notifyFailedSystemEnabled,
                            notifyFailedToastEnabled = notifyFailedToastEnabled,
                            notifyFailedText = notifyFailedText,
                            notifyFailedToastText = notifyFailedToastText,
                            announceEnabled = announceEnabled,
                            announceText = announceText
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
                    InputRow("模板名称", "例如：慢抢红包群、只抢口令红包", name) { name = it }
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用模板",
                        summary = "关闭后使用该模板的聊天不会自动抢",
                        onCheckedChange = { enabled = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "抢包") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "抢包模式",
                        summary = if (grabMode == 1) "静默抢包" else "打开红包页面",
                        options = optionItems("打开红包页面" to 0, "静默抢包" to 1),
                        currentValue = grabMode,
                        onValueChanged = { grabMode = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = skipSelf,
                        title = "跳过自己的红包",
                        summary = "自己发出的红包不会抢",
                        onCheckedChange = { skipSelf = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "延迟") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "抢包延迟",
                        summary = redPacketDelayModeLabel(delayMode),
                        options = redPacketDelayModeOptions(includeRandom = true),
                        currentValue = delayMode,
                        onValueChanged = { delayMode = it }
                    )
                    if (delayMode == RedPacketRuleConfig.DELAY_CUSTOM) {
                        InsetDivider()
                        NumberInputRow("自定义延迟", "单位 ms，0 表示不延迟", delayMs) { delayMs = it }
                    } else if (delayMode == RedPacketRuleConfig.DELAY_RANDOM) {
                        InsetDivider()
                        NumberInputRow("最小延迟", "单位 ms", randomMinMs) { randomMinMs = it }
                        InsetDivider()
                        NumberInputRow("最大延迟", "单位 ms，不能小于最小延迟", randomMaxMs) { randomMaxMs = it }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "过滤") }
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
                            if (it == 0) keywords = ""
                            keywordMode = it
                        }
                    )
                    if (keywordMode != 0) {
                        InsetDivider()
                        InputRow(
                            title = "关键词",
                            summary = "多个用 |、逗号或换行分隔",
                            value = keywords,
                            minLines = 2,
                            onValueChange = { keywords = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = quietEnabled,
                        title = "禁抢时段",
                        summary = "在指定时间段内不抢红包",
                        onCheckedChange = { quietEnabled = it }
                    )
                    if (quietEnabled) {
                        InsetDivider()
                        TimeOfDayPickerRow("开始时间", quietStart) { quietStart = it }
                        InsetDivider()
                        TimeOfDayPickerRow("结束时间", quietEnd) { quietEnd = it }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "抢后回复") }
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "通知提醒") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = notifySystemEnabled,
                        title = "通知栏提醒",
                        summary = "抢到红包时提醒",
                        onCheckedChange = { notifySystemEnabled = it }
                    )
                    if (notifySystemEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "通知栏文案",
                            summary = "支持下方变量",
                            value = notifyText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifyToastEnabled,
                        title = "浮窗提醒",
                        summary = "抢到红包时短暂提示",
                        onCheckedChange = { notifyToastEnabled = it }
                    )
                    if (notifyToastEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "浮窗文案",
                            summary = "支持下方变量",
                            value = notifyToastText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyToastText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifySoundEnabled,
                        title = "通知铃声",
                        summary = "开启后播放通知铃声",
                        onCheckedChange = { notifySoundEnabled = it }
                    )
                    if (notifySoundEnabled) {
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
                                    }
                                }
                                if (notifySoundMode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                                    RingtonePickerBridge.launchFile(activity, onPicked)
                                } else {
                                    RingtonePickerBridge.launchSystem(activity, notifySoundUri, onPicked)
                                }
                            }
                        }
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifyVibrateEnabled,
                        title = "通知震动",
                        summary = "开启后触发通知震动",
                        onCheckedChange = { notifyVibrateEnabled = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = announceEnabled,
                        title = "抢到红包播报",
                        summary = "抢到后用系统语音播报",
                        onCheckedChange = { announceEnabled = it }
                    )
                    if (announceEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "播报文案",
                            summary = "支持下方变量",
                            value = announceText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { announceText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifyFailedSystemEnabled,
                        title = "未抢到提醒",
                        summary = "未抢到红包时通知",
                        onCheckedChange = { notifyFailedSystemEnabled = it }
                    )
                    if (notifyFailedSystemEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "未抢到通知栏文案",
                            summary = "支持下方变量",
                            value = notifyFailedText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyFailedText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifyFailedToastEnabled,
                        title = "未抢到浮窗",
                        summary = "未抢到红包时短暂提示",
                        onCheckedChange = { notifyFailedToastEnabled = it }
                    )
                    if (notifyFailedToastEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "未抢到浮窗文案",
                            summary = "支持下方变量",
                            value = notifyFailedToastText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyFailedToastText = it }
                        )
                    }
                }
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除模板", "删除后使用该模板的聊天会变成未绑定模板") {
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
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val targetTitle = request.binding.label.ifBlank { request.binding.targetId }

    PageScaffold(
        title = targetTitle,
        largeTitle = targetTitle,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存聊天",
                onPrimaryClick = {
                    onSave(
                        request.binding.copy(
                            enabled = enabled,
                            templateId = templateId,
                            customRules = false,
                            overrideRule = null
                        )
                    )
                    Toast.makeText(context, "适用聊天已保存", Toast.LENGTH_SHORT).show()
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
            item { SmallTitle(text = "聊天") }
            item {
                SettingsCard {
                    InfoRow("名称", targetTitle)
                    InsetDivider()
                    InfoRow("ID", request.binding.targetId)
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用",
                        summary = "关闭后这个聊天不会按模板自动抢红包",
                        onCheckedChange = { enabled = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
            item {
                SettingsCard {
                    OptionChoiceRow(
                        item = OptionItem("跟随默认规则", -1, "启用后按默认规则或旧版全局设置抢红包"),
                        selected = templateId.isBlank(),
                        onClick = { templateId = "" }
                    )
                    if (templates.isNotEmpty()) InsetDivider()
                    templates.forEachIndexed { index, template ->
                        OptionChoiceRow(
                            item = OptionItem(
                                label = template.name.ifBlank { "模板 ${index + 1}" },
                                value = index,
                                summary = describeRedPacketTemplate(template)
                            ),
                            selected = templateId == template.id,
                            onClick = { templateId = template.id }
                        )
                        if (index < templates.lastIndex) InsetDivider()
                    }
                }
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("移除适用聊天", "移除后该聊天回到默认规则") {
                            onDelete()
                            Toast.makeText(context, "适用聊天已移除", Toast.LENGTH_SHORT).show()
                        }
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
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = save@{
                    val normalizedTimeFormat = noticeTimeFormat.trim()
                        .ifBlank { AntiRecallSettings.DEFAULT_NOTICE_TIME_FORMAT }
                    val timeFormatValid = runCatching {
                        SimpleDateFormat(normalizedTimeFormat, Locale.getDefault()).format(Date())
                    }.isSuccess
                    if (usesTimeVariable && !timeFormatValid) {
                        Toast.makeText(context, "时间格式无效", Toast.LENGTH_SHORT).show()
                        return@save
                    }
                    sp.edit()
                        .putString(
                            AntiRecallSettings.KEY_NOTICE_TEXT,
                            noticeText.ifBlank { AntiRecallSettings.DEFAULT_NOTICE_TEXT }
                        )
                        .putString(
                            AntiRecallSettings.KEY_NOTICE_TIME_FORMAT,
                            if (timeFormatValid) {
                                normalizedTimeFormat
                            } else {
                                AntiRecallSettings.DEFAULT_NOTICE_TIME_FORMAT
                            }
                        )
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
                bottom = padding.calculateBottomPadding() + 20.dp
            )
        ) {
            item { SmallTitle(text = "防撤回") }
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
                        onValueChange = { noticeText = it }
                    )
                    if (usesTimeVariable) {
                        InsetDivider()
                        InputRow(
                            "时间格式",
                            "使用日期格式，例如 yyyy-MM-dd HH:mm:ss",
                            noticeTimeFormat
                        ) { noticeTimeFormat = it }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CrashReportMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, CrashReportSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(CrashReportSettings.KEY_ENABLE, CrashReportSettings.DEFAULT_ENABLE)
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "调试") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "捕获异常日志",
                        summary = if (enabled) {
                            "记录 Java、Native 和 ANR 异常，并在下次启动时显示日志"
                        } else {
                            "关闭后不再捕获或弹出异常日志"
                        }
                    ) { checked ->
                        if (sp.edit().putBoolean(CrashReportSettings.KEY_ENABLE, checked).commit()) {
                            enabled = checked
                            CrashReportRuntime.onSettingChanged(context, checked)
                        } else {
                            Toast.makeText(context, "保存设置失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProtobufPacketMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, ProtobufPacketSettings.PREFS_NAME) }
    var blockTypes by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_BLOCK_TYPES,
            ProtobufPacketSettings.DEFAULT_BLOCK_TYPES
        ) ?: ProtobufPacketSettings.DEFAULT_BLOCK_TYPES)
    }
    var uri by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_URI,
            ProtobufPacketSettings.DEFAULT_SEND_URI
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_URI)
    }
    var cgiId by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_TYPE,
            ProtobufPacketSettings.DEFAULT_SEND_TYPE
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_TYPE)
    }
    var funcId by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_FUNC_ID,
            ProtobufPacketSettings.DEFAULT_SEND_FUNC_ID
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_FUNC_ID)
    }
    var routeId by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_ROUTE_ID,
            ProtobufPacketSettings.DEFAULT_SEND_ROUTE_ID
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_ROUTE_ID)
    }
    var payload by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_PAYLOAD,
            ProtobufPacketSettings.DEFAULT_SEND_PAYLOAD
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_PAYLOAD)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = {
                    sp.edit()
                        .putString(ProtobufPacketSettings.KEY_BLOCK_TYPES, blockTypes)
                        .putString(ProtobufPacketSettings.KEY_SEND_URI, uri.trim())
                        .putString(ProtobufPacketSettings.KEY_SEND_TYPE, cgiId.trim())
                        .putString(ProtobufPacketSettings.KEY_SEND_FUNC_ID, funcId.trim())
                        .putString(ProtobufPacketSettings.KEY_SEND_ROUTE_ID, routeId.trim())
                        .putString(ProtobufPacketSettings.KEY_SEND_PAYLOAD, payload)
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
            item { SmallTitle(text = "抓包") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        ProtobufPacketSettings.KEY_ENABLE,
                        "Protobuf 抓包",
                        "开启后输出请求和响应到 LSPosed 日志",
                        ProtobufPacketSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        ProtobufPacketSettings.KEY_CAPTURE_REQUEST,
                        "抓请求",
                        "记录请求 URI、Type 和 PB JSON",
                        ProtobufPacketSettings.DEFAULT_CAPTURE_REQUEST
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        ProtobufPacketSettings.KEY_CAPTURE_RESPONSE,
                        "抓响应",
                        "记录响应 URI、Type 和 PB JSON",
                        ProtobufPacketSettings.DEFAULT_CAPTURE_RESPONSE
                    )
                    InsetDivider()
                    InputRow(
                        "过滤 Type",
                        "多个 Type 用逗号或空格分隔",
                        blockTypes,
                        minLines = 3
                    ) { blockTypes = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "自定义发包") }
            item {
                SettingsCard {
                    InputRow("URI", "例如 /cgi-bin/micromsg-bin/oplog", uri) { uri = it }
                    InsetDivider()
                    InputRow("Type", "CGI ID，例如 681", cgiId) { cgiId = it.filter { ch -> ch.isDigit() } }
                    InsetDivider()
                    InputRow("FuncId", "默认 0", funcId) { funcId = it.filter { ch -> ch.isDigit() } }
                    InsetDivider()
                    InputRow("RouteId", "默认 0", routeId) { routeId = it.filter { ch -> ch.isDigit() } }
                    InsetDivider()
                    InputRow(
                        "JSON 载荷",
                        "字段号作为 key；字符串按 UTF-8，hex-> 表示原始字节",
                        payload,
                        minLines = 8
                    ) { payload = it }
                    InsetDivider()
                    ActionRow("发送", "优先通用发包，未就绪时使用同类请求编辑重放") {
                        val type = cgiId.toIntOrNull()
                        val func = funcId.toIntOrNull() ?: 0
                        val route = routeId.toIntOrNull() ?: 0
                        if (type == null) {
                            Toast.makeText(context, "Type 必须是数字", Toast.LENGTH_SHORT).show()
                            return@ActionRow
                        }
                        ProtobufPacketRuntime.send(uri.trim(), type, func, route, payload) { success, message ->
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
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
