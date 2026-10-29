# WeiPlus 微信 8.0.80 C-2A R3：首页文字颜色安装状态收口

日期：2026-10-29

基线：`6bc7b26709c1bf0e45dedc836b13da8a727006aa`

范围：只处理 #52 的零个现代候选判定与主会话/其他页面 Hook 调度独立性。未重做 R2 的现代 Holder/Bind 定位和有效 Hook 句柄方案；未安装 APK、未操作微信、未进行真机 UI 验收；不进入 C-2B。

## 修复

### 现代路径零候选

`HomeTextColorModernConversationResolver` 现在显式接收 DexKit 查询是否成功、WARMUP 是否已就绪以及 legacy 会话 Hook 是否确实有效：

- DexKit 异常，无论候选数如何，都返回 `RETRY`。
- 已发现候选但解析到宿主 ClassLoader 的方法数不足时返回 `RETRY`。
- 查询成功且 WARMUP 已就绪，零候选只有在 legacy 会话 Hook 有效时才判为 `NOT_APPLICABLE`，允许主会话安装完成；否则保留 `RETRY`。
- 查询成功、候选非零且均已解析，但没有现代 bind 结构时，WARMUP 后判为 `NOT_APPLICABLE`。候选不唯一、Holder 不唯一仍可通过已有有界调度重试。
- 未使用 versionCode 或版本白名单。

### 页面 Hook 独立调度

- 主会话路径继续使用 `home_text_color` 调度键；联系人、Preference、布局创建和 Recycler 路径使用独立的 `home_text_color:optional_pages` 调度键，均复用已有 `DexInstallScheduler` WARMUP 阶段和有限重试。
- 主会话完成不再结束其他页面的补装任务。DexReady 生命周期事件会分别重新调度主路径和可选页面路径。
- 可选页面任务完成前再次运行时使用 HookRegistry 的 `hookOnce` 与现有有效句柄检查，避免重复注册；待所有可选 Hook 有效后停止该任务。
- 销毁时分别取消两个调度键，页面 coordinator 同时进入销毁状态；DexInstallScheduler 的安装代次租约拒绝旧代次任务继续安装。

## 回归

新增/扩展 `HomeTextColorInstallCoordinatorTest` 共 4 项：

- 查询异常和候选已发现但 ClassLoader 未解析仍重试。
- 现代路径成功定位、legacy-only 结构路径判定。
- DexKit 在 WARMUP 后成功返回 0 个候选且 legacy 会话 Hook 有效时，主会话安装完成。
- 可选页面首次失败后可恢复；成功后重复安装不增加尝试；销毁后不再补装。

结果：

| 检查 | 结果 |
|---|---|
| 定向 HomeTextColor 单测 | **PASS**，4/4 |
| 全量 Debug 单测 | **PASS**，99 项，失败 0、错误 0、跳过 0 |
| 全仓库 `scripts/run_*.cjs` 回归 | **PASS**，30/30 |
| Debug Lint / Release Lint | **PASS** |
| 正式 Release / R8 | **PASS**，`assembleRelease` 成功 |
| APK 签名验证 | **PASS**，APK Signature Scheme v2 有效 |

## 正式 APK

- 文件：`dist/Hchat-release-signed.apk`
- SHA-256：`4afb2c07fd2210b5c091239bb05f9b598f0d29fd05e16a1f0f9ab5214a9573fd`
- 签名证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`
- 使用原有 `MUCHEN.jks` 和本地凭据。中文前缀不作为密码；凭据通过 Gradle 进程环境传递，未输出或提交。

## 验收边界

- #52 在矩阵中继续为 **NOT_VERIFIED**。本轮只有源码控制流和测试证据，没有 R3 APK 的设备 locator、Hook 命中、颜色呈现、滚动复用或关闭恢复证据；等待用户自行安装验收。
- #18 同样等待用户在新 APK 上验收。
- 本轮没有 ADB、微信 UI、LSPosed scope、设置切换或业务数据操作。
- R3 修复、矩阵和本报告提交并推送至 `main`；最终 commit SHA 见交付信息。APK 保留于 `dist/`，不纳入 Git。
