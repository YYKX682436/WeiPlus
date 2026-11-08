package h.Hchat.hooks.api.message;

import android.database.Cursor;
import android.text.TextUtils;

import h.Hchat.hooks.api.contact.WeChatAccountApi;
import h.Hchat.hooks.api.model.WeChatMessage;
import h.Hchat.hooks.api.runtime.WeChatDatabaseApi;
import h.Hchat.dexkit.DexFinder;
import h.Hchat.utils.KavaReflector;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 微信消息存储 API。
 *
 * 49/58/66/68/72/74/76 均确认 message 表字段稳定，优先通过数据库读取，避免绑定混淆类。
 */
public final class WeChatMessageStoreApi {
    public enum ConversationClearResult { COMPLETED, NOT_SUBMITTED, UNCONFIRMED, STOPPED }
    private static final long MESSAGE_CLEAR_CONFIRMATION_TIMEOUT_MS = 30_000L;
    private static final class MessageRowsObservation {
        final Boolean messagesRemain;
        final String partitionState;
        final String mainState;
        final String reason;
        final boolean broadScan;

        MessageRowsObservation(Boolean messagesRemain, String partitionState, String mainState, String reason) {
            this(messagesRemain, partitionState, mainState, reason, false);
        }

        MessageRowsObservation(Boolean messagesRemain, String partitionState, String mainState,
                               String reason, boolean broadScan) {
            this.messagesRemain = messagesRemain;
            this.partitionState = partitionState;
            this.mainState = mainState;
            this.reason = reason;
            this.broadScan = broadScan;
        }

        String detail() {
            return "partition=" + partitionState + " main=" + mainState + " reason=" + reason;
        }
    }
    public interface Logger {
        void log(String message);
    }

    private static final String MESSAGE_COLUMNS =
            "msgId, msgSvrId, type, status, isSend, createTime, talker, content, "
                    + "imgPath, reserved, transContent, flag";

    private final WeChatDatabaseApi databaseApi;
    private final WeChatAccountApi accountApi;
    private final DexFinder dexFinder;
    private final Logger logger;

    public WeChatMessageStoreApi(WeChatDatabaseApi databaseApi, Logger logger) {
        this(databaseApi, null, null, logger);
    }

    public WeChatMessageStoreApi(WeChatDatabaseApi databaseApi,
                                 WeChatAccountApi accountApi,
                                 Logger logger) {
        this(databaseApi, accountApi, null, logger);
    }

    public WeChatMessageStoreApi(WeChatDatabaseApi databaseApi,
                                 WeChatAccountApi accountApi,
                                 DexFinder dexFinder,
                                 Logger logger) {
        this.databaseApi = databaseApi;
        this.accountApi = accountApi;
        this.dexFinder = dexFinder;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return databaseApi != null && databaseApi.isAvailable();
    }

    /**
     * 使用微信设置页同款 Stage1/Stage2 异步链路清空一个会话的本地聊天记录。
     */
    public boolean clearConversationMessages(String talker) {
        if (TextUtils.isEmpty(talker)) return false;
        List<String> talkers = new ArrayList<>();
        talkers.add(talker.trim());
        return clearConversationMessages(talkers);
    }

    /**
     * 一次提交多个会话给微信原生批量清理链路。返回值只表示提交成功，实际清理异步完成。
     */
    public boolean clearConversationMessages(List<String> talkers) {
        if (dexFinder == null || talkers == null || talkers.isEmpty()) return false;
        List<String> targets = new ArrayList<>();
        for (String talker : talkers) {
            String value = talker != null ? talker.trim() : "";
            if (!TextUtils.isEmpty(value) && !targets.contains(value)) targets.add(value);
        }
        if (targets.isEmpty()) return false;

        Method method = dexFinder.messageClearBatchMethod;
        if (method == null || method.getParameterTypes().length < 2) {
            log("原生消息清理API尚未就绪");
            return false;
        }
        Class<?> callbackType = method.getParameterTypes()[1];
        if (!callbackType.isInterface()) {
            log("原生消息清理回调类型异常: " + callbackType.getName());
            return false;
        }
        Object callback = Proxy.newProxyInstance(
                callbackType.getClassLoader(),
                new Class<?>[]{callbackType},
                (proxy, callbackMethod, args) -> {
                    if (callbackMethod.getDeclaringClass() == Object.class) {
                        String name = callbackMethod.getName();
                        if ("toString".equals(name)) return "HchatMessageClearCallback";
                        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                        if ("equals".equals(name)) return proxy == (args != null && args.length > 0 ? args[0] : null);
                    }
                    Class<?> returnType = callbackMethod.getReturnType();
                    if (returnType == boolean.class || returnType == Boolean.class) return false;
                    return defaultPrimitiveValue(returnType);
                });
        try {
            if (method.getParameterTypes().length == 3) {
                KavaReflector.invokeOrThrow(method, null, targets, callback, Long.MAX_VALUE);
            } else {
                KavaReflector.invokeOrThrow(method, null, targets, callback);
            }
            return true;
        } catch (Throwable e) {
            log("原生消息清理提交失败: " + e.getMessage() + " count=" + targets.size());
            return false;
        }
    }

