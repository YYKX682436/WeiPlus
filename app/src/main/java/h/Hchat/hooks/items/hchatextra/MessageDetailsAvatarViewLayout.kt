package h.Hchat.hooks.items.hchatextra

import android.view.ViewGroup
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Small production seam for the avatar label's actual Android layout parameters. */
internal object MessageDetailsAvatarViewLayout {
    fun layoutParams(
        placement: MessageDetailsAvatarPlacement
    ): RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        addRule(RelativeLayout.ALIGN_PARENT_TOP)
        topMargin = placement.topMargin
        leftMargin = placement.leftMargin
    }

    fun attach(parent: RelativeLayout, label: TextView, params: RelativeLayout.LayoutParams) {
        val previousParent = label.parent as? ViewGroup
        if (previousParent !== parent) {
            previousParent?.removeView(label)
            parent.addView(label, params)
        } else if (!sameLayoutParams(label.layoutParams, params)) {
            label.layoutParams = params
        }
    }

    private fun sameLayoutParams(current: ViewGroup.LayoutParams?, expected: RelativeLayout.LayoutParams): Boolean {
        val value = current as? RelativeLayout.LayoutParams ?: return false
        return value.width == expected.width && value.height == expected.height &&
            value.leftMargin == expected.leftMargin && value.topMargin == expected.topMargin &&
            value.rightMargin == expected.rightMargin && value.bottomMargin == expected.bottomMargin &&
            value.marginStart == expected.marginStart && value.marginEnd == expected.marginEnd &&
            value.rules.contentEquals(expected.rules)
    }
}

internal object MessageDetailsClickBinding {
    fun update(
        label: TextView,
        enabled: Boolean,
        bindingTag: Int,
        onBoundClick: (View, Any) -> Unit
    ) {
        if (enabled) {
            if (!label.isClickable) {
                label.setOnClickListener { view ->
                    view.getTag(bindingTag)?.let { onBoundClick(view, it) }
                }
                label.isClickable = true
            }
        } else if (label.isClickable) {
            disable(label)
        }
        disableFocus(label)
    }

    fun clear(label: TextView, bindingTag: Int) {
        label.setOnClickListener(null)
        label.setTag(bindingTag, null)
        label.isClickable = false
        disableFocus(label)
    }

    private fun disable(label: TextView) {
        label.setOnClickListener(null)
        label.isClickable = false
    }

    private fun disableFocus(label: TextView) {
        if (label.isFocusable) label.isFocusable = false
        if (label.isFocusableInTouchMode) label.isFocusableInTouchMode = false
    }
}

internal data class MessageDetailsOwnedAvatarOffset(
    val parent: WeakReference<RelativeLayout>,
    val originalTopMargin: Int,
    val appliedTopMargin: Int
)

/** Tracks only offsets the module still owns and never retains a row or its parent. */
internal class MessageDetailsAvatarOffsetRegistry {
    private val offsets = WeakHashMap<View, MessageDetailsOwnedAvatarOffset>()

    @Synchronized
    fun current(branch: View, parent: RelativeLayout): MessageDetailsOwnedAvatarOffset? {
        val value = offsets[branch] ?: return null
        val current = branch.layoutParams as? RelativeLayout.LayoutParams
        if (value.parent.get() !== parent || branch.parent !== parent || current?.topMargin != value.appliedTopMargin) {
            offsets.remove(branch)
            return null
        }
        return value
    }

    @Synchronized
    fun setAdditionalOffset(branch: View, parent: RelativeLayout, additional: Int) {
        val current = branch.layoutParams as? RelativeLayout.LayoutParams ?: return
        val existing = current(branch, parent)
        val original = existing?.originalTopMargin ?: current.topMargin
        val target = original + additional.coerceAtLeast(0)
        if (current.topMargin != target) {
            branch.layoutParams = RelativeLayout.LayoutParams(current).apply { topMargin = target }
        }
        if (target == original) offsets.remove(branch) else {
            offsets[branch] = MessageDetailsOwnedAvatarOffset(WeakReference(parent), original, target)
        }
    }

    fun restoreAround(root: View, keep: View? = null, isWithinRoot: (View, View) -> Boolean) {
        val entries = synchronized(this) {
            offsets.entries
                .filter { (branch, value) ->
                    branch !== keep && (isWithinRoot(branch, root) || value.parent.get() == null)
                }
                .map { it.key to it.value }
                .also { matches -> matches.forEach { (branch, _) -> offsets.remove(branch) } }
        }
        entries.forEach { (branch, value) -> restoreIfOwned(branch, value) }
    }

    fun restoreAll() {
        val entries = synchronized(this) {
            offsets.entries.map { it.key to it.value }.also { offsets.clear() }
        }
        entries.forEach { (branch, value) -> restoreIfOwned(branch, value) }
    }

    private fun restoreIfOwned(branch: View, value: MessageDetailsOwnedAvatarOffset) {
        val parent = value.parent.get() ?: return
        val current = branch.layoutParams as? RelativeLayout.LayoutParams ?: return
        if (branch.parent !== parent || current.topMargin != value.appliedTopMargin) return
        branch.layoutParams = RelativeLayout.LayoutParams(current).apply {
            topMargin = value.originalTopMargin
        }
    }
}
