# box

FongMi 影视 5.x 配置与 Java 爬虫，包含「爱看机器人」「爱电影」两个影视站和「豆瓣导航」。

## 给接手的 AI / 开发者

先读 **[HANDOFF.md](HANDOFF.md)**。它汇总当前状态、源码位置、构建与发布步骤、测试边界及资料入口。

- [JAR 编写指南与 SPI 返回格式](docs/JAR_GUIDE.md)
- [资料索引：宿主源码、参考实现、官网文档](docs/REFERENCES.md)
- [发布、校验和回滚](docs/RELEASING.md)

## 导入影视

在设置 → 点播配置中填写固定地址：

```text
https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json
```

如固定地址仍缓存旧版，本次更新专用地址为：

```text
https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config-20261028-ivdy.json
```

JSON 与 JAR 都使用 `gh-proxy.com` 加速。配置引用带 MD5 的固定版本 JAR，避免旧二进制缓存。
站点顺序为「豆瓣导航」「爱看机器人」「爱电影」，第一个是打开影视后默认停留的分类页。

## 豆瓣导航

「豆瓣导航」用来挑片：首页是实时热门，另有热门电影、热播剧集、热播综艺、电影筛选、
剧集筛选、综艺筛选和榜单（含豆瓣 Top250）。点开卡片后，影视用片名去搜索：手机版进入搜索页，
电视版进入按关键词聚合的收集页，再由你选来源播放。
它本身不提供详情和播放，也不参与全站搜索——这是为了避免点开卡片又绕回搜索。
让点击走搜索而不是直接播放的是站点配置里的 `indexs: 1`，删掉这个字段就会变成自动播第一个同名结果。

剧集/综艺的筛选分成了两个分类，因为豆瓣的标签是「与」关系，把电视剧形式和综艺形式放进同一组会互相冲突。

## 当前验证

- 爱看机器人：用户已反馈查看、搜索、播放可用；老友记第一季封面路径已修复。
  首页、分类、搜索、详情新增短 TTL 缓存，详情命中缓存为 0 毫秒。
  **封面直连豆瓣 CDN，并带 `@Referer=` 后缀**：豆瓣图床不带 Referer 返回 418。
  曾先后试过「本插件代理」和「站点封面代理 `imgp2`」，前者多一跳、后者是境外 Worker
  （实测单张 4–40 秒、约一半不响应），都会让封面加载不出来；现已改为直连豆瓣。
  2026-10-29 在 Android TV 模拟器上确认过「补请求头后」的海报正常显示，直连方案尚未在设备上复现。
- 爱电影：首页、五个分类、筛选、分页、搜索、电影和剧集详情、封面、选集、播放接口已接通。
- 爱电影播放：**用户已在设备上确认可播放**（2026-10-29）。此前记录的
  `403 the source ip is forbidden` 来自当时测试网络的 IP 限制，换网络后已不复现。
- 爱电影画质：默认播放该集可用的最高分辨率（1080p 优先），同画质时取站点默认线路。
  接口虽把 720/1080 标为 `needLogin`，匿名请求实际可播，实测选中 1080p 并取到有效分片。
- 豆瓣导航：七个分类与首页推荐通过实网检查，筛选、Top250 翻页、封面直连均正常；
  2026-10-29 实网每类 20 条，封面 270x405。
- 豆瓣导航点击：**仓库所有者已在 FongMi 5.8.4 上确认点卡片进入搜索页并可自行选源**（2026-10-29），
  依赖站点配置 `indexs: 1`；手机版走搜索页，电视版走按关键词聚合的收集页。
- 共 80 项本地契约检查通过（爱看机器人 35、爱电影 17、豆瓣 28）。
  设备上的声音、拖动与弱性能盒子的 1080 解码表现未单独确认。

## 源码与构建

使用 `bash build.sh` 构建、`bash test.sh` 测试；需 JDK 17 和 Android SDK。
实网检查：爱看机器人 `bash test.sh --live`／`--posters`，爱电影 `--ivdy`，豆瓣 `--douban`。
媒体清单未通过时退出码为 2。
构建产物在 `dist/`；根目录 JAR 与 JSON 为当前发布版本。

- [构建与原有实现说明](DEVELOPMENT.md)
- [爱电影实现与限制](IVDY.md)
- [豆瓣导航实现与限制](DOUBAN.md)
- [爱电影实网记录](ivdy-verification.json)
- [豆瓣实网记录](douban-verification.json)
- [加速配置](https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json)
- [原始配置](https://raw.githubusercontent.com/tardlk/box/main/config.json)

爱看机器人参考：[lushunming/AndroidCatVodSpider](https://github.com/lushunming/AndroidCatVodSpider/blob/multiThread/app/src/main/java/com/github/catvod/spider/Ikanbot.java)。
爱电影按 [ivdy.com](https://ivdy.com) 当前网页客户端公开接口实现。
