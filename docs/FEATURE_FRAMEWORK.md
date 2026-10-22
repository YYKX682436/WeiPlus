# Hchat 功能框架

## 当前运行边界

- 模块只接受 `com.tencent.mm` 与 `com.tencent.mm.wx`；微信内部 Java 类名仍使用微信原有的 `com.tencent.mm.*` 命名空间。主进程加载普通功能，小程序进程只安装明确需要的轻量 Hook。
- 原生功能由 `FeatureRegistry` 注册，`FeatureManager` 先初始化设置，再预装轻量 Hook。Hook 回调读取当前设置并快速返回；只有无法在运行中补装的早期功能才显式声明 `ENABLED_ONLY` 或重启要求。
- `DexInstallScheduler` 串行安排昂贵定位工作；`HookRegistry` 负责 Hook 去重和安装代次。销毁时先使旧代次失效，再清理异步任务与资源。
- 共享 Hook 由实际原生功能拥有，并为各功能提供独立订阅。功能销毁时应取消订阅、监听器和视图回调，不能依赖其它功能的初始化顺序。
- 配置、缓存和文件目录应从当前宿主 `Context` 获取。`HchatStorage` 的内存缓存键包含宿主包名，不能用一个包名的缓存代替另一个包名。

## 项目位置

- `app/src/main/java/h/Hchat/ModuleEntry.java`：LSPosed 注入入口和进程分流。
- `app/src/main/java/h/Hchat/hooks/core/`：Feature 生命周期、Hook 注册表与 Dex 安装调度。
- `app/src/main/java/h/Hchat/hooks/items/`：原生功能实现。
- `app/src/main/java/h/Hchat/hooks/api/`：共享的微信反射 API。
- `app/src/main/java/h/Hchat/ui/miuix/`：模块设置界面。

## 依赖边界

- DexKit、KavaRef、dalvik-dx、OkHttp、FastKV 和 Silk 编解码依赖仍服务于原生 Hook、设置注入、网络桥接、宿主存储或语音能力。
- 新依赖必须有可定位的原生调用者。Release 开启 R8 与资源压缩；反射、Xposed 和 JNI 入口按实际调用方式保留。
- 版本发布只维护当前 `main` 代码，不从已删除的分支下载或生成第二套 APK。

## 日志规则

模块自身的失败、异常、未找到、未命中和不可用情况统一使用 `h.Hchat.utils.HLog.e(...)`。普通运行状态不输出到 LSPosed；用户明确开启的调试日志按对应功能设置记录。

`捕获异常日志` 位于 `娱乐 > 调试`，默认关闭。开启后，模块在用户同意协议后于微信主进程启动期安装异常记录器；运行中开启也会立即补装。Java 捕获器在 `Application.onCreate` 原方法执行前安装，未捕获异常会写入完整线程堆栈；Native 捕获器覆盖 `SIGSEGV`、`SIGABRT`、`SIGBUS`、`SIGILL`、`SIGFPE`、`SIGSYS`，以信号现场信息保存信号、线程、故障地址、关键寄存器、所属 so 与偏移、可执行内存映射，并在 ARM64 上保存最多 32 层经映射校验的 frame-pointer 回溯。记录完成后继续转交微信原处理器，不能阻断系统 tombstone。Android 11 及以上同时读取 `ApplicationExitInfo`：Native 异常补充退出 PID/UID、内存和系统 tombstone；系统明确记录为 `REASON_ANR` 时单独生成 ANR 报告并附系统 trace，不用固定时长的主线程看门狗推断 ANR。记录保存在宿主私有目录 `Hchat/crash/`，稳定 Activity 显示后可查看和复制。关闭开关会立即取消待展示弹窗并清理尚未归档记录，`last_crash.log` 继续保留。

## 分层规则

- `hooks/api/**`: 只放公共微信 API，不放功能私有业务逻辑。
- `hooks/items/<feature>/**`: 每个功能一个独立功能包。
- `hooks/core/**`: 功能生命周期、Hook 注册和共享框架辅助逻辑。
- `ui/**`: 共享设置 UI 框架、Provider 接口和内嵌 Miuix 设置页。
- `event/**`: 跨功能事件。
- `dexkit/**`: DexKit 定位和已缓存的反射目标。
- `utils/KavaReflector.kt`: 项目统一反射入口，基于 KavaRef 封装。新增反射代码必须走这里；即使 Xposed/DexKit 需要原始 `Method`、`Field` 或 `Constructor`，也先由这里获取。
- `compat/kavaref/**`: KavaRef 运行时本地兼容层。文件目录放在 `h/Hchat/compat/...` 是为了项目结构好看，但文件内部包名必须保持 `com.highcapable.kavaref...`，因为 KavaRef core 会按这个完整类名查找。

## 开发规则

当某个行为依赖微信内部逻辑且答案不确定时，先逆向微信。宁愿多花时间确认，也不要猜。

硬规则：

- 不要编造类名、方法名、字段、构造签名、数据库表、Intent 参数或网络请求结构。
- 修改触碰微信内部逻辑的代码前，必须先有 DexKit、DexClub 或 APK 逆向证据。
- 证据不完整时要说明未知点，并保留受保护的 `isAvailable()` / `supports...()` 路径，不要写假实现。
- 不要把不确定的修复当成事实。
- 代码必须放在正确分层：
  - 功能行为放在 `hooks/items/<feature>/**`
  - 可复用且已验证的微信能力放在 `hooks/api/**`
  - DexKit 目标发现和缓存放在 `dexkit/**`
  - 框架生命周期和 Hook 清理放在 `hooks/core/**`
  - UI Provider 和共享 UI 辅助逻辑放在 `ui/**`
- 不要因为方便就把功能私有逻辑塞进 `hooks/api/**`。
- 不要在功能内重复实现公共 API。先使用 `WeChatApis`，只有验证目标版本后才新增 DexKit 逻辑。
- 新增或修改功能只要使用 DexKit 定位微信类、方法、构造器或字段，就必须缓存定位结果。缓存 key 必须包含微信版本、versionCode、clientVersion、Tinker/热更新标识、APK 时间戳和 ClassLoader 指纹。读取缓存时如果已存 key 和当前 runtime key 不一致，必须立即清空旧 descriptor 并写入当前 key，避免微信升级/降级后界面或文件仍显示旧版本缓存。缓存命中时不得每次启动微信都重新跑 DexKit；只有缓存缺失、缓存目标无法解析、微信升级/降级或热更新标识变化时才重新定位；不要跨版本或跨热更新复用旧 descriptor。
- DexKit 较重的定位和相关 Hook 优先放到后台线程安装，避免拖慢微信启动。必须在微信 attach、登录或消息初始化前生效的功能可以同步早期安装，但必须先尝试有效缓存路径，并在代码或功能文档里说明原因。
- 不要在功能代码里散落原始 Java 反射。字段、方法、构造器查找、实例创建、类加载和 `Method.invoke` 统一使用 `KavaReflector`。

## 反射规则

项目代码默认使用 KavaRef 作为反射封装。

依赖：

- `com.highcapable.kavaref:kavaref-core:1.1.0`

为什么有本地兼容层：

- 官方 `kavaref-android:1.1.0` 要求 `compileSdk 37`。
- 本地项目使用 `compileSdk 34` / `targetSdk 34` 和 AGP `9.0.1`，GitHub Actions 构建时临时覆盖为 `compileSdk 37` / `targetSdk 37`。
- 为了保持 release 构建稳定，项目在 `h/Hchat/compat/kavaref/**` 提供 `kavaref-core` 需要的少量 platform/runtime 类。
- 不要修改这些文件里的 `package com.highcapable.kavaref...`。目录是为了项目结构好看，包名是 KavaRef 的运行时约定。

推荐写法：

```kotlin
val ctor = KavaReflector.findConstructor(clazz, String::class.java, Int::class.javaPrimitiveType)
val request = KavaReflector.newInstance(ctor, talker, 0)

val fields = KavaReflector.declaredFields(clazz)
val value = KavaReflector.readField(instance, "fieldName")
```

Java 调用：

```java
Object value = KavaReflector.readField(target, "fieldName");
Object request = KavaReflector.newInstanceByArgs(clazz, args);
```

规则：

- 新业务/新功能不要直接调用 `getDeclaredFields`、`getDeclaredMethods`、`getDeclaredConstructors`、`getDeclaredField` 或 `Constructor.newInstance`，除非有明确互调原因。
- 如果 Xposed、DexKit 或缓存需要原始 `Method` / `Field` / `Constructor`，必须通过 `KavaReflector` 获取。
- 项目没有主动升级到兼容的 compile SDK 前，不要添加 `kavaref-android`。
- 不要把 `kavaref-jvm` 和 `kavaref-core` 一起加进依赖；`1.1.0` 版本会出现重复的 `KavaRefProperties` 类。

## Kotlin / Java 规则

新增或修改代码时，只要实际可行就优先使用 Kotlin。如果某段 Xposed、反射、DexKit、R8 或 Java 互调路径用 Kotlin 会变得别扭或有风险，就改用 Java。

现存 Java 文件属于当前稳定代码，不是必须迁移的待办事项。

新增会被 Java 调用的 Kotlin 代码时，保持 Java 可调用名称稳定：

- Java 需要静态风格调用时使用 `@JvmStatic`
- 只有 Java 代码需要直接字段访问时才使用 `@JvmField`
- Kotlin 的 `val isAvailable` 或显式函数要保持 `isAvailable()` 兼容
- 公共 API、生命周期组件和反射边界保留类级注释
- 不要把语言迁移和行为变更混在一起
- 默认不在本地执行 Gradle；明确允许“本地编译”时，只跑 compile 任务；明确允许“本地构建”时，验证 release 构建使用 `:app:assembleRelease`。

## 功能包结构

较大的功能要保持入口类精简，并按职责拆分实现。

当前红包功能结构：

- `hooks/items/payment/core`: 功能入口、设置、状态和流程编排。
- `hooks/items/payment/detect`: 旧消息/数据库兜底识别、XML/nativeUrl 解析、反射辅助和过滤。
- `hooks/items/payment/grab`: 静默网络领取流程和 UI 领取兜底。
- `hooks/items/payment/reply`: 领取成功后的自动文本回复。
- `hooks/items/payment/notify`: Toast/系统通知输出和模板变量格式化。
- `hooks/items/payment/fake`: 假红包/拆包兼容。

当前转账功能结构：

- `hooks/items/payment/transfer`: 自动收转账功能、设置、XML 解析和规则。
- `hooks/items/payment/fakebalance`: 伪造零钱显示功能，只替换钱包、零钱、零钱通和经营账户相关页面的本地 UI 显示，不修改真实余额、数据库、支付请求或网络返回。
- 伪造零钱显示采用 WeKit 同款思路：只 hook `com.tencent.mm.plugin.wallet_core.ui.view.WcPayMoneyLoadingView` 的金额设置入口，并补 hook `com.robinhood.ticker.TickerView` 文本设置入口；余额、零钱通和经营账户分别提供独立开关、显示方式和金额，设置页只在对应开关开启时显示配置，关闭时保留已保存内容。显示方式可选固定金额、增加金额或减少金额，金额输入只保存非负数；增加或减少模式基于微信本次传入的真实金额计算，例如真实余额 `12.63` 选择增加金额并填写 `100` 后显示 `112.63`，减少后的结果最低为 `0.00`。旧版没有显示方式字段时默认使用固定金额，曾保存的带符号金额仍会识别为对应增减模式。旧版总开关仅作为三个新开关尚未保存时的兼容默认值，不再显示或写入。启用对应账户后不替换构造阶段的无数字空文本，零钱通和经营账户等待微信设置最终字号后再一次性替换金额；服务页 cell、金额加载控件与 TickerView 的嵌套调用共用一次动态计算结果，同一控件重绘时也会识别上次渲染值并继续以缓存的微信原始金额为基准，不能把已经增加或减少过的显示值再次参与计算。模块不修改微信传入的字号，只在字号变更后刷新 TickerView 每个字符的滚动高度和最终位置，避免小字号创建的字符列在放大后显示重影。服务页钱包列表按 `MallWalletSectionCellView.c(..., String, ...)` 的 cell 绑定参数区分 `balance_cell` 和 `lqt_cell`。余额、零钱通和经营账户页面优先用金额控件局部父层级文案区分金额类型，经营账户识别服务端动态下发的经营账户、经营账号、商户账户、商户余额和商家账户文案；局部文案未命中时先读 Activity 页面标题，再回退类名或调用栈，避免经营账户复用零钱通页面类时继续误用零钱通金额。经营账户金额旧配置未保存该值时先沿用零钱通金额，用户保存后独立生效。8.0.66/68/72/74 额外修改 Kinda 零钱通详情入口传入的分单位余额；8.0.49/58 没有该入口，仍走旧 UI 金额控件链路。不劫持配置存储、不做页面生命周期补刷、不扫描整页普通 `TextView`，避免多链路补丁互相覆盖导致金额跳回。
- 自动收款只对微信转账状态机里的待领取状态发起操作：空 `paysubtype` 兼容旧消息，明确状态只接受 `1`、`7`、`21`、`27`；`21/27` 覆盖高版本延迟到账转账。其它状态视为已结束或不可领取，不自动收款/退回。
- 自动收款支持选择零钱、零钱通、经营账户和服务端下发的其它具体账户。零钱直接发起 confirm，不增加查询等待；选择非默认位置时先按当前登录 wxid 复用已缓存的 `recv_account_type`、`bind_serial`、`sub_recv_channel_id`，仅在缓存缺失时主动发送微信原生 remittance query，从 `recv_account_info.recv_channel` 按账户名称匹配真实账户并持久化，后续转账直接复用。查询类通过 `Micromsg.NetSceneTenpayRemittanceQuery`、`recv_account_info`、`recv_channel` 和构造/响应签名动态定位并缓存，不固定混淆类名、方法名、字段名或账户类型数值；8.0.49、8.0.58 使用五参数构造，8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 使用包含 `transfer_attach` 的六参数构造。领取/退回的 `transferoperation` 请求同样按完整类型签名映射：8.0.49 为 10/9 参数、8.0.58 为 12/11 参数、8.0.66 为 13/12 参数、8.0.68/72/74/76 为 14/13 参数，后一项是省略 `left_button_continue` 的委托构造；群聊请求传真实群 wxid，构造或发包失败日志包含微信版本、失败阶段、请求类和构造签名。查询失败、超时、账号切换、选项失效或服务端未提供对应账户时回退零钱，详情页账户项 Hook 仅保留为缓存补充。
- 自动收款规则与红包助手对齐为独立的模板、默认模板和适用聊天模型；旧版全局设置继续作为无模板时的兜底。每个好友或群聊最多绑定一个模板，绑定关闭时该聊天不自动收款，模板删除会解除相关绑定，批量套用会覆盖同一聊天旧绑定；适用聊天列表支持选择部分条目或全选当前筛选结果并确认批量删除。模板整体保存收款位置、固定/随机延迟、白名单/黑名单、金额条件、关键词、禁收时段、拒收退回、回复步骤和成功提醒。
- 全局设置和模板都支持多步骤收款后回复，私聊与群聊分别配置，回复类型包括文字、图片、语音、视频、表情、文件、收藏和 XML；每一步可设置发送前延迟并随机追加 `0-2` 秒，按列表顺序发送。文字、XML、通知、Toast 和播报支持 `{amount}`、`{talker}`、`{sender}`、`{time}`，文字回复额外支持 `{@转账的人}` 生成真正的群聊 @。旧版只有一套回复配置时，私聊和群聊继续共用旧配置；保存群聊配置后可留空，以关闭群聊自动回复而不影响私聊。`{time}` 表示本次收款成功时的本机时间，格式由自动收款全局设置单独配置，默认 `yyyy-MM-dd HH:mm:ss`；保存时会校验 `SimpleDateFormat` 格式，多步骤延迟回复仍使用同一次收款时间。
- 自动收款成功提醒支持通知栏、Toast、系统/自定义铃声、震动和 TTS 播报，使用独立通知通道；不提供失败通知，收款请求无法提交时只记录模块错误日志。
- 转账领取/退回本身不在功能包里实现，必须调用 `WeChatApis.payment().transfers()`，让 DexKit 定位集中在 `hooks/api/payment` 和 `dexkit/DexFinder`。

