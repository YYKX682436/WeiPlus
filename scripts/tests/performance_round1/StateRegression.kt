package h.Hchat.hooks.items.groupmonitor

import h.Hchat.hooks.items.hchatextra.MessageDetailsBindingIndex
import h.Hchat.hooks.items.antirecall.RecallCachePolicy
import java.lang.ref.WeakReference

private fun scenario(name: String, action: () -> Unit) { action(); println("PASS: $name") }
private class Row { var label: Label? = null }
private class Label(val root: Row) { var binding: Binding? = null }
private class Binding(val root: Row, val holder: Label)
private fun retained(index: MessageDetailsBindingIndex<Label, Binding>): Pair<WeakReference<Row>, WeakReference<Label>> {
    val root = Row()
    val label = Label(root)
    root.label = label
    val binding = Binding(root, label)
    label.binding = binding
    index[label] = binding
    check(index[label] === binding)
    return WeakReference(root) to WeakReference(label)
}
fun main() {
    scenario("recall cache demand preserves follow-recall dependency and PB fallback/outgoing updates") {
        check(!RecallCachePolicy.needed(false, false, true))
        check(!RecallCachePolicy.needed(false, true, false))
        check(RecallCachePolicy.needed(false, true, true))
        check(RecallCachePolicy.needed(true, false, false))
        for (direction in listOf(null, 0, 1)) {
            check(!RecallCachePolicy.needsDatabase(false, false, direction, true, false))
            check(!RecallCachePolicy.needsDatabase(true, false, direction, true, true))
        }
        check(!RecallCachePolicy.needsDatabase(true, true, 0, false, false))
        check(RecallCachePolicy.needsDatabase(true, false, 0, false, false))
        check(RecallCachePolicy.needsDatabase(true, true, null, true, false))
        check(RecallCachePolicy.needsDatabase(true, true, 0, true, false))
        check(RecallCachePolicy.needsDatabase(true, true, 1, false, false))
    }
    val room = GroupSnapshotState.Scope(groups = setOf("g"))
    val off = GroupSnapshotState.Demand()
    val on = GroupSnapshotState.Demand(room, room)
    fun snapshot(vararg members: String) = GroupSnapshotState.Snapshot(members.toSet(), members.associateWith { it })
    scenario("disabled state maintains no group or name data") {
        val state = GroupSnapshotState()
        state.account("com.tencent.mm:a")
        state.update("leave", off)
        check(!state.active() && state.snapshots.isEmpty())
        check(!state.baseline(state.generation, "g", snapshot("a")))
    }
    scenario("specific scope, shared baseline, and repeated preload preserve live events") {
        val state = GroupSnapshotState()
        state.update("leave", on)
        val token = state.generation
        check(!state.baseline(token, "other", snapshot("a")))
        check(state.baseline(token, "g", snapshot("a")))
        state.update("rename", on)
        check(state.baseline(state.generation, "g", snapshot("a")))
        check(state.previous("rename", "g")?.members == setOf("a"))
        val readRevision = state.revision
        state.commit("g", snapshot("a", "b"))
        check(!state.baseline(state.generation, "g", snapshot("a"), readRevision))
        check(state.snapshots["g"]?.members == setOf("a", "b"))
        check(state.baseline(state.generation, "g", snapshot("a")))
        check(state.snapshots["g"]?.members == setOf("a", "b"))
        check(state.snapshots.size == 1)
    }
    scenario("first event and newly added scopes establish baselines without historical deltas") {
        val state = GroupSnapshotState()
        state.update("leave", on)
        check(state.previous("leave", "g") == null)
        state.commit("g", snapshot("historical"))
        check(state.previous("leave", "g")?.members == setOf("historical"))
        state.update("leave", GroupSnapshotState.Demand(GroupSnapshotState.Scope(groups = setOf("g", "new"))))
        check(state.previous("leave", "g") != null)
        check(state.previous("leave", "new") == null)
    }
    scenario("off-on and account/package changes reject queued reads and discard historical baselines") {
        val state = GroupSnapshotState()
        state.account("com.tencent.mm:account1")
        state.update("leave", on)
        val old = state.generation
        state.baseline(old, "g", snapshot("a"))
        state.update("leave", off)
        check(state.snapshots.isEmpty() && !state.baseline(old, "g", snapshot("a")))
        state.update("leave", on)
        check(state.previous("leave", "g") == null)
        check(!state.baseline(old, "g", snapshot("a")))
        val beforeAccount = state.generation
        state.account("com.tencent.mm:account2")
        check(!state.baseline(beforeAccount, "g", snapshot("a")))
        state.baseline(state.generation, "g", snapshot("b"))
        state.account("com.tencent.mm.wx:account2")
        check(state.snapshots.isEmpty() && state.previous("leave", "g") == null)
    }
    scenario("scope shrink and nickname consumer shutdown release unneeded data") {
        val state = GroupSnapshotState()
        state.update("leave", on)
        state.update("rename", on)
        state.baseline(state.generation, "g", snapshot("a"))
        state.update("leave", GroupSnapshotState.Demand(room))
        state.remove("rename")
        check(state.snapshots["g"]?.names?.isEmpty() == true)
        state.update("leave", off)
        check(state.snapshots.isEmpty())
    }
    scenario("a process-wide binding index cannot retain a row through its value") {
        val index = MessageDetailsBindingIndex<Label, Binding>()
        val (root, label) = retained(index)
        repeat(30) {
            if (root.get() != null || label.get() != null) { System.gc(); Thread.sleep(10) }
        }
        check(root.get() == null && label.get() == null)
        check(index.snapshot().isEmpty())
    }
    scenario("row-owned binding survives temporary detachment, replacement and removal are explicit") {
        val index = MessageDetailsBindingIndex<Label, Binding>()
        val row = Row(); val label = Label(row); row.label = label
        val first = Binding(row, label); label.binding = first; index[label] = first
        System.gc()
        check(index[label] === first)
        val second = Binding(row, label); label.binding = second; index[label] = second
        check(index[label] === second)
        label.binding = null; index.remove(label)
        check(index[label] == null)
    }
}
