package h.Hchat.hooks.items.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import h.Hchat.hooks.api.contact.WeChatNativeMutationResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class BatchTaskManagerRegressionTest {
    @Test
    fun likeTaskRequiresFriendAndServerTimeAndCopiesTargetsBeforeDispatch() {
        val start = CountDownLatch(1)
        val accepted = LinkedBlockingQueue<BatchTaskRequest>()
        val manager = BatchTaskManager.createForTesting(BatchTaskProcessor { request, _ ->
            check(start.await(5, TimeUnit.SECONDS))
            accepted.put(request)
            BatchTaskRunner.fromResults(request.targetIds, emptyList(), "")
        })
        try {
            val ids = mutableListOf("90")
            val times = mutableMapOf("90" to 1_720_000_000L)
            val request = BatchTaskRequest(BatchOperation.LIKE_FRIEND_MOMENTS, ids, "account-A", true,
                momentTimes = times, momentFriend = "friend-A")
            assertEquals(BatchTaskSubmitResult.INVALID, manager.submit(request.copy(momentFriend = "")))
            assertEquals(BatchTaskSubmitResult.INVALID, manager.submit(request.copy(momentTimes = emptyMap())))
            assertEquals(BatchTaskSubmitResult.STARTED, manager.submit(request))
            ids[0] = "91"
            times["90"] = 99
            start.countDown()
            val queued = accepted.poll(5, TimeUnit.SECONDS)
            assertEquals(listOf("90"), queued?.targetIds)
            assertEquals(mapOf("90" to 1_720_000_000L), queued?.momentTimes)
            assertEquals("friend-A", queued?.momentFriend)
            assertEquals("account-A", queued?.expectedAccount)
        } finally {
            start.countDown()
            manager.shutdownForTesting()
        }
    }

    @Test
    fun destructiveSelectionsDeduplicateRepeatedRowsBeforeQueueing() {
        val accepted = LinkedBlockingQueue<BatchTaskRequest>()
        val manager = BatchTaskManager.createForTesting(BatchTaskProcessor { request, _ ->
            accepted.put(request)
            BatchTaskRunner.fromResults(
                request.targetIds,
                request.targetIds.map { BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "test") },
                ""
            )
        })
        try {
            assertEquals(BatchTaskSubmitResult.STARTED, manager.submit(BatchTaskRequest(
                BatchOperation.DELETE_FRIENDS,
                listOf("friend-a", "friend-a", "friend-b"),
                "account-A",
                true
            )))
            assertEquals(listOf("friend-a", "friend-b"), accepted.poll(5, TimeUnit.SECONDS)?.targetIds)
            awaitStatus(manager, BatchTaskStatus.COMPLETED)
        } finally {
            manager.shutdownForTesting()
        }
    }

    @Test
    fun allTenProductionTaskKindsStartImmediatelyAndPreserveSelectedState() {
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
            BatchTaskRequest(BatchOperation.FRIEND_LABEL, listOf("friend-d-remove"), "account-A", false, "label-9"),
            BatchTaskRequest(BatchOperation.STAR_FRIEND, listOf("friend-star-on"), "account-A", true),
            BatchTaskRequest(BatchOperation.STAR_FRIEND, listOf("friend-star-off"), "account-A", false),
            BatchTaskRequest(
                BatchOperation.FRIEND_PERMISSION,
                listOf("friend-only-chat"),
                "account-A",
                true,
                friendPermissionSelection = BatchFriendPermissionSelection(
                    access = BatchFriendAccessChoice.ONLY_CHAT
                )
            ),
            BatchTaskRequest(
                BatchOperation.FRIEND_PERMISSION,
                listOf("friend-normal"),
                "account-A",
                false,
                friendPermissionSelection = BatchFriendPermissionSelection(
                    access = BatchFriendAccessChoice.NORMAL
                )
            ),
            BatchTaskRequest(
                BatchOperation.FRIEND_PERMISSION,
                listOf("friend-hide-moments"),
                "account-A",
                true,
                friendPermissionSelection = BatchFriendPermissionSelection(
                    dontSee = BatchPermissionToggle.ENABLE
                )
            ),
            BatchTaskRequest(
                BatchOperation.FRIEND_PERMISSION,
                listOf("friend-show-moments"),
                "account-A",
                false,
                friendPermissionSelection = BatchFriendPermissionSelection(
                    dontLetSee = BatchPermissionToggle.DISABLE
                )
            ),
            BatchTaskRequest(BatchOperation.GROUP_SAVE_TO_CONTACTS, listOf("room-save"), "account-A", true),
            BatchTaskRequest(BatchOperation.GROUP_SAVE_TO_CONTACTS, listOf("room-unsave"), "account-A", false),
            BatchTaskRequest(BatchOperation.DELETE_CHATS, listOf("chat-delete"), "account-A", true),
            BatchTaskRequest(BatchOperation.DELETE_FRIENDS, listOf("friend-delete-default"), "account-A", true),
            BatchTaskRequest(
                BatchOperation.DELETE_FRIENDS,
                listOf("friend-delete-with-history"),
                "account-A",
                true,
                deleteFriendClearHistory = true
            ),
            BatchTaskRequest(
                BatchOperation.GROUP_NICKNAME,
                listOf("room-nickname"),
                "account-A",
                true,
                groupNickname = "我的群昵称"
            )
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
    fun deleteFriendHistoryChoiceDefaultsOffAndIsCapturedInTheQueuedRequest() {
        val accepted = LinkedBlockingQueue<BatchTaskRequest>()
        val manager = BatchTaskManager.createForTesting(BatchTaskProcessor { request, _ ->
            accepted.put(request)
            BatchTaskRunner.fromResults(
                request.targetIds,
                request.targetIds.map {
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "test")
                },
                ""
            )
        })
        val defaultRequest = BatchTaskRequest(BatchOperation.DELETE_FRIENDS, listOf("friend-off"), "account-A", true)
        val clearHistoryRequest = defaultRequest.copy(
            targetIds = listOf("friend-on"),
            deleteFriendClearHistory = true
        )
        try {
            assertFalse(defaultRequest.deleteFriendClearHistory)
            assertEquals(BatchTaskSubmitResult.STARTED, manager.submit(defaultRequest))
            assertEquals(defaultRequest, accepted.poll(5, TimeUnit.SECONDS))
            awaitStatus(manager, BatchTaskStatus.COMPLETED)

            assertEquals(BatchTaskSubmitResult.STARTED, manager.submit(clearHistoryRequest))
            assertEquals(clearHistoryRequest, accepted.poll(5, TimeUnit.SECONDS))
            awaitStatus(manager, BatchTaskStatus.COMPLETED)
        } finally {
            manager.shutdownForTesting()
        }
    }

    @Test
    fun groupNicknameRequestsRequireValidNativeLengthBeforeQueueing() {
        val manager = BatchTaskManager.createForTesting(BatchTaskProcessor { request, _ ->
            BatchTaskRunner.fromResults(
                request.targetIds,
                request.targetIds.map {
                    BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "captured")
                },
                ""
            )
        })
        try {
            assertEquals(
                BatchTaskSubmitResult.INVALID,
                manager.submit(BatchTaskRequest(
                    BatchOperation.GROUP_NICKNAME,
                    listOf("room"),
                    "account-A",
                    true,
                    groupNickname = ""
                ))
            )
            assertEquals(
                BatchTaskSubmitResult.INVALID,
                manager.submit(BatchTaskRequest(
                    BatchOperation.GROUP_NICKNAME,
                    listOf("room"),
                    "account-A",
                    true,
                    groupNickname = "12345678901234567"
                ))
            )
            assertEquals(
                BatchTaskSubmitResult.STARTED,
                manager.submit(BatchTaskRequest(
                    BatchOperation.GROUP_NICKNAME,
                    listOf("room"),
                    "account-A",
                    true,
                    groupNickname = "1234567890123456"
                ))
            )
            awaitStatus(manager, BatchTaskStatus.COMPLETED)
        } finally {
            manager.shutdownForTesting()
        }
    }

    @Test
    fun friendPermissionSelectionDefaultsToNoChangeAndMapsOnlyExplicitChoices() {
        val unchanged = BatchFriendPermissionSelection()
        assertTrue(unchanged.actions().isEmpty())
        val manager = BatchTaskManager.createForTesting(BatchTaskProcessor { request, _ ->
            BatchTaskRunner.fromResults(
                request.targetIds,
                request.targetIds.map { BatchTaskRunner.TaskResult.of(BatchTaskRunner.Outcome.SUCCESS, "test") },
                ""
            )
        })
        try {
            assertEquals(
                BatchTaskSubmitResult.INVALID,
                manager.submit(BatchTaskRequest(
                    BatchOperation.FRIEND_PERMISSION,
                    listOf("friend"),
                    "account-A",
                    enabled = false
                ))
            )
        } finally {
            manager.shutdownForTesting()
        }

        val single = BatchFriendPermissionSelection(access = BatchFriendAccessChoice.NORMAL).actions()
        assertEquals(1, single.size)
        assertEquals(BatchFriendPermissionSetting.ONLY_CHAT, single[0].setting)
        assertEquals(false, single[0].enabled)

        val multiple = BatchFriendPermissionSelection(
            access = BatchFriendAccessChoice.ONLY_CHAT,
            dontLetSee = BatchPermissionToggle.DISABLE,
            dontSee = BatchPermissionToggle.ENABLE
        ).actions()
        assertEquals(3, multiple.size)
        assertEquals(listOf(
            BatchFriendPermissionSetting.ONLY_CHAT,
            BatchFriendPermissionSetting.DONT_LET_SEE_MOMENTS,
            BatchFriendPermissionSetting.DONT_SEE_MOMENTS
        ), multiple.map { it.setting })
        assertEquals(listOf(true, false, true), multiple.map { it.enabled })
    }

    @Test
    fun friendPermissionRunnerSurfacesPartialFailureInsteadOfMaskingConfirmedChanges() {
        val selection = BatchFriendPermissionSelection(
            access = BatchFriendAccessChoice.NORMAL,
            dontLetSee = BatchPermissionToggle.ENABLE,
            dontSee = BatchPermissionToggle.DISABLE
        )
        val submitted = mutableListOf<BatchFriendPermissionSetting>()
        val result = BatchFriendPermissionExecution.executeOne(
            id = "friend",
            expectedAccount = "account-A",
            selection = selection,
            stopSignal = BatchTaskRunner.StopSignal(),
            currentAccount = { "account-A" },
            validateTarget = { null },
            mutate = { setting, _, _ ->
                submitted += setting
                when (setting) {
                    BatchFriendPermissionSetting.ONLY_CHAT,
                    BatchFriendPermissionSetting.DONT_SEE_MOMENTS ->
                        WeChatNativeMutationResult.of(
                            WeChatNativeMutationResult.Outcome.CONFIRMED, "confirmed"
                        )
                    BatchFriendPermissionSetting.DONT_LET_SEE_MOMENTS ->
                        WeChatNativeMutationResult.of(
                            WeChatNativeMutationResult.Outcome.SERVER_REJECTED, "rejected"
                        )
                }
            }
        )

        assertEquals(BatchTaskRunner.Outcome.FAILED, result.outcome)
        assertEquals(3, submitted.size)
        assertTrue(result.message.contains("聊天、朋友圈、微信运动等：已确认"))
        assertTrue(result.message.contains("服务端拒绝"))
        assertTrue(result.message.contains("不看他（她）：关闭：已确认"))
    }

    @Test
    fun friendPermissionRunnerStopsBeforeNextSettingAfterStopOrAccountSwitch() {
        val stop = BatchTaskRunner.StopSignal()
        val stoppedCalls = mutableListOf<BatchFriendPermissionSetting>()
        val selection = BatchFriendPermissionSelection(
            access = BatchFriendAccessChoice.ONLY_CHAT,
            dontLetSee = BatchPermissionToggle.ENABLE
        )
        val stopped = BatchFriendPermissionExecution.executeOne(
            id = "friend",
            expectedAccount = "account-A",
            selection = selection,
            stopSignal = stop,
            currentAccount = { "account-A" },
            validateTarget = { null },
            mutate = { setting, _, _ ->
                stoppedCalls += setting
                stop.stop()
                WeChatNativeMutationResult.of(
                    WeChatNativeMutationResult.Outcome.CONFIRMED, "confirmed"
                )
            }
        )
        assertEquals(1, stoppedCalls.size)
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, stopped.outcome)
        assertTrue(stopped.message.contains("停止后未提交"))

        var identity = "account-A"
        val accountCalls = mutableListOf<BatchFriendPermissionSetting>()
        val switched = BatchFriendPermissionExecution.executeOne(
            id = "friend",
            expectedAccount = "account-A",
            selection = selection,
            stopSignal = BatchTaskRunner.StopSignal(),
            currentAccount = { identity },
            validateTarget = { null },
            mutate = { setting, _, _ ->
                accountCalls += setting
                identity = "account-B"
                WeChatNativeMutationResult.of(
                    WeChatNativeMutationResult.Outcome.CONFIRMED, "confirmed"
                )
            }
        )
        assertEquals(1, accountCalls.size)
        assertEquals(BatchTaskRunner.Outcome.UNCONFIRMED, switched.outcome)
        assertTrue(switched.message.contains("账号切换后未提交"))
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
                BatchOperation.GROUP_NICKNAME,
                listOf("one", "two"),
                "account-A",
                enabled = true,
                groupNickname = "My group name"
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
                BatchOperation.STAR_FRIEND, listOf("three"), "account-A", enabled = true
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
                    BatchOperation.FRIEND_PERMISSION,
                    listOf("one", "two"),
                    "account-A",
                    enabled = true,
                    friendPermissionSelection = BatchFriendPermissionSelection(
                        dontSee = BatchPermissionToggle.ENABLE
                    )
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