红包识别顺序：

1. 优先使用 `WeChatApis.message().observe()` 作为统一消息源。
2. 只有 `observe()` 不可用时才使用 `RedPacketMessageHook` 和 `RedPacketDatabaseHook`。
3. 去重逻辑保留在 `RedPacketState.markDetected(...)`。
4. 红包过滤时按 XML `fromusername` -> nativeUrl `sendusername` -> 消息前缀 -> observe sender 解析发送者，让“跳过自己的红包”基于真实红包发送者判断。

消息观察 API 和红包解析器通过 `XmlValueReader` 共用标签文本提取，正确处理多行 CDATA、普通文本的 XML 实体和数字实体；CDATA 内容保持原样，不把其中的伪标签识别为支付字段。企业红包以明确的 `weixin://weixinunionhongbao/` 协议、结构化支付字段或企业会话判定，不扫描祝福语中的“企业微信”等关键词。可信企业证据优先于普通 scene 值；共享 `c2cbizmessagehandler` 入口本身不代表企业红包。UI 领取入口统一填写 `scene_id` 和 `key_way`，企业红包场景为 1005。逐版本依据、回归方法和验证边界见 [企业红包修复记录](RED_PACKET_UNION_FIX.md)。

红包规则配置：

- 领取执行器沿用本次红包命中的有效规则，不再因旧版全局模式与聊天规则不同而退出。UI 自动点击和结果处理只接受模块已经登记的 UI 领取任务，手动打开或静默任务的页面不接管；成功、失败和页面启动失败释放登记。关闭红包总开关会阻止后续点击、回调拆包和延迟重试。

- 红包助手支持“红包模板”和“适用聊天”两级配置。模板只定义抢包策略，适用聊天只负责把好友或群聊分配到某个模板，同一个聊天只保留一条记录，避免同一对象被多个规则重复命中；适用聊天不包含公众号。
- 适用聊天列表支持批量删除。批量模式下可按当前搜索结果全选或取消全选，删除前必须通过模块弹窗确认；删除按绑定 ID 过滤并立即持久化，不会误删其它模板或规则。
- 规则匹配优先级固定为：适用聊天优先；命中的适用聊天如果开关关闭则直接不抢，开关开启时按绑定模板执行，没有绑定模板时跟随默认规则或旧版全局设置；未命中适用聊天时使用默认规则；没有默认模板或没有任何规则时回到旧版全局设置。绑定到不存在模板的聊天视为规则关闭，不再意外回落到其它模板。
- 当前红包模板覆盖抢包模式、无延迟/自定义延迟/随机延迟、跳过自己、关键词过滤、禁抢时段、私聊红包回复步骤、群红包回复步骤，以及通知栏、浮窗、铃声、震动、失败提醒和抢到后播报。全局旧版设置也分别保存私聊和群聊回复；升级后尚未保存群聊回复字段的旧配置继续让群聊沿用原回复步骤，用户保存后两套步骤才独立生效，任一列表为空只关闭对应场景的回复。禁抢时段通过时、分、秒选择器配置并按当天秒数匹配；新 JSON 使用 `quietStartSecond` / `quietEndSecond`，读取旧分钟字段时自动乘以 60。通知栏、浮窗、失败提醒、播报和文字/XML 回复支持 `{time}`，表示本次红包处理时的本机时间；时间格式由红包全局设置单独配置，默认 `yyyy-MM-dd HH:mm:ss`，保存时校验 `SimpleDateFormat` 格式。通知栏和浮窗提醒文案分别配置，对应输入框只在开启对应提醒后显示。Android 8+ 红包通知通道保持静音，铃声由模块按模板或全局设置手动播放，避免通知通道声音创建后不可更新导致换铃声不生效；从文件选择的铃声会复制到模块外部文件目录，避免原始文档 URI 权限失效。两类回复步骤都在二级页管理，每条步骤可单独设置文字、图片、语音、视频、表情、文件、收藏或 XML、发送前延迟和随机追加延迟，运行时按列表顺序发送。文字回复支持 `{@发红包的人}` 变量，命中时会用微信原生 @ 参数发送红包发送者。媒体/文件类回复在设置页通过系统文件管理器选择文件，不要求用户手填路径；收藏回复通过收藏选择页选择一个或多个收藏 `localId`，选择页只走微信原生收藏存储接口，并按微信原生游标推进方式连续分页加载全部收藏，不使用数据库兜底，也不固定混淆类名、方法名或字段名。旧版白名单/黑名单和自动屏蔽新进群仍作为全局兼容设置保留。
- 旧版全局抢包延迟设置同样收敛为 `无延迟`、`自定义延迟` 和 `随机延迟` 三个选项；只有选中 `自定义延迟` 时显示单个延迟输入框，选中 `随机延迟` 时显示最小/最大延迟输入框。
- `自动屏蔽新进群` 是自动抢红包的全局保护开关，默认关闭。模块启动后会把当前群聊记录为基线；开关开启期间新加入或新出现的群聊会自动加入 `适用聊天`，并把该群开关设为关闭。用户可以在 `适用聊天` 里手动打开某个新群，打开后按该群绑定模板、默认规则或旧版全局设置正常抢红包。
- 红包关键词过滤只匹配红包祝福语、发送标题、描述、备注等用户可见文案，不直接匹配完整 XML、`nativeurl`、发送者、接收者或其它固定协议字段；发送标题、描述里如果是“给你发了一个红包”等微信固定文案会被排除，避免单字关键词误伤所有红包。关键词支持用 `|`、中英文逗号或换行分隔；屏蔽关键词模式命中任一关键词即跳过，指定关键词模式要求命中至少一个关键词才抢。
- 抢到红包后可选系统语音播报，播报文案复用红包模板变量，例如 `{amount}`、`{talker}`、`{sender}`、`{time}`。播报开关默认关闭；命中模板时按模板通知配置执行，未配置通知字段的旧模板会继续沿用旧版全局通知设置。播报使用系统 TTS 的媒体音频通道，只有成功提交 TTS 后才写入去重记录；首次初始化失败会保留待播报内容并重建一次 TTS，避免首条红包播报直接丢失。

依赖关系要通过清晰 API 向内收敛：

- 功能入口负责创建和组装组件
- detect/grab/reply/notify/fake 不要自己注册到 `FeatureRegistry`
- 共享微信行为放到 `hooks/api/**`，不要放进单个功能包
- 仅该功能使用的辅助逻辑留在功能包内部
- 操作微信转账请求的支付功能必须使用 `TransferOperationParams` 和 `WeChatApis.payment().transfers()`

## 新增功能

1. 创建 `hooks/items/<feature>/<FeatureName>Feature.kt`。
2. 继承 `BaseFeature`。
3. 在 `onFeatureInit` 注册 settings provider。
4. 在 `onFeatureInstall` 安装 Hook 或 API 观察者。
5. 在 `FeatureRegistry` 注册功能。

```kotlin
class ExampleFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "示例功能"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ExampleSettingsProvider())
        subscribe(Events.MessageReceived::class.java) {
            // 轻量事件处理
        }
    }

    @Throws(Throwable::class)
    override fun onFeatureInstall(context: FeatureContext) {
        // 安装 Hook 或使用 WeChatApis
    }

    companion object {
        const val ID = "example"
    }
}
```

## 新增设置卡片

Settings provider 只提供元数据，不能打开弹窗、Activity 或自定义 View。

设置入口只需要固定元数据时，继承 `SimpleFeatureSettingsProvider`。

```kotlin
class ExampleSettingsProvider : SimpleFeatureSettingsProvider(
    ExampleFeature.ID,
    "示例功能",
    "功能说明",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
```

Provider 分类决定主页面所在页签：

- `FeatureSettingsProvider.CATEGORY_PRACTICAL`: 实用
- `FeatureSettingsProvider.CATEGORY_ENTERTAINMENT`: 娱乐
- `FeatureSettingsProvider.CATEGORY_ENHANCE`: 插件

`SimpleFeatureSettingsProvider(featureId, title, subtitle)` 默认放到 `插件`。需要放到 `实用` 或 `娱乐` 时，使用四参数构造。主页面先按分类进入页签，页签内可在 `MiuixSettingsPage.kt` 里按功能 ID 做固定分组和排序。

然后在 `ui/miuix/MiuixSettingsPage.kt` 添加实际页面：

```kotlin
@Composable
private fun FeatureSettingsPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    when (provider.featureId()) {
        ExampleFeature.ID -> ExampleMiuixPage(context, provider, onBack)
        else -> UnsupportedFeaturePage(provider, onBack)
    }
}
```

## 设置 UI 规则

所有设置 UI 都由 `MiuixSettingsPage.kt` 里的内嵌 Miuix 框架渲染。

硬规则：

- 功能设置不要使用 `AlertDialog`、`Dialog`、模块 `Activity` 或功能私有设置 View。
- 不要在 `FeatureSettingsProvider` 上重新引入 `showDetail(Context)`。
- `FeatureSettingsProvider` 只暴露 `featureId`、`title` 和 `subtitle`，不要把图标/颜色字段加回来。
- 功能所在主页签由 `FeatureSettingsProvider.category()` 控制。
- `SettingsUI.show(context)` 保持模块设置入口并调用 `MiuixSettingsPage.show(context)`。启动入口包括微信设置项、按设置启用的 LauncherUI 右上角加号菜单、长按加号入口和微信进程内悬浮快捷菜单；这些入口复用已有注入链路。
- 新版微信的模块设置项保持在 `SettingAdditionHeaderSearch` 与 `SettingGroupPersonalInfo` 之间。微信按 `SettingLocation.frontItem -> 当前项` 构造单链；WeKit 会在 `SettingGroupPersonalInfo` 的位置方法上用 `beforeHook` 直接返回其代理项，默认优先级下可能让后注册模块的 `afterHook` 根本不进入回调队列。Hchat 对该位置方法使用最高优先级 Hook，先进入回调队列并在最后执行 `afterHook`：读取 WeKit 或其它入口已经返回的 `frontItem` 作为 Hchat 前置项，再把 `SettingGroupPersonalInfo` 接到 Hchat 后面，保持原位置且不覆盖微信原生设置。
- 右上角加号菜单入口必须通过结构校验动态定位 `MicroMsg.PlusSubMenuHelper`。不要写死 `of/rg` 这类特定版本混淆类、`lf/og` 这类 wrapper 类或 `mf/pg` 这类 item 类。
- Hchat 右上角加号菜单图标从菜单 adapter 行 View 上按模块菜单 ID 设置，不能把模块 `R.drawable` ID 传进微信菜单 item。设置入口和全部已读使用透明底白色线性 `H` 自绘图标。
- 右上角加号菜单的 `SparseArray` key 必须严格保持为从 `0` 开始的连续 adapter position key。已横向确认微信 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76 的 adapter 都通过 `items.get(position)` 读取 wrapper；负 key、跳号或移除后遗留的空洞都会让 `getCount()` 覆盖到空 wrapper。前置、后置和移除菜单项时必须按最终显示顺序整体重排并连续写回，点击仍通过注入 item id 识别。
- 长按右上角加号入口只通过 `com.tencent.mm.ui.HomeUI$PlusActionView` 构造函数绑定，并调用其 `h()` 方法获取真实按钮 `View` 后设置 `OnLongClickListener`；不要再安装 `View.setOnClickListener`、`dispatchTouchEvent` 或 `performLongClick` 兜底 Hook。
- 插入 Hchat 菜单行不能只依赖加号菜单 adapter 工厂 Hook。微信 8.0.58/66/68 会在子类无参 `boolean d()` 里填充菜单 `SparseArray` 后再委托父类显示逻辑，8.0.74 可能在父类构造阶段提前创建并缓存 adapter。插入逻辑必须在子类填充方法后和父类显示方法前都执行。
- 平板模式必须在主进程过滤前安装早期 Hook，确保微信 main、push、tools 和迁移相关进程在升级或降级后都返回一致的平板状态。其它 Hchat 功能仍然只能保持主进程生效。
- 平板模式围绕 `TinkerApplication.onBaseContextAttached` 安装：`before` 先使用当前 loader 已有效的缓存。开启屏蔽热更新时，`before` 也可以立即定位并 Hook base APK loader，因为 Tinker 补丁会被拦截，base loader 就是预期运行时；这样能防止降级后的首次启动在平板模式生效前进入微信 attach/登录检查。未开启屏蔽热更新时，完整 DexKit 定位必须在 `after` 用 post-Tinker class loader 执行。Hook 状态必须按 class loader 跟踪，不能用单个全局 boolean。
- 平板模式只能 Hook 通过 `Lenovo TB-9707F` 锚点定位到的微信内部平板检测方法，以及通过 `loginAsOtherDeviceBtn` 定位到的登录按钮可见性方法。不要全局伪造 `Build.*` 或设备指纹。方法 descriptor 缓存必须按当前微信版本、clientVersion 和 Tinker 标识区分，升级或降级后不能回退使用其它版本的 descriptor。
- 设置页嵌入当前微信 `Activity` 的 decor view，不能启动模块 app。
- 保持 page/ComposeView 上安装 `EmbeddedComposeOwner`，并给 ComposeView 绑定当前页面独立的 `Recomposer`；不在 decor 上安装 owner，不复用窗口级 `WindowRecomposer`，页面关闭时同步销毁自己的 composition、Recomposer、lifecycle、saved-state、ViewModel 和 navigation owner。
- 没有右上角关闭按钮。返回行为是：详情页返回主列表，主列表关闭 overlay。

