package h.Hchat.ui.miuix

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.items.aichat.*
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.autoreply.AutoReplySettingsProvider
import h.Hchat.ui.FeatureSettingsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
internal fun AiChatMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val settings = remember { AiChatSettings(context) }
    val prefs = settings.prefs
    val scope = rememberCoroutineScope()
    val self = WeChatApis.account()?.selfWxId().orEmpty()
    var route by remember { mutableStateOf("main") }
    var configs by remember { mutableStateOf(settings.configs()) }
    var active by remember { mutableStateOf(settings.activeConfig()?.id.orEmpty()) }
    var draft by remember { mutableStateOf(AiChatConfig()) }
    var creating by remember { mutableStateOf(false) }
    var targetId by remember { mutableStateOf("") }
    var targets by remember(self) { mutableStateOf(settings.targets(self)) }
    val pickerStateHolder = rememberSaveableStateHolder()
    var prompt by remember { mutableStateOf(prefs.getString(AiChatSettings.KEY_PROMPT, AiChatSettings.DEFAULT_PROMPT).orEmpty()) }
    var tail by remember { mutableStateOf(prefs.getString(AiChatSettings.KEY_TAIL, AiChatSettings.DEFAULT_TAIL).orEmpty()) }
    var historyBudget by remember { mutableStateOf(prefs.getInt(AiChatSettings.KEY_CONTEXT, 7000).toString()) }
    var modelQuery by remember { mutableStateOf("") }
    var modelStatus by remember { mutableStateOf("") }
    var testStatus by remember { mutableStateOf("") }
    var facts by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var summaries by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var preferencesVersion by remember { mutableStateOf(0) }
    var showModels by remember { mutableStateOf(false) }
    var modelRefresh by remember { mutableStateOf(0) }
    var showSearchFee by remember { mutableStateOf(false) }
    var searchEnabled by remember { mutableStateOf(prefs.getBoolean(AiChatSettings.KEY_SEARCH_ENABLED, false)) }
    var searchSource by remember { mutableStateOf(prefs.getString(AiChatSettings.KEY_SEARCH_SOURCE, AiChatSettings.SEARCH_TAVILY).orEmpty()) }
    var tavilyKey by remember { mutableStateOf(prefs.getString(AiChatSettings.KEY_TAVILY_KEY, "").orEmpty()) }
    var searchTestStatus by remember { mutableStateOf("") }
    var usageExpanded by remember { mutableStateOf(false) }
    var legacyAiCount by remember { mutableStateOf(0) }
    var legacyAiRunning by remember { mutableStateOf(false) }
    val todayStart = remember(route) { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> Handler(Looper.getMainLooper()).post { preferencesVersion++ } }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val latestStatus = remember(preferencesVersion) { prefs.getString(AiChatSettings.KEY_STATUS, "暂无请求").orEmpty() }
    var usageRows by remember { mutableStateOf<List<AiUsageRecord>>(emptyList()) }
    var searchRows by remember { mutableStateOf<List<AiSearchUsage>>(emptyList()) }
    LaunchedEffect(route) {
        if (route == "main") {
            val old = AutoReplySettings(context)
            val rules = old.rules().filter { rule -> rule.steps.any { it.mode == AutoReplySettings.REPLY_ZHILIA_AI } }
            val accepting = old.autoAcceptSteps().any { it.mode == AutoReplySettings.REPLY_ZHILIA_AI }
            val greeting = old.greetAcceptedSteps().any { it.mode == AutoReplySettings.REPLY_ZHILIA_AI }
            legacyAiCount = rules.size + (if (accepting) 1 else 0) + (if (greeting) 1 else 0)
            legacyAiRunning = (old.isEnabled() && (rules.any { it.enabled } || (greeting && old.getBoolean(AutoReplySettings.KEY_GREET_ACCEPTED_ENABLE, false)))) ||
                (accepting && old.getBoolean(AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE, false))
        }
    }
    LaunchedEffect(route, targetId) {
        while (route == "memory" && targetId.isNotBlank() && isActive) {
            val state = withContext(Dispatchers.IO) { AiChatMemory.get(context).state(self, targetId) }
            progress = "已处理 ${state.count} 条 · ${if (state.initialDone) "等待增量" else "回填中"} · 记忆整理输入 ${state.input} / 输出 ${state.output} token" +
                if (state.pending) "\n请求正在处理或上次结果未确认" else if (state.error.isNotBlank()) "\n${state.error}" else ""
            delay(5000)
        }
    }
    LaunchedEffect(route, self) {
        if (route == "usage") {
            val result = withContext(Dispatchers.IO) { AiChatMemory.get(context).let { it.usage(self) to it.searchUsage(self) } }
            usageRows = result.first; searchRows = result.second
        }
    }
    LaunchedEffect(showModels, modelRefresh, draft.id, draft.provider, draft.baseUrl, draft.apiKey) {
        if (showModels) {
            val current = draft
            modelStatus = "正在拉取模型"
            val result = withContext(Dispatchers.IO) { runCatching { AiChatClient.fetchModels(current) } }
            if (showModels && draft.id == current.id && draft.provider == current.provider && draft.endpoint() == current.endpoint() && draft.apiKey == current.apiKey) {
                result.onSuccess { models ->
                    draft = draft.copy(models = models)
                    if (!creating) { configs = configs.map { if (it.id == draft.id) draft else it }; settings.saveConfigs(configs, active) }
                    modelStatus = "已获取 ${models.size} 个模型"
                }.onFailure { modelStatus = it.message ?: "拉取失败，当前模型已保留" }
            }
        }
    }

    fun updateDraft(value: AiChatConfig) {
        draft = value
        if (!creating && value.name.isNotBlank() && configs.none { it.id != value.id && it.name == value.name.trim() }) {
            configs = configs.map { if (it.id == value.id) value else it }
            settings.saveConfigs(configs, active)
        }
    }
    fun updateTarget(value: AiChatTarget) {
        targets = targets.map { if (it.talker == value.talker) value else it }
        settings.saveTargets(self, targets)
        AiChatRuntime.scheduleMemory(context)
    }
    fun loadMemory(talker: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val db = AiChatMemory.get(context)
                db.state(self, talker) to db.summaries(self, talker)
            }
            facts = result.first.facts
            progress = "已处理 ${result.first.count} 条 · ${if (result.first.initialDone) "等待增量" else "回填中"} · 记忆整理输入 ${result.first.input} / 输出 ${result.first.output} token" +
                if (result.first.pending) "\n上次请求结果未确认，请手动继续" else if (result.first.error.isNotBlank()) "\n${result.first.error}" else ""
            summaries = result.second
        }
    }
    fun selectConfig(value: AiChatConfig, isNew: Boolean) {
        draft = value; creating = isNew; modelStatus = ""; testStatus = ""; modelQuery = ""; route = "config"
    }
    fun goBack() {
        route = when (route) { "main" -> { onBack(); "main" }; "config" -> "configs"; "memory" -> "target"; "target" -> "picker"; else -> "main" }
    }
    val target = targets.firstOrNull { it.talker == targetId }
    SettingsRouteTransition(targetState = route, label = "AiChatRoute", depthOf = {
        when (it) { "main" -> 0; "configs", "picker", "usage", "search", "legacyRules" -> 1; "memory" -> 3; else -> 2 }
    }) { page ->
        if (page == "picker") {
            val request = ContactPickerRequest("聊天对象", ContactPickerMode.BOTH, true,
                formatIds(targets.filter { it.enabled }.map { it.talker }), onValue = {}, strictFriendsAndGroupsOnly = true,
                showSelectedCount = true, preserveScrollOnOpen = true, onDetails = { option ->
                    if (targets.none { it.talker == option.id }) {
                        targets = targets + AiChatTarget(option.id, enabled = false)
                        settings.saveTargets(self, targets)
                    }
                    targetId = option.id; route = "target"
                })
            pickerStateHolder.SaveableStateProvider("ai-chat-contacts") {
                ContactPickerPage(context, request, onBack = { route = "main" }, onSelectionChange = { selected ->
                    val oldIds = targets.mapTo(HashSet()) { it.talker }
                    targets = targets.map { it.copy(enabled = it.talker in selected) } +
                        selected.filterNot { it in oldIds }.map { AiChatTarget(it) }
                    settings.saveTargets(self, targets)
                    AiChatRuntime.scheduleMemory(context)
                })
            }
        } else if (page == "legacyRules") {
            AutoReplyMiuixPage(context, remember { AutoReplySettingsProvider() }, onBack = { route = "main" },
                initialScene = AutoReplyInitialScene.MAIN, relatedProviders = emptyList(), onOpenProvider = {})
        } else {
            val title = when (page) { "configs" -> "模型服务"; "config" -> if (creating) "新增模型配置" else "编辑模型配置"; "target" -> "聊天对象"; "memory" -> "记忆管理"; "usage" -> "用量与费用"; "search" -> "联网搜索"; else -> provider.title() }
            val scroll = MiuixScrollBehavior()
            PageScaffold(title, title, scroll, onBack = ::goBack, showBackIcon = false) { padding ->
                LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)) {
                    when (page) {
                        "main" -> {
                            item { SettingsSectionTitle("AI 聊天") }
                            item { SettingsCard {
                                SwitchRow(prefs, AiChatSettings.KEY_ENABLE, "AI 聊天", "仅回复已开启的聊天对象", false)
                                InsetDivider()
                                ActionRow("模型服务", configs.firstOrNull { it.id == active }?.let { "${providerName(it.provider)} · ${it.model.ifBlank { "未选择模型" }}" } ?: "尚未配置") { route = "configs" }
                            } }
                            item { SettingsSectionTitle("全局指令") }
                            item { SettingsCard { InputRow("全局指令", "留空即可；会话独立指令优先", prompt, minLines = 3) { prompt = it; prefs.edit().putString(AiChatSettings.KEY_PROMPT, it).apply() } } }
                            item { SettingsSectionTitle("聊天对象") }
                            item { SettingsCard { ActionRow("聊天对象", "已启用 ${targets.count { it.enabled }} 个好友或群聊") {
                                if (self.isNotBlank()) route = "picker" else Toast.makeText(context, "微信账号尚未就绪", Toast.LENGTH_SHORT).show()
                            } } }
                            item { SettingsSectionTitle("记忆与上下文") }
                            item { SettingsCard {
                                InputRow("输入预算", "token 估算；0 表示不带历史，当前问题仍会发送", historyBudget) {
                                    historyBudget = it
                                    it.toIntOrNull()?.takeIf { n -> n in 0..16000 }?.let { n -> prefs.edit().putInt(AiChatSettings.KEY_CONTEXT, n).apply() }
                                }
                            } }
                            item { SettingsSectionTitle("其他") }
                            item { SettingsCard {
                                InputRow("AI 引用小尾巴", "", tail) { tail = it; prefs.edit().putString(AiChatSettings.KEY_TAIL, it).apply() }
                                InsetDivider()
                                ActionRow("联网搜索", if (!searchEnabled) "已关闭" else if (searchSource == AiChatSettings.SEARCH_OFFICIAL) "官方搜索 · 可能额外收费" else "Tavily · 按需搜索") { route = "search" }
                                InsetDivider()
                                InfoRow("最近状态", latestStatus)
                                InsetDivider()
                                ActionRow("用量与费用", "查看今日和累计预估") { route = "usage" }
                                if (legacyAiCount > 0) {
                                    InsetDivider(); ActionRow("旧自动回复 AI 规则", "${legacyAiCount} 条 · ${if (legacyAiRunning) "仍由自动回复控制" else "当前未启用"}") { route = "legacyRules" }
                                }
                            } }
                        }
                        "configs" -> {
                            item { SettingsSectionTitle("配置") }
                            configs.forEach { config -> item(key = config.id) { SettingsCard(modifier = Modifier.padding(bottom = 8.dp)) {
                                ContentActionRow("${if (active == config.id) "●" else "○"} ${config.name}${if (active == config.id) " · 当前使用" else ""}") { active = config.id; settings.saveConfigs(configs, active) }
                                InsetDivider(); ActionRow("编辑配置", "${providerName(config.provider)} · ${config.model.ifBlank { "未选择模型" }}") { selectConfig(config, false) }
                            } } }
                            item { SettingsCard { AddActionRow("新增配置") { selectConfig(AiChatConfig(), true) } } }
                        }
                        "config" -> {
                            item { SettingsSectionTitle("服务商") }
                            item { SettingsCard {
                                AiProvider.entries.forEach { p -> ContentActionRow((if (draft.provider == p) "✓ " else "") + when (p) { AiProvider.DEEPSEEK -> "DeepSeek"; AiProvider.MIMO -> "小米 MiMo"; AiProvider.CUSTOM -> "第三方中转站" }) { if (p != draft.provider) updateDraft(draft.copy(provider = p, model = "", models = emptyList(), webSearch = false)) } }
                            } }
                            item { SettingsSectionTitle("配置") }
                            item { SettingsCard {
                                InputRow("配置名称", "", draft.name) { updateDraft(draft.copy(name = it)) }; InsetDivider()
                                InputRow("API Key", "", draft.apiKey) { updateDraft(draft.copy(apiKey = it.trim())) }; InsetDivider()
                                if (draft.provider == AiProvider.CUSTOM) {
                                    InputRow("API 地址", "例如 https://example.com/v1", draft.baseUrl) { updateDraft(draft.copy(baseUrl = it.trim())) }; InsetDivider()
                                    InputRow("接口路径", "默认 /chat/completions", draft.chatPath) { updateDraft(draft.copy(chatPath = it.trim())) }; InsetDivider()
                                    InputRow("模型", "可手动填写", draft.model) { updateDraft(draft.copy(model = it.trim())) }; InsetDivider()
                                    SwitchRow(draft.image, "图片能力", "由中转站实际支持情况决定") { updateDraft(draft.copy(image = it)) }; InsetDivider()
                                    SwitchRow(draft.audio, "原生音频能力", "由中转站实际支持情况决定") { updateDraft(draft.copy(audio = it)) }; InsetDivider()
                                    SwitchRow(draft.video, "视频能力", "由中转站实际支持情况决定") { updateDraft(draft.copy(video = it)) }; InsetDivider()
                                }
                                ActionRow("选择模型", draft.model.ifBlank { "尚未选择" }) { modelQuery = ""; showModels = true }; InsetDivider()
                                InfoRow("媒体能力", buildList { if (draft.supports("image")) add("图片"); add("微信语音转文字"); if (draft.supports("audio")) add("模型理解音频文件"); if (draft.supports("video")) add("视频") }.joinToString(" / ")); InsetDivider()
                                ActionRow("测试连通性", testStatus.ifBlank { "发送一次短请求" }) {
                                    val current = draft; testStatus = "正在测试"
                                    scope.launch { testStatus = withContext(Dispatchers.IO) { runCatching {
                                        AiChatRuntime.complete(context, self, current, AiPurpose.TEST, listOf(AiInput("user", "请只回复 OK")), 16)
                                        "连接成功"
                                    }.getOrElse { it.message ?: "连接失败" } } }
                                }
                            } }
                            item { SettingsCard(modifier = Modifier.padding(top = 12.dp)) {
                                if (creating) ContentActionRow("创建配置", emphasis = ContentActionEmphasis.PRIMARY) {
                                    if (draft.name.isBlank() || draft.apiKey.isBlank() || draft.model.isBlank() || draft.endpoint().isBlank() || configs.any { it.name == draft.name.trim() }) {
                                        Toast.makeText(context, "请完整填写配置，且名称不能重复", Toast.LENGTH_SHORT).show()
                                    } else {
                                        configs = configs + draft.copy(name = draft.name.trim()); if (active.isBlank()) active = draft.id
                                        settings.saveConfigs(configs, active); route = "configs"
                                    }
                                } else {
                                    ContentActionRow("复制配置") {
                                        val base = draft.name + " 副本"
                                        var name = base
                                        var number = 2
                                        while (configs.any { it.name == name }) name = "$base ${number++}"
                                        val copy = draft.copy(id = java.util.UUID.randomUUID().toString(), name = name)
                                        configs = configs + copy; settings.saveConfigs(configs, active); route = "configs"
                                    }
                                    ContentActionRow("删除配置", emphasis = ContentActionEmphasis.DANGER) { confirmDelete = true }
                                }
                            } }
                        }
                        "search" -> {
                            item { SettingsCard {
                                SwitchRow(searchEnabled, "联网搜索", "仅在需要当前信息或使用 #搜索 时调用") {
                                    searchEnabled = it; prefs.edit().putBoolean(AiChatSettings.KEY_SEARCH_ENABLED, it).apply()
                                }
                            } }
                            item { SettingsSectionTitle("搜索来源") }
                            item { SettingsCard {
                                ContentActionRow("${if (searchSource == AiChatSettings.SEARCH_TAVILY) "●" else "○"} Tavily") {
                                    searchSource = AiChatSettings.SEARCH_TAVILY; prefs.edit().putString(AiChatSettings.KEY_SEARCH_SOURCE, searchSource).apply()
                                }
                                ContentActionRow("${if (searchSource == AiChatSettings.SEARCH_OFFICIAL) "●" else "○"} 模型服务商官方搜索 · 可能额外收费") {
                                    if (searchSource != AiChatSettings.SEARCH_OFFICIAL) showSearchFee = true
                                }
                            } }
                            if (searchSource == AiChatSettings.SEARCH_TAVILY) {
                                item { SettingsCard(modifier = Modifier.padding(top = 8.dp)) {
                                    InputRow("Tavily API Key", "", tavilyKey) { tavilyKey = it.trim(); prefs.edit().putString(AiChatSettings.KEY_TAVILY_KEY, tavilyKey).apply(); searchTestStatus = "" }
                                    InsetDivider(); InfoRow("免费额度", "免费方案每月 1,000 credits；basic 搜索通常消耗 1 credit。额度以 Tavily 账户为准。")
                                    InsetDivider(); ActionRow("测试连接", searchTestStatus.ifBlank { "查询账户额度，不执行搜索" }) {
                                        val key = tavilyKey; searchTestStatus = "正在测试"
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) { runCatching { "连接成功 · 可用额度 ${AiChatSearch.remaining(key)} credits" }.getOrElse { it.message ?: "连接失败" } }
                                            if (key == tavilyKey) searchTestStatus = result
                                        }
                                    }
                                } }
                            }
                        }
                        "usage" -> {
                            item { SettingsSectionTitle("预估费用") }
                            item { SettingsCard {
                                InfoRow("今日", AiChatUsage.costSummary(usageRows.filter { it.started >= todayStart })); InsetDivider()
                                InfoRow("累计", AiChatUsage.costSummary(usageRows)); InsetDivider()
                                InfoRow("缓存命中率", AiChatUsage.cacheSummary(usageRows)); InsetDivider()
                                InfoRow("模型请求", "${usageRows.size} 次"); InsetDivider()
                                ActionRow(if (usageExpanded) "收起详细统计" else "详细统计", "") { usageExpanded = !usageExpanded }
                            } }
                            if (usageExpanded) {
                                val totals = AiChatUsage.totals(usageRows)
                                item { SettingsCard(modifier = Modifier.padding(top = 8.dp)) {
                                    InfoRow("Token", "输入 ${totals.input} · 输出 ${totals.output} · 命中 ${totals.cacheHit}")
                                    InsetDivider(); InfoRow("官方搜索", "${totals.search} 次；网页读取 ${totals.fetch} 次")
                                    InsetDivider(); InfoRow("Tavily", "${searchRows.sumOf { it.credits ?: 0L }} credits${if (searchRows.any { it.credits == null }) "（部分未知）" else ""}；不计入模型费用")
                                    InsetDivider(); InfoRow("统计开始", usageRows.minOfOrNull { it.started }?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) } ?: "等待首次请求")
                                    InsetDivider(); InfoRow("说明", "按 2026-11-16 官方按量价格估算，未知部分未计入；非官方账单。")
                                } }
                                usageRows.groupBy { it.provider }.forEach { (provider, rows) -> item(key = provider.name) {
                                    SettingsCard(modifier = Modifier.padding(top = 8.dp)) { InfoRow(providerName(provider), "${rows.size} 次 · ${AiChatUsage.costSummary(rows)}") }
                                } }
                                item { SettingsCard(modifier = Modifier.padding(top = 8.dp)) { InfoRow("旧版未分类用量", "输入 ${prefs.getLong("usage_input", 0)} / 输出 ${prefs.getLong("usage_output", 0)} / 缓存 ${prefs.getLong("usage_cached", 0)}${if (prefs.getBoolean("usage_cache_unknown", false)) "（部分未知）" else ""}") } }
                            }
                        }
                        "target" -> if (target != null) {
                            item { SettingsSectionTitle(target.talker) }
                            item { SettingsCard {
                                SwitchRow(target.enabled, "启用会话", "关闭后保留记忆") { updateTarget(target.copy(enabled = it)) }; InsetDivider()
                                InputRow("独立指令", "留空时使用全局指令", target.prompt, minLines = 3) { updateTarget(target.copy(prompt = it)) }
                                if (target.talker.endsWith("@chatroom") || target.talker.endsWith("@im.chatroom")) {
                                    InsetDivider(); SwitchRow(target.atMe, "@我触发", "") { updateTarget(target.copy(atMe = it)) }
                                    InsetDivider(); SwitchRow(target.quoteMe, "引用我触发", "") { updateTarget(target.copy(quoteMe = it)) }
                                }
                                InsetDivider(); ActionRow("记忆管理", if (target.paused) "历史整理已暂停" else "历史整理进行中") { loadMemory(target.talker); route = "memory" }
                            } }
                        }
                        "memory" -> if (target != null) {
                            item { SettingsSectionTitle("历史整理") }
                            item { SettingsCard {
                                InfoRow("进度", progress)
                                SwitchRow(!target.paused, "自动整理", "", onCheckedChange = { updateTarget(target.copy(paused = !it)) })
                                ContentActionRow("继续未完成的整理") {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { AiChatRuntime.retryMemory(context, self, target.talker) }
                                        if (ok) loadMemory(target.talker) else Toast.makeText(context, "已有整理任务运行中", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } }
                            item { SettingsSectionTitle("长期记忆") }
                            item { SettingsCard {
                                InputRow("记忆", "编辑完成后自动保存", facts, minLines = 5, onEditingFinished = { value -> scope.launch(Dispatchers.IO) { AiChatMemory.get(context).editFacts(self, target.talker, value) } }) { facts = it }
                                InsetDivider(); ContentActionRow("清空记忆", emphasis = ContentActionEmphasis.DANGER) { confirmClear = true }
                            } }
                            if (summaries.isNotEmpty()) {
                                item { SettingsSectionTitle("历史摘要") }
                                summaries.forEach { summary -> item { SettingsCard(modifier = Modifier.padding(bottom = 8.dp)) { InfoRow("摘要", summary) } } }
                            }
                        }
                    }
                }
            }
        }
    }
    SettingsWindowDialog(show = showModels, title = "选择模型", onDismissRequest = { showModels = false }) {
        Column {
            InfoRow("状态", modelStatus)
            ContentActionRow("刷新模型列表") { modelRefresh++ }
            if (draft.provider == AiProvider.CUSTOM && draft.models.size > 30) InputRow("搜索", "模型名称", modelQuery) { modelQuery = it }
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(draft.models.filter { it.id.contains(modelQuery, true) }, key = { it.id }) { model ->
                    ContentActionRow("${if (model.id == draft.model) "●" else "○"} ${model.id}") {
                        updateDraft(draft.copy(model = model.id)); showModels = false
                    }
                }
            }
        }
    }
    SettingsWindowDialog(show = showSearchFee, title = "启用联网搜索", onDismissRequest = { showSearchFee = false }) {
        Column {
            InfoRow("费用", "官方联网可能产生额外搜索和网页读取费用。MiMo 需要先在控制台开通联网插件。")
            ContentActionRow("选择官方搜索") { searchSource = AiChatSettings.SEARCH_OFFICIAL; prefs.edit().putString(AiChatSettings.KEY_SEARCH_SOURCE, searchSource).apply(); showSearchFee = false }
            ContentActionRow("取消") { showSearchFee = false }
        }
    }
    BatchDeleteConfirmDialog(show = confirmDelete, message = "将删除该模型配置。", labels = listOf(draft.name), onDismiss = { confirmDelete = false }, onConfirm = {
        configs = configs.filterNot { it.id == draft.id }; if (active == draft.id) active = configs.firstOrNull()?.id.orEmpty()
        settings.saveConfigs(configs, active); confirmDelete = false; route = "configs"
    })
    BatchDeleteConfirmDialog(show = confirmClear, message = "清空记忆和摘要，已有历史不会自动重新整理。", labels = listOf(targetId), onDismiss = { confirmClear = false }, onConfirm = {
        confirmClear = false
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val store = WeChatApis.messageStore()?.takeIf { it.isAvailable } ?: return@withContext false
                val latest = store.getLatestMessage(targetId)
                if (latest == null && store.hasMessagesAfterState(targetId, -1L, 0L) != false) return@withContext false
                AiChatMemory.get(context).clear(self, targetId, latest?.createTime ?: System.currentTimeMillis(), latest?.msgId ?: 0)
                true
            }
            if (ok) loadMemory(targetId) else Toast.makeText(context, "微信历史尚不可读取，未清空", Toast.LENGTH_SHORT).show()
        }
    })
}

private fun providerName(provider: AiProvider): String = when (provider) {
    AiProvider.DEEPSEEK -> "DeepSeek"
    AiProvider.MIMO -> "小米 MiMo"
    AiProvider.CUSTOM -> "第三方"
}

private fun purposeName(purpose: AiPurpose): String = when (purpose) {
    AiPurpose.CHAT -> "聊天"
    AiPurpose.MEMORY -> "记忆整理"
    AiPurpose.TEST -> "连通性测试"
}
