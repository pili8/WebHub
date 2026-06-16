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

    private static final String[] TAB_URLS = {URL_CARD, URL_TABLE, URL_INPUT};
    private static final String[] TAB_NAMES = {"销售机会", "最近新增", "录入线索"};

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvTitle;
    private LinearLayout tabCard, tabTable, tabInput;
    private LinearLayout[] tabs;
    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 保持屏幕常亮
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 初始化视图
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);
        tabCard = findViewById(R.id.tab_card);
        tabTable = findViewById(R.id.tab_table);
        tabInput = findViewById(R.id.tab_input);

        tabs = new LinearLayout[]{tabCard, tabTable, tabInput};

        // 设置选项卡点击事件
        tabCard.setOnClickListener(v -> switchTab(0));
        tabTable.setOnClickListener(v -> switchTab(1));
        tabInput.setOnClickListener(v -> switchTab(2));

        setupWebView();

        // 默认显示第一个选项卡
        switchTab(0);
    }

    /**
     * 切换选项卡
     */
    private void switchTab(int index) {
        currentTab = index;

        // 更新标题
        tvTitle.setText(TAB_NAMES[index]);

        // 更新选项卡样式
        for (int i = 0; i < tabs.length; i++) {
            LinearLayout tab = tabs[i];
            ImageView icon = (ImageView) tab.getChildAt(0);
            TextView text = (TextView) tab.getChildAt(1);

            if (i == index) {
                // 选中状态
                text.setTextColor(Color.parseColor("#4CAF50"));
                icon.setColorFilter(Color.parseColor("#4CAF50"));
            } else {
                // 未选中状态
                text.setTextColor(Color.parseColor("#666666"));
                icon.setColorFilter(Color.parseColor("#666666"));
            }
        }

        // 加载对应 URL
        webView.loadUrl(TAB_URLS[index]);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
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
                progressBar.setVisibility(View.VISIBLE);
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
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
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
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                // 重新加载当前选项卡
                webView.loadUrl(TAB_URLS[currentTab]);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}
