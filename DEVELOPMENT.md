# 爱看机器人 `csp_Ikanbot` 实现说明

> **该站点 2026-10-30 起暂停启用**（手机上一直转圈、原因未定位，见 [docs/HISTORY.md](docs/HISTORY.md) 第 8 节）。
> 本文保留实现细节，供重新启用时参考。

站点入口类 `com.github.catvod.spider.Ikanbot`，配置里写 `csp_Ikanbot`，站点 key 是 `ikanbot_fresh`。
三个站点的整体状态、构建、发布流程见 [HANDOFF.md](HANDOFF.md)；本文只讲这个站点怎么实现的、哪些坑踩过。

## 详情取址

1. 入口用 `https://v.ikanbot.com`，跟随站点重定向。观察时落在 `https://www1.ikanbot.com`，
   之后的接口、分页和播放 Referer 都用实际落地域名（`SiteHttp` 会把 `origin` 换成落地域名，后续不再吃 301）。
   请求 UA 与上游 `Util.CHROME` 一致。
2. 请求详情页，读取页面给出的影片 ID、类型和线路参数。
3. 用页面当前 `get_tks` 的切片规则算出请求参数，调用 `/api/getResN`。
4. 解析资源组、版本和每集媒体 URL；不执行接口返回的 JavaScript。
5. 直连失败时默认打开一个**可见的 Android WebView 对话框**，让站点自己跑脚本。
   若出现验证，需要用户本人完成；页面生成线路后只读公开显示的名称和媒体链接，然后自动关闭。
6. 拿到媒体 URL 后，`playerContent` 返回 `parse:0` 和播放请求头。

上游参考实现走的是同一条「详情参数 → token → getResN → 媒体直链」，但没有 WebView。
本工程保留完整 JSON 解析、搜索游标和可选网页后备，不复制上游「正则只提第一个资源」的做法。

网页路径会真的等媒体线路，不造「网页播放」占位选集。窗口可取消，**最多等 25 秒**
（原为 90 秒，等于让用户干等一分半）。退出站点或线程中断时清理窗口与 WebView。
不会自动过验证码，不导出 Cookie，也不下载额外的原生程序。

```json
"ext": {"webview": true}
```

只想检查 HTTP 取址时改成 `false`。配置的 `timeout:120` 是给网页加载和手动验证留的。
影视拿不到当前 Activity、系统 WebView 跑不起来，或站点始终不给线路时，会明确返回错误而不是假装成功。

## 缓存

首页/分类/搜索 180 秒、详情 120 秒。之前完全没有缓存，每次切 Tab、每次回看都重拉整页。
实测详情首次 9.3 秒（上游慢），**命中缓存 0 毫秒**。

## 封面

豆瓣域名**直连豆瓣 CDN**，地址后追加 `@Referer=https://movie.douban.com/`，由宿主 `ImgUtil`
解析成请求头并剥掉后缀，客户端自己取图；非豆瓣域名仍走本插件代理。

豆瓣图床只要 Referer：不带或只带 UA 都返回 **418**，带 Referer 才 200（每张约 0.1 秒）。
**不要改回走站点封面代理 `imgp2.ikanbot.eu.org`**：那是境外 Cloudflare Worker，
实测单张 4–40 秒、约一半请求完全不响应，正是「封面加载不出来」的原因。
详见 [docs/HISTORY.md](docs/HISTORY.md) 第 6 节。

历史注记：该代理的 `i` 参数是**缓存键**（早先误记为「只认 `u`」）。
用「A 的 `i` + B 的 `u`」请求返回的是 A 的图；只有 `i` 未命中才去取 `u`。
所以换 `s_ratio_poster` 想拿小图无效，缓存会直接吐大图。

## 入口域名

入口直连 `https://www.ikanbot.com`。**不要改成 `v.ikanbot.com`**：`v` 会 301 到 `www1`，
而 `www1` 在不同网络下的速度差别很大——仓库所有者的手机访问 `www1` 极慢，表现为「一直转圈」，
同一时刻 `www` 正常。

早先记录过「www 详情页超时、v 跳到 www1 可用」，那是当时本机链路的结论，换个网络就反过来了。
现在的实测（`/play/390655` 3/3 通过，15210 字节，`current_id`/`e_token`/`mtype`/`get_tks` 齐全）
表明 www 的详情页没问题。详见 [docs/HISTORY.md](docs/HISTORY.md) 第 7 节。

`SiteHttp` 仍会跟随一次跳转并把落地域名写进 `origin`，所以万一 www 哪天也开始 301，不会立刻失效。
写死单一域名终究是脆的，真正的解法是入口探测，暂未实施（原因见 HISTORY 7.5）。

## 源码分工

| 文件 | 职责 |
|---|---|
| `Ikanbot.java` | Spider 入口、分类、搜索分页、详情调度、播放输出、短 TTL 缓存 |
| `SiteHttp.java` | HTTP 状态、站点会话、超时、gzip 和响应大小限制 |
| `SiteParser.java` | HTML 卡片、接口资源、分页游标和标准 JSON |
| `BrowserPage.java` | 可见 WebView、公开 DOM 读取、取消和销毁 |
| `Proxy.java` | 为已登记封面补请求头，支持实例和静态代理入口 |

`src/stubs/` 只提供编译接口，不打进产物；运行时用影视自带的 `Spider`。
FongMi 新版的 `getActivity()`、`getProxyUrl()` 通过可选接口调用，另有公开宿主辅助方法兼容路径。
Android 端可执行代码全在源码里，无 `.so` 依赖。
