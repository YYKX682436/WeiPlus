package h.Hchat.hooks.items.hometextcolor

import android.view.View
import android.view.ViewGroup
import com.tencent.mm.storage.HomeTextColorStorageFixture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTextColorInstallCoordinatorTest {
    @Test
    fun `modern 8 0 80 bind retries after an initial class loader miss`() {
        val adapter = ModernConversationAdapterFixture()
        val legacy = LegacyConversationAdapterFixture::class.java.getDeclaredMethod(
            "getView",
            Int::class.javaPrimitiveType,
            View::class.java,
            ViewGroup::class.java
        )
        val methods = adapter.javaClass.declaredMethods.toList() + legacy
        val resolver = { dexCandidateCount: Int, resolved: List<java.lang.reflect.Method>, querySucceeded: Boolean ->
            HomeTextColorModernConversationResolver.resolve(
                dexCandidateCount = dexCandidateCount,
                resolvedMethods = resolved,
                querySucceeded = querySucceeded,
                warmupReady = true,
                legacyConversationInstalled = true,
                isBind = ::isHomeTextColorModernConversationBind,
                holderCandidates = { candidate ->
                    candidate.declaringClass.declaredMethods.filter {
                        isHomeTextColorModernConversationHolder(
                            it,
                            candidate.parameterTypes[1]
                        )
                    }
                }
            )
        }

        val firstAttempt = resolver(0, emptyList(), false)
        assertTrue(firstAttempt is HomeTextColorModernLookup.Retry)
        assertFalse(
            "a legacy hook alone must not complete 8.0.80 while its DEX modern target is unresolved",
            HomeTextColorInstallCoordinator.isComplete(
                legacyConversationInstalled = true,
                modernPath = HomeTextColorModernPathResult.RETRY
            )
        )

        val classLoaderMiss = resolver(methods.size, listOf(legacy), true)
        assertTrue(classLoaderMiss is HomeTextColorModernLookup.Retry)

        val retry = resolver(methods.size, methods, true)
        assertTrue(retry is HomeTextColorModernLookup.Found)
        assertTrue(
            HomeTextColorInstallCoordinator.isComplete(
                legacyConversationInstalled = false,
                modernPath = HomeTextColorModernPathResult.INSTALLED
            )
        )
    }

    @Test
    fun `resolved legacy-only 8 0 76 path does not wait for modern-only bindings`() {
        val legacy = LegacyConversationAdapterFixture::class.java.getDeclaredMethod(
            "getView",
            Int::class.javaPrimitiveType,
            View::class.java,
            ViewGroup::class.java
        )
        val lookup = HomeTextColorModernConversationResolver.resolve(
            dexCandidateCount = 1,
            resolvedMethods = listOf(legacy),
            querySucceeded = true,
            warmupReady = true,
            legacyConversationInstalled = true,
            isBind = ::isHomeTextColorModernConversationBind,
            holderCandidates = { emptyList() }
        )

        assertTrue(lookup === HomeTextColorModernLookup.NotApplicable)
        assertTrue(
            "legacy conversation binding is sufficient when the modern path is structurally absent",
            HomeTextColorInstallCoordinator.isComplete(
                legacyConversationInstalled = true,
                modernPath = HomeTextColorModernPathResult.NOT_APPLICABLE
            )
        )
        assertFalse(
            "neither home conversation binding can complete installation",
            HomeTextColorInstallCoordinator.isComplete(
                legacyConversationInstalled = false,
                modernPath = HomeTextColorModernPathResult.NOT_APPLICABLE
            )
        )
    }

    @Test
    fun `successful empty modern query accepts installed legacy path only after warmup`() {
        val resolved = HomeTextColorModernConversationResolver.resolve(
            dexCandidateCount = 0,
            resolvedMethods = emptyList(),
            querySucceeded = true,
            warmupReady = true,
            legacyConversationInstalled = true,
            isBind = ::isHomeTextColorModernConversationBind,
            holderCandidates = { emptyList() }
        )
        assertTrue(resolved === HomeTextColorModernLookup.NotApplicable)
        assertTrue(HomeTextColorInstallCoordinator.isComplete(true, HomeTextColorModernPathResult.NOT_APPLICABLE))

        val failedQuery = HomeTextColorModernConversationResolver.resolve(
            dexCandidateCount = 0,
            resolvedMethods = emptyList(),
            querySucceeded = false,
            warmupReady = true,
            legacyConversationInstalled = true,
            isBind = ::isHomeTextColorModernConversationBind,
            holderCandidates = { emptyList() }
        )
        assertTrue(failedQuery === HomeTextColorModernLookup.Retry)
    }

    @Test
    fun `optional pages retry independently and stop after destroy`() {
        val coordinator = HomeTextColorPageInstallCoordinator()
        var attempts = 0
        val installer = {
            attempts++
            attempts > 1
        }
        assertTrue(coordinator.install("contacts", installer) === HomeTextColorInstallAttempt.RETRY)
        assertTrue(coordinator.install("contacts", installer) === HomeTextColorInstallAttempt.COMPLETE)
        assertTrue(coordinator.install("contacts", installer) === HomeTextColorInstallAttempt.COMPLETE)
        assertTrue(attempts == 2)
        coordinator.destroy()
        assertTrue(coordinator.install("preferences", installer) === HomeTextColorInstallAttempt.COMPLETE)
        assertTrue(attempts == 2)
    }
}

private class ModernConversationAdapterFixture {
    fun bind(
        position: Int,
        holder: ModernConversationHolderFixture,
        item: ModernConversationItemFixture,
        storage: HomeTextColorStorageFixture
    ) = Unit

    fun initHolder(holder: ModernConversationHolderFixture, row: View) = Unit
}

private class ModernConversationHolderFixture
private class ModernConversationItemFixture

private class LegacyConversationAdapterFixture {
    fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        convertView ?: View(parent.context)
}
