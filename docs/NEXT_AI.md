# 交给下一个 AI（可直接粘贴）

新会话开场把下面这段整段发给对方即可。它不依赖你我之间的聊天记录。

---

## 提示词（复制以下内容）

```
我在维护一个 FongMi 影视 5.x 的 Android 爬虫插件仓库：https://github.com/tardlk/box（分支 main）。
先读仓库里的 HANDOFF.md，再看 README.md、DEVELOPMENT.md、DOUBAN.md、docs/REFERENCES.md、docs/JAR_GUIDE.md。
文档是事实来源，不要靠猜；不确定的地方先验证再动手。

现在有什么：
- 一个 JAR 里三个站点：豆瓣导航（csp_Douban，导航站，配置里 indexs=1、searchable=0，排在第一位）、
  爱看机器人（csp_Ikanbot）、爱电影（csp_Ivdy）。
- 线上配置：https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json
- 当前线上 JAR：jar/box-934ad6bce4626704024e66dd59d1b111.jar
- 构建 bash build.sh；测试 bash test.sh（80 项契约检查）。
  实网开关：--live（爱看机器人）、--posters（爱看机器人封面）、--ivdy（爱电影）、--douban（豆瓣）。
  在 macOS 本机跑实网前先 `export JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=<端口> -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=<端口>"`
  （端口取 `env | grep -i proxy`），否则 Java 不走本地代理，会报 `Remote host terminated the handshake`。
  别把这个代理加进 SiteHttp 源码。

硬性约束，别再踩一遍：
1. 豆瓣站点配置里的 indexs: 1 不能删。宿主靠它决定"点卡片进搜索页而不是直接播第一个同名结果"
   （手机版 SearchActivity，电视版 CollectActivity）。删了就退化成自动播放。
2. 豆瓣接口只认微信小程序 UA；换成普通浏览器 UA 会返回 400 / code 997。见 Douban.UA。
3. 豆瓣封面是直连地址加 @Referer= 后缀（宿主 ImgUtil 会解析并去掉），不要改回走本项目的图片代理。
4. 爱看机器人封面走站点自己的代理 imgp2.ikanbot.eu.org；非豆瓣域名才用本项目代理。
   这个代理**要求 UA + Referer**：裸请求、只带 UA、只带 Referer 都是 403。
   所以封面地址必须带 `@Referer=…@User-Agent=…` 后缀（宿主 ImgUtil 解析后剥掉），
   删掉就静默变成"没有封面"——不报错，只是不给图。见 HANDOFF.md §13。
5. 爱看机器人的入口必须保持 v.ikanbot.com。它 301 到当时可用的镜像（www1 可用），
   而直连 www.ikanbot.com 的详情页会超时。不要"优化"成 www。
6. 三个站点共用 JAR，已有站点 key（douban、ikanbot_fresh、ivdy）不能改、不能互相覆盖。
7. 爱电影默认播放最高画质是仓库所有者的决定（2026-10-29），不要按接口标注把它降回 720。
8. 发布流程固定：build → scripts/prepare_release.py --output build/release --snapshot config-<日期>-<主题>.json
   → scripts/verify_release.py 校验 → 按 release-manifest.json 复制到仓库根 → 提交 → push → 再用
   --config-url 回读线上配置确认 MD5。只改配置时不要重打包 JAR（改 package.py 后手工重排 dist/config.json），
   这样用户不用重新下载。
9. 不要为了"看起来快"加长缓存或改超时然后声称验证过；测不了就说没测。

协作偏好：
- 直接动手，不要反复问"要不要我继续"；但遇到需要判断产品取舍的地方先说明代价再让我选。
- 报告要给证据：命令输出、耗时、Activity 名、截图，而不是"应该没问题"。
- 文档用中文写，代码注释用英文或中文都可以，但保持和现有风格一致。

如果我要新增一个站点，按 HANDOFF.md 第 5 节的顺序做，并在 scripts/package.py 与
scripts/verify_release.py 里同步站点配置和必需入口类。
```

---

## 交接时的事实快照（2026-10-29 16:20）

| 项 | 值 |
|---|---|
| 分支 / 提交 | `main`，与 `origin/main` 同步 |
| 线上 JAR | `box-91c19c170b12bd16fb74c018e6d4fc3c.jar`（182835 字节，封面请求头修复） |
| 配置 | 站点顺序 `douban` → `ikanbot_fresh` → `ivdy`；豆瓣 `indexs:1`、`searchable:0` |
| 契约检查 | **81 项**（爱看机器人 36 + 爱电影 17 + 豆瓣 28） |
| 最近发布的快照 | `config-20261029-poster-headers.json` |

## 三个站点各自的关键约束

| 站点 | 必须保持 |
|---|---|
| 豆瓣导航 `csp_Douban` | `indexs:1`；微信小程序 UA；封面直连 + `@Referer=`；`searchable:0`（否则点卡片会绕回自己） |
| 爱看机器人 `csp_Ikanbot` | 入口 `v.ikanbot.com`；封面走 `imgp2` 且必须带 `@Referer=`/`@User-Agent=` 后缀（否则 403 无封面）；缓存首页/分类/搜索 180s、详情 120s；WebView 降级上限 25s |
| 爱电影 `csp_Ivdy` | 默认取最高画质；域名轮换由 `IvdyApi.discover()` 跟随，不写死 |

## 当前环境（换机器需重配）

- JDK 17：`~/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`
- Android SDK：`~/Library/Android/sdk`（需要 `platforms/android-30`、`build-tools/35.0.0`）
- 也可以用环境变量 `IKANBOT_JAVA_HOME` / `IKANBOT_ANDROID_SDK` 覆盖
- 模拟器（可选，用于真机行为验证）：AVD `boxtest`（电视 1080p）、`boxphone`（Pixel 5），
  系统镜像 `system-images;android-34;google_apis;arm64-v8a`；
  配置注入比 UI 输入可靠：`adb root` 后改
  `/data/data/com.fongmi.android.tv/databases/tv` 的 `Config` 表（`url` 改掉、`json` 置 NULL）再重启应用。

## 已知待办 / 没验证的部分

1. ~~爱看机器人搜索第二页未在实网验证~~ **已于 2026-10-29 15:29 验证通过**：返回 10 条且 `page=2`。
   同轮 `--live` 还通过首页 24、分类第二页 24、搜索第一页 10、详情《黑球》28 线路、HLS 113914 字符。
2. 搜索翻页仍是 `next` 游标串行；JS 源用 `?p=N` 直达，但实测该参数返回空页，未采用。
3. 爱看机器人 15 秒读超时在该站抽风时会直接报错，**仍未决定是否放宽**。
   2026-10-29 15:29 采样到同一请求在「2–8 秒成功」与「40 秒只吐半截正文」之间跳（HTTP 200，无 Content-Length）。
   细节和三个可选方案见 HANDOFF.md 第 12 节；改这个要重打包 JAR，先让所有者选。
4. 电视版（leanback）点豆瓣卡片进的是 `CollectActivity`，实际体验未在真机确认。
5. 弱性能盒子播放 1080p 的稳定性、声音与拖动未确认。
6. 2026-10-29 16:20 那次 `--posters` 是 0/10（三次），全部卡在搜索步骤的 `Read timed out`，
   属本机链路抖动而非代码退化（同日 14:31 的记录是 10/10）。
   注意仓库根 `poster-check.json` 的 URL 是**加请求头后缀之前**的格式，不代表当前实现。
   换网络后重跑 `--posters` 刷新，不要改写历史记录的日期。
