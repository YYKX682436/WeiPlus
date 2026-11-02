package h.Hchat.hooks.items.hotupdate

import android.content.Context
import h.Hchat.dexkit.DexMethodCache
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.hooks.core.XposedRuntime
import h.Hchat.tests.api102.FrameworkFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.luckypray.dexkit.DexKitBridge
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpgradeResponseHookIntegrationTest {
    class Owner { fun target(file: File) { file.name } }
    @get:org.junit.Rule val temp = org.junit.rules.TemporaryFolder()
    private val registry get() = HookRegistry.get()
    private val runtime = "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000|dex-crc|patch-files|stable"

    @Before fun setup() {
        try { XposedRuntime.get() } catch (_: IllegalStateException) { XposedRuntime.initialize(FrameworkFixture().create()) }
        registry.unhookAll()
    }
    @After fun cleanup() { registry.unhookAll() }

    @Test fun disabledFeatureDoesNotInstallHooks() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = h.Hchat.preferences.HchatStorage.preferences(context, DisableHotUpdateSettings.PREFS_NAME)
        prefs.edit().putBoolean(DisableHotUpdateSettings.KEY_ENABLE, false).commit()
        assertEquals(false, DisableHotUpdateFeature.isEnabled(context))
        val apk = temp.newFile("disabled.apk")
        java.util.zip.ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("classes.dex")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        val bridge = DexKitBridge.createManaged(apk.absolutePath, 30_000L)
        DisableHotUpdateFeature.install(context, javaClass.classLoader, bridge)
        assertEquals(0, registry.size())
        prefs.edit().putBoolean(DisableHotUpdateSettings.KEY_ENABLE, true).commit()
        assertEquals(true, DisableHotUpdateFeature.isEnabled(context))
    }

    @Test fun cachedPositiveInstallsAndCanBeReinstalledAfterUnhook() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("positive-hook-test", Context.MODE_PRIVATE)
        val loader = javaClass.classLoader!!
        DexMethodCache.saveList(prefs, runtime, "upgrade_response", listOf(Owner::class.java.getDeclaredMethod("target", File::class.java)))
        val apk = temp.newFile("fixture.apk")
        java.util.zip.ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("classes.dex")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        val bridge = DexKitBridge.createManaged(apk.absolutePath, 30_000L)
        val companion = DisableHotUpdateFeature.Companion
        val install = companion.javaClass.getDeclaredMethod("hookUpgradeResponse", Context::class.java, ClassLoader::class.java, DexKitBridge::class.java, android.content.SharedPreferences::class.java, String::class.java)
        install.isAccessible = true
        install.invoke(companion, context, loader, bridge, prefs, runtime)
        assertEquals(1, registry.size())
        registry.unhookAll()
        assertEquals(0, registry.size())
        install.invoke(companion, context, loader, bridge, prefs, runtime)
        assertEquals(1, registry.size())
    }
}
