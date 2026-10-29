package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashMap;

/** Opt-in timing probe: which call actually costs the seconds. */
public final class TimingProbe {
    private static long time(String label, Call call) throws Exception {
        long start = System.nanoTime();
        String result = call.run();
        long ms = (System.nanoTime() - start) / 1_000_000;
        int items = 0;
        try { items = new JSONObject(result).optJSONArray("list") == null ? 0 : new JSONObject(result).getJSONArray("list").length(); }
        catch (Exception ignored) { }
        System.out.printf("TIMING %-28s %5d ms  items=%d%n", label, ms, items);
        return ms;
    }

    interface Call { String run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Ikanbot spider = new Ikanbot();
        SiteHttp http = new SiteHttp();

        time("html 首页", () -> String.valueOf(http.get(http.site("/")).length()));
        time("html 分类电影热门", () -> String.valueOf(http.get(http.site("/hot/index-movie-%E7%83%AD%E9%97%A8.html")).length()));
        time("html 搜索老友记", () -> String.valueOf(http.get(http.site("/search?q=%E8%80%81%E5%8F%8B%E8%AE%B0")).length()));
        time("html 详情 1393859", () -> String.valueOf(http.get(http.site("/play/1393859")).length()));

        time("spider homeVideoContent", spider::homeVideoContent);
        time("spider homeVideoContent(2)", spider::homeVideoContent);
        HashMap<String, String> hot = new HashMap<>();
        hot.put("tag", "热门");
        time("spider category movie p1", () -> spider.categoryContent("movie", "1", true, hot));
        time("spider category movie p2", () -> spider.categoryContent("movie", "2", true, hot));
        time("spider search p1", () -> spider.searchContent("老友记", false, "1"));
        time("spider detail", () -> spider.detailContent(Collections.singletonList("1393859")));

        // 封面代理：一次 DNS + 一次 TLS
        String poster = new JSONObject(spider.homeVideoContent()).getJSONArray("list").getJSONObject(0).optString("vod_pic");
        System.out.println("卡片封面: " + poster);
        String raw = "https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2186920269.jpg";
        long start = System.nanoTime();
        Object[] response = Proxy.proxy(Collections.singletonMap("image", Proxy.register(raw)));
        long first = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        Proxy.proxy(Collections.singletonMap("image", Proxy.register(raw)));
        long second = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("TIMING 封面代理 首次 %d ms / 复用 %d ms  status=%s%n", first, second, response[0]);
    }
}
