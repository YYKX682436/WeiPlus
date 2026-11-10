package h.Hchat.hooks.items.linkparse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkImageExtractorTest {
    @Test fun douyinUsesOnlyWatermarkFreeOriginalImageCandidatesInOrder() {
        val id = "7616399587141737704"
        val body = """{"status_code":0,"aweme_detail":{"aweme_id":"$id","aweme_type":68,"images":[
            {"download_url_list":["https://p3-pc-sign.douyinpic.com/a-watermarked.webp"],"url_list":["https://p3-pc-sign.douyinpic.com/a-q75.webp","https://p9-pc-sign.douyinpic.com/a-origin.webp"]},
            {"download_url_list":["https://p9-pc-sign.douyinpic.com/b-watermarked.webp"],"url_list":["https://p3-pc-sign.douyinpic.com/b-q75.webp"]}
        ]}}"""
        assertEquals(
            listOf(
                listOf("https://p3-pc-sign.douyinpic.com/a-q75.webp", "https://p9-pc-sign.douyinpic.com/a-origin.webp"),
                listOf("https://p3-pc-sign.douyinpic.com/b-q75.webp")
            ),
            WorkImageExtractor.douyin(body, id)?.map { it.candidates }
        )
        assertNull(WorkImageExtractor.douyin(body, "7392565659223248165"))
        assertFalse(WorkImageExtractor.allowedDouyinImage("https://douyinpic.com.evil.example/a.webp"))
        assertFalse(WorkImageExtractor.allowedDouyinImage("http://p3-pc-sign.douyinpic.com/a.webp"))
    }

    @Test fun douyinRejectsNonAlbumOversizedAndPartialGalleries() {
        val id = "7616399587141737704"
        val item = """{"download_url_list":["https://p3-pc-sign.douyinpic.com/a-watermarked.webp"],"url_list":["https://p3-pc-sign.douyinpic.com/a.webp"]}"""
        val oversized = (0..WorkImageExtractor.MAX_IMAGES).joinToString(",") { item }
        val tooMany = """{"status_code":0,"aweme_detail":{"aweme_id":"$id","aweme_type":68,"images":[$oversized]}}"""
        assertNull(WorkImageExtractor.douyin(tooMany, id))

        val missing = """{"status_code":0,"aweme_detail":{"aweme_id":"$id","aweme_type":68,"images":[{},$item]}}"""
        assertNull(WorkImageExtractor.douyin(missing, id))
        val video = tooMany.replace("\"aweme_type\":68", "\"aweme_type\":0")
        assertNull(WorkImageExtractor.douyin(video, id))
    }

    @Test fun xiaohongshuUsesOriginalFileIdUrlInOrder() {
        val id = "6ad9e5320000000014012d15"
        val images = (0..14).joinToString(",") { index ->
            """{"fileId":"oss-sg/notes_pre_post/image$index","url":"http://sns-webpic-qc.xhscdn.com/low$index!h5_1080jpg"}"""
        }
        val html = """<script>window.__INITIAL_STATE__={"noteData":{"data":{"noteData":{"noteId":"$id","type":"normal","imageList":[$images]}}}};</script>"""
        val resolved = WorkImageExtractor.xiaohongshu(html, id)!!
        assertEquals(15, resolved.size)
        assertEquals(
            "https://sns-img-qc.xhscdn.com/oss-sg/notes_pre_post/image0?imageView2/2/w/1920/format/jpg",
            resolved.first().candidates.last()
        )
        assertEquals("https://sns-img-qc.xhscdn.com/oss-sg/notes_pre_post/image0", resolved.first().candidates.first())
        assertTrue(resolved.all { image -> image.candidates.all(WorkImageExtractor::allowedXhsImage) })
        assertNull(WorkImageExtractor.xiaohongshu(html, "6a9a9fe70000000011033fd1"))
        assertNull(WorkImageExtractor.xiaohongshu(html.replace("\"type\":\"normal\"", "\"type\":\"video\""), id))
    }

    @Test fun mixedDouyinGalleryKeepsPerImageVideoAndIgnoresTopLevelVideo() {
        val id = "7616399587141737704"
        val body = """{"status_code":0,"aweme_detail":{"aweme_id":"$id","aweme_type":68,
            "video":{"play_addr":{"url_list":["https://v3.douyinvod.com/whole.mp4"]}},
            "images":[{"url_list":["https://p3.douyinpic.com/still"]},
            {"url_list":["https://p3.douyinpic.com/live1"],"video":{"play_addr":{"url_list":["https://v3.douyinvod.com/clip1"]}}},
            {"url_list":["https://p3.douyinpic.com/animation"],"format":"gif"},
            {"url_list":["https://p3.douyinpic.com/live2"],"cover_timestamp_ms":125,
              "video":{"play_addr_h264":{"url_list":["https://v3.douyinvod.com/clip2","https://douyinvod.com.evil.example/file"]}}}
        ]}}"""
        val images = WorkImageExtractor.douyin(body, id)!!
        assertEquals(listOf("still", "live1", "animation", "live2"), images.map { it.candidates.first().substringAfterLast('/') })
        assertEquals(listOf(null, listOf("https://v3.douyinvod.com/clip1"), null, listOf("https://v3.douyinvod.com/clip2")),
            images.map { it.live?.candidates })
        assertEquals(listOf(null, 0L, null, 125L), images.map { it.live?.coverTimeMs })
        assertTrue(images[2].expectsAnimation)
        assertTrue(WorkVideoExtractor.douyinCandidates(body, id).isEmpty())
    }

    @Test fun mixedXhsGalleryKeepsPairedStreamsAndDoesNotDowngradeMissingLive() {
        val id = "6ad9e5320000000014012d15"
        val html = """<script>window.__INITIAL_STATE__={"noteData":{"data":{"noteData":{"noteId":"$id","type":"normal","imageList":[
            {"fileId":"a","livePhoto":true,"stream":{"h264":[{"masterUrl":"http://sns-video.xhscdn.com/a","backupUrls":["https://sns-video.xhscdn.com/a2","https://invalid.example/a"]}]}},
            {"fileId":"b","livePhoto":false},
            {"fileId":"c","mimeType":"image/gif","urlDefault":"https://sns-webpic-qc.xhscdn.com/c"},
            {"fileId":"d","livePhoto":true,"coverTimeStampMs":-20},
            {"fileId":"e","livePhoto":true,"coverTimeStampMs":42,"stream":{"h264":[{"masterUrl":"https://sns-video.xhscdn.com/e"}]}}
        ]}}}};</script>"""
        val images = WorkImageExtractor.xiaohongshu(html, id)!!
        assertEquals(listOf("https://sns-video.xhscdn.com/a", "https://sns-video.xhscdn.com/a2"), images[0].live?.candidates)
        assertNull(images[1].live)
        assertTrue(images[2].expectsAnimation)
        assertFalse(images[2].candidates.any { "format/jpg" in it })
        assertEquals(WorkLiveSource(emptyList(), -20), images[3].live)
        assertEquals(WorkLiveSource(listOf("https://sns-video.xhscdn.com/e"), 42), images[4].live)
    }

    @Test fun xiaohongshuRejectsMissingIdsAndOversizedGalleries() {
        val id = "6ad9e5320000000014012d15"
        val image = """{"fileId":"oss-sg/notes_pre_post/image"}"""
        val oversized = (0..WorkImageExtractor.MAX_IMAGES).joinToString(",") { image }
        val html = """<script>window.__INITIAL_STATE__={"noteData":{"data":{"noteData":{"noteId":"$id","type":"normal","imageList":[$oversized]}}}};</script>"""
        assertNull(WorkImageExtractor.xiaohongshu(html, id))
        assertNull(WorkImageExtractor.xiaohongshu(html.replace("oss-sg/notes_pre_post/image", "../evil"), id))
    }
}
