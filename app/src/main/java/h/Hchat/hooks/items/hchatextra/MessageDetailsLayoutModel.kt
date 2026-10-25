package h.Hchat.hooks.items.hchatextra

import kotlin.math.max
import java.lang.ref.WeakReference
import java.util.WeakHashMap

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

internal data class MessageDetailsDisplayValues(
    val id: Long,
    val serverId: Long,
    val createTime: Long,
    val type: Int,
    val talker: String,
    val isSelf: Boolean
)

/** Only values used by the active display tokens belong in this cache key. */
internal data class MessageDetailsDisplayInputs(val values: List<Pair<String, String>>) {
    companion object {
        fun capture(tokens: Set<String>, source: MessageDetailsDisplayValues): MessageDetailsDisplayInputs {
            val values = tokens.sorted().mapNotNull { token ->
                val value = when (token) {
                    "time", "relativeTime", "createTime" -> source.createTime.toString()
                    "msgId" -> source.id.toString()
                    "msgSvrId" -> source.serverId.toString()
                    "talker" -> source.talker
                    "direction" -> if (source.isSelf) "1" else "0"
                    "baseType", "typeDec" -> source.type.toString()
                    "typeHex" -> "0x" + Integer.toUnsignedString(source.type, 16)
                    else -> null
                }
                value?.let { token to it }
            }
            return MessageDetailsDisplayInputs(values)
        }
    }
}

/** Display reuse requires the same message identity and the same values used by its tokens. */
internal object MessageDetailsDisplayReusePolicy {
    fun canReuse(
        previousIdentity: MessageDetailsIdentity?,
        nextIdentity: MessageDetailsIdentity?,
        previousInputs: MessageDetailsDisplayInputs?,
        nextInputs: MessageDetailsDisplayInputs,
        previousRenderKey: MessageDetailsRenderKey?,
        nextRenderKey: MessageDetailsRenderKey,
        hasContentDisplayTokens: Boolean
    ): Boolean = previousIdentity != null && nextIdentity != null &&
        previousIdentity.sameMessage(nextIdentity) && previousInputs == nextInputs &&
        previousRenderKey == nextRenderKey && !hasContentDisplayTokens
}

internal data class MessageDetailsAvatarPlacement(
    val branchTopOffset: Int,
    val topMargin: Int,
    val leftMargin: Int
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

/** The process-wide label index is deliberately weak in both directions. */
internal class MessageDetailsRowLabelIndex<Row : Any, Label : Any> {
    private val labels = WeakHashMap<Row, WeakReference<Label>>()

    @Synchronized
    fun get(row: Row): Label? {
        val label = labels[row]?.get()
        if (label == null) labels.remove(row)
        return label
    }

    @Synchronized
    fun getOrCreate(row: Row, create: () -> Label): Label {
        labels[row]?.get()?.let { return it }
        return create().also { labels[row] = WeakReference(it) }
    }

    @Synchronized
    fun set(row: Row, label: Label) {
        labels[row] = WeakReference(label)
    }

    @Synchronized
    fun remove(row: Row, expected: Label? = null) {
        val current = labels[row]?.get()
        if (expected == null || current === expected || current == null) labels.remove(row)
    }

    @Synchronized
    fun clear() = labels.clear()
}

internal object MessageDetailsUnresolvedBindPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun shouldClearExistingBinding(attempt: Int, maxAttempts: Int): Boolean = true
}

internal data class MessageDetailsClickSource<Message : Any, Details : Any>(
    val nativeMessage: Message?,
    val details: Details
)

internal object MessageDetailsClickResolver {
    fun <Message : Any, Details : Any> resolve(
        source: MessageDetailsClickSource<Message, Details>?,
        readFullDetails: (Message, Details) -> Details
    ): Details? {
        val current = source ?: return null
        val message = current.nativeMessage ?: return null
        return readFullDetails(message, current.details)
    }
}

/** Geometry shared by the native RelativeLayout path and its regression runner. */
internal object MessageDetailsAvatarLayout {
    fun placement(
        parentWidth: Int,
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        anchorLeft: Int,
        anchorRight: Int,
        anchorTop: Int,
        anchorBottom: Int,
        labelWidth: Int,
        labelHeight: Int,
        gap: Int,
        requestedAbove: Boolean,
        previouslyOwnedOffset: Int
    ): MessageDetailsAvatarPlacement {
        val baseAnchorTop = anchorTop - previouslyOwnedOffset
        val baseAnchorBottom = anchorBottom - previouslyOwnedOffset
        val branchOffset = if (requestedAbove) avatarBranchTopOffset(
            paddingTop, baseAnchorTop, labelHeight, gap
        ) else 0
        val topMargin = if (requestedAbove) {
            if (branchOffset > 0) 0 else
                (baseAnchorTop - paddingTop - labelHeight - gap.coerceAtLeast(0)).coerceAtLeast(0)
        } else {
            (baseAnchorBottom - paddingTop + gap.coerceAtLeast(0)).coerceAtLeast(0)
        }
        val maxLeft = (parentWidth - paddingRight - labelWidth).coerceAtLeast(paddingLeft)
        val centeredLeft = anchorLeft + (anchorRight - anchorLeft - labelWidth) / 2
        return MessageDetailsAvatarPlacement(
            branchTopOffset = branchOffset,
            topMargin = topMargin,
            leftMargin = centeredLeft.coerceIn(paddingLeft, maxLeft) - paddingLeft
        )
    }

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
