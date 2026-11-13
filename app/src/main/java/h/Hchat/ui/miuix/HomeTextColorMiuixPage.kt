package h.Hchat.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import h.Hchat.hooks.items.hometextcolor.HomeTextColorSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
internal fun HomeTextColorMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, HomeTextColorSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(HomeTextColorSettings.KEY_ENABLE, HomeTextColorSettings.DEFAULT_ENABLE)
        )
    }
    var titleColor by remember {
        mutableStateOf(
            sp.getString(
                HomeTextColorSettings.KEY_TITLE_COLOR,
                HomeTextColorSettings.DEFAULT_TITLE_COLOR
            ).orEmpty()
        )
    }
    var subtitleColor by remember {
        mutableStateOf(
            sp.getString(
                HomeTextColorSettings.KEY_SUBTITLE_COLOR,
                HomeTextColorSettings.DEFAULT_SUBTITLE_COLOR
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
            item { SettingsSectionTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "首页文字颜色",
                        summary = "应用到微信、通讯录、发现和我",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(HomeTextColorSettings.KEY_ENABLE, it).apply()
                        }
                    )
                }
            }
            item { SettingsSectionTitle(text = "颜色") }
            item {
                SettingsCard {
                    ColorPickerRow(
                        title = "标题颜色",
                        summary = "会话名称、联系人名称和菜单标题",
                        value = titleColor,
                        onReset = {
                            titleColor = HomeTextColorSettings.DEFAULT_TITLE_COLOR
                            sp.edit().putString(HomeTextColorSettings.KEY_TITLE_COLOR, titleColor).apply()
                        },
                        error = if (titleColor.isNotBlank() && !isCompleteColorSpec(titleColor, true)) "颜色不完整，保留上次保存的值" else null,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(HomeTextColorSettings.KEY_TITLE_COLOR, "").apply() }
                    ) {
                        titleColor = it
                        if (isCompleteColorSpec(it, true)) {
                            sp.edit().putString(HomeTextColorSettings.KEY_TITLE_COLOR, HomeTextColorSettings.cleanColorSpec(it)).apply()
                        }
                    }
                    InsetDivider()
                    ColorPickerRow(
                        title = "副标题颜色",
                        summary = "消息摘要、账号信息和辅助文字",
                        value = subtitleColor,
                        onReset = {
                            subtitleColor = HomeTextColorSettings.DEFAULT_SUBTITLE_COLOR
                            sp.edit().putString(HomeTextColorSettings.KEY_SUBTITLE_COLOR, subtitleColor).apply()
                        },
                        error = if (subtitleColor.isNotBlank() && !isCompleteColorSpec(subtitleColor, true)) "颜色不完整，保留上次保存的值" else null,
                        onEditingFinished = { if (it.isBlank()) sp.edit().putString(HomeTextColorSettings.KEY_SUBTITLE_COLOR, "").apply() }
                    ) {
                        subtitleColor = it
                        if (isCompleteColorSpec(it, true)) {
                            sp.edit().putString(HomeTextColorSettings.KEY_SUBTITLE_COLOR, HomeTextColorSettings.cleanColorSpec(it)).apply()
                        }
                    }
                }
            }
        }
    }
}
