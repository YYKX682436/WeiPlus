# WeiPlus 微信 8.0.80 C-2A R2：#52 修复与正式 APK

日期：2026-10-29

基线：`fe14e20224224f1e0825b29b2c2c7f9755ba97ab`

范围：只修复 #52 首页文字颜色的生产安装/重试控制流，更新验收矩阵并构建正式签名 APK。未进入 C-2B。

## 1. 结论

- 已依据 R1 的失败现象和官方 8.0.80 DEX 结构修正 #52 的安装状态判断、现代首页路径重试和 HookRegistry 句柄管理。
- R2 没有在设备安装 APK、操作微信页面或切换配置。首页文字颜色仍为 **NOT_VERIFIED**，须由用户在新 APK 上确认实际视觉效果；#18 同样保持 **NOT_VERIFIED**。
- #02 更正为 **NOT_VERIFIED**：API 102 模块入口加载不构成 AddMsg、数据库 wrapper、文本请求或 SNS helper 任一公共 API 子功能成功证据。
- Debug 单测 97/97 通过；仓库 `scripts/run_*.cjs` 回归脚本 30/30 通过；Debug/Release Lint 与正式 Release/R8 均成功。
- 正式 APK：`dist/Hchat-release-signed.apk`，SHA-256：`8b96e3f41b70f167dd03f616c6dc855bf627680bbefa46980e43ac030b0086f7`。`apksigner verify` 通过，证书 SHA-256 为指定的 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`。

## 2. 缺陷复现与根因

R1 官方 8.0.80 页面中，设置标题蓝色、摘要洋红色，但首页会话行未呈现对应颜色。生产链路中 `HomeTextColorRuntime.install()` 原先把首页会话、联系人、设置页、布局创建和两类 Recycler 绑定 Hook 的安装结果合并成一个全成功条件；首页关键路径已经可用时，任意可选路径缺失仍会导致安装任务不完成。现代首页定位首次失败还会设置 `modernConversationUnavailable`，之后不再重试。安装状态保存在该 runtime 的永久布尔值中，不能可靠反映 HookRegistry 句柄是否仍然有效。

新增回归直接调用生产共享的 locator 结果判定及安装完成协调器，先复现空候选/宿主 ClassLoader 暂不可解析时不应终结安装，再验证后续候选可恢复；并验证现代路径安装不受无关可选路径影响、8.0.76 结构无现代路径时仅由 legacy 会话路径决定完成，以及重复/失效句柄遵循 HookRegistry `hookOnce` 与有效句柄状态。

## 3. 8.0.80 结构证据与修复

使用 DexSQL 对官方 `com.tencent.mm` 8.0.80（versionCode 3220）APK 做只读结构分析。证据文件为此前已存在的官方 APK，SHA-256：`c35a05d5c13bd43cf1f7ff42c13fab070d21509ab2e233596f776d1afcae52f5`。

- 首页 BaseAdapter `Lcp5/a1;` 的 `getView(ILandroid/view/View;Landroid/view/ViewGroup;)Landroid/view/View;` 调用 `Lcp5/j0;->b(Lcp5/s;Landroid/view/View;)V` 初始化 holder，并调用 `Lcp5/j0;->a(ILcp5/s;Lcp5/a0;Lcom/tencent/mm/storage/l4;)V` 绑定会话行。
- `j0.a` 含 `[getView] position=`、`handleShowTipCnt` 锚点；`j0.b` 通过资源 ID `0x7f0a4d3b`（`kbq`）与 `0x7f0a3d94`（`ht5`）取得标题和摘要 `NoMeasuredTextView`，时间组件 `0x7f0a6dac`（`otg`）为 TextView。
- `Lcom/tencent/mm/ui/conversation/t2;->getView` 是 legacy 路径，返回类型与现代 holder/bind 签名不符，不会误作为现代绑定 Hook。

修复只使用结构签名和现有内容锚点，没有新增硬编码混淆类名或扩大到无关页面：

- 现代 holder 与 bind 成对安装；两者有效后才视为现代路径完成。legacy 会话行单独记录；仅在结构确认没有现代路径时才由 legacy 成功完成安装。
- DexKit 空结果、候选暂时无法由宿主 ClassLoader 解析、目标签名暂不完整，以及 Hook 安装失败均保留有界重试；不再用永久失败 latch 阻止后续定位。
- 联系人、设置页、布局创建和 Recycler 绑定作为独立可选路径安装，不再阻断首页会话行完成判断。
- Hook 使用稳定 descriptor key 调用 `HookRegistry.hookOnce`，并检查有效句柄；安装代次撤销后重新定位/安装可恢复，避免重复回调。
- 安装安排在 DexKit WARMUP 阶段，销毁时取消待执行任务。渲染前恢复旧行颜色的路径保持不变，以支持关闭和滚动复用恢复。

## 4. 回归与构建

| 检查 | 结果 |
|---|---|
| 生产安装控制流回归：先失败后可用、8.0.80 现代路径、8.0.76 legacy 路径、独立可选路径、Hook 句柄生命周期 | **PASS**；新生产协调器 JUnit 2 项通过，静态生产路径防回归通过；先前旧 latch 版本触发回归失败，修复版本通过。仓库 HookRegistry 回归也覆盖 hookOnce 去重、失效句柄和新代次恢复 |
| Debug 单测 | **PASS**；97 项，失败 0、错误 0、跳过 0；其中新增协调器测试 2 项 |
| 全量仓库 `scripts/run_*.cjs` 回归脚本 | **PASS**；30/30 |
| Debug Lint / Release Lint | **PASS**；Gradle 两个 Lint 任务成功 |
| 正式签名 Release / R8 | **PASS**；`minifyReleaseWithR8`、`assembleRelease` 成功 |
| APK 签名校验与摘要 | **PASS**；见下方 APK 交付信息 |

签名使用既有 `/Users/muchen/Downloads/Android App正式签名密钥/MUCHEN.jks` 和本机 `签名密钥密码.env`，Gradle 进程通过 `HCAT_*` 环境参数接收凭据；中文前缀不是密码，凭据值没有输出、提交或写入仓库。签名校验确认 v2 scheme 有效，签发证书 SHA-256 与目标一致。

- 最终正式 APK：`dist/Hchat-release-signed.apk`
- SHA-256：`8b96e3f41b70f167dd03f616c6dc855bf627680bbefa46980e43ac030b0086f7`
- 签名证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`
- `aapt dump badging` 确认包名 `h.Hchat`；本轮未执行设备安装。

