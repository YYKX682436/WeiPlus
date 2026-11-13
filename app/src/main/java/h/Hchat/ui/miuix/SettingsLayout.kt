package h.Hchat.ui.miuix

/** Presentation order only. Provider and hook registration remain independent of this list. */
internal data class SettingsGroupSpec(
    val id: String,
    val title: String,
    val providerIds: List<String>,
    val directProviderId: String? = null,
    val batchOperations: Boolean = false
)

internal object SettingsLayout {
    val groups = listOf(
        SettingsGroupSpec("theme", "主题", listOf(
            "message_bubble", "message_text_color", "home_text_color", "chat_time_style",
            "round_avatar", "input_hint", "floating_bottom_bar", "custom_bottom_bar", "hide_chat_avatar"
        )),
        SettingsGroupSpec("forward", "转发", listOf(
            "message_forward", "remove_forward_limit", "auto_message_forward", "selected_messages"
        )),
        SettingsGroupSpec("scheduled", "定时任务", listOf("scheduled_task"), "scheduled_task"),
        SettingsGroupSpec("auto_reply", "自动回复", listOf("auto_reply", "qq_music_order", "link_parse"), "auto_reply"),
        SettingsGroupSpec("contacts", "通讯录", listOf("zombie_check", "custom_friend_avatar")),
        SettingsGroupSpec("moments", "朋友圈", listOf(
            "moments_auto_like", "moments_auto_comment", "moments_auto_forward", "moments_auto_refresh",
            "moments_contact_filter", "moments_keyword_block", "moments_bottom_detail", "moments_post_notification",
            "original_moments_upload", "moments_upload_tail", "sns_anti_recall", "moments_fake_like",
            "moments_fake_comment", "moments_fake_forward", "remove_moments_ads"
        )),
        SettingsGroupSpec("payments", "红包转账", listOf(
            "auto_redpacket", "auto_gift", "auto_transfer", "fake_wallet_balance", "hchat_red_packet_details"
        )),
        SettingsGroupSpec("groups", "群组", listOf(
            "group_chat_label", "group_leave_monitor", "group_rename_monitor", "real_name_tail",
            "group_nickname_color", "member_title", "hchat_group_member_history"
        )),
        SettingsGroupSpec("chat", "聊天", listOf(
            "anti_recall", "multi_recall", "message_affix", "block_typing_report", "disable_pat",
            "auto_original_image", "auto_view_original", "quick_mark_read", "hchat_message_time",
            "quote_delete_clear", "swipe_quote", "emoji_save", "call_media_limit", "call_ringtone_block",
            "fake_voice_duration", "voice_forward", "edit_message",
            "conversation_group", "hide_chat_menu"
        )),
        SettingsGroupSpec("message_block", "屏蔽消息", listOf("message_block"), "message_block"),
        SettingsGroupSpec("batch", "批量操作", emptyList(), batchOperations = true),
        SettingsGroupSpec("quick", "快捷操作", listOf(
            "quick_moments", "quick_terminate", "quick_group_chat_label", "quick_contact_edit"
        )),
        SettingsGroupSpec("notifications", "自定义通知", listOf(
            "custom_notification", "keyword_notification"
        ), "custom_notification"),
        SettingsGroupSpec("other", "其他", listOf(
            "wechat_keep_alive", "wechat_tablet", "disable_hot_update", "fake_mini_program_base_lib",
            "skip_mini_program_video_ads", "skip_global_mini_program_splash_ads", "fake_location",
            "game_emoji_result", "fake_scan_camera", "finder_media_download", "hchat_skip_web_risk",
            "status_text_limit", "sport_step", "emoji_collection_limit", "profile_id"
        ))
    )

    fun compositeGroupFor(providerId: String): String? = when (providerId) {
        "message_forward", "remove_forward_limit" -> "forward"
        "quick_moments", "quick_terminate", "quick_group_chat_label", "quick_contact_edit" -> "quick"
        else -> null
    }
}

internal enum class AutoReplyInitialScene(val route: String) {
    MAIN("main"), AUTO_ACCEPT("autoAccept"), GREET_ACCEPTED("greetAccepted")
}
