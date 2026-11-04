package h.Hchat.ui.miuix

import android.content.Context
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
import h.Hchat.hooks.items.linkparse.LinkParseSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun LinkParseMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { HchatStorage.preferences(context, LinkParseSettings.PREFS_NAME) }
    var mode by remember { mutableStateOf(prefs.getString(LinkParseSettings.KEY_GROUP_MODE, LinkParseSettings.GROUP_OFF).orEmpty()) }
    val modes = listOf(PopupChoice("关闭", LinkParseSettings.GROUP_OFF), PopupChoice("仅 @我", LinkParseSettings.GROUP_AT), PopupChoice("自动", LinkParseSettings.GROUP_AUTO))
    val scroll = MiuixScrollBehavior()
    PageScaffold(title = provider.title(), largeTitle = provider.title(), scrollBehavior = scroll, bottomBar = { BottomActionBar("返回", onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection), state = rememberLazyListState(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)) {
            item { SmallTitle(text = "链接解析") }
            item {
                SettingsCard {
                    SwitchRow(prefs, LinkParseSettings.KEY_ENABLE, "启用链接解析", "私聊自动识别；仅发送公开视频，图集暂不可用", false)
                    InsetDivider()
                    SwitchRow(prefs, LinkParseSettings.KEY_DOUYIN, "抖音", "识别公开作品链接", false)
                    InsetDivider()
                    SwitchRow(prefs, LinkParseSettings.KEY_XHS, "小红书", "识别公开作品链接", false)
                    InsetDivider()
                    SwitchRow(prefs, LinkParseSettings.KEY_KUAISHOU, "快手", "识别公开作品链接", false)
                    InsetDivider()
                    PopupChoiceRow("群聊触发模式", modes.firstOrNull { it.value == mode }?.label ?: "关闭", modes, mode, {
                        mode = it
                        prefs.edit().putString(LinkParseSettings.KEY_GROUP_MODE, it).commit()
                    })
                }
            }
        }
    }
}
