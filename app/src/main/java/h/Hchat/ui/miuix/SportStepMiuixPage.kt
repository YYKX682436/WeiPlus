package h.Hchat.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import h.Hchat.hooks.items.sportstep.StepMultiplierPolicy
import h.Hchat.hooks.items.sportstep.StepRequestState
import h.Hchat.hooks.items.sportstep.SportStepFeature
import h.Hchat.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun SportStepMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("尚未提交") }
    val runtime = SportStepFeature.active
    DisposableEffect(runtime) {
        val observer: (StepRequestState.Status) -> Unit = { result ->
            status = when (result) {
                StepRequestState.Status.IDLE -> "尚未提交"
                StepRequestState.Status.SUBMITTING -> "正在提交"
                StepRequestState.Status.ENQUEUED -> "请求已入队，等待网络结果"
                StepRequestState.Status.NETWORK_SUCCESS -> "网络处理成功；微信运动实际生效情况需查看排行榜"
                StepRequestState.Status.NETWORK_FAILURE -> "网络处理失败，微信运动实际生效情况未确认"
                StepRequestState.Status.ENQUEUE_FAILURE -> "请求未入队"
                StepRequestState.Status.TIMEOUT -> "等待网络结果超时，微信运动实际生效情况未确认"
            }
        }
        runtime?.listen(observer)
        onDispose { runtime?.stopListening(observer) }
    }
    PageScaffold(title = provider.title(), largeTitle = provider.title(), scrollBehavior = scrollBehavior,
        onBack = onBack,
        showBackIcon = false) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState, contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            )) {
            item { SmallTitle(text = "手动提交") }
            item {
                SettingsCard {
                    InputRow("目标步数", "请输入 1 至 2147483647 的整数", input) { input = it }
                    TextButton(text = "提交", onClick = {
                        val step = StepMultiplierPolicy.parsePositiveInt(input)
                        when {
                            step <= 0 -> status = "输入无效：请输入范围内的正整数"
                            runtime == null -> status = "功能尚未就绪，未提交"
                            else -> runtime.submit(step)?.let { status = it }
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary())
                    Text(text = "操作状态：$status", modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                }
            }
        }
    }
}
