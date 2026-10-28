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

当前版本已通过直接取址与 HLS 清单访问检查；Android 真机解码、声音、拖动和 WebView 路径仍需实际设备验证。

## 构建

源码在 `src/`，使用 `bash build.sh` 构建、`bash test.sh` 测试；需要 JDK 17 和 Android SDK。
构建产物写入 `dist/`，仓库根目录文件为当前发布版本。

上游站点实现参考：[lushunming/AndroidCatVodSpider](https://github.com/lushunming/AndroidCatVodSpider/blob/multiThread/app/src/main/java/com/github/catvod/spider/Ikanbot.java)。
