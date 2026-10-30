# 交给下一个 AI（可直接粘贴）

新会话开场把下面这段整段发给对方即可。它不依赖聊天记录，因为事实都在仓库文档里。

---

## 提示词（复制以下内容）

```
我在维护一个 FongMi 影视 5.x 的 Android 爬虫插件仓库：https://github.com/tardlk/box（分支 main）。

先按顺序读，文档是事实来源，不要靠猜：
1. HANDOFF.md —— 当前状态、硬约束、源码地图、构建与发布流程、待办。唯一讲"现在"的文档。
2. docs/HISTORY.md —— 每次改动的原因、实测证据、当时没验证的部分。遇到"为什么这么写"查这里。
3. 按任务再看：docs/JAR_GUIDE.md（SPI 与打包）、docs/RELEASING.md（发布与回滚）、
   docs/REFERENCES.md（宿主源码、站点定位词）、DEVELOPMENT.md / DOUBAN.md / IVDY.md（各站点实现）。

现在有什么：
- 一个 JAR 里三个站点：豆瓣导航（csp_Douban，导航站，indexs=1、searchable=0，排第一）、
  爱看机器人（csp_Ikanbot）、爱电影（csp_Ivdy）。
- 线上配置：https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json
- 构建 bash build.sh；测试 bash test.sh（80 项契约检查）。
  实网开关：--live（爱看机器人）、--posters（封面）、--ivdy（爱电影）、--douban（豆瓣）。
- 本机跑实网测试前必须先给 JVM 指定代理，否则会误判成插件坏了：
  export JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=<端口> -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=<端口>"
  端口从 env | grep -i proxy 取。别把这个代理写进 SiteHttp 源码。
- 设备验证固定用本机 BlueStacks Air（Android 13，环境已配好），不要新建 AVD 或换其它模拟器；
  怎么改配置、怎么抓 TV-* 日志、怎么截图见 HANDOFF.md 第 5.4 节。

最容易踩的几条（完整清单和原因见 HANDOFF.md 第 3 节）：
- 豆瓣 indexs:1 / searchable:0 / 微信小程序 UA / 封面 @Referer= 后缀，都不能动。
- 爱看机器人封面直连豆瓣 CDN + @Referer=https://movie.douban.com/；不要改回走 imgp2 代理。
- 爱看机器人入口用 www.ikanbot.com，不要改回 v（v 会跳到 www1，部分网络里极慢）。
- 爱电影默认最高画质是所有者决定，不要按接口标注降回 720。
- 发布流程固定；只改配置时不要重新打包 JAR；核对源码一致性要比 classes.dex，不比外层 JAR MD5。

协作偏好：
- 直接动手，不要反复问"要不要我继续"；但涉及产品取舍（改超时、改画质、改封面策略）先说代价再让我选。
- 报告给证据：命令输出、耗时、Activity 名、截图，而不是"应该没问题"。
- 测不了就说没测，不要用"应该"糊过去。
- 文档用中文写；代码注释中英文都行，跟现有风格一致。

新增站点按 HANDOFF.md 第 7 节的顺序做，并在 scripts/package.py 与 scripts/verify_release.py
里同步站点配置和必需入口类。
```

---

## 为什么这份提示词这么短

早先它把全部硬约束抄了一遍，结果每次改动后这段就过期，和 `HANDOFF.md` 打起架来（比如里面还写着早已换掉的封面代理）。
现在只保留「去哪儿读」和「最容易踩的几条」，其余以仓库文档为准：单一事实来源，不会分叉。
