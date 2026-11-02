# WeiPlus 第一轮内存与运行开销优化验收

日期：2026-11-02
基线提交：`53a50951592864f19aa6e58f69b45ccf85a20187`
验收提交：见本记录关联的 Git 提交
设备：Xiaomi 22081212C，Android 15 / SDK 35，WeChat 8.0.80 / code 3220

## 修改范围与根因

1. **业务订阅生命周期**：关闭功能后，业务消息 listener 和回调可能继续挂在共享观察器或 `BaseFeature.trackedSubscriptions`，关闭态仍可能创建消息对象、排队工作。加入可替换的消息观察 lease、有效配置监听和关闭代次检查；必要 PB/DB 内部确认逻辑与业务订阅分离，PB 不可用时仍保留数据库备用路径。
2. **媒体转发留存**：一小时后的媒体删除任务之前与完整待转发对象耦合；下载超时、结束和迟到回调也可能延长对象生命周期或触碰已复用路径。清理任务现在仅持有缓存根目录、限于管理目录的文件路径和 token；结束时取消超时，下载回调使用弱引用并验证任务、代次、token 和目标路径。仍在一小时保护窗口内的迟到文件交由既有路径清理任务删除；原清理已过期且所有权释放后才即时清理。
3. **头像解码与绘制**：通知头像使用全尺寸解码及按条目数限制的可空缓存；自定义头像绘制路径重复检查文件/摘要并可能生成同尺寸圆角副本。改为 4 MiB、按 `allocationByteCount` 计费的共享 LRU，按系统通知大图标像素尺寸采样解码；绘制路径只用内存状态和 Canvas 裁切，异步合并加载、验证行绑定并在保存/删除后刷新。
4. **页面状态引用链**：底栏 host、RecyclerView 手势状态能从弱索引 value 或监听器回到页面/Activity。索引 value 改为弱引用；真正销毁时移除索引、监听和回调，临时 detach 保留可重附状态；SwipeQuote 对接管、子视图消费、纵向滚动和 CANCEL 分别收尾。

通知头像 LRU 预算为 **4 MiB**，依据是项目现有 `FloatingShortcutIconLoader` 使用的字节上限；大小按 Bitmap 实际分配字节计算。淘汰仅解除缓存持有，不回收 View/Notification 仍可能使用的 Bitmap。

主要文件：

- 消息观察：`WeChatMessageObserveApi.java`、`BaseFeature.kt`、新增 `MessageObserveLease.kt`，以及 AutoMessageForward、CustomNotification、KeywordNotification、TextSpeech 的 Feature/Settings。
- 转发：`AutoMessageForwardRuntime.kt`、`AutoMessageForwardSettings.kt`、`AutoMessageForwardFeature.kt`。
- 头像和通知：新增 `NotificationAvatarBitmapCache.kt`，以及 CustomFriendAvatar、CustomNotification、KeywordNotification 的 Feature/Runtime/Store/Settings。
- 页面：`CustomBottomBarFeature.kt`、`SwipeQuoteFeature.kt`。
- 回归：`scripts/run_performance_round1_tests.cjs`、`scripts/tests/performance_round1/MessageRegression.java`、`scripts/run_native_regression_tests.cjs`、`scripts/tests/native_regressions/`。

没有调整默认开关、版本支持、双包隔离及现有业务语义；未扩展启动预热、AI 队列、公共线程池、朋友圈轮询、毛玻璃录制或 Hook 失败重试。未重构消息详情、会话标签栏和设置页已正确的释放机制。

## 自动回归与构建

- `node scripts/run_performance_round1_tests.cjs /tmp/weiplus-round1-kotlin-libs`：通过，包含 PB/DB 内部备用需求、消息补全/去重及转发媒体、头像和视图引用约束。
- `node scripts/run_native_regression_tests.cjs /tmp/weiplus-round1-kotlin-libs`：通过；实际 lease 回归覆盖 20 次开关、替换、失效回调和销毁。
- `node scripts/run_message_block_tests.cjs`：61 项断言通过。
- `node scripts/run_database_chain_tests.cjs /tmp/weiplus-round1-kotlin-libs`：通过。
- `node scripts/run_lifecycle_chain_tests.cjs`：通过。
- `:app:compileDebugKotlin`、`:app:compileReleaseKotlin`、`:app:compileReleaseJavaWithJavac`、`:app:assembleRelease`：通过；构建内 `lintVitalRelease` 通过。
- 最终 APK 使用 `adb install -r` 覆盖安装成功，随后 WeChat 8.0.80 正常重新启动并到达 `LauncherUI`；未卸载微信、未清理应用数据/缓存。安装后四个配置槽位哈希仍一致。
- `git diff --check`：通过。
- **完整 Release Lint 未完成**：`:app:lint` / `:app:lintRelease` 的 Lint worker 在 `BidirectionalTextDetector` 遍历 Kotlin PSI（`KtCommonFile.accept`）时持续占用 CPU，等待 25 分钟仍无结果后停止。该阻塞不是源代码编译错误；`lintVitalRelease` 通过，但不能据此称完整 Lint 通过。

