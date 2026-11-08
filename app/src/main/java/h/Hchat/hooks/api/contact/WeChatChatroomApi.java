package h.Hchat.hooks.api.contact;

import android.content.Context;
import android.text.TextUtils;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.api.conversation.WeChatConversationApi;
import h.Hchat.hooks.api.model.WeChatChatroom;
import h.Hchat.hooks.api.model.WeChatContact;
import h.Hchat.hooks.api.net.WeChatNetworkDispatcher;
import h.Hchat.hooks.api.runtime.WeChatDatabaseApi;
import h.Hchat.hooks.items.batch.BatchGroupCleanupPolicy;
import h.Hchat.hooks.items.batch.BatchGroupSchemaPolicy;
import h.Hchat.hooks.items.batch.BatchGroupStatePolicy;
import h.Hchat.hooks.api.media.WeChatInternalServices;
import h.Hchat.utils.KavaReflector;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 微信群聊 API。
 */
public final class WeChatChatroomApi {
    public static final int ROLE_MEMBER = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_OWNER = 2;
    private static final int ROOM_FLAG_ADMIN = 0x800;
    private static final String GROUP_ID_SQL = "(r.username LIKE '%@chatroom' OR r.username LIKE '%@im.chatroom')";

    public static final class GroupCandidate {
        public final WeChatContact contact;
        public final BatchGroupStatePolicy.CleanupReason cleanupReason;

        GroupCandidate(WeChatContact contact, BatchGroupStatePolicy.CleanupReason cleanupReason) {
            this.contact = contact;
            this.cleanupReason = cleanupReason;
        }

        public String cleanupReasonLabel() {
            if (cleanupReason == BatchGroupStatePolicy.CleanupReason.DISBANDED) return "已解散";
            if (cleanupReason == BatchGroupStatePolicy.CleanupReason.LEFT_OR_REMOVED) return "已退出或被移出";
            return "";
        }
    }

    public static final class GroupCandidateResult {
        public final boolean success;
        public final List<GroupCandidate> candidates;
        public final String error;

        private GroupCandidateResult(boolean success, List<GroupCandidate> candidates, String error) {
            this.success = success;
            this.candidates = candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
            this.error = error == null ? "" : error;
        }
    }

    public static final class GroupOperationStatus {
        public final boolean readSucceeded;
        public final boolean contactPresent;
        public final boolean chatroomRowPresent;
        public final BatchGroupStatePolicy.State state;
        public final BatchGroupStatePolicy.CleanupReason cleanupReason;
        public final String roomOwner;
        public final String error;

        private GroupOperationStatus(GroupStateSnapshot snapshot) {
            readSucceeded = snapshot.readSucceeded;
            contactPresent = snapshot.contactPresent;
            chatroomRowPresent = snapshot.chatroomRowPresent;
            state = snapshot.state;
            cleanupReason = BatchGroupStatePolicy.cleanupReason(snapshot.state);
            roomOwner = snapshot.roomOwner;
            error = snapshot.error;
        }
    }

    private static final class GroupStateSnapshot {
        final boolean readSucceeded;
        final boolean contactPresent;
        final boolean chatroomRowPresent;
        final String memberList;
        final String roomOwner;
        final Integer chatroomStatus;
        final BatchGroupStatePolicy.State state;
        final String error;

        GroupStateSnapshot(boolean readSucceeded, boolean contactPresent, boolean chatroomRowPresent,
                           String memberList, String roomOwner, Integer chatroomStatus,
                           BatchGroupStatePolicy.State state, String error) {
            this.readSucceeded = readSucceeded;
            this.contactPresent = contactPresent;
            this.chatroomRowPresent = chatroomRowPresent;
            this.memberList = memberList;
            this.roomOwner = roomOwner;
            this.chatroomStatus = chatroomStatus;
            this.state = state;
            this.error = error == null ? "" : error;
        }
    }

    private static final class GroupStateSchema {
        final Set<String> rcontactColumns;
        final Set<String> chatroomColumns;
        final boolean hasRoomOwner;
        final boolean hasChatroomStatus;
        final String error;

        GroupStateSchema(Set<String> rcontactColumns, Set<String> chatroomColumns,
                         boolean hasRoomOwner, boolean hasChatroomStatus, String error) {
            this.rcontactColumns = rcontactColumns == null ? Collections.emptySet() : rcontactColumns;
            this.chatroomColumns = chatroomColumns == null ? Collections.emptySet() : chatroomColumns;
            this.hasRoomOwner = hasRoomOwner;
            this.hasChatroomStatus = hasChatroomStatus;
            this.error = error == null ? "" : error;
        }

        boolean success() { return error.isEmpty(); }
    }

    private static final class GroupStateReadResult {
        final boolean success;
        final Map<String, GroupStateSnapshot> states;
        final String error;

        private GroupStateReadResult(boolean success, Map<String, GroupStateSnapshot> states, String error) {
            this.success = success;
            this.states = states == null ? Collections.emptyMap() : states;
            this.error = error == null ? "" : error;
        }

        static GroupStateReadResult success(Map<String, GroupStateSnapshot> states) {
            return new GroupStateReadResult(true, states, "");
        }

        static GroupStateReadResult failed(String error) {
            return new GroupStateReadResult(false, null, error);
        }
    }

    public interface Logger {
        void log(String message);
    }

    private final WeChatDatabaseApi databaseApi;
    private final WeChatContactApi contactApi;
    private final WeChatAccountApi accountApi;
    private final WeakReference<Context> hostContext;
    private final PendingGroupExitGuard pendingGroupExitGuard;
    private final DexFinder dexFinder;
    private final WeChatNetworkDispatcher networkDispatcher;
    private final Logger logger;
    private final Map<String, Boolean> columnExistsCache = new ConcurrentHashMap<>();
    private volatile boolean networkHookInstalled;
    private volatile GroupStateSchema groupStateSchema;

    public WeChatChatroomApi(WeChatDatabaseApi databaseApi,
                             WeChatContactApi contactApi,
                             Logger logger) {
        this(null, null, databaseApi, contactApi, null, null, logger);
    }

    public WeChatChatroomApi(WeChatDatabaseApi databaseApi,
                             WeChatContactApi contactApi,
                             DexFinder dexFinder,
                             WeChatNetworkDispatcher networkDispatcher,
                             Logger logger) {
        this(null, null, databaseApi, contactApi, dexFinder, networkDispatcher, logger);
    }

    public WeChatChatroomApi(Context hostContext,
                             WeChatAccountApi accountApi,
                             WeChatDatabaseApi databaseApi,
                             WeChatContactApi contactApi,
                             DexFinder dexFinder,
                             WeChatNetworkDispatcher networkDispatcher,
                             Logger logger) {
        Context applicationContext = hostContext != null && hostContext.getApplicationContext() != null
                ? hostContext.getApplicationContext() : hostContext;
        this.hostContext = applicationContext != null ? new WeakReference<>(applicationContext) : null;
        this.accountApi = accountApi;
        this.pendingGroupExitGuard = applicationContext != null
                ? new PendingGroupExitGuard(applicationContext) : null;
        this.databaseApi = databaseApi;
        this.contactApi = contactApi;
        this.dexFinder = dexFinder;
        this.networkDispatcher = networkDispatcher;
        this.logger = logger;
    }

    /**
     * Loads every confirmed current member group from rcontact + chatroom, including groups that
     * have no homepage conversation. Missing/empty roster data is omitted as unknown.
     */
    public GroupCandidateResult getJoinedGroupCandidates() {
        String selfId = accountApi != null ? accountApi.selfWxId() : "";
        if (TextUtils.isEmpty(selfId)) return candidatesFailed("无法确认当前微信账号");
        WeChatContactApi.ContactListResult groupResult = contactApi != null
                ? contactApi.getGroupsResult() : null;
        if (groupResult == null || !groupResult.success) {
            return candidatesFailed(groupResult != null && !TextUtils.isEmpty(groupResult.error)
                    ? groupResult.error : "读取群聊联系人失败");
        }
        GroupStateReadResult stateResult = readAllGroupStates(selfId);
        if (!stateResult.success) return candidatesFailed(stateResult.error);
        Map<String, GroupStateSnapshot> states = stateResult.states;
        GroupStateSchema schema = readGroupStateSchema();
        if (!schema.hasRoomOwner) {
            return candidatesFailed("chatroom.roomowner 字段不可用，不能安全确认群主并列出退群候选");
        }
        List<GroupCandidate> candidates = new ArrayList<>();
        int confirmedJoined = 0;
        boolean hasOwnerProof = false;
        boolean sampleProofLogged = false;
        for (WeChatContact contact : groupResult.contacts) {
            if (contact == null || TextUtils.isEmpty(contact.wxId)) continue;
            GroupStateSnapshot state = states.get(contact.wxId);
            if (state == null || !BatchGroupStatePolicy.isLeaveCandidate(state.state, selfId, state.roomOwner)) continue;
            confirmedJoined++;
            hasOwnerProof = true;
            candidates.add(new GroupCandidate(contact, null));
            if (!sampleProofLogged) {
                Boolean nativeAlreadyQuit = readNativeAlreadyQuit(contact.wxId);
                log("群状态脱敏普通群样本: memberListContainsSelf="
                        + memberListContains(state.memberList, selfId)
                        + " nativeAlreadyQuit=" + nativeAlreadyQuit
                        + " roomOwnerKnown=" + !TextUtils.isEmpty(state.roomOwner)
                        + " selfIsOwner=" + selfId.equals(state.roomOwner));
                sampleProofLogged = true;
            }
        }
        log("群聊退群候选读取完成: contacts=" + groupResult.contacts.size()
                + " rows=" + states.size() + " confirmedJoinedNonOwner=" + confirmedJoined
                + " selfRosterAndNativeStateAgreed=" + (confirmedJoined > 0)
                + " ownerConfirmed=" + hasOwnerProof);
        return new GroupCandidateResult(true, candidates, "");
    }

