package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutGlyph
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutGlyphDrawable
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutGlyphs
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutIconPickResult
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutIconPicker
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutIconStore
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutItem
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutRuntime
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutSettings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.items.hidemenu.HideChatMenuSettings
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveRuntime
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveSettings
import h.Hchat.hooks.items.membertitle.MemberTitleStore
import h.Hchat.hooks.items.moments.OriginalMomentsUploadSettings
import h.Hchat.hooks.items.moments.MomentsAutoCommentSettings
import h.Hchat.hooks.items.moments.MomentsAutoLikeSettings
import h.Hchat.hooks.items.moments.MomentsAutoForwardSettings
import h.Hchat.hooks.items.moments.MomentsAutoRefreshSettings
import h.Hchat.hooks.items.moments.MomentsBottomDetailSettings
import h.Hchat.hooks.items.moments.MomentsContactFilterSettings
import h.Hchat.hooks.items.moments.MomentsKeywordBlockSettings
import h.Hchat.hooks.items.moments.MomentsPostNotificationSettings
import h.Hchat.hooks.items.moments.MomentsUploadTailSettings
import h.Hchat.hooks.items.moments.RemoveMomentsAdsSettings
import h.Hchat.hooks.items.moments.SnsAntiRecallSettings
import h.Hchat.hooks.items.momentsfake.MomentsFakeForwardRuntimeRegistry
import h.Hchat.hooks.items.momentsfake.MomentsFakeForwardSettingsProvider
import h.Hchat.hooks.items.momentsfake.MomentsFakeLikeSettingsProvider
import h.Hchat.hooks.items.momentsfake.MomentsFakeLikeCandidateRepository
import h.Hchat.hooks.items.momentsfake.MomentsFakeInteractionRuntimeRegistry
import h.Hchat.hooks.items.momentsfake.MomentsFakeInteractionSettings
import h.Hchat.hooks.items.profileid.ProfileIdSettings
import h.Hchat.hooks.items.quickread.QuickMarkReadSettings
import h.Hchat.hooks.items.settings.SettingsEntrySettings
import h.Hchat.hooks.items.shortvideo.FinderMediaDownloadSettings
import h.Hchat.hooks.items.hchatextra.HchatExtraSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.utils.KeywordReplacementRule
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MomentsAutoLikeMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsAutoLikeSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsAutoLikeRoute>(MomentsAutoLikeRoute.Main) }
    var whitelist by remember { mutableStateOf(parseIds(sp.getString(MomentsAutoLikeSettings.KEY_WHITELIST, "").orEmpty())) }
    var blacklist by remember { mutableStateOf(parseIds(sp.getString(MomentsAutoLikeSettings.KEY_BLACKLIST, "").orEmpty())) }

    SettingsRouteTransition(
        targetState = route,
        label = "MomentsAutoLikeRoute",
        depthOf = { if (it is MomentsAutoLikeRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsAutoLikeRoute.Main -> MomentsAutoLikeMainPage(
                provider = provider,
                sp = sp,
                whitelist = whitelist,
                blacklist = blacklist,
                onBack = onBack,
                onPickTargets = { listMode ->
                    val selected = if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) blacklist else whitelist
                    route = MomentsAutoLikeRoute.ContactPicker(
                        listMode,
                        ContactPickerRequest(
                            title = if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) "选择点赞黑名单" else "选择点赞白名单",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(selected),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is MomentsAutoLikeRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsAutoLikeRoute.Main },
                onConfirm = { picked ->
                    val ids = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    if (current.listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) {
                        blacklist = ids
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_BLACKLIST, formatIds(ids)).apply()
                    } else {
                        whitelist = ids
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_WHITELIST, formatIds(ids)).apply()
                    }
                    route = MomentsAutoLikeRoute.Main
                }
            )
        }
    }
}

