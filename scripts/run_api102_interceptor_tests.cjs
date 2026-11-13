// Execute production interceptor source with the published API 102 Chain contract.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),lib=process.argv[2]||process.env.KOTLIN_LIB_DIR;
if(!lib)throw Error('Pass Kotlin compiler library directory');
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-api102-interceptors-'));
process.once('exit',()=>fs.rmSync(tmp,{recursive:true,force:true}));
const api=require('./lib/api102.cjs').prepare(tmp),jars=fs.readdirSync(lib).filter(x=>x.endsWith('.jar')).map(x=>path.join(lib,x));
const stdlib=jars.find(x=>/kotlin-stdlib-[\d.]+\.jar$/.test(x));
function run(command,args){const r=spawnSync(command,args,{cwd:root,encoding:'utf8',timeout:90000});process.stdout.write(r.stdout||'');process.stderr.write(r.stderr||'');if(r.status!==0)throw Error('API102 interceptor regression failed');}
function block(input,anchor){const at=input.indexOf(anchor);if(at<0)throw Error('Missing production source '+anchor);const begin=input.indexOf('{',at);let depth=1,end=begin+1;while(depth&&end<input.length){if(input[end]==='{')depth++;if(input[end]==='}')depth--;end++;}if(depth)throw Error('Unbalanced source '+anchor);return input.slice(at,end);}
function data(input,anchor){const at=input.indexOf(anchor);if(at<0)throw Error('Missing '+anchor);const begin=input.indexOf('(',at);let depth=1,end=begin+1;while(depth){if(input[end]==='(')depth++;if(input[end]===')')depth--;end++;}return input.slice(at,end);}
const source=fs.readFileSync(path.join(root,'app/src/main/java/h/Hchat/hooks/items/sendbutton/NativeSendButtonHook.kt'),'utf8');
const replacements={INTERCEPT:block(source,'internal fun interceptSendClick'),APPLY:block(source,'private fun applyTextDecorators'),RESTORE:block(source,'private fun restoreRejectedDecoration'),DATA:data(source,'private data class AppliedTextDecoration'),INPUT_START:data(source,'private data class InputStart').replace('private data class','data class')};
let fixture=fs.readFileSync(path.join(root,'scripts/tests/api102/SendClickRegression.kt.in'),'utf8');
for(const [key,value]of Object.entries(replacements))fixture=fixture.replace('@'+key+'@',value);
const extracted=path.join(tmp,'SendClickRegression.kt');fs.writeFileSync(extracted,fixture);
run('javac',['-cp',api.classpath,'-d',tmp,'scripts/tests/api102/ChainFixture.java']);
const cp=[tmp,stdlib,api.classpath].join(path.delimiter),jar=path.join(tmp,'interceptors.jar');
run('java',['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',jar,extracted,'app/src/main/java/h/Hchat/hooks/items/sendbutton/NativeSendButtonHandlers.kt','app/src/main/java/h/Hchat/hooks/api/message/OutgoingTextDecoratorRegistry.kt','scripts/tests/native_regressions/h/Hchat/utils/HLog.kt']);
run('java',['-cp',[jar,cp].join(path.delimiter),'SendClickRegressionKt']);

