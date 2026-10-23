package h.Hchat.hooks.items.groupmonitor

import android.content.Context
import h.Hchat.hooks.api.contact.WeChatChatroomChangeApi
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.model.WeChatChatroom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Host-scoped shared query/subscription owner. Callbacks and invalidation are serialized by this lock. */
internal class GroupMonitorSnapshots private constructor(private val context: Context) {
    private val state = GroupSnapshotState()
    private val callbacks = LinkedHashMap<String, (String, GroupSnapshotState.Snapshot?, GroupSnapshotState.Snapshot, WeChatChatroomChangeApi.ChatroomChange) -> Unit>()
    private var subscription: WeChatChatroomChangeApi.Subscription? = null
    private var subscriptionToken: Any? = null
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "Hchat-GroupBaseline").apply { isDaemon = true } }
    private var preloadQueued = false
    private var completedGeneration: Long? = null
    private val recentNames = LinkedHashMap<String, String>()

    private fun identity(): String = context.packageName + ":" +
        WeChatApis.contact().account()?.sessionIdentity().orEmpty()

    private fun checkAccount() {
        if (state.account(identity())) recentNames.clear()
    }

    @Synchronized
    fun configure(owner: String, demand: GroupSnapshotState.Demand, invalidate: Boolean = false,
                  callback: (String, GroupSnapshotState.Snapshot?, GroupSnapshotState.Snapshot, WeChatChatroomChangeApi.ChatroomChange) -> Unit) {
        if (state.active() || demand.members.active) checkAccount()
        callbacks[owner] = callback
        val before = state.generation
        state.update(owner, demand)
        if (invalidate && state.generation == before) {
            val complete = completedGeneration == before
            state.invalidate()
            if (complete) completedGeneration = state.generation
        }
        recentNames.keys.removeAll { !state.needs(it.substringBefore('|')) }
        syncSubscription()
        preload()
    }

    private fun syncSubscription() {
        if (!state.active()) {
            subscriptionToken = null
            subscription?.unsubscribe()
            subscription = null
            recentNames.clear()
        } else if (subscription == null) {
            val token = Any()
            subscriptionToken = token
            subscription = (WeChatApis.contact().chatroomChanges() ?: WeChatApis.chatroomChanges())
                ?.subscribe { change ->
                    synchronized(this) {
                        if (subscriptionToken === token) onChange(change)
                    }
                }
            if (subscription == null) subscriptionToken = null
        }
    }

    @Synchronized
    fun remove(owner: String) {
        callbacks.remove(owner)
        state.remove(owner)
        recentNames.keys.removeAll { !state.needs(it.substringBefore('|')) }
        syncSubscription()
    }

    @Synchronized
    fun lease(): Long { if (state.active()) checkAccount(); return state.generation }

    @Synchronized
    fun current(owner: String, group: String, token: Long): Boolean {
        if (token != state.generation || !state.wants(owner, group)) return false
        checkAccount()
        return token == state.generation && state.wants(owner, group)
    }

    @Synchronized
    fun memberName(group: String, member: String): String? =
        state.snapshots[group]?.names?.get(member) ?: recentNames["$group|$member"]

    @Synchronized
    fun preload() {
        if (!state.active()) return
        checkAccount()
        if (!state.active() || preloadQueued || completedGeneration == state.generation) return
        preloadQueued = true
        // Both features install in the same turn. Collect their union before querying once.
        executor.schedule({ loadBaseline() }, 20L, TimeUnit.MILLISECONDS)
    }

    private fun loadBaseline() {
        val plan = synchronized(this) {
            if (!state.active()) { preloadQueued = false; return }
            checkAccount()
            if (!state.active()) { preloadQueued = false; return }
            BaselinePlan(state.generation, state.revision, state.all(), state.groups())
        }
        var success = false
        try {
            val rooms = WeChatApis.contact().chatrooms() ?: return
            val sources = if (plan.all) rooms.getChatrooms() else plan.groups.mapNotNull { rooms.getChatroom(it) }
            for (room in sources) {
                val group = room.chatroomId.trim()
                val namesNeeded = synchronized(this) {
                    checkAccount()
                    if (plan.token != state.generation) return
                    if (!state.needs(group)) return@synchronized null
                    val old = state.snapshots[group]
                    if (old != null && (!state.needsNames(group) || old.names.isNotEmpty())) {
                        state.baseline(plan.token, group, old, plan.revision)
                        success = true
                        null
                    } else state.needsNames(group)
                } ?: continue
                val snapshot = read(room, group, namesNeeded)
                if (snapshot.members.isEmpty()) continue
                synchronized(this) {
                    checkAccount()
                    state.baseline(plan.token, group, snapshot, plan.revision)
                }
                success = true
            }
        } catch (failure: Exception) {
            h.Hchat.utils.HLog.e("[Hchat:GroupBaseline] 预载失败", failure)
        } finally {
            synchronized(this) {
                preloadQueued = false
                if (success && plan.token == state.generation) completedGeneration = plan.token
                // Configuration may have invalidated the query while it was outside the lock.
                if (plan.token != state.generation) preload()
            }
        }
    }

    private data class BaselinePlan(val token: Long, val revision: Long, val all: Boolean, val groups: Set<String>)

    private fun read(room: WeChatChatroom?, group: String, needsNames: Boolean): GroupSnapshotState.Snapshot {
        val members = (room?.memberIds ?: WeChatApis.contact().chatrooms()?.getMemberIds(group).orEmpty())
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (members.isEmpty()) return GroupSnapshotState.Snapshot(emptySet(), emptyMap())
        val names = if (needsNames) {
            val resolved = WeChatApis.contact().contacts()?.getGroupMemberRoomDisplayNames(group).orEmpty()
                .filterKeys { it in members }.mapValues { (id, name) -> name.trim().takeUnless { it == id }.orEmpty() }
                .toMutableMap()
            // Preserve the room record fallback when the native nickname decoder has partial data.
            if (room != null && room.rawDisplayNames.isNotBlank() && !resolved.keys.containsAll(members)) {
                val raw = room.rawDisplayNames
                val parts = listOf("\u0001", "\u0002", "\n", ";").asSequence()
                    .map { raw.split(it) }.firstOrNull { it.size == room.memberIds.size }
                if (parts != null) room.memberIds.forEachIndexed { index, member ->
                    if (member in members && !resolved.containsKey(member)) {
                        resolved[member] = parts[index].trim().takeUnless { it == member }.orEmpty()
                    }
                }
            }
            resolved
        } else emptyMap()
        return GroupSnapshotState.Snapshot(members, names.takeIf { it.keys.containsAll(members) }.orEmpty())
    }

    @Synchronized
    private fun onChange(change: WeChatChatroomChangeApi.ChatroomChange) {
        val group = change.chatroomId().trim()
        if (!state.needs(group) || (!change.mayMemberListChanged() && !change.mayRoomDataChanged())) return
        val accountChanged = state.account(identity())
        if (accountChanged) { recentNames.clear(); preload() }
        val token = state.generation
        val old = state.snapshots[group]
        var next = read(change.chatroom, group, state.needsNames(group))
        checkAccount()
        if (token != state.generation) { preload(); return }
        // A failed/temporarily empty host query must not look like every member just left.
        if (next.members.isEmpty()) return
        val now = System.currentTimeMillis()
        val bulk = old != null && (old.members - next.members).isEmpty() && (next.members - old.members).size >= 10
        next = next.copy(warmUntil = if (old == null || bulk) now + 15_000L else old.warmUntil)
        old?.names?.filterKeys { it !in next.members }?.forEach { (member, name) -> recentNames["$group|$member"] = name }
        while (recentNames.size > 256) recentNames.remove(recentNames.keys.first())
        val previous = callbacks.keys.associateWith { owner -> state.previous(owner, group) }
        state.commit(group, next)
        callbacks.toMap().forEach { (owner, callback) ->
            if (state.wants(owner, group)) callback(group, previous[owner], next, change)
        }
    }

    companion object {
        private val instances = HashMap<String, GroupMonitorSnapshots>()
        @Synchronized fun get(context: Context): GroupMonitorSnapshots = instances.getOrPut(context.packageName) {
            GroupMonitorSnapshots(context.applicationContext ?: context)
        }
    }
}