@Composable
private fun MomentsAutoLikeMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    whitelist: Set<String>,
    blacklist: Set<String>,
    onBack: () -> Unit,
    onPickTargets: (Int) -> Unit
) {
    var enabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ENABLE, false)) }
    var likeSelf by remember {
        mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_LIKE_SELF, MomentsAutoLikeSettings.DEFAULT_LIKE_SELF))
    }
    var listMode by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_LIST_MODE, MomentsAutoLikeSettings.DEFAULT_LIST_MODE))
    }
    var dailyLikeLimit by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoLikeSettings.KEY_DAILY_LIKE_LIMIT,
                MomentsAutoLikeSettings.DEFAULT_DAILY_LIKE_LIMIT
            ).toString()
        )
    }
    var delayMode by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_DELAY_MODE, MomentsAutoLikeSettings.DEFAULT_DELAY_MODE))
    }
    var fixedDelay by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_FIXED_DELAY_SECONDS, MomentsAutoLikeSettings.DEFAULT_FIXED_DELAY_SECONDS).toString())
    }
    var randomMin by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_RANDOM_MIN_SECONDS, MomentsAutoLikeSettings.DEFAULT_RANDOM_MIN_SECONDS).toString())
    }
    var randomMax by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_RANDOM_MAX_SECONDS, MomentsAutoLikeSettings.DEFAULT_RANDOM_MAX_SECONDS).toString())
    }
    var timeWindow by remember {
        mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_TIME_WINDOW_ENABLE, MomentsAutoLikeSettings.DEFAULT_TIME_WINDOW_ENABLE))
    }
    var startTime by remember {
        mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_START_TIME, MomentsAutoLikeSettings.DEFAULT_START_TIME).orEmpty())
    }
    var endTime by remember {
        mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_END_TIME, MomentsAutoLikeSettings.DEFAULT_END_TIME).orEmpty())
    }
    var maxAge by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_MAX_AGE_HOURS, MomentsAutoLikeSettings.DEFAULT_MAX_AGE_HOURS).toString())
    }
    var allowText by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_TEXT, true)) }
    var allowImage by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_IMAGE, true)) }
    var allowVideo by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_VIDEO, true)) }
    var allowLink by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_LINK, false)) }
    var allowMusic by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_MUSIC, false)) }
    var allowOther by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_OTHER, false)) }
    var allowUnknown by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_UNKNOWN, false)) }
    var keywordsText by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_KEYWORDS_TEXT, "").orEmpty()) }
    var keywordsImageText by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_KEYWORDS_IMAGE_TEXT, "").orEmpty()) }
    var keywordsVideoText by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_KEYWORDS_VIDEO_TEXT, "").orEmpty()) }
    var keywordsCardText by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_KEYWORDS_CARD_TEXT, "").orEmpty()) }
    var keywordText by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_TEXT, true)) }
    var keywordImage by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_IMAGE, true)) }
    var keywordVideo by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_VIDEO, true)) }
    var keywordCard by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_CARD, true)) }
    var logEnabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_LOG_ENABLE, false)) }
    var logs by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_LOGS, "").orEmpty()) }
    val list = if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) blacklist else whitelist
    val listSummary = when {
        listMode == MomentsAutoLikeSettings.LIST_BLACKLIST && list.isEmpty() -> "未排除好友，将匹配全部好友"
        listMode == MomentsAutoLikeSettings.LIST_WHITELIST && list.isEmpty() -> "未选择好友，自动点赞不会执行"
        else -> "已选择 ${list.size} 位好友"
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "自动点赞") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈自动点赞", "按下方规则处理新获取的朋友圈") {
                        enabled = it
                        sp.edit()
                            .putBoolean(MomentsAutoLikeSettings.KEY_ENABLE, it)
                            .putLong(
                                MomentsAutoLikeSettings.KEY_ENABLED_AT_SECONDS,
                                if (it) System.currentTimeMillis() / 1000L else 0L
                            )
                            .apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        SwitchRow(likeSelf, "点赞自己的朋友圈", "自己的朋友圈不受好友名单限制") {
                            likeSelf = it
                            sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_LIKE_SELF, it).apply()
                        }
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "好友范围") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "名单模式",
                            summary = if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) "除黑名单外均可点赞" else "只点赞白名单好友",
                            options = optionItems(
                                "白名单" to MomentsAutoLikeSettings.LIST_WHITELIST,
                                "黑名单" to MomentsAutoLikeSettings.LIST_BLACKLIST
                            ),
                            currentValue = listMode,
                            onValueChanged = {
                                listMode = it
                                sp.edit().putInt(MomentsAutoLikeSettings.KEY_LIST_MODE, it).apply()
                            }
                        )
                        InsetDivider()
                        ActionRow(if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) "黑名单" else "白名单", listSummary) {
                            onPickTargets(listMode)
                        }
                        InsetDivider()
                        NumberInputRow("同一人每天点赞数量", "每天最多点赞同一人的朋友圈条数，0 表示不限制", dailyLikeLimit) {
                            dailyLikeLimit = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit()
                                    .putInt(MomentsAutoLikeSettings.KEY_DAILY_LIKE_LIMIT, value.coerceAtLeast(0))
                                    .apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "执行时间") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "点赞延迟",
                            summary = if (delayMode == MomentsAutoLikeSettings.DELAY_RANDOM) "在范围内随机等待" else "每条等待固定时间",
                            options = optionItems(
                                "固定延迟" to MomentsAutoLikeSettings.DELAY_FIXED,
                                "随机延迟" to MomentsAutoLikeSettings.DELAY_RANDOM
                            ),
                            currentValue = delayMode,
                            onValueChanged = {
                                delayMode = it
                                sp.edit().putInt(MomentsAutoLikeSettings.KEY_DELAY_MODE, it).apply()
                            }
                        )
                        if (delayMode == MomentsAutoLikeSettings.DELAY_FIXED) {
                            InsetDivider()
                            NumberInputRow("等待时间", "单位秒，最少 0 秒", fixedDelay) {
                                fixedDelay = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoLikeSettings.KEY_FIXED_DELAY_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                        } else {
                            InsetDivider()
                            NumberInputRow("最短等待", "单位秒，最少 0 秒", randomMin) {
                                randomMin = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoLikeSettings.KEY_RANDOM_MIN_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                            InsetDivider()
                            NumberInputRow("最长等待", "单位秒，不能小于最短等待", randomMax) {
                                randomMax = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoLikeSettings.KEY_RANDOM_MAX_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                        }
                        InsetDivider()
                        SwitchRow(timeWindow, "限制运行时段", "支持跨零点时段") {
                            timeWindow = it
                            sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_TIME_WINDOW_ENABLE, it).apply()
                        }
                        if (timeWindow) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", startTime) {
                                startTime = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_START_TIME, it).apply()
                            }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", endTime) {
                                endTime = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_END_TIME, it).apply()
                            }
                        }
                        InsetDivider()
                        NumberInputRow("发布时间限制", "仅点赞发布后指定小时内的朋友圈，最少 1 小时", maxAge) {
                            maxAge = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit().putInt(MomentsAutoLikeSettings.KEY_MAX_AGE_HOURS, value.coerceAtLeast(1)).apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容过滤") }
                item {
                    SettingsCard {
                        SwitchRow(allowText, "文字朋友圈", "允许自动点赞") {
                            allowText = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_TEXT, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowImage, "图片/图文朋友圈", "允许自动点赞") {
                            allowImage = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_IMAGE, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowVideo, "视频/视文朋友圈", "允许自动点赞") {
                            allowVideo = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_VIDEO, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowLink, "网页/链接朋友圈", "允许自动点赞") {
                            allowLink = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_LINK, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowMusic, "音乐朋友圈", "允许自动点赞") {
                            allowMusic = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_MUSIC, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowOther, "其他卡片朋友圈", "允许自动点赞") {
                            allowOther = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_OTHER, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowUnknown, "未知类型朋友圈", "允许自动点赞，默认关闭") {
                            allowUnknown = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_UNKNOWN, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(keywordText, "文字朋友圈关键词", "开启后按关键词排除") {
                            keywordText = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_TEXT, it).apply()
                        }
                        if (keywordText) {
                            InsetDivider()
                            InputRow("文字排除关键词", "多个关键词用逗号或换行分隔", keywordsText, minLines = 2) {
                                keywordsText = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordImage, "图文朋友圈关键词", "开启后按关键词排除") {
                            keywordImage = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_IMAGE, it).apply()
                        }
                        if (keywordImage) {
                            InsetDivider()
                            InputRow("图文排除关键词", "多个关键词用逗号或换行分隔", keywordsImageText, minLines = 2) {
                                keywordsImageText = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_IMAGE_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordVideo, "视文朋友圈关键词", "开启后按关键词排除") {
                            keywordVideo = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_VIDEO, it).apply()
                        }
                        if (keywordVideo) {
                            InsetDivider()
                            InputRow("视文排除关键词", "多个关键词用逗号或换行分隔", keywordsVideoText, minLines = 2) {
                                keywordsVideoText = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_VIDEO_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordCard, "链接/卡片朋友圈关键词", "开启后按关键词排除") {
                            keywordCard = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_CARD, it).apply()
                        }
                        if (keywordCard) {
                            InsetDivider()
                            InputRow("链接/卡片排除关键词", "多个关键词用逗号或换行分隔", keywordsCardText, minLines = 2) {
                                keywordsCardText = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_CARD_TEXT, it).apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行记录") }
                item {
                    SettingsCard {
                        SwitchRow(logEnabled, "记录运行日志", "只保留最近 200 条") {
                            logEnabled = it
                            sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_LOG_ENABLE, it).apply()
                        }
                        if (logEnabled) {
                            InsetDivider()
                            ActionRow("刷新日志", if (logs.isBlank()) "暂无记录" else "${logs.lineSequence().count()} 条记录") {
                                logs = sp.getString(MomentsAutoLikeSettings.KEY_LOGS, "").orEmpty()
                            }
                            if (logs.isNotBlank()) {
                                InsetDivider()
                                Text(
                                    text = logs.take(5000),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                                InsetDivider()
                                ActionRow("清空日志", "删除当前自动点赞运行记录") {
                                    sp.edit().remove(MomentsAutoLikeSettings.KEY_LOGS).apply()
                                    logs = ""
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal object MomentsAutoCommentPage {
    @Composable
    operator fun invoke(
        context: Context,
        provider: FeatureSettingsProvider,
        onBack: () -> Unit
    ) {
    val sp = remember { HchatStorage.preferences(context, MomentsAutoCommentSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsAutoCommentRoute>(MomentsAutoCommentRoute.Main) }
    var whitelist by remember { mutableStateOf(parseIds(sp.getString(MomentsAutoCommentSettings.KEY_WHITELIST, "").orEmpty())) }
    var blacklist by remember { mutableStateOf(parseIds(sp.getString(MomentsAutoCommentSettings.KEY_BLACKLIST, "").orEmpty())) }

    SettingsRouteTransition(
        targetState = route,
        label = "MomentsAutoCommentRoute",
        depthOf = { if (it is MomentsAutoCommentRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsAutoCommentRoute.Main -> MainPage(
                provider = provider,
                sp = sp,
                whitelist = whitelist,
                blacklist = blacklist,
                onBack = onBack,
                onPickTargets = { listMode ->
                    val selected = if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) blacklist else whitelist
                    route = MomentsAutoCommentRoute.ContactPicker(
                        listMode,
                        ContactPickerRequest(
                            title = if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) "选择评论黑名单" else "选择评论白名单",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(selected),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is MomentsAutoCommentRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsAutoCommentRoute.Main },
                onConfirm = { picked ->
                    val ids = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    if (current.listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) {
                        blacklist = ids
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_BLACKLIST, formatIds(ids)).apply()
                    } else {
                        whitelist = ids
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_WHITELIST, formatIds(ids)).apply()
                    }
                    route = MomentsAutoCommentRoute.Main
                }
            )
        }
    }
    }

    @Composable
    private fun MainPage(
        provider: FeatureSettingsProvider,
        sp: SharedPreferences,
        whitelist: Set<String>,
        blacklist: Set<String>,
        onBack: () -> Unit,
        onPickTargets: (Int) -> Unit
    ) {
    var enabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ENABLE, false)) }
    var commentContent by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoCommentSettings.KEY_COMMENT_CONTENT,
                MomentsAutoCommentSettings.DEFAULT_COMMENT_CONTENT
            ).orEmpty()
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoCommentSettings.KEY_TIME_FORMAT,
                MomentsAutoCommentSettings.DEFAULT_TIME_FORMAT
            ).orEmpty()
        )
    }
    var commentSelf by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoCommentSettings.KEY_COMMENT_SELF,
                MomentsAutoCommentSettings.DEFAULT_COMMENT_SELF
            )
        )
    }
    var listMode by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_LIST_MODE, MomentsAutoCommentSettings.DEFAULT_LIST_MODE))
    }
    var dailyCommentLimit by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoCommentSettings.KEY_DAILY_COMMENT_LIMIT,
                MomentsAutoCommentSettings.DEFAULT_DAILY_COMMENT_LIMIT
            ).toString()
        )
    }
    var delayMode by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_DELAY_MODE, MomentsAutoCommentSettings.DEFAULT_DELAY_MODE))
    }
    var fixedDelay by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_FIXED_DELAY_SECONDS, MomentsAutoCommentSettings.DEFAULT_FIXED_DELAY_SECONDS).toString())
    }
    var randomMin by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_RANDOM_MIN_SECONDS, MomentsAutoCommentSettings.DEFAULT_RANDOM_MIN_SECONDS).toString())
    }
    var randomMax by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_RANDOM_MAX_SECONDS, MomentsAutoCommentSettings.DEFAULT_RANDOM_MAX_SECONDS).toString())
    }
    var timeWindow by remember {
        mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_TIME_WINDOW_ENABLE, MomentsAutoCommentSettings.DEFAULT_TIME_WINDOW_ENABLE))
    }
    var startTime by remember {
        mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_START_TIME, MomentsAutoCommentSettings.DEFAULT_START_TIME).orEmpty())
    }
    var endTime by remember {
        mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_END_TIME, MomentsAutoCommentSettings.DEFAULT_END_TIME).orEmpty())
    }
    var maxAge by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_MAX_AGE_HOURS, MomentsAutoCommentSettings.DEFAULT_MAX_AGE_HOURS).toString())
    }
    var allowText by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_TEXT, true)) }
    var allowImage by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_IMAGE, true)) }
    var allowVideo by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_VIDEO, true)) }
    var allowLink by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_LINK, false)) }
    var allowMusic by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_MUSIC, false)) }
    var allowOther by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_OTHER, false)) }
    var allowUnknown by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_UNKNOWN, false)) }
    var keywordsText by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_KEYWORDS_TEXT, "").orEmpty()) }
    var keywordsImageText by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_KEYWORDS_IMAGE_TEXT, "").orEmpty()) }
    var keywordsVideoText by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_KEYWORDS_VIDEO_TEXT, "").orEmpty()) }
    var keywordsCardText by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_KEYWORDS_CARD_TEXT, "").orEmpty()) }
    var keywordText by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_TEXT, true)) }
    var keywordImage by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_IMAGE, true)) }
    var keywordVideo by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_VIDEO, true)) }
    var keywordCard by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_CARD, true)) }
    var logEnabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_LOG_ENABLE, false)) }
    var logs by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_LOGS, "").orEmpty()) }
    val list = if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) blacklist else whitelist
    val listSummary = when {
        listMode == MomentsAutoCommentSettings.LIST_BLACKLIST && list.isEmpty() -> "未排除好友，将匹配全部好友"
        listMode == MomentsAutoCommentSettings.LIST_WHITELIST && list.isEmpty() -> "未选择好友，自动评论不会执行"
        else -> "已选择 ${list.size} 位好友"
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "自动评论") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈自动评论", "按下方规则处理新获取的朋友圈") {
                        enabled = it
                        sp.edit()
                            .putBoolean(MomentsAutoCommentSettings.KEY_ENABLE, it)
                            .putLong(
                                MomentsAutoCommentSettings.KEY_ENABLED_AT_SECONDS,
                                if (it) System.currentTimeMillis() / 1000L else 0L
                            )
                            .apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "评论内容",
                            summary = if (commentContent.isBlank()) "需要填写" else "点击下方中文变量插入到光标位置",
                            value = commentContent,
                            variables = momentsAutoCommentTemplateVariables,
                            minLines = 3
                        ) {
                            commentContent = it
                            sp.edit().putString(MomentsAutoCommentSettings.KEY_COMMENT_CONTENT, it).apply()
                        }
                        if (commentContent.contains(MomentsAutoCommentSettings.VAR_TIME)) {
                            InsetDivider()
                            InputRow(
                                title = "时间变量格式",
                                summary = if (MomentsAutoCommentSettings.isValidTimeFormat(timeFormat)) {
                                    "例如 HH:mm:ss 或 yyyy-MM-dd HH:mm:ss"
                                } else {
                                    "格式无效，当前输入不会保存"
                                },
                                value = timeFormat
                            ) {
                                timeFormat = it
                                if (MomentsAutoCommentSettings.isValidTimeFormat(it)) {
                                    sp.edit().putString(
                                        MomentsAutoCommentSettings.KEY_TIME_FORMAT,
                                        MomentsAutoCommentSettings.normalizeTimeFormat(it)
                                    ).apply()
                                }
                            }
                        }
                        InsetDivider()
                        SwitchRow(commentSelf, "评论自己的朋友圈", "自己的朋友圈不受好友名单限制") {
                            commentSelf = it
                            sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_COMMENT_SELF, it).apply()
                        }
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "好友范围") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "名单模式",
                            summary = if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) "除黑名单外均可评论" else "只评论白名单好友",
                            options = optionItems(
                                "白名单" to MomentsAutoCommentSettings.LIST_WHITELIST,
                                "黑名单" to MomentsAutoCommentSettings.LIST_BLACKLIST
                            ),
                            currentValue = listMode,
                            onValueChanged = {
                                listMode = it
                                sp.edit().putInt(MomentsAutoCommentSettings.KEY_LIST_MODE, it).apply()
                            }
                        )
                        InsetDivider()
                        ActionRow(if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) "黑名单" else "白名单", listSummary) {
                            onPickTargets(listMode)
                        }
                        InsetDivider()
                        NumberInputRow("同一人每天评论数量", "每天最多评论同一人的朋友圈条数，0 表示不限制", dailyCommentLimit) {
                            dailyCommentLimit = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit()
                                    .putInt(MomentsAutoCommentSettings.KEY_DAILY_COMMENT_LIMIT, value.coerceAtLeast(0))
                                    .apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "执行时间") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "评论延迟",
                            summary = if (delayMode == MomentsAutoCommentSettings.DELAY_RANDOM) "在范围内随机等待" else "每条等待固定时间",
                            options = optionItems(
                                "固定延迟" to MomentsAutoCommentSettings.DELAY_FIXED,
                                "随机延迟" to MomentsAutoCommentSettings.DELAY_RANDOM
                            ),
                            currentValue = delayMode,
                            onValueChanged = {
                                delayMode = it
                                sp.edit().putInt(MomentsAutoCommentSettings.KEY_DELAY_MODE, it).apply()
                            }
                        )
                        if (delayMode == MomentsAutoCommentSettings.DELAY_FIXED) {
                            InsetDivider()
                            NumberInputRow("等待时间", "单位秒，最少 0 秒", fixedDelay) {
                                fixedDelay = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoCommentSettings.KEY_FIXED_DELAY_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                        } else {
                            InsetDivider()
                            NumberInputRow("最短等待", "单位秒，最少 0 秒", randomMin) {
                                randomMin = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoCommentSettings.KEY_RANDOM_MIN_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                            InsetDivider()
                            NumberInputRow("最长等待", "单位秒，不能小于最短等待", randomMax) {
                                randomMax = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoCommentSettings.KEY_RANDOM_MAX_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                        }
                        InsetDivider()
                        SwitchRow(timeWindow, "限制运行时段", "支持跨零点时段") {
                            timeWindow = it
                            sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_TIME_WINDOW_ENABLE, it).apply()
                        }
                        if (timeWindow) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", startTime) {
                                startTime = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_START_TIME, it).apply()
                            }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", endTime) {
                                endTime = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_END_TIME, it).apply()
                            }
                        }
                        InsetDivider()
                        NumberInputRow("发布时间限制", "仅评论发布后指定小时内的朋友圈，最少 1 小时", maxAge) {
                            maxAge = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit().putInt(MomentsAutoCommentSettings.KEY_MAX_AGE_HOURS, value.coerceAtLeast(1)).apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容过滤") }
                item {
                    SettingsCard {
                        SwitchRow(allowText, "文字朋友圈", "允许自动评论") {
                            allowText = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_TEXT, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowImage, "图片/图文朋友圈", "允许自动评论") {
                            allowImage = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_IMAGE, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowVideo, "视频/视文朋友圈", "允许自动评论") {
                            allowVideo = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_VIDEO, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowLink, "网页/链接朋友圈", "允许自动评论") {
                            allowLink = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_LINK, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowMusic, "音乐朋友圈", "允许自动评论") {
                            allowMusic = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_MUSIC, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowOther, "其他卡片朋友圈", "允许自动评论") {
                            allowOther = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_OTHER, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowUnknown, "未知类型朋友圈", "允许自动评论，默认关闭") {
                            allowUnknown = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_UNKNOWN, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(keywordText, "文字朋友圈关键词", "开启后按关键词排除") {
                            keywordText = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_TEXT, it).apply()
                        }
                        if (keywordText) {
                            InsetDivider()
                            InputRow("文字排除关键词", "多个关键词用逗号或换行分隔", keywordsText, minLines = 2) {
                                keywordsText = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordImage, "图文朋友圈关键词", "开启后按关键词排除") {
                            keywordImage = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_IMAGE, it).apply()
                        }
                        if (keywordImage) {
                            InsetDivider()
                            InputRow("图文排除关键词", "多个关键词用逗号或换行分隔", keywordsImageText, minLines = 2) {
                                keywordsImageText = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_IMAGE_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordVideo, "视文朋友圈关键词", "开启后按关键词排除") {
                            keywordVideo = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_VIDEO, it).apply()
                        }
                        if (keywordVideo) {
                            InsetDivider()
                            InputRow("视文排除关键词", "多个关键词用逗号或换行分隔", keywordsVideoText, minLines = 2) {
                                keywordsVideoText = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_VIDEO_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordCard, "链接/卡片朋友圈关键词", "开启后按关键词排除") {
                            keywordCard = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_CARD, it).apply()
                        }
                        if (keywordCard) {
                            InsetDivider()
                            InputRow("链接/卡片排除关键词", "多个关键词用逗号或换行分隔", keywordsCardText, minLines = 2) {
                                keywordsCardText = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_CARD_TEXT, it).apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行记录") }
                item {
                    SettingsCard {
                        SwitchRow(logEnabled, "记录运行日志", "只保留最近 200 条") {
                            logEnabled = it
                            sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_LOG_ENABLE, it).apply()
                        }
                        if (logEnabled) {
                            InsetDivider()
                            ActionRow("刷新日志", if (logs.isBlank()) "暂无记录" else "${logs.lineSequence().count()} 条记录") {
                                logs = sp.getString(MomentsAutoCommentSettings.KEY_LOGS, "").orEmpty()
                            }
                            if (logs.isNotBlank()) {
                                InsetDivider()
                                Text(
                                    text = logs.take(5000),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                                InsetDivider()
                                ActionRow("清空日志", "删除当前自动评论运行记录") {
                                    sp.edit().remove(MomentsAutoCommentSettings.KEY_LOGS).apply()
                                    logs = ""
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun MomentsAutoForwardMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsAutoForwardSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsAutoForwardRoute>(MomentsAutoForwardRoute.Main) }
    var targets by remember {
        mutableStateOf(
            parseIds(
                sp.getString(
                    MomentsAutoForwardSettings.KEY_TARGETS,
                    MomentsAutoForwardSettings.DEFAULT_TARGETS
                ).orEmpty()
            )
        )
    }
    var replacementRules by remember {
        mutableStateOf(
            MomentsAutoForwardSettings.decodeKeywordReplacements(
                sp.getString(
                    MomentsAutoForwardSettings.KEY_KEYWORD_REPLACEMENTS,
                    MomentsAutoForwardSettings.DEFAULT_KEYWORD_REPLACEMENTS
                )
            )
        )
    }
    val mainListState = rememberLazyListState()

    SettingsRouteTransition(
        targetState = route,
        label = "MomentsAutoForwardRoute",
        depthOf = { if (it is MomentsAutoForwardRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsAutoForwardRoute.Main -> MomentsAutoForwardMainPage(
                provider = provider,
                sp = sp,
                listState = mainListState,
                targetCount = targets.size,
                replacementRuleCount = replacementRules.size,
                onBack = onBack,
                onOpenReplacementRules = { route = MomentsAutoForwardRoute.ReplacementRules },
                onPickTargets = {
                    route = MomentsAutoForwardRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "选择转发好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(targets),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is MomentsAutoForwardRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsAutoForwardRoute.Main },
                onConfirm = { picked ->
                    targets = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    sp.edit().putString(MomentsAutoForwardSettings.KEY_TARGETS, formatIds(targets)).apply()
                    route = MomentsAutoForwardRoute.Main
                }
            )
            MomentsAutoForwardRoute.ReplacementRules -> KeywordReplacementRulesPage(
                context = context,
                initialRules = replacementRules,
                onBack = { route = MomentsAutoForwardRoute.Main },
                onSave = { updated ->
                    replacementRules = updated
                    sp.edit()
                        .putString(
                            MomentsAutoForwardSettings.KEY_KEYWORD_REPLACEMENTS,
                            MomentsAutoForwardSettings.encodeKeywordReplacements(updated)
                        )
                        .apply()
                    Toast.makeText(context, "替换规则已保存", Toast.LENGTH_SHORT).show()
                    route = MomentsAutoForwardRoute.Main
                }
            )
        }
    }
}

@Composable
private fun MomentsAutoForwardMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    listState: LazyListState,
    targetCount: Int,
    replacementRuleCount: Int,
    onBack: () -> Unit,
    onOpenReplacementRules: () -> Unit,
    onPickTargets: () -> Unit
) {
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ENABLE,
                MomentsAutoForwardSettings.DEFAULT_ENABLE
            )
        )
    }
    var allowText by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_TEXT,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_TEXT
            )
        )
    }
    var allowImage by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_IMAGE,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_IMAGE
            )
        )
    }
    var allowVideo by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_VIDEO,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_VIDEO
            )
        )
    }
    var allowLivePhoto by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_LIVE_PHOTO,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_LIVE_PHOTO
            )
        )
    }
    var allowLink by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_LINK,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_LINK
            )
        )
    }
    var allowMusic by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_MUSIC,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_MUSIC
            )
        )
    }
    var allowOther by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_OTHER,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_OTHER
            )
        )
    }
    var allowUnknown by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_UNKNOWN,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_UNKNOWN
            )
        )
    }
    var delayMode by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_DELAY_MODE,
                MomentsAutoForwardSettings.DEFAULT_DELAY_MODE
            )
        )
    }
    var fixedDelay by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_FIXED_DELAY_SECONDS,
                MomentsAutoForwardSettings.DEFAULT_FIXED_DELAY_SECONDS
            ).toString()
        )
    }
    var randomMin by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_RANDOM_MIN_SECONDS,
                MomentsAutoForwardSettings.DEFAULT_RANDOM_MIN_SECONDS
            ).toString()
        )
    }
    var randomMax by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_RANDOM_MAX_SECONDS,
                MomentsAutoForwardSettings.DEFAULT_RANDOM_MAX_SECONDS
            ).toString()
        )
    }
    var dailyLimit by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_DAILY_LIMIT,
                MomentsAutoForwardSettings.DEFAULT_DAILY_LIMIT
            ).toString()
        )
    }
    var includeKeywords by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoForwardSettings.KEY_INCLUDE_KEYWORDS,
                MomentsAutoForwardSettings.DEFAULT_INCLUDE_KEYWORDS
            ).orEmpty()
        )
    }
    var includeKeywordsEnabled by remember {
        mutableStateOf(MomentsAutoForwardSettings.includeKeywordsEnabled(sp))
    }
    var excludeKeywords by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoForwardSettings.KEY_EXCLUDE_KEYWORDS,
                MomentsAutoForwardSettings.DEFAULT_EXCLUDE_KEYWORDS
            ).orEmpty()
        )
    }
    var excludeKeywordsEnabled by remember {
        mutableStateOf(MomentsAutoForwardSettings.excludeKeywordsEnabled(sp))
    }
    var replaceKeywordsEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_REPLACE_KEYWORDS_ENABLE,
                MomentsAutoForwardSettings.DEFAULT_REPLACE_KEYWORDS_ENABLE
            )
        )
    }
    var contentTemplate by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoForwardSettings.KEY_CONTENT_TEMPLATE,
                MomentsAutoForwardSettings.DEFAULT_CONTENT_TEMPLATE
            ).orEmpty()
        )
    }
    var logEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_LOG_ENABLE,
                MomentsAutoForwardSettings.DEFAULT_LOG_ENABLE
            )
        )
    }
    var logs by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoForwardSettings.KEY_LOGS,
                MomentsAutoForwardSettings.DEFAULT_LOGS
            ).orEmpty()
        )
    }
    val templateVariables = remember {
        listOf(
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_CONTENT, "原文"),
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_SENDER, "发布者"),
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_WXID, "发布者ID"),
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_TYPE, "类型"),
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_SNS_ID, "朋友圈ID")
        )
    }
    val targetSummary = if (targetCount == 0) {
        "未选择好友，自动转发不会执行"
    } else {
        "已选择 $targetCount 位好友"
    }
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "自动转发") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈自动转发", "按规则静默转发指定好友的新朋友圈") {
                        enabled = it
                        sp.edit()
                            .putBoolean(MomentsAutoForwardSettings.KEY_ENABLE, it)
                            .putLong(
                                MomentsAutoForwardSettings.KEY_ENABLED_AT_SECONDS,
                                if (it) System.currentTimeMillis() / 1000L else 0L
                            )
                            .apply()
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "转发好友") }
                item {
                    SettingsCard {
                        ActionRow("转发好友", targetSummary, onPickTargets)
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容类型") }
                item {
                    SettingsCard {
                        SwitchRow(allowText, "文字", "允许自动转发") {
                            allowText = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_TEXT, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowImage, "图片/图文", "允许自动转发") {
                            allowImage = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_IMAGE, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowVideo, "视频/视文", "允许自动转发") {
                            allowVideo = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_VIDEO, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowLivePhoto, "实况照片", "允许自动转发") {
                            allowLivePhoto = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_LIVE_PHOTO, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowLink, "网页/链接", "按正文和链接转为文字朋友圈") {
                            allowLink = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_LINK, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowMusic, "音乐", "按正文和链接转为文字朋友圈") {
                            allowMusic = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_MUSIC, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowOther, "其他卡片", "按可解析内容转为文字朋友圈") {
                            allowOther = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_OTHER, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowUnknown, "未知类型", "按可解析内容转发，默认关闭") {
                            allowUnknown = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_UNKNOWN, it).apply()
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "执行方式") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "转发延迟",
                            summary = if (delayMode == MomentsAutoForwardSettings.DELAY_RANDOM) {
                                "在范围内随机等待"
                            } else {
                                "每条等待固定时间"
                            },
                            options = optionItems(
                                "固定延迟" to MomentsAutoForwardSettings.DELAY_FIXED,
                                "随机延迟" to MomentsAutoForwardSettings.DELAY_RANDOM
                            ),
                            currentValue = delayMode,
                            onValueChanged = {
                                delayMode = it
                                sp.edit().putInt(MomentsAutoForwardSettings.KEY_DELAY_MODE, it).apply()
                            }
                        )
                        if (delayMode == MomentsAutoForwardSettings.DELAY_FIXED) {
                            InsetDivider()
                            NumberInputRow("等待时间", "单位秒，最少 0 秒", fixedDelay) {
                                fixedDelay = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit()
                                        .putInt(
                                            MomentsAutoForwardSettings.KEY_FIXED_DELAY_SECONDS,
                                            value.coerceAtLeast(0)
                                        )
                                        .apply()
                                }
                            }
                        } else {
                            InsetDivider()
                            NumberInputRow("最短等待", "单位秒，最少 0 秒", randomMin) {
                                randomMin = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit()
                                        .putInt(
                                            MomentsAutoForwardSettings.KEY_RANDOM_MIN_SECONDS,
                                            value.coerceAtLeast(0)
                                        )
                                        .apply()
                                }
                            }
                            InsetDivider()
                            NumberInputRow("最长等待", "单位秒，不能小于最短等待", randomMax) {
                                randomMax = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit()
                                        .putInt(
                                            MomentsAutoForwardSettings.KEY_RANDOM_MAX_SECONDS,
                                            value.coerceAtLeast(0)
                                        )
                                        .apply()
                                }
                            }
                        }
                        InsetDivider()
                        NumberInputRow("每日转发上限", "每天最多转发的朋友圈数量，0 表示不限制", dailyLimit) {
                            dailyLimit = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit()
                                    .putInt(MomentsAutoForwardSettings.KEY_DAILY_LIMIT, value.coerceAtLeast(0))
                                    .apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容处理") }
                item {
                    SettingsCard {
                        SwitchRow(
                            includeKeywordsEnabled,
                            "包含关键词筛选",
                            "开启后，正文需命中任意关键词才转发"
                        ) {
                            includeKeywordsEnabled = it
                            sp.edit()
                                .putBoolean(MomentsAutoForwardSettings.KEY_INCLUDE_KEYWORDS_ENABLE, it)
                                .apply()
                        }
                        if (includeKeywordsEnabled) {
                            InsetDivider()
                            InputRow(
                                "包含关键词",
                                "多个关键词用逗号或换行分隔",
                                includeKeywords,
                                minLines = 2
                            ) {
                                includeKeywords = it
                                sp.edit().putString(MomentsAutoForwardSettings.KEY_INCLUDE_KEYWORDS, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(
                            excludeKeywordsEnabled,
                            "排除关键词筛选",
                            "开启后，正文命中任意关键词时不转发，优先于包含规则"
                        ) {
                            excludeKeywordsEnabled = it
                            sp.edit()
                                .putBoolean(MomentsAutoForwardSettings.KEY_EXCLUDE_KEYWORDS_ENABLE, it)
                                .apply()
                        }
                        if (excludeKeywordsEnabled) {
                            InsetDivider()
                            InputRow(
                                "排除关键词",
                                "多个关键词用逗号或换行分隔",
                                excludeKeywords,
                                minLines = 2
                            ) {
                                excludeKeywords = it
                                sp.edit().putString(MomentsAutoForwardSettings.KEY_EXCLUDE_KEYWORDS, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(
                            replaceKeywordsEnabled,
                            "替换关键词",
                            "转发前按多条规则替换朋友圈原文"
                        ) {
                            replaceKeywordsEnabled = it
                            sp.edit()
                                .putBoolean(MomentsAutoForwardSettings.KEY_REPLACE_KEYWORDS_ENABLE, it)
                                .apply()
                        }
                        if (replaceKeywordsEnabled) {
                            InsetDivider()
                            ActionRow(
                                "替换规则",
                                if (replacementRuleCount == 0) {
                                    "暂无规则"
                                } else {
                                    "已设置 $replacementRuleCount 条规则"
                                },
                                onOpenReplacementRules
                            )
                        }
                        InsetDivider()
                        VariableInputRow(
                            "文案模板",
                            "支持下方变量",
                            contentTemplate,
                            templateVariables,
                            minLines = 3
                        ) {
                            contentTemplate = it
                            sp.edit().putString(MomentsAutoForwardSettings.KEY_CONTENT_TEMPLATE, it).apply()
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行记录") }
                item {
                    SettingsCard {
                        SwitchRow(logEnabled, "记录运行日志", "记录自动转发执行情况") {
                            logEnabled = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_LOG_ENABLE, it).apply()
                        }
                        if (logEnabled) {
                            InsetDivider()
                            ActionRow(
                                "刷新日志",
                                if (logs.isBlank()) "暂无记录" else "${logs.lineSequence().count()} 条记录"
                            ) {
                                logs = sp.getString(
                                    MomentsAutoForwardSettings.KEY_LOGS,
                                    MomentsAutoForwardSettings.DEFAULT_LOGS
                                ).orEmpty()
                            }
                            if (logs.isNotBlank()) {
                                InsetDivider()
                                Text(
                                    text = logs.take(5000),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                                InsetDivider()
                                ActionRow("清空日志", "删除当前自动转发运行记录") {
                                    sp.edit().remove(MomentsAutoForwardSettings.KEY_LOGS).apply()
                                    logs = ""
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeywordReplacementRulesPage(
    context: Context,
    initialRules: List<KeywordReplacementRule>,
    onBack: () -> Unit,
    onSave: (List<KeywordReplacementRule>) -> Unit
) {
    var rules by remember(initialRules) { mutableStateOf(initialRules) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun updateRule(index: Int, update: (KeywordReplacementRule) -> KeywordReplacementRule) {
        if (index !in rules.indices) return
        rules = rules.toMutableList().also { next -> next[index] = update(next[index]) }
    }

    fun save() {
        val normalized = rules.map { it.copy(keyword = it.keyword.trim()) }
        if (normalized.any { it.keyword.isEmpty() }) {
            Toast.makeText(context, "原关键词不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        val duplicate = normalized.groupingBy { it.keyword.lowercase(Locale.ROOT) }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        if (duplicate != null) {
            Toast.makeText(context, "存在重复的原关键词", Toast.LENGTH_SHORT).show()
            return
        }
        onSave(normalized)
    }

    PageScaffold(
        title = "替换规则",
        largeTitle = "替换规则",
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("保存", ::save, "返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "关键词替换") }
            if (rules.isEmpty()) {
                item {
                    SettingsCard {
                        InfoRow("替换规则", "暂无规则")
                    }
                }
            } else {
                rules.forEachIndexed { index, rule ->
                    item(key = "keyword-replacement-$index") {
                        SettingsCard {
                            InputRow(
                                "原关键词",
                                "匹配时不区分大小写",
                                rule.keyword
                            ) { value ->
                                updateRule(index) { it.copy(keyword = value) }
                            }
                            InsetDivider()
                            InputRow(
                                "替换内容",
                                "留空表示删除原关键词",
                                rule.replacement
                            ) { value ->
                                updateRule(index) { it.copy(replacement = value) }
                            }
                            InsetDivider()
                            ActionRow("删除本条", "移除第 ${index + 1} 条替换规则") {
                                rules = rules.toMutableList().also { it.removeAt(index) }
                            }
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("新增规则", "添加一条关键词替换规则") {
                        rules = rules + KeywordReplacementRule("", "")
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsAutoRefreshMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsAutoRefreshSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoRefreshSettings.KEY_ENABLE, false)) }
    var interval by remember {
        mutableStateOf(sp.getInt(MomentsAutoRefreshSettings.KEY_INTERVAL_SECONDS, MomentsAutoRefreshSettings.DEFAULT_INTERVAL_SECONDS).toString())
    }
    var timeWindow by remember {
        mutableStateOf(sp.getBoolean(MomentsAutoRefreshSettings.KEY_TIME_WINDOW_ENABLE, MomentsAutoRefreshSettings.DEFAULT_TIME_WINDOW_ENABLE))
    }
    var startTime by remember {
        mutableStateOf(sp.getString(MomentsAutoRefreshSettings.KEY_START_TIME, MomentsAutoRefreshSettings.DEFAULT_START_TIME).orEmpty())
    }
    var endTime by remember {
        mutableStateOf(sp.getString(MomentsAutoRefreshSettings.KEY_END_TIME, MomentsAutoRefreshSettings.DEFAULT_END_TIME).orEmpty())
    }
    var lastTime by remember { mutableStateOf(sp.getLong(MomentsAutoRefreshSettings.KEY_LAST_TIME, 0L)) }
    var lastResult by remember { mutableStateOf(sp.getString(MomentsAutoRefreshSettings.KEY_LAST_RESULT, "").orEmpty()) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "后台刷新") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈自动刷新", "按设定间隔获取新的朋友圈内容") {
                        enabled = it
                        sp.edit().putBoolean(MomentsAutoRefreshSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        NumberInputRow("刷新间隔", "单位秒，最少 0 秒", interval) {
                            interval = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit().putInt(MomentsAutoRefreshSettings.KEY_INTERVAL_SECONDS, value.coerceAtLeast(0)).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(timeWindow, "限制刷新时段", "支持跨零点时段") {
                            timeWindow = it
                            sp.edit().putBoolean(MomentsAutoRefreshSettings.KEY_TIME_WINDOW_ENABLE, it).apply()
                        }
                        if (timeWindow) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", startTime) {
                                startTime = it
                                sp.edit().putString(MomentsAutoRefreshSettings.KEY_START_TIME, it).apply()
                            }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", endTime) {
                                endTime = it
                                sp.edit().putString(MomentsAutoRefreshSettings.KEY_END_TIME, it).apply()
                            }
                        }
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "状态") }
                item {
                    SettingsCard {
                        ActionRow(
                            "刷新状态",
                            if (lastTime <= 0L) "尚未执行" else SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(lastTime)) + " · " + lastResult
                        ) {
                            lastTime = sp.getLong(MomentsAutoRefreshSettings.KEY_LAST_TIME, 0L)
                            lastResult = sp.getString(MomentsAutoRefreshSettings.KEY_LAST_RESULT, "").orEmpty()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsContactFilterMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsContactFilterSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsContactFilterRoute>(MomentsContactFilterRoute.Main) }
    var targets by remember {
        mutableStateOf(
            parseIds(
                sp.getString(
                    MomentsContactFilterSettings.KEY_TARGETS,
                    MomentsContactFilterSettings.DEFAULT_TARGETS
                ).orEmpty()
            )
        )
    }

    SettingsRouteTransition(
        targetState = route,
        label = "MomentsContactFilterRoute",
        depthOf = { if (it is MomentsContactFilterRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsContactFilterRoute.Main -> MomentsContactFilterMainPage(
                provider = provider,
                sp = sp,
                targets = targets,
                onBack = onBack,
                onPickTargets = {
                    route = MomentsContactFilterRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "选择朋友圈过滤对象",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(targets),
                            onValue = {},
                            enableLabels = true,
                            enableScopeSelection = true
                        )
                    )
                }
            )
            is MomentsContactFilterRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsContactFilterRoute.Main },
                onConfirm = { picked ->
                    targets = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    sp.edit()
                        .putString(MomentsContactFilterSettings.KEY_TARGETS, formatIds(targets))
                        .apply()
                    route = MomentsContactFilterRoute.Main
                }
            )
        }
    }
}

@Composable
private fun MomentsContactFilterMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    targets: Set<String>,
    onBack: () -> Unit,
    onPickTargets: () -> Unit
) {
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsContactFilterSettings.KEY_ENABLE,
                MomentsContactFilterSettings.DEFAULT_ENABLE
            )
        )
    }
    var mode by remember {
        mutableStateOf(
            sp.getInt(
                MomentsContactFilterSettings.KEY_MODE,
                MomentsContactFilterSettings.DEFAULT_MODE
            )
        )
    }
    val targetSummary = when {
        targets.isEmpty() -> "未选择好友，暂不筛选"
        mode == MomentsContactFilterSettings.MODE_INCLUDE_ONLY -> "只显示所选 ${targets.size} 位好友"
        else -> "隐藏所选 ${targets.size} 位好友"
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "朋友圈过滤",
                        summary = "按好友范围过滤朋友圈内容"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(MomentsContactFilterSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        PopupOptionRow(
                            title = "过滤模式",
                            summary = if (mode == MomentsContactFilterSettings.MODE_INCLUDE_ONLY) {
                                "只看所选好友的朋友圈"
                            } else {
                                "过滤所选好友的朋友圈"
                            },
                            options = optionItems(
                                "过滤所选好友" to MomentsContactFilterSettings.MODE_EXCLUDE,
                                "只看所选好友" to MomentsContactFilterSettings.MODE_INCLUDE_ONLY
                            ),
                            currentValue = mode,
                            onValueChanged = {
                                mode = it
                                sp.edit().putInt(MomentsContactFilterSettings.KEY_MODE, it).apply()
                            }
                        )
                        InsetDivider()
                        ActionRow(
                            title = "过滤对象",
                            summary = targetSummary
                        ) {
                            onPickTargets()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsKeywordBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsKeywordBlockSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsKeywordBlockSettings.KEY_ENABLE,
                MomentsKeywordBlockSettings.DEFAULT_ENABLE
            )
        )
    }
    var keywords by remember {
        mutableStateOf(
            sp.getString(
                MomentsKeywordBlockSettings.KEY_KEYWORDS,
                MomentsKeywordBlockSettings.DEFAULT_KEYWORDS
            ).orEmpty()
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "朋友圈关键词屏蔽",
                        summary = "隐藏正文命中任意关键词的朋友圈"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(MomentsKeywordBlockSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        InputRow(
                            title = "屏蔽关键词",
                            summary = "多个关键词用逗号或换行分隔",
                            value = keywords,
                            minLines = 3
                        ) {
                            keywords = it
                            sp.edit().putString(MomentsKeywordBlockSettings.KEY_KEYWORDS, it).apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsBottomDetailMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsBottomDetailSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsBottomDetailSettings.KEY_ENABLE,
                MomentsBottomDetailSettings.DEFAULT_ENABLE
            )
        )
    }
    var textFormat by remember {
        mutableStateOf(
            sp.getString(
                MomentsBottomDetailSettings.KEY_TEXT_FORMAT,
                MomentsBottomDetailSettings.DEFAULT_TEXT_FORMAT
            ).orEmpty()
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                MomentsBottomDetailSettings.KEY_TIME_FORMAT,
                MomentsBottomDetailSettings.DEFAULT_TIME_FORMAT
            ).orEmpty()
        )
    }
    var hideGroupIcon by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsBottomDetailSettings.KEY_HIDE_GROUP_ICON,
                MomentsBottomDetailSettings.DEFAULT_HIDE_GROUP_ICON
            )
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = {
                    val timeFormatValid = MomentsBottomDetailSettings.isValidTimeFormat(timeFormat)
                    if (enabled && !timeFormatValid) {
                        Toast.makeText(context, "时间格式无效", Toast.LENGTH_SHORT).show()
                    } else {
                        val normalizedText = MomentsBottomDetailSettings.normalizeTextFormat(textFormat)
                        val normalizedTime = if (timeFormatValid) {
                            MomentsBottomDetailSettings.normalizeTimeFormat(timeFormat)
                        } else {
                            MomentsBottomDetailSettings.normalizeTimeFormat(
                                sp.getString(
                                    MomentsBottomDetailSettings.KEY_TIME_FORMAT,
                                    MomentsBottomDetailSettings.DEFAULT_TIME_FORMAT
                                )
                            )
                        }
                        textFormat = normalizedText
                        timeFormat = normalizedTime
                        sp.edit()
                            .putBoolean(MomentsBottomDetailSettings.KEY_ENABLE, enabled)
                            .putString(MomentsBottomDetailSettings.KEY_TEXT_FORMAT, normalizedText)
                            .putString(MomentsBottomDetailSettings.KEY_TIME_FORMAT, normalizedTime)
                            .putBoolean(MomentsBottomDetailSettings.KEY_HIDE_GROUP_ICON, hideGroupIcon)
                            .apply()
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                    }
                },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "朋友圈底部详情") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "朋友圈底部详情",
                        summary = "在朋友圈底部显示自定义时间和详情"
                    ) {
                        enabled = it
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "格式") }
                item {
                    SettingsCard {
                        VariableInputRow(
                            title = "文本格式",
                            summary = "留空使用默认格式",
                            value = textFormat,
                            variables = momentsBottomDetailTemplateVariables,
                            onValueChange = { textFormat = it }
                        )
                        InsetDivider()
                        InputRow(
                            "时间格式",
                            "使用日期格式，例如 yyyy-MM-dd HH:mm:ss",
                            timeFormat
                        ) { timeFormat = it }
                        InsetDivider()
                        SwitchRow(
                            checked = hideGroupIcon,
                            title = "隐藏可见范围",
                            summary = "隐藏朋友圈底部的可见范围图标"
                        ) {
                            hideGroupIcon = it
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsPostNotificationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsPostNotificationSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsPostNotificationRoute>(MomentsPostNotificationRoute.Main) }
    var targets by remember {
        mutableStateOf(parseIds(sp.getString(MomentsPostNotificationSettings.KEY_TARGETS, "").orEmpty()))
    }
    SettingsRouteTransition(
        targetState = route,
        label = "MomentsPostNotificationRoute",
        depthOf = { if (it is MomentsPostNotificationRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsPostNotificationRoute.Main -> MomentsPostNotificationMainPage(
                provider = provider,
                sp = sp,
                targetCount = targets.size,
                onBack = onBack,
                onPickTargets = {
                    route = MomentsPostNotificationRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "选择朋友圈提醒好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(targets),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is MomentsPostNotificationRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsPostNotificationRoute.Main },
                onConfirm = { picked ->
                    targets = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    sp.edit().putString(MomentsPostNotificationSettings.KEY_TARGETS, formatIds(targets)).apply()
                    route = MomentsPostNotificationRoute.Main
                }
            )
        }
    }
}

@Composable
private fun MomentsPostNotificationMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    targetCount: Int,
    onBack: () -> Unit,
    onPickTargets: () -> Unit
) {
    var enabled by remember { mutableStateOf(sp.getBoolean(MomentsPostNotificationSettings.KEY_ENABLE, false)) }
    var systemNotification by remember {
        mutableStateOf(sp.getBoolean(MomentsPostNotificationSettings.KEY_SYSTEM_NOTIFICATION, MomentsPostNotificationSettings.DEFAULT_SYSTEM_NOTIFICATION))
    }
    var toast by remember {
        mutableStateOf(sp.getBoolean(MomentsPostNotificationSettings.KEY_TOAST, MomentsPostNotificationSettings.DEFAULT_TOAST))
    }
    var titleTemplate by remember {
        mutableStateOf(sp.getString(MomentsPostNotificationSettings.KEY_TITLE_TEMPLATE, MomentsPostNotificationSettings.DEFAULT_TITLE_TEMPLATE).orEmpty())
    }
    var bodyTemplate by remember {
        mutableStateOf(sp.getString(MomentsPostNotificationSettings.KEY_BODY_TEMPLATE, MomentsPostNotificationSettings.DEFAULT_BODY_TEMPLATE).orEmpty())
    }
    var toastTemplate by remember {
        mutableStateOf(sp.getString(MomentsPostNotificationSettings.KEY_TOAST_TEMPLATE, MomentsPostNotificationSettings.DEFAULT_TOAST_TEMPLATE).orEmpty())
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "发布提醒") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈发布通知", "指定好友发布新朋友圈时提醒") {
                        enabled = it
                        sp.edit()
                            .putBoolean(MomentsPostNotificationSettings.KEY_ENABLE, it)
                            .putLong(
                                MomentsPostNotificationSettings.KEY_ENABLED_AT_SECONDS,
                                if (it) System.currentTimeMillis() / 1000L else 0L
                            )
                            .apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        ActionRow("提醒好友", if (targetCount == 0) "未选择好友" else "已选择 $targetCount 位好友", onPickTargets)
                        InsetDivider()
                        SwitchRow(systemNotification, "系统通知", "显示在系统通知栏") {
                            systemNotification = it
                            sp.edit().putBoolean(MomentsPostNotificationSettings.KEY_SYSTEM_NOTIFICATION, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(toast, "Toast提醒", "在微信界面短暂显示提醒") {
                            toast = it
                            sp.edit().putBoolean(MomentsPostNotificationSettings.KEY_TOAST, it).apply()
                        }
                    }
                }
            }
            if (enabled && (systemNotification || toast)) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提醒模板") }
                item {
                    SettingsCard {
                        if (systemNotification) {
                            VariableInputRow("通知标题", "留空使用默认标题", titleTemplate, momentsPostNotificationTemplateVariables) {
                                titleTemplate = it
                                sp.edit().putString(MomentsPostNotificationSettings.KEY_TITLE_TEMPLATE, it).apply()
                            }
                            InsetDivider()
                            VariableInputRow("通知内容", "留空使用发布者、类型和完整正文", bodyTemplate, momentsPostNotificationTemplateVariables, minLines = 2) {
                                bodyTemplate = it
                                sp.edit().putString(MomentsPostNotificationSettings.KEY_BODY_TEMPLATE, it).apply()
                            }
                        }
                        if (toast) {
                            if (systemNotification) InsetDivider()
                            VariableInputRow("Toast内容", "留空使用默认提醒", toastTemplate, momentsPostNotificationTemplateVariables, minLines = 2) {
                                toastTemplate = it
                                sp.edit().putString(MomentsPostNotificationSettings.KEY_TOAST_TEMPLATE, it).apply()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OriginalMomentsUploadMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, OriginalMomentsUploadSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        OriginalMomentsUploadSettings.KEY_ENABLE,
                        "原图上传",
                        "发布朋友圈图片和视频时尽量跳过微信压缩",
                        OriginalMomentsUploadSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun MomentsUploadTailMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsUploadTailSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsUploadTailSettings.KEY_ENABLE,
                MomentsUploadTailSettings.DEFAULT_ENABLE
            )
        )
    }
    var sdkId by remember {
        mutableStateOf(
            sp.getString(
                MomentsUploadTailSettings.KEY_SDK_ID,
                MomentsUploadTailSettings.DEFAULT_SDK_ID
            ).orEmpty()
        )
    }
    var sdkAppName by remember {
        mutableStateOf(
            sp.getString(
                MomentsUploadTailSettings.KEY_SDK_APP_NAME,
                MomentsUploadTailSettings.DEFAULT_SDK_APP_NAME
            ).orEmpty()
        )
    }

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        enabled,
                        "朋友圈上传尾巴",
                        "发布时附带指定的 SDK 来源"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(MomentsUploadTailSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        InputRow("SDK ID", "输入第三方 SDK 标识", sdkId) {
                            sdkId = it
                            sp.edit().putString(MomentsUploadTailSettings.KEY_SDK_ID, it).apply()
                        }
                        InsetDivider()
                        InputRow("SDK 名称", "输入显示的 SDK 名称", sdkAppName) {
                            sdkAppName = it
                            sp.edit()
                                .putString(MomentsUploadTailSettings.KEY_SDK_APP_NAME, it)
                                .apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SnsAntiRecallMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, SnsAntiRecallSettings.PREFS_NAME) }
    var momentMarkText by remember {
        mutableStateOf(
            sp.getString(
                SnsAntiRecallSettings.KEY_CUSTOM_MARK_TEXT,
                SnsAntiRecallSettings.DEFAULT_CUSTOM_MARK_TEXT
            ).orEmpty()
        )
    }
    var commentMarkText by remember {
        mutableStateOf(
            sp.getString(
                SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_TEXT,
                SnsAntiRecallSettings.DEFAULT_COMMENT_CUSTOM_MARK_TEXT
            ).orEmpty()
        )
    }
    var momentMarkEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                SnsAntiRecallSettings.KEY_CUSTOM_MARK_ENABLE,
                SnsAntiRecallSettings.DEFAULT_CUSTOM_MARK_ENABLE
            )
        )
    }
    var commentMarkEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_ENABLE,
                SnsAntiRecallSettings.DEFAULT_COMMENT_CUSTOM_MARK_ENABLE
            )
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SnsAntiRecallSettings.KEY_ENABLE,
                        "朋友圈防撤回",
                        "已缓存的朋友圈在对方删除或限制可见范围后继续显示",
                        SnsAntiRecallSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = momentMarkEnabled,
                        title = "自定义朋友圈撤回提示",
                        summary = "开启后可自定义朋友圈正文被删除时的提示文案"
                    ) { checked ->
                        momentMarkEnabled = checked
                        sp.edit().putBoolean(SnsAntiRecallSettings.KEY_CUSTOM_MARK_ENABLE, checked).apply()
                    }
                    if (momentMarkEnabled) {
                        InsetDivider()
                        InputRow("朋友圈提示文案", "默认 [已删除]", momentMarkText) { momentMarkText = it }
                    }
                    InsetDivider()
                    SwitchRow(
                        sp,
                        SnsAntiRecallSettings.KEY_COMMENT_ENABLE,
                        "朋友圈评论防撤回",
                        "已缓存的朋友圈评论被删除后继续显示，并标记已删除",
                        SnsAntiRecallSettings.DEFAULT_COMMENT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = commentMarkEnabled,
                        title = "自定义评论撤回提示",
                        summary = "开启后可自定义朋友圈评论被删除时的提示文案"
                    ) { checked ->
                        commentMarkEnabled = checked
                        sp.edit().putBoolean(SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_ENABLE, checked).apply()
                    }
                    if (commentMarkEnabled) {
                        InsetDivider()
                        InputRow("评论提示文案", "默认 [已删除]", commentMarkText) { commentMarkText = it }
                    }
                    InsetDivider()
                    SwitchRow(
                        sp,
                        SnsAntiRecallSettings.KEY_FORCE_LEGACY_PROFILE,
                        "强制旧版个人主页朋友圈",
                        "Flutter 个人主页看不到已删除朋友圈时，改用微信旧版 SnsUserUI",
                        SnsAntiRecallSettings.DEFAULT_FORCE_LEGACY_PROFILE
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sp.edit()
                .putString(
                    SnsAntiRecallSettings.KEY_CUSTOM_MARK_TEXT,
                    momentMarkText.ifBlank { SnsAntiRecallSettings.DEFAULT_CUSTOM_MARK_TEXT }
                )
                .putString(
                    SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_TEXT,
                    commentMarkText.ifBlank { SnsAntiRecallSettings.DEFAULT_COMMENT_CUSTOM_MARK_TEXT }
                )
                .apply()
        }
    }
}

@Composable
internal fun MomentsFakeInteractionMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember {
        HchatStorage.preferences(context, MomentsFakeInteractionSettings.PREFS_NAME)
    }
    val isLike = provider.featureId() == MomentsFakeLikeSettingsProvider.ID
    val isForward = provider.featureId() == MomentsFakeForwardSettingsProvider.ID
    val enableKey = when {
        isLike -> MomentsFakeInteractionSettings.KEY_FAKE_LIKE_ENABLE
        isForward -> MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_ENABLE
        else -> MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_ENABLE
    }
    val defaultEnabled = when {
        isLike -> MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_ENABLE
        isForward -> MomentsFakeInteractionSettings.DEFAULT_FAKE_FORWARD_ENABLE
        else -> MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_ENABLE
    }
    val pendingRestoreKey = if (isLike) {
        MomentsFakeInteractionSettings.KEY_PENDING_RESTORE_LIKES
    } else {
        MomentsFakeInteractionSettings.KEY_PENDING_RESTORE_COMMENTS
    }
    val useNonFriendsKey = if (isLike) {
        MomentsFakeInteractionSettings.KEY_FAKE_LIKE_USE_NON_FRIENDS
    } else {
        MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_USE_NON_FRIENDS
    }
    val defaultUseNonFriends = if (isLike) {
        MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_USE_NON_FRIENDS
    } else {
        MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_USE_NON_FRIENDS
    }
    var enabled by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(enableKey, defaultEnabled)
        )
    }
    var useNonFriends by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(useNonFriendsKey, defaultUseNonFriends)
        )
    }
    var randomCommentContent by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(
                MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_RANDOM_CONTENT,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_RANDOM_CONTENT
            )
        )
    }
    var commentContents by remember(provider.featureId()) {
        mutableStateOf(MomentsFakeInteractionSettings.commentContents(sp))
    }
    var randomOrder by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(
                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_RANDOM_ORDER,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_RANDOM_ORDER
            )
        )
    }
    var autoSelect by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(
                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_AUTO_SELECT
            )
        )
    }
    var autoSelectCount by remember(provider.featureId()) {
        mutableStateOf(
            sp.getInt(
                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT_COUNT,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_AUTO_SELECT_COUNT
            ).toString()
        )
    }
    var excludedIds by remember(provider.featureId()) {
        mutableStateOf(
            sp.getStringSet(
                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_EXCLUDED_IDS,
                emptySet()
            ).orEmpty().toSet()
        )
    }
    val menuTextKey = when {
        isLike -> MomentsFakeInteractionSettings.KEY_FAKE_LIKE_MENU_TEXT
        isForward -> MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_MENU_TEXT
        else -> MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_MENU_TEXT
    }
    val defaultMenuText = when {
        isLike -> MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_MENU_TEXT
        isForward -> MomentsFakeInteractionSettings.DEFAULT_FAKE_FORWARD_MENU_TEXT
        else -> MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_MENU_TEXT
    }
    val hideMenuKey = when {
        isLike -> MomentsFakeInteractionSettings.KEY_FAKE_LIKE_HIDE_MENU
        isForward -> MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_HIDE_MENU
        else -> MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_HIDE_MENU
    }
    val defaultHideMenu = when {
        isLike -> MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_HIDE_MENU
        isForward -> MomentsFakeInteractionSettings.DEFAULT_FAKE_FORWARD_HIDE_MENU
        else -> MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_HIDE_MENU
    }
    var hideMenu by remember(provider.featureId()) {
        mutableStateOf(sp.getBoolean(hideMenuKey, defaultHideMenu))
    }
    var menuText by remember(provider.featureId()) {
        mutableStateOf(sp.getString(menuTextKey, defaultMenuText).orEmpty().ifBlank { defaultMenuText })
    }
    var forwardDebugLog by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(
                MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_DEBUG_LOG,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_FORWARD_DEBUG_LOG
            )
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = provider.title()) }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = provider.title(),
                        summary = provider.subtitle()
                    ) {
                        enabled = it
                        sp.edit().putBoolean(enableKey, it).commit()
                        MomentsFakeInteractionRuntimeRegistry.reapplyAll()
                    }
                }
            }
            if (enabled && isLike) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "生成方式") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = useNonFriends,
                            title = "使用非好友",
                            summary = "仅在伪集赞选择器中额外加入群成员"
                        ) {
                            useNonFriends = it
                            sp.edit().putBoolean(useNonFriendsKey, it).commit()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = randomOrder,
                            title = "随机排序",
                            summary = "保存时随机排列点赞人的显示顺序"
                        ) {
                            randomOrder = it
                            sp.edit().putBoolean(
                                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_RANDOM_ORDER,
                                it
                            ).commit()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = autoSelect,
                            title = "自动勾选好友",
                            summary = "长按伪集赞后自动勾选指定人数"
                        ) {
                            autoSelect = it
                            sp.edit().putBoolean(
                                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT,
                                it
                            ).commit()
                        }
                        if (autoSelect) {
                            InsetDivider()
                            NumberInputRow(
                                title = "自动勾选数量",
                                summary = "最少 1 人，不设上限",
                                value = autoSelectCount
                            ) { next ->
                                val count = next.toIntOrNull()?.coerceAtLeast(1)
                                autoSelectCount = count?.toString().orEmpty()
                                count?.let {
                                    sp.edit().putInt(
                                        MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT_COUNT,
                                        it
                                    ).apply()
                                }
                            }
                        }
                        InsetDivider()
                        ActionRow(
                            title = "设置排除名单",
                            summary = if (excludedIds.isEmpty()) "未设置" else "已排除 ${excludedIds.size} 人",
                            onClick = {
                                showMomentsFakeLikeExclusionPicker(
                                    context = context,
                                    includeNonFriends = useNonFriends,
                                    selectedIds = excludedIds
                                ) { selected ->
                                    excludedIds = selected
                                    sp.edit().putStringSet(
                                        MomentsFakeInteractionSettings.KEY_FAKE_LIKE_EXCLUDED_IDS,
                                        selected
                                    ).commit()
                                }
                            }
                        )
                    }
                }
            }
            if (enabled && !isLike && !isForward) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "评论人范围") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = useNonFriends,
                            title = "使用非好友",
                            summary = "仅在伪评论选择器中额外加入群成员"
                        ) {
                            useNonFriends = it
                            sp.edit().putBoolean(useNonFriendsKey, it).commit()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = randomCommentContent,
                            title = "随机评论",
                            summary = if (commentContents.isEmpty()) {
                                "评论池为空时仍使用手动输入"
                            } else {
                                "为每位选中的好友从 ${commentContents.size} 条评论中随机选择"
                            }
                        ) {
                            randomCommentContent = it
                            sp.edit().putBoolean(
                                MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_RANDOM_CONTENT,
                                it
                            ).commit()
                        }
                        InsetDivider()
                        ActionRow(
                            title = "添加新评论",
                            summary = if (commentContents.isEmpty()) "尚未添加" else "当前 ${commentContents.size} 条"
                        ) {
                            showFakeCommentContentEditor(context, "") { content ->
                                val next = commentContents + content
                                if (MomentsFakeInteractionSettings.saveCommentContents(sp, next)) {
                                    commentContents = next
                                }
                            }
                        }
                        commentContents.forEachIndexed { index, content ->
                            InsetDivider()
                            ActionRow(
                                title = "评论 ${index + 1}",
                                summary = content
                            ) {
                                showFakeCommentContentActions(
                                    context = context,
                                    content = content,
                                    onEdit = { edited ->
                                        val next = commentContents.toMutableList().also { it[index] = edited }
                                        if (MomentsFakeInteractionSettings.saveCommentContents(sp, next)) {
                                            commentContents = next
                                        }
                                    },
                                    onDelete = {
                                        val next = commentContents.toMutableList().also { it.removeAt(index) }
                                        if (MomentsFakeInteractionSettings.saveCommentContents(sp, next)) {
                                            commentContents = next
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "长按菜单") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = hideMenu,
                            title = "隐藏长按菜单",
                            summary = when {
                                isLike -> "隐藏伪集赞入口，已伪造的点赞仍然显示"
                                isForward -> "隐藏伪转发入口，已保存的本地朋友圈仍然显示"
                                else -> "隐藏伪评论入口，已伪造的评论仍然显示"
                            }
                        ) {
                            hideMenu = it
                            sp.edit().putBoolean(hideMenuKey, it).commit()
                        }
                        if (!hideMenu) {
                            InsetDivider()
                            InputRow(
                                title = when {
                                    isLike -> "伪集赞自定义文本"
                                    isForward -> "伪转发自定义文本"
                                    else -> "伪评论自定义文本"
                                },
                                summary = "完整替换朋友圈长按菜单文字",
                                value = menuText,
                                onValueChange = { next ->
                                    menuText = next
                                    sp.edit().putString(menuTextKey, next).apply()
                                }
                            )
                        }
                    }
                }
            }
            if (isForward) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "诊断") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = forwardDebugLog,
                            title = "调试日志",
                            summary = "输出伪转发创建、分页、刷新和清理诊断到 LSPosed 日志"
                        ) {
                            if (sp.edit().putBoolean(
                                    MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_DEBUG_LOG,
                                    it
                                ).commit()
                            ) {
                                forwardDebugLog = it
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow(
                            title = "清空伪转发",
                            summary = "删除全部已登记的本地伪转发，不影响真实朋友圈",
                            onClick = {
                                confirmClearMomentsFakeForwards(context) {
                                    val scheduled = MomentsFakeForwardRuntimeRegistry.clearAll { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "已清空伪转发" else "部分记录删除失败，请稍后重试",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    Toast.makeText(
                                        context,
                                        if (scheduled) "正在清理..." else "微信运行时尚未就绪",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            } else {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow(
                            title = if (isLike) "清除伪集赞" else "清除伪评论",
                        summary = if (isLike) {
                            "清空已保存的本地点赞，保留当前功能设置"
                        } else {
                            "清空已保存的本地评论，保留当前功能设置"
                        },
                        onClick = {
                            confirmClearMomentsFakeInteractions(context, isLike) {
                                scheduleMomentsFakeInteractionRestore(context, sp, isLike)
                            }
                        }
                    )
                    InsetDivider()
                    ActionRow(
                        title = "恢复默认",
                        summary = if (isLike) {
                            "关闭伪集赞并清空已保存的本地点赞"
                        } else {
                            "关闭伪评论并清空已保存的本地评论"
                        },
                        onClick = {
                            val editor = sp.edit()
                                .putBoolean(enableKey, defaultEnabled)
                                .putBoolean(pendingRestoreKey, true)
                            if (isLike) {
                                editor
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_USE_NON_FRIENDS)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_RANDOM_ORDER)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT_COUNT)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_EXCLUDED_IDS)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_HIDE_MENU)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_MENU_TEXT)
                            } else {
                                editor
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_USE_NON_FRIENDS)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_HIDE_MENU)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_MENU_TEXT)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_RANDOM_CONTENT)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_CONTENTS)
                            }
                            val settingsRestored = editor.commit()
                            if (!settingsRestored) {
                                Toast.makeText(context, "恢复默认失败", Toast.LENGTH_SHORT).show()
                            } else {
                                enabled = defaultEnabled
                                hideMenu = defaultHideMenu
                                menuText = defaultMenuText
                                useNonFriends = defaultUseNonFriends
                                randomCommentContent = MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_RANDOM_CONTENT
                                commentContents = emptyList()
                                if (isLike) {
                                    randomOrder = MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_RANDOM_ORDER
                                    autoSelect = MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_AUTO_SELECT
                                    autoSelectCount = MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_AUTO_SELECT_COUNT.toString()
                                    excludedIds = emptySet()
                                }
                                scheduleMomentsFakeInteractionRestore(context, sp, isLike, "已恢复默认")
                            }
                        }
                    )
                }
            }
            }
        }
    }
}

