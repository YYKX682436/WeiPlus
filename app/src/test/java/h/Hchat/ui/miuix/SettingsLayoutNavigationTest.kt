package h.Hchat.ui.miuix

import h.Hchat.ui.FeatureSettingsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutNavigationTest {
    private data class Provider(private val id: String, private val label: String = id) : FeatureSettingsProvider {
        override fun featureId() = id
        override fun title() = label
        override fun subtitle() = ""
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
        val providers = listOf(Provider("qq_music_order"), Provider("text_speech"), Provider("future_feature"))
        val groups = featureGroups(providers)
        assertFalse(groups.any { it.id == "auto_reply" || it.id == "notifications" || it.id == "contacts" })
        assertEquals(providers.map { it.featureId() }.toSet(), groups.single { it.id == "other" }.providers.map { it.featureId() }.toSet())
        providers.forEach { provider ->
            assertEquals(1, filterFeatureSearchResults(provider.featureId(), groups, providers).count { it.provider == provider })
            assertEquals(provider, (resolveFeatureTarget(provider, providers) as DetailPage.Feature).provider)
        }
    }
}
