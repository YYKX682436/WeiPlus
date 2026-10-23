// Execute production snapshot state, binding index, summary policy and message APIs on the JVM.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const lib = process.argv[2] || process.env.KOTLIN_LIB_DIR;
if (!lib) throw new Error('Pass Kotlin compiler library directory');
const root = path.resolve(__dirname, '..');
const assert = require('node:assert/strict');
const finderSource = fs.readFileSync(path.join(root,'app/src/main/java/h/Hchat/dexkit/DexFinder.java'),'utf8');
const summaryLocator = finderSource.slice(finderSource.indexOf('public synchronized void resolveMsgDescTextApi()'),finderSource.indexOf('private boolean isMsgDescTextMethod('));
assert(summaryLocator.indexOf('DexMethodCache.INSTANCE.loadList') >= 0 && summaryLocator.indexOf('DexMethodCache.INSTANCE.loadList') < summaryLocator.indexOf('dexKit.findMethod'));
assert(summaryLocator.includes('DexMethodCache.INSTANCE.saveList'));
assert(summaryLocator.includes('summary_v" + MSG_DESC_LOCATOR_VERSION'));

const output = fs.mkdtempSync(path.join(os.tmpdir(), 'weiplus-perf-r1-tests-'));
process.once('exit', () => fs.rmSync(output, { recursive: true, force: true }));
const jars = fs.readdirSync(lib).filter(x => x.endsWith('.jar')).map(x => path.join(lib, x));
const stdlib = jars.find(x => /kotlin-stdlib-[\d.]+\.jar$/.test(x));
const source = 'app/src/main/java/h/Hchat/';
const tests = 'scripts/tests/performance_round1/';
function run(command, args) {
  const r = spawnSync(command, args, { cwd: root, encoding: 'utf8', timeout: 90000 });
  process.stdout.write(r.stdout || ''); process.stderr.write(r.stderr || '');
  if (r.error || r.status !== 0) throw r.error || Error(command + ' failed: ' + r.status);
}
const fixtures = {
 'de/robv/android/xposed/XC_MethodHook.java': `package de.robv.android.xposed;
 public class XC_MethodHook {
  public static class MethodHookParam { private Object result; private Throwable failure;
   public Object getResult(){return result;}public Throwable getThrowable(){return failure;}
   public void setResult(Object v){result=v;failure=null;}public void setThrowable(Throwable v){failure=v;result=null;}}
  protected void beforeHookedMethod(MethodHookParam p)throws Throwable{}protected void afterHookedMethod(MethodHookParam p)throws Throwable{}
  public void before(MethodHookParam p)throws Throwable{beforeHookedMethod(p);}public void after(MethodHookParam p)throws Throwable{afterHookedMethod(p);}}
 `,
'android/content/ContentValues.java': `package android.content; import java.util.*; public class ContentValues extends HashMap<String,Object> {
 public ContentValues() {} public ContentValues(ContentValues other) {super(other);} public Long getAsLong(String k){Object v=get(k);return v==null?null:Long.valueOf(v.toString());}
 public Integer getAsInteger(String k){Long v=getAsLong(k);return v==null?null:v.intValue();} }`,
'android/database/Cursor.java': `package android.database; public interface Cursor {int getColumnIndex(String s);boolean moveToNext();long getLong(int i);int getInt(int i);String getString(int i);void close();}`,
'h/Hchat/event/Events.java': `package h.Hchat.event; public class Events {
 public static class MessageReceived {public String xml="",sender="",talker="",content="",msgType="1",msgSource="",source="";public long createTimeSeconds,msgSvrId; public boolean outgoing;}
 public static class RedPacketDetected {public String talker,sender,xml,nativeUrl;}
 public static class PatDetected {public String talker,fromUser,pattedUser,template;public long createTime,svrId;}}
`,
'h/Hchat/event/EventBus.java': `package h.Hchat.event; public class EventBus {}`,
'h/Hchat/hooks/api/contact/WeChatAccountApi.java': `package h.Hchat.hooks.api.contact; public class WeChatAccountApi {public String selfWxId(){return "self";}}`,
'h/Hchat/hooks/api/runtime/WeChatDatabaseListenerApi.java': `package h.Hchat.hooks.api.runtime; import h.Hchat.hooks.api.model.DatabaseChange; import java.util.function.Consumer; public class WeChatDatabaseListenerApi {public Consumer<DatabaseChange> listener; public boolean isAvailable(){return true;}public boolean isOperational(){return true;}public void install(){}public int hookedMethodCount(){return 1;}public int hookedWrapperMutationMethodCount(){return 1;}public int hookedWrapperInsertMethodCount(){return 1;} public void subscribe(Consumer<DatabaseChange> l){listener=l;}}`,
'h/Hchat/hooks/api/runtime/WeChatDatabaseApi.java': `package h.Hchat.hooks.api.runtime; import java.util.*; import android.database.Cursor; public class WeChatDatabaseApi {
 public final List<String> queries=new ArrayList<>();public Map<String,Object> row=new HashMap<>();public boolean isAvailable(){return true;}public List<Map<String,Object>> query(String sql,String[] args){queries.add(sql);return row.isEmpty()?new ArrayList<>():List.of(new HashMap<>(row));}
 public String quoteTable(String t){return t.matches("[a-zA-Z0-9_]+")?'"'+t+'"':"";} public List<String> messageTables(){throw new AssertionError("cross-table probe");}public String messageTableForTalker(String t){return "message";}public String queryFirstString(String s,String[] a,String c){return "";}public Cursor rawQuery(String s,String[] a){return null;}}`,
'h/Hchat/dexkit/DexFinder.java': `package h.Hchat.dexkit; public class DexFinder {public java.lang.reflect.Method messageClearBatchMethod;}`,
'h/Hchat/hooks/api/message/WeChatMessageApi.java': `package h.Hchat.hooks.api.message;public class WeChatMessageApi {public static void cancelPendingLocalSend(String t,String c){}}`,
'h/Hchat/hooks/api/message/WeChatMessageEventApi.java': `package h.Hchat.hooks.api.message; import h.Hchat.event.Events; import java.util.function.Consumer; public class WeChatMessageEventApi {
 public boolean operational; public Consumer<Events.MessageReceived> messages; public boolean isAvailable(){return true;} public boolean isOperational(){return operational;} public void subscribeMessage(Consumer<Events.MessageReceived> c){messages=c;} public void subscribePat(Consumer<Events.PatDetected> c){} }`,
'h/Hchat/hooks/api/message/WeChatMessageParseApi.java': `package h.Hchat.hooks.api.message; public class WeChatMessageParseApi {public String getXmlParamByTag(String s,String t){return "";}public String extractXml(String s){return s;}public boolean containsRedPacketMarker(String s){return false;}public boolean isGroupTalker(String s){return s.endsWith("@chatroom");}}`,
'h/Hchat/utils/KavaReflector.java': `package h.Hchat.utils;public class KavaReflector { public static Object invokeOrThrow(java.lang.reflect.Method m,Object o,Object... a)throws Throwable{return m.invoke(o,a);} }`
};
for (const [file, data] of Object.entries(fixtures)) { const p=path.join(output,file);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,data); }
fs.mkdirSync(path.join(output, 'android/text'), {recursive:true});
fs.copyFileSync(path.join(root,'scripts/tests/message_parser/TextUtils.java'), path.join(output,'android/text/TextUtils.java'));
run(process.env.JAVAC || 'javac', ['-d', output, ...['android/content/ContentValues.java','android/text/TextUtils.java','android/database/Cursor.java'].map(x=>path.join(output,x))]);
function kotlinHook(file, anchor) {
 const input=fs.readFileSync(path.join(root,source+file),'utf8');
 const start=input.indexOf('object : XC_MethodHook()',input.indexOf(anchor));
 const begin=input.indexOf('{',start);let depth=1,end=begin+1;
 while(depth){if(input[end]==='{')depth++;if(input[end]==='}')depth--;end++;}
 return input.slice(begin,end);
}
const rowHooks=path.join(output,'RowHookRegression.kt');
fs.writeFileSync(rowHooks,fs.readFileSync(path.join(root,tests+'RowHookRegression.kt.in'),'utf8')
 .replace('@DETAILS_HOOK@',kotlinHook('hooks/items/hchatextra/HchatExtraFeature.kt','fun installMessageDetails()'))
 .replace('@BUBBLE_HOOK@',kotlinHook('hooks/items/messagebubble/MessageBubbleFeature.kt','fun install()')));
