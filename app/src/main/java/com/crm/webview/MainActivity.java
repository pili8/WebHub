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
import android.widget.PopupWindow;
import android.widget.ScrollView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.view.ViewGroup;
import android.animation.ObjectAnimator;
import android.animation.AnimatorListenerAdapter;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_SETTINGS = 200;
    private static final int MAX_TABS = 6;

    // 工作区自定义颜色（默认预设）
    private static final String[] DEFAULT_TAB_COLORS = {
        "#1976D2", "#4CAF50", "#FF9800", "#9C27B0", "#F44336", "#00BCD4"
    };
    // 预设颜色列表（供选择）
    private static final String[] PRESET_COLORS = {
        "#1976D2", "#4CAF50", "#FF9800", "#9C27B0", "#F44336", "#00BCD4",
        "#E91E63", "#607D8B", "#795548", "#FF5722"
    };

    // WebView 容器和工作区容器
    private FrameLayout webViewContainer;
    private LinearLayout tabContainer;
    private LinearLayout bottomMenuContainer;

    // 多 WebView（每个工作区独立）
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

    // 定时刷新相关（按工作区配置）
    private android.os.Handler autoRefreshHandler = new android.os.Handler();
    private Runnable autoRefreshRunnable;
    private int autoRefreshInterval = 0; // 当前工作区的刷新间隔（兼容旧逻辑）
    private int[] tabAutoRefresh = new int[MAX_TABS]; // 每个工作区独立的刷新间隔
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
    private String[] tabColors = new String[MAX_TABS]; // 工作区自定义颜色
    private List<List<LinkItem>> tabLinks = new ArrayList<>();

    // 设置变更标志（Feature 7）
    private boolean settingsChanged = false;

    // 进度条覆盖层（Feature 8）
    private View progressOverlay;

    // 回到主页浮动按钮（Feature 4）
    private TextView homeButton;
    private boolean isHomeButtonVisible = false;

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

        // 从设置页面返回时，检查是否需要重载（Feature 7）
        isNightMode = prefs.getBoolean("night_mode", false);
        isNightModeCSS = prefs.getBoolean("night_mode_css", false);
        applyAppNightMode();

        // 只在设置变更时才重新加载配置和重建UI
        if (settingsChanged) {
            settingsChanged = false;

            // 重新加载按工作区刷新配置
            loadTabAutoRefresh();
            // 重新启动定时器
            if (autoRefreshRunnable != null) {
                autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            }
            autoRefreshInterval = tabAutoRefresh[currentTab];
            if (autoRefreshInterval > 0) {
                setAutoRefresh(autoRefreshInterval);
            }
            updateAutoRefreshIndicator();

            // 检查工作区数量是否变化
            int oldTabCount = tabCount;
            loadConfig();

            // 如果工作区数量变化，重新创建UI
            if (tabCount != oldTabCount) {
                createTabsAndWebViews();
                switchTab(0);
            } else {
                // 工作区数量没变，只刷新当前页面操作
                wv = getCurrentWebView();
                if (wv != null) {
                    stopMutationObserver(wv);
                    executeCustomScript(wv);
                }
            }
        } else {
            // 设置没变，只恢复定时刷新状态
            autoRefreshInterval = tabAutoRefresh[currentTab];
            updateAutoRefreshIndicator();

            // 重新执行页面操作（APP从后台返回时WebView可能重新加载）
            wv = getCurrentWebView();
            if (wv != null) {
                stopMutationObserver(wv);
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

        // 定时刷新（按工作区配置）
        autoRefreshDot = findViewById(R.id.autoRefreshDot);
        loadTabAutoRefresh();

        // 进度条覆盖层（Feature 8）
        initProgressOverlay();

        // 回到主页浮动按钮（Feature 4）
        initHomeButton();
    }

    /** 初始化进度条覆盖层（Feature 8）*/
    private void initProgressOverlay() {
        // 在 toolbar 下方添加进度条（不遮挡按钮）
        progressOverlay = new View(this);
        progressOverlay.setBackgroundColor(Color.parseColor("#42A5F5"));
        progressOverlay.setPivotX(0); // 从左向右增长
        progressOverlay.setVisibility(View.GONE);

        // 插入到 toolbar 之后
        LinearLayout toolbar = findViewById(R.id.toolbar);
        if (toolbar != null && toolbar.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) toolbar.getParent();
            int toolbarIndex = parent.indexOfChild(toolbar);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(3));
            progressOverlay.setLayoutParams(lp);
            parent.addView(progressOverlay, toolbarIndex + 1);
        }
    }

    /** 初始化回到主页浮动按钮（Feature 4）*/
    private void initHomeButton() {
        homeButton = new TextView(this);
        homeButton.setText("🏠 回到主页");
        homeButton.setTextSize(12);
        homeButton.setTextColor(Color.WHITE);
        homeButton.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1976D2"));
        bg.setCornerRadius(dpToPx(20));
        homeButton.setBackground(bg);
        homeButton.setElevation(dpToPx(6));
        homeButton.setVisibility(View.GONE);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.bottomMargin = dpToPx(16);
        lp.rightMargin = dpToPx(16);
        homeButton.setLayoutParams(lp);

        homeButton.setOnClickListener(v -> {
            loadCurrentLink();
            hideHomeButton();
            Toast.makeText(this, "已回到主页", Toast.LENGTH_SHORT).show();
        });

        webViewContainer.addView(homeButton);
    }

    /** 显示回到主页按钮 */
    private void showHomeButton() {
        if (!isHomeButtonVisible && homeButton != null) {
            isHomeButtonVisible = true;
            homeButton.setVisibility(View.VISIBLE);
            homeButton.setAlpha(0f);
            homeButton.animate().alpha(1f).setDuration(200).start();
        }
    }

    /** 隐藏回到主页按钮 */
    private void hideHomeButton() {
        if (isHomeButtonVisible && homeButton != null) {
            isHomeButtonVisible = false;
            homeButton.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                homeButton.setVisibility(View.GONE);
            }).start();
        }
    }

    /** 加载每个工作区的刷新间隔配置（Feature 5）*/
    private void loadTabAutoRefresh() {
        for (int i = 0; i < MAX_TABS; i++) {
            tabAutoRefresh[i] = prefs.getInt("auto_refresh_tab_" + i, 0);
        }
        // 兼容旧的全局配置
        int oldGlobal = prefs.getInt("auto_refresh_interval", 0);
        if (oldGlobal > 0) {
            // 迁移旧配置到第一个工作区
            boolean anySet = false;
            for (int i = 0; i < MAX_TABS; i++) {
                if (tabAutoRefresh[i] > 0) { anySet = true; break; }
            }
            if (!anySet) {
                tabAutoRefresh[0] = oldGlobal;
                prefs.edit().putInt("auto_refresh_tab_0", oldGlobal).remove("auto_refresh_interval").apply();
            }
        }
        autoRefreshInterval = tabAutoRefresh[currentTab];
        updateAutoRefreshIndicator();
    }

    /** 保存指定工作区的刷新间隔 */
    private void saveTabAutoRefresh(int tabIndex, int interval) {
        tabAutoRefresh[tabIndex] = interval;
        prefs.edit().putInt("auto_refresh_tab_" + tabIndex, interval).apply();
    }

    private void loadConfig() {
        tabCount = prefs.getInt("tab_count", 3);
        if (tabCount < 2) tabCount = 2;
        if (tabCount > MAX_TABS) tabCount = MAX_TABS;

        String[] defaultIcons = {"📊", "📋", "➕", "📁", "👤", "📌"};
        String[] defaultTitles = {"工作区1", "工作区2", "工作区3", "工作区4", "工作区5", "工作区6"};
        String[] defaultUrls = {
                "about:blank",
                "about:blank",
                "about:blank",
                "about:blank",
                "about:blank",
                "about:blank"
        };

        // 初始化默认颜色
        for (int i = 0; i < MAX_TABS; i++) {
            tabColors[i] = DEFAULT_TAB_COLORS[i % DEFAULT_TAB_COLORS.length];
        }

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
                tabColors[i] = tab.optString("color", DEFAULT_TAB_COLORS[i % DEFAULT_TAB_COLORS.length]);
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

            // 创建工作区
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            tab.setLayoutParams(tabParams);

            // 工作区图标（带自定义颜色）
            TextView icon = new TextView(this);
            icon.setText(tabIcons[i]);
            icon.setTextSize(22);
            icon.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            icon.setLayoutParams(iconParams);

            // 工作区图标颜色
            icon.setTextColor(i == 0 ? Color.parseColor("#1976D2") : Color.parseColor("#666666"));

            // 工作区文字
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

            // 点击切换工作区
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

        // 长按标题栏显示浏览历史
        tvTitle.setOnLongClickListener(v -> {
            showHistoryDialog();
            return true;
        });
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
        boolean dark = isNightMode;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = dpToPx(8);
        int padV = dpToPx(6);
        root.setPadding(padH, padV, padH, padV);
        root.setBackgroundColor(dark ? Color.parseColor("#2A2A2A") : Color.WHITE);

        String[][] items = {
            {"🔍", "搜索"},
            {"📋", "复制链接"},
            {isNightMode ? "☀️" : "🌙", isNightMode ? "日间模式" : "夜间模式"},
            {"⚙️", "设置"},
            {"⏰", tabAutoRefresh[currentTab] > 0 ? "刷新中" : "定时刷新"},
            {"🎯", isInspectMode ? "退出查找" : "查找元素"},
            {"📊", "内存占用"},
            {"🚪", "退出"}
        };

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            row.setBackgroundResource(outValue.resourceId);

            TextView icon = new TextView(this);
            icon.setText(items[i][0]);
            icon.setTextSize(18);
            icon.setPadding(0, 0, dpToPx(12), 0);

            TextView label = new TextView(this);
            label.setText(items[i][1]);
            label.setTextSize(14);
            label.setTextColor(dark ? Color.parseColor("#E0E0E0") : Color.parseColor("#333333"));
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            label.setLayoutParams(labelLp);

            row.addView(icon);
            row.addView(label);

            if (i == 4) {
                TextView arrow = new TextView(this);
                arrow.setText("▸");
                arrow.setTextSize(12);
                arrow.setTextColor(dark ? Color.parseColor("#888888") : Color.parseColor("#999999"));
                row.addView(arrow);
            }

            root.addView(row);

            if (i < items.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(dark ? Color.parseColor("#3A3A3A") : Color.parseColor("#F0F0F0"));
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divLp.leftMargin = dpToPx(46);
                divider.setLayoutParams(divLp);
                root.addView(divider);
            }
        }

        PopupWindow popup = new PopupWindow(root,
                dpToPx(200), LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(dpToPx(8));
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                dark ? Color.parseColor("#2A2A2A") : Color.WHITE));
        popup.setOutsideTouchable(true);

        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof LinearLayout) {
                final int index = i / 2;
                child.setOnClickListener(v -> {
                    popup.dismiss();
                    switch (index) {
                        case 0: toggleSearch(); break;
                        case 1: copyCurrentUrl(); break;
                        case 2: toggleNightMode(); break;
                        case 3:
                            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                            intent.putExtra("night_mode", isNightMode);
                            startActivityForResult(intent, REQUEST_SETTINGS);
                            break;
                        case 4: showAutoRefreshPicker(); break;
                        case 5: toggleInspectMode(); break;
                        case 6: showMemoryInfo(); break;
                        case 7: finish(); break;
                    }
                });
            }
        }

        popup.showAsDropDown(btnMenu, -dpToPx(156), dpToPx(4));
    }

    private void showAutoRefreshPicker() {
        boolean dark = isNightMode;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = dpToPx(8);
        int padV = dpToPx(6);
        root.setPadding(padH, padV, padH, padV);
        root.setBackgroundColor(dark ? Color.parseColor("#2A2A2A") : Color.WHITE);

        // 显示当前工作区名称
        String wsName = (currentTab < tabTitles.length && tabTitles[currentTab] != null) ? tabTitles[currentTab] : "工作区" + (currentTab + 1);
        TextView title = new TextView(this);
        title.setText("定时刷新 - " + wsName);
        title.setTextSize(13);
        title.setTextColor(dark ? Color.parseColor("#AAAAAA") : Color.parseColor("#999999"));
        title.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        root.addView(title);

        View titleDivider = new View(this);
        titleDivider.setBackgroundColor(dark ? Color.parseColor("#3A3A3A") : Color.parseColor("#F0F0F0"));
        titleDivider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(titleDivider);

        String[] labels = {"关闭", "每30秒", "每1分钟", "每5分钟"};
        int[] intervals = {0, 30, 60, 300};
        int currentInterval = tabAutoRefresh[currentTab];

        for (int i = 0; i < labels.length; i++) {
            final int interval = intervals[i];
            boolean checked = currentInterval == interval;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            row.setBackgroundResource(outValue.resourceId);

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(14);
            label.setTextColor(dark ? Color.parseColor("#E0E0E0") : Color.parseColor("#333333"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            label.setLayoutParams(lp);
            row.addView(label);

            if (checked) {
                TextView check = new TextView(this);
                check.setText("✓");
                check.setTextSize(16);
                check.setTextColor(Color.parseColor("#1976D2"));
                row.addView(check);
            }

            root.addView(row);

            if (i < labels.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(dark ? Color.parseColor("#3A3A3A") : Color.parseColor("#F0F0F0"));
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divLp.leftMargin = dpToPx(14);
                divider.setLayoutParams(divLp);
                root.addView(divider);
            }
        }

        PopupWindow popup = new PopupWindow(root,
                dpToPx(200), LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(dpToPx(8));
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                dark ? Color.parseColor("#2A2A2A") : Color.WHITE));
        popup.setOutsideTouchable(true);

        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof LinearLayout && child != root) {
                final int interval = intervals[i / 2];
                child.setOnClickListener(v -> {
                    popup.dismiss();
                    // 保存当前工作区的刷新间隔
                    saveTabAutoRefresh(currentTab, interval);
                    // 更新当前运行的定时器
                    setAutoRefresh(interval);
                });
            }
        }

        popup.showAsDropDown(btnMenu, -dpToPx(132), dpToPx(4));
    }


    // ========== 内存占用 ==========

    private void showMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();

        // 应用内存（JVM）
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        int appPercent = (int) (usedMemory * 100 / maxMemory);

        // 系统内存
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(memInfo);
        long totalSys = memInfo.totalMem;
        long availSys = memInfo.availMem;
        long usedSys = totalSys - availSys;
        int sysPercent = (int) (usedSys * 100 / totalSys);

        // 工作区状态
        int activeCount = 0;
        int loadedCount = 0;
        for (int i = 0; i < MAX_TABS; i++) {
            if (webViews[i] != null) {
                activeCount++;
                if (tabLoaded[i]) loadedCount++;
            }
        }

        // 构建自定义视图
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        boolean dark = isNightMode;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(dark ? Color.parseColor("#1E1E1E") : Color.WHITE);

        // 圆环区域
        LinearLayout rings = new LinearLayout(this);
        rings.setOrientation(LinearLayout.HORIZONTAL);
        rings.setGravity(android.view.Gravity.CENTER);

        // 应用内存圆环
        LinearLayout appRing = createRingView("应用", appPercent,
                formatBytes(usedMemory) + " / " + formatBytes(maxMemory), density);
        rings.addView(appRing);

        // 间距
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams((int) (30 * density), 1));
        rings.addView(spacer);

        // 系统内存圆环
        LinearLayout sysRing = createRingView("系统", sysPercent,
                formatBytes(usedSys) + " / " + formatBytes(totalSys), density);
        rings.addView(sysRing);

        root.addView(rings);

        // 工作区标题
        int titleTopPad = (int) (16 * density);
        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(1, titleTopPad));
        root.addView(spacer2);

        TextView tvWsTitle = new TextView(this);
        tvWsTitle.setText("工作区");
        tvWsTitle.setTextSize(13);
        tvWsTitle.setTextColor(dark ? Color.parseColor("#888888") : Color.parseColor("#999999"));
        tvWsTitle.setPadding(0, 0, 0, (int) (6 * density));
        root.addView(tvWsTitle);

        // 工作区进度条
        LinearLayout barBg = new LinearLayout(this);
        barBg.setOrientation(LinearLayout.HORIZONTAL);
        barBg.setBackgroundColor(dark ? Color.parseColor("#333333") : Color.parseColor("#EEEEEE"));
        barBg.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (6 * density)));

        View barFill = new View(this);
        barFill.setBackgroundColor(Color.parseColor("#1976D2"));
        float ratio = tabCount > 0 ? (float) loadedCount / tabCount : 0;
        barFill.setLayoutParams(new LinearLayout.LayoutParams(
                0, (int) (6 * density), ratio));
        barBg.addView(barFill);

        if (loadedCount < activeCount) {
            View barCreated = new View(this);
            barCreated.setBackgroundColor(Color.parseColor("#90CAF9"));
            float ratioCreated = tabCount > 0 ? (float) (activeCount - loadedCount) / tabCount : 0;
            barCreated.setLayoutParams(new LinearLayout.LayoutParams(
                    0, (int) (6 * density), ratioCreated));
            barBg.addView(barCreated);
        }

        root.addView(barBg);

        // 工作区详情列表
        LinearLayout wsList = new LinearLayout(this);
        wsList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wsListParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wsListParams.topMargin = (int) (8 * density);
        wsList.setLayoutParams(wsListParams);

        for (int i = 0; i < tabCount; i++) {
            String title = (i < tabTitles.length && tabTitles[i] != null) ? tabTitles[i] : "工作区" + (i + 1);
            boolean exists = webViews[i] != null;
            boolean loaded = tabLoaded[i];
            int linkCount = (i < tabLinks.size()) ? tabLinks.get(i).size() : 0;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, (int) (3 * density), 0, (int) (3 * density));

            // 状态指示点
            View dot = new View(this);
            int dotSize = (int) (8 * density);
            dot.setLayoutParams(new LinearLayout.LayoutParams(dotSize, dotSize));
            dot.setBackgroundResource(exists ? (loaded ? R.drawable.dot_green : R.drawable.dot_orange) : R.drawable.dot_gray);
            row.addView(dot);

            // 名称
            TextView tvName = new TextView(this);
            tvName.setText(title);
            tvName.setTextSize(13);
            tvName.setTextColor(dark ? Color.parseColor(exists ? "#E0E0E0" : "#666666") : Color.parseColor(exists ? "#333333" : "#BBBBBB"));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            nameParams.leftMargin = (int) (8 * density);
            tvName.setLayoutParams(nameParams);
            row.addView(tvName);

            // 链接数
            TextView tvLinks = new TextView(this);
            tvLinks.setText(linkCount + " 链接");
            tvLinks.setTextSize(11);
            tvLinks.setTextColor(dark ? Color.parseColor("#777777") : Color.parseColor("#999999"));
            tvLinks.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
            row.addView(tvLinks);

            // 状态标签
            TextView tvStatus = new TextView(this);
            tvStatus.setTextSize(11);
            if (!exists) {
                tvStatus.setText("未创建");
                tvStatus.setTextColor(dark ? Color.parseColor("#555555") : Color.parseColor("#BBBBBB"));
            } else if (loaded) {
                tvStatus.setText("已加载");
                tvStatus.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                tvStatus.setText("已创建");
                tvStatus.setTextColor(Color.parseColor("#FF9800"));
            }
            row.addView(tvStatus);

            wsList.addView(row);
        }
        root.addView(wsList);

        // 底部汇总
        TextView tvSummary = new TextView(this);
        tvSummary.setText(loadedCount + " 已加载 / " + activeCount + " 已创建");
        tvSummary.setTextSize(12);
        tvSummary.setTextColor(dark ? Color.parseColor("#777777") : Color.parseColor("#999999"));
        tvSummary.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = (int) (8 * density);
        tvSummary.setLayoutParams(summaryParams);
        root.addView(tvSummary);

        AlertDialog memDialog = new AlertDialog.Builder(this)
                .setTitle("📊 内存占用")
                .setView(root)
                .setPositiveButton("确定", null)
                .create();
        if (dark) {
            memDialog.getWindow().getDecorView().setBackgroundColor(Color.parseColor("#1E1E1E"));
        }
        memDialog.show();
    }

    private LinearLayout createRingView(String label, int percent, String detail, float density) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        int size = (int) (100 * density);
        layout.setLayoutParams(new LinearLayout.LayoutParams(size, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 自定义圆环视图
        View ringView = new View(this) {
            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();
                float strokeWidth = 8 * density;
                float radius = (Math.min(w, h) - strokeWidth) / 2f;
                float cx = w / 2f;
                float cy = h / 2f;

                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setAntiAlias(true);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);

                // 背景圆环
                paint.setColor(Color.parseColor("#E0E0E0"));
                canvas.drawCircle(cx, cy, radius, paint);

                // 进度圆环
                int color = percent < 60 ? Color.parseColor("#4CAF50") :
                           percent < 85 ? Color.parseColor("#FF9800") :
                           Color.parseColor("#F44336");
                paint.setColor(color);
                float sweepAngle = 360f * percent / 100f;
                canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                        -90, sweepAngle, false, paint);

                // 中间百分比文字
                android.graphics.Paint textPaint = new android.graphics.Paint();
                textPaint.setAntiAlias(true);
                textPaint.setTextSize(16 * density);
                textPaint.setColor(color);
                textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                android.graphics.Rect bounds = new android.graphics.Rect();
                textPaint.getTextBounds(percent + "%", 0, (percent + "%").length(), bounds);
                canvas.drawText(percent + "%", cx, cy + bounds.height() / 2f, textPaint);
            }
        };
        ringView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        layout.addView(ringView);

        // 标签
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(Color.parseColor("#666666"));
        tvLabel.setGravity(android.view.Gravity.CENTER);
        layout.addView(tvLabel);

        // 详情
        TextView tvDetail = new TextView(this);
        tvDetail.setText(detail);
        tvDetail.setTextSize(10);
        tvDetail.setTextColor(Color.parseColor("#999999"));
        tvDetail.setGravity(android.view.Gravity.CENTER);
        layout.addView(tvDetail);

        return layout;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        else return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
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

        // 停止之前的定时器
        if (autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        }

        if (interval > 0) {
            // 启动定时刷新（只刷新当前工作区）
            autoRefreshRunnable = () -> {
                WebView wv = getCurrentWebView();
                if (wv != null) {
                    wv.reload();
                }
                autoRefreshHandler.postDelayed(autoRefreshRunnable, interval * 1000L);
            };
            autoRefreshHandler.postDelayed(autoRefreshRunnable, interval * 1000L);
            Toast.makeText(this, "定时刷新: " + formatInterval(interval) + " (当前工作区)", Toast.LENGTH_SHORT).show();
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
            // 底部工作区变暗
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

        // 如果点击的是当前工作区，切换显示/隐藏
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

        // 更新工作区样式
        for (int i = 0; i < tabTextViews.size(); i++) {
            tabTextViews.get(i).setTextColor(i == tabIndex ? Color.parseColor("#1976D2") : Color.parseColor("#666666"));
            tabIconViews.get(i).setTextColor(i == tabIndex ? Color.parseColor("#1976D2") : Color.parseColor("#666666"));
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

        // 切换工作区时，更新定时刷新状态（Feature 5）
        autoRefreshInterval = tabAutoRefresh[index];
        if (autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        }
        if (autoRefreshInterval > 0) {
            autoRefreshRunnable = () -> {
                WebView wv = getCurrentWebView();
                if (wv != null) {
                    wv.reload();
                }
                autoRefreshHandler.postDelayed(autoRefreshRunnable, autoRefreshInterval * 1000L);
            };
            autoRefreshHandler.postDelayed(autoRefreshRunnable, autoRefreshInterval * 1000L);
        }
        updateAutoRefreshIndicator();

        // 隐藏回到主页按钮
        hideHomeButton();

        // 切换工作区样式
        for (int i = 0; i < tabTextViews.size(); i++) {
            tabTextViews.get(i).setTextColor(i == index ? Color.parseColor("#1976D2") : Color.parseColor("#666666"));
            tabIconViews.get(i).setTextColor(i == index ? Color.parseColor("#1976D2") : Color.parseColor("#666666"));
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
        String savedUA = prefs.getString("user_agent", "");
        if (!savedUA.isEmpty()) {
            settings.setUserAgentString(savedUA);
        } else {
            settings.setUserAgentString(settings.getUserAgentString().replace("; wv", ""));
        }
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setGeolocationEnabled(true);

        // 注册 JS 接口，用于 SPA 导航回调
        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void onSpaNavigate() {
                runOnUiThread(() -> {
                    // 停止旧的 Observer
                    stopMutationObserver(webView);
                    // 重新执行页面操作（带轮询）
                    executeCustomScript(webView);
                });
            }
        }, "_webhub");

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
                    // 显示进度覆盖层（Feature 8）
                    if (progressOverlay != null) {
                        progressOverlay.setVisibility(View.VISIBLE);
                        progressOverlay.setScaleX(0f);
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                // 进度覆盖层动画完成（Feature 8）
                if (progressOverlay != null && webView != null && view == webView) {
                    progressOverlay.animate()
                            .scaleX(1f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                progressOverlay.animate()
                                        .alpha(0f)
                                        .setDuration(300)
                                        .setStartDelay(100)
                                        .withEndAction(() -> {
                                            progressOverlay.setVisibility(View.GONE);
                                            progressOverlay.setAlpha(1f);
                                            progressOverlay.setScaleX(0f);
                                        })
                                        .start();
                            })
                            .start();
                }
                // 检查是否离开配置域名，显示/隐藏回到主页按钮（Feature 4）
                checkDomainAndShowHomeButton(url);
                // 执行自定义操作
                executeCustomScript(view);
                // 夜间模式 CSS
                if (isNightMode && isNightModeCSS) {
                    injectNightModeCSS(view);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // 只处理主帧加载错误（Feature 3）
                if (request.isForMainFrame()) {
                    showErrorPage(view, error.getDescription() != null ? error.getDescription().toString() : "页面加载失败");
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                // 只处理主帧 HTTP 错误（Feature 3）
                if (request.isForMainFrame() && errorResponse != null) {
                    int statusCode = errorResponse.getStatusCode();
                    if (statusCode >= 400) {
                        showErrorPage(view, "HTTP 错误: " + statusCode);
                    }
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
                    // 更新进度覆盖层（Feature 8）
                    if (progressOverlay != null && progressOverlay.getVisibility() == View.VISIBLE) {
                        progressOverlay.setScaleX(newProgress / 100f);
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
                "  var prev=document.getElementById('wh-inspect-hl');if(prev){prev.style.outline=prev.getAttribute('wh-old-outline')||'';prev.removeAttribute('id');}" +
                "  el.setAttribute('wh-old-outline',el.style.outline||'');el.id='wh-inspect-hl';" +
                "  el.style.outline = '3px solid #FF5722';" +
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

        // 退出查找按钮
        TextView btnExitInspect = dialogView.findViewById(R.id.btnExitInspect);
        if (btnExitInspect != null) {
            btnExitInspect.setVisibility(isInspectMode ? View.VISIBLE : View.GONE);
            btnExitInspect.setOnClickListener(v -> {
                dialog.dismiss();
                toggleInspectMode();
            });
        }

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
        } else if (requestCode == REQUEST_SETTINGS) {
            // 从设置页面返回（Feature 7）
            if (resultCode == RESULT_OK && data != null && data.getBooleanExtra("settings_changed", false)) {
                settingsChanged = true;
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
        executeCustomScriptWithRetry(webView, 0);
    }

    private void executeCustomScriptWithRetry(WebView webView, int attempt) {
        // 检查页面操作开关
        boolean pageActionsEnabled = prefs.getBoolean("page_actions_enabled", true);
        if (!pageActionsEnabled) return;

        // 获取当前页面 URL
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.isEmpty() || "about:blank".equals(currentUrl)) return;

        // 获取当前链接索引
        int linkIndex = activeLinkIndex >= 0 ? activeLinkIndex : currentLinkIndex;

        // 收集所有匹配的操作（scope 语义：每个链接的 scope 决定它要应用到哪些页面）
        List<ActionItem> allActions = new ArrayList<>();

        for (int tabIndex = 0; tabIndex < tabLinks.size(); tabIndex++) {
            for (LinkItem link : tabLinks.get(tabIndex)) {
                if (!hasActions(link)) continue;
                if (isLinkMatchesPage(link, tabIndex, linkIndex, currentUrl)) {
                    collectActions(link, allActions);
                }
            }
        }

        if (allActions.isEmpty()) return;

        String js = buildScriptFromActions(allActions);
        if (js.isEmpty()) return;

        // 检查目标元素是否已存在
        String checkJs = buildElementCheckJs(allActions);
        webView.evaluateJavascript(checkJs, value -> {
            boolean found = "true".equals(value);

            if (found) {
                // 元素已存在，立即执行
                runActionScript(webView, js);
            } else if (attempt < 20) {
                // 元素还没出现，500ms 后重试
                webView.postDelayed(() -> executeCustomScriptWithRetry(webView, attempt + 1), 500);
            } else {
                // 超时，最后一次尝试
                runActionScript(webView, js);
            }
        });
    }

    /**
     * 判断某个链接的操作是否应该应用到当前页面
     * scope 语义：该链接的操作要应用到哪些页面
     */
    private boolean isLinkMatchesPage(LinkItem link, int tabIndex, int currentLinkIndex, String currentUrl) {
        String scope = link.scope;

        if ("all".equals(scope)) {
            // 所有工作区 → 无条件应用
            return true;
        } else if ("tab".equals(scope)) {
            // 当前工作区 → 只要在同一个工作区就应用
            return tabIndex == currentTab;
        } else if ("domain".equals(scope)) {
            // 相似域名 → 域名匹配就应用
            String linkDomain = getDomain(link.url);
            String pageDomain = getDomain(currentUrl);
            return !linkDomain.isEmpty() && linkDomain.equals(pageDomain);
        } else {
            // 仅此链接 → 只有当前链接才应用
            return tabIndex == currentTab && link == getCurrentLink();
        }
    }

    /** 获取当前正在查看的链接 */
    private LinkItem getCurrentLink() {
        if (currentTab >= 0 && currentTab < tabLinks.size()) {
            List<LinkItem> links = tabLinks.get(currentTab);
            int idx = activeLinkIndex >= 0 ? activeLinkIndex : currentLinkIndex;
            if (idx >= 0 && idx < links.size()) {
                return links.get(idx);
            }
        }
        return null;
    }

    /** 构建检测元素是否存在的 JS */
    private String buildElementCheckJs(List<ActionItem> actions) {
        StringBuilder js = new StringBuilder("(function(){");
        boolean hasSelectors = false;
        for (ActionItem action : actions) {
            if ("script".equals(action.type)) continue; // 脚本类型不需要检测选择器
            if (action.selector == null || action.selector.isEmpty()) continue;
            js.append("if(document.querySelectorAll(").append(jsString(action.selector)).append(").length>0)return true;");
            hasSelectors = true;
        }
        if (!hasSelectors) {
            // 如果所有操作都是脚本类型，直接返回true
            js.append("return true;");
            js.append("})()");
            return js.toString();
        }
        // 检查 iframe 内部
        js.append("try{var iframes=document.querySelectorAll('iframe');for(var i=0;i<iframes.length;i++){try{var doc=iframes[i].contentDocument||iframes[i].contentWindow.document;");
        for (ActionItem action : actions) {
            if ("script".equals(action.type)) continue;
            if (action.selector == null || action.selector.isEmpty()) continue;
            js.append("if(doc.querySelectorAll(").append(jsString(action.selector)).append(").length>0)return true;");
        }
        js.append("}catch(e){}}}catch(e){}");
        js.append("return false;})()");
        return js.toString();
    }

    /** 执行操作脚本（主文档 + iframe） */
    private void runActionScript(WebView webView, String mainJs) {
        // 在主文档执行
        webView.evaluateJavascript(mainJs, null);

        // 在所有可访问的 iframe 中执行
        String iframeJs = "(function(){" +
                "try{" +
                "var iframes=document.querySelectorAll('iframe');" +
                "for(var i=0;i<iframes.length;i++){" +
                "  try{" +
                "    var doc=iframes[i].contentDocument||iframes[i].contentWindow.document;" +
                "    if(doc)" + mainJs +
                "  }catch(e){}" +
                "}" +
                "}catch(e){}" +
                "})()";
        webView.evaluateJavascript(iframeJs, null);

        // 启动 MutationObserver
        startMutationObserver(webView, mainJs);
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
        // MutationObserver + SPA 导航监听
        String observerJs = "(function() {" +
                "if (window._webhubObserver) return;" +
                "var timeout = null;" +
                "var actionFn = function() {" +
                "  try { " + actionJs + " } catch(e) {}" +
                // 同时在 iframe 中执行
                "  try{" +
                "    var iframes=document.querySelectorAll('iframe');" +
                "    for(var i=0;i<iframes.length;i++){" +
                "      try{var doc=iframes[i].contentDocument||iframes[i].contentWindow.document;if(doc)" + actionJs + "}catch(e){}" +
                "    }" +
                "  }catch(e){}" +
                "};" +
                // MutationObserver，1000ms 去抖
                "window._webhubObserver = new MutationObserver(function(mutations) {" +
                "  if (timeout) clearTimeout(timeout);" +
                "  timeout = setTimeout(actionFn, 1000);" +
                "});" +
                "if (document.body) {" +
                "  window._webhubObserver.observe(document.body, {" +
                "    childList: true," +
                "    subtree: true," +
                "    characterData: true" +
                "  });" +
                "  actionFn();" +
                "}" +
                // SPA 导航监听：劫持 pushState/replaceState + popstate
                "if (!window._webhubNavHooked) {" +
                "  window._webhubNavHooked = true;" +
                "  var origPush = history.pushState;" +
                "  var origReplace = history.replaceState;" +
                "  var navTimeout = null;" +
                "  var notifyNav = function() {" +
                "    if (navTimeout) clearTimeout(navTimeout);" +
                "    navTimeout = setTimeout(function() {" +
                "      try { _webhub.onSpaNavigate(); } catch(e) { actionFn(); }" +
                "    }, 1500);" +
                "  };" +
                "  history.pushState = function() {" +
                "    origPush.apply(this, arguments);" +
                "    notifyNav();" +
                "  };" +
                "  history.replaceState = function() {" +
                "    origReplace.apply(this, arguments);" +
                "    notifyNav();" +
                "  };" +
                "  window.addEventListener('popstate', function() {" +
                "    notifyNav();" +
                "  });" +
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
            if (action == null) continue;

            // 自定义脚本类型（Feature 6）：直接执行JS代码，selector可为空
            if ("script".equals(action.type)) {
                String script = action.value;
                if (script != null && !script.isEmpty()) {
                    int delay = Math.max(0, action.delay);
                    if (delay > 0) {
                        js.append("setTimeout(function(){try{").append(script).append("}catch(e){}},").append(delay * 1000).append(");");
                    } else {
                        js.append("try{").append(script).append("}catch(e){}");
                    }
                }
                continue;
            }

            if (action.selector == null || action.selector.isEmpty()) continue;

            String selector = jsString(action.selector);
            if ("hide".equals(action.type)) {
                js.append("try{document.querySelectorAll(").append(selector).append(").forEach(el=>el.style.setProperty('display','none','important'));}catch(e){}");
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

    /** 显示错误页面（Feature 3）*/
    private void showErrorPage(WebView webView, String errorMsg) {
        String escapedMsg = errorMsg.replace("\'", "\\'").replace("\"", "\\\"");
        String errorHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<style>" +
                "body{margin:0;padding:40px 20px;font-family:-apple-system,sans-serif;text-align:center;background:#f5f5f5;color:#333;}" +
                ".icon{font-size:64px;margin-bottom:16px;}" +
                ".title{font-size:18px;font-weight:bold;margin-bottom:8px;}" +
                ".msg{font-size:14px;color:#666;margin-bottom:24px;}" +
                ".btn{display:inline-block;padding:12px 32px;background:#1976D2;color:#fff;border:none;border-radius:6px;font-size:15px;cursor:pointer;}" +
                ".btn:active{background:#1565C0;}" +
                "</style></head><body>" +
                "<div class=\"icon\">⚠️</div>" +
                "<div class=\"title\">页面加载失败</div>" +
                "<div class=\"msg\">" + escapedMsg + "</div>" +
                "<button class=\"btn\" onclick=\"location.reload()\">🔄 重新加载</button>" +
                "</body></html>";
        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null);
    }

    /** 检查当前URL是否属于配置的域名，不属于则显示回到主页按钮（Feature 4）*/
    private void checkDomainAndShowHomeButton(String currentUrl) {
        if (currentUrl == null || currentUrl.isEmpty() || "about:blank".equals(currentUrl)) {
            hideHomeButton();
            return;
        }

        // 获取当前工作区配置的链接 URL
        if (currentTab >= 0 && currentTab < tabLinks.size()) {
            List<LinkItem> links = tabLinks.get(currentTab);
            int idx = activeLinkIndex >= 0 ? activeLinkIndex : currentLinkIndex;
            if (idx >= 0 && idx < links.size()) {
                String configUrl = links.get(idx).url;
                // 只要当前URL不是配置的URL，就显示回到主页按钮
                if (configUrl != null && !configUrl.isEmpty() && !configUrl.equals(currentUrl)) {
                    showHomeButton();
                    return;
                }
            }
        }
        hideHomeButton();
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





    /** 显示浏览历史对话框（Feature 2）*/
    private void showHistoryDialog() {
        WebView wv = getCurrentWebView();
        if (wv == null) return;

        android.webkit.WebBackForwardList history = wv.copyBackForwardList();
        if (history == null || history.getSize() == 0) {
            Toast.makeText(this, "没有浏览历史", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean dark = isNightMode;
        int currentIdx = history.getCurrentIndex();
        int size = history.getSize();

        // 构建历史列表视图
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? Color.parseColor("#1E1E1E") : Color.WHITE);
        root.setPadding(0, dpToPx(12), 0, dpToPx(12));

        // 标题
        TextView titleView = new TextView(this);
        titleView.setText("浏览历史 (" + size + " 条)");
        titleView.setTextSize(15);
        titleView.setTextColor(dark ? Color.parseColor("#E0E0E0") : Color.parseColor("#333333"));
        titleView.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(12));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(titleView);

        // 可滚动列表
        ScrollView scrollView = new ScrollView(this);
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        // 从旧到新排列
        for (int i = 0; i < size; i++) {
            final int index = i;
            android.webkit.WebHistoryItem item = history.getItemAtIndex(i);
            if (item == null) continue;

            String pageTitle = item.getTitle();
            String pageUrl = item.getUrl();
            if (pageTitle == null || pageTitle.isEmpty()) pageTitle = pageUrl;
            if (pageTitle == null) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));

            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            row.setBackgroundResource(outValue.resourceId);

            // 当前页标记
            if (i == currentIdx) {
                row.setBackgroundColor(dark ? Color.parseColor("#1A237E") : Color.parseColor("#E3F2FD"));
            }

            // 序号
            TextView numView = new TextView(this);
            numView.setText(String.valueOf(i + 1));
            numView.setTextSize(11);
            numView.setTextColor(dark ? Color.parseColor("#666666") : Color.parseColor("#999999"));
            numView.setPadding(0, 0, dpToPx(10), 0);
            numView.setMinWidth(dpToPx(24));
            numView.setGravity(Gravity.END);
            row.addView(numView);

            // 标题和URL
            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            textCol.setLayoutParams(textColLp);

            TextView titleTv = new TextView(this);
            titleTv.setText(pageTitle);
            titleTv.setTextSize(13);
            titleTv.setTextColor(dark ? Color.parseColor("#E0E0E0") : Color.parseColor("#333333"));
            titleTv.setMaxLines(1);
            titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(titleTv);

            if (pageUrl != null && !pageUrl.equals(pageTitle)) {
                TextView urlTv = new TextView(this);
                urlTv.setText(pageUrl);
                urlTv.setTextSize(11);
                urlTv.setTextColor(dark ? Color.parseColor("#666666") : Color.parseColor("#999999"));
                urlTv.setMaxLines(1);
                urlTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                textCol.addView(urlTv);
            }

            row.addView(textCol);

            // 当前页标记文字
            if (i == currentIdx) {
                TextView curTag = new TextView(this);
                curTag.setText("当前");
                curTag.setTextSize(10);
                curTag.setTextColor(Color.parseColor("#1976D2"));
                curTag.setPadding(dpToPx(8), 0, 0, 0);
                row.addView(curTag);
            }

            row.setOnClickListener(v -> {
                // 跳转到选中的历史条目
                int steps = index - currentIdx;
                if (steps != 0) {
                    wv.goBackOrForward(steps);
                }
            });

            listLayout.addView(row);

            // 分割线
            if (i < size - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(dark ? Color.parseColor("#2A2A2A") : Color.parseColor("#F0F0F0"));
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divLp.leftMargin = dpToPx(50);
                divider.setLayoutParams(divLp);
                listLayout.addView(divider);
            }
        }

        scrollView.addView(listLayout);
        root.addView(scrollView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .setNegativeButton("关闭", null)
                .create();
        if (dark) {
            dialog.getWindow().getDecorView().setBackgroundColor(Color.parseColor("#1E1E1E"));
        }
        dialog.show();
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
