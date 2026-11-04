// Run the production upload method against a recording request/dispatcher seam.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/api/message/WeChatMessageApi.java'), 'utf8');
const start = source.indexOf('    public boolean uploadDeviceStep(long step) {');
const end = source.indexOf('\n    public boolean sendPat(', start);
if (start < 0 || end < 0) throw new Error('Production upload method not found');
const method = source.slice(start, end);
const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'hchat-step-boundary-'));
try {
    const java = `public class StepUploadBoundary {
        static class Finder { Object uploadDeviceStepCtor = new Object(); }
        static class Dispatcher { boolean result = true; int sends; Object last; boolean send(Object x) { last = x; sends++; return result; } }
        static class KavaReflector { static Object newInstance(Object ctor, Object... args) { constructed++; value = (Integer) args[4]; return new Object(); } }
        static int constructed, value;
        Finder dexFinder = new Finder(); Dispatcher networkDispatcher = new Dispatcher();
        void installNetworkHook() {} void log(String s) {} int todayStartSeconds() { return 1; }
        int nowSeconds() { return 2; } String buildDeviceInfoXml() { return ""; }
        ${method}
        static void check(boolean v) { if (!v) throw new AssertionError(); }
        public static void main(String[] args) {
            StepUploadBoundary api = new StepUploadBoundary();
            check(!api.uploadDeviceStep(Long.MIN_VALUE)); check(!api.uploadDeviceStep(-1));
            check(!api.uploadDeviceStep(0)); check(!api.uploadDeviceStep(2147483648L));
            check(!api.uploadDeviceStep(Long.MAX_VALUE)); check(constructed == 0 && api.networkDispatcher.sends == 0);
            check(api.uploadDeviceStep(1)); check(value == 1);
            check(api.uploadDeviceStep(Integer.MAX_VALUE)); check(value == Integer.MAX_VALUE);
            api.networkDispatcher.result = false; check(!api.uploadDeviceStep(99));
            check(constructed == 3 && api.networkDispatcher.sends == 3);
            check(!api.uploadDeviceStep(77, request -> false));
            check(constructed == 4 && api.networkDispatcher.sends == 3);
            api.networkDispatcher.result = true;
            Object[] captured = new Object[1];
            check(api.uploadDeviceStep(42, request -> { captured[0] = request; return true; }));
            check(captured[0] == api.networkDispatcher.last && value == 42 && api.networkDispatcher.sends == 4);
            System.out.println("Step upload boundary and enqueue failure checks passed");
        }
    }`;
    const file = path.join(directory, 'StepUploadBoundary.java');
    fs.writeFileSync(file, java);
    for (const [cmd, args] of [[process.env.JAVAC || 'javac', ['-encoding', 'UTF-8', file]],
        [process.env.JAVA || 'java', ['-cp', directory, 'StepUploadBoundary']]]) {
        const result = spawnSync(cmd, args, { encoding: 'utf8', timeout: 60000 });
        process.stdout.write(result.stdout || ''); process.stderr.write(result.stderr || '');
        if (result.error || result.status !== 0) throw result.error || new Error(`${cmd} failed`);
    }
} finally { fs.rmSync(directory, { recursive: true, force: true }); }
