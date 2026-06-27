package com.crm.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
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

import com.crm.webview.config.ConfigManager;
import com.crm.webview.engine.PageActionEngine;
import com.crm.webview.model.AppConfig;
import com.crm.webview.model.AppConfig.ActionItem;
import com.crm.webview.model.AppConfig.LinkItem;
import com.crm.webview.model.AppConfig.SearchResult;
import com.crm.webview.util.AliasManager;
import com.crm.webview.util.UIHelper;
import com.crm.webview.webview.WebViewFactory;

public class MainActivity extends AppCompatActivity implements WebViewFactory.WebViewCallbacks {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_SETTINGS = 200;

    // WebView 容器和工作区容器
    private FrameLayout webViewContainer;
    private LinearLayout tabContainer;
    private LinearLayout bottomMenuContainer;

    // 多 WebView（每个工作区独立）
    private WebView[] webViews = new WebView[AppConfig.MAX_TABS];
    private boolean[] tabLoaded = new boolean[AppConfig.MAX_TABS]; // 是否已加载过
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
    private boolean autoRefreshRunning = false; // 防止重复调度
    private int autoRefreshInterval = 0; // 当前工作区的刷新间隔（兼容旧逻辑）
    private int[] tabAutoRefresh = new int[AppConfig.MAX_TABS]; // 每个工作区独立的刷新间隔
    private View autoRefreshDot;

    // 搜索相关
    private LinearLayout searchBar;
    private EditText etSearch;
    private LinearLayout searchResults;
    private CheckBox cbSearchContent;

    // 配置数据
    private int tabCount = 3;
    private String[] tabIcons = new String[AppConfig.MAX_TABS];
    private String[] tabTitles = new String[AppConfig.MAX_TABS];
    private String[] tabActions = new String[AppConfig.MAX_TABS];
    private String[] tabColors = new String[AppConfig.MAX_TABS]; // 工作区自定义颜色
    private List<List<LinkItem>> tabLinks = new ArrayList<>();

    // 设置变更标志（Feature 7）
    private boolean settingsChanged = false;
    private String lastTabsConfig = ""; // 用于检测配置是否变化

    // 进度条覆盖层（Feature 8）
    private View progressOverlay;

    private SharedPreferences prefs;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 确保启动图标存在（覆盖安装后 PackageManager 状态可能不一致）
        AliasManager.ensureLauncherAlias(getPackageManager(), getPackageName());

        prefs = getSharedPreferences("app_config", MODE_PRIVATE);

        initViews();
        loadConfig();
        createTabsAndWebViews();
        setupListeners();
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
        // 双重检测：settingsChanged 标志 + 配置快照比对（防止标志丢失）
        isNightMode = prefs.getBoolean("night_mode", false);
        isNightModeCSS = prefs.getBoolean("night_mode_css", false);
        applyAppNightMode();

        String currentTabsConfig = prefs.getInt("tab_count", 3) + "|" + prefs.getString("tabs_config", "");
        boolean configChanged = settingsChanged || !currentTabsConfig.equals(lastTabsConfig);

