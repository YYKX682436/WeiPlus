# 微信 8.0.80 核心适配：阶段 B 结果

- 日期：2026-10-29
- 仓库：`/Users/muchen/Documents/ChatGPT/WeiPlus`，`main`
- 实施基线：`24e9f618fde6b0efaa14db277b8b620248f54f3f`
目标：`com.tencent.mm` 与 `com.tencent.mm.wx`，最低微信版本 8.0.76（含）

## 结果概览

阶段 B 的源码修复与回归已完成。版本门禁、DexKit 缓存身份、AddMsg 观察/前置屏蔽、文本请求构造器、朋友圈视频 helper、调度完成状态和指定低版本分支均有对应生产代码与回归。

| 状态 | 结果 |
|---|---|
| **PASS** | 版本门禁、缓存、API 102 HookRegistry、AddMsg、文本构造器、朋友圈视频 helper 及其他现有回归；完整 Gradle 检查和仓库回归脚本通过 |
| **PASS** | Debug APK 已构建并通过 `apksigner verify`；SHA-256 为 `884717b5e2912e992f038869590ea3bd6339c4643dafc879cec41d08130cf15d`，大小 23,821,560 bytes，Debug 证书 SHA-256 为 `be329aa3605e5f74f5cc758dc08ae275a7c818e7198279458f2561876115182b` |
| **PASS** | 已核对正式签名 keystore 的证书与设备现有模块证书一致，SHA-256 为 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd` |
| **BLOCKED** | 正式 Release 打包/签名所需的 `HCAT_STORE_PASSWORD` 与 `HCAT_KEY_ALIAS` 当前不可用；Release Kotlin/Java 编译及 R8 minify 通过，但未生成或安装正式签名 APK |
| **NOT_VERIFIED** | 未在真机安装本次构建或启动微信验证运行时 Hook。静态兼容和回归通过不代表 87 项功能已兼容 |
| **NOT_VERIFIED** | 没有可信官方 8.0.76 APK。`.wx` 8.0.76/3140 是经修改的对照样本，不能代表官方版 |

本阶段未卸载微信、清除微信数据、重装微信、发送真实消息、修改联系人、支付或发布朋友圈。Debug 证书与正式模块证书不同，因此没有尝试用 Debug APK 覆盖安装。

## 证据基线

- 阶段 A 审计记录的微信 8.0.80 正式 APK SHA-256 为 `c35a05d5c13bd43cf1f7ff42c13fab070d21509ab2e233596f776d1afcae52f5`；包名 `com.tencent.mm`、versionName `8.0.80`、versionCode `3220`，共 18 个 DEX。该 APK 与设备当时安装的正式包匹配。
- 8.0.76 对照样本为 `com.tencent.mm.wx`、versionCode `3140`、SHA-256 `19b71802548d9a30d87c344fc0457270b1e7f2ceefdcfad4f15386fcab62c6b4`，共 17 个 DEX。DEX 中有修改痕迹，只用于已核实的结构和调用链对照。
- 使用 DexSQL 对两个样本的 DEX 表、方法调用/字段访问关系和相关 Smali 做了结构查询。阶段 A 记录详见 [wechat-8.0.80-audit.md](wechat-8.0.80-audit.md)。DexSQL 的静态结构证据不等于微信进程中的 Hook 回调验证。

## 生产实现

### 版本门禁与身份代次

- `ModuleEntry.onPackageReady` 先校验 PackageReady 参数、系统 PackageManager 结果及 APK/split 路径，再通过集中门禁检查版本；门禁在任何 Tinker、平板、底栏或 appbrand 业务 Hook 前运行。版本读取使用系统 Context，不要求宿主 `Application` 已创建。
- 只接受两个目标包名及对应主/子进程。versionName 使用数值段解析；8.0.75 拒绝，8.0.76 和 8.0.80 接受。缺失、格式异常、包/进程/PackageManager 身份矛盾或已知版本与 versionCode 冲突均 fail closed。versionCode 不会单独放行。
- Tinker attach、主进程 Application 初始化及后续运行时身份检查复核批准的包、版本、APK DEX、补丁与 ClassLoader 身份。身份变化会使旧 HookRegistry 代次失效、撤销 Hook，并取消尚未执行的 Dex 安装任务。
- 版本门禁生产入口回归覆盖两个包名、主进程与 appbrand 等子进程、8.0.75 拒绝、8.0.76/8.0.80 放行及异常/矛盾身份拒绝。

### DexKit 缓存

- 全局缓存 schema 提升至 4；分别记录扫描完成与关键 locator 完成。解析流程结束、关键定位成功、Hook 实际安装成功和回调已验证分别表示，不再共用一个“resolved”状态。
- 关键 AddMsg、文本发送与视频 locator 未命中时不会把空结果保存成完整有效快照；重试针对缺失 locator 定点进行，不要求下一次启动重扫整个 APK。读取缓存会验证反射成员描述符，包括 owner、参数及返回类型。
- Runtime identity 包含包名、versionName/versionCode、clientVersion、Tinker ID/patch ID、补丁文件身份与 APK/split 中 DEX 的 CRC/大小等轻量元数据；不在主线程计算整个 273 MB APK 的 SHA-256。`sameRuntime()` 比较完整身份 key，拒绝旧七段 key；若缓存 key 带额外 schema 后缀，也要求后缀完全一致。
- 保留 DexKit 30 秒空闲回收、查询时按需恢复、串行扫描及 HookRegistry 安装代次保护。

### AddMsg 观察与前置屏蔽

8.0.80 正式 DEX 与已安装的 8.0.76 修改版样本中，旧短锚点 `dkAddMsg`、`processAddMsg` 均不能用于定位。替代定位不是宽泛扫描相似方法：

- DexSQL 找到完整 AddMsg 处理日志文本 `dkAddMsg from:%s to:%s id:[%d,%d,%d] status:%d type:%d time:[%d %s] diff:%d imgstatus:%d imgbuf:%d src:%d push:%d content:%s flag:%s`，并找到同 owner 的入库失败文本 `processAddMsg insert db error`。调用关系、参数类型、请求 payload 字段、存储结果字段与消息对象类型共同约束唯一候选。
- 两个版本的 Smali 与调用边均指向同一结构：请求携带 AddMsg payload；静态主处理返回含已存储 message 的结果；同 owner 的 void 前置调用者先解析原始 payload，再进入主处理。8.0.80 的存储消息字段为 `com.tencent.mm.storage.f9`，请求 payload 与其存储结果分别验证，不依赖混淆类名。
- 消息观察 Hook 仅在主处理完成且得到存储消息结果后发事件；这满足观察语义。该 Hook 不用于屏蔽。
- `MessageBlockFeature` 只在定位到确证的入库前调用者后安装屏蔽 Hook；正常消息调用 `proceed()` 一次，匹配阻断时才短路，解析或回调异常 fail open。generation 失效时直接继续宿主链。两处 Hook 分离，因此观察不能伪装为入库前拦截。
- 消息事件按 talker/sender/time/server-id/content 做短期去重。API 可用性、Hook 安装和回调验证分别记录；依赖 AddMsg 但找不到锚点的功能保持未就绪。
- ECS `updateGiftMsgByCgi` 在两个 DEX 样本中均没有可用引用。它仍标记为未定位且可重试；没有以普通 AddMsg 观察冒充 ECS 礼物处理成功。

### 文本发送请求

- 结构锚点仍从 CGI、`NetSceneSendMsg` 等真实请求字符串及其方法 owner 找候选，再验证唯一 owner 与精确构造器签名；不写死混淆类名。
- DexSQL 的 8.0.76 修改版候选是五参数 `(String,String,int,int,Object|long)`；8.0.80 是六参数 `(String,String,int,int,Object|long,String)`。对应调用点保持 talker、content、messageType、sendFlag、sourceArgs 顺序。
- 第六个 String 的 Smali/call site 语义为 `msgSource`；普通文本发送路径以 `null` 构造，故六参版本明确传 `null`。`long` sourceArgs 重载仅接受没有 sourceArgs 的请求并使用 `0L`；不以空字符串伪装语义。不存在精确受支持构造器时请求创建失败，不发送。
- 回归覆盖 .76 五参、.80 六参两类结构与参数顺序；本阶段没有真实发送消息。

### 朋友圈视频 helper

- 从精确 `addSightObjectByPath`/`produceSightByPath` 锚点反查 helper owner，筛选 boolean 返回且全 String 的四参/五参方法；只有唯一候选通过，不按相似签名直接接受。
- 五参第五项经 8.0.80 方法体与 `SightWidget` 调用点确认是 `rawFingerPrintForXiaoWei`；标准调用者传 `""`。调用方同步转递确认的第五参数。四参路径继续使用原四项。
- 只执行构造器/参数和定位模拟测试；没有真实上传、发布或分享朋友圈。

### 旧分支与诊断状态

- 已移除已确认仅针对最低线以下的 `.49` 转发例外、Image API 8.0.66/versionCode fallback、转发限制 versionCode 2841 和朋友圈开关 versionCode 3020 门槛；能力/结构检测仍保留。
- 保留 `SettingsInjector` 的旧设置页尝试路径：官方 8.0.76 样本不可得，尚不能证明该兼容能力可安全删除。类似版本注释/结构保护也未仅凭历史数字清理。
- `WechatApiFeature` 与 `DexInstallScheduler` 以安装 Hook 的真实活动状态决定成功/重试；DexReady 表示解析阶段完成，不代表所有 API 就绪。取消代次、异常隔离及失败重试继续沿用事件驱动机制，不引入轮询或常驻线程。

## 测试和构建

- **PASS** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac :app:minifyReleaseWithR8 --no-daemon`。
- **PASS** 仓库全部 29 个 `scripts/run_*_tests.cjs` 回归脚本，以及 `scripts/tests/startup/run.js`、`startup_version/run.js`、`phase_b/run.js` 和 `PhaseBSourceRegression.cjs`（合计 33 项回归入口）。另由缓存回归覆盖全局快照冷读/热读、identity 变化、schema 迁移和损坏描述符；`phase_b/run.js` 中的托管句柄回归覆盖 cold open、warm reuse、30 秒空闲回收、下一次查询惰性恢复及 close。其余覆盖 API 102 HookRegistry/取消代次/异常隔离、消息和 DB、设置入口、Native、性能、版本门禁、两个消息构造器、SNS helper 与低版本分支。
- **PASS** 将新增的八项阶段 B 源码回归放到基线 `24e9f618fde6b0efaa14db277b8b620248f54f3f` 的生产源码上运行，八项均按预期失败；在修复后同一组八项全部通过。
- **BLOCKED** `assembleRelease` 需要 `HCAT_STORE_PASSWORD` 和 `HCAT_KEY_ALIAS`；当前凭据未配置，未生成 Release APK。Release 编译和 R8 已通过，不把它们报告成完整签名 Release 构建。
- **PASS** Debug APK 签名结构验证通过。Debug 证书与现有正式模块证书不匹配，因此只报告构建身份，没有覆盖安装。

## 真机与未解决项

- **NOT_VERIFIED**：设备上既有 `com.tencent.mm` 8.0.80/3220、`com.tencent.mm.wx` 8.0.76/3140 修改版及 `h.Hchat` 模块的只读 PackageManager 证据。未安装本次 APK，未启动微信检查 LSPosed 加载、Tinker 时序、ClassLoader、DexKit cache hit/cold 或实际回调。
- **NOT_VERIFIED**：官方微信 8.0.76 结构，以及 .80 中 87 个功能逐项运行表现；本报告不能据此宣称 87 项已兼容。
- **未就绪**：ECS 礼物链路 `updateGiftMsgByCgi` 暂无经证实的安全替代入口，保持未定位/可重试，留待专项适配。
- Release 正式签名与真机覆盖验证需在签名凭据可用后另行执行。阶段 B 到此结束，不自动进入阶段 C。
