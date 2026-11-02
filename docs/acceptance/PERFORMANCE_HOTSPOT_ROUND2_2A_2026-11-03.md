# WeiPlus ②-2A：全局性能热点定位

日期：2026-11-03。源码基线 `main` / `1ee352fcb0432fc47ea937c961a9ed88bd42b59c`；本轮只分析和采样，没有修改业务代码、功能开关、LSPosed 作用域，没有重新发布 APK。结论是对当前设备和场景的观察，不代表所有配置或微信版本。

## 结论与优先级

| 顺序 | 候选及实际证据 | 可归因开销与预期收益 | 实施风险和建议 |
|---|---|---|---|
| 1 | 启用屏蔽热更新时的 `Hchat-HotUpdate` / DexKit 启动工作：三次 CPU 采样窗口分别占目标进程采样周期的 38.29%、31.78%、35.53%；一次 trace 中同名线程累计运行 9.484 CPU 秒。实际原生调用栈见下文。 | 是模块启动工作，且重复出现；可能减少启动 CPU 和串行等待，但尚无可交互时间收益证明。缓存缺项是候选原因，不是已证实的唯一根因。 | 中高：拦截热更新与类加载时序敏感。②-2B 先补逐定位器观测并验证，再仅对证实重复的工作做最小修复；不得仅凭空结果做永久负缓存。 |
| 2 | 公共 API 就绪链和串行门：三次进程重启日志中消息观察从 `hook=false` 到 `hook=true` 相隔 7.073 / 6.034 / 6.137 秒；另外两次为 6.091 / 6.105 秒。 | 是可核查的就绪间隔，不等于 DexFinder 耗时、锁等待或所有功能就绪时间。DexKit 共享门和启动工作可能影响顺序，但锁竞争时长 **NOT_VERIFIED**。 | 高：过早发布 `DexReady`、拆锁或改变安装次序可能丢事件、破坏 Tinker 身份和卸载代次安全。先测每段及依赖，不能直接并行化。 |
| 3 | 自动回复单队列等待：生产源码使用单线程执行器，同一任务内有延迟和同步 AI/语音等待；本机自动回复开关已启用，但采样没有安全受控地触发相应消息或请求。隔离 FIFO 模型三次后续任务等待 1004.1 / 1005.2 / 1002.1 ms，前任务被人为阻塞约 1 秒。 | 是确定的串行机制与潜在队头等待；模型并非生产代码实测，不能当作本机实际延迟或 CPU 热点。 | 中高：分队列/并发可能改变同会话顺序、冷却、去重、好友申请和发送语义。暂不列入直接实现，需先用生产代码隔离桩测量。 |
| 4 | 聊天滚动与首页往返帧卡顿：三次滚动 jank 5.07% / 4.93% / 4.46%，P95 均 11 ms；Perfetto 捕获主线程 `doFrame → traversal → layout` 的 53.38 / 53.18 / 45.35 ms 片段。 | 观测到卡顿，但没有可归因到模块回调的调用栈；前两轮分别叠加 CPU 采样或 trace。不能据此优化模块或宣称收益。 | 风险取决于具体 Hook；应先补可解析的 Java 栈、帧关联及对照。 |

**本轮不建议直接进入实现。** ②-2B 建议限定为首项的定位证据补齐和确认后的单点修复；若逐定位器证据不能复现，停止并保持现状。自动回复、绘制、全局预热和锁拆分不应顺带重构。

## 环境、配置与方法

