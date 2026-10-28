package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 爱电影 (ivdy.com), using the anonymous website API and current redirect origin. */
public final class Ivdy extends Spider {
    private final IvdyApi api = new IvdyApi();
    private static final String[][] TYPES = {{"1", "电影"}, {"2", "电视剧"}, {"3", "综艺"}, {"4", "动漫"}, {"88", "短剧"}};

    @Override public String homeContent(boolean filter) {
        try {
            JSONArray types = new JSONArray();
            for (String[] type : TYPES) types.put(new JSONObject().put("type_id", type[0]).put("type_name", type[1]));
            JSONObject result = new JSONObject().put("class", types);
            if (filter) {
                try {
                    JSONObject source = api.get("/anonymous/v1/get/filer/list", Collections.emptyMap());
                    JSONObject filters = new JSONObject();
                    for (String[] type : TYPES) {
                        JSONObject group = source.optJSONObject(type[0]);
                        if (group != null) filters.put(type[0], IvdyParser.filters(group, type[0]));
                    }
                    result.put("filters", filters);
                } catch (Exception failure) { result.put("msg", "筛选项暂不可用：" + failure.getMessage()); }
            }
            return result.toString();
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String homeVideoContent() {
        try {
            JSONObject source = api.get("/anonymous/v1/home/all/list", params("clientType", "1", "pageNum", "1"));
            JSONArray all = new JSONArray();
            String[] groups = {"homeBroadcastPageData", "homeManagerPageData", "homeNewMoviePageData", "newestTvPageData", "newestVarietyPageData", "newestCartoonPageData", "newestShortTvPageData"};
            for (String group : groups) {
                JSONObject section = source.optJSONObject(group);
                JSONArray entries = section == null ? null : section.optJSONArray("list");
                if (entries != null) for (int i = 0; i < entries.length(); i++) all.put(entries.get(i));
            }
            return new JSONObject().put("list", IvdyParser.cards(all)).toString();
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            if (!tid.matches("(?:1|2|3|4|88)")) throw new Exception("未知分类");
            int number = page(pg);
            Map<String, String> params = params("type1", tid, "pageNum", Integer.toString(number), "pageSize", "24", "sort", "1", "sortBy", "1");
            if (extend != null) for (String key : new String[]{"type", "v_class", "area", "year", "lang", "filterStatus", "sort"}) {
                String value = extend.get(key);
                if (value != null && !value.isEmpty()) params.put(key, value);
            }
            return IvdyParser.page(api.get("/anonymous/video/list", params), number).toString();
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String searchContent(String key, boolean quick) { return searchContent(key, quick, "1"); }

    @Override public String searchContent(String key, boolean quick, String pg) {
        try {
            int number = page(pg);
            if (key == null || key.trim().isEmpty()) return IvdyParser.page(new JSONObject(), number).toString();
            JSONObject data = api.get("/anonymous/video/searchByWord", params("keyword", key.trim(), "pageNum", Integer.toString(number), "pageSize", "24", "sourceCode", "1"));
            JSONObject result = data.optJSONObject("result");
            if (result == null) throw new Exception("搜索接口缺少结果列表");
            return IvdyParser.page(result, number).toString();
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String detailContent(List<String> ids) {
        try {
            String id = ids == null || ids.isEmpty() ? "" : ids.get(0);
            if (!id.matches("[0-9]+")) throw new Exception("影片 ID 无效");
            JSONObject vod = IvdyParser.detail(api.get("/anonymous/video/detail", params("id", id)), id);
            return new JSONObject().put("list", new JSONArray().put(vod)).toString();
        } catch (Exception failure) { return error(failure); }
    }

    @Override public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            if (id == null || !id.matches("ivdy:[0-9]+:[0-9]+")) throw new Exception("选集标识无效");
            String[] parts = id.split(":");
            JSONObject data = api.get("/anonymous/v2/video/episode/url", params("clientType", "1", "id", parts[1], "nid", parts[2]));
            JSONObject selected = IvdyParser.playable(data);
            JSONObject headers = new JSONObject().put("User-Agent", SiteHttp.UA).put("Referer", api.origin() + "/");
            return new JSONObject().put("parse", 0).put("url", selected.getString("url")).put("header", headers.toString()).toString();
        } catch (Exception failure) { return error(failure); }
    }

    static Map<String, String> params(String... pairs) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) params.put(pairs[i], pairs[i + 1]);
        return params;
    }

    private static int page(String input) {
        try { return Math.max(1, Math.min(1000, Integer.parseInt(input))); }
        catch (Exception ignored) { return 1; }
    }

    private static String error(Exception failure) {
        try { return new JSONObject().put("list", new JSONArray()).put("url", "").put("msg", "爱电影：" + failure.getMessage()).toString(); }
        catch (Exception ignored) { return "{\"list\":[],\"url\":\"\",\"msg\":\"爱电影请求失败\"}"; }
    }
}
