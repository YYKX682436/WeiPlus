package h.Hchat.hooks.api.model

object WeChatMessageTypes {
    const val TEXT = 1
    const val IMAGE = 3
    const val VOICE = 34
    const val VIDEO = 43
    const val EMOJI = 47
    const val LOCATION = 48
    const val APP = 49
    const val SYSTEM = 10000
    const val RECALLED = 10002
    fun normalize(type: Int): Int = type
    fun isText(type: Int): Boolean = normalize(type) == TEXT
    fun isImage(type: Int): Boolean = normalize(type) == IMAGE
    fun isVoice(type: Int): Boolean = normalize(type) == VOICE
    fun isVideo(type: Int): Boolean = normalize(type) == VIDEO
    fun isEmoji(type: Int): Boolean = normalize(type) == EMOJI
    fun isLocation(type: Int): Boolean = normalize(type) == LOCATION
    fun isApp(type: Int): Boolean = normalize(type) == APP
    fun isSystem(type: Int): Boolean = normalize(type) == SYSTEM || normalize(type) == RECALLED
    fun isVideoAccount(type: Int): Boolean = false
}