    /** Clears one chat through WeChat's native asynchronous stages and confirms both stores empty. */
    public ConversationClearResult clearConversationMessagesAndWait(
            String talker, BooleanSupplier shouldStop) {
        String target = talker == null ? "" : talker.trim();
        if (target.isEmpty() || dexFinder == null) {
            logBatchDeletePhase(target, "message-clear-not-submitted", false, null, "invalid-target-or-dexfinder");
            return ConversationClearResult.NOT_SUBMITTED;
        }
        dexFinder.resolveMessageClearApi();
        Method method = dexFinder.messageClearBatchMethod;
        if (method == null || method.getParameterTypes().length < 2) {
            logBatchDeletePhase(target, "message-clear-locator-failed", false, null,
                    "batchMethod=" + (method != null ? method.toGenericString() : "null"));
            return ConversationClearResult.NOT_SUBMITTED;
        }
        Class<?> callbackType = method.getParameterTypes()[1];
        if (!callbackType.isInterface()) {
            logBatchDeletePhase(target, "message-clear-not-submitted", false, null,
                    "callbackType=" + callbackType.getName());
            return ConversationClearResult.NOT_SUBMITTED;
        }
        AtomicBoolean stageOneReceived = new AtomicBoolean();
        Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(),
                new Class<?>[]{callbackType}, (proxy, callbackMethod, args) -> {
                    if (callbackMethod.getDeclaringClass() == Object.class) {
                        switch (callbackMethod.getName()) {
                            case "toString": return "HchatMessageClearCallback";
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals": return proxy == (args != null && args.length > 0 ? args[0] : null);
                        }
                    }
                    if ("b".equals(callbackMethod.getName())
                            && callbackMethod.getParameterTypes().length == 0
                            && stageOneReceived.compareAndSet(false, true)) {
                        logBatchDeletePhase(target, "stage1-callback-received", true, null,
                                callbackMethod.toGenericString());
                    }
                    return defaultPrimitiveValue(callbackMethod.getReturnType());
                });
        try {
            if (method.getParameterTypes().length == 3) {
                KavaReflector.invokeOrThrow(method, null, java.util.Collections.singletonList(target), callback,
                        Long.MAX_VALUE);
            } else {
                KavaReflector.invokeOrThrow(method, null, java.util.Collections.singletonList(target), callback);
            }
        } catch (Throwable e) {
            logBatchDeletePhase(target, "message-clear-submit-threw", false, null,
                    method.toGenericString() + " error=" + e.getClass().getSimpleName());
            return ConversationClearResult.UNCONFIRMED;
        }
        logBatchDeletePhase(target, "message-clear-request-submitted", false, null, method.toGenericString());

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MESSAGE_CLEAR_CONFIRMATION_TIMEOUT_MS);
        try {
            while (true) {
                boolean stopped = shouldStop != null && shouldStop.getAsBoolean();
                MessageRowsObservation observation = observeConversationMessageRows(target);
                Boolean remains = observation.messagesRemain;
                boolean timedOut = System.nanoTime() >= deadline;
                MessageClearCompletionPolicy.Decision decision =
                        MessageClearCompletionPolicy.decide(remains, stopped, timedOut);
                if (decision == MessageClearCompletionPolicy.Decision.COMPLETED) {
                    logBatchDeletePhase(target, "stage2-message-tables-empty",
                            stageOneReceived.get(), false, observation.detail());
                    if (!stageOneReceived.get()) {
                        logBatchDeletePhase(target, "stage1-callback-missing-but-stage2-confirmed",
                                false, false, method.toGenericString());
                    }
                    return ConversationClearResult.COMPLETED;
                }
                if (decision == MessageClearCompletionPolicy.Decision.STOPPED) {
                    logBatchDeletePhase(target, "message-clear-stopped-unconfirmed",
                            stageOneReceived.get(), remains, "nativeRequestMayBeInFlight=true");
                    return ConversationClearResult.STOPPED;
                }
                if (decision == MessageClearCompletionPolicy.Decision.UNCONFIRMED) {
                    logBatchDeletePhase(target, "stage2-message-clear-unconfirmed",
                            stageOneReceived.get(), remains,
                            "timeoutMs=" + MESSAGE_CLEAR_CONFIRMATION_TIMEOUT_MS
                                    + " " + observation.detail());
                    return ConversationClearResult.UNCONFIRMED;
                }
                TimeUnit.MILLISECONDS.sleep(observation.broadScan ? 1_000L : 200L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logBatchDeletePhase(target, "message-clear-interrupted-unconfirmed",
                    stageOneReceived.get(), null, "nativeRequestMayBeInFlight=true");
            return ConversationClearResult.STOPPED;
        } catch (Throwable e) {
            logBatchDeletePhase(target, "stage2-message-state-read-failed",
                    stageOneReceived.get(), null, e.getClass().getSimpleName());
            return ConversationClearResult.UNCONFIRMED;
        }
    }

    private void logBatchDeletePhase(String talker, String phase, boolean stageOneReceived,
                                     Boolean messagesRemain, String detail) {
        String target = talker == null ? "" : talker;
        log("批量删除聊天核验 phase=" + phase
                + " targetHash=" + Integer.toHexString(target.hashCode())
                + " stage1Callback=" + stageOneReceived
                + " messageRows=" + (messagesRemain == null ? "unreadable" : messagesRemain ? "present" : "empty")
                + " detail=" + detail);
    }

    /** null means the message store could not be queried reliably. */
    public Boolean hasConversationMessagesState(String talker) {
        return observeConversationMessageRows(talker).messagesRemain;
    }

    private MessageRowsObservation observeConversationMessageRows(String talker) {
        String target = talker == null ? "" : talker.trim();
        if (target.isEmpty()) {
            return new MessageRowsObservation(null, "unreadable", "not-checked", "invalid-target");
        }
        if (databaseApi == null) {
            return new MessageRowsObservation(null, "unreadable", "not-checked", "database-api-null");
        }
        if (!databaseApi.isAvailable()) {
            return new MessageRowsObservation(null, "unreadable", "not-checked", "database-api-unavailable");
        }
        String partition = messageTable(target);
        if (TextUtils.isEmpty(partition)) {
            return observeAllKnownMessageTables(target, "talker-partition-unresolved");
        }
        WeChatDatabaseApi.QueryResult partitionResult = queryMessageRows(partition, target);
        if (!partitionResult.success) {
            return observeAllKnownMessageTables(target, "partition-query-failed");
        }
        if (!partitionResult.rows.isEmpty()) {
            return new MessageRowsObservation(true, "present", "not-checked", "partition-has-rows");
        }
        if ("message".equalsIgnoreCase(unquoteTable(partition))) {
            return observeAllKnownMessageTables(target, "main-table-is-talker-partition");
        }
        WeChatDatabaseApi.QueryResult mainResult = queryMessageRows(databaseApi.quoteTable("message"), target);
        if (!mainResult.success) {
            return observeAllKnownMessageTables(target, "main-table-query-failed");
        }
        if (!mainResult.rows.isEmpty()) {
            return new MessageRowsObservation(true, "empty", "present", "main-table-has-rows");
        }
        return observeAllKnownMessageTables(target, "mapped-partition-and-main-empty");
    }

    /** If WeChat's talker-to-partition mapper is unavailable, read every schema-verified message table. */
    private MessageRowsObservation observeAllKnownMessageTables(String talker, String cause) {
        LinkedHashSet<String> tables = new LinkedHashSet<>(databaseApi.messageTables());
        tables.add("message");
        List<String> quotedTables = new ArrayList<>();
        for (String table : tables) {
            String quoted = databaseApi.quoteTable(table);
            if (!TextUtils.isEmpty(quoted)) quotedTables.add(quoted);
        }
        if (quotedTables.isEmpty()) {
            return new MessageRowsObservation(null, "unreadable", "unreadable",
                    cause + ";knownMessageTables=0", true);
        }

        final int chunkSize = 128;
        for (int start = 0; start < quotedTables.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, quotedTables.size());
            StringBuilder sql = new StringBuilder();
            List<String> args = new ArrayList<>(end - start);
            for (int index = start; index < end; index++) {
                if (sql.length() > 0) sql.append(" UNION ALL ");
                sql.append("SELECT 1 FROM ").append(quotedTables.get(index)).append(" WHERE talker=?");
                args.add(talker);
            }
            sql.append(" LIMIT 1");
            WeChatDatabaseApi.QueryResult query = databaseApi.queryWithStatus(
                    sql.toString(), args.toArray(new String[0]));
            if (!query.success) {
                return new MessageRowsObservation(null, "all-known-unreadable", "included-in-scan",
                        cause + ";scanQueryFailed=" + queryFailureCode(query)
                                + ";knownMessageTables=" + quotedTables.size(), true);
            }
            if (!query.rows.isEmpty()) {
                return new MessageRowsObservation(true, "all-known-scanned", "included-in-scan",
                        cause + ";rowsRemain=true;knownMessageTables=" + quotedTables.size(), true);
            }
        }
        return new MessageRowsObservation(false, "all-known-empty", "empty",
                cause + ";knownMessageTables=" + quotedTables.size(), true);
    }

    private WeChatDatabaseApi.QueryResult queryMessageRows(String table, String talker) {
        return databaseApi.queryWithStatus(
                "SELECT 1 FROM " + table + " WHERE talker=? LIMIT 1", new String[]{talker});
    }

    private String queryFailureCode(WeChatDatabaseApi.QueryResult query) {
        String error = query != null ? query.error : "";
        if (error.contains("不可用")) return "database-unavailable";
        if (error.contains("读取失败")) return "cursor-unavailable";
        if (error.contains("列信息")) return "columns-unavailable";
        if (error.contains("异常")) return "query-exception";
        return "query-failed";
    }

    private String unquoteTable(String table) {
        if (TextUtils.isEmpty(table)) return "";
        String value = table.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '`' && last == '`') || (first == '"' && last == '"')
                    || (first == '[' && last == ']')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /** A mutation already identifies its table: never probe other tables for that event. */
    public WeChatMessage getMessageById(String table, long msgId) {
        String quoted = quoteMessageTable(table);
        if (msgId <= 0L || TextUtils.isEmpty(quoted)) return null;
        List<WeChatMessage> rows = queryMessages("SELECT * FROM " + quoted + " WHERE msgId=? LIMIT 1",
                new String[]{String.valueOf(msgId)});
        return rows.isEmpty() ? null : rows.get(0);
    }

    public WeChatMessage getMessageById(long msgId) {
        if (msgId <= 0L) return null;
        for (String table : messageTables()) {
            String quoted = quoteMessageTable(table);
            if (TextUtils.isEmpty(quoted)) continue;
            List<WeChatMessage> tableRows = queryMessages(
                    "SELECT * FROM " + quoted + " WHERE msgId=? LIMIT 1",
                    new String[]{String.valueOf(msgId)});
            if (!tableRows.isEmpty()) return tableRows.get(0);
        }
        List<WeChatMessage> rows = queryMessages(
                "SELECT " + selectColumns() + " FROM message WHERE msgId=? LIMIT 1",
                new String[]{String.valueOf(msgId)});
        return rows.isEmpty() ? null : rows.get(0);
    }

    public WeChatMessage getMessageBySvrId(String talker, long msgSvrId) {
        if (TextUtils.isEmpty(talker) || msgSvrId <= 0L) return null;
        List<WeChatMessage> rows = queryMessagesBySvrIdInTalker(talker, msgSvrId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public WeChatMessage getMessageBySvrId(long msgSvrId) {
        if (msgSvrId <= 0L) return null;
        for (String table : messageTables()) {
            String quoted = quoteMessageTable(table);
            if (TextUtils.isEmpty(quoted)) continue;
            List<WeChatMessage> tableRows = queryMessages(
                    "SELECT * FROM " + quoted + " WHERE msgSvrId=? "
                            + "ORDER BY createTime DESC, msgId DESC LIMIT 1",
                    new String[]{String.valueOf(msgSvrId)});
            if (!tableRows.isEmpty()) {
                log("按msgSvrId命中分表: id=" + msgSvrId + " table=" + table);
                return tableRows.get(0);
            }
        }
        List<WeChatMessage> rows = queryMessages(
                "SELECT * FROM message WHERE msgSvrId=? "
                        + "ORDER BY createTime DESC, msgId DESC LIMIT 1",
                new String[]{String.valueOf(msgSvrId)});
        if (!rows.isEmpty()) {
            log("按msgSvrId命中主表: id=" + msgSvrId);
            return rows.get(0);
        }
        log("按msgSvrId未命中: id=" + msgSvrId);
        return null;
    }

    public long getCreateTimeBySvrId(long msgSvrId) {
        if (msgSvrId <= 0L || databaseApi == null) return 0L;
        for (String table : messageTables()) {
            String quoted = quoteMessageTable(table);
            if (TextUtils.isEmpty(quoted)) continue;
            String tableValue = databaseApi.queryFirstString(
                    "SELECT createTime FROM " + quoted
                            + " WHERE msgSvrId=? ORDER BY createTime DESC LIMIT 1",
                    new String[]{String.valueOf(msgSvrId)},
                    "createTime");
            long tableTime = parseLong(tableValue);
            if (tableTime > 0L) return tableTime;
        }
        String value = databaseApi.queryFirstString(
                "SELECT createTime FROM message WHERE msgSvrId=? ORDER BY createTime DESC LIMIT 1",
                new String[]{String.valueOf(msgSvrId)},
                "createTime");
        return parseLong(value);
    }

    public WeChatMessage getLatestMessage(String talker) {
        List<WeChatMessage> rows = getRecentMessages(talker, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<WeChatMessage> getRecentMessages(String talker, int limit) {
        if (TextUtils.isEmpty(talker)) return new ArrayList<>();
        int safeLimit = clamp(limit, 1, 200);
        String table = messageTable(talker);
        if (!TextUtils.isEmpty(table)) {
            List<WeChatMessage> rows = queryMessages(
                    "SELECT " + selectColumns() + " FROM " + table
                            + " ORDER BY createTime DESC, msgId DESC LIMIT ?",
                    new String[]{String.valueOf(safeLimit)});
            if (!rows.isEmpty()) return rows;
        }
        return queryMessages("SELECT " + selectColumns()
                + " FROM message WHERE talker=? ORDER BY createTime DESC, msgId DESC LIMIT ?",
                new String[]{talker, String.valueOf(safeLimit)});
    }

    public List<WeChatMessage> queryHistoryMsg(String talker, int count) {
        return getRecentMessages(talker, count);
    }

    public List<WeChatMessage> queryHistoryMsg(String talker, long startTime, int count) {
        if (TextUtils.isEmpty(talker)) return new ArrayList<>();
        if (startTime <= 0L) return getRecentMessages(talker, count);
        return getMessagesAfter(talker, startTime, count);
    }

    public List<WeChatMessage> getOutgoingMessages(long startTime, long endTime) {
        List<WeChatMessage> result = getOutgoingMessagesOrNull(startTime, endTime);
        return result != null ? result : new ArrayList<>();
    }

    public List<WeChatMessage> getOutgoingMessagesOrNull(long startTime, long endTime) {
        long from = Math.min(startTime, endTime);
        long to = Math.max(startTime, endTime);
        if (from < 0L || to <= from) return new ArrayList<>();

        List<String> tables = outgoingMessageTablesOrNull();
        if (tables == null) return null;

        Map<String, WeChatMessage> unique = new LinkedHashMap<>();
        String[] args = new String[]{String.valueOf(from), String.valueOf(to)};
        for (String table : tables) {
            String quoted = quoteMessageTable(table);
            if (TextUtils.isEmpty(quoted)) continue;
            Boolean compatible = hasOutgoingMessageColumns(quoted);
            if (compatible == null) return null;
            if (!compatible) continue;
            List<WeChatMessage> rows = queryMessagesOrNull(
                    "SELECT msgId,msgSvrId,type,isSend,createTime,talker,content FROM " + quoted
                            + " WHERE isSend=1 AND createTime>=? AND createTime<?",
                    args);
            if (rows == null) return null;
            for (WeChatMessage message : rows) {
                if (message == null || !message.isOutgoing()) continue;
                String key = message.msgId > 0L
                        ? "id:" + message.msgId
                        : "row:" + message.msgSvrId + ':' + message.createTime + ':'
                        + message.talker + ':' + message.type + ':' + message.content.hashCode();
                unique.putIfAbsent(key, message);
            }
        }
        return new ArrayList<>(unique.values());
    }

    public List<WeChatMessage> getMessages(String talker, int pageIndex, int pageSize) {
        if (TextUtils.isEmpty(talker)) return new ArrayList<>();
        int safePage = Math.max(0, pageIndex);
        int safeSize = clamp(pageSize, 1, 200);
        int offset = safePage * safeSize;
        String table = messageTable(talker);
        if (!TextUtils.isEmpty(table)) {
            List<WeChatMessage> rows = queryMessages(
                    "SELECT " + selectColumns() + " FROM " + table
                            + " ORDER BY createTime DESC, msgId DESC LIMIT ? OFFSET ?",
                    new String[]{String.valueOf(safeSize), String.valueOf(offset)});
            if (!rows.isEmpty()) return rows;
        }
        return queryMessages(
                "SELECT " + selectColumns()
                        + " FROM message WHERE talker=? ORDER BY createTime DESC, msgId DESC LIMIT ? OFFSET ?",
                new String[]{talker, String.valueOf(safeSize), String.valueOf(offset)});
    }

    public List<WeChatMessage> getMessagesByType(String talker, int type, int limit) {
        if (TextUtils.isEmpty(talker)) return new ArrayList<>();
        int safeLimit = clamp(limit, 1, 200);
        String table = messageTable(talker);
        if (!TextUtils.isEmpty(table)) {
            List<WeChatMessage> rows = queryMessages(
                    "SELECT " + selectColumns() + " FROM " + table
                            + " WHERE type=? ORDER BY createTime DESC, msgId DESC LIMIT ?",
                    new String[]{String.valueOf(type), String.valueOf(safeLimit)});
            if (!rows.isEmpty()) return rows;
        }
        return queryMessages(
                "SELECT " + selectColumns()
                        + " FROM message WHERE talker=? AND type=? ORDER BY createTime DESC, msgId DESC LIMIT ?",
                new String[]{talker, String.valueOf(type), String.valueOf(safeLimit)});
    }

    public List<WeChatMessage> getMessagesAfter(String talker, long createTime, int limit) {
        if (TextUtils.isEmpty(talker)) return new ArrayList<>();
        int safeLimit = clamp(limit, 1, 200);
        String table = messageTable(talker);
        if (!TextUtils.isEmpty(table)) {
            List<WeChatMessage> rows = queryMessages(
                    "SELECT " + selectColumns() + " FROM " + table
                            + " WHERE createTime>? ORDER BY createTime ASC, msgId ASC LIMIT ?",
                    new String[]{String.valueOf(createTime), String.valueOf(safeLimit)});
            if (!rows.isEmpty()) return rows;
        }
        return queryMessages(
                "SELECT " + selectColumns()
                        + " FROM message WHERE talker=? AND createTime>? "
                        + "ORDER BY createTime ASC, msgId ASC LIMIT ?",
                new String[]{talker, String.valueOf(createTime), String.valueOf(safeLimit)});
    }

    public List<WeChatMessage> getMessagesBefore(String talker, long createTime, int limit) {
        if (TextUtils.isEmpty(talker)) return new ArrayList<>();
        int safeLimit = clamp(limit, 1, 200);
        String table = messageTable(talker);
        if (!TextUtils.isEmpty(table)) {
            List<WeChatMessage> rows = queryMessages(
                    "SELECT " + selectColumns() + " FROM " + table
                            + " WHERE createTime<? ORDER BY createTime DESC, msgId DESC LIMIT ?",
                    new String[]{String.valueOf(createTime), String.valueOf(safeLimit)});
            if (!rows.isEmpty()) return rows;
        }
        return queryMessages(
                "SELECT " + selectColumns()
                        + " FROM message WHERE talker=? AND createTime<? "
                        + "ORDER BY createTime DESC, msgId DESC LIMIT ?",
                new String[]{talker, String.valueOf(createTime), String.valueOf(safeLimit)});
    }

    public List<WeChatMessage> getMessagesBetween(String talker,
                                                  long startTime,
                                                  long endTime,
                                                  int limit) {
        if (TextUtils.isEmpty(talker)) return new ArrayList<>();
        long from = Math.min(startTime, endTime);
        long to = Math.max(startTime, endTime);
        int safeLimit = clamp(limit, 1, 500);
        String table = messageTable(talker);
        if (!TextUtils.isEmpty(table)) {
            List<WeChatMessage> rows = queryMessages(
                    "SELECT " + selectColumns() + " FROM " + table
                            + " WHERE createTime>=? AND createTime<=? "
                            + "ORDER BY createTime ASC, msgId ASC LIMIT ?",
                    new String[]{String.valueOf(from), String.valueOf(to), String.valueOf(safeLimit)});
            if (!rows.isEmpty()) return rows;
        }
        return queryMessages(
                "SELECT " + selectColumns()
                        + " FROM message WHERE talker=? AND createTime>=? AND createTime<=? "
                        + "ORDER BY createTime ASC, msgId ASC LIMIT ?",
                new String[]{talker, String.valueOf(from), String.valueOf(to), String.valueOf(safeLimit)});
    }

    public List<WeChatMessage> searchMessages(String talker, String keyword, int limit) {
        if (TextUtils.isEmpty(keyword)) return new ArrayList<>();
        int safeLimit = clamp(limit, 1, 100);
        String like = "%" + keyword + "%";
        if (TextUtils.isEmpty(talker)) {
            return queryMessages(
                    "SELECT " + selectColumns()
                            + " FROM message WHERE content LIKE ? ORDER BY createTime DESC, msgId DESC LIMIT ?",
                    new String[]{like, String.valueOf(safeLimit)});
        }
        String table = messageTable(talker);
        if (!TextUtils.isEmpty(table)) {
            List<WeChatMessage> rows = queryMessages(
                    "SELECT " + selectColumns() + " FROM " + table
                            + " WHERE content LIKE ? ORDER BY createTime DESC, msgId DESC LIMIT ?",
                    new String[]{like, String.valueOf(safeLimit)});
            if (!rows.isEmpty()) return rows;
        }
        return queryMessages(
                "SELECT " + selectColumns()
                        + " FROM message WHERE talker=? AND content LIKE ? "
                        + "ORDER BY createTime DESC, msgId DESC LIMIT ?",
                new String[]{talker, like, String.valueOf(safeLimit)});
    }

    private List<WeChatMessage> queryMessages(String sql, String[] args) {
        List<WeChatMessage> result = new ArrayList<>();
        for (Map<String, Object> row : queryRows(sql, args)) {
            result.add(toMessage(row));
        }
        return result;
    }

    private List<WeChatMessage> queryMessagesOrNull(String sql, String[] args) {
        if (databaseApi == null) return null;
        Cursor cursor = databaseApi.rawQuery(sql, args);
        if (cursor == null) return null;
        List<WeChatMessage> result = new ArrayList<>();
        try {
            int msgId = cursor.getColumnIndex("msgId");
            int msgSvrId = cursor.getColumnIndex("msgSvrId");
            int type = cursor.getColumnIndex("type");
            int isSend = cursor.getColumnIndex("isSend");
            int createTime = cursor.getColumnIndex("createTime");
            int talker = cursor.getColumnIndex("talker");
            int content = cursor.getColumnIndex("content");
            if (msgId < 0 || msgSvrId < 0 || type < 0 || isSend < 0
                    || createTime < 0 || talker < 0 || content < 0) {
                return null;
            }
            while (cursor.moveToNext()) {
                result.add(new WeChatMessage(
                        cursor.getLong(msgId),
                        cursor.getLong(msgSvrId),
                        cursor.getInt(type),
                        0,
                        cursor.getInt(isSend),
                        cursor.getLong(createTime),
                        cursor.getString(talker),
                        cursor.getString(content),
                        "",
                        "",
                        "",
                        0));
            }
            return result;
        } catch (Throwable e) {
            log("发送统计消息读取失败: " + e.getMessage());
            return null;
        } finally {
            try {
                cursor.close();
            } catch (Throwable ignored) {}
        }
    }

    private List<String> outgoingMessageTablesOrNull() {
        if (databaseApi == null) return null;
        Cursor cursor = databaseApi.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                null);
        if (cursor == null) return null;
        List<String> result = new ArrayList<>();
        try {
            int nameIndex = cursor.getColumnIndex("name");
            if (nameIndex < 0) return null;
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                if (isOutgoingMessageTableName(name)) result.add(name);
            }
            return result.isEmpty() ? null : result;
        } catch (Throwable e) {
            log("发送统计消息表读取失败: " + e.getMessage());
            return null;
        } finally {
            try {
                cursor.close();
            } catch (Throwable ignored) {}
        }
    }

    private Boolean hasOutgoingMessageColumns(String quotedTable) {
        if (databaseApi == null || TextUtils.isEmpty(quotedTable)) return null;
        Cursor cursor = databaseApi.rawQuery("PRAGMA table_info(" + quotedTable + ")", null);
        if (cursor == null) return null;
        boolean hasMsgId = false;
        boolean hasMsgSvrId = false;
        boolean hasType = false;
        boolean hasIsSend = false;
        boolean hasCreateTime = false;
        boolean hasTalker = false;
        boolean hasContent = false;
        try {
            int nameIndex = cursor.getColumnIndex("name");
            if (nameIndex < 0) return null;
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                if ("msgId".equals(name)) hasMsgId = true;
                if ("msgSvrId".equals(name)) hasMsgSvrId = true;
                if ("type".equals(name)) hasType = true;
                if ("isSend".equals(name)) hasIsSend = true;
                if ("createTime".equals(name)) hasCreateTime = true;
                if ("talker".equals(name)) hasTalker = true;
                if ("content".equals(name)) hasContent = true;
            }
            return hasMsgId && hasMsgSvrId && hasType && hasIsSend
                    && hasCreateTime && hasTalker && hasContent;
        } catch (Throwable e) {
            log("发送统计消息表结构读取失败: " + e.getMessage());
            return null;
        } finally {
            try {
                cursor.close();
            } catch (Throwable ignored) {}
        }
    }

    private boolean isOutgoingMessageTableName(String table) {
        if (TextUtils.isEmpty(table)) return false;
        String lower = table.toLowerCase(java.util.Locale.US);
        return "message".equals(lower)
                || lower.startsWith("message_")
                || lower.endsWith("_message");
    }

    private List<WeChatMessage> queryMessagesBySvrIdInTalker(String talker, long msgSvrId) {
        String table = messageTable(talker);
        if (!TextUtils.isEmpty(table)) {
            List<WeChatMessage> rows = queryMessages(
                    "SELECT * FROM " + table
                            + " WHERE talker=? AND msgSvrId=? ORDER BY createTime DESC, msgId DESC LIMIT 1",
                    new String[]{talker, String.valueOf(msgSvrId)});
            if (!rows.isEmpty()) {
                log("按talker/msgSvrId命中会话表");
                return rows;
            }
        }
        String rawTalkerTable = rawMessageTable(talker);
        for (String anyTable : messageTables()) {
            if (anyTable.equals(rawTalkerTable)) continue;
            String quoted = quoteMessageTable(anyTable);
            if (TextUtils.isEmpty(quoted)) continue;
            List<WeChatMessage> rows = queryMessages(
                    "SELECT * FROM " + quoted
                            + " WHERE talker=? AND msgSvrId=? ORDER BY createTime DESC, msgId DESC LIMIT 1",
                    new String[]{talker, String.valueOf(msgSvrId)});
            if (!rows.isEmpty()) {
                log("按talker/msgSvrId命中分表");
                return rows;
            }
        }
        return queryMessages(
                "SELECT * FROM message WHERE talker=? AND msgSvrId=? "
                        + "ORDER BY createTime DESC, msgId DESC LIMIT 1",
                new String[]{talker, String.valueOf(msgSvrId)});
    }

    private String messageTable(String talker) {
        String table = rawMessageTable(talker);
        return quoteMessageTable(table);
    }

    private String rawMessageTable(String talker) {
        if (databaseApi == null || TextUtils.isEmpty(talker)) return "";
        return databaseApi.messageTableForTalker(talker);
    }

    private String quoteMessageTable(String table) {
        if (databaseApi == null || TextUtils.isEmpty(table)) return "";
        return databaseApi.quoteTable(table);
    }

    private List<String> messageTables() {
        List<String> result = new ArrayList<>();
        if (databaseApi == null) return result;
        result.addAll(databaseApi.messageTables());
        return result;
    }

    private List<Map<String, Object>> queryRows(String sql, String[] args) {
        if (databaseApi == null) return new ArrayList<>();
        try {
            return databaseApi.query(sql, args);
        } catch (Throwable e) {
            log("消息查询失败: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private String selectColumns() {
        return MESSAGE_COLUMNS;
    }

    private WeChatMessage toMessage(Map<String, Object> row) {
        return new WeChatMessage(
                longValue(row, "msgId"),
                longValue(row, "msgSvrId"),
                intValue(row, "type"),
                intValue(row, "status"),
                intValue(row, "isSend"),
                longValue(row, "createTime"),
                str(row, "talker"),
                str(row, "content"),
                str(row, "imgPath"),
                str(row, "reserved"),
                str(row, "transContent"),
                intValue(row, "flag"),
                str(row, "msgSource"),
                selfWxId());
    }

    private String selfWxId() {
        return accountApi != null ? accountApi.selfWxId() : "";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        return value != null ? String.valueOf(value) : "";
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

    private static long longValue(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value == null) return 0L;
        return parseLong(String.valueOf(value));
    }

    private static long parseLong(String value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static Object defaultPrimitiveValue(Class<?> type) {
        if (type == null || !type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatMessageStoreApi] " + message);
    }
}
