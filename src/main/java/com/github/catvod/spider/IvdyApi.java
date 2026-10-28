package com.github.catvod.spider;

import org.json.JSONObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Anonymous API contract used by ivdy.com's own web client. */
final class IvdyApi {
    static final String ROOT = "https://ivdy.com";
    private static final String SIGN_KEY = "cb808529bae6b6be45ecfab29a4889bc";
    private final String deviceId = UUID.randomUUID().toString();
    private volatile String origin = "";
    private long discoveredAt;

    String origin() { return origin.isEmpty() ? ROOT : origin; }

    private synchronized void discover() throws IOException {
        if (!origin.isEmpty() && System.currentTimeMillis() - discoveredAt < 30 * 60 * 1000L) return;
        HttpURLConnection connection = SiteHttp.open(ROOT, ROOT + "/");
        try {
            int status = connection.getResponseCode();
            String resolved = connection.getURL().toString();
            URI uri = URI.create(resolved);
            if (status != 200 || !"https".equalsIgnoreCase(uri.getScheme()) || !SiteHttp.publicUrl(resolved)) {
                throw new IOException("站点入口暂时不可用（HTTP " + status + "）");
            }
            origin = "https://" + uri.getAuthority();
            discoveredAt = System.currentTimeMillis();
        } finally { connection.disconnect(); }
    }

    JSONObject get(String path, Map<String, String> values) throws Exception {
        discover();
        TreeMap<String, String> params = new TreeMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) params.put(entry.getKey(), entry.getValue());
        }
        String timestamp = Long.toString(System.currentTimeMillis());
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) query.append('&');
            query.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        HttpURLConnection connection = SiteHttp.open(origin + "/api/mw-movie" + path + (query.length() == 0 ? "" : "?" + query), origin + "/");
        // The signature describes this origin's request; do not forward it to another host.
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("client-type", "1");
        connection.setRequestProperty("deviceId", deviceId);
        connection.setRequestProperty("t", timestamp);
        connection.setRequestProperty("sign", signature(params, timestamp));
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("站点接口返回 HTTP " + status);
            JSONObject response = new JSONObject(new String(SiteHttp.read(connection, 4 * 1024 * 1024), StandardCharsets.UTF_8));
            if (response.optInt("code") != 200) throw new IOException(response.optString("msg", "站点接口拒绝请求") + "（" + response.optInt("code") + "）");
            JSONObject data = response.optJSONObject("data");
            if (data == null) throw new IOException("站点接口缺少 data 对象");
            return data;
        } finally { connection.disconnect(); }
    }

    static String signature(Map<String, String> params, String timestamp) throws Exception {
        StringBuilder plain = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            plain.append(entry.getKey()).append('=').append(entry.getValue()).append('&');
        }
        plain.append("key=").append(SIGN_KEY).append("&t=").append(timestamp);
        return digest("SHA-1", digest("MD5", plain.toString()));
    }

    private static String digest(String algorithm, String input) throws Exception {
        byte[] bytes = MessageDigest.getInstance(algorithm).digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
