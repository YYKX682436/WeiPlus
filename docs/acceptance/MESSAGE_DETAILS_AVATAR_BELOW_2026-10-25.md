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

## R1 验收遗留收口

审查基线：`3eb6b345a2258fb1d92ba4970460318829926561`。本节记录本轮在该基线上的复现与修复。

### 修复前复现

- 旧缓存只比较消息身份和格式键，没有比较模板实际展示字段。同一 `localId` 的 `serverId` 从 0 确认到真实值、同一消息的 `createTime` 改变、原生对象原地更新字段时，旧输出都可能复用。
- 旧绑定路径每次进入标签插入都会遍历全局 binding snapshot。96 行各重复绑定 80 次的回归计数为 372,384 次全局条目访问。
- 头像上方布局固定以父布局顶部为基准。真实 `RelativeLayout` 测量回归中，头像上方应留 5px，旧实现实际留下 41px。
- 旧回归运行在旧生产实现上时，10 项中有 6 项失败，分别覆盖服务端 ID 更新、时间字段更新、同对象字段变更、无法解析时沿用 A 详情、全局扫描以及头像上方定位。修复后再次运行，11 项 Robolectric 测试全部通过；绑定 runner 也确认旧提交 `3eb6b345` 会被拒绝。

### 修改

- 将消息身份与显示输入拆开。身份使用会话、local/server ID、类型和方向；缓存键只快照活动模板真正使用的字段，并结合格式、时区、locale 和相对时间有效期判断显示结果是否可复用。未解析到新消息时立即取消旧代次任务、清除旧标签和旧点击 binding，不把 A 的详情作为 B 的内容。
- 正常行绑定通过弱 key、弱 value 的 row→label 索引直接取得标签；常规插入不再遍历全局 snapshot。弱引用 binding 索引与行内 tag 所有权保持分离，页面清理释放索引和回调。
- 头像上下方都以实测头像边界定位。上方空间足够时按头像上沿、标签测量高度和间距计算位置；不足时只移动头像分支并由原生 `RelativeLayout` 测量行高。重复 attach 保留同一 TextView 和相同 LayoutParams。
- 点击开关只安装或移除交互监听；标签点击从当前 binding 读取消息，并在点击时才解析完整详情。保留对微信原生 `timeTV` 的既有监听清理，标签本身的绑定仅在移除时清除。

### 本轮验证

