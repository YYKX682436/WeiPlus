# WeiPlus C-2A：87 项矩阵与基础功能真机验收

日期：2026-10-29  
仓库：`/Users/muchen/Documents/ChatGPT/WeiPlus`  
基线：`3ea627eaed3fe6bdf9ef4d4a063e3094a9738a3f`  
生产修复提交：`7bbd7acce2e228a4a51ec2b135834d7c0ca705e6`（已推送 `origin/main`）

本轮完整阅读了 8.0.80 总审计、B-R2、C0、C1、C1-R2 文档，并重新核对 FeatureRegistry、FeatureManager、ModuleEntry、DexFinder、WechatApiFeature、SettingsInjector、HookRegistry、DexInstallScheduler。阶段 A 关于按版本号拒绝的建议已过期，本轮未新增版本号/versionCode 白名单；目标能力仍按运行时身份与真实定位处理。

完整逐项矩阵见 [wechat-8.0.80-phase-c2-matrix.md](wechat-8.0.80-phase-c2-matrix.md)。本轮矩阵按当前 FeatureRegistry 的 87 个登记调用生成，顺序、featureId 和策略取自当前源码。

## 1. 验收结论

矩阵结果为 **2 PASS、2 PARTIAL、1 NOT_VERIFIED、1 NOT_ENABLED、81 NOT_TESTED**。只有实际观察到功能效果的项目记 PASS；未启用与未执行项目没有记 FAIL，也没有算兼容 PASS。

- `01 设置入口`：通过微信设置页的 Hchat 行实际进入模块设置，PASS。
- `49 输入框提示`：临时启用后，聊天输入框实际显示“今日已发0条”，没有输入或发送；回滚后配置字节已恢复，PASS。
- `18 资料页显示 ID`：开关和本人资料页路径已观察，但未能确证资料 ID 的注入效果，PARTIAL。
- `87 分支扩展功能`：消息时间标签在聊天 UI 中出现，滚动复用后仍在；消息详情点击和内容格式化未启用，父功能 PARTIAL。
- `02 微信公共 API`：模块 API 102 已加载，但关键定位、Hook 的有效安装状态及真实业务回调没有足够证据，NOT_VERIFIED。
- `50 消息气泡`：原始开关关闭且未配置气泡资源，NOT_ENABLED。
- 本轮其余项目均未做专项功能验收，见矩阵中的条件、依赖和未验证状态。

## 2. APK、设备与 LSPosed scope

### 2.1 APK 来源与签名

确认本地 R2 正式 APK `dist/Hchat-release-signed.apk` 的原始 SHA-256 是用户提供的 `3ab0d185c3f9d3ed2331a46de4dd85cd4e67a05bf137b86dc92b14c3539a66f2`，`apksigner` 验证通过，证书 SHA-256 与用户提供的指纹一致。随后针对本轮定位到的生产缺陷，从该仓库签名配置重新构建 Release，并使用 `adb install -r` 覆盖安装；未卸载模块或微信，也未清除数据。设备上安装的 `base.apk` SHA-256 与本轮最终 APK 完全相同。

最终正式 APK：`dist/Hchat-release-signed.apk`  
SHA-256：`3cde3ec0e81062713dc22352d5e32a2c5ef92858bfa1de8d74ee7ffb6726ec27`  
签名证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`

这是包含本轮修复的正式签名构建；它的文件哈希不同于 R2 原包，但签名证书相同。APK 保留在 `dist/` 作为本轮交付物。

### 2.2 设备和包身份

测试设备为 `22081212C`、Android 15 / API 35。测试对象：

- `com.tencent.mm`：官方微信 8.0.80，versionCode 3220。
- `com.tencent.mm.wx`：修改版微信 8.0.76，versionCode 3140。它是回归对照，不是官方 8.0.76。

测试前读取并保存了 LSPosed 模块 scope 与相关配置。原始 scope 是官方包勾选、`.wx` 未勾选。为运行修改版回归，本轮通过 LSPosed Manager 临时勾选 `.wx`，只启动到登录选择页并读取运行日志；结束时取消勾选，并在页面确认恢复为官方包勾选、`.wx` 未勾选。全程没有登录或访问修改版中的业务数据。官方微信的 30 个 Hchat FastKV 设置文件也在测试前保存；临时切换的 `profile_id`、`conversation_group`、`input_hint` 均通过恢复原始文件回滚。恢复后逐个比较 30 个文件，字节不一致数为 0，并重新冷启动官方微信。

### 2.3 API 102、运行时身份与进程

本轮最终 APK 冷启动官方微信时，LSPosed 记录到 API 102 加载和 `host_identity=approved`，版本为 8.0.80，来源 `tinker_attach_before`。主进程、`:push`、`:appbrand0`、`:appbrand1` 均观察到 API 102 加载及运行身份批准。修改版回归中，主进程和 `:push` 也观察到 API 102 加载；`.wx` 的版本身份被识别为 8.0.76。

运行身份批准使用了当前 Package、版本、APK/Dex 身份和 ClassLoader 身份决策；日志没有输出稳定的 ClassLoader 指纹明细或运行代次切换。因此：

- 已验证主进程在 Tinker attach 前拿到完整 8.0.80 身份并获批准。
- 本轮没有自然观察到 `tinker_attach_after`，也没有人为制造补丁场景，Tinker 前后切换为 **NOT_VERIFIED**。
- ClassLoader 实例/指纹代次变化为 **NOT_VERIFIED**；不能从 `host_identity=approved` 推导已经发生过切换。
- `DexFinder.resolveAll()` 的完成状态不作为关键定位成功证据；关键 locator 必须单独记录。

## 3. 精确生产修复

### 根因

官方 8.0.80 启动时，`HchatExtraFeature` 将完整 `DexMethodCache.runtimeKey` 与用途后缀直接拼为 FastKV key，分别用于聊天数据 adapter 和 class lookup 缓存。该 runtimeKey 对当前 APK 身份足够长，超过 FastKV v3 的 255 字符限制，触发 `key's length must be less than 256`。日志表现为 HchatExtra 安装异常，并使对应的消息显示时间定位/安装调度任务失败。这是确定的缓存 key 长度问题，不是目标类不存在，也不是微信版本不支持。

