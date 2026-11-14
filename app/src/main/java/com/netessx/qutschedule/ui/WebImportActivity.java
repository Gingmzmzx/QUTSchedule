package com.netessx.qutschedule.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.auto.ZfKeepAliveJobService;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.data.ZfCaptureStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 教务系统导入：在 WebView 里完成统一身份认证，用户打开「个人课表」后自动抓取课表接口。
 *
 * <p>目标请求是 {@code GET .../portal/api/v2/datacenter/get/xskb}。抓取走两条通道：
 * 页面内 JS 钩子（首选，能拿到页面自己的响应原文）和 WebView 请求拦截后的同源重取
 * （兜底，页面用 WebSocket/特殊方式取数时仍能拿到）。抓到的内容只交给
 * {@link ZfCaptureStore} 写入应用私有目录，不打印、不外发。
 */
public class WebImportActivity extends BaseActivity {

    private static final String START_URL = "https://vpnportal.qut.edu.cn/";
    /** 教务系统「个人课表」数据接口，URL 里带这段就认定为目标请求。 */
    private static final String CAPTURE_PATH = "/portal/api/v2/datacenter/get/xskb";
    private static final int MAX_CHARS = 8 * 1024 * 1024;
    private static final long REFETCH_DELAY_MS = 1500;

    /** 引导流程用：抓到就自动导入并返回，不用用户再点按钮。 */
    public static final String EXTRA_AUTO_IMPORT = "extra_auto_import";

    private WebView webView;
    private TextView tvStatus;
    private ProgressBar progress;
    private Button btnParse;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean refetching = new AtomicBoolean(false);
    private final Set<String> pageCaptured = ConcurrentHashMap.newKeySet();
    private final Object saveLock = new Object();
    private String lastSavedUrl;
    private long lastSavedAt;
    private volatile boolean captured;
    private boolean autoImport;
    private volatile boolean autoImported;
    private int navAttempts;
    private ZfCaptureStore store;

    private static final String HOOK_JS = "(function(){"
            + "if(window.__qutCaptureHook){return;}window.__qutCaptureHook=1;"
            + "var M='" + CAPTURE_PATH + "';"
            + "function report(u,b,s){try{if(window.QutBridge){window.QutBridge.onCapture(u,b,s);}}catch(e){}}"
            + "var f=window.fetch;"
            + "if(f){window.fetch=function(i,o){var u=(typeof i==='string')?i:((i&&i.url)||'');"
            + "return f.apply(this,arguments).then(function(r){"
            + "try{if(u.indexOf(M)>=0){r.clone().text().then(function(t){report(u,t,r.status);});}}catch(e){}"
            + "return r;});};}"
            + "var xo=XMLHttpRequest.prototype.open;"
            + "XMLHttpRequest.prototype.open=function(m,u){this.__qutUrl=u;return xo.apply(this,arguments);};"
            + "var xs=XMLHttpRequest.prototype.send;"
            + "XMLHttpRequest.prototype.send=function(){var x=this;"
            + "x.addEventListener('load',function(){try{var u=x.__qutUrl||'';"
            + "if(u.indexOf(M)>=0){report(u,x.responseText,x.status);}}catch(e){}});"
            + "return xs.apply(this,arguments);};"
            + "})();";

    /** 「个人课表」在 WebVPN 下的页面路径。 */
    private static final String PORTAL_EVENT = "portal/event";

    /** 页面自己没发课表请求时，等这么久再兜底直接请求。 */
    private static final long FALLBACK_DELAY_MS = 15000;

    /**
     * 兜底：页面自己没发出课表请求时，用当前 WebVPN 前缀直接请求一次。
     * 前缀从 location.pathname 读取，不写死 hash。
     */
    private static final String FALLBACK_FETCH_JS = "(function(){try{"
            + "var p=location.pathname.match(/^(\\/https\\/webvpn[0-9a-f]+\\/)/);"
            + "var base=p?p[1]:'/';"
            + "var d=new Date(),y=d.getFullYear(),m=d.getMonth()+1,xn,xq;"
            + "if(m>=8){xn=y+'-'+(y+1);xq='1';}else if(m===1){xn=(y-1)+'-'+y;xq='1';}"
            + "else{xn=(y-1)+'-'+y;xq='2';}"
            + "fetch(base+'portal/api/v2/datacenter/get/xskb?XN='+xn+'&XQ='+xq,"
            + "{headers:{'X-Requested-With':'XMLHttpRequest'},credentials:'include'});"
            + "}catch(e){}})();";

    /** 从当前 URL 取 WebVPN 前缀（形如 /https/webvpn<hex>/），取不到返回 null。 */
    static String webvpnPrefix(String url) {
        if (url == null) {
            return null;
        }
        int start = url.indexOf("/https/webvpn");
        if (start < 0) {
            return null;
        }
        int end = url.indexOf('/', start + "/https/webvpn".length());
        return end < 0 ? null : url.substring(start, end + 1);
    }

