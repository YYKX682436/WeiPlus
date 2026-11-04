package h.Hchat.hooks.items.linkparse

import android.content.Context
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.tests.api102.ChainFixture
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkParseWriteHookRegressionTest {
    class Native(val field_status: Int, val field_isSend: Int, val field_talker: String = "filehelper")
    class Host { fun write(id: Long, message: Native, notify: Boolean): Int = 1 }
    private val method = Host::class.java.getDeclaredMethod("write", Long::class.javaPrimitiveType, Native::class.java, Boolean::class.javaPrimitiveType)
    private fun msg(id: Long = 42) = WeChatMessage(id, 11, 1, 2, 1, System.currentTimeMillis(), "filehelper", "https://v.douyin.com/_WFxKTl9V78/", "", "", "", 0)

    @Test fun realInterceptorKeepsHostResultAndSharedClaimsDeduplicate() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("link-hook-r2", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val claims = MessageClaims(prefs)
        var accepted = 0
        var lookups = 0
        val hook = XposedInterface.Hooker { chain ->
            LinkParseWriteGate.intercept(chain, { true }, { id -> lookups++; msg(id) }, {
                if (claims.claim("filehelper:${it.msgId}", Long.MAX_VALUE, 1)) accepted++
            }, { fail("unexpected observer failure") })
        }
        fun invoke(result: Int, native: Native): Pair<Any?, Int> {
            val chain = ChainFixture(method, Host(), arrayOf(42L, native, true)) { _, args ->
                assertEquals(42L, args[0]); assertSame(native, args[1]); result
            }
            val actual = chain.run(hook)
            return actual to chain.proceeds
        }
        assertEquals(1 to 1, invoke(1, Native(2, 1)))
        assertEquals(1 to 1, invoke(1, Native(2, 1)))
        assertEquals(1, accepted)
        assertEquals(0 to 1, invoke(0, Native(2, 1)))
        assertEquals(1 to 1, invoke(1, Native(5, 1)))
        assertEquals(2, lookups)
        assertEquals(1, accepted)
    }

    @Test fun disabledOrUnavailableHookDoesNotStopDatabaseRouting() {
        val chain = ChainFixture(method, Host(), arrayOf(42L, Native(2, 1), true)) { _, _ -> 1 }
        val hook = XposedInterface.Hooker { c ->
            LinkParseWriteGate.intercept(c, { false }, { fail("disabled observer must not query"); null }, { fail("must not accept") }, { fail("no error expected") })
        }
        assertEquals(1, chain.run(hook))
        assertEquals(1, chain.proceeds)
        assertTrue("a successful database update remains independently eligible", LinkParseEntryPolicy.acceptDatabase(false, true, true, 2, 2))
    }

    @Test fun observerErrorsCannotChangeSuccessfulHostResult() {
        val chain = ChainFixture(method, Host(), arrayOf(42L, Native(2, 1), true)) { _, _ -> 1 }
        val hook = XposedInterface.Hooker { c ->
            LinkParseWriteGate.intercept(c, { true }, { throw IllegalStateException("lookup") }, { fail("must not accept") }, { throw IllegalStateException("logger") })
        }
        assertEquals(1, chain.run(hook))
        assertEquals(1, chain.proceeds)
    }

    @Test fun hostExceptionPropagatesWithoutLookupOrMutation() {
        val error = IllegalStateException("host failure")
        val chain = ChainFixture(method, Host(), arrayOf(42L, Native(2, 1), true)) { _, _ -> throw error }
        val hook = XposedInterface.Hooker { c ->
            LinkParseWriteGate.intercept(c, { true }, { fail("lookup must not occur"); null }, { fail("must not accept") }, { fail("host exception is not observer error") })
        }
        try { chain.run(hook); fail("host error must propagate") } catch (actual: IllegalStateException) { assertSame(error, actual) }
        assertEquals(1, chain.proceeds)
    }
}
