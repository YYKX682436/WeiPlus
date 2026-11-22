package h.Hchat.hooks.items.aichat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

internal data class AiSearchResult(val data: String, val source: String)

/** One basic Tavily search per batch, with a small private in-memory cache. */
internal object AiChatSearch {
    private const val BASE = "https://api.tavily.com"
    private data class Cached(val time: Long, val result: AiSearchResult)
    private val cache = LinkedHashMap<String, Cached>()
    private val json = "application/json; charset=utf-8".toMediaType()
    private const val SEARCH_PREFIX = "(?:请|麻烦|帮我|帮忙|你帮我|你|可以|能否|能不能|请帮我)"
    private const val SEARCH_NETWORK = "(?:在|到)?(?:网上|网络上|互联网|联网|上网)"
    private const val SEARCH_VERB = "(?:搜索|搜|查询|查找|查|找)(?:一下|一查|一搜|下)?"
    private val hashCommand = Regex("(?i)[#＃](?:搜索|search)")
    private val hashQuery = Regex("(?i)[#＃](?:搜索|search)\\s*([^\\n]+)")
    private val requestedSearchCommand = Regex("(?i)$SEARCH_PREFIX[^\\n]{0,8}?$SEARCH_VERB")
    private val networkSearchCommand = Regex("(?i)$SEARCH_NETWORK\\s*(?:使用|用)?\\s*$SEARCH_VERB")
    private val leadingSearchCommand = Regex("(?i)^\\s*$SEARCH_VERB")
    private val requestedSearch = Regex("(?i)$SEARCH_PREFIX[^\\n]{0,8}?$SEARCH_VERB\\s*(?:[：:，,]\\s*)?(.+)")
    private val networkSearch = Regex("(?i)$SEARCH_NETWORK\\s*(?:使用|用)?\\s*$SEARCH_VERB\\s*(?:[：:，,]\\s*)?(.+)")
    private val leadingSearch = Regex("(?i)^\\s*$SEARCH_VERB\\s*(?:[：:，,]\\s*)?(.+)")

    fun query(text: String, allowKnowledge: Boolean = true): String? {
        val input = text.trim()
        val hash = hashQuery.find(input)?.groupValues?.get(1)?.trim()
            ?.replace(Regex("^(?:一下|一搜|一查)\\s*"), "")
        if (!hash.isNullOrBlank()) return cleanQuery(hash)
        val spoken = listOf(requestedSearch, networkSearch, leadingSearch)
            .firstNotNullOfOrNull { it.find(input)?.groupValues?.get(1)?.trim() }
        if (!spoken.isNullOrBlank()) return cleanQuery(spoken)
        if (!allowKnowledge) return null
        val time = "(?:最新|实时|现在|今日|今天|近期|本周|最近|当下|current|latest|today|right now)"
        val currentSubjects = "(?:新闻|价格|股价|汇率|天气|比赛|比分|政策|公告|事件|票价|price|news|weather|score|exchange rate)"
        val currentInfo = Regex("(?i)(?:$time.{0,30}$currentSubjects|$currentSubjects.{0,30}$time)")
        val knowledgeQuestion = Regex("(?i)(?:知不知道|你知道|知道.{0,20}(?:吗|么|不)|听说过|了解.{0,15}(?:吗|不)|是什么|是啥|什么来头|谁是|是谁|什么型号|哪个型号|什么公司|哪家公司)")
        val knowledgeMatch = knowledgeQuestion.find(input)
        val knowledgeSubject = knowledgeMatch?.let { match ->
            input.removeRange(match.range).replace(Regex("你们|我们|你|我|这个|那个|这|那|吗|呢|呀|什么"), "")
                .filter { it.isLetterOrDigit() || it.code in 0x4e00..0x9fff }
        }.orEmpty()
        return input.take(400).takeIf { currentInfo.containsMatchIn(it) || knowledgeSubject.length >= 2 }
    }

    fun isExplicit(text: String): Boolean = hashCommand.containsMatchIn(text) || requestedSearchCommand.containsMatchIn(text) ||
        networkSearchCommand.containsMatchIn(text) || leadingSearchCommand.containsMatchIn(text)

    private fun cleanQuery(query: String): String = query.trim().trimStart(' ', ':', '：', ',', '，', '。').take(400)

    fun requestBody(query: String): JSONObject = JSONObject().put("query", query).put("search_depth", "basic")
        .put("max_results", 4).put("include_answer", false).put("include_raw_content", false)
        .put("include_usage", true).put("auto_parameters", false)

