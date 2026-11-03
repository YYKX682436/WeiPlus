# WeiPlus 启动与 API 换代 R1 验收

日期：2026-11-03

基线：`d1f71c58db2f88035303e3d2840086c3daf1e935`（main）

## 修复说明

### 1. `onPackageReady` 同步安装一次性入口

`onPackageReady` 现在在回调线程直接调用 `installPackageHooks`，然后才提交 APK 身份预热工作。`StartupEntryDispatch.installBeforeScheduling` 明确保持该顺序。Application 主入口 Hook 先于其他尽力型入口注册，并在框架瞬时拒绝时同步再试一次；之后仍保留有限异步补装。APK 归档扫描继续走后台串行任务，不占主线程，也不再排在关键入口 Hook 前面。

定向回归用单线程 Executor 阻塞预热任务，再模拟一次性事件；断言预热仍被阻塞时关键 Hook 已注册且事件只到达一次。旧提交的源码门禁在此项为 **RED**，修复后为 **PASS**。

### 2. 区分 Application 就绪与模块初始化状态

`ModuleStartupState` 独立保存 Application 已进入 `onCreate`、模块初始化正在执行、模块初始化成功三种状态。Tinker attach 只有在 Application 已就绪时才可提交模块初始化，因此 attach 先于 `Application.onCreate` 不会把初始化提前完成，也不会让 Application 回调误以为早期功能已经安装。

首次协议、会话标签、悬浮快捷入口和早期底栏按入口记录成功状态。每个成功入口只运行一次；失败入口保留补装资格。Application 回调同步执行这些不依赖 DexKit 的早期入口，再提交完整模块初始化。定向回归覆盖 Tinker 先到、Application 后到、失败后可重试及成功入口去重。

### 3. 隔离早期热更新 Hook 故障

Tinker 入口注册通过 `StartupEntryDispatch.installOptional` 独立执行。Tinker 类或方法缺失、Hook 注册暂时失败时只记录该入口的补装任务，`installPackageHooks` 继续注册 Application 与其他入口，不会因一个可选 Tinker Hook 失败而退出。Tinker attach 回调仍保留原有安装状态和限次重试。

定向失败注入使可选安装器抛错，验证重试被提交且后续主入口仍运行。旧代码的同步异常路径门禁为 **RED**，修复后为 **PASS**。

### 4. 核心 API 以必要 Hook 的真实状态判定就绪

`WechatApiFeature` 现在必须成功安装并确认当前 Activity、Activity 启动和 Activity 生命周期三个业务基础 Hook，才发布整组公共 API；只构造出 API 对象不再满足核心就绪条件。Dex 定位、网络、数据库监听等可延后的可选 Hook 各自捕获错误，不会阻断所有业务功能。必要 Hook 失败时 FeatureManager 会等待重试；局部 Hook 仍由安装代次回滚。

Robolectric/API 102 框架夹具在 `Activity.onResume` 注入真实 Hook 注册失败，确认核心安装返回未就绪；去掉注入后重试成功。另有核心 Hook 必需状态谓词测试。旧版本核心对象非空检查的源码门禁为 **RED**，修复后为 **PASS**。

### 5. 公共 API 换代时释放并重新绑定业务订阅

当 `revalidateRuntime` 要刷新公共核心 API 时，它将已安装业务功能加入本次定向复核队列，并保证核心 API 先完成替换。随后各业务功能通过 `onDestroy(previousContext)` 释放旧订阅，再用新上下文执行 `onInit/install` 建立新订阅和恢复功能。正常未发生核心 API 换代时仍只复核受影响的功能，不触发本路径。

隔离 FeatureManager 回归在公共 API 换代前后使用两组订阅总线：旧订阅被取消，新总线恰有一个活跃监听，旧总线不再回调，新总线事件只回调一次，功能安装状态恢复。旧提交上的可控订阅用例实际 **RED**，失败断言为旧代订阅仍活跃；修复后 **PASS**。

## 先红后绿与验收结果

- `R1StartupSourceRegression.cjs --baseline` 从基线提交读取源码，入口时序、Application/Tinker 门禁、热更新隔离、核心就绪和 API 订阅换代五项全部 **RED**；工作树源码五项全部 **PASS**。
- `FeatureManagerRecovery.kt` 的可控公共 API 换代用例在旧 FeatureManager 上因旧订阅未释放而失败，修复后通过。
- 定向单测：Application/预热阻塞、attach 先后顺序、早期入口去重和失败重试、可选 Tinker 注入失败隔离、必要核心 Hook 失败与恢复、业务订阅换代恢复均通过。
- 完整 `:app:testDebugUnitTest`：151 tests，0 failures、0 errors、0 skipped。
- 全部仓库回归：32/32 个 `scripts/run_*.cjs` 通过，其中 R1 专项 runner 3/3 通过。
- Release Lint：654 warnings，0 errors、0 fatal。
- `:app:assembleRelease`：R8 与正式签名构建成功；APK 签名验证成功，v2 签名有效，单一签名者。
- 构建后执行 `git diff --check`，无空白错误。

## 交付 APK

- 文件：`dist/Hchat-1.2.1-startup-recovery-r1-signed.apk`
- package/version：`h.Hchat` / `1.2.1` / versionCode `261103`
- APK SHA-256：`f3458ac5cdfc21ff7bd5bc7fdec1015bbe236d8206b2fd252a996be7cd40311c`
- 签名证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`

## 运行态边界

本轮未安装或启动微信，没有强停微信、清除微信数据或改变用户配置。入口同步时序、API 安装失败和订阅换代由 JVM/Robolectric、API 102 框架夹具与隔离回归验证；真实微信 8.0.76+、8.0.80、双包及设备上的 Tinker attach/Activity 时序仍为 **NOT_VERIFIED**。此前关于补丁元数据误判的修复保留；本轮也没有人为制造或触发热补丁。
