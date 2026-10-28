const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '../../..');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'weiplus-phase-b-version-'));
process.once('exit', () => fs.rmSync(output, { recursive: true, force: true }));
const compile = spawnSync(process.env.JAVAC || 'javac', [
  '-d', output,
  'app/src/main/java/h/Hchat/hooks/api/runtime/WeChatVersionGate.java',
  'scripts/tests/phase_b/WeChatVersionGateRegression.java'
], { cwd: root, encoding: 'utf8' });
if (compile.stdout) process.stdout.write(compile.stdout);
if (compile.stderr) process.stderr.write(compile.stderr);
if (compile.error || compile.status !== 0) throw compile.error || new Error('Version gate javac failed');
const run = spawnSync(process.env.JAVA || 'java', [
  '-cp', output, 'WeChatVersionGateRegression'
], { cwd: root, encoding: 'utf8' });
if (run.stdout) process.stdout.write(run.stdout);
if (run.stderr) process.stderr.write(run.stderr);
if (run.error || run.status !== 0) throw run.error || new Error('Version gate regression failed');

function jar(group, name, version) {
  const directory = path.join(os.homedir(), '.gradle/caches/modules-2/files-2.1', group, name, version);
  for (const hash of fs.readdirSync(directory)) {
    const candidate = path.join(directory, hash, name + '-' + version + '.jar');
    if (fs.existsSync(candidate)) return candidate;
  }
  throw new Error('Missing Kotlin compiler dependency: ' + directory);
}
const stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.20');
const compiler = [
  jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.20'), stdlib,
  jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.4.0'),
  jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.4.20'),
  jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
  jar('org.jetbrains', 'annotations', '13.0')
];
const idleOutput = path.join(output, 'idle');
fs.mkdirSync(idleOutput);
const idleCompile = spawnSync(process.env.JAVA || 'java', [
  '-Xmx256m', '-cp', compiler.join(path.delimiter), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
  '-no-stdlib', '-no-reflect', '-classpath', stdlib, '-d', idleOutput,
  'third_party/dexkit-memory/IdleNativeHandle.kt',
  'scripts/tests/phase_b/IdleNativeHandleRegression.kt'
], { cwd: root, encoding: 'utf8', timeout: 60000 });
if (idleCompile.stdout) process.stdout.write(idleCompile.stdout);
if (idleCompile.stderr) process.stderr.write(idleCompile.stderr);
if (idleCompile.error || idleCompile.status !== 0) {
  throw idleCompile.error || new Error('DexKit idle lifecycle test compilation failed');
}
const idleRun = spawnSync(process.env.JAVA || 'java', [
  '-cp', [idleOutput, stdlib].join(path.delimiter), 'org.luckypray.dexkit.IdleNativeHandleRegressionKt'
], { cwd: root, encoding: 'utf8', timeout: 60000 });
if (idleRun.stdout) process.stdout.write(idleRun.stdout);
if (idleRun.stderr) process.stderr.write(idleRun.stderr);
if (idleRun.error || idleRun.status !== 0) {
  throw idleRun.error || new Error('DexKit idle lifecycle regression failed');
}
