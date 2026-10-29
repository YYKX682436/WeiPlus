package h.Hchat

/** Exact install package names supported by the module. Internal WeChat class names remain com.tencent.mm.*. */
object WechatPackage {
    const val OFFICIAL = "com.tencent.mm"
    const val MODIFIED = "com.tencent.mm.wx"

    @JvmStatic
    fun isTarget(packageName: String?): Boolean = packageName == OFFICIAL || packageName == MODIFIED

    /** The entry filter uses only the exact package name and its own process namespace. */
    @JvmStatic
    fun isModuleEntryScope(packageName: String?, processName: String?): Boolean =
        isTarget(packageName) && processName != null &&
            (processName == packageName || processName.startsWith("$packageName:"))

    @JvmStatic
    fun isMainProcess(packageName: String?, processName: String?): Boolean =
        isTarget(packageName) && (processName == null || processName == packageName)

    @JvmStatic
    fun isAppBrandProcess(packageName: String?, processName: String?): Boolean =
        isTarget(packageName) && processName != null && processName.startsWith("$packageName:appbrand")
}
