# 微信 8.0.80 防撤回提示修复

- 日期：2026-11-03
- 基线提交：`18e0ffb8d71b524492c7126dacaaaa8093ac5edb`
- 目标微信：官方 `com.tencent.mm` 8.0.80（versionCode 3220）
- DexSQL 样本：`/Users/muchen/Downloads/weixin8080正式版.apk`，SHA-256 `c35a05d5c13bd43cf1f7ff42c13fab070d21509ab2e233596f776d1afcae52f5`

## 根因与证据

修复前，设备安装 Hchat 1.2.0（versionCode 261030），官方微信 8.0.80 主进程已进入防撤回处理。用户报告“小号群”两条他人撤回均没有显示提示。LSPosed 记录与这两次操作对应：13:17:56.651 和 13:17:58.674，系统消息插入均以 `createTime hook 未就绪` 结束，随后 AntiRecall 记录提示插入失败。事件 Hook 已命中并走到提示插入 API；失败发生在调用微信原生系统消息链之前。

DexSQL 对上述官方 APK 的静态查询确认，微信的系统消息监听方法调用本地消息存储插入并取得实际消息 ID，也调用原生创建时间方法。对应的已验证调用描述符为 `Lzx3/l;->a(String,String,String)V`、`Lcom/tencent/mm/storage/g9;->Zb(Lcom/tencent/mm/storage/f9;)J` 和 `Lw41/ba;->o(String,J)J`。创建时间辅助方法接收秒并返回毫秒。

旧定位逻辑通过静态 `(LocalMessage) -> long` 插入方法的 declaring class 查找时间辅助方法；8.0.80 系统消息监听链没有该静态插入候选，因此时间方法没有被解析。当前兼容记录中的 listener 字符串定位可找到原生监听器，却没有从它的实际调用边收集存储插入和创建时间方法。修复从已验证的监听器调用边解析这两个实例方法及 LocalMessage 类型，不在生产代码中硬编码混淆类名。

`resolveRecalledCreateTime()` **不是这两条失败事件的根因**：处理路径已拿到原消息时间并调用 `insertSystemMessageAt()`；若原消息时间未定位，会记为 `未定位原消息时间`，本次日志记录的是更靠后的 `createTime hook 未就绪`。仍将时间回退收紧为同一 talker 下验证过的服务器消息 ID，避免把服务器 ID 当本地消息 ID，或从其他会话定位到同号消息。

`insertViaWechatSystemMessageMethod()` 中调用 void 原生方法后无条件返回成功的风险也不是本次事件的起因，因为当时还未执行该方法。该风险一并修复：插入 Hook 捕获微信存储层返回的实际正数消息 ID，再从消息存储回读并验证 ID、talker、内容、系统消息类型和目标时间；若原生调用可能已经落库但回读不确定，则报告失败且不再插入第二条。

## 修改

- `app/src/main/java/h/Hchat/dexkit/DexFinder.java`：从已定位原生系统消息监听器的调用边解析其 LocalMessage 存储插入和创建时间方法，并缓存新定位结果。
- `app/src/main/java/h/Hchat/hooks/api/message/WeChatLocalMessageApi.kt`、`NativeSystemMessageInsertReceipt.kt`：记录真实插入回执、存储回读校验；原生链未产生插入时才允许走相同已验证存储方法的直接回退。未确认的插入不再返回成功。
- `app/src/main/java/h/Hchat/hooks/api/message/WeChatMessageStoreApi.java`：按 `talker AND msgSvrId` 查询，避免跨会话误匹配。
- `app/src/main/java/h/Hchat/hooks/items/antirecall/AntiRecallFeature.kt`、`RecallRuntimePolicy.kt`：原子去重；提示失败会释放去重门闩以便重试，但不因此放过原撤回事件；提示开关关闭时保留原防撤回策略。
- `app/src/test/java/h/Hchat/hooks/items/antirecall/RecallRuntimePolicyRegressionTest.kt`：覆盖重复/并发事件、失败重试、消息缺失和跨会话拒绝、时间单位和格式、插入回执失败及开关策略。
- `gradle.properties`：版本升级到 1.2.1（261103）。`scripts/tests/api102/AvatarRegression.kt.in` 同步当前 Avatar 生产代码的测试替身；这是为了让全量仓库回归可以执行，不改变 Avatar 生产代码。

