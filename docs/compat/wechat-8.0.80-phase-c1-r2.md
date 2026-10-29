# WeiPlus C-1 R2：保留 LVBuffer 消息详情来源

基线：`59730a44b3c4a432c3b87b2b6730a0cdd57ed647`

日期：2026-10-29

## 回归与修复

对照 R1 前的 `HchatExtraHooker.readMessageSourceFromLvBuffer()`：结构化 LVBuffer 的来源字段按长度读取后直接解码，即使其内容为 `.msgsource.atuserlist=notify@all` 也会传给详情中的 `atUserList()`。R1 的共享 `extract()` 只返回包含完整 `<msgsource>…</msgsource>` 标签的文本，因此上述合法非 XML 来源变为空。用户已独立确认该基线回归；本轮以源码对照及生产详情方法回归记录其行为，并未另行执行基线红灯测试。

共享 `WeChatLvBufferMsgSource` 现在提供两个入口，共用结构化字段的长度校验与严格 UTF-8 解码：

- `extractXml()` 用于数据库事件，结构化输入只在来源字段中寻找完整的 msgsource 标签；非 XML 来源返回空值。非结构化 XML 的有界提取继续保留。
- `extractRawSource()` 用于消息详情，返回结构化来源字段原文，包括非 XML atuserlist 表达式；非结构化输入沿用 XML 提取能力。

输入上限为 64 KiB，直接 XML 扫描上限为 16 KiB，结构化字符串长度及来源文本上限为 3072 字节。错误长度、截断、非法 UTF-8 和超限数据安全返回空值。结构化字段后允许其他字段，再由最终 `}` 结束，保持旧读取器的记录形状兼容性。解析不改写输入数组、微信 ContentValues 或 SQL，也不增加查询。

## 验证

- 消息详情回归通过反射调用真实 `HchatExtraHooker.readMessageSourceFromLvBuffer()` 和 `atUserList()`：结构化 `.msgsource.atuserlist=notify@all` 返回 `notify@all`，原始字节不变。
- 数据库生产 Hook 回归覆盖结构化 XML、非 XML 来源拒绝、已有 `msgSource` 优先，以及 ContentValues 与字节不变。
- 在结构化首字符串放入诱导 XML，来源字段放入另一段 XML；解析器与数据库事件均返回来源字段的 XML，证明测试走过结构化字段读取而非全缓冲区 XML 搜索。
- 无效长度、截断、非法 UTF-8、超限以及原有嵌套写入去重、异常传播、Hook 撤销和重装回归通过。
- 全量 `:app:testDebugUnitTest`：89 项通过，0 失败、0 错误、0 跳过；包含消息详情及数据库生产 Hook 回归。
- `:app:lintDebug`、`:app:lintRelease`、`:app:assembleRelease`：通过；Release 执行 R8。Lint 保留项目已有的三个 `firstOrNull()` 多候选警告。
- 继续使用原有 `MUCHEN.jks` 与本地凭据，未输出或提交凭据。`apksigner verify` 通过，证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`。
- 最终 APK `dist/Hchat-release-signed.apk` SHA-256：`3ab0d185c3f9d3ed2331a46de4dd85cd4e67a05bf137b86dc92b14c3539a66f2`。

## 未验证边界

本轮未安装 APK 或执行真机启动；设备端消息详情及自然消息数据库事件为 **NOT_VERIFIED**。结构化读取覆盖已有记录布局，未知二进制变体和超过限制的来源返回空值。未进入 87 项功能验收。
