package h.Hchat.hooks.items.linkparse

import org.junit.Assert.*
import org.junit.Test

class WorkLinkMatcherTest {
    @Test fun findsWorkLinksInShareTextAndPreservesParameters() {
        val match = WorkLinkMatcher.find("看看笔记 https://xhslink.com/o/6A6YKVkqAP2?x=1，复制查看")!!
        assertEquals(WorkLinkMatcher.Platform.XIAOHONGSHU, match.platform)
        assertEquals("https://xhslink.com/o/6A6YKVkqAP2?x=1", match.url)
        assertTrue(match.requiresRedirect)
        assertEquals(WorkLinkMatcher.Platform.DOUYIN, WorkLinkMatcher.find("https://www.douyin.com/note/7616399587141737704")?.platform)
        assertEquals(WorkLinkMatcher.Platform.KUAISHOU, WorkLinkMatcher.find("https://v.kuaishou.com/JuuRuozy")?.platform)
    }

    @Test fun ignoresNonWorkAndConfusableHosts() {
        listOf(
            "https://example.com/video/1234567890123456789",
            "https://www.douyin.com/user/1234567890123456789",
            "https://live.kuaishou.com/profile/3x656662gkkep29",
            "https://www.xiaohongshu.com/user/profile/6ad9e5320000000014012d15",
            "https://xhslink.com/m/abcdef",
            "https://douyin.com.evil.example/video/7616399587141737704",
            "https://www.douyin.com@evil.example/video/7616399587141737704",
            "https://www.douyin.com:8443/video/7616399587141737704",
            "https://www.douyin.com/video/%37%36%31%36%33%39%39%35%38%37%31%34%31%37%33%37%37%30%34"
        ).forEach { assertNull(it, WorkLinkMatcher.find(it)) }
    }

    @Test fun recognizesDirectWorksButNotTheirHomepages() {
        assertFalse(WorkLinkMatcher.find("https://www.xiaohongshu.com/discovery/item/6ad9e5320000000014012d15?xsec_token=sample")!!.requiresRedirect)
        assertTrue(WorkLinkMatcher.find("https://v.douyin.com/abc123/")!!.requiresRedirect)
        assertFalse(WorkLinkMatcher.find("https://kphm5nf3.m.chenzhongtech.com/fw/photo/3x656662gkkep29")!!.requiresRedirect)
        assertNull(WorkLinkMatcher.find("https://kphm5nf3.m.chenzhongtech.com.evil.example/fw/photo/3x656662gkkep29"))
        assertNull(WorkLinkMatcher.find("https://www.kuaishou.com/profile/3x656662gkkep29"))
    }
}
