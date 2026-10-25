package h.Hchat.hooks.items.hchatextra

import kotlin.math.max

/** Scalar message identity is independent of which fields a display template requests. */
internal class MessageDetailsIdentity(
    val talker: String,
    val localId: Long,
    val serverId: Long,
    val createTime: Long,
    val type: Int,
    val isSelf: Boolean,
    private val fallbackMessage: Any?
) {
    fun sameMessage(other: MessageDetailsIdentity): Boolean {
        if (talker != other.talker || type != other.type || isSelf != other.isSelf) return false
        if (localId > 0L && other.localId > 0L) return localId == other.localId
        if (serverId > 0L && other.serverId > 0L) return serverId == other.serverId
        if (localId != other.localId || serverId != other.serverId) return false
        return fallbackMessage != null && fallbackMessage === other.fallbackMessage
    }
}

internal data class MessageDetailsRenderKey(
    val format: String,
    val timePattern: String,
    val zoneId: String,
    val localeTag: String,
    val relativeDay: Long?,
    val relativeMinute: Long?
)

/** A generation invalidates deferred work when a holder changes message identity. */
internal class MessageDetailsBindingGeneration {
    @Volatile private var current = Any()

    fun token(): Any = current

    fun invalidate() {
        current = Any()
    }

    fun isCurrent(token: Any): Boolean = current === token
}

/** Geometry shared by the native RelativeLayout path and its regression runner. */
internal object MessageDetailsAvatarLayout {
    fun requiredRowBottom(
        nativeContentBottom: Int,
        avatarBottom: Int,
        labelHeight: Int,
        gap: Int,
        trailingSpace: Int
    ): Int = max(nativeContentBottom, avatarBottom + labelHeight.coerceAtLeast(0) + gap.coerceAtLeast(0)) +
        trailingSpace.coerceAtLeast(0)

    fun additionalRowHeight(
        currentRowHeight: Int,
        nativeContentBottom: Int,
        avatarBottom: Int,
        labelHeight: Int,
        gap: Int,
        trailingSpace: Int
    ): Int = (requiredRowBottom(nativeContentBottom, avatarBottom, labelHeight, gap, trailingSpace) -
        currentRowHeight).coerceAtLeast(0)

    fun avatarBranchTopOffset(parentPaddingTop: Int, anchorTop: Int, labelHeight: Int, gap: Int): Int =
        (parentPaddingTop + labelHeight.coerceAtLeast(0) + gap.coerceAtLeast(0) - anchorTop).coerceAtLeast(0)
}
