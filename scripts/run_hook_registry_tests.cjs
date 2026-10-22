// Isolated JVM regressions for production HookRegistry and native send-button dispatch.
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
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'weiplus-hook-tests-'));
process.once('exit', () => fs.rmSync(output, { recursive: true, force: true }));
function run(args) {
  const result = spawnSync(java, args, { cwd: root, encoding: 'utf8', timeout: 60_000 });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error || result.status !== 0) throw result.error || new Error('JVM check failed: ' + result.status);
}
const groups = [
  ['hook_registry', 'HookRegistryRegressionKt', [
    'app/src/main/java/h/Hchat/hooks/core/HookRegistry.kt',
    'scripts/tests/hook_registry/XposedStub.kt',
    'scripts/tests/hook_registry/HookRegistryRegression.kt'
  ]],
  ['send_dispatch', 'h.Hchat.hooks.items.sendbutton.NativeSendButtonHandlersRegressionKt', [
    'app/src/main/java/h/Hchat/hooks/items/sendbutton/NativeSendButtonHandlers.kt',
    'scripts/tests/native_regressions/NativeSendButtonHandlersRegression.kt'
  ]]
];
for (const [name, main, sources] of groups) {
  const jar = path.join(output, name + '.jar');
  run(['-Xmx256m', '-cp', jars.map(file => path.join(lib, file)).join(path.delimiter),
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
    '-classpath', path.join(lib, stdlib), '-d', jar, ...sources]);
  run(['-Djava.io.tmpdir=' + output, '-cp', jar + path.delimiter + path.join(lib, stdlib), main]);
}
const hook = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/sendbutton/NativeSendButtonHook.kt'), 'utf8');
assert.doesNotMatch(hook, /dispatchTouchEvent/, 'Native send hook must not install a global touch listener');
assert.match(hook, /withInputDuration\(inputDuration\)/);
assert.match(hook, /OutgoingTextDecoratorRegistry\.decorate/);
assert.match(hook, /restoreRejectedDecoration/);
console.log('PASS: native send Hook retains input duration, decorators, rejected-send restore; no dispatchTouchEvent Hook');
