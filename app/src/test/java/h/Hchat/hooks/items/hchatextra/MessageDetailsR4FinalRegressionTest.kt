package h.Hchat.hooks.items.hchatextra

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.core.XposedRuntime
import h.Hchat.tests.api102.ChainFixture
import h.Hchat.tests.api102.FrameworkFixture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap

/** Calls the private production entry points; construction is bypassed to avoid host settings/DexKit. */
internal class ProductionMessageDetailsHarness {
    private val type = Class.forName("h.Hchat.hooks.items.hchatextra.HchatExtraHooker")
    private val target: Any = run {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        (field.get(null) as sun.misc.Unsafe).allocateInstance(type)
    }
    init {
        for (name in listOf("holderTimeFieldCache", "nativeMessageClassCache", "messageNestedFieldCache", "messageAccessorCache")) {
            type.getDeclaredField(name).apply { isAccessible = true }.set(target, ConcurrentHashMap<Any, Any>())
        }
        type.getDeclaredField("logger").apply { isAccessible = true }
            .set(target, { _: String, _: Throwable? -> Unit })
    }
    fun structural(container: View, adapter: Any, position: Int): Boolean =
        type.getDeclaredMethod("messageDetailsKnownStructuralRow", View::class.java, Any::class.java, Integer.TYPE)
            .apply { isAccessible = true }.invoke(target, container, adapter, position) as Boolean
    fun nativeMessage(item: Any): Any? = type.declaredMethods.single {
        it.name == "resolveNativeMessage" && it.parameterCount == 1
    }.apply { isAccessible = true }.invoke(target, item)
    fun install(container: View) {
        type.getDeclaredMethod("installMessageDetailsRecyclerScrollHook", View::class.java)
            .apply { isAccessible = true }.invoke(target, container)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageDetailsR4FinalRegressionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val registry get() = HookRegistry.get()
    private val production = ProductionMessageDetailsHarness()

    @Before fun reset() {
        XposedRuntime.initialize(framework.createOnce)
        registry.unhookAll()
        resetLegacyInstallCache() // Only isolates baseline tests; never called between install/reinstall phases.
        framework.failOn = null
        MessageDetailsFollowLatestRegistry.clear()
    }
    @After fun cleanup() {
        framework.failOn = null
        framework.handles.forEach { it.fail = false }
        registry.unhookAll()
        MessageDetailsFollowLatestRegistry.clear()
    }

    @Test fun `production rejects a newer opaque message holder without timeTV`() {
        val a = NativeMessage(1)
        val b = PendingMessage()
        val data = mutableListOf<Any>(a, b)
        val recycler = recycler(data)
        val holderB = recycler.findViewHolderForAdapterPosition(1)!!
        assertFalse(holderB.javaClass.declaredFields.any { it.name == "timeTV" })
        assertNotNull(production.nativeMessage(a))
        assertNull("B's native data is not available yet", production.nativeMessage(b))
        val latest = MessageDetailsScrollSupport.isConfirmedLatestMessage(
            0, data.size,
            itemAt = { MessageDetailsScrollSupport.ItemProbe(true, data[it]) },
            isMessageItem = { production.nativeMessage(it) != null },
            candidateMatchesIdentity = { production.nativeMessage(it) === a },
            isKnownStructuralRow = { position, _ -> production.structural(recycler, recycler.adapter!!, position) }
        )
        assertFalse("an opaque real message after A is not positive evidence of a footer", latest)
        assertFalse("production must never learn B as a structural row",
            production.structural(recycler, recycler.adapter!!, 1))
    }

    @Test fun `production accepts explicit Android footer metadata even without attached holder`() {
        val a = NativeMessage(1)
        val list = ListView(context)
        list.addHeaderView(TextView(context).apply { text = "header" })
        list.addFooterView(TextView(context).apply { text = "footer" }, Any(), false)
        list.adapter = object : BaseAdapter() {
            override fun getCount() = 1
            override fun getItem(position: Int) = a
            override fun getItemId(position: Int) = 1L
            override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
                TextView(context).apply { text = "A"; height = 40 }
        }
        val adapter = list.adapter
        assertEquals(3, adapter.count)
        assertEquals(0, list.childCount) // Positive proof comes from Android's fixed footer metadata.
        assertTrue(production.structural(list, adapter, 2))
        assertFalse("the wrapped adapter's message is not a footer", production.structural(list, adapter, 1))
        assertTrue(MessageDetailsScrollSupport.isConfirmedLatestMessage(
            1, adapter.count,
            itemAt = { MessageDetailsScrollSupport.ItemProbe(true, adapter.getItem(it)) },
            isMessageItem = { production.nativeMessage(it) != null },
            candidateMatchesIdentity = { production.nativeMessage(it) === a },
            isKnownStructuralRow = { position, _ -> production.structural(list, adapter, position) }
        ))
    }

    @Test fun `production scroll hooks reinstall active API102 callbacks after unhookAll`() {
        val recycler = recycler(MutableList(20) { NativeMessage(it + 1) })
        production.install(recycler)
        val initial = framework.handles.filter { it.active }
        assertTrue(initial.isNotEmpty())
        val before = framework.calls
        repeat(64) { production.install(recycler) }
        assertEquals("live hooks deduplicate without new framework registrations", before, framework.calls)
        exerciseInstalledPositionObserver(recycler)
        val generation = registry.installationGeneration()
        registry.unhookAll()
        assertTrue(initial.none { it.active })
        assertTrue(registry.installationGeneration() > generation)
        production.install(recycler)
        assertTrue("a new generation must contain fresh active framework handles",
            framework.handles.any { it.active && it !in initial })
        exerciseInstalledPositionObserver(recycler)
    }

    @Test fun `production scroll hooks reinstall after lease rollback and failed registration`() {
        val recycler = recycler(MutableList(20) { NativeMessage(it + 1) })
        val generation = registry.installationGeneration()
        assertTrue(runCatching {
            registry.withInstallationGeneration(generation) {
                production.install(recycler)
                error("force enclosing installation rollback")
            }
        }.isFailure)
        assertFalse(framework.handles.any { it.active })
        production.install(recycler)
        assertTrue("a rolled-back successful install must not poison the retry cache",
            framework.handles.any { it.active })
        exerciseInstalledPositionObserver(recycler)

        registry.unhookAll()
        resetLegacyInstallCache()
        val method = MessageDetailsScrollSupport.hostMethod(recycler, "scrollToPosition", Integer.TYPE)!!
        framework.failOn = method
        production.install(recycler)
        assertFalse(framework.handles.any { it.active && it.executable == method })
        framework.failOn = null
        production.install(recycler)
        assertTrue("transient registration failure can install an active callback on retry",
            framework.handles.any { it.active && it.executable == method })
        exerciseInstalledPositionObserver(recycler)
    }

    @Test fun `ordinary ScrollView has no adapter qualification and installs no automatic scroll hooks`() {
        val scroll = ScrollView(context)
        assertNull(MessageDetailsScrollSupport.adapter(scroll))
        val before = framework.calls
        production.install(scroll)
        assertEquals("ScrollView geometry support does not authorize production automatic correction",
            before, framework.calls)
    }

    private fun exerciseInstalledPositionObserver(recycler: RecyclerView) {
        MessageDetailsFollowLatestRegistry.clear()
        recycler.scrollToPosition(recycler.adapter!!.itemCount - 1)
        measure(recycler)
        assertFalse(recycler.canScrollVertically(1))
        MessageDetailsFollowLatestRegistry.observe(recycler)
        assertTrue(MessageDetailsFollowLatestRegistry.isFollowingLatest(recycler))
        val method = MessageDetailsScrollSupport.hostMethod(recycler, "scrollToPosition", Integer.TYPE)!!
        val handle = framework.handles.last { it.active && it.executable == method }
        val chain = ChainFixture(method, recycler, arrayOf<Any>(0)) { _, _ -> 17 }
        assertEquals(17, chain.run(handle.hooker))
        assertEquals(1, chain.proceeds)
        assertFalse("the installed ManagedHook really invokes the production state observer",
            MessageDetailsFollowLatestRegistry.isFollowingLatest(recycler))
    }

    private fun recycler(data: List<Any>): RecyclerView = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = object : RecyclerView.Adapter<OpaqueHolder>() {
            override fun getItemCount() = data.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = OpaqueHolder(TextView(context))
            override fun onBindViewHolder(holder: OpaqueHolder, position: Int) {
                (holder.itemView as TextView).text = if (position == 0) "A" else "B"
                holder.itemView.layoutParams = RecyclerView.LayoutParams(320, 40)
            }
        }
        measure(this)
    }
    private fun measure(view: View) {
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 320, 120)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private class OpaqueHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    private class PendingMessage
    private class NativeMessage(id: Int) {
        @JvmField val field_msgId = id.toLong()
        @JvmField val field_msgSvrId = 0L
        @JvmField val field_type = 1
        @JvmField val field_content = "test"
    }
    private fun resetLegacyInstallCache() {
        runCatching { Class.forName("h.Hchat.hooks.items.hchatextra.MessageDetailsScrollHookInstallState") }
            .getOrNull()?.let { type ->
                val instance = type.getField("INSTANCE").get(null)
                for (name in listOf("containerClasses", "hookedMethods")) {
                    (type.getDeclaredField(name).apply { isAccessible = true }.get(instance) as MutableSet<*>).clear()
                }
            }
    }
    companion object {
        private val framework = FrameworkState()
    }
    private class FrameworkState {
        private val value = FrameworkFixture()
        val createOnce = value.create()
        val handles get() = value.handles
        val calls get() = value.calls
        var failOn: java.lang.reflect.Executable?
            get() = value.failOn
            set(value) { this.value.failOn = value }
    }
}
