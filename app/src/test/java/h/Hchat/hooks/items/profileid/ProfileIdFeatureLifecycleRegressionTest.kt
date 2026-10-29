package h.Hchat.hooks.items.profileid

import android.content.Context
import android.os.Looper
import com.tencent.mm.chatroom.ui.ChatroomInfoUI
import com.tencent.mm.plugin.profile.ui.ContactInfoUI
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.core.XposedRuntime
import h.Hchat.tests.api102.FrameworkFixture
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileIdFeatureLifecycleRegressionTest {
    private val registry get() = HookRegistry.get()
    private lateinit var loader: DelayedProfileClassLoader

    @Before
    fun setUp() {
        try {
            XposedRuntime.get()
        } catch (_: IllegalStateException) {
            registrationFixture = FrameworkFixture()
            XposedRuntime.initialize(requireNotNull(registrationFixture).create())
        }
        registry.unhookAll()
        loader = DelayedProfileClassLoader(requireNotNull(javaClass.classLoader))
    }

    @After
    fun cleanUp() {
        DexInstallScheduler.cancelAll()
        registry.unhookAll()
    }

    @Test
    fun `a missing profile target that later becomes available retries through production install`() {
        val context: Context = RuntimeEnvironment.getApplication()

        val first = ProfileIdFeature.install(context, loader)
        assertFalse(first.contactInstalled)
        assertFalse(first.chatroomInstalled)
        assertEquals("initially missing pages must install no hooks", 0, registry.size())

        loader.contactAvailable = true
        loader.chatroomAvailable = true
        assertEquals(
            ContactInfoUI::class.java,
            Class.forName("com.tencent.mm.plugin.profile.ui.ContactInfoUI", false, loader)
        )
        assertEquals(
            ChatroomInfoUI::class.java,
            Class.forName("com.tencent.mm.chatroom.ui.ChatroomInfoUI", false, loader)
        )
        val retried = ProfileIdFeature.install(context, loader)
        assertTrue(diagnostic(retried), retried.contactInstalled)
        assertTrue(diagnostic(retried), retried.chatroomInstalled)

        assertEquals(
            "production install must retry both profile targets after the class loader gains them",
            3,
            registry.size()
        )
    }

    @Test
    fun `warmup scheduler retries each target after its initial locator misses`() {
        val context = RuntimeEnvironment.getApplication()
        val first = ProfileIdFeature.install(context, loader)
        assertFalse(first.contactInstalled)
        assertFalse(first.chatroomInstalled)
        assertEquals(0, registry.size())

        DexInstallScheduler.markDexWarmupReady()
        awaitCondition("both scheduler tasks must observe the initial miss") {
            loader.contactMisses.get() >= 2 && loader.chatroomMisses.get() >= 2
        }

        loader.contactAvailable = true
        loader.chatroomAvailable = true
        awaitCondition("finite scheduler retries should install both profile targets") {
            registry.size() == 3
        }
    }

    @Test
    fun `a successful contact hook does not mask a missing chatroom hook`() {
        val context = RuntimeEnvironment.getApplication()
        loader.contactAvailable = true

        val partial = ProfileIdFeature.install(context, loader)

        assertTrue(diagnostic(partial), partial.contactInstalled)
        assertFalse(partial.chatroomInstalled)
        assertEquals("only the contact initView hook should be active", 1, registry.size())

        loader.chatroomAvailable = true
        val completed = ProfileIdFeature.install(context, loader)
        assertTrue(completed.contactInstalled)
        assertTrue(completed.chatroomInstalled)
        assertEquals("retry should add only the two missing chatroom hooks", 3, registry.size())
    }

    @Test
    fun `duplicate install is idempotent and can recover after registry invalidation`() {
        val context = RuntimeEnvironment.getApplication()
        loader.contactAvailable = true
        loader.chatroomAvailable = true

        assertTrue(ProfileIdFeature.install(context, loader).contactInstalled)
        assertTrue(ProfileIdFeature.install(context, loader).chatroomInstalled)
        assertEquals(3, registry.size())

        ProfileIdFeature.install(context, loader)
        assertEquals("repeated installs must reuse active HookRegistry handles", 3, registry.size())

        registry.unhookAll()
        assertEquals(0, registry.size())

        val restored = ProfileIdFeature.install(context, loader)
        assertTrue(restored.contactInstalled)
        assertTrue(restored.chatroomInstalled)
        assertEquals("a new registry generation must accept the profile hooks again", 3, registry.size())
    }

    private class DelayedProfileClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        var contactAvailable = false
        var chatroomAvailable = false
        val contactMisses = AtomicInteger()
        val chatroomMisses = AtomicInteger()

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name == "com.tencent.mm.plugin.profile.ui.ContactInfoUI" ||
                name == "com.tencent.mm.chatroom.ui.ChatroomInfoUI"
            ) {
                val available = if (name == "com.tencent.mm.plugin.profile.ui.ContactInfoUI") {
                    contactAvailable
                } else {
                    chatroomAvailable
                }
                if (!available) {
                    if (name == "com.tencent.mm.plugin.profile.ui.ContactInfoUI") contactMisses.incrementAndGet()
                    else chatroomMisses.incrementAndGet()
                    throw ClassNotFoundException(name)
                }
            }
            return super.loadClass(name, resolve)
        }
    }

    private fun awaitCondition(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(10)
        }
        assertTrue(description, condition())
    }

    private fun diagnostic(result: ProfileIdInstallResult): String =
        "contact=${result.contactInstalled}, room=${result.chatroomInstalled}, " +
            "handles=${registry.size()}, logs=${registrationFixture?.logs?.map { it.message }}"

    companion object {
        private var registrationFixture: FrameworkFixture? = null
    }
}
