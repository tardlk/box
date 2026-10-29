package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;

public final class IvdyLiveTest {
    private static final JSONObject report = new JSONObject();
    private static JSONArray entries(String name, String json, int page) throws Exception {
        JSONObject response = new JSONObject(json);
        JSONArray list = response.optJSONArray("list");
        if (list == null || list.length() == 0) throw new AssertionError(name + ": " + response.optString("msg", "空列表"));
        if (page > 0 && response.getInt("page") != page) throw new AssertionError(name + " 页码错误");
        report.put(name, list.length());
        System.out.println("IVDY LIVE PASS " + name + " " + list.length() + " 条");
        return list;
    }
    public static void main(String[] args) throws Exception {
        Ivdy spider = new Ivdy();
        report.put("checked_at", Instant.now().toString());
        JSONObject home = new JSONObject(spider.homeContent(true));
        if (home.getJSONArray("class").length() != 5 || home.getJSONObject("filters").length() != 5) throw new AssertionError("分类或筛选不完整");
        entries("home", spider.homeVideoContent(), 0);
        for (String type : new String[]{"1", "2", "3", "4", "88"}) entries("category_" + type, spider.categoryContent(type, "1", true, null), 1);
        JSONArray page2 = entries("movie_page_2", spider.categoryContent("1", "2", true, null), 2);
        HashMap<String, String> extend = new HashMap<>();
        extend.put("v_class", "动作"); extend.put("area", "中国大陆"); extend.put("year", "2026");
        entries("filtered_movie", spider.categoryContent("1", "1", true, extend), 1);
        JSONArray search = entries("search_page_1", spider.searchContent("老友记", false, "1"), 1);
        entries("search_page_2", spider.searchContent("老友记", false, "2"), 2);
        String tv = "";
        for (int i = 0; i < search.length(); i++) if (search.getJSONObject(i).getString("vod_name").equals("老友记1")) tv = search.getJSONObject(i).getString("vod_id");
        if (tv.isEmpty()) throw new AssertionError("第一季不在搜索结果内");
        String[][] samples = {{"movie", "147582"}, {"tv", tv}};
        JSONArray media = new JSONArray();
        boolean anyManifest = false;
        for (String[] sample : samples) {
            JSONObject vod = entries("detail_" + sample[0], spider.detailContent(Collections.singletonList(sample[1])), 0).getJSONObject(0);
            report.put(sample[0] + "_episode_count", vod.getString("vod_play_url").split("#").length);
            String poster = vod.getString("vod_pic");
            HttpURLConnection image = SiteHttp.open(poster, "https://ivdy.com/");
            try {
                int status = image.getResponseCode();
                byte[] bytes = SiteHttp.read(image, 4 * 1024 * 1024);
                boolean webp = bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
                boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
                boolean png = bytes.length >= 8 && (bytes[0] & 255) == 137 && new String(bytes, 1, 3, StandardCharsets.US_ASCII).equals("PNG");
                if (status != 200 || bytes.length < 1000 || !(webp || jpeg || png)) throw new AssertionError("封面内容不是有效图片");
                report.put(sample[0] + "_poster", new JSONObject().put("status", status).put("bytes", bytes.length).put("mime", image.getContentType()));
            } finally { image.disconnect(); }
            String episode = vod.getString("vod_play_url").split("#")[0];
            String id = episode.substring(episode.indexOf('$') + 1);
            JSONObject play = new JSONObject(spider.playerContent("默认线路", id, Collections.emptyList()));
            if (play.optInt("parse", -1) != 0 || !SiteHttp.mediaUrl(play.optString("url"))) throw new AssertionError(play.toString());
            JSONObject item = new JSONObject().put("sample", sample[0]).put("api_play_url", "passed");
            String[] parts = id.split(":");
            JSONObject offered = new IvdyApi().get("/anonymous/v2/video/episode/url",
                    Ivdy.params("clientType", "1", "id", parts[1], "nid", parts[2]));
            JSONArray tiers = new JSONArray();
            JSONArray offeredList = offered.optJSONArray("list");
            if (offeredList != null) for (int i = 0; i < offeredList.length(); i++) {
                JSONObject tier = offeredList.optJSONObject(i);
                if (tier != null) tiers.put(new JSONObject().put("resolution", tier.optInt("resolution"))
                        .put("needLogin", tier.optBoolean("needLogin")).put("flag", tier.optBoolean("flag")));
            }
            JSONObject chosen = IvdyParser.playable(offered);
            item.put("tiers", tiers).put("selected_resolution", chosen.optInt("resolution"));
            JSONObject headers = new JSONObject(play.getString("header"));
            HttpURLConnection connection = SiteHttp.open(play.getString("url"), headers.getString("Referer"));
            try {
                int status = connection.getResponseCode();
                item.put("manifest_http_status", status).put("host", URI.create(play.getString("url")).getHost());
                if (status != 200) {
                    InputStream error = connection.getErrorStream();
                    byte[] message = new byte[512];
                    int n = error == null ? 0 : error.read(message);
                    item.put("error", n > 0 ? new String(message, 0, n, StandardCharsets.UTF_8) : "HTTP " + status);
                    System.out.println("IVDY MEDIA BLOCKED " + sample[0] + " HTTP " + status + " " + item.optString("error"));
                } else {
                    String manifest = new String(SiteHttp.read(connection, 2 * 1024 * 1024), StandardCharsets.UTF_8);
                    if (!manifest.trim().startsWith("#EXTM3U")) throw new AssertionError("不是有效 HLS 清单");
                    anyManifest = true;
                    item.put("manifest_characters", manifest.length());
                    System.out.println("IVDY MEDIA PASS " + sample[0] + " HLS " + manifest.length());
                }
            } finally { connection.disconnect(); }
            media.put(item);
        }
        report.put("media", media).put("hls_verified", anyManifest).put("android_device_tested", false);
        Files.write(Paths.get("dist/ivdy-verification.json"), (report.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
        if (!anyManifest) System.exit(2);
    }
}
