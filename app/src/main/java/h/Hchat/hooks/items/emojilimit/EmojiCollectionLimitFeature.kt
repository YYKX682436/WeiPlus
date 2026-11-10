package h.Hchat.hooks.items.emojilimit

import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider
import h.Hchat.utils.HLog
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Modifier

class EmojiCollectionLimitFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "扩展表情收藏上限"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(object : SimpleFeatureSettingsProvider(
            ID,
            name(),
            "将本机收藏上限提高到 5000",
            FeatureSettingsProvider.CATEGORY_PRACTICAL
        ) {})
    }

    override fun isEnabled(context: FeatureContext): Boolean =
        context.configStore().getBoolean(ID, KEY_ENABLED, DEFAULT_ENABLED)

    override fun onFeatureInstall(context: FeatureContext) {
        val version = WeChatApis.version()?.current() ?: return
        if (version.packageName != "com.tencent.mm" || version.versionName != "8.0.80" ||
            version.versionCode != 3220L
        ) return

        // classes12.dex: the storage full check, collection manager and album picker share this getter.
        val method = runCatching {
            val target = context.dexKitBridge().findMethod(
                FindMethod().matcher(
                    MethodMatcher().declaredClass("tr.a0").name("a").paramCount(0)
                        .returnType("int").usingEqStrings("CustomEmojiMaxSize")
                )
            ).singleOrNull()
            target?.getMethodInstance(context.hostClassLoader())?.takeIf {
                it.declaringClass.name == "tr.a0" && it.name == "a" && it.parameterCount == 0 &&
                    it.returnType == Int::class.javaPrimitiveType && Modifier.isPublic(it.modifiers) &&
                    Modifier.isStatic(it.modifiers)
            }
        }.getOrElse {
            logError("无法核对收藏上限方法，跳过 Hook", it)
            return
        } ?: run {
            logError("未匹配 tr.a0.a()I 和 CustomEmojiMaxSize，跳过 Hook", null)
            return
        }

        HookRegistry.get().hook(method, XposedInterface.Hooker { chain ->
            if (isEnabled(context)) EXPANDED_LIMIT else chain.proceed()
        })
        HLog.i("[Hchat:$ID] 已安装 tr.a0.a()I Hook")
    }

    companion object {
        const val ID = "emoji_collection_limit"
        const val KEY_ENABLED = "enabled"
        const val DEFAULT_ENABLED = false
        private const val EXPANDED_LIMIT = 5000
    }
}