`MiuixSettingsPage.kt` 已经统一处理的可复用 UI 行为：

- `LazyColumn` 页面使用 `rememberLazyListState()` 并传入 `state = listState`，从二级选择页返回时保持滚动位置。
- 会跳转或选择的可点击行使用 `responsiveTap(...)`，不要直接用 `Modifier.clickable(...)`，避免滚动后的第一次点击被吞。
- 少量互斥枚举/int 选项优先使用类似系统设置的页内弹出选择菜单，即 `PopupChoiceRow` / `PopupChoice`；不要做点击一行循环切换值。
- 只有选项很多、需要多选、需要搜索或需要复杂配置时，才使用 `OptionPickerPage` 或单独二/三级页面。
- 新增功能的二级/三级菜单切换必须走 `SettingsRouteTransition` 或同等过渡动画；三级菜单返回二级菜单必须保留二级列表原滚动位置，不允许返回后跳到顶部；系统返回、返回手势和模块返回按钮必须执行当前可见页面的同一返回动作，四级页面必须逐级返回三级页面，不能由外层宿主直接跳回二级页面。
- 二级选择菜单选中条目后不要自动返回上一级，必须等用户手动点击返回，避免连续配置多个选项时反复进入菜单。
- 选择器里的筛选分组、标签筛选、搜索筛选属于同层状态切换，不做左右横向页面进出动画；只有进入标签成员、群成员、编辑页这类真实下一层页面时才做横向过渡。
- 联系人选择器每个联系人使用独立 lazy list item，不要把大联系人列表塞进一个 `SettingsCard`。
- 大型联系人名单必须使用批量标签关联、轻量联系人记录和仅含会话 ID 的排序查询；不能按标签逐个扫描 `rcontact`，也不能只为排序读取完整会话或解析无关的 `lvbuff`。筛选结果在一次重组内复用，已选置顶保持线性稳定分区，联系人 lazy item 使用稳定 ID key。
- 好友、群聊、群成员、标签好友等名单选择器进入页面时，必须在当前筛选和搜索结果内把已保存的已选项置顶，未选择项保持原有排序；本次进入后新增勾选或取消勾选不要立刻触发列表重新排序。通用会话/联系人名单选择器支持按聊天分组及其全部子分组筛选候选项，提交时只持久化真实会话 ID，不保存 `wxid_hchat_group_*` 虚拟分组 wxid。通用好友选择只包含微信好友和企业微信联系人，不包含单向联系人或群成员；所有联系人按微信原生会话的新旧顺序排列，无会话记录的项目放在末尾。群聊存在备注时统一显示为 `群备注(群名称)`，搜索同时匹配群备注、群名称和群号；弹窗或页内弹出菜单如果承载名单/多选，也遵守相同显示、搜索和置顶规则。普通少量单选枚举弹窗保持业务顺序即可。
- 涉及好友、群聊、公众号、标签好友或群成员名单的设置必须使用现有选择器，不要额外暴露 wxid、群号或成员 ID 手动填写框。
- 涉及文件、媒体或路径选择的设置必须使用 Android 系统文件管理器或系统选择器，不要要求用户手动输入绝对路径作为唯一入口。
- 联系人和选项的选中标记保持小尺寸并放在右侧。
- 主功能行保持普通设置行样式，不显示 provider 图标。图标只保留给底部导航。
- 联系人头像由 `AvatarMemoryCache` 做进程内缓存，只在微信进程重启后重新加载。
- 开关使用 Miuix 默认主题色，不要写死红色开关。
- `PageScaffold` 统一处理系统导航栏避让：三键/虚拟按键模式下，底部导航、底部操作栏和内容底部 padding 要整体避开虚拟按键，并保留一层间距；如果内嵌微信 decor 后 `WindowInsets.navigationBars` 取不到高度，要用系统 `navigation_bar_height` 兜底。全面屏手势模式保持原贴底观感。
- 多级设置页不要用早退直接替换 Composable。普通选择器、模板/名单管理、标签成员和群成员这类子页面切换统一走 `SettingsRouteTransition`，按页面深度做横向进出动画。
- 通知会话规则和屏蔽名单列表顶部提供 `公众号`、`群聊`、`好友`、`全部` 四个分类 Tab，顺序固定为公众号、群聊、好友、全部；红包适用聊天和收款适用聊天不显示公众号，只提供群聊、好友、全部；自动回复规则不显示分类 Tab。上述五类规则或会话列表以及改名、进退群监听群管理页都支持批量删除，分类页的全选只作用于当前分类和搜索结果，其余页面只作用于当前搜索结果，删除前统一二次确认。
- 主设置页使用本地 KSU 风格 `FloatingBottomBar`，三个页签为实用、娱乐、设置。实用和娱乐页签首页展示大分组入口，点击后进入二级菜单；页签顶部的全局搜索可按功能名、描述和分组搜索模块功能，搜索页顶部固定输入框和取消按钮，并保存真实搜索记录。从功能详情返回时先回到所属分组页或搜索页，再返回页签首页。
- 主页签归类当前固定为：
  - `实用`: 返回 `CATEGORY_PRACTICAL` 的 provider；返回 `CATEGORY_ENHANCE` 的 provider 显示在页内 `增强` 分组。
  - `娱乐`: 返回 `CATEGORY_ENTERTAINMENT` 的 provider。
  - `设置`: 全局 UI 设置和关于信息。
- 实用页签按聊天、语音、红包转账、增强、群组、朋友圈、美化、界面和杂项组织原生功能；娱乐页签按群聊、状态、聊天和抓包组织对应功能。功能目录与设置 provider 应保持一致。
- “快捷设置群聊标签”默认关闭。开启后，微信首页和聊天分组二级页的真实群聊长按菜单都会显示“群聊标签”，可多选已有模块群聊标签、移出全部标签，或新建标签并立即加入当前群聊；修改后会触发聊天分组同步，使按群聊标签配置的自动归拢及时更新。普通联系人、虚拟分组会话和其它非群聊条目不显示该菜单。
- `文字转语音播报` 位于 `增强` 分组，处理允许名单内收到的普通文字消息，并可单独开启默认关闭的 `播放语音消息`；允许名单支持好友和群聊且为空时不播报。文字消息可选择跟随系统默认或指定任一已安装的 TTS 引擎；引擎目录按实际声明 `android.intent.action.TTS_SERVICE` 的可用服务枚举，不强制厂商引擎提供额外元数据。跟随系统默认时，空包名初始化失败会依次尝试当前 Android 用户内可见的系统默认包名和其它已安装引擎，回退只在当前进程临时生效并在设置页显示实际引擎，不改写用户配置；手动指定引擎时不会静默切换。每次初始化都有超时保护，切换候选前会关闭旧实例并忽略迟到回调；微信分身所在 Android 用户看不到任何 TTS 服务时停止无效重试并记录明确原因，模块不能绕过 Android 跨用户权限绑定主用户的 TTS 服务。设置页通过所选引擎的 Android `Voice` API 读取播报角色，优先展示声明支持中文的角色，没有中文元数据时展示该引擎全部角色，并按稳定的 `Voice.name` 保存选择。角色影响所有 TTS 播报，切换引擎时会恢复为该引擎默认角色，旧角色不再提供时明确标为不可用且不会擅自改选。切换引擎时销毁旧实例并使用所选包名重新初始化，保留当前待播报文字，已卸载的历史选择在设置页明确标为不可用。微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的清单均声明了 `android.intent.action.TTS_SERVICE` 包可见性查询。语音消息不交给 TTS：收到 PB 消息后按 `talker + msgSvrId` 异步等待消息入库和本地语音文件落地，重试期间会周期性按本地消息 ID、会话内 `msgSvrId` 和必要时的全局 `msgSvrId` 刷新已落库消息，逐个验证去重后的候选文件名与实际路径，避免 PB 临时文件名阻止后续落库结果；确认文件存在后再通过微信原生 `SceneVoicePlayer` 播放 Silk/AMR 原语音；等待最多 60 秒，功能、语音开关、允许名单或免打扰状态失效时立即取消。原生播放器在七个版本中均以稳定日志和实例 `(String,boolean,boolean,int)->boolean` 播放签名定位，暂停、继续和停止入口一并缓存，不固定混淆类名。文字和原语音进入同一串行队列，不会同时发声；原生完成或错误回调缺失时按语音时长加宽限超时跳过，避免阻塞后续任务。`自定义播报内容` 沿用旧 `播报发送人` 配置键，使用单一模板和中文可点击变量，支持微信昵称、备注、群内昵称、群聊名称、会话名称、播报来源、消息正文、消息类型、语音时长、消息时间和发送者 ID；微信昵称读取原始 `nickname`，不使用会拼接备注的完整显示名。默认模板为 `{播报来源}{消息正文}`，播报来源在私聊生成“昵称发了一条消息说”，在群聊生成“群聊名称里的群内昵称说”；语音消息的正文变量为空，模板前导语与原语音连续入队，用户可用消息类型或语音时长变量自行补充。消息时间按独立格式设置渲染，格式无效时不保存并回退默认格式。默认开启的 `免打扰不播报` 同时读取私聊和群聊的微信原生会话免打扰状态；独立的定时免打扰支持跨午夜。音键控制仅在存在播报任务时接管按键：播放中按音量减暂停，暂停后再按音量减跳过当前消息，按音量加从暂停位置继续，空闲时保留微信原有音量行为；原语音使用微信播放器自身的暂停、继续和停止。微信前台通过按键分发 Hook 处理，切到后台后在播报和暂停期间通过临时 `MediaSession` 接收音量调节，并以系统媒体音量变化监听兼容不分发远程音量事件的系统；队列结束或功能关闭时立即释放会话和监听。功能关闭或模块销毁时停止 TTS 与原生语音并清空队列。
- 自定义播报模板的“发送者昵称”按钮插入 `{发送者昵称}`，并兼容手动填写的 `{微信昵称}`；两者都只读取原始微信昵称。模板还提供发送者微信号和会话 ID 变量，备注、群内昵称、群聊名称始终独立渲染。
- 模块内所有系统文件输入选择器统一使用 `*/*`，不按图片、视频、音频或其它 MIME 类型过滤文件列表；具体发送方式仍由用户在功能中选择的内容类型决定。系统分享 Intent 和创建输出文件使用的 MIME 保留实际业务类型，它们不属于输入文件列表过滤。
- 视频号媒体菜单兼容标准 `ContextMenu` 和微信自定义菜单对象，覆盖普通视频流、分享面板及作者主页二级视频菜单。菜单项优先调用微信原生带图标添加方法；`8.0.49` 的五参数入口最后一个状态参数必须传 `false`，避免新增项显示为灰色禁用，找不到原生入口时才回退模块位图图标或普通菜单添加方法。作者主页旧入口额外用 `ref_eid`、`tridot`、`delete`、`forward` 锚定 `onCreateMMMenu`；新版继续使用 `getCreateSecondMoreMenuListener: username=` 等现有锚点。定位缓存升级到新槽位后只重建一次，后续仍按微信 runtime key 复用。
- 点击视频号菜单时只从当前回调参数、点击监听对象的直属字段和一层 holder 字段提取媒体，不做无界对象图扫描，避免误复制或下载相邻视频。当前 `FinderItem` 的普通媒体 JSON 缺少 `media_cdn_info.pcdn_url` 时，会按相同下标读取 `getMediaExtList()` 并合并扩展媒体里的 PCDN 直链；扩展数据仍无直链时继续使用现有密链、密钥和详情补全流程。
- 视频号视频下载会从媒体 `spec` 中优先选择 `codingFormat=h265` 的规格，并把对应 `fileFormat` 作为 `X-snsvideoflag` 请求参数写入原始 CDN 密链后下载解密，避免 PCDN 或默认空规格返回 H.266/VVC 视频。没有 H.265 规格或原始密链不可用时保留原下载流程；规格字段已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`。
- 视频号聊天分享 XML 先隔离完整 `finderFeed` 节，再用 PullParser `nextText()` 直接读取 `mediaList/media` 叶子字段。`Xml.newPullParser()` 返回系统解析器，R8 不能改写 `org.xmlpull.v1` 公共接口名称；发布规则保留该接口 ABI。解析异常按 10 秒限频记录原始 Throwable、输入长度和内容哈希，不能再静默转换成 `null`；媒体入口、补齐成功、普通未命中和回调检测的临时诊断日志不保留。
- 娱乐页签内二级 `群聊` 分组包含实名尾巴、群昵称自定义颜色和群成员头衔；`状态` 分组包含解除状态词长度限制；`聊天` 分组包含修改聊天记录；`抓包` 分组包含 Protobuf 抓包/发包。
- `设置` 页签把全局 UI 选项存到 `Hchat_miuix_ui`：`floating_nav` 和 `glass_nav`。
- 模块自己的设置使用 `HchatStorage.preferences(context, name)`。日常读写由 FastKV 处理，路径在微信私有 `Hchat/` 目录，不在 Android `shared_prefs/`。
- FastKV 可能为同一个配置名创建 `.kva/.kvb` 文件。`HchatStorage` 不再迁移旧存储数据。功能代码不要直接实例化 FastKV。
- `glass_nav` 使用 compose-miuix-ui 的 `miuix-blur`（`rememberLayerBackdrop` / `layerBackdrop` / `drawBackdrop`），并配合本地 `lens(...)` / `vibrancy(...)` 辅助实现底栏液态玻璃效果。
- `glass_nav` 只能在 Android 13 及以上启用；低版本没有 `android.graphics.RuntimeShader`，设置页必须禁用该开关并回退普通底栏。
- 重要：backdrop 只能从页面内容层记录。底栏必须作为独立 overlay 画在 `layerBackdrop(...)` 容器外，否则 blur 目标可能记录自身并在微信宿主进程触发 native `libhwui` 崩溃。
- 底栏是从 KernelSU 的 KSU 风格液态底栏复制/适配而来，不是 Miuix `NavigationBar`。相关文件：
  - `ui/miuix/FloatingBottomBar.kt`
  - `ui/miuix/LiquidGlass.kt`
  - `ui/miuix/animation/DampedDragAnimation.kt`
  - `ui/miuix/animation/InteractiveHighlight.kt`
  - `ui/miuix/animation/DragGestureInspector.kt`
  - `ui/miuix/liquid/CombinedBackdrop.kt`
  - `ui/miuix/liquid/InnerShadow.kt`
- 不要把底栏替换成简单的 `textureBlur` 背景或手写白色半透明胶囊。KSU 风格行为依赖三层：普通内容、由 `tabsBackdrop` 捕获的隐藏主色 tint 内容、以及移动选中液态胶囊绘制的 `combinedBackdrop`。
- 可见底栏图标/文字保持 `onSurface`。主色/蓝色选中效果来自液态胶囊下方移动的隐藏 tint 内容层。如果单独按 `selectedIndex` 给可见 item 着色，慢速拖动时会因为颜色只在选择完成后变化而显得不对。
- 点击项必须调用 `FloatingBottomBar` 内容作用域提供的选择回调，并且只更新外部 `selectedTab`；外部选中索引是离散切换驱动胶囊位置的唯一入口，离散位置弹簧沿用 KSU 的 `press -> move -> release` 反馈；拖动中的连续位置与最终吸附也必须通过同一个互斥动画通道同步，不能让点击回调和外部同步同时启动位置动画，也不能用待确认标记跳过外部同步。用于 `tabsBackdrop` 的透明主色内容层只参与绘制，不能重复注册点击事件。不要绕过组件直接更新页签，也不要只把页签状态保存在底栏内部。
- 开启 `floating_nav` 时，底栏使用 intrinsic width 并居中悬浮。关闭时底栏必须 `fillMaxWidth()`，让三个 item 分散到整个底部。
- 除非有明确测量原因，否则保持 KSU blur 强度：外层/tint backdrop 使用 `blur(4.dp.toPx(), 4.dp.toPx())`；选中胶囊使用 `lens(...)`，不要叠加强模糊。更大的 blur 会冲淡图标和文字。
- 底栏图标是本地 `ImageVector` 定义，路径数据来自 AndroidX Material Rounded。微信内嵌 UI 不要使用 `vectorResource(R.drawable...)`；模块 drawable 资源可能按微信资源表解析并触发 `Resources$NotFoundException`。

新增设置页时：

1. 用 `registerSettingsProvider(...)` 注册元数据 provider。
2. 在 `FeatureSettingsPage(...)` 添加分支。
3. 使用 `PageScaffold`、`SettingsCard`、`SwitchRow`、`PopupChoiceRow`、`ActionRow`、`InputRow`、`NumberInputRow`、`ContactPickerPage` 和 `OptionPickerPage` 构建页面。
   `PageScaffold` 负责 Miuix 顶栏、内嵌 backdrop 和底栏管线。
4. 在任何 `picker` / `optionPicker` 分支前保存页面滚动状态：

```kotlin
val listState = rememberLazyListState()
val scrollBehavior = MiuixScrollBehavior()