- `:app:testDebugUnitTest`：Robolectric 11 项通过，0 failures / 0 errors。覆盖真实 Android `RelativeLayout` 测量、上下定位、零间距、高内容不重复扩行、同消息显示输入失效、A→B 点击数据更新、关闭点击不改几何和偏移恢复。
- 通过：`run_message_details_binding_tests.cjs`（并拒绝审查基线）、`run_message_details_layout_tests.cjs`（7 项）、`run_message_details_predraw_tests.cjs`（14 项）、`run_message_type_labels_tests.cjs`（96 项）、`run_performance_round1_tests.cjs`、`run_native_regression_tests.cjs`、`run_api102_r1_tests.cjs`。
- `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lint`：成功。Lint 为 0 errors、653 warnings、50 hints；现有 `firstOrNull()` 非唯一结果警告仍在 Hchat 扩展和 Tablet 功能处。
- 最终 APK：`dist/Hchat-release-signed.apk`，SHA-256：`ab6e17357e19c55e911e6af246c4558f298df2b815a91087afb86d4df2cd8cd3`。apksigner 验证通过，证书 SHA-256 与正式证书 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd` 一致；包名/版本为 `h.Hchat` 1.0.0，`META-INF/xposed/module.prop` 标记 min/target API 102。Release 已覆盖安装，未卸载或清除微信数据。
- 设备仍为 Redmi K50 Ultra（22081212C，Android 15 / API 35）。安装并实测 `com.tencent.mm` 8.0.76 / versionCode 3140；`com.tencent.mm.wx` 未安装。
- 最终 Release 在文件传输助手连续发送 3 条短消息，三个最新标签均完整识别为 `21:41:52`、`21:42:08`、`21:42:10`。零间距头像下方标签位于头像正下方；历史滚动和回到底部后标签仍随消息行显示。静置 12 秒前后，7 个可见时间标签的文字和屏幕边界坐标完全一致。
- 最终 Release 点击开启时，点击时间标签后 WeChat 增加了居中详情窗口；关闭时点击同一标签没有新窗口，点击前后可见时间坐标不变。测试后恢复为原先的点击关闭状态；时间功能保持开启。

### 尚未覆盖

- `com.tencent.mm.wx` 未安装，未实测。
- 本轮未在真机覆盖隐藏头像、卡片消息、头像上方不足空间、大字号和长自定义格式；这些分支由本轮实际 Android 布局回归及既有验收证据覆盖，但不计为本轮真机通过。

## R3/R4 通用滚动验收收口

R4 收口日期：2026-10-26
审查基线：`7fbea8a86c2b9893ca166619732ab4a00babac0f`。保留 R3 对“旧底部滚动位置先于消息行增长”的修复。本轮发现的 R4 是通用列表识别、历史跟随状态、最新消息确认和热路径缓存缺口，不改变 R3 的已确认根因。

### R3 根因与 R1/R2 覆盖边界

- R3 真机诊断显示，时间标签已经在消息行内并参与消息行原生测量；遮挡发生在微信先按旧消息行高度定位到底部，随后发送状态/`createTime` 补齐或 payload bind 使行高增长。行高增长后的最新行下边界越过当时的实际聊天列表视口，旧位置没有随高度变化补足。键盘开关会改变视口，但不是触发该时序的必要条件。
- R1/R2 的主要回归覆盖消息身份与显示缓存、标签复用和真实行布局；未覆盖 `ListView` 的可见 adapter 范围变化、未附着的新 adapter 消息、非消息 footer、列表子类的真实继承识别，以及延迟修正执行时消息身份/适配器尾部变化。因此它们通过不能证明尾部跟随状态和最新消息资格正确。

### R4 修复前复现与实现

- R3 基线的四项定向红测均复现：实际继承 RecyclerView 但类名不含该字符串时无法识别；`scrollY == 0` 但可见行向历史移动的 ListView 仍被误认为跟随最新；适配器已有未附着 B 时旧 A 仍可通过尾部候选检查；同一位置 A→B 复用后排队的 A 回调仍可能修正。
- 容器现在按实际继承关系识别 `RecyclerView`、`AbsListView`/`ListView`、`ScrollView` 及子类；未知结构不进入修正。ListView 根据 first/last visible adapter position、滚动方向和用户触摸意图更新状态。用户已进入历史阅读后，微信自己的程序化滚动到底部不会清除该意图；手动返回真实尾部后才恢复跟随。Hook 在 `proceed()` 后观察，不替换微信监听器。
- 尾部资格以当前 adapter item、消息身份和 adapter position 为准。消息之后的行只有在当前 attached holder 被确认不是消息行，或其 viewType 曾在同一 adapter 中由实际 attached 非消息 holder 确认后，才作为 footer/结构行接受；null 数据、未知 item 或单纯不同 viewType 都不再作为证据。延迟回调执行时重新验证位置、消息身份、绑定代次、附着、功能开关、最新消息资格及跟随状态；未知或过期状态跳过，不强制滚动。
- 容器类型、反射方法、adapter item 访问和 Hook 安装状态按宿主 class/method/adapter 生命周期缓存。先排除非跟随和无时间溢出的情况；suffix 检查在发现更新消息后立即停止。未知结构不扫描整张列表。初版 `ClassValue` 因不兼容 minSdk 27 已换为并发类缓存。

### R4 自动回归与构建

- R4 红测在当前 R3 实现上先失败，修复后完整 Robolectric：R1 原有 15 项 PASS，R4 新增 9 项 PASS，0 failures / 0 errors。新增用例使用真实 AndroidX `RecyclerView`、Android `ListView`/`AbsListView`、`ScrollView` 与名称不含容器类名的自定义子类；覆盖实际行高和裁切测量、尾部单次修正、ListView 可见范围变化、未附着 B、真实附着 footer 被确认后脱附仍能识别、未知 footer 安全拒绝、历史可见行不跳动、同位置 A→B、过期资格、detach/reattach、关闭清理、缓存计数与提前停止 suffix 检查。
- `run_message_details_binding_tests.cjs`（并以 R1 审查基线 `3eb6b345` 验证旧实现被拒绝）、消息详情布局 7 项、预绘制/回调生命周期 14 项、Native 提取与回归、性能、API 102 R1/interceptor、消息类型标签 96 项：PASS。缓存回归确认同类和同签名方法只解析一次；性能 runner 未报告退化。
- `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lint :app:lintRelease`：BUILD SUCCESSFUL。Debug/Release Lint 均 0 errors、653 warnings、50 hints；现有 `firstOrNull()` 非唯一结果提示位于 `HchatExtraFeature.kt` 与 `WeChatTabletFeature.kt`。
- `scripts/verify_api102_apk.py`：正式 R8 APK PASS，单一 API 102 入口、framework API 外置；`apksigner` v2 验签通过，证书 SHA-256 与现有正式证书一致：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`。

