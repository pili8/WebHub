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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
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

    private WebView webView1, webView2, webView3;
    private WebView[] webViews;

    private ProgressBar progressBar;
    private TextView tvTitle, tvArrow;
    private TextView btnMenu;
    private ImageView btnRefresh;
    private LinearLayout btnDropdown, dropdownList;
    private TextView inspectBanner;
    private LinearLayout tab1, tab2, tab3;
    private LinearLayout[] tabs;
    private TextView iconTab1, iconTab2, iconTab3;
    private TextView[] tabIcons;
    private TextView textTab1, textTab2, textTab3;
    private TextView[] tabTexts;

    private int currentTab = 0;
    private int currentLinkIndex = 0;
    private boolean isDropdownOpen = false;
    private boolean isInspectMode = false;
    private boolean[] tabInitialized = {false, false, false};

    private String[] tabIconsEmoji = {"📊", "📋", "➕"};
    private String[] tabTitles = {"销售机会", "最近新增", "录入线索"};
    private String[] tabActions = {"", "", ""};
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

        tabLinks.add(new ArrayList<>());
        tabLinks.add(new ArrayList<>());
        tabLinks.add(new ArrayList<>());

        initViews();
        loadConfig();
        updateUI();
        setupListeners();
        setupAllWebViews();
        requestPermissions();
        switchTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webViews[currentTab].onResume();

        String[] oldLinks = {prefs.getString("links1", ""), prefs.getString("links2", ""), prefs.getString("links3", "")};
        loadConfig();
        updateUI();

        String[] newLinks = {prefs.getString("links1", ""), prefs.getString("links2", ""), prefs.getString("links3", "")};

        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            if (!oldLinks[i].equals(newLinks[i])) {
                changed = true;
                tabInitialized[i] = false;
            }
        }

        if (changed) {
            currentLinkIndex = 0;
            loadCurrentLink();
        }
    }

    private void initViews() {
        webView1 = findViewById(R.id.webview1);
        webView2 = findViewById(R.id.webview2);
        webView3 = findViewById(R.id.webview3);
        webViews = new WebView[]{webView1, webView2, webView3};

        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);
        tvArrow = findViewById(R.id.tvArrow);
        btnMenu = findViewById(R.id.btnMenu);
        btnDropdown = findViewById(R.id.btnDropdown);
        dropdownList = findViewById(R.id.dropdownList);
        inspectBanner = findViewById(R.id.inspectBanner);
        btnRefresh = findViewById(R.id.btnRefresh);

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

        btnRefresh.setOnClickListener(v -> webViews[currentTab].reload());

        btnRefresh.setOnLongClickListener(v -> {
            webViews[currentTab].getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webViews[currentTab].reload();
            webViews[currentTab].postDelayed(() -> {
                webViews[currentTab].getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            }, 1000);
            Toast.makeText(this, "正在刷新最新数据...", Toast.LENGTH_SHORT).show();
            return true;
        });

        btnDropdown.setOnClickListener(v -> toggleDropdown());

        // 菜单按钮
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

    /**
     * 在指定坐标处查找元素信息
     */
    private void inspectElementAt(float x, float y) {
        String js = "(function() {" +
                "  var el = document.elementFromPoint(" + x + ", " + y + ");" +
                "  if (!el) return null;" +
                "" +
                "  var result = {};" +
                "  result.tag = el.tagName.toLowerCase();" +
                "  result.id = el.id || '';" +
                "  result.classes = (typeof el.className === 'string') ? el.className.trim() : '';" +
                "  var text = el.textContent || '';" +
                "  result.text = text.length > 100 ? text.substring(0, 100) + '...' : text.trim();" +
                "" +
                "  el.style.outline = '3px solid #FF5722';" +
                "  setTimeout(function() { el.style.outline = ''; }, 2000);" +
                "" +
                "  return JSON.stringify(result);" +
                "})()";

        webViews[currentTab].evaluateJavascript(js, value -> {
            if (value != null && !value.equals("null")) {
                try {
                    // 解析 JSON
                    String json = value;
                    if (json.startsWith("\"")) {
                        json = json.substring(1, json.length() - 1);
                    }
                    json = json.replace("\\\"", "\"");
                    json = json.replace("\\\\", "\\");

                    // 简单解析
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

        // 设置数据
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
            // 只显示第一个 class
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

        // 复制全部
        StringBuilder allInfo = new StringBuilder();
        allInfo.append("标签: ").append(tag).append("\n");
        if (id != null && !id.isEmpty()) allInfo.append("ID: #").append(id).append("\n");
        if (classes != null && !classes.isEmpty()) allInfo.append("Class: .").append(classes.split("\\s+")[0]).append("\n");
        if (text != null && !text.isEmpty()) allInfo.append("文本: ").append(text);

        btnCopyAll.setOnClickListener(v -> {
            copyToClipboard(allInfo.toString());
            Toast.makeText(this, "已复制全部信息", Toast.LENGTH_SHORT).show();
        });

        // 创建底部弹窗
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
        dialog.getWindow().setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
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

    private void loadConfig() {
        tabIconsEmoji[0] = prefs.getString("icon1", "📊");
        tabIconsEmoji[1] = prefs.getString("icon2", "📋");
        tabIconsEmoji[2] = prefs.getString("icon3", "➕");

        tabTitles[0] = prefs.getString("title1", "销售机会");
        tabTitles[1] = prefs.getString("title2", "最近新增");
        tabTitles[2] = prefs.getString("title3", "录入线索");

        tabActions[0] = prefs.getString("actions1", "");
        tabActions[1] = prefs.getString("actions2", "");
        tabActions[2] = prefs.getString("actions3", "");

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
        webViews[currentTab].onPause();

        // 退出查看模式
        if (isInspectMode) {
            isInspectMode = false;
            inspectBanner.setVisibility(View.GONE);
        }

        currentTab = index;
        currentLinkIndex = 0;
        isDropdownOpen = false;

        for (int i = 0; i < webViews.length; i++) {
            webViews[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }

        webViews[index].onResume();

        for (int i = 0; i < 3; i++) {
            if (i == index) {
                tabTexts[i].setTextColor(Color.parseColor("#1976D2"));
            } else {
                tabTexts[i].setTextColor(Color.parseColor("#666666"));
            }
        }

        if (!tabInitialized[index]) {
            loadCurrentLink();
            tabInitialized[index] = true;
        }

        updateDropdown();
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
            webViews[currentTab].loadUrl(links.get(currentLinkIndex).url);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupAllWebViews() {
        for (int i = 0; i < webViews.length; i++) {
            setupWebView(webViews[i]);
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

        // 触摸事件处理（用于查看元素模式）
        webView.setOnTouchListener((v, event) -> {
            if (isInspectMode && event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                // 转换为网页坐标
                float density = getResources().getDisplayMetrics().density;
                float webX = x / density;
                float webY = y / density;
                inspectElementAt(webX, webY);
                return true; // 消耗事件
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
                if (view == webViews[currentTab]) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (view == webViews[currentTab]) {
                    progressBar.setVisibility(View.GONE);
                }
                CookieManager.getInstance().flush();

                // 执行自定义操作
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
                if (view == webViews[currentTab]) {
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

        // 处理特殊协议
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

        // 允许所有 HTTP/HTTPS 网址
        if (url.startsWith("http://") || url.startsWith("https://")) return true;

        // 允许其他常见协议
        if (url.startsWith("javascript:") || url.startsWith("about:blank") || url.startsWith("data:")) return true;

        return false;
    }

    private void executeCustomScript(WebView webView) {
        int tabIndex = -1;
        for (int i = 0; i < webViews.length; i++) {
            if (webViews[i] == webView) {
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
                // 点击操作：支持延迟
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

            if (tryClosePopup()) {
                return true;
            }

            if (webViews[currentTab].canGoBack()) {
                webViews[currentTab].goBack();
                return true;
            }

            loadCurrentLink();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean tryClosePopup() {
        float x = webViews[currentTab].getWidth() / 2f;
        float y = 15f;

        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        webViews[currentTab].dispatchTouchEvent(downEvent);
        downEvent.recycle();

        webViews[currentTab].postDelayed(() -> {
            MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
            webViews[currentTab].dispatchTouchEvent(upEvent);
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
