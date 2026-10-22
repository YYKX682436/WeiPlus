// Isolated JVM regressions over native package, feature-policy, storage, send and friend-request production code.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const lib = process.argv[2] || process.env.KOTLIN_LIB_DIR;
if (!lib) throw new Error('Pass the directory containing Kotlin compiler and stdlib JARs.');
const jars = fs.readdirSync(lib).filter(name => /^(kotlin-compiler-embeddable-|kotlin-stdlib-|kotlin-reflect-|kotlin-script-runtime-|kotlinx-coroutines-core-jvm-|annotations-|trove4j-).*\.jar$/.test(name));
const stdlib = jars.find(name => /^kotlin-stdlib-[0-9]/.test(name));
if (!stdlib || !jars.some(name => name.startsWith('kotlin-compiler-embeddable-'))) throw new Error('Missing Kotlin compiler or stdlib');
const java = process.env.JAVA || 'java';
const root = path.resolve(__dirname, '..');
const testRoot = path.join(root, 'scripts/tests/native_regressions');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'weiplus-native-tests-'));
process.once('exit', () => fs.rmSync(output, { recursive: true, force: true }));
function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(file) : file.endsWith('.kt') ? [path.relative(root, file)] : [];
  });
}
function run(args) {
  const result = spawnSync(java, args, { cwd: root, encoding: 'utf8', timeout: 90_000 });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error || result.status !== 0) throw result.error || new Error('JVM check failed: ' + result.status);
}
const sources = [
  'app/src/main/java/h/Hchat/WechatPackage.kt',
  'app/src/main/java/h/Hchat/hooks/core/FeatureInstallDecision.kt',
  'app/src/main/java/h/Hchat/hooks/items/sendbutton/NativeSendButtonHandlers.kt',
  'app/src/main/java/h/Hchat/hooks/items/autoreply/AutoReplyFriendRequestHook.kt',
  'app/src/main/java/h/Hchat/preferences/HchatStorage.kt',
  ...walk(testRoot)
];
const jar = path.join(output, 'native-regressions.jar');
run(['-Xmx384m', '-cp', jars.map(file => path.join(lib, file)).join(path.delimiter),
  'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
  '-classpath', path.join(lib, stdlib), '-d', jar, ...sources]);
run(['-Djava.io.tmpdir=' + output, '-cp', jar + path.delimiter + path.join(lib, stdlib), 'NativeRegressionKt']);
const entry = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/ModuleEntry.java'), 'utf8');
const packageHelper = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/WechatPackage.kt'), 'utf8');
const scope = fs.readFileSync(path.join(root, 'app/src/main/res/values/arrays.xml'), 'utf8');
const featureManager = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/core/FeatureManager.kt'), 'utf8');
const dexFinder = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/dexkit/DexFinder.java'), 'utf8');
const quickReadFeature = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/quickread/QuickMarkReadFeature.kt'), 'utf8');
const nativeSendFeature = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/sendbutton/NativeSendButtonFeature.kt'), 'utf8');
assert.match(entry, /WechatPackage\.isTarget/);
assert.doesNotMatch(entry, /startsWith\("com\.tencent\.mm"\)/);
assert.match(scope, /<item>com\.tencent\.mm<\/item>/);
assert.match(scope, /<item>com\.tencent\.mm\.wx<\/item>/);
assert.match(packageHelper, /packageName == OFFICIAL \|\| packageName == MODIFIED/);
assert.match(featureManager, /lifecycleFeatures\.toList\(\)\.asReversed\(\)/);
assert.match(featureManager, /globalPrefs\.registerOnSharedPreferenceChangeListener/);
assert.match(featureManager, /prefs\.registerOnSharedPreferenceChangeListener/);
assert.match(quickReadFeature, /override fun onFeatureDestroy/);
assert.match(quickReadFeature, /QuickMarkReadRuntime\.destroy\(\)/);
assert.match(nativeSendFeature, /chatPage\.subscribe/);
assert.match(nativeSendFeature, /event\.isEnter/);
assert.match(nativeSendFeature, /chatPageRetryAttempted\.compareAndSet/);
assert.match(dexFinder, /resolveChatPageApi\(\);\s*resolveNativeSendButtonApi\(\);/);
const sendButtonResolver = dexFinder.slice(
  dexFinder.indexOf('public synchronized void resolveNativeSendButtonApi()'),
  dexFinder.indexOf('// ============ DexKit 结果缓存 ============')
);
assert.match(sendButtonResolver, /ensureSnapshotLoaded\(\);\s*if \(chatFooterSendClickMethod != null\) return;/);
assert.doesNotMatch(sendButtonResolver, /if \(ensureSnapshotLoaded\(\)\) return;/);
console.log('PASS: ModuleEntry exact target gate, LSPosed dual-package scope, and cached send-button fallback');
