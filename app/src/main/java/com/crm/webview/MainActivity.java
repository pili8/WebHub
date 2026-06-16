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

    // 动态 WebView 和选项卡
    private List<WebView> webViews = new ArrayList<>();
    private List<LinearLayout> tabViews = new ArrayList<>();
    private FrameLayout webViewContainer;
    private LinearLayout tabContainer;

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
    private boolean[] tabInitialized;

    // 配置数据
    private int tabCount = 3;
    private String[] tabIcons;
    private String[] tabTitles;
    private String[] tabActions;
    private List<List<LinkItem>> tabLinks = new ArrayList<>();

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
        createDynamicViews();
        setupListeners();
        setupAllWebViews();
        requestPermissions();
        switchTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webViews.size() > currentTab) {
            webViews.get(currentTab).onResume();
        }

        // 检查配置是否改变
        int oldTabCount = tabCount;
        loadConfig();

        if (tabCount != oldTabCount) {
            // 选项卡数量改变，需要重建
            recreate();
            return;
        }

        // 检查链接是否改变
        boolean changed = false;
        for (int i = 0; i < tabCount; i++) {
            String newLinks = prefs.getString("links" + (i + 1), "");
            // 简单检查，实际应该比较内容
        }

        updateUI();
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webViewContainer);
        tabContainer = findViewById(R.id.tabContainer);
        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);
        tvArrow = findViewById(R.id.tvArrow);
        btnMenu = findViewById(R.id.btnMenu);
        btnDropdown = findViewById(R.id.btnDropdown);
        dropdownList = findViewById(R.id.dropdownList);
        inspectBanner = findViewById(R.id.inspectBanner);
        btnRefresh = findViewById(R.id.btnRefresh);
    }

    private void loadConfig() {
        tabCount = prefs.getInt("tab_count", 3);
        if (tabCount < 2) tabCount = 2;
        if (tabCount > 5) tabCount = 5;

        tabIcons = new String[tabCount];
        tabTitles = new String[tabCount];
        tabActions = new String[tabCount];
        tabInitialized = new boolean[tabCount];

        String[] defaultIcons = {"📊", "📋", "➕", "📁", "👤"};
        String[] defaultTitles = {"销售机会", "最近新增", "录入线索", "选项卡4", "选项卡5"};

        for (int i = 0; i < tabCount; i++) {
            tabIcons[i] = prefs.getString("icon" + (i + 1), i < defaultIcons.length ? defaultIcons[i] : "📌");
            tabTitles[i] = prefs.getString("title" + (i + 1), i < defaultTitles.length ? defaultTitles[i] : "选项卡 " + (i + 1));
            tabActions[i] = prefs.getString("actions" + (i + 1), "");

            // 加载链接
            List<LinkItem> links = new ArrayList<>();
            String linksStr = prefs.getString("links" + (i + 1), "");
            if (!linksStr.isEmpty()) {
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
    private void createDynamicViews() {
        webViewContainer.removeAllViews();
        tabContainer.removeAllViews();
        webViews.clear();
        tabViews.clear();

        for (int i = 0; i < tabCount; i++) {
            // 创建 WebView
            WebView webView = new WebView(this);
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            webView.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            webViewContainer.addView(webView);
            webViews.add(webView);

            // 创建选项卡
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            tab.setBackgroundResource(android.R.attr.selectableItemBackground);

            TextView icon = new TextView(this);
            icon.setText(tabIcons[i]);
            icon.setTextSize(20);
            icon.setGravity(Gravity.CENTER);

            TextView text = new TextView(this);
            text.setText(tabTitles[i]);
            text.setTextSize(10);
            text.setGravity(Gravity.CENTER);
            text.setTextColor(i == 0 ? Color.parseColor("#1976D2") : Color.parseColor("#666666"));

            tab.addView(icon);
            tab.addView(text);
            tabContainer.addView(tab);
            tabViews.add(tab);

            final int index = i;
            tab.setOnClickListener(v -> switchTab(index));
        }
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> webViews.get(currentTab).reload());

        btnRefresh.setOnLongClickListener(v -> {
            webViews.get(currentTab).getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webViews.get(currentTab).reload();
            webViews.get(currentTab).postDelayed(() -> {
                webViews.get(currentTab).getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            }, 1000);
            Toast.makeText(this, "正在刷新最新数据...", Toast.LENGTH_SHORT).show();
            return true;
        });

        btnDropdown.setOnClickListener(v -> toggleDropdown());
        btnMenu.setOnClickListener(v -> showPopupMenu());
    }

    private void showPopupMenu() {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, btnMenu);
        popup.getMenu().add(0, 1, 0, isInspectMode ? "退出查找元素" : "🔍 查找元素");
        popup.getMenu().add(0, 2, 0, "⚙️ 设置");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                toggleInspectMode();
                return true;
            } else if (item.getItemId() == 2) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        popup.show();
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

        if (webViews.size() > currentTab) {
            webViews.get(currentTab).onPause();
        }

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
        for (int i = 0; i < tabViews.size(); i++) {
            LinearLayout tab = tabViews.get(i);
            TextView text = (TextView) tab.getChildAt(1);
            if (i == index) {
                text.setTextColor(Color.parseColor("#1976D2"));
            } else {
                text.setTextColor(Color.parseColor("#666666"));
            }
        }

        if (webViews.size() > index) {
            webViews.get(index).onResume();
        }

        if (!tabInitialized[index]) {
            loadCurrentLink();
            tabInitialized[index] = true;
        }

        updateDropdown();
    }

    private void updateUI() {
        for (int i = 0; i < tabViews.size(); i++) {
            LinearLayout tab = tabViews.get(i);
            TextView icon = (TextView) tab.getChildAt(0);
            TextView text = (TextView) tab.getChildAt(1);
            icon.setText(tabIcons[i]);
            text.setText(tabTitles[i]);
        }
        updateDropdown();
    }

    private void updateDropdown() {
        if (tabLinks.size() <= currentTab) return;

        List<LinkItem> links = tabLinks.get(currentTab);
        if (links.isEmpty()) return;

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
        if (currentLinkIndex < links.size() && webViews.size() > currentTab) {
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
                float webX = x / density;
                float webY = y / density;
                inspectElementAt(webX, webY);
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
                if (view == webViews.get(currentTab)) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (view == webViews.get(currentTab)) {
                    progressBar.setVisibility(View.GONE);
                }
                CookieManager.getInstance().flush();
                executeCustomScript(view);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view == webViews.get(currentTab)) {
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
            if (isInspectMode) {
                toggleInspectMode();
                return true;
            }

            if (isDropdownOpen) {
                isDropdownOpen = false;
                updateDropdown();
                return true;
            }

            boolean kdocsOptimize = prefs.getBoolean("kdocs_optimize", true);
            String currentUrl = webViews.get(currentTab).getUrl();
            if (kdocsOptimize && isKdocsUrl(currentUrl)) {
                tryClosePopup();
                return true;
            }

            if (webViews.get(currentTab).canGoBack()) {
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
        float x = webViews.get(currentTab).getWidth() / 2f;
        float y = 15f;

        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        webViews.get(currentTab).dispatchTouchEvent(downEvent);
        downEvent.recycle();

        webViews.get(currentTab).postDelayed(() -> {
            MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
            webViews.get(currentTab).dispatchTouchEvent(upEvent);
            upEvent.recycle();
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
