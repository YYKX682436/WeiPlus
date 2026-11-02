# WeiPlus 第一轮优化最小收尾 R2

基于提交：`e479826433c8aabc97ff760156785482712964ab`
日期：2026-11-02

## 复现与修复

修复前的可控时钟/调度回归失败：首次解码失败后立即重绘仍处于 2 秒退避期；静止页面不再产生外部绘制或重新绑定，调度器里没有任何待执行任务。推进虚拟时间跨过退避后，I/O 请求仍只有 1 次，断言显示 `actual requests=1, pendingTimers=0`。

现在 `AvatarLoadCoordinator` 在失败完成时为 key 安排可取消的重试任务。waiter 使用弱引用、绑定 token 和有效性检查；任务到期后由 Handler 回到主线程，再经现有单线程 executor 做解码。成功回调仍触发 Drawable 刷新。退避按 2、4、8、16、32、60 秒递增并封顶；同 key 仅保留一个计时器，等待重试总数限制为 128。

功能总开关关闭、Runtime 销毁、头像缓存命中、头像更换/删除、Drawable 换绑时，会清除或取消旧 waiter/任务。旧 token 不会刷新重绑后的视图。16 MiB Bitmap LRU、成功加载合并、绘制热路径零文件 I/O 均保留。

## 验证结果

- `run_native_regression_tests.cjs`：通过。新增可控时钟/调度覆盖静止页面自动唤醒、成功刷新、重试成功时多个 Drawable 的刷新、同 key I/O 合并、持续失败退避与单计时器上限，以及关闭/销毁/删除/换绑时取消。
- `run_performance_round1_tests.cjs`：通过。
- `run_message_block_tests.cjs`：61 项断言通过。
- `run_database_chain_tests.cjs`：通过。
- Debug/Release Kotlin 编译、Release Java 编译、正式签名 `assembleRelease`：通过。
- 完整 `:app:lintRelease`：通过（约 1 分 37 秒），0 error、652 warning、50 hint；其中 Lint 日志还有项目既有 `firstOrNull()` 多匹配提示。
- APK 使用正式密钥签名，`apksigner verify --verbose --print-certs` 的 v2 验签通过。

## APK 与未执行的真机验证

最终正式签名 APK：`dist/Hchat-1.2.0-release-signed.apk`
包名/版本：`h.Hchat` / `1.2.0` / `261030`
APK SHA-256：`7c6e49b0156a4034c418525860ca08e9ab11503908b0abbd7fc1049391ee2fcd`
证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`

本轮未将最终 APK 安装到真机，也未在设备上验证头像失败后的自动重试；真机行为仍待后续验收。本轮未改微信或模块配置、未触碰微信业务数据、未发送消息，也未采集新的 PSS 数据；上一轮性能记录保持原样。

重试任务最多保留 128 个不同头像 key；容量达到上限时淘汰最早的待重试 key。被淘汰 key 的后续绘制请求仍可重新触发加载。
