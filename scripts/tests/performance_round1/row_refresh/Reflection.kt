package h.Hchat.utils
object KavaReflector {
    var threadReads = 0
    fun loadClass(name: String, loader: ClassLoader?): Class<*> = Class.forName(name)
    fun invokeStaticMethod(type: Class<*>, name: String): Any? {
        threadReads++; return type.getMethod(name).invoke(null)
    }
    fun readField(receiver: Any?, name: String): Any? = receiver?.javaClass?.getDeclaredField(name)?.let {
        it.isAccessible = true; it.get(receiver)
    }
    fun invokeMethod(receiver: Any, name: String): Any? = receiver.javaClass.getMethod(name).let {
        it.isAccessible = true; it.invoke(receiver)
    }
}
