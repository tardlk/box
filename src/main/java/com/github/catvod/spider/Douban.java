package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 豆瓣导航: browses Douban lists and hands each title to the video sites.
 *
 * Cards carry the host's "msearch:" id prefix, so FongMi never asks this spider for a
 * detail page or a play URL. Search stays off in the site config on purpose: searching
 * here would return more "msearch:" cards and send the user in a circle.
 */
public final class Douban extends Spider {
    static final String SEARCH_PREFIX = "msearch:";
    static final int PAGE_SIZE = 20;
    private static final String API = "https://frodo.douban.com/api/v2";
    private static final String KEY = "?apikey=0ac44ae016490db2204ce0a042db2916";
    private static final String REFERER = "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html";
    // This key belongs to Douban's WeChat mini program; a plain browser User-Agent is answered with
    // HTTP 400 / code 997 instead of data, so the client identity has to be kept.
    private static final String UA = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat";

    private static final String[][] CLASSES = {
            {"hot_movie", "热门电影"}, {"hot_tv", "热播剧集"}, {"hot_show", "热播综艺"},
            {"filter_movie", "电影筛选"}, {"filter_tv", "剧集筛选"}, {"filter_show", "综艺筛选"},
            {"rank", "榜单"}};

    private static final String[] MOVIE_GENRES = {"喜剧", "爱情", "动作", "科幻", "动画", "悬疑", "犯罪", "惊悚",
            "冒险", "音乐", "历史", "奇幻", "恐怖", "战争", "传记", "歌舞", "武侠", "情色", "灾难", "西部", "纪录片", "短片"};
    private static final String[] TV_GENRES = {"剧情", "喜剧", "爱情", "悬疑", "古装", "武侠", "家庭", "犯罪", "科幻",
            "恐怖", "历史", "战争", "动作", "冒险", "传记", "奇幻", "惊悚", "灾难", "歌舞", "音乐", "动画"};
    private static final String[] SHOW_GENRES = {"真人秀", "脱口秀", "音乐", "歌舞"};
    private static final String[] AREAS = {"华语", "欧美", "韩国", "日本", "中国大陆", "美国", "中国香港", "中国台湾",
            "英国", "法国", "德国", "意大利", "西班牙", "印度", "泰国", "俄罗斯", "加拿大", "澳大利亚", "爱尔兰", "瑞典", "巴西", "丹麦"};
    private static final String[] YEARS = {"2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019",
            "2010年代", "2000年代", "90年代", "80年代", "70年代", "60年代", "更早"};
    private static final String[] PLATFORMS = {"腾讯视频", "爱奇艺", "优酷", "湖南卫视", "Netflix", "HBO", "BBC",
            "NHK", "CBS", "NBC", "tvN"};

    private static final String[][] RANKS = {
            {"实时热门电影", "movie_real_time_hotest"}, {"一周口碑电影", "movie_weekly_best"}, {"豆瓣 Top250", "movie_top250"},
            {"实时热门剧集", "tv_real_time_hotest"}, {"华语口碑剧集", "tv_chinese_best_weekly"}, {"全球口碑剧集", "tv_global_best_weekly"},
            {"华语口碑综艺", "show_chinese_best_weekly"}, {"全球口碑综艺", "show_global_best_weekly"}};

