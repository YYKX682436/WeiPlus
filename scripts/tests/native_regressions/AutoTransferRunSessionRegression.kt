package h.Hchat.hooks.items.payment.transfer

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object AutoTransferRunSessionRegression {
    fun run() {
        reproduceLegacyQueryRace()
        verifyCloseAndReopenRejectOldCallbacks()
        verifyBlockedPaymentDoesNotBlockCloseOrMainCaller()
    }

    private fun reproduceLegacyQueryRace() {
        val querySent = CountDownLatch(1)
        val callbackRelease = CountDownLatch(1)
        val legacyReceiveCalls = AtomicInteger()
        val enabled = AtomicInteger(1)
        val callback = Thread {
            querySent.countDown()
            check(callbackRelease.await(2, TimeUnit.SECONDS))
            // The old AutoTransferFeature callback unconditionally called finishReceive().
            legacyReceiveCalls.incrementAndGet()
        }
        callback.start()
        check(querySent.await(2, TimeUnit.SECONDS))
        enabled.set(0)
        callbackRelease.countDown()
        callback.join(2_000)
        check(!callback.isAlive)
        check(enabled.get() == 0 && legacyReceiveCalls.get() == 1) {
            "Legacy query callback should reproduce an operation after disable"
        }
        println("REPRODUCED: legacy query callback initiated receive after disable")
    }

    private fun verifyCloseAndReopenRejectOldCallbacks() {
        val session = AutoTransferRunSession()
        val firstGeneration = session.activate()
        val querySent = CountDownLatch(1)
        val callbackRelease = CountDownLatch(1)
        val receiveCalls = AtomicInteger()
        val refundCalls = AtomicInteger()
        val responseCacheWrites = AtomicInteger()
        val staleCallback = Thread {
            querySent.countDown()
            check(callbackRelease.await(2, TimeUnit.SECONDS))
            session.runIfCurrent(firstGeneration) {
                responseCacheWrites.incrementAndGet()
                receiveCalls.incrementAndGet()
            }
            session.runIfCurrent(firstGeneration) {
                responseCacheWrites.incrementAndGet()
                refundCalls.incrementAndGet()
            }
        }
        staleCallback.start()
        check(querySent.await(2, TimeUnit.SECONDS))

        session.invalidate()
        val secondGeneration = session.activate()
        check(secondGeneration != firstGeneration && session.isCurrent(secondGeneration))
        callbackRelease.countDown()
        staleCallback.join(2_000)

        check(!staleCallback.isAlive)
        check(receiveCalls.get() == 0) { "A stale callback initiated a receive after reopen" }
        check(refundCalls.get() == 0) { "A stale callback initiated a refund after reopen" }
        check(responseCacheWrites.get() == 0) { "A stale callback contaminated the new session's account cache" }
        check(session.isCurrent(secondGeneration)) { "Old callback invalidated the new session" }

        session.runIfCurrent(secondGeneration) { receiveCalls.incrementAndGet() }
        check(receiveCalls.get() == 1) { "Current session did not accept its own operation" }
        session.invalidate()
        println("PASS: delayed query callbacks cannot receive, refund, cache accounts, or invalidate a reopened session")
    }

    private fun verifyBlockedPaymentDoesNotBlockCloseOrMainCaller() {
        val session = AutoTransferRunSession()
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "AutoTransferRegressionWorker").apply { isDaemon = true }
        }
        val dispatcher = AutoTransferActionDispatcher(session, executor)
        val firstGeneration = session.activate()
        val apiStarted = CountDownLatch(1)
        val releaseApi = CountDownLatch(1)
        val apiCalls = AtomicInteger()
        val staleApiCalls = AtomicInteger()
        val currentApiCalls = AtomicInteger()
        val currentActionDone = CountDownLatch(1)

        try {
            check(dispatcher.dispatch(firstGeneration) {
                apiStarted.countDown()
                check(releaseApi.await(3, TimeUnit.SECONDS))
                apiCalls.incrementAndGet()
            })
            check(apiStarted.await(2, TimeUnit.SECONDS))

            val releaseTimer = Thread {
                Thread.sleep(700)
                releaseApi.countDown()
            }.apply { isDaemon = true }
            releaseTimer.start()
            val startedAt = System.nanoTime()
            session.invalidate()
            val closeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            check(closeMillis < 350L) {
                "Disabling auto-transfer waited $closeMillis ms for a blocked payment API"
            }

            val secondGeneration = session.activate()
            check(secondGeneration != firstGeneration)
            check(!dispatcher.dispatch(firstGeneration) { staleApiCalls.incrementAndGet() }) {
                "A callback from the closed session was admitted after reopen"
            }
            check(dispatcher.dispatch(secondGeneration) {
                currentApiCalls.incrementAndGet()
                currentActionDone.countDown()
            })
            releaseApi.countDown()
            check(currentActionDone.await(2, TimeUnit.SECONDS))
            check(apiCalls.get() == 1) { "The already-started fake API call did not finish exactly once" }
            check(staleApiCalls.get() == 0) { "A stale callback initiated a payment API call" }
            check(currentApiCalls.get() == 1) { "The reopened session could not execute its own action" }
            println("PASS: blocked payment API runs off the caller, close does not wait, stale work is rejected")
        } finally {
            releaseApi.countDown()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
            session.invalidate()
        }
    }
}