picker?.let {
    ContactPickerPage(...)
    return
}

PageScaffold(...) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(...)
    ) {
        // 设置项
    }
}
```

5. 打开联系人选择或其它子页面的行使用 `ActionRow`。
6. 少量枚举/int 选项使用 `PopupChoiceRow` / `PopupChoice` 作为页内弹出选择菜单；大量、可搜索、多选或复杂选项才使用 `OptionPickerPage`，不要点击行后直接改值。
7. 好友/群聊选择使用 `ContactPickerPage`。除非共享选择器无法覆盖需求，否则不要在功能 UI 页面里直接查询联系人。
8. 文件/媒体选择使用 Android 系统文件管理器；选择结果需要普通文件路径时，应复制到模块私有缓存后再交给发送或运行时接口。

## 朋友圈底部详情

`朋友圈底部详情` 的入口是 `设置` -> `实用` -> `朋友圈` -> `朋友圈底部详情`，默认关闭。开启后，在发现页朋友圈的底部时间和好友个人主页的原生日期区域按同一自定义模板显示；功能只改时间详情展示，不改变朋友圈发布内容或原有可见范围设置。个人主页通过七个支持版本共有的 `SnsSelfAdapter` 条目读取和日期绑定入口取得当前 `SnsInfo`，避免按位置猜测或查询数据库；同一次绑定可能连续读取前一条、当前条和同组图片条目，因此运行时按 `createTime` 保存最近条目并与日期绑定参数精确匹配，不能依赖最后一次 `getItem()` 的返回值。新版本的 Flutter 朋友圈页无法承载该模板，因此功能开启时使用微信原生页面渲染，关闭后不改变微信原有页面选择。

格式设置包括：

- `文本格式` 默认值为 `${originalText} | ${time}`。留空保存时恢复该默认值；模板会替换已知变量，普通文本原样保留。
- `时间格式` 默认值为 `yyyy-MM-dd HH:mm:ss`，使用 Java `SimpleDateFormat` 规则，按设备当前时区格式化朋友圈创建时间。格式无效时不能保存；创建时间缺失或格式化失败时回退为微信原时间文本。
- 支持的变量为 `${originalText}`（微信原时间）、`${time}`（按时间格式生成的时间）、`${type}`（朋友圈中文类型）、`${snsId}`（朋友圈 ID，无符号十进制）和 `${userName}`（发布者 ID）。

文本格式输入框下方提供中文变量按钮：`微信原时间`、`自定义时间`、`朋友圈类型`、`朋友圈ID`、`发布者ID`。点击按钮会把对应的实际占位符插入当前光标位置，并替换当前选中文本，用户不需要手动记忆变量名。

`隐藏可见范围` 默认关闭。开启后只隐藏朋友圈底部的可见范围图标，不修改朋友圈的真实可见范围或权限判断。

多版本定位和缓存遵循统一规则：发现页时间文本方法通过 `getTimeString` 字符串锚点定位，可见范围方法通过 `getShowGroupEnable` 字符串锚点定位；个人主页日期绑定方法通过 `cerateTimeView` 与 `formatTimeInGrid` 定位，当前条目读取方法通过 `getItem` 与 `com.tencent.mm.plugin.sns.ui.SnsSelfAdapter` 定位，新版页面选择开关通过 `enableFlutterSNSPage` 与 `com.tencent.mm.plugin.sns.router.SnsRouter` 定位。每个候选都继续校验实例方法、参数、返回类型、继承关系和朋友圈数据字段等结构条件，不能写死任一微信版本的混淆类名或方法名。所有 descriptor 都必须通过 `DexMethodCache` 缓存，且每次读写缓存前实时生成 runtime key，至少区分微信版本、`versionCode`、`clientVersion`、热更新标识、APK 时间戳和 ClassLoader 指纹。发现页时间、可见范围和个人主页 Hook 使用独立的 `DexInstallScheduler` 任务，任一定位失败不会重复挂载或阻断其它 Hook。缓存命中时直接复用，缓存缺失、解析失败、微信升级/降级、热更新变化或 ClassLoader 变化时清理旧 descriptor 并按当前版本重新定位；定位和安装走后台流程，不阻塞微信启动。个人主页的日期绑定和条目读取入口已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`。

## 移除通话媒体限制

`call_media_limit` 默认关闭。开启后把微信 `DeviceOccupy` 的通话、语音、相机和小程序音视频占用检查结果改为 `false`，允许聊天中播放语音条、查看视频和打开拍摄。关闭后 Hook 保留安装，但每次调用都实时读取开关并执行微信原逻辑。

`call_ringtone_block` 放在聊天分组，提供 `屏蔽通话呼入铃声` 和 `屏蔽通话呼出铃声` 两个独立开关，默认均关闭，只阻止对应方向的微信语音/视频通话铃声启动，不改变系统音量、通话音频、语音消息或视频播放。运行时按铃声入口中的 `isOutCall` 区分呼入与呼出，同时覆盖旧 `VoIPAudioManager` 三参数入口、`VoipRingtoneLogic` / `VoIPMPRingtoneController` 五参数入口、CoreV2 原生回调，以及群聊多人通话独立使用的 `MultiTalkAudioManager` 单布尔方向入口；CoreV2 被屏蔽时仍调用同一原生确认方法，避免阻断通话建立流程。群聊入口通过 `MicroMsg.MT.MultiTalkAudioManager` 与两条 `requestAudioDeviceToStartRing` 日志定位路由方法，再从其唯一同类 `void(boolean)` 调用解析真实铃声入口，不写死混淆类名。上述入口已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`，定位结果写入运行时版本隔离的 `DexMethodCache`。

底层限制来自微信 `DeviceOccupy`：动态定位带有 `MicroMsg.DeviceOccupy` 标签的 `isMultiTalking`、`isCameraUsing`、`isVoiceUsing` 和 `checkAppBrandVoiceUsingAndShowToast isVoiceUsing:%b, isCameraUsing:%b` 方法，并从群通话检查方法的调用关系补出静态无参的原始群通话状态方法。功能开启期间这些方法始终返回 `false`；该层不依赖 `AudioManager` 或语音播放器状态，因此语音播放改变音频路由后不会使功能失效。

语音消息入口还会绕过 `DeviceOccupy`，直接派发 `VoipCheckIsDeviceUsingEvent` 并读取结果决定是否显示“正在语音通话”提示。功能动态定位聊天语音消息的这层检查方法，并用线程作用域把事件结果改为未占用；作用域退出后其它业务继续看到真实通话状态，不能全局清空事件结果，否则会影响发起新通话等流程。

不额外 Hook `SceneVoiceAudioManager` 或播放器 `setMute(boolean)`，避免播放器实例、音频焦点和通话状态切换产生相互竞争。DexKit 缓存 `DeviceOccupy` 占用方法组和语音消息通话检查方法，安装统一走 `DexInstallScheduler`。

以上 `DeviceOccupy` 方法组、语音消息直接事件检查和 `MsgQuoteItem` 参数形态已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`，运行时不能固定混淆类名或方法名。

## 指定骰子猜拳

`game_emoji_result` 位于实用页签的 `杂项` 分组，提供两个互斥模式：`使用固定结果` 按设置直接改写骰子点数和猜拳结果，`发送时选择` 在点击微信骰子或猜拳表情后用模块 Miuix 弹窗选择本次结果；两个模式都关闭时完全保留微信原逻辑。取消选择不会发送本次游戏表情，弹窗不可用时回退为微信原结果。

运行时复用公共表情 API 已定位的 `NetSceneUploadEmoji: msgId` 发送方法，只处理 `EmojiInfo` 中名称、协议内容或 md5 明确属于 `dice` / `jsb` 的对象。骰子按 `gameext type=2` 使用内容值 `4~9` 表示 `1~6` 点，猜拳按 `gameext type=1` 使用内容值 `1~3` 表示剪刀、石头、布；发送对象直接沿用微信本次调用的 `talker` 和其它参数，不额外推断当前会话。

发送方法已用 DexClub 横向确认：`8.0.49` 到 `8.0.76` 均为 `void(String, EmojiInfo, ...)`，后续参数数量和类型随版本变化但前两项稳定；`EmojiInfo` 的游戏目录 `50`、`field_md5`、`field_size`、`field_content`、`field_name` 等持久化字段，以及按 `jsb` / `dice` 名称读取内置资源的规则在 `8.0.49` 与 `8.0.76` 两端一致。功能不得写死 `hp.t`、`os1.h` 等单版本混淆类名。

## Release 构建

Release 构建必须使用现有 release 配置。Release 已开启 R8/minify；keep 规则按 LSPosed 入口、JNI 编解码和反射调用路径保留必要类型，不要为了让反编译输出更短而削弱这些规则。

Release 打包会排除协程调试探针和常见依赖元数据，例如 `META-INF/*.version`、`META-INF/**/LICENSE*` 和 `META-INF/**/NOTICE*`。不要为了删除 AGP 生成的 `META-INF/com/android/build/gradle/app-metadata.properties` 或 `META-INF/version-control-info.textproto` 去二次处理已签名 APK；如果当前 AGP 版本有稳定 DSL 开关，再使用 DSL 关闭。

任务是产出 release APK 时，不要跑 debug 构建。

当前构建栈：

