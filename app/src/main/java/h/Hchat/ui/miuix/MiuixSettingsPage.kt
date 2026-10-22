package h.Hchat.ui.miuix

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import h.Hchat.BuildConfig
import h.Hchat.R
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutSettings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.items.audiotransform.AudioTransformSettings
import h.Hchat.hooks.items.antirecall.AntiRecallSettings
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.autooriginal.AutoOriginalImageSettings
import h.Hchat.hooks.items.autovieworiginal.AutoViewOriginalSettings
import h.Hchat.hooks.items.atallnotify.AtAllNotificationBlockSettings
import h.Hchat.hooks.items.callmedialimit.CallMediaLimitSettings
import h.Hchat.hooks.items.chattime.ChatTimeStyleSettings
import h.Hchat.hooks.items.conversationgroup.ConversationGroupStore
import h.Hchat.hooks.items.conversationtabs.ConversationTabsStore
import h.Hchat.hooks.items.customfriendavatar.CustomFriendAvatarSettings
import h.Hchat.hooks.items.customnotify.CustomNotificationSettings
import h.Hchat.hooks.items.editmsg.EditMessageSettings
import h.Hchat.hooks.items.emojisave.EmojiSaveSettings
import h.Hchat.hooks.items.fakelocation.FakeLocationSettings
import h.Hchat.hooks.items.fakescancamera.FakeScanCameraSettings
import h.Hchat.hooks.items.fakevoiceduration.FakeVoiceDurationSettings
import h.Hchat.hooks.items.groupleave.GroupLeaveMonitorSettings
import h.Hchat.hooks.items.groupnicknamecolor.GroupNicknameColorSettings
import h.Hchat.hooks.items.grouprename.GroupRenameMonitorSettings
import h.Hchat.hooks.items.hideavatar.HideChatAvatarSettings
import h.Hchat.hooks.items.hidemenu.HideChatMenuSettings
import h.Hchat.hooks.items.hotupdate.DisableHotUpdateSettings
import h.Hchat.hooks.items.inputhint.InputHintSettings
import h.Hchat.hooks.items.backgroundassist.BackgroundRunAssistSettings
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationSettings
import h.Hchat.hooks.items.zombiecheck.ZombieCheckSettings
import h.Hchat.hooks.items.membertitle.MemberTitleSettings
import h.Hchat.hooks.items.messageaffix.MessageAffixSettings
import h.Hchat.hooks.items.messagebubble.MessageBubbleSettings
import h.Hchat.hooks.items.messagetextcolor.MessageTextColorSettings
import h.Hchat.hooks.items.messageblock.MessageBlockSettings
import h.Hchat.hooks.items.moments.MomentsAutoCommentSettings
import h.Hchat.hooks.items.moments.MomentsAutoLikeSettings
import h.Hchat.hooks.items.moments.MomentsAutoForwardSettings
import h.Hchat.hooks.items.moments.MomentsAutoRefreshSettings
import h.Hchat.hooks.items.moments.MomentsBottomDetailSettings
import h.Hchat.hooks.items.moments.MomentsContactFilterSettings
import h.Hchat.hooks.items.moments.MomentsPostNotificationSettings
import h.Hchat.hooks.items.moments.RemoveMomentsAdsSettings
import h.Hchat.hooks.items.momentsfake.MomentsFakeInteractionSettings
import h.Hchat.hooks.items.multirecall.MultiRecallSettings
import h.Hchat.hooks.items.payment.core.RedPacketSettings
import h.Hchat.hooks.items.payment.fakebalance.FakeWalletBalanceSettings
import h.Hchat.hooks.items.payment.transfer.AutoTransferSettings
import h.Hchat.hooks.items.profileid.ProfileIdSettings
import h.Hchat.hooks.items.protobuf.ProtobufPacketSettings
import h.Hchat.hooks.items.quoteclear.QuoteDeleteClearSettings
import h.Hchat.hooks.items.quickcontactedit.QuickContactEditSettings
import h.Hchat.hooks.items.quickread.QuickMarkReadSettings
import h.Hchat.hooks.items.realtail.RealNameTailSettings
import h.Hchat.hooks.items.roundavatar.RoundAvatarSettings
import h.Hchat.hooks.items.settings.SettingsEntrySettings
import h.Hchat.hooks.items.shortvideo.FinderMediaDownloadSettings
import h.Hchat.hooks.items.statuslimit.StatusTextLimitSettings
import h.Hchat.hooks.items.swipequote.SwipeQuoteSettings
import h.Hchat.hooks.items.tablet.WeChatTabletSettings
import h.Hchat.hooks.items.textspeech.TextSpeechSettings
import h.Hchat.hooks.items.textvoice.TextVoiceSettings
import h.Hchat.hooks.items.voiceforward.VoiceForwardSettings
import h.Hchat.hooks.items.hchatextra.HchatExtraSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.preferences.TermsGate
import h.Hchat.ui.UIRegistry
import h.Hchat.utils.KavaReflector
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale

internal const val UI_PREFS_NAME = "Hchat_miuix_ui"
internal const val KEY_FLOATING_NAV = "floating_nav"
internal const val KEY_GLASS_NAV = "glass_nav"
internal const val KEY_SEARCH_HISTORY = "search_history"
internal const val SEARCH_HISTORY_LIMIT = 16
private const val PRESS_RELEASE_DELAY_MS = 110L
private const val RINGTONE_SYSTEM_REQUEST_CODE = 0x48435254
private const val RINGTONE_FILE_REQUEST_CODE = 0x48435255
private const val REDPACKET_REPLY_FILE_REQUEST_CODE = 0x48435256
private const val AUTO_REPLY_FILE_REQUEST_CODE = 0x48435257
private const val CONFIG_EXPORT_REQUEST_CODE = 0x48435258
private const val CONFIG_IMPORT_REQUEST_CODE = 0x48435259
private const val SCHEDULED_TASK_FILE_REQUEST_CODE = 0x4843525A
private const val AUDIO_TRANSFORM_INPUT_REQUEST_CODE = 0x4843525B
private const val AUDIO_TRANSFORM_OUTPUT_REQUEST_CODE = 0x4843525C
private const val FAKE_LOCATION_WECHAT_PICKER_REQUEST_CODE = 0x4843525D
private const val MARKDOWN_LINK_TAG = "md_link"
private val MARKDOWN_LINK_REGEX = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
internal val NAVIGATION_BUTTON_MIN_INSET = 24.dp
internal val NAVIGATION_BUTTON_EXTRA_GAP = 8.dp
internal const val REDPACKET_AT_SENDER_VARIABLE = "{@发红包的人}"

internal fun isLiquidGlassSupported(): Boolean = Build.VERSION.SDK_INT >= 33
internal val REDPACKET_AT_SENDER_VARIABLES = listOf(
    REDPACKET_AT_SENDER_VARIABLE,
    "{@sender}",
    "{@成员}"
)

internal val redPacketTemplateVariables = listOf(
    TemplateVariable("{amount}", "金额"),
    TemplateVariable("{talker}", "会话"),
    TemplateVariable("{sender}", "成员"),
    TemplateVariable("{time}", "时间")
)
internal val redPacketReplyTemplateVariables = redPacketTemplateVariables + TemplateVariable(
    REDPACKET_AT_SENDER_VARIABLE,
    "@发红包人"
)
internal val transferTemplateVariables = listOf(
    TemplateVariable("{amount}", "金额"),
    TemplateVariable("{talker}", "会话"),
    TemplateVariable("{sender}", "转账人"),
    TemplateVariable("{@转账的人}", "@转账人"),
    TemplateVariable("{time}", "时间")
)

