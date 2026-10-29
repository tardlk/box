package com.github.catvod.spider;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Normal, visible browser rendering. No CAPTCHA solver, hidden token extraction or cookie export. */
final class BrowserPage {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicReference<Session> active = new AtomicReference<>();

    // Read only the metadata and resource links the page has rendered for the visitor.
    private static final String SNAPSHOT = "(function(){"
            + "var box=document.querySelector('#lineContent');if(!box)return '';"
            + "var groups=[];Array.from(box.children).forEach(function(row){"
            + "var res=[];row.querySelectorAll('[name=lineData][udata]').forEach(function(e){"
            + "var u=e.getAttribute('udata');if(u)res.push({url:(e.textContent.trim()||'播放')+'$'+u});});"
            + "if(res.length)groups.push({resData:res});});if(!groups.length)return '';"
            + "var title=document.querySelector('#video_title');var image=document.querySelector('.result-info img');"
            + "return JSON.stringify({title:title?title.textContent.trim():'',"
            + "picture:image?(image.getAttribute('data-src')||image.src):'',"
            + "meta:Array.from(document.querySelectorAll('.result-info .detail .meta')).map(function(e){return e.textContent.trim();}),"
            + "groups:groups});})()";

    JSONObject load(Context context, Object spider, String url) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new Exception("详情读取必须在后台线程执行");
        Session session = new Session();
        if (!active.compareAndSet(null, session)) throw new Exception("另一个网页正在读取，请关闭后重试");
        main.post(() -> show(session, context, spider, url));
        try {
            // 曾经等 90 秒，直连失败时用户要干等一分半；验证码通常几秒内就有结果，给 25 秒足够。
        if (!session.ready.await(25, TimeUnit.SECONDS)) finish(session, null, "网页读取超时；站点验证可能尚未完成");
            if (session.result == null) throw new Exception(session.error == null ? "网页读取失败" : session.error);
            return session.result;
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            throw cancelled;
        } finally {
            session.finished.set(true);
            active.compareAndSet(session, null);
            main.post(() -> cleanup(session));
        }
    }

    private void show(Session session, Context context, Object spider, String url) {
        try {
            if (session.finished.get()) return;
            Activity activity = activity(context, spider);
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                finish(session, null, "无法取得影视当前页面，请进入站点后重新打开详情");
                return;
            }
            LinearLayout panel = new LinearLayout(activity);
            panel.setOrientation(LinearLayout.VERTICAL);
            TextView notice = new TextView(activity);
            notice.setText("正在读取爱看机器人。若页面要求验证，请手动完成；取得线路后会自动关闭。");
            notice.setPadding(24, 16, 24, 16);
            panel.addView(notice);
            WebView view = new WebView(activity);
            session.view = view;
            view.getSettings().setJavaScriptEnabled(true);
            view.getSettings().setDomStorageEnabled(true);
            view.getSettings().setAllowFileAccess(false);
            view.getSettings().setAllowContentAccess(false);
            view.getSettings().setMediaPlaybackRequiresUserGesture(true);
            view.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
            view.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView web, WebResourceRequest request) {
                    String host = request.getUrl().getHost();
                    return request.isForMainFrame() && !SiteHttp.siteHost(host);
                }
            });
            panel.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
            session.dialog = new AlertDialog.Builder(activity).setTitle("爱看机器人网页读取")
                    .setView(panel).setNegativeButton("取消", (dialog, which) -> finish(session, null, "已取消网页读取"))
                    .setOnCancelListener(dialog -> finish(session, null, "已取消网页读取")).create();
            session.dialog.show();
            if (session.dialog.getWindow() != null) {
                android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
                session.dialog.getWindow().setLayout((int) (metrics.widthPixels * .96), (int) (metrics.heightPixels * .9));
            }
            session.poll = new Runnable() {
                @Override public void run() {
                    if (session.finished.get()) return;
                    view.evaluateJavascript(SNAPSHOT, value -> {
                        if (session.finished.get()) return;
                        try {
                            Object decoded = new JSONTokener(value).nextValue();
                            if (decoded instanceof String && ((String) decoded).length() > 0 && ((String) decoded).length() < 4 * 1024 * 1024) {
                                JSONObject result = new JSONObject((String) decoded);
                                if (result.optJSONArray("groups") != null && result.getJSONArray("groups").length() > 0) {
                                    finish(session, result, null);
                                    return;
                                }
                            }
                        } catch (Exception ignored) { }
                        main.postDelayed(this, 750);
                    });
                }
            };
            view.loadUrl(url);
            main.postDelayed(session.poll, 750);
        } catch (Exception failure) { finish(session, null, "网页初始化失败：" + failure.getMessage()); }
    }

    private Activity activity(Context context, Object spider) {
        if (context instanceof Activity) return (Activity) context;
        try {
            Object result = spider.getClass().getMethod("getActivity").invoke(spider);
            if (result instanceof Activity) return (Activity) result;
        } catch (Exception ignored) { }
        for (String name : new String[]{"com.github.catvod.Runtime", "com.fongmi.android.tv.App"}) {
            try {
                Object result = Class.forName(name).getMethod("activity").invoke(null);
                if (result instanceof Activity) return (Activity) result;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private void finish(Session session, JSONObject result, String error) {
        if (!session.finished.compareAndSet(false, true)) return;
        session.result = result;
        session.error = error;
        session.ready.countDown();
        main.post(() -> cleanup(session));
    }

    private void cleanup(Session session) {
        if (session.poll != null) main.removeCallbacks(session.poll);
        if (session.dialog != null) {
            try { session.dialog.dismiss(); } catch (Exception ignored) { }
            session.dialog = null;
        }
        if (session.view != null) {
            WebView view = session.view;
            session.view = null;
            try {
                if (view.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) view.getParent()).removeView(view);
                view.stopLoading();
                view.removeAllViews();
                view.destroy();
            } catch (Exception ignored) { }
        }
    }

    void close() {
        Session session = active.get();
        if (session != null) finish(session, null, "站点已关闭");
    }

    private static final class Session {
        final CountDownLatch ready = new CountDownLatch(1);
        final AtomicBoolean finished = new AtomicBoolean();
        volatile JSONObject result;
        volatile String error;
        WebView view;
        AlertDialog dialog;
        Runnable poll;
    }
}