    /** 登录后要跳转的「个人课表」地址；已在目标页或取不到前缀时返回 null。 */
    static String portalEventUrl(String url) {
        if (url == null || url.contains(PORTAL_EVENT)) {
            return null;
        }
        String prefix = webvpnPrefix(url);
        if (prefix == null) {
            return null;
        }
        int start = url.indexOf(prefix);
        return url.substring(0, start) + prefix + PORTAL_EVENT;
    }

    private final Runnable fallbackFetch = new Runnable() {
        @Override
        public void run() {
            if (captured || isFinishing() || webView == null) {
                return;
            }
            webView.evaluateJavascript(FALLBACK_FETCH_JS, null);
        }
    };

    private void scheduleFallbackFetch() {
        main.removeCallbacks(fallbackFetch);
        main.postDelayed(fallbackFetch, FALLBACK_DELAY_MS);
    }

    /** 登录页不自动跳转，其余页面都尝试打开个人课表。 */
    private static boolean isLoginPage(String url) {
        if (url == null) {
            return true;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/sso/login") || lower.contains("cas/login")
                || lower.contains("/login?") || lower.contains("/login#");
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = ZfCaptureStore.get(this);
        autoImport = getIntent().getBooleanExtra(EXTRA_AUTO_IMPORT, false);
        setPageTitle(getString(R.string.title_web_import));

        View root = LayoutInflater.from(this).inflate(R.layout.activity_web_import, null, false);
        setPageContent(root);
        tvStatus = root.findViewById(R.id.tv_status);
        progress = root.findViewById(R.id.progress);
        webView = root.findViewById(R.id.web_view);
        btnParse = root.findViewById(R.id.btn_parse);
        btnParse.setEnabled(store.hasCapture());
        if (autoImport) {
            // 自动导入模式下不需要「解析并预览」，抓到后直接回上一页
            btnParse.setVisibility(View.GONE);
            tvStatus.setText(R.string.web_import_auto_hint);
        }
        btnParse.setOnClickListener(v -> startActivity(
                new Intent(this, ImportActivity.class).putExtra(ImportActivity.EXTRA_WEB, true)));

        root.findViewById(R.id.btn_reload).setOnClickListener(v -> webView.reload());
        root.findViewById(R.id.btn_clear).setOnClickListener(v -> confirmClear());

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new Bridge(), "QutBridge");
        webView.setWebViewClient(new Client());
        webView.loadUrl(START_URL);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.web_import_clear)
                .setMessage(R.string.web_import_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.web_import_clear_confirm, (d, w) -> clearSession())
                .show();
    }

    /** 清登录态和已抓取内容，回到起始页。 */
    private void clearSession() {
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();
        WebStorage.getInstance().deleteAllData();
        webView.clearCache(true);
        webView.clearFormData();
        webView.clearHistory();
        webView.clearSslPreferences();
        store.clear();
        pageCaptured.clear();
        captured = false;
        btnParse.setEnabled(false);
        synchronized (saveLock) {
            lastSavedUrl = null;
            lastSavedAt = 0;
        }
        tvStatus.setText(R.string.web_import_wait_login);
        webView.loadUrl(START_URL);
        Toast.makeText(this, R.string.web_import_cleared, Toast.LENGTH_SHORT).show();
    }

    /** 页面钩子命中目标请求。 */
    private final class Bridge {
        @JavascriptInterface
        public void onCapture(String url, String body, int status) {
            if (url == null || !url.contains(CAPTURE_PATH)) {
                return;
            }
            pageCaptured.add(url);
            handleCapture(url, "GET", body, status, "page", null);
        }
    }

    /** 拦截到目标请求后，页面自己还没回传时用同源重取兜底。 */
    private void onTargetRequest(String url, String method, Map<String, String> headers) {
        tvStatus.setText(R.string.web_import_capturing);
        if (!refetching.compareAndSet(false, true)) {
            return;
        }
        JSONObject sanitized = sanitizeHeaders(headers);
        io.execute(() -> {
            try {
                Thread.sleep(REFETCH_DELAY_MS);
                if (!pageCaptured.contains(url)) {
                    String body = refetch(url, headers);
                    if (body != null && !body.isEmpty()) {
                        handleCapture(url, method, body, 200, "refetch", sanitized);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                main.post(() -> tvStatus.setText(
                        getString(R.string.web_import_failed, String.valueOf(e.getMessage()))));
            } finally {
                refetching.set(false);
            }
        });
    }

    /** 这些请求头由连接自己管理，或者含登录态，不能保存也不能照抄。 */
    private static boolean skipHeader(String key) {
        if (key == null) {
            return true;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.equals("cookie") || lower.equals("host") || lower.equals("content-length")
                || lower.equals("accept-encoding") || lower.equals("connection")
                || lower.equals("upgrade-insecure-requests");
    }

    /** 把页面请求头去掉敏感字段后存进抓取元数据，后台自动刷新时原样复放。 */
    private static JSONObject sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        JSONObject out = new JSONObject();
        try {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (skipHeader(e.getKey()) || e.getValue() == null) {
                    continue;
                }
                out.put(e.getKey(), e.getValue());
            }
        } catch (JSONException ignored) {
            // 键来自 HTTP 头，不会发生
        }
        return out.length() == 0 ? null : out;
    }

    /**
     * 用 WebView 的 cookie 重取一次。请求头沿用页面发出的那些（去掉会破坏签名的几个），
     * 这样即使页面把响应藏在闭包里也能拿到原始 JSON。
     */
    private String refetch(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (skipHeader(e.getKey())) {
                        continue;
                    }
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                throw new IOException("HTTP " + code);
            }
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_CHARS) {
                        throw new IOException("响应过大");
                    }
                }
                return out.toString("UTF-8");
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    private void handleCapture(String url, String method, String body, int status, String source,
                               JSONObject headers) {
        if (body == null) {
            return;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty() || !(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            // 登录页、错误页也可能命中路径前缀，只有 JSON 才当课表数据。
            main.post(() -> tvStatus.setText(R.string.web_import_not_json));
            return;
        }
        if (trimmed.length() > MAX_CHARS) {
            main.post(() -> tvStatus.setText(R.string.web_import_too_large));
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (saveLock) {
            if (url.equals(lastSavedUrl) && now - lastSavedAt < REFETCH_DELAY_MS) {
                return;
            }
            lastSavedUrl = url;
            lastSavedAt = now;
        }
        io.execute(() -> {
            try {
                store.save(url, method, body, status, source, headers);
                // 顺手记一份 Cookie，后台自动刷新冷启动时 CookieManager 可能还读不到
                try {
                    store.saveCookie(url, CookieManager.getInstance().getCookie(url));
                } catch (RuntimeException ignored) {
                    // WebView 未初始化时拿不到，忽略即可
                }
                // 重新登录成功：清掉「登录已过期」标记，并恢复会话保活
                ScheduleStore schedule = ScheduleStore.get(this);
                schedule.data().prefs.autoRefreshSessionExpired = false;
                schedule.save();
                ZfKeepAliveJobService.sync(this);
                captured = true;
                main.removeCallbacks(fallbackFetch);
                if (autoImport && !autoImported) {
                    autoImported = true;
                    com.netessx.qutschedule.data.ZfImporter.Result imported =
                            com.netessx.qutschedule.data.ZfImporter.importCaptured(this);
                    main.post(() -> {
                        if (imported.ok) {
                            tvStatus.setText(getString(R.string.web_import_auto_done_fmt, imported.courses));
                            main.postDelayed(() -> {
                                setResult(RESULT_OK);
                                finish();
                            }, 700);
                        } else {
                            tvStatus.setText(imported.error);
                        }
                    });
                    return;
                }
                main.post(() -> {
                    btnParse.setEnabled(true);
                    tvStatus.setText(getString(R.string.web_import_saved_fmt, formatSize(body.length())));
                });
            } catch (IOException e) {
                main.post(() -> tvStatus.setText(
                        getString(R.string.web_import_failed, String.valueOf(e.getMessage()))));
            }
        });
    }

    private static String formatSize(int chars) {
        if (chars < 1024) {
            return chars + " B";
        }
        return String.format(Locale.ROOT, "%.1f KB", chars / 1024.0);
    }

    private final class Client extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            view.evaluateJavascript(HOOK_JS, null);
            if (isLoginPage(url)) {
                if (!captured) {
                    tvStatus.setText(R.string.web_import_wait_login);
                }
                return;
            }
            if (captured) {
                return;
            }
            tvStatus.setText(R.string.web_import_auto_nav);
            String target = portalEventUrl(url);
            if (target != null && navAttempts < 2) {
                navAttempts++;
                view.loadUrl(target);
                return;
            }
            scheduleFallbackFetch();
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (url.contains(CAPTURE_PATH)) {
                Map<String, String> headers = new HashMap<>(request.getRequestHeaders());
                String method = request.getMethod();
                main.post(() -> onTargetRequest(url, method, headers));
            }
            return null;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                progress.setVisibility(View.GONE);
                tvStatus.setText(R.string.web_import_load_failed);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.removeJavascriptInterface("QutBridge");
            webView.destroy();
            webView = null;
        }
        CookieManager.getInstance().flush();
        super.onDestroy();
    }
}