const jar=path.join(output,'production.jar');
run(process.env.JAVA || 'java', ['-Xmx384m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
 '-no-stdlib','-no-reflect','-classpath',[output,stdlib].join(path.delimiter),'-d',jar,
 ...['hooks/api/model/WeChatMessage.kt','hooks/api/model/WeChatMessageTypes.kt','hooks/api/model/DatabaseChange.kt',
 'hooks/items/groupmonitor/GroupSnapshotState.kt','hooks/items/hchatextra/MessageDetailsBindingIndex.kt','hooks/items/antirecall/RecallCachePolicy.kt','hooks/core/ChatAdapterRefresh.kt'].map(x=>source+x), tests+'StateRegression.kt',tests+'AdapterRefreshRegression.kt',rowHooks]);
const entry = fs.readFileSync(path.join(root, source+'ModuleEntry.java'),'utf8');
const start = entry.indexOf('new XC_MethodHook()',entry.indexOf('private void installMsgDescTextFallback'));
const brace = entry.indexOf('{',start); let depth=1, end=brace+1;
while (depth) {if(entry[end]==='{')depth++;if(entry[end]==='}')depth--;end++;}
fs.writeFileSync(path.join(output,'ActualSummaryHook.java'),
 'import java.lang.reflect.Method; import de.robv.android.xposed.XC_MethodHook; public class ActualSummaryHook { public static XC_MethodHook create(Method m) { return '+entry.slice(start,end)+'; }}');
