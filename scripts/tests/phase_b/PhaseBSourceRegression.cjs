const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '../../..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const checks = [
  ['package-ready version gate precedes all early business hooks', () => {
    const source = read('app/src/main/java/h/Hchat/ModuleEntry.java');
    const ready = source.slice(source.indexOf('void onPackageReady'), source.indexOf('private void installPackageHooks'));
    assert.match(source, /WeChatVersionGate\.evaluateForProcess/);
    assert.ok(ready.indexOf('checkMinimumVersion') < ready.indexOf('installPackageHooks'));
    assert.match(source, /param\.getApplicationInfo\(\)/);
    assert.match(source, /getSystemContext\(\)/);
    assert.match(source, /appbrand_tinker_attach/);
    assert.match(source, /"tinker_attach_before"/);
    assert.match(source, /"tablet_tinker_attach_" \+ stage/);
    assert.match(source, /application_on_create/);
    assert.match(source, /DexInstallScheduler\.INSTANCE\.cancelAll\(\)[\s\S]*?HookRegistry\.get\(\)\.unhookAll\(\)/);
  }],
  ['runtime identity compares every identity field', () => {
    const source = read('app/src/main/java/h/Hchat/dexkit/DexCacheIdentity.kt');
    assert.doesNotMatch(source, /take\(7\)/);
    assert.match(source, /cachedKey\s*==\s*runtimeKey/);
    assert.match(source, /cached\.size\s*<\s*10/);
    assert.match(source, /isCompleteRuntimeIdentity/);
    assert.match(source, /cachedKey\s*==\s*runtimeKey/);
    const tablet = read('app/src/main/java/h/Hchat/hooks/items/tablet/WeChatTabletFeature.kt');
    assert.match(tablet, /WeChatVersionApi\.build\(context, classLoader\)\.cacheKey/);
    for (const file of [
      'app/src/main/java/h/Hchat/dexkit/SettingsDexFinder.java',
      'app/src/main/java/h/Hchat/dexkit/DexMethodCache.kt'
    ]) assert.match(read(file), /DexCacheIdentity\.sameRuntime/);
  }],
  ['Tinker lifecycle separates loader rotation from host identity invalidation', () => {
    const entry = read('app/src/main/java/h/Hchat/ModuleEntry.java');
    const guard = read('app/src/main/java/h/Hchat/hooks/api/runtime/HostRuntimeIdentityDecision.java');
    assert.match(entry, /runtimeIdentity\.approve\(/);
    assert.match(entry, /runtimeIdentity\.verify\([\s\S]*?invalidateHostIdentity\(source \+ ":runtime_identity_changed"\)/);
    assert.match(entry, /DexInstallScheduler\.INSTANCE\.cancelAll\(\);[\s\S]*?HookRegistry\.get\(\)\.unhookAll\(\)/);
    assert.match(guard, /CLASS_LOADER_GENERATION_CHANGED/);
    assert.match(guard, /approvedHostIdentity\.equals\(currentHostIdentity\)/);
    assert.match(read('app/src/test/java/h/Hchat/hooks/api/runtime/HostRuntimeIdentityLifecycleTest.java'), /legalTinkerLoaderRotationSurvivesAndPatchChangeCancelsGeneration/);
  }],
  ['global Dex cache stores locator state separately from scan completion', () => {
    const finder = read('app/src/main/java/h/Hchat/dexkit/DexFinder.java');
    const store = read('app/src/main/java/h/Hchat/dexkit/DexFinderCacheStore.java');
    assert.match(finder, /CACHE_SNAPSHOT_VERSION\s*=\s*"[4-9]/);
    assert.match(finder, /addMsgLocatorResolved/);
    assert.match(finder, /sendTextMsgLocatorResolved/);
    assert.match(store, /scanFinished/);
    assert.match(store, /criticalLocatorsResolved/);
    assert.match(finder, /CACHE_SNAPSHOT_VERSION\s*=\s*"4"/);
    assert.match(finder, /loadMethodSoft\("addMsgObserverMethod"\)/);
    const cacheLoad = finder.slice(finder.indexOf('private synchronized boolean loadCache()'), finder.indexOf('private void resetLoadedCacheDescriptors()'));
    assert.equal((cacheLoad.match(/DexCacheRestorePolicy\.restore\(/g) || []).length, 3);
    assert.doesNotMatch(cacheLoad, /isAddMsgApiAnchored|isSendTextOwnerAnchored|resolveSnsUploadApi\(|resolveSnsUploadMethods\(/);
    assert.match(cacheLoad, /findSnsAddVideoMethod\(snsUploadPackHelperClass\)/);
    assert.match(cacheLoad, /resolveSnsUploadHelperClass\(\)/);
    const methodCache = read('app/src/main/java/h/Hchat/dexkit/DexMethodCache.kt');
    assert.match(methodCache, /it\.toDexDescriptor\(\) == descriptor/);
    assert.match(methodCache, /loadConstructorDescriptor[\s\S]*?getConstructorInstance\(classLoader\)[\s\S]*?toDexDescriptor\(\) == descriptor/);
  }],
  ['AddMsg observation and pre-database blocking use distinct exact methods', () => {
    const finder = read('app/src/main/java/h/Hchat/dexkit/DexFinder.java');
    const events = read('app/src/main/java/h/Hchat/hooks/api/message/WeChatMessageEventApi.java');
    const block = read('app/src/main/java/h/Hchat/hooks/items/messageblock/MessageBlockFeature.kt');
    assert.match(finder, /ADDMSG_PROCESS_LOG_ANCHOR\s*=\s*"dkAddMsg from:%s to:%s id:\[%d,%d,%d\]/);
    assert.match(finder, /ADDMSG_PREPROCESS_ERROR_ANCHOR\s*=\s*"processAddMsg insert db error"/);
    assert.doesNotMatch(finder, /mkMethodUsingStrings\("dkAddMsg"\)|mkMethodUsingStrings\("processAddMsg"\)/);
    assert.match(finder, /findAddMsgPreProcessCallers[\s\S]*?storageErrorCallers\.contains\(caller\)[\s\S]*?invokeMethods/);
    assert.match(finder, /addMsgObserverMethod/);
    assert.match(finder, /addMsgPreProcessMethod/);
    assert.doesNotMatch(events, /for \(Method method : KavaReflector\.declaredMethods\(clazz\)\)/);
    assert.doesNotMatch(block, /for \(method in KavaReflector\.declaredMethods\(clazz\)\)/);
    assert.match(block, /addMsgPreProcessMethod/);
    assert.match(events, /chain\.proceed\(\)[\s\S]*?storedMessageField\.get\(result\)/);
    assert.match(events, /addMsgDeduplicator\.markIfNew/);
    assert.match(events, /if \(fresh\) for \(RawAddMsgListener listener/);
    assert.match(block, /registry\.isInstallationGenerationActive\(generation\)/);
  }],
  ['text request constructor supports the confirmed six-parameter request', () => {
    const finder = read('app/src/main/java/h/Hchat/dexkit/DexFinder.java');
    const message = read('app/src/main/java/h/Hchat/hooks/api/message/WeChatMessageApi.java');
    const args = read('app/src/main/java/h/Hchat/hooks/api/message/SendTextRequestArguments.java');
    assert.match(finder, /types\.length == 5 \|\| types\.length == 6/);
    assert.match(finder, /int\.class, int\.class, sourceArgsType, String\.class/);
    assert.match(args, /sourceArgs, null/);
    assert.doesNotMatch(message, /newInstanceByArgs\([\s\S]{0,150}sendTextMsgClass/);
  }],
  ['SNS video helper supports unique four and five String layouts and forwards the confirmed optional fingerprint', () => {
    const finder = read('app/src/main/java/h/Hchat/dexkit/DexFinder.java');
    const sns = read('app/src/main/java/h/Hchat/hooks/api/sns/WeChatSnsApi.kt');
    const args = read('app/src/main/java/h/Hchat/hooks/api/sns/SnsVideoRequestArguments.java');
    assert.match(finder, /params\.length != 4 && params\.length != 5/);
    assert.match(finder, /candidates\.size\(\) != 1/);
    assert.match(sns, /SnsVideoRequestArguments\.create/);
    assert.match(args, /rawFingerPrintForXiaoWei/);
    assert.match(args, /fileMd5, ""/);
  }],
  ['ECS locator failure remains retryable and warmup reports real readiness', () => {
    const finder = read('app/src/main/java/h/Hchat/dexkit/DexFinder.java');
    const api = read('app/src/main/java/h/Hchat/hooks/api/core/WechatApiFeature.java');
    assert.doesNotMatch(finder, /logDetail\("ECS礼物静默类未定位到"\);\s*ecsGiftLocatorResolved\s*=\s*true/);
    assert.doesNotMatch(api, /finally\s*\{[\s\S]*?markDexWarmupReady\(\);[\s\S]*?\}\s*return true;/);
    assert.match(api, /AddMsg定位=.*Hook=/);
  }],
  ['below-minimum gates are removed while uncertain settings path remains', () => {
    const image = read('app/src/main/java/h/Hchat/hooks/api/media/WeChatImageApi.java');
    assert.doesNotMatch(image, /isWeChatAtLeast\(8,\s*0,\s*66\)|shouldUseAsyncAppInfo/);
    assert.match(image, /!TextUtils\.isEmpty\(appId\) && canSendAsyncAppInfo\(\)/);
    const retransmit = read('app/src/main/java/h/Hchat/hooks/api/message/WeChatRetransmitPayload.kt');
    const forward = read('app/src/main/java/h/Hchat/hooks/items/forwardlimit/RemoveForwardLimitFeature.kt');
    const moments = read('app/src/main/java/h/Hchat/hooks/items/moments/MomentsKeywordBlockFeature.kt');
    const settings = read('app/src/main/java/h/Hchat/hooks/api/ui/SettingsInjector.java');
    assert.match(retransmit, /allowRawImageTokenFallback\(\): Boolean \{\s*return true/);
    assert.doesNotMatch(forward, /UIC_FORWARD_MIN_VERSION_CODE|requiresUicHook/);
    assert.doesNotMatch(moments, /IMPROVE_MIN_VERSION_CODE/);
    assert.match(settings, /injectLegacy\(\);[\s\S]*?injectModernMethod2\(\);/);
  }]
];

let failures = 0;
for (const [name, test] of checks) {
  try {
    test();
    console.log(`PASS: ${name}`);
  } catch (error) {
    failures++;
    console.error(`FAIL: ${name}\n  ${error.message.split('\n')[0]}`);
  }
}
if (failures) {
  console.error(`${failures}/${checks.length} Phase B source regressions failed`);
  process.exitCode = 1;
} else {
  console.log(`All ${checks.length} Phase B source regressions passed`);
}