internal val antiRecallTemplateVariables = listOf(
    TemplateVariable(AntiRecallSettings.VAR_RECALLER_NAME, "撤回者"),
    TemplateVariable(AntiRecallSettings.VAR_RECALL_TEXT, "文字内容"),
    TemplateVariable(AntiRecallSettings.VAR_SEND_TIME, "发送时间"),
    TemplateVariable(AntiRecallSettings.VAR_RECALL_TIME, "撤回时间")
)
internal val messageAffixTemplateVariables = listOf(
    TemplateVariable(MessageAffixSettings.VAR_SEND_TEXT, "原消息"),
    TemplateVariable(MessageAffixSettings.VAR_LINE, "换行"),
    TemplateVariable(MessageAffixSettings.VAR_SEND_TIME, "发送时间"),
    TemplateVariable(MessageAffixSettings.VAR_SEND_DURATION, "发送耗时"),
    TemplateVariable(MessageAffixSettings.VAR_TOTAL_MESSAGES, "发送总数"),
    TemplateVariable(MessageAffixSettings.VAR_TEXT_MESSAGES, "文字消息数"),
    TemplateVariable(MessageAffixSettings.VAR_TEXT_CHARACTERS, "文字字数"),
    TemplateVariable(MessageAffixSettings.VAR_EMOJI_MESSAGES, "表情消息数"),
    TemplateVariable(MessageAffixSettings.VAR_TRANSFER_MESSAGES, "转账消息数"),
    TemplateVariable(MessageAffixSettings.VAR_RED_PACKET_MESSAGES, "红包消息数"),
    TemplateVariable(MessageAffixSettings.VAR_FILE_MESSAGES, "文件消息数")
)
internal val inputHintTemplateVariables = listOf(
    TemplateVariable(InputHintSettings.VAR_TOTAL_MESSAGES, "发送总数"),
    TemplateVariable(InputHintSettings.VAR_TEXT_MESSAGES, "文字消息数"),
    TemplateVariable(InputHintSettings.VAR_TEXT_CHARACTERS, "文字字数"),
    TemplateVariable(InputHintSettings.VAR_EMOJI_MESSAGES, "表情消息数"),
    TemplateVariable(InputHintSettings.VAR_TRANSFER_MESSAGES, "转账消息数"),
    TemplateVariable(InputHintSettings.VAR_RED_PACKET_MESSAGES, "红包消息数"),
    TemplateVariable(InputHintSettings.VAR_FILE_MESSAGES, "文件消息数")
)
internal val keywordNotificationTemplateVariables = listOf(
    TemplateVariable("%keyword%", "关键词"),
    TemplateVariable("%sender%", "发送者"),
    TemplateVariable("%wxid%", "发送者ID"),
    TemplateVariable("%content%", "内容"),
    TemplateVariable("%type%", "消息类型")
)
internal val momentsPostNotificationTemplateVariables = listOf(
    TemplateVariable("%sender%", "发布者"),
    TemplateVariable("%wxid%", "发布者ID"),
    TemplateVariable("%type%", "类型"),
    TemplateVariable("%content%", "内容"),
    TemplateVariable("%snsid%", "朋友圈ID")
)
internal val momentsAutoCommentTemplateVariables = listOf(
    TemplateVariable(MomentsAutoCommentSettings.VAR_TIME, "时间")
)
internal val textSpeechTemplateVariables = listOf(
    TemplateVariable(TextSpeechSettings.VAR_SENDER_NICKNAME, "发送者昵称"),
    TemplateVariable(TextSpeechSettings.VAR_WECHAT_ID, "发送者微信号"),
    TemplateVariable(TextSpeechSettings.VAR_REMARK_NAME, "备注"),
    TemplateVariable(TextSpeechSettings.VAR_GROUP_NICKNAME, "群内昵称"),
    TemplateVariable(TextSpeechSettings.VAR_GROUP_NAME, "群聊名称"),
    TemplateVariable(TextSpeechSettings.VAR_CONVERSATION_NAME, "会话名称"),
    TemplateVariable(TextSpeechSettings.VAR_ANNOUNCEMENT_SOURCE, "播报来源"),
    TemplateVariable(TextSpeechSettings.VAR_MESSAGE_CONTENT, "消息正文"),
    TemplateVariable(TextSpeechSettings.VAR_MESSAGE_TYPE, "消息类型"),
    TemplateVariable(TextSpeechSettings.VAR_VOICE_DURATION, "语音时长"),
    TemplateVariable(TextSpeechSettings.VAR_MESSAGE_TIME, "消息时间"),
    TemplateVariable(TextSpeechSettings.VAR_SENDER_ID, "发送者ID"),
    TemplateVariable(TextSpeechSettings.VAR_CONVERSATION_ID, "会话ID")
)
internal val momentsBottomDetailTemplateVariables = listOf(
    TemplateVariable(MomentsBottomDetailSettings.VAR_ORIGINAL_TEXT, "微信原时间"),
    TemplateVariable(MomentsBottomDetailSettings.VAR_TIME, "自定义时间"),
    TemplateVariable(MomentsBottomDetailSettings.VAR_TYPE, "朋友圈类型"),
    TemplateVariable(MomentsBottomDetailSettings.VAR_SNS_ID, "朋友圈ID"),
    TemplateVariable(MomentsBottomDetailSettings.VAR_USER_NAME, "发布者ID")
)
internal val groupMemberTemplateVariables = listOf(
    TemplateVariable("%userName%", "微信昵称"),
    TemplateVariable("%groupNickname%", "群内昵称"),
    TemplateVariable("%userWxid%", "Wxid"),
    TemplateVariable("%realNameTail%", "实名尾字"),
    TemplateVariable("%gender%", "性别"),
    TemplateVariable("%region%", "地区"),
    TemplateVariable("%groupName%", "群名"),
    TemplateVariable("%time%", "时间"),
    TemplateVariable("[AtWx=%userWxid%]", "@成员"),
    TemplateVariable("[AtWx=]", "@其他人")
)
internal val groupRenameTemplateVariables = listOf(
    TemplateVariable("%userName%", "微信昵称"),
    TemplateVariable("%oldGroupNickname%", "旧群内昵称"),
    TemplateVariable("%newGroupNickname%", "新群内昵称"),
    TemplateVariable("%userWxid%", "Wxid"),
    TemplateVariable("%realNameTail%", "实名尾字"),
    TemplateVariable("%gender%", "性别"),
    TemplateVariable("%region%", "地区"),
    TemplateVariable("%groupName%", "群名"),
    TemplateVariable("%time%", "时间"),
    TemplateVariable("[AtWx=%userWxid%]", "@成员"),
    TemplateVariable("[AtWx=]", "@其他人")
)
internal val groupRenameNoticeVariables = listOf(
    TemplateVariable("%oldGroupNickname%", "旧群内昵称"),
    TemplateVariable("%newGroupNickname%", "新群内昵称"),
    TemplateVariable("%userName%", "微信昵称"),
    TemplateVariable("%userWxid%", "可点击Wxid"),
    TemplateVariable("%realNameTail%", "真实姓名尾巴"),
    TemplateVariable("%gender%", "性别"),
    TemplateVariable("%region%", "地区"),
    TemplateVariable("%groupName%", "群名"),
    TemplateVariable("%time%", "时间")
)
internal val groupLeaveNoticeVariables = listOf(
    TemplateVariable("%displayName%", "完整显示名"),
    TemplateVariable("%groupNickname%", "群内昵称"),
    TemplateVariable("%userName%", "微信昵称"),
    TemplateVariable("%remarkName%", "备注"),
    TemplateVariable("%userWxid%", "可点击Wxid"),
    TemplateVariable("%groupName%", "群名"),
    TemplateVariable("%time%", "时间")
)
internal val groupInviteNoticeVariables = listOf(
    TemplateVariable("%inviterName%", "邀请者名称"),
    TemplateVariable("%inviterGroupNickname%", "邀请者群昵称"),
    TemplateVariable("%inviterWxid%", "邀请者可点击Wxid"),
    TemplateVariable("%inviteeName%", "被邀请者名称"),
    TemplateVariable("%inviteeGroupNickname%", "被邀请者群昵称"),
    TemplateVariable("%inviteeWxid%", "被邀请者可点击Wxid"),
    TemplateVariable("%inviteCount%", "累计邀请次数"),
    TemplateVariable("%groupName%", "群名"),
    TemplateVariable("%time%", "时间")
)
internal val autoReplyTemplateVariables = listOf(
    TemplateVariable("%friendName%", "好友/成员名"),
    TemplateVariable("%senderName%", "发送者"),
    TemplateVariable("%senderWxid%", "发送者ID"),
    TemplateVariable("%talker%", "会话ID"),
    TemplateVariable("%groupName%", "群名"),
    TemplateVariable("%content%", "原消息"),
    TemplateVariable("%atSender%", "@发送者"),
    TemplateVariable("%atAll%", "@所有人")
)
internal val scheduledTaskTemplateVariables = listOf(
    TemplateVariable("%friendName%", "好友/成员名")
)

