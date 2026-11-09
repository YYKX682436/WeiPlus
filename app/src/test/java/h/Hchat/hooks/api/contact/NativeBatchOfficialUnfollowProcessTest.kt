package h.Hchat.hooks.api.contact

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBatchOfficialUnfollowProcessTest {
    @Test
    fun nativeQueueObserverReleasesWaiterAfterSceneProcessing() {
        val scene = Any()
        val completed = CountDownLatch(1)
        val observed = AtomicBoolean(false)
        val errorType = AtomicInteger(Int.MIN_VALUE)
        val errorCode = AtomicInteger(Int.MIN_VALUE)

        val recorded = NativeBatchOfficialUnfollowProcess.recordQueueCallback(
            scene,
            "onSceneEnd",
            arrayOf<Any?>(0, 0, "", scene),
            completed,
            observed,
            errorType,
            errorCode
        )

        assertTrue(recorded)
        assertTrue(completed.await(1, TimeUnit.MILLISECONDS))
        assertTrue(observed.get())
        assertEquals(0, errorType.get())
        assertEquals(0, errorCode.get())
    }

    @Test
    fun unrelatedAndMalformedQueueCallbacksCannotCompleteRequest() {
        val completed = CountDownLatch(1)
        val observed = AtomicBoolean(false)
        val errorType = AtomicInteger(Int.MIN_VALUE)
        val errorCode = AtomicInteger(Int.MIN_VALUE)

        assertFalse(
            NativeBatchOfficialUnfollowProcess.recordQueueCallback(
                Any(),
                "onSceneEnd",
                arrayOf<Any?>(0, 0, "", Any()),
                completed,
                observed,
                errorType,
                errorCode
            )
        )
        val scene = Any()
        assertFalse(
            NativeBatchOfficialUnfollowProcess.recordQueueCallback(
                scene,
                "onSceneEnd",
                arrayOf<Any?>(0, 0),
                completed,
                observed,
                errorType,
                errorCode
            )
        )
        assertFalse(completed.await(1, TimeUnit.MILLISECONDS))
        assertFalse(observed.get())
    }

    @Test
    fun observerReadFailureDoesNotPreventCompletionCallback() {
        val scene = Any()
        val completed = CountDownLatch(1)
        val observed = AtomicBoolean(false)
        val errorType = AtomicInteger(Int.MIN_VALUE)
        val errorCode = AtomicInteger(Int.MIN_VALUE)

        assertFalse(
            NativeBatchOfficialUnfollowProcess.runObserverSafely(
                { throw IllegalStateException("bad native field") },
                null,
                "测试结果"
            )
        )
        assertTrue(
            NativeBatchOfficialUnfollowProcess.recordQueueCallback(
                scene,
                "onSceneEnd",
                arrayOf<Any?>(0, 0, "", scene),
                completed,
                observed,
                errorType,
                errorCode
            )
        )

        assertTrue(completed.await(1, TimeUnit.MILLISECONDS))
        assertTrue(observed.get())
        assertEquals(0, errorType.get())
        assertEquals(0, errorCode.get())
    }
}
