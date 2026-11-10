package h.Hchat.hooks.items.linkparse

import h.Hchat.hooks.api.media.WeChatChatImageSender
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal data class GallerySegment(
    val indices: IntRange,
    val group: WeChatChatImageSender.Group?
)

internal object LinkParseGalleryPlan {
    fun segments(formats: List<WorkImageFormat>, canGroup: Boolean): List<GallerySegment> = buildList {
        var first = 0
        while (first < formats.size) {
            var end = first + 1
            if (formats[first] != WorkImageFormat.GIF) {
                while (end < formats.size && formats[end] != WorkImageFormat.GIF) end++
            }
            val count = end - first
            val group = if (canGroup && count >= 3) {
                WeChatChatImageSender.Group(UUID.randomUUID().toString(), count)
            } else null
            add(GallerySegment(first until end, group))
            first = end
        }
    }
}

/** The native queue may create a message before upload completes; only Finish supplies a final result. */
internal class GalleryPhotoReceipt(
    val talker: String, val files: List<File>, val submittedAt: Long, val position: Int
) {
    val localMessage = CompletableFuture<Boolean>()
    @Volatile var finish: String? = null
        private set

    fun onEvent(localId: Long, result: String?) {
        if (result == null) {
            if (localId > 0) localMessage.complete(true)
        } else {
            finish = result
            localMessage.complete(result == "Success" && localId > 0)
        }
    }

    fun canRelease(now: Long, ttl: Long): Boolean = finish != null || now - submittedAt > ttl
}
