package h.Hchat.hooks.items.tablet

import android.app.Application
import h.Hchat.dexkit.DexCacheIdentity
import h.Hchat.hooks.api.runtime.WeChatVersionApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TabletCacheIdentityRegressionTest {
    @Test fun tabletCacheUsesTheCompleteRuntimeIdentity() {
        val context = RuntimeEnvironment.getApplication() as Application
        val loader = javaClass.classLoader
        val expected = WeChatVersionApi.build(context, loader).cacheKey
        val companion = WeChatTabletFeature::class.java.getDeclaredField("Companion").apply {
            isAccessible = true
        }.get(null)
        val method = companion.javaClass.getDeclaredMethod("cacheKey", android.content.Context::class.java, ClassLoader::class.java).apply {
            isAccessible = true
        }
        val actual = method.invoke(companion, context, loader) as String
        assertEquals(expected, actual)
        assertEquals(10, actual.split('|').size)
        val trustedHostIdentity = "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000|apk-dex|patch-files|stable"
        assertEquals(true, DexCacheIdentity.sameRuntime(trustedHostIdentity, trustedHostIdentity))
    }
}
