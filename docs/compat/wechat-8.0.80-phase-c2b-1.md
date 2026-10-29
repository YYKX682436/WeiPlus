# WeiPlus 微信 8.0.80 C-2B-1：消息观察就绪时序

日期：2026-10-29
基线：`e225b9a115218e790de19bdbbbd7d32441b8d506`
范围：AddMsg、统一消息观察器、消息变更数据库 fallback 及其订阅生命周期。C-2A R3 已结束；本轮没有改首页文字颜色或其他已验收视觉功能。

## 基线失败

生产安装顺序先调用 `WeChatMessageObserveApi.install()`，之后才由 `wechat_api:addmsg_observer` 的 WARMUP 任务解析并安装 AddMsg Hook。初次调用时 AddMsg locator 未就绪，观察器只保留可用的数据库 fallback，不会订阅 PB 事件。基线 WARMUP 任务成功安装 AddMsg Hook 后没有再次运行观察器安装，所以 PB 层始终没有订阅。

先新增 `WeChatMessageObserveReadinessRegressionTest.addMsgWarmupSuccessResubscribesTheProductionObserverAfterInitialMiss`，让真实 `WechatApiFeature` WARMUP 调度入口、生产 Event API、生产 Observe API 和模拟的宿主 AddMsg 方法按该顺序运行。修复前，Hook 的 post-process 边界被调用后统一观察器收到 **0** 个事件（期望 1），测试按预期失败。这复现了延迟安装缺口。

## 修复与状态转换

AddMsg WARMUP 安装任务抽为包内生产入口，由真实初始化路径和回归共同使用。任务现在按以下顺序执行：

1. 定位 AddMsg；定位失败时返回失败，让现有有限重试继续。
2. 安装或复用有效 AddMsg Hook，并确认当前代次仍 operational。
3. Hook 有效后调用 `WeChatMessageObserveApi.install()` 补订阅 PB 消息与拍一拍事件。
4. 只有 Hook 与 PB 订阅都有效时，WARMUP 任务才报告成功；PB 暂不可用时不会误报就绪。

数据库监听继续作为 PB 不可用时的 fallback。PB 就绪后，已有筛选和去重路径仍负责避免入站消息重复观察；数据库 fallback 仍处理既有的方向和变更类型规则。消息去重、发送方向、消息来源、LVBuffer 只读补全和嵌套数据库写入去重均保留。

`WeChatMessageObserveApi` 现在持有 EventBus 消息/拍一拍订阅凭证及数据库变化订阅凭证。API 公开的业务监听凭证也被精确跟踪。`WeChatApis.clear()` 销毁观察器、消息变化 API 和 AddMsg Event API，并逐项取消其订阅。HookRegistry 代次撤销时，注册的清理回调会清除该代次的 Hook 引用、业务 listener、去重缓存和订阅凭证；代次绑定的回调会拒绝旧事件。重新装 Hook 不会复活旧业务回调，需要下游在新代次重新订阅。

EventBus 订阅现在对应唯一凭证。取消其中一个对同一回调对象的订阅不会误删另一个；撤销后的 token 也不会从并发派发快照再次调用。移除了观察器内不能表达 PB 就绪事实的 `installed`、`pbLayerActive`、`dbLayerActive` 状态，PB 和数据库状态改由有效句柄与活动凭证计算。

观察器就绪诊断按状态变化记录 `locator`、`hook`、`subscription`、`event`、`db_subscription`、`db_hook`。诊断与目标链错误日志不记录聊天正文、XML、联系人身份或真实消息 ID；异常只记录异常类型。真实事件层只有收到事件后才变为 `observed`。

## 回归验证

修复后，生产链时序回归与数据库监听既有回归通过；覆盖项包括：

- 初始 AddMsg locator miss 后重试成功，随后 PB 观察器补订阅并只派发一次模拟事件。
- 重复 DexReady、重复 install、同一 AddMsg 重放时 Hook 注册与消息派发不重复。
- 数据库 fallback 在 PB 未就绪时继续工作；PB 就绪后相同入站消息不会由数据库路径再次派发，输入 `ContentValues` 不变。
- 取消 AddMsg 安装任务、HookRegistry 代次切换、显式销毁和精确订阅凭证取消后，旧观察器/监听器不再派发；同一 EventBus/RawAddMsg 回调的凭证可分别取消。
- 无效用户名、空消息、缺失存储结果、解析失败和抛异常的业务回调不会逃出宿主 Hook 边界。
- `com.tencent.mm` 与 `com.tencent.mm.wx` 的入口作用域、运行身份和 Dex 缓存身份分别校验，不会交叉复用。
- 既有数据库回归继续覆盖嵌套 wrapper/WCDB 写入只派发一次、LVBuffer 只读补全、失败写入不派发及代次撤销后重新订阅。

