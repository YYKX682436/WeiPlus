const fs=require('node:fs'),os=require('node:os'),path=require('node:path');const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../../..'),out=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-transfer-audio-'));
const production=fs.readFileSync(path.join(root,'app/src/main/java/h/Hchat/hooks/items/payment/transfer/TransferSuccessFeedback.kt'),'utf8');
const start=production.indexOf('    private fun sendReplySteps('),end=production.indexOf('    private fun sendStep(',start);
if(start<0||end<0)throw new Error('missing production transfer scheduling code');
const body=production.slice(start,end);
const files={
'core.kt':`package h.Hchat.hooks.items.payment.core
class RedPacketReplyStep(val mode:Int,val content:String){fun nextDelayMillis():Long=0}
object RedPacketRuleConfig{const val REPLY_TEXT=1;const val REPLY_VOICE=4;const val REPLY_FAVORITE=9}`,
'api.kt':`package h.Hchat.hooks.api.core
object WeChatApis{var favoriteVoice:Boolean?=false;fun media():Media?=Media();class Media{fun favorites():Favorite?=Favorite()};class Favorite{fun requiresSilk(s:String):Boolean?=favoriteVoice}}`,
'message.kt':`package h.Hchat.hooks.api.message
class WeChatMessageObserveApi{class ObservedMessage}`,
'silk.kt':`package h.Hchat.loader.utils
object SilkLibrary{var ready=false;val waiters=mutableListOf<(Boolean)->Unit>();fun isReady()=ready;fun whenReady(f:(Boolean)->Unit){waiters.add(f)};fun finish(value:Boolean){ready=value;val x=waiters.toList();waiters.clear();x.forEach{it(value)}}}`,
'harness.kt':`package h.Hchat.hooks.items.payment.transfer
import h.Hchat.hooks.items.payment.core.*
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.loader.utils.SilkLibrary
class TransferMessageInfo
class Handler{val tasks=ArrayDeque<()->Unit>();fun postDelayed(f:()->Unit,d:Long){tasks.add(f)};fun post(f:()->Unit){tasks.add(f)};fun run(){check(tasks.isNotEmpty());tasks.removeFirst()()}}
class Harness{
 val mainHandler=Handler();var closed=false;val sent=mutableListOf<String>();val errors=mutableListOf<String>()
 fun logger(message:String,error:Throwable?){errors.add(message)}
 fun pick(value:String)=value
 fun start(steps:List<RedPacketReplyStep>){sendReplySteps("friend","sender",h.Hchat.hooks.api.message.WeChatMessageObserveApi.ObservedMessage(),TransferMessageInfo(),steps,0,0)}
 fun stop(){closed=true;mainHandler.tasks.clear()}
 private fun sendStep(talker:String,sender:String,message:h.Hchat.hooks.api.message.WeChatMessageObserveApi.ObservedMessage,info:TransferMessageInfo,step:RedPacketReplyStep,receivedAt:Long,selected:String):Boolean{sent.add("\u0024{step.mode}:\u0024selected");return true}
 ${body}
}
fun main(){
 fun reset(){SilkLibrary.ready=false;SilkLibrary.waiters.clear();WeChatApis.favoriteVoice=false}
 reset();var h=Harness();h.start(listOf(RedPacketReplyStep(4,"v"),RedPacketReplyStep(1,"t")));h.mainHandler.run()
 check(h.sent.isEmpty() && SilkLibrary.waiters.size==1);SilkLibrary.finish(true);check(h.sent.isEmpty());h.mainHandler.run();check(h.sent==listOf("4:v"));h.mainHandler.run();check(h.sent==listOf("4:v","1:t"))
 reset();h=Harness();h.start(listOf(RedPacketReplyStep(4,"v")));h.mainHandler.run();h.stop();SilkLibrary.finish(true);h.mainHandler.run();check(h.sent.isEmpty())
 reset();h=Harness();h.start(listOf(RedPacketReplyStep(4,"v"),RedPacketReplyStep(1,"t")));h.mainHandler.run();SilkLibrary.finish(false);h.mainHandler.run();check(h.sent.isEmpty()&&h.errors.isNotEmpty());h.mainHandler.run();check(h.sent==listOf("1:t"))
 reset();WeChatApis.favoriteVoice=true;h=Harness();h.start(listOf(RedPacketReplyStep(9,"f")));h.mainHandler.run();check(h.sent.isEmpty()&&SilkLibrary.waiters.size==1);SilkLibrary.finish(true);h.mainHandler.run();check(h.sent==listOf("9:f"))
 reset();WeChatApis.favoriteVoice=false;h=Harness();h.start(listOf(RedPacketReplyStep(9,"f")));h.mainHandler.run();check(h.sent==listOf("9:f")&&SilkLibrary.waiters.isEmpty())
 reset();WeChatApis.favoriteVoice=null;h=Harness();h.start(listOf(RedPacketReplyStep(9,"missing")));h.mainHandler.run();check(h.sent.isEmpty()&&h.errors.isNotEmpty()&&SilkLibrary.waiters.isEmpty())
 println("PASS: transfer voice readiness preserves ordering, shutdown, failure and voice-only favorite gating")
}`
};
function jar(g,n,v){const dir=path.join(os.homedir(),'.gradle/caches/modules-2/files-2.1',g,n,v);for(const h of fs.readdirSync(dir)){const f=path.join(dir,h,`${n}-${v}.jar`);if(fs.existsSync(f))return f;}throw Error(`missing ${n}`)}
try{const paths=[];for(const [n,s] of Object.entries(files)){const f=path.join(out,n);fs.writeFileSync(f,s);paths.push(f)}
 const std=jar('org.jetbrains.kotlin','kotlin-stdlib','2.4.20'),compiler=[jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.4.20'),std,jar('org.jetbrains.kotlin','kotlin-reflect','2.4.0'),jar('org.jetbrains.kotlin','kotlin-script-runtime','2.4.20'),jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),jar('org.jetbrains','annotations','13.0')];
 for(const [cmd,args] of [['java',['-cp',compiler.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',std,'-d',out,...paths]],['java',['-cp',[out,std].join(path.delimiter),'h.Hchat.hooks.items.payment.transfer.HarnessKt']]]){const r=spawnSync(cmd,args,{cwd:root,stdio:'inherit'});if(r.error||r.status!==0)throw r.error||Error(`${cmd}: ${r.status}`)}
}finally{fs.rmSync(out,{recursive:true,force:true})}
