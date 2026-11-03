# WeiPlus 启动与核心 Hook 安装 R2 验收

日期：2026-11-03

基线：`8941d045f1e10c2e19e0c8f84b851f40dc08da46`（main）

## 修复范围

### Lifecycle API：三个回调分别跟踪

`WeChatLifecycleApi` 为 `onResume`、`onPause`、`onDestroy` 各自保存真实 `HookHandle`。安装成功只代表对应入口成功；API 只有在三个句柄都仍有效时才报告 `isAvailable()`。部分失败会保留已成功句柄，重试只补缺项；重复 `install()` 不会重复 Hook。状态查询会检查 Registry 中句柄是否仍活跃，因此全量卸载后可用状态会退回未就绪。

Robolectric/API 102 故障注入分别让三个生命周期方法安装失败，检查首次就绪状态和句柄数，移除失败注入后重试，确认只新增一个句柄、三个宿主回调各执行一次、三个事件各分发一次、重复安装不增加句柄，最后 Registry 清理为零。原 R1 用 `onResume` 故障的核心就绪用例也扩展为检查恢复后生命周期有效句柄确为 3 个。

### CurrentActivity API：失败入口可补装且状态跟随句柄

`WeChatCurrentActivityApi` 同样分别跟踪三个必需回调。失败后成功的 Hook 会被保留，重试只安装缺项；`isAvailable()` 反映三个真实句柄均有效。清理 Hook Registry 后，API 不再保留过时的可用状态。

测试在 R1 提交 `8941d045` 上单独运行 CurrentActivity 用例，失败在“重试只安装缺失项”断言：原实现会重复安装先成功的 `onResume` Hook。当前实现对 `onPause`、`onResume`、`onDestroy` 逐项注入失败后均能只补装对应入口；调用链保持每次一次，Activity 跟踪在 resume 时更新、pause/destroy 时清空。

### ActivityStart API：按方法跟踪所有已发现入口

`WeChatActivityStartApi` 为 `Activity` 与 `ContextWrapper` 中发现的每个 `startActivity`/`startActivityForResult` 方法保存句柄。安装某方法失败会单独记录并继续其他方法；`isAvailable()` 只有在所有发现的入口句柄都有效时才为真。再次安装仅补缺失项，已成功的入口不会重复。

API 102 注入一个已发现的 Activity 启动方法失败，确认首次未就绪、其他方法仍有有效句柄、核心门禁未通过；去掉注入后只补装失败方法。调用失败方法的 Hook 后监听器与宿主各运行一次，之后清理所有句柄。

### 核心门禁和公共 API 换代失败重试

现有核心就绪门禁使用三个 API 的 `isAvailable()`，因此生命周期或 ActivityStart 必需入口不全时不会发布核心就绪。真实注入 Lifecycle `onResume` 失败时，CurrentActivity 与 ActivityStart 已预先安装，确认核心就绪仍为 false；补装后门禁恢复为 true。

另在真实 FeatureManager 隔离 harness 中注入一次公共 API 核心替换失败，再执行成功重试。失败时旧 API 所有者撤销旧总线订阅；pending revalidation 保留核心与业务目标，重试先换核心、再清理/重绑业务功能。旧总线和失败候选总线均无回调，恢复后的总线只有一个订阅且只回调一次。该检查通过，因此没有扩大修改 R1 的 `FeatureManager` 换代流程。

### R1 红测基线固定

`scripts/tests/startup_recovery/R1StartupSourceRegression.cjs` 现在固定读取原始基线提交 `d1f71c58db2f88035303e3d2840086c3daf1e935`。

执行 `node scripts/tests/startup_recovery/R1StartupSourceRegression.cjs --baseline`，原始五项入口时序、Application/Tinker 状态、Tinker 失败隔离、核心 Hook 门禁和 API 换代检查继续全部 **RED**；工作树版本五项全部通过。这样后续提交不会把基线漂移到当前 `HEAD`。

## 验收结果

- R2 故障注入先红后绿：基线 R2 定向套件 5 项中 4 项失败；CurrentActivity R1 基线的独立用例确认成功前缀在重试中被重复安装。修复后定向套件通过。
- 完整 `:app:testDebugUnitTest`：154 tests，0 failures、0 errors、0 skipped。
- 全部仓库回归脚本：32/32 通过；R1 专项 runner 3/3 通过。
- Release Lint：654 warnings，0 errors、0 fatal。
- `:app:assembleRelease`：R8 正式签名构建成功；`apksigner verify` 确认 v2 签名有效，1 signer。
- 构建后 `git diff --check` 通过。

## R2 APK

- 文件：`dist/Hchat-1.2.1-startup-recovery-r2-signed.apk`
- package/version：`h.Hchat` / `1.2.1` / versionCode `261103`
- APK SHA-256：`2ade22fdf4eec6dbb5f0ed7adc06103c0386aae26b61bf84fa3fbfcbf2dea0ff`
- 签名证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`

## 运行态边界

本轮未安装、启动或强停微信，没有清除微信数据、改变配置或人为制造热补丁。R2 的部分 Hook、恢复、核心门禁和回调次数由 Robolectric/API 102 框架夹具验证；设备上微信的真实框架 Hook 注册和 Activity 生命周期仍是 **NOT_VERIFIED**。
