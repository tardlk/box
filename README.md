# box

FongMi 影视 5.x 的配置与 Java 爬虫，一个 JAR 里装三个站点：**豆瓣导航**（挑片）、**爱看机器人**、**爱电影**。

## 导入影视

设置 → 点播配置，填固定地址：

```text
https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json
```

固定地址若仍命中旧缓存，改用当前发布快照：

```text
https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config-20261029-douban-covers.json
```

JSON 与 JAR 都走 `gh-proxy.com` 加速；配置引用带 MD5 的固定版本 JAR，避免旧二进制缓存。
站点顺序是「豆瓣导航」→「爱看机器人」→「爱电影」，第一个是打开影视后默认停留的页面。

## 三个站点

**豆瓣导航**用来挑片：首页实时热门，另有热门电影、热播剧集、热播综艺、电影/剧集/综艺筛选和榜单（含 Top250）。
点卡片后影视拿片名去搜索——手机版进搜索页，电视版进按关键词聚合的收集页，由你选源播放。
它本身没有详情和播放，也不参与全站搜索（否则点开又绕回搜索）。
让点击走搜索的开关是站点配置里的 `indexs: 1`，删掉就会变成自动播第一个同名结果。
剧集筛选和综艺筛选拆成两个分类，因为豆瓣标签是「与」关系，混在一组会互相冲突。

**爱看机器人**是完整影视站：浏览、筛选、搜索、选集、播放，详情页直连失败时会开一个可见的 WebView 让站点自己跑脚本。

**爱电影**接的是 ivdy.com 的匿名公开接口：五个分类、筛选、搜索、详情、选集、取址，默认播放该集可用的最高画质。

## 当前状态

三个站点都能浏览与播放；爱看机器人、爱电影、豆瓣导航的播放与选源已由仓库所有者确认。

已知未验证：真机上的声音、拖动、弱性能盒子播 1080p 的稳定性；电视版遥控器操作手感。
完整的状态表、验证边界和已知问题见 **[HANDOFF.md](HANDOFF.md) 第 2 节**。

## 源码与构建

```bash
bash build.sh   # 需 JDK 17、Android SDK（platforms;android-30、build-tools;35.0.0）、Python 3
bash test.sh    # 80 项契约检查
```

实网检查按站点分开跑：`--live`（爱看机器人）、`--posters`（封面）、`--ivdy`（爱电影）、`--douban`（豆瓣）。
构建产物在 `dist/`；仓库根的 JAR 与 `config*.json` 是当前发布版本。

## 文档

- [HANDOFF.md](HANDOFF.md)：接手必读——当前状态、硬约束、源码地图、构建与发布流程、待办。
- [docs/HISTORY.md](docs/HISTORY.md)：每次改动的原因、证据和当时没验证的部分。
- [docs/JAR_GUIDE.md](docs/JAR_GUIDE.md)：SPI 方法签名、打包边界、播放器 JSON 格式。
- [docs/RELEASING.md](docs/RELEASING.md)：发布、校验、回滚。
- [docs/REFERENCES.md](docs/REFERENCES.md)：宿主源码、参考实现、站点定位词、官方文档。
- [DEVELOPMENT.md](DEVELOPMENT.md) / [DOUBAN.md](DOUBAN.md) / [IVDY.md](IVDY.md)：各站点实现细节。
- 实网记录：[ivdy-verification.json](ivdy-verification.json)、[douban-verification.json](douban-verification.json)、[poster-check.json](poster-check.json)

参考实现：[lushunming/AndroidCatVodSpider](https://github.com/lushunming/AndroidCatVodSpider/blob/multiThread/app/src/main/java/com/github/catvod/spider/Ikanbot.java)（爱看机器人）、
[ivdy.com](https://ivdy.com) 当前网页客户端公开接口（爱电影）。
