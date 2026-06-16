package com.crm.webview;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
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

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvTitle, tvArrow;
    private ImageView btnRefresh, btnSettings;
    private LinearLayout btnDropdown, dropdownList;
    private LinearLayout tab1, tab2, tab3;
    private LinearLayout[] tabs;
    private TextView iconTab1, iconTab2, iconTab3;
    private TextView[] tabIcons;
    private TextView textTab1, textTab2, textTab3;
    private TextView[] tabTexts;

    private int currentTab = 0;
    private int currentLinkIndex = 0;
    private boolean isDropdownOpen = false;

    private String[] tabIconsEmoji = {"📊", "📋", "➕"};
    private String[] tabTitles = {"销售机会", "最近新增", "录入线索"};
    private List<List<LinkItem>> tabLinks = new ArrayList<>();

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

        tabLinks.add(new ArrayList<>());
        tabLinks.add(new ArrayList<>());
        tabLinks.add(new ArrayList<>());

        initViews();
        loadConfig();
        updateUI();
        setupListeners();
        setupWebView();
        switchTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();

        String[] oldLinks = {prefs.getString("links1", ""), prefs.getString("links2", ""), prefs.getString("links3", "")};
        loadConfig();
        updateUI();

        String[] newLinks = {prefs.getString("links1", ""), prefs.getString("links2", ""), prefs.getString("links3", "")};

        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            if (!oldLinks[i].equals(newLinks[i])) {
                changed = true;
                break;
            }
        }

        if (changed) {
            currentLinkIndex = 0;
            loadCurrentLink();
        }
    }

    private void initViews() {
        webView = findViewById(R.id.webview_main);
        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);
        tvArrow = findViewById(R.id.tvArrow);
        btnDropdown = findViewById(R.id.btnDropdown);
        dropdownList = findViewById(R.id.dropdownList);
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
        btnRefresh.setOnClickListener(v -> webView.reload());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnDropdown.setOnClickListener(v -> toggleDropdown());
    }

    private void loadConfig() {
        tabIconsEmoji[0] = prefs.getString("icon1", "📊");
        tabIconsEmoji[1] = prefs.getString("icon2", "📋");
        tabIconsEmoji[2] = prefs.getString("icon3", "➕");

        tabTitles[0] = prefs.getString("title1", "销售机会");
        tabTitles[1] = prefs.getString("title2", "最近新增");
        tabTitles[2] = prefs.getString("title3", "录入线索");

        for (int i = 0; i < 3; i++) {
            tabLinks.get(i).clear();
            String linksStr = prefs.getString("links" + (i + 1), "");
            String[] lines = linksStr.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    tabLinks.get(i).add(new LinkItem(parts[0].trim(), parts[1].trim()));
                }
            }
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
        updateDropdown();
    }

    private void switchTab(int index) {
        currentTab = index;
        currentLinkIndex = 0;
        isDropdownOpen = false;

        for (int i = 0; i < 3; i++) {
            if (i == index) {
                tabTexts[i].setTextColor(Color.parseColor("#1976D2"));
            } else {
                tabTexts[i].setTextColor(Color.parseColor("#666666"));
            }
        }

        updateDropdown();
        loadCurrentLink();
    }

    private void updateDropdown() {
        List<LinkItem> links = tabLinks.get(currentTab);

        // 更新标题显示（当前链接名称）
        tvTitle.setText(links.get(currentLinkIndex).title);

        // 多个链接时显示箭头，单个链接时隐藏
        if (links.size() > 1) {
            tvArrow.setVisibility(View.VISIBLE);
            tvArrow.setText(isDropdownOpen ? "▲" : "▼");
        } else {
            tvArrow.setVisibility(View.GONE);
            isDropdownOpen = false;
        }

        // 更新下拉列表
        updateDropdownList();
    }

    private void updateDropdownList() {
        dropdownList.removeAllViews();

        List<LinkItem> links = tabLinks.get(currentTab);

        if (!isDropdownOpen || links.size() <= 1) {
            dropdownList.setVisibility(View.GONE);
            return;
        }

        dropdownList.setVisibility(View.VISIBLE);

        for (int i = 0; i < links.size(); i++) {
            final int index = i;
            LinkItem link = links.get(i);

            TextView item = new TextView(this);
            item.setText(link.title);
            item.setTextSize(14);
            item.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

            if (i == currentLinkIndex) {
                item.setBackgroundColor(Color.parseColor("#E3F2FD"));
                item.setTextColor(Color.parseColor("#1976D2"));
                item.setTypeface(null, Typeface.BOLD);
            } else {
                item.setBackgroundColor(Color.TRANSPARENT);
                item.setTextColor(Color.parseColor("#333333"));
            }

            item.setOnClickListener(v -> {
                currentLinkIndex = index;
                isDropdownOpen = false;
                updateDropdown();
                loadCurrentLink();
            });

            // 添加分割线
            if (i < links.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                dropdownList.addView(item);
                dropdownList.addView(divider);
            } else {
                dropdownList.addView(item);
            }
        }
    }

    private void toggleDropdown() {
        List<LinkItem> links = tabLinks.get(currentTab);
        if (links.size() <= 1) return; // 单个链接时不切换

        isDropdownOpen = !isDropdownOpen;
        updateDropdown();
    }

    private void loadCurrentLink() {
        List<LinkItem> links = tabLinks.get(currentTab);
        if (currentLinkIndex < links.size()) {
            webView.loadUrl(links.get(currentLinkIndex).url);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
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
            // 如果下拉菜单打开，先关闭
            if (isDropdownOpen) {
                isDropdownOpen = false;
                updateDropdown();
                return true;
            }
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
