package h.Hchat.hooks.api.message;

import android.text.TextUtils;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.event.EventBus;
import h.Hchat.event.EventHandler;
import h.Hchat.event.Events;
import h.Hchat.hooks.api.contact.WeChatAccountApi;
import h.Hchat.hooks.api.model.WeChatParsedMessage;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.utils.KavaReflector;

import java.lang.reflect.Field;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 微信消息事件 API。
 */
public final class WeChatMessageEventApi {
    public interface Logger {
        void log(String message);
    }

    public interface RawAddMsgListener {
        void onRawAddMsg(Object addMsg);
    }

    private final DexFinder dexFinder;
    private final WeChatAccountApi accountApi;
    private final WeChatMessageParseApi parseApi;
    private final EventBus eventBus;
    private final Logger logger;
    private volatile io.github.libxposed.api.XposedInterface.HookHandle addMsgHook;
    private volatile boolean patHooked;
    private final ConcurrentHashMap<String, Long> recentPatEvents = new ConcurrentHashMap<>();
    private final AddMsgEventDeduplicator addMsgDeduplicator = new AddMsgEventDeduplicator();
    private final CopyOnWriteArrayList<RawAddMsgListener> rawAddMsgListeners = new CopyOnWriteArrayList<>();
    private volatile boolean callbackVerified;

    public WeChatMessageEventApi(DexFinder dexFinder,
                                 WeChatAccountApi accountApi,
                                 WeChatMessageParseApi parseApi,
                                 EventBus eventBus,
                                 Logger logger) {
        this.dexFinder = dexFinder;
        this.accountApi = accountApi;
        this.parseApi = parseApi;
        this.eventBus = eventBus;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return dexFinder != null && dexFinder.isAddMsgLocatorResolved()
                && parseApi != null && eventBus != null;
    }

    public boolean isOperational() {
        return HookRegistry.get().isActive(addMsgHook);
    }

    public synchronized boolean installAddMsgHook() {
        if (!isAvailable()) return false;
        if (isOperational()) {
            installPatMsgHook();
            return true;
        }
        final Field payloadField = dexFinder.addMsgRequestPayloadField;
        final Field storedMessageField = dexFinder.addMsgResultMessageField;
        if (payloadField == null || storedMessageField == null) return false;
        try {
            final HookRegistry registry = HookRegistry.get();
            final long generation = registry.installationGeneration();
            addMsgHook = HookRegistry.get().hookOnce(
                    "wechat.addmsg.observer",
                    dexFinder.addMsgObserverMethod,
                    chain -> {
                        if (!registry.isInstallationGenerationActive(generation)) return chain.proceed();
                        Object request = chain.getArg(0);
                        Object result = chain.proceed();
                        if (!registry.isInstallationGenerationActive(generation)) return result;
                        try {
                            Object stored = result != null ? storedMessageField.get(result) : null;
                            Object addMsg = request != null ? payloadField.get(request) : null;
                            if (stored != null && addMsg != null) {
                                boolean fresh = dispatchAddMsg(addMsg);
                                if (fresh) for (RawAddMsgListener listener : rawAddMsgListeners) {
                                    try { listener.onRawAddMsg(addMsg); }
                                    catch (Throwable e) { log("原始消息观察回调异常: " + e.getMessage()); }
                                }
                                if (!callbackVerified) {
                                    callbackVerified = true;
                                    log("AddMsg callback=verified observer=post_process");
                                }
                            }
                        } catch (Throwable e) {
                            log("AddMsg观察结果读取失败: " + e.getMessage());
                        }
                        return result;
                    });
        installPatMsgHook();
        log("AddMsg定位=" + dexFinder.addMsgObserverMethod.getName() + " Hook="
                + (isOperational() ? "installed" : "inactive"));
        return isOperational();
        } catch (Throwable e) {
            addMsgHook = null;
            log("AddMsg Hook安装失败: " + e.getMessage());
            return false;
        }
    }

    public RawSubscription subscribeRawAddMsg(RawAddMsgListener listener) {
        if (listener == null) return null;
        rawAddMsgListeners.addIfAbsent(listener);
        return new RawSubscription(this, listener);
    }

    public static final class RawSubscription {
        private final WeChatMessageEventApi owner;
        private final RawAddMsgListener listener;
        private volatile boolean active = true;

        private RawSubscription(WeChatMessageEventApi owner, RawAddMsgListener listener) {
            this.owner = owner;
            this.listener = listener;
        }

        public void unsubscribe() {
            if (active) {
                active = false;
                owner.rawAddMsgListeners.remove(listener);
            }
        }
    }

    public EventBus.Subscription subscribeMessage(EventHandler<Events.MessageReceived> handler) {
        return eventBus != null ? eventBus.subscribe(Events.MessageReceived.class, handler)
                : null;
    }