- Gradle wrapper: 9.7.1
- Android Gradle Plugin: 9.4.1
- Kotlin Compose plugin: 2.4.20
- Compose plugin: 1.12.0
- Miuix UI: `top.yukonga.miuix.kmp:miuix-ui` 0.9.4 对应本地 `miuix-ui-android`、`miuix-core-android`、`miuix-preference-android`、`miuix-icons-android`、`miuix-squircle-android` AAR
- Miuix Nav: 0.9.4 本地 `miuix-nav-android` AAR；设置首页、搜索、分组、功能详情和插件页使用 `NavDisplay` 与内存型 `NavBackStack`，获得统一前进/返回转场、Entry 生命周期和预测返回，页面内部选择器仍使用现有嵌套返回注册表
- Miuix 0.9.4 交互组件：设置主页使用 `HorizontalPager`、`pagerGestureOverride` 和 `springAnimateToPage`；聊天分组编辑页使用可点击 `BreadcrumbBar`。
- Miuix blur/shader: 0.9.4 本地 AAR，用于 KSU 风格悬浮底栏玻璃效果；Android 13+ 的底栏外壳和交互镜像层使用 `progressiveTextureBlurEffect`，低版本继续走无模糊回退
- Miuix 0.9.4 的居中弹窗使用 `WindowDialog` 的 `largeScreen`、`maxWidth` 和 `cornerRadius`；下拉菜单迁移到 `WindowDropdownMenu` / `DropdownEntry` / `DropdownItem`
- 底栏图标：本地 `ImageVector` 路径数据来自 AndroidX Material Rounded。不要为了单个图标引入完整 `material-icons-extended` 依赖。

当前支持的宿主环境使用已安装的 Android SDK 与 Gradle wrapper。构建命令：

```sh
./gradlew :app:assembleRelease -x :app:checkReleaseAarMetadata \
  --no-daemon --no-watch-fs --max-workers=1
```

Release 输出为 `dist/Hchat-release-signed.apk`。构建后验证 APK、SHA-256 和签名。设置 `HCAT_APK_NAME` 可按需要指定本地文件名。

## 生命周期清理

`BaseFeature` 会自动清理：

- 通过 `registerSettingsProvider` 注册的 settings provider
- 通过 `subscribe` 注册的 EventBus 订阅
- 传给 `trackSubscription` 的 API 订阅

原始 Xposed Hook 应使用 `HookRegistry.get().hook(...)`，单独解除时使用 `HookRegistry.get().unhook(handle)`，同时释放 Xposed 注册和模块注册表中的强引用。脚本手动解除、关闭及加载失败清理均走该入口，避免反复重载保留旧回调及解释器。

普通功能的延迟任务、去重和限频统一使用 `WeChatApis.runtime().tasks()`，不要在功能内自建 timer。脚本 `delay()` 因需要按每次加载实例取消，使用独立的生命周期管理：主进程仍在主线程回调，小程序进程使用共享的两线程调度器，关闭、重载及加载失败取消该实例尚未执行的任务。每实例最多等待 128 项、每进程最多等待 512 项，超限跳过并限频记录；已开始执行的用户脚本不能被强制终止，旧实例不能继续追加延迟任务。

脚本普通消息分发使用容量 128 的单线程 FIFO 队列。满载丢弃新脚本事件并限频计数，不得使用 CallerRunsPolicy 将用户脚本转移到微信调用线程。无消费者时跳过包装和去重；最后消费者卸载时清空队列。入队时固定目标插件实例，执行及取得解释器锁后均检查实例身份，避免旧消息进入重载后的新解释器。

## 运行时设置

所有功能开关和用户可编辑设置在保存后必须立即生效。普通设置不要要求重启微信。

规则：

- 不要只在 `hookAll()`、`onInit()` 或 `onFeatureInstall()` 里读取一次设置，然后一直使用旧值。
- 如果功能为了性能缓存设置，必须在处理事件前刷新缓存，或在设置页保存时接收明确刷新信号。
- 保存设置时必须先写入值，再让运行时代码读取。下一步操作立即依赖新值时使用 `commit()`。
- 开关控件可以点击即保存，但保存按钮仍必须写入完整表单。
- 普通运行时设置包括启用开关、通知开关、自动回复、自动关闭、跳过自己、自动屏蔽新进群、延迟、模式、白名单/黑名单、关键词过滤、假红包选项和文本/媒体回复模板。红包助手总开关、自动关闭页面、通知、随机回复等开关默认关闭，必须由用户主动开启。
- 红包助手的模板和适用聊天属于运行时规则配置，保存后必须被下一条红包识别立即读取；不要只在启动时构造一次规则快照。
- 红包助手和自动收款的白名单/黑名单联系人选择支持好友、群聊和标签分组；标签分组只选择标签下好友，联系人行底部显示所属标签，并支持对当前筛选结果全选/取消全选。选择页保存时必须用当前勾选结果覆盖原名单，允许空选保存为空，不能只追加旧名单。
- DexKit 定位、Hook 安装和其它只在启动期决定的结构性能力仍可能需要重启，但普通功能行为不应该需要重启。

红包设置不要只在启动时刷新：

```text
sFastSkipSelf
sFastListMode
sFastKwMode
sFastKeywords
sFastMyWxid
```

应在红包识别/过滤前刷新这些值，或替换成保存时会刷新的运行时设置快照。

## 日志

红包临时诊断输出已关闭，不提供设置页日志入口。`RedPacketLogger` 丢弃普通状态、消息观察、规则过滤及正常领取过程日志；失败、缺失组件、不可用接口和异常继续通过 `HLog.e` 输出到 LSPosed，异常保留原始堆栈。已移除临时版本查询、消息计数和逐消息诊断，避免正常运行时额外开销。

运行时日志默认保持安静，只记录：

- 错误
- 缺失的类/方法
- 不可用的关键 API
- 明确开启的调试或冒烟测试输出

规则：

- 普通模式不要记录每个成功的 Hook/API 初始化。
- 菜单接管、标签栏挂载、防崩兜底安装成功、DexKit 空闲释放及正常登录/发包过程不输出运行日志；失败继续记录，功能与回调保持不变。
- DexKit 成功命中日志必须放在 `VERBOSE=false` 或冒烟/调试开关后面。
- 启动兼容性检查可以自动运行，但只能打印缺失项。
- 数据库变更等高频事件必须按表过滤并限频。
- 除非相关调试设置已开启，功能代码不要为普通业务流程调用 `XposedBridge.log(...)`。

## 群昵称自定义颜色

- `群昵称自定义颜色`位于娱乐页签的`群聊`分组，使用独立总开关、颜色和字重配置，不与实名尾字颜色互相覆盖。
- 颜色配置复用实名尾字的拾色器和格式，支持 `#RRGGBB`、`#AARRGGBB` 与起止色渐变；留空时保持微信原昵称颜色。字重范围为 `100..900`，按 100 进位规整。
- 昵称颜色、群成员头衔和实名尾字共用 `ChatNameDecorationLayout` 状态与同一个群昵称绑定 Hook。颜色只应用到基础群昵称文本，不能覆盖头衔或尾字 span，也不能再新增聊天 Adapter bind Hook。
- 群昵称绑定入口已横向确认微信 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76`；`8.0.76` 对应 `a0.o(g0, fd5.d, e9, String)`，仍直接读取 holder 的 `userTV`。

## 实名尾字

`实名尾字`在群聊昵称后追加缓存到的实名尾字，显示格式为：

```text
昵称(实名尾字)
```

显示页提供两个独立开关：

- `KEY_SHOW_GENDER`: 在尾字后追加性别。男、女、未知三种文案分别由 `KEY_GENDER_MALE_TEXT`、`KEY_GENDER_FEMALE_TEXT`、`KEY_GENDER_UNKNOWN_TEXT` 控制；未知文案留空时不显示。
- `KEY_SHOW_REGION`: 在尾字后追加地区，地区来自联系人资料原始省市文本，模块不做全称补全。
- `KEY_TAIL_COLOR`、`KEY_BRACKET_COLOR`、`KEY_GENDER_COLOR`、`KEY_REGION_COLOR`: 分别控制实名尾字、左右括号、性别、地区颜色。设置页提供 PS 风格拾色器和十六进制输入，颜色行标题右侧带 `单击` 提示；展开后可选择起始色/结束色，左侧色盘和右侧色相条都支持点击取色与滑动取色，值支持 `#RRGGBB`、`#AARRGGBB` 或 `#RRGGBB,#RRGGBB` 渐变，留空时跟随微信原昵称颜色。
- `KEY_TAIL_WEIGHT`、`KEY_BRACKET_WEIGHT`、`KEY_GENDER_WEIGHT`、`KEY_REGION_WEIGHT`: 分别控制实名尾字、左右括号、性别、地区粗细。设置值为 `100..900`，会按 100 进位规整；渲染层使用自绘 span 按真实绘制位置应用渐变和字重，Android 9 及以上按真实权重绘制，旧系统退化为普通/加粗两档。
- 如果开启性别或地区但本地资料缺失，实名尾字会后台预取联系人资料。预取优先调用微信原生 `MicroMsg.GetContactService`，失败时才回退到原生网络请求兜底；请求按联系人 ID 冷却，避免高频刷包。资料返回并落库后会重新刷新当前昵称。
- 实名尾字只读取 `/cgi-bin/mmpay-bin/beforetransfer` 回包 protobuf 字段 `4`，在 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的微信生成类中均对应响应对象字段 `f`；不能再遍历 `e/g/i` 等其它字符串字段猜姓名。`@openim` 企业联系人不支持该查询，服务端可能在字段 `4` 返回长纯数字业务标识，因此不发起实名查询；纯数字、联系人 ID、URL 和 OpenIM 的历史错误缓存会在读取时删除，不再渲染。
- 如果群成员昵称全是换行符或空白符，渲染时会尝试用联系人显示名兜底；仍取不到时使用不换行空格 `\u00A0` 占位，避免因为空昵称导致括号内尾字不显示。
- 已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 的 `GetContactService` 入口都存在，但混淆类名和方法名不同。兼容时只能按字符串锚点 `dkverify add Contact` / `[addContact] has consume` 和参数形态动态定位：`String,String` 或 `String,String,int`。拿服务实例时优先反查返回该服务类型的静态无参 getter，对齐资料页里的 `n8.a()` / `r7.a()` 这类包装入口。
- 已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74`、`8.0.76` 的群聊昵称填充都经过 `MicroMsg.ChattingItem` 的 `fillingUsername:need getKfInfo` 方法，签名固定为 `(holder, chattingContext, nativeMessage, memberId)`，最后一个 `String` 就是成员 ID；真实昵称控件是 holder 的 `userTV` 字段。实名尾字、昵称颜色和头衔渲染必须优先使用该方法传入的成员 ID，并以 holder 的 `userTV` 作为唯一昵称定位源；显示层统一在 `userTV` 内组合“头衔 / 昵称 / 尾字”，不要额外注册聊天适配器 bind hook 二次拆消息和猜 sender，也不要按 `brc`、`0x7f091065` 这类资源名或固定 ID 兜底查找昵称 View，资源 ID 在不同版本会漂移并可能指向其它控件。
- 已横向确认 `8.0.49` 的 `o0(holder, nick)` 和 `8.0.74` 的 `X(holder, nick)` 在昵称为空时会把 `userTV` 设为 `GONE`。红包、语音等卡片消息仍会复用同一个 `userTV` 字段；模块渲染头衔/实名尾字时如果发现原生昵称控件已隐藏，必须先按当前成员 ID 兜底恢复联系人显示名，再挂载头衔和尾字，同时对自己发送消息和无效成员清理旧装饰，避免 RecyclerView 复用残留。

两个开关都打开时格式为：

```text
昵称(实名尾字 男 浙江 杭州)
```

关闭开关后再次绑定昵称时会自动清理旧的追加后缀，避免重复显示。

## 群员头衔

`群员头衔`在群聊昵称左侧插入圆角头衔徽标。默认头衔来自群成员身份：群主、管理员、群员；点击聊天里的头衔可以给当前群的当前成员单独设置自定义头衔名称和颜色。

设置项：

- `KEY_ENABLE`: 开启群员头衔。
- `KEY_SHOW_MEMBER`: 是否显示普通群员；关闭后仍显示群主、管理员和已自定义头衔的成员。
- `KEY_OWNER_TITLE`、`KEY_ADMIN_TITLE`、`KEY_MEMBER_TITLE`: 分别控制群主、管理员、群员的默认头衔名称，留空时恢复默认文案。
- `KEY_OWNER_COLOR`、`KEY_ADMIN_COLOR`、`KEY_MEMBER_COLOR`、`KEY_CUSTOM_COLOR`: 分别控制群主、管理员、群员、自定义头衔默认颜色。默认色为群主黄色、管理员绿色、群员灰色。设置页取色盘可选择起始色/结束色；颜色支持单色 `#RRGGBB` / `#AARRGGBB`，也支持 `#RRGGBB,#RRGGBB` 渐变。
- 聊天内点击头衔弹出的 Miuix 弹窗可按当前群和当前成员单独设置头衔名称与颜色，颜色行默认只显示输入框和预览色块，点击颜色行后才展开同款取色盘，单人颜色同样支持渐变；内容超出屏幕时必须可向下滚动。弹窗只通过保存、重置、取消或空白关闭，不再拦截系统返回。
- 头衔、昵称、实名尾字统一塞回微信原生昵称 `userTV` 这一个 `TextView` 里显示，顺序为头衔、昵称、实名尾字；不要再包装 `userTV` 或向其父容器追加兄弟 View，避免红包、语音等特殊消息布局被微信后续 bind 逻辑重新定位后错位。头衔背景、头衔文字、实名尾字/性别/地区颜色必须用 span 自绘支持起始色/终止色渐变。昵称和尾字不能省略，过长时允许在同一个 `TextView` 内换到第二行或更多行完整显示。
- 群员头衔和实名尾字共用昵称装饰状态。任一功能显示或隐藏装饰前，如果 `userTV` 当前文本不是模块上次渲染结果，必须先把微信刚绑定的新文本同步为基础昵称，再组合头衔和尾字；不能让先执行的关闭态 Hook 用 RecyclerView 上一条消息缓存的基础昵称覆盖当前成员昵称。
- 头衔绑定成员 ID 时要沿用实名尾字的有效联系人 ID 校验规则，允许非 `wxid_` 开头的自定义微信号，但必须过滤纯 hex、长纯数字、URL、群 ID 和媒体消息里的 24~64 位客户端标识，避免媒体消息把头衔绑定到随机 ID 上；自己发送消息必须清理旧头衔，不显示群员身份。
- 头衔和左滑引用这类依赖聊天适配器 bind hook 的功能，安装时不能在定位失败后永久标记成功；缓存失效、微信刚 attach 完 ClassLoader 未稳定或 DexKit 暂时失败时，必须后台有限重试补装，hook 真正安装成功后才停止重试。

