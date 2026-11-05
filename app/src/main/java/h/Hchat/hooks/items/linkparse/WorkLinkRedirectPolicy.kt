package h.Hchat.hooks.items.linkparse

import okhttp3.HttpUrl

/** Keeps manual redirect following inside the selected platform while retaining only concrete work IDs. */
internal object WorkLinkRedirectPolicy {
    private val douyinId = Regex("^/(?:share/)?(?:video|note|slides)/([1-9][0-9]{15,19})/?$")
    private val xhsId = Regex("^/(?:explore|discovery/item)/([a-fA-F0-9]{24})/?$")
    private val kuaishouId = Regex("^/short-video/([A-Za-z0-9]{8,40})/?$")

    fun allows(url: HttpUrl, platform: WorkLinkMatcher.Platform): Boolean {
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        if (url.scheme != "http" && url.scheme != "https") return false
        if (url.scheme == "https" && url.port != 443 || url.scheme == "http" && url.port != 80) return false
        val host = url.host.lowercase()
        return when (platform) {
            WorkLinkMatcher.Platform.DOUYIN -> host in DOUYIN_HOSTS
            WorkLinkMatcher.Platform.XIAOHONGSHU -> host in XHS_HOSTS
            WorkLinkMatcher.Platform.KUAISHOU -> host in KUAISHOU_HOSTS || host.endsWith(".m.chenzhongtech.com")
        }
    }

    fun workId(url: HttpUrl, platform: WorkLinkMatcher.Platform): String? {
        val fromPath = when (platform) {
            WorkLinkMatcher.Platform.DOUYIN -> douyinId.matchEntire(url.encodedPath)?.groupValues?.getOrNull(1)
            WorkLinkMatcher.Platform.XIAOHONGSHU -> xhsId.matchEntire(url.encodedPath)?.groupValues?.getOrNull(1)
            WorkLinkMatcher.Platform.KUAISHOU -> kuaishouId.matchEntire(url.encodedPath)?.groupValues?.getOrNull(1)
        }
        val fromQuery = when (platform) {
            WorkLinkMatcher.Platform.DOUYIN -> listOf("modal_id", "aweme_id", "item_id")
                .firstNotNullOfOrNull(url::queryParameter)?.takeIf { it.matches(Regex("[1-9][0-9]{15,19}")) }
            WorkLinkMatcher.Platform.XIAOHONGSHU -> listOf("noteId", "note_id")
                .firstNotNullOfOrNull(url::queryParameter)?.takeIf { it.matches(Regex("[a-fA-F0-9]{24}")) }
            WorkLinkMatcher.Platform.KUAISHOU -> url.queryParameter("shareObjectId")
                ?.takeIf { it.matches(Regex("[1-9][0-9]{5,24}")) }
        }
        return fromPath ?: fromQuery
    }

    private val DOUYIN_HOSTS = setOf(
        "v.douyin.com", "www.douyin.com", "douyin.com", "m.douyin.com", "iesdouyin.com", "www.iesdouyin.com"
    )
    private val XHS_HOSTS = setOf(
        "xhslink.com", "www.xhslink.com", "xhslink.cn", "www.xhslink.cn", "xiaohongshu.com", "www.xiaohongshu.com"
    )
    private val KUAISHOU_HOSTS = setOf(
        "v.kuaishou.com", "www.kuaishou.com", "kuaishou.com", "m.gifshow.com", "m.chenzhongtech.com"
    )
}
