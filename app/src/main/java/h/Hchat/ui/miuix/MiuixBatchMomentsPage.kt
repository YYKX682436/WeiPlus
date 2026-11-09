package h.Hchat.ui.miuix

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.widget.ImageView
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.key
import androidx.compose.ui.viewinterop.AndroidView
import h.Hchat.hooks.api.sns.SnsHistoryPage
import h.Hchat.hooks.api.sns.SnsHistorySession
import h.Hchat.hooks.api.sns.SnsLikeState
import h.Hchat.utils.HLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.window.WindowDialog
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.model.WeChatSnsPost
import h.Hchat.hooks.items.batch.BatchMomentContentType
import h.Hchat.hooks.items.batch.BatchMomentFilter
import h.Hchat.hooks.items.batch.BatchMomentItem
import h.Hchat.hooks.items.batch.BatchMomentsSelection
import h.Hchat.hooks.items.batch.BatchOperation
import h.Hchat.hooks.items.batch.BatchTaskManager
import h.Hchat.hooks.items.batch.BatchTaskRequest
import h.Hchat.hooks.items.batch.BatchTaskStatus
import h.Hchat.hooks.items.batch.BatchTaskSubmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun BatchMomentsPickerPage(
    accountIdentity: String,
    manager: BatchTaskManager,
    friend: ContactOption?,
    onBack: () -> Unit,
    onSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val forLike = friend != null
    val operation = if (forLike) BatchOperation.LIKE_FRIEND_MOMENTS else BatchOperation.DELETE_OWN_MOMENTS
    val action = if (forLike) "点赞" else "删除"
    val taskSnapshot by manager.snapshot.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    var reload by remember { mutableStateOf(0) }
    var posts by remember(accountIdentity, reload) { mutableStateOf<List<BatchMomentItem>>(emptyList()) }
    var selectedIds by remember(accountIdentity, reload) { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf(BatchMomentFilter(timeZoneId = TimeZone.getDefault().id)) }
    var run by remember { mutableStateOf(0) }
    val stop = remember(accountIdentity, reload, run) { AtomicBoolean(false) }
    var stopped by remember(accountIdentity, reload, run) { mutableStateOf(false) }
    var session by remember(accountIdentity, reload) { mutableStateOf<SnsHistorySession?>(null) }
    var loading by remember(accountIdentity, reload, run) { mutableStateOf(true) }
    var complete by remember(accountIdentity, reload) { mutableStateOf(false) }
    var received by remember(accountIdentity, reload) { mutableStateOf(0) }
    var pending by remember(accountIdentity, reload) { mutableStateOf(0) }
    var unreadable by remember(accountIdentity, reload) { mutableStateOf(0) }
    var error by remember(accountIdentity, reload) { mutableStateOf("") }
    var accessLimited by remember(accountIdentity, reload) { mutableStateOf(false) }
    var pendingAction by remember(accountIdentity, reload) { mutableStateOf<List<BatchMomentItem>?>(null) }
    var preview by remember(accountIdentity, reload) { mutableStateOf<BatchMomentItem?>(null) }
    val ownerUserName = session?.owner.orEmpty()

    fun applyPage(page: SnsHistoryPage) {
        if (page.accountChanged) {
            posts = emptyList()
            selectedIds = emptySet()
            pendingAction = null
            preview = null
            complete = false
        }
        val converted = page.posts.mapNotNull { post ->
            momentFromPost(post, forLike, page.likeStates[post.getSnsId()] ?: SnsLikeState.UNKNOWN)
        }
        if (converted.isNotEmpty()) posts = (posts + converted).distinctBy(BatchMomentItem::snsId)
        received = page.received
        pending = page.pending
        unreadable = page.unreadable
        if (page.error.isNotEmpty()) error = page.error
        accessLimited = accessLimited || page.accessLimited
        complete = complete || page.complete
    }

    LaunchedEffect(accountIdentity, reload, run) {
        val job = coroutineContext[Job]
        val allowed = { job?.isActive == true && !stop.get() }
        val api = WeChatApis.snsApi()
        val current = session ?: withContext(Dispatchers.IO) {
            if (friend == null) api?.newOwnSnsHistorySession(accountIdentity)
            else api?.newFriendSnsHistorySession(accountIdentity, friend.id)
        }
        if (current == null || api == null) {
            error = if (forLike) "无法确认当前微信账号、好友关系或朋友圈服务"
                else "无法确认当前微信账号或朋友圈服务"
            loading = false
            return@LaunchedEffect
        }
        session = current
        try {
            while (allowed() && !current.terminal) {
                val page = withContext(Dispatchers.IO) { api.loadSnsHistoryPage(current, allowed) }
                if (!isActive) break
                applyPage(page)
                if (page.error.isNotEmpty() || page.complete) break
                // No evidence that all albums (including pinned posts) are ordered by CreateTime.
                // Date filters apply as pages arrive; only a native terminal response ends history.
            }
            if (allowed() && pending > 0) {
                applyPage(withContext(Dispatchers.IO) { api.finishSnsMapping(current, allowed) })
            }
        } finally {
            if (isActive) loading = false
        }
    }

    val refresh: () -> Unit = {
        stop.set(true)
        reload++
    }

    val filtered = remember(posts, ownerUserName, filter) { BatchMomentsSelection.filter(posts, ownerUserName, filter) }
    val visible = remember(filtered, selectedIds) { BatchMomentsSelection.selectedFirst(filtered, selectedIds) }
    val selectable = remember(visible, forLike) { BatchMomentsSelection.selectable(visible, forLike) }
    val allVisibleSelected = selectable.isNotEmpty() && selectable.all { it.snsId in selectedIds }

    BatchDeleteConfirmDialog(
        show = pendingAction != null,
        message = if (forLike) "将点赞 ${friend.label} 的所选 ${pendingAction?.size ?: 0} 条朋友圈。执行前会复核，已点赞会跳过，结果未知不会重发。"
        else "将通过微信原生删除请求删除所选 ${pendingAction?.size ?: 0} 条自己的朋友圈。提交前会复核所选动态；结果未知的请求不会重复提交。此操作不可恢复。",
        labels = pendingAction.orEmpty().map { item ->
            val summary = BatchMomentsSelection.summary(item.text).ifBlank { "（${momentTypeLabel(item.contentType)}）" }
            "${formatMomentTime(item.createTimeSeconds)} · $summary"
        },
        title = "确认${action}朋友圈",
        confirmText = "确认$action",
        onDismiss = { pendingAction = null },
        onConfirm = {
            val targets = BatchMomentsSelection.selectable(pendingAction.orEmpty(), forLike)
            pendingAction = null
            if (targets.isEmpty()) return@BatchDeleteConfirmDialog
            if (accountIdentity.isBlank() || WeChatApis.account()?.sessionIdentity()?.trim() != accountIdentity) {
                Toast.makeText(context, "微信账号已变化，未提交$action", Toast.LENGTH_SHORT).show()
                return@BatchDeleteConfirmDialog
            }
            when (manager.submit(
                BatchTaskRequest(
                    operation = operation,
                    momentFriend = friend?.id.orEmpty(),
                    targetIds = targets.map(BatchMomentItem::snsId),
                    expectedAccount = accountIdentity,
                    enabled = true,
                    momentTimes = targets.associate { it.snsId to it.createTimeSeconds }
                )
            )) {
                BatchTaskSubmitResult.STARTED -> { stop.set(true); onSubmitted() }
                BatchTaskSubmitResult.BUSY -> Toast.makeText(context, "已有批量任务正在执行", Toast.LENGTH_SHORT).show()
                BatchTaskSubmitResult.UNAVAILABLE -> Toast.makeText(context, "后台任务队列不可用，未启动", Toast.LENGTH_SHORT).show()
                BatchTaskSubmitResult.INVALID -> Toast.makeText(context, "${action}对象无效，未启动", Toast.LENGTH_SHORT).show()
            }
        }
    )

    preview?.let { item ->
        WindowDialog(
            show = true,
            title = if (item.contentType == BatchMomentContentType.VIDEO) "视频封面" else "图片预览",
            onDismissRequest = { preview = null }
        ) {
            MomentMediaPreview(accountIdentity, item, !forLike, Modifier.fillMaxWidth().height(300.dp), null)
            ActionRow("关闭预览", "") { preview = null }
        }
    }

    PageScaffold(
        title = if (forLike) "点赞好友朋友圈" else "删除自己的朋友圈",
        largeTitle = friend?.label ?: "批量删除自己的朋友圈",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        bottomBar = {
            BottomActionBar(
                primaryText = "$action（${selectedIds.size}）",
                onPrimaryClick = {
                    if (selectedIds.isNotEmpty()) {
                        pendingAction = BatchMomentsSelection.selectable(posts, forLike).filter { it.snsId in selectedIds }
                    }
                },
                primaryEnabled = selectedIds.isNotEmpty() && taskSnapshot.status != BatchTaskStatus.RUNNING && taskSnapshot.status != BatchTaskStatus.STOPPING,
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 76.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SmallTitle(text = "筛选") }
            item {
                SettingsCard {
                    MomentDateFilterRow("开始日期", filter.startDate, onDate = { filter = filter.copy(startDate = it) }, onClear = { filter = filter.copy(startDate = "") })
                    InsetDivider()
                    MomentDateFilterRow("结束日期", filter.endDate, onDate = { filter = filter.copy(endDate = it) }, onClear = { filter = filter.copy(endDate = "") })
                    InsetDivider()
                    PopupChoiceRow(
                        title = "内容类型",
                        summary = filter.contentType?.let(::momentTypeLabel) ?: "全部",
                        options = listOf(
                            PopupChoice("全部", "ALL"),
                            PopupChoice("文字", BatchMomentContentType.TEXT.name),
                            PopupChoice("图片", BatchMomentContentType.IMAGE.name),
                            PopupChoice("视频", BatchMomentContentType.VIDEO.name),
                            PopupChoice("其他", BatchMomentContentType.OTHER.name)
                        ),
                        currentValue = filter.contentType?.name ?: "ALL",
                        onValueChanged = { value ->
                            filter = filter.copy(contentType = BatchMomentContentType.entries.firstOrNull { it.name == value })
                        }
                    )
                    InsetDivider()
                    InputRow("搜索文字", "按朋友圈文字内容匹配", filter.query, onValueChange = { filter = filter.copy(query = it) })
                }
            }
            if (!BatchMomentsSelection.isValidDateRange(filter)) {
                item { SettingsCard { InfoRow("日期范围无效", "开始日期必须早于或等于结束日期") } }
            }
            item { SmallTitle(text = "动态 · 已选 ${selectedIds.size}") }
            item {
                SettingsCard {
                    ActionRow(
                        title = if (allVisibleSelected) "取消全选当前筛选结果" else "全选当前筛选结果",
                        summary = if (forLike) "当前筛选 ${visible.size} 条，${selectable.size} 条可选；已加载 ${posts.size} 条"
                            else "当前筛选 ${visible.size} 条；已加载历史 ${posts.size} 条"
                    ) {
                        selectedIds = BatchMomentsSelection.selectAllCurrentFilter(selectedIds, selectable)
                    }
                }
            }
            item {
                SettingsCard {
                    val title = when {
                        accessLimited -> "好友朋友圈可见范围受限"
                        error.isNotEmpty() -> if (forLike) "好友朋友圈读取失败" else "历史读取未完成"
                        stopped && !complete -> "已停止读取"
                        loading -> if (received == 0) "正在读取最新动态" else "正在读取更早内容"
                        complete && forLike && received == 0 -> "暂无朋友圈动态"
                        complete -> "已读到历史末尾"
                        else -> "历史读取未完成"
                    }
                    InfoRow(title, buildString {
                        append("已读取 $received 条")
                        if (pending > 0) append(" · $pending 条内容待同步")
                        if (unreadable > 0) append(" · $unreadable 条内容不可读")
                        if (error.isNotEmpty()) append(" · $error")
                    })
                    when {
                        loading && !stopped -> ActionRow("停止读取", "当前已提交的请求会自然结束") {
                            stop.set(true)
                            stopped = true
                        }
                        !loading && stopped && !complete && error.isEmpty() -> ActionRow("继续读取", "从当前游标继续") { run++ }
                        !loading -> ActionRow("重新读取", "从最新动态开始") { refresh() }
                    }
                }
            }
            if (visible.isEmpty() && !loading && error.isEmpty()) {
                item {
                    SettingsCard {
                        InfoRow("暂无匹配动态", when {
                            complete && received == 0 -> "服务器已确认历史结束，未返回动态"
                            pending + unreadable > 0 -> "部分动态内容暂不可显示"
                            !complete -> "尚未读取全部历史"
                            else -> "没有符合当前筛选条件的动态"
                        })
                    }
                }
            }
            items(visible, key = BatchMomentItem::snsId) { item ->
                BatchMomentCard(accountIdentity, item, !forLike, item.snsId in selectedIds, onPreview = { preview = item }) {
                    selectedIds = if (item.snsId in selectedIds) selectedIds - item.snsId else selectedIds + item.snsId
                }
            }
        }
    }
}

