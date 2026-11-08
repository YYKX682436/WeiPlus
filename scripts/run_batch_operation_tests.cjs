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

try {
    run(process.env.JAVAC || 'javac', [
        '-encoding', 'UTF-8', '-d', output,
        'app/src/main/java/h/Hchat/hooks/api/conversation/WeChatConversationIdentity.java',
        'app/src/main/java/h/Hchat/hooks/api/contact/ContactLabelIds.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchSelectionPolicy.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchSelectionState.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchStatePolicy.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchTaskRunner.java',
        'scripts/tests/batch_operations/BatchOperationRegression.java'
    ]);
    run(process.env.JAVA || 'java', ['-cp', output, 'BatchOperationRegression']);

    const batchUi = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/ui/miuix/MiuixBatchOperationsPage.kt'), 'utf8');
    const picker = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/ui/miuix/MiuixPickerPages.kt'), 'utf8');
    const contactPickerStart = picker.indexOf('internal fun ContactPickerPage(');
    const contactPickerEnd = picker.indexOf('@Composable\ninternal fun FavoritePickerPage', contactPickerStart);
    const contactPicker = picker.slice(contactPickerStart, contactPickerEnd);
    const shared = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/ui/miuix/MiuixSharedComponents.kt'), 'utf8');
    const dexFinder = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/dexkit/DexFinder.java'), 'utf8');
    const groupRuntime = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/conversationgroup/ConversationGroupRuntime.kt'), 'utf8');
    const conversationApi = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/api/conversation/WeChatConversationApi.java'), 'utf8');
    const contactApi = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/api/contact/WeChatContactApi.java'), 'utf8');
    const executor = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/batch/BatchOperationExecutor.kt'), 'utf8');
    const manager = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/batch/BatchTaskManager.kt'), 'utf8');
    const moduleEntry = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/ModuleEntry.java'), 'utf8');

    assert.match(batchUi, /private enum class BatchPage \{ HOME, PICKER \}/,
        'batch operation must use a single picker followed by direct submission');
    assert.match(batchUi, /onConfirm = \{ submitSelection\(it\) \}/,
        'one confirm must submit the task directly');
    assert.match(batchUi, /BatchTaskSubmitResult\.STARTED -> \{[\s\S]*?已开始后台处理[\s\S]*?returnHome\(\)/,
        'accepted work must show a queued notice and return home immediately');
    assert.match(batchUi, /manager\.submit\(\s*BatchTaskRequest\(/,
        'the selector must submit to the process-owned task manager');
    assert.match(batchUi, /BatchOperation\.entries\.forEachIndexed/,
        'the home feature count and list must come from implemented operations');
    assert.doesNotMatch(batchUi, /ProgressIndicator|BatchPage\.RUNNING|BatchPage\.RESULTS|LaunchedEffect\([^\n]*task/i,
        'batch UI must not own a progress/result screen or execution coroutine');
    assert.match(batchUi, /BatchTaskStatus\.RUNNING, BatchTaskStatus\.STOPPING[\s\S]*?停止后续任务/,
        'the home page must retain an explicit stop-later control');
    assert.match(batchUi, /InfoRow\("成功"[\s\S]*?InfoRow\("失败"[\s\S]*?InfoRow\("跳过"[\s\S]*?InfoRow\("已提交／未确认"/,
        'the persisted in-process summary must keep each outcome distinct');
    assert.match(batchUi, /BatchChoiceRow\([\s\S]*?当前聊天[\s\S]*?全部好友与群聊/,
        'DND and pin must offer current-chat and all-friends/groups scopes');
    assert.match(batchUi, /BatchCandidateMode\.CURRENT_FRIENDS[\s\S]*?BatchCandidateMode\.ALL_FRIENDS/,
        'friend labels must offer current-chat friends and all friends');
    assert.match(batchUi, /BatchOperation\.PIN -> if \(scope == BatchScope\.CURRENT\)[\s\S]*?BatchCandidateMode\.HOME_CONVERSATIONS[\s\S]*?PINNABLE_FRIENDS_AND_GROUPS/,
        'pin candidates must be scoped to real conversations and disable missing rows');
    assert.doesNotMatch(batchUi, /InfoRow\("当前微信账号"|InputRow\(\s*title = "搜索"|Text\("当前操作/,
        'batch selectors must omit account, search and repeated operation copy');

    assert.match(contactPicker, /request\.hideSearch\) "" else query/,
        'hide-search batch selectors must not apply a hidden query');
    assert.match(contactPicker, /if \(!request\.hideSearch\) item/,
        'batch search field must not render');
    assert.match(contactPicker, /request\.flatBatchSelection\) batchSelectionOrder\.size/,
        'batch footer must count selections retained across scopes');
    assert.match(contactPicker, /BatchSelectionState\.selectedFirst[\s\S]*?batchOptionCache\[id\]/,
        'selected rows from earlier scopes must remain visible at the top');
    assert.match(contactPicker, /BatchSelectionState\.toggleVisible/,
        'select-all must toggle the currently visible result set');
    assert.match(contactPicker, /multiSelectConfirmPrefix != null -> "\$\{request\.multiSelectConfirmPrefix\}（\$selectedContactCount）"/,
        'batch selector must expose one direct confirm action with the total selection count');
    assert.match(shared, /conversations\?\.getNativeHomeConversationUsernames\(\)[\s\S]*?throw IllegalStateException\("读取微信首页原生会话列表失败"\)/,
        'chat scopes must fail closed if the native home query cannot be read');
    assert.match(shared, /BatchCandidateMode\.HOME_CONVERSATIONS ->[\s\S]*?nativeContactOptions\(eligible, api, conversations\)/,
        'hide candidates must be taken from the complete ordered native home list');
    assert.match(shared, /preserveNativeHomeOrder = nativeHomeConversationList \|\| batchCandidateMode in setOf\([\s\S]*?CURRENT_FRIENDS[\s\S]*?val sorted = if \(preserveNativeHomeOrder\) deduplicated/,
        'batch current-chat ranges must preserve the native WeChat home ordering');
    assert.match(shared, /getExistingConversationUsernames\(\)[\s\S]*?无会话记录；微信置顶会创建空会话记录/,
        'pin candidates without a conversation row must be disabled to avoid creating chats');
    assert.match(shared, /selectable = false[\s\S]*?disabledReason/,
        'disabled pin candidates must provide a visible reason');
    assert.equal((conversationApi.match(/public List<String> getNativeHomeConversationUsernames\(\)/g) || []).length, 1,
        'native home query wrapper must have one implementation');

    assert.match(manager, /Executors\.newSingleThreadExecutor[\s\S]*?isDaemon = true/,
        'the process manager must use a bounded serial daemon queue');
    assert.match(manager, /if \(current != null\) return BatchTaskSubmitResult\.BUSY/,
        'the process manager must reject overlapping tasks');
    assert.match(moduleEntry, /BatchTaskManager\.shutdownProcessManagerForHostInvalidation\(\)/,
        'host identity invalidation must stop future process submissions');
    assert.match(executor, /stopSignal\.isStopped\(\)[\s\S]*?isAccountCurrent/,
        'each operation must recheck stop and account state close to native submission');
    assert.match(executor, /setWechatDoNotDisturb\(id, request\.enabled\)/,
        'the selected DND state must reach WeChat native operations');
    assert.match(executor, /setWechatPlacedTop\(id, request\.enabled\)/,
        'pin and unpin must use the native placed-top path');
    assert.match(executor, /hideConversationFromHome\(id\)/,
        'hide must use WeChat native hide behavior');
    assert.match(executor, /setContactLabelMembershipsById\(/,
        'label batch tasks must enter the safe native bulk API');
    assert.match(executor, /ContactLabelMutationResult\.INVALID_TARGET ->\s*result\(BatchTaskRunner\.Outcome\.SKIPPED/,
        'a no-longer-valid friend must be counted as skipped');
    assert.match(executor, /BatchTaskRunner\.run\([\s\S]*?0L,/,
        'local operations must not use a fixed inter-item delay');

    assert.doesNotMatch(dexFinder, /nativeConversationActionLookupResolved/,
        'native locators must not mark all actions resolved before each method is found');
    assert.match(dexFinder, /conversationSetPlacedTopRetryAfterMs[\s\S]*?conversationUnsetPlacedTopRetryAfterMs[\s\S]*?conversationHideFromHomeRetryAfterMs/,
        'native action locators must track independent retry windows');
    assert.match(groupRuntime, /resolveNativeConversationHomeListQueryApi\(\)/,
        'conversation groups and batch selection must share the native query locator');

    const batchLabelMethod = methodBody(contactApi, 'public List<ContactLabelBatchItemResult> setContactLabelMembershipsById');
    assert.equal((batchLabelMethod.match(/hasContactLabelId\(/g) || []).length, 1,
        'the selected native label ID must be resolved once per batch');
    assert.match(batchLabelMethod, /LinkedList<Object> requestItems/,
        'the verified native label scene must carry a LinkedList of contact entries');
    assert.match(batchLabelMethod, /for \(LabelBatchChange change : changes\)[\s\S]*?buildModifyContactLabelItem\(ctor, change\.username, change\.labelIds\)/,
        'each friend must carry that friend\'s complete label ID list');
    assert.match(batchLabelMethod, /sendNativeContactScene\(scene\)/,
        'the batch list must submit through the existing native network scene');
    assert.match(batchLabelMethod, /getContactLabelMembershipStateById\(change\.username, targetId\)/,
        'each member status must be read back after the scene callback');
    assert.doesNotMatch(batchLabelMethod, /databaseApi\.update\(/,
        'batch label sync must never be faked through a local database write');
    assert.match(batchLabelMethod, /ContactRequestDispatch\.safeToFallbackToPerItem\(dispatch\)/,
        'individual fallback is allowed only when the bulk scene was not submitted');
    assert.match(contactApi, /if \(currentLabelIds == null\)[\s\S]*?读取好友原有标签失败，未提交修改/,
        'a failed label row read must block an overwrite request');
    assert.match(contactApi, /SERVER_ACCEPTED[\s\S]*?SUBMITTED_UNCONFIRMED[\s\S]*?SERVER_REJECTED/,
        'label outcomes must separate server acceptance, unknown submission and rejection');

    process.stdout.write('Batch UI, lifecycle, candidate and native API assertions passed\n');
} finally {
    fs.rmSync(output, { recursive: true, force: true });
}