    /** Only groups whose native status proves they are disbanded or no longer contain this user. */
    public GroupCandidateResult getInvalidGroupCandidates() {
        String selfId = accountApi != null ? accountApi.selfWxId() : "";
        if (TextUtils.isEmpty(selfId)) return candidatesFailed("无法确认当前微信账号");
        WeChatContactApi.ContactListResult groupResult = contactApi != null
                ? contactApi.getGroupsResult() : null;
        if (groupResult == null || !groupResult.success) {
            return candidatesFailed(groupResult != null && !TextUtils.isEmpty(groupResult.error)
                    ? groupResult.error : "读取群聊联系人失败");
        }
        GroupStateReadResult stateResult = readAllGroupStates(selfId);
        if (!stateResult.success) return candidatesFailed(stateResult.error);
        Map<String, GroupStateSnapshot> states = stateResult.states;
        List<GroupCandidate> candidates = new ArrayList<>();
        int leftOrRemoved = 0;
        int disbanded = 0;
        for (WeChatContact contact : groupResult.contacts) {
            if (contact == null || TextUtils.isEmpty(contact.wxId)) continue;
            GroupStateSnapshot state = states.get(contact.wxId);
            if (state == null || !state.readSucceeded || !state.contactPresent || !state.chatroomRowPresent) continue;
            BatchGroupStatePolicy.CleanupReason reason = BatchGroupStatePolicy.cleanupReason(state.state);
            if (reason != null) {
                candidates.add(new GroupCandidate(contact, reason));
                if (reason == BatchGroupStatePolicy.CleanupReason.DISBANDED) disbanded++;
                else leftOrRemoved++;
            }
        }
        log("群聊失效候选读取完成: contacts=" + groupResult.contacts.size()
                + " rows=" + states.size() + " leftOrRemoved=" + leftOrRemoved
                + " disbanded=" + disbanded + " unknownExcluded=" + Math.max(0, states.size() - candidates.size()));
        return new GroupCandidateResult(true, candidates, "");
    }

    /** Live state read used again immediately before a selected group operation. */
    public GroupOperationStatus getGroupOperationStatus(String chatroomId) {
        String roomId = chatroomId == null ? "" : chatroomId.trim();
        String selfId = accountApi != null ? accountApi.selfWxId() : "";
        if (!isGroup(roomId) || TextUtils.isEmpty(selfId)) {
            return new GroupOperationStatus(groupStateReadFailed("无法确认群聊或当前账号"));
        }
        return new GroupOperationStatus(readGroupState(roomId, selfId));
    }

    /** Uses the exact native batch process and fails closed for owners or stale group state. */
    public NativeBatchQuitChatroomProcess.Result submitNativeBatchQuit(
            List<String> roomIds,
            String expectedAccount,
            BooleanSupplier shouldContinue) {
        List<String> targets = roomIds == null ? Collections.emptyList() : new ArrayList<>(roomIds);
        if (targets.isEmpty() || targets.size() > NativeBatchQuitChatroomProcess.MAX_BATCH_SIZE) {
            return NativeBatchQuitChatroomProcess.Result.failed("微信原生退群单批上限为20个群聊");
        }
        if (accountApi == null || TextUtils.isEmpty(expectedAccount)
                || !expectedAccount.trim().equals(accountApi.sessionIdentity())) {
            return NativeBatchQuitChatroomProcess.Result.failed("账号已切换或当前账号不可用");
        }
        String selfId = accountApi.selfWxId();
        if (TextUtils.isEmpty(selfId)) {
            return NativeBatchQuitChatroomProcess.Result.failed("无法确认当前微信账号");
        }
        for (String target : targets) {
            if (!isGroup(target)) {
                return NativeBatchQuitChatroomProcess.Result.failed("所选对象已不是群聊；未提交退群请求");
            }
            GroupStateSnapshot state = readGroupState(target, selfId);
            if (!state.readSucceeded || !state.contactPresent || !state.chatroomRowPresent
                    || !BatchGroupStatePolicy.isLeaveCandidate(state.state, selfId, state.roomOwner)) {
                return NativeBatchQuitChatroomProcess.Result.failed(
                        "群聊状态、成员身份或群主状态已变化；未提交该批退群请求");
            }
            if (shouldContinue != null && !safeContinue(shouldContinue)) {
                return NativeBatchQuitChatroomProcess.Result.failed("账号已切换或任务已停止");
            }
        }
        Context context = hostContext != null ? hostContext.get() : null;
        if (context == null || dexFinder == null) {
            return NativeBatchQuitChatroomProcess.Result.failed("微信原生退群 API 未就绪");
        }
        if (pendingGroupExitGuard != null) {
            for (String target : targets) pendingGroupExitGuard.mark(expectedAccount, target);
        }
        NativeBatchQuitChatroomProcess.Result nativeResult = NativeBatchQuitChatroomProcess.execute(
                context, dexFinder, targets, this::log);
        for (String id : nativeResult.submittedIds) {
            GroupStateSnapshot after = readGroupState(id, selfId);
            boolean membershipResolved = after.readSucceeded && (
                    after.state == BatchGroupStatePolicy.State.NOT_MEMBER
                            || after.state == BatchGroupStatePolicy.State.DISBANDED
                            || after.state == BatchGroupStatePolicy.State.CONTACT_MISSING);
            boolean uncertain = !membershipResolved && (
                    nativeResult.serverAcceptedIds.contains(id)
                            || nativeResult.timedOut
                            || nativeResult.networkError
                            || nativeResult.accountDetached
                            || !nativeResult.completed
                            || nativeResult.retrySuppressedIds.contains(id));
            if (pendingGroupExitGuard != null) {
                if (uncertain) pendingGroupExitGuard.mark(expectedAccount, id);
                else if (membershipResolved) pendingGroupExitGuard.clear(expectedAccount, id);
                else if (nativeResult.completed && nativeResult.failedIds.contains(id)
                        && !nativeResult.serverAcceptedIds.contains(id)) {
                    pendingGroupExitGuard.clear(expectedAccount, id);
                }
            }
        }
        if (pendingGroupExitGuard != null) {
            for (String id : targets) {
                if (!nativeResult.submittedIds.contains(id)) pendingGroupExitGuard.clear(expectedAccount, id);
            }
        }
        return nativeResult;
    }

