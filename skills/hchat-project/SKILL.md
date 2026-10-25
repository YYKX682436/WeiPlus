# WeiPlus / Hchat 项目工作指引

## 项目边界

- 本仓库只维护 `main`。不要创建或同步已删除的分支。
- LSPosed 目标包名仅为 `com.tencent.mm` 和 `com.tencent.mm.wx`。安装包名和微信内部类名分开处理：微信内部类仍可能是 `com.tencent.mm.*`。
- 项目包含原生微信功能、原生 AI 自动回复与 MCP 桥接、定时任务和设置界面；维护时要保留这些能力。
- 真机操作前先用 `agent-device` 或 UI Automator 读取界面结构；优先文本、资源 ID 和元素定位，截图只用于视觉验收或结构异常。禁止卸载微信或清除微信数据。

## 架构检查

- 只支持官方 libxposed API `102.0.0`，`minApiVersion=targetApiVersion=102`。入口为无参 `XposedModule`，只使用 `META-INF/xposed` 注册文件；禁止恢复旧 API、双入口、旧参数仿制层或 API 100/101 兼容路径。
- `onModuleLoaded` 后才能使用框架 API，`onPackageReady` 安装 Application/Tinker 早期 Hook；attach 后重新读取宿主 ClassLoader。调用链使用官方 Hooker/Chain，修改参数复制数组，正常 proceed 一次，短路零次，finally 清理，宿主异常原样传播。Chain 不跨线程或回调生命周期。
- 框架绕过调用只用官方 ORIGIN invoker；反射复用 KavaReflector 或标准反射，不引入 API 100 helper。框架日志由 HLog 分级记录。Native 模块身份只取框架 ApplicationInfo.sourceDir。

- 从 `ModuleEntry` 跟踪包名与进程分流，再看 `FeatureRegistry`、`FeatureManager`、`DexInstallScheduler`、`HookRegistry` 和具体回调。
- 普通开关应安装轻量 Hook 并在回调快速检查当前配置。昂贵 DexKit、数据库、网络和资源初始化应按需执行。
- 仅早期必须生效的功能声明重启要求或 `ENABLED_ONLY`。配置读取异常时有副作用的行为应关闭。
- 共享 Hook 要由一个原生拥有者安装，并让实际功能单独订阅；销毁时取消订阅、异步任务、监听器和视图回调。保留 Hook 注册代次防护。
- 从当前宿主 Context 取得配置、缓存、资源和通知身份；不可把一套微信包的路径或缓存交给另一套。

## 常用构建与检查

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lint
```

Release 已启用 R8 和资源压缩。必须检查混淆后 java_init.list 对应真实入口类，API 类未打包、旧注册和旧类型清零。确认反射、Xposed、JNI 和运行时资源引用后再调整 keep 规则。修改前查看 Git 状态，保留已有未提交文件；只清理可确认无用的项目产物，包含历史遗留；保留签名材料和用户数据。

## 回归

- `scripts/lib/api102.cjs` 从正式 API 102 AAR 提取官方声明，最小测试 Chain 执行真实生产拦截器；不允许旧 API stub 或 before/after 测试壳制造通过。
- 运行受影响回归与现有脚本，完成 Debug、正式签名 Release 与 Lint。真机先核实实际框架 API，再验证可用宿主；框架或环境不满足时记录 NOT_VERIFIED，不恢复旧路径。

## 交付标准

- 按实际运行结果报告构建、测试、CI 与真机状态；没有证据的项目写 `NOT_VERIFIED`。
- 提交前检查 `git diff --check`、`git status` 和最终差异；未经用户要求不要推送到其他仓库或操作线上业务。
