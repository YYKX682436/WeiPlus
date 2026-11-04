package h.Hchat.hooks.items.linkparse

import java.net.URI
import java.util.Locale

/** Recognizes possible work links; short links still require a verified work redirect. */
internal object WorkLinkMatcher {
    enum class Platform { DOUYIN, XIAOHONGSHU, KUAISHOU }
    data class Match(val platform: Platform, val url: String, val requiresRedirect: Boolean)

    private val urls = Regex("https?://[^\\s\\u0000-\\u001f<>\\\"'（）【】《》，。；：！？、]+", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = ".,;:!?，。；：！？、)]}）】》"
    private val douyinWork = Regex("^/(?:video|note)/[1-9][0-9]{15,19}/?$")
    private val douyinShare = Regex("^/share/(?:video|note)/[1-9][0-9]{15,19}/?$")
    private val shortCode = Regex("^/[A-Za-z0-9_-]{3,80}/?$")
    private val xhsShort = Regex("^/(?:a|o)/[A-Za-z0-9_-]{3,80}/?$")
    private val xhsWork = Regex("^/(?:explore|discovery/item)/[a-fA-F0-9]{24}/?$")
    private val kuaishouWork = Regex("^/short-video/[A-Za-z0-9]{8,40}/?$")
    private val kuaishouShare = Regex("^/fw/(?:photo|long-video)/[A-Za-z0-9]{8,40}/?$")

    fun find(text: String): Match? = urls.findAll(text).firstNotNullOfOrNull { result ->
        parse(result.value.trimEnd(*trailingPunctuation.toCharArray()))
    }

    fun parse(raw: String): Match? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) return null
        if (uri.userInfo != null || (uri.port != -1 && uri.port != 443 && uri.port != 80)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = uri.rawPath ?: return null
        if (path.contains('%') || path.contains("..") || uri.fragment != null) return null
        return when {
            host == "v.douyin.com" && shortCode.matches(path) -> Match(Platform.DOUYIN, raw, true)
            host in setOf("www.douyin.com", "douyin.com") && douyinWork.matches(path) -> Match(Platform.DOUYIN, raw, false)
            host in setOf("www.iesdouyin.com", "iesdouyin.com", "m.douyin.com") && douyinShare.matches(path) -> Match(Platform.DOUYIN, raw, false)
            host in setOf("xhslink.com", "www.xhslink.com", "xhslink.cn", "www.xhslink.cn") && (xhsShort.matches(path) || shortCode.matches(path)) -> Match(Platform.XIAOHONGSHU, raw, true)
            host in setOf("www.xiaohongshu.com", "xiaohongshu.com") && xhsWork.matches(path) -> Match(Platform.XIAOHONGSHU, raw, false)
            host == "v.kuaishou.com" && shortCode.matches(path) -> Match(Platform.KUAISHOU, raw, true)
            host in setOf("www.kuaishou.com", "kuaishou.com") && kuaishouWork.matches(path) -> Match(Platform.KUAISHOU, raw, false)
            (host == "m.gifshow.com" || host == "m.chenzhongtech.com" || host.endsWith(".m.chenzhongtech.com")) && kuaishouShare.matches(path) -> Match(Platform.KUAISHOU, raw, false)
            else -> null
        }
    }
}
