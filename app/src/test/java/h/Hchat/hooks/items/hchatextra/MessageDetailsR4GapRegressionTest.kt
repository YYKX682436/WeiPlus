package h.Hchat.hooks.items.hchatextra

import android.content.Context
import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.AbsListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageDetailsR4GapRegressionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private class ShortNamedRecycler(context: Context) : RecyclerView(context)
    private class ShortNamedList(context: Context) : ListView(context)
    private class ShortNamedScroll(context: Context) : ScrollView(context)
    private class CacheProbeList(context: Context) : ListView(context) {
        fun acceptanceProbe(view: View): Int = view.id
    }

    @Test
    fun `actual custom subclasses are recognized by Android inheritance`() {
        val recycler = ShortNamedRecycler(context)
        val list = ShortNamedList(context)
        val scroll = ShortNamedScroll(context)
        assertFalse(recycler.javaClass.name.contains("RecyclerView"))
        assertFalse(list.javaClass.name.contains("ListView"))
        assertFalse(scroll.javaClass.name.contains("ScrollView"))

        assertEquals(MessageDetailsScrollSupport.ContainerKind.RECYCLER,
            MessageDetailsScrollSupport.containerKind(recycler))
        assertEquals(MessageDetailsScrollSupport.ContainerKind.ABS_LIST,
            MessageDetailsScrollSupport.containerKind(list))
        assertEquals(MessageDetailsScrollSupport.ContainerKind.SCROLL,
            MessageDetailsScrollSupport.containerKind(scroll))
        assertEquals(MessageDetailsScrollSupport.ContainerKind.UNKNOWN,
            MessageDetailsScrollSupport.containerKind(View(context)))

        val adapter = MessageFixtureAdapter(mutableListOf("message:A", "message:B"))
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        measureAndLayout(recycler, 320, 170)
        assertTrue("the actual AndroidX RecyclerView lays out real adapter children", recycler.childCount > 0)
    }

    @Test
    fun `ListView visible range moving into history cancels following without scrollY`() {
        val list = ListView(context)
        list.adapter = object : BaseAdapter() {
            override fun getCount() = 40
            override fun getItem(position: Int) = position
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (convertView as? TextView ?: TextView(context)).apply {
                    text = "row $position"
                    height = 34
                    minHeight = 34
                }
        }
        val width = View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(170, View.MeasureSpec.EXACTLY)
        list.measure(width, height)
        list.layout(0, 0, 320, 170)
        list.setSelection(28)
        shadowOf(Looper.getMainLooper()).idle()
        list.measure(width, height)
        list.layout(0, 0, 320, 170)
        val previousFirst = list.firstVisiblePosition
        val previousLast = list.lastVisiblePosition
        list.setSelection(2)
        shadowOf(Looper.getMainLooper()).idle()
        list.measure(width, height)
        list.layout(0, 0, 320, 170)
        val firstVisible = list.firstVisiblePosition
        val lastVisible = list.lastVisiblePosition
        assertTrue("the Android ListView moved its visible window toward older rows", firstVisible < previousFirst)
        assertEquals("AbsListView does not encode this movement in View.scrollY", 0, list.scrollY)

        val state = MessageDetailsFollowLatestState(initiallyFollowingLatest = true)
        state.onAbsListVisiblePositions(previousFirst, previousLast, firstVisible, lastVisible, atLatestEdge = false)
        assertFalse("visible-position movement into history disables follow", state.isFollowingLatest())
    }

    @Test
    fun `unattached newer adapter item rejects old row as a tail candidate`() {
        val recycler = ShortNamedRecycler(context)
        val adapter = MessageFixtureAdapter(mutableListOf("message:A", "message:B"))
        recycler.adapter = adapter
        assertEquals(2, adapter.itemCount)
        assertTrue("B has not been attached yet", recycler.childCount == 0)

        assertFalse("A at adapter position zero is not latest while B is already in adapter data",
            isLatest(adapter, position = 0))
        assertTrue("the actual newest adapter row is confirmed even before layout attaches it",
            isLatest(adapter, position = 1))

        val withFooter = MessageFixtureAdapter(mutableListOf("message:A", null))
        recycler.adapter = withFooter
        assertFalse("null data alone cannot authorize a non-message footer",
            isLatest(withFooter, position = 0))
        assertTrue("a null item is accepted only with explicit structural evidence",
            MessageDetailsScrollSupport.isConfirmedLatestMessage(
                0, withFooter.itemCount,
                itemAt = { itemPosition -> MessageDetailsScrollSupport.ItemProbe(true, withFooter.valueAt(itemPosition)) },
                isMessageItem = { it is String && it.startsWith("message:") },
                candidateMatchesIdentity = { it == "message:A" },
                isKnownStructuralRow = { itemPosition, item -> itemPosition == 1 && item == null }
            ))

        val ambiguousFooter = MessageFixtureAdapter(mutableListOf("message:A", "unknown-footer"))
        assertFalse("an unclassified non-message item is never guessed to be a footer",
            isLatest(ambiguousFooter, position = 0))

        val attachedFooter = MessageFixtureAdapter(mutableListOf("message:A", "unknown-footer"))
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = attachedFooter
        measureAndLayout(recycler, 320, 170)
        assertTrue("the real RecyclerView attached its structural footer",
            recycler.findViewHolderForAdapterPosition(1) != null)
        val production = ProductionMessageDetailsHarness()
        assertFalse("an attached RecyclerView row without explicit footer metadata is ambiguous",
            MessageDetailsScrollSupport.isConfirmedLatestMessage(
                0,
                attachedFooter.itemCount,
                itemAt = { itemPosition -> MessageDetailsScrollSupport.ItemProbe(true, attachedFooter.valueAt(itemPosition)) },
                isMessageItem = { it is String && it.startsWith("message:") },
                candidateMatchesIdentity = { it == "message:A" },
                isKnownStructuralRow = { position, _ ->
                    production.structural(recycler, attachedFooter, position)
                }
            ))
        recycler.scrollToPosition(0)
        measureAndLayout(recycler, 320, 40)
        assertEquals("the footer is now outside the actual RecyclerView viewport and detached", null,
            recycler.findViewHolderForAdapterPosition(1))
        val detachedFooterAdapter = attachedFooter
        val footerLatestConfirmed = MessageDetailsScrollSupport.isConfirmedLatestMessage(
            0,
            detachedFooterAdapter.itemCount,
            itemAt = { itemPosition -> MessageDetailsScrollSupport.ItemProbe(true, detachedFooterAdapter.valueAt(itemPosition)) },
            isMessageItem = { it is String && it.startsWith("message:") },
            candidateMatchesIdentity = { it == "message:A" },
            isKnownStructuralRow = { position, _ ->
                production.structural(recycler, detachedFooterAdapter, position)
            }
        )
        assertFalse("detachment cannot turn unknown RecyclerView data into a proven footer",
            footerLatestConfirmed)

        val history = MessageFixtureAdapter(mutableListOf("message:A", "message:B", "message:C"))
        assertFalse("a historical row is rejected even if it is the only attached row",
            isLatest(history, position = 0))
    }

    @Test
    fun `actual RecyclerView and ListView correct measured tail overflow without moving history`() {
        val recycler = ShortNamedRecycler(context).apply { layoutManager = LinearLayoutManager(context) }
        val recyclerAdapter = HeightRecyclerAdapter(rowCount = 16, lastRowHeight = 40)
        recycler.adapter = recyclerAdapter
        measureAndLayout(recycler, 320, 120)
        recycler.scrollToPosition(recyclerAdapter.itemCount - 1)
        measureAndLayout(recycler, 320, 120)
        assertFalse("RecyclerView fixture starts at its old tail", recycler.canScrollVertically(1))
        recyclerAdapter.lastRowHeight = 100
        recyclerAdapter.notifyItemChanged(recyclerAdapter.itemCount - 1)
        measureAndLayout(recycler, 320, 120)
        val recyclerLabel = recycler.findViewHolderForAdapterPosition(recyclerAdapter.itemCount - 1)?.itemView as? TextView
        assertTrue("the actual latest RecyclerView row remains attached", recyclerLabel != null)
        assertTrue("measured RecyclerView row growth creates a real viewport overflow",
            MessageDetailsFollowLatestScroll.overflowDistance(recycler, recyclerLabel!!, recyclerLabel) > 0)
        val recyclerCorrection = MessageDetailsFollowLatestScroll.correctTailOverflow(
            recycler, recyclerLabel, recyclerLabel, followingLatest = true, isLatestRow = true
        )
        measureAndLayout(recycler, 320, 120)
        assertTrue("latest RecyclerView timestamp receives the measured correction", recyclerCorrection > 0)
        assertEquals(recyclerLabel.height, visibleHeightWithin(recyclerLabel))

        val list = ListView(context)
        val listAdapter = HeightListAdapter(rowCount = 16, lastRowHeight = 40)
        list.adapter = listAdapter
        measureAndLayout(list, 320, 120)
        list.setSelection(listAdapter.count - 1)
        measureAndLayout(list, 320, 120)
        assertFalse("ListView fixture starts at its old tail", list.canScrollVertically(1))
        listAdapter.lastRowHeight = 100
        listAdapter.notifyDataSetChanged()
        measureAndLayout(list, 320, 120)
        val lastIndex = listAdapter.count - 1 - list.firstVisiblePosition
        val listLabel = list.getChildAt(lastIndex) as? TextView
        assertTrue("the actual latest ListView row remains attached", listLabel != null)
        assertTrue("measured ListView row growth creates a real viewport overflow",
            MessageDetailsFollowLatestScroll.overflowDistance(list, listLabel!!, listLabel) > 0)
        val listCorrection = MessageDetailsFollowLatestScroll.correctTailOverflow(
            list, listLabel, listLabel, followingLatest = true, isLatestRow = true
        )
        measureAndLayout(list, 320, 120)
        assertTrue("latest ListView timestamp receives the measured correction", listCorrection > 0)
        assertEquals(listLabel.height, visibleHeightWithin(listLabel))

        recycler.scrollToPosition(2)
        measureAndLayout(recycler, 320, 120)
        val readingHistoryPosition = (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        assertEquals("a clipped row never changes history position when follow is false", 0,
            MessageDetailsFollowLatestScroll.correctTailOverflow(
                recycler, recyclerLabel, recyclerLabel, followingLatest = false, isLatestRow = true
            ))
        assertEquals(readingHistoryPosition,
            (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
    }

    @Test
    fun `deferred correction rejects same row position after message identity changes`() {
        val messageA = identity("A", 7L)
        val messageB = identity("B", 8L)
        assertFalse("the deferred gate must reject identity replacement even at the same position",
            MessageDetailsScrollSupport.delayedRowStillMatches(
                expectedPosition = 7,
                actualPosition = 7,
                expectedIdentity = messageA,
                currentIdentity = messageB,
                bindIsCurrent = true,
                rowIsAttached = true,
                featureEnabled = true,
                latestMessageConfirmed = true,
                followingLatest = true
            ))
        assertFalse("the deferred gate must reject a row that is no longer latest",
            MessageDetailsScrollSupport.delayedRowStillMatches(
                7, 7, messageA, messageA, true, true, true, false, true
            ))
        assertFalse("the deferred gate rejects changed adapter position, generation, attachment and following state",
            MessageDetailsScrollSupport.delayedRowStillMatches(
                7, 8, messageA, messageA, false, false, false, true, false
            ))
    }

    @Test
    fun `container and method resolution are cached per host class including missing methods`() {
        val list = CacheProbeList(context)
        val beforeType = MessageDetailsScrollSupport.cacheStats().containerTypeResolutions
        repeat(64) { assertEquals(MessageDetailsScrollSupport.ContainerKind.ABS_LIST,
            MessageDetailsScrollSupport.containerKind(list)) }
        assertEquals("runtime type classification is resolved once for the concrete ListView class",
            beforeType + 1, MessageDetailsScrollSupport.cacheStats().containerTypeResolutions)

        val beforeMethod = MessageDetailsScrollSupport.cacheStats().methodResolutions
        val method = MessageDetailsScrollSupport.hostMethod(list, "acceptanceProbe", View::class.java)
        repeat(64) {
            assertTrue(method === MessageDetailsScrollSupport.hostMethod(list, "acceptanceProbe", View::class.java))
        }
        assertEquals("method reflection occurs once for a class and signature",
            beforeMethod + 1, MessageDetailsScrollSupport.cacheStats().methodResolutions)

        val beforeMissing = MessageDetailsScrollSupport.cacheStats().methodResolutions
        repeat(64) { assertEquals(null, MessageDetailsScrollSupport.hostMethod(list, "missingAcceptanceMethod")) }
        assertEquals("negative reflection results are cached without pretending a Hook was installed",
            beforeMissing + 1, MessageDetailsScrollSupport.cacheStats().methodResolutions)
    }

    @Test
    fun `newer adapter message rejects non-tail row before reading its identity`() {
        val probedPositions = mutableListOf<Int>()
        val latest = MessageDetailsScrollSupport.isConfirmedLatestMessage(
            position = 7,
            itemCount = 10,
            itemAt = { position ->
                probedPositions += position
                MessageDetailsScrollSupport.ItemProbe(true, if (position == 8) "message:B" else "message:A")
            },
            isMessageItem = { it is String && it.startsWith("message:") },
            candidateMatchesIdentity = { it == "message:A" }
        )
        assertFalse(latest)
        assertEquals("the fast path stops at the first unseen newer message", listOf(8), probedPositions)
    }

    @Test
    fun `isolated ScrollView geometry fixture exercises delayed validation without production tail authorization`() {
        MessageDetailsFollowLatestRegistry.clear()
        val stale = overflowFixture()
        try {
            MessageDetailsFollowLatestRegistry.observe(stale.scroll)
            val identity = identity("A", 77L)
            var currentIdentity = identity
            remember(stale, identity, currentIdentity = { currentIdentity }) { true }
            currentIdentity = identity("B", 78L) // The holder reuses position zero for B.
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("an A callback is inert after adapter identity changes", 0, stale.scroll.scrollY)
            assertTrue("the row and timestamp are measured inside a real Android view tree",
                stale.label.bottom <= stale.row.measuredHeight)
        } finally {
            MessageDetailsFollowLatestRegistry.clear()
            stale.controller.pause().stop().destroy()
        }

        MessageDetailsFollowLatestRegistry.clear()
        val current = overflowFixture()
        try {
            MessageDetailsFollowLatestRegistry.observe(current.scroll)
            assertTrue("initial tail state is captured before growth",
                MessageDetailsFollowLatestRegistry.isFollowingLatest(current.scroll))
            val identity = identity("B", 78L)
            var validated = false
            remember(current, identity) { validated = true; true }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("the queued correction revalidates its live row", validated)
            assertTrue("a still-current confirmed tail row receives one viewport correction",
                current.scroll.scrollY > 0)
            assertEquals("the complete measured timestamp is visible after correction",
                current.label.height, visibleHeightWithin(current.label))
        } finally {
            MessageDetailsFollowLatestRegistry.clear()
            current.controller.pause().stop().destroy()
        }
    }

    @Test
    fun `detach and feature clear invalidate queued correction callbacks`() {
        MessageDetailsFollowLatestRegistry.clear()
        val fixture = overflowFixture()
        try {
            MessageDetailsFollowLatestRegistry.observe(fixture.scroll)
            val identity = identity("A", 79L)
            var latest = true
            remember(fixture, identity) { latest }
            fixture.activity.setContentView(View(context))
            latest = false
            shadowOf(Looper.getMainLooper()).idle()
            fixture.activity.setContentView(fixture.scroll)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("detach and reattach do not revive a stale message callback", 0, fixture.scroll.scrollY)

            latest = true
            remember(fixture, identity) { latest }
            MessageDetailsFollowLatestRegistry.clear() // disable/page exit owns this cleanup path
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("clearing on disable/page exit cancels queued correction", 0, fixture.scroll.scrollY)
        } finally {
            MessageDetailsFollowLatestRegistry.clear()
            fixture.controller.pause().stop().destroy()
        }
    }

    private fun isLatest(adapter: MessageFixtureAdapter, position: Int): Boolean =
        MessageDetailsScrollSupport.isConfirmedLatestMessage(
            position = position,
            itemCount = adapter.itemCount,
            itemAt = { itemPosition ->
                if (itemPosition in 0 until adapter.itemCount) {
                    MessageDetailsScrollSupport.ItemProbe(true, adapter.valueAt(itemPosition))
                } else {
                    MessageDetailsScrollSupport.ItemProbe(false, null)
                }
            },
            isMessageItem = { it is String && it.startsWith("message:") },
            candidateMatchesIdentity = { it is String && it.startsWith("message:") }
        )

    private fun identity(id: String, localId: Long) = MessageDetailsIdentity(
        talker = "filehelper", localId = localId, serverId = 0L, createTime = 100L,
        type = 1, isSelf = true, fallbackMessage = id
    )

    private data class ScrollFixture(
        val controller: org.robolectric.android.controller.ActivityController<Activity>,
        val activity: Activity,
        val scroll: ScrollView,
        val row: LinearLayout,
        val label: TextView
    )

    private fun overflowFixture(): ScrollFixture {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val scroll = ScrollView(activity)
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(View(activity), LinearLayout.LayoutParams(1, 40))
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        activity.setContentView(scroll)
        measureAndLayout(scroll, 240, 100)
        // Capture the native tail-follow state before a new row changes the scroll range.
        MessageDetailsFollowLatestRegistry.observe(scroll)

        val spacer = View(activity)
        content.addView(spacer, LinearLayout.LayoutParams(1, 140))
        val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        row.addView(View(activity), LinearLayout.LayoutParams(1, 90))
        val label = TextView(activity).apply {
            text = "21:44:08"
            minHeight = 30
            textSize = 16f
        }
        row.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30))
        content.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        measureAndLayout(scroll, 240, 100)
        assertTrue("fixture creates real measured overflow", MessageDetailsFollowLatestScroll.overflowDistance(scroll, row, label) > 0)
        assertTrue("fixture starts with an attached row", scroll.isAttachedToWindow && row.isAttachedToWindow)
        return ScrollFixture(controller, activity, scroll, row, label)
    }

    private fun remember(
        fixture: ScrollFixture,
        identity: MessageDetailsIdentity,
        currentIdentity: () -> MessageDetailsIdentity = { identity },
        latest: () -> Boolean
    ) {
        val confirmation = MessageDetailsTailConfirmation(
            position = 0,
            identity = identity,
            bindIsCurrent = true,
            rowIsAttached = true,
            featureEnabled = true,
            latestMessageConfirmed = latest()
        )
        MessageDetailsFollowLatestRegistry.rememberTailRow(
            fixture.scroll,
            fixture.row,
            fixture.label,
            confirmation
        ) {
            MessageDetailsTailConfirmation(
                position = 0,
                identity = currentIdentity(),
                bindIsCurrent = true,
                rowIsAttached = fixture.row.isAttachedToWindow,
                featureEnabled = true,
                latestMessageConfirmed = latest()
            )
        }
    }

    private fun measureAndLayout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
    }

    private fun visibleHeightWithin(descendant: View): Int {
        val bounds = android.graphics.Rect()
        return if (descendant.getGlobalVisibleRect(bounds)) bounds.height() else 0
    }

    private class MessageFixtureAdapter(private val values: MutableList<String?>) :
        RecyclerView.Adapter<MessageFixtureAdapter.Row>() {
        class Row(val label: TextView) : RecyclerView.ViewHolder(label)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Row(TextView(parent.context))
        override fun onBindViewHolder(holder: Row, position: Int) {
            holder.label.text = values[position] ?: "footer"
            holder.label.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40)
        }
        override fun getItemCount(): Int = values.size
        override fun getItemViewType(position: Int): Int =
            if (values[position]?.startsWith("message:") == true) 0 else 1
        fun valueAt(position: Int): String? = values[position]
    }

    private class HeightRecyclerAdapter(
        private val rowCount: Int,
        var lastRowHeight: Int
    ) : RecyclerView.Adapter<HeightRecyclerAdapter.Row>() {
        class Row(val label: TextView) : RecyclerView.ViewHolder(label)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Row(TextView(parent.context))
        override fun onBindViewHolder(holder: Row, position: Int) {
            holder.label.text = "21:45:${position.toString().padStart(2, '0')}"
            holder.label.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (position == rowCount - 1) lastRowHeight else 40
            )
        }
        override fun getItemCount(): Int = rowCount
    }

    private class HeightListAdapter(
        private val rowCount: Int,
        var lastRowHeight: Int
    ) : BaseAdapter() {
        override fun getCount(): Int = rowCount
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            (convertView as? TextView ?: TextView(parent.context)).apply {
                text = "21:46:${position.toString().padStart(2, '0')}"
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (position == rowCount - 1) lastRowHeight else 40
                )
            }
    }
}
