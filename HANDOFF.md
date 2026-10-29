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

两个站点共用一个 JAR，历史命名仍叫 `ikanbot.jar`，不表示只含 ikanbot。
当前运行源码基线提交：[7484e1a](https://github.com/tardlk/box/commit/7484e1a44a6d34e30ce51f21cb230e614e825347)。
本次交接只整理文档和开发工具，现有线上 JAR 不需要更换。

## 2. 已完成与未完成

| 项目 | 当前证据 | 下一位 AI 不能误判的部分 |
|---|---|---|
| 爱看机器人 `csp_Ikanbot` | 用户明确反馈浏览、点击、搜索、播放可用 | 不是旧 AI 的残留工程；请在当前实现上修改 |
| 老友记第一季海报 | 旧豆瓣地址 403；改路径后 10 季海报代理均返回可解码图片 | 封面非空字符串不代表图片可用 |
| 爱电影 `csp_Ivdy` | 分类、筛选、分页、搜索、详情、封面、选集和取址接口通过实网检查 | **HLS 未验证通过**，CDN 返回 `403 / the source ip is forbidden`，原站播放器在测试网络也失败 |
| 爱电影画质 | 匿名接口默认标清，高清/蓝光 `needLogin=true` | 没有实现登录，不能宣称支持需要登录的画质 |
| 本地测试 | 原有 30 项 + 爱电影 16 项，共 46 项通过（2026-10-28） | 历史记录不能当成本轮新测试；没有连接 Android 真机 |

如果继续修爱电影播放，先在设备所在网络对照原站，再比较接口与媒体请求；保留失败正文。
不要把返回 `parse=0` 或获得 `.m3u8` 地址当作播放成功。

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
| `src/stubs/.../Spider.java` | 本地最小编译接口；实际能力以 FongMi 宿主源码为准 |
| `build.sh` / `scripts/package.py` | 编译、D8、依赖合并、DEX 检查、JAR 和配置生成 |
| `test/` / `test.sh` | 契约测试与按需执行的实网测试 |
| `IVDY.md` / `DEVELOPMENT.md` | 两个站点的具体实现与历史验证说明 |

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
3. 对照 `Ivdy.java` 或 `Ikanbot.java` 写新的公开 Spider 子类，把解析逻辑和网络逻辑分开。
4. `csp_Xxx` 对应 `com.github.catvod.spider.Xxx`，保留无参构造；按 [SPI 格式](docs/JAR_GUIDE.md) 返回 JSON。
5. 在 `scripts/package.py` 增加站点配置及入口类存在检查；不要覆盖已有两站。
6. 用真实响应的最小样本做解析/签名/分页回归；实网验证图片实际字节和媒体清单，记录失败。
7. 生成新版本发布目录，连同源码、文档和记录一次提交；校验加速配置实际下载到新 JAR。

不要先用另一个项目的整个 JAR 当黑箱依赖，再声称实现完毕。这个工程已包含自己需要的 Jsoup。

## 6. 按需验证与接手优先级

```bash
bash test.sh             # 46 项本地检查；先构建
bash test.sh --posters   # ikanbot 老友记 10 季图片代理，检查真实图片
bash test.sh --live      # ikanbot 首页/分类/搜索/详情/HLS
bash test.sh --ivdy      # 爱电影完整接口；没有可访问 HLS 时退出码 2
```

每个实网开关只执行对应检查，不是把所有站点都跑一遍。
测试写到 `dist/` 的记录是新结果；仓库根同名记录是历史发布证据，不要悄悄刷新日期。

后续优先级：爱电影设备网络上的播放确认 → ikanbot 原生 WebView 与遥控器交互确认。
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
