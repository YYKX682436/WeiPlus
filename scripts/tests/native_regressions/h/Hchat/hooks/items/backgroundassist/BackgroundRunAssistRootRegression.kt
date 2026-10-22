package h.Hchat.hooks.items.backgroundassist

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object BackgroundRunAssistRootRegression {
    private const val OFFICIAL = "com.tencent.mm"
    private const val MODIFIED = "com.tencent.mm.wx"
    private val backgroundOps = listOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND")
    private val legacyOps = listOf("WAKE_LOCK", "START_FOREGROUND")

    fun run() {
        rootStateIsIndependentAndRestoresOnlyManagedValues()
        failuresAndLegacySnapshotsRemainRecoverable()
        latestConfigurationWinsAndCancelsStaleWork()
        productionCommandRunnerReportsExitAndTimeout()
        wakeLockLeasesAreBoundedAndIdempotent()
    }

    private fun rootStateIsIndependentAndRestoresOnlyManagedValues() {
        val commands = FakeRootCommands()
        val root = RootSettingsController(commands)
        val officialState = MemoryRootState()
        val modifiedState = MemoryRootState()
        commands.whitelist += OFFICIAL
        commands.setModes(OFFICIAL, "allow", "allow", "default", "default")
        commands.setModes(MODIFIED, "ignore", "default", "default", "default")

        check(root.reconcile(OFFICIAL, RootRunOptions(true, true), officialState))
        check(root.reconcile(MODIFIED, RootRunOptions(true, true), modifiedState))
        check(commands.whitelist.containsAll(setOf(OFFICIAL, MODIFIED)))
        check(officialState.contains("doze_original_$OFFICIAL") && officialState.getBoolean("doze_original_$OFFICIAL"))
        check(!modifiedState.getBoolean("doze_original_$MODIFIED"))
        check(officialState.getBoolean("appops_snapshot_$OFFICIAL"))
        check(!officialState.contains("appops_original_${OFFICIAL}_$OFFICIAL"))
        check(commands.mode(OFFICIAL, "RUN_ANY_IN_BACKGROUND") == "allow")
        check(commands.mode(OFFICIAL, "WAKE_LOCK") == "default") {
            "New settings must not change legacy WakeLock AppOps"
        }

        // A subsequent user change to an AppOp is left alone while the module is disabled.
        commands.whitelist.remove(MODIFIED)
        commands.setMode(MODIFIED, "RUN_IN_BACKGROUND", "ignore")
        check(root.reconcile(OFFICIAL, RootRunOptions(false, false), officialState))
        check(root.reconcile(MODIFIED, RootRunOptions(false, false), modifiedState))
        check(commands.whitelist == setOf(OFFICIAL)) { "Disabling one host must preserve the other host's Doze state" }
        check(commands.mode(OFFICIAL, "RUN_ANY_IN_BACKGROUND") == "allow") {
            "A mode that was already allowed must not be revoked"
        }
        check(commands.mode(MODIFIED, "RUN_ANY_IN_BACKGROUND") == "ignore")
        check(commands.mode(MODIFIED, "RUN_IN_BACKGROUND") == "ignore") {
            "A user-changed mode must not be overwritten by restoration"
        }
        check(!modifiedState.getBoolean("appops_snapshot_$MODIFIED"))
        check(!modifiedState.contains("doze_original_$MODIFIED"))
        check(commands.calls.none { it == "cmd deviceidle whitelist -$OFFICIAL" }) {
            "The pre-existing official whitelist entry must not be removed"
        }
        check(commands.calls.none { it == "cmd deviceidle whitelist -$MODIFIED" }) {
            "A user-removed whitelist entry must not be rewritten during module shutdown"
        }
        check(RootSettingsController(commands).reconcile("com.tencent.mm.fake", RootRunOptions(true, true), MemoryRootState()).not())
    }

    private fun failuresAndLegacySnapshotsRemainRecoverable() {
        val host = MODIFIED
        val commands = FakeRootCommands()
        val controller = RootSettingsController(commands)
        val state = MemoryRootState()
        commands.failNext("cmd deviceidle whitelist +$host", RootCommandResult("", 13))
        check(!controller.reconcile(host, RootRunOptions(true, false), state)) {
            "A non-zero Root command exit must not be reported as success"
        }
        check(state.contains("doze_original_$host")) { "Failed Root work must retain its rollback snapshot" }

        val timeoutCommands = FakeRootCommands()
        val timeoutState = MemoryRootState()
        timeoutCommands.failNext("cmd deviceidle whitelist +$host", RootCommandResult(null, null, timedOut = true))
        check(!RootSettingsController(timeoutCommands).reconcile(host, RootRunOptions(true, false), timeoutState))
        check(timeoutState.contains("doze_original_$host"))
        check(timeoutCommands.whitelist.none { it == host })

        val partialCommands = FakeRootCommands()
        val partialState = MemoryRootState()
        partialCommands.failAfterEffect("cmd deviceidle whitelist +$host", RootCommandResult("", 9))
        val partialController = RootSettingsController(partialCommands)
        check(!partialController.reconcile(host, RootRunOptions(true, false), partialState))
        check(partialCommands.whitelist.contains(host))
        check(partialState.contains("doze_original_$host"))
        check(partialController.reconcile(host, RootRunOptions(false, false), partialState))
        check(!partialCommands.whitelist.contains(host))
        check(!partialState.contains("doze_original_$host"))

        val commitFailureState = MemoryRootState().apply { failCommits = true }
        val untouchedCommands = FakeRootCommands()
        check(!RootSettingsController(untouchedCommands).reconcile(host, RootRunOptions(true, false), commitFailureState))
        check(untouchedCommands.whitelist.isEmpty()) { "Doze must not change before its original state is persisted" }

        val legacyCommands = FakeRootCommands()
        val legacyState = MemoryRootState().apply {
            commit(mapOf(
                "appops_snapshot_$host" to true,
                "appops_original_${host}_RUN_ANY_IN_BACKGROUND" to "ignore",
                "appops_original_${host}_RUN_IN_BACKGROUND" to "default",
                "appops_original_${host}_WAKE_LOCK" to "ignore",
                "appops_original_${host}_START_FOREGROUND" to "default"
            ))
        }
        legacyCommands.setModes(host, "allow", "allow", "allow", "allow")
        check(RootSettingsController(legacyCommands).reconcile(host, RootRunOptions(false, false), legacyState))
        check(legacyCommands.mode(host, "WAKE_LOCK") == "ignore")
        check(legacyCommands.mode(host, "START_FOREGROUND") == "default")
        check(!legacyState.getBoolean("appops_snapshot_$host"))
    }

    private fun latestConfigurationWinsAndCancelsStaleWork() {
        val commands = FakeRootCommands()
        val state = MemoryRootState()
        val manualExecutor = ManualExecutor()
        val queue = LatestRootOptionsQueue(manualExecutor, RootSettingsController(commands))
        check(queue.submit(OFFICIAL, 1, RootRunOptions(true, false)) { state })
        check(queue.submit(OFFICIAL, 2, RootRunOptions(false, false)) { state })
        check(queue.submit(OFFICIAL, 3, RootRunOptions(true, false)) { state })
        manualExecutor.runAll()
        check(commands.whitelist.contains(OFFICIAL)) { "The newest option in a rapid toggle burst must win" }
        check(queue.submit(OFFICIAL, 2, RootRunOptions(false, false)) { state })
        check(!queue.submit("com.tencent.mm.fake", 4, RootRunOptions(true, true)) { state })
        manualExecutor.runAll()
        check(commands.whitelist.contains(OFFICIAL)) { "An older async request must not overwrite newer settings" }

        val blockingCommands = FakeRootCommands()
        val blockingState = MemoryRootState()
        val enteredAdd = CountDownLatch(1)
        val releaseAdd = CountDownLatch(1)
        blockingCommands.blockNext("cmd deviceidle whitelist +$OFFICIAL", enteredAdd, releaseAdd)
        val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(2))
        try {
            val blockingQueue = LatestRootOptionsQueue(executor, RootSettingsController(blockingCommands))
            check(blockingQueue.submit(OFFICIAL, 1, RootRunOptions(true, false)) { blockingState })
            check(enteredAdd.await(2, TimeUnit.SECONDS)) { "Root add command did not reach the controlled block" }
            check(blockingQueue.submit(OFFICIAL, 2, RootRunOptions(false, false)) { blockingState })
            releaseAdd.countDown()
            executor.submit {}.get(3, TimeUnit.SECONDS)
            check(!blockingCommands.whitelist.contains(OFFICIAL)) {
                "A setting turned off during a blocking Root call must be reconciled immediately after it returns"
            }
            check(!blockingState.contains("doze_original_$OFFICIAL"))
        } finally {
            releaseAdd.countDown()
            executor.shutdownNow()
        }
    }

    private fun productionCommandRunnerReportsExitAndTimeout() {
        val runner = RootShellCommandRunner(
            RootProcessFactory { command ->
                ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true).start()
            },
            timeoutMillis = 100
        )
        val success = runner.execute("printf ok")
        check(success.succeeded && success.output == "ok")
        val failedExit = runner.execute("printf failed; exit 17")
        check(!failedExit.succeeded && failedExit.exitCode == 17)
        val timeout = runner.execute("exec sleep 5")
        check(!timeout.succeeded && timeout.timedOut) { "A timed out Root process must never look successful" }
    }

    private fun wakeLockLeasesAreBoundedAndIdempotent() {
        val created = mutableListOf<FakeWakeLock>()
        val registry = WakeLockLeaseRegistry()
        val createLock = { FakeWakeLock().also(created::add) }
        check(registry.setDesired(OFFICIAL, 1, true, createLock))
        check(registry.setDesired(OFFICIAL, 1, true, createLock))
        check(created.size == 1 && created.single().acquireCalls == 1)
        check(created.single().lastTimeout == WakeLockLeaseRegistry.MAX_WAKE_LOCK_MILLIS)
        check(created.single().lastTimeout == TimeUnit.MINUTES.toMillis(10))
        check(registry.setDesired(MODIFIED, 1, true, createLock))
        check(created.size == 2)
        check(registry.setDesired(OFFICIAL, 0, false)) { "Stale shutdown must not release a newer lease" }
        check(registry.heldFor(OFFICIAL))
        check(registry.setDesired(OFFICIAL, 2, false))
        check(!registry.heldFor(OFFICIAL) && created.first().releaseCalls == 1)
        check(registry.heldFor(MODIFIED)) { "Releasing one host must leave the other host's lock alone" }

        created.last().isHeld = false // The system's timed acquisition has expired.
        check(registry.setDesired(MODIFIED, 2, true, createLock))
        check(created.size == 3 && registry.heldFor(MODIFIED)) {
            "A timed-out lease must be replaced only by a later explicit configuration change"
        }
        check(registry.setDesired(MODIFIED, 3, false))
        check(created.last().releaseCalls == 1)

        val failing = FakeWakeLock(throwOnAcquire = true)
        val failureRegistry = WakeLockLeaseRegistry()
        check(!failureRegistry.setDesired(OFFICIAL, 1, true) { failing })
        check(!failureRegistry.heldFor(OFFICIAL) && failing.releaseCalls == 1) {
            "Acquire exceptions must run cleanup and leave no tracked lock"
        }
        check(!failureRegistry.setDesired("com.tencent.mm.fake", 2, true))
    }

    private class MemoryRootState : RootStateStore {
        private val values = mutableMapOf<String, Any>()
        var failCommits = false

        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun getBoolean(key: String, fallback: Boolean): Boolean = values[key] as? Boolean ?: fallback
        override fun getString(key: String): String? = values[key] as? String
        override fun commit(changes: Map<String, Any?>): Boolean {
            if (failCommits) return false
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
    }

    private class FakeRootCommands : RootCommandRunner {
        val whitelist = linkedSetOf<String>()
        val calls = mutableListOf<String>()
        private val modes = mutableMapOf<Pair<String, String>, String>()
        private val forced = mutableMapOf<String, Pair<RootCommandResult, Boolean>>()
        private var blockingCommand: String? = null
        private var entered: CountDownLatch? = null
        private var release: CountDownLatch? = null

        fun setModes(host: String, backgroundAny: String, background: String, wakeLock: String, foreground: String) {
            setMode(host, "RUN_ANY_IN_BACKGROUND", backgroundAny)
            setMode(host, "RUN_IN_BACKGROUND", background)
            setMode(host, "WAKE_LOCK", wakeLock)
            setMode(host, "START_FOREGROUND", foreground)
        }

        fun setMode(host: String, operation: String, mode: String) {
            synchronized(this) { modes[host to operation] = mode }
        }

        fun mode(host: String, operation: String): String = synchronized(this) {
            modes[host to operation] ?: "default"
        }

        fun failNext(command: String, result: RootCommandResult) {
            synchronized(this) { forced[command] = result to false }
        }

        fun failAfterEffect(command: String, result: RootCommandResult) {
            synchronized(this) { forced[command] = result to true }
        }

        fun blockNext(command: String, entered: CountDownLatch, release: CountDownLatch) {
            this.blockingCommand = command
            this.entered = entered
            this.release = release
        }

        override fun execute(command: String): RootCommandResult {
            calls += command
            if (command == blockingCommand) {
                entered?.countDown()
                check(release?.await(3, TimeUnit.SECONDS) == true) { "Timed out waiting for test release" }
            }
            val (forcedResult, applyBeforeFailure) = synchronized(this) { forced.remove(command) }
                ?: return executeNormally(command)
            if (applyBeforeFailure) executeNormally(command)
            return forcedResult
        }

        private fun executeNormally(command: String): RootCommandResult = synchronized(this) {
            when {
                command == "cmd deviceidle whitelist" -> RootCommandResult(
                    if (whitelist.isEmpty()) "Whitelist: none" else "Whitelist: user,${whitelist.joinToString(",")}", 0
                )
                command.startsWith("cmd deviceidle whitelist +") -> {
                    whitelist += command.substringAfterLast('+')
                    RootCommandResult("", 0)
                }
                command.startsWith("cmd deviceidle whitelist -") -> {
                    whitelist -= command.substringAfterLast('-')
                    RootCommandResult("", 0)
                }
                command.startsWith("cmd appops get ") -> {
                    val parts = command.split(' ')
                    val host = parts[3]
                    val operation = parts[4]
                    RootCommandResult("$operation: ${modes[host to operation] ?: "default"}; time=now", 0)
                }
                command.startsWith("cmd appops set ") -> {
                    val parts = command.split(' ')
                    modes[parts[3] to parts[4]] = parts[5]
                    RootCommandResult("", 0)
                }
                else -> RootCommandResult(null, 2)
            }
        }
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    private class FakeWakeLock(private val throwOnAcquire: Boolean = false) : TimedWakeLockPort {
        override var isHeld: Boolean = false
        var acquireCalls = 0
        var releaseCalls = 0
        var lastTimeout = 0L

        override fun acquire(timeoutMillis: Long) {
            acquireCalls++
            lastTimeout = timeoutMillis
            if (throwOnAcquire) error("acquire failed")
            isHeld = true
        }

        override fun release() {
            releaseCalls++
            isHeld = false
        }
    }
}
