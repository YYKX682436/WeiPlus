# 批量操作 2C：微信原生接口证据

## 证据范围

- 目标包：官方微信 8.0.80，`versionCode=3220`。
- 静态分析对象：本机已安装 APK，SHA-256 `c35a05d5c13bd43cf1f7ff42c13fab070d21509ab2e233596f776d1afcae52f`。
- DexSQL 查询只读 DEX/Smali；服务端实际结果以真机响应为准。
- 用户决定取消“批量清理收藏”，该功能的入口、任务、删除 API 与专用测试已移除；既有收藏选择、分页、预览和发送仍保留。本次没有删除真实收藏。

## 公众号：读取当前关注关系

原生联系人选择器中的 `Lmx1/v;->call(Ljava/lang/Object;)Ljava/lang/Object;` 查询 `rcontact` 与 `bizinfo` 的关联，并同时要求 `(rcontact.verifyFlag & 8) != 0` 和 `(rcontact.type & 1) != 0`。它只接受 `bizinfo.type` 为 0、1、2 的公众号类型，并排除微信内置系统联系人。WeiPlus 的 `WeChatContactApi.getFollowedOfficialAccountsResult(...)` 沿用这些条件，校验当前账号、self、群聊、品牌位、联系人关系位和系统保留项；使用 `queryWithStatus` 区分 SQL 失败与正常空结果。按选中用户名复核时使用参数化 `IN` 查询。

## 公众号：资料页取消关注与原生批量接口

### 资料页单个取消关注

官方资料页确认入口为：

1. `Lcom/tencent/mm/plugin/profile/ui/tab/t0;->f()V` 调用 `Lcom/tencent/mm/ui/tools/s1;->c(Lyk/o;Landroid/app/Activity;Lcom/tencent/mm/storage/z3;ZLjava/lang/Runnable;I)V` 显示原生确认框。
2. `Lcom/tencent/mm/ui/tools/l1;->onClick(Landroid/content/DialogInterface;I)V` 确认后调用 `Lcom/tencent/mm/ui/tools/s1;->b(Lyk/o;Landroid/app/Activity;Lcom/tencent/mm/storage/z3;ZI)V`。
3. `s1.b` 通过 `zp0.j1.v(Lxb5/g.class)` 取得 `Law1/n` 服务，读取资料页联系人的用户名并调用 `Law1/n;->a(Ljava/lang/String;Z)V`，参数 `true` 表示同时清理该公众号的会话消息。
4. `Law1/n.a` 创建 `Ly61/l(username, isClearRecord=true)` 并提交到 `Ly61/f` 请求队列；`Law1/n.r2` 解析 `gd5/pd0` 响应，成功时更新联系人关系并回调资料页。APK 字节码未直接给出 HTTP URL。

### 原生多公众号取消关注

`Lhy/f0;->unSubscribeAllContacts(Ljava/util/List;)V` 构造 `Lq61/i0`，参数为 `(List, type=1, operation=2, maxItems)`。`maxItems` 来自 `MMBatchModContactTypeMaxNumForServer`，默认 50；该原生入口向命令号 `0xf96` 注册回调并调用 `com.tencent.mm.modelbase.s1.g(scene)`。

`Lq61/i0` 是 `NetSceneBatchModContactType`：

- 构造器：`Lq61/i0;-><init>(Ljava/util/List;III)V`。
- `doScene(Lcom/tencent/mm/network/s;Lcom/tencent/mm/modelbase/u0;)I` 设置 `/cgi-bin/micromsg-bin/batchmodcontacttype` 和命令号 `0xf96`。
- 请求 `gd5/ug.e` 中每项 `gd5/sw4` 写入 `d=username`、`e=1`、`f=2`。
- `onGYNetEnd(IIILjava/lang/String;Lcom/tencent/mm/network/y0;[B)V` 解析 `gd5/vg.e` 的逐项结果；`tw4.d` 是结果码，`tw4.e.d` 是用户名，`tw4.e.e/f` 回传 type 和 operation。结果为 0 的项目进入原生成功列表并更新联系人存储。
- 原生 `u0.onSceneEnd(IILjava/lang/String;Lcom/tencent/mm/modelbase/n1;)V` 队列回调在 scene 响应处理之后分发。

WeiPlus 按微信实时上限调用原生 scene，通过队列回调等待 scene 处理完成；随后读取逐项服务端码、本地成功列表和本地清理失败列表。反射读取被隔离在只读观察器中，失败时保留未确认结果，不会打断微信的原生响应处理。队列回调在完成或超时后注销。每批提交前重新核对账号和关注关系；结果不明时不重试，只有逐项服务端成功、本地成功标记存在且最终关注查询确认关系解除时才报告成功。

资料页单个取消关注的 `isClearRecord=true` 与上述批量 scene 分开：批量 scene 没有清理会话记录的参数，不调用好友删除或会话删除接口。

### 真机结果与当前限制

- 设备目标为官方微信 8.0.80/3220。公众号/服务号合并选择页曾成功读到 9 个候选，勾选两项并显示“取消关注（2）”，确认文本正确。
- 旧等待实现提交过一次用户授权的真实批量请求，60 秒内没有观察到队列回调；后续重新读取仍看到 9 个候选。该次请求未重试，最终效果记为 `UNCONFIRMED/NOT_VERIFIED`。
- 复核代码后发现一个可导致请求处理失败的缺陷：旧结果 Hook 在微信执行 `onGYNetEnd` 原逻辑前做未保护的反射读取；读取异常可能中断宿主处理，同时等待条件依赖之后的队列回调。现改为仅在原生队列回调中读取 scene 结果并释放等待。该修复尚未重新提交真实取关请求，因此修复版的服务器接受和实际关注关系解除仍为 `NOT_VERIFIED`；不会把旧请求结果或静态分析当作成功。

## 收藏读取与现有发送

微信原生收藏列表的分页由 `Lcom/tencent/mm/plugin/fav/ui/adapter/c;->a()V` 发起，首批调用 `Lbe2/q5;->w6(...)`，后续调用 `Lbe2/q5;->ee(...)`。本机收藏存储 `Lme2/f;->Z(JII)J` 按更新时间查询更早记录；没有更旧记录时返回原游标。`WeChatFavoriteApi` 继续复用这些原生读取方法，按本地收藏 ID 合并重复页，并保留预览和收藏发送 API。

收藏删除入口和对应远端删除请求不再由 WeiPlus 批量操作功能调用。批量操作目录当前保留原十二项功能与公众号/服务号批量取消关注，共十三项。
