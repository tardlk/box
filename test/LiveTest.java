package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.Collections;

public final class LiveTest {
    private static JSONObject list(String name, String json, int page) throws Exception {
        JSONObject result = new JSONObject(json);
        JSONArray entries = result.optJSONArray("list");
        if (entries == null || entries.length() == 0 || !entries.getJSONObject(0).optString("vod_id").matches("[0-9]+")) {
            throw new Exception(name + ": " + result.optString("msg", "没有真实影片"));
        }
        if (result.getInt("page") != page) throw new Exception(name + ": 页码错误");
        System.out.println("LIVE PASS " + name + " " + entries.length() + " 条");
        return result;
    }

    public static void main(String[] args) throws Exception {
        Ikanbot spider = new Ikanbot();
        spider.init(null, "{\"webview\":false}");
        JSONObject home = list("首页", spider.homeVideoContent(), 1);
        list("电影第二页", spider.categoryContent("movie", "2", true, null), 2);
        list("搜索第一页", spider.searchContent("变形金刚", false, "1"), 1);
        list("搜索第二页", spider.searchContent("变形金刚", false, "2"), 2);
        String id = home.getJSONArray("list").getJSONObject(0).getString("vod_id");
        JSONObject detail = new JSONObject(spider.detailContent(Collections.singletonList(id)));
        JSONArray videos = detail.getJSONArray("list");
        if (videos.length() == 0) {
            System.out.println("LIVE BLOCKED 详情: " + detail.optString("msg"));
            System.out.println("WebView 路径需在 Android 设备验证；本轮没有把失败记成取址通过。");
            System.exit(2);
        }
        JSONObject vod = videos.getJSONObject(0);
        String[] lines = vod.getString("vod_play_url").split("\\$\\$\\$");
        System.out.println("LIVE PASS 真实详情: " + vod.getString("vod_name") + ", " + lines.length + " 条线路");
        boolean manifest = false;
        for (int index = 0; index < Math.min(3, lines.length); index++) {
            String episode = lines[index].split("#")[0];
            String url = episode.substring(episode.indexOf('$') + 1);
            JSONObject play = new JSONObject(spider.playerContent("线路", url, Collections.emptyList()));
            if (play.getInt("parse") != 0 || play.optString("url").isEmpty()) throw new Exception("播放入口未返回媒体地址");
            try {
                String body = new SiteHttp().get(url);
                if (body.trim().startsWith("#EXTM3U")) {
                    System.out.println("LIVE PASS HLS 清单: " + URI.create(url).getHost() + ", " + body.length() + " 字符");
                    manifest = true;
                    break;
                }
            } catch (Exception failure) { System.out.println("LIVE MEDIA FAILED 线路" + (index + 1) + ": " + failure.getMessage()); }
        }
        if (!manifest) throw new Exception("尚未取得有效 HLS 清单");
        System.out.println("LIVE NOTE Android 解码、声音、拖动以及 WebView 仍需真机确认");
    }
}
