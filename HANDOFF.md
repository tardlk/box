# box：接手说明

FongMi 影视 5.x 的 Java 爬虫插件。一个 JAR 里装三个站点：豆瓣导航（挑片）、爱看机器人、爱电影。

本文只讲**当前状态与规矩**。每次改动「当时为什么这么做、证据是什么、没验证什么」见 [docs/HISTORY.md](docs/HISTORY.md)。

## 1. 交付物

| 项 | 值 |
|---|---|
| 仓库 | [tardlk/box](https://github.com/tardlk/box)，分支 `main` |
| 导入地址（固定） | `https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json` |
| 当前 JAR | `jar/box-5d4df9465d4ec35bc519dcc83d1a9f50.jar`（182430 字节） |
| 当前发布快照 | `config-20261030-referer-fix.json` |
| 契约检查 | 80 项（爱看机器人 35 + 爱电影 17 + 豆瓣 28） |

JAR 是 Android 插件：Java 源码 → D8 生成 `classes.dex` → 装进扩展名为 `.jar` 的 ZIP。
只用 `javac` 打普通桌面 JAR 不行。历史文件名仍叫 `ikanbot.jar`，但它装的是三个站点。

| 站点 key | 入口类 | 状态 |
|---|---|---|
| `douban` | `csp_Douban` | 导航站，`indexs:1`、`searchable:0`，排第一位 |
| `ikanbot_fresh` | `csp_Ikanbot` | 爱看机器人，HTML + 资源接口，可选 WebView 后备 |
| `ivdy` | `csp_Ivdy` | 爱电影，匿名 API + 客户端签名 |

## 2. 当前状态

已验证的（有记录，但不等于本轮重测）：

- 爱看机器人：首页/分类/搜索/详情/HLS 实网通过；详情曾因带 Referer 被 Cloudflare 判成 403，已修（HISTORY 第 9 节）。所有者手机上此前一直转圈，修复后待其确认。
- 爱电影：五个分类、筛选、分页、搜索、电影与剧集详情、封面、选集、取址全部通过；HLS 清单验证通过；**所有者确认设备可播放**；默认取最高画质。
- 豆瓣导航：七个分类、筛选、Top250 翻页、封面直连通过；**所有者确认点卡片进搜索页并可选源**。

还没验证的：

- 真机上的声音、拖动、弱性能盒子播 1080p 的稳定性（只有模拟器与接口层证据）。
- 电视版（leanback）点豆瓣卡片进 `CollectActivity` 的实际体验；遥控器操作手感。
- 跨站搜索结果的聚合表现。
- 爱看机器人搜索第二页在「非本机网络」下的表现（本机验证过，但本机链路不稳）。

已知问题：

- 爱看机器人 15 秒读超时在站点抽风时会直接报错。放宽还是加重试**尚未决定**，代价是要重新发布 JAR，见 HISTORY 第 4.4 节。
- 站点与部分图床在本机会间歇性「返回 200 但正文传到一半不收尾」。这是链路问题，不是解析 bug；别为了绕它去改超时然后声称验证过。

## 3. 硬约束

1. **豆瓣配置里的 `indexs: 1` 不能删。** 宿主靠它把点卡片变成「进搜索页」；手机版进 `SearchActivity`，电视版进 `CollectActivity`。删了就退化成自动播第一个同名结果。
2. **豆瓣接口只认微信小程序 UA。** 换普通浏览器 UA 返回 400 / `code 997`。见 `Douban.UA`。
3. **豆瓣封面直连 + `@Referer=` 后缀。** 宿主 `ImgUtil` 会把后缀变成请求头再剥掉。不要改回走本插件代理。
4. **爱看机器人封面直连豆瓣 CDN + `@Referer=https://movie.douban.com/`。** 豆瓣图床不带 Referer 返回 **418**。不要改回走站点代理 `imgp2.ikanbot.eu.org`——它是境外 Worker，实测单张 4–40 秒、约一半不响应（HISTORY 第 6 节）。
5. **爱看机器人入口用 `www.ikanbot.com`。** 不要改回 `v`——`v` 会 301 到 `www1`，而 `www1` 在部分网络里极慢（仓库所有者手机就是这样，表现为一直转圈）。`www` 自己 200，列表与详情都直出。`SiteHttp` 仍会跟随一次跳转并把落地域名换进 `origin`。
6. **三个站点共用一个 JAR，已有 key 不能改、不能互相覆盖。** 新增站点必须用新 key。
7. **爱电影默认最高画质是仓库所有者的决定**（2026-10-29）。不要按接口的 `needLogin` 标注把它降回 720。
8. **豆瓣 `searchable` 必须是 0。** 否则搜索结果还是 `msearch:` 卡片，点进去又触发搜索，绕回自己。
9. **`detailContent` 返回 `{"list":[]}` 且不带 `msg`。** 宿主用 `detailEmpty(hasMsg())` 判断，带提示语会被当成最终结果。
10. **不要为了「看起来快」加长缓存或改超时，然后声称验证过。** 测不了就说没测。

## 4. 源码地图

| 路径 | 用途 |
|---|---|
| `src/main/java/com/github/catvod/spider/Ikanbot.java` | 爱看机器人入口：分类、搜索分页、详情调度、播放输出、短 TTL 缓存 |
| 同目录 `SiteParser.java` | HTML 卡片、真实分页游标、token、资源 JSON、豆瓣海报路径修正 |
| 同目录 `SiteHttp.java` | HTTP 会话、落地域名、UA、gzip、超时、媒体 URL 校验 |
| 同目录 `Proxy.java` | 登记海报 ID、补请求头、返回图片字节 |
| 同目录 `BrowserPage.java` | Android 可见 WebView、公开 DOM 读取、取消与销毁 |
| 同目录 `Ivdy.java` / `IvdyApi.java` / `IvdyParser.java` | 爱电影：域名发现、公开签名、接口转播放器 JSON |
| 同目录 `Douban.java` | 豆瓣导航：分类与筛选、`msearch:` 卡片、封面直连 |
| `src/stubs/.../Spider.java` | 本地最小编译接口；实际能力以宿主源码为准，未定义的方法不代表宿主没有 |
| `build.sh` / `scripts/package.py` | 编译、D8、依赖合并、DEX 边界检查、JAR 与本地配置生成 |
| `test/` / `test.sh` | 契约测试 + 按需实网测试 |
| `scripts/prepare_release.py` / `verify_release.py` | 发布暂存与校验 |

## 5. 构建与测试

```bash
git clone https://github.com/tardlk/box.git && cd box
bash build.sh          # 产物在 dist/
bash test.sh           # 80 项契约检查
```

需要 JDK 17、Android SDK 的 `platforms/android-30` 与 `build-tools/35.0.0`、Python 3。
可用 `IKANBOT_JAVA_HOME` / `IKANBOT_ANDROID_SDK` 覆盖默认路径；`build.sh` 会下载并校验固定版本的 Jsoup 与测试用 JSON 库。

实网开关，每个只跑对应站点，不是全跑：

```bash
bash test.sh --live      # 爱看机器人：首页、分类第二页、搜索两页、详情、HLS
bash test.sh --posters   # 爱看机器人：老友记 10 季封面（豆瓣直连，检查真实图片）
bash test.sh --ivdy      # 爱电影：完整接口 + HLS（没有可用 HLS 时退出码 2）
bash test.sh --douban    # 豆瓣：七分类、筛选、榜单翻页、封面
```

`dist/` 里的记录是新结果；仓库根同名记录是发布证据，不要悄悄改日期。

**本机跑实网测试必须先给 JVM 指定代理。** 这台机器的出口是本地代理（TUN + `HTTP_PROXY`），而 Java 不读 `http_proxy`：

```bash
export JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=<端口> -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=<端口>"
```

不给就会看到 `Remote host terminated the handshake`——那是网络路径问题，**不要改插件代码**。
端口从 `env | grep -i proxy` 取当前值。安卓真机用设备自己的网络，不要把代理写进 `SiteHttp`。

## 6. 发布

完整步骤见 [docs/RELEASING.md](docs/RELEASING.md)。要点：

1. `bash build.sh` → `bash test.sh` → 按改动选实网开关。
2. `python3 scripts/prepare_release.py --output build/release --snapshot config-<日期>-<主题>.json`
3. `python3 scripts/verify_release.py --directory build/release`
4. 按 `release-manifest.json` 复制到仓库根与 `jar/`，源码、配置、JAR 放**同一次提交**。
5. push 后用 `--config-url ... --expected-md5 <新 MD5>` 回读线上确认。

只改配置时**不要重新打包 JAR**，用户就不用重新下载。
`package.py` 的 ZIP 条目带打包时间，同一份源码多次打包 MD5 会不同——核对源码一致性要比 `classes.dex` 的 SHA-256，不要比外层 JAR MD5。
不要覆盖已发布的 `jar/box-<md5>.jar` 与历史 `config-日期-主题.json`；回滚靠新增提交，不改写历史。

## 7. 加新站点

1. 先在真实页面确认稳定入口、落地域名、分类、搜索分页、详情选集、播放请求、封面请求。
2. 判断数据在 HTML、公开 JSON API 还是渲染后的 DOM。
3. 对照 `Ivdy.java`、`Ikanbot.java` 或 `Douban.java` 写新的 `Spider` 子类，解析与网络分开。导航型站点参考 `Douban.java`（`msearch:` + `indexs:1` + `searchable:0`）。
4. 类名 `com.github.catvod.spider.Xxx` 对应配置 `csp_Xxx`，保留无参构造。
5. 在 `scripts/package.py` 增加站点配置与入口类检查，同步 `scripts/verify_release.py` 的必需 `csp_` 集合；不要覆盖已有三站。
6. 用真实响应的最小样本做解析回归；实网验证要检查真实图片字节和媒体清单。
7. 一次提交里带源码、文档、记录，再走发布流程。

不要拿别的项目的整包 JAR 当黑箱依赖，本工程已自带 Jsoup。

## 8. 待办优先级

1. 真机确认：电视版豆瓣卡片的 `CollectActivity` 与遥控器操作。
2. 弱性能盒子播 1080p 的稳定性、声音、拖动。
3. 决定爱看机器人读超时策略（HISTORY 第 4.4 节有数据与代价）。
4. 站点若改版，先复现具体入口，不要因为一个站点故障重写另一个已可用的站点。

## 9. 资料入口

- [docs/REFERENCES.md](docs/REFERENCES.md)：宿主源码（固定提交）、站点当前页面、公开 JS 定位词、官方文档。站点域名与 JS chunk 会变，按里面的定位方法重查。
- [docs/JAR_GUIDE.md](docs/JAR_GUIDE.md)：SPI 方法签名、打包边界、播放器 JSON 格式。
- [docs/RELEASING.md](docs/RELEASING.md)：发布、校验、回滚。
- [docs/HISTORY.md](docs/HISTORY.md)：每次改动的原因与证据。
- [DEVELOPMENT.md](DEVELOPMENT.md)、[DOUBAN.md](DOUBAN.md)、[IVDY.md](IVDY.md)：各站点实现细节。
