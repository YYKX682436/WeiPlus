package h.Hchat.hooks.api.contact;

import android.text.TextUtils;
import android.content.ContentValues;
import android.database.Cursor;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.api.media.WeChatInternalServices;
import h.Hchat.hooks.api.net.WeChatNetworkDispatcher;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.utils.KavaReflector;
import h.Hchat.hooks.api.model.ContactLabelBean;
import h.Hchat.hooks.api.model.WeChatContact;
import h.Hchat.hooks.api.runtime.WeChatDatabaseApi;

import java.nio.ByteBuffer;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedInterface;

/**
 * 微信联系人/群聊 API。
 */
public final class WeChatContactApi {
    public enum ContactLabelMutationResult {
        CONFIRMED,
        SERVER_ACCEPTED,
        SUBMITTED_UNCONFIRMED,
        SERVER_REJECTED,
        SUBMITTED,
        NOT_SUBMITTED,
        ALREADY_SATISFIED,
        INVALID_TARGET
    }

    @FunctionalInterface
    public interface BatchSubmissionGuard {
        boolean mayContinue();
    }

    public static final class ContactLabelBatchItemResult {
        public final String username;
        public final ContactLabelMutationResult outcome;
        public final String message;

        public ContactLabelBatchItemResult(
                String username, ContactLabelMutationResult outcome, String message) {
            this.username = username == null ? "" : username;
            this.outcome = outcome == null ? ContactLabelMutationResult.NOT_SUBMITTED : outcome;
            this.message = message == null ? "" : message;
        }
    }

    public static final class ContactLabelListResult {
        public final boolean success;
        public final List<ContactLabelBean> labels;
        public final String error;

        private ContactLabelListResult(
                boolean success, List<ContactLabelBean> labels, String error) {
            this.success = success;
            this.labels = labels == null ? Collections.emptyList() : new ArrayList<>(labels);
            this.error = error == null ? "" : error;
        }
    }

    private static final int SELF_FIELD_WXID = 2;
    private static final int SELF_FIELD_GENDER = 12290;
    private static final int SELF_FIELD_CITY = 12292;
    private static final int SELF_FIELD_PROVINCE = 12293;
    public interface Logger {
        void log(String message);
    }

    private static final String CONTACT_FIELDS_BASE =
            "r.username, r.alias, r.conRemark, r.nickname, r.encryptUsername, r.type, r.lvbuff AS"
                    + " lvbuff, i.reserved1 AS avatarUrl, i.reserved2 AS avatarBackupUrl";
    private static final String PICKER_CONTACT_FIELDS_BASE =
            "r.username, r.alias, r.conRemark, r.nickname, r.encryptUsername, "
                    + "r.type, i.reserved1 AS avatarUrl, i.reserved2 AS avatarBackupUrl";
    private static final String[] CONTACT_LABEL_TABLES = {
            "ContactLabel",
            "contactlabel"
    };
    private static final String[] CONTACT_LABEL_ID_COLUMNS = {
            "labelID",
            "labelId",
            "labelid"
    };
    private static final String[] CONTACT_LABEL_NAME_COLUMNS = {
            "labelName",
            "labelname"
    };
    private static final String[] CONTACT_LABEL_IDS_COLUMNS = {
            "contactLabelIds",
            "contactLabelids"
    };
    private static final String[] CONTACT_STORAGE_ACCESSOR_NAMES = {
            "u",
            "r",
            "ig",
            "hh"
    };

    private final WeChatDatabaseApi databaseApi;
    private final DexFinder dexFinder;
    private final WeChatNetworkDispatcher networkDispatcher;
    private final Logger logger;
    private final Map<String, Boolean> columnExistsCache = new ConcurrentHashMap<>();
    private volatile boolean networkHookInstalled;
    private volatile Class<?> modifyContactLabelItemClass;
    private volatile Object nativeContactStorage;
    private volatile Method nativeContactGetterMethod;

    public WeChatContactApi(WeChatDatabaseApi databaseApi, Logger logger) {
        this(databaseApi, null, null, logger);
    }

    public WeChatContactApi(WeChatDatabaseApi databaseApi, DexFinder dexFinder, Logger logger) {
        this(databaseApi, dexFinder, null, logger);
    }

    public WeChatContactApi(WeChatDatabaseApi databaseApi,
                            DexFinder dexFinder,
                            WeChatNetworkDispatcher networkDispatcher,
                            Logger logger) {
        this.databaseApi = databaseApi;
        this.dexFinder = dexFinder;
        this.networkDispatcher = networkDispatcher;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return databaseApi != null && databaseApi.isAvailable();
    }

    public boolean isGroup(String wxId) {
        return !TextUtils.isEmpty(wxId)
                && (wxId.endsWith("@chatroom") || wxId.endsWith("@im.chatroom"));
    }

    /** Confirms a group remains represented as a chatroom contact at the moment of submission. */
    public boolean isValidGroupContact(String wxId) {
        if (!isGroup(wxId) || databaseApi == null || !databaseApi.isAvailable()) return false;
        try {
            return !databaseApi.query(
                    "SELECT username FROM rcontact WHERE username=? "
                            + "AND (username LIKE '%@chatroom' OR username LIKE '%@im.chatroom') LIMIT 1",
                    new String[]{wxId}).isEmpty();
        } catch (Throwable e) {
            log("复核群聊对象失败: " + e.getMessage());
            return false;
        }
    }

    public String getDisplayName(String wxId) {
        if (TextUtils.isEmpty(wxId)) return "";
        try {
            if (isGroup(wxId)) {
                String nickname = databaseApi.queryFirstString(
                        "SELECT nickname FROM rcontact WHERE username=?",
                        new String[]{wxId},
                        "nickname");
                return !TextUtils.isEmpty(nickname) ? nickname : wxId;
            }

            WeChatContact contact = getContact(wxId);
            return contact != null ? contact.displayName() : wxId;
        } catch (Throwable e) {
            log("获取显示名失败: " + wxId + " " + e.getMessage());
            return wxId;
        }
    }

    public int getGender(String wxId) {
        if (TextUtils.isEmpty(wxId)) return 0;
        if (isSelf(wxId)) return intValue(selfProfileField(SELF_FIELD_GENDER));
        WeChatContact contact = getContact(wxId);
        return contact != null ? contact.gender : 0;
    }

    public String getProvince(String wxId) {
        if (TextUtils.isEmpty(wxId)) return "";
        if (isSelf(wxId)) return selfProfileField(SELF_FIELD_PROVINCE);
        WeChatContact contact = getContact(wxId);
        return contact != null ? contact.province : "";
    }

    public String getCity(String wxId) {
        if (TextUtils.isEmpty(wxId)) return "";
        if (isSelf(wxId)) return selfProfileField(SELF_FIELD_CITY);
        WeChatContact contact = getContact(wxId);
        return contact != null ? contact.city : "";
    }

    public String getRegion(String wxId) {
        if (TextUtils.isEmpty(wxId)) return "";
        if (isSelf(wxId)) return buildRegion(selfProfileField(SELF_FIELD_PROVINCE), selfProfileField(SELF_FIELD_CITY));
        WeChatContact contact = getContact(wxId);
        return contact != null ? contact.getRegion() : "";
    }

    private String buildRegion(String province, String city) {
        String p = province == null ? "" : province.trim();
        String c = city == null ? "" : city.trim();
        if (TextUtils.isEmpty(p)) return c;
        if (TextUtils.isEmpty(c) || p.equals(c)) return p;
        return p + " " + c;
    }

    public String getGroupMemberDisplayName(String groupId, String memberWxId) {
        if (TextUtils.isEmpty(memberWxId)) return "";
        String internalName = getGroupMemberDisplayNameByWeChat(groupId, memberWxId);
        if (!TextUtils.isEmpty(internalName)) return internalName;
        String roomName = getGroupMemberRoomDisplayName(groupId, memberWxId);
        if (!TextUtils.isEmpty(roomName)) return roomName;
        String name = getDisplayName(memberWxId);
        log("群成员昵称回退: group=" + groupId
                + " member=" + memberWxId
                + " contactName=" + name);
        return !TextUtils.isEmpty(name) ? name : memberWxId;
    }

    private String getGroupMemberDisplayNameByWeChat(String groupId, String memberWxId) {
        if (!isGroup(groupId) || TextUtils.isEmpty(memberWxId) || dexFinder == null) return "";
        try {
            Method method = dexFinder.groupMemberDisplayNameMethod;
            if (method == null) {
                log("微信内部群昵称方法未解析: group=" + groupId + " member=" + memberWxId);
                return "";
            }
            Object result = KavaReflector.invoke(method, null, memberWxId, groupId);
            String name = result instanceof String ? ((String) result).trim() : "";
            log("微信内部群昵称返回: method="
                    + method.getDeclaringClass().getName() + "#" + method.getName()
                    + " group=" + groupId
                    + " member=" + memberWxId
                    + " name=" + name);
            if (!TextUtils.isEmpty(name) && !memberWxId.equals(name)) return name;
        } catch (Throwable e) {
            log("微信内部群昵称方法失败: group=" + groupId
                    + " member=" + memberWxId + " " + e.getMessage());
        }
        return "";
    }

    public String getGroupMemberRoomDisplayName(String groupId, String memberWxId) {
        if (TextUtils.isEmpty(memberWxId)) return "";
        String name = getGroupMemberRoomDisplayNames(groupId).get(memberWxId);
        return !TextUtils.isEmpty(name) ? name : "";
    }