private fun confirmClearMomentsFakeForwards(
    context: Context,
    onConfirmed: () -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    VoiceForwardMiuixDialog.showConfirm(
        activity = activity,
        title = "清空伪转发",
        message = "删除全部已登记的本地伪转发？真实朋友圈不会受影响。",
        onResult = { if (it) onConfirmed() },
        onDismiss = {}
    )
}

private fun showMomentsFakeLikeExclusionPicker(
    context: Context,
    includeNonFriends: Boolean,
    selectedIds: Set<String>,
    onConfirm: (Set<String>) -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    val loading = VoiceForwardMiuixDialog.showLoading(
        activity = activity,
        title = "设置排除名单",
        message = if (includeNonFriends) "正在载入好友和非好友..." else "正在载入好友列表...",
        onDismiss = {}
    )
    Thread({
        val contacts = runCatching {
            MomentsFakeLikeCandidateRepository.loadForLikes(includeNonFriends)
        }.getOrDefault(emptyList())
        activity.runOnUiThread {
            loading.close()
            activity.window?.decorView?.postOnAnimation {
                if (activity.isFinishing || activity.isDestroyed) return@postOnAnimation
                if (contacts.isEmpty()) {
                    Toast.makeText(activity, "没有可选择的联系人", Toast.LENGTH_SHORT).show()
                    return@postOnAnimation
                }
                val visibleIds = contacts.mapTo(hashSetOf()) { it.id }
                val hiddenSelectedIds = selectedIds.filterNotTo(linkedSetOf()) {
                    visibleIds.contains(it)
                }
                VoiceForwardMiuixDialog.showContacts(
                    activity = activity,
                    contacts = contacts,
                    title = "设置排除名单",
                    confirmText = "保存",
                    showGroupFilter = false,
                    initialSelectedIds = selectedIds,
                    allowEmpty = true,
                    showClearSelectionAction = true,
                    onConfirm = { selected ->
                        onConfirm(hiddenSelectedIds + selected.mapTo(linkedSetOf()) { it.id })
                    },
                    onDismiss = {}
                )
            }
        }
    }, "Hchat-MomentsFakeLikeExclude").apply { isDaemon = true }.start()
}

