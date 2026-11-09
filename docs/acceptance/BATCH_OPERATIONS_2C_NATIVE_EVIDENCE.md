# 批量操作 2C：微信原生接口证据

## 证据范围

- 目标包：官方微信 8.0.80，`versionCode=3220`。
- 静态分析对象：从本机已安装包提取的 APK；SHA-256 `c35a05d5c13bd43cf1f7ff42c13fab070d21509ab2e233596f776d1afcae52f5`。
- DexSQL 仅用于只读 DEX/Smali 查询。后续经用户回复“随便取”后，真机提交过一次两项原生批量取关请求；未删除收藏。
- 下文的调用链、参数和回调结论来自 APK 字节码；服务端业务效果与真实 UI 写入仍为 `NOT_VERIFIED`。

## 公众号：读取当前关注关系

原生联系人选择器中的 `Lmx1/v;->call(Ljava/lang/Object;)Ljava/lang/Object;` 读取：

```sql
select bizinfo.brandIconURL, bizinfo.type, bizinfo.status,
       rcontact.username, rcontact.conRemark, rcontact.nickname,
       rcontact.alias, rcontact.conRemarkPYFull,
       rcontact.conRemarkPYShort, rcontact.showHead,
       rcontact.pyInitial, rcontact.quanPin, rcontact.quanPin
from rcontact, bizinfo
where bizinfo.username = rcontact.username
  and (rcontact.verifyFlag & 8) != 0
  and (rcontact.type & 1) != 0
order by showHead asc, ...
```

因此候选需要同时有 `bizinfo` 对应行、公众号 brand 位 `(verifyFlag & 8) != 0` 和当前联系人关系位 `(rcontact.type & 1) != 0`。该原生方法还按 `bizinfo.type` 分组，只接受 0、1、2，跳过 3 及其他值。它会过滤若干系统保留账号；`gh_579db1f2cf89` 和 `gh_b7695f100443` 受微信本地开关控制。本实现读取这两个开关后再决定是否显示；读取失败则整次候选查询报错。

原生 `Lmx1/v.call` 还跳过 `gh_43f2581f6fd6`、`Contact.z3.X4(String)`、`Contact.z3.r4(String)`、`Contact.z3.A4(String)` 命中的内置联系人。APK 中 `X4` 明确包含 `gh_3dfda90e39d6`、`gh_b4af18eac3d5`、`gh_f0a92aa7146c`、`gh_e087bb5b95e6`；`r4` 对应 `gh_051d9102de63`；`A4` 对应 `weixin` 与 `gh_9639b5a92773`。此外 `gh_579db1f2cf89` 是否隐藏由 `pa2.d0.mD` 配置决定，`gh_b7695f100443` 是否隐藏由 `y10.f.d()` 决定。

WeiPlus 的 `WeChatContactApi.getFollowedOfficialAccountsResult(...)` 复用这个联系表与品牌表关系，读取当前账号 `userinfo.id=2`，拒绝 self、群聊、缺失的 `bizinfo`、不满足 brand/contact 位、无效 `bizinfo.type` 和微信原生系统账号。使用 `queryWithStatus` 区分 SQL 错误与正常空列表；按所选用户名复核时也使用参数化 `IN` 查询。

## 公众号：资料页取消关注与批量接口

### 资料页单个取消关注

目标 APK 中的调用链：

