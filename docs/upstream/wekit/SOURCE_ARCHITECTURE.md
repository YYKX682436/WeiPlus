# WeKit → Hchat：架构、依赖与来源核查

核查日期：2026-11-06（北京时间）。本文件记录固定源码的架构与依赖事实；本次未修改业务实现、构建 APK 或执行微信行为。工作入口见 [README](README.md)，执行和验收见 [逐功能工作流](PORTING_WORKFLOW.md)。

## 1. 固定证据基线

| 项目 | 已读取提交 | 当前分支 | 角色 |
|---|---|---|---|
| Ujhhgtg/WeKit | `650febaa3717eddd39f5f29a4dfa6dfd37875cb1` | `master` | 上游参考源码 |
| YYKX682436/WeiPlus | `332c9650103fa68fc5f755d4c2d8cef4502d0c34` | `main` | Hchat 目标工程 |

以下 `W:` 路径相对 WeKit 根目录，`H:` 路径相对 WeiPlus 根目录。源码行号只用于这个 SHA；未来以符号名和固定提交链接定位，不能把行号当永久入口。

## 2. 核心结论

**可建立轻量、可持续的逐功能移植工作台：把索引、两边的接入地图和来源记录放在 WeiPlus 文档内；WeKit 用独立参考 checkout 固定在上游 SHA。选择具体功能后再读取它的依赖闭包，按 Hchat 的现有底座重写接入。**

WeKit 本身是完整 Android 应用，不是可直接作为依赖接入 Hchat 的功能库。它有独立的启动器、Feature 生命周期、KSP 注册、MMKV、reflekt、DexKit DSL/缓存、UI、Rust native 和 Zygisk 运行时。为了移植一项普通功能，没有必要把整套底座引入 Hchat，也没有必要先成功构建 WeKit。

需要明确的两个边界：

- 上游现有适配范围不包含 Hchat 当前目标微信 8.0.80。源码能提供入口与结构证据，不能直接当作 8.0.80 已验收实现。
- 顶层 GPL v3 文件不表示逐文件、子模块和第三方来源已经全部核实。现在可以完成索引和分析；实际复制代码时要保留来源并处理目标工程的许可声明。

## 3. WeKit 的真实启动与功能链

### 3.1 入口与桥接

