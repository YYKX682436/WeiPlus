# WeiPlus ②-3：全局性能优化最终综合验收

日期：2026-11-03
仓库：`/Users/muchen/Documents/ChatGPT/WeiPlus`
冻结基线：`main` / `da449bde32ae62ea3380c7d36c4e8a4d13ca2538`
总体结论：**PASS**

本轮未修改业务源码、未调整版本号、未发布新版本或创建 GitHub Release，也未更换签名。完整自动回归、Release Lint、正式签名 R8 构建及正式包真机启动通过；设置入口、聊天页面往返与固定页面滚动已经通过无障碍节点复验。需要真实业务输入或外部副作用的功能仍标记 NOT_VERIFIED；CPU、PSS、jank、耗电的整体优化收益没有配对基线证明。

## 1. 基线、设备和状态

开始时工作区干净，`HEAD`、本地 `main` 与 `origin/main` 均为冻结提交。设备为 Xiaomi 22081212C / Android 15 / SDK 35；宿主为官方微信 `com.tencent.mm` 8.0.80 / 3220；LSPosed 2.2.1-log 中 `h.Hchat` 在官方微信主进程和相关子进程加载。最终测试没有卸载微信或清除其数据，没有触发消息发送、AI 请求、红包、收款或转账，也没有切换生产功能开关。

最终启动与静态性能三轮使用的 `patch_meta.info` SHA-256 均为 `eef71592e267b5781c8d5223ca1177d08596c29a608f29c05768902d4c1b9389`。之后的无障碍 UI 复测在新 WeChat PID `11734` 上观察到当前 `patch_meta.info` 为 `4c2c3131ec507a13b4f0eaa762158151914801aaa31f7837bc40bee2715d6dcc`；该运行时身份获准。UI 三轮的 Tinker 文件全集哈希逐轮前后相同，且热更新方法缓存和空结果标记目录及内容哈希均相同。两个阶段的 patch 身份不同，因此不把两组数据当作相同 Tinker 身份配对比较。

验收前的历史基线尝试使用了第一轮 R3 APK。它与最终包同包名、版本码和签名，但缺少后续轮次的代码；该旧包启动时记录过 `tablet_tinker_attach_after:runtime_identity_changed`，所以其数据被排除。该预检阶段 WeChat 的 Tinker patch 子文件发生变化，`patch_meta.info` 未变化；正式三轮在该事件之后进行。启动性能三轮的原始 Tinker 子文件未逐轮分别快照；UI 三轮保存了完整文件哈希且各轮不变。

另有一个历史正式 APK `dist/Hchat-1.2.0-2b2-v4-release-signed.apk`。它与本轮正式包的 `classes.dex`、全部 ARM64 native library 和其他 APK 条目内容一致，只有 `META-INF/version-control-info.textproto` 构建元数据不同；两包均为 `h.Hchat` / `1.2.0` / `261030`、同一证书和 v2 签名。故它是相同代码载荷的重建包，不能提供有意义的代码前后性能对照。②-2B-2 报告中的诊断性能数值来自插桩包，本报告不混入正式包性能数据。

设备上 292 个 Hchat FastKV 文件的验收前后哈希有 110 个文件发生变化，即 55 对槽位。变更项均为定位器或运行时缓存；`Hchat_forwarded_record_type_v1` 也由 `DexMethodCache.prefs` 持有，属于定位缓存。未发现功能配置槽位变更，也没有通过 UI 修改设置。LSPosed 配置数据库文件哈希有变化，但本轮没有修改其作用域或设置；最终包仍在官方微信进程加载。由于没有在前后各自查询数据库中的作用域记录，本报告不声称配置数据库逐字节相同。

## 2. 第一轮及②-1、②-2最终复核矩阵