private fun confirmClearMomentsFakeInteractions(
    context: Context,
    isLike: Boolean,
    onConfirmed: () -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    VoiceForwardMiuixDialog.showConfirm(
        activity = activity,
        title = if (isLike) "清除伪集赞" else "清除伪评论",
        message = if (isLike) "清空全部朋友圈已保存的本地点赞？" else "清空全部朋友圈已保存的本地评论？",
        onResult = { if (it) onConfirmed() },
        onDismiss = {}
    )
}

private fun showFakeCommentContentEditor(
    context: Context,
    initialValue: String,
    onSaved: (String) -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    VoiceForwardMiuixDialog.showTextInput(
        activity = activity,
        title = if (initialValue.isBlank()) "添加新评论" else "修改评论内容",
        summary = "保存后可用于随机评论",
        initialValue = initialValue,
        placeholder = "请输入评论内容",
        maxLength = 1000,
        singleLine = false,
        onConfirm = onSaved,
        onDismiss = {}
    )
}

private fun showFakeCommentContentActions(
    context: Context,
    content: String,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    VoiceForwardMiuixDialog.showChoices(
        activity = activity,
        title = "评论内容",
        summary = content,
        choices = listOf(
            "修改" to "编辑这条随机评论内容",
            "删除" to "从评论池移除这条内容"
        ),
        onSelected = { index ->
            if (index == 0) {
                showFakeCommentContentEditor(context, content, onEdit)
            } else {
                VoiceForwardMiuixDialog.showConfirm(
                    activity = activity,
                    title = "删除评论内容",
                    message = "从随机评论池删除这条内容？",
                    onResult = { if (it) onDelete() },
                    onDismiss = {}
                )
            }
        },
        onDismiss = {}
    )
}

