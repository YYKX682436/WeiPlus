# WeKit → Hchat 功能移植入口

这份资料用于：用户说「把 WeKit 的某个功能整合进 Hchat」后，讨论窗口能迅速定位源码、分析差异并给出确定的执行指令；本地 Codex 能读取同一份源码和接入说明完成实现。

**先讨论方案，再实现单个功能。这里是导航与静态基线，不是全部功能已经适配或通过验收的声明。**

## 1. 已固定的研究基线

| 项目 | 仓库与基线 | 用途 |
| --- | --- | --- |
| WeKit | [`Ujhhgtg/WeKit`](https://github.com/Ujhhgtg/WeKit)，`master`，[`650febaa3717eddd39f5f29a4dfa6dfd37875cb1`](https://github.com/Ujhhgtg/WeKit/commit/650febaa3717eddd39f5f29a4dfa6dfd37875cb1) | 本索引和来源链接对应的参考版本 |
| Hchat | [`YYKX682436/WeiPlus`](https://github.com/YYKX682436/WeiPlus)，`main`，[`332c9650103fa68fc5f755d4c2d8cef4502d0c34`](https://github.com/YYKX682436/WeiPlus/commit/332c9650103fa68fc5f755d4c2d8cef4502d0c34) | 接入地图的源码基线；每次实施仍读取当前 Hchat |
| 记录日期 | 2026-11-06，Asia/Shanghai | 研究日期，不代表真机测试日期 |

Hchat 此基线版本为 `1.2.2 / 261106`，编译配置为 libxposed API `102.0.0`、`minSdk 27`、`compileSdk/targetSdk 37`、ARM64。实际版本与依赖以当前 `gradle.properties`、`app/build.gradle.kts` 为准。

WeKit 上游文档把国内微信 `8.0.65–8.0.68` 列为维护、`8.0.69–8.0.76` 列为支持、`8.0.77–8.0.78` 列为初步适配。本项目已存在微信 `8.0.80 (3220)` 的适配记录；**不能把上游对旧宿主的声明转换成 Hchat 当前版本可直接运行的证据**。每次以本机实际 APK、versionCode、构建来源和热更新状态取证。

## 2. 按需阅读

| 你现在要做什么 | 先读什么 |
| --- | --- |
| 第一次使用这套资料 | 本页 → [本地准备](LOCAL_SETUP.md) |
| 根据中文名寻找功能 | [功能索引](FEATURE_INDEX.md)；精确检索用 [feature-index.json](feature-index.json) |
| 判断借用实现需要哪些底层组件 | [WeKit 架构与来源边界](SOURCE_ARCHITECTURE.md) |
| 找到 Hchat 的注册、设置、API、缓存接入位置 | [Hchat 接入地图](HCHAT_INTEGRATION.md) |
| 写本次方案、Codex 指令、验收结果 | [逐功能工作流](PORTING_WORKFLOW.md) |
| 核对固定版本和索引范围 | [baseline.json](baseline.json) |

以上文档均可直接从 GitHub 读取。本地参考源码位于仓库内被 Git 忽略的 `references/WeKit/`；它是独立参考仓库，不参加 Hchat 编译，也不提交整份源码。

## 3. 新讨论窗口从这里开始

用户只需说明想要的功能、期望效果，以及与上游不同的要求。例如：

> 我想把 WeKit 的「快捷回底」整合进 Hchat。请按仓库 `docs/upstream/wekit/README.md` 的入口，先读取双方相关源码，分析 Hchat 的接入位置和现有冲突，再给我本地 Codex 指令。

讨论窗口应依次执行：

1. 读取当前 Hchat 的 [AGENTS.md](../../../AGENTS.md)、[项目工作指引](../../../skills/hchat-project/SKILL.md)、本页和当前分支 HEAD。不能用旧聊天记忆代替源码。
2. 用中文名、近义词、`technicalId` 或类名定位索引中的对象。若只有截图或名称模糊，先利用描述和源码缩小范围；只有存在会改变产品行为的多种解释时才提问。
3. 读取固定 WeKit SHA 下的功能入口、实际使用的 API/工具/设置/资源与相应上游文档；索引中记录的依赖只作导航，不能代替完整调用链检查。
4. 读取 Hchat 当前对应实现和共享 API，判断为「已有能力复用」「在已有功能内补齐」或「新增独立功能」。名称相近不等于语义一致，已有功能也不能直接假定没有缺陷。
5. 输出用户行为、接入文件、依赖映射、宿主证据、测试范围和待确认事项，再生成第一人称 Codex 指令。缺少本机 APK 证据时，先给一段范围明确的只读取证任务。
6. Codex 回报后，核对实际 commit、diff 和证据。测试通过、定位成功、真机可用分别报告。

跨窗口不会自动继承本次聊天的完整上下文。要达到「以后只说功能名」，请在 WeiPlus 项目指令保留下面这一句，或在新窗口附上本入口链接：

> 涉及借鉴或移植 WeKit 功能时，先读取 https://github.com/YYKX682436/WeiPlus/blob/main/docs/upstream/wekit/README.md ，按其中索引分析当前源码，再生成给本地 Codex 的执行指令。

## 4. 共同采用的实现方向

- 保留 Hchat 官方 API 102、`ModuleEntry`、`FeatureRegistry`、`FeatureManager`、`HookRegistry`、`DexInstallScheduler`、`HchatStorage` 和 Miuix 设置体系；借用上游的功能算法、可靠的宿主定位证据及必要的最小实现。
- 优先使用 `WeChatApis` 中已有能力。Hchat 中已有相同业务时，在原归属内合并，避免双份开关、重复监听、重复发包和两套缓存。
- 上游 `hookBefore` / `hookAfter` 不能简单换名。必须改写为官方 `Hooker/Chain`，核对正常路径 proceed 一次、短路零次、宿主异常原样传播、清理在 finally 中且不重复执行副作用。
- 上游 WePrefs/MMKV、reflekt、KSP 注册、Xposed 旧入口、Zygisk/Frida、Rust 构建和额外服务均按实际功能需要逐项判断；不会因移植一个小功能自动引入。
- 普通开关在回调中读取当前配置；重定位、网络和数据库工作按需安排。共享 Hook 的拥有者、异步取消、页面退出和功能销毁都应具体说明。
- 上游的 AGENTS.md 说明上游自己的开发方式，不覆盖 Hchat 的项目指令。尤其不照搬其个人目录、分支约定、宿主版本范围或 best-effort 清理策略。

## 5. 研究完成范围

本次已固定双方源码版本，核对 WeKit 的源码发现机制、功能名称与入口，深读双方的核心加载、Hook、配置、缓存和 UI 接入链路，并整理现有能力重叠和后续验收格式。

本次没有逐一证明每个 WeKit 功能的全部行为，没有对 262 个注册对象进行目标微信真机验收；索引中的服务、调试项和可选功能也不应混为用户功能数量。本次没有移植业务代码、改变 Hchat 版本号或触发 APK 发布。

## 6. 维护方法

Hchat 会持续迭代，因此每次任务读取当前 Hchat，并仅复核与目标功能相关的差异。WeKit 参考副本保持固定 SHA，避免讨论和执行看到两份不同源码。

若用户点名的功能不在当前索引，先检查它是否在 WeKit 后续版本新增或改名，再由讨论窗口选定新 SHA。仅某个任务采用更新实现时，在该功能的方案/来源卡写明覆盖 SHA 和新入口，并使用另一份干净参考副本；全局索引仍保留原版本，不能只改总 SHA 就冒充全部条目已更新。

只有正式更新整个研究基线时，才同时更新 `baseline.json`、功能索引/JSON、统计、架构链接及本页对应版本；本地准备从 `source.commit` 读取 SHA。不要静默切到最新 `master`。已经开始的移植继续使用该任务已确认的 SHA，除非重新评估并同步调整指令。

后续每个功能的方案和结果可放入 `docs/upstream/wekit/ports/<feature-id>.md`，按 [工作流模板](PORTING_WORKFLOW.md) 记录状态、来源与验证证据。这个目录按实际需要创建，不预建空报告。
