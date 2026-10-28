package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SiteParser {
    interface Image { String url(String original); }
    static final class Page {
        final JSONArray list;
        final String next;
        Page(JSONArray list, String next) { this.list = list; this.next = next; }
        JSONObject json(int number) throws Exception {
            return new JSONObject().put("page", number).put("pagecount", next.isEmpty() ? number : number + 1)
                    .put("limit", list.length()).put("list", list);
        }
    }

    static Page page(String html, boolean search, Image images) throws Exception {
        return page(html, search, images, SiteHttp.ROOT);
    }

    static Page page(String html, boolean search, Image images, String base) throws Exception {
        Document document = Jsoup.parse(html, base);
        JSONArray videos = new JSONArray();
        Set<String> seen = new LinkedHashSet<>();
        String selector = search ? ".media" : ".v-list a.item, .list-wp a.item, .item-root";
        for (Element card : document.select(selector)) {
            Element link = card.is("a[href]") ? card : card.selectFirst("a[href^=/play/]");
            if (link == null) continue;
            String id = id(link.attr("href"));
            if (id.isEmpty() || !seen.add(id)) continue;
            Element image = card.selectFirst("img");
            String title = image == null ? "" : image.attr("alt").trim();
            if (title.isEmpty()) title = card.select(".title-text, .title, p").text().trim();
            if (title.isEmpty()) title = link.text().trim();
            if (title.isEmpty()) continue;
            String picture = image == null ? "" : image.attr("data-src");
            if (picture.isEmpty() && image != null) picture = image.attr("src");
            if (picture.startsWith("data:")) picture = "";
            if (!picture.isEmpty()) picture = URI.create(base + "/").resolve(picture).toString();
            if (!SiteHttp.publicUrl(picture)) picture = "";
            videos.put(new JSONObject().put("vod_id", id).put("vod_name", title)
                    .put("vod_pic", images.url(picture)).put("vod_remarks", card.select(".label, .rate, .rating").text()));
        }
        String next = "";
        for (Element link : document.select(".page-more a[href]")) {
            if (!link.text().contains("下一页")) continue;
            URI uri = URI.create(base + "/").resolve(link.attr("href"));
            if (SiteHttp.siteHost(uri.getHost()) && "https".equalsIgnoreCase(uri.getScheme())) next = uri.toString();
        }
        return new Page(videos, next);
    }

    static String id(String value) {
        if (value == null) return "";
        if (value.matches("[0-9]+")) return value;
        try {
            URI uri = URI.create(value);
            if (uri.getHost() != null && !SiteHttp.siteHost(uri.getHost())) return "";
            String path = uri.getPath();
            if (path != null && path.matches("/play/[0-9]+/?")) return path.replaceAll("[^0-9]", "");
        } catch (Exception ignored) { }
        return "";
    }

    static JSONObject metadata(String html, String id, Image images) throws Exception {
        Document document = Jsoup.parse(html, SiteHttp.ROOT);
        List<String> metadata = document.select(".result-info .detail .meta").eachText();
        String title = document.select("#video_title").text();
        if (title.isEmpty() && !metadata.isEmpty()) title = metadata.get(0);
        Element image = document.selectFirst(".result-info img");
        String picture = image == null ? document.select("meta[property=og:image]").attr("content") : image.attr("data-src");
        String year = "";
        for (String item : metadata) if (item.matches("(?:19|20)[0-9]{2}")) year = item;
        String people = metadata.size() > 4 ? metadata.get(4) : "";
        String[] credits = people.split("/", 2);
        return new JSONObject().put("vod_id", id).put("vod_name", title)
                .put("vod_pic", images.url(picture)).put("vod_year", year)
                .put("vod_area", metadata.size() > 3 ? metadata.get(3) : "")
                .put("vod_director", credits.length > 1 ? credits[0] : "")
                .put("vod_actor", credits.length > 1 ? credits[1] : people)
                .put("vod_content", SiteHttp.join(" · ", metadata));
    }

    static String token(String id, String encoded) throws Exception {
        if (id == null || !id.matches("[0-9]{4,}") || encoded == null) throw new Exception("详情页缺少有效线路参数");
        String remaining = encoded;
        StringBuilder token = new StringBuilder();
        for (char digit : id.substring(id.length() - 4).toCharArray()) {
            int start = (digit - '0') % 3 + 1;
            if (remaining.length() < start + 8) throw new Exception("站点线路参数格式已变化");
            token.append(remaining, start, start + 8);
            remaining = remaining.substring(start + 8);
        }
        return token.toString();
    }

    static JSONArray array(Object value) throws Exception {
        for (int depth = 0; depth < 5; depth++) {
            if (value instanceof JSONArray) return (JSONArray) value;
            if (value instanceof String) value = new JSONTokener((String) value).nextValue();
            else if (value instanceof JSONObject) {
                JSONObject object = (JSONObject) value;
                if (object.has("url")) return new JSONArray().put(object);
                value = object.opt("resData");
            } else break;
        }
        return new JSONArray();
    }

    static JSONObject lines(JSONArray groups) throws Exception {
        List<String> names = new ArrayList<>();
        List<String> playlists = new ArrayList<>();
        for (int index = 0; index < groups.length(); index++) {
            JSONObject group = groups.optJSONObject(index);
            if (group == null) continue;
            JSONArray resources = array(group.opt("resData"));
            List<String> episodes = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (int number = 0; number < resources.length(); number++) {
                JSONObject resource = resources.optJSONObject(number);
                if (resource == null) continue;
                for (String episode : resource.optString("url").split("#")) {
                    String[] fields = episode.split("\\$", -1);
                    if (fields.length < 2 || !SiteHttp.mediaUrl(fields[1].trim()) || !seen.add(fields[1].trim())) continue;
                    String label = clean(fields[0]);
                    if (label.isEmpty()) label = "第" + (episodes.size() + 1) + "集";
                    String version = clean(resource.optString("newName"));
                    if (!version.isEmpty() && !version.matches("[0-9]+")) label = version + " " + label;
                    episodes.add(label + "$" + fields[1].trim());
                }
            }
            if (episodes.isEmpty()) continue;
            names.add("线路" + (index + 1));
            playlists.add(SiteHttp.join("#", episodes));
        }
        return new JSONObject().put("vod_play_from", SiteHttp.join("$$$", names)).put("vod_play_url", SiteHttp.join("$$$", playlists));
    }

    static String clean(String value) { return value.replace('$', '＄').replace('#', '＃').trim(); }
}
