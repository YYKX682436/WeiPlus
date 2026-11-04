package h.Hchat.hooks.items.hchatextra

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses the production tail registry and a real RecyclerView, with explicit draw phases. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageDetailsTailPreDrawRegressionTest {
    @After fun clear() = MessageDetailsFollowLatestRegistry.clear()

    @Test fun `pre-bind edge survives first expanded layout and correction precedes draw`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        val row = fixture.growTail(100)
        assertTrue("the grown row actually crosses the RecyclerView viewport", fixture.overflow(row) > 0)
        assertTrue("growth makes the framework report more downward scrolling", fixture.list.canScrollVertically(1))
        MessageDetailsFollowLatestRegistry.observe(fixture.list)
        assertTrue("a second registration cannot discard the earlier native edge", fixture.following())

        val identity = fixture.identity(1)
        val candidate = MessageDetailsPreDrawQueue()
        candidate.schedule(row, row) { fixture.remember(row, identity, inPreDraw = true) }
        val before = fixture.overflow(row)
        assertEquals("binding has not scrolled before the layout's draw phase", before, fixture.overflow(row))
        fixture.dispatchPreDraw()
        assertEquals("the same pre-draw pass aligns the measured label", 0, fixture.overflow(row))
        candidate.clear()
        fixture.destroy()
    }

    @Test fun `tail remembered without overflow follows subsequent row and viewport growth`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        var row = fixture.tail()
        assertEquals(0, fixture.overflow(row))
        fixture.remember(row, fixture.identity(2), inPreDraw = true)

        row = fixture.resizeAttachedTail(90)
        assertTrue("subsequent native row growth creates overflow", fixture.overflow(row) > 0)
        assertTrue("no posted repair has run before pre-draw", fixture.overflow(row) > 0)
        fixture.dispatchPreDraw()
        assertEquals("the retained tail is corrected before drawing", 0, fixture.overflow(row))

        fixture.layout(height = 85)
        if (fixture.overflow(row) > 0) {
            fixture.dispatchPreDraw()
            assertEquals("viewport shrink is handled at its own pre-draw", 0, fixture.overflow(row))
        } else {
            assertEquals("native layout already kept the label visible", 0, fixture.overflow(row))
        }
        fixture.destroy()
    }

    @Test fun `viewport overflow uses the drawn position when a native row is translated`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        val row = fixture.tail()
        assertEquals(0, fixture.overflow(row))
        row.translationY = 30f
        assertTrue("the screen position, including the host translation, crosses the real viewport",
            fixture.overflow(row) > 0)
        fixture.destroy()
    }

    @Test @Config(sdk = [27])
    fun `screen position includes row translation on the minimum supported Android version`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        val row = fixture.tail()
        row.translationY = 30f
        assertTrue("API 27 uses the actual translated position", fixture.overflow(row) > 0)
        fixture.destroy()
    }

    @Test fun `window offset does not create a false overflow`() {
        // Global visible rectangles are relative to the window, while screen locations also
        // include the window's placement (for example in split screen or a floating window).
        val fixture = Fixture(screenOffset = 250)
        fixture.observeAtNativeEdge()
        assertEquals("a visible tail remains visible when the whole window is offset", 0,
            fixture.overflow(fixture.tail()))
        val grown = fixture.growTail(100)
        assertTrue("a real overflow is still detected in the offset window", fixture.overflow(grown) > 0)
        fixture.destroy()
    }

    @Test fun `root view offset in a real hierarchy does not create false overflow`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        fixture.shiftDecor(50)
        val visible = Rect()
        assertTrue(fixture.list.getGlobalVisibleRect(visible))
        val window = IntArray(2)
        fixture.list.getLocationInWindow(window)
        assertEquals("the real root offset separates window and root coordinates", 50, window[1] - visible.top)
        assertEquals("an offset root must not create overflow", 0, fixture.overflow(fixture.tail()))
        fixture.destroy()
    }

    @Test fun `root offset retains translated and scaled tail geometry`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        fixture.shiftDecor(50)
        val row = fixture.tail()
        row.translationY = 20f
        assertEquals("translation is measured against the root-clipped viewport", 20, fixture.overflow(row))
        row.translationY = 0f
        row.pivotY = 0f
        row.scaleY = 1.5f
        assertEquals("scale is measured against the same viewport", 20, fixture.overflow(row))
        fixture.destroy()
    }

    @Test fun `a first observation without earlier tail proof does not guess history intent`() {
        val fixture = Fixture()
        fixture.growTail(100)
        assertTrue(fixture.list.canScrollVertically(1))
        MessageDetailsFollowLatestRegistry.observe(fixture.list)
        assertFalse("a scrollable viewport alone cannot prove the user was following", fixture.following())
        val row = fixture.tail()
        val before = fixture.overflow(row)
        fixture.remember(row, fixture.identity(6), inPreDraw = true)
        assertEquals("an ambiguous first observation leaves the viewport unchanged", before, fixture.overflow(row))
        fixture.destroy()
    }

    @Test fun `automatic negative recycler scroll after growth keeps following`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        fixture.growTail(100)
        assertTrue(fixture.list.canScrollVertically(1))
        MessageDetailsFollowLatestRegistry.onRecyclerScrolled(fixture.list, -1)
        assertTrue("a host layout scroll without user input must not revoke tail following", fixture.following())
        val row = fixture.tail()
        fixture.remember(row, fixture.identity(70), inPreDraw = true)
        assertEquals("the measured tail is still corrected before drawing", 0, fixture.overflow(row))
        fixture.destroy()
    }

    @Test fun `actual history gesture prevents later automatic correction`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        MessageDetailsFollowLatestRegistry.onRecyclerTouch(fixture.list, down)
        val row = fixture.growTail(100)
        val move = MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 20f, 100f, 0)
        MessageDetailsFollowLatestRegistry.onRecyclerTouch(fixture.list, move)
        assertFalse("a deliberate gesture toward history revokes following", fixture.following())
        val before = fixture.overflow(row)
        fixture.remember(row, fixture.identity(71), inPreDraw = true)
        fixture.dispatchPreDraw()
        assertEquals("layout after a history gesture cannot pull the list back", before, fixture.overflow(row))
        down.recycle()
        move.recycle()
        fixture.destroy()
    }

    @Test fun `mouse wheel toward history prevents later automatic correction`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        val row = fixture.growTail(100)
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val coords = MotionEvent.PointerCoords().apply {
            x = 20f; y = 20f; setAxisValue(MotionEvent.AXIS_VSCROLL, 1f)
        }
        val wheel = MotionEvent.obtain(0, 20, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties),
            arrayOf(coords), 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_MOUSE, 0)
        MessageDetailsFollowLatestRegistry.onRecyclerTouch(fixture.list, wheel)
        assertFalse("a mouse wheel toward history revokes following", fixture.following())
        val before = fixture.overflow(row)
        fixture.remember(row, fixture.identity(72), inPreDraw = true)
        assertEquals(before, fixture.overflow(row))
        wheel.recycle()
        fixture.destroy()
    }

    @Test fun `explicit keyboard or accessibility history navigation protects the viewport`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        MessageDetailsFollowLatestRegistry.onUserNavigation(fixture.list, towardHistory = true)
        assertFalse("accepted asynchronous history navigation is protected before movement", fixture.following())
        val row = fixture.growTail(100)
        MessageDetailsFollowLatestRegistry.onUserNavigation(fixture.list, towardHistory = true)
        assertFalse("handled history navigation revokes following after movement", fixture.following())
        val before = fixture.overflow(row)
        fixture.remember(row, fixture.identity(73), inPreDraw = true)
        assertEquals(before, fixture.overflow(row))
        fixture.destroy()
    }

    @Test fun `native settling advances a hidden row before only the entering timestamp is corrected`() {
        val fixture = AnimatedFixture()
        fixture.list.settling = true
        fixture.growTail()
        val before = fixture.overflow()
        assertTrue("the new timestamp starts wholly below the viewport", before > fixture.label().height)
        fixture.remember()
        assertEquals("the first pre-draw must not jump the whole new row to the bottom", before, fixture.overflow())
        fixture.dispatchPreDraw()
        assertEquals("settling keeps the native scroll in control while the timestamp is hidden", before, fixture.overflow())

        fixture.list.scrollBy(0, before - fixture.label().height + 8)
        MessageDetailsFollowLatestRegistry.onRecyclerScrolled(fixture.list, 1)
        val entering = fixture.overflow()
        assertTrue(entering in 1..fixture.label().height)
        fixture.dispatchPreDraw()
        assertEquals("the entering label is aligned in that pre-draw", 0, fixture.overflow())
        fixture.destroy()
    }

    @Test fun `a hidden tail without native movement is recovered after one frame`() {
        val fixture = AnimatedFixture()
        fixture.growTail()
        val before = fixture.overflow()
        assertTrue(before > fixture.label().height)
        fixture.remember()
        assertEquals(before, fixture.overflow())
        fixture.dispatchPreDraw()
        assertEquals("an idle host cannot leave the latest label stranded", 0, fixture.overflow())
        fixture.destroy()
    }

    @Test fun `a stuck native settling state does not keep a per-frame observer forever`() {
        val fixture = AnimatedFixture()
        fixture.list.settling = true
        fixture.growTail()
        val before = fixture.overflow()
        fixture.remember()
        repeat(110) { fixture.dispatchPreDraw() }
        assertEquals(before, fixture.overflow())
        fixture.list.settling = false
        fixture.dispatchPreDraw()
        assertEquals("the bounded animation observer has stopped", before, fixture.overflow())
        MessageDetailsFollowLatestRegistry.onRecyclerScrollStateChanged(fixture.list)
        fixture.dispatchPreDraw()
        assertEquals("the real idle event can still recover the tail", 0, fixture.overflow())
        fixture.destroy()
    }

    @Test fun `ancestor viewport clipping change is observed without a list bounds change`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val adapter = Rows()
        val list = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
        }
        val parent = FrameLayout(activity).apply {
            clipChildren = true
            addView(list, FrameLayout.LayoutParams(320, 120))
        }
        activity.setContentView(parent)
        parent.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 320, 120)
        list.scrollToPosition(adapter.itemCount - 1)
        list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY))
        list.layout(0, 0, 320, 120)
        MessageDetailsFollowLatestRegistry.observe(list)
        val row = list.findViewHolderForAdapterPosition(adapter.itemCount - 1)!!.itemView as TextView
        val identity = MessageDetailsIdentity("filehelper", 400, 0, 100, 1, true, null)
        val confirmation = MessageDetailsTailConfirmation(adapter.itemCount - 1, identity, true, true, true, true)
        var validations = 0
        MessageDetailsFollowLatestRegistry.rememberTailRow(list, row, row, confirmation, alreadyInPreDraw = true) {
            validations++
            confirmation
        }
        assertEquals(0, validations)

        parent.layout(0, 0, 320, 90)
        assertEquals("the list's own measured height has not changed", 120, list.height)
        assertTrue("the ancestor now clips the label", MessageDetailsFollowLatestScroll.overflowDistance(list, row, row) > 0)
        list.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals("layout observation does not run the correction early", 0, validations)
        list.viewTreeObserver.dispatchOnPreDraw()
        assertEquals("the actual ancestor-clipped viewport is checked before draw", 1, validations)
    }

    @Test fun `history intent and stale identity or lifecycle never repair a queued row`() {
        val fixture = Fixture()
        fixture.observeAtNativeEdge()
        var row = fixture.growTail(100)
        var current = fixture.identity(3)
        var enabled = true
        var bindCurrent = true
        var latest = true
        val original = current
        fixture.remember(row, original, inPreDraw = false) {
            fixture.confirmation(current, enabled, bindCurrent, latest, row.isAttachedToWindow)
        }
        current = fixture.identity(4) // A -> B before the scheduled pre-draw.
        val before = fixture.overflow(row)
        fixture.dispatchPreDraw()
        assertEquals("A cannot move B after the holder is reused", before, fixture.overflow(row))

        current = original
        fixture.remember(row, original, inPreDraw = false) {
            fixture.confirmation(current, enabled, bindCurrent, latest, row.isAttachedToWindow)
        }
        bindCurrent = false
        fixture.dispatchPreDraw()
        assertEquals("an invalidated bind generation does not scroll", before, fixture.overflow(row))

        bindCurrent = true
        fixture.remember(row, original, inPreDraw = false) {
            fixture.confirmation(current, enabled, bindCurrent, latest, row.isAttachedToWindow)
        }
        enabled = false
        fixture.dispatchPreDraw()
        assertEquals("a disabled feature does not scroll", before, fixture.overflow(row))

        enabled = true
        fixture.remember(row, original, inPreDraw = false) {
            fixture.confirmation(current, enabled, bindCurrent, latest, row.isAttachedToWindow)
        }
        latest = false
        fixture.dispatchPreDraw()
        assertEquals("a newer adapter message makes the old callback inert", before, fixture.overflow(row))

        latest = true
        fixture.remember(row, original, inPreDraw = false)
        fixture.list.dragging = true
        MessageDetailsFollowLatestRegistry.onRecyclerScrolled(fixture.list, -10)
        assertFalse("the observed movement into history revokes following", fixture.following())
        fixture.dispatchPreDraw()
        assertEquals("history remains at its previous geometry", before, fixture.overflow(row))

        MessageDetailsFollowLatestRegistry.clear()
        fixture.list.scrollToPosition(fixture.adapter.itemCount - 1)
        fixture.layout()
        MessageDetailsFollowLatestRegistry.observe(fixture.list)
        row = fixture.resizeAttachedTail(110)
        assertTrue("the exit check has a real pending overflow", fixture.overflow(row) > 0)
        fixture.remember(row, original, inPreDraw = false)
        val beforeExit = fixture.overflow(row)
        MessageDetailsFollowLatestRegistry.clear() // page exit / switch off
        fixture.dispatchPreDraw()
        assertEquals("page exit cancels the correction before draw", beforeExit, fixture.overflow(row))
        fixture.destroy()
    }

    private class Fixture(screenOffset: Int = 0) {
        private val controller = Robolectric.buildActivity(Activity::class.java).setup()
        private val activity = controller.get()
        val adapter = Rows(screenOffset)
        val list = ScreenOffsetRecycler(activity, screenOffset).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@Fixture.adapter
        }
        private var currentHeight = 120

        init {
            activity.setContentView(list)
            layout()
            list.scrollToPosition(adapter.itemCount - 1)
            layout()
        }

        fun layout(height: Int = currentHeight) {
            currentHeight = height
            list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            list.layout(0, 0, 320, height)
        }
        fun observeAtNativeEdge() {
            assertFalse("fixture starts at the old native tail", list.canScrollVertically(1))
            MessageDetailsFollowLatestRegistry.observe(list)
            assertTrue(following())
        }
        fun following() = MessageDetailsFollowLatestRegistry.isFollowingLatest(list)
        fun shiftDecor(top: Int) {
            val decor = activity.window.decorView
            decor.layout(decor.left, top, decor.right, top + decor.height)
        }
        fun tail(): TextView = list.findViewHolderForAdapterPosition(adapter.itemCount - 1)!!.itemView as TextView
        fun growTail(height: Int): TextView {
            adapter.lastHeight = height
            adapter.notifyItemChanged(adapter.itemCount - 1)
            layout()
            return tail()
        }
        fun resizeAttachedTail(height: Int): TextView {
            val original = tail()
            original.layoutParams = (original.layoutParams as RecyclerView.LayoutParams).apply {
                this.height = height
            }
            layout()
            assertSame("geometry-only growth keeps the observed latest row", original, tail())
            return original
        }
        fun overflow(row: TextView) = MessageDetailsFollowLatestScroll.overflowDistance(list, row, row)
        fun dispatchPreDraw() { list.viewTreeObserver.dispatchOnPreDraw() }
        fun identity(id: Long) = MessageDetailsIdentity("filehelper", id, 0, 100, 1, true, null)
        fun confirmation(identity: MessageDetailsIdentity, enabled: Boolean = true,
                         bindCurrent: Boolean = true, latest: Boolean = true,
                         attached: Boolean = true) = MessageDetailsTailConfirmation(
            adapter.itemCount - 1, identity, bindCurrent, attached, enabled, latest
        )
        fun remember(row: TextView, identity: MessageDetailsIdentity, inPreDraw: Boolean,
                     validate: () -> MessageDetailsTailConfirmation? = { confirmation(identity) }) {
            MessageDetailsFollowLatestRegistry.rememberTailRow(
                list, row, row, confirmation(identity), alreadyInPreDraw = inPreDraw, validator = validate
            )
        }
        fun destroy() { controller.pause().stop().destroy() }
    }

    private class ScreenOffsetRecycler(activity: Activity, private val screenOffset: Int) : RecyclerView(activity) {
        var dragging = false
        override fun getScrollState() = if (dragging) SCROLL_STATE_DRAGGING else super.getScrollState()
        override fun getLocationOnScreen(outLocation: IntArray) {
            super.getLocationOnScreen(outLocation)
            outLocation[1] += screenOffset
        }
    }

    private class ScreenOffsetTextView(context: Context, private val screenOffset: Int) : TextView(context) {
        override fun getLocationOnScreen(outLocation: IntArray) {
            super.getLocationOnScreen(outLocation)
            outLocation[1] += screenOffset
        }
    }

    private class Rows(private val screenOffset: Int = 0) : RecyclerView.Adapter<Rows.Holder>() {
        var lastHeight = 40
        class Holder(view: TextView) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ScreenOffsetTextView(parent.context, screenOffset))
        override fun getItemCount() = 16
        override fun onBindViewHolder(holder: Holder, position: Int) {
            (holder.itemView as TextView).text = "22:08:53"
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, if (position == itemCount - 1) lastHeight else 40
            )
        }
    }

    private class ControlledRecycler(activity: Activity) : RecyclerView(activity) {
        var settling = false
        override fun getScrollState() = if (settling) SCROLL_STATE_SETTLING else super.getScrollState()
    }

    private class AnimatedFixture {
        private val controller = Robolectric.buildActivity(Activity::class.java).setup()
        private val activity = controller.get()
        private val adapter = Cards()
        val list = ControlledRecycler(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@AnimatedFixture.adapter
        }
        private val identity = MessageDetailsIdentity("filehelper", 300, 0, 100, 1, true, null)
        init {
            activity.setContentView(list)
            layout()
            list.scrollToPosition(adapter.itemCount - 1)
            layout()
            assertFalse(list.canScrollVertically(1))
            MessageDetailsFollowLatestRegistry.observe(list)
        }
        private fun layout() {
            list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY))
            list.layout(0, 0, 320, 120)
        }
        fun row(): FrameLayout = list.findViewHolderForAdapterPosition(adapter.itemCount - 1)!!.itemView as FrameLayout
        fun label(): TextView = row().getChildAt(0) as TextView
        fun growTail() {
            adapter.lastHeight = 100
            adapter.notifyItemChanged(adapter.itemCount - 1)
            layout()
        }
        fun overflow() = MessageDetailsFollowLatestScroll.overflowDistance(list, row(), label())
        fun remember() {
            val confirmation = MessageDetailsTailConfirmation(adapter.itemCount - 1, identity, true, true, true, true)
            MessageDetailsFollowLatestRegistry.rememberTailRow(
                list, row(), label(), confirmation, alreadyInPreDraw = true
            ) { confirmation }
        }
        fun dispatchPreDraw() { list.viewTreeObserver.dispatchOnPreDraw() }
        fun destroy() { controller.pause().stop().destroy() }
    }

    private class Cards : RecyclerView.Adapter<Cards.Holder>() {
        var lastHeight = 40
        class Holder(view: FrameLayout) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = FrameLayout(parent.context)
            row.addView(TextView(parent.context).apply { text = "22:08:53" },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30, Gravity.BOTTOM))
            return Holder(row)
        }
        override fun getItemCount() = 16
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, if (position == itemCount - 1) lastHeight else 40
            )
        }
    }
}
