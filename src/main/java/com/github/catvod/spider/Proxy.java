package com.github.catvod.spider;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Image requests reference an in-memory ID, never an arbitrary URL parameter. */
public final class Proxy {
    private static final String IMAGE_ACCEPT = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8";
    private static final Map<String, String> images = Collections.synchronizedMap(new LinkedHashMap<String, String>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> item) { return size() > 1000; }
    });

    static String register(String url) {
        String id = UUID.randomUUID().toString();
        images.put(id, url);
        return id;
    }

    /**
     * Client-facing URL for an image that needs its own headers, e.g. Douban's image CDN
     * answers 418 unless the request looks like a browser image load.
     * Falls back to the original URL when the host offers no proxy entry point.
     */
    public static String url(Object spider, String tag, String original) {
        if (!SiteHttp.publicUrl(original)) return "";
        String asset = register(original);
        Map<String, String> params = Collections.singletonMap("image", asset);
        try {
            Object url = spider.getClass().getMethod("getProxyUrl", Map.class).invoke(spider, params);
            if (url instanceof String && !((String) url).isEmpty()) return (String) url;
        } catch (Exception ignored) { }
        try {
            Class<?> helper = Class.forName("com.github.catvod.Proxy");
            String base;
            try { base = (String) helper.getMethod("getUrl", boolean.class).invoke(null, true); }
            catch (NoSuchMethodException ignored) { base = (String) helper.getMethod("getUrl").invoke(null); }
            if (base != null && !base.isEmpty()) return base + "?do=" + tag + "&image=" + asset;
        } catch (Exception ignored) { }
        return original;
    }

    public static Object[] proxy(Map<String, String> params) throws Exception {
        String target = images.get(params.get("image"));
        if (target == null) return text(404, "图片引用已过期");
        HttpURLConnection connection = null;
        try {
            for (int redirect = 0; redirect < 4; redirect++) {
                if (!SiteHttp.publicUrl(target)) return text(400, "图片地址无效");
                String host = URI.create(target).getHost();
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                        return text(400, "图片目标不是公网地址");
                    }
                }
                connection = SiteHttp.open(target, host.endsWith("doubanio.com") ? "https://movie.douban.com/" : SiteHttp.ROOT + "/");
                connection.setRequestProperty("Accept", IMAGE_ACCEPT);
                connection.setInstanceFollowRedirects(false);
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (location == null) return text(502, "图片重定向失败");
                    target = URI.create(target).resolve(location).toString();
                    continue;
                }
                if (status != 200) return text(status >= 400 && status <= 599 ? status : 502, "图片请求失败");
                String mime = connection.getContentType();
                if (mime == null || !mime.startsWith("image/")) return text(502, "上游未返回图片");
                byte[] data = SiteHttp.read(connection, 8 * 1024 * 1024);
                return new Object[]{200, mime, new ByteArrayInputStream(data), Collections.singletonMap("Cache-Control", "max-age=3600")};
            }
            return text(502, "图片重定向次数过多");
        } finally { if (connection != null) connection.disconnect(); }
    }

    private static Object[] text(int status, String message) {
        return new Object[]{status, "text/plain; charset=utf-8", new ByteArrayInputStream(message.getBytes(java.nio.charset.StandardCharsets.UTF_8))};
    }
}