private fun scheduleMomentsFakeInteractionRestore(
    context: Context,
    sp: SharedPreferences,
    isLike: Boolean,
    successText: String = "已清除"
) {
    val pendingKey = if (isLike) {
        MomentsFakeInteractionSettings.KEY_PENDING_RESTORE_LIKES
    } else {
        MomentsFakeInteractionSettings.KEY_PENDING_RESTORE_COMMENTS
    }
    if (!sp.edit().putBoolean(pendingKey, true).commit()) {
        Toast.makeText(context, "保存清理状态失败", Toast.LENGTH_SHORT).show()
        return
    }
    val onRestored: (Boolean) -> Unit = { restored ->
        Toast.makeText(
            context,
            if (restored) successText else "缓存将在下次启动时继续清理",
            Toast.LENGTH_SHORT
        ).show()
    }
    val scheduled = if (isLike) {
        MomentsFakeInteractionRuntimeRegistry.restoreLikes(onRestored)
    } else {
        MomentsFakeInteractionRuntimeRegistry.restoreComments(onRestored)
    }
    Toast.makeText(
        context,
        if (scheduled) "正在清理..." else "缓存将在下次启动时清理",
        Toast.LENGTH_SHORT
    ).show()
}

@Composable
internal fun RemoveMomentsAdsMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RemoveMomentsAdsSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        RemoveMomentsAdsSettings.KEY_ENABLE,
                        "去除朋友圈广告",
                        "阻止朋友圈广告信息解析和展示",
                        RemoveMomentsAdsSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun FinderMediaDownloadMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FinderMediaDownloadSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "视频号") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        FinderMediaDownloadSettings.KEY_ENABLE,
                        "视频号媒体下载",
                        "在视频号分享菜单增加复制链接和下载入口，媒体保存到 Hchat/Finder",
                        FinderMediaDownloadSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun HchatExtraToggleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    key: String,
    defaultValue: Boolean,
    summary: String,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, HchatExtraSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "功能开关") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        key,
                        provider.title(),
                        summary,
                        defaultValue
                    )
                }
            }
        }
    }
}

