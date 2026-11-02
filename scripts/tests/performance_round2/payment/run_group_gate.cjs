const fs=require('node:fs'),os=require('node:os'),path=require('node:path');const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../../..'),out=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-group-audio-'));
const src=fs.readFileSync(path.join(root,'app/src/main/java/h/Hchat/hooks/items/groupleave/GroupLeaveMonitorRuntime.kt'),'utf8');
const a=src.indexOf('            ) { path ->',src.indexOf('            "voice" to pathsReplyTask(')),b=src.indexOf('\n            },\n            "emoji"',a);
if(a<0||b<0)throw Error('missing production group voice action');
const body=src.slice(a+'            ) { path ->'.length,b);
const files={
'android.kt':`package android.os
object Main{val queue=java.util.concurrent.LinkedBlockingQueue<()->Unit>()}
class Handler(l:Looper){fun post(f:()->Unit){Main.queue.add(f)}}
class Looper{companion object{fun getMainLooper()=Looper()}}`,
'silk.kt':`package h.Hchat.loader.utils
object SilkLibrary{var fail=false;var entered=java.util.concurrent.CountDownLatch(1);var release=java.util.concurrent.CountDownLatch(1);fun ensureLoaded(){entered.countDown();release.await();if(fail)throw IllegalStateException("load")}}`,
'api.kt':`package h.Hchat.hooks.api.core
object WeChatApis{var sent=0;fun media():Media?=Media();class Media{fun sendVoice(group:String,path:String):Boolean{sent++;return true}}}`,
'harness.kt':`package fixture
import android.os.*
import h.Hchat.loader.utils.SilkLibrary
import h.Hchat.hooks.api.core.WeChatApis
class Snapshot{var current=true;fun current(owner:String,group:String,lease:Long)=current}
class Harness{val snapshots=Snapshot();val OWNER="owner";val groupId="group";val lease=1L;val type="type";var enabled=true;val errors=mutableListOf<String>()
 fun valid()=snapshots.current(OWNER,groupId,lease)&&replyEnabled(type,groupId)
 fun replyEnabled(type:String,group:String)=enabled
 fun logger(message:String,error:Throwable?){errors.add(message)}
 fun voice(path:String):Boolean = run { ${body} }
}
fun main(){
 var h=Harness();var worker=Thread{h.voice("voice")};worker.start();check(SilkLibrary.entered.await(3,java.util.concurrent.TimeUnit.SECONDS));check(Main.queue.isEmpty());SilkLibrary.release.countDown();worker.join(3000);check(!worker.isAlive && Main.queue.size==1);Main.queue.take()();check(WeChatApis.sent==1)
 h=Harness();SilkLibrary.entered=java.util.concurrent.CountDownLatch(1);SilkLibrary.release=java.util.concurrent.CountDownLatch(1);worker=Thread{h.voice("voice")};worker.start();check(SilkLibrary.entered.await(3,java.util.concurrent.TimeUnit.SECONDS));SilkLibrary.release.countDown();worker.join(3000);h.enabled=false;Main.queue.take()();check(WeChatApis.sent==1)
 h=Harness();SilkLibrary.entered=java.util.concurrent.CountDownLatch(1);SilkLibrary.release=java.util.concurrent.CountDownLatch(0);SilkLibrary.fail=true;worker=Thread{h.voice("voice")};worker.start();worker.join(3000);check(h.errors.isNotEmpty()&&Main.queue.isEmpty()&&WeChatApis.sent==1)
 println("PASS: group reply loads on its existing worker; disable and failure suppress stale sends")
}`
};
function jar(g,n,v){const dir=path.join(os.homedir(),'.gradle/caches/modules-2/files-2.1',g,n,v);for(const h of fs.readdirSync(dir)){const f=path.join(dir,h,`${n}-${v}.jar`);if(fs.existsSync(f))return f;}throw Error(`missing ${n}`)}
try{const paths=[];for(const [n,s] of Object.entries(files)){const f=path.join(out,n);fs.writeFileSync(f,s);paths.push(f)}
const std=jar('org.jetbrains.kotlin','kotlin-stdlib','2.4.20'),compiler=[jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.4.20'),std,jar('org.jetbrains.kotlin','kotlin-reflect','2.4.0'),jar('org.jetbrains.kotlin','kotlin-script-runtime','2.4.20'),jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),jar('org.jetbrains','annotations','13.0')];
for(const [cmd,args] of [['java',['-cp',compiler.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',std,'-d',out,...paths]],['java',['-cp',[out,std].join(path.delimiter),'fixture.HarnessKt']]]){const r=spawnSync(cmd,args,{cwd:root,stdio:'inherit'});if(r.error||r.status!==0)throw r.error||Error(`${cmd}: ${r.status}`)}
}finally{fs.rmSync(out,{recursive:true,force:true})}
