# WeiPlus 8.0.80 阶段 B R2 收口

基线：`a5ba1e8afbdf77aed5dd38d14151d8ad84395ced`

本轮只处理全局版本门禁和 DexFinder 完成态阻断定点重试。兼容范围仍以 8.0.76 及以上为维护目标；版本不再决定模块能否加载。本报告不代表 87 项功能均已兼容。

## 验收状态

| 状态 | 项目 |
|---|---|
| **PASS** | 删除全局版本拒绝；两个微信包名和各自进程命名空间仍经过严格入口过滤 |
| **PASS** | 真实 DexFinder locator 在全局扫描完成后仍可定点重试；有效 locator 走零查询快速路径 |
| **PASS** | 冷/热身份、APK 变化、Tinker ClassLoader 轮换、补丁变化及 Hook 代次回归 |
| **PASS** | 69 个 Debug JVM 单测；29/29 仓库回归脚本；启动、阶段 B、Native 空闲回收脚本 |
| **PASS** | Debug 构建、Lint、Release Kotlin/Java 编译、`minifyReleaseWithR8` |
| **PASS** | ADB 只读确认设备上现有 `h.Hchat` 证书 SHA-256 为目标值 |
| **BLOCKED** | 正式签名 Release：keystore 文件存在，但 Gradle 所需既有正式凭据未找到 |
| **NOT_VERIFIED** | 新模块覆盖安装、微信 8.0.80 启动、设置页、Tinker、真实设备缓存和 Hook 状态 |

## R1 基线复现

在修改生产逻辑前，基于 `a5ba1e8` 运行了两项临时失败回归：

1. 入口版本身份回归要求合法目标包的 `8.0.49` appbrand 进程不因版本被整体拒绝。旧 `WeChatVersionGate` 返回拒绝，断言失败。
2. `DexFinderFinishedSnapshotRetryTest` 直接调用生产 `resolveConversationMuteApi()`、`resolveDatabaseApi()` 和 `resolveContactCardApi()`。将全局状态置为扫描已完成后，旧 `ensureSnapshotLoaded()` 直接返回 true，三类 locator 均没有发起查询，首个查询计数断言失败。

临时基线版本测试已删除，旧 `WeChatVersionGate` 和对应拒绝回归也已删除；不保留兼容壳。

## 生产改动

### 入口与宿主身份

- `ModuleEntry.onPackageReady()` 现在只调用 `WechatPackage.isModuleEntryScope()` 检查精确包名和包自己的进程命名空间，再安装入口 Hook。主进程、`:appbrand*` 及其他微信子进程保留原作用域处理。
- package-ready 阶段不再读取 PackageManager，不反射获取系统 Context，也不遍历 APK ZIP 元数据或 Tinker 文件。运行时身份在真实 Context 可用后建立。
- 删除 `WeChatVersionGate`。低版本、未来版本、未知 versionName 和非标准 versionCode 不再触发全局 Hook 拒绝。`WeChatVersionApi` 继续提供版本诊断和缓存身份。
- 宿主代次 key 由包名、APK/DEX 元数据和 Tinker 补丁身份组成，不含版本号、构建号或 ClassLoader。版本变化和合法 ClassLoader 轮换不会关闭整个模块；补丁/APK 身份变化仍取消旧安装任务并撤销旧 Hook。
- 运行时版本信息按当前 loader 代次复用，已知 Tinker attach 完成后刷新一次；不加轮询或常驻任务。版本字段仍可参与 Dex 缓存 key，所以缓存身份变化会使该缓存失效，但不会拒绝模块加载。

### DexFinder 定点恢复

- `ensureSnapshotLoaded()` 现在只负责首次读取缓存，不再用 `resolutionFinished` 判断当前 locator 是否成功。35 个调用点均不会因全局扫描完成而直接返回。
- 每个 locator 继续依据自身的结构与字段完整性检查结果：有效结果走快速路径；缺项或无效结果执行对应定位方法。`resolveAll()` 的完成状态仍表示全局扫描结束，重复调用不会再次全量扫描。
- 通过 `DexFinderQuery` 抽出 DexKit 查询边界，测试直接驱动生产 `DexFinder` locator 方法并记录查询数。生产端仍调用同一个 DexKit bridge，DexKit 串行执行、HookRegistry 安装代次及 30 秒 Native 空闲回收机制未改变。
- 联系人服务候选会先移除无效方法；语音、表情、视频及群成员昵称 locator 在复用缓存结果前检查对应签名，缺失时保留定点恢复路径。

