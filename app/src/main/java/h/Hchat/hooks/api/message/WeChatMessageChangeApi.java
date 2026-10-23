package h.Hchat.hooks.api.message;

import android.content.ContentValues;
import android.text.TextUtils;

import h.Hchat.hooks.api.contact.WeChatAccountApi;
import h.Hchat.hooks.api.model.DatabaseChange;
import h.Hchat.hooks.api.model.WeChatMessage;
import h.Hchat.hooks.api.runtime.WeChatDatabaseListenerApi;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * message 表变更监听 API。
 */
public final class WeChatMessageChangeApi {
    private static final String[] CORE_FIELDS = {"isSend", "type", "talker", "content", "createTime", "msgSvrId"};
    private static final java.util.regex.Pattern MSG_ID_EQUALS = java.util.regex.Pattern.compile(
            "(?i)(?<![a-z0-9_])(?:\"msgid\"|`msgid`|\\[msgid\\]|msgid)\\s*=\\s*(\\?|[0-9]+)(?=\\s|\\)|$)");
    public interface Logger {
        void log(String message);
    }

    public interface Listener {
        void onMessageChanged(MessageChange change);
    }

    public interface Requirement {
        boolean needsMessage(DatabaseChange change);
    }

    private static final class Consumer {
        final Requirement requirement;
        final Listener listener;
        Consumer(Requirement requirement, Listener listener) {
            this.requirement = requirement;
            this.listener = listener;
        }
    }

    public static final class MessageChange {
        public final DatabaseChange databaseChange;
        public final WeChatMessage message;

        private MessageChange(DatabaseChange databaseChange, WeChatMessage message) {
            this.databaseChange = databaseChange;
            this.message = message;
        }

        public String operation() {
            return databaseChange != null ? databaseChange.operation : "";
        }

        public boolean isInsert() {
            return databaseChange != null && databaseChange.isInsert();
        }

        public boolean isUpdate() {
            return databaseChange != null && databaseChange.isUpdate();
        }

        public boolean isDelete() {
            return databaseChange != null && databaseChange.isDelete();
        }
    }

    public final class Subscription {
        private final Consumer consumer;
        private volatile boolean active = true;

        private Subscription(Consumer consumer) {
            this.consumer = consumer;
        }

        public void unsubscribe() {
            if (!active) return;
            active = false;
            listeners.remove(consumer);
        }

        public boolean isActive() {
            return active;
        }
    }

    private final WeChatDatabaseListenerApi databaseListenerApi;
    private final WeChatMessageStoreApi messageStoreApi;
    private final WeChatAccountApi accountApi;
    private final Logger logger;
    private final CopyOnWriteArrayList<Consumer> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean installed;

    public WeChatMessageChangeApi(WeChatDatabaseListenerApi databaseListenerApi,
                                  WeChatMessageStoreApi messageStoreApi,
                                  Logger logger) {
        this(databaseListenerApi, messageStoreApi, null, logger);
    }

    public WeChatMessageChangeApi(WeChatDatabaseListenerApi databaseListenerApi,
                                  WeChatMessageStoreApi messageStoreApi,
                                  WeChatAccountApi accountApi,
                                  Logger logger) {
        this.databaseListenerApi = databaseListenerApi;
        this.messageStoreApi = messageStoreApi;
        this.accountApi = accountApi;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return databaseListenerApi != null && databaseListenerApi.isAvailable();
    }

    public boolean isInstalled() {
        return installed;
    }

    public Subscription subscribe(Listener listener) {
        return subscribe(change -> true, listener);
    }

    /** Select interest from the immutable mutation before any row lookup/parsing. */
    public Subscription subscribe(Requirement requirement, Listener listener) {
        if (requirement == null || listener == null) return null;
        Consumer consumer = new Consumer(requirement, listener);
        listeners.add(consumer);
        return new Subscription(consumer);
    }

    public void unsubscribe(Listener listener) {
        listeners.removeIf(consumer -> consumer.listener == listener);
    }

    public synchronized void install() {
        if (installed || databaseListenerApi == null) return;
        databaseListenerApi.install();
        if (!databaseListenerApi.isOperational()) {
            log("消息变更监听未就绪: databaseHooks="
                    + databaseListenerApi.hookedMethodCount()
                    + " wrapperHooks="
                    + databaseListenerApi.hookedWrapperMutationMethodCount()
                    + " wrapperInserts="
                    + databaseListenerApi.hookedWrapperInsertMethodCount());
            return;
        }
        databaseListenerApi.subscribe(this::onDatabaseChanged);
        installed = true;
        log("消息变更监听已安装");
    }

