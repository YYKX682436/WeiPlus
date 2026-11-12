# 搜抖音：独立技术验证（2026-11-04）

## 结论

**本轮没有找到满足全部硬性要求、经联网验证的完整方案，暂不建议开发正式功能。** 必应可作为后续研究候选，但本次仅一个关键词找到候选作品，两个候选均未取得视频；抖音未签名匿名搜索接口返回 `blocked`。应先证明无登录、无用户 Cookie 的搜索→作品详情→完整视频下载链路，再评估 Android 接入。下述源码判断不等于联网验证通过。

## 环境和方法

- 2026-11-04 约 21:57–21:59（中国标准时间），仓库 HEAD `94f9f997f88bd2d35ef33c8050a0d5cb5a8870d5`。每个关键词每条搜索请求重复两次；同一时段短间隔样本不能代表长期成功率。
- 电脑 curl 使用 `--noproxy '*'`，没有环境代理变量，系统 HTTP/SOCKS/PAC 关闭，但目标域名解析为 `198.18.x.x`，可能有虚拟 DNS 或透明代理，**不能证明真正直连、出口地区或代理状态**。没有改变网络设置，也没有建立额外代理对照。
- ADB 手机型号 22081212C、语言 zh-CN、运营商国家属性 cn、Wi-Fi 路由；系统 HTTP 代理为 null。不能证明公网出口在大陆或排除透明代理。手机 curl 使用现有网络，没有读取私人账号数据。
- 不带私人 Cookie，不登录，不申请密钥。百度验证页及抖音 `blocked` 均停止，没有解验证码或规避访问控制。HTTP 200 只说明页面或响应可达。

## 搜索实测

候选数为从返回 HTML 匹配并去重的 `douyin.com/video/<18–20 位 ID>`，不是引擎总结果数，可能漏掉编码跳转或其他形式。相关性仅依据引擎摘要，作品本身尚未核实。每格为第 1 / 2 次。

| 路线 / 设备 | 猫猫打架 | 武汉美食 | 搞笑视频 | 观察 |
|---|---:|---:|---:|---|
| 百度，电脑 | 0 / 0 | 0 / 0 | 0 / 0 | 6/6 返回 HTTP 200 的“百度安全验证”页，不是零搜索结果；0.265–0.370 秒。 |
| 百度，手机 | 0 / 0 | 0 / 0 | 0 / 0 | 6/6 同为安全验证页；0.354–0.497 秒。 |
| 必应，电脑 | 0 / 0 | 0 / 0 | 2 / 2 | 6/6 搜索页面 HTTP 200，仅第三词找到直接作品 URL，两个 ID 两次相同；0.254–0.329 秒。 |
| 必应，手机 | 0 / 0 | 0 / 0 | 0 / 0 | 6/6 搜索页面 HTTP 200；0.302–0.527 秒。手机 UA 与电脑移动 Chrome UA 不同，不能单独归因于网络。 |
| 抖音搜索网页，电脑 / 手机 | 0 / 0 | 0 / 0 | 0 / 0 | 各 6/6 返回相同大小的 72,914 字节 HTML 外壳，无可提取作品 ID；未执行网页 JS，不能断言浏览器最终表现。电脑 0.221–0.353 秒，手机 0.148–0.254 秒。 |
| 抖音未签名搜索 API，电脑 / 手机 | 0 / 0 | 0 / 0 | 0 / 0 | 各 6/6 HTTP 200 JSON 的 `status_msg: blocked`，无结果；电脑 0.105–0.191 秒，手机 0.201–0.280 秒。`status_code: 0` 不代表搜索成功。 |