- Xiaomi 22081212C，Android 15 / API 35，KernelSU Root；微信 `com.tencent.mm` 8.0.80 / 3220。安装的模块为 1.2.0，APK SHA-256 `2c0c94ba60addafb3b1a042d9d97b82e2dc6f88b967bac92ac8360a3c27061fc`，与上一轮最终设备包一致；源码对应关系依据上一轮验收记录，并非本轮重建验证。LSPosed 配置只读检查显示模块启用、作用域为官方微信。
- 检查 FastKV 当前状态：`auto_reply_enable` 为 true，好友自动通过键未见；悬浮底栏与原生自定义底栏配置存储均为空，对应默认关闭。读取的是采样后的状态，不等于每次回调均执行；未读取或记录 AI 密钥、会话内容。FastKV 的布尔编码通过本机 3.0.1 库字节码核对。
- 三次进程重启使用 `am force-stop` 后启动 `LauncherUI`，**未清除定位缓存**；系统的 Splash `TotalTime` 分别 1873 / 1820 / 1718 ms。系统将这三次标记为 WARM，即使进程已终止；后两次额外采样分别为 1766 ms（WARM）和 1395 ms（COLD）。这些是启动页而非主页可交互时间。三次重启间 `patch_meta.info` SHA-256 均为 `133be4efeb952c096cd6e79af02625a3084cf15b1dd706b2b4b19ee050bc69da`；各次日志均显示宿主身份批准。相同元数据不保证 ClassLoader 与全部缓存身份相同。
- 三轮在同一聊天内各做 8 对固定上下滑动；第一轮 `simpleperf` 199 Hz `cpu-cycles` / frame-pointer 栈，第二轮 Perfetto 25 秒，第三轮不叠加这两种采样；均重置 `gfxinfo`。另连续三次点击同一聊天、返回首页并上下滑动，合并一次帧统计，不能分离每次进出或首页刷新。未主动触发消息发送、AI 请求、好友操作或更改配置。
- 启动首轮 Perfetto 与 simpleperf 有重叠；两次补充启动 CPU 采样为 99 Hz、各 8 秒。Perfetto 收集 sched、waking、gfx、view、wm、am、dalvik，可能影响调度和帧。采样后日志及配置读取仅做只读诊断；后台最初息屏，CPU 使用 `cpu-clock` 测试。没有主动 GC；第一轮滚动期间执行过 `dumpsys meminfo`，CPU 栈中出现 ART `CountInstances`，可能包含诊断干扰，因此不能作为自然 GC 或分配热点归因。
- 外部消息、微信自身网络工作和系统负载不可完全控制；开始负载约 0.27 / 0.39 / 0.75，末尾约 4.09 / 3.38 / 1.96。未建立隔离消息环境、无模块关闭对照。三次之间操作与诊断开销并不完全相同，数字不是前后收益比较。
- 原始 Perfetto、simpleperf、帧统计、采样日志和隔离模型结果保留在本机 Git 忽略目录 `dist/round2_2a_raw/`；文件可能包含运行信息，不提交。截图只用于确认页面，完成后删除；设备端本轮临时采样文件已清理。分析输出 `trace_analysis.txt` 中启动进程名因 trace 元数据沿用了 `zygote64`，使用进程 PID、线程 ID 与启动日志关联，不能把全部 `zygote64` 线程算作微信。trace 记录一次 `systrace_parse_failure`；结果不作为完整时序证明。

## 启动调用链、缓存和 CPU

1. [`ModuleEntry`](../../app/src/main/java/h/Hchat/ModuleEntry.java) 在目标进程的 Tinker attach 安装早期 Hook。Application `onCreate` 返回后先做身份验证和必须的早期页面安装，再启动 `Hchat-Init`。屏蔽热更新启用时，attach 后另起 `Hchat-HotUpdateHook`，通过 `DexInstallScheduler.runDexKitTask` 进入串行门；线程及其原生子线程名称在系统中会截断/继承。
2. `Hchat-Init` 在同一 DexKit 门内创建受管理的 bridge、`DexFinder`、共享上下文和功能管理器；[`FeatureManager`](../../app/src/main/java/h/Hchat/hooks/core/FeatureManager.kt) 先逐项 `onInit`、按策略安装，之后发布 BRIDGE 就绪。bridge 按需初始化，30 秒空闲释放 native 分析资源，不能把动态库映射等同于正在扫描。
3. [`WechatApiFeature`](../../app/src/main/java/h/Hchat/hooks/api/core/WechatApiFeature.java) 先创建 API 并尝试安装基础 Hook、注册 `WeChatApis`，再排队 `resolveAll` 和额外定位/补装。只有解析流程完成才发布 WARMUP 和 `DexReady`；这不保证每个关键定位均成功。后续功能按各自 stage、优先级和重试规则安装。`DexInstallScheduler` 使用单 worker 和共享 `dexKitExecutionLock`，含有生命周期代次和取消保护。
4. [`DexFinder.resolveAll`](../../app/src/main/java/h/Hchat/dexkit/DexFinder.java) 首先加载按运行时身份、快照版本及完成状态校验的持久快照；失配或描述符无效会回退相应解析。它是同步方法，冷缓存扫描与进程冷启动是不同条件。此次结束后本机全局缓存 `cache.complete`、`cache.scan.finished`、`cache.critical.complete` 均为 true；`VERBOSE=false`，无逐次命中日志，不能证明这五次均完整命中或量化冷热定位耗时。没有人为删除缓存制造冷扫描。
5. 屏蔽热更新的 [`DisableHotUpdateFeature`](../../app/src/main/java/h/Hchat/hooks/items/hotupdate/DisableHotUpdateFeature.kt) 对三个定位点分别尝试跨进程描述符缓存；空结果调用 DexKit 查找，找不到则清除该项。采样后缓存文件中可见 `sync_response_consumer` 和 `manual_updater` 键，未见 `upgrade_response` 键。此文件字节检查不是逐次运行时命中跟踪：空项可能引起重复查找，但身份校验、查找结果和每个定位器耗时均 **NOT_VERIFIED**。不得据此断言唯一根因或默认缓存空结果安全。