    @Override public String homeContent(boolean filter) {
        try {
            JSONArray types = new JSONArray();
            for (String[] item : CLASSES) types.put(new JSONObject().put("type_id", item[0]).put("type_name", item[1]));
            JSONObject result = new JSONObject().put("class", types);
            if (filter) result.put("filters", filters());
            return result.toString();
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String homeVideoContent() {
        try {
            return result(get(API + "/subject_collection/subject_real_time_hotest/items" + KEY
                    + "&start=0&count=" + PAGE_SIZE), "subject_collection_items", 1).toString();
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = page(pg);
            Map<String, String> options = extend == null ? Collections.<String, String>emptyMap() : extend;
            return result(get(categoryUrl(tid, options, page)), itemKey(tid), page).toString();
        } catch (Exception failure) { return error(failure); }
    }

    /** Built separately from the request so filters and paging stay testable without the network. */
    static String categoryUrl(String tid, Map<String, String> options, int page) throws Exception {
        switch (tid == null ? "" : tid) {
            case "hot_movie":
                return paged(API + "/movie/hot_gaia" + KEY
                        + "&sort=" + encode(value(options, "sort", "recommend"))
                        + "&area=" + encode(value(options, "area", "全部")), page);
            case "filter_movie":
                return paged(API + "/movie/recommend" + KEY + "&sort=" + encode(value(options, "sort", "T"))
                        + "&tags=" + encode(tags("电影", options, "类型", "地区", "年代")), page);
            case "filter_tv":
                return paged(API + "/tv/recommend" + KEY + "&sort=" + encode(value(options, "sort", "T"))
                        + "&tags=" + encode(tags("电视剧", options, "电视剧形式", "地区", "年代", "平台")), page);
            case "filter_show":
                return paged(API + "/tv/recommend" + KEY + "&sort=" + encode(value(options, "sort", "T"))
                        + "&tags=" + encode(tags("综艺", options, "综艺形式", "地区", "年代")), page);
            case "hot_tv":
                return collectionUrl(value(options, "type", "tv_hot"), page);
            case "hot_show":
                return collectionUrl(value(options, "type", "show_hot"), page);
            case "rank":
                String board = value(options, "榜单", "movie_real_time_hotest");
                if (!allowedRank(board)) throw new Exception("榜单标识无效");
                return collectionUrl(board, page);
            default:
                throw new Exception("未知分类");
        }
    }

    static String itemKey(String tid) {
        return "hot_tv".equals(tid) || "hot_show".equals(tid) || "rank".equals(tid)
                ? "subject_collection_items" : "items";
    }

    /** Navigation only: the host never calls this for a "msearch:" card. */
    @Override public String detailContent(List<String> ids) {
        try {
            return new JSONObject().put("list", new JSONArray())
                    .put("msg", "豆瓣只提供导航，点击卡片会用片名搜索影视站点").toString();
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String searchContent(String key, boolean quick) { return searchContent(key, quick, "1"); }

    @Override public String searchContent(String key, boolean quick, String pg) {
        try {
            return new JSONObject().put("page", 1).put("pagecount", 1).put("limit", PAGE_SIZE)
                    .put("total", 0).put("list", new JSONArray()).toString();
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String playerContent(String flag, String id, List<String> vipFlags) {
        return error(new Exception("豆瓣只提供导航，不提供播放"));
    }

    private static String collectionUrl(String board, int page) throws Exception {
        if (!board.matches("[a-z0-9_]+")) throw new Exception("集合标识无效");
        return paged(API + "/subject_collection/" + board + "/items" + KEY, page);
    }

    private static String paged(String url, int page) {
        return url + "&start=" + ((page - 1) * PAGE_SIZE) + "&count=" + PAGE_SIZE;
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection connection = SiteHttp.open(url, REFERER, UA);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("豆瓣接口返回 HTTP " + status);
            JSONObject response = new JSONObject(new String(SiteHttp.read(connection, 4 * 1024 * 1024), StandardCharsets.UTF_8));
            int code = response.optInt("code", 200);
            if (code == 997) throw new IOException("豆瓣请求过于频繁（997），请稍后再试");
            if (code != 200) throw new IOException(response.optString("msg", "豆瓣接口拒绝请求") + "（" + code + "）");
            return response;
        } finally { connection.disconnect(); }
    }

    JSONObject result(JSONObject source, String key, int page) throws Exception {
        JSONArray items = source.optJSONArray(key);
        if (items == null) throw new Exception("豆瓣接口缺少 " + key);
        int total = source.optInt("total");
        int pages = total <= 0 ? page : Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        return new JSONObject().put("page", page).put("pagecount", Math.max(page, pages))
                .put("limit", PAGE_SIZE).put("total", total).put("list", cards(items));
    }

    JSONArray cards(JSONArray source) throws Exception {
        JSONArray list = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        if (source == null) return list;
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            JSONObject card = item == null ? null : card(item);
            if (card != null && seen.add(card.getString("vod_id"))) list.put(card);
        }
        return list;
    }

    JSONObject card(JSONObject item) throws Exception {
        String id = item.optString("id").trim();
        String name = item.optString("title").trim();
        // "chart" entries such as movie_scifi have a slug id and no poster; they are not subjects.
        if (!id.matches("[0-9]+") || name.isEmpty()) return null;
        return new JSONObject().put("vod_id", SEARCH_PREFIX + id).put("vod_name", name)
                .put("vod_pic", picture(item)).put("vod_remarks", remarks(item));
    }

    private String picture(JSONObject item) {
        JSONObject pic = item.optJSONObject("pic");
        String url = pic == null ? "" : pic.optString("normal").trim();
        if (url.isEmpty()) url = item.optString("cover_url").trim();
        return url.isEmpty() ? "" : Proxy.url(this, "douban", url);
    }

    static String remarks(JSONObject item) {
        String rating = "";
        JSONObject score = item.optJSONObject("rating");
        double value = score == null ? 0 : score.optDouble("value", 0);
        if (value > 0) rating = "豆瓣 " + (value == Math.floor(value)
                ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value));
        String year = year(item);
        if (rating.isEmpty()) return year;
        return year.isEmpty() ? rating : rating + " · " + year;
    }

    private static String year(JSONObject item) {
        String year = item.optString("year").trim();
        if (year.matches("(?:19|20)[0-9]{2}")) return year;
        String first = item.optString("card_subtitle").split("/", 2)[0].trim();
        return first.matches("(?:19|20)[0-9]{2}") ? first : "";
    }

    private static JSONObject filters() throws Exception {
        JSONObject filters = new JSONObject();
        filters.put("hot_movie", new JSONArray()
                .put(pairs("排序", "sort", "热度", "recommend", "最新", "time", "评分", "rank"))
                .put(pairs("地区", "area", "全部", "全部", "华语", "华语", "欧美", "欧美", "韩国", "韩国", "日本", "日本")));
        filters.put("hot_tv", new JSONArray().put(pairs("分类", "type", "综合", "tv_hot", "国产剧", "tv_domestic",
                "欧美剧", "tv_american", "日剧", "tv_japanese", "韩剧", "tv_korean", "动画", "tv_animation")));
        filters.put("hot_show", new JSONArray().put(pairs("分类", "type", "综合", "show_hot",
                "国内", "show_domestic", "国外", "show_foreign")));
        filters.put("filter_movie", new JSONArray()
                .put(plain("类型", "类型", MOVIE_GENRES))
                .put(plain("地区", "地区", AREAS))
                .put(plain("年代", "年代", YEARS))
                .put(pairs("排序", "sort", "近期热度", "T", "最高评分", "S", "最新上映", "R")));
        filters.put("filter_tv", new JSONArray()
                .put(plain("形式", "电视剧形式", TV_GENRES))
                .put(plain("地区", "地区", AREAS))
                .put(plain("年代", "年代", YEARS))
                .put(plain("平台", "平台", PLATFORMS))
                .put(pairs("排序", "sort", "近期热度", "T", "最高评分", "S", "最新上映", "R")));
        filters.put("filter_show", new JSONArray()
                .put(plain("形式", "综艺形式", SHOW_GENRES))
                .put(plain("地区", "地区", AREAS))
                .put(plain("年代", "年代", YEARS))
                .put(pairs("排序", "sort", "近期热度", "T", "最高评分", "S", "最新上映", "R")));
        JSONArray boards = new JSONArray();
        for (String[] rank : RANKS) boards.put(new JSONObject().put("n", rank[0]).put("v", rank[1]));
        filters.put("rank", new JSONArray().put(new JSONObject().put("key", "榜单").put("name", "榜单").put("value", boards)));
        return filters;
    }

    private static JSONObject plain(String name, String key, String[] values) throws Exception {
        JSONArray choices = new JSONArray().put(new JSONObject().put("n", "全部").put("v", ""));
        for (String value : values) choices.put(new JSONObject().put("n", value).put("v", value));
        return new JSONObject().put("key", key).put("name", name).put("value", choices);
    }

    private static JSONObject pairs(String name, String key, String... values) throws Exception {
        JSONArray choices = new JSONArray();
        for (int i = 0; i + 1 < values.length; i += 2) {
            choices.put(new JSONObject().put("n", values[i]).put("v", values[i + 1]));
        }
        return new JSONObject().put("key", key).put("name", name).put("value", choices);
    }

    private static String tags(String prefix, Map<String, String> options, String... keys) {
        StringBuilder tags = new StringBuilder(prefix);
        for (String key : keys) {
            String value = options.get(key);
            if (value != null && !value.trim().isEmpty()) tags.append(',').append(value.trim());
        }
        return tags.toString();
    }

    private static String value(Map<String, String> options, String key, String fallback) {
        String value = options.get(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static boolean allowedRank(String board) {
        for (String[] rank : RANKS) if (rank[1].equals(board)) return true;
        return false;
    }

    private static int page(String value) {
        try { return Math.max(1, Math.min(1000, Integer.parseInt(value))); }
        catch (Exception ignored) { return 1; }
    }

    private static String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }

    private static String error(Exception failure) {
        try { return new JSONObject().put("list", new JSONArray()).put("msg", "豆瓣：" + failure.getMessage()).toString(); }
        catch (Exception ignored) { return "{\"list\":[],\"msg\":\"豆瓣请求失败\"}"; }
    }
}