internal val groupRandomJoinTexts = listOf(
    "[AtWx=%userWxid%] 欢迎 %userName% 加入 %groupName%",
    "欢迎新朋友 %userName% 加入，大家请多关照",
    "欢迎 %userName%，记得看群公告",
    "新成员 %userName% 已加入群聊",
    "欢迎 %userName%，愿在 %groupName% 玩得开心"
)

internal val groupRandomLeftTexts = listOf(
    "有缘再会，祝 %userName% 一切顺利",
    "%userName% 已离开群聊，愿一切安好",
    "青山不改，绿水长流，后会有期",
    "我们会想念你的，%userName%",
    "%userName% 已退群，感谢曾经同行"
)

internal val groupRandomJoinCardTitles = listOf(
    "欢迎：%userName%",
    "群聊因你而精彩",
    "新成员到来：%userName%"
)

internal val groupRandomJoinCardDescs = listOf(
    "常来聊天",
    "群名称：%groupName%\n名片：%groupNickname%\n进群时间：%time%",
    "快来和大家一起玩\nID：%userWxid%"
)

internal val groupRandomLeftCardTitles = listOf(
    "成员离群通知",
    "%userName% 已离开",
    "祝你一切顺利"
)

internal val groupRandomLeftCardDescs = listOf(
    "我们有缘再见",
    "群名称：%groupName%\n名片：%groupNickname%\n离群时间：%time%",
    "相逢是缘，祝君安好"
)

internal object NavIcons {
    val Back: ImageVector = navIcon(
        name = "Rounded.ArrowBack",
        path = "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"
    )
    val Attach: ImageVector = navIcon(
        name = "Rounded.AttachFile",
        path = "M16.5,6.5v11c0,2.21 -1.79,4 -4,4s-4,-1.79 -4,-4V5c0,-1.38 1.12,-2.5 2.5,-2.5s2.5,1.12 2.5,2.5v10.5c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V6.5H10v9c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5V5c0,-2.21 -1.79,-4 -4,-4S7,2.79 7,5v12.5c0,3.04 2.46,5.5 5.5,5.5s5.5,-2.46 5.5,-5.5v-11h-1.5z"
    )
    val Add: ImageVector = navIcon(
        name = "Rounded.Add",
        path = "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
    )
    val Compact: ImageVector = navIcon(
        name = "Rounded.Summarize",
        path = "M14,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8l-6,-6z M13,9V3.5L18.5,9H13z M8,13h8v2H8v-2z M8,17h8v2H8v-2z M8,9h3v2H8V9z"
    )
    val Close: ImageVector = navIcon(
        name = "Rounded.Close",
        path = "M18.3,5.71L12,12l6.3,6.29 -1.41,1.42L10.59,13.41 4.29,19.71 2.88,18.29 9.17,12 2.88,5.71 4.29,4.29 10.59,10.59 16.89,4.29z"
    )
    val History: ImageVector = navIcon(
        name = "Rounded.History",
        path = "M13,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c4.63,0 8.44,-3.5 8.94,-8h-2.02c-0.49,3.39 -3.4,6 -6.92,6 -3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7c1.93,0 3.68,0.79 4.95,2.05L15,10h6V4l-2.63,2.63C16.73,4.42 14.95,3 13,3z M12,7v6l5,3 1,-1.64 -4,-2.36V7h-2z"
    )
    val Bell: ImageVector = navIcon(
        name = "Rounded.Notifications",
        path = "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2z M18,16v-5c0,-3.07 -1.63,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5S10.5,3.17 10.5,4v0.68C7.64,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"
    )
    val Modules: ImageVector = navIcon(
        name = "Rounded.Extension",
        path = "M20.5,11H19V7c0,-1.1 -0.9,-2 -2,-2h-4V3.5C13,2.12 11.88,1 10.5,1S8,2.12 8,3.5V5H4c-1.1,0 -1.99,0.9 -1.99,2v3.8H3.5c1.49,0 2.7,1.21 2.7,2.7s-1.21,2.7 -2.7,2.7H2V20c0,1.1 0.9,2 2,2h3.8v-1.5c0,-1.49 1.21,-2.7 2.7,-2.7s2.7,1.21 2.7,2.7V22H17c1.1,0 2,-0.9 2,-2v-4h1.5c1.38,0 2.5,-1.12 2.5,-2.5S21.88,11 20.5,11z"
    )
    val Settings: ImageVector = navIcon(
        name = "Rounded.Settings",
        path = "M19.5,12c0,-0.23 -0.01,-0.45 -0.03,-0.68l1.86,-1.41c0.4,-0.3 0.51,-0.86 0.26,-1.3l-1.87,-3.23c-0.25,-0.44 -0.79,-0.62 -1.25,-0.42l-2.15,0.91c-0.37,-0.26 -0.76,-0.49 -1.17,-0.68l-0.29,-2.31C14.8,2.38 14.37,2 13.87,2h-3.73C9.63,2 9.2,2.38 9.14,2.88L8.85,5.19c-0.41,0.19 -0.8,0.42 -1.17,0.68L5.53,4.96c-0.46,-0.2 -1,-0.02 -1.25,0.42L2.41,8.62c-0.25,0.44 -0.14,0.99 0.26,1.3l1.86,1.41C4.51,11.55 4.5,11.77 4.5,12s0.01,0.45 0.03,0.68l-1.86,1.41c-0.4,0.3 -0.51,0.86 -0.26,1.3l1.87,3.23c0.25,0.44 0.79,0.62 1.25,0.42l2.15,-0.91c0.37,0.26 0.76,0.49 1.17,0.68l0.29,2.31c0.06,0.5 0.49,0.88 0.99,0.88h3.73c0.5,0 0.93,-0.38 0.99,-0.88l0.29,-2.31c0.41,-0.19 0.8,-0.42 1.17,-0.68l2.15,0.91c0.46,0.2 1,0.02 1.25,-0.42l1.87,-3.23c0.25,-0.44 0.14,-0.99 -0.26,-1.3l-1.86,-1.41C19.49,12.45 19.5,12.23 19.5,12z M12.04,15.5c-1.93,0 -3.5,-1.57 -3.5,-3.5s1.57,-3.5 3.5,-3.5s3.5,1.57 3.5,3.5S13.97,15.5 12.04,15.5z"
    )
    val More: ImageVector = navIcon(
        name = "Rounded.MoreVert",
        path = "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z M12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z M12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
    )
    val Import: ImageVector = navIcon(
        name = "Rounded.FileDownload",
        path = "M19,9h-4V3H9v6H5l7,7 7,-7z M5,18v2h14v-2H5z"
    )
    val Export: ImageVector = navIcon(
        name = "Rounded.FileUpload",
        path = "M9,16h6v-6h4l-7,-7 -7,7h4v6z M5,18v2h14v-2H5z"
    )
    val Practical: ImageVector = navIcon(
        name = "Rounded.GridView",
        path = "M5,3h4c1.1,0 2,0.9 2,2v4c0,1.1 -0.9,2 -2,2H5c-1.1,0 -2,-0.9 -2,-2V5c0,-1.1 0.9,-2 2,-2z M15,3h4c1.1,0 2,0.9 2,2v4c0,1.1 -0.9,2 -2,2h-4c-1.1,0 -2,-0.9 -2,-2V5c0,-1.1 0.9,-2 2,-2z M5,13h4c1.1,0 2,0.9 2,2v4c0,1.1 -0.9,2 -2,2H5c-1.1,0 -2,-0.9 -2,-2v-4c0,-1.1 0.9,-2 2,-2z M15,13h4c1.1,0 2,0.9 2,2v4c0,1.1 -0.9,2 -2,2h-4c-1.1,0 -2,-0.9 -2,-2v-4c0,-1.1 0.9,-2 2,-2z"
    )
    val Entertainment: ImageVector = navIcon(
        name = "Rounded.PlayCircle",
        path = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10s10,-4.48 10,-10S17.52,2 12,2z M10,15.5v-7c0,-0.4 0.45,-0.64 0.78,-0.42l5.25,3.5c0.3,0.2 0.3,0.64 0,0.84l-5.25,3.5C10.45,16.14 10,15.9 10,15.5z"
    )
    val Play: ImageVector = navIcon(
        name = "Rounded.PlayArrow",
        path = "M8,5v14l11,-7z"
    )
    val Pause: ImageVector = navIcon(
        name = "Rounded.Pause",
        path = "M6,19h4V5H6v14z M14,5v14h4V5h-4z"
    )
    val Search: ImageVector = navIcon(
        name = "Rounded.Search",
        path = "M9.5,3C5.91,3 3,5.91 3,9.5S5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l4.42,4.42c0.29,0.29 0.77,0.29 1.06,0s0.29,-0.77 0,-1.06l-4.42,-4.42C15.91,12.09 16,10.82 16,9.5C16,5.91 13.09,3 9.5,3z M9.5,4.5c2.76,0 5,2.24 5,5s-2.24,5 -5,5s-5,-2.24 -5,-5s2.24,-5 5,-5z"
    )
    val Send: ImageVector = navIcon(
        name = "Rounded.Send",
        path = "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z"
    )
    val Stop: ImageVector = navIcon(
        name = "Rounded.Stop",
        path = "M6,6h12v12H6z"
    )
    val Refresh: ImageVector = navIcon(
        name = "Rounded.Refresh",
        path = "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"
    )
    val Copy: ImageVector = navIcon(
        name = "Rounded.Copy",
        path = "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V4h12V1z M19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2z"
    )
    val Quote: ImageVector = navIcon(
        name = "Rounded.Reply",
        path = "M10,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z"
    )
    val Volume: ImageVector = navIcon(
        name = "Rounded.VolumeUp",
        path = "M3,9v6h4l5,5V4L7,9H3z M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02z M14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"
    )
    val Edit: ImageVector = navIcon(
        name = "Rounded.Edit",
        path = "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25z M20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"
    )
    val Delete: ImageVector = navIcon(
        name = "Rounded.Delete",
        path = "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12z M8,9h8v10H8V9z M15.5,4l-1,-1h-5l-1,1H5v2h14V4z"
    )
    val MoveUp: ImageVector = navIcon(
        name = "Rounded.KeyboardArrowUp",
        path = "M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z"
    )
    val MoveDown: ImageVector = navIcon(
        name = "Rounded.KeyboardArrowDown",
        path = "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6z"
    )
    val Expand: ImageVector = navIcon(
        name = "Rounded.ChevronRight",
        path = "M9.29,6.71c-0.39,0.39 -0.39,1.02 0,1.41L13.17,12l-3.88,3.88c-0.39,0.39 -0.39,1.02 0,1.41 0.39,0.39 1.02,0.39 1.41,0l4.59,-4.59c0.39,-0.39 0.39,-1.02 0,-1.41L10.7,6.7c-0.38,-0.38 -1.02,-0.38 -1.41,0.01z"
    )
    val Pin: ImageVector = navIcon(
        name = "Rounded.PushPin",
        path = "M16,9V4l1,-1V2H7v1l1,1v5c0,1.66 -1.34,3 -3,3v2h6v7l1,1 1,-1v-7h6v-2c-1.66,0 -3,-1.34 -3,-3z"
    )
    val Lock: ImageVector = navIcon(
        name = "Rounded.Lock",
        path = "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2z M9,6c0,-1.66 1.34,-3 3,-3s3,1.34 3,3v2H9V6z M12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z"
    )
    val Unlock: ImageVector = navIcon(
        name = "Rounded.LockOpen",
        path = "M12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z M18,8h-8V6c0,-1.1 0.9,-2 2,-2 0.95,0 1.74,0.66 1.95,1.54l1.93,-0.52C15.43,3.28 13.86,2 12,2 9.79,2 8,3.79 8,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2z"
    )
    val Drag: ImageVector = navIcon(
        name = "Rounded.DragHandle",
        path = "M4,10.5h16v-2H4v2z M4,15.5h16v-2H4v2z"
    )
    val Swipe: ImageVector = navIcon(
        name = "Rounded.SwapHoriz",
        path = "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3z M21,9l-3.99,-4v3H10v2h7.01v3L21,9z"
    )
    val Branch: ImageVector = navIcon(
        name = "Rounded.AccountTree",
        path = "M17,11h3c0.55,0 1,-0.45 1,-1V4c0,-0.55 -0.45,-1 -1,-1h-6c-0.55,0 -1,0.45 -1,1v2H9.83C9.42,4.84 8.31,4 7,4 5.34,4 4,5.34 4,7s1.34,3 3,3c1.31,0 2.42,-0.84 2.83,-2H13v2c0,0.55 0.45,1 1,1h1v2H9.83C9.42,11.84 8.31,11 7,11c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c1.31,0 2.42,-0.84 2.83,-2H15v5c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-4c0,-0.55 -0.45,-1 -1,-1h-3v-4z"
    )
    val Info: ImageVector = navIcon(
        name = "Rounded.Info",
        path = "M11,17h2v-6h-2v6z M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z M11,9h2V7h-2v2z"
    )
    val Terminal: ImageVector = navIcon(
        name = "Rounded.Terminal",
        path = "M20,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6C22,4.9 21.1,4 20,4z M20,18H4V6h16V18z M18,15h-6v-1.5h6V15z M7.5,15l-1.06,-1.06L8.38,12L6.44,10.06L7.5,9l3,3L7.5,15z"
    )

