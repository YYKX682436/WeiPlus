package h.Hchat.hooks.items.payment.detect;

import android.text.TextUtils;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.event.EventBus;
import h.Hchat.event.Events;
import h.Hchat.hooks.api.core.WeChatApis;
import h.Hchat.hooks.api.message.WeChatMessageEventApi;
import h.Hchat.hooks.items.payment.core.RedPacketSettings;

/**
 * AddMsg 消息预监听。
 * 负责从微信 AddMsg 对象中识别红包消息，并把识别结果回调给业务层。
 */
public class RedPacketMessageHook {
    public interface Logger {
        void log(String message);
    }

    public interface LoginProvider {
        String getLoginWxid();
    }

    public interface RedPacketDetectedCallback {
        void onDetected(String source, String xml, String sender, String talker,
                        String nativeUrl, String exclusiveRecvUser);
    }

    private final DexFinder dexFinder;
    private final RedPacketSettings settings;
    private final LoginProvider loginProvider;
    private final RedPacketDetectedCallback callback;
    private final Logger logger;
    private volatile EventBus.Subscription subscription;

    public RedPacketMessageHook(
            DexFinder dexFinder,
            RedPacketSettings settings,
            LoginProvider loginProvider,
            RedPacketDetectedCallback callback,
            Logger logger
    ) {
        this.dexFinder = dexFinder;
        this.settings = settings;
        this.loginProvider = loginProvider;
        this.callback = callback;
        this.logger = logger;
    }

    public synchronized Object hook() {
        if (subscription != null) return new SubscriptionOwner(this);
        WeChatMessageEventApi api = WeChatApis.messageEvents();
        if (api == null || !api.isOperational()) {
            log("消息事件入口未就绪，跳过直接Hook");
            return null;
        }
        subscription = api.subscribeRedPacket(event -> {
            if (!settings.getBoolean(RedPacketSettings.KEY_ENABLE, false) || event == null) return;
            String nativeUrl = event.nativeUrl;
            if (TextUtils.isEmpty(nativeUrl)) {
                nativeUrl = RedPacketParser.getXmlParamByTag(event.xml, "nativeurl");
            }
            if (TextUtils.isEmpty(nativeUrl)) return;
            if (callback != null) callback.onDetected(
                    event.source != null ? event.source : "AddMsgObserver",
                    event.xml != null ? event.xml : "",
                    event.sender != null ? event.sender : "",
                    event.talker != null ? event.talker : "",
                    nativeUrl,
                    event.exclusiveRecvUser != null ? event.exclusiveRecvUser : "");
        });
        log("红包消息监听=" + (subscription != null ? "subscribed" : "missing"));
        return subscription != null ? new SubscriptionOwner(this) : null;
    }

    public synchronized void unsubscribe() {
        EventBus.Subscription previous = subscription;
        subscription = null;
        WeChatMessageEventApi api = WeChatApis.messageEvents();
        if (api != null && previous != null) api.unsubscribe(previous);
    }

    public boolean isHooked() {
        return subscription != null;
    }

    private static final class SubscriptionOwner {
        private final RedPacketMessageHook owner;
        SubscriptionOwner(RedPacketMessageHook owner) { this.owner = owner; }
        public void unsubscribe() { owner.unsubscribe(); }
    }

    private void log(String message) {
        if (logger != null) logger.log(message);
    }
}
