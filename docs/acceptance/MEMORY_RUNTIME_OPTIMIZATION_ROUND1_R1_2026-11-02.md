# WeiPlus 第一轮优化验收返工 R1

基于提交：`2c66906a7bf708c5d5e5410f495b685b4c6970c3`
日期：2026-11-02

## 修复前复现

修复前运行 7 条目标断言，**7/7 失败**：头像成功标记脱离 16 MiB LRU、失败被永久标成已完成、同 key 只记住一个 Drawable；自动转发 rules 快照变化会重建运行器；AutoForward/TextSpeech 同步路径未串行化；lease 快速复用未检查 observer 实例。

## 根因与修复

1. **头像加载**：`completedAvatarLoads` 独立于 Store 的 16 MiB LRU；`loadBitmap()` 返回 null 也可能写入完成标记，成功项还会在 Bitmap 淘汰后继续压住重载。原 in-flight key 只弱持有首个 Drawable。现在用 `AvatarLoadCoordinator` 合并 key 的 I/O，成功状态不越过 Bitmap cache 生命周期；失败只进入有上限的 2 秒退避；waiter 弱持有 Drawable、同目标绑定替换为最新 token，完成时刷新所有仍匹配的绑定。绘制热路径仍只查内存缓存并调度加载。
2. **转发连续性**：Feature 将整份 rules 列表作为快照，任意规则编辑都会 shutdown 旧 Runtime，丢弃有效的延迟任务。移除快照重建，Runtime 在有效期内复用；实际发送仍通过 `PendingForwardRuleGate` 按原始命中 rule ID、当前目标、有效开关和完整匹配条件重新核验。总开关关闭或目标规则失效时，任务按原逻辑跳过/结束；重新开启后可建立新 Runtime 和订阅。
3. **运行器并发与 observer 换代**：偏好监听与 DexReady 可并发进入 `synchronizeRuntime()`，形成重复创建窗口。AutoForward/TextSpeech 的 install、destroy、同步方法现在共用对象监视器；`FeatureRuntimeSlot` 对 get/create/clear 提供线程安全单例。`MessageObserveLease` 另存订阅所绑定的 observer，复用时要求 callback、active token 与 observer 实例都相同；实例替换会先退旧订阅再接新订阅。

## 回归与构建

- `run_native_regression_tests.cjs`：通过。新增覆盖头像 cache eviction 重载、加载失败后退避恢复、多 Drawable 通知、同 Drawable 换绑、旧 wxid 结果抑制；延迟目标规则在无关/模板名编辑、目标规则禁用/重开、总开关关闭时的重新核验；32 路并发 runtime 创建、destroy/reinstall；observer 替换、旧 token 退订和 16 路并发开关。
- `run_performance_round1_tests.cjs`：通过，额外检查头像 draw 路径只读缓存、无文件/摘要/解码调用，以及两个 Feature 使用序列化运行器路径。
- `run_message_block_tests.cjs`：61 项断言通过。
- `run_database_chain_tests.cjs`：通过。
- Debug/Release Kotlin 编译、Release Java 编译、正式签名 `assembleRelease`：通过。
- **完整 `:app:lintRelease`：通过**，耗时约 1 分 43 秒；报告 0 error、645 warning、50 hint。该结果来自完整 Release Lint task，不是 `lintVitalRelease`。
- 为定位之前的长时间扫描，在当前 `lintRelease` 约 30 秒时采集 JVM 栈，样本位于 `PrivateApiDetector` 的 `PrivateApiLookup` / `ApiDatabase.writeDatabase` 初始化路径；随后任务自行完成。当前运行没有复现之前约 25 分钟的 `BidirectionalTextDetector` / Kotlin PSI 停滞，因此不能据此断言旧停滞的根因已确定。当前报告中的 warning/hint 保留在 Gradle Lint 报告中。

## 真机边界、配置与 APK

- 最终 APK 以 `adb install -r` 覆盖安装，WeChat 正常重启到 `LauncherUI`。微信未卸载、未清数据/缓存，未发送消息。
- 验收前后 `Hchat_custom_friend_avatar_config` 与 `Hchat_auto_message_forward_config` 的 `.kva/.kvb` 哈希均为默认空值 `ad7facb2586fc6e966c004d7d1d16b024f5805ff7cb47c7a85dabd8b48892ca7`；设备上也没有 `custom_friend_avatars` 目录。因此没有更改用户配置或添加临时头像来伪造头像滚动收益，**真机自定义头像反复滚动未验证**。
- 自动转发设备配置同样为空；验证真实延迟转发会要求产生消息，且可能向目标发送。本轮遵守“不擅自发送消息”，由隔离 JVM 回归验证规则编辑与运行器连续性，**真实消息的真机转发未验证**。
- 没有采集新的 PSS/性能数据，上一轮验收记录未修改；本轮也不据此宣称资源收益。

最终正式签名 APK：`dist/Hchat-1.2.0-release-signed.apk`
包名/版本：`h.Hchat` / `1.2.0` / `261030`
APK SHA-256：`fdb0f4ac43c1251767538e4e142234d127af4e55496ab3ea2775671b06764dcf`
签名方案：v2 验签通过
证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`