逆向依据：

- 已横向确认 `8.0.49`、`8.0.58`、`8.0.66`、`8.0.68`、`8.0.72`、`8.0.74` 的 `GroupAdminManagerUI` 都从 chatroom 对象成员列表读取管理员状态。
- 群主来自 `chatroom.roomowner`，实现应直接查询该列，不能依赖联系人表是否已经把该会话识别为群聊。
- 管理员来自 `chatroom.roomdata` protobuf：顶层字段 `1` 是成员子消息，成员字段 `1=userName`、字段 `2=displayName`、字段 `3=roomFlag`，`roomFlag & 0x800 != 0` 表示管理员。实现直接解析本地 `roomdata`，不写死微信混淆类名或方法名；读取 blob 时要兼容 `byte[]`、hex 字符串和带 `toByteArray/getBytes` 的包装对象。
- 微信升级、降级或热更新后的首次启动，`chatroom` 表可能短时间未就绪；字段探测和 `roomdata` 空结果不能永久缓存。头衔不应按消息逐条轮询身份数据，应订阅 `chatroom` 表变更，等 `roomowner` 或 `roomdata` 真正更新后刷新当前群可见头衔。

## 防撤回

防撤回提示模板支持 `{name}`、`{content}`、`{sendTime}` 和 `{recallTime}`，设置页分别显示为撤回者、文字内容、发送时间和撤回时间。发送时间取被撤回原消息的创建时间；撤回时间优先取微信撤回事件的服务端时间，无法解析时回退到本机收到事件的时间。模板包含任一时间变量时显示共用的时间格式输入，默认 `yyyy-MM-dd HH:mm:ss`；保存前按 `SimpleDateFormat` 校验，运行时遇到旧配置或异常格式则回退默认格式。

`防撤回`开启“保留自己撤回的内容”时不能只恢复 `message` 表字段。8.0.66/68/72/74 已逆向确认，撤回入口会先调用 `MsgProcessingManager` 清理 `talker_msgId` 对应的 `MsgProcessingInfo` 内存/MMKV 记录，图片、视频、语音等媒体查看依赖该处理信息；模块应动态定位该清理方法，并且只阻止已确认处于自己撤回链路的媒体消息清理处理信息，而不是拦截通用 `File.delete()`。

拦截自己撤回的消息存储更新时必须按目标方法返回类型返回兼容值：`int` 返回 `0`、`boolean` 返回 `false`、`void` 返回 `null`。不要对 primitive 返回方法直接 `setResult(null)`，否则 8.0.74 这类版本会在 `NetSceneRevokeMsg.onGYNetEnd` 拆箱时闪退。

8.0.49/58 没有 `MsgProcessingManager`，自己撤回回包会先调用旧版统一媒体清理入口，再更新 `message` 表。49 已确认 `NetSceneRevokeMsg.onGYNetEnd -> tj0.a9.e(k9,false)`，58 已确认 `NetSceneRevokeMsg.onGYNetEnd -> fq0.x8.e(q9,false)`；这类旧版入口按 `MicroMsg.NetSceneRevokeMsg` / `[oneliang][doSceneEnd.revokeMsg]` / `cannot find the msg:%d after revoke.` 动态定位，只拦调用栈来自 `NetSceneRevokeMsg.onGYNetEnd`、第二个参数为 `false`、消息是自己发送且类型为图片/语音/视频/表情的调用，避免影响普通删除消息。

## 兼容性自检

`WechatApiFeature` 初始化公共 API 后，会运行一个安静的兼容性自检。

自检只在被关注能力缺失时打印日志，日常使用不输出完整成功报告。

关注的能力包括：

- 运行时版本/指纹 API
- 消息观察/来源 API
- 文本发送 API
- 网络分发 API
- 数据库变更监听
- 群成员显示名方法
- 聊天页启动、Fragment 进入与退出方法
- 红包领取/打开 DexKit 目标

新增可复用微信 API 时，在目标 APK 验证完成后，也要把它加入 `WeChatDiagnosticsApi.buildCompatibilityIssues()`。

## 功能开发模板

新增功能时复制这个结构，不要把业务逻辑写到 `hooks/api` 或 `loader`。

代码语言原则：能用 Kotlin 写就尽量写 Kotlin；如果某段代码因为 Xposed、反射、DexKit、R8 或 Java 互调原因更适合 Java，就用 Java，不要硬转。

### Directory

简单功能使用单层目录：

```text
hooks/items/example/
  ExampleFeature.kt
  ExampleSettingsProvider.kt
  ExampleSettings.kt
```

复杂功能按职责拆子包，入口类保持很薄：

```text
hooks/items/example/core/
  ExampleFeature.kt            # 只注册设置、订阅、组装组件
  ExampleSettings.kt           # 只读配置
  ExampleState.kt              # 只放内存状态
  ExampleCoordinator.kt        # 串联业务流程
hooks/items/example/detect/    # 识别、解析、过滤
hooks/items/example/action/    # 执行动作，例如发送消息、网络请求
hooks/items/example/notify/    # Toast、系统通知、模板变量
```

不要把功能私有逻辑放进 `hooks/api`。只有多个功能都能复用、并且已经确认兼容的微信能力才放 API 层。

设置 UI 不放在功能包里。新增功能的设置页面统一写到 `ui/miuix/MiuixSettingsPage.kt`，并从 `FeatureSettingsPage(...)` 按 `featureId()` 分发。

主设置页已经内置 KSU 风格 Miuix 液态玻璃底部导航：实用、娱乐、插件、设置。不要为单个功能再做独立底部导航；功能详情页需要底部操作时使用统一的 `BottomActionBar`。

当前页签归类：

- `实用`: 红包、转账等日常工具，当前放 `AutoRedPacketFeature` 和 `AutoTransferFeature`。
- `娱乐`: 消息/玩法类功能，当前暂无默认功能。
- `设置`: 全局 UI 设置和关于信息。

支付相关功能不要在业务包里重复找微信混淆类。普通转账领取/退回使用 `WeChatApis.payment().transfers()`，消息识别和规则判断可以放在功能包内。

### Feature

```kotlin
package h.Hchat.hooks.items.example

import h.Hchat.event.Events
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext

class ExampleFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "示例功能"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ExampleSettingsProvider())
        subscribe(Events.MessageReceived::class.java) {
            // lightweight event handling
        }
    }

    @Throws(Throwable::class)
    override fun onFeatureInstall(context: FeatureContext) {
        if (!featureBoolean("enabled", true)) return
        if (WeChatApis.message().observe() != null && WeChatApis.message().hasObserve()) {
            trackSubscription(WeChatApis.message().observe().subscribe {
                // API subscription, auto cleanup by BaseFeature
            })
        }
    }

    companion object {
        const val ID = "example"
    }
}
```

### Settings Provider

Provider 只提供入口元信息，不打开页面。

```kotlin
package h.Hchat.hooks.items.example

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class ExampleSettingsProvider : SimpleFeatureSettingsProvider(
    ExampleFeature.ID,
    "示例功能",
    "一句话说明",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
```

不要实现或恢复 `showDetail(Context)`。不要在 Provider 中创建 `Dialog`、`AlertDialog`、`Activity` 或自定义悬浮 View。Provider 只保留 `featureId/title/subtitle/category`，不要重新加图标、颜色或旧 `UIEntry`。

分类规则：

- `FeatureSettingsProvider.CATEGORY_PRACTICAL`: 放到 `实用`
- `FeatureSettingsProvider.CATEGORY_ENTERTAINMENT`: 放到 `娱乐`
- `FeatureSettingsProvider.CATEGORY_ENHANCE`: 放到 `插件`

三参数 `SimpleFeatureSettingsProvider(featureId, title, subtitle)` 默认放到 `插件`。不要在 `MiuixSettingsPage` 主页面里按功能 ID 写分组判断。

### Miuix Settings Page

在 `app/src/main/java/h/Hchat/ui/miuix/MiuixSettingsPage.kt` 的 `FeatureSettingsPage(...)` 添加分支：

```kotlin
@Composable
private fun FeatureSettingsPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    when (provider.featureId()) {
        ExampleFeature.ID -> ExampleMiuixPage(context, provider, onBack)
        else -> UnsupportedFeaturePage(provider, onBack)
    }
}
```

页面模板：

```kotlin
@Composable
private fun ExampleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, "example_settings") }
    var optionPicker by remember { mutableStateOf<OptionPickerRequest?>(null) }
    var picker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    optionPicker?.let { request ->
        OptionPickerPage(
            request = request,
            onBack = { optionPicker = null },
            onSelected = { selected ->
                request.onSelected(selected)
                optionPicker = null
            }
        )
        return
    }

    picker?.let { request ->
        ContactPickerPage(
            context = context,
            request = request,
            onBack = { picker = null },
            onConfirm = { selected ->
                request.onValue(selected.joinToString("|") { it.id })
                picker = null
            }
        )
        return
    }

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    // write full form values here
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "功能") }
            item {
                SettingsCard {
                    SwitchRow(sp, "enabled", "启用", "开启示例功能", true)
                    InsetDivider()
                    OptionRow(
                        sp = sp,
                        key = "mode",
                        title = "模式",
                        options = optionItems("模式一" to 0, "模式二" to 1),
                        defaultValue = 0,
                        openPicker = { optionPicker = it }
                    )
                    InsetDivider()
                    ActionRow("选择联系人", "从好友或群聊列表选择") {
                        picker = ContactPickerRequest(
                            title = "选择联系人",
                            mode = ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = "",
                            onValue = { value ->
                                sp.edit().putString("contacts", value).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}
```

UI 规则：

- 使用 `PageScaffold` + Compose `LazyColumn` + Miuix `SettingsCard` / rows。`PageScaffold` 负责 Miuix top app bar、backdrop 和 bottom bar。
- 需要二级选择的配置用 `OptionRow`，不要点击一行就循环切换值。
- 好友/群聊选择用 `ContactPickerPage`，它已经处理头像、头像进程内缓存、右侧选中框和完整列表点击。
- 会跳转或选择的行使用现有 `ActionRow` / `OptionRow` / `ContactRow`，不要直接写普通 `Modifier.clickable`。
- 所有页面都要给 `LazyColumn` 传 `rememberLazyListState()`，返回二级菜单时才能保持滚动位置。
- `LazyColumn` 要加 `.nestedScroll(scrollBehavior.nestedScrollConnection)`，保证 Miuix 顶栏滚动行为一致。
- 主设置页的 设置 tab 已有 `悬浮底栏` 和 `液态玻璃` 开关，值存在 `Hchat_miuix_ui`。不要在功能页重复做这些全局 UI 设置。
- 新增功能配置统一使用 `HchatStorage.preferences(context, name)` 或 `ConfigStore`，文件位于微信私有目录 `Hchat/`，日常读写由 FastKV 负责。不要直接 `context.getSharedPreferences(...)` 或自行实例化 FastKV 写模块配置。
- 主底栏实现位于 `ui/miuix/FloatingBottomBar.kt` 及其 `animation/`、`liquid/` 辅助文件。它使用 KSU 风格的多层 backdrop：普通内容层、隐藏主色 tint 层、移动选中液态胶囊。新增功能页不要改这套底栏状态或颜色机制。
- 底栏图标是本地 `ImageVector`，路径来自 AndroidX Material Rounded。不要在微信内嵌 Compose UI 中使用 `vectorResource(R.drawable...)`。
- 主功能列表不显示 Provider 图标，保持普通设置列表样式。图标只保留在底部导航。
- 液态玻璃使用 compose-miuix-ui 的 `miuix-blur`。不要在功能页单独创建 blur/backdrop；统一复用 `PageScaffold` 的结构。
- 液态玻璃只支持 Android 13 及以上；低版本必须回退普通底栏，不能构造 `RuntimeShader` 相关效果。
- `layerBackdrop(...)` 只能包住页面内容层，底栏必须作为 overlay 放在内容层外。底栏玻璃由 `FloatingBottomBar` 内部的 `drawBackdrop(...)` / `lens(...)` 处理。不要把底栏放进被记录的 backdrop 层里。
- 不要新增 Dialog，不要启动模块 Activity，不要加右上角关闭按钮。

### Register

Add one line in `FeatureRegistry`:

```kotlin
.register(ExampleFeature())
```

### Common Patterns

订阅消息：

```kotlin
trackSubscription(WeChatApis.message().observe().subscribe { message ->
    if (message.isText()) {
        val talker = message.talker
        val sender = message.sender
        val content = message.content
    }
})
```

延迟、限频、防重复：

```kotlin
WeChatApis.runtime().tasks().runOnMainDelayed("example:$id", 1000) {
    // delayed work
}

WeChatApis.runtime().tasks().runOnce("example_once:$id") {
    // only once per key
}

WeChatApis.runtime().tasks().runThrottled("example_rate:$talker", 3000) {
    // rate limited per talker
}
```

安装 Xposed Hook：

```kotlin
HookRegistry.get().hook(method, object : XC_MethodHook() {
    override fun afterHookedMethod(param: MethodHookParam) {
        // hook body
    }
})
```

