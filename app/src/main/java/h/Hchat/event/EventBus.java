package h.Hchat.event;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 轻量级事件总线。
 * 支持按事件类型订阅/取消，线程安全，支持优先级。
 * 事件默认在发布者线程同步回调，不做线程切换（Xposed 环境下保持可预测性）。
 */
public final class EventBus {

    private static final String TAG = "[Hchat:EventBus]";

    private static final EventBus INSTANCE = new EventBus();

    // eventType -> handlers，按优先级降序排列
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<HandlerEntry<?>>> handlers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, Object> stickyEvents = new ConcurrentHashMap<>();

    private EventBus() {}

    public static EventBus get() {
        return INSTANCE;
    }

    // ============ 发布 ============

    /** 发布事件，同步回调所有订阅者。 */
    public <E> void post(E event) {
        if (event == null) return;
        Class<?> eventClass = event.getClass();
        if (isStickyEvent(eventClass)) {
            stickyEvents.put(eventClass, event);
        }
        List<HandlerEntry<?>> entries = handlers.get(eventClass);
        if (entries == null || entries.isEmpty()) return;
        for (HandlerEntry<?> entry : entries) {
            if (entry.subscription != null && entry.subscription.isActive()) {
                dispatch(event, entry.handler);
            }
        }
    }

    // ============ 订阅 ============

    /**
     * 订阅事件，默认优先级 0。
     * @return Subscription，用于后续取消订阅
     */
    public <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler) {
        return subscribe(eventType, handler, 0);
    }

    /**
     * 订阅事件，带优先级。优先级越高越先执行。
     * @return Subscription，用于后续取消订阅
     */
    public <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler, int priority) {
        if (eventType == null || handler == null) return Subscription.EMPTY;
        HandlerEntry<E> entry = new HandlerEntry<>(handler, priority);
        Subscription subscription = new Subscription(eventType, handler, entry);
        entry.subscription = subscription;
        CopyOnWriteArrayList<HandlerEntry<?>> list =
                handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        // 按优先级降序插入
        int insertIndex = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).priority >= priority) {
                insertIndex = i + 1;
            }
        }
        if (insertIndex >= list.size()) {
            list.add(entry);
        } else {
            list.add(insertIndex, entry);
        }
        dispatchStickyIfReady(eventType, handler);
        return subscription;
    }

    /**
     * 取消指定订阅。
     */
    public void unsubscribe(Subscription subscription) {
        if (subscription == null || subscription == Subscription.EMPTY) return;
        List<HandlerEntry<?>> entries = handlers.get(subscription.eventType);
        if (entries != null && subscription.entry != null) entries.remove(subscription.entry);
        subscription.active = false;
    }

    /**
     * 取消某个类的所有订阅（用于 Feature 销毁时批量清理）。
     */
    public void unsubscribeAll(Class<?> ownerClass) {
        if (ownerClass == null) return;
        for (CopyOnWriteArrayList<HandlerEntry<?>> entries : handlers.values()) {
            entries.removeIf(e -> {
                boolean remove = ownerClass.isInstance(e.handler);
                if (remove && e.subscription != null) e.subscription.active = false;
                return remove;
            });
        }
    }

    /**
     * 清除所有订阅（慎用，仅用于测试或模块卸载）。
     */
    public void clearAll() {
        for (CopyOnWriteArrayList<HandlerEntry<?>> entries : handlers.values()) {
            for (HandlerEntry<?> entry : entries) {
                if (entry.subscription != null) entry.subscription.active = false;
            }
        }
        handlers.clear();
        stickyEvents.clear();
    }

    private boolean isStickyEvent(Class<?> eventType) {
        return eventType == Events.DexReady.class;
    }

    private <E> void dispatchStickyIfReady(Class<E> eventType, EventHandler<E> handler) {
        Object event = stickyEvents.get(eventType);
        if (event == null) return;
        try {
            handler.handleEvent(eventType.cast(event));
        } catch (Throwable e) {
            logHandlerError(eventType, handler, e);
        }
    }

    private <E> void dispatch(E event, EventHandler<?> rawHandler) {
        try {
            @SuppressWarnings("unchecked")
            EventHandler<E> handler = (EventHandler<E>) rawHandler;
            handler.handleEvent(event);
        } catch (Throwable e) {
            logHandlerError(event.getClass(), rawHandler, e);
        }
    }

    private void logHandlerError(Class<?> eventType, EventHandler<?> handler, Throwable e) {
        h.Hchat.utils.HLog.e(TAG + " 处理事件异常: " + eventType.getSimpleName()
                + ", handler=" + handler.getClass().getSimpleName()
                + ", errorType=" + e.getClass().getSimpleName());
    }

    // ============ 内部结构 ============

    private static final class HandlerEntry<E> {
        final EventHandler<E> handler;
        final int priority;
        volatile Subscription subscription;

        HandlerEntry(EventHandler<E> handler, int priority) {
            this.handler = handler;
            this.priority = priority;
        }
    }

    /**
     * 订阅凭证，用于取消订阅。
     */
    public static final class Subscription {
        static final Subscription EMPTY = new Subscription(null, null);
        final Class<?> eventType;
        final EventHandler<?> handler;
        final HandlerEntry<?> entry;
        private volatile boolean active = true;

        Subscription(Class<?> eventType, EventHandler<?> handler) {
            this(eventType, handler, null);
        }

        Subscription(Class<?> eventType, EventHandler<?> handler, HandlerEntry<?> entry) {
            this.eventType = eventType;
            this.handler = handler;
            this.entry = entry;
        }

        public static Subscription empty() {
            return EMPTY;
        }

        public boolean isActive() {
            return this != EMPTY && active;
        }
    }
}