    /**
     * Rechecks the selected invalid reason, then uses WeChat's own conversation and whole-room
     * deletion helpers. It never deletes message rows or writes rcontact/chatroom SQL directly.
     */
    public WeChatNativeMutationResult deleteInvalidGroupNative(
            String chatroomId,
            BatchGroupStatePolicy.CleanupReason expectedReason,
            WeChatConversationApi conversations,
            WeChatContactApi.BatchSubmissionGuard guard) {
        String roomId = chatroomId == null ? "" : chatroomId.trim();
        if (!isGroup(roomId) || expectedReason == null || conversations == null) {
            return mutation(WeChatNativeMutationResult.Outcome.INVALID_TARGET, "无效群聊对象");
        }
        if (databaseApi == null || !databaseApi.isAvailable() || accountApi == null) {
            return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED, "群聊或账号 API 不可用");
        }
        String selfId = accountApi.selfWxId();
        if (TextUtils.isEmpty(selfId)) {
            return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED, "无法确认当前微信账号");
        }
        GroupStateSnapshot before = readGroupState(roomId, selfId);
        if (!before.readSucceeded) {
            return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED, "复核群聊状态失败");
        }
        if (!before.contactPresent || !before.chatroomRowPresent
                || !BatchGroupStatePolicy.stillMatchesCleanupReason(before.state, expectedReason)) {
            return mutation(WeChatNativeMutationResult.Outcome.INVALID_TARGET,
                    "群聊状态已变化或已可用，未清理");
        }
        if (dexFinder == null || !dexFinder.resolveDeleteWholeChatroomApi()
                || dexFinder.deleteWholeChatroomMethod == null) {
            return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED,
                    "微信原生群资料删除入口不可用");
        }
        if (!maySubmit(guard)) {
            return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED, "账号已切换或任务已停止");
        }
        Boolean conversationPresent = conversations.conversationEntryExistsState(roomId);
        if (conversationPresent == null) {
            return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED,
                    "无法读取残留会话状态，未删除群资料");
        }
        BatchGroupCleanupPolicy.ConversationState conversationState = conversationPresent
                ? BatchGroupCleanupPolicy.ConversationState.EXISTS
                : BatchGroupCleanupPolicy.ConversationState.ABSENT;
        BatchGroupCleanupPolicy.Action cleanupAction = BatchGroupCleanupPolicy.decide(
                true, conversationState);
        if (cleanupAction == BatchGroupCleanupPolicy.Action.ABORT) {
            return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED,
                    "残留会话状态不可确认，未删除群资料");
        }
        boolean conversationWasRemoved = cleanupAction
                == BatchGroupCleanupPolicy.Action.DELETE_CONVERSATION_THEN_GROUP;
        if (conversationWasRemoved) {
            if (!maySubmit(guard)) {
                return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED,
                        "账号已切换或任务已停止，未删除会话");
            }
            boolean conversationRemoved;
            try {
                conversationRemoved = conversations.deleteConversation(roomId);
            } catch (Throwable e) {
                log("调用微信原生会话删除失败: " + e.getMessage());
                return mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                        "原生会话删除失败或未确认，未删除群资料");
            }
            if (!conversationRemoved) {
                Boolean remains = conversations.conversationEntryExistsState(roomId);
                if (remains == null || remains) {
                    return mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                            "原生会话删除失败或状态未确认，未删除群资料");
                }
                conversationWasRemoved = true;
            }
        }

        if (!maySubmit(guard)) {
            return mutation(conversationWasRemoved
                            ? WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED
                            : WeChatNativeMutationResult.Outcome.NOT_SUBMITTED,
                    conversationWasRemoved
                            ? "会话已移除；账号或任务状态变化，群资料删除未继续"
                            : "账号或任务状态变化，群资料删除未提交");
        }
        GroupStateSnapshot beforeRoomRemoval = readGroupState(roomId, selfId);
        if (!beforeRoomRemoval.readSucceeded) {
            return mutation(conversationWasRemoved
                            ? WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED
                            : WeChatNativeMutationResult.Outcome.READ_FAILED,
                    conversationWasRemoved
                            ? "会话已移除；群聊状态复核失败，群资料删除未继续"
                            : "群聊状态复核失败，未删除群资料");
        }
        if (!beforeRoomRemoval.contactPresent || !beforeRoomRemoval.chatroomRowPresent
                || !BatchGroupStatePolicy.stillMatchesCleanupReason(
                        beforeRoomRemoval.state, expectedReason)) {
            return mutation(conversationWasRemoved
                            ? WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED
                            : WeChatNativeMutationResult.Outcome.INVALID_TARGET,
                    conversationWasRemoved
                            ? "会话已移除；群聊状态已变化，群资料删除未继续"
                            : "群聊状态已变化，未删除群资料");
        }

        try {
            Object value = KavaReflector.invokeOrThrow(
                    dexFinder.deleteWholeChatroomMethod, null, roomId);
            if (value instanceof Boolean && !((Boolean) value)) {
                return mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                        conversationWasRemoved
                                ? "微信原生群资料删除未确认；会话已移除"
                                : "微信原生群资料删除未确认；会话此前不存在");
            }
        } catch (Throwable e) {
            log("调用微信原生群资料删除失败: " + e.getMessage());
            return mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                    conversationWasRemoved
                            ? "群资料删除未确认；会话已移除"
                            : "群资料删除未确认；会话此前不存在");
        }
        return confirmNativeGroupRemoved(roomId, conversations)
                ? mutation(WeChatNativeMutationResult.Outcome.CONFIRMED,
                        "已按微信原生方式移除失效群资料与残留会话；聊天记录未额外清空")
                : mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                        "已提交微信原生群资料删除；最终状态未确认");
    }

    private boolean confirmNativeGroupRemoved(String roomId, WeChatConversationApi conversations) {
        WeChatDatabaseApi.QueryResult contact = databaseApi.queryWithStatus(
                "SELECT username FROM rcontact WHERE username=? LIMIT 1", new String[]{roomId});
        WeChatDatabaseApi.QueryResult room = databaseApi.queryWithStatus(
                "SELECT chatroomname FROM chatroom WHERE chatroomname=? LIMIT 1", new String[]{roomId});
        Boolean conversation = conversations.conversationEntryExistsState(roomId);
        return contact.success && contact.rows.isEmpty()
                && room.success && room.rows.isEmpty()
                && Boolean.FALSE.equals(conversation);
    }

    private GroupStateReadResult readAllGroupStates(String selfId) {
        GroupStateSchema schema = readGroupStateSchema();
        if (!schema.success()) return GroupStateReadResult.failed(schema.error);
        if (dexFinder == null || !dexFinder.resolveChatroomAlreadyQuitApi()) {
            return GroupStateReadResult.failed("无法定位微信原生群聊已退出状态方法 w41/v1.E(String)");
        }

        String roomOwnerColumn = schema.hasRoomOwner ? "c.roomowner" : "NULL";
        String statusColumn = schema.hasChatroomStatus ? "c.chatroomStatus" : "NULL";
        String roomProbeSql = "SELECT COUNT(*) AS rowCount FROM chatroom";
        WeChatDatabaseApi.QueryResult roomProbe = databaseApi.queryWithStatus(roomProbeSql, null);
        if (!roomProbe.success) {
            return GroupStateReadResult.failed("chatroom 群资料查询失败: " + roomProbe.error);
        }

        String sql = "SELECT r.username AS username, c.chatroomname AS chatroomname, "
                + "c.memberlist AS memberlist, " + roomOwnerColumn + " AS roomowner, "
                + statusColumn + " AS chatroomStatus "
                + "FROM rcontact r LEFT JOIN chatroom c ON c.chatroomname=r.username WHERE "
                + GROUP_ID_SQL + " ORDER BY r.username";
        WeChatDatabaseApi.QueryResult query = databaseApi.queryWithStatus(sql, null);
        if (!query.success) {
            return GroupStateReadResult.failed("rcontact 与 chatroom 群状态 JOIN 查询失败: " + query.error);
        }

        Map<String, GroupStateSnapshot> result = new LinkedHashMap<>();
        int unknownCount = 0;
        int rosterJoinedCount = 0;
        int nativeJoinedConfirmedCount = 0;
        int membershipConflictCount = 0;
        int rosterNotMemberCount = 0;
        int nativeLeftConfirmedCount = 0;
        int nativeMembershipUnreadableCount = 0;
        boolean sampleProofLogged = false;
        for (Map<String, Object> row : query.rows) {
            String id = str(row, "username");
            if (TextUtils.isEmpty(id)) continue;
            boolean roomPresent = !TextUtils.isEmpty(str(row, "chatroomname"));
            String memberList = str(row, "memberlist");
            String roomOwner = str(row, "roomowner");
            Integer status = schema.hasChatroomStatus ? nullableIntValue(row, "chatroomStatus") : null;
            Boolean nativeAlreadyQuit = readNativeAlreadyQuit(id);
            boolean statusValueReadable = !schema.hasChatroomStatus || status != null;
            BatchGroupStatePolicy.State rosterState = statusValueReadable
                    ? BatchGroupStatePolicy.classify(true, true, roomPresent, selfId, memberList, status)
                    : BatchGroupStatePolicy.State.UNKNOWN;
            if (rosterState == BatchGroupStatePolicy.State.JOINED) {
                rosterJoinedCount++;
                if (nativeAlreadyQuit == null) nativeMembershipUnreadableCount++;
                else if (nativeAlreadyQuit) membershipConflictCount++;
                else nativeJoinedConfirmedCount++;
            } else if (rosterState == BatchGroupStatePolicy.State.NOT_MEMBER) {
                rosterNotMemberCount++;
                if (nativeAlreadyQuit == null) nativeMembershipUnreadableCount++;
                else if (nativeAlreadyQuit) nativeLeftConfirmedCount++;
                else membershipConflictCount++;
            }
            boolean needsNativeMemberCheck = rosterState == BatchGroupStatePolicy.State.JOINED
                    || rosterState == BatchGroupStatePolicy.State.NOT_MEMBER;
            boolean stateReadSucceeded = statusValueReadable
                    && (!needsNativeMemberCheck || nativeAlreadyQuit != null);
            BatchGroupStatePolicy.State state = stateReadSucceeded
                    ? BatchGroupStatePolicy.classify(
                            true, true, roomPresent, selfId, memberList, status, nativeAlreadyQuit)
                    : BatchGroupStatePolicy.State.UNKNOWN;
            String error = !statusValueReadable
                    ? "chatroom.chatroomStatus 当前行为空或格式无效"
                    : needsNativeMemberCheck && nativeAlreadyQuit == null
                            ? "微信原生群成员状态读取失败，当前群标记为 UNKNOWN" : "";
            if (state == BatchGroupStatePolicy.State.UNKNOWN) unknownCount++;
            if (!sampleProofLogged && state == BatchGroupStatePolicy.State.JOINED
                    && !TextUtils.isEmpty(roomOwner) && !selfId.equals(roomOwner)) {
                log("群状态 JOIN 脱敏样本: memberListContainsSelf="
                        + memberListContains(memberList, selfId)
                        + " nativeAlreadyQuit=" + nativeAlreadyQuit
                        + " roomOwnerKnown=true selfIsOwner=false");
                sampleProofLogged = true;
            }
            result.put(id, applyPendingExitGuard(id, new GroupStateSnapshot(
                    stateReadSucceeded, true, roomPresent, memberList, roomOwner, status, state, error)));
        }
        log("群状态 JOIN 读取完成: rcontactGroups=" + result.size()
                + " chatroomStatusColumn=" + schema.hasChatroomStatus
                + " roomownerColumn=" + schema.hasRoomOwner
                + " rosterJoined=" + rosterJoinedCount
                + " nativeJoinedConfirmed=" + nativeJoinedConfirmedCount
                + " rosterNotMember=" + rosterNotMemberCount
                + " nativeLeftConfirmed=" + nativeLeftConfirmedCount
                + " nativeStateConflicts=" + membershipConflictCount
                + " nativeStateUnreadable=" + nativeMembershipUnreadableCount
                + " unknownRows=" + unknownCount);
        return GroupStateReadResult.success(result);
    }

    private GroupStateSnapshot readGroupState(String roomId, String selfId) {
        GroupStateSchema schema = readGroupStateSchema();
        if (!schema.success()) return groupStateReadFailed(schema.error);
        if (dexFinder == null || !dexFinder.resolveChatroomAlreadyQuitApi()) {
            return groupStateReadFailed("无法定位微信原生群聊已退出状态方法 w41/v1.E(String)");
        }
        String roomOwnerColumn = schema.hasRoomOwner ? "c.roomowner" : "NULL";
        String statusColumn = schema.hasChatroomStatus ? "c.chatroomStatus" : "NULL";
        WeChatDatabaseApi.QueryResult query = databaseApi.queryWithStatus(
                "SELECT r.username AS username, c.chatroomname AS chatroomname, "
                        + "c.memberlist AS memberlist, " + roomOwnerColumn + " AS roomowner, "
                        + statusColumn + " AS chatroomStatus "
                        + "FROM rcontact r LEFT JOIN chatroom c ON c.chatroomname=r.username "
                        + "WHERE r.username=? AND " + GROUP_ID_SQL + " LIMIT 1",
                new String[]{roomId});
        if (!query.success) return groupStateReadFailed("群聊状态查询失败: " + query.error);
        if (query.rows.isEmpty()) {
            return applyPendingExitGuard(roomId, new GroupStateSnapshot(true, false, false, null, "", null,
                    BatchGroupStatePolicy.State.CONTACT_MISSING, ""));
        }
        Map<String, Object> row = query.rows.get(0);
        boolean roomPresent = !TextUtils.isEmpty(str(row, "chatroomname"));
        String memberList = str(row, "memberlist");
        String owner = str(row, "roomowner");
        Integer status = schema.hasChatroomStatus ? nullableIntValue(row, "chatroomStatus") : null;
        Boolean nativeAlreadyQuit = readNativeAlreadyQuit(roomId);
        boolean statusValueReadable = !schema.hasChatroomStatus || status != null;
        BatchGroupStatePolicy.State rosterState = statusValueReadable
                ? BatchGroupStatePolicy.classify(true, true, roomPresent, selfId, memberList, status)
                : BatchGroupStatePolicy.State.UNKNOWN;
        boolean needsNativeMemberCheck = rosterState == BatchGroupStatePolicy.State.JOINED
                || rosterState == BatchGroupStatePolicy.State.NOT_MEMBER;
        boolean stateReadSucceeded = statusValueReadable
                && (!needsNativeMemberCheck || nativeAlreadyQuit != null);
        BatchGroupStatePolicy.State state = stateReadSucceeded
                ? BatchGroupStatePolicy.classify(
                        true, true, roomPresent, selfId, memberList, status, nativeAlreadyQuit)
                : BatchGroupStatePolicy.State.UNKNOWN;
        String error = !statusValueReadable
                ? "chatroom.chatroomStatus 当前行为空或格式无效"
                : needsNativeMemberCheck && nativeAlreadyQuit == null
                        ? "微信原生群成员状态读取失败" : "";
        return applyPendingExitGuard(roomId, new GroupStateSnapshot(
                stateReadSucceeded, true, roomPresent, memberList, owner, status, state, error));
    }

    private GroupStateSchema readGroupStateSchema() {
        GroupStateSchema cached = groupStateSchema;
        if (cached != null) return cached;
        if (databaseApi == null || !databaseApi.isAvailable()) {
            return new GroupStateSchema(null, null, false, false, "微信数据库 API 不可用");
        }
        WeChatDatabaseApi.QueryResult contactColumns = databaseApi.queryWithStatus(
                "PRAGMA table_info(rcontact)", null);
        if (!contactColumns.success) {
            return new GroupStateSchema(null, null, false, false,
                    "读取 rcontact 表结构失败: " + contactColumns.error);
        }
        WeChatDatabaseApi.QueryResult roomColumns = databaseApi.queryWithStatus(
                "PRAGMA table_info(chatroom)", null);
        if (!roomColumns.success) {
            return new GroupStateSchema(null, null, false, false,
                    "读取 chatroom 表结构失败: " + roomColumns.error);
        }
        Set<String> contactNames = columnNames(contactColumns.rows);
        Set<String> roomNames = columnNames(roomColumns.rows);
        BatchGroupSchemaPolicy.Result schemaResult = BatchGroupSchemaPolicy.evaluate(
                true, contactNames, true, roomNames);
        if (!schemaResult.success) {
            return new GroupStateSchema(contactNames, roomNames, false, false, schemaResult.error);
        }
        GroupStateSchema schema = new GroupStateSchema(
                contactNames, roomNames, schemaResult.hasRoomOwner, schemaResult.hasChatroomStatus, "");
        groupStateSchema = schema;
        log("已核验群状态 SQLite 表结构: rcontact.username=" + contactNames.contains("username")
                + " chatroom.chatroomname=" + roomNames.contains("chatroomname")
                + " chatroom.memberlist=" + roomNames.contains("memberlist")
                + " chatroom.roomowner=" + schema.hasRoomOwner
                + " chatroom.chatroomStatus=" + schema.hasChatroomStatus
                + " rcontact.chatroomStatus=" + contactNames.contains("chatroomStatus"));
        return schema;
    }

    private static Set<String> columnNames(List<Map<String, Object>> rows) {
        Set<String> names = new HashSet<>();
        if (rows == null) return names;
        for (Map<String, Object> row : rows) {
            String name = str(row, "name");
            if (!TextUtils.isEmpty(name)) names.add(name);
        }
        return names;
    }

    private static boolean memberListContains(String rawMembers, String target) {
        if (TextUtils.isEmpty(rawMembers) || TextUtils.isEmpty(target)) return false;
        for (String member : rawMembers.split(";", -1)) {
            if (target.equals(member.trim())) return true;
        }
        return false;
    }

    /** Returns the native "already quit" predicate, or null if WeChat cannot verify it. */
    private Boolean readNativeAlreadyQuit(String roomId) {
        if (dexFinder == null || !dexFinder.resolveChatroomAlreadyQuitApi()
                || dexFinder.chatroomAlreadyQuitMethod == null) return null;
        try {
            Object value = KavaReflector.invokeOrThrow(dexFinder.chatroomAlreadyQuitMethod, null, roomId);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Throwable e) {
            log("读取微信原生群成员状态失败: " + e.getMessage());
            return null;
        }
    }

    private GroupStateSnapshot applyPendingExitGuard(String roomId, GroupStateSnapshot snapshot) {
        if (pendingGroupExitGuard == null || accountApi == null || !snapshot.readSucceeded) return snapshot;
        String account = accountApi.sessionIdentity();
        if (BatchGroupStatePolicy.resolvesPendingRequest(snapshot.state)) {
            pendingGroupExitGuard.clear(account, roomId);
            return snapshot;
        }
        if (!BatchGroupStatePolicy.shouldBlockRepeat(snapshot.state,
                pendingGroupExitGuard.contains(account, roomId))) return snapshot;
        return new GroupStateSnapshot(true, snapshot.contactPresent, snapshot.chatroomRowPresent,
                snapshot.memberList, snapshot.roomOwner, snapshot.chatroomStatus,
                BatchGroupStatePolicy.State.UNKNOWN,
                "退群结果尚未确认；等待微信原生成员状态更新，禁止重复提交");
    }

    private GroupStateSnapshot groupStateReadFailed(String error) {
        return new GroupStateSnapshot(false, false, false, null, "", null,
                BatchGroupStatePolicy.State.UNKNOWN, error);
    }

    private GroupCandidateResult candidatesFailed(String error) {
        return new GroupCandidateResult(false, Collections.emptyList(), error);
    }

    public boolean isAvailable() {
        return databaseApi != null && databaseApi.isAvailable()
                && contactApi != null && contactApi.isAvailable();
    }

    public WeChatChatroom getChatroom(String chatroomId) {
        if (!isGroup(chatroomId) || databaseApi == null) return null;
        try {
            List<Map<String, Object>> rows = databaseApi.query(
                    "SELECT chatroomname, memberlist, displayname, roomowner "
                            + "FROM chatroom WHERE chatroomname=? LIMIT 1",
                    new String[]{chatroomId});
            if (rows.isEmpty()) return null;
            Map<String, Object> row = rows.get(0);
            return new WeChatChatroom(
                    str(row, "chatroomname"),
                    getChatroomName(chatroomId),
                    str(row, "roomowner"),
                    splitMembers(str(row, "memberlist")),
                    str(row, "displayname"));
        } catch (Throwable e) {
            log("读取群聊失败: " + chatroomId + " " + e.getMessage());
            return null;
        }
    }

    public List<WeChatChatroom> getChatrooms() {
        List<WeChatChatroom> result = new ArrayList<>();
        if (contactApi == null) return result;
        for (WeChatContact contact : contactApi.getGroups()) {
            WeChatChatroom room = getChatroom(contact.wxId);
            if (room != null) {
                result.add(room);
            } else {
                result.add(new WeChatChatroom(
                        contact.wxId, contact.displayName(), "", new ArrayList<>(), ""));
            }
        }
        return result;
    }

    public String getChatroomName(String chatroomId) {
        if (TextUtils.isEmpty(chatroomId)) return "";
        String name = contactApi != null ? contactApi.getDisplayName(chatroomId) : "";
        return !TextUtils.isEmpty(name) ? name : chatroomId;
    }

    public String getOwner(String chatroomId) {
        if (TextUtils.isEmpty(chatroomId) || databaseApi == null) return "";
        try {
            String owner = databaseApi.queryFirstString(
                    "SELECT roomowner FROM chatroom WHERE chatroomname=? LIMIT 1",
                    new String[]{chatroomId},
                    "roomowner");
            if (!TextUtils.isEmpty(owner)) return owner.trim();
        } catch (Throwable e) {
            log("读取群主失败: " + chatroomId + " " + e.getMessage());
        }
        WeChatChatroom room = getChatroom(chatroomId);
        return room != null ? room.owner : "";
    }

    public List<String> getMemberIds(String chatroomId) {
        if (contactApi != null) return contactApi.getGroupMemberIds(chatroomId);
        WeChatChatroom room = getChatroom(chatroomId);
        return room != null ? room.memberIds : new ArrayList<>();
    }

    public int getMemberCount(String chatroomId) {
        return getMemberIds(chatroomId).size();
    }

    /**
     * Reuses ChatroomInfoUI's RoomSDK service method (interface k(contact, save)). The native
     * method owns the local contact update and sends the group-contact operation; it exposes no
     * response callback, so this API reports submission as unconfirmed even if local type changes.
     */
    public WeChatNativeMutationResult setGroupSavedToContacts(
            String chatroomId, boolean saved, WeChatContactApi.BatchSubmissionGuard guard) {
        GroupContactState before = readGroupContactState(chatroomId);
        if (!before.success) return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED, before.error);
        if (!before.found) return mutation(WeChatNativeMutationResult.Outcome.INVALID_TARGET, "群聊联系人已失效");
        if (((before.type & 1) != 0) == saved) {
            return mutation(WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED, "保存通讯录状态已符合目标");
        }
        if (!maySubmit(guard)) return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED, "账号已切换或任务已停止");
        Object preloadedContact = contactApi.getNativeContactObject(chatroomId);
        if (preloadedContact == null) {
            return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED, "读取微信原生群聊联系人对象失败");
        }
        GroupContactState current = readGroupContactState(chatroomId);
        if (!current.success) return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED,
                "提交前读取群聊状态失败");
        if (!current.found) return mutation(WeChatNativeMutationResult.Outcome.INVALID_TARGET,
                "群聊联系人已失效");
        if (((current.type & 1) != 0) == saved) {
            return mutation(WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED, "保存通讯录状态已符合目标");
        }
        Object roomApi;
        Method save;
        try {
            roomApi = nativeRoomApi(chatroomId);
            save = findRoomSaveMethod(roomApi, preloadedContact);
            if (save == null) throw new IllegalStateException("微信原生保存群聊入口不可用");
        } catch (Throwable e) {
            log("准备群聊通讯录请求失败: " + e.getMessage());
            return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED,
                    "微信原生保存群聊入口不可用");
        }
        AtomicBoolean nativeCallStarted = new AtomicBoolean(false);
        try {
            Boolean submitted = WeChatMainThread.call(() -> {
                if (!maySubmit(guard)) throw new IllegalStateException("账号已切换或任务已停止");
                Object nativeContact = contactApi.getNativeContactObject(chatroomId);
                if (nativeContact == null) throw new IllegalStateException("微信原生群聊联系人对象不可用");
                Boolean currentSaved = readNativeGroupSavedFlag(nativeContact);
                if (currentSaved == null) throw new IllegalStateException("读取微信原生群保存状态失败");
                if (currentSaved == saved) return Boolean.FALSE;
                nativeCallStarted.set(true);
                KavaReflector.invokeOrThrow(save, roomApi, nativeContact, saved);
                return Boolean.TRUE;
            });
            if (!Boolean.TRUE.equals(submitted)) {
                return mutation(WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED, "保存通讯录状态已符合目标");
            }
            return mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                    "微信原生保存请求已提交，服务端结果未确认");
        } catch (Throwable e) {
            log("保存群聊到通讯录失败: " + e.getMessage());
            if (nativeCallStarted.get()) {
                return mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                        "原生保存入口已调用，请求结果未确认");
            }
            return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED,
                    e.getMessage() != null && e.getMessage().contains("已切换")
                            ? e.getMessage() : "微信原生保存群聊入口不可用");
        }
    }

    private Boolean readNativeGroupSavedFlag(Object nativeContact) throws Exception {
        Method getter = KavaReflector.findMethodRecursive(nativeContact.getClass(), "r2");
        if (getter == null || getter.getReturnType() != boolean.class || getter.getParameterTypes().length != 0) {
            return null;
        }
        Object value = KavaReflector.invokeOrThrow(getter, nativeContact);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    /**
     * Reuses the ModRemarkRoomNameUI RoomSDK n(roomId, currentWxId, nickname) operation. The
     * native callback receives the server response. Only after a zero error code does this method
     * run the same local chatroom-row update that the native b6 callback performs.
     */
    public WeChatNativeMutationResult setMyGroupNickname(
            String chatroomId,
            String requestedNickname,
            String selfWxId,
            WeChatContactApi.BatchSubmissionGuard guard,
            WeChatContactApi.BatchSubmissionGuard accountGuard) {
        if (!isGroup(chatroomId)) return mutation(WeChatNativeMutationResult.Outcome.INVALID_TARGET, "群聊 ID 无效");
        String roomId = chatroomId.trim();
        String selfId = selfWxId == null ? "" : selfWxId.trim();
        if (selfId.isEmpty()) return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED, "无法确认当前微信账号");
        GroupContactState contactState = readGroupContactState(roomId);
        if (!contactState.success) return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED, contactState.error);
        if (!contactState.found) return mutation(WeChatNativeMutationResult.Outcome.INVALID_TARGET, "群聊联系人已失效");

        String nickname;
        try {
            nickname = normalizeNativeRoomNickname(requestedNickname, roomId);
        } catch (Throwable e) {
            return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED, "微信昵称校验 API 不可用");
        }
        if (nickname.trim().isEmpty()) {
            return mutation(WeChatNativeMutationResult.Outcome.INVALID_TARGET, "群聊昵称不允许为空");
        }
        if (nickname.length() > 16) {
            return mutation(WeChatNativeMutationResult.Outcome.INVALID_TARGET, "群聊昵称不允许超过16个汉字");
        }

        RoomNicknameState before = readMyNicknameState(roomId, selfId);
        if (!before.success) return mutation(WeChatNativeMutationResult.Outcome.READ_FAILED, before.error);
        if (!before.member) return mutation(WeChatNativeMutationResult.Outcome.INVALID_TARGET, "当前账号已不在该群聊");
        if (nickname.equals(before.nickname)) {
            return mutation(WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED, "我在群里的昵称已符合目标");
        }
        if (!maySubmit(guard)) return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED, "账号已切换或任务已停止");

        PendingNickname pending = new PendingNickname();
        final Object factory;
        final Method submit;
        try {
            // Keep database reads, service lookup, and callback wiring off the main looper.
            // The native editor calls the factory submission method from its click listener, so
            // only that short request handoff is marshalled to the UI thread below.
            GroupContactState currentContact = readGroupContactState(roomId);
            if (!currentContact.success || !currentContact.found) {
                throw new IllegalStateException("提交前无法确认群聊联系人");
            }
            RoomNicknameState current = readMyNicknameState(roomId, selfId);
            if (!current.success) throw new IllegalStateException(current.error);
            if (!current.member) throw new IllegalStateException("当前账号已不在该群聊");
            if (nickname.equals(current.nickname)) {
                return mutation(WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED,
                        "我在群里的昵称已符合目标");
            }
            Object roomApi = nativeRoomApi(roomId);
            Method modify = findRoomNicknameMethod(roomApi);
            if (modify == null) throw new IllegalStateException("微信原生群昵称入口不可用");
            factory = KavaReflector.invokeOrThrow(modify, roomApi, roomId, selfId, nickname);
            if (factory == null) throw new IllegalStateException("微信原生群昵称请求构造失败");
            installNicknameCallback(factory, roomId, selfId, nickname, accountGuard, pending);
            submit = findRoomFactorySubmitMethod(factory);
            if (submit == null) {
                throw new IllegalStateException("RoomSDK 群昵称提交方法不可用");
            }
        } catch (Throwable e) {
            log("准备群昵称请求失败: " + e.getMessage());
            return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED,
                    e.getMessage() != null && e.getMessage().contains("已切换")
                            ? e.getMessage() : "微信原生群昵称入口不可用");
        }

        try {
            Boolean submitted = WeChatMainThread.call(() -> {
                if (!maySubmit(guard)) throw new IllegalStateException("账号已切换或任务已停止");
                pending.submitted.set(true);
                KavaReflector.invokeOrThrow(submit, factory);
                return Boolean.TRUE;
            });
            if (!Boolean.TRUE.equals(submitted)) {
                return mutation(WeChatNativeMutationResult.Outcome.ALREADY_SATISFIED, "我在群里的昵称已符合目标");
            }
        } catch (Throwable e) {
            log("提交群昵称失败: " + e.getMessage());
            WeChatNativeMutationResult completed = pending.result.get();
            if (completed != null) return completed;
            if (pending.submitted.get()) {
                return mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED,
                        "RoomSDK 提交结果未知，未重试");
            }
            return mutation(WeChatNativeMutationResult.Outcome.NOT_SUBMITTED,
                    e.getMessage() != null && e.getMessage().contains("已切换")
                            ? e.getMessage() : "微信原生群昵称入口不可用");
        }

        try {
            if (!pending.completed.await(ContactRequestDispatch.DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                return mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED, "已提交／未确认");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED, "已提交／等待确认中断");
        }
        WeChatNativeMutationResult result = pending.result.get();
        return result != null ? result
                : mutation(WeChatNativeMutationResult.Outcome.SUBMITTED_UNCONFIRMED, "已提交／未确认");
    }

    public boolean isMember(String chatroomId, String memberWxId) {
        if (TextUtils.isEmpty(memberWxId)) return false;
        for (String member : getMemberIds(chatroomId)) {
            if (memberWxId.equals(member)) return true;
        }
        return false;
    }

    public String getMemberDisplayName(String chatroomId, String memberWxId) {
        if (contactApi == null) return !TextUtils.isEmpty(memberWxId) ? memberWxId : "";
        return contactApi.getGroupMemberDisplayName(chatroomId, memberWxId);
    }
    public String getRoomDisplayName(String chatroomId, String memberWxId) {
        if (contactApi == null) return memberWxId != null ? memberWxId : "";
        return contactApi.getGroupMemberRoomDisplayName(chatroomId, memberWxId);
    }

    public int getMemberRole(String chatroomId, String memberWxId) {
        if (TextUtils.isEmpty(chatroomId) || TextUtils.isEmpty(memberWxId)) return ROLE_MEMBER;
        String owner = getOwner(chatroomId);
        if (!TextUtils.isEmpty(owner) && owner.equals(memberWxId)) return ROLE_OWNER;
        RoomDataMember member = getRoomDataMembers(chatroomId).get(memberWxId);
        return member != null && (member.roomFlag & ROOM_FLAG_ADMIN) != 0 ? ROLE_ADMIN : ROLE_MEMBER;
    }

    private GroupContactState readGroupContactState(String chatroomId) {
        if (!isGroup(chatroomId) || databaseApi == null || !databaseApi.isAvailable()) {
            return new GroupContactState(false, false, 0, "微信群数据库不可用");
        }
        WeChatDatabaseApi.QueryResult query = databaseApi.queryWithStatus(
                "SELECT username, type FROM rcontact WHERE username=? "
                        + "AND (username LIKE '%@chatroom' OR username LIKE '%@im.chatroom') LIMIT 1",
                new String[]{chatroomId.trim()});
        if (!query.success) return new GroupContactState(false, false, 0,
                query.error.isBlank() ? "读取群聊联系人失败" : query.error);
        if (query.rows.isEmpty()) return new GroupContactState(true, false, 0, "");
        return new GroupContactState(true, true, intValue(query.rows.get(0), "type"), "");
    }

    private RoomNicknameState readMyNicknameState(String roomId, String selfId) {
        if (databaseApi == null || !databaseApi.isAvailable()) {
            return new RoomNicknameState(false, false, "", "微信群数据库不可用");
        }
        WeChatDatabaseApi.QueryResult query = databaseApi.queryWithStatus(
                "SELECT memberlist, selfDisplayName FROM chatroom WHERE chatroomname=? LIMIT 1",
                new String[]{roomId});
        if (!query.success) return new RoomNicknameState(false, false, "",
                query.error.isBlank() ? "读取群成员状态失败" : query.error);
        if (query.rows.isEmpty()) {
            return new RoomNicknameState(false, false, "", "群聊成员资料不可用，无法确认当前账号身份");
        }
        Map<String, Object> row = query.rows.get(0);
        String rawMembers = str(row, "memberlist");
        if (rawMembers.trim().isEmpty()) {
            return new RoomNicknameState(false, false, "", "群成员资料为空，无法确认当前账号身份");
        }
        boolean member = false;
        for (String memberId : splitMembers(rawMembers)) {
            if (selfId.equals(memberId.trim())) {
                member = true;
                break;
            }
        }
        return new RoomNicknameState(true, member, str(row, "selfDisplayName"), "");
    }

    private Object nativeRoomApi(String roomId) throws Exception {
        if (dexFinder == null) throw new IllegalStateException("DexFinder 不可用");
        Method getter = dexFinder.chatroomMuteServiceGetterMethod;
        if (getter == null || getter.getParameterTypes().length != 1
                || getter.getParameterTypes()[0] != String.class
                || getter.getReturnType().isPrimitive()
                || getter.getReturnType() == void.class) {
            dexFinder.resolveConversationMuteApi();
            getter = dexFinder.chatroomMuteServiceGetterMethod;
        }
        if (getter == null || getter.getParameterTypes().length != 1
                || getter.getParameterTypes()[0] != String.class) {
            throw new IllegalStateException("微信 RoomSDK 群服务定位失败");
        }
        Object service = WeChatInternalServices.getService(dexFinder, getter.getDeclaringClass());
        if (service == null) throw new IllegalStateException("微信 RoomSDK 群服务不可用");
        Object roomApi = KavaReflector.invokeOrThrow(getter, service, roomId);
        if (roomApi == null || !getter.getReturnType().isInstance(roomApi)) {
            throw new IllegalStateException("微信 RoomSDK 群服务返回对象无效");
        }
        return roomApi;
    }

    private Method findRoomSaveMethod(Object roomApi, Object nativeContact) {
        if (roomApi == null || nativeContact == null) return null;
        Method method = KavaReflector.findMethodRecursive(
                roomApi.getClass(), "k", nativeContact.getClass(), boolean.class);
        if (method == null || method.getReturnType() != void.class
                || method.getParameterTypes().length != 2
                || !method.getParameterTypes()[0].isInstance(nativeContact)
                || method.getParameterTypes()[1] != boolean.class) return null;
        return method;
    }

    private Method findRoomNicknameMethod(Object roomApi) {
        if (roomApi == null) return null;
        Method method = KavaReflector.findMethodRecursive(
                roomApi.getClass(), "n", String.class, String.class, String.class);
        if (method == null || method.getParameterTypes().length != 3
                || method.getReturnType().isPrimitive() || method.getReturnType() == void.class
                || !"com.tencent.mm.roomsdk.model.factory.a".equals(method.getReturnType().getName())) {
            return null;
        }
        return method;
    }

    /** Exact abstract API used by RoomSDK's native request factory; invocation dispatches to f.b(). */
    private Method findRoomFactorySubmitMethod(Object factory) {
        if (factory == null || factory.getClass().getClassLoader() == null) return null;
        try {
            Class<?> factoryBase = Class.forName(
                    "com.tencent.mm.roomsdk.model.factory.a", false, factory.getClass().getClassLoader());
            if (!factoryBase.isInstance(factory)) return null;
            Method submit = factoryBase.getDeclaredMethod("b");
            if (submit.getReturnType() != void.class || submit.getParameterTypes().length != 0
                    || Modifier.isStatic(submit.getModifiers())) return null;
            submit.setAccessible(true);
            return submit;
        } catch (Throwable e) {
            return null;
        }
    }

    private String normalizeNativeRoomNickname(String requestedNickname, String roomId) throws Exception {
        if (TextUtils.isEmpty(requestedNickname)) return "";
        Object nativeContact = contactApi != null ? contactApi.getNativeContactObject(roomId) : null;
        if (nativeContact == null || nativeContact.getClass().getClassLoader() == null) {
            throw new IllegalStateException("微信原生群聊联系人对象不可用");
        }
        Class<?> normalizer = Class.forName(
                "com.tencent.mm.sdk.platformtools.b9", false, nativeContact.getClass().getClassLoader());
        Method method = normalizer.getDeclaredMethod("n", String.class);
        if (!KavaReflector.isStatic(method) || method.getReturnType() != String.class) {
            throw new IllegalStateException("微信原生昵称标准化方法签名不匹配");
        }
        method.setAccessible(true);
        Object normalized = KavaReflector.invokeOrThrow(method, null, requestedNickname.trim());
        if (!(normalized instanceof String)) throw new IllegalStateException("微信昵称标准化失败");
        return ((String) normalized).trim();
    }

    private void installNicknameCallback(
            Object factory,
            String roomId,
            String selfId,
            String nickname,
            WeChatContactApi.BatchSubmissionGuard accountGuard,
            PendingNickname pending) throws Exception {
        Field callbackField = KavaReflector.findFieldRecursive(factory.getClass(), "d");
        if (callbackField == null || !callbackField.getType().isInterface()) {
            throw new IllegalStateException("RoomSDK 群昵称回调接口不可用");
        }
        Class<?> callbackType = callbackField.getType();
        Method callbackMethod = callbackType.getMethod(
                "a", int.class, int.class, String.class, callbackType);
        if (callbackMethod.getReturnType() != void.class) {
            throw new IllegalStateException("RoomSDK 群昵称回调签名不匹配");
        }
        Object callback = Proxy.newProxyInstance(
                callbackType.getClassLoader(), new Class<?>[]{callbackType},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("toString".equals(method.getName())) return "WeiPlusRoomNicknameCallback";
                            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                            if ("equals".equals(method.getName())) return proxy == (args != null && args.length > 0 ? args[0] : null);
                        }
                        if ("a".equals(method.getName()) && args != null && args.length == 4
                                && args[0] instanceof Number && args[1] instanceof Number) {
                            if (!pending.completedOnce.compareAndSet(false, true)) return null;
                            int errorCode = ((Number) args[1]).intValue();
                            WeChatNativeMutationResult result;
                            if (errorCode != 0) {
                                result = mutation(WeChatNativeMutationResult.Outcome.SERVER_REJECTED,
                                        "微信服务端拒绝群昵称修改");
                            } else if (accountGuard == null || !accountGuard.mayContinue()) {
                                result = mutation(WeChatNativeMutationResult.Outcome.SERVER_ACCEPTED,
                                        "服务端已接受；账号已切换，本地状态未更新");
                            } else {
                                boolean updated = WeChatMainThread.call(
                                        () -> updateMyNicknameCache(roomId, nickname, factory.getClass().getClassLoader()));
                                RoomNicknameState finalState = readMyNicknameState(roomId, selfId);
                                result = updated && finalState.success && finalState.member
                                        && nickname.equals(finalState.nickname)
                                        ? mutation(WeChatNativeMutationResult.Outcome.CONFIRMED,
                                                "群昵称服务端回调成功且状态已确认")
                                        : mutation(WeChatNativeMutationResult.Outcome.SERVER_ACCEPTED,
                                                "服务端已接受，本地最终状态未确认");
                            }
                            pending.result.set(result);
                            pending.completed.countDown();
                        }
                        return null;
                    }
                });
        if (!KavaReflector.writeField(callbackField, factory, callback)) {
            throw new IllegalStateException("RoomSDK 群昵称回调注册失败");
        }
    }

    /** Mirrors the native b6 success callback only after the RoomSDK response returns code 0. */
    private boolean updateMyNicknameCache(String roomId, String nickname, ClassLoader loader) {
        try {
            Class<?> storageApiClass = Class.forName("x12.f", false, loader);
            Object storageApi = WeChatInternalServices.getService(dexFinder, storageApiClass);
            if (storageApi == null) return false;
            Method storageGetter = KavaReflector.findMethodRecursive(storageApi.getClass(), "a");
            if (storageGetter == null || storageGetter.getParameterTypes().length != 0) return false;
            Object storage = KavaReflector.invokeOrThrow(storageGetter, storageApi);
            if (storage == null) return false;
            Method entityGetter = KavaReflector.findMethodRecursive(storage.getClass(), "I1", String.class);
            if (entityGetter == null || entityGetter.getParameterTypes().length != 1) return false;
            Object entity = KavaReflector.invokeOrThrow(entityGetter, storage, roomId);
            if (entity == null || !roomId.equals(KavaReflector.readField(entity, "field_chatroomname"))) {
                return false;
            }
            Class<?> entityInterface = Class.forName("gg5.f0", false, loader);
            Method update = KavaReflector.findMethodRecursive(
                    storage.getClass(), "update", entityInterface, String[].class);
            if (update == null || update.getReturnType() != boolean.class) return false;
            if (!KavaReflector.writeField(entity, "field_selfDisplayName", nickname)) return false;
            Object result = KavaReflector.invokeOrThrow(update, storage, entity, new String[0]);
            return Boolean.TRUE.equals(result);
        } catch (Throwable e) {
            log("群昵称原生成功回调的本地状态刷新失败: " + e.getMessage());
            return false;
        }
    }

    private boolean maySubmit(WeChatContactApi.BatchSubmissionGuard guard) {
        if (guard == null) return true;
        try {
            return guard.mayContinue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeContinue(BooleanSupplier supplier) {
        try { return supplier.getAsBoolean(); } catch (Throwable ignored) { return false; }
    }

    private static WeChatNativeMutationResult mutation(
            WeChatNativeMutationResult.Outcome outcome, String message) {
        return WeChatNativeMutationResult.of(outcome, message);
    }

    private static int intValue(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Integer nullableIntValue(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public String getMemberRoleName(String chatroomId, String memberWxId) {
        int role = getMemberRole(chatroomId, memberWxId);
        if (role == ROLE_OWNER) return "群主";
        if (role == ROLE_ADMIN) return "管理员";
        return "群员";
    }

    public String getMemberInviter(String chatroomId, String memberWxId) {
        if (TextUtils.isEmpty(chatroomId) || TextUtils.isEmpty(memberWxId)) return "";
        RoomDataMember member = getRoomDataMembers(chatroomId).get(memberWxId);
        if (member == null || TextUtils.isEmpty(member.inviterWxId)) return "";
        String inviter = member.inviterWxId.trim();
        return memberWxId.equals(inviter) ? "" : inviter;
    }

    public List<String> getAdminMemberIds(String chatroomId) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, RoomDataMember> entry : getRoomDataMembers(chatroomId).entrySet()) {
            RoomDataMember member = entry.getValue();
            if (member != null && (member.roomFlag & ROOM_FLAG_ADMIN) != 0) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public boolean addChatroomMember(String chatroomId, String addMember) {
        if (TextUtils.isEmpty(addMember)) return false;
        return addChatroomMember(chatroomId, Collections.singletonList(addMember));
    }

    public boolean addChatroomMember(String chatroomId, List<String> addMemberList) {
        if (TextUtils.isEmpty(chatroomId) || addMemberList == null || addMemberList.isEmpty()) {
            log("添加群成员失败: chatroom/member为空");
            return false;
        }
        if (dexFinder == null || dexFinder.addChatroomMemberCtor == null || networkDispatcher == null) {
            log("添加群成员失败: API未就绪");
            return false;
        }
        List<String> members = cleanMemberList(addMemberList);
        if (members.isEmpty()) {
            log("添加群成员失败: member为空");
            return false;
        }
        installNetworkHook();
        try {
            Object scene = KavaReflector.newInstance(
                    dexFinder.addChatroomMemberCtor, chatroomId, members, "", null);
            boolean result = networkDispatcher.send(scene);
            log("添加群成员" + (result ? "成功" : "失败") + ": " + chatroomId + " count=" + members.size());
            return result;
        } catch (Throwable e) {
            log("添加群成员异常: " + e.getMessage());
            return false;
        }
    }

    public boolean inviteChatroomMember(String chatroomId, String inviteMember) {
        if (TextUtils.isEmpty(inviteMember)) return false;
        return inviteChatroomMember(chatroomId, Collections.singletonList(inviteMember));
    }

    public boolean inviteChatroomMember(String chatroomId, List<String> inviteMemberList) {
        if (TextUtils.isEmpty(chatroomId) || inviteMemberList == null || inviteMemberList.isEmpty()) {
            log("邀请群成员失败: chatroom/member为空");
            return false;
        }
        if (dexFinder == null || dexFinder.inviteChatroomMemberCtor == null || networkDispatcher == null) {
            log("邀请群成员失败: API未就绪");
            return false;
        }
        List<String> members = cleanMemberList(inviteMemberList);
        if (members.isEmpty()) {
            log("邀请群成员失败: member为空");
            return false;
        }
        installNetworkHook();
        try {
            Object scene = KavaReflector.newInstance(
                    dexFinder.inviteChatroomMemberCtor, chatroomId, members, 0, null);
            boolean result = networkDispatcher.send(scene);
            log("邀请群成员" + (result ? "成功" : "失败") + ": " + chatroomId + " count=" + members.size());
            return result;
        } catch (Throwable e) {
            log("邀请群成员异常: " + e.getMessage());
            return false;
        }
    }

    public boolean delChatroomMember(String chatroomId, String delMember) {
        if (TextUtils.isEmpty(delMember)) return false;
        return delChatroomMember(chatroomId, Collections.singletonList(delMember));
    }

    public boolean delChatroomMember(String chatroomId, List<String> delMemberList) {
        if (TextUtils.isEmpty(chatroomId) || delMemberList == null || delMemberList.isEmpty()) {
            log("移除群成员失败: chatroom/member为空");
            return false;
        }
        if (dexFinder == null || dexFinder.delChatroomMemberCtor == null || networkDispatcher == null) {
            log("移除群成员失败: API未就绪");
            return false;
        }
        List<String> members = cleanMemberList(delMemberList);
        if (members.isEmpty()) {
            log("移除群成员失败: member为空");
            return false;
        }
        installNetworkHook();
        try {
            Object scene = KavaReflector.newInstance(
                    dexFinder.delChatroomMemberCtor, chatroomId, members, 0);
            boolean result = networkDispatcher.send(scene);
            log("移除群成员" + (result ? "成功" : "失败") + ": " + chatroomId + " count=" + members.size());
            return result;
        } catch (Throwable e) {
            log("移除群成员异常: " + e.getMessage());
            return false;
        }
    }

    public boolean isGroup(String wxId) {
        return contactApi != null && contactApi.isGroup(wxId);
    }

    private void installNetworkHook() {
        if (networkHookInstalled || networkDispatcher == null || dexFinder == null) return;
        if (dexFinder.netQueueClass == null && dexFinder.netQueueCandidateClasses.isEmpty()) return;
        networkDispatcher.hookNetworkQueue(dexFinder.netQueueClass, dexFinder.netQueueCandidateClasses);
        networkHookInstalled = true;
    }

    private List<String> cleanMemberList(List<String> members) {
        List<String> result = new ArrayList<>();
        if (members == null) return result;
        for (String member : members) {
            if (TextUtils.isEmpty(member) || result.contains(member)) continue;
            result.add(member);
        }
        return result;
    }

    private List<String> splitMembers(String memberList) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(memberList)) return result;
        for (String part : memberList.split(";")) {
            if (!TextUtils.isEmpty(part)) result.add(part);
        }
        return result;
    }

    private Map<String, RoomDataMember> getRoomDataMembers(String chatroomId) {
        Map<String, RoomDataMember> result = new LinkedHashMap<>();
        if (TextUtils.isEmpty(chatroomId) || databaseApi == null || !hasColumn("chatroom", "roomdata")) {
            return result;
        }
        try {
            List<Map<String, Object>> rows = databaseApi.query(
                    "SELECT roomdata FROM chatroom WHERE chatroomname=? LIMIT 1",
                    new String[]{chatroomId});
            if (rows.isEmpty()) return result;
            byte[] roomData = blob(rows.get(0), "roomdata");
            if (roomData == null || roomData.length == 0) return result;
            ProtoReader reader = new ProtoReader(roomData);
            while (!reader.isAtEnd()) {
                int tag = reader.readVarint32();
                int fieldNumber = tag >>> 3;
                int wireType = tag & 7;
                if (fieldNumber == 1 && wireType == 2) {
                    RoomDataMember member = parseRoomDataMember(reader.readBytes());
                    if (!TextUtils.isEmpty(member.username)) {
                        result.put(member.username, member);
                    }
                } else {
                    reader.skip(wireType);
                }
            }
        } catch (Throwable e) {
            log("解析群成员身份失败: " + e.getMessage());
        }
        return result;
    }

    private RoomDataMember parseRoomDataMember(byte[] memberData) {
        RoomDataMember member = new RoomDataMember();
        try {
            ProtoReader reader = new ProtoReader(memberData);
            while (!reader.isAtEnd()) {
                int tag = reader.readVarint32();
                int fieldNumber = tag >>> 3;
                int wireType = tag & 7;
                if (wireType == 2 && fieldNumber == 1) {
                    member.username = reader.readString().trim();
                } else if (wireType == 2 && fieldNumber == 2) {
                    member.displayName = reader.readString().trim();
                } else if (wireType == 0 && fieldNumber == 3) {
                    member.roomFlag = reader.readVarint32();
                } else if (wireType == 2 && fieldNumber == 4) {
                    member.inviterWxId = reader.readString().trim();
                } else {
                    reader.skip(wireType);
                }
            }
        } catch (Throwable ignored) {
            return new RoomDataMember();
        }
        return member;
    }

    private boolean hasColumn(String table, String column) {
        if (TextUtils.isEmpty(table) || TextUtils.isEmpty(column) || databaseApi == null) return false;
        String key = table + ":" + column;
        Boolean cached = columnExistsCache.get(key);
        if (cached != null) return cached;
        boolean exists = false;
        try {
            List<Map<String, Object>> rows = databaseApi.query("PRAGMA table_info(" + table + ")", null);
            for (Map<String, Object> row : rows) {
                if (column.equalsIgnoreCase(str(row, "name"))) {
                    exists = true;
                    break;
                }
            }
        } catch (Throwable e) {
            log("检测字段失败: " + table + "." + column + " " + e.getMessage());
        }
        if (exists) {
            columnExistsCache.put(key, true);
        } else {
            columnExistsCache.remove(key);
        }
        return exists;
    }

    private static byte[] blob(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        if (value instanceof byte[]) return (byte[]) value;
        if (value instanceof String) return hexStringToBytes((String) value);
        if (value == null) return null;
        try {
            Method method = value.getClass().getMethod("toByteArray");
            Object result = method.invoke(value);
            if (result instanceof byte[]) return (byte[]) result;
        } catch (Throwable ignored) {}
        try {
            Method method = value.getClass().getMethod("getBytes");
            Object result = method.invoke(value);
            if (result instanceof byte[]) return (byte[]) result;
        } catch (Throwable ignored) {}
        return null;
    }

    private static byte[] hexStringToBytes(String value) {
        if (TextUtils.isEmpty(value)) return null;
        String s = value.trim();
        if (s.startsWith("hex->")) s = s.substring(5);
        s = s.replace(" ", "").replace("\n", "").replace("\r", "");
        if (s.length() < 2 || (s.length() & 1) != 0) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return null;
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        return value != null ? String.valueOf(value) : "";
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatChatroomApi] " + message);
    }

    private static final class GroupContactState {
        final boolean success;
        final boolean found;
        final int type;
        final String error;

        GroupContactState(boolean success, boolean found, int type, String error) {
            this.success = success;
            this.found = found;
            this.type = type;
            this.error = error == null ? "" : error;
        }
    }

    private static final class RoomNicknameState {
        final boolean success;
        final boolean member;
        final String nickname;
        final String error;

        RoomNicknameState(boolean success, boolean member, String nickname, String error) {
            this.success = success;
            this.member = member;
            this.nickname = nickname == null ? "" : nickname;
            this.error = error == null ? "" : error;
        }
    }

    private static final class PendingNickname {
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicBoolean completedOnce = new AtomicBoolean(false);
        final AtomicBoolean submitted = new AtomicBoolean(false);
        final AtomicReference<WeChatNativeMutationResult> result = new AtomicReference<>();
    }

    private static final class RoomDataMember {
        String username = "";
        String displayName = "";
        String inviterWxId = "";
        int roomFlag;
    }

    private static final class ProtoReader {
        private final byte[] data;
        private int position;

        ProtoReader(byte[] data) {
            this.data = data != null ? data : new byte[0];
        }

        boolean isAtEnd() {
            return position >= data.length;
        }

        int readVarint32() {
            long value = readVarint64();
            if (value > Integer.MAX_VALUE) {
                throw new IllegalStateException("varint过大");
            }
            return (int) value;
        }

        long readVarint64() {
            long result = 0L;
            for (int shift = 0; shift < 64; shift += 7) {
                require(1);
                int b = data[position++] & 0xFF;
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return result;
            }
            throw new IllegalStateException("varint异常");
        }

        String readString() {
            byte[] bytes = readBytes();
            try {
                return new String(bytes, "UTF-8");
            } catch (Throwable ignored) {
                return new String(bytes);
            }
        }

        byte[] readBytes() {
            int length = readVarint32();
            if (length < 0 || length > data.length - position) {
                throw new IllegalStateException("bytes长度异常");
            }
            byte[] bytes = new byte[length];
            System.arraycopy(data, position, bytes, 0, length);
            position += length;
            return bytes;
        }

        void skip(int wireType) {
            switch (wireType) {
                case 0:
                    readVarint64();
                    return;
                case 1:
                    skipBytes(8);
                    return;
                case 2:
                    skipBytes(readVarint32());
                    return;
                case 5:
                    skipBytes(4);
                    return;
                default:
                    throw new IllegalStateException("不支持的wireType: " + wireType);
            }
        }

        private void skipBytes(int count) {
            if (count < 0) throw new IllegalStateException("跳过长度异常");
            require(count);
            position += count;
        }

        private void require(int count) {
            if (position + count > data.length) {
                throw new IllegalStateException("protobuf长度不足");
            }
        }
    }
}
