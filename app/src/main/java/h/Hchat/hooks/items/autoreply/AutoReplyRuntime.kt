package h.Hchat.hooks.items.autoreply

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.utils.HLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random

object AutoReplyRuntime {
    private const val TAG = "[Hchat:AutoReply]"
    private const val AT_ALL_WXID = "notify@all"
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Hchat-AutoReply").apply { isDaemon = true }
    }
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val replyCounts = ConcurrentHashMap<String, Int>()
    private val lastReplyTimes = ConcurrentHashMap<String, Long>()
    private val aiHistories = ConcurrentHashMap<String, MutableList<AiMessage>>()

    fun clearAiHistories() {
        aiHistories.clear()
    }

    fun fetchAiModels(apiBaseUrl: String, apiKey: String): List<String> {
        val urls = candidateModelUrls(apiBaseUrl)
        for (url in urls) {
            val models = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey")
                    }
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful || text.isBlank()) return@use emptyList<String>()
                    parseModelList(text)
                }
            }.getOrElse {
                HLog.e("$TAG 拉取模型列表失败: ${it.message}", it)
                emptyList()
            }
            if (models.isNotEmpty()) return models
        }
        return emptyList()
    }

    fun testAiConnectivity(config: AutoReplyAiConfig): String {
        if (config.apiKey.isBlank() || config.apiBaseUrl.isBlank() || config.model.isBlank()) {
            return "请先填写 API Key、API 地址和模型"
        }
        val history = listOf(
            AiMessage("system", config.systemPrompt.ifBlank { AutoReplySettings.DEFAULT_AI_SYSTEM_PROMPT }),
            AiMessage("user", "请只回复 OK")
        )
        val nonStreamOk = callAiOnce(config.copy(stream = false), history).orEmpty().isNotBlank()
        val streamOk = callAiStream(config.copy(stream = true), history).orEmpty().isNotBlank()
        return when {
            nonStreamOk && streamOk -> "流式、非流式都可用"
            nonStreamOk -> "非流式可用，流式不可用"
            streamOk -> "流式可用，非流式不可用"
            else -> "流式、非流式都不可用"
        }
    }

    fun handleMessage(context: Context, message: WeChatMessageObserveApi.ObservedMessage) {
        val settings = AutoReplySettings(context)
        if (!settings.isEnabled()) return
        if (shouldSkipMessage(message)) return
        val talker = message.talker.ifBlank { message.getTalker() }
        if (settings.shouldExcludeTalker(talker)) return
        executor.execute {
            runCatching {
                handleFriendAccepted(context, settings, message)
                handleAutoReply(context, settings, message)
            }.onFailure {
                HLog.e("$TAG 处理消息失败: ${it.message}", it)
            }
        }
    }

    fun handleNewFriend(context: Context, wxid: String, ticket: String, scene: Int) {
        val settings = AutoReplySettings(context)
        if (!settings.getBoolean(AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE, false)) return
        executor.execute {
            runCatching {
                val verifyWxid = AutoReplyFriendRequestHook.resolveVerifyUsername(wxid, ticket, scene)
                WeChatApis.contact().verifyUser()?.verifyUser(verifyWxid, ticket, scene)
                modifyLabelIfNeeded(
                    wxid,
                    settings.getBoolean(AutoReplySettings.KEY_AUTO_ACCEPT_TAG_ENABLE, false),
                    settings.getString(AutoReplySettings.KEY_AUTO_ACCEPT_TAG_NAME, "")
                )
                applyFriendLabels(settings, wxid, autoAcceptAutomationKeys)
                applyFriendRemark(settings, wxid, autoAcceptAutomationKeys)
                val delay = settings.getLong(AutoReplySettings.KEY_AUTO_ACCEPT_DELAY_MS, 2000L).coerceAtLeast(0L)
                executeSteps(context, wxid, settings.autoAcceptSteps(), MessageContext(talker = wxid, sender = wxid), delay)
            }.onFailure {
                HLog.e("$TAG 好友申请处理失败: ${it.message}", it)
            }
        }
    }

    private fun handleFriendAccepted(
        context: Context,
        settings: AutoReplySettings,
        message: WeChatMessageObserveApi.ObservedMessage
    ) {
        if (!settings.getBoolean(AutoReplySettings.KEY_GREET_ACCEPTED_ENABLE, false)) return
        val talker = message.talker.ifBlank { message.getTalker() }
        val content = plainContent(message).trim()
        if (talker.isBlank() || message.group || talker.endsWith("@chatroom")) return
        if (content != AutoReplySettings.FRIEND_ACCEPTED_KEYWORD) return
        modifyLabelIfNeeded(
            talker,
            settings.getBoolean(AutoReplySettings.KEY_GREET_ACCEPTED_TAG_ENABLE, false),
            settings.getString(AutoReplySettings.KEY_GREET_ACCEPTED_TAG_NAME, "")
        )
        applyFriendLabels(settings, talker, greetAcceptedAutomationKeys)
        applyFriendRemark(settings, talker, greetAcceptedAutomationKeys)
        val delay = settings.getLong(AutoReplySettings.KEY_GREET_ACCEPTED_DELAY_MS, 2000L).coerceAtLeast(0L)
        executeSteps(context, talker, settings.greetAcceptedSteps(), MessageContext(talker = talker, sender = talker, content = content), delay)
    }

    private fun handleAutoReply(
        context: Context,
        settings: AutoReplySettings,
        message: WeChatMessageObserveApi.ObservedMessage
    ) {
        val talker = message.talker.ifBlank { message.getTalker() }
        if (talker.isBlank()) return
        val group = message.group || message.isGroupChat() || talker.endsWith("@chatroom") || talker.endsWith("@im.chatroom")
        val sender = resolveSender(message, talker, group)
        val content = plainContent(message)
        val isPat = message.isPat()
        val isText = message.isText()
        if (message.isSystem() && !isPat) return
        if (isText && content.isBlank() && !isPat) return

        val ctx = MessageContext(
            talker = talker,
            sender = sender,
            content = content,
            msgId = message.getMsgId(),
            group = group,
            atMe = message.isAtMe(),
            atAll = message.isNotifyAll(),
            patMe = isPat && message.getPatMsg()?.pattedUser == WeChatApis.contact().account()?.selfWxId()
        )
        for (rule in settings.rules()) {
            if (!rule.enabled) continue
            val executableSteps = filterRemovedReplySteps(rule.steps)
            if (executableSteps.isEmpty()) continue
            if (!inTimeRange(rule.startTime, rule.endTime)) continue
            if (!targetMatches(rule, ctx, message)) continue
            if (!triggerMatches(rule, ctx, isPat)) continue
            if (!keywordMatches(rule, content, isText, isPat)) continue
            if (isReplyCoolingDown(rule, ctx.talker)) continue
            if (!countAndCheck(rule, ctx)) continue
            if (executeFilteredSteps(context, talker, executableSteps, ctx, 0L, rule.replyAsQuote)) {
                markReplyCooldown(rule, ctx.talker)
            }
        }
    }

    internal fun filterRemovedReplySteps(steps: List<AutoReplyStep>): List<AutoReplyStep> =
        steps.filterNot { AutoReplySettings.isRemovedReplyMode(it.mode) }

    private fun executeSteps(
        context: Context,
        target: String,
        steps: List<AutoReplyStep>,
        message: MessageContext,
        initialDelayMs: Long = 0L,
        replyAsQuote: Boolean = false
    ): Boolean = executeFilteredSteps(
        context,
        target,
        filterRemovedReplySteps(steps),
        message,
        initialDelayMs,
        replyAsQuote
    )

    private fun executeFilteredSteps(
        context: Context,
        target: String,
        executableSteps: List<AutoReplyStep>,
        message: MessageContext,
        initialDelayMs: Long,
        replyAsQuote: Boolean
    ): Boolean {
        if (executableSteps.isEmpty()) return false
        if (initialDelayMs > 0) sleep(initialDelayMs)
        var sent = false
        for ((index, step) in executableSteps.withIndex()) {
            val delay = step.delayMs + if (step.randomDelay) Random.nextLong(0L, 2001L) else 0L
            if (delay > 0) sleep(delay)
            sent = sendStep(context, target, step, message, replyAsQuote) || sent
            if (index < executableSteps.lastIndex && step.delayMs <= 0L) sleep(300L)
        }
        return sent
    }

    private fun sendStep(
        context: Context,
        target: String,
        step: AutoReplyStep,
        message: MessageContext,
        replyAsQuote: Boolean
    ): Boolean {
        val content = format(step.content, message)
        val messageApi = WeChatApis.message().sender()
        val mediaApi = WeChatApis.media()
        return runCatching {
            when (step.mode) {
                AutoReplySettings.REPLY_TEXT -> {
                    val parts = splitReplies(content)
                    var sent = false
                    parts.forEachIndexed { index, text ->
                        val currentSent = if (replyAsQuote && message.msgId > 0 && !text.contains("[AtWx=")) {
                            messageApi?.sendQuote(target, message.msgId, text) == true
                        } else {
                            sendText(target, text)
                        }
                        sent = currentSent || sent
                        if (index < parts.lastIndex) sleep(300L)
                    }
                    sent
                }
                AutoReplySettings.REPLY_IMAGE -> sendPaths(content) { mediaApi?.sendImage(target, it) == true }
                AutoReplySettings.REPLY_VOICE -> sendPaths(content) { mediaApi?.sendVoice(target, it) == true }
                AutoReplySettings.REPLY_VOICE_RANDOM_FOLDER -> {
                    val file = randomAudioFile(content) ?: return@runCatching false
                    mediaApi?.sendVoice(target, file) == true
                }
                AutoReplySettings.REPLY_EMOJI -> sendPaths(content) { mediaApi?.sendEmoji(target, it) == true }
                AutoReplySettings.REPLY_VIDEO -> sendPaths(content) { mediaApi?.videos()?.send(target, it) == true }
                AutoReplySettings.REPLY_FILE -> sendPaths(content) { path ->
                    mediaApi?.sendFile(target, path, File(path).name) == true
                }
                AutoReplySettings.REPLY_FAVORITE -> sendFavorites(content) {
                    mediaApi?.favorites()?.send(target, it) == true
                }
                AutoReplySettings.REPLY_CARD -> splitMulti(content).any { messageApi?.sendShareCard(target, it) == true }
                AutoReplySettings.REPLY_INVITE_GROUP -> {
                    val member = if (message.group) message.sender else target
                    splitMulti(content).any { WeChatApis.contact().chatrooms()?.inviteChatroomMember(it, member) == true }
                }
                AutoReplySettings.REPLY_XML -> messageApi?.sendXml(target, ensureXml(content)) == true
                AutoReplySettings.REPLY_ZHILIA_AI -> sendZhiliaAiReply(context, target, message, replyAsQuote)
                else -> false
            }
        }.onFailure {
            HLog.e("$TAG 发送回复失败: ${it.message}", it)
        }.getOrDefault(false)
    }

    private fun sendZhiliaAiReply(
        context: Context,
        target: String,
        message: MessageContext,
        replyAsQuote: Boolean
    ): Boolean {
        val config = AutoReplySettings(context).aiConfig()
        if (config.apiKey.isBlank()) {
            showToast(context, "请先配置自动回复 AI Key")
            return false
        }
        val question = aiQuestion(message)
        if (question.isBlank()) return false
        val answer = requestAi(target, config, question)
        if (answer.isBlank()) return false
        val sender = WeChatApis.message().sender() ?: return false
        return if (replyAsQuote && message.msgId > 0L) {
            sender.sendQuote(target, message.msgId, answer)
        } else {
            sendText(target, answer)
        }
    }

    private fun aiQuestion(message: MessageContext): String =
        message.content.replace(Regex("""@[^\s]+\s+"""), "").trim()

    private fun requestAi(talker: String, config: AutoReplyAiConfig, question: String): String {
        val history = aiHistories.getOrPut(talker) {
            mutableListOf<AiMessage>().also {
                if (config.systemPrompt.isNotBlank()) it += AiMessage("system", config.systemPrompt)
            }
        }
        synchronized(history) {
            if (history.firstOrNull()?.role == "system" && history.first().content != config.systemPrompt) {
                if (config.systemPrompt.isBlank()) history.removeAt(0) else history[0] = AiMessage("system", config.systemPrompt)
            } else if (history.none { it.role == "system" } && config.systemPrompt.isNotBlank()) {
                history.add(0, AiMessage("system", config.systemPrompt))
            }
            history += AiMessage("user", question)
            trimHistory(history, config.contextLimit)
            val answer = if (config.stream) {
                callAiStream(config, history) ?: callAiOnce(config, history)
            } else {
                callAiOnce(config, history) ?: callAiStream(config, history)
            }.orEmpty().cleanAiText()
            if (answer.isNotBlank()) {
                history += AiMessage("assistant", answer)
                trimHistory(history, config.contextLimit)
            }
            return answer
        }
    }

    private fun callAiOnce(config: AutoReplyAiConfig, history: List<AiMessage>): String? {
        return runCatching {
            val body = aiRequestBody(config, history, false)
            val response = httpClient.newCall(
                Request.Builder()
                    .url(finalAiUrl(config))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful || text.isBlank()) return@runCatching null
                JSONObject(text).optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.takeIf { value -> value.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun callAiStream(config: AutoReplyAiConfig, history: List<AiMessage>): String? {
        return runCatching {
            val body = aiRequestBody(config, history, true)
            val response = httpClient.newCall(
                Request.Builder()
                    .url(finalAiUrl(config))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            response.use {
                if (!it.isSuccessful) return@runCatching null
                val reader = it.body?.byteStream()?.bufferedReader(Charsets.UTF_8) ?: return@runCatching null
                val out = StringBuilder()
                reader.useLines { lines ->
                    lines.forEach { line ->
                        val data = line.trim().removePrefix("data:").trim()
                        if (data.isBlank() || data == line.trim() || data == "[DONE]") return@forEach
                        val obj = runCatching { JSONObject(data) }.getOrNull() ?: return@forEach
                        val choice = obj.optJSONArray("choices")?.optJSONObject(0) ?: return@forEach
                        val delta = choice.optJSONObject("delta")
                        val piece = delta?.optString("content").orEmpty()
                        if (piece.isNotBlank() && !piece.equals("null", ignoreCase = true)) out.append(piece)
                    }
                }
                out.toString().takeIf { value -> value.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun aiRequestBody(config: AutoReplyAiConfig, history: List<AiMessage>, stream: Boolean): JSONObject {
        return JSONObject().apply {
            put("model", config.model)
            put("temperature", 0.7)
            put("stream", stream)
            put("messages", JSONArray().apply {
                history.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
        }
    }

    private fun finalAiUrl(config: AutoReplyAiConfig): String {
        val base = config.apiBaseUrl.trim().trimEnd('/')
        val path = config.apiPath.trim().ifBlank { AutoReplySettings.DEFAULT_AI_API_PATH }
        return base + if (path.startsWith("/")) path else "/$path"
    }

    private fun candidateModelUrls(apiBaseUrl: String): List<String> {
        val base = apiBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) return emptyList()
        val primary = if (base.endsWith("/chat/completions")) {
            base.removeSuffix("/chat/completions") + "/models"
        } else if (base.endsWith("/models")) {
            base
        } else {
            "$base/models"
        }
        val alt = primary.removeSuffix("/models").removeSuffix("/v1") + "/v1/models"
        return listOf(primary, alt).distinct()
    }

    private fun parseModelList(text: String): List<String> {
        return runCatching {
            val obj = JSONObject(text)
            val result = linkedSetOf<String>()
            fun addArray(array: JSONArray?) {
                if (array == null) return
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    val id = when (item) {
                        is JSONObject -> item.optString("id").ifBlank { item.optString("name") }
                        is String -> item
                        else -> ""
                    }.trim()
                    if (id.isNotEmpty()) result += id
                }
            }
            addArray(obj.optJSONArray("data"))
            addArray(obj.optJSONArray("models"))
            addArray(obj.optJSONArray("result"))
            result.sorted()
        }.getOrDefault(emptyList())
    }

    private fun trimHistory(history: MutableList<AiMessage>, contextLimit: Int) {
        val max = contextLimit.coerceAtLeast(0) * 2 + if (history.firstOrNull()?.role == "system") 1 else 0
        if (max <= 0) {
            history.clear()
            return
        }
        while (history.size > max) {
            val removeIndex = if (history.firstOrNull()?.role == "system") 1 else 0
            if (removeIndex in history.indices) history.removeAt(removeIndex) else break
        }
    }

    private fun targetMatches(
        rule: AutoReplyRule,
        ctx: MessageContext,
        message: WeChatMessageObserveApi.ObservedMessage
    ): Boolean {
        if (rule.excludedIds.contains(ctx.talker) ||
            rule.excludedIds.contains(ctx.sender) ||
            (ctx.group && rule.excludedIds.contains("${ctx.talker}/${ctx.sender}"))
        ) return false
        if (ctx.group && (
                rule.excludedGroupMembers.contains(ctx.sender) ||
                    rule.excludedGroupMembers.contains("${ctx.talker}/${ctx.sender}")
                )
        ) return false
        if (rule.targetMode == AutoReplySettings.TARGET_SPECIFIC) {
            return includedTargetMatches(rule, ctx)
        }
        return when (rule.targetMode) {
            AutoReplySettings.TARGET_PRIVATE -> !ctx.group && !isOfficialTalker(message, ctx.talker)
            AutoReplySettings.TARGET_GROUP -> ctx.group
            AutoReplySettings.TARGET_OFFICIAL -> isOfficialTalker(message, ctx.talker)
            else -> true
        }
    }

    private fun isOfficialTalker(
        message: WeChatMessageObserveApi.ObservedMessage,
        talker: String
    ): Boolean {
        return message.isOfficialAccount() ||
            talker.startsWith("gh_", ignoreCase = true) ||
            talker.endsWith("@app", ignoreCase = true) ||
            talker.equals("newsapp", ignoreCase = true)
    }

    private fun shouldSkipMessage(message: WeChatMessageObserveApi.ObservedMessage): Boolean {
        if (message.isSend()) return true
        val selfWxId = WeChatApis.contact().account()?.selfWxId().orEmpty()
        if (selfWxId.isBlank()) return message.source != "message_db"
        return message.sender.equals(selfWxId, ignoreCase = true) ||
            message.getSendTalker().equals(selfWxId, ignoreCase = true)
    }

    private fun includedTargetMatches(rule: AutoReplyRule, ctx: MessageContext): Boolean {
        val targetIds = rule.targetIds.filter { isValidTargetId(it) }.toSet()
        val includedMembers = rule.includedGroupMembers.filter { isValidTargetId(it) }.toSet()
        return targetIds.contains(ctx.talker) ||
            (!ctx.group && targetIds.contains(ctx.sender)) ||
            (ctx.group && targetIds.contains("${ctx.talker}/${ctx.sender}")) ||
            (ctx.group && includedMembers.contains("${ctx.talker}/${ctx.sender}")) ||
            (ctx.group && includedMembers.contains(ctx.sender))
    }

    private fun isValidTargetId(value: String): Boolean {
        return value.split('/').all { part ->
            val id = part.trim()
            id.isNotEmpty() && !id.contains("@@")
        }
    }

    private fun triggerMatches(rule: AutoReplyRule, ctx: MessageContext, isPat: Boolean): Boolean {
        if (ctx.group) {
            if (rule.atTrigger == AutoReplySettings.AT_ME && !ctx.atMe) return false
            if (rule.atTrigger == AutoReplySettings.AT_ALL && !ctx.atAll) return false
        } else if (rule.atTrigger != AutoReplySettings.AT_NONE) {
            return false
        }
        if (rule.patTrigger == AutoReplySettings.PAT_ME && !ctx.patMe) return false
        return rule.patTrigger != AutoReplySettings.PAT_ME || isPat
    }

    private fun keywordMatches(
        rule: AutoReplyRule,
        content: String,
        isText: Boolean,
        isPat: Boolean
    ): Boolean {
        if (isPat && rule.patTrigger == AutoReplySettings.PAT_ME) return true
        val keyword = rule.keyword
        return when (rule.matchType) {
            AutoReplySettings.MATCH_ANY -> !isText ||
                splitKeywords(rule.excludedKeywords).none { content.contains(it) }
            AutoReplySettings.MATCH_EXACT -> isText && splitKeywords(keyword).any { content == it }
            AutoReplySettings.MATCH_REGEX -> isText &&
                runCatching { Pattern.compile(keyword).matcher(content).find() }.getOrDefault(false)
            else -> isText && splitKeywords(keyword).any { content.contains(it) || it.isBlank() }
        }
    }

    private fun countAndCheck(rule: AutoReplyRule, ctx: MessageContext): Boolean {
        if (rule.maxReplyCount <= 0) return true
        val key = "${rule.id}|${ctx.talker}|${ctx.sender}"
        val current = replyCounts[key] ?: 0
        if (current >= rule.maxReplyCount) return false
        replyCounts[key] = current + 1
        return true
    }

    private fun isReplyCoolingDown(rule: AutoReplyRule, talker: String): Boolean {
        if (rule.cooldownSeconds <= 0L) return false
        val key = replyCooldownKey(rule, talker)
        val lastReplyAt = lastReplyTimes[key] ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now >= lastReplyAt && now - lastReplyAt < replyCooldownMillis(rule)) return true
        lastReplyTimes.remove(key, lastReplyAt)
        return false
    }

    private fun markReplyCooldown(rule: AutoReplyRule, talker: String) {
        if (rule.cooldownSeconds <= 0L) return
        lastReplyTimes[replyCooldownKey(rule, talker)] = SystemClock.elapsedRealtime()
    }

    private fun replyCooldownKey(rule: AutoReplyRule, talker: String): String =
        "${rule.id}|$talker"

    private fun replyCooldownMillis(rule: AutoReplyRule): Long =
        rule.cooldownSeconds.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L

    private fun plainContent(message: WeChatMessageObserveApi.ObservedMessage): String {
        val raw = message.content.ifBlank { message.getContent() }.ifBlank { message.xml }
        val groupPrefix = Regex("""^[^\s:]{3,80}:\n""")
        return raw.replace(groupPrefix, "").trim()
    }

    private fun resolveSender(message: WeChatMessageObserveApi.ObservedMessage, talker: String, group: Boolean): String {
        val direct = message.sender.ifBlank { message.getSendTalker() }
        if (direct.isNotBlank()) return direct
        if (!group) return talker
        return Regex("""^([^\s:]{3,80}):\n""")
            .find(message.content.ifBlank { message.getContent() })
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

    private fun format(template: String, ctx: MessageContext): String {
        val contacts = WeChatApis.contact().contacts()
        val senderName = if (ctx.group) {
            contacts?.getGroupMemberDisplayName(ctx.talker, ctx.sender).orEmpty()
        } else {
            contacts?.getDisplayName(ctx.sender).orEmpty()
        }.ifBlank { ctx.sender }
        val groupName = if (ctx.group) contacts?.getDisplayName(ctx.talker).orEmpty() else ""
        return template
            .replace("%friendName%", senderName)
            .replace("%senderName%", senderName)
            .replace("%senderWxid%", ctx.sender)
            .replace("%talker%", ctx.talker)
            .replace("%groupName%", groupName)
            .replace("%content%", ctx.content)
            .replace("%atSender%", "[AtWx=${ctx.sender}]")
            .replace("%atAll%", if (ctx.group) "[AtWx=$AT_ALL_WXID]" else "@所有人")
    }

    private fun sendText(talker: String, content: String): Boolean {
        if (talker.isBlank() || content.isBlank()) return false
        val sender = WeChatApis.message().sender() ?: return false
        val atRegex = Regex("""\[AtWx=([^\]]+)]""")
        val atList = atRegex.findAll(content).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
        val text = atRegex.replace(content) { match ->
            val wxid = match.groupValues[1].trim()
            val name = if (wxid == AT_ALL_WXID) {
                "所有人"
            } else {
                val contacts = WeChatApis.contact().contacts()
                contacts?.getGroupMemberDisplayName(talker, wxid).orEmpty()
                    .ifBlank { contacts?.getDisplayName(wxid).orEmpty() }
                    .ifBlank { wxid }
            }
            "@$name\u2005"
        }
        return if (atList.isEmpty()) sender.sendText(talker, text) else sender.sendTextWithAtList(talker, text, atList)
    }

    private fun sendPaths(value: String, sender: (String) -> Boolean): Boolean {
        var ok = false
        for (path in splitMulti(value)) {
            val file = File(path)
            if (file.isFile && sender(path)) ok = true
            sleep(300L)
        }
        return ok
    }

    private fun sendFavorites(value: String, sender: (String) -> Boolean): Boolean {
        var ok = false
        for (localId in splitMulti(value)) {
            if (sender(localId)) ok = true
            sleep(300L)
        }
        return ok
    }

    private fun randomAudioFile(folderPath: String): String? {
        val directFiles = splitMulti(folderPath)
            .map { File(it) }
            .filter { it.isFile && it.extension.lowercase() in audioExtensions }
        if (directFiles.isNotEmpty()) return directFiles.random().absolutePath
        val folder = File(folderPath)
        if (!folder.isDirectory) return null
        val files = folder.listFiles { file ->
            file.isFile && file.extension.lowercase() in audioExtensions
        }.orEmpty()
        return files.randomOrNull()?.absolutePath
    }

    private fun splitReplies(value: String): List<String> =
        value.split('|').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(value).filter { it.isNotBlank() } }

    private fun splitKeywords(value: String): List<String> =
        value.split('|', '，', ',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun splitMulti(value: String): List<String> =
        value.split(";;;")
            .flatMap { it.split('\n') }
            .flatMap { it.split('|') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private val audioExtensions = setOf("mp3", "wav", "ogg", "aac", "m4a", "silk")

    private fun ensureXml(content: String): String {
        val text = content.trim()
        if (text.startsWith("<")) return content
        val safe = content.replace("]]>", "]]]]><![CDATA[>")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><msg><appmsg appid=\"\" sdkver=\"0\"><title><![CDATA[$safe]]></title><des><![CDATA[$safe]]></des><type>1</type><content><![CDATA[$safe]]></content></appmsg></msg>"
    }

    private fun inTimeRange(start: String, end: String): Boolean {
        if (start.isBlank() || end.isBlank()) return true
        val startSecond = parseSecondOfDay(start) ?: return true
        val endSecond = parseSecondOfDay(end) ?: return true
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 3600 +
            now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND)
        return if (endSecond < startSecond) {
            current >= startSecond || current < endSecond
        } else {
            current >= startSecond && current < endSecond
        }
    }

    private fun parseSecondOfDay(value: String): Int? {
        val parts = value.split(':')
        if (parts.size !in 2..3) return null
        val hour = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: return null
        val minute = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: return null
        val second = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hour * 3600 + minute * 60 + second
    }

    private fun modifyLabelIfNeeded(wxid: String, enabled: Boolean, label: String) {
        if (!enabled || wxid.isBlank() || label.isBlank()) return
        runCatching { WeChatApis.contact().contacts()?.modifyContactLabelList(wxid, label) }
    }

    private fun applyFriendLabels(settings: AutoReplySettings, wxid: String, keys: FriendAutomationKeys) {
        if (wxid.isBlank()) return
        val labels = linkedSetOf<String>()
        if (settings.getBoolean(keys.labelNewFriendEnable, false)) {
            labels += "新加好友"
        }
        if (settings.getBoolean(keys.labelDateEnable, false)) {
            labels += formatDate(settings.getString(keys.labelDateFormat, "yyyy-MM-dd"), "yyyy-MM-dd")
        }
        if (settings.getBoolean(keys.labelExistingEnable, false)) {
            labels += splitSavedNames(settings.getString(keys.labelSelectedNames, ""))
        }
        if (labels.isEmpty()) return
        val contacts = WeChatApis.contact().contacts() ?: return
        labels.forEach { label ->
            runCatching { contacts.addContactLabel(label) }
                .onFailure { HLog.e("$TAG 创建好友标签失败: $label ${it.message}", it) }
        }
        runCatching { contacts.modifyContactLabelList(wxid, labels.toList()) }
            .onFailure { HLog.e("$TAG 修改好友标签失败: ${it.message}", it) }
    }

    private fun applyFriendRemark(settings: AutoReplySettings, wxid: String, keys: FriendAutomationKeys) {
        if (wxid.isBlank()) return
        val parts = mutableListOf<String>()
        if (settings.getBoolean(keys.remarkNewFriendEnable, false)) {
            parts += "新加好友"
        }
        if (settings.getBoolean(keys.remarkDateEnable, false)) {
            parts += formatDate(settings.getString(keys.remarkDateFormat, "yyMMdd"), "yyMMdd")
        }
        if (settings.getBoolean(keys.remarkCustomEnable, false)) {
            settings.getString(keys.remarkCustomText, "").trim()
                .takeIf { it.isNotBlank() }
                ?.let { parts += it }
        }
        if (parts.isEmpty()) return
        val contacts = WeChatApis.contact().contacts() ?: return
        val suffix = parts.joinToString("")
        val nickname = contacts.getContact(wxid)?.let { contact ->
            contact.nickname.ifBlank { contact.displayName() }.ifBlank { wxid }
        } ?: wxid
        val remark = if (settings.getBoolean(keys.remarkNicknameSuffixEnable, false)) {
            nickname + suffix
        } else {
            suffix
        }
        runCatching { contacts.modifyContactRemark(wxid, remark) }
            .onFailure { HLog.e("$TAG 修改好友备注失败: ${it.message}", it) }
    }

    private fun splitSavedNames(value: String): List<String> =
        value.split(";;;", "|", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun formatDate(pattern: String, fallback: String): String {
        val clean = pattern.trim().ifBlank { fallback }
        return runCatching { SimpleDateFormat(clean, Locale.getDefault()).format(Date()) }
            .getOrElse { SimpleDateFormat(fallback, Locale.getDefault()).format(Date()) }
    }

    private fun showToast(context: Context, text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    private fun sleep(ms: Long) {
        if (ms <= 0L) return
        runCatching { Thread.sleep(ms) }
    }

    private fun String.cleanAiText(): String = replace("\u0000", "")
        .replace(Regex("(?i)^null"), "")
        .replace(Regex("(?i)null$"), "")
        .trim()

    private data class MessageContext(
        val talker: String,
        val sender: String,
        val content: String = "",
        val msgId: Long = 0L,
        val group: Boolean = false,
        val atMe: Boolean = false,
        val atAll: Boolean = false,
        val patMe: Boolean = false
    )

    private data class AiMessage(val role: String, val content: String)

    private data class FriendAutomationKeys(
        val labelNewFriendEnable: String,
        val labelDateEnable: String,
        val labelDateFormat: String,
        val labelExistingEnable: String,
        val labelSelectedNames: String,
        val remarkNewFriendEnable: String,
        val remarkNicknameSuffixEnable: String,
        val remarkDateEnable: String,
        val remarkDateFormat: String,
        val remarkCustomEnable: String,
        val remarkCustomText: String
    )

    private val autoAcceptAutomationKeys = FriendAutomationKeys(
        AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_NEW_FRIEND_ENABLE,
        AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_DATE_ENABLE,
        AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_DATE_FORMAT,
        AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_EXISTING_ENABLE,
        AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_SELECTED_NAMES,
        AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_NEW_FRIEND_ENABLE,
        AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_NICKNAME_SUFFIX_ENABLE,
        AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_DATE_ENABLE,
        AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_DATE_FORMAT,
        AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_CUSTOM_ENABLE,
        AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_CUSTOM_TEXT
    )

    private val greetAcceptedAutomationKeys = FriendAutomationKeys(
        AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_NEW_FRIEND_ENABLE,
        AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_DATE_ENABLE,
        AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_DATE_FORMAT,
        AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_EXISTING_ENABLE,
        AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_SELECTED_NAMES,
        AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_NEW_FRIEND_ENABLE,
        AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_NICKNAME_SUFFIX_ENABLE,
        AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_DATE_ENABLE,
        AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_DATE_FORMAT,
        AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_CUSTOM_ENABLE,
        AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_CUSTOM_TEXT
    )
}
