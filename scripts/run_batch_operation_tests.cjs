const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '..');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'hchat-batch-operation-tests-'));

function run(command, args) {
    const result = spawnSync(command, args, { cwd: root, encoding: 'utf8', timeout: 60000 });
    process.stdout.write(result.stdout || '');
    process.stderr.write(result.stderr || '');
    if (result.error || result.status !== 0) {
        throw result.error || new Error(`Command failed: ${command}`);
    }
}

try {
    run(process.env.JAVAC || 'javac', [
        '-encoding', 'UTF-8', '-d', output,
        'app/src/main/java/h/Hchat/hooks/api/conversation/WeChatConversationIdentity.java',
        'app/src/main/java/h/Hchat/hooks/api/contact/ContactLabelIds.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchSelectionPolicy.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchStatePolicy.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchTaskLifecycle.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchTaskRunner.java',
        'scripts/tests/batch_operations/BatchOperationRegression.java'
    ]);
    run(process.env.JAVA || 'java', ['-cp', output, 'BatchOperationRegression']);

    const batchUi = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/ui/miuix/MiuixBatchOperationsPage.kt'), 'utf8');
    const picker = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/ui/miuix/MiuixPickerPages.kt'), 'utf8');
    const shared = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/ui/miuix/MiuixSharedComponents.kt'), 'utf8');
    const dexFinder = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/dexkit/DexFinder.java'), 'utf8');
    const groupRuntime = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/conversationgroup/ConversationGroupRuntime.kt'), 'utf8');
    const conversationApi = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/api/conversation/WeChatConversationApi.java'), 'utf8');
    const contactApi = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/api/contact/WeChatContactApi.java'), 'utf8');

    assert.match(batchUi, /private enum class BatchPage \{ HOME, PICKER, RUNNING, RESULTS \}/,
        'batch flow must not reintroduce configure or confirm pages');
    assert.match(batchUi, /DisposableEffect\(taskSession\)\s*\{\s*val ownedSession = taskSession\s*onDispose \{ taskLifecycle\.stop\(ownedSession\) \}/,
        'effect cleanup must stop only the session captured when it was created');
    assert.match(batchUi, /onConfirm = \{ startTask\(it\) \}/,
        'the picker confirm action must directly begin execution');
    assert.match(batchUi, /override fun onStart[\s\S]*?activeId = id[\s\S]*?override fun onResult[\s\S]*?completedCount = index/,
        'progress must count a target only after its result callback');
    assert.match(batchUi, /BatchOperationChoices[\s\S]*?当前操作/,
        'operation choices and the selected operation must be visible on the picker');
    assert.match(batchUi, /BatchFeature\.DO_NOT_DISTURB -> \{[\s\S]*?onEnabledChange\(true\)[\s\S]*?onEnabledChange\(false\)[\s\S]*?setWechatDoNotDisturb\(id, enabled\)/,
        'DND open/close selection must be passed to the native operation');
    assert.match(batchUi, /BatchFeature\.PIN -> \{[\s\S]*?onEnabledChange\(true\)[\s\S]*?onEnabledChange\(false\)[\s\S]*?setWechatPlacedTop\(id, enabled\)/,
        'pin/unpin selection must be passed to the native operation');
    assert.match(batchUi, /setContactLabelMembershipById\(id, labelId, enabled\)/,
        'append/remove and the chosen label ID must reach the per-friend native request');
    assert.match(picker, /request\.multiSelectConfirmPrefix != null -> "\$\{request\.multiSelectConfirmPrefix\}（\$selectedContactCount）"/,
        'batch picker must show one direct confirmation with selected count');
    assert.match(picker, /primaryEnabled = batchConfirmEnabled/,
        'batch confirmation must be disabled without a valid operation and selection');
    assert.match(shared, /conversations\?\.getNativeHomeConversationUsernames\(\)/,
        'conversation pickers must read the native home list');
    assert.match(shared, /eligibleNativeIds\.forEach \{ id ->[\s\S]*?ContactOption\(/,
        'native home rows must survive even without a matching contact row');
    assert.equal((conversationApi.match(/public List<String> getNativeHomeConversationUsernames\(\)/g) || []).length, 1,
        'native home query wrapper must have one implementation');
    assert.doesNotMatch(dexFinder, /nativeConversationActionLookupResolved/,
        'native locators must not mark all actions resolved before each method is found');
    assert.match(dexFinder, /conversationSetPlacedTopRetryAfterMs[\s\S]*?conversationUnsetPlacedTopRetryAfterMs[\s\S]*?conversationHideFromHomeRetryAfterMs/,
        'native action locators must track independent retry windows');
    assert.match(groupRuntime, /resolveNativeConversationHomeListQueryApi\(\)/,
        'conversation groups and batch selection must share the native query locator');
    assert.match(contactApi, /getContactLabelMembershipStateById[\s\S]*?readContactLabelIds\(username, labelIdsColumn\)/,
        'label batch updates must read only the selected contact row');
    assert.match(contactApi, /SERVER_ACCEPTED[\s\S]*?SUBMITTED_UNCONFIRMED[\s\S]*?SERVER_REJECTED/,
        'label results must separate request submission and server acceptance');
    process.stdout.write('Batch UI and API contract assertions passed\n');
} finally {
    fs.rmSync(output, { recursive: true, force: true });
}
