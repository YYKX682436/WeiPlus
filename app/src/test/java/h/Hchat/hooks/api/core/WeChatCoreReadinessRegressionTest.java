package h.Hchat.hooks.api.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ContextWrapper;
import android.content.Intent;

import h.Hchat.hooks.api.ui.WeChatActivityStartApi;
import h.Hchat.hooks.api.ui.WeChatCurrentActivityApi;
import h.Hchat.hooks.api.ui.WeChatLifecycleApi;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.hooks.core.XposedRuntime;
import h.Hchat.tests.api102.FrameworkFixture;
import h.Hchat.tests.api102.ChainFixture;
import h.Hchat.utils.KavaReflector;
import io.github.libxposed.api.XposedInterface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WeChatCoreReadinessRegressionTest {
    private FrameworkFixture fixture;

    @Before public void setUp() {
        XposedInterface runtime;
        try { runtime = XposedRuntime.get(); }
        catch (IllegalStateException notInitialized) {
            runtime = new FrameworkFixture().create();
            XposedRuntime.initialize(runtime);
        }
        fixture = FrameworkFixture.forRuntime(runtime);
        assertTrue("API 102 fixture must allow controlled Hook failure", fixture != null);
        HookRegistry.get().unhookAll();
    }

    @After public void tearDown() {
        fixture.failOn = null;
        HookRegistry.get().unhookAll();
        assertTrue("test cleanup must remove every framework Hook", HookRegistry.get().size() == 0);
    }

    @Test public void failedRequiredHookCannotPublishCoreReadiness() {
        assertFalse(WeChatApis.areRequiredCoreHooksOperational(false, true, true));
        assertFalse(WeChatApis.areRequiredCoreHooksOperational(true, false, true));
        assertFalse(WeChatApis.areRequiredCoreHooksOperational(true, true, false));
        assertTrue(WeChatApis.areRequiredCoreHooksOperational(true, true, true));
    }

    @Test public void injectedRequiredLifecycleHookFailureBlocksCoreReadinessAndCanRecover() throws Exception {
        WeChatCurrentActivityApi current = new WeChatCurrentActivityApi(null);
        WeChatActivityStartApi activityStart = new WeChatActivityStartApi(null);
        WeChatLifecycleApi lifecycle = new WeChatLifecycleApi(null);
        current.install();
        activityStart.install();
        fixture.failOn = Activity.class.getDeclaredMethod("onResume");
        assertFalse("a failed required lifecycle Hook must block core readiness",
                WechatApiFeature.installRequiredCoreHooks(current, activityStart, lifecycle));
        assertTrue(current.isAvailable());
        assertTrue(activityStart.isAvailable());
        assertFalse("all three lifecycle Hooks are required", lifecycle.isAvailable());
        assertTrue("the other two lifecycle Hooks remain installed and are not duplicated",
                lifecycle.hookedMethodCount() == 2);

        fixture.failOn = null;
        assertTrue("the missing required Hook can be installed by retry",
                WechatApiFeature.installRequiredCoreHooks(current, activityStart, lifecycle));
        assertTrue(lifecycle.hookedMethodCount() == 3);
    }

    @Test public void eachLifecycleHookFailureRetriesOnlyTheMissingEntryAndDispatchesOnce() throws Throwable {
        for (String name : Arrays.asList("onResume", "onPause", "onDestroy")) {
            HookRegistry.get().unhookAll();
            fixture.failOn = Activity.class.getDeclaredMethod(name);
            WeChatLifecycleApi lifecycle = new WeChatLifecycleApi(null);
            AtomicInteger hostCalls = new AtomicInteger();
            List<String> events = new ArrayList<>();
            lifecycle.subscribe(event -> events.add(event.event));

            lifecycle.install();
            assertFalse(name + " failure must not mark lifecycle ready", lifecycle.isAvailable());
            assertTrue("two lifecycle entries should install around injected failure", lifecycle.hookedMethodCount() == 2);
            assertTrue(HookRegistry.get().size() == 2);
            int callsBeforeRetry = fixture.calls;

            fixture.failOn = null;
            lifecycle.install();
            assertTrue("retry must make all callbacks operational", lifecycle.isAvailable());
            assertTrue(lifecycle.hookedMethodCount() == 3);
            assertTrue("retry installs only the missing method", fixture.calls == callsBeforeRetry + 1);
            assertTrue("no lifecycle Hook is duplicated", HookRegistry.get().size() == 3);
            int callsAfterRetry = fixture.calls;
            lifecycle.install();
            assertTrue(fixture.calls == callsAfterRetry && HookRegistry.get().size() == 3);

            Activity activity = org.robolectric.Robolectric.buildActivity(Activity.class).get();
            for (String callback : Arrays.asList("onResume", "onPause", "onDestroy")) {
                Method method = Activity.class.getDeclaredMethod(callback);
                FrameworkFixture.Handle handle = activeHandle(method);
                Object result = new ChainFixture(method, activity, new Object[0], (receiver, args) -> {
                    hostCalls.incrementAndGet();
                    return callback;
                }).run(handle.hooker);
                assertTrue(callback.equals(result));
            }
            assertTrue("host callbacks run exactly once each", hostCalls.get() == 3);
            assertTrue("each lifecycle event is dispatched exactly once",
                    events.equals(Arrays.asList("resume", "pause", "destroy")));

            HookRegistry.get().unhookAll();
            assertFalse("availability follows real handles after cleanup", lifecycle.isAvailable());
            assertTrue(lifecycle.hookedMethodCount() == 0 && HookRegistry.get().size() == 0);
        }
    }

    @Test public void currentActivityPartialInstallationCanRetryWithoutDuplicatingHooks() throws Throwable {
        for (String name : Arrays.asList("onPause", "onResume", "onDestroy")) {
            HookRegistry.get().unhookAll();
            fixture.failOn = Activity.class.getDeclaredMethod(name);
            WeChatCurrentActivityApi current = new WeChatCurrentActivityApi(null);
            current.install();
            assertFalse(name + " failure must not mark current Activity ready", current.isAvailable());
            assertFalse("partial current Activity hooks must block core readiness",
                    WeChatApis.areRequiredCoreHooksOperational(current.isAvailable(), true, true));
            int installedPrefix = countActiveActivityHooks();
            int callsBeforeRetry = fixture.calls;
            fixture.failOn = null;
            current.install();
            assertTrue(current.isAvailable() && countActiveActivityHooks() == 3);
            assertTrue("current Activity retry installs only missing callbacks",
                    fixture.calls == callsBeforeRetry + 3 - installedPrefix);
            int callsAfterRetry = fixture.calls;
            current.install();
            assertTrue(fixture.calls == callsAfterRetry && countActiveActivityHooks() == 3);

            Activity activity = org.robolectric.Robolectric.buildActivity(Activity.class).get();
            AtomicInteger hostCalls = new AtomicInteger();
            invokeActivityHook("onResume", activity, hostCalls);
            assertTrue(current.currentActivity() == activity);
            invokeActivityHook("onPause", activity, hostCalls);
            assertTrue(current.currentActivity() == null);
            invokeActivityHook("onResume", activity, hostCalls);
            invokeActivityHook("onDestroy", activity, hostCalls);
            assertTrue(current.currentActivity() == null);
            assertTrue("current Activity wrappers invoke each host callback once", hostCalls.get() == 4);

            HookRegistry.get().unhookAll();
            assertFalse(current.isAvailable());
            assertTrue(countActiveActivityHooks() == 0 && HookRegistry.get().size() == 0);
        }
    }

    @Test public void activityStartPartialInstallationRetriesMissingMethodOnly() throws Throwable {
        HookRegistry.get().unhookAll();
        List<Method> expected = activityStartMethods();
        assertTrue("fixture platform must expose Activity launch methods", !expected.isEmpty());
        Method failedMethod = expected.get(Math.min(1, expected.size() - 1));
        fixture.failOn = failedMethod;
        WeChatActivityStartApi api = new WeChatActivityStartApi(null);
        AtomicInteger hostCalls = new AtomicInteger();
        AtomicInteger listenerCalls = new AtomicInteger();
        Intent wanted = new Intent("r2-test");
        api.subscribe((intent, caller, method) -> {
            if (intent == wanted) listenerCalls.incrementAndGet();
        });

        api.install();
        assertFalse("a missing discovered launch Hook cannot report complete", api.isAvailable());
        assertTrue(api.hookedMethodCount() == expected.size() - 1);
        assertTrue(HookRegistry.get().size() == expected.size() - 1);
        assertFalse(WeChatApis.areRequiredCoreHooksOperational(true, api.isAvailable(), true));
        int callsBeforeRetry = fixture.calls;

        fixture.failOn = null;
        api.install();
        assertTrue(api.isAvailable());
        assertTrue(api.hookedMethodCount() == expected.size());
        assertTrue("only the failed launch method is installed on retry", fixture.calls == callsBeforeRetry + 1);
        assertTrue(HookRegistry.get().size() == expected.size());
        int callsAfterRetry = fixture.calls;
        api.install();
        assertTrue(fixture.calls == callsAfterRetry && HookRegistry.get().size() == expected.size());

        Object[] args = defaultArguments(failedMethod.getParameterTypes(), wanted);
        FrameworkFixture.Handle handle = activeHandle(failedMethod);
        Object result = new ChainFixture(failedMethod, org.robolectric.Robolectric.buildActivity(Activity.class).get(), args,
                (receiver, hostArgs) -> { hostCalls.incrementAndGet(); return "host"; }).run(handle.hooker);
        assertTrue("host result is preserved", "host".equals(result));
        assertTrue(hostCalls.get() == 1 && listenerCalls.get() == 1);

        HookRegistry.get().unhookAll();
        assertFalse(api.isAvailable());
        assertTrue(api.hookedMethodCount() == 0 && HookRegistry.get().size() == 0);
    }

    private FrameworkFixture.Handle activeHandle(Method method) {
        return fixture.handles.stream()
                .filter(handle -> handle.active && handle.getExecutable().equals(method))
                .findFirst().orElseThrow(() -> new AssertionError("missing active Hook for " + method));
    }

    private int countActiveActivityHooks() {
        int count = 0;
        for (String name : Arrays.asList("onResume", "onPause", "onDestroy")) {
            try {
                Method method = Activity.class.getDeclaredMethod(name);
                if (fixture.handles.stream().anyMatch(handle -> handle.active && handle.getExecutable().equals(method))) count++;
            } catch (NoSuchMethodException failure) { throw new AssertionError(failure); }
        }
        return count;
    }

    private void invokeActivityHook(String name, Activity activity, AtomicInteger hostCalls) throws Throwable {
        Method method = Activity.class.getDeclaredMethod(name);
        Object result = new ChainFixture(method, activity, new Object[0], (receiver, hostArgs) -> {
            hostCalls.incrementAndGet();
            return "host";
        }).run(activeHandle(method).hooker);
        assertTrue("host result preserved", "host".equals(result));
    }

    private static List<Method> activityStartMethods() {
        List<Method> result = new ArrayList<>();
        for (Class<?> owner : Arrays.asList(Activity.class, ContextWrapper.class)) {
            for (Method method : KavaReflector.declaredMethods(owner)) {
                if (!(method.getName().equals("startActivity") || method.getName().equals("startActivityForResult"))) continue;
                if (Arrays.stream(method.getParameterTypes()).anyMatch(type -> type == Intent.class)) result.add(method);
            }
        }
        return result;
    }

    private static Object[] defaultArguments(Class<?>[] types, Intent intent) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == Intent.class) args[i] = intent;
            else if (types[i] == boolean.class) args[i] = false;
            else if (types[i] == byte.class) args[i] = (byte) 0;
            else if (types[i] == short.class) args[i] = (short) 0;
            else if (types[i] == int.class) args[i] = 0;
            else if (types[i] == long.class) args[i] = 0L;
            else if (types[i] == float.class) args[i] = 0f;
            else if (types[i] == double.class) args[i] = 0d;
            else if (types[i] == char.class) args[i] = '\0';
        }
        return args;
    }
}
