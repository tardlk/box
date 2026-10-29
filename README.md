# box

FongMi 影视 5.x 配置与 Java 爬虫，包含「爱看机器人」和「爱电影」。

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
加载后选择「爱看机器人」或「爱电影」。

## 当前验证

- 爱看机器人：用户已反馈查看、搜索、播放可用；老友记第一季封面路径已修复，10 季海报代理实测通过。
- 爱电影：首页、五个分类、筛选、分页、搜索、电影和剧集详情、封面、选集、播放接口已接通。
  仅接入未登录可用画质，本轮接口默认提供标清，高清和蓝光需要登录。
- 爱电影的视频 CDN 在当前测试网络返回 HTTP 403：`the source ip is forbidden`；原站播放器同样失败。
  **尚未验证爱电影实际播放成功**，需在设备所在网络确认。
- 共 46 项本地契约检查通过；Android 画面、声音、拖动仍需真机验证。

## 源码与构建

使用 `bash build.sh` 构建、`bash test.sh` 测试；需 JDK 17 和 Android SDK。
实网检查爱电影：`bash test.sh --ivdy`。媒体清单未通过时退出码为 2。
构建产物在 `dist/`；根目录 JAR 与 JSON 为当前发布版本。

- [构建与原有实现说明](DEVELOPMENT.md)
- [爱电影实现与限制](IVDY.md)
- [爱电影实网记录](ivdy-verification.json)
- [加速配置](https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json)
- [原始配置](https://raw.githubusercontent.com/tardlk/box/main/config.json)

爱看机器人参考：[lushunming/AndroidCatVodSpider](https://github.com/lushunming/AndroidCatVodSpider/blob/multiThread/app/src/main/java/com/github/catvod/spider/Ikanbot.java)。
爱电影按 [ivdy.com](https://ivdy.com) 当前网页客户端公开接口实现。
