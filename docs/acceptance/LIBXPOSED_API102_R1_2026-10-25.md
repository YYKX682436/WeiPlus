# API 102 迁移 R1 收尾（2026-10-25）

审查与执行基线：`2862b6e0adbf57f35fb7d830164febc4536faa83`。执行前工作区干净，当前分支 main，fetch 后本地与 origin/main 一致。保留迁移主体与官方 `compileOnly("io.github.libxposed:api:102.0.0")`，最低/目标框架 API 都为 102，不增加旧入口或兼容层。

## 显示异常穿透

根因有两层：整数解析成功不代表合法 Unicode 标量；`Character.toChars(1114112)` 位于原捕获范围外。生产字色 interceptor 在宿主成功绑定后调用 `applyTextColor`，普通 `run after@` 只是作用域，安装外层的 runCatching 也无法保护稍后执行的回调。因此模块绘制错误在 PASSTHROUGH 下替换了宿主成功结果。

生产解析现在使用 `toIntOrNull`，只转换有效且不属于代理区的码点。越界、整数溢出及孤立代理值保留数字引用字面文本；中文数字引用、表情和补充字符正常转换，原有实体解码轮次不变。

新增 `HookErrorBoundary` 只接收可选模块工作，不接收 Chain 或重新调用宿主的兜底逻辑。字色后处理成功与失败都返回宿主原值；宿主 `proceed()` 位于边界外，异常保持原对象传播。日志接口异常也不会替换已经完成的宿主结果。

同类复核补齐了已确认缺少运行时隔离的显示/观察点：气泡恢复和绘制、时间显示、首页字色、隐藏头像、群员头衔、实名尾字与查询结果观察、圆角头像参数转换、会话标签 layout/resume、转发记录类型标签、输入提示、隐藏聊天菜单、语音预览菜单，以及 CDN 管理器构造完成后的观察。已有局部保护的资料 ID、生命周期销毁等路径保留；发送、数据库和请求接管逻辑没有机械套入显示边界。

气泡仍按恢复旧样式 → 微信绑定一次 → 成功且仍启用时绘制的顺序执行；时间及消息详情的嵌套栈保留 finally 退栈。显示模块异常不进入第二次 proceed，宿主失败也不会进入成功后的绘制。

### 异常策略