    private fun navIcon(name: String, path: String): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(path),
            fill = SolidColor(Color.Black)
        ).build()
    }
}

private class SettingsBackHandlerRegistry {
    private val handlers = LinkedHashMap<Any, () -> Unit>()

    @Synchronized
    fun register(token: Any, handler: () -> Unit) {
        handlers.remove(token)
        handlers[token] = handler
    }

    @Synchronized
    fun unregister(token: Any) {
        handlers.remove(token)
    }

    fun handle(): Boolean {
        val handler = synchronized(this) { handlers.values.lastOrNull() } ?: return false
        handler()
        return true
    }

    @Synchronized
    fun clear() {
        handlers.clear()
    }
}

private val LocalSettingsBackHandlerRegistry = staticCompositionLocalOf<SettingsBackHandlerRegistry?> { null }

@Composable
internal fun RegisterSettingsBackHandler(onBack: (() -> Unit)?) {
    val registry = LocalSettingsBackHandlerRegistry.current
    val currentOnBack by rememberUpdatedState(onBack)
    val token = remember { Any() }
    DisposableEffect(registry, token, onBack != null) {
        if (registry != null && onBack != null) {
            registry.register(token) { currentOnBack?.invoke() }
        }
        onDispose { registry?.unregister(token) }
    }
}

object MiuixSettingsPage {
    private const val PAGE_TAG = "Hchat:MiuixSettingsPage"

    private class SettingsPageHostTag {
        var close: (() -> Unit)? = null
    }

    @OptIn(ExperimentalAnimationApi::class)
    @JvmStatic
    fun show(context: Context) {
        show(context, null)
    }

    @JvmStatic
    fun showFeature(context: Context, featureId: String): Boolean {
        val provider = UIRegistry.get().getAllProviders()
            .firstOrNull { it.featureId() == featureId }
            ?: return false
        show(context, DetailPage.Feature(provider))
        return true
    }