    public EventBus.Subscription subscribeRedPacket(EventHandler<Events.RedPacketDetected> handler) {
        return eventBus != null ? eventBus.subscribe(Events.RedPacketDetected.class, handler)
                : null;
    }

    public EventBus.Subscription subscribePat(EventHandler<Events.PatDetected> handler) {
        return eventBus != null ? eventBus.subscribe(Events.PatDetected.class, handler)
                : null;
    }

    public void unsubscribe(EventBus.Subscription subscription) {
        if (eventBus != null) eventBus.unsubscribe(subscription);
    }

    private boolean dispatchAddMsg(Object addMsg) {
        try {
            String selfWxId = accountApi != null ? accountApi.selfWxId() : "";
            WeChatParsedMessage parsed = parseApi.parseAddMsg(addMsg, selfWxId);
            if (parsed == null) return false;
            long now = System.currentTimeMillis();
            if (!addMsgDeduplicator.markIfNew(parsed.talker, parsed.sender,
                    parsed.createTimeSeconds, parsed.msgSvrId, parsed.content, now)) return false;
            eventBus.post(new Events.MessageReceived(
                    parsed.xml, parsed.sender, parsed.talker, parsed.content,
                    String.valueOf(parsed.type), parsed.createTimeSeconds, parsed.msgSvrId,
                    parsed.msgSource, parsed.selfWxId));
            if (parsed.redPacketMessage) {
                eventBus.post(new Events.RedPacketDetected(
                        "WeChatMessageEventApi",
                        parsed.xml,
                        parsed.sender,
                        parsed.talker,
                        parsed.nativeUrl,
                        parsed.exclusiveRecvUser));
            }
            return true;
        } catch (Throwable e) {
            log("派发消息事件失败: " + e.getMessage());
            return false;
        }
    }

    private void installPatMsgHook() {
        if (patHooked || dexFinder == null || dexFinder.patDisplayTemplateMethod == null
                || eventBus == null) {
            return;
        }
        HookRegistry.get()
                .hook(
                        dexFinder.patDisplayTemplateMethod,
                        chain -> {
                            if (!chain.getArgs().isEmpty()) {
                                dispatchPatMsg(
                                        chain.getArg(0),
                                        chain.getArgs().size() > 1 ? chain.getArg(1) : null);
                            }
                            return chain.proceed();
                        });
        patHooked = true;
        log("拍一拍事件Hook: " + dexFinder.patDisplayTemplateMethod.getDeclaringClass().getName()
                + "#" + dexFinder.patDisplayTemplateMethod.getName());
    }

    private void dispatchPatMsg(Object patMsg, Object talkerArg) {
        try {
            if (patMsg == null) return;
            String fromUser = readFieldString(patMsg, "d");
            String pattedUser = readFieldString(patMsg, "e");
            String template = readFieldString(patMsg, "f");
            long createTime = readPatCreateTime(patMsg);
            long svrId = readPatSvrId(patMsg);
            if (TextUtils.isEmpty(fromUser) || TextUtils.isEmpty(pattedUser)) return;
            if (!isRecentPat(createTime)) return;
            String key =
                    fromUser + ":" + pattedUser + ":" + svrId + ":" + createTime + ":" + template;
            long now = System.currentTimeMillis();
            Long previous = recentPatEvents.put(key, now);
            if (previous != null && now - previous < 300000L) return;
            cleanupPatEvents(now);
            String talker = talkerArg instanceof String ? (String) talkerArg : "";
            eventBus.post(new Events.PatDetected(
                    fromUser,
                    pattedUser,
                    template,
                    talker,
                    createTime,
                    svrId));
        } catch (Throwable e) {
            log("派发拍一拍事件失败: " + e.getMessage());
        }
    }

    private long readPatCreateTime(Object patMsg) {
        if (hasField(patMsg.getClass(), "g")) return readFieldLong(patMsg, "g");
        return readFieldLong(patMsg, "i");
    }

    private long readPatSvrId(Object patMsg) {
        if (hasField(patMsg.getClass(), "g")) return readFieldLong(patMsg, "i");
        return readFieldLong(patMsg, "n");
    }

    private boolean isRecentPat(long createTime) {
        if (createTime <= 0L) return true;
        long millis = createTime > 100000000000L ? createTime : createTime * 1000L;
        return Math.abs(System.currentTimeMillis() - millis) <= 300000L;
    }

    private String readFieldString(Object obj, String field) {
        Object value = KavaReflector.readField(obj, field);
        return value != null ? String.valueOf(value) : "";
    }

    private long readFieldLong(Object obj, String field) {
        Object value = KavaReflector.readField(obj, field);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private boolean hasField(Class<?> clazz, String name) {
        return KavaReflector.findFieldRecursive(clazz, name) != null;
    }

    private void cleanupPatEvents(long now) {
        if (recentPatEvents.size() < 128) return;
        recentPatEvents.entrySet().removeIf(entry -> now - entry.getValue() > 300000L);
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatMessageEventApi] " + message);
    }
}
