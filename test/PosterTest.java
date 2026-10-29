package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import javax.imageio.ImageIO;

/**
 * Opt-in live regression for the poster path the client actually uses.
 * Cards hand out the site's own poster proxy, so this checks those URLs return real images.
 */
public final class PosterTest {
    public static void main(String[] args) throws Exception {
        Ikanbot spider = new Ikanbot();
        JSONArray entries = new JSONObject(spider.searchContent("老友记", false, "1")).getJSONArray("list");
        if (entries.length() == 0) throw new AssertionError("搜索没有返回影片");
        JSONArray checked = new JSONArray();
        boolean firstSeason = false;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String url = entry.getString("vod_pic");
            if (!url.startsWith("https://imgp2.ikanbot.eu.org/")) {
                throw new AssertionError("封面没走站点代理: " + entry.getString("vod_name") + " " + url);
            }
            HttpURLConnection connection = SiteHttp.open(url, SiteHttp.ROOT + "/");
            int status;
            String mime;
            byte[] bytes;
            try {
                status = connection.getResponseCode();
                mime = connection.getContentType();
                bytes = SiteHttp.read(connection, 4 * 1024 * 1024);
            } finally { connection.disconnect(); }
            if (status != 200 || mime == null || !mime.startsWith("image/")) {
                throw new AssertionError(entry.getString("vod_name") + ": " + status + " " + mime);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() < 100 || image.getHeight() < 100) throw new AssertionError("不是有效海报: " + url);
            checked.put(new JSONObject().put("title", entry.getString("vod_name")).put("url", url)
                    .put("status", status).put("bytes", bytes.length).put("width", image.getWidth()).put("height", image.getHeight()));
            if (entry.getString("vod_name").equals("老友记 第一季")) {
                firstSeason = true;
                Files.write(Paths.get("build/friends-season1.jpg"), bytes);
            }
            System.out.println("POSTER PASS " + entry.getString("vod_name") + " " + image.getWidth() + "x" + image.getHeight());
        }
        if (!firstSeason) throw new AssertionError("搜索结果缺少第一季，未覆盖本次故障");
        JSONObject report = new JSONObject().put("checked_at", Instant.now().toString()).put("query", "老友记")
                .put("path", "site-poster-proxy").put("first_season_verified", firstSeason).put("posters", checked);
        Files.write(Paths.get("dist/poster-check.json"), (report.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
