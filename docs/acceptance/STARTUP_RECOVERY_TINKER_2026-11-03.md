# WeiPlus 启动恢复与 Tinker 身份修复验收

日期：2026-11-03
分支：`main`
范围：模块初始化恢复、核心 API 门禁、宿主 APK 路径诊断、Tinker 运行代码身份、禁止热更新入口状态。

## 结论

源码修复已完成。完整单测、仓库回归脚本、Release Lint、R8 Release 构建、正式签名和 APK 签名验证均通过。没有对真机执行安装、启动、强停或配置修改，因此真实微信进程中的启动、Tinker attach 与 Hook 代次切换仍标记为 **NOT_VERIFIED**。

## 已确认的代码缺陷

- `ModuleEntry` 在启动初始化线程开始前就消耗了一次性 `applicationReady` 闸门；`initModule` 只记录异常，没有重试入口。临时 Native/DexKit/框架失败会让本进程无法再次初始化。
- `FeatureManager.installAll` 在核心 API 完成前就把生命周期标记为已安装。核心功能初始化或安装失败后仍继续下游功能，重复调用也直接退出。局部安装异常的 Hook 清理边界不足。
- `HostRuntimeIdentityDecision` 所用宿主身份包含整个 Tinker 补丁文件变化、`tinkerId` 和 `patchId`。`patch_meta.info` 等元数据更新时间因此可以被误当作宿主代码代次变化，调用全局撤销。
- `DisableHotUpdateFeature` 早期入口按“任意一个 Hook 成功”记录整个加载器已安装；后置入口则在各项安装结果未知时无条件登记加载器完成。后续失败入口失去补装机会。

