package h.Hchat.ui.miuix

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.items.aichat.*
import h.Hchat.ui.FeatureSettingsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var pickerMode by remember { mutableStateOf(ContactPickerMode.FRIENDS) }
    var pickerExisting by remember { mutableStateOf(false) }
    var contactNames by remember(self) { mutableStateOf<Map<String, String>>(emptyMap()) }
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
    var favoriteVersion by remember { mutableStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> Handler(Looper.getMainLooper()).post { preferencesVersion++ } }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val latestStatus = remember(preferencesVersion) { prefs.getString(AiChatSettings.KEY_STATUS, "暂无请求").orEmpty() }
    val latestUsage = remember(preferencesVersion) {
        "输入 ${prefs.getLong("usage_input", 0)} · 输出 ${prefs.getLong("usage_output", 0)} · 缓存 ${if (prefs.getBoolean("usage_cache_unknown", false)) "部分未知" else prefs.getLong("usage_cached", 0).toString()}"
    }
    val favorites = remember(draft.endpoint(), favoriteVersion) { settings.favorites(draft.endpoint()) }
    LaunchedEffect(self, targets.map { it.talker }) {
        contactNames = withContext(Dispatchers.IO) {
            targets.associate { target -> target.talker to WeChatApis.contacts()?.getDisplayName(target.talker).orEmpty() }
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
        route = when (route) { "main" -> { onBack(); "main" }; "models" -> "config"; "config" -> "configs"; "memory" -> "target"; else -> "main" }
    }
    val target = targets.firstOrNull { it.talker == targetId }
    SettingsRouteTransition(targetState = route, label = "AiChatRoute", depthOf = {
        when (it) { "main" -> 0; "configs", "picker", "target" -> 1; else -> 2 }
    }) { page ->
        if (page == "picker") {
            val request = ContactPickerRequest("添加聊天对象", pickerMode, true, "", onValue = {}, strictFriendsOnly = pickerMode == ContactPickerMode.FRIENDS,
                onlyExistingConversations = pickerExisting, strictFriendsAndGroupsOnly = pickerExisting, nativeHomeConversationList = pickerExisting)
            ContactPickerPage(context, request, onBack = { route = "main" }, onConfirm = { selected ->
                val additions = selected.map { AiChatTarget(it.id) }.filter { t -> targets.none { it.talker == t.talker } }
                targets = targets + additions
                settings.saveTargets(self, targets)
                AiChatRuntime.scheduleMemory(context)
                route = "main"
            })
        } else {
            val title = when (page) { "configs" -> "模型服务"; "config" -> if (creating) "新增模型配置" else "编辑模型配置"; "models" -> "选择模型"; "target" -> "聊天对象"; "memory" -> "记忆管理"; else -> provider.title() }
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
                                ActionRow("模型服务", settings.activeConfig()?.let { "${it.name} · ${it.model}" } ?: "尚未配置") { route = "configs" }
                            } }
                            item { SettingsSectionTitle("全局指令") }
                            item { SettingsCard { InputRow("全局指令", "会话独立指令优先", prompt, minLines = 3) { prompt = it; prefs.edit().putString(AiChatSettings.KEY_PROMPT, it).apply() } } }
                            item { SettingsSectionTitle("聊天对象") }
                            item { SettingsCard {
                                ActionRow("添加好友", "多选好友") { if (self.isNotBlank()) { pickerExisting = false; pickerMode = ContactPickerMode.FRIENDS; route = "picker" } else Toast.makeText(context, "微信账号尚未就绪", Toast.LENGTH_SHORT).show() }
                                InsetDivider()
                                ActionRow("添加群聊", "多选群聊") { if (self.isNotBlank()) { pickerExisting = false; pickerMode = ContactPickerMode.GROUPS; route = "picker" } else Toast.makeText(context, "微信账号尚未就绪", Toast.LENGTH_SHORT).show() }
                                InsetDivider()
                                ActionRow("从会话添加", "选择已有好友或群聊") { if (self.isNotBlank()) { pickerExisting = true; pickerMode = ContactPickerMode.BOTH; route = "picker" } else Toast.makeText(context, "微信账号尚未就绪", Toast.LENGTH_SHORT).show() }
                            } }
                            targets.forEach { itemTarget -> item {
                                SettingsCard(modifier = Modifier.padding(top = 8.dp)) {
                                    ActionRow(contactNames[itemTarget.talker].orEmpty().ifBlank { itemTarget.talker }, if (itemTarget.enabled) "已开启" else "已关闭") { targetId = itemTarget.talker; route = "target" }
                                }
                            } }
                            item { SettingsSectionTitle("记忆与上下文") }
                            item { SettingsCard {
                                InputRow("输入预算", "token 估算；0 表示不带历史，当前问题仍会发送", historyBudget) {
                                    historyBudget = it
                                    it.toIntOrNull()?.takeIf { n -> n in 0..16000 }?.let { n -> prefs.edit().putInt(AiChatSettings.KEY_CONTEXT, n).apply() }
                                }
                            } }
                            item { SettingsSectionTitle("媒体") }
                            item { SettingsCard {
                                SwitchRow(prefs, AiChatSettings.KEY_IMAGE, "图片理解", "", false); InsetDivider()
                                SwitchRow(prefs, AiChatSettings.KEY_VOICE, "语音理解", "优先微信转文字", false); InsetDivider()
                                SwitchRow(prefs, AiChatSettings.KEY_NATIVE_AUDIO, "模型直接理解音频", "启用时将语音转换为兼容格式", false); InsetDivider()
                                SwitchRow(prefs, AiChatSettings.KEY_VIDEO, "视频理解", "", false)
                            } }
                            item { SettingsSectionTitle("其他") }
                            item { SettingsCard {
                                InputRow("AI 引用小尾巴", "", tail) { tail = it; prefs.edit().putString(AiChatSettings.KEY_TAIL, it).apply() }
                                InsetDivider()
                                InfoRow("最近状态", latestStatus)
                                InsetDivider()
                                InfoRow("用量", latestUsage)
                            } }
                        }
                        "configs" -> {
                            item { SettingsSectionTitle("配置") }
                            configs.forEach { config -> item { SettingsCard(modifier = Modifier.padding(bottom = 8.dp)) {
                                ActionRow(config.name, "${config.model.ifBlank { "未选择模型" }}${if (active == config.id) " · 当前" else ""}") { selectConfig(config, false) }
                                ContentActionRow("设为当前") { active = config.id; settings.saveConfigs(configs, active) }
                            } } }
                            item { SettingsCard { AddActionRow("新增配置") { selectConfig(AiChatConfig(), true) } } }
                        }
                        "config" -> {
                            item { SettingsSectionTitle("服务商") }
                            item { SettingsCard {
                                AiProvider.entries.forEach { p -> ContentActionRow((if (draft.provider == p) "✓ " else "") + when (p) { AiProvider.DEEPSEEK -> "DeepSeek"; AiProvider.MIMO -> "小米 MiMo"; AiProvider.CUSTOM -> "第三方中转站" }) { updateDraft(draft.copy(provider = p, model = "", models = emptyList())) } }
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
                                ActionRow("选择模型", draft.model.ifBlank { "尚未选择" }) { route = "models" }; InsetDivider()
                                ActionRow("测试连通性", testStatus.ifBlank { "发送一次短请求" }) {
                                    val current = draft; testStatus = "正在测试"
                                    scope.launch { testStatus = withContext(Dispatchers.IO) { runCatching {
                                        val result = AiChatClient.complete(current, listOf(AiInput("user", "请只回复 OK")), 16)
                                        AiChatRuntime.recordUsage(context, result.usage); "连接成功"
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
                        "models" -> {
                            item { SettingsSectionTitle("模型") }
                            item { SettingsCard {
                                ActionRow("拉取模型列表", modelStatus.ifBlank { "从当前服务获取 /models" }) {
                                    val current = draft; modelStatus = "正在拉取"
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { runCatching { AiChatClient.fetchModels(current) } }
                                        if (draft.id == current.id && draft.provider == current.provider && draft.endpoint() == current.endpoint() && draft.apiKey == current.apiKey) {
                                            result.onSuccess { models -> updateDraft(draft.copy(models = models)); modelStatus = "已获取 ${models.size} 个模型" }
                                                .onFailure { modelStatus = it.message ?: "拉取失败，可手动输入第三方模型" }
                                        }
                                    }
                                }
                                InsetDivider(); InputRow("搜索", "", modelQuery) { modelQuery = it }
                            } }
                            draft.models.filter { it.id.contains(modelQuery, true) }.sortedWith(compareByDescending<AiModel> { it.id in favorites }.thenBy { it.id }).forEach { model -> item {
                                SettingsCard(modifier = Modifier.padding(top = 8.dp)) {
                                    ActionRow(model.id, if (model.id == draft.model) "当前模型" else model.modalities.joinToString(" / ")) { updateDraft(draft.copy(model = model.id)); route = "config" }
                                    ContentActionRow(if (model.id in favorites) "取消收藏" else "收藏") { settings.saveFavorites(draft.endpoint(), if (model.id in favorites) favorites - model.id else favorites + model.id); favoriteVersion++ }
                                }
                            } }
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
