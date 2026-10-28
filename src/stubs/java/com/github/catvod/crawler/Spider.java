package com.github.catvod.crawler;

import android.content.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compile-time contract only. This class is never included in the plugin. */
public abstract class Spider {
    public void init(Context context, String extend) throws Exception { }
    public String homeContent(boolean filter) throws Exception { return ""; }
    public String homeVideoContent() throws Exception { return ""; }
    public String categoryContent(String tid, String page, boolean filter, HashMap<String, String> extend) throws Exception { return ""; }
    public String searchContent(String key, boolean quick) throws Exception { return ""; }
    public String searchContent(String key, boolean quick, String page) throws Exception { return ""; }
    public String detailContent(List<String> ids) throws Exception { return ""; }
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception { return ""; }
    public Object[] proxy(Map<String, String> params) throws Exception { return null; }
    public void destroy() { }
}
