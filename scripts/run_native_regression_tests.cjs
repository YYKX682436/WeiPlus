// Isolated JVM regressions over host isolation, background-assist Root state, native hooks, and R3 async boundaries.
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
  'app/src/main/java/h/Hchat/hooks/api/model/WeChatMessage.kt',
  'app/src/main/java/h/Hchat/hooks/core/FeatureInstallDecision.kt',
  'app/src/main/java/h/Hchat/hooks/items/backgroundassist/BackgroundRunAssistRoot.kt',
  'app/src/main/java/h/Hchat/hooks/items/payment/transfer/AutoTransferRunSession.kt',
  'app/src/main/java/h/Hchat/hooks/items/payment/transfer/AutoTransferActionDispatcher.kt',
  'app/src/main/java/h/Hchat/hooks/items/autoreply/AutoReplyObservationState.kt',
  'app/src/main/java/h/Hchat/hooks/items/sendbutton/NativeSendButtonHandlers.kt',
  'app/src/main/java/h/Hchat/hooks/items/autoreply/AutoReplyFriendRequestHook.kt',
  'app/src/main/java/h/Hchat/preferences/HchatStorage.kt',
  ...walk(testRoot)
];
sources.push(process.env.WEIPLUS_AUTOREPLY_FEATURE_SOURCE ||
  'app/src/main/java/h/Hchat/hooks/items/autoreply/AutoReplyFeature.kt');
sources.push(process.env.WEIPLUS_BACKGROUND_ASSIST_FEATURE_SOURCE ||
  'app/src/main/java/h/Hchat/hooks/items/backgroundassist/BackgroundRunAssistFeature.kt');
const jar = path.join(output, 'native-regressions.jar');
run(['-Xmx384m', '-cp', jars.map(file => path.join(lib, file)).join(path.delimiter),
  'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
  '-classpath', path.join(lib, stdlib), '-d', jar, ...sources]);
run(['-Djava.io.tmpdir=' + output, '-cp', jar + path.delimiter + path.join(lib, stdlib), 'NativeRegressionKt']);

const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const entry = read('app/src/main/java/h/Hchat/ModuleEntry.java');
const packageHelper = read('app/src/main/java/h/Hchat/WechatPackage.kt');
const scope = read('app/src/main/res/values/arrays.xml');
const featureManager = read('app/src/main/java/h/Hchat/hooks/core/FeatureManager.kt');
const backgroundRuntime = read('app/src/main/java/h/Hchat/hooks/items/backgroundassist/BackgroundRunAssistRuntime.kt');
const backgroundRoot = read('app/src/main/java/h/Hchat/hooks/items/backgroundassist/BackgroundRunAssistRoot.kt');
const backgroundFeature = read('app/src/main/java/h/Hchat/hooks/items/backgroundassist/BackgroundRunAssistFeature.kt');
const backgroundSettings = read('app/src/main/java/h/Hchat/hooks/items/backgroundassist/BackgroundRunAssistSettings.kt');
const settingsPages = read('app/src/main/java/h/Hchat/ui/miuix/MiuixMomentsPages.kt');
const manifest = read('app/src/main/AndroidManifest.xml');
const autoReplyFeature = read('app/src/main/java/h/Hchat/hooks/items/autoreply/AutoReplyFeature.kt');
const autoTransferFeature = read('app/src/main/java/h/Hchat/hooks/items/payment/transfer/AutoTransferFeature.kt');
const momentsAutoCommentFeature = read('app/src/main/java/h/Hchat/hooks/items/moments/MomentsAutoCommentFeature.kt');
const dexFinder = read('app/src/main/java/h/Hchat/dexkit/DexFinder.java');
const quickReadFeature = read('app/src/main/java/h/Hchat/hooks/items/quickread/QuickMarkReadFeature.kt');
const nativeSendFeature = read('app/src/main/java/h/Hchat/hooks/items/sendbutton/NativeSendButtonFeature.kt');
const appSources = path.join(root, 'app/src/main');
assert.match(entry, /WechatPackage\.isTarget/);
assert.doesNotMatch(entry, /startsWith\("com\.tencent\.mm"\)/);
assert.match(scope, /<item>com\.tencent\.mm<\/item>/);
assert.match(scope, /<item>com\.tencent\.mm\.wx<\/item>/);
assert.match(packageHelper, /packageName == OFFICIAL \|\| packageName == MODIFIED/);
assert.match(featureManager, /lifecycleFeatures\.toList\(\)\.asReversed\(\)/);
assert.doesNotMatch(featureManager, /observeConfigChanges|notifyConfigChanged|configPreferenceListeners|getFeaturePrefs/);
assert.doesNotMatch(backgroundRuntime + backgroundRoot + backgroundFeature + manifest,
  /WeChatKeepAliveService|IWeChatKeepAliveControl|TargetBinding|watchdogTick|heartbeatTick|startForegroundService|FOREGROUND_SERVICE|specialUse|connectivitycheck\.gstatic\.com/);
