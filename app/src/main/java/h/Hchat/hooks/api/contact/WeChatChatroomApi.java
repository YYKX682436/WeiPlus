package h.Hchat.hooks.api.contact;

import android.text.TextUtils;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.api.model.WeChatChatroom;
import h.Hchat.hooks.api.model.WeChatContact;
import h.Hchat.hooks.api.net.WeChatNetworkDispatcher;
import h.Hchat.hooks.api.runtime.WeChatDatabaseApi;
import h.Hchat.hooks.api.media.WeChatInternalServices;
import h.Hchat.utils.KavaReflector;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 微信群聊 API。
 */
public final class WeChatChatroomApi {
    public static final int ROLE_MEMBER = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_OWNER = 2;
    private static final int ROOM_FLAG_ADMIN = 0x800;

    public interface Logger {
        void log(String message);
    }

    private final WeChatDatabaseApi databaseApi;
    private final WeChatContactApi contactApi;
    private final DexFinder dexFinder;
    private final WeChatNetworkDispatcher networkDispatcher;
    private final Logger logger;
    private final Map<String, Boolean> columnExistsCache = new ConcurrentHashMap<>();
    private volatile boolean networkHookInstalled;

    public WeChatChatroomApi(WeChatDatabaseApi databaseApi,
                             WeChatContactApi contactApi,
                             Logger logger) {
        this(databaseApi, contactApi, null, null, logger);
    }

    public WeChatChatroomApi(WeChatDatabaseApi databaseApi,
                             WeChatContactApi contactApi,
                             DexFinder dexFinder,
                             WeChatNetworkDispatcher networkDispatcher,
                             Logger logger) {
        this.databaseApi = databaseApi;
        this.contactApi = contactApi;
        this.dexFinder = dexFinder;
        this.networkDispatcher = networkDispatcher;
        this.logger = logger;
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