`XposedRuntime` 的 HookBuilder 与 `module.prop` 明确保留 PASSTHROUGH。依据[官方 API 102 异常策略](https://github.com/libxposed/api/blob/102.0.0/api/src/main/java/io/github/libxposed/api/XposedInterface.java)，PROTECTIVE 会在模块尚未 proceed 时尝试继续调用链；这不适合作为已经发送或产生副作用后的通用兜底。这里用局部边界保护可选模块显示/观察工作，将宿主调用留在边界外；发送接管仍显式决定返回值和零次/一次宿主调用，没有全局模式替换。

## 批量回滚所有权

原 hookBatch 只有成功返回时才把集合交给调用者。后续注册失败且前缀 unhook 也失败时，原集合随异常丢失；错误的观察回调仍存活，重装形成重复处理。设置返回键还会在撤销失败后无条件清空页面集合。

现在每次安装立即取得受管理的官方 HookHandle。批次对象在下一次注册前持有句柄，整个批次完成前业务门保持关闭，失败时整批失效。撤销先清空业务回调，再调用框架 unhook；框架撤销失败时，句柄进入进程内失败集合，原拥有者也只在成功后删除自己的句柄。残留仅继续一次宿主调用，无法处理请求或叠加观察；清空回调同时释放页面/请求捕获。

失败集合只保存待撤销的惰性句柄，没有永久安装临时 Hook，也没有后台轮询。注册表销毁、明确重新安装、页面返回键清理、设置结果拥有者 clear，以及 Picker/群文件请求清理会显式重试。框架恢复撤销能力后，两层集合均可清空。官方 replaceHook 的旧调用快照与返回新句柄语义也有回归。

## 失败复现与修复证据

在未修改生产代码的基线先执行初版针对性回归，复现解析/后处理异常、重装双回调及页面句柄丢失。最终测试还支持 `WEIPLUS_R1_BASELINE=2862b6e0adbf57f35fb7d830164febc4536faa83`，通过 git show 将该提交真实生产源码放入独立临时目录；不重置工作区，也不注入另一份解析修复。

```sh
WEIPLUS_R1_BASELINE=2862b6e0adbf57f35fb7d830164febc4536faa83 \
  node scripts/run_api102_r1_tests.cjs "$KOTLIN_LIB_DIR"
node scripts/run_api102_r1_tests.cjs "$KOTLIN_LIB_DIR"
```

- 使用正式 API AAR 声明、现有最小 Chain/注册故障夹具与真实生产 interceptor；解析、候选文本匹配、WeChatMessage 分类/XML 读取、页面返回键和完整 CDN 安装方法均关联生产代码。View/缓存边界提供夹具，未重写 Hook 框架。
- 基线残留 `framework active=1`、调用者得到 0 个句柄；重装 `observer callbacks=2, host calls=1`。修复后 `observer callbacks=1, host calls=1`，显式销毁后活句柄与失败集合清零。
- 字色实际备用路径覆盖引用 type 57、笔记 type 53，首选正文 View 缺失，含 `&#x110000;`、`&#1114112;` 与合法表情。非法数字引用保留原文；真实 fallback 匹配并绘制一次。
- 注入模块绘制失败时保留宿主返回值对象、宿主调用一次；注入宿主异常时保留异常身份，跳过后处理并退栈。气泡恢复/绘制故障同样覆盖。
- 组合故障覆盖第二个注册失败 + 第一个撤销失败；真实 WeChatImageApi 分别覆盖构造器内部批次失败，以及构造器已交给 Registry、下载入口后续失败两种阶段。另验证真实构造后观察异常不覆盖宿主成功。
- 现有 HookRegistry、Picker、设置桥接回归增加组合故障，验证失败句柄仍被管理、重装残留无业务效果、取消/关闭后恢复撤销并清空。
- 现有发送 interceptor 验证模块接管时宿主零次，普通发送一次，宿主异常不导致再次发送；数据库、消息详情、群目录/群基线和样式恢复回归保留原断言。

最终基线回放退出 1，19 项断言失败；修复版退出 0，25 个 R1 场景通过。全套 28 个 `run_*tests.cjs` runner 最终全部通过。首轮 correctness closure 夹具遗漏新增日志 TAG 编译失败，补齐夹具依赖后单独完整重跑通过，初次失败和重跑日志均保留，没有删业务断言。

## 构建与 APK

执行 `./gradlew --no-daemon :app:assembleDebug :app:assembleRelease :app:lint :app:lintRelease`，BUILD SUCCESSFUL（6m17s）；Debug、正式签名 R8 Release、lintDebug、lintRelease 均通过，两个 Lint 均为 0 errors / 648 warnings / 50 hints，未新增抑制或关闭检查。正式签名使用现有 MUCHEN.jks 与原凭据字段，未生成替代签名。最终 Release SHA-256 为 `f1b9e01ef4b91931a999413e79a076687fb9c5bdfa66300b232c65891ac2f31c`，证书 SHA-256 为 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`。

实际运行 `scripts/verify_api102_apk.py` 检查 Debug（20 DEX）与 R8 Release（1 DEX），均通过。Release `java_init.list` 为 `se2`，与 mapping 和 DEX 中真实 public 无参 XposedModule 类一致；现代注册资源齐全，最低/目标 102，静态 scope 仅两个微信包。没有旧 API 引用/注册，未打包框架 API 类。生产源码与测试夹具旧接口搜索也为空；验证脚本中用于拒绝旧 API 的检测字符串保留。

## 真机验证

Redmi K50 Ultra / Android 15，实际框架 DEX 的 LIB_API 为 102，getApiVersion 实现返回 102。使用 `adb install -r` 覆盖正式 Release 成功，已安装 base.apk 哈希与本次 Release 完全一致。没有卸载或清除微信数据。

冷启动后主进程 PID 13573，新入口日志 13:56:10 明确 `module_loaded api=102`；push、appbrand0、appbrand1 也有新入口记录。以下交互全部在最终 APK 上完成：

| 项目 | 实际结果 |
| --- | --- |
| 设置进退 | PASS，打开设置/美化/消息文本颜色，保存与多级返回正常 |
| 即时字色开关 | PASS，同一 PID 开启后把右侧浅色设为红色，旧消息重新绑定即显示红色 |
| 普通消息 | PASS，仅向文件传输助手发送一条 `api102_r1_&#x110000;_&#1114112;`，单条显示红色，非法引用保留字面内容，输入清空 |
| 引用消息 | PASS，原生长按引用上述消息，回复 `r1_quote_&#x110000;_&#1114112;`，单条显示红色、引用块正常，输入和引用状态清空，PID 保持不变 |
| 故障注入及强制备用 View 路径 | JVM 生产 interceptor/真实 fallback 回归 PASS；真机未注入模块绘制或框架撤销故障，未强制移除首选正文 View，NOT_VERIFIED 真机故障注入 |
| 笔记标题 | 生产笔记 type 53 fallback 回归 PASS；没有创建真机笔记样本，NOT_VERIFIED 真机笔记交互 |
| 第二微信包 | `com.tencent.mm.wx` 未安装，NOT_VERIFIED |
| 发送接管/其它 Picker/小程序业务 | 本轮生产回归通过，新入口进程加载有证据；未重复此前整轮迁移的各项端到端业务，NOT_VERIFIED 本轮真机场景 |

本轮没有触发远端 CI，也不把本地构建或 JVM 注入夹具当作全部真实 ART/业务验收。


字色原配置已恢复：启用关闭、右侧浅色 `#FF000000`，其余颜色未改变；保存后同一进程重新进入聊天，原生字色恢复。只向文件传输助手新增两条本轮样本，没有对外联系人发送。

## 清理与交付

归档唯一正式 Release APK、压缩 mapping、基线失败与修复后回归记录、构建/Lint/签名/入口检查和真机摘要于 dist（不提交 Git）。清理前轮被替换的 dist 证据、重复 Debug APK、中间编译目录、项目 .gradle/.kotlin 缓存、已知回归临时目录及本轮截图/脚本/UI dump；保留用户数据、签名文件、SDK 与共享依赖缓存。构建后的生产源码指纹 `308b1c04da8ac341b63710eac8c65a5a7b74d045d8e81eb8594628302498c95b` 在最终审查时未变化。

提交前再次 fetch，远端 main 仍为审查基线；最终提交及远端一致性见交付消息与 dist 验证摘要。
