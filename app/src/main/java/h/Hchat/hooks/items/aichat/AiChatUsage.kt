package h.Hchat.hooks.items.aichat

import java.util.Locale

/** Official pay-as-you-go prices checked on 2026-11-16; amounts are estimates, not invoices. */
internal object AiChatUsage {
    data class Totals(val input: Long, val output: Long, val cacheInput: Long, val cacheHit: Long,
                      val requests: Int, val unknown: Int, val search: Long, val searchUnknown: Int,
                      val fetch: Long, val imageSearch: Long, val deepseekRequests: Int, val deepseekToolUnknown: Int)
    data class Estimate(val currency: String, val minimum: Double, val maximum: Double, val incomplete: Boolean)
    private data class Rate(val currency: String, val hit: Double, val miss: Double, val output: Double, val search: Double)

    fun totals(records: List<AiUsageRecord>): Totals {
        var input = 0L; var output = 0L; var eligible = 0L; var hit = 0L; var unknown = 0
        var search = 0L; var searchUnknown = 0
        var fetch = 0L; var imageSearch = 0L; var deepseekRequests = 0; var deepseekToolUnknown = 0
        records.forEach { row ->
            val u = row.usage
            input += u?.input ?: 0; output += u?.output ?: 0
            if (u?.input != null && u.cached != null && u.cached in 0..u.input) { eligible += u.input; hit += u.cached }
            else unknown++
            if (u?.search == null) searchUnknown++ else search += u.search
            if (row.provider == AiProvider.DEEPSEEK) {
                deepseekRequests++
                fetch += u?.fetch ?: 0; imageSearch += u?.imageSearch ?: 0
                if (u?.fetch == null || u.imageSearch == null) deepseekToolUnknown++
            }
        }
        return Totals(input, output, eligible, hit, records.size, unknown, search, searchUnknown,
            fetch, imageSearch, deepseekRequests, deepseekToolUnknown)
    }

    fun estimate(records: List<AiUsageRecord>): List<Estimate> {
        val values = linkedMapOf<String, DoubleArray>()
        records.forEach { row ->
            val rates = rates(row.provider, row.model)
            if (rates.isEmpty()) {
                val value = values.getOrPut("未计价") { doubleArrayOf(0.0, 0.0, 0.0) }; value[2] = 1.0
                return@forEach
            }
            rates.groupBy { it.currency }.forEach { (currency, choices) ->
                val total = values.getOrPut(currency) { doubleArrayOf(0.0, 0.0, 0.0) }
                val usage = row.usage
                if (usage?.input == null || usage.output == null || usage.search == null) total[2] = 1.0
                if (row.provider == AiProvider.DEEPSEEK && (usage?.fetch == null || usage.imageSearch == null)) total[2] = 1.0
                val input = usage?.input ?: 0L; val output = usage?.output ?: 0L
                val cached = usage?.cached?.takeIf { it in 0..input }
                if (cached == null) total[2] = 1.0
                fun cost(rate: Rate, hit: Long): Double =
                    (hit * rate.hit + (input - hit) * rate.miss + output * rate.output) / 1_000_000.0 +
                        ((usage?.search ?: 0L) + (usage?.fetch ?: 0L) + (usage?.imageSearch ?: 0L)) * rate.search / 1000.0
                total[0] += choices.minOf { cost(it, cached ?: input) }
                total[1] += choices.maxOf { cost(it, cached ?: 0L) }
            }
        }
        return values.map { (currency, v) -> Estimate(currency, v[0], v[1], v[2] != 0.0) }
    }

    private fun rates(provider: AiProvider, model: String): List<Rate> = when (provider) {
        AiProvider.DEEPSEEK -> when (model) {
            "deepseek-flash" -> listOf(Rate("USD", .003, .15, .6, .75), Rate("USD", .006, .30, 1.2, .75))
            "deepseek-v4-pro" -> listOf(Rate("USD", .022, .66, 1.98, .75), Rate("USD", .044, 1.32, 3.96, .75))
            else -> emptyList()
        }
        AiProvider.MIMO -> when (model) {
            "mimo-v2.6-flash" -> listOf(Rate("CNY", .02, 1.0, 2.0, 16.0), Rate("USD", .0028, .14, .28, 5.0))
            "mimo-v2.6-pro" -> listOf(Rate("CNY", .025, 3.0, 6.0, 16.0), Rate("USD", .0036, .435, .87, 5.0))
            "mimo-v2.6-pro-ultraspeed" -> listOf(Rate("CNY", .25, 30.0, 60.0, 16.0), Rate("USD", .036, 4.35, 8.7, 5.0))
            else -> emptyList()
        }
        AiProvider.CUSTOM -> emptyList()
    }

    fun description(records: List<AiUsageRecord>): String {
        val t = totals(records)
        val rate = if (t.cacheInput > 0) String.format(Locale.ROOT, "%.1f%%", 100.0 * t.cacheHit / t.cacheInput) else "未知"
        val costs = estimate(records).joinToString("；") { e ->
            if (e.currency == "未计价") "部分模型未计价" else {
                val low = String.format(Locale.ROOT, "%.6f", e.minimum)
                val high = String.format(Locale.ROOT, "%.6f", e.maximum)
                "${e.currency} $low${if (low == high) "" else "–$high"}${if (e.incomplete) "（部分未知）" else ""}"
            }
        }.ifBlank { "暂无费用" }
        val extraTools = if (t.deepseekRequests > 0) "；读网页 ${t.fetch} 次、图片搜索 ${t.imageSearch} 次，${t.deepseekToolUnknown} 次未知" else ""
        return "${t.requests} 次 · 输入 ${t.input} / 输出 ${t.output}\n缓存命中 ${t.cacheHit} / 可统计输入 ${t.cacheInput}（$rate），${t.unknown} 次不完整\n搜索 ${t.search} 次，${t.searchUnknown} 次未知$extraTools\n预估 $costs"
    }
}