需要微信内部类：

```text
1. 先用 DexKit/DexClub 在目标版本确认类、方法、构造器。
2. 把解析结果放进 DexFinder，并加版本/热更新缓存。
3. 业务功能只消费 DexFinder 或 WeChatApis，不散落 findClass。
4. 反射查找、类加载、实例创建和方法调用必须走 h.Hchat.utils.KavaReflector。
5. 未确认的能力只写 supports/isAvailable，不写猜测实现。
```

KavaRef 约定：

```kotlin
val ctor = KavaReflector.findConstructor(clazz, String::class.java)
val instance = KavaReflector.newInstance(ctor, value)
val field = KavaReflector.readField(instance, "fieldName")
```

不要在新功能里直接散落 `getDeclared*`、`setAccessible`、`Constructor.newInstance`、`Class.forName` 或 `Method.invoke`。如果必须保留原始 `Method` / `Field` / `Constructor` 给 Xposed 或 DexKit，也必须由 `KavaReflector` 获取。

`h/Hchat/compat/kavaref/**` 是 KavaRef core 的本地兼容层。目录在 Hchat 下，文件内部包名仍是 `com.highcapable.kavaref...`，不要改包名。

运行时设置实时生效：

```kotlin
// Good: read current value when handling the event.
if (!settings.getBoolean(ExampleSettings.KEY_ENABLE, true)) return

// Good: if cached, refresh before event processing.
settingsSnapshot.refresh()
if (settingsSnapshot.skipCurrentEvent()) return

// Good: settings page writes immediately when later runtime code depends on it.
sp.edit().putBoolean(ExampleSettings.KEY_ENABLE, enabled).commit()
```

不要这样写：

```kotlin
// Bad: this value becomes stale after user saves settings.
private var enabled = false

fun hookAll() {
    enabled = settings.getBoolean(ExampleSettings.KEY_ENABLE, true)
}

fun onEvent() {
    if (!enabled) return
}
```

### Rules

- Use `BaseFeature` for lifecycle.
- Use `registerSettingsProvider`, `subscribe`, and `trackSubscription` for cleanup.
- Use `WeChatApis` before adding new DexKit logic.
- Put new DexKit results into `DexFinder` only after verifying target WeChat versions by reverse engineering.
- 新增反射代码使用 `KavaReflector`，不要直接散落原始 Java 反射。
- Runtime logs should be quiet by default. Log failures, missing classes, or explicit debug output only.
- Keep feature entry classes small. Put parsing, action, notification, and state into separate classes.
- Do not use WA/WAuxiliary API.
- Do not use Dialog/AlertDialog/module Activity for settings. All settings pages must be embedded Miuix pages under `MiuixSettingsPage.kt`.
- Settings providers are metadata only. Do not add `showDetail(Context)`.
- Use shared Miuix rows/pickers so tap handling, scroll-position retention, contact avatars, and right-side selection marks stay consistent.
- All switches and editable settings must take effect immediately after saving. Do not require restarting WeChat for normal settings.
- If settings are cached, refresh the cache before processing events or when the settings page saves.
- If a WeChat internal detail is uncertain, reverse engineer first. Do not guess class names, method names, fields, database tables, intent extras, or request parameters.
- Put every new file in the correct layer. Feature-specific code belongs in `hooks/items/<feature>/**`; only reusable verified WeChat APIs belong in `hooks/api/**`.

## 悬浮菜单方向与消息标签扩展（2026-09-11）

- 悬浮快捷菜单在原有向上、向下之外支持向左、向右展开，配置值为 `left` / `right`。左右方向使用横向滚动容器，保留三种显示模式；展开/收起动画沿横轴，展开时拖动仍保持菜单跟随。靠近屏幕边缘时入口会向内避让，并保存调整后位置。
- 冷启动时，在协议已通过的 Application 初始化阶段提前注册原生 Activity 生命周期回调，免于等待完整 DexKit 初始化；恢复页面时立即挂载并以 0/80/250 ms 有限补偿，暂停、关闭和销毁时取消过期补偿。
- 消息详情的 `${type}` 使用 `MessageTypeLabels` 细分卡片类型，涵盖文件、音乐、聊天记录、小程序、引用回复、红包、转账、视频号、接龙等。只解析外层 `appmsg` 的直接子节点 `type`，不把引用/聊天记录内的类型作为外层类型；未知类型保留编号，损坏 XML 回退为通用标签。
- 新增可插入变量：`${appType}` 卡片子类型编号（非卡片或未识别时为空）、`${baseType}` 归一化基础类型、`${direction}` 收到/发出、`${talker}` 会话 ID、`${createTime}` 原始毫秒时间戳（无有效值时为空）、`${rawAtUserList}` 原始艾特名单。既有变量和用户模板保持兼容，正文按所需变量读取。
- 依据：`com.qechat.wechat` 1.0.0 debug APK（SHA-256 `7c22adb3d1aa444aa7088676ada05b63d476b64719a49556e15abc646b84afce`）中 `HchatExtraHooker.detailedMessageTypeLabel` 的 DexClub Java 导出与对应本地源码。分类扩展只消费已有消息模型，不新增微信 Hook、表字段或 DexKit 定位；未完成多版本真机验证。
- 本轮分类检查覆盖文件、红包、转账、小程序、引用回复、嵌套类型、群聊正文前缀、未知编号、视频号、拍一拍、损坏 XML 和拒绝 DTD，共 17 个样例。界面触摸与边缘布局仍需设备验证。
- 本地测试包通过临时 Gradle init script 使用 Release 编译模式、关闭 R8 和资源压缩、改用本机调试证书签名。该脚本不改仓库发布配置；测试包名 `Hchat-test-signed.apk`，不等同于官方签名发布包。此方式避开现有单文件设置页在 Debug Compose 编译中出现的 `ClassTooLargeException`。

## 启动加载优化（2026-09-11）

版本指纹仍在每次定位前实时计算，但 WeChatVersionApi 按原优先级逐项判空读取 BuildConfig、加载器信息和 Tinker 文件，仅信息不足时访问后备来源，避免 Java 可变参数提前求值造成多次目录扫描。没有新增 TTL 或永久运行时键缓存。

DexFinder 将 Protobuf 基类描述与其它类一起保存到现有版本/热更新/加载器指纹限定的缓存；旧缓存没有该项时补定位一次，之后复用。公共 API 缓存命中后的其它补定位保持执行。

NativeLibraryLoader 只记录同一模块 ClassLoader 下已经成功加载的库；后续调用跳过 APK 路径查找、ZIP 打开、跨进程文件锁和目录清理，失败及异常不缓存，仍可重试。ARM 32/64 位选择与原提取校验流程保持一致。


平板模式的缓存 Hook 不使用 DexKit，先在共享 DexKit 串行门外尝试安装，命中即返回；缓存缺失时仍进入原串行门并复查，再按原阶段定位，保留早期安装时序。详见 STARTUP_REVIEW_2026-09-11.md。

### 悬浮快捷菜单响应优化（2026-09-11）

悬浮快捷菜单在初始化及快捷项变更时更新启用项快照，展开时不再反复解析配置；自定义图标在独立后台队列按需加载及预取，以 4 MB 内存缓存复用，解码结果最大边长 256 像素，待办不超过 64 项。读取期间先显示默认图标，深色图标选择与回退规则保持不变。同路径替换、删除、关闭功能会使图标缓存及待执行结果失效。菜单直接在当前窗口尺寸内测量定位，取消滚动容器对子项按压反馈的等待；移除菜单后在下一帧遍历完成后打开页面，切后台或卸载会取消待执行跳转。生产图标加载器回归可通过 scripts/run_floating_icon_tests.cjs 执行；未进行真机帧时间测量。


## 消息显示时间：排除系统提示与通话记录（2026-09-24）

- “消息显示时间”仅为普通聊天消息添加 Hchat 标签；邀请入群等系统提示、拍一拍、撤回提示和通话记录不再添加该标签。微信原有的提示内容、时间分隔和消息数据保持不变；普通文字提及“撤回”“拍一拍”“通话”等词不会被过滤。
- `MessageDetailsVisibility` 按完整消息类型判断，在读取详情、查找时间控件和布局锚点之前跳过排除项。统一插入入口也执行相同判断，覆盖设置刷新、布局恢复和挂载回调；排除时取消延迟任务、使旧绑定失效、移除 Hchat 标签并恢复头像预留间距，不进入布局失败重试。删除原来专门为系统/拍一拍行定位居中时间的分支及资源 ID 缓存。
- DexClub 静态核验的系统类型并集为 `10000, 10002, 570425393, 64, 603979825, 889192497, 922746929, 268445456, 268445458, 285222674, -1879048191, 1077936177`。其中 `285222674` 自本次核验的 8.0.66 起出现在系统谓词；其余在全部八版一致。使用精确整数比较，不将低位相同的普通卡片一起排除。
- 系统谓词：8.0.49 `storage.k9.K1()`、8.0.58 `storage.q9.b2()`、8.0.66 `storage.f8.h2()`、8.0.68 `storage.f8.i2()`、8.0.72/74 `storage.f9.j2()`、8.0.76/77 `storage.e9.j2()`，类名前缀均为 `com.tencent.mm`，返回 `boolean`。八版原生通话谓词均核验出 `50/53` 与 `52`；同时沿用公共消息模型及消息文本颜色功能已记录的 `1000052/1000053` 语音/视频通话类型。未经本次确认的 `51` 不额外扩入过滤范围。
- `node scripts/run_message_type_labels_tests.cjs` 覆盖系统/拍一拍/通话排除，普通文字、语音、图片、卡片、高位消息、有符号礼物及未知类型保留，共 96 项断言通过。本次未运行 Gradle、未打包，真实聊天列表与滚动复用仍需设备验证。

## 语音转发与静默红包兼容（8.0.77 / 8.0.78）

微信启用新语音文件存储后，消息的旧 `imgPath` 可以为空。聊天语音转发与通用转发快照优先把已有原生消息交给 `WeChatVoiceApi.resolvePath(nativeMessage, fileName)`，由微信服务解析新旧存储路径；旧入口仍用于没有新服务的版本。定位使用 `[f11]getVoiceFullPath, businessType: ` 锚点和实例 `(消息对象,String,boolean)->String` 签名，结果进入现有微信版本、热更新及 ClassLoader 指纹缓存；旧版锚点不存在也记录为已检查，缓存描述符失效则重新定位。

8.0.78 的完成录音方法增加第五个 `msgSource` 参数。公共语音发送同时支持三、四、五参数版本，新增参数按微信自身复制语音调用传 `null`。新增路径解析与完成语音记录的异常记录版本和目标签名；转发结果以实际发送返回值为准，不能仅凭网络队列已就绪报告成功。

DexClub 横向核验如下，表内混淆名称仅作 APK 证据，运行时代码使用动态定位：

| 微信版本 | 完成录音参数数 | 按原生消息取语音路径 | 普通收包 / 拆包类 |
| --- | --- | --- | --- |
| 8.0.49 | 3 | 锚点未命中，使用旧路径 | `m5 / g5` |
| 8.0.58 | 4 | 锚点未命中，使用旧路径 | `t5 / n5` |
| 8.0.66 | 4 | `c50.b.ai` | `i6 / c6` |
| 8.0.68 | 4 | `z50.b.Fh` | `i6 / c6` |
| 8.0.72 | 4 | `m80.b.Ui` | `i6 / c6` |
| 8.0.74 | 4 | `k90.b.yj` | `l6 / f6` |
| 8.0.76 | 4 | `h90.b.Lj` | `l6 / f6` |
| 8.0.77 | 4 | `za0.b.tj` | `n6 / h6` |
| 8.0.78 | 5 | `wb0.b.Dj` | `n6 / h6` |

红包类名前缀均为 `com.tencent.mm.plugin.luckymoney.model`。九版普通请求都保留 `/cgi-bin/mmpay-bin/receivewxhb`、`/cgi-bin/mmpay-bin/openwxhb` 及 `onGYNetEnd(int,String,JSONObject)`；原生收包请求持有红包 ID，回调本身不要求 JSON 再回显该 ID。企业微信联系人/群聊即使携带 `sceneid=1002` 等非 `1005` 值，也按会话后缀 `@openim` / `@im.chatroom` 或明确的 `union_source`、企业微信场景标记走 Union 收包/拆包请求，不能只用固定 sceneid 判断；普通微信群中出现企业微信发送者不能单独触发 Union。静默处理按模块登记的请求对象关联，响应带 `sendId/sendid` 时交叉校验；非零收包错误码不触发拆包。收包与拆包回调均需原子认领，避免重复发包或重复统计。拆包重试保留实际选中请求的会话和原生请求，成功后释放原生请求并取消重试，不影响后续通知与回复信息。Union 类和网络队列各自维护定位版本；同一微信版本命中旧缓存时只重定位这两类红包依赖，避免继续使用失效的队列类名。队列反射调用异常或返回空值视为发包失败，交给收包/拆包重试，不得进入等待回调状态。

`node scripts/run_voice_compat_tests.cjs` 和 `node scripts/run_redpacket_silent_tests.cjs` 分别验证实际语音 API 与静默红包处理器；后者可通过 `JSON_JAR` 指定本机的 `org.json` JAR。APK 静态核验和独立 JVM 回归不等于设备验证，仍需安装后验证实际语音收发与红包响应；未取得反馈设备响应样本，不能断定该次红包失败一定由缺少 ID 导致。

## 标签分组（2026-09-29）

