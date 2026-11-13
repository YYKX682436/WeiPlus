package h.Hchat.ui.miuix

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.hooks.items.voiceforward.VoiceForwardSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VoiceForwardMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, VoiceForwardSettings.PREFS_NAME) }
    val legacyDefault = remember { sp.getBoolean(VoiceForwardSettings.KEY_ENABLE, VoiceForwardSettings.DEFAULT_ENABLE) }
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
            item { SmallTitle(text = "聊天语音") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        VoiceForwardSettings.KEY_CHAT_FORWARD_ENABLE,
                        "聊天语音转发",
                        "长按聊天语音后显示转发入口",
                        legacyDefault
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        VoiceForwardSettings.KEY_CHAT_SAVE_ENABLE,
                        "聊天语音保存",
                        "长按聊天语音后显示保存入口",
                        legacyDefault
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        VoiceForwardSettings.KEY_CHAT_MULTI_FORWARD_ENABLE,
                        "多选语音转发",
                        "多选语音后显示逐条转发入口",
                        legacyDefault
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        VoiceForwardSettings.KEY_CHAT_MULTI_MERGE_ENABLE,
                        "多选语音合并",
                        "多选语音后合成为一条语音，可转发或保存为 MP3",
                        legacyDefault
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "收藏语音") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        VoiceForwardSettings.KEY_FAVORITE_FORWARD_ENABLE,
                        "收藏语音转发",
                        "长按收藏语音后显示转发入口",
                        legacyDefault
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        VoiceForwardSettings.KEY_FAVORITE_SAVE_ENABLE,
                        "收藏语音保存",
                        "长按收藏语音后显示保存入口",
                        legacyDefault
                    )
                }
            }
        }
    }
}

@Composable
internal fun FeatureListCard(
    providers: List<FeatureSettingsProvider>,
    emptyText: String,
    onOpenProvider: (FeatureSettingsProvider) -> Unit
) {
    SettingsCard {
        if (providers.isEmpty()) {
            EmptyText(emptyText)
        } else {
            providers.forEachIndexed { index, provider ->
                FeatureRow(
                    provider = provider,
                    onClick = { onOpenProvider(provider) }
                )
                if (index < providers.lastIndex) {
                    InsetDivider()
                }
            }
        }
    }
}

@Composable
internal fun FeatureGroupRow(group: FeatureGroupEntry, onClick: () -> Unit) {
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
            Text(text = group.title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(
                text = when {
                    group.batchOperations -> "${batchOperationsFeatureCount()} 项功能"
                    group.directProviderId != null -> group.providers.firstOrNull { it.featureId() == group.directProviderId }?.subtitle().orEmpty()
                    else -> "${group.providers.size + if (group.hasFriendScenes) 2 else 0} 项功能"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
    }
}

@Composable
private fun FeatureRow(provider: FeatureSettingsProvider, onClick: () -> Unit) {
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
            Text(text = provider.title(), color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(text = provider.subtitle(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
    }
}
