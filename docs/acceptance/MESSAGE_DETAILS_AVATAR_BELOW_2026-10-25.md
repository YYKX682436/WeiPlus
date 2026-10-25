# 消息显示时间：头像下方验收记录

日期：2026-10-25
仓库：`YYKX682436/WeiPlus`
审查基线：`4b375eb897725784429592b19ba2fb100f1dc1eb`

## 结果摘要

已重做头像下方时间标签的绑定复用和行内测量，并在目标手机安装本轮正式签名 Release。默认时间在文件传输助手冷进入口可见；短行只增加标签所需高度，已有较高内容的行不再重复增加时间高度。

静态审查确认，基线实现存在以下问题：每次最外层 full/payload bind 都会删除标签；头像下方路径先将预留空间清零，再靠 pre-draw 补 padding/margin，并直接调用 `layout`；标签固定上下各 2dp padding 且开启 font padding；颜色和点击配置变化也请求聊天行刷新。这些是代码事实。实际症状与短行尺寸由本轮控制对照和设备测量验证；不把单独的代码推断当作设备结论。

## 修改范围

- `HchatExtraFeature.kt`：保留同一消息的 TextView 与布局参数；A→B 复用时更新当前 binding、详情点击数据并使旧代次任务失效。标签身份不再依赖默认 `${time}` 模板下为 0 的显示 `id`，而使用 talker、localId/serverId、类型和方向等身份字段；展示结果单独以模板、时间格式、时区、locale 和相对时间桶做失效。缺少 createTime 时显示空值，不以当前时间替代。
- 头像下方标签作为真实行 RelativeLayout 的直接子项，以 `WRAP_CONTENT` 和 `ALIGN_PARENT_TOP` 参加宿主原生测量。头像本身不重挂，不修改其父子结构或点击/长按行为。标签零固定上下 padding，关闭额外 font padding；不使用负 margin、裁字或手动 `layout`。只由本模块拥有且宿主未改写的头像分支 top margin 会被恢复。
- 详情点击只更新可点击状态，不请求几何刷新；点击时才读取完整消息详情。默认时间模板不读正文、XML、@信息或数据库。
- 配置更新合并处理：颜色只改颜色，点击只改交互；位置、字号、格式和间距才刷新测量。页面进入的补绑限定当前聊天，首次页面检测为有界一次任务；页面退出、禁用或销毁会取消旧任务和时钟监听。
- `MessageDetailsLayoutModel.kt` 与 `run_message_details_binding_tests.cjs` / `scripts/tests/message_details_binding/`：增加消息身份、绑定代次和短行/高行必要下边界回归。

## 实机与尺寸证据

- 设备：Redmi K50 Ultra（22081212C），Android 15 / API 35，ADB `feb70d35`。
- 目标宿主：已安装 `com.tencent.mm` 8.0.76 / versionCode 3140，base APK。`com.tencent.mm.wx` 未安装，本轮无法实测该包。
- 框架：LSPosed 2.1.0，libxposed API 102；模块 `h.Hchat` 1.0.0。正式 APK 证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`。
- 真机测量中，短消息行原生内容下边界约 160px，头像下沿为 159px；10sp 时间标签实测 120×41px，标签顶与头像下沿同为 159px，最终行高 200px（只增加 40px 一次）。
- 高消息行原高 412px，添加标签后仍为 412px；标签下沿 376px，原生内容仍决定行高。头像下方的间距设为 0 时，标签布局顶边紧接头像边界，不再叠加固定内边距。
- 绑定诊断覆盖 7 行、14 次 full/payload capture：标签创建 7 次、移除 0 次；重复绑定没有先隐藏、清空高度或重建；每行仅有一次必要的标签 LayoutParams 写入，无父 padding 写入或手动 `layout`。短行扩高只发生一次。诊断期间阅读锚点/焦点无变化；静置 12 秒的两次侧边像素采样一致，没有模块持续改几何。
- 长格式 `yyyy-MM-dd HH:mm:ss XXX` 与 24sp 实机检查中，时区后缀可见且未裁切。10sp 默认格式下向文件传输助手发送的短消息，时间完整显示在头像下方，无需手动补滑。最终 Release 安装后恢复原偏好，再冷进入聊天时可直接看到已有消息时间。
- 时间关闭时症状消失、开启且点击详情关闭时标签参与行测量的对照已在本轮设备检查中完成。点击详情开启时，前一候选安装包从时间标签打开详情；本轮最终包的点击坐标复验被 MIUI 系统界面接管，故**最终包的点击详情入口未验证**。代码回归覆盖开关只改变交互、不改变几何。
- 最终测试后将原始 FastKV `.kva` / `.kvb` 配置原样恢复（设备文件 SHA-256 均为 `2bc633e2e154dd4a773acab22f7ef4e8333297f61294615250b13f4aa1b02ff0`），并移除测试期间生成的 `.kvc`；没有卸载微信或清除微信数据。

## 自动回归与构建

以下命令均通过：

- `WEIPLUS_MESSAGE_DETAILS_BASELINE=4b375eb897725784429592b19ba2fb100f1dc1eb node scripts/run_message_details_binding_tests.cjs /tmp/wei-kotlin-test-libs`：新绑定与真实父布局边界回归通过，并确认旧生产实现会失败。
- `node scripts/run_message_details_layout_tests.cjs /tmp/wei-kotlin-test-libs`：7 项布局观察回归通过。
- `node scripts/run_message_details_predraw_tests.cjs /tmp/wei-kotlin-test-libs`：队列、代次、attach/detach 生命周期回归通过。
- `node scripts/run_message_type_labels_tests.cjs`：96 项断言通过。
- `node scripts/run_performance_round1_tests.cjs /tmp/wei-kotlin-test-libs`、`node scripts/run_native_regression_tests.cjs /tmp/wei-kotlin-test-libs`、`node scripts/run_api102_r1_tests.cjs /tmp/wei-kotlin-test-libs`：通过。
- `./gradlew --no-daemon :app:assembleDebug :app:assembleRelease :app:lint`：成功。Lint 有一条未影响构建的现存警告，位于 `WeChatTabletFeature.kt:200`。
- APK 检查：`h.Hchat` / versionCode 1 / versionName 1.0.0；正式签名验证通过；Release 已用 `adb install -r` 覆盖安装到上述手机，未卸载微信。

## 未验证范围

- `com.tencent.mm.wx` 因未安装未验证。
- 最终安装包的详情点击开启状态、键盘完整开合循环、滚动复用长时间压力、所有 incoming/outgoing 消息类型，以及隐藏头像、卡片消息分支未完成全覆盖实机验收；相关生命周期/布局行为由回归和静态检查覆盖，但不代替这些实机结果。
- `uiautomator dump` 在该设备只返回空根结构；详情入口最终包复验的点击落入 MIUI 系统界面，因此不记为通过。

## APK

- Debug：`dist/Hchat-debug.apk`
- 正式签名 R8 Release：`dist/Hchat-release-signed.apk`
- Release SHA-256：`c14fa689d8eeed3934701980d1889a795fb539817e9e13369c39d526f5ca62e7`