    @OptIn(ExperimentalAnimationApi::class)
    private fun show(context: Context, initialPage: DetailPage?) {
        val activity = context as? Activity ?: return
        val decor = activity.window?.decorView as? ViewGroup ?: return
        removeExistingPage(decor)

        val hostTag = SettingsPageHostTag()
        val owner = EmbeddedComposeOwner()
        val detailBackStack = navBackStackOf(SettingsNavKey(initialPage))
        fun currentDetail(): DetailPage? = (detailBackStack.lastOrNull() as? SettingsNavKey)?.page
        fun openDetail(page: DetailPage?) {
            detailBackStack.add(SettingsNavKey(page))
        }
        fun popDetail() {
            if (detailBackStack.size > 1) detailBackStack.removeLastOrNull()
        }
        val configVersion = mutableStateOf(0)
        val settingsBackHandlers = SettingsBackHandlerRegistry()
        lateinit var page: FrameLayout
        val activityBackHooks = ArrayList<XC_MethodHook.Unhook>()
        var consumingBack = false
        var closed = false
        fun unhookActivityBack() {
            activityBackHooks.forEach {
                try {
                    it.unhook()
                } catch (_: Throwable) {
                }
            }
            activityBackHooks.clear()
        }
        fun closePage() {
            if (closed) return
            closed = true
            hostTag.close = null
            settingsBackHandlers.clear()
            unhookActivityBack()
            runCatching {
                page.findViewById<ComposeView>(android.R.id.content)?.disposeComposition()
            }.onFailure {
                h.Hchat.utils.HLog.e("[Hchat:MiuixSettings] 销毁页面 Composition 失败: ${it.message}", it)
            }
            runCatching {
                if (page.parent === decor) decor.removeView(page)
            }.onFailure {
                h.Hchat.utils.HLog.e("[Hchat:MiuixSettings] 移除页面失败: ${it.message}", it)
            }
            runCatching { owner.destroy() }.onFailure {
                h.Hchat.utils.HLog.e("[Hchat:MiuixSettings] 销毁页面 Owner 失败: ${it.message}", it)
            }
        }
        fun handleBack() {
            if (consumingBack) return
            consumingBack = true
            if (settingsBackHandlers.handle()) {
                // The current nested settings route owns this back event.
            } else if (detailBackStack.size <= 1) {
                closePage()
            } else {
                popDetail()
            }
            page.post { consumingBack = false }
        }
        hostTag.close = { closePage() }
        fun installActivityBackHooks() {
            if (activityBackHooks.isNotEmpty()) return
            val keyHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return
                    if (event.keyCode != KeyEvent.KEYCODE_BACK || page.parent == null) return
                    if (event.action == KeyEvent.ACTION_UP) {
                        handleBack()
                    }
                    param.result = true
                }
            }
            val pressedHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (page.parent == null) return
                    handleBack()
                    param.result = null
                }
            }
            listOf(Activity::class.java, activity.javaClass).forEach { clazz ->
                try {
                    activityBackHooks.addAll(XposedBridge.hookAllMethods(clazz, "dispatchKeyEvent", keyHook))
                } catch (_: Throwable) {
                }
                try {
                    activityBackHooks.addAll(XposedBridge.hookAllMethods(clazz, "onKeyDown", keyHook))
                } catch (_: Throwable) {
                }
                try {
                    activityBackHooks.addAll(XposedBridge.hookAllMethods(clazz, "onKeyUp", keyHook))
                } catch (_: Throwable) {
                }
                try {
                    activityBackHooks.addAll(XposedBridge.hookAllMethods(clazz, "onBackPressed", pressedHook))
                } catch (_: Throwable) {
                }
            }
        }
        page = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        handleBack()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            tag = PAGE_TAG
            setTag(R.id.hchat_settings_page_host, hostTag)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) {
                    // 宿主销毁或外部移除页面也必须释放全局返回 Hook 和私有 Owner。
                    closePage()
                }
            })
            setBackgroundColor(if (isDarkMode(activity)) AndroidColor.BLACK else AndroidColor.WHITE)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        owner.install(page)
        owner.attach()
        val selectedTab = mutableStateOf(MainTab.PRACTICAL)
        val termsAccepted = mutableStateOf(TermsGate.isAccepted(activity))
        val composeView = try {
            ComposeView(activity).apply {
                id = android.R.id.content
                owner.install(this)
                owner.installComposition(this)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                val mainListState = rememberLazyListState()
                val featureGroupListStates = remember { mutableMapOf<FeatureGroupEntry, LazyListState>() }
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides owner,
                    LocalSettingsBackHandlerRegistry provides settingsBackHandlers
                ) {
                    HchatMiuixTheme(activity) {
                        if (!termsAccepted.value) {
                            FirstUseAgreementDialog(
                                context = activity,
                                onCancel = { closePage() },
                                onAccepted = {
                                    termsAccepted.value = true
                                    Toast.makeText(activity, "已同意协议，重启微信后完整启用模块功能", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                        NavDisplay(
                            backStack = detailBackStack,
                            modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background),
                            transition = NavTransitions.MiuixDefault,
                            onBack = {
                                if (detailBackStack.size > 1) popDetail() else closePage()
                            }
                        ) {
                            entry<SettingsNavKey> { route ->
                                val targetPage = route.page
                            if (targetPage == null) {
                                MainSettingsPage(
                                    context = activity,
                                    providers = UIRegistry.get().getAllProviders(),
                                    listState = mainListState,
                                    selectedTab = selectedTab.value,
                                    configVersion = configVersion.value,
                                    onSelectedTab = { selectedTab.value = it },
                                    onOpenSearch = { openDetail(DetailPage.Search) },
                                    onOpenProvider = { openDetail(DetailPage.Feature(it)) },
                                    onOpenGroup = { openDetail(DetailPage.FeatureGroup(it)) },
                                    onConfigImported = { configVersion.value += 1 }
                                )
                            } else if (targetPage is DetailPage.Search) {
                                SettingsSearchPage(
                                    context = activity,
                                    providers = UIRegistry.get().getAllProviders(),
                                    onBack = { popDetail() },
                                    onOpenProvider = { provider, group ->
                                        openDetail(DetailPage.Feature(
                                            provider = provider,
                                            sourceGroup = group,
                                            returnToSearch = true
                                        ))
                                    },
                                )
                            } else if (targetPage is DetailPage.FeatureGroup) {
                                FeatureGroupSettingsPage(
                                    group = targetPage.group,
                                    listState = featureGroupListStates.getOrPut(targetPage.group) { LazyListState() },
                                    onBack = { popDetail() },
                                    onOpenProvider = {
                                        openDetail(DetailPage.Feature(it, targetPage.group))
                                    }
                                )
                            } else if (targetPage is DetailPage.Feature) {
                                FeatureSettingsPage(
                                    context = activity,
                                    provider = targetPage.provider,
                                    onBack = {
                                        popDetail()
                                    }
                                )
                            }
                            }
                        }
                    }
                }
                }
            }
        } catch (throwable: Throwable) {
            closePage()
            h.Hchat.utils.HLog.e(
                "[Hchat:MiuixSettings] 创建页面 ComposeView 失败: ${throwable.message}",
                throwable
            )
            return
        }

        try {
            page.addView(
                composeView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            decor.addView(page)
            composeView.createComposition()
        } catch (throwable: Throwable) {
            closePage()
            h.Hchat.utils.HLog.e(
                "[Hchat:MiuixSettings] 创建页面 Composition 失败: ${throwable.message}",
                throwable
            )
            return
        }
        installActivityBackHooks()
        page.requestFocus()
    }

    private fun findExistingPage(decor: ViewGroup): View? {
        for (index in decor.childCount - 1 downTo 0) {
            val child = decor.getChildAt(index)
            if (child.tag == PAGE_TAG) return child
        }
        return null
    }

    private fun removeExistingPage(decor: ViewGroup) {
        val pages = (decor.childCount - 1 downTo 0)
            .map { decor.getChildAt(it) }
            .filter { it.tag == PAGE_TAG }
        pages.forEach { child ->
            val close = (child.getTag(R.id.hchat_settings_page_host) as? SettingsPageHostTag)?.close
            if (close != null) close() else decor.removeView(child)
        }
    }
}

internal object AvatarMemoryCache {
    private const val MAX_ENTRIES = 256
    private const val MAX_BYTES = 8L * 1024 * 1024
    private data class Entry(val bitmap: ImageBitmap, val bytes: Long)
    private val bitmaps = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var totalBytes = 0L

    @Synchronized
    fun cached(value: String): Pair<Boolean, ImageBitmap?> {
        val entry = bitmaps[value] ?: return false to null
        return true to entry.bitmap
    }

    @Synchronized
    fun put(value: String, bitmap: ImageBitmap?) {
        if (bitmap == null) return
        val bytes = bitmap.asAndroidBitmap().allocationByteCount.toLong()
        bitmaps.remove(value)?.let { totalBytes -= it.bytes }
        if (bytes <= 0L || bytes > MAX_BYTES) return
        bitmaps[value] = Entry(bitmap, bytes)
        totalBytes += bytes
        val oldest = bitmaps.entries.iterator()
        while ((bitmaps.size > MAX_ENTRIES || totalBytes > MAX_BYTES) && oldest.hasNext()) {
            totalBytes -= oldest.next().value.bytes
            oldest.remove()
        }
        // 缓存淘汰只释放引用；Compose 中仍显示的头像不能主动 recycle。
    }
}

