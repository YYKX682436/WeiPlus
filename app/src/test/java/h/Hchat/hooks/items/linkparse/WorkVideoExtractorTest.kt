package h.Hchat.hooks.items.linkparse

import org.junit.Assert.*
import org.junit.Test

class WorkVideoExtractorTest {
    @Test fun douyinRequiresMatchingVideoAndIgnoresGallery() {
        val id = "7392565659223248165"
        val video = """{"status_code":0,"aweme_detail":{"aweme_id":"$id","aweme_type":0,"video":{"play_addr":{"url_list":["https://v9.douyinvod.com/a.mp4"]}}}}"""
        assertEquals("https://v9.douyinvod.com/a.mp4", WorkVideoExtractor.douyin(video, id))
        assertNull(WorkVideoExtractor.douyin(video, "7457473400202726715"))
        assertNull(WorkVideoExtractor.douyin(video.replace("\"aweme_type\":0", "\"aweme_type\":68,\"images\":[{}]"), id))
        assertNull(WorkVideoExtractor.douyin(video.replace("v9.douyinvod.com", "v9.douyinvod.com.evil.example"), id))
    }

    @Test fun xhsChecksTheNoteIdentityAndDoesNotTreatImagesAsVideo() {
        val id = "6a9a9fe70000000011033fd1"
        val html = """<script>window.__INITIAL_STATE__ = {"unused":undefined,"noteData":{"data":{"noteData":{"noteId":"$id","type":"video","title":"undefined is text","video":{"media":{"stream":{"h264":[{"masterUrl":"https://sns-video-v6.xhscdn.com/a.mp4"}]}}}}}}};</script>"""
        assertEquals("https://sns-video-v6.xhscdn.com/a.mp4", WorkVideoExtractor.xiaohongshu(html, id))
        assertEquals("https://sns-video-v6.xhscdn.com/a.mp4", WorkVideoExtractor.xiaohongshu(html.replace("https://sns-video", "http://sns-video"), id))
        assertNull(WorkVideoExtractor.xiaohongshu(html.replace("sns-video-v6.xhscdn.com", "sns-video-v6.xhscdn.com.evil.example"), id))
        assertNull(WorkVideoExtractor.xiaohongshu(html, "6ad9e5320000000014012d15"))
        assertNull(WorkVideoExtractor.xiaohongshu(html.replace("\"type\":\"video\"", "\"type\":\"normal\""), id))
        assertNull(WorkVideoExtractor.xiaohongshu("<html>login</html>", id))
    }

    @Test fun kuaishouRejectsWrongOrAmbiguousWorkAndAtlas() {
        val id = "5239093935709739099"
        val entry = """{"photo":{"photoId":"$id","photoType":"VIDEO","mainMvUrls":[{"url":"https://txmov2.a.kwimgs.com/a.mp4"}]}}"""
        val html = "<script>window.INIT_STATE = {\"sample\":$entry};</script>"
        assertEquals("https://txmov2.a.kwimgs.com/a.mp4", WorkVideoExtractor.kuaishou(html, id))
        assertNull(WorkVideoExtractor.kuaishou(html, "5188428409809700258"))
        assertNull(WorkVideoExtractor.kuaishou(html.replace("VIDEO", "VERTICAL_ATLAS"), id))
        assertNull(WorkVideoExtractor.kuaishou(html.replace("\"sample\":$entry", "\"sample\":$entry,\"other\":$entry"), id))
        assertFalse(WorkVideoExtractor.allowedMedia("http://txmov2.a.kwimgs.com/a.mp4", WorkLinkMatcher.Platform.KUAISHOU))
        assertFalse(WorkVideoExtractor.allowedMedia("https://txmov2.a.kwimgs.com.evil.example/a.mp4", WorkLinkMatcher.Platform.KUAISHOU))
    }
}
