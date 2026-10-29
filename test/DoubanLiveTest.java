package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import javax.imageio.ImageIO;

/** Opt-in live check: every Douban category, the home recommendation and one real poster. */
public final class DoubanLiveTest {
    private static final JSONObject report = new JSONObject();

    private static JSONArray entries(String name, String json, int page) throws Exception {
        JSONObject response = new JSONObject(json);
        JSONArray list = response.optJSONArray("list");
        if (list == null || list.length() == 0) throw new AssertionError(name + ": " + response.optString("msg", "空列表"));
        if (page > 0 && response.getInt("page") != page) throw new AssertionError(name + " 页码错误");
        report.put(name, list.length());
        System.out.println("DOUBAN LIVE PASS " + name + " " + list.length() + " 条");
        return list;
    }

    public static void main(String[] args) throws Exception {
        Douban spider = new Douban();
        report.put("checked_at", Instant.now().toString());
        JSONObject home = new JSONObject(spider.homeContent(true));
        if (home.getJSONArray("class").length() != 7) throw new AssertionError("分类不完整");

        JSONArray featured = entries("home", spider.homeVideoContent(), 1);
        JSONObject first = featured.getJSONObject(0);
        if (!first.getString("vod_id").startsWith(Douban.SEARCH_PREFIX)) throw new AssertionError("首页卡片缺少 msearch 前缀");
        report.put("sample", new JSONObject().put("name", first.getString("vod_name"))
                .put("remarks", first.getString("vod_remarks")));

        for (String type : new String[]{"hot_movie", "hot_tv", "hot_show", "filter_movie", "filter_tv", "filter_show", "rank"}) {
            entries(type, spider.categoryContent(type, "1", true, null), 1);
            Thread.sleep(400);
        }

        HashMap<String, String> extend = new HashMap<>();
        extend.put("类型", "科幻");
        extend.put("年代", "2026");
        entries("filter_movie_scifi_2026", spider.categoryContent("filter_movie", "1", true, extend), 1);

        HashMap<String, String> board = new HashMap<>();
        board.put("榜单", "movie_top250");
        entries("rank_top250", spider.categoryContent("rank", "1", true, board), 1);

        JSONObject second = new JSONObject(spider.categoryContent("rank", "2", true, board));
        report.put("rank_top250_page2", second.optJSONArray("list").length());
        System.out.println("DOUBAN LIVE PASS rank_top250_page2 " + second.optJSONArray("list").length() + " 条");

        // The card carries the "@Referer=" suffix the host strips before loading, so check the
        // address the client will actually request.
        String pic = first.getString("vod_pic").split("@")[0];
        java.net.HttpURLConnection connection = SiteHttp.open(pic, "https://movie.douban.com/");
        int status;
        String mime;
        byte[] bytes;
        try {
            status = connection.getResponseCode();
            mime = connection.getContentType();
            bytes = SiteHttp.read(connection, 4 * 1024 * 1024);
        } finally { connection.disconnect(); }
        java.awt.image.BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (status != 200 || mime == null || !mime.startsWith("image/") || image == null || image.getWidth() < 100) {
            throw new AssertionError("封面不是有效图片: " + status + " " + mime);
        }
        report.put("poster", new JSONObject().put("status", status).put("mime", mime)
                .put("bytes", bytes.length).put("width", image.getWidth()).put("height", image.getHeight()));
        System.out.println("DOUBAN LIVE PASS poster " + image.getWidth() + "x" + image.getHeight() + " " + bytes.length + " bytes");

        report.put("search_enabled", false).put("detail_is_navigation_only", true);
        Files.write(Paths.get("dist/douban-verification.json"),
                (report.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
