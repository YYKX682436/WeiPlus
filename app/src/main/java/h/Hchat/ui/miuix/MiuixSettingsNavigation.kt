package h.Hchat.ui.miuix

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.webkit.WebView
import h.Hchat.crash.CrashReportSettingsProvider
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutFeature
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.audiotransform.AudioTransformFeature
import h.Hchat.hooks.items.antirecall.AntiRecallFeature
import h.Hchat.hooks.items.autoreply.AutoReplyFeature
import h.Hchat.hooks.items.autooriginal.AutoOriginalImageFeature
import h.Hchat.hooks.items.autovieworiginal.AutoViewOriginalFeature
import h.Hchat.hooks.items.atallnotify.AtAllNotificationBlockFeature
import h.Hchat.hooks.items.automessageforward.AutoMessageForwardFeature
import h.Hchat.hooks.items.callmedialimit.CallMediaLimitFeature
import h.Hchat.hooks.items.callmedialimit.CallRingtoneBlockFeature
import h.Hchat.hooks.items.chattime.ChatTimeStyleFeature
import h.Hchat.hooks.items.conversationgroup.ConversationGroup
import h.Hchat.hooks.items.conversationgroup.ConversationGroupFeature
import h.Hchat.hooks.items.conversationtabs.ConversationTabsFeature
import h.Hchat.hooks.items.custombottombar.CustomBottomBarFeature
import h.Hchat.hooks.items.custombottombar.FloatingBottomBarSettings
import h.Hchat.hooks.items.customnotify.CustomNotificationFeature
import h.Hchat.hooks.items.customfriendavatar.CustomFriendAvatarFeature
import h.Hchat.hooks.items.editmsg.EditMessageFeature
import h.Hchat.hooks.items.emojisave.EmojiSaveFeature
import h.Hchat.hooks.items.fakelocation.FakeLocationFeature
import h.Hchat.hooks.items.fakescancamera.FakeScanCameraFeature
import h.Hchat.hooks.items.fakevoiceduration.FakeVoiceDurationFeature
import h.Hchat.hooks.items.forwardlimit.RemoveForwardLimitFeature
import h.Hchat.hooks.items.gameemoji.GameEmojiFeature
import h.Hchat.hooks.items.groupleave.GroupLeaveMonitorFeature
import h.Hchat.hooks.items.grouplabel.GroupChatLabel
import h.Hchat.hooks.items.grouplabel.GroupChatLabelFeature
import h.Hchat.hooks.items.grouplabel.QuickGroupChatLabelFeature
import h.Hchat.hooks.items.groupnicknamecolor.GroupNicknameColorSettings
import h.Hchat.hooks.items.grouprename.GroupRenameMonitorFeature
import h.Hchat.hooks.items.grouprename.GroupRenameReplyTemplate
import h.Hchat.hooks.items.hideavatar.HideChatAvatarFeature
import h.Hchat.hooks.items.hidemenu.HideChatMenuFeature
import h.Hchat.hooks.items.hotupdate.DisableHotUpdateFeature
import h.Hchat.hooks.items.hometextcolor.HomeTextColorFeature
import h.Hchat.hooks.items.inputhint.InputHintFeature
import h.Hchat.hooks.items.backgroundassist.BackgroundRunAssistFeature
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationFeature
import h.Hchat.hooks.items.zombiecheck.ZombieCheckFeature
import h.Hchat.hooks.items.membertitle.MemberTitleFeature
import h.Hchat.hooks.items.messageaffix.MessageAffixFeature
import h.Hchat.hooks.items.messagebubble.MessageBubbleFeature
import h.Hchat.hooks.items.messagetextcolor.MessageTextColorFeature
import h.Hchat.hooks.items.messageblock.MessageBlockFeature
import h.Hchat.hooks.items.messageforward.MessageForwardFeature
import h.Hchat.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibFeature
import h.Hchat.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsFeature
import h.Hchat.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsFeature
import h.Hchat.hooks.items.musicorder.QQMusicOrderFeature
import h.Hchat.hooks.items.moments.OriginalMomentsUploadFeature
import h.Hchat.hooks.items.moments.MomentsAutoCommentFeature
import h.Hchat.hooks.items.moments.MomentsAutoLikeFeature
import h.Hchat.hooks.items.moments.MomentsAutoForwardFeature
import h.Hchat.hooks.items.moments.MomentsAutoRefreshFeature
import h.Hchat.hooks.items.moments.MomentsBottomDetailFeature
import h.Hchat.hooks.items.moments.MomentsContactFilterFeature
import h.Hchat.hooks.items.moments.MomentsKeywordBlockFeature
import h.Hchat.hooks.items.moments.MomentsPostNotificationFeature
import h.Hchat.hooks.items.moments.MomentsUploadTailFeature
import h.Hchat.hooks.items.moments.RemoveMomentsAdsFeature
import h.Hchat.hooks.items.moments.SnsAntiRecallFeature
import h.Hchat.hooks.items.momentsfake.MomentsFakeCommentSettingsProvider
import h.Hchat.hooks.items.momentsfake.MomentsFakeForwardSettingsProvider
import h.Hchat.hooks.items.momentsfake.MomentsFakeLikeSettingsProvider
import h.Hchat.hooks.items.multirecall.MultiRecallFeature
import h.Hchat.hooks.items.payment.core.AutoRedPacketFeature
import h.Hchat.hooks.items.payment.fakebalance.FakeWalletBalanceFeature
import h.Hchat.hooks.items.payment.gift.AutoGiftFeature
import h.Hchat.hooks.items.payment.gift.GiftSettings
import h.Hchat.hooks.items.payment.transfer.AutoTransferFeature
import h.Hchat.hooks.items.patblock.PatBlockFeature
import h.Hchat.hooks.items.profileid.ProfileIdFeature
import h.Hchat.hooks.items.protobuf.ProtobufPacketFeature
import h.Hchat.hooks.items.quoteclear.QuoteDeleteClearFeature
import h.Hchat.hooks.items.quickcontactedit.QuickContactEditFeature
import h.Hchat.hooks.items.quickmoments.QuickMomentsFeature
import h.Hchat.hooks.items.quickread.QuickMarkReadFeature
import h.Hchat.hooks.items.quickterminate.QuickTerminateFeature
import h.Hchat.hooks.items.realtail.RealNameTailFeature
import h.Hchat.hooks.items.roundavatar.RoundAvatarFeature
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskFeature
import h.Hchat.hooks.items.selectedmessages.SelectedMessagesFeature
import h.Hchat.hooks.items.settings.SettingsFeature
import h.Hchat.hooks.items.shortvideo.FinderMediaDownloadFeature
import h.Hchat.hooks.items.statuslimit.StatusTextLimitFeature
import h.Hchat.hooks.items.swipequote.SwipeQuoteFeature
import h.Hchat.hooks.items.tablet.WeChatTabletFeature
import h.Hchat.hooks.items.textspeech.TextSpeechFeature
import h.Hchat.hooks.items.textvoice.TextVoiceFeature
import h.Hchat.hooks.items.typingreport.TypingReportBlockFeature
import h.Hchat.hooks.items.voiceforward.VoiceForwardFeature
import h.Hchat.hooks.items.hchatextra.GroupMemberHistorySettingsProvider
import h.Hchat.hooks.items.hchatextra.MessageDetailsSettingsProvider
import h.Hchat.hooks.items.hchatextra.RedPacketDetailsSettingsProvider
import h.Hchat.hooks.items.hchatextra.SkipWebRiskSettingsProvider
import h.Hchat.hooks.items.hchatextra.HchatExtraSettings
import h.Hchat.hooks.items.voicepreview.VoicePreviewFeature
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import org.json.JSONArray
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun MainSettingsPage(
    context: Context,
    providers: List<FeatureSettingsProvider>,
    listState: LazyListState,
    selectedTab: MainTab,
    configVersion: Int,
    onSelectedTab: (MainTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProvider: (FeatureSettingsProvider) -> Unit,
    onOpenGroup: (FeatureGroupEntry) -> Unit,
    onConfigImported: () -> Unit
) {
    val uiPrefs = remember(configVersion) { HchatStorage.preferences(context, UI_PREFS_NAME) }
    val glassSupported = remember { isLiquidGlassSupported() }
    var floatingNav by remember(configVersion) { mutableStateOf(uiPrefs.getBoolean(KEY_FLOATING_NAV, true)) }
    var glassNav by remember(configVersion) { mutableStateOf(glassSupported && uiPrefs.getBoolean(KEY_GLASS_NAV, true)) }
    val entertainmentListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val practicalProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_PRACTICAL }
    val entertainmentProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_ENTERTAINMENT }
    val enhanceProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_ENHANCE }
    val practicalGroups = remember(practicalProviders, enhanceProviders) {
        practicalFeatureGroups(practicalProviders, enhanceProviders)
    }
    val entertainmentGroups = remember(entertainmentProviders) {
        entertainmentFeatureGroups(entertainmentProviders)
    }
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(
        initialPage = selectedTab.ordinal,
        pageCount = { tabs.size }
    )
    LaunchedEffect(pagerState.currentPage) {
        tabs.getOrNull(pagerState.currentPage)?.let(onSelectedTab)
    }
    val activeTab = tabs.getOrElse(pagerState.currentPage) { MainTab.PRACTICAL }
    PageScaffold(
        title = "Hchat",
        largeTitle = "Hchat",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            MainNavigationBar(
                selectedTab = activeTab,
                floating = floatingNav,
                glass = glassSupported && glassNav,
                backdrop = it,
                onSelected = { tab ->
                    scope.launch { pagerState.springAnimateToPage(tab.ordinal) }
                }
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize(),
            key = { tabs[it] }
        ) { page ->
            val tab = tabs[page]
            val activeListState = when (tab) {
                MainTab.PRACTICAL -> listState
                MainTab.ENTERTAINMENT -> entertainmentListState
                MainTab.SETTINGS -> settingsListState
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                state = activeListState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 78.dp
                )
            ) {
                item {
                    SettingsSearchEntry(onClick = onOpenSearch)
                }
                when (tab) {
                    MainTab.PRACTICAL -> {
                        featureGroupMenu(
                            title = "实用功能",
                            groups = practicalGroups,
                            onOpenGroup = onOpenGroup
                        )
                        if (practicalProviders.isEmpty() && enhanceProviders.isEmpty()) {
                            item { SmallTitle(text = "实用功能") }
                            item { FeatureListCard(emptyList(), "暂无实用功能", onOpenProvider) }
                        }
                    }
                    MainTab.ENTERTAINMENT -> {
                        featureGroupMenu(
                            title = "娱乐功能",
                            groups = entertainmentGroups,
                            onOpenGroup = onOpenGroup
                        )
                        if (entertainmentProviders.isEmpty()) {
                            item { SmallTitle(text = "娱乐功能") }
                            item { FeatureListCard(emptyList(), "暂无娱乐功能", onOpenProvider) }
                        }
                    }
                    MainTab.SETTINGS -> {
                        item { SmallTitle(text = "设置") }
                        item {
                            SettingsCard {
                                SwitchRow(
                                    checked = floatingNav,
                                    title = "悬浮底栏",
                                    summary = "使用悬浮样式的底部导航栏",
                                    onCheckedChange = {
                                        floatingNav = it
                                        uiPrefs.edit().putBoolean(KEY_FLOATING_NAV, it).apply()
                                    }
                                )
                                InsetDivider()
                                SwitchRow(
                                    checked = glassSupported && glassNav,
                                    title = "液态玻璃",
                                    summary = if (glassSupported) {
                                        "启用悬浮底栏的液态玻璃效果"
                                    } else {
                                        "Android 13 以下不支持液态玻璃效果"
                                    },
                                    enabled = glassSupported,
                                    onCheckedChange = {
                                        if (!glassSupported) return@SwitchRow
                                        glassNav = it
                                        uiPrefs.edit().putBoolean(KEY_GLASS_NAV, it).apply()
                                    }
                                )
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "关于") }
                        item { AboutCard(context) }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "配置") }
                        item {
                            ConfigBackupCard(
                                context = context,
                                onImported = onConfigImported
                            )
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "群组") }
                        item { GroupLinksCard(context) }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "致谢") }
                        item { ThanksCard(context) }
                        item {
                            Text(
                                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                                text = "Hchat",
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun List<FeatureSettingsProvider>.filterByIds(vararg ids: String): List<FeatureSettingsProvider> {
    val order = ids.toList()
    return filter { it.featureId() in order }
        .sortedBy { order.indexOf(it.featureId()) }
}

private fun practicalFeatureGroups(
    practicalProviders: List<FeatureSettingsProvider>,
    enhanceProviders: List<FeatureSettingsProvider>
): List<FeatureGroupEntry> {
    return listOf(
        FeatureGroupEntry(
            title = "聊天",
            providers = practicalProviders.filterByIds(
                AntiRecallFeature.ID,
                MultiRecallFeature.ID,
                AutoReplyFeature.ID,
                MessageAffixFeature.ID,
                TypingReportBlockFeature.ID,
                PatBlockFeature.ID,
                AutoOriginalImageFeature.ID,
                AutoViewOriginalFeature.ID,
                RemoveForwardLimitFeature.ID,
                MessageBlockFeature.ID,
                QuickMarkReadFeature.ID,
                MessageDetailsSettingsProvider.FEATURE_ID,
                QuoteDeleteClearFeature.ID,
                SwipeQuoteFeature.ID,
                EmojiSaveFeature.ID,
                CallMediaLimitFeature.ID,
                CallRingtoneBlockFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "语音",
            providers = practicalProviders.filterByIds(
                AudioTransformFeature.ID,
                TextVoiceFeature.ID,
                FakeVoiceDurationFeature.ID,
                VoicePreviewFeature.ID,
                VoiceForwardFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "红包转账",
            providers = practicalProviders.filterByIds(
                AutoRedPacketFeature.ID,
                AutoGiftFeature.ID,
                AutoTransferFeature.ID,
                FakeWalletBalanceFeature.ID,
                RedPacketDetailsSettingsProvider.FEATURE_ID
            )
        ),
        FeatureGroupEntry(title = "增强", providers = enhanceProviders),
        FeatureGroupEntry(
            title = "群组",
            providers = practicalProviders.filterByIds(
                GroupChatLabelFeature.ID,
                AtAllNotificationBlockFeature.ID,
                GroupLeaveMonitorFeature.ID,
                GroupRenameMonitorFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "朋友圈",
            providers = practicalProviders.filterByIds(
                MomentsAutoLikeFeature.ID,
                MomentsAutoCommentFeature.ID,
                MomentsAutoForwardFeature.ID,
                MomentsAutoRefreshFeature.ID,
                MomentsContactFilterFeature.ID,
                MomentsKeywordBlockFeature.ID,
                MomentsBottomDetailFeature.ID,
                MomentsPostNotificationFeature.ID,
                OriginalMomentsUploadFeature.ID,
                MomentsUploadTailFeature.ID,
                SnsAntiRecallFeature.ID,
                MomentsFakeLikeSettingsProvider.ID,
                MomentsFakeCommentSettingsProvider.ID,
                MomentsFakeForwardSettingsProvider.ID,
                RemoveMomentsAdsFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "美化",
            providers = practicalProviders.filterByIds(
                MessageBubbleFeature.ID,
                MessageTextColorFeature.ID,
                HomeTextColorFeature.ID,
                ChatTimeStyleFeature.ID,
                InputHintFeature.ID,
                HideChatAvatarFeature.ID,
                RoundAvatarFeature.ID,
                CustomFriendAvatarFeature.ID,
                CustomBottomBarFeature.ID,
                FloatingBottomBarSettings.FEATURE_ID
            )
        ),
        FeatureGroupEntry(
            title = "界面",
            providers = practicalProviders.filterByIds(
                SettingsFeature.ID,
                FloatingShortcutFeature.ID,
                ProfileIdFeature.ID,
                GroupMemberHistorySettingsProvider.FEATURE_ID,
                QuickContactEditFeature.ID,
                QuickGroupChatLabelFeature.ID,
                QuickMomentsFeature.ID,
                QuickTerminateFeature.ID,
                HideChatMenuFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "杂项",
            providers = practicalProviders.filterByIds(
                BackgroundRunAssistFeature.ID,
                WeChatTabletFeature.ID,
                DisableHotUpdateFeature.ID,
                FakeMiniProgramBaseLibFeature.ID,
                SkipMiniProgramVideoAdsFeature.ID,
                SkipGlobalMiniProgramSplashAdsFeature.ID,
                FakeLocationFeature.ID,
                GameEmojiFeature.ID,
                FakeScanCameraFeature.ID,
                FinderMediaDownloadFeature.ID,
                SkipWebRiskSettingsProvider.FEATURE_ID
            )
        )
    ).filter { it.providers.isNotEmpty() }
}

private fun entertainmentFeatureGroups(
    entertainmentProviders: List<FeatureSettingsProvider>
): List<FeatureGroupEntry> {
    return listOf(
        FeatureGroupEntry(
            title = "点歌",
            providers = entertainmentProviders.filterByIds(QQMusicOrderFeature.ID)
        ),
        FeatureGroupEntry(
            title = "群聊",
            providers = entertainmentProviders.filterByIds(
                RealNameTailFeature.ID,
                GroupNicknameColorSettings.FEATURE_ID,
                MemberTitleFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "状态",
            providers = entertainmentProviders.filterByIds(StatusTextLimitFeature.ID)
        ),
        FeatureGroupEntry(
            title = "聊天",
            providers = entertainmentProviders.filterByIds(EditMessageFeature.ID)
        ),
        FeatureGroupEntry(
            title = "抓包",
            providers = entertainmentProviders.filterByIds(ProtobufPacketFeature.ID)
        ),
        FeatureGroupEntry(
            title = "调试",
            providers = entertainmentProviders.filterByIds(CrashReportSettingsProvider.ID)
        )
    ).filter { it.providers.isNotEmpty() }
}

@Composable
private fun SettingsSearchEntry(onClick: () -> Unit) {
    SearchBarSurface(
        query = "",
        placeholder = "搜索功能",
        readOnly = true,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        onClick = onClick,
        onQueryChange = {}
    )
}

@Composable
internal fun SettingsSearchPage(
    context: Context,
    providers: List<FeatureSettingsProvider>,
    onBack: () -> Unit,
    onOpenProvider: (FeatureSettingsProvider, FeatureGroupEntry?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val uiPrefs = remember { HchatStorage.preferences(context, UI_PREFS_NAME) }
    var searchHistory by remember { mutableStateOf(readSearchHistory(uiPrefs)) }
    val practicalProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_PRACTICAL }
    val entertainmentProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_ENTERTAINMENT }
    val enhanceProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_ENHANCE }
    val groups = remember(practicalProviders, enhanceProviders, entertainmentProviders) {
        practicalFeatureGroups(practicalProviders, enhanceProviders) +
            entertainmentFeatureGroups(entertainmentProviders)
    }
    val featureResults = remember(query, groups, providers) {
        filterFeatureSearchResults(query, groups, providers)
    }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    fun recordSearch() {
        searchHistory = rememberSearchKeyword(uiPrefs, query)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 74.dp,
                bottom = navigationButtonBottomInset() + NAVIGATION_BUTTON_EXTRA_GAP + 24.dp
            )
        ) {
            if (query.isBlank()) {
                item {
                    SearchHistorySection(
                        searchHistory,
                        onSelect = { query = it },
                        onClear = {
                            saveSearchHistory(uiPrefs, emptyList())
                            searchHistory = emptyList()
                        }
                    )
                }
            } else {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "功能(${featureResults.size})") }
                item {
                    FeatureSearchResultCard(
                        results = featureResults,
                        onOpenProvider = { provider, group ->
                            recordSearch()
                            onOpenProvider(provider, group)
                        }
                    )
                }
            }
        }
        SearchPageTopBar(
            query = query,
            focusRequester = focusRequester,
            onQueryChange = { query = it },
            onCancel = onBack
        )
    }
}

@Composable
private fun SearchPageTopBar(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MiuixTheme.colorScheme.background)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                start = 12.dp,
                end = 12.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SearchBarSurface(
            query = query,
            placeholder = "搜索功能",
            modifier = Modifier.weight(1f),
            focusRequester = focusRequester,
            onQueryChange = onQueryChange
        )
        Text(
            text = "取消",
            color = MiuixTheme.colorScheme.primary,
            fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onCancel() }
                .padding(horizontal = 4.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun SearchBarSurface(
    query: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: (() -> Unit)? = null,
    onQueryChange: (String) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            imageVector = NavIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary)
        )
        if (readOnly) {
            Text(
                text = query.ifEmpty { placeholder },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
            )
        } else {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .padding(start = 10.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
private fun SearchHistorySection(
    history: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    if (history.isEmpty()) {
        EmptyText("暂无搜索记录")
        return
    }
    SmallTitle(text = "搜索记录")
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        history.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { keyword ->
                    ContactFilterChip(
                        text = keyword,
                        selected = false,
                        onClick = { onSelect(keyword) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
    Text(
        text = "清除搜索记录",
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth()
            .padding(top = 18.dp)
            .clickable { onClear() },
        textAlign = TextAlign.Center
    )
}

@Composable
private fun FeatureSearchResultCard(
    results: List<FeatureSearchResult>,
    onOpenProvider: (FeatureSettingsProvider, FeatureGroupEntry?) -> Unit
) {
    SettingsCard {
        if (results.isEmpty()) {
            EmptyText("没有匹配功能")
        } else {
            results.forEachIndexed { index, result ->
                FeatureSearchResultRow(
                    result = result,
                    onClick = { onOpenProvider(result.provider, result.group) }
                )
                if (index < results.lastIndex) {
                    InsetDivider()
                }
            }
        }
    }
}

@Composable
private fun FeatureSearchResultRow(result: FeatureSearchResult, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = result.provider.title(), color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(
                text = result.summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
    }
}

private fun LazyListScope.featureGroupMenu(
    title: String,
    groups: List<FeatureGroupEntry>,
    onOpenGroup: (FeatureGroupEntry) -> Unit
) {
    if (groups.isEmpty()) return
    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = title) }
    item {
        SettingsCard {
            groups.forEachIndexed { index, group ->
                FeatureGroupRow(
                    group = group,
                    onClick = { onOpenGroup(group) }
                )
                if (index < groups.lastIndex) {
                    InsetDivider()
                }
            }
        }
    }
}

@Composable
internal fun FeatureGroupSettingsPage(
    group: FeatureGroupEntry,
    listState: LazyListState,
    onBack: () -> Unit,
    onOpenProvider: (FeatureSettingsProvider) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = group.title,
        largeTitle = group.title,
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
            item { SmallTitle(text = "功能") }
            item { FeatureListCard(group.providers, "暂无功能", onOpenProvider) }
        }
    }
}

internal enum class MainTab {
    PRACTICAL,
    ENTERTAINMENT,
    SETTINGS
}

private data class MainNavItem(
    val tab: MainTab,
    val label: String,
    val icon: ImageVector
)

internal data class FeatureGroupEntry(
    val title: String,
    val providers: List<FeatureSettingsProvider>
)

private data class FeatureSearchResult(
    val provider: FeatureSettingsProvider,
    val group: FeatureGroupEntry?,
    val summary: String
)

internal data class TemplateVariable(
    val token: String,
    val label: String
)

internal sealed class DetailPage {
    object Search : DetailPage()
    data class FeatureGroup(val group: FeatureGroupEntry) : DetailPage()
    data class Feature(
        val provider: FeatureSettingsProvider,
        val sourceGroup: FeatureGroupEntry? = null,
        val returnToSearch: Boolean = false
    ) : DetailPage()
}

internal data class SettingsNavKey(val page: DetailPage?) : NavKey


private fun filterFeatureSearchResults(
    query: String,
    groups: List<FeatureGroupEntry>,
    allProviders: List<FeatureSettingsProvider>
): List<FeatureSearchResult> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return emptyList()
    val grouped = LinkedHashSet<String>()
    val results = ArrayList<FeatureSearchResult>()
    groups.forEach { group ->
        group.providers.forEach { provider ->
            val matchedTerms = matchedFeatureSubTerms(keyword, provider)
            if (matchesFeature(keyword, provider, group.title) || matchedTerms.isNotEmpty()) {
                grouped.add(provider.featureId())
                results += FeatureSearchResult(
                    provider = provider,
                    group = group,
                    summary = featureSearchSummary(group.title, provider, matchedTerms)
                )
            }
        }
    }
    allProviders.forEach { provider ->
        val matchedTerms = matchedFeatureSubTerms(keyword, provider)
        if (provider.featureId() !in grouped && (matchesFeature(keyword, provider, null) || matchedTerms.isNotEmpty())) {
            results += FeatureSearchResult(
                provider = provider,
                group = null,
                summary = featureSearchSummary(null, provider, matchedTerms)
            )
        }
    }
    return results
}

private fun featureSearchSummary(
    groupTitle: String?,
    provider: FeatureSettingsProvider,
    matchedTerms: List<String>
): String {
    return buildString {
        if (!groupTitle.isNullOrBlank()) {
            append(groupTitle)
            append(" / ")
        }
        append(provider.subtitle())
        if (matchedTerms.isNotEmpty()) {
            append("\n匹配: ")
            append(matchedTerms.joinToString("、"))
        }
    }
}

private fun matchesFeature(
    query: String,
    provider: FeatureSettingsProvider,
    groupTitle: String?
): Boolean {
    return containsSearchText(provider.title(), query) ||
        containsSearchText(provider.subtitle(), query) ||
        containsSearchText(provider.featureId(), query) ||
        containsSearchText(groupTitle.orEmpty(), query)
}

private fun matchedFeatureSubTerms(query: String, provider: FeatureSettingsProvider): List<String> {
    return featureSubSearchTerms(provider.featureId())
        .filter { containsSearchText(it, query) }
        .distinct()
        .take(5)
}

private fun featureSubSearchTerms(featureId: String): List<String> {
    return when (featureId) {
        AntiRecallFeature.ID -> listOf(
            "私聊防撤回", "群聊防撤回", "公众号防撤回", "撤回提示", "自定义撤回提示", "发送时间", "撤回时间", "时间格式", "语音防撤回", "图片防撤回", "视频防撤回"
        )
        MultiRecallFeature.ID -> listOf("多选撤回", "批量撤回", "多选消息", "分享菜单", "撤回自己消息")
        MessageForwardFeature.ID -> listOf("转发", "转发[H]", "朋友圈", "好友", "分享", "群发助手", "好友标签", "朋友圈转发", "个人主页朋友圈")
        SelectedMessagesFeature.ID -> listOf("群发助手", "群发助手[H]", "定时转发[H]", "多选消息", "定时转发", "模块群发", "微信原生群发助手", "群发助手间隔延迟", "群发间隔延迟")
        AutoReplyFeature.ID -> listOf(
            "自动回复规则", "任意消息", "关键词", "正则", "艾特我", "@我", "艾特全体", "@所有人", "回复步骤", "延迟回复",
            "回复冷却时间", "冷却秒数", "最大回复次数",
            "小智", "小智AI", "小智回复", "小智语音", "小智语音回复", "小智文字回复", "唤醒词", "语音输入", "TTS",
            "上下文", "固定ID", "清空上下文", "智谱", "模型", "Agent", "角色", "语音角色", "好友自动化", "新好友打招呼"
        )
        AutoMessageForwardFeature.ID -> listOf(
            "消息自动转发", "自动转发消息", "转发规则", "监听会话", "转发到", "好友", "群聊", "公众号",
            "文字", "图片", "语音", "视频", "表情", "文件", "链接", "音乐", "小程序", "关键词", "包含关键词", "排除关键词", "替换关键词", "替换规则"
        )
        ConversationGroupFeature.ID -> listOf(
            "会话分组", "聊天分组", "嵌套分组", "归拢会话", "隐藏会话", "移动会话", "分组管理"
        )
        ConversationTabsFeature.ID -> listOf(
            "标签分组", "顶栏分组", "全部会话", "未读会话", "群聊", "私聊", "公众号", "自定义标签", "标签图标"
        )
        MessageAffixFeature.ID -> listOf(
            "发送文本格式", "消息格式", "消息前后缀", "消息前缀", "消息后缀", "固定文字", "发送时间", "换行"
        )
        InputHintFeature.ID -> listOf(
            "输入框提示", "聊天输入框", "默认提示", "占位文字", "发送统计", "消息数量", "文字字数"
        )
        MessageBlockFeature.ID -> listOf(
            "屏蔽消息", "屏蔽规则", "默认规则", "私聊规则", "群聊规则", "公众号规则", "任意消息", "关键词", "正则",
            "艾特我", "@我", "艾特全体", "@所有人", "允许入库", "消息入库", "模板", "名单", "批量绑定", "标签好友", "未知类型"
        )
        QuickMarkReadFeature.ID -> listOf("快捷已读", "一键已读", "长按已读", "通知已读", "会话已读")
        AutoOriginalImageFeature.ID -> listOf("自动勾选原图", "原图发送", "聊天图片原图", "发送图片")
        AutoViewOriginalFeature.ID -> listOf(
            "自动查看原图", "查看原图", "自动查看原视频", "查看原视频", "聊天图片", "聊天视频", "原画质"
        )
        RemoveForwardLimitFeature.ID -> listOf("移除转发限制", "转发上限", "9个会话", "多选转发", "原生转发")
        TypingReportBlockFeature.ID -> listOf("拦截正在输入上报", "屏蔽正在输入", "正在输入", "输入状态", "输入上报")
        PatBlockFeature.ID -> listOf("禁止拍一拍", "屏蔽拍一拍", "双击头像", "头像双击")
        MessageDetailsSettingsProvider.FEATURE_ID -> listOf(
            "消息显示时间", "消息显示详情", "消息详情", "消息时间", "消息类型", "时间格式", "时间颜色", "显示位置", "点击显详情"
        )
        GroupMemberHistorySettingsProvider.FEATURE_ID -> listOf("历史发言记录", "群成员历史发言", "群成员资料")
        AtAllNotificationBlockFeature.ID -> listOf("屏蔽艾特所有人", "屏蔽@所有人", "艾特全体", "通知拦截")
        QuoteDeleteClearFeature.ID -> listOf("删除键清引用", "清除引用", "引用消息", "删除引用")
        EmojiSaveFeature.ID -> listOf("保存表情", "表情保存", "动画表情", "长按菜单", "本地文件")
        SwipeQuoteFeature.ID -> listOf("滑动手势", "左滑引用", "右滑复读", "长按复读", "菜单复读", "+1", "复读语音", "语音时长", "滑动距离")
        CallMediaLimitFeature.ID -> listOf("通话媒体限制", "语音通话", "视频通话", "播放语音", "查看视频", "打开拍摄")
        CallRingtoneBlockFeature.ID -> listOf("屏蔽通话铃声", "呼入铃声", "呼出铃声", "来电铃声", "等待铃声", "语音通话", "视频通话")
        AudioTransformFeature.ID -> listOf(
            "音频转换",
            "音频转码",
            "语音转码",
            "silk",
            "mp3",
            "m4a",
            "语音发送",
            "切割语音",
            "分段语音",
            "裁剪语音",
            "自动分割",
            "开始时间",
            "结束时间",
            "试听",
            "播放音频"
        )
        TextVoiceFeature.ID -> listOf("文本转语音", "文字转语音", "在线语音", "发送文字转语音", "转语音播放", "#tts", "英文语音", "TTS引擎", "TTS角色", "语速", "播放速度")
        FakeVoiceDurationFeature.ID -> listOf("伪造语音时长", "语音显示时长", "假语音时长", "语音秒数")
        VoicePreviewFeature.ID -> listOf("语音消息预览", "预览语音", "播放语音", "语音时长", "长按语音")
        FakeMiniProgramBaseLibFeature.ID -> listOf("兼容低版本小程序", "伪装小程序宿主版本", "阻止小程序升级跳转", "微信版本较低", "官方升级页")
        SkipMiniProgramVideoAdsFeature.ID -> listOf("跳过小程序视频广告", "小程序广告", "视频广告")
        SkipGlobalMiniProgramSplashAdsFeature.ID -> listOf("跳过全局小程序开屏广告", "小程序开屏广告", "启动广告", "全局广告")
        FakeLocationFeature.ID -> listOf("虚拟定位", "模拟定位", "小程序定位", "微信地图", "地图选点", "搜索地点", "经纬度", "GPS")
        GameEmojiFeature.ID -> listOf("指定骰子猜拳", "自定义骰子", "骰子点数", "猜拳结果", "剪刀", "石头", "布", "发送时选择")
        VoiceForwardFeature.ID -> listOf("语音转发", "语音保存", "合并语音", "合并MP3", "转发弹窗", "好友列表", "群聊列表", "语音列表", "批量转发")
        ScheduledTaskFeature.ID -> listOf("定时任务", "定时发送", "群发任务", "计划发送", "循环发送", "每日发送", "每周发送", "朋友圈", "图文", "视文")
        AutoRedPacketFeature.ID -> listOf(
            "自动抢红包", "红包关键词", "红包规则", "规则模板", "回复步骤", "私聊红包回复", "群红包回复", "延迟抢红包", "仅群聊", "仅私聊", "排除名单", "领取后回复"
        )
        AutoTransferFeature.ID -> listOf(
            "自动收款", "转账关键词", "转账规则", "延迟到账", "自动领取转账", "自动收转账", "收款回复",
            "收款模板", "适用聊天", "批量套用", "随机延迟", "禁收时段", "收款通知", "收款播报", "媒体回复"
        )
        FakeWalletBalanceFeature.ID -> listOf("伪造零钱", "钱包余额", "零钱余额", "零钱通", "经营账户", "金额显示")
        RedPacketDetailsSettingsProvider.FEATURE_ID -> listOf("红包显示详情", "红包详情", "红包金额", "领取时间")
        CustomNotificationFeature.ID -> listOf(
            "自定义通知", "通知规则", "通知模板", "私聊通知", "群聊通知", "公众号通知", "艾特我", "@我", "艾特全体", "@所有人", "免打扰通知", "微信消息免打扰"
        )
        KeywordNotificationFeature.ID -> listOf(
            "关键词通知", "关键词列表", "关键词模板", "通知模板", "艾特我", "@我", "艾特全体", "@所有人", "允许入库", "消息入库"
        )
        TextSpeechFeature.ID -> listOf(
            "文字转语音播报", "文字转语音", "语音播报", "TTS引擎", "语音引擎", "播报角色", "TTS角色", "声音角色", "音色", "Voice", "自动播放语音", "播放语音消息", "原语音", "音键控制", "音量键控制", "播报发送人", "免打扰", "开始时间", "结束时间", "设置允许名单", "允许名单"
        )
        ZombieCheckFeature.ID -> listOf("僵尸粉检测", "好友关系检测", "单向好友", "删除好友", "异常好友", "好友标签")
        QQMusicOrderFeature.ID -> listOf("QQ点歌", "点歌", "音乐卡片", "点歌发送卡片", "点歌发送语音", "触发词", "歌手", "封面", "AppID", "允许点歌聊天")
        GroupChatLabelFeature.ID -> listOf("群聊标签", "群组分类", "群聊分类", "标签群聊", "批量选择群聊")
        QuickGroupChatLabelFeature.ID -> listOf("快捷设置群聊标签", "群聊标签", "会话长按菜单", "聊天分组", "群聊分类")
        GroupLeaveMonitorFeature.ID -> listOf("进退群监控", "进群邀请", "退群提醒", "监听群", "批量保护", "新群保护", "群成员变化")
        GroupRenameMonitorFeature.ID -> listOf("改名监控", "群昵称", "群内昵称", "改名提醒", "改名卡片", "改名模板", "批量套用", "改名系统消息")
        MomentsAutoLikeFeature.ID -> listOf("朋友圈自动点赞", "自动点赞", "点赞自己的朋友圈", "点赞白名单", "点赞黑名单", "同一人每天点赞数量", "每日点赞上限", "随机延迟", "发布时间限制", "关键词过滤", "链接朋友圈", "音乐朋友圈", "卡片朋友圈", "未知类型朋友圈")
        MomentsAutoCommentFeature.ID -> listOf("朋友圈自动评论", "自动评论", "评论内容", "时间变量", "评论时间", "时间格式", "评论自己的朋友圈", "评论白名单", "评论黑名单", "同一人每天评论数量", "每日评论上限", "评论延迟", "随机延迟", "发布时间限制", "关键词过滤", "评论日志", "链接朋友圈", "音乐朋友圈", "卡片朋友圈", "未知类型朋友圈")
        MomentsAutoForwardFeature.ID -> listOf("朋友圈自动转发", "静默转发", "指定好友", "每日上限", "包含关键词", "排除关键词", "替换关键词", "替换规则", "文案替换", "文案模板", "链接朋友圈", "音乐朋友圈", "卡片朋友圈", "未知类型朋友圈")
        MomentsAutoRefreshFeature.ID -> listOf("朋友圈自动刷新", "后台刷新", "原生刷新", "刷新间隔", "刷新时段")
        MomentsContactFilterFeature.ID -> listOf("朋友圈过滤", "好友过滤", "过滤好友", "只看好友", "朋友圈白名单", "朋友圈黑名单", "好友标签", "聊天分组")
        MomentsKeywordBlockFeature.ID -> listOf("朋友圈关键词屏蔽", "朋友圈屏蔽", "关键词过滤", "隐藏朋友圈")
        MomentsBottomDetailFeature.ID -> listOf("朋友圈底部详情", "朋友圈时间", "底部时间", "自定义时间", "时间格式", "可见范围")
        MomentsPostNotificationFeature.ID -> listOf("朋友圈发布通知", "朋友圈提醒", "好友发布朋友圈", "朋友圈Toast", "通知模板")
        OriginalMomentsUploadFeature.ID -> listOf("朋友圈原图", "原图上传", "原视频", "原视频上传", "不压缩", "朋友圈图片", "朋友圈视频")
        MomentsUploadTailFeature.ID -> listOf("朋友圈上传尾巴", "SDK ID", "SDK名称", "SDK来源", "第三方来源")
        SnsAntiRecallFeature.ID -> listOf(
            "朋友圈防撤回", "朋友圈评论防撤回", "评论防撤回", "已删除标签", "自定义撤回提示", "评论撤回提示", "提示开关", "三天可见", "个人主页朋友圈", "发现页朋友圈", "强制旧版个人主页朋友圈"
        )
        MomentsFakeLikeSettingsProvider.ID -> listOf("朋友圈伪集赞", "伪集赞[H]", "本地点赞", "使用非好友", "群成员", "随机排序", "自动勾选好友", "排除名单", "随机选择好友", "凭空生成点赞", "虚拟点赞人", "伪造点赞数量", "隐藏长按菜单", "自定义文本")
        MomentsFakeCommentSettingsProvider.ID -> listOf("朋友圈伪评论", "伪评论[H]", "使用非好友", "群成员", "多选好友", "全选好友", "随机评论", "添加新评论", "评论池", "评论时间", "评论顺序", "隐藏长按菜单", "自定义文本", "清除伪评论")
        MomentsFakeForwardSettingsProvider.ID -> listOf("朋友圈伪转发", "伪转发[H]", "本地朋友圈", "日期", "时间", "重复转发", "隐藏长按菜单", "自定义文本", "清空伪转发", "调试日志")
        RemoveMomentsAdsFeature.ID -> listOf("去除朋友圈广告", "朋友圈广告", "广告屏蔽")
        MessageBubbleFeature.ID -> listOf("消息气泡", "聊天气泡", "自定义气泡", "左侧气泡", "右侧气泡", "红包气泡", "转账气泡", "系统消息气泡", "浅色模式", "深色模式", "NinePatch", "九宫格")
        MessageTextColorFeature.ID -> listOf("消息文本颜色", "文字颜色", "聊天气泡文字", "取色器", "浅色模式", "深色模式")
        HomeTextColorFeature.ID -> listOf("首页文字颜色", "标题颜色", "副标题颜色", "渐变文字", "微信首页", "通讯录", "发现", "我")
        ChatTimeStyleFeature.ID -> listOf("会话时间样式", "聊天时间", "微信时间", "自定义时间", "隐藏时间", "时间格式")
        HideChatAvatarFeature.ID -> listOf("隐藏头像", "隐藏自己头像", "隐藏对方头像", "聊天头像", "群聊头像", "私聊头像")
        CustomBottomBarFeature.ID -> listOf(
            "自定义底栏", "底部导航", "微信首页", "修改图标", "修改标题", "隐藏标题", "隐藏底栏"
        )
        FloatingBottomBarSettings.FEATURE_ID -> listOf(
            "悬浮底栏", "底部导航", "微信首页", "液态玻璃", "模糊半径", "隐藏标签", "显示角标", "振动反馈", "振动强度"
        )
        RoundAvatarFeature.ID -> listOf("圆角头像", "圆形头像", "头像圆角", "头像弧度", "朋友圈头像", "公众号头像", "通知头像")
        SettingsFeature.ID -> listOf(
            "设置入口",
            "微信设置入口",
            "加号菜单入口",
            "长按加号",
            "启动器入口",
            "入口开关"
        )
        FloatingShortcutFeature.ID -> listOf(
            "悬浮快捷菜单", "悬浮按钮", "快捷入口",
            "按钮外观", "主按钮大小", "主按钮颜色", "副按钮大小", "副按钮颜色", "按钮渐变",
            "菜单名称颜色", "菜单名称大小", "菜单文字",
            "微信页面", "扫一扫", "朋友圈", "视频号", "收藏", "钱包"
        )
        ProfileIdFeature.ID -> listOf("显示ID", "好友ID", "群聊ID", "wxid", "复制ID", "资料页ID")
        QuickContactEditFeature.ID -> listOf("快捷设置备注和标签", "好友备注", "好友标签", "新建标签", "会话长按菜单", "朋友圈头像")
        QuickMomentsFeature.ID -> listOf("快捷查看朋友圈", "朋友圈", "会话长按菜单", "个人朋友圈", "聊天分组", "自己", "企业微信联系人")
        QuickTerminateFeature.ID -> listOf("快捷终止", "结束微信", "终止微信", "加号菜单", "关闭微信")
        HideChatMenuFeature.ID -> listOf("隐藏聊天菜单", "长按菜单", "隐藏菜单项", "提醒", "搜一搜", "收藏")
        BackgroundRunAssistFeature.ID -> listOf("后台运行辅助", "WakeLock", "Root Doze 白名单", "后台 AppOps")
        WeChatTabletFeature.ID -> listOf("平板模式", "Pad模式", "设备登录", "多设备登录", "登录为平板")
        DisableHotUpdateFeature.ID -> listOf("屏蔽热更新", "禁用热更新", "Tinker", "补丁", "热补丁")
        FakeScanCameraFeature.ID -> listOf("伪造扫码相机", "扫码相机", "扫一扫", "相册扫码", "扫码入口")
        SkipWebRiskSettingsProvider.FEATURE_ID -> listOf("跳过网页风险", "网页风险", "WebView", "风险拦截")
        EditMessageFeature.ID -> listOf("修改聊天记录", "编辑消息", "改消息", "消息XML", "发送XML")
        ProtobufPacketFeature.ID -> listOf("Protobuf抓包", "Protobuf发包", "抓包", "发包", "pb", "网络包", "重放")
        CrashReportSettingsProvider.ID -> listOf("捕获异常日志", "异常日志", "闪退日志", "Java异常", "Native异常", "ANR", "调试")
        StatusTextLimitFeature.ID -> listOf("解除状态词长度限制", "状态词", "微信状态", "状态长度", "字数限制")
        RealNameTailFeature.ID -> listOf("实名尾巴", "群聊尾巴", "实名后缀", "尾巴格式", "群成员实名")
        GroupNicknameColorSettings.FEATURE_ID -> listOf("群昵称自定义颜色", "群昵称颜色", "群内昵称颜色", "昵称渐变", "昵称粗细", "颜色取色器")
        MemberTitleFeature.ID -> listOf("群成员头衔", "头衔颜色", "自定义头衔", "头衔模板", "群昵称头衔", "颜色取色器")
        else -> emptyList()
    }
}

private fun containsSearchText(text: String, query: String): Boolean {
    return text.contains(query, ignoreCase = true)
}

private fun readSearchHistory(sp: SharedPreferences): List<String> {
    val raw = sp.getString(KEY_SEARCH_HISTORY, "").orEmpty()
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val keyword = array.optString(index).trim()
                if (keyword.isNotEmpty() && keyword !in this) add(keyword)
                if (size >= SEARCH_HISTORY_LIMIT) break
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveSearchHistory(sp: SharedPreferences, history: List<String>) {
    val array = JSONArray()
    history.take(SEARCH_HISTORY_LIMIT).forEach { array.put(it) }
    sp.edit().putString(KEY_SEARCH_HISTORY, array.toString()).apply()
}

private fun rememberSearchKeyword(sp: SharedPreferences, keyword: String): List<String> {
    val value = keyword.trim()
    if (value.isBlank()) return readSearchHistory(sp)
    val next = (listOf(value) + readSearchHistory(sp).filterNot { it.equals(value, ignoreCase = true) })
        .take(SEARCH_HISTORY_LIMIT)
    saveSearchHistory(sp, next)
    return next
}

internal sealed class FeatureSubRoute {
    object Main : FeatureSubRoute()
    data class ContactPicker(val request: ContactPickerRequest) : FeatureSubRoute()
    data class OptionPicker(val request: OptionPickerRequest) : FeatureSubRoute()
}

internal sealed class MomentsAutoLikeRoute {
    object Main : MomentsAutoLikeRoute()
    data class ContactPicker(val listMode: Int, val request: ContactPickerRequest) : MomentsAutoLikeRoute()
}

internal sealed class AtAllNotificationBlockRoute {
    object Main : AtAllNotificationBlockRoute()
    data class GroupPicker(val request: ContactPickerRequest) : AtAllNotificationBlockRoute()
}

internal sealed class MomentsAutoCommentRoute {
    object Main : MomentsAutoCommentRoute()
    data class ContactPicker(val listMode: Int, val request: ContactPickerRequest) : MomentsAutoCommentRoute()
}

internal sealed class MomentsAutoForwardRoute {
    object Main : MomentsAutoForwardRoute()
    object ReplacementRules : MomentsAutoForwardRoute()
    data class ContactPicker(val request: ContactPickerRequest) : MomentsAutoForwardRoute()
}

internal sealed class MomentsContactFilterRoute {
    object Main : MomentsContactFilterRoute()
    data class ContactPicker(val request: ContactPickerRequest) : MomentsContactFilterRoute()
}

internal sealed class MomentsPostNotificationRoute {
    object Main : MomentsPostNotificationRoute()
    data class ContactPicker(val request: ContactPickerRequest) : MomentsPostNotificationRoute()
}

internal sealed class RedPacketRoute {
    object Main : RedPacketRoute()
    object TemplateManager : RedPacketRoute()
    object ListManager : RedPacketRoute()
    object ReplySteps : RedPacketRoute()
    data class TemplateEditor(val request: RedPacketTemplateEditorRequest) : RedPacketRoute()
    data class BindingEditor(val request: RedPacketBindingEditorRequest) : RedPacketRoute()
    data class ContactPicker(val request: ContactPickerRequest) : RedPacketRoute()
    data class RuleContactPicker(val request: MessageBlockContactPickerRequest) : RedPacketRoute()
    data class OptionPicker(val request: OptionPickerRequest) : RedPacketRoute()
}

internal sealed class AutoTransferRoute {
    object Main : AutoTransferRoute()
    object TemplateManager : AutoTransferRoute()
    object BindingManager : AutoTransferRoute()
    object BatchApply : AutoTransferRoute()
    data class GlobalReplySteps(val target: TransferReplyTarget) : AutoTransferRoute()
    data class TemplateEditor(val request: TransferTemplateEditorRequest) : AutoTransferRoute()
    data class BindingEditor(val request: TransferBindingEditorRequest) : AutoTransferRoute()
    data class ContactPicker(val request: ContactPickerRequest) : AutoTransferRoute()
}

internal enum class TransferReplyTarget {
    PRIVATE,
    GROUP
}

internal sealed class MessageBlockRoute {
    object Main : MessageBlockRoute()
    object TemplateManager : MessageBlockRoute()
    object ListManager : MessageBlockRoute()
    data class DefaultRuleEditor(val kind: MessageBlockDefaultRuleKind) : MessageBlockRoute()
    data class TemplateEditor(val request: MessageBlockTemplateEditorRequest) : MessageBlockRoute()
    data class BindingEditor(val request: MessageBlockBindingEditorRequest) : MessageBlockRoute()
    data class BatchBindingEditor(val request: MessageBlockBatchBindingEditorRequest) : MessageBlockRoute()
    data class ContactPicker(val request: ContactPickerRequest) : MessageBlockRoute()
    data class GroupMemberPicker(val request: GroupMemberPickerRequest) : MessageBlockRoute()
}

internal sealed class GroupLeaveRoute {
    object Main : GroupLeaveRoute()
    object GroupManager : GroupLeaveRoute()
    object TemplateManager : GroupLeaveRoute()
    object BatchTemplateBinding : GroupLeaveRoute()
    data class TemplateEditor(val request: GroupLeaveTemplateEditorRequest) : GroupLeaveRoute()
    data class GroupEditor(val groupId: String, val label: String) : GroupLeaveRoute()
    data class ContactPicker(val request: ContactPickerRequest) : GroupLeaveRoute()
    data class FavoritePicker(val request: FavoritePickerRequest) : GroupLeaveRoute()
}

internal sealed class GroupChatLabelRoute {
    object Main : GroupChatLabelRoute()
    data class Editor(val label: GroupChatLabel, val existing: Boolean) : GroupChatLabelRoute()
    data class GroupPicker(val label: GroupChatLabel, val existing: Boolean) : GroupChatLabelRoute()
}

internal sealed class ConversationGroupRoute {
    object Main : ConversationGroupRoute()
    data class Editor(val group: ConversationGroup, val existing: Boolean) : ConversationGroupRoute()
    data class ConversationPicker(val group: ConversationGroup, val existing: Boolean) : ConversationGroupRoute()
    data class ParentPicker(val group: ConversationGroup, val existing: Boolean) : ConversationGroupRoute()
    data class AutomaticGrouping(val group: ConversationGroup) : ConversationGroupRoute()
    data class AutomaticPicker(
        val group: ConversationGroup,
        val kind: ConversationGroupAutomaticPickerKind
    ) : ConversationGroupRoute()
}

internal enum class ConversationGroupAutomaticPickerKind(
    val title: String,
    val emptyText: String
) {
    GROUPS("选择自动归拢群聊", "没有可选群聊"),
    OFFICIAL_INCLUDE("选择要包含的公众号", "没有可选公众号"),
    OFFICIAL_EXCLUDE("选择要排除的公众号", "没有可选公众号")
}

internal sealed class GroupRenameRoute {
    object Main : GroupRenameRoute()
    object GroupManager : GroupRenameRoute()
    object TemplateManager : GroupRenameRoute()
    object BatchTemplateBinding : GroupRenameRoute()
    data class GroupEditor(val groupId: String, val label: String) : GroupRenameRoute()
    data class TemplateEditor(val index: Int, val template: GroupRenameReplyTemplate, val canDelete: Boolean) : GroupRenameRoute()
    data class ContactPicker(val request: ContactPickerRequest, val fromMain: Boolean = false) : GroupRenameRoute()
}

internal sealed class ScheduledTaskRoute {
    object Main : ScheduledTaskRoute()
    object Editor : ScheduledTaskRoute()
    object ContactPicker : ScheduledTaskRoute()
    data class FavoritePicker(val index: Int, val request: FavoritePickerRequest) : ScheduledTaskRoute()
}

internal sealed class SelectedMessagesRoute {
    object Main : SelectedMessagesRoute()
    object Editor : SelectedMessagesRoute()
    object ContactPicker : SelectedMessagesRoute()
    data class FavoritePicker(val request: FavoritePickerRequest) : SelectedMessagesRoute()
}

internal sealed class AudioTransformRoute {
    object Main : AudioTransformRoute()
    object ContactPicker : AudioTransformRoute()
}

internal sealed class CustomNotificationRoute {
    object Main : CustomNotificationRoute()
    object RuleList : CustomNotificationRoute()
    object BatchEditor : CustomNotificationRoute()
    data class DefaultEditor(val kind: CustomNotificationDefaultKind) : CustomNotificationRoute()
    data class RuleEditor(val talker: String) : CustomNotificationRoute()
    data class ContactPicker(val request: ContactPickerRequest) : CustomNotificationRoute()
    data class GroupMemberPicker(val talker: String, val request: GroupMemberPickerRequest) : CustomNotificationRoute()
}

internal sealed class KeywordNotificationRoute {
    object Main : KeywordNotificationRoute()
    object KeywordList : KeywordNotificationRoute()
    object TemplateEditor : KeywordNotificationRoute()
    data class KeywordEditor(val oldKeyword: String?) : KeywordNotificationRoute()
    data class ContactPicker(val request: ContactPickerRequest, val includeMode: Boolean) : KeywordNotificationRoute()
}

internal sealed class TextSpeechRoute {
    object Main : TextSpeechRoute()
    data class ContactPicker(val request: ContactPickerRequest) : TextSpeechRoute()
}

internal enum class MessageBlockDefaultRuleKind {
    PRIVATE,
    GROUP,
    OFFICIAL
}

internal enum class CustomNotificationDefaultKind {
    PRIVATE,
    GROUP,
    OFFICIAL
}

internal data class AutoReplyFriendAutomationKeys(
    val labelNewFriendEnable: String,
    val labelDateEnable: String,
    val labelDateFormat: String,
    val labelExistingEnable: String,
    val labelSelectedNames: String,
    val remarkNewFriendEnable: String,
    val remarkNicknameSuffixEnable: String,
    val remarkDateEnable: String,
    val remarkDateFormat: String,
    val remarkCustomEnable: String,
    val remarkCustomText: String
)

internal sealed class AutoReplyEditorRoute {
    object Main : AutoReplyEditorRoute()
    data class OptionPicker(val request: OptionPickerRequest) : AutoReplyEditorRoute()
    data class ContactPicker(val request: ContactPickerRequest) : AutoReplyEditorRoute()
    data class GroupMemberPicker(val request: GroupMemberPickerRequest) : AutoReplyEditorRoute()
}

internal sealed class AutoReplyStepsRoute {
    object Main : AutoReplyStepsRoute()
    data class OptionPicker(val request: OptionPickerRequest) : AutoReplyStepsRoute()
    data class ContactPicker(val request: ContactPickerRequest) : AutoReplyStepsRoute()
    data class FavoritePicker(val request: FavoritePickerRequest) : AutoReplyStepsRoute()
}

internal sealed class AutoReplyAiRoute {
    object Main : AutoReplyAiRoute()
    object Xiaozhi : AutoReplyAiRoute()
    object Zhilia : AutoReplyAiRoute()
    object ZhiliaConfigs : AutoReplyAiRoute()
    object ZhiliaModels : AutoReplyAiRoute()
    data class OptionPicker(val request: OptionPickerRequest) : AutoReplyAiRoute()
}

internal sealed class RedPacketReplyStepsRoute {
    object Main : RedPacketReplyStepsRoute()
    data class StepEditor(val index: Int) : RedPacketReplyStepsRoute()
    data class FavoritePicker(val request: FavoritePickerRequest) : RedPacketReplyStepsRoute()
}

internal sealed class MessageBlockContactPickerRoute {
    object Labels : MessageBlockContactPickerRoute()
    data class Contacts(
        val filter: MessageBlockContactFilter,
        val label: MessageBlockLabelOption?
    ) : MessageBlockContactPickerRoute()
}

internal sealed class GroupMemberPickerRoute {
    object Groups : GroupMemberPickerRoute()
    data class Members(val group: ContactOption) : GroupMemberPickerRoute()
}

internal fun FeatureSubRoute.depth(): Int = when (this) {
    FeatureSubRoute.Main -> 0
    is FeatureSubRoute.ContactPicker,
    is FeatureSubRoute.OptionPicker -> 1
}

internal fun RedPacketRoute.depth(): Int = when (this) {
    RedPacketRoute.Main -> 0
    RedPacketRoute.TemplateManager,
    RedPacketRoute.ListManager,
    RedPacketRoute.ReplySteps,
    is RedPacketRoute.ContactPicker,
    is RedPacketRoute.OptionPicker -> 1
    is RedPacketRoute.TemplateEditor,
    is RedPacketRoute.BindingEditor,
    is RedPacketRoute.RuleContactPicker -> 2
}

internal fun AutoTransferRoute.depth(): Int = when (this) {
    AutoTransferRoute.Main -> 0
    AutoTransferRoute.TemplateManager,
    AutoTransferRoute.BindingManager,
    AutoTransferRoute.BatchApply,
    is AutoTransferRoute.GlobalReplySteps,
    is AutoTransferRoute.ContactPicker -> 1
    is AutoTransferRoute.TemplateEditor,
    is AutoTransferRoute.BindingEditor -> 2
}

internal fun MessageBlockRoute.depth(): Int = when (this) {
    MessageBlockRoute.Main -> 0
    MessageBlockRoute.TemplateManager,
    is MessageBlockRoute.DefaultRuleEditor,
    MessageBlockRoute.ListManager -> 1
    is MessageBlockRoute.TemplateEditor,
    is MessageBlockRoute.BindingEditor,
    is MessageBlockRoute.BatchBindingEditor,
    is MessageBlockRoute.ContactPicker,
    is MessageBlockRoute.GroupMemberPicker -> 2
}

internal fun GroupLeaveRoute.depth(): Int = when (this) {
    GroupLeaveRoute.Main -> 0
    GroupLeaveRoute.GroupManager,
    GroupLeaveRoute.TemplateManager,
    GroupLeaveRoute.BatchTemplateBinding,
    is GroupLeaveRoute.ContactPicker,
    is GroupLeaveRoute.FavoritePicker -> 1
    is GroupLeaveRoute.TemplateEditor,
    is GroupLeaveRoute.GroupEditor -> 2
}

internal fun ScheduledTaskRoute.depth(): Int = when (this) {
    ScheduledTaskRoute.Main -> 0
    ScheduledTaskRoute.Editor -> 1
    ScheduledTaskRoute.ContactPicker,
    is ScheduledTaskRoute.FavoritePicker -> 2
}

internal fun SelectedMessagesRoute.depth(): Int = when (this) {
    SelectedMessagesRoute.Main -> 0
    SelectedMessagesRoute.Editor -> 1
    SelectedMessagesRoute.ContactPicker,
    is SelectedMessagesRoute.FavoritePicker -> 2
}

internal fun AudioTransformRoute.depth(): Int = when (this) {
    AudioTransformRoute.Main -> 0
    AudioTransformRoute.ContactPicker -> 1
}

internal fun CustomNotificationRoute.depth(): Int = when (this) {
    CustomNotificationRoute.Main -> 0
    CustomNotificationRoute.RuleList,
    is CustomNotificationRoute.DefaultEditor,
    is CustomNotificationRoute.ContactPicker -> 1
    CustomNotificationRoute.BatchEditor,
    is CustomNotificationRoute.RuleEditor,
    is CustomNotificationRoute.GroupMemberPicker -> 2
}

internal fun KeywordNotificationRoute.depth(): Int = when (this) {
    KeywordNotificationRoute.Main -> 0
    KeywordNotificationRoute.KeywordList,
    KeywordNotificationRoute.TemplateEditor,
    is KeywordNotificationRoute.ContactPicker -> 1
    is KeywordNotificationRoute.KeywordEditor -> 2
}

internal fun TextSpeechRoute.depth(): Int = when (this) {
    TextSpeechRoute.Main -> 0
    is TextSpeechRoute.ContactPicker -> 1
}

internal fun autoReplyRouteDepth(route: String): Int = when (route) {
    "main" -> 0
    "rules",
    "autoAccept",
    "greetAccepted",
    "ai" -> 1
    "ruleEditor",
    "steps",
    "autoAcceptLabels",
    "greetAcceptedLabels" -> 2
    else -> 0
}

internal fun AutoReplyEditorRoute.depth(): Int = when (this) {
    AutoReplyEditorRoute.Main -> 0
    is AutoReplyEditorRoute.OptionPicker,
    is AutoReplyEditorRoute.ContactPicker,
    is AutoReplyEditorRoute.GroupMemberPicker -> 1
}

internal fun AutoReplyStepsRoute.depth(): Int = when (this) {
    AutoReplyStepsRoute.Main -> 0
    is AutoReplyStepsRoute.OptionPicker,
    is AutoReplyStepsRoute.ContactPicker,
    is AutoReplyStepsRoute.FavoritePicker -> 1
}

internal fun AutoReplyAiRoute.depth(): Int = when (this) {
    AutoReplyAiRoute.Main -> 0
    AutoReplyAiRoute.Xiaozhi,
    AutoReplyAiRoute.Zhilia -> 1
    AutoReplyAiRoute.ZhiliaConfigs,
    AutoReplyAiRoute.ZhiliaModels,
    is AutoReplyAiRoute.OptionPicker -> 2
}

internal fun RedPacketReplyStepsRoute.depth(): Int = when (this) {
    RedPacketReplyStepsRoute.Main -> 0
    is RedPacketReplyStepsRoute.StepEditor -> 1
    is RedPacketReplyStepsRoute.FavoritePicker -> 2
}

internal fun MessageBlockContactPickerRoute.depth(): Int = when (this) {
    MessageBlockContactPickerRoute.Labels -> 0
    is MessageBlockContactPickerRoute.Contacts -> if (label == null) 0 else 1
}

internal fun GroupMemberPickerRoute.depth(): Int = when (this) {
    GroupMemberPickerRoute.Groups -> 0
    is GroupMemberPickerRoute.Members -> 1
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun <T> SettingsRouteTransition(
    targetState: T,
    modifier: Modifier = Modifier.fillMaxSize(),
    label: String,
    depthOf: (T) -> Int,
    horizontalOnSameDepth: Boolean = true,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            val initialDepth = depthOf(initialState)
            val targetDepth = depthOf(targetState)
            if (!horizontalOnSameDepth && initialDepth == targetDepth) {
                fadeIn(animationSpec = tween(120)) togetherWith
                    fadeOut(animationSpec = tween(90))
            } else {
                val forward = targetDepth >= initialDepth
                val enter = slideInHorizontally(
                    animationSpec = tween(240),
                    initialOffsetX = { width -> if (forward) width else -width / 4 }
                ) + fadeIn(animationSpec = tween(160))
                val exit = slideOutHorizontally(
                    animationSpec = tween(220),
                    targetOffsetX = { width -> if (forward) -width / 4 else width }
                ) + fadeOut(animationSpec = tween(140))
                enter togetherWith exit
            }
        },
        modifier = modifier.background(MiuixTheme.colorScheme.background),
        label = label
    ) { route ->
        content(route)
    }
}

@Composable
private fun MainNavigationBar(
    selectedTab: MainTab,
    floating: Boolean,
    glass: Boolean,
    backdrop: Backdrop,
    onSelected: (MainTab) -> Unit
) {
    val items = remember {
        listOf(
            MainNavItem(MainTab.PRACTICAL, "实用", NavIcons.Practical),
            MainNavItem(MainTab.ENTERTAINMENT, "娱乐", NavIcons.Entertainment),
            MainNavItem(MainTab.SETTINGS, "设置", NavIcons.Settings)
        )
    }
    val selectedIndex = items.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
    val contentColor = MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (floating) 12.dp else 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingBottomBar(
            selectedIndex = selectedIndex,
            onSelected = { index -> onSelected(items[index].tab) },
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = glass,
            floating = floating
        ) { selectTab ->
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                FloatingBottomBarItem(
                    onClick = { selectTab(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Image(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            colorFilter = ColorFilter.tint(contentColor),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        color = contentColor,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun GiftMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, GiftSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    var successCount by remember { mutableStateOf(sp.getInt(GiftSettings.KEY_SUCCESS, 0)) }
    var failedCount by remember { mutableStateOf(sp.getInt(GiftSettings.KEY_FAILED, 0)) }
    var lastOrder by remember { mutableStateOf(sp.getString(GiftSettings.KEY_LAST_ORDER, "").orEmpty()) }
    var lastTime by remember { mutableStateOf(sp.getLong(GiftSettings.KEY_LAST_TIME, 0L)) }
    var delayText by remember { mutableStateOf(sp.getInt(GiftSettings.KEY_DELAY_VALUE, 0).toString()) }

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
            item { SmallTitle(text = "自动领取") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        GiftSettings.KEY_ENABLE,
                        "自动抢礼物",
                        "收到礼物消息时自动静默领取",
                        false
                    )
                    SwitchRow(
                        sp,
                        GiftSettings.KEY_LOG_ENABLE,
                        "记录日志",
                        "在 LSPosed 日志中输出领取过程，便于排查问题",
                        false
                    )
                }
            }
            item { SmallTitle(text = "领取设置") }
            item {
                SettingsCard {
                    InputRow(
                        title = "延迟领取",
                        summary = "收到礼物后延迟多少毫秒再领取，0 为立即领取",
                        value = delayText,
                        onValueChange = { raw ->
                            val text = raw.filter { it.isDigit() }.take(6)
                            delayText = text
                            sp.edit()
                                .putInt(GiftSettings.KEY_DELAY_VALUE, text.toIntOrNull() ?: 0)
                                .putInt(GiftSettings.KEY_DELAY_UNIT, 0)
                                .apply()
                        }
                    )
                }
            }
            item { SmallTitle(text = "领取统计") }
            item {
                SettingsCard {
                    InfoRow("成功", successCount.toString())
                    InfoRow("失败", failedCount.toString())
                    InfoRow("最近订单", lastOrder.ifEmpty { "无" })
                    InfoRow("最近时间", if (lastTime > 0L) timeFormat.format(Date(lastTime)) else "无")
                    InfoRow("刷新统计", "") {
                        successCount = sp.getInt(GiftSettings.KEY_SUCCESS, 0)
                        failedCount = sp.getInt(GiftSettings.KEY_FAILED, 0)
                        lastOrder = sp.getString(GiftSettings.KEY_LAST_ORDER, "").orEmpty()
                        lastTime = sp.getLong(GiftSettings.KEY_LAST_TIME, 0L)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeatureSettingsPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit,
) {
    when (provider.featureId()) {
        AutoRedPacketFeature.ID -> RedPacketMiuixPage(context, provider, onBack)
        AutoGiftFeature.ID -> GiftMiuixPage(context, provider, onBack)
        AutoTransferFeature.ID -> AutoTransferMiuixPage(context, provider, onBack)
        FakeWalletBalanceFeature.ID -> FakeWalletBalanceMiuixPage(context, provider, onBack)
        RealNameTailFeature.ID -> RealNameTailMiuixPage(context, provider, onBack)
        GroupNicknameColorSettings.FEATURE_ID -> GroupNicknameColorMiuixPage(context, provider, onBack)
        MemberTitleFeature.ID -> MemberTitleMiuixPage(context, provider, onBack)
        AntiRecallFeature.ID -> AntiRecallMiuixPage(context, provider, onBack)
        MultiRecallFeature.ID -> MultiRecallMiuixPage(context, provider, onBack)
        MessageForwardFeature.ID -> MessageForwardMiuixPage(context, provider, onBack)
        SelectedMessagesFeature.ID -> SelectedMessagesMiuixPage(context, provider, onBack)
        CallMediaLimitFeature.ID -> CallMediaLimitMiuixPage(context, provider, onBack)
        CallRingtoneBlockFeature.ID -> CallRingtoneBlockMiuixPage(context, provider, onBack)
        DisableHotUpdateFeature.ID -> DisableHotUpdateMiuixPage(context, provider, onBack)
        ProfileIdFeature.ID -> ProfileIdMiuixPage(context, provider, onBack)
        QuickContactEditFeature.ID -> QuickContactEditMiuixPage(context, provider, onBack)
        QuickGroupChatLabelFeature.ID -> QuickGroupChatLabelMiuixPage(context, provider, onBack)
        QuickMomentsFeature.ID -> QuickMomentsMiuixPage(context, provider, onBack)
        QuickTerminateFeature.ID -> QuickTerminateMiuixPage(context, provider, onBack)
        HideChatMenuFeature.ID -> HideChatMenuMiuixPage(context, provider, onBack)
        MessageAffixFeature.ID -> MessageAffixMiuixPage(context, provider, onBack)
        TypingReportBlockFeature.ID -> TypingReportBlockMiuixPage(context, provider, onBack)
        PatBlockFeature.ID -> PatBlockMiuixPage(context, provider, onBack)
        AutoOriginalImageFeature.ID -> AutoOriginalImageMiuixPage(context, provider, onBack)
        AutoViewOriginalFeature.ID -> AutoViewOriginalMiuixPage(context, provider, onBack)
        RemoveForwardLimitFeature.ID -> RemoveForwardLimitMiuixPage(context, provider, onBack)
        AtAllNotificationBlockFeature.ID -> AtAllNotificationBlockMiuixPage(context, provider, onBack)
        SettingsFeature.ID -> SettingsEntryMiuixPage(context, provider, onBack)
        FloatingShortcutFeature.ID -> FloatingShortcutMiuixPage(context, provider, onBack)
        MessageDetailsSettingsProvider.FEATURE_ID -> MessageDetailsConfigPage(
            context = context,
            sp = HchatStorage.preferences(context, HchatExtraSettings.PREFS_NAME),
            onBack = onBack
        )
        GroupMemberHistorySettingsProvider.FEATURE_ID -> HchatExtraToggleMiuixPage(
            context = context,
            provider = provider,
            key = HchatExtraSettings.KEY_GROUP_MEMBER_HISTORY,
            defaultValue = HchatExtraSettings.DEFAULT_GROUP_MEMBER_HISTORY,
            summary = "在群成员资料页添加历史发言记录入口",
            onBack = onBack
        )
        RedPacketDetailsSettingsProvider.FEATURE_ID -> HchatExtraToggleMiuixPage(
            context = context,
            provider = provider,
            key = HchatExtraSettings.KEY_RED_PACKET_DETAILS,
            defaultValue = HchatExtraSettings.DEFAULT_RED_PACKET_DETAILS,
            summary = "红包详情页显示金额、个数和领取时间",
            onBack = onBack
        )
        SkipWebRiskSettingsProvider.FEATURE_ID -> HchatExtraToggleMiuixPage(
            context = context,
            provider = provider,
            key = HchatExtraSettings.KEY_SKIP_WEB_RISK,
            defaultValue = HchatExtraSettings.DEFAULT_SKIP_WEB_RISK,
            summary = "跳过微信 WebView 高风险网页拦截提示",
            onBack = onBack
        )
        QuickMarkReadFeature.ID -> QuickMarkReadMiuixPage(context, provider, onBack)
        AutoReplyFeature.ID -> AutoReplyMiuixPage(context, provider, onBack)
        AutoMessageForwardFeature.ID -> AutoMessageForwardMiuixPage(context, provider, onBack)
        ConversationGroupFeature.ID -> ConversationGroupMiuixPage(context, provider, onBack)
        ConversationTabsFeature.ID -> ConversationTabsMiuixPage(context, onBack)
        CustomNotificationFeature.ID -> CustomNotificationMiuixPage(context, provider, onBack)
        KeywordNotificationFeature.ID -> KeywordNotificationMiuixPage(context, provider, onBack)
        TextSpeechFeature.ID -> TextSpeechMiuixPage(context, provider, onBack)
        ZombieCheckFeature.ID -> ZombieCheckMiuixPage(context, provider, onBack)
        QQMusicOrderFeature.ID -> QQMusicOrderMiuixPage(context, provider, onBack)
        BackgroundRunAssistFeature.ID -> BackgroundRunAssistMiuixPage(context, provider, onBack)
        QuoteDeleteClearFeature.ID -> QuoteDeleteClearMiuixPage(context, provider, onBack)
        EmojiSaveFeature.ID -> EmojiSaveMiuixPage(context, provider, onBack)
        SwipeQuoteFeature.ID -> SwipeQuoteMiuixPage(context, provider, onBack)
        AudioTransformFeature.ID -> AudioTransformSettingsPage(context, provider, onBack)
        TextVoiceFeature.ID -> TextVoiceMiuixPage(context, provider, onBack)
        FakeVoiceDurationFeature.ID -> FakeVoiceDurationMiuixPage(context, provider, onBack)
        VoicePreviewFeature.ID -> VoicePreviewMiuixPage(context, provider, onBack)
        FakeMiniProgramBaseLibFeature.ID -> FakeMiniProgramBaseLibMiuixPage(context, provider, onBack)
        SkipMiniProgramVideoAdsFeature.ID -> SkipMiniProgramVideoAdsMiuixPage(context, provider, onBack)
        SkipGlobalMiniProgramSplashAdsFeature.ID -> SkipGlobalMiniProgramSplashAdsMiuixPage(context, provider, onBack)
        FakeLocationFeature.ID -> FakeLocationMiuixPage(context, provider, onBack)
        GameEmojiFeature.ID -> GameEmojiMiuixPage(context, provider, onBack)
        MessageBubbleFeature.ID -> MessageBubbleMiuixPage(context, provider, onBack)
        MessageTextColorFeature.ID -> MessageTextColorMiuixPage(context, provider, onBack)
        HomeTextColorFeature.ID -> HomeTextColorMiuixPage(context, provider, onBack)
        ChatTimeStyleFeature.ID -> ChatTimeStyleMiuixPage(context, provider, onBack)
        InputHintFeature.ID -> InputHintMiuixPage(context, provider, onBack)
        HideChatAvatarFeature.ID -> HideChatAvatarMiuixPage(context, provider, onBack)
        CustomBottomBarFeature.ID -> CustomBottomBarMiuixPage(context, provider, onBack)
        FloatingBottomBarSettings.FEATURE_ID -> FloatingBottomBarMiuixPage(context, provider, onBack)
        RoundAvatarFeature.ID -> RoundAvatarMiuixPage(context, provider, onBack)
        CustomFriendAvatarFeature.ID -> CustomFriendAvatarMiuixPage(context, provider, onBack)
        MessageBlockFeature.ID -> MessageBlockMiuixPage(context, provider, onBack)
        GroupChatLabelFeature.ID -> GroupChatLabelMiuixPage(context, provider, onBack)
        GroupLeaveMonitorFeature.ID -> GroupLeaveMonitorMiuixPage(context, provider, onBack)
        GroupRenameMonitorFeature.ID -> GroupRenameMonitorMiuixPage(context, provider, onBack)
        MomentsAutoLikeFeature.ID -> MomentsAutoLikeMiuixPage(context, provider, onBack)
        MomentsAutoCommentFeature.ID -> MomentsAutoCommentPage(context, provider, onBack)
        MomentsAutoForwardFeature.ID -> MomentsAutoForwardMiuixPage(context, provider, onBack)
        MomentsAutoRefreshFeature.ID -> MomentsAutoRefreshMiuixPage(context, provider, onBack)
        MomentsContactFilterFeature.ID -> MomentsContactFilterMiuixPage(context, provider, onBack)
        MomentsKeywordBlockFeature.ID -> MomentsKeywordBlockMiuixPage(context, provider, onBack)
        MomentsBottomDetailFeature.ID -> MomentsBottomDetailMiuixPage(context, provider, onBack)
        MomentsPostNotificationFeature.ID -> MomentsPostNotificationMiuixPage(context, provider, onBack)
        OriginalMomentsUploadFeature.ID -> OriginalMomentsUploadMiuixPage(context, provider, onBack)
        MomentsUploadTailFeature.ID -> MomentsUploadTailMiuixPage(context, provider, onBack)
        SnsAntiRecallFeature.ID -> SnsAntiRecallMiuixPage(context, provider, onBack)
        MomentsFakeLikeSettingsProvider.ID,
        MomentsFakeCommentSettingsProvider.ID,
        MomentsFakeForwardSettingsProvider.ID -> MomentsFakeInteractionMiuixPage(context, provider, onBack)
        RemoveMomentsAdsFeature.ID -> RemoveMomentsAdsMiuixPage(context, provider, onBack)
        EditMessageFeature.ID -> EditMessageMiuixPage(context, provider, onBack)
        VoiceForwardFeature.ID -> VoiceForwardMiuixPage(context, provider, onBack)
        ScheduledTaskFeature.ID -> ScheduledTaskMiuixPage(context, provider, onBack)
        FakeScanCameraFeature.ID -> FakeScanCameraMiuixPage(context, provider, onBack)
        FinderMediaDownloadFeature.ID -> FinderMediaDownloadMiuixPage(context, provider, onBack)
        ProtobufPacketFeature.ID -> ProtobufPacketMiuixPage(context, provider, onBack)
        CrashReportSettingsProvider.ID -> CrashReportMiuixPage(context, provider, onBack)
        StatusTextLimitFeature.ID -> StatusTextLimitMiuixPage(context, provider, onBack)
        WeChatTabletFeature.ID -> WeChatTabletMiuixPage(context, provider, onBack)
        else -> UnsupportedFeaturePage(provider, onBack)
    }
}