assert.match(backgroundRuntime, /ArrayBlockingQueue\(2\)/);
assert.match(backgroundRuntime, /context\.getSharedPreferences\(ROOT_STATE_PREFS, Context\.MODE_PRIVATE\)/);
assert.match(backgroundRoot, /RootCommandResult\(null, null, timedOut = true\)/);
assert.match(backgroundRoot, /if \(!applied && current\(\)\) throw IllegalStateException/);
assert.match(backgroundRoot, /fun submit\(hostPackage: String, generation: Long/);
assert.match(backgroundRoot, /MAX_WAKE_LOCK_MILLIS = 10 \* 60 \* 1_000L/);
assert.match(backgroundSettings, /const val DEFAULT_WAKE_LOCK = false/);
assert.doesNotMatch(manifest, /FOREGROUND_SERVICE|WeChatKeepAliveService|foregroundServiceType|specialUse/);
assert.match(manifest, /android:name="android.permission.WAKE_LOCK"/);
assert.match(manifest, /<package android:name="com\.tencent\.mm"\s*\/>/);
assert.match(manifest, /<package android:name="com\.tencent\.mm\.wx"\s*\/>/);
assert.match(backgroundFeature, /HchatStorage\.preferences\(hostContext, BackgroundRunAssistSettings\.PREFS_NAME\)/);
assert.match(backgroundFeature, /unregisterOnSharedPreferenceChangeListener/);
assert.doesNotMatch(settingsPages, /WeChatKeepAliveMiuixPage|WeChatKeepAliveRuntime\.apply|强保活|看门狗|网络心跳|立即应用保活|前台服务保活/);
assert.match(settingsPages, /BackgroundRunAssistMiuixPage/);
assert.match(settingsPages, /单次最多持有 10 分钟/);
assert.match(settingsPages, /需要 Root/);
assert.doesNotMatch(settingsPages, /WeChatKeepAliveSettings\.KEY_(FOREGROUND_SERVICE|WATCHDOG|NETWORK_HEARTBEAT)/);
assert.doesNotMatch(fs.existsSync(path.join(root, 'app/src/main/aidl/h/Hchat/hooks/items/keepalive/IWeChatKeepAliveControl.aidl')) ? 'present' : '', /present/);
assert.doesNotMatch(fs.existsSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/keepalive/WeChatKeepAliveService.kt')) ? 'present' : '', /present/);
function sourceFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? sourceFiles(file) : [file];
  });
}
const forbiddenEntry = /WeChatKeepAliveService|IWeChatKeepAliveControl|TargetBinding|watchdogTick|heartbeatTick|generate_204/;
for (const file of sourceFiles(appSources).filter(file => /\.(kt|java|aidl|xml)$/.test(file))) {
  const source = fs.readFileSync(file, 'utf8');
  assert.doesNotMatch(source, forbiddenEntry, `obsolete runtime reference in ${path.relative(root, file)}`);
}
assert.match(autoReplyFeature, /AutoReplySettings\.KEY_ENABLE/);
assert.match(autoReplyFeature, /registerOnSharedPreferenceChangeListener/);
assert.match(autoReplyFeature, /observationState\.acceptsFallback\(\)/);
assert.match(autoReplyFeature, /AutoReplyRuntime\.handleMessage\(hostContext, message\)/);
assert.match(autoReplyFeature, /AutoReplyFriendRequestHook\.handleMessage\(message\)/);
assert.match(autoReplyFeature, /if \(!autoReplyEnabled && !autoAcceptEnabled\) return/);
assert.doesNotMatch(autoReplyFeature, /messageObservationInstalled/);
assert.match(autoTransferFeature, /AutoTransferSettings\.KEY_ENABLE/);
assert.match(autoTransferFeature, /runSession\.runIfCurrent\(session\)/);
assert.match(autoTransferFeature, /dispatchTransferAction\(session\) \{ handleMessage\(context, message, session\) \}/);
assert.match(autoTransferFeature, /Thread\(runnable, "Hchat-AutoTransfer"\)/);
assert.match(autoTransferFeature, /ArrayBlockingQueue\(ACTION_QUEUE_CAPACITY\)/);
assert.match(autoTransferFeature, /runSession\.invalidate\(\)/);
assert.doesNotMatch(autoTransferFeature, /handledTransfers\.clear\(\)/);
assert.match(momentsAutoCommentFeature, /syncEnabledResourcesLocked\(enabled\)/);
assert.match(momentsAutoCommentFeature, /if \(enabled\)/);
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
console.log('PASS: no service/Binder/watchdog/heartbeat entry; host-scoped background assist, dual package gate and R3 regressions');