[W:app/src/standard/java/dev/ujhhgtg/wekit/loader/entry/lxp/LxpHookEntry.kt:13–31](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/standard/java/dev/ujhhgtg/wekit/loader/entry/lxp/LxpHookEntry.kt#L13) 的现代入口继承 `XposedModule`，在 `onModuleLoaded` 初始化 `LxpHookImpl`，在 `onPackageReady` 检查宿主包并调用 `ModuleLoader.init`。

传统入口在 `W:app/src/main/java/dev/ujhhgtg/wekit/loader/entry/xp51/`。Zygisk 入口在 `.../loader/entry/zygisk/`。[W:app/build.gradle.kts:59–78](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/build.gradle.kts#L59) 区分 `standard` 和 `legacy` flavor；不要把 AGENTS 中列出的接口范围误当成 Hchat 应同时兼容三套入口的要求。

统一启动顺序的直接证据：

| 环节 | 文件与符号 | 实际工作 |
|---|---|---|
| 宿主 attach | [W:.../loader/startup/UnifiedEntryPoint.kt:19–58](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/loader/startup/UnifiedEntryPoint.kt#L19), `entry` | 设置桥接、调整模块父加载器；在微信 Application attach 后 hook Instrumentation 的 application create 回调 |
| 启动事务 | [W:.../loader/startup/StartupAgent.kt:32–73](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/loader/startup/StartupAgent.kt#L32), `startup` | 设置真实宿主 ClassLoader，初始化 HostInfo/native，再调用 WeLauncher；全部完成后才置 initialized |
| 功能装载 | [W:.../loader/startup/WeLauncher.kt:20–44](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/loader/startup/WeLauncher.kt#L20), `init` | Dex 缓存身份、资源注入、主进程 ActivityProxy、调用 FeaturesLoader |
| 注册表 | [W:libs/common/annotation-scanner/src/main/java/dev/ujhhgtg/wekit/features/FeaturesScanner.kt:52–79](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/libs/common/annotation-scanner/src/main/java/dev/ujhhgtg/wekit/features/FeaturesScanner.kt#L52) | 从源代码对象发现 BaseFeature 子类，生成 FeaturesProvider；不是维护一份人工 HookItems 列表 |
| 功能调度 | [W:.../features/core/FeaturesLoader.kt:32–75](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/features/core/FeaturesLoader.kt#L32) | 读取开关；safe mode 只装 ApiFeature；失效/缺键 Dex 项暂不启动，安排修复 |

其中 `...` 统一指 `app/src/main/java/dev/ujhhgtg/wekit`。

**容易误读的残留：** `libs/common/annotation-scanner/bin/main/` 仍有旧 `Feature.kt` 与旧扫描器文件。实际 KSP 来源为 `src/main/`，不能按 `bin/main` 的旧注解设计功能索引或 Hchat 接入方案。

### 3.2 功能对象、生命周期和 Hook 参数

- `BaseFeature.kt:25–41` 定义 `technicalId`、字符串资源、分类、进程集合；默认只在 `MAIN`。
- `BaseFeature.kt:56–80` 在 enable 时安装，失败撤销已登记 hook；disable 会 unhook 并调用 `onDisable`。`isActive` 是当前进程安装状态。
- `SwitchFeature.kt:18–60` 区分默认值、持久化意图和运行时安装；`applyToggle` 才写 WePrefs。`ClickableFeature.kt:7–14` 还有 `alwaysEnabled`/`noSwitchWidget`。
- `BaseFeature.kt:98–105` 通过 `unhooks` 管理解除；类内 `hookBefore/hookAfter` 代理自动登记 handle；`executeHookAction` 在第 198–201 行记录回调异常。
- `IHookBridge.kt:12–60` 暴露 before/after、可变 `args/result/throwable/extra`、invoke-original 和构造器语义。`HookUtils.kt:10–14` 只是这些参数/handle 的类型别名，并非 libxposed 102 的 Chain 本身。
- `LxpHookWrapper.kt:217–286` 将上述语义转成现代 interceptor；`ArtHookBridge.kt:16–22` 明确 Zygisk 内 native hook 持续到进程结束，解除操作只去掉回调，backup 保持有效。

**移植含义：** 不能简单把 `result = null`、`args[index] = ...`、`param.extra` 机械换名。必须在 Hchat 的 native libxposed 102 注册/拦截机制里明确：是否执行原方法、何时改参数、after 是否仍运行、同一 hook 的临时状态如何保存，以及失败时如何保留宿主原始行为。Hchat 现有 HookRegistry/Feature 生命周期是接入依据。

### 3.3 Dex 解析不是几个字符串

关键文件：

- [W:.../dexkit/abc/IResolveDex.kt:12–49](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/dexkit/abc/IResolveDex.kt#L12)：每个 Feature 持有委托列表，解析结果与缓存按 key 恢复。
- [W:.../dexkit/dsl/DexDelegates.kt:28–41,183–211](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/dexkit/dsl/DexDelegates.kt#L28)：委托序列化、placeholder、查找唯一性与错误分类；多结果默认失败，`allowFailure` 的零结果成为预期缺失，不能混作成功。
- [W:.../dexkit/resolution/DexResolutionContext.kt:39–50,75–99](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/dexkit/resolution/DexResolutionContext.kt#L39)：跨 Feature 依赖会递归 resolve 所属 Feature，受协调器管理；某 Feature 引用另一个 Feature 的 `.data` 并不等于其依赖可忽略。
- [W:.../dexkit/cache/DexCacheManager.kt:43–52,63–97,184–188](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/dexkit/cache/DexCacheManager.kt#L43)：宿主版本身份变化清缓存；每项需所有 key 与生成 `methodHash` 一致。
- [W:buildSrc/src/main/kotlin/GenerateMethodHashesTask.kt:26–61](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/buildSrc/src/main/kotlin/GenerateMethodHashesTask.kt#L26)：扫描解析源码，按 technicalId 生成 method hashes。
- [W:.../dexkit/cache/CloudDexResolver.kt:32–35,47–76](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/java/dev/ujhhgtg/wekit/dexkit/cache/CloudDexResolver.kt#L32)：还可读取 WeKit 自己的 GitHub Dex-Test 发布报告；按宿主、methodHash、delegate keys 选取。

**移植含义：** 复用 matcher 的语义和可核实证据；不要导入 WeKit 的缓存文件、云解析 URL、MD5 生成器或依赖解析协调器。Hchat 已有自己的缓存与预热策略，新增目标要进入其现有机制。8.0.80 的唯一匹配、签名、调用方、字段类型要重新核对；若失败，应把该能力标成不可用并给出可定位的日志，不能用宽匹配“凑通过”。

## 4. 配置、UI、数据与依赖清单

### 4.1 两边关键差异

| 方面 | WeKit 已核实事实 | Hchat 已核实事实 | 适配建议 |
|---|---|---|---|
| 主工程 | `app` application module；[W:app/build.gradle.kts:8–15](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/build.gradle.kts#L8) | `h.Hchat` application module；[H:app/build.gradle.kts:9–24](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L9) | 按功能接入，不 include 上游 app |
| minSdk | 28；[W:gradle/libs.versions.toml:58](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/gradle/libs.versions.toml#L58) | 27；[H:app/build.gradle.kts:18](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L18) | 检查新 API 与反糖，不自动提高 Hchat minSdk |
| 运行时 hook | 传统/现代 Xposed 与 LSPlant Zygisk 三套桥 | `compileOnly io.github.libxposed:api:102.0.0`；[H:app/build.gradle.kts:143](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L143) | 沿用 Hchat 102 |
| 反射 | reflekt 子模块；[W:app/build.gradle.kts:354](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/build.gradle.kts#L354) | KavaRef；[H:app/build.gradle.kts:148](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L148) | 读取目标工程现有反射助手，不再引一套 reflekt |
| DexKit | Maven DexKit 2.2.0；[W:gradle/libs.versions.toml:5,120](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/gradle/libs.versions.toml#L5) | 本地定制 AAR；[H:app/build.gradle.kts:145](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L145) | 不因上游版本同为 2.2.0 就替换 Hchat AAR |
| 存储 | MMKV；`WePrefs.kt:56–58` | FastKV；[H:app/build.gradle.kts:144](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L144)，`preferences/ConfigStore.kt:7–9` | 将配置映射到 Hchat ConfigStore、功能 Settings 与设置 provider |
| 设置 UI | Compose/M3、自有 widget、多语言资源、独立 activity/navigation | Hchat Compose/miuix 组件；[H:app/build.gradle.kts:152–165](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L152) | 保持 Hchat 现有页面与样式，功能语义单独适配 |
| 主 HTTP 库 | OkHttp 5.5.0；[W:gradle/libs.versions.toml:22,115](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/gradle/libs.versions.toml#L22) | OkHttp 4.12.0；[H:app/build.gradle.kts:151](https://github.com/YYKX682436/WeiPlus/blob/332c9650103fa68fc5f755d4c2d8cef4502d0c34/app/build.gradle.kts#L151) | 避免复制新版本专有 API；无需一起升级全局依赖 |

### 4.2 配置与账户边界

`WePrefs.kt:56–58` 的默认文件名是 `wekit_prefs`，`MmkvPrefsImpl.kt:14` 使用 MULTI_PROCESS_MODE；`NativeLoader.kt:39–49` 把 MMKV 初始化在宿主 files/mmkv。设置项不天然等于按微信账户隔离。

`KnownPaths.kt:16–29` 把功能数据与缓存放在外部存储 Android/data/当前宿主包/WeKit 路径，`downloads` 则是 Download/WeKit。未来迁移时要重新落实 Hchat 的存储目录、账户切换、清理和读写时机；不能顺带读写旧 WeKit 数据，也不能假设两个项目的配置 key 可直接通用。

### 4.3 哪些依赖会把“搬一个功能”变成更大工程

| 依赖群 | 源码证据 | 逐功能分析时要回答 |
|---|---|---|
| WeChat API 层 | `features/api/core/WeMessageApi.kt`、`WeDatabaseApi.kt`、`features/api/ui/`；例如 RemoveMessageSelectionLimit 引用 WeMessageApi 的其他委托 | Hchat 是否已有同等能力？同名能力的事件/返回语义是否一致？ |
| 语音转换 | `utils/AudioUtils.kt:7–43` 直接调用 native，失败后 Android 解码再调 native PCM→SILK | Hchat 已有语音能力可否覆盖？实际缺的是编码器还是业务入口？ |
| Telegram 表情转换 | `utils/TelegramStickerConverter.kt:14–52` 的 TGS/WebM native 转换；native Cargo 使用 rasterlottie/gif/webm-iterable | 是否必须引入此格式，还是现有平台解码足够？ |
| AI/代理/终端 | `app` 依赖 MCP、Room、Ktor、JSch；`agent/environment/OwnedProcess.kt` 与 `agent/terminal/NativeTerminalBackend.kt` 调 JNI | 是独立子系统需求，不能用普通开关功能的工作量估计 |
| Java 脚本 | bsh、scripta；`settings.gradle.kts:74–82` 与 `app/build.gradle.kts:327,342` | 是否真的要脚本引擎，还是只移植脚本的具体行为？ |
| Python 脚本 | `libs/python-runtime-api`、`extension-packs/python-runtime`；版本表含 Chaquopy 固定 revision | 扩展包加载/发布/运行时边界需专项分析 |
| 已读追踪 | `services/read-receipts/README.md:3–38` 与客户端 ReadReceipts | 需要服务端；这里统计的是图片请求来源 IP 的近似人数，不是微信原生已读回执 |
| 付费/红包/自动发送 | 相关 features 调 NetScene/数据库/发送 API | 必须以真实网络回执和可验证结果验收，不能以 UI toast 或队列成功冒充完成 |

这张表不是功能可用性评级。对某一项，必须实际走到依赖闭包末端，再决定移植范围。

## 5. Rust、C++、Zygisk 与构建范围

[W:Cargo.toml:1–3](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/Cargo.toml#L1) workspace 包含 application native、read-receipts、wekit-zygisk/native、xtask。

[W:app/src/main/rust/wekit-native/Cargo.toml:6–45](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/rust/wekit-native/Cargo.toml#L6) 的 cdylib 含 JNI、SILK、Symphonia、LAME、Lottie/GIF/WebM 等依赖。JNI 符号在 `src/lib.rs` 中绑定 `dev_ujhhgtg_wekit_*` 包/类名。因此直接把 .so 拿到 `h.Hchat` 下而改 Kotlin 包名，会遇到 JNI 名称不匹配；是否提取子能力需以实际 native 符号和加载逻辑设计。

[W:app/src/main/rust/wekit-native/build.rs:8–10,103–122](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/src/main/rust/wekit-native/build.rs#L8) 还会获取固定版本、固定 SHA-256 的 libvpx 源码，不能只复制一个 Cargo.toml 就声称具有可复现完整原生构建。

[W:wekit-zygisk/native/cpp/lsplant_bridge.cpp](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/wekit-zygisk/native/cpp/lsplant_bridge.cpp) 包装 LSPlant；`ArtHookBridge.kt:117–122` 的 external 方法对应这个独立 Zygisk 层，而不是普通业务 Feature 的必需依赖。

[W:xtask/src/main.rs:778–832](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/xtask/src/main.rs#L778) 编译 app native 并复制到 jniLibs；`893–921` 明确仅初始化 LSPlant 所需子模块，避免无关 SSH 测试依赖。[W:app/build.gradle.kts:81](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/app/build.gradle.kts#L81) 只打包既有 jniLibs；上游 AGENTS 因此要求使用 `./x`，不是直接 Gradle。

**当前前置工作应保持只读引用：** 不执行上游 `./x`、不初始化整个工具链、不复制上游 CI/发布脚本、不增加 Zygisk 入口。只有将来选中确实依赖 native 的功能时，才给出 Hchat 的最小构建变更。上游 AGENTS 的分支、构建、测试规则描述上游工程，不是 Hchat 的新全局指令。

## 6. 宿主支持与验证边界

[W:docs/getting-started.md:23–26](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/docs/getting-started.md#L23) 对国内版本的声明是：8.0.65–8.0.68 维护，8.0.69–8.0.76 支持，8.0.77–8.0.78 初步适配；Google Play 范围单独列在第 28–32 行。`AGENTS.md:75,187` 的 desktop Dex 门槛为 8.0.65–8.0.78。

因此，对 Hchat 的微信 8.0.80 (3220) 每一项都至少要区分：

1. 上游实现已经定位并读过。
2. 目标 8.0.80 APK 的 Dex/调用结构已验证。
3. Hchat 单测/编译/Release Lint/R8 等适用检查通过。
4. 真机中开关、触发、实际效果与受影响旧功能已验证。

任何一层缺失就明确写缺失。第 2 层不能证明第 4 层，编译通过不能证明对方收到消息、网络完成或不存在后续信令。不要以支持范围差异新增整个 Hchat 的版本拒绝；按具体能力结构适配。

## 7. 许可与代码来源：已核实事实

这里只记录许可证文件与声明，不作“所有代码均可直接商用/重许可”的法律结论。

### 7.1 主仓库

- WeKit 根 `LICENSE:1–2` 是 GNU GPL Version 3 正文；README 当前未列另一许可证，也没有一份与 LICENSE 相矛盾的许可标题。
- `README.md:34–52` 致谢 cwuom/WeKit、WAuxiliary、NewMiko、QAuxiliary、FingerprintPay、WADN/WAD、FunBox、I-Am-Pad；致谢不能代替逐文件来源链。
- 一批 `ui/content/m3`、动画与 UI 工具文件头明确 `GPL-3.0-only`，作者有 InstallerX Revived/KernelSU contributors。例：`ui/content/m3/BaseWidget.kt:1–2`、`ui/content/FloatingBottomBar.kt:1–4`。
- `ui/content/liquid/Lens.kt:1–6` 及同目录 Vibrancy、InnerShadow、CombinedBackdrop 文件头明确 Apache-2.0，并标出 compose-miuix-ui/AndroidLiquidGlass 来源。
- `utils/HexUtils.kt:5` 写来自 Hutool；`utils/serialization/XmlJsonParser.kt:31` 写从 org.json.XML 移植。
- `features/items/beautify/Themes.kt:108` 注释写来自 cherrywechat_deobf；`features/items/chat/MessageEntranceAnimation.kt:40` 注释提到闭源 Geek 的语义/行为移植。这些是需要在选中相应功能时核对来源的线索，不足以断言侵权或授权完备。
- WeiPlus 顶层 README 只有项目名与 Android LSPosed 模块说明；当前 tracked tree 没有根 LICENSE/NOTICE。`third_party/dexkit-memory/DexKitBridge.kt:4–20` 则有 LuckyPray 的 GPL v3-or-later 文件头。

建议：实际复制一项时记录原仓库、原提交、原路径、作者/文件头、变更说明、目标路径及依赖许可证；目标工程的总体许可与分发说明需要明确，不能默默给现有代码整体改许可。当前资料整理不需要先为所有候选功能做完整法律审查，也不应因此阻断前置工作。

### 7.2 六个子模块的固定引用

以下 SHA 来自 WeKit 的 gitlink。许可证文件通过 GitHub 在同一 SHA 读取，未使用漂移的 master/main。

| 子模块 | 固定 SHA | 已看到的具体许可证据 | 尚未证明 |
|---|---|---|---|
| Ujhhgtg/bsh | `66fabc1aa1fa0db1733e7453721e73a3a5e34638` | Interpreter.java 文件头 Apache-2.0；嵌入 ASM/LICENSE.txt 为 BSD-3-Clause | 顶层无 LICENSE；新 snapshot/BshSnapshot.java 没有单独文件头；未完成全部新增/移植文件的来源审计 |
| Ujhhgtg/reflekt | `3807ae1019115b93cb17eb56810823969b5bfcac` | 完整目录树未发现 LICENSE/COPYING/NOTICE，抽查 Reflekt.kt 没有许可证头 | 不能假设顶层 WeKit GPL 自动替代这个独立仓库的授权声明 |
| Ujhhgtg/scripta | `2bc2e42c5f04ad99c89770e943df37dd06da7c49` | 根 LICENSE 为 Apache License 2.0 | 未做所有传递依赖许可证审计 |
| LSPosed/LSPlant | `d8b5d1dbb664abc606644036822e4bb64547edf6` | 根 LICENSE 为 GNU LGPL Version 3 | 不应据许可证示例条款自行加上 or-later；传递 DexBuilder 等需按具体文件核查 |
| berilevi/proot-static | `6f8ebfd8e24887dfba64c3f2d7d5fe9dc059b60e` | COPYING 含 GPL Version 2 正文；README 说明携带 talloc-2.4.2 | 是否允许 later/具体嵌入依赖的许可，需看项目实际授权声明和文件头，不能仅看 GPL 模板最后的示例 |
| tukaani-project/xz-embedded | `ae63ae3a36ed01724674e8f3d750dc47bf125410` | COPYING 给出允许为任何目的复制/修改/分发的宽松授权及无担保条款 | 此处未对其所有文件再归类 SPDX |

固定证据链接：

- [WeKit LICENSE](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/LICENSE)
- [WeKit 子模块声明](https://github.com/Ujhhgtg/WeKit/blob/650febaa3717eddd39f5f29a4dfa6dfd37875cb1/.gitmodules)
- [bsh Interpreter.java](https://github.com/Ujhhgtg/bsh/blob/66fabc1aa1fa0db1733e7453721e73a3a5e34638/src/main/java/bsh/Interpreter.java)
- [bsh ASM 许可证](https://github.com/Ujhhgtg/bsh/blob/66fabc1aa1fa0db1733e7453721e73a3a5e34638/src/main/java/bsh/org/objectweb/asm/LICENSE.txt)
- [bsh BshSnapshot.java](https://github.com/Ujhhgtg/bsh/blob/66fabc1aa1fa0db1733e7453721e73a3a5e34638/src/main/java/bsh/snapshot/BshSnapshot.java)
- [reflekt 固定树](https://github.com/Ujhhgtg/reflekt/tree/3807ae1019115b93cb17eb56810823969b5bfcac)
- [scripta LICENSE](https://github.com/Ujhhgtg/scripta/blob/2bc2e42c5f04ad99c89770e943df37dd06da7c49/LICENSE)
- [LSPlant LICENSE](https://github.com/LSPosed/LSPlant/blob/d8b5d1dbb664abc606644036822e4bb64547edf6/LICENSE)
- [PRoot COPYING](https://github.com/berilevi/proot-static/blob/6f8ebfd8e24887dfba64c3f2d7d5fe9dc059b60e/COPYING)
- [XZ COPYING](https://github.com/tukaani-project/xz-embedded/blob/ae63ae3a36ed01724674e8f3d750dc47bf125410/COPYING)

## 8. 使用这份地图

先在 [功能索引](FEATURE_INDEX.md) 找到目标对象，再跟踪其完整调用链，按 [Hchat 接入地图](HCHAT_INTEGRATION.md) 复用现有底座。参考副本按 [本地准备](LOCAL_SETUP.md) 固定版本；具体方案、来源记录与验收使用 [逐功能工作流](PORTING_WORKFLOW.md)。