历史验收报告记录过最终 APK 首启时 `tablet_tinker_attach_after:runtime_identity_changed`，随后 DexKit 未映射，普通重启恢复；相邻记录也说明 `patch_meta.info` 在采样期间变化。[历史记录](STARTUP_BACKGROUND_ROUND2_1_2026-11-02.md#L31-L33) 与当前身份实现相符，但当时没有逐字段保存“获准前/attach 后”身份值，所以不能证明该设备日志中的每一次失效都由元数据变化单独触发。代码层面的误判条件已通过可控用例复现并移除。

## 修复

### 启动与安装恢复

- 包入口 Hook 和完整模块初始化改由唯一的 `DexInstallScheduler` 串行工作线程调度，临时失败按有限退避策略重试，成功后停止；耗尽后新的生命周期请求可以重新启动一次重试批次。
- `applicationReady` 只在完整模块初始化成功后置位。临时身份读取不完整或 APK 路径不一致会安全等待重试，不会永久消耗首次启动机会。
- `WechatApiFeature` 被标记为必需核心功能并移到功能列表首位。核心 API 建立失败时阻断业务功能安装；`FeatureManager` 保留可重试状态，成功功能不会重复初始化。
- `HookRegistry.withFeature` 为同步及异步 Hook 保存功能归属。异常/取消会清理该功能本次部分安装的 Hook；同一键的活跃 Hook 仍由 `hookOnce` 去重。
- 运行代码改变时，调度器取消仍使用旧运行时上下文的功能任务。`FeatureManager` 比较 Hook 声明类在新 ClassLoader 中的实际 `Class`，只重验受影响功能和公共 API；重验失败时只保持该功能 Hook 停止并有限重试。

### APK 路径与 Native 缓存

- Native 库继续使用框架提供的模块 `ApplicationInfo.sourceDir`，按 APK 条目的 CRC、ABI 和文件锁提取版本化缓存；成功加载只在当前模块 ClassLoader 生命周期内缓存。日志增加框架加载 APK 的版本、路径、已安装路径和库 CRC。
- 宿主身份对照框架 `PackageReadyParam`、当前 `Context` 与 `PackageManager` 的 APK 路径。路径不一致时记录诊断；已加载的框架路径继续作为 DexKit 与宿主归档的输入，不搜索历史 APK，也不跨版本回退 Native 库。
- DexKit bridge 缓存键现在包括规范 APK 路径与归档身份摘要，避免同一路径内容被替换后继续复用旧 bridge。
- 没有找到历史验收中框架模块 APK 路径错误或升级路径竞态的直接证据；本轮增强了三路路径诊断及同路径内容替换保护，没有宣称旧路径问题已在真机上复现。

### Tinker 身份与 Hook 代次

- 宿主安全身份只比较目标包和框架报告的实际加载 APK 归档身份。真实 APK 身份变化仍触发全局失效保护。
- Tinker 补丁文件与版本元数据保留作诊断，不再放入宿主身份或 DexKit 缓存键。活动补丁代码身份只从当前 ClassLoader 描述的 Tinker DEX archive 读取；未活动及历史 patch 目录不会参与身份。腾讯 Tinker 的 ClassLoader 日志示例显示活动 DEX 位于 `tinker/patch-*/dex/classes.dex.jar` 路径中，[Tencent Tinker 示例](https://github.com/Tencent/tinker/issues/466)。
- 代码代次变化返回独立决策，触发定位缓存与受影响 Hook 复核；ClassLoader 换代但 APK 未变时不撤销整个模块。无法确认新类定义的 Hook 会单独停用。
- `onBaseContextAttached` 的热更新拦截先于身份元数据读取安装；`chain.proceed()` 后再按新 ClassLoader 做身份与代码代次检查，避免短暂身份读取不完整直接漏掉早期拦截。

### 禁止热更新

- 默认开关与 `autoHotReload` 配置未改变。
- 早期 `getTinkerFlags`、每个 `tryLoadPatchFilesInternal` 重载，以及后置响应/手动更新/Legacy 入口分别记录 `HookRegistry` 安装代次与真实状态。
- 部分成功时只补装失败入口；成功入口通过 `hookOnce` 保持唯一。不存在的可选入口记录为 `NOT_APPLICABLE`，避免对不适用版本重复扫描。

## 先红后绿的故障注入

修复前在当前代码边界加入受控故障用例并执行，以下断言均失败；修复后相同用例通过：

1. 核心 `onInit` 第一次失败：旧 `FeatureManager` 仍初始化下游，第二次调用不恢复。修复后下游暂停，核心重试成功后各功能只安装一次。
2. 只改写 Tinker 元数据：旧 `WeChatVersionApi` 改变宿主身份和定位缓存键。修复后两者保持稳定；活动 `classes.dex.jar` 内容变化则更新运行代码身份与定位缓存键。
3. 早期两个 TinkerLoader 重载中第二个 Hook 失败：旧状态把首个成功误记为整个加载器完成。第二次安装保持两个 Hook 而不是补全三个；修复后仅补装失败重载。
4. 后置两个响应 Hook 中第二个 Hook 失败：使用原版 `DisableHotUpdateFeature` 注入失败，旧 `lateInstalledLoaders` 跳过后续补装。为适配当前测试调用签名，基线运行只加了返回值包装，未改旧门闩逻辑。修复后公开安装入口完成缺失 Hook，成功入口无重复。

另外覆盖真正 APK 身份变化仍触发失效、临时身份不完整可重试、ClassLoader 换代、活动补丁代码换代、不同功能类定义比较、每功能部分 Hook 回滚、重复安装去重、调度器重试耗尽后重新排队，以及框架/Context/PackageManager APK 路径不一致诊断。

## 验收结果

- `:app:testDebugUnitTest`：145 tests，0 failures、0 errors、0 skipped。
- 所有仓库顶层回归和启动/身份专项回归：36/36 通过，包含 `run_performance_round1_tests.cjs`、`run_performance_round2_tests.cjs`、API 102、DexKit、Native 提取、语音兼容与业务回归。
- Release Lint：654 warnings，0 errors、0 fatal。
- `:app:assembleRelease`：通过；日志确认执行 `minifyReleaseWithR8`、`verifyReleaseSigning`、`packageRelease` 和 `assembleRelease`。
- APK：`h.Hchat`，`1.2.1`，`versionCode=261103`，ABI 为 `arm64-v8a`；Xposed module metadata 与 `libdexkit.so`、`libsilk_codec.so` 均存在。
- 签名：`apksigner verify --verbose` 通过 v2；签名证书 SHA-256 为 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`。
- APK SHA-256：`c802ed88adfa1a50a7869a5215b19c2782c12308e2dfec62c915b74a852e5574`。

## 未验证事项

本轮没有读取或改变微信用户数据/设置，没有安装 APK、重启或强停微信。实际微信 8.0.76+、8.0.80、双包、API 102 下的首启恢复、Tinker 真实代码换代后的 Hook 补装及 Native 库映射仍为 **NOT_VERIFIED**。JVM/Robolectric/隔离运行器证明状态机和失败清理逻辑，不替代真实运行态验收。
