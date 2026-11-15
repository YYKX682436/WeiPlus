package h.Hchat.hooks.items.aichat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import h.Hchat.utils.HLog
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

internal data class AiUsage(val input: Long?, val output: Long?, val cached: Long?,
                            val search: Long? = null, val fetch: Long? = null, val imageSearch: Long? = null,
                            val reasoning: Long? = null)
internal data class AiCompletion(val text: String, val usage: AiUsage, val finishReason: String = "",
                                 val source: String = "", val toolState: String = "", val serverToolCalls: String = "",
                                 val webSucceeded: Boolean = false)
internal data class AiInput(val role: String, val content: Any, val name: String = "", val serverToolCalls: String = "")
internal enum class AiPurpose { CHAT, MEMORY, TEST }
internal class AiRequestException(message: String, val usage: AiUsage? = null) : Exception(message)

/** Shared non-streaming client for both the new feature and legacy rule steps. */
internal object AiChatClient {
    internal val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun fetchModels(config: AiChatConfig): List<AiModel> {
        val base = config.endpoint()
        if (base.isBlank()) throw AiRequestException("请填写 API 地址")
        val url = base.removeSuffix("/chat/completions").removeSuffix("/models") + "/models"
        val request = request(config, url).get().build()
        val body = execute(request, config.provider, false)
        return parseModels(body, config.provider)
    }

