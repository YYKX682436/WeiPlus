import android.content.ContentValues
import android.content.Context
import h.Hchat.WechatPackage
import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.model.DatabaseChange
import h.Hchat.hooks.api.runtime.WeChatDatabaseListenerApi
import h.Hchat.hooks.core.FeatureInstallDecision
import h.Hchat.hooks.items.autoreply.AutoReplyFriendRequestHook
import h.Hchat.hooks.items.sendbutton.NativeSendButtonHandlers
import h.Hchat.preferences.HchatStorage
import java.io.File

fun main() {
    check(WechatPackage.isTarget("com.tencent.mm"))
    check(WechatPackage.isTarget("com.tencent.mm.wx"))
    check(!WechatPackage.isTarget("com.tencent.mm.fake"))
    check(!WechatPackage.isTarget(null))
    check(WechatPackage.isMainProcess("com.tencent.mm.wx", "com.tencent.mm.wx"))
    check(!WechatPackage.isMainProcess("com.tencent.mm.wx", "com.tencent.mm.wx:appbrand0"))
    check(!WechatPackage.isMainProcess("com.tencent.mm.fake", null))
    check(WechatPackage.isAppBrandProcess("com.tencent.mm", "com.tencent.mm:appbrand0"))
    check(WechatPackage.isAppBrandProcess("com.tencent.mm.wx", "com.tencent.mm.wx:appbrand2"))
    check(!WechatPackage.isAppBrandProcess("com.tencent.mm.fake", "com.tencent.mm.fake:appbrand0"))

    var enabledReads = 0
    check(FeatureInstallDecision.shouldInstall(false, { enabledReads++; false }, {}))
    check(enabledReads == 0) { "Ordinary preinstalled features must not be skipped by the enable check" }
    check(!FeatureInstallDecision.shouldInstall(true, { false }, {}))
    check(FeatureInstallDecision.shouldInstall(true, { true }, {}))
    var errors = 0
    check(!FeatureInstallDecision.shouldInstall(true, { error("prefs unavailable") }, { errors++ }))
    check(errors == 1) { "Enabled-only features must fail closed when config reads fail" }

    val handlerErrors = mutableListOf<String>()
    val handlers = NativeSendButtonHandlers { id, _ -> handlerErrors += id }
    var staleCalls = 0
    handlers.register("voice", { staleCalls++; false })
    var nativeCalls = 0
    handlers.register("voice", { text -> text == "hello" })
    handlers.register("affix", { nativeCalls++; false })
    check(handlers.dispatch("hello"))
    check(staleCalls == 0 && nativeCalls == 0) { "Replacement and intercept ordering are incorrect" }
    handlers.register("broken", { error("handler failed") })
    handlers.register("pass", { false })
    check(!handlers.dispatch("ordinary"))
    check(handlerErrors == listOf("broken"))
    handlers.clear()
    check(!handlers.dispatch("hello"))

    val root = File(System.getProperty("java.io.tmpdir"), "hchat-native-test-${System.nanoTime()}")
    val officialContext = Context("com.tencent.mm", File(root, "official"))
    val modifiedContext = Context("com.tencent.mm.wx", File(root, "modified"))
    val officialPrefs = HchatStorage.preferences(officialContext, "same-name")
    check(officialPrefs === HchatStorage.preferences(officialContext, "same-name"))
    val modifiedPrefs = HchatStorage.preferences(modifiedContext, "same-name")
    check(officialPrefs !== modifiedPrefs) { "Host package configurations shared one cache entry" }
    check(HchatStorage.storageDir(officialContext) != HchatStorage.storageDir(modifiedContext))

    val db = WeChatDatabaseListenerApi()
    WeChatApis.databaseChangesApi = db
    AutoReplyFriendRequestHook.uninstall()
    val requests = mutableListOf<AutoReplyFriendRequestHook.NewFriendEvent>()
    val subscription = AutoReplyFriendRequestHook.subscribe(requests::add)
    check(AutoReplyFriendRequestHook.install())
    check(db.installCalls == 1)
    val body = "<msg><encryptusername>v3_alice@stranger</encryptusername><fromusername>wxid_alice</fromusername><ticket>ticket-a</ticket><scene>3</scene></msg>"
    AutoReplyFriendRequestHook.handleMessage(Events.MessageReceived(false, content = body, msgType = "37"))
    AutoReplyFriendRequestHook.handleMessage(Events.MessageReceived(false, content = body, msgType = "37"))
    check(requests.size == 1) { "Duplicate friend request notifications were dispatched" }
    check(requests.single().wxid == "wxid_alice")
    check(requests.single().verifyUsername == "v3_alice@stranger")
    check(AutoReplyFriendRequestHook.resolveVerifyUsername("wxid_alice", "ticket-a", 3) == "v3_alice@stranger")
    AutoReplyFriendRequestHook.handleMessage(Events.MessageReceived(false, content = body, msgType = "42"))
    AutoReplyFriendRequestHook.handleMessage(Events.MessageReceived(true, content = body, msgType = "37"))
    check(requests.size == 1) { "Contact cards or outgoing messages must not trigger friend request handling" }
    db.emit(DatabaseChange("fmessage_msginfo", ContentValues(mapOf(
        "isSend" to 0,
        "msgContent" to "<msg><fromusername>wxid_bob</fromusername><ticket>ticket-b</ticket><scene>1</scene></msg>"
    ))))
    check(requests.size == 2 && requests.last().wxid == "wxid_bob")
    subscription.unsubscribe()
    AutoReplyFriendRequestHook.handleMessage(Events.MessageReceived(false,
        content = "<msg><fromusername>wxid_c</fromusername><ticket>ticket-c</ticket><scene>2</scene></msg>", msgType = "37"))
    check(requests.size == 2) { "Unsubscribed native listener received a friend request" }
    AutoReplyFriendRequestHook.uninstall()

    println("PASS: exact dual-package/process rules, isolated host config, ordinary/early toggle decisions, native send dispatch, friend request parse/dedupe/verification")
}
