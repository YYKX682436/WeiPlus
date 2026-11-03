const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../..');
const baseline = process.argv.includes('--baseline');
function read(file) {
  return baseline
    ? execFileSync('git', ['show', `HEAD:${file}`], { cwd: root, encoding: 'utf8' })
    : fs.readFileSync(path.join(root, file), 'utf8');
}
function body(source, signature) {
  const start = source.indexOf(signature);
  assert.notEqual(start, -1, `missing ${signature}`);
  const open = source.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}' && --depth === 0) return source.slice(open + 1, i);
  }
  throw new Error(`unclosed ${signature}`);
}

const entry = read('app/src/main/java/h/Hchat/ModuleEntry.java');
const ready = body(entry, 'void onPackageReady(PackageReadyParam param)');
const appCreate = body(entry, '"application-on-create"');
const init = body(entry, 'private void scheduleModuleInitialization(');
const tinker = body(entry, baseline
  ? 'private void installHotUpdateEarlyHook('
  : 'private boolean installHotUpdateEarlyHook(');
const hotUpdate = body(entry, baseline
  ? 'private void installHotUpdateEarlyHook('
  : 'private boolean installHotUpdateEarlyHook(');
const apis = read('app/src/main/java/h/Hchat/hooks/api/core/WeChatApis.java');
const coreReady = body(apis, 'boolean isCoreInitialized()');
const manager = read('app/src/main/java/h/Hchat/hooks/core/FeatureManager.kt');
const revalidate = body(manager, 'fun revalidateRuntime(');

const checks = [
  ['entry Hook is installed synchronously before prewarm scheduling',
    /StartupEntryDispatch\.installBeforeScheduling\([\s\S]*installPackageHooks\(param\)[\s\S]*scheduleArchiveIdentityPrewarm\(param\)/.test(ready)],
  ['Application.onCreate independently marks readiness and installs early entries before init',
    /startupState\.markApplicationReady\(\)[\s\S]*installApplicationEarlyFeatures\(app, lpparam\)[\s\S]*scheduleModuleInitialization/.test(appCreate)],
  ['Tinker attach cannot initialize before Application and early Tinker failure is retryable',
    /startupState\.shouldScheduleInitialization\(\)/.test(tinker) &&
      /startupState\.isApplicationReady\(\)/.test(init) &&
      /StartupEntryDispatch\.installOptional/.test(body(entry, 'private boolean installPackageHooks(')) &&
      /catch \(Throwable failure\)[\s\S]*return false/.test(hotUpdate) &&
      /scheduleHotUpdateEntryHookRetry/.test(entry)],
  ['core readiness checks successful required Hook state',
    /areRequiredCoreHooksOperational/.test(coreReady) &&
      /currentActivityApi\.isAvailable/.test(coreReady) &&
      /activityStartApi\.isAvailable/.test(coreReady) &&
      /lifecycleApi\.isAvailable/.test(coreReady)],
  ['core API replacement rebinds installed business subscriptions after core first',
    /pendingRevalidations\.addAll\(installedFeatures/.test(revalidate) &&
      /sortedBy \{ if \(it == CORE_API_ID\) 0 else 1 \}/.test(revalidate) &&
      /feature\.onDestroy\(previousContext\)[\s\S]*feature\.onInit\(context\)[\s\S]*feature\.install\(context\)/.test(revalidate)]
];

if (baseline) {
  const red = checks.filter(([, passed]) => !passed).map(([name]) => name);
  assert.equal(red.length, checks.length, `expected old source to fail all R1 checks, got ${red.length}/${checks.length}`);
  for (const name of red) process.stdout.write(`RED (baseline): ${name}\n`);
} else {
  for (const [name, passed] of checks) {
    assert.ok(passed, `R1 source regression failed: ${name}`);
    process.stdout.write(`PASS: ${name}\n`);
  }
}
