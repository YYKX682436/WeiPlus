package h.Hchat.ui.miuix

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun BatchMomentsPickerPage(
    accountIdentity: String,
    manager: BatchTaskManager,
    onBack: () -> Unit,
    onSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val taskSnapshot by manager.snapshot.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    var posts by remember { mutableStateOf<List<BatchMomentItem>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf(BatchMomentFilter(timeZoneId = TimeZone.getDefault().id)) }
    var loading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var complete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<List<BatchMomentItem>?>(null) }
    var ownerUserName by remember { mutableStateOf("") }

    suspend fun loadPage(restart: Boolean) {
        if (loading) return
        loading = true
        error = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val owner = WeChatApis.account()?.selfWxId()?.trim().orEmpty()
                val page = WeChatApis.snsApi()?.loadOwnSnsHistoryPage(
                    expectedAccount = accountIdentity,
                    restart = restart
                ) ?: throw IllegalStateException("微信朋友圈 API 不可用")
                owner to page
            }
        }
        result.fold(
            onSuccess = { (owner, page) ->
                ownerUserName = owner
                if (!page.success) {
                    error = page.error.ifBlank { "朋友圈读取失败" }
                    hasMore = false
                    complete = false
                } else {
                    val converted = page.posts.mapNotNull(::momentFromPost)
                    if (converted.size != page.posts.size) {
                        error = "服务端动态中存在无法确认作者身份的记录，本页未展示"
                        hasMore = false
                        complete = false
                    } else {
                        posts = (posts + converted).distinctBy(BatchMomentItem::snsId)
                            .sortedByDescending(BatchMomentItem::createTimeSeconds)
                        selectedIds = BatchMomentsSelection.keepKnownSelections(selectedIds, posts)
                        hasMore = page.hasMore
                        complete = page.complete
                    }
                }
            },
            onFailure = { error = it.message ?: "朋友圈读取失败" }
        )
        loading = false
    }

    LaunchedEffect(accountIdentity) {
        posts = emptyList()
        selectedIds = emptySet()
        ownerUserName = ""
        hasMore = false
        complete = false
        error = ""
        filter = BatchMomentFilter(timeZoneId = TimeZone.getDefault().id)
        if (accountIdentity.isBlank()) {
            error = "无法确认当前微信账号，未读取朋友圈"
        } else {
            loadPage(restart = true)
        }
    }

    val filtered = BatchMomentsSelection.filter(posts, ownerUserName, filter)
    val visible = BatchMomentsSelection.selectedFirst(filtered, selectedIds)
    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.snsId in selectedIds }

    BatchDeleteConfirmDialog(
        show = pendingDelete != null,
        message = "将通过微信原生删除请求删除所选 ${pendingDelete?.size ?: 0} 条自己的朋友圈。每条动态都会重新读取完整服务端历史并核对账号、作者和 SNS ID；结果未知的请求不会自动重试。此操作不可恢复。",
        labels = pendingDelete.orEmpty().map { item ->
            val summary = BatchMomentsSelection.summary(item.text).ifBlank { "（${momentTypeLabel(item.contentType)}）" }
            "${formatMomentTime(item.createTimeSeconds)} · $summary"
        },
        title = "确认删除朋友圈",
        confirmText = "确认删除",
        onDismiss = { pendingDelete = null },
        onConfirm = {
            val targets = pendingDelete.orEmpty()
            pendingDelete = null
            if (targets.isEmpty()) return@BatchDeleteConfirmDialog
            if (accountIdentity.isBlank() || WeChatApis.account()?.sessionIdentity()?.trim() != accountIdentity) {
                Toast.makeText(context, "微信账号已变化，未提交删除", Toast.LENGTH_SHORT).show()
                return@BatchDeleteConfirmDialog
            }
            when (manager.submit(
                BatchTaskRequest(
                    operation = BatchOperation.DELETE_OWN_MOMENTS,
                    targetIds = targets.map(BatchMomentItem::snsId),
                    expectedAccount = accountIdentity,
                    enabled = true
                )
            )) {
                BatchTaskSubmitResult.STARTED -> onSubmitted()
                BatchTaskSubmitResult.BUSY -> Toast.makeText(context, "已有批量任务正在执行", Toast.LENGTH_SHORT).show()
                BatchTaskSubmitResult.UNAVAILABLE -> Toast.makeText(context, "后台任务队列不可用，未启动", Toast.LENGTH_SHORT).show()
                BatchTaskSubmitResult.INVALID -> Toast.makeText(context, "删除对象无效，未启动", Toast.LENGTH_SHORT).show()
            }
        }
    )

    PageScaffold(
        title = "删除自己的朋友圈",
        largeTitle = "批量删除自己的朋友圈",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        bottomBar = {
            BottomActionBar(
                primaryText = "删除（${selectedIds.size}）",
                onPrimaryClick = { if (selectedIds.isNotEmpty()) pendingDelete = posts.filter { it.snsId in selectedIds } },
                primaryEnabled = selectedIds.isNotEmpty() && !loading && taskSnapshot.status != BatchTaskStatus.RUNNING && taskSnapshot.status != BatchTaskStatus.STOPPING,
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
                        summary = "当前筛选 ${visible.size} 条；已加载历史 ${posts.size} 条"
                    ) {
                        selectedIds = BatchMomentsSelection.selectAllCurrentFilter(selectedIds, visible)
                    }
                }
            }
            if (loading && posts.isEmpty()) {
                item { SettingsCard { InfoRow("朋友圈读取中", "正在从微信服务器读取当前账号自己的动态") } }
            }
            if (error.isNotBlank()) {
                item {
                    SettingsCard {
                        InfoRow("本页读取失败", error)
                        ActionRow("重新读取首页", "读取失败不会显示为无动态或历史已完成") {
                            if (!loading) coroutineScope.launch { loadPage(restart = true) }
                        }
                    }
                }
            }
            if (visible.isEmpty() && !loading && error.isBlank()) {
                item {
                    SettingsCard {
                        val emptyText = if (complete && posts.isEmpty()) "已收到服务器结束标记，此账号没有朋友圈动态" else if (posts.isEmpty()) "尚未读取到动态；历史范围仍可能未完成" else "没有符合当前筛选条件的动态"
                        InfoRow("暂无匹配动态", emptyText)
                    }
                }
            }
            items(visible, key = BatchMomentItem::snsId) { item ->
                BatchMomentCard(item, item.snsId in selectedIds) {
                    selectedIds = if (item.snsId in selectedIds) selectedIds - item.snsId else selectedIds + item.snsId
                }
            }
            if (loading && posts.isNotEmpty()) {
                item { SettingsCard { InfoRow("正在读取更早动态", "已加载内容仍可查看和筛选") } }
            }
            if (hasMore && !loading) {
                item {
                    SettingsCard {
                        ActionRow("读取更早动态", "已读 ${posts.size} 条；仍有历史范围尚未读取") {
                            if (!loading) coroutineScope.launch { loadPage(restart = false) }
                        }
                    }
                }
            }
            if (complete && error.isBlank()) {
                item { SettingsCard { InfoRow("历史读取完成", "已收到微信服务端结束标记 · ${posts.size} 条动态") } }
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
private fun BatchMomentCard(item: BatchMomentItem, selected: Boolean, onClick: () -> Unit) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MomentMediaPreview(item)
            Column(modifier = Modifier.weight(1f)) {
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
            SelectionMark(selected = selected, multiSelect = true)
        }
    }
}

@Composable
private fun MomentMediaPreview(item: BatchMomentItem) {
    val url = item.previewUrl.takeIf { it.startsWith("https://", ignoreCase = true) }
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = if (url == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 3_000
                    readTimeout = 4_000
                    useCaches = true
                }
                connection.getInputStream().use { input ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(input, null, options)
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "朋友圈媒体预览",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        ) else Text(
            text = if (item.mediaCount > 0) momentTypeLabel(item.contentType) else "文字",
            color = MiuixTheme.colorScheme.onSecondaryVariant,
            fontSize = 12.sp
        )
        if (item.mediaCount > 1) Text(
            "${item.mediaCount}",
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomEnd).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .65f)).padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

private fun momentFromPost(post: WeChatSnsPost): BatchMomentItem? {
    if (!post.isSelf()) return null
    return BatchMomentItem(
        snsId = post.getSnsId(),
        userName = post.getUserName(),
        createTimeSeconds = post.getCreateTimeSeconds(),
        contentType = BatchMomentsSelection.contentTypeFromNative(post.getType()),
        text = post.getText(),
        previewUrl = post.getMediaList().firstOrNull()?.getThumbUrl().orEmpty(),
        mediaCount = post.getMediaList().size
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