### 最终 Release 真机结果

- 设备：Redmi K50 Ultra（22081212C），Android 15 / API 35，微信 `com.tencent.mm` 8.0.76 / versionCode 3140。最终正式签名 Release 通过 `adb install -r` 覆盖安装并重启微信进程加载；未卸载微信、未清除微信数据或改动用户配置。
- 最终 R4 Release 覆盖安装后，文件传输助手连续短文字发送：键盘关闭 3/3、键盘打开 3/3；六条时间都完整显示，无需手动补滑。发送时分别确认 IME 隐藏/显示，键盘打开时最新时间仍在实际缩短的聊天视口内完整可见。此前 R4 构建的 5/5 + 5/5 是前一 APK 的结果，不计入最终 SHA 对应的轮次。
- 手动滑入历史后，长文字和较早短消息仍在原可视区域；静置 5 秒期间聊天内容裁切区逐像素一致，没有自动回到底部。历史位置直接聚焦输入框会触发微信自己的尾部定位；该宿主行为无法与模块修正完全归因，直接从历史发送列为 **NOT_VERIFIED**。Robolectric 已验证 `followingLatest=false` 时，即使最新行标签溢出也不会移动列表。
- 本轮真机 PASS：最终 Release 键盘开/关各 3 次短文字发送、历史滚动静置 5 秒。模拟测试 PASS：三类 Android 滚动 View、footer 最新消息资格、延迟身份/代次/生命周期与历史跟随。NOT_VERIFIED：`com.tencent.mm.wx`（设备上未安装）、历史位置聚焦输入框后宿主/模块的滚动归因，以及其他消息类型的本轮真机压力覆盖。本轮最终 Release 未观察到时间遮挡。

## R4 APK

- Debug：`dist/Hchat-debug.apk`
- 正式签名 R8 Release：`dist/Hchat-release-signed.apk`
- Release SHA-256：`b291a530f3800be3bd045a72f38a60ee9da278825e5d33cd474f02625e09f783`

## R4 最后一轮精准收口（2026-10-26）

基线：`7b24fb19015c686f288856d35a23f76e23ed4245`。本节修正上一节对结构行证据、Hook 安装缓存和 ScrollView 支持范围的判断；保留既有测量与尾部修正机制。

- **结构行识别**：`!isMessageDetailsHolder()` 只能说明没有找到直接的 `timeTV`，不能证明它是 footer。删除该反向推断及从它学习的 adapter viewType 集合。生产只接受 Android `ListView`/`HeaderViewListAdapter` 明确登记的 footer 范围，并核对当前 adapter 身份、header/content/footer 数量。RecyclerView 没有通用 footer 契约；未知后续行（包括无法解析的真实 B）安全跳过修正，不凭 holder 字段、tag 或 viewType 猜测。
- **Hook 存活**：删除独立的永久 class/method“已安装”集合。生产直接调用 `HookRegistry.hookOnce()`，由其现有有效句柄、安装代次和失败回滚管理去重/重装。反射类型与方法解析缓存保留；64 次重复调用生产安装方法没有新增 framework 注册。
- **ScrollView 范围**：普通 ScrollView 没有 adapter 和可靠的最新消息身份，生产观察、Hook 安装、尾部检查和资格确认入口明确跳过自动修正。既有 ScrollView 测量/调度测试仅是独立 helper 测试，不再作为完整生产链路支持证据。