## 回归结果

### 版本、入口和 Tinker

- `WechatPackageEntryScopeTest` 覆盖 `com.tencent.mm` 与 `com.tencent.mm.wx` 的主进程、appbrand、tools 子进程及包名前缀冲突。
- `WeChatVersionInfoIdentityTest` 覆盖低版本名 `8.0.49`、未来/非标准版本名、缺失版本名和未知数字 versionCode；这些版本字段不改变宿主代次 key。APK 身份或补丁身份变化会改变 key。
- `HostRuntimeIdentityLifecycleTest` 覆盖 Tinker ClassLoader 变化被接受以及真实补丁变化触发代次失效。全局版本拒绝类及相关用例已删除。

### 缓存与定位查询数

真实 `DexFinder` 控制流回归将全局扫描设为已完成，再依次调用缺失的私聊免打扰、数据库和联系人 locator：

| 路径 | fixture 查询数 |
|---|---:|
| 私聊免打扰首次定点尝试 | 5 次 Method 查询 |
| 私聊免打扰再次尝试 | 5 次 Method 查询 |
| 数据库缺项 | 4 次 Class 查询 |
| 联系人缺项 | 2 次 Method 查询 |
| 已验证服务 getter 热命中 | 0 次 DexKit 查询 |
| 再次调用已完成的 `resolveAll()` | 0 次 Method/Class 查询 |

这些计数来自测试查询后端对生产 locator 方法的观测，不是设备 APK 的 Native DexKit 计数。冷/热缓存、损坏描述符、身份变化和缓存恢复由既有单测与回归继续覆盖；实际 8.0.80 设备查询次数因未覆盖安装而 **NOT_VERIFIED**。

### 自动化

- **PASS** Debug JVM 单测：69 项，0 failures，0 errors。
- **PASS** `scripts/run_*_tests.cjs`：29/29。
- **PASS** `scripts/tests/startup/run.js`、`startup_version/run.js`（27 项）、`phase_b/run.js`、`PhaseBSourceRegression.cjs`（10 项）。阶段 B 脚本覆盖冷/热 Native handle 与 30 秒空闲回收、惰性恢复及 close。
- **PASS** `assembleDebug`、`lintDebug`、`compileReleaseKotlin`、`compileReleaseJavaWithJavac`、`minifyReleaseWithR8`。
- Lint 通过；输出包含既有 `firstOrNull()` 选择警告。Release 只完成编译与 R8，本轮未执行签名。

## 启动性能与设备

package-ready 路径现在只做少量包名/进程字符串比较，不做 APK ZIP 或 Tinker 目录扫描。运行时身份扫描移动到 Context 可用后的身份核验，并在同一 loader 代次复用结果；Tinker 完成时按需刷新。该路径的代码与回归已检查，但没有本轮真机耗时数据。

ADB 只读读取设备现有 `h.Hchat` APK，并用 `apksigner verify --print-certs` 核对证书：

`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`

与目标证书一致。设备上没有安装本轮产物，也没有启动微信或改动微信数据。

## 正式签名与未验证边界

- 正式 keystore `/Users/muchen/Downloads/Android App正式签名密钥/MUCHEN.jks` 存在，大小 2,281 bytes。
- 当前构建所需 `HCAT_STORE_PASSWORD`、`HCAT_KEY_ALIAS`、`HCAT_KEY_PASSWORD`、`HCAT_KEYSTORE_PATH` 环境变量均未配置；仓库/用户 Gradle properties 与标准 shell 配置中也未发现这些已存在的凭据设置。没有猜测密码、输出秘密或更换证书。
- 因无法安全取得正式凭据，正式签名 Release 为 **BLOCKED**。未用 Debug 包替代，未运行正式覆盖安装；本轮设备设置入口、微信启动、Tinker attach、缓存命中和 Hook 安装状态均为 **NOT_VERIFIED**。
- 本轮没有真实发消息、支付、改联系人或发布朋友圈，也不据此宣称 87 项功能全面兼容。
