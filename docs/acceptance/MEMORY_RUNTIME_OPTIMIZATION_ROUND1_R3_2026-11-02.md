# WeiPlus 第一轮优化最终边界收口 R3

基于提交：`d8b727211aa4f0fbbca4d08a84c19d6c2b62e911`

日期：2026-11-02

## 根因与修复

头像 Store 的修订号是进程级全局值。更改或删除 A 会递增该值，使正在解码的 B 结果被 Store 的代际栅栏正确判为过期；同时，Runtime 原先对任何头像变更都调用 `avatarLoadCoordinator.clear()`，会取消 B 的退避任务或移除 B 的在途 waiter。只去掉 `clear()` 也不够，因为 waiter 还会把 B 的有效绑定误判为过期。

修复后，头像变更按包名和 wxid 前缀只取消该头像自己的任务；waiter 有效性检查只判断 Runtime、绑定 token、账号和功能配置。Store 对异步解码的全局修订号栅栏及完成时的代际比较保留，因此旧 B 位图不会进入缓存；仍有效的 B waiter 能在失败退避到期后以最新修订号重新加载并刷新。

## 修复前复现与回归

修复前运行 `node scripts/run_native_regression_tests.cjs /tmp/weiplus-round1-kotlin-libs`，新增隔离断言按预期失败：

- B 首次失败并等待延迟重试时更改 A：期望 B 共加载 2 次，实际 1 次。
- B 正在异步解码时删除 A：期望 B 共加载 2 次，实际 1 次。

修复后，同一隔离回归通过：A 自己的重试被取消，静止页面上的 B 重试保留；A 变更导致 B 旧代际解码失败后，B 使用当前全局代际重载并刷新。既有头像回归仍覆盖 16 MiB LRU 淘汰后重载、解码失败恢复、同 key 合并与多个 Drawable 刷新、绑定换代、退避和 128 个定时器上限、关闭/销毁取消。

## 验证

- `node scripts/run_native_regression_tests.cjs /tmp/weiplus-round1-kotlin-libs`：通过，含本轮定向回归和现有生命周期/异步回归。
- `node scripts/run_performance_round1_tests.cjs /tmp/weiplus-round1-kotlin-libs`：通过，新增静态约束检查确认定向取消、保留 Store 全局代际栅栏且绘制热路径仍无磁盘 I/O。
- `node scripts/run_message_block_tests.cjs`：61 项断言通过。
- `node scripts/run_database_chain_tests.cjs /tmp/weiplus-round1-kotlin-libs`：通过。
- `./gradlew --no-daemon :app:lintRelease :app:assembleRelease`：成功；完整 Release Lint 报告 653 warnings、50 hints，无错误。`lintRelease` 任务本身已完成，不以 `lintVitalRelease` 代替。
- 正式签名 Release 构建成功。`apksigner verify --verbose --print-certs` 的 v2 验签通过，正式证书 SHA-256 为 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`。

## APK 与设备验证

- APK：`dist/Hchat-1.2.0-release-signed.apk`
- 包名/版本/版本码：`h.Hchat` / `1.2.0` / `261030`
- APK SHA-256：`94b1e73fa9fb18da1fd2438e9f3299d72dc720d2b3c4b6dc3f2b5fd6555d0110`
- 已通过 `adb install -r` 覆盖安装。随后拉取设备 `base.apk` 与本地 APK 比对，SHA-256 相同且 `cmp` 逐字节匹配。
- 重启官方微信 `com.tencent.mm` 后进程启动成功；采样日志中看到 LSPosed 框架活动，未发现对应启动窗口内的 `FATAL EXCEPTION`。未发送消息、未改微信或模块配置，也未清理微信数据。

本轮未在真机上人为制造头像解码失败或修改/删除头像，因此跨头像自动恢复的真实 UI 行为未在设备上验证；该场景由可控时钟和隔离调度回归覆盖。本轮不重跑 PSS 采样，保留既有性能记录。
