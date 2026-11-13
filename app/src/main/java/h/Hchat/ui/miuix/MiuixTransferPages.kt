package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.customnotify.CustomNotificationRuntime
import h.Hchat.hooks.items.groupnicknamecolor.GroupNicknameColorSettings
import h.Hchat.hooks.items.groupnicknamecolor.GroupNicknameColorStore
import h.Hchat.hooks.items.membertitle.MemberTitleSettings
import h.Hchat.hooks.items.membertitle.MemberTitleStore
import h.Hchat.hooks.items.payment.core.PaymentTemplateTimeFormatter
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import h.Hchat.hooks.items.payment.fakebalance.FakeWalletBalanceSettings
import h.Hchat.hooks.items.payment.transfer.AutoTransferSettings
import h.Hchat.hooks.items.payment.transfer.TransferReceiveAccountStore
import h.Hchat.hooks.items.payment.transfer.TransferRuleBinding
import h.Hchat.hooks.items.payment.transfer.TransferRuleConfig
import h.Hchat.hooks.items.payment.transfer.TransferRuleTemplate
import h.Hchat.hooks.items.realtail.RealNameTailSettings
import h.Hchat.hooks.items.realtail.RealNameTailStore
import h.Hchat.hooks.items.tablet.WeChatTabletSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import java.util.LinkedHashSet
import java.util.Locale