    /** Refuse to start a search when the account's available plan credits cannot be verified. */
    fun remaining(key: String, base: String = BASE): Long {
        if (key.isBlank()) throw AiRequestException("请先填写 Tavily API Key")
        val request = Request.Builder().url("${base.trimEnd('/')}/usage").header("Authorization", "Bearer $key").get().build()
        val body = try { AiChatClient.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AiRequestException(when (response.code) {
                401, 403 -> "Tavily 密钥无效"; 429 -> "Tavily 请求过于频繁"; else -> "无法确认 Tavily 额度（HTTP ${response.code}）"
            })
            response.body?.string().orEmpty()
        } } catch (e: AiRequestException) { throw e } catch (_: IOException) { throw AiRequestException("无法连接 Tavily 额度接口") }
        val account = runCatching { JSONObject(body).getJSONObject("account") }.getOrNull()
            ?: throw AiRequestException("Tavily 未返回可确认的额度")
        if (!account.has("plan_usage") || account.isNull("plan_usage") || !account.has("plan_limit") || account.isNull("plan_limit")) {
            throw AiRequestException("Tavily 未返回可确认的额度")
        }
        val used = account.optLong("plan_usage", -1)
        val limit = account.optLong("plan_limit", -1)
        if (used < 0 || limit <= 0) throw AiRequestException("Tavily 额度信息无效")
        return (minOf(limit, 1000L) - used).coerceAtLeast(0)
    }

    @Synchronized fun search(account: String, talker: String, key: String, query: String,
                             base: String = BASE, record: (Long?, String) -> Unit): AiSearchResult {
        if (query.isBlank()) throw AiRequestException("搜索关键词为空")
        val id = digest("$account\u0000$talker\u0000$key\u0000$query")
        val now = System.currentTimeMillis()
        cache[id]?.takeIf { now - it.time < 300_000 }?.let { return it.result }
        cache.entries.removeAll { now - it.value.time >= 300_000 }
        if (remaining(key, base) < 1) throw AiRequestException("Tavily 免费额度已用完，本次未联网")
        val request = Request.Builder().url("${base.trimEnd('/')}/search").header("Authorization", "Bearer $key")
            .post(requestBody(query).toString().toRequestBody(json)).build()
        var recorded = false
        fun report(value: Long?, status: String) { if (!recorded) { recorded = true; record(value, status) } }
        try {
            val response = AiChatClient.http.newCall(request).execute()
            val parsed = response.use { r ->
                val text = r.body?.string().orEmpty()
                val obj = runCatching { JSONObject(text) }.getOrNull()
                val credits = obj?.optJSONObject("usage")?.let { u -> if (u.has("credits") && !u.isNull("credits")) u.optLong("credits", -1).takeIf { it >= 0 } else null }
                report(credits, if (r.isSuccessful) "已返回" else "搜索失败")
                if (!r.isSuccessful) throw AiRequestException(when (r.code) {
                    401, 403 -> "Tavily 密钥无效"; 429 -> "Tavily 请求过于频繁"; 432, 433 -> "Tavily 额度已用完，本次未联网"
                    else -> "Tavily 搜索失败（HTTP ${r.code}）"
                })
                obj ?: throw AiRequestException("Tavily 返回的数据格式无效")
            }
            val results = parsed.optJSONArray("results") ?: JSONArray()
            val clean = JSONArray()
            var source = ""
            for (i in 0 until minOf(results.length(), 4)) {
                val item = results.optJSONObject(i) ?: continue
                val uri = runCatching { java.net.URI(item.optString("url")) }.getOrNull()
                if (uri?.scheme !in setOf("http", "https") || uri?.host.isNullOrBlank()) continue
                if (source.isBlank()) source = uri!!.host.take(80)
                clean.put(JSONObject().put("title", item.optString("title").take(160))
                    .put("url", uri.toString().take(600)).put("content", item.optString("content").take(700))
                    .put("published_date", item.optString("published_date").take(40)))
            }
            if (clean.length() == 0) throw AiRequestException("Tavily 没有返回可用搜索结果")
            val result = AiSearchResult(clean.toString(), source)
            cache[id] = Cached(now, result)
            while (cache.size > 32) cache.remove(cache.keys.first())
            return result
        } catch (e: AiRequestException) { report(null, "搜索失败"); throw e }
        catch (_: IOException) { report(null, "结果未知"); throw AiRequestException("Tavily 网络失败，搜索结果和用量未知") }
        catch (e: Exception) { report(null, "结果未知"); throw e }
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
