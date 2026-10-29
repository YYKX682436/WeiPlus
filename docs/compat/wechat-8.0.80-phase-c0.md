# WeiPlus 阶段 C-0：正式签名与真机基础验收

基线：`2dbd70c3b5cc9a2ed13cd3c3af2fb6523f53e7cb`
设备：USB ADB `feb70d35`，Redmi K50 Ultra（设备标识 `22081212C`）
日期：2026-10-29

## 状态摘要

| 状态 | 验收项 |
|---|---|
| **PASS** | 从下载目录原有 `MUCHEN.jks` 与 `签名密钥密码.env` 恢复本地签名；未生成/更换证书、未运行发布工作流 |
| **PASS** | Release APK 签名有效，证书 SHA-256 与设备原模块及目标值一致 |
| **PASS** | `adb install -r` 覆盖安装成功；设备已安装模块 APK SHA-256 与最终 Release 完全一致，未卸载或清除数据 |
| **PASS** | LSPosed API 102 在官方微信主进程及 appbrand 子进程加载；临时加入 scope 后，修改版主进程也加载 API 102 |
| **PASS** | 观察到的冷/热启动窗口中未发现微信 FATAL EXCEPTION 或 ANR |
| **FAIL** | 两个包的日志均报告数据库变更监听未就绪：`databaseHooks=31 wrapperHooks=0 wrapperInserts=0`；依赖该 API 的发送统计提示不可用 |
| **BLOCKED** | `DexMethodCache.runtimeKey()` 在启动期反复遍历 APK ZIP central directory，已测出明确性能阻断；按要求停止其余业务验证，留待下一专项修复 |
| **NOT_VERIFIED** | 微信内设置入口的可见性、具体 HookRegistry 条目、AddMsg/文本请求/朋友圈 helper 的实际定位与安装、设备冷/热缓存及 30 秒回收 |

本报告不代表 87 项业务功能全面兼容。本轮没有发送消息、操作红包或转账、修改联系人或发布朋友圈。

## 正式签名与安装身份

历史验收记录 `docs/acceptance/CORRECTNESS_CLOSURE_2026-10-24.md` 和 `docs/acceptance/LIBXPOSED_API102_2026-10-25.md` 指向现存 `MUCHEN.jks` 及标注凭据文件。文件现位于 `/Users/muchen/Downloads/Android App正式签名密钥/`。我按 Gradle `HCAT_*` 配置从该文件在进程内读取凭据；值没有打印、写入仓库或传入命令行。别名与 JKS 条目匹配，keytool 导出的证书指纹匹配目标证书。

