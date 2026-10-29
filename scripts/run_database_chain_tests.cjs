// Compile the full production database listener and execute its installed API 102 Hookers.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),lib=process.argv[2]||process.env.KOTLIN_LIB_DIR;
if(!lib)throw Error('Pass Kotlin compiler library directory');
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-database-chain-'));
process.once('exit',()=>fs.rmSync(tmp,{recursive:true,force:true}));
const api=require('./lib/api102.cjs').prepare(tmp),jars=fs.readdirSync(lib).filter(x=>x.endsWith('.jar')).map(x=>path.join(lib,x));
const stdlib=jars.find(x=>/kotlin-stdlib-[\d.]+\.jar$/.test(x));
function run(command,args){const r=spawnSync(command,args,{cwd:root,encoding:'utf8',timeout:90000});process.stdout.write(r.stdout||'');process.stderr.write(r.stderr||'');if(r.status!==0)throw Error('Database Chain regression failed');}
const tests='scripts/tests/database_chain/';
run('javac',['-cp',api.classpath,'-d',tmp,tests+'ContentValues.java','scripts/tests/message_parser/TextUtils.java','scripts/tests/api102/ChainFixture.java']);
const cp=[tmp,stdlib,api.classpath].join(path.delimiter);
run('java',['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',tmp,'app/src/main/java/h/Hchat/hooks/api/model/DatabaseChange.kt']);
run('javac',['-cp',cp,'-d',tmp,...fs.readdirSync(path.join(root,tests)).filter(x=>x.endsWith('.java')).map(x=>tests+x),'app/src/main/java/h/Hchat/hooks/api/runtime/WeChatSqliteWrapperLocator.java','app/src/main/java/h/Hchat/hooks/api/runtime/WeChatDatabaseListenerApi.java']);
run('java',['-cp',cp,'DatabaseChainRegression']);
