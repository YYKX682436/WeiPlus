package h.Hchat.hooks.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ModuleStartupStateTest {
    @Test fun oneShotEntryInstallsBeforeABlockedIdentityPrewarmWorker() {
        val executor = Executors.newSingleThreadExecutor()
        val prewarmStarted = CountDownLatch(1)
        val releasePrewarm = CountDownLatch(1)
        val hookInstalled = AtomicBoolean(false)
        var eventReachedHook = 0
        try {
            StartupEntryDispatch.installBeforeScheduling(
                { hookInstalled.set(true) },
                {
                    executor.execute {
                        prewarmStarted.countDown()
                        check(releasePrewarm.await(5, TimeUnit.SECONDS))
                    }
                })
            assertTrue("identity prewarm should be blocked on its worker", prewarmStarted.await(2, TimeUnit.SECONDS))
            if (hookInstalled.get()) eventReachedHook++
            assertTrue("the one-time startup event must see its synchronously installed Hook", hookInstalled.get())
            assertTrue(eventReachedHook == 1)
        } finally {
            releasePrewarm.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun tinkerBeforeApplicationCannotStartInitAndEarlyEntriesInstallOnce() {
        val state = ModuleStartupState()
        val earlyCalls = AtomicInteger()

        // Tinker attach occurs before Application.onCreate.
        assertFalse(state.shouldScheduleInitialization())
        assertFalse(state.beginInitializationAttempt())

        state.markApplicationReady()
        assertTrue(state.shouldScheduleInitialization())
        listOf("first_use_agreement", "custom_bottom_bar").forEach { key ->
            assertTrue(state.installEarlyEntryOnce(key) { earlyCalls.incrementAndGet(); true })
            assertTrue(state.installEarlyEntryOnce(key) { earlyCalls.incrementAndGet(); true })
        }
        assertTrue(state.beginInitializationAttempt())
        assertFalse(state.beginInitializationAttempt())
        state.finishInitializationAttempt(false)
        assertTrue("failed init is retryable after Application readiness", state.beginInitializationAttempt())
        state.finishInitializationAttempt(true)
        assertFalse(state.shouldScheduleInitialization())
        assertTrue("each early entry is installed exactly once", earlyCalls.get() == 2)
    }

    @Test fun failedEarlyEntryCanRetryWithoutReinstallingSuccessfulEntries() {
        val state = ModuleStartupState()
        val goodCalls = AtomicInteger()
        val flakyCalls = AtomicInteger()
        assertTrue(state.installEarlyEntryOnce("good") { goodCalls.incrementAndGet(); true })
        assertFalse(state.installEarlyEntryOnce("flaky") { flakyCalls.incrementAndGet(); false })
        assertTrue(state.installEarlyEntryOnce("flaky") { flakyCalls.incrementAndGet(); true })
        assertTrue(state.installEarlyEntryOnce("good") { goodCalls.incrementAndGet(); true })
        assertTrue(goodCalls.get() == 1 && flakyCalls.get() == 2)
    }

    @Test fun failedOptionalTinkerEntrySchedulesRetryAndDoesNotBlockMainStartup() {
        val retryScheduled = AtomicBoolean(false)
        val mainEntryInstalled = AtomicBoolean(false)
        assertFalse(StartupEntryDispatch.installOptional(
            { throw IllegalStateException("injected Tinker Hook failure") },
            { retryScheduled.set(true) }
        ))
        // installPackageHooks proceeds to the independent Application entry after the optional call.
        mainEntryInstalled.set(true)
        assertTrue(retryScheduled.get())
        assertTrue(mainEntryInstalled.get())
    }
}