修复后 `:app:testDebugUnitTest --tests h.Hchat.hooks.api.core.WeChatMessageObserveReadinessRegressionTest --tests h.Hchat.hooks.api.runtime.WeChatDatabaseListenerApiRegressionTest` 通过。Debug 全量单测共 107 项通过；测试 JVM 按测试类独立 fork，以隔离已有 Robolectric `XposedRuntime` 静态框架夹具。仓库 `scripts/run_*_tests.cjs` 的 29 个回归脚本全部通过；性能回归脚本的生产源码夹具同步增加了本轮新增 API 的最小测试替身。

## APK 结构核对与边界

官方微信 8.0.80 样本 `/Users/muchen/Downloads/weixin8080正式版.apk` SHA-256：`c35a05d5c13bd43cf1f7ff42c13fab070d21509ab2e233596f776d1afcae52f5`。DexSQL 静态核对到 AddMsg 处理入口 `Lcom/tencent/mm/plugin/messenger/foundation/a2;->b(Lcom/tencent/mm/modelbase/p0;Lgd5/e55;Lgp3/e5;)Lcom/tencent/mm/modelbase/q0;`；配套预处理器随后读取 `q0.a:Lcom/tencent/mm/storage/f9;`。数据库 wrapper `Lai5/d0;` 位于 `classes15.dex`，具有 57 个方法；该类可见 3 个 insert 变体、1 个 update 与 1 个 delete。此结果核对了 locator 使用的结构锚点，不证明设备上的 locator 命中或有效 Hook。

设备当前可读到的修改版 8.0.76 样本 SHA-256 为 `15a9d50494ea583cb7cc2a214f0d14710fd7141a4f77a715c26a4a69855f2d03`。该 APK 的代码 DEX 仅含 LSPatch loader 入口（10 个类、93 个方法），没有可用于 AddMsg 或数据库 wrapper 对照的微信业务实现。本仓库旧兼容记录中的另一修改版 8.0.76 样本哈希为 `19b71802548d9a30d87c344fc0457270b1e7f2ceefdcfad4f15386fcab62c6b4`，不是本轮读取到的样本，不能把旧样本的结构结论冒充为当前 APK 对照结果。

本轮未安装或启动微信，未发送消息、修改联系人、写数据库、支付、发布朋友圈或变更群成员。没有真实消息事件时，AddMsg 运行定位、实际 Hook 回调、统一观察器真机订阅及真实事件仍为 **NOT_VERIFIED**。生产 API 的模拟回归不将 87 项矩阵中的公共 API 或自动回复、自动转发、红包、转账、通知、朗读、群事件、防撤回等下游消费者标为真机 PASS；这些项目保持原矩阵状态。本轮已检查的消费者包括 AutoReply、AutoMessageForward、AutoTransfer、RedPacketHooker/Gift/RedPacketMessage、AntiRecall、GroupLeaveMonitor、MusicOrder、Keyword/Custom/AtAll 通知、TextSpeech 与 RealNameTail 等；没有触发任何真实业务动作。

## 构建与签名

`./gradlew :app:lintDebug :app:lintRelease assembleRelease`：**BUILD SUCCESSFUL**。Debug/Release Lint 均通过；输出保留仓库既有 Kotlin `firstOrNull()` 选择告警及其他既有编译警告，没有关闭检查或添加抑制。`:app:minifyReleaseWithR8` 成功。

- Release APK：`dist/Hchat-release-signed.apk`，包名 `h.Hchat`，版本 `1.0.0`。
- APK SHA-256：`1c01cbb1273bf61d35dc53407eae97efd2418ce7b3230d5e70130c276de71408`。
- `apksigner verify --verbose --print-certs`：验证通过，使用 APK Signature Scheme v2，1 个 signer。
- 签名证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`，与既有正式签名一致；使用现有 MUCHEN.jks，未更换证书。
- APK 未安装，未操作微信。
