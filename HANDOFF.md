# box：交给下一个 AI 的接手说明

更新：2026-10-29（Asia/Shanghai）。先读本页，再查链接中的具体代码。

## 1. 项目与交付目标

仓库：[tardlk/box](https://github.com/tardlk/box)，当前发布分支 `main`。
用户使用 **FongMi 影视 5.x**，希望站点能浏览、搜索、看封面、选集并播放。
这是 Android Spider 插件：Java 源码经 D8 生成 `classes.dex`，再放入扩展名为 `.jar` 的 ZIP。
仅用 `javac` 生成普通桌面 JAR 不够。

固定导入地址：

```text
https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json
```

三个站点共用一个 JAR，历史命名仍叫 `ikanbot.jar`，不表示只含 ikanbot。
当前线上 JAR：`jar/box-50812b34404760998b435d60361aea54.jar`（2026-10-29 起，含豆瓣导航）。
源码基线与该 JAR 为同一次提交；上一版为 `jar/box-752e5b34485ae70709935cc68c738f0a.jar`。

## 2. 已完成与未完成

| 项目 | 当前证据 | 下一位 AI 不能误判的部分 |
|---|---|---|
| 爱看机器人 `csp_Ikanbot` | 用户明确反馈浏览、点击、搜索、播放可用 | 不是旧 AI 的残留工程；请在当前实现上修改 |
| 老友记第一季海报 | 旧豆瓣地址 403；改路径后 10 季海报代理均返回可解码图片 | 封面非空字符串不代表图片可用 |
| 爱电影 `csp_Ivdy` | 分类、筛选、分页、搜索、详情、封面、选集和取址接口通过实网检查；2026-10-29 用户确认设备上可播放 | 声音、拖动与弱性能盒子的解码表现未单独确认；播放可用不等于各画质都稳定 |
| 爱电影画质 | 2026-10-29 起默认取最高分辨率，复测电影样例实际选中 1080p 并取到有效分片；同画质优先 `flag=true` | 接口对 720/1080 仍标 `needLogin=true`。放开高分档是仓库所有者的决定（2026-10-29 明确要求），不是技术判断；不要反过来又按标注降级 |
| 豆瓣导航 `csp_Douban` | 七个分类、首页推荐、筛选、Top250 翻页、封面直连通过实网检查；2026-10-29 用户在 FongMi 5.8.4 确认点卡片进搜索页并可选源 | 它是导航站：`vod_id` 用 `msearch:` 前缀，靠片名交给影视站搜索，本身无详情与播放。`searchable` 必须是 0，否则点卡片会绕回搜索 |
| 本地测试 | 爱看机器人 35 + 爱电影 17 + 豆瓣 28，共 80 项通过（2026-10-29） | 历史记录不能当成本轮新测试；没有连接 Android 真机 |

如果爱电影播放再次失败，先在当前网络对照原站，再比较接口与媒体请求；保留失败正文。
已确认 `403 the source ip is forbidden` 是 CDN 按来源 IP 放行造成的，换 Referer、换新鲜令牌都不会改变结果，
不要在插件里试图绕过它。不要把返回 `parse=0` 或获得 `.m3u8` 地址当作播放成功。

## 3. 五分钟定位源码

| 路径 | 用途 / 最适合从哪里开始读 |
|---|---|
| `src/main/java/com/github/catvod/spider/Ikanbot.java` | 完整 Spider 调用流程，详情 HTTP 优先、可选 WebView 后备 |
| 同目录 `SiteParser.java` | HTML 卡片、真实分页、token、资源 JSON、豆瓣海报修正 |
| 同目录 `SiteHttp.java` | HTTP 会话、实际落地域名、UA、gzip、超时、媒体 URL 检查 |
| 同目录 `Proxy.java` | 登记海报 ID，补 Referer，返回图片字节；包含公网地址检查 |
| 同目录 `BrowserPage.java` | Android 可见 WebView、页面公开 DOM 读取、取消和销毁 |
| 同目录 `Ivdy.java` | 新站点入口；用选集 ID 延迟取址，适合学习新增 API 型站点 |
| 同目录 `IvdyApi.java` / `IvdyParser.java` | 域名发现、公开客户端签名、接口转播放器 JSON |
| 同目录 `Douban.java` | 导航型站点：分类与筛选项、`msearch:` 卡片、封面代理；无详情与播放 |
| `src/stubs/.../Spider.java` | 本地最小编译接口；实际能力以 FongMi 宿主源码为准 |
| `build.sh` / `scripts/package.py` | 编译、D8、依赖合并、DEX 检查、JAR 和配置生成 |
| `test/` / `test.sh` | 契约测试与按需执行的实网测试 |
| `IVDY.md` / `DOUBAN.md` / `DEVELOPMENT.md` | 各站点的具体实现与历史验证说明 |

本机原工作目录为 `ikanbot-spider/`，曾通过 GitHub 连接器直接提交，没有 `.git`。
换机器接手应直接 `git clone https://github.com/tardlk/box.git`，不依赖原机或旧聊天。
README、源码、开发说明现在在本地和 GitHub 使用相同文件名，无需手动重命名上传。

## 4. 从干净克隆构建

```bash
git clone https://github.com/tardlk/box.git
cd box
export IKANBOT_JAVA_HOME=/absolute/path/to/jdk-17
export IKANBOT_ANDROID_SDK=/absolute/path/to/android-sdk
bash build.sh
bash test.sh
```

SDK 需要 `platforms/android-30/android.jar` 和 `build-tools/35.0.0/d8`；还需要 Python 3。
`build.sh` 会下载并校验固定版本 Jsoup 与 JVM 测试 JSON 依赖；`lib/` 无需随仓库复制。
在 macOS 未设置上述环境变量时，脚本使用用户 `Library/` 下的既有安装。
其他环境显式设置变量；不要硬编码原用户的用户名或绝对路径。

构建后本地 `dist/` 提供相对 JAR 地址的配置，仓库根 `config.json` 是线上加速配置；两者用途不同。
发布前使用 [发布脚本](docs/RELEASING.md) 生成线上配置，不能直接用本地相对配置覆盖线上文件。

## 5. 加一个新站点的顺序

1. 在真实页面确认稳定入口、最终跳转域名、分类、搜索分页、详情选集、播放请求和封面请求。
2. 判断数据在 HTML、公开 JSON API 还是网页运行后 DOM。优先参考当前站点和宿主源码。
3. 对照 `Ivdy.java`、`Ikanbot.java` 或 `Douban.java` 写新的公开 Spider 子类，把解析逻辑和网络逻辑分开。
   导航型站点参考 `Douban.java`：`vod_id` 用 `msearch:` 前缀，`searchable` 设为 0。
4. `csp_Xxx` 对应 `com.github.catvod.spider.Xxx`，保留无参构造；按 [SPI 格式](docs/JAR_GUIDE.md) 返回 JSON。
5. 在 `scripts/package.py` 增加站点配置及入口类存在检查，并同步 `scripts/verify_release.py` 的必需 `csp_` 集合；不要覆盖已有三站。
6. 用真实响应的最小样本做解析/签名/分页回归；实网验证图片实际字节和媒体清单，记录失败。
7. 生成新版本发布目录，连同源码、文档和记录一次提交；校验加速配置实际下载到新 JAR。

不要先用另一个项目的整个 JAR 当黑箱依赖，再声称实现完毕。这个工程已包含自己需要的 Jsoup。

## 6. 按需验证与接手优先级

```bash
bash test.sh             # 80 项本地检查；先构建
bash test.sh --posters   # ikanbot 老友记 10 季图片代理，检查真实图片
bash test.sh --live      # ikanbot 首页/分类/搜索/详情/HLS
bash test.sh --ivdy      # 爱电影完整接口；没有可访问 HLS 时退出码 2
bash test.sh --douban    # 豆瓣七个分类/筛选/榜单翻页/封面代理
```

每个实网开关只执行对应检查，不是把所有站点都跑一遍。
测试写到 `dist/` 的记录是新结果；仓库根同名记录是历史发布证据，不要悄悄刷新日期。

后续优先级：真机上确认豆瓣卡片的点击与跨站搜索 → ikanbot 原生 WebView 与遥控器交互确认
（爱电影播放已由用户确认可用）。
若网站更新，先复现具体入口，不要因一个站点故障重写另一个已可用站点。

## 7. 去哪里查资料

先查 [docs/REFERENCES.md](docs/REFERENCES.md)：已核对 FongMi 的 `Spider.java`、`JarLoader.java`，
以及 lushunming 的参考实现；同时列出了 Android D8、SDK 和 Jsoup 官方资料。
站点域名、JS chunk 名称和接口会变，资料索引说明了重新定位的方法。
发布和回滚见 [docs/RELEASING.md](docs/RELEASING.md)。

## 8. 本次交接校验

2026-10-29 在不复用构建缓存的干净目录完成构建、46 项本地测试和发布暂存校验。
新构建 DEX 与当前线上 JAR 的 DEX 完全相同；JAR 外层打包时间导致 MD5 不同，因此没有替换线上产物。
同时核对了当前加速配置下载的 JAR，以及发布工具对旧 MD5、损坏文件、缺失既有站点的拒绝行为。
结构化记录见 [docs/handoff-validation.json](docs/handoff-validation.json)。
本次未重跑站点实网取址或连接 Android，原有实网记录仍保留原日期及限制。

## 9. 2026-10-29 网络恢复后的复测

用户告知原测试网络的 IP 限制已解除，随后在干净克隆上完整重跑，结论与交接时不同：

- `bash build.sh` 通过；新构建 `classes.dex` 与线上 `jar/box-7b075ad627b2d5cbbfc29f253d3b3b1c.jar` 的 SHA-256 完全相同，JAR 仅外层时间戳不同，因此不需要替换线上产物。
- `bash test.sh` 46 项全过；`--live` 爱看机器人全过（HLS 清单 70181 字符）；`--posters` 老友记 10 季全过。
- `--ivdy` 全部通过，**HLS 首次验证成功**：电影 181288 字符、剧集 35629 字符，首分片为有效 TS（同步字节 `0x47`）。
- 之前记录的 `403 the source ip is forbidden` 未复现。同一时刻换 Referer（站内 / 无 / ivdy.com）、换刚签发的令牌，响应一致，说明当时是 CDN 按来源 IP 放行，不是插件问题。
- 落地域名由 `y2s52n7.com` 换到新的轮换域名，`IvdyApi.discover()` 正常跟随；CDN 主机也从 `ppvod01.kqgfbs.com` 换到 `ppvod011.blbtgg.com`，鉴权参数形态由 `whip` 变为 `auth_key`。这两处都说明写死域名或参数会随站点轮换失效。
- 新记录见 `dist/ivdy-verification.json`，仓库根同名文件已更新为本次结果（`hls_verified: true`）。
- 仍未单独确认：设备上的声音、拖动，以及弱性能盒子播放 1080 时的稳定性。

本轮没有修改运行源码，不需要重新发布 JAR；只更新了验证记录与文档。

### 9.1 同日：默认画质改为最高（本次发布）

- 仓库所有者要求「升级到 1080p，默认播放就是最高画质」，因此改为按 `resolution` 取最高档，`needLogin` 不再参与筛选，同画质优先 `flag=true`；没有可用媒体地址时报「当前剧集没有可用的播放线路」。
- 契约测试由「遵循未登录默认画质」改为「默认选择最高画质，不因登录标记降级」，并新增同画质优先默认线路、无媒体地址两条，爱电影契约共 17 项，全库 47 项。
- 实网复测：电影样例可选 1080/720/480，实际选中 **1080p**，清单 163689 字符；三档首分片均为有效 TS（1080 为 2541948 字节）。老友记样例当轮只提供 720，按规则取 720。
- 已按发布流程用 `prepare_release.py` 暂存、`verify_release.py` 校验同一次提交内的新 JAR、根 `config.json`、快照配置，并回读线上配置比对 MD5。
- **已由用户确认设备上可播放**（2026-10-29，反馈「目前可以播放了」）。仍未单独确认的是声音、拖动和弱性能盒子上的 1080 解码表现；若反馈卡顿，回退方式是把配置指回上一版固定 JAR。

## 10. 2026-10-29：新增豆瓣导航（本次发布）

> 收口状态：用户已在 FongMi 5.8.4 上确认「点卡片进入搜索页、自己选源」可用。关键是配置里的 `indexs: 1`（见 10.2），
> 代码侧无需再改；手机版进 `SearchActivity`，电视版进 `CollectActivity`。


用户要求在参考实现 `lushunming/AndroidCatVodSpider` 的 `Douban.java` 基础上加豆瓣导航。实现见 `Douban.java`，说明见 [DOUBAN.md](DOUBAN.md)。

接手时要先知道的四件事：

1. **`msearch:` 是宿主约定，不是本项目发明**。`VodPlaybackController.checkId` 遇到 `msearch:` 前缀直接 `detailEmpty(false)`，不请求详情。
   **前缀后面必须跟片名**（`msearch:老友记 第一季`）：仓库所有者另一个 JS 脚本这样写，真机进入搜索流程；放豆瓣数字 ID 会直接开播第一个同名结果。
   当前宿主分支只用前缀、不解析后缀，所以别以本地读到的源码为唯一依据——以真机行为为准。
   同理 `detailContent` 返回 `{"list":[]}` 且**不带 `msg`**，否则 `detailEmpty(hasMsg())` 会把空详情当最终结果。
2. **`searchable` 必须是 0**。否则搜索结果还是 `msearch:` 卡片，点进去又触发搜索，绕回自己。
   参考实现同样设为 0（`json/index.json` 的「豆瓣仅推荐」）。
3. **接口只认微信小程序 UA**。同一 `apikey` 配 Chrome UA 稳定返回 HTTP 400 / `code 997`；交替实测确认过。
   不能把 `Douban.UA` 换成通用的 `SiteHttp.UA`，因此 `SiteHttp.open` 增加了带 UA 的重载——这是本次唯一改动的共用代码。
4. **封面是直连地址加 `@Referer=` 后缀**，由宿主 `ImgUtil.getUrl` 解析出请求头再加载，不要改回走 `Proxy`。
   2026-10-29 曾误判「封面必须带 `Accept: image/*`」，已更正：那些 418 是连续请求触发的 IP 级限流，不是缺头。

第一版把封面接到了 `Proxy` 上，并为此抽出 `Proxy.url`、把 `Ikanbot.image` 改成调用它。设备上看不到豆瓣封面后已整体还原：
`Proxy.java`、`Ikanbot.java` 与加豆瓣之前逐字节一致，豆瓣改为直连 URL。加这个站点最终只新增 `Douban.java` 和一处 `SiteHttp` 重载。

关于「点卡片没有选源」：第一版把 `msearch:` 后面写成豆瓣数字 ID，真机点开直接播了第一个同名结果。
仓库所有者指出他另一个 JS 脚本能把点击变成搜索，对照后差别就在后缀——JS 写的是 `msearch:` + **片名**。
现已照此对齐，并同步去掉 `detailContent` 的 `msg`。以后遇到「点卡片行为不对」，先比对这个脚本的约定，
不要只根据本地宿主源码下结论：当前分支不解析后缀，但真机行为说明宿主链路里还有别处在用这段内容。

### 10.2 点卡片进搜索页靠的是 `indexs: 1`

宿主用 `Site.isIndex()`（即配置里 `indexs == 1`）区分「导航站」：手机版点卡片走 `SearchActivity`，
电视版走 `CollectActivity`（都带 keyword）；没有这个字段就直接 `VideoActivity` 并自动播第一个同名结果。
2026-10-29 在 Android TV 模拟器 + FongMi 5.8.4 上做了三组对照（我们的 JAR / 仓库所有者的 JS / 其 39 站完整配置），
行为差异全部由这个字段解释。**配置里删掉 `indexs` 就会退回“点一下就播”。**

分类设计上把剧集筛选与综艺筛选拆成两个分类：豆瓣的 `tags` 之间是「与」关系，
电视剧形式与综艺形式放在同一组会互相冲突，选两个就查不到东西。

验证：75 项契约检查（30 + 17 + 28）全过；`--douban` 七个分类各 20 条、榜单 10 条、Top250 第二页、封面 270x405 直连成功。
仍未确认：真机上的卡片点击、跨站搜索结果聚合与遥控器操作。

### 10.1 站点顺序

`package.py` 的 `sites` 顺序即首页 Tab 顺序，第一个是默认页。2026-10-29 按仓库所有者要求把 `douban` 调到第一，
次序为 `douban`、`ikanbot_fresh`、`ivdy`。这次只改配置，没有重新打包 JAR，线上 MD5 仍是 `4c420eb0…`。

注意：豆瓣 `searchable=0`，所以点卡片后的同名搜索不会把豆瓣算进去，自动选中哪个来源仍由 `ikanbot_fresh`、`ivdy` 的相对顺序决定；
想让 1080p 的爱电影优先，需要把它挪到爱看机器人之前（当前未改）。

## 11. 2026-10-29：爱看机器人提速

用户反馈同一个站，仓库所有者的 drpy3 源（`爱看机器人.js`）明显比我们的 Java 快。对比后按四处结构差异改：

1. **加缓存**：首页/分类/搜索 180 秒、详情 120 秒。此前完全没有缓存，每次切 Tab、每次回看都重拉整页。
   实测详情首次 9.3 秒、**命中缓存 0 毫秒**。
2. **封面改走站点自己的封面代理** `imgp2.ikanbot.eu.org/i/<ID>?u=<base64url>`，与 JS 源一致。
   之前每张封面都要「客户端 → 本插件代理 → 源站」绕一圈，一页 24 张就是 24 次多余往返；
   而且 `Proxy.proxy` 每张图都做一次 DNS 校验、`image()` 每张卡片做一次反射。`i` 只是缓存名，传任意数字都行。
3. **WebView 降级从 90 秒收到 25 秒**。直连失败时会开 WebView，90 秒等于让用户干等一分半。
4. **入口域名保持 `v.ikanbot.com`**。曾试图直连 `www.ikanbot.com` 省一次 301，实测 www 的详情页直接超时，
   而 `v` 会 301 到可用的 www1。`SiteHttp` 跟随一次后把 `origin` 更新成落地域名，后续不再吃 301。**不要改成 www。**

`PosterTest` 已改为检查站点封面代理这条真实链路（10 季全部返回可解码 JPEG）。
未做：搜索翻页仍靠 `next` 游标（JS 用 `?p=N` 直达，但实测该参数返回空页，没敢用）。
遗留：15 秒读超时在该站抽风时会直接报错；若长期如此需要单独评估是否放宽。
