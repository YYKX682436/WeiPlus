package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.media.ThumbnailUtils
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import h.Hchat.BuildConfig
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.media.WeChatFavoriteItem
import h.Hchat.hooks.api.model.WeChatContact
import h.Hchat.hooks.items.antirecall.AntiRecallSettings
import h.Hchat.hooks.items.autoreply.XiaozhiMcpStatus
import h.Hchat.hooks.items.groupleave.GroupLeaveReplyTemplate
import h.Hchat.hooks.items.grouplabel.GroupChatLabel
import h.Hchat.hooks.items.membertitle.MemberTitleStore
import h.Hchat.hooks.items.messageblock.MessageBlockBinding
import h.Hchat.hooks.items.messageblock.MessageBlockDefaultRule
import h.Hchat.hooks.items.messageblock.MessageBlockSettings
import h.Hchat.hooks.items.messageblock.MessageBlockTemplate
import h.Hchat.hooks.items.payment.core.RedPacketReplyStep
import h.Hchat.hooks.items.payment.core.RedPacketRuleBinding
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import h.Hchat.hooks.items.payment.core.RedPacketRuleTemplate
import h.Hchat.hooks.items.payment.core.RedPacketSettings
import h.Hchat.hooks.items.payment.transfer.AutoTransferSettings
import h.Hchat.hooks.items.payment.transfer.TransferReceiveAccountStore
import h.Hchat.hooks.items.payment.transfer.TransferRuleBinding
import h.Hchat.hooks.items.payment.transfer.TransferRuleConfig
import h.Hchat.hooks.items.payment.transfer.TransferRuleTemplate
import h.Hchat.hooks.items.realtail.RealNameTailStore
import h.Hchat.hooks.items.roundavatar.RoundAvatarSettings
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.WindowDropdownMenu
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.LinkedHashSet
import java.util.Locale

@Composable
internal fun UnsupportedFeaturePage(provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
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
            item {
                SettingsCard {
                    EmptyText("这个模块暂时没有可配置项")
                }
            }
        }
    }
}

@Composable
internal fun PageScaffold(
    title: String,
    largeTitle: String,
    scrollBehavior: ScrollBehavior,
    onBack: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable ((Backdrop) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    RegisterSettingsBackHandler(onBack)
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = rememberLayerBackdrop(graphicsLayer)
    val navigationInset = navigationButtonBottomInset()
    val navigationGap = if (navigationInset > 0.dp) NAVIGATION_BUTTON_EXTRA_GAP else 0.dp
    val bottomAvoidance = navigationInset + navigationGap
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            top.yukonga.miuix.kmp.basic.Scaffold(
                topBar = {
                    TopAppBar(
                        title = title,
                        largeTitle = largeTitle,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            onBack?.let { back ->
                                Box(
                                    modifier = Modifier.size(40.dp).responsiveTap(onClick = back),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        imageVector = NavIcons.Back,
                                        contentDescription = "返回",
                                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        },
                        actions = topBarActions,
                        defaultWindowInsetsPadding = true
                    )
                },
                bottomBar = {},
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                content = { padding ->
                    content(
                        PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + bottomAvoidance
                        )
                    )
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomAvoidance)
                .align(Alignment.BottomCenter)
        ) {
            bottomBar?.invoke(backdrop)
        }
    }
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        cornerRadius = 18.dp
    ) {
        content()
    }
}

@Composable
internal fun SwitchRow(
    sp: SharedPreferences,
    key: String,
    title: String,
    summary: String,
    defaultValue: Boolean
) {
    var checked by remember { mutableStateOf(sp.getBoolean(key, defaultValue)) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            checked = !checked
            sp.edit().putBoolean(key, checked).apply()
        }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            if (summary.isNotBlank()) {
                Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                sp.edit().putBoolean(key, it).apply()
            }
        )
    }
}

@Composable
internal fun SwitchRow(
    checked: Boolean,
    title: String,
    summary: String,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().then(
            if (enabled) {
                Modifier.clickable { onCheckedChange(!checked) }
            } else {
                Modifier
            }
        ).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) }
        )
    }
}

@Composable
internal fun OptionRow(
    sp: SharedPreferences,
    key: String,
    title: String,
    options: List<OptionItem>,
    defaultValue: Int,
    onValueChanged: (Int) -> Unit = {}
) {
    var currentValue by remember {
        mutableStateOf(sp.getInt(key, defaultValue))
    }
    PopupOptionRow(
        title = title,
        summary = options.firstOrNull { it.value == currentValue }?.label
            ?: options.firstOrNull { it.value == defaultValue }?.label
            ?: "",
        options = options,
        currentValue = currentValue,
        onValueChanged = {
            currentValue = it
            onValueChanged(it)
            sp.edit().putInt(key, it).apply()
        }
    )
}

@Composable
internal fun PopupOptionRow(
    title: String,
    summary: String,
    options: List<OptionItem>,
    currentValue: Int,
    onValueChanged: (Int) -> Unit,
    enabled: Boolean = true
) {
    val selectedIndex = options.indexOfFirst { it.value == currentValue }.takeIf { it >= 0 } ?: 0
    val labels = options.map { it.label }
    WindowDropdownMenu(
        entry = DropdownEntry(
            items = labels.mapIndexed { index, label ->
                DropdownItem(
                    text = label,
                    selected = index == selectedIndex,
                    onClick = { options.getOrNull(index)?.let { onValueChanged(it.value) } }
                )
            }
        ),
        title = title,
        summary = summary,
        enabled = enabled,
        collapseOnSelection = true
    )
}

