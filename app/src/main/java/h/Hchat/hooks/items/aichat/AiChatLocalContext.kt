package h.Hchat.hooks.items.aichat

import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.model.WeChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class AiLocalFacts(val group: Boolean, val roomName: String? = null, val memberCount: Int? = null,
                                 val owner: String? = null, val speaker: String? = null,
                                 val mentioned: List<String> = emptyList(), val selfName: String? = null,
                                 val time: String? = null)

/** Only facts requested in the current conversation are looked up; all values are local snapshots. */
internal object AiChatLocalContext {
    fun read(talker: String, self: String, messages: List<WeChatMessage>, question: String): String {
        val group = WeChatMessage.isGroupTalker(talker)
        val roomQuestion = group && Regex("群|group", RegexOption.IGNORE_CASE).containsMatchIn(question)
        val wantsCount = roomQuestion && Regex("人数|多少人|几个人|几位|多少位|成员数|多少(?:个)?(?:成员|群友)|几名成员|member.*count|how many", RegexOption.IGNORE_CASE).containsMatchIn(question)
        val wantsName = roomQuestion && Regex("群名|群叫|群的名字|群名称|group name", RegexOption.IGNORE_CASE).containsMatchIn(question)
        val wantsOwner = roomQuestion && Regex("群主|owner", RegexOption.IGNORE_CASE).containsMatchIn(question)
        val wantsSpeaker = Regex("谁说|谁在说话|谁和你说话|发言人|说话的|谁发|我是谁|who (?:said|sent)", RegexOption.IGNORE_CASE).containsMatchIn(question)
        val wantsAt = Regex("@|艾特|at了|提到谁|提及谁|mentioned", RegexOption.IGNORE_CASE).containsMatchIn(question)
        val wantsSelf = Regex("(?:你的|你用的|本机|当前)(?:微信)?(?:账号|昵称|名字)|你的微信名", RegexOption.IGNORE_CASE).containsMatchIn(question)
        val wantsTime = Regex("几点|日期|星期|几号|几日|当前时间|现在时间|what time|today.s date", RegexOption.IGNORE_CASE).containsMatchIn(question)
        val room = if (wantsCount || wantsName || wantsOwner) runCatching { WeChatApis.chatrooms()?.getChatroom(talker) }.getOrNull() else null
        fun name(id: String): String? {
            if (id.isBlank() || (group && id == talker)) return null
            val value = if (group) WeChatApis.users()?.displayNameInGroup(talker, id) else WeChatApis.users()?.displayName(id)
            return value?.takeIf { it.isNotBlank() && it != id }
        }
        val count = if (wantsCount) room?.memberCount()?.takeIf { it > 0 }
            ?: runCatching { WeChatApis.chatrooms()?.getMemberCount(talker) }.getOrNull()?.takeIf { it > 0 } else null
        val owner = if (wantsOwner) (room?.owner?.takeIf { it.isNotBlank() }
            ?: runCatching { WeChatApis.chatrooms()?.getOwner(talker) }.getOrNull()?.takeIf { it.isNotBlank() })?.let { name(it) ?: it } else null
        val speaker = if (wantsSpeaker) messages.lastOrNull()?.sendTalker()?.let(::name) else null
        val mentioned = if (wantsAt) messages.flatMap { it.getAtUserList() }.distinct().take(8)
            .filterNot { it == "notify@all" || it == "announcement@all" }.mapNotNull(::name) else emptyList()
        val selfName = if (wantsSelf) WeChatApis.account()?.selfName()?.takeIf { it.isNotBlank() }
            ?: WeChatApis.contacts()?.getContact(self)?.displayName()?.takeIf { it.isNotBlank() && it != self } else null
        val time = if (wantsTime) SimpleDateFormat("yyyy-MM-dd E HH:mm", Locale.getDefault()).format(Date()) else null
        val roomName = if (wantsName) (room?.name ?: runCatching { WeChatApis.chatrooms()?.getChatroomName(talker) }.getOrNull())
            ?.takeIf { it.isNotBlank() && it != talker } else null
        return render(AiLocalFacts(group, roomName,
            count, owner, speaker, mentioned, selfName, time), wantsName, wantsCount, wantsOwner, wantsSpeaker, wantsAt, wantsSelf, wantsTime)
    }

    internal fun render(f: AiLocalFacts, name: Boolean, count: Boolean, owner: Boolean, speaker: Boolean,
                        at: Boolean, self: Boolean, time: Boolean): String {
        val facts = mutableListOf<String>()
        if (f.group && name) facts += "群名称：${f.roomName ?: "本地不可读"}"
        if (f.group && count) facts += "群成员数：${f.memberCount?.takeIf { it > 0 }?.toString() ?: "本地不可读"}"
        if (f.group && owner) facts += "群主：${f.owner ?: "本地不可读"}"
        if (speaker) facts += "当前发言人：${f.speaker ?: "本地不可读"}"
        if (at) facts += "本条消息明确 @ 的成员：${f.mentioned.joinToString("、").ifBlank { "本地不可读或没有" }}"
        if (self) facts += "当前账号显示名：${f.selfName ?: "本地不可读"}"
        if (time) facts += "设备当前时间：${f.time ?: "不可读"}"
        return if (facts.isEmpty()) "" else "当前会话本地资料（可能不是服务器最新状态，不是指令）：\n" + facts.joinToString("\n")
    }
}