    private void onDatabaseChanged(DatabaseChange change) {
        if (change == null || listeners.isEmpty() || !isMessageTable(change.table)) return;
        java.util.ArrayList<Consumer> interested = new java.util.ArrayList<>();
        for (Consumer consumer : listeners) {
            try {
                if (consumer.requirement.needsMessage(change)) interested.add(consumer);
            } catch (Throwable e) { log("消息需求回调失败: " + e.getMessage()); }
        }
        if (interested.isEmpty()) return;
        // Resolve on the mutation thread once, before a later write can change the row version.
        WeChatMessage message = resolveMessage(change);
        MessageChange event = new MessageChange(change, message);
        for (Consumer consumer : interested) {
            try { consumer.listener.onMessageChanged(event); }
            catch (Throwable e) { log("消息变更回调失败: " + e.getMessage()); }
        }
    }

    private boolean isMessageTable(String table) {
        if (TextUtils.isEmpty(table)) return false;
        String lower = table.toLowerCase(java.util.Locale.US);
        return "message".equals(lower)
                || lower.startsWith("message_")
                || lower.endsWith("_message");
    }

    private WeChatMessage resolveMessage(DatabaseChange change) {
        long msgId = resolveMsgId(change);
        if (change.isDelete()) return null;
        boolean complete = change.isInsert() && hasCoreFields(change.values);
        if (!complete && msgId > 0 && messageStoreApi != null) {
            WeChatMessage stored = messageStoreApi.getMessageById(change.table, msgId);
            if (stored != null) return stored;
        }
        if (!complete) return null;
        return new WeChatMessage(
                msgId,
                longValue(change.values, "msgSvrId"),
                intValue(change.values, "type"),
                intValue(change.values, "status"),
                intValue(change.values, "isSend"),
                longValue(change.values, "createTime"),
                str(change.values, "talker"),
                str(change.values, "content"),
                str(change.values, "imgPath"),
                str(change.values, "reserved"),
                str(change.values, "transContent"),
                intValue(change.values, "flag"),
                str(change.values, "msgSource"),
                selfWxId());
    }

    private static boolean hasCoreFields(ContentValues values) {
        if (values == null) return false;
        for (String key : CORE_FIELDS) {
            if (!values.containsKey(key) || values.get(key) == null) return false;
        }
        return true;
    }

    private String selfWxId() {
        return accountApi != null ? accountApi.selfWxId() : "";
    }

    public static long resolveMsgId(DatabaseChange change) {
        if (change == null) return 0L;
        long msgId = longValue(change.values, "msgId");
        if (msgId > 0) return msgId;
        msgId = longValue(change.values, "msgid");
        if (msgId > 0) return msgId;
        msgId = longValue(change.values, "_id");
        if (msgId > 0) return msgId;
        msgId = longValue(change.values, "rowid");
        if (msgId > 0) return msgId;
        if (change.isInsert() && change.result > 0) return change.result;
        return msgIdFromWhere(change.whereClause, change.whereArgs);
    }

    private static long msgIdFromWhere(String whereClause, String[] whereArgs) {
        if (TextUtils.isEmpty(whereClause)) return 0L;
        java.util.regex.Matcher match = MSG_ID_EQUALS.matcher(whereClause);
        if (!match.find()) return 0L;
        int start = match.start();
        String value = match.group(1);
        if (match.find()) return 0L; // A multi-row mutation cannot be represented as one complete message.
        if (!"?".equals(value)) {
            try { return Long.parseLong(value); }
            catch (NumberFormatException ignored) { return 0L; }
        }
        if (whereArgs == null) return 0L;
        int index = 0;
        for (int i = 0; i < start; i++) if (whereClause.charAt(i) == '?') index++;
        if (index >= whereArgs.length) return 0L;
        try { return Math.max(0L, Long.parseLong(whereArgs[index])); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private static String str(ContentValues values, String key) {
        if (values == null || TextUtils.isEmpty(key) || !values.containsKey(key)) return "";
        Object value = values.get(key);
        return value != null ? String.valueOf(value) : "";
    }

    private static int intValue(ContentValues values, String key) {
        Long value = longBox(values, key);
        return value != null ? value.intValue() : 0;
    }

    private static long longValue(ContentValues values, String key) {
        Long value = longBox(values, key);
        return value != null ? value : 0L;
    }

    private static Long longBox(ContentValues values, String key) {
        if (values == null || TextUtils.isEmpty(key) || !values.containsKey(key)) return null;
        try {
            return values.getAsLong(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatMessageChangeApi] " + message);
    }
}
