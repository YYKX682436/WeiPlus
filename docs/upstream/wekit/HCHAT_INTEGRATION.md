# Hchat 接入地图：WeKit 功能移植前置基线

## 1. 适用范围与证据等级

- Hchat / WeiPlus 基线：`332c9650103fa68fc5f755d4c2d8cef4502d0c34`，分支 `main`。
- 对照 WeKit 基线：`650febaa3717eddd39f5f29a4dfa6dfd37875cb1`，分支 `master`。
- 日期：2026-11-06（Asia/Shanghai）。
- 本文由仓库静态阅读生成：读了入口、Feature 生命周期、Hook 所有权、配置与 UI 路由、共享 API、缓存/预热、现有功能注册表、相关修复代码与历史验收材料。**没有运行构建、自动测试或真机；不是 89 个登记项的逐个正确性审计。**
- 注册表实际为 **89 个 Feature**，含公共 API、设置入口、发送入口等基础设施；不宣称有 89 个开关，也不宣称全部行为已验证。
- 所有文件与行号以下列固定提交为准。未来窗口先读最新 `AGENTS.md` 与 `skills/hchat-project/SKILL.md`，再比对当前 HEAD。**当前源码/构建配置和当前用户指令优先于旧架构文档。**

已有项目规范：只维护 `main`；目标安装包只有 `com.tencent.mm` 和 `com.tencent.mm.wx`；只能使用 libxposed API 102。宿主内部类名与安装包名不是一回事，修改版包名下内部类仍可能叫 `com.tencent.mm.*`。禁止卸载微信或清空微信数据。[项目技能](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/skills/hchat-project/SKILL.md#L1-L42)；[包名规则](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/WechatPackage.kt#L3-L23)。

旧 `docs/FEATURE_FRAMEWORK.md` / `docs/WECHAT_APIS.md` 适合辅助检索，不作为无条件执行指令；例如旧架构说明中的 SDK 信息不能覆盖当前构建配置。当前事实是 minSdk 27、compileSdk / targetSdk 37、arm64-v8a、Java 21、`compileOnly("io.github.libxposed:api:102.0.0")`，版本为 1.2.2 / 261106。[构建配置](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L9-L25)；[依赖](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L142-L175)；[版本](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/gradle.properties)。

## 2. 从入口到业务功能的真实调用链

1. `ModuleEntry.onModuleLoaded()` 初始化 `XposedRuntime` 并取得进程名。框架接口在这个时点之后才能使用。
2. `onPackageReady()` 用 `WechatPackage.isModuleEntryScope()` 过滤安装包和其进程命名空间，然后先安装关键早期 Hook，再调度 APK 归档身份预热。
3. `installPackageHooks()` 拒绝 32 位进程；所有目标进程安装确有需要的早期热更新/平板入口。普通业务仅在主进程；`:appbrand*` 走最小运行时，安装已明确列出的定位/小程序广告等功能，不能把全部业务初始化直接放进子进程。
4. 主进程在 `Application.onCreate()` 结束后核实当前宿主身份，结合 Tinker attach 后的 ClassLoader，调度 `initModule()`。底栏、标签分组等确有首帧需求的入口有独立早期路径。
5. `initModule()` 建立共享 DexKit / DexFinder / ConfigStore / EventBus / UIRegistry，创建 `FeatureContext`，执行 `FeatureRegistry.createDefaultManager().installAll()`。
6. `FeatureManager` 先初始化功能、登记设置，再按策略安装；必需核心 API 未就绪会阻止后续功能，普通失败按功能隔离。安装操作绑定 Hook 代次和 Feature 所有者；Tinker 变化时按受影响类和 API 代次重新验证。

源码锚点：[ModuleEntry 入口](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ModuleEntry.java#L82-L144)、[进程分流与 Application](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ModuleEntry.java#L339-L441)、[appbrand 最小运行时](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ModuleEntry.java#L562-L586)、[完整初始化](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ModuleEntry.java#L789-L845)、[FeatureManager](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/FeatureManager.kt#L34-L217)。

**接入点是 `FeatureRegistry`，不是名为 HookItems 的注册文件。**新增真正独立的功能时，通常只需要一个局部 `hooks/items/<feature>/` 包和一次 `.register(...)`，不应重新搭建模块入口，也不应把 WeKit 的整个加载器放进 Hchat。[注册表](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/FeatureRegistry.kt#L94-L192)。

## 3. WeKit 基础设施应如何落到 Hchat

| WeKit 概念 | Hchat 已有接入点 | 移植决策 |
|---|---|---|
| `BaseFeature` / `SwitchFeature` / `ClickableFeature` 对象、KSP 自动发现 | `Feature`、`BaseFeature`、`FeatureRegistry` | 把实际业务行为改写为 Hchat Feature；确定已有功能能否扩展，再决定是否新增注册。不要把 WeKit KSP 注册器和对象启停系统一起引入。 |
| `technicalId`、资源名、`categoryIds` | `featureId()`、`FeatureSettingsProvider`、显式导航分组和页面路由 | Hchat ID、显示名称、设置 key 分开；不因为上游改名就清空用户配置。 |
| `WePrefs` / MMKV / `prefOption` | `ConfigStore` + `HchatStorage`（FastKV，SharedPreferences 接口），不少业务有独立 `*Settings` | 新功能按 Hchat 约定存配置；不为一个功能引入第二套全局偏好层。 |
| `hookBefore` / `hookAfter` / 自定义 `HookParam` / legacy API | 官方 `XposedInterface.Hooker` / `Chain` + `HookRegistry` / `XposedRuntime` | 明确转换为前处理、一次宿主调用、后处理；不得连带搬入旧 API 适配层。 |
| `IResolveDex` / `dexMethod` / `dexClass` / `DexCacheManager` | 共享 `FeatureContext.dexKitBridge()`、已有 `DexFinder`、局部 Locator + `DexMethodCache`、`DexInstallScheduler` | 重用已有结构定位；只有新增语义成员才加局部 Locator 或确实共享的 DexFinder 能力，保持缓存键、描述符验证和调度所有权。 |
| `reflekt` / WeKit 自己的反射辅助类 | `KavaReflector` / 标准反射，kavaref 依赖 | 逐个 API 对照，不能仅替换 import；不要把 WeKit 子模块整体复制进项目。 |
| `WeMessageApi` / `WeDatabaseApi` / `WeContactApi` 等 | `WeChatApis` 持有的消息、联系人、DB、媒体、Sns 等服务 | 以 Hchat 共享 API 为默认；若缺一项能力，补最小共享 API，不复制整份 WeKit API 大类。 |
| 全局 Activity/消息/菜单事件 | `WeChatApis.lifecycle()`、`chatPage()`、`messageObserve()`、相应菜单定位/分发器 | 订阅现有入口，按 Feature 跟踪并释放，不在每个功能重新安装全局生命周期 Hook。 |
| Material 3 设置 Activity / 弹窗族 | `ui/miuix/`、`PageScaffold`、`SettingsCard`、`SwitchRow`、共用 Picker | 保留 Hchat 设置结构；不为每个上游功能新增一套独立 Activity 和导航。 |
| WeKit Rust/JNI、Zygisk、Java/Python 扩展包 | Hchat 当前 ARM64 APK、已有 DexKit / Silk / 工具设施 | 只有所选功能确实依赖且方案已决定时，单独评估最小依赖；它们不是“复制几个 Kotlin 文件”的隐含前置项。 |

WeKit 框架差异证据：[BaseFeature](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/features/core/BaseFeature.kt#L25-L106)、[SwitchFeature](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/features/core/SwitchFeature.kt#L7-L62)、[项目架构说明](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/AGENTS.md)。Hchat 对应证据：[Feature](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/Feature.kt)、[BaseFeature](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/BaseFeature.kt)、[FeatureContext](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/FeatureContext.kt)。

## 4. Hook、缓存与调度必须保留的合同

### 4.1 Hook 调用语义

- 常规放行路径 `Chain.proceed()` 恰好一次；明确接管/拦截路径为零次。改参数先复制数组；不要持有 Chain 跨线程或逃出当前回调。
- 观测/绘制后处理可以用局部 `HookErrorBoundary` 隔离模块异常；宿主 `proceed()` 必须位于该边界之外，使宿主异常保持原样。
- `XposedRuntime` 当前使用 `PASSTHROUGH`，处理发送/写入等副作用路径不能靠全局改成 PROTECTIVE 兜底，否则可能在接管失败后意外放行原操作。
- `HookRegistry.hookOnce()` 防同键重复安装、记录代次/所有者。新功能不要绕过注册表保留一份私有永久 Hook。
- 批量 Hook 在全部安装完成前不执行业务；撤销先失效回调，框架撤销失败时保留待清理句柄，不能直接清空句柄列表后假装撤销成功。

来源：[项目技能](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/skills/hchat-project/SKILL.md#L10-L24)、[HookErrorBoundary](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/HookErrorBoundary.java#L5-L18)、[XposedRuntime](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/XposedRuntime.java#L39-L163)、[HookRegistry](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/HookRegistry.kt#L23-L154)。

### 4.2 开关、生命周期与异步任务

默认策略 `PREINSTALL_HOOKS` 是安装轻量外壳，在业务回调检查实时配置；仅确有早期语义需求才采用 `ENABLED_ONLY` 或注明重启。开关关闭时不能继续异步发送或写数据；取消订阅、任务、Handler 回调、监听器与持有的 View。`BaseFeature.trackSubscription()` / `replaceTrackedSubscription()` 可管理订阅，但 Feature 自己创建的线程/任务/资源仍须主动销毁。`FeatureManager.destroyAll()` 会先取消调度并失效 Hook 代次，再逆序清理 Feature。[BaseFeature](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/BaseFeature.kt#L13-L125)、[FeatureManager 清理](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/FeatureManager.kt#L194-L216)。

现有共享消息观察可配合 `MessageObserveLease.sync()` 按开关绑定、替换 API 代次，并用 `unsubscribe()` 销毁；不把 `isAvailable()` 简单等同于真实事件已到达。[消息租约](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/MessageObserveLease.kt#L6-L80)、[观察器就绪诊断](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/api/message/WeChatMessageObserveApi.java#L401-L477)。

### 4.3 DexKit / DexSQL 分工

- **Android 运行时：DexKit。**`ModuleEntry.createDexKitBridge()` 以当前 APK 身份复用托管桥，空闲 30 秒释放 native 分析资源；`third_party/dexkit-memory/` 是本地补丁源码。单个功能不得自建另一套长期存活 DexKit，引入不相关 native 库或直接关闭共享桥。[桥建立](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ModuleEntry.java#L952-L976)、[补丁说明](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/third_party/dexkit-memory/README.md#L1-L26)。
- **桌面静态分析：DexSQL。**既有 `docs/compat/wechat-8.0.80-audit.md` 记录了 Mac 上 DexSQL v0.1.1 的安装与 MCP 握手及 APK 分析；这些只是历史环境证据，未来 Codex 仍须检查本机工具是否可用。DexSQL 没放进仓库或模块 APK，不能用它替代 Android DexKitBridge。[静态分析工具边界](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/docs/compat/wechat-8.0.80-audit.md#L32-L65)。
- `DexInstallScheduler` 串行处理 EARLY/BRIDGE/WARMUP 阶段，记录 Feature 所有者，做有界重试与取消。新定位任务通过该设施进入，不在 UI 回调里跑全量扫描，不另造无限重试线程。[调度器](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/DexInstallScheduler.kt#L16-L77)、[撤销与重试](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/DexInstallScheduler.kt#L102-L234)。
- `DexMethodCache` 缓存描述符，恢复时用当前 ClassLoader 解析；运行时键来自 `WeChatVersionApi.buildCacheKey()`。`DexCacheIdentity` 要求完整包/APK/DEX/活动补丁代码身份，版本字段用于身份诊断，**不是模块全局准入门槛**。不要为了移植而恢复“只允许一个 versionCode”总开关。[方法缓存](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/dexkit/DexMethodCache.kt#L11-L65)、[缓存身份](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/dexkit/DexCacheIdentity.kt#L5-L24)。
- 上游注释、字符串匹配、反编译类名只能构成候选。必须在用户目标 APK 上核实签名/结构/调用边/候选唯一性；缓存命中后也要确认运行时成员结构。不要把某版混淆名直接推广成全版本规则。

## 5. 设置接入必须走通的五个环节

1. **配置实体和 key**：同一个 `*Settings` 声明 `PREFS_NAME`、各 key、默认值、范围校验；UI 与业务读取同一处。副作用功能读取异常时采用关闭/拒绝触发的行为。
2. **Provider 注册**：`onFeatureInit()` 调 `registerSettingsProvider()`；不要误以为类存在或 register Feature 后会自动有页面。
3. **首页分组**：`MiuixSettingsNavigation.featureGroups()` 使用显式 ID 白名单；新的 Provider 要加入对应分组，或根据功能扩展现有页面。
4. **页面路由与搜索**：`FeatureSettingsPage()` 对 `featureId()` 做显式 `when` 分发，未命中会进 Unsupported 页面；还要核对 `featureSubSearchTerms()` 的检索词。页面用当前 Miuix 共享组件和联系人/群组 Picker。
5. **导入导出与资源生命周期**：`MiuixSettingsPage.kt` 的 `HchatConfigBackup.prefsNames` 是显式配置文件名单；新增配置若要随用户导出，必须登记。不要自动备份 locator 缓存。嵌入 Compose 的 owner/recomposer 按 `EmbeddedComposeRuntime` 和 `EmbeddedComposeOwnerInstaller` 管理，不能随意复制 WeKit Activity 的生命周期假设。

证据：[ConfigStore](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/preferences/ConfigStore.kt#L7-L17)、[FastKV 分包存储](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/preferences/HchatStorage.kt#L16-L66)、[UIRegistry](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ui/UIRegistry.kt#L11-L23)、[首页分组](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ui/miuix/MiuixSettingsNavigation.kt#L252-L270)、[页面路由](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ui/miuix/MiuixSettingsNavigation.kt#L1522-L1648)、[配置导入导出](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ui/miuix/MiuixSettingsPage.kt#L2125-L2255)、[Compose 生命周期](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ui/miuix/EmbeddedComposeRuntime.kt)。

可参考 `LinkParseFeature.kt` 的“Feature + Runtime + Settings + Provider + 页面”组织方式；这里只参考职责划分，不把其每一行实现视为适用于所有功能的模板。[功能结构](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/linkparse/LinkParseFeature.kt#L8-L27)、[Picker 复用示例](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ui/miuix/LinkParseMiuixPage.kt#L40-L67)。

## 6. 按功能需求查 Hchat 共享能力

下表提供接入入口，不保证所有子能力在每个宿主版本可用。调用前看具体方法合同、状态与结果；“对象已初始化”“定位完成”“Hook 安装成功”“观察到事件”“完成业务效果”分别记录。

| 需要的能力 | 优先读的 Hchat 源文件 / 方法 | 接入注意 |
|---|---|---|
| 消息入站/出站观察与解析 | `hooks/api/message/WeChatMessageObserveApi.java`：`subscribe()`、`readinessDiagnostics()`；`WeChatMessageEventApi.java`；`WeChatMessageParseApi.java` | 观察器结合 PB 和数据库来源；订阅生命周期、去重、方向和消息类型由业务明确。不要另装一套全局 AddMsg 入口。 |
| 文本、@、引用、撤回、XML 消息 | `hooks/api/message/WeChatMessageApi.java`：`sendText()`、`sendAt()`、`sendQuote()`、`revoke()`、`sendXml()` | boolean/入队状态不自动等于对方收到；必要时接网络回执或最终 UI。不要复制 WeKit 整个 WeMessageApi。 |
| 本地系统消息和撤回提示 | `WeChatLocalMessageApi.kt`、`NativeSystemMessageInsertReceipt.kt` | 真实插入 ID + 回读核对；UNKNOWN 不是“没写进去”，不能直接再插一条。 |
| 联系人、群组、账号、选择器 | `hooks/api/contact/`；`ui/miuix/MiuixPickerPages.kt`：`ContactPickerPage()` / `GroupMemberPickerPage()` | 当前宿主身份、群组与私聊语义要保留；用户显示名不能当稳定 wxid。 |
| 数据库与变更订阅 | `hooks/api/runtime/WeChatDatabaseApi.java` / `WeChatDatabaseListenerApi.java`；`WeChatMessageChangeApi.java` | 先选择正确 talker/表，避免跨会话 ID 碰撞；要看数据库真实写入结果，不能只观察调用前参数。 |
| 图片/视频/语音/表情/文件/收藏发送 | `hooks/api/media/WeChatMediaApi.java` 及 `WeChatImageApi` / `WeChatVideoApi` / `WeChatVoiceApi` / `WeChatEmojiApi` / `WeChatFileApi` / `WeChatFavoriteApi` | 读取 `canSend*Silently()`、路径和时长单位；保存与发送是不同任务。 |
| 媒体下载 | `utils/HchatMediaDownloader.kt`：`downloadToFileAtomically()`、Finder 下载解密；`hooks/items/linkparse/WorkVideoClient.kt` / `WorkImageClient.kt` | 下载路径与宿主隔离、只暴露完整产物、失败清理；平台解析规则不能当成所有 HTTP URL 的通用契约。 |
| 当前页面与 Activity 生命周期 | `WeChatApis.chatPage()` / `lifecycle()` / `currentActivity()` / `activityStart()`；`hooks/api/ui/` | Activity 恢复不代表聊天页面；订阅和 View 必须按生命周期释放。 |
| 单条聊天消息长按菜单 | `SingleMessageMenuLocator.kt`：`menuCreateMethods()` / `menuClickMethods()`；现有 VoicePreview / TextVoice / EmojiSave / MessageForward | 它是**共享定位器，不是统一消息菜单事件总线**。复用 locator，并评估菜单 ID、点击消费顺序、现有 Hook 交互。 |
| 多选消息操作 | `MultiSelectMessageMenuLocator.kt` / `MultiSelectMessageResolver.kt` / `MultiSelectMessageUi.kt` | 多选退出、消息身份、重复提交和生命周期仍需功能级核实。 |
| 会话列表长按菜单 | `hooks/core/ConversationMenuExtensionRegistry.kt`，宿主入口在 `ConversationGroupRuntime.kt` | 现有 extension 模型按 itemId 注册/注销；先核实它所依赖的会话菜单所有者是否安装，避免另造平行菜单系统。 |
| 朋友圈菜单 | `hooks/api/sns/SnsContextMenuDispatcher.kt` / `SnsContextMenuLocator.kt` | 注册/注销 owner；与现有转发、伪互动合用，检查 itemId 冲突和宿主绑定生命周期。 |
| 朋友圈读取/发布/点赞/评论/媒体准备 | `hooks/api/sns/WeChatSnsApi.kt`：`observePosts()`、`prepareSnsPostMedia()`、`publishSnsPost()`、`like()`、`comment()` 等 | 静态存在不代表所有媒体形态都通过真机；要单独核对 LivePhoto、长视频等分支。 |
| 发送文本装饰 | `OutgoingTextDecoratorRegistry.kt`，已有 `MessageAffixFeature` 和 `NativeSendButtonHook` | 合并已有装饰顺序，避免上游和 Hchat 各加一次前/后缀或重复发送。 |
| 延时与后台任务 | `hooks/api/runtime/WeChatTaskApi.kt`：`runOnMainDelayed()` / `runAsync()` / `runOnce()` / `cancel()` | 用独立业务 key 和销毁清理；Dex 定位仍走 DexInstallScheduler，不把 Chain 传入任务。 |

共享服务集中创建在 [WechatApiFeature.install()](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/api/core/WechatApiFeature.java#L54-L200)，公布在 [WeChatApis](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/api/core/WeChatApis.java#L200-L334)。方法级入口：[消息发送](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/api/message/WeChatMessageApi.java#L201-L301)、[媒体](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/api/media/WeChatMediaApi.java#L43-L147)、[单消息菜单定位器](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/api/message/SingleMessageMenuLocator.kt#L13-L88)、[朋友圈菜单](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/api/sns/SnsContextMenuDispatcher.kt#L37-L105)、[会话菜单 extension](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/ConversationMenuExtensionRegistry.kt#L17-L76)。

## 7. 已有相近功能：先比较差异，再决定新建还是增强

以下只是静态定位到的**重叠候选**，不是等价证明、移植完成证明或真机通过清单。将一项 WeKit 功能整合到 Hchat 时，先比较需求、默认值、触发时机、配置范围、关闭行为、数据与回执；只引入缺少的语义。不要重复 Provider、Feature、全局 Hook 或菜单项。

| WeKit 源码相对 `features/items/` | Hchat 现有入口（相对 `hooks/items/`） | 要先问清/比较的差异 |
|---|---|---|
| `chat/AntiMessageRecall.kt` | `antirecall/AntiRecallFeature.kt` | 本地保留、自撤回、提示时刻/模板、服务器事件与系统消息插入语义。 |
| `chat/DisplayMessageDetails.kt`、`chat/MessageTimeEnhancements.kt` | `hchatextra/HchatExtraFeature.kt` 与 `MessageDetails*`；`chattime/ChatTimeStyleFeature.kt` | 消息下方详情与会话列表时间是不同层面；不能仅按名字替换。 |
| `chat/AutoEnableSendOriginalMedia.kt`、`chat/AutoViewOriginalMedia.kt` | `autooriginal/AutoOriginalImageFeature.kt`、`autovieworiginal/AutoViewOriginalFeature.kt` | 上游的 media 是否包含视频；只发原图与接收后查看原图分开。 |
| `chat/DisableTypingStatusUploading.kt` | `typingreport/TypingReportBlockFeature.kt` | 上报入口、私聊/群聊适用条件及失败放行语义。 |
| `chat/BlockAtAllNotifications.kt` | `atallnotify/AtAllNotificationBlockFeature.kt` | 通知层与消息入库层是否相同；普通 @我 必须单独核实。 |
| `chat/ForwardMessages.kt`、`chat/ForwardFavoriteVoices.kt`、`chat/SaveVoicesToLocalStorage.kt` | `messageforward/MessageForwardFeature.kt`、`voiceforward/VoiceForwardFeature.kt` | 不同内容类型、收藏语音、批量、多选退出、导出位置等。 |
| `chat/FakeVoiceDuration.kt` | `fakevoiceduration/FakeVoiceDurationFeature.kt` | 修改的仅展示时长还是发送元数据，单位与上限。 |
| `chat/HideMessagesAvatars.kt` | `hideavatar/HideChatAvatarFeature.kt` | View 复用、自己/他人、文字排列与消息详情尾行的联动。 |
| `contacts/CustomLocalFriendAvatars.kt`、`contacts/RoundAvatars.kt` | `customfriendavatar/CustomFriendAvatarFeature.kt`、`roundavatar/RoundAvatarFeature.kt` | 本地替换、加载缓存、列表复用、大小和复原策略。 |
| `contacts/RemoveMessageBatchForwardLimit.kt` | `forwardlimit/RemoveForwardLimitFeature.kt` | 解除选择数限制不代表服务端允许所有行为，仍要查目标调用链。 |
| `contacts/QuickOpenMoments.kt` | `quickmoments/QuickMomentsFeature.kt` | 入口位置与已装会话菜单所有者。 |
| `voip/BlockVoipRingtone.kt`、`voip/RemoveLimitsDuringCalls.kt` | `callmedialimit/CallRingtoneBlockFeature.kt`、`CallMediaLimitFeature.kt` | 仅铃声、通话中媒体限制、来电接管三者分开。 |
| `system/ModifySportsStepCount.kt` | `sportstep/SportStepFeature.kt` | 用户现有选择是手动提交；不得借移植偷偷恢复自动倍率。 |
| `system/FakeLocation.kt` | `fakelocation/FakeLocationFeature.kt` | 主进程、小程序进程、定位坐标转换与账号范围。 |
| `miniapps/RemoveSplashAds.kt`、`miniapps/RemoveVideoAds.kt`、`miniapps/SkipRewardedAds.kt` | `miniprogramsplashad/SkipGlobalMiniProgramSplashAdsFeature.kt`、`miniprogramvideoad/SkipMiniProgramVideoAdsFeature.kt` | 去开屏、去视频、模拟激励完成不是一个行为；按具体需求逐个比较。 |
| `moments/AutoLikeMoments.kt`、`AutoRepostMoments.kt`、`RepostMoments.kt` | `moments/MomentsAutoLikeFeature.kt`、`MomentsAutoForwardFeature.kt`；`messageforward/` | 自动订阅与手动菜单不同；循环转发/重复触发与媒体失败要单独分析。 |
| `moments/AntiMomentsDelete.kt`、`AntiMomentCommentsDelete.kt` | `moments/SnsAntiRecallFeature.kt` | 动态删除和评论删除的保留层级、持久性与重进效果可能不同。 |
| `moments/FakeMomentsLikes.kt` | `momentsfake/MomentsFakeInteractionFeature.kt` | 本地显示与真实网络互动必须明确分开。 |
| `moments/RemoveMomentsAds.kt` | `moments/RemoveMomentsAdsFeature.kt` | 过滤数据、Adapter 绑定和显示层可能不同，避免两次删除。 |
| `notifications/NotificationsEvolved.kt`；`CustomConversationNotifications.kt` 仅为已注释的历史代码 | `customnotify/CustomNotificationFeature.kt`、`keywordnotify/KeywordNotificationFeature.kt` | 当前有效功能以 NotificationsEvolved 为准；不能把已注释的旧文件当成可移植注册对象。自定义内容与通知框架增强覆盖范围不同，保留现有消息屏蔽联动。 |

WeKit 路径前缀为 `app/src/main/java/dev/ujhhgtg/wekit/`；Hchat 路径前缀为 `app/src/main/java/h/Hchat/`。本表文件已在对应固定提交检索确认；已注释的历史文件单独标明，不算有效 Feature；以后通过功能索引定位中文名称后，还需完整阅读该功能和直接依赖。

## 8. 迁入时不能倒退的具体边界

### 8.1 链接解析范围和发送去重

当前 `LinkParseSettings.allows()`：总开关和平台开关要开启；自己发送的链接不受联系人白名单或群 @ 条件限制；他人必须位于允许的 talker；群“仅 @我”仅约束他人群消息。平台枚举目前只有抖音和小红书。不要用通用“允许他人”判断覆盖它，也不要把旧快手/群触发模式带回。

当前 `LinkParseRuntime` 的数据库/写入观察与持久去重，还需保持成功状态、msgId 与 talker 一致性；历史文档会包含比当前配置更旧的描述，以当前源码为准。[规则源码](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/linkparse/LinkParseSettings.kt#L10-L56)、[R2 历史证据](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/docs/acceptance/LINK_PARSE_R2_2026-11-05.md)。

### 8.2 聊天行刷新和分享联系人页闪退防线

`MessageBubbleRuntime.watchPageTransitions()` 只在聊天页进入，或受控聊天 Activity 恢复且 `chatPage.isInChatPage()` 时请求刷新；退出/关闭/销毁会取消待执行刷新。`ChatRowRefresh.requestChatMessageRows*()` 进一步要求列表当前行的 holder 属于 `com.tencent.mm.ui.chatting.viewitems.*`。其他页面 RecyclerView 不应因聊天外观功能被刷新。**新增聊天视觉功能不使用无差别 `ChatRowRefresh.request()` 或给所有 Activity.onResume 加一轮全树刷新。**[页面门禁](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/messagebubble/MessageBubbleFeature.kt#L91-L141)、[目标列表门禁](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/ChatRowRefresh.kt#L17-L25)、[holder 判断](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/core/ChatRowRefresh.kt#L94-L135)。

### 8.3 消息详情尾行与历史滚动

`MessageDetailsFollowLatestScroll.kt` 把根 View 可见矩形换算到窗口坐标，且区别用户向历史导航与自动负向滚动；上游若改消息行布局/头像/气泡，不能覆盖这套处理。既有验收把 Robolectric/生产回调注入与真机证据分开，外接键盘/无障碍实机覆盖仍有限。[实现](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/hchatextra/MessageDetailsFollowLatestScroll.kt#L22-L135)、[坐标与意图验收](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/docs/acceptance/MESSAGE_TIME_COORDINATES_AND_INTENT_2026-11-05.md)。

### 8.4 防撤回提示的真实插入回执

`WeChatLocalMessageApi` 使用真实存储 ID 与同 talker 回读核验。结果为 UNKNOWN 时不直接再插入，避免已落库却未观测到回执导致双提示。消息时间的秒/毫秒、服务器 ID 与本地 ID、跨会话查找保持区分。既有文档对修复后群聊仍标 NOT_VERIFIED，不能因现在源码存在就改为全部通过。[插入语义](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/api/message/WeChatLocalMessageApi.kt#L77-L165)、[防撤回验收](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/docs/compat/wechat-8.0.80-antirecall-2026-11-03.md)。

### 8.5 步数维持手动产品范围

设置页只有目标步数输入和提交，区分入队、网络处理成功及排行榜实际结果；仓库仍有 `StepMultiplierPolicy` 名称不代表用户要恢复自动倍率。按当前用户决定维护现有手动范围。[手动功能](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/sportstep/SportStepFeature.kt#L8-L24)、[状态页面](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/ui/miuix/SportStepMiuixPage.kt#L30-L71)。

### 8.6 当前 VoIP 源码需单独复核，不能把它当规范模板

基线中的 `MessageBlockVoipIncomingCallHooker.kt` 会发 `ackstatus=2` 忙线回复后短路，这是**拒绝来电**路径；不是“不回包、让对方等正常超时”。若新需求涉及通话，需重新确认语义，不能仅引用以前聊天摘要。

静态阅读还发现：该文件 48–93 行的 `try` 内有若干 `return@Hooker chain.proceed()`，而 `catch` 又调用 `chain.proceed()`。若前一次宿主放行抛异常，会进入 catch 再执行一次原调用；这与项目“一次 proceed、宿主异常原样传播”要求冲突。此处只记录源码可见风险，**本轮未改代码、未做故障注入或真机验证**；之后触及该功能时应单独回归并处理，不把这段 try/catch 结构当作移植模板。[当前来电实现](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/messageblock/MessageBlockVoipIncomingCallHooker.kt#L43-L95)。

## 9. 将来单功能任务的验证入口

本轮是资料准备，不运行 Android 构建。以下用于未来实际迁入的 Codex 指令，按受影响能力选择现有验证；避免为纯文档或直线常量添加镜像测试。

| 变化范围 | 现有入口（不是本轮 PASS 声明） |
|---|---|
| API102 拦截器、调用次数、异常和批量注册 | `scripts/run_api102_interceptor_tests.cjs`、`scripts/run_api102_r1_tests.cjs`、`scripts/run_hook_registry_tests.cjs`；`scripts/lib/api102.cjs` 从正式 API AAR 提取声明 |
| 启动、缓存、Tinker 代次、恢复 | `scripts/run_startup_r1_tests.cjs`；`app/src/test/java/h/Hchat/dexkit/`、`hooks/api/runtime/HostRuntimeIdentityLifecycleTest.java`、`hooks/core/ModuleStartupStateTest.kt` |
| 共享数据库、消息、生命周期 | `scripts/run_database_chain_tests.cjs`、`scripts/run_message_parser_tests.cjs`、`scripts/run_lifecycle_chain_tests.cjs`；`WeChatMessageObserveReadinessRegressionTest.java` |
| 设置页、联系人 Picker | `scripts/run_settings_bridge_tests.cjs`、`scripts/run_picker_chain_tests.cjs`、`scripts/run_contact_api_tests.cjs` |
| 消息详情/气泡/头像/滑动 | `scripts/run_message_details_binding_tests.cjs`、`scripts/run_message_details_layout_tests.cjs`、`scripts/run_message_details_predraw_tests.cjs`；`ChatRowRefreshTargetTest.kt` 和 `hooks/items/hchatextra/` 测试 |
| 链接解析 | `app/src/test/java/h/Hchat/hooks/items/linkparse/`：Settings / MessageGate / WriteGate / WriteHook / Database / MessageClaims / Gallery 等现成回归 |
| 防撤回 | `app/src/test/java/h/Hchat/hooks/items/antirecall/RecallRuntimePolicyRegressionTest.kt` |
| 消息/来电屏蔽 | `scripts/run_message_block_tests.cjs`；`MessageBlockDecisionTest.java`、`MessageBlockVoipIncomingRegressionTest.kt`（新增合同风险不代表现有测试已覆盖） |
| 步数 | `scripts/run_step_upload_boundary_tests.cjs`、`scripts/run_step_multiplier_policy_tests.cjs`；前者与当前手动发送边界相关，后者名称不扩大产品范围 |
| 正式 APK 结构 | `scripts/verify_api102_apk.py`、APK 包名/版本/正式证书及 v2 签名验证，检查 java_init.list 可达、旧 API 不复活、反射/JNI/resource 引用经过 R8 |

实际实现时，根据项目 skill 运行受影响回归，并完成 Debug、正式签名 Release、Lint；典型任务为 `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:lintRelease`、`:app:assembleRelease`。先检查本机 SDK/JDK/签名环境，缺项记真实阻碍；不要改变包名、换签名来制造“构建成功”。Release 的 `verifyReleaseSigning` 会检查已有签名配置，R8 与资源压缩已开启。[构建/签名门禁](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L28-L78)、[现有测试环境](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L92-L111)、[交付规范](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/skills/hchat-project/SKILL.md#L27-L42)。

当前两个 GitHub workflow 都仅手动触发。`android-test.yml` 是**关闭 R8 的签名构建**，没有执行单测；`android-single.yml` 默认 `publish_release=true`，会发 GitHub Release 并在配置齐全时发 Telegram。它们不能当作“推代码自动全验收”，也不应为文档安装擅自触发。[正式 workflow](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/.github/workflows/android-single.yml#L1-L10)、[发布步骤](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/.github/workflows/android-single.yml#L126-L168)、[测试构建](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/.github/workflows/android-test.yml#L35-L71)。

真机验收必须记录实际宿主包、versionName/versionCode、APK hash、框架 API、模块 commit/APK hash、定位/安装/回调/效果。首测、关闭、重启、重复触发、私聊/群聊、回收复用等场景由所选功能决定。未观察到真实行为时写 NOT_VERIFIED；编译通过、DexSQL 查到符号、fixture 成功和屏幕无崩溃均不是完整业务成功证明。

## 10. 当前完整登记表

由 `FeatureRegistry.createDefaultManager()` 的 `.register(...)` 逐项提取，中文名来自对应类的 `name()`。用于发现已有入口，不替代每项源码阅读。

| # | 名称 | Feature 类 | featureId | 源码 |
|---:|---|---|---|---|
| 1 | 微信公共API | `WechatApiFeature` | `wechat_api` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/api/core/WechatApiFeature.java) |
| 2 | 设置入口 | `SettingsFeature` | `settings` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/settings/SettingsFeature.kt) |
| 3 | 原生发送按钮 | `NativeSendButtonFeature` | `shared_native_send_button` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/sendbutton/NativeSendButtonFeature.kt) |
| 4 | 悬浮快捷菜单 | `FloatingShortcutFeature` | `floating_shortcut_menu` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/floatingshortcut/FloatingShortcutFeature.kt) |
| 5 | 自定义底栏 | `CustomBottomBarFeature` | `custom_bottom_bar` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/custombottombar/CustomBottomBarFeature.kt) |
| 6 | 圆角头像 | `RoundAvatarFeature` | `round_avatar` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/roundavatar/RoundAvatarFeature.kt) |
| 7 | 自定义头像 | `CustomFriendAvatarFeature` | `custom_friend_avatar` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/customfriendavatar/CustomFriendAvatarFeature.kt) |
| 8 | 实名尾字 | `RealNameTailFeature` | `real_name_tail` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/realtail/RealNameTailFeature.kt) |
| 9 | 群员头衔 | `MemberTitleFeature` | `member_title` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/membertitle/MemberTitleFeature.kt) |
| 10 | 自动抢红包 | `AutoRedPacketFeature` | `auto_redpacket` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/payment/core/AutoRedPacketFeature.kt) |
| 11 | 自动抢礼物 | `AutoGiftFeature` | `auto_gift` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/payment/gift/AutoGiftFeature.kt) |
| 12 | 平板模式 | `WeChatTabletFeature` | `wechat_tablet` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/tablet/WeChatTabletFeature.kt) |
| 13 | 自动收款 | `AutoTransferFeature` | `auto_transfer` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/payment/transfer/AutoTransferFeature.kt) |
| 14 | 伪造零钱 | `FakeWalletBalanceFeature` | `fake_wallet_balance` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/payment/fakebalance/FakeWalletBalanceFeature.kt) |
| 15 | 防撤回 | `AntiRecallFeature` | `anti_recall` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/antirecall/AntiRecallFeature.kt) |
| 16 | 多选撤回 | `MultiRecallFeature` | `multi_recall` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/multirecall/MultiRecallFeature.kt) |
| 17 | 屏蔽热更新 | `DisableHotUpdateFeature` | `disable_hot_update` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/hotupdate/DisableHotUpdateFeature.kt) |
| 18 | 资料页显示ID | `ProfileIdFeature` | `profile_id` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/profileid/ProfileIdFeature.kt) |
| 19 | 隐藏长按菜单 | `HideChatMenuFeature` | `hide_chat_menu` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/hidemenu/HideChatMenuFeature.kt) |
| 20 | 快捷设置备注和标签 | `QuickContactEditFeature` | `quick_contact_edit` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/quickcontactedit/QuickContactEditFeature.kt) |
| 21 | 快捷设置群聊标签 | `QuickGroupChatLabelFeature` | `quick_group_chat_label` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/grouplabel/QuickGroupChatLabelFeature.kt) |
| 22 | 快捷查看朋友圈 | `QuickMomentsFeature` | `quick_moments` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/quickmoments/QuickMomentsFeature.kt) |
| 23 | 快捷已读 | `QuickMarkReadFeature` | `quick_mark_read` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/quickread/QuickMarkReadFeature.kt) |
| 24 | 快捷终止 | `QuickTerminateFeature` | `quick_terminate` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/quickterminate/QuickTerminateFeature.kt) |
| 25 | 自动回复 | `AutoReplyFeature` | `auto_reply` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/autoreply/AutoReplyFeature.kt) |
| 26 | 消息自动转发 | `AutoMessageForwardFeature` | `auto_message_forward` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/automessageforward/AutoMessageForwardFeature.kt) |
| 27 | 聊天分组 | `ConversationGroupFeature` | `conversation_group` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/conversationgroup/ConversationGroupFeature.kt) |
| 28 | 标签分组 | `ConversationTabsFeature` | `conversation_tabs` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/conversationtabs/ConversationTabsFeature.kt) |
| 29 | 发送文本格式 | `MessageAffixFeature` | `message_affix` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/messageaffix/MessageAffixFeature.kt) |
| 30 | 拦截正在输入上报 | `TypingReportBlockFeature` | `block_typing_report` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/typingreport/TypingReportBlockFeature.kt) |
| 31 | 禁止拍一拍 | `PatBlockFeature` | `disable_pat` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/patblock/PatBlockFeature.kt) |
| 32 | 自动勾选原图 | `AutoOriginalImageFeature` | `auto_original_image` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/autooriginal/AutoOriginalImageFeature.kt) |
| 33 | 自动查看原图 | `AutoViewOriginalFeature` | `auto_view_original` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/autovieworiginal/AutoViewOriginalFeature.kt) |
| 34 | 移除转发限制 | `RemoveForwardLimitFeature` | `remove_forward_limit` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/forwardlimit/RemoveForwardLimitFeature.kt) |
| 35 | 屏蔽艾特所有人 | `AtAllNotificationBlockFeature` | `block_at_all_notification` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/atallnotify/AtAllNotificationBlockFeature.kt) |
| 36 | 移除通话媒体限制 | `CallMediaLimitFeature` | `call_media_limit` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/callmedialimit/CallMediaLimitFeature.kt) |
| 37 | 屏蔽通话铃声 | `CallRingtoneBlockFeature` | `call_ringtone_block` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/callmedialimit/CallRingtoneBlockFeature.kt) |
| 38 | 自定义通知 | `CustomNotificationFeature` | `custom_notification` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/customnotify/CustomNotificationFeature.kt) |
| 39 | 关键词通知 | `KeywordNotificationFeature` | `keyword_notification` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/keywordnotify/KeywordNotificationFeature.kt) |
| 40 | 文字转语音播报 | `TextSpeechFeature` | `text_speech` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/textspeech/TextSpeechFeature.kt) |
| 41 | 文本转语音 | `TextVoiceFeature` | `text_voice` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/textvoice/TextVoiceFeature.kt) |
| 42 | 僵尸粉检测 | `ZombieCheckFeature` | `zombie_check` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/zombiecheck/ZombieCheckFeature.kt) |
| 43 | 微信后台运行辅助 | `BackgroundRunAssistFeature` | `wechat_keep_alive` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/backgroundassist/BackgroundRunAssistFeature.kt) |
| 44 | 删除键清引用 | `QuoteDeleteClearFeature` | `quote_delete_clear` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/quoteclear/QuoteDeleteClearFeature.kt) |
| 45 | 滑动手势 | `SwipeQuoteFeature` | `swipe_quote` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/swipequote/SwipeQuoteFeature.kt) |
| 46 | 音频转换 | `AudioTransformFeature` | `audio_transform` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/audiotransform/AudioTransformFeature.kt) |
| 47 | 伪造语音时长 | `FakeVoiceDurationFeature` | `fake_voice_duration` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/fakevoiceduration/FakeVoiceDurationFeature.kt) |
| 48 | 会话时间样式 | `ChatTimeStyleFeature` | `chat_time_style` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/chattime/ChatTimeStyleFeature.kt) |
| 49 | 输入框提示 | `InputHintFeature` | `input_hint` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/inputhint/InputHintFeature.kt) |
| 50 | 消息气泡 | `MessageBubbleFeature` | `message_bubble` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/messagebubble/MessageBubbleFeature.kt) |
| 51 | 消息文本颜色 | `MessageTextColorFeature` | `message_text_color` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/messagetextcolor/MessageTextColorFeature.kt) |
| 52 | 首页文字颜色 | `HomeTextColorFeature` | `home_text_color` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/hometextcolor/HomeTextColorFeature.kt) |
| 53 | 隐藏头像 | `HideChatAvatarFeature` | `hide_chat_avatar` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/hideavatar/HideChatAvatarFeature.kt) |
| 54 | 屏蔽消息 | `MessageBlockFeature` | `message_block` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/messageblock/MessageBlockFeature.kt) |
| 55 | 虚拟定位 | `FakeLocationFeature` | `fake_location` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/fakelocation/FakeLocationFeature.kt) |
| 56 | 指定骰子猜拳 | `GameEmojiFeature` | `game_emoji_result` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/gameemoji/GameEmojiFeature.kt) |
| 57 | 兼容低版本小程序 | `FakeMiniProgramBaseLibFeature` | `fake_mini_program_base_lib` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/miniprogrambaselib/FakeMiniProgramBaseLibFeature.kt) |
| 58 | 跳过小程序视频广告 | `SkipMiniProgramVideoAdsFeature` | `skip_mini_program_video_ads` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/miniprogramvideoad/SkipMiniProgramVideoAdsFeature.kt) |
| 59 | 跳过全局小程序开屏广告 | `SkipGlobalMiniProgramSplashAdsFeature` | `skip_global_mini_program_splash_ads` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/miniprogramsplashad/SkipGlobalMiniProgramSplashAdsFeature.kt) |
| 60 | QQ点歌 | `QQMusicOrderFeature` | `qq_music_order` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/musicorder/QQMusicOrderFeature.kt) |
| 61 | 链接解析 | `LinkParseFeature` | `link_parse` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/linkparse/LinkParseFeature.kt) |
| 62 | 修改运动步数 | `SportStepFeature` | `sport_step` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/sportstep/SportStepFeature.kt) |
| 63 | 朋友圈自动点赞 | `MomentsAutoLikeFeature` | `moments_auto_like` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/MomentsAutoLikeFeature.kt) |
| 64 | 朋友圈自动评论 | `MomentsAutoCommentFeature` | `moments_auto_comment` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/MomentsAutoCommentFeature.kt) |
| 65 | 朋友圈自动转发 | `MomentsAutoForwardFeature` | `moments_auto_forward` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/MomentsAutoForwardFeature.kt) |
| 66 | 朋友圈自动刷新 | `MomentsAutoRefreshFeature` | `moments_auto_refresh` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/MomentsAutoRefreshFeature.kt) |
| 67 | 朋友圈关键词屏蔽 | `MomentsKeywordBlockFeature` | `moments_keyword_block` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/MomentsKeywordBlockFeature.kt) |
| 68 | 朋友圈过滤 | `MomentsContactFilterFeature` | `moments_contact_filter` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/MomentsContactFilterFeature.kt) |
| 69 | 朋友圈底部详情 | `MomentsBottomDetailFeature` | `moments_bottom_detail` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/MomentsBottomDetailFeature.kt) |
| 70 | 朋友圈发布通知 | `MomentsPostNotificationFeature` | `moments_post_notification` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/MomentsPostNotificationFeature.kt) |
| 71 | 朋友圈原图上传 | `OriginalMomentsUploadFeature` | `original_moments_upload` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/OriginalMomentsUploadFeature.kt) |
| 72 | 朋友圈上传尾巴 | `MomentsUploadTailFeature` | `moments_upload_tail` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/MomentsUploadTailFeature.kt) |
| 73 | 朋友圈防撤回 | `SnsAntiRecallFeature` | `sns_anti_recall` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/SnsAntiRecallFeature.kt) |
| 74 | 朋友圈伪互动 | `MomentsFakeInteractionFeature` | `moments_fake_interaction` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/momentsfake/MomentsFakeInteractionFeature.kt) |
| 75 | 群聊标签 | `GroupChatLabelFeature` | `group_chat_label` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/grouplabel/GroupChatLabelFeature.kt) |
| 76 | 进退群监控 | `GroupLeaveMonitorFeature` | `group_leave_monitor` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/groupleave/GroupLeaveMonitorFeature.kt) |
| 77 | 改名监控 | `GroupRenameMonitorFeature` | `group_rename_monitor` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/grouprename/GroupRenameMonitorFeature.kt) |
| 78 | 去除朋友圈广告 | `RemoveMomentsAdsFeature` | `remove_moments_ads` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/moments/RemoveMomentsAdsFeature.kt) |
| 79 | 修改聊天记录 | `EditMessageFeature` | `edit_message` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/editmsg/EditMessageFeature.kt) |
| 80 | 保存表情 | `EmojiSaveFeature` | `emoji_save` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/emojisave/EmojiSaveFeature.kt) |
| 81 | 语音消息预览 | `VoicePreviewFeature` | `voice_preview` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/voicepreview/VoicePreviewFeature.kt) |
| 82 | 语音转发保存 | `VoiceForwardFeature` | `voice_forward` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/voiceforward/VoiceForwardFeature.kt) |
| 83 | 群发助手 | `SelectedMessagesFeature` | `selected_messages` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/selectedmessages/SelectedMessagesFeature.kt) |
| 84 | 转发 | `MessageForwardFeature` | `message_forward` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/messageforward/MessageForwardFeature.kt) |
| 85 | 定时任务 | `ScheduledTaskFeature` | `scheduled_task` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/scheduledtask/ScheduledTaskFeature.kt) |
| 86 | 模拟相机扫码 | `FakeScanCameraFeature` | `fake_scan_camera` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/fakescancamera/FakeScanCameraFeature.kt) |
| 87 | 解除状态词长度限制 | `StatusTextLimitFeature` | `status_text_limit` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/statuslimit/StatusTextLimitFeature.kt) |
| 88 | 视频号媒体下载 | `FinderMediaDownloadFeature` | `finder_media_download` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/shortvideo/FinderMediaDownloadFeature.kt) |
| 89 | 分支扩展功能 | `HchatExtraFeature` | `hchat_extra` | [查看](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/src/main/java/h/Hchat/hooks/items/hchatextra/HchatExtraFeature.kt) |

## 11. 下一步

从 [功能索引](FEATURE_INDEX.md) 选定上游入口后，按本页找到当前 Hchat 的对应职责，再依照 [逐功能工作流](PORTING_WORKFLOW.md) 形成具体方案。基础阅读入口是 [README](README.md)。
