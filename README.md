# box

爱看机器人 · FongMi 影视 5.x 配置与 Java 爬虫。

## 导入影视

在影视的设置中打开点播配置，填写：

```text
https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json
```

JSON 中的 JAR 地址同样使用 `https://gh-proxy.com/` 加速。

- [加速配置](https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json)
- [加速 JAR](https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/ikanbot.jar)
- [原始配置](https://raw.githubusercontent.com/tardlk/box/main/config.json)
- [构建与验证说明](DEVELOPMENT.md)

加载后选择「爱看机器人」。配置入口为 `csp_Ikanbot`。

当前版本修复了豆瓣旧封面路径造成的《老友记 第一季》无封面问题。实网检查搜索结果中的 10 季海报全部可用，30 项契约检查通过。

如加速服务仍缓存旧配置，可使用本次更新专用链接：

```text
https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config-20261028-poster.json
```

配置引用带 MD5 的固定版本 JAR 路径，避免旧文件缓存。用户已反馈查看、搜索和播放可用；本次封面修复仍待设备确认。

## 构建

源码在 `src/`，使用 `bash build.sh` 构建、`bash test.sh` 测试；需要 JDK 17 和 Android SDK。
构建产物写入 `dist/`，仓库根目录文件为当前发布版本。

上游站点实现参考：[lushunming/AndroidCatVodSpider](https://github.com/lushunming/AndroidCatVodSpider/blob/multiThread/app/src/main/java/com/github/catvod/spider/Ikanbot.java)。