- 构建方式：本机直接运行 `:app:assembleRelease`，使用现有 Release signing config 与 R8；没有运行 GitHub Release/正式发布 workflow。
- 最终未插桩 APK：`dist/Hchat-c0-2dbd70c.apk`
- APK SHA-256：`2e3e66b25f33c6663921bfe19fd1f1af1bd5ad37a0eff3a14e52fd2948230a49`
- `apksigner verify`：**PASS**
- 证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`
- 设备现存 `h.Hchat` 原包在安装前使用同一证书。覆盖安装后从设备拉取 `base.apk` 只读核验，SHA-256 与上述最终 APK 完全一致，证书也相同。
- 覆盖安装命令为 `adb install -r`，返回 `Success`。没有卸载模块、清除模块数据或触碰微信数据。

测量用构建只临时插入计数与耗时日志，使用同一正式证书，未提交源码或 APK。测量后已恢复源码、重新安装上述无插桩正式 APK，并停止两个微信进程使测量代码卸载。测量 APK 已清理。

## 设备宿主与加载结果

安装前后读取到的包身份：

| 包名 | versionName | versionCode | 运行结果 |
|---|---:|---:|---|
| `com.tencent.mm` | `8.0.80` | `3220` | 冷启动和热启动成功；API 102 在主进程与子进程日志中出现 |
| `com.tencent.mm.wx` | `8.0.76` | `3140` | 临时加入 LSPosed scope 后冷启动和热启动成功；主进程和 push 子进程出现 API 102 日志 |

`h.Hchat` 在 LSPosed 中原本只对 `com.tencent.mm` 启用。为执行用户要求的双包验证，我暂时通过 LSPosed 管理器勾选 `com.tencent.mm.wx`，完成冷/热启动检查后恢复了原配置。最终只保留原有 `com.tencent.mm` scope；模块仍启用。修改版测试后没有观察到 appbrand 子进程，因此修改版 appbrand 加载为 **NOT_VERIFIED**。

LSPosed 模块日志明确记录 `module_loaded api=102`、`framework=LSPosed 2.1.0`。官方包主进程及 `:push`、`:appbrand0`、`:appbrand1`、`:hotpot..` 有 API 102 加载日志；主进程和 appbrand 进程记录 `host_identity=approved`。修改版主进程及 `:push` 也记录 API 102 和 `host_identity=approved`。两包可见的身份来源均为 `tinker_attach_before`。本次未观察到 Tinker patch attach 后的 ClassLoader 切换/`attach_after`，所以 attach 后路径为 **NOT_VERIFIED**。

`am start -W` 观察值：

- 官方包插桩前冷启动 `TotalTime=1651 ms`，热启动 `WaitTime=10 ms`。
- 修改版启用模块 scope 后，插桩前冷启动约 `3582 ms`，热启动 `WaitTime=8 ms`。
- 插桩测量时官方冷启动约 `1208 ms`、热启动约 `82 ms`；修改版冷启动约 `3857 ms`、热启动约 `93 ms`。这些启动值只代表本次设备及当前进程/缓存状态，不能与累积后台定位耗时混为一谈。

在所采集的本次启动 logcat 窗口内，两个包均未匹配到 `FATAL EXCEPTION` 或 `ANR in <package>`。这只证明观察窗口内没有对应记录，不替代长时间稳定性验收。

### Hook 与 API 边界

- **PASS**：框架 API 102 实际进入微信进程；宿主身份在入口获批；官方包 appbrand0/appbrand1 真实启动并加载模块。
- **FAIL**：官方包和修改版均记录 `[WeChatMessageChangeApi] 消息变更监听未就绪: databaseHooks=31 wrapperHooks=0 wrapperInserts=0`；随后 `[input_hint] 消息变更 API 未就绪，发送统计暂不可用`。这不是全局模块加载失败，但对应消息变更功能不可用。
- **NOT_VERIFIED**：当前日志对 DexFinder 的 `resolveAll` 和 HookRegistry 成功安装不提供逐项正向诊断；不能仅凭模块加载推断目标 Hook 成功。
- **NOT_VERIFIED**：未从设备诊断出 AddMsg 观察入口、文本请求构造器或朋友圈视频 helper 的实际目标描述符及安装回调。没有做真实发送或朋友圈操作。
- **NOT_VERIFIED**：本轮没有确认 Hchat 设置入口在微信界面中的可见性或点击跳转。`uiautomator dump` 只返回无文本的根节点，未取得设置入口的可执行 UI 证据；LSPosed 管理器显示模块启用不能替代该检查。

## 启动性能：明确阻断

为测量真实运行路径，临时在 `DexMethodCache.runtimeKey()` 与 `DexArchiveIdentity.forApplication()` 记录调用次数及函数内 elapsed time。日志仅由 Hchat 进程产生，并按包名和 PID 汇总。原方法每次 `runtimeKey()` 都调用 `WeChatVersionApi.buildCacheKey()`，后者重新调用 `DexArchiveIdentity.forApplication()`；该实现遍历 APK ZIP entry metadata，读取 central directory 中的 entry 名、CRC 与大小，不读取 APK/DEX payload。

| 包 | `runtimeKey()` 次数 | `runtimeKey()` 累计/单次中位数 | APK 身份遍历次数 | APK 身份累计/单次中位数 | 单次最大身份遍历 |
|---|---:|---:|---:|---:|---:|
| `com.tencent.mm` 8.0.80 | 71 | 4,596.72 ms / 64.441 ms | 71 | 4,537.39 ms / 63.610 ms | 83.580 ms |
| `com.tencent.mm.wx` 8.0.76 | 103 | 6,700.43 ms / 65.099 ms | 103 | 6,653.26 ms / 64.682 ms | 71.886 ms |

这些是进程内各次调用耗时之和，可能跨线程重叠，不应解释成 splash 的单条串行阻塞时长；但每次调用都重复做约 64 ms 的 ZIP 元数据遍历，启动期 71/103 次累计已达 4.54/6.65 秒，属于明确且可复现的启动工作放大。未改造或缓存该路径。按用户要求，发现此阻断后停止设置入口、特定 locator 和 Native 30 秒回收的后续验收，交由下一专项修复。

设备上的 cold/hot 启动时间已记录，但 DexKit 冷缓存、热缓存和设备侧 30 秒空闲回收本轮 **NOT_VERIFIED**。R2 自动化回归曾覆盖冷/热及 30 秒回收；它不替代本次因阻断而未完成的真机观察。

## 清理与交付边界

已恢复临时源码插桩、恢复 LSPosed 原始 scope、安装无插桩正式签名 APK，并 force-stop 两个测试过的微信包以结束测量进程。未修改微信数据。保留最终正式 APK `dist/Hchat-c0-2dbd70c.apk` 供检查；临时测量 APK、屏幕截图、UI dump、脚本和构建/Gradle 输出在收尾时清理。

本轮仅提交本验收报告，不改生产源码；性能阻断留给下一专项，不自动进入 87 项业务验收。
