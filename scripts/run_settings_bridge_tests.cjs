// Execute the actual settings result owner and configuration bridge, without loading Compose.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),lib=process.argv[2]||process.env.KOTLIN_LIB_DIR;
if(!lib)throw Error('Pass Kotlin compiler library directory');
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-settings-bridge-'));
process.once('exit',()=>fs.rmSync(tmp,{recursive:true,force:true}));
const api=require('./lib/api102.cjs').prepare(tmp),jars=fs.readdirSync(lib).filter(x=>x.endsWith('.jar')).map(x=>path.join(lib,x));
const stdlib=jars.find(x=>/kotlin-stdlib-[\d.]+\.jar$/.test(x)),cp=[tmp,stdlib,api.classpath].join(path.delimiter);
function run(command,args){const r=spawnSync(command,args,{cwd:root,encoding:'utf8',timeout:90000});process.stdout.write(r.stdout||'');process.stderr.write(r.stderr||'');if(r.status!==0)throw Error('Settings bridge regression failed');}
function block(input,anchor){const at=input.indexOf(anchor);if(at<0)throw Error('Missing production source '+anchor);const begin=input.indexOf('{',at);let depth=1,end=begin+1;while(depth&&end<input.length){if(input[end]==='{')depth++;if(input[end]==='}')depth--;end++;}if(depth)throw Error('Unbalanced source '+anchor);return input.slice(at,end);}
const source=fs.readFileSync(path.join(root,'app/src/main/java/h/Hchat/ui/miuix/MiuixSettingsPage.kt'),'utf8');
const parts={OWNER:block(source,'private class SettingsActivityResultHooks'),BRIDGE:block(source,'internal object ConfigImportExportBridge'),CONSTANTS:source.split('\n').filter(line=>/^private const val CONFIG_(?:EXPORT|IMPORT)_REQUEST_CODE =/.test(line)).join('\n')};
let fixture=fs.readFileSync(path.join(root,'scripts/tests/settings_bridge/Regression.kt.in'),'utf8');for(const[key,value]of Object.entries(parts))fixture=fixture.replace('@'+key+'@',value);
const extracted=path.join(tmp,'SettingsBridgeRegression.kt');fs.writeFileSync(extracted,fixture);
const test='scripts/tests/settings_bridge/';
run('javac',['-cp',api.classpath,'-d',tmp,...fs.readdirSync(path.join(root,test)).filter(x=>x.endsWith('.java')).map(x=>test+x),'scripts/tests/api102/ChainFixture.java','scripts/tests/api102/FrameworkFixture.java','app/src/main/java/h/Hchat/hooks/core/XposedRuntime.java','app/src/main/java/h/Hchat/utils/HLog.java']);
const jar=path.join(tmp,'bridge.jar');
run('java',['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',jar,extracted]);
run('java',['-cp',[jar,cp].join(path.delimiter),'SettingsBridgeRegressionKt']);
