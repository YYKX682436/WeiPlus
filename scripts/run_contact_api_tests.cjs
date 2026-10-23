const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'hchat-contact-api-tests-'));
function run(command, args) {
    const result = spawnSync(command, args, { cwd: root, encoding: 'utf8', timeout: 60000 });
    process.stdout.write(result.stdout || '');
    process.stderr.write(result.stderr || '');
    if (result.error || result.status !== 0) throw result.error || new Error(`Command failed: ${command}`);
}

try {
    run(process.env.JAVAC || 'javac', ['-encoding', 'UTF-8', '-d', output,
        'app/src/main/java/h/Hchat/hooks/api/contact/ContactRequestDispatch.java',
        'app/src/main/java/h/Hchat/dexkit/RetryableLocatorState.java',
        'app/src/main/java/h/Hchat/dexkit/DexFinderCacheStore.java',
        'scripts/tests/contact_api/ContactDispatchRegression.java',
        'scripts/tests/contact_api/DexFinderCacheRegression.java']);
    run(process.env.JAVA || 'java', ['-cp', output,
        'h.Hchat.hooks.api.contact.ContactDispatchRegression']);
    run(process.env.JAVA || 'java', ['-cp', output,
        'h.Hchat.dexkit.DexFinderCacheRegression']);

    const api = fs.readFileSync(path.join(root,
        'app/src/main/java/h/Hchat/hooks/api/contact/WeChatContactApi.java'), 'utf8');
    assert.match(api, /result == null \|\| !result\.serverAccepted\(\)/,
        'remark persistence must require server acceptance');
    assert.match(api, /nativeResult\.serverAccepted\(\)/,
        'native label persistence must require server acceptance');
    assert.doesNotMatch(api, /modifyContactRemarkByNativeStorage/,
        'failed remark requests must not mutate local native storage');
    console.log('Contact API call-chain regression assertions passed');
} finally {
    fs.rmSync(output, { recursive: true, force: true });
}
