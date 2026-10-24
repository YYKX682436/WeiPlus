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
    // Cache only the discovered IDs; snapshots themselves own per-group data readiness.
    private var allGroups: Set<String>? = null
    private val recentNames = LinkedHashMap<String, String>()

    private fun identity(): String = context.packageName + ":" +
        WeChatApis.contact().account()?.sessionIdentity().orEmpty()

    private fun checkAccount() {
        if (state.account(identity())) { recentNames.clear(); allGroups = null }
    }

    @Synchronized
    fun configure(owner: String, demand: GroupSnapshotState.Demand, invalidate: Boolean = false,
                  callback: (String, GroupSnapshotState.Snapshot?, GroupSnapshotState.Snapshot, WeChatChatroomChangeApi.ChatroomChange) -> Unit) {
        if (state.active() || demand.members.active) checkAccount()
        callbacks[owner] = callback
        val before = state.generation
        state.update(owner, demand)
        if (invalidate && state.generation == before) {
            state.invalidate()
        }
        recentNames.keys.removeAll { !state.needsNames(it.substringBefore('|')) }
        if (!state.all()) allGroups = null
        syncSubscription()
        preload()
    }

    private fun syncSubscription() {
        if (!state.active()) {
            subscriptionToken = null
            subscription?.unsubscribe()
            subscription = null
            recentNames.clear()
            allGroups = null
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
        if (!state.all()) allGroups = null
        recentNames.keys.removeAll { !state.needsNames(it.substringBefore('|')) }
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
        if (!state.active() || preloadQueued) return
        val groups = if (state.all()) allGroups else state.groups()
        if (groups != null && groups.all(state::complete)) return
        preloadQueued = true
        // Both features install in the same turn. Collect their union before querying once.
        executor.schedule({ loadBaseline() }, 20L, TimeUnit.MILLISECONDS)
    }

    private fun loadBaseline() {
        val plan = synchronized(this) {
            if (!state.active()) { preloadQueued = false; return }
            checkAccount()
            if (!state.active()) { preloadQueued = false; return }
            BaselinePlan(state.generation, state.revision,
                (if (state.all()) allGroups else state.groups())?.filterNot(state::complete)?.toSet())
        }
        try {
            val rooms = WeChatApis.contact().chatrooms() ?: return
            // Only the first all-groups load enumerates. Subsequent explicit entries retry missing
            // groups/data, without polling or reloading already complete membership/nickname maps.
            val discovered = if (plan.groups == null) rooms.getChatrooms().associateBy { it.chatroomId.trim() } else emptyMap()
            val groups = synchronized(this) {
                checkAccount()
                if (plan.token != state.generation) return
                if (plan.groups == null) allGroups = discovered.keys + state.snapshots.keys
                plan.groups ?: allGroups.orEmpty()
            }
            for (group in groups) {
                try {
                    val demand = synchronized(this) {
                        checkAccount()
                        if (plan.token != state.generation) return
                        if (!state.needs(group) || state.complete(group)) null
                        else state.snapshots[group]?.members to state.needsNames(group)
                    } ?: continue
                    val knownMembers = demand.first
                    // A nickname-only supplement uses the established members. The contact API
                    // supplies nickname/raw room data in one query; no membership lookup is needed.
                    val room = if (knownMembers != null) null else discovered[group] ?: rooms.getChatroom(group)
                    if (room == null && knownMembers == null) continue
                    val snapshot = read(room, group, demand.second, knownMembers)
                    if (snapshot.members.isEmpty()) continue
                    synchronized(this) {
                        checkAccount()
                        if (state.baseline(plan.token, group, snapshot, plan.revision)) {
                            state.snapshots[group]?.names?.keys?.forEach { recentNames.remove("$group|$it") }
                        }
                    }
                } catch (failure: Exception) {
                    h.Hchat.utils.HLog.e("[Hchat:GroupBaseline] 群资料预载失败: $group", failure)
                }
            }
        } catch (failure: Exception) {
            h.Hchat.utils.HLog.e("[Hchat:GroupBaseline] 预载失败", failure)
        } finally {
            synchronized(this) {
                preloadQueued = false
                // At most one new coalesced pass for a configuration/account transition. Missing
                // host data waits for the next explicit install/DexReady/preload, with no retry loop.
                if (plan.token != state.generation) preload()
            }
        }
    }

    private data class BaselinePlan(val token: Long, val revision: Long, val groups: Set<String>?)

    private fun read(room: WeChatChatroom?, group: String, needsNames: Boolean, knownMembers: Set<String>? = null): GroupSnapshotState.Snapshot {
        val members = (knownMembers ?: room?.memberIds ?: WeChatApis.contact().chatrooms()?.getMemberIds(group).orEmpty())
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
        return GroupSnapshotState.Snapshot(members, names)
    }

    @Synchronized
    private fun onChange(change: WeChatChatroomChangeApi.ChatroomChange) {
        val group = change.chatroomId().trim()
        if (!state.needs(group) || (!change.mayMemberListChanged() && !change.mayRoomDataChanged())) return
        val accountChanged = state.account(identity())
        if (accountChanged) { recentNames.clear(); allGroups = null; preload() }
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
        // Keep a last reliable display value only where the new query is unknown or the member
        // has left. A reliable empty value is not unknown and invalidates an old fallback.
        old?.names?.filter { (member, name) -> name.isNotBlank() && !next.names.containsKey(member) }
            ?.forEach { (member, name) -> recentNames["$group|$member"] = name }
        next.names.keys.forEach { member -> recentNames.remove("$group|$member") }
        if (state.all()) allGroups = allGroups?.plus(group)
        while (recentNames.size > 256) recentNames.remove(recentNames.keys.first())
        val previous = callbacks.keys.associateWith { owner -> state.previous(owner, group) }
        state.commit(group, next)
        callbacks.toMap().forEach { (owner, callback) ->
            if (state.wants(owner, group)) {
                try { callback(group, previous[owner], next, change) }
                catch (failure: Throwable) {
                    h.Hchat.utils.HLog.e("[Hchat:GroupMonitor] 消费者失败: $owner", failure)
                }
            }
        }
    }

    companion object {
        private val instances = HashMap<String, GroupMonitorSnapshots>()
        @Synchronized fun get(context: Context): GroupMonitorSnapshots = instances.getOrPut(context.packageName) {
            GroupMonitorSnapshots(context.applicationContext ?: context)
        }
    }
}
