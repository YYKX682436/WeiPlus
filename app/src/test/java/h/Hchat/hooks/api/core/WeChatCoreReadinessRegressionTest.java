package h.Hchat.hooks.api.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;

import h.Hchat.hooks.api.ui.WeChatActivityStartApi;
import h.Hchat.hooks.api.ui.WeChatCurrentActivityApi;
import h.Hchat.hooks.api.ui.WeChatLifecycleApi;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.hooks.core.XposedRuntime;
import h.Hchat.tests.api102.FrameworkFixture;
import io.github.libxposed.api.XposedInterface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WeChatCoreReadinessRegressionTest {
    @Test public void failedRequiredHookCannotPublishCoreReadiness() {
        assertFalse(WeChatApis.areRequiredCoreHooksOperational(false, true, true));
        assertFalse(WeChatApis.areRequiredCoreHooksOperational(true, false, true));
        assertFalse(WeChatApis.areRequiredCoreHooksOperational(true, true, false));
        assertTrue(WeChatApis.areRequiredCoreHooksOperational(true, true, true));
    }

    @Test public void injectedRequiredCoreHookFailureBlocksReadinessAndCanRecover() throws Exception {
        XposedInterface runtime;
        try { runtime = XposedRuntime.get(); }
        catch (IllegalStateException notInitialized) {
            runtime = new FrameworkFixture().create();
            XposedRuntime.initialize(runtime);
        }
        FrameworkFixture fixture = FrameworkFixture.forRuntime(runtime);
        assertTrue("API 102 fixture must allow controlled Hook failure", fixture != null);
        HookRegistry.get().unhookAll();
        fixture.failOn = Activity.class.getDeclaredMethod("onResume");

        WeChatCurrentActivityApi current = new WeChatCurrentActivityApi(null);
        WeChatActivityStartApi activityStart = new WeChatActivityStartApi(null);
        WeChatLifecycleApi lifecycle = new WeChatLifecycleApi(null);
        assertFalse("a failed required current-Activity Hook must block core readiness",
                WechatApiFeature.installRequiredCoreHooks(current, activityStart, lifecycle));
        assertFalse(current.isAvailable());

        fixture.failOn = null;
        assertTrue("the missing required Hook can be installed by retry",
                WechatApiFeature.installRequiredCoreHooks(current, activityStart, lifecycle));
        HookRegistry.get().unhookAll();
    }
}
