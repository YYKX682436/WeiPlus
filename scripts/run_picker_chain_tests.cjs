// Exercise the complete production picker owner, including a deterministic launch/teardown race.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),lib=process.argv[2]||process.env.KOTLIN_LIB_DIR;
if(!lib)throw Error('Pass Kotlin compiler library directory');
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-picker-chain-'));
process.once('exit',()=>fs.rmSync(tmp,{recursive:true,force:true}));
const api=require('./lib/api102.cjs').prepare(tmp),jars=fs.readdirSync(lib).filter(x=>x.endsWith('.jar')).map(x=>path.join(lib,x));
const stdlib=jars.find(x=>/kotlin-stdlib-[\d.]+\.jar$/.test(x)),cp=[tmp,stdlib,api.classpath].join(path.delimiter);
function run(command,args){const r=spawnSync(command,args,{cwd:root,encoding:'utf8',timeout:90000});process.stdout.write(r.stdout||'');process.stderr.write(r.stderr||'');if(r.status!==0)throw Error('Picker Chain regression failed');}
const test='scripts/tests/picker_chain/';
run('javac',['-cp',api.classpath,'-d',tmp,...['Activity.java','Intent.java','Uri.java'].map(x=>test+x),'scripts/tests/api102/ChainFixture.java','scripts/tests/api102/FrameworkFixture.java','app/src/main/java/h/Hchat/hooks/core/XposedRuntime.java','app/src/main/java/h/Hchat/utils/HLog.java']);
const jar=path.join(tmp,'picker.jar');
run('java',['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',jar,test+'Store.kt',test+'PickerRegression.kt','app/src/main/java/h/Hchat/hooks/items/custombottombar/CustomBottomBarIconPicker.kt']);
run('java',['-cp',[jar,cp].join(path.delimiter),'PickerRegressionKt']);