internal object WeChatAvatarPathCache {
    @Volatile
    private var avatarRoot: String? = null

    fun resolveAvatarRoot(): String? {
        avatarRoot?.let { return it }
        val api = WeChatApis.database() ?: return null
        val rows = try {
            api.query("PRAGMA database_list", null)
        } catch (_: Throwable) {
            emptyList()
        }
        for (row in rows) {
            val filePath = row["file"]?.toString().orEmpty()
            if (filePath.isBlank()) continue
            val dbFile = File(filePath)
            val parent = dbFile.parentFile ?: continue
            val avatarDir = File(parent, "avatar")
            if (avatarDir.isDirectory || parent.isDirectory) {
                val path = avatarDir.absolutePath
                avatarRoot = path
                return path
            }
        }
        return null
    }
}

internal object NativeFakeLocationPickerBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var pending: PendingResult? = null

    @Synchronized
    fun launch(activity: Activity, onResult: (Double, Double) -> Unit) {
        pending = PendingResult(activity, onResult)
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent().apply {
            setClassName(activity.packageName, "${activity.packageName}.plugin.location.ui.RedirectUI")
            putExtra("map_view_type", 8)
        }
        runCatching {
            activity.startActivityForResult(intent, FAKE_LOCATION_WECHAT_PICKER_REQUEST_CODE)
        }.onFailure {
            clearPending()
            Toast.makeText(activity, "启动微信地图失败: ${it.message.orEmpty()}", Toast.LENGTH_SHORT).show()
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        runCatching {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != FAKE_LOCATION_WECHAT_PICKER_REQUEST_CODE) return
                    val result = takePending() ?: return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    val data = param.args.getOrNull(2) as? Intent
                    if (resultCode != Activity.RESULT_OK || data == null) return
                    val location = extractLocation(data)
                    if (location == null) {
                        Toast.makeText(result.activity, "解析微信地图选点失败", Toast.LENGTH_SHORT).show()
                    } else {
                        result.callback(location.first, location.second)
                    }
                }
            })
        }.onSuccess { unhooks ->
            if (unhooks.isNotEmpty()) hookedClasses.add(clazz)
        }.onFailure {
            h.Hchat.utils.HLog.e("[Hchat:FakeLocation] 地图选点结果 Hook 安装失败: ${clazz.name}", it)
        }
    }

    @Suppress("DEPRECATION")
    private fun extractLocation(data: Intent): Pair<Double, Double>? {
        val latitude = data.getDoubleExtra("kwebmap_slat", Double.NaN)
        val longitude = data.getDoubleExtra("kwebmap_lng", Double.NaN)
        validFakeLocation(latitude, longitude)?.let { return it }

        val locationIntent = runCatching {
            data.getParcelableExtra<Parcelable>("KLocationIntent")
        }.getOrNull() ?: return null
        val fieldLatitude = (KavaReflector.readField(locationIntent, "d") as? Number)?.toDouble()
        val fieldLongitude = (KavaReflector.readField(locationIntent, "e") as? Number)?.toDouble()
        if (fieldLatitude != null && fieldLongitude != null) {
            validFakeLocation(fieldLatitude, fieldLongitude)?.let { return it }
        }
        val locationData = KavaReflector.declaredMethods(locationIntent.javaClass)
            .asSequence()
            .filter { it.parameterTypes.isEmpty() && it.returnType == String::class.java }
            .mapNotNull { method -> KavaReflector.invoke(method, locationIntent) as? String }
            .firstOrNull { it.contains("lat", ignoreCase = true) && it.contains("lng", ignoreCase = true) }
            ?: locationIntent.toString()
        return parseNativeFakeLocation(locationData)
    }

    @Synchronized
    private fun takePending(): PendingResult? {
        val result = pending
        pending = null
        return result
    }

    @Synchronized
    private fun clearPending() {
        pending = null
    }

    private data class PendingResult(
        val activity: Activity,
        val callback: (Double, Double) -> Unit
    )
}

internal object RingtonePickerBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var onPicked: ((String) -> Unit)? = null
    private var currentActivity: Activity? = null

    @Synchronized
    fun launchSystem(activity: Activity, currentUri: String, onResult: (String) -> Unit) {
        onPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择通知铃声")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (currentUri.isNotBlank()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
            }
        }
        activity.startActivityForResult(intent, RINGTONE_SYSTEM_REQUEST_CODE)
    }

    @Synchronized
    fun launchFile(activity: Activity, onResult: (String) -> Unit) {
        onPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, RINGTONE_FILE_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivityForResult(Intent.createChooser(fallback, "选择铃声文件"), RINGTONE_FILE_REQUEST_CODE)
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != RINGTONE_SYSTEM_REQUEST_CODE && requestCode != RINGTONE_FILE_REQUEST_CODE) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val picked = extractPickedUri(data, requestCode)
                    takeReadPermission(data, picked)
                    onPicked?.invoke(picked?.toString().orEmpty())
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }

    private fun extractPickedUri(data: Intent, requestCode: Int): Uri? {
        if (requestCode == RINGTONE_SYSTEM_REQUEST_CODE) {
            @Suppress("DEPRECATION")
            (data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri)?.let { return it }
        }
        data.data?.let { return it }
        @Suppress("DEPRECATION")
        (data.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { return it }
        firstUriInClipData(data.clipData)?.let { return it }
        return firstUriInBundle(data.extras)
    }

    private fun firstUriInClipData(clipData: ClipData?): Uri? {
        if (clipData == null || clipData.itemCount <= 0) return null
        return try {
            clipData.getItemAt(0)?.uri
        } catch (_: Throwable) {
            null
        }
    }

    private fun firstUriInBundle(bundle: Bundle?): Uri? {
        if (bundle == null) return null
        return try {
            bundle.keySet().firstNotNullOfOrNull { key ->
                when (val value = bundle.get(key)) {
                    is Uri -> value
                    is Intent -> value.data ?: firstUriInBundle(value.extras)
                    is Bundle -> firstUriInBundle(value)
                    else -> null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun takeReadPermission(data: Intent, uri: Uri?) {
        if (uri == null || uri.scheme != "content") return
        try {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return
            currentActivity?.contentResolver
                ?.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }
}

private fun Intent.preferSystemDocumentsUi(context: Context): Intent {
    val candidates = listOf(
        "com.google.android.documentsui",
        "com.android.documentsui"
    )
    for (packageName in candidates) {
        val copy = Intent(this).setPackage(packageName)
        val resolved = runCatching {
            context.packageManager.queryIntentActivities(copy, 0)
        }.getOrDefault(emptyList())
        if (resolved.isNotEmpty()) {
            setPackage(packageName)
            break
        }
    }
    return this
}

internal object RedPacketReplyFilePickerBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var onPicked: ((List<String>) -> Unit)? = null
    private var currentActivity: Activity? = null

    @Synchronized
    fun launch(activity: Activity, replyMode: Int, onResult: (List<String>) -> Unit) {
        onPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, REDPACKET_REPLY_FILE_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivityForResult(
                Intent.createChooser(fallback, redPacketReplyPickerTitle(replyMode)),
                REDPACKET_REPLY_FILE_REQUEST_CODE
            )
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != REDPACKET_REPLY_FILE_REQUEST_CODE) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val activity = currentActivity ?: return
                    val paths = pickedUris(data)
                        .mapNotNull { uri ->
                            takeReadPermission(activity, data, uri)
                            copyReplyFileToCache(activity, uri)
                        }
                        .distinct()
                    if (paths.isNotEmpty()) onPicked?.invoke(paths)
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }

    private fun pickedUris(data: Intent): List<Uri> {
        val result = ArrayList<Uri>()
        data.data?.let { result.add(it) }
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                try {
                    clip.getItemAt(i)?.uri?.let { result.add(it) }
                } catch (_: Throwable) {
                }
            }
        }
        @Suppress("DEPRECATION")
        (data.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { result.add(it) }
        return result.distinct()
    }

    private fun takeReadPermission(activity: Activity, data: Intent, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }

    private fun copyReplyFileToCache(context: Context, uri: Uri): String? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            val path = uri.path.orEmpty()
            if (path.isNotBlank() && File(path).isFile) return path
        }
        return try {
            val name = safeReplyFileName(queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "reply_file")
            val dir = File(context.filesDir, "Hchat/redpacket_reply").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath.takeIf { target.isFile && target.length() > 0L }
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeReplyFileName(value: String): String {
        return value.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "reply_file" }
    }
}

internal object AutoReplyFilePickerBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var onPicked: ((List<String>) -> Unit)? = null
    private var currentActivity: Activity? = null

    @Synchronized
    fun launch(activity: Activity, replyMode: Int, onResult: (List<String>) -> Unit) {
        onPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, AUTO_REPLY_FILE_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivityForResult(
                Intent.createChooser(fallback, autoReplyPickerTitle(replyMode)),
                AUTO_REPLY_FILE_REQUEST_CODE
            )
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != AUTO_REPLY_FILE_REQUEST_CODE) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val activity = currentActivity ?: return
                    val paths = pickedUris(data)
                        .mapNotNull { uri ->
                            takeReadPermission(activity, data, uri)
                            copyReplyFileToCache(activity, uri)
                        }
                        .distinct()
                    if (paths.isNotEmpty()) onPicked?.invoke(paths)
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }

    private fun pickedUris(data: Intent): List<Uri> {
        val result = ArrayList<Uri>()
        data.data?.let { result.add(it) }
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                try {
                    clip.getItemAt(i)?.uri?.let { result.add(it) }
                } catch (_: Throwable) {
                }
            }
        }
        @Suppress("DEPRECATION")
        (data.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { result.add(it) }
        return result.distinct()
    }

    private fun takeReadPermission(activity: Activity, data: Intent, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }

    private fun copyReplyFileToCache(context: Context, uri: Uri): String? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            val path = uri.path.orEmpty()
            if (path.isNotBlank() && File(path).isFile) return path
        }
        return try {
            val name = safeReplyFileName(queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "reply_file")
            val dir = File(context.filesDir, "Hchat/auto_reply").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath.takeIf { target.isFile && target.length() > 0L }
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeReplyFileName(value: String): String {
        return value.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "reply_file" }
    }
}

internal object ScheduledTaskFilePickerBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var onPicked: ((List<String>) -> Unit)? = null
    private var currentActivity: Activity? = null

    @Synchronized
    fun launch(activity: Activity, taskType: Int, onResult: (List<String>) -> Unit) {
        onPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, SCHEDULED_TASK_FILE_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivityForResult(
                Intent.createChooser(fallback, scheduledTaskPickerTitle(taskType)),
                SCHEDULED_TASK_FILE_REQUEST_CODE
            )
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != SCHEDULED_TASK_FILE_REQUEST_CODE) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val activity = currentActivity ?: return
                    val paths = scheduledTaskPickedUris(data)
                        .mapNotNull { uri ->
                            scheduledTaskTakeReadPermission(activity, data, uri)
                            copyScheduledTaskFileToCache(activity, uri)
                        }
                        .distinct()
                    if (paths.isNotEmpty()) onPicked?.invoke(paths)
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }

    private fun scheduledTaskPickedUris(data: Intent): List<Uri> {
        val result = ArrayList<Uri>()
        data.data?.let { result.add(it) }
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                try {
                    clip.getItemAt(index)?.uri?.let { result.add(it) }
                } catch (_: Throwable) {
                }
            }
        }
        @Suppress("DEPRECATION")
        (data.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { result.add(it) }
        return result.distinct()
    }

    private fun scheduledTaskTakeReadPermission(activity: Activity, data: Intent, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }

    private fun copyScheduledTaskFileToCache(context: Context, uri: Uri): String? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            val path = uri.path.orEmpty()
            if (path.isNotBlank() && File(path).isFile) return path
        }
        return try {
            val name = safeScheduledTaskFileName(queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "scheduled_task_file")
            val dir = File(context.filesDir, "Hchat/scheduled_task").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath.takeIf { target.isFile && target.length() > 0L }
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeScheduledTaskFileName(value: String): String {
        return value.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "scheduled_task_file" }
    }
}

internal object AudioTransformDocumentBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var onInputPicked: ((String, String) -> Unit)? = null
    private var onOutputPicked: ((Uri, String) -> Unit)? = null
    private var currentActivity: Activity? = null

    @Synchronized
    fun launchInput(activity: Activity, onResult: (String, String) -> Unit) {
        onInputPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, AUDIO_TRANSFORM_INPUT_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivityForResult(
                Intent.createChooser(fallback, "选择音频文件"),
                AUDIO_TRANSFORM_INPUT_REQUEST_CODE
            )
        }
    }

    @Synchronized
    fun launchOutput(
        activity: Activity,
        suggestedFileName: String,
        mimeType: String,
        onResult: (Uri, String) -> Unit
    ) {
        onOutputPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, suggestedFileName)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, AUDIO_TRANSFORM_OUTPUT_REQUEST_CODE)
        } catch (_: Throwable) {
            Toast.makeText(activity, "当前系统不支持创建输出文件", Toast.LENGTH_SHORT).show()
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != AUDIO_TRANSFORM_INPUT_REQUEST_CODE &&
                        requestCode != AUDIO_TRANSFORM_OUTPUT_REQUEST_CODE
                    ) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val activity = currentActivity ?: return
                    val uri = data.data ?: return
                    if (requestCode == AUDIO_TRANSFORM_INPUT_REQUEST_CODE) {
                        takeReadPermission(activity, data, uri)
                        val copied = copyInputFileToCache(activity, uri) ?: return
                        onInputPicked?.invoke(copied.first, copied.second)
                    } else {
                        val displayName = queryDisplayName(activity, uri)
                            ?: uri.lastPathSegment
                            ?.substringAfterLast('/')
                            ?.substringAfterLast(':')
                            .orEmpty()
                        onOutputPicked?.invoke(uri, displayName.ifBlank { "output" })
                    }
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }

    private fun takeReadPermission(activity: Activity, data: Intent, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }

    private fun copyInputFileToCache(context: Context, uri: Uri): Pair<String, String>? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            val path = uri.path.orEmpty()
            if (path.isNotBlank() && File(path).isFile) {
                val name = File(path).name.ifBlank { "audio_input" }
                return path to name
            }
        }
        return try {
            val name = safeAudioTransformFileName(queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "audio_input")
            val dir = File(context.filesDir, "Hchat/audio_transform/input").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (!target.isFile || target.length() <= 0L) return null
            target.absolutePath to name
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeAudioTransformFileName(value: String): String {
        return value.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "audio_input" }
    }
}

