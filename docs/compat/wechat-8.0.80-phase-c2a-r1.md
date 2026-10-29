# 微信 8.0.80 C-2A R1：基础功能验收收口

- 基线：183fd9711b32cb3b429af1b8cd88275c8fce1328
- 日期：2026-10-29
- 验收目标：修复 ProfileIdFeature 的安装重试与代次恢复；补齐 C-2A 首批 15 项的安全专项观察。
- 本轮没有进入 C-2B。

## 1. 结论

87 项登记数、顺序和矩阵行与当前 FeatureRegistry 的 87 个注册项逐项一致。矩阵中的首批结果为：

| 状态 | 数量 | 项目 |
|---|---:|---|
| PASS | 8 | 01、04、05、28、48、49、51、53 |
| PARTIAL | 2 | 02、87 |
| NOT_VERIFIED | 4 | 03、18、27、52 |
| NOT_ENABLED | 1 | 50 |
| NOT_TESTED | 72 | 其余登记项 |

PASS 只表示本报告注明的真实页面效果已观察到。真机视觉项是在当时已安装的正式签名 R2 APK 上完成；不是 R1 新 APK 的运行结果。R1 生产代码修复已通过 Debug 回归，但签名凭据缺失，导致 R1 Release APK 无法生成，因此没有安装 R1 包，也没有把 R2 运行结果冒充成 R1。

## 2. ProfileIdFeature 生命周期与重试修复

### 根因与复现

旧实现用静态 installed 布尔值短路后续安装。好友资料页类和群资料页类首次定位失败后，控制流仍会把该值设为 true；当同一 ClassLoader 后来可以解析目标时，下一次 install 直接返回，两个目标都无法重试。

先在未修改的生产控制流上运行回归：模拟目标首次不可用、随后变为可用；旧控制流无法重新定位并安装。该回归先按预期失败，修改后再运行通过。

### 修复

- 删除永久 installed 标志；install 分别返回好友资料与群资料的安装结果。
- 两类目标各自通过 DexInstallScheduler 的 WARMUP 阶段有限重试，按功能、目标种类和 ClassLoader 身份隔离任务；调度器每轮最多 6 次重试，失败不会形成无界后台循环。
- 成功时取消对应重试。功能销毁时取消两类待处理任务。
- 所有 Hook 通过 HookRegistry.hookOnce 使用稳定键；仅将 HookRegistry 仍有效的句柄视为已安装。重复 install 复用有效句柄；句柄撤销后，下一次 install 能在新安装代次重新挂钩。
- 群资料的 initView 与 Preference 点击 Hook 分别安装；任一失败不会被好友资料 Hook 或另一个群资料 Hook 掩盖。已成功的部分通过 hookOnce 避免重复回调，后续重试只补缺失项。
- 沿用现有目标类定位常量，没有增加混淆类名；未读写微信资料数据。

### 回归覆盖

新增 ProfileIdFeatureLifecycleRegressionTest，直接走 ProfileIdFeature.install 与 HookRegistry/DexInstallScheduler 生产控制流，覆盖：

1. 两类目标首次缺失、后来出现后可以安装。
2. WARMUP 重试在目标变为可用后安装。
3. 好友 Hook 成功不掩盖群资料目标缺失；之后只补上群资料缺少的 Hook。
4. 重复安装保持 3 个有效句柄，不增加重复回调；HookRegistry 失效并进入新代次后可重新安装。

目标状态：未命中时注册 0 个句柄；完整安装时好友 initView 及群资料两项共 3 个有效句柄。定向回归在旧实现上复现失败，在修复后通过。

## 3. 真机环境、APK 与运行时证据

| 项目 | 结果 |
|---|---|
| 设备 | serial feb70d35；Xiaomi 22081212C；Android 15 / API 35 |
| 官方测试包 | com.tencent.mm，versionCode 3220（8.0.80） |
| 对照包 | com.tencent.mm.wx，versionCode 3140（修改版 8.0.76）；本轮未启用其 LSPosed scope，也未将其视为官方版本 |
| LSPosed scope | 原始状态保持：官方包启用、修改版禁用；本轮没有改动 scope |
| 已安装模块包 | 之前的正式签名 R2 APK，SHA-256 为 3cde3ec0e81062713dc22352d5e32a2c5ef92858bfa1de8d74ee7ffb6726ec27；证书 SHA-256 为 5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd |
| 本轮 R1 APK | 未生成、未安装；签名配置没有提供 HCAT_STORE_PASSWORD、HCAT_KEY_ALIAS、HCAT_KEY_PASSWORD。app/build.gradle.kts 在缺少 HCAT_STORE_PASSWORD 时拒绝配置 Release。没有尝试卸载或清除应用数据。 |

LSPosed 日志在官方包冷启动期间记录到 API 102 加载和 host_identity=approved。该证据只说明模块入口被加载，不代表某个 API 定位成功或回调生效。

