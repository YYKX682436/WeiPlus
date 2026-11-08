package h.Hchat.hooks.items.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class BatchTaskManagerRegressionTest {
    @Test
    fun allFourProductionTaskKindsStartImmediatelyAndPreserveSelectedState() {
        val accepted = LinkedBlockingQueue<BatchTaskRequest>()
        val manager = BatchTaskManager.createForTesting(BatchTaskProcessor { request, _ ->
            accepted.put(request)
            BatchTaskRunner.fromResults(
                request.targetIds,
                request.targetIds.map {
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "verified by test processor")
                },
                ""
            )
        })
        val requests = listOf(
            BatchTaskRequest(BatchOperation.HIDE, listOf("chat-a"), "account-A", true),
            BatchTaskRequest(BatchOperation.DO_NOT_DISTURB, listOf("chat-b-on"), "account-A", true),
            BatchTaskRequest(BatchOperation.DO_NOT_DISTURB, listOf("chat-b"), "account-A", false),
            BatchTaskRequest(BatchOperation.PIN, listOf("chat-c-on"), "account-A", true),
            BatchTaskRequest(BatchOperation.PIN, listOf("chat-c"), "account-A", false),
            BatchTaskRequest(BatchOperation.FRIEND_LABEL, listOf("friend-d-add"), "account-A", true, "label-9"),
            BatchTaskRequest(BatchOperation.FRIEND_LABEL, listOf("friend-d-remove"), "account-A", false, "label-9")
        )

        try {
            requests.forEach { request ->
                // submit returns as soon as the production process queue accepts the task.
                assertEquals(BatchTaskSubmitResult.STARTED, manager.submit(request))
                assertEquals(request, accepted.poll(5, TimeUnit.SECONDS))
                awaitStatus(manager, BatchTaskStatus.COMPLETED)
                assertEquals(BatchTaskStatus.COMPLETED, manager.snapshot.value.status)
                assertEquals(request.targetIds.size, manager.snapshot.value.report?.success)
                assertEquals("批量操作成功", BatchTaskCompletionMessage.from(manager.snapshot.value))
            }
        } finally {
            manager.shutdownForTesting()
        }
    }

    @Test
    fun completionFeedbackRunsOnceAfterPartialResultsWithoutTreatingThemAsSuccess() {
        val notified = CountDownLatch(1)
        val calls = AtomicInteger()
        val manager = BatchTaskManager.createForTesting(
            BatchTaskProcessor { request, _ ->
                BatchTaskRunner.fromResults(
                    request.targetIds,
                    listOf(
                        BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "confirmed"),
                        BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.FAILED, "rejected"),
                        BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.UNCONFIRMED, "accepted only")
                    ),
                    ""
                )
            },
            BatchTaskCompletionNotifier { snapshot ->
                assertEquals(BatchTaskStatus.COMPLETED, snapshot.status)
                assertEquals(1, snapshot.report?.success)
                assertEquals(1, snapshot.report?.failed)
                assertEquals(1, snapshot.report?.unconfirmed)
                calls.incrementAndGet()
                notified.countDown()
            }
        )
        try {
            assertEquals(
                BatchTaskSubmitResult.STARTED,
                manager.submit(BatchTaskRequest(
                    BatchOperation.FRIEND_LABEL,
                    listOf("one", "two", "three"),
                    "account-A",
                    enabled = false,
                    labelId = "label-A"
                ))
            )
            assertTrue("completion feedback should follow the final report", notified.await(5, TimeUnit.SECONDS))
            repeat(3) { manager.snapshot.value }
            assertEquals(1, calls.get())
            val message = BatchTaskCompletionMessage.from(manager.snapshot.value)
            assertTrue(message.contains("失败 1"))
            assertTrue(message.contains("未确认 1"))
            assertTrue(!message.contains("批量操作成功"))
        } finally {
            manager.shutdownForTesting()
        }
    }

    @Test
    fun hostInvalidationLetsInflightItemFinishButStopsLaterSubmissions() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val submitted = AtomicInteger()
        val notifications = AtomicInteger()
        val manager = BatchTaskManager.createForTesting(
            BatchTaskProcessor { request, signal ->
                val report = BatchTaskRunner.run(
                    request.targetIds,
                    request.expectedAccount,
                    { "account-A" },
                    signal,
                    0,
                    { _, _ ->
                        submitted.incrementAndGet()
                        firstStarted.countDown()
                        releaseFirst.await(5, TimeUnit.SECONDS)
                        BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "already submitted")
                    },
                    null
                )
                finished.countDown()
                report
            },
            BatchTaskCompletionNotifier { notifications.incrementAndGet() }
        )
        try {
            val request = BatchTaskRequest(
                BatchOperation.PIN, listOf("one", "two"), "account-A", enabled = true
            )
            assertEquals(BatchTaskSubmitResult.STARTED, manager.submit(request))
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            manager.shutdownForTesting()
            releaseFirst.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            awaitStatus(manager, BatchTaskStatus.STOPPED)
            assertEquals(1, submitted.get())
            assertEquals(1, manager.snapshot.value.report?.success)
            assertEquals(1, manager.snapshot.value.report?.skipped)
            assertEquals(0, notifications.get())
            assertEquals(BatchTaskSubmitResult.UNAVAILABLE, manager.submit(request))
        } finally {
            releaseFirst.countDown()
            manager.shutdownForTesting()
        }
    }

    @Test
    fun workOutlivesUiObservationIsMutuallyExclusiveStopsAndCanRestart() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstFinished = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val submissions = AtomicInteger()
        val manager = BatchTaskManager.createForTesting(BatchTaskProcessor { request, signal ->
            val report = BatchTaskRunner.run(
                request.targetIds,
                request.expectedAccount,
                { "account-A" },
                signal,
                0,
                { _, _ ->
                    submissions.incrementAndGet()
                    if (request.targetIds.size == 2) {
                        firstStarted.countDown()
                        releaseFirst.await(5, TimeUnit.SECONDS)
                    }
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "native action returned")
                },
                null
            )
            if (request.targetIds.size == 2) firstFinished.countDown() else secondFinished.countDown()
            report
        })

        try {
            val firstRequest = BatchTaskRequest(
                BatchOperation.PIN, listOf("one", "two"), "account-A", enabled = true
            )
            assertEquals(BatchTaskSubmitResult.STARTED, manager.submit(firstRequest))
            assertTrue("first task should begin", firstStarted.await(5, TimeUnit.SECONDS))

            // The screen has no ownership handle to dispose. Re-entry observes the running snapshot.
            assertEquals(BatchTaskStatus.RUNNING, manager.snapshot.value.status)
            assertEquals(BatchTaskSubmitResult.BUSY, manager.submit(firstRequest))
            assertEquals(1, submissions.get())

            assertTrue(manager.stopLaterTasks())
            releaseFirst.countDown()
            assertTrue("the in-flight operation must finish", firstFinished.await(5, TimeUnit.SECONDS))
            awaitStatus(manager, BatchTaskStatus.STOPPED)
            assertEquals(BatchTaskStatus.STOPPED, manager.snapshot.value.status)
            assertEquals(1, manager.snapshot.value.report?.success)
            assertEquals(1, manager.snapshot.value.report?.skipped)
            assertEquals("已停止后续操作", BatchTaskCompletionMessage.from(manager.snapshot.value))
            assertEquals(1, submissions.get())

            val restart = BatchTaskRequest(
                BatchOperation.HIDE, listOf("three"), "account-A", enabled = true
            )
            assertEquals(BatchTaskSubmitResult.STARTED, manager.submit(restart))
            assertTrue("a finished process task should be restartable", secondFinished.await(5, TimeUnit.SECONDS))
            awaitStatus(manager, BatchTaskStatus.COMPLETED)
            assertEquals(BatchTaskStatus.COMPLETED, manager.snapshot.value.status)
            assertEquals(2, submissions.get())
        } finally {
            releaseFirst.countDown()
            manager.shutdownForTesting()
        }
    }

    @Test
    fun productionSerialRunnerStopsBeforeSubmittingToSwitchedAccount() {
        val account = AtomicReference("account-A")
        val secondFinished = CountDownLatch(1)
        val submitted = AtomicInteger()
        val manager = BatchTaskManager.createForTesting(BatchTaskProcessor { request, signal ->
            val report = BatchTaskRunner.run(
                request.targetIds,
                request.expectedAccount,
                { account.get() },
                signal,
                0,
                { id, _ ->
                    submitted.incrementAndGet()
                    if (id == "one") account.set("account-B")
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "submitted")
                },
                null
            )
            secondFinished.countDown()
            report
        })
        try {
            assertEquals(
                BatchTaskSubmitResult.STARTED,
                manager.submit(BatchTaskRequest(
                    BatchOperation.DO_NOT_DISTURB,
                    listOf("one", "two"),
                    "account-A",
                    enabled = true
                ))
            )
            assertTrue(secondFinished.await(5, TimeUnit.SECONDS))
            awaitStatus(manager, BatchTaskStatus.STOPPED)
            assertEquals(1, submitted.get())
            assertEquals(1, manager.snapshot.value.report?.skipped)
            assertTrue(manager.snapshot.value.report?.stopReason?.contains("账号") == true)
            assertFalse(manager.stopLaterTasks())
        } finally {
            manager.shutdownForTesting()
        }
    }

    private fun awaitStatus(manager: BatchTaskManager, expected: BatchTaskStatus) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (manager.snapshot.value.status != expected && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
    }
}
