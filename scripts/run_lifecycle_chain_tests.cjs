// Full production lifecycle and chat-page APIs with minimal Android value fixtures.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),tmp=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-lifecycle-chain-'));
process.once('exit',()=>fs.rmSync(tmp,{recursive:true,force:true}));
const api=require('./lib/api102.cjs').prepare(tmp),cp=[tmp,api.classpath].join(path.delimiter);
function run(command,args){const r=spawnSync(command,args,{cwd:root,encoding:'utf8',timeout:90000});process.stdout.write(r.stdout||'');process.stderr.write(r.stderr||'');if(r.status!==0)throw Error('Lifecycle Chain regression failed');}
const test='scripts/tests/lifecycle_chain/';
run('javac',['-cp',api.classpath,'-d',tmp,...fs.readdirSync(path.join(root,test)).filter(x=>x.endsWith('.java')).map(x=>test+x),
 'scripts/tests/database_chain/DexFinder.java','scripts/tests/database_chain/KavaReflector.java','scripts/tests/database_chain/HookRegistry.java',
 'scripts/tests/api102/ChainFixture.java','scripts/tests/api102/FrameworkFixture.java','scripts/tests/message_parser/TextUtils.java',
 'app/src/main/java/h/Hchat/hooks/core/XposedRuntime.java','app/src/main/java/h/Hchat/utils/HLog.java',
 'app/src/main/java/h/Hchat/hooks/api/ui/WeChatLifecycleApi.java','app/src/main/java/h/Hchat/hooks/api/ui/WeChatChatPageApi.java']);
run('java',['-cp',cp,'LifecycleChainRegression']);