@Composable
internal fun AutoTransferMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AutoTransferSettings.PREFS_NAME) }
    var templates by remember {
        mutableStateOf(TransferRuleConfig.parseTemplates(sp.getString(TransferRuleConfig.KEY_TEMPLATES, "")))
    }
    var bindings by remember {
        mutableStateOf(TransferRuleConfig.parseBindings(sp.getString(TransferRuleConfig.KEY_BINDINGS, "")))
    }
    var defaultTemplateId by remember {
        mutableStateOf(sp.getString(TransferRuleConfig.KEY_DEFAULT_TEMPLATE_ID, "") ?: "")
    }
    var whitelist by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_WHITELIST, "") ?: "") }
    var blacklist by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_BLACKLIST, "") ?: "") }
    var listMode by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_MODE, 0)) }
    var filterExpanded by remember { mutableStateOf(false) }
    var processingExpanded by remember { mutableStateOf(false) }
    var delayMs by remember { mutableStateOf(sp.getLong(AutoTransferSettings.KEY_DELAY_MS, 0L).toString()) }
    var delayMode by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_DELAY_MODE, TransferRuleConfig.DELAY_CUSTOM)) }
    var randomMinMs by remember { mutableStateOf(sp.getLong(AutoTransferSettings.KEY_DELAY_RANDOM_MIN, 500L).toString()) }
    var randomMaxMs by remember { mutableStateOf(sp.getLong(AutoTransferSettings.KEY_DELAY_RANDOM_MAX, 3000L).toString()) }
    var amountEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_AMOUNT_ENABLE, false)) }
    var amountCondition by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_AMOUNT_COND, 1)) }
    var amountAction by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_AMOUNT_ACTION, 0)) }
    var amountValue by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_AMOUNT_VALUE, "0") ?: "0") }
    var keywords by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_KEYWORDS, "") ?: "") }
    var keywordMode by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_KEYWORD_MODE, 0)) }
    var quietEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_QUIET_ENABLE, false)) }
    var quietStart by remember { mutableStateOf(formatRedPacketSecond(sp.getInt(AutoTransferSettings.KEY_QUIET_START_SECOND, 0))) }
    var quietEnd by remember { mutableStateOf(formatRedPacketSecond(sp.getInt(AutoTransferSettings.KEY_QUIET_END_SECOND, 0))) }
    var replySteps by remember { mutableStateOf(loadGlobalTransferReplySteps(sp)) }
    var groupReplySteps by remember { mutableStateOf(loadGlobalGroupTransferReplySteps(sp)) }
    var notifySystemEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_SYSTEM_ENABLE, false)) }
    var notifyToastEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_TOAST_ENABLE, false)) }
    var notifySoundEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_SOUND_ENABLE, false)) }
    var notifySoundMode by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_NOTIFY_SOUND_MODE, AutoTransferSettings.NOTIFY_SOUND_MODE_SYSTEM)) }
    var notifyVibrateEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_VIBRATE_ENABLE, false)) }
    var notifySoundUri by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_NOTIFY_SOUND_URI, "") ?: "") }
    var notifyText by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_NOTIFY_TEXT, "已收款 {amount} 元") ?: "") }
    var notifyToastText by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_NOTIFY_TOAST_TEXT, "已收款 {amount} 元") ?: "") }
    var announceEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_ANNOUNCE_ENABLE, false)) }
    var announceText by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_ANNOUNCE_TEXT, "收到转账 {amount} 元") ?: "") }
    var timeFormat by remember {
        mutableStateOf(
            PaymentTemplateTimeFormatter.normalizePattern(
                sp.getString(AutoTransferSettings.KEY_TIME_FORMAT, AutoTransferSettings.DEFAULT_TIME_FORMAT)
            )
        )
    }
    var receiveAccount by remember {
        mutableStateOf(
            sp.getString(
                AutoTransferSettings.KEY_RECEIVE_ACCOUNT,
                TransferReceiveAccountStore.DEFAULT_KEY
            ) ?: TransferReceiveAccountStore.DEFAULT_KEY
        )
    }
    val receiveAccountOptions = remember { transferReceiveAccountOptions(context) }
    if (receiveAccountOptions.none { it.value == receiveAccount }) {
        receiveAccount = TransferReceiveAccountStore.DEFAULT_KEY
    }
    var picker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var pickerIsOrdinary by remember { mutableStateOf(false) }
    var templateEditor by remember { mutableStateOf<TransferTemplateEditorRequest?>(null) }
    var bindingEditor by remember { mutableStateOf<TransferBindingEditorRequest?>(null) }
    var showTemplates by remember { mutableStateOf(false) }
    var showBindings by remember { mutableStateOf(false) }
    var showBatchApply by remember { mutableStateOf(false) }
    var showReplySteps by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf(TransferReplyTarget.PRIVATE) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun persistRules(
        nextTemplates: List<TransferRuleTemplate> = templates,
        nextBindings: List<TransferRuleBinding> = bindings,
        nextDefault: String = defaultTemplateId
    ) {
        sp.edit()
            .putString(TransferRuleConfig.KEY_TEMPLATES, TransferRuleConfig.encodeTemplates(nextTemplates))
            .putString(TransferRuleConfig.KEY_BINDINGS, TransferRuleConfig.encodeBindings(nextBindings))
            .putString(TransferRuleConfig.KEY_DEFAULT_TEMPLATE_ID, nextDefault)
            .commit()
    }

    fun persistDelay() {
        val editor = sp.edit()
        when (delayMode) {
            TransferRuleConfig.DELAY_CUSTOM -> {
                val delay = delayMs.toLongOrNull()?.takeIf { it in 0L..600000L } ?: return
                editor.putLong(AutoTransferSettings.KEY_DELAY_MS, delay)
            }
            TransferRuleConfig.DELAY_RANDOM -> {
                val min = randomMinMs.toLongOrNull()?.takeIf { it in 0L..600000L } ?: return
                val max = randomMaxMs.toLongOrNull()?.takeIf { it in min..600000L } ?: return
                editor.putLong(AutoTransferSettings.KEY_DELAY_RANDOM_MIN, min)
                    .putLong(AutoTransferSettings.KEY_DELAY_RANDOM_MAX, max)
            }
            else -> editor.putLong(AutoTransferSettings.KEY_DELAY_MS, 0L)
        }
        editor.putInt(AutoTransferSettings.KEY_DELAY_MODE, delayMode).apply()
    }

    fun persistQuietTimes() {
        val start = parseRedPacketSecond(quietStart, -1)
        val end = parseRedPacketSecond(quietEnd, -1)
        if (start >= 0 && end >= 0) sp.edit()
            .putInt(AutoTransferSettings.KEY_QUIET_START_SECOND, start)
            .putInt(AutoTransferSettings.KEY_QUIET_END_SECOND, end)
            .apply()
    }

    fun persistText(key: String, value: String, finished: Boolean = false) {
        if (value.isNotBlank() || finished) sp.edit().putString(key, value).apply()
    }

    val route: AutoTransferRoute = when {
        templateEditor != null -> AutoTransferRoute.TemplateEditor(templateEditor!!)
        bindingEditor != null -> AutoTransferRoute.BindingEditor(bindingEditor!!)
        picker != null -> AutoTransferRoute.ContactPicker(picker!!)
        showReplySteps -> AutoTransferRoute.GlobalReplySteps(replyTarget)
        showTemplates -> AutoTransferRoute.TemplateManager
        showBindings -> AutoTransferRoute.BindingManager
        showBatchApply -> AutoTransferRoute.BatchApply
        else -> AutoTransferRoute.Main
    }

    SettingsRouteTransition(
        targetState = route,
        label = "AutoTransferRouteTransition",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is AutoTransferRoute.ContactPicker -> {
                val request = currentRoute.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { picker = null },
                    onConfirm = { selected ->
                        request.onValue(formatIds(selected.map { it.id }))
                        picker = null
                    },
                    onSelectionChange = if (pickerIsOrdinary) { selected -> request.onValue(formatIds(selected)) } else null
                )
            }
            is AutoTransferRoute.TemplateEditor -> TransferTemplateEditorPage(
                context = context,
                request = currentRoute.request,
                onBack = { templateEditor = null },
                onSave = { updated ->
                    val request = currentRoute.request
                    val next = if (request.index in templates.indices) {
                        templates.toMutableList().also { it[request.index] = updated }
                    } else templates + updated
                    templates = next
                    persistRules(nextTemplates = next)
                    if (!request.canDelete) templateEditor = null
                },
                onDelete = {
                    val request = currentRoute.request
                    if (request.index in templates.indices) {
                        val deleted = templates[request.index].id
                        templates = templates.toMutableList().also { it.removeAt(request.index) }
                        bindings = bindings.map { if (it.templateId == deleted) it.copy(templateId = "") else it }
                        if (defaultTemplateId == deleted) defaultTemplateId = templates.firstOrNull()?.id.orEmpty()
                        persistRules()
                    }
                    templateEditor = null
                }
            )
            is AutoTransferRoute.BindingEditor -> TransferBindingEditorPage(
                context = context,
                request = currentRoute.request,
                templates = templates,
                onBack = { bindingEditor = null },
                onSave = { updated ->
                    val index = bindings.indexOfFirst { it.targetId == updated.targetId }
                    bindings = if (index >= 0) bindings.toMutableList().also { it[index] = updated } else bindings + updated
                    persistRules(nextBindings = bindings)
                    if (!currentRoute.request.canDelete) bindingEditor = null
                },
                onDelete = {
                    val index = bindings.indexOfFirst { it.targetId == currentRoute.request.binding.targetId }
                    if (index >= 0) {
                        bindings = bindings.toMutableList().also { it.removeAt(index) }
                        persistRules(nextBindings = bindings)
                    }
                    bindingEditor = null
                }
            )
            AutoTransferRoute.TemplateManager -> TransferTemplateListPage(
                templates = templates,
                onBack = { showTemplates = false },
                onOpen = { index, value -> templateEditor = TransferTemplateEditorRequest(index, value, true) },
                onAdd = { templateEditor = TransferTemplateEditorRequest(templates.size, newTransferTemplate(templates.size + 1, sp), false) }
            )
            AutoTransferRoute.BindingManager -> TransferBindingListPage(
                bindings = bindings,
                templates = templates,
                onBack = { showBindings = false },
                onOpen = { index, value -> bindingEditor = TransferBindingEditorRequest(index, value, true) },
                onDeleteBindings = { targets ->
                    val targetIds = targets.mapTo(HashSet()) { it.id }
                    bindings = bindings.filterNot { it.id in targetIds }
                    persistRules(nextBindings = bindings)
                    Toast.makeText(context, "已删除 ${targets.size} 个适用聊天", Toast.LENGTH_SHORT).show()
                },
                onAdd = {
                    pickerIsOrdinary = false
                    picker = ContactPickerRequest(
                        title = "选择适用聊天",
                        mode = ContactPickerMode.BOTH,
                        multiSelect = true,
                        existingValue = "",
                        enableLabels = true,
                        onValue = { value ->
                            val additions = parseIds(value).map { id ->
                                bindings.firstOrNull { it.targetId == id }
                                    ?: TransferRuleBinding(id, id, transferContactLabel(id), false, if (templates.size == 1) templates.first().id else "")
                            }
                            if (additions.size == 1) {
                                val item = additions.first()
                                val index = bindings.indexOfFirst { it.targetId == item.targetId }
                                bindingEditor = TransferBindingEditorRequest(index.takeIf { it >= 0 } ?: bindings.size, item, index >= 0)
                            } else if (additions.isNotEmpty()) {
                                bindings = upsertTransferBindings(bindings, additions)
                                persistRules(nextBindings = bindings)
                            }
                        }
                    )
                }
            )
            AutoTransferRoute.BatchApply -> TransferBatchApplyPage(
                templates = templates,
                bindings = bindings,
                onBack = { showBatchApply = false },
                onPickChats = { templateId ->
                    pickerIsOrdinary = false
                    picker = ContactPickerRequest(
                        title = "批量套用收款模板",
                        mode = ContactPickerMode.BOTH,
                        multiSelect = true,
                        existingValue = formatIds(bindings.filter { it.templateId == templateId }.map { it.targetId }),
                        enableLabels = true,
                        onValue = { value ->
                            val selected = parseIds(value)
                            val retained = bindings.filterNot { it.templateId == templateId && it.targetId !in selected }
                            val additions = selected.map { id ->
                                TransferRuleBinding(id, id, transferContactLabel(id), true, templateId)
                            }
                            bindings = upsertTransferBindings(retained, additions)
                            persistRules(nextBindings = bindings)
                            Toast.makeText(context, "模板已套用到 ${selected.size} 个聊天", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
            is AutoTransferRoute.GlobalReplySteps -> RedPacketReplyStepsPage(
                context = context,
                title = if (currentRoute.target == TransferReplyTarget.GROUP) "群聊收款回复" else "私聊收款回复",
                initialSteps = if (currentRoute.target == TransferReplyTarget.GROUP) groupReplySteps else replySteps,
                templateVariables = transferTemplateVariables,
                onBack = { showReplySteps = false },
                onSave = {
                    if (currentRoute.target == TransferReplyTarget.GROUP) {
                        groupReplySteps = it
                        sp.edit().putString(AutoTransferSettings.KEY_REPLY_GROUP_ITEMS, RedPacketRuleConfig.encodeReplySteps(it)).apply()
                    } else {
                        replySteps = it
                        if (!sp.contains(AutoTransferSettings.KEY_REPLY_GROUP_ITEMS)) groupReplySteps = it
                        sp.edit()
                            .putString(AutoTransferSettings.KEY_REPLY_ITEMS, RedPacketRuleConfig.encodeReplySteps(it))
                            .putBoolean(AutoTransferSettings.KEY_REPLY_ENABLE, it.isNotEmpty())
                            .putString(AutoTransferSettings.KEY_REPLY_TEXT, it.firstOrNull()?.content.orEmpty())
                            .apply()
                    }
                }
            )
            AutoTransferRoute.Main -> PageScaffold(
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
                    item { SettingsSectionTitle(text = "规则") }
                    item {
                        SettingsCard {
                            SwitchRow(sp, AutoTransferSettings.KEY_ENABLE, "自动收款", "自动领取待收款转账", false)
                            InsetDivider()
                            ActionRow("收款规则模板", if (templates.isEmpty()) "暂无模板" else "${templates.size} 个模板") { showTemplates = true }
                            InsetDivider()
                            PopupChoiceRow(
                                title = "默认规则",
                                summary = describeTransferDefault(defaultTemplateId, templates),
                                options = listOf(PopupChoice("旧版全局设置", "")) + templates.map { PopupChoice(it.name, it.id) },
                                currentValue = defaultTemplateId,
                                onValueChanged = {
                                    defaultTemplateId = it
                                    persistRules(nextDefault = it)
                                }
                            )
                            InsetDivider()
                            ActionRow("适用聊天", if (bindings.isEmpty()) "暂无单独配置" else "${bindings.size} 个聊天") { showBindings = true }
                            InsetDivider()
                            ActionRow("批量套用模板", "一次给多个聊天分配同一规则") { showBatchApply = true }
                        }
                    }
                    item { SettingsSectionTitle(text = "全局收款") }
                    item {
                        SettingsCard {
                            PopupChoiceRow("收款位置", "使用微信当前账号提供的收款账户", receiveAccountOptions, receiveAccount, onValueChanged = { receiveAccount = it; sp.edit().putString(AutoTransferSettings.KEY_RECEIVE_ACCOUNT, it).apply() })
                            InsetDivider()
                            SwitchRow(sp, AutoTransferSettings.KEY_REFUND_REJECTED, "拒收时退回", "规则不通过时原路退回", false)

                        }
                    }
                    item {
                        SequenceItemCard(
                            title = "过滤与延迟",
                            summary = "${transferListModeLabel(listMode)} · ${transferDelayModeLabel(delayMode)}",
                            expanded = filterExpanded,
                            modifier = Modifier.padding(top = 24.dp),
                            onToggle = {
                                val invalidDelay = when (delayMode) {
                                    TransferRuleConfig.DELAY_CUSTOM -> delayMs.toLongOrNull()?.let { it in 0L..600000L } != true
                                    TransferRuleConfig.DELAY_RANDOM -> randomMinMs.toLongOrNull()?.let { min -> min in 0L..600000L && randomMaxMs.toLongOrNull()?.let { it in min..600000L } == true } != true
                                    else -> false
                                }
                                if (filterExpanded && (invalidDelay || (amountValue.isNotBlank() && !isCompleteTransferAmount(amountValue))))
                                    Toast.makeText(context, "请先修正金额或延迟", Toast.LENGTH_SHORT).show()
                                else filterExpanded = !filterExpanded
                            }
                        ) {
                            PopupOptionRow("收款延迟", transferDelayModeLabel(delayMode), redPacketDelayModeOptions(true), delayMode, onValueChanged = { delayMode = it; persistDelay() })
                            if (delayMode == TransferRuleConfig.DELAY_CUSTOM) {
                                InsetDivider(); NumberInputRow("自定义延迟", if (delayMs.toLongOrNull()?.let { it in 0L..600000L } != true) "请输入 0–600000，保留上次有效配置" else "单位 ms", delayMs) { delayMs = it; persistDelay() }
                            } else if (delayMode == TransferRuleConfig.DELAY_RANDOM) {
                                InsetDivider(); NumberInputRow("最小延迟", "单位 ms", randomMinMs) { randomMinMs = it; persistDelay() }
                                InsetDivider(); NumberInputRow("最大延迟", "单位 ms", randomMaxMs) { randomMaxMs = it; persistDelay() }
                                if (randomMinMs.toLongOrNull()?.let { min -> min in 0L..600000L && randomMaxMs.toLongOrNull()?.let { it in min..600000L } == true } != true) {
                                    InsetDivider(); InfoRow("延迟范围", "请输入有效范围，保留上次有效配置")
                                }
                            }
                            InsetDivider()
                            PopupOptionRow("收款范围", transferListModeLabel(listMode), optionItems("全部接收" to 0, "只接收白名单" to 1, "拒收黑名单" to 2), listMode, onValueChanged = { listMode = it; sp.edit().putInt(AutoTransferSettings.KEY_MODE, it).apply() })
                            if (listMode == 1 || listMode == 2) {
                                InsetDivider()
                                ActionRow(if (listMode == 1) "白名单" else "黑名单", autoReplySelectedIdSummary(if (listMode == 1) whitelist else blacklist)) {
                                    val isWhitelist = listMode == 1
                                    pickerIsOrdinary = true
                                    picker = ContactPickerRequest(if (listMode == 1) "选择白名单" else "选择黑名单", ContactPickerMode.BOTH, true, if (listMode == 1) whitelist else blacklist, {
                                        if (isWhitelist) whitelist = it else blacklist = it
                                        sp.edit().putString(if (isWhitelist) AutoTransferSettings.KEY_WHITELIST else AutoTransferSettings.KEY_BLACKLIST, it).apply()
                                    }, true)
                                }
                            }
                            InsetDivider()
                            SwitchRow(amountEnabled, "金额规则", "按转账金额决定接收或拒收") { amountEnabled = it; sp.edit().putBoolean(AutoTransferSettings.KEY_AMOUNT_ENABLE, it).apply() }
                            InsetDivider(); PopupOptionRow("金额条件", transferAmountConditionLabel(amountCondition), optionItems("大于" to 0, "小于" to 1, "等于" to 2), amountCondition, onValueChanged = { amountCondition = it; sp.edit().putInt(AutoTransferSettings.KEY_AMOUNT_COND, it).apply() })
                            InsetDivider(); InputRow("金额数值", if (amountValue.isNotBlank() && !isCompleteTransferAmount(amountValue)) "请输入有效金额，保留上次有效配置" else "单位元，例如 10.5", amountValue, onEditingFinished = { if (filterExpanded && it.isBlank()) sp.edit().putString(AutoTransferSettings.KEY_AMOUNT_VALUE, "").apply() }) { amountValue = it; if (isCompleteTransferAmount(it)) sp.edit().putString(AutoTransferSettings.KEY_AMOUNT_VALUE, it).apply() }
                            InsetDivider(); PopupOptionRow("命中后动作", transferAmountActionLabel(amountAction), optionItems("拒收/忽略" to 0, "仅接收满足条件" to 1), amountAction, onValueChanged = { amountAction = it; sp.edit().putInt(AutoTransferSettings.KEY_AMOUNT_ACTION, it).apply() })
                            InsetDivider()
                            PopupOptionRow("关键词规则", transferKeywordModeLabel(keywordMode), optionItems("不启用" to 0, "必须包含关键词" to 1, "包含则拒收" to 2), keywordMode, onValueChanged = { keywordMode = it; sp.edit().putInt(AutoTransferSettings.KEY_KEYWORD_MODE, it).apply() })
                            if (keywordMode != 0) {
                                InsetDivider(); InputRow("关键词", "多个关键词用 |、逗号或换行分隔", keywords, minLines = 2, onEditingFinished = { if (filterExpanded) persistText(AutoTransferSettings.KEY_KEYWORDS, it, true) }) { keywords = it; persistText(AutoTransferSettings.KEY_KEYWORDS, it) }
                            }
                            InsetDivider()
                            SwitchRow(quietEnabled, "禁收时段", "指定时段内不自动收款") { quietEnabled = it; sp.edit().putBoolean(AutoTransferSettings.KEY_QUIET_ENABLE, it).apply() }
                            InsetDivider(); TimeOfDayPickerRow("开始时间", quietStart) { quietStart = it; persistQuietTimes() }
                            InsetDivider(); TimeOfDayPickerRow("结束时间", quietEnd) { quietEnd = it; persistQuietTimes() }
                        }
                    }
                    item {
                        SettingsDisclosure(
                            title = "收款后处理",
                            summary = "${replySteps.size} 条私聊回复 · ${groupReplySteps.size} 条群聊回复 · ${if (notifySystemEnabled || notifyToastEnabled || notifySoundEnabled || notifyVibrateEnabled || announceEnabled) "提醒已开" else "提醒关闭"}",
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
                                onEditingFinished = { if (processingExpanded && it.isBlank()) sp.edit().putString(AutoTransferSettings.KEY_TIME_FORMAT, PaymentTemplateTimeFormatter.normalizePattern(it)).apply() }
                            ) { timeFormat = it; if (it.isNotBlank() && PaymentTemplateTimeFormatter.isValidPattern(it)) sp.edit().putString(AutoTransferSettings.KEY_TIME_FORMAT, PaymentTemplateTimeFormatter.normalizePattern(it)).apply() }
                        }
                    }
                    item { SettingsSectionTitle(text = "收款后回复") }
                    item {
                        SettingsCard {
                            SelectRow("私聊收款回复", describeRedPacketReplySteps(replySteps)) {
                                replyTarget = TransferReplyTarget.PRIVATE
                                showReplySteps = true
                            }
                            InsetDivider()
                            SelectRow("群聊收款回复", describeRedPacketReplySteps(groupReplySteps)) {
                                replyTarget = TransferReplyTarget.GROUP
                                showReplySteps = true
                            }
                        }
                    }
                    item { SettingsSectionTitle(text = "成功提醒") }
                    item {
                        TransferNotificationSettingsCard(
                            context, notifySystemEnabled, { notifySystemEnabled = it; sp.edit().putBoolean(AutoTransferSettings.KEY_NOTIFY_SYSTEM_ENABLE, it).apply() },
                            notifyToastEnabled, { notifyToastEnabled = it; sp.edit().putBoolean(AutoTransferSettings.KEY_NOTIFY_TOAST_ENABLE, it).apply() },
                            notifySoundEnabled, { notifySoundEnabled = it; sp.edit().putBoolean(AutoTransferSettings.KEY_NOTIFY_SOUND_ENABLE, it).apply() },
                            notifySoundMode, { notifySoundMode = it; notifySoundUri = ""; sp.edit().putInt(AutoTransferSettings.KEY_NOTIFY_SOUND_MODE, it).putString(AutoTransferSettings.KEY_NOTIFY_SOUND_URI, "").apply() },
                            notifyVibrateEnabled, { notifyVibrateEnabled = it; sp.edit().putBoolean(AutoTransferSettings.KEY_NOTIFY_VIBRATE_ENABLE, it).apply() },
                            notifySoundUri, { notifySoundUri = it; sp.edit().putString(AutoTransferSettings.KEY_NOTIFY_SOUND_URI, it).apply() },
                            notifyText, { notifyText = it; persistText(AutoTransferSettings.KEY_NOTIFY_TEXT, it) },
                            notifyToastText, { notifyToastText = it; persistText(AutoTransferSettings.KEY_NOTIFY_TOAST_TEXT, it) },
                            announceEnabled, { announceEnabled = it; sp.edit().putBoolean(AutoTransferSettings.KEY_ANNOUNCE_ENABLE, it).apply() },
                            announceText, { announceText = it; persistText(AutoTransferSettings.KEY_ANNOUNCE_TEXT, it) },
                            onNoticeTextFinished = { if (processingExpanded) persistText(AutoTransferSettings.KEY_NOTIFY_TEXT, it, true) },
                            onToastTextFinished = { if (processingExpanded) persistText(AutoTransferSettings.KEY_NOTIFY_TOAST_TEXT, it, true) },
                            onAnnounceTextFinished = { if (processingExpanded) persistText(AutoTransferSettings.KEY_ANNOUNCE_TEXT, it, true) }
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferTemplateListPage(
    templates: List<TransferRuleTemplate>,
    onBack: () -> Unit,
    onOpen: (Int, TransferRuleTemplate) -> Unit,
    onAdd: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "收款规则模板",
        largeTitle = "收款规则模板",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。新增后可设为默认规则或分配给指定聊天。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SelectRow(
                                template.name.ifBlank { "模板 ${index + 1}" },
                                describeTransferTemplate(template)
                            ) { onOpen(index, template) }
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }

                    InsetDivider()
                    AddActionRow("新增模板", onClick = onAdd)
                }
            }
        }
    }
}

@Composable
private fun TransferBindingListPage(
    bindings: List<TransferRuleBinding>,
    templates: List<TransferRuleTemplate>,
    onBack: () -> Unit,
    onOpen: (Int, TransferRuleBinding) -> Unit,
    onAdd: () -> Unit,
    onDeleteBindings: (List<TransferRuleBinding>) -> Unit
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf(ConversationRuleCategory.ALL) }
    var query by remember { mutableStateOf("") }
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val lower = query.trim().lowercase(Locale.US)
    val visible = bindings.mapIndexed { index, value -> index to value }.filter { (_, value) ->
        conversationRuleCategoryMatches(value, category) && (
            lower.isBlank() || value.label.lowercase(Locale.US).contains(lower) ||
                value.targetId.lowercase(Locale.US).contains(lower) ||
                templates.firstOrNull { it.id == value.templateId }?.name?.lowercase(Locale.US)?.contains(lower) == true
            )
    }
    val visibleIds = visible.mapTo(LinkedHashSet()) { it.second.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val selectedBindings = bindings.filter { it.id in selectedIds }
    val scrollBehavior = MiuixScrollBehavior()
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
            item { SettingsCard { InputRow("搜索聊天", "昵称 / ID / 模板名", query) { query = it } } }
            item { SettingsSectionTitle(text = "聊天 · ${visible.size}/${bindings.size} 项") }


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
                        AddActionRow("添加聊天", onClick = onAdd)
                    }

                    InsetDivider()
                    if (visible.isEmpty()) {
                        EmptyText(if (bindings.isEmpty()) "暂无适用聊天。" else "没有匹配结果。")
                    } else {
                        visible.forEachIndexed { row, (index, binding) ->
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = binding.label.ifBlank { binding.targetId },
                                        value = index,
                                        summary = describeTransferBinding(binding, templates)
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
                                    binding.label.ifBlank { binding.targetId },
                                    describeTransferBinding(binding, templates)
                                ) { onOpen(index, binding) }
                            }
                            if (row < visible.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedBindings.size} 个适用聊天，此操作不可撤销。",
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
private fun TransferBindingEditorPage(
    context: Context,
    request: TransferBindingEditorRequest,
    templates: List<TransferRuleTemplate>,
    onBack: () -> Unit,
    onSave: (TransferRuleBinding) -> Unit,
    onDelete: () -> Unit
) {
    var enabled by remember(request) { mutableStateOf(request.binding.enabled) }
    var templateId by remember(request) { mutableStateOf(request.binding.templateId) }
    val scrollBehavior = MiuixScrollBehavior()
    fun persist(create: Boolean = false) {
        if (request.canDelete || create) onSave(request.binding.copy(enabled = enabled, templateId = templateId))
    }
    PageScaffold(
        title = request.binding.label.ifBlank { request.binding.targetId },
        largeTitle = request.binding.label.ifBlank { request.binding.targetId },
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "聊天") }
            item {
                SettingsCard {
                    InfoRow("ID", request.binding.targetId)
                    InsetDivider()
                    SwitchRow(enabled, "自动收款", "关闭后该聊天不会自动收款") { enabled = it; persist() }
                }
            }
            item { SettingsSectionTitle(text = "模板") }
            item {
                SettingsCard {
                    OptionChoiceRow(
                        OptionItem("跟随默认规则", -1, "使用默认模板或全局设置"),
                        templateId.isBlank()
                    ) { templateId = ""; persist() }
                    templates.forEachIndexed { index, template ->
                        InsetDivider()
                        OptionChoiceRow(
                            OptionItem(template.name.ifBlank { "模板 ${index + 1}" }, index, describeTransferTemplate(template)),
                            templateId == template.id
                        ) { templateId = template.id; persist() }
                    }
                }
            }

            item { SettingsSectionTitle(text = "操作") }
            item {
                SettingsCard {
                    if (!request.canDelete) ContentActionRow("创建") { persist(create = true) }
                    else ContentActionRow("移除", onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun TransferBatchApplyPage(
    templates: List<TransferRuleTemplate>,
    bindings: List<TransferRuleBinding>,
    onBack: () -> Unit,
    onPickChats: (String) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "批量套用模板",
        largeTitle = "批量套用模板",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "选择模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("请先新增收款规则模板。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            val count = bindings.count { it.templateId == template.id }
                            SelectRow(template.name, "$count 个聊天 · ${describeTransferTemplate(template)}") {
                                onPickChats(template.id)
                            }
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

private sealed class TransferTemplateRoute {
    object Main : TransferTemplateRoute()
    data class Reply(val target: TransferReplyTarget) : TransferTemplateRoute()
    data class Picker(val request: ContactPickerRequest) : TransferTemplateRoute()
}

@Composable
private fun TransferTemplateEditorPage(
    context: Context,
    request: TransferTemplateEditorRequest,
    onBack: () -> Unit,
    onSave: (TransferRuleTemplate) -> Unit,
    onDelete: () -> Unit
) {
    var value by remember(request) { mutableStateOf(request.template) }
    var storedValue by remember(request) { mutableStateOf(request.template) }
    var showReplySteps by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf(TransferReplyTarget.PRIVATE) }
    var groupReplySteps by remember(request) {
        mutableStateOf(request.template.groupReplySteps ?: request.template.replySteps)
    }
    var groupReplyEdited by remember(request) { mutableStateOf(false) }
    var delayText by remember(request) { mutableStateOf(request.template.delayMs.toString()) }
    var minText by remember(request) { mutableStateOf(request.template.randomMinMs.toString()) }
    var maxText by remember(request) { mutableStateOf(request.template.randomMaxMs.toString()) }
    var amountText by remember(request) { mutableStateOf(request.template.amountValue) }
    var picker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    val accountOptions = remember { transferReceiveAccountOptions(context) }
    fun update(change: (TransferRuleTemplate) -> TransferRuleTemplate) {
        value = change(value)
        if (request.canDelete) {
            storedValue = change(storedValue)
            onSave(storedValue)
        }
    }
    fun updateDelay() {
        val delay = delayText.toLongOrNull()?.takeIf { it in 0L..600000L }
        val min = minText.toLongOrNull()?.takeIf { it in 0L..600000L }
        val max = maxText.toLongOrNull()?.takeIf { min != null && it in min..600000L }
        when (value.delayMode) {
            TransferRuleConfig.DELAY_CUSTOM -> if (delay != null) update { it.copy(delayMode = value.delayMode, delayMs = delay) }
            TransferRuleConfig.DELAY_RANDOM -> if (min != null && max != null) update { it.copy(delayMode = value.delayMode, randomMinMs = min, randomMaxMs = max) }
            else -> update { it.copy(delayMode = value.delayMode, delayMs = 0L) }
        }
    }
    val scrollBehavior = MiuixScrollBehavior()
    val innerRoute = when {
        showReplySteps -> TransferTemplateRoute.Reply(replyTarget)
        picker != null -> TransferTemplateRoute.Picker(picker!!)
        else -> TransferTemplateRoute.Main
    }
    SettingsRouteTransition(
        targetState = innerRoute,
        label = "TransferTemplateRoute",
        depthOf = { if (it is TransferTemplateRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            is TransferTemplateRoute.Reply -> RedPacketReplyStepsPage(
                context = context,
                title = if (current.target == TransferReplyTarget.GROUP) "模板群聊收款回复" else "模板私聊收款回复",
                initialSteps = if (current.target == TransferReplyTarget.GROUP) groupReplySteps else value.replySteps,
                draftOnly = !request.canDelete,
                templateVariables = transferTemplateVariables,
                onBack = { showReplySteps = false },
                onSave = { steps ->
                    if (current.target == TransferReplyTarget.GROUP) {
                        groupReplyEdited = true
                        groupReplySteps = steps
                        update { it.copy(groupReplySteps = steps) }
                    } else {
                        if (value.groupReplySteps == null && !groupReplyEdited) groupReplySteps = steps
                        update { it.copy(replySteps = steps) }
                    }
                }
            )
            is TransferTemplateRoute.Picker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { picker = null },
                onSelectionChange = { current.request.onValue(formatIds(it)) }
            )
            TransferTemplateRoute.Main ->
    PageScaffold(
        title = value.name.ifBlank { "收款模板" },
        largeTitle = value.name.ifBlank { "收款模板" },
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "模板") }
            item {
                SettingsCard {
                    InputRow("模板名称", if (value.name.isBlank() && request.canDelete) "请输入名称，保留上次有效配置" else "用于默认规则和聊天绑定", value.name) { text ->
                        if (text.isBlank()) value = value.copy(name = text) else update { it.copy(name = text) }
                    }
                    InsetDivider()
                    SwitchRow(value.enabled, "模板", "关闭后使用该模板的聊天不会自动收款") { checked -> update { it.copy(enabled = checked) } }
                }
            }
            item { SettingsSectionTitle(text = "收款") }
            item {
                SettingsCard {
                    PopupChoiceRow("收款位置", "选择转账到账账户", accountOptions, value.receiveAccount, onValueChanged = { selected -> update { it.copy(receiveAccount = selected) } })
                    InsetDivider()
                    SwitchRow(value.refundRejected, "拒收时退回", "规则不通过时原路退回") { checked -> update { it.copy(refundRejected = checked) } }
                    InsetDivider()
                    PopupOptionRow("收款延迟", transferDelayModeLabel(value.delayMode), redPacketDelayModeOptions(true), value.delayMode, onValueChanged = { value = value.copy(delayMode = it); updateDelay() })
                    if (value.delayMode == TransferRuleConfig.DELAY_CUSTOM) {
                        InsetDivider(); NumberInputRow("自定义延迟", if (delayText.toLongOrNull()?.let { it in 0L..600000L } != true) "请输入 0–600000，保留上次有效配置" else "单位 ms", delayText) { delayText = it; updateDelay() }
                    } else if (value.delayMode == TransferRuleConfig.DELAY_RANDOM) {
                        InsetDivider(); NumberInputRow("最小延迟", "单位 ms", minText) { minText = it; updateDelay() }
                        InsetDivider(); NumberInputRow("最大延迟", "单位 ms", maxText) { maxText = it; updateDelay() }
                        if (minText.toLongOrNull()?.let { min -> min in 0L..600000L && maxText.toLongOrNull()?.let { it in min..600000L } == true } != true) {
                            InsetDivider(); InfoRow("延迟范围", "请输入有效范围，保留上次有效配置")
                        }
                    }
                }
            }
            item { SettingsSectionTitle(text = "过滤") }
            item {
                SettingsCard {
                    PopupOptionRow("收款范围", transferListModeLabel(value.listMode), optionItems("全部接收" to 0, "只接收白名单" to 1, "拒收黑名单" to 2), value.listMode, onValueChanged = { selected -> update { it.copy(listMode = selected) } })
                    if (value.listMode == 1 || value.listMode == 2) {
                        InsetDivider()
                        val listValue = if (value.listMode == 1) value.whitelist else value.blacklist
                        ActionRow(if (value.listMode == 1) "白名单" else "黑名单", autoReplySelectedIdSummary(listValue)) {
                            val white = value.listMode == 1
                            picker = ContactPickerRequest(
                                if (value.listMode == 1) "选择白名单" else "选择黑名单",
                                ContactPickerMode.BOTH,
                                true,
                                listValue,
                                { selected -> update { if (white) it.copy(whitelist = selected) else it.copy(blacklist = selected) } },
                                true
                            )
                        }
                    }
                    InsetDivider()
                    SwitchRow(value.amountEnabled, "金额规则", "按转账金额决定接收或拒收") { checked -> update { it.copy(amountEnabled = checked) } }
                    InsetDivider(); PopupOptionRow("金额条件", transferAmountConditionLabel(value.amountCondition), optionItems("大于" to 0, "小于" to 1, "等于" to 2), value.amountCondition, onValueChanged = { selected -> update { it.copy(amountCondition = selected) } })
                    InsetDivider(); InputRow("金额数值", if (amountText.isNotBlank() && !isCompleteTransferAmount(amountText)) "请输入有效金额，保留上次有效配置" else "单位元，例如 10.5", amountText, onEditingFinished = { text -> if (text.isBlank()) update { it.copy(amountValue = "") } }) { text -> amountText = text; if (isCompleteTransferAmount(text)) update { it.copy(amountValue = text) } }
                    InsetDivider(); PopupOptionRow("命中后动作", transferAmountActionLabel(value.amountAction), optionItems("拒收/忽略" to 0, "仅接收满足条件" to 1), value.amountAction, onValueChanged = { selected -> update { it.copy(amountAction = selected) } })
                    InsetDivider()
                    PopupOptionRow("关键词规则", transferKeywordModeLabel(value.keywordMode), optionItems("不启用" to 0, "必须包含关键词" to 1, "包含则拒收" to 2), value.keywordMode, onValueChanged = { selected -> update { it.copy(keywordMode = selected) } })
                    if (value.keywordMode != 0) {
                        InsetDivider(); InputRow("关键词", "多个关键词用 |、逗号或换行分隔", value.keywords, minLines = 2, onEditingFinished = { text -> if (text.isBlank()) update { it.copy(keywords = text) } }) { text -> if (text.isBlank()) value = value.copy(keywords = text) else update { it.copy(keywords = text) } }
                    }
                    InsetDivider()
                    SwitchRow(value.quietEnabled, "禁收时段", "指定时段内不自动收款") { checked -> update { it.copy(quietEnabled = checked) } }
                    InsetDivider(); TimeOfDayPickerRow("开始时间", formatRedPacketSecond(value.quietStartSecond)) { text -> update { it.copy(quietStartSecond = parseRedPacketSecond(text, it.quietStartSecond)) } }
                    InsetDivider(); TimeOfDayPickerRow("结束时间", formatRedPacketSecond(value.quietEndSecond)) { text -> update { it.copy(quietEndSecond = parseRedPacketSecond(text, it.quietEndSecond)) } }
                }
            }
            item { SettingsSectionTitle(text = "收款后回复") }
            item {
                SettingsCard {
                    SelectRow("私聊收款回复", describeRedPacketReplySteps(value.replySteps)) {
                        replyTarget = TransferReplyTarget.PRIVATE
                        showReplySteps = true
                    }
                    InsetDivider()
                    SelectRow("群聊收款回复", describeRedPacketReplySteps(groupReplySteps)) {
                        replyTarget = TransferReplyTarget.GROUP
                        showReplySteps = true
                    }
                }
            }
            item { SettingsSectionTitle(text = "成功提醒") }
            item {
                TransferNotificationSettingsCard(
                    context,
                    value.notifySystemEnabled, { checked -> update { it.copy(notifySystemEnabled = checked) } },
                    value.notifyToastEnabled, { checked -> update { it.copy(notifyToastEnabled = checked) } },
                    value.notifySoundEnabled, { checked -> update { it.copy(notifySoundEnabled = checked) } },
                    value.notifySoundMode, { selected -> update { it.copy(notifySoundMode = selected, notifySoundUri = "") } },
                    value.notifyVibrateEnabled, { checked -> update { it.copy(notifyVibrateEnabled = checked) } },
                    value.notifySoundUri, { uri -> update { it.copy(notifySoundUri = uri) } },
                    value.notifyText, { text -> if (text.isBlank()) value = value.copy(notifyText = text) else update { it.copy(notifyText = text) } },
                    value.notifyToastText, { text -> if (text.isBlank()) value = value.copy(notifyToastText = text) else update { it.copy(notifyToastText = text) } },
                    value.announceEnabled, { checked -> update { it.copy(announceEnabled = checked) } },
                    value.announceText, { text -> if (text.isBlank()) value = value.copy(announceText = text) else update { it.copy(announceText = text) } },
                    onNoticeTextFinished = { text -> if (text.isBlank()) update { it.copy(notifyText = text) } },
                    onToastTextFinished = { text -> if (text.isBlank()) update { it.copy(notifyToastText = text) } },
                    onAnnounceTextFinished = { text -> if (text.isBlank()) update { it.copy(announceText = text) } }
                )
            }

            item { SettingsSectionTitle(text = "操作") }
            item {
                SettingsCard {
                    if (!request.canDelete) ContentActionRow("创建") {
                        val validDelay = when (value.delayMode) {
                            TransferRuleConfig.DELAY_CUSTOM -> delayText.toLongOrNull()?.let { it in 0L..600000L } == true
                            TransferRuleConfig.DELAY_RANDOM -> minText.toLongOrNull()?.let { min -> min in 0L..600000L && maxText.toLongOrNull()?.let { it in min..600000L } == true } == true
                            else -> true
                        }
                        if ((value.replySteps + groupReplySteps).any { it.content.isBlank() }) Toast.makeText(context, "请完善或移除未完成的回复", Toast.LENGTH_SHORT).show()
                        else if (!validDelay || (amountText.isNotBlank() && !isCompleteTransferAmount(amountText))) Toast.makeText(context, "请输入有效金额和延迟", Toast.LENGTH_SHORT).show()
                        else onSave(value.copy(name = value.name.ifBlank { "收款模板" }, amountValue = amountText))
                    } else ContentActionRow("删除", onClick = onDelete)
                }
            }
        }
    }
        }
    }
}

@Composable
private fun TransferNotificationSettingsCard(
    context: Context,
    system: Boolean,
    onSystem: (Boolean) -> Unit,
    toast: Boolean,
    onToast: (Boolean) -> Unit,
    sound: Boolean,
    onSound: (Boolean) -> Unit,
    soundMode: Int,
    onSoundMode: (Int) -> Unit,
    vibrate: Boolean,
    onVibrate: (Boolean) -> Unit,
    soundUri: String,
    onSoundUri: (String) -> Unit,
    noticeText: String,
    onNoticeText: (String) -> Unit,
    toastText: String,
    onToastText: (String) -> Unit,
    announce: Boolean,
    onAnnounce: (Boolean) -> Unit,
    announceText: String,
    onAnnounceText: (String) -> Unit,
    onNoticeTextFinished: ((String) -> Unit)? = null,
    onToastTextFinished: ((String) -> Unit)? = null,
    onAnnounceTextFinished: ((String) -> Unit)? = null
) {
    SettingsCard {
        SwitchRow(system, "通知栏提醒", "收款请求成功后显示") { onSystem(it) }
        InsetDivider(); VariableInputRow("通知栏文案", "支持下方变量", noticeText, transferTemplateVariables, onEditingFinished = onNoticeTextFinished, onValueChange = onNoticeText)

        InsetDivider()
        SwitchRow(toast, "浮窗提醒", "收款请求成功后短暂提示") { onToast(it) }
        InsetDivider(); VariableInputRow("浮窗文案", "支持下方变量", toastText, transferTemplateVariables, onEditingFinished = onToastTextFinished, onValueChange = onToastText)

        InsetDivider()
        SwitchRow(sound, "通知铃声", "开启后播放通知铃声") { onSound(it) }
        InsetDivider()
        PopupOptionRow(
            "铃声模式",
            if (soundMode == AutoTransferSettings.NOTIFY_SOUND_MODE_CUSTOM) "从文件选择铃声" else "选择系统铃声",
            optionItems("选择系统铃声" to AutoTransferSettings.NOTIFY_SOUND_MODE_SYSTEM, "从文件选择铃声" to AutoTransferSettings.NOTIFY_SOUND_MODE_CUSTOM),
            soundMode,
            onSoundMode
        )
        InsetDivider()
        ActionRow("选择铃声", ringtoneDisplayName(context, soundUri, soundMode)) {
            val activity = context as? Activity
            if (activity == null) {
                Toast.makeText(context, "当前页面无法打开铃声选择器", Toast.LENGTH_SHORT).show()
            } else {
                val picked: (String) -> Unit = { uri ->
                    if (uri.isNotBlank()) {
                        onSoundUri(
                            if (soundMode == AutoTransferSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                                CustomNotificationRuntime.freezeRingtoneUri(context, uri)
                            } else uri
                        )
                    }
                }
                if (soundMode == AutoTransferSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                    RingtonePickerBridge.launchFile(activity, picked)
                } else {
                    RingtonePickerBridge.launchSystem(activity, soundUri, picked)
                }
            }
        }

        InsetDivider()
        SwitchRow(vibrate, "通知震动", "开启后触发通知震动") { onVibrate(it) }
        InsetDivider()
        SwitchRow(announce, "收款语音播报", "收款请求成功后用系统语音播报") { onAnnounce(it) }
        InsetDivider(); VariableInputRow("播报文案", "支持下方变量", announceText, transferTemplateVariables, onEditingFinished = onAnnounceTextFinished, onValueChange = onAnnounceText)

    }
}

@Composable
internal fun FakeWalletBalanceMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FakeWalletBalanceSettings.PREFS_NAME) }
    var balanceEnabled by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.isAccountEnabled(
                sp,
                FakeWalletBalanceSettings.KEY_BALANCE_ENABLE
            )
        )
    }
    var lqtEnabled by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.isAccountEnabled(
                sp,
                FakeWalletBalanceSettings.KEY_LQT_ENABLE
            )
        )
    }
    var businessEnabled by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.isAccountEnabled(
                sp,
                FakeWalletBalanceSettings.KEY_BUSINESS_ENABLE
            )
        )
    }
    val storedBalanceAmount = remember {
        sp.getString(
            FakeWalletBalanceSettings.KEY_BALANCE_AMOUNT,
            FakeWalletBalanceSettings.DEFAULT_AMOUNT
        ) ?: FakeWalletBalanceSettings.DEFAULT_AMOUNT
    }
    val storedLqtAmount = remember {
        sp.getString(
            FakeWalletBalanceSettings.KEY_LQT_AMOUNT,
            FakeWalletBalanceSettings.DEFAULT_AMOUNT
        ) ?: FakeWalletBalanceSettings.DEFAULT_AMOUNT
    }
    val storedBusinessAmount = remember {
        sp.getString(FakeWalletBalanceSettings.KEY_BUSINESS_AMOUNT, null)
            ?: storedLqtAmount
    }
    var balanceAmount by remember {
        mutableStateOf(FakeWalletBalanceSettings.normalizeAmount(storedBalanceAmount))
    }
    var lqtAmount by remember {
        mutableStateOf(FakeWalletBalanceSettings.normalizeAmount(storedLqtAmount))
    }
    var businessAmount by remember {
        mutableStateOf(FakeWalletBalanceSettings.normalizeAmount(storedBusinessAmount))
    }
    var balanceMode by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.amountMode(
                sp,
                FakeWalletBalanceSettings.KEY_BALANCE_MODE,
                storedBalanceAmount
            )
        )
    }
    var lqtMode by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.amountMode(
                sp,
                FakeWalletBalanceSettings.KEY_LQT_MODE,
                storedLqtAmount
            )
        )
    }
    var businessMode by remember {
        val fallbackMode = if (!sp.contains(FakeWalletBalanceSettings.KEY_BUSINESS_AMOUNT)) {
            FakeWalletBalanceSettings.amountMode(
                sp,
                FakeWalletBalanceSettings.KEY_LQT_MODE,
                storedLqtAmount
            )
        } else {
            FakeWalletBalanceSettings.DEFAULT_MODE
        }
        mutableStateOf(
            FakeWalletBalanceSettings.amountMode(
                sp,
                FakeWalletBalanceSettings.KEY_BUSINESS_MODE,
                storedBusinessAmount,
                fallbackMode
            )
        )
    }
    val amountModeOptions = remember {
        listOf(
            PopupChoice("固定金额", FakeWalletBalanceSettings.MODE_FIXED),
            PopupChoice("增加金额", FakeWalletBalanceSettings.MODE_INCREASE),
            PopupChoice("减少金额", FakeWalletBalanceSettings.MODE_DECREASE)
        )
    }
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
            item { SettingsSectionTitle(text = "显示") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = balanceEnabled,
                        title = "余额",
                        summary = "固定显示或按真实金额动态增减"
                    ) { enabled ->
                        balanceEnabled = enabled
                        sp.edit().putBoolean(FakeWalletBalanceSettings.KEY_BALANCE_ENABLE, enabled).apply()
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = lqtEnabled,
                        title = "零钱通",
                        summary = "固定显示或按真实金额动态增减"
                    ) { enabled ->
                        lqtEnabled = enabled
                        sp.edit().putBoolean(FakeWalletBalanceSettings.KEY_LQT_ENABLE, enabled).apply()
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = businessEnabled,
                        title = "经营账户",
                        summary = "固定显示或按真实金额动态增减"
                    ) { enabled ->
                        businessEnabled = enabled
                        sp.edit().putBoolean(FakeWalletBalanceSettings.KEY_BUSINESS_ENABLE, enabled).apply()
                    }
                }
            }
            item { SettingsSectionTitle(text = "金额") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "余额显示方式",
                        summary = "选择固定显示或按真实余额计算",
                        options = amountModeOptions,
                        currentValue = balanceMode,
                        onValueChanged = { balanceMode = it; sp.edit().putString(FakeWalletBalanceSettings.KEY_BALANCE_MODE, it).apply() }
                    )
                    InsetDivider()
                    InputRow(
                        when (balanceMode) {
                            FakeWalletBalanceSettings.MODE_INCREASE -> "余额增加金额"
                            FakeWalletBalanceSettings.MODE_DECREASE -> "余额减少金额"
                            else -> "余额金额"
                        },
                        if (balanceAmount.isNotBlank() && !isCompleteWalletAmount(balanceAmount)) {
                            "金额不完整；保留上次保存的值"
                        } else if (balanceMode == FakeWalletBalanceSettings.MODE_FIXED) {
                            "直接显示该金额"
                        } else {
                            "基于微信真实余额计算"
                        },
                        balanceAmount,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(FakeWalletBalanceSettings.KEY_BALANCE_AMOUNT, FakeWalletBalanceSettings.normalizeAmount("0")).apply() }
                    ) { value ->
                        balanceAmount = value.filter { it.isDigit() || it == '.' || it == ',' }
                        if (isCompleteWalletAmount(balanceAmount)) sp.edit().putString(FakeWalletBalanceSettings.KEY_BALANCE_AMOUNT, FakeWalletBalanceSettings.normalizeAmount(balanceAmount)).apply()
                    }
                    InsetDivider()
                    PopupChoiceRow(
                        title = "零钱通显示方式",
                        summary = "选择固定显示或按真实金额计算",
                        options = amountModeOptions,
                        currentValue = lqtMode,
                        onValueChanged = { lqtMode = it; sp.edit().putString(FakeWalletBalanceSettings.KEY_LQT_MODE, it).apply() }
                    )
                    InsetDivider()
                    InputRow(
                        when (lqtMode) {
                            FakeWalletBalanceSettings.MODE_INCREASE -> "零钱通增加金额"
                            FakeWalletBalanceSettings.MODE_DECREASE -> "零钱通减少金额"
                            else -> "零钱通金额"
                        },
                        if (lqtAmount.isNotBlank() && !isCompleteWalletAmount(lqtAmount)) {
                            "金额不完整；保留上次保存的值"
                        } else if (lqtMode == FakeWalletBalanceSettings.MODE_FIXED) {
                            "直接显示该金额"
                        } else {
                            "基于微信真实金额计算"
                        },
                        lqtAmount,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(FakeWalletBalanceSettings.KEY_LQT_AMOUNT, FakeWalletBalanceSettings.normalizeAmount("0")).apply() }
                    ) { value ->
                        lqtAmount = value.filter { it.isDigit() || it == '.' || it == ',' }
                        if (isCompleteWalletAmount(lqtAmount)) sp.edit().putString(FakeWalletBalanceSettings.KEY_LQT_AMOUNT, FakeWalletBalanceSettings.normalizeAmount(lqtAmount)).apply()
                    }
                    InsetDivider()
                    PopupChoiceRow(
                        title = "经营账户显示方式",
                        summary = "选择固定显示或按真实金额计算",
                        options = amountModeOptions,
                        currentValue = businessMode,
                        onValueChanged = { businessMode = it; sp.edit().putString(FakeWalletBalanceSettings.KEY_BUSINESS_MODE, it).apply() }
                    )
                    InsetDivider()
                    InputRow(
                        when (businessMode) {
                            FakeWalletBalanceSettings.MODE_INCREASE -> "经营账户增加金额"
                            FakeWalletBalanceSettings.MODE_DECREASE -> "经营账户减少金额"
                            else -> "经营账户金额"
                        },
                        if (businessAmount.isNotBlank() && !isCompleteWalletAmount(businessAmount)) {
                            "金额不完整；保留上次保存的值"
                        } else if (businessMode == FakeWalletBalanceSettings.MODE_FIXED) {
                            "直接显示该金额"
                        } else {
                            "基于微信真实金额计算"
                        },
                        businessAmount,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(FakeWalletBalanceSettings.KEY_BUSINESS_AMOUNT, FakeWalletBalanceSettings.normalizeAmount("0")).apply() }
                    ) { value ->
                        businessAmount = value.filter { it.isDigit() || it == '.' || it == ',' }
                        if (isCompleteWalletAmount(businessAmount)) sp.edit().putString(FakeWalletBalanceSettings.KEY_BUSINESS_AMOUNT, FakeWalletBalanceSettings.normalizeAmount(businessAmount)).apply()
                    }
                }
            }
        }
    }
}

