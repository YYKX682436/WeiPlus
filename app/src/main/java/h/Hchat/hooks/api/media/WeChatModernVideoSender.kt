package h.Hchat.hooks.api.media

import h.Hchat.dexkit.DexBridgeHolder
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Newer WeChat video pipeline. Discovery is fail-closed and must run off the UI thread. */
internal class WeChatModernVideoSender(
    private val bridge: DexBridgeHolder,
    private val legacy: WeChatVideoApi
) {
    private var bindings: Bindings? = null

    @Synchronized
    fun prepare(): Boolean {
        if (bindings != null) return true
        bindings = runCatching { discover() }.getOrNull()
        return bindings != null
    }

    /** True indicates enqueue only, not delivery. Keep the source until the host finishes copying it. */
    fun send(talker: String, path: String): Boolean {
        if (talker.isBlank() || !File(path).isFile || !prepare()) return false
        val api = bindings ?: return false
        return runCatching {
            val thumb = legacy.generateThumb(path, File(path).parentFile)
            if (thumb.isNullOrBlank() || !File(thumb).isFile) return false
            val filename = api.filename.invoke(null, talker) as? String
            if (filename.isNullOrBlank()) return false
            val element = api.element.newInstance(filename, path, thumb, false, 0, null, null, 0x60, null)
            val option = api.option.newInstance(api.gallery)
            val params = api.params.newInstance(talker, element, false, option)
            val task = api.task.newInstance(params)
            api.submit.invoke(api.owner, task)
            true
        }.getOrDefault(false)
    }

    private fun discover(): Bindings? {
        val loader = bridge.hostClassLoader
        fun load(name: String) = runCatching { loader.loadClass(name) }.getOrNull()
        val paramsClasses = bridge.findClassesByStrings("VideoSendParams(toUser='")
            .mapNotNull(::load).distinct().mapNotNull { cls ->
                cls.declaredConstructors.singleOrNull { c ->
                    val p = c.parameterTypes
                    p.size == 4 && p[0] == String::class.java && p[2] == Boolean::class.javaPrimitiveType
                }?.let { cls to it }
            }
        if (paramsClasses.size != 1) return null
        val (paramsClass, params) = paramsClasses.single()
        val elementClass = params.parameterTypes[1]
        val optionClass = params.parameterTypes[3]
        val element = elementClass.declaredConstructors.singleOrNull { c ->
            val p = c.parameterTypes
            p.size == 9 && p[0] == String::class.java && p[1] == String::class.java &&
                p[2] == String::class.java && p[3] == Boolean::class.javaPrimitiveType &&
                p[4] == Int::class.javaPrimitiveType && p[7] == Int::class.javaPrimitiveType
        } ?: return null
        val option = optionClass.declaredConstructors.singleOrNull { it.parameterCount == 1 && it.parameterTypes[0].isEnum } ?: return null
        val gallery = option.parameterTypes[0].enumConstants?.singleOrNull { (it as? Enum<*>)?.name == "FromGallery" } ?: return null
        val taskClasses = bridge.findClassesByStrings("MicroMsg.VideoMsg.SendVideoTask", "copy video file failed.target file path")
            .mapNotNull(::load).distinct().mapNotNull { cls ->
                cls.declaredConstructors.singleOrNull { it.parameterTypes.contentEquals(arrayOf(paramsClass)) }?.let { cls to it }
            }
        if (taskClasses.size != 1) return null
        val (taskClass, task) = taskClasses.single()
        val submits = bridge.findMethodsByStrings("MicroMsg.video.VideoSendFeatureServicesendAsync only support SendVideoTask Type")
            .flatMap { info ->
                load(info.className)?.declaredMethods?.filter { m ->
                    m.name == info.methodName && m.parameterCount == 1 && m.parameterTypes[0].isAssignableFrom(taskClass) && !Modifier.isStatic(m.modifiers)
                }.orEmpty()
            }.distinct()
        if (submits.size != 1) return null
        val submit = submits.single()
        val owners = submit.declaringClass.declaredFields.filter { Modifier.isStatic(it.modifiers) && it.type == submit.declaringClass }
        if (owners.size != 1) return null
        val owner = owners.single().apply { isAccessible = true }.get(null) ?: return null
        val filenames = bridge.findMethodsByStrings("yyMMddHHmmss", "@")
            .flatMap { info ->
                load(info.className)?.declaredMethods?.filter { m ->
                    m.name == info.methodName && Modifier.isStatic(m.modifiers) && m.returnType == String::class.java &&
                        m.parameterTypes.contentEquals(arrayOf(String::class.java))
                }.orEmpty()
            }.distinct()
        if (filenames.size != 1) return null
        return Bindings(params, element, option, gallery, task, submit, owner, filenames.single()).also {
            listOf(it.params, it.element, it.option, it.task).forEach { c -> c.isAccessible = true }
            it.submit.isAccessible = true
            it.filename.isAccessible = true
        }
    }

    private data class Bindings(
        val params: Constructor<*>, val element: Constructor<*>, val option: Constructor<*>, val gallery: Any,
        val task: Constructor<*>, val submit: Method, val owner: Any, val filename: Method
    )
}
