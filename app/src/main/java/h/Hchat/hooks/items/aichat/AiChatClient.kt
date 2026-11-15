package h.Hchat.hooks.items.aichat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

internal data class AiUsage(val input: Long?, val output: Long?, val cached: Long?)
internal data class AiCompletion(val text: String, val usage: AiUsage)
internal data class AiInput(val role: String, val content: Any)
internal class AiRequestException(message: String) : Exception(message)

/** Shared non-streaming client for both the new feature and legacy rule steps. */
internal object AiChatClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun fetchModels(config: AiChatConfig): List<AiModel> {
        val base = config.endpoint()
        if (base.isBlank()) throw AiRequestException("请填写 API 地址")
        val url = base.removeSuffix("/chat/completions").removeSuffix("/models") + "/models"
        val request = request(config, url).get().build()
        val body = execute(request)
        return parseModels(body, config.provider)
    }

    fun complete(config: AiChatConfig, messages: List<AiInput>, maxOutput: Int = 512): AiCompletion {
        if (config.apiKey.isBlank() || config.endpoint().isBlank() || config.model.isBlank()) {
            throw AiRequestException("请先填写 API Key、地址和模型")
        }
        val path = if (config.provider == AiProvider.CUSTOM) config.chatPath.trim().ifBlank { "/chat/completions" }
            else "/chat/completions"
        val url = config.endpoint() + if (path.startsWith('/')) path else "/$path"
        val request = request(config, url).post(requestBody(config, messages, maxOutput).toString().toRequestBody(jsonType)).build()
        val result = runCatching { JSONObject(execute(request)) }.getOrElse { error ->
            if (error is AiRequestException) throw error
            throw AiRequestException("上游响应不是有效 JSON")
        }
        val content = result.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        if (content.isBlank() || content == "null") throw AiRequestException("模型未返回有效回答")
        return AiCompletion(content.trim(), parseUsage(result.optJSONObject("usage")))
    }

    internal fun requestBody(config: AiChatConfig, messages: List<AiInput>, maxOutput: Int): JSONObject = JSONObject().apply {
        put("model", config.model)
        put("stream", false)
        val modelMaximum = config.models.firstOrNull { it.id == config.model }?.maxOutputTokens ?: 0
        val output = if (modelMaximum > 0) minOf(maxOutput, modelMaximum) else maxOutput
        put(if (config.provider == AiProvider.MIMO) "max_completion_tokens" else "max_tokens", output.coerceIn(1, 4096))
        if (config.provider != AiProvider.CUSTOM) put("thinking", JSONObject().put("type", "disabled"))
        put("messages", JSONArray().apply {
            messages.forEach { message -> put(JSONObject().put("role", message.role).put("content", message.content)) }
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

    internal fun parseUsage(usage: JSONObject?): AiUsage {
        fun number(obj: JSONObject?, key: String): Long? = if (obj != null && obj.has(key) && !obj.isNull(key)) obj.optLong(key) else null
        return AiUsage(number(usage, "prompt_tokens"), number(usage, "completion_tokens"),
            number(usage?.optJSONObject("prompt_tokens_details"), "cached_tokens") ?: number(usage, "prompt_cache_hit_tokens"))
    }

    private fun request(config: AiChatConfig, url: String): Request.Builder = try {
        Request.Builder().url(url).header("Authorization", "Bearer ${config.apiKey}").header("Content-Type", "application/json")
    } catch (_: IllegalArgumentException) { throw AiRequestException("API 地址无效") }

    private fun execute(request: Request): String {
        try {
            http.newCall(request).execute().use { response ->
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
                    throw AiRequestException("$kind（HTTP ${response.code}）" + if (detail.isBlank()) "" else ": ${detail.take(200)}")
                }
                return body
            }
        } catch (e: AiRequestException) { throw e }
        catch (_: SocketTimeoutException) { throw AiRequestException("网络超时，请求是否已到达上游未知") }
        catch (_: IOException) { throw AiRequestException("网络连接失败，请求是否已到达上游未知") }
    }
}