## 5. 验收边界

- **#52 首页文字颜色：NOT_VERIFIED**。R1 的真实页面失败是本次修复依据；DexSQL 只提供结构证据，生产回归只覆盖安装决策。R2 正式 APK 未安装，未观察新包 locator、Hook 命中、首页渲染、滚动复用或关闭恢复。
- **#18 资料页显示 ID：NOT_VERIFIED**。本轮未运行微信；等待用户在新 APK 上进入正确的好友或群资料页验收。
- **#02 微信公共 API：NOT_VERIFIED**。API 102 入口加载不代表公共 API 子功能成功；本轮没有真实消息、数据库变更、文本发送或朋友圈业务事件。
- 未发送消息、修改微信数据库、联系人资料或其他业务数据。所有 R1 视觉设置及 FastKV 快照属于历史验收；R2 未切换设置。
- 不卸载应用、不清除数据；未进行设备安装或微信 UI 操作。

## 6. 提交与工作区

- #52 生产修复、87 项矩阵、R2 报告及回归代码一并提交并推送至 `main`；最终 commit SHA 在本轮交付信息中提供。
- Release APK 单独保留在 `dist/` 供用户验收，不提交 APK、签名材料或测试隐私数据。
- 本轮未安装 APK 或操作微信；提交后清理构建中间产物和临时回归文件，保留正式 APK。
