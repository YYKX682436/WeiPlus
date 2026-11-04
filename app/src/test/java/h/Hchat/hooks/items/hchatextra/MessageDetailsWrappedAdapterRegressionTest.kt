package h.Hchat.hooks.items.hchatextra

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Exercises HchatExtraHooker's actual renderer and tail confirmation with an outer header. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageDetailsWrappedAdapterRegressionTest {
    @After fun clear() = MessageDetailsFollowLatestRegistry.clear()

    @Test fun `inner bind position is checked against the actual outer adapter identity`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val messageA = NativeMessage(101)
        val adapter = WrappedAdapter(messageA)
        val list = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
        }
        activity.setContentView(list)
        layout(list)
        val row = list.findViewHolderForAdapterPosition(1)!!.itemView as RelativeLayout
        val avatar = row.getChildAt(0)
        val harness = MessageDetailsR5RegressionTest.ProductionAvatarBindHarness()
        assertTrue(harness.bindNative(row, NativeHolder(avatar), messageA, bindPosition = 0, id = 101))
        layout(list)
        val label = (0 until row.childCount).map(row::getChildAt).filterIsInstance<TextView>().single()

        val current = harness.confirmation(list, row, label, bindPosition = 0)
        assertNotNull("outer position 1 must not be rejected merely because the inner bind used 0", current)
        assertEquals(1, current!!.position)
        assertTrue("the actual outer item has A's identity and no newer message", current.latestMessageConfirmed)

        adapter.message = NativeMessage(202)
        val reused = harness.confirmation(list, row, label, bindPosition = 0)
        assertNotNull(reused)
        assertFalse("a different message at the same outer position never authorizes A", reused!!.latestMessageConfirmed)
        adapter.message = messageA
        adapter.newer = NativeMessage(303)
        val withNewer = harness.confirmation(list, row, label, bindPosition = 0)
        assertNotNull(withNewer)
        assertFalse("an unbound newer adapter item also rejects A", withNewer!!.latestMessageConfirmed)
    }

    @Test fun `all call types render their own native creation time through the production renderer`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val row = RelativeLayout(activity).apply {
            addView(View(activity), RelativeLayout.LayoutParams(48, 48))
        }
        activity.setContentView(row)
        layout(row)
        val avatar = row.getChildAt(0)
        val harness = MessageDetailsR5RegressionTest.ProductionAvatarBindHarness(format = "\${time}")
        for ((index, type) in listOf(50, 52, 53, 1000052, 1000053).withIndex()) {
            val id = 200L + index
            val created = 1_760_000_000_000L + index * 1_000L
            val message = NativeMessage(id, type, created)
            assertTrue(MessageDetailsVisibility.shouldShow(type))
            assertTrue(harness.bindNative(row, NativeHolder(avatar), message, 0, id, type))
            layout(row)
            val label = (0 until row.childCount).map(row::getChildAt).filterIsInstance<TextView>().single()
            val expected = Instant.ofEpochMilli(created).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            assertEquals("call type $type uses the native message time", expected, label.text.toString())
        }
        for (type in listOf(10000, 10002, 889192497, 922746929)) {
            assertFalse("system, recall and pat rows remain excluded", MessageDetailsVisibility.shouldShow(type))
        }
    }

    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 320, 180)
    }

    private class NativeHolder(@JvmField val avatarIV: View)

    private class NativeMessage(id: Long, type: Int = 1, created: Long = 1_760_000_000_000L) {
        @JvmField val field_msgId = id
        @JvmField val field_msgSvrId = id + 1000
        @JvmField val field_type = type
        @JvmField val field_content = "test"
        @JvmField val field_createTime = created
        @JvmField val field_talker = "filehelper"
        @JvmField val field_isSend = 1
    }

    private class WrappedAdapter(var message: NativeMessage) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var newer: NativeMessage? = null
        fun getItem(position: Int): Any? = when (position) {
            0 -> null // outer adapter header
            1 -> message
            2 -> newer
            else -> null
        }
        override fun getItemCount() = 2 + if (newer == null) 0 else 1
        override fun getItemViewType(position: Int) = position
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = RelativeLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(View(parent.context), RelativeLayout.LayoutParams(48, 48))
            }
            return object : RecyclerView.ViewHolder(row) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }
}
