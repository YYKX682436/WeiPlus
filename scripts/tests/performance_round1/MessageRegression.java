import android.content.ContentValues;
import h.Hchat.hooks.api.message.*;
import h.Hchat.hooks.api.model.*;
import h.Hchat.hooks.api.runtime.*;
import h.Hchat.hooks.core.MsgDescFallback;
import java.lang.reflect.*;
import java.util.*;

public class MessageRegression {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static ContentValues fields(int outgoing, String content) {
        ContentValues v=new ContentValues();v.put("isSend",outgoing);v.put("type",1);v.put("talker","peer");
        v.put("content",content);v.put("createTime",System.currentTimeMillis());v.put("msgId",7L);v.put("msgSvrId",700L);return v;
    }
    static DatabaseChange change(String operation, ContentValues v) {
        return new DatabaseChange(operation,"message_7",null,v,"talker=? AND msgId=?",new String[]{"12345","7"},7,"update");
    }
    static class Fixture {
        WeChatDatabaseApi db=new WeChatDatabaseApi();
        WeChatDatabaseListenerApi events=new WeChatDatabaseListenerApi();
        WeChatMessageStoreApi store=new WeChatMessageStoreApi(db,null);
        WeChatMessageChangeApi changes=new WeChatMessageChangeApi(events,store,null);
        WeChatMessageEventApi pb=new WeChatMessageEventApi();
        WeChatMessageObserveApi observe;
        List<WeChatMessageObserveApi.ObservedMessage> observed=new ArrayList<>();
        Fixture(boolean operational) {
            pb.operational=operational;changes.install();
            observe=new WeChatMessageObserveApi(pb,changes,null,null,null);observe.subscribe(observed::add);observe.install();
        }
        void row(int outgoing,String content) { db.row.clear();db.row.putAll(fields(outgoing,content)); }
        void emit(String operation,ContentValues values) { events.listener.accept(change(operation,values)); }
    }
    static int calls;
    public static String summary(String mode) {
        calls++;
        if (mode.equals("npe")) throw new NullPointerException("Attempt to invoke virtual method boolean java.lang.String.isEmpty() on a null object reference");
        if (mode.equals("other")) throw new IllegalStateException("native failure");
        return mode.equals("null")?null:mode;
    }
    static Object callSummary(Method m,String mode) throws Throwable {
        try { return m.invoke(null,mode); }
        catch(InvocationTargetException e) {Throwable t=e.getCause();if(MsgDescFallback.isTargetFailure(m,t))return "";throw t;}
    }
    public static void main(String[] args) throws Throwable {
        Method summary=MessageRegression.class.getMethod("summary",String.class);
        de.robv.android.xposed.XC_MethodHook actual = ActualSummaryHook.create(summary);
        for (String mode : List.of("normal", "null", "npe", "other")) {
            var param = new de.robv.android.xposed.XC_MethodHook.MethodHookParam();
            int before = calls;
            actual.before(param);
            check(calls == before && param.getThrowable() == null && param.getResult() == null);
            try { param.setResult(summary.invoke(null, mode)); }
            catch (InvocationTargetException e) { param.setThrowable(e.getCause()); }
            Throwable originalFailure = param.getThrowable();
            actual.after(param);
            check(calls == before + 1);
            if (mode.equals("npe")) check(param.getThrowable() == null && "".equals(param.getResult()));
            if (mode.equals("other")) check(param.getThrowable() == originalFailure);
            if (mode.equals("normal")) check("normal".equals(param.getResult()));
            if (mode.equals("null")) check(param.getResult() == null);
        }
        calls = 0;
        check("normal".equals(callSummary(summary,"normal")) && calls==1);
        check(callSummary(summary,"null")==null && calls==2);
        check("".equals(callSummary(summary,"npe")) && calls==3);
        try {callSummary(summary,"other");throw new AssertionError();}catch(IllegalStateException expected){}
        NullPointerException unrelated = new NullPointerException("different native failure");
        unrelated.setStackTrace(new StackTraceElement[]{new StackTraceElement(MessageRegression.class.getName(), "summary", "fixture", 1)});
        check(!MsgDescFallback.isTargetFailure(summary, unrelated));
        check(!MsgDescFallback.isTargetFailure(summary,new NullPointerException("foreign hook")));
        check(!MsgDescFallback.isTargetFailure(summary,new InvocationTargetException(new NullPointerException())));
        System.out.println("PASS: normal/null summary executed once, own NPE recovered, other/foreign failures preserved");

        Fixture f=new Fixture(true);
        f.row(0,"incoming");f.emit("insert",new ContentValues(fields(0,"incoming")));
        check(f.db.queries.isEmpty() && f.observed.isEmpty());
        List<WeChatMessage> other=new ArrayList<>();
        f.changes.subscribe(c->other.add(c.message));
        ContentValues partial=new ContentValues();partial.put("status",2);
        f.row(0,"needed by other consumer");f.emit("update",partial);
        check(f.db.queries.size()==1 && f.observed.isEmpty() && other.get(0).content.equals("needed by other consumer"));
        check(f.db.queries.get(0).contains("FROM \"message_7\" WHERE msgId=?"));
        System.out.println("PASS: PB incoming prefilter does no lookup; another consumer still receives its complete update");

        Fixture outgoing=new Fixture(true);
        outgoing.row(1,"preserved body");outgoing.emit("update",partial);
        check(outgoing.db.queries.size()==1 && outgoing.observed.size()==1);
        check(outgoing.observed.get(0).outgoing && outgoing.observed.get(0).content.equals("preserved body"));
        outgoing.row(1,"next version");outgoing.emit("update",partial);
        check(outgoing.observed.size()==2 && outgoing.observed.get(0).content.equals("preserved body"));
        check(outgoing.observed.get(1).content.equals("next version"));
        System.out.println("PASS: missing direction/body on UPDATE is resolved at the mutation version; outgoing order retained");

        Fixture fallback=new Fixture(false);
        fallback.row(0,"fallback incoming");fallback.emit("insert",partial);
        check(fallback.db.queries.size()==1 && fallback.observed.size()==1 && !fallback.observed.get(0).outgoing);
        fallback.emit("insert",partial);
        check(fallback.observed.size()==1);
        fallback.row(0,"incoming update");fallback.emit("update",partial);
        check(fallback.observed.size()==1);
        System.out.println("PASS: located but uninstalled PB uses DB fallback; duplicate inserts and incoming updates filtered");

        Fixture insert=new Fixture(true);
        insert.emit("insert",fields(1,"complete outgoing"));
        check(insert.db.queries.isEmpty() && insert.observed.size()==1);
        final int[] callbacks={0};insert.changes.subscribe(c->{check(c.message==null);callbacks[0]++;});
        insert.emit("delete",null);check(callbacks[0]==1);
        System.out.println("PASS: complete INSERT avoids reread; DELETE retains mutation semantics without a fabricated message");

        WeChatDatabaseListenerApi raw=new WeChatDatabaseListenerApi();
        WeChatDatabaseApi db=new WeChatDatabaseApi();
        WeChatMessageChangeApi change=new WeChatMessageChangeApi(raw,new WeChatMessageStoreApi(db,null),null);
        change.subscribe(c->false,c->{throw new AssertionError();});change.install();raw.listener.accept(change("update",partial));
        check(db.queries.isEmpty());
        check(WeChatMessageChangeApi.resolveMsgId(new DatabaseChange("update", "message", null, null,
            "talker=? AND `msgId`=?", new String[]{"12345", "7"}, 1, "update")) == 7);
        check(WeChatMessageChangeApi.resolveMsgId(new DatabaseChange("update", "message", null, null,
            "[msgId]=9", null, 1, "update")) == 9);
        check(WeChatMessageChangeApi.resolveMsgId(new DatabaseChange("update", "message", null, null,
            "msgId=? OR msgId=?", new String[]{"7", "8"}, 2, "update")) == 0);
        System.out.println("PASS: inactive consumers perform no synchronous queries; exact ID binding preserves SQL argument order");
    }
}