### 修复与回归

`HchatExtraCacheKey` 将完整 runtimeKey 做 SHA-256，并使用固定短前缀和受限用途后缀构造 FastKV key；空 runtimeKey 时安全跳过持久化缓存。原始定位逻辑不变。新增单元测试覆盖旧 key 超长、生成 key 长度、确定性、runtime/purpose 区分和空 key。

- 定向单元测试：`:app:testDebugUnitTest --tests h.Hchat.hooks.items.hchatextra.HchatExtraCacheKeyTest`，通过。
- Release 构建：`:app:assembleRelease`，成功。
- 修复提交：`7bbd7acce2e228a4a51ec2b135834d7c0ca705e6`，已推送 `main`。
- 本轮最终 APK 的官方 8.0.80 启动没有再出现该 FastKV key 长度异常；聊天消息时间标签 UI 效果也实际出现。`.wx` 启动未出现该异常，但其完整业务 UI 未进入。

这次修复范围只限 HchatExtra 的两类缓存查找键；没有重构 DexFinder、加入无界重试、写死混淆类名或恢复全局版本门禁。

## 4. 公共 API 与 Hook 状态

### 4.1 微信公共 API

`WechatApiFeature` 初始化了消息、数据库、账户、联系人、会话、通知、配置、网络、用户、聊天室、存储、消息解析/事件、本地消息、UI、多媒体、权限、数据库变化、Activity/生命周期、诊断、任务、消息观察、聊天页、版本、转账、身份验证及 SNS helper 等公共 API，并安排 DexFinder 预热和独立补装任务。

官方 8.0.80 日志没有给出本轮需要的逐 API 成功定位、有效 Hook handle 或业务事件证据，因此逐 API 的端到端状态仍是 **NOT_VERIFIED**：

- AddMsg：定位/Hook 生效及真实收到消息回调均未证实；没有发送消息制造事件。
- 数据库 wrapper、消息变更监听：没有确认 wrapper 已 operational，也没有真实数据库消息事件。
- 模块文本发送构造器：未调用，没有文本发送事件。
- SNS 视频 helper：未确认实际定位/安装，没有下载或发布事件。
- `updateGiftMsgByCgi`：仍无可信定位证据；普通 AddMsg 成功不能替代 ECS 礼物兼容证据。

修改版 `.wx` 启动停在登录选择页。初始阶段出现 `未找到 SqliteDB wrapper` 日志，最后一次此类日志在 16:51:10；之后超过一分钟未再出现。朋友圈缓存存储查找仍在 16:52:34 出现告警。由于该安装没有进入已登录会话，也没有可安全触发的业务事件，不能把这两类运行日志解释为 8.0.76 的功能 FAIL；数据库 wrapper 与 SNS 缓存状态均记为 **NOT_VERIFIED / 待登录态场景**。它们是在完整业务 UI 和安全测试账号/fixture 受限时留下的阻断线索，本轮没有据此改动 API 定位代码。

### 4.2 HookRegistry 与异常隔离

静态源码确认 `FeatureManager` 以 `PREINSTALL_HOOKS` / `ENABLED_ONLY` 决定安装策略；每个 feature 的安装异常被捕获并隔离。`HookRegistry.withInstallationGeneration` 对失败或取消的部分安装执行 lease 回滚，`destroyAll()` 先取消调度并使旧安装代次失效，再卸载 hook、调用清理。此为源码行为证据，本轮没有人为注入异常验证。

发行日志不记录活动 Hook 数、每项安装代次或逐项 locator 结果，因此“存在注册句柄”不能等同于 Hook 生效。本轮有效安装代次、hook 总数、逐功能关闭后的动态卸载均为 **NOT_VERIFIED**。

