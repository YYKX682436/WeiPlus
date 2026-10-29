package h.Hchat.hooks.api.core;

import android.content.ContentValues;
import h.Hchat.WechatPackage;
import h.Hchat.dexkit.DexFinder;
import h.Hchat.event.EventBus;
import h.Hchat.event.Events;
import h.Hchat.hooks.api.message.WeChatMessageEventApi;
import h.Hchat.hooks.api.message.WeChatMessageChangeApi;
import h.Hchat.hooks.api.message.WeChatMessageObserveApi;
import h.Hchat.hooks.api.message.WeChatMessageParseApi;
import h.Hchat.hooks.api.model.DatabaseChange;
import h.Hchat.hooks.api.runtime.WeChatDatabaseListenerApi;
import h.Hchat.hooks.core.DexInstallScheduler;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.hooks.core.XposedRuntime;
import h.Hchat.tests.api102.ChainFixture;
import h.Hchat.tests.api102.FrameworkFixture;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WeChatMessageObserveReadinessRegressionTest {
    private final HookRegistry registry = HookRegistry.get();

    @Before
    public void setUp() {
        DexInstallScheduler.INSTANCE.cancelAll();
        initializeRuntimeIfNeeded();
        registry.unhookAll();
        EventBus.get().clearAll();
    }

    @After
    public void tearDown() {
        DexInstallScheduler.INSTANCE.cancelAll();
        registry.unhookAll();
        EventBus.get().clearAll();
    }

    @Test
    public void addMsgWarmupSuccessResubscribesTheProductionObserverAfterInitialMiss() throws Throwable {
        RetryableAddMsgFinder finder = new RetryableAddMsgFinder();
        WeChatMessageParseApi parser = new WeChatMessageParseApi();
        WeChatMessageEventApi eventApi = new WeChatMessageEventApi(
                finder, null, parser, EventBus.get(), ignored -> {});
        WeChatMessageObserveApi observeApi = new WeChatMessageObserveApi(
                eventApi, null, parser, null, ignored -> {});
        List<WeChatMessageObserveApi.ObservedMessage> received = new ArrayList<>();
        observeApi.subscribe(received::add);

        // This is the early install in WechatApiFeature: AddMsg has not located yet.
        observeApi.install();
        assertFalse("PB readiness stays false while the AddMsg locator is unavailable",
                eventApi.isOperational());

        WechatApiFeature feature = new WechatApiFeature();
        feature.scheduleAddMsgObserverInstall(finder, eventApi, observeApi);
        DexInstallScheduler.markDexWarmupReady();
        awaitCondition("the real WARMUP installer must first observe the AddMsg locator miss",
                () -> finder.firstAttemptCompleted);
        assertFalse("the first AddMsg locator result must be a real miss", finder.firstAttemptResult);
        assertFalse(eventApi.isOperational());

        finder.locatorReady = true;
        awaitCondition("the scheduled retry must install the production AddMsg Hook",
                eventApi::isOperational);
        assertTrue("a second production resolution attempt must succeed", finder.resolveAttempts.get() >= 2);

        Request request = request("deferred-event", 991_240L);
        invokeAddMsgHook(request);
        assertEquals("a successful AddMsg Hook must trigger PB subscription and dispatch once", 1,
                received.size());
        assertEquals("add_msg", received.get(0).source);
        assertEquals("wxid_peer", received.get(0).talker);
        assertFalse("the incoming AddMsg remains incoming", received.get(0).outgoing);
        assertEquals("AddMsg msgSource is retained through the shared observer",
                "<msgsource><atuserlist>wxid_peer</atuserlist></msgsource>",
                received.get(0).message.msgSource);

        int registrations = registry.size();
        observeApi.install();
        observeApi.install();
        feature.scheduleAddMsgObserverInstall(finder, eventApi, observeApi);
        DexInstallScheduler.markDexWarmupReady();
        awaitCondition("repeated DexReady/install keeps the effective Hook", eventApi::isOperational);
        invokeAddMsgHook(request);
        assertEquals("repeat readiness and a duplicate message must dispatch only once", 1,
                received.size());
        assertEquals("duplicate installs reuse the active Hook handle", registrations, registry.size());
        assertEquals("locator=true hook=true subscription=true event=observed db_subscription=false db_hook=false",
                observeApi.readinessDiagnostics());

        EventBus.get().post(new Events.MessageReceived(
                "", "self-id", "wxid_peer", "synthetic outgoing", "1",
                System.currentTimeMillis() / 1000L, 991_253L,
                "<msgsource>sent-source</msgsource>", "self-id", "local_send", true));
        assertEquals(2, received.size());
        assertTrue("the explicit send direction is preserved", received.get(1).outgoing);
        assertEquals("local_send", received.get(1).source);
        assertEquals("<msgsource>sent-source</msgsource>", received.get(1).message.msgSource);
    }

    @Test
    public void invalidPayloadAndListenerExceptionsStayInsideTheObserverBoundary() throws Throwable {
        RetryableAddMsgFinder finder = new RetryableAddMsgFinder();
        finder.locatorReady = true;
        WeChatMessageParseApi parser = new WeChatMessageParseApi();
        WeChatMessageEventApi eventApi = new WeChatMessageEventApi(
                finder, null, parser, EventBus.get(), ignored -> {});
        WeChatMessageObserveApi observeApi = new WeChatMessageObserveApi(
                eventApi, null, parser, null, ignored -> {});
        AtomicInteger delivered = new AtomicInteger();
        observeApi.subscribe(message -> { throw new IllegalStateException("synthetic callback failure"); });
        observeApi.subscribe(message -> delivered.incrementAndGet());

        assertTrue(eventApi.installAddMsgHook());
        observeApi.install();
        invokeAddMsgHook(request("valid-event", 991_241L));
        assertEquals("one listener failure cannot prevent a later observer", 1, delivered.get());

        Payload invalidTalker = new Payload();
        invalidTalker.e = "1";
        invalidTalker.h = "invalid-payload";
        invokeAddMsgHook(new Request(invalidTalker));
        Payload missingContent = new Payload();
        missingContent.h = "";
        invokeAddMsgHook(new Request(missingContent));
        invokeAddMsgHook(request("null-storage-result", 991_242L), null);

        assertEquals("invalid usernames, parse failures, and missing storage results are ignored", 1,
                delivered.get());
        assertTrue("AddMsg callback verification requires a parsed new message",
                (Boolean) fieldValue(eventApi, "callbackVerified"));
    }

    @Test
    public void generationRevocationRemovesOldEventAndBusinessSubscriptionsBeforeReinstall() throws Throwable {
        RetryableAddMsgFinder finder = new RetryableAddMsgFinder();
        finder.locatorReady = true;
        WeChatMessageParseApi parser = new WeChatMessageParseApi();
        WeChatMessageEventApi eventApi = new WeChatMessageEventApi(
                finder, null, parser, EventBus.get(), ignored -> {});
        WeChatMessageObserveApi observeApi = new WeChatMessageObserveApi(
                eventApi, null, parser, null, ignored -> {});
        AtomicInteger oldListener = new AtomicInteger();
        WeChatMessageObserveApi.Subscription oldToken = observeApi.subscribe(message -> oldListener.incrementAndGet());
        observeApi.install();
        WechatApiFeature feature = new WechatApiFeature();
        feature.scheduleAddMsgObserverInstall(finder, eventApi, observeApi);
        DexInstallScheduler.markDexWarmupReady();
        awaitCondition("the first generation installs AddMsg and the PB observer", eventApi::isOperational);
        invokeAddMsgHook(request("generation-one", 991_243L));
        assertEquals(1, oldListener.get());

        registry.unhookAll();
        assertFalse("the old business subscription token is revoked", oldToken.isActive());
        EventBus.get().post(new Events.MessageReceived(
                "", "wxid_peer", "wxid_peer", "after-revoke", "1",
                System.currentTimeMillis() / 1000L, 991_244L));
        assertEquals("the previous observer cannot dispatch after a generation switch", 1,
                oldListener.get());

        AtomicInteger newListener = new AtomicInteger();
        WeChatMessageObserveApi.Subscription newToken = observeApi.subscribe(message -> newListener.incrementAndGet());
        observeApi.install();
        feature.scheduleAddMsgObserverInstall(finder, eventApi, observeApi);
        awaitCondition("a new generation reinstalls the AddMsg and PB hooks", eventApi::isOperational);
        invokeAddMsgHook(request("generation-two", 991_245L));
        assertEquals(1, newListener.get());
        assertEquals("old callbacks remain cleared after reinstall", 1, oldListener.get());

        observeApi.destroy();
        eventApi.destroy();
        assertFalse("destroy revokes its public subscription token", newToken.isActive());
        EventBus.get().post(new Events.MessageReceived(
                "", "wxid_peer", "wxid_peer", "after-destroy", "1",
                System.currentTimeMillis() / 1000L, 991_246L));
        assertEquals(1, newListener.get());
    }

    @Test
    public void canceledAddMsgRetryCannotInstallAStaleHookLater() throws Exception {
        RetryableAddMsgFinder finder = new RetryableAddMsgFinder();
        WeChatMessageParseApi parser = new WeChatMessageParseApi();
        WeChatMessageEventApi eventApi = new WeChatMessageEventApi(
                finder, null, parser, EventBus.get(), ignored -> {});
        WeChatMessageObserveApi observeApi = new WeChatMessageObserveApi(
                eventApi, null, parser, null, ignored -> {});
        observeApi.install();
        new WechatApiFeature().scheduleAddMsgObserverInstall(finder, eventApi, observeApi);
        DexInstallScheduler.markDexWarmupReady();
        awaitCondition("the pending AddMsg task completes its failed locator attempt",
                () -> finder.firstAttemptCompleted);
        assertFalse(finder.firstAttemptResult);

        DexInstallScheduler.INSTANCE.cancel("wechat_api:addmsg_observer");
        finder.locatorReady = true;
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(Duration.ofSeconds(30));
        assertFalse("cancelled AddMsg retries must remain cancelled", eventApi.isOperational());
        assertFalse(observeApi.isPbLayerSubscribed());
    }

    @Test
    public void databaseFallbackRemainsAvailableAndDoesNotDuplicateAnAddMsgEvent() throws Throwable {
        RetryableAddMsgFinder finder = new RetryableAddMsgFinder();
        finder.sqliteDbWrapperClass = MessageDbWrapper.class;
        WeChatMessageParseApi parser = new WeChatMessageParseApi();
        WeChatMessageEventApi eventApi = new WeChatMessageEventApi(
                finder, null, parser, EventBus.get(), ignored -> {});
        WeChatDatabaseListenerApi databaseApi = new WeChatDatabaseListenerApi(
                finder, getClass().getClassLoader(), ignored -> {});
        WeChatMessageChangeApi changeApi = new WeChatMessageChangeApi(
                databaseApi, null, null, ignored -> {});
        changeApi.install();
        WeChatMessageObserveApi observeApi = new WeChatMessageObserveApi(
                eventApi, changeApi, parser, null, ignored -> {});
        List<WeChatMessageObserveApi.ObservedMessage> received = new ArrayList<>();
        observeApi.subscribe(received::add);
        observeApi.install();
        assertFalse(observeApi.isPbLayerSubscribed());
        assertTrue(changeApi.isInstalled());

        ContentValues fallbackValues = messageValues("database-fallback", 991_247L);
        MapSnapshot fallbackSnapshot = new MapSnapshot(fallbackValues);
        invokeDatabaseInsert(databaseApi, fallbackValues, 101L);
        assertEquals("database remains a functioning fallback before AddMsg is ready", 1,
                received.size());
        assertEquals("message_db", received.get(0).source);
        fallbackSnapshot.assertUnchanged(fallbackValues);

        WechatApiFeature feature = new WechatApiFeature();
        feature.scheduleAddMsgObserverInstall(finder, eventApi, observeApi);
        DexInstallScheduler.markDexWarmupReady();
        awaitCondition("the first AddMsg locator attempt fails", () -> finder.firstAttemptCompleted);
        assertFalse(finder.firstAttemptResult);
        finder.locatorReady = true;
        awaitCondition("AddMsg retry installs both the Hook and PB subscriptions", eventApi::isOperational);
        assertTrue(finder.resolveAttempts.get() >= 2);
        assertTrue(observeApi.isPbLayerSubscribed());

        Request addMsg = request("shared-event", 991_248L);
        invokeAddMsgHook(addMsg);
        assertEquals(2, received.size());
        assertEquals("add_msg", received.get(1).source);

        ContentValues duplicateValues = messageValues("shared-event", 991_248L);
        MapSnapshot duplicateSnapshot = new MapSnapshot(duplicateValues);
        invokeDatabaseInsert(databaseApi, duplicateValues, 102L);
        assertEquals("the active PB layer suppresses the duplicate database notification", 2,
                received.size());
        duplicateSnapshot.assertUnchanged(duplicateValues);
    }

    @Test
    public void supportedWechatPackagesKeepProcessAndDexCacheIdentitiesSeparate() {
        String official = "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000|dex-a|patch-files|loader-main";
        String modified = "com.tencent.mm.wx|8.0.76|3140|client|tinker|patch|1700000000|dex-b|patch-files|loader-modified";
        assertTrue(WechatPackage.isTarget(WechatPackage.OFFICIAL));
        assertTrue(WechatPackage.isTarget(WechatPackage.MODIFIED));
        assertTrue(WechatPackage.isModuleEntryScope(WechatPackage.OFFICIAL, "com.tencent.mm:push"));
        assertTrue(WechatPackage.isModuleEntryScope(WechatPackage.MODIFIED, "com.tencent.mm.wx:push"));
        assertFalse(WechatPackage.isModuleEntryScope(WechatPackage.OFFICIAL, "com.tencent.mm.wx:push"));
        assertFalse(h.Hchat.dexkit.DexCacheIdentity.sameRuntime(official, modified));

        h.Hchat.hooks.api.runtime.HostRuntimeIdentityDecision identity =
                new h.Hchat.hooks.api.runtime.HostRuntimeIdentityDecision();
        assertTrue(identity.approve(official, "official-loader"));
        assertEquals(h.Hchat.hooks.api.runtime.HostRuntimeIdentityDecision.Result.HOST_IDENTITY_CHANGED,
                identity.verify(modified, "modified-loader", () -> {}));
    }

    @Test
    public void eventBusSubscriptionCredentialsRemoveOnlyTheirOwnRegistration() {
        AtomicInteger dispatches = new AtomicInteger();
        h.Hchat.event.EventHandler<Events.MessageReceived> handler = event -> dispatches.incrementAndGet();
        EventBus.Subscription first = EventBus.get().subscribe(Events.MessageReceived.class, handler);
        EventBus.Subscription second = EventBus.get().subscribe(Events.MessageReceived.class, handler);

        EventBus.get().unsubscribe(first);
        EventBus.get().post(messageEvent("credential-test", 991_249L));

        assertFalse(first.isActive());
        assertTrue(second.isActive());
        assertEquals("canceling one token retains the other identical handler registration", 1,
                dispatches.get());
        EventBus.get().unsubscribe(second);
        EventBus.get().post(messageEvent("credential-test-2", 991_250L));
        assertEquals(1, dispatches.get());
    }

    @Test
    public void duplicateRawListenersKeepIndependentCancellationCredentials() throws Throwable {
        RetryableAddMsgFinder finder = new RetryableAddMsgFinder();
        finder.locatorReady = true;
        WeChatMessageParseApi parser = new WeChatMessageParseApi();
        WeChatMessageEventApi eventApi = new WeChatMessageEventApi(
                finder, null, parser, EventBus.get(), ignored -> {});
        AtomicInteger calls = new AtomicInteger();
        WeChatMessageEventApi.RawAddMsgListener listener = ignored -> calls.incrementAndGet();
        WeChatMessageEventApi.RawSubscription first = eventApi.subscribeRawAddMsg(listener);
        WeChatMessageEventApi.RawSubscription second = eventApi.subscribeRawAddMsg(listener);
        assertTrue(eventApi.installAddMsgHook());

        first.unsubscribe();
        invokeAddMsgHook(request("raw-credential-one", 991_251L));
        assertEquals(1, calls.get());
        assertFalse(first.isActive());
        assertTrue(second.isActive());

        second.unsubscribe();
        invokeAddMsgHook(request("raw-credential-two", 991_252L));
        assertEquals("the final raw token removes the callback", 1, calls.get());
    }

    private void invokeAddMsgHook(Request request) throws Throwable {
        invokeAddMsgHook(request, new Object());
    }

    private void invokeAddMsgHook(Request request, Object storedMessage) throws Throwable {
        Method target = AddMsgHost.class.getDeclaredMethod(
                "process", Request.class, int.class, int.class);
        HookHandle registration = null;
        for (HookHandle hook : registeredHooks()) {
            if (target.equals(hook.getExecutable())) {
                registration = hook;
                break;
            }
        }
        if (registration == null) throw new AssertionError("production AddMsg hook was not registered");
        Field callbackField = registration.getClass().getDeclaredField("callback");
        callbackField.setAccessible(true);
        Hooker callback = (Hooker) callbackField.get(registration);
        ChainFixture chain = new ChainFixture(target, null, new Object[]{request, 0, 0},
                (receiver, args) -> new Result(storedMessage));
        chain.run(callback);
    }

    private Object fieldValue(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private ContentValues messageValues(String content, long serverId) {
        ContentValues values = new ContentValues();
        values.put("isSend", 0);
        values.put("type", 1);
        values.put("talker", "wxid_peer");
        values.put("content", content);
        values.put("createTime", System.currentTimeMillis() / 1000L);
        values.put("msgSvrId", serverId);
        values.put("msgSource", "");
        return values;
    }

    private void invokeDatabaseInsert(WeChatDatabaseListenerApi databaseApi,
                                      ContentValues values,
                                      long rowId) throws Throwable {
        Method target = MessageDbWrapper.class.getDeclaredMethod(
                "insert", String.class, String.class, ContentValues.class);
        Field registrationsField = WeChatDatabaseListenerApi.class.getDeclaredField("hookRegistrations");
        registrationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Method, ?> registrations = (Map<Method, ?>) registrationsField.get(databaseApi);
        Object registration = registrations.get(target);
        if (registration == null) throw new AssertionError("production database insert hook was not registered");
        Field handleField = registration.getClass().getDeclaredField("handle");
        handleField.setAccessible(true);
        Object handle = handleField.get(registration);
        Field callbackField = handle.getClass().getDeclaredField("callback");
        callbackField.setAccessible(true);
        Hooker callback = (Hooker) callbackField.get(handle);
        ChainFixture chain = new ChainFixture(target, new MessageDbWrapper(),
                new Object[]{"message", null, values}, (receiver, args) -> rowId);
        chain.run(callback);
    }

    private List<HookHandle> registeredHooks() throws Exception {
        Field handlesField = HookRegistry.class.getDeclaredField("unhooks");
        handlesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HookHandle> handles = (List<HookHandle>) handlesField.get(registry);
        return handles;
    }

    private void awaitCondition(String description, java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(Duration.ofMillis(100));
            try {
                Thread.sleep(10L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(description, condition.getAsBoolean());
    }

    private void initializeRuntimeIfNeeded() {
        try {
            XposedRuntime.get();
        } catch (IllegalStateException missing) {
            XposedRuntime.initialize(new FrameworkFixture().create());
        }
    }

    private static Request request(String content, long serverId) {
        Payload payload = new Payload();
        payload.h = content;
        payload.r = serverId;
        payload.o = System.currentTimeMillis() / 1000L;
        return new Request(payload);
    }

    private static Events.MessageReceived messageEvent(String content, long serverId) {
        return new Events.MessageReceived("", "wxid_peer", "wxid_peer", content, "1",
                System.currentTimeMillis() / 1000L, serverId);
    }

    private static final class RetryableAddMsgFinder extends DexFinder {
        volatile boolean locatorReady;
        volatile boolean firstAttemptCompleted;
        volatile boolean firstAttemptResult;
        final AtomicInteger resolveAttempts = new AtomicInteger();

        RetryableAddMsgFinder() throws Exception {
            super(null, WeChatMessageObserveReadinessRegressionTest.class.getClassLoader(), null);
            addMsgObserverMethod = AddMsgHost.class.getDeclaredMethod(
                    "process", Request.class, int.class, int.class);
            addMsgRequestPayloadField = Request.class.getDeclaredField("payload");
            addMsgResultMessageField = Result.class.getDeclaredField("message");
            addMsgRequestPayloadField.setAccessible(true);
            addMsgResultMessageField.setAccessible(true);
        }

        @Override
        public synchronized boolean resolveAddMsgApi() {
            int attempt = resolveAttempts.incrementAndGet();
            boolean resolved = locatorReady;
            if (attempt == 1) {
                firstAttemptResult = resolved;
                firstAttemptCompleted = true;
            }
            return resolved;
        }

        @Override
        public synchronized boolean isAddMsgLocatorResolved() {
            return locatorReady;
        }
    }

    private static final class MapSnapshot {
        private final Map<String, Object> before = new HashMap<>();
        MapSnapshot(ContentValues values) {
            for (String key : values.keySet()) before.put(key, values.get(key));
        }
        void assertUnchanged(ContentValues values) {
            if (!before.keySet().equals(values.keySet())) {
                throw new AssertionError("database observer changed ContentValues keys");
            }
            for (String key : before.keySet()) {
                if (!java.util.Objects.equals(before.get(key), values.get(key))) {
                    throw new AssertionError("database observer changed ContentValues value");
                }
            }
        }
    }

    public static final class Payload {
        public String e = "wxid_peer";
        public String f = "";
        public String p = "<msgsource><atuserlist>wxid_peer</atuserlist></msgsource>";
        public String h = "";
        public int g = 1;
        public long o;
        public long r;
    }

    public static final class Request {
        public Payload payload;
        Request(Payload payload) { this.payload = payload; }
    }

    public static final class Result {
        public Object message;
        Result(Object message) { this.message = message; }
    }

    public static final class AddMsgHost {
        public static Result process(Request request, int option, int sequence) {
            return new Result(new Object());
        }
    }

    public static final class MessageDbWrapper {
        public long insert(String table, String nullColumnHack, ContentValues values) { return 0L; }
        public int update(String table, ContentValues values, String where, String[] args) { return 0; }
        public int delete(String table, String where, String[] args) { return 0; }
    }
}