    public Map<String, String> getGroupMemberRoomDisplayNames(String groupId) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!isGroup(groupId)) return result;
        try {
            boolean hasRoomData = hasColumn("chatroom", "roomdata");
            List<Map<String, Object>> rows = databaseApi.query(
                    "SELECT memberlist, displayname"
                            + (hasRoomData ? ", roomdata" : "")
                            + " FROM chatroom WHERE chatroomname=? LIMIT 1",
                    new String[]{groupId});
            if (rows.isEmpty()) return result;

            Map<String, Object> row = rows.get(0);
            if (hasRoomData) {
                result.putAll(getRoomDataMemberDisplayNames(blob(row, "roomdata")));
            }

            String memberList = str(row, "memberlist");
            String displayNames = str(row, "displayname");
            if (TextUtils.isEmpty(memberList) || TextUtils.isEmpty(displayNames)) {
                return result;
            }

            String[] members = splitMembers(memberList);
            String[] names = splitDisplayNames(displayNames, members.length);
            if (members.length == 0 || members.length != names.length) {
                log("chatroom.displayname无法对齐: group=" + groupId
                        + " members=" + members.length
                        + " names=" + names.length);
                return result;
            }

            for (int i = 0; i < members.length; i++) {
                String member = members[i];
                String name = names[i] != null ? names[i].trim() : "";
                if (!TextUtils.isEmpty(member)
                        && !result.containsKey(member)) {
                    result.put(member, member.equals(name) ? "" : name);
                }
            }
        } catch (Throwable e) {
            log("获取群成员昵称失败: group=" + groupId + " " + e.getMessage());
        }
        return result;
    }

    private Map<String, String> getRoomDataMemberDisplayNames(byte[] roomData) {
        Map<String, String> result = new LinkedHashMap<>();
        if (roomData == null || roomData.length == 0) return result;
        try {
            ProtoReader reader = new ProtoReader(roomData);
            while (!reader.isAtEnd()) {
                int tag = reader.readVarint32();
                int fieldNumber = tag >>> 3;
                int wireType = tag & 7;
                if (fieldNumber == 1 && wireType == 2) {
                    byte[] memberBytes = reader.readBytes();
                    RoomDataMember member = parseRoomDataMember(memberBytes);
                    if (!TextUtils.isEmpty(member.username) && member.displayName != null) {
                        String displayName = member.displayName;
                        result.put(
                                member.username,
                                member.username.equals(displayName) ? "" : displayName);
                    }
                } else {
                    reader.skip(wireType);
                }
            }
        } catch (Throwable e) {
            log("解析群roomdata失败: " + e.getMessage());
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
                } else {
                    reader.skip(wireType);
                }
            }
        } catch (Throwable ignored) {
            return new RoomDataMember();
        }
        return member;
    }

    public WeChatContact getContact(String wxId) {
        if (TextUtils.isEmpty(wxId)) return null;
        List<Map<String, Object>> rows = databaseApi.query(
                "SELECT " + contactFields()
                        + " FROM rcontact r "
                        + "LEFT JOIN img_flag i ON r.username = i.username "
                        + "WHERE r.username=? LIMIT 1",
                new String[]{wxId});
        return rows.isEmpty() ? null : toContact(rows.get(0));
    }

    /**
     * Returns the persisted chatroom notification state, or null when lvbuff is unavailable.
     */
    public Boolean getChatroomDoNotDisturbState(String wxId) {
        if (TextUtils.isEmpty(wxId)
                || (!wxId.endsWith("@chatroom") && !wxId.endsWith("@im.chatroom"))) {
            return null;
        }
        List<Map<String, Object>> rows = databaseApi.query(
                "SELECT lvbuff FROM rcontact WHERE username=? LIMIT 1",
                new String[]{wxId});
        if (rows.isEmpty()) return null;
        Integer chatRoomNotify = parseLvContactProfile(blob(rows.get(0), "lvbuff")).chatRoomNotify;
        return chatRoomNotify != null ? chatRoomNotify == 0 : null;
    }

    public boolean isFriend(String wxId) {
        if (TextUtils.isEmpty(wxId)) return false;
        List<Map<String, Object>> rows = databaseApi.query(
                "SELECT r.username FROM rcontact r "
                        + "WHERE r.username=? "
                        + "AND r.verifyFlag=0 "
                        + "AND (r.type & 1)!=0 "
                        + "AND (r.type & 8)=0 "
                        + "AND (r.type & 32)=0 "
                        + "AND r.username NOT LIKE '%chatroom' "
                        + "AND (r.encryptUsername!='' "
                        + "OR r.username=(SELECT value FROM userinfo WHERE id=2)) LIMIT 1",
                new String[]{wxId});
        return !rows.isEmpty();
    }

    public List<WeChatContact> getContacts() {
        return queryContacts(
                "SELECT " + contactFields()
                        + " FROM rcontact r "
                        + "LEFT JOIN img_flag i ON r.username = i.username "
                        + "WHERE r.verifyFlag=0",
                null);
    }

    public List<WeChatContact> getFriends() {
        return queryContacts(
                "SELECT "
                        + contactFields()
                        + " FROM rcontact r LEFT JOIN img_flag i ON r.username = i.username WHERE"
                        + " (r.encryptUsername!='' OR r.username=(SELECT value FROM userinfo WHERE"
                        + " id=2)) AND r.verifyFlag=0 AND (r.type & 1)!=0 AND (r.type & 8)=0 AND"
                        + " (r.type & 32)=0 AND r.username NOT LIKE '%chatroom'",
                null);
    }

    /**
     * 联系人选择器使用的联系人集合。
     *
     * 通用选择器只保留严格好友和企业微信联系人。需要群成员的功能应在
     * 自己的候选范围内显式追加，不能把群成员或单向联系人混入公共集合。
     */
    public List<WeChatContact> getPickerContacts() {
        return queryPickerContacts(
                "SELECT "
                        + pickerContactFields()
                        + " FROM rcontact r LEFT JOIN img_flag i ON r.username = i.username WHERE"
                        + " ((r.verifyFlag=0 AND r.username NOT LIKE '%@chatroom' AND r.username"
                        + " NOT LIKE '%@im.chatroom' AND r.username NOT LIKE 'gh\\_%' ESCAPE '\\'"
                        + " AND r.username NOT IN "
                        + "('filehelper','fmessage','tmessage','qqmail','weixin','floatbottle','medianote','medianote@chatroom','newsapp','masssend','feedsapp','blogapp')"
                        + " AND ((r.encryptUsername!='' OR r.username=(SELECT value FROM userinfo"
                        + " WHERE id=2)) AND (r.type & 1)!=0 AND (r.type & 8)=0 AND (r.type &"
                        + " 32)=0)) OR r.username LIKE '%@openim' OR LOWER(COALESCE(r.username,''))"
                        + " LIKE '%clawbot%' OR LOWER(COALESCE(r.alias,'')) LIKE '%clawbot%' OR"
                        + " LOWER(COALESCE(r.conRemark,'')) LIKE '%clawbot%' OR"
                        + " LOWER(COALESCE(r.nickname,'')) LIKE '%clawbot%')",
                null);
    }

    /** Lightweight group records for contact pickers. */
    public List<WeChatContact> getPickerGroups() {
        return queryPickerContacts(
                "SELECT " + pickerContactFields()
                        + " FROM rcontact r "
                        + "LEFT JOIN img_flag i ON r.username = i.username "
                        + "WHERE r.username LIKE '%@chatroom' "
                        + "OR r.username LIKE '%@im.chatroom'",
                null);
    }

    /** Lightweight official-account records for contact pickers. */
    public List<WeChatContact> getPickerOfficialAccounts() {
        return queryPickerContacts(
                "SELECT " + pickerContactFields()
                        + " FROM rcontact r "
                        + "LEFT JOIN img_flag i ON r.username = i.username "
                        + "WHERE r.username LIKE 'gh\\_%' ESCAPE '\\' "
                        + "OR (r.verifyFlag IS NOT NULL AND r.verifyFlag!=0)",
                null);
    }

    public List<WeChatContact> getGroups() {
        return queryContacts(
                "SELECT " + contactFields()
                        + " FROM rcontact r "
                        + "LEFT JOIN img_flag i ON r.username = i.username "
                        + "WHERE r.username LIKE '%@chatroom' "
                        + "OR r.username LIKE '%@im.chatroom'",
                null);
    }

    public List<WeChatContact> getOfficialAccounts() {
        return queryContacts(
                "SELECT " + contactFields()
                        + " FROM rcontact r "
                        + "LEFT JOIN img_flag i ON r.username = i.username "
                        + "WHERE r.username LIKE 'gh\\_%' ESCAPE '\\' "
                        + "OR (r.verifyFlag IS NOT NULL AND r.verifyFlag!=0)",
                null);
    }

    public List<WeChatContact> searchContacts(String keyword, int limit) {
        return searchContactsInternal(keyword, limit, "");
    }

    public List<WeChatContact> searchFriends(String keyword, int limit) {
        return searchContactsInternal(keyword, limit,
                "AND r.verifyFlag=0 "
                        + "AND (r.type & 1)!=0 "
                        + "AND (r.type & 8)=0 "
                        + "AND (r.type & 32)=0 "
                        + "AND r.username NOT LIKE '%chatroom' ");
    }

    public List<WeChatContact> searchGroups(String keyword, int limit) {
        return searchContactsInternal(keyword, limit,
                "AND (r.username LIKE '%@chatroom' OR r.username LIKE '%@im.chatroom') ");
    }

    public List<WeChatContact> getContactsByIds(List<String> wxIds) {
        List<WeChatContact> result = new ArrayList<>();
        if (wxIds == null || wxIds.isEmpty() || databaseApi == null) return result;

        List<String> cleanIds = new ArrayList<>();
        for (String wxId : wxIds) {
            if (!TextUtils.isEmpty(wxId) && !cleanIds.contains(wxId)) cleanIds.add(wxId);
        }
        if (cleanIds.isEmpty()) return result;

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < cleanIds.size(); i++) {
            if (i > 0) placeholders.append(',');
            placeholders.append('?');
        }

        Map<String, WeChatContact> byId = new HashMap<>();
        for (WeChatContact contact : queryContacts(
                "SELECT " + contactFields()
                        + " FROM rcontact r "
                        + "LEFT JOIN img_flag i ON r.username = i.username "
                        + "WHERE r.username IN (" + placeholders + ")",
                cleanIds.toArray(new String[0]))) {
            byId.put(contact.wxId, contact);
        }

        for (String wxId : wxIds) {
            WeChatContact contact = byId.get(wxId);
            result.add(
                    contact != null
                            ? contact
                            : new WeChatContact(wxId, "", "", "", "", "", "", "", "", 0, 0));
        }
        return result;
    }

    public List<WeChatContact> getGroupMembers(String groupId) {
        if (!isGroup(groupId)) return new ArrayList<>();
        return getContactsByIds(getGroupMemberIds(groupId));
    }

    public Map<String, String> getGroupMemberDisplayNames(String groupId) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!isGroup(groupId)) return result;
        for (String memberWxId : getGroupMemberIds(groupId)) {
            result.put(memberWxId, getGroupMemberDisplayName(groupId, memberWxId));
        }
        return result;
    }

    public List<String> getGroupMemberIds(String groupId) {
        List<String> result = new ArrayList<>();
        if (!isGroup(groupId)) return result;

        String memberList = databaseApi.queryFirstString(
                "SELECT memberlist FROM chatroom WHERE chatroomname=?",
                new String[]{groupId},
                "memberlist");
        if (TextUtils.isEmpty(memberList)) return result;

        String[] parts = memberList.split(";");
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) result.add(part);
        }
        return result;
    }

    private String[] splitMembers(String memberList) {
        if (TextUtils.isEmpty(memberList)) return new String[0];
        return memberList.split(";");
    }

    private String[] splitDisplayNames(String displayNames, int expectedCount) {
        if (TextUtils.isEmpty(displayNames)) return new String[0];
        String[] delimiters = {"\u0001", "\u0002", "\n", ";"};
        for (String delimiter : delimiters) {
            String[] parts = displayNames.split(java.util.regex.Pattern.quote(delimiter), -1);
            if (expectedCount <= 0 || parts.length == expectedCount) return parts;
        }
        return new String[]{displayNames};
    }

    public String getAvatarUrl(String wxId) {
        return getAvatarUrl(wxId, true);
    }

    public String getAvatarUrl(String wxId, boolean isBigHeadImg) {
        if (TextUtils.isEmpty(wxId)) return "";
        List<Map<String, Object>> rows = databaseApi.query(
                "SELECT reserved2, reserved1 FROM img_flag WHERE username=? LIMIT 1",
                new String[]{wxId});
        if (rows.isEmpty()) return "";
        String reserved1 = str(rows.get(0), "reserved1");
        String reserved2 = str(rows.get(0), "reserved2");
        if (isBigHeadImg) {
            return !TextUtils.isEmpty(reserved1) ? reserved1 : reserved2;
        }
        return !TextUtils.isEmpty(reserved2) ? reserved2 : reserved1;
    }

    public List<ContactLabelBean> getContactLabelList() {
        return getContactLabelListResult().labels;
    }

    /** A successful empty snapshot is distinct from a failed label or membership query. */
    public ContactLabelListResult getContactLabelListResult() {
        if (databaseApi == null || !databaseApi.isAvailable()) {
            return failedContactLabelList("联系人数据库不可用");
        }
        LabelTableInfo info = findContactLabelTable();
        if (info == null) return failedContactLabelList("好友标签表不可用");
        String labelIdsColumn = findRcontactLabelIdsColumn();
        if (TextUtils.isEmpty(labelIdsColumn)) {
            return failedContactLabelList("好友标签关系字段不可用");
        }

        String labelsSql = "SELECT " + quoteColumn(info.idColumn) + " AS labelId, "
                + quoteColumn(info.nameColumn) + " AS labelName"
                + " FROM " + databaseApi.quoteTable(info.table)
                + " ORDER BY CAST(" + quoteColumn(info.idColumn) + " AS INTEGER), "
                + quoteColumn(info.nameColumn);
        Cursor labelCursor = databaseApi.rawQuery(labelsSql, null);
        if (labelCursor == null) return failedContactLabelList("读取好友标签失败");
        LinkedHashMap<String, String> labelNames = new LinkedHashMap<>();
        try {
            int idIndex = labelCursor.getColumnIndex("labelId");
            int nameIndex = labelCursor.getColumnIndex("labelName");
            if (idIndex < 0 || nameIndex < 0) {
                return failedContactLabelList("好友标签字段读取失败");
            }
            while (labelCursor.moveToNext()) {
                String labelId = safeCursorString(labelCursor, idIndex);
                String labelName = safeCursorString(labelCursor, nameIndex);
                if (TextUtils.isEmpty(labelId) && TextUtils.isEmpty(labelName)) continue;
                labelNames.put(labelId, labelName);
            }
        } catch (Throwable e) {
            log("读取好友标签失败: " + e.getMessage());
            return failedContactLabelList("读取好友标签失败");
        } finally {
            closeCursor(labelCursor);
        }

        String quotedLabelIds = quoteColumn(labelIdsColumn);
        String contactsSql = "SELECT username, " + quotedLabelIds + " AS labelIds "
                + "FROM rcontact WHERE " + quotedLabelIds + " IS NOT NULL "
                + "AND " + quotedLabelIds + "!=''";
        Cursor contactsCursor = databaseApi.rawQuery(contactsSql, null);
        if (contactsCursor == null) return failedContactLabelList("读取好友标签成员失败");
        Map<String, Set<String>> usersByLabelId = new LinkedHashMap<>();
        try {
            int usernameIndex = contactsCursor.getColumnIndex("username");
            int labelIdsIndex = contactsCursor.getColumnIndex("labelIds");
            if (usernameIndex < 0 || labelIdsIndex < 0) {
                return failedContactLabelList("好友标签成员字段读取失败");
            }
            while (contactsCursor.moveToNext()) {
                String username = safeCursorString(contactsCursor, usernameIndex);
                if (TextUtils.isEmpty(username)) continue;
                for (String labelId : splitLabelIds(safeCursorString(contactsCursor, labelIdsIndex))) {
                    usersByLabelId.computeIfAbsent(labelId, ignored -> new LinkedHashSet<>()).add(username);
                }
            }
        } catch (Throwable e) {
            log("读取好友标签成员失败: " + e.getMessage());
            return failedContactLabelList("读取好友标签成员失败");
        } finally {
            closeCursor(contactsCursor);
        }

        List<ContactLabelBean> labels = new ArrayList<>();
        for (Map.Entry<String, String> entry : labelNames.entrySet()) {
            labels.add(new ContactLabelBean(
                    entry.getKey(),
                    entry.getValue(),
                    new ArrayList<>(usersByLabelId.getOrDefault(entry.getKey(), Collections.emptySet()))));
        }
        return new ContactLabelListResult(true, labels, "");
    }

    private static ContactLabelListResult failedContactLabelList(String error) {
        return new ContactLabelListResult(false, Collections.emptyList(), error);
    }

    private static String safeCursorString(Cursor cursor, int column) {
        return cursor.isNull(column) ? "" : cursor.getString(column);
    }

    private static void closeCursor(Cursor cursor) {
        if (cursor == null) return;
        try {
            cursor.close();
        } catch (Throwable ignored) {}
    }

    /** Reads only the label table; used once as a batch preflight before per-friend requests. */
    public Boolean hasContactLabelId(String labelId) {
        if (TextUtils.isEmpty(labelId) || databaseApi == null || !databaseApi.isAvailable()) return null;
        LabelTableInfo info = findContactLabelTable();
        if (info == null) return null;
        Cursor cursor = databaseApi.rawQuery(
                "SELECT 1 FROM " + databaseApi.quoteTable(info.table)
                        + " WHERE " + quoteColumn(info.idColumn) + "=? LIMIT 1",
                new String[]{labelId.trim()});
        if (cursor == null) return null;
        try {
            return cursor.moveToFirst();
        } catch (Throwable e) {
            log("检查好友标签是否存在失败: " + e.getMessage());
            return null;
        } finally {
            try {
                cursor.close();
            } catch (Throwable ignored) {}
        }
    }

    public List<String> getContactByLabelId(String labelId) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(labelId)) return result;
        String labelIdsColumn = findRcontactLabelIdsColumn();
        if (TextUtils.isEmpty(labelIdsColumn)) return result;
        try {
            String sql = "SELECT username FROM rcontact WHERE "
                    + quoteColumn(labelIdsColumn) + "=? "
                    + "OR " + quoteColumn(labelIdsColumn) + " LIKE ? "
                    + "OR " + quoteColumn(labelIdsColumn) + " LIKE ? "
                    + "OR " + quoteColumn(labelIdsColumn) + " LIKE ?";
            for (Map<String, Object> row : databaseApi.query(
                    sql,
                    new String[]{labelId, labelId + ",%", "%," + labelId + ",%", "%," + labelId})) {
                String wxId = str(row, "username");
                if (!TextUtils.isEmpty(wxId) && !result.contains(wxId)) result.add(wxId);
            }
        } catch (Throwable e) {
            log("按标签ID获取联系人失败: " + e.getMessage());
        }
        return result;
    }

    public List<String> getContactByLabelName(String labelName) {
        if (TextUtils.isEmpty(labelName)) return new ArrayList<>();
        for (ContactLabelBean label : getContactLabelList()) {
            if (labelName.equals(label.getLabelName()) || labelName.equals(label.getName())) {
                return new ArrayList<>(label.getUserNameList());
            }
        }
        return new ArrayList<>();
    }

    public boolean hasContactLabel(String username, String labelName) {
        return Boolean.TRUE.equals(getContactLabelMembershipState(username, labelName));
    }

    /** Returns null when the contact row, label mapping, or label-id column cannot be read. */
    public Boolean getContactLabelMembershipState(String username, String labelName) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(labelName)) return null;
        List<String> labelIds = labelIdsByNames(java.util.Collections.singletonList(labelName.trim()));
        if (labelIds.size() != 1) return null;
        return getContactLabelMembershipStateById(username, labelIds.get(0));
    }

    /** Reads one friend's current label IDs without rescanning the label list. */
    public Boolean getContactLabelMembershipStateById(String username, String labelId) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(labelId) || databaseApi == null) return null;
        String labelIdsColumn = findRcontactLabelIdsColumn();
        if (TextUtils.isEmpty(labelIdsColumn)) return null;
        String currentLabelIds = readContactLabelIds(username, labelIdsColumn);
        return ContactLabelIds.membershipState(currentLabelIds, labelId.trim());
    }

    public String addContactLabel(String labelName) {
        if (TextUtils.isEmpty(labelName)) return "";
        String cleanName = labelName.trim();
        for (ContactLabelBean label : getContactLabelList()) {
            if (cleanName.equals(label.getLabelName()) || cleanName.equals(label.getName())) {
                return label.getLabelId();
            }
        }
        String predictedId = predictNextContactLabelId();
        return sendAddContactLabel(cleanName) ? predictedId : "";
    }

    public boolean modifyContactLabelList(String username, String labelName) {
        List<String> labels = new ArrayList<>();
        if (!TextUtils.isEmpty(labelName)) labels.add(labelName);
        return modifyContactLabelList(username, labels);
    }

    public boolean modifyContactLabelList(String username, List<String> labelNames) {
        if (TextUtils.isEmpty(username) || labelNames == null) return false;
        String labelIdsColumn = findRcontactLabelIdsColumn();
        if (TextUtils.isEmpty(labelIdsColumn)) return false;

        String currentLabelIds =
                databaseApi.queryFirstString(
                        "SELECT "
                                + quoteColumn(labelIdsColumn)
                                + " AS labelIds FROM rcontact WHERE username=? LIMIT 1",
                        new String[] {username},
                        "labelIds");
        String labelIdsValue = buildContactLabelIdsValue(currentLabelIds, labelNames);
        if (labelIdsValue == null) return false;
        return applyContactLabelIds(username, labelIdsColumn, labelIdsValue);
    }

    public boolean replaceContactLabelList(String username, List<String> labelNames) {
        if (TextUtils.isEmpty(username) || labelNames == null) return false;
        String labelIdsColumn = findRcontactLabelIdsColumn();
        if (TextUtils.isEmpty(labelIdsColumn)) return false;
        String labelIdsValue = buildContactLabelIdsValue("", labelNames);
        if (labelIdsValue == null) return false;
        return applyContactLabelIds(username, labelIdsColumn, labelIdsValue);
    }

    /**
     * Adds or removes exactly one native friend label while retaining every other stored label ID.
     * A true return means the native request was accepted and the local contact row was refreshed;
     * callers must not treat it as final server-side state confirmation.
     */
    public ContactLabelMutationResult setContactLabelMembership(
            String username, String labelName, boolean enabled) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(labelName)
                || databaseApi == null || !databaseApi.isAvailable() || !isFriend(username)) {
            return ContactLabelMutationResult.NOT_SUBMITTED;
        }
        String cleanName = labelName.trim();
        List<String> targetIds = labelIdsByNames(java.util.Collections.singletonList(cleanName));
        if (targetIds.size() != 1 || TextUtils.isEmpty(targetIds.get(0))) {
            log("修改好友标签失败: 标签尚未同步 name=" + cleanName);
            return ContactLabelMutationResult.NOT_SUBMITTED;
        }
        String labelIdsColumn = findRcontactLabelIdsColumn();
        if (TextUtils.isEmpty(labelIdsColumn)) return ContactLabelMutationResult.NOT_SUBMITTED;
        String currentLabelIds = readContactLabelIds(username, labelIdsColumn);
        if (currentLabelIds == null) {
            log("读取好友原有标签失败，未提交修改: username=" + username);
            return ContactLabelMutationResult.NOT_SUBMITTED;
        }
        String targetId = targetIds.get(0);
        if (ContactLabelIds.contains(currentLabelIds, targetId) == enabled) {
            return ContactLabelMutationResult.ALREADY_SATISFIED;
        }
        String updatedLabelIds = ContactLabelIds.setMembership(currentLabelIds, targetId, enabled);
        if (updatedLabelIds == null || !sendModifyContactLabelList(username, updatedLabelIds)) {
            return ContactLabelMutationResult.NOT_SUBMITTED;
        }
        ContentValues values = new ContentValues();
        values.put(labelIdsColumn, updatedLabelIds);
        int rows = databaseApi.update("rcontact", values, "username=?", new String[]{username});
        if (rows <= 0) {
            log("好友标签请求已接受但本地列表刷新失败: username=" + username + " rows=" + rows);
        }
        return ContactLabelMutationResult.SUBMITTED;
    }

    /** Updates one ID after a successful row read, preserving every unrelated stored label ID. */
    public ContactLabelMutationResult setContactLabelMembershipById(
            String username, String labelId, boolean enabled) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(labelId)
                || databaseApi == null || !databaseApi.isAvailable() || !isFriend(username)) {
            return ContactLabelMutationResult.NOT_SUBMITTED;
        }
        String labelIdsColumn = findRcontactLabelIdsColumn();
        if (TextUtils.isEmpty(labelIdsColumn)) return ContactLabelMutationResult.NOT_SUBMITTED;
        String currentLabelIds = readContactLabelIds(username, labelIdsColumn);
        if (currentLabelIds == null) {
            log("读取好友原有标签失败，未提交修改: username=" + username);
            return ContactLabelMutationResult.NOT_SUBMITTED;
        }
        String targetId = labelId.trim();
        if (ContactLabelIds.contains(currentLabelIds, targetId) == enabled) {
            return ContactLabelMutationResult.ALREADY_SATISFIED;
        }
        String updatedLabelIds = ContactLabelIds.setMembership(currentLabelIds, targetId, enabled);
        if (updatedLabelIds == null) return ContactLabelMutationResult.NOT_SUBMITTED;
        ContactRequestDispatch.Result dispatch = sendModifyContactLabelListDispatch(username, updatedLabelIds);
        if (dispatch == null) return ContactLabelMutationResult.NOT_SUBMITTED;
        if (dispatch.outcome == ContactRequestDispatch.Outcome.SERVER_REJECTED) {
            return ContactLabelMutationResult.SERVER_REJECTED;
        }
        if (dispatch.outcome == ContactRequestDispatch.Outcome.SERVER_ACCEPTED) {
            Boolean finalMembership = getContactLabelMembershipStateById(username, targetId);
            if (Boolean.valueOf(enabled).equals(finalMembership)) {
                return ContactLabelMutationResult.CONFIRMED;
            }
            return ContactLabelMutationResult.SERVER_ACCEPTED;
        }
        return dispatch.wasSubmitted()
                ? ContactLabelMutationResult.SUBMITTED_UNCONFIRMED
                : ContactLabelMutationResult.NOT_SUBMITTED;
    }

    /**
     * Submits WeChat's native list-scene once for all prepared contacts when available.
     * Each entry carries that contact's own complete label-ID string. The request response is
     * scene-wide, so final state is read back per contact; this method never writes rcontact.
     */
    public List<ContactLabelBatchItemResult> setContactLabelMembershipsById(
            List<String> usernames,
            String labelId,
            boolean enabled,
            BatchSubmissionGuard guard) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        if (usernames != null) {
            for (String username : usernames) {
                if (username == null) continue;
                String cleanUsername = username.trim();
                if (!TextUtils.isEmpty(cleanUsername)) uniqueIds.add(cleanUsername);
            }
        }
        LinkedHashMap<String, ContactLabelBatchItemResult> results = new LinkedHashMap<>();
        for (String username : uniqueIds) {
            results.put(username, null);
        }
        if (uniqueIds.isEmpty()) return new ArrayList<>();
        if (TextUtils.isEmpty(labelId) || databaseApi == null || !databaseApi.isAvailable()) {
            return batchResultList(uniqueIds, results, ContactLabelMutationResult.NOT_SUBMITTED,
                    "好友标签 API 或标签 ID 不可用");
        }
        Boolean labelExists = hasContactLabelId(labelId);
        if (!Boolean.TRUE.equals(labelExists)) {
            return batchResultList(uniqueIds, results, ContactLabelMutationResult.NOT_SUBMITTED,
                    labelExists == null ? "无法读取所选标签" : "所选标签已不存在");
        }
        String targetId = labelId.trim();
        String labelIdsColumn = findRcontactLabelIdsColumn();
        if (TextUtils.isEmpty(labelIdsColumn)) {
            return batchResultList(uniqueIds, results, ContactLabelMutationResult.NOT_SUBMITTED,
                    "好友标签字段不可用");
        }

        List<LabelBatchChange> changes = new ArrayList<>();
        for (String username : uniqueIds) {
            if (!maySubmit(guard)) {
                results.put(username, batchItem(username, ContactLabelMutationResult.NOT_SUBMITTED,
                        "账号已切换或任务已停止"));
                break;
            }
            if (!isFriend(username)) {
                results.put(username, batchItem(username, ContactLabelMutationResult.INVALID_TARGET,
                        "好友关系已失效"));
                continue;
            }
            String currentLabelIds = readContactLabelIds(username, labelIdsColumn);
            if (currentLabelIds == null) {
                results.put(username, batchItem(username, ContactLabelMutationResult.NOT_SUBMITTED,
                        "读取好友原有标签失败，未提交"));
                continue;
            }
            if (ContactLabelIds.contains(currentLabelIds, targetId) == enabled) {
                results.put(username, batchItem(username, ContactLabelMutationResult.ALREADY_SATISFIED,
                        "好友标签已符合目标"));
                continue;
            }
            String updated = ContactLabelIds.setMembership(currentLabelIds, targetId, enabled);
            if (updated == null) {
                results.put(username, batchItem(username, ContactLabelMutationResult.NOT_SUBMITTED,
                        "生成好友标签列表失败，未提交"));
                continue;
            }
            changes.add(new LabelBatchChange(username, updated));
        }
        if (changes.isEmpty()) {
            return completeBatchResults(uniqueIds, results, "");
        }
        if (!maySubmit(guard)) {
            for (LabelBatchChange change : changes) {
                results.put(change.username, batchItem(change.username,
                        ContactLabelMutationResult.NOT_SUBMITTED, "账号已切换或任务已停止"));
            }
            return completeBatchResults(uniqueIds, results, "账号已切换或任务已停止");
        }

        Constructor<?> ctor = dexFinder != null ? dexFinder.modifyContactLabelListCtor : null;
        boolean batchReady = ctor != null && networkDispatcher != null && dexFinder != null;
        LinkedList<Object> requestItems = new LinkedList<>();
        if (batchReady) {
            installNetworkHook();
            for (LabelBatchChange change : changes) {
                Object item = buildModifyContactLabelItem(ctor, change.username, change.labelIds);
                if (item == null) {
                    batchReady = false;
                    break;
                }
                requestItems.add(item);
            }
        }

        ContactRequestDispatch.Result dispatch = null;
        if (batchReady && requestItems.size() == changes.size() && maySubmit(guard)) {
            boolean nativeDispatchStarted = false;
            try {
                Object scene = KavaReflector.newInstance(ctor, requestItems);
                if (scene != null) {
                    nativeDispatchStarted = true;
                    dispatch = sendNativeContactScene(scene);
                }
            } catch (Throwable e) {
                log("好友标签批量请求构造或提交失败: " + e.getMessage());
                dispatch = nativeDispatchStarted ? ContactRequestDispatch.dispatchFailed() : null;
            }
        }
        if (dispatch == null && !maySubmit(guard)) {
            for (LabelBatchChange change : changes) {
                results.put(change.username, batchItem(change.username,
                        ContactLabelMutationResult.NOT_SUBMITTED, "账号已切换或任务已停止"));
            }
            return completeBatchResults(uniqueIds, results, "账号已切换或任务已停止");
        }

        if (ContactRequestDispatch.safeToFallbackToPerItem(dispatch)) {
            log("原生好友标签批量入口未提交，退回逐好友原生请求");
            for (LabelBatchChange change : changes) {
                if (!maySubmit(guard)) {
                    results.put(change.username, batchItem(change.username,
                            ContactLabelMutationResult.NOT_SUBMITTED, "账号已切换或任务已停止"));
                    continue;
                }
                results.put(change.username, batchItem(
                        change.username,
                        setContactLabelMembershipById(change.username, targetId, enabled),
                        "逐好友原生请求"));
            }
            return completeBatchResults(uniqueIds, results,
                    maySubmit(guard) ? "" : "账号已切换或任务已停止");
        }

        for (LabelBatchChange change : changes) {
            Boolean finalMembership = getContactLabelMembershipStateById(change.username, targetId);
            if (Boolean.valueOf(enabled).equals(finalMembership)) {
                results.put(change.username, batchItem(change.username,
                        ContactLabelMutationResult.CONFIRMED, "原生批量请求后标签状态已确认"));
            } else if (dispatch.outcome == ContactRequestDispatch.Outcome.SERVER_ACCEPTED) {
                results.put(change.username, batchItem(change.username,
                        ContactLabelMutationResult.SERVER_ACCEPTED, "服务端已接受，最终状态未确认"));
            } else if (dispatch.outcome == ContactRequestDispatch.Outcome.SERVER_REJECTED) {
                results.put(change.username, batchItem(change.username,
                        ContactLabelMutationResult.SERVER_REJECTED, "服务端拒绝好友标签批量请求"));
            } else {
                results.put(change.username, batchItem(change.username,
                        ContactLabelMutationResult.SUBMITTED_UNCONFIRMED,
                        "批量请求已提交，服务端响应未确认"));
            }
        }
        return completeBatchResults(uniqueIds, results, "");
    }

    private static final class LabelBatchChange {
        final String username;
        final String labelIds;

        LabelBatchChange(String username, String labelIds) {
            this.username = username;
            this.labelIds = labelIds;
        }
    }

    private static boolean maySubmit(BatchSubmissionGuard guard) {
        if (guard == null) return true;
        try {
            return guard.mayContinue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ContactLabelBatchItemResult batchItem(
            String username, ContactLabelMutationResult outcome, String message) {
        return new ContactLabelBatchItemResult(username, outcome, message);
    }

    private static List<ContactLabelBatchItemResult> batchResultList(
            Iterable<String> usernames,
            Map<String, ContactLabelBatchItemResult> current,
            ContactLabelMutationResult outcome,
            String message) {
        for (String username : usernames) current.put(username, batchItem(username, outcome, message));
        return new ArrayList<>(current.values());
    }

    private static List<ContactLabelBatchItemResult> completeBatchResults(
            Iterable<String> usernames,
            Map<String, ContactLabelBatchItemResult> current,
            String defaultMessage) {
        for (String username : usernames) {
            if (current.get(username) == null) {
                current.put(username, batchItem(username, ContactLabelMutationResult.NOT_SUBMITTED,
                        defaultMessage.isEmpty() ? "未提交" : defaultMessage));
            }
        }
        return new ArrayList<>(current.values());
    }

    private String readContactLabelIds(String username, String labelIdsColumn) {
        Cursor cursor = databaseApi.rawQuery(
                "SELECT IFNULL(" + quoteColumn(labelIdsColumn) + ", '') AS labelIds "
                        + "FROM rcontact WHERE username=? LIMIT 1",
                new String[]{username});
        if (cursor == null) return null;
        try {
            if (!cursor.moveToFirst()) return null;
            int index = cursor.getColumnIndex("labelIds");
            return index >= 0 ? cursor.getString(index) : null;
        } catch (Throwable e) {
            log("读取好友标签列表失败: " + e.getMessage() + " username=" + username);
            return null;
        } finally {
            try {
                cursor.close();
            } catch (Throwable ignored) {}
        }
    }

    private boolean applyContactLabelIds(String username, String labelIdsColumn, String labelIdsValue) {
        if (!sendModifyContactLabelList(username, labelIdsValue)) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(labelIdsColumn, labelIdsValue);
        int rows = databaseApi.update("rcontact", values, "username=?", new String[]{username});
        if (rows <= 0) {
            log("修改好友标签失败: username=" + username + " rows=" + rows);
            return false;
        }
        return true;
    }

    public boolean modifyContactRemark(String username, String remarkName) {
        if (TextUtils.isEmpty(username)) return false;
        String cleanRemark = remarkName == null ? "" : remarkName.trim();
        ContactRequestDispatch.Result result =
                sendSetContactPropertyRemark(username, cleanRemark, "");
        if (result == null || !result.serverAccepted()) {
            log("修改好友备注失败: 服务端未确认 username=" + username
                    + " outcome=" + (result != null ? result.outcome : "unavailable"));
            return false;
        }
        ContentValues values = new ContentValues();
        values.put("conRemark", cleanRemark);
        int rows = databaseApi.update("rcontact", values, "username=?", new String[]{username});
        if (rows <= 0) {
            log("修改好友备注本地刷新失败: username=" + username + " rows=" + rows);
            return false;
        }
        return true;
    }

    /**
     * 返回微信联系人存储中的原生 Contact 对象，供其它内部 API 调用微信原生业务入口。
     */
    public Object getNativeContactObject(String username) {
        if (TextUtils.isEmpty(username)) return null;
        return findNativeContact(username.trim());
    }

    private Object findNativeContact(String username) {
        Object storage = nativeContactStorage;
        Method getter = nativeContactGetterMethod;
        Object contact = invokeNativeContactGetter(storage, getter, username);
        if (contact != null) return contact;

        Object serviceContact = nativeContactFromService(username);
        if (serviceContact != null) return serviceContact;

        Object coreStorage = databaseApi != null ? databaseApi.getCoreStorage() : null;
        if (coreStorage == null) return null;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return findNativeContactRecursive(coreStorage, username, visited);
    }

    private Object nativeContactFromService(String username) {
        if (dexFinder == null) return null;
        Method storageGetter = dexFinder.contactStorageGetterMethod;
        Method contactGetter = dexFinder.contactStorageQueryMethod;
        if (storageGetter == null || contactGetter == null) return null;
        try {
            Object service = WeChatInternalServices.getService(
                    dexFinder, storageGetter.getDeclaringClass());
            if (service == null) return null;
            Object storage = KavaReflector.invoke(storageGetter, service);
            if (storage == null || !contactGetter.getDeclaringClass().isInstance(storage)) return null;
            Object contact = invokeNativeContactGetter(storage, contactGetter, username);
            if (contact == null) return null;
            nativeContactStorage = storage;
            nativeContactGetterMethod = contactGetter;
            return contact;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object findNativeContactRecursive(Object candidate, String username, Set<Object> visited) {
        if (candidate == null || visited.contains(candidate)) return null;
        visited.add(candidate);
        Method getter = findNativeContactGetterMethod(candidate, username);
        Object contact = invokeNativeContactGetter(candidate, getter, username);
        if (contact != null) {
            nativeContactStorage = candidate;
            nativeContactGetterMethod = getter;
            return contact;
        }
        Object accessorContact = findNativeContactFromAccessors(candidate, username, visited);
        if (accessorContact != null) return accessorContact;
        for (Field field : KavaReflector.declaredFields(candidate.getClass())) {
            try {
                if (KavaReflector.isStatic(field)) continue;
                Class<?> type = field.getType();
                if (type == null || type.isPrimitive() || type == String.class) continue;
                Object value = KavaReflector.readField(field, candidate);
                Object nested = findNativeContactRecursive(value, username, visited);
                if (nested != null) return nested;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Object findNativeContactFromAccessors(Object candidate, String username, Set<Object> visited) {
        Class<?> cur = candidate.getClass();
        while (cur != null && cur != Object.class) {
            for (Method method : KavaReflector.declaredMethods(cur)) {
                if (!isContactStorageAccessor(method)) continue;
                try {
                    Object value = KavaReflector.invoke(method, candidate);
                    Object nested = findNativeContactRecursive(value, username, visited);
                    if (nested != null) return nested;
                } catch (Throwable ignored) {
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private boolean isContactStorageAccessor(Method method) {
        if (method == null || KavaReflector.isStatic(method)) return false;
        if (method.getParameterTypes().length != 0) return false;
        Class<?> returnType = method.getReturnType();
        if (returnType == null || returnType.isPrimitive() || returnType == String.class || returnType == Void.TYPE) {
            return false;
        }
        String name = method.getName();
        for (String accessor : CONTACT_STORAGE_ACCESSOR_NAMES) {
            if (accessor.equals(name)) return true;
        }
        return false;
    }

    private Method findNativeContactGetterMethod(Object candidate, String username) {
        Class<?> clazz = candidate != null ? candidate.getClass() : null;
        if (clazz == null) return null;
        for (Method method : KavaReflector.declaredMethods(clazz)) {
            Class<?>[] params = method.getParameterTypes();
            if (params == null || params.length != 2 || params[0] != String.class
                    || (params[1] != Boolean.TYPE && params[1] != Boolean.class)) {
                continue;
            }
            try {
                Object result = KavaReflector.invoke(method, candidate, username, Boolean.TRUE);
                if (isNativeContactObject(result, username)) return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Object invokeNativeContactGetter(Object storage, Method getter, String username) {
        if (storage == null || getter == null || TextUtils.isEmpty(username)) return null;
        try {
            Object result = KavaReflector.invoke(getter, storage, username, Boolean.TRUE);
            return isNativeContactObject(result, username) ? result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isNativeContactObject(Object value, String username) {
        if (value == null) return false;
        Object fieldUsername = KavaReflector.readField(value, "field_username");
        if (username.equals(fieldUsername)) return true;
        Object plainUsername = KavaReflector.readField(value, "username");
        if (username.equals(plainUsername)) return true;
        if (KavaReflector.findFieldRecursive(value.getClass(), "field_username") != null
                && KavaReflector.findFieldRecursive(value.getClass(), "field_conRemark") != null) {
            return true;
        }
        if (KavaReflector.findFieldRecursive(value.getClass(), "username") != null
                && KavaReflector.findFieldRecursive(value.getClass(), "conRemark") != null) {
            return true;
        }
        return false;
    }

    private List<WeChatContact> queryContacts(String sql, String[] args) {
        List<WeChatContact> result = new ArrayList<>();
        for (Map<String, Object> row : databaseApi.query(sql, args)) {
            result.add(toContact(row));
        }
        return result;
    }

    private List<WeChatContact> queryPickerContacts(String sql, String[] args) {
        List<WeChatContact> result = new ArrayList<>();
        for (Map<String, Object> row : databaseApi.query(sql, args)) {
            result.add(toPickerContact(row));
        }
        return result;
    }

    private String buildContactLabelIdsValue(String currentLabelIds, List<String> labelNames) {
        if (labelNames == null) return null;
        List<String> cleanNames = new ArrayList<>();
        for (String labelName : labelNames) {
            if (TextUtils.isEmpty(labelName)) continue;
            String cleanName = labelName.trim();
            if (!TextUtils.isEmpty(cleanName) && !cleanNames.contains(cleanName)) {
                cleanNames.add(cleanName);
            }
        }
        if (cleanNames.isEmpty()) return "";
        List<ContactLabelBean> labels = getContactLabelList();
        List<String> labelIds = labelIdsByNames(cleanNames, labels);
        if (labelIds.size() != cleanNames.size()) {
            log("生成好友标签ID失败: 存在尚未同步的标签 names=" + cleanNames);
            return null;
        }
        List<String> validLabelIds = new ArrayList<>();
        for (ContactLabelBean label : labels) {
            String labelId = label.getLabelId();
            if (!TextUtils.isEmpty(labelId) && !validLabelIds.contains(labelId)) {
                validLabelIds.add(labelId);
            }
        }
        return mergeLabelIdsFallback(currentLabelIds, labelIds, validLabelIds);
    }

    private List<String> labelIdsByNames(List<String> labelNames) {
        return labelIdsByNames(labelNames, getContactLabelList());
    }

    private List<String> labelIdsByNames(List<String> labelNames, List<ContactLabelBean> labels) {
        List<String> result = new ArrayList<>();
        if (labelNames == null || labelNames.isEmpty()) return result;
        for (String labelName : labelNames) {
            if (TextUtils.isEmpty(labelName)) continue;
            String cleanName = labelName.trim();
            for (ContactLabelBean label : labels) {
                if (!cleanName.equals(label.getLabelName()) && !cleanName.equals(label.getName())) continue;
                String labelId = label.getLabelId();
                if (!TextUtils.isEmpty(labelId) && !result.contains(labelId)) result.add(labelId);
                break;
            }
        }
        return result;
    }

    private String mergeLabelIdsFallback(String currentLabelIds,
                                         List<String> labelIds,
                                         List<String> validLabelIds) {
        if (labelIds == null || labelIds.isEmpty()) return "";
        List<String> mergedIds = new ArrayList<>();
        for (String labelId : splitLabelIds(currentLabelIds)) {
            if (validLabelIds != null && validLabelIds.contains(labelId) && !mergedIds.contains(labelId)) {
                mergedIds.add(labelId);
            }
        }
        for (String labelId : labelIds) {
            String cleanId = labelId == null ? "" : labelId.trim();
            if (TextUtils.isEmpty(cleanId)) continue;
            if (!mergedIds.contains(cleanId)) mergedIds.add(cleanId);
        }
        if (mergedIds.isEmpty()) return "";
        return TextUtils.join(",", mergedIds) + '\u0000';
    }

    private String stripLabelIdsSuffix(String value) {
        if (TextUtils.isEmpty(value)) return "";
        return value.endsWith("\u0000") ? value.substring(0, value.length() - 1) : value;
    }

    private List<String> splitLabelIds(String value) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(value)) return result;
        String[] parts = stripLabelIdsSuffix(value).split(",");
        for (String part : parts) {
            if (TextUtils.isEmpty(part)) continue;
            String clean = part.trim();
            if (!TextUtils.isEmpty(clean) && !result.contains(clean)) result.add(clean);
        }
        return result;
    }

    private boolean sendAddContactLabel(String labelName) {
        Constructor<?> ctor = dexFinder != null ? dexFinder.addContactLabelCtorString : null;
        if (ctor == null || networkDispatcher == null || dexFinder == null) {
            log("增加联系人标签失败: 网络API未就绪");
            return false;
        }
        installNetworkHook();
        try {
            Object scene = KavaReflector.newInstance(ctor, labelName);
            boolean result = networkDispatcher.send(scene);
            if (!result) log("增加联系人标签发包失败: " + labelName);
            return result;
        } catch (Throwable e) {
            log("增加联系人标签发包异常: " + e.getMessage());
            return false;
        }
    }

    private boolean sendModifyContactLabelList(String username, String labelIdsValue) {
        ContactRequestDispatch.Result result = sendModifyContactLabelListDispatch(username, labelIdsValue);
        return result != null && result.serverAccepted();
    }

    private ContactRequestDispatch.Result sendModifyContactLabelListDispatch(
            String username, String labelIdsValue) {
        Constructor<?> ctor = dexFinder != null ? dexFinder.modifyContactLabelListCtor : null;
        if (ctor != null && networkDispatcher != null && dexFinder != null) {
            installNetworkHook();
            try {
                Object item = buildModifyContactLabelItem(ctor, username, labelIdsValue);
                if (item != null) {
                    LinkedList<Object> list = new LinkedList<>();
                    list.add(item);
                    Object scene = KavaReflector.newInstance(ctor, list);
                    ContactRequestDispatch.Result nativeResult = sendNativeContactScene(scene);
                    if (nativeResult != null
                            && nativeResult.outcome != ContactRequestDispatch.Outcome.NOT_SUBMITTED
                            && nativeResult.outcome != ContactRequestDispatch.Outcome.DISPATCH_FAILED) {
                        if (!nativeResult.serverAccepted()) {
                            log("修改好友标签原生请求未获服务端确认: username=" + username
                                    + " outcome=" + nativeResult.outcome);
                        }
                        return nativeResult;
                    }
                    log("修改好友标签原生请求未提交，尝试联系人请求通道: username=" + username);
                } else {
                    log("修改好友标签原生请求项为空，尝试联系人请求通道");
                }
            } catch (Throwable e) {
                log("修改好友标签原生请求异常: " + e.getMessage());
                return ContactRequestDispatch.dispatchFailed();
            }
        }
        return sendModifyContactLabelListByPb(username, labelIdsValue);
    }

    private ContactRequestDispatch.Result sendModifyContactLabelListByPb(String username, String labelIdsValue) {
        if (TextUtils.isEmpty(username)) return ContactRequestDispatch.dispatchFailed();
        try {
            return ContactProtobufSender.modifyContactLabelList(dexFinder, username, labelIdsValue);
        } catch (Throwable e) {
            log("修改好友标签PB发包异常: " + e.getMessage());
            return ContactRequestDispatch.dispatchFailed();
        }
    }

    private ContactRequestDispatch.Result sendSetContactPropertyRemark(
            String username, String remarkName, String roomName) {
        if (TextUtils.isEmpty(username)) return ContactRequestDispatch.dispatchFailed();
        try {
            return ContactProtobufSender.setContactRemark(dexFinder, username, remarkName, roomName);
        } catch (Throwable e) {
            log("修改好友备注PB发包异常: " + e.getMessage());
            return ContactRequestDispatch.dispatchFailed();
        }
    }

    private ContactRequestDispatch.Result sendNativeContactScene(Object scene) {
        if (scene == null || networkDispatcher == null) return ContactRequestDispatch.notSubmitted();
        Method callbackMethod = findContactSceneCallbackMethod(scene.getClass());
        if (callbackMethod == null) {
            log("联系人原生场景没有可观察的服务端回调");
            return ContactRequestDispatch.notSubmitted();
        }
        AtomicReference<ContactRequestDispatch.Callback> nativeCallback = new AtomicReference<>();

        final XposedInterface.HookHandle unhook;
        try {
            unhook =
                    HookRegistry.get()
                            .hook(
                                    callbackMethod,
                                    new XposedInterface.Hooker() {
                                        @Override
                                        public Object intercept(XposedInterface.Chain chain)
                                                throws Throwable {
                                            Object[] args = chain.getArgs().toArray();
                                            Object hookResult = chain.proceed();
                                            Object receiver = chain.getThisObject();
                                            if (receiver != scene
                                                    || args == null
                                                    || args.length < 2
                                                    || !(args[0] instanceof Number)
                                                    || !(args[1] instanceof Number)) {
                                                return hookResult;
                                            }
                                            ContactRequestDispatch.Callback callback =
                                                    nativeCallback.get();
                                            if (callback != null) {
                                                callback.onResponse(
                                                        ((Number) args[0]).intValue(),
                                                        ((Number) args[1]).intValue());
                                            }

                                            return hookResult;
                                        }
                                    });
        } catch (Throwable e) {
            log("联系人原生场景回调Hook失败: " + e.getMessage());
            return ContactRequestDispatch.notSubmitted();
        }

        try {
            return ContactRequestDispatch.submitAndAwait(callback -> {
                nativeCallback.set(callback);
                return networkDispatcher.send(scene) ? scene : null;
            });
        } finally {
            nativeCallback.set(null);
            HookRegistry.get().unhook(unhook);
        }
    }

    private Method findContactSceneCallbackMethod(Class<?> sceneClass) {
        Class<?> sceneType = sceneClass;
        while (sceneType != null && sceneType != Object.class) {
            for (Method method : KavaReflector.declaredMethods(sceneType)) {
                Class<?>[] params = method.getParameterTypes();
                if (!"callback".equals(method.getName())
                        || method.getReturnType() != int.class
                        || params.length != 5
                        || params[0] != int.class
                        || params[1] != int.class
                        || params[2] != String.class
                        || params[3].isPrimitive()
                        || !params[4].isAssignableFrom(sceneClass)) {
                    continue;
                }
                return KavaReflector.accessible(method);
            }
            sceneType = sceneType.getSuperclass();
        }
        return null;
    }

    private Object buildModifyContactLabelItem(Constructor<?> sceneCtor, String username, String labelIdsValue) {
        try {
            Class<?> itemClass = resolveModifyContactLabelItemClass(sceneCtor);
            if (itemClass == null) return null;
            Object item = KavaReflector.newInstanceByArgs(itemClass, new Object[0]);
            if (item == null) {
                item = KavaReflector.newInstance(KavaReflector.findConstructor(itemClass));
            }
            if (item == null) return null;
            boolean writeUsername = KavaReflector.writeField(item, "d", username);
            boolean writeLabelIds =
                    KavaReflector.writeField(item, "e", labelIdsValue == null ? "" : labelIdsValue);
            if (!writeUsername || !writeLabelIds) {
                log("构造标签请求项失败: 字段写入失败 username=" + writeUsername + " labelIds=" + writeLabelIds);
                return null;
            }
            return item;
        } catch (Throwable e) {
            log("构造标签请求项失败: " + e.getMessage());
            return null;
        }
    }

    private Class<?> resolveModifyContactLabelItemClass(Constructor<?> sceneCtor) {
        Class<?> cached = modifyContactLabelItemClass;
        if (cached != null) return cached;
        if (sceneCtor == null) return null;
        try {
            Object scene = KavaReflector.newInstance(sceneCtor, new LinkedList<>());
            Object requestPb = findModifyLabelRequestProto(scene);
            if (requestPb == null) return null;
            for (Field field : KavaReflector.declaredFields(requestPb.getClass())) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                Type genericType = field.getGenericType();
                if (!(genericType instanceof ParameterizedType)) continue;
                Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
                if (args == null || args.length != 1 || !(args[0] instanceof Class<?>)) continue;
                Class<?> itemClass = (Class<?>) args[0];
                Field usernameField = KavaReflector.findFieldRecursive(itemClass, "d");
                Field labelIdsField = KavaReflector.findFieldRecursive(itemClass, "e");
                if (usernameField == null || labelIdsField == null) continue;
                if (usernameField.getType() != String.class || labelIdsField.getType() != String.class) continue;
                modifyContactLabelItemClass = itemClass;
                return modifyContactLabelItemClass;
            }
        } catch (Throwable e) {
            log("解析标签请求项类型失败: " + e.getMessage());
        }
        return null;
    }

    private Object findModifyLabelRequestProto(Object scene) {
        if (scene == null) return null;
        try {
            Object requestWrapper = KavaReflector.readField(scene, "d");
            Object reqResp =
                    requestWrapper != null ? KavaReflector.readField(requestWrapper, "a") : null;
            Object requestPb = reqResp != null ? KavaReflector.readField(reqResp, "a") : null;
            if (requestPb != null) return requestPb;
        } catch (Throwable ignored) {
        }
        Object requestPb = findProtoObject(scene);
        if (requestPb != null) return requestPb;
        List<Object> protoObjects = new ArrayList<>();
        collectProtoObjects(scene, new java.util.IdentityHashMap<>(), protoObjects);
        return protoObjects.isEmpty() ? null : protoObjects.get(0);
    }

    private Object findProtoObject(Object value) {
        return findProtoObject(value, new java.util.IdentityHashMap<>());
    }

    private void collectProtoObjects(Object value,
                                     java.util.IdentityHashMap<Object, Boolean> seen,
                                     List<Object> out) {
        if (value == null || out == null) return;
        if (seen.containsKey(value)) return;
        seen.put(value, Boolean.TRUE);
        if (isProtoObject(value)) {
            out.add(value);
        }
        try {
            for (Field field : KavaReflector.declaredFields(value.getClass())) {
                Object child = KavaReflector.readField(field, value);
                if (child == null || child == value) continue;
                collectProtoObjects(child, seen, out);
            }
        } catch (Throwable ignored) {
        }
    }

    private Object findProtoObject(Object value, java.util.IdentityHashMap<Object, Boolean> seen) {
        if (value == null) return null;
        if (seen.containsKey(value)) return null;
        seen.put(value, Boolean.TRUE);
        if (isProtoObject(value)) return value;
        try {
            for (Field field : KavaReflector.declaredFields(value.getClass())) {
                Object child = KavaReflector.readField(field, value);
                if (child == null || child == value) continue;
                if (isProtoObject(child)) return child;
                Object nested = findProtoObject(child, seen);
                if (nested != null) return nested;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isProtoObject(Object value) {
        return value != null
                && KavaReflector.findMethodRecursive(value.getClass(), "toByteArray") != null
                && KavaReflector.findMethodRecursive(value.getClass(), "parseFrom", byte[].class)
                        != null;
    }

    private void installNetworkHook() {
        if (networkHookInstalled || networkDispatcher == null || dexFinder == null) return;
        if (dexFinder.netQueueClass == null && dexFinder.netQueueCandidateClasses.isEmpty()) return;
        networkDispatcher.hookNetworkQueue(dexFinder.netQueueClass, dexFinder.netQueueCandidateClasses);
        networkHookInstalled = true;
    }

    private String nextContactLabelId(LabelTableInfo info) {
        if (info == null) return "";
        String value = databaseApi.queryFirstString(
                "SELECT MAX(CAST(" + quoteColumn(info.idColumn) + " AS INTEGER)) AS maxId"
                        + " FROM " + databaseApi.quoteTable(info.table),
                null,
                "maxId");
        int next = intValue(value) + 1;
        if (next <= 0) next = 1;
        return String.valueOf(next);
    }

    private String predictNextContactLabelId() {
        try {
            return nextContactLabelId(findContactLabelTable());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private List<WeChatContact> searchContactsInternal(String keyword, int limit, String extraWhere) {
        if (TextUtils.isEmpty(keyword)) return new ArrayList<>();
        int safeLimit = clamp(limit, 1, 200);
        String like = "%" + keyword.trim() + "%";
        return queryContacts(
                "SELECT " + contactFields()
                        + " FROM rcontact r "
                        + "LEFT JOIN img_flag i ON r.username = i.username "
                        + "WHERE (r.username LIKE ? OR r.alias LIKE ? "
                        + "OR r.conRemark LIKE ? OR r.nickname LIKE ?) "
                        + extraWhere
                        + "ORDER BY CASE "
                        + "WHEN r.conRemark LIKE ? THEN 0 "
                        + "WHEN r.nickname LIKE ? THEN 1 "
                        + "WHEN r.alias LIKE ? THEN 2 "
                        + "ELSE 3 END, r.nickname COLLATE NOCASE LIMIT ?",
                new String[]{like, like, like, like, like, like, like, String.valueOf(safeLimit)});
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private WeChatContact toContact(Map<String, Object> row) {
        LvContactProfile profile = parseLvContactProfile(blob(row, "lvbuff"));
        return new WeChatContact(
                str(row, "username"),
                str(row, "nickname"),
                str(row, "alias"),
                str(row, "conRemark"),
                str(row, "avatarUrl"),
                str(row, "avatarBackupUrl"),
                str(row, "encryptUsername"),
                profile.province,
                profile.city,
                profile.gender,
                intValue(row, "type"));
    }

    private WeChatContact toPickerContact(Map<String, Object> row) {
        return new WeChatContact(
                str(row, "username"),
                str(row, "nickname"),
                str(row, "alias"),
                str(row, "conRemark"),
                str(row, "avatarUrl"),
                str(row, "avatarBackupUrl"),
                str(row, "encryptUsername"),
                "",
                "",
                0,
                intValue(row, "type"));
    }

    private String contactFields() {
        return CONTACT_FIELDS_BASE;
    }

    private String pickerContactFields() {
        return PICKER_CONTACT_FIELDS_BASE;
    }

    private LabelTableInfo findContactLabelTable() {
        if (databaseApi == null) return null;
        for (String table : CONTACT_LABEL_TABLES) {
            if (!tableExists(table)) continue;
            String idColumn = firstExistingColumn(table, CONTACT_LABEL_ID_COLUMNS);
            String nameColumn = firstExistingColumn(table, CONTACT_LABEL_NAME_COLUMNS);
            if (TextUtils.isEmpty(idColumn) || TextUtils.isEmpty(nameColumn)) continue;
            return new LabelTableInfo(
                    table,
                    idColumn,
                    nameColumn);
        }
        return null;
    }

    private String findRcontactLabelIdsColumn() {
        return firstExistingColumn("rcontact", CONTACT_LABEL_IDS_COLUMNS);
    }

    private boolean tableExists(String table) {
        if (TextUtils.isEmpty(table) || databaseApi == null) return false;
        try {
            String exists = databaseApi.queryFirstString(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                    new String[]{table},
                    "name");
            return !TextUtils.isEmpty(exists);
        } catch (Throwable e) {
            return false;
        }
    }

    private String firstExistingColumn(String table, String[] columns) {
        if (columns == null) return "";
        for (String column : columns) {
            if (hasColumn(table, column)) return column;
        }
        return "";
    }

    private String quoteColumn(String column) {
        if (TextUtils.isEmpty(column)) return "";
        for (int i = 0; i < column.length(); i++) {
            char c = column.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_') {
                continue;
            }
            return "";
        }
        return "`" + column + "`";
    }

    private boolean hasColumn(String table, String column) {
        if (TextUtils.isEmpty(table) || TextUtils.isEmpty(column) || databaseApi == null) return false;
        String key = table + ":" + column;
        Boolean cached = columnExistsCache.get(key);
        if (cached != null) return cached;
        boolean exists = false;
        try {
            List<Map<String, Object>> rows =
                    databaseApi.query("PRAGMA table_info(" + table + ")", null);
            for (Map<String, Object> row : rows) {
                if (column.equalsIgnoreCase(str(row, "name"))) {
                    exists = true;
                    break;
                }
            }
        } catch (Throwable e) {
            log("检测字段失败: " + table + "." + column + " " + e.getMessage());
        }
        columnExistsCache.put(key, exists);
        return exists;
    }

    private static final class LabelTableInfo {
        final String table;
        final String idColumn;
        final String nameColumn;

        LabelTableInfo(String table, String idColumn, String nameColumn) {
            this.table = table;
            this.idColumn = idColumn;
            this.nameColumn = nameColumn;
        }
    }

    private String str(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        return value != null ? String.valueOf(value) : "";
    }

    private byte[] blob(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        return value instanceof byte[] ? (byte[]) value : null;
    }

    private int intValue(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private boolean isSelf(String wxId) {
        return !TextUtils.isEmpty(wxId) && wxId.equals(selfProfileField(SELF_FIELD_WXID));
    }

    private String selfProfileField(int fieldCode) {
        if (databaseApi == null) return "";
        return databaseApi.queryFirstString(
                "SELECT value FROM userinfo WHERE id=?",
                new String[]{String.valueOf(fieldCode)},
                "value");
    }

    private int intValue(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private LvContactProfile parseLvContactProfile(byte[] lvbuff) {
        LvContactProfile profile = new LvContactProfile();
        if (lvbuff == null || lvbuff.length < 2) return profile;
        if (lvbuff[0] != 123 || lvbuff[lvbuff.length - 1] != 125) return profile;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(lvbuff);
            buffer.position(1);

            readInt(buffer);
            profile.gender = readInt(buffer);
            readString(buffer);
            readLong(buffer);
            readInt(buffer);
            readString(buffer);
            readString(buffer);
            readInt(buffer);
            readInt(buffer);
            readString(buffer);
            readString(buffer);
            profile.chatRoomNotify = readInt(buffer);
            readInt(buffer);
            readString(buffer);
            profile.province = readString(buffer);
            profile.city = readString(buffer);
        } catch (Throwable e) {
            log("解析联系人lvbuff失败: " + e.getMessage());
        }
        return profile;
    }

    private int readInt(ByteBuffer buffer) {
        ensureRemaining(buffer, 4);
        return buffer.getInt();
    }

    private long readLong(ByteBuffer buffer) {
        ensureRemaining(buffer, 8);
        return buffer.getLong();
    }

    private String readString(ByteBuffer buffer) {
        ensureRemaining(buffer, 2);
        int length = buffer.getShort() & 0xFFFF;
        if (length == 0) return "";
        if (length > 1048576) throw new IllegalStateException("字符串长度异常: " + length);
        ensureRemaining(buffer, length);
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        try {
            return new String(bytes, "UTF-8");
        } catch (Throwable ignored) {
            return new String(bytes);
        }
    }

    private void ensureRemaining(ByteBuffer buffer, int need) {
        if (buffer.remaining() < need) {
            throw new IllegalStateException("lvbuff长度不足");
        }
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
            if (count < 0 || position + count > data.length) {
                throw new IllegalStateException("protobuf长度不足");
            }
        }
    }

    private static final class RoomDataMember {
        String username = "";
        String displayName; // null: field absent; "": explicitly present with no room nickname.
    }

    private static final class LvContactProfile {
        int gender;
        Integer chatRoomNotify;
        String province = "";
        String city = "";
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatContactApi] " + message);
    }
}
