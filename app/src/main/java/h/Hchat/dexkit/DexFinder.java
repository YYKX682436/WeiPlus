package h.Hchat.dexkit;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Pair;
import h.Hchat.hooks.api.runtime.WeChatVersionApi;
import h.Hchat.hooks.api.runtime.WeChatSqliteWrapperLocator;
import h.Hchat.preferences.HchatStorage;
import h.Hchat.utils.KavaReflector;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.UsingFieldData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import h.Hchat.hooks.api.message.WeChatMessageParseApi;
import h.Hchat.hooks.api.contact.DeleteContactServiceMethodPolicy;
import h.Hchat.hooks.api.media.WeChatInternalServices;
import h.Hchat.utils.HLog;

/**
 * DexFinder - 使用 DexKit 定位微信混淆后的类和方法
 * 兼容定位逻辑以微信 8.0.76 及以上为维护范围；版本信息不参与模块加载判断。
 */
public class DexFinder {

    private static final String TAG = "[Hchat:DexFinder]";
    private static final String CACHE_PREFS = "Hchat_dex_cache";
    private static final String CONTACT_CACHE_PREFS = "Hchat_contact_sender_cache";
    private static final String CACHE_COMPLETE = "cache.complete";
    private static final String CACHE_KEY = "cache.key";
    private static final String CACHE_SNAPSHOT_VERSION_KEY = "cache.snapshot.version";
    private static final String CACHE_SNAPSHOT_VERSION = "4";
    private static final String SQLITE_WRAPPER_LOCATOR_VERSION_KEY = "locator.sqlite.wrapper.version";
    private static final String SQLITE_WRAPPER_LOCATOR_VERSION = "1";
    private static final String CACHE_ADDMSG_LOCATOR_RESOLVED = "locator.addmsg.resolved";
    private static final String CACHE_SEND_TEXT_LOCATOR_RESOLVED = "locator.send.text.resolved";
    private static final String CACHE_SNS_VIDEO_LOCATOR_RESOLVED = "locator.sns.video.resolved";
    // Exact, DEX-confirmed diagnostics in the message processor and its storage caller.
    // The former short anchors "dkAddMsg" and "processAddMsg" are not literal strings in
    // either supported sample; DexKit's usingStrings matcher requires the complete constant.
    private static final String ADDMSG_PROCESS_LOG_ANCHOR =
            "dkAddMsg from:%s to:%s id:[%d,%d,%d] status:%d type:%d time:[%d %s] diff:%d imgstatus:%d imgbuf:%d src:%d push:%d content:%s flag:%s";
    private static final String ADDMSG_PREPROCESS_ERROR_ANCHOR = "processAddMsg insert db error";
    private static final String CONTACT_CACHE_VERSION = "1";
    private static final String LOCATOR_WISH_COMPLETE = "locator.wish.complete";
    private static final String LOCATOR_PACKET_COMPLETE = "locator.packet.complete";
    private static final String LOCATOR_ECS_COMPLETE = "locator.ecs.complete";
    private static final String LOCATOR_MSG_DESC_COMPLETE = "locator.msg.desc.complete";
    private static final String MSG_DESC_LOCATOR_VERSION_KEY = "locator.msg.desc.version";
    private static final String MSG_DESC_LOCATOR_VERSION = "2";
    private static final long NATIVE_CONVERSATION_LOCATOR_RETRY_MS = 30_000L;
    private static final String NATIVE_HOME_QUERY_ANCHOR = "select * from rconversation where";
    private static final String NATIVE_HOME_PARENT_ANCHOR = "parentRef = '";
    private static final String RED_PACKET_UNION_LOCATOR_VERSION_KEY = "redpacket.union.locator.version";
    private static final String RED_PACKET_UNION_LOCATOR_VERSION = "1";
    private static final String NETWORK_QUEUE_LOCATOR_VERSION_KEY = "network.queue.locator.version";
    private static final String NETWORK_QUEUE_LOCATOR_VERSION = "2";
    private static final boolean VERBOSE = false;
    private final DexFinderQuery dexQueries;
    private final ClassLoader classLoader;
    private final SharedPreferences cachePrefs;
    private final SharedPreferences contactCachePrefs;
    private final SharedPreferences msgDescCachePrefs;
    private final DexFinderCacheStore cacheStore;
    private final DexFinderCacheStore.Preferences cachePreferenceView;
    private final DexFinderCacheStore.Preferences contactCachePreferenceView;
    private final String runtimeCacheKey;
    private final RetryableLocatorState contactSenderLocatorState = new RetryableLocatorState();
    private boolean resolutionFinished;
    private boolean cacheLoadAttempted;
    private boolean cacheDescriptorInvalid;
    private boolean addMsgLocatorResolved;
    private boolean sendTextMsgLocatorResolved;
    private boolean snsVideoLocatorResolved;

    // The protobuf dispatch method observes completed storage results; the caller is the only
    // verified pre-processing point suitable for a deliberate message block decision.
    public Method addMsgObserverMethod;
    public Method addMsgPreProcessMethod;
    public Field addMsgRequestPayloadField;
    public Field addMsgResultMessageField;

    // AddMsg 处理类
    public List<Class<?>> addMsgClasses = new ArrayList<>();
    // 静默收红包
    public Class<?> receiveLuckyMoneyClass;
    public Class<?> receiveLuckyMoneyUnionClass;
    public Constructor<?> receiveCtor;
    public Constructor<?> unionReceiveCtor;
    // 静默拆红包
    public Class<?> openLuckyMoneyClass;
    public Class<?> openLuckyMoneyUnionClass;
    public Constructor<?> openCtor10;
    public Constructor<?> openCtor9;
    public Constructor<?> openCtor8;
    public Constructor<?> unionOpenCtor10;
    public Constructor<?> unionOpenCtor9;
    // 网络请求队列
    public Class<?> netQueueClass;
    public List<Class<?>> netQueueCandidateClasses = new ArrayList<>();
    public List<Class<?>> packetBaseClasses = new ArrayList<>();
    public List<Class<?>> packetQueueClasses = new ArrayList<>();
    public List<Class<?>> fakePacketClasses = new ArrayList<>();
    private Class<?> protobufBaseClass;
    public Class<?> protobufRawReqClass;
    public Class<?> protobufGenericRespClass;
    public Class<?> protobufConfigBuilderClass;
    public Class<?> protobufReqRespClass;
    public Class<?> protobufCallbackClass;
    public Class<?> protobufNetSceneBaseClass;
    public Method protobufStaticDispatchMethod;
    // 祝福语
    public Class<?> wishWxHbClass;
    public Constructor<?> wishWxHbCtor;
    // 文本消息发送
    public Class<?> sendTextMsgClass;
    public Constructor<?> sendTextMsgCtorLong;
    public Constructor<?> sendTextMsgCtorObject;
    // 微信服务容器
    public Method serviceGetterMethod;
    // ECS 礼物静默处理
    public Class<?> ecsGiftTaskClass;
    public Class<?> ecsGiftServiceClass;
    public Class<?> ecsGiftMsgClass;
    public List<Method> getContactAddMethods = new ArrayList<>();
    public List<Method> getContactServiceGetters = new ArrayList<>();
    // 图片发送高层入口
    public Method sendImageMethod;
    public Class<?> sendImageAsyncParamsClass;
    public Class<?> sendImageCrossParamsClass;
    public Class<?> sendImageAppInfoClass;
    public Method sendImageAsyncSubmitMethod;
    public Class<?> imageCdnTaskClass;
    public Method imageCdnSubmitMethod;
    public Method imageCdnServiceGetterMethod;
    public Class<?> marsCdnManagerClass;
    public Class<?> marsCdnDownloadRequestClass;
    public Class<?> marsCdnDownloadCallbackClass;
    public Method marsCdnStartDownloadMethod;
    public Method imageBestPathMethod;
    public Method imageStorageGetterMethod;
    public Method imageTokenPathMethod;
    // 文件发送高层入口
    public Method sendFileMethod;
    public Method sendFileAttachDirMethod;
    public Method sendFileAttachPathMethod;
    // 微信消息描述文本方法（签名 (Context, boolean)→String；微信对该类方法内部对 null 调 isEmpty() 会崩，用于防崩兜底）
    // 跨版本通用：不依赖混淆类名/方法名，按签名定位（各版本这类方法极少，8.0.76 实测仅 3 个）
    public List<Method> msgDescTextMethods = new java.util.ArrayList<>();
    // XML/AppMsg 原始发送入口
    public Method sendXmlAppMsgMethod;
    public Method appMsgParseMethod;
    public Class<?> groupSolitairePluginClass;
    public Method groupSolitaireSendMethod;
    // 本地消息插入
    public Class<?> localMessageClass;
    public Constructor<?> localMessageCtor;
    public Method localSystemMessageMethod;
    public Method localSystemMessageStorageInsertMethod;
    public Method localMessageInsertMethod;
    public Method localMessageCreateTimeMethod;
    // 视频发送高层入口
    public Method sendVideoMethod;
    public Class<?> sendVideoTaskClass;
    public Method videoPathMethod;
    public Method videoPathOwnerGetterMethod;
    private Class<?> videoInfoClass;
    public Method videoInfoByFileNameMethod;
    // 转账领取/退回
    public Class<?> transferOperationClass;
    // 转账详情查询
    public Class<?> transferQueryClass;
    public Method transferQueryResponseMethod;
    // 通过好友申请
    public Class<?> verifyUserClass;
    // 发送联系人名片
    public Method contactCardXmlMethod;
    // 拍一拍系统消息模板解析
    public Method patDisplayTemplateMethod;
    public Class<?> patExtensionClass;
    public Method patCreatePairMethod;
    public Method patSuffixMethod;
    public Method patCanSendMethod;
    public Class<?> sendPatSceneClass;
    public Constructor<?> sendPatSceneCtor;
    // 语音消息发送
    public Method voiceStartRecordMethod;
    public Method voiceFullPathMethod;
    public Method voiceMessagePathMethod;
    private boolean voiceMessagePathLookupComplete;
    public Method voiceFinishRecordMethod;
    public Method voiceInfoQueryMethod;
    public Class<?> voiceUploadClass;
    public Constructor<?> voiceUploadCtor;
    public Constructor<?> voiceUploadCdnCtor;
    // 微信原生语音播放
    public Method voicePlaybackStartMethod;
    public Method voicePlaybackPauseMethod;
    public Method voicePlaybackResumeMethod;
    public Method voicePlaybackStopMethod;
    // 表情消息发送
    public Method emojiSendMethod;
    public Method emojiManagerSendMethod;
    public Method emojiGetByMd5Method;
    public Method emojiCreateInfoMethod;
    public Method emojiUpdateInfoMethod;
    public Method emojiAccPathMethod;
    public Method emojiCheckGifMethod;
    public Method emojiFilePathMethod;
    public Method emojiDecodeDataMethod;
    public Method emojiDecodeManagerGetterMethod;
    // 收藏消息
    public Class<?> favoriteItemClass;
    public Method favoriteItemConvertFromCursorMethod;
    public Class<?> favoriteServiceClass;
    public Method favoriteServiceResolverMethod;
    public Method favoriteStorageGetterMethod;
    public Method favoriteListMethod;
    public Method favoriteListNextMethod;
    public Method favoriteListCursorMethod;
    public Method favoriteGetMethod;
    public Method favoriteSendMethod;
    // 微信数据库/联系人 API
    public Class<?> mmKernelClass;
    public Class<?> coreStorageClass;
    public Method coreStorageGetter;
    public Class<?> configStorageClass;
    public Class<?> sqliteDbWrapperClass;
    public Method conversationDeleteMethod;
    public Method conversationDeleteFromHomeMethod;
    public Method nativeConversationHomeListQueryMethod;
    public Method conversationSetPlacedTopMethod;
    public Method conversationUnsetPlacedTopMethod;
    public Method conversationHideFromHomeMethod;
    public Method messageClearByTalkerMethod;
    public Method messageClearBatchMethod;
    public Method deleteContactServiceMethod;
    public Method deleteContactServiceResponseMethod;
    public Method deleteContactServiceGetterMethod;
    public Method deleteContactServiceUiCallerMethod;
    public int deleteContactServiceCandidateCount = -1;
    public int deleteContactServiceResponseCandidateCount = -1;
    public int deleteContactServiceGetterCandidateCount = -1;
    // 微信原生消息免打扰
    public Method contactMuteStateMethod;
    public Method contactMuteEnableMethod;
    public Method contactMuteDisableMethod;
    public Method contactStorageGetterMethod;
    public Method contactStorageQueryMethod;
    public Method chatroomMuteServiceGetterMethod;
    public Method chatroomMuteBuildMethod;
    public Method chatroomMuteSubmitMethod;
    public Method groupMemberDisplayNameMethod;
    public Class<?> addChatroomMemberClass;
    public Constructor<?> addChatroomMemberCtor;
    public Class<?> inviteChatroomMemberClass;
    public Constructor<?> inviteChatroomMemberCtor;
    public Class<?> delChatroomMemberClass;
    public Constructor<?> delChatroomMemberCtor;
    public Class<?> revokeMsgClass;
    public Constructor<?> revokeMsgCtor;
    public Class<?> uploadDeviceStepClass;
    public Constructor<?> uploadDeviceStepCtor;
    public Class<?> addContactLabelClass;
    public Constructor<?> addContactLabelCtorString;
    public Constructor<?> addContactLabelCtorList;
    public Class<?> modifyContactLabelListClass;
    public Constructor<?> modifyContactLabelListCtor;
    public Class<?> snsUploadPackHelperClass;
    public Class<?> snsUploadManagerClass;
    public Method snsUploadManagerGetterMethod;
    public Method snsSetContentMethod;
    public Method snsSetSdkIdMethod;
    public Method snsSetSdkAppNameMethod;
    public Method snsAddImageMethod;
    public Method snsAddVideoMethod;
    public Method snsCommitMethod;
    public Method snsUploadCheckMethod;
    public Method snsShareAppMsgMethod;
    // 聊天页 API
    public Method chatPageStartMethod;
    public Method chatPageFragmentEnterMethod;
    public Method chatPageFragmentExitMethod;
    // 脚本发送按钮 hook
    public Method chatFooterSendClickMethod;

    private boolean receiveUnionLocatorResolved;
    private boolean openUnionLocatorResolved;
    private boolean networkQueueLocatorResolved;
    private boolean wishLocatorResolved;
    private boolean packetCompatLocatorResolved;
    private boolean ecsGiftLocatorResolved;
    private boolean msgDescLocatorResolved;
    private long nativeConversationHomeQueryRetryAfterMs;
    private long conversationSetPlacedTopRetryAfterMs;
    private long conversationUnsetPlacedTopRetryAfterMs;
    private long conversationHideFromHomeRetryAfterMs;

    public DexFinder(DexKitBridge dexKit, ClassLoader classLoader) {
        this(dexKit, classLoader, null);
    }

    public DexFinder(DexKitBridge dexKit, ClassLoader classLoader, Context hostContext) {
        this(dexKit == null ? null : new DexFinderQuery() {
            @Override public List<MethodData> findMethod(FindMethod query) {
                return dexKit.findMethod(query);
            }
            @Override public List<ClassData> findClass(FindClass query) {
                return dexKit.findClass(query);
            }
        }, classLoader, hostContext);
    }

    DexFinder(DexFinderQuery dexQueries, ClassLoader classLoader, Context hostContext) {
        this.dexQueries = dexQueries;
        this.classLoader = classLoader;
        this.cachePrefs = hostContext != null
                ? HchatStorage.preferences(hostContext, CACHE_PREFS)
                : null;
        this.contactCachePrefs = hostContext != null
                ? HchatStorage.preferences(hostContext, CONTACT_CACHE_PREFS)
                : null;
        this.msgDescCachePrefs = hostContext != null
                ? HchatStorage.preferences(hostContext, "Hchat_msg_desc_method_cache")
                : null;
        this.cachePreferenceView = cachePrefs != null ? new SharedPreferencesView(cachePrefs) : null;
        this.contactCachePreferenceView = contactCachePrefs != null
                ? new SharedPreferencesView(contactCachePrefs) : null;
        this.cacheStore = new DexFinderCacheStore(cachePreferenceView, contactCachePreferenceView);
        this.runtimeCacheKey = buildRuntimeCacheKey(hostContext, classLoader);
    }

    public synchronized void resolveAll() {
        if (resolutionFinished) {
            logDetail("resolveAll 已完成，跳过重复解析");
            return;
        }
        receiveUnionLocatorResolved = false;
        openUnionLocatorResolved = false;
        networkQueueLocatorResolved = false;
        if (loadCache()) {
            resolutionFinished = true;
            logDetail("命中缓存: " + shortKey(runtimeCacheKey));
            return;
        }
        resolveAddMsgClasses();
        resolveReceiveLuckyMoney();
        resolveOpenLuckyMoney();
        resolveNetworkQueue();
        resolveServiceManagerApi();
        resolveSendTextMsg();
        resolveGetContactServiceApi();
        resolveSendImageApi();
        resolveImageCdnDownloadApi();
        resolveSendFileApi();
        resolveSendXmlApi();
        resolveGroupSolitaireApi();
        resolveLocalMessageApi();
        resolveSendVideoTaskApi();
        resolveVideoPathApi();
        resolveVideoInfoApi();
        resolveSendVoiceApi();
        resolveSendEmojiApi();
        resolveFavoriteApi();
        resolveTransferOperationApi();
        resolveTransferQueryApi();
        resolveVerifyUserApi();
        resolveContactCardApi();
        resolvePatMessageApi();
        resolveDatabaseApi();
        resolveConversationDeleteApi();
        resolveMessageClearApi();
        resolveConversationMuteApi();
        resolveGroupMemberDisplayName();
        resolveAddChatroomMemberApi();
        resolveInviteChatroomMemberApi();
        resolveDelChatroomMemberApi();
        resolveRevokeMsgApi();
        resolveUploadDeviceStepApi();
        resolveContactLabelNetworkApi();
        resolveSnsUploadApi();
        resolveChatPageApi();
        resolveNativeSendButtonApi();
        resolveWishWxHb();
        resolvePacketCompatClasses();
        logMissingCritical();
        resolutionFinished = true;
        saveCache();
        logDetail("解析流程完成: critical=" + areCriticalLocatorsResolved()
                + " cache=" + shortKey(runtimeCacheKey));
    }

    public synchronized boolean isResolvedAll() {
        return resolutionFinished;
    }

    public synchronized boolean isResolutionFinished() {
        return resolutionFinished;
    }

    public synchronized boolean areCriticalLocatorsResolved() {
        return addMsgLocatorResolved && sendTextMsgLocatorResolved;
    }

    public synchronized boolean isAddMsgLocatorResolved() {
        return addMsgLocatorResolved && isAddMsgApiValid();
    }

    public synchronized boolean isSendTextMsgLocatorResolved() {
        return sendTextMsgLocatorResolved && isSendTextRequestApiValid();
    }

    public synchronized boolean isSnsVideoLocatorResolved() {
        return snsVideoLocatorResolved && isSnsAddVideoMethod(snsUploadPackHelperClass, snsAddVideoMethod);
    }

    public synchronized boolean resolveAddMsgApi() {
        if (isAddMsgApiValid()) {
            addMsgLocatorResolved = true;
            return true;
        }
        resolveAddMsgClasses();
        addMsgLocatorResolved = isAddMsgApiValid();
        if (resolutionFinished) saveCache();
        return addMsgLocatorResolved;
    }

    // ============ Helper: create FindMethod/FindClass ============
    private FindMethod mkMethodUsingStrings(String... strings) {
        FindMethod fm = new FindMethod();
        MethodMatcher mm = new MethodMatcher();
        mm.usingStrings(Arrays.asList(strings));
        fm.matcher(mm);
        return fm;
    }

    private FindMethod mkMethodUsingStringsAndName(String methodName, String... strings) {
        FindMethod fm = new FindMethod();
        MethodMatcher mm = new MethodMatcher();
        mm.name(methodName);
        mm.usingStrings(Arrays.asList(strings));
        fm.matcher(mm);
        return fm;
    }

    private FindClass mkClassUsingStrings(String... strings) {
        FindClass fc = new FindClass();
        ClassMatcher cm = new ClassMatcher();
        cm.usingStrings(Arrays.asList(strings));
        fc.matcher(cm);
        return fc;
    }

    private FindClass mkClassByName(String name) {
        FindClass fc = new FindClass();
        ClassMatcher cm = new ClassMatcher();
        cm.className(name);
        fc.matcher(cm);
        return fc;
    }

    private void collectMethodOwnerClass(List<MethodData> methods, List<Class<?>> out) {
        for (MethodData m : methods) {
            try {
                Class<?> cl = KavaReflector.loadClass(m.getClassName(), classLoader);
                if (!out.contains(cl)) out.add(cl);
            } catch (Throwable ignored) {}
        }
    }

    // ============ AddMsg ============
    private void resolveAddMsgClasses() {
        if (isAddMsgApiValid()) {
            addMsgLocatorResolved = true;
            return;
        }
        addMsgObserverMethod = null;
        addMsgPreProcessMethod = null;
        addMsgRequestPayloadField = null;
        addMsgResultMessageField = null;
        addMsgClasses.clear();
        try {
            LinkedHashSet<Method> processCandidates = new LinkedHashSet<>();
            for (String anchor : new String[]{ADDMSG_PROCESS_LOG_ANCHOR}) {
                for (MethodData data : dexQueries.findMethod(mkMethodUsingStrings(anchor))) {
                    try {
                        Method candidate = data.getMethodInstance(classLoader);
                        if (isAddMsgCentralProcessMethod(candidate)) processCandidates.add(candidate);
                    } catch (Throwable ignored) {}
                }
            }

            List<AddMsgMethodPair> pairs = new ArrayList<>();
            for (Method process : processCandidates) {
                Class<?> requestType = process.getParameterTypes()[0];
                Class<?> resultType = process.getReturnType();
                Field requestPayload = findUniqueAddMsgPayloadField(requestType);
                Field resultMessage = findUniqueObjectField(resultType);
                if (requestPayload == null || resultMessage == null) continue;
                for (Method preProcess : findAddMsgPreProcessCallers(process, requestPayload.getType())) {
                    if (preProcess.getDeclaringClass() != process.getDeclaringClass()) continue;
                    pairs.add(new AddMsgMethodPair(process, preProcess, requestPayload, resultMessage));
                }
            }
            if (pairs.size() != 1) {
                logDetail("AddMsg定位拒绝: structurallyValidPairs=" + pairs.size());
                addMsgLocatorResolved = false;
                return;
            }
            AddMsgMethodPair pair = pairs.get(0);
            addMsgObserverMethod = KavaReflector.accessible(pair.processMethod);
            addMsgPreProcessMethod = KavaReflector.accessible(pair.preProcessMethod);
            pair.requestPayloadField.setAccessible(true);
            pair.resultMessageField.setAccessible(true);
            addMsgRequestPayloadField = pair.requestPayloadField;
            addMsgResultMessageField = pair.resultMessageField;
            addMsgClasses.add(addMsgPreProcessMethod.getDeclaringClass());
            addMsgLocatorResolved = isAddMsgApiValid();
            logDetail("AddMsg定位: observer=" + methodName(addMsgObserverMethod)
                    + " preProcess=" + methodName(addMsgPreProcessMethod)
                    + " state=" + (addMsgLocatorResolved ? "located" : "rejected"));
        } catch (Throwable e) {
            addMsgLocatorResolved = false;
            h.Hchat.utils.HLog.e(TAG + " resolveAddMsg 失败: " + e.getMessage(), e);
        }
    }

    private boolean isAddMsgCentralProcessMethod(Method method) {
        if (method == null || !Modifier.isStatic(method.getModifiers())
                || method.getReturnType() == void.class || method.getReturnType().isPrimitive()) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 3 && params[0] != Object.class && !params[0].isPrimitive()
                && findUniqueAddMsgPayloadField(params[0]) != null
                && findUniqueObjectField(method.getReturnType()) != null;
    }

