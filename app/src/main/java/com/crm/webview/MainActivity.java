package com.crm.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MAX_TABS = 5;

    // WebView 容器和选项卡容器
    private FrameLayout webViewContainer;
    private LinearLayout tabContainer;
    private LinearLayout bottomMenuContainer;

    // 多 WebView（每个选项卡独立）
    private WebView[] webViews = new WebView[MAX_TABS];
    private boolean[] tabLoaded = new boolean[MAX_TABS]; // 是否已加载过
    private List<LinearLayout> tabViews = new ArrayList<>();
    private List<TextView> tabIconViews = new ArrayList<>();
    private List<TextView> tabTextViews = new ArrayList<>();

    private ProgressBar progressBar;
    private TextView tvTitle, tvArrow;
    private TextView btnMenu;
    private ImageView btnRefresh;
    private LinearLayout btnDropdown, dropdownList;
    private TextView inspectBanner;

    private int currentTab = 0;
    private int currentLinkIndex = 0;
    private int activeLinkIndex = -1; // 当前活跃的链接索引（用于页面操作）
    private boolean isDropdownOpen = false;
    private boolean isInspectMode = false;
    private boolean isBottomMenuOpen = false;
    private boolean isSearchOpen = false;
    private boolean isNightMode = false;
    private boolean isNightModeCSS = true; // 网页也应用夜间模式

    // 定时刷新相关
    private android.os.Handler autoRefreshHandler = new android.os.Handler();
    private Runnable autoRefreshRunnable;
    private int autoRefreshInterval = 0; // 0=关闭, 30=30秒, 60=1分钟, 300=5分钟
    private View autoRefreshDot;

    // 搜索相关
    private LinearLayout searchBar;
    private EditText etSearch;
    private LinearLayout searchResults;
    private CheckBox cbSearchContent;

    // 配置数据
    private int tabCount = 3;
    private String[] tabIcons = new String[MAX_TABS];
    private String[] tabTitles = new String[MAX_TABS];
    private String[] tabActions = new String[MAX_TABS];
    private List<List<LinkItem>> tabLinks = new ArrayList<>();

    private SharedPreferences prefs;
    private ValueCallback<Uri[]> filePathCallback;

    static class LinkItem {
        String title;
        String url;
        String actions;
        String scope; // link/domain/tab/all
        List<ActionItem> actionItems = new ArrayList<>();

        LinkItem(String title, String url) {
            this.title = title;
            this.url = url;
            this.actions = "";
            this.scope = "link";
        }

        LinkItem(String title, String url, String actions) {
            this.title = title;
            this.url = url;
            this.actions = actions;
            this.scope = "link";
            this.actionItems = parseLegacyActions(actions);
        }

        LinkItem(String title, String url, String actions, String scope) {
            this.title = title;
            this.url = url;
            this.actions = actions;
            this.scope = scope;
            this.actionItems = parseLegacyActions(actions);
        }

        LinkItem(String title, String url, String scope, List<ActionItem> actionItems) {
            this.title = title;
            this.url = url;
            this.scope = scope;
            this.actionItems = actionItems != null ? actionItems : new ArrayList<>();
            this.actions = "";
        }
    }

    static class ActionItem {
        String type;
        String selector;
        String value;
        int delay;

        ActionItem(String type, String selector, String value, int delay) {
            this.type = type;
            this.selector = selector;
            this.value = value;
            this.delay = delay;
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
        createTabsAndWebViews();
        setupListeners();
        setupAllWebViews();
        requestPermissions();
        switchTab(0);

        // 应用 App 暗色
        if (isNightMode) {
            applyAppNightMode();
        }
        // 应用网页暗色（等页面加载后会自动注入）
    }

    @Override
    protected void onResume() {
        super.onResume();
        WebView wv = getCurrentWebView();
        if (wv != null) {
            wv.onResume();
        }

        // 从设置页面返回时，重新加载夜间模式设置
        isNightMode = prefs.getBoolean("night_mode", false);
        isNightModeCSS = prefs.getBoolean("night_mode_css", false);
        applyAppNightMode();

        // 恢复定时刷新
        autoRefreshInterval = prefs.getInt("auto_refresh_interval", 0);
        if (autoRefreshInterval > 0) {
            setAutoRefresh(autoRefreshInterval);
        }

        // 检查选项卡数量是否变化
        int oldTabCount = tabCount;
        loadConfig();

        // 如果选项卡数量变化，重新创建UI
        if (tabCount != oldTabCount) {
            createTabsAndWebViews();
            switchTab(0);
        } else {
            // 重新执行页面操作（APP从后台返回时WebView可能重新加载）
            wv = getCurrentWebView();
            if (wv != null) {
                // 先停止旧的 MutationObserver
                stopMutationObserver(wv);
                // 重新执行页面操作
                executeCustomScript(wv);
            }
        }
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webViewContainer);
        tabContainer = findViewById(R.id.tabContainer);
        bottomMenuContainer = findViewById(R.id.bottomMenuContainer);
        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);
        tvArrow = findViewById(R.id.tvArrow);
        btnMenu = findViewById(R.id.btnMenu);
        btnDropdown = findViewById(R.id.btnDropdown);
        dropdownList = findViewById(R.id.dropdownList);
        inspectBanner = findViewById(R.id.inspectBanner);
        btnRefresh = findViewById(R.id.btnRefresh);

        // 搜索相关
        searchBar = findViewById(R.id.searchBar);
        etSearch = findViewById(R.id.etSearch);
        searchResults = findViewById(R.id.searchResults);
        cbSearchContent = findViewById(R.id.cbSearchContent);

        // 夜间模式状态
        isNightMode = prefs.getBoolean("night_mode", false);
        isNightModeCSS = prefs.getBoolean("night_mode_css", false);

        // 定时刷新
        autoRefreshDot = findViewById(R.id.autoRefreshDot);
        autoRefreshInterval = prefs.getInt("auto_refresh_interval", 0);
        updateAutoRefreshIndicator();
    }

    private void loadConfig() {
        tabCount = prefs.getInt("tab_count", 3);
        if (tabCount < 2) tabCount = 2;
        if (tabCount > MAX_TABS) tabCount = MAX_TABS;

        String[] defaultIcons = {"📊", "📋", "➕", "📁", "👤"};
        String[] defaultTitles = {"工作区1", "工作区2", "工作区3", "工作区4", "工作区5"};
        String[] defaultUrls = {
                "about:blank",
                "about:blank",
                "about:blank",
                "about:blank",
                "about:blank"
        };

        String tabsJson = prefs.getString("tabs_config", "");

        if (!tabsJson.isEmpty() && loadConfigFromJson(tabsJson, defaultIcons, defaultTitles, defaultUrls)) {
            return;
        }

        for (int i = 0; i < tabCount; i++) {
            tabIcons[i] = prefs.getString("icon" + (i + 1), defaultIcons[i]);
            tabTitles[i] = prefs.getString("title" + (i + 1), defaultTitles[i]);
            tabActions[i] = prefs.getString("actions" + (i + 1), "");

            // 加载链接
            List<LinkItem> links = new ArrayList<>();
            String linksStr = prefs.getString("links" + (i + 1), "");

            if (linksStr.isEmpty()) {
                links.add(new LinkItem(tabTitles[i], defaultUrls[i]));
            } else {
                String[] lines = linksStr.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\|", 2);
                    String titleUrl = parts[0];
                    String actions = parts.length > 1 ? parts[1] : "";

                    String[] titleUrlParts = titleUrl.split(",", 3);
                    if (titleUrlParts.length >= 2) {
                        String scope = titleUrlParts.length > 2 ? titleUrlParts[2].trim() : "link";
                        links.add(new LinkItem(titleUrlParts[0].trim(), titleUrlParts[1].trim(), actions, scope));
                    }
                }
            }

            if (links.isEmpty()) {
                links.add(new LinkItem(tabTitles[i], "about:blank"));
            }

            if (tabLinks.size() > i) {
                tabLinks.set(i, links);
            } else {
                tabLinks.add(links);
            }
        }

        while (tabLinks.size() > tabCount) {
            tabLinks.remove(tabLinks.size() - 1);
        }
    }

    private boolean loadConfigFromJson(String tabsJson, String[] defaultIcons, String[] defaultTitles, String[] defaultUrls) {
        try {
            JSONArray tabsArray = new JSONArray(tabsJson);
            tabCount = Math.max(2, Math.min(MAX_TABS, tabsArray.length()));

            for (int i = 0; i < tabCount; i++) {
                JSONObject tab = tabsArray.getJSONObject(i);
                tabIcons[i] = tab.optString("icon", defaultIcons[i]);
                tabTitles[i] = tab.optString("title", defaultTitles[i]);
                tabActions[i] = "";

                List<LinkItem> links = new ArrayList<>();
                JSONArray linksArray = tab.optJSONArray("links");
                if (linksArray != null) {
                    for (int j = 0; j < linksArray.length(); j++) {
                        JSONObject linkJson = linksArray.getJSONObject(j);
                        String title = linkJson.optString("title", "");
                        String url = linkJson.optString("url", "");
                        if (title.isEmpty() || url.isEmpty()) continue;

                        List<ActionItem> actions = new ArrayList<>();
                        JSONArray actionsArray = linkJson.optJSONArray("actions");
                        if (actionsArray != null) {
                            for (int k = 0; k < actionsArray.length(); k++) {
                                JSONObject actionJson = actionsArray.getJSONObject(k);
                                String selector = actionJson.optString("selector", "");
                                if (selector.isEmpty()) continue;
                                actions.add(new ActionItem(
                                        actionJson.optString("type", "hide"),
                                        selector,
                                        actionJson.optString("value", ""),
                                        actionJson.optInt("delay", 0)
                                ));
                            }
                        }

                        links.add(new LinkItem(title, url, linkJson.optString("scope", "link"), actions));
                    }
                }

                if (links.isEmpty()) {
                    links.add(new LinkItem(tabTitles[i], defaultUrls[i]));
                }

                if (tabLinks.size() > i) {
                    tabLinks.set(i, links);
                } else {
                    tabLinks.add(links);
                }
            }

            while (tabLinks.size() > tabCount) {
                tabLinks.remove(tabLinks.size() - 1);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createTabsAndWebViews() {
        // 清除旧视图
        webViewContainer.removeAllViews();
        tabContainer.removeAllViews();
        tabViews.clear();
        tabIconViews.clear();
        tabTextViews.clear();

        // 初始化 WebView 数组
        for (int i = 0; i < MAX_TABS; i++) {
            webViews[i] = null;
        }

        // 只创建第一个 WebView
        createWebView(0);

        for (int i = 0; i < tabCount; i++) {

            // 创建选项卡
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            tab.setLayoutParams(tabParams);

            // 选项卡图标
            TextView icon = new TextView(this);
            icon.setText(tabIcons[i]);
            icon.setTextSize(22);
            icon.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            icon.setLayoutParams(iconParams);

            // 选项卡文字
            TextView text = new TextView(this);
            text.setText(tabTitles[i]);
            text.setTextSize(10);
            text.setGravity(Gravity.CENTER);
            text.setTextColor(i == 0 ? Color.parseColor("#1976D2") : Color.parseColor("#666666"));
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            textParams.topMargin = dpToPx(2);
            text.setLayoutParams(textParams);

            tab.addView(icon);
            tab.addView(text);
            tabContainer.addView(tab);

            tabViews.add(tab);
            tabIconViews.add(icon);
            tabTextViews.add(text);

            // 点击切换选项卡
            final int index = i;
            tab.setOnClickListener(v -> switchTab(index));

            // 长按显示子链接菜单
            tab.setOnLongClickListener(v -> {
                showBottomMenu(index);
                return true;
            });
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView(int index) {
        if (webViews[index] != null) return; // 已创建

        WebView webView = new WebView(this);
        FrameLayout.LayoutParams wvParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(wvParams);
        webView.setVisibility(index == currentTab ? View.VISIBLE : View.GONE);
        webViewContainer.addView(webView);
        webViews[index] = webView;

        // 设置 WebView
        setupWebView(webView);
    }

    private WebView getCurrentWebView() {
        return webViews[currentTab];
    }

    private void setupListeners() {
        // 短按刷新：强制联网刷新
        btnRefresh.setOnClickListener(v -> {
            WebView wv = getCurrentWebView();
            if (wv != null) {
                wv.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                wv.reload();
                wv.postDelayed(() -> {
                    if (wv != null) {
                        wv.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                    }
                }, 1000);
                Toast.makeText(this, "刷新中...", Toast.LENGTH_SHORT).show();
            }
        });

        // 长按刷新：回到配置的链接
        btnRefresh.setOnLongClickListener(v -> {
            loadCurrentLink();
            Toast.makeText(this, "已回到首页", Toast.LENGTH_SHORT).show();
            return true;
        });

        btnDropdown.setOnClickListener(v -> toggleDropdown());
        btnMenu.setOnClickListener(v -> showPopupMenu());

        // 搜索取消按钮
        TextView btnSearchCancel = findViewById(R.id.btnSearchCancel);
        btnSearchCancel.setOnClickListener(v -> closeSearch());

        // 搜索输入框监听
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void showPopupMenu() {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, btnMenu);

        // 第一组：搜索、复制
        popup.getMenu().add(1, 2, 0, "🔍 搜索");
        popup.getMenu().add(1, 1, 0, "📋 复制链接");

        // 第二组：夜间、刷新
        popup.getMenu().add(2, 3, 0, isNightMode ? "☀️ 日间模式" : "🌙 夜间模式");
        popup.getMenu().add(2, 4, 0, autoRefreshInterval > 0 ? "⏰ 刷新中" : "⏰ 定时刷新");

        // 第三组：工具、设置、退出
        popup.getMenu().add(3, 5, 0, "🎯 查找元素");
        popup.getMenu().add(3, 6, 0, "⚙️ 设置");
        popup.getMenu().add(3, 7, 0, "🚪 退出");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                copyCurrentUrl();
                return true;
            } else if (item.getItemId() == 2) {
                toggleSearch();
                return true;
            } else if (item.getItemId() == 3) {
                toggleNightMode();
                return true;
            } else if (item.getItemId() == 4) {
                // 显示定时刷新选项
                showAutoRefreshMenu();
                return true;
            } else if (item.getItemId() == 5) {
                toggleInspectMode();
                return true;
            } else if (item.getItemId() == 6) {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra("night_mode", isNightMode);
                startActivity(intent);
                return true;
            } else if (item.getItemId() == 7) {
                finish();
                return true;
            } else if (item.getItemId() >= 50 && item.getItemId() <= 53) {
                int[] intervals = {0, 30, 60, 300};
                int index = item.getItemId() - 50;
                setAutoRefresh(intervals[index]);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void showAutoRefreshMenu() {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, btnMenu);
        popup.getMenu().add(1, 50, 0, "关闭").setChecked(autoRefreshInterval == 0);
        popup.getMenu().add(1, 51, 0, "每30秒").setChecked(autoRefreshInterval == 30);
        popup.getMenu().add(1, 52, 0, "每1分钟").setChecked(autoRefreshInterval == 60);
        popup.getMenu().add(1, 53, 0, "每5分钟").setChecked(autoRefreshInterval == 300);
        popup.getMenu().setGroupCheckable(1, true, true);

        popup.setOnMenuItemClickListener(item -> {
            int[] intervals = {0, 30, 60, 300};
            int index = item.getItemId() - 50;
            if (index >= 0 && index < intervals.length) {
                setAutoRefresh(intervals[index]);
            }
            return true;
        });

        popup.show();
    }

    // ========== 复制地址 ==========

    private void copyCurrentUrl() {
        WebView wv = getCurrentWebView();
        if (wv != null) {
            String url = wv.getUrl();
            if (url != null && !url.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("url", url);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "已复制: " + url, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "当前页面没有URL", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ========== 定时刷新 ==========

    private void setAutoRefresh(int interval) {
        autoRefreshInterval = interval;
        prefs.edit().putInt("auto_refresh_interval", interval).apply();

        // 停止之前的定时器
        if (autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        }

        if (interval > 0) {
            // 启动定时刷新
            autoRefreshRunnable = () -> {
                WebView wv = getCurrentWebView();
                if (wv != null) {
                    wv.reload();
                }
                autoRefreshHandler.postDelayed(autoRefreshRunnable, interval * 1000L);
            };
            autoRefreshHandler.postDelayed(autoRefreshRunnable, interval * 1000L);
            Toast.makeText(this, "定时刷新: " + formatInterval(interval), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "定时刷新已关闭", Toast.LENGTH_SHORT).show();
        }

        updateAutoRefreshIndicator();
    }

    private String formatInterval(int seconds) {
        if (seconds < 60) return seconds + "秒";
        if (seconds < 3600) return (seconds / 60) + "分钟";
        return (seconds / 3600) + "小时";
    }

    private void updateAutoRefreshIndicator() {
        if (autoRefreshDot != null) {
            autoRefreshDot.setVisibility(autoRefreshInterval > 0 ? View.VISIBLE : View.GONE);
        }
    }

    // ========== 搜索功能 ==========

    private void toggleSearch() {
        if (isSearchOpen) {
            closeSearch();
        } else {
            openSearch();
        }
    }

    private void openSearch() {
        isSearchOpen = true;
        searchBar.setVisibility(View.VISIBLE);
        searchResults.setVisibility(View.VISIBLE);
        webViewContainer.setVisibility(View.GONE);
        etSearch.requestFocus();

        // 显示键盘
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearch() {
        isSearchOpen = false;
        searchBar.setVisibility(View.GONE);
        searchResults.setVisibility(View.GONE);
        webViewContainer.setVisibility(View.VISIBLE);
        etSearch.setText("");

        // 隐藏键盘
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    private void performSearch() {
        String query = etSearch.getText().toString().trim().toLowerCase();
        if (query.isEmpty()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean searchContent = cbSearchContent.isChecked();
        searchResults.removeAllViews();

        // 显示搜索中提示
        TextView loadingText = new TextView(this);
        loadingText.setText("搜索中...");
        loadingText.setTextSize(14);
        loadingText.setTextColor(Color.parseColor("#999999"));
        loadingText.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        loadingText.setGravity(Gravity.CENTER);
        searchResults.addView(loadingText);

        if (searchContent) {
            // 搜索网页内容（后台加载）
            new Thread(() -> {
                List<SearchResult> results = searchWebContent(query);
                runOnUiThread(() -> {
                    searchResults.removeAllViews();
                    displaySearchResults(results, query);
                });
            }).start();
        } else {
            // 只搜索标题和URL（即时）
            List<SearchResult> results = searchLinksOnly(query);
            searchResults.removeAllViews();
            displaySearchResults(results, query);
        }
    }

    private List<SearchResult> searchLinksOnly(String query) {
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < tabLinks.size(); i++) {
            List<LinkItem> links = tabLinks.get(i);
            for (int j = 0; j < links.size(); j++) {
                LinkItem link = links.get(j);
                if (link.title.toLowerCase().contains(query) || link.url.toLowerCase().contains(query)) {
                    results.add(new SearchResult(i, j, link.title, link.url, "标题/URL"));
                }
            }
        }
        return results;
    }

    private List<SearchResult> searchWebContent(String query) {
        List<SearchResult> results = new ArrayList<>();

        // 先搜索标题和URL
        results.addAll(searchLinksOnly(query));

        // 遍历所有已创建的 WebView
        for (int i = 0; i < MAX_TABS; i++) {
            WebView wv = webViews[i];
            if (wv == null) continue;

            try {
                final String[] content = {""};
                final boolean[] done = {false};
                final int tabIndex = i;

                runOnUiThread(() -> {
                    wv.evaluateJavascript(
                            "(function() { return document.body.innerText; })();",
                            value -> {
                                content[0] = value != null ? value : "";
                                synchronized (done) {
                                    done[0] = true;
                                    done.notify();
                                }
                            }
                    );
                });

                synchronized (done) {
                    if (!done[0]) done.wait(3000);
                }

                if (!content[0].isEmpty() && content[0].toLowerCase().contains(query)) {
                    // 查找这个 WebView 对应的链接
                    if (tabIndex < tabLinks.size()) {
                        List<LinkItem> links = tabLinks.get(tabIndex);
                        int linkIndex = (tabIndex == currentTab) ?
                                (activeLinkIndex >= 0 ? activeLinkIndex : currentLinkIndex) : 0;

                        if (linkIndex >= 0 && linkIndex < links.size()) {
                            LinkItem link = links.get(linkIndex);
                            boolean exists = false;
                            for (SearchResult r : results) {
                                if (r.tabIndex == tabIndex && r.linkIndex == linkIndex) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                results.add(new SearchResult(tabIndex, linkIndex, link.title, link.url, "页面内容"));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }

        return results;
    }

    private void displaySearchResults(List<SearchResult> results, String query) {
        if (results.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("未找到匹配结果");
            emptyText.setTextSize(14);
            emptyText.setTextColor(Color.parseColor("#999999"));
            emptyText.setPadding(dpToPx(16), dpToPx(32), dpToPx(16), dpToPx(16));
            emptyText.setGravity(Gravity.CENTER);
            searchResults.addView(emptyText);
            return;
        }

        // 结果数量
        TextView countText = new TextView(this);
        countText.setText("找到 " + results.size() + " 个结果");
        countText.setTextSize(12);
        countText.setTextColor(Color.parseColor("#999999"));
        countText.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(8));
        searchResults.addView(countText);

        // 结果列表
        for (SearchResult result : results) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
            item.setBackgroundResource(android.R.drawable.list_selector_background);

            // 标题
            TextView titleView = new TextView(this);
            titleView.setText(result.title);
            titleView.setTextSize(15);
            titleView.setTextColor(Color.parseColor("#333333"));
            titleView.setMaxLines(1);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.addView(titleView);

            // URL
            TextView urlView = new TextView(this);
            urlView.setText(result.url);
            urlView.setTextSize(12);
            urlView.setTextColor(Color.parseColor("#1976D2"));
            urlView.setMaxLines(1);
            urlView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            urlView.setPadding(0, dpToPx(2), 0, 0);
            item.addView(urlView);

            // 来源
            TextView sourceView = new TextView(this);
            sourceView.setText("匹配: " + result.source);
            sourceView.setTextSize(11);
            sourceView.setTextColor(Color.parseColor("#999999"));
            sourceView.setPadding(0, dpToPx(2), 0, 0);
            item.addView(sourceView);

            // 分割线
            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dividerParams.topMargin = dpToPx(12);
            divider.setLayoutParams(dividerParams);

            // 点击打开链接
            final int tabIndex = result.tabIndex;
            final int linkIndex = result.linkIndex;
            item.setOnClickListener(v -> {
                closeSearch();
                switchTab(tabIndex);
                switchLink(tabIndex, linkIndex);
            });

            searchResults.addView(item);
            searchResults.addView(divider);
        }
    }

    static class SearchResult {
        int tabIndex;
        int linkIndex;
        String title;
        String url;
        String source;

        SearchResult(int tabIndex, int linkIndex, String title, String url, String source) {
            this.tabIndex = tabIndex;
            this.linkIndex = linkIndex;
            this.title = title;
            this.url = url;
            this.source = source;
        }
    }

    // ========== 夜间模式 ==========

    // App 暗色模式（主开关）
    private void toggleNightMode() {
        isNightMode = !isNightMode;
        prefs.edit().putBoolean("night_mode", isNightMode).apply();
        applyAppNightMode();
        Toast.makeText(this, isNightMode ? "夜间模式已开启" : "夜间模式已关闭", Toast.LENGTH_SHORT).show();
    }

    // 应用 App 暗色
    private void applyAppNightMode() {
        if (isNightMode) {
            // 标题栏变暗
            findViewById(R.id.toolbar).setBackgroundColor(Color.parseColor("#1E1E1E"));
            // 底部选项卡变暗
            tabContainer.setBackgroundColor(Color.parseColor("#1E1E1E"));
            bottomMenuContainer.setBackgroundColor(Color.parseColor("#1E1E1E"));
            // WebView 背景变暗
            webViewContainer.setBackgroundColor(Color.parseColor("#121212"));

            // 如果网页暗色开关开启，注入 CSS
            if (isNightModeCSS) {
                for (int i = 0; i < MAX_TABS; i++) {
                    if (webViews[i] != null) {
                        injectNightModeCSS(webViews[i]);
                    }
                }
            }
        } else {
            // 标题栏恢复
            findViewById(R.id.toolbar).setBackgroundColor(Color.parseColor("#1976D2"));
            // 底部恢复
            tabContainer.setBackgroundColor(Color.WHITE);
            bottomMenuContainer.setBackgroundColor(Color.WHITE);
            webViewContainer.setBackgroundColor(Color.WHITE);

            // 移除网页暗色 CSS
            for (int i = 0; i < MAX_TABS; i++) {
                if (webViews[i] != null) {
                    removeNightModeCSS(webViews[i]);
                }
            }
        }
    }

    private void injectNightModeCSS(WebView webView) {
        String css = "var s=document.getElementById('wh-nm');" +
                "if(!s){s=document.createElement('style');s.id='wh-nm';document.head.appendChild(s);}" +
                "s.textContent='*{background-color:#1a1a1a!important;color:#ccc!important;border-color:#333!important}" +
                "a{color:#6db3f2!important}" +
                "input,textarea,select,button{background:#2a2a2a!important;color:#ccc!important}" +
                "img,video{opacity:.85}';";
        webView.evaluateJavascript("(function(){" + css + "})()", null);
    }

    private void removeNightModeCSS(WebView webView) {
        webView.evaluateJavascript("(function(){var s=document.getElementById('wh-nm');if(s)s.remove();})()", null);
    }

    /**
     * 显示底部子链接菜单
     */
    private void showBottomMenu(int tabIndex) {
        if (tabIndex >= tabLinks.size()) return;
        List<LinkItem> links = tabLinks.get(tabIndex);
        if (links.size() <= 1) return;

        // 如果点击的是当前选项卡，切换显示/隐藏
        if (tabIndex == currentTab && isBottomMenuOpen) {
            hideBottomMenu();
            return;
        }

        isBottomMenuOpen = true;
        bottomMenuContainer.removeAllViews();
        bottomMenuContainer.setVisibility(View.VISIBLE);

        // 标题
        TextView title = new TextView(this);
        title.setText(tabTitles[tabIndex] + " - 选择链接");
        title.setTextSize(12);
        title.setTextColor(Color.parseColor("#999999"));
        title.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(4));
        bottomMenuContainer.addView(title);

        // 链接列表
        for (int i = 0; i < links.size(); i++) {
            final int linkIndex = i;
            LinkItem link = links.get(i);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

            // 高亮当前选中的链接
            if (tabIndex == currentTab && i == currentLinkIndex) {
                item.setBackgroundColor(Color.parseColor("#E3F2FD"));
            }

            TextView dot = new TextView(this);
            dot.setText("•");
            dot.setTextSize(14);
            dot.setTextColor(Color.parseColor("#1976D2"));
            dot.setPadding(0, 0, dpToPx(8), 0);

            TextView linkTitle = new TextView(this);
            linkTitle.setText(link.title);
            linkTitle.setTextSize(14);
            linkTitle.setTextColor(Color.parseColor("#333333"));

            item.addView(dot);
            item.addView(linkTitle);

            item.setOnClickListener(v -> {
                switchToTab(tabIndex, linkIndex);
                hideBottomMenu();
            });

            bottomMenuContainer.addView(item);

            // 分割线
            if (i < links.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.parseColor("#E0E0E0"));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                dividerParams.leftMargin = dpToPx(16);
                divider.setLayoutParams(dividerParams);
                bottomMenuContainer.addView(divider);
            }
        }
    }

    private void hideBottomMenu() {
        isBottomMenuOpen = false;
        bottomMenuContainer.setVisibility(View.GONE);
        bottomMenuContainer.removeAllViews();
    }

    private void switchToTab(int tabIndex, int linkIndex) {
        if (tabIndex < 0 || tabIndex >= tabCount) return;
        if (linkIndex < 0 || tabIndex >= tabLinks.size()) return;
        if (linkIndex >= tabLinks.get(tabIndex).size()) return;

        currentTab = tabIndex;
        currentLinkIndex = linkIndex;

        // 更新选项卡样式
        for (int i = 0; i < tabTextViews.size(); i++) {
            tabTextViews.get(i).setTextColor(i == tabIndex ? Color.parseColor("#1976D2") : Color.parseColor("#666666"));
        }

        // 加载链接
        loadCurrentLink();
        updateDropdown();
    }

    private void toggleInspectMode() {
        isInspectMode = !isInspectMode;
        if (isInspectMode) {
            inspectBanner.setVisibility(View.VISIBLE);
            Toast.makeText(this, "已进入查找元素模式", Toast.LENGTH_SHORT).show();
        } else {
            inspectBanner.setVisibility(View.GONE);
            Toast.makeText(this, "已退出查找元素模式", Toast.LENGTH_SHORT).show();
        }
    }

    private void switchTab(int index) {
        if (index < 0 || index >= tabCount) return;

        // 关闭底部菜单
        hideBottomMenu();

        if (isInspectMode) {
            isInspectMode = false;
            inspectBanner.setVisibility(View.GONE);
        }

        // 隐藏当前 WebView 并停止 MutationObserver
        WebView oldWebView = getCurrentWebView();
        if (oldWebView != null) {
            stopMutationObserver(oldWebView);
            oldWebView.setVisibility(View.GONE);
        }

        currentTab = index;
        currentLinkIndex = 0;
        activeLinkIndex = -1; // 重置活跃链接
        isDropdownOpen = false;

        // 切换选项卡样式
        for (int i = 0; i < tabTextViews.size(); i++) {
            if (i == index) {
                tabTextViews.get(i).setTextColor(Color.parseColor("#1976D2"));
            } else {
                tabTextViews.get(i).setTextColor(Color.parseColor("#666666"));
            }
        }

        // 创建 WebView（如果不存在）
        createWebView(index);

        // 显示当前 WebView
        WebView newWebView = getCurrentWebView();
        if (newWebView != null) {
            newWebView.setVisibility(View.VISIBLE);
            // 恢复 WebView（可能被系统暂停）
            newWebView.onResume();
        }

        // 检查 WebView 是否有效（被系统回收后可能白屏）
        if (tabLoaded[index] && newWebView != null) {
            String url = newWebView.getUrl();
            if (url == null || url.equals("about:blank")) {
                // WebView 被回收，重新加载
                loadCurrentLink();
            }
        } else if (!tabLoaded[index]) {
            // 第一次访问
            loadCurrentLink();
            tabLoaded[index] = true;
        }

        updateDropdown();
    }

    private void switchLink(int tabIndex, int linkIndex) {
        if (tabIndex < 0 || tabIndex >= tabLinks.size()) return;
        List<LinkItem> links = tabLinks.get(tabIndex);
        if (linkIndex < 0 || linkIndex >= links.size()) return;

        currentLinkIndex = linkIndex;

        // 加载链接
        WebView wv = getCurrentWebView();
        if (wv != null) {
            LinkItem link = links.get(linkIndex);
            if (!isAllowedUrl(link.url)) return;
            wv.loadUrl(link.url);
            tvTitle.setText(link.title);
        }

        updateDropdown();
    }

    private void updateDropdown() {
        if (tabLinks.size() <= currentTab) return;
        List<LinkItem> links = tabLinks.get(currentTab);
        if (links.isEmpty()) return;

        if (currentLinkIndex >= links.size()) {
            currentLinkIndex = 0;
        }

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

        if (tabLinks.size() <= currentTab) return;
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
        if (tabLinks.size() <= currentTab) return;
        List<LinkItem> links = tabLinks.get(currentTab);
        if (links.size() <= 1) return;

        isDropdownOpen = !isDropdownOpen;
        updateDropdown();
    }

    private void loadCurrentLink() {
        if (tabLinks.size() <= currentTab) return;
        List<LinkItem> links = tabLinks.get(currentTab);
        WebView wv = getCurrentWebView();
        if (currentLinkIndex < links.size() && wv != null) {
            String url = links.get(currentLinkIndex).url;
            if (!isAllowedUrl(url)) return;
            activeLinkIndex = currentLinkIndex; // 设置活跃链接
            wv.loadUrl(url);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupAllWebViews() {
        // 只设置已创建的 WebView
        for (int i = 0; i < MAX_TABS; i++) {
            if (webViews[i] != null) {
                setupWebView(webViews[i]);
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUserAgentString(settings.getUserAgentString().replace("; wv", ""));
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setGeolocationEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setOnTouchListener((v, event) -> {
            if (isInspectMode && event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                float density = getResources().getDisplayMetrics().density;
                inspectElementAt(x / density, y / density);
                return true;
            }
            return false;
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowedUrl(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (webView != null && view == webView) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                // 执行自定义操作
                executeCustomScript(view);
                // 夜间模式 CSS
                if (isNightMode && isNightModeCSS) {
                    injectNightModeCSS(view);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(MainActivity.this, "证书错误，已停止加载", Toast.LENGTH_SHORT).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (webView != null && view == webView) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

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

    private void inspectElementAt(float x, float y) {
        WebView wv = getCurrentWebView();
        if (wv == null) return;

        String js = "(function() {" +
                "  var el = document.elementFromPoint(" + x + ", " + y + ");" +
                "  if (!el) return null;" +
                "  var result = {};" +
                "  result.tag = el.tagName.toLowerCase();" +
                "  result.id = el.id || '';" +
                "  result.classes = (typeof el.className === 'string') ? el.className.trim() : '';" +
                "  var text = el.textContent || '';" +
                "  result.text = text.length > 100 ? text.substring(0, 100) + '...' : text.trim();" +
                "  el.style.outline = '3px solid #FF5722';" +
                "  setTimeout(function() { el.style.outline = ''; }, 2000);" +
                "  return JSON.stringify(result);" +
                "})()";

        wv.evaluateJavascript(js, value -> {
            if (value != null && !value.equals("null")) {
                try {
                    String json = value;
                    if (json.startsWith("\"")) {
                        json = json.substring(1, json.length() - 1);
                    }
                    json = json.replace("\\\"", "\"");
                    json = json.replace("\\\\", "\\");

                    String tag = extractJsonString(json, "tag");
                    String id = extractJsonString(json, "id");
                    String classes = extractJsonString(json, "classes");
                    String text = extractJsonString(json, "text");

                    showElementInfoDialog(tag, id, classes, text);
                } catch (Exception e) {
                    Toast.makeText(this, "解析失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }

    private void showElementInfoDialog(String tag, String id, String classes, String text) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_element_info, null);

        TextView tvTag = dialogView.findViewById(R.id.tvTag);
        TextView tvId = dialogView.findViewById(R.id.tvId);
        TextView tvClass = dialogView.findViewById(R.id.tvClass);
        TextView tvText = dialogView.findViewById(R.id.tvText);

        TextView btnCopyId = dialogView.findViewById(R.id.btnCopyId);
        TextView btnCopyClass = dialogView.findViewById(R.id.btnCopyClass);
        TextView btnCopyAll = dialogView.findViewById(R.id.btnCopyAll);
        TextView btnClose = dialogView.findViewById(R.id.btnClose);

        tvTag.setText("<" + tag + ">");

        if (id != null && !id.isEmpty()) {
            tvId.setVisibility(View.VISIBLE);
            tvId.setText("#" + id);
            btnCopyId.setVisibility(View.VISIBLE);
            btnCopyId.setOnClickListener(v -> {
                copyToClipboard("#" + id);
                Toast.makeText(this, "已复制: #" + id, Toast.LENGTH_SHORT).show();
            });
        }

        if (classes != null && !classes.isEmpty()) {
            String firstClass = classes.split("\\s+")[0];
            tvClass.setVisibility(View.VISIBLE);
            tvClass.setText("." + firstClass);
            btnCopyClass.setVisibility(View.VISIBLE);
            btnCopyClass.setOnClickListener(v -> {
                copyToClipboard("." + firstClass);
                Toast.makeText(this, "已复制: ." + firstClass, Toast.LENGTH_SHORT).show();
            });
        }

        if (text != null && !text.isEmpty()) {
            tvText.setVisibility(View.VISIBLE);
            tvText.setText(text);
        }

        StringBuilder allInfo = new StringBuilder();
        allInfo.append("标签: ").append(tag).append("\n");
        if (id != null && !id.isEmpty()) allInfo.append("ID: #").append(id).append("\n");
        if (classes != null && !classes.isEmpty()) allInfo.append("Class: .").append(classes.split("\\s+")[0]).append("\n");
        if (text != null && !text.isEmpty()) allInfo.append("文本: ").append(text);

        btnCopyAll.setOnClickListener(v -> {
            copyToClipboard(allInfo.toString());
            Toast.makeText(this, "已复制全部信息", Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.getWindow().setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("element", text);
        clipboard.setPrimaryClip(clip);
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.VIBRATE,
                Manifest.permission.NFC,
                Manifest.permission.BLUETOOTH
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

        if (url.startsWith("tel:")) {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(intent);
            } else {
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                startActivity(dial);
            }
            return false;
        }
        if (url.startsWith("mailto:")) {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
            startActivity(intent);
            return false;
        }
        if (url.startsWith("sms:")) {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
            startActivity(intent);
            return false;
        }

        if (url.startsWith("http://")) {
            Toast.makeText(this, "当前仅允许加载 HTTPS 链接", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (url.startsWith("https://")) return true;
        if (url.startsWith("javascript:") || url.startsWith("about:blank") || url.startsWith("data:")) return true;

        return false;
    }

    private void executeCustomScript(WebView webView) {
        // 检查页面操作开关
        boolean pageActionsEnabled = prefs.getBoolean("page_actions_enabled", true);
        if (!pageActionsEnabled) return;

        // 收集所有需要执行的操作
        List<ActionItem> allActions = new ArrayList<>();

        // 获取当前链接的 scope
        String currentScope = "link";
        if (currentTab >= 0 && currentTab < tabLinks.size()) {
            List<LinkItem> links = tabLinks.get(currentTab);
            int linkIndex = activeLinkIndex >= 0 ? activeLinkIndex : currentLinkIndex;
            if (linkIndex >= 0 && linkIndex < links.size()) {
                currentScope = links.get(linkIndex).scope;
            }
        }

        // 根据 scope 收集操作
        if ("all".equals(currentScope)) {
            // 所有选项卡
            for (int i = 0; i < tabLinks.size(); i++) {
                collectActions(tabLinks.get(i), allActions);
            }
        } else if ("tab".equals(currentScope)) {
            // 当前选项卡
            if (currentTab >= 0 && currentTab < tabLinks.size()) {
                collectActions(tabLinks.get(currentTab), allActions);
            }
        } else if ("domain".equals(currentScope)) {
            // 相似域名
            if (currentTab >= 0 && currentTab < tabLinks.size()) {
                List<LinkItem> links = tabLinks.get(currentTab);
                int linkIndex = activeLinkIndex >= 0 ? activeLinkIndex : currentLinkIndex;
                if (linkIndex >= 0 && linkIndex < links.size()) {
                    String currentDomain = getDomain(links.get(linkIndex).url);
                    for (List<LinkItem> tabLinksList : tabLinks) {
                        for (LinkItem link : tabLinksList) {
                            if (hasActions(link)) {
                                String domain = getDomain(link.url);
                                if (currentDomain.equals(domain)) {
                                    collectActions(link, allActions);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 仅此链接
            if (currentTab >= 0 && currentTab < tabLinks.size()) {
                List<LinkItem> links = tabLinks.get(currentTab);
                int linkIndex = activeLinkIndex >= 0 ? activeLinkIndex : currentLinkIndex;
                if (linkIndex >= 0 && linkIndex < links.size()) {
                    collectActions(links.get(linkIndex), allActions);
                }
            }
        }

        if (!allActions.isEmpty()) {
            String js = buildScriptFromActions(allActions);
            if (!js.isEmpty()) {
                // 先执行一次
                webView.evaluateJavascript(js, null);

                // 启动 MutationObserver 监听页面变化
                startMutationObserver(webView, js);
            }
        }
    }

    private void collectActions(List<LinkItem> links, List<ActionItem> allActions) {
        for (LinkItem link : links) {
            collectActions(link, allActions);
        }
    }

    private void collectActions(LinkItem link, List<ActionItem> allActions) {
        if (link.actionItems != null && !link.actionItems.isEmpty()) {
            allActions.addAll(link.actionItems);
        } else if (link.actions != null && !link.actions.isEmpty()) {
            allActions.addAll(parseLegacyActions(link.actions));
        }
    }

    private boolean hasActions(LinkItem link) {
        return (link.actionItems != null && !link.actionItems.isEmpty())
                || (link.actions != null && !link.actions.isEmpty());
    }

    private String getDomain(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host == null) return "";
            // 提取主域名（去掉子域名）
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            }
            return host;
        } catch (Exception e) {
            return "";
        }
    }

    private void startMutationObserver(WebView webView, String actionJs) {
        // 使用 MutationObserver 监听页面内容变化
        String observerJs = "(function() {" +
                "if (window._webhubObserver) return;" +
                "var timeout = null;" +
                "var actionFn = function() {" +
                "  try { " + actionJs + " } catch(e) {}" +
                "};" +
                "window._webhubObserver = new MutationObserver(function(mutations) {" +
                "  if (timeout) clearTimeout(timeout);" +
                "  timeout = setTimeout(actionFn, 300);" +
                "});" +
                "if (document.body) {" +
                "  window._webhubObserver.observe(document.body, {" +
                "    childList: true," +
                "    subtree: true," +
                "    characterData: true" +
                "  });" +
                "  actionFn();" + // 立即执行一次
                "}" +
                "})()";

        webView.evaluateJavascript(observerJs, null);
    }

    private void stopMutationObserver(WebView webView) {
        if (webView != null) {
            webView.evaluateJavascript(
                "(function() {" +
                "if (window._webhubObserver) {" +
                "  window._webhubObserver.disconnect();" +
                "  window._webhubObserver = null;" +
                "}" +
                "})()", null);
        }
    }

    private String buildScriptFromActions(List<ActionItem> actions) {
        StringBuilder js = new StringBuilder();
        js.append("(function(){");

        for (ActionItem action : actions) {
            if (action == null || action.selector == null || action.selector.isEmpty()) continue;

            String selector = jsString(action.selector);
            if ("hide".equals(action.type)) {
                js.append("try{document.querySelectorAll(").append(selector).append(").forEach(el=>el.style.display='none');}catch(e){}");
            } else if ("click".equals(action.type)) {
                int delay = Math.max(0, action.delay);
                if (delay > 0) {
                    js.append("setTimeout(function(){try{document.querySelectorAll(").append(selector).append(").forEach(el=>el.click());}catch(e){}},").append(delay * 1000).append(");");
                } else {
                    js.append("try{document.querySelectorAll(").append(selector).append(").forEach(el=>el.click());}catch(e){}");
                }
            } else if ("modify".equals(action.type)) {
                js.append("try{document.querySelectorAll(").append(selector).append(").forEach(el=>el.textContent=").append(jsString(action.value)).append(");}catch(e){}");
            }
        }

        js.append("})()");
        return js.toString();
    }

    private static List<ActionItem> parseLegacyActions(String actions) {
        List<ActionItem> items = new ArrayList<>();
        if (actions == null || actions.isEmpty()) return items;

        String[] actionGroups = actions.split(";");
        for (String group : actionGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;

            String[] parts = group.split("\\|");
            if (parts.length < 2) continue;

            String type = parts[0];
            String selector = parts[1];
            if (selector.startsWith("@")) continue;

            int delay = 0;
            String value = "";
            if ("click".equals(type) && parts.length > 2 && !parts[2].startsWith("@")) {
                try {
                    delay = Integer.parseInt(parts[2]);
                } catch (Exception e) {}
            } else if ("modify".equals(type) && parts.length > 2 && !parts[2].startsWith("@")) {
                value = parts[2];
            }
            items.add(new ActionItem(type, selector, value, delay));
        }
        return items;
    }

    private String jsString(String value) {
        if (value == null) value = "";
        return JSONObject.quote(value);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 关闭底部菜单
            if (isBottomMenuOpen) {
                hideBottomMenu();
                return true;
            }

            // 退出查看模式
            if (isInspectMode) {
                toggleInspectMode();
                return true;
            }

            // 关闭下拉菜单
            if (isDropdownOpen) {
                isDropdownOpen = false;
                updateDropdown();
                return true;
            }

            // 金山文档优化
            boolean kdocsOptimize = prefs.getBoolean("kdocs_optimize", true);
            WebView wv = getCurrentWebView();
            String currentUrl = wv != null ? wv.getUrl() : null;
            if (kdocsOptimize && isKdocsUrl(currentUrl)) {
                tryClosePopup();
                return true;
            }

            // 普通返回逻辑
            if (wv != null && wv.canGoBack()) {
                wv.goBack();
                Toast.makeText(this, "返回上一页", Toast.LENGTH_SHORT).show();
                return true;
            }

            // 没有历史记录，不退出APP
            Toast.makeText(this, "已到第一页", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isKdocsUrl(String url) {
        if (url == null) return false;
        return url.contains("kdocs.cn") || url.contains("wps.cn") || url.contains("wps.com");
    }

    private boolean tryClosePopup() {
        WebView wv = getCurrentWebView();
        if (wv == null) return false;

        float x = wv.getWidth() / 2f;
        float y = 15f;

        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        wv.dispatchTouchEvent(downEvent);
        downEvent.recycle();

        wv.postDelayed(() -> {
            if (wv != null) {
                MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
                wv.dispatchTouchEvent(upEvent);
                upEvent.recycle();
            }
        }, 50);

        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (int i = 0; i < MAX_TABS; i++) {
            if (webViews[i] != null) {
                webViews[i].onPause();
            }
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            autoRefreshRunnable = null;
        }
        for (int i = 0; i < MAX_TABS; i++) {
            if (webViews[i] != null) {
                webViews[i].destroy();
                webViews[i] = null;
            }
        }
    }
}
