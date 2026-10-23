package h.Hchat.hooks.items.groupmonitor

/** One copy of each group, with independent baseline readiness for each consumer. Guarded by runtime lock. */
internal class GroupSnapshotState {
    data class Scope(val all: Boolean = false, val groups: Set<String> = emptySet()) {
        fun contains(group: String) = all || group in groups
        val active get() = all || groups.isNotEmpty()
    }
    data class Demand(val members: Scope = Scope(), val names: Scope = Scope())
    data class Snapshot(val members: Set<String>, val names: Map<String, String>, val warmUntil: Long = 0)
    private data class Consumer(var demand: Demand, val ready: MutableSet<String> = HashSet())
    private val consumers = LinkedHashMap<String, Consumer>()
    val snapshots = LinkedHashMap<String, Snapshot>()
    var generation = 0L
        private set
    var revision = 0L
        private set
    private val revisions = HashMap<String, Long>()
    private var identity = ""

    fun invalidate() { generation++ }

    fun account(value: String): Boolean {
        if (identity == value) return false
        identity = value
        generation++
        snapshots.clear()
        revisions.clear()
        consumers.values.forEach { it.ready.clear() }
        return true
    }

    fun update(owner: String, demand: Demand) {
        val old = consumers[owner]
        if (old?.demand == demand) return
        generation++
        if (old == null) consumers[owner] = Consumer(demand)
        else {
            old.ready.removeAll { !demand.members.contains(it) ||
                (demand.names.contains(it) && !old.demand.names.contains(it)) }
            old.demand = demand
        }
        snapshots.keys.removeAll { !needs(it) }
        revisions.keys.removeAll { !needs(it) }
        snapshots.replaceAll { group, snapshot ->
            if (needsNames(group)) snapshot else snapshot.copy(names = emptyMap())
        }
    }

    fun remove(owner: String) {
        if (consumers.remove(owner) != null) generation++
        snapshots.keys.removeAll { !needs(it) }
        revisions.keys.removeAll { !needs(it) }
        snapshots.replaceAll { group, snapshot ->
            if (needsNames(group)) snapshot else snapshot.copy(names = emptyMap())
        }
    }

    fun needs(group: String) = consumers.values.any { it.demand.members.contains(group) }
    fun needsNames(group: String) = consumers.values.any { it.demand.names.contains(group) }
    fun active() = consumers.values.any { it.demand.members.active }
    fun all() = consumers.values.any { it.demand.members.all }
    fun groups() = consumers.values.flatMap { it.demand.members.groups }.toSet()
    fun wants(owner: String, group: String) = consumers[owner]?.demand?.members?.contains(group) == true

    fun previous(owner: String, group: String): Snapshot? {
        val consumer = consumers[owner] ?: return null
        if (!consumer.ready.add(group)) return snapshots[group]
        return null
    }

    /** Async baselines never overwrite a snapshot already maintained by events. */
    fun baseline(token: Long, group: String, snapshot: Snapshot, readRevision: Long = revision): Boolean {
        if (token != generation || !needs(group) || (revisions[group] ?: 0L) > readRevision) return false
        val existing = snapshots[group]
        if (existing == null) snapshots[group] = snapshot
        else if (existing.names.isEmpty() && snapshot.names.isNotEmpty()) {
            snapshots[group] = existing.copy(names = snapshot.names.filterKeys { it in existing.members })
        }
        consumers.values.filter { it.demand.members.contains(group) }.forEach { it.ready.add(group) }
        return true
    }

    fun commit(group: String, snapshot: Snapshot) {
        revisions[group] = ++revision
        snapshots[group] = snapshot
    }
}