### 修复前失败、修复后通过

新增 `MessageDetailsR4FinalRegressionTest` 直接反射调用投入生产的私有识别/安装方法以及消息解析器，不执行 Hooker 构造中的宿主配置/DexKit 初始化。基线生产源码保持不变时，5 项全部失败：

1. 真实 RecyclerView 已附着 B、adapter 数据暂不可解析、ViewHolder 无直接 `timeTV` 时，A 被错误确认是最新消息。
2. Android 明确登记的 ListView footer 在尚未附着时无法被确认。
3. `unhookAll()` 后，同一进程新安装代次没有重新获得有效 Hook。
4. 外层安装 lease 回滚后，同代次重试被旧安装集合阻止。
5. 没有 adapter 的普通 ScrollView 仍安装了两条无用滚动触摸 Hook。

修复后原有 24 项及新增 5 项 Robolectric 共 **29/29 PASS**。原 footer 用例已移除测试专属布尔 tag，改用生产识别逻辑；实际 Android ListView 固定 footer 仍可确认，未知 RecyclerView 行不能确认。Hook 用例使用官方 `io.github.libxposed:api:102.0.0`、生产 HookRegistry/XposedRuntime 和既有 API 102 注册 fixture，实际执行重装后的 ManagedHook，验证生产跟随状态改变且宿主执行一次；覆盖成功、撤销、新代次重装、同代次回滚重试、framework 注册失败重试。此 JVM 注册 fixture 不代替真机 ART 故障注入。

- 消息绑定、布局 7 项、预绘制 14 项、消息类型 96 项、性能、Native 回归及提取、API 102 R1/interceptor、HookRegistry 全部 PASS。
- Debug、正式签名 R8 Release、Debug/Release Lint 构建成功；两种 Lint 均 0 errors、653 warnings、50 hints。正式 APK API 102 检查与 v2 签名检查 PASS，证书保持正式证书。

### 本轮真机与边界

- Redmi K50 Ultra / Android 15 / 微信 `com.tencent.mm` 8.0.76，最终 Release 已覆盖安装。原始配置恢复后键盘关闭发送 **2/2**、键盘打开发送 **2/2**，四条最新时间完整可见，无需补滑。历史位置静置 5 秒，排除边缘滚动条的内容裁切区逐像素一致，没有跳到底部。
- UI 禁用后无标签、重新启用保存后标签恢复，完成一次禁用/恢复。测试前备份的 `Hchat_extra_config.kva/.kvb` 与 `Hchat_global_config.kva/.kvb` 已逐字节恢复；后续发送及历史验证后再次核对完全一致。extra 两份 SHA-256 均为 `0bfe8245ad949f423f55ecd7d578ae3e720d82e00a4b0da775c25f1d91f2c964`，global 两份均为 `969407ccbaa6b61fb9cfffa7d8e8697c7458dd19708d21c3409cf2c3ae33a87c`。未卸载微信、未清除数据。
- **NOT_VERIFIED/观察边界**：第一次安装后冷进入聊天，开关为开但旧行标签一度未出现，该次不计 PASS；原始配置恢复后再次冷启动进入已正常显示，本轮未确认首次现象的原因。未在真机注入 `unhookAll()`/安装失败；该恢复路径由生产代码的官方 API 102 注册回归覆盖。`com.tencent.mm.wx`、其他消息类型压力与未知 RecyclerView footer 的正向资格契约未验证；后者保守跳过，普通 ScrollView 不执行自动修正。

最终正式签名 R8 APK：`dist/Hchat-release-signed.apk`，SHA-256：`488a5442d7ae4bba9445ecfca1b2a67722437ef8dfa357fed0b2ade46bfc47b6`。