1. `Lcom/tencent/mm/plugin/profile/ui/tab/t0;->f()V` 调用 `Lcom/tencent/mm/ui/tools/s1;->c(Lyk/o;Landroid/app/Activity;Lcom/tencent/mm/storage/z3;ZLjava/lang/Runnable;I)V`，构造原生确认对话框。
2. `Lcom/tencent/mm/ui/tools/l1;->onClick(Landroid/content/DialogInterface;I)V` 确认后调用 `Lcom/tencent/mm/ui/tools/s1;->b(Lyk/o;Landroid/app/Activity;Lcom/tencent/mm/storage/z3;ZI)V`。
3. `s1.b` 通过 `zp0.j1.v(Lxb5/g.class)` 获取 `Law1/n` 服务实例，读取资料页 `Contact.a1()` 用户名，并调用 `Law1/n;->a(Ljava/lang/String;Z)V`。该处明确传 `true`。
4. `Law1/n.a(username, true)` 创建 `Ly61/l(username, isClearRecord=true)` 请求对象，交给 `Ly61/f.c(Lip3/q0;)V`，随后注册原生请求结果回调 `Ly61/f.a(4, callback, false)`。
5. `Law1/n;->r2(ILgd5/ja5;Lip3/q0;)V` 解析 `gd5/pd0` 响应，取响应用户名与 `ret`，调用 `Law1/n;->b(Ljava/lang/String;IZ)V`，再通知注册的 `u1(Ljava/lang/String;ILgd5/ja5;)V` 回调。资料页回调校验返回用户名与当前联系对象一致；`ret==0` 走成功处理，非零结果由原生 UI 显示错误。
6. `Law1/n.b` 在 `ret==0` 时调用联系人存储更新。`isClearRecord=true` 分支调用 `Lw41/ba;->j(Ljava/lang/String;Lw41/ia;)V`，最终进入 `ba.k(List, callback)` 的 `deleteMsgByTalker` 异步清理阶段，并将联系人 type 置零。也就是说，资料页这个调用会清理该公众号会话消息。

字节码证据没有在该服务方法中直接给出 HTTP URL；可以确认的网络请求对象是 `Ly61/l(username, true)`，它经微信 `Lip3/f0`/`Ly61/f` 服务队列提交，响应由 `Law1/n.r2` 处理。没有从此证据推导更具体的服务器路径。

### 微信原生多公众号取消关注

`Lhy/f0;->unSubscribeAllContacts(Ljava/util/List;)V` 位于 `MicroMsg.FlutterBizConversationPlugin`。它把用户名传给 `Lq61/i0`，构造参数为 `(List, type=1, operation=2, maxItems)`；`maxItems` 读微信配置 `MMBatchModContactTypeMaxNumForServer`，默认 50。它把回调注册到命令号 `0xf96`，将进度对话框回调交给 `com.tencent.mm.ui.tools.p1`，再通过 `com.tencent.mm.modelbase.s1.g(scene)` 提交。

`Lq61/i0` 是 `NetSceneBatchModContactType`：

- 构造器：`Lq61/i0;-><init>(Ljava/util/List;III)V`。
- `doScene(Lcom/tencent/mm/network/s;Lcom/tencent/mm/modelbase/u0;)I` 设置路由 `/cgi-bin/micromsg-bin/batchmodcontacttype`、命令号 `0xf96`。
- 请求 `gd5/ug.e` 中每项 `gd5/sw4` 写入 `d=username`、`e=1`、`f=2`；按原生配置上限分批。
- 回调 `onGYNetEnd(IIILjava/lang/String;Lcom/tencent/mm/network/y0;[B)V` 解析 `gd5/vg.e` 的 `gd5/tw4`；`tw4.d` 是逐项返回码，`tw4.e.d` 是用户名，`tw4.e.e/f` 是返回的 type/operation。
- `ret==0` 的项目进入场景本地成功列表 `e` 并走微信原生联系人存储同步；错误项不会进入成功列表。`Lcom/tencent/mm/ui/tools/p1.onSceneEnd` 读取成功列表及 `scene.J()` 失败数。

WeiPlus 直接构造这个原生 scene，不调用带进度弹窗的 `unSubscribeAllContacts` UI 方法；按微信实时配置上限分批，并使用 `s1.a(0xf96, callback)`、`s1.g(scene)` 与 `s1.q(0xf96, callback)` 注册、提交、注销回调。每批提交前重读账号与关注关系；仅当逐项服务端 `ret==0`、微信原生本地成功列表包含该用户名、最终关注查询成功且关系已解除时报告成功。未知或本地结果未确认时不重试，并停止后续批次。

