# 设置入口与页面去向

本文记录 `SettingsLayout` 和 `FeatureSettingsPage` 的当前路由；Provider、Hook 和配置标识不随页面合并而变化。首页按下表顺序显示，实际注册情况决定某个入口是否出现。搜索和旧功能入口通过原 Provider ID 解析；内嵌项定位到分类页对应开关。未列入已知分类的已注册 Provider 仍归入“其他”。

| 首页入口 | 最终去向 |
| --- | --- |
| 主题 | 气泡 `message_bubble`、消息颜色 `message_text_color`、首页颜色 `home_text_color`、时间样式 `chat_time_style`、圆形头像 `round_avatar`、输入提示 `input_hint`、悬浮底栏 `floating_bottom_bar`、自定义底栏 `custom_bottom_bar`、隐藏头像 `hide_chat_avatar` 保留各自编辑页。 |
| 转发 | `message_forward` 的聊天、收藏、朋友圈开关和 `remove_forward_limit` 内嵌分类页；自动转发 `auto_message_forward`、群发 `selected_messages` 保留编辑页。自动转发主页面合并总开关与规则列表，规则类型原地展开。 |
| 定时任务 | `scheduled_task` 直接进入总开关与任务列表；任务编辑页包含对象、内容序列、执行计划和节奏，联系人、收藏及文件选择仍独立。 |
| 自动回复 | `auto_reply` 直接进入总开关与规则列表，规则编辑同页完成条件、范围和回复序列；全局排除折叠，AI 配置独立。点歌 `qq_music_order`、链接解析 `link_parse` 保留相关功能入口与独立配置页。 |
| 通讯录 | 收到好友申请、对方通过验证直接进入相应场景编辑，回复序列行内编辑，标签与备注折叠；僵尸粉检测 `zombie_check`、好友头像 `custom_friend_avatar` 及名单、标签选择仍独立。 |
| 朋友圈 | 原图上传 `original_moments_upload`、去广告 `remove_moments_ads` 内嵌；自动点赞 `moments_auto_like`、评论 `moments_auto_comment`、转发 `moments_auto_forward`、刷新 `moments_auto_refresh`、联系人过滤 `moments_contact_filter`、关键词过滤 `moments_keyword_block`、底部详情 `moments_bottom_detail`、发布通知 `moments_post_notification`、上传尾巴 `moments_upload_tail`、防撤回 `sns_anti_recall`、模拟点赞／评论／转发 `moments_fake_like` / `moments_fake_comment` / `moments_fake_forward` 保留各自编辑或管理页。 |
| 红包转账 | 红包详情 `hchat_red_packet_details` 内嵌；自动红包 `auto_redpacket`、礼物 `auto_gift`、转账 `auto_transfer`、钱包余额 `fake_wallet_balance` 保留独立业务页。红包与转账的过滤、延迟和后处理折叠；模板库、适用聊天与回复序列编辑独立，回复项在序列页原地展开。 |
| 群组 | 群成员历史 `hchat_group_member_history` 内嵌；群标签 `group_chat_label`、进退群监控 `group_leave_monitor`、改名监控 `group_rename_monitor`、实名尾巴 `real_name_tail`、昵称颜色 `group_nickname_color`、头衔 `member_title` 保留编辑页。进退群媒体按原有类型顺序和共享素材编辑；群、成员及模板管理保留独立。 |
| 聊天 | `multi_recall`、`block_typing_report`、`disable_pat`、`auto_original_image`、`auto_view_original`、`quote_delete_clear`、`emoji_save` 原地开关；防撤回 `anti_recall`、前后缀 `message_affix`、快捷已读 `quick_mark_read`、消息时间 `hchat_message_time`、滑动引用 `swipe_quote`、通话媒体 `call_media_limit`、通话铃声 `call_ringtone_block`、伪造语音 `fake_voice_duration`、语音转发 `voice_forward`、编辑消息 `edit_message`、会话分组 `conversation_group`、聊天菜单 `hide_chat_menu` 保留独立编辑或管理页。 |
| 屏蔽消息 | `message_block` 直接进入主页面；按私聊、群聊、公众号区分默认规则，模板与名单保留独立管理，模板类型和关键词在模板页编辑。 |
| 批量操作 | 主页面按对象和任务分组，保留状态与停止入口；各操作的筛选、选择、参数及确认仍在原有任务页。批量朋友圈保留独立任务页。 |
| 快捷操作 | `quick_moments`、`quick_terminate`、`quick_group_chat_label`、`quick_contact_edit` 开关内嵌分类页。 |
| 自定义通知 | `custom_notification` 直接进入主页面；默认规则、会话规则及 `keyword_notification` 各有独立编辑或管理入口，规则按提醒方式、通知内容、静默与过滤分区。 |
| 其他 | `wechat_tablet`、`disable_hot_update`、`fake_mini_program_base_lib`、`skip_mini_program_video_ads`、`skip_global_mini_program_splash_ads`、`finder_media_download`、`hchat_skip_web_risk`、`emoji_collection_limit`、`profile_id` 原地开关；保活 `wechat_keep_alive`、定位 `fake_location`、猜拳骰子 `game_emoji_result`、相机 `fake_scan_camera`、状态文字 `status_text_limit`、步数 `sport_step` 保留独立编辑页。 |

首页更多菜单仍提供配置导入、导出和设置入口控制。选择联系人、群成员、收藏、媒体、标签以及可复用的模板和名单是独立的管理／选择页面；不因返回预览创建业务条目。实际页面与条目以 `SettingsLayout.kt`、`MiuixSettingsNavigation.kt` 及各业务设置文件为准。
