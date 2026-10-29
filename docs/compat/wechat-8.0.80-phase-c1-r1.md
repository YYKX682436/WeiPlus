# WeiPlus 阶段 C-1 R1：消息来源回归收口

基线：`a294bb13d0fd02d2eded620f8bdb68bb55c3a4b8`  
日期：2026-10-29

## 结论

| 验收 | 状态 |
|---|---|
| 真实生产 `WeChatDatabaseListenerApi` → `WeChatMessageChangeApi` 来源回归 | **PASS** |
| 写入前输入不变、成功写入后事件补全来源 | **PASS** |
| 直接 XML、结构化 LVBuffer、艾特列表与艾特所有人语义 | **PASS** |
| 错误 UTF-8、截断数据和超大 LVBuffer 安全处理 | **PASS** |
| 嵌套写入去重、异常传播、撤销/重新安装 | **PASS** |
| Debug 单测、Debug/Release Lint、正式签名 Release/R8 | **PASS** |
| 模块 APK 安全覆盖安装 | **PASS** |
| 微信无副作用冷启动观察 | **PASS**（仅启动官方 8.0.80 主界面） |
| 自然发生的真实微信消息数据库事件 | **NOT_VERIFIED** |
| 修改版 8.0.76 本轮启动观察 | **NOT_VERIFIED** |

没有发送消息、修改联系人、触碰数据库、支付或操作朋友圈。本轮到此结束，不进入 87 项功能验收。

## 根因与复现

C-1 前的 `WeChatDatabaseListenerApi.normalizeLegacyMsgSource()` 在 `chain.proceed()` 前检查空 `msgSource`，从 `lvbuffer` 的 UTF-8 XML 中提取 `<msgsource>`，并调用 `ContentValues.put("msgSource", source)`。C-1 移除这段写入前修改以保持微信传入数据不变，但 `WeChatMessageChangeApi.resolveMessage()` 仍只读取 `msgSource`，导致只存在于 `lvbuffer` 的来源没有进入事件快照。

本轮先加了经过真实生产 Hook 回调的回归：由 `WeChatDatabaseListenerApi` 安装 wrapper/WCDB Hook，再由 `WeChatMessageChangeApi` 订阅并接收成功的嵌套 insert。修复前运行新回归失败，失败于事件 `message.msgSource` 与预期 XML 不相等（空值）；这确认了 C-1 的事件来源回归，而不是单测直接调用解析器的假阳性。

## 修复

在 `WeChatMessageChangeApi` 只读构建消息快照时，先保留非空的 `ContentValues.msgSource`；只有它为空且 `lvbuffer` 值为 `byte[]` 时，才调用共享 `WeChatLvBufferMsgSource`。提取器复用消息详情原有 LVBuffer 布局知识，并供该调用处共用：支持字节数组中直接嵌入的 XML，也支持 LVBuffer 的长度字段、字符串字段、4 字节结构段和来源长度字段。只提取有完整起止标签且严格有效 UTF-8 的来源文本。

解析边界为：输入最多 64 KiB，直接扫描最多 16 KiB，单个来源最多 3072 字节。超限、长度越界、截断、缺失结束标签或非法 UTF-8 均返回空字符串；不会把整个 LVBuffer 转成字符串或复制大块输入。解析仅发生在消息表 insert 快照已完整、且 `msgSource` 为空的路径。没有更改 SQL、微信 `ContentValues`、原字节数组或持久化字段；没有增加查询或数据库事件派发。

## 回归验证

新增/扩展 `WeChatDatabaseListenerApiRegressionTest`，所有场景均经过生产数据库监听和消息变更 API：

- 只在带前后缀字节数组中包含 `<msgsource><atuserlist>…</atuserlist></msgsource>`；事件补全成功，原始 `ContentValues` 各字段、键集合和 `lvbuffer` 字节逐项不变。
- 结构化 LVBuffer 中含 `notify@all` 来源；事件保持艾特所有人分类和艾特用户列表语义。
- 已有非空 `msgSource` 优先于 LVBuffer 来源，不被覆盖。
- 截断输入、含非法 UTF-8 的来源及 1 MiB 数据均安全返回空值；超限数据在扫描前被拒绝。
- 原有真实 Hook 回归继续覆盖 wrapper 与嵌套 WCDB 写入仅派发一次、失败写入不派发、宿主异常原样传播、撤销 Hook 后不报告已安装，以及重新定位和安装不重复派发。

修复前回归按预期失败；实现后当前全部 Debug Unit Test 共 86 项通过，0 failures、0 errors、0 skipped。项目未定义 `testReleaseUnitTest` 任务，因此测试由现有 `:app:testDebugUnitTest` 执行。

## 构建与设备观察

- `:app:testDebugUnitTest`：**PASS**（86 项）
- `:app:lintDebug`、`:app:lintRelease`：**PASS**。项目自定义检查仍报告既有 `firstOrNull()` 多候选警告，位置为 `HchatExtraFeature.kt:1019`、`:1094` 和 `WeChatTabletFeature.kt:192`；没有阻断 Lint。
- `:app:assembleDebug`：**PASS**
- `:app:assembleRelease`：**PASS**，执行 R8 `minifyReleaseWithR8`，使用原有 `MUCHEN.jks` 和本地凭据文件。没有输出或提交凭据。
- `apksigner verify`：**PASS**，APK Signature Scheme v2 验证通过。
- 最终 APK：`dist/Hchat-release-signed.apk`
- 最终 APK SHA-256：`f20daf0bfbbdd86ba3f654eb5c7627ff992d54224ef6a571f8de3fbcf1f9b1d2`
- 证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`
- `adb install -r` 覆盖安装返回 `Success`；设备上安装的 `base.apk` SHA-256 与最终 APK 一致。
- 官方微信 8.0.80 主界面在最终 APK 安装后执行一次无业务交互冷启动，`am start -W` 报告 `TotalTime=1313 ms`；本轮清空后的 logcat 观察窗口未出现微信 `FATAL EXCEPTION` 或 ANR。没有观察到本轮新增的模块加载诊断行，所以本条仅验收启动与崩溃窗口，不据此断言 Hook 已在设备上加载或真实消息事件有效。
- 修改版 8.0.76 未启动；保留 LSPosed 原 scope 设置。没有自然消息写入事件，本轮数据库事件端到端仍为 **NOT_VERIFIED**。

## 边界

生产 API 回归验证了只读补全、消息来源分类和监听生命周期；真实微信消息写入事件没有自然发生或被观察到，故不能宣称设备端端到端事件 PASS。结构化解析只支持已确认的 LVBuffer 记录形状和有界 XML 来源字段；其他版本或未知二进制变体返回空值，未作臆测解析。