该批量 scene 没有 `isClearRecord` 参数，也没有调用 `DeleteContactService` 或会话删除接口。它与单个资料页的 `clearRecord=true` 流程分开实现。真实服务端结果、真实关注关系解除和设备 UI 行为均为 `NOT_VERIFIED`。

## 收藏：原生多选删除与 ID 对应关系

微信收藏多选入口：

1. `Lcom/tencent/mm/plugin/fav/ui/ea;->c()V` 从原生所选收藏 `Lbe2/u3` 读取 `field_localId:J`、`field_id:I` 及内容类型；特殊 `S1` 项不进入常规批量删除列表。
2. `Lcom/tencent/mm/plugin/fav/ui/ca;->onClick(...)` 确认后调用 `Lcom/tencent/mm/plugin/fav/ui/id;->a(Activity, List, FavoriteHandler, source=26, Map)`。
3. 该 helper 显示原生进度框并调度 `Lcom/tencent/mm/plugin/fav/ui/fd.run()`；`fd.run` 调用 `Lbe2/a3;->o(Ljava/util/List;ILjava/util/Map;)Z`。
4. `a3.o` 对原生收藏对象使用 `field_localId` 清理本地收藏存储、缩略图/内容文件及本地缓存；`field_id` 加入待删除服务器 ID 集。之后经 `FavoriteService` 提交服务器删除场景。
5. `Lbe2/v6;-><init>(Ljava/util/LinkedList;Ljava/util/Map;)V` 创建 `NetSceneBatchDelFavItem`，路由 `/cgi-bin/micromsg-bin/batchdelfavitem`。`doScene` 将服务器 `field_id` 放入请求 `gd5/hf.e`，每个服务器 ID 最多 20 项，并把 ID 与上下文写入 `gd5/vx6`。
6. `onGYNetEnd` 解析响应 `gd5/if.e` 的 `gd5/ud0`：`ud0.e` 是服务端 favId，`ud0.d < 0` 表示该项失败；非负值加入成功列表后调用 `Lbe2/a3;->H0(List)` 更新成功删除标记。`H()` 只在尚有输入 ID 未发出时继续下一批；WeiPlus 每次最多向此原生入口传 20 个 ID，后续 20 项由任务执行器在前一批得到确认后单独提交。

因此 `field_localId` 是本机收藏行键，`field_id` 是服务器 favId，二者不可互换。WeiPlus 删除前用 `localId` 重新读取原生对象，并同时核对 `serverId/field_id`、类型、更新时间和 `S1` 特殊标记；重复 localId 或服务器 ID 会拒绝整批。仅选中项目进入请求。

收藏选择器沿用 `WeChatFavoriteApi` 原生分页读取：批量清理时清空旧页缓存，从第一页重新读取；每次加载返回 `hasMore/error/changed`；游标不前进、重复 ID 内容冲突、解码失败或数据库读取失败都阻止删除。只有读到结束页且无错误后才允许“全选当前筛选结果”和提交。结果分别记录服务端接受、本地对象移除和最终确认，任务完成后失效插件收藏缓存；现有收藏发送 API 不调用删除路径。

收藏原生写入、服务器接受及删除后的微信 UI 刷新行为均为 `NOT_VERIFIED`；本次未实际删除任何真实收藏。选择器已在官方微信 8.0.80/3220 真机打开。首次验收显示已读 29 项后报“收藏分页游标没有前进”。

### 收藏分页补充证据