防撤回主开关、自己撤回策略、提示开关、提示位于原消息下方的顺序，以及 `{name}`、`{content}`、`{sendTime}`、`{recallTime}` 变量替换规则均保留。此修复没有编辑已保存提示模板。

## 自动验证

- `./gradlew :app:testDebugUnitTest`：通过。30 个测试套件、130 项，0 failures、0 errors、0 skipped。
- `scripts/run_*_tests.cjs`：30/30 通过。
- `git diff --check`：通过。
- `./gradlew :app:lintRelease :app:assembleRelease`：通过；包含 R8 和正式签名。Lint 输出保留既有的 `firstOrNull()` 非唯一匹配告警，没有新增抑制规则。
- `apksigner verify --verbose --print-certs`：通过，APK Signature Scheme v2 有效，单 signer。

## 真机验证矩阵

| 场景 | 结果 | 证据与限制 |
|---|---|---|
| 修复前官方 8.0.80 群聊他人撤回 | **CONFIRMED_FAIL** | 两条用户测试撤回分别在 13:17:56.651、13:17:58.674 命中旧版插入失败日志 `createTime hook 未就绪`；用户确认未看到提示。日志中的联系人与消息 ID未写入本报告。 |
| 修复后私聊撤回、提示开启 | **PASS（用户确认）** | 用户确认当前私聊测试成功；私聊界面出现已保存模板的提示。退出后从会话列表重新进入，提示仍在且没有因重进新增重复项。 |
| 修复后私聊自己撤回、保留自己撤回开启 | **PASS（有限证据）** | 用户表示撤回后仍可看到消息；当前界面同时出现一条自撤回提示。该事件没有单独的、可归属到特定消息 ID 的日志。 |
| 修复后群聊他人撤回 | **NOT_VERIFIED** | 用户说明群聊测试账号无法在群里发送，要求跳过；不把群里原有提示当成本次补丁验证。 |
| 修复后群聊前台实时刷新、群聊退出重进 | **NOT_VERIFIED** | 按用户要求跳过群聊用例。 |
| 提示关闭时的真机行为 | **NOT_VERIFIED** | 设置开关曾切换到关闭，但没有得到可确认的关闭状态撤回事件；开关策略由单测覆盖。 |
| 自己撤回关闭策略的真机行为 | **NOT_VERIFIED** | 原设置已恢复为关闭；没有完成一条能确认触发撤回回调的关闭策略测试。 |
| 私聊与群聊错会话插入、重复触发、异常稳定性 | **部分由自动测试覆盖** | 单测拒绝跨会话命中并验证原子去重/失败重试；真机没有对群聊用例作 PASS 声明。目标微信进程在观测窗口内未发现崩溃。 |

测试过程中“慕晨”私聊曾被微信限制发送，因此改由用户指定的其他置顶私聊会话验证。当前设置已恢复为：防撤回开启、保留自己撤回关闭、显示撤回提示开启；原提示模板未修改。未卸载微信，未清除微信或 Hchat 数据。

## 正式 APK

- 路径：`dist/Hchat-1.2.1-release-signed.apk`
- 包名与版本：`h.Hchat`，1.2.1 / 261103，minSdk 27。
- SHA-256：`f1c49721cb104827e6c496fb03fceacb21dc0701bf678c6069613b8aecdaa2a3`
- 签名证书 SHA-256：`5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`，与既有正式签名一致。
- 已通过 `adb install -r` 安装到设备；官方微信仍为 8.0.80（3220）。

本报告不把群聊、提示关闭或自己撤回关闭策略列为真机通过项；这些场景保持 **NOT_VERIFIED**。
