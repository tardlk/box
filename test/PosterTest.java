package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import javax.imageio.ImageIO;

/** Opt-in live regression: use the actual search parser and the player's image proxy. */
public final class PosterTest {
    public static void main(String[] args) throws Exception {
        SiteHttp http = new SiteHttp();
        String html = http.get(SiteHttp.ROOT + "/search?q=" + java.net.URLEncoder.encode("老友记", "UTF-8"));
        JSONArray entries = SiteParser.page(html, true, value -> value, http.origin()).list;
        if (entries.length() == 0) throw new AssertionError("搜索没有返回影片");
        JSONArray checked = new JSONArray();
        boolean firstSeason = false;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String url = entry.getString("vod_pic");
            Object[] response = Proxy.proxy(Collections.singletonMap("image", Proxy.register(url)));
            if (!Integer.valueOf(200).equals(response[0]) || !response[1].toString().startsWith("image/")) {
                throw new AssertionError(entry.getString("vod_name") + ": " + response[0]);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream stream = (InputStream) response[2]) {
                byte[] chunk = new byte[8192];
                int size;
                while ((size = stream.read(chunk)) != -1) buffer.write(chunk, 0, size);
            }
            byte[] bytes = buffer.toByteArray();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() < 100 || image.getHeight() < 100) throw new AssertionError("不是有效海报: " + url);
            checked.put(new JSONObject().put("title", entry.getString("vod_name")).put("url", url)
                    .put("status", response[0]).put("bytes", bytes.length).put("width", image.getWidth()).put("height", image.getHeight()));
            if (entry.getString("vod_name").equals("老友记 第一季")) {
                firstSeason = true;
                Files.write(Paths.get("build/friends-season1.jpg"), bytes);
            }
            System.out.println("POSTER PASS " + entry.getString("vod_name") + " " + image.getWidth() + "x" + image.getHeight());
        }
        if (!firstSeason) throw new AssertionError("搜索结果缺少第一季，未覆盖本次故障");
        JSONObject report = new JSONObject().put("checked_at", Instant.now().toString()).put("query", "老友记")
                .put("first_season_verified", firstSeason).put("posters", checked);
        Files.write(Paths.get("dist/poster-check.json"), (report.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
