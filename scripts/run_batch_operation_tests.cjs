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
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchCandidateLoadGate.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchSelectionPolicy.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchSelectionState.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchStatePolicy.java',
        'app/src/main/java/h/Hchat/hooks/api/contact/WeChatContactTypeState.java',
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
    const messageStore = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/api/message/WeChatMessageStoreApi.java'), 'utf8');
    const zombieFeature = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/zombiecheck/ZombieCheckFeature.kt'), 'utf8');
    const chatroomApi = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/api/contact/WeChatChatroomApi.java'), 'utf8');
    const databaseApi = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/api/runtime/WeChatDatabaseApi.java'), 'utf8');
    const executor = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/batch/BatchOperationExecutor.kt'), 'utf8');
    const manager = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/batch/BatchTaskManager.kt'), 'utf8');
    const moduleEntry = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/ModuleEntry.java'), 'utf8');

    assert.match(batchUi, /private enum class BatchPage \{ HOME, PICKER \}/,
        'batch operation must use a single picker followed by direct submission');
    assert.match(batchUi, /onConfirm = \{ submitSelection\(it\) \}/,
        'one confirm must submit the task directly');
    assert.match(batchUi, /BatchTaskSubmitResult\.STARTED -> \{\s*returnHome\(\)/,
        'accepted work must return home immediately without a progress page');
    assert.doesNotMatch(batchUi, /已开始后台处理|最近一次结果|InfoRow\("成功"|InfoRow\("失败"|InfoRow\("跳过"|已提交／未确认/,
        'the home page must not show a start toast or permanent result summary');
    assert.match(batchUi, /manager\.submit\(\s*BatchTaskRequest\(/,
        'the selector must submit to the process-owned task manager');
    assert.match(batchUi, /BatchOperation\.entries\.forEachIndexed/,
        'the home feature count and list must come from implemented operations');
    assert.match(manager, /DELETE_CHATS\("批量删除聊天"\)[\s\S]*?DELETE_FRIENDS\("批量删除好友"\)/,
        'the production entry list must add both requested delete operations');
    assert.match(batchUi, /BatchOperation\.DELETE_CHATS, BatchOperation\.DELETE_FRIENDS -> "删除"[\s\S]*?BatchDeleteConfirmDialog/,
        'deletion uses a counted footer and one final consequence confirmation');
    assert.match(batchUi, /将删除所选[\s\S]*?聊天记录[\s\S]*?好友关系与群成员关系会保留[\s\S]*?此操作不可恢复/,
        'chat deletion confirmation must state message cleanup and preserved relationships');
    assert.match(batchUi, /BatchOperation\.DELETE_CHATS -> BatchCandidateMode\.HOME_CONVERSATIONS[\s\S]*?BatchOperation\.DELETE_FRIENDS -> BatchCandidateMode\.ALL_FRIENDS/,
        'delete candidates must use the ordered home chats and valid friends data sets');
    for (const operation of ['STAR_FRIEND', 'FRIEND_PERMISSION', 'GROUP_SAVE_TO_CONTACTS', 'GROUP_NICKNAME', 'DELETE_CHATS', 'DELETE_FRIENDS']) {
        assert.match(manager, new RegExp(`\\b${operation}\\b`), `missing production task kind ${operation}`);
        assert.match(executor, new RegExp(`BatchOperation\\.${operation}`), `executor does not route ${operation}`);
    }
    assert.doesNotMatch(batchUi, /ProgressIndicator|BatchPage\.RUNNING|BatchPage\.RESULTS|LaunchedEffect\([^\n]*task/i,
        'batch UI must not own a progress/result screen or execution coroutine');
    assert.match(batchUi, /BatchTaskStatus\.RUNNING, BatchTaskStatus\.STOPPING[\s\S]*?停止后续任务/,
        'the home page must retain an explicit stop-later control');
    assert.doesNotMatch(batchUi, /BatchChoiceRow|ContactFilterChip/,
        'batch configuration must use compact existing Miuix setting rows');
    assert.match(batchUi, /PopupChoiceRow\([\s\S]*?"开启"[\s\S]*?"关闭"[\s\S]*?当前聊天[\s\S]*?全部好友与群聊/,
        'DND choices and scope must be visible in compact Miuix rows');
    assert.match(batchUi, /PopupChoiceRow\([\s\S]*?"置顶"[\s\S]*?"取消置顶"[\s\S]*?当前聊天[\s\S]*?全部好友与群聊/,
        'pin choices and scope must be visible in compact Miuix rows');
    assert.match(batchUi, /title = "目标标签"[\s\S]*?title = "标签操作"/,
        'friend label selection and append/remove controls must be visible');
    assert.match(batchUi, /BatchOperation\.STAR_FRIEND -> PopupChoiceRow[\s\S]*?设为星标朋友[\s\S]*?取消星标朋友/,
        'star enable and disable must be explicit compact choices');
    assert.match(batchUi, /title = "设置朋友权限"[\s\S]*?BatchFriendAccessChoice\.entries[\s\S]*?title = "不让他（她）看"[\s\S]*?title = "不看他（她）"/,
        'friend permission must expose the three native control rows in the requested order');
    assert.match(manager, /BatchFriendAccessChoice[\s\S]*?UNCHANGED\("不修改"\)[\s\S]*?NORMAL\("聊天、朋友圈、微信运动等"\)[\s\S]*?ONLY_CHAT\("仅聊天"\)/,
        'friend access choices must include no-change and map standard access/only-chat separately');
    assert.match(batchUi, /BatchPermissionToggle\.entries\.map[\s\S]*?friendPermissionSelection\.dontLetSee[\s\S]*?BatchPermissionToggle\.entries\.map[\s\S]*?friendPermissionSelection\.dontSee/,
        'both Moments controls must include their default no-change choice');
    assert.match(batchUi, /friendPermissionSelection\.actions\(\)\.isNotEmpty\(\)/,
        'friend permission cannot submit while all three rows remain no-change');
    assert.match(batchUi, /BatchOperation\.GROUP_SAVE_TO_CONTACTS -> PopupChoiceRow[\s\S]*?保存[\s\S]*?取消保存/,
        'group save and unsave must be explicit compact choices');
    assert.match(batchUi, /BatchOperation\.GROUP_NICKNAME -> InputRow[\s\S]*?value = groupNickname/,
        'group nickname must be entered once in the shared picker header');
    assert.match(batchUi, /BatchOperation\.STAR_FRIEND -> if \(enabled\)[\s\S]*?BatchCandidateMode\.ALL_FRIENDS[\s\S]*?BatchCandidateMode\.STARRED_FRIENDS/,
        'star uses all valid friends and unstar uses only the starred-friend candidate mode');
    assert.match(batchUi, /BatchOperation\.FRIEND_PERMISSION -> BatchCandidateMode\.ALL_FRIENDS[\s\S]*?GROUP_SAVE_TO_CONTACTS, BatchOperation\.GROUP_NICKNAME -> BatchCandidateMode\.ALL_GROUPS/,
        'permission operations use friends and group operations use groups');
    assert.match(batchUi, /BatchOperation\.STAR_FRIEND -> enabled\.toString\(\)/,
        'switching between star and unstar resets the previous selection');
    assert.match(batchUi, /validGroupNickname\(groupNickname\)/,
        'invalid empty or overlength group nicknames must not enable submission');
    assert.doesNotMatch(batchUi, /当前聊天中的好友|全部好友"\s*,\s*BatchScope/,
        'friend label management must not offer an unrelated chat-scope switch');
    assert.match(batchUi, /BatchOperation\.PIN -> if \(scope == BatchScope\.CURRENT\)[\s\S]*?BatchCandidateMode\.HOME_CONVERSATIONS[\s\S]*?PINNABLE_FRIENDS_AND_GROUPS/,
        'pin candidates must be scoped to real conversations and disable missing rows');
    assert.match(batchUi, /BatchOperation\.FRIEND_LABEL -> if \(enabled\)[\s\S]*?ALL_FRIENDS[\s\S]*?ALL_FRIENDS_WITH_LABEL/,
        'append must use all friends while removal uses only members of the chosen label');
    assert.doesNotMatch(batchUi, /hideSearch\s*=\s*true/,
        'all batch operations must retain the shared picker search');
    assert.doesNotMatch(batchUi, /InfoRow\("当前微信账号"|InputRow\(\s*title = "搜索"|Text\("当前操作/,
        'batch selectors must omit account, search and repeated operation copy');

    assert.match(contactPicker, /if \(!request\.hideSearch\) item/,
        'the common picker search remains available for all eight batch operations');
    assert.match(contactPicker, /\.filter \{ it\.matchesSearch\(lower\) \}/,
        'picker search must filter by the contact aliases and IDs supported by ContactOption');
    assert.match(contactPicker, /request\.flatBatchSelection\) batchSelectionOrderForUi\.size/,
        'batch footer must count selections retained across scopes');
    assert.match(contactPicker, /BatchSelectionState\.selectedOutsideMatches[\s\S]*?selectedBatchContacts \+ batchMatchingContacts/,
        'selected rows that do not match the search must be separate from matching results');
    assert.match(contactPicker, /PickerSectionHeader\(text = "已选[\s\S]*?PickerSectionHeader\(text = "匹配结果/,
        'the UI must label the retained selection and actual search results separately');
    assert.match(contactPicker, /BatchSelectionState\.selectedFirst\(filteredContacts\.map \{ it\.id \}, batchSelectionOrderForUi\)/,
        'selected search matches must lead the actual filtered result list');
    assert.match(contactPicker, /BatchSelectionState\.toggleVisible/,
        'select-all must toggle the currently visible result set');
    assert.match(contactPicker, /batchSelectionResetKey[\s\S]*?shouldResetForKeyChange[\s\S]*?batchOptionCache\.clear\(\)/,
        'changing tag or add/remove mode must clear stale selected members');
    assert.match(contactPicker, /BatchCandidateLoadGate[\s\S]*?loadGate\.isCurrent\(generation\)/,
        'stale list reads after quick scope/tag changes or page disposal must be discarded');
    assert.match(contactPicker, /LaunchedEffect\(request, candidateLoadToken\)/,
        'a changed tag or candidate mode must load a distinct candidate snapshot');
    assert.match(contactPicker, /multiSelectConfirmPrefix != null -> "\$\{request\.multiSelectConfirmPrefix\}（\$selectedContactCount）"/,
        'batch selector must expose one direct confirm action with the total selection count');
    assert.match(contactPicker, /request\.confirmEnabled\(\) && selectedContactCount > 0 && !candidateLoading && error\.isBlank\(\) && candidateStateFresh/,
        'a failed or pending candidate read must disable task submission');
    assert.match(contactPicker, /BatchCandidateMode\.ALL_FRIENDS_WITH_LABEL[\s\S]*?该标签下没有有效好友/,
        'an empty successful removal membership query must have an empty-state message');
    assert.match(shared, /conversations\?\.getNativeHomeConversationUsernames\(\)[\s\S]*?throw IllegalStateException\("读取微信首页原生会话列表失败"\)/,
        'chat scopes must fail closed if the native home query cannot be read');
    assert.match(shared, /BatchCandidateMode\.HOME_CONVERSATIONS ->[\s\S]*?nativeContactOptions\(eligible, api, conversations\)/,
        'hide candidates must be taken from the complete ordered native home list');
    assert.match(shared, /getContactLabelListResult\(\)[\s\S]*?BatchCandidateMode\.ALL_FRIENDS_WITH_LABEL ->[\s\S]*?filterFriendLabelMembers/,
        'remove-tag candidates must come from a successfully-read tag membership snapshot intersected with valid friends');
    assert.match(shared, /getFriendsResult\(\)[\s\S]*?读取有效好友失败/,
        'friend candidates must distinguish a successful empty list from database failure');
    assert.match(shared, /BatchCandidateMode\.STARRED_FRIENDS ->[\s\S]*?WeChatContactTypeState\.STARRED_FRIEND[\s\S]*?filterStarredFriendCandidates/,
        'unstar candidates must be filtered from the successful valid-friend result using type bit 0x40');
    assert.match(shared, /BatchCandidateMode\.STARRED_FRIENDS,/,
        'starred candidate loading must share the database success state instead of treating a read failure as empty');
    assert.match(shared, /getGroupsResult\(\)[\s\S]*?读取有效群聊失败/,
        'group candidates must distinguish a successful empty list from database failure');
    assert.match(shared, /!labelSnapshot\.success[\s\S]*?读取好友标签成员失败/,
        'a failed membership read must produce an error distinct from an empty membership result');
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
    assert.match(manager, /FRIEND_PERMISSION && request\.friendPermissionSelection\.actions\(\)\.isEmpty\(\)/,
        'the manager must reject a friend-permission task with all rows set to no-change');
    assert.match(manager, /FRIEND_PERMISSION && request\.friendPermissionSelection\.actions\(\)\.isEmpty\(\)/,
        'the task manager must reject an all-no-change friend-permission request');
    assert.match(manager, /completionNotifier\.notify\(snapshot\)/,
        'the process manager must send one completion notification after storing the final report');
    assert.match(manager, /shouldNotify = !shutDown/,
        'host runtime invalidation must suppress stale completion feedback');
    const notifier = fs.readFileSync(path.join(root, 'app/src/main/java/h/Hchat/hooks/items/batch/BatchTaskCompletionToastNotifier.kt'), 'utf8');
    assert.match(notifier, /WeChatApis\.notifyApi\(\)\?\.showToast/,
        'completion feedback must use the host notification API without retaining a page');
    assert.match(notifier, /report\.success == report\.total[\s\S]*?report\.failed == 0[\s\S]*?report\.unconfirmed == 0/,
        'success toast must require confirmed success for every selected item');
    assert.match(notifier, /已停止后续操作[\s\S]*?批量操作部分完成[\s\S]*?未确认/,
        'stop, partial failure and unconfirmed outcomes must not be reported as full success');
    assert.match(moduleEntry, /BatchTaskManager\.shutdownProcessManagerForHostInvalidation\(\)/,
        'host identity invalidation must stop future process submissions');
    assert.doesNotMatch(executor, /Dispatchers\.Main|Looper\.getMainLooper/,
        'native operations must stay on the bounded worker and let WeChat marshal its observers');
    assert.match(executor, /stopSignal\.isStopped\(\)[\s\S]*?isAccountCurrent/,
        'each operation must recheck stop and account state close to native submission');
    assert.match(executor, /setWechatDoNotDisturb\(id, request\.enabled\)/,
        'the selected DND state must reach WeChat native operations');
    assert.match(executor, /setWechatPlacedTop\(id, request\.enabled\)/,
        'pin and unpin must use the native placed-top path');
    assert.match(executor, /hideConversationFromHome\(id\)/,
        'hide must use WeChat native hide behavior');
    const deleteChatMethod = methodBody(executor, 'private fun deleteChat(');
    assert.ok(deleteChatMethod.indexOf('clearConversationMessagesAndWait') < deleteChatMethod.indexOf('deleteConversation(id)'),
        'chat index removal must happen only after confirmed native message cleanup');
    assert.match(deleteChatMethod, /getNativeHomeConversationUsernames\(\)[\s\S]*?home\.contains\(id\)/,
        'a chat must still be in the current root home list when its delete begins');
    const deleteFriendMethod = methodBody(executor, 'private fun deleteFriend(');
    assert.match(deleteFriendMethod, /submitDeleteFriendWithResult\(id, true\)[\s\S]*?awaitNativeResult[\s\S]*?friendNativeResult\(nativeResult\)[\s\S]*?hasConversationMessagesState\(id\)/,
        'friend deletion must distinguish a native rejection, a missing response and cleared message rows');
    assert.match(contactApi, /resolveDeleteContactServiceApi\(\)[\s\S]*?KavaReflector\.invokeOrThrow\(method, service, target, clearRecord\)/,
        'native DeleteContactService must live in the common contact API with its clear-record argument');
    assert.match(contactApi, /submitDeleteFriendWithResult[\s\S]*?HookRegistry\.get\(\)\.hook[\s\S]*?request\.complete\(\(\(Number\) args\[1\]\)\.intValue\(\)\)/,
        'the common contact API must capture the native response code for batch outcome classification');
    assert.match(zombieFeature, /submitDeleteFriend\(wxid, shouldClearRecord\(wxid, clearRecord\)\)/,
        'zombie-check deletion must reuse the common native entry');
    assert.match(messageStore, /stageOne\.await[\s\S]*?hasConversationMessagesState\(target\)[\s\S]*?ConversationClearResult\.COMPLETED/,
        'message clear must await native stage 1 and confirm the message table is empty');
    assert.match(executor, /setStarredFriend\([\s\S]*?setFriendPermission\([\s\S]*?setGroupSavedToContacts\([\s\S]*?setMyGroupNickname\(/,
        'the four new actions must enter their public WeChat APIs');
    assert.match(executor, /BatchFriendPermissionExecution\.executeOne\([\s\S]*?request\.friendPermissionSelection[\s\S]*?setFriendPermission\(/,
        'the selected friend permission plan must run through the serial production executor');
    assert.match(executor, /BatchFriendPermissionExecution[\s\S]*?Outcome\.SERVER_REJECTED[\s\S]*?Outcome\.READ_FAILED/,
        'partial permission server rejection and state-read failures must remain distinguishable');
    assert.match(executor, /unconfirmed \|\| \(interrupted && nativeRequestMayHaveBeenSent\)/,
        'a partially executed permission plan interrupted by stop/account switch cannot be reported as success');
    assert.match(executor, /停止后.*未提交|账号切换后.*未提交/,
        'unsubmitted settings after a stop or account switch must be listed in the per-target result');
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
    assert.match(contactApi, /class ContactLabelListResult[\s\S]*?public final boolean success/,
        'label table read failure must remain distinct from a successful empty list');
    const labelListMethod = methodBody(contactApi, 'public ContactLabelListResult getContactLabelListResult');
    assert.match(labelListMethod, /labelCursor == null[\s\S]*?failedContactLabelList/,
        'label table query failures must be returned as failures');
    assert.match(labelListMethod, /contactsCursor == null[\s\S]*?failedContactLabelList/,
        'member relation query failures must be returned as failures');
    assert.match(labelListMethod, /new ContactLabelListResult\(true, labels, ""\)/,
        'a successfully-read table with no members must remain a valid empty result');
    assert.match(contactApi, /SERVER_ACCEPTED[\s\S]*?SUBMITTED_UNCONFIRMED[\s\S]*?SERVER_REJECTED/,
        'label outcomes must separate server acceptance, unknown submission and rejection');
    assert.match(contactApi, /WeChatContactTypeState\.ONLY_CHAT[\s\S]*?readSnsTagMembership[\s\S]*?NativeSceneDispatch\.submitAndAwait/,
        'only-chat and SNS privacy changes must use their actual native status sources and server callbacks');
    const localPermissionMethod = methodBody(contactApi, 'private WeChatNativeMutationResult setLocalContactPermission');
    assert.match(localPermissionMethod, /Method action = findNativeContactHelper\(nativeContact, helperName\)[\s\S]*?WeChatMainThread\.call[\s\S]*?action\.invoke/,
        'star and outside-permission updates must resolve the native helper off-main and call it on the native UI looper');
    const localPermissionMainCall = methodBody(localPermissionMethod, 'Boolean submitted = WeChatMainThread.call(() ->');
    assert.doesNotMatch(localPermissionMainCall, /findNativeContactHelper/,
        'repeated native contact method scans must not run on WeChat main thread');
    assert.match(contactApi, /WeChatContactTypeState\.STARRED_FRIEND|WeChatContactTypeState\.ONLY_CHAT/,
        'friend state reads must use the verified native contact type bits');
    const starMethod = methodBody(contactApi, 'public WeChatNativeMutationResult setStarredFriend');
    assert.match(starMethod, /starred \? "r0" : "C0"/,
        'star and unstar must select the verified native add/remove helpers');
    const friendPermissionMethod = methodBody(contactApi, 'public WeChatNativeMutationResult setFriendPermission');
    assert.match(friendPermissionMethod, /final int bit = WeChatContactTypeState\.ONLY_CHAT[\s\S]*?q61\.i0[\s\S]*?0x800000/,
        'only-chat and ordinary-friend choices must use the native bulk permission scene and exact type bit');
    assert.match(friendPermissionMethod, /DONT_SEE_MOMENTS[\s\S]*?setSnsTagPermission\(username, enabled, guard\)/,
        'SNS do-not-see must route through verified tag membership instead of a contact type bit');
    assert.match(friendPermissionMethod, /getContactStateResult\(username\)[\s\S]*?NativeSceneDispatch\.submitAndAwait[\s\S]*?getContactStateResult\(username\)/,
        'friend permission changes must check validity before submission and reread state after the native callback');
    assert.match(friendPermissionMethod, /latest\.type, bit, enabled/,
        'friend permission submission must retain the other independently selected permission bits');
    const snsPermissionMethod = methodBody(contactApi, 'private WeChatNativeMutationResult setSnsTagPermission');
    assert.match(snsPermissionMethod, /readSnsTagMembership\(username, 5L\)[\s\S]*?j3[\s\S]*?5L[\s\S]*?Collections\.singletonList\(username\)/,
        'SNS permission must read and change only the selected member of native tag 5');
    assert.match(snsPermissionMethod, /NativeSceneDispatch\.submitAndAwait[\s\S]*?readSnsTagMembership\(username, 5L\)/,
        'SNS tag state must be reread after the native server response');
    assert.match(contactApi, /SnsPermissionUI\.G7[\s\S]*?com\.tencent\.mm\.plugin\.sns\.model\.p4[\s\S]*?"P1", String\.class, long\.class/,
        'SNS membership reads must reuse the exact native tag-store query path');
    assert.doesNotMatch(contactApi, /DO_NOT_SEE_MOMENTS\s*=\s*0x10000/,
        'SNS tag membership must not be misrepresented as rcontact.type bit 0x10000');
    assert.match(databaseApi, /queryWithStatus\([\s\S]*?new QueryResult\(true, rows, ""\)/,
        'batch candidate and state reads must preserve empty-versus-failure semantics');
    const groupSaveMethod = methodBody(chatroomApi, 'public WeChatNativeMutationResult setGroupSavedToContacts');
    assert.match(chatroomApi, /"k", nativeContact\.getClass\(\), boolean\.class/,
        'group save must use the native ChatRoomInfo service and distinguish submission from confirmation');
    assert.match(groupSaveMethod, /findRoomSaveMethod\(roomApi, preloadedContact\)[\s\S]*?WeChatMainThread\.call/,
        'group save must resolve the verified native entry before its short main-thread handoff');
    assert.match(groupSaveMethod, /current\.type & 1[\s\S]*?readNativeGroupSavedFlag[\s\S]*?SUBMITTED_UNCONFIRMED/,
        'group save must recheck the native state and never confuse local submission with server confirmation');
    const groupSaveMainCall = methodBody(groupSaveMethod, 'Boolean submitted = WeChatMainThread.call(() ->');
    assert.doesNotMatch(groupSaveMainCall, /readGroupContactState|databaseApi\.query/,
        'group save database validation must stay off the main looper');
    assert.match(chatroomApi, /"n", String\.class, String\.class, String\.class/,
        'group nickname must call the native RoomSDK operation with room ID, self ID and nickname');
    assert.match(chatroomApi, /"com\.tencent\.mm\.roomsdk\.model\.factory\.a"\.equals\(method\.getReturnType\(\)\.getName\(\)\)[\s\S]*?findRoomFactorySubmitMethod[\s\S]*?factoryBase\.getDeclaredMethod\("b"\)/,
        'RoomSDK nickname factory and submit method must pass the exact WeChat 8.0.80 signatures');
    const groupNicknameMethod = methodBody(chatroomApi, 'public WeChatNativeMutationResult setMyGroupNickname');
    assert.match(groupNicknameMethod, /normalizeNativeRoomNickname[\s\S]*?nickname\.length\(\) > 16/,
        'group nickname must apply WeChat normalization and length validation before creating a request');
    assert.match(groupNicknameMethod, /readMyNicknameState\(roomId, selfId\)[\s\S]*?if \(!current\.member\)/,
        'group nickname must recheck current membership and the current account before submission');
    assert.match(chatroomApi, /errorCode != 0[\s\S]*?SERVER_REJECTED[\s\S]*?updateMyNicknameCache/,
        'group nickname must identify the current member, await the native RoomSDK callback and update cache only after success');
    const groupNicknameMainCall = methodBody(groupNicknameMethod, 'Boolean submitted = WeChatMainThread.call(() ->');
    assert.doesNotMatch(groupNicknameMainCall, /readGroupContactState|readMyNicknameState|databaseApi\.query/,
        'group nickname database reads must stay off the main looper');
    assert.match(chatroomApi, /memberlist[\s\S]*?群成员资料为空，无法确认当前账号身份/,
        'empty group member data must block nickname writes without treating the room as deleted');

    process.stdout.write('Batch UI, lifecycle, candidate and native API assertions passed\n');
} finally {
    fs.rmSync(output, { recursive: true, force: true });
}