@Composable
internal fun InputRow(
    title: String,
    summary: String,
    value: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            minLines = minLines,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.secondaryVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
internal fun PopupChoiceRow(
    title: String,
    summary: String,
    options: List<PopupChoice<String>>,
    currentValue: String,
    onValueChanged: (String) -> Unit,
    enabled: Boolean = true
) {
    val selectedIndex = options.indexOfFirst { it.value == currentValue }.takeIf { it >= 0 } ?: 0
    WindowDropdownMenu(
        entry = DropdownEntry(
            items = options.mapIndexed { index, option ->
                DropdownItem(
                    text = option.label,
                    selected = index == selectedIndex,
                    onClick = { onValueChanged(option.value) }
                )
            }
        ),
        title = title,
        summary = summary,
        enabled = enabled,
        collapseOnSelection = true
    )
}

@Composable
internal fun ColorPickerRow(
    title: String,
    summary: String,
    value: String,
    allowGradient: Boolean = true,
    onReset: (() -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentValue by rememberUpdatedState(value)
    val colorParts = if (allowGradient) {
        colorSpecParts(value)
    } else {
        MemberTitleStore.cleanColor(value) to ""
    }
    var editEnd by remember { mutableStateOf(colorParts.second.isNotEmpty()) }
    val selectedColor = if (allowGradient && editEnd) colorParts.second.ifEmpty { colorParts.first } else colorParts.first
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ClickHintTag()
                }
                Text(
                    text = if (allowGradient) {
                        "$summary，支持 #RRGGBB / #AARRGGBB / #A,#B 渐变"
                    } else {
                        "$summary，支持 #RRGGBB / #AARRGGBB"
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )
            }
            ColorPreviewDot(if (allowGradient) value else colorParts.first)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(if (allowGradient) 19 else 9)) },
                textStyle = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.secondaryVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            if (onReset != null) {
                Text(
                    text = "重置",
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onReset)
                        .background(MiuixTheme.colorScheme.secondaryVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
        if (expanded) {
            if (allowGradient) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorSpecChip(
                        label = "起始色",
                        value = colorParts.first,
                        selected = !editEnd,
                        onClick = { editEnd = false },
                        modifier = Modifier.weight(1f)
                    )
                    ColorSpecChip(
                        label = "结束色",
                        value = colorParts.second,
                        selected = editEnd,
                        onClick = { editEnd = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            PsColorPicker(
                value = selectedColor,
                onValueChange = { picked ->
                    val next = if (allowGradient) {
                        val latestParts = colorSpecParts(currentValue)
                        if (editEnd) {
                            composeColorSpec(latestParts.first.ifEmpty { picked }, picked)
                        } else {
                            composeColorSpec(picked, latestParts.second)
                        }
                    } else {
                        picked
                    }
                    onValueChange(next)
                },
                modifier = Modifier.padding(top = if (allowGradient) 12.dp else 8.dp)
            )
            Text(
                text = if (allowGradient) {
                    "先选起始色或结束色，再用色盘取色；清空输入框可恢复默认/跟随昵称"
                } else {
                    "可直接输入颜色值，也可以用色盘取色；清空输入框可恢复默认色"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ColorSpecChip(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline.copy(alpha = 0.45f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ColorPreviewDot(value, size = 24.dp)
        Column {
            Text(text = label, color = MiuixTheme.colorScheme.onSurface, fontSize = 12.sp)
            Text(
                text = value.ifEmpty { "未设置" },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ColorPreviewDot(value: String, size: Dp = 34.dp) {
    val colorParts = colorSpecParts(value)
    val startColor = composeColorFromHex(colorParts.first) ?: MiuixTheme.colorScheme.secondaryVariant
    val endColor = composeColorFromHex(colorParts.second)
    val shape = RoundedCornerShape(size / 2)
    val colorBackground = if (endColor != null && endColor != startColor) {
        Modifier.background(Brush.horizontalGradient(listOf(startColor, endColor)))
    } else {
        Modifier.background(startColor)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .then(colorBackground)
            .border(2.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (value.isEmpty()) {
            Text(text = "-", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PsColorPicker(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = colorPickerSelection(value)
    val markerColor = MiuixTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val paletteSize = 228.dp
    val hueBarWidth = 40.dp
    val paletteSizePx = remember(density) { with(density) { paletteSize.roundToPx() } }
    val hueBarWidthPx = remember(density) { with(density) { hueBarWidth.roundToPx() } }
    val paletteBitmap = remember(selected.hue, paletteSizePx) {
        buildSvPaletteBitmap(selected.hue, paletteSizePx)
    }
    val hueBitmap = remember(paletteSizePx, hueBarWidthPx) {
        buildHuePaletteBitmap(hueBarWidthPx, paletteSizePx)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .size(paletteSize)
                .pointerInput(selected.hue) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onValueChange(
                            colorFromSvOffset(
                                selected.hue,
                                down.position,
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            onValueChange(
                                colorFromSvOffset(
                                    selected.hue,
                                    change.position,
                                    size.width.toFloat(),
                                    size.height.toFloat()
                                )
                            )
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
            drawImage(paletteBitmap)
            val marker = Offset(
                selected.saturation * size.width,
                (1f - selected.value) * size.height
            )
            drawCircle(color = markerColor, radius = 9f, center = marker)
            drawCircle(color = Color.White, radius = 5.5f, center = marker)
        }
        Canvas(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(width = hueBarWidth, height = paletteSize)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(selected.saturation, selected.value) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onValueChange(
                            colorFromHueOffset(
                                selected.saturation,
                                selected.value,
                                down.position.y,
                                size.height.toFloat()
                            )
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            onValueChange(
                                colorFromHueOffset(
                                    selected.saturation,
                                    selected.value,
                                    change.position.y,
                                    size.height.toFloat()
                                )
                            )
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
            drawImage(hueBitmap)
            val markerY = (selected.hue / 360f).coerceIn(0f, 1f) * size.height
            drawCircle(color = markerColor, radius = 10f, center = Offset(size.width / 2f, markerY))
            drawCircle(color = Color.White, radius = 6f, center = Offset(size.width / 2f, markerY))
        }
    }
}

private fun buildSvPaletteBitmap(hue: Float, sizePx: Int): ImageBitmap {
    val safeSize = sizePx.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(safeSize * safeSize)
    var index = 0
    for (y in 0 until safeSize) {
        val value = (1f - (y.toFloat() / (safeSize - 1))).coerceIn(0f, 1f)
        for (x in 0 until safeSize) {
            val saturation = (x.toFloat() / (safeSize - 1)).coerceIn(0f, 1f)
            pixels[index++] = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        }
    }
    bitmap.setPixels(pixels, 0, safeSize, 0, 0, safeSize, safeSize)
    return bitmap.asImageBitmap()
}

private fun buildHuePaletteBitmap(widthPx: Int, heightPx: Int): ImageBitmap {
    val safeWidth = widthPx.coerceAtLeast(2)
    val safeHeight = heightPx.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(safeWidth * safeHeight)
    var index = 0
    for (y in 0 until safeHeight) {
        val hue = (y.toFloat() / (safeHeight - 1)).coerceIn(0f, 1f) * 360f
        val color = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))
        repeat(safeWidth) {
            pixels[index++] = color
        }
    }
    bitmap.setPixels(pixels, 0, safeWidth, 0, 0, safeWidth, safeHeight)
    return bitmap.asImageBitmap()
}

private fun composeColorFromHex(value: String): Color? {
    val normalized = RealNameTailStore.cleanColor(value)
    if (normalized.isEmpty()) return null
    return runCatching { Color(AndroidColor.parseColor(normalized)) }.getOrNull()
}

private fun colorSpecParts(value: String): Pair<String, String> {
    val cleaned = MemberTitleStore.cleanColorSpec(value)
    if (cleaned.isEmpty()) return "" to ""
    val parts = cleaned.split(',').take(2)
    return parts.getOrElse(0) { "" } to parts.getOrElse(1) { "" }
}

private fun composeColorSpec(start: String, end: String): String {
    val first = MemberTitleStore.cleanColor(start)
    val second = MemberTitleStore.cleanColor(end)
    if (first.isEmpty()) return ""
    return if (second.isEmpty() || second == first) first else "$first,$second"
}

@Composable
internal fun WeightInputRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    InputRow(
        title = title,
        summary = "100-900，600 以上显示为加粗",
        value = value,
        onValueChange = { next ->
            onValueChange(next.filter { it.isDigit() }.take(3))
        }
    )
}

private data class ColorPickerSelection(val hue: Float, val saturation: Float, val value: Float)

private fun colorPickerSelection(value: String): ColorPickerSelection {
    val normalized = MemberTitleStore.cleanColor(value)
    if (normalized.isEmpty()) return ColorPickerSelection(0f, 1f, 1f)
    return runCatching {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(AndroidColor.parseColor(normalized), hsv)
        ColorPickerSelection(hsv[0], hsv[1], hsv[2])
    }.getOrDefault(ColorPickerSelection(0f, 1f, 1f))
}

private fun colorFromSvOffset(hue: Float, offset: Offset, width: Float, height: Float): String {
    val saturation = (offset.x / width).coerceIn(0f, 1f)
    val value = (1f - (offset.y / height)).coerceIn(0f, 1f)
    val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    return String.format("#%06X", 0xFFFFFF and color)
}

private fun colorFromHueOffset(saturation: Float, value: Float, y: Float, height: Float): String {
    val hue = (y / height).coerceIn(0f, 1f) * 360f
    val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    return String.format("#%06X", 0xFFFFFF and color)
}

@Composable
internal fun VariableInputRow(
    title: String,
    summary: String,
    value: String,
    variables: List<TemplateVariable>,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        if (summary.isNotBlank()) {
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onValueChange(it.text)
            },
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            minLines = minLines,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.secondaryVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
        VariableChipRow(
            variables = variables,
            onInsert = { token ->
                val next = fieldValue.insertToken(token)
                fieldValue = next
                onValueChange(next.text)
            }
        )
    }
}

@Composable
private fun RedPacketReplyContentEditor(
    context: Context,
    replyMode: Int,
    value: String,
    onValueChange: (String) -> Unit,
    onFavoritePicker: (FavoritePickerRequest) -> Unit,
    templateVariables: List<TemplateVariable> = redPacketReplyTemplateVariables
) {
    when {
        redPacketReplyUsesText(replyMode) -> {
            VariableInputRow(
                title = "回复内容",
                summary = "多个回复用 | 分隔随机选择",
                value = value,
                variables = templateVariables,
                minLines = 2,
                onValueChange = onValueChange
            )
        }
        replyMode == RedPacketRuleConfig.REPLY_XML -> {
            VariableInputRow(
                title = "XML 内容",
                summary = "输入 <msg><appmsg>...</appmsg></msg>，支持变量",
                value = value,
                variables = templateVariables,
                minLines = 4,
                onValueChange = onValueChange
            )
        }
        replyMode == RedPacketRuleConfig.REPLY_FAVORITE -> {
            ActionRow(
                title = redPacketReplyPickerTitle(replyMode),
                summary = redPacketReplyContentSummary(replyMode, value)
            ) {
                onFavoritePicker(
                    FavoritePickerRequest(
                        title = redPacketReplyPickerTitle(replyMode),
                        existingValue = value,
                        onValue = onValueChange,
                        multiSelect = true,
                        delimiter = "|"
                    )
                )
            }
            if (value.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选收藏", "当前会从该回复类型移除已选收藏") {
                    onValueChange("")
                }
            }
        }
        else -> {
            ActionRow(
                title = redPacketReplyPickerTitle(replyMode),
                summary = redPacketReplyContentSummary(replyMode, value)
            ) {
                val activity = context as? Activity
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                } else {
                    RedPacketReplyFilePickerBridge.launch(activity, replyMode) { paths ->
                        if (paths.isNotEmpty()) {
                            onValueChange(paths.joinToString("|"))
                            Toast.makeText(context, "已选择 ${paths.size} 个文件", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            if (value.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选文件", "当前会从该回复类型移除已选文件") {
                    onValueChange("")
                }
            }
        }
    }
}

@Composable
internal fun RedPacketReplyStepsPage(
    context: Context,
    title: String,
    initialSteps: List<RedPacketReplyStep>,
    onBack: () -> Unit,
    onSave: (List<RedPacketReplyStep>) -> Unit,
    templateVariables: List<TemplateVariable> = redPacketReplyTemplateVariables
) {
    var steps by remember(initialSteps) {
        mutableStateOf(initialSteps.map { normalizeRedPacketReplyStepForUi(it) })
    }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    val listState = rememberLazyListState()

    fun updateStep(index: Int, step: RedPacketReplyStep) {
        if (index !in steps.indices) return
        steps = steps.toMutableList().also { it[index] = normalizeRedPacketReplyStepForUi(step) }
    }

    val currentEditingIndex = editingIndex?.takeIf { it in steps.indices }
    val route = when {
        favoritePicker != null -> RedPacketReplyStepsRoute.FavoritePicker(favoritePicker!!)
        currentEditingIndex != null -> RedPacketReplyStepsRoute.StepEditor(currentEditingIndex)
        else -> RedPacketReplyStepsRoute.Main
    }

    SettingsRouteTransition(
        targetState = route,
        label = "RedPacketReplyStepsRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is RedPacketReplyStepsRoute.FavoritePicker -> {
                FavoritePickerPage(
                    request = currentRoute.request,
                    onBack = { favoritePicker = null }
                )
            }
            is RedPacketReplyStepsRoute.StepEditor -> {
                val index = currentRoute.index
                val step = steps[index]
                RedPacketReplyStepEditorPage(
                    context = context,
                    title = "第 ${index + 1} 条回复",
                    step = step,
                    canMoveUp = index > 0,
                    canMoveDown = index < steps.lastIndex,
                    onBack = { editingIndex = null },
                    onStepChange = { updateStep(index, it) },
                    templateVariables = templateVariables,
                    onFavoritePicker = { favoritePicker = it },
                    onMoveUp = {
                        if (index > 0) {
                            steps = steps.toMutableList().also {
                                val current = it.removeAt(index)
                                it.add(index - 1, current)
                            }
                            editingIndex = index - 1
                        }
                    },
                    onMoveDown = {
                        if (index < steps.lastIndex) {
                            steps = steps.toMutableList().also {
                                val current = it.removeAt(index)
                                it.add(index + 1, current)
                            }
                            editingIndex = index + 1
                        }
                    },
                    onDelete = {
                        steps = steps.toMutableList().also { it.removeAt(index) }
                        editingIndex = null
                    }
                )
            }
            RedPacketReplyStepsRoute.Main -> RedPacketReplyStepsListPage(
                title = title,
                steps = steps,
                listState = listState,
                onBack = onBack,
                onSave = { onSave(cleanRedPacketReplySteps(steps)) },
                onOpenStep = { editingIndex = it },
                onAddStep = {
                    val index = steps.size
                    steps = steps + newRedPacketReplyStep(index + 1)
                    editingIndex = index
                },
                onMoveUp = { index ->
                    if (index > 0) {
                        steps = steps.toMutableList().also {
                            val current = it.removeAt(index)
                            it.add(index - 1, current)
                        }
                    }
                },
                onMoveDown = { index ->
                    if (index < steps.lastIndex) {
                        steps = steps.toMutableList().also {
                            val current = it.removeAt(index)
                            it.add(index + 1, current)
                        }
                    }
                },
                onDelete = { index ->
                    if (index in steps.indices) steps = steps.toMutableList().also { it.removeAt(index) }
                }
            )
        }
    }
}

@Composable
private fun RedPacketReplyStepsListPage(
    title: String,
    steps: List<RedPacketReplyStep>,
    listState: LazyListState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenStep: (Int) -> Unit,
    onAddStep: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存回复",
                onPrimaryClick = onSave,
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
            item { SmallTitle(text = "按顺序发送") }
            if (steps.isEmpty()) {
                item {
                    SettingsCard {
                        EmptyText("暂无回复步骤。添加后会按列表顺序依次发送。")
                    }
                }
            } else {
                steps.forEachIndexed { index, step ->
                    item { SmallTitle(modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp), text = "第 ${index + 1} 条") }
                    item {
                        SettingsCard {
                            SelectRow(
                                title = redPacketReplyModeLabel(step.mode),
                                summary = redPacketReplyStepSummary(step),
                                onClick = { onOpenStep(index) }
                            )
                            InsetDivider()
                            RedPacketReplyStepActions(
                                canMoveUp = index > 0,
                                canMoveDown = index < steps.lastIndex,
                                onMoveUp = { onMoveUp(index) },
                                onMoveDown = { onMoveDown(index) },
                                onDelete = { onDelete(index) }
                            )
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("新增回复", "添加一条按顺序发送的回复", onAddStep)
                }
            }
        }
    }
}

@Composable
private fun RedPacketReplyStepEditorPage(
    context: Context,
    title: String,
    step: RedPacketReplyStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onBack: () -> Unit,
    onStepChange: (RedPacketReplyStep) -> Unit,
    templateVariables: List<TemplateVariable>,
    onFavoritePicker: (FavoritePickerRequest) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "完成",
                onPrimaryClick = onBack,
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "回复内容") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "回复类型",
                        summary = redPacketReplyModeLabel(step.mode),
                        options = redPacketReplyStepModeOptions(),
                        currentValue = step.mode,
                        onValueChanged = { mode ->
                            onStepChange(
                                step.copy(
                                    mode = mode,
                                    content = if (mode == step.mode) {
                                        step.content
                                    } else {
                                        defaultRedPacketReplyContent(mode)
                                    }
                                )
                            )
                        }
                    )
                    InsetDivider()
                    RedPacketReplyContentEditor(
                        context = context,
                        replyMode = step.mode,
                        value = step.content,
                        onValueChange = { onStepChange(step.copy(content = it)) },
                        onFavoritePicker = onFavoritePicker,
                        templateVariables = templateVariables
                    )
                    InsetDivider()
                    NumberInputRow(
                        title = "发送前延迟",
                        summary = "单位 ms，按步骤顺序等待",
                        value = step.delayMs.coerceAtLeast(0L).toString(),
                        onValueChange = { value ->
                            onStepChange(step.copy(delayMs = value.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L))
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = step.random,
                        title = "随机追加延迟",
                        summary = "在发送前延迟后随机追加 0-2 秒",
                        onCheckedChange = { onStepChange(step.copy(random = it)) }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    RedPacketReplyStepActions(
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
internal fun RedPacketReplyStepActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            text = "上移",
            onClick = { if (canMoveUp) onMoveUp() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        TextButton(
            text = "下移",
            onClick = { if (canMoveDown) onMoveDown() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        TextButton(
            text = "删除",
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}

@Composable
private fun VariableChipRow(
    variables: List<TemplateVariable>,
    onInsert: (String) -> Unit
) {
    if (variables.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        variables.forEach { variable ->
            VariableChip(variable = variable, onClick = { onInsert(variable.token) })
        }
    }
}

@Composable
private fun VariableChip(variable: TemplateVariable, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val bg = if (pressed) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = variable.label,
            color = MiuixTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun TextFieldValue.insertToken(token: String): TextFieldValue {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    val left = minOf(start, end)
    val right = maxOf(start, end)
    val nextText = text.replaceRange(left, right, token)
    val cursor = left + token.length
    return TextFieldValue(nextText, TextRange(cursor))
}

@Composable
internal fun NumberInputRow(title: String, summary: String, value: String, onValueChange: (String) -> Unit) {
    InputRow(title, summary, value.filter { it.isDigit() }, onValueChange = { next ->
        onValueChange(next.filter { it.isDigit() })
    })
}

@Composable
internal fun TimeOfDayPickerRow(
    title: String,
    value: String,
    allowEmpty: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val normalized = normalizeTimeOfDay(value, if (allowEmpty) "" else "00:00:00")
    ActionRow(
        title = title,
        summary = normalized.ifBlank { "不限制" },
        onClick = { showPicker = true }
    )
    if (showPicker) {
        TimeOfDayPickerDialog(
            title = title,
            initialValue = normalized,
            allowEmpty = allowEmpty,
            onDismiss = { showPicker = false },
            onConfirm = {
                onValueChange(it)
                showPicker = false
            }
        )
    }
}

@Composable
private fun TimeOfDayPickerDialog(
    title: String,
    initialValue: String,
    allowEmpty: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialSeconds = parseTimeOfDaySeconds(initialValue).takeIf { it >= 0 } ?: 0
    var hour by remember(initialValue) { mutableStateOf((initialSeconds / 3600).toString()) }
    var minute by remember(initialValue) { mutableStateOf((initialSeconds / 60 % 60).toString()) }
    var second by remember(initialValue) { mutableStateOf((initialSeconds % 60).toString()) }
    val hourValue = hour.toIntOrNull()
    val minuteValue = minute.toIntOrNull()
    val secondValue = second.toIntOrNull()
    val valid = hourValue != null && hourValue in 0..23 &&
        minuteValue != null && minuteValue in 0..59 &&
        secondValue != null && secondValue in 0..59
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeOfDayNumberField("时", hour, 23, Modifier.weight(1f)) { hour = it }
                    TimeOfDayNumberField("分", minute, 59, Modifier.weight(1f)) { minute = it }
                    TimeOfDayNumberField("秒", second, 59, Modifier.weight(1f)) { second = it }
                }
                Text(
                    text = if (valid) {
                        String.format(
                            Locale.US,
                            "%02d:%02d:%02d",
                            hourValue ?: 0,
                            minuteValue ?: 0,
                            secondValue ?: 0
                        )
                    } else {
                        "请输入有效的时、分、秒"
                    },
                    color = if (valid) MiuixTheme.colorScheme.onSurfaceVariantSummary else Color(0xFFD93025),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (allowEmpty) {
                        TextButton(
                            text = "清空",
                            onClick = { onConfirm("") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            if (valid) {
                                onConfirm(
                                    String.format(
                                        Locale.US,
                                        "%02d:%02d:%02d",
                                        hourValue ?: 0,
                                        minuteValue ?: 0,
                                        secondValue ?: 0
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

@Composable
private fun TimeOfDayNumberField(
    label: String,
    value: String,
    max: Int,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            textAlign = TextAlign.Center
        )
        BasicTextField(
            value = value,
            onValueChange = { next ->
                val digits = next.filter { it.isDigit() }.take(2)
                val number = digits.toIntOrNull()
                if (digits.isEmpty() || (number != null && number <= max)) onValueChange(digits)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        )
    }
}

private fun normalizeTimeOfDay(value: String?, fallback: String): String {
    val seconds = parseTimeOfDaySeconds(value)
    if (seconds < 0) return fallback
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        seconds / 3600,
        seconds / 60 % 60,
        seconds % 60
    )
}

private fun parseTimeOfDaySeconds(value: String?): Int {
    if (value.isNullOrBlank()) return -1
    val parts = value.trim().split(":")
    if (parts.size !in 2..3) return -1
    val hour = parts[0].toIntOrNull() ?: return -1
    val minute = parts[1].toIntOrNull() ?: return -1
    val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return -1
    return hour * 3600 + minute * 60 + second
}

@Composable
internal fun MessageBlockContactFilterRow(
    selected: MessageBlockContactFilter,
    enableOfficials: Boolean = true,
    onSelected: (MessageBlockContactFilter) -> Unit
) {
    val items = buildList {
        add(MessageBlockContactFilter.FRIENDS)
        add(MessageBlockContactFilter.GROUPS)
        if (enableOfficials) add(MessageBlockContactFilter.OFFICIALS)
        add(MessageBlockContactFilter.LABELS)
        add(MessageBlockContactFilter.ALL)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            ContactFilterChip(
                text = item.label,
                selected = item == selected,
                onClick = { onSelected(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ConversationRuleCategoryTabs(
    selected: ConversationRuleCategory,
    onSelected: (ConversationRuleCategory) -> Unit,
    includeOfficial: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ConversationRuleCategory.entries
            .filter { includeOfficial || it != ConversationRuleCategory.OFFICIAL }
            .forEach { category ->
            ContactFilterChip(
                text = category.label,
                selected = category == selected,
                onClick = { onSelected(category) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun BatchDeleteConfirmDialog(
    show: Boolean,
    message: String,
    labels: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!show) return
    WindowDialog(
        show = true,
        title = "确认批量删除",
        onDismissRequest = onDismiss,
        content = {
            Column {
                Text(
                    text = buildString {
                        append(message)
                        val names = labels.filter { it.isNotBlank() }.take(6).joinToString("、")
                        if (names.isNotBlank()) append("\n\n").append(names)
                        if (labels.size > 6) append(" 等")
                    },
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "确认删除",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

@Composable
internal fun FriendContactFilterRow(
    selected: ContactPickerFilter,
    onSelected: (ContactPickerFilter) -> Unit
) {
    val items = listOf(ContactPickerFilter.FRIENDS, ContactPickerFilter.LABELS)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            ContactFilterChip(
                text = item.label,
                selected = item == selected,
                onClick = { onSelected(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ContactPickerScopeFilterRow(
    selected: ContactPickerScopeTab,
    onSelected: (ContactPickerScopeTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ContactPickerScopeTab.entries.forEach { item ->
            ContactFilterChip(
                text = item.label,
                selected = item == selected,
                onClick = { onSelected(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ContactPickerScopeRow(
    title: String,
    memberCount: Int,
    selectedCount: Int,
    memberUnit: String,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val allSelected = memberCount > 0 && selectedCount == memberCount
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rememberPressFeedbackColor(pressed))
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (selectedCount == 0) {
                    "$memberCount $memberUnit"
                } else {
                    "已选 $selectedCount / $memberCount $memberUnit"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        SelectionMark(selected = allSelected, multiSelect = true)
    }
}

@Composable
internal fun ContactFilterRow(
    selected: ContactPickerFilter,
    enableLabels: Boolean = false,
    enableOfficials: Boolean = false,
    onSelected: (ContactPickerFilter) -> Unit
) {
    val items = buildList {
        add(ContactPickerFilter.FRIENDS)
        add(ContactPickerFilter.GROUPS)
        if (enableOfficials) add(ContactPickerFilter.OFFICIALS)
        if (enableLabels) add(ContactPickerFilter.LABELS)
        add(ContactPickerFilter.ALL)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            ContactFilterChip(
                text = item.label,
                selected = item == selected,
                onClick = { onSelected(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun FavoritePickerFilterRow(
    selected: FavoritePickerFilter,
    onSelected: (FavoritePickerFilter) -> Unit
) {
    val rows = listOf(
        listOf(FavoritePickerFilter.IMAGE, FavoritePickerFilter.TEXT, FavoritePickerFilter.VOICE),
        listOf(FavoritePickerFilter.VIDEO, FavoritePickerFilter.OTHER, FavoritePickerFilter.ALL)
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        rows.forEachIndexed { index, filters ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { item ->
                    ContactFilterChip(
                        text = item.label,
                        selected = item == selected,
                        onClick = { onSelected(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ContactLabelFilterRow(
    labels: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    if (labels.isEmpty()) {
        EmptyText("没有好友标签")
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            ContactFilterChip(
                text = label,
                selected = label == selected,
                onClick = { onSelected(label) },
                modifier = Modifier.defaultMinSize(minWidth = 70.dp)
            )
        }
    }
}

@Composable
internal fun GroupChatLabelPickerRow(
    label: GroupChatLabel,
    selectedCount: Int,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    val allSelected = selectedCount == label.groupIds.size
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
            Text(
                text = label.name,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (selectedCount == 0) {
                    "${label.groupIds.size} 个群聊"
                } else {
                    "已选 $selectedCount / ${label.groupIds.size} 个群聊"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        SelectionMark(selected = allSelected, multiSelect = true)
    }
}

@Composable
internal fun ContactFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    var visualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            visualPressed = true
        } else {
            delay(100L)
            visualPressed = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (visualPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 60 else 145),
        label = "ContactFilterPressScale"
    )
    val background = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = if (visualPressed) 0.88f else 1f)
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = if (visualPressed) 0.18f else 0.06f)
    }
    val textColor = if (selected) {
        Color.White
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Box(
        modifier = modifier
            .height(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

@Composable
internal fun PickerSectionHeader(
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun ActionRow(title: String, summary: String, onClick: () -> Unit) {
    SelectRow(title = title, summary = summary, onClick = onClick)
}

@Composable
internal fun SelectRow(title: String, summary: String, onClick: () -> Unit) {
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
            Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            if (summary.isNotBlank()) {
                Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            }
        }
        Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
    }
}

@Composable
internal fun MessageBlockLabelListCard(
    label: MessageBlockLabelOption,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 14.dp
    ) {
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
            Box(
                modifier = Modifier.size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.secondaryVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.name.take(1).ifEmpty { "签" },
                    color = MiuixTheme.colorScheme.onSecondaryVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = label.name, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(
                    text = "${label.contactIds.size} 人",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )
            }
            Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
        }
    }
}

@Composable
internal fun MessageBlockContactListCard(
    row: MessageBlockContactOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val option = row.contact
    val summary = if (row.labelNames.isEmpty()) {
        option.id
    } else {
        option.id + " · " + row.labelNames.joinToString(" / ")
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 14.dp
    ) {
        MessageBlockContactRow(
            option = option,
            summary = summary,
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
private fun MessageBlockContactRow(
    option: ContactOption,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
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
        ContactAvatar(option)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = option.label, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        SelectionMark(selected = selected, multiSelect = true)
    }
}

@Composable
internal fun ContactListCard(
    option: ContactOption,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 14.dp
    ) {
        ContactRow(
            option = option,
            selected = selected,
            multiSelect = multiSelect,
            onClick = onClick
        )
    }
}

@Composable
internal fun FavoriteListCard(
    item: WeChatFavoriteItem,
    selected: Boolean,
    multiSelect: Boolean,
    onPreview: (String) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 8.dp
    ) {
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
            FavoriteMediaThumbnail(item = item, onPreview = onPreview)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = item.displayTitle(),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.displaySummary(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (item.tags.isNotEmpty()) {
                    Text(
                        text = "标签 · ${item.tags.joinToString(" / ")}",
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            SelectionMark(selected = selected, multiSelect = multiSelect)
        }
    }
}

@Composable
private fun FavoriteMediaThumbnail(
    item: WeChatFavoriteItem,
    onPreview: (String) -> Unit
) {
    var previewPath by remember(item.localId) { mutableStateOf("") }
    var thumbnail by remember(item.localId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.localId, item.type) {
        if (item.type != 2 && item.type != 4) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            val path = WeChatApis.media()?.favorites()?.previewPath(item.localId).orEmpty()
            path to path.takeIf { it.isNotBlank() }
                ?.let { loadFavoriteThumbnail(it, item.type, 320) }
        }
        previewPath = result.first
        thumbnail = result.second
    }
    val previewModifier = if (previewPath.isNotBlank()) {
        Modifier.responsiveTap(onClick = { onPreview(previewPath) })
    } else {
        Modifier
    }
    Box(
        modifier = Modifier.size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant)
            .then(previewModifier),
        contentAlignment = Alignment.Center
    ) {
        val image = thumbnail
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = if (item.type == 4) "预览视频" else "预览图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.type == 4) {
                Box(
                    modifier = Modifier.size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.56f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        imageVector = NavIcons.Entertainment,
                        contentDescription = "播放",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Text(
                text = item.typeLabel().take(1).ifEmpty { "藏" },
                color = MiuixTheme.colorScheme.onSecondaryVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

private fun loadFavoriteThumbnail(path: String, type: Int, maxDimension: Int): ImageBitmap? {
    if (!File(path).isFile) return null
    return if (type == 4) {
        @Suppress("DEPRECATION")
        ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)?.asImageBitmap()
    } else {
        decodeFavoriteImage(path, maxDimension)
    }
}

internal fun decodeFavoriteImage(path: String, maxDimension: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}

@Composable
internal fun FavoriteMediaPreviewDialog(
    media: FavoriteMediaPreview,
    onClose: () -> Unit
) {
    var image by remember(media.path) { mutableStateOf<ImageBitmap?>(null) }
    var videoView by remember(media.path) { mutableStateOf<VideoView?>(null) }
    val previewHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.62f).coerceAtMost(560.dp)
    LaunchedEffect(media.path, media.item.type) {
        if (media.item.type == 2) {
            image = withContext(Dispatchers.IO) { decodeFavoriteImage(media.path, 2048) }
        }
    }
    DisposableEffect(media.path) {
        onDispose { videoView?.stopPlayback() }
    }
    WindowDialog(
        show = true,
        title = "${media.item.typeLabel()}预览",
        onDismissRequest = onClose,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (media.item.type == 4) {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).also { view ->
                                val controller = MediaController(context)
                                controller.setAnchorView(view)
                                view.setMediaController(controller)
                                view.setVideoPath(media.path)
                                view.setOnPreparedListener { view.start() }
                                videoView = view
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = previewHeight)
                    )
                } else {
                    val bitmap = image
                    if (bitmap == null) {
                        EmptyText("正在载入预览...")
                    } else {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "图片预览",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = previewHeight)
                        )
                    }
                }
                TextButton(
                    text = "关闭",
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    )
}

@Composable
private fun ContactRow(
    option: ContactOption,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit
) {
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
        ContactAvatar(option)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = option.label, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            val summary = buildList {
                add(option.id)
                option.labels.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" / ")) }
                option.extraSummary.takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(" · ")
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        SelectionMark(selected = selected, multiSelect = multiSelect)
    }
}

@Composable
private fun ContactAvatar(option: ContactOption) {
    val context = LocalContext.current
    var bitmap by remember(option.id, option.avatarUrl, option.avatarBackupUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(option.id, option.avatarUrl, option.avatarBackupUrl) {
        bitmap = loadAvatarBitmap(option.id, option.avatarUrl, option.avatarBackupUrl)
    }
    val image = bitmap
    val avatarBg = if (image == null && option.group) Color(0xFF2E7DFF) else MiuixTheme.colorScheme.secondaryVariant
    val avatarTextColor = if (image == null && option.group) Color.White else MiuixTheme.colorScheme.onSecondaryVariant
    val avatarShape = if (RoundAvatarSettings.enabled(context)) {
        RoundedCornerShape((42f * RoundAvatarSettings.radiusFactor(context)).dp)
    } else {
        RoundedCornerShape(12.dp)
    }
    Box(
        modifier = Modifier.size(42.dp)
            .clip(avatarShape)
            .background(avatarBg),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = if (option.group) "群" else option.label.take(1).ifEmpty { "友" },
                color = avatarTextColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun OptionChoiceRow(
    item: OptionItem,
    selected: Boolean,
    onClick: () -> Unit
) {
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.label, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            if (item.summary.isNotEmpty()) {
                Text(text = item.summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            }
        }
        SelectionMark(selected = selected, multiSelect = false)
    }
}

@Composable
internal fun SelectionMark(selected: Boolean, multiSelect: Boolean) {
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Checkbox(
            modifier = Modifier.size(22.dp),
            state = if (selected) ToggleableState.On else ToggleableState.Off,
            onClick = null
        )
    }
}

@Composable
internal fun BottomActionBar(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    secondaryText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    middleText: String? = null,
    onMiddleClick: (() -> Unit)? = null
) {
    val backAction = when {
        secondaryText == "取消" && onSecondaryClick != null -> onSecondaryClick
        middleText == "取消" && onMiddleClick != null -> onMiddleClick
        primaryText == "取消" -> onPrimaryClick
        secondaryText == "返回" && onSecondaryClick != null -> onSecondaryClick
        middleText == "返回" && onMiddleClick != null -> onMiddleClick
        primaryText == "返回" -> onPrimaryClick
        secondaryText == "关闭" && onSecondaryClick != null -> onSecondaryClick
        middleText == "关闭" && onMiddleClick != null -> onMiddleClick
        primaryText == "关闭" -> onPrimaryClick
        else -> null
    }
    RegisterSettingsBackHandler(backAction)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.92f))
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (secondaryText != null && onSecondaryClick != null) {
            BottomBarButton(
                text = secondaryText,
                modifier = Modifier.weight(1f),
                filled = false,
                onClick = onSecondaryClick
            )
        }
        if (middleText != null && onMiddleClick != null) {
            BottomBarButton(
                text = middleText,
                modifier = Modifier.weight(1f),
                filled = false,
                onClick = onMiddleClick
            )
        }
        BottomBarButton(
            text = primaryText,
            modifier = Modifier.weight(1f),
            filled = true,
            onClick = onPrimaryClick
        )
    }
}

@Composable
internal fun navigationButtonBottomInset(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val gestureNavigation = remember(context) { isGestureNavigationMode(context) }
    if (gestureNavigation) return 0.dp
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val resourceBottom = remember(context, density) {
        with(density) { navigationBarHeightPx(context).toDp() }
    }
    val bottomInset = if (navigationBottom > resourceBottom) navigationBottom else resourceBottom
    return if (bottomInset >= NAVIGATION_BUTTON_MIN_INSET) bottomInset else 0.dp
}

private fun navigationBarHeightPx(context: Context): Int {
    val resources = context.resources
    val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (id > 0) {
        runCatching { resources.getDimensionPixelSize(id) }.getOrDefault(0)
    } else {
        0
    }
}

private fun isGestureNavigationMode(context: Context): Boolean {
    val resolver = context.contentResolver
    val androidMode = runCatching {
        Settings.Secure.getInt(resolver, "navigation_mode", -1)
    }.getOrDefault(-1)
    if (androidMode == 2) return true
    val miuiGlobalGesture = runCatching {
        Settings.Global.getInt(resolver, "force_fsg_nav_bar", 0)
    }.getOrDefault(0)
    val miuiSecureGesture = runCatching {
        Settings.Secure.getInt(resolver, "force_fsg_nav_bar", 0)
    }.getOrDefault(0)
    return miuiGlobalGesture == 1 || miuiSecureGesture == 1
}

@Composable
private fun BottomBarButton(text: String, modifier: Modifier, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryVariant
    val fg = if (filled) Color.White else MiuixTheme.colorScheme.onSecondaryVariant
    Text(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        text = text,
        color = fg,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}

@Composable
internal fun StatsCard(sp: SharedPreferences) {
    SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "红包记录")
    SettingsCard {
        InfoRow("已抢红包", "${sp.getInt(RedPacketSettings.KEY_STATS_COUNT, 0)} 个")
        InsetDivider()
        InfoRow("累计金额", String.format("%.2f 元", sp.getInt(RedPacketSettings.KEY_STATS_AMOUNT, 0) / 100.0))
        InsetDivider()
        InfoRow("今日抢到", "${sp.getInt(RedPacketSettings.KEY_STATS_TODAY, 0)} 个")
        InsetDivider()
        InfoRow("失败次数", "${sp.getInt(RedPacketSettings.KEY_STATS_FAILED, 0)} 次")
    }
}

@Composable
internal fun AboutCard(context: Context) {
    val hostVersion = WeChatApis.version()?.current()?.displayVersion()?.takeIf { it.isNotBlank() } ?: "未知"
    val moduleVersion = BuildConfig.VERSION_NAME.takeIf { it.isNotBlank() } ?: "未知"
    SettingsCard {
        InfoRow(label = "版本", value = moduleVersion)
        InsetDivider()
        InfoRow(label = "宿主", value = hostVersion)
        InsetDivider()
        InfoRow(label = "作者", value = "。。")
    }
}

@Composable
internal fun ConfigBackupCard(
    context: Context,
    onImported: () -> Unit
) {
    val activity = context as? Activity
    SettingsCard {
        ActionRow(
            title = "导出配置",
            summary = "",
            onClick = {
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件管理器", Toast.LENGTH_SHORT).show()
                } else {
                    ConfigImportExportBridge.launchExport(activity)
                }
            }
        )
        InsetDivider()
        ActionRow(
            title = "导入配置",
            summary = "",
            onClick = {
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件管理器", Toast.LENGTH_SHORT).show()
                } else {
                    ConfigImportExportBridge.launchImport(activity, onImported)
                }
            }
        )
    }
}

@Composable
internal fun GroupLinksCard(context: Context) {
    SettingsCard {
        InfoRow(label = "Telegram Channel", value = "Hchat_ci", onClick = {
            openExternalLink(context, "https://t.me/Hchat_ci")
        })
        InsetDivider()
        InfoRow(label = "Telegram Group", value = "Hchat_Group", onClick = {
            openExternalLink(context, "https://t.me/Hchat_Group")
        })
    }
}

@Composable
internal fun ThanksCard(context: Context) {
    SettingsCard {
        InfoRow(label = "KavaRef", value = "HighCapable", onClick = {
            openExternalLink(context, "https://github.com/HighCapable/KavaRef")
        })
        InsetDivider()
        InfoRow(label = "DexKit", value = "LuckyPray", onClick = {
            openExternalLink(context, "https://github.com/LuckyPray/DexKit")
        })
        InsetDivider()
        InfoRow(label = "FastKV", value = "BillyWei01", onClick = {
            openExternalLink(context, "https://github.com/BillyWei01/FastKV")
        })
        InsetDivider()
        InfoRow(label = "WeChat Pad", value = "lovejiuwu", onClick = {
            openExternalLink(context, "https://github.com/Xposed-Modules-Repo/top.hookvip.wxtablet")
        })
        InsetDivider()
        InfoRow(label = "LSPosed", value = "LSPosed", onClick = {
            openExternalLink(context, "https://github.com/LSPosed/LSPosed")
        })
        InsetDivider()
        InfoRow(label = "Miuix", value = "YuKongA", onClick = {
            openExternalLink(context, "https://github.com/compose-miuix-ui/miuix")
        })
        InsetDivider()
        InsetDivider()
        InfoRow(label = "Silk Codec", value = "YunJavaPro", onClick = {
            openExternalLink(context, "https://github.com/YunJavaPro/Silk-Codec-Android")
        })
    }
}

@Composable
internal fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = value, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
            if (onClick != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(width = 16.dp, height = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "›",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 18.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun XiaozhiMcpStatusRow(status: XiaozhiMcpStatus) {
    val dotColor = if (status.connected) Color(0xFF20C05C) else Color(0xFF8C8C8C)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "连接状态",
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(dotColor)
            )
            Text(
                text = status.label,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

internal fun openExternalLink(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun extractFirstHttpUrl(text: String): String? {
    val matcher = android.util.Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val url = matcher.group().orEmpty()
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            return url
        }
    }
    return null
}

@Composable
internal fun EmptyText(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center
    )
}

@Composable
internal fun InsetDivider(start: Dp = 16.dp) {
    Box(
        modifier = Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(Dp.Hairline)
            .background(MiuixTheme.colorScheme.dividerLine)
    )
}

internal class EmbeddedComposeOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner,
    NavigationEventDispatcherOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val navigationDispatcher = NavigationEventDispatcher()
    private val composeRuntime = lazy(LazyThreadSafetyMode.NONE) {
        EmbeddedComposeRuntime(lifecycleRegistry)
    }
    private var restored = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val navigationEventDispatcher: NavigationEventDispatcher
        get() = navigationDispatcher

    fun install(view: View) {
        EmbeddedComposeOwnerInstaller.install(view, this, this, this, this)
    }

    fun clear(view: View) {
        EmbeddedComposeOwnerInstaller.clear(view)
    }

    fun installComposition(view: ComposeView) {
        composeRuntime.value.install(view)
    }

    fun attach() {
        if (!restored) {
            savedStateRegistryController.performRestore(Bundle.EMPTY)
            restored = true
        }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        if (composeRuntime.isInitialized()) composeRuntime.value.destroy()
        navigationDispatcher.dispose()
        store.clear()
    }
}

internal fun firstTemplate(value: String): String {
    return value.split('|').firstOrNull { it.trim().isNotEmpty() }?.trim() ?: value.trim()
}

internal fun normalizeAntiRecallNoticeText(value: String): String {
    return if (value == AntiRecallSettings.LEGACY_NOTICE_TEXT) {
        AntiRecallSettings.DEFAULT_NOTICE_TEXT
    } else {
        value
    }
}

internal enum class ContactPickerMode {
    FRIENDS,
    GROUPS,
    BOTH,
    ALL_CHATS
}

internal fun ContactPickerMode.supportsGroups(): Boolean {
    return this == ContactPickerMode.GROUPS ||
        this == ContactPickerMode.BOTH ||
        this == ContactPickerMode.ALL_CHATS
}

internal enum class ContactPickerFilter(
    val label: String,
    val title: String
) {
    FRIENDS("好友", "好友"),
    GROUPS("群聊", "群聊"),
    OFFICIALS("公众号", "公众号"),
    LABELS("标签", "标签好友"),
    ALL("全部", "全部")
}

internal enum class ContactPickerScopeTab(val label: String) {
    FRIENDS("好友"),
    LABELS("标签"),
    CONVERSATION_GROUPS("聊天分组")
}

internal data class ContactPickerScope(
    val name: String,
    val memberIds: Set<String>
)

internal enum class ConversationRuleCategory(val label: String) {
    OFFICIAL("公众号"),
    GROUP("群聊"),
    FRIEND("好友"),
    ALL("全部")
}

internal enum class MessageBlockContactKind {
    FRIEND,
    GROUP,
    OFFICIAL
}

internal enum class MessageBlockContactFilter(
    val label: String,
    val title: String
) {
    FRIENDS("好友", "好友"),
    GROUPS("群聊", "群聊"),
    OFFICIALS("公众号", "公众号"),
    LABELS("标签", "标签好友"),
    ALL("全部", "全部")
}

internal data class ContactPickerRequest(
    val title: String,
    val mode: ContactPickerMode,
    val multiSelect: Boolean,
    val existingValue: String,
    val onValue: (String) -> Unit,
    val enableLabels: Boolean = false,
    val enableGroupLabels: Boolean = true,
    val enableScopeSelection: Boolean = false,
    val singleConfirmText: String = "发送"
)

internal enum class AutoMessageForwardRoute {
    MAIN,
    RULES,
    EDITOR,
    CONTACTS,
    MEMBERS,
    TYPES,
    REPLACEMENTS
}

internal enum class AutoMessageForwardContactField {
    SOURCES,
    TARGETS
}

internal data class FavoritePickerRequest(
    val title: String,
    val existingValue: String,
    val onValue: (String) -> Unit,
    val multiSelect: Boolean = true,
    val delimiter: String = "|"
)

internal enum class FavoritePickerFilter(val label: String) {
    IMAGE("图片"),
    TEXT("文字"),
    VOICE("语音"),
    VIDEO("视频"),
    OTHER("其他"),
    ALL("全部");

    fun matches(type: Int): Boolean = when (this) {
        IMAGE -> type == 2
        TEXT -> type == 1
        VOICE -> type == 3
        VIDEO -> type == 4
        OTHER -> type !in setOf(1, 2, 3, 4)
        ALL -> true
    }
}

internal data class FavoriteMediaPreview(
    val item: WeChatFavoriteItem,
    val path: String
)

internal data class MessageBlockContactPickerRequest(
    val title: String,
    val existingValue: String,
    val onValue: (String) -> Unit,
    val allowOfficialAccounts: Boolean = true
)

internal data class GroupMemberPickerRequest(
    val title: String,
    val existingValue: String,
    val onValue: (String) -> Unit,
    val allowedGroupIds: Set<String>? = null
)

internal data class RedPacketTemplateEditorRequest(
    val index: Int,
    val template: RedPacketRuleTemplate,
    val canDelete: Boolean
)

internal data class RedPacketBindingEditorRequest(
    val index: Int,
    val binding: RedPacketRuleBinding,
    val canDelete: Boolean
)

internal data class TransferTemplateEditorRequest(
    val index: Int,
    val template: TransferRuleTemplate,
    val canDelete: Boolean
)

internal data class TransferBindingEditorRequest(
    val index: Int,
    val binding: TransferRuleBinding,
    val canDelete: Boolean
)

internal data class MessageBlockTemplateEditorRequest(
    val index: Int,
    val template: MessageBlockTemplate,
    val canDelete: Boolean
)

internal data class MessageBlockBindingEditorRequest(
    val index: Int,
    val binding: MessageBlockBinding,
    val canDelete: Boolean
)

internal data class MessageBlockBatchBindingEditorRequest(
    val title: String,
    val bindings: List<MessageBlockBinding>
)

internal data class GroupLeaveTemplateEditorRequest(
    val index: Int,
    val template: GroupLeaveReplyTemplate,
    val canDelete: Boolean
)

internal data class MessageBlockTypeOption(
    val key: String,
    val title: String,
    val summary: String
)

internal data class MessageBlockRuleState(
    val typeAll: Boolean,
    val types: Set<String>,
    val textKeywords: String
)

internal data class OptionPickerRequest(
    val title: String,
    val options: List<OptionItem>,
    val currentValue: Int,
    val onSelected: (OptionItem) -> Unit
)

internal data class OptionItem(
    val label: String,
    val value: Int,
    val summary: String = ""
)

internal data class PopupChoice<T>(
    val label: String,
    val value: T
)

internal fun optionItems(vararg items: Pair<String, Int>): List<OptionItem> {
    return items.map { OptionItem(label = it.first, value = it.second) }
}

internal fun ringtoneDisplayName(context: Context, uri: String, mode: Int): String {
    if (uri.isBlank()) return "跟随系统"
    val parsed = try {
        Uri.parse(uri)
    } catch (_: Throwable) {
        null
    }
    if (mode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) {
        queryDisplayName(context, parsed)?.let { return it }
    }
    return try {
        val ringtone = parsed?.let { RingtoneManager.getRingtone(context, it) }
        ringtone?.getTitle(context)?.takeIf { it.isNotBlank() }
            ?: queryDisplayName(context, parsed)
            ?: "已选择自定义铃声"
    } catch (_: Throwable) {
        queryDisplayName(context, parsed)
            ?: "已选择自定义铃声"
    }
}

internal fun queryDisplayName(context: Context, uri: Uri?): String? {
    if (uri == null) return null
    if ("content".equals(uri.scheme, ignoreCase = true)) {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            return cursor.getString(index)?.takeIf { it.isNotBlank() }
                        }
                    }
                }
        } catch (_: Throwable) {
        }
    }
    return uri.lastPathSegment
        ?.let { Uri.decode(it) }
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.takeIf { it.isNotBlank() }
}

internal data class ContactOption(
    val id: String,
    val label: String,
    val group: Boolean,
    val avatarUrl: String,
    val avatarBackupUrl: String,
    val labels: List<String> = emptyList(),
    val official: Boolean = false,
    val extraSummary: String = "",
    val searchAliases: List<String> = emptyList()
)

internal fun ContactOption.matchesSearch(lower: String): Boolean {
    return lower.isEmpty() ||
        label.lowercase(Locale.US).contains(lower) ||
        id.lowercase(Locale.US).contains(lower) ||
        labels.any { it.lowercase(Locale.US).contains(lower) } ||
        extraSummary.lowercase(Locale.US).contains(lower) ||
        searchAliases.any { it.lowercase(Locale.US).contains(lower) }
}

internal data class MessageBlockContactOption(
    val contact: ContactOption,
    val kind: MessageBlockContactKind,
    val labelNames: List<String> = emptyList()
)

internal data class MessageBlockLabelOption(
    val id: String,
    val name: String,
    val contactIds: List<String>
)

internal data class MessageBlockContactData(
    val contacts: List<MessageBlockContactOption> = emptyList(),
    val labels: List<MessageBlockLabelOption> = emptyList()
)

internal data class MessageBlockInitialState(
    val templates: List<MessageBlockTemplate>,
    val bindings: List<MessageBlockBinding>,
    val shouldPersist: Boolean
)

internal fun newRedPacketTemplate(index: Int, sp: SharedPreferences): RedPacketRuleTemplate {
    val replyEnabled = sp.getBoolean(RedPacketSettings.KEY_REPLY_ENABLE, false)
    val storedReplyMode = if (replyEnabled) {
        sp.getInt(RedPacketSettings.KEY_REPLY_TYPE, RedPacketRuleConfig.REPLY_TEXT)
    } else {
        RedPacketRuleConfig.REPLY_OFF
    }
    val replySteps = loadGlobalRedPacketReplySteps(sp)
    val firstReply = replySteps.firstOrNull()
    val legacyReplyText = if (redPacketReplyUsesText(storedReplyMode)) {
        sp.getString(
            RedPacketSettings.KEY_REPLY_TEMPLATES,
            sp.getString(RedPacketSettings.KEY_REPLY_TEXT, "谢谢老板") ?: "谢谢老板"
        ) ?: "谢谢老板"
    } else {
        sp.getString(RedPacketSettings.KEY_REPLY_MEDIA_PATHS, "") ?: ""
    }
    return RedPacketRuleTemplate(
        id = System.currentTimeMillis().toString() + "_" + index,
        name = "模板 $index",
        enabled = false,
        grabMode = sp.getInt(RedPacketSettings.KEY_GRAB_MODE, RedPacketSettings.DEFAULT_GRAB_MODE),
        delayMode = redPacketDelayModeFromPrefs(sp),
        delayMs = redPacketDelayMillisFromPrefs(sp),
        randomMinMs = sp.getInt(RedPacketSettings.KEY_DELAY_RANDOM_MIN, 500).coerceAtLeast(0).toLong(),
        randomMaxMs = sp.getInt(RedPacketSettings.KEY_DELAY_RANDOM_MAX, 3000).coerceAtLeast(0).toLong(),
        skipSelf = sp.getBoolean(RedPacketSettings.KEY_SKIP_SELF, false),
        listMode = 0,
        whitelist = "",
        blacklist = "",
        keywordMode = sp.getInt(RedPacketSettings.KEY_KW_MODE, 0),
        keywords = sp.getString(RedPacketSettings.KEY_KEYWORDS, "") ?: "",
        quietEnabled = false,
        quietStartSecond = 0,
        quietEndSecond = 0,
        replyMode = firstReply?.mode ?: normalizeRedPacketReplyModeForUi(storedReplyMode),
        replyText = firstReply?.content ?: normalizeRedPacketReplyTextForUi(storedReplyMode, legacyReplyText),
        replyDelayMs = firstReply?.delayMs ?: redPacketReplyDelayMillisFromPrefs(sp),
        replyRandom = firstReply?.random ?: sp.getBoolean(RedPacketSettings.KEY_REPLY_RANDOM, false),
        replySteps = replySteps,
        groupReplySteps = loadGlobalGroupRedPacketReplySteps(sp),
        notificationConfigured = true,
        notifySystemEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SYSTEM_ENABLE, false),
        notifyToastEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_TOAST_ENABLE, false),
        notifySoundEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SOUND_ENABLE, false),
        notifySoundMode = sp.getInt(RedPacketSettings.KEY_NOTIFY_SOUND_MODE, RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM),
        notifyVibrateEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_VIBRATE_ENABLE, false),
        notifySoundUri = sp.getString(RedPacketSettings.KEY_NOTIFY_SOUND_URI, "") ?: "",
        notifyText = sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元",
        notifyToastText = sp.getString(
            RedPacketSettings.KEY_NOTIFY_TOAST_TEXT,
            sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
        ) ?: "抢到红包 {amount} 元",
        notifyFailedSystemEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_SYSTEM_ENABLE, false),
        notifyFailedToastEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_ENABLE, false),
        notifyFailedText = sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "未抢到红包",
        notifyFailedToastText = sp.getString(
            RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_TEXT,
            sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "未抢到红包"
        ) ?: "未抢到红包",
        announceEnabled = sp.getBoolean(RedPacketSettings.KEY_ANNOUNCE_ENABLE, false),
        announceText = sp.getString(RedPacketSettings.KEY_ANNOUNCE_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
    )
}

internal fun transferReceiveAccountOptions(context: Context): List<PopupChoice<String>> {
    return listOf(
        PopupChoice("零钱", TransferReceiveAccountStore.DEFAULT_KEY),
        PopupChoice("零钱通", TransferReceiveAccountStore.LQT_KEY),
        PopupChoice("经营账户", TransferReceiveAccountStore.BUSINESS_KEY)
    ) + TransferReceiveAccountStore.list(context)
        .filter { it.available }
        .filterNot {
            val name = it.name.replace(" ", "")
            name == "零钱" || name.contains("零钱通") || name.contains("经营") || name.contains("商户")
        }
        .map { PopupChoice(it.name, it.key) }
}

internal fun loadGlobalTransferReplySteps(sp: SharedPreferences): List<RedPacketReplyStep> {
    val stored = sp.getString(AutoTransferSettings.KEY_REPLY_ITEMS, "").orEmpty()
    if (stored.isNotBlank()) return RedPacketRuleConfig.parseReplySteps(stored).map(::normalizeRedPacketReplyStepForUi)
    if (!sp.getBoolean(AutoTransferSettings.KEY_REPLY_ENABLE, false)) return emptyList()
    return RedPacketRuleConfig.legacyReplySteps(
        RedPacketRuleConfig.REPLY_TEXT,
        sp.getString(AutoTransferSettings.KEY_REPLY_TEXT, "谢谢老板") ?: "谢谢老板",
        1000L,
        false
    )
}

internal fun loadGlobalGroupTransferReplySteps(sp: SharedPreferences): List<RedPacketReplyStep> {
    if (!sp.contains(AutoTransferSettings.KEY_REPLY_GROUP_ITEMS)) {
        return loadGlobalTransferReplySteps(sp)
    }
    return RedPacketRuleConfig.parseReplySteps(
        sp.getString(AutoTransferSettings.KEY_REPLY_GROUP_ITEMS, "") ?: ""
    ).map(::normalizeRedPacketReplyStepForUi)
}

internal fun newTransferTemplate(index: Int, sp: SharedPreferences): TransferRuleTemplate {
    val minDelay = sp.getLong(AutoTransferSettings.KEY_DELAY_RANDOM_MIN, 500L).coerceIn(0L, 600000L)
    val maxDelay = sp.getLong(AutoTransferSettings.KEY_DELAY_RANDOM_MAX, 3000L).coerceIn(minDelay, 600000L)
    return TransferRuleTemplate(
        id = "transfer_${System.currentTimeMillis()}_$index",
        name = "收款模板 $index",
        enabled = true,
        delayMode = sp.getInt(AutoTransferSettings.KEY_DELAY_MODE, TransferRuleConfig.DELAY_CUSTOM),
        delayMs = sp.getLong(AutoTransferSettings.KEY_DELAY_MS, 0L).coerceIn(0L, 600000L),
        randomMinMs = minDelay,
        randomMaxMs = maxDelay,
        receiveAccount = sp.getString(AutoTransferSettings.KEY_RECEIVE_ACCOUNT, TransferReceiveAccountStore.DEFAULT_KEY)
            ?: TransferReceiveAccountStore.DEFAULT_KEY,
        listMode = sp.getInt(AutoTransferSettings.KEY_MODE, 0),
        whitelist = sp.getString(AutoTransferSettings.KEY_WHITELIST, "").orEmpty(),
        blacklist = sp.getString(AutoTransferSettings.KEY_BLACKLIST, "").orEmpty(),
        amountEnabled = sp.getBoolean(AutoTransferSettings.KEY_AMOUNT_ENABLE, false),
        amountCondition = sp.getInt(AutoTransferSettings.KEY_AMOUNT_COND, 1),
        amountValue = sp.getString(AutoTransferSettings.KEY_AMOUNT_VALUE, "0") ?: "0",
        amountAction = sp.getInt(AutoTransferSettings.KEY_AMOUNT_ACTION, 0),
        keywordMode = sp.getInt(AutoTransferSettings.KEY_KEYWORD_MODE, 0),
        keywords = sp.getString(AutoTransferSettings.KEY_KEYWORDS, "").orEmpty(),
        quietEnabled = sp.getBoolean(AutoTransferSettings.KEY_QUIET_ENABLE, false),
        quietStartSecond = sp.getInt(AutoTransferSettings.KEY_QUIET_START_SECOND, 0),
        quietEndSecond = sp.getInt(AutoTransferSettings.KEY_QUIET_END_SECOND, 0),
        refundRejected = sp.getBoolean(AutoTransferSettings.KEY_REFUND_REJECTED, false),
        replySteps = loadGlobalTransferReplySteps(sp),
        groupReplySteps = loadGlobalGroupTransferReplySteps(sp),
        notificationConfigured = true,
        notifySystemEnabled = sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_SYSTEM_ENABLE, false),
        notifyToastEnabled = sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_TOAST_ENABLE, false),
        notifySoundEnabled = sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_SOUND_ENABLE, false),
        notifySoundMode = sp.getInt(AutoTransferSettings.KEY_NOTIFY_SOUND_MODE, AutoTransferSettings.NOTIFY_SOUND_MODE_SYSTEM),
        notifyVibrateEnabled = sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_VIBRATE_ENABLE, false),
        notifySoundUri = sp.getString(AutoTransferSettings.KEY_NOTIFY_SOUND_URI, "").orEmpty(),
        notifyText = sp.getString(AutoTransferSettings.KEY_NOTIFY_TEXT, "已收款 {amount} 元") ?: "已收款 {amount} 元",
        notifyToastText = sp.getString(AutoTransferSettings.KEY_NOTIFY_TOAST_TEXT, "已收款 {amount} 元") ?: "已收款 {amount} 元",
        announceEnabled = sp.getBoolean(AutoTransferSettings.KEY_ANNOUNCE_ENABLE, false),
        announceText = sp.getString(AutoTransferSettings.KEY_ANNOUNCE_TEXT, "收到转账 {amount} 元") ?: "收到转账 {amount} 元"
    )
}

internal fun describeTransferDefault(id: String, templates: List<TransferRuleTemplate>): String {
    if (id.isBlank()) return "旧版全局设置"
    return templates.firstOrNull { it.id == id }?.name ?: "模板不存在，使用全局设置"
}

internal fun describeTransferTemplate(value: TransferRuleTemplate): String {
    val state = if (value.enabled) "启用" else "关闭"
    val delay = when (value.delayMode) {
        TransferRuleConfig.DELAY_RANDOM -> "随机 ${value.randomMinMs}-${value.randomMaxMs}ms"
        TransferRuleConfig.DELAY_CUSTOM -> "延迟 ${value.delayMs}ms"
        else -> "无延迟"
    }
    val amount = if (value.amountEnabled) {
        "金额${transferAmountConditionLabel(value.amountCondition)}${value.amountValue}"
    } else null
    val quiet = if (value.quietEnabled) {
        "禁收 ${formatRedPacketSecond(value.quietStartSecond)}-${formatRedPacketSecond(value.quietEndSecond)}"
    } else null
    return listOfNotNull(
        state,
        delay,
        transferKeywordModeLabel(value.keywordMode),
        amount,
        quiet,
        "私聊 ${describeRedPacketReplySteps(value.replySteps)}",
        "群聊 ${describeRedPacketReplySteps(value.groupReplySteps ?: value.replySteps)}"
    ).joinToString(" · ")
}

internal fun describeTransferBinding(value: TransferRuleBinding, templates: List<TransferRuleTemplate>): String {
    val state = if (value.enabled) "启用" else "关闭"
    val template = templates.firstOrNull { it.id == value.templateId }?.name ?: "跟随默认规则"
    return "$state · $template · ${value.targetId}"
}

internal fun transferContactLabel(id: String): String = runCatching {
    WeChatApis.contact().contacts()?.getDisplayName(id).orEmpty().ifBlank { id }
}.getOrDefault(id)

internal fun upsertTransferBindings(
    current: List<TransferRuleBinding>,
    additions: List<TransferRuleBinding>
): List<TransferRuleBinding> {
    val result = linkedMapOf<String, TransferRuleBinding>()
    current.forEach { if (it.targetId.isNotBlank()) result[it.targetId.trim()] = it.copy(id = it.targetId.trim()) }
    additions.forEach {
        val id = it.targetId.trim()
        if (id.isNotBlank()) result[id] = it.copy(id = id, targetId = id, label = it.label.ifBlank { transferContactLabel(id) })
    }
    return result.values.toList()
}

internal fun transferDelayModeLabel(value: Int): String = when (value) {
    TransferRuleConfig.DELAY_RANDOM -> "随机延迟"
    TransferRuleConfig.DELAY_CUSTOM -> "自定义延迟"
    else -> "无延迟"
}

internal fun transferListModeLabel(value: Int): String = when (value) {
    1 -> "只接收白名单"
    2 -> "拒收黑名单"
    else -> "全部接收"
}

internal fun transferAmountConditionLabel(value: Int): String = when (value) {
    0 -> "大于"
    2 -> "等于"
    else -> "小于"
}

internal fun transferAmountActionLabel(value: Int): String = if (value == 1) "仅接收满足条件" else "拒收/忽略"

internal fun transferKeywordModeLabel(value: Int): String = when (value) {
    1 -> "必须包含关键词"
    2 -> "包含则拒收"
    else -> "不启用"
}

internal fun loadGlobalRedPacketReplySteps(sp: SharedPreferences): List<RedPacketReplyStep> {
    val stored = sp.getString(RedPacketSettings.KEY_REPLY_ITEMS, "") ?: ""
    if (stored.isNotBlank()) {
        return RedPacketRuleConfig.parseReplySteps(stored).map { normalizeRedPacketReplyStepForUi(it) }
    }
    val replyEnabled = sp.getBoolean(RedPacketSettings.KEY_REPLY_ENABLE, false)
    if (!replyEnabled) return emptyList()
    val replyMode = sp.getInt(RedPacketSettings.KEY_REPLY_TYPE, RedPacketRuleConfig.REPLY_TEXT)
    val content = if (redPacketReplyUsesText(replyMode)) {
        sp.getString(
            RedPacketSettings.KEY_REPLY_TEMPLATES,
            sp.getString(RedPacketSettings.KEY_REPLY_TEXT, "谢谢老板") ?: "谢谢老板"
        ) ?: "谢谢老板"
    } else {
        sp.getString(RedPacketSettings.KEY_REPLY_MEDIA_PATHS, "") ?: ""
    }
    return normalizeRedPacketReplyStepsForUi(
        RedPacketRuleConfig.legacyReplySteps(
            replyMode,
            content,
            redPacketReplyDelayMillisFromPrefs(sp),
            sp.getBoolean(RedPacketSettings.KEY_REPLY_RANDOM, false)
        ),
        RedPacketRuleConfig.REPLY_OFF,
        "",
        0L,
        false
    )
}

internal fun loadGlobalGroupRedPacketReplySteps(sp: SharedPreferences): List<RedPacketReplyStep> {
    if (!sp.contains(RedPacketSettings.KEY_REPLY_GROUP_ITEMS)) {
        return loadGlobalRedPacketReplySteps(sp)
    }
    return RedPacketRuleConfig.parseReplySteps(
        sp.getString(RedPacketSettings.KEY_REPLY_GROUP_ITEMS, "") ?: ""
    ).map(::normalizeRedPacketReplyStepForUi)
}

private fun redPacketDelayMillisFromPrefs(sp: SharedPreferences): Long {
    val value = sp.getInt(RedPacketSettings.KEY_DELAY_VALUE, 0).coerceAtLeast(0)
    return if (sp.getInt(RedPacketSettings.KEY_DELAY_UNIT, 0) == 1) value * 1000L else value.toLong()
}

internal fun redPacketDelayModeFromPrefs(sp: SharedPreferences): Int {
    return when (val storedMode = sp.getInt(RedPacketSettings.KEY_DELAY_MODE, Int.MIN_VALUE)) {
        RedPacketRuleConfig.DELAY_FIXED,
        RedPacketRuleConfig.DELAY_CUSTOM,
        RedPacketRuleConfig.DELAY_RANDOM -> storedMode
        else -> if (redPacketDelayMillisFromPrefs(sp) > 0L) {
            RedPacketRuleConfig.DELAY_CUSTOM
        } else {
            RedPacketRuleConfig.DELAY_FIXED
        }
    }
}

private fun redPacketReplyDelayMillisFromPrefs(sp: SharedPreferences): Long {
    if (!sp.getBoolean(RedPacketSettings.KEY_REPLY_CUSTOM_ENABLE, false)) return 0L
    val value = sp.getInt(RedPacketSettings.KEY_REPLY_DELAY_VALUE, 1).coerceAtLeast(0)
    return if (sp.getInt(RedPacketSettings.KEY_REPLY_DELAY_UNIT, 1) == 1) value * 1000L else value.toLong()
}

internal fun normalizeRedPacketDelayModeForUi(template: RedPacketRuleTemplate): Int {
    return when (template.delayMode) {
        RedPacketRuleConfig.DELAY_RANDOM -> RedPacketRuleConfig.DELAY_RANDOM
        RedPacketRuleConfig.DELAY_CUSTOM -> RedPacketRuleConfig.DELAY_CUSTOM
        else -> if (template.delayMs > 0L) RedPacketRuleConfig.DELAY_CUSTOM else RedPacketRuleConfig.DELAY_FIXED
    }
}

internal fun redPacketDelayModeLabel(mode: Int): String {
    return when (mode) {
        RedPacketRuleConfig.DELAY_CUSTOM -> "自定义延迟"
        RedPacketRuleConfig.DELAY_RANDOM -> "随机延迟"
        else -> "无延迟"
    }
}

internal fun redPacketDelayModeOptions(includeRandom: Boolean): List<OptionItem> {
    val options = mutableListOf(
        OptionItem("无延迟", RedPacketRuleConfig.DELAY_FIXED),
        OptionItem("自定义延迟", RedPacketRuleConfig.DELAY_CUSTOM)
    )
    if (includeRandom) options += OptionItem("随机延迟", RedPacketRuleConfig.DELAY_RANDOM)
    return options
}

internal fun describeRedPacketDefaultTemplate(
    defaultTemplateId: String,
    templates: List<RedPacketRuleTemplate>
): String {
    if (defaultTemplateId.isBlank()) return "旧版全局设置"
    val template = templates.firstOrNull { it.id == defaultTemplateId } ?: return "模板不存在，当前会回到旧版全局设置"
    return template.name.ifBlank { defaultTemplateId }
}

internal fun describeRedPacketTemplate(template: RedPacketRuleTemplate): String {
    val state = if (template.enabled) "启用" else "关闭"
    val mode = if (template.grabMode == 1) "静默" else "打开页面"
    val delay = when (normalizeRedPacketDelayModeForUi(template)) {
        RedPacketRuleConfig.DELAY_RANDOM -> {
            val minDelay = template.randomMinMs.coerceAtLeast(0L)
            val maxDelay = template.randomMaxMs.coerceAtLeast(minDelay)
            "随机 $minDelay-${maxDelay}ms"
        }
        RedPacketRuleConfig.DELAY_CUSTOM -> "延迟 ${template.delayMs.coerceAtLeast(0L)}ms"
        else -> "无延迟"
    }
    val keyword = when (template.keywordMode) {
        1 -> "只抢关键词"
        2 -> "屏蔽关键词"
        else -> "不限关键词"
    }
    val quiet = if (template.quietEnabled) {
        "禁抢 ${formatRedPacketSecond(template.quietStartSecond)}-${formatRedPacketSecond(template.quietEndSecond)}"
    } else {
        null
    }
    val privateReply = describeRedPacketReplySteps(
        normalizeRedPacketReplyStepsForUi(
            template.replySteps,
            template.replyMode,
            template.replyText,
            template.replyDelayMs,
            template.replyRandom
        )
    )
    val groupReply = describeRedPacketReplySteps(
        template.groupReplySteps
            ?.map(::normalizeRedPacketReplyStepForUi)
            ?: normalizeRedPacketReplyStepsForUi(
                template.replySteps,
                template.replyMode,
                template.replyText,
                template.replyDelayMs,
                template.replyRandom
            )
    )
    val notify = describeRedPacketTemplateNotify(template)
    return listOfNotNull(
        state,
        mode,
        delay,
        keyword,
        quiet,
        "私聊 $privateReply",
        "群聊 $groupReply",
        notify
    ).joinToString(" · ")
}

private fun describeRedPacketTemplateNotify(template: RedPacketRuleTemplate): String? {
    if (!template.notificationConfigured) return "通知沿用全局"
    val items = buildList {
        if (template.notifySystemEnabled) add("通知栏")
        if (template.notifyToastEnabled) add("浮窗")
        if (template.notifySoundEnabled) add("铃声")
        if (template.notifyVibrateEnabled) add("震动")
        if (template.announceEnabled) add("播报")
        if (template.notifyFailedSystemEnabled || template.notifyFailedToastEnabled) add("未抢到提醒")
    }
    return if (items.isEmpty()) "通知关闭" else items.joinToString("/")
}

private fun redPacketReplyModeOptions(): List<OptionItem> {
    return listOf(
        OptionItem("不回复", RedPacketRuleConfig.REPLY_OFF, "只抢红包，不发送回复"),
        OptionItem("发送文字", RedPacketRuleConfig.REPLY_TEXT, "抢到后发送文字"),
        OptionItem("发送图片", RedPacketRuleConfig.REPLY_IMAGE, "从系统文件管理器选择图片"),
        OptionItem("发送语音", RedPacketRuleConfig.REPLY_VOICE, "从系统文件管理器选择语音文件"),
        OptionItem("发送视频", RedPacketRuleConfig.REPLY_VIDEO, "从系统文件管理器选择视频"),
        OptionItem("发送表情", RedPacketRuleConfig.REPLY_EMOJI, "从系统文件管理器选择表情文件"),
        OptionItem("发送文件", RedPacketRuleConfig.REPLY_FILE, "从系统文件管理器选择任意文件"),
        OptionItem("发送收藏", RedPacketRuleConfig.REPLY_FAVORITE, "从最近收藏选择"),
        OptionItem("发送 XML", RedPacketRuleConfig.REPLY_XML, "发送 AppMsg/XML 内容")
    )
}

private fun redPacketReplyStepModeOptions(): List<OptionItem> {
    return redPacketReplyModeOptions().filter { it.value != RedPacketRuleConfig.REPLY_OFF }
}

internal fun redPacketReplyUsesText(replyMode: Int): Boolean {
    return replyMode == RedPacketRuleConfig.REPLY_TEXT ||
        replyMode == RedPacketRuleConfig.REPLY_AT_SENDER
}

private fun normalizeRedPacketReplyModeForUi(replyMode: Int): Int {
    return if (replyMode == RedPacketRuleConfig.REPLY_AT_SENDER) {
        RedPacketRuleConfig.REPLY_TEXT
    } else {
        replyMode
    }
}

private fun normalizeRedPacketReplyTextForUi(replyMode: Int, text: String): String {
    if (replyMode != RedPacketRuleConfig.REPLY_AT_SENDER) return text
    if (redPacketReplyContainsAtSenderVariable(text)) return text
    return REDPACKET_AT_SENDER_VARIABLE + text
}

private fun redPacketReplyContainsAtSenderVariable(text: String): Boolean {
    return REDPACKET_AT_SENDER_VARIABLES.any { text.contains(it) }
}

internal fun normalizeRedPacketReplyStepsForUi(
    steps: List<RedPacketReplyStep>,
    legacyMode: Int,
    legacyText: String,
    legacyDelayMs: Long,
    legacyRandom: Boolean
): List<RedPacketReplyStep> {
    val source = if (steps.isNotEmpty()) {
        steps
    } else {
        RedPacketRuleConfig.legacyReplySteps(legacyMode, legacyText, legacyDelayMs, legacyRandom)
    }
    return source.map { normalizeRedPacketReplyStepForUi(it) }
}

internal fun normalizeRedPacketReplyStepForUi(step: RedPacketReplyStep): RedPacketReplyStep {
    val mode = normalizeRedPacketReplyModeForUi(step.mode)
    val content = normalizeRedPacketReplyTextForUi(step.mode, step.content)
    return step.copy(
        id = step.id.ifBlank { System.currentTimeMillis().toString() },
        mode = mode,
        content = content,
        delayMs = step.delayMs.coerceIn(0L, 600000L)
    )
}

internal fun cleanRedPacketReplySteps(steps: List<RedPacketReplyStep>): List<RedPacketReplyStep> {
    return steps.map { normalizeRedPacketReplyStepForUi(it) }
        .filter { it.mode != RedPacketRuleConfig.REPLY_OFF && it.content.isNotBlank() }
}

private fun newRedPacketReplyStep(index: Int): RedPacketReplyStep {
    return RedPacketReplyStep(
        id = System.currentTimeMillis().toString() + "_$index",
        mode = RedPacketRuleConfig.REPLY_TEXT,
        content = "谢谢老板",
        delayMs = 0L,
        random = false
    )
}

private fun defaultRedPacketReplyContent(replyMode: Int): String {
    return when {
        redPacketReplyUsesText(replyMode) -> "谢谢老板"
        replyMode == RedPacketRuleConfig.REPLY_XML -> "<msg><appmsg appid=\"\" sdkver=\"0\"><title>谢谢老板</title></appmsg></msg>"
        else -> ""
    }
}

private fun redPacketReplyModeLabel(replyMode: Int): String {
    return when (normalizeRedPacketReplyModeForUi(replyMode)) {
        RedPacketRuleConfig.REPLY_TEXT -> "发送文字"
        RedPacketRuleConfig.REPLY_IMAGE -> "发送图片"
        RedPacketRuleConfig.REPLY_VOICE -> "发送语音"
        RedPacketRuleConfig.REPLY_VIDEO -> "发送视频"
        RedPacketRuleConfig.REPLY_EMOJI -> "发送表情"
        RedPacketRuleConfig.REPLY_FILE -> "发送文件"
        RedPacketRuleConfig.REPLY_FAVORITE -> "发送收藏"
        RedPacketRuleConfig.REPLY_XML -> "发送 XML"
        else -> "不回复"
    }
}

internal fun describeRedPacketReplySteps(steps: List<RedPacketReplyStep>): String {
    if (steps.isEmpty()) return "不回复"
    val types = steps.take(3).joinToString("/") { redPacketReplyModeShortLabel(it.mode) }
    val suffix = if (steps.size > 3) "等" else ""
    return "${steps.size} 条回复 · $types$suffix · 顺序发送"
}

private fun redPacketReplyStepSummary(step: RedPacketReplyStep): String {
    val content = if (redPacketReplyUsesText(step.mode) || step.mode == RedPacketRuleConfig.REPLY_XML) {
        step.content.trim().replace('\n', ' ').ifBlank { "未填写内容" }.take(24)
    } else {
        redPacketReplyContentSummary(step.mode, step.content, withHint = false).removePrefix("已选择：")
    }
    val delay = step.delayMs.coerceAtLeast(0L)
    val delayText = if (delay > 0L) "延迟 ${delay}ms" else "无延迟"
    val randomText = if (step.random) "随机追加" else "固定"
    return "$content · $delayText · $randomText"
}

private fun redPacketReplyModeShortLabel(replyMode: Int): String {
    return when (normalizeRedPacketReplyModeForUi(replyMode)) {
        RedPacketRuleConfig.REPLY_TEXT -> "文字"
        RedPacketRuleConfig.REPLY_IMAGE -> "图片"
        RedPacketRuleConfig.REPLY_VOICE -> "语音"
        RedPacketRuleConfig.REPLY_VIDEO -> "视频"
        RedPacketRuleConfig.REPLY_EMOJI -> "表情"
        RedPacketRuleConfig.REPLY_FILE -> "文件"
        RedPacketRuleConfig.REPLY_FAVORITE -> "收藏"
        RedPacketRuleConfig.REPLY_XML -> "XML"
        else -> "关闭"
    }
}

internal fun redPacketReplyPickerTitle(replyMode: Int): String {
    return when (replyMode) {
        RedPacketRuleConfig.REPLY_IMAGE -> "选择图片"
        RedPacketRuleConfig.REPLY_VOICE -> "选择语音文件"
        RedPacketRuleConfig.REPLY_VIDEO -> "选择视频"
        RedPacketRuleConfig.REPLY_EMOJI -> "选择表情文件"
        RedPacketRuleConfig.REPLY_FILE -> "选择文件"
        RedPacketRuleConfig.REPLY_FAVORITE -> "选择收藏"
        else -> "选择文件"
    }
}

internal fun redPacketReplyContentSummary(replyMode: Int, value: String, withHint: Boolean = true): String {
    return if (replyMode == RedPacketRuleConfig.REPLY_FAVORITE) {
        favoriteSummary(value)
    } else {
        redPacketReplyFileSummary(value, withHint)
    }
}

private fun redPacketReplyFileSummary(value: String, withHint: Boolean = true): String {
    val names = RedPacketRuleConfig.splitTokens(value)
        .map { redPacketReplyFileDisplayName(it) }
        .filter { it.isNotBlank() }
    if (names.isEmpty()) return "未选择文件"
    val preview = names.take(3).joinToString("、")
    val suffix = when {
        names.size == 1 -> ""
        names.size > 3 -> " 等 ${names.size} 个文件"
        else -> "，共 ${names.size} 个"
    }
    val summary = "已选择：$preview$suffix"
    return if (withHint) "$summary，点击重新选择" else summary
}

private fun redPacketReplyFileDisplayName(value: String): String {
    val raw = value.trim()
    if (raw.isBlank()) return ""
    val name = if (raw.startsWith("content://", ignoreCase = true) || raw.startsWith("file://", ignoreCase = true)) {
        runCatching { Uri.parse(raw) }.getOrNull()
            ?.lastPathSegment
            ?.let { Uri.decode(it) }
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            .orEmpty()
            .ifBlank { raw.substringAfterLast('/') }
    } else {
        File(raw).name.ifBlank { raw.substringAfterLast('/') }
    }
    val cacheNameMatch = Regex("""^\d{10,}_(.+)$""").matchEntire(name)
    return (cacheNameMatch?.groupValues?.getOrNull(1) ?: name).trim()
}

internal fun describeRedPacketBinding(
    binding: RedPacketRuleBinding,
    templates: List<RedPacketRuleTemplate>
): String {
    val state = if (binding.enabled) "启用" else "关闭"
    val template = templates.firstOrNull { it.id == binding.templateId }
        ?.name
        ?.ifBlank { binding.templateId }
        ?: "跟随默认规则"
    return "$state · $template · ${binding.targetId}"
}

internal fun redPacketBindingMatchesQuery(
    binding: RedPacketRuleBinding,
    templates: List<RedPacketRuleTemplate>,
    lowerQuery: String
): Boolean {
    if (binding.label.lowercase(Locale.US).contains(lowerQuery)) return true
    if (binding.targetId.lowercase(Locale.US).contains(lowerQuery)) return true
    val template = templates.firstOrNull { it.id == binding.templateId }
    return template != null && (
        template.name.lowercase(Locale.US).contains(lowerQuery) ||
            describeRedPacketTemplate(template).lowercase(Locale.US).contains(lowerQuery)
        )
}

internal fun redPacketBindingFromContact(
    row: MessageBlockContactOption,
    templates: List<RedPacketRuleTemplate>,
    existing: RedPacketRuleBinding?
): RedPacketRuleBinding {
    val option = row.contact
    val targetId = option.id.trim()
    return RedPacketRuleBinding(
        id = existing?.id ?: RedPacketRuleConfig.bindingKey(targetId),
        targetId = targetId,
        label = existing?.label?.takeIf { it.isNotBlank() } ?: option.label.ifBlank { targetId },
        enabled = existing?.enabled ?: false,
        templateId = existing?.templateId ?: if (templates.size == 1) templates.first().id else "",
        customRules = false,
        overrideRule = null
    )
}

internal fun findRedPacketBinding(
    bindings: List<RedPacketRuleBinding>,
    targetId: String
): RedPacketRuleBinding? {
    val key = RedPacketRuleConfig.bindingKey(targetId)
    return bindings.firstOrNull { RedPacketRuleConfig.bindingKey(it.targetId) == key }
}

internal fun normalizedRedPacketBinding(binding: RedPacketRuleBinding): RedPacketRuleBinding {
    val targetId = binding.targetId.trim()
    return binding.copy(
        id = RedPacketRuleConfig.bindingKey(targetId),
        targetId = targetId,
        label = binding.label.ifBlank { targetId },
        templateId = binding.templateId.trim(),
        customRules = false,
        overrideRule = null
    )
}

internal fun upsertRedPacketBindings(
    current: List<RedPacketRuleBinding>,
    additions: List<RedPacketRuleBinding>
): List<RedPacketRuleBinding> {
    val result = linkedMapOf<String, RedPacketRuleBinding>()
    current.forEach { binding ->
        val normalized = normalizedRedPacketBinding(binding)
        if (normalized.targetId.isNotBlank()) {
            result[RedPacketRuleConfig.bindingKey(normalized.targetId)] = normalized
        }
    }
    additions.forEach { binding ->
        val normalized = normalizedRedPacketBinding(binding)
        if (normalized.targetId.isBlank()) return@forEach
        val key = RedPacketRuleConfig.bindingKey(normalized.targetId)
        val old = result[key]
        result[key] = if (old == null) {
            normalized
        } else {
            normalized.copy(
                id = old.id,
                enabled = old.enabled,
                label = old.label.ifBlank { normalized.label },
                templateId = old.templateId.ifBlank { normalized.templateId }
            )
        }
    }
    return result.values.toList()
}

internal fun formatRedPacketSecond(value: Int): String {
    val second = value.coerceIn(0, 86399)
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        second / 3600,
        second / 60 % 60,
        second % 60
    )
}

internal fun parseRedPacketSecond(value: String, fallback: Int): Int {
    return parseTimeOfDaySeconds(value).takeIf { it >= 0 } ?: fallback.coerceIn(0, 86399)
}

internal fun initialMessageBlockState(sp: SharedPreferences): MessageBlockInitialState {
    val rawTemplates = sp.getString(MessageBlockSettings.KEY_TEMPLATES, "") ?: ""
    val rawBindings = sp.getString(MessageBlockSettings.KEY_BINDINGS, "") ?: ""
    val parsedTemplates = MessageBlockSettings.parseTemplates(rawTemplates)
    val cleanedTemplates = parsedTemplates.map { MessageBlockSettings.clearTemplateTargets(it) }
    val existingBindings = MessageBlockSettings.parseBindings(rawBindings)
    val legacyBindings = if (rawBindings.isBlank()) {
        MessageBlockSettings.legacyBindingsFromTemplates(parsedTemplates)
    } else {
        emptyList()
    }
    val bindings = if (existingBindings.isNotEmpty()) existingBindings else legacyBindings
    val shouldPersist = rawBindings.isBlank() && legacyBindings.isNotEmpty()
    return MessageBlockInitialState(cleanedTemplates, bindings, shouldPersist)
}

internal fun newMessageBlockTemplate(index: Int): MessageBlockTemplate {
    return MessageBlockTemplate(
        id = System.currentTimeMillis().toString() + "_" + index,
        name = "模板 $index",
        enabled = true,
        mode = MessageBlockSettings.MODE_TARGETS,
        targets = "",
        targetGroupMembers = "",
        excludes = "",
        excludeGroupMembers = "",
        typeAll = false,
        types = emptySet(),
        textKeywords = ""
    )
}

internal fun describeMessageBlockTemplate(template: MessageBlockTemplate): String {
    val enabled = if (template.enabled) "启用" else "关闭"
    val type = describeMessageBlockRuleState(
        MessageBlockRuleState(template.typeAll, template.types, template.textKeywords)
    )
    return "$enabled · $type"
}

private fun describeMessageBlockRuleState(rules: MessageBlockRuleState): String {
    val type = if (rules.typeAll) "所有消息" else {
        val names = messageBlockTypeOptions()
            .filter { rules.types.contains(it.key) }
            .joinToString("、") { it.title }
        names.ifBlank { "未选类型" }
    }
    val keyword = if (rules.textKeywords.isBlank()) "" else "，关键词"
    return "$type$keyword"
}

internal fun messageBlockRuleStateFromTemplates(
    templateIds: Set<String>,
    templates: List<MessageBlockTemplate>
): MessageBlockRuleState {
    val selected = templates.filter { templateIds.contains(it.id) }
    if (selected.isEmpty()) return MessageBlockRuleState(false, emptySet(), "")
    val types = LinkedHashSet<String>()
    selected.forEach { template -> types += template.types }
    val textKeywords = selected.map { it.textKeywords.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("\n")
    return MessageBlockRuleState(
        typeAll = selected.any { it.typeAll },
        types = types,
        textKeywords = textKeywords
    )
}

internal fun describeMessageBlockBinding(binding: MessageBlockBinding, templates: List<MessageBlockTemplate>): String {
    if (binding.quickBlockAll) return "聊天分组快捷屏蔽 · 所有消息"
    val state = if (binding.enabled) "启用" else "关闭"
    val action = if (binding.action == MessageBlockSettings.ACTION_EXCLUDE) "排除" else "屏蔽"
    val names = templates
        .filter { binding.templateIds.contains(it.id) }
        .joinToString("、") { it.name.ifBlank { it.id } }
        .ifBlank { "未绑定模板" }
    val rules = if (binding.customRules) {
        " · 专属规则：${describeMessageBlockRuleState(MessageBlockRuleState(binding.typeAll, binding.types, binding.textKeywords))}"
    } else {
        ""
    }
    return "$state · $action · $names$rules"
}

internal fun describeMessageBlockDefaultRule(rule: MessageBlockDefaultRule, templates: List<MessageBlockTemplate>): String {
    if (!rule.enabled) return "未启用"
    val names = templates
        .filter { rule.templateIds.contains(it.id) }
        .joinToString("、") { it.name.ifBlank { it.id } }
    val rules = if (rule.customRules) {
        "专属规则：${describeMessageBlockRuleState(MessageBlockRuleState(rule.typeAll, rule.types, rule.textKeywords))}"
    } else {
        names.ifBlank { "未绑定模板" }
    }
    return "启用 · $rules"
}

internal fun messageBlockBindingMatchesQuery(
    binding: MessageBlockBinding,
    templates: List<MessageBlockTemplate>,
    lowerQuery: String
): Boolean {
    if (binding.label.lowercase(Locale.US).contains(lowerQuery)) return true
    if (binding.targetId.lowercase(Locale.US).contains(lowerQuery)) return true
    if (binding.customRules) {
        val rules = describeMessageBlockRuleState(
            MessageBlockRuleState(binding.typeAll, binding.types, binding.textKeywords)
        ).lowercase(Locale.US)
        if (rules.contains(lowerQuery) || binding.textKeywords.lowercase(Locale.US).contains(lowerQuery)) return true
    }
    return templates.any { template ->
        binding.templateIds.contains(template.id) &&
            (
                template.name.lowercase(Locale.US).contains(lowerQuery) ||
                    template.id.lowercase(Locale.US).contains(lowerQuery)
                )
    }
}

internal fun messageBlockBindingTargetSummary(binding: MessageBlockBinding): String {
    val type = if (binding.targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) "群成员" else "联系人 / 群聊 / 公众号"
    return "$type · ${binding.targetId}"
}

internal fun messageBlockBindingFromContact(
    option: ContactOption,
    templates: List<MessageBlockTemplate>,
    existing: MessageBlockBinding?
): MessageBlockBinding {
    val label = option.label.ifBlank { option.id }
    if (existing != null) return existing.copy(label = label)
    return MessageBlockBinding(
        id = MessageBlockSettings.bindingKey(MessageBlockSettings.TARGET_CONTACT, option.id),
        targetType = MessageBlockSettings.TARGET_CONTACT,
        targetId = option.id,
        label = label,
        enabled = true,
        action = MessageBlockSettings.ACTION_BLOCK,
        templateIds = defaultMessageBlockTemplateIds(templates)
    )
}

internal fun messageBlockBindingFromGroupMember(
    entry: String,
    templates: List<MessageBlockTemplate>,
    existing: MessageBlockBinding?
): MessageBlockBinding {
    val normalized = normalizeGroupMemberEntry(entry) ?: entry.trim()
    if (existing != null) return existing.copy(
        targetId = normalized,
        label = existing.label.ifBlank { normalized }
    )
    return MessageBlockBinding(
        id = MessageBlockSettings.bindingKey(MessageBlockSettings.TARGET_GROUP_MEMBER, normalized),
        targetType = MessageBlockSettings.TARGET_GROUP_MEMBER,
        targetId = normalized,
        label = normalized,
        enabled = true,
        action = MessageBlockSettings.ACTION_BLOCK,
        templateIds = defaultMessageBlockTemplateIds(templates)
    )
}

internal fun defaultMessageBlockTemplateIds(templates: List<MessageBlockTemplate>): Set<String> {
    return if (templates.size == 1) setOf(templates.first().id) else emptySet()
}

internal fun normalizedMessageBlockBinding(binding: MessageBlockBinding): MessageBlockBinding {
    val targetType = if (binding.targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        MessageBlockSettings.TARGET_GROUP_MEMBER
    } else {
        MessageBlockSettings.TARGET_CONTACT
    }
    val normalizedTarget = if (targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        normalizeGroupMemberEntry(binding.targetId) ?: binding.targetId.trim()
    } else {
        binding.targetId.trim()
    }
    val action = if (binding.action == MessageBlockSettings.ACTION_EXCLUDE) {
        MessageBlockSettings.ACTION_EXCLUDE
    } else {
        MessageBlockSettings.ACTION_BLOCK
    }
    return binding.copy(
        id = MessageBlockSettings.bindingKey(targetType, normalizedTarget),
        targetType = targetType,
        targetId = normalizedTarget,
        action = action,
        templateIds = binding.templateIds.filter { it.isNotBlank() }.toSet(),
        types = binding.types.filter { it.isNotBlank() }.toSet(),
        textKeywords = binding.textKeywords.trim()
    )
}

internal fun findMessageBlockBinding(
    bindings: List<MessageBlockBinding>,
    targetType: String,
    targetId: String
): MessageBlockBinding? {
    val normalizedType = if (targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        MessageBlockSettings.TARGET_GROUP_MEMBER
    } else {
        MessageBlockSettings.TARGET_CONTACT
    }
    val normalizedTarget = if (normalizedType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        normalizeGroupMemberEntry(targetId) ?: targetId.trim()
    } else {
        targetId.trim()
    }
    return bindings.firstOrNull {
        it.targetType == normalizedType && it.targetId == normalizedTarget
    }
}

internal fun upsertMessageBlockBindings(
    current: List<MessageBlockBinding>,
    additions: List<MessageBlockBinding>
): List<MessageBlockBinding> {
    val result = linkedMapOf<String, MessageBlockBinding>()
    current.forEach { binding ->
        val normalized = normalizedMessageBlockBinding(binding)
        result[MessageBlockSettings.bindingKey(normalized.targetType, normalized.targetId)] = normalized
    }
    additions.forEach { binding ->
        val normalized = normalizedMessageBlockBinding(binding)
        val key = MessageBlockSettings.bindingKey(normalized.targetType, normalized.targetId)
        val old = result[key]
        result[key] = if (old == null) {
            normalized
        } else {
            normalized.copy(
                id = old.id,
                enabled = old.enabled,
                action = old.action,
                templateIds = old.templateIds + normalized.templateIds,
                customRules = old.customRules,
                typeAll = old.typeAll,
                types = old.types,
                textKeywords = old.textKeywords
            )
        }
    }
    return result.values.toList()
}

internal fun messageBlockTypeOptions(): List<MessageBlockTypeOption> {
    return listOf(
        MessageBlockTypeOption(MessageBlockSettings.TYPE_TEXT, "文字", "普通文本消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_QUOTE, "引用消息", "引用文字、图片和其它消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_IMAGE, "图片", "图片消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_VIDEO, "小视频", "视频和短视频消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_VOICE, "语音", "语音消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_LINK, "文章、链接", "文章、链接、文件和接龙类 AppMsg"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_MUSIC, "音乐", "音乐类 AppMsg"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_MINI_PROGRAM, "小程序", "小程序卡片"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_CARD, "名片", "联系人名片"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_EMOJI, "动画表情", "表情消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_RED_PACKET, "红包", "红包消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_TRANSFER, "转账", "转账消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_VOIP, "视频/语音聊天", "通话邀请和记录"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_LOCATION, "地图位置", "位置分享消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_SYSTEM, "系统消息", "普通系统提示"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_PAT, "拍一拍", "拍一拍系统消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_VIDEO_NUMBER, "视频号链接", "视频号分享卡片"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_UNKNOWN, "未知类型", "微信新增或暂未识别的消息类型")
    )
}

internal fun loadMessageBlockContacts(
    context: Context,
    callback: (MessageBlockContactData, Throwable?) -> Unit
) {
    val api = WeChatApis.contact().contacts()
    if (api == null || !api.isAvailable) {
        callback(MessageBlockContactData(), IllegalStateException("联系人列表不可用"))
        return
    }
    val main = Handler(Looper.getMainLooper())
    Thread({
        try {
            val labelsByUser = linkedMapOf<String, MutableList<String>>()
            val labels = runCatching { api.getContactLabelList() }
                .getOrDefault(emptyList())
                .mapNotNull { label ->
                    val labelId = label.labelId.trim()
                    val labelName = label.labelName.trim().ifBlank { labelId }
                    if (labelId.isBlank() && labelName.isBlank()) return@mapNotNull null
                    val ids = label.userNameList.ifEmpty {
                        if (labelId.isBlank()) emptyList() else runCatching { api.getContactByLabelId(labelId) }.getOrDefault(emptyList())
                    }.map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    ids.forEach { wxId ->
                        labelsByUser.getOrPut(wxId) { arrayListOf() }.add(labelName)
                    }
                    MessageBlockLabelOption(
                        id = labelId.ifBlank { labelName },
                        name = labelName,
                        contactIds = ids
                    )
                }
                .sortedBy { it.name.lowercase(Locale.US) }

            val byId = linkedMapOf<String, MessageBlockContactOption>()
            fun putContact(contact: WeChatContact?, kind: MessageBlockContactKind, group: Boolean) {
                val option = contact.toOption(group) ?: return
                byId[option.id] = MessageBlockContactOption(
                    contact = option,
                    kind = kind,
                    labelNames = labelsByUser[option.id].orEmpty().distinct()
                )
            }

            api.getPickerContacts().forEach { putContact(it, MessageBlockContactKind.FRIEND, false) }
            api.getPickerGroups().forEach { putContact(it, MessageBlockContactKind.GROUP, true) }
            api.getPickerOfficialAccounts().forEach { putContact(it, MessageBlockContactKind.OFFICIAL, false) }

            val missingLabelIds = labels.flatMap { it.contactIds }
                .distinct()
                .filterNot { byId.containsKey(it) }
            if (missingLabelIds.isNotEmpty()) {
                api.getContactsByIds(missingLabelIds).forEach { contact ->
                    val kind = when {
                        contact.isGroup() -> MessageBlockContactKind.GROUP
                        contact.isOfficialAccount() -> MessageBlockContactKind.OFFICIAL
                        else -> MessageBlockContactKind.FRIEND
                    }
                    putContact(contact, kind, contact.isGroup())
                }
            }

            val contacts = byId.values.sortedWith(
                compareBy<MessageBlockContactOption> { it.kind.ordinal }
                    .thenBy { it.contact.label.lowercase(Locale.US) }
            )
            main.post { callback(MessageBlockContactData(contacts, labels), null) }
        } catch (throwable: Throwable) {
            main.post { callback(MessageBlockContactData(), throwable) }
        }
    }, "HchatMessageBlockContacts").start()
}

internal fun loadContacts(
    context: Context,
    mode: ContactPickerMode,
    includeLabels: Boolean = false,
    callback: (List<ContactOption>, Throwable?) -> Unit
) {
    val api = WeChatApis.contact().contacts()
    if (api == null || !api.isAvailable) {
        callback(emptyList(), IllegalStateException("联系人列表不可用"))
        return
    }
    val main = Handler(Looper.getMainLooper())
    Thread({
        try {
            val rows = mutableListOf<ContactOption>()
            val labelsByUser = linkedMapOf<String, MutableList<String>>()
            if (includeLabels) {
                runCatching { api.getContactLabelList() }.getOrDefault(emptyList()).forEach { label ->
                    val labelName = label.labelName.ifBlank { label.labelId }
                    if (labelName.isBlank()) return@forEach
                    label.userNameList.forEach { wxId ->
                        if (wxId.isNotBlank()) {
                            labelsByUser.getOrPut(wxId) { arrayListOf() }.add(labelName)
                        }
                    }
                }
            }
            if (mode == ContactPickerMode.FRIENDS || mode == ContactPickerMode.BOTH || mode == ContactPickerMode.ALL_CHATS) {
                rows += api.getPickerContacts().mapNotNull { it.toOption(false, labelsByUser[it.wxId].orEmpty()) }
            }
            if (mode == ContactPickerMode.GROUPS || mode == ContactPickerMode.BOTH || mode == ContactPickerMode.ALL_CHATS) {
                rows += api.getPickerGroups().mapNotNull { it.toOption(true) }
            }
            if (mode == ContactPickerMode.ALL_CHATS) {
                rows += api.getPickerOfficialAccounts().mapNotNull { it.toOption(false, official = true) }
            }
            val conversationOrder = WeChatApis.conversations()
                ?.getRecentConversationUsernames(10000)
                .orEmpty()
                .mapIndexed { index, username -> username to index }
                .toMap()
            val sorted = rows.distinctBy { it.id }.sortedWith(
                compareBy<ContactOption> { conversationOrder[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.group }
                    .thenBy { it.official }
                    .thenBy { it.label.lowercase(Locale.US) }
            )
            main.post { callback(sorted, null) }
        } catch (throwable: Throwable) {
            main.post { callback(emptyList(), throwable) }
        }
    }, "HchatContactPicker").start()
}

internal fun loadGroupMembers(
    context: Context,
    group: ContactOption,
    callback: (List<ContactOption>, Throwable?) -> Unit
) {
    val api = WeChatApis.contact().contacts()
    if (api == null || !api.isAvailable) {
        callback(emptyList(), IllegalStateException("群成员列表不可用"))
        return
    }
    val main = Handler(Looper.getMainLooper())
    Thread({
        try {
            val roomNames = api.getGroupMemberRoomDisplayNames(group.id)
            val rows = api.getGroupMembers(group.id).mapNotNull { contact ->
                val id = contact.wxId.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ContactOption(
                    id = id,
                    label = roomNames[id]?.takeIf { it.isNotBlank() }
                        ?: contact.displayName().ifEmpty { id },
                    group = false,
                    avatarUrl = contact.avatarUrl,
                    avatarBackupUrl = contact.avatarBackupUrl,
                    searchAliases = listOf(contact.remarkName, contact.nickname, contact.customWxId)
                        .filter { it.isNotBlank() }
                        .distinct()
                )
            }.sortedBy { it.label.lowercase(Locale.US) }
            main.post { callback(rows, null) }
        } catch (throwable: Throwable) {
            main.post { callback(emptyList(), throwable) }
        }
    }, "HchatGroupMemberPicker").start()
}

internal fun WeChatContact?.toOption(
    group: Boolean,
    labels: List<String> = emptyList(),
    official: Boolean = false
): ContactOption? {
    if (this == null || wxId.isNullOrEmpty()) return null
    return ContactOption(
        id = wxId,
        label = pickerDisplayName(group),
        group = group,
        avatarUrl = avatarUrl,
        avatarBackupUrl = avatarBackupUrl,
        labels = labels.distinct(),
        official = official,
        searchAliases = listOf(remarkName, nickname, customWxId)
            .filter { it.isNotBlank() }
            .distinct()
    )
}

internal fun parseIds(value: String): Set<String> {
    return value.split('|').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

internal fun formatIds(ids: Iterable<String>): String {
    return ids
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("|")
}

internal fun parseFavoriteIds(value: String): Set<String> {
    return value.replace(";;;", "|")
        .split("|", ",", "，", "\n", "\r")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

internal fun formatFavoriteIds(ids: Iterable<String>, delimiter: String, multiSelect: Boolean): String {
    val values = ids
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (values.isEmpty()) return ""
    return if (multiSelect) {
        values.joinToString(delimiter)
    } else {
        values.first()
    }
}

internal fun favoriteSummary(value: String): String {
    val count = parseFavoriteIds(value).size
    return if (count == 0) "未选择收藏" else "已选择 $count 个收藏"
}

internal fun <T> List<T>.selectedFirst(isSelected: (T) -> Boolean): List<T> {
    if (size < 2) return this
    val selected = ArrayList<T>()
    val unselected = ArrayList<T>()
    for (value in this) {
        if (isSelected(value)) selected.add(value) else unselected.add(value)
    }
    if (selected.isEmpty() || unselected.isEmpty()) return this
    return selected.apply { addAll(unselected) }
}

internal fun groupMemberEntry(groupId: String, memberId: String): String {
    return "${groupId.trim()}/${memberId.trim()}"
}

internal fun parseGroupMemberEntries(value: String): Set<String> {
    return value.split("|", ",", "，", "\n", "\r")
        .mapNotNull { normalizeGroupMemberEntry(it) }
        .toSet()
}

internal fun formatGroupMemberEntries(entries: Iterable<String>): String {
    return entries
        .mapNotNull { normalizeGroupMemberEntry(it) }
        .distinct()
        .joinToString("|")
}

private fun normalizeGroupMemberEntry(value: String): String? {
    val token = value.trim()
    if (token.isEmpty()) return null
    val separators = charArrayOf('/', '#', ':', '：')
    val index = separators.map { token.indexOf(it) }
        .filter { it > 0 }
        .minOrNull()
        ?: return null
    val groupId = token.substring(0, index).trim()
    val memberId = token.substring(index + 1).trim()
    if (groupId.isEmpty() || memberId.isEmpty()) return null
    return groupMemberEntry(groupId, memberId)
}

suspend fun loadAvatarBitmap(wxId: String, primary: String, backup: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val sources = avatarSources(wxId, primary, backup)
    if (sources.isEmpty()) {
        null
    } else {
        val cacheKey = sources.joinToString("|")
        val cached = AvatarMemoryCache.cached(cacheKey)
        if (cached.first) {
            cached.second
        } else {
            var bitmap: ImageBitmap? = null
            for (source in sources) {
                bitmap = loadAvatarSource(source)?.asImageBitmap()
                if (bitmap != null) break
            }
            bitmap?.also { AvatarMemoryCache.put(cacheKey, it) }
        }
    }
}

private fun avatarSources(wxId: String, primary: String, backup: String): List<String> {
    val result = LinkedHashSet<String>()
    if (primary.isNotBlank()) result.add(primary)
    if (backup.isNotBlank()) result.add(backup)
    val local = avatarLocalPath(wxId, hd = false)
    if (!local.isNullOrBlank()) result.add(local)
    val hdLocal = avatarLocalPath(wxId, hd = true)
    if (!hdLocal.isNullOrBlank()) result.add(hdLocal)
    return result.toList()
}

private fun avatarLocalPath(wxId: String, hd: Boolean): String? {
    if (wxId.isBlank()) return null
    val root = WeChatAvatarPathCache.resolveAvatarRoot() ?: return null
    val hash = md5Lower(wxId) ?: return null
    return buildString(root.length + 64) {
        append(root.trimEnd('/'))
        append('/')
        append(hash.substring(0, 2))
        append('/')
        append(hash.substring(2, 4))
        append("/user_")
        if (hd) append("hd_")
        append(hash)
        append(".png")
    }
}

private fun md5Lower(value: String): String? {
    return try {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        buildString(digest.size * 2) {
            for (b in digest) {
                append(((b.toInt() ushr 4) and 0xF).toString(16))
                append((b.toInt() and 0xF).toString(16))
            }
        }
    } catch (_: Throwable) {
        null
    }
}

private fun loadAvatarSource(value: String): android.graphics.Bitmap? {
    return try {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            val connection = URL(value).openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.getInputStream().use { BitmapFactory.decodeStream(it) }
        } else {
            val file = File(value)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    } catch (_: Throwable) {
        null
    }
}

internal fun isDarkMode(context: Context): Boolean {
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return night == Configuration.UI_MODE_NIGHT_YES
}

internal const val FAVORITE_BACKGROUND_BATCH_DELAY_MS = 40L
