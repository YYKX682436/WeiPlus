const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '../../..');
const gradleCache = path.join(os.homedir(), '.gradle/caches/modules-2/files-2.1');
function jar(group, name, version) {
  const directory = path.join(gradleCache, group, name, version);
  for (const hash of fs.readdirSync(directory)) {
    const candidate = path.join(directory, hash, name + '-' + version + '.jar');
    if (fs.existsSync(candidate)) return candidate;
  }
  throw new Error('Missing Kotlin compiler dependency: ' + directory);
}

const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'weiplus-scheduler-recovery-'));
process.once('exit', () => fs.rmSync(temp, { recursive: true, force: true }));
const stubs = {
  'android/os/Looper.kt': `package android.os
class Looper { companion object { private val MAIN = Looper(); @JvmStatic fun getMainLooper() = MAIN } }
`,
  'android/os/Handler.kt': `package android.os
class Handler(looper: Looper) {
  private val tasks get() = queue
  fun postDelayed(task: Runnable, delayMillis: Long): Boolean { synchronized(queue) { tasks.addLast(task) }; return true }
  fun removeCallbacks(task: Runnable) { synchronized(queue) { tasks.removeAll { it === task } } }
  fun pendingCount() = synchronized(queue) { tasks.size }
  fun runNext() { val task = synchronized(queue) { check(tasks.isNotEmpty()); tasks.removeFirst() }; task.run() }
  companion object { private val queue = ArrayDeque<Runnable>(); @JvmField val main = Handler(Looper.getMainLooper()) }
}
`,
  'HookRegistry.kt': `package h.Hchat.hooks.core
import java.util.concurrent.CancellationException
object HookRegistry {
  private var generation = 0L
  private val owner = ThreadLocal<String>()
  fun get() = this
  fun installationGeneration() = generation
  fun currentFeatureId(): String? = owner.get()
  fun isInstallationGenerationActive(value: Long) = value == generation
  fun <T> withFeature(id: String, action: () -> T): T { val old=owner.get(); owner.set(id); try{return action()}finally{if(old==null)owner.remove()else owner.set(old)} }
  fun <T> withInstallationGeneration(value: Long, isLeaseActive: () -> Boolean = { true }, action: () -> T): T {
    if(value != generation || !isLeaseActive()) throw CancellationException("cancelled")
    return action()
  }
}
`,
  'HLog.kt': `package h.Hchat.utils
object HLog { fun e(message: String, failure: Throwable? = null) {} }
`
};
for (const [file, source] of Object.entries(stubs)) {
  const target = path.join(temp, file);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, source);
}

const stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.20');
const compiler = [
  jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.20'),
  stdlib,
  jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.4.0'),
  jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.4.20'),
  jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
  jar('org.jetbrains', 'annotations', '13.0')
].join(path.delimiter);
const output = path.join(temp, 'classes');
fs.mkdirSync(output);
const compile = spawnSync(process.env.JAVA || 'java', [
  '-Xmx256m', '-cp', compiler, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
  '-no-stdlib', '-no-reflect', '-classpath', stdlib, '-d', output,
  ...Object.keys(stubs).map(file => path.join(temp, file)),
  path.join(root, 'app/src/main/java/h/Hchat/hooks/core/DexInstallScheduler.kt'),
  path.join(__dirname, 'SchedulerRetryRegression.kt')
], { cwd: root, encoding: 'utf8', timeout: 60_000 });
if (compile.stdout) process.stdout.write(compile.stdout);
if (compile.stderr) process.stderr.write(compile.stderr);
if (compile.error || compile.status !== 0) throw compile.error || new Error('scheduler harness compile failed');
const run = spawnSync(process.env.JAVA || 'java', [
  '-cp', [output, stdlib].join(path.delimiter), 'SchedulerRetryRegressionKt'
], { cwd: root, encoding: 'utf8', timeout: 30_000 });
if (run.stdout) process.stdout.write(run.stdout);
if (run.stderr) process.stderr.write(run.stderr);
if (run.error || run.status !== 0) throw run.error || new Error('scheduler failure injection failed');
