package com.github.catvod.spider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

final class SiteHttp {
    // 入口直连 www，不再经 v 跳板：v 会 301 到 www1，而 www1 在不同网络下快慢差别很大
    // （仓库所有者实测 www1 在 Edge 里极慢，www 正常；本机则相反）。www 自己 200，列表与详情都直出，
    // 详情页的 current_id / e_token / mtype / get_tks 齐全。
    // 仍然跟随一次跳转：万一 www 哪天也开始 301，落地域名会被记进 origin，后续不再吃跳转。
    static final String ROOT = "https://www.ikanbot.com";
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private volatile String origin = ROOT;

    String origin() { return origin; }
    String site(String path) { return origin + path; }
    static boolean siteHost(String host) { return host != null && host.matches("(?i)(?:(?:v|www[0-9]*)\\.)?ikanbot\\.com"); }

    static class Failure extends IOException {
        final boolean verification;
        Failure(String message, boolean verification) { super(message); this.verification = verification; }
    }

    String get(String url) throws IOException {
        HttpURLConnection connection = open(url, origin + "/");
        try {
            URI uri = URI.create(url);
            synchronized (cookies) {
                for (Map.Entry<String, List<String>> entry : cookies.get(uri, java.util.Collections.emptyMap()).entrySet()) {
                    connection.setRequestProperty(entry.getKey(), join("; ", entry.getValue()));
                }
            }
            int status = connection.getResponseCode();
            URI effective = URI.create(connection.getURL().toString());
            if (siteHost(effective.getHost()) && "https".equalsIgnoreCase(effective.getScheme())) {
                origin = "https://" + effective.getAuthority();
            }
            synchronized (cookies) { cookies.put(effective, connection.getHeaderFields()); }
            boolean challenge = "challenge".equalsIgnoreCase(connection.getHeaderField("cf-mitigated"));
            if (status == 403 || challenge) throw new Failure("站点要求网页验证（HTTP " + status + "）", true);
            if (status < 200 || status >= 300) throw new Failure("站点请求失败：HTTP " + status, false);
            String body = new String(read(connection, 4 * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            if (body.contains("cf-chl-") || body.contains("Just a moment") || body.contains("Sorry, you have been blocked")) {
                throw new Failure("站点要求网页验证", true);
            }
            if (body.trim().isEmpty()) throw new Failure("站点返回了空内容", false);
            return body;
        } finally { connection.disconnect(); }
    }

    static HttpURLConnection open(String url, String referer) throws IOException {
        return open(url, referer, UA);
    }

    /** Some public clients (Douban's mini program) only answer their own User-Agent. */
    static HttpURLConnection open(String url, String referer, String userAgent) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Referer", referer);
        connection.setRequestProperty("Accept-Encoding", "gzip");
        return connection;
    }

    static byte[] read(HttpURLConnection connection, int limit) throws IOException {
        InputStream stream = connection.getInputStream();
        if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) stream = new GZIPInputStream(stream);
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (output.size() + count > limit) throw new IOException("站点响应超过大小限制");
                output.write(chunk, 0, count);
            }
            return output.toByteArray();
        }
    }

    static boolean publicUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || host == null || uri.getUserInfo() != null) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (!host.contains(".") || host.contains(":")) return false;
            if (host.matches("[0-9.]+")) return false;
            return !host.matches(".*\\.(?:local|localhost|internal|lan|invalid|test)$");
        } catch (Exception ignored) { return false; }
    }

    static boolean mediaUrl(String value) {
        if (!publicUrl(value)) return false;
        try { return new URI(value).getPath().toLowerCase(Locale.ROOT).matches(".*\\.(?:m3u8|mp4|m4v|mpd)$"); }
        catch (Exception ignored) { return false; }
    }

    static String join(String delimiter, Iterable<String> values) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String value : values) {
            if (!first) result.append(delimiter);
            result.append(value);
            first = false;
        }
        return result.toString();
    }
}