三轮性能样本采自同一优化 Release 候选包。样本完成后只对媒体迟到下载回调增加了“所有者仍在一小时保护窗口时不提前删除”的边界保护；固定滚动、SwipeQuote 预览、进出聊天和后台静置流程不会进入此媒体回调分支。最终 APK 已重新全量编译/签名、覆盖安装并完成启动冒烟与配置哈希复核，但没有在这处仅涉及媒体迟到回调的最终差异后重做三轮采样。**因此下表是本轮候选包的三轮比较，不能表述为最终 APK 位级相同包的三轮采样。**本轮没有发送消息，媒体实际下载/转发路径由定向回归、回调边界和编译验证；真机媒体转发验收未做。

## 真机基线与修改后采样

同一设备、正式签名 Release、固定操作流程各三轮。每轮记录稳定启动后、10 对固定聊天滚动后、关闭 SwipeQuote 预览后的关闭态、15 次主页/聊天进出后、清醒后台 60 秒后数据。未主动触发 GC，未清微信数据或缓存，未卸载微信。未发送消息；滑动引用预览只打开后取消。`dumpsys gfxinfo` 仅重置帧统计计数。定向开关回归后恢复配置。

**PSS，MiB（R1 / R2 / R3；1 MiB = 1024 KiB）**

| 阶段 | 基线 | 修改后 |
|---|---:|---:|
| 稳定启动 | 957.0 / 783.9 / 888.5 | 697.5 / 1045.2 / 722.8 |
| 固定滚动 | 881.8 / 830.2 / 897.7 | 745.2 / 806.6 / 764.0 |
| 功能关闭态 | 930.9 / 824.7 / 928.3 | 745.3 / 751.5 / 744.3 |
| 15 次进出后 | 934.4 / 983.4 / 953.5 | 832.8 / 779.5 / 916.4 |
| 清醒后台 60 秒 | 884.0 / 824.5 / 837.3 | 720.9 / 768.1 / 741.3 |

每轮的滚动、关闭态、15 次进出和清醒后台样本都低于对应基线；启动样本波动明显，修改后 R2 启动为 1045.2 MiB（其中 native PSS 约 410 MiB），故不将测量变化解释为确定的代码节省比例。PSS 是运行时整体采样，不等同于本轮改动可归因的堆内存；仍需更多控制变量采样才能证明因果收益。

滚动帧的 janky frame 比率（基线 → 修改后）：R1 4.39% → 4.56%，R2 3.81% → 5.08%，R3 2.11% → 5.63%。本轮数据不支持“流畅度变好”的结论。

启动采样若发生于休眠屏幕状态已排除；修改后 R3 以唤醒后稳定 45 秒的样本替代。修改后 R2 的高启动样本保留并标注，没有通过主动 GC 消除。基线 R1 后台屏幕状态记录不完整，应视为较弱可比样本。

修改后样本分量也有波动：PSS 的 native/Java 部分会随运行轮次变化，故当前证据足以支持“特定代码中的多余工作和强持有链已修复”，不足以将实际内存、电量、温度或帧时变化量归因到本次代码。

## 配置、APK 与签名

四个关键 FastKV 槽位在验收后与基线哈希一致：

- `Hchat_custom_notification.kva`、`.kvb`：`f19f48fce80f7a050a9d493ba9ea3c50d142d76b7dcc656381239a3b8bf5b077`
- `Hchat_swipe_quote_config.kva`、`.kvb`：`6dc36ed999a3c00ecaa278c511b88c01eef11513cbe52185c1fb03798211fd4b`

Release APK：`dist/Hchat-1.2.0-release-signed.apk`
包名/版本：`h.Hchat` / `1.2.0` / `261030`
APK SHA-256：`550ebeebeba59a4927a4cc1259e70c92d6c74c33ffc8e3355504f3c9e0919482`
签名证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`
签名校验：`apksigner verify --verbose --print-certs` 通过（v2）。

旧的基线/历史 APK 仅用于比较和构建验证，完成后从 `dist/` 清理；仅保留本轮 Release APK。
