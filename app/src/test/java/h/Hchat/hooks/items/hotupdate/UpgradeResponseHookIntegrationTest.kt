package h.Hchat.hooks.items.hotupdate

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
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
    class TwoOwners {
        fun first(file: File) { file.name }
        fun second(file: File) { file.path }
    }
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

    @Test fun partialEarlyHookInstallCanRetryOnlyTheFailedEntry() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = hchatStoragePrefs(context)
        prefs.edit().putBoolean(DisableHotUpdateSettings.KEY_ENABLE, true).commit()
        val runtime = XposedRuntime.get()
        val fixture = FrameworkFixture.forRuntime(runtime)
            ?: throw AssertionError("API 102 framework fixture is required for failure injection")
        val loader = javaClass.classLoader!!
        val methods = loader.loadClass("com.tencent.tinker.loader.TinkerLoader").declaredMethods
            .filter { method ->
                method.name == "tryLoadPatchFilesInternal" && method.parameterTypes.size >= 2 &&
                    method.parameterTypes[0].name == "com.tencent.tinker.loader.app.TinkerApplication" &&
                    method.parameterTypes[1] == android.content.Intent::class.java
            }
        assertEquals("two controlled TinkerLoader overloads", 2, methods.size)

        fixture.failOn = methods.last()
        DisableHotUpdateFeature.installEarly(context, loader)
        assertEquals("flag and first loader entry installed before injected failure", 2, registry.size())

        fixture.failOn = null
        DisableHotUpdateFeature.installEarly(context, loader)
        assertEquals("retry fills the missing loader entry without duplicating the first", 3, registry.size())
        DisableHotUpdateFeature.installEarly(context, loader)
        assertEquals("successful entries remain deduplicated", 3, registry.size())
    }

    @Test fun partialLateHookInstallRetriesOnlyTheFailedEntry() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("partial-late-hook-test", Context.MODE_PRIVATE)
        val owner = TwoOwners()
        val first = owner.javaClass.getDeclaredMethod("first", File::class.java)
        val second = owner.javaClass.getDeclaredMethod("second", File::class.java)
        DexMethodCache.saveList(prefs, runtime, "upgrade_response", listOf(first, second))
        val apk = temp.newFile("partial-fixture.apk")
        java.util.zip.ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("classes.dex")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        val bridge = DexKitBridge.createManaged(apk.absolutePath, 30_000L)
        val fixture = FrameworkFixture.forRuntime(XposedRuntime.get())
            ?: throw AssertionError("API 102 framework fixture is required for failure injection")
        fixture.failOn = second

        val companion = DisableHotUpdateFeature.Companion
        val install = companion.javaClass.getDeclaredMethod(
            "hookUpgradeResponse", Context::class.java, ClassLoader::class.java,
            DexKitBridge::class.java, android.content.SharedPreferences::class.java, String::class.java)
        install.isAccessible = true
        install.invoke(companion, context, javaClass.classLoader, bridge, prefs, runtime)
        assertEquals("the successful response entry remains while the second install is injected to fail", 1, registry.size())

        fixture.failOn = null
        install.invoke(companion, context, javaClass.classLoader, bridge, prefs, runtime)
        assertEquals("a later attempt fills the failed entry", 2, registry.size())
        install.invoke(companion, context, javaClass.classLoader, bridge, prefs, runtime)
        assertEquals("the recovered entries are not duplicated", 2, registry.size())
    }

    @Test fun publicInstallerDoesNotSealLateEntriesAfterOneHookFails() {
        val base = RuntimeEnvironment.getApplication()
        val apk = temp.newFile("host-runtime.apk")
        java.util.zip.ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("classes.dex")); zip.write(byteArrayOf(1, 2, 3)); zip.closeEntry()
        }
        val appInfo = ApplicationInfo(base.applicationInfo).apply { sourceDir = apk.absolutePath }
        val context = object : ContextWrapper(base) {
            override fun getPackageName() = "com.tencent.mm"
            override fun getApplicationContext(): Context = this
            override fun getApplicationInfo() = appInfo
        }
        val loader = javaClass.classLoader!!
        val prefs = DexMethodCache.prefs(context, "Hchat_hot_update_method_cache")
        prefs.edit().clear().commit()
        val runtimeKey = DexMethodCache.runtimeKey(context, loader)
        val owner = TwoOwners()
        val first = owner.javaClass.getDeclaredMethod("first", File::class.java)
        val second = owner.javaClass.getDeclaredMethod("second", File::class.java)
        DexMethodCache.saveList(prefs, runtimeKey, "upgrade_response", listOf(first, second))
        assertEquals(runtimeKey, prefs.getString("cache.key", ""))
        assertEquals(2, DexMethodCache.loadUpgradeResponse(prefs, runtimeKey, loader).methods.size)
        val bridge = DexKitBridge.createManaged(apk.absolutePath, 30_000L)
        val fixture = FrameworkFixture.forRuntime(XposedRuntime.get())
            ?: throw AssertionError("API 102 framework fixture is required for failure injection")
        fixture.failOn = second

        val completeFirst = DisableHotUpdateFeature.install(context, loader, bridge)
        assertEquals("one late endpoint failure leaves this install incomplete", false, completeFirst)
        assertEquals("three early entries and one late entry survive the isolated failure", 4, registry.size())

        fixture.failOn = null
        DisableHotUpdateFeature.install(context, loader, bridge)
        assertEquals("public install retries the missing late entry", 5, registry.size())
        DisableHotUpdateFeature.install(context, loader, bridge)
        assertEquals("successful public installs are deduplicated", 5, registry.size())
    }

    private fun hchatStoragePrefs(context: Context) =
        h.Hchat.preferences.HchatStorage.preferences(context, DisableHotUpdateSettings.PREFS_NAME)
}
