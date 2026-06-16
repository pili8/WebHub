package com.crm.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

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

    // 文件上传回调
    private ValueCallback<Uri[]> filePathCallback;

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
        requestPermissions();
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

        // 点击刷新：使用缓存快速加载
        btnRefresh.setOnClickListener(v -> webView.reload());

        // 长按刷新：强制从网络加载最新数据
        btnRefresh.setOnLongClickListener(v -> {
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webView.reload();
            // 恢复缓存策略
            webView.postDelayed(() -> {
                webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            }, 1000);
            Toast.makeText(this, "正在刷新最新数据...", Toast.LENGTH_SHORT).show();
            return true;
        });

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnDropdown.setOnClickListener(v -> toggleDropdown());
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        List<String> needRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest.add(permission);
            }
        }

        if (!needRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 权限请求结果，WebView 会自动处理
        }
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
        tvTitle.setText(links.get(currentLinkIndex).title);

        if (links.size() > 1) {
            tvArrow.setVisibility(View.VISIBLE);
            tvArrow.setText(isDropdownOpen ? "▲" : "▼");
        } else {
            tvArrow.setVisibility(View.GONE);
            isDropdownOpen = false;
        }

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
        if (links.size() <= 1) return;

        isDropdownOpen = !isDropdownOpen;
        updateDropdown();
    }

    private void loadCurrentLink() {
        List<LinkItem> links = tabLinks.get(currentTab);
        if (currentLinkIndex < links.size()) {
            String url = links.get(currentLinkIndex).url;

            // 先用缓存快速加载
            webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            webView.loadUrl(url);

            // 延迟后后台刷新（确保缓存版本先显示）
            webView.postDelayed(() -> {
                webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            }, 3000);
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

        // 缓存策略：优先使用缓存，网络不通时也能用
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUserAgentString(settings.getUserAgentString().replace("; wv", ""));
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // 启用定位
        settings.setGeolocationEnabled(true);

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

            // 定位权限请求
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            // 文件选择
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, 1001);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
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
            // 1. 如果下拉菜单打开，先关闭
            if (isDropdownOpen) {
                isDropdownOpen = false;
                updateDropdown();
                return true;
            }

            // 2. 尝试关闭弹窗
            if (tryClosePopup()) {
                return true;
            }

            // 3. 如果有网页历史，返回上一页
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }

            // 4. 都没有则重新加载当前链接
            loadCurrentLink();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean tryClosePopup() {
        // 通过点击屏幕顶部区域尝试关闭弹窗
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

        return true; // 总是返回 true，后续由网页自行处理
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
