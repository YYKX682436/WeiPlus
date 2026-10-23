package h.Hchat.preferences

import android.content.Context
import h.Hchat.utils.HLog

object TermsGate {
    const val AGREEMENT_TEXT = "我同意"
    const val TERMS_VERSION = 1
    val AGREEMENT_CONTENT = """
        Hchat 使用协议与免责声明

        1. 本模块为免费模块，仅供个人学习、研究、测试、逆向分析和备份用途使用，不提供任何商业授权、售后承诺或稳定性保证。
        2. 本模块与微信、腾讯及其关联主体无关，不代表官方立场，也不是官方客户端、官方插件或官方服务的一部分。
        3. 禁止倒卖、付费分发、捆绑销售、二次打包收费、引流售卖、以捐赠名义收费，禁止冒充作者、官方渠道或授权代理发布。
        4. 请勿在国内公开平台、群组、论坛、短视频平台、网盘分享页、应用市场或其它公开渠道传播、推广、引流、售卖或组织分发本模块。
        5. 本模块可能会修改微信运行时行为，使用后可能出现功能异常、消息异常、账号风控、限制登录、数据异常、闪退、掉线、模块冲突或其它不可预期问题。
        6. 使用者应自行确认所在地法律法规、平台协议、设备环境和账号风险；因安装、使用、传播、修改、二次分发或与其它模块共存产生的任何后果均由使用者自行承担。
        7. 禁止将本模块用于骚扰、欺诈、刷量、营销轰炸、盗取信息、破坏服务稳定性、绕过平台风控、侵犯他人权益或其它违法违规用途。
        8. 本模块不保证适配所有微信版本、系统版本、设备环境、热更新状态和其它模块共存环境，也不承诺持续维护、及时修复或提供任何形式的服务保障。
        9. 如果你不同意以上任一条款，请点击取消并停止使用本模块。

        如果你理解并接受以上内容，请在下方输入“我同意”后继续使用。
    """.trimIndent()

    private const val KEY_ACCEPTED = "terms_accepted"
    private const val KEY_VERSION = "terms_version"

    fun isAccepted(context: Context): Boolean {
        val store = ConfigStore(context)
        return store.getGlobalBoolean(KEY_ACCEPTED, false) &&
            store.getGlobalInt(KEY_VERSION, 0) == TERMS_VERSION
    }

    fun isAccepted(store: ConfigStore): Boolean {
        return store.getGlobalBoolean(KEY_ACCEPTED, false) &&
            store.getGlobalInt(KEY_VERSION, 0) == TERMS_VERSION
    }

    fun accept(context: Context): Boolean {
        val store = ConfigStore(context)
        val prefs = store.reopenGlobalPrefsIfFilesMissing()
        return try {
            val committed = prefs.edit()
                .putBoolean(KEY_ACCEPTED, true)
                .putInt(KEY_VERSION, TERMS_VERSION)
                .commit()
            committed && prefs.getBoolean(KEY_ACCEPTED, false) &&
                prefs.getInt(KEY_VERSION, 0) == TERMS_VERSION
        } catch (e: Throwable) {
            HLog.e("[Hchat:TermsGate] 保存协议状态失败: ${e.message}", e)
            false
        }
    }
}