    fun complete(config: AiChatConfig, messages: List<AiInput>, maxOutput: Int = 256,
                 purpose: AiPurpose = AiPurpose.CHAT, externalSources: Boolean = false, onUsage: (AiUsage?) -> Unit = {}): AiCompletion {
        if (config.apiKey.isBlank() || config.endpoint().isBlank() || config.model.isBlank()) {
            throw AiRequestException("请先填写 API Key、地址和模型")
        }
        if (purpose == AiPurpose.CHAT && config.webSearch && !supportsWebSearch(config)) throw AiRequestException("当前模型不支持官方联网搜索")
        val path = if (config.provider == AiProvider.CUSTOM) config.chatPath.trim().ifBlank { "/chat/completions" }
            else "/chat/completions"
        val url = config.endpoint() + if (path.startsWith('/')) path else "/$path"
        val request = request(config, url).post(requestBody(config, messages, maxOutput, purpose).toString().toRequestBody(jsonType)).build()
        val safePath = if (config.provider == AiProvider.CUSTOM) "/<custom-path>" else request.url.encodedPath
        val safeEndpoint = redact("${request.url.scheme}://${request.url.host}$safePath", config.apiKey)
        val parts = messages.mapNotNull { it.content as? JSONArray }.flatMap { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("type") } }
        runCatching { HLog.i("[Hchat:AI-Request] provider=${config.provider} config=${config.id} model=${redact(config.model, config.apiKey)} endpoint=$safeEndpoint purpose=$purpose thinking=${if (config.provider == AiProvider.CUSTOM) "upstream-default" else "disabled"} maxOutput=$maxOutput history=${messages.any { it.role == "assistant" }} web=${config.webSearch && purpose == AiPurpose.CHAT} images=${parts.count { it == "image_url" }} videos=${parts.count { it == "video_url" }}") }
        var reported = false
        fun report(usage: AiUsage?) { if (!reported) { reported = true; runCatching { onUsage(usage) } } }
        try {
            val result = try { JSONObject(execute(request, config.provider, config.webSearch && purpose == AiPurpose.CHAT)) } catch (e: AiRequestException) { throw e }
                catch (_: Exception) { throw AiRequestException("上游响应不是有效 JSON") }
            val usage = parseUsage(result.optJSONObject("usage"), config.provider, config.webSearch && purpose == AiPurpose.CHAT)
            report(usage)
            return parseResponse(config, result, purpose, request.url.host, usage, externalSources)
        } catch (e: Exception) {
            report((e as? AiRequestException)?.usage)
            throw e
        }
    }

    internal fun parseResponse(config: AiChatConfig, result: JSONObject, purpose: AiPurpose = AiPurpose.CHAT,
                               host: String = "", usage: AiUsage = parseUsage(result.optJSONObject("usage"), config.provider, config.webSearch),
                               externalSources: Boolean = false): AiCompletion {
        val error = result.optJSONObject("error")
        if (error != null) {
            val hint = if (config.provider == AiProvider.MIMO && config.webSearch && purpose == AiPurpose.CHAT) "；请确认控制台已开通联网服务插件" else ""
            throw AiRequestException("上游返回错误：${redact(error.optString("message"), config.apiKey).take(200)}$hint", usage)
        }
        val choice = result.optJSONArray("choices")?.optJSONObject(0)
        val message = choice?.optJSONObject("message") ?: throw AiRequestException("模型未返回有效回答", usage)
        val raw = message.opt("content")
        val content = when (raw) {
            is String -> raw
            is JSONArray -> (0 until raw.length()).mapNotNull { i -> raw.optJSONObject(i)?.takeIf { it.optString("type") in setOf("text", "output_text") }?.optString("text") }.joinToString("")
            else -> ""
        }
        val finish = choice.optString("finish_reason")
        val reasoningLength = if (message.has("reasoning_content") && !message.isNull("reasoning_content")) message.optString("reasoning_content").length else 0
        runCatching { HLog.i("[Hchat:AI-Response] provider=${config.provider} config=${config.id} model=${redact(config.model, config.apiKey)} host=$host purpose=$purpose finish=$finish contentChars=${content.length} reasoningChars=$reasoningLength reasoningTokens=${usage.reasoning ?: -1}") }
        val answer = AiChatOutput.visibleAnswer(content) ?: throw AiRequestException("模型没有返回可用的最终回答", usage)
        val calls = message.optJSONArray("server_tool_calls")
        val toolState = toolState(calls)
        if (toolState == "failed") throw AiRequestException("联网工具执行失败，请检查服务和模型", usage)
        val source = firstSource(message.optJSONArray("annotations"))
        val toolUsed = externalSources || hasToolResult(calls) || (source.isNotBlank() && (toolState == "completed" || (usage.search ?: 0L) > 0))
        val visible = if (!toolUsed) answer.replace(Regex("(?:我|已|刚刚|刚才)?(?:查到了|搜索到了|检索到了|查阅了)"), "暂时没有查到") else answer
        runCatching { HLog.i("[Hchat:AI-Tool] provider=${config.provider} config=${config.id} model=${redact(config.model, config.apiKey)} state=$toolState hasResult=$toolUsed") }
        return AiCompletion(visible, usage, finish, if (toolUsed) source else "", toolState, calls?.toString().orEmpty(), toolUsed)
    }

    internal fun supportsWebSearch(config: AiChatConfig): Boolean = when (config.provider) {
        AiProvider.DEEPSEEK -> config.model == "deepseek-flash"
        AiProvider.MIMO -> config.model in setOf("mimo-v2.6-flash", "mimo-v2.6-pro", "mimo-v2.6-pro-ultraspeed")
        AiProvider.CUSTOM -> false
    }

    internal fun requestBody(config: AiChatConfig, messages: List<AiInput>, maxOutput: Int, purpose: AiPurpose = AiPurpose.CHAT): JSONObject = JSONObject().apply {
        put("model", config.model)
        put("stream", false)
        val modelMaximum = config.models.firstOrNull { it.id == config.model }?.maxOutputTokens ?: 0
        val output = if (modelMaximum > 0) minOf(maxOutput, modelMaximum) else maxOutput
        put(if (config.provider == AiProvider.MIMO) "max_completion_tokens" else "max_tokens", output.coerceIn(1, 4096))
        if (config.provider != AiProvider.CUSTOM) put("thinking", JSONObject().put("type", "disabled"))
        if (purpose == AiPurpose.CHAT && config.webSearch && supportsWebSearch(config)) {
            put("tools", JSONArray().apply {
                if (config.provider == AiProvider.MIMO) put(JSONObject().put("type", "web_search").put("max_keyword", 1).put("force_search", false).put("limit", 1))
                if (config.provider == AiProvider.DEEPSEEK) {
                    put(JSONObject().put("type", "deepseek_web_search"))
                    if (messages.takeLastWhile { it.role == "user" }.any { it.content.toString().contains(Regex("https?://")) }) put(JSONObject().put("type", "deepseek_read_url"))
                }
            })
        }
        put("messages", JSONArray().apply {
            messages.forEach { message -> put(JSONObject().put("role", message.role).put("content", message.content).apply {
                if (message.role == "user" && message.name.isNotBlank()) put("name", message.name)
                if (config.provider == AiProvider.DEEPSEEK && message.role == "assistant" && message.serverToolCalls.isNotBlank()) {
                    runCatching { put("server_tool_calls", JSONArray(message.serverToolCalls)) }
                }
            }) }
        })
    }

    internal fun parseModels(text: String, provider: AiProvider): List<AiModel> {
        val obj = try { JSONObject(text) } catch (_: Exception) { throw AiRequestException("模型列表格式无效") }
        val array = obj.optJSONArray("data") ?: obj.optJSONArray("models") ?: throw AiRequestException("上游未提供模型列表")
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i)
            val id = (item?.optString("id") ?: array.optString(i)).trim()
            if (id.isBlank() || id == "null" || (provider == AiProvider.MIMO && !isMiMoChatModel(id))) return@mapNotNull null
            val modalities = item?.optJSONArray("input_modalities")
            AiModel(id, if (modalities == null) emptySet() else (0 until modalities.length()).map { modalities.optString(it).lowercase() }.toSet(),
                item?.optInt("context_window") ?: 0, item?.optInt("max_output_tokens") ?: 0)
        }.distinctBy { it.id }.sortedBy { it.id }
    }

    internal fun isMiMoChatModel(id: String): Boolean {
        val name = id.lowercase()
        return name.startsWith("mimo-") && !listOf("asr", "tts", "speech", "voiceclone", "voicedesign", "embed", "rerank", "moderation").any { it in name }
    }

    internal fun parseUsage(usage: JSONObject?, provider: AiProvider = AiProvider.CUSTOM, searchEnabled: Boolean = false): AiUsage {
        fun number(obj: JSONObject?, key: String): Long? = if (obj != null && obj.has(key) && !obj.isNull(key)) obj.optLong(key) else null
        val server = usage?.optJSONObject("server_tool_use")
        val web = usage?.optJSONObject("web_search_usage")
        val search = when (provider) {
            AiProvider.DEEPSEEK -> if (usage == null) null else number(server, "tool_search") ?: 0L
            AiProvider.MIMO -> number(web, "tool_usage") ?: if (!searchEnabled && usage != null) 0L else null
            AiProvider.CUSTOM -> null
        }
        return AiUsage(number(usage, "prompt_tokens"), number(usage, "completion_tokens"),
            number(usage?.optJSONObject("prompt_tokens_details"), "cached_tokens") ?: number(usage, "prompt_cache_hit_tokens"),
            search, if (provider == AiProvider.DEEPSEEK && usage != null) number(server, "tool_fetch") ?: 0L else null,
            if (provider == AiProvider.DEEPSEEK && usage != null) number(server, "tool_image_search") ?: 0L else null,
            number(usage?.optJSONObject("completion_tokens_details"), "reasoning_tokens"))
    }

    private fun toolState(steps: JSONArray?): String {
        if (steps == null || steps.length() == 0) return "not_used"
        var completed = false
        for (i in 0 until steps.length()) {
            val calls = steps.optJSONObject(i)?.optJSONArray("calls") ?: continue
            for (j in 0 until calls.length()) when (calls.optJSONObject(j)?.optString("status")) {
                "failed" -> return "failed"
                "completed" -> completed = true
            }
        }
        return if (completed) "completed" else "unknown"
    }
    private fun hasToolResult(steps: JSONArray?): Boolean {
        if (steps == null) return false
        for (i in 0 until steps.length()) {
            val calls = steps.optJSONObject(i)?.optJSONArray("calls") ?: continue
            for (j in 0 until calls.length()) {
                val call = calls.optJSONObject(j) ?: continue
                if (call.optString("status") != "completed") continue
                if ((call.optJSONArray("sources")?.length() ?: 0) > 0 || call.optJSONObject("page") != null || call.optString("signature").isNotBlank()) return true
            }
        }
        return false
    }
    private fun firstSource(annotations: JSONArray?): String {
        if (annotations == null) return ""
        for (i in 0 until annotations.length()) {
            val item = annotations.optJSONObject(i) ?: continue
            val url = item.optJSONObject("url_citation")?.optString("url") ?: item.optString("url")
            val uri = runCatching { java.net.URI(url) }.getOrNull()
            val host = uri?.takeIf { it.scheme == "https" || it.scheme == "http" }?.host
            if (!host.isNullOrBlank()) return host.take(80)
        }
        return ""
    }
    private fun redact(value: String, secret: String): String = if (secret.isBlank()) value else value.replace(secret, "***")

    private fun request(config: AiChatConfig, url: String): Request.Builder = try {
        Request.Builder().url(url).header("Authorization", "Bearer ${config.apiKey}").header("Content-Type", "application/json")
    } catch (_: IllegalArgumentException) { throw AiRequestException("API 地址无效") }

    private fun execute(request: Request, provider: AiProvider, searchEnabled: Boolean): String {
        try {
            http.newCall(request).execute().use { response ->
                runCatching { HLog.i("[Hchat:AI-HTTP] provider=$provider status=${response.code}") }
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val rawDetail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
                    val secret = request.header("Authorization").orEmpty().removePrefix("Bearer ")
                    val detail = if (secret.isBlank()) rawDetail else rawDetail.replace(secret, "***")
                    val kind = when (response.code) {
                        401, 403 -> "鉴权失败"
                        429 -> "请求受限"
                        in 500..599 -> "上游服务错误"
                        else -> "上游拒绝请求"
                    }
                    val usage = runCatching { JSONObject(body).optJSONObject("usage")?.let { parseUsage(it, provider, searchEnabled) } }.getOrNull()
                    val hint = if (provider == AiProvider.MIMO && searchEnabled && response.code in 400..499) "；请确认控制台已开通联网服务插件" else ""
                    throw AiRequestException("$kind（HTTP ${response.code}）" + (if (detail.isBlank()) "" else ": ${detail.take(200)}") + hint, usage)
                }
                return body
            }
        } catch (e: AiRequestException) { throw e }
        catch (_: SocketTimeoutException) { throw AiRequestException("网络超时，请求是否已到达上游未知") }
        catch (_: IOException) { throw AiRequestException("网络连接失败，请求是否已到达上游未知") }
    }
}
