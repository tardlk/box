package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fresh implementation for FongMi 5.x, built from the site's current HTML and public player contract. */
public final class Ikanbot extends Spider {
    /**
     * Douban's image CDN answers 418 without a Referer and 200 with one, so the poster URL carries
     * the Referer for the host's ImgUtil to lift out. Same convention as Douban.java.
     */
    private static final String DOUBAN_IMAGE_REFERER = "https://movie.douban.com/";
    private static final long TTL_HOME = 180_000L;
    private static final long TTL_LIST = 180_000L;
    private static final long TTL_DETAIL = 120_000L;
    private final SiteHttp http = new SiteHttp();
    private final Map<String, String> searchCursors = new ConcurrentHashMap<>();
    private final Map<String, Object[]> cache = new ConcurrentHashMap<>();
    private Context context;
    private volatile BrowserPage browser;
    private boolean allowWebView = true;

    private interface Body { String get() throws Exception; }

    /** Short-lived cache: the site answers a page in seconds, and tab switching used to refetch every time. */
    private String cached(String key, long ttl, Body body) throws Exception {
        Object[] hit = cache.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && now - (Long) hit[0] < ttl) return (String) hit[1];
        String value = body.get();
        if (cache.size() > 200) cache.clear();
        cache.put(key, new Object[]{now, value});
        return value;
    }

    @Override public void init(Context context, String extend) throws Exception {
        this.context = context == null ? null : context.getApplicationContext();
        this.allowWebView = true;
        if (extend != null && !extend.trim().isEmpty()) allowWebView = new JSONObject(extend).optBoolean("webview", true);
    }

    @Override public String homeContent(boolean filter) throws Exception {
        JSONArray types = new JSONArray().put(new JSONObject().put("type_id", "movie").put("type_name", "电影"))
                .put(new JSONObject().put("type_id", "tv").put("type_name", "剧集"));
        JSONObject result = new JSONObject().put("class", types);
        if (filter) {
            JSONObject filters = new JSONObject();
            filters.put("movie", tags(new String[]{"热门", "最新", "经典", "豆瓣高分", "冷门佳片", "华语", "欧美", "韩国", "日本", "动作", "喜剧", "爱情", "科幻", "悬疑", "恐怖", "动画"}));
            filters.put("tv", tags(new String[]{"热门", "美剧", "英剧", "韩剧", "日剧", "国产剧", "港剧", "日本动画", "综艺", "纪录片"}));
            result.put("filters", filters);
        }
        return result.toString();
    }

    private JSONArray tags(String[] values) throws Exception {
        JSONArray choices = new JSONArray();
        for (String value : values) choices.put(new JSONObject().put("n", value).put("v", value));
        return new JSONArray().put(new JSONObject().put("key", "tag").put("name", "类型").put("value", choices));
    }

    @Override public String homeVideoContent() {
        try {
            return cached("home", TTL_HOME, () -> SiteParser.page(http.get(http.site("/")), false, this::poster, http.origin()).json(1).toString());
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            if (!tid.equals("movie") && !tid.equals("tv")) throw new Exception("未知分类");
            int number = page(pg);
            String tag = extend == null ? "热门" : extend.getOrDefault("tag", "热门");
            String key = "cat:" + tid + "|" + tag + "|" + number;
            return cached(key, TTL_LIST, () -> {
                String url = http.site("/hot/index-" + tid + "-" + encode(tag) + (number == 1 ? "" : "-p-" + number) + ".html");
                return SiteParser.page(http.get(url), false, this::poster, http.origin()).json(number).toString();
            });
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String searchContent(String key, boolean quick) { return searchContent(key, quick, "1"); }

    @Override public String searchContent(String key, boolean quick, String pg) {
        try {
            int number = page(pg);
            String word = key == null ? "" : key.trim();
            if (word.isEmpty()) return new SiteParser.Page(new JSONArray(), "").json(number).toString();
            return cached("search:" + word + "|" + number, TTL_LIST, () -> {
                String url = searchCursors.get(word + "\n" + number);
                if (url == null) {
                    if (number > 20) throw new Exception("请从搜索第一页顺序翻页");
                    url = http.site("/search?q=" + encode(word));
                    for (int current = 1; current < number; current++) {
                        SiteParser.Page previous = SiteParser.page(http.get(url), true, this::poster, http.origin());
                        if (previous.next.isEmpty()) return new SiteParser.Page(new JSONArray(), "").json(number).toString();
                        url = previous.next;
                        searchCursors.put(word + "\n" + (current + 1), url);
                    }
                }
                SiteParser.Page result = SiteParser.page(http.get(url), true, this::poster, http.origin());
                if (searchCursors.size() > 200) searchCursors.clear();
                if (!result.next.isEmpty()) searchCursors.put(word + "\n" + (number + 1), result.next);
                return result.json(number).toString();
            });
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String detailContent(List<String> ids) {
        try {
            String id = ids == null || ids.isEmpty() ? "" : SiteParser.id(ids.get(0));
            if (id.isEmpty()) throw new Exception("影片 ID 无效");
            return cached("detail:" + id, TTL_DETAIL, () -> {
                JSONObject vod;
                try { vod = directDetail(id); }
                catch (Exception directFailure) {
                    if (!allowWebView || context == null) throw directFailure;
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("请求已取消");
                    JSONObject snapshot = browser().load(context, this, http.site("/play/" + id));
                    vod = browserDetail(id, snapshot);
                }
                if (vod.optString("vod_play_url").isEmpty()) throw new Exception("站点没有返回可播放的媒体线路");
                return new JSONObject().put("list", new JSONArray().put(vod)).toString();
            });
        } catch (Exception failure) { return error(failure); }
    }

    private JSONObject directDetail(String id) throws Exception {
        String html = http.get(http.site("/play/" + id));
        Document document = Jsoup.parse(html);
        String current = document.select("#current_id").attr("value");
        if (!current.equals(id)) throw new Exception("详情页影片标识不匹配");
        String type = document.select("#mtype").attr("value");
        String token = SiteParser.token(current, document.select("#e_token").attr("value"));
        String api = http.site("/api/getResN?videoId=" + encode(id) + "&mtype=" + encode(type) + "&token=" + encode(token));
        JSONObject response = new JSONObject(http.get(api));
        if (response.optInt("state") != 1) throw new Exception("线路接口拒绝请求：state=" + response.optInt("state"));
        JSONObject data = response.optJSONObject("data");
        JSONArray groups = data == null ? null : data.optJSONArray("list");
        if (groups == null) throw new Exception("线路接口缺少 data.list");
        JSONObject vod = SiteParser.metadata(html, id, this::poster);
        applyLines(vod, SiteParser.lines(groups));
        if (vod.optString("vod_play_url").isEmpty()) throw new Exception("线路列表为空或格式发生变化");
        return vod;
    }

    private JSONObject browserDetail(String id, JSONObject snapshot) throws Exception {
        JSONObject vod = new JSONObject().put("vod_id", id).put("vod_name", snapshot.optString("title"))
                .put("vod_pic", poster(snapshot.optString("picture")));
        JSONArray meta = snapshot.optJSONArray("meta");
        if (meta != null) {
            for (int i = 0; i < meta.length(); i++) if (meta.optString(i).matches("(?:19|20)[0-9]{2}")) vod.put("vod_year", meta.getString(i));
            vod.put("vod_area", meta.optString(3));
            String[] people = meta.optString(4).split("/", 2);
            if (people.length > 1) { vod.put("vod_director", people[0]); vod.put("vod_actor", people[1]); }
        }
        applyLines(vod, SiteParser.lines(snapshot.getJSONArray("groups")));
        return vod;
    }

    private void applyLines(JSONObject vod, JSONObject lines) throws Exception {
        vod.put("vod_play_from", lines.getString("vod_play_from"));
        vod.put("vod_play_url", lines.getString("vod_play_url"));
    }

    @Override public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (!SiteHttp.mediaUrl(id)) throw new Exception("不是有效的公开媒体地址");
            JSONObject headers = new JSONObject().put("User-Agent", SiteHttp.UA).put("Referer", http.origin() + "/");
            return new JSONObject().put("parse", 0).put("url", id).put("header", headers.toString()).toString();
        } catch (Exception failure) { return error(failure); }
    }

    /**
     * 豆瓣封面直连图片 CDN，只补宿主认识的 {@code @Referer=} 后缀。
     *
     * <p>豆瓣图床要 Referer：不带或只带 UA 都是 418，带上才是 200（2026-10-29 实测）。
     * 站点自己的封面代理 {@code imgp2.ikanbot.eu.org} 是境外 Cloudflare Worker，
     * 实测同一张图单次 4–40 秒且约一半请求完全不响应，而豆瓣直连 12/12 成功、每张约 0.1 秒，
     * 因此不再绕它。宿主 {@code ImgUtil} 会把后缀变成请求头再剥掉，客户端仍然直连。
     */
    String poster(String original) {
        if (original == null || original.isEmpty()) return "";
        String fixed = SiteParser.poster(original);
        String host;
        try { host = java.net.URI.create(fixed).getHost(); }
        catch (Exception ignored) { return image(fixed); }
        if (host != null && (host.equalsIgnoreCase("doubanio.com")
                || host.toLowerCase(java.util.Locale.ROOT).endsWith(".doubanio.com"))) {
            return fixed + "@Referer=" + DOUBAN_IMAGE_REFERER;
        }
        return image(fixed);
    }

    private String image(String original) {
        if (!SiteHttp.publicUrl(original)) return "";
        String asset = Proxy.register(original);
        Map<String, String> params = Collections.singletonMap("image", asset);
        try {
            Object url = Spider.class.getMethod("getProxyUrl", Map.class).invoke(this, params);
            if (url instanceof String && !((String) url).isEmpty()) return (String) url;
        } catch (Exception ignored) { }
        try {
            Class<?> helper = Class.forName("com.github.catvod.Proxy");
            String base;
            try { base = (String) helper.getMethod("getUrl", boolean.class).invoke(null, true); }
            catch (NoSuchMethodException ignored) { base = (String) helper.getMethod("getUrl").invoke(null); }
            if (base != null && !base.isEmpty()) return base + "?do=ikanbot&image=" + asset;
        } catch (Exception ignored) { }
        return original;
    }

    @Override public Object[] proxy(Map<String, String> params) throws Exception { return Proxy.proxy(params); }

    private synchronized BrowserPage browser() {
        if (browser == null) browser = new BrowserPage();
        return browser;
    }

    @Override public void destroy() { context = null; if (browser != null) browser.close(); searchCursors.clear(); }

    private static int page(String value) {
        try { return Math.max(1, Math.min(1000, Integer.parseInt(value))); }
        catch (Exception ignored) { return 1; }
    }

    private static String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }

    private static String error(Exception failure) {
        // 空 message 的异常（典型是 NPE）以前会显示成"爱看机器人：null"，等于什么都没说。
        String detail = failure.getMessage();
        if (detail == null || detail.trim().isEmpty()) detail = failure.getClass().getSimpleName();
        try { return new JSONObject().put("list", new JSONArray()).put("url", "").put("msg", "爱看机器人：" + detail).toString(); }
        catch (Exception ignored) { return "{\"list\":[],\"msg\":\"爱看机器人请求失败\"}"; }
    }
}