DexKit 正常预热及重试时间窗后，正式 APK 没有公开逐项成功日志。日志中没有可核验的 resolveAll 完成标记、AddMsg/数据库 wrapper/文本请求/SNS helper 的定位成功记录或有效句柄明细。DexFinder 正式构建关闭详细成功日志，WechatApiFeature 也不输出逐项成功状态。因此这些项目统一保留 NOT_VERIFIED，不以 resolveAll 返回或注册句柄代替定位与效果证据。

- AddMsg、数据库 wrapper、消息变更监听、模块文本请求构造器、SNS 视频 helper：逐项定位与有效 Hook 状态 NOT_VERIFIED；没有人为制造消息、数据库变更、文本发送或朋友圈事件。
- updateGiftMsgByCgi：没有可信定位证据；不从普通 AddMsg 能力推断 ECS 礼物兼容。
- 原生发送按钮：没有可见的逐项定位/有效 Hook 日志；未点击发送或发送消息。
- Tinker 自然切换：本轮没有观察到前后状态变化，标记 NOT_VERIFIED；没有制造假补丁场景。
- 运行时 ClassLoader 稳定身份、DexKit 冷/热命中和 HookRegistry 实际运行代次：正式 APK 日志未提供可核验的逐项状态，设备侧标记 NOT_VERIFIED。单测另行验证了 HookRegistry 句柄失效和新代次恢复。
- 视觉验收使用旧 R2 包，故不声称观察到 R1 新代码在设备上的安装代次。没有伪造 Native 空闲回收或热恢复测量结果。

## 4. 首批 15 项专项记录

### 4.1 #01 设置入口 — PASS（沿用 C-2A 证据）

之前已在官方微信设置中点击 Hchat 入口并进入模块设置；本轮未重复整套操作。设置入口 UI 效果已证，逐项 locator/有效句柄仍未单独记录。证据：wechat-8.0.80-phase-c2a.md §5.1。

### 4.2 #02 微信公共 API — PARTIAL

测试条件为官方 8.0.80 主进程冷启动，并等待常规预热/重试时间窗。实际观察到 API 102 加载和 host_identity=approved；AddMsg、SQLite wrapper、消息变更监听、文本请求构造器、SNS 视频 helper 的逐项定位与有效句柄状态没有正式日志可证。没有真实业务事件，所以没有端到端回调证据。未切换公共 API 设置，也未修改微信数据。

### 4.3 #03 原生发送按钮 — NOT_VERIFIED

进入现有聊天页只用于确认可访问条件；本轮没有输入或发送消息。日志没有目标 locator 和 Hook 有效状态，不能判断是定位失败、安装失败或仅未触发回调。没有设置恢复项；没有产生消息。

### 4.4 #04 悬浮快捷菜单 — PASS

在官方微信首页点击浮动入口打开菜单，关闭菜单后返回原有聊天页面；关闭功能后浮动入口消失。真实开/关 UI 差异已观察。最终 FastKV 快照恢复了原始设置字节。

### 4.5 #05 自定义底栏 — PASS

临时配置首个底栏标题为 R1；离开设置后回到微信首页，底栏显示新标题；再次进入首页仍显示；关闭功能后原生底栏恢复。未做进程冷启动复测。临时配置由 30 文件快照恢复。

### 4.6 #18 资料页显示 ID — NOT_VERIFIED

本轮没有在 R1 包上进入好友或群资料页：R1 APK 未能签名生成，旧 C-2A 使用本人资料页的观察不符合验收条件，不能沿用为 PASS。好友/群资料 locator、Hook 安装及 UI 注入均没有本轮 R1 设备证据。ProfileId 配置文件已按原始快照恢复；本轮未修改资料数据。需有 R1 正式包后，在现存好友或群资料页复验。

### 4.7 #27 聊天分组 — NOT_VERIFIED（安全阻断）

查看了现有安全观察条件与生产实现。ConversationGroupRuntime 会创建/更新虚拟会话行并改写会话父级引用，功能测试会触及微信会话数据库；用户明确禁止本轮变更数据库，因此没有启用或创建分组。没有 locator、Hook、UI 效果结论；原设置与 30 文件快照一致。阻断原因是明确的业务数据安全边界，不是推断功能失败。

### 4.8 #28 标签分组 — PASS

使用功能内置默认标签，不创建自定义标签、不改联系人标签或微信数据库。首页显示“全部/未读/群聊/私聊/公众号”分栏；选择“私聊”后，选中态和现有会话过滤效果可见。关闭后重新返回首页，分栏消失并恢复微信原始会话列表。配置最终按原始快照恢复。

### 4.9 #48 会话时间样式 — PASS

