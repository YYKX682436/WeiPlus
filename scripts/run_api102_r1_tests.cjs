// Execute production parser, fallback matcher and interceptors, plus combined teardown faults.
const fs = require('node:fs'), path = require('node:path'), os = require('node:os');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname, '..'), lib = process.argv[2] || process.env.KOTLIN_LIB_DIR;
if (!lib) throw Error('Pass Kotlin compiler library directory');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'weiplus-api102-r1-'));
process.once('exit', () => fs.rmSync(tmp, {recursive: true, force: true}));
const api = require('./lib/api102.cjs').prepare(tmp);
const baseline = process.env.WEIPLUS_R1_BASELINE;
function read(file) {
  if (!baseline) return fs.readFileSync(path.join(root, file), 'utf8');
  const result = spawnSync('git', ['show', baseline + ':' + file], {cwd:root,encoding:'utf8'});
  if(result.status !== 0) throw Error(result.stderr);
  return result.stdout;
}
function production(file) {
  if(!baseline) return file;
  const destination=path.join(tmp,'baseline',file);fs.mkdirSync(path.dirname(destination),{recursive:true});
  fs.writeFileSync(destination,read(file));return destination;
}

const jars = fs.readdirSync(lib).filter(x => x.endsWith('.jar')).map(x => path.join(lib, x));
const stdlib = jars.find(x => /kotlin-stdlib-[\d.]+\.jar$/.test(x));
function block(input, anchor) {
  const at = input.indexOf(anchor); if (at < 0) throw Error('Missing production source ' + anchor);
  const mask = input.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g, x => ' '.repeat(x.length));
  const begin = mask.indexOf('{', at); let depth = 1, end = begin + 1;
  while (depth && end < mask.length) { if (mask[end] === '{') depth++; if (mask[end] === '}') depth--; end++; }
  if (depth) throw Error('Unbalanced source ' + anchor);
  return input.slice(at, end);
}
function run(command, args) {
  const r = spawnSync(command, args, {cwd: root, encoding: 'utf8', timeout: 90000});
  process.stdout.write(r.stdout || ''); process.stderr.write(r.stderr || '');
  return r.status === 0;
}
const support = ['scripts/tests/api102/ChainFixture.java', 'scripts/tests/api102/FrameworkFixture.java',
  production('app/src/main/java/h/Hchat/hooks/core/XposedRuntime.java'), production('app/src/main/java/h/Hchat/utils/HLog.java')];
if (!baseline && fs.existsSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/core/HookErrorBoundary.java'))) support.push('app/src/main/java/h/Hchat/hooks/core/HookErrorBoundary.java');
if (!run('javac', ['-cp', api.classpath, '-d', tmp, ...support])) process.exit(1);
const source = read('app/src/main/java/h/Hchat/hooks/items/messagetextcolor/MessageTextColorFeature.kt');
const methods = ['applyTextColor','resolveMessageTextTargets','isChatRecord','boundMessageBodyTarget','originalMessageTextTarget','findMessageTextTarget','targetTextView','collectMessageTextTargets','messageTextCandidates','addRawText','messageTextTargets','addMessageTextTarget','addNormalizedTarget','stripMarkupText','legacyTextContainsMessage','textContainsMessage','textMatchesMessage','hashTopicComparable','normalizeText','decodeMessageText'];
const hook = block(source, 'XposedInterface.Hooker {').replace('XposedInterface.Hooker ', '');
let color = fs.readFileSync(path.join(root, 'scripts/tests/api102_r1/DisplayRegression.kt.in'), 'utf8')
  .replace('@METHODS@', methods.map(name => block(source, 'private fun ' + name + '(')).join('\n'))
  .replace('@HOOK@', hook);
const time = read('app/src/main/java/h/Hchat/hooks/items/chattime/ChatTimeStyleFeature.kt');
color = color.replace('@TIME_HOOK@', block(time, 'XposedInterface.Hooker {').replace('XposedInterface.Hooker ', ''));
// The boundary import becomes available with the production fix; no replacement implementation is injected.
color = color.replace('@BOUNDARY_IMPORT@', !baseline && fs.existsSync(path.join(root,'app/src/main/java/h/Hchat/hooks/core/HookErrorBoundary.java')) ? 'import h.Hchat.hooks.core.HookErrorBoundary' : '');
fs.writeFileSync(path.join(tmp, 'DisplayRegression.kt'), color);
const settings = read('app/src/main/java/h/Hchat/ui/miuix/MiuixSettingsPage.kt');
let batch = fs.readFileSync(path.join(root, 'scripts/tests/api102_r1/BatchRegression.kt.in'), 'utf8')
  .replace('@BACK_CLEANUP@', block(settings, 'fun unhookActivityBack()'))
  .replace('@BACK_HOOK@', block(settings.slice(settings.indexOf('val keyHook =')), 'XposedInterface.Hooker {').replace('XposedInterface.Hooker ', ''));
fs.writeFileSync(path.join(tmp, 'BatchRegression.kt'), batch);
const cp = [tmp, stdlib, api.classpath].join(path.delimiter); let failed = false;
for (const [name, sources] of [
  ['DisplayRegression', [path.join(tmp,'DisplayRegression.kt'), 'app/src/main/java/h/Hchat/hooks/api/model/WeChatMessage.kt','app/src/main/java/h/Hchat/hooks/api/model/WeChatMessageTypes.kt']],
  ['BatchRegression', [path.join(tmp,'BatchRegression.kt'), production('app/src/main/java/h/Hchat/hooks/core/HookRegistry.kt')]],
]) {
  const jar = path.join(tmp, name + '.jar');
  const compiled = run('java', ['-Xmx384m','-cp',jars.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',jar,...sources]);
  if (!compiled || !run('java',['-cp',[jar,cp].join(path.delimiter),name+'Kt'])) failed = true;
}
const imageSource=read('app/src/main/java/h/Hchat/hooks/api/media/WeChatImageApi.java');
const image=fs.readFileSync(path.join(root,'scripts/tests/api102_r1/ImageOwnerRegression.java.in'),'utf8')
  .replace('@INSTALL@',block(imageSource,'public static void installMarsCdnManagerHook(Class<?> managerClass'))
  .replace('@BOUNDARY_IMPORT@',baseline ? '' : 'import h.Hchat.hooks.core.HookErrorBoundary;');
const imagePath=path.join(tmp,'ImageOwnerRegression.java');fs.writeFileSync(imagePath,image);
const imageCp=[path.join(tmp,'BatchRegression.jar'),cp].join(path.delimiter);
if(!run('javac',['-cp',imageCp,'-d',tmp,imagePath]) || !run('java',['-cp',imageCp,'ImageOwnerRegression'])) failed=true;
if (failed) process.exitCode = 1;
