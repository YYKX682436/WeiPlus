# 微信 8.0.80 兼容性阶段 A 审计

审计日期：2026-10-28
仓库：YYKX682436/WeiPlus，分支 main
审计基线：53ed89e8e733c706e0f7819f1fbca0f74bf999c0
目标：微信 8.0.80（3220），最低支持线 8.0.76
范围：DexSQL 接入、源码全局扫描、8.0.80 DEX 结构查询、旧版条件审计。此报告只记录证据和建议，不改生产 Hook、不删旧代码。

## 结论摘要

- 已确认 8.0.80 正式 APK 的本地副本与真机安装包 SHA-256 相同：**c35a05d5c13bd43cf1f7ff42c13fab070d21509ab2e233596f776d1afcae52f5**。包名 **com.tencent.mm**，versionName **8.0.80**，versionCode **3220**。
- DexSQL v0.1.1 已安装为 Apple Silicon ARM64 程序，校验官方发布文件 SHA-256，Codex 全局 MCP 已完成 initialize 与 tools/list 握手；本报告的数据来自对 APK DEX 的实际结构查询和 smali 解码，不是字符串搜索摘要。
- 已发现三项明确的 8.0.80 base DEX 定位失配：AddMsg 定位的两个字符串均无引用；文本发送构造器从 5 个参数变成 6 个参数，当前精确构造器规则无法命中；朋友圈视频上传 helper 从 4 个 String 参数变成 5 个，当前签名过滤无法命中。它们分别会影响消息事件链、模块文本发送 API、朋友圈视频上传相关路径。
- 收红包/拆红包、转账请求、联系人标签、群成员请求、朋友圈互动、聊天时间/气泡、头像、右上角加号菜单等均在 DEX 中找到具有相符描述符的候选，但没有在 8.0.80 进程中实际安装并触发 Hook；统一标为“静态候选，运行时未验证”。
- 本机未发现可确认身份的官方 8.0.76 APK。比较对象是手机上安装的 com.tencent.mm.wx 8.0.76（3140）修改版，且其 DEX 中有 B$codexTabletOriginal 等改写痕迹，只作为参考，不作为官方旧版基线。
- 真机 8.0.80 进程当时未运行；模块包 h.Hchat 已安装，但 LSPosed 对该进程的启用/加载状态无法从只读检查确认。本阶段没有启动微信、没有执行支付或消息发送，也没有卸载微信或清除数据。

## 1. 版本基线和 APK 身份

| 项目 | 审计结果 |
|---|---|
| Git 仓库 | /Users/muchen/Documents/ChatGPT/WeiPlus，main，初始工作区干净；HEAD 为 53ed89e8e733c706e0f7819f1fbca0f74bf999c0，与 origin/main 对齐 |
| 目标正式 APK | 本机已有 /Users/muchen/Downloads/weixin8080正式版.apk；SHA-256 c35a05d5c13bd43cf1f7ff42c13fab070d21509ab2e233596f776d1afcae52f5 |
| 真机正式包 | com.tencent.mm，versionName 8.0.80，versionCode 3220；只读提取 base.apk 后 SHA-256 与上述正式 APK 完全相同，含 18 个 DEX |
| 8.0.76 比较样本 | 真机 com.tencent.mm.wx，versionName 8.0.76，versionCode 3140；base.apk SHA-256 19b71802548d9a30d87c344fc0457270b1e7f2ceefdcfad4f15386fcab62c6b4，含 17 个 DEX。样本为重打包/修改版，不是已核验的官方原版 |
| 支持身份 | 项目仍以 com.tencent.mm 与 com.tencent.mm.wx 为受支持包名；libxposed API 102；最低版本需求按 8.0.76 执行 |
| 设备运行态 | 设备序列号 feb70d35，型号 22081212C；读取时微信 8.0.80 进程未运行。h.Hchat 包存在，模块是否在 LSPosed 中启用及是否成功加载未验证 |
| 8.0.76 官方基线 | 未取得。下文 .76 对比均显式指向已安装修改版样本，不能据此确认官方 8.0.76 的全部结构差异 |

包身份依据 Package Manager 返回的包名、versionName、versionCode，与 APK 文件 SHA-256 和 DEX 清单交叉核对。没有把文件名或上传路径作为身份依据。没有 aapt 可用；APK 身份核验不依赖 aapt 输出。

## 2. DexSQL 安装及实际分析证据

### 安装与 MCP