@Composable
internal fun MessageDetailsConfigPage(
    context: Context,
    sp: SharedPreferences,
    onBack: () -> Unit
) {
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                HchatExtraSettings.KEY_MESSAGE_DETAILS,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS
            )
        )
    }
    var lightBg by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_BG,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG
            ).orEmpty()
        )
    }
    var lightText by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_TEXT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT
            ).orEmpty()
        )
    }
    var darkBg by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_BG,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG
            ).orEmpty()
        )
    }
    var darkText by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_TEXT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT
            ).orEmpty()
        )
    }
    var format by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT
            ).orEmpty()
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_TIME_FORMAT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TIME_FORMAT
            ).orEmpty()
        )
    }
    var position by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_POSITION,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_POSITION
            ).orEmpty().ifBlank { HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_POSITION }
        )
    }
    var avatarGap by remember {
        mutableStateOf(
            sp.getInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_AVATAR_GAP,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_AVATAR_GAP
            ).toString()
        )
    }
    var leftMargin by remember {
        mutableStateOf(
            sp.getInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_LEFT_MARGIN,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LEFT_MARGIN
            ).toString()
        )
    }
    var rightMargin by remember {
        mutableStateOf(
            sp.getInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_RIGHT_MARGIN,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_RIGHT_MARGIN
            ).toString()
        )
    }
    var textSize by remember {
        mutableStateOf(
            sp.getInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_TEXT_SIZE,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TEXT_SIZE
            ).toString()
        )
    }
    var clickShow by remember {
        mutableStateOf(
            sp.getBoolean(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_CLICK_SHOW,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_CLICK_SHOW
            )
        )
    }
    var formatContent by remember {
        mutableStateOf(
            sp.getBoolean(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT_CONTENT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT_CONTENT
            )
        )
    }

    fun resetValues() {
        enabled = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS
        lightBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG
        lightText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT
        darkBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG
        darkText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT
        format = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT
        timeFormat = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TIME_FORMAT
        position = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_POSITION
        avatarGap = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_AVATAR_GAP.toString()
        leftMargin = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LEFT_MARGIN.toString()
        rightMargin = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_RIGHT_MARGIN.toString()
        textSize = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TEXT_SIZE.toString()
        clickShow = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_CLICK_SHOW
        formatContent = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT_CONTENT
    }

    fun saveValues() {
        val savedLightBg = cleanMessageDetailsColor(lightBg, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG)
        val savedLightText = cleanMessageDetailsColor(lightText, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT)
        val savedDarkBg = cleanMessageDetailsColor(darkBg, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG)
        val savedDarkText = cleanMessageDetailsColor(darkText, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT)
        val savedPosition = position.takeIf { it in messageDetailsPositionValues() }
            ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_POSITION
        val savedAvatarGap = (avatarGap.toIntOrNull() ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_AVATAR_GAP)
            .coerceIn(0, 64)
        sp.edit()
            .putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS, enabled)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_BG, savedLightBg)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_TEXT, savedLightText)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_BG, savedDarkBg)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_TEXT, savedDarkText)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT, format.ifBlank { HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT })
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_TIME_FORMAT, timeFormat.ifBlank { HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TIME_FORMAT })
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_POSITION, savedPosition)
            .putInt(HchatExtraSettings.KEY_MESSAGE_DETAILS_AVATAR_GAP, savedAvatarGap)
            .putInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_LEFT_MARGIN,
                leftMargin.toIntOrNull() ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LEFT_MARGIN
            )
            .putInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_RIGHT_MARGIN,
                rightMargin.toIntOrNull() ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_RIGHT_MARGIN
            )
            .putInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_TEXT_SIZE,
                textSize.toIntOrNull() ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TEXT_SIZE
            )
            .putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS_CLICK_SHOW, clickShow)
            .putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT_CONTENT, formatContent)
            .apply()
        lightBg = savedLightBg
        lightText = savedLightText
        darkBg = savedDarkBg
        darkText = savedDarkText
        position = savedPosition
        avatarGap = savedAvatarGap.toString()
        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "消息显示时间",
        largeTitle = "消息显示时间",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = { saveValues() },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "消息显示时间",
                        summary = "开启后按所选位置显示自定义消息时间",
                        onCheckedChange = { enabled = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = clickShow,
                        title = "点击显详情",
                        summary = "点击详情文字后打开消息内容详情",
                        onCheckedChange = { clickShow = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = formatContent,
                        title = "内容格式化",
                        summary = "打开详情时格式化 XML 内容",
                        onCheckedChange = { formatContent = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "颜色") }
            item {
                SettingsCard {
                    ColorPickerRow(
                        "浅色背景",
                        "浅色模式消息时间背景",
                        lightBg,
                        allowGradient = false,
                        onReset = { lightBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG }
                    ) { lightBg = it.take(9) }
                    InsetDivider()
                    ColorPickerRow(
                        "浅色文字",
                        "浅色模式消息时间文字",
                        lightText,
                        allowGradient = false,
                        onReset = { lightText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT }
                    ) { lightText = it.take(9) }
                    InsetDivider()
                    ColorPickerRow(
                        "深色背景",
                        "深色模式消息时间背景",
                        darkBg,
                        allowGradient = false,
                        onReset = { darkBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG }
                    ) { darkBg = it.take(9) }
                    InsetDivider()
                    ColorPickerRow(
                        "深色文字",
                        "深色模式消息时间文字",
                        darkText,
                        allowGradient = false,
                        onReset = { darkText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT }
                    ) { darkText = it.take(9) }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本") }
            item {
                SettingsCard {
                    VariableInputRow(
                        title = "文本格式",
                        summary = "显示在消息旁的内容模板",
                        value = format,
                        variables = messageDetailsVariables(),
                        onValueChange = { format = it }
                    )
                    InsetDivider()
                    PopupChoiceRow(
                        title = "时间显示",
                        summary = messageDetailsTimeFormatChoices(timeFormat).first { it.value == timeFormat }.label,
                        options = messageDetailsTimeFormatChoices(timeFormat),
                        currentValue = timeFormat,
                        onValueChanged = { timeFormat = it }
                    )
                    InsetDivider()
                    InputRow("自定义时间格式", "yyyy 年、MM 月、dd 日；例如 yyyy-MM-dd HH:mm:ss", timeFormat, onValueChange = { timeFormat = it })
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "布局") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "显示位置",
                        summary = messageDetailsPositionLabel(position),
                        options = messageDetailsPositionChoices(),
                        currentValue = position,
                        onValueChanged = { position = it }
                    )
                    InsetDivider()
                    if (position == HchatExtraSettings.POSITION_MESSAGE_BOTTOM) {
                        NumberInputRow("左边距", "单位 dp，对方消息使用", leftMargin, onValueChange = { leftMargin = it })
                        InsetDivider()
                        NumberInputRow("右边距", "单位 dp，自己消息使用", rightMargin, onValueChange = { rightMargin = it })
                        InsetDivider()
                    } else {
                        NumberInputRow("与头像间距", "单位 dp，可设置 0-64", avatarGap, onValueChange = { avatarGap = it })
                        InsetDivider()
                    }
                    NumberInputRow("字体大小", "单位 sp", textSize, onValueChange = { textSize = it })
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("恢复默认", "重置本页全部消息显示时间设置") {
                        resetValues()
                        Toast.makeText(context, "已恢复默认，保存后生效", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

private fun cleanMessageDetailsColor(value: String, fallback: String): String {
    return MemberTitleStore.cleanColor(value).ifEmpty { fallback }
}

private fun messageDetailsTimeFormatChoices(current: String): List<PopupChoice<String>> {
    val choices = listOf(
        PopupChoice(label = "时分秒 · 14:30:05", value = "HH:mm:ss"),
        PopupChoice(label = "月日 时分秒 · 09-11 14:30:05", value = "MM-dd HH:mm:ss"),
        PopupChoice(label = "年月日 时分秒 · 2026-09-11 14:30:05", value = "yyyy-MM-dd HH:mm:ss"),
        PopupChoice(label = "中文年月日 · 2026年9月11日 14:30:05", value = "yyyy年M月d日 HH:mm:ss"),
        PopupChoice(label = "仅年月日 · 2026-09-11", value = "yyyy-MM-dd")
    )
    return if (choices.any { it.value == current }) choices
    else choices + PopupChoice(label = "自定义 · "+ current.ifBlank { "默认时分秒" }, value = current)
}

private fun messageDetailsPositionValues(): Set<String> = setOf(
    HchatExtraSettings.POSITION_MESSAGE_BOTTOM,
    HchatExtraSettings.POSITION_AVATAR_ABOVE,
    HchatExtraSettings.POSITION_AVATAR_BELOW
)

private fun messageDetailsPositionChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice(label = "消息下方", value = HchatExtraSettings.POSITION_MESSAGE_BOTTOM),
    PopupChoice(label = "头像上方", value = HchatExtraSettings.POSITION_AVATAR_ABOVE),
    PopupChoice(label = "头像下方", value = HchatExtraSettings.POSITION_AVATAR_BELOW)
)

private fun messageDetailsPositionLabel(value: String): String = when (value) {
    HchatExtraSettings.POSITION_AVATAR_ABOVE -> "头像上方"
    HchatExtraSettings.POSITION_AVATAR_BELOW -> "头像下方"
    else -> "消息下方"
}

private fun messageDetailsVariables(): List<TemplateVariable> {
    return listOf(
        TemplateVariable("\${time}", "时间"),
        TemplateVariable("\${relativeTime}", "相对时间"),
        TemplateVariable("\${type}", "消息类型（含文件、红包、小程序等）"),
        TemplateVariable("\${appType}", "卡片子类型编号（非卡片为空）"),
        TemplateVariable("\${baseType}", "基础消息类型编号"),
        TemplateVariable("\${direction}", "收发方向"),
        TemplateVariable("\${talker}", "会话 ID"),
        TemplateVariable("\${createTime}", "消息时间戳（毫秒）"),
        TemplateVariable("\${rawAtUserList}", "原始艾特名单"),
        TemplateVariable("\${typeDec}", "类型编号（十进制）"),
        TemplateVariable("\${typeHex}", "类型编号（十六进制）"),
        TemplateVariable("\${msgId}", "本地消息编号"),
        TemplateVariable("\${msgSvrId}", "服务端消息编号"),
        TemplateVariable("\${atUserList}", "艾特对象"),
        TemplateVariable("\${mentionedUsers}", "提及摘要")
    )
}

@Composable
internal fun ProfileIdMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, ProfileIdSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "资料页") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        ProfileIdSettings.KEY_ENABLE,
                        "显示好友/群聊ID",
                        "在好友和群聊资料页显示可点击复制的 ID",
                        ProfileIdSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun HideChatMenuMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HideChatMenuSettings.preferences(context) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(HideChatMenuSettings.KEY_ENABLE, HideChatMenuSettings.DEFAULT_ENABLE)
        )
    }
    var titles by remember {
        mutableStateOf(
            sp.getString(HideChatMenuSettings.KEY_TITLES, HideChatMenuSettings.DEFAULT_TITLES)
                .orEmpty()
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun save() {
        val normalized = HideChatMenuSettings.parseTitles(titles).joinToString(",")
        titles = normalized
        sp.edit()
            .putBoolean(HideChatMenuSettings.KEY_ENABLE, enabled)
            .putString(HideChatMenuSettings.KEY_TITLES, normalized)
            .apply()
        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = { save() },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天菜单") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "隐藏长按菜单",
                        summary = "隐藏聊天消息长按菜单中的指定项目",
                        onCheckedChange = { enabled = it }
                    )
                    if (enabled) {
                        InsetDivider()
                        InputRow(
                            "隐藏菜单项",
                            "输入菜单显示名称，多个项目用逗号、分号或换行分隔",
                            titles,
                            minLines = 2,
                            onValueChange = { titles = it }
                        )
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("恢复默认", "恢复初始菜单名称示例") {
                        titles = HideChatMenuSettings.DEFAULT_TITLES
                        Toast.makeText(context, "已恢复默认，保存后生效", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsEntryMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, SettingsEntrySettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "入口") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SettingsEntrySettings.KEY_PLUS_MENU_ENABLE,
                        "注入加号菜单",
                        "在微信右上角加号菜单中显示 Hchat 入口，重启微信后生效",
                        SettingsEntrySettings.DEFAULT_PLUS_MENU_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        SettingsEntrySettings.KEY_PLUS_LONG_PRESS_ENABLE,
                        "长按加号入口",
                        "长按微信右上角加号打开 Hchat 设置，重启微信后生效",
                        SettingsEntrySettings.DEFAULT_PLUS_LONG_PRESS_ENABLE
                    )
                }
            }
        }
    }
}

