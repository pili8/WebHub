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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MAX_TABS = 5;

    // WebView 容器和选项卡容器
    private FrameLayout webViewContainer;
    private LinearLayout tabContainer;
    private LinearLayout bottomMenuContainer;

    // 动态创建的视图
    private List<WebView> webViews = new ArrayList<>();
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
    private boolean isDropdownOpen = false;
    private boolean isInspectMode = false;
    private boolean isBottomMenuOpen = false;
    private boolean isSearchOpen = false;
    private boolean isNightMode = false;
    private boolean isNightModeCSS = true; // 网页也应用夜间模式

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
    private boolean[] tabInitialized = new boolean[MAX_TABS];

    private SharedPreferences prefs;
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
        if (currentTab < webViews.size()) {
            webViews.get(currentTab).onResume();
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
        isNightModeCSS = prefs.getBoolean("night_mode_css", true);
    }

    private void loadConfig() {
        tabCount = prefs.getInt("tab_count", 3);
        if (tabCount < 2) tabCount = 2;
        if (tabCount > MAX_TABS) tabCount = MAX_TABS;

        String[] defaultIcons = {"📊", "📋", "➕", "📁", "👤"};
        String[] defaultTitles = {"销售机会", "最近新增", "录入线索", "选项卡4", "选项卡5"};
        String[] defaultUrls = {
                "https://www.kdocs.cn/wo/sl/v12CEOZt",
                "https://www.kdocs.cn/wo/sl/v14T2gpD",
                "https://www.kdocs.cn/wo/sl/v13iHfr4",
                "about:blank",
                "about:blank"
        };

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
                    String[] titleUrlParts = titleUrl.split(",", 2);
                    if (titleUrlParts.length == 2) {
                        links.add(new LinkItem(titleUrlParts[0].trim(), titleUrlParts[1].trim()));
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
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createTabsAndWebViews() {
        // 清除旧视图
        webViewContainer.removeAllViews();
        tabContainer.removeAllViews();
        webViews.clear();
        tabViews.clear();
        tabIconViews.clear();
        tabTextViews.clear();

        for (int i = 0; i < tabCount; i++) {
            // 创建 WebView
            WebView webView = new WebView(this);
            FrameLayout.LayoutParams wvParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            webView.setLayoutParams(wvParams);
            webView.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            webViewContainer.addView(webView);
            webViews.add(webView);

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

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> {
            if (currentTab < webViews.size()) {
                webViews.get(currentTab).reload();
            }
        });

        btnRefresh.setOnLongClickListener(v -> {
            if (currentTab < webViews.size()) {
                webViews.get(currentTab).getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                webViews.get(currentTab).reload();
                webViews.get(currentTab).postDelayed(() -> {
                    if (currentTab < webViews.size()) {
                        webViews.get(currentTab).getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                    }
                }, 1000);
            }
            Toast.makeText(this, "正在刷新最新数据...", Toast.LENGTH_SHORT).show();
            return true;
        });

        btnDropdown.setOnClickListener(v -> toggleDropdown());
        btnMenu.setOnClickListener(v -> showPopupMenu());

        // 搜索按钮
        ImageView btnSearch = findViewById(R.id.btnSearch);
        btnSearch.setOnClickListener(v -> toggleSearch());

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
        popup.getMenu().add(0, 1, 0, isInspectMode ? "退出查找元素" : "🔍 查找元素");
        popup.getMenu().add(0, 2, 0, isNightMode ? "☀️ 日间模式" : "🌙 夜间模式");
        popup.getMenu().add(0, 3, 0, "⚙️ 设置");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                toggleInspectMode();
                return true;
            } else if (item.getItemId() == 2) {
                toggleNightMode();
                return true;
            } else if (item.getItemId() == 3) {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra("night_mode", isNightMode);
                startActivity(intent);
                return true;
            }
            return false;
        });

        popup.show();
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

        // 然后搜索网页内容
        for (int i = 0; i < tabLinks.size(); i++) {
            List<LinkItem> links = tabLinks.get(i);
            for (int j = 0; j < links.size(); j++) {
                LinkItem link = links.get(j);
                if (link.url.equals("about:blank")) continue;

                try {
                    // 创建临时 WebView 加载网页
                    final int tabIndex = i;
                    final int linkIndex = j;
                    final String[] content = {""};

                    runOnUiThread(() -> {
                        WebView tempWebView = new WebView(MainActivity.this);
                        tempWebView.getSettings().setJavaScriptEnabled(true);
                        tempWebView.setWebViewClient(new WebViewClient() {
                            @Override
                            public void onPageFinished(WebView view, String url) {
                                view.evaluateJavascript(
                                        "(function() { return document.body.innerText; })();",
                                        value -> {
                                            content[0] = value;
                                            synchronized (content) {
                                                content.notify();
                                            }
                                        }
                                );
                            }
                        });
                        tempWebView.loadUrl(link.url);
                    });

                    synchronized (content) {
                        content.wait(5000); // 最多等待5秒
                    }

                    if (!content[0].isEmpty() && content[0].toLowerCase().contains(query)) {
                        // 检查是否已在结果中
                        boolean exists = false;
                        for (SearchResult r : results) {
                            if (r.tabIndex == i && r.linkIndex == j) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            results.add(new SearchResult(i, j, link.title, link.url, "网页内容"));
                        }
                    }
                } catch (Exception e) {
                    // 忽略加载失败的网页
                }
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
            // App 界面变暗
            tabContainer.setBackgroundColor(Color.parseColor("#1E1E1E"));
            bottomMenuContainer.setBackgroundColor(Color.parseColor("#1E1E1E"));
            webViewContainer.setBackgroundColor(Color.parseColor("#121212"));

            // 如果网页暗色开启，应用 CSS
            if (isNightModeCSS) {
                for (WebView webView : webViews) {
                    injectNightModeCSS(webView);
                }
            }
        } else {
            // App 界面恢复正常
            tabContainer.setBackgroundColor(Color.WHITE);
            bottomMenuContainer.setBackgroundColor(Color.WHITE);
            webViewContainer.setBackgroundColor(Color.WHITE);

            // 关闭网页暗色 CSS
            for (WebView webView : webViews) {
                removeNightModeCSS(webView);
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

        // 切换 WebView
        for (int i = 0; i < webViews.size(); i++) {
            webViews.get(i).setVisibility(i == tabIndex ? View.VISIBLE : View.GONE);
        }

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
        if (index >= webViews.size()) return;

        // 关闭底部菜单
        hideBottomMenu();

        if (isInspectMode) {
            isInspectMode = false;
            inspectBanner.setVisibility(View.GONE);
        }

        currentTab = index;
        currentLinkIndex = 0;
        isDropdownOpen = false;

        // 切换 WebView 显示
        for (int i = 0; i < webViews.size(); i++) {
            webViews.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }

        // 切换选项卡样式
        for (int i = 0; i < tabTextViews.size(); i++) {
            if (i == index) {
                tabTextViews.get(i).setTextColor(Color.parseColor("#1976D2"));
            } else {
                tabTextViews.get(i).setTextColor(Color.parseColor("#666666"));
            }
        }

        if (!tabInitialized[index]) {
            loadCurrentLink();
            tabInitialized[index] = true;
        }

        updateDropdown();
    }

    private void switchLink(int tabIndex, int linkIndex) {
        if (tabIndex < 0 || tabIndex >= tabLinks.size()) return;
        List<LinkItem> links = tabLinks.get(tabIndex);
        if (linkIndex < 0 || linkIndex >= links.size()) return;

        currentLinkIndex = linkIndex;

        // 加载链接
        if (tabIndex < webViews.size()) {
            LinkItem link = links.get(linkIndex);
            webViews.get(tabIndex).loadUrl(link.url);
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
        if (currentLinkIndex < links.size() && currentTab < webViews.size()) {
            webViews.get(currentTab).loadUrl(links.get(currentLinkIndex).url);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupAllWebViews() {
        for (WebView webView : webViews) {
            setupWebView(webView);
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
                if (currentTab < webViews.size() && view == webViews.get(currentTab)) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (currentTab < webViews.size() && view == webViews.get(currentTab)) {
                    progressBar.setVisibility(View.GONE);
                }
                CookieManager.getInstance().flush();
                executeCustomScript(view);

                // 网页暗色（从属于 App 暗色模式）
                if (isNightMode && isNightModeCSS) {
                    injectNightModeCSS(view);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (currentTab < webViews.size() && view == webViews.get(currentTab)) {
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
        if (currentTab >= webViews.size()) return;

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

        webViews.get(currentTab).evaluateJavascript(js, value -> {
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

        LinearLayout rowId = dialogView.findViewById(R.id.rowId);
        LinearLayout rowClass = dialogView.findViewById(R.id.rowClass);
        LinearLayout rowText = dialogView.findViewById(R.id.rowText);

        TextView btnCopyId = dialogView.findViewById(R.id.btnCopyId);
        TextView btnCopyClass = dialogView.findViewById(R.id.btnCopyClass);
        TextView btnCopyAll = dialogView.findViewById(R.id.btnCopyAll);
        TextView btnClose = dialogView.findViewById(R.id.btnClose);

        tvTag.setText("<" + tag + ">");

        if (id != null && !id.isEmpty()) {
            rowId.setVisibility(View.VISIBLE);
            tvId.setText("#" + id);
            btnCopyId.setVisibility(View.VISIBLE);
            btnCopyId.setOnClickListener(v -> {
                copyToClipboard("#" + id);
                Toast.makeText(this, "已复制: #" + id, Toast.LENGTH_SHORT).show();
            });
        }

        if (classes != null && !classes.isEmpty()) {
            rowClass.setVisibility(View.VISIBLE);
            String firstClass = classes.split("\\s+")[0];
            tvClass.setText("." + firstClass);
            btnCopyClass.setVisibility(View.VISIBLE);
            btnCopyClass.setOnClickListener(v -> {
                copyToClipboard("." + firstClass);
                Toast.makeText(this, "已复制: ." + firstClass, Toast.LENGTH_SHORT).show();
            });
        }

        if (text != null && !text.isEmpty()) {
            rowText.setVisibility(View.VISIBLE);
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

        if (url.startsWith("http://") || url.startsWith("https://")) return true;
        if (url.startsWith("javascript:") || url.startsWith("about:blank") || url.startsWith("data:")) return true;

        return false;
    }

    private void executeCustomScript(WebView webView) {
        int tabIndex = -1;
        for (int i = 0; i < webViews.size(); i++) {
            if (webViews.get(i) == webView) {
                tabIndex = i;
                break;
            }
        }

        if (tabIndex >= 0 && tabIndex < tabActions.length) {
            String actions = tabActions[tabIndex];
            if (actions != null && !actions.isEmpty()) {
                String js = buildScriptFromActions(actions);
                if (!js.isEmpty()) {
                    webView.postDelayed(() -> {
                        webView.evaluateJavascript(js, null);
                    }, 500);
                }
            }
        }
    }

    private String buildScriptFromActions(String actions) {
        StringBuilder js = new StringBuilder();
        js.append("(function(){");

        String[] lines = actions.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\|");
            if (parts.length < 2) continue;

            String action = parts[0];
            String selector = parts[1];
            selector = selector.replace("'", "\\'");

            if ("hide".equals(action)) {
                js.append("document.querySelectorAll('").append(selector).append("').forEach(el=>el.style.display='none');");
            } else if ("click".equals(action)) {
                int delay = 0;
                if (parts.length > 2) {
                    try {
                        delay = Integer.parseInt(parts[2]);
                    } catch (Exception e) {}
                }
                if (delay > 0) {
                    js.append("setTimeout(function(){document.querySelectorAll('").append(selector).append("').forEach(el=>el.click());},").append(delay * 1000).append(");");
                } else {
                    js.append("document.querySelectorAll('").append(selector).append("').forEach(el=>el.click());");
                }
            } else if ("modify".equals(action)) {
                String value = parts.length > 2 ? parts[2] : "";
                value = value.replace("'", "\\'");
                js.append("document.querySelectorAll('").append(selector).append("').forEach(el=>el.textContent='").append(value).append("');");
            }
        }

        js.append("})()");
        return js.toString();
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
            String currentUrl = currentTab < webViews.size() ? webViews.get(currentTab).getUrl() : null;
            if (kdocsOptimize && isKdocsUrl(currentUrl)) {
                tryClosePopup();
                return true;
            }

            // 普通返回逻辑
            if (currentTab < webViews.size() && webViews.get(currentTab).canGoBack()) {
                webViews.get(currentTab).goBack();
                return true;
            }

            // 没有历史记录，不做任何操作
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isKdocsUrl(String url) {
        if (url == null) return false;
        return url.contains("kdocs.cn") || url.contains("wps.cn") || url.contains("wps.com");
    }

    private boolean tryClosePopup() {
        if (currentTab >= webViews.size()) return false;

        float x = webViews.get(currentTab).getWidth() / 2f;
        float y = 15f;

        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        webViews.get(currentTab).dispatchTouchEvent(downEvent);
        downEvent.recycle();

        webViews.get(currentTab).postDelayed(() -> {
            if (currentTab < webViews.size()) {
                MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
                webViews.get(currentTab).dispatchTouchEvent(upEvent);
                upEvent.recycle();
            }
        }, 50);

        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (WebView webView : webViews) {
            webView.onPause();
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (WebView webView : webViews) {
            if (webView != null) {
                webView.destroy();
            }
        }
    }
}
