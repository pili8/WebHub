package com.crm.webview;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // ========================================
    // 三个视图的链接
    // ========================================
    private static final String URL_CARD = "https://www.kdocs.cn/wo/sl/v12CEOZt";   // 销售机会（卡片）
    private static final String URL_TABLE = "https://www.kdocs.cn/wo/sl/v14T2gpD";  // 最近新增（表格）
    private static final String URL_INPUT = "https://www.kdocs.cn/wo/sl/v13iHfr4";  // 录入线索（录入）
    // ========================================

    private WebView webViewCard, webViewTable, webViewInput;
    private WebView[] webViews;
    private ProgressBar progressBar;
    private TextView tvTitle;
    private LinearLayout tabCard, tabTable, tabInput;
    private LinearLayout[] tabs;
    private ImageView iconCard, iconTable, iconInput;
    private ImageView[] icons;
    private TextView textCard, textTable, textInput;
    private TextView[] texts;
    private int currentTab = 0;
    private boolean[] webViewInitialized = {false, false, false};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 保持屏幕常亮
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 初始化视图
        webViewCard = findViewById(R.id.webview_card);
        webViewTable = findViewById(R.id.webview_table);
        webViewInput = findViewById(R.id.webview_input);
        webViews = new WebView[]{webViewCard, webViewTable, webViewInput};

        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);

        tabCard = findViewById(R.id.tab_card);
        tabTable = findViewById(R.id.tab_table);
        tabInput = findViewById(R.id.tab_input);
        tabs = new LinearLayout[]{tabCard, tabTable, tabInput};

        iconCard = findViewById(R.id.icon_card);
        iconTable = findViewById(R.id.icon_table);
        iconInput = findViewById(R.id.icon_input);
        icons = new ImageView[]{iconCard, iconTable, iconInput};

        textCard = findViewById(R.id.text_card);
        textTable = findViewById(R.id.text_table);
        textInput = findViewById(R.id.text_input);
        texts = new TextView[]{textCard, textTable, textInput};

        // 设置选项卡点击事件
        tabCard.setOnClickListener(v -> switchTab(0));
        tabTable.setOnClickListener(v -> switchTab(1));
        tabInput.setOnClickListener(v -> switchTab(2));

        // 初始化所有 WebView
        setupWebView(webViewCard, 0);
        setupWebView(webViewTable, 1);
        setupWebView(webViewInput, 2);

        // 默认显示第一个选项卡
        switchTab(0);
    }

    /**
     * 切换选项卡
     */
    private void switchTab(int index) {
        currentTab = index;

        // 更新标题
        String[] titles = {"销售机会", "最近新增", "录入线索"};
        tvTitle.setText(titles[index]);

        // 更新选项卡样式
        for (int i = 0; i < tabs.length; i++) {
            if (i == index) {
                // 选中状态
                icons[i].setColorFilter(Color.parseColor("#4CAF50"));
                texts[i].setTextColor(Color.parseColor("#4CAF50"));
            } else {
                // 未选中状态
                icons[i].setColorFilter(Color.parseColor("#666666"));
                texts[i].setTextColor(Color.parseColor("#666666"));
            }
        }

        // 切换 WebView 显示
        for (int i = 0; i < webViews.length; i++) {
            if (i == index) {
                webViews[i].setVisibility(View.VISIBLE);
            } else {
                webViews[i].setVisibility(View.GONE);
            }
        }

        // 首次切换到该选项卡时加载 URL
        if (!webViewInitialized[index]) {
            String[] urls = {URL_CARD, URL_TABLE, URL_INPUT};
            webViews[index].loadUrl(urls[index]);
            webViewInitialized[index] = true;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(WebView webView, int index) {
        WebSettings settings = webView.getSettings();

        // 启用 JavaScript
        settings.setJavaScriptEnabled(true);

        // 启用 DOM 存储
        settings.setDomStorageEnabled(true);

        // 启用数据库
        settings.setDatabaseEnabled(true);

        // 启用缓存
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 允许文件访问
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // 设置 UA 为移动端
        settings.setUserAgentString(settings.getUserAgentString()
                .replace("; wv", ""));

        // 自适应屏幕
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);

        // 混合内容模式
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // 启用 Cookie 持久化
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !isAllowedUrl(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // 只有当前显示的 WebView 才显示进度条
                if (view.getVisibility() == View.VISIBLE) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        // WebChromeClient
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view.getVisibility() == View.VISIBLE) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    /**
     * 判断是否为允许加载的 URL
     */
    private boolean isAllowedUrl(String url) {
        if (url == null) return false;

        // 允许金山文档的所有域名
        if (url.contains("kdocs.cn")) return true;
        if (url.contains("wps.cn")) return true;
        if (url.contains("wps.com")) return true;

        // 允许金山相关的 CDN
        if (url.contains("klcdn.com")) return true;
        if (url.contains("wpscdn.com")) return true;

        // 允许登录和认证
        if (url.contains("account.")) return true;

        // 允许 JavaScript 和空白页
        if (url.startsWith("javascript:") || url.startsWith("about:blank")) return true;

        // 允许数据 URI
        if (url.startsWith("data:")) return true;

        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 获取当前显示的 WebView
            WebView currentWebView = webViews[currentTab];

            // 先尝试关闭弹窗
            closePopup(currentWebView);

            return true; // 不退出 APP
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 关闭金山文档中的弹窗
     */
    private void closePopup(WebView webView) {
        // 先检查页面上是否有弹窗
        String checkJs = "(function() {" +
                "  var modals = document.querySelectorAll('[class*=\"modal\"], [class*=\"dialog\"], [class*=\"popup\"], [class*=\"drawer\"], [class*=\"detail\"]');" +
                "  for (var i = 0; i < modals.length; i++) {" +
                "    var el = modals[i];" +
                "    var style = window.getComputedStyle(el);" +
                "    if (style.display !== 'none' && style.visibility !== 'hidden' && el.offsetWidth > 0) {" +
                "      return true;" +
                "    }" +
                "  }" +
                "  return false;" +
                "})()";

        webView.evaluateJavascript(checkJs, hasPopup -> {
            if (hasPopup != null && hasPopup.equals("true")) {
                // 有弹窗，尝试关闭
                String closeJs = "(function() {" +
                        "  // 尝试 ESC 键" +
                        "  document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', keyCode: 27, bubbles: true}));" +
                        "" +
                        "  // 点击页面左上角空白区域（弹窗外）" +
                        "  var evt = new MouseEvent('click', {clientX: 10, clientY: 10, bubbles: true});" +
                        "  document.elementFromPoint(10, 10).dispatchEvent(evt);" +
                        "" +
                        "  // 查找并点击关闭按钮" +
                        "  var btns = document.querySelectorAll('button, [role=\"button\"], .close, [class*=\"close\"]');" +
                        "  for (var i = 0; i < btns.length; i++) {" +
                        "    var rect = btns[i].getBoundingClientRect();" +
                        "    if (rect.width < 60 && rect.height < 60 && (rect.top < 80 || rect.right > window.innerWidth - 80)) {" +
                        "      btns[i].click();" +
                        "      return 'closed';" +
                        "    }" +
                        "  }" +
                        "  return 'attempted';" +
                        "})()";

                webView.evaluateJavascript(closeJs, null);
            } else {
                // 没有弹窗，执行返回
                if (webView.canGoBack()) {
                    webView.goBack();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停所有 WebView
        for (WebView webView : webViews) {
            webView.onPause();
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 恢复当前显示的 WebView
        webViews[currentTab].onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 销毁所有 WebView
        for (WebView webView : webViews) {
            if (webView != null) {
                webView.destroy();
            }
        }
    }
}
