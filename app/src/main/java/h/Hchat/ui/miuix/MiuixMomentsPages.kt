package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.items.hidemenu.HideChatMenuSettings
import h.Hchat.hooks.items.backgroundassist.BackgroundRunAssistRuntime
import h.Hchat.hooks.items.backgroundassist.BackgroundRunAssistSettings
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
import h.Hchat.hooks.items.moments.MomentsUploadTailPresets
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
                onSelectionChange = { selected ->
                    val ids = selected.filter { it.isNotBlank() }.toSet()
                    if (current.listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) {
                        blacklist = ids
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_BLACKLIST, formatIds(ids)).apply()
                    } else {
                        whitelist = ids
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_WHITELIST, formatIds(ids)).apply()
                    }
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

    fun saveRandomRange() {
        val min = randomMin.toIntOrNull()?.takeIf { it >= 0 } ?: return
        val max = randomMax.toIntOrNull()?.takeIf { it >= min } ?: return
        sp.edit().putInt(MomentsAutoLikeSettings.KEY_RANDOM_MIN_SECONDS, min).putInt(MomentsAutoLikeSettings.KEY_RANDOM_MAX_SECONDS, max).apply()
    }
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
                    InsetDivider()
                    SwitchRow(likeSelf, "点赞自己的朋友圈", "自己的朋友圈不受好友名单限制") {
                        likeSelf = it
                        sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_LIKE_SELF, it).apply()
                    }
                }
            }
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
                        it.toIntOrNull()?.takeIf { value -> value >= 0 }?.let { value ->
                            sp.edit()
                                .putInt(MomentsAutoLikeSettings.KEY_DAILY_LIKE_LIMIT, value)
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
                            it.toIntOrNull()?.takeIf { value -> value >= 0 }?.let { value ->
                                sp.edit().putInt(MomentsAutoLikeSettings.KEY_FIXED_DELAY_SECONDS, value).apply()
                            }
                        }
                    } else {
                        InsetDivider()
                        NumberInputRow("最短等待", "单位秒，最少 0 秒", randomMin) {
                            randomMin = it
                            saveRandomRange()
                        }
                        InsetDivider()
                        NumberInputRow("最长等待", "单位秒，不能小于最短等待", randomMax) {
                            randomMax = it
                            saveRandomRange()
                        }
                        if (randomMin.toIntOrNull()?.let { min -> min >= 0 && randomMax.toIntOrNull()?.let { it >= min } == true } != true) {
                            InsetDivider(); InfoRow("延迟范围", "请输入有效范围，保留上次有效配置")
                        }
                    }
                    InsetDivider()
                    SwitchRow(timeWindow, "限制运行时段", "支持跨零点时段") {
                        timeWindow = it
                        sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_TIME_WINDOW_ENABLE, it).apply()
                    }
                    InsetDivider()
                    TimeOfDayPickerRow("开始时间", startTime) {
                        startTime = it
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_START_TIME, startTime).putString(MomentsAutoLikeSettings.KEY_END_TIME, endTime).apply()
                    }
                    InsetDivider()
                    TimeOfDayPickerRow("结束时间", endTime) {
                        endTime = it
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_START_TIME, startTime).putString(MomentsAutoLikeSettings.KEY_END_TIME, endTime).apply()
                    }

                    InsetDivider()
                    NumberInputRow("发布时间限制", "仅点赞发布后指定小时内的朋友圈，最少 1 小时", maxAge) {
                        maxAge = it
                        it.toIntOrNull()?.takeIf { it >= 1 }?.let { value ->
                            sp.edit().putInt(MomentsAutoLikeSettings.KEY_MAX_AGE_HOURS, value).apply()
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
                    InsetDivider()
                    InputRow("文字排除关键词", "多个关键词用逗号或换行分隔", keywordsText, minLines = 2) {
                        keywordsText = it
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_TEXT, it).apply()
                    }
                    InsetDivider()
                    SwitchRow(keywordImage, "图文朋友圈关键词", "开启后按关键词排除") {
                        keywordImage = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_IMAGE, it).apply()
                    }
                    InsetDivider()
                    InputRow("图文排除关键词", "多个关键词用逗号或换行分隔", keywordsImageText, minLines = 2) {
                        keywordsImageText = it
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_IMAGE_TEXT, it).apply()
                    }
                    InsetDivider()
                    SwitchRow(keywordVideo, "视文朋友圈关键词", "开启后按关键词排除") {
                        keywordVideo = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_VIDEO, it).apply()
                    }
                    InsetDivider()
                    InputRow("视文排除关键词", "多个关键词用逗号或换行分隔", keywordsVideoText, minLines = 2) {
                        keywordsVideoText = it
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_VIDEO_TEXT, it).apply()
                    }
                    InsetDivider()
                    SwitchRow(keywordCard, "链接/卡片朋友圈关键词", "开启后按关键词排除") {
                        keywordCard = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_CARD, it).apply()
                    }
                    InsetDivider()
                    InputRow("链接/卡片排除关键词", "多个关键词用逗号或换行分隔", keywordsCardText, minLines = 2) {
                        keywordsCardText = it
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_CARD_TEXT, it).apply()
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
                        }
                    }

                    if (logEnabled || logs.isNotBlank()) {
                        InsetDivider()
                        ActionRow("清空日志", "") {
                            sp.edit().remove(MomentsAutoLikeSettings.KEY_LOGS).apply()
                            logs = ""
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
                onSelectionChange = { selected ->
                    val ids = selected.filter { it.isNotBlank() }.toSet()
                    if (current.listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) {
                        blacklist = ids
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_BLACKLIST, formatIds(ids)).apply()
                    } else {
                        whitelist = ids
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_WHITELIST, formatIds(ids)).apply()
                    }
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

    fun saveRandomRange() {
        val min = randomMin.toIntOrNull()?.takeIf { it >= 0 } ?: return
        val max = randomMax.toIntOrNull()?.takeIf { it >= min } ?: return
        sp.edit().putInt(MomentsAutoCommentSettings.KEY_RANDOM_MIN_SECONDS, min).putInt(MomentsAutoCommentSettings.KEY_RANDOM_MAX_SECONDS, max).apply()
    }
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
                        it.toIntOrNull()?.takeIf { value -> value >= 0 }?.let { value ->
                            sp.edit()
                                .putInt(MomentsAutoCommentSettings.KEY_DAILY_COMMENT_LIMIT, value)
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
                            it.toIntOrNull()?.takeIf { value -> value >= 0 }?.let { value ->
                                sp.edit().putInt(MomentsAutoCommentSettings.KEY_FIXED_DELAY_SECONDS, value).apply()
                            }
                        }
                    } else {
                        InsetDivider()
                        NumberInputRow("最短等待", "单位秒，最少 0 秒", randomMin) {
                            randomMin = it
                            saveRandomRange()
                        }
                        InsetDivider()
                        NumberInputRow("最长等待", "单位秒，不能小于最短等待", randomMax) {
                            randomMax = it
                            saveRandomRange()
                        }
                        if (randomMin.toIntOrNull()?.let { min -> min >= 0 && randomMax.toIntOrNull()?.let { it >= min } == true } != true) {
                            InsetDivider(); InfoRow("延迟范围", "请输入有效范围，保留上次有效配置")
                        }
                    }
                    InsetDivider()
                    SwitchRow(timeWindow, "限制运行时段", "支持跨零点时段") {
                        timeWindow = it
                        sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_TIME_WINDOW_ENABLE, it).apply()
                    }
                    InsetDivider()
                    TimeOfDayPickerRow("开始时间", startTime) {
                        startTime = it
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_START_TIME, startTime).putString(MomentsAutoCommentSettings.KEY_END_TIME, endTime).apply()
                    }
                    InsetDivider()
                    TimeOfDayPickerRow("结束时间", endTime) {
                        endTime = it
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_START_TIME, startTime).putString(MomentsAutoCommentSettings.KEY_END_TIME, endTime).apply()
                    }

                    InsetDivider()
                    NumberInputRow("发布时间限制", "仅评论发布后指定小时内的朋友圈，最少 1 小时", maxAge) {
                        maxAge = it
                        it.toIntOrNull()?.takeIf { it >= 1 }?.let { value ->
                            sp.edit().putInt(MomentsAutoCommentSettings.KEY_MAX_AGE_HOURS, value).apply()
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
                    InsetDivider()
                    InputRow("文字排除关键词", "多个关键词用逗号或换行分隔", keywordsText, minLines = 2) {
                        keywordsText = it
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_TEXT, it).apply()
                    }
                    InsetDivider()
                    SwitchRow(keywordImage, "图文朋友圈关键词", "开启后按关键词排除") {
                        keywordImage = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_IMAGE, it).apply()
                    }
                    InsetDivider()
                    InputRow("图文排除关键词", "多个关键词用逗号或换行分隔", keywordsImageText, minLines = 2) {
                        keywordsImageText = it
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_IMAGE_TEXT, it).apply()
                    }
                    InsetDivider()
                    SwitchRow(keywordVideo, "视文朋友圈关键词", "开启后按关键词排除") {
                        keywordVideo = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_VIDEO, it).apply()
                    }
                    InsetDivider()
                    InputRow("视文排除关键词", "多个关键词用逗号或换行分隔", keywordsVideoText, minLines = 2) {
                        keywordsVideoText = it
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_VIDEO_TEXT, it).apply()
                    }
                    InsetDivider()
                    SwitchRow(keywordCard, "链接/卡片朋友圈关键词", "开启后按关键词排除") {
                        keywordCard = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_CARD, it).apply()
                    }
                    InsetDivider()
                    InputRow("链接/卡片排除关键词", "多个关键词用逗号或换行分隔", keywordsCardText, minLines = 2) {
                        keywordsCardText = it
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_CARD_TEXT, it).apply()
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
                        }
                    }

                    if (logEnabled || logs.isNotBlank()) {
                        InsetDivider()
                        ActionRow("清空日志", "") {
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
                onSelectionChange = { selected ->
                    targets = selected.filter { it.isNotBlank() }.toSet()
                    sp.edit().putString(MomentsAutoForwardSettings.KEY_TARGETS, formatIds(targets)).apply()
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

    fun saveRandomRange() {
        val min = randomMin.toIntOrNull()?.takeIf { it >= 0 } ?: return
        val max = randomMax.toIntOrNull()?.takeIf { it >= min } ?: return
        sp.edit().putInt(MomentsAutoForwardSettings.KEY_RANDOM_MIN_SECONDS, min).putInt(MomentsAutoForwardSettings.KEY_RANDOM_MAX_SECONDS, max).apply()
    }
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
                            it.toIntOrNull()?.takeIf { value -> value >= 0 }?.let { value ->
                                sp.edit()
                                    .putInt(
                                        MomentsAutoForwardSettings.KEY_FIXED_DELAY_SECONDS,
                                        value
                                    )
                                    .apply()
                            }
                        }
                    } else {
                        InsetDivider()
                        NumberInputRow("最短等待", "单位秒，最少 0 秒", randomMin) {
                            randomMin = it
                            saveRandomRange()
                        }
                        InsetDivider()
                        NumberInputRow("最长等待", "单位秒，不能小于最短等待", randomMax) {
                            randomMax = it
                            saveRandomRange()
                        }
                        if (randomMin.toIntOrNull()?.let { min -> min >= 0 && randomMax.toIntOrNull()?.let { it >= min } == true } != true) {
                            InsetDivider(); InfoRow("延迟范围", "请输入有效范围，保留上次有效配置")
                        }
                    }
                    InsetDivider()
                    NumberInputRow("每日转发上限", "每天最多转发的朋友圈数量，0 表示不限制", dailyLimit) {
                        dailyLimit = it
                        it.toIntOrNull()?.takeIf { value -> value >= 0 }?.let { value ->
                            sp.edit()
                                .putInt(MomentsAutoForwardSettings.KEY_DAILY_LIMIT, value)
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
                        }
                    }

                    if (logEnabled || logs.isNotBlank()) {
                        InsetDivider()
                        ActionRow("清空日志", "") {
                            sp.edit().remove(MomentsAutoForwardSettings.KEY_LOGS).apply()
                            logs = ""
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
    var rules by remember { mutableStateOf(initialRules) }
    var savedRules by remember { mutableStateOf(initialRules) }
    var newRule by remember { mutableStateOf<KeywordReplacementRule?>(null) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun updateRule(index: Int, update: (KeywordReplacementRule) -> KeywordReplacementRule) {
        if (index !in rules.indices) return
        rules = rules.toMutableList().also { next -> next[index] = update(next[index]) }
        val changed = rules[index].copy(keyword = rules[index].keyword.trim())
        val duplicate = savedRules.withIndex().any { (otherIndex, other) ->
            otherIndex != index && other.keyword.equals(changed.keyword, ignoreCase = true)
        }
        if (changed.keyword.isNotEmpty() && !duplicate) {
            savedRules = savedRules.toMutableList().also { it[index] = changed }
            onSave(savedRules)
        }
    }

    PageScaffold(
        title = "替换规则",
        largeTitle = "替换规则",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
                                if (rule.keyword.isBlank()) "原关键词不能为空，保留上次有效配置" else if (savedRules.withIndex().any { (otherIndex, other) -> otherIndex != index && other.keyword.equals(rule.keyword.trim(), ignoreCase = true) }) "原关键词重复，保留上次有效配置" else "匹配时不区分大小写",
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
                                savedRules = savedRules.toMutableList().also { it.removeAt(index) }
                                onSave(savedRules)
                            }
                        }
                    }
                }
            }
            newRule?.let { pending ->
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "新规则") }
                item {
                    SettingsCard {
                        InputRow("原关键词", "匹配时不区分大小写", pending.keyword) { newRule = pending.copy(keyword = it) }
                        InsetDivider()
                        InputRow("替换内容", "留空表示删除原关键词", pending.replacement) { newRule = pending.copy(replacement = it) }
                        InsetDivider()
                        ActionRow("放弃新增", "移除尚未完成的草稿") { newRule = null }
                    }
                }
            }

            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ContentActionRow(if (newRule == null) "新增" else "完成") {
                        val pending = newRule
                        if (pending == null) {
                            newRule = KeywordReplacementRule("", "")
                        } else {
                            val normalized = pending.copy(keyword = pending.keyword.trim())
                            when {
                                normalized.keyword.isEmpty() -> Toast.makeText(context, "原关键词不能为空", Toast.LENGTH_SHORT).show()
                                savedRules.any { it.keyword.equals(normalized.keyword, ignoreCase = true) } -> Toast.makeText(context, "存在重复的原关键词", Toast.LENGTH_SHORT).show()
                                else -> {
                                    rules = rules + normalized
                                    savedRules = savedRules + normalized
                                    onSave(savedRules)
                                    newRule = null
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SmallTitle(text = "后台刷新") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈自动刷新", "按设定间隔获取新的朋友圈内容") {
                        enabled = it
                        sp.edit().putBoolean(MomentsAutoRefreshSettings.KEY_ENABLE, it).apply()
                    }
                    InsetDivider()
                    NumberInputRow("刷新间隔", "单位秒，最少 0 秒", interval) {
                        interval = it
                        it.toIntOrNull()?.takeIf { value -> value >= 0 }?.let { value ->
                            sp.edit().putInt(MomentsAutoRefreshSettings.KEY_INTERVAL_SECONDS, value).apply()
                        }
                    }
                    InsetDivider()
                    SwitchRow(timeWindow, "限制刷新时段", "支持跨零点时段") {
                        timeWindow = it
                        sp.edit().putBoolean(MomentsAutoRefreshSettings.KEY_TIME_WINDOW_ENABLE, it).apply()
                    }
                    InsetDivider()
                    TimeOfDayPickerRow("开始时间", startTime) {
                        startTime = it
                        sp.edit().putString(MomentsAutoRefreshSettings.KEY_START_TIME, startTime).putString(MomentsAutoRefreshSettings.KEY_END_TIME, endTime).apply()
                    }
                    InsetDivider()
                    TimeOfDayPickerRow("结束时间", endTime) {
                        endTime = it
                        sp.edit().putString(MomentsAutoRefreshSettings.KEY_START_TIME, startTime).putString(MomentsAutoRefreshSettings.KEY_END_TIME, endTime).apply()
                    }

                }
            }
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
                onSelectionChange = { selected ->
                    targets = selected.filter { it.isNotBlank() }.toSet()
                    sp.edit()
                        .putString(MomentsContactFilterSettings.KEY_TARGETS, formatIds(targets))
                        .apply()
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
                        sp.edit().putBoolean(MomentsBottomDetailSettings.KEY_ENABLE, it).apply()
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "格式") }
            item {
                SettingsCard {
                    VariableInputRow(
                        title = "文本格式",
                        summary = "留空使用默认格式",
                        value = textFormat,
                        variables = momentsBottomDetailTemplateVariables,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(MomentsBottomDetailSettings.KEY_TEXT_FORMAT, MomentsBottomDetailSettings.DEFAULT_TEXT_FORMAT).apply() },
                        onValueChange = {
                            textFormat = it
                            if (it.isNotBlank()) sp.edit().putString(MomentsBottomDetailSettings.KEY_TEXT_FORMAT, MomentsBottomDetailSettings.normalizeTextFormat(it)).apply()
                        }
                    )
                    InsetDivider()
                    InputRow(
                        "时间格式",
                        if (timeFormat.isNotBlank() && !MomentsBottomDetailSettings.isValidTimeFormat(timeFormat)) "时间格式无效；保留上次保存的值" else "使用日期格式，例如 yyyy-MM-dd HH:mm:ss",
                        timeFormat,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(MomentsBottomDetailSettings.KEY_TIME_FORMAT, MomentsBottomDetailSettings.DEFAULT_TIME_FORMAT).apply() }
                    ) {
                        timeFormat = it
                        if (it.isNotBlank() && MomentsBottomDetailSettings.isValidTimeFormat(it)) sp.edit().putString(MomentsBottomDetailSettings.KEY_TIME_FORMAT, MomentsBottomDetailSettings.normalizeTimeFormat(it)).apply()
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = hideGroupIcon,
                        title = "隐藏可见范围",
                        summary = "隐藏朋友圈底部的可见范围图标"
                    ) {
                        hideGroupIcon = it
                        sp.edit().putBoolean(MomentsBottomDetailSettings.KEY_HIDE_GROUP_ICON, it).apply()
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
                onSelectionChange = { selected ->
                    targets = selected.filter { it.isNotBlank() }.toSet()
                    sp.edit().putString(MomentsPostNotificationSettings.KEY_TARGETS, formatIds(targets)).apply()
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提醒模板") }
            item {
                SettingsCard {
                    VariableInputRow("通知标题", "留空使用默认标题", titleTemplate, momentsPostNotificationTemplateVariables) {
                        titleTemplate = it
                        sp.edit().putString(MomentsPostNotificationSettings.KEY_TITLE_TEMPLATE, it).apply()
                    }
                    InsetDivider()
                    VariableInputRow("通知内容", "留空使用发布者、类型和完整正文", bodyTemplate, momentsPostNotificationTemplateVariables, minLines = 2) {
                        bodyTemplate = it
                        sp.edit().putString(MomentsPostNotificationSettings.KEY_BODY_TEMPLATE, it).apply()
                    }
                    InsetDivider()
                    VariableInputRow("Toast内容", "留空使用默认提醒", toastTemplate, momentsPostNotificationTemplateVariables, minLines = 2) {
                        toastTemplate = it
                        sp.edit().putString(MomentsPostNotificationSettings.KEY_TOAST_TEMPLATE, it).apply()
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
    var showPresetPicker by remember { mutableStateOf(false) }
    var presetQuery by remember { mutableStateOf("") }

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
                    InsetDivider()
                    ActionRow(
                        "选择内置尾巴",
                        MomentsUploadTailPresets.matchingPreset(sdkId, sdkAppName)?.name ?: "自定义"
                    ) {
                        presetQuery = ""
                        showPresetPicker = true
                    }
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
    if (showPresetPicker) {
        val query = presetQuery.trim().lowercase(Locale.ROOT)
        val visiblePresets = remember(query) {
            if (query.isEmpty()) MomentsUploadTailPresets.options
            else MomentsUploadTailPresets.options.filter { it.name.lowercase(Locale.ROOT).contains(query) }
        }
        SettingsWindowDialog(
            show = true,
            title = "选择内置尾巴",
            onDismissRequest = { showPresetPicker = false },
            content = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                ) {
                    item {
                        InputRow("搜索名称", "", presetQuery) { presetQuery = it }
                    }
                    if (visiblePresets.isEmpty()) {
                        item { EmptyText("没有匹配的名称") }
                    } else {
                        items(visiblePresets, key = { it.appId }) { preset ->
                            SelectRow(preset.name, "") {
                                sdkId = preset.appId
                                sdkAppName = preset.name
                                MomentsUploadTailPresets.saveSelection(sp, preset)
                                showPresetPicker = false
                            }
                        }
                    }
                }
            }
        )
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
                    InsetDivider()
                    InputRow("朋友圈提示文案", "默认 [已删除]", momentMarkText, onEditingFinished = { if (it.isBlank()) sp.edit().putString(SnsAntiRecallSettings.KEY_CUSTOM_MARK_TEXT, SnsAntiRecallSettings.DEFAULT_CUSTOM_MARK_TEXT).apply() }) {
                        momentMarkText = it
                        if (it.isNotBlank()) sp.edit().putString(SnsAntiRecallSettings.KEY_CUSTOM_MARK_TEXT, it).apply()
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
                    InsetDivider()
                    InputRow("评论提示文案", "默认 [已删除]", commentMarkText, onEditingFinished = { if (it.isBlank()) sp.edit().putString(SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_TEXT, SnsAntiRecallSettings.DEFAULT_COMMENT_CUSTOM_MARK_TEXT).apply() }) {
                        commentMarkText = it
                        if (it.isNotBlank()) sp.edit().putString(SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_TEXT, it).apply()
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
            if (isLike) {
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
                        InsetDivider()
                        NumberInputRow(
                            title = "自动勾选数量",
                            summary = if (autoSelectCount.toIntOrNull()?.let { it >= 1 } != true) "请输入不少于 1 的整数；保留上次有效配置" else "最少 1 人，不设上限",
                            value = autoSelectCount
                        ) { next ->
                            autoSelectCount = next
                            next.toIntOrNull()?.takeIf { it >= 1 }?.let {
                                sp.edit().putInt(
                                    MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT_COUNT,
                                    it
                                ).apply()
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
            if (!isLike && !isForward) {
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
                    confirmText = "完成",
                    showGroupFilter = false,
                    initialSelectedIds = selectedIds,
                    allowEmpty = true,
                    showClearSelectionAction = true,
                    onConfirm = {},
                    onSelectionChange = { selected ->
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
        summary = if (initialValue.isBlank()) "完成后可用于随机评论" else "修改会自动保存",
        initialValue = initialValue,
        placeholder = "请输入评论内容",
        maxLength = 1000,
        singleLine = false,
        onConfirm = if (initialValue.isBlank()) onSaved else { _ -> },
        onDismiss = {},
        onValidChange = if (initialValue.isBlank()) null else onSaved
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
        sp.edit()
            .putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS, enabled)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_BG, lightBg)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_TEXT, lightText)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_BG, darkBg)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_TEXT, darkText)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT, format)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_TIME_FORMAT, timeFormat)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_POSITION, position)
            .putInt(HchatExtraSettings.KEY_MESSAGE_DETAILS_AVATAR_GAP, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_AVATAR_GAP)
            .putInt(HchatExtraSettings.KEY_MESSAGE_DETAILS_LEFT_MARGIN, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LEFT_MARGIN)
            .putInt(HchatExtraSettings.KEY_MESSAGE_DETAILS_RIGHT_MARGIN, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_RIGHT_MARGIN)
            .putInt(HchatExtraSettings.KEY_MESSAGE_DETAILS_TEXT_SIZE, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TEXT_SIZE)
            .putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS_CLICK_SHOW, clickShow)
            .putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT_CONTENT, formatContent)
            .apply()
    }

    fun saveColor(key: String, value: String, fallback: String, finished: Boolean = false) {
        val color = MemberTitleStore.cleanColor(value)
        if (color.isNotEmpty()) sp.edit().putString(key, color).apply()
        else if (finished && value.isBlank()) sp.edit().putString(key, fallback).apply()
    }

    fun saveNumber(key: String, value: String, fallback: Int, max: Int? = null, finished: Boolean = false) {
        val number = value.toIntOrNull()
        if (number != null && number >= 0 && (max == null || number <= max)) sp.edit().putInt(key, number).apply()
        else if (finished && value.isBlank()) sp.edit().putInt(key, fallback).apply()
    }

    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "消息显示时间",
        largeTitle = "消息显示时间",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "消息显示时间",
                        summary = "开启后按所选位置显示自定义消息时间",
                        onCheckedChange = { enabled = it; sp.edit().putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS, it).apply() }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = clickShow,
                        title = "点击显详情",
                        summary = "点击详情文字后打开消息内容详情",
                        onCheckedChange = { clickShow = it; sp.edit().putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS_CLICK_SHOW, it).apply() }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = formatContent,
                        title = "内容格式化",
                        summary = "打开详情时格式化 XML 内容",
                        onCheckedChange = { formatContent = it; sp.edit().putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT_CONTENT, it).apply() }
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
                        onReset = { lightBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG; saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_BG, lightBg, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG) },
                        error = if (lightBg.isNotBlank() && MemberTitleStore.cleanColor(lightBg).isEmpty()) "请输入完整颜色" else null,
                        onEditingFinished = { saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_BG, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG, true) }
                    ) { lightBg = it; saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_BG, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG) }
                    InsetDivider()
                    ColorPickerRow(
                        "浅色文字",
                        "浅色模式消息时间文字",
                        lightText,
                        allowGradient = false,
                        onReset = { lightText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT; saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_TEXT, lightText, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT) },
                        error = if (lightText.isNotBlank() && MemberTitleStore.cleanColor(lightText).isEmpty()) "请输入完整颜色" else null,
                        onEditingFinished = { saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_TEXT, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT, true) }
                    ) { lightText = it; saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_TEXT, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT) }
                    InsetDivider()
                    ColorPickerRow(
                        "深色背景",
                        "深色模式消息时间背景",
                        darkBg,
                        allowGradient = false,
                        onReset = { darkBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG; saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_BG, darkBg, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG) },
                        error = if (darkBg.isNotBlank() && MemberTitleStore.cleanColor(darkBg).isEmpty()) "请输入完整颜色" else null,
                        onEditingFinished = { saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_BG, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG, true) }
                    ) { darkBg = it; saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_BG, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG) }
                    InsetDivider()
                    ColorPickerRow(
                        "深色文字",
                        "深色模式消息时间文字",
                        darkText,
                        allowGradient = false,
                        onReset = { darkText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT; saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_TEXT, darkText, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT) },
                        error = if (darkText.isNotBlank() && MemberTitleStore.cleanColor(darkText).isEmpty()) "请输入完整颜色" else null,
                        onEditingFinished = { saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_TEXT, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT, true) }
                    ) { darkText = it; saveColor(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_TEXT, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT) }
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
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT).apply() },
                        onValueChange = { format = it; if (it.isNotBlank()) sp.edit().putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT, it).apply() }
                    )
                    InsetDivider()
                    PopupChoiceRow(
                        title = "时间显示",
                        summary = messageDetailsTimeFormatChoices(timeFormat).first { it.value == timeFormat }.label,
                        options = messageDetailsTimeFormatChoices(timeFormat),
                        currentValue = timeFormat,
                        onValueChanged = { timeFormat = it; sp.edit().putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_TIME_FORMAT, it).apply() }
                    )
                    InsetDivider()
                    InputRow("自定义时间格式", if (timeFormat.isNotBlank() && runCatching { SimpleDateFormat(timeFormat, Locale.CHINA) }.isFailure) "时间格式无效，保留原设置" else "yyyy 年、MM 月、dd 日；例如 yyyy-MM-dd HH:mm:ss", timeFormat,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_TIME_FORMAT, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TIME_FORMAT).apply() },
                        onValueChange = { timeFormat = it; if (it.isNotBlank() && runCatching { SimpleDateFormat(it, Locale.CHINA) }.isSuccess) sp.edit().putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_TIME_FORMAT, it).apply() })
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
                        onValueChanged = { position = it; sp.edit().putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_POSITION, it).apply() }
                    )
                    InsetDivider()
                    if (position == HchatExtraSettings.POSITION_MESSAGE_BOTTOM) {
                        NumberInputRow("左边距", "单位 dp，对方消息使用", leftMargin, onEditingFinished = { saveNumber(HchatExtraSettings.KEY_MESSAGE_DETAILS_LEFT_MARGIN, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LEFT_MARGIN, finished = true) }, onValueChange = { leftMargin = it; saveNumber(HchatExtraSettings.KEY_MESSAGE_DETAILS_LEFT_MARGIN, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LEFT_MARGIN) })
                        InsetDivider()
                        NumberInputRow("右边距", "单位 dp，自己消息使用", rightMargin, onEditingFinished = { saveNumber(HchatExtraSettings.KEY_MESSAGE_DETAILS_RIGHT_MARGIN, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_RIGHT_MARGIN, finished = true) }, onValueChange = { rightMargin = it; saveNumber(HchatExtraSettings.KEY_MESSAGE_DETAILS_RIGHT_MARGIN, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_RIGHT_MARGIN) })
                        InsetDivider()
                    } else {
                        NumberInputRow("与头像间距", if (avatarGap.isNotEmpty() && avatarGap.toIntOrNull()?.let { it in 0..64 } != true) "请输入 0–64，保留原设置" else "单位 dp，可设置 0-64", avatarGap, onEditingFinished = { saveNumber(HchatExtraSettings.KEY_MESSAGE_DETAILS_AVATAR_GAP, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_AVATAR_GAP, 64, true) }, onValueChange = { avatarGap = it; saveNumber(HchatExtraSettings.KEY_MESSAGE_DETAILS_AVATAR_GAP, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_AVATAR_GAP, 64) })
                        InsetDivider()
                    }
                    NumberInputRow("字体大小", "单位 sp", textSize, onEditingFinished = { saveNumber(HchatExtraSettings.KEY_MESSAGE_DETAILS_TEXT_SIZE, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TEXT_SIZE, finished = true) }, onValueChange = { textSize = it; saveNumber(HchatExtraSettings.KEY_MESSAGE_DETAILS_TEXT_SIZE, it, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TEXT_SIZE) })
                }
            }

            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ContentActionRow("恢复默认", onClick = ::resetValues)
                }
            }
        }
    }
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SmallTitle(text = "聊天菜单") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "隐藏长按菜单",
                        summary = "隐藏聊天消息长按菜单中的指定项目",
                        onCheckedChange = { enabled = it; sp.edit().putBoolean(HideChatMenuSettings.KEY_ENABLE, it).apply() }
                    )
                    InsetDivider()
                    InputRow(
                        "隐藏菜单项",
                        "输入菜单显示名称，多个项目用逗号、分号或换行分隔",
                        titles,
                        minLines = 2,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(HideChatMenuSettings.KEY_TITLES, "").apply() },
                        onValueChange = {
                            titles = it
                            if (it.isNotBlank()) sp.edit().putString(HideChatMenuSettings.KEY_TITLES, HideChatMenuSettings.parseTitles(it).joinToString(",")).apply()
                        }
                    )
                }
            }

            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ContentActionRow("恢复默认") {
                        titles = HideChatMenuSettings.DEFAULT_TITLES
                        sp.edit().putString(HideChatMenuSettings.KEY_TITLES, titles).apply()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsEntryControls(sp: SharedPreferences) {
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SmallTitle(text = "入口") }
            item {
                SettingsCard {
                    SettingsEntryControls(sp)
                }
            }
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
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
internal fun BackgroundRunAssistMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, BackgroundRunAssistSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(sp.getBoolean(BackgroundRunAssistSettings.KEY_ENABLE, BackgroundRunAssistSettings.DEFAULT_ENABLE)) }
    var wakeLock by remember { mutableStateOf(sp.getBoolean(BackgroundRunAssistSettings.KEY_WAKE_LOCK, BackgroundRunAssistSettings.DEFAULT_WAKE_LOCK)) }
    var rootWhitelist by remember { mutableStateOf(sp.getBoolean(BackgroundRunAssistSettings.KEY_ROOT_DOZE_WHITELIST, BackgroundRunAssistSettings.DEFAULT_ROOT_DOZE_WHITELIST)) }
    var rootAppOps by remember { mutableStateOf(sp.getBoolean(BackgroundRunAssistSettings.KEY_ROOT_APP_OPS, BackgroundRunAssistSettings.DEFAULT_ROOT_APP_OPS)) }
    var ignoringBattery by remember { mutableStateOf(BackgroundRunAssistRuntime.isIgnoringBatteryOptimizations(context)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun saveBoolean(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
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
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SmallTitle(text = "后台运行辅助") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "后台运行辅助",
                        summary = "仅应用下方明确选择的辅助选项，不承诺阻止系统结束微信",
                        onCheckedChange = {
                            enabled = it
                            saveBoolean(BackgroundRunAssistSettings.KEY_ENABLE, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = wakeLock,
                        title = "限时 WakeLock",
                        summary = "单次最多持有 10 分钟，之后由系统自动释放；会增加耗电且不能绕过 Doze",
                        onCheckedChange = {
                            wakeLock = it
                            saveBoolean(BackgroundRunAssistSettings.KEY_WAKE_LOCK, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = rootWhitelist,
                        title = "Root Doze 白名单",
                        summary = "需要 Root。仅调整当前微信包名；关闭时尝试恢复保存的原状态，Root 命令会修改系统设置",
                        onCheckedChange = {
                            rootWhitelist = it
                            saveBoolean(BackgroundRunAssistSettings.KEY_ROOT_DOZE_WHITELIST, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = rootAppOps,
                        title = "Root 后台限制放行",
                        summary = "需要 Root。仅调整当前微信包名的后台 AppOps；关闭时尝试恢复保存的原状态，Root 命令会修改系统设置",
                        onCheckedChange = {
                            rootAppOps = it
                            saveBoolean(BackgroundRunAssistSettings.KEY_ROOT_APP_OPS, it)
                        }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "系统限制") }
            item {
                SettingsCard {
                    InfoRow(
                        "能力边界",
                        "本页不启动服务、不监测或拉起微信。WakeLock 仅在当前注入进程运行期间生效；Root 选项只修改系统可查询到的包状态，不能保证后台运行。"
                    )
                    InsetDivider()
                    InfoRow(
                        "电池优化白名单",
                        if (ignoringBattery) "当前宿主微信已忽略电池优化" else "当前宿主微信仍可能受系统省电影响"
                    )
                    InsetDivider()
                    ActionRow("打开电池优化设置", "建议把微信设置为不限制") {
                        openBatterySettings()
                    }
                    InsetDivider()
                    ActionRow("刷新状态", if (ignoringBattery) "当前已忽略优化" else "当前未忽略优化") {
                        ignoringBattery = BackgroundRunAssistRuntime.isIgnoringBatteryOptimizations(context)
                        Toast.makeText(context, "状态已刷新", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