internal fun isCompleteWalletAmount(value: String): Boolean =
    value.matches(Regex("[0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?"))

internal fun isCompleteTransferAmount(value: String): Boolean =
    value.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) && value.toDoubleOrNull()?.isFinite() == true

@Composable
internal fun MemberTitleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MemberTitleSettings.PREFS_NAME) }
    var ownerTitle by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_OWNER_TITLE, MemberTitleSettings.DEFAULT_OWNER_TITLE) ?: MemberTitleSettings.DEFAULT_OWNER_TITLE) }
    var adminTitle by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_ADMIN_TITLE, MemberTitleSettings.DEFAULT_ADMIN_TITLE) ?: MemberTitleSettings.DEFAULT_ADMIN_TITLE) }
    var memberTitle by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_MEMBER_TITLE, MemberTitleSettings.DEFAULT_MEMBER_TITLE) ?: MemberTitleSettings.DEFAULT_MEMBER_TITLE) }
    var ownerColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_OWNER_COLOR, MemberTitleSettings.DEFAULT_OWNER_COLOR) ?: MemberTitleSettings.DEFAULT_OWNER_COLOR) }
    var adminColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_ADMIN_COLOR, MemberTitleSettings.DEFAULT_ADMIN_COLOR) ?: MemberTitleSettings.DEFAULT_ADMIN_COLOR) }
    var memberColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_MEMBER_COLOR, MemberTitleSettings.DEFAULT_MEMBER_COLOR) ?: MemberTitleSettings.DEFAULT_MEMBER_COLOR) }
    var customColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_CUSTOM_COLOR, MemberTitleSettings.DEFAULT_CUSTOM_COLOR) ?: MemberTitleSettings.DEFAULT_CUSTOM_COLOR) }
    var ownerTextColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_OWNER_TEXT_COLOR, MemberTitleSettings.DEFAULT_TEXT_COLOR) ?: MemberTitleSettings.DEFAULT_TEXT_COLOR) }
    var adminTextColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_ADMIN_TEXT_COLOR, MemberTitleSettings.DEFAULT_TEXT_COLOR) ?: MemberTitleSettings.DEFAULT_TEXT_COLOR) }
    var memberTextColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_MEMBER_TEXT_COLOR, MemberTitleSettings.DEFAULT_TEXT_COLOR) ?: MemberTitleSettings.DEFAULT_TEXT_COLOR) }
    var customTextColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_CUSTOM_TEXT_COLOR, MemberTitleSettings.DEFAULT_TEXT_COLOR) ?: MemberTitleSettings.DEFAULT_TEXT_COLOR) }
    var showMember by remember { mutableStateOf(sp.getBoolean(MemberTitleSettings.KEY_SHOW_MEMBER, MemberTitleSettings.DEFAULT_SHOW_MEMBER)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    fun saveTitle(key: String, value: String, fallback: String, finished: Boolean = false) {
        val cleaned = MemberTitleStore.cleanTitle(value)
        if (value.trim().length <= 8 && cleaned.isNotEmpty()) sp.edit().putString(key, cleaned).apply()
        else if (finished && value.isBlank()) sp.edit().putString(key, fallback).apply()
    }
    fun saveColor(key: String, value: String, fallback: String, finished: Boolean = false) {
        if (isCompleteColorSpec(value, true)) sp.edit().putString(key, MemberTitleStore.cleanColorSpec(value)).apply()
        else if (finished && value.isBlank()) sp.edit().putString(key, fallback).apply()
    }
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
            item { SettingsSectionTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(sp, MemberTitleSettings.KEY_ENABLE, "群员头衔", "在群聊昵称左侧显示身份头衔", MemberTitleSettings.DEFAULT_ENABLE)
                    InsetDivider()
                    SwitchRow(showMember, "显示普通群员", "关闭后只显示群主、管理员和自定义头衔") {
                        showMember = it
                        sp.edit().putBoolean(MemberTitleSettings.KEY_SHOW_MEMBER, it).apply()
                    }
                }
            }
            item { SettingsSectionTitle(text = "名称") }
            item {
                SettingsCard {
                    InputRow("群主名称", if (ownerTitle.length > 8) "最多 8 个字符，保留上次有效配置" else "最多 8 个字符", ownerTitle, onEditingFinished = { saveTitle(MemberTitleSettings.KEY_OWNER_TITLE, it, MemberTitleSettings.DEFAULT_OWNER_TITLE, true) }) { ownerTitle = it; saveTitle(MemberTitleSettings.KEY_OWNER_TITLE, it, MemberTitleSettings.DEFAULT_OWNER_TITLE) }
                    InsetDivider()
                    InputRow("管理员名称", if (adminTitle.length > 8) "最多 8 个字符，保留上次有效配置" else "最多 8 个字符", adminTitle, onEditingFinished = { saveTitle(MemberTitleSettings.KEY_ADMIN_TITLE, it, MemberTitleSettings.DEFAULT_ADMIN_TITLE, true) }) { adminTitle = it; saveTitle(MemberTitleSettings.KEY_ADMIN_TITLE, it, MemberTitleSettings.DEFAULT_ADMIN_TITLE) }
                    InsetDivider()
                    InputRow("群员名称", if (memberTitle.length > 8) "最多 8 个字符，保留上次有效配置" else "最多 8 个字符", memberTitle, onEditingFinished = { saveTitle(MemberTitleSettings.KEY_MEMBER_TITLE, it, MemberTitleSettings.DEFAULT_MEMBER_TITLE, true) }) { memberTitle = it; saveTitle(MemberTitleSettings.KEY_MEMBER_TITLE, it, MemberTitleSettings.DEFAULT_MEMBER_TITLE) }

                }
            }
            item { SettingsSectionTitle(text = "颜色") }
            item {
                SettingsCard {
                    ColorPickerRow("群主颜色", "默认黄色，渐变用 #F59E0B,#FDE047", ownerColor, onReset = { ownerColor = MemberTitleSettings.DEFAULT_OWNER_COLOR; saveColor(MemberTitleSettings.KEY_OWNER_COLOR, ownerColor, MemberTitleSettings.DEFAULT_OWNER_COLOR) }, error = if (ownerColor.isNotBlank() && !isCompleteColorSpec(ownerColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(MemberTitleSettings.KEY_OWNER_COLOR, it, MemberTitleSettings.DEFAULT_OWNER_COLOR, true) }) { ownerColor = it; saveColor(MemberTitleSettings.KEY_OWNER_COLOR, it, MemberTitleSettings.DEFAULT_OWNER_COLOR) }
                    InsetDivider()
                    ColorPickerRow("群主文字颜色", "默认白色，支持渐变", ownerTextColor, onReset = { ownerTextColor = MemberTitleSettings.DEFAULT_TEXT_COLOR; saveColor(MemberTitleSettings.KEY_OWNER_TEXT_COLOR, ownerTextColor, MemberTitleSettings.DEFAULT_TEXT_COLOR) }, error = if (ownerTextColor.isNotBlank() && !isCompleteColorSpec(ownerTextColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(MemberTitleSettings.KEY_OWNER_TEXT_COLOR, it, MemberTitleSettings.DEFAULT_TEXT_COLOR, true) }) { ownerTextColor = it; saveColor(MemberTitleSettings.KEY_OWNER_TEXT_COLOR, it, MemberTitleSettings.DEFAULT_TEXT_COLOR) }
                    InsetDivider()
                    ColorPickerRow("管理员颜色", "默认绿色，渐变用 #22C55E,#14B8A6", adminColor, onReset = { adminColor = MemberTitleSettings.DEFAULT_ADMIN_COLOR; saveColor(MemberTitleSettings.KEY_ADMIN_COLOR, adminColor, MemberTitleSettings.DEFAULT_ADMIN_COLOR) }, error = if (adminColor.isNotBlank() && !isCompleteColorSpec(adminColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(MemberTitleSettings.KEY_ADMIN_COLOR, it, MemberTitleSettings.DEFAULT_ADMIN_COLOR, true) }) { adminColor = it; saveColor(MemberTitleSettings.KEY_ADMIN_COLOR, it, MemberTitleSettings.DEFAULT_ADMIN_COLOR) }
                    InsetDivider()
                    ColorPickerRow("管理员文字颜色", "默认白色，支持渐变", adminTextColor, onReset = { adminTextColor = MemberTitleSettings.DEFAULT_TEXT_COLOR; saveColor(MemberTitleSettings.KEY_ADMIN_TEXT_COLOR, adminTextColor, MemberTitleSettings.DEFAULT_TEXT_COLOR) }, error = if (adminTextColor.isNotBlank() && !isCompleteColorSpec(adminTextColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(MemberTitleSettings.KEY_ADMIN_TEXT_COLOR, it, MemberTitleSettings.DEFAULT_TEXT_COLOR, true) }) { adminTextColor = it; saveColor(MemberTitleSettings.KEY_ADMIN_TEXT_COLOR, it, MemberTitleSettings.DEFAULT_TEXT_COLOR) }
                    InsetDivider()
                    ColorPickerRow("群员颜色", "默认灰色，渐变用 #64748B,#94A3B8", memberColor, onReset = { memberColor = MemberTitleSettings.DEFAULT_MEMBER_COLOR; saveColor(MemberTitleSettings.KEY_MEMBER_COLOR, memberColor, MemberTitleSettings.DEFAULT_MEMBER_COLOR) }, error = if (memberColor.isNotBlank() && !isCompleteColorSpec(memberColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(MemberTitleSettings.KEY_MEMBER_COLOR, it, MemberTitleSettings.DEFAULT_MEMBER_COLOR, true) }) { memberColor = it; saveColor(MemberTitleSettings.KEY_MEMBER_COLOR, it, MemberTitleSettings.DEFAULT_MEMBER_COLOR) }
                    InsetDivider()
                    ColorPickerRow("群员文字颜色", "默认白色，支持渐变", memberTextColor, onReset = { memberTextColor = MemberTitleSettings.DEFAULT_TEXT_COLOR; saveColor(MemberTitleSettings.KEY_MEMBER_TEXT_COLOR, memberTextColor, MemberTitleSettings.DEFAULT_TEXT_COLOR) }, error = if (memberTextColor.isNotBlank() && !isCompleteColorSpec(memberTextColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(MemberTitleSettings.KEY_MEMBER_TEXT_COLOR, it, MemberTitleSettings.DEFAULT_TEXT_COLOR, true) }) { memberTextColor = it; saveColor(MemberTitleSettings.KEY_MEMBER_TEXT_COLOR, it, MemberTitleSettings.DEFAULT_TEXT_COLOR) }

                    InsetDivider()
                    ColorPickerRow("自定义默认色", "点击聊天头衔可单独设置每个人的名称和颜色，支持渐变", customColor, onReset = { customColor = MemberTitleSettings.DEFAULT_CUSTOM_COLOR; saveColor(MemberTitleSettings.KEY_CUSTOM_COLOR, customColor, MemberTitleSettings.DEFAULT_CUSTOM_COLOR) }, error = if (customColor.isNotBlank() && !isCompleteColorSpec(customColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(MemberTitleSettings.KEY_CUSTOM_COLOR, it, MemberTitleSettings.DEFAULT_CUSTOM_COLOR, true) }) { customColor = it; saveColor(MemberTitleSettings.KEY_CUSTOM_COLOR, it, MemberTitleSettings.DEFAULT_CUSTOM_COLOR) }
                    InsetDivider()
                    ColorPickerRow("自定义文字默认色", "单人未设置文字颜色时使用，支持渐变", customTextColor, onReset = { customTextColor = MemberTitleSettings.DEFAULT_TEXT_COLOR; saveColor(MemberTitleSettings.KEY_CUSTOM_TEXT_COLOR, customTextColor, MemberTitleSettings.DEFAULT_TEXT_COLOR) }, error = if (customTextColor.isNotBlank() && !isCompleteColorSpec(customTextColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(MemberTitleSettings.KEY_CUSTOM_TEXT_COLOR, it, MemberTitleSettings.DEFAULT_TEXT_COLOR, true) }) { customTextColor = it; saveColor(MemberTitleSettings.KEY_CUSTOM_TEXT_COLOR, it, MemberTitleSettings.DEFAULT_TEXT_COLOR) }
                }
            }
        }
    }
}

@Composable
internal fun GroupNicknameColorMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, GroupNicknameColorSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(GroupNicknameColorSettings.KEY_ENABLE, GroupNicknameColorSettings.DEFAULT_ENABLE)
        )
    }
    var color by remember {
        mutableStateOf(
            sp.getString(GroupNicknameColorSettings.KEY_COLOR, GroupNicknameColorSettings.DEFAULT_COLOR)
                ?: GroupNicknameColorSettings.DEFAULT_COLOR
        )
    }
    var weight by remember {
        mutableStateOf(
            sp.getInt(GroupNicknameColorSettings.KEY_WEIGHT, GroupNicknameColorSettings.DEFAULT_WEIGHT).toString()
        )
    }
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
            item { SettingsSectionTitle(text = "群昵称") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "群昵称自定义颜色", "修改群聊成员昵称的颜色和粗细") {
                        enabled = it
                        sp.edit().putBoolean(GroupNicknameColorSettings.KEY_ENABLE, it).apply()
                    }
                    InsetDivider()
                    ColorPickerRow(
                        "昵称颜色",
                        "留空跟随微信，渐变用 #F59E0B,#22C55E",
                        color,
                        onReset = {
                            color = GroupNicknameColorSettings.DEFAULT_COLOR
                            sp.edit().putString(GroupNicknameColorSettings.KEY_COLOR, color).apply()
                        },
                        error = if (color.isNotBlank() && !isCompleteColorSpec(color, true)) "颜色不完整，保留上次保存的值" else null,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(GroupNicknameColorSettings.KEY_COLOR, "").apply() }
                    ) {
                        color = it
                        if (isCompleteColorSpec(it, true)) sp.edit().putString(GroupNicknameColorSettings.KEY_COLOR, GroupNicknameColorStore.cleanColorSpec(it)).apply()
                    }
                    InsetDivider()
                    InputRow("昵称粗细", if (weight.toIntOrNull()?.let { it in 100..900 } != true) "请输入 100-900；保留上次保存的值" else "100-900，600 以上显示为加粗", weight) {
                        weight = it.filter(Char::isDigit).take(3)
                        weight.toIntOrNull()?.takeIf { value -> value in 100..900 }?.let { value ->
                            sp.edit().putInt(GroupNicknameColorSettings.KEY_WEIGHT, GroupNicknameColorStore.cleanWeight(value)).apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RealNameTailMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RealNameTailSettings.PREFS_NAME) }
    var prefix by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GLOBAL_PREFIX, "") ?: "") }
    var maleText by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GENDER_MALE_TEXT, RealNameTailSettings.DEFAULT_GENDER_MALE_TEXT) ?: RealNameTailSettings.DEFAULT_GENDER_MALE_TEXT) }
    var femaleText by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GENDER_FEMALE_TEXT, RealNameTailSettings.DEFAULT_GENDER_FEMALE_TEXT) ?: RealNameTailSettings.DEFAULT_GENDER_FEMALE_TEXT) }
    var unknownGenderText by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GENDER_UNKNOWN_TEXT, RealNameTailSettings.DEFAULT_GENDER_UNKNOWN_TEXT) ?: RealNameTailSettings.DEFAULT_GENDER_UNKNOWN_TEXT) }
    var tailColor by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_TAIL_COLOR, RealNameTailSettings.DEFAULT_TAIL_COLOR) ?: RealNameTailSettings.DEFAULT_TAIL_COLOR) }
    var bracketColor by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_BRACKET_COLOR, RealNameTailSettings.DEFAULT_BRACKET_COLOR) ?: RealNameTailSettings.DEFAULT_BRACKET_COLOR) }
    var genderColor by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GENDER_COLOR, RealNameTailSettings.DEFAULT_GENDER_COLOR) ?: RealNameTailSettings.DEFAULT_GENDER_COLOR) }
    var regionColor by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_REGION_COLOR, RealNameTailSettings.DEFAULT_REGION_COLOR) ?: RealNameTailSettings.DEFAULT_REGION_COLOR) }
    var tailWeight by remember { mutableStateOf(sp.getInt(RealNameTailSettings.KEY_TAIL_WEIGHT, RealNameTailSettings.DEFAULT_TEXT_WEIGHT).toString()) }
    var bracketWeight by remember { mutableStateOf(sp.getInt(RealNameTailSettings.KEY_BRACKET_WEIGHT, RealNameTailSettings.DEFAULT_TEXT_WEIGHT).toString()) }
    var genderWeight by remember { mutableStateOf(sp.getInt(RealNameTailSettings.KEY_GENDER_WEIGHT, RealNameTailSettings.DEFAULT_TEXT_WEIGHT).toString()) }
    var regionWeight by remember { mutableStateOf(sp.getInt(RealNameTailSettings.KEY_REGION_WEIGHT, RealNameTailSettings.DEFAULT_TEXT_WEIGHT).toString()) }
    var prefixEnabled by remember { mutableStateOf(sp.getBoolean(RealNameTailSettings.KEY_GLOBAL_PREFIX_ENABLE, false)) }
    var showGender by remember { mutableStateOf(sp.getBoolean(RealNameTailSettings.KEY_SHOW_GENDER, RealNameTailSettings.DEFAULT_SHOW_GENDER)) }
    var showRegion by remember { mutableStateOf(sp.getBoolean(RealNameTailSettings.KEY_SHOW_REGION, RealNameTailSettings.DEFAULT_SHOW_REGION)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    fun saveText(key: String, value: String, limit: Int, finished: Boolean = false) {
        if (value.length <= limit && (value.isNotBlank() || finished)) sp.edit().putString(key, value.trim()).apply()
    }
    fun saveColor(key: String, value: String, finished: Boolean = false) {
        if (isCompleteColorSpec(value, true)) sp.edit().putString(key, RealNameTailStore.cleanColorSpec(value)).apply()
        else if (finished && value.isBlank()) sp.edit().putString(key, "").apply()
    }
    fun saveWeight(key: String, value: String, finished: Boolean = false) {
        val number = value.toIntOrNull()
        if (number != null && number in 100..900) sp.edit().putInt(key, RealNameTailStore.cleanWeight(number)).apply()
        else if (finished && value.isBlank()) sp.edit().putInt(key, RealNameTailSettings.DEFAULT_TEXT_WEIGHT).apply()
    }
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
            item { SettingsSectionTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(sp, RealNameTailSettings.KEY_ENABLE, "实名尾字", "在群聊昵称后显示已查询到的实名尾字", RealNameTailSettings.DEFAULT_ENABLE)
                    InsetDivider()
                    SwitchRow(sp, RealNameTailSettings.KEY_MESSAGE_QUERY, "消息触发查询", "收到群成员消息后自动补查", RealNameTailSettings.DEFAULT_MESSAGE_QUERY)
                    InsetDivider()
                    SwitchRow(sp, RealNameTailSettings.KEY_VISIBLE_QUERY, "可见成员查询", "聊天页出现未缓存成员时自动补查", RealNameTailSettings.DEFAULT_VISIBLE_QUERY)
                }
            }
            item { SettingsSectionTitle(text = "显示") }
            item {
                SettingsCard {
                    SwitchRow(prefixEnabled, "实名前缀", "用统一前缀替换实名尾字前半部分") {
                        prefixEnabled = it
                        sp.edit().putBoolean(RealNameTailSettings.KEY_GLOBAL_PREFIX_ENABLE, it).apply()
                    }
                    InsetDivider()
                    InputRow("前缀内容", if (prefix.length > 8) "最多 8 个字符，保留上次有效配置" else "最多 8 个字符，例如 *", prefix, onEditingFinished = { saveText(RealNameTailSettings.KEY_GLOBAL_PREFIX, it, 8, true) }) { prefix = it; saveText(RealNameTailSettings.KEY_GLOBAL_PREFIX, it, 8) }

                    InsetDivider()
                    SwitchRow(showGender, "显示性别", "在实名尾字后追加联系人性别") {
                        showGender = it
                        sp.edit().putBoolean(RealNameTailSettings.KEY_SHOW_GENDER, it).apply()
                    }
                    InsetDivider()
                    InputRow("男性文案", if (maleText.length > 12) "最多 12 个字符，保留上次有效配置" else "最多 12 个字符，默认 男", maleText, onEditingFinished = { saveText(RealNameTailSettings.KEY_GENDER_MALE_TEXT, it, 12, true) }) { maleText = it; saveText(RealNameTailSettings.KEY_GENDER_MALE_TEXT, it, 12) }
                    InsetDivider()
                    InputRow("女性文案", if (femaleText.length > 12) "最多 12 个字符，保留上次有效配置" else "最多 12 个字符，默认 女", femaleText, onEditingFinished = { saveText(RealNameTailSettings.KEY_GENDER_FEMALE_TEXT, it, 12, true) }) { femaleText = it; saveText(RealNameTailSettings.KEY_GENDER_FEMALE_TEXT, it, 12) }
                    InsetDivider()
                    InputRow("未知文案", if (unknownGenderText.length > 12) "最多 12 个字符，保留上次有效配置" else "最多 12 个字符，留空则不显示", unknownGenderText, onEditingFinished = { saveText(RealNameTailSettings.KEY_GENDER_UNKNOWN_TEXT, it, 12, true) }) { unknownGenderText = it; saveText(RealNameTailSettings.KEY_GENDER_UNKNOWN_TEXT, it, 12) }

                    InsetDivider()
                    SwitchRow(showRegion, "显示地区", "在实名尾字后追加联系人地区") {
                        showRegion = it
                        sp.edit().putBoolean(RealNameTailSettings.KEY_SHOW_REGION, it).apply()
                    }
                    InsetDivider()
                    ColorPickerRow("尾字颜色", "留空跟随昵称，渐变用 #F59E0B,#22C55E", tailColor, onReset = { tailColor = RealNameTailSettings.DEFAULT_TAIL_COLOR; saveColor(RealNameTailSettings.KEY_TAIL_COLOR, tailColor, true) }, error = if (tailColor.isNotBlank() && !isCompleteColorSpec(tailColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(RealNameTailSettings.KEY_TAIL_COLOR, it, true) }) { tailColor = it; saveColor(RealNameTailSettings.KEY_TAIL_COLOR, it) }
                    InsetDivider()
                    WeightInputRow("尾字粗细", tailWeight, onEditingFinished = { saveWeight(RealNameTailSettings.KEY_TAIL_WEIGHT, it, true) }) { tailWeight = it; saveWeight(RealNameTailSettings.KEY_TAIL_WEIGHT, it) }
                    InsetDivider()
                    ColorPickerRow("括号颜色", "留空跟随昵称，渐变用 #F59E0B,#22C55E", bracketColor, onReset = { bracketColor = RealNameTailSettings.DEFAULT_BRACKET_COLOR; saveColor(RealNameTailSettings.KEY_BRACKET_COLOR, bracketColor, true) }, error = if (bracketColor.isNotBlank() && !isCompleteColorSpec(bracketColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(RealNameTailSettings.KEY_BRACKET_COLOR, it, true) }) { bracketColor = it; saveColor(RealNameTailSettings.KEY_BRACKET_COLOR, it) }
                    InsetDivider()
                    WeightInputRow("括号粗细", bracketWeight, onEditingFinished = { saveWeight(RealNameTailSettings.KEY_BRACKET_WEIGHT, it, true) }) { bracketWeight = it; saveWeight(RealNameTailSettings.KEY_BRACKET_WEIGHT, it) }
                    InsetDivider()
                    ColorPickerRow("性别颜色", "留空跟随昵称，支持渐变", genderColor, onReset = { genderColor = RealNameTailSettings.DEFAULT_GENDER_COLOR; saveColor(RealNameTailSettings.KEY_GENDER_COLOR, genderColor, true) }, error = if (genderColor.isNotBlank() && !isCompleteColorSpec(genderColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(RealNameTailSettings.KEY_GENDER_COLOR, it, true) }) { genderColor = it; saveColor(RealNameTailSettings.KEY_GENDER_COLOR, it) }
                    InsetDivider()
                    WeightInputRow("性别粗细", genderWeight, onEditingFinished = { saveWeight(RealNameTailSettings.KEY_GENDER_WEIGHT, it, true) }) { genderWeight = it; saveWeight(RealNameTailSettings.KEY_GENDER_WEIGHT, it) }

                    InsetDivider()
                    ColorPickerRow("地区颜色", "留空跟随昵称，支持渐变", regionColor, onReset = { regionColor = RealNameTailSettings.DEFAULT_REGION_COLOR; saveColor(RealNameTailSettings.KEY_REGION_COLOR, regionColor, true) }, error = if (regionColor.isNotBlank() && !isCompleteColorSpec(regionColor, true)) "颜色不完整，保留上次有效配置" else null, onEditingFinished = { saveColor(RealNameTailSettings.KEY_REGION_COLOR, it, true) }) { regionColor = it; saveColor(RealNameTailSettings.KEY_REGION_COLOR, it) }
                    InsetDivider()
                    WeightInputRow("地区粗细", regionWeight, onEditingFinished = { saveWeight(RealNameTailSettings.KEY_REGION_WEIGHT, it, true) }) { regionWeight = it; saveWeight(RealNameTailSettings.KEY_REGION_WEIGHT, it) }

                }
            }
        }
    }
}
