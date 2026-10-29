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
        val resolver = { dexCandidateCount: Int, resolved: List<java.lang.reflect.Method> ->
            HomeTextColorModernConversationResolver.resolve(
                dexCandidateCount = dexCandidateCount,
                resolvedMethods = resolved,
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

        val firstAttempt = resolver(0, emptyList())
        assertTrue(firstAttempt is HomeTextColorModernLookup.Retry)
        assertFalse(
            "a legacy hook alone must not complete 8.0.80 while its DEX modern target is unresolved",
            HomeTextColorInstallCoordinator.isComplete(
                legacyConversationInstalled = true,
                modernPath = HomeTextColorModernPathResult.RETRY
            )
        )

        val classLoaderMiss = resolver(methods.size, listOf(legacy))
        assertTrue(classLoaderMiss is HomeTextColorModernLookup.Retry)

        val retry = resolver(methods.size, methods)
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