- 官方项目：[LSPosed/dexsql-release](https://github.com/LSPosed/dexsql-release)；审计时最新 Release 为 [v0.1.1](https://github.com/LSPosed/dexsql-release/releases/tag/v0.1.1)，未发现更新 Release。
- 官方 install-dexsql Skill 已安装到用户级 Codex Skill 目录，并按其 Apple Silicon 流程安装兼容 arm64 版本。
- 可执行文件：/Users/muchen/.local/share/dexsql/v0.1.1/dexsql；dexsql --version 返回 dexsql 0.1.1；文件类型为 arm64 Mach-O。
- 官方发布资产 18,928,192 bytes；SHA-256 为 d983de33007e11c56e681aaa238ae15637eff961623335f1131527f8d2c82a77，与安装文件一致。随附 LICENSE SHA-256：a5e8ba355d3bbc08a1de333b93cfaadff62c1b335454930c3bc8163d710b5a30；LICENSE-AUXILIARY SHA-256：91c80095437ed20c2dca7758f63f03e15eedb46aa4854577b5df20c788ecc514。
- 已通过 codex mcp add dexsql -- /Users/muchen/.local/share/dexsql/v0.1.1/dexsql mcp 接入用户级 Codex MCP；codex mcp get dexsql --json 显示 stdio server enabled、命令路径与参数正确。独立 JSON-RPC initialize 和 tools/list 成功，ServerInfo 为 dexsql 0.1.1+6ac7870875a519ec471e41c93fc86d80e8e2bce7。
- DexSQL 二进制和许可证保留在用户级工具目录；没有放进仓库或模块 APK。

### APK DEX 结构

DexSQL 实际打开并分析 8.0.80 正式 APK 与 8.0.76 修改版样本：

| DEX 结构项 | 8.0.80 正式版 | 8.0.76 修改版样本 |
|---|---:|---:|
| DEX 文件数 | 18 | 17 |
| class definitions | 254,508 | 236,037 |
| method identifiers | 1,138,510 | 1,050,779 |
| field identifiers | 742,476 | 683,206 |
| DEX version | 037 | 037 |

分析使用 DexSQL 的 dex_files、classes、class_methods、class_fields、method_strings、method_calls、method_field_accesses、method_used_types 等结构化表，并用 decode 输出目标方法的 smali。表中的字符串命中数是 DEX 静态引用数；不等于 DexKit 的最终候选数、类加载成功数或运行时 Hook 成功数。各处单独列出结构过滤后候选数，不能将“字符串存在”视为定位成功。

重点 smali 解码样本：8.0.80 的 Lxx0/r;->l(Landroid/content/Context;Z)Ljava/lang/String;。方法体实际调用 Context.getPackageName()、Context.getResources()、Resources.getIdentifier(String,String,String)、Resources.getString(int)，并调用 String.isEmpty()。这一调用链支持消息描述兜底规则的静态匹配判断，但不能证明 Hook 后业务行为安全。

## 3. 源码入口、类加载、失败和缓存审计

### 实际安装入口

- ModuleEntry.onPackageReady 只接受两个微信包名和对应进程；主进程及子进程都会先进入 installPackageHooks。当前代码先装 Tinker attach 与平板早期 Hook，非主进程随后只处理 :appbrand* 轻量 Hook；主进程再处理 Mars CDN、底栏、Application.onCreate 后完整初始化。见 app/src/main/java/h/Hchat/ModuleEntry.java:80-155,253-270。
- Tinker attach 从 Context 中获取 host/Tinker ClassLoader；完整初始化时依次优先使用 Context loader、线程 context loader、framework loader。DexKit bridge 以 ApplicationInfo.sourceDir 为 APK 输入，30 秒无查询后释放托管 native 分析资源。见 ModuleEntry.java:543-570。
- third_party/dexkit-memory 包含 DexKitBridge 托管、native idle handle 回收、APK 身份校验与恢复逻辑。本阶段只读；保留现有 DexKitBridge 运行时，不触碰自定义 native 空闲回收补丁。DexSQL 是桌面静态分析工具，不提供此处 Android DexKitBridge API 的替代品。

### 87 项功能注册及失败隔离

- FeatureRegistry.createDefaultManager() 恰好注册 87 个功能，完整清单见第 4 节，注册顺序见 app/src/main/java/h/Hchat/hooks/core/FeatureRegistry.kt:95-186。
- FeatureManager.installAll 先逐个执行全部功能 onInit，然后按 installation policy 和开关逐个安装；单项 onInit 或 install 异常会记日志并清理该项，继续处理其他功能。见 FeatureManager.kt:32-104。
- DexInstallScheduler 将任务放入单线程队列，按 EARLY / BRIDGE / WARMUP 和 DexKit 锁串行执行；installer 返回 false 或抛异常时最多再试 6 次，延时为 1、3、6、12、24、48 秒（受 60 秒上限限制）。返回 true 就视为安装成功。见 DexInstallScheduler.kt:16-31,130-215。
- 因此，真正触发重试的条件是 feature installer 返回 false 或抛出异常。若 locator 内部吞掉异常、返回空字段，但上层 installer 仍返回 true，调度器不会补试。DexFinder.resolveAll() 即属关注点：缓存未命中后依次调用定位器，最后无条件设置 resolvedAll=true 并保存；这个标志仅表示本轮解析完成，不表示所有必需方法都已找到。见 DexFinder.java:296-353。
- HookRegistry 使用安装代次 lease、去重键和失败回滚。部分 Hook 失败或生命周期取消时可撤销本 lease 已装 Hook；destroyAll 先使旧代次失效再 unhook。见 HookRegistry.kt:23-54,72-96,125-134。

### DexFinder、SettingsDexFinder 和公共 API

- DexFinder 中央解析约 40 组请求/服务/界面相关定位器，覆盖 AddMsg、收拆红包、网络队列、文本/图片/文件/视频/语音/表情发送、数据库、撤回、联系人和群成员、转账、SNS、聊天页、原生发送按钮及礼物 API；其文件头适配范围仍写着“8.0.49 ~ 8.0.72+”，属于过时说明。见 DexFinder.java:35-38,296-353。
- SettingsDexFinder 单独解析旧 Preference 设置页、新版 SettingItem 框架和加号菜单；核心设置缓存与 PlusSubMenu 缓存分开读写。菜单定位不是硬编码 zg，而是对带 MicroMsg.PlusSubMenuHelper 字符串的类继续过滤：点击方法形状、无参且返回 BaseAdapter 的方法、实例 SparseArray 字段和 Context 字段。见 SettingsDexFinder.java:110-140,613-679。
- WechatApiFeature 构建消息、联系人、群聊、数据库变更、会话变更、媒体、SNS、转账、好友验证、生命周期等公共 API，并在 DexReady 后预热/补装 DB listener、消息事件和会话观察器。见 app/src/main/java/h/Hchat/hooks/api/core/WechatApiFeature.java:44-170。目前这些 API 最关键的共有依赖之一是 addMsgClasses。
- WeChatVersionApi 的 runtime key 使用包名、versionName、versionCode、clientVersion、Tinker ID、patch ID、APK source 文件修改时间；没有 APK SHA-256。DexCacheIdentity.sameRuntime 在跨进程比较时只比前 7 个字段，不包括末尾 scope/loader 字段。见 WeChatVersionApi.java:96-163、DexCacheIdentity.kt:6-17。
- DexMethodCache 按缓存描述符重新反射解析 Method/Constructor；它证明成员在当前 ClassLoader 中存在且描述符一致，不证明方法的业务语义仍一致。多数 locator 会自行再过滤，程度并不一致。
- DexFinder 的全局快照只有版本/运行时身份、complete 位和描述符可解析性门槛；不会把每个关键 locator 的“是否成功”纳入整体 complete 判断。缓存命中时设置 resolvedAll=true 并跳过重新扫描。静态失败的空列表可能作为完成快照保存，之后相同 runtime key 继续命中。见 DexFinder.java:3538-3575,4887-4920、DexFinderCacheStore.java:36-63。
- 重点缓存语义风险：WeChatTabletFeature.loadCachedMethod 取得精确方法描述符后没有在使用前重新验证目标方法行为；cache key 又不含 APK hash。修改版微信若保留 version/Tinker/source mtime 等身份字段，可能复用结构上可解析、语义已变的缓存。见 WeChatTabletFeature.kt:183-240 与 WeChatVersionApi.java:134-163。
- 额外 RetryableLocatorState 只用于联系人 protobuf sender 定位；没有统一的失败状态/重新扫描语义。SNS、设置、AddMsg 等定位器各有自己的空结果及缓存处理方式。
- 独立 feature 目录也有 DexKit 查询、反射、固定类名和固定 Hook 方法；第 4 节完整登记注册项，第 5 节重点对照本轮用户列出的路径。静态出现 com.tencent.mm.* 名称只是 DEX 字符串/类型引用，若它不是 class definition，仍需考虑 split/plugin/Tinker 动态加载；本报告不会仅据此判定功能失效。

## 4. 87 项功能清单及当前兼容状态

状态说明：**候选/未验**表示 8.0.80 base DEX 中看到与当前锚点/形状相符的候选，尚未运行微信并触发 Hook；**未验**表示源码入口已登记，但当前证据不能证明功能可用；**静态风险**是已确认的 locator 失配或强风险。所有条目在 8.0.80 真机上的功能状态都仍是 NOT_VERIFIED。

### 核心入口、设置和支付（01–18）

| # | 功能 | 注册类 | 状态 |
|---:|---|---|---|
| 01 | 设置入口 | SettingsFeature | 新版 MainSettingsUI 与加号菜单结构候选；旧设置页类缺失；未验 |
| 02 | 微信公共 API | WechatApiFeature | 部分 API 候选；消息事件链受 AddMsg 静态风险影响 |
| 03 | 原生发送按钮 | NativeSendButtonFeature | send msg onClick 锚点在 .80 只有 1 个方法引用，形状为 onClick(View):void；未验 |
| 04 | 悬浮快捷菜单 | FloatingShortcutFeature | 有 Application 早期安装入口；运行时未验 |
| 05 | 自定义底栏 | CustomBottomBarFeature | 有 Tinker attach 早期入口；运行时未验 |
| 06 | 圆角头像 | RoundAvatarFeature | AvatarDrawable 3 个方法引用，精确形状过滤后 1 个候选；未验 |
| 07 | 自定义头像 | CustomFriendAvatarFeature | 未验 |
| 08 | 实名尾字 | RealNameTailFeature | 未验 |
| 09 | 群员头衔 | MemberTitleFeature | 未验 |
| 10 | 自动抢红包 | AutoRedPacketFeature | 收/拆红包及 Union 请求候选存在；未执行红包业务 |
| 11 | 自动抢礼物 | AutoGiftFeature | AddMsg 依赖静态风险；ECS updateGiftMsgByCgi 锚点在 .80 无引用 |
| 12 | 平板模式 | WeChatTabletFeature | 设备与登录按钮锚点候选存在；缓存语义风险；.76 对照样本受修改补丁污染 |
| 13 | 自动收款 | AutoTransferFeature | 转账请求与查询候选存在；未执行转账业务 |
| 14 | 伪造零钱 | FakeWalletBalanceFeature | 未验 |
| 15 | 防撤回 | AntiRecallFeature | 未验 |
| 16 | 多选撤回 | MultiRecallFeature | 撤回请求候选存在；未验 |
| 17 | 屏蔽热更新 | DisableHotUpdateFeature | TinkerApplication/TinkerLoader 结构存在；未验 |
| 18 | 资料页显示ID | ProfileIdFeature | ContactInfoUI 固定类存在；未验 |

### 会话、消息、通知、媒体及小程序（19–60）

| # | 功能 | 注册类 | 状态 |
|---:|---|---|---|
| 19 | 隐藏长按菜单 | HideChatMenuFeature | 未验 |
| 20 | 快捷设置备注和标签 | QuickContactEditFeature | 备注 Protobuf 通道与标签请求有静态候选；未验 |
| 21 | 快捷设置群聊标签 | QuickGroupChatLabelFeature | 群聊 API 候选存在；未验 |
| 22 | 快捷查看朋友圈 | QuickMomentsFeature | SnsTimeLineUI 存在；未验 |
| 23 | 快捷已读 | QuickMarkReadFeature | 未验 |
| 24 | 快捷终止 | QuickTerminateFeature | 未验 |
| 25 | 自动回复 | AutoReplyFeature | 未验 |
| 26 | 消息自动转发 | AutoMessageForwardFeature | 未验 |
| 27 | 聊天分组 | ConversationGroupFeature | 未验 |
| 28 | 标签分组 | ConversationTabsFeature | 首页早期捕获入口存在；未验 |
| 29 | 发送文本格式 | MessageAffixFeature | 依赖模块文本发送 API；构造器规则静态失配 |
| 30 | 拦截正在输入上报 | TypingReportBlockFeature | 未验 |
| 31 | 禁止拍一拍 | PatBlockFeature | 未验 |
| 32 | 自动勾选原图 | AutoOriginalImageFeature | 未验 |
| 33 | 自动查看原图 | AutoViewOriginalFeature | 未验 |
| 34 | 移除转发限制 | RemoveForwardLimitFeature | UIC 候选可能存在；最低版本门槛冗余；未验 |
| 35 | 屏蔽艾特所有人 | AtAllNotificationBlockFeature | 未验 |
| 36 | 移除通话媒体限制 | CallMediaLimitFeature | 未验 |
| 37 | 屏蔽通话铃声 | CallRingtoneBlockFeature | 未验 |
| 38 | 自定义通知 | CustomNotificationFeature | 未验 |
| 39 | 关键词通知 | KeywordNotificationFeature | 通知独立路径未验；可能依赖消息观察 API |
| 40 | 文字转语音播报 | TextSpeechFeature | 未验 |
| 41 | 文本转语音 | TextVoiceFeature | 未验 |
| 42 | 僵尸粉检测 | ZombieCheckFeature | 未验 |
| 43 | 微信后台运行辅助 | BackgroundRunAssistFeature | 未验 |
| 44 | 删除键清引用 | QuoteDeleteClearFeature | 未验 |
| 45 | 滑动手势 | SwipeQuoteFeature | 未验 |
| 46 | 音频转换 | AudioTransformFeature | 未验 |
| 47 | 伪造语音时长 | FakeVoiceDurationFeature | 未验 |
| 48 | 会话时间样式 | ChatTimeStyleFeature | MvvmChattingItem 结构锚点存在，方法形状候选；未验 |
| 49 | 输入框提示 | InputHintFeature | 未验 |
| 50 | 消息气泡 | MessageBubbleFeature | MvvmChattingItem 结构锚点存在，方法形状候选；未验 |
| 51 | 消息文本颜色 | MessageTextColorFeature | ChattingDataAdapterV3 / _onBindViewHolder[ 锚点存在；多方法引用需运行时过滤 |
| 52 | 首页文字颜色 | HomeTextColorFeature | LauncherUI 存在；未验 |
| 53 | 隐藏头像 | HideChatAvatarFeature | 未验 |
| 54 | 屏蔽消息 | MessageBlockFeature | AddMsg 锚点静态失配，不能据当前定位规则确认可用 |
| 55 | 虚拟定位 | FakeLocationFeature | SLocationListener、Wgs84、DefaultTencentLocationManager 锚点候选存在；未验 |
| 56 | 指定骰子猜拳 | GameEmojiFeature | 未验 |
| 57 | 兼容低版本小程序 | FakeMiniProgramBaseLibFeature | appbrand 入口及基础类候选存在；未验 |
| 58 | 跳过小程序视频广告 | SkipMiniProgramVideoAdsFeature | appbrand 独立入口；未验 |
| 59 | 跳过全局小程序开屏广告 | SkipGlobalMiniProgramSplashAdsFeature | 8.0.80 有共同锚点与 canShowAd 方法候选；未验 |
| 60 | QQ点歌 | QQMusicOrderFeature | 未验 |

### 朋友圈、群聊及记录工具（61–87）

| # | 功能 | 注册类 | 状态 |
|---:|---|---|---|
| 61 | 朋友圈自动点赞 | MomentsAutoLikeFeature | 互动 API 有候选；未验 |
| 62 | 朋友圈自动评论 | MomentsAutoCommentFeature | 互动 API 有候选；未验 |
| 63 | 朋友圈自动转发 | MomentsAutoForwardFeature | 未验 |
| 64 | 朋友圈自动刷新 | MomentsAutoRefreshFeature | SnsTimeLineUI 存在；未验 |
| 65 | 朋友圈关键词屏蔽 | MomentsKeywordBlockFeature | SnsInfo 存在；多个历史类名仅作为字符串锚点，需逐项确认 |
| 66 | 朋友圈过滤 | MomentsContactFilterFeature | SnsInfo 存在；未验 |
| 67 | 朋友圈底部详情 | MomentsBottomDetailFeature | SnsTimeLineUI 存在；未验 |
| 68 | 朋友圈发布通知 | MomentsPostNotificationFeature | 朋友圈/消息观察入口未验 |
| 69 | 朋友圈原图上传 | OriginalMomentsUploadFeature | UploadPackHelper 与图片 helper 有候选；未验 |
| 70 | 朋友圈上传尾巴 | MomentsUploadTailFeature | 图片/文本候选存在；视频 helper 当前参数规则失配 |
| 71 | 朋友圈防撤回 | SnsAntiRecallFeature | 未验 |
| 72 | 朋友圈伪互动 | MomentsFakeInteractionFeature | SNS interaction 候选存在；未验 |
| 73 | 群聊标签 | GroupChatLabelFeature | 联系人标签/群成员显示名结构候选存在；未验 |
| 74 | 进退群监控 | GroupLeaveMonitorFeature | 群成员请求及聊天 API 候选存在；未验 |
| 75 | 改名监控 | GroupRenameMonitorFeature | 群聊显示名方法候选存在；未验 |
| 76 | 去除朋友圈广告 | RemoveMomentsAdsFeature | SnsTimeLineUI 与 ImproveSnsTimelineUI 存在；未验 |
| 77 | 修改聊天记录 | EditMessageFeature | LocalMessage/DB 结构锚点存在；未验 |
| 78 | 保存表情 | EmojiSaveFeature | 收藏适配器固定类存在；未验 |
| 79 | 语音消息预览 | VoicePreviewFeature | 未验 |
| 80 | 语音转发保存 | VoiceForwardFeature | 未验 |
| 81 | 群发助手 | SelectedMessagesFeature | 未验 |
| 82 | 转发 | MessageForwardFeature | SelectConversationUI 等 UI 条件需实际触发 |
| 83 | 定时任务 | ScheduledTaskFeature | 未验 |
| 84 | 模拟相机扫码 | FakeScanCameraFeature | 未验 |
| 85 | 解除状态词长度限制 | StatusTextLimitFeature | 未验 |
| 86 | 视频号媒体下载 | FinderMediaDownloadFeature | 未验 |
| 87 | 分支扩展功能 | HchatExtraFeature | MessageTypeLabels 注释明确为 .76 映射；必须在 .80 重核类型值与 UI 路径 |

## 5. 核心 Hook 的源码与 8.0.80 DEX 对照

候选数量口径：method_strings 的引用数、类定义数、或满足源码形状条件的反射候选数会在文字中分别说明。字符串引用命中不是 Hook 唯一命中。

| Hook/路径 | 当前源码规则及使用位置 | 8.0.76 → 8.0.80 DEX 证据 | 唯一性、风险和建议修改范围 |
|---|---|---|---|
| 进程入口 / Tinker | ModuleEntry 固定加载 com.tencent.tinker.loader.app.TinkerApplication.onBaseContextAttached(Context,long,long)；主进程和 appbrand 子进程均挂早期入口。ClassLoader 在 attach 后从 Tinker/Application/线程 loader 解析。 | 两版 base DEX 均有 TinkerApplication、TinkerLoader；.80 另有 TinkerSyncResponse、LegacyTinkerCore$PatchService。目标类定义各 1 个，目标签名稳定。 | 静态存在；Hook 是否在目标进程成功安装未知。最低版本 gate 必须先于此 Hook，见第 7 节。 |
| 设置页 / 加号菜单 | 旧设置页以固定 com.tencent.mm.plugin.setting.ui.setting.SettingsUI 加载；新版使用 setting_new.MainSettingsUI。加号菜单按字符串锚点再过滤 onItemClick(AdapterView,View,int,long):void、无参 BaseAdapter 返回方法和 SparseArray/Context 实例字段。 | 两版都有 MainSettingsUI，均没有旧 SettingsUI。菜单锚点 .80 有 6 个引用、4 个 owner；候选从 .76 的 com.tencent.mm.ui.rg 换为 .80 的 com.tencent.mm.ui.zg。.80 的 zg 有 1 个匹配点击签名、b():BaseAdapter、实例 SparseArray 和 Context；HomeUI 的锚点方法由 o() 变 r()。 | 菜单规则经过结构过滤后在 base DEX 中唯一。保持规则按结构定位，不写死混淆名；主设置 UI 和菜单需真机打开验证。 |
| AppMessage 消息详情描述 | DexFinder.resolveMsgDescTextApi 按方法名 l、返回 String、参数 (Context,boolean)、AppMessage 锚点、Resources.getIdentifier 和 String.isEmpty 调用关系过滤；ModuleEntry 无条件尝试安装 after fallback。见 DexFinder.java:1301-1351、ModuleEntry.java:455-485。 | 已有 .76 证据是 pt0/r；.80 唯一精确形状候选为 xx0/r->l(Context,Z):String。方法体调用资源查找/读取和 String.isEmpty()。 | 结构和调用链匹配，不等于异常边界确认。兜底当前对所有支持版本尝试安装，后续应明确版本/方法失败类型边界，并用 8.0.80 安全消息样本验证。 |
| 模块文本发送 | DexFinder.resolveSendTextMsg 搜索 /cgi-bin/micromsg-bin/newsendmsg 等类/方法字符串，再只接受构造器 (String,String,int,int,long) 或 (String,String,int,int,Object)。WeChatMessageApi 与格式/自动发送功能消费该 API。见 DexFinder.java:703-742、WeChatMessageApi.java:189-196,791-808。 | .76 修改版的候选 Ly11/r0 有上述两个 5 参数构造器。.80 有 4 个 URL 锚点方法引用（owner：VoIP y、q61/s0、s61/b0、gd5/q76）；其中真正请求候选 q61/s0 的构造器为 (String,String,int,int,long,String) 或 (String,String,int,int,Object,String)，末尾新增 String。全体 .80 class_methods 查询没有匹配当前 5 参数构造器。 | **确认静态失配：当前 exact constructor matcher 在 .80 为 0 命中。** sendTextMsgClass 将保持 null，模块文本发送 API 不能据源码认为可用。下一阶段仅修改 DexFinder 这一请求定位和所有构造调用方适配；不可直接改 Hook 前需确认多出的参数语义。 |
| AddMsg 消息处理入口 | resolveAddMsgClasses() 只尝试 dkAddMsg，失败后尝试 processAddMsg，然后将方法 owner 加入 addMsgClasses。消费方包括消息事件 API、消息屏蔽、礼物监听和红包消息检测。见 DexFinder.java:403-422、WeChatMessageEventApi.java:47-78、MessageBlockFeature.kt:30-70、GiftMessageHook.java:37-52。 | DexSQL 在 .76 修改版及 .80 对两个字符串都查到 0 个 method-string 引用。没有可交给该规则过滤的候选方法/参数。 | **确认静态锚点失配。** 新版消息处理类可能使用其他锚点或动态 DEX，但当前 base DEX 查询和源码规则无法命中。影响消息接收事件、关键词拦截、礼物和红包监听。新规则应先用真正的结构/调用关系定位，再观察运行时消息类参数；不凭猜测填入混淆类名。 |
| 数据库 / 消息与会话监听 | resolveDatabaseApi 用 MMKernel、mCoreStorage not initialized!、MicroMsg.ConfigStorage、shouldProcessEvent db is close :%s、MicroMsg.SqliteDB、sql is null 定位存储及 SQL wrapper；WechatApiFeature 随后安装 database/message/conversation/contact/chatroom observers。 | .80 的 mCoreStorage not initialized! 1 个引用，唯一目标形状 x():CoreStorage；ConfigStorage 13 个 class/method 引用；shouldProcessEvent 8；SqliteDB 3；sql is null 14。.76 对应计数分别为 1、12、8、3、14。核心 getter .80 owner 为 zp0/j1，返回 zp0/b0。 | 核心入口和关键字符串仍在，但多命中项必须依源码 owner/返回类型/数据库字段和调用关系过滤。静态结构不是 DB observer 实际收到回调的证明。优先配合 AddMsg/消息存储 API 验证。 |
| 消息撤回请求 | resolveRevokeMsgApi 按 revoke URL 与 NetSceneRevokeMsg 字符串收集类，要求构造参数为 LocalMessage 兼容类型加两个 String。见 DexFinder.java:3200-3228,5298-5311。 | .80 revoke URL 2 个方法引用、NetSceneRevokeMsg 6 个；候选 com/tencent/mm/modelsimple/d1 构造器 (storage/f9,String,String) 符合形状。另一个 URL owner pw3/a 的 (String,long,long,long,String) 不符合。.76 修改版 LocalMessage 参数包名为 storage/e9。 | 按参数结构候选唯一，但撤回后端调用及权限需要运行时验证。不要因 URL 字符串命中就推断可安全调用。 |
| 会话列表绑定 / 消息时间 / 气泡 | 对应 Feature 以 MicroMsg.MvvmChattingItem、onBindView 等字符串与 holder/bean/列表参数形状定位。 | .80 与 .76 均有 MvvmChattingItem 字符串锚点，候选方法各 1 个；.80 nm5/g->h(qw5/s0,qw5/c,int,int,boolean,List):void，.76 ve5/g->h(po5/s0,po5/c,int,int,boolean,List):void。onBindView 字符串在 .80 不存在，但当前使用多个备选锚点。 | 描述符形状兼容且当前锚点非单点依赖；需打开会话并分别覆盖文本、媒体、系统消息和滚动复用场景。若未来规则把单一 onBindView 提升为必需条件将失配。 |
| 头像 / 消息颜色 | RoundAvatar 用 AvatarDrawable 类锚点和方法签名过滤；MessageTextColor 使用 ChattingDataAdapterV3、_onBindViewHolder[、msgInfo 与 holder 结构。 | AvatarDrawable 在 .80 有 3 个方法引用；满足静态 void (ImageView,String,float,boolean) 形状的候选 1 个（pluginsdk/ui/u->b）。MicroMsg.ChattingDataAdapterV3 有 18 个方法引用，_onBindViewHolder[ 1 个，msgInfo 字段锚点存在。 | 头像候选按签名唯一，仍需消息 UI 验收。颜色定位不能只按 18 个 owner 引用判断成功；需确认 holder 与消息实体绑定。 |
| 原生发送按钮 | resolveNativeSendButtonApi 查 onClick 与 ChatFooter、send msg onClick、paste clip board to send，再通过 isChatFooterSendClickMethod 做反射约束。见 DexFinder.java:3490-3528。 | send msg onClick 在两版均只有 1 个引用，.76 owner p1、.80 owner pluginsdk/ui/chat/z1，两者为 onClick(View):void。其他 ChatFooter 字符串命中很多，不能作为候选数。 | 形状可定位；请在真实聊天页验证发送前回调、插件拦截顺序、重复点击和功能开关。 |
| 联系人备注 / 标签 / 好友验证 | 备注通过 ContactProtobufSender 使用 setcontactproperty CGI 10022 和动态 Protobuf dispatch；标签使用 CGI + MicroMsg.Label.* 锚点和 String/List/LinkedList 构造器；verifyuser 使用 CGI/日志锚点再校验请求构造器及 doScene/回调。见 WeChatContactApi.java:705-719,1027-1034、DexFinder.java:5616-5705。 | .80 setcontactproperty 有 1 个静态引用，`iz3/v->r(Context,Continuation):Object`；.76 修改版为 `bs3/v`、同参数/返回形状。通用 Protobuf base `com.tencent.mm.protobuf.f` 有 `op(int,Object[]):int`，NetSceneBase `com.tencent.mm.modelbase.n1` 有 `dispatch(network/s,network/y0,network/l0):int`。addcontactlabel 3 个引用/2 个 owner，`dh3/a` 有 `(String)` 与 `(List)` 构造器；modifycontactlabellist 2 个引用/2 个 owner，`dh3/e(LinkedList)`。verifyuser 3 个 URL 引用/2 个 owner；`pluginsdk/model/r3` 有 8 参数和 11 参数请求构造器、网络请求/回调方法。.76 修改版参数形状相近，混淆依赖包变化。 | 备注发送依赖多个通用 Protobuf 类的联合结构，静态 endpoint 引用不等于 sender 唯一命中；需在目标 ClassLoader 中确认 config builder、request/response、callback 与 static dispatch 的整体合同。标签形状候选清楚；好友验证请求有多个构造器且 URL 关联不代表同一业务，需要从当前 API 调用点判定采用哪个请求。不得进行真实修改备注、加好友或发请求验收。 |
| 群成员/群成员显示名 | Add/Del/Invite API 使用 CGI 字符串和参数化构造器；显示名用 ChatroomDisplayNameCache + (String,String):String。 | .80 add 1 个引用，tn/n(String,List,String,Object)；del 1 个引用，tn/q(String,List,int)；invite 2 个引用，tn/y 有 (String,List,int,Object) 与 (String,List,int,String,storage/f9) 两个候选。显示名 anchor 5 个引用，经签名过滤 w41/v1->m(String,String):String 唯一。.76 对应混淆 owner kn/* / e01/v1。 | Add/Del 与显示名有结构候选；Invite 仍有两个重载待确定调用语义。只做静态定位，不执行群成员变更。 |
| 朋友圈互动 | SnsInteractionLocator 用 SnsService / can not add Comment 锚点，再按 Like/Comment 方法参数结构区分并缓存。 | .80 SnsService 16 个方法引用；can not add Comment 2 个 owner method。候选 .80 h6.n(SnsInfo,int,ek4/a,mg6,boolean,int,int):mg6 与 h6.o(SnsInfo,int,String,long,String,boolean,int):mg6；.76 修改版 owner 同为 h6，形状近似但包依赖改变。 | 有 2 个形状不同的候选，可用于对照 like/comment 分支；仍需核验当前 validator 对两个方法的判别和返回值含义，不能实际点赞/评论。 |
| 朋友圈发布 / 图片上传 | UploadPackHelper 用 MicroMsg.UploadPackHelper、setContentDes、addImageMediaObjByPath、setSdkId/Name 联合候选，再校验链式返回、参数和布尔返回。 | .80 helper 候选为 com.tencent.mm.plugin.sns.model.i8；setContentDes 的 (String):i8 候选存在；图片添加锚点存在并有 bool(String,String) helper 方法。getSnsUploadManager 的真实 getter 为无参静态返回 manager 的方法；同锚点还混入一个 UI 方法，需当前结构过滤器排除。 | 文本和图片路径结构候选存在。朋友圈发布会产生外部业务副作用，需在独立验收授权下使用本地测试媒体和测试账号。 |
| 朋友圈视频上传 | findSnsAddVideoMethod 依次按 addSightObjectByPath、produceSightByPath 搜索，只接受 helper 声明、boolean 返回、恰好 4 个 String 参数。见 DexFinder.java:4695-4736。 | .80 addSightObjectByPath 有 5 个引用；helper i8.f(String,String,String,String,String):boolean 为 5 个 String。produceSightByPath 共 4 个引用、3 个类；同 helper 的 boolean 候选仍是 5 个 String，另一个 helper 方法返回请求对象且形状更长。.76 参考样本 helper 类结构不同。 | **确认静态签名失配：当前两组 anchor 的 .80 helper 候选均不满足 4 个 String。** 视频上传尾巴/视频发布路径不能判定可用。后续先通过 decode/call graph 确认第五参数意义，再调整签名与调用方。 |
| 小程序 API / 广告 | 以 AppBrand CGI、QualitySession、LaunchFromNotifyReferrer、JSAPI private_openUrl、AppBrandAdUtils 与 canShowAd 结构定位；appbrand 进程单独安装。 | .80 base DEX 存在 QualitySession、AppBrandLaunchFromNotifyReferrer；private_openUrl 1 个候选 jsapi/dc->A(Jsapi,JSONObject,int):void，形状与源码约束一致。全局 splash 的 isAdContact...canShowAd... 为 1 个方法候选 gd1/j->a(AppBrandInitConfigWC):boolean。启动参数日志锚点所在构造器 17 参数。 | 结构候选与类型合同基本相符，但 appbrand 动态加载和真实启动时序未验。不得仅因 base DEX 有定义就认为子进程 Hook 完成。 |
| 平板模式 / 虚拟定位 | 平板模式使用设备字符串与登录按钮结构，并读缓存；虚拟定位从 SLocationListener/Wgs84/默认 TencentLocationManager 定位监听。 | .80 Lenovo TB-9707F 1 个引用在 ui/k9->a():boolean，登录按钮锚点 1 个候选；fake location 有 SLocationListener 1、Wgs84 1、DefaultTencentLocationManager 9 个字符串引用，出现 3 个 LocationChanged 形状候选。.76 样本 Lenovo 字符串位于 bk->B$codexTabletOriginal，确认该对照受补丁修改。 | 只确认静态锚点；tablet cache 缺少行为再验且 runtime key 不含 APK hash。定位需覆盖主进程与 appbrand 进程一致性、恢复/关闭设置，不做设备身份业务变更。 |
| 收红包 / 拆红包 / Union | 使用 CGI URL / union 日志字符串定位类，再要求 7/10 等参数数目的构造器；见 DexFinder.java:425-570。 | .80 receive URL 1 个方法引用，候选 n6->H():String 的请求类有 7 参数构造器；open URL 1 个引用，h6->H():String 请求类有 10 参数构造器。Union receive 6 参数构造器及 Union open 10 参数构造器均有对应候选；.76 修改版同形、混淆名不同。UI 的 LuckyMoneyNewReceiveUI、LuckyMoneyNotHookReceiveUI 类存在，旧 LuckyMoneyReceiveUI 类定义不在 base DEX。 | request 与 UI 静态结构候选存在，但参数语义、调用时机、红包状态机和 UI fallback 均未运行。AutoRedPacket/自动收款/礼物不在本阶段执行任何支付或业务操作。 |
| 祝福红包 / 礼物消息 | wishwxhb 按 CGI URL 候选、4 参数构造器；ECS 礼物静默处理按 updateGiftMsgByCgi 方法字符串找到 2 参数 Task ctor，其中一参须为 storage message。 | .80 wish URL 1 个引用，luckymoney/model/g6 有 (String,String,String,String) 构造器、H():String 及 JSON 回调。ECS updateGiftMsgByCgi 在 .80 和 .76 修改版均为 0 个方法字符串引用；礼物监听也依赖空的 AddMsg 定位结果。 | wish request 规则静态匹配；ECS 静默礼物处理与消息监听目前不可由现有锚点证明。需拆开普通礼物定位、ECS 分支、消息入口分别审计。 |
| 转账操作 / 查询 | 优先固定加载 com.tencent.mm.plugin.remittance.model.n0 并用结构 validator；fallback 用 Remittance 字符串和 transferoperation URL。查询按请求日志、返回 JSON 方法形状过滤。见 DexFinder.java:2264-2336。 | .80 n0 类存在，transferoperation URL 1 个方法引用；请求类构造器约 14 个参数，带多 String/整数/Map/long；.76 修改版有重载且父类 package 不同。查询候选 h1 有 6 参数构造器，另有其他结构相似类。 | 静态候选存在；父类和构造器字段布局有版本变化。支付/转账副作用不可用本阶段验证；后续先只验证只读结构和异常保护，再单独计划真实业务验收。 |

### DEX 中的固定类名检查

以下 class definitions 在 .80 base DEX 中存在：LauncherUI、MainTabUI、HomeUI、ChattingUI、SnsTimeLineUI、ImproveSnsTimelineUI、SnsInfo、ContactInfoUI、ChatroomInfoUI、LuckyMoneyNewReceiveUI、LuckyMoneyNotHookReceiveUI、收藏 adapter com.tencent.mm.plugin.fav.ui.adapter.c、MainSettingsUI。旧 SettingsUI 与 MvvmSelectContactUI 未找到 class definition。

一些 SNS 名称（SnsCore、SnsPathHelper、SnsVideoLogic/Service、SnsRouter、SnsInfoStorage、SnsSqliteDB、SnsSelfAdapter、SnsTimeLineBaseAdapter、ImproveTimelineItemMeasure）在 DEX 中作为字符串/类型引用出现，但未找到同名 base class definition。凡是 locator 用这些字符串作 method anchor 时不能直接判失败；若 feature 通过 KavaReflector 固定 loadClass 则需确认 split、插件或运行时 classloader 中是否提供同名类。.80 base DEX 静态查询没有覆盖 Tinker patch、其他进程按需加载的 plugin/split DEX。

## 6. 已确认、疑似及尚未验证的问题

### 已确认的静态失配

1. **AddMsg：** 当前规则只有 dkAddMsg 与 processAddMsg 两个字符串；两者在目标 base DEX 均为 0 引用。消息事件、消息屏蔽、红包/礼物消息监听共享 addMsgClasses，属于公共依赖问题。
2. **文本发送构造器：** .76 修改版有当前 5 参数构造器；.80 候选增加尾部 String，当前精确签名无命中。模块发文本 API 及依赖文本发送的功能会受影响。
3. **朋友圈视频上传：** 当前要求 helper 的 boolean 方法恰好 4 个 String 参数；.80 addSightObjectByPath/produceSightByPath helper 是 5 个 String，当前规则无命中。
4. **ECS 礼物静默：** updateGiftMsgByCgi 锚点在 .80 无引用；当前解析失败后会把 ECS locator 标记为 resolved，且与 AddMsg 消息监听共同限制 AutoGift。
5. **旧设置 UI：** com.tencent.mm.plugin.setting.ui.setting.SettingsUI 在 .80 无定义；新版 MainSettingsUI 存在。当前 legacy injector 需自跳过，不能把旧类缺失误判为整个设置入口失效。
6. **消息描述 fallback 的历史范围：** ModuleEntry 注释和 MsgDescFallback 主要以 .76 风险为依据，但实际安装会对所有支持版本运行。目标方法 .80 有结构匹配，不代表只有预期异常才会被吞；应确认异常过滤和版本边界。

### 疑似风险，需运行时或更完整代码关系确认

- DexFinder.resolveAll 的 complete 位不代表所有 locator 成功；空结果可能与全局快照一同保存。建议在新版本兼容层记录逐 locator 状态，防止“解析流程完成”掩盖关键 API 缺失。
- DexFinder/feature 缓存含精确 descriptor，但语义校验覆盖不均；runtime key 没有 APK SHA。修改包保留 versionName/versionCode/Tinker/mtime 时有同身份复用风险。
- WeChatTabletFeature 缓存命中后直接用成员描述符，没有目标行为 validator。
- PlusSubMenu、SNS 各候选能按结构过滤，但目前仍没有 8.0.80 动态 ClassLoader 的实际反射结果、Hook 回调轨迹或 UI 回归。
- RedPacketHooker 内存在未做 version scoping 的混淆类名集合与方法名集合；需要逐条和 8.0.80 class definition/调用点核对，不能据此触发真实红包。
- 搜索到的固定类名不在 base DEX definitions 中时，可能仍由 plugin/split/Tinker 提供；需要运行时 ClassLoader 证据，不能直接列为已失效。
- .76 修改版已改写平板方法等逻辑，不能拿它给官方 .76 做全面二进制差异结论。

### 尚未验证

87 项功能全部没有在 8.0.80 运行并逐项触发。下阶段应覆盖主进程、:appbrand*、启动时序、DexKit cache miss/hit 两种启动、关键 UI 首帧、DB observer 实际事件、后台/恢复、低端与横竖屏场景。支付、好友申请、群成员变更、朋友圈发布/点赞/评论等具有外部业务副作用的场景需要独立测试计划和测试数据。

## 7. 旧版本兼容代码清理建议（本阶段未删除）

全局扫描生产源码中的版本字符串/阈值后，确认到以下旧分支。没有发现针对 8.0.58、8.0.66、8.0.68、8.0.72、8.0.74 的明确生产代码专用分支；相关命中主要是现代版本阈值或注释。旧版本号并不自动意味着可以删除，清理前需按最低支持 8.0.76 和 8.0.80 结构验证。

| 代码位置 | 旧规则 | 与 8.0.76–8.0.80 的关系 | 建议 |
|---|---|---|---|
| WeChatRetransmitPayload.kt:185-188 | !versionName.startsWith("8.0.49") && versionCode != 2600 | 两个条件只排除旧 8.0.49/2600；最低支持线 .76 已高于它们 | 阶段 B 确认项目没有更早可运行目标后，删除例外，保留通用媒体 token fallback；无需仅凭 versionCode 判断新版 |
| WeChatImageApi.java:630-634 | 先 isWeChatAtLeast(8,0,66)，再 fallback versionCode >= 2980 | 已支持版本均应满足 8.0.66；第二个条件对可信 versionName 冗余，versionName 缺失时会误把旧 build 归入新版 | 保留 feature gate 的必要性评估后简化到统一版本身份 API，避免混搭阈值 |
| MomentsKeywordBlockFeature.kt:1195,1224 | Improve SNS profile switch 最低 versionCode 3020 | 低于 .76 的基线 versionCode 3140，按支持集冗余 | 先核对 modified 包的版本映射，再考虑移除数值门槛；保留类/能力检测 |
| RemoveForwardLimitFeature.kt:298,320 | UIC 转发最小 versionCode 2841 | 低于 .76 支持线，按支持集冗余 | 与 UIC 类结构验证一起清理；不要因阈值删除真实 capability guard |
| SettingsInjector.java:98-99,137-143 | 每次注入都会尝试旧 SettingsUI legacy path，再尝试 modern path | .80 无 legacy class definition，新版 MainSettingsUI 存在；legacy 方法自身会跳过缺失类 | 先确认 .76/.80 所有包变体均由现代设置框架覆盖，再移除 legacy 调用；属于降低无用探测，不应改公共 API |
| DexFinder.java:37-38 | 文件头称支持 8.0.49–8.0.72+ | 注释已过时，与实际最低支持 8.0.76 冲突 | 更新文档注释；不代表定位逻辑本身需要删除 |
| MessageDetailsVisibility.kt:6、MessageTypeLabels.kt:12、ModuleEntry.java:458-485 | .76/.77 专项验证或崩溃修复说明 | .76 在最低支持范围内；.80 已找到部分相同结构，但行为未验 | 不应删除；先在 .80 真机复验并将条件升级为按结构/异常分类，而不是清掉“历史旧代码” |
| SettingsDexFinder.java:77 | 现代 SettingItem 框架标记为 >= 8.0.49 | 对支持集可能冗余，但本身是机制文档/框架描述 | 只有确认 8.0.76 起无旧框架需求后再简化；优先保留运行时结构探测 |

### 最低版本全局门禁建议

当前 ModuleEntry.onPackageReady 在识别包名与进程后即安装热更新和平板早期 Hook；随后主进程会装 Mars/底栏/首页早期 Hook，appbrand 子进程会装自己的 Tinker/Application 入口。因此 gate 必须位于 ModuleEntry.onPackageReady 或 installPackageHooks 的最前部、任何早期 Hook 之前，并被两个目标包和所有子进程共同调用。见 ModuleEntry.java:80-113,180-249,253-270。

建议建立一个集中 WechatCompatibilityGate，输出支持/拒绝及证据原因，且 fail closed：

1. 获取 Package Manager 的 versionName 和 versionCode；以可解析的 versionName 判断最低线 8.0.76，不把 versionCode 单独当作真相。
2. 可用时再读取 WeChat 内嵌 clientVersion/BuildConfig 和 Tinker patch 身份，与版本映射交叉核对；versionCode 仅作辅助。如果元数据互相矛盾或无法确认 build，不自动安装可能改变 UI/消息/支付的 Hook。
3. 对 main、com.tencent.mm:appbrand*、com.tencent.mm.wx 进程统一走同一个决策；主进程 Tinker attach、appbrand attach/Application fallback 不能绕过 gate。
4. 在后续稳定 Context 可用时重新核对 WeChatVersionApi.current()，若 patch/clientVersion 与初始检查不一致，撤销或禁止后续 Dex 安装。
5. 保留最低版本以下的安全提示/日志，但不注册任何 production Hook；诊断中记包名、版本证据、Tinker ID、是否拒绝。不要把 APK SHA-256 作为唯一版本识别方式，因为合法修改版包会有不同 hash。

具体实现前需检查 libxposed API 102 的 package-ready 可用字段和系统 PackageManager 访问方式，保证 gate 真正在 Tinker 前执行；不要把需要 host Application 的 WeChatVersionApi 构造移到一个 Application 尚未创建的时刻而导致 gate 缺失。

## 8. 阶段 B/C 建议顺序

### 阶段 B：兼容内核与核心静态定位

1. 先落地双包、全进程最低版本 gate 和结构化诊断；确保旧版拒绝路径在所有早期 Hook 前生效。
2. 为缓存增加 APK/DEX 身份或能力指纹和逐 locator success state；升级 schema，使空定位结果不能伪装成完整有效快照。对运行时 ClassLoader 重新解析缓存成员后补充关键参数/返回类型/owner 断言。
3. 先修公共阻断：AddMsg 新锚点/结构链、模块文本发送构造器新增参数、DB/message observer 安装状态。修复应以 8.0.80 DEX/调用关系证据为依据，覆盖当前 .76 路径，不能写死本次发现的混淆类名。
4. 再修朋友圈上传 helper，分别验证文本、图片、视频、提交、分享和回调；视频五参的每个参数要通过 smali/call site 追踪，才写入生产 Hook。
5. 修复设置菜单、消息详情、native send button、聊天列表绑定、头像/消息显示，再补联系人标签/验证、群成员、会话页 API。
6. 将“解析完成”和“关键 API 可用”拆成不同诊断状态；修复 retry 调度中 locator 内部吞异常但外层返回成功的路径。
7. 最后处理平板与 fake location、多进程/动态 Tinker 类、朋友圈互动/列表定位等易受运行时加载影响的 feature。

### 阶段 C：风险分层验收

1. 先只读启动检查：.76、.80 与低于 .76 的拒绝边界，主进程及 appbrand 进程，Tinker 前后 ClassLoader，DexKit cold/warm cache，完整错误日志。
2. 验收无外部副作用 UI：设置入口/加号菜单、聊天列表、聊天详情、时间、头像、气泡、发送按钮回调、消息存储和 DB observer。
3. 用自有测试帐号和可删除测试数据验证联系人/群聊、朋友圈读取及媒体上传；消息发送只在明确授权的隔离帐号/对话进行。
4. 红包、转账、礼物真实请求单独列为高风险验收；先仅验证 parser、候选构造和 Hook 安装状态，不调用真实支付/转账接口。任何真实业务验证需在后续任务单独明确。
5. 全量验收 87 项功能的启用/禁用、缓存命中/重建、恢复、横竖屏与子进程；以功能级日志和对照视频/日志记录通过或未通过，不能用“模块加载成功”替代功能验收。

## 9. 风险、阻断与本轮边界

- **阶段 B 的明确阻断项：** 当前文本发送、AddMsg、朋友圈视频上传 locator 均存在静态失配，公共 API 与多个功能存在级联影响。不得在没有新定位证据时把功能状态标为 8.0.80 已兼容。
- **比较资料缺口：** 没有官方 8.0.76 APK；真机 .wx 样本经过改写。要做可信二进制差异，应在后续找到官方 .76 正式 APK 或另行核验签名与发布渠道。
- **运行态证据缺口：** 本阶段未启动微信；无法证明 LSPosed 加载、Tinker 时序、运行时 ClassLoader、实际 Hook 数、数据库回调、页面行为和修改版包兼容。
- **外部副作用边界：** 没有发送消息、加好友、邀请/移除群成员、发布/点赞/评论朋友圈、收发红包、转账或礼物，也没有清除数据或卸载微信。
- **提交范围边界：** 仅提交本审计文档；DexSQL、APK、DEX 输出、原始 smali、敏感日志和临时文件均不进入仓库。本阶段到此停止，等待阶段 B 指令。

### 主要源码定位索引

- app/src/main/java/h/Hchat/ModuleEntry.java
- app/src/main/java/h/Hchat/WechatPackage.kt
- app/src/main/java/h/Hchat/hooks/core/FeatureRegistry.kt
- app/src/main/java/h/Hchat/hooks/core/FeatureManager.kt
- app/src/main/java/h/Hchat/hooks/core/DexInstallScheduler.kt
- app/src/main/java/h/Hchat/hooks/core/HookRegistry.kt
- app/src/main/java/h/Hchat/dexkit/DexFinder.java
- app/src/main/java/h/Hchat/dexkit/SettingsDexFinder.java
- app/src/main/java/h/Hchat/dexkit/DexMethodCache.kt
- app/src/main/java/h/Hchat/dexkit/DexFinderCacheStore.java
- app/src/main/java/h/Hchat/dexkit/DexCacheIdentity.kt
- app/src/main/java/h/Hchat/hooks/api/runtime/WeChatVersionApi.java
- app/src/main/java/h/Hchat/hooks/api/core/WechatApiFeature.java
- app/src/main/java/h/Hchat/hooks/api/message/WeChatMessageEventApi.java
- third_party/dexkit-memory/README.md、DexKitBridge.kt、IdleNativeHandle.kt、ManagedApkIdentity.kt
- 功能逐项注册清单：app/src/main/java/h/Hchat/hooks/core/FeatureRegistry.kt:95-186
