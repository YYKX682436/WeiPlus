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

const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'weiplus-startup-recovery-'));
process.once('exit', () => fs.rmSync(temp, { recursive: true, force: true }));
const sources = {
  'api.kt': `package h.Hchat.hooks.core
class ApiSubscription(private val bus: ApiBus, val listener: () -> Unit) {
  var active = true; private set
  fun unsubscribe() { if (active) { active = false; bus.listeners.remove(this) } }
}
class ApiBus {
  val listeners = arrayListOf<ApiSubscription>()
  fun subscribe(listener: () -> Unit) = ApiSubscription(this, listener).also(listeners::add)
  fun post() = listeners.toList().forEach { if (it.active) it.listener() }
}
`,
  'stubs.kt': `package h.Hchat.hooks.core
class FeatureContext(val publicApi: ApiBus = ApiBus()) { fun eventBus() = h.Hchat.event.EventBus() }
interface Feature {
  enum class InstallationPolicy { PREINSTALL_HOOKS, ENABLED_ONLY }
  fun featureId(): String
  fun name(): String = featureId()
  fun isRequiredCore(): Boolean = false
  fun isEnabled(context: FeatureContext): Boolean = true
  fun installationPolicy(): InstallationPolicy = InstallationPolicy.PREINSTALL_HOOKS
  fun onInit(context: FeatureContext) {}
  fun install(context: FeatureContext)
  fun onDestroy(context: FeatureContext) {}
}
object HookRegistry {
  private var generation = 0L
  fun get() = this
  fun installationGeneration() = generation
  fun currentFeatureId(): String? = null
  fun isInstallationGenerationActive(value: Long) = value == generation
  fun <T> withInstallationGeneration(value: Long, action: () -> T): T = action()
  fun <T> withFeature(featureId: String, action: () -> T): T = action()
  fun featuresWithChangedClasses(loader: ClassLoader): Set<String> = emptySet()
  fun unhookFeature(featureId: String) {}
  fun unhookAll() { generation++ }
}
object FeatureInstallDecision {
  fun shouldInstall(enabledOnly: Boolean, isEnabled: () -> Boolean, onEnabledCheckError: (Throwable) -> Unit): Boolean =
    if (!enabledOnly) true else try { isEnabled() } catch (failure: Throwable) { onEnabledCheckError(failure); false }
}
object DexInstallScheduler { fun markDexBridgeReady() {}; fun cancelAll() {}; fun cancelFeature(featureId: String) {}; fun pendingFeatureOwners(): Set<String> = emptySet() }
`,
  'events.kt': `package h.Hchat.event
class EventBus { fun post(event: Any) {} }
object Events { data class FeatureInstalled(val name: String) }
`,
  'projection.kt': `package h.Hchat.hooks.items.conversationgroup
object ConversationGroupHomeProjection { fun resetAfterUnhookAll() {} }
`,
  'log.kt': `package h.Hchat.utils
object HLog { fun e(message: String, failure: Throwable? = null) {} }
`
};
for (const [file, source] of Object.entries(sources)) fs.writeFileSync(path.join(temp, file), source);

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
  ...Object.keys(sources).map(file => path.join(temp, file)),
  path.join(root, 'app/src/main/java/h/Hchat/hooks/core/FeatureManager.kt'),
  path.join(root, 'scripts/tests/startup_recovery/FeatureManagerRecovery.kt')
], { cwd: root, encoding: 'utf8', timeout: 60_000 });
if (compile.stdout) process.stdout.write(compile.stdout);
if (compile.stderr) process.stderr.write(compile.stderr);
if (compile.error || compile.status !== 0) throw compile.error || new Error('FeatureManager harness compile failed');
const run = spawnSync(process.env.JAVA || 'java', [
  '-cp', [output, stdlib].join(path.delimiter), 'FeatureManagerRecoveryKt'
], { cwd: root, encoding: 'utf8', timeout: 30_000 });
if (run.stdout) process.stdout.write(run.stdout);
if (run.stderr) process.stderr.write(run.stderr);
if (run.error || run.status !== 0) throw run.error || new Error('FeatureManager failure injection failed');
