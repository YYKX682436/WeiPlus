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
    assert.match(api, /public Boolean hasContactLabelId\(String labelId\)/,
        'batch label preflight must resolve one label ID before iterating friends');
    assert.match(api, /public ContactLabelMutationResult setContactLabelMembershipById\([\s\S]*?readContactLabelIds\(username, labelIdsColumn\)[\s\S]*?ContactLabelIds\.setMembership\(currentLabelIds, targetId, enabled\)/,
        'each friend update must read only that row and preserve other label IDs');
    assert.match(api, /if \(currentLabelIds == null\)\s*\{[\s\S]*?return ContactLabelMutationResult\.NOT_SUBMITTED;/,
        'failed row reads must block requests instead of being treated as empty labels');
    assert.match(api, /if \(dispatch\.outcome == ContactRequestDispatch\.Outcome\.SERVER_ACCEPTED\)[\s\S]*?getContactLabelMembershipStateById\(username, targetId\)[\s\S]*?ContactLabelMutationResult\.CONFIRMED/,
        'server acceptance and final label state confirmation must remain separate');
    assert.match(api, /dispatch\.wasSubmitted\(\)[\s\S]*?ContactLabelMutationResult\.SUBMITTED_UNCONFIRMED/,
        'submitted requests without an accepted final state must be reported as unconfirmed');
    console.log('Contact API call-chain regression assertions passed');
} finally {
    fs.rmSync(output, { recursive: true, force: true });
}