首次启动采样在 `am start -W` 返回后附着，实际采样 12.18 秒，5781 样本、0 reported lost；`Hchat-HotUpdate` 1085 样本贡献 38.29% 的采样事件周期，另外两次 556 / 446 样本贡献 31.78% / 35.53%。这些百分比是各自采样窗口内目标进程加权 CPU cycles 占比，**不是单核利用率、墙钟时间或整个启动过程占比**。频率、窗口和系统负载不同，不应做精确横向比较。

实际截取的栈是 `__start_thread → __pthread_start → libdexkit.so+0x2e14a → +0x2b95e → +0x3253e → +0x5399a → +0x53b2e → +0x456d2 → +0x50b22 → +0x4a0f6 → +0x4a7dc`。库无符号，筛选后调用链错误率约 42.95%；不能从该栈恢复 Java 调用者、具体查询或把偏移命名成某个算法。线程名称、调用栈和代码共同支持模块 DexKit 工作归属，不单凭线程名称排除微信线程中的 Hook。

启动 Perfetto 25 秒中，与该主进程 PID 关联的 26 个 `Hchat-HotUpdate` 同名线程合计 9483.5 ms CPU；`Hchat-Init` 同名线程 2343.6 ms，`Hchat-DexInstal` 同名线程 2032.9 ms。多线程 CPU 时间可大于墙钟时间，线程名称可继承，不能将其视为单个 Java 线程或单个方法耗时。sched 的睡眠/可运行时间不能证明具体 monitor 持有者；主线程被 DexKit 锁阻塞、缓存命中耗时、各功能首次可用时刻均 **NOT_VERIFIED**。

## 前台、内存和后台

| 场景 | 帧数 | jank | P95 / P99 |
|---|---:|---:|---:|
| 聊天滚动 R1：simpleperf | 513 | 5.07% | 11 / 18 ms |
| 聊天滚动 R2：Perfetto | 588 | 4.93% | 11 / 19 ms |
| 聊天滚动 R3：无上述采样 | 516 | 4.46% | 11 / 17 ms |
| 进出聊天与首页滑动合并三次 | 727 | 7.43% | 16 / 65 ms |

滚动采样 2752 个 CPU cycles 样本、0 reported lost、6.80% 错误调用链。可核查的一条原生栈为 `RenderThread → WorkQueue::process → DrawFrameTask::postAndWait → CanvasContext::draw → SkiaOpenGLPipeline::draw → SkiaPipeline::renderFrame → RenderNodeDrawable::drawContent → DisplayListData::draw`；这是系统渲染路径，不证明模块造成。主线程叶子中有微信 `MMPullDownView.dispatchTouchEvent` 3.47%（目标进程采样周期），以及 HWUI `android_view_RenderNode_hasIdentityMatrix` 3.65%；ART `CountInstances` / `ConcurrentCopying::MarkRoot` 发生在 binder 线程，可能受本轮 `dumpsys meminfo` 影响。未有可靠模块 Java Hook 栈，不能把余下成本全判为微信自身。

滚动 trace 中主线程和 RenderThread 分别运行 3337.6 / 2723.6 ms（整个约 24.92 秒窗口，不是每帧耗时）；最长帧片段和布局片段如上。trace 没有足够信息将该片段归因到某个模块 Hook 或具体分配。聊天往返的截图确认只在测试前，合并数据没有每次操作的独立帧区间，因此页面切换根因及首页刷新开销 **NOT_VERIFIED**。

一次滚动期间 PSS 810971 KiB；第二、三次启动约 15 秒后 PSS 分别 1255828 / 1152152 KiB。采样时机、不同任务状态、累积启动和诊断开销不同，PSS 是整个进程共享/私有内存的快照，不是分配速率、泄漏或模块占用。热分配调用栈和对照 **NOT_VERIFIED**；不能主动 GC 制造差值。

后台原状息屏时的第一段约 12.56 秒记录仅 1 个 `cpu-clock` 样本，栈为 `wc_lp_srvinit_1 → _copy_to_user`，不能用于热点排序。采集前后进程 ticks 增加 28（`CLK_TCK=250`，约 112 ms CPU；区间含采样后处理，不是严格 12.56 秒），线程数为 263。第二段中途唤醒屏幕，第三段微信转前台，均不再是相同后台条件；样本分别 0 / 1，不计入三轮后台对比。采样出现大量 DEX 符号读取警告；周期任务、唤醒和无名线程中的模块 Hook 无法从这组数据完整归因。上一轮在屏幕保持清醒的后台 60 秒条件下，最终包三轮进程 CPU 为 16.87% / 14.89% / 14.79% 单核，存续 `Hchat` 命名线程为 0%，但不能将其余 CPU 全归于微信，也不能与本次息屏短段比较。

