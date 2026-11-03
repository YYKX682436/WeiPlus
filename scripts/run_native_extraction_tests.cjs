const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { prepare } = require('./lib/api102.cjs');
const root = path.resolve(__dirname, '..');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'weiplus-native-extraction-'));
try {
  fs.mkdirSync(path.join(output, 'android/os'), { recursive: true });
  const processFixture = path.join(output, 'android/os/Process.java');
  fs.writeFileSync(processFixture, 'package android.os; public class Process { public static int myPid(){return 123;} public static boolean is64Bit(){return true;} }');
  const cp = prepare(output).classpath;
  const files = [processFixture,
    'app/src/main/java/h/Hchat/hooks/core/XposedRuntime.java',
    'app/src/main/java/h/Hchat/hooks/api/runtime/HostApkPathDecision.java',
    'app/src/main/java/h/Hchat/utils/HLog.java',
    'app/src/main/java/h/Hchat/loader/utils/NativeLoadCache.java',
    'app/src/main/java/h/Hchat/loader/utils/NativeLibraryLoader.java',
    'scripts/tests/startup/NativeExtractionRegression.java'];
  for (const [command, args] of [
    ['javac', ['-cp', cp, '-d', output, ...files]],
    ['java', ['-cp', output + path.delimiter + cp, 'NativeExtractionRegression', output]],
  ]) {
    const result = spawnSync(command, args, { cwd: root, encoding: 'utf8' });
    if (result.stdout) process.stdout.write(result.stdout);
    if (result.stderr) process.stderr.write(result.stderr);
    if (result.error || result.status !== 0) throw result.error || new Error(`${command}: ${result.status}`);
  }
} finally {
  fs.rmSync(output, { recursive: true, force: true });
}
