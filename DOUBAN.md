# 豆瓣导航 · 豆瓣推荐

FongMi 影视 5.x 入口：`com.github.catvod.spider.Douban`，配置使用 `csp_Douban`，站点 key 为 `douban`。
与另外两个站点共用一个构建 JAR。

这是一个**导航站点**：只负责浏览和挑片，点开卡片后用片名去影视站点搜索，本身不提供详情页和播放地址。

## 为什么卡片是 `msearch:`

宿主（FongMi）对 `vod_id` 有约定：以 `msearch:` 开头时不请求详情，直接用 `vod_name` 触发全站搜索。
源码见 `app/src/main/java/com/fongmi/android/tv/playback/vod/VodPlaybackController.java` 的 `checkId`：

```java
if (id.isEmpty() || id.startsWith(SEARCH_PREFIX)) detailEmpty(false);
```

所以卡片的 `vod_id` 是 `msearch:<片名>`，例如 `msearch:老友记 第一季`。
前缀后面跟的是片名，这一点照抄仓库所有者另一个已验证可用的 JS 脚本
（[Fun/js/豆瓣推荐.js](https://github.com/tardlk/Fun/blob/main/js/%E8%B1%86%E7%93%A3%E6%8E%A8%E8%8D%90.js)）：它的写法就是 `vod_id: 'msearch:' + item.title`，
在真机上能进入搜索流程；早先本实现放的是豆瓣数字 ID，真机上会直接开播第一个同名结果。
**不要把它改回豆瓣 ID。**

同源约定还有一条：`detailContent` 返回 `{"list":[]}`，**不要带 `msg`**。
宿主 `onDetail` 用 `detailEmpty(result.hasMsg())` 判断，带提示语会被当成最终结果而中断后续流程。

宿主对 `msearch:` 的默认兜底是「自动搜索并播第一个片名完全相等的来源」（`VodFallbackPolicy` 的
`setSelectFirstSource(true)` → `nextSource()`），能不能走到「先搜索再选源」，取决于宿主版本对前缀后面这段内容的处理；
保持一致的做法是像 JS 脚本那样把片名放进去，而不是断言某个具体行为。

## 为什么必须带 `indexs: 1`

宿主把 `indexs == 1` 的站点当「导航站」，点卡片时不直接进播放页，而是带着片名跳到检索页面：

| 版本 | 点卡片后 |
|---|---|
| 手机版 | `TypeFragment` → `SearchActivity.start(name)`，搜索页 |
| 电视版 | `TypeFragment` → `CollectActivity.start(name)`，按关键词的聚合页 |

没有这个字段时，宿主走 `VideoActivity.start(...)`，由播放控制器自动搜同名结果并**直接开播第一个**。
2026-10-29 实测：同一台设备、同一批影视站，只把站点配置从我们这版换成仓库所有者的 JS 版（带 `indexs: 1`），
点击行为就变成进搜索页。差别只在这一个字段，与爬虫是 Java 还是 JS 无关。

**不要删这个字段**，否则退化成“点一下就播”，用户失去选源机会。

## 为什么 `searchable: 0`

本站点如果开启搜索，搜索结果仍然是 `msearch:` 卡片，点进去又触发一次全站搜索，会绕回自己形成循环。
lushunming 的参考实现同样设为不可搜索（`json/index.json` 中 `"searchable": 0`），本项目保持一致。
`searchContent` 保留实现但只返回空列表，避免宿主换版本后拿到 `null`。

## 接口

根地址 `https://frodo.douban.com/api/v2`，公共参数 `?apikey=0ac44ae016490db2204ce0a042db2916`。

| 用途 | 路径 | 取值 |
|---|---|---|
| 首页推荐 | `/subject_collection/subject_real_time_hotest/items` | `subject_collection_items` |
| 热门电影 | `/movie/hot_gaia` | `items`；`sort=recommend/time/rank`，`area=全部/华语/欧美/韩国/日本` |
| 电影筛选 | `/movie/recommend` | `items`；`sort=T/S/R`，`tags` |
| 剧集/综艺筛选 | `/tv/recommend` | `items`；`tags` 以 `电视剧` 或 `综艺` 开头 |
| 集合（热播/榜单） | `/subject_collection/<集合名>/items` | `subject_collection_items` |

分页统一为 `start=(页码-1)*20` 与 `count=20`，总页数按响应里的 `total` 计算。
`total` 在推荐接口上封顶 500（25 页），集合接口给的是真实条数（如 Top250 为 250）。

集合名：`tv_hot`（综合）、`tv_domestic`、`tv_american`、`tv_japanese`、`tv_korean`、`tv_animation`、
`show_hot`、`show_domestic`、`show_foreign`、
`movie_real_time_hotest`、`movie_weekly_best`、`movie_top250`、
`tv_real_time_hotest`、`tv_chinese_best_weekly`、`tv_global_best_weekly`、
`show_chinese_best_weekly`、`show_global_best_weekly`。
榜单只接受这份白名单，避免把外部传入的字符串直接拼进路径。

## 必须保留的请求身份（容易踩的一处）

**接口只认微信小程序 UA**。同一个 `apikey` 用普通浏览器 UA 请求会返回 HTTP 400、正文 `{"code":997,"msg":"invalid_request_997"}`。
实测交替发送微信 UA 与 Chrome UA：微信 UA 稳定 200，Chrome UA 稳定 997。所以 `Douban.UA` 不能换成通用的 `SiteHttp.UA`。
Referer 用 `https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html`。

## 封面为什么是直连加 `@Referer=`

`vod_pic` 形如 `https://img3.doubanio.com/.../p480747492.jpg@Referer=https://movie.douban.com/`。
宿主 `ImgUtil.getUrl` 会从这个地址里取出 `@Referer=` / `@User-Agent=` 加为请求头，再把后缀去掉后才加载
（`app/src/main/java/com/fongmi/android/tv/utils/ImgUtil.java`）。因此封面由客户端直接向豆瓣 CDN 取，不经过本项目的图片代理。

这样做的原因：参考实现也是这么写的，而本项目的图片代理对豆瓣是多余的一跳。**不要把封面改回走 `Proxy`。**
2026-10-29 曾误判「封面必须带 `Accept: image/*` 才不被拒」，实测不带该头、不带 Referer 都是 200；
当时那些 418（正文 `cdn error 001`）是短时间内连续请求触发的 IP 级限流，静默几十秒即恢复。

## 字段与展示

- 片名取 `title`；`id` 必须是纯数字才是影片条目，`type=chart` 的榜单条目（如 `movie_scifi`）没有海报，直接跳过。
- 海报优先 `pic.normal`，没有时用 `cover_url`（搜索类结果才有，带签名）。
- 备注拼成 `豆瓣 9.7 · 2026`；没有评分时只显示年份，都没有时留空，不显示「评分：0」。

## 验证与限制

```bash
bash build.sh
bash test.sh            # 75 项本地检查，其中豆瓣 28 项
bash test.sh --douban   # 实网：首页、七个分类、筛选、榜单第二页、真实封面
```

2026-10-29 实网结果：七个分类各 20 条（榜单默认 10 条），筛选与 Top250 第二页正常，封面 270x405、20432 字节（直连）。
记录见 `dist/douban-verification.json`。

限制：

- 豆瓣对短时间大量请求会返回 997。本实现把它翻成「豆瓣请求过于频繁（997），请稍后再试」，没有做本地缓存；快速连续翻页仍可能触发。
- 豆瓣没有公开的稳定接口承诺，`apikey`、集合名和字段都可能变化；失效时先按 [REFERENCES.md](docs/REFERENCES.md) 第 6 节重新定位公开客户端。
- 只做导航：不解析详情页、不解析播放地址，也不保存任何账号 Cookie。
- 仓库所有者已在 FongMi 5.8.4 上确认「点卡片进入搜索页、自己选源」可用（2026-10-29）。
- 仍未在真机确认：跨站搜索结果的聚合表现与遥控器操作手感（电视版走的是 `CollectActivity`）。
  机器可用时用 AVD `boxtest`（电视 1080p）复现。