## 专项与不值得立即优化的候选

- [`AutoReplyRuntime`](../../app/src/main/java/h/Hchat/hooks/items/autoreply/AutoReplyRuntime.kt) 的 `handleMessage` 和 `handleNewFriend` 共用 `newSingleThreadExecutor`；`executeSteps` 在该 worker 内 sleep，AI 同步 HTTP、备用请求及小智等待会占用同一执行位置，后续任务须排队。隔离模型只是验证单 worker 的队头阻塞机制，未执行生产 Runtime，也没有请求网络或发消息；本机启用不代表此次样本有触发、排队或 AI 请求。实际队长、等待分布、超时、内存增长以及跨会话收益 **NOT_VERIFIED**。
- [`ViewBackdrop`](../../app/src/main/java/h/Hchat/ui/miuix/ViewBackdrop.kt) 的 `drawBackdrop` 在调用时执行 `recordSource` 并 `view.draw`，pre-draw 在源 View dirty 时增加版本；[`CustomBottomBarContent`](../../app/src/main/java/h/Hchat/hooks/items/custombottombar/CustomBottomBarContent.kt) 为悬浮玻璃底栏创建 backdrop。当前底栏配置为空、默认关闭，且聊天滚动不是该控件启用态，不能归因为本次热点。启用时实际录制频次与成本 **NOT_VERIFIED**。补充独立 Android `app_process` 离屏模型：每次以 `Picture` 录制 100 个合成矩形，三轮各 300 次录制、300 次合成 draw，录制中位数为 493 / 489 / 457 μs，P95 为 613 / 531 / 530 μs；[探针源码](../../scripts/diagnostics/BackdropProbe.java) 已提交，设备输出保存在 `dist/round2_2a_raw/draw_probe/`。模型不执行生产 Compose `GraphicsLayer`、真实 `View.draw`、GPU 模糊或微信界面，不能推算真实频次、开销或收益；缓存/脏区优化可能导致背景滞后、坐标或模糊错误。
- Silk 启动按需加载、朋友圈按到期调度、聊天行关闭态快速返回、共享群监控和消息补全，已有第一轮及 ②-1 优化；本次未复现新的可归因热点，不重复改造。朋友圈自动刷新上一轮实机默认关闭，其启用态后台收益仍未验证。

## ②-2B 建议边界与验收

1. 用独立、可关闭且不记录用户内容的诊断，在相同 APK/微信/Tinker 身份下分别记录屏蔽热更新三个定位器的缓存判定、实际扫描次数和墙钟/线程 CPU；对全局快照记录命中与各解析阶段、BRIDGE/WARMUP、消息观察和功能就绪事件，记录锁获取前后时间与持有者。使用受控合成或隔离副本验证缓存未命中，不清除生产微信数据或配置。必要时用有符号调试构建和不改变 LSPosed scope 的隔离环境补栈。
2. 只有证实某项每次重复扫描且能保留版本、APK、DEX、Tinker 和 ClassLoader 安全约束时，实施最小缓存/调度修复。重点验证有效结果、空结果、错误/不完整结果、身份变化、跨进程、失效恢复及并发；不要把暂时失败永久当作无匹配，也不要提前发布就绪。保留早期拦截功能与原安装顺序。
3. 至少三轮同条件重复，记录无采样和采样开销；比较目标定位器扫描次数、累计 CPU、墙钟及用户可交互上界。建议以证实的重复扫描消除为功能门槛，并以目标定位器 CPU 中位数下降至少 30% 为待确认目标，而非本轮已达成承诺；主线程、消息观察就绪、首帧/交互、P95/P99 jank 和内存不能出现可复现的实质回退。出现噪声或缓存身份漂移时补样本，不以三次微小差异宣称收益。
4. 验收需覆盖已有缓存、受控未命中、异常重试、Tinker 身份变化、功能早期保护、卸载代次和 30 秒资源释放；不卸载微信、不清数据、不主动 GC、不改变生产开关、不触发真实消息或 AI 请求。自动回复与 ViewBackdrop 如需推进，先建立运行生产代码的无外部副作用隔离探针及独立验收，再另行确认范围。

此前性能与限制见 [②-1 启动和后台验收](STARTUP_BACKGROUND_ROUND2_1_2026-11-02.md) 和 [第一轮内存与卡顿验收](MEMORY_JANK_ROUND1_2026-10-24.md)。本报告未声明整体性能改善。
