package h.Hchat.hooks.items.hchatextra

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.ui.WeChatChatPageApi
import h.Hchat.hooks.api.ui.WeChatCurrentActivityApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** Regressions for R5. Layout checks invoke HchatExtraHooker's production bind entry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageDetailsR5RegressionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `avatar detail remains attached to its anchor after host moves it in nested padded layout`() {
        val harness = ProductionAvatarBindHarness()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { addView(rows) }
        activity.setContentView(scroll)

        val row = createRow()
        rows.addView(row.root, LinearLayout.LayoutParams(320, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout(activity)
        bind(harness, row, id = 41)
        layout(activity)
        val initialAvatar = boundsIn(row.root, row.avatar)
        val initialLabel = boundsIn(row.root, label(row.root))
        assertEquals("zero gap starts directly below the real nested avatar", 0, initialLabel.top - initialAvatar.bottom)

        val avatarParams = row.avatar.layoutParams as FrameLayout.LayoutParams
        avatarParams.topMargin += 28
        row.avatar.layoutParams = avatarParams
        row.avatar.requestLayout()
        layout(activity)
        shadowOf(Looper.getMainLooper()).idle()
        layout(activity)

        val movedAvatar = boundsIn(row.root, row.avatar)
        val movedLabel = boundsIn(row.root, label(row.root))
        assertEquals("the timestamp follows the avatar after the host branch is remeasured", 0,
            movedLabel.top - movedAvatar.bottom)
        assertEquals("both moved by the same host geometry delta", 28,
            movedLabel.top - initialLabel.top)
        assertTrue("parent padding remains native layout input", row.root.paddingTop > 0)

        val heightBeforeHostGrowth = row.root.height
        val branch = row.root.getChildAt(0)
        val branchParams = branch.layoutParams as RelativeLayout.LayoutParams
        branchParams.height += 24
        branch.layoutParams = branchParams
        layout(activity)
        shadowOf(Looper.getMainLooper()).idle()
        layout(activity)
        assertEquals("a later host row-height change preserves the zero avatar gap", 0,
            boundsIn(row.root, label(row.root)).top - boundsIn(row.root, row.avatar).bottom)
        assertEquals("the host row keeps its own measured height change", 24, row.root.height - heightBeforeHostGrowth)
    }

    @Test
    fun `avatar above preserves a nonzero configured gap after host geometry changes`() {
        val gapDp = 5
        val harness = ProductionAvatarBindHarness(position = "avatar_above", gapDp = gapDp)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        activity.setContentView(ScrollView(context).apply { addView(rows) })
        val row = createRow(avatarTop = 62)
        rows.addView(row.root, LinearLayout.LayoutParams(320, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout(activity)
        bind(harness, row, id = 55)
        layout(activity)
        val label = label(row.root)
        val gap = (gapDp * context.resources.displayMetrics.density + 0.5f).toInt()
        val initialAvatar = boundsIn(row.root, row.avatar)
        val initialLabel = boundsIn(row.root, label)
        assertEquals("avatar-above placement honors dp gap", gap, initialAvatar.top - initialLabel.bottom)

        val params = row.avatar.layoutParams as FrameLayout.LayoutParams
        params.topMargin += 17
        row.avatar.layoutParams = params
        layout(activity)
        shadowOf(Looper.getMainLooper()).idle()
        layout(activity)

        assertEquals("layout resynchronization preserves the same nonzero gap", gap,
            boundsIn(row.root, row.avatar).top - boundsIn(row.root, label).bottom)
        assertEquals("the label follows the updated avatar rather than its previous coordinate", 17,
            boundsIn(row.root, label).top - initialLabel.top)
    }

    @Test
    fun `repeated bind and A to B reuse preserve adjacent row geometry and current display data`() {
        val harness = ProductionAvatarBindHarness()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        activity.setContentView(ScrollView(context).apply { addView(rows) })
        val a = createRow(avatarTop = 18)
        val b = createRow(avatarTop = 31)
        rows.addView(a.root, LinearLayout.LayoutParams(320, ViewGroup.LayoutParams.WRAP_CONTENT))
        rows.addView(b.root, LinearLayout.LayoutParams(320, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout(activity)

        bind(harness, a, id = 101)
        bind(harness, b, id = 202)
        layout(activity)
        val aLabel = label(a.root)
        val bLabel = label(b.root)
        repeat(20) { bind(harness, a, id = 101) }
        layout(activity)

        assertSame("same-row payload binds reuse the one owned label", aLabel, label(a.root))
        assertEquals(0, boundsIn(a.root, aLabel).top - boundsIn(a.root, a.avatar).bottom)
        assertEquals(0, boundsIn(b.root, bLabel).top - boundsIn(b.root, b.avatar).bottom)

        val replacementAvatar = View(context)
        val deepestParent = deepestFrame(a.root)
        deepestParent.addView(replacementAvatar, FrameLayout.LayoutParams(48, 48).apply {
            leftMargin = 50
            topMargin = 66
        })
        layout(activity)
        bind(harness, Row(a.root, replacementAvatar, Holder(replacementAvatar)), id = 303)
        layout(activity)
        assertSame("A to B holder reuse keeps the row-owned label", aLabel, label(a.root))
        assertEquals("the text follows B's display fields", "303", aLabel.text.toString())
        assertEquals("the label is positioned from B's current avatar", 0,
            boundsIn(a.root, aLabel).top - boundsIn(a.root, replacementAvatar).bottom)
        assertEquals("the adjacent row remains independent", 0,
            boundsIn(b.root, bLabel).top - boundsIn(b.root, b.avatar).bottom)
    }

    @Test
    fun `page backfill waits for attached focused populated RecyclerView and cancels stale lifecycle work`() {
        val harness = ProductionAvatarBindHarness()
        val activity = Robolectric.buildActivity(FocusActivity::class.java).setup().get()
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = FrameLayout.LayoutParams(320, 180)
        }
        val adapter = CountingAdapter()
        recycler.adapter = adapter
        activity.setContentView(FrameLayout(context).apply { addView(recycler) })
        layout(activity)
        assertTrue("the real RecyclerView has visible Android children", recycler.childCount > 0)
        assertNotNull("the production visible-range fallback reads the real LayoutManager",
            MessageDetailsScrollSupport.visibleAdapterRange(recycler))
        assertTrue("the production child-position lookup resolves attached rows through AndroidX APIs",
            (0 until recycler.childCount).mapNotNull { index ->
                MessageDetailsScrollSupport.childAdapterPosition(recycler, recycler.getChildAt(index))
            }.isNotEmpty())
        val oldActivityApi = staticField("currentActivityApi").get(null)
        val oldChatPageApi = staticField("chatPageApi").get(null)
        try {
            val activityApi = WeChatCurrentActivityApi(null).apply { updateCurrentActivity(activity) }
            val chatPageApi = WeChatChatPageApi(null, null, null, null, null, null)
            WeChatChatPageApi::class.java.getDeclaredMethod("dispatchEnter", String::class.java, String::class.java)
                .apply { isAccessible = true }.invoke(chatPageApi, "filehelper", "test-chat")
            staticField("currentActivityApi").set(null, activityApi)
            staticField("chatPageApi").set(null, chatPageApi)
            harness.prepareBackfill(CountingAdapter::class.java)

            activity.focused = false
            harness.scheduleInitialBackfill()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("late hook installation before window focus must wait", 0, adapter.rangeChangeCount)

            activity.focused = true
            harness.dispatchPendingFocus()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("the focused, ready chat triggers exactly one visible-range rebind", 1,
                adapter.rangeChangeCount)
            assertTrue("the visible range is narrower than the full adapter", adapter.lastRangeCount < adapter.itemCount)

            activity.focused = false
            harness.advancePageGeneration()
            harness.scheduleInitialBackfill()
            harness.advancePageGeneration() // chat exit invalidates the queued request
            activity.focused = true
            harness.dispatchPendingFocus()
            assertEquals("page exit invalidates a queued backfill", 1, adapter.rangeChangeCount)

            activity.focused = false
            harness.scheduleInitialBackfill()
            harness.setEnabled(false) // disable invalidates the same queued path
            activity.focused = true
            harness.dispatchPendingFocus()
            assertEquals("feature disable invalidates the queued backfill", 1, adapter.rangeChangeCount)

            harness.setEnabled(true)
            harness.advancePageGeneration() // a later page entry gets a fresh one-shot request
            activity.focused = false
            harness.scheduleInitialBackfill()
            activity.focused = true
            harness.dispatchPendingFocus()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("re-entry creates one fresh visible-only backfill", 2, adapter.rangeChangeCount)
        } finally {
            staticField("currentActivityApi").set(null, oldActivityApi)
            staticField("chatPageApi").set(null, oldChatPageApi)
        }
    }

    @Test
    fun `late hook readiness always performs adapter refresh on the Android main thread`() {
        val activity = Robolectric.buildActivity(FocusActivity::class.java).setup().get()
        val decor = activity.window.decorView
        assertTrue("the fixture uses an attached real Activity decor", decor.isAttachedToWindow)
        val callbackThread = AtomicReference<Thread?>()
        val gate = MessageDetailsPageReadyGate()
        val installer = Thread {
            gate.schedule(
                decor,
                isCurrent = { true },
                tryBackfill = {
                    callbackThread.set(Thread.currentThread())
                    true
                }
            )
        }
        installer.start()
        installer.join()
        assertNull("worker-thread installation must enqueue the ready attempt", callbackThread.get())
        shadowOf(Looper.getMainLooper()).idle()

        assertSame(
            "a Dex/hook install thread must never invoke View or adapter work directly",
            Looper.getMainLooper().thread,
            callbackThread.get()
        )
        gate.cancel()
    }

    @Test
    fun `production cold-entry scheduling accesses Activity Window and adapter only on main and drops stale generation`() {
        val activity = Robolectric.buildActivity(ThreadTrackingActivity::class.java).setup().get()
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = FrameLayout.LayoutParams(320, 180)
        }
        val adapter = CountingAdapter()
        recycler.adapter = adapter
        activity.setContentView(FrameLayout(context).apply { addView(recycler) })
        layout(activity)
        assertTrue("the real RecyclerView has visible Android children", recycler.childCount > 0)

        val oldActivityApi = staticField("currentActivityApi").get(null)
        val oldChatPageApi = staticField("chatPageApi").get(null)
        try {
            val activityApi = WeChatCurrentActivityApi(null).apply { updateCurrentActivity(activity) }
            val chatPageApi = WeChatChatPageApi(null, null, null, null, null, null)
            WeChatChatPageApi::class.java.getDeclaredMethod("dispatchEnter", String::class.java, String::class.java)
                .apply { isAccessible = true }.invoke(chatPageApi, "filehelper", "thread-test")
            staticField("currentActivityApi").set(null, activityApi)
            staticField("chatPageApi").set(null, chatPageApi)

            val harness = ProductionAvatarBindHarness()
            harness.prepareBackfill(CountingAdapter::class.java)
            activity.clearAccessThreads()
            val staleInstaller = Thread { harness.scheduleInitialBackfill() }
            staleInstaller.start()
            staleInstaller.join()
            assertTrue("background production entry must not touch Activity or Window before main runs",
                activity.accessThreads().isEmpty())

            harness.advancePageGeneration()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("a queued request from an old page generation must be discarded before Activity/Window access",
                activity.accessThreads().isEmpty())
            assertEquals("the stale request must not notify the adapter", 0, adapter.rangeChangeCount)

            activity.clearAccessThreads()
            val currentInstaller = Thread { harness.scheduleInitialBackfill() }
            currentInstaller.start()
            currentInstaller.join()
            assertTrue("the valid background request only queues main-thread work",
                activity.accessThreads().isEmpty())
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue("Activity/Window access occurs after dispatch on the main thread",
                activity.accessThreads().isNotEmpty() && activity.accessThreads().all {
                    it === Looper.getMainLooper().thread
                })
            assertEquals("the valid page gets one visible-range adapter notification", 1,
                adapter.rangeChangeCount)
            assertSame("the final RecyclerView adapter notification runs on main",
                Looper.getMainLooper().thread, adapter.lastRangeChangeThread)
        } finally {
            staticField("currentActivityApi").set(null, oldActivityApi)
            staticField("chatPageApi").set(null, oldChatPageApi)
        }
    }

    @Test
    fun `wrapped host adapter is recognized by runtime adapter identity`() {
        val harness = ProductionAvatarBindHarness()
        val dataAdapter = CountingAdapter()
        val wrapper = AdapterWrapper(dataAdapter)
        assertSame("the declared field type may be a RecyclerView.Adapter supertype", dataAdapter,
            harness.chattingAdapterFrom(wrapper, CountingAdapter::class.java))
    }

    private fun bind(harness: ProductionAvatarBindHarness, row: Row, id: Long) {
        assertTrue("production message-details insertion succeeds", harness.bind(
            row.root, row.holder, harness.details(id)
        ))
    }

    private fun createRow(avatarTop: Int = 24): Row {
        val root = RelativeLayout(context).apply { setPadding(7, 9, 5, 3) }
        val branch = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(branch, RelativeLayout.LayoutParams(320, 150).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
        })
        var current: ViewGroup = branch
        repeat(4) {
            val nested = FrameLayout(context)
            current.addView(nested, LinearLayout.LayoutParams(320, 150))
            current = nested
        }
        val avatar = View(context)
        current.addView(avatar, FrameLayout.LayoutParams(48, 48).apply {
            leftMargin = 52
            topMargin = avatarTop
        })
        return Row(root, avatar, Holder(avatar))
    }

    private fun deepestFrame(root: View): FrameLayout {
        var deepest: FrameLayout? = null
        fun visit(view: View) {
            if (view is FrameLayout) deepest = view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(root)
        return deepest ?: error("frame missing")
    }

    private fun label(root: View): TextView {
        if (root is TextView && root.tag == "hchat_message_details_view") return root
        if (root !is ViewGroup) error("label missing")
        for (index in 0 until root.childCount) runCatching { label(root.getChildAt(index)) }
            .getOrNull()?.let { return it }
        error("label missing")
    }

    private fun boundsIn(parent: ViewGroup, child: View): Rect = Rect(0, 0, child.width, child.height).also {
        parent.offsetDescendantRectToMyCoords(child, it)
    }

    private fun layout(activity: Activity) {
        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
        )
        decor.layout(0, 0, 320, 640)
        decor.viewTreeObserver.dispatchOnGlobalLayout()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun staticField(name: String) = WeChatApis::class.java.getDeclaredField(name).apply { isAccessible = true }

    private data class Row(val root: RelativeLayout, val avatar: View, val holder: Holder)
    private class Holder(@JvmField val avatarIV: View)

    private class FocusActivity : Activity() {
        var focused = true
        override fun hasWindowFocus(): Boolean = focused
    }

    private class ThreadTrackingActivity : Activity() {
        private val accessed = Collections.synchronizedList(mutableListOf<Thread>())
        var focused = true
        override fun getWindow(): android.view.Window {
            accessed.add(Thread.currentThread())
            return super.getWindow()
        }
        override fun hasWindowFocus(): Boolean {
            accessed.add(Thread.currentThread())
            return focused
        }
        fun accessThreads(): List<Thread> = synchronized(accessed) { accessed.toList() }
        fun clearAccessThreads() = accessed.clear()
    }

    private class CountingAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val rows = MutableList(12) { it }
        var rangeChangeCount = 0
        var lastRangeCount = 0
        var lastRangeChangeThread: Thread? = null
        init {
            registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                    rangeChangeCount++
                    lastRangeCount = itemCount
                    lastRangeChangeThread = Thread.currentThread()
                }
            })
        }
        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(View(parent.context)) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder.itemView.layoutParams = RecyclerView.LayoutParams(320, 60)
        }
    }

    private class AdapterWrapper(@JvmField val contentAdapter: RecyclerView.Adapter<*>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(View(parent.context)) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }

    /** Production HchatExtraHooker entry, allocated without unrelated host/DexKit setup. */
    internal class ProductionAvatarBindHarness(
        private val position: String = "avatar_below",
        private val gapDp: Int = 0,
        private val format: String = "\${msgId}"
    ) {
        private val type = Class.forName("h.Hchat.hooks.items.hchatextra.HchatExtraHooker")
        private val target: Any = run {
            val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            (unsafeField.get(null) as sun.misc.Unsafe).allocateInstance(type)
        }
        private val detailsType = Class.forName("h.Hchat.hooks.items.hchatextra.MessageDetails")

        init {
            val caches = mapOf(
                "holderTimeFieldCache" to ConcurrentHashMap<Any, Any>(),
                "holderAvatarFieldCache" to ConcurrentHashMap<Any, Any>(),
                "nativeMessageClassCache" to ConcurrentHashMap<Any, Any>(),
                "messageNestedFieldCache" to ConcurrentHashMap<Any, Any>(),
                "messageAccessorCache" to ConcurrentHashMap<Any, Any>(),
                "holderClickAreaFieldCache" to ConcurrentHashMap<Any, Any>(),
                "holderRootFieldCache" to ConcurrentHashMap<Any, Any>(),
                "adapterItemAccessCache" to ConcurrentHashMap<Any, Any>(),
                "itemListFieldCache" to ConcurrentHashMap<Any, Any>()
            )
            caches.forEach { (name, value) -> field(name).set(target, value) }
            field("messageDetailsLabels").set(target, Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
            field("messageDetailsBindings").set(target, MessageDetailsBindingIndex<Any, Any> {})
            field("ownedAvatarBranchOffsets").set(target, MessageDetailsAvatarOffsetRegistry())
            field("messageDetailsRowLabels").set(target, MessageDetailsRowLabelIndex<View, TextView>())
            field("messageDetailsAttachCallbacks").set(target, MessageDetailsAttachQueue())
            field("messageDetailsLayoutCallbacks").set(target, MessageDetailsLayoutObserver())
            field("messageDetailsAvatarGeometryCallbacks").set(target, MessageDetailsAvatarGeometryObserver())
            field("messageDetailsRetryListeners").set(target, MessageDetailsPreDrawQueue())
            field("messageDetailsPositionListeners").set(target, MessageDetailsPreDrawQueue())
            field("messageDetailsColorListeners").set(target, MessageDetailsPreDrawQueue())
            field("messageDetailsBindTokens").set(target, WeakHashMap<View, MessageDetailsBindingGeneration>())
            field("messageDetailsScrollAttachListeners").set(target, WeakHashMap<View, View.OnAttachStateChangeListener>())
            field("messageDetailsTailChecks").set(target, MessageDetailsPreDrawQueue())
            field("logger").set(target, { _: String, _: Throwable? -> Unit })
            field("hideSelfAvatar").setBoolean(target, false)
            field("hideOtherAvatar").setBoolean(target, false)
            val config = config(enabled = true)
            field("messageDetailsConfig").set(target, config)
            field("appliedMessageDetailsConfig").set(target, config)
            field("messageDetailsPageReadyGate").set(target, MessageDetailsPageReadyGate())
            field("messageDetailsConfigHandler").set(target, Handler(Looper.getMainLooper()))
            field("messageDetailsInitialRefreshLock").set(target, Any())
        }

        fun details(id: Long, messageType: Int = 1): Any = detailsType.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(messageType, id, 0L, "filehelper", "", "", "", "", "", "", 0L, true)

        fun bind(root: View, holder: Any, details: Any): Boolean {
            val method = type.getDeclaredMethod(
                "insertMessageDetailsLabel", View::class.java, TextView::class.java, Any::class.java,
                detailsType, TextView::class.java, Any::class.java, Integer.TYPE
            ).apply { isAccessible = true }
            return method.invoke(target, root, null, holder, details, null, null, 0) as Boolean
        }

        fun bindNative(root: View, holder: Any, nativeMessage: Any, bindPosition: Int, id: Long,
                       messageType: Int = 1): Boolean {
            val method = type.getDeclaredMethod(
                "insertMessageDetailsLabel", View::class.java, TextView::class.java, Any::class.java,
                detailsType, TextView::class.java, Any::class.java, Integer.TYPE
            ).apply { isAccessible = true }
            return method.invoke(target, root, null, holder, details(id, messageType), null, nativeMessage, bindPosition) as Boolean
        }

        fun confirmation(container: View, root: View, label: TextView, bindPosition: Int): MessageDetailsTailConfirmation? {
            val token = type.getDeclaredMethod("messageDetailsBindToken", View::class.java)
                .apply { isAccessible = true }.invoke(target, root)
            return type.getDeclaredMethod(
                "currentMessageDetailsTailConfirmation", View::class.java, View::class.java, TextView::class.java,
                Integer.TYPE, Any::class.java
            ).apply { isAccessible = true }.invoke(target, container, root, label, bindPosition, token)
                as? MessageDetailsTailConfirmation
        }

        fun prepareBackfill(adapterClass: Class<*>) {
            field("chattingDataAdapterClass").set(target, adapterClass)
            field("messageDetailsPageActive").setBoolean(target, true)
            field("messageDetailsHooksInstalled").setBoolean(target, true)
            field("messageDetailsPageGeneration").setLong(target, 1L)
        }

        fun chattingAdapterFrom(source: Any, adapterClass: Class<*>): Any? {
            field("chattingDataAdapterClass").set(target, adapterClass)
            return type.getDeclaredMethod("chattingAdapterFromBindObject", Any::class.java)
                .apply { isAccessible = true }.invoke(target, source)
        }

        fun scheduleInitialBackfill() {
            type.getDeclaredMethod("scheduleMessageDetailsInitialPageRefresh")
                .apply { isAccessible = true }.invoke(target)
        }

        fun dispatchPendingFocus() {
            val gate = field("messageDetailsPageReadyGate").get(target)
            val entry = gate.javaClass.getDeclaredField("entry").apply { isAccessible = true }.get(gate)
                ?: return
            entry.javaClass.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(entry, true)
        }

        fun advancePageGeneration() {
            val generation = field("messageDetailsPageGeneration").getLong(target)
            field("messageDetailsPageGeneration").setLong(target, generation + 1)
        }

        fun setEnabled(enabled: Boolean) {
            field("messageDetailsConfig").set(target, config(enabled))
        }

        private fun config(enabled: Boolean): Any {
            val configType = Class.forName("h.Hchat.hooks.items.hchatextra.HchatExtraHooker\$MessageDetailsConfig")
            return configType.declaredConstructors.single().apply { isAccessible = true }.newInstance(
                enabled, position, format, "HH:mm:ss", if (format == "\${time}") setOf("time") else setOf("msgId"), 10f,
                gapDp, 0, 0, false, 0xff000000.toInt(), 0xff000000.toInt(), 0, 0
            )
        }

        private fun field(name: String) = type.getDeclaredField(name).apply { isAccessible = true }
    }
}