const avatar=fs.readFileSync(path.join(root,'app/src/main/java/h/Hchat/hooks/items/customfriendavatar/CustomFriendAvatarFeature.kt'),'utf8');
function hookAfter(anchor){const start=avatar.search(anchor);if(start<0)throw Error('Missing avatar hook '+anchor);return block(avatar.slice(start),'Hooker {').replace('Hooker ','');}
let avatarFixture=fs.readFileSync(path.join(root,'scripts/tests/api102/AvatarRegression.kt.in'),'utf8');
for(const [key,anchor]of Object.entries({LEGACY:/val legacy\s*=\s*hook\(/,CONSTRUCTOR:/val constructor\s*=\s*hook\(/,MODIFY:/val modify\s*=\s*avatarMembers\.workerModify/,DRAW:/val draw\s*=\s*hook\(/}))avatarFixture=avatarFixture.replace('@'+key+'@',hookAfter(anchor));
const avatarSource=path.join(tmp,'AvatarRegression.kt'),avatarJar=path.join(tmp,'avatar.jar');fs.writeFileSync(avatarSource,avatarFixture);
run('java',['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',avatarJar,avatarSource]);
run('java',['-cp',[avatarJar,cp].join(path.delimiter),'AvatarRegressionKt']);

const projection=fs.readFileSync(path.join(root,'app/src/main/java/h/Hchat/hooks/items/conversationgroup/ConversationGroupHomeProjection.kt'),'utf8');
function projectionHook(anchor){const start=projection.indexOf(anchor);if(start<0)throw Error('Missing projection hook '+anchor);return block(projection.slice(start),'XposedInterface.Hooker {').replace('XposedInterface.Hooker ','');}
let projectionFixture=fs.readFileSync(path.join(root,'scripts/tests/api102/ProjectionRegression.kt.in'),'utf8');
for(const [key,value]of Object.entries({LEGACY:projectionHook('(queries + changes).forEach'),MODERN:projectionHook('hook(page,')}))projectionFixture=projectionFixture.replace('@'+key+'@',value);
const projectionSource=path.join(tmp,'ProjectionRegression.kt'),projectionJar=path.join(tmp,'projection.jar');fs.writeFileSync(projectionSource,projectionFixture);
run('java',['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',projectionJar,projectionSource]);
run('java',['-cp',[projectionJar,cp].join(path.delimiter),'ProjectionRegressionKt']);

const featureRoot=path.join(root,'app/src/main/java/h/Hchat/hooks/items');
const upload=fs.readFileSync(path.join(featureRoot,'moments/OriginalMomentsUploadFeature.kt'),'utf8');
const scan=fs.readFileSync(path.join(featureRoot,'fakescancamera/FakeScanCameraFeature.kt'),'utf8');
const sns=fs.readFileSync(path.join(featureRoot,'moments/SnsAntiRecallFeature.kt'),'utf8');
const expression=(input,anchor)=>block(input.slice(input.indexOf(anchor)),'XposedInterface.Hooker {').replace('XposedInterface.Hooker ','');
let mutations=fs.readFileSync(path.join(root,'scripts/tests/api102/ArgumentMutationRegression.kt.in'),'utf8');
const mutationParts={UPLOAD_ARGS:block(upload,'private fun patchGalleryTimelineArgs'),UPLOAD_RESULT:block(upload,'private fun patchGalleryResultIntent'),UPLOAD_HOOK:expression(upload,'if (!imagePreviewInstalled)'),SCAN_ARGS:block(scan,'private fun patchQBarStringArgs'),SCAN_SCENE:block(scan,'private fun isAlbumOrLongPressScene'),SCAN_INT:block(scan,'private fun intArg'),SCAN_HOOK:expression(scan,'fun install('),SNS_QUERY:block(sns,'private fun handleSnsQuery'),SNS_SQL:block(sns,'private fun rewriteSnsSql'),SNS_IN:block(sns,'private fun enhanceSourceTypeInFilters'),SNS_REGEX:sns.slice(sns.indexOf('private val SNS_SELECT_REGEX'),sns.lastIndexOf('\n    }')),SNS_HOOK:expression(sns,'private fun hookDatabaseClass')};
for(const [key,value]of Object.entries(mutationParts))mutations=mutations.replace('@'+key+'@',value);
const mutationsSource=path.join(tmp,'ArgumentMutationRegression.kt'),mutationsJar=path.join(tmp,'mutations.jar');fs.writeFileSync(mutationsSource,mutations);
run('java',['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',mutationsJar,mutationsSource]);
run('java',['-cp',[mutationsJar,cp].join(path.delimiter),'ArgumentMutationRegressionKt']);

let footers=fs.readFileSync(path.join(root,'scripts/tests/api102/FooterCleanupRegression.kt.in'),'utf8');
for(const [key,file]of Object.entries({QUOTE:'quoteclear/QuoteDeleteClearFeature.kt',SWIPE:'swipequote/SwipeQuoteFeature.kt'})){
 const input=fs.readFileSync(path.join(featureRoot,file),'utf8');
 footers=footers.replace('@'+key+'@',expression(input,'if (onDetached != null)'));
}
const footersSource=path.join(tmp,'FooterCleanupRegression.kt'),footersJar=path.join(tmp,'footers.jar');fs.writeFileSync(footersSource,footers);
run('java',['-Xmx256m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',footersJar,footersSource]);
run('java',['-cp',[footersJar,cp].join(path.delimiter),'FooterCleanupRegressionKt']);