    private Field findUniqueAddMsgPayloadField(Class<?> requestType) {
        if (requestType == null) return null;
        List<Field> matches = new ArrayList<>();
        WeChatMessageParseApi parser = new WeChatMessageParseApi();
        for (Field field : KavaReflector.declaredFields(requestType)) {
            Class<?> type = field.getType();
            if (type == null || type == Object.class || type.isPrimitive() || type.isArray()) continue;
            if (parser.isLikelyAddMsgClass(type)) matches.add(field);
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private Field findUniqueObjectField(Class<?> resultType) {
        if (resultType == null) return null;
        List<Field> matches = new ArrayList<>();
        for (Field field : KavaReflector.declaredFields(resultType)) {
            Class<?> type = field.getType();
            if (type != null && !type.isPrimitive() && type != String.class && type != Object.class
                    && !type.isArray()) matches.add(field);
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private List<Method> findAddMsgPreProcessCallers(Method processMethod, Class<?> payloadType) {
        List<Method> result = new ArrayList<>();
        try {
            LinkedHashSet<Method> storageErrorCallers = new LinkedHashSet<>();
            for (MethodData data : dexQueries.findMethod(mkMethodUsingStrings(ADDMSG_PREPROCESS_ERROR_ANCHOR))) {
                try {
                    Method candidate = data.getMethodInstance(classLoader);
                    if (candidate.getDeclaringClass() == processMethod.getDeclaringClass()) {
                        storageErrorCallers.add(candidate);
                    }
                } catch (Throwable ignored) {}
            }
            if (storageErrorCallers.isEmpty()) return result;
            MethodMatcher target = new MethodMatcher()
                    .declaredClass(processMethod.getDeclaringClass().getName())
                    .name(processMethod.getName())
                    .paramTypes(processMethod.getParameterTypes())
                    .returnType(processMethod.getReturnType());
            MethodMatcher callerMatcher = new MethodMatcher();
            callerMatcher.invokeMethods(new MethodsMatcher().add(target));
            for (MethodData data : dexQueries.findMethod(new FindMethod().matcher(callerMatcher))) {
                try {
                    Method caller = data.getMethodInstance(classLoader);
                    Class<?>[] params = caller.getParameterTypes();
                    if (!storageErrorCallers.contains(caller)
                            || caller.getDeclaringClass() != processMethod.getDeclaringClass()
                            || caller.getReturnType() != void.class
                            || Modifier.isStatic(caller.getModifiers())
                            || params.length < 1 || params[0] != payloadType) continue;
                    if (!result.contains(caller)) result.add(KavaReflector.accessible(caller));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable e) {
            logDetail("AddMsg调用关系定位失败: " + e.getMessage());
        }
        return result;
    }

    private boolean isAddMsgApiValid() {
        if (addMsgObserverMethod == null || addMsgPreProcessMethod == null
                || addMsgRequestPayloadField == null || addMsgResultMessageField == null) return false;
        Class<?>[] processParams = addMsgObserverMethod.getParameterTypes();
        Class<?>[] preParams = addMsgPreProcessMethod.getParameterTypes();
        return Modifier.isStatic(addMsgObserverMethod.getModifiers())
                && addMsgObserverMethod.getReturnType() != void.class
                && processParams.length == 3
                && addMsgPreProcessMethod.getReturnType() == void.class
                && !Modifier.isStatic(addMsgPreProcessMethod.getModifiers())
                && preParams.length > 0
                && preParams[0] == addMsgRequestPayloadField.getType()
                && addMsgRequestPayloadField.getDeclaringClass() == processParams[0]
                && addMsgResultMessageField.getDeclaringClass() == addMsgObserverMethod.getReturnType()
                && addMsgClasses != null
                && addMsgClasses.contains(addMsgPreProcessMethod.getDeclaringClass());
    }

    private void restoreAddMsgApiFields() {
        addMsgRequestPayloadField = null;
        addMsgResultMessageField = null;
        if (addMsgObserverMethod == null || addMsgPreProcessMethod == null) return;
        Class<?>[] processParams = addMsgObserverMethod.getParameterTypes();
        Class<?>[] preParams = addMsgPreProcessMethod.getParameterTypes();
        if (processParams.length != 3 || preParams.length == 0) return;
        List<Field> requestFields = new ArrayList<>();
        for (Field field : KavaReflector.declaredFields(processParams[0])) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == preParams[0]) {
                requestFields.add(field);
            }
        }
        if (requestFields.size() == 1) {
            requestFields.get(0).setAccessible(true);
            addMsgRequestPayloadField = requestFields.get(0);
        }
        Field resultMessage = findUniqueObjectField(addMsgObserverMethod.getReturnType());
        if (resultMessage != null) {
            resultMessage.setAccessible(true);
            addMsgResultMessageField = resultMessage;
        }
        if (addMsgClasses == null) addMsgClasses = new ArrayList<>();
        if (!addMsgClasses.contains(addMsgPreProcessMethod.getDeclaringClass())) {
            addMsgClasses.add(addMsgPreProcessMethod.getDeclaringClass());
        }
    }

    private static final class AddMsgMethodPair {
        final Method processMethod;
        final Method preProcessMethod;
        final Field requestPayloadField;
        final Field resultMessageField;

        AddMsgMethodPair(Method processMethod, Method preProcessMethod,
                         Field requestPayloadField, Field resultMessageField) {
            this.processMethod = processMethod;
            this.preProcessMethod = preProcessMethod;
            this.requestPayloadField = requestPayloadField;
            this.resultMessageField = resultMessageField;
        }
    }

    // ============ 收红包 ============
    private void resolveReceiveLuckyMoney() {
        try {
            // 策略1: 用真实的 CGI URL 字符串定位
            List<ClassData> classes = dexQueries.findClass(
                    mkClassUsingStrings("cgi-bin/mmpay-bin/receivewxhb"));
            for (ClassData cd : classes) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    Constructor<?> ctor = findFirstCtorByArgCounts(cl, 7, 10, 8);
                    if (ctor != null) {
                        receiveLuckyMoneyClass = cl;
                        receiveCtor = ctor;
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            // 策略2: 回退到旧锚点
            if (receiveLuckyMoneyClass == null) {
                List<ClassData> classes2 = dexQueries.findClass(
                        mkClassUsingStrings("receivehongbao"));
                for (ClassData cd : classes2) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                        Constructor<?> ctor = findCtorByArgCount(cl, 7);
                        if (ctor != null) {
                            receiveLuckyMoneyClass = cl;
                            receiveCtor = ctor;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            resolveReceiveLuckyMoneyUnion();

            logDetail("收红包类: " +
                    (receiveLuckyMoneyClass != null ? receiveLuckyMoneyClass.getName() : "null")
                    + " Union=" + (receiveLuckyMoneyUnionClass != null
                    ? receiveLuckyMoneyUnionClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveReceive 失败: " + e.getMessage(), e);
        }
    }

    private boolean resolveReceiveLuckyMoneyUnion() {
        receiveLuckyMoneyUnionClass = null;
        unionReceiveCtor = null;
        receiveUnionLocatorResolved = false;
        try {
            List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings(
                    "MicroMsg.NetSceneReceiveLuckyMoneyUnion", "union_source"));
            for (ClassData cd : classes) {
                try {
                    Class<?> clazz = KavaReflector.loadClass(cd.getName(), classLoader);
                    Constructor<?> ctor = findCtorByArgCount(clazz, 6);
                    if (ctor == null) continue;
                    receiveLuckyMoneyUnionClass = clazz;
                    unionReceiveCtor = ctor;
                    break;
                } catch (Throwable ignored) {}
            }
            receiveUnionLocatorResolved = receiveLuckyMoneyUnionClass != null
                    && unionReceiveCtor != null;
            logDetail("Union收红包类: " + (receiveLuckyMoneyUnionClass != null
                    ? receiveLuckyMoneyUnionClass.getName() : "null")
                    + " ctor6=" + (unionReceiveCtor != null));
            return receiveUnionLocatorResolved;
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveReceiveUnion 失败: " + e.getMessage(), e);
            return false;
        }
    }

    // ============ 拆红包 ============
    private void resolveOpenLuckyMoney() {
        try {
            // 策略1: 用真实的 CGI URL 字符串定位
            List<ClassData> classes = dexQueries.findClass(
                    mkClassUsingStrings("cgi-bin/mmpay-bin/openwxhb"));
            for (ClassData cd : classes) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    Constructor<?> c10 = findCtorByArgCount(cl, 10);
                    Constructor<?> c9 = findCtorByArgCount(cl, 9);
                    Constructor<?> c8 = findCtorByArgCount(cl, 8);
                    if (c10 != null || c9 != null || c8 != null) {
                        openLuckyMoneyClass = cl;
                        openCtor10 = c10;
                        openCtor9 = c9;
                        openCtor8 = c8;
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            // 策略2: 回退到旧锚点
            if (openLuckyMoneyClass == null) {
                List<ClassData> classes2 = dexQueries.findClass(
                        mkClassUsingStrings("open lucky"));
                for (ClassData cd : classes2) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                        Constructor<?> c10 = findCtorByArgCount(cl, 10);
                        Constructor<?> c9 = findCtorByArgCount(cl, 9);
                        Constructor<?> c8 = findCtorByArgCount(cl, 8);
                        if (c10 != null || c9 != null || c8 != null) {
                            openLuckyMoneyClass = cl;
                            openCtor10 = c10;
                            openCtor9 = c9;
                            openCtor8 = c8;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            resolveOpenLuckyMoneyUnion();

            logDetail("拆红包类: " +
                    (openLuckyMoneyClass != null ? openLuckyMoneyClass.getName() : "null")
                    + " Union=" + (openLuckyMoneyUnionClass != null
                    ? openLuckyMoneyUnionClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveOpen 失败: " + e.getMessage(), e);
        }
    }

    private boolean resolveOpenLuckyMoneyUnion() {
        openLuckyMoneyUnionClass = null;
        unionOpenCtor10 = null;
        unionOpenCtor9 = null;
        openUnionLocatorResolved = false;
        try {
            List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings(
                    "MicroMsg.NetSceneOpenLuckyMoneyUnion", "union_source"));
            for (ClassData cd : classes) {
                try {
                    Class<?> clazz = KavaReflector.loadClass(cd.getName(), classLoader);
                    Constructor<?> ctor10 = findCtorByArgCount(clazz, 10);
                    Constructor<?> ctor9 = findCtorByArgCount(clazz, 9);
                    if (ctor10 == null && ctor9 == null) continue;
                    openLuckyMoneyUnionClass = clazz;
                    unionOpenCtor10 = ctor10;
                    unionOpenCtor9 = ctor9;
                    break;
                } catch (Throwable ignored) {}
            }
            openUnionLocatorResolved = openLuckyMoneyUnionClass != null
                    && (unionOpenCtor10 != null || unionOpenCtor9 != null);
            logDetail("Union拆红包类: " + (openLuckyMoneyUnionClass != null
                    ? openLuckyMoneyUnionClass.getName() : "null")
                    + " ctor10=" + (unionOpenCtor10 != null)
                    + " ctor9=" + (unionOpenCtor9 != null));
            return openUnionLocatorResolved;
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveOpenUnion 失败: " + e.getMessage(), e);
            return false;
        }
    }

    // ============ 网络队列 ============
    private void resolveNetworkQueue() {
        netQueueClass = null;
        netQueueCandidateClasses.clear();
        networkQueueLocatorResolved = false;
        try {
            collectKnownNetworkQueueClasses();

            // 先按真实红包请求类型筛选，避免 doSceneImp 的普通业务类抢到首位。
            collectNetworkQueueClassesByAnchors();
            for (Class<?> candidate : netQueueCandidateClasses) {
                if (hasQueueSendMethodForRequests(candidate)) {
                    netQueueClass = candidate;
                    break;
                }
            }

            // 策略1: 搜索 doSceneImp 字符串，找到包含 dispatch 方法的类
            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings("doSceneImp"));
            if (netQueueClass == null) {
                for (MethodData m : methods) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(m.getClassName(), classLoader);
                        addNetQueueCandidate(cl);
                        if (hasQueueSendMethodForRequests(cl)) {
                            netQueueClass = cl;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // 策略2: 如果没找到直接发包方法，继续收集候选，后面再统一筛选。
            if (netQueueClass == null) {
                for (MethodData m : methods) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(m.getClassName(), classLoader);
                        addNetQueueCandidate(cl);
                    } catch (Throwable ignored) {}
                }
            }

            if (netQueueClass == null || !hasQueueSendMethodForRequests(netQueueClass)) {
                Class<?> sendClass = findFirstLikelyQueueClass();
                if (sendClass != null) netQueueClass = sendClass;
            }

            networkQueueLocatorResolved = netQueueClass != null
                    && hasQueueSendMethodForRequests(netQueueClass);

            logDetail("网络队列类: " +
                    (netQueueClass != null ? netQueueClass.getName() : "null")
                    + " candidates=" + netQueueCandidateClasses.size());
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveQueue 失败: " + e.getMessage(), e);
        }
    }

    // ============ 祝福语 ============
    private void resolveWishWxHb() {
        if (wishLocatorResolved) return;
        if (wishWxHbClass != null) {
            wishLocatorResolved = true;
            return;
        }
        try {
            String[][] anchors = {
                    {"/cgi-bin/mmpay-bin/wishwxhb"},
                    {"wishwxhb"},
                    {"NetSceneWishWxHb"}
            };
            for (String[] anchor : anchors) {
                List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings(anchor));
                for (ClassData cd : classes) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                        Constructor<?> ctor = findCtorByArgCount(cl, 4);
                        if (ctor != null) {
                            wishWxHbClass = cl;
                            wishWxHbCtor = ctor;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
                if (wishWxHbClass != null) break;
            }
            logDetail("祝福语类: " +
                    (wishWxHbClass != null ? wishWxHbClass.getName() : "null"));
            wishLocatorResolved = true;
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveWish 失败: " + e.getMessage(), e);
        }
    }

    // ============ 文本消息发送 ============
    public void resolveServiceManagerApi() {
        ensureSnapshotLoaded();
        try {
            if (isServiceGetterMethod(serviceGetterMethod)) return;
            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings("calling getService(...)"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isServiceGetterMethod(method)) continue;
                    KavaReflector.accessible(method);
                    serviceGetterMethod = method;
                    break;
                } catch (Throwable ignored) {}
            }
            logDetail("服务容器方法: " + methodName(serviceGetterMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveServiceManagerApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveSendTextMsg() {
        try {
            if (isSendTextRequestApiValid()) {
                sendTextMsgLocatorResolved = true;
                if (resolutionFinished) saveCache();
                return;
            }
            sendTextMsgLocatorResolved = false;
            sendTextMsgClass = null;
            sendTextMsgCtorLong = null;
            sendTextMsgCtorObject = null;

            LinkedHashSet<Class<?>> candidates = new LinkedHashSet<>();
            List<Class<?>> candidateList = new ArrayList<>();
            collectSendTextClassCandidates(candidateList, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/newsendmsg"},
                            {"MicroMsg.NetSceneSendMsg"},
                            {"NetSceneSendMsg"},
                            {"newsendmsg"}
                    });
            collectSendTextMethodOwnerCandidates(candidateList, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/newsendmsg"},
                            {"MicroMsg.NetSceneSendMsg"},
                            {"NetSceneSendMsg"}
                    });

            candidates.addAll(candidateList);
            List<Class<?>> validOwners = new ArrayList<>();
            for (Class<?> cl : candidates) {
                Constructor<?> longCtor = findSendTextConstructor(cl, long.class);
                Constructor<?> objectCtor = findSendTextConstructor(cl, Object.class);
                if (longCtor != null || objectCtor != null) validOwners.add(cl);
            }
            if (validOwners.size() == 1) {
                sendTextMsgClass = validOwners.get(0);
                sendTextMsgCtorLong = findSendTextConstructor(sendTextMsgClass, long.class);
                sendTextMsgCtorObject = findSendTextConstructor(sendTextMsgClass, Object.class);
            }
            sendTextMsgLocatorResolved = isSendTextRequestApiValid();
            if (resolutionFinished) saveCache();

            logDetail("文本发送类: "
                    + (sendTextMsgClass != null ? sendTextMsgClass.getName() : "null")
                    + " longCtor=" + (sendTextMsgCtorLong != null)
                    + " objectCtor=" + (sendTextMsgCtorObject != null)
                    + " state=" + (sendTextMsgLocatorResolved ? "located" : "missing_or_ambiguous"));
        } catch (Throwable e) {
            sendTextMsgLocatorResolved = false;
            h.Hchat.utils.HLog.e(TAG + " resolveSendText 失败: " + e.getMessage(), e);
        }
    }

    private Constructor<?> findSendTextConstructor(Class<?> owner, Class<?> sourceArgsType) {
        if (owner == null || (sourceArgsType != long.class && sourceArgsType != Object.class)) return null;
        Constructor<?> six = findCtorByExactTypes(owner, String.class, String.class,
                int.class, int.class, sourceArgsType, String.class);
        if (six != null) return six;
        return findCtorByExactTypes(owner, String.class, String.class,
                int.class, int.class, sourceArgsType);
    }

    private boolean isSendTextRequestApiValid() {
        return sendTextMsgClass != null
                && (isSendTextConstructorValid(sendTextMsgCtorLong, long.class)
                || isSendTextConstructorValid(sendTextMsgCtorObject, Object.class));
    }

    private boolean isSendTextConstructorValid(Constructor<?> constructor, Class<?> sourceArgsType) {
        if (constructor == null || constructor.getDeclaringClass() != sendTextMsgClass) return false;
        Class<?>[] types = constructor.getParameterTypes();
        return (types.length == 5 || types.length == 6)
                && types[0] == String.class && types[1] == String.class
                && types[2] == int.class && types[3] == int.class
                && types[4] == sourceArgsType
                && (types.length == 5 || types[5] == String.class);
    }

    public void resolveGetContactServiceApi() {
        ensureSnapshotLoaded();
        try {
            getContactAddMethods.removeIf(method -> !isGetContactAddMethod(method));
            getContactServiceGetters.removeIf(method -> method == null
                    || !isGetContactServiceGetter(method, method.getReturnType()));
            if (getContactAddMethods.isEmpty()) {
                collectGetContactAddMethods("dkverify add Contact");
                collectGetContactAddMethods("[addContact] has consume");
            }
            if (getContactServiceGetters.isEmpty()) {
                collectGetContactServiceGetters();
            }
            logDetail("联系人资料服务方法: " + getContactAddMethods.size());
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveGetContactServiceApi 失败: " + e.getMessage(), e);
        }
    }

    private void collectGetContactAddMethods(String anchor) {
        try {
            List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings(anchor));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isGetContactAddMethod(method)) continue;
                    KavaReflector.accessible(method);
                    if (!getContactAddMethods.contains(method)) {
                        getContactAddMethods.add(method);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void collectGetContactServiceGetters() {
        for (Method addMethod : getContactAddMethods) {
            if (addMethod == null) continue;
            collectServiceGettersForType(addMethod.getDeclaringClass());
            for (Class<?> itf : addMethod.getDeclaringClass().getInterfaces()) {
                collectServiceGettersForType(itf);
            }
        }
    }

    private void collectServiceGettersForType(Class<?> serviceType) {
        if (serviceType == null || serviceType == Object.class) return;
        try {
            FindMethod fm = new FindMethod();
            MethodMatcher mm = new MethodMatcher();
            mm.paramCount(0);
            mm.returnType(serviceType);
            fm.matcher(mm);
            List<MethodData> methods = dexQueries.findMethod(fm);
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isGetContactServiceGetter(method, serviceType)) continue;
                    KavaReflector.accessible(method);
                    if (!getContactServiceGetters.contains(method)) {
                        getContactServiceGetters.add(method);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // ============ 图片消息发送 ============
    public void resolveSendImageApi() {
        ensureSnapshotLoaded();
        try {
            if (isSendImageAppInfoMethod(sendImageMethod)) {
                resolveSendImageAsyncAppInfoApi();
                resolveImageBestPathApi();
                return;
            }

            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings("sendImg: args error"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isSendImageAppInfoMethod(method)) continue;
                    KavaReflector.accessible(method);
                    sendImageMethod = method;
                    break;
                } catch (Throwable ignored) {}
            }
            if (sendImageMethod == null) {
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isSendImageMethod(method)) continue;
                        KavaReflector.accessible(method);
                        sendImageMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            logDetail("图片发送方法: " + methodName(sendImageMethod));
            resolveSendImageAsyncAppInfoApi();
            resolveImageBestPathApi();
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSendImageApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveImageCdnDownloadApi() {
        ensureSnapshotLoaded();
        try {
            resolveMarsCdnDownloadApi();
            if (isImageCdnTaskClass(imageCdnTaskClass)
                    && isPreferredImageCdnSubmitMethod(imageCdnSubmitMethod)
                    && isImageCdnServiceGetterMethod(imageCdnServiceGetterMethod)) {
                return;
            }
            imageCdnSubmitMethod = null;
            imageCdnServiceGetterMethod = null;

            if (!isImageCdnTaskClass(imageCdnTaskClass)) {
                List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings(
                        "field_fullpath",
                        "field_fileId",
                        "field_aesKey",
                        "field_fileType"));
                for (ClassData classData : classes) {
                    try {
                        Class<?> clazz = classData.getInstance(classLoader);
                        if (!isImageCdnTaskClass(clazz)) continue;
                        imageCdnTaskClass = clazz;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (!isImageCdnTaskClass(imageCdnTaskClass)) {
                List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings(
                        "field_fullpath",
                        "field_fileId",
                        "field_fileType"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        Class<?> ret = method.getReturnType();
                        if (isImageCdnTaskClass(ret)) {
                            imageCdnTaskClass = ret;
                            break;
                        }
                        for (Class<?> param : method.getParameterTypes()) {
                            if (!isImageCdnTaskClass(param)) continue;
                            imageCdnTaskClass = param;
                            break;
                        }
                        if (imageCdnTaskClass != null) break;
                    } catch (Throwable ignored) {}
                }
            }

            if (imageCdnTaskClass != null) {
                FindMethod fm = new FindMethod();
                MethodMatcher mm = new MethodMatcher();
                mm.paramCount(2);
                fm.matcher(mm);
                List<MethodData> methods = dexQueries.findMethod(fm);
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isPreferredImageCdnSubmitMethod(method)) continue;
                        KavaReflector.accessible(method);
                        imageCdnSubmitMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (imageCdnTaskClass != null && imageCdnSubmitMethod == null) {
                FindMethod fm = new FindMethod();
                MethodMatcher mm = new MethodMatcher();
                mm.paramCount(1);
                fm.matcher(mm);
                List<MethodData> methods = dexQueries.findMethod(fm);
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isImageCdnSubmitMethod(method)) continue;
                        KavaReflector.accessible(method);
                        imageCdnSubmitMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (imageCdnSubmitMethod == null) {
                List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings(
                        "field_fileId",
                        "field_aesKey",
                        "field_fullpath"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isImageCdnSubmitMethod(method)) continue;
                        KavaReflector.accessible(method);
                        imageCdnSubmitMethod = method;
                        imageCdnTaskClass = method.getParameterTypes()[0];
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (imageCdnSubmitMethod != null) {
                Class<?> owner = imageCdnSubmitMethod.getDeclaringClass();
                FindMethod fm = new FindMethod();
                MethodMatcher mm = new MethodMatcher();
                mm.paramCount(0);
                fm.matcher(mm);
                List<MethodData> methods = dexQueries.findMethod(fm);
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!KavaReflector.isStatic(method)) continue;
                        if (method.getReturnType() != owner) continue;
                        KavaReflector.accessible(method);
                        imageCdnServiceGetterMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            logDetail("图片CDN下载API: task=" + className(imageCdnTaskClass)
                    + " submit=" + methodName(imageCdnSubmitMethod)
                    + " getter=" + methodName(imageCdnServiceGetterMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveImageCdnDownloadApi 失败: " + e.getMessage(), e);
        }
    }

    private void resolveMarsCdnDownloadApi() {
        try {
            if (isMarsCdnReady()) return;
            try {
                marsCdnManagerClass = classLoader.loadClass("com.tencent.mars.cdn.CdnManager");
            } catch (Throwable ignored) {}
            try {
                marsCdnDownloadRequestClass = classLoader.loadClass("com.tencent.mars.cdn.CdnManager$C2CDownloadRequest");
            } catch (Throwable ignored) {}
            if (marsCdnDownloadRequestClass == null) {
                try {
                    marsCdnDownloadRequestClass = classLoader.loadClass("com.tencent.mars.cdn.CdnLogic$C2CDownloadRequest");
                } catch (Throwable ignored) {}
            }
            if (marsCdnDownloadRequestClass == null) {
                List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings("must set marscdnBizType,apptype"));
                for (ClassData classData : classes) {
                    try {
                        Class<?> clazz = classData.getInstance(classLoader);
                        if (isMarsCdnRequestClass(clazz)) {
                            marsCdnDownloadRequestClass = clazz;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (marsCdnDownloadCallbackClass == null) {
                try {
                    marsCdnDownloadCallbackClass = classLoader.loadClass("com.tencent.mars.cdn.CdnManager$DownloadCallback");
                } catch (Throwable ignored) {}
            }
            if (marsCdnDownloadCallbackClass == null) {
                try {
                    marsCdnDownloadCallbackClass = classLoader.loadClass("com.tencent.mars.cdn.CdnLogic$DownloadCallback");
                } catch (Throwable ignored) {}
            }
            if (marsCdnDownloadCallbackClass == null) {
                List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings("aeskey must be 32 bytes"));
                for (ClassData classData : classes) {
                    try {
                        Class<?> clazz = classData.getInstance(classLoader);
                        if (isMarsCdnCallbackClass(clazz)) {
                            marsCdnDownloadCallbackClass = clazz;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (marsCdnManagerClass != null && marsCdnDownloadRequestClass != null && marsCdnDownloadCallbackClass != null) {
                marsCdnStartDownloadMethod = findMarsStartDownloadMethod(
                        marsCdnManagerClass,
                        marsCdnDownloadRequestClass,
                        marsCdnDownloadCallbackClass);
            }
            if (marsCdnManagerClass != null && marsCdnStartDownloadMethod == null) {
                for (Method method : marsCdnManagerClass.getDeclaredMethods()) {
                    if (!"startC2CDownload".equals(method.getName())) continue;
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != 2) continue;
                    KavaReflector.accessible(method);
                    marsCdnStartDownloadMethod = method;
                    marsCdnDownloadRequestClass = params[0];
                    marsCdnDownloadCallbackClass = params[1];
                    break;
                }
            }
            logDetail("Mars CDN下载API: manager=" + className(marsCdnManagerClass)
                    + " request=" + className(marsCdnDownloadRequestClass)
                    + " callback=" + className(marsCdnDownloadCallbackClass)
                    + " start=" + methodName(marsCdnStartDownloadMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveMarsCdnDownloadApi 失败: " + e.getMessage(), e);
        }
    }

    private boolean isMarsCdnRequestClass(Class<?> clazz) {
        if (clazz == null) return false;
        if (clazz.getName().contains("C2CDownloadRequest")) return true;
        return KavaReflector.findMethod(clazz, "setFileid", String.class) != null
                && KavaReflector.findMethod(clazz, "setAeskey", String.class) != null
                && KavaReflector.findMethod(clazz, "setSavePath2", String.class) != null
                && KavaReflector.findMethod(clazz, "setFileType", int.class) != null;
    }

    private boolean isMarsCdnCallbackClass(Class<?> clazz) {
        if (clazz == null) return false;
        String name = clazz.getName();
        return name.contains("DownloadCallback") || name.contains("CdnCallback");
    }

    private boolean sameOrAssignable(Class<?> a, Class<?> b) {
        return a == b || a.isAssignableFrom(b) || b.isAssignableFrom(a);
    }

    private Method findMarsStartDownloadMethod(Class<?> managerClass, Class<?> requestClass, Class<?> callbackClass) {
        if (managerClass == null || requestClass == null || callbackClass == null) return null;
        for (Method method : managerClass.getDeclaredMethods()) {
            if (!"startC2CDownload".equals(method.getName())) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 2) continue;
            if (!sameOrAssignable(params[0], requestClass)) continue;
            if (!sameOrAssignable(params[1], callbackClass)) continue;
            KavaReflector.accessible(method);
            return method;
        }
        return null;
    }

    private void resolveImageBestPathApi() {
        try {
            if (!isImageBestPathMethod(imageBestPathMethod)) {
                imageBestPathMethod = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("[getBigPicPath] msg is null."));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isImageBestPathMethod(method)) continue;
                        KavaReflector.accessible(method);
                        imageBestPathMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            Class<?> storageClass = imageBestPathMethod != null
                    ? imageBestPathMethod.getDeclaringClass() : null;
            if (!isImageStorageGetter(imageStorageGetterMethod, storageClass)) {
                imageStorageGetterMethod = findImageStorageGetter(storageClass);
            }
            if (!isImageTokenPathMethod(imageTokenPathMethod)) {
                imageTokenPathMethod = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings(
                                "THUMBNAIL://",
                                "THUMBNAIL_DIRPATH://",
                                "read img buf failed: "));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isImageTokenPathMethod(method)) continue;
                        imageTokenPathMethod = KavaReflector.accessible(method);
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            logDetail("图片原图路径API: path=" + methodName(imageBestPathMethod)
                    + " storage=" + methodName(imageStorageGetterMethod)
                    + " token=" + methodName(imageTokenPathMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveImageBestPathApi 失败: " + e.getMessage(), e);
        }
    }

    private Method findImageStorageGetter(Class<?> storageClass) {
        if (storageClass == null) return null;
        try {
            FindMethod fm = new FindMethod();
            MethodMatcher mm = new MethodMatcher();
            mm.paramCount(0);
            mm.returnType(storageClass.getName());
            fm.matcher(mm);
            for (MethodData methodData : dexQueries.findMethod(fm)) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isImageStorageGetter(method, storageClass)) continue;
                    return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void resolveSendImageAsyncAppInfoApi() {
        try {
            if (isSendImageAsyncAppInfoApiReady()) return;
            resolveSendImageAsyncParamClasses();
            if (sendImageAppInfoClass == null) {
                sendImageAppInfoClass = findSendImageAppInfoClass();
            }
            if (!isSendImageAsyncSubmitMethod(sendImageAsyncSubmitMethod)) {
                sendImageAsyncSubmitMethod = findSendImageAsyncSubmitMethod();
            }
            logDetail("图片新版appid链路: params=" + className(sendImageAsyncParamsClass)
                    + " cross=" + className(sendImageCrossParamsClass)
                    + " appinfo=" + className(sendImageAppInfoClass)
                    + " submit=" + methodName(sendImageAsyncSubmitMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSendImageAsyncAppInfoApi 失败: " + e.getMessage(), e);
        }
    }

    private boolean isSendImageAsyncAppInfoApiReady() {
        return sendImageAsyncParamsClass != null
                && sendImageCrossParamsClass != null
                && sendImageAppInfoClass != null
                && isSendImageAsyncSubmitMethod(sendImageAsyncSubmitMethod);
    }

    private void resolveSendImageAsyncParamClasses() {
        if (sendImageAsyncParamsClass != null && sendImageCrossParamsClass != null) return;
        try {
            List<ClassData> classes = dexQueries.findClass(
                    mkClassUsingStrings("msg_raw_img_send", "crossParams", "imgPath", "fromUsername", "toUsername"));
            for (ClassData classData : classes) {
                try {
                    Class<?> clazz = KavaReflector.loadClass(classData.getName(), classLoader);
                    Constructor<?> ctor = findSendImageAsyncParamsCtor(clazz);
                    if (ctor == null) continue;
                    sendImageAsyncParamsClass = clazz;
                    sendImageCrossParamsClass = ctor.getParameterTypes()[4];
                    break;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private Class<?> findSendImageAppInfoClass() {
        Class<?> fromCrossParams = findSendImageAppInfoClassFromCrossParams();
        if (fromCrossParams != null) return fromCrossParams;
        try {
            List<ClassData> classes = dexQueries.findClass(
                    mkClassUsingStrings("appid", "mediatagname", "messageext", "messageaction", "appinfo"));
            for (ClassData classData : classes) {
                try {
                    Class<?> clazz = KavaReflector.loadClass(classData.getName(), classLoader);
                    if (isSendImageAppInfoClass(clazz)) return clazz;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Method findSendImageAsyncSubmitMethod() {
        if (sendImageAsyncParamsClass == null) return null;
        try {
            FindMethod fm = new FindMethod();
            MethodMatcher mm = new MethodMatcher();
            mm.paramTypes(sendImageAsyncParamsClass.getName());
            fm.matcher(mm);
            List<MethodData> methods = dexQueries.findMethod(fm);
            Method fallback = null;
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isSendImageAsyncSubmitMethod(method)) continue;
                    KavaReflector.accessible(method);
                    if (isKotlinFlowReturn(method)) return method;
                    if (fallback == null) fallback = method;
                } catch (Throwable ignored) {}
            }
            return fallback;
        } catch (Throwable ignored) {}
        return null;
    }

    public void resolveSendFileApi() {
        ensureSnapshotLoaded();
        try {
            if (isSendFileAppMsgMethod(sendFileMethod)) {
                if (sendFileAttachDirMethod == null || sendFileAttachPathMethod == null) {
                    resolveSendFileAttachHelpers(sendFileMethod.getDeclaringClass());
                }
                return;
            }

            List<Class<?>> candidates = new ArrayList<>();
            collectAppMsgLogicCandidates(candidates, 20,
                    new String[][]{
                            {"summerbig content url:"},
                            {"MicroMsg.AppMsgLogic"},
                            {"/cgi-bin/micromsg-bin/uploadappattach"}
                    });

            for (Class<?> clazz : candidates) {
                Method method = findSendFileAppMsgMethod(clazz);
                if (method == null) continue;
                KavaReflector.accessible(method);
                sendFileMethod = method;
                resolveSendFileAttachHelpers(clazz);
                break;
            }
            if (sendFileMethod != null && (sendFileAttachDirMethod == null || sendFileAttachPathMethod == null)) {
                resolveSendFileAttachHelpers(sendFileMethod.getDeclaringClass());
            }

            logDetail("文件发送方法: " + methodName(sendFileMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSendFileApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveSendXmlApi() {
        ensureSnapshotLoaded();
        try {
            if (isSendXmlAppMsgMethod(sendXmlAppMsgMethod)) {
                resolveAppMsgParseMethod(sendXmlAppMsgMethod.getParameterTypes()[0]);
                return;
            }

            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings("summerbig sendAppMsg attachFilePath"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isSendXmlAppMsgMethod(method)) continue;
                    KavaReflector.accessible(method);
                    sendXmlAppMsgMethod = method;
                    resolveAppMsgParseMethod(method.getParameterTypes()[0]);
                    break;
                } catch (Throwable ignored) {}
            }

            logDetail("XML发送方法: " + methodName(sendXmlAppMsgMethod)
                    + " parse=" + methodName(appMsgParseMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSendXmlApi 失败: " + e.getMessage(), e);
        }
    }

    public synchronized void resolveMsgDescTextApi() {
        ensureSnapshotLoaded();
        try {
            msgDescTextMethods.removeIf(method -> !isMsgDescTextMethod(method));
            if (msgDescTextMethods.isEmpty()) msgDescLocatorResolved = false;
            if (!msgDescLocatorResolved && msgDescCachePrefs != null) {
                for (Method method : DexMethodCache.INSTANCE.loadList(msgDescCachePrefs, runtimeCacheKey,
                        classLoader, "summary_v" + MSG_DESC_LOCATOR_VERSION)) {
                    if (isMsgDescTextMethod(method) && !msgDescTextMethods.contains(method)) {
                        msgDescTextMethods.add(method);
                    }
                }
                msgDescLocatorResolved = !msgDescTextMethods.isEmpty();
            }
            if (msgDescLocatorResolved) return;
            if (!msgDescTextMethods.isEmpty()) {
                msgDescLocatorResolved = true;
                saveCache();
                return;
            }
            // 8.0.76 pt0.r.l: AppMessage resource lookup followed by nullable String.isEmpty().
            // Match behavior and owner as well as signature; "string" alone is far too broad.
            MethodMatcher matcher = new MethodMatcher();
            matcher.name("l");
            matcher.returnType(String.class);
            matcher.paramTypes(android.content.Context.class, boolean.class);
            matcher.usingEqStrings("string");
            matcher.declaredClass(new ClassMatcher().usingEqStrings("MicroMsg.AppMessage"));
            matcher.invokeMethods(new org.luckypray.dexkit.query.matchers.MethodsMatcher()
                    .add(new MethodMatcher().declaredClass("java.lang.String").name("isEmpty").paramTypes())
                    .add(new MethodMatcher().declaredClass("android.content.res.Resources").name("getIdentifier")));
            List<MethodData> methods = dexQueries.findMethod(new FindMethod().matcher(matcher));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isMsgDescTextMethod(method)) continue;
                    KavaReflector.accessible(method);
                    msgDescTextMethods.add(method);
                } catch (Throwable ignored) {}
            }
            if (!msgDescTextMethods.isEmpty()) {
                // The complete global snapshot may be unsavable because an unrelated locator failed.
                // Reuse the established small method cache instead of re-scanning on every launch.
                if (msgDescCachePrefs != null) DexMethodCache.INSTANCE.saveList(msgDescCachePrefs,
                        runtimeCacheKey, "summary_v" + MSG_DESC_LOCATOR_VERSION, msgDescTextMethods);
                logDetail("消息描述文本兜底方法: " + msgDescTextMethods.size() + " 个");
            }
            msgDescLocatorResolved = true;
            saveCache();
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveMsgDescTextApi 失败: " + e.getMessage(), e);
        }
    }

    private boolean isMsgDescTextMethod(Method method) {
        if (method == null) return false;
        if (!"l".equals(method.getName()) || java.lang.reflect.Modifier.isStatic(method.getModifiers())
                || java.lang.reflect.Modifier.isAbstract(method.getModifiers())
                || method.isBridge() || method.isSynthetic()
                || method.getDeclaringClass().isInterface()) return false;
        if (method.getReturnType() != String.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 2 && params[0] == android.content.Context.class && params[1] == boolean.class;
    }

    private void resolveAppMsgParseMethod(Class<?> appMsgClass) {
        if (appMsgClass == null) return;
        if (isAppMsgParseMethod(appMsgParseMethod, appMsgClass)) return;
        try {
            for (Method method : KavaReflector.declaredMethods(appMsgClass)) {
                if (!isAppMsgParseMethod(method, appMsgClass)) continue;
                KavaReflector.accessible(method);
                appMsgParseMethod = method;
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private void resolveSendFileAttachHelpers(Class<?> clazz) {
        if (clazz == null) return;
        try {
            if (sendFileAttachDirMethod == null) {
                for (Method method : KavaReflector.declaredMethods(clazz)) {
                    if (!isStaticNoArgStringMethod(method)) continue;
                    KavaReflector.accessible(method);
                    if (looksLikeAttachDir(method)) {
                        sendFileAttachDirMethod = method;
                        break;
                    }
                }
            }
            if (sendFileAttachPathMethod == null) {
                for (Method method : KavaReflector.declaredMethods(clazz)) {
                    if (!isSendFileAttachPathMethod(method)) continue;
                    KavaReflector.accessible(method);
                    sendFileAttachPathMethod = method;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean looksLikeAttachDir(Method method) {
        try {
            Object value = KavaReflector.invoke(method, null);
            if (!(value instanceof String)) return false;
            String path = ((String) value).toLowerCase();
            return path.contains("attachment") || path.contains("appattach") || path.contains("app_attach");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void resolveSendVideoApi() {
        ensureSnapshotLoaded();
        try {
            if (isSendVideoMethod(sendVideoMethod)) return;
            sendVideoMethod = null;

            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings("send vedio args error"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isSendVideoMethod(method)) continue;
                    KavaReflector.accessible(method);
                    sendVideoMethod = method;
                    break;
                } catch (Throwable ignored) {}
            }

            logDetail("视频发送方法: " + methodName(sendVideoMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSendVideoApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveSendVideoTaskApi() {
        ensureSnapshotLoaded();
        try {
            if (sendVideoTaskClass != null) return;
            sendVideoTaskClass = findFirstClassByStrings(
                    "MicroMsg.MsgRetransmitUI",
                    "CopyVideoTask ori[%s] status[%d] new[%s]");
            logDetail("视频静默Task: "
                    + (sendVideoTaskClass != null ? sendVideoTaskClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSendVideoTaskApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveVideoPathApi() {
        ensureSnapshotLoaded();
        try {
            if (!isVideoPathMethod(videoPathMethod)) {
                videoPathMethod = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings(
                                "MicroMsg.C2CVideoPathFeatureService",
                                "success restore file, from ",
                                ".mp4"));
                if (methods.isEmpty()) {
                    methods = dexQueries.findMethod(
                            mkMethodUsingStrings(
                                    "MicroMsg.VideoInfoStorage",
                                    "success restore file, from ",
                                    ".mp4"));
                }
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isVideoPathMethod(method)) continue;
                        videoPathMethod = KavaReflector.accessible(method);
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            Class<?> owner = videoPathMethod != null ? videoPathMethod.getDeclaringClass() : null;
            if (videoPathMethod != null && !KavaReflector.isStatic(videoPathMethod)) {
                if (!isVideoPathOwnerGetter(videoPathOwnerGetterMethod, owner)) {
                    videoPathOwnerGetterMethod = findVideoPathOwnerGetter(owner);
                }
            } else {
                videoPathOwnerGetterMethod = null;
            }
            logDetail("视频消息路径API: path=" + methodName(videoPathMethod)
                    + " owner=" + methodName(videoPathOwnerGetterMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveVideoPathApi 失败: " + e.getMessage(), e);
        }
    }

    private boolean isVideoPathMethod(Method method) {
        return method != null
                && method.getReturnType() == String.class
                && method.getParameterTypes().length == 1
                && method.getParameterTypes()[0] == String.class;
    }

    private Method findVideoPathOwnerGetter(Class<?> owner) {
        if (owner == null) return null;
        try {
            FindMethod fm = new FindMethod();
            MethodMatcher mm = new MethodMatcher();
            mm.paramCount(0);
            mm.returnType(owner.getName());
            fm.matcher(mm);
            for (MethodData methodData : dexQueries.findMethod(fm)) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isVideoPathOwnerGetter(method, owner)) continue;
                    return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean isVideoPathOwnerGetter(Method method, Class<?> owner) {
        return method != null
                && owner != null
                && KavaReflector.isStatic(method)
                && method.getParameterTypes().length == 0
                && owner.isAssignableFrom(method.getReturnType());
    }

    public void resolveVideoInfoApi() {
        ensureSnapshotLoaded();
        try {
            if (isVideoInfoByFileNameMethod(videoInfoByFileNameMethod, videoInfoClass)) return;
            videoInfoByFileNameMethod = null;

            videoInfoClass = findFirstClassByStrings("VideoInfo{fileName='");
            if (videoInfoClass == null) return;

            FindMethod find = new FindMethod();
            MethodMatcher matcher = new MethodMatcher();
            matcher.paramTypes(String.class);
            matcher.returnType(videoInfoClass.getName());
            find.matcher(matcher);

            List<Method> candidates = new ArrayList<>();
            for (MethodData data : dexQueries.findMethod(find)) {
                try {
                    Method method = data.getMethodInstance(classLoader);
                    if (!isVideoInfoByFileNameMethod(method, videoInfoClass)) continue;
                    if (!samePackage(method.getDeclaringClass(), videoInfoClass)) continue;
                    Method accessible = KavaReflector.accessible(method);
                    if (!candidates.contains(accessible)) candidates.add(accessible);
                } catch (Throwable ignored) {}
            }
            if (candidates.size() == 1) {
                videoInfoByFileNameMethod = candidates.get(0);
            }
            logDetail("视频信息查询API: " + methodName(videoInfoByFileNameMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveVideoInfoApi 失败: " + e.getMessage(), e);
        }
    }

    private boolean isVideoInfoByFileNameMethod(Method method, Class<?> expectedReturnType) {
        return method != null
                && expectedReturnType != null
                && KavaReflector.isStatic(method)
                && method.getParameterTypes().length == 1
                && method.getParameterTypes()[0] == String.class
                && method.getReturnType() == expectedReturnType;
    }

    private boolean samePackage(Class<?> left, Class<?> right) {
        if (left == null || right == null) return false;
        String leftName = left.getName();
        String rightName = right.getName();
        int leftDot = leftName.lastIndexOf('.');
        int rightDot = rightName.lastIndexOf('.');
        return leftDot == rightDot
                && leftDot >= 0
                && leftName.regionMatches(0, rightName, 0, leftDot);
    }

    public void resolveSendVoiceApi() {
        ensureSnapshotLoaded();
        try {
            if (!isVoiceStartRecordMethod(voiceStartRecordMethod)) {
                voiceStartRecordMethod = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("startRecord insert voicestg success"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isVoiceStartRecordMethod(method)) continue;
                        KavaReflector.accessible(method);
                        voiceStartRecordMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (!isVoiceFullPathMethod(voiceFullPathMethod)) {
                voiceFullPathMethod = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("getAmrFullPath cost:"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isVoiceFullPathMethod(method)) continue;
                        KavaReflector.accessible(method);
                        voiceFullPathMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (voiceMessagePathMethod != null && !isVoiceMessagePathMethod(voiceMessagePathMethod)) {
                voiceMessagePathMethod = null;
                voiceMessagePathLookupComplete = false;
            }
            if (voiceMessagePathMethod == null && !voiceMessagePathLookupComplete) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("[f11]getVoiceFullPath, businessType: "));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isVoiceMessagePathMethod(method)) continue;
                        voiceMessagePathMethod = KavaReflector.accessible(method);
                        break;
                    } catch (Throwable ignored) {}
                }
                voiceMessagePathLookupComplete = methods.isEmpty() || voiceMessagePathMethod != null;
            }

            if (voiceFinishRecordMethod == null && voiceStartRecordMethod != null) {
                Class<?> owner = voiceStartRecordMethod.getDeclaringClass();
                for (Method method : KavaReflector.declaredMethods(owner)) {
                    if (!isVoiceFinishRecordMethod(method)) continue;
                    KavaReflector.accessible(method);
                    voiceFinishRecordMethod = method;
                    break;
                }
            }

            if (voiceInfoQueryMethod == null) {
                voiceInfoQueryMethod = findVoiceInfoQueryMethod();
            }

            if (voiceUploadClass == null || (voiceUploadCtor == null && voiceUploadCdnCtor == null)) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("/cgi-bin/micromsg-bin/uploadvoice"));
                for (MethodData methodData : methods) {
                    try {
                        Class<?> clazz = KavaReflector.loadClass(methodData.getClassName(), classLoader);
                        Constructor<?> ctor = findCtorByExactTypes(clazz, String.class, int.class);
                        Constructor<?> cdnCtor = findCtorByExactTypes(clazz, String.class, boolean.class);
                        if (ctor == null && cdnCtor == null) continue;
                        voiceUploadClass = clazz;
                        voiceUploadCtor = ctor;
                        voiceUploadCdnCtor = cdnCtor;
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            if (voiceUploadClass != null && voiceUploadCdnCtor == null) {
                voiceUploadCdnCtor = findCtorByExactTypes(voiceUploadClass, String.class, boolean.class);
            }

            resolveVoicePlaybackApi();

            logDetail("语音发送API: start=" + methodName(voiceStartRecordMethod)
                    + " path=" + methodName(voiceFullPathMethod)
                    + " messagePath=" + methodName(voiceMessagePathMethod)
                    + " finish=" + methodName(voiceFinishRecordMethod)
                    + " info=" + methodName(voiceInfoQueryMethod)
                    + " upload=" + (voiceUploadClass != null ? voiceUploadClass.getName() : "null")
                    + " cdnCtor=" + (voiceUploadCdnCtor != null));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSendVoiceApi 失败: " + e.getMessage(), e);
        }
    }

    private void resolveVoicePlaybackApi() {
        if (!isVoicePlaybackStartMethod(voicePlaybackStartMethod)) {
            voicePlaybackStartMethod = findVoicePlaybackMethod(
                    null,
                    "start file name:[%s] speakerOn:[%b], isFullPath: %s, type: %s, userType: %s",
                    boolean.class,
                    String.class, boolean.class, boolean.class, int.class);
        }
        Class<?> owner = voicePlaybackStartMethod != null
                ? voicePlaybackStartMethod.getDeclaringClass()
                : null;
        if (owner == null) {
            voicePlaybackPauseMethod = null;
            voicePlaybackResumeMethod = null;
            voicePlaybackStopMethod = null;
            return;
        }
        if (!isExactInstanceMethod(voicePlaybackPauseMethod, owner, boolean.class, boolean.class)) {
            voicePlaybackPauseMethod = findVoicePlaybackMethod(
                    owner, "pause ret:%s", boolean.class, boolean.class);
        }
        if (!isExactInstanceMethod(voicePlaybackResumeMethod, owner, boolean.class)) {
            voicePlaybackResumeMethod = findVoicePlaybackMethod(
                    owner, "resumePlaying set mute false", boolean.class);
        }
        if (!isExactInstanceMethod(voicePlaybackStopMethod, owner, void.class, boolean.class)) {
            voicePlaybackStopMethod = findVoicePlaybackMethod(
                    owner, "stop player failed cause player is null %s", void.class, boolean.class);
        }
        logDetail("语音播放API: start=" + methodName(voicePlaybackStartMethod)
                + " pause=" + methodName(voicePlaybackPauseMethod)
                + " resume=" + methodName(voicePlaybackResumeMethod)
                + " stop=" + methodName(voicePlaybackStopMethod));
    }

    private Method findVoicePlaybackMethod(Class<?> owner,
                                           String anchor,
                                           Class<?> returnType,
                                           Class<?>... parameterTypes) {
        try {
            for (MethodData data : dexQueries.findMethod(mkMethodUsingStrings(anchor))) {
                try {
                    Method method = data.getMethodInstance(classLoader);
                    Class<?> expectedOwner = owner != null ? owner : method.getDeclaringClass();
                    if (!isExactInstanceMethod(method, expectedOwner, returnType, parameterTypes)) continue;
                    return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean isVoicePlaybackStartMethod(Method method) {
        return method != null
                && isExactInstanceMethod(
                        method,
                        method.getDeclaringClass(),
                        boolean.class,
                        String.class, boolean.class, boolean.class, int.class)
                && findCtorByExactTypes(method.getDeclaringClass(), Context.class, int.class) != null;
    }

    private boolean isExactInstanceMethod(Method method,
                                          Class<?> owner,
                                          Class<?> returnType,
                                          Class<?>... parameterTypes) {
        return method != null
                && owner != null
                && method.getDeclaringClass() == owner
                && !KavaReflector.isStatic(method)
                && method.getReturnType() == returnType
                && Arrays.equals(method.getParameterTypes(), parameterTypes);
    }

    private Method findVoiceInfoQueryMethod() {
        List<Method> wrappers = new ArrayList<>();
        try {
            FindMethod find = new FindMethod();
            MethodMatcher matcher = new MethodMatcher();
            matcher.paramTypes(String.class);
            matcher.addUsingString("voiceinfo WHERE FileName= ?", StringMatchType.Contains, false);
            find.matcher(matcher);
            for (MethodData data : dexQueries.findMethod(find)) {
                Method storageMethod;
                try {
                    storageMethod = data.getMethodInstance(classLoader);
                } catch (Throwable ignored) {
                    continue;
                }
                if (!isVoiceInfoStorageQueryMethod(storageMethod)) continue;
                for (MethodData callerData : data.getCallers()) {
                    try {
                        Method caller = callerData.getMethodInstance(classLoader);
                        if (!isVoiceInfoQueryMethod(caller, storageMethod.getReturnType())) continue;
                        KavaReflector.accessible(caller);
                        if (!wrappers.contains(caller)) wrappers.add(caller);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return wrappers.size() == 1 ? wrappers.get(0) : null;
    }

    private boolean isVoiceInfoStorageQueryMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        Class<?> returnType = method.getReturnType();
        return params.length == 1
                && params[0] == String.class
                && returnType != void.class
                && !returnType.isPrimitive();
    }

    private boolean isVoiceInfoQueryMethod(Method method, Class<?> voiceInfoClass) {
        if (method == null || voiceInfoClass == null || !KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1
                && params[0] == String.class
                && method.getReturnType() == voiceInfoClass;
    }

    public void resolveSendEmojiApi() {
        ensureSnapshotLoaded();
        try {
            if (!isEmojiSendMethod(emojiSendMethod)) {
                emojiSendMethod = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("NetSceneUploadEmoji: msgId"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isEmojiSendMethod(method)) continue;
                        KavaReflector.accessible(method);
                        emojiSendMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (!isEmojiManagerSendMethod(emojiManagerSendMethod)) {
                emojiManagerSendMethod = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("sendEmoji: context is null", "sendEmoji: emoji not found"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isEmojiManagerSendMethod(method)) continue;
                        KavaReflector.accessible(method);
                        emojiManagerSendMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (!isEmojiGetByMd5Method(emojiGetByMd5Method)) {
                emojiGetByMd5Method = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("getEmojiByMd5"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isEmojiGetByMd5Method(method)) continue;
                        KavaReflector.accessible(method);
                        emojiGetByMd5Method = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (!isEmojiCreateInfoMethod(emojiCreateInfoMethod)) {
                emojiCreateInfoMethod = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("createEmojiInfo"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isEmojiCreateInfoMethod(method)) continue;
                        KavaReflector.accessible(method);
                        emojiCreateInfoMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (!isEmojiUpdateInfoMethod(emojiUpdateInfoMethod)) {
                emojiUpdateInfoMethod = null;
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("updateEmojiInfo"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isEmojiUpdateInfoMethod(method)) continue;
                        KavaReflector.accessible(method);
                        emojiUpdateInfoMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (emojiAccPathMethod == null) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("getAccPath"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isNoArgStringMethod(method)) continue;
                        KavaReflector.accessible(method);
                        emojiAccPathMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (emojiCheckGifMethod == null) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("checkGifFile"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isStringBooleanMethod(method)) continue;
                        KavaReflector.accessible(method);
                        emojiCheckGifMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (emojiFilePathMethod == null) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("[cpan] get icon path failed. productid and md5 are null."));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isEmojiFilePathMethod(method)) continue;
                        KavaReflector.accessible(method);
                        emojiFilePathMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (emojiDecodeDataMethod == null || emojiDecodeManagerGetterMethod == null) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings(
                                "MicroMsg.emoji.EmojiFileEncryptMgr",
                                "decode emoji file failed. path is no exist :%s "));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isEmojiDecodeDataMethod(method)) continue;
                        Method getter = findEmojiDecodeManagerGetter(method.getDeclaringClass());
                        if (getter == null) continue;
                        emojiDecodeDataMethod = KavaReflector.accessible(method);
                        emojiDecodeManagerGetterMethod = KavaReflector.accessible(getter);
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            logDetail("表情发送API: send=" + methodName(emojiSendMethod)
                    + " managerSend=" + methodName(emojiManagerSendMethod)
                    + " getByMd5=" + methodName(emojiGetByMd5Method)
                    + " create=" + methodName(emojiCreateInfoMethod)
                    + " accPath=" + methodName(emojiAccPathMethod)
                    + " decode=" + methodName(emojiDecodeDataMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSendEmojiApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveFavoriteApi() {
        ensureSnapshotLoaded();
        try {
            resolveFavoriteItemApi();
            resolveFavoriteListApi();
            resolveFavoriteSendApi();
            logDetail("收藏API: item="
                    + (favoriteItemClass != null ? favoriteItemClass.getName() : "null")
                    + " convert=" + methodName(favoriteItemConvertFromCursorMethod)
                    + " list=" + methodName(favoriteListMethod)
                    + " send=" + methodName(favoriteSendMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveFavoriteApi 失败: " + e.getMessage(), e);
        }
    }

    private void resolveFavoriteListApi() {
        try {
            if (favoriteServiceClass != null
                    && isFavoriteServiceResolverMethod(favoriteServiceResolverMethod)
                    && isFavoriteStorageGetterMethod(favoriteStorageGetterMethod)
                    && isFavoriteListMethod(favoriteListMethod)
                    && isFavoriteListNextMethod(favoriteListNextMethod)
                    && isFavoriteListCursorMethod(favoriteListCursorMethod)
                    && isFavoriteGetMethod(favoriteGetMethod)) return;

            favoriteServiceClass = null;
            favoriteServiceResolverMethod = null;
            favoriteStorageGetterMethod = null;
            favoriteListMethod = null;
            favoriteListNextMethod = null;
            favoriteListCursorMethod = null;
            favoriteGetMethod = null;
            Method cursor = findFavoriteListCursorMethod();
            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings(
                            "getItemList error, getFavItemInfoStorage null.",
                            "MicroMsg.Fav.FavApiLogic"));
            for (MethodData methodData : methods) {
                Method resolver = null;
                Method getter = null;
                Method list = null;
                Method next = null;
                Class<?> service = null;
                for (MethodData invokeData : methodData.getInvokes()) {
                    Method invoked;
                    try {
                        invoked = invokeData.getMethodInstance(classLoader);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (isFavoriteServiceResolverMethod(invoked)) resolver = invoked;
                    if (isFavoriteListMethod(invoked)) list = invoked;
                    if (isFavoriteListNextMethod(invoked)) next = invoked;
                }
                if (list != null) {
                    for (MethodData invokeData : methodData.getInvokes()) {
                        Method invoked;
                        try {
                            invoked = invokeData.getMethodInstance(classLoader);
                        } catch (Throwable ignored) {
                            continue;
                        }
                        if (!isFavoriteStorageGetterMethod(invoked)) continue;
                        if (invoked.getReturnType() != list.getDeclaringClass()) continue;
                        getter = invoked;
                        service = invoked.getDeclaringClass();
                        break;
                    }
                }
                if (resolver == null || getter == null || list == null || next == null
                        || cursor == null || service == null) continue;
                KavaReflector.accessible(resolver);
                KavaReflector.accessible(getter);
                KavaReflector.accessible(list);
                KavaReflector.accessible(next);
                favoriteServiceClass = service;
                favoriteServiceResolverMethod = resolver;
                favoriteStorageGetterMethod = getter;
                favoriteListMethod = list;
                favoriteListNextMethod = next;
                favoriteListCursorMethod = cursor;
                favoriteGetMethod = findFavoriteGetMethod(list.getDeclaringClass());
                if (favoriteGetMethod == null) continue;
                KavaReflector.accessible(favoriteGetMethod);
                return;
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveFavoriteListApi 失败: " + e.getMessage(), e);
        }
    }

    private boolean isFavoriteServiceResolverMethod(Method method) {
        if (method == null || !java.lang.reflect.Modifier.isStatic(method.getModifiers())) return false;
        Class<?>[] p = method.getParameterTypes();
        return p.length == 1 && p[0] == Class.class && method.getReturnType() != void.class;
    }

    private boolean isFavoriteStorageGetterMethod(Method method) {
        if (method == null || java.lang.reflect.Modifier.isStatic(method.getModifiers())) return false;
        return method.getParameterTypes().length == 0
                && method.getReturnType() != void.class
                && !method.getReturnType().isPrimitive();
    }

    private boolean isFavoriteListMethod(Method method) {
        if (method == null || java.lang.reflect.Modifier.isStatic(method.getModifiers())) return false;
        Class<?>[] p = method.getParameterTypes();
        return java.util.List.class.isAssignableFrom(method.getReturnType())
                && p.length == 5
                && p[0] == int.class
                && p[1] == int.class
                && java.util.List.class.isAssignableFrom(p[2])
                && java.util.Set.class.isAssignableFrom(p[3]);
    }

    private boolean isFavoriteListNextMethod(Method method) {
        if (method == null || java.lang.reflect.Modifier.isStatic(method.getModifiers())) return false;
        Class<?>[] p = method.getParameterTypes();
        return java.util.List.class.isAssignableFrom(method.getReturnType())
                && p.length == 5
                && p[0] == long.class
                && p[1] == int.class
                && java.util.List.class.isAssignableFrom(p[2])
                && java.util.Set.class.isAssignableFrom(p[3]);
    }

    private Method findFavoriteListCursorMethod() {
        List<MethodData> methods = dexQueries.findMethod(
                mkMethodUsingStrings("tryStartBatchGet..."));
        for (MethodData methodData : methods) {
            try {
                Method method = methodData.getMethodInstance(classLoader);
                if (!isFavoriteListCursorMethod(method)) continue;
                KavaReflector.accessible(method);
                return method;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private Class<?> findSendImageAppInfoClassFromCrossParams() {
        if (sendImageCrossParamsClass == null) return null;
        List<Class<?>> indexedCandidates = new ArrayList<>();
        for (Field field : KavaReflector.declaredFields(sendImageCrossParamsClass)) {
            Class<?> type = field.getType();
            if (type == null
                    || type.isPrimitive()
                    || type == String.class
                    || type == Object.class
                    || type.getName().startsWith("java.")) {
                continue;
            }
            if (hasDirectImageAppInfoFields(type)) return type;
            if (isSendImageAppInfoClass(type)) indexedCandidates.add(type);
        }
        return indexedCandidates.isEmpty() ? null : indexedCandidates.get(0);
    }

    private boolean isFavoriteListCursorMethod(Method method) {
        if (method == null || !java.lang.reflect.Modifier.isStatic(method.getModifiers())) return false;
        Class<?>[] p = method.getParameterTypes();
        return method.getReturnType() == long.class
                && p.length == 3
                && p[0] == long.class
                && p[1] == int.class
                && p[2] == int.class;
    }

    private Method findFavoriteGetMethod(Class<?> storageClass) {
        if (storageClass == null || favoriteItemClass == null) return null;
        for (Method method : KavaReflector.declaredMethods(storageClass)) {
            if (!isFavoriteGetMethod(method)) continue;
            return method;
        }
        return null;
    }

    private boolean isFavoriteGetMethod(Method method) {
        if (method == null || java.lang.reflect.Modifier.isStatic(method.getModifiers())) return false;
        Class<?>[] p = method.getParameterTypes();
        return p.length == 1 && p[0] == long.class
                && favoriteItemClass != null
                && favoriteItemClass.isAssignableFrom(method.getReturnType());
    }

    private void resolveFavoriteItemApi() {
        try {
            if (isFavoriteItemClass(favoriteItemClass)) {
                favoriteItemConvertFromCursorMethod = findFavoriteItemConvertFromCursorMethod(favoriteItemClass);
                if (favoriteItemConvertFromCursorMethod != null) return;
            }

            favoriteItemClass = null;
            favoriteItemConvertFromCursorMethod = null;

            List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings("FavItemInfo"));
            for (ClassData classData : classes) {
                Class<?> clazz = KavaReflector.loadClass(classData.getName(), classLoader);
                if (!isFavoriteItemClass(clazz)) continue;
                favoriteItemClass = clazz;
                favoriteItemConvertFromCursorMethod = findFavoriteItemConvertFromCursorMethod(clazz);
                if (favoriteItemConvertFromCursorMethod != null) return;
            }

            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings("sendFavMsg: processing favId=%d, favType=%d, dataListSize=%d"));
            for (MethodData methodData : methods) {
                Class<?> clazz = favoriteItemClassFromUsingFields(methodData);
                if (!isFavoriteItemClass(clazz)) continue;
                favoriteItemClass = clazz;
                favoriteItemConvertFromCursorMethod = findFavoriteItemConvertFromCursorMethod(clazz);
                if (favoriteItemConvertFromCursorMethod != null) return;
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveFavoriteItemApi 失败: " + e.getMessage(), e);
        }
    }

    private void resolveFavoriteSendApi() {
        try {
            Method cached = findFavoriteSendDispatchMethod(favoriteSendMethod);
            if (cached != null) {
                favoriteSendMethod = cached;
                return;
            }

            favoriteSendMethod = null;
            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings(
                            "want to send fav msg, but context is null",
                            "want to send fav msg, but info is null"));
            for (MethodData methodData : methods) {
                Method anchor = methodData.getMethodInstance(classLoader);
                Method dispatch = findFavoriteSendDispatchMethod(anchor);
                if (dispatch == null) continue;
                favoriteSendMethod = dispatch;
                return;
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveFavoriteSendApi 失败: " + e.getMessage(), e);
        }
    }

    private Method findFavoriteSendDispatchMethod(Method anchor) {
        if (anchor == null) return null;
        if (isFavoriteSendMethod(anchor)) return KavaReflector.accessible(anchor);
        for (Method method : KavaReflector.declaredMethods(anchor.getDeclaringClass())) {
            if (!isFavoriteSendMethod(method)) continue;
            return KavaReflector.accessible(method);
        }
        return null;
    }

    private Class<?> favoriteItemClassFromUsingFields(MethodData methodData) {
        if (methodData == null) return null;
        try {
            for (UsingFieldData usingFieldData : methodData.getUsingFields()) {
                FieldData fieldData = usingFieldData.getField();
                if (fieldData == null) continue;
                if (!"field_favProto".equals(fieldData.getFieldName())) continue;
                Class<?> clazz = KavaReflector.loadClass(fieldData.getClassName(), classLoader);
                if (isFavoriteItemClass(clazz)) return clazz;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isFavoriteItemClass(Class<?> clazz) {
        if (clazz == null) return false;
        if (KavaReflector.findFieldRecursive(clazz, "field_localId") == null) return false;
        if (KavaReflector.findFieldRecursive(clazz, "field_type") == null) return false;
        if (KavaReflector.findFieldRecursive(clazz, "field_favProto") == null) return false;
        if (KavaReflector.findConstructor(clazz) == null) return false;
        return findFavoriteItemConvertFromCursorMethod(clazz) != null;
    }

    private Method findFavoriteItemConvertFromCursorMethod(Class<?> clazz) {
        if (clazz == null) return null;
        Method named = KavaReflector.findMethodRecursive(clazz, "convertFrom", Cursor.class);
        if (isFavoriteItemConvertFromCursorMethod(named)) {
            KavaReflector.accessible(named);
            return named;
        }
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : KavaReflector.declaredMethods(current)) {
                if (!isFavoriteItemConvertFromCursorMethod(method)) continue;
                KavaReflector.accessible(method);
                return method;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private boolean isFavoriteItemConvertFromCursorMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1 && Cursor.class.isAssignableFrom(params[0]);
    }

    private boolean isFavoriteSendMethod(Method method) {
        if (method == null || !KavaReflector.isStatic(method) || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length == 6) {
            return Context.class.isAssignableFrom(params[0])
                    && params[1] == String.class
                    && params[2] == String.class
                    && params[3] == boolean.class
                    && favoriteItemClass != null
                    && (params[4].isAssignableFrom(favoriteItemClass)
                        || favoriteItemClass.isAssignableFrom(params[4]))
                    && Runnable.class.isAssignableFrom(params[5]);
        }
        return params.length == 5
                && Context.class.isAssignableFrom(params[0])
                && params[1] == String.class
                && params[2] == String.class
                && List.class.isAssignableFrom(params[3])
                && Runnable.class.isAssignableFrom(params[4]);
    }

    public void resolveTransferOperationApi() {
        ensureSnapshotLoaded();
        try {
            if (isTransferOperationClass(transferOperationClass)) return;

            try {
                Class<?> direct = KavaReflector.loadClass(
                        "com.tencent.mm.plugin.remittance.model.n0", classLoader);
                if (isTransferOperationClass(direct)) {
                    transferOperationClass = direct;
                    logDetail("转账操作类: " + transferOperationClass.getName());
                    return;
                }
            } catch (Throwable ignored) {}

            List<ClassData> classes = dexQueries.findClass(
                    mkClassUsingStrings("Micromsg.NetSceneTenpayRemittanceConfirm",
                            "recv_account_type"));
            for (ClassData cd : classes) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    if (!isTransferOperationClass(cl)) continue;
                    transferOperationClass = cl;
                    break;
                } catch (Throwable ignored) {}
            }

            if (transferOperationClass == null) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("/cgi-bin/mmpay-bin/transferoperation"));
                for (MethodData methodData : methods) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(methodData.getClassName(), classLoader);
                        if (!isTransferOperationClass(cl)) continue;
                        transferOperationClass = cl;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            logDetail("转账操作类: "
                    + (transferOperationClass != null ? transferOperationClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveTransferOperationApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveTransferQueryApi() {
        ensureSnapshotLoaded();
        try {
            if (isTransferQueryClass(transferQueryClass)
                    && isTransferQueryResponseMethod(transferQueryResponseMethod, transferQueryClass)) return;

            List<ClassData> classes = dexQueries.findClass(
                    mkClassUsingStrings("Micromsg.NetSceneTenpayRemittanceQuery",
                            "recv_account_info", "recv_channel"));
            for (ClassData cd : classes) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    Method response = findTransferQueryResponseMethod(cl);
                    if (!isTransferQueryClass(cl) || response == null) continue;
                    transferQueryClass = cl;
                    transferQueryResponseMethod = response;
                    break;
                } catch (Throwable ignored) {}
            }
            logDetail("转账查询类: "
                    + (transferQueryClass != null ? transferQueryClass.getName() : "null")
                    + " response=" + methodName(transferQueryResponseMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveTransferQueryApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveVerifyUserApi() {
        ensureSnapshotLoaded();
        try {
            if (isVerifyUserClass(verifyUserClass)) return;

            List<ClassData> classes = dexQueries.findClass(
                    mkClassUsingStrings("/cgi-bin/micromsg-bin/verifyuser",
                            "MicroMsg.NetSceneVerifyUser.dkverify"));
            for (ClassData cd : classes) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    if (!isVerifyUserClass(cl)) continue;
                    verifyUserClass = cl;
                    break;
                } catch (Throwable ignored) {}
            }

            if (verifyUserClass == null) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("/cgi-bin/micromsg-bin/verifyuser"));
                for (MethodData methodData : methods) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(methodData.getClassName(), classLoader);
                        if (!isVerifyUserClass(cl)) continue;
                        verifyUserClass = cl;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            logDetail("好友申请验证类: "
                    + (verifyUserClass != null ? verifyUserClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveVerifyUserApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveContactCardApi() {
        ensureSnapshotLoaded();
        try {
            if (isContactCardXmlMethod(contactCardXmlMethod)) return;

            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings("MicroMsg.SendContactCardHelper", "getBizNameCardString"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isContactCardXmlMethod(method)) continue;
                    contactCardXmlMethod = KavaReflector.accessible(method);
                    break;
                } catch (Throwable ignored) {}
            }

            if (contactCardXmlMethod == null) {
                methods = dexQueries.findMethod(mkMethodUsingStrings("bigheadimgurl", "smallheadimgurl"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isContactCardXmlMethod(method)) continue;
                        contactCardXmlMethod = KavaReflector.accessible(method);
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            logDetail("名片XML方法: " + methodName(contactCardXmlMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveContactCardApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolvePatMessageApi() {
        ensureSnapshotLoaded();
        try {
            if (!isPatDisplayTemplateMethod(patDisplayTemplateMethod)) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("MicroMsg.PluginPatMsg",
                                "parseDisplayTemplate realtime templateStr:%s"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isPatDisplayTemplateMethod(method)) continue;
                        KavaReflector.accessible(method);
                        patDisplayTemplateMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            resolvePatSendApi();
            logDetail("拍一拍API: template=" + methodName(patDisplayTemplateMethod)
                    + " extension=" + (patExtensionClass != null ? patExtensionClass.getName() : "null")
                    + " create=" + methodName(patCreatePairMethod)
                    + " suffix=" + methodName(patSuffixMethod)
                    + " scene=" + (sendPatSceneClass != null ? sendPatSceneClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolvePatMessageApi 失败: " + e.getMessage(), e);
        }
    }

    private void resolvePatSendApi() {
        if (sendPatSceneCtor == null) {
            sendPatSceneCtor = findSendPatSceneCtor(sendPatSceneClass);
        }
        if (isPatCreatePairMethod(patCreatePairMethod)
                && isPatSuffixMethod(patSuffixMethod)
                && sendPatSceneCtor != null) {
            return;
        }
        resolvePatExtensionApi();
        resolveSendPatSceneApi();
    }

    private void resolvePatExtensionApi() {
        try {
            if (isPatCreatePairMethod(patCreatePairMethod)
                    && isPatSuffixMethod(patSuffixMethod)
                    && patExtensionClass != null) {
                return;
            }
            if (!isPatCreatePairMethod(patCreatePairMethod)) {
                patCreatePairMethod = findPatCreatePairMethodByStrings(
                        "MicroMsg.PatMsgExtension", "insert pat msg %d %s %s");
                if (patCreatePairMethod == null) {
                    patCreatePairMethod = findPatCreatePairMethodByStrings("insert pat msg %d %s %s");
                }
            }
            if (!isPatSuffixMethod(patSuffixMethod)) {
                patSuffixMethod = findPatSuffixMethodByStrings(
                        "MicroMsg.PatMsgExtension", "pattedUser %s, suffix %s");
                if (patSuffixMethod == null) {
                    patSuffixMethod = findPatSuffixMethodByStrings("pattedUser %s, suffix %s");
                }
            }
            if (!isPatCanSendMethod(patCanSendMethod)) {
                patCanSendMethod = findPatCanSendMethodByStrings(
                        "MicroMsg.PatMsgExtension", "cannot pat, talker %s");
                if (patCanSendMethod == null) {
                    patCanSendMethod = findPatCanSendMethodByStrings("cannot pat, talker %s");
                }
            }
            if (patCreatePairMethod != null && patSuffixMethod != null
                    && patCreatePairMethod.getDeclaringClass() == patSuffixMethod.getDeclaringClass()) {
                patExtensionClass = patCreatePairMethod.getDeclaringClass();
                if (!isPatCanSendMethod(patCanSendMethod)) {
                    patCanSendMethod = findPatCanSendMethod(patExtensionClass);
                }
                return;
            }
            List<Class<?>> candidates = new ArrayList<>();
            collectSendTextMethodOwnerCandidates(candidates, 20,
                    new String[][]{
                            {"MicroMsg.PatMsgExtension", "insert pat msg %d %s %s"},
                            {"MicroMsg.PatMsgExtension", "pattedUser %s, suffix %s"},
                            {"insert pat msg %d %s %s"},
                            {"pattedUser %s, suffix %s"}
                    });
            for (Class<?> candidate : candidates) {
                Method createMethod = findPatCreatePairMethod(candidate);
                Method suffixMethod = findPatSuffixMethod(candidate);
                if (createMethod == null || suffixMethod == null) continue;
                patExtensionClass = candidate;
                patCreatePairMethod = createMethod;
                patSuffixMethod = suffixMethod;
                patCanSendMethod = findPatCanSendMethod(candidate);
                break;
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolvePatExtensionApi 失败: " + e.getMessage(), e);
        }
    }

    private Method findPatCreatePairMethodByStrings(String... strings) {
        try {
            List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings(strings));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isPatCreatePairMethod(method)) continue;
                    return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Method findPatSuffixMethodByStrings(String... strings) {
        try {
            List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings(strings));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isPatSuffixMethod(method)) continue;
                    return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Method findPatCanSendMethodByStrings(String... strings) {
        try {
            List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings(strings));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isPatCanSendMethod(method)) continue;
                    return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void resolveSendPatSceneApi() {
        try {
            if (sendPatSceneCtor == null) {
                sendPatSceneCtor = findSendPatSceneCtor(sendPatSceneClass);
            }
            if (sendPatSceneCtor != null) return;
            List<Class<?>> candidates = new ArrayList<>();
            collectSendTextClassCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/sendpat"},
                            {"MicroMsg.NetSceneSendPat"},
                            {"sendpat"}
                    });
            collectSendTextMethodOwnerCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/sendpat"},
                            {"MicroMsg.NetSceneSendPat"}
                    });
            for (Class<?> candidate : candidates) {
                if (!isSendPatSceneClass(candidate)) continue;
                sendPatSceneClass = candidate;
                sendPatSceneCtor = findSendPatSceneCtor(candidate);
                break;
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSendPatSceneApi 失败: " + e.getMessage(), e);
        }
    }

    // ============ 数据库/联系人公共 API ============
    public void resolveDatabaseApi() {
        ensureSnapshotLoaded();
        try {
            if (sqliteDbWrapperClass != null
                    && !WeChatSqliteWrapperLocator.supportsMessageMutations(sqliteDbWrapperClass)) {
                logDetail("SqliteDB 缓存候选结构失效: " + sqliteDbWrapperClass.getName());
                sqliteDbWrapperClass = null;
            }
            if (coreStorageGetter != null && sqliteDbWrapperClass != null && configStorageClass != null) {
                return;
            }

            mmKernelClass = findFirstClassByStrings(
                    "MicroMsg.MMKernel",
                    "Kernel not null, has initialized.");

            List<MethodData> storageMethods = dexQueries.findMethod(
                    mkMethodUsingStrings("mCoreStorage not initialized!"));
            for (MethodData methodData : storageMethods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (method.getParameterTypes().length != 0) continue;
                    if (method.getReturnType() == void.class) continue;
                    KavaReflector.accessible(method);
                    coreStorageGetter = method;
                    coreStorageClass = method.getReturnType();
                    break;
                } catch (Throwable ignored) {}
            }

            if (coreStorageClass == null) {
                coreStorageClass = findFirstClassByStrings(
                        "MMKernel.CoreStorage",
                        "CheckData path[%s] blocksize:%s blockcount:%s availcount:%s");
            }

            configStorageClass = findFirstClassByStrings(
                    "MicroMsg.ConfigStorage",
                    "shouldProcessEvent db is close :%s");

            sqliteDbWrapperClass = findSqliteDbWrapperClass();

            logDetail("数据库API: kernel="
                    + (mmKernelClass != null ? mmKernelClass.getName() : "null")
                    + " storageGetter=" + (coreStorageGetter != null ? coreStorageGetter.getName() : "null")
                    + " coreStorage=" + (coreStorageClass != null ? coreStorageClass.getName() : "null")
                    + " config=" + (configStorageClass != null ? configStorageClass.getName() : "null")
                    + " sqlite=" + (sqliteDbWrapperClass != null ? sqliteDbWrapperClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveDatabaseApi 失败: " + e.getMessage(), e);
        }
    }

    private Class<?> findSqliteDbWrapperClass() {
        List<ClassData> sqliteMarkerCandidates =
                dexQueries.findClass(mkClassUsingStrings("MicroMsg.SqliteDB"));
        List<ClassData> sqlErrorCandidates =
                dexQueries.findClass(mkClassUsingStrings("sql is null "));
        List<String> sqliteMarkerNames = new ArrayList<>();
        List<String> sqlErrorNames = new ArrayList<>();
        for (ClassData candidate : sqliteMarkerCandidates) sqliteMarkerNames.add(candidate.getName());
        for (ClassData candidate : sqlErrorCandidates) sqlErrorNames.add(candidate.getName());
        Set<String> jointAnchorNames = WeChatSqliteWrapperLocator.intersectCandidateNames(
                sqliteMarkerNames, sqlErrorNames);

        List<Class<?>> structurallyValid = new ArrayList<>();
        for (ClassData candidate : sqliteMarkerCandidates) {
            if (!jointAnchorNames.contains(candidate.getName())) continue;
            try {
                Class<?> loaded = KavaReflector.loadClass(candidate.getName(), classLoader);
                if (WeChatSqliteWrapperLocator.supportsMessageMutations(loaded)) {
                    structurallyValid.add(loaded);
                }
            } catch (Throwable ignored) {}
        }
        Class<?> selected = WeChatSqliteWrapperLocator.selectUniqueSupportedCandidate(structurallyValid);
        logDetail("SqliteDB 定位: markerCandidates=" + sqliteMarkerNames.size()
                + " errorCandidates=" + sqlErrorNames.size()
                + " jointAnchors=" + jointAnchorNames.size()
                + " structurallyValid=" + structurallyValid.size()
                + " selected=" + (selected != null ? selected.getName() : "null"));
        return selected;
    }

    public void resolveConversationDeleteApi() {
        ensureSnapshotLoaded();
        try {
            if (isConversationDeleteMethod(conversationDeleteMethod)) return;
            conversationDeleteMethod = null;

            List<Method> candidates = new ArrayList<>();
            FindMethod find = new FindMethod();
            MethodMatcher matcher = new MethodMatcher();
            matcher.usingEqStrings("delChatContact username:%s  stack:%s");
            find.matcher(matcher);
            for (MethodData methodData : dexQueries.findMethod(find)) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isConversationDeleteMethod(method) || candidates.contains(method)) continue;
                    KavaReflector.accessible(method);
                    candidates.add(method);
                } catch (Throwable ignored) {}
            }
            if (candidates.size() == 1) {
                conversationDeleteMethod = candidates.get(0);
                logDetail("原生会话删除API: " + methodName(conversationDeleteMethod));
            } else {
                h.Hchat.utils.HLog.e(TAG + " 原生会话删除API定位失败: candidates="
                        + candidates.size() + " key=" + shortKey(runtimeCacheKey));
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveConversationDeleteApi 失败: " + e.getMessage(), e);
        }
    }

    /** Resolves the conversation deletion helper invoked by WeChat's confirmed home-row UI path. */
    public synchronized void resolveConversationDeleteFromHomeApi() {
        ensureSnapshotLoaded();
        if (isConversationDeleteFromHomeMethod(conversationDeleteFromHomeMethod)) return;
        conversationDeleteFromHomeMethod = null;
        try {
            FindMethod find = new FindMethod();
            find.matcher(new MethodMatcher().usingEqStrings(
                    "MicroMsg.ConvDelLogic",
                    "doDeleteConv() called talker:%s reserveConversation:%s hasLeftChatroom:%s"));
            List<Method> candidates = new ArrayList<>();
            for (MethodData data : dexQueries.findMethod(find)) {
                try {
                    Method method = data.getMethodInstance(classLoader);
                    if (isConversationDeleteFromHomeMethod(method) && !candidates.contains(method)) {
                        candidates.add(KavaReflector.accessible(method));
                    }
                } catch (Throwable ignored) {}
            }
            if (candidates.size() == 1) {
                conversationDeleteFromHomeMethod = candidates.get(0);
                logDetail("微信首页会话删除链路: " + methodName(conversationDeleteFromHomeMethod));
                if (resolutionFinished) saveCache();
            } else {
                h.Hchat.utils.HLog.e(TAG + " 微信首页会话删除链路定位失败: candidates=" + candidates.size());
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveConversationDeleteFromHomeApi 失败: " + e.getMessage(), e);
        }
    }

    private boolean isConversationDeleteFromHomeMethod(Method method) {
        if (method == null || !KavaReflector.isStatic(method) || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return method.getDeclaringClass().getName().startsWith("com.tencent.mm.ui.conversation.")
                && params.length == 1 && params[0] == String.class;
    }

    /**
     * Resolve the same native conversation storage actions used by
     * ConversationGroupRuntime so callers share one version-scoped locator.
     */
    public synchronized void resolveNativeConversationActionsApi() {
        ensureSnapshotLoaded();
        long now = System.currentTimeMillis();
        boolean cacheChanged = false;
        if (!isNativePlacedTopMethod(conversationSetPlacedTopMethod)) {
            conversationSetPlacedTopMethod = null;
            if (now >= conversationSetPlacedTopRetryAfterMs) {
                conversationSetPlacedTopMethod = resolveConversationActionWithRetry(
                        "[setPlacedTop] flag=%s result=%s", false, boolean.class,
                        "com.tencent.mm.storage.", now);
                conversationSetPlacedTopRetryAfterMs = conversationSetPlacedTopMethod != null
                        ? 0L : now + NATIVE_CONVERSATION_LOCATOR_RETRY_MS;
                cacheChanged |= conversationSetPlacedTopMethod != null;
            }
        }
        if (!isNativePlacedTopMethod(conversationUnsetPlacedTopMethod)) {
            conversationUnsetPlacedTopMethod = null;
            if (now >= conversationUnsetPlacedTopRetryAfterMs) {
                conversationUnsetPlacedTopMethod = resolveConversationActionWithRetry(
                        "unSetPlacedTop conversation failed", false, boolean.class,
                        "com.tencent.mm.storage.", now);
                conversationUnsetPlacedTopRetryAfterMs = conversationUnsetPlacedTopMethod != null
                        ? 0L : now + NATIVE_CONVERSATION_LOCATOR_RETRY_MS;
                cacheChanged |= conversationUnsetPlacedTopMethod != null;
            }
        }
        if (!isNativeHideFromHomeMethod(conversationHideFromHomeMethod)) {
            conversationHideFromHomeMethod = null;
            if (now >= conversationHideFromHomeRetryAfterMs) {
                conversationHideFromHomeMethod = resolveConversationActionWithRetry(
                        "hidden_conv_parent", true, void.class, null, now);
                conversationHideFromHomeRetryAfterMs = conversationHideFromHomeMethod != null
                        ? 0L : now + NATIVE_CONVERSATION_LOCATOR_RETRY_MS;
                cacheChanged |= conversationHideFromHomeMethod != null;
            }
        }
        if (cacheChanged && resolutionFinished) saveCache();
        logDetail("会话原生操作API: setPlacedTop=" + methodName(conversationSetPlacedTopMethod)
                + " unSetPlacedTop=" + methodName(conversationUnsetPlacedTopMethod)
                + " hide=" + methodName(conversationHideFromHomeMethod));
    }

    /** Resolves the native root-list query already shared with ConversationGroupRuntime. */
    public synchronized void resolveNativeConversationHomeListQueryApi() {
        ensureSnapshotLoaded();
        if (isNativeConversationHomeListQueryMethod(nativeConversationHomeListQueryMethod)) return;
        nativeConversationHomeListQueryMethod = null;
        long now = System.currentTimeMillis();
        if (now < nativeConversationHomeQueryRetryAfterMs) return;
        try {
            List<Method> candidates = new ArrayList<>();
            FindMethod find = new FindMethod();
            find.matcher(new MethodMatcher()
                    .declaredClass("com.tencent.mm.storage.", StringMatchType.StartsWith, false)
                    .usingStrings(Arrays.asList(NATIVE_HOME_QUERY_ANCHOR, NATIVE_HOME_PARENT_ANCHOR)));
            for (MethodData data : dexQueries.findMethod(find)) {
                try {
                    Method candidate = data.getMethodInstance(classLoader);
                    if (isNativeConversationHomeListQueryMethod(candidate) && !candidates.contains(candidate)) {
                        candidates.add(candidate);
                    }
                } catch (Throwable ignored) {}
            }
            if (candidates.size() == 1) {
                nativeConversationHomeListQueryMethod = KavaReflector.accessible(candidates.get(0));
                nativeConversationHomeQueryRetryAfterMs = 0L;
                if (resolutionFinished) saveCache();
            } else {
                nativeConversationHomeQueryRetryAfterMs = now + NATIVE_CONVERSATION_LOCATOR_RETRY_MS;
                logDetail("微信首页会话查询入口定位不唯一: candidates=" + candidates.size());
            }
        } catch (Throwable e) {
            nativeConversationHomeQueryRetryAfterMs = now + NATIVE_CONVERSATION_LOCATOR_RETRY_MS;
            h.Hchat.utils.HLog.e(TAG + " 定位微信首页会话查询入口失败: " + e.getMessage(), e);
        }
    }

    private Method resolveConversationActionWithRetry(String anchor,
                                                      boolean isStatic,
                                                      Class<?> returnType,
                                                      String ownerPrefix,
                                                      long now) {
        try {
            Method method = findUniqueNativeConversationAction(anchor, isStatic, returnType, ownerPrefix);
            if (method == null) {
                logDetail("会话原生操作入口将在冷却后重试: anchor=" + anchor
                        + " retryAfterMs=" + (now + NATIVE_CONVERSATION_LOCATOR_RETRY_MS));
            }
            return method;
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " 定位会话原生操作入口失败: anchor=" + anchor, e);
            return null;
        }
    }

    private Method findUniqueNativeConversationAction(String anchor,
                                                       boolean isStatic,
                                                       Class<?> returnType,
                                                       String ownerPrefix) {
        List<Method> candidates = new ArrayList<>();
        FindMethod find = new FindMethod();
        find.matcher(new MethodMatcher().usingEqStrings(anchor));
        for (MethodData methodData : dexQueries.findMethod(find)) {
            try {
                Method method = methodData.getMethodInstance(classLoader);
                if (method == null || method.getReturnType() != returnType
                        || Modifier.isStatic(method.getModifiers()) != isStatic
                        || Modifier.isAbstract(method.getModifiers())
                        || method.getParameterTypes().length != 1
                        || method.getParameterTypes()[0] != String.class
                        || (ownerPrefix != null && !method.getDeclaringClass().getName().startsWith(ownerPrefix))) {
                    continue;
                }
                if (!candidates.contains(method)) candidates.add(method);
            } catch (Throwable ignored) {}
        }
        if (candidates.size() != 1) {
            logDetail("会话原生操作API定位不唯一: anchor=" + anchor + " candidates=" + candidates.size());
            return null;
        }
        return KavaReflector.accessible(candidates.get(0));
    }

    private boolean isNativePlacedTopMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method) || Modifier.isAbstract(method.getModifiers())
                || method.getReturnType() != boolean.class) return false;
        return method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == String.class
                && method.getDeclaringClass().getName().startsWith("com.tencent.mm.storage.");
    }

    private boolean isNativeHideFromHomeMethod(Method method) {
        if (method == null || !KavaReflector.isStatic(method) || Modifier.isAbstract(method.getModifiers())
                || method.getReturnType() != void.class) return false;
        return method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == String.class;
    }

    private boolean isNativeConversationHomeListQueryMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method) || Modifier.isAbstract(method.getModifiers())
                || !Cursor.class.isAssignableFrom(method.getReturnType())
                || !method.getDeclaringClass().getName().startsWith("com.tencent.mm.storage.")) return false;
        Class<?>[] types = method.getParameterTypes();
        return types.length == 4 && types[0] == int.class
                && List.class.isAssignableFrom(types[1])
                && types[2] == String.class && types[3] == int.class;
    }

    private boolean isConversationDeleteMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method) || method.getReturnType() != void.class) {
            return false;
        }
        Class<?> owner = method.getDeclaringClass();
        Class<?>[] params = method.getParameterTypes();
        return owner != null
                && owner.getName().startsWith("com.tencent.mm.storage.")
                && params.length == 1
                && params[0] == String.class;
    }

    public void resolveMessageClearApi() {
        ensureSnapshotLoaded();
        try {
            if (isMessageClearByTalkerMethod(messageClearByTalkerMethod, messageClearBatchMethod)) {
                return;
            }
            messageClearByTalkerMethod = null;
            messageClearBatchMethod = null;

            List<Method> batchCandidates = new ArrayList<>();
            FindMethod find = new FindMethod();
            MethodMatcher matcher = new MethodMatcher();
            matcher.usingEqStrings(
                    "MicroMsg.MsgInfoStorageLogic",
                    "summerdel deleteMsgByTalker[%s] stack[%s]",
                    "summerdel deleteMsgByTalker is null or empty",
                    "AsyncDeleteMessageStage1");
            find.matcher(matcher);
            for (MethodData methodData : dexQueries.findMethod(find)) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isMessageClearBatchMethod(method) || batchCandidates.contains(method)) continue;
                    KavaReflector.accessible(method);
                    batchCandidates.add(method);
                } catch (Throwable ignored) {}
            }
            Method batch = batchCandidates.size() == 1 ? batchCandidates.get(0) : null;
            if (batch != null) {
                Class<?> callbackType = batch.getParameterTypes()[1];
                List<Method> singleCandidates = new ArrayList<>();
                for (Method method : KavaReflector.declaredMethods(batch.getDeclaringClass())) {
                    if (!isMessageClearByTalkerMethod(method, batch)) continue;
                    if (method.getParameterTypes()[1] != callbackType) continue;
                    KavaReflector.accessible(method);
                    singleCandidates.add(method);
                }
                if (singleCandidates.size() == 1) {
                    messageClearBatchMethod = batch;
                    messageClearByTalkerMethod = singleCandidates.get(0);
                    logDetail("原生消息清理API: single=" + methodName(messageClearByTalkerMethod)
                            + " batch=" + methodName(messageClearBatchMethod));
                    return;
                }
                h.Hchat.utils.HLog.e(TAG + " 原生单会话消息清理API定位失败: candidates="
                        + singleCandidates.size() + " key=" + shortKey(runtimeCacheKey));
            }
            h.Hchat.utils.HLog.e(TAG + " 原生批量消息清理API定位失败: candidates="
                    + batchCandidates.size() + " key=" + shortKey(runtimeCacheKey));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveMessageClearApi 失败: " + e.getMessage(), e);
        }
    }

    /** Locates WeChat's native DeleteContactService request that carries isClearRecord. */
    public synchronized void resolveDeleteContactServiceApi() {
        ensureSnapshotLoaded();
        if (isDeleteContactServiceMethod(deleteContactServiceMethod)) {
            deleteContactServiceCandidateCount = 1;
            return;
        }
        deleteContactServiceMethod = null;
        deleteContactServiceCandidateCount = 0;
        try {
            FindMethod find = new FindMethod();
            find.matcher(new MethodMatcher().usingEqStrings(
                    "MicroMsg.DeleteContactService", "delete contact %s isClearRecord:%s"));
            List<Method> candidates = new ArrayList<>();
            for (MethodData data : dexQueries.findMethod(find)) {
                try {
                    Method method = data.getMethodInstance(classLoader);
                    if (isDeleteContactServiceMethod(method) && !candidates.contains(method)) {
                        candidates.add(KavaReflector.accessible(method));
                    }
                } catch (Throwable ignored) {}
            }
            deleteContactServiceCandidateCount = candidates.size();
            if (candidates.size() == 1) {
                deleteContactServiceMethod = candidates.get(0);
                logDetail("原生好友删除API: " + methodName(deleteContactServiceMethod));
            } else {
                h.Hchat.utils.HLog.e(TAG + " 原生好友删除API定位失败: candidates=" + candidates.size());
            }
            if (resolutionFinished && deleteContactServiceMethod != null) saveCache();
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveDeleteContactServiceApi 失败: " + e.getMessage(), e);
        }
    }

    /** Locates the exact response handler from its result log anchor, independently of submission. */
    public synchronized void resolveDeleteContactServiceResponseApi() {
        ensureSnapshotLoaded();
        resolveDeleteContactServiceApi();
        Class<?> serviceClass = deleteContactServiceMethod != null
                ? deleteContactServiceMethod.getDeclaringClass() : null;
        if (isDeleteContactServiceResponseMethod(deleteContactServiceResponseMethod, serviceClass)) {
            deleteContactServiceResponseCandidateCount = 1;
            return;
        }
        deleteContactServiceResponseMethod = null;
        deleteContactServiceResponseCandidateCount = 0;
        if (serviceClass == null) return;
        try {
            FindMethod find = new FindMethod();
            find.matcher(new MethodMatcher().usingEqStrings(
                    "MicroMsg.DeleteContactService",
                    "handel delete contact username:%s, getEncryptUsername:%s ret:%d, isClearRecord:%s"));
            List<Method> candidates = new ArrayList<>();
            for (MethodData data : dexQueries.findMethod(find)) {
                try {
                    Method method = data.getMethodInstance(classLoader);
                    if (isDeleteContactServiceResponseMethod(method, serviceClass)
                            && !candidates.contains(method)) {
                        candidates.add(KavaReflector.accessible(method));
                    }
                } catch (Throwable ignored) {}
            }
            deleteContactServiceResponseCandidateCount = candidates.size();
            if (candidates.size() == 1) {
                deleteContactServiceResponseMethod = candidates.get(0);
                logDetail("原生好友删除回执API: " + methodName(deleteContactServiceResponseMethod));
                if (resolutionFinished) saveCache();
            } else {
                h.Hchat.utils.HLog.e(TAG + " 原生好友删除回执API定位失败: candidates=" + candidates.size());
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveDeleteContactServiceResponseApi 失败: " + e.getMessage(), e);
        }
    }

    /** Finds the exact service getter called by WeChat's own contact-delete menu handler. */
    public synchronized void resolveDeleteContactServiceGetterApi() {
        ensureSnapshotLoaded();
        resolveDeleteContactServiceApi();
        Method submitMethod = deleteContactServiceMethod;
        if (submitMethod == null) return;
        if (isServiceGetterMethod(deleteContactServiceGetterMethod)
                && isNativeDeleteContactUiMethod(deleteContactServiceUiCallerMethod)) {
            deleteContactServiceGetterCandidateCount = 1;
            return;
        }
        deleteContactServiceGetterMethod = null;
        deleteContactServiceUiCallerMethod = null;
        deleteContactServiceGetterCandidateCount = 0;
        try {
            MethodMatcher submitMatcher = new MethodMatcher()
                    .declaredClass(submitMethod.getDeclaringClass().getName())
                    .name(submitMethod.getName())
                    .paramTypes(submitMethod.getParameterTypes())
                    .returnType(submitMethod.getReturnType());
            MethodMatcher uiMatcher = new MethodMatcher()
                    .usingEqStrings("MicroMsg.DeleteContactService", "delete contact %s")
                    .invokeMethods(new MethodsMatcher().add(submitMatcher));
            LinkedHashSet<Method> uiCallers = new LinkedHashSet<>();
            for (MethodData data : dexQueries.findMethod(new FindMethod().matcher(uiMatcher))) {
                try {
                    Method caller = data.getMethodInstance(classLoader);
                    if (!isNativeDeleteContactUiMethod(caller)) continue;
                    uiCallers.add(caller);
                } catch (Throwable ignored) {}
            }
            if (uiCallers.size() != 1) {
                h.Hchat.utils.HLog.e(TAG + " 删除好友UI调用点定位失败: candidates=" + uiCallers.size());
                return;
            }
            Method uiCaller = uiCallers.iterator().next();
            LinkedHashSet<Method> getters = new LinkedHashSet<>();
            MethodMatcher uiCallerMatcher = new MethodMatcher()
                    .declaredClass(uiCaller.getDeclaringClass().getName())
                    .name(uiCaller.getName())
                    .paramTypes(uiCaller.getParameterTypes())
                    .returnType(uiCaller.getReturnType());
            MethodMatcher getterMatcher = new MethodMatcher()
                    .callerMethods(new MethodsMatcher().add(uiCallerMatcher));
            for (MethodData data : dexQueries.findMethod(new FindMethod().matcher(getterMatcher))) {
                try {
                    Method getter = data.getMethodInstance(classLoader);
                    if (isServiceGetterMethod(getter)) getters.add(KavaReflector.accessible(getter));
                } catch (Throwable ignored) {}
            }
            deleteContactServiceGetterCandidateCount = getters.size();
            if (getters.size() == 1) {
                deleteContactServiceUiCallerMethod = KavaReflector.accessible(uiCaller);
                deleteContactServiceGetterMethod = getters.iterator().next();
                logDetail("好友删除UI服务实例入口: caller=" + methodName(deleteContactServiceUiCallerMethod)
                        + " getter=" + methodName(deleteContactServiceGetterMethod));
                if (resolutionFinished) saveCache();
            } else {
                h.Hchat.utils.HLog.e(TAG + " 删除好友UI服务Getter定位失败: candidates=" + getters.size()
                        + " caller=" + methodName(uiCaller));
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveDeleteContactServiceGetterApi 失败: " + e.getMessage(), e);
        }
    }

    private boolean isNativeDeleteContactUiMethod(Method method) {
        if (method == null || !Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                || !method.getDeclaringClass().getName().startsWith("com.tencent.mm.ui.tools.")) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 5
                && params[0] != null && !params[0].isPrimitive()
                && params[1] == Activity.class
                && "com.tencent.mm.storage.z3".equals(params[2].getName())
                && params[3] == boolean.class
                && params[4] == int.class;
    }

    private boolean isDeleteContactServiceMethod(Method method) {
        return DeleteContactServiceMethodPolicy.isSubmitMethod(method);
    }

    private boolean isDeleteContactServiceResponseMethod(Method method, Class<?> serviceClass) {
        return DeleteContactServiceMethodPolicy.isResponseMethod(method, serviceClass);
    }

    private boolean isMessageClearBatchMethod(Method method) {
        if (method == null || !KavaReflector.isStatic(method) || method.getReturnType() != void.class) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 2 && params.length != 3) return false;
        if (!java.util.List.class.isAssignableFrom(params[0]) || !params[1].isInterface()) return false;
        return params.length == 2 || params[2] == long.class;
    }

    private boolean isMessageClearByTalkerMethod(Method method, Method batchMethod) {
        if (method == null || batchMethod == null || !KavaReflector.isStatic(method)
                || method.getReturnType() != void.class
                || method.getDeclaringClass() != batchMethod.getDeclaringClass()) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        Class<?>[] batchParams = batchMethod.getParameterTypes();
        return (params.length == 2 || params.length == 3)
                && params[0] == String.class
                && params[1] == batchParams[1]
                && (params.length == 2 || params[2] == long.class);
    }

    public void resolveConversationMuteApi() {
        ensureSnapshotLoaded();
        try {
            if (!isServiceGetterMethod(serviceGetterMethod)) {
                resolveServiceManagerApi();
            }
            if (!isContactMuteMethod(contactMuteEnableMethod)) {
                contactMuteEnableMethod = findUniqueMethodUsingString(
                        "setMute contact invalid username");
            }
            if (!isContactMuteMethod(contactMuteDisableMethod)) {
                contactMuteDisableMethod = findUniqueMethodUsingString(
                        "unSetMute contact invalid username");
            }
            if (!isContactMuteStateMethod(contactMuteStateMethod, contactMuteEnableMethod)) {
                contactMuteStateMethod = findContactMuteStateMethod();
            }
            if (!isContactStorageLookupApiReady()) {
                resolveContactStorageLookupApi();
            }
            if (!isChatroomMuteApiReady()) {
                resolveChatroomMuteApi();
            }
            logDetail("原生免打扰API: state=" + methodName(contactMuteStateMethod)
                    + " enable=" + methodName(contactMuteEnableMethod)
                    + " disable=" + methodName(contactMuteDisableMethod)
                    + " contactStorageGetter=" + methodName(contactStorageGetterMethod)
                    + " contactStorageQuery=" + methodName(contactStorageQueryMethod)
                    + " roomGetter=" + methodName(chatroomMuteServiceGetterMethod)
                    + " roomBuild=" + methodName(chatroomMuteBuildMethod)
                    + " roomSubmit=" + methodName(chatroomMuteSubmitMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveConversationMuteApi 失败: " + e.getMessage(), e);
        }
    }

    public boolean isPrivateConversationMuteApiReady() {
        return isServiceGetterMethod(serviceGetterMethod)
                && isContactMuteMethod(contactMuteEnableMethod)
                && isContactMuteMethod(contactMuteDisableMethod)
                && isContactMuteStateMethod(contactMuteStateMethod, contactMuteEnableMethod)
                && isContactStorageLookupApiReady();
    }

    private Method findUniqueMethodUsingString(String anchor) {
        List<Method> candidates = new ArrayList<>();
        for (MethodData data : dexQueries.findMethod(mkMethodUsingStrings(anchor))) {
            try {
                Method method = data.getMethodInstance(classLoader);
                if (candidates.contains(method)) continue;
                if (!isContactMuteMethod(method)) continue;
                KavaReflector.accessible(method);
                candidates.add(method);
            } catch (Throwable ignored) {}
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private boolean isContactMuteMethod(Method method) {
        if (method == null || !KavaReflector.isStatic(method)
                || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 2
                && !params[0].isPrimitive()
                && params[1] == boolean.class;
    }

    private Method findContactMuteStateMethod() {
        Method mute = contactMuteEnableMethod;
        if (!isContactMuteMethod(mute)) return null;
        List<Method> candidates = new ArrayList<>();
        for (MethodData entry : dexQueries.findMethod(mkMethodUsingStrings("room_notify_new_msg"))) {
            if (!"com.tencent.mm.ui.SingleChatInfoUI".equals(entry.getClassName())) continue;
            for (MethodData invokeData : entry.getInvokes()) {
                try {
                    Method method = invokeData.getMethodInstance(classLoader);
                    if (!isContactMuteStateMethod(method, mute) || candidates.contains(method)) continue;
                    KavaReflector.accessible(method);
                    candidates.add(method);
                } catch (Throwable ignored) {}
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private boolean isContactMuteStateMethod(Method method, Method muteMethod) {
        if (method == null || !isContactMuteMethod(muteMethod)
                || !KavaReflector.isStatic(method)
                || method.getReturnType() != boolean.class
                || method.getDeclaringClass() != muteMethod.getDeclaringClass()) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1 && params[0] == muteMethod.getParameterTypes()[0];
    }

    private void resolveContactStorageLookupApi() {
        contactStorageGetterMethod = null;
        contactStorageQueryMethod = null;
        Method mute = contactMuteEnableMethod;
        if (!isContactMuteMethod(mute)) return;

        Class<?> contactType = mute.getParameterTypes()[0];
        List<Method> invokes = new ArrayList<>();
        for (MethodData data : dexQueries.findMethod(
                mkMethodUsingStrings("setMute contact invalid username"))) {
            try {
                Method candidate = data.getMethodInstance(classLoader);
                if (!mute.equals(candidate)) continue;
                for (MethodData invokeData : data.getInvokes()) {
                    Method invoked = invokeData.getMethodInstance(classLoader);
                    if (!invokes.contains(invoked)) invokes.add(invoked);
                }
            } catch (Throwable ignored) {}
        }

        List<Method[]> pairs = new ArrayList<>();
        for (Method query : invokes) {
            if (!isContactStorageQueryMethod(query, contactType)) continue;
            for (Method getter : invokes) {
                if (!isContactStorageGetterMethod(getter, query)) continue;
                pairs.add(new Method[]{getter, query});
            }
        }
        if (pairs.size() == 1) {
            contactStorageGetterMethod = KavaReflector.accessible(pairs.get(0)[0]);
            contactStorageQueryMethod = KavaReflector.accessible(pairs.get(0)[1]);
            return;
        }
        h.Hchat.utils.HLog.e(TAG + " 原生联系人查询API定位失败: pairs="
                + pairs.size() + " invokes=" + invokes.size()
                + " key=" + shortKey(runtimeCacheKey));
    }

    private boolean isContactStorageLookupApiReady() {
        if (!isContactMuteMethod(contactMuteEnableMethod)) return false;
        Class<?> contactType = contactMuteEnableMethod.getParameterTypes()[0];
        return isContactStorageQueryMethod(contactStorageQueryMethod, contactType)
                && isContactStorageGetterMethod(contactStorageGetterMethod, contactStorageQueryMethod);
    }

    private boolean isContactStorageQueryMethod(Method method, Class<?> contactType) {
        if (method == null || contactType == null || KavaReflector.isStatic(method)
                || method.getReturnType() != contactType) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 2
                && params[0] == String.class
                && params[1] == boolean.class;
    }

    private boolean isContactStorageGetterMethod(Method method, Method query) {
        if (method == null || query == null || KavaReflector.isStatic(method)
                || method.getParameterTypes().length != 0) return false;
        Class<?> returnType = method.getReturnType();
        return returnType != void.class
                && !returnType.isPrimitive()
                && query.getDeclaringClass().isAssignableFrom(returnType);
    }

    private void resolveChatroomMuteApi() {
        chatroomMuteServiceGetterMethod = null;
        chatroomMuteBuildMethod = null;
        chatroomMuteSubmitMethod = null;

        List<MethodData> entries = dexQueries.findMethod(mkMethodUsingStrings(
                "ChatroomMuteRefine OpModChatRoomNotify roomId = %s, notifyMsg = %d, defaultNeedPushFlag=%d"));
        if (entries.isEmpty()) {
            entries = dexQueries.findMethod(mkMethodUsingStrings("room_notify_new_msg"));
        }
        for (MethodData entry : entries) {
            List<Method> invokes = new ArrayList<>();
            for (MethodData invokeData : entry.getInvokes()) {
                try {
                    Method invoked = invokeData.getMethodInstance(classLoader);
                    if (!invokes.contains(invoked)) invokes.add(invoked);
                } catch (Throwable ignored) {}
            }
            for (Method build : invokes) {
                if (!isChatroomMuteBuildMethod(build)) continue;
                Method getter = null;
                Method submit = null;
                for (Method invoked : invokes) {
                    if (isChatroomMuteServiceGetterMethod(invoked, build)) getter = invoked;
                    if (isChatroomMuteSubmitMethod(invoked, build)) submit = invoked;
                }
                if (getter == null || submit == null) continue;
                KavaReflector.accessible(getter);
                KavaReflector.accessible(build);
                KavaReflector.accessible(submit);
                chatroomMuteServiceGetterMethod = getter;
                chatroomMuteBuildMethod = build;
                chatroomMuteSubmitMethod = submit;
                return;
            }
        }
        h.Hchat.utils.HLog.e(TAG + " 原生群聊免打扰API定位失败: entries="
                + entries.size() + " key=" + shortKey(runtimeCacheKey));
    }

    private boolean isChatroomMuteApiReady() {
        return isChatroomMuteBuildMethod(chatroomMuteBuildMethod)
                && isChatroomMuteServiceGetterMethod(
                        chatroomMuteServiceGetterMethod, chatroomMuteBuildMethod)
                && isChatroomMuteSubmitMethod(chatroomMuteSubmitMethod, chatroomMuteBuildMethod);
    }

    private boolean isChatroomMuteBuildMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method)
                || method.getReturnType() == void.class
                || method.getReturnType().isPrimitive()
                || !method.getReturnType().getName().startsWith("com.tencent.mm.roomsdk.model.factory.")) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        return (params.length == 2 || params.length == 3)
                && params[0] == String.class
                && params[1] == int.class
                && (params.length == 2 || params[2] == int.class);
    }

    private boolean isChatroomMuteServiceGetterMethod(Method method, Method build) {
        if (method == null || build == null || KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1
                && params[0] == String.class
                && method.getReturnType() == build.getDeclaringClass();
    }

    private boolean isChatroomMuteSubmitMethod(Method method, Method build) {
        return method != null
                && build != null
                && !KavaReflector.isStatic(method)
                && method.getReturnType() == void.class
                && method.getParameterTypes().length == 0
                && method.getDeclaringClass().isAssignableFrom(build.getReturnType());
    }

    public void resolveLocalMessageApi() {
        ensureSnapshotLoaded();
        try {
            boolean nativeInsertObservable = localSystemMessageMethod == null
                    || localSystemMessageStorageInsertMethod != null;
            boolean directInsertAvailable = localMessageInsertMethod != null
                    || localSystemMessageStorageInsertMethod != null;
            if (hasLocalMessageApi() && localMessageCreateTimeMethod != null
                    && nativeInsertObservable && directInsertAvailable) return;
            int candidateCount = resolveLocalMessageApiBySignature();
            logDetail("本地消息API: insert=" + methodName(localMessageInsertMethod)
                    + " system=" + methodName(localSystemMessageMethod)
                    + " systemInsert=" + methodName(localSystemMessageStorageInsertMethod)
                    + " createTime=" + methodName(localMessageCreateTimeMethod)
                    + " msg=" + (localMessageClass != null ? localMessageClass.getName() : "null")
                    + " ctor=" + (localMessageCtor != null ? localMessageCtor.getParameterTypes().length : -1));
            if (!hasLocalMessageApi()) {
                h.Hchat.utils.HLog.e(TAG + " 本地消息API未找到: candidates=" + candidateCount
                        + " key=" + shortKey(runtimeCacheKey));
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveLocalMessageApi 失败: " + e.getMessage(), e);
        }
    }

    private int resolveLocalMessageApiBySignature() {
        int count = 0;
        resolveLocalSystemMessageMethod();
        try {
            if (localSystemMessageMethod == null && localMessageInsertMethod != null
                    && localMessageCreateTimeMethod == null) {
                localMessageCreateTimeMethod = findLocalMessageCreateTimeMethod(localMessageInsertMethod.getDeclaringClass());
            }
            if (localSystemMessageMethod == null && localMessageInsertMethod != null
                    && localMessageCreateTimeMethod != null) {
                return count;
            }
            if (localSystemMessageMethod != null && localSystemMessageStorageInsertMethod != null
                    && localMessageCreateTimeMethod != null) {
                return count;
            }
            FindMethod fm = new FindMethod();
            MethodMatcher mm = new MethodMatcher();
            mm.name("x");
            mm.returnType(long.class);
            mm.paramCount(1);
            fm.matcher(mm);
            List<MethodData> methods = dexQueries.findMethod(fm);
            count = methods != null ? methods.size() : 0;
            if (methods == null) return 0;
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isLocalMessageInsertMethod(method)) continue;
                    setLocalMessageApi(method);
                    break;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return count;
    }

    private void resolveLocalSystemMessageMethod() {
        if (localSystemMessageMethod != null
                && localSystemMessageStorageInsertMethod != null
                && isLocalMessageCreateTimeMethod(localMessageCreateTimeMethod)) return;
        try {
            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings(
                            "will insert sysmsg from:",
                            "content null, cannot to insert sysmsg!",
                            "failed to insert sysmsg"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isLocalSystemMessageMethod(method)) continue;
                    if (localSystemMessageMethod != null && !localSystemMessageMethod.equals(method)) continue;
                    List<Method> storageInsertMethods = new ArrayList<>();
                    List<Method> createTimeMethods = new ArrayList<>();
                    for (MethodData invokeData : methodData.getInvokes()) {
                        try {
                            Method invoke = invokeData.getMethodInstance(classLoader);
                            if (isLocalSystemMessageStorageInsertMethod(invoke)
                                    && !storageInsertMethods.contains(invoke)) {
                                storageInsertMethods.add(invoke);
                            }
                            if (isLocalMessageCreateTimeMethod(invoke) && !createTimeMethods.contains(invoke)) {
                                createTimeMethods.add(invoke);
                            }
                        } catch (Throwable ignored) {}
                    }
                    localSystemMessageMethod = KavaReflector.accessible(method);
                    if (storageInsertMethods.size() == 1) {
                        setLocalSystemMessageStorageInsertApi(storageInsertMethods.get(0));
                    } else {
                        localSystemMessageStorageInsertMethod = null;
                    }
                    if (createTimeMethods.size() == 1) {
                        localMessageCreateTimeMethod = KavaReflector.accessible(createTimeMethods.get(0));
                    } else {
                        localMessageCreateTimeMethod = null;
                    }
                    break;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveLocalSystemMessageMethod 失败: " + e.getMessage(), e);
        }
    }

    private boolean isLocalSystemMessageMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) return false;
        if (KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 3
                && params[0] == String.class
                && params[1] == String.class
                && params[2] == String.class;
    }

    private void setLocalMessageApi(Method method) {
        KavaReflector.accessible(method);
        localMessageInsertMethod = method;
        localMessageClass = method.getParameterTypes()[0];
        localMessageCtor = findLocalMessageConstructor(localMessageClass);
        if (localSystemMessageMethod == null) {
            localMessageCreateTimeMethod = findLocalMessageCreateTimeMethod(method.getDeclaringClass());
        }
    }

    public void resolveGroupMemberDisplayName() {
        ensureSnapshotLoaded();
        try {
            if (isGroupMemberDisplayNameMethod(groupMemberDisplayNameMethod)) return;
            groupMemberDisplayNameMethod = null;
            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings("ChatroomDisplayNameCache"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isGroupMemberDisplayNameMethod(method)) continue;
                    KavaReflector.accessible(method);
                    groupMemberDisplayNameMethod = method;
                    break;
                } catch (Throwable ignored) {}
            }
            logDetail("群成员昵称方法: "
                    + (groupMemberDisplayNameMethod != null
                    ? groupMemberDisplayNameMethod.getDeclaringClass().getName()
                    + "#" + groupMemberDisplayNameMethod.getName()
                    : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveGroupMemberDisplayName 失败: " + e.getMessage(), e);
        }
    }

    private boolean isGroupMemberDisplayNameMethod(Method method) {
        if (method == null || method.getReturnType() != String.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 2 && params[0] == String.class && params[1] == String.class;
    }

    public void resolveInviteChatroomMemberApi() {
        ensureSnapshotLoaded();
        try {
            if (inviteChatroomMemberCtor == null) {
                inviteChatroomMemberCtor = findInviteChatroomMemberCtor(inviteChatroomMemberClass);
            }
            if (inviteChatroomMemberCtor != null) return;
            List<Class<?>> candidates = new ArrayList<>();
            collectSendTextClassCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/invitechatroommember"},
                            {"MicroMsg.NetSceneInviteChatRoomMember"},
                            {"invitechatroommember"}
                    });
            collectSendTextMethodOwnerCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/invitechatroommember"},
                            {"MicroMsg.NetSceneInviteChatRoomMember"}
                    });
            for (Class<?> candidate : candidates) {
                Constructor<?> ctor = findInviteChatroomMemberCtor(candidate);
                if (ctor == null) continue;
                inviteChatroomMemberClass = candidate;
                inviteChatroomMemberCtor = ctor;
                break;
            }
            logDetail("邀请群成员API: "
                    + (inviteChatroomMemberClass != null ? inviteChatroomMemberClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveInviteChatroomMemberApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveAddChatroomMemberApi() {
        ensureSnapshotLoaded();
        try {
            if (addChatroomMemberCtor == null) {
                addChatroomMemberCtor = findAddChatroomMemberCtor(addChatroomMemberClass);
            }
            if (addChatroomMemberCtor != null) return;
            List<Class<?>> candidates = new ArrayList<>();
            collectSendTextClassCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/addchatroommember"},
                            {"MicroMsg.NetSceneAddChatRoomMember"},
                            {"addchatroommember"}
                    });
            collectSendTextMethodOwnerCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/addchatroommember"},
                            {"MicroMsg.NetSceneAddChatRoomMember"}
                    });
            for (Class<?> candidate : candidates) {
                Constructor<?> ctor = findAddChatroomMemberCtor(candidate);
                if (ctor == null) continue;
                addChatroomMemberClass = candidate;
                addChatroomMemberCtor = ctor;
                break;
            }
            logDetail("添加群成员API: "
                    + (addChatroomMemberClass != null ? addChatroomMemberClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveAddChatroomMemberApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveDelChatroomMemberApi() {
        ensureSnapshotLoaded();
        try {
            if (delChatroomMemberCtor == null) {
                delChatroomMemberCtor = findDelChatroomMemberCtor(delChatroomMemberClass);
            }
            if (delChatroomMemberCtor != null) return;
            List<Class<?>> candidates = new ArrayList<>();
            collectSendTextClassCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/delchatroommember"},
                            {"delchatroommember"}
                    });
            collectSendTextMethodOwnerCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/delchatroommember"}
                    });
            for (Class<?> candidate : candidates) {
                Constructor<?> ctor = findDelChatroomMemberCtor(candidate);
                if (ctor == null) continue;
                delChatroomMemberClass = candidate;
                delChatroomMemberCtor = ctor;
                break;
            }
            logDetail("移除群成员API: "
                    + (delChatroomMemberClass != null ? delChatroomMemberClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveDelChatroomMemberApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveRevokeMsgApi() {
        ensureSnapshotLoaded();
        try {
            if (revokeMsgCtor == null) {
                revokeMsgCtor = findRevokeMsgCtor(revokeMsgClass);
            }
            if (revokeMsgCtor != null) return;
            List<Class<?>> candidates = new ArrayList<>();
            collectSendTextClassCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/revokemsg", "MicroMsg.NetSceneRevokeMsg"},
                            {"NetSceneRevokeMsg"}
                    });
            collectSendTextMethodOwnerCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/micromsg-bin/revokemsg", "MicroMsg.NetSceneRevokeMsg"},
                            {"NetSceneRevokeMsg"}
                    });
            for (Class<?> candidate : candidates) {
                Constructor<?> ctor = findRevokeMsgCtor(candidate);
                if (ctor == null) continue;
                revokeMsgClass = candidate;
                revokeMsgCtor = ctor;
                break;
            }
            logDetail("撤回消息API: "
                    + (revokeMsgClass != null ? revokeMsgClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveRevokeMsgApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveUploadDeviceStepApi() {
        ensureSnapshotLoaded();
        try {
            if (uploadDeviceStepCtor == null) {
                uploadDeviceStepCtor = findUploadDeviceStepCtor(uploadDeviceStepClass);
            }
            if (uploadDeviceStepCtor != null) return;
            List<Class<?>> candidates = new ArrayList<>();
            collectSendTextClassCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/mmoc-bin/hardware/uploaddevicestep", "MicroMsg.Sport.NetSceneUploadDeviceStep"},
                            {"NetSceneUploadDeviceStep"}
                    });
            collectSendTextMethodOwnerCandidates(candidates, 20,
                    new String[][]{
                            {"/cgi-bin/mmoc-bin/hardware/uploaddevicestep", "MicroMsg.Sport.NetSceneUploadDeviceStep"},
                            {"NetSceneUploadDeviceStep"}
                    });
            for (Class<?> candidate : candidates) {
                Constructor<?> ctor = findUploadDeviceStepCtor(candidate);
                if (ctor == null) continue;
                uploadDeviceStepClass = candidate;
                uploadDeviceStepCtor = ctor;
                break;
            }
            logDetail("上传步数API: "
                    + (uploadDeviceStepClass != null ? uploadDeviceStepClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveUploadDeviceStepApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveContactLabelNetworkApi() {
        ensureSnapshotLoaded();
        try {
            if (addContactLabelCtorString == null) {
                addContactLabelCtorString = findCtorByExactTypes(addContactLabelClass, String.class);
            }
            if (addContactLabelCtorList == null) {
                addContactLabelCtorList = findCtorByExactTypes(addContactLabelClass, List.class);
            }
            if (modifyContactLabelListCtor == null) {
                modifyContactLabelListCtor = findCtorByExactTypes(modifyContactLabelListClass, java.util.LinkedList.class);
            }
            if (addContactLabelCtorString == null || addContactLabelCtorList == null) {
                List<Class<?>> candidates = new ArrayList<>();
                collectSendTextClassCandidates(candidates, 20,
                        new String[][]{
                                {"/cgi-bin/micromsg-bin/addcontactlabel", "MicroMsg.Label.NetSceneAddContactLabel"},
                                {"NetSceneAddContactLabel"},
                                {"addcontactlabel"}
                        });
                collectSendTextMethodOwnerCandidates(candidates, 20,
                        new String[][]{
                                {"/cgi-bin/micromsg-bin/addcontactlabel", "MicroMsg.Label.NetSceneAddContactLabel"},
                                {"NetSceneAddContactLabel"}
                        });
                for (Class<?> candidate : candidates) {
                    Constructor<?> stringCtor = findCtorByExactTypes(candidate, String.class);
                    Constructor<?> listCtor = findCtorByExactTypes(candidate, List.class);
                    if (stringCtor == null && listCtor == null) continue;
                    addContactLabelClass = candidate;
                    if (stringCtor != null) addContactLabelCtorString = stringCtor;
                    if (listCtor != null) addContactLabelCtorList = listCtor;
                    break;
                }
            }
            if (modifyContactLabelListCtor == null) {
                List<Class<?>> candidates = new ArrayList<>();
                collectSendTextClassCandidates(candidates, 20,
                        new String[][]{
                                {"/cgi-bin/micromsg-bin/modifycontactlabellist", "MicroMsg.Label.NetSceneModifyContactLabelList"},
                                {"NetSceneModifyContactLabelList"},
                                {"modifycontactlabellist"}
                        });
                collectSendTextMethodOwnerCandidates(candidates, 20,
                        new String[][]{
                                {"/cgi-bin/micromsg-bin/modifycontactlabellist", "MicroMsg.Label.NetSceneModifyContactLabelList"},
                                {"NetSceneModifyContactLabelList"}
                        });
                for (Class<?> candidate : candidates) {
                    Constructor<?> ctor = findCtorByExactTypes(candidate, java.util.LinkedList.class);
                    if (ctor == null) continue;
                    modifyContactLabelListClass = candidate;
                    modifyContactLabelListCtor = ctor;
                    break;
                }
            }
            logDetail("联系人标签网络API: add="
                    + (addContactLabelClass != null ? addContactLabelClass.getName() : "null")
                    + " modify="
                    + (modifyContactLabelListClass != null ? modifyContactLabelListClass.getName() : "null"));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveContactLabelNetworkApi 失败: " + e.getMessage(), e);
        }
    }

    public void resolveSnsUploadApi() {
        try {
            if (hasSnsUploadApi() && snsAddVideoMethod != null && snsShareAppMsgMethod != null) {
                snsVideoLocatorResolved = isSnsAddVideoMethod(snsUploadPackHelperClass, snsAddVideoMethod);
                if (resolutionFinished) saveCache();
                return;
            }

            resolveSnsUploadHelperClass();
            resolveSnsUploadManagerClass();

            resolveSnsUploadMethods();
            snsVideoLocatorResolved = isSnsAddVideoMethod(snsUploadPackHelperClass, snsAddVideoMethod);
            if (resolutionFinished) saveCache();

            logDetail("朋友圈发布API: helper="
                    + (snsUploadPackHelperClass != null ? snsUploadPackHelperClass.getName() : "null")
                    + " manager="
                    + (snsUploadManagerClass != null ? snsUploadManagerClass.getName() : "null")
                    + " getter=" + methodName(snsUploadManagerGetterMethod)
                    + " content=" + methodName(snsSetContentMethod)
                    + " addImage=" + methodName(snsAddImageMethod)
                    + " addVideo=" + methodName(snsAddVideoMethod)
                    + " commit=" + methodName(snsCommitMethod)
                    + " shareAppMsg=" + methodName(snsShareAppMsgMethod)
                    + " check=" + methodName(snsUploadCheckMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveSnsUploadApi 失败: " + e.getMessage(), e);
        }
    }

    private void resolveSnsUploadHelperClass() {
        if (isSnsUploadPackHelperClass(snsUploadPackHelperClass)) return;
        List<Class<?>> candidates = new ArrayList<>();
        collectSendTextClassCandidates(candidates, 20,
                new String[][]{
                        {"MicroMsg.UploadPackHelper", "setContentDes"},
                        {"addImageMediaObjByPath", "setUploadList"},
                        {"setSdkId", "setSdkAppName"}
                });
        collectSendTextMethodOwnerCandidates(candidates, 20,
                new String[][]{
                        {"MicroMsg.UploadPackHelper", "setContentDes"},
                        {"addImageMediaObjByPath", "setUploadList"},
                        {"setSdkId", "setSdkAppName"}
                });
        for (Class<?> candidate : candidates) {
            if (!isSnsUploadPackHelperClass(candidate)) continue;
            snsUploadPackHelperClass = candidate;
            return;
        }
    }

    private void resolveSnsUploadManagerClass() {
        if (isSnsUploadManagerClass(snsUploadManagerClass)) return;
        List<Class<?>> candidates = new ArrayList<>();
        collectSendTextClassCandidates(candidates, 20,
                new String[][]{
                        {"MicroMsg.UploadManager", "checkPostInUI"},
                        {"checkTLE snsinfo localId it time limit"},
                        {"getSnsUploadManager"}
                });
        collectSendTextMethodOwnerCandidates(candidates, 20,
                new String[][]{
                        {"MicroMsg.UploadManager", "checkPostInUI"},
                        {"checkTLE snsinfo localId it time limit"},
                        {"getSnsUploadManager"}
                });
        for (Class<?> candidate : candidates) {
            if (!isSnsUploadManagerClass(candidate)) continue;
            snsUploadManagerClass = candidate;
            return;
        }
    }

    private void resolveSnsUploadMethods() {
        if (snsUploadPackHelperClass != null) {
            if (snsSetContentMethod == null) {
                snsSetContentMethod = findSnsHelperChainMethod("setContentDes");
            }
            if (snsSetSdkIdMethod == null) {
                snsSetSdkIdMethod = findSnsHelperChainMethod("setSdkId");
            }
            if (snsSetSdkAppNameMethod == null) {
                snsSetSdkAppNameMethod = findSnsHelperChainMethod("setSdkAppName");
            }
            if (snsAddImageMethod == null) {
                snsAddImageMethod = findSnsAddImageMethod(snsUploadPackHelperClass);
            }
            if (snsAddVideoMethod == null) {
                snsAddVideoMethod = findSnsAddVideoMethod(snsUploadPackHelperClass);
            }
            if (snsCommitMethod == null) {
                snsCommitMethod = findSnsCommitMethod(snsUploadPackHelperClass);
            }
        }
        if (snsUploadManagerClass != null) {
            if (snsUploadManagerGetterMethod == null) {
                snsUploadManagerGetterMethod = findSnsUploadManagerGetter(snsUploadManagerClass);
            }
            if (snsShareAppMsgMethod == null) {
                snsShareAppMsgMethod = findSnsShareAppMsgMethod(snsUploadManagerClass);
            }
            if (snsUploadCheckMethod == null) {
                snsUploadCheckMethod = findSnsUploadCheckMethod(snsUploadManagerClass);
            }
        }
    }

    public void resolveChatPageApi() {
        ensureSnapshotLoaded();
        try {
            if (chatPageStartMethod != null
                    && chatPageFragmentEnterMethod != null
                    && chatPageFragmentExitMethod != null) return;

            if (chatPageStartMethod == null) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("try startChatting, ishow:%b userName:%s needAnim:%b"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isChatPageStartMethod(method)) continue;
                        KavaReflector.accessible(method);
                        chatPageStartMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (chatPageFragmentEnterMethod == null) {
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("onEnterBegin", "Chat_User"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isNoArgVoidMethod(method)) continue;
                        KavaReflector.accessible(method);
                        chatPageFragmentEnterMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            if (chatPageFragmentExitMethod == null && chatPageFragmentEnterMethod != null) {
                Class<?> fragmentClass = chatPageFragmentEnterMethod.getDeclaringClass();
                List<MethodData> methods = dexQueries.findMethod(
                        mkMethodUsingStrings("onExitBegin"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isNoArgVoidMethod(method)
                                || method.getDeclaringClass() != fragmentClass) continue;
                        KavaReflector.accessible(method);
                        chatPageFragmentExitMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }

            logDetail("聊天页API: start="
                    + methodName(chatPageStartMethod)
                    + " fragmentEnter=" + methodName(chatPageFragmentEnterMethod)
                    + " fragmentExit=" + methodName(chatPageFragmentExitMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveChatPageApi 失败: " + e.getMessage(), e);
        }
    }

    public synchronized void resolveNativeSendButtonApi() {
        try {
            if (chatFooterSendClickMethod != null) return;
            // A complete cache can predate a separately resolved native API. Load it,
            // then resolve the send entry if the cached snapshot does not contain one.
            ensureSnapshotLoaded();
            if (chatFooterSendClickMethod != null) return;
            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStringsAndName("onClick",
                            "MicroMsg.ChatFooter",
                            "send msg onClick",
                            "paste clip board to send"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!isChatFooterSendClickMethod(method)) continue;
                    KavaReflector.accessible(method);
                    chatFooterSendClickMethod = method;
                    break;
                } catch (Throwable ignored) {}
            }
            if (chatFooterSendClickMethod == null) {
                methods = dexQueries.findMethod(
                        mkMethodUsingStringsAndName("onClick", "send msg onClick"));
                for (MethodData methodData : methods) {
                    try {
                        Method method = methodData.getMethodInstance(classLoader);
                        if (!isChatFooterSendClickMethod(method)) continue;
                        KavaReflector.accessible(method);
                        chatFooterSendClickMethod = method;
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            if (chatFooterSendClickMethod != null && resolutionFinished) saveCache();
            logDetail("原生发送按钮API: click=" + methodName(chatFooterSendClickMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveNativeSendButtonApi 失败: " + e.getMessage(), e);
        }
    }

    // ============ DexKit 结果缓存 ============
    /** Load one persisted snapshot; each resolver decides readiness from its own validated result. */
    private void ensureSnapshotLoaded() {
        if (!cacheLoadAttempted) loadCache();
    }

    private synchronized boolean loadCache() {
        cacheLoadAttempted = true;
        if (cachePrefs == null || runtimeCacheKey.length() == 0) return false;
        try {
            String savedKey = cachePrefs.getString(CACHE_KEY, "");
            boolean runtimeMatches = DexCacheIdentity.sameRuntime(runtimeCacheKey, savedKey);
            if (!runtimeMatches) {
                resetCacheForRuntimeKey();
                return false;
            }
            if (!cacheStore.canLoadGlobal(
                    runtimeMatches,
                    cachePrefs.getString(CACHE_SNAPSHOT_VERSION_KEY, ""),
                    CACHE_SNAPSHOT_VERSION,
                    cachePrefs.getBoolean(CACHE_COMPLETE, false)
                            && cachePrefs.getBoolean(DexFinderCacheStore.GLOBAL_SCAN_FINISHED_KEY, false),
                    true)) return false;
            cacheDescriptorInvalid = false;

            addMsgClasses = new ArrayList<>();
            addMsgObserverMethod = loadMethodSoft("addMsgObserverMethod");
            addMsgPreProcessMethod = loadMethodSoft("addMsgPreProcessMethod");
            restoreAddMsgApiFields();
            boolean savedAddMsgResolved = cachePrefs.getBoolean(CACHE_ADDMSG_LOCATOR_RESOLVED, false);
            addMsgLocatorResolved = Boolean.TRUE.equals(DexCacheRestorePolicy.restore(
                    runtimeMatches, savedAddMsgResolved,
                    isAddMsgApiValid() ? Boolean.TRUE : null,
                    Boolean.TRUE::equals,
                    () -> {
                        resolveAddMsgClasses();
                        return isAddMsgApiValid() ? Boolean.TRUE : null;
                    }));
            if (!addMsgLocatorResolved) {
                addMsgObserverMethod = null;
                addMsgPreProcessMethod = null;
                addMsgRequestPayloadField = null;
                addMsgResultMessageField = null;
                addMsgClasses.clear();
            }
            receiveLuckyMoneyClass = loadClass("receiveLuckyMoneyClass");
            receiveLuckyMoneyUnionClass = loadClass("receiveLuckyMoneyUnionClass");
            openLuckyMoneyClass = loadClass("openLuckyMoneyClass");
            openLuckyMoneyUnionClass = loadClass("openLuckyMoneyUnionClass");
            String savedUnionLocatorVersion = cachePrefs.getString(
                    RED_PACKET_UNION_LOCATOR_VERSION_KEY, "");
            unionReceiveCtor = findCtorByArgCount(receiveLuckyMoneyUnionClass, 6);
            unionOpenCtor10 = findCtorByArgCount(openLuckyMoneyUnionClass, 10);
            unionOpenCtor9 = findCtorByArgCount(openLuckyMoneyUnionClass, 9);
            receiveUnionLocatorResolved = RED_PACKET_UNION_LOCATOR_VERSION.equals(savedUnionLocatorVersion)
                    && receiveLuckyMoneyUnionClass != null && unionReceiveCtor != null;
            openUnionLocatorResolved = RED_PACKET_UNION_LOCATOR_VERSION.equals(savedUnionLocatorVersion)
                    && openLuckyMoneyUnionClass != null
                    && (unionOpenCtor10 != null || unionOpenCtor9 != null);
            netQueueClass = loadClass("netQueueClass");
            netQueueCandidateClasses = loadClassList("netQueueCandidateClasses");
            networkQueueLocatorResolved = NETWORK_QUEUE_LOCATOR_VERSION.equals(
                    cachePrefs.getString(NETWORK_QUEUE_LOCATOR_VERSION_KEY, ""))
                    && netQueueClass != null;
            packetBaseClasses = loadClassList("packetBaseClasses");
            packetQueueClasses = loadClassList("packetQueueClasses");
            fakePacketClasses = loadClassList("fakePacketClasses");
            wishWxHbClass = loadClass("wishWxHbClass");
            ecsGiftTaskClass = loadClass("ecsGiftTaskClass");
            ecsGiftServiceClass = loadClass("ecsGiftServiceClass");
            ecsGiftMsgClass = loadClass("ecsGiftMsgClass");
            msgDescTextMethods = loadMethodList("msgDescTextMethods");
            if (!MSG_DESC_LOCATOR_VERSION.equals(cachePrefs.getString(MSG_DESC_LOCATOR_VERSION_KEY, ""))) {
                msgDescTextMethods.clear();
            }
            wishLocatorResolved = cachePrefs.getBoolean(LOCATOR_WISH_COMPLETE, false);
            packetCompatLocatorResolved = cachePrefs.getBoolean(LOCATOR_PACKET_COMPLETE, false);
            ecsGiftLocatorResolved = cachePrefs.getBoolean(LOCATOR_ECS_COMPLETE, false)
                    && isEcsGiftApiLocated();
            msgDescLocatorResolved = cachePrefs.getBoolean(LOCATOR_MSG_DESC_COMPLETE, false);
            sendTextMsgClass = loadClassSoft("sendTextMsgClass");
            sendTextMsgCtorLong = findSendTextConstructor(sendTextMsgClass, long.class);
            sendTextMsgCtorObject = findSendTextConstructor(sendTextMsgClass, Object.class);
            boolean savedSendTextResolved = cachePrefs.getBoolean(CACHE_SEND_TEXT_LOCATOR_RESOLVED, false);
            sendTextMsgLocatorResolved = Boolean.TRUE.equals(DexCacheRestorePolicy.restore(
                    runtimeMatches, savedSendTextResolved,
                    isSendTextRequestApiValid() ? Boolean.TRUE : null,
                    Boolean.TRUE::equals,
                    () -> {
                        resolveSendTextMsg();
                        return isSendTextRequestApiValid() ? Boolean.TRUE : null;
                    }));
            if (!sendTextMsgLocatorResolved) {
                sendTextMsgClass = null;
                sendTextMsgCtorLong = null;
                sendTextMsgCtorObject = null;
            }
            serviceGetterMethod = loadMethod("serviceGetterMethod");
            getContactAddMethods = loadMethodList("getContactAddMethods");
            getContactServiceGetters = loadMethodList("getContactServiceGetters");
            sendImageMethod = loadMethod("sendImageMethod");
            sendImageAsyncParamsClass = loadClass("sendImageAsyncParamsClass");
            sendImageCrossParamsClass = loadClass("sendImageCrossParamsClass");
            sendImageAppInfoClass = loadClass("sendImageAppInfoClass");
            sendImageAsyncSubmitMethod = loadMethod("sendImageAsyncSubmitMethod");
            imageCdnTaskClass = loadClass("imageCdnTaskClass");
            imageCdnSubmitMethod = loadMethod("imageCdnSubmitMethod");
            imageCdnServiceGetterMethod = loadMethod("imageCdnServiceGetterMethod");
            marsCdnManagerClass = loadClass("marsCdnManagerClass");
            marsCdnDownloadRequestClass = loadClass("marsCdnDownloadRequestClass");
            marsCdnDownloadCallbackClass = loadClass("marsCdnDownloadCallbackClass");
            marsCdnStartDownloadMethod = loadMethod("marsCdnStartDownloadMethod");
            imageBestPathMethod = loadMethod("imageBestPathMethod");
            imageStorageGetterMethod = loadMethod("imageStorageGetterMethod");
            imageTokenPathMethod = loadMethod("imageTokenPathMethod");
            sendFileMethod = loadMethod("sendFileMethod");
            sendFileAttachDirMethod = loadMethod("sendFileAttachDirMethod");
            sendFileAttachPathMethod = loadMethod("sendFileAttachPathMethod");
            sendXmlAppMsgMethod = loadMethod("sendXmlAppMsgMethod");
            appMsgParseMethod = loadMethod("appMsgParseMethod");
            groupSolitairePluginClass = loadClass("groupSolitairePluginClass");
            groupSolitaireSendMethod = loadMethod("groupSolitaireSendMethod");
            localSystemMessageMethod = loadMethod("localSystemMessageMethod");
            localSystemMessageStorageInsertMethod = loadMethod("localSystemMessageStorageInsertMethod");
            localMessageInsertMethod = loadMethod("localMessageInsertMethod");
            localMessageCreateTimeMethod = loadMethod("localMessageCreateTimeMethod");
            localMessageClass = loadClass("localMessageClass");
            sendVideoMethod = loadMethod("sendVideoMethod");
            sendVideoTaskClass = loadClass("sendVideoTaskClass");
            videoPathMethod = loadMethod("videoPathMethod");
            videoPathOwnerGetterMethod = loadMethod("videoPathOwnerGetterMethod");
            videoInfoClass = loadClass("videoInfoClass");
            videoInfoByFileNameMethod = loadMethod("videoInfoByFileNameMethod");
            transferOperationClass = loadClass("transferOperationClass");
            transferQueryClass = loadClass("transferQueryClass");
            transferQueryResponseMethod = loadMethod("transferQueryResponseMethod");
            verifyUserClass = loadClass("verifyUserClass");
            contactCardXmlMethod = loadMethod("contactCardXmlMethod");
            patDisplayTemplateMethod = loadMethod("patDisplayTemplateMethod");
            patExtensionClass = loadClass("patExtensionClass");
            patCreatePairMethod = loadMethod("patCreatePairMethod");
            patSuffixMethod = loadMethod("patSuffixMethod");
            patCanSendMethod = loadMethod("patCanSendMethod");
            sendPatSceneClass = loadClass("sendPatSceneClass");
            voiceStartRecordMethod = loadMethod("voiceStartRecordMethod");
            voiceFullPathMethod = loadMethod("voiceFullPathMethod");
            voiceMessagePathMethod = loadMethod("voiceMessagePathMethod");
            if (!isVoiceMessagePathMethod(voiceMessagePathMethod)) voiceMessagePathMethod = null;
            voiceMessagePathLookupComplete = voiceMessagePathMethod != null
                    || (cachePrefs.getBoolean("voiceMessagePathLookupComplete", false)
                    && cachePrefs.getString("voiceMessagePathMethod", "").isEmpty());
            voiceFinishRecordMethod = loadMethod("voiceFinishRecordMethod");
            if (!isVoiceFinishRecordMethod(voiceFinishRecordMethod)) voiceFinishRecordMethod = null;
            voiceInfoQueryMethod = loadMethod("voiceInfoQueryMethod");
            voiceUploadClass = loadClass("voiceUploadClass");
            voiceUploadCdnCtor = findCtorByExactTypes(voiceUploadClass, String.class, boolean.class);
            voicePlaybackStartMethod = loadMethod("voicePlaybackStartMethod");
            voicePlaybackPauseMethod = loadMethod("voicePlaybackPauseMethod");
            voicePlaybackResumeMethod = loadMethod("voicePlaybackResumeMethod");
            voicePlaybackStopMethod = loadMethod("voicePlaybackStopMethod");
            emojiSendMethod = loadMethod("emojiSendMethod");
            emojiManagerSendMethod = loadMethod("emojiManagerSendMethod");
            emojiGetByMd5Method = loadMethod("emojiGetByMd5Method");
            emojiCreateInfoMethod = loadMethod("emojiCreateInfoMethod");
            emojiUpdateInfoMethod = loadMethod("emojiUpdateInfoMethod");
            emojiAccPathMethod = loadMethod("emojiAccPathMethod");
            emojiCheckGifMethod = loadMethod("emojiCheckGifMethod");
            emojiFilePathMethod = loadMethod("emojiFilePathMethod");
            emojiDecodeDataMethod = loadMethod("emojiDecodeDataMethod");
            emojiDecodeManagerGetterMethod = loadMethod("emojiDecodeManagerGetterMethod");
            favoriteItemClass = loadClass("favoriteItemClass");
            favoriteItemConvertFromCursorMethod = loadMethod("favoriteItemConvertFromCursorMethod");
            favoriteServiceClass = loadClass("favoriteServiceClass");
            favoriteServiceResolverMethod = loadMethod("favoriteServiceResolverMethod");
            favoriteStorageGetterMethod = loadMethod("favoriteStorageGetterMethod");
            favoriteListMethod = loadMethod("favoriteListMethod");
            favoriteListNextMethod = loadMethod("favoriteListNextMethod");
            favoriteListCursorMethod = loadMethod("favoriteListCursorMethod");
            favoriteGetMethod = loadMethod("favoriteGetMethod");
            favoriteSendMethod = loadMethod("favoriteSendMethod");
            mmKernelClass = loadClass("mmKernelClass");
            coreStorageClass = loadClass("coreStorageClass");
            configStorageClass = loadClass("configStorageClass");
            sqliteDbWrapperClass = loadClass("sqliteDbWrapperClass");
            if (!SQLITE_WRAPPER_LOCATOR_VERSION.equals(
                    cachePrefs.getString(SQLITE_WRAPPER_LOCATOR_VERSION_KEY, ""))
                    || !WeChatSqliteWrapperLocator.supportsMessageMutations(sqliteDbWrapperClass)) {
                sqliteDbWrapperClass = null;
            }
            conversationDeleteMethod = loadMethod("conversationDeleteMethod");
            conversationDeleteFromHomeMethod = loadMethod("conversationDeleteFromHomeMethod");
            nativeConversationHomeListQueryMethod = loadMethod("nativeConversationHomeListQueryMethod");
            conversationSetPlacedTopMethod = loadMethod("conversationSetPlacedTopMethod");
            conversationUnsetPlacedTopMethod = loadMethod("conversationUnsetPlacedTopMethod");
            conversationHideFromHomeMethod = loadMethod("conversationHideFromHomeMethod");
            messageClearByTalkerMethod = loadMethod("messageClearByTalkerMethod");
            messageClearBatchMethod = loadMethod("messageClearBatchMethod");
            deleteContactServiceMethod = loadMethod("deleteContactServiceMethod");
            deleteContactServiceResponseMethod = loadMethod("deleteContactServiceResponseMethod");
            deleteContactServiceGetterMethod = loadMethod("deleteContactServiceGetterMethod");
            deleteContactServiceUiCallerMethod = loadMethod("deleteContactServiceUiCallerMethod");
            contactMuteStateMethod = loadMethod("contactMuteStateMethod");
            contactMuteEnableMethod = loadMethod("contactMuteEnableMethod");
            contactMuteDisableMethod = loadMethod("contactMuteDisableMethod");
            contactStorageGetterMethod = loadMethod("contactStorageGetterMethod");
            contactStorageQueryMethod = loadMethod("contactStorageQueryMethod");
            chatroomMuteServiceGetterMethod = loadMethod("chatroomMuteServiceGetterMethod");
            chatroomMuteBuildMethod = loadMethod("chatroomMuteBuildMethod");
            chatroomMuteSubmitMethod = loadMethod("chatroomMuteSubmitMethod");

            receiveCtor = findFirstCtorByArgCounts(receiveLuckyMoneyClass, 7, 10, 8);
            unionReceiveCtor = findCtorByArgCount(receiveLuckyMoneyUnionClass, 6);
            openCtor10 = findCtorByArgCount(openLuckyMoneyClass, 10);
            openCtor9 = findCtorByArgCount(openLuckyMoneyClass, 9);
            openCtor8 = findCtorByArgCount(openLuckyMoneyClass, 8);
            unionOpenCtor10 = findCtorByArgCount(openLuckyMoneyUnionClass, 10);
            unionOpenCtor9 = findCtorByArgCount(openLuckyMoneyUnionClass, 9);
            wishWxHbCtor = findCtorByArgCount(wishWxHbClass, 4);
            if (localMessageClass == null && localMessageInsertMethod != null
                    && localMessageInsertMethod.getParameterTypes().length == 1) {
                localMessageClass = localMessageInsertMethod.getParameterTypes()[0];
            }
            localMessageCtor = findLocalMessageConstructor(localMessageClass);
            if (localMessageCreateTimeMethod == null && localMessageInsertMethod != null) {
                localMessageCreateTimeMethod = findLocalMessageCreateTimeMethod(localMessageInsertMethod.getDeclaringClass());
            }
            voiceUploadCtor = findCtorByExactTypes(voiceUploadClass, String.class, int.class);
            voiceUploadCdnCtor = findCtorByExactTypes(voiceUploadClass, String.class, boolean.class);
            sendPatSceneCtor = findSendPatSceneCtor(sendPatSceneClass);
            coreStorageGetter = loadMethod("coreStorageGetter");
            groupMemberDisplayNameMethod = loadMethod("groupMemberDisplayNameMethod");
            addChatroomMemberClass = loadClass("addChatroomMemberClass");
            addChatroomMemberCtor = findAddChatroomMemberCtor(addChatroomMemberClass);
            inviteChatroomMemberClass = loadClass("inviteChatroomMemberClass");
            inviteChatroomMemberCtor = findInviteChatroomMemberCtor(inviteChatroomMemberClass);
            delChatroomMemberClass = loadClass("delChatroomMemberClass");
            delChatroomMemberCtor = findDelChatroomMemberCtor(delChatroomMemberClass);
            revokeMsgClass = loadClass("revokeMsgClass");
            revokeMsgCtor = findRevokeMsgCtor(revokeMsgClass);
            uploadDeviceStepClass = loadClass("uploadDeviceStepClass");
            uploadDeviceStepCtor = findUploadDeviceStepCtor(uploadDeviceStepClass);
            addContactLabelClass = loadClass("addContactLabelClass");
            addContactLabelCtorString = findCtorByExactTypes(addContactLabelClass, String.class);
            addContactLabelCtorList = findCtorByExactTypes(addContactLabelClass, List.class);
            modifyContactLabelListClass = loadClass("modifyContactLabelListClass");
            modifyContactLabelListCtor = findCtorByExactTypes(modifyContactLabelListClass, java.util.LinkedList.class);
            snsUploadPackHelperClass = loadClassSoft("snsUploadPackHelperClass");
            snsUploadManagerClass = loadClass("snsUploadManagerClass");
            snsUploadManagerGetterMethod = loadMethod("snsUploadManagerGetterMethod");
            snsSetContentMethod = loadMethod("snsSetContentMethod");
            snsSetSdkIdMethod = loadMethod("snsSetSdkIdMethod");
            snsSetSdkAppNameMethod = loadMethod("snsSetSdkAppNameMethod");
            snsAddImageMethod = loadMethod("snsAddImageMethod");
            snsAddVideoMethod = loadMethodSoft("snsAddVideoMethod");
            snsCommitMethod = loadMethod("snsCommitMethod");
            snsShareAppMsgMethod = loadMethod("snsShareAppMsgMethod");
            snsUploadCheckMethod = loadMethod("snsUploadCheckMethod");
            chatPageStartMethod = loadMethod("chatPageStartMethod");
            chatPageFragmentEnterMethod = loadMethod("chatPageFragmentEnterMethod");
            chatPageFragmentExitMethod = loadMethod("chatPageFragmentExitMethod");
            chatFooterSendClickMethod = loadMethod("chatFooterSendClickMethod");
            boolean savedSnsVideoResolved = cachePrefs.getBoolean(CACHE_SNS_VIDEO_LOCATOR_RESOLVED, false);
            snsAddVideoMethod = DexCacheRestorePolicy.restore(
                    runtimeMatches, savedSnsVideoResolved,
                    isSnsAddVideoMethod(snsUploadPackHelperClass, snsAddVideoMethod)
                            ? snsAddVideoMethod : null,
                    method -> isSnsAddVideoMethod(snsUploadPackHelperClass, method),
                    () -> {
                        resolveSnsUploadHelperClass();
                        snsAddVideoMethod = isSnsUploadPackHelperClass(snsUploadPackHelperClass)
                                ? findSnsAddVideoMethod(snsUploadPackHelperClass) : null;
                        return isSnsAddVideoMethod(snsUploadPackHelperClass, snsAddVideoMethod)
                                ? snsAddVideoMethod : null;
                    });
            snsVideoLocatorResolved = snsAddVideoMethod != null;
            if (!snsVideoLocatorResolved) snsAddVideoMethod = null;
            if (cacheDescriptorInvalid) {
                logDetail("缓存描述符有损坏项；保留其他有效项并等待对应 locator 定点重试");
            }
            return true;
        } catch (Throwable e) {
            logDetail("读取缓存失败，重新解析: " + e.getMessage());
            resetLoadedCacheDescriptors();
            return false;
        }
    }

    private void resetLoadedCacheDescriptors() {
        boolean resetFailed = false;
        for (Field field : DexFinder.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;
            String name = field.getName();
            if ("dexKit".equals(name) || "classLoader".equals(name)
                    || "cachePrefs".equals(name) || "contactCachePrefs".equals(name)
                    || "cacheStore".equals(name) || "cachePreferenceView".equals(name)
                    || "contactCachePreferenceView".equals(name) || "runtimeCacheKey".equals(name)
                    || "cacheLoadAttempted".equals(name)) continue;
            try {
                field.setAccessible(true);
                if (List.class.isAssignableFrom(field.getType())) {
                    field.set(this, new ArrayList<>());
                } else if (field.getType() == boolean.class) {
                    field.setBoolean(this, false);
                } else if (field.getType() == byte.class) {
                    field.setByte(this, (byte) 0);
                } else if (field.getType() == short.class) {
                    field.setShort(this, (short) 0);
                } else if (field.getType() == int.class) {
                    field.setInt(this, 0);
                } else if (field.getType() == long.class) {
                    field.setLong(this, 0L);
                } else if (field.getType() == float.class) {
                    field.setFloat(this, 0f);
                } else if (field.getType() == double.class) {
                    field.setDouble(this, 0d);
                } else if (field.getType() == char.class) {
                    field.setChar(this, '\0');
                } else {
                    field.set(this, null);
                }
            } catch (Throwable ignored) {
                resetFailed = true;
            }
        }
        cacheDescriptorInvalid = resetFailed;
        resolutionFinished = false;
    }

    private static final class SharedPreferencesView implements DexFinderCacheStore.Preferences {
        private final SharedPreferences preferences;

        SharedPreferencesView(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public String getString(String key, String fallback) {
            return preferences.getString(key, fallback);
        }

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            return preferences.getBoolean(key, fallback);
        }

        @Override
        public DexFinderCacheStore.Editor edit() {
            return new SharedPreferencesEditorView(preferences.edit());
        }
    }

    private static final class SharedPreferencesEditorView implements DexFinderCacheStore.Editor {
        private final SharedPreferences.Editor editor;

        SharedPreferencesEditorView(SharedPreferences.Editor editor) {
            this.editor = editor;
        }

        @Override
        public DexFinderCacheStore.Editor clear() {
            editor.clear();
            return this;
        }

        @Override
        public DexFinderCacheStore.Editor putString(String key, String value) {
            editor.putString(key, value);
            return this;
        }

        @Override
        public DexFinderCacheStore.Editor putBoolean(String key, boolean value) {
            editor.putBoolean(key, value);
            return this;
        }

        @Override
        public void apply() {
            editor.apply();
        }
    }

    private void resetCacheForRuntimeKey() {
        try {
            cachePrefs.edit()
                    .clear()
                    .putString(CACHE_KEY, runtimeCacheKey)
                    .commit();
        } catch (Throwable ignored) {
        }
    }

    private boolean isSendImageMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length < 8) return false;
        return params[0] == Context.class
                && params[1] == String.class
                && params[2] == String.class
                && (params[3] == int.class || params[3] == Integer.class)
                && params[4] == String.class
                && params[5] == String.class;
    }

    private boolean isImageBestPathMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method) || method.getReturnType() != String.class) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1
                && params[0].getName().startsWith("com.tencent.mm.storage.");
    }

    private boolean isImageStorageGetter(Method method, Class<?> storageClass) {
        return method != null
                && storageClass != null
                && KavaReflector.isStatic(method)
                && method.getParameterTypes().length == 0
                && method.getReturnType() == storageClass;
    }

    private boolean isImageTokenPathMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method) || method.getReturnType() != String.class) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        return params.length == 2
                && params[0] == String.class
                && (params[1] == boolean.class || params[1] == Boolean.class);
    }

    private boolean isVoiceStartRecordMethod(Method method) {
        if (method == null || method.getReturnType() != String.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return KavaReflector.isStatic(method)
                && params.length == 2
                && params[0] == String.class
                && params[1] == String.class;
    }

    private boolean isVoiceFullPathMethod(Method method) {
        if (method == null || method.getReturnType() != String.class) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length == 2) {
            return KavaReflector.isStatic(method)
                    && params[0] == String.class
                    && (params[1] == boolean.class || params[1] == Boolean.class);
        }
        return params.length == 3
                && !params[0].isPrimitive()
                && params[1] == String.class
                && (params[2] == boolean.class || params[2] == Boolean.class);
    }

    private boolean isVoiceFinishRecordMethod(Method method) {
        if (method == null || method.getReturnType() != boolean.class) return false;
        if (!KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length < 3 || params.length > 5) return false;
        if (params.length >= 4 && !params[3].getName().startsWith("com.tencent.mm.storage.")) return false;
        if (params.length == 5 && params[4] != String.class) return false;
        return params[0] == String.class
                && (params[1] == int.class || params[1] == Integer.class)
                && (params[2] == int.class || params[2] == Integer.class);
    }

    private boolean isVoiceMessagePathMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method)
                || java.lang.reflect.Modifier.isAbstract(method.getModifiers())
                || method.getReturnType() != String.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 3
                && params[0].getName().startsWith("com.tencent.mm.storage.")
                && params[1] == String.class
                && params[2] == boolean.class;
    }

    private boolean isSendImageAppInfoMethod(Method method) {
        if (!isSendImageMethod(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 8
                && params[5] == String.class
                && params[6] == String.class;
    }

    private Constructor<?> findSendImageAsyncParamsCtor(Class<?> clazz) {
        if (clazz == null) return null;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(clazz)) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 5) continue;
            if (params[0] == String.class
                    && (params[1] == int.class || params[1] == Integer.class)
                    && params[2] == String.class
                    && params[3] == String.class
                    && params[4] != null
                    && !params[4].isPrimitive()) {
                return KavaReflector.accessible(ctor);
            }
        }
        return null;
    }

    private boolean isSendImageAppInfoClass(Class<?> clazz) {
        if (clazz == null || KavaReflector.findConstructor(clazz) == null) return false;
        if (hasDirectImageAppInfoFields(clazz)) return true;
        boolean hasOffsetField = false;
        for (Field field : KavaReflector.declaredFields(clazz)) {
            if (!KavaReflector.isStatic(field)
                    && (field.getType() == int.class || field.getType() == Integer.class)) {
                hasOffsetField = true;
                break;
            }
        }
        if (!hasOffsetField) return false;
        return findIndexedSetter(clazz) != null;
    }

    private boolean hasDirectImageAppInfoFields(Class<?> clazz) {
        int strings = 0;
        int ints = 0;
        int longs = 0;
        int objects = 0;
        for (Field field : KavaReflector.declaredFields(clazz)) {
            if (KavaReflector.isStatic(field)) continue;
            Class<?> type = field.getType();
            if (type == String.class) strings++;
            else if (type == int.class || type == Integer.class) ints++;
            else if (type == long.class || type == Long.class) longs++;
            else if (!type.isPrimitive()) objects++;
        }
        return strings == 5 && ints == 1 && longs == 1 && objects == 0;
    }

    private Method findIndexedSetter(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : KavaReflector.declaredMethods(current)) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2
                        && (params[0] == int.class || params[0] == Integer.class)
                        && params[1] == Object.class) {
                    if ("set".equals(method.getName())) {
                        return KavaReflector.accessible(method);
                    }
                }
            }
            current = current.getSuperclass();
        }
        current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : KavaReflector.declaredMethods(current)) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2
                        && (params[0] == int.class || params[0] == Integer.class)
                        && params[1] == Object.class) {
                    return KavaReflector.accessible(method);
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private boolean isSendImageAsyncSubmitMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method)) return false;
        if (KavaReflector.isAbstract(method.getModifiers())) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1
                && sendImageAsyncParamsClass != null
                && params[0] == sendImageAsyncParamsClass;
    }

    private boolean isKotlinFlowReturn(Method method) {
        if (method == null || method.getReturnType() == null) return false;
        String name = method.getReturnType().getName();
        return name != null && name.startsWith("kotlinx.coroutines.flow.");
    }

    private boolean isImageCdnTaskClass(Class<?> clazz) {
        if (clazz == null) return false;
        return KavaReflector.findFieldRecursive(clazz, "field_mediaId") != null
                && KavaReflector.findFieldRecursive(clazz, "field_fileId") != null
                && KavaReflector.findFieldRecursive(clazz, "field_aesKey") != null
                && KavaReflector.findFieldRecursive(clazz, "field_fullpath") != null
                && KavaReflector.findFieldRecursive(clazz, "field_fileType") != null;
    }

    private boolean isImageCdnSubmitMethod(Method method) {
        if (method == null || imageCdnTaskClass == null) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1 && params.length != 2) return false;
        if (!params[0].isAssignableFrom(imageCdnTaskClass)
                && !imageCdnTaskClass.isAssignableFrom(params[0])) {
            return false;
        }
        if (params.length == 2 && params[1] != int.class && params[1] != Integer.class) return false;
        Class<?> ret = method.getReturnType();
        return ret == boolean.class || ret == Boolean.class || ret == int.class || ret == Integer.class
                || ret == imageCdnTaskClass || ret == void.class;
    }

    private boolean isPreferredImageCdnSubmitMethod(Method method) {
        if (!isImageCdnSubmitMethod(method) || KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 2 || (params[1] != int.class && params[1] != Integer.class)) return false;
        Class<?> ret = method.getReturnType();
        return ret == boolean.class || ret == Boolean.class;
    }

    private boolean isImageCdnServiceGetterMethod(Method method) {
        return method != null
                && imageCdnSubmitMethod != null
                && KavaReflector.isStatic(method)
                && method.getParameterTypes().length == 0
                && method.getReturnType() == imageCdnSubmitMethod.getDeclaringClass();
    }

    public boolean isMarsCdnReady() {
        return marsCdnManagerClass != null
                && marsCdnDownloadRequestClass != null
                && marsCdnDownloadCallbackClass != null
                && marsCdnStartDownloadMethod != null;
    }

    private boolean isContactCardXmlMethod(Method method) {
        if (method == null || !KavaReflector.isStatic(method)) return false;
        if (method.getReturnType() != String.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 2
                && params[0] == String.class
                && !params[1].isPrimitive();
    }

    private boolean isServiceGetterMethod(Method method) {
        if (method == null || !KavaReflector.isStatic(method)) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1
                && params[0] == Class.class
                && method.getReturnType() != void.class;
    }

    private Method findPatCreatePairMethod(Class<?> owner) {
        if (owner == null) return null;
        for (Method method : KavaReflector.declaredMethods(owner)) {
            if (isPatCreatePairMethod(method)) {
                return KavaReflector.accessible(method);
            }
        }
        return null;
    }

    private boolean isPatCreatePairMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method)) return false;
        if (method.getReturnType() != Pair.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 6
                && params[0] == String.class
                && params[1] == String.class
                && params[2] == String.class
                && params[3] == String.class
                && (params[4] == int.class || params[4] == Integer.class)
                && (params[5] == long.class || params[5] == Long.class);
    }

    private Method findPatSuffixMethod(Class<?> owner) {
        if (owner == null) return null;
        for (Method method : KavaReflector.declaredMethods(owner)) {
            if (isPatSuffixMethod(method)) {
                return KavaReflector.accessible(method);
            }
        }
        return null;
    }

    private boolean isPatSuffixMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method)) return false;
        if (method.getReturnType() != String.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 2
                && params[0] == String.class
                && params[1] == String.class;
    }

    private Method findPatCanSendMethod(Class<?> owner) {
        if (owner == null) return null;
        for (Method method : KavaReflector.declaredMethods(owner)) {
            if (isPatCanSendMethod(method)) {
                return KavaReflector.accessible(method);
            }
        }
        return null;
    }

    private boolean isPatCanSendMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method)) return false;
        if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 3
                && (params[0] == int.class || params[0] == Integer.class)
                && params[1] == String.class
                && params[2] == String.class;
    }

    private boolean isSendPatSceneClass(Class<?> clazz) {
        return findSendPatSceneCtor(clazz) != null;
    }

    private Constructor<?> findSendPatSceneCtor(Class<?> clazz) {
        if (clazz == null) return null;
        Constructor<?> ctor = findCtorByExactTypes(clazz, Pair.class, String.class, String.class, int.class);
        if (ctor != null) return ctor;
        return findCtorByExactTypes(clazz, Pair.class, String.class, String.class, Integer.class);
    }

    private Constructor<?> findInviteChatroomMemberCtor(Class<?> clazz) {
        if (clazz == null) return null;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(clazz)) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 4) continue;
            if (params[0] != String.class) continue;
            if (!List.class.isAssignableFrom(params[1])) continue;
            if (params[2] != int.class && params[2] != Integer.class) continue;
            return KavaReflector.accessible(ctor);
        }
        return null;
    }

    private Constructor<?> findAddChatroomMemberCtor(Class<?> clazz) {
        if (clazz == null) return null;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(clazz)) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 4) continue;
            if (params[0] != String.class) continue;
            if (!List.class.isAssignableFrom(params[1])) continue;
            if (params[2] != String.class) continue;
            return KavaReflector.accessible(ctor);
        }
        return null;
    }

    private Constructor<?> findDelChatroomMemberCtor(Class<?> clazz) {
        if (clazz == null) return null;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(clazz)) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 3) continue;
            if (params[0] != String.class) continue;
            if (!List.class.isAssignableFrom(params[1])) continue;
            if (params[2] != int.class && params[2] != Integer.class) continue;
            return KavaReflector.accessible(ctor);
        }
        return null;
    }

    private boolean isSendFileAppMsgMethod(Method method) {
        if (method == null || method.getReturnType() != int.class) return false;
        if (!KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 6
                && "com.tencent.mm.opensdk.modelmsg.WXMediaMessage".equals(params[0].getName())
                && params[1] == String.class
                && params[2] == String.class
                && params[3] == String.class
                && (params[4] == int.class || params[4] == Integer.class)
                && params[5] == String.class;
    }

    private boolean isSendXmlAppMsgMethod(Method method) {
        if (method == null || method.getReturnType() == void.class) return false;
        if (!"android.util.Pair".equals(method.getReturnType().getName())) return false;
        if (!KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 10 && params.length != 12) return false;
        if (params[0].isPrimitive()) return false;
        return params[1] == String.class
                && params[2] == String.class
                && params[3] == String.class
                && params[4] == String.class
                && params[5] == byte[].class
                && params[6] == String.class
                && params[7] == String.class
                && params[8] == String.class
                && ((params.length == 10 && (params[9] == long.class || params[9] == Long.class))
                || (params.length == 12
                && !params[9].isPrimitive()
                && (params[10] == boolean.class || params[10] == Boolean.class)
                && params[11] == String.class));
    }

    private boolean isAppMsgParseMethod(Method method, Class<?> appMsgClass) {
        if (method == null || appMsgClass == null) return false;
        if (!KavaReflector.isStatic(method)) return false;
        if (method.getReturnType() != appMsgClass) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1 && params[0] == String.class;
    }

    private boolean isStaticNoArgStringMethod(Method method) {
        return method != null
                && KavaReflector.isStatic(method)
                && method.getReturnType() == String.class
                && method.getParameterTypes().length == 0;
    }

    private boolean isSendFileAttachPathMethod(Method method) {
        if (method == null || method.getReturnType() != String.class) return false;
        if (!KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 3
                && params[0] == String.class
                && params[1] == String.class
                && params[2] == String.class;
    }

    private Method findSendFileAppMsgMethod(Class<?> clazz) {
        if (clazz == null) return null;
        try {
            for (Method method : KavaReflector.declaredMethods(clazz)) {
                if (isSendFileAppMsgMethod(method)) return method;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean isSendVideoMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length < 13) return false;
        return params[0] == Context.class
                && params[1] == String.class
                && params[2] == String.class
                && params[3] == String.class
                && (params[4] == int.class || params[4] == Integer.class)
                && (params[5] == int.class || params[5] == Integer.class)
                && (params[7] == boolean.class || params[7] == Boolean.class)
                && (params[8] == boolean.class || params[8] == Boolean.class)
                && params[9] == String.class
                && params[10] == String.class;
    }

    private boolean isEmojiSendMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length < 4) return false;
        return params[0] == String.class
                && isEmojiInfoClass(params[1]);
    }

    private boolean isEmojiManagerSendMethod(Method method) {
        if (method == null || method.getReturnType() != boolean.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 5
                && Context.class.isAssignableFrom(params[0])
                && params[1] == String.class
                && params[2] == String.class
                && "com.tencent.mm.plugin.msg.MsgIdTalker".equals(params[3].getName())
                && (params[4] == int.class || params[4] == Integer.class);
    }

    private boolean isEmojiGetByMd5Method(Method method) {
        if (method == null || !isEmojiInfoClass(method.getReturnType())) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1 && params[0] == String.class;
    }

    private boolean isEmojiCreateInfoMethod(Method method) {
        if (method == null || !isEmojiInfoClass(method.getReturnType())) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 4
                && params[0] == String.class
                && (params[1] == int.class || params[1] == Integer.class)
                && (params[2] == int.class || params[2] == Integer.class)
                && (params[3] == int.class || params[3] == Integer.class);
    }

    private boolean isEmojiUpdateInfoMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1 && isEmojiInfoClass(params[0]);
    }

    private boolean isNoArgStringMethod(Method method) {
        return method != null
                && method.getReturnType() == String.class
                && method.getParameterTypes().length == 0;
    }

    private boolean isStringBooleanMethod(Method method) {
        if (method == null || method.getReturnType() != boolean.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1 && params[0] == String.class;
    }

    private boolean isEmojiFilePathMethod(Method method) {
        if (method == null || method.getReturnType() != String.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return KavaReflector.isStatic(method)
                && params.length == 3
                && params[0] == String.class
                && params[1] == String.class
                && params[2] == String.class;
    }

    private boolean isEmojiDecodeDataMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method) || method.getReturnType() != byte[].class) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        return params.length == 1
                && "com.tencent.mm.api.IEmojiInfo".equals(params[0].getName());
    }

    private Method findEmojiDecodeManagerGetter(Class<?> managerClass) {
        if (managerClass == null) return null;
        for (Method method : KavaReflector.declaredMethods(managerClass)) {
            if (KavaReflector.isStatic(method)
                    && method.getParameterTypes().length == 0
                    && method.getReturnType() == managerClass) {
                return method;
            }
        }
        return null;
    }

    private boolean isEmojiInfoClass(Class<?> clazz) {
        return clazz != null && "com.tencent.mm.storage.emotion.EmojiInfo".equals(clazz.getName());
    }

    private boolean isTransferOperationClass(Class<?> clazz) {
        if (clazz == null) return false;
        if (!hasTransferOperationCtor(clazz)) return false;
        try {
            Object instance = newTransferProbe(clazz);
            if (instance == null) return false;
            Method uri = KavaReflector.findDeclaredMethod(clazz, "getUri");
            if (uri == null) throw new NoSuchMethodException("getUri");
            Object value = KavaReflector.invoke(uri, instance);
            return "/cgi-bin/mmpay-bin/transferoperation".equals(value);
        } catch (Throwable ignored) {
            return "com.tencent.mm.plugin.remittance.model.n0".equals(clazz.getName());
        }
    }

    private boolean isTransferQueryClass(Class<?> clazz) {
        if (clazz == null) return false;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(clazz)) {
            Class<?>[] p = ctor.getParameterTypes();
            if ((p.length == 5 || p.length == 6)
                    && p[0] == int.class && p[1] == String.class && p[2] == String.class
                    && p[3] == int.class && p[4] == String.class
                    && (p.length == 5 || p[5] == String.class)) return true;
        }
        return false;
    }

    private Method findTransferQueryResponseMethod(Class<?> clazz) {
        if (clazz == null) return null;
        for (Method method : KavaReflector.declaredMethods(clazz)) {
            if (isTransferQueryResponseMethod(method, clazz)) return method;
        }
        return null;
    }

    private boolean isTransferQueryResponseMethod(Method method, Class<?> owner) {
        if (method == null || owner == null || method.getDeclaringClass() != owner
                || java.lang.reflect.Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != void.class) return false;
        Class<?>[] p = method.getParameterTypes();
        return p.length == 3 && p[0] == int.class && p[1] == String.class
                && "org.json.JSONObject".equals(p[2].getName());
    }

    private boolean isVerifyUserClass(Class<?> clazz) {
        if (clazz == null) return false;
        if (!hasVerifyUserCtor(clazz)) return false;
        try {
            Method type = KavaReflector.findMethodRecursive(clazz, "getType");
            if (type == null) return false;
            Class<?>[] params = type.getParameterTypes();
            return params.length == 0
                    && (type.getReturnType() == int.class || type.getReturnType() == Integer.class);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasVerifyUserCtor(Class<?> clazz) {
        if (clazz == null) return false;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(clazz)) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 4
                    && isIntClass(params[0])
                    && params[1] == String.class
                    && params[2] == String.class
                    && isIntClass(params[3])) {
                return true;
            }
            if (params.length == 6
                    && isIntClass(params[0])
                    && params[1] == String.class
                    && params[2] == String.class
                    && isIntClass(params[3])
                    && params[4] == String.class
                    && isIntClass(params[5])) {
                return true;
            }
            if (params.length == 8
                    && isIntClass(params[0])
                    && params[1] == String.class
                    && params[2] == String.class
                    && isIntClass(params[3])
                    && params[4] == String.class
                    && isIntClass(params[5])
                    && List.class.isAssignableFrom(params[6])) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTransferOperationCtor(Class<?> clazz) {
        if (clazz == null) return false;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(clazz)) {
            if (isTransferOperationCtorTypes(ctor.getParameterTypes())) return true;
        }
        return false;
    }

    private boolean isTransferOperationCtorTypes(Class<?>[] params) {
        if (params == null || params.length < 6
                || params[0] != String.class
                || params[1] != String.class
                || !isIntClass(params[2])
                || params[3] != String.class
                || params[4] != String.class
                || !isIntClass(params[5])) {
            return false;
        }
        if (params.length == 9) {
            return params[6] == String.class
                    && isIntClass(params[7])
                    && Map.class.isAssignableFrom(params[8]);
        }
        if (params.length == 10) {
            return params[6] == String.class
                    && params[7] == String.class
                    && isIntClass(params[8])
                    && Map.class.isAssignableFrom(params[9]);
        }
        if (params.length == 11) {
            return params[6] == String.class
                    && isIntClass(params[7])
                    && Map.class.isAssignableFrom(params[8])
                    && isLongClass(params[9])
                    && params[10] == String.class;
        }
        if (params.length == 12) {
            boolean full58 = params[6] == String.class
                    && params[7] == String.class
                    && isIntClass(params[8])
                    && Map.class.isAssignableFrom(params[9])
                    && isLongClass(params[10])
                    && params[11] == String.class;
            boolean short66 = params[6] == String.class
                    && isIntClass(params[7])
                    && Map.class.isAssignableFrom(params[8])
                    && isLongClass(params[9])
                    && params[10] == String.class
                    && params[11] == String.class;
            return full58 || short66;
        }
        if (params.length == 13) {
            boolean full66 = params[6] == String.class
                    && params[7] == String.class
                    && isIntClass(params[8])
                    && Map.class.isAssignableFrom(params[9])
                    && isLongClass(params[10])
                    && params[11] == String.class
                    && params[12] == String.class;
            boolean short68 = params[6] == String.class
                    && isIntClass(params[7])
                    && params[8] == String.class
                    && Map.class.isAssignableFrom(params[9])
                    && isLongClass(params[10])
                    && params[11] == String.class
                    && params[12] == String.class;
            return full66 || short68;
        }
        if (params.length == 14) {
            return params[6] == String.class
                    && params[7] == String.class
                    && isIntClass(params[8])
                    && params[9] == String.class
                    && Map.class.isAssignableFrom(params[10])
                    && isLongClass(params[11])
                    && params[12] == String.class
                    && params[13] == String.class;
        }
        return false;
    }

    private boolean isIntClass(Class<?> clazz) {
        return clazz == int.class || clazz == Integer.class;
    }

    private boolean isLongClass(Class<?> clazz) {
        return clazz == long.class || clazz == Long.class;
    }

    private boolean isSnsUploadPackHelperClass(Class<?> clazz) {
        if (clazz == null) return false;
        boolean hasCtor = false;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(clazz)) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 2 && isIntClass(params[0])
                    && Context.class.isAssignableFrom(params[1])) {
                hasCtor = true;
                break;
            }
        }
        if (!hasCtor) return false;

        boolean hasCommit = false;
        boolean hasAddImage = false;
        int chainStringMethods = 0;
        for (Method method : KavaReflector.declaredMethods(clazz)) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 0 && isIntClass(method.getReturnType())) {
                hasCommit = true;
            } else if (params.length == 2
                    && method.getReturnType() == boolean.class
                    && params[0] == String.class
                    && params[1] == String.class) {
                hasAddImage = true;
            } else if (params.length == 1
                    && method.getReturnType() == clazz
                    && params[0] == String.class) {
                chainStringMethods++;
            }
        }
        return hasCommit && hasAddImage && chainStringMethods >= 2;
    }

    private boolean isSnsUploadManagerClass(Class<?> clazz) {
        if (clazz == null) return false;
        boolean hasCheckPost = false;
        boolean hasListenerMethod = false;
        for (Method method : KavaReflector.declaredMethods(clazz)) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 0 && method.getReturnType() == void.class) {
                hasCheckPost = true;
            }
            if (params.length == 1 && method.getReturnType() == void.class
                    && !params[0].isPrimitive()
                    && params[0].getName().startsWith("com.tencent.mm.plugin.sns.model.")) {
                hasListenerMethod = true;
            }
        }
        return hasCheckPost && hasListenerMethod;
    }

    private Method findSnsUploadManagerGetter(Class<?> managerClass) {
        if (managerClass == null) return null;
        try {
            List<MethodData> methods = dexQueries.findMethod(
                    mkMethodUsingStrings("getSnsUploadManager"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (!KavaReflector.isStatic(method)) continue;
                    if (method.getParameterTypes().length != 0) continue;
                    if (method.getReturnType() != managerClass) continue;
                    return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Method findSnsHelperChainMethod(String marker) {
        if (snsUploadPackHelperClass == null) return null;
        try {
            List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings(marker));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (method.getDeclaringClass() != snsUploadPackHelperClass) continue;
                    if (method.getReturnType() != snsUploadPackHelperClass) continue;
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != 1 || params[0] != String.class) continue;
                    return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Method findSnsAddImageMethod(Class<?> helperClass) {
        if (helperClass == null) return null;
        try {
            List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings("addImageMediaObjByPath"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (method.getDeclaringClass() != helperClass) continue;
                    if (method.getReturnType() != boolean.class) continue;
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                        return KavaReflector.accessible(method);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        for (Method method : KavaReflector.declaredMethods(helperClass)) {
            if (method.getReturnType() != boolean.class) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                return KavaReflector.accessible(method);
            }
        }
        return null;
    }

    private Method findSnsAddVideoMethod(Class<?> helperClass) {
        if (helperClass == null) return null;
        LinkedHashSet<Method> candidates = new LinkedHashSet<>();
        for (String anchor : new String[]{"addSightObjectByPath", "produceSightByPath"}) {
            try {
                for (MethodData data : dexQueries.findMethod(mkMethodUsingStrings(anchor))) {
                    try {
                        Method method = data.getMethodInstance(classLoader);
                        if (isSnsAddVideoMethod(helperClass, method)) candidates.add(method);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        if (candidates.size() != 1) {
            snsVideoLocatorResolved = false;
            logDetail("朋友圈视频helper定位拒绝: uniqueAnchoredCandidates=" + candidates.size());
            return null;
        }
        return KavaReflector.accessible(candidates.iterator().next());
    }

    private boolean isSnsAddVideoMethod(Class<?> helperClass, Method method) {
        if (helperClass == null || method == null || method.getDeclaringClass() != helperClass
                || method.getReturnType() != boolean.class) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 4 && params.length != 5) return false;
        for (Class<?> param : params) if (param != String.class) return false;
        return true;
    }

    private Method findSnsCommitMethod(Class<?> helperClass) {
        if (helperClass == null) return null;
        try {
            List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings("commit sns info ret %d"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (method.getDeclaringClass() != helperClass) continue;
                    if (!isIntClass(method.getReturnType())) continue;
                    if (method.getParameterTypes().length == 0) return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        for (Method method : KavaReflector.declaredMethods(helperClass)) {
            if (method.getParameterTypes().length == 0 && isIntClass(method.getReturnType())) {
                return KavaReflector.accessible(method);
            }
        }
        return null;
    }

    private Method findSnsUploadCheckMethod(Class<?> managerClass) {
        if (managerClass == null) return null;
        try {
            List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings("checkPostInUI"));
            for (MethodData methodData : methods) {
                try {
                    Method method = methodData.getMethodInstance(classLoader);
                    if (method.getDeclaringClass() != managerClass) continue;
                    if (method.getReturnType() != void.class) continue;
                    if (method.getParameterTypes().length == 0) return KavaReflector.accessible(method);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        for (Method method : KavaReflector.declaredMethods(managerClass)) {
            if (method.getReturnType() == void.class && method.getParameterTypes().length == 0) {
                return KavaReflector.accessible(method);
            }
        }
        return null;
    }

    private Method findSnsShareAppMsgMethod(Class<?> managerClass) {
        if (managerClass == null) return null;
        for (Method method : KavaReflector.declaredMethods(managerClass)) {
            if (isSnsShareAppMsgMethod(method)) {
                return KavaReflector.accessible(method);
            }
        }
        return null;
    }

    private boolean isSnsShareAppMsgMethod(Method method) {
        if (method == null || KavaReflector.isStatic(method)) return false;
        if (method.getReturnType() == void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 4
                && "com.tencent.mm.opensdk.modelmsg.WXMediaMessage".equals(params[0].getName())
                && params[1] == String.class
                && params[2] == String.class
                && params[3] == String.class;
    }

    private boolean isPatDisplayTemplateMethod(Method method) {
        if (method == null || method.getReturnType() == void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 2
                && !params[0].isPrimitive()
                && params[1] == String.class;
    }

    private Object newTransferProbe(Class<?> clazz) {
        if (clazz == null) return null;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(clazz)) {
            try {
                Class<?>[] params = ctor.getParameterTypes();
                if (!isTransferOperationCtorTypes(params)) continue;
                Object[] args = new Object[params.length];
                for (int i = 0; i < params.length; i++) {
                    if (params[i] == String.class) {
                        args[i] = "";
                    } else if (params[i] == int.class || params[i] == Integer.class) {
                        args[i] = 0;
                    } else if (params[i] == long.class || params[i] == Long.class) {
                        args[i] = 0L;
                    } else if (Map.class.isAssignableFrom(params[i])) {
                        args[i] = null;
                    } else {
                        args[i] = null;
                    }
                }
                args[3] = "confirm";
                return KavaReflector.newInstance(ctor, args);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public boolean hasTransferOperationApi() {
        return transferOperationClass != null && hasTransferOperationCtor(transferOperationClass);
    }

    public boolean hasTransferQueryApi() {
        return isTransferQueryClass(transferQueryClass)
                && isTransferQueryResponseMethod(transferQueryResponseMethod, transferQueryClass);
    }

    public boolean hasVerifyUserApi() {
        return verifyUserClass != null && hasVerifyUserCtor(verifyUserClass);
    }

    public boolean hasRevokeMsgApi() {
        return revokeMsgCtor != null || findRevokeMsgCtor(revokeMsgClass) != null;
    }

    public boolean hasUploadDeviceStepApi() {
        return uploadDeviceStepCtor != null || findUploadDeviceStepCtor(uploadDeviceStepClass) != null;
    }

    public boolean hasContactLabelNetworkApi() {
        return (addContactLabelCtorString != null || addContactLabelCtorList != null)
                && (modifyContactLabelListCtor != null
                || findCtorByExactTypes(modifyContactLabelListClass, java.util.LinkedList.class) != null);
    }

    public boolean hasSnsUploadApi() {
        if (!isSnsUploadPackHelperClass(snsUploadPackHelperClass)) return false;
        if (!isSnsUploadManagerClass(snsUploadManagerClass)) return false;
        resolveSnsUploadMethods();
        return snsUploadManagerGetterMethod != null
                && snsShareAppMsgMethod != null
                && snsSetContentMethod != null
                && snsAddImageMethod != null
                && snsCommitMethod != null
                && snsUploadCheckMethod != null;
    }

    public boolean hasLocalMessageApi() {
        return localSystemMessageMethod != null
                || (localMessageInsertMethod != null
                && localMessageCreateTimeMethod != null
                && localMessageClass != null
                && localMessageCtor != null);
    }

    public boolean hasGroupSolitaireApi() {
        return groupSolitairePluginClass != null && groupSolitaireSendMethod != null;
    }

    private synchronized void saveCache() {
        if (runtimeCacheKey.length() == 0
                || !cacheStore.canSaveGlobal(resolutionFinished, !cacheDescriptorInvalid)) return;
        try {
            DexFinderCacheStore.Editor editor = cacheStore.beginGlobalSnapshot(
                    resolutionFinished, !cacheDescriptorInvalid, areCriticalLocatorsResolved(),
                    runtimeCacheKey, CACHE_SNAPSHOT_VERSION);
            if (editor == null) return;
            editor.putString(CACHE_KEY, runtimeCacheKey);
            if (receiveUnionLocatorResolved && openUnionLocatorResolved) {
                editor.putString(RED_PACKET_UNION_LOCATOR_VERSION_KEY,
                        RED_PACKET_UNION_LOCATOR_VERSION);
            }
            if (networkQueueLocatorResolved) {
                editor.putString(NETWORK_QUEUE_LOCATOR_VERSION_KEY,
                        NETWORK_QUEUE_LOCATOR_VERSION);
            }
            putMethod(editor, "addMsgObserverMethod", addMsgObserverMethod);
            putMethod(editor, "addMsgPreProcessMethod", addMsgPreProcessMethod);
            editor.putBoolean(CACHE_ADDMSG_LOCATOR_RESOLVED, addMsgLocatorResolved && isAddMsgApiValid());
            editor.putBoolean(CACHE_SEND_TEXT_LOCATOR_RESOLVED,
                    sendTextMsgLocatorResolved && isSendTextRequestApiValid());
            editor.putBoolean(CACHE_SNS_VIDEO_LOCATOR_RESOLVED,
                    snsVideoLocatorResolved && isSnsAddVideoMethod(snsUploadPackHelperClass, snsAddVideoMethod));
            putClass(editor, "receiveLuckyMoneyClass", receiveLuckyMoneyClass);
            putClass(editor, "receiveLuckyMoneyUnionClass", receiveLuckyMoneyUnionClass);
            putClass(editor, "openLuckyMoneyClass", openLuckyMoneyClass);
            putClass(editor, "openLuckyMoneyUnionClass", openLuckyMoneyUnionClass);
            putClass(editor, "netQueueClass", netQueueClass);
            editor.putString("netQueueCandidateClasses", joinClassNames(netQueueCandidateClasses));
            editor.putString("packetBaseClasses", joinClassNames(packetBaseClasses));
            editor.putString("packetQueueClasses", joinClassNames(packetQueueClasses));
            editor.putString("fakePacketClasses", joinClassNames(fakePacketClasses));
            putClass(editor, "wishWxHbClass", wishWxHbClass);
            putClass(editor, "ecsGiftTaskClass", ecsGiftTaskClass);
            putClass(editor, "ecsGiftServiceClass", ecsGiftServiceClass);
            putClass(editor, "ecsGiftMsgClass", ecsGiftMsgClass);
            putMethodList(editor, "msgDescTextMethods", msgDescTextMethods);
            putClass(editor, "sendTextMsgClass", sendTextMsgClass);
            putMethod(editor, "serviceGetterMethod", serviceGetterMethod);
            putMethodList(editor, "getContactAddMethods", getContactAddMethods);
            putMethodList(editor, "getContactServiceGetters", getContactServiceGetters);
            putMethod(editor, "sendImageMethod", sendImageMethod);
            putClass(editor, "sendImageAsyncParamsClass", sendImageAsyncParamsClass);
            putClass(editor, "sendImageCrossParamsClass", sendImageCrossParamsClass);
            putClass(editor, "sendImageAppInfoClass", sendImageAppInfoClass);
            putMethod(editor, "sendImageAsyncSubmitMethod", sendImageAsyncSubmitMethod);
            putClass(editor, "imageCdnTaskClass", imageCdnTaskClass);
            putMethod(editor, "imageCdnSubmitMethod", imageCdnSubmitMethod);
            putMethod(editor, "imageCdnServiceGetterMethod", imageCdnServiceGetterMethod);
            putClass(editor, "marsCdnManagerClass", marsCdnManagerClass);
            putClass(editor, "marsCdnDownloadRequestClass", marsCdnDownloadRequestClass);
            putClass(editor, "marsCdnDownloadCallbackClass", marsCdnDownloadCallbackClass);
            putMethod(editor, "marsCdnStartDownloadMethod", marsCdnStartDownloadMethod);
            putMethod(editor, "imageBestPathMethod", imageBestPathMethod);
            putMethod(editor, "imageStorageGetterMethod", imageStorageGetterMethod);
            putMethod(editor, "imageTokenPathMethod", imageTokenPathMethod);
            putMethod(editor, "sendFileMethod", sendFileMethod);
            putMethod(editor, "sendFileAttachDirMethod", sendFileAttachDirMethod);
            putMethod(editor, "sendFileAttachPathMethod", sendFileAttachPathMethod);
            putMethod(editor, "sendXmlAppMsgMethod", sendXmlAppMsgMethod);
            putMethod(editor, "appMsgParseMethod", appMsgParseMethod);
            putClass(editor, "groupSolitairePluginClass", groupSolitairePluginClass);
            putMethod(editor, "groupSolitaireSendMethod", groupSolitaireSendMethod);
            putMethod(editor, "localSystemMessageMethod", localSystemMessageMethod);
            putMethod(editor, "localSystemMessageStorageInsertMethod", localSystemMessageStorageInsertMethod);
            putClass(editor, "localMessageClass", localMessageClass);
            putMethod(editor, "localMessageInsertMethod", localMessageInsertMethod);
            putMethod(editor, "localMessageCreateTimeMethod", localMessageCreateTimeMethod);
            putMethod(editor, "sendVideoMethod", sendVideoMethod);
            putClass(editor, "sendVideoTaskClass", sendVideoTaskClass);
            putMethod(editor, "videoPathMethod", videoPathMethod);
            putMethod(editor, "videoPathOwnerGetterMethod", videoPathOwnerGetterMethod);
            putClass(editor, "videoInfoClass", videoInfoClass);
            putMethod(editor, "videoInfoByFileNameMethod", videoInfoByFileNameMethod);
            putClass(editor, "transferOperationClass", transferOperationClass);
            putClass(editor, "transferQueryClass", transferQueryClass);
            putMethod(editor, "transferQueryResponseMethod", transferQueryResponseMethod);
            putClass(editor, "verifyUserClass", verifyUserClass);
            putMethod(editor, "contactCardXmlMethod", contactCardXmlMethod);
            putMethod(editor, "patDisplayTemplateMethod", patDisplayTemplateMethod);
            putClass(editor, "patExtensionClass", patExtensionClass);
            putMethod(editor, "patCreatePairMethod", patCreatePairMethod);
            putMethod(editor, "patSuffixMethod", patSuffixMethod);
            putMethod(editor, "patCanSendMethod", patCanSendMethod);
            putClass(editor, "sendPatSceneClass", sendPatSceneClass);
            putMethod(editor, "voiceStartRecordMethod", voiceStartRecordMethod);
            putMethod(editor, "voiceFullPathMethod", voiceFullPathMethod);
            putMethod(editor, "voiceMessagePathMethod", voiceMessagePathMethod);
            editor.putBoolean("voiceMessagePathLookupComplete", voiceMessagePathLookupComplete);
            putMethod(editor, "voiceFinishRecordMethod", voiceFinishRecordMethod);
            putMethod(editor, "voiceInfoQueryMethod", voiceInfoQueryMethod);
            putClass(editor, "voiceUploadClass", voiceUploadClass);
            putMethod(editor, "voicePlaybackStartMethod", voicePlaybackStartMethod);
            putMethod(editor, "voicePlaybackPauseMethod", voicePlaybackPauseMethod);
            putMethod(editor, "voicePlaybackResumeMethod", voicePlaybackResumeMethod);
            putMethod(editor, "voicePlaybackStopMethod", voicePlaybackStopMethod);
            putMethod(editor, "emojiSendMethod", emojiSendMethod);
            putMethod(editor, "emojiManagerSendMethod", emojiManagerSendMethod);
            putMethod(editor, "emojiGetByMd5Method", emojiGetByMd5Method);
            putMethod(editor, "emojiCreateInfoMethod", emojiCreateInfoMethod);
            putMethod(editor, "emojiUpdateInfoMethod", emojiUpdateInfoMethod);
            putMethod(editor, "emojiAccPathMethod", emojiAccPathMethod);
            putMethod(editor, "emojiCheckGifMethod", emojiCheckGifMethod);
            putMethod(editor, "emojiFilePathMethod", emojiFilePathMethod);
            putMethod(editor, "emojiDecodeDataMethod", emojiDecodeDataMethod);
            putMethod(editor, "emojiDecodeManagerGetterMethod", emojiDecodeManagerGetterMethod);
            putClass(editor, "favoriteItemClass", favoriteItemClass);
            putMethod(editor, "favoriteItemConvertFromCursorMethod", favoriteItemConvertFromCursorMethod);
            putClass(editor, "favoriteServiceClass", favoriteServiceClass);
            putMethod(editor, "favoriteServiceResolverMethod", favoriteServiceResolverMethod);
            putMethod(editor, "favoriteStorageGetterMethod", favoriteStorageGetterMethod);
            putMethod(editor, "favoriteListMethod", favoriteListMethod);
            putMethod(editor, "favoriteListNextMethod", favoriteListNextMethod);
            putMethod(editor, "favoriteListCursorMethod", favoriteListCursorMethod);
        putMethod(editor, "favoriteGetMethod", favoriteGetMethod);
        putMethod(editor, "favoriteSendMethod", favoriteSendMethod);
            putClass(editor, "mmKernelClass", mmKernelClass);
            putClass(editor, "coreStorageClass", coreStorageClass);
            putClass(editor, "configStorageClass", configStorageClass);
            putClass(editor, "sqliteDbWrapperClass", sqliteDbWrapperClass);
            editor.putString(SQLITE_WRAPPER_LOCATOR_VERSION_KEY,
                    WeChatSqliteWrapperLocator.supportsMessageMutations(sqliteDbWrapperClass)
                            ? SQLITE_WRAPPER_LOCATOR_VERSION : "");
            putMethod(editor, "coreStorageGetter", coreStorageGetter);
            putMethod(editor, "conversationDeleteMethod", conversationDeleteMethod);
            putMethod(editor, "conversationDeleteFromHomeMethod", conversationDeleteFromHomeMethod);
            putMethod(editor, "nativeConversationHomeListQueryMethod", nativeConversationHomeListQueryMethod);
            putMethod(editor, "conversationSetPlacedTopMethod", conversationSetPlacedTopMethod);
            putMethod(editor, "conversationUnsetPlacedTopMethod", conversationUnsetPlacedTopMethod);
            putMethod(editor, "conversationHideFromHomeMethod", conversationHideFromHomeMethod);
            putMethod(editor, "messageClearByTalkerMethod", messageClearByTalkerMethod);
            putMethod(editor, "messageClearBatchMethod", messageClearBatchMethod);
            putMethod(editor, "deleteContactServiceMethod", deleteContactServiceMethod);
            putMethod(editor, "deleteContactServiceResponseMethod", deleteContactServiceResponseMethod);
            putMethod(editor, "deleteContactServiceGetterMethod", deleteContactServiceGetterMethod);
            putMethod(editor, "deleteContactServiceUiCallerMethod", deleteContactServiceUiCallerMethod);
            putMethod(editor, "contactMuteStateMethod", contactMuteStateMethod);
            putMethod(editor, "contactMuteEnableMethod", contactMuteEnableMethod);
            putMethod(editor, "contactMuteDisableMethod", contactMuteDisableMethod);
            putMethod(editor, "contactStorageGetterMethod", contactStorageGetterMethod);
            putMethod(editor, "contactStorageQueryMethod", contactStorageQueryMethod);
            putMethod(editor, "chatroomMuteServiceGetterMethod", chatroomMuteServiceGetterMethod);
            putMethod(editor, "chatroomMuteBuildMethod", chatroomMuteBuildMethod);
            putMethod(editor, "chatroomMuteSubmitMethod", chatroomMuteSubmitMethod);
            putMethod(editor, "groupMemberDisplayNameMethod", groupMemberDisplayNameMethod);
            putClass(editor, "addChatroomMemberClass", addChatroomMemberClass);
            putClass(editor, "inviteChatroomMemberClass", inviteChatroomMemberClass);
            putClass(editor, "delChatroomMemberClass", delChatroomMemberClass);
            putClass(editor, "revokeMsgClass", revokeMsgClass);
            putClass(editor, "uploadDeviceStepClass", uploadDeviceStepClass);
            putClass(editor, "addContactLabelClass", addContactLabelClass);
            putClass(editor, "modifyContactLabelListClass", modifyContactLabelListClass);
            putClass(editor, "snsUploadPackHelperClass", snsUploadPackHelperClass);
            putClass(editor, "snsUploadManagerClass", snsUploadManagerClass);
            putMethod(editor, "snsUploadManagerGetterMethod", snsUploadManagerGetterMethod);
            putMethod(editor, "snsSetContentMethod", snsSetContentMethod);
            putMethod(editor, "snsSetSdkIdMethod", snsSetSdkIdMethod);
            putMethod(editor, "snsSetSdkAppNameMethod", snsSetSdkAppNameMethod);
            putMethod(editor, "snsAddImageMethod", snsAddImageMethod);
            putMethod(editor, "snsAddVideoMethod", snsAddVideoMethod);
            putMethod(editor, "snsCommitMethod", snsCommitMethod);
            putMethod(editor, "snsShareAppMsgMethod", snsShareAppMsgMethod);
            putMethod(editor, "snsUploadCheckMethod", snsUploadCheckMethod);
            putMethod(editor, "chatPageStartMethod", chatPageStartMethod);
            putMethod(editor, "chatPageFragmentEnterMethod", chatPageFragmentEnterMethod);
            putMethod(editor, "chatPageFragmentExitMethod", chatPageFragmentExitMethod);
            putMethod(editor, "chatFooterSendClickMethod", chatFooterSendClickMethod);
            editor.putBoolean(LOCATOR_WISH_COMPLETE, wishLocatorResolved);
            editor.putBoolean(LOCATOR_PACKET_COMPLETE, packetCompatLocatorResolved);
            editor.putBoolean(LOCATOR_ECS_COMPLETE, ecsGiftLocatorResolved);
            editor.putBoolean(LOCATOR_MSG_DESC_COMPLETE, msgDescLocatorResolved);
            editor.putString(MSG_DESC_LOCATOR_VERSION_KEY, MSG_DESC_LOCATOR_VERSION);
            editor.apply();
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " 保存缓存失败: " + e.getMessage(), e);
        }
    }

    private Class<?> loadClass(String key) {
        try {
            String name = cachePrefs.getString(key, "");
            if (name == null || name.length() == 0) return null;
            Class<?> value = KavaReflector.loadClass(name, classLoader);
            if (value == null) cacheDescriptorInvalid = true;
            return value;
        } catch (Throwable ignored) {
            cacheDescriptorInvalid = true;
            return null;
        }
    }

    private Class<?> loadClassSoft(String key) {
        boolean previousInvalid = cacheDescriptorInvalid;
        cacheDescriptorInvalid = false;
        Class<?> value = loadClass(key);
        boolean invalid = cacheDescriptorInvalid;
        cacheDescriptorInvalid = previousInvalid;
        return invalid ? null : value;
    }

    private List<Class<?>> loadClassList(String key) {
        List<Class<?>> result = new ArrayList<>();
        try {
            String text = cachePrefs.getString(key, "");
            if (text == null || text.length() == 0) return result;
            String[] names = text.split(",");
            for (String raw : names) {
                String name = raw.trim();
                if (name.length() == 0) continue;
                try {
                    Class<?> clazz = KavaReflector.loadClass(name, classLoader);
                    if (clazz == null) cacheDescriptorInvalid = true;
                    if (clazz != null && !result.contains(clazz)) result.add(clazz);
                } catch (Throwable ignored) { cacheDescriptorInvalid = true; }
            }
        } catch (Throwable ignored) { cacheDescriptorInvalid = true; }
        return result;
    }

    private Method loadMethod(String key) {
        try {
            String spec = cachePrefs.getString(key, "");
            if (spec == null || spec.length() == 0) return null;
            int hash = spec.indexOf('#');
            int left = spec.indexOf('(', hash + 1);
            int right = spec.indexOf(')', left + 1);
            if (hash <= 0 || left <= hash || right < left) {
                cacheDescriptorInvalid = true;
                return null;
            }
            Class<?> owner = KavaReflector.loadClass(spec.substring(0, hash), classLoader);
            String name = spec.substring(hash + 1, left);
            String paramsText = spec.substring(left + 1, right);
            Class<?>[] params = parseParamTypes(paramsText);
            Method value = KavaReflector.findDeclaredMethod(owner, name, params);
            if (value == null) cacheDescriptorInvalid = true;
            return value;
        } catch (Throwable ignored) {
            cacheDescriptorInvalid = true;
            return null;
        }
    }

    private Method loadMethodSoft(String key) {
        boolean previousInvalid = cacheDescriptorInvalid;
        cacheDescriptorInvalid = false;
        Method value = loadMethod(key);
        boolean invalid = cacheDescriptorInvalid;
        cacheDescriptorInvalid = previousInvalid;
        return invalid ? null : value;
    }

    private List<Method> loadMethodList(String key) {
        List<Method> result = new ArrayList<>();
        try {
            String text = cachePrefs.getString(key, "");
            if (text == null || text.length() == 0) return result;
            String[] specs = text.split("\\n");
            for (String spec : specs) {
                Method method = loadMethodSpec(spec.trim());
                if (method != null && !result.contains(method)) result.add(method);
            }
        } catch (Throwable ignored) { cacheDescriptorInvalid = true; }
        return result;
    }

    private Method loadMethodSpec(String spec) {
        try {
            if (spec == null || spec.length() == 0) return null;
            int hash = spec.indexOf('#');
            int left = spec.indexOf('(', hash + 1);
            int right = spec.indexOf(')', left + 1);
            if (hash <= 0 || left <= hash || right < left) {
                cacheDescriptorInvalid = true;
                return null;
            }
            Class<?> owner = KavaReflector.loadClass(spec.substring(0, hash), classLoader);
            String name = spec.substring(hash + 1, left);
            String paramsText = spec.substring(left + 1, right);
            Class<?>[] params = parseParamTypes(paramsText);
            Method method = KavaReflector.findDeclaredMethod(owner, name, params);
            if (method == null) cacheDescriptorInvalid = true;
            return method;
        } catch (Throwable ignored) {
            cacheDescriptorInvalid = true;
            return null;
        }
    }

    private Class<?>[] parseParamTypes(String paramsText) throws ClassNotFoundException {
        if (paramsText == null || paramsText.length() == 0) return new Class<?>[0];
        String[] names = paramsText.split(",");
        Class<?>[] result = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) {
            result[i] = typeOf(names[i].trim());
        }
        return result;
    }

    private Class<?> typeOf(String name) throws ClassNotFoundException {
        if ("boolean".equals(name)) return boolean.class;
        if ("byte".equals(name)) return byte.class;
        if ("char".equals(name)) return char.class;
        if ("short".equals(name)) return short.class;
        if ("int".equals(name)) return int.class;
        if ("long".equals(name)) return long.class;
        if ("float".equals(name)) return float.class;
        if ("double".equals(name)) return double.class;
        if ("void".equals(name)) return void.class;
        return KavaReflector.loadClass(name, classLoader);
    }

    private void putClass(DexFinderCacheStore.Editor editor, String key, Class<?> clazz) {
        editor.putString(key, clazz != null ? clazz.getName() : "");
    }

    private void putMethod(DexFinderCacheStore.Editor editor, String key, Method method) {
        editor.putString(key, method != null ? methodSpec(method) : "");
    }

    private void putMethodList(DexFinderCacheStore.Editor editor, String key, List<Method> methods) {
        StringBuilder sb = new StringBuilder();
        if (methods != null) {
            for (Method method : methods) {
                if (method == null) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(methodSpec(method));
            }
        }
        editor.putString(key, sb.toString());
    }

    private String joinClassNames(List<Class<?>> classes) {
        if (classes == null || classes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Class<?> clazz : classes) {
            if (clazz == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(clazz.getName());
        }
        return sb.toString();
    }

    private String methodSpec(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getDeclaringClass().getName())
                .append('#')
                .append(method.getName())
                .append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(params[i].getName());
        }
        return sb.append(')').toString();
    }

    private String buildRuntimeCacheKey(Context context, ClassLoader loader) {
        return WeChatVersionApi.buildCacheKey(context, loader);
    }

    private String shortKey(String key) {
        if (key == null) return "";
        return key.length() <= 80 ? key : key.substring(0, 80) + "...";
    }

    // ============ 伪造/分裂红包与网络包兼容 ============
    private void resolvePacketCompatClasses() {
        if (packetCompatLocatorResolved) return;
        try {
            packetBaseClasses.clear();
            packetQueueClasses.clear();
            fakePacketClasses.clear();

            collectClassCandidates(packetBaseClasses, 10,
                    new String[][]{{"MicroMsg.NetSceneBase"}});
            collectClassCandidates(packetQueueClasses, 10,
                    new String[][]{{"doSceneImp mmcgi"}});
            collectClassCandidates(fakePacketClasses, 10,
                    new String[][]{
                            {"/cgi-bin/mmpay-bin/requestwxhb"},
                            {"NetScenePrepareLuckyMoney"},
                            {"sendMsgXml"}
                    });

            logDetail("包兼容类: base=" + packetBaseClasses.size()
                    + " queue=" + packetQueueClasses.size()
                    + " fake=" + fakePacketClasses.size());
            packetCompatLocatorResolved = true;
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolvePacketCompat 失败: " + e.getMessage(), e);
        }
    }

    private void collectClassCandidates(List<Class<?>> out, int maxCount, String[][] anchors) {
        if (out == null || anchors == null) return;
        for (String[] anchor : anchors) {
            try {
                List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings(anchor));
                int max = Math.min(classes.size(), maxCount);
                for (int i = 0; i < max; i++) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(classes.get(i).getName(), classLoader);
                        if (!out.contains(cl)) out.add(cl);
                    } catch (Throwable ignored) {}
                }
                if (!out.isEmpty()) return;
            } catch (Throwable ignored) {}
        }
    }

    private Class<?> findFirstClassByStrings(String... strings) {
        try {
            List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings(strings));
            for (ClassData cd : classes) {
                try {
                    return KavaReflector.loadClass(cd.getName(), classLoader);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Constructor<?> findCtorByArgCount(Class<?> cl, int argCount) {
        if (cl == null) return null;
        for (Constructor<?> c : KavaReflector.declaredConstructors(cl)) {
            if (c.getParameterTypes().length == argCount) {
                return c;
            }
        }
        return null;
    }

    private Constructor<?> findFirstCtorByArgCounts(Class<?> cl, int... argCounts) {
        if (cl == null || argCounts == null) return null;
        for (int argCount : argCounts) {
            Constructor<?> ctor = findCtorByArgCount(cl, argCount);
            if (ctor != null) return ctor;
        }
        return null;
    }

    private Constructor<?> findLocalMessageConstructor(Class<?> cl) {
        Constructor<?> emptyCtor = findCtorByExactTypes(cl);
        if (emptyCtor != null) return emptyCtor;
        return findCtorByExactTypes(cl, String.class);
    }

    private Constructor<?> findRevokeMsgCtor(Class<?> cl) {
        if (cl == null || localMessageClass == null) return null;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(cl)) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 3) continue;
            if (!params[0].isAssignableFrom(localMessageClass)
                    && !localMessageClass.isAssignableFrom(params[0])) {
                continue;
            }
            if (params[1] != String.class || params[2] != String.class) continue;
            return KavaReflector.accessible(ctor);
        }
        return null;
    }

    private Constructor<?> findUploadDeviceStepCtor(Class<?> cl) {
        if (cl == null) return null;
        for (Constructor<?> ctor : KavaReflector.declaredConstructors(cl)) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 7) continue;
            if (params[0] != String.class || params[1] != String.class) continue;
            if (params[2] != int.class || params[3] != int.class || params[4] != int.class) continue;
            if (params[5] != String.class || params[6] != int.class) continue;
            return KavaReflector.accessible(ctor);
        }
        return null;
    }

    /** The native network completion override belonging to the located upload scene. */
    public Method uploadDeviceStepResponseMethod() {
        Class<?> cl = uploadDeviceStepClass;
        if (cl == null) return null;
        Method result = null;
        for (Method method : KavaReflector.declaredMethods(cl)) {
            Class<?>[] p = method.getParameterTypes();
            if (!"onGYNetEnd".equals(method.getName()) || KavaReflector.isStatic(method)
                    || method.getReturnType() != void.class || p.length != 6
                    || p[0] != int.class || p[1] != int.class || p[2] != int.class
                    || p[3] != String.class || p[4].isPrimitive() || p[5] != byte[].class) continue;
            if (result != null) return null;
            result = KavaReflector.accessible(method);
        }
        return result;
    }

    /** Resolve WeChat's sport-day boundary, rejecting ambiguous matches. */
    public Method sportDayStartMethod() {
        if (dexQueries == null) return null;
        try {
            MethodMatcher matcher = new MethodMatcher();
            matcher.returnType(long.class);
            matcher.paramTypes();
            matcher.declaredClass(new ClassMatcher().usingEqStrings("getMIUIStep"));
            matcher.invokeMethods(new MethodsMatcher().add(
                    new MethodMatcher().declaredClass("java.util.Calendar")
                            .name("getTimeInMillis").paramTypes().returnType(long.class)));
            Method result = null;
            for (MethodData data : dexQueries.findMethod(new FindMethod().matcher(matcher))) {
                Method method = data.getMethodInstance(classLoader);
                if (method == null || !KavaReflector.isStatic(method)
                        || method.getReturnType() != long.class || method.getParameterCount() != 0) continue;
                if (result != null && !result.equals(method)) return null;
                result = KavaReflector.accessible(method);
            }
            return result;
        } catch (Throwable e) {
            HLog.e(TAG + " sportDayStartMethod 定位失败: " + e.getMessage(), e);
            return null;
        }
    }

    private boolean isLocalMessageInsertMethod(Method method) {
        if (method == null || method.getReturnType() != long.class) return false;
        if (!KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1) return false;
        Class<?> msgClass = params[0];
        if (msgClass == null || !msgClass.getName().startsWith("com.tencent.mm.storage.")) return false;
        return KavaReflector.findConstructor(msgClass) != null
                || KavaReflector.findConstructor(msgClass, String.class) != null;
    }

    private boolean isLocalSystemMessageStorageInsertMethod(Method method) {
        if (method == null || method.getReturnType() != long.class || KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1) return false;
        Class<?> msgClass = params[0];
        if (msgClass == null || !msgClass.getName().startsWith("com.tencent.mm.storage.")) return false;
        return KavaReflector.findConstructor(msgClass) != null
                || KavaReflector.findConstructor(msgClass, String.class) != null;
    }

    private void setLocalSystemMessageStorageInsertApi(Method method) {
        KavaReflector.accessible(method);
        localSystemMessageStorageInsertMethod = method;
        localMessageClass = method.getParameterTypes()[0];
        localMessageCtor = findLocalMessageConstructor(localMessageClass);
    }

    public void resolveGroupSolitaireApi() {
        ensureSnapshotLoaded();
        try {
            if (groupSolitaireSendMethod != null && groupSolitairePluginClass != null) return;
            List<Class<?>> candidates = new ArrayList<>();
            collectSendTextClassCandidates(candidates, 20,
                    new String[][]{
                            {"sendGroupSolitatire() content ret:%s", "PluginGroupSolitaire"},
                            {"GroupSolitaire", "sendGroupSolitatire"},
                            {"solitaire_info", "PluginGroupSolitaire"}
                    });
            collectSendTextMethodOwnerCandidates(candidates, 20,
                    new String[][]{
                            {"sendGroupSolitatire() content ret:%s", "PluginGroupSolitaire"},
                            {"sendGroupSolitatire"}
                    });
            for (Class<?> candidate : candidates) {
                Method method = findGroupSolitaireSendMethod(candidate);
                if (method == null) continue;
                groupSolitairePluginClass = candidate;
                groupSolitaireSendMethod = method;
                break;
            }
            logDetail("接龙发送API: "
                    + (groupSolitairePluginClass != null ? groupSolitairePluginClass.getName() : "null")
                    + " method=" + methodName(groupSolitaireSendMethod));
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveGroupSolitaireApi 失败: " + e.getMessage(), e);
        }
    }

    private Method findGroupSolitaireSendMethod(Class<?> cl) {
        if (cl == null) return null;
        for (Method method : KavaReflector.declaredMethods(cl)) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 6) continue;
            if (params[0] != String.class || params[1] != String.class) continue;
            if (!params[2].getName().equals("jh2.a")
                    || !params[3].getName().equals("jh2.a")
                    || !params[4].getName().equals("jh2.a")) {
                continue;
            }
            if (params[5] != boolean.class && params[5] != Boolean.class) continue;
            return KavaReflector.accessible(method);
        }
        return null;
    }

    private Method findLocalMessageCreateTimeMethod(Class<?> owner) {
        if (owner == null) return null;
        Method fallback = null;
        for (Method method : KavaReflector.declaredMethods(owner)) {
            if (!isLocalMessageCreateTimeMethod(method)) continue;
            if ("m".equals(method.getName())) {
                return KavaReflector.accessible(method);
            }
            if (fallback == null) {
                fallback = KavaReflector.accessible(method);
            }
        }
        return fallback;
    }

    private boolean isLocalMessageCreateTimeMethod(Method method) {
        if (method == null || method.getReturnType() != long.class) return false;
        if (!KavaReflector.isStatic(method)) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 2
                && params[0] == String.class
                && (params[1] == long.class || params[1] == Long.class);
    }

    private Constructor<?> findCtorByExactTypes(Class<?> cl, Class<?>... types) {
        if (cl == null || types == null) return null;
        for (Constructor<?> c : KavaReflector.declaredConstructors(cl)) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length != types.length) continue;
            boolean match = true;
            for (int i = 0; i < params.length; i++) {
                if (params[i] != types[i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return c;
            }
        }
        return null;
    }

    private void collectSendTextClassCandidates(List<Class<?>> out, int maxCount, String[][] anchors) {
        if (out == null || anchors == null) return;
        for (String[] anchor : anchors) {
            try {
                List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings(anchor));
                int max = Math.min(classes.size(), maxCount);
                for (int i = 0; i < max; i++) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(classes.get(i).getName(), classLoader);
                        if (!out.contains(cl)) out.add(cl);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    private void collectSendTextMethodOwnerCandidates(List<Class<?>> out, int maxCount, String[][] anchors) {
        if (out == null || anchors == null) return;
        for (String[] anchor : anchors) {
            try {
                List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings(anchor));
                int max = Math.min(methods.size(), maxCount);
                for (int i = 0; i < max; i++) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(methods.get(i).getClassName(), classLoader);
                        if (!out.contains(cl)) out.add(cl);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    private void collectAppMsgLogicCandidates(List<Class<?>> out, int maxCount, String[][] anchors) {
        if (out == null || anchors == null) return;
        for (String[] anchor : anchors) {
            try {
                List<MethodData> methods = dexQueries.findMethod(mkMethodUsingStrings(anchor));
                int max = Math.min(methods.size(), maxCount);
                for (int i = 0; i < max; i++) {
                    try {
                        Class<?> cl = KavaReflector.loadClass(methods.get(i).getClassName(), classLoader);
                        if (findSendFileAppMsgMethod(cl) != null && !out.contains(cl)) {
                            out.add(cl);
                        }
                    } catch (Throwable ignored) {}
                }
                if (!out.isEmpty()) return;
            } catch (Throwable ignored) {}
        }
    }

    private void addNetQueueCandidate(Class<?> cl) {
        if (cl != null && !netQueueCandidateClasses.contains(cl)) {
            netQueueCandidateClasses.add(cl);
        }
    }

    private Class<?> findFirstLikelyQueueClass() {
        for (Class<?> cl : netQueueCandidateClasses) {
            if (hasQueueSendMethodForRequests(cl)) return cl;
        }
        return null;
    }

    private boolean hasLikelyQueueSendMethod(Class<?> cl) {
        if (cl == null) return false;
        try {
            for (Method method : KavaReflector.declaredMethods(cl)) {
                String name = method.getName();
                if ("equals".equals(name) || "hashCode".equals(name) || "toString".equals(name)
                        || "wait".equals(name) || "notify".equals(name) || "notifyAll".equals(name)
                        || "cancel".equals(name)) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params == null || (params.length != 1 && params.length != 2)) continue;
                if (params.length == 2 && params[1] != int.class && params[1] != Integer.class) {
                    continue;
                }
                Class<?> first = params[0];
                if (first == null || first.isPrimitive() || first == String.class || first == Object.class) {
                    continue;
                }
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class || rt == Boolean.class
                        || rt == int.class || rt == Integer.class
                        || rt == void.class) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean hasQueueSendMethodForRequests(Class<?> cl) {
        return hasQueueSendMethodForRequest(cl, receiveLuckyMoneyClass)
                || hasQueueSendMethodForRequest(cl, receiveLuckyMoneyUnionClass)
                || hasQueueSendMethodForRequest(cl, openLuckyMoneyClass)
                || hasQueueSendMethodForRequest(cl, openLuckyMoneyUnionClass);
    }

    private boolean hasQueueSendMethodForRequest(Class<?> cl, Class<?> requestClass) {
        if (cl == null || requestClass == null) return false;
        try {
            for (Method method : KavaReflector.declaredMethods(cl)) {
                String name = method.getName();
                if ("equals".equals(name) || "hashCode".equals(name) || "toString".equals(name)
                        || "wait".equals(name) || "notify".equals(name) || "notifyAll".equals(name)
                        || "cancel".equals(name)) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params == null || (params.length != 1 && params.length != 2)) continue;
                if (params.length == 2 && params[1] != int.class && params[1] != Integer.class) continue;
                Class<?> first = params[0];
                if (first == null || first.isPrimitive() || first == String.class || first == Object.class) continue;
                if (!first.isAssignableFrom(requestClass) && !requestClass.isAssignableFrom(first)) continue;
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class || rt == Boolean.class || rt == int.class
                        || rt == Integer.class || rt == void.class) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void logMissingCritical() {
        if (addMsgClasses == null || addMsgClasses.isEmpty()) {
            h.Hchat.utils.HLog.e(TAG + " AddMsg类未找到");
        }
        if (receiveLuckyMoneyClass == null) {
            h.Hchat.utils.HLog.e(TAG + " 收红包类未找到");
        }
        if (openLuckyMoneyClass == null) {
            h.Hchat.utils.HLog.e(TAG + " 拆红包类未找到");
        }
        if (netQueueClass == null) {
            h.Hchat.utils.HLog.e(TAG + " 网络队列类未找到");
        }
        if (sendTextMsgClass == null) {
            h.Hchat.utils.HLog.e(TAG + " 文本发送类未找到");
        }
        if (sqliteDbWrapperClass == null) {
            h.Hchat.utils.HLog.e(TAG + " 数据库wrapper未找到");
        }
            if (chatPageStartMethod == null
                    || chatPageFragmentEnterMethod == null
                    || chatPageFragmentExitMethod == null) {
                h.Hchat.utils.HLog.e(TAG + " 聊天页API方法未找到: start="
                        + methodName(chatPageStartMethod)
                        + " fragmentEnter=" + methodName(chatPageFragmentEnterMethod)
                        + " fragmentExit=" + methodName(chatPageFragmentExitMethod));
        }
    }

    // ============ 联系人业务 Protobuf 发送器 ============
    // ============ ECS 礼物静默处理 ============
    public synchronized boolean resolveEcsGiftApi() {
        if (isEcsGiftApiLocated()) {
            ecsGiftLocatorResolved = true;
            return true;
        }
        ecsGiftTaskClass = null;
        ecsGiftServiceClass = null;
        ecsGiftMsgClass = null;
        ecsGiftLocatorResolved = false;
        try {
            for (MethodData md : dexQueries.findMethod(mkMethodUsingStrings("updateGiftMsgByCgi"))) {
                Class<?> task = KavaReflector.loadClass(md.getClassName(), classLoader);
                if (task == null) continue;
                for (Constructor<?> ctor : KavaReflector.declaredConstructors(task)) {
                    Class<?>[] ps = ctor.getParameterTypes();
                    if (ps == null || ps.length != 2) continue;
                    Class<?> msg = null;
                    Class<?> svc = null;
                    for (Class<?> pt : ps) {
                        if (pt.getName() != null && pt.getName().startsWith("com.tencent.mm.storage.")) msg = pt;
                        else svc = pt;
                    }
                    if (msg == null || svc == null) continue;
                    ecsGiftTaskClass = task;
                    ecsGiftServiceClass = svc;
                    ecsGiftMsgClass = msg;
                    ecsGiftLocatorResolved = isEcsGiftApiLocated();
                    logDetail("ECS礼物静默类: task=" + task.getName() + " svc=" + svc.getName() + " msg=" + msg.getName());
                    if (resolutionFinished) saveCache();
                    return ecsGiftLocatorResolved;
                }
            }
            logDetail("ECS礼物locator=not_ready (updateGiftMsgByCgi无结构候选)");
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " resolveEcsGiftApi 失败: " + e.getMessage(), e);
        }
        return false;
    }

    private boolean isEcsGiftApiLocated() {
        return ecsGiftTaskClass != null && ecsGiftServiceClass != null && ecsGiftMsgClass != null;
    }

    public synchronized boolean resolveContactProtobufSenderApi() {
        if (hasContactProtobufSenderApi()) {
            if (contactSenderLocatorState.beginAttempt()) contactSenderLocatorState.finishAttempt(true);
            return true;
        }
        if (contactSenderLocatorState.isResolved() || !contactSenderLocatorState.beginAttempt()) return false;
        boolean complete = false;
        try {
            ensureSnapshotLoaded();
            if (hasContactProtobufSenderApi()) {
                complete = true;
                return true;
            }
            if (loadContactProtobufCache()) {
                complete = true;
                return true;
            }
            resetContactProtobufSenderFields();
            if (protobufBaseClass == null) protobufBaseClass = findProtobufBaseClass();
            Class<?> protoBase = protobufBaseClass;
            if (protoBase == null) {
                clearContactProtobufCacheQuietly();
                return false;
            }
            if (protobufRawReqClass == null) protobufRawReqClass = findRawReqClass();
            if (protobufGenericRespClass == null) protobufGenericRespClass = findGenericRespClass();
            if (protobufConfigBuilderClass == null) protobufConfigBuilderClass = findConfigBuilderClass(protoBase);
            if (protobufNetSceneBaseClass == null) protobufNetSceneBaseClass = findProtobufNetSceneBaseClass();
            if (protobufCallbackClass != null && !protobufCallbackClass.isInterface()) {
                protobufCallbackClass = null;
                protobufStaticDispatchMethod = null;
            }
            if (protobufReqRespClass == null || protobufCallbackClass == null) resolveProtobufCallbackApi();
            if (protobufReqRespClass == null) protobufReqRespClass = findReqRespClassFromConfigBuilder();
            resolveProtobufDispatchApi();
            complete = hasContactProtobufSenderApi();
            if (complete) {
                saveContactProtobufCache();
            } else {
                resetContactProtobufSenderFields();
                clearContactProtobufCacheQuietly();
            }
            return complete;
        } catch (Throwable e) {
            resetContactProtobufSenderFields();
            clearContactProtobufCacheQuietly();
            h.Hchat.utils.HLog.e(TAG + " 联系人 Protobuf 发送器定位失败: " + e.getMessage(), e);
            return false;
        } finally {
            contactSenderLocatorState.finishAttempt(complete);
        }
    }

    private boolean hasContactProtobufSenderApi() {
        return isContactProtobufSenderApi(
                protobufBaseClass,
                protobufRawReqClass,
                protobufGenericRespClass,
                protobufConfigBuilderClass,
                protobufReqRespClass,
                protobufCallbackClass,
                protobufNetSceneBaseClass,
                protobufStaticDispatchMethod);
    }

    private boolean isContactProtobufSenderApi(
            Class<?> baseClass,
            Class<?> rawReqClass,
            Class<?> genericRespClass,
            Class<?> configBuilderClass,
            Class<?> reqRespClass,
            Class<?> callbackClass,
            Class<?> sceneBaseClass,
            Method dispatchMethod) {
        if (baseClass == null || rawReqClass == null
                || KavaReflector.findConstructor(rawReqClass, byte[].class) == null
                || genericRespClass == null
                || KavaReflector.findConstructor(genericRespClass) == null
                || configBuilderClass == null
                || KavaReflector.findConstructor(configBuilderClass) == null
                || reqRespClass == null
                || callbackClass == null
                || !callbackClass.isInterface()
                || sceneBaseClass == null
                || dispatchMethod == null) return false;
        Class<?>[] params = dispatchMethod.getParameterTypes();
        return KavaReflector.isStatic(dispatchMethod)
                && params.length == 3
                && params[0] == reqRespClass
                && params[1] == callbackClass
                && params[2] == boolean.class
                && dispatchMethod.getReturnType() == sceneBaseClass;
    }

    private boolean loadContactProtobufCache() {
        if (contactCachePreferenceView == null || runtimeCacheKey.length() == 0) return false;
        String savedKey = contactCachePreferenceView.getString(DexFinderCacheStore.CONTACT_KEY_KEY, "");
        boolean runtimeMatches = DexCacheIdentity.sameRuntime(runtimeCacheKey, savedKey);
        if (!runtimeMatches) {
            cacheStore.clearContactSnapshot();
            return false;
        }
        if (!cacheStore.canLoadContact(
                runtimeMatches,
                contactCachePreferenceView.getString(DexFinderCacheStore.CONTACT_VERSION_KEY, ""),
                CONTACT_CACHE_VERSION,
                contactCachePreferenceView.getBoolean(DexFinderCacheStore.CONTACT_COMPLETE_KEY, false))) {
            cacheStore.clearContactSnapshot();
            return false;
        }

        Class<?> baseClass = loadContactClass("protobufBaseClass");
        Class<?> rawReqClass = loadContactClass("protobufRawReqClass");
        Class<?> genericRespClass = loadContactClass("protobufGenericRespClass");
        Class<?> configBuilderClass = loadContactClass("protobufConfigBuilderClass");
        Class<?> reqRespClass = loadContactClass("protobufReqRespClass");
        Class<?> callbackClass = loadContactClass("protobufCallbackClass");
        Class<?> sceneBaseClass = loadContactClass("protobufNetSceneBaseClass");
        Method dispatchMethod = loadContactMethod("protobufStaticDispatchMethod");
        if (!isContactProtobufSenderApi(baseClass, rawReqClass, genericRespClass,
                configBuilderClass, reqRespClass, callbackClass, sceneBaseClass, dispatchMethod)) {
            cacheStore.clearContactSnapshot();
            return false;
        }
        protobufBaseClass = baseClass;
        protobufRawReqClass = rawReqClass;
        protobufGenericRespClass = genericRespClass;
        protobufConfigBuilderClass = configBuilderClass;
        protobufReqRespClass = reqRespClass;
        protobufCallbackClass = callbackClass;
        protobufNetSceneBaseClass = sceneBaseClass;
        protobufStaticDispatchMethod = dispatchMethod;
        return true;
    }

    private Class<?> loadContactClass(String key) {
        try {
            String name = contactCachePreferenceView.getString(key, "");
            return name.isEmpty() ? null : KavaReflector.loadClass(name, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Method loadContactMethod(String key) {
        try {
            String spec = contactCachePreferenceView.getString(key, "");
            if (spec == null || spec.isEmpty()) return null;
            int hash = spec.indexOf('#');
            int left = spec.indexOf('(', hash + 1);
            int right = spec.indexOf(')', left + 1);
            if (hash <= 0 || left <= hash || right < left) return null;
            Class<?> owner = KavaReflector.loadClass(spec.substring(0, hash), classLoader);
            Class<?>[] params = parseParamTypes(spec.substring(left + 1, right));
            return KavaReflector.findDeclaredMethod(owner, spec.substring(hash + 1, left), params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void saveContactProtobufCache() {
        if (!cacheStore.canSaveContact(hasContactProtobufSenderApi())) return;
        DexFinderCacheStore.Editor editor = cacheStore.beginContactSnapshot(
                true, runtimeCacheKey, CONTACT_CACHE_VERSION);
        if (editor == null) return;
        putClass(editor, "protobufBaseClass", protobufBaseClass);
        putClass(editor, "protobufRawReqClass", protobufRawReqClass);
        putClass(editor, "protobufGenericRespClass", protobufGenericRespClass);
        putClass(editor, "protobufConfigBuilderClass", protobufConfigBuilderClass);
        putClass(editor, "protobufReqRespClass", protobufReqRespClass);
        putClass(editor, "protobufCallbackClass", protobufCallbackClass);
        putClass(editor, "protobufNetSceneBaseClass", protobufNetSceneBaseClass);
        putMethod(editor, "protobufStaticDispatchMethod", protobufStaticDispatchMethod);
        editor.apply();
    }

    private void resetContactProtobufSenderFields() {
        protobufBaseClass = null;
        protobufRawReqClass = null;
        protobufGenericRespClass = null;
        protobufConfigBuilderClass = null;
        protobufReqRespClass = null;
        protobufCallbackClass = null;
        protobufNetSceneBaseClass = null;
        protobufStaticDispatchMethod = null;
    }

    private void clearContactProtobufCacheQuietly() {
        try {
            cacheStore.clearContactSnapshot();
        } catch (Throwable ignored) {
        }
    }

    private Class<?> findProtobufBaseClass() {
        try {
            FindClass fc = new FindClass();
            ClassMatcher cm = new ClassMatcher();
            cm.usingEqStrings("Cannot use this method");
            MethodsMatcher methods = new MethodsMatcher();
            methods.add(new MethodMatcher().name("op").paramTypes("int", "java.lang.Object[]"));
            cm.methods(methods);
            fc.matcher(cm);
            for (ClassData cd : dexQueries.findClass(fc)) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    if (cl != null) return cl;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Class<?> findRawReqClass() {
        try {
            FindClass fc = new FindClass();
            ClassMatcher cm = new ClassMatcher();
            cm.fieldCount(1);
            cm.addFieldForType(byte[].class);
            cm.addMethod(new MethodMatcher().name("<init>").paramTypes("byte[]"));
            cm.addMethod(new MethodMatcher().name("toByteArray").returnType("byte[]"));
            fc.matcher(cm);
            for (ClassData cd : dexQueries.findClass(fc)) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    if (KavaReflector.findConstructor(cl, byte[].class) != null
                            && KavaReflector.findMethod(cl, "toByteArray") != null) {
                        return cl;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }



    public Class<?> findNativeNetSceneClass(String uri, int cgiId) {
        if (uri == null || uri.length() == 0 || cgiId <= 0) return null;
        try {
            FindClass fc = new FindClass();
            ClassMatcher cm = new ClassMatcher();
            cm.usingStrings(uri);
            MethodsMatcher methods = new MethodsMatcher();
            methods.add(new MethodMatcher().name("getType").returnType(int.class).usingNumbers(cgiId));
            cm.methods(methods);
            fc.matcher(cm);
            for (ClassData cd : dexQueries.findClass(fc)) {
                Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                if (isNativeNetSceneCandidate(cl, cgiId)) {
                    logDetail("原生NetScene定位: uri=" + uri + " type=" + cgiId + " class=" + cl.getName());
                    return cl;
                }
            }
        } catch (Throwable ignored) {}

        try {
            FindMethod fm = mkMethodUsingStrings(uri);
            for (MethodData md : dexQueries.findMethod(fm)) {
                Class<?> cl = KavaReflector.loadClass(md.getClassName(), classLoader);
                if (isNativeNetSceneCandidate(cl, cgiId)) {
                    logDetail("原生NetScene定位: uri=" + uri + " type=" + cgiId + " class=" + cl.getName());
                    return cl;
                }
            }
        } catch (Throwable e) {
            h.Hchat.utils.HLog.e(TAG + " findNativeNetSceneClass 失败: uri=" + uri + " type=" + cgiId + " | " + e.getMessage(), e);
        }
        return null;
    }

    private boolean isNativeNetSceneCandidate(Class<?> cl, int cgiId) {
        if (cl == null) return false;
        if (protobufNetSceneBaseClass != null && !protobufNetSceneBaseClass.isAssignableFrom(cl)) {
            return false;
        }
        try {
            Method method = KavaReflector.findMethodRecursive(cl, "getType");
            if (method != null && method.getParameterTypes().length == 0) {
                Object value = KavaReflector.invoke(method, KavaReflector.newInstance(KavaReflector.findConstructor(cl)));
                if (value instanceof Number && ((Number) value).intValue() == cgiId) return true;
            }
        } catch (Throwable ignored) {}
        try {
            for (Method method : KavaReflector.declaredMethods(cl)) {
                if (!"getType".equals(method.getName())) continue;
                if (method.getParameterTypes().length != 0) continue;
                if (method.getReturnType() == int.class || method.getReturnType() == Integer.class) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private Class<?> findGenericRespClass() {
        try {
            FindClass fc = new FindClass();
            ClassMatcher cm = new ClassMatcher();
            cm.fieldCount(0, 1);
            cm.addMethod(new MethodMatcher().name("<init>"));
            cm.addMethod(new MethodMatcher().name("op").paramTypes("int", "java.lang.Object[]").returnType("int"));
            fc.matcher(cm);
            for (ClassData cd : dexQueries.findClass(fc)) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    if (KavaReflector.findConstructor(cl) != null
                            && KavaReflector.findMethod(cl, "op", int.class, Object[].class) != null) {
                        return cl;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Class<?> findConfigBuilderClass(Class<?> protoBase) {
        if (protoBase == null) return null;
        try {
            FindClass fc = new FindClass();
            ClassMatcher cm = new ClassMatcher();
            cm.superClass("java.lang.Object");
            cm.fieldCount(10, 80);
            cm.addFieldForType(protoBase);
            cm.addFieldForType(protoBase);
            cm.addFieldForType(String.class);
            fc.matcher(cm);
            for (ClassData cd : dexQueries.findClass(fc)) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    if (cl != null && hasConfigBuilderShape(cl, protoBase)) return cl;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean hasConfigBuilderShape(Class<?> cl, Class<?> protoBase) {
        if (cl == null || protoBase == null) return false;
        int protoFields = 0;
        int stringFields = 0;
        int intFields = 0;
        boolean hasBuild = false;
        for (java.lang.reflect.Field field : KavaReflector.declaredFields(cl)) {
            Class<?> type = field.getType();
            if (protoBase.isAssignableFrom(type)) protoFields++;
            if (type == String.class) stringFields++;
            if (type == int.class || type == Integer.class) intFields++;
        }
        for (Method method : KavaReflector.declaredMethods(cl)) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() != void.class) {
                hasBuild = true;
                break;
            }
        }
        return protoFields >= 2 && stringFields >= 1 && intFields >= 3 && hasBuild;
    }

    private Class<?> findReqRespClassFromConfigBuilder() {
        if (protobufConfigBuilderClass == null) return null;
        for (Method method : KavaReflector.declaredMethods(protobufConfigBuilderClass)) {
            try {
                if (method.getParameterTypes().length != 0) continue;
                Class<?> rt = method.getReturnType();
                if (rt == null || rt == void.class || rt.isPrimitive() || rt == String.class) continue;
                if (hasReqRespShape(rt)) return rt;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private boolean hasReqRespShape(Class<?> cl) {
        if (cl == null) return false;
        boolean hasType = false;
        boolean hasUri = false;
        boolean hasReq = false;
        boolean hasResp = false;
        for (Method method : KavaReflector.declaredMethods(cl)) {
            if (method.getParameterTypes().length != 0) continue;
            if ("getType".equals(method.getName()) && method.getReturnType() == int.class) hasType = true;
            if ("getUri".equals(method.getName()) && method.getReturnType() == String.class) hasUri = true;
            if ("getReqObj".equals(method.getName()) && method.getReturnType() != void.class) hasReq = true;
            if ("getRespObj".equals(method.getName()) && method.getReturnType() != void.class) hasResp = true;
        }
        return hasType && hasUri && hasReq && hasResp;
    }

    private Class<?> findProtobufNetSceneBaseClass() {
        try {
            FindClass fc = mkClassUsingStrings("MicroMsg.NetSceneBase");
            for (ClassData cd : dexQueries.findClass(fc)) {
                try {
                    Class<?> cl = KavaReflector.loadClass(cd.getName(), classLoader);
                    if (isProtobufNetSceneBaseClass(cl)) return cl;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        if (packetBaseClasses != null && !packetBaseClasses.isEmpty()) {
            return packetBaseClasses.get(0);
        }
        return null;
    }

    private boolean isProtobufNetSceneBaseClass(Class<?> cl) {
        if (cl == null) return false;
        for (Method method : KavaReflector.declaredMethods(cl)) {
            try {
                if ("dispatch".equals(method.getName())
                        && method.getParameterTypes().length == 3
                        && method.getReturnType() == int.class) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private void resolveProtobufCallbackApi() {
        if (protobufNetSceneBaseClass == null) return;
        try {
            FindMethod fm = new FindMethod();
            MethodMatcher mm = new MethodMatcher();
            mm.name("callback");
            mm.returnType(int.class);
            mm.paramCount(5);
            fm.matcher(mm);
            for (MethodData md : dexQueries.findMethod(fm)) {
                try {
                    Method method = md.getMethodInstance(classLoader);
                    if (!isProtobufCallbackMethod(method)) continue;
                    protobufCallbackClass = method.getDeclaringClass();
                    protobufReqRespClass = method.getParameterTypes()[3];
                    return;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private boolean isProtobufCallbackMethod(Method method) {
        if (method == null) return false;
        if (!"callback".equals(method.getName()) || method.getReturnType() != int.class) return false;
        Class<?> owner = method.getDeclaringClass();
        Class<?>[] params = method.getParameterTypes();
        return owner != null
                && owner.isInterface()
                && params.length == 5
                && params[0] == int.class
                && params[1] == int.class
                && params[2] == String.class
                && params[3] != null
                && params[3] != Object.class
                && protobufNetSceneBaseClass != null
                && params[4] == protobufNetSceneBaseClass;
    }




    private void resolveProtobufDispatchApi() {
        try {
            if (protobufReqRespClass != null && protobufCallbackClass != null && protobufStaticDispatchMethod != null) {
                return;
            }
            if (protobufReqRespClass == null || protobufCallbackClass == null) return;
            Method dispatch = findStaticDispatch(protobufReqRespClass, protobufCallbackClass);
            if (dispatch != null) {
                protobufStaticDispatchMethod = dispatch;
            }
        } catch (Throwable ignored) {}
    }

    private Method findStaticDispatch(Class<?> reqResp, Class<?> callback) {
        if (reqResp == null || callback == null) return null;
        Method broad = findStaticDispatchByName(reqResp, callback);
        if (broad != null) return broad;
        try {
            FindMethod fm = new FindMethod();
            MethodMatcher mm = new MethodMatcher();
            mm.paramCount(3);
            mm.addParamType(reqResp);
            mm.addParamType(callback);
            mm.addParamType(boolean.class);
            fm.matcher(mm);
            for (MethodData md : dexQueries.findMethod(fm)) {
                try {
                    Method method = md.getMethodInstance(classLoader);
                    Class<?>[] params = method != null ? method.getParameterTypes() : null;
                    if (method != null
                            && KavaReflector.isStatic(method)
                            && params != null
                            && params.length == 3
                            && params[0] == reqResp
                            && params[1] == callback
                            && params[2] == boolean.class) {
                        return KavaReflector.accessible(method);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Method findStaticDispatchByName(Class<?> reqResp, Class<?> callback) {
        try {
            FindMethod fm = new FindMethod();
            MethodMatcher mm = new MethodMatcher();
            mm.name("d");
            mm.paramCount(3);
            fm.matcher(mm);
            for (MethodData md : dexQueries.findMethod(fm)) {
                try {
                    Method method = md.getMethodInstance(classLoader);
                    if (isProtobufStaticDispatch(method, reqResp, callback)) {
                        return KavaReflector.accessible(method);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean isProtobufStaticDispatch(Method method, Class<?> reqResp, Class<?> callback) {
        if (method == null || reqResp == null || callback == null) return false;
        Class<?>[] params = method.getParameterTypes();
        return KavaReflector.isStatic(method)
                && params != null
                && params.length == 3
                && params[0] == reqResp
                && params[1] == callback
                && params[2] == boolean.class
                && protobufNetSceneBaseClass != null
                && method.getReturnType() == protobufNetSceneBaseClass;
    }

    private boolean isChatPageStartMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length == 3
                && params[0] == String.class
                && params[1] == Bundle.class
                && (params[2] == boolean.class || params[2] == Boolean.class);
    }

    private boolean isNoArgVoidMethod(Method method) {
        return method != null
                && method.getReturnType() == void.class
                && method.getParameterTypes().length == 0;
    }

    private boolean isChatFooterSendClickMethod(Method method) {
        if (method == null) return false;
        if (!"onClick".equals(method.getName())) return false;
        if (method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1 || params[0] != android.view.View.class) return false;
        Class<?> owner = method.getDeclaringClass();
        if (owner == null) return false;
        if (!"com.tencent.mm.pluginsdk.ui.chat".equals(owner.getPackage() != null ? owner.getPackage().getName() : "")) {
            return false;
        }
        for (java.lang.reflect.Field field : KavaReflector.declaredFields(owner)) {
            Class<?> type = field.getType();
            if (type != null && "com.tencent.mm.pluginsdk.ui.chat.ChatFooter".equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isGetContactAddMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) return false;
        Class<?>[] params = method.getParameterTypes();
        if (params == null) return false;
        if (params.length == 2) {
            return params[0] == String.class && params[1] == String.class;
        }
        return params.length == 3
                && params[0] == String.class
                && params[1] == String.class
                && (params[2] == int.class || params[2] == Integer.class);
    }

    private boolean isGetContactServiceGetter(Method method, Class<?> serviceType) {
        if (method == null || serviceType == null) return false;
        return KavaReflector.isStatic(method)
                && method.getParameterTypes().length == 0
                && serviceType.isAssignableFrom(method.getReturnType());
    }

    private String methodName(Method method) {
        if (method == null) return "null";
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    private String className(Class<?> clazz) {
        return clazz != null ? clazz.getName() : "null";
    }

    private void logDetail(String message) {
        if (VERBOSE) {
            HLog.i(TAG + " " + message);
        }
    }

    private void collectKnownNetworkQueueClasses() {
        String[] names = {
                "tk0.j1",
                "gp0.j1",
                "com.tencent.mm.modelbase.r1",
                "com.tencent.mm.kernel.h",
                "com.tencent.mm.kernel.g",
                "com.tencent.mm.model.bh",
                "com.tencent.mm.model.ak",
                "com.tencent.mm.model.az"
        };
        for (String name : names) {
            try {
                addNetQueueCandidate(KavaReflector.loadClass(name, classLoader));
            } catch (Throwable ignored) {}
        }
    }

    private void collectNetworkQueueClassesByAnchors() {
        String[][] anchors = {
                {"MicroMsg.NetSceneQueue"},
                {"NetSceneQueue"},
                {"doSceneImp start"},
                {"doSceneImp mmcgi"},
                {"On doscene  mmcgi"},
                {"doscene mmcgi Failed"},
                {"waitingQueue_size"},
                {"MicroMsg.MMKernel"},
                {"Kernel not initialized by MMApplication"},
                {"Initialize kernel, create whole WeChat world"},
                {"mCoreNetwork not initialized"},
                {"MMKernel.CoreNetwork"},
                {"MicroMsg.CoreNetwork"},
                {"doSceneImp err"},
                {"dispatcher is null"}
        };
        for (String[] anchor : anchors) {
            try {
                List<ClassData> classes = dexQueries.findClass(mkClassUsingStrings(anchor));
                int max = Math.min(classes.size(), 30);
                for (int i = 0; i < max; i++) {
                    try {
                        addNetQueueCandidate(KavaReflector.loadClass(classes.get(i).getName(), classLoader));
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }
}
