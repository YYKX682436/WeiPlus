package h.Hchat.ui.miuix

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.hooks.items.batch.BatchOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutNavigationTest {
    @Test
    fun `batch sections keep every operation exactly once`() {
        val displayed = batchOperationSections.flatMap { it.second }
        assertEquals(BatchOperation.entries.toSet(), displayed.toSet())
        assertEquals(BatchOperation.entries.size, displayed.size)
    }
    private data class Provider(private val id: String, private val label: String = id) : FeatureSettingsProvider {
        override fun featureId() = id
        override fun title() = label
        override fun subtitle() = ""
    }

    @Test
    fun `single switch routes retain provider ids and focus their category`() {
        val ids = listOf("emoji_save", "quote_delete_clear", "disable_pat", "block_typing_report", "auto_original_image", "original_moments_upload", "remove_moments_ads", "wechat_tablet")
        val providers = ids.map { Provider(it) }
        providers.forEach { provider ->
            val target = resolveFeatureTarget(provider, providers) as DetailPage.FeatureGroup
            assertEquals(SettingsLayout.inlineGroupFor(provider.featureId()), target.group.id)
            assertEquals(provider.featureId(), target.focusId)
            assertEquals(1, filterFeatureSearchResults(provider.featureId(), featureGroups(providers), providers).count { it.provider == provider })
        }
    }

    @Test
    fun `legacy inline provider ids resolve to their composite pages`() {
        val providers = listOf("message_forward", "remove_forward_limit", "quick_moments", "quick_terminate", "quick_group_chat_label", "quick_contact_edit", "settings")
            .map { Provider(it) }
        providers.forEach { provider ->
            val target = resolveFeatureTarget(provider, providers)
            when (provider.featureId()) {
                "settings" -> assertEquals(DetailPage.More, target)
                else -> {
                    assertTrue(target is DetailPage.FeatureGroup)
                    target as DetailPage.FeatureGroup
                    assertEquals(SettingsLayout.compositeGroupFor(provider.featureId()), target.group.id)
                    assertEquals(if (provider.featureId() == "message_forward") "forward.menu" else provider.featureId(), target.focusId)
                }
            }
        }
    }

    @Test
    fun `friend aliases open the correct scene while normal auto reply stays at main`() {
        val autoReply = Provider("auto_reply", "自动回复")
        val providers = listOf(autoReply)
        val groups = featureGroups(providers)
        val aliases = mapOf(
            "收到好友申请" to AutoReplyInitialScene.AUTO_ACCEPT,
            "对方加我好友" to AutoReplyInitialScene.AUTO_ACCEPT,
            "好友请求处理" to AutoReplyInitialScene.AUTO_ACCEPT,
            "对方通过验证" to AutoReplyInitialScene.GREET_ACCEPTED,
            "对方通过验证后" to AutoReplyInitialScene.GREET_ACCEPTED,
            "我加对方好友" to AutoReplyInitialScene.GREET_ACCEPTED,
            "通过后欢迎语" to AutoReplyInitialScene.GREET_ACCEPTED
        )
        aliases.forEach { (query, scene) ->
            val result = filterFeatureSearchResults(query, groups, providers).single { it.scene == scene }
            assertEquals(scene, (resolveFeatureTarget(result.provider, providers, scene = result.scene) as DetailPage.Feature).initialScene)
        }
        val normal = filterFeatureSearchResults("自动回复", groups, providers).single { it.provider == autoReply }
        assertEquals(AutoReplyInitialScene.MAIN, (resolveFeatureTarget(normal.provider, providers, scene = normal.scene) as DetailPage.Feature).initialScene)
    }

    @Test
    fun `forward control searches focus the corresponding inline switch`() {
        val provider = Provider("message_forward", "转发")
        val providers = listOf(provider)
        val groups = featureGroups(providers)
        mapOf(
            "转发菜单" to "forward.menu",
            "多选转发到朋友圈" to "forward.multi",
            "转发收藏" to "forward.favorite",
            "朋友圈转发" to "forward.moments"
        ).forEach { (query, focusId) ->
            val result = filterFeatureSearchResults(query, groups, providers).single()
            assertEquals(focusId, (resolveFeatureTarget(result.provider, providers, result.focusId) as DetailPage.FeatureGroup).focusId)
        }
    }

    @Test
    fun `unregistered parents do not hide independent and newly registered features`() {
        val providers = listOf(Provider("qq_music_order"), Provider("keyword_notification"), Provider("future_feature"))
        val groups = featureGroups(providers)
        assertFalse(groups.any { it.id == "auto_reply" || it.id == "notifications" || it.id == "contacts" })
        assertEquals(providers.map { it.featureId() }.toSet(), groups.single { it.id == "other" }.providers.map { it.featureId() }.toSet())
        providers.forEach { provider ->
            assertEquals(1, filterFeatureSearchResults(provider.featureId(), groups, providers).count { it.provider == provider })
            assertEquals(provider, (resolveFeatureTarget(provider, providers) as DetailPage.Feature).provider)
        }
    }
    @Test
    fun `retained features remain searchable and removed AI has no search result`() {
        val providers = listOf(
            Provider("auto_reply", "自动回复"), Provider("conversation_group", "聊天分组"),
            Provider("voice_forward", "语音转发"), Provider("custom_notification", "自定义通知"),
            Provider("keyword_notification", "关键词通知")
        )
        val groups = featureGroups(providers)
        listOf("自动回复", "聊天分组", "语音转发", "自定义通知", "关键词通知").forEach { query ->
            assertTrue(query, filterFeatureSearchResults(query, groups, providers).isNotEmpty())
        }
        assertTrue(filterFeatureSearchResults("小智", groups, providers).isEmpty())
        assertEquals(0, AutoReplyAiRoute.Zhilia.depth())
        assertEquals(1, AutoReplyAiRoute.ZhiliaConfigs.depth())
        assertEquals(1, AutoReplyAiRoute.ZhiliaModels.depth())
    }

}
