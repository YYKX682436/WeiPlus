package h.Hchat.hooks.items.hchatextra

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import h.Hchat.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageDetailsR1RegressionTest {
    private val renderKey = MessageDetailsRenderKey("\${time}", "HH:mm:ss", "UTC", "en-US", null, null)

    @Test
    fun `server id becoming available invalidates display text`() {
        val message = Any()
        val before = identity(message, localId = 17L, serverId = 0L, createTime = 100L)
        val after = identity(message, localId = 17L, serverId = 88001L, createTime = 100L)
        assertTrue("server acknowledgement remains the same message", before.sameMessage(after))

        assertFalse(
            "the ${'$'}{msgSvrId} output changes from 0 to 88001",
            canReuse(before, after, setOf("msgSvrId"), 0L, 88001L, 100L, 100L)
        )
    }

    @Test
    fun `create time change invalidates time and createTime outputs`() {
        val message = Any()
        val before = identity(message, localId = 19L, serverId = 44L, createTime = 100L)
        val after = identity(message, localId = 19L, serverId = 44L, createTime = 200L)
        assertTrue("createTime is display input, not message identity", before.sameMessage(after))

        for (token in listOf("time", "createTime")) {
            assertFalse(
                "${'$'}{$token} must reflect the updated native field",
                canReuse(before, after, setOf(token), 0L, 0L, 100L, 200L)
            )
        }
    }

    @Test
    fun `mutating the same native object refreshes captured display fields`() {
        data class Native(var localId: Long, var serverId: Long, var createTime: Long)
        val native = Native(31L, 0L, 100L)
        val beforeIdentity = identity(native, native.localId, native.serverId, native.createTime)
        val beforeInputs = inputs(setOf("msgSvrId", "time"), native.serverId, native.createTime)
        native.serverId = 900L
        native.createTime = 300L
        val afterIdentity = identity(native, native.localId, native.serverId, native.createTime)
        val afterInputs = inputs(setOf("msgSvrId", "time"), native.serverId, native.createTime)
        assertTrue(beforeIdentity.sameMessage(afterIdentity))
        assertFalse(
            "identity may stay stable while the same object's rendered fields change",
            MessageDetailsDisplayReusePolicy.canReuse(
                beforeIdentity, afterIdentity, beforeInputs, afterInputs, renderKey, renderKey, false
            )
        )
    }

    @Test
    fun `same message and same display inputs remain reusable`() {
        val message = Any()
        val a = identity(message, 21L, 501L, 100L)
        val b = identity(Any(), 21L, 501L, 100L)
        val values = inputs(setOf("time", "msgSvrId"), 501L, 100L)
        assertTrue(
            MessageDetailsDisplayReusePolicy.canReuse(a, b, values, values, renderKey, renderKey, false)
        )
    }

    @Test
    fun `click source follows B and an unresolved bind clears A`() {
        val label = TextView(RuntimeEnvironment.getApplication())
        label.text = "12:34:56"
        label.textSize = 16f
        label.includeFontPadding = false
        val parent = RelativeLayout(RuntimeEnvironment.getApplication())
        parent.addView(label, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) })
        measureAndLayout(parent)
        val originalParams = label.layoutParams
        val originalGeometry = listOf(label.left, label.top, label.right, label.bottom, parent.measuredHeight)
        var fullReads = 0
        var opened: String? = null
        MessageDetailsClickBinding.update(label, true, R.id.hchat_message_details_binding) { _, raw ->
            val source = raw as MessageDetailsClickSource<String, String>
            opened = MessageDetailsClickResolver.resolve(source) { message, summary ->
                fullReads++
                "full:$message:$summary"
            }
        }
        label.setTag(R.id.hchat_message_details_binding, MessageDetailsClickSource("message-A", "summary-A"))
        label.setTag(R.id.hchat_message_details_binding, MessageDetailsClickSource("message-B", "summary-B"))
        assertEquals("click does not parse content until the user taps", 0, fullReads)
        assertTrue(label.performClick())
        assertEquals("click must resolve the current row tag", "full:message-B:summary-B", opened)
        assertEquals(1, fullReads)
        assertFalse("the time label must not take composer/list focus", label.isFocusable)

        MessageDetailsClickBinding.update(label, false, R.id.hchat_message_details_binding) { _, _ ->
            error("disabled details click must not run")
        }
        assertEquals("turning off clicks preserves the current binding", true,
            label.getTag(R.id.hchat_message_details_binding) != null)
        assertFalse("disabled click must not run", label.performClick())
        measureAndLayout(parent)
        assertSame("clickability updates retain the row LayoutParams", originalParams, label.layoutParams)
        assertEquals("clickability updates do not alter measured or laid out geometry", originalGeometry,
            listOf(label.left, label.top, label.right, label.bottom, parent.measuredHeight))
        MessageDetailsClickBinding.update(label, true, R.id.hchat_message_details_binding) { _, raw ->
            val source = raw as MessageDetailsClickSource<String, String>
            opened = MessageDetailsClickResolver.resolve(source) { message, _ ->
                fullReads++
                "full:$message"
            }
        }
        assertTrue("re-enabled click uses the same current message", label.performClick())
        assertEquals("full:message-B", opened)
        measureAndLayout(parent)
        assertEquals("re-enabling detail clicks also leaves geometry unchanged", originalGeometry,
            listOf(label.left, label.top, label.right, label.bottom, parent.measuredHeight))

        assertTrue(
            "unresolved B must invalidate A on the first bind attempt",
            MessageDetailsUnresolvedBindPolicy.shouldClearExistingBinding(attempt = 0, maxAttempts = 4)
        )
        MessageDetailsClickBinding.clear(label, R.id.hchat_message_details_binding)
        opened = "full:message-A"
        val unresolved = MessageDetailsClickSource<String, String>(null, "summary-A")
        assertEquals("an unresolved recycled row cannot fall back to A's summary", null,
            MessageDetailsClickResolver.resolve(unresolved) { _, _ -> "stale-A" })
        opened = null
        assertFalse(label.performClick())
        assertEquals("cleared A binding cannot still open details", null, opened)
        assertEquals(null, label.getTag(R.id.hchat_message_details_binding))
    }

    @Test
    fun `large repeated R1 lookups create one label per row and perform no global scans`() {
        data class Binding(val root: Any)
        val rowLabels = MessageDetailsRowLabelIndex<Any, String>()
        var globalSnapshotScans = 0
        val globalBindings = MessageDetailsBindingIndex<String, Binding> { globalSnapshotScans++ }
        val rows = List(96) { Any() }
        val labels = mutableListOf<String>()
        val rowOwnedBindings = mutableMapOf<String, Binding>()
        var created = 0
        var lookups = 0
        repeat(80) {
            rows.forEach { row ->
                lookups++
                val label = rowLabels.get(row) ?: "label-${created++}".also {
                    labels.add(it)
                    rowLabels.set(row, it)
                }
                Binding(row).also { binding ->
                    rowOwnedBindings[label] = binding
                    globalBindings[label] = binding
                }
            }
        }
        assertEquals("label creation must be stable across payload binds", rows.size, created)
        assertEquals("each row retains only its current binding", rows.size, rowOwnedBindings.size)
        assertEquals("test covers a large repeated bind batch", 7_680, lookups)
        assertEquals("row lookup must not read the process-wide binding snapshot", 0, globalSnapshotScans)
    }

    @Test
    fun `avatar above uses its anchor when there is enough leading space`() {
        val (parent, avatar, label) = row(anchorTop = 84)
        val placement = plan(parent, avatar, label, gap = 5, above = true)
        MessageDetailsAvatarViewLayout.attach(parent, label, MessageDetailsAvatarViewLayout.layoutParams(placement))
        measureAndLayout(parent)

        assertEquals(
            "label must sit above the avatar by the configured gap",
            avatar.top - label.bottom,
            5
        )
        assertTrue("above-avatar label must not be pinned to the row top", label.top > parent.paddingTop)
    }

    @Test
    fun `insufficient leading space moves only the avatar branch and preserves the gap`() {
        val (parent, avatar, label) = row(anchorTop = 13)
        val offsets = MessageDetailsAvatarOffsetRegistry()
        val originalMargin = (avatar.layoutParams as RelativeLayout.LayoutParams).topMargin
        val placement = plan(parent, avatar, label, gap = 4, above = true)
        offsets.setAdditionalOffset(avatar, parent, placement.branchTopOffset)
        MessageDetailsAvatarViewLayout.attach(parent, label, MessageDetailsAvatarViewLayout.layoutParams(placement))
        measureAndLayout(parent)

        assertEquals(4, avatar.top - label.bottom)
        assertEquals(originalMargin + placement.branchTopOffset, (avatar.layoutParams as RelativeLayout.LayoutParams).topMargin)
        offsets.setAdditionalOffset(avatar, parent, 0)
        assertEquals("moving to avatar-below restores the original host margin", originalMargin,
            (avatar.layoutParams as RelativeLayout.LayoutParams).topMargin)
    }

    @Test
    fun `avatar below gap zero and tall native content use the real RelativeLayout measure pass`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = RelativeLayout(context).apply { setPadding(0, 8, 0, 0) }
        val nativeContent = View(context)
        parent.addView(nativeContent, RelativeLayout.LayoutParams(220, 280).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
        })
        val avatar = View(context)
        parent.addView(avatar, RelativeLayout.LayoutParams(48, 48).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            leftMargin = 70
            topMargin = 82
        })
        val label = label(context)
        measureAndLayout(parent)
        val initialHeight = parent.measuredHeight
        val placement = plan(parent, avatar, label, gap = 0, above = false)
        MessageDetailsAvatarViewLayout.attach(parent, label, MessageDetailsAvatarViewLayout.layoutParams(placement))
        measureAndLayout(parent)

        assertEquals("gap zero means touching bounds", avatar.bottom, label.top)
        assertEquals("native tall content still determines the row height", initialHeight, parent.measuredHeight)
    }

    @Test
    fun `R2 appended row and label growth can clip the latest time in a real scroll viewport`() {
        val context = RuntimeEnvironment.getApplication()
        val scroll = ScrollView(context)
        val messages = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        repeat(5) {
            messages.addView(RelativeLayout(context).apply {
                addView(View(context), RelativeLayout.LayoutParams(48, 48).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_TOP)
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        measureAndLayout(scroll, width = 240, height = 200)
        scroll.scrollTo(0, messages.measuredHeight)
        assertFalse("the host has positioned the current last row at the viewport bottom", scroll.canScrollVertically(1))

        val row = RelativeLayout(context)
        val avatar = View(context)
        row.addView(avatar, RelativeLayout.LayoutParams(48, 48).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            leftMargin = 70
        })
        messages.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        measureAndLayout(scroll, width = 240, height = 200)
        assertTrue("the new native row arrives after the host's earlier bottom positioning",
            scroll.canScrollVertically(1))

        val label = label(context)
        val placement = MessageDetailsAvatarLayout.placement(
            parentWidth = row.measuredWidth,
            paddingLeft = row.paddingLeft,
            paddingTop = row.paddingTop,
            paddingRight = row.paddingRight,
            anchorLeft = avatar.left,
            anchorRight = avatar.right,
            anchorTop = avatar.top,
            anchorBottom = avatar.bottom,
            labelWidth = label.measuredWidth,
            labelHeight = label.measuredHeight,
            gap = 0,
            requestedAbove = false,
            previouslyOwnedOffset = 0
        )

        // This is the R2 sequence: the host has followed the old content end,
        // then a new row and its correctly measured native child extend the scroll range.
        MessageDetailsAvatarViewLayout.attach(row, label, MessageDetailsAvatarViewLayout.layoutParams(placement))
        measureAndLayout(scroll, width = 240, height = 200)

        assertTrue("the label remains a real child inside the measured row", label.parent === row)
        assertTrue("the label bottom is included in the row's measured height", label.bottom <= row.measuredHeight)
        assertEquals("the R2 stale tail offset hides the measured timestamp from the real viewport", 0,
            visibleHeightWithin(scroll, label))
        assertTrue("R2 red test starts with a clipped time and stale bottom offset",
            MessageDetailsFollowLatestScroll.correctTailOverflow(
                scroll, row, label, followingLatest = false, isLatestRow = true
            ) == 0 && scroll.canScrollVertically(1))

        val correctedBy = MessageDetailsFollowLatestScroll.correctTailOverflow(
            scroll, row, label, followingLatest = true, isLatestRow = true
        )
        measureAndLayout(scroll, width = 240, height = 200)
        assertTrue("a following user receives a measured overflow correction", correctedBy > 0)
        assertEquals("the corrected latest time is fully visible", label.height,
            visibleHeightWithin(scroll, label))
        assertFalse("the latest row now reaches the viewport end", scroll.canScrollVertically(1))

        scroll.scrollTo(0, 0)
        val historyScrollY = scroll.scrollY
        assertEquals("a user reading history is never pulled to the tail", 0,
            MessageDetailsFollowLatestScroll.correctTailOverflow(
                scroll, row, label, followingLatest = false, isLatestRow = true
            ))
        assertEquals(historyScrollY, scroll.scrollY)

        measureAndLayout(scroll, width = 240, height = 150)
        val viewportCorrection = MessageDetailsFollowLatestScroll.correctTailOverflow(
            scroll, row, label, followingLatest = true, isLatestRow = true
        )
        measureAndLayout(scroll, width = 240, height = 150)
        assertTrue("a changed viewport receives only the new clipped distance", viewportCorrection > 0)
        assertTrue("time remains visible after a keyboard-like viewport resize",
            visibleHeightWithin(scroll, label) == label.height)
    }

    @Test
    fun `tail follow state leaves history and returns only when the actual list end is reached`() {
        val state = MessageDetailsFollowLatestState(initiallyFollowingLatest = true)
        state.onScrolled(verticalDelta = -12, atLatestEdge = false)
        assertFalse("scrolling upward into history cancels automatic following", state.isFollowingLatest())

        state.onPositionRequested(targetsLatestRow = true)
        assertFalse("an adapter request cannot undo the user's history-reading state", state.isFollowingLatest())

        state.onScrolled(verticalDelta = 18, atLatestEdge = false)
        assertFalse("partial movement toward the newest row does not claim the tail", state.isFollowingLatest())

        state.onScrolled(verticalDelta = 5, atLatestEdge = true)
        assertTrue("reaching the real list edge resumes tail following", state.isFollowingLatest())

        state.onScrolled(verticalDelta = -1, atLatestEdge = false)
        assertFalse("a later history scroll cancels following again", state.isFollowingLatest())
    }

    @Test
    fun `touch drag into history disables correction until the list reaches its actual end`() {
        val state = MessageDetailsFollowLatestState(initiallyFollowingLatest = true)
        state.onUserTouch(
            fingerDeltaY = 30f,
            touchSlop = 12,
            canScrollTowardHistory = true,
            atLatestEdge = false
        )
        assertFalse("a downward drag beyond touch slop marks history-reading intent", state.isFollowingLatest())

        state.onPositionRequested(targetsLatestRow = true)
        assertFalse("a native position request cannot override the physical history gesture", state.isFollowingLatest())

        state.onUserTouch(
            fingerDeltaY = -36f,
            touchSlop = 12,
            canScrollTowardHistory = true,
            atLatestEdge = false
        )
        assertFalse("a partial swipe toward recent messages does not re-enable following", state.isFollowingLatest())

        state.onUserTouch(
            fingerDeltaY = -48f,
            touchSlop = 12,
            canScrollTowardHistory = true,
            atLatestEdge = true
        )
        assertTrue("touch-driven movement resumes following only at the actual list end", state.isFollowingLatest())
    }

    @Test
    fun `tail row detection ignores attached structural footer but rejects a newer message row`() {
        data class AttachedItem(val adapterPosition: Int, val messageRow: Boolean)
        val container = FrameLayout(RuntimeEnvironment.getApplication())
        fun add(position: Int, messageRow: Boolean): View = View(container.context).apply {
            tag = AttachedItem(position, messageRow)
            container.addView(this, FrameLayout.LayoutParams(20, 20))
        }
        val candidate = add(position = 7, messageRow = true)
        add(position = 8, messageRow = false) // A list footer follows the newest message.
        val childPosition: (View) -> Int? = { (it.tag as? AttachedItem)?.adapterPosition }
        val isMessageRow: (View) -> Boolean = { (it.tag as? AttachedItem)?.messageRow == true }

        assertFalse(
            "a structural footer after the newest message does not hide the tail row",
            MessageDetailsFollowLatestScroll.hasNewerAttachedMessage(container, 7, childPosition, isMessageRow)
        )
        val newerMessage = add(position = 9, messageRow = true)
        assertTrue(
            "an actually newer attached message prevents correcting the older row",
            MessageDetailsFollowLatestScroll.hasNewerAttachedMessage(container, 7, childPosition, isMessageRow)
        )
        container.removeView(newerMessage)
        assertFalse(
            "the candidate becomes the newest message once later message content is gone",
            MessageDetailsFollowLatestScroll.hasNewerAttachedMessage(container, 7, childPosition, isMessageRow)
        )
        assertSame("the Android ViewGroup contains the candidate row", container, candidate.parent)
    }

    @Test
    fun `repeat placement does not rewrite the same TextView layout parameters`() {
        val (parent, avatar, label) = row(anchorTop = 60)
        val placement = plan(parent, avatar, label, gap = 0, above = false)
        val params = MessageDetailsAvatarViewLayout.layoutParams(placement)
        MessageDetailsAvatarViewLayout.attach(parent, label, params)
        val firstParams = label.layoutParams
        val firstWriteCount = label.writes
        MessageDetailsAvatarViewLayout.attach(parent, label, MessageDetailsAvatarViewLayout.layoutParams(placement))

        assertSame(firstParams, label.layoutParams)
        assertEquals(firstWriteCount, label.writes)
    }

    @Test
    fun `position switch and feature close restore only module owned avatar offsets`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = RelativeLayout(context)
        val branch = View(context)
        val originalMargin = 11
        parent.addView(branch, RelativeLayout.LayoutParams(40, 40).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            topMargin = originalMargin
        })
        val offsets = MessageDetailsAvatarOffsetRegistry()

        offsets.setAdditionalOffset(branch, parent, 28)
        assertEquals(originalMargin + 28, (branch.layoutParams as RelativeLayout.LayoutParams).topMargin)
        offsets.setAdditionalOffset(branch, parent, 0) // switch to avatar-below
        assertEquals("switch must restore the previous above-avatar offset", originalMargin,
            (branch.layoutParams as RelativeLayout.LayoutParams).topMargin)

        offsets.setAdditionalOffset(branch, parent, 17)
        offsets.restoreAll() // disable / leave the chat
        assertEquals("feature close restores the original host margin", originalMargin,
            (branch.layoutParams as RelativeLayout.LayoutParams).topMargin)

        offsets.setAdditionalOffset(branch, parent, 17)
        (branch.layoutParams as RelativeLayout.LayoutParams).let { hostChanged ->
            branch.layoutParams = RelativeLayout.LayoutParams(hostChanged).apply { topMargin = 49 }
        }
        offsets.restoreAll()
        assertEquals("host-owned replacement margin must not be overwritten", 49,
            (branch.layoutParams as RelativeLayout.LayoutParams).topMargin)
    }

    private fun canReuse(
        before: MessageDetailsIdentity,
        after: MessageDetailsIdentity,
        tokens: Set<String>,
        oldServerId: Long,
        newServerId: Long,
        oldCreateTime: Long,
        newCreateTime: Long
    ): Boolean {
        val previousInputs = inputs(tokens, oldServerId, oldCreateTime)
        val nextInputs = inputs(tokens, newServerId, newCreateTime)
        return MessageDetailsDisplayReusePolicy.canReuse(
            before, after, previousInputs, nextInputs, renderKey, renderKey, false
        )
    }

    private fun inputs(tokens: Set<String>, serverId: Long, createTime: Long) =
        MessageDetailsDisplayInputs.capture(
            tokens,
            MessageDetailsDisplayValues(0L, serverId, createTime, 1, "filehelper", true)
        )

    private fun identity(message: Any, localId: Long, serverId: Long, createTime: Long) =
        MessageDetailsIdentity("filehelper", localId, serverId, createTime, 1, true, message)

    private data class TestRow(val parent: RelativeLayout, val avatar: View, val label: CountingTextView)

    private fun row(anchorTop: Int): TestRow {
        val context = RuntimeEnvironment.getApplication()
        val parent = RelativeLayout(context).apply { setPadding(0, 8, 0, 0) }
        val avatar = View(context)
        parent.addView(avatar, RelativeLayout.LayoutParams(48, 48).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            leftMargin = 70
            topMargin = anchorTop - parent.paddingTop
        })
        val label = label(context)
        measureAndLayout(parent)
        return TestRow(parent, avatar, label)
    }

    private fun label(context: Context) = CountingTextView(context).apply {
        text = "19:43:28"
        textSize = 18f
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        measure(
            MeasureSpec.makeMeasureSpec(240, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
    }

    private fun plan(
        parent: RelativeLayout,
        avatar: View,
        label: TextView,
        gap: Int,
        above: Boolean
    ): MessageDetailsAvatarPlacement = MessageDetailsAvatarLayout.placement(
        parentWidth = 240,
        paddingLeft = parent.paddingLeft,
        paddingTop = parent.paddingTop,
        paddingRight = parent.paddingRight,
        anchorLeft = avatar.left,
        anchorRight = avatar.right,
        anchorTop = avatar.top,
        anchorBottom = avatar.bottom,
        labelWidth = label.measuredWidth,
        labelHeight = label.measuredHeight,
        gap = gap,
        requestedAbove = above,
        previouslyOwnedOffset = 0
    )

    private fun measureAndLayout(parent: RelativeLayout) {
        parent.measure(
            MeasureSpec.makeMeasureSpec(240, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
    }

    private fun measureAndLayout(parent: ScrollView, width: Int, height: Int) {
        parent.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
    }

    private fun visibleHeightWithin(container: ViewGroup, descendant: View): Int {
        val descendantBounds = Rect(0, 0, descendant.width, descendant.height)
        container.offsetDescendantRectToMyCoords(descendant, descendantBounds)
        val left = if (container.clipToPadding) container.paddingLeft else 0
        val top = if (container.clipToPadding) container.paddingTop else 0
        val right = container.width - if (container.clipToPadding) container.paddingRight else 0
        val bottom = container.height - if (container.clipToPadding) container.paddingBottom else 0
        return if (descendantBounds.intersect(
                left, top + container.scrollY, right, bottom + container.scrollY
            )) descendantBounds.height() else 0
    }

    private class CountingTextView(context: Context) : TextView(context) {
        var writes = 0
        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            writes++
            super.setLayoutParams(params)
        }
    }
}
