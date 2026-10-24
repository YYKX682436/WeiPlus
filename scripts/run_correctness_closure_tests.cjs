// Execute the production config listener, hook, background/padding/text restore, and shared group runtime.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),lib=process.argv[2]||process.env.KOTLIN_LIB_DIR;
if(!lib)throw Error('Pass Kotlin compiler library directory');
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-closure-tests-'));
process.once('exit',()=>fs.rmSync(tmp,{recursive:true,force:true}));
const jars=fs.readdirSync(lib).filter(x=>x.endsWith('.jar')).map(x=>path.join(lib,x));
const stdlib=jars.find(x=>/kotlin-stdlib-[\d.]+\.jar$/.test(x));
const src='app/src/main/java/h/Hchat/',test='scripts/tests/performance_round1/';
function block(input,anchor,start=0){const at=input.indexOf(anchor,start);if(at<0)throw Error('Missing '+anchor);const begin=input.indexOf('{',at);let n=1,end=begin+1;while(n){if(end>=input.length)throw Error('Unbalanced '+anchor);if(input[end]==='{')n++;if(input[end]==='}')n--;end++;}return input.slice(at,end);}
function compile(name,files){const jar=path.join(tmp,name+'.jar');const r=spawnSync('java',['-Xmx384m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',stdlib,'-d',jar,...files],{cwd:root,encoding:'utf8'});if(r.status!==0)throw Error(r.stdout+r.stderr);return [jar,stdlib].join(path.delimiter);}
const source=fs.readFileSync(path.join(root,src+'hooks/items/messagebubble/MessageBubbleFeature.kt'),'utf8');
const replacements={
 CONFIG:source.slice(source.indexOf('private val boundRoots'),source.indexOf('private val bubbleViewIds')),
 DATA:source.slice(source.indexOf('private data class OriginalState'),source.indexOf('private data class DrawableChoice')),
 CAPTURE:block(source,'private fun captureBindState'),RESTORE_BOUND:block(source,'private fun restoreBoundBubble'),
 RESTORE_ORIGINAL:block(source,'private fun restoreOriginal'),RESTORE_TEXT:block(source,'private fun restoreTextColor'),
 APPLY_TEXT:block(source,'private fun applyTextContrast'),SET_TEXT:block(source,'private fun setTargetTextColor'),
 APPLY_DRAWABLE:block(source,'private fun applyDrawable'),HOOK:block(source,'object : XC_MethodHook()',source.indexOf('fun install()')).replace('object : XC_MethodHook()','')};
let bubble=fs.readFileSync(path.join(root,test+'BubbleClosure.kt.in'),'utf8');
for(const [key,value] of Object.entries(replacements))bubble=bubble.replace('@'+key+'@',value);
const bf=path.join(tmp,'BubbleClosure.kt');fs.writeFileSync(bf,bubble);const bcp=compile('bubble',[bf]);
const rename=fs.readFileSync(path.join(root,src+'hooks/items/grouprename/GroupRenameMonitorRuntime.kt'),'utf8');
const rf=path.join(tmp,'RenameHarness.kt');fs.writeFileSync(rf,'package h.Hchat.hooks.items.groupmonitor\ndata class MemberRename(val memberId:String,val oldName:String,val newName:String)\nclass RenameHarness { '+block(rename,'private fun renamedMembers').replace('private fun renamedMembers','fun compare')+' }');
const gf=path.join(tmp,'ClosureRegression.kt');fs.copyFileSync(path.join(root,test+'group_runtime/ClosureRegression.kt.in'),gf);
const gcp=compile('groups',[src+'hooks/items/groupmonitor/GroupSnapshotState.kt',src+'hooks/items/groupmonitor/GroupMonitorSnapshots.kt',rf,gf,...fs.readdirSync(path.join(root,test+'group_runtime')).filter(x=>x.endsWith('.kt')&&x!=='RuntimeRegression.kt').map(x=>test+'group_runtime/'+x)]);
const contact=fs.readFileSync(path.join(root,src+'hooks/api/contact/WeChatContactApi.java'),'utf8');
const raw=path.join(tmp,'RawNamesRegression.java');
fs.writeFileSync(raw,`import java.util.*; import java.nio.charset.StandardCharsets;
public class RawNamesRegression {
static class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
void log(String s){}
`+block(contact,'private Map<String, String> getRoomDataMemberDisplayNames')+'\n'+block(contact,'private RoomDataMember parseRoomDataMember')+'\n'+block(contact,'private static final class ProtoReader')+'\n'+block(contact,'private static final class RoomDataMember')+`
static byte[] bytes(int... a){byte[] b=new byte[a.length];for(int i=0;i<a.length;i++)b[i]=(byte)a[i];return b;}
public static void main(String[] args){RawNamesRegression r=new RawNamesRegression();
Map<String,String> unknown=r.getRoomDataMemberDisplayNames(bytes(10,3,10,1,66));
if(unknown.containsKey("B"))throw new AssertionError("Missing protobuf nickname must stay unknown");
Map<String,String> empty=r.getRoomDataMemberDisplayNames(bytes(10,5,10,1,66,18,0));
if(!empty.containsKey("B")||!"".equals(empty.get("B")))throw new AssertionError("Explicit empty nickname must be reliable");
System.out.println("PASS: production protobuf reader preserves missing versus explicitly empty nickname fields");}}
`);
const javac=spawnSync('javac',['-d',tmp,raw],{encoding:'utf8'});if(javac.status!==0)throw Error(javac.stderr);
let failed=0;
const rawResult=spawnSync('java',['-cp',tmp,'RawNamesRegression'],{encoding:'utf8'});process.stdout.write(rawResult.stdout||'');if(rawResult.status!==0){failed++;process.stderr.write('FAIL raw nickname semantics\n'+rawResult.stderr);}

for(const [cp,main,cases] of [[bcp,'BubbleClosureKt',['queue','worker','rapid','worker-rapid','equal-text','text-owner','page']],[gcp,'h.Hchat.hooks.items.groupmonitor.ClosureRegressionKt',['partial','unknown','retry','mixed','recovery','groups','all-groups','empty-directory','fault','live','close','shrink','account']]]){
 for(const name of cases){const r=spawnSync('java',['-cp',cp,main,name],{cwd:root,encoding:'utf8',timeout:15000});process.stdout.write(r.stdout||'');if(r.status!==0){failed++;process.stderr.write('FAIL '+main+' '+name+'\n'+r.stderr);}}
}
process.exitCode=failed?1:0;

const stats=fs.readFileSync(path.join(root,src+'hooks/items/inputhint/OutgoingMessageStatsRepository.kt'),'utf8');
require('node:assert/strict').match(stats,/val table = change\.table\s+executor\.execute \{ applyMessageChange\(msgId, table\) \}/);
