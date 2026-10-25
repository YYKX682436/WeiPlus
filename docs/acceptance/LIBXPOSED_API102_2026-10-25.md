# libxposed API 102 全量迁移验收（2026-10-25）

审查基线：`main` 的 `a712fd2b7b172b5aaf3cd139d2d0970ebed4cebb`。执行前工作区干净，fetch 后 origin/main 与基线一致。本次直接在 main 迁移，不保留旧入口或兼容层。

## 官方依据与版本

- [API 正式 102.0.0 标签](https://github.com/libxposed/api/tree/102.0.0)，源码提交 `45e7c5cfe54725b6d828d8b7be65e22ce60c67e4`。
- [Example](https://github.com/libxposed/example/tree/b94ee7e5f53d7039d38446b61605d6fa2a393c8f)：无参入口、注册、R8、Chain 和 ORIGIN invoker。
- [Service](https://github.com/libxposed/service/tree/0cfe0a4cd099f030f47251a6d5b35e259ec219c8)：核对通信边界，本次不引入。
- [Helper](https://github.com/libxposed/helper/tree/d2fb47f6caf7150f58fd049476308415375c1162)：其依赖仍为 API 100，本次不引入。

编译依赖为 `compileOnly("io.github.libxposed:api:102.0.0")`。最低与目标框架 API 都为 102；`staticScope=true`、`autoHotReload=false`。scope 只有 `com.tencent.mm` 与 `com.tencent.mm.wx`。框架 API 不随模块 APK 打包。

## 迁移边界

入口在 `onModuleLoaded` 后绑定框架接口、记录框架进程名；`onPackageReady` 使用官方包信息与应用 ClassLoader，先装 Application 与 Tinker attach Hook。attach 前安装必须提前生效的逻辑，attach 成功后重新取得宿主 ClassLoader。主进程、appbrand 与其他进程继续分流，Application 初始化按宿主包身份去重。FeatureContext 中无调用方的旧加载参数已删除。

注册只保留 `META-INF/xposed/java_init.list`、`module.prop`、`scope.list`；旧资产入口、Manifest 注册 metadata、旧 scope 数组和旧 API keep/仓库已删除。应用 label/description 使用标准属性。R8 使用官方入口保留与清单内容重写规则。

所有生产 Hook 直接使用官方 Executable、Hooker、Chain、HookHandle。XposedRuntime 只绑定框架身份、安装 HookBuilder 及提供事务式批量撤销；没有 before/after/param 仿制框架。HookRegistry 保留共享键、代次、取消租约、异常回滚和撤销重试语义。反射使用现有 KavaReflector 或标准反射；需要绕过链的调用使用官方 ORIGIN。

配置、资源、数据库、私有缓存和通知身份继续取当前微信宿主；内部微信类名独立保留 `com.tencent.mm.*`。不新增 Service、remote preferences、配置双写或整模块热重载。

## 关键调用语义

- 发送按钮：模块接管时宿主零次调用，正常路径一次；装饰与输入状态使用调用内局部变量和 finally。部分文字修改失败恢复原文；宿主异常不触发重发。
- 消息详情：保留 ThreadLocal 嵌套绑定栈，每个 frame 在 finally 退栈，成功后才合并和绘制。
- 气泡：恢复复用行旧样式、执行微信绑定、成功且仍启用才绘制；稳定关闭状态不扫描行树。
- 数据库和首页投影：query/mutation 进入退出配对，异常清理嵌套状态，成功才派发。保留业务 `legacyDepth`。
- 自定义头像：复制参数数组，保留请求的原群 ID；构造器完成后读取 thisObject，不能使用 null 返回值替代实例。
- 消息描述防崩：生产 interceptor 仅 proceed 一次，仅 MsgDescFallback 已确认的目标异常返回空串，其他异常保持原对象传播。
- 原图上传、扫码和朋友圈防撤回：真实辅助函数改写参数后以 `proceed(args)` 传入，测试覆盖间接修改，避免只检查显式数组赋值。
- 页面/文件/图片/地图结果 Hook 由请求拥有，销毁/取消/异常撤销；批量失败回滚，启动和后台清理按同一对象锁串行化，旧代次回调失效。
- PAUSE/DESTROY、标签页销毁、输入栏解绑、红包页面销毁在 finally 清理；RESUME 等业务只在成功后执行。

Hook 使用 PASSTHROUGH，避免模块已接管外部副作用后由异常兜底再次执行宿主。可恢复的观测/UI 清理错误在模块本地处理，宿主 proceed 不放进重试 catch。

Native 来源固定为框架 `getModuleApplicationInfo().sourceDir`。删除 ClassLoader 字符串/字段猜测、安装目录扫描、任意历史 so 和不确定 loadLibrary 回退；保留宿主缓存隔离、CRC、跨进程锁、原子提取、损坏恢复及 DexKit 按需加载/空闲释放。

## 回归

测试使用正式 API 102.0.0 AAR 的真实声明和最小调用边界夹具，执行真实生产类、interceptor 或逐字提取的生产 interceptor/辅助方法；已删除旧框架 stub 与回调字符串壳。JVM 夹具不等同于真实 ART 注入。

已通过的既有回归：HookRegistry、performance_round1、correctness_closure（21 场景，含群目录空结果及群基线）、message_block（56 断言）、redpacket_silent（329 检查/25 场景，UI 17 检查/4 场景）、redpacket_logging（32）、conversation_tabs_lifecycle（24 场景/161 检查）、群首页、官方游标（43）、父群运行时（71）、父群策略（160）、标签（18）、NativeLoadCache、启动版本（22）、原生功能、联系人（3 组）、悬浮图标（9）、语音（71）、消息解析（153+5）、消息类型（96）、红包解析（32）、详情布局（7）及预绘制（14）。

新增直接生产拦截器回归：`run_api102_interceptor_tests.cjs`、`run_database_chain_tests.cjs`、`run_picker_chain_tests.cjs`、`run_lifecycle_chain_tests.cjs`、`run_settings_bridge_tests.cjs`。覆盖参数复制、返回替换、短路、构造器对象身份、优先级、调用一次/零次、宿主异常身份、嵌套清理、取消、部分失败回滚、过期代次和 Chain 失效。`run_native_extraction_tests.cjs` 执行生产提取代码，覆盖同长度损坏 CRC、重提取和失败时原子/临时文件清理。

## 构建与 APK

- 冻结源码执行 `./gradlew --no-daemon :app:assembleDebug :app:assembleRelease :app:lint :app:lintRelease`：成功。Debug 和 Release Lint 均为 **0 errors, 653 warnings, 50 hints**，没有关闭检查或降低门槛。首次并行编辑期间分析器内部异常已由稳定源码重试解决；随后发现并修正了头像 Hook 缩进错误。
- 全量 **29/29 回归 runner 成功**，并额外复跑过格式调整后的发送/头像/投影 interceptor。设置桥接整所有者测试覆盖导入/导出终止失败、生命周期注册/注销失败、部分安装回滚、撤销失败重试、重入和旧代次回调。
- 实际解析 Debug/Release 编译与运行依赖图：编译只有正式 `io.github.libxposed:api:102.0.0`，运行依赖没有框架 API、旧 API 或 helper。
- 生产代码及测试旧框架类型/调用引用清零，旧入口和旧 stub 已删除。业务 legacy 命名不作框架兼容处理。
- `scripts/verify_api102_apk.py` 实际检查 Debug（20 个 DEX）与 R8 Release（1 个 DEX）：入口资源完整，旧入口/metadata 不存在，DEX 不含旧框架引用或被打包的 libxposed API 类。Release 清单入口是 `ne2`，与 mapping 和真实 public 无参 XposedModule 类一致；Debug 为 `h.Hchat.ModuleEntry`。
- 正式签名 Release：`dist/Hchat-release-signed.apk`，SHA-256 `bc446fa9a358cfdd8bf7b36fe8ddfc982596416b5673536aa3ad51aa750c0998`。
- 证书 SHA-256 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`，与手机原模块一致。使用既有 MUCHEN.jks 和标注凭据，无替代签名。
- 最终生产源码指纹（源文件路径及内容 SHA-256）`f8357bde4b420c2805ef429dfed64142a5e0cf5c4d42ca2ae38f1a7eaaba510f`；验收期间冻结，构建完成后复核一致。
- 归档于 dist 的当前 mapping、Lint、回归和 API 验证证据不提交 Git。没有触发发布工作流或声称远端 CI 成功。

## 真机

连接设备为 Redmi K50 Ultra，Android 15/API 35，ARM64。从实际安装的框架 DEX 核对到 `LIB_API=102`、`getApiVersion()` 返回 102、无参 XposedModule、新生命周期和 HookHandle.replaceHook；管理器界面也显示 API 102。并非只凭 LSPosed 的版本名推断。

仅 `com.tencent.mm` 已安装，`com.tencent.mm.wx` 未安装，其所有真机行为为 NOT_VERIFIED。不卸载或清除任何微信数据。


正式签名最终 APK 已使用 `adb install -r` 覆盖安装成功，已安装 base.apk 的 SHA-256 与交付 APK 完全一致。冷启动后的主进程 PID 为 29935，同一进程完成开关和发送验证；最后一轮新入口日志时间为 13:13，主进程、`:push`、`:appbrand0`、`:appbrand1` 均明确记录 `api=102`。没有恢复旧入口路径。

| 真机项目 | 结果与范围 |
| --- | --- |
| 冷启动、新入口与宿主 UI | PASS，正式签名 Release，模块设置页与原生微信均正常打开；无此次新运行产生的模块异常日志 |
| 设置页与返回键 | PASS，多级页面进退、键盘返回、保存和重新进入正常 |
| 普通开关即时生效 | PASS，同一进程保存文本格式前缀后立即生效，恢复关闭后立即恢复普通发送 |
| 发送装饰与普通发送 | PASS，文件传输助手单次点击各产生一条消息，装饰前缀及关闭后文本符合设置，输入清空；不声称额外做过服务器侧数据库核验 |
| 模块接管发送 | PASS，启用文本转语音后两次 `#tts` 分别提示会话模式开启/关闭，均清空输入且未生成普通 `#tts` 消息；未请求实际语音合成 |
| 聊天行 | PASS，现有与新测试消息重新绑定可正常显示；具体自定义气泡素材/各类消息详情布局未逐个做真机样本验收 |
| 文件选择回调 | PASS，系统 DocumentsUI 选择本次无效纯文本样本后，Toast Queue 观察到单条“导入失败或文件无配置”；格式校验在任何 preferences editor 前返回，无配置改写。再次打开并取消也正常返回 |
| 小程序进程 | PASS 新入口加载与进程分流的运行证据；具体小程序功能入口端到端 NOT_VERIFIED，未提供确定的功能样本 |
| 第二微信包 | NOT_VERIFIED，`com.tencent.mm.wx` 未安装 |
| 首次协议同意/取消退出 | NOT_VERIFIED 真机交互，保留现有接受状态，没有重置用户协议配置；迁移未改变协议业务实现与接受后运行时重入路径 |
| 其它 Picker/业务功能 | 地图、各类图库以及红包转账等真实业务未逐项端到端验收；对应已列出的生产回归与全量构建通过，不能扩展为全业务真机通过 |

发送文本格式已恢复为原来的关闭状态、`${sendText}` 和 `HH:mm:ss`；文本转语音发送/播放开关均恢复关闭，没有改动转换引擎等其它选项。手机上仅向文件传输助手发送两条本次测试文本，没有对外联系人发送消息。无效配置样本与 UI dump 已删除。未卸载微信、未清除微信数据、未替换正式签名。

## 清理与提交边界

保留本次唯一正式 Release APK、压缩 R8 mapping、Lint、回归和验证摘要；清理重复 APK、中间产物、旧 dist 证据、已知测试临时目录、本次脚本和截图以及已确认的历史调试截图。保留签名材料、用户数据、项目依赖和共享 Gradle/Android SDK 缓存。提交前检查源码冻结指纹、`git diff --check`、暂存差异与 main 最新状态，提交并推送 main；最终提交 SHA 由交付消息和 dist 验证摘要记录。
