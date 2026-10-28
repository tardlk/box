package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.Map;

public final class IvdyContractTest {
    private static int count;
    private static void check(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
        count++;
        System.out.println("PASS IVDY " + description);
    }
    public static void main(String[] args) throws Exception {
        Map<String, String> params = Ivdy.params("pageSize", "24", "keyword", "老友记 & Friends", "pageNum", "2", "empty", "");
        check(IvdyApi.signature(params, "1793178000000").equals("7a84f5c31c5c475141539ea822a7972dbed6d5ed"), "请求签名按原始参数排序，保留中文和特殊符号");
        JSONObject source = new JSONObject().put("vodId", 122951).put("vodName", "老友记1").put("vodPic", "https://images.example.com/1.webp")
                .put("vodSerial", "24").put("vodTotal", 24).put("vodIsend", 1).put("vodContent", "<p>简介</p>");
        JSONObject list = IvdyParser.page(new JSONObject().put("list", new JSONArray().put(source).put(source).put(new JSONObject()))
                .put("totalPage", 17).put("totalCount", 387).put("pageSize", 24), 2);
        check(list.getJSONArray("list").length() == 1 && list.getInt("pagecount") == 17 && list.getInt("page") == 2, "分页沿用真实总页数并去重");
        check(list.getJSONArray("list").getJSONObject(0).getString("vod_pic").endsWith("1.webp"), "封面保留原始 WebP 地址");
        check(IvdyParser.card(source).getString("vod_remarks").equals("24集全"), "完整剧集显示集数");
        source.put("episodeList", new JSONArray().put(new JSONObject().put("nid", 42).put("name", "1").put("playUrl", JSONObject.NULL))
                .put(new JSONObject().put("nid", 42).put("name", "duplicate"))
                .put(new JSONObject().put("nid", 43).put("name", "花絮#$")));
        JSONObject detail = IvdyParser.detail(source, "122951");
        check(detail.getString("vod_play_url").equals("第1集$ivdy:122951:42#花絮＃＄$ivdy:122951:43"), "真实选集去重且播放时重新取址");
        check(detail.getString("vod_content").equals("简介"), "简介移除 HTML 标记");
        boolean wrongId = false;
        try { IvdyParser.detail(source, "1"); } catch (Exception expected) { wrongId = true; }
        check(wrongId, "详情标识必须与请求一致");
        boolean empty = false;
        try { IvdyParser.detail(new JSONObject(source.toString()).put("episodeList", new JSONArray()), "122951"); } catch (Exception expected) { empty = true; }
        check(empty, "无选集时不生成占位播放项");
        JSONObject hd = new JSONObject().put("needLogin", true).put("flag", false).put("resolution", 1080).put("url", "https://media.example.com/hd.m3u8");
        JSONObject sd = new JSONObject().put("needLogin", false).put("flag", true).put("resolution", 480).put("url", "https://media.example.com/sd.m3u8");
        JSONObject data = new JSONObject().put("list", new JSONArray().put(hd).put(sd));
        check(IvdyParser.playable(data).getInt("resolution") == 480, "遵循站点未登录默认画质");
        boolean denied = false;
        try { IvdyParser.playable(new JSONObject().put("list", new JSONArray().put(hd))); } catch (Exception expected) { denied = true; }
        check(denied, "没有公开画质时明确报告需要登录");
        denied = false;
        try { IvdyParser.playable(new JSONObject().put("list", new JSONArray().put(new JSONObject().put("needLogin", false).put("url", "http://127.0.0.1/x.m3u8")))); } catch (Exception expected) { denied = true; }
        check(denied, "拒绝非公网媒体 URL");
        JSONObject choices = new JSONObject().put("yearList", new JSONArray().put(new JSONObject().put("itemText", "2026").put("itemValue", "1")));
        check(IvdyParser.filters(choices, "1").getJSONObject(0).getJSONArray("value").getJSONObject(1).getString("v").equals("2026"), "年份按站点网页规则使用文字值");
        Ivdy spider = new Ivdy();
        check(new JSONObject(spider.homeContent(false)).getJSONArray("class").length() == 5, "提供电影电视剧综艺动漫短剧五类");
        check(new JSONObject(spider.playerContent("", "https://media.example.com/x.m3u8", Collections.emptyList())).optString("url").isEmpty(), "播放入口只接收本插件选集标识");
        check(new JSONObject(spider.detailContent(Collections.singletonList("bad"))).getJSONArray("list").length() == 0, "无效详情请求返回明确错误");
        check(new JSONObject(spider.searchContent(" ", false, "2")).getJSONArray("list").length() == 0, "空搜索不请求站点");
        System.out.println("IVDY CONTRACT PASS " + count);
    }
}