- 官方列表分页由 `Lcom/tencent/mm/plugin/fav/ui/adapter/c;->a()V` 发起：从父类 `Lcom/tencent/mm/plugin/fav/ui/adapter/b;` 读取 `d:J`（游标）和 `e:I`（类型），页大小为 20，调用静态 `Lbe2/a3;->R0(JII)J` 并保存返回游标。
- `Lcom/tencent/mm/plugin/fav/ui/adapter/c;->o()V` 从 `Lcom/tencent/mm/plugin/fav/n;->Vj()Lbe2/q5;` 取得收藏存储实例。游标为 0 时调用 `Lbe2/q5;->w6(IILjava/util/List;Ljava/util/Set;Lbe2/o5;)Ljava/util/List;`；后续调用 `Lbe2/q5;->ee(JILjava/util/List;Ljava/util/Set;Lbe2/o5;)Ljava/util/List;`。实参包括类型、20 项首批限制、列表/排除集合及回调。
- `Lbe2/a3;->R0(JII)J` 在同一个存储实例上调用 `W0(JII)J` 与 `Z(JII)J`，并按原生逻辑决定游标及是否安排收藏补取。`Lme2/f;->Z(JII)J` 的本机查询按更新时间小于输入游标、倒序并限制数量；没有更旧行时返回原游标。`Lme2/f;->ee(JI, List, Set, callback)` 查询更新时间大于或等于游标的记录且无 LIMIT，因此后续结果会包含已读记录并非“每页最多 20 条”。另一实现 `Loe2/d;` 也实现 `Lbe2/q5;`。
- WeiPlus 首批使用 `w6(...,20,...)`，后续使用 `ee(cursor,...)` 并按本地收藏 ID 合并。设备上的 29 项是累计结果；重复游标来自原生查询已没有更旧记录，是终止标记，不是分页失败。读取成功但重复 ID 内容冲突、非法或倒退游标仍保持失败关闭。
- 真机复验：公众号/服务号合并选择页读取 9 个候选，勾选两项显示“取消关注（2）”，确认文案正确。用户授权任意两项后，提交一次真实原生批量请求；60 秒内未观察到原生回调，后续重新读取时两项仍在候选列表。请求结果保持 `UNCONFIRMED/NOT_VERIFIED`，未重试、未报告成功。
- 收藏页修复后真机读到 `29 / 29` 项，无分页错误；图片筛选显示 `1 / 29`，选中一项显示“删除（1）”，最终确认标题和按钮均为“确认删除收藏”。随后取消弹窗，没有实际删除收藏。服务端删除及删除后的微信 UI 刷新仍为 `NOT_VERIFIED`。
- 首轮取关选择页出现“微信数据库列信息不可用”。设备侧对应查询使用 `contactFields()` 投影，其中头像列来自 `img_flag`；原 SQL 漏了该表连接。补上 `LEFT JOIN img_flag i ON r.username = i.username`，并为 `getColumnNames()` 不可用的游标增加 `getColumnCount()/getColumnName(i)` 读取回退后，真机可加载候选页。

### 取关回调等待补充

- `Lq61/i0;->onGYNetEnd(IIILjava/lang/String;Lcom/tencent/mm/network/y0;[B)V` 是原生批量场景响应处理。其后由 `Lcom/tencent/mm/modelbase/s1;->onSceneEnd(IILjava/lang/String;Lcom/tencent/mm/modelbase/n1;)V` 调 `s1.b(II,String,n1)`；`s1.b` 将 `v1` Runnable post 到 `n3`，随后才由 `u0.onSceneEnd(IILjava/lang/String;Lcom/tencent/mm/modelbase/n1;)V` 分发队列观察回调。因此场景回调先于 `u0` 队列观察回调。
- 首次真机请求使用旧等待条件，已提交但未在 60 秒内观察到队列回调；任务报告未确认，重新读取仍看到 9 个候选。请求未重试，实际关注关系保持 `UNCONFIRMED/NOT_VERIFIED`。
- 等待器现改为在本机 `onGYNetEnd` 原方法返回后记录 errType/errCode 并释放等待；队列级回调只负责后续注销。增加单元测试模拟“场景响应先到、队列观察器尚未运行”的顺序。修复后的真实取关请求未再次提交。