| 阶段 | 当前源码复验 | 仍成立的路径 | 边界 |
|---|---|---|---|
| 第一轮 R1–R3 | `run_performance_round1_tests.cjs`、`run_native_regression_tests.cjs`、消息阻断、数据库链、生命周期链、HookRegistry 均通过 | 消息 lease/observer 换代，转发规则重核验，头像 LRU/失败重试/跨头像隔离，弱引用与页面清理仍通过隔离回归 | 真机没有更改头像或发送转发消息；头像实际失败重试和真实转发仍未验证 |
| ②-1 | `run_performance_round2_tests.cjs`、语音兼容、Native 提取和阶段 B 通过 | Silk 按需加载/失败重试、音频入口、朋友圈调度与销毁回归通过；最终正式包主进程中没有映射模块 `libsilk_codec` | 未触发真实音频、红包或收款业务；朋友圈启用态未切换 |
| ②-2A | 阶段 B、DexKit 运行时身份和安装代次回归通过 | 串行门、早期 Tinker Hook、身份隔离及正向 Hook 保留仍成立 | 历史热点日志来自此前运行；本轮不把它们当成本轮性能测量 |
| ②-2B-1 | DexKit 缓存、运行时身份、HookRegistry 与阶段 B 回归通过 | 正向描述符和初始化顺序继续保留 | 负缓存功能尚未在该阶段验收 |
| ②-2B-2 | `UpgradeResponseEmptyCacheTest`、`UpgradeResponseResolverTest`、`UpgradeResponseHookIntegrationTest` 纳入的 Android 单元测试通过；当前 marker 在正式包三轮启动前后保持稳定 | 完整身份下的空结果缓存、异常/无效缓存回退和原正向 Hook 测试仍通过；正式包日志还观察到 Tinker 早期 Hook 安装 | 正式包没有扫描计数插桩；设备上以缓存文件稳定和 Hook 日志间接复核，不把它说成直接测得 0 次查询 |

历史报告：[第一轮 R3](MEMORY_RUNTIME_OPTIMIZATION_ROUND1_R3_2026-11-02.md)、[②-1](STARTUP_BACKGROUND_ROUND2_1_2026-11-02.md)、[②-2A](PERFORMANCE_HOTSPOT_ROUND2_2A_2026-11-03.md)、[②-2B-1](HOT_UPDATE_DEXKIT_DIAG_ROUND2_2B_1_2026-11-03.md)、[②-2B-2](HOT_UPDATE_EMPTY_CACHE_ROUND2_2B_2_2026-11-03.md)。

## 3. 自动回归、Lint 和构建

| 命令 / 覆盖 | 结果 |
|---|---|
| `node scripts/run_performance_round1_tests.cjs /tmp/weiplus-final-2-3-kotlin-libs` | PASS；消息状态、行绑定刷新、bubble hook 与转发/头像路径断言通过 |
| `node scripts/run_native_regression_tests.cjs /tmp/weiplus-final-2-3-kotlin-libs` | PASS；observer lease、头像驱逐/退避/隔离、规则重核验、并发 Runtime、旧回调拒绝、支付与自动回复生命周期通过 |
| `node scripts/run_message_block_tests.cjs` | PASS，61 项断言 |
| `node scripts/run_database_chain_tests.cjs /tmp/weiplus-final-2-3-kotlin-libs` | PASS |
| `node scripts/run_lifecycle_chain_tests.cjs` | PASS |
| `node scripts/run_hook_registry_tests.cjs /tmp/weiplus-final-2-3-kotlin-libs` | PASS；含 1000 次移除周期、失败回滚和旧代次拒绝 |
| `node scripts/run_performance_round2_tests.cjs` | PASS；Silk 需求/失败重试/并发、朋友圈时钟与销毁、红包/收款/进退群语音门、Native load cache 与调度器通过 |
| `node scripts/run_voice_compat_tests.cjs` | PASS，76 项 |
| `node scripts/run_native_extraction_tests.cjs` | PASS；CRC、损坏、替换和失败清理 |
| `node scripts/tests/phase_b/run.js`、`node scripts/run_contact_api_tests.cjs` | PASS；Tinker identity / DexKit 生命周期、DexFinder 与联系人调用链通过 |
| `node scripts/run_redpacket_silent_tests.cjs` | PASS；329 项 / 25 场景，另有 UI 17 项 / 4 场景 |
| `node scripts/run_redpacket_parser_tests.cjs`、`node scripts/run_redpacket_logging_tests.cjs` | PASS，各 32 项 |
| `node scripts/run_settings_bridge_tests.cjs /tmp/weiplus-final-2-3-kotlin-libs` | PASS；设置桥接导入/导出启动、失败回滚和旧回调回归 |
| `node scripts/tests/startup_version/run.js`（临时 Android `Os/StructStat` 测试桩） | PASS，27 项。独立脚本未包含桌面 JDK 所需 Android 系统类型；通过临时测试桩补足运行时后通过，未改产品源码 |
| `./gradlew --no-daemon :app:testDebugUnitTest` | PASS，124 个单元测试，0 failure / 0 error / 0 skipped；包含 `UpgradeResponseEmptyCacheTest`、正向 Hook 集成、Dex/Tinker 身份与 observer readiness |
| `./gradlew --no-daemon :app:lintRelease :app:assembleRelease` | PASS；完整 Release Lint 0 error、654 warning、50 hint；R8、正式签名和 APK 复制均完成 |