private data class FloatingShortcutEditorRequest(
    val original: FloatingShortcutItem?,
    val draft: FloatingShortcutItem
)

private enum class FloatingShortcutRoute {
    MAIN,
    APPEARANCE
}

@Composable
internal fun FloatingShortcutMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FloatingShortcutSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(FloatingShortcutRuntime.isEnabled(context)) }
    var scope by remember {
        mutableStateOf(
            sp.getString(FloatingShortcutSettings.KEY_SCOPE, FloatingShortcutSettings.DEFAULT_SCOPE)
                ?: FloatingShortcutSettings.DEFAULT_SCOPE
        )
    }
    var displayMode by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_DISPLAY_MODE,
                FloatingShortcutSettings.DEFAULT_DISPLAY_MODE
            ) ?: FloatingShortcutSettings.DEFAULT_DISPLAY_MODE
        )
    }
    var expandDirection by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_EXPAND_DIRECTION,
                FloatingShortcutSettings.DEFAULT_EXPAND_DIRECTION
            ) ?: FloatingShortcutSettings.DEFAULT_EXPAND_DIRECTION
        )
    }
    var bubbleIcon by remember {
        mutableStateOf(sp.getString(FloatingShortcutSettings.KEY_BUBBLE_ICON, "").orEmpty())
    }
    var bubbleDarkIcon by remember {
        mutableStateOf(sp.getString(FloatingShortcutSettings.KEY_BUBBLE_DARK_ICON, "").orEmpty())
    }
    var shortcuts by remember { mutableStateOf(FloatingShortcutSettings.loadItems(context)) }
    var editor by remember { mutableStateOf<FloatingShortcutEditorRequest?>(null) }
    var route by remember { mutableStateOf(FloatingShortcutRoute.MAIN) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun saveShortcuts(value: List<FloatingShortcutItem>) {
        shortcuts = value
        FloatingShortcutSettings.saveItems(context, value)
    }

    fun pickIcon(key: String, onSaved: (String) -> Unit) {
        val activity = (context as? Activity) ?: WeChatApis.currentActivity()?.currentActivity()
        if (activity == null) {
            Toast.makeText(context, "当前页面无法打开图片选择器", Toast.LENGTH_SHORT).show()
            return
        }
        FloatingShortcutIconPicker.launch(activity, key) { result ->
            when (result) {
                is FloatingShortcutIconPickResult.Saved -> onSaved(result.path)
                FloatingShortcutIconPickResult.FAILED ->
                    Toast.makeText(context, "图标读取失败", Toast.LENGTH_SHORT).show()
                FloatingShortcutIconPickResult.CANCELLED -> Unit
            }
        }
    }

    fun dismissEditor() {
        val request = editor ?: return
        if (request.draft.iconPath != request.original?.iconPath) {
            FloatingShortcutIconStore.delete(context, request.draft.iconPath)
        }
        if (request.draft.darkIconPath != request.original?.darkIconPath) {
            FloatingShortcutIconStore.delete(context, request.draft.darkIconPath)
        }
        editor = null
    }

    SettingsRouteTransition(
        targetState = route,
        label = "FloatingShortcutRoute",
        depthOf = { if (it == FloatingShortcutRoute.MAIN) 0 else 1 }
    ) { currentRoute ->
        when (currentRoute) {
            FloatingShortcutRoute.MAIN -> PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "悬浮入口") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "悬浮快捷菜单",
                        summary = "在微信页面显示可自由拖动和停放的快捷入口",
                        onCheckedChange = {
                            enabled = it
                            FloatingShortcutRuntime.setEnabled(context, it)
                        }
                    )
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "显示方式") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "显示范围",
                            summary = if (scope == FloatingShortcutSettings.SCOPE_ALL) {
                                "微信所有页面"
                            } else {
                                "仅微信主页"
                            },
                            options = listOf(
                                PopupChoice("仅微信主页", FloatingShortcutSettings.SCOPE_HOME),
                                PopupChoice("所有微信页面", FloatingShortcutSettings.SCOPE_ALL)
                            ),
                            currentValue = scope,
                            onValueChanged = {
                                scope = it
                                sp.edit().putString(FloatingShortcutSettings.KEY_SCOPE, it).apply()
                            }
                        )
                        InsetDivider()
                        PopupChoiceRow(
                            title = "快捷项样式",
                            summary = when (displayMode) {
                                FloatingShortcutSettings.DISPLAY_TEXT -> "仅文字"
                                FloatingShortcutSettings.DISPLAY_BOTH -> "图标和文字"
                                else -> "仅图标"
                            },
                            options = listOf(
                                PopupChoice("仅图标", FloatingShortcutSettings.DISPLAY_ICON),
                                PopupChoice("仅文字", FloatingShortcutSettings.DISPLAY_TEXT),
                                PopupChoice("图标和文字", FloatingShortcutSettings.DISPLAY_BOTH)
                            ),
                            currentValue = displayMode,
                            onValueChanged = {
                                displayMode = it
                                sp.edit().putString(FloatingShortcutSettings.KEY_DISPLAY_MODE, it).apply()
                            }
                        )
                        InsetDivider()
                        PopupChoiceRow(
                            title = "展开方向",
                            summary = when (expandDirection) {
                                FloatingShortcutSettings.EXPAND_DOWN -> "向下展开"
                                FloatingShortcutSettings.EXPAND_LEFT -> "向左展开"
                                FloatingShortcutSettings.EXPAND_RIGHT -> "向右展开"
                                else -> "向上展开"
                            },
                            options = listOf(
                                PopupChoice("向上展开", FloatingShortcutSettings.EXPAND_UP),
                                PopupChoice("向下展开", FloatingShortcutSettings.EXPAND_DOWN),
                                PopupChoice("向左展开", FloatingShortcutSettings.EXPAND_LEFT),
                                PopupChoice("向右展开", FloatingShortcutSettings.EXPAND_RIGHT)
                            ),
                            currentValue = expandDirection,
                            onValueChanged = {
                                expandDirection = it
                                sp.edit().putString(FloatingShortcutSettings.KEY_EXPAND_DIRECTION, it).apply()
                            }
                        )
                        InsetDivider()
                        ActionRow(
                            title = "按钮外观",
                            summary = "设置按钮渐变、大小和菜单名称样式",
                            onClick = { route = FloatingShortcutRoute.APPEARANCE }
                        )
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "主按钮图标") }
                item {
                    SettingsCard {
                        FloatingShortcutIconActionRow(
                            title = "浅色模式图标",
                            iconPath = bubbleIcon,
                            defaultSummary = "未选择时使用内置快捷菜单图标",
                            onSelect = {
                                pickIcon("bubble-${System.nanoTime()}") { path ->
                                    val previous = bubbleIcon
                                    bubbleIcon = path
                                    sp.edit().putString(FloatingShortcutSettings.KEY_BUBBLE_ICON, path).apply()
                                    FloatingShortcutIconStore.delete(context, previous)
                                }
                            },
                            onReset = if (bubbleIcon.isBlank()) null else {
                                {
                                    val previous = bubbleIcon
                                    bubbleIcon = ""
                                    sp.edit().remove(FloatingShortcutSettings.KEY_BUBBLE_ICON).apply()
                                    FloatingShortcutIconStore.delete(context, previous)
                                }
                            }
                        )
                        InsetDivider()
                        FloatingShortcutIconActionRow(
                            title = "深色模式图标",
                            iconPath = bubbleDarkIcon,
                            defaultSummary = "未选择时沿用浅色模式图标",
                            onSelect = {
                                pickIcon("bubble-dark-${System.nanoTime()}") { path ->
                                    val previous = bubbleDarkIcon
                                    bubbleDarkIcon = path
                                    sp.edit().putString(FloatingShortcutSettings.KEY_BUBBLE_DARK_ICON, path).apply()
                                    FloatingShortcutIconStore.delete(context, previous)
                                }
                            },
                            onReset = if (bubbleDarkIcon.isBlank()) null else {
                                {
                                    val previous = bubbleDarkIcon
                                    bubbleDarkIcon = ""
                                    sp.edit().remove(FloatingShortcutSettings.KEY_BUBBLE_DARK_ICON).apply()
                                    FloatingShortcutIconStore.delete(context, previous)
                                }
                            }
                        )
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "快捷项") }
                item {
                    SettingsCard {
                        if (shortcuts.isEmpty()) {
                            Text(
                                text = "暂无快捷项",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            shortcuts.forEachIndexed { index, item ->
                                if (index > 0) InsetDivider()
                                FloatingShortcutSettingsItemRow(
                                    item = item,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < shortcuts.lastIndex,
                                    onEnabledChange = { checked ->
                                        saveShortcuts(shortcuts.toMutableList().apply {
                                            this[index] = item.copy(enabled = checked)
                                        })
                                    },
                                    onMoveUp = {
                                        if (index > 0) {
                                            saveShortcuts(shortcuts.toMutableList().apply {
                                                val moved = removeAt(index)
                                                add(index - 1, moved)
                                            })
                                        }
                                    },
                                    onMoveDown = {
                                        if (index < shortcuts.lastIndex) {
                                            saveShortcuts(shortcuts.toMutableList().apply {
                                                val moved = removeAt(index)
                                                add(index + 1, moved)
                                            })
                                        }
                                    },
                                    onEdit = { editor = FloatingShortcutEditorRequest(item, item) },
                                    onDelete = {
                                        FloatingShortcutIconStore.delete(context, item.iconPath)
                                        FloatingShortcutIconStore.delete(context, item.darkIconPath)
                                        saveShortcuts(shortcuts.filterNot { it.id == item.id })
                                    }
                                )
                            }
                        }
                        InsetDivider()
                        ActionRow(
                            title = "新增快捷项",
                            summary = "添加微信页面或模块设置快捷入口",
                            onClick = {
                                editor = FloatingShortcutEditorRequest(
                                    original = null,
                                    draft = FloatingShortcutItem(
                                        id = "shortcut_${System.currentTimeMillis()}_${shortcuts.size}",
                                        title = "",
                                        actionType = FloatingShortcutSettings.ACTION_ACTIVITY,
                                        target = ""
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
            FloatingShortcutRoute.APPEARANCE -> FloatingShortcutAppearanceMiuixPage(
                context = context,
                onBack = { route = FloatingShortcutRoute.MAIN }
            )
        }
    }

    editor?.let { request ->
        val draft = request.draft
        val valid = draft.title.isNotBlank() &&
            (draft.actionType != FloatingShortcutSettings.ACTION_ACTIVITY || draft.target.isNotBlank())
        WindowDialog(
            show = true,
            title = if (request.original == null) "新增快捷项" else "编辑快捷项",
            onDismissRequest = ::dismissEditor,
            content = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    InputRow(
                        title = "名称",
                        summary = "显示在展开菜单中的名称",
                        value = draft.title,
                        onValueChange = { editor = request.copy(draft = draft.copy(title = it)) }
                    )
                    PopupChoiceRow(
                        title = "动作类型",
                        summary = if (draft.actionType == FloatingShortcutSettings.ACTION_MODULE_SETTINGS) {
                            "打开 Hchat 设置"
                        } else {
                            "打开微信页面"
                        },
                        options = listOf(
                            PopupChoice("微信页面", FloatingShortcutSettings.ACTION_ACTIVITY),
                            PopupChoice("模块设置", FloatingShortcutSettings.ACTION_MODULE_SETTINGS)
                        ),
                        currentValue = draft.actionType,
                        onValueChanged = { type ->
                            editor = request.copy(
                                draft = draft.copy(
                                    actionType = type,
                                    target = if (type == FloatingShortcutSettings.ACTION_ACTIVITY) draft.target else ""
                                )
                            )
                        }
                    )
                    if (draft.actionType == FloatingShortcutSettings.ACTION_ACTIVITY) {
                        InputRow(
                            title = "Activity 类名",
                            summary = "填写微信页面的完整 Activity 类名",
                            value = draft.target,
                            onValueChange = { editor = request.copy(draft = draft.copy(target = it)) }
                        )
                    }
                    FloatingShortcutIconActionRow(
                        title = "浅色模式图标",
                        iconPath = draft.iconPath,
                        defaultSummary = "未选择时使用内置图标",
                        onSelect = {
                            pickIcon("item-${draft.id}-${System.nanoTime()}") { path ->
                                if (draft.iconPath != request.original?.iconPath) {
                                    FloatingShortcutIconStore.delete(context, draft.iconPath)
                                }
                                editor = request.copy(draft = draft.copy(iconPath = path))
                            }
                        },
                        onReset = if (draft.iconPath.isBlank()) null else {
                            {
                                if (draft.iconPath != request.original?.iconPath) {
                                    FloatingShortcutIconStore.delete(context, draft.iconPath)
                                }
                                editor = request.copy(draft = draft.copy(iconPath = ""))
                            }
                        }
                    )
                    FloatingShortcutIconActionRow(
                        title = "深色模式图标",
                        iconPath = draft.darkIconPath,
                        defaultSummary = "未选择时沿用浅色模式图标",
                        onSelect = {
                            pickIcon("item-dark-${draft.id}-${System.nanoTime()}") { path ->
                                if (draft.darkIconPath != request.original?.darkIconPath) {
                                    FloatingShortcutIconStore.delete(context, draft.darkIconPath)
                                }
                                editor = request.copy(draft = draft.copy(darkIconPath = path))
                            }
                        },
                        onReset = if (draft.darkIconPath.isBlank()) null else {
                            {
                                if (draft.darkIconPath != request.original?.darkIconPath) {
                                    FloatingShortcutIconStore.delete(context, draft.darkIconPath)
                                }
                                editor = request.copy(draft = draft.copy(darkIconPath = ""))
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = ::dismissEditor,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        TextButton(
                            text = "保存",
                            onClick = {
                                if (!valid) {
                                    Toast.makeText(context, "请填写完整的快捷项信息", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                val saved = draft.copy(
                                    title = draft.title.trim(),
                                    target = draft.target.trim()
                                )
                                val original = request.original
                                if (original != null && original.iconPath != saved.iconPath) {
                                    FloatingShortcutIconStore.delete(context, original.iconPath)
                                }
                                if (original != null && original.darkIconPath != saved.darkIconPath) {
                                    FloatingShortcutIconStore.delete(context, original.darkIconPath)
                                }
                                val next = shortcuts.toMutableList()
                                val index = next.indexOfFirst { it.id == saved.id }
                                if (index >= 0) next[index] = saved else next.add(saved)
                                saveShortcuts(next)
                                editor = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun FloatingShortcutAppearanceMiuixPage(
    context: Context,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FloatingShortcutSettings.PREFS_NAME) }
    var bubbleSize by remember {
        mutableStateOf(
            sp.getInt(
                FloatingShortcutSettings.KEY_BUBBLE_SIZE,
                FloatingShortcutSettings.DEFAULT_BUBBLE_SIZE
            ).coerceIn(FloatingShortcutSettings.MIN_BUTTON_SIZE, FloatingShortcutSettings.MAX_BUTTON_SIZE)
        )
    }
    var bubbleColor by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_BUBBLE_COLOR,
                FloatingShortcutSettings.DEFAULT_BUBBLE_COLOR
            ) ?: FloatingShortcutSettings.DEFAULT_BUBBLE_COLOR
        )
    }
    var actionSize by remember {
        mutableStateOf(
            sp.getInt(
                FloatingShortcutSettings.KEY_ACTION_SIZE,
                FloatingShortcutSettings.DEFAULT_ACTION_SIZE
            ).coerceIn(FloatingShortcutSettings.MIN_BUTTON_SIZE, FloatingShortcutSettings.MAX_BUTTON_SIZE)
        )
    }
    var actionColor by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_ACTION_COLOR,
                FloatingShortcutSettings.DEFAULT_ACTION_COLOR
            ) ?: FloatingShortcutSettings.DEFAULT_ACTION_COLOR
        )
    }
    var labelTextSize by remember {
        mutableStateOf(
            sp.getInt(
                FloatingShortcutSettings.KEY_LABEL_TEXT_SIZE,
                FloatingShortcutSettings.DEFAULT_LABEL_TEXT_SIZE
            ).coerceIn(
                FloatingShortcutSettings.MIN_LABEL_TEXT_SIZE,
                FloatingShortcutSettings.MAX_LABEL_TEXT_SIZE
            )
        )
    }
    var labelColor by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_LABEL_COLOR,
                FloatingShortcutSettings.DEFAULT_LABEL_COLOR
            ) ?: FloatingShortcutSettings.DEFAULT_LABEL_COLOR
        )
    }
    val currentBubbleSize by rememberUpdatedState(bubbleSize)
    val currentBubbleColor by rememberUpdatedState(bubbleColor)
    val currentActionSize by rememberUpdatedState(actionSize)
    val currentActionColor by rememberUpdatedState(actionColor)
    val currentLabelTextSize by rememberUpdatedState(labelTextSize)
    val currentLabelColor by rememberUpdatedState(labelColor)
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun persistAppearance() {
        sp.edit()
            .putInt(FloatingShortcutSettings.KEY_BUBBLE_SIZE, currentBubbleSize)
            .putString(FloatingShortcutSettings.KEY_BUBBLE_COLOR, currentBubbleColor.trim())
            .putInt(FloatingShortcutSettings.KEY_ACTION_SIZE, currentActionSize)
            .putString(FloatingShortcutSettings.KEY_ACTION_COLOR, currentActionColor.trim())
            .putInt(FloatingShortcutSettings.KEY_LABEL_TEXT_SIZE, currentLabelTextSize)
            .putString(FloatingShortcutSettings.KEY_LABEL_COLOR, currentLabelColor.trim())
            .apply()
    }

    DisposableEffect(sp) {
        onDispose(::persistAppearance)
    }

    PageScaffold(
        title = "按钮外观",
        largeTitle = "按钮外观",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "返回",
                onPrimaryClick = {
                    persistAppearance()
                    onBack()
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "主按钮") }
            item {
                SettingsCard {
                    FloatingShortcutSizeSlider("主按钮大小", bubbleSize) { bubbleSize = it }
                    InsetDivider()
                    ColorPickerRow(
                        title = "主按钮颜色",
                        summary = "圆形背景色",
                        value = bubbleColor,
                        onReset = { bubbleColor = FloatingShortcutSettings.DEFAULT_BUBBLE_COLOR }
                    ) { bubbleColor = it.take(19) }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "副按钮") }
            item {
                SettingsCard {
                    FloatingShortcutSizeSlider("副按钮大小", actionSize) { actionSize = it }
                    InsetDivider()
                    ColorPickerRow(
                        title = "副按钮颜色",
                        summary = "留空时跟随深浅色模式",
                        value = actionColor,
                        onReset = { actionColor = FloatingShortcutSettings.DEFAULT_ACTION_COLOR }
                    ) { actionColor = it.take(19) }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "菜单名称") }
            item {
                SettingsCard {
                    FloatingShortcutTextSizeSlider("菜单名称大小", labelTextSize) { labelTextSize = it }
                    InsetDivider()
                    ColorPickerRow(
                        title = "菜单名称颜色",
                        summary = "留空时跟随深浅色模式",
                        value = labelColor,
                        onReset = { labelColor = FloatingShortcutSettings.DEFAULT_LABEL_COLOR }
                    ) { labelColor = it.take(19) }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "重置") }
            item {
                SettingsCard {
                    ActionRow(
                        title = "恢复默认外观",
                        summary = "恢复按钮和菜单名称的默认外观",
                        onClick = {
                            bubbleSize = FloatingShortcutSettings.DEFAULT_BUBBLE_SIZE
                            bubbleColor = FloatingShortcutSettings.DEFAULT_BUBBLE_COLOR
                            actionSize = FloatingShortcutSettings.DEFAULT_ACTION_SIZE
                            actionColor = FloatingShortcutSettings.DEFAULT_ACTION_COLOR
                            labelTextSize = FloatingShortcutSettings.DEFAULT_LABEL_TEXT_SIZE
                            labelColor = FloatingShortcutSettings.DEFAULT_LABEL_COLOR
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingShortcutTextSizeSlider(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "$title ${value}sp",
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            valueRange = FloatingShortcutSettings.MIN_LABEL_TEXT_SIZE.toFloat()..
                FloatingShortcutSettings.MAX_LABEL_TEXT_SIZE.toFloat(),
            steps = FloatingShortcutSettings.MAX_LABEL_TEXT_SIZE -
                FloatingShortcutSettings.MIN_LABEL_TEXT_SIZE - 1,
            showKeyPoints = true,
            keyPoints = listOf(10f, 12f, 14f, 16f, 18f, 20f, 24f)
        )
    }
}

@Composable
private fun FloatingShortcutSizeSlider(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "$title ${value}dp",
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            valueRange = FloatingShortcutSettings.MIN_BUTTON_SIZE.toFloat()..
                FloatingShortcutSettings.MAX_BUTTON_SIZE.toFloat(),
            steps = FloatingShortcutSettings.MAX_BUTTON_SIZE -
                FloatingShortcutSettings.MIN_BUTTON_SIZE - 1,
            showKeyPoints = true,
            keyPoints = listOf(36f, 44f, 52f, 60f, 64f)
        )
    }
}

@Composable
private fun FloatingShortcutSettingsItemRow(
    item: FloatingShortcutItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FloatingShortcutIconPreview(
                if (isSystemInDarkTheme() && item.darkIconPath.isNotBlank()) item.darkIconPath else item.iconPath,
                item.title,
                defaultGlyph = FloatingShortcutGlyphs.forItem(item)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = item.title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (item.actionType) {
                        FloatingShortcutSettings.ACTION_MODULE_SETTINGS -> "模块设置"
                        else -> item.target
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = item.enabled, onCheckedChange = onEnabledChange)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingShortcutItemAction(NavIcons.MoveUp, "上移", canMoveUp, onMoveUp)
            FloatingShortcutItemAction(NavIcons.MoveDown, "下移", canMoveDown, onMoveDown)
            FloatingShortcutItemAction(NavIcons.Edit, "编辑", true, onEdit)
            FloatingShortcutItemAction(NavIcons.Delete, "删除", true, onDelete, destructive = true)
        }
    }
}

@Composable
private fun FloatingShortcutItemAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val color = when {
        !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
        destructive -> Color(0xFFD93025)
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Image(
        imageVector = icon,
        contentDescription = description,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.padding(start = 4.dp).size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(7.dp)
    )
}

@Composable
private fun FloatingShortcutIconActionRow(
    title: String,
    iconPath: String,
    defaultSummary: String,
    onSelect: () -> Unit,
    onReset: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingShortcutIconPreview(iconPath, title)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(
                text = if (iconPath.isBlank()) defaultSummary else File(iconPath).name,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            text = "选择",
            onClick = onSelect,
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        if (onReset != null) {
            TextButton(
                text = "默认",
                onClick = onReset,
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun FloatingShortcutIconPreview(
    path: String,
    description: String,
    defaultGlyph: FloatingShortcutGlyph? = null
) {
    val previewSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val bitmap = remember(path, previewSizePx) {
        path.takeIf { it.isNotBlank() }?.let { decodeFavoriteImage(it, previewSizePx) }
    }
    val fallbackTint = MiuixTheme.colorScheme.onSecondaryVariant.toArgb()
    Box(
        modifier = Modifier.size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (defaultGlyph != null) {
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
                },
                update = { imageView ->
                    imageView.contentDescription = description
                    imageView.setImageDrawable(FloatingShortcutGlyphDrawable(defaultGlyph, fallbackTint))
                },
                modifier = Modifier.size(24.dp)
            )
        } else {
            Image(
                imageVector = NavIcons.Practical,
                contentDescription = description,
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSecondaryVariant),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun QuickMarkReadMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickMarkReadSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "快捷已读") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuickMarkReadSettings.KEY_DRAG_READ,
                        "拖拽已读",
                        "在微信底部未读角标向上拖动后，清空全部会话未读",
                        QuickMarkReadSettings.DEFAULT_DRAG_READ
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QuickMarkReadSettings.KEY_PLUS_MENU_READ,
                        "注入加号菜单已读",
                        "在右上角加号菜单添加“全部已读”，重启微信后生效",
                        QuickMarkReadSettings.DEFAULT_PLUS_MENU_READ
                    )
                }
            }
        }
    }
}

@Composable
internal fun WeChatKeepAliveMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, WeChatKeepAliveSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_ENABLE, WeChatKeepAliveSettings.DEFAULT_ENABLE)) }
    var foregroundService by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_FOREGROUND_SERVICE, WeChatKeepAliveSettings.DEFAULT_FOREGROUND_SERVICE)) }
    var wakeLock by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_WAKE_LOCK, WeChatKeepAliveSettings.DEFAULT_WAKE_LOCK)) }
    var rootWhitelist by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_ROOT_DOZE_WHITELIST, WeChatKeepAliveSettings.DEFAULT_ROOT_DOZE_WHITELIST)) }
    var watchdog by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_WATCHDOG, WeChatKeepAliveSettings.DEFAULT_WATCHDOG)) }
    var rootAppOps by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_ROOT_APP_OPS, WeChatKeepAliveSettings.DEFAULT_ROOT_APP_OPS)) }
    var networkHeartbeat by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_NETWORK_HEARTBEAT, WeChatKeepAliveSettings.DEFAULT_NETWORK_HEARTBEAT)) }
    var ignoringBattery by remember { mutableStateOf(WeChatKeepAliveRuntime.isIgnoringBatteryOptimizations(context)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun saveBoolean(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
        WeChatKeepAliveRuntime.apply(context, allowForegroundStart = true)
    }

    fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show() }
    }

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "强保活") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用微信强保活",
                        summary = "开启后尝试保持微信息屏运行",
                        onCheckedChange = {
                            enabled = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_ENABLE, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = foregroundService,
                        title = "前台服务保活",
                        summary = "看门狗或网络监测启用时显示常驻通知；系统仍可限制后台启动或结束服务",
                        onCheckedChange = {
                            foregroundService = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_FOREGROUND_SERVICE, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = wakeLock,
                        title = "WakeLock 保活",
                        summary = "息屏后保持 CPU 运行，耗电会增加",
                        onCheckedChange = {
                            wakeLock = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_WAKE_LOCK, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = rootWhitelist,
                        title = "Root Doze 白名单",
                        summary = "有 Root 时执行 deviceidle 白名单命令",
                        onCheckedChange = {
                            rootWhitelist = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_ROOT_DOZE_WHITELIST, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = rootAppOps,
                        title = "Root 后台限制放行",
                        summary = "有 Root 时放行微信后台运行相关 AppOps",
                        onCheckedChange = {
                            rootAppOps = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_ROOT_APP_OPS, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = watchdog,
                        title = "看门狗拉起微信",
                        summary = "检查微信主进程；后台启动可能受系统限制，有 Root 时只尝试启动两个受支持的微信包名",
                        onCheckedChange = {
                            watchdog = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_WATCHDOG, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = networkHeartbeat,
                        title = "网络心跳保活",
                        summary = "定时发起轻量网络请求保持链路活跃",
                        onCheckedChange = {
                            networkHeartbeat = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_NETWORK_HEARTBEAT, it)
                        }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "系统限制") }
            item {
                SettingsCard {
                    InfoRow(
                        "监测服务降级",
                        "Android 可能拒绝跨应用绑定或后台启动前台服务；此时看门狗和网络心跳不运行，本地 WakeLock / Root 选项仍按系统结果生效。"
                    )
                    InsetDivider()
                    InfoRow(
                        "电池优化白名单",
                        if (ignoringBattery) "微信已在白名单" else "微信可能仍受系统省电影响"
                    )
                    InsetDivider()
                    ActionRow("打开电池优化设置", "建议把微信设置为不限制") {
                        openBatterySettings()
                    }
                    InsetDivider()
                    ActionRow("刷新状态", if (ignoringBattery) "当前已忽略优化" else "当前未忽略优化") {
                        ignoringBattery = WeChatKeepAliveRuntime.isIgnoringBatteryOptimizations(context)
                        Toast.makeText(context, "状态已刷新", Toast.LENGTH_SHORT).show()
                    }
                    InsetDivider()
                    ActionRow("立即应用保活", "应用 WakeLock / Root 选项；在前台请求启动可见监测通知") {
                        WeChatKeepAliveRuntime.apply(context, allowForegroundStart = true)
                        ignoringBattery = WeChatKeepAliveRuntime.isIgnoringBatteryOptimizations(context)
                        Toast.makeText(context, "已提交配置；监测服务仍受 Android 系统限制", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
