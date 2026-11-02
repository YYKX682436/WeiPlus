package h.Hchat.hooks.items.payment.reply;
import h.Hchat.hooks.api.core.WeChatApis;
import h.Hchat.hooks.api.runtime.WeChatTaskApi;
import h.Hchat.hooks.items.payment.core.*;
import h.Hchat.loader.utils.SilkLibrary;
import java.util.*;
public final class RedPacketAudioGateRegression {
    static void check(boolean b,String message) { if (!b) throw new AssertionError(message); }
    static RedPacketAutoReply create(RedPacketState state) {
        return new RedPacketAutoReply(new RedPacketSettings(),state,()->"self",s->{});
    }
    static RedPacketState state(String key,RedPacketReplyStep...steps) {
        RedPacketState state=new RedPacketState(); state.talkerMap.put(key,"friend");
        state.senderMap.put(key,"other"); state.ruleMap.put(key,new RedPacketEffectiveRule(Arrays.asList(steps))); return state;
    }
    static RedPacketReplyStep step(int mode,String text) { return new RedPacketReplyStep(mode,text); }
    static void reset() { SilkLibrary.reset(); WeChatApis.reset(); }
    public static void main(String[] args) throws Exception {
        reset(); String k="first";
        RedPacketAutoReply reply=create(state(k,step(RedPacketRuleConfig.REPLY_VOICE,"voice"),step(RedPacketRuleConfig.REPLY_TEXT,"next")));
        reply.replyAfterGrabbed(k,"friend","1",false);
        WeChatTaskApi tasks=WeChatApis.TASKS; tasks.runDelayed();
        check(WeChatApis.SENT.isEmpty() && SilkLibrary.waiters()==1,"voice was sent or main thread blocked before ready");
        SilkLibrary.finish(true); check(WeChatApis.SENT.isEmpty(),"audio completion ran reply off main");
        tasks.runMain(); check(WeChatApis.SENT.equals(List.of("voice:voice")),"voice not sent exactly once");
        tasks.runDelayed(); check(WeChatApis.SENT.equals(List.of("voice:voice","text:next")),"reply order changed");
        reset(); k="cancel"; reply=create(state(k,step(RedPacketRuleConfig.REPLY_VOICE,"voice")));
        reply.replyAfterGrabbed(k,"friend","1",false); tasks=WeChatApis.TASKS; tasks.runDelayed();
        reply.cancelPendingReplies(); reply.replyAfterGrabbed(k,"friend","1",false);
        SilkLibrary.finish(true); tasks.runMain(); check(WeChatApis.SENT.isEmpty(),"cancelled completion sent or consumed new task");
        tasks.runDelayed(); check(WeChatApis.SENT.equals(List.of("voice:voice")),"reopened reply lost");
        reset(); k="fail"; reply=create(state(k,step(RedPacketRuleConfig.REPLY_VOICE,"voice"),step(RedPacketRuleConfig.REPLY_TEXT,"next")));
        reply.replyAfterGrabbed(k,"friend","1",false); tasks=WeChatApis.TASKS; tasks.runDelayed(); SilkLibrary.finish(false); tasks.runMain();
        check(WeChatApis.SENT.isEmpty(),"failed load sent voice"); tasks.runDelayed();
        check(WeChatApis.SENT.equals(List.of("text:next")),"failed audio prevented later step");
        reset(); k="fav"; WeChatApis.FAVORITE_VOICE=true;
        reply=create(state(k,step(RedPacketRuleConfig.REPLY_FAVORITE,"7"))); reply.replyAfterGrabbed(k,"friend","1",false);
        tasks=WeChatApis.TASKS; tasks.runDelayed(); check(WeChatApis.SENT.isEmpty() && SilkLibrary.waiters()==1,"voice favorite bypassed audio gate");
        SilkLibrary.finish(true); tasks.runMain(); check(WeChatApis.SENT.equals(List.of("favorite:7")),"voice favorite did not resume");
        reset(); k="textfav"; WeChatApis.FAVORITE_VOICE=false;
        reply=create(state(k,step(RedPacketRuleConfig.REPLY_FAVORITE,"8"))); reply.replyAfterGrabbed(k,"friend","1",false);
        tasks=WeChatApis.TASKS; tasks.runDelayed(); check(SilkLibrary.waiters()==0 && WeChatApis.SENT.equals(List.of("favorite:8")),"non-voice favorite loaded Silk or was delayed");
        reset(); k="retry"; reply=create(state(k,step(RedPacketRuleConfig.REPLY_VOICE,"first"),step(RedPacketRuleConfig.REPLY_VOICE,"second")));
        reply.replyAfterGrabbed(k,"friend","1",false); tasks=WeChatApis.TASKS; tasks.runDelayed(); SilkLibrary.finish(false); tasks.runMain();
        tasks.runDelayed(); check(SilkLibrary.waiters()==1 && WeChatApis.SENT.isEmpty(),"next voice did not retry after failure");
        SilkLibrary.finish(true); tasks.runMain(); check(WeChatApis.SENT.equals(List.of("voice:second")),"retry lost the later voice");
        reset(); k="fallback"; WeChatApis.TASKS=null;
        reply=create(state(k,step(RedPacketRuleConfig.REPLY_VOICE,"voice"),step(RedPacketRuleConfig.REPLY_TEXT,"next")));
        reply.replyAfterGrabbed(k,"friend","1",false); check(WeChatApis.SENT.isEmpty() && SilkLibrary.waiters()==1,"fallback blocked or sent before ready");
        SilkLibrary.finish(true); check(WeChatApis.SENT.equals(List.of("voice:voice","text:next")),"fallback did not preserve sequence");
        reset(); k="inflight"; SilkLibrary.finish(true);
        reply=create(state(k,step(RedPacketRuleConfig.REPLY_VOICE,"voice"))); reply.replyAfterGrabbed(k,"friend","1",false);
        tasks=WeChatApis.TASKS; WeChatApis.ENTERED=new java.util.concurrent.CountDownLatch(1); WeChatApis.RELEASE=new java.util.concurrent.CountDownLatch(1);
        WeChatTaskApi captured=tasks; Thread sending=new Thread(captured::runDelayed); sending.start();
        check(WeChatApis.ENTERED.await(3,java.util.concurrent.TimeUnit.SECONDS),"in-flight send did not begin");
        reply.cancelPendingReplies(); reply.replyAfterGrabbed(k,"friend","1",false);
        check(tasks.delayed.isEmpty(),"duplicate reply scheduled while original was in flight");
        WeChatApis.RELEASE.countDown(); sending.join(3000); check(!sending.isAlive(),"in-flight send stalled");
        WeChatApis.ENTERED=null; reply.replyAfterGrabbed(k,"friend","1",false); tasks.runDelayed();
        check(WeChatApis.SENT.equals(List.of("voice:voice","voice:voice")),"run state stranded after cancelled in-flight reply");
        System.out.println("PASS: red-packet voice readiness preserves ordering, cancellation, failure and voice-only favorite gating");
    }
}
