package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class DoubanContractTest {
    private static int count;

    private static void check(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
        count++;
        System.out.println("PASS 豆瓣 " + description);
    }

    public static void main(String[] args) throws Exception {
        Douban spider = new Douban();
        JSONObject home = new JSONObject(spider.homeContent(true));
        check(home.getJSONArray("class").length() == 7, "提供七个导航分类");
        JSONObject filters = home.getJSONObject("filters");
        check(filters.length() == 7 && filters.has("rank"), "每个分类都有筛选项");

        JSONObject item = new JSONObject().put("id", 1292052).put("title", " 肖申克的救赎 ").put("year", "1994")
                .put("rating", new JSONObject().put("value", 9.7))
                .put("pic", new JSONObject().put("normal", "https://img3.doubanio.com/view/photo/s_ratio_poster/public/p480747492.jpg"));
        JSONObject card = spider.card(item);
        check(card.getString("vod_id").equals("msearch:1292052"), "卡片用 msearch 前缀交给宿主搜索");
        check(card.getString("vod_name").equals("肖申克的救赎"), "片名去掉首尾空白");
        check(card.getString("vod_remarks").equals("豆瓣 9.7 · 1994"), "评分与年份拼成备注");
        check(card.getString("vod_pic").equals("https://img3.doubanio.com/view/photo/s_ratio_poster/public/p480747492.jpg"
                + "@Referer=https://movie.douban.com/"), "封面直连并附加宿主认识的 Referer 后缀");
        JSONObject local = new JSONObject().put("id", "3").put("title", "本机图")
                .put("pic", new JSONObject().put("normal", "http://127.0.0.1/x.jpg"));
        check(spider.card(local).getString("vod_pic").isEmpty(), "封面对非公网地址返回空");

        JSONObject unrated = new JSONObject().put("id", "1").put("title", "无评分")
                .put("rating", new JSONObject().put("value", 0)).put("card_subtitle", "2026 / 中国大陆 / 剧情");
        check(Douban.remarks(unrated).equals("2026"), "无评分时退化为年份");
        check(Douban.remarks(new JSONObject().put("id", "2").put("title", "空")).isEmpty(), "无评分无年份时备注为空");

        JSONObject chart = new JSONObject().put("id", "movie_scifi").put("title", "高分经典科幻片榜");
        check(spider.card(chart) == null, "榜单型条目不是影片，跳过");

        JSONArray items = new JSONArray().put(item).put(new JSONObject(item.toString())).put(chart);
        check(spider.cards(items).length() == 1, "重复影片只保留一条");

        JSONObject source = new JSONObject().put("total", 45).put("list", items);
        JSONObject page2 = spider.result(source, "list", 2);
        check(page2.getInt("pagecount") == 3 && page2.getInt("page") == 2, "按 total 计算总页数");
        check(spider.result(new JSONObject().put("list", new JSONArray()), "list", 3).getInt("pagecount") == 3,
                "缺少 total 时不把总页数压成一页");

        check(Douban.itemKey("rank").equals("subject_collection_items"), "集合类分类读取 collection 键");
        check(Douban.itemKey("filter_movie").equals("items"), "推荐类分类读取 items 键");

        Map<String, String> movie = new HashMap<>();
        movie.put("类型", "科幻");
        movie.put("地区", "中国大陆");
        movie.put("年代", "2026");
        movie.put("sort", "S");
        String url = Douban.categoryUrl("filter_movie", movie, 2);
        check(url.contains("%E7%94%B5%E5%BD%B1%2C%E7%A7%91%E5%B9%BB%2C%E4%B8%AD%E5%9B%BD%E5%A4%A7%E9%99%86%2C2026"),
                "筛选标签按电影+类型+地区+年代顺序拼接并编码");
        check(url.contains("&sort=S") && url.contains("&start=20&count=20"), "排序与分页参数正确");

        Map<String, String> tv = new HashMap<>();
        tv.put("电视剧形式", "悬疑");
        tv.put("平台", "腾讯视频");
        check(Douban.categoryUrl("filter_tv", tv, 1).contains("%2C%E6%82%AC%E7%96%91%2C%E8%85%BE%E8%AE%AF%E8%A7%86%E9%A2%91"),
                "剧集筛选带上隐藏的电视剧形式标记与平台");

        Map<String, String> empty = Collections.emptyMap();
        check(Douban.categoryUrl("filter_show", empty, 1).contains("&tags=%E7%BB%BC%E8%89%BA"), "空筛选只带综艺标记");
        check(Douban.categoryUrl("rank", empty, 1).contains("/subject_collection/movie_real_time_hotest/items"),
                "榜单默认实时热门电影");
        check(Douban.categoryUrl("rank", Collections.singletonMap("榜单", "movie_top250"), 1)
                .contains("/subject_collection/movie_top250/items"), "榜单按选择切换集合");
        check(Douban.categoryUrl("hot_movie", empty, 1).contains("&sort=recommend&area=%E5%85%A8%E9%83%A8"),
                "热门电影默认热度排序与全部地区");

        boolean rejected = false;
        try { Douban.categoryUrl("rank", Collections.singletonMap("榜单", "https://evil.example.com"), 1); }
        catch (Exception expected) { rejected = true; }
        check(rejected, "拒绝白名单外的榜单标识");
        rejected = false;
        try { Douban.categoryUrl("nope", empty, 1); } catch (Exception expected) { rejected = true; }
        check(rejected, "未知分类直接报错");

        JSONObject detail = new JSONObject(spider.detailContent(Collections.singletonList("msearch:1292052")));
        check(detail.getJSONArray("list").length() == 0, "详情不返回伪造的播放线路");
        check(new JSONObject(spider.playerContent("", "msearch:1292052", Collections.emptyList()))
                .optString("msg").contains("不提供播放"), "播放入口明确拒绝");
        check(new JSONObject(spider.searchContent("老友记", false, "1")).getJSONArray("list").length() == 0,
                "站点自身不开搜索，避免 msearch 回到自己");

        System.out.println("DOUBAN CONTRACT PASS " + count);
    }
}
