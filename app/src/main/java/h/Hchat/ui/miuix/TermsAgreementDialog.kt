package h.Hchat.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import h.Hchat.preferences.TermsGate
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.delay

@Composable
fun FirstUseAgreementDialog(
    context: Context,
    onCancel: () -> Unit,
    onAccepted: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var remainingSeconds by remember { mutableStateOf(30) }
    val acceptedText = TermsGate.AGREEMENT_TEXT
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        }
    }
    val agreement = remember {
        """
        Hchat 使用协议与免责声明

        1. 本模块为免费模块，仅供个人学习、研究、测试、逆向分析和备份用途使用，不提供任何商业授权、售后承诺或稳定性保证。
        2. 本模块与微信、腾讯及其关联主体无关，不代表官方立场，也不是官方客户端、官方插件或官方服务的一部分。
        3. 禁止倒卖、付费分发、捆绑销售、二次打包收费、引流售卖、以捐赠名义收费，禁止冒充作者、官方渠道或授权代理发布。
        4. 请勿在国内公开平台、群组、论坛、短视频平台、网盘分享页、应用市场或其它公开渠道传播、推广、引流、售卖或组织分发本模块。
        5. 本模块可能会修改微信运行时行为，使用后可能出现功能异常、消息异常、账号风控、限制登录、数据异常、闪退、掉线、模块冲突或其它不可预期问题。
        6. 使用者应自行确认所在地法律法规、平台协议、设备环境和账号风险；因安装、使用、传播、修改、二次分发或与其它模块共存产生的任何后果均由使用者自行承担。
        7. 禁止将本模块用于骚扰、欺诈、刷量、营销轰炸、盗取信息、破坏服务稳定性、绕过平台风控、侵犯他人权益或其它违法违规用途。
        8. 本模块不保证适配所有微信版本、系统版本、设备环境、热更新状态和其它模块共存环境，也不承诺持续维护、及时修复或提供任何形式的服务保障。
        9. 如果你不同意以上任一条款，请点击取消并停止使用本模块。

        如果你理解并接受以上内容，请在下方输入“$acceptedText”后继续使用。
        """.trimIndent()
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val dialogMaxHeight = (screenHeight * 0.78f).coerceAtMost(640.dp)
    val agreementMaxHeight = (screenHeight * 0.42f).coerceIn(220.dp, 430.dp)
    WindowDialog(
        show = true,
        title = "使用协议",
        onDismissRequest = {},
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = agreementMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = agreement,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
                Text(
                    text = "请输入“$acceptedText”确认",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 14.dp)
                )
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.secondaryVariant)
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = if (remainingSeconds > 0) "继续（${remainingSeconds}s）" else "同意并继续",
                        onClick = {
                            if (remainingSeconds > 0) {
                                Toast.makeText(context, "请等待 ${remainingSeconds} 秒后继续", Toast.LENGTH_SHORT).show()
                            } else if (input.trim() == acceptedText) {
                                if (TermsGate.accept(context)) {
                                    onAccepted()
                                } else {
                                    Toast.makeText(context, "协议状态保存失败，请重试", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "请输入“$acceptedText”后继续", Toast.LENGTH_SHORT).show()
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