## 5. C-2A 功能效果

所有截图只用于当轮检查，没有把带个人内容的临时截图提交仓库。报告保留截图所支持的观察结论与条件，不把当前设备隐私数据作为附件。

### 5.1 设置入口（01）

从官方微信原生设置页实际找到并点击 Hchat 行，成功进入模块的 Miuix 设置页；可见分类包括“聊天、语音、增强、群组、朋友圈、美化、界面、杂项”。这是该入口的真实 UI 效果，记 PASS。没有把 UI 行出现误当成其他功能已安装。

### 5.2 输入框提示（49）

在美化设置中打开输入框提示，保存默认模板后预览显示“今日已发0条”；返回同一聊天页后，输入框 placeholder 实际显示该文案。没有输入或发送任何内容。结束后恢复原始 30 个配置文件，冷启动官方微信；配置恢复已逐字节核验。提示关闭后的视觉消失没有另做截屏，所以“开关/文件回滚”有证据，恢复后的具体 UI 反向效果未单独记录。

### 5.3 资料 ID、分组和消息详情（18、27、87）

- `18 资料页显示 ID`：临时开启设置并进入本人资料信息页；没有确认模块注入 ID 的独立 UI 效果，未对联系人/群资料做任何变更，记 PARTIAL。
- `27 聊天分组`：开关页可正常显示，也曾临时开启；没有建立分组或给会话分类，首页没有可确认的归组投影效果，最终记 NOT_TESTED。配置已按快照回滚。
- `87 分支扩展功能`：设置页中“消息显示时间”原始状态为开启，“点击显详情”和“内容格式化”关闭。进入现有聊天页后看到红色消息时间标签；滚动列表并让行复用后仍看到标签。没有点消息详情、修改/删除消息或改变数据库。仅该子功能 UI 效果已验证，父功能记 PARTIAL。不得把它归入 #48“会话时间样式”。

### 5.4 其他首批项目

`03` 未按发送按钮，`04` 未打开浮动快捷菜单，`05` 未启用自定义底栏。`28` 未准备安全的已标记会话；`48` 未单独切换会话时间样式。`50` 开关原始关闭且没有气泡资源；`51`、`52`、`53` 未切换颜色或隐藏头像。这些项目的真实 locator/Hook 状态没有独立日志，均按矩阵记为未验收/未启用，不计 PASS。

## 6. 性能、缓存与运行期边界

### 6.1 冷/热启动

在最终正式 APK、原始官方 Hchat 设置下，使用 `am start -W` 采集到：

- 冷启动 TotalTime：926 ms、1000 ms、1079 ms（不同启动轮次）。
- 热启动已在前台：TotalTime 0 ms、WaitTime 7 ms。
- 从后台回到已运行进程：TotalTime 224 ms、WaitTime 230 ms。

这是单设备启动活动耗时，不是应用性能基准。测试手机为 Android 15，受设备当时状态和缓存影响。

### 6.2 APK 身份缓存、DexKit 与 Native idle

当前 `DexArchiveIdentity` 使用有限 128 项进程内 LRU，键为规范路径与 file key/dev/inode/size/mode/纳秒 mtime/ctime；同一文件快照命中会直接复用 ZIP central directory 元数据指纹，不读 APK/DEX payload。`ModuleEntry` 在 EARLY 阶段预热 APK 身份，`WeChatVersionApi` 后续调用使用同一服务。源码审阅未发现回退为每个 runtimeKey 都重新遍历 ZIP 的逻辑；本轮运行日志没有输出缓存 hit/miss 计数，所以不能声称测得了设备侧的 cold/hot 命中次数。

DexFinder 的冷/热缓存命中、Native DexKit 30 秒空闲回收及实际资源释放量均没有发行版遥测，记 **NOT_VERIFIED**。不依据 `resolveAll()` 完成推导这些状态。

## 7. 安全边界、恢复与未验证项

本轮只做只读 ADB 检查、页面导航/截图、LSPosed scope 临时切换和可恢复的视觉设置切换。没有发送消息、改联系人、撤回/删除消息、修改微信数据库、操作红包/转账、改变群成员、点赞评论或发布朋友圈。没有卸载微信/模块或清除应用数据。

以下项目留给后续阶段：

- AddMsg/数据库事件/文本发送/SNS 发布或视频相关 API 的真实业务闭环。
- 更多安全数据集下的功能项 locator、Hook install 与关闭恢复验收。
- `.wx` 登录态下的数据库 wrapper 与 SNS 缓存 helper 回归；当前只能确认包元数据、API 102 主进程/`:push` 加载、运行身份识别及登录选择页状态。
- 自然 Tinker 切换、ClassLoader 代次细节、DexFinder/Natives 30 秒空闲回收、HookRegistry 活动句柄和逐 feature 安装代次遥测。

本轮到 C-2A 为止；未开始 C-2B、C 或 D。
