package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

final class IvdyParser {
    static JSONObject card(JSONObject source) throws Exception {
        String id = source.optString("vodId");
        if (!id.matches("[0-9]+") || source.optString("vodName").trim().isEmpty()) return null;
        String picture = source.optString("vodPic");
        String remarks = source.optString("vodRemarks");
        if (remarks.isEmpty()) {
            String serial = source.optString("vodSerial");
            int total = source.optInt("vodTotal");
            if (serial.matches("[1-9][0-9]*")) remarks = source.optInt("vodIsend") == 1 ? serial + "集全" : "更新至" + serial + "集";
            else if (total > 0 && source.optInt("vodIsend") == 1) remarks = total + "集全";
            else remarks = source.optString("vodVersion");
        }
        return new JSONObject().put("vod_id", id).put("vod_name", source.optString("vodName"))
                .put("vod_pic", SiteHttp.publicUrl(picture) ? picture : "").put("vod_remarks", SiteParser.clean(remarks));
    }

    static JSONArray cards(JSONArray source) throws Exception {
        JSONArray list = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        if (source != null) for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            JSONObject card = item == null ? null : card(item);
            if (card != null && seen.add(card.getString("vod_id"))) list.put(card);
        }
        return list;
    }

    static JSONObject page(JSONObject data, int page) throws Exception {
        return new JSONObject().put("page", page).put("pagecount", Math.max(page, data.optInt("totalPage", page)))
                .put("limit", data.optInt("pageSize", 24)).put("total", data.optInt("totalCount"))
                .put("list", cards(data.optJSONArray("list")));
    }

    static JSONObject detail(JSONObject source, String id) throws Exception {
        if (!id.equals(source.optString("vodId"))) throw new Exception("影片标识不匹配");
        JSONObject vod = card(source);
        if (vod == null) throw new Exception("影片资料不完整");
        JSONArray sourceEpisodes = source.optJSONArray("episodeList");
        ArrayList<String> episodes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (sourceEpisodes != null) for (int i = 0; i < sourceEpisodes.length(); i++) {
            JSONObject episode = sourceEpisodes.optJSONObject(i);
            if (episode == null) continue;
            String nid = episode.optString("nid");
            if (!nid.matches("[0-9]+") || !seen.add(nid)) continue;
            String name = SiteParser.clean(episode.optString("name"));
            if (name.isEmpty()) name = "第" + (episodes.size() + 1) + "集";
            else if (name.matches("[0-9]+")) name = "第" + name + "集";
            // Resolve at play time: the website's media signatures expire.
            episodes.add(name + "$ivdy:" + id + ":" + nid);
        }
        if (episodes.isEmpty()) throw new Exception("站点没有返回选集");
        return vod.put("type_name", source.optString("vodClass")).put("vod_year", source.optString("vodYear"))
                .put("vod_area", source.optString("vodArea")).put("vod_lang", source.optString("vodLang"))
                .put("vod_actor", source.optString("vodActor")).put("vod_director", source.optString("vodDirector"))
                .put("vod_content", Jsoup.parse(source.optString("vodContent")).text())
                .put("vod_play_from", "默认线路").put("vod_play_url", SiteHttp.join("#", episodes));
    }

    static JSONObject playable(JSONObject data) throws Exception {
        JSONArray list = data.optJSONArray("list");
        JSONObject selected = null;
        if (list != null) for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            // The site labels 720p/1080p as needLogin but serves them to anonymous requests,
            // so the resolution decides instead of the label. Only the URL still has to be usable.
            if (item == null || !SiteHttp.mediaUrl(item.optString("url"))) continue;
            if (selected == null || preferred(item, selected)) selected = item;
        }
        if (selected == null) throw new Exception("当前剧集没有可用的播放线路");
        return selected;
    }

    private static boolean preferred(JSONObject candidate, JSONObject current) {
        int difference = candidate.optInt("resolution") - current.optInt("resolution");
        if (difference != 0) return difference > 0;
        return candidate.optBoolean("flag") && !current.optBoolean("flag");
    }

    static JSONArray filters(JSONObject source, String type) throws Exception {
        String[][] specs = {{"filterStatus", "状态", "serialList"}, {"type", "类型", "typeList"},
                {"v_class", "剧情", "plotList"}, {"area", "地区", "districtList"},
                {"year", "年份", "yearList"}, {"lang", "语言", "languageList"}};
        JSONArray filters = new JSONArray();
        for (String[] spec : specs) {
            if (type.equals("1") && spec[0].equals("type")) continue;
            JSONArray items = source.optJSONArray(spec[2]);
            if (items == null || items.length() == 0) continue;
            JSONArray choices = new JSONArray().put(new JSONObject().put("n", "全部").put("v", ""));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && !item.optString("itemText").isEmpty() && !item.optString("itemValue").isEmpty()) {
                    boolean coded = spec[0].equals("type") || spec[0].equals("filterStatus");
                    choices.put(new JSONObject().put("n", item.optString("itemText")).put("v", item.optString(coded ? "itemValue" : "itemText")));
                }
            }
            filters.put(new JSONObject().put("key", spec[0]).put("name", spec[1]).put("value", choices));
        }
        JSONArray sort = new JSONArray().put(new JSONObject().put("n", type.equals("1") ? "上映时间" : "最近更新").put("v", "1"))
                .put(new JSONObject().put("n", "最新添加").put("v", "2"))
                .put(new JSONObject().put("n", "人气").put("v", "3"))
                .put(new JSONObject().put("n", "评分").put("v", "4"));
        return filters.put(new JSONObject().put("key", "sort").put("name", "排序").put("value", sort));
    }
}