internal object ConfigImportExportBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var onImported: (() -> Unit)? = null
    private var currentActivity: Activity? = null

    @Synchronized
    fun launchExport(activity: Activity) {
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val fileName = "Hchat_config_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, CONFIG_EXPORT_REQUEST_CODE)
        } catch (_: Throwable) {
            Toast.makeText(activity, "当前系统不支持创建配置文件", Toast.LENGTH_SHORT).show()
        }
    }

    @Synchronized
    fun launchImport(activity: Activity, onDone: () -> Unit) {
        onImported = onDone
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, CONFIG_IMPORT_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivityForResult(Intent.createChooser(fallback, "选择 Hchat 配置文件"), CONFIG_IMPORT_REQUEST_CODE)
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != CONFIG_EXPORT_REQUEST_CODE && requestCode != CONFIG_IMPORT_REQUEST_CODE) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val uri = data.data ?: return
                    val activity = currentActivity ?: return
                    if (requestCode == CONFIG_EXPORT_REQUEST_CODE) {
                        val count = HchatConfigBackup.exportToUri(activity, uri)
                        if (count > 0) {
                            Toast.makeText(activity, "已导出 $count 项配置", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, "导出失败", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val count = HchatConfigBackup.importFromUri(activity, uri)
                        if (count > 0) {
                            onImported?.invoke()
                            Toast.makeText(activity, "已导入 $count 项配置，当前设置页已刷新", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(activity, "导入失败或文件无配置", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }
}

private object HchatConfigBackup {
    private const val VERSION = 1
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "string_set"
    private const val KEY_TYPE = "type"
    private const val KEY_VALUE = "value"

    private val prefsNames = listOf(
        UI_PREFS_NAME,
        CallMediaLimitSettings.PREFS_NAME,
        FakeLocationSettings.PREFS_NAME,
        FinderMediaDownloadSettings.PREFS_NAME,
        HchatExtraSettings.PREFS_NAME,
        StatusTextLimitSettings.PREFS_NAME,
        EditMessageSettings.PREFS_NAME,
        DisableHotUpdateSettings.PREFS_NAME,
        RemoveMomentsAdsSettings.PREFS_NAME,
        MomentsAutoCommentSettings.PREFS_NAME,
        MomentsAutoLikeSettings.PREFS_NAME,
        MomentsAutoForwardSettings.PREFS_NAME,
        MomentsAutoRefreshSettings.PREFS_NAME,
        MomentsContactFilterSettings.PREFS_NAME,
        MomentsBottomDetailSettings.PREFS_NAME,
        MomentsPostNotificationSettings.PREFS_NAME,
        MomentsFakeInteractionSettings.PREFS_NAME,
        ProfileIdSettings.PREFS_NAME,
        SettingsEntrySettings.PREFS_NAME,
        FloatingShortcutSettings.PREFS_NAME,
        QuickMarkReadSettings.PREFS_NAME,
        CustomNotificationSettings.PREFS_NAME,
        ConversationGroupStore.PREFS_NAME,
        ConversationTabsStore.PREFS_NAME,
        AutoReplySettings.PREFS_NAME,
        MessageAffixSettings.PREFS_NAME,
        InputHintSettings.PREFS_NAME,
        AutoOriginalImageSettings.PREFS_NAME,
        AutoViewOriginalSettings.PREFS_NAME,
        AtAllNotificationBlockSettings.PREFS_NAME,
        KeywordNotificationSettings.PREFS_NAME,
        TextSpeechSettings.PREFS_NAME,
        TextVoiceSettings.PREFS_NAME,
        ZombieCheckSettings.PREFS_NAME,
        BackgroundRunAssistSettings.PREFS_NAME,
        QuoteDeleteClearSettings.PREFS_NAME,
        ChatTimeStyleSettings.PREFS_NAME,
        EmojiSaveSettings.PREFS_NAME,
        SwipeQuoteSettings.PREFS_NAME,
        AudioTransformSettings.PREFS_NAME,
        FakeVoiceDurationSettings.PREFS_NAME,
        MessageBubbleSettings.PREFS_NAME,
        MessageTextColorSettings.PREFS_NAME,
        HideChatAvatarSettings.PREFS_NAME,
        HideChatMenuSettings.PREFS_NAME,
        QuickContactEditSettings.PREFS_NAME,
        RoundAvatarSettings.PREFS_NAME,
        CustomFriendAvatarSettings.PREFS_NAME,
        MessageBlockSettings.PREFS_NAME,
        GroupLeaveMonitorSettings.PREFS_NAME,
        GroupRenameMonitorSettings.PREFS_NAME,
        MultiRecallSettings.PREFS_NAME,
        VoiceForwardSettings.PREFS_NAME,
        FakeScanCameraSettings.PREFS_NAME,
        RedPacketSettings.PREFS_NAME,
        AntiRecallSettings.PREFS_NAME,
        ProtobufPacketSettings.PREFS_NAME,
        WeChatTabletSettings.PREFS_NAME,
        AutoTransferSettings.PREFS_NAME,
        FakeWalletBalanceSettings.PREFS_NAME,
        MemberTitleSettings.PREFS_NAME,
        RealNameTailSettings.PREFS_NAME,
        GroupNicknameColorSettings.PREFS_NAME
    ).distinct()

    fun exportToUri(context: Context, uri: Uri): Int {
        return runCatching {
            val prefsJson = JSONObject()
            var itemCount = 0
            prefsNames.forEach { name ->
                val sp = HchatStorage.preferences(context, name)
                val entries = JSONObject()
                sp.all.forEach { (key, value) ->
                    encodePreferenceValue(value)?.let { encoded ->
                        entries.put(key, encoded)
                        itemCount++
                    }
                }
                if (entries.length() > 0) {
                    prefsJson.put(name, entries)
                }
            }
            val root = JSONObject().apply {
                put("format", "HchatConfigBackup")
                put("version", VERSION)
                put("appVersion", BuildConfig.VERSION_NAME)
                put("exportedAt", System.currentTimeMillis())
                put("prefs", prefsJson)
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return 0
            itemCount
        }.getOrDefault(0)
    }

    fun importFromUri(context: Context, uri: Uri): Int {
        return runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.reader(Charsets.UTF_8).readText()
            }.orEmpty()
            if (text.isBlank()) return 0
            val root = JSONObject(text)
            if (root.optString("format") != "HchatConfigBackup") return 0
            val prefsJson = root.optJSONObject("prefs") ?: return 0
            var itemCount = 0
            prefsNames.forEach { name ->
                val entries = prefsJson.optJSONObject(name) ?: return@forEach
                val editor = HchatStorage.preferences(context, name).edit().clear()
                val keys = entries.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val encoded = entries.optJSONObject(key) ?: continue
                    if (decodePreferenceValue(editor, key, encoded)) {
                        itemCount++
                    }
                }
                editor.commit()
            }
            itemCount
        }.getOrDefault(0)
    }

    private fun encodePreferenceValue(value: Any?): JSONObject? {
        val obj = JSONObject()
        when (value) {
            is Boolean -> {
                obj.put(KEY_TYPE, TYPE_BOOLEAN)
                obj.put(KEY_VALUE, value)
            }
            is Int -> {
                obj.put(KEY_TYPE, TYPE_INT)
                obj.put(KEY_VALUE, value)
            }
            is Long -> {
                obj.put(KEY_TYPE, TYPE_LONG)
                obj.put(KEY_VALUE, value)
            }
            is Float -> {
                obj.put(KEY_TYPE, TYPE_FLOAT)
                obj.put(KEY_VALUE, value.toDouble())
            }
            is String -> {
                obj.put(KEY_TYPE, TYPE_STRING)
                obj.put(KEY_VALUE, value)
            }
            is Set<*> -> {
                obj.put(KEY_TYPE, TYPE_STRING_SET)
                obj.put(KEY_VALUE, JSONArray().apply {
                    value.filterIsInstance<String>().forEach { put(it) }
                })
            }
            else -> return null
        }
        return obj
    }

    private fun decodePreferenceValue(
        editor: SharedPreferences.Editor,
        key: String,
        encoded: JSONObject
    ): Boolean {
        return try {
            when (encoded.optString(KEY_TYPE)) {
                TYPE_BOOLEAN -> editor.putBoolean(key, encoded.optBoolean(KEY_VALUE))
                TYPE_INT -> editor.putInt(key, encoded.optInt(KEY_VALUE))
                TYPE_LONG -> editor.putLong(key, encoded.optLong(KEY_VALUE))
                TYPE_FLOAT -> editor.putFloat(key, encoded.optDouble(KEY_VALUE).toFloat())
                TYPE_STRING -> editor.putString(key, encoded.optString(KEY_VALUE, ""))
                TYPE_STRING_SET -> editor.putStringSet(key, decodeStringSet(encoded.optJSONArray(KEY_VALUE)))
                else -> return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun decodeStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val result = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            result.add(array.optString(i, ""))
        }
        return result
    }
}

@Composable
internal fun Modifier.responsiveTap(
    onClick: () -> Unit,
    onPressedChange: (Boolean) -> Unit = {}
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            currentOnPressedChange(true)
            val up = waitForUpOrCancellation()
            currentOnPressedChange(false)
            if (up != null) {
                currentOnClick()
            }
        }
    }
}

@Composable
internal fun rememberPressFeedbackColor(pressed: Boolean): Color {
    var feedbackVisible by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            feedbackVisible = true
        } else {
            delay(PRESS_RELEASE_DELAY_MS)
            feedbackVisible = false
        }
    }
    val target = if (feedbackVisible) {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.075f)
    } else {
        Color.Transparent
    }
    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (pressed) 90 else 210),
        label = "PressFeedback"
    ).value
}

@Composable
private fun HchatMiuixTheme(context: Context, content: @Composable () -> Unit) {
    val colors = if (isDarkMode(context)) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    MiuixTheme(colors = colors, content = content)
}