独立启用“每条消息显示时间”样式，在已有聊天中观察到逐条灰色时间；关闭后恢复“跟随微信显示（间隔）”。这是独立测试，没有把 #87 的消息详情点击当作 #48 的证据。配置快照恢复。

### 4.10 #49 输入框提示 — PASS（沿用 C-2A 证据）

此前已观察到输入框提示 UI，本轮不重复整套测试。提示 UI 有效果证据；真实发送统计准确性需要消息发送事件，本轮禁止发送，继续 NOT_VERIFIED。证据：wechat-8.0.80-phase-c2a.md §5。

### 4.11 #50 消息气泡 — NOT_ENABLED

原始配置关闭且没有可用资源，本轮未强行开启。没有 Hook/UI 效果结论；不计兼容 PASS，也不报 FAIL。原设置保持关闭。

### 4.12 #51 消息文本颜色 — PASS

在已有聊天中把指定消息文字临时设为红色；滚动列表并触发复用后，目标消息仍显示红色。关闭功能并重新进入聊天，消息恢复微信原生文字颜色。临时配置按原始快照恢复。

### 4.13 #52 首页文字颜色 — NOT_VERIFIED

临时设置会话标题为蓝色、摘要为洋红色并查看首页现有会话行；实际颜色未变化。随后关闭功能、恢复配置。因本轮没有逐项 locator/Hook 证据，无法定位问题阶段，故记录为 NOT_VERIFIED，不把无效果直接归因于微信目标失配或安装失败。

### 4.14 #53 隐藏头像 — PASS

在现有私聊中隐藏对方头像；对方头像消失、自己的头像保留。滚动/复用后效果仍保持。关闭并重新进入聊天后双方头像恢复。没有改联系人资料；临时设置按快照恢复。

### 4.15 #87 分支扩展功能 — PARTIAL

在已有消息上点击 #48 时间样式产生的时间项，打开只读消息详情展示。只查看详情，没有使用编辑、复制或任何会改消息的操作。随后关闭消息详情点击项并恢复原始时间样式设置。仅消息详情的安全只读子功能有 UI 证据；分支扩展其他能力没有因此通过，整体保留 PARTIAL。

## 5. 测试、Lint 与 Release/R8

| 验证 | 结果 |
|---|---|
| ProfileIdFeatureLifecycleRegressionTest 定向回归 | 通过；旧控制流先复现失败，修复后通过 |
| :app:testDebugUnitTest | BUILD SUCCESSFUL；95 tests，0 failures、0 errors、0 skipped |
| 全仓库 scripts/run_*.cjs | 29/29 通过 |
| PhaseBSourceRegression.cjs | 10/10 通过 |
| :app:lintDebug、:app:lintRelease | BUILD SUCCESSFUL；各有 646 warnings、50 hints，无 lint error |
| :app:minifyReleaseWithR8 | BUILD SUCCESSFUL |
| assembleRelease | 失败于配置阶段：缺少 HCAT_STORE_PASSWORD；其余 HCAT_KEY_ALIAS、HCAT_KEY_PASSWORD 也未提供 |

为使全量回归可运行，只修正了两个测试辅助文件：性能脚本补齐编译夹具缺失的 WeChatLvBufferMsgSource stub；Phase B 源码检查改为精确截取 onPackageReady 方法体，避免把后续的 C-1 预热辅助逻辑误判成该方法的副作用。没有修改对应生产逻辑。

正式 Release/R8 代码缩减阶段已通过，但未生成签名 APK，因而没有新的 R1 APK SHA-256，也没有执行安全覆盖安装。现有旧 R2 APK 的证书指纹与用户给定证书一致；它的 SHA-256 是本节前列的 3cde…，不能作为 R1 产物散列。

## 6. 设置、scope 与隐私清理

- 将原始 30 个 FastKV 文件（15 组 kva/kvb）逐字节回写；回写后 30/30 相同，官方微信冷启动后再次核验仍为 30/30 相同。
- LSPosed scope 未修改：官方 8.0.80 包启用、修改版 8.0.76 包禁用。
- 未发送消息、修改联系人资料、修改微信数据库、支付、收红包或发布朋友圈。
- 本轮临时截图、UI hierarchy 和设备验证归档已删除；不在仓库保留原始隐私数据。

## 7. 未完成边界与后续动作

当前代码、单测、全仓库回归、Lint 与 R8 检查可供独立验收。缺少既有签名配置是本轮真机 R1 验收的阻断条件。要完成 R1 安全覆盖安装并在正确好友/群资料页验证 #18，需要在本机恢复原有 HCAT_STORE_PASSWORD、HCAT_KEY_ALIAS、HCAT_KEY_PASSWORD（以及需要时的 HCAT_KEYSTORE_PATH），随后重新构建、核对证书与 APK SHA-256、覆盖安装并复验。请勿在聊天中发送签名密码。

此报告收口于 C-2A R1；不自动开始 C-2B。