const cp=[output,jar,stdlib].join(path.delimiter);
run(process.env.JAVAC || 'javac', ['-cp',cp,'-d',output,...Object.keys(fixtures).map(x=>path.join(output,x)),
 path.join(output,'android/text/TextUtils.java'),source+'hooks/api/message/WeChatMessageChangeApi.java',
 source+'hooks/api/message/WeChatMessageStoreApi.java',source+'hooks/api/message/WeChatMessageObserveApi.java',
 source+'hooks/core/MsgDescFallback.java',path.join(output,'ActualSummaryHook.java'),tests+'MessageRegression.java']);
run(process.env.JAVA || 'java',['-cp',cp,'h.Hchat.hooks.items.groupmonitor.StateRegressionKt']);
run(process.env.JAVA || 'java',['-cp',cp,'MessageRegression']);
run(process.env.JAVA || 'java',['-cp',cp,'RowHookRegressionKt']);
run(process.env.JAVA || 'java',['-cp',cp,'h.Hchat.hooks.core.AdapterRefreshRegressionKt']);
const runtimeJar=path.join(output,'group-runtime.jar');
run(process.env.JAVA || 'java', ['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
 '-no-stdlib','-no-reflect','-classpath',stdlib,'-d',runtimeJar,
 source+'hooks/items/groupmonitor/GroupSnapshotState.kt',source+'hooks/items/groupmonitor/GroupMonitorSnapshots.kt',
 ...fs.readdirSync(path.join(root,tests,'group_runtime')).filter(x=>x.endsWith('.kt')).map(x=>tests+'group_runtime/'+x)]);
run(process.env.JAVA || 'java',['-cp',[runtimeJar,stdlib].join(path.delimiter),'h.Hchat.hooks.items.groupmonitor.RuntimeRegressionKt']);
const refreshJar=path.join(output,'row-refresh.jar');
run(process.env.JAVA || 'java', ['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
 '-no-stdlib','-no-reflect','-classpath',stdlib,'-d',refreshJar,
 source+'hooks/core/ChatAdapterRefresh.kt',source+'hooks/core/ChatRowRefresh.kt',
 ...fs.readdirSync(path.join(root,tests,'row_refresh')).filter(x=>x.endsWith('.kt')).map(x=>tests+'row_refresh/'+x)]);
run(process.env.JAVA || 'java',['-cp',[refreshJar,stdlib].join(path.delimiter),'h.Hchat.hooks.core.RefreshRegressionKt']);
