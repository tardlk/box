package com.github.catvod.spider;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;

public final class ContractTest {
    private static int count;
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        count++;
        System.out.println("PASS " + description);
    }

    public static void main(String[] args) throws Exception {
        String card = "<div class='v-list'><a class='item' href='/play/123456'><img alt='样片' data-src='https://images.example.com/a.jpg'></a></div>";
        String next = "<div class='page-more'><a href='/hot/index-movie-热门-p-3.html'>下一页</a></div>";
        SiteParser.Page page = SiteParser.page(card + card + next, false, value -> value);
        check(page.list.length() == 1, "重复影片去重");
        check(page.list.getJSONObject(0).getString("vod_name").equals("样片"), "首页标题和封面解析");
        check(page.json(2).getInt("page") == 2 && page.json(2).getInt("pagecount") == 3, "当前页码和下一页状态正确");
        check(!page.json(2).has("total"), "未知总数不编造 total");
        SiteParser.Page search = SiteParser.page("<div class='media'><a href='/play/22'><img alt='搜索样片'></a></div>"
                + "<div class='page-more'><a href='/search?q=a&amp;p=2&amp;n=x%2By'>下一页</a></div>", true, value -> value);
        check(search.list.length() == 1 && search.next.contains("n=x%2By"), "搜索游标及编码保持原样");
        String legacyPoster = "https://img1.doubanio.com/view/movie_poster_cover/lpst/public/p2186920269.jpg";
        String currentPoster = "https://img1.doubanio.com/view/photo/s_ratio_poster/public/p2186920269.jpg";
        String friends = "<div class='media'><a href='/play/390655'><img alt='老友记 第一季' src='data:image/svg+xml,placeholder' data-src='" + legacyPoster + "'></a></div>";
        check(SiteParser.page(friends, true, value -> value).list.getJSONObject(0).getString("vod_pic").equals(currentPoster), "搜索第一季封面保留海报 ID 并替换豆瓣旧尺寸路径");
        check(SiteParser.metadata("<div class='result-info'><img data-src='" + legacyPoster + "'></div>", "390655", value -> value).getString("vod_pic").equals(currentPoster), "详情封面采用相同修正规则");
        check(SiteParser.poster(currentPoster + "?x=1").equals(currentPoster + "?x=1"), "现用封面路径与参数保持原样");
        String otherPoster = legacyPoster.replace("img1.doubanio.com", "images.example.com");
        check(SiteParser.poster(otherPoster).equals(otherPoster), "其他图片来源不套用豆瓣路径规则");
        check(SiteParser.page("<div class='page-more'><a href='https://evil.example/search'>下一页</a></div>", true, value -> value).next.isEmpty(), "不跟随跨站分页链接");
        check(SiteParser.id("https://www.ikanbot.com/play/12345?x=1").equals("12345"), "支持站内详情链接");
        check(SiteParser.id("https://www1.ikanbot.com/play/12345").equals("12345"), "支持作者入口重定向后的 www1 域名");
        check(SiteParser.page(card + next, false, value -> value, "https://www1.ikanbot.com").next.startsWith("https://www1.ikanbot.com/"), "分页使用实际重定向域名");
        check(SiteParser.id("https://other.example/play/12345").isEmpty(), "拒绝非站点影片链接");
        check(SiteParser.token("1011144", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdef").equals("CDEFGHIJMNOPQRSTWXYZ01236789abcd"), "页面 token 算法固定向量");
        boolean invalid = false;
        try { SiteParser.token("abcd", "short"); } catch (Exception expected) { invalid = true; }
        check(invalid, "页面参数失效时明确失败");

        JSONArray resources = new JSONArray().put(new JSONObject().put("flag", "test").put("url",
                "第1集$https://media.example.com/a.m3u8$test#第2集$https://media.example.com/b.mp4"
                + "#重复$https://media.example.com/a.m3u8#无效$http://127.0.0.1/p.mp4"));
        JSONArray groups = new JSONArray().put(new JSONObject().put("resData", resources.toString()))
                .put(new JSONObject().put("resData", resources))
                .put(new JSONObject().put("resData", new JSONObject().put("resData", resources).toString()));
        JSONObject lines = SiteParser.lines(groups);
        check(lines.getString("vod_play_from").split("\\$\\$\\$").length == 3, "数组、字符串和包装对象格式均可解析");
        String[] first = lines.getString("vod_play_url").split("\\$\\$\\$")[0].split("#");
        check(first.length == 2, "去除重复及无效媒体地址");
        check(first[0].equals("第1集$https://media.example.com/a.m3u8"), "保留剧集并去掉尾部 flag 字段");
        check(SiteHttp.mediaUrl("https://cdn.example.com:65/video/index.m3u8?x=1"), "支持站点公开 CDN 的非标准端口");
        check(!SiteHttp.mediaUrl("https://cdn.example.com/page.html") && !SiteHttp.mediaUrl("file:///sdcard/a.mp4"), "播放入口不把网页或本地文件当作直链");
        JSONObject metadata = SiteParser.metadata("<h1 id='video_title'>标题</h1><div class='result-info'><div class='detail'>"
                + "<h2 class='meta'>标题</h2><h3 class='meta'>English</h3><h3 class='meta'>2026</h3>"
                + "<h3 class='meta'>中国</h3><h3 class='meta'>导演 / 演员</h3></div></div>", "1", value -> value);
        check(metadata.getString("vod_year").equals("2026") && metadata.getString("vod_director").equals("导演 "), "详情年份与人物字段解析");

        Ikanbot spider = new Ikanbot();
        spider.init(null, "{\"webview\":false}");
        check(new JSONObject(spider.homeContent(true)).getJSONArray("class").length() == 2, "SPI 分类入口可调用");
        check(new JSONObject(spider.playerContent("线路1", "https://cdn.example.com/a.m3u8", Collections.emptyList())).getInt("parse") == 0, "真实媒体使用 parse=0");
        check(new JSONObject(spider.playerContent("线路1", "https://cdn.example.com/page", Collections.emptyList())).optString("url").isEmpty(), "错误地址不产生占位选集");
        check(new JSONObject(spider.detailContent(Collections.singletonList("bad-id"))).getJSONArray("list").length() == 0, "错误详情返回 msg 与空列表");
        check(Proxy.proxy(Collections.singletonMap("image", "missing"))[0].equals(404), "代理拒绝未登记图片引用");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cookie", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "session=public-test; Path=/");
            exchange.sendResponseHeaders(200, 2); exchange.getResponseBody().write("ok".getBytes()); exchange.close();
        });
        server.createContext("/check", exchange -> {
            byte[] body = String.valueOf(exchange.getRequestHeaders().getFirst("Cookie")).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/blocked", exchange -> {
            exchange.getResponseHeaders().set("cf-mitigated", "challenge");
            exchange.sendResponseHeaders(403, -1); exchange.close();
        });
        server.createContext("/gzip", exchange -> {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) { gzip.write("压缩内容".getBytes(StandardCharsets.UTF_8)); }
            byte[] body = buffer.toByteArray();
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            SiteHttp client = new SiteHttp();
            client.get(base + "/cookie");
            check(client.get(base + "/check").contains("session=public-test"), "HTTP 会话保留站点自身 Cookie");
            check(client.get(base + "/gzip").equals("压缩内容"), "HTTP gzip 正文正确解码");
            boolean blocked = false;
            try { client.get(base + "/blocked"); } catch (SiteHttp.Failure failure) { blocked = failure.verification; }
            check(blocked, "站点验证单独标识，不视为成功响应");
        } finally { server.stop(0); }
        // 豆瓣封面直连图床并补 Referer：图床不带 Referer 是 418，站点封面代理又慢又不稳
        Ikanbot ik = new Ikanbot();
        String doubanPoster = "https://img1.doubanio.com/view/photo/s_ratio_poster/public/p2186920269.jpg";
        String proxied = ik.poster(doubanPoster);
        check(proxied.equals(doubanPoster + "@Referer=https://movie.douban.com/"),
                "豆瓣封面直连图床并附加宿主认识的 Referer 后缀");
        check(!proxied.contains("imgp2") && !proxied.contains("/i/"),
                "豆瓣封面不再绕站点封面代理");
        String legacy = ik.poster("https://img1.doubanio.com/view/movie_poster_cover/lpst/public/p2186920269.jpg");
        check(legacy.equals("https://img1.doubanio.com/view/photo/s_ratio_poster/public/p2186920269.jpg"
                + "@Referer=https://movie.douban.com/"), "旧版海报路径先修正再补 Referer");
        check(!ik.poster("https://images.example.com/a.jpg").contains("imgp2"), "非豆瓣封面仍走原有代理逻辑");
        check(!ik.poster("https://images.example.com/a.jpg").contains("@Referer="), "非豆瓣封面不附加豆瓣 Referer");

        System.out.println("CONTRACT PASS " + count);
    }
}
