const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'hchat-contact-api-tests-'));
function methodBody(source, signature) {
    const start = source.indexOf(signature);
    assert.notEqual(start, -1, `missing method: ${signature}`);
    const open = source.indexOf('{', start);
    let depth = 0;
    for (let index = open; index < source.length; index++) {
        if (source[index] === '{') depth++;
        else if (source[index] === '}' && --depth === 0) return source.slice(open, index + 1);
    }
    throw new Error(`unclosed method: ${signature}`);
}
function run(command, args) {
    const result = spawnSync(command, args, { cwd: root, encoding: 'utf8', timeout: 60000 });
    process.stdout.write(result.stdout || '');
    process.stderr.write(result.stderr || '');
    if (result.error || result.status !== 0) throw result.error || new Error(`Command failed: ${command}`);
}

try {
    run(process.env.JAVAC || 'javac', ['-encoding', 'UTF-8', '-d', output,
        'app/src/main/java/h/Hchat/hooks/api/contact/ContactRequestDispatch.java',
        'app/src/main/java/h/Hchat/hooks/api/contact/NativeSceneResponse.java',
        'app/src/main/java/h/Hchat/hooks/api/contact/WeChatNativeMutationResult.java',
        'app/src/main/java/h/Hchat/hooks/api/contact/WeChatPermissionResultPolicy.java',
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
    const sceneDispatch = fs.readFileSync(path.join(root,
        'app/src/main/java/h/Hchat/hooks/api/contact/NativeSceneDispatch.java'), 'utf8');
    const sceneResponse = fs.readFileSync(path.join(root,
        'app/src/main/java/h/Hchat/hooks/api/contact/NativeSceneResponse.java'), 'utf8');
    const sharedPicker = fs.readFileSync(path.join(root,
        'app/src/main/java/h/Hchat/ui/miuix/MiuixSharedComponents.kt'), 'utf8');
    assert.match(api, /result == null \|\| !result\.serverAccepted\(\)/,
        'remark persistence must require server acceptance');
    assert.match(api, /nativeResult\.serverAccepted\(\)/,
        'native label persistence must require server acceptance');
    assert.doesNotMatch(api, /modifyContactRemarkByNativeStorage/,
        'failed remark requests must not mutate local native storage');
    assert.match(api, /public Boolean hasContactLabelId\(String labelId\)/,
        'batch label preflight must resolve one label ID before iterating friends');
    assert.match(api, /public static final class ContactLabelListResult[\s\S]*?public final boolean success[\s\S]*?public final List<ContactLabelBean> labels/,
        'label membership snapshots must expose read success separately from empty lists');
    assert.match(api, /public ContactLabelListResult getContactLabelListResult\(\)[\s\S]*?labelCursor == null\) return failedContactLabelList[\s\S]*?contactsCursor == null\) return failedContactLabelList[\s\S]*?new ContactLabelListResult\(true, labels, ""\)/,
        'a failed tag/member query must not look like a valid empty tag or membership list');
    assert.match(sharedPicker, /labelSnapshot\.labels\.firstOrNull[\s\S]*?userNameList/,
        'removal candidate loading must reuse the complete tag member relationship snapshot');
    assert.match(api, /public ContactLabelMutationResult setContactLabelMembershipById\([\s\S]*?readContactLabelIds\(username, labelIdsColumn\)[\s\S]*?ContactLabelIds\.setMembership\(currentLabelIds, targetId, enabled\)/,
        'each friend update must read only that row and preserve other label IDs');
    assert.match(api, /if \(currentLabelIds == null\)\s*\{[\s\S]*?return ContactLabelMutationResult\.NOT_SUBMITTED;/,
        'failed row reads must block requests instead of being treated as empty labels');
    assert.match(api, /if \(dispatch\.outcome == ContactRequestDispatch\.Outcome\.SERVER_ACCEPTED\)[\s\S]*?getContactLabelMembershipStateById\(username, targetId\)[\s\S]*?ContactLabelMutationResult\.CONFIRMED/,
        'server acceptance and final label state confirmation must remain separate');
    assert.match(api, /dispatch\.wasSubmitted\(\)[\s\S]*?ContactLabelMutationResult\.SUBMITTED_UNCONFIRMED/,
        'submitted requests without an accepted final state must be reported as unconfirmed');
    assert.match(sceneDispatch, /NativeSceneResponse\.fromArguments\(args\)/,
        'six-argument native scene callbacks must use the verified argument decoder');
    assert.doesNotMatch(sceneDispatch, /args\[0\].*args\[1\]/,
        'native scene dispatch must not interpret netId and errType as the error pair');
    assert.match(sceneResponse, /args\[1\].*args\[2\]/,
        'the error decoder must read errType and errCode from the second and third integer arguments');
    const onlyChatMethod = methodBody(api, 'public WeChatNativeMutationResult setFriendPermission');
    assert.match(onlyChatMethod, /dispatch\.outcome == ContactRequestDispatch\.Outcome\.SERVER_REJECTED[\s\S]*?WeChatPermissionResultPolicy\.classify/,
        'explicit server rejection must be handled before final local state can confirm a permission change');
    const snsMethod = methodBody(api, 'private WeChatNativeMutationResult setSnsTagPermission');
    assert.match(snsMethod, /dispatch\.outcome == ContactRequestDispatch\.Outcome\.SERVER_REJECTED[\s\S]*?WeChatPermissionResultPolicy\.classify/,
        'SNS permission rejection must also take precedence over local tag state');
    assert.match(api, /WeChatPermissionResultPolicy\.classify\(/,
        'friend permission status must use the production server-acceptance and state-read classifier');
    console.log('Contact API call-chain regression assertions passed');
} finally {
    fs.rmSync(output, { recursive: true, force: true });
}