        // 只在设置变更时才重新加载配置和重建UI
        if (configChanged) {
            settingsChanged = false;

            // 重新加载按工作区刷新配置
            tabAutoRefresh = ConfigManager.loadTabAutoRefreshWithMigration(prefs, currentTab);
            // 重新启动定时器
            startAutoRefresh(tabAutoRefresh[currentTab]);

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
                    PageActionEngine.stopMutationObserver(wv);
                    PageActionEngine.executeCustomScript(wv, prefs.getBoolean("page_actions_enabled", true), tabLinks, currentTab, currentLinkIndex, activeLinkIndex, getCurrentLink());
                }
            }
        } else {
            // 设置没变，只恢复定时刷新状态
            autoRefreshInterval = tabAutoRefresh[currentTab];
            updateAutoRefreshIndicator();

            // 重新执行页面操作（APP从后台返回时WebView可能重新加载）
            wv = getCurrentWebView();
            if (wv != null) {
                PageActionEngine.stopMutationObserver(wv);
                PageActionEngine.executeCustomScript(wv, prefs.getBoolean("page_actions_enabled", true), tabLinks, currentTab, currentLinkIndex, activeLinkIndex, getCurrentLink());
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
        tabAutoRefresh = ConfigManager.loadTabAutoRefreshWithMigration(prefs, currentTab);
        autoRefreshInterval = tabAutoRefresh[currentTab];
        updateAutoRefreshIndicator();

        // 进度条覆盖层（Feature 8）
        initProgressOverlay();
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
                    LinearLayout.LayoutParams.MATCH_PARENT, UIHelper.dpToPx(this, 3));
            progressOverlay.setLayoutParams(lp);
            parent.addView(progressOverlay, toolbarIndex + 1);
        }
    }


    private void loadConfig() {
        ConfigManager.ConfigData data = ConfigManager.loadConfig(prefs);
        tabCount = data.tabCount;
        tabIcons = data.tabIcons;
        tabTitles = data.tabTitles;
        tabActions = data.tabActions;
        tabColors = data.tabColors;
        tabLinks = data.tabLinks;

        // 记录配置快照，用于 onResume 检测变化
        lastTabsConfig = ConfigManager.getTabsConfigSnapshot(prefs);
    }


    @SuppressLint("SetJavaScriptEnabled")
    private void createTabsAndWebViews() {
        // 先销毁旧的 WebView（防止内存泄漏）
        for (int i = 0; i < AppConfig.MAX_TABS; i++) {
            if (webViews[i] != null) {
                PageActionEngine.stopMutationObserver(webViews[i]);
                webViews[i].stopLoading();
                webViewContainer.removeView(webViews[i]);
                webViews[i].destroy();
                webViews[i] = null;
            }
        }

        // 清除旧视图
        webViewContainer.removeAllViews();
        tabContainer.removeAllViews();
        tabViews.clear();
        tabIconViews.clear();
        tabTextViews.clear();

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
            textParams.topMargin = UIHelper.dpToPx(this, 2);
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

        WebView webView = WebViewFactory.createWebView(this);
        FrameLayout.LayoutParams wvParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(wvParams);
        webView.setVisibility(index == currentTab ? View.VISIBLE : View.GONE);
        webViewContainer.addView(webView);
        webViews[index] = webView;

        // 设置 WebView
        WebViewFactory.setupWebView(webView, prefs, this, progressBar, progressOverlay);
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
        int padH = UIHelper.dpToPx(this, 8);
        int padV = UIHelper.dpToPx(this, 6);
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
            row.setPadding(UIHelper.dpToPx(this, 14), UIHelper.dpToPx(this, 12), UIHelper.dpToPx(this, 14), UIHelper.dpToPx(this, 12));
            row.setTag(i); // 标记菜单项索引

            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            row.setBackgroundResource(outValue.resourceId);

            TextView icon = new TextView(this);
            icon.setText(items[i][0]);
            icon.setTextSize(18);
            icon.setPadding(0, 0, UIHelper.dpToPx(this, 12), 0);

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
                divLp.leftMargin = UIHelper.dpToPx(this, 46);
                divider.setLayoutParams(divLp);
                root.addView(divider);
            }
        }

        PopupWindow popup = new PopupWindow(root,
                UIHelper.dpToPx(this, 200), LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(UIHelper.dpToPx(this, 8));
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                dark ? Color.parseColor("#2A2A2A") : Color.WHITE));
        popup.setOutsideTouchable(true);

        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof LinearLayout && child.getTag() instanceof Integer) {
                final int index = (int) child.getTag();
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

        popup.showAsDropDown(btnMenu, -UIHelper.dpToPx(this, 156), UIHelper.dpToPx(this, 4));
    }

    private void showAutoRefreshPicker() {
        boolean dark = isNightMode;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = UIHelper.dpToPx(this, 8);
        int padV = UIHelper.dpToPx(this, 6);
        root.setPadding(padH, padV, padH, padV);
        root.setBackgroundColor(dark ? Color.parseColor("#2A2A2A") : Color.WHITE);

        // 显示当前工作区名称
        String wsName = (currentTab < tabTitles.length && tabTitles[currentTab] != null) ? tabTitles[currentTab] : "工作区" + (currentTab + 1);
        TextView title = new TextView(this);
        title.setText("定时刷新 - " + wsName);
        title.setTextSize(13);
        title.setTextColor(dark ? Color.parseColor("#AAAAAA") : Color.parseColor("#999999"));
        title.setPadding(UIHelper.dpToPx(this, 14), UIHelper.dpToPx(this, 8), UIHelper.dpToPx(this, 14), UIHelper.dpToPx(this, 8));
        root.addView(title);

        View titleDivider = new View(this);
        titleDivider.setBackgroundColor(dark ? Color.parseColor("#3A3A3A") : Color.parseColor("#F0F0F0"));
        titleDivider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        root.addView(titleDivider);

        String[] labels = {"关闭", "每30秒", "每1分钟", "每5分钟"};
        int[] intervals = {0, 30, 60, 300};
        int currentInterval = tabAutoRefresh[currentTab];

        // 用列表记录哪些是菜单行，避免索引计算错误
        List<LinearLayout> menuRows = new ArrayList<>();
        List<Integer> menuIntervals = new ArrayList<>();

        for (int i = 0; i < labels.length; i++) {
            final int interval = intervals[i];
            boolean checked = currentInterval == interval;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(UIHelper.dpToPx(this, 14), UIHelper.dpToPx(this, 12), UIHelper.dpToPx(this, 14), UIHelper.dpToPx(this, 12));
            row.setTag(interval); // 标记对应的间隔值

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

            menuRows.add(row);
            menuIntervals.add(interval);
            root.addView(row);

            if (i < labels.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(dark ? Color.parseColor("#3A3A3A") : Color.parseColor("#F0F0F0"));
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                divLp.leftMargin = UIHelper.dpToPx(this, 14);
                divider.setLayoutParams(divLp);
                root.addView(divider);
            }
        }

        PopupWindow popup = new PopupWindow(root,
                UIHelper.dpToPx(this, 200), LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(UIHelper.dpToPx(this, 8));
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                dark ? Color.parseColor("#2A2A2A") : Color.WHITE));
        popup.setOutsideTouchable(true);

        for (int i = 0; i < menuRows.size(); i++) {
            final int interval = menuIntervals.get(i);
            menuRows.get(i).setOnClickListener(v -> {
                popup.dismiss();
                ConfigManager.saveTabAutoRefresh(prefs, currentTab, interval);
                setAutoRefresh(interval);
            });
        }

        popup.showAsDropDown(btnMenu, -UIHelper.dpToPx(this, 132), UIHelper.dpToPx(this, 4));
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
        for (int i = 0; i < AppConfig.MAX_TABS; i++) {
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
                UIHelper.formatBytes(usedMemory) + " / " + UIHelper.formatBytes(maxMemory), density);
        rings.addView(appRing);

        // 间距
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams((int) (30 * density), 1));
        rings.addView(spacer);

        // 系统内存圆环
        LinearLayout sysRing = createRingView("系统", sysPercent,
                UIHelper.formatBytes(usedSys) + " / " + UIHelper.formatBytes(totalSys), density);
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
        startAutoRefresh(interval);
        if (interval > 0) {
            Toast.makeText(this, "定时刷新: " + UIHelper.formatInterval(interval) + " (当前工作区)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "定时刷新已关闭", Toast.LENGTH_SHORT).show();
        }
    }

    /** 启动定时刷新（无 Toast，供内部调用复用） */
    private void startAutoRefresh(int interval) {
        autoRefreshInterval = interval;
        stopAutoRefresh();

        if (interval > 0) {
            autoRefreshRunnable = () -> {
                autoRefreshRunning = false;
                if (isFinishing() || isDestroyed()) return;
                WebView wv = getCurrentWebView();
                if (wv != null) {
                    wv.reload();
                }
                if (autoRefreshInterval > 0) {
                    scheduleAutoRefresh();
                }
            };
            scheduleAutoRefresh();
        }

        updateAutoRefreshIndicator();
    }

    /** 调度下一次自动刷新 */
    private void scheduleAutoRefresh() {
        if (autoRefreshRunning || autoRefreshInterval <= 0) return;
        if (isFinishing() || isDestroyed()) return;
        autoRefreshRunning = true;
        autoRefreshHandler.postDelayed(autoRefreshRunnable, autoRefreshInterval * 1000L);
    }

    /** 停止自动刷新 */
    private void stopAutoRefresh() {
        autoRefreshRunning = false;
        autoRefreshHandler.removeCallbacksAndMessages(null);
        autoRefreshRunnable = null;
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
        loadingText.setPadding(UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 16));
        loadingText.setGravity(Gravity.CENTER);
        searchResults.addView(loadingText);

        if (searchContent) {
            // 搜索网页内容（异步回调，避免阻塞线程）
            searchWebContent(query, results -> {
                searchResults.removeAllViews();
                displaySearchResults(results, query);
            });
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

    /** 搜索回调接口 */
    interface SearchCallback {
        void onSearchComplete(List<SearchResult> results);
    }

    private void searchWebContent(String query, SearchCallback callback) {
        List<SearchResult> results = new ArrayList<>(searchLinksOnly(query));

        // 统计需要搜索的 WebView 数量
        int pendingCount = 0;
        for (int i = 0; i < AppConfig.MAX_TABS; i++) {
            if (webViews[i] != null) pendingCount++;
        }

        if (pendingCount == 0) {
            callback.onSearchComplete(results);
            return;
        }

        // 异步搜索每个 WebView 的页面内容
        final int[] pending = {pendingCount};
        final String lowerQuery = query.toLowerCase();

        for (int i = 0; i < AppConfig.MAX_TABS; i++) {
            WebView wv = webViews[i];
            if (wv == null) continue;

            final int tabIndex = i;
            wv.evaluateJavascript(
                    "(function() { return document.body.innerText; })();",
                    value -> {
                        if (value != null && value.toLowerCase().contains(lowerQuery)) {
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

                        pending[0]--;
                        if (pending[0] == 0) {
                            callback.onSearchComplete(results);
                        }
                    }
            );
        }
    }

    private void displaySearchResults(List<SearchResult> results, String query) {
        if (results.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("未找到匹配结果");
            emptyText.setTextSize(14);
            emptyText.setTextColor(Color.parseColor("#999999"));
            emptyText.setPadding(UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 32), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 16));
            emptyText.setGravity(Gravity.CENTER);
            searchResults.addView(emptyText);
            return;
        }

        // 结果数量
        TextView countText = new TextView(this);
        countText.setText("找到 " + results.size() + " 个结果");
        countText.setTextSize(12);
        countText.setTextColor(Color.parseColor("#999999"));
        countText.setPadding(UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 12), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 8));
        searchResults.addView(countText);

        // 结果列表
        for (SearchResult result : results) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 12), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 12));
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
            urlView.setPadding(0, UIHelper.dpToPx(this, 2), 0, 0);
            item.addView(urlView);

            // 来源
            TextView sourceView = new TextView(this);
            sourceView.setText("匹配: " + result.source);
            sourceView.setTextSize(11);
            sourceView.setTextColor(Color.parseColor("#999999"));
            sourceView.setPadding(0, UIHelper.dpToPx(this, 2), 0, 0);
            item.addView(sourceView);

            // 分割线
            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dividerParams.topMargin = UIHelper.dpToPx(this, 12);
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
                for (int i = 0; i < AppConfig.MAX_TABS; i++) {
                    if (webViews[i] != null) {
                        WebViewFactory.injectNightModeCSS(webViews[i]);
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
            for (int i = 0; i < AppConfig.MAX_TABS; i++) {
                if (webViews[i] != null) {
                    WebViewFactory.removeNightModeCSS(webViews[i]);
                }
            }
        }
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
        title.setPadding(UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 8), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 4));
        bottomMenuContainer.addView(title);

        // 链接列表
        for (int i = 0; i < links.size(); i++) {
            final int linkIndex = i;
            LinkItem link = links.get(i);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 12), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 12));

            // 高亮当前选中的链接
            if (tabIndex == currentTab && i == currentLinkIndex) {
                item.setBackgroundColor(Color.parseColor("#E3F2FD"));
            }

            TextView dot = new TextView(this);
            dot.setText("•");
            dot.setTextSize(14);
            dot.setTextColor(Color.parseColor("#1976D2"));
            dot.setPadding(0, 0, UIHelper.dpToPx(this, 8), 0);

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
                dividerParams.leftMargin = UIHelper.dpToPx(this, 16);
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
        if (tabIndex < 0 || tabIndex >= tabCount || tabIndex >= tabLinks.size()) return;
        if (linkIndex < 0 || linkIndex >= tabLinks.get(tabIndex).size()) return;

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
            PageActionEngine.stopMutationObserver(oldWebView);
            oldWebView.setVisibility(View.GONE);
        }

        currentTab = index;
        currentLinkIndex = 0;
        activeLinkIndex = -1; // 重置活跃链接
        isDropdownOpen = false;

        // 切换工作区时，更新定时刷新状态（Feature 5）
        startAutoRefresh(tabAutoRefresh[index]);

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
            if (!WebViewFactory.isAllowedUrl(link.url, this)) return;
            WebViewFactory.applyDesktopModeUA(wv, link.desktopMode);
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
            item.setPadding(UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 12), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 12));

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
            LinkItem link = links.get(currentLinkIndex);
            String url = link.url;
            if (!WebViewFactory.isAllowedUrl(url, this)) return;
            activeLinkIndex = currentLinkIndex; // 设置活跃链接
            WebViewFactory.applyDesktopModeUA(wv, link.desktopMode);
            wv.loadUrl(url);
        }
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

                    String tag = UIHelper.extractJsonString(json, "tag");
                    String id = UIHelper.extractJsonString(json, "id");
                    String classes = UIHelper.extractJsonString(json, "classes");
                    String text = UIHelper.extractJsonString(json, "text");

                    showElementInfoDialog(tag, id, classes, text);
                } catch (Exception e) {
                    Toast.makeText(this, "解析失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
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
                UIHelper.copyToClipboard(this, "#" + id);
                Toast.makeText(this, "已复制: #" + id, Toast.LENGTH_SHORT).show();
            });
        }

        if (classes != null && !classes.isEmpty()) {
            String firstClass = classes.split("\\s+")[0];
            tvClass.setVisibility(View.VISIBLE);
            tvClass.setText("." + firstClass);
            btnCopyClass.setVisibility(View.VISIBLE);
            btnCopyClass.setOnClickListener(v -> {
                UIHelper.copyToClipboard(this, "." + firstClass);
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
            UIHelper.copyToClipboard(this, allInfo.toString());
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


    /** 显示错误页面（Feature 3）*/
    private void showErrorPage(WebView webView, String errorMsg) {
        // 转义 HTML 特殊字符，防止 XSS
        String safeMsg = errorMsg
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
                "<div class=\"msg\">" + safeMsg + "</div>" +
                "<button class=\"btn\" onclick=\"location.reload()\">🔄 重新加载</button>" +
                "</body></html>";
        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null);
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
        root.setPadding(0, UIHelper.dpToPx(this, 12), 0, UIHelper.dpToPx(this, 12));

        // 标题
        TextView titleView = new TextView(this);
        titleView.setText("浏览历史 (" + size + " 条)");
        titleView.setTextSize(15);
        titleView.setTextColor(dark ? Color.parseColor("#E0E0E0") : Color.parseColor("#333333"));
        titleView.setPadding(UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 8), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 12));
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
            row.setPadding(UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 10), UIHelper.dpToPx(this, 16), UIHelper.dpToPx(this, 10));

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
            numView.setPadding(0, 0, UIHelper.dpToPx(this, 10), 0);
            numView.setMinWidth(UIHelper.dpToPx(this, 24));
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
                curTag.setPadding(UIHelper.dpToPx(this, 8), 0, 0, 0);
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
                divLp.leftMargin = UIHelper.dpToPx(this, 50);
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
        for (int i = 0; i < AppConfig.MAX_TABS; i++) {
            if (webViews[i] != null) {
                webViews[i].onPause();
            }
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
        for (int i = 0; i < AppConfig.MAX_TABS; i++) {
            if (webViews[i] != null) {
                webViews[i].destroy();
                webViews[i] = null;
            }
        }
    }

    // ==================== WebViewCallbacks 实现 ====================

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

    @Override
    public boolean isPageActionsEnabled() {
        return prefs.getBoolean("page_actions_enabled", true);
    }

    @Override
    public boolean isInspectMode() {
        return isInspectMode;
    }

    @Override
    public boolean isNightMode() {
        return isNightMode;
    }

    @Override
    public boolean isNightModeCSS() {
        return isNightModeCSS;
    }

    @Override
    public ValueCallback<Uri[]> getFilePathCallback() {
        return filePathCallback;
    }

    @Override
    public void setFilePathCallback(ValueCallback<Uri[]> callback) {
        filePathCallback = callback;
    }

    @Override
    public void onExecuteCustomScript(WebView webView) {
        PageActionEngine.executeCustomScript(webView, prefs.getBoolean("page_actions_enabled", true),
                tabLinks, currentTab, currentLinkIndex, activeLinkIndex, getCurrentLink());
    }

    @Override
    public void onInjectNightModeCSS(WebView webView) {
        WebViewFactory.injectNightModeCSS(webView);
    }

    @Override
    public void onRemoveNightModeCSS(WebView webView) {
        WebViewFactory.removeNightModeCSS(webView);
    }

    @Override
    public void onApplyDesktopZoom(WebView webView) {
        float density = getResources().getDisplayMetrics().density;
        int screenW = (int) (getResources().getDisplayMetrics().widthPixels / density);
        WebViewFactory.applyDesktopZoom(webView, density, screenW);
    }

    @Override
    public void onInspectElementAt(float x, float y) {
        inspectElementAt(x, y);
    }

    @Override
    public void onShowErrorPage(WebView webView, String errorMsg) {
        showErrorPage(webView, errorMsg);
    }

    @Override
    public Context getContext() {
        return this;
    }
}
