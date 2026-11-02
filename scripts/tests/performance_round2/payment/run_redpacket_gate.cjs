const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../../..'); const out=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-audio-reply-'));
const stubs={
'android/text/TextUtils.java':`package android.text; public class TextUtils { public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;} }`,
'android/os/Looper.java':`package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }`,
'android/os/Handler.java':`package android.os; public class Handler { public Handler(Looper l){} public boolean post(Runnable r){r.run();return true;} }`,
'h/Hchat/loader/utils/SilkLibrary.java':`package h.Hchat.loader.utils; import java.util.*; import java.util.function.Consumer;
 public class SilkLibrary { static boolean ready; static List<Consumer<Boolean>> callbacks=new ArrayList<>();
 public static void reset(){ready=false;callbacks.clear();} public static boolean isReady(){return ready;}
 public static void prewarm(){} public static void whenReady(Consumer<Boolean> cb){callbacks.add(cb);}
 public static int waiters(){return callbacks.size();} public static void finish(boolean ok){ready=ok; List<Consumer<Boolean>> x=new ArrayList<>(callbacks);callbacks.clear();for(var c:x)c.accept(ok);} }`,
'h/Hchat/hooks/items/payment/core/RedPacketSettings.java':`package h.Hchat.hooks.items.payment.core; public class RedPacketSettings {
 public static final String KEY_REPLY_CUSTOM_ENABLE="custom",KEY_REPLY_DELAY_UNIT="unit",KEY_REPLY_DELAY_VALUE="delay",KEY_REPLY_ENABLE="enable",KEY_REPLY_GROUP_ITEMS="group",KEY_REPLY_ITEMS="items",KEY_REPLY_MEDIA_PATHS="media",KEY_REPLY_RANDOM="random",KEY_REPLY_TEMPLATES="templates",KEY_REPLY_TEXT="text",KEY_REPLY_TYPE="type";
 public boolean getBoolean(String key,boolean d){return key.equals(KEY_REPLY_ENABLE)||d;} public int getInt(String key,int d){return d;}
 public String getString(String key,String d){return d;} public boolean contains(String key){return false;} }`,
'h/Hchat/hooks/items/payment/core/RedPacketReplyStep.java':`package h.Hchat.hooks.items.payment.core; public class RedPacketReplyStep { private final int mode; private final String content;
 public RedPacketReplyStep(int mode,String content){this.mode=mode;this.content=content;} public int getMode(){return mode;} public String getContent(){return content;} public long nextDelayMillis(){return 0;} }`,
'h/Hchat/hooks/items/payment/core/RedPacketRuleConfig.java':`package h.Hchat.hooks.items.payment.core; import java.util.*; public class RedPacketRuleConfig {
 public static final int REPLY_TEXT=1,REPLY_AT_SENDER=2,REPLY_IMAGE=3,REPLY_VOICE=4,REPLY_VIDEO=5,REPLY_EMOJI=6,REPLY_FILE=7,REPLY_XML=8,REPLY_FAVORITE=9;
 public static List<RedPacketReplyStep> parseReplySteps(String s){return List.of();} public static List<RedPacketReplyStep> legacyReplySteps(int m,String c,long d,boolean r){return List.of();} }`,
'h/Hchat/hooks/items/payment/core/RedPacketEffectiveRule.java':`package h.Hchat.hooks.items.payment.core; import java.util.*; public class RedPacketEffectiveRule { final List<RedPacketReplyStep> steps;
 public RedPacketEffectiveRule(List<RedPacketReplyStep> steps){this.steps=steps;} public boolean getEnabled(){return true;} public List<RedPacketReplyStep> getReplySteps(){return steps;} public List<RedPacketReplyStep> getGroupReplySteps(){return steps;} }`,
'h/Hchat/hooks/items/payment/core/RedPacketState.java':`package h.Hchat.hooks.items.payment.core; import java.util.*; public class RedPacketState { public final Map<String,RedPacketEffectiveRule> ruleMap=new HashMap<>(); public final Map<String,String> senderMap=new HashMap<>(),talkerMap=new HashMap<>(); }`,
'h/Hchat/hooks/items/payment/notify/RedPacketTemplateFormatter.java':`package h.Hchat.hooks.items.payment.notify; import h.Hchat.hooks.items.payment.core.*; import java.util.function.Consumer; public class RedPacketTemplateFormatter {
 public RedPacketTemplateFormatter(RedPacketState a,RedPacketSettings b,Consumer<String> c){} public String resolveSenderId(String n){return "other";} public String format(String c,String a,String t,String n,String x){return c;} }`,
'h/Hchat/hooks/api/runtime/WeChatTaskApi.java':`package h.Hchat.hooks.api.runtime; import java.util.*; public class WeChatTaskApi {
 public final Map<String,Runnable> delayed=new LinkedHashMap<>(); public final Queue<Runnable> main=new ArrayDeque<>(); public final Set<String> once=new HashSet<>();
 public boolean runOnce(String key,Runnable r){if(!once.add(key))return false;r.run();return true;} public void clearRunState(String key){once.remove(key);}
 public void runOnMainDelayed(String key,long d,Runnable r){delayed.put(key,r);} public void runOnMain(Runnable r){main.add(r);} public void cancel(String key){delayed.remove(key);}
 public void runDelayed(){if(delayed.isEmpty())throw new AssertionError("no delayed task");String k=delayed.keySet().iterator().next(); delayed.remove(k).run();}
 public void runMain(){if(main.isEmpty())throw new AssertionError("no main callback");main.remove().run();} }`,
'h/Hchat/hooks/api/message/WeChatMessageApi.java':`package h.Hchat.hooks.api.message; import h.Hchat.hooks.api.core.WeChatApis; import java.util.*; public class WeChatMessageApi {
 public boolean sendText(String talker,String text){WeChatApis.SENT.add("text:"+text);return true;} public boolean sendTextWithAtList(String t,String text,List<String> a){return sendText(t,text);} public boolean sendXml(String t,String c){return true;} }`,
'h/Hchat/hooks/api/media/WeChatMediaApi.java':`package h.Hchat.hooks.api.media; import h.Hchat.hooks.api.core.WeChatApis; public class WeChatMediaApi {
 public boolean sendVoice(String t,String p){WeChatApis.SENT.add("voice:"+p);if(WeChatApis.ENTERED!=null){WeChatApis.ENTERED.countDown();try{WeChatApis.RELEASE.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}return true;} public boolean sendImage(String t,String p){return true;} public boolean sendEmoji(String t,String p){return true;} public boolean sendFile(String t,String p){return true;}
 public Video videos(){return new Video();} public Favorite favorites(){return new Favorite();} public static class Video{public boolean send(String t,String p){return true;}}
 public static class Favorite{public Boolean requiresSilk(String id){return WeChatApis.FAVORITE_VOICE;} public boolean send(String t,String id){WeChatApis.SENT.add("favorite:"+id);return true;}} }`,
'h/Hchat/hooks/api/core/WeChatApis.java':`package h.Hchat.hooks.api.core; import java.util.*; import h.Hchat.hooks.api.runtime.WeChatTaskApi; import h.Hchat.hooks.api.media.WeChatMediaApi; import h.Hchat.hooks.api.message.WeChatMessageApi;
 public class WeChatApis { public static WeChatTaskApi TASKS=new WeChatTaskApi(); public static final List<String> SENT=new ArrayList<>(); public static boolean FAVORITE_VOICE; public static java.util.concurrent.CountDownLatch ENTERED,RELEASE;
 public static void reset(){TASKS=new WeChatTaskApi();SENT.clear();FAVORITE_VOICE=false;ENTERED=null;RELEASE=null;} public static Runtime runtime(){return new Runtime();} public static class Runtime{public WeChatTaskApi tasks(){return TASKS;}}
 public static Message message(){return new Message();} public static class Message{public WeChatMessageApi sender(){return new WeChatMessageApi();}} public static WeChatMediaApi media(){return new WeChatMediaApi();} }`
};
try {
 const files=[];for(const [name,body] of Object.entries(stubs)){const f=path.join(out,name);fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,body);files.push(f);}
 for(const [cmd,args] of [
  ['javac',['-d',out,...files,'app/src/main/java/h/Hchat/hooks/items/payment/reply/RedPacketAutoReply.java','scripts/tests/performance_round2/payment/RedPacketAudioGateRegression.java']],
  ['java',['-cp',out,'h.Hchat.hooks.items.payment.reply.RedPacketAudioGateRegression']]
 ]){const r=spawnSync(cmd,args,{cwd:root,stdio:'inherit'});if(r.error||r.status!==0)throw r.error||new Error(`${cmd}: ${r.status}`);}
} finally {fs.rmSync(out,{recursive:true,force:true});}
