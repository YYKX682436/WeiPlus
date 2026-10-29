package h.Hchat.hooks.items.hometextcolor

import android.view.View
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal enum class HomeTextColorModernPathResult {
    INSTALLED,
    NOT_APPLICABLE,
    RETRY
}

internal sealed class HomeTextColorModernLookup {
    data class Found(val holder: Method, val bind: Method) : HomeTextColorModernLookup()
    object NotApplicable : HomeTextColorModernLookup()
    object Retry : HomeTextColorModernLookup()
}

/** Shared production decision for the home binding paths; optional page hooks do not gate it. */
internal object HomeTextColorInstallCoordinator {
    fun isComplete(
        legacyConversationInstalled: Boolean,
        modernPath: HomeTextColorModernPathResult
    ): Boolean = when (modernPath) {
        HomeTextColorModernPathResult.INSTALLED -> true
        HomeTextColorModernPathResult.NOT_APPLICABLE -> legacyConversationInstalled
        HomeTextColorModernPathResult.RETRY -> false
    }
}

/** Resolves the modern holder/bind pair while preserving retries for transient class-loader misses. */
internal object HomeTextColorModernConversationResolver {
    fun resolve(
        dexCandidateCount: Int,
        resolvedMethods: List<Method>,
        isBind: (Method) -> Boolean,
        holderCandidates: (Method) -> List<Method>
    ): HomeTextColorModernLookup {
        val binds = resolvedMethods.asSequence()
            .filter(isBind)
            .distinctBy(Method::toGenericString)
            .toList()
        if (binds.size > 1) return HomeTextColorModernLookup.Retry
        if (binds.size == 1) {
            val bind = binds.single()
            val holders = holderCandidates(bind).distinctBy(Method::toGenericString)
            val holder = holders.singleOrNull() ?: return HomeTextColorModernLookup.Retry
            return HomeTextColorModernLookup.Found(holder, bind)
        }

        // Empty DEX results and candidates that the host loader could not materialize are both
        // retryable: either can be transient during startup. A fully resolved search with no
        // matching bind is the structurally absent older-layout case.
        return if (dexCandidateCount == 0 || resolvedMethods.size < dexCandidateCount) {
            HomeTextColorModernLookup.Retry
        } else {
            HomeTextColorModernLookup.NotApplicable
        }
    }
}

internal fun isHomeTextColorModernConversationBind(method: Method): Boolean {
    val types = method.parameterTypes
    val ownerPackage = method.declaringClass.name.substringBeforeLast('.', "")
    return !Modifier.isStatic(method.modifiers) &&
        !Modifier.isAbstract(method.modifiers) &&
        method.returnType == Void.TYPE &&
        types.size == 4 &&
        types[0] == Int::class.javaPrimitiveType &&
        types[1].name.substringBeforeLast('.', "") == ownerPackage &&
        types[2].name.substringBeforeLast('.', "") == ownerPackage &&
        types[3].name.startsWith("com.tencent.mm.storage.")
}

internal fun isHomeTextColorModernConversationHolder(method: Method, holderClass: Class<*>): Boolean {
    val types = method.parameterTypes
    return !Modifier.isStatic(method.modifiers) &&
        !Modifier.isAbstract(method.modifiers) &&
        method.returnType == Void.TYPE &&
        types.contentEquals(arrayOf(holderClass, View::class.java))
}
