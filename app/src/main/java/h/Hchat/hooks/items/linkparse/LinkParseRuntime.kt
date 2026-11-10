package h.Hchat.hooks.items.linkparse

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.media.WeChatChatImageSender
import h.Hchat.hooks.api.media.WeChatModernVideoSender
import h.Hchat.hooks.api.media.WeChatImageApi
import h.Hchat.hooks.api.media.WeChatEmojiApi
import h.Hchat.hooks.api.message.WeChatMessageChangeApi
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.utils.KavaReflector
import io.github.libxposed.api.XposedInterface
import h.Hchat.utils.HLog
import h.Hchat.preferences.HchatStorage
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

internal class LinkParseRuntime(private val context: FeatureContext) {
    private val host = context.hostContext().applicationContext ?: context.hostContext()
    private val settings = LinkParseSettings(host)
    private val claims = MessageClaims(HchatStorage.preferences(host, HISTORY_PREFS))
    private val client = WorkVideoClient()
    private val imageClient = WorkImageClient()
    private val pending = HashSet<String>()
    private val pendingUploads = ConcurrentHashMap<String, ConcurrentLinkedDeque<PendingUpload>>()
    private val galleryUploads = ConcurrentHashMap<String, GalleryPhotoReceipt>()
    @Volatile private var gifReceipt: GifReceipt? = null
    private val hookGeneration = HookRegistry.get().installationGeneration()
    private var chatImageSender: WeChatChatImageSender? = null
    @Volatile private var lastGifMessage: Pair<String, Long>? = null
    private val nextTaskId = AtomicLong(0)
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(3), { r ->
        Thread(r, "HchatLinkParse").apply { isDaemon = true }
    }, ThreadPoolExecutor.AbortPolicy())
    private val cleaner = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "HchatLinkParseCleanup").apply { isDaemon = true }
    }
    @Volatile private var stopped = false
    private var modern: WeChatModernVideoSender? = null

    private data class VideoMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val rotation: Int,
        val codec: String
    )

    private enum class UploadKind(val label: String) { VIDEO("video"), IMAGE("image") }

    private data class PendingUpload(
        val taskId: Long,
        val kind: UploadKind,
        val enqueuedAt: Long,
        val createdAt: Long,
        val file: File
    )

    private data class GalleryMedia(val cover: DownloadedWorkImage, val live: WeChatChatImageSender.Live?) {
        val files = listOfNotNull(cover.file, live?.path?.let(::File))
        val isGif get() = cover.format == WorkImageFormat.GIF
    }

    private data class GifReceipt(
        val talker: String, val digest: String, val startedAt: Long,
        val messageId: CompletableFuture<Long> = CompletableFuture()
    )

    fun install(track: (Any) -> Unit) {
        cleaner.scheduleWithFixedDelay({
            runCatching {
                cleanupPendingUploads()
                cleanup(File(host.cacheDir, CACHE_DIR))
            }
        }, 0, 30, TimeUnit.MINUTES)
        DexInstallScheduler.schedule("link_parse_message_write", "链接解析消息写入", DexInstallScheduler.Stage.WARMUP) { installMessageWriteHook() }
        val changes = WeChatApis.message().changes()
        if (changes == null) HLog.i("[Hchat:LinkParse] 数据库监听不可用") else {
            runCatching { changes.install() }.onFailure { HLog.i("[Hchat:LinkParse] 数据库监听安装失败") }
            if (!changes.isInstalled) HLog.i("[Hchat:LinkParse] 数据库监听未就绪")
            val subscription = changes.subscribe({
                (it.isInsert() || it.isUpdate()) && (settings.enabled() || pendingUploads.isNotEmpty() || gifReceipt != null)
            }, ::onChange)
            if (subscription == null) HLog.i("[Hchat:LinkParse] 数据库订阅失败") else {
                track(subscription)
                HLog.i("[Hchat:LinkParse] 数据库订阅已注册")
            }
        }
    }

    fun destroy() {
        stopped = true
        DexInstallScheduler.cancel("link_parse_message_write")
        executor.shutdownNow()
        cleaner.shutdownNow()
        synchronized(pending) { pending.clear() }
        pendingUploads.clear()
        galleryUploads.clear()
        gifReceipt = null
    }

    private fun onChange(change: WeChatMessageChangeApi.MessageChange) {
        observeGifInsert(change)
        observeUploadCompletion(change)
        val values = change.databaseChange?.values
        val status = if (values?.containsKey("status") == true) runCatching { values.getAsInteger("status") }.getOrNull() else null
        val message = change.message ?: run {
            if (status == 2) HLog.i("[Hchat:LinkParse] 消息字段不可用")
            return
        }
        if (!LinkParseEntryPolicy.acceptDatabase(change.isInsert, change.isUpdate, message.isOutgoing(), message.status, status)) return
        handleMessage(message, "db_${change.operation()}")
    }

    private fun installMessageWriteHook(): Boolean {
        if (stopped) return false
        val classes = context.dexBridgeHolder().findClassesByStrings(
            "CREATE INDEX IF NOT EXISTS messageSendCreateTimeIndex ON message ( status,isSend,createTime )"
        ).mapNotNull { runCatching { context.hostClassLoader().loadClass(it) }.getOrNull() }.distinct()
        val candidates = classes.flatMap { cls -> cls.declaredMethods.filter { method ->
            val p = method.parameterTypes
            p.size == 3 && p[0] == Long::class.javaPrimitiveType && p[2] == Boolean::class.javaPrimitiveType &&
                method.returnType == Int::class.javaPrimitiveType &&
                KavaReflector.findFieldRecursive(p[1], "field_status") != null &&
                KavaReflector.findFieldRecursive(p[1], "field_isSend") != null
        } }
        if (candidates.size != 1) { HLog.i("[Hchat:LinkParse] 消息写入定位不可用"); return false }
        HookRegistry.get().hookOnce("link_parse:message_write", candidates.single(), XposedInterface.Hooker { chain ->
            LinkParseWriteGate.intercept(chain, { !stopped && settings.enabled() }, { id ->
                WeChatApis.message().store()?.getMessageById(id)
            }, { handleMessage(it, "send_write") }, { HLog.i("[Hchat:LinkParse] 消息写入观察失败") })
        })
        HLog.i("[Hchat:LinkParse] 消息写入观察已安装")
        return true
    }

    private fun handleMessage(message: WeChatMessage, entry: String) {
        val timestamp = LinkParseMessageGate.eligibleTime(message, System.currentTimeMillis()) ?: return
        val group = message.isGroupChat()
        val outgoing = message.isOutgoing()
        val needsAt = group && !outgoing && settings.groupAtMeOnly()
        val self = if (needsAt) {
            message.selfWxId.ifBlank { WeChatApis.account()?.selfWxId().orEmpty() }
        } else ""
        val atMe = message.isDirectlyAtMe(self)
        val content = message.bodyContent()
        HLog.i("[Hchat:LinkParse] 收到数据库文本")
        val match = WorkLinkMatcher.find(content) ?: run {
            HLog.i(
                "[Hchat:LinkParse] 未识别作品链接 chars=${content.length} " +
                    "has_http=${content.contains("http", true)} has_douyin=${content.contains("douyin", true)}"
            )
            return
        }
        if (!settings.allows(match.platform, message.talker, group, outgoing, atMe)) {
            val listed = message.talker in settings.allowedTalkers()
            val reason = when {
                !settings.enabled() || !settings.enabled(match.platform) -> "disabled"
                !listed -> "whitelist"
                self.isBlank() -> "no_self"
                else -> "not_direct_at"
            }
            HLog.i("[Hchat:LinkParse] trigger_rejected reason=$reason entry=$entry type=${message.type} " +
                "isSend=${message.isSend} self_present=${self.isNotBlank()} " +
                "source=${message.msgSourceOrigin} source_chars=${message.msgSource.length} listed=$listed direct_at=$atMe")
            return
        }
        if (needsAt) HLog.i("[Hchat:LinkParse] trigger_allowed entry=$entry " +
            "source=${message.msgSourceOrigin} source_chars=${message.msgSource.length} direct_at=$atMe")
        val key = key(message.talker, message.msgId)
        submit(
            message.talker,
            match,
            group,
            outgoing,
            atMe,
            key,
            timestamp + LinkParseMessageGate.MAX_AGE + LinkParseMessageGate.FUTURE_TOLERANCE,
            SystemClock.elapsedRealtime()
        )
    }

    private fun submit(
        talker: String,
        match: WorkLinkMatcher.Match,
        group: Boolean,
        outgoing: Boolean,
        atMe: Boolean,
        key: String?,
        expiry: Long,
        enqueuedAt: Long
    ) {
        if (key != null) synchronized(pending) {
            if (stopped || !pending.add(key)) return
        }
        try {
            executor.execute {
                try {
                    if (!stopped && (key == null || claims.claim(key, expiry, System.currentTimeMillis()))) {
                        val queueWaitMs = (SystemClock.elapsedRealtime() - enqueuedAt).coerceAtLeast(0L)
                        process(talker, match, group, outgoing, atMe, enqueuedAt, queueWaitMs)
                    }
                } catch (_: Throwable) {
                    HLog.i("[Hchat:LinkParse] 处理异常")
                } finally {
                    if (key != null) synchronized(pending) { pending.remove(key) }
                }
            }
        } catch (_: RejectedExecutionException) {
            if (key != null) synchronized(pending) { pending.remove(key) }
            HLog.i("[Hchat:LinkParse] 队列已满")
        }
    }

    private fun process(
        talker: String,
        match: WorkLinkMatcher.Match,
        group: Boolean,
        outgoing: Boolean,
        atMe: Boolean,
        enqueuedAt: Long,
        queueWaitMs: Long
    ) {
        if (stopped || !settings.allows(match.platform, talker, group, outgoing, atMe)) return
        val dir = File(host.cacheDir, CACHE_DIR)
        if ((!dir.isDirectory && !dir.mkdirs()) || !dir.canWrite()) return
        cleanup(dir)
        if ((dir.listFiles()?.sumOf { it.length().coerceAtLeast(0) } ?: 0L) > MAX_CACHE_BYTES - MAX_VIDEO_BYTES) return

        val resolveStart = SystemClock.elapsedRealtime()
        val resolved = client.resolve(match)
        val resolveMs = (SystemClock.elapsedRealtime() - resolveStart).coerceAtLeast(0L)
        if (resolved == null) {
            HLog.i("[Hchat:LinkParse] 解析失败 platform=${match.platform} queue_ms=$queueWaitMs resolve_ms=$resolveMs")
            return
        }
        when (resolved) {
            is WorkResolution.Video -> processVideo(talker, match, group, outgoing, atMe, enqueuedAt, queueWaitMs, resolveMs, resolved.candidates, dir)
            is WorkResolution.Gallery -> processGallery(talker, match, group, outgoing, atMe, queueWaitMs, resolveMs, resolved.images, dir)
        }
    }

    private fun processVideo(
        talker: String,
        match: WorkLinkMatcher.Match,
        group: Boolean,
        outgoing: Boolean,
        atMe: Boolean,
        enqueuedAt: Long,
        queueWaitMs: Long,
        resolveMs: Long,
        urls: List<String>,
        dir: File
    ) {
        val target = File(dir, "video_${System.currentTimeMillis()}_${System.nanoTime()}.mp4")
        var submitted = false
        var tracked: PendingUpload? = null
        try {
            val downloadStart = SystemClock.elapsedRealtime()
            for (url in urls) {
                if (!stopped && client.download(url, match.platform, target)) break
            }
            val downloadMs = (SystemClock.elapsedRealtime() - downloadStart).coerceAtLeast(0L)
            if (!target.isFile) {
                HLog.i("[Hchat:LinkParse] 下载失败 platform=${match.platform} queue_ms=$queueWaitMs resolve_ms=$resolveMs download_ms=$downloadMs")
                return
            }
            if (stopped || !settings.allows(match.platform, talker, group, outgoing, atMe)) return

            val bytes = target.length()
            val metadata = readVideoMetadata(target)
            val rate = if (downloadMs > 0) bytes.toDouble() / 1024.0 / (downloadMs / 1000.0) else 0.0
            HLog.i(
                "[Hchat:LinkParse] timing media=video platform=${match.platform} queue_ms=$queueWaitMs " +
                    "resolve_ms=$resolveMs download_ms=$downloadMs bytes=$bytes avg_kib_s=${String.format(Locale.US, "%.1f", rate)} " +
                    "width=${metadata.width} height=${metadata.height} rotation=${metadata.rotation} codec=${metadata.codec} duration_ms=${metadata.durationMs}"
            )

            val videos = WeChatApis.media()?.videos() ?: run { HLog.i("[Hchat:LinkParse] 发送接口不可用"); return }
            val digestStart = SystemClock.elapsedRealtime()
            val fileMd5 = md5File(target)
            val digestMs = (SystemClock.elapsedRealtime() - digestStart).coerceAtLeast(0L)
            val taskId = nextTaskId.incrementAndGet()
            tracked = fileMd5?.let { registerUpload(talker, UploadKind.VIDEO, it, taskId, enqueuedAt, target) }
            val submitStart = SystemClock.elapsedRealtime()
            submitted = if (videos.canSendSilently()) videos.send(talker, target.absolutePath) else {
                val sender = modern ?: WeChatModernVideoSender(context.dexBridgeHolder(), videos).also { modern = it }
                sender.send(talker, target.absolutePath)
            }
            val submitMs = (SystemClock.elapsedRealtime() - submitStart).coerceAtLeast(0L)
            HLog.i(
                "[Hchat:LinkParse] timing send_submit media=video platform=${match.platform} " +
                    "submit_ms=$submitMs digest_ms=$digestMs accepted=$submitted bytes=$bytes task=$taskId"
            )
            if (!submitted) {
                tracked?.let(::removeUpload)
                HLog.i("[Hchat:LinkParse] 发送失败")
            }
        } finally {
            // Host sending is asynchronous; accepted files stay until a completion event or age cleanup.
            if (!submitted && target.exists()) target.delete()
        }
    }

    private fun processGallery(
        talker: String,
        match: WorkLinkMatcher.Match,
        group: Boolean,
        outgoing: Boolean,
        atMe: Boolean,
        queueWaitMs: Long,
        resolveMs: Long,
        sources: List<WorkImageSource>,
        dir: File
    ) {
        if (!LinkParseGallerySupport.isVerified(match.platform) ||
            sources.isEmpty() || sources.size > WorkImageExtractor.MAX_IMAGES) return
        val media = WeChatApis.media() ?: return
        val imageApi = media.images()
        val version = WeChatApis.version()?.versionCode() ?: 0L
        val native = if (version == 3220L) nativeGallerySender(imageApi, version) ?: run {
            HLog.i("[Hchat:LinkParse] gallery_async_unavailable")
            return
        } else null
        val items = ArrayList<GalleryMedia>(sources.size)
        val retained = HashSet<File>()
        fun allowed() = !stopped && settings.allows(match.platform, talker, group, outgoing, atMe)
        try {
            var totalBytes = 0L
            val downloadStart = SystemClock.elapsedRealtime()
            val gifLimits = native?.gifLimits()
            for ((index, source) in sources.withIndex()) {
                if (!allowed()) return
                val base = File(dir, "image_${System.currentTimeMillis()}_${System.nanoTime()}_$index")
                val remaining = MAX_GALLERY_BYTES - totalBytes
                val cover = imageClient.download(source, match.platform, base, remaining) { reason ->
                    HLog.i("[Hchat:LinkParse] gallery_download_failed platform=${match.platform} index=${index + 1} reason=$reason")
                } ?: return
                // Own the cover before probing/downloading the paired video so failures clean up both.
                items += GalleryMedia(cover, null)
                val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(cover.file.absolutePath, size)
                if (size.outWidth <= 0 || size.outHeight <= 0) {
                    HLog.i("[Hchat:LinkParse] gallery_invalid_image index=${index + 1}")
                    return
                }
                if (cover.format == WorkImageFormat.GIF &&
                    (gifLimits == null || cover.bytes > gifLimits.bytes ||
                        maxOf(size.outWidth, size.outHeight) > gifLimits.edge)) {
                    HLog.i("[Hchat:LinkParse] gallery_gif_unavailable_or_over_limit index=${index + 1}")
                    return
                }
                val sourceLive = source.live
                if (sourceLive != null) {
                    val live = if (native != null) downloadLive(
                        sourceLive, match.platform, File(base.absolutePath + ".mp4"), remaining - cover.bytes
                    ) else null
                    if (live == null) {
                        HLog.i("[Hchat:LinkParse] gallery_live_invalid_or_unavailable index=${index + 1}")
                        return
                    }
                    items[index] = GalleryMedia(cover, live)
                }
                totalBytes += items[index].files.sumOf(File::length)
            }
            val downloadMs = (SystemClock.elapsedRealtime() - downloadStart).coerceAtLeast(0L)
            HLog.i("[Hchat:LinkParse] timing media=gallery platform=${match.platform} queue_ms=$queueWaitMs " +
                "resolve_ms=$resolveMs download_ms=$downloadMs images=${items.size} bytes=$totalBytes")
            if (!allowed()) return
            if (native == null) {
                // Preserve plain image sending on other host builds; the additional fields are 3220-specific.
                if (imageApi.canSendSilently()) for (item in items) {
                    if (!allowed() || !imageApi.sendOriginal(talker, item.cover.file.absolutePath)) return
                    retained += item.cover.file
                }
                return
            }

            val hasGif = items.any { it.isGif }
            val emoji = media.emojis()
            if (hasGif && (!emoji.canSendLocalPathSilently() || WeChatApis.message().changes()?.isInstalled != true)) {
                HLog.i("[Hchat:LinkParse] gallery_gif_receipt_unavailable")
                return
            }
            val self = WeChatApis.account()?.selfWxId().orEmpty()
            val batchId = UUID.randomUUID().toString()
            val segments = LinkParseGalleryPlan.segments(items.map { it.cover.format }, native.canGroup(talker))
            // Construct and validate every native request before submitting the first member.
            val submissions = items.mapIndexed { index, item ->
                if (item.isGif) null else native.prepare(talker, self, item.cover.file.absolutePath,
                    batchId, index, item.live, segments.first { index in it.indices }.group)
            }
            val gifDigests = items.map { if (it.isGif) md5File(it.cover.file) ?: return else null }
            for ((segmentIndex, segment) in segments.withIndex()) {
                if (!allowed()) return
                val first = segment.indices.first
                if (items[first].isGif) {
                    if (!sendGif(emoji, talker, items[first].cover.file, checkNotNull(gifDigests[first]))) return
                    continue
                }
                val receipts = ArrayList<GalleryPhotoReceipt>()
                for (index in segment.indices) {
                    val item = items[index]
                    val submission = checkNotNull(submissions[index])
                    val receipt = GalleryPhotoReceipt(talker, item.files, SystemClock.elapsedRealtime(), index + 1)
                    check(galleryUploads.putIfAbsent(submission.sendId, receipt) == null)
                    // Once entering the native call, even an exception can be ambiguous. Do not retry.
                    retained.addAll(item.files)
                    val accepted = runCatching { submission.invoke.call() != null }.getOrDefault(false)
                    HLog.i("[Hchat:LinkParse] gallery_submit index=${index + 1}/${items.size} " +
                        "media=${if (item.live == null) "image" else "live"} group_count=${segment.group?.count ?: 0} accepted=$accepted")
                    if (!accepted) return
                    receipts += receipt
                }
                if (segmentIndex != segments.lastIndex && !awaitPhotos(receipts)) {
                    HLog.i("[Hchat:LinkParse] gallery_order_stop image_receipt_missing_or_failed")
                    return
                }
            }
        } finally {
            items.flatMap { it.files }.filterNot { it in retained }.forEach(File::delete)
            cleanupGalleryUploads()
        }
    }

    private fun nativeGallerySender(imageApi: WeChatImageApi, version: Long): WeChatChatImageSender? {
        chatImageSender?.let { return it }
        val sender = WeChatChatImageSender.locate(imageApi, context.dexFinder(), version) ?: return null
        return runCatching {
            HookRegistry.get().withInstallationGeneration(hookGeneration, { !stopped }) {
                sender.observe(LinkParseFeature.ID, { id, talker -> galleryUploads[id]?.talker == talker },
                    ::onNativeImageEvent)
            }
            sender.also { chatImageSender = it }
        }.getOrNull()
    }

    private fun onNativeImageEvent(id: String, localId: Long, result: String?) {
        val receipt = galleryUploads[id] ?: return
        receipt.onEvent(localId, result)
        // Only update state on WeChat's thread; file I/O and diagnostics stay on our cleaner.
        if (result != null) runCatching { cleaner.execute { cleanupGalleryUploads() } }
    }

    private fun downloadLive(
        source: WorkLiveSource, platform: WorkLinkMatcher.Platform, target: File, remaining: Long
    ): WeChatChatImageSender.Live? {
        if (source.coverTimeMs < 0) return null
        for (candidate in source.candidates) {
            if (stopped) return null
            if (!client.download(candidate, platform, target, remaining)) continue
            val duration = videoTrackDurationMs(target)
            if (duration != null && source.coverTimeMs < duration) {
                return WeChatChatImageSender.Live(target.absolutePath, duration, source.coverTimeMs)
            }
            target.delete()
        }
        return null
    }

    private fun videoTrackDurationMs(file: File): Int? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).asSequence().map(extractor::getTrackFormat).firstOrNull {
                it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true &&
                    it.containsKey(MediaFormat.KEY_WIDTH) && it.containsKey(MediaFormat.KEY_HEIGHT) &&
                    it.getInteger(MediaFormat.KEY_WIDTH) > 0 && it.getInteger(MediaFormat.KEY_HEIGHT) > 0
            } ?: return@runCatching null
            val durationMs = if (track.containsKey(MediaFormat.KEY_DURATION)) track.getLong(MediaFormat.KEY_DURATION) / 1000
                else readVideoMetadata(file).durationMs
            durationMs.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
        } finally {
            extractor.release()
        }
    }.getOrNull()

    private fun awaitPhotos(receipts: List<GalleryPhotoReceipt>): Boolean {
        val deadline = SystemClock.elapsedRealtime() + RECEIPT_TIMEOUT_MS
        return try {
            receipts.all { receipt ->
                receipt.localMessage.get((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L), TimeUnit.MILLISECONDS) &&
                    (receipt.finish == null || receipt.finish == "Success")
            } && receipts.all { it.finish == null || it.finish == "Success" }
        } catch (_: TimeoutException) {
            false
        }
    }

    private fun sendGif(emoji: WeChatEmojiApi, talker: String, file: File, digest: String): Boolean {
        val receipt = GifReceipt(talker, digest, System.currentTimeMillis())
        gifReceipt = receipt
        return try {
            val accepted = emoji.sendLocalPath(talker, file.absolutePath, true)
            val localId = if (accepted || receipt.messageId.isDone) {
                try {
                    receipt.messageId.get(RECEIPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) { null }
            } else null
            if (localId != null) lastGifMessage = talker to localId
            HLog.i("[Hchat:LinkParse] gallery_gif_submit accepted=$accepted local_message=${localId != null}")
            localId != null
        } finally {
            gifReceipt = null
        }
    }

    private fun observeGifInsert(change: WeChatMessageChangeApi.MessageChange) {
        val receipt = gifReceipt ?: return
        if (!change.isInsert) return
        val message = change.message ?: return
        if (!message.isOutgoing() || !message.isEmoji() || message.status == 5 || message.msgId <= 0 ||
            message.talker != receipt.talker || message.createTime < receipt.startedAt - 2_000L ||
            lastGifMessage == (message.talker to message.msgId)) return
        if (message.getEmojiMd5().equals(receipt.digest, ignoreCase = true)) {
            receipt.messageId.complete(message.msgId)
        }
    }

    private fun cleanupGalleryUploads() {
        val now = SystemClock.elapsedRealtime()
        for ((id, receipt) in galleryUploads) {
            if (!receipt.canRelease(now, CACHE_TTL) || !galleryUploads.remove(id, receipt)) continue
            receipt.files.forEach(File::delete)
            HLog.i("[Hchat:LinkParse] gallery_image_finish index=${receipt.position} result=${receipt.finish ?: "unobserved_expired"} " +
                "after_submit_ms=${(now - receipt.submittedAt).coerceAtLeast(0L)}")
        }
    }

    private fun readVideoMetadata(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            VideoMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                codec = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty().ifBlank { "unknown" }
            )
        } catch (_: Throwable) {
            VideoMetadata(0L, 0, 0, 0, "unknown")
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun registerUpload(
        talker: String,
        kind: UploadKind,
        fileMd5: String,
        taskId: Long,
        enqueuedAt: Long,
        file: File
    ): PendingUpload? {
        val digest = fileMd5.lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-f0-9]{32}")) } ?: return null
        val key = uploadKey(talker, kind, digest)
        val upload = PendingUpload(taskId, kind, enqueuedAt, SystemClock.elapsedRealtime(), file)
        pendingUploads.computeIfAbsent(key) { ConcurrentLinkedDeque() }.addLast(upload)
        return upload
    }

    private fun removeUpload(upload: PendingUpload) {
        for ((key, queue) in pendingUploads.entries) {
            queue.remove(upload)
            if (queue.isEmpty()) pendingUploads.remove(key, queue)
        }
    }

    private fun observeUploadCompletion(change: WeChatMessageChangeApi.MessageChange) {
        if (!change.isUpdate()) return
        val message = change.message ?: return
        if (!message.isOutgoing()) return
        val statusFromChange = change.databaseChange?.values?.let { values ->
            if (values.containsKey("status")) runCatching { values.getAsInteger("status") }.getOrNull() else null
        }
        val status = statusFromChange ?: message.status
        if (status != 2 && status != 5) return
        val kind: UploadKind
        val digests: List<String>
        when {
            message.isVideo() || message.type == 62 -> {
                kind = UploadKind.VIDEO
                val video = message.getVideoMsg() ?: return
                digests = listOf(video.md5, video.newMd5)
            }
            message.isImage() -> {
                kind = UploadKind.IMAGE
                digests = listOf(message.getImageMsg()?.md5.orEmpty())
            }
            else -> return
        }
        val upload = digests.asSequence()
            .filter { it.isNotBlank() }
            .map { uploadKey(message.talker, kind, it.lowercase(Locale.ROOT)) }
            .mapNotNull { key ->
                val queue = pendingUploads[key] ?: return@mapNotNull null
                val first = queue.pollFirst() ?: return@mapNotNull null
                if (queue.isEmpty()) pendingUploads.remove(key, queue)
                first
            }
            .firstOrNull() ?: return
        val result = if (status == 2) "success" else "failed"
        val elapsed = (SystemClock.elapsedRealtime() - upload.createdAt).coerceAtLeast(0L)
        val totalElapsed = (SystemClock.elapsedRealtime() - upload.enqueuedAt).coerceAtLeast(0L)
        HLog.i(
            "[Hchat:LinkParse] timing send_complete media=${kind.label} result=$result " +
                "after_submit_ms=$elapsed total_ms=$totalElapsed task=${upload.taskId}"
        )
        if (status == 5 && upload.file.exists()) upload.file.delete()
    }

    private fun cleanupPendingUploads() {
        cleanupGalleryUploads()
        val now = SystemClock.elapsedRealtime()
        for ((key, queue) in pendingUploads.entries) {
            queue.removeIf { now - it.createdAt > CACHE_TTL }
            if (queue.isEmpty()) pendingUploads.remove(key, queue)
        }
    }

    private fun cleanup(dir: File) {
        val cutoff = System.currentTimeMillis() - CACHE_TTL
        val retained = galleryUploads.values.flatMapTo(HashSet()) { it.files }
        dir.listFiles()?.forEach { file ->
            if (file !in retained && file.isFile && (file.name.startsWith("video_") || file.name.startsWith("image_")) &&
                file.lastModified() in 1 until cutoff) file.delete()
        }
    }

    private fun uploadKey(talker: String, kind: UploadKind, digest: String): String =
        sha256("${kind.label}\u0000$talker\u0000$digest")

    private fun md5File(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun key(talker: String, id: Long): String = MessageDigest.getInstance("SHA-256")
        .digest("$talker\u0000$id".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        private const val HISTORY_PREFS = "Hchat_link_parse_history"
        private const val CACHE_DIR = "Hchat_link_parse"
        private const val CACHE_TTL = 6 * 60 * 60 * 1000L
        private const val MAX_CACHE_BYTES = 256L * 1024 * 1024
        private const val MAX_VIDEO_BYTES = 50L * 1024 * 1024
        private const val RECEIPT_TIMEOUT_MS = 45_000L
        private const val MAX_GALLERY_BYTES = 40L * 1024 * 1024
    }
}
