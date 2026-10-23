package h.Hchat.hooks.core

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** A content update also rebinds visible rows behind the host's stable-ID wrapper adapter. */
internal object ChatAdapterRefresh {
    fun notifyChanged(adapter: Any): Boolean {
        val methods = generateSequence(adapter.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }.toList()
        val count = methods.firstOrNull {
            isPublicInstance(it) && it.name == "getItemCount" &&
                it.parameterCount == 0 && it.returnType == Int::class.javaPrimitiveType
        }
        val range = methods.firstOrNull {
            isPublicInstance(it) && it.name == "notifyItemRangeChanged" && it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        }
        if (count != null && range != null) {
            val size = runCatching { count.isAccessible = true; count.invoke(adapter) as Int }.getOrNull()
            if (size != null && size >= 0 && invoke(range, adapter, 0, size)) return true
        }
        val named = methods.firstOrNull {
            isPublicInstance(it) && it.name == "notifyDataSetChanged" &&
                it.parameterCount == 0 && it.returnType == Void.TYPE
        }
        return named != null && invoke(named, adapter)
    }

    private fun isPublicInstance(method: Method): Boolean =
        Modifier.isPublic(method.modifiers) && !Modifier.isStatic(method.modifiers)

    private fun invoke(method: Method, adapter: Any, vararg args: Any): Boolean = runCatching {
        method.isAccessible = true
        method.invoke(adapter, *args)
        true
    }.getOrDefault(false)
}
