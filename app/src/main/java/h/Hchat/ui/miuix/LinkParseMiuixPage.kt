package h.Hchat.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import h.Hchat.hooks.items.talkerlist.TalkerListMode
import h.Hchat.hooks.items.talkerlist.TalkerListPolicy
import h.Hchat.hooks.items.linkparse.LinkParseSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

private sealed class LinkParseRoute {
    data object Main : LinkParseRoute()
    data class ContactPicker(val request: ContactPickerRequest) : LinkParseRoute()
}

@Composable
internal fun LinkParseMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val settings = remember { LinkParseSettings(context) }
    var route by remember { mutableStateOf<LinkParseRoute>(LinkParseRoute.Main) }
    var listMode by remember { mutableStateOf(settings.listMode()) }
    var listTalkers by remember { mutableStateOf(settings.talkersFor(listMode)) }
    var listIsValid by remember { mutableStateOf(settings.isTalkerListValid(listMode)) }

    SettingsRouteTransition(
        targetState = route,
        label = "LinkParseRoute",
        depthOf = { if (it is LinkParseRoute.Main) 0 else 1 }
    ) { currentRoute ->
        when (currentRoute) {
            LinkParseRoute.Main -> LinkParseMainPage(
                context = context,
                provider = provider,
                listMode = listMode,
                listTalkers = listTalkers,
                listIsValid = listIsValid,
                onBack = onBack,
                onListModeChanged = { value ->
                    TalkerListMode.fromPreferenceValue(value)?.let { selectedMode ->
                        settings.saveListMode(selectedMode)
                        listMode = selectedMode
                        listTalkers = settings.talkersFor(selectedMode)
                        listIsValid = settings.isTalkerListValid(selectedMode)
                    }
                },
                onPickTalkers = {
                    route = LinkParseRoute.ContactPicker(
                        ContactPickerRequest(
                            title = listMode.title,
                            mode = ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = listTalkers.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is LinkParseRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = LinkParseRoute.Main },
                onSelectionChange = { selected ->
                    listTalkers = selected.filter(TalkerListPolicy::isValidChatId).toSet()
                    settings.saveTalkers(listMode, listTalkers)
                    listIsValid = settings.isTalkerListValid(listMode)
                }
            )
        }
    }
}

@Composable
private fun LinkParseMainPage(
    context: Context,
    provider: FeatureSettingsProvider,
    listMode: TalkerListMode,
    listTalkers: Set<String>,
    listIsValid: Boolean,
    onBack: () -> Unit,
    onListModeChanged: (String) -> Unit,
    onPickTalkers: () -> Unit
) {
    val prefs = remember { HchatStorage.preferences(context, LinkParseSettings.PREFS_NAME) }
    val scroll = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scroll,
        onBack = onBack,
        showBackIcon = false
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item { SettingsSectionTitle(text = "链接解析") }
            item {
                SettingsCard {
                    SwitchRow(
                        prefs,
                        LinkParseSettings.KEY_ENABLE,
                        "链接解析",
                        "解析你发送的公开作品链接；他人权限由名单模式决定",
                        false
                    )
                    InsetDivider()
                    SwitchRow(prefs, LinkParseSettings.KEY_DOUYIN, "抖音", "识别公开作品链接", false)
                    InsetDivider()
                    SwitchRow(prefs, LinkParseSettings.KEY_XHS, "小红书", "识别公开作品链接", false)
                    InsetDivider()
                    PopupChoiceRow(
                        title = "名单模式",
                        summary = if (listMode == TalkerListMode.BLACKLIST) {
                            "默认允许所有有效聊天，名单中的聊天禁止他人使用"
                        } else {
                            "仅允许名单中的聊天使用，其他聊天禁止"
                        },
                        options = listOf(
                            PopupChoice("黑名单", TalkerListMode.BLACKLIST.preferenceValue),
                            PopupChoice("白名单", TalkerListMode.WHITELIST.preferenceValue)
                        ),
                        currentValue = listMode.preferenceValue,
                        onValueChanged = onListModeChanged
                    )
                    InsetDivider()
                    ActionRow(
                        listMode.title,
                        talkerListSummary(listMode, listTalkers.size, listIsValid),
                        onPickTalkers
                    )
                }
            }
        }
    }
}

private fun talkerListSummary(mode: TalkerListMode, count: Int, isValid: Boolean): String = when {
    !isValid -> "名单配置异常，当前已拒绝他人使用 · $count 个聊天"
    count > 0 -> "名单内 $count 个聊天"
    mode == TalkerListMode.BLACKLIST -> "所有聊天均可使用 · 0 个聊天"
    else -> "尚未允许任何聊天 · 0 个聊天"
}
