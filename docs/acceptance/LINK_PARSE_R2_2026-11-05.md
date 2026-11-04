# 链接解析 R2 排查（2026-11-05）

- 基线 `f44c676`；手机微信 8.0.80/3220 的安装 APK SHA-256 `c35a05d5c13bd43cf1f7ff42c13fab070d21509ab2e233596f776d1afcae52f5` 与本地静态分析样本一致。手机 Hchat APK SHA-256 `2001d19121d1636ea179de09cac364a0624f5d10999e7f3c6f1081778a582bf3`，不是上轮 `f44c676` 测试包 `259dbe708e148a15bb31b462afc695ecb80a93fcea34f16e9351d5bf82dba917`。二者签名证书 SHA-256 均为 `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`。
- LSPosed 记录微信主进程加载 Hchat；02:08:17 消息观察报告 `hook=true subscription=true db_subscription=true db_hook=true`。旧包无分阶段链接解析日志，不能确定该条消息是否通过过滤，或当时解析/下载/发送在哪步失败。配置持久文件包含总开关及抖音开关记录，未证明进程内实时值；也没有获得该条消息的数据库字段。
- 微信 DEX 的 `Lcom/tencent/mm/storage/f9;->s1(I)V` 对 status=2 发布 `SendMsgSuccessEvent`、status=5 发布 `SendMsgFailEvent`；基类 `Llm/d8;->s1(I)V` 写入 `field_status`。`Lq61/s0;->onGYNetEnd` 成功分支设置 2 后调用 `g9.Sd` 更新消息。`g9.Sd` 的普通分支使用 `msgId=?`，另有 ORM 分支尚未验证。现有数据库监听在成功执行数据库写入之后分发变化；消息变化 API 对更新按 ID 读取消息。
- 代码现在只从数据库事件触发：对方文本限 INSERT；自己文本须 INSERT/UPDATE 显式携带 status=2 且读取行状态为 2，失败及缺失值拒绝。依据消息 ID 持久去重。移除了发送按钮入口，因此无按钮 Hook 时不会阻断数据库路径，也不会在按键时提前发送。真实微信数据库状态变更是否被现有 Hook 捕获、读回行是否完整，尚未验证；状态分析不能代替运行验证。
- 分享文案的短链在当前网络重定向至作品 `7704176965926659034`，详情返回 status=0、匹配作品 ID、视频类型和三个媒体地址。第一次独立探测中第一地址返回 403，第二地址下载 23,244,196 字节 MP4；正式客户端探针对完整分享文案和短链分别解析并下载相同字节数，其中一次使用第二候选。地址、风控和网络可能变化，不能证明 02:08 手机遭遇了同样失败。
- 本轮没有向受限微信账号发送消息，没有卸载或清除微信；群聊、其他账号接收、真实手机数据库触发和实际媒体发送均未验收。图集及搜抖音不在范围。
- 自动验证：`:app:testDebugUnitTest` 197 项，0 失败、0 错误、0 跳过；`:app:lintRelease`、`:app:assembleRelease` 和 APK v2 签名验证通过。候选 APK SHA-256 `ac3e7a14cd551d70ffd4bcebb89c085832fa9a2edbeeadb444bc033d8a1c8b7e`。候选只具备静态和自动测试证据，不表示实际发送链路通过。
