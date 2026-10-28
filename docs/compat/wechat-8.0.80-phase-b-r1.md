# 微信 8.0.80 阶段 B 严格验收返工 R1

- 日期：2026-10-29
- 分支：`main`
- R1 基线：`face63c100d516294c630882436e1f589be2a234`
- 目标包：`com.tencent.mm`、`com.tencent.mm.wx`
- 最低版本：8.0.76（含）

## 验收状态

| 状态 | 结果 |
|---|---|
| **PASS** | `.76` / versionCode 3140、3141，`.80` / 3220，以及两个包名和主进程、子进程的版本门禁回归。低于最低线、非法包/进程/版本及已知跨版本矛盾身份拒绝。 |
| **PASS** | 平板缓存使用完整 APK/DEX/Tinker key；七段旧 key 与损坏身份拒绝；相同可信 key 命中；同版本号但 APK 元数据变化会失效。资源型 split 无 DEX 时仍形成有效身份。 |
| **PASS** | Tinker ClassLoader 代际切换回归：包体/补丁身份相同时接受新 loader；APK/补丁身份变化使旧代次失效，并进入现有取消任务、撤销 Hook 的路径。 |
| **PASS** | DexFinder 热缓存恢复策略计数：AddMsg、文本请求和 SNS 视频三个有效描述符均恢复时，DexKit 查询回调计数为 0；身份变化、损坏描述符或缺项分别触发对应定点回调。 |
| **PASS** | AddMsg 去重回归：无 msgSvrId 的相同内容连续消息均通过；有 msgSvrId 的重试只派发一次；过期后重新通过；并发重试只通过一次；600 项输入后集合保持在 512 项，缓存不保留完整消息正文。 |
| **PASS** | Debug 单测 65 项、Lint、Debug 构建、Release Kotlin/Java 编译、R8 minify，以及仓库 33 个回归脚本全部通过。 |
| **PASS** | 手机现有 `h.Hchat` APK 只读签名检查通过，证书 SHA-256 为目标值 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`。 |
| **BLOCKED** | 正式 Release 签名所需的密码和 alias 当前未配置；keystore 文件存在，但本轮未猜测密码或 alias，未生成正式签名 Release。 |
| **NOT_VERIFIED** | 本轮未覆盖安装模块、启动微信或验证真机 Tinker、缓存和 Hook 回调。签名步骤受阻，因此没有安装 Debug APK。 |
| **NOT_VERIFIED** | 87 项功能逐项兼容、官方原版微信 8.0.76 的结构，以及真实微信内的 Hook 回调仍未验证。 |

## R1 基线复现

在生产修复前加入回归并针对基线生产代码执行：

- 版本门禁回归在 `8.0.76/3141` 失败，原因是旧逻辑把 3141 当成与 8.0.76 矛盾；旧逻辑也将 `.76` 错误地限定为 3140。
- AddMsg 去重回归复现同一会话同一秒的相同正文会误丢合法消息，以及 600 条有效身份使旧集合超过 512 项。
- 平板模式 Robolectric 回归复现其七段 key 与完整运行时 key 不相等，热缓存无法命中。
- 资源 split 回归复现旧 `forApplication()` 对无 DEX split 返回空身份。
- 新增同版本、同长度、同 mtime、同 DEX、仅资源条目变化的 APK 探针：基线 `DexArchiveIdentity` 得到相同身份；修复后身份不同。探针未写入仓库，临时目录已删除。
- 对基线 `ModuleEntry` 的运行时校验确认，Tinker attach 后会把运行时 loader 与 package-ready loader 直接比较；旧 `DexFinder.loadCache()` 对三个缓存 locator 分别调用额外锚点定位。两项均由状态迁移/查询计数生产回归覆盖修复后的行为。
- 独立复核发现，基线 `DexCacheIdentity.sameRuntime()` 在长度检查后才做完全相等判断，七段相同 key 本身已被拒绝。因此该“提前相等返回 true”的缺陷在指定基线上不可复现；本轮仍加强了完整字段校验，并移除了 DexFinder 的直接字符串相等绕行。实际平板七段 key 热缓存失配在基线上可复现。

## 生产修复

### 版本门禁

- `8.0.76` 的 3140、3141 均接受；已知跨版本矛盾 `.76/3220` 与 `.80/3140、3141` 拒绝，`.80/3220` 正常。未知 versionCode 只作为辅助证据，不覆盖数值 versionName 判断。
- 入口仍先比对 PackageReady 与 PackageManager 的包身份、base/split 路径及进程名，再通过统一门禁；检查使用系统 Context，不要求尚未创建的宿主 Application。
- 覆盖 `com.tencent.mm` / `com.tencent.mm.wx`、主进程和 `:appbrand0`、`:tools` 子进程；缺失、格式错误、版本身份冲突与非目标进程拒绝。

### 缓存身份及热路径

- `WeChatTabletFeature.cacheKey()` 统一取 `WeChatVersionApi` 完整运行时 key。`DexCacheIdentity.sameRuntime()` 要求至少十段身份，核验包名、数值版本、versionCode、APK/DEX 身份、Tinker 补丁文件身份和 scope，再比较完整 key；七段旧 key 不会命中。DexFinder、SettingsDexFinder、DexMethodCache 与平板缓存调用相同校验。
- `DexArchiveIdentity` 读取 ZIP 中央目录条目名、CRC 和大小，并计算这些小型元数据的 SHA-256；不打开或散列 APK/DEX payload。base APK 同时识别资源元数据变化；资源/配置 split 允许没有 `classes.dex`，但身份仍包含该 split 的中央目录元数据。
- `DexFinder.loadCache()` 先按完整身份恢复并验证缓存 Method/Constructor 的 owner 和结构。AddMsg、文本请求、SNS 视频使用独立恢复策略；结构有效时不执行完整锚点扫描，明确缺项或损坏时只调用相应 locator。SNS 视频恢复不会借机查询文本、图片、commit 或上传管理器链路。
- `DexFinderCacheStore` schema 版本保持阶段 B 的新 schema；旧 schema 不会作为新快照命中。保留 30 秒 Native 空闲回收、惰性恢复和 DexKit 串行执行。

### Tinker 身份代际

- `HostRuntimeIdentityDecision` 作为 `ModuleEntry` 实际使用的有状态守卫，分别记录获准的包体/补丁身份和 loader 身份。
- 同一包体与补丁身份下的 loader 轮换被记录为新 loader 代际并允许继续安装；真正的 APK DEX、版本或 Tinker 补丁身份变化会触发既有 `invalidateHostIdentity()`，取消待执行 Dex 安装任务并撤销 HookRegistry 代次。重复失效不会重复清理。
- 可执行 Java/JUnit 生命周期回归覆盖 package-ready loader、Tinker loader、真实补丁身份变化、不完整身份及旧代次保持失效。API 102 HookRegistry 安装代次和撤销仍由现有集成回归覆盖。

### AddMsg 事件去重

- 缺少可靠 `msgSvrId` 时不入缓存、不去重，避免用会话、发送者、秒级时间或正文丢弃合法消息。
- 有身份时用 talker 与 `msgSvrId` 构造短 key，保留短时重试去重。并发映射加原子容量预留及 FIFO 清理，集合硬上限 512；没有保存正文，也没有全局锁或额外线程。

## 测试、构建与 APK 身份

- **PASS** `:app:testDebugUnitTest`：65 tests，0 failures，0 errors，0 skipped。
- **PASS** Debug、Lint、Release Kotlin/Java compile 和 `minifyReleaseWithR8`：
  `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac :app:minifyReleaseWithR8`。
- **PASS** 33/33 仓库测试入口，包括 API 102 HookRegistry、安装代次和异常隔离，DexKit cold/warm/30 秒回收与惰性恢复，消息、数据库、设置入口、Native 与性能回归。
- **PASS** 门禁独立生产入口回归覆盖包名、进程、`.76/3140`、`.76/3141`、`.80/3220`、低版本和非法/矛盾身份。
- **PASS** Debug APK 由 `apksigner verify` 验证，身份为 `h.Hchat`、versionCode `1`、versionName `1.0.0`，大小 `23,988,833` bytes，SHA-256 `3b01dcaa2d9b5888a913bad27c3aaa92f8c053e1e5933d88adb284ad516e5f53`。证书为 Android Debug，SHA-256 `be329aa3605e5f74f5cc758dc08ae275a7c818e7198279458f2561876115182b`；只作构建验证，不作为正式包或覆盖安装替代品。
- **BLOCKED** 正式 keystore 路径 `/Users/muchen/Downloads/Android App正式签名密钥/MUCHEN.jks` 存在；Gradle 使用环境变量 `HCAT_KEYSTORE_PATH`、`HCAT_STORE_PASSWORD`、`HCAT_KEY_ALIAS`、`HCAT_KEY_PASSWORD`，本机当前未提供后三项及自定义路径。未猜测凭据，也未运行正式签名任务；Release APK 不存在。
- 因本轮没有 keystore 凭据，未独立重新读取 keystore 中的证书；阶段 B 原报告曾记录该 keystore 指纹与目标匹配。本轮独立确认的是设备现有模块证书指纹。
- **PASS** 只读提取手机 `feb70d35` 上已安装的 `h.Hchat` APK 并由 apksigner 验证。包身份为 `h.Hchat`/1/1.0.0，证书 SHA-256 与用户给定目标一致。检查仅读取 APK；未覆盖安装。

## 未验证边界与后续

- 真机未启动微信，故 Tinker attach 前后 loader、DexKit 热缓存查询数、Hook 实际安装/回调及设置入口均为 **NOT_VERIFIED**。
- 正式 Release 签名和覆盖安装为 **BLOCKED**，待现有正确凭据可用后再执行；不得使用 Debug 包替代。
- AddMsg、消息屏蔽、文本请求和 SNS 视频只做生产代码结构及安全模拟回归，没有真实消息发送或真实朋友圈发布。
- ECS 礼物 `updateGiftMsgByCgi` 仍无可信定位证据，保持未就绪；未把普通 AddMsg 观察当作礼物链路成功。
- 以上结果不代表 87 项功能全面兼容。R1 到此结束，不进入阶段 C。