项目没有生成 `:app:testReleaseUnitTest` 变体；本次 Android 单元测试使用实际存在的 `testDebugUnitTest`。少数隔离脚本首次因临时 Kotlin 依赖目录未准备而没有启动，准备目录后均通过，不计为源码失败。

## 4. 正式 APK、安装和签名

- 包名：`h.Hchat`
- 版本：`1.2.0` / 版本码 `261030`
- 最终 APK：[Hchat-1.2.0-release-signed.apk](../../dist/Hchat-1.2.0-release-signed.apk)
- APK SHA-256：`35177bb50e53a372335fe4cee3b64b2b37dba74e8f26dc4fadcdfa7a76718cc7`
- 签名证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`
- `apksigner verify --verbose --print-certs`：APK Signature Scheme v2 **true**；v1 / v3 / v3.1 / v4 均未启用。
- `adb install -r` 成功；设备安装的 `base.apk` SHA-256 与本地正式包一致。未卸载微信、未清微信数据。

## 5. 最终真机性能复测

### 方法与限制

使用未插桩正式签名 APK，不主动 GC、不清微信缓存或数据、不更改功能设置。每轮对微信主进程执行 `am force-stop` 后通过 `LauncherUI` 启动；Android 将这三次启动分类为 `WARM`，所以它们是进程终止后的重新启动样本，不是系统 `COLD` 启动。`am start -W TotalTime` 记录的是 Splash 启动时间，不代表主页可交互时间。

每轮稳定观察启动约 22 秒后取样，前台静置 45 秒并保持屏幕 Awake；执行固定的 10 组上下滑动；随后清醒后台静置 60 秒，再单独息屏 60 秒。Wake 输入每 8 秒用于维持清醒；息屏段开始与结束均确认 `Asleep`。帧统计由 `dumpsys gfxinfo reset/framestats` 采集。此前发现无障碍树无法确认微信列表。复测时启用了已安装的无障碍服务，并基于节点文本、资源 ID、可点击行边界和未读标记检查来定位固定的“文件传输助手”会话；每次打开前均重新检查会话标题及未读标记，没有盲点聊天行。聊天页以标题 OCR、消息列表 RecyclerView 和输入框节点共同确认。

CPU 和 PSS 均为 `com.tencent.mm` 主进程总体值，不包括所有微信子进程，也不能归因到模块。前台和清醒后台 CPU 区间包含定期唤醒输入及 adb 采集开销。CPU 线程快照中 Hchat 命名线程持续约 7 条、阶段线程总数没有上升；按线程名汇总的 ticks 差因线程退出/替换出现负值，已弃用，不用于模块 CPU 结论。单秒 `top -H` 快照中的 Hchat 命名线程为 0.0% CPU，但分辨率不足以证明其全区间 CPU 为零。

### 启动与就绪（每轮原始值 / 中位数）

| 指标 | R1 | R2 | R3 | 中位数 |
|---|---:|---:|---:|---:|
| `am start -W` Splash `TotalTime`（ms） | 1640 | 1476 | 1527 | 1527 |
| `am start -W` `WaitTime`（ms） | 1645 | 1488 | 1531 | 1488 |
| `LauncherUI` resumed 观测上界（ms，自 force-stop 前起计） | 2496 | 1694 | 2415 | 2415 |
| LSPosed `module_loaded`（ms，自 force-stop 前起计） | 799 | 694 | 680 | 694 |
| `host_identity=approved`（ms，自 force-stop 前起计） | 1040 | 940 | 924 | 940 |
| 消息观察 `locator=true / hook=true / subscription=true`（ms，自 force-stop 前起计） | 6826 | 6659 | 6473 | 6659 |
| 已有任务再次拉前 `HOT` `WaitTime`（ms） | 202 | 203 | 176 | 202 |

`LauncherUI` resumed 是 Activity 状态的时间上界，不是通过点击验证的主页可交互时间。安装最终包后的预热首次启动曾约 19 秒才记录到 observer ready，并同时创建当前 Tinker 身份的空结果标记；该启动处于环境/缓存转换阶段，不计入上表三轮。正式三轮均观察到 observer ready 和身份获准。

### 内存（MiB，原始三轮 / 中位数）

| 阶段 | R1 | R2 | R3 | 中位数 |
|---|---:|---:|---:|---:|
| 启动后约 22 秒 | 1228.4 | 1182.4 | 1171.9 | 1182.4 |
| 前台清醒静置后 | 745.7 | 760.8 | 757.4 | 757.4 |
| 前轮固定主界面手势后 | 785.2 | 795.9 | 792.9 | 792.9 |
| 清醒后台静置后 | 664.0 | 673.9 | 667.2 | 667.2 |
| 息屏后台静置后 | 676.3 | 694.1 | 679.0 | 679.0 |

启动阶段 PSS 明显高于后续静置读数；Native Heap PSS 启动为 353.4 / 359.7 / 353.4 MiB，前台静置为 151.1 / 157.8 / 155.2 MiB。Java Heap 与 Gfx dev 分量保存在原始 `dumpsys meminfo` 文件中。该变化来自整个微信进程的不同阶段，不能归因于模块，也没有通过主动 GC 消除。

### CPU 与线程数

| 区间 | CPU 单核占用 R1 / R2 / R3（%） | 中位数 | 线程数起始 → 结束 |
|---|---|---:|---|
| 前台清醒静置 45 秒 | 2.322 / 2.502 / 2.409 | 2.409 | 308→286 / 301→280 / 306→285 |
| 清醒后台 60 秒 | 14.069 / 14.005 / 14.454 | 14.069 | 289→275 / 283→270 / 288→274 |
| 息屏后台 60 秒 | 1.285 / 1.793 / 1.593 | 1.593 | 273→271 / 266→266 / 272→270 |

清醒后台的主进程 CPU 读数约 14% 单核；主进程包含微信本身工作，不能据此推断模块后台任务占用。息屏段独立记录，没有并入清醒后台数据。三轮均未发现线程总数随静置增长；`top -H` 单点快照未显示明显活跃的 Hchat 命名线程。

### 固定主界面手势帧统计

| 指标 | R1 | R2 | R3 | 中位数 |
|---|---:|---:|---:|---:|
| 渲染帧数 | 1506 | 1520 | 1507 | 1507 |
| Janky frames | 95 / 6.31% | 99 / 6.51% | 101 / 6.70% | 99 / 6.51% |
| P50 | 8 ms | 8 ms | 8 ms | 8 ms |
| P95 | 13 ms | 14 ms | 13 ms | 13 ms |
| P99 | 15 ms | 15 ms | 15 ms | 15 ms |
| Slow UI thread | 95 | 99 | 101 | 99 |
| Slow issue draw commands | 0 | 0 | 0 | 0 |

这是此前三轮 `LauncherUI` 手势数据。以下新增聊天与首页正式 UI 滚动数据仍没有 Perfetto 归因或主线程/RenderThread 调度跟踪；`gfxinfo` 的 Slow UI thread / draw counters 不等同于模块 hook 的长帧归因。

### 关键优化路径现场证据与对照

- 三轮正式进程的 `/proc/<pid>/maps` 均未出现模块 `libsilk_codec`，均有 `libdexkit`。此结论只针对模块库；微信自带的 `libwechatvoicesilk.so` 不属于 Hchat Silk 代码。
- 每轮 `Hchat_hot_update_method_cache` 哈希和 5 个现有空结果 marker 清单均未改变；安装最终包后的首个环境转换启动创建了当前身份 marker；进入正式三轮采样前该 marker 已存在，三轮间没有新增。此现场证据与隔离单元测试一致，但正式包没有查询计数插桩，不能把它写成直接观察到 0 次 DexKit 查询。
- LSPosed/LSPlant 日志在正式包启动中记录到 `TinkerApplication.getTinkerFlags`、`TinkerSyncResponse` 响应入口及 `LegacyTinkerCore$PatchService` 的 Hook 安装；每轮身份均获准，未出现身份失效。两轮保留了与 PID 对应的 LSPlant 安装行，另外一轮由正式包首次启动日志确认同类 Hook。
- 没有可安全、同身份的旧代码正式包性能对照。R1 APK在当前 Tinker attach 时身份失效，排除；②-2B-2 APK载荷与最终包同代码，而其历史 ②-2B-2 性能表来自诊断包，不混入本次数据。CPU、PSS、jank、耗电的整体提升均未证明。

#### 无障碍确认后的聊天、首页交互复测

在同一官方微信主进程 PID `11734`、正式无插桩 APK 和当前 Tinker 身份 `4c2c3131…` 下，完成三轮，每轮 15 次“文件传输助手”聊天进入/退出（合计 45 次），随后在已确认的聊天消息列表与首页会话列表各执行 10 组上下滑动。打开目标前核验精确标题、可点击行、无无障碍未读/红点标记；聊天页由“文件传输助手”标题 OCR、输入框和消息 RecyclerView 节点确认。未发送消息、未触发 AI 或业务动作、未调整设置。聊天进入/退出耗时包含无障碍树采集、OCR 和 adb 开销，不能当作产品转场延迟。

| 指标 | R1 | R2 | R3 | 中位数 |
|---|---:|---:|---:|---:|
| 聊天进出帧数 | 1627 | 1599 | 1666 | 1627 |
| 聊天进出 jank | 7.38% | 7.32% | 7.98% | 7.38% |
| 聊天进出 P50 / P95 / P99 (ms) | 7 / 32 / 61 | 7 / 34 / 53 | 7 / 32 / 57 | 7 / 32 / 57 |
| 聊天滚动帧数 | 1476 | 1457 | 1469 | 1469 |
| 聊天滚动 jank | 8.06% | 7.00% | 7.35% | 7.35% |
| 聊天滚动 P50 / P95 / P99 (ms) | 7 / 13 / 18 | 7 / 12 / 17 | 7 / 12 / 20 | 7 / 12 / 18 |
| 首页列表滚动帧数 | 1456 | 1430 | 1499 | 1456 |
| 首页列表滚动 jank | 4.53% | 8.32% | 4.07% | 4.53% |
| 首页列表滚动 P50 / P95 / P99 (ms) | 7 / 12 / 14 | 7 / 14 / 15 | 6 / 11 / 13 | 7 / 12 / 14 |
| 主进程 PSS：轮前 / 聊天滚动后 / 首页滚动后 (MiB) | 854.4 / 931.7 / 880.9 | 850.9 / 928.5 / 881.9 | 844.4 / 891.7 / 888.8 | 850.9 / 928.5 / 881.9 |
| 线程数：轮前 → 结束 | 265 → 267 | 267 → 267 | 266 → 267 | 267 → 267 |
| Tinker 全文件哈希轮前=轮后 | 是 | 是 | 是 | 是 |
| 热更新缓存与空结果标记轮前=轮后 | 是 | 是 | 是 | 是 |

`gfxinfo` 统计包含微信窗口中的其他帧和诊断时长，不能只归因于目标页面或 Hchat。每轮结束线程数与开始接近，没有观察到本轮交互后的单调线程累积；PSS 是整个 WeChat 主进程值，聊天滚动后的上升不能归因于模块或称作泄漏。原始 UI 循环指标、逐轮 Tinker/cache 哈希和帧统计均保留在 Git 忽略目录 `dist/round2_3_raw/ui_r1/`。此前启动静置三轮、PSS/CPU 详情、过滤后的 Hook 日志和测量脚本也保留在 `dist/round2_3_raw/`。

## 6. 关键功能、风险和未验收项

| 场景 | 结果 | 依据 / 边界 |
|---|---|---|
| 正式包覆盖安装后微信主进程启动 | PASS | `LauncherUI` 恢复；主进程运行；无本轮采样窗口内 ANR/FATAL 记录 |
| 主进程身份、早期 Hook、消息观察器初始化 | PASS | Tinker 身份获准；LSPlant 记录原 Hook 安装；三轮 observer 最终 ready |
| Silk 无音频需求不加载 | PASS | 正式包主进程映射无模块 `libsilk_codec`；没有触发音频业务 |
| `upgrade_response` 空结果复用和正向 Hook | 隔离回归 PASS；真机查询次数 NOT_VERIFIED | Android 单元/Hook 集成通过；marker 与 cache 三轮稳定；无正式包扫描计数 |
| 自定义头像失败重试、自动转发、自动回复、真实语音 | 隔离回归 PASS；真实业务 NOT_VERIFIED | 不添加头像、不发消息或语音、不调用 AI |
| 朋友圈刷新、红包和收款/转账 | 隔离回归 PASS；真实业务 NOT_VERIFIED | 朋友圈没有切换功能状态；不执行任何真实红包或收付款 |
| 设置入口真实 UI | PASS | 通过无障碍节点打开微信“我 → 设置 → Hchat”，进入“聊天”二级设置，滚动并返回，再次进入；没有点击或切换开关 |
| 聊天页进入/退出、多次往返、聊天固定滚动 | PASS | 固定的“文件传输助手”标题通过节点与 OCR 交叉确认；每次打开前未发现无障碍未读/红点标记；三轮共 45 次往返和三轮消息列表滚动 |
| 账号与双包现场隔离 | 隔离回归 PASS；第二包现场 NOT_VERIFIED | 当前官方包实际加载；本轮未启动修改版微信验证第二个作用域 |

预检时，在无障碍树为空的页面上曾进行一次盲坐标点击，随后出现 WeChat 三点工具栏；按 Back 返回 `LauncherUI`。该点击可能打开了一个无法识别的会话并改变其未读状态；无法确认会话身份或原状态。没有发送消息，也未继续点击其他聊天行。本轮没有清除或修改消息数据。

没有发现可复现的崩溃、ANR、重复线程增长或缓存跨身份复用缺陷。真实业务副作用受限制的头像失败重试、真实转发/自动回复、音频、朋友圈刷新、红包/收款/转账仍为 NOT_VERIFIED；第二微信包的现场隔离也未在本轮启用验证。历史预检期间曾在无障碍树不可用时发生一次盲坐标点击，随后出现 WeChat 三点工具栏并返回 LauncherUI；无法确认当时打开的会话及其此前未读状态。该情况已保留为环境记录。之后的正式 UI 复测只点击已确认且无可见未读标记的固定会话，没有发送内容。PSS/CPU/jank 缺少同身份旧代码正式包配对基线；整体 CPU、PSS、耗电和启动时间收益没有得到证明。当前没有需要提出的代码修复。

## 7. 总结与交付状态

**结论：PASS。** 阻断性自动回归、完整 Release Lint、正式签名 R8 构建通过；正式 APK 可运行，设置和安全固定会话的 UI 入口/返回/滚动已复验；无音频需求时模块 Silk 仍未加载，热更新正向 Hook 安装、运行时身份获准、消息观察器就绪，以及 UI 复测三轮间 Tinker/cache 稳定性均得到确认。未发现可复现的重大功能、稳定性或性能退化。受副作用限制的真实业务流程与第二包隔离仍标记 NOT_VERIFIED；没有同身份旧代码正式包配对对照，因此 CPU、PSS、耗电和启动收益未证明。PASS 表示本轮阻断性验收通过，不代表整体性能收益已量化。

最终文档为本文件；业务源码保持冻结提交 `da449bde32ae62ea3380c7d36c4e8a4d13ca2538`。本次结论为 PASS，按任务要求将只含验收文档的提交推送到 `main`。最终正式 APK 保留为 `dist/Hchat-1.2.0-release-signed.apk`。