- “增强分组”下新增首页顶栏标签分组，内置全部、未读、群聊、私聊、公众号和自定义会话类型；自定义标签使用现有会话选择器保存真实会话 ID，最多保留 20 个标签。每个标签可选择只显示名称、只显示图标或图标与名称，并支持符号和系统图片图标。
- 标签栏挂载在首页所属控制器的 `id/jlt` 内容层，保留原生首页根视图和标题层级；查询 Hook 安装失败时仍显示标签栏，筛选能力在 Hook 补装成功后启用。标签只改写首页查询投影，不修改 `rconversation.parentRef`、公众号消息记录或聊天数据。旧版首页的根查询和新版分页对象都在查询构造阶段追加条件，避免分页后过滤造成漏会话；查询重写会跟随 `rconversation` 的表别名，并兼容没有 `WHERE` 的原生查询，同时保留排序、分页和尾部子句。切换标签后的刷新合并为短时间内一次。未读条件同时检查普通未读和免打扰未读，群聊识别 `@chatroom` / `@im.chatroom`，公众号识别 `gh_` 与 `rcontact.verifyFlag`。
- 首页 UI Hook 在主进程 `Application.onCreate` 完成后、后台 `Hchat-Init` 启动前注册，不等待完整 DexKit 初始化；完整功能初始化后为已登记首页加载配置和渲染标签。`getLayoutView()` 返回时根尚未加入控制器则延后挂载，返回首页时按 `MainUI` 的唯一 `MainUIView` 类型实例字段读取现有根视图并补挂。重复恢复不重复添加，根重建或迁移到另一内容层时清理旧标签。不能为了补挂再次调用 `getLayoutView()`，它会新建首页；也不能把旧根写回新一次布局的返回值。九版均具体实现 `getLayoutView/onResume/onDestroy`，8.0.78 的根字段为 `H`、其余八个目标版本为 `G`；运行时只按字段类型定位。
- 标签栏位置修复（2026-10-10）：沿当前 `MainUIView` 的父链寻找 `id/jlt`，避免命中其它页面的内容层或任意中间 `FrameLayout`；该层位于可选 bounce 包装之外。按原生 `id/ei` 标题的窗口坐标下沿放置标签，保留标题下拉时的 translation，转换到父层坐标时处理父层滚动。标签先以 `INVISIBLE` 完成测量，标题、容器或列表未就绪时不显示；给列表预留空间后等待下一次布局再显示，避免首帧盖住状态栏或第一条会话。下拉时跟随标题，超出内容层显示范围时隐藏，返回后恢复，不在小程序页顶部留下固定标签。
- 只给当前 `ConversationListView` 或 `com.tencent.mm.ui.conversation.recycler.ConversationRecyclerView` 增加标签实测高度的 topMargin，不移动 status mask，不修改原生 `ALIGN_PARENT_TOP/BELOW` 规则，也不创建另一套标题。列表替换、标签实测高度变化或原生 margin 重算时重新计算预留值，不能累计；禁用、销毁和更换列表时恢复模块增加的空间，原生刚写入的新 margin 不被旧值覆盖。模块不写 TaskBar header 和 scrollOffset 字段；原生 header 仍会按调整后的列表可用高度重新测量，不保证下拉内部坐标完全不变。
- 几何监听只在启用且首页根已 attach 时注册。全局布局回调仅标记 dirty，需要更新时只检查 `MainUIView` 的直接子节点；标题引用缓存失效时才重新定位。普通 pre-draw 使用缓存视图及两个复用坐标数组，按需改标签 translation/可见性，不遍历视图树、解码图片、查询数据库或反复写 LayoutParams。禁用、根 detach 和销毁时移除监听，重新 attach 后恢复；坐标监听异常走 `HLog.e` 并关闭该标签栏。
- 图标导入在后台线程缩放到 128 像素以内，并使用 2 MB LRU 缓存；顶栏使用横向滚动容器，不给会话行增加绑定监听，保证滚动路径不执行图片解码或 SQL 编译。账号变化时清空当前标签选择，全部标签始终保留为返回原生首页的入口。
- DexClub 横向逆向确认 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76、8.0.77、8.0.78 首页均存在 `SelectSql` 分页构造路径；8.0.78 对应构造器为 `uf5.l0.<init>(String, String[], long, boolean, boolean)`，运行时代码通过 DexKit 类字符串定位并按完整构造签名缓存，不写死混淆类名。独立回归使用 `node scripts/run_conversation_tabs_tests.cjs`，覆盖默认配置、账号会话清洗、分类匹配、SQL 转义和分页尾部保留；未进行 Gradle 构建和设备帧时间测量。
- 挂载时序与坐标回归使用 `node scripts/run_conversation_tabs_lifecycle_tests.cjs`，提取并编译生产初始化、Hook、固定层挂载和几何更新方法，23 个场景、156 项检查通过，覆盖挂载、下拉坐标、空间恢复、列表替换、监听清理及视图持有 Host 的清理，以及连续 120 帧普通滚动不增加资源查找、视图扫描或布局请求。替身区分 measuredHeight 与布局 height，窗口坐标包含自身及祖先 translation 并扣除父层 scroll；首条会话空间检查使用明确标注的合成原生 header 基线。缺失恢复挂载、复用旧根、标签放在顶部、误移 status mask 四个故障变体均成功编译并被运行时检查检出。此回归不代替 Android 实际布局测量或安装后的显示/筛选验证。

本次 DexClub 核验的标题与页面内容资源如下。资源名在九版一致，数值不同，运行时用微信资源按名称解析，不新增 DexKit 查询：

| 微信版本 | 原生标题 `id/ei` | 页面内容层 `id/jlt` | Controller 初始化方法 |
| --- | --- | --- | --- |
| 8.0.49 | `0x7f0900cb` | `0x7f093bff` | `com.tencent.mm.ui.ea.c0` |
| 8.0.58 | `0x7f0900d5` | `0x7f0940b9` | `com.tencent.mm.ui.wa.c0` |
| 8.0.66 | `0x7f0900d7` | `0x7f0943fe` | `com.tencent.mm.ui.x9.f0` |
| 8.0.68 | `0x7f0900d7` | `0x7f09455f` | `com.tencent.mm.ui.x9.e0` |
| 8.0.72 | `0x7f0900d6` | `0x7f094678` | `com.tencent.mm.ui.ha.e0` |
| 8.0.74 | `0x7f0900d5` | `0x7f09474a` | `com.tencent.mm.ui.ga.e0` |
| 8.0.76 | `0x7f0900d5` | `0x7f0947dc` | `com.tencent.mm.ui.ga.e0` |
| 8.0.77 | `0x7f0900d5` | `0x7f094919` | `com.tencent.mm.ui.ga.e0` |
| 8.0.78 | `0x7f0a00d4` | `0x7f0a49b0` | `com.tencent.mm.ui.ha.e0` |

九版 Controller 的默认 `layout/bxp` 都为页面实例单独 inflate，`id/jlt` 是该布局根，控制器按 `FrameLayout` 使用，再加入本页内容或 bounce 包装。49/58 根类型为 `LayoutListenerView`，66～78 为 `FrostedContentView`；不能从整个 Activity 搜 `jlt` 后假定是首页。九版首页 `layout/bvn` 均以 `MainUIView(RelativeLayout)` 为根，列表和顶部装饰使用 `ALIGN_PARENT_TOP`。8.0.78 `MainUI.w0` 可将旧列表替换为 RecyclerView 并复用旧 LayoutParams，因此恢复旧列表空间后再给新列表添加预留高度。

## 分组页面与后台任务的内存清理

- 聊天分组弹窗和原生长按绑定采用弱值索引，活跃弹窗句柄由自身视图持有；菜单目标弱引用 Activity/Fragment。功能销毁关闭分组弹窗并恢复仍属于模块的长按监听器，不覆盖其它模块后来安装的监听器。
- 分组菜单后台操作使用 `ConversationGroupTask`：一个执行线程、最多八个排队任务，执行中及等待主线程/下一帧交付合计最多九项。取消、页面销毁及功能销毁释放页面回调、结果和加载层，移除排队任务；后台只携带业务数据和 ApplicationContext。不可中断的宿主调用仍可能运行至返回，但取消后不再交付结果或继续批量操作。
- 标签栏由视图持有 Host，全局仅保留弱索引；重复配置重载合并为一项待执行任务和一份待主线程交付结果。旧图标解码、安装回调和销毁回调按代次失效，共享首页刷新也合并调度。
- 分组同步使用 ApplicationContext、合并请求和代次校验。销毁不提前释放仍运行任务的调度标记；旧任务不能回填分组快照、头像缓存或会话存储对象，重装后的同步请求仍会执行。
- 已见群聊历史和自定义排序是业务数据，不按运行时缓存容量任意截断。限频失败日志仍可按容量控制；不能把业务数据量增长直接称为内存泄漏。
- 回归入口：`run_conversation_group_ui_memory_tests.cjs`、`run_conversation_group_task_memory_tests.cjs`、`run_conversation_group_sync_memory_tests.cjs`、`run_conversation_group_store_memory_tests.cjs`、`run_conversation_tabs_lifecycle_tests.cjs`。脚本位于 `scripts/`，用 Node 执行；涉及 JSON 时可通过 `JSON_JAR` 指定依赖。测试使用生产类或逐字提取的生产方法与 Android/微信边界桩，不替代完整 APK 构建、实机堆分析或全局热重载验证。


### 文件请求与全量销毁的后续清理

- 分组文档导入导出及发送文件选择器使用 `ConversationGroupFileRequest`，等待选择、后台执行、排队与待主线程交付合计最多九项，后台只有一个执行线程。文件读写和文件名查询仅持有 ApplicationContext，页面销毁或功能销毁会清空页面回调、结果和待执行工作。功能销毁接入 `cancelPendingDocuments()` 与 `ConversationGroupSendPicker.cancelAll()`；正常选中与取消保持一次交付，备用文件选择器仍使用原来的 `*/*`。
- 标签图片选择也复用该请求池，和分组文件操作合计最多九项，不再单独建立无界队列。页面销毁会释放回调并移除排队读取；标签运行时销毁调用 `ConversationTabsIconPicker.cancelAll()`，同时取消等待选择、读取中及待交付的请求并卸载选择结果 Hook。无法中断的文件提供者仍可能读到返回，但返回后的图标文件和取消时尚未交付的文件都会删除；正常交付的图标保留。请求的 `onDiscard` 负责未交付结果的资源清理，仅捕获 ApplicationContext 和业务数据。
- 图标选择回归：`node scripts/run_conversation_tabs_icon_memory_tests.cjs`，覆盖十二个场景，包括页面回收、取消后文件清理、正常交付、异常恢复、千次请求容量限制和重复销毁重装。`TABS_ICON_MUTATION=retain-callback` 与 `TABS_ICON_MUTATION=drop-discard` 分别在临时副本中恢复持页和漏删文件问题，应被回归拒绝。标签运行时的销毁接线由 `run_conversation_tabs_lifecycle_tests.cjs` 覆盖；这些是 JVM 接缝检查，不替代 Android 文件提供者、图片解码或设备堆分析。
- 实名尾字的 `RealNameTailFeature.onFeatureDestroy` 会销毁查询调度器，清理消息队列、等待主线程执行的任务、延迟重试和超时任务，并释放调度器的完成与日志回调。初始化尚未成功的循环不再吞掉中断；超时检查使用可取消的延迟任务，不再为每次查询单开睡眠线程。任务键按调度器实例隔离，旧实例销毁不影响新实例；任务 API 未就绪时不启动查询或裸线程。晚到查询结果不会保存或恢复旧队列，正常查询仍保存并清理超时任务。
- 实名尾字回归：`node scripts/run_real_name_tail_lifecycle_tests.cjs`，编译真实 Feature 和 Scheduler，覆盖八组生命周期场景。不可中断的底层初始化或查询仍可能持续至返回，但不阻塞调度器销毁；本轮未修改 `BeforeTransferNameQuery` 的 Hook/定位，查询对象自身在调用期间仍可能持有 context/logger，因此不代表整个功能已无长期引用，也不替代设备验证。
- 导入保留 8 MB 限制及原有双参数接口，另外记录选择文件时的账号，在解析前与保存前复查账号和取消状态，防止选择过程中切换账号后写入错误账号。底层已开始的同步读写不能强杀；阻塞读取在取消后返回时不再进入业务提交，已经开始的同步保存不作强制回滚。
- `DexInstallScheduler` 在安装成功、取消或本轮重试耗尽后释放安装闭包，销毁时移除排队任务与延时重试。同键新安装使用独立状态，旧任务结束不能修改新状态。`runDexKitTask` 与普通调度任务均在执行前绑定 Hook 注册代次；`HookRegistry` 在全量卸载时使旧代失效，旧任务不能通过 `hook/add` 重新挂钩。
- 首页共享投影仅在 `FeatureManager.destroyAll` 的全量卸载中复位，不能因单独关闭聊天分组而破坏标签栏。安装去重索引、提供器、刷新对象及排队刷新一并清理，旧查询回调和旧刷新不能污染新代。全量销毁不额外等待 DexKit 全局串行门，但功能自身仍保留已有同步边界。
- 新增回归：`node scripts/run_conversation_group_file_memory_tests.cjs`、`node scripts/run_dex_scheduler_lifecycle_tests.cjs`、`node scripts/run_hook_projection_lifecycle_tests.cjs`。使用真实生产类/方法与 Android、Xposed、DexKit 边界桩；实际 Runtime 销毁接线另由同步生命周期回归验证。这些检查不替代完整 APK 构建和实机堆分析。当前没有新增对外全量热重载入口，也不表示所有功能都已具备完整热重载能力；注册代次租约不自动覆盖功能另起的异步线程。


## DexKit 按需驻留与空闲回收

共享入口使用 `DexKitBridge.createManaged(...)`，仅缓存轻量 Java 对象；实际查询才加载 native 实例，最后一次查询结束后空闲 30 秒释放。后续功能、脚本及旧查询结果的懒加载会按需恢复；回收和查询使用同一串行锁，永久 `close()` 后不可恢复。主进程和启用相关早期功能的子进程均覆盖，不关闭原有功能。定位缓存命中且无需 native 查询时，不再提前创建分析实例。

补丁源码、输入身份保护、原 API/native 库兼容核验与构建方式见 `third_party/dexkit-memory/README.md`。空闲回收不再输出 `[Hchat:DexMemory]` 提示，按需加载与空闲释放逻辑保持不变。回归通过不等于实机内存降幅。