@Composable
private fun MomentDateFilterRow(label: String, value: String, onDate: (String) -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    val current = remember(value) {
        runCatching {
            val parsed = if (value.isBlank()) Date() else SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(value) ?: Date()
            java.util.Calendar.getInstance().apply { time = parsed }
        }.getOrElse { java.util.Calendar.getInstance() }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            DatePickerDialog(context, { _, year, month, day ->
                onDate(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day))
            }, current.get(java.util.Calendar.YEAR), current.get(java.util.Calendar.MONTH), current.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(value.ifBlank { "不限 · 点击选择日期" }, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        if (value.isNotBlank()) Text(
            "清除",
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onClear).padding(8.dp)
        )
    }
}

@Composable
private fun BatchMomentCard(
    account: String, item: BatchMomentItem, isSelf: Boolean, selected: Boolean, onPreview: () -> Unit, onClick: () -> Unit
) {
    val canSelect = isSelf || item.likeState == SnsLikeState.NOT_LIKED
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (item.mediaCount > 0) MomentMediaPreview(account, item, isSelf, Modifier.size(68.dp), onPreview)
            Column(modifier = Modifier.weight(1f).clickable(enabled = canSelect, onClick = onClick)) {
                Text(formatMomentTime(item.createTimeSeconds), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                Text(
                    BatchMomentsSelection.summary(item.text).ifBlank { "（${momentTypeLabel(item.contentType)}）" },
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "${momentTypeLabel(item.contentType)}${if (item.mediaCount > 0) " · ${item.mediaCount} 项媒体" else ""}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (canSelect) {
                Box(Modifier.clickable(onClick = onClick)) { SelectionMark(selected = selected, multiSelect = true) }
            } else Text(
                if (item.likeState == SnsLikeState.LIKED) "已点赞" else "点赞状态未知",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MomentMediaPreview(account: String, item: BatchMomentItem, isSelf: Boolean, modifier: Modifier, onPreview: (() -> Unit)?) {
    val api = WeChatApis.snsApi()
    val video = item.contentType == BatchMomentContentType.VIDEO
    val cover by produceState<Pair<Any, Bitmap?>?>(null, account, item.snsId) {
        value = withContext(Dispatchers.IO) {
            val media = runCatching { api?.snsHistoryCover(account, item.snsId, item.userName, item.createTimeSeconds, isSelf) }.getOrNull()
            media?.let { it to runCatching { api?.cachedOwnSnsCover(account, it) }.getOrNull() }
        }
    }
    val tap = onPreview?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MiuixTheme.colorScheme.secondaryVariant).then(tap),
        contentAlignment = Alignment.Center) {
        Text(momentTypeLabel(item.contentType), color = MiuixTheme.colorScheme.onSecondaryVariant, fontSize = 12.sp)
        val local = cover?.second
        if (local != null) {
            Image(
                bitmap = local.asImageBitmap(),
                contentDescription = if (video) "视频封面" else "朋友圈图片封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = if (onPreview == null) ContentScale.Fit else ContentScale.Crop
            )
            LaunchedEffect(local) {
                HLog.i("sns_r1 phase=cover rendered=true source=cache kind=${if (video) "video" else "image"}")
            }
        } else cover?.first?.let { nativeMedia ->
            key(account, item.snsId, nativeMedia) {
                AndroidView(
                    factory = { context ->
                        MomentCoverView(context, video).apply {
                            scaleType = if (onPreview == null) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                            contentDescription = if (video) "视频封面" else "朋友圈图片封面"
                            unbind = api?.bindOwnSnsCover(account, nativeMedia, this, video)
                            if (onPreview != null) setOnClickListener { onPreview() }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onReset = null,
                    onRelease = { it.unbind?.invoke(); it.unbind = null }
                )
            }
        }
        val badge = if (video) "视频" else if (item.mediaCount > 1) "${item.mediaCount} 张" else ""
        if (badge.isNotEmpty()) Text(badge, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .65f))
                .padding(horizontal = 5.dp, vertical = 2.dp))
    }
}

private class MomentCoverView(context: Context, private val video: Boolean) : ImageView(context) {
    var unbind: (() -> Unit)? = null
    private var logged = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!logged && drawable?.let { it.intrinsicWidth > 2 && it.intrinsicHeight > 2 } == true) {
            logged = true
            HLog.i("sns_r1 phase=cover rendered=true source=loader kind=${if (video) "video" else "image"}")
        }
    }
}

private fun momentFromPost(post: WeChatSnsPost, forLike: Boolean, likeState: SnsLikeState): BatchMomentItem? {
    if (post.isSelf() == forLike) return null
    return BatchMomentItem(
        snsId = post.getSnsId(),
        userName = post.getUserName(),
        createTimeSeconds = post.getCreateTimeSeconds(),
        contentType = BatchMomentsSelection.contentTypeFromNative(post.getType()),
        text = post.getText(),
        mediaCount = post.getMediaList().size,
        likeState = likeState
    )
}

private fun momentTypeLabel(type: BatchMomentContentType): String = when (type) {
    BatchMomentContentType.TEXT -> "文字"
    BatchMomentContentType.IMAGE -> "图片"
    BatchMomentContentType.VIDEO -> "视频"
    BatchMomentContentType.OTHER -> "其他"
}

private fun formatMomentTime(seconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(seconds * 1000L))
