# DexKit
-keep class org.luckypray.dexkit.DexKitBridge { *; }

# Miuix Nav/Compose resolves this composition local from library bytecode.
-keep class androidx.lifecycle.viewmodel.compose.** { *; }

# Android Xml API 返回系统 XmlPullParser 实现，接口 ABI 不能重命名或收窄
-keep class org.xmlpull.v1.** { *; }

-keep class h.Hchat.hooks.items.protobuf.ProtobufPacketRuntime$Packet { *; }
-keep class h.Hchat.hooks.api.model.ContactLabelBean { *; }
-keep class h.Hchat.hooks.api.model.WeChatContact { *; }
-keep class h.Hchat.hooks.api.model.WeChatChatroom { *; }
-keep class h.Hchat.hooks.api.model.WeChatMessage { *; }
-keep class h.Hchat.hooks.api.model.WeChatQuoteMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatImageMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatVideoMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatFileMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatTransferMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatPatMsg { *; }
-keep class h.Hchat.hooks.api.model.WeChatSnsMedia { *; }
-keep class h.Hchat.hooks.api.model.WeChatSnsPost { *; }
-keep class h.Hchat.hooks.api.model.WeChatSnsLivePhoto { *; }
-keep class h.Hchat.hooks.api.model.WeChatSnsPrepareResult { *; }
-keep class h.Hchat.hooks.api.core.WeChatApis { *; }
-keep class h.Hchat.hooks.api.runtime.WeChatDatabaseApi { *; }
-keep class h.Hchat.dexkit.DexBridgeHolder { *; }
-keep class h.Hchat.dexkit.DexFinder { *; }
-keep class h.Hchat.utils.KavaReflector { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class me.yun.silk.** { *; }
-keep class me.yun.silk.utils.** { *; }

# Xposed
-keep class de.robv.android.xposed.** { *; }
-keep class h.Hchat.ModuleEntry { *; }
-keep class h.Hchat.crash.NativeCrashBridge { *; }
-keep class h.Hchat.crash.CrashExitInfoApi30 { *; }

# 反射注入 native 库搜索路径用到的加载器
-keep class h.Hchat.loader.utils.NativeLibraryLoader { *; }

# 保留反射访问的微信内部类字段名
-keepclassmembers class * {
    java.lang.String e;
    java.lang.String f;
    java.lang.String h;
    java.lang.String i;
    java.lang.String m;
    java.lang.String d;
}