必应第三词索引候选：[7457473400202726715](https://www.douyin.com/video/7457473400202726715)（摘要为监控搞笑瞬间）和 [7457017269722402067](https://www.douyin.com/video/7457017269722402067)（摘要为搞笑视频合集）。摘要与关键词相关；这些是引擎索引候选，**未验证作品当前真实存在、可播放或链接有效**。其他两词未发现可提取的候选，相关性和有效性无法评估。两次一致仅指该时段和匹配方法。

## 视频解析实测

参考 [ldsoy/douyin-video-parser](https://github.com/ldsoy/douyin-video-parser) 源码版本 `2ebcb268d706a392de77301855aba9306d04cc84`，手工复现分享页 `_ROUTER_DATA` → `videoInfoRes` 主路径及未签名详情 API 回退请求；没有运行整个项目、没有部署服务。

两个候选各请求一次：`www.douyin.com/video/<ID>` 返回 200、72,914 字节外壳；`www.iesdouyin.com/share/video/<ID>` 返回 200，存在 `_ROUTER_DATA` 和对应 `itemId`，但 `videoInfoRes` 和 `play_addr` 均缺失。随后两个详情 API 请求均 HTTP 403，响应 `Blocked by ArgusSecurityPlugin Uifid Not Found`。没有取得真实媒体 URL，**解析成功 0/2，下载成功 0/2**；无法检测视频格式、完整性、时长或播放，更不能证明作品已删除、私密或有效。未尝试绕过 403。手机只实测搜索，未实测分享解析或下载。

## 开源方案与复用判断

- [tamnd/douyin-cli](https://github.com/tamnd/douyin-cli) 本地只读源码 `b70c8d7e3f650023aada02013aa75850f67f0222`：搜索调用 web general search，包含 `a_bogus` 签名和生成的 `msToken`；项目说明存在反爬限制、可能需要会话。此处没有运行其签名搜索：环境无 Go，且无签名请求已返回 `blocked`，继续尝试规避该限制超出本轮边界。签名方案可行性和成功率 **未实测**。
- [JoeanAmier/TikTokDownloader](https://github.com/JoeanAmier/TikTokDownloader) 为 Python 项目，功能和依赖较重，版本变化及 Cookie 相关配置增加移植维护复杂度；本轮只查阅公开仓库和发布记录，**未运行其搜索或解析**，不能推断匿名成功。
- [QQMusicOrderRuntime.kt](../../app/src/main/java/h/Hchat/hooks/items/musicorder/QQMusicOrderRuntime.kt) 提供文本插入订阅、自发命令拦截、群消息内容处理、聊天授权、串行后台执行和失败回复的参考；[QQMusicClient.kt](../../app/src/main/java/h/Hchat/hooks/items/musicorder/QQMusicClient.kt) 提供 HTTP/结果建模参考。
- [WeChatVideoApi.java](../../app/src/main/java/h/Hchat/hooks/api/media/WeChatVideoApi.java) 的 `canSendSilently()`、`send(talker, videoPath)` 接受本地文件并生成缩略图及发起任务，仓库已有多个调用点。返回 true 只表明任务调用成功，不能证明对端收到或视频可播放。缓存文件生命周期、大小/时长限制、异步发送完成和真实微信行为仍需验证，本轮未发送微信消息。

## 比较与建议

| 路线 | 本轮按关键词获得直接候选 | 耗时和维护 |
|---|---|---|
| 百度公开搜索 | 电脑 0/3，手机 0/3；全部被验证拦截 | 页面 0.265–0.497 秒；HTML 和访问策略易变，当前不可用。 |
| 必应公开搜索 | 电脑 1/3，手机 0/3；不是完整链路成功率 | 页面 0.254–0.527 秒；HTTP + HTML 相对轻量，但结果少、索引可能陈旧，解析本次失败。 |
| 抖音匿名页面 / 未签名 API | 电脑和手机均 0/3 | 响应 0.105–0.353 秒；API 返回 blocked。网页 JS、签名和访问限制可能变动；签名路线未验证。 |

优先保留 **必应公开搜索 + 合规公开作品解析** 为研究候选，因为可用 Android 常规 HTTP 客户端实现，无需引入 Python 或 Go 运行时；但当前解析成功率为零，**并不满足交付条件**。联网结果不包含视频下载、Android 集成、微信实际发送、代理/真正直连分组或长期稳定性验证。

## 复现示例

不使用 Cookie；搜索词通过 `--data-urlencode` 编码。将 `<ID>` 换为上述候选，检查响应内容而非仅检查 HTTP 状态：

```sh
curl --noproxy '*' -G 'https://cn.bing.com/search' --data-urlencode 'q=site:douyin.com/video/ 搞笑视频' -A 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36'
curl --noproxy '*' -G 'https://www.douyin.com/aweme/v1/web/general/search/single/' --data-urlencode 'device_platform=webapp' --data-urlencode 'aid=6383' --data-urlencode 'channel=channel_pc_web' --data-urlencode 'keyword=猫猫打架' --data-urlencode 'search_channel=aweme_general' --data-urlencode 'search_source=normal_search' --data-urlencode 'offset=0' --data-urlencode 'count=10' -A 'Mozilla/5.0'
curl --noproxy '*' 'https://www.iesdouyin.com/share/video/<ID>' -A 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1'
curl --noproxy '*' 'https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=<ID>&aid=6383&cookie_enabled=true' -A 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1'
```

手机对照可使用 `adb exec-out curl` 和相同 URL/参数。响应受时间、网络和 UA 影响，之后结果可能变化。
