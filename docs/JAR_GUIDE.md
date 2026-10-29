# 怎么写 FongMi Spider JAR

本项目的源码都是可读 Java，运行入口位于 `src/main/java/com/github/catvod/spider/`。
先读一个完整实例 [Ivdy.java](../src/main/java/com/github/catvod/spider/Ivdy.java)，再看其 API 和解析器。
宿主接口的核对入口见 [资料索引](REFERENCES.md)，不要把本地编译桩当成宿主全部功能。

## 1. JAR 里到底是什么

```text
Java 8 源码
  └─ javac（JDK 17，android.jar + Spider 编译桩作为 classpath）
      └─ Java class 文件 + Jsoup 依赖
          └─ D8 --release --min-api 24
              └─ classes.dex
                  └─ ZIP/JAR（classes.dex、MANIFEST、Jsoup 许可证）
```

`build/plugin-classes.jar` 是 D8 的输入；`dist/ikanbot.jar` 才是交付给 FongMi 的产物。
本工程最低运行 API 是 24；`android-30` 是编译平台，两者不是同一含义。
`--release 8` 控制 Java 字节码兼容性，不会让新 JDK 的所有库在 Android 上自动可用。

打包边界：

| 内容 | 是否放进发布 JAR | 原因 |
|---|---|---|
| 新站 Spider、解析器、网络辅助类 | 是 | 本插件自身实现 |
| Jsoup 1.18.3 | 是 | 本插件解析依赖，D8 合并进 DEX |
| `src/stubs/` 的 Spider | 否 | 运行时继承宿主提供的真正 Spider |
| `org.json` 测试依赖 | 否 | JVM 测试需要，Android 已提供 JSON 实现 |
| `android.jar` | 否 | 编译平台接口 |
| `test/` | 否 | 桌面测试代码，不进 Android 产物 |

`package.py` 从 DEX 类定义表检查这些边界，而不是仅搜索 DEX 字符串。
当前打包只交付 `classes.dex`；如果将来新增大量依赖导致多 DEX，必须先修改打包与校验，不要静默漏掉 `classes2.dex`。

## 2. 准备工具链

需要 JDK 17、Python 3，以及 Android SDK 的：

```text
platforms;android-30
build-tools;35.0.0
```

通过 Android Studio 的 SDK Manager 或当前官方命令行工具安装；已安装 `sdkmanager` 的环境也可使用：

```bash
sdkmanager "platforms;android-30" "build-tools;35.0.0"
```

SDK 官方文档目前同时介绍 Android CLI 和既有 sdkmanager，安装方式以官方页面为准。
本工程只依赖最后的文件路径，不强制使用某一种安装工具。

```bash
export IKANBOT_JAVA_HOME=/absolute/path/to/jdk-17
export IKANBOT_ANDROID_SDK=/absolute/path/to/android-sdk
bash build.sh
bash test.sh
```

`build.sh` 会创建 `lib/` 并调用 `scripts/dependencies.py`。
依赖固定在 Maven Central，下载后逐个核对 SHA-256；失败时修复下载或调查依赖，不要删除校验绕过问题。

## 3. 新站入口怎么接

类名规则：公开、非抽象、可无参实例化的 `com.github.catvod.spider.NewSite`，配置写 `csp_NewSite`，大小写一致。
FongMi `JarLoader` 会实例化这个类，并调用继承自 `com.github.catvod.crawler.Spider` 的方法。

| 方法 | 输入与输出 | 项目实例 |
|---|---|---|
| `init(Context, String)` | 保存站点配置 `ext`，按需要初始化 | Ikanbot 的 WebView 开关 |
| `homeContent(boolean)` | 分类 `class`，可选筛选字典 `filters` | Ivdy 的五个分类 |
| `homeVideoContent()` | 首页推荐 `list` | Ivdy 合并推荐后按 ID 去重 |
| `categoryContent(String, String, boolean, HashMap)` | 分类 ID、页码、筛选参数 → 分页列表 | Ikanbot 的真实下一页；Ivdy 的 totalPage |
| `searchContent(String, boolean)` | 搜索第一页，委托分页重载 | 两个现有 Spider |
| `searchContent(String, boolean, String)` | 搜索词、页码 → 列表 | Ikanbot 保留下一页游标；Ivdy 使用 pageNum |
| `detailContent(List<String>)` | 影片 ID → 详情与选集 | Ivdy 的 episodeList |
| `playerContent(String, String, List<String>)` | 线路、选集标识 → 当前媒体 URL | Ivdy 点击时获取新签名 URL |
| `proxy(Map<String,String>)` | 可选，本地 HTTP 转发返回状态、类型与流 | Ikanbot 图片代理 |
| `destroy()` | 关闭会话资源、对话框、WebView | Ikanbot |

