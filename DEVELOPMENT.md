# 爱看机器人 — FongMi 影视 5.x 插件

本目录是本次新建的独立工程。运行入口为 `com.github.catvod.spider.Ikanbot`，
配置中的类名为 **`csp_Ikanbot`**，注意大小写。

## 使用

`dist/` 包含：

- `ikanbot.jar`：插件，包含 Jsoup；不依赖另一个爬虫 JAR。
- `ikanbot.jar.md5`：本次产物的 MD5。
- `config.json`：可导入影视的单站点配置。
- `build-report.json`、`verification.json`、`media-check.json`：构建和验证记录。

把 `ikanbot.jar` 和 `config.json` 放在同一个可访问的 HTTP 目录中，
在影视的配置地址导入 `config.json` 的完整 URL。配置里的 JAR 使用相对地址，
随配置所在目录解析；不要将配置与 JAR 分开放置。

新配置的站点 key 为 `ikanbot_fresh`，便于避免命中旧配置的缓存。

## 详情取址

1. 使用上游实现的入口 `https://v.ikanbot.com`，跟随站点重定向。
   本次实际落在 `https://www1.ikanbot.com`，后续接口、分页和播放 Referer 使用实际落地域名。
   请求 User-Agent 与上游 `Util.CHROME` 一致。
2. 请求详情页，读取它提供的影片 ID、类型和线路参数。
3. 使用页面当前 `get_tks` 的切片规则计算请求参数，调用 `/api/getResN`。
4. 解析资源组、版本和每集的媒体 URL；不执行接口返回的 JavaScript。
5. 直接请求失败时，默认打开一个**可见的 Android WebView 对话框**，让站点正常运行自己的脚本。
   如果出现验证，需要用户本人完成。页面生成线路后，只读取公开显示的名称和媒体链接，自动关闭窗口。
6. 取得媒体 URL 后，`playerContent` 返回 `parse:0` 和播放请求头。

对照的上游实现：
https://github.com/lushunming/AndroidCatVodSpider/blob/multiThread/app/src/main/java/com/github/catvod/spider/Ikanbot.java

上游也是“详情参数 → token → getResN → 媒体直链”，没有 WebView。
此工程保留完整 JSON 解析、搜索游标和可选网页后备；不复制上游正则只提取首个资源的做法。

网页路径会实际等待媒体线路，不创建“网页播放”占位选集。
窗口可取消，最多等待 90 秒；退出站点或线程中断时清理窗口和 WebView。
不会自动完成验证码，不导出浏览器 Cookie，也不下载额外的原生代理程序。

默认参数：

```json
"ext": {"webview": true}
```

需要只检查 HTTP 取址时，可改为 `false`。配置的 `timeout:120` 为网页加载和手动验证预留时间。
若影视无法提供当前 Activity、系统 WebView 无法运行页面，或站点始终不给出线路，会明确返回错误。

## 源码

| 文件 | 职责 |
|---|---|
| `Ikanbot.java` | Spider 入口、分类、搜索分页、详情调度、播放输出 |
| `SiteHttp.java` | HTTP 状态、站点会话、超时、gzip 和响应大小限制 |
| `SiteParser.java` | HTML、接口资源、分页游标和标准 JSON |
| `BrowserPage.java` | 可见 WebView、公开 DOM 读取、取消和销毁 |
| `Proxy.java` | 为已登记封面补请求头，支持实例和静态代理入口 |

`src/stubs/` 仅提供编译接口，不打入产物；运行时使用影视自带的 Spider。
FongMi 新版的 `getActivity()`、`getProxyUrl()` 通过可选接口调用，另有公开宿主辅助方法兼容路径。
所有 Android 端可执行代码均在源码中；无 `.so` 依赖。

## 构建

需要 JDK 17、Android SDK `platforms;android-30`、`build-tools;35.0.0`、Python 3。
默认读取用户 Library 下已安装的 JDK 和 Android SDK，也可设置：

```bash
export IKANBOT_JAVA_HOME=/path/to/jdk17
export IKANBOT_ANDROID_SDK=/path/to/android-sdk
bash build.sh
bash test.sh
bash test.sh --live
```

脚本直接执行 `javac → D8 → JAR`，任一步失败立即停止。
依赖从 Maven Central 下载，并核对固定 SHA-256。独立 JAR 的最低 DEX API 为 24。
Jsoup 的 MIT 许可证保存在 JAR 的 `META-INF/JSOUP-LICENSE.txt` 中。

## 本轮验证 — 2026-10-28

- 编译和 DEX 结构检查通过，确认入口类、封面代理和 Jsoup 已打包，Spider 编译桩未打包。
- **30 项本地契约检查通过**，涵盖分页、游标、资源 JSON、请求会话、重定向域名和错误处理。
- 《老友记》搜索结果 10 季封面经实际图片代理全部返回 HTTP 200，可解码为有效海报；第一季为 270×399。
  豆瓣旧 `movie_poster_cover/lpst` 路径已替换为 `photo/s_ratio_poster`，搜索与详情均适用。
  可通过 `bash test.sh --posters` 重现，详细结果见 `dist/poster-check.json`。
- 首页 24 条、电影第二页 24 条、搜索前两页各 10 条，实网检查通过。
- 新版 Java 实现通过完整实网取址：经作者入口到实际落地域名，取得《逃出布迪秀》的 **31 条线路**，
  并读取到第一条线路的有效 HLS 清单（70,181 字符）。本轮取址未使用 WebView。
- 在普通浏览器上，使用新网页读取逻辑取得《逃出布迪秀》的 **31 组、41 个媒体地址**。
- 从其中两条公开线路分别读取到 HLS 清单（HTTP 200），且取得媒体分片样本（HTTP 206、各 1024 字节）。
- **没有连接 Android 设备**：FongMi 中的 WebView 窗口、遥控器交互，以及画面、声音和拖动播放尚未验证。

桌面浏览器的渲染结果和媒体可访问性，不能代替 Android 真机验收。
