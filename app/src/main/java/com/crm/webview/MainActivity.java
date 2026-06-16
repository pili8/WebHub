package com.crm.webview;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // 默认配置
    private static final String DEFAULT_CONFIG =
        "tab1|📊|销售机会|销售机会,https://www.kdocs.cn/wo/sl/v12CEOZt\n" +
        "tab2|📋|最近新增|最近新增,https://www.kdocs.cn/wo/sl/v14T2gpD\n" +
        "tab3|➕|录入线索|录入线索,https://www.kdocs.cn/wo/sl/v13iHfr4";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvTitle, tvSubMenu, tvMenuIcon;
    private ImageView btnRefresh, btnSettings;
    private LinearLayout btnMenu;
    private LinearLayout tab1, tab2, tab3;
    private LinearLayout[] tabs;
    private TextView iconTab1, iconTab2, iconTab3;
    private TextView[] tabIcons;
    private TextView textTab1, textTab2, textTab3;
    private TextView[] tabTexts;

    private int currentTab = 0;
    private int currentLinkIndex = 0; // 当前选项卡下的链接索引

    // 配置数据
    private String[] tabIconsEmoji = {"📊", "📋", "➕"};
    private String[] tabTitles = {"销售机会", "最近新增", "录入线索"};
    private List<List<LinkItem>> tabLinks = new ArrayList<>(); // 每个选项卡的链接列表

    private SharedPreferences prefs;

    static class LinkItem {
        String title;
        String url;

        LinkItem(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences("app_config", MODE_PRIVATE);

        initViews();
        loadConfig();
        setupListeners();

        setupWebView();
        switchTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();

        // 从设置返回时重新加载
        String oldConfig = getConfigString();
        loadConfig();
        String newConfig = getConfigString();

        if (!oldConfig.equals(newConfig)) {
            updateUI();
            loadCurrentLink();
        }
    }

    private String getConfigString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append("tab").append(i + 1).append("|");
            sb.append(tabIconsEmoji[i]).append("|");
            sb.append(tabTitles[i]).append("|");
            for (int j = 0; j < tabLinks.get(i).size(); j++) {
                LinkItem link = tabLinks.get(i).get(j);
                sb.append(link.title).append(",").append(link.url);
                if (j < tabLinks.get(i).size() - 1) sb.append("\n");
            }
            if (i < 2) sb.append("\n");
        }
        return sb.toString();
    }

    private void initViews() {
        webView = findViewById(R.id.webview_main);
        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubMenu = findViewById(R.id.tvSubMenu);
        tvMenuIcon = findViewById(R.id.tvMenuIcon);
        btnMenu = findViewById(R.id.btnMenu);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnSettings = findViewById(R.id.btnSettings);

        tab1 = findViewById(R.id.tab1);
        tab2 = findViewById(R.id.tab2);
        tab3 = findViewById(R.id.tab3);
        tabs = new LinearLayout[]{tab1, tab2, tab3};

        iconTab1 = findViewById(R.id.icon_tab1);
        iconTab2 = findViewById(R.id.icon_tab2);
        iconTab3 = findViewById(R.id.icon_tab3);
        tabIcons = new TextView[]{iconTab1, iconTab2, iconTab3};

        textTab1 = findViewById(R.id.text_tab1);
        textTab2 = findViewById(R.id.text_tab2);
        textTab3 = findViewById(R.id.text_tab3);
        tabTexts = new TextView[]{textTab1, textTab2, textTab3};
    }

    private void setupListeners() {
        tab1.setOnClickListener(v -> switchTab(0));
        tab2.setOnClickListener(v -> switchTab(1));
        tab3.setOnClickListener(v -> switchTab(2));
        btnMenu.setOnClickListener(v -> showSubMenu());
        btnRefresh.setOnClickListener(v -> webView.reload());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void loadConfig() {
        tabLinks.clear();
        tabLinks.add(new ArrayList<>());
        tabLinks.add(new ArrayList<>());
        tabLinks.add(new ArrayList<>());

        String config = prefs.getString("config", DEFAULT_CONFIG);
        String[] lines = config.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\|", 4);
            if (parts.length < 4) continue;

            int tabIndex;
            try {
                tabIndex = Integer.parseInt(parts[0].replace("tab", "")) - 1;
            } catch (Exception e) {
                continue;
            }
            if (tabIndex < 0 || tabIndex > 2) continue;

            tabIconsEmoji[tabIndex] = parts[1];
            tabTitles[tabIndex] = parts[2];

            // 解析链接列表
            String[] links = parts[3].split("\n");
            for (String link : links) {
                link = link.trim();
                if (link.isEmpty()) continue;
                String[] linkParts = link.split(",", 2);
                if (linkParts.length == 2) {
                    tabLinks.get(tabIndex).add(new LinkItem(linkParts[0].trim(), linkParts[1].trim()));
                }
            }
        }

        // 确保每个选项卡至少有一个链接
        for (int i = 0; i < 3; i++) {
            if (tabLinks.get(i).isEmpty()) {
                tabLinks.get(i).add(new LinkItem(tabTitles[i], "about:blank"));
            }
        }
    }

    private void updateUI() {
        for (int i = 0; i < 3; i++) {
            tabIcons[i].setText(tabIconsEmoji[i]);
            tabTexts[i].setText(tabTitles[i]);
        }
        updateSubMenuDisplay();
    }

    private void switchTab(int index) {
        currentTab = index;
        currentLinkIndex = 0; // 切换选项卡时重置为第一个链接

        tvTitle.setText(tabTitles[index]);

        // 更新选项卡样式
        for (int i = 0; i < 3; i++) {
            if (i == index) {
                tabTexts[i].setTextColor(Color.parseColor("#1976D2"));
            } else {
                tabTexts[i].setTextColor(Color.parseColor("#666666"));
            }
        }

        updateSubMenuDisplay();
        loadCurrentLink();
    }

    private void updateSubMenuDisplay() {
        List<LinkItem> links = tabLinks.get(currentTab);
        if (links.size() > 1) {
            tvSubMenu.setVisibility(View.VISIBLE);
            tvMenuIcon.setVisibility(View.VISIBLE);
            tvSubMenu.setText(links.get(currentLinkIndex).title);
        } else {
            tvSubMenu.setVisibility(View.GONE);
            tvMenuIcon.setVisibility(View.GONE);
        }
    }

    private void loadCurrentLink() {
        List<LinkItem> links = tabLinks.get(currentTab);
        if (currentLinkIndex < links.size()) {
            webView.loadUrl(links.get(currentLinkIndex).url);
        }
    }

    private void showSubMenu() {
        List<LinkItem> links = tabLinks.get(currentTab);
        if (links.size() <= 1) return;

        String[] items = new String[links.size()];
        for (int i = 0; i < links.size(); i++) {
            items[i] = links.get(i).title;
        }

        new AlertDialog.Builder(this)
                .setTitle(tabTitles[currentTab])
                .setItems(items, (dialog, which) -> {
                    currentLinkIndex = which;
                    updateSubMenuDisplay();
                    loadCurrentLink();
                })
                .show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUserAgentString(settings.getUserAgentString().replace("; wv", ""));
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowedUrl(request.getUrl().toString());
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

    private boolean isAllowedUrl(String url) {
        if (url == null) return false;
        if (url.contains("kdocs.cn")) return true;
        if (url.contains("wps.cn")) return true;
        if (url.contains("wps.com")) return true;
        if (url.contains("klcdn.com")) return true;
        if (url.contains("wpscdn.com")) return true;
        if (url.contains("account.")) return true;
        if (url.startsWith("javascript:") || url.startsWith("about:blank")) return true;
        if (url.startsWith("data:")) return true;
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            closePopup();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void closePopup() {
        float x = webView.getWidth() / 2f;
        float y = 15f;

        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        webView.dispatchTouchEvent(downEvent);
        downEvent.recycle();

        webView.postDelayed(() -> {
            MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
            webView.dispatchTouchEvent(upEvent);
            upEvent.recycle();
        }, 50);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}
