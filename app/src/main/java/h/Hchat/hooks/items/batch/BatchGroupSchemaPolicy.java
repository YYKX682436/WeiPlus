package h.Hchat.hooks.items.batch;

import java.util.Set;

/** Validates the live table metadata while distinguishing required and optional group columns. */
public final class BatchGroupSchemaPolicy {
    public static final class Result {
        public final boolean success;
        public final boolean hasRoomOwner;
        public final boolean hasChatroomStatus;
        public final String error;

        private Result(boolean success, boolean hasRoomOwner, boolean hasChatroomStatus, String error) {
            this.success = success;
            this.hasRoomOwner = hasRoomOwner;
            this.hasChatroomStatus = hasChatroomStatus;
            this.error = error == null ? "" : error;
        }
    }

    private BatchGroupSchemaPolicy() {}

    public static Result evaluate(boolean rcontactReadSucceeded,
                                  Set<String> rcontactColumns,
                                  boolean chatroomReadSucceeded,
                                  Set<String> chatroomColumns) {
        if (!rcontactReadSucceeded) return failed("rcontact 表结构读取失败");
        if (rcontactColumns == null) return failed("rcontact 列信息不可用");
        if (!chatroomReadSucceeded) return failed("chatroom 表结构读取失败");
        if (chatroomColumns == null) return failed("chatroom 列信息不可用");
        if (!rcontactColumns.contains("username")) return failed("rcontact 缺少必需列 username");
        if (!chatroomColumns.contains("chatroomname")) return failed("chatroom 缺少必需列 chatroomname");
        if (!chatroomColumns.contains("memberlist")) return failed("chatroom 缺少必需列 memberlist");
        return new Result(true, chatroomColumns.contains("roomowner"),
                chatroomColumns.contains("chatroomStatus"), "");
    }

    private static Result failed(String error) {
        return new Result(false, false, false, error);
    }
}
