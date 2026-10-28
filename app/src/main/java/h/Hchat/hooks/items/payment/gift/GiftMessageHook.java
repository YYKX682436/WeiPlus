package h.Hchat.hooks.items.payment.gift;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import h.Hchat.hooks.api.core.WeChatApis;
import h.Hchat.hooks.api.message.WeChatMessageEventApi;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.items.payment.detect.RedPacketParser;
import h.Hchat.hooks.items.payment.detect.RedPacketReflector;

public class GiftMessageHook {
    private static final int MAX_DEDUP = 300;

    private final DexFinder dexFinder;
    private final GiftSettings settings;
    private final GiftSilentHandler handler;
    private final GiftSilentHandler.Logger logger;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> processedOrders = new LinkedHashSet<>();
    private final List<Object> subscriptions = new ArrayList<>();
    private WeChatMessageEventApi.RawSubscription rawSubscription;

    public GiftMessageHook(DexFinder dexFinder, GiftSettings settings, GiftSilentHandler handler,
                           GiftSilentHandler.Logger logger) {
        this.dexFinder = dexFinder;
        this.settings = settings;
        this.handler = handler;
        this.logger = logger;
    }

    public List<Object> hook() {
        if (rawSubscription != null) return subscriptions;
        if (dexFinder == null || !dexFinder.resolveAddMsgApi()) {
            log("AddMsg定位未就绪，跳过礼物监听");
            return subscriptions;
        }
        WeChatMessageEventApi api = WeChatApis.messageEvents();
        if (api == null || !api.isOperational()) {
            log("AddMsg post-process observer未就绪，跳过礼物监听");
            return subscriptions;
        }
        rawSubscription = api.subscribeRawAddMsg(this::onMessage);
        if (rawSubscription != null) subscriptions.add(rawSubscription);
        log("礼物消息观察=" + (rawSubscription != null ? "subscribed" : "missing"));
        return subscriptions;
    }

    public void unsubscribe() {
        if (rawSubscription != null) rawSubscription.unsubscribe();
        rawSubscription = null;
        subscriptions.clear();
    }

    public boolean isHooked() {
        return rawSubscription != null;
    }

    private void onMessage(Object addMsg) {
        if (addMsg == null) return;
        if (!settings.isEnabled()) return;

        String content = RedPacketReflector.findAddMsgContent(addMsg);
        if (TextUtils.isEmpty(content)) return;

        String xml = content;
        int split = content.indexOf(":\n");
        if (split > 0 && content.indexOf('<') > split) {
            xml = content.substring(split + 2);
        }
        if (xml.indexOf("<orderid>") < 0) return;

        final String orderId = RedPacketParser.getXmlParamByTag(xml, "orderid");
        if (TextUtils.isEmpty(orderId)) return;
        if (!markProcessed(orderId)) return;

        final Object message = addMsg;
        long delay = settings.getDelayMillis();
        log("检测到礼物消息, orderId=" + orderId + (delay > 0 ? " 延迟" + delay + "ms" : ""));

        if (delay > 0) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    handler.grab(message, orderId);
                }
            }, delay);
        } else {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    handler.grab(message, orderId);
                }
            });
        }
    }

    private boolean markProcessed(String orderId) {
        synchronized (processedOrders) {
            if (!processedOrders.add(orderId)) return false;
            if (processedOrders.size() > MAX_DEDUP) {
                String first = processedOrders.iterator().next();
                processedOrders.remove(first);
            }
            return true;
        }
    }

    private void log(String message) {
        if (logger != null) logger.log(message);
    }
}
