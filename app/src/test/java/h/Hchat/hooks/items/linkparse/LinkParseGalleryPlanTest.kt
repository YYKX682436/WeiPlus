package h.Hchat.hooks.items.linkparse

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LinkParseGalleryPlanTest {
    private val image = WorkImageFormat.JPEG
    private val liveCover = WorkImageFormat.WEBP
    private val gif = WorkImageFormat.GIF

    @Test fun groupsOnlyEligibleContiguousRunsAtThreeAndUsesFreshIds() {
        for (count in 1..4) {
            val plan = LinkParseGalleryPlan.segments(List(count) { image }, true).single()
            assertEquals(0 until count, plan.indices)
            assertEquals(count.takeIf { it >= 3 }, plan.group?.count)
        }
        val items = listOf(image, liveCover, liveCover, gif, image, image, gif, image, image, liveCover)
        val segments = LinkParseGalleryPlan.segments(items, true)
        assertEquals(listOf(0..2, 3..3, 4..5, 6..6, 7..9), segments.map { it.indices })
        assertEquals(listOf(3, null, null, null, 3), segments.map { it.group?.count })
        assertNotEquals(segments.first().group?.id, segments.last().group?.id)
        assertNotEquals(segments.first().group?.id, LinkParseGalleryPlan.segments(items, true).first().group?.id)
        assertTrue(LinkParseGalleryPlan.segments(items, false).all { it.group == null })
        assertTrue(LinkParseGalleryPlan.segments(listOf(image, gif, image, image), true).all { it.group == null })
    }

    @Test fun liveRetainsCoverAndVideoUntilFinishOrCacheExpiry() {
        val files = listOf(File("cover.jpg"), File("video.mp4"))
        val receipt = GalleryPhotoReceipt("room", files, 100, 1)
        receipt.onEvent(0, null)
        assertFalse(receipt.localMessage.isDone)
        receipt.onEvent(42, null)
        assertTrue(receipt.localMessage.get())
        assertNull(receipt.finish)
        assertFalse(receipt.canRelease(150, 100))
        receipt.onEvent(42, "Success")
        assertEquals("Success", receipt.finish)
        assertTrue(receipt.canRelease(150, 100))
        assertEquals(files, receipt.files)
        val failed = GalleryPhotoReceipt("room", files, 100, 2)
        failed.onEvent(0, "PrepareError")
        assertFalse(failed.localMessage.get())
        assertEquals("PrepareError", failed.finish)
        val unknown = GalleryPhotoReceipt("room", files, 100, 3)
        assertTrue(unknown.canRelease(201, 100))
        assertNull(unknown.finish)
    }
}
