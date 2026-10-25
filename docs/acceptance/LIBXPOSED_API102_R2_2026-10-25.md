# libxposed API 102 R2：共享 Hook 有效性（2026-10-25）

## 范围与根因

审查基线为 `42d039cdd6af128f316625bea4351ece5ac9d902`。本轮只调整 `HookRegistry` 与 `XposedRuntime` 的共享句柄有效状态及其验收，不改变 API 102 单一路径或 R1 的异常边界。

`ManagedHook.unhook()` 在底层撤销前先清空 callback，这是保留撤销失败句柄时让残留回调失效的必要行为。问题在于 `HookRegistry.hookOnce()` 只检查 key、代次和 `Executable`，不判断现有 ManagedHook 是否仍有效。因此租约回滚的框架撤销失败后，旧句柄虽然 inert，仍留在 `keyedHooks` 并被误当成安装成功；即使 `retryFailedUnhooks()` 已经将它物理移除，key 仍指向旧句柄，同代次安装仍无法恢复。

## 当前提交失败复现

新增回归编译并运行当前生产 `HookRegistry.kt`、`XposedRuntime.java`，使用现有 API 102 Chain/框架夹具。先保持生产文件不变，以基线生产代码运行，准确复现用户描述的两个状态：

- 安装租约先注册共享 key，再在后续框架注册失败时回滚；共享句柄的底层撤销同时失败。相同代次再调 `hookOnce(key, same Executable, ...)`，旧行为返回相同旧句柄且注册次数为 0；宿主调用 1 次，旧业务 callback 0 次。
- 再恢复底层撤销并调用 `XposedRuntime.retryFailedUnhooks()` 后，同 key 再安装仍返回物理撤销的旧句柄，注册次数为 0。

基线 JVM 运行以退出码 1 失败，目标状态断言 7 项失败，包含两项复现调用结果和其余取消租约/旧句柄替换检查。原始记录保存在 `dist/api102-r2-hookonce-baseline-failures.txt`。

## 修复与状态约束

- `ManagedHook.isActive()` 由 `XposedRuntime.isHookActive()` 暴露为只读状态：只有 callback 仍存在、没有被物理移除且所属批次 gate 已激活的 ManagedHook 才有效。
- `hookOnce()` 对有效同代次同 `Executable` 仍返回同一个句柄。遇到失效/已移除句柄时，只从有效 key 索引中按当前记录身份移除它，然后为同 key 建立新 Hook；旧句柄仍留在注册表和运行时待清理集合中，继续拥有撤销失败句柄的清理责任。
- `unhook()` 保持按句柄身份移除 key 记录。旧句柄的迟到清理不会删除新安装的同 key 记录；失效租约和旧代次仍由原代次/租约检查拒绝安装。
- 没有更改 Chain 或构造兼容接口，也没有异步轮询。失败回调在新安装后仍然 inert；恢复撤销后，显式重试完成物理删除，最终注册表和失败撤销集合归零。

## 回归证据

基线红测之后，未改变测试的业务目的或删除断言；修复版实际生产回归全部通过，日志保存在 `dist/api102-r2-hookonce-fixed.txt`：

1. 正常有效共享 Hook 仍按同 key 去重，仅注册一次。
2. 后续安装失败与取消租约分别触发回滚；旧句柄撤销失败时，同代次重试均创建新有效句柄。
3. 重装后的宿主执行一次、新 callback 执行一次、旧残留 callback 执行零次；宿主异常对象身份保持，且宿主调用仍为一次。
4. 全局 `retryFailedUnhooks()` 成功物理撤销后，同 key 会重新安装。持有旧、新句柄时撤销旧句柄不影响新句柄去重。
5. 恢复底层撤销能力后显式清理所有句柄，注册表大小、失败撤销数和框架存活句柄均为 0。
6. 原 HookRegistry/API102 Chain 回归、R1 字色真实解析与异常边界回归、发送/头像/群目录等 API102 interceptor 回归及全部 28 个现有 `run_*tests.cjs` runner 均通过。全量日志在 `dist/api102-r2-regressions.txt`，R1 与 API102 interceptor 单独复跑日志也保存在 dist。

## 构建与 APK 检查

执行 `./gradlew --no-daemon :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease`，`BUILD SUCCESSFUL`。Debug、原正式签名 R8 Release 和两种 Lint 均通过；Lint 为 0 errors，Debug 649 warnings/50 hints，Release 648 warnings/50 hints。

- Debug APK：`dist/Hchat-debug.apk`，SHA-256 `88294b171fc2f07d3a898a9bed824d40f320050c713ae01152ad14a23bd4525b`。
- 正式签名 R8 Release：`dist/Hchat-release-signed.apk`，SHA-256 `259dc3a25e84cd82e54c837c39291f52fd24a2eacde7c917e3059cfedb8df7e9`。APK 签名验证通过，证书 SHA-256 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`，与当前正式签名一致。
- `scripts/verify_api102_apk.py` 对 Debug（20 DEX）和 Release（1 DEX）均通过。Release `META-INF/xposed/java_init.list` 为 `se2`，与 Release mapping 中 `h.Hchat.ModuleEntry -> se2` 对应；`module.prop` 的最低/目标 API 均为 102，`staticScope=true`、`autoHotReload=false`；scope 仅含 `com.tencent.mm` 和 `com.tencent.mm.wx`。APK 未打包 libxposed 框架 API 类。
- 正式 APK 检查、签名/摘要、Lint 报告和构建过程见 `dist/api102-r2-apk-checks.txt`、`dist/api102-r2-build.txt`、`dist/api102-r2-lint-results-{debug,release}.html`。

## 真机范围

设备恢复授权后已补做烟测。Redmi K50 Ultra / Android 15/API 35；重新读取实际 `/data/adb/modules/zygisk_lsposed/framework.dex`，确认 `LIB_API=102` 且 `getApiVersion()` 的字节码直接返回 102。LSPosed 模块数据库确认 `h.Hchat` 对 user 0 已启用，并将 `com.tencent.mm` 纳入 scope。通过 `adb install -r` 更新正式 APK，没有卸载模块/微信或清除数据；安装后设备端 `base.apk` SHA-256 与交付 APK 均为 `259dc3a25e84cd82e54c837c39291f52fd24a2eacde7c917e3059cfedb8df7e9`。

| 检查 | 结果 |
| --- | --- |
| 新入口冷启动 | PASS，主进程 `com.tencent.mm` 在 15:01:06 加载 `api=102`；`push`、`appbrand0`、`appbrand1` 也有入口加载记录 |
| 设置进入/返回 | PASS，从微信“我→设置→Hchat→美化”进入，页面底部返回回到 Hchat 设置，系统返回回到微信设置 |
| 基础发送 | PASS，向文件传输助手发送唯一一条 `r2_hookonce_smoke_20261025`，新消息在会话中显示 |
| 模块设置保存/开关变更 | 未改动用户配置，NOT_VERIFIED 本轮真机设置写入/即时切换 |
| R2 撤销故障注入 | 生产 JVM 回归 PASS；真机未注入框架撤销故障，NOT_VERIFIED |
| 第二微信包 | 设备未安装 `com.tencent.mm.wx`，NOT_VERIFIED |

入口日志摘要保存在 `dist/api102-r2-device-smoke.txt`。本轮真机确认了 API、模块注册、入口加载、设置导航与文件传输助手发送；没有改变任何模块偏好设置。