严格的方法签名见本项目编译桩与宿主源码。不要改成 `Map` 以为能覆盖参数类型为 `HashMap` 的方法。
`init` 的 context 不一定是 Activity；UI 必须切到主线程并正确清理，网络不能堵主线程。

## 4. 播放器 JSON 的最小格式

分类与筛选：

```json
{
  "class": [{"type_id": "movie", "type_name": "电影"}],
  "filters": {
    "movie": [{"key": "tag", "name": "类型", "value": [{"n": "全部", "v": ""}]}]
  }
}
```

影片列表：

```json
{
  "page": 1,
  "pagecount": 2,
  "limit": 24,
  "list": [{"vod_id": "123", "vod_name": "示例影片", "vod_pic": "https://images.example.com/poster.jpg", "vod_remarks": "更新至3集"}]
}
```

字段以实际结果为准。知道 `total` 就返回；只有“下一页”时可以只声明当前页/下一页，不编造总条数。
空搜索应该返回空列表，不应抓首页充数。

详情中的线路和剧集：

```json
{
  "list": [{
    "vod_id": "123", "vod_name": "示例影片", "vod_pic": "https://images.example.com/poster.jpg",
    "vod_year": "2026", "vod_area": "中国大陆", "vod_actor": "演员", "vod_director": "导演", "vod_content": "简介",
    "vod_play_from": "默认线路",
    "vod_play_url": "第1集$mysite:123:1#第2集$mysite:123:2"
  }]
}
```

上述域名和 ID 仅演示格式，不是可播放测试资源。真实数据必须从页面或接口取得。
`$` 分开选集名与选集标识，`#` 分集；多线路之间用 `$$$`，`vod_play_from` 与 `vod_play_url` 的线路数量一致。
对来源标题里的这些分隔符做清理。详情不一定要放最终 URL；有时效的地址应像 Ivdy 一样在播放时获取。

实际得到可用媒体 URL 后：

```java
JSONObject headers = new JSONObject().put("User-Agent", SiteHttp.UA).put("Referer", origin + "/");
return new JSONObject().put("parse", 0).put("url", mediaUrl).put("header", headers.toString()).toString();
```

项目采用 `header` 为 JSON 字符串的兼容格式。`parse=0` 只用于真正的媒体 URL；网页地址不能当作媒体直链。
当前媒体校验覆盖 m3u8/mp4，如新站实际采用其他媒体协议，先验证宿主支持再按需扩展。
错误返回有意义的 `msg`、空列表/空 URL，不能给失败接口补一个虚假的播放选集。

## 5. 网络、图片与动态页面

- 先看稳定入口的实际跳转。Ikanbot 和 Ivdy 各有独立域名发现逻辑，不要把某日落地域名永久写死。
- 站点需要哪些 UA、Referer、Cookie、参数，以当前正常网页请求为依据。Ivdy 的公开签名实现见 `IvdyApi.signature()`。
- 豆瓣海报可能是旧尺寸路径，补 Referer 仍会 403；当前 `SiteParser.poster()` 保留海报 ID 换路径。
- 图片代理要实测状态、正文与可解码性。Ivdy 的 WebP 即使 Content-Type 是 octet-stream 也可能是正确图片。
- FongMi 实例代理优先使用 `getProxyUrl(Map)`，把处理放在 Spider 的 `proxy()`；静态 `Proxy.proxy(Map)` 是兼容入口。
- Ikanbot 的 WebView 是用户可见的网页后备，只读站点公开渲染内容；需要本人完成验证时保留交互与取消。
- 不将登录限制或 CDN 拒绝当成“换个字符串就能成功”的解析问题，先定位失败层。

## 6. 最后把新站接进构建

1. 在 `src/main/java/.../spider/` 增加入口及辅助类。
2. 在 `scripts/package.py` 的 `config['sites']` 加新站，分配新的稳定 key 和匹配类名的 api。
3. 给入口增加 DEX 类定义检查，保留两个既有入口检查与编译桩排除检查。
4. `test/` 增加与故障相关的契约测试；如增加新的测试主类，`test.sh` 需显式调用它。
   该脚本编译所有 `test/*.java`，但不会自动执行每一个类。
5. `bash build.sh` → `bash test.sh` → 相关实网验证 → [准备与校验发布](RELEASING.md)。

构建成功不代表能播放：分别记录 JSON/图片、媒体清单/分片、设备实际播放这几层结果。
