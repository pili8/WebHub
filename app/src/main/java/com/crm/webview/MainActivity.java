package com.crm.webview;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // ========================================
    // 金山多维表分享视图链接
    // ========================================
    private static final String TARGET_URL = "https://www.kdocs.cn/wo/sl/v14T2gpD";
    // ========================================

    private WebView webView;
    private ProgressBar progressBar;
    private ImageView btnBack;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 保持屏幕常亮
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 初始化视图
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);

        // 设置返回按钮
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });

        setupWebView();
        webView.loadUrl(TARGET_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // 启用 JavaScript
        settings.setJavaScriptEnabled(true);

        // 启用 DOM 存储（多维表需要）
        settings.setDomStorageEnabled(true);

        // 启用数据库
        settings.setDatabaseEnabled(true);

        // 启用缓存
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 允许文件访问（部分页面需要）
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // 设置 UA 为移动端
        settings.setUserAgentString(settings.getUserAgentString()
                .replace("; wv", ""));

        // 自适应屏幕
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);

        // 混合内容模式（允许 HTTPS 加载 HTTP 资源）
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // 启用 Cookie 持久化
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // WebViewClient - 控制页面导航
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // 只允许加载目标视图 URL，阻止所有其他导航
                if (isAllowedUrl(url)) {
                    return false; // 允许加载
                }

                // 阻止外部链接和跳转到首页
                return true;
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

                // 更新返回按钮状态
                btnBack.setVisibility(view.canGoBack() ? View.VISIBLE : View.GONE);

                // 保存 Cookie
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // 对金山域名的证书错误不中断
                handler.proceed();
            }
        });

        // WebChromeClient - 进度条
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                // 更新标题（可选：显示页面标题或固定显示 CRM）
                // tvTitle.setText(title);
            }
        });
    }

    /**
     * 判断是否为允许加载的 URL
     * 只允许当前视图链接，阻止跳转到首页或其他页面
     */
    private boolean isAllowedUrl(String url) {
        if (url == null) return false;

        // 允许目标 URL
        if (url.equals(TARGET_URL)) return true;

        // 允许视图内的子链接（同一文档的不同视图）
        if (url.startsWith("https://www.kdocs.cn/wo/sl/")) return true;

        // 允许登录页面
        if (url.contains("account.wps.cn") || url.contains("account.kdocs.cn")) return true;

        // 允许 JavaScript 和空白页
        if (url.startsWith("javascript:") || url.startsWith("about:blank")) return true;

        // 允许金山文档的静态资源
        if (url.contains("cdn.kdocs.cn") || url.contains("static.kdocs.cn")) return true;

        // 阻止其他所有链接（包括首页）
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                // 有历史记录时执行页面内返回
                webView.goBack();
                return true;
            } else {
                // 无历史记录时，重新加载目标页面（不退出 APP）
                webView.loadUrl(TARGET_URL);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        // 暂停时保存 Cookie
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
