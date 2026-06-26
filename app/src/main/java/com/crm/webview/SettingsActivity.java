package com.crm.webview;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout settingsContainer;
    private Switch switchKdocsOptimize;
    private Switch switchNightModeCSS;
    private Switch switchPageActions;
    private android.widget.Spinner spinnerUA;
    private Spinner spinnerPreset;
    private int currentPresetIndex = -1;
    private boolean presetInitialized = false;
    private SharedPreferences prefs;

    private static final String[] UA_LABELS = {
        "默认", "Android Chrome", "iPhone", "iPad", "微信内置",
        "桌面 Chrome", "桌面 Edge", "桌面 Firefox", "桌面 Safari", "桌面 IE"
    };
    private static final String[] UA_VALUES = {
        "",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/125.0.0.0 Mobile Safari/537.36 MicroMessenger/8.0.49.2600",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko"
    };

    private static final String[] ACTION_TYPES = {"隐藏", "点击", "修改", "自定义脚本"};
    private static final String[] DEFAULT_TAB_ICONS = {"📊", "📋", "➕", "📁", "👤", "📌", "⭐", "🔧"};
    private static final String[] DEFAULT_TAB_TITLES = {"工作区1", "工作区2", "工作区3", "工作区4", "工作区5", "工作区6", "工作区7", "工作区8"};

    // 应用名称和图标预设
    private static final String[] ALIAS_NAMES = {
        "com.crm.webview.AliasWebHub",
        "com.crm.webview.AliasECM",
        "com.crm.webview.AliasLanHub",
        "com.crm.webview.AliasWebHubPng",
        "com.crm.webview.AliasGming",
        "com.crm.webview.AliasPili",
        "com.crm.webview.AliasPiliDouyin"
    };
    private static final String[] PRESET_LABELS = {
        "WebHub",
        "ECM",
        "LanHub",
        "WebHub",
        "Gming",
        "Pili",
        "PILI"
    };

    private List<TabData> tabsData = new ArrayList<>();

    static class TabData {
        String icon;
        String title;
        List<LinkData> links = new ArrayList<>();
        View sectionView;
        LinearLayout linksContainer;
        boolean isExpanded = false;
    }

    static class LinkData {
        String title;
        String url;
        String scope = "link"; // link/domain/tab/all
        boolean desktopMode = false;
        List<ActionData> actions = new ArrayList<>();
        View cardView;
        LinearLayout actionsContainer;
        Switch switchDesktopMode;
    }

    static class ActionData {
        String type;
        String selector;
        String value;
        String remark = "";
        int delay = 0;
        View actionView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        settingsContainer = findViewById(R.id.settingsContainer);
        switchKdocsOptimize = findViewById(R.id.switchKdocsOptimize);
        switchNightModeCSS = findViewById(R.id.switchNightModeCSS);
        switchPageActions = findViewById(R.id.switchPageActions);

        switchKdocsOptimize.setChecked(prefs.getBoolean("kdocs_optimize", true));
        switchNightModeCSS.setChecked(prefs.getBoolean("night_mode_css", false));
        switchPageActions.setChecked(prefs.getBoolean("page_actions_enabled", true));

        // UA 切换
        spinnerUA = findViewById(R.id.spinnerUA);
        ArrayAdapter<String> uaAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, UA_LABELS);
        uaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUA.setAdapter(uaAdapter);
        String savedUA = prefs.getString("user_agent", "");
        for (int i = 0; i < UA_VALUES.length; i++) {
            if (UA_VALUES[i].equals(savedUA)) {
                spinnerUA.setSelection(i);
                break;
            }
        }

        // 检查是否夜间模式
        boolean isNightMode = prefs.getBoolean("night_mode", false);
        if (isNightMode) {
            applyDarkTheme();
        }

        // 应用名称和图标预设切换
        setupPresetSwitcher();

        setupCache();
        setupExportImport();
        setupAbout();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveConfig());

        loadConfig();
        buildUI();
    }

    private void applyDarkTheme() {
        // 设置页面背景
        View rootView = findViewById(android.R.id.content);
        rootView.setBackgroundColor(Color.parseColor("#121212"));

        // 标题栏
        findViewById(R.id.settingsToolbar).setBackgroundColor(Color.parseColor("#1E1E1E"));

        // 关于区域文字颜色适配
        TextView tvAboutInfo = findViewById(R.id.tvAboutInfo);
        TextView tvAboutChangelog = findViewById(R.id.tvAboutChangelog);
        TextView tvAboutArrow = findViewById(R.id.tvAboutArrow);
        if (tvAboutInfo != null) tvAboutInfo.setTextColor(Color.parseColor("#AAAAAA"));
        if (tvAboutChangelog != null) tvAboutChangelog.setTextColor(Color.parseColor("#777777"));
        if (tvAboutArrow != null) tvAboutArrow.setTextColor(Color.parseColor("#666666"));
        // aboutHeader 内的标题文字
        LinearLayout aboutHeader = findViewById(R.id.aboutHeader);
        if (aboutHeader != null && aboutHeader.getChildCount() > 0) {
            View firstChild = aboutHeader.getChildAt(0);
            if (firstChild instanceof TextView) {
                ((TextView) firstChild).setTextColor(Color.parseColor("#E0E0E0"));
            }
        }
    }

    private void setupCache() {
        TextView tvCacheSize = findViewById(R.id.tvCacheSize);
        TextView btnClearCache = findViewById(R.id.btnClearCache);

        updateCacheSize(tvCacheSize);

        btnClearCache.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("清除缓存")
                    .setMessage("确定要清除所有缓存吗？\n这会清除登录状态。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        clearAllCache();
                        updateCacheSize(tvCacheSize);
                        Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void setupExportImport() {
        TextView btnExport = findViewById(R.id.btnExport);
        TextView btnImport = findViewById(R.id.btnImport);

        btnExport.setOnClickListener(v -> exportSettings());
        btnImport.setOnClickListener(v -> importSettings());
    }

    private void setupAbout() {
        TextView tvAboutInfo = findViewById(R.id.tvAboutInfo);
        TextView tvAboutChangelog = findViewById(R.id.tvAboutChangelog);
        TextView tvAboutGithub = findViewById(R.id.tvAboutGithub);
        LinearLayout aboutHeader = findViewById(R.id.aboutHeader);
        LinearLayout aboutContent = findViewById(R.id.aboutContent);
        TextView tvAboutArrow = findViewById(R.id.tvAboutArrow);

        // 折叠/展开切换
        aboutHeader.setOnClickListener(v -> {
            if (aboutContent.getVisibility() == View.GONE) {
                aboutContent.setVisibility(View.VISIBLE);
                tvAboutArrow.setText("▾");
            } else {
                aboutContent.setVisibility(View.GONE);
                tvAboutArrow.setText("▸");
            }
        });

        // 版本信息
        String versionName = "unknown";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {}

        tvAboutInfo.setText(
                "📱 WebHub v" + versionName + "\n" +
                "把常用网页变成 APP，支持自定义外观和自动化操作。\n" +
                "开发者: pili8 | 开源协议: MIT License");

        // 更新日志（发版时同步更新）
        tvAboutChangelog.setText(
                "📋 最近更新:\n" +
                "v2.7.6 - 设置页样式恢复、操作项折叠、桌面模式缩放优化\n" +
                "v2.7.5 - 桌面模式开关、设置页重排、Bug修复\n" +
                "v2.7.4 - 修复定时刷新闪退、工作区上限8个、支持HTTP、页面操作优化\n" +
                "v2.7.3 - 工作区自定义颜色、浏览历史、定时刷新、自定义脚本\n" +
                "v2.7.2 - 菜单重构、搜索优化");

        // 跳转 GitHub
        tvAboutGithub.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pili8/WebHub/releases"));
            startActivity(intent);
        });
    }

    private void setupPresetSwitcher() {
        spinnerPreset = findViewById(R.id.spinnerPreset);
        TextView tvPresetInfo = findViewById(R.id.tvPresetInfo);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, PRESET_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPreset.setAdapter(adapter);

        // 找到当前启用的别名
        currentPresetIndex = -1;
        PackageManager pm = getPackageManager();
        for (int i = 0; i < ALIAS_NAMES.length; i++) {
            try {
                ComponentName cn = new ComponentName(getPackageName(), ALIAS_NAMES[i]);
                int state = pm.getComponentEnabledSetting(cn);
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                    currentPresetIndex = i;
                    break;
                }
            } catch (Exception e) {}
        }
        if (currentPresetIndex < 0) currentPresetIndex = 0;
        presetInitialized = false;
        spinnerPreset.setSelection(currentPresetIndex);
        tvPresetInfo.setText(PRESET_LABELS[currentPresetIndex]);

        spinnerPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 跳过初始化时的自动触发
                if (!presetInitialized) {
                    presetInitialized = true;
                    return;
                }
                if (position >= 0 && position < ALIAS_NAMES.length && position != currentPresetIndex) {
                    currentPresetIndex = position;
                    switchAppPreset(position);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void switchAppPreset(int index) {
        PackageManager pm = getPackageManager();

        // 禁用所有别名
        for (String name : ALIAS_NAMES) {
            ComponentName cn = new ComponentName(getPackageName(), name);
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }

        // 启用选中的别名
        ComponentName selected = new ComponentName(getPackageName(), ALIAS_NAMES[index]);
        pm.setComponentEnabledSetting(selected,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

        TextView tvPresetInfo = findViewById(R.id.tvPresetInfo);
        tvPresetInfo.setText(PRESET_LABELS[index]);

        Toast.makeText(this, "已切换为「" + PRESET_LABELS[index] + "」，返回桌面查看", Toast.LENGTH_LONG).show();
    }

    private void exportSettings() {
        // 使用 SAF (Storage Access Framework) 选择保存位置
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "WebHub_Config_" + System.currentTimeMillis() + ".json");
        startActivityForResult(intent, 1002);
    }

    private void doExport(Uri uri) {
        try {
            JSONObject json = new JSONObject();

            json.put("version", 2);
            json.put("kdocs_optimize", prefs.getBoolean("kdocs_optimize", true));
            json.put("night_mode", prefs.getBoolean("night_mode", false));
            json.put("night_mode_css", prefs.getBoolean("night_mode_css", false));
            json.put("page_actions_enabled", prefs.getBoolean("page_actions_enabled", true));
            json.put("auto_refresh_interval", prefs.getInt("auto_refresh_interval", 0));
            json.put("tab_count", prefs.getInt("tab_count", 3));
            json.put("tabs_config", prefs.getString("tabs_config", buildTabsJsonFromPrefs().toString()));

            // 保留旧格式字段，方便旧版本导入。
            JSONArray tabsArray = new JSONArray();
            for (int i = 0; i < prefs.getInt("tab_count", 3); i++) {
                JSONObject tab = new JSONObject();
                tab.put("icon", prefs.getString("icon" + (i + 1), DEFAULT_TAB_ICONS[i]));
                tab.put("title", prefs.getString("title" + (i + 1), DEFAULT_TAB_TITLES[i]));
                tab.put("links", prefs.getString("links" + (i + 1), ""));
                tabsArray.put(tab);
            }
            json.put("tabs", tabsArray);

            // 写入到用户选择的位置
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(json.toString(2).getBytes("UTF-8"));
                os.close();
                Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show();

                // 分享文件
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/json");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(Intent.createChooser(shareIntent, "分享配置文件"));
            }

        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importSettings() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == 1001) {
            // 导入
            doImport(uri);
        } else if (requestCode == 1002) {
            // 导出
            doExport(uri);
        }
    }

    private void doImport(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            InputStreamReader reader = new InputStreamReader(is);
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());

            SharedPreferences.Editor editor = prefs.edit();

            if (json.has("kdocs_optimize")) {
                editor.putBoolean("kdocs_optimize", json.getBoolean("kdocs_optimize"));
            }
            if (json.has("night_mode")) {
                editor.putBoolean("night_mode", json.getBoolean("night_mode"));
            }
            if (json.has("night_mode_css")) {
                editor.putBoolean("night_mode_css", json.getBoolean("night_mode_css"));
            }
            if (json.has("page_actions_enabled")) {
                editor.putBoolean("page_actions_enabled", json.getBoolean("page_actions_enabled"));
            }
            if (json.has("auto_refresh_interval")) {
                editor.putInt("auto_refresh_interval", json.getInt("auto_refresh_interval"));
            }
            if (json.has("tab_count")) {
                editor.putInt("tab_count", json.getInt("tab_count"));
            }
            if (json.has("tabs_config")) {
                editor.putString("tabs_config", json.getString("tabs_config"));
            } else if (json.has("tabs")) {
                JSONArray tabsArray = json.getJSONArray("tabs");
                for (int i = 0; i < tabsArray.length(); i++) {
                    JSONObject tab = tabsArray.getJSONObject(i);
                    editor.putString("icon" + (i + 1), tab.getString("icon"));
                    editor.putString("title" + (i + 1), tab.getString("title"));
                    editor.putString("links" + (i + 1), tab.getString("links"));
                }
            }

            editor.apply();
            loadConfig();
            buildUI();
            switchKdocsOptimize.setChecked(prefs.getBoolean("kdocs_optimize", true));
            switchNightModeCSS.setChecked(prefs.getBoolean("night_mode_css", false));
            switchPageActions.setChecked(prefs.getBoolean("page_actions_enabled", true));

            Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCacheSize(TextView textView) {
        long size = getCacheSize();
        if (size < 1024) {
            textView.setText(String.format("当前缓存: %d B", size));
        } else if (size < 1024 * 1024) {
            textView.setText(String.format("当前缓存: %.1f KB", size / 1024.0));
        } else {
            textView.setText(String.format("当前缓存: %.1f MB", size / (1024.0 * 1024.0)));
        }
    }

    private long getCacheSize() {
        long size = 0;
        try {
            size += getDirSize(getCacheDir());
            File webviewDir = new File(getFilesDir(), "../app_webview");
            if (webviewDir.exists()) size += getDirSize(webviewDir);
        } catch (Exception e) {}
        return size;
    }

    private long getDirSize(File dir) {
        long size = 0;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    size += file.isFile() ? file.length() : getDirSize(file);
                }
            }
        }
        return size;
    }

    private void clearAllCache() {
        try {
            android.webkit.WebView webView = new android.webkit.WebView(this);
            webView.clearCache(true);
            webView.clearHistory();
            webView.destroy();
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            WebStorage.getInstance().deleteAllData();
            deleteDir(getCacheDir());
            File webviewDir = new File(getFilesDir(), "../app_webview");
            if (webviewDir.exists()) deleteDir(webviewDir);
        } catch (Exception e) {}
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
        }
        return dir != null && dir.delete();
    }

    private void loadConfig() {
        tabsData.clear();

        int tabCount = prefs.getInt("tab_count", 3);
        if (tabCount < 2) tabCount = 2;
        if (tabCount > 8) tabCount = 8;

        String tabsJson = prefs.getString("tabs_config", "");
        if (!tabsJson.isEmpty() && loadConfigFromJson(tabsJson, tabCount)) {
            return;
        }

        for (int i = 0; i < tabCount; i++) {
            TabData tab = new TabData();
            tab.icon = prefs.getString("icon" + (i + 1), DEFAULT_TAB_ICONS[i]);
            tab.title = prefs.getString("title" + (i + 1), DEFAULT_TAB_TITLES[i]);

            String linksStr = prefs.getString("links" + (i + 1), "");
            if (linksStr.isEmpty()) {
                LinkData link = new LinkData();
                link.title = DEFAULT_TAB_TITLES[i];
                link.url = "about:blank";
                tab.links.add(link);
            } else {
                String[] lines = linksStr.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    LinkData link = new LinkData();
                    String[] parts = line.split("\\|", 2);
                    String titleUrl = parts[0];
                    String actionsStr = parts.length > 1 ? parts[1] : "";

                    String[] titleUrlParts = titleUrl.split(",", 4);
                    if (titleUrlParts.length >= 2) {
                        link.title = titleUrlParts[0].trim();
                        link.url = titleUrlParts[1].trim();
                        link.scope = titleUrlParts.length > 2 ? titleUrlParts[2].trim() : "link";
                        // 第4个字段为桌面模式标记
                        if (titleUrlParts.length > 3 && "1".equals(titleUrlParts[3].trim())) {
                            link.desktopMode = true;
                        }
                    }

                    if (!actionsStr.isEmpty()) {
                        // 用分号分隔多个操作
                        String[] actionGroups = actionsStr.split(";");
                        for (String group : actionGroups) {
                            group = group.trim();
                            if (group.isEmpty()) continue;

                            String[] actionParts = group.split("\\|");
                            if (actionParts.length < 2) continue;

                            ActionData action = new ActionData();
                            action.type = actionParts[0];
                            action.selector = actionParts[1];
                            action.value = "";
                            action.remark = "";
                            action.delay = 0;

                            // 解析剩余部分
                            for (int k = 2; k < actionParts.length; k++) {
                                String part = actionParts[k];
                                if (part.startsWith("@")) {
                                    // 备注（反转义特殊字符）
                                    action.remark = part.substring(1)
                                        .replace("｜", "|")
                                        .replace("；", ";");
                                } else if ("click".equals(action.type)) {
                                    // 延迟
                                    try { action.delay = Integer.parseInt(part); } catch (Exception e) {}
                                } else if ("modify".equals(action.type)) {
                                    // 新值
                                    action.value = part;
                                }
                            }

                            link.actions.add(action);
                        }
                    }

                    tab.links.add(link);
                }
            }

            tabsData.add(tab);
        }
    }

    private boolean loadConfigFromJson(String tabsJson, int tabCount) {
        try {
            JSONArray tabsArray = new JSONArray(tabsJson);
            int count = Math.max(2, Math.min(8, Math.min(tabCount, tabsArray.length())));
            for (int i = 0; i < count; i++) {
                JSONObject tabJson = tabsArray.getJSONObject(i);
                TabData tab = new TabData();
                tab.icon = tabJson.optString("icon", DEFAULT_TAB_ICONS[i]);
                tab.title = tabJson.optString("title", DEFAULT_TAB_TITLES[i]);

                JSONArray linksArray = tabJson.optJSONArray("links");
                if (linksArray != null) {
                    for (int j = 0; j < linksArray.length(); j++) {
                        JSONObject linkJson = linksArray.getJSONObject(j);
                        LinkData link = new LinkData();
                        link.title = linkJson.optString("title", "");
                        link.url = linkJson.optString("url", "");
                        link.scope = linkJson.optString("scope", "link");
                        link.desktopMode = linkJson.optBoolean("desktopMode", false);
                        if (link.title.isEmpty() || link.url.isEmpty()) continue;

                        JSONArray actionsArray = linkJson.optJSONArray("actions");
                        if (actionsArray != null) {
                            for (int k = 0; k < actionsArray.length(); k++) {
                                JSONObject actionJson = actionsArray.getJSONObject(k);
                                ActionData action = new ActionData();
                                action.type = actionJson.optString("type", "hide");
                                action.selector = actionJson.optString("selector", "");
                                action.value = actionJson.optString("value", "");
                                action.remark = actionJson.optString("remark", "");
                                action.delay = actionJson.optInt("delay", 0);
                                if (!action.selector.isEmpty()) {
                                    link.actions.add(action);
                                }
                            }
                        }
                        tab.links.add(link);
                    }
                }

                if (tab.links.isEmpty()) {
                    LinkData link = new LinkData();
                    link.title = tab.title;
                    link.url = "about:blank";
                    tab.links.add(link);
                }
                tabsData.add(tab);
            }
            return !tabsData.isEmpty();
        } catch (Exception e) {
            tabsData.clear();
            return false;
        }
    }

    private boolean isEditMode = false;

    private void buildUI() {
        settingsContainer.removeAllViews();
        isEditMode = false;

        for (int i = 0; i < tabsData.size(); i++) {
            TabData tab = tabsData.get(i);
            final int tabIndex = i;

            // ========== 第一级：工作区 ==========
            View tabView = LayoutInflater.from(this).inflate(R.layout.item_tab_level1, settingsContainer, false);
            tab.sectionView = tabView;

            TextView tvArrow = tabView.findViewById(R.id.tvArrow);
            TextView tvTabIcon = tabView.findViewById(R.id.tvTabIcon);
            TextView tvTabTitle = tabView.findViewById(R.id.tvTabTitle);
            EditText etTabIcon = tabView.findViewById(R.id.etTabIcon);
            EditText etTabTitle = tabView.findViewById(R.id.etTabTitle);
            TextView btnDeleteTab = tabView.findViewById(R.id.btnDeleteTab);
            LinearLayout linksContainer = tabView.findViewById(R.id.linksContainer);
            TextView btnAddLink = tabView.findViewById(R.id.btnAddLink);

            tvTabIcon.setText(tab.icon);
            tvTabTitle.setText(tab.title);
            etTabIcon.setText(tab.icon);
            etTabTitle.setText(tab.title);
            tab.linksContainer = linksContainer;

            // 确认编辑按钮
            TextView btnConfirmEdit = tabView.findViewById(R.id.btnConfirmEdit);
            btnConfirmEdit.setOnClickListener(v -> {
                toggleEditMode(tab, tvTabIcon, tvTabTitle, etTabIcon, etTabTitle,
                        linksContainer, btnAddLink, tvArrow, btnDeleteTab, btnConfirmEdit, false);
            });



            // 拖动排序（工作区）
            TextView btnDrag = tabView.findViewById(R.id.btnDrag);
            if (btnDrag != null) {
                btnDrag.setOnLongClickListener(v -> {
                    // 创建拖动阴影（蓝色底色更醒目）
                    View.DragShadowBuilder shadow = new View.DragShadowBuilder(tabView) {
                        @Override
                        public void onDrawShadow(android.graphics.Canvas canvas) {
                            // 画蓝色背景
                            android.graphics.Paint paint = new android.graphics.Paint();
                            paint.setColor(Color.parseColor("#1976D2"));
                            paint.setAlpha(180);
                            canvas.drawRect(0, 0, tabView.getWidth(), tabView.getHeight(), paint);
                            // 画原始内容
                            tabView.draw(canvas);
                        }

                        @Override
                        public void onProvideShadowMetrics(android.graphics.Point outShadowSize, android.graphics.Point outShadowTouchPoint) {
                            outShadowSize.set(tabView.getWidth(), tabView.getHeight());
                            outShadowTouchPoint.set(tabView.getWidth() / 2, tabView.getHeight() / 2);
                        }
                    };

                    // 开始拖动
                    tabView.startDrag(null, shadow, tab, 0);
                    tabView.setAlpha(0.3f);
                    return true;
                });

                // 阻止触摸事件传递到父视图
                btnDrag.setOnTouchListener((v, event) -> {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return false;
                });

                // 设置拖动监听器
                tabView.setOnDragListener((v, event) -> {
                    switch (event.getAction()) {
                        case DragEvent.ACTION_DRAG_STARTED:
                            // 只接受工作区类型的拖动
                            return event.getLocalState() instanceof TabData;
                        case DragEvent.ACTION_DRAG_ENTERED:
                            v.setBackgroundColor(Color.parseColor("#BBDEFB"));
                            return true;
                        case DragEvent.ACTION_DRAG_EXITED:
                            v.setBackgroundColor(Color.WHITE);
                            return true;
                        case DragEvent.ACTION_DROP:
                            v.setBackgroundColor(Color.WHITE);
                            Object dropState = event.getLocalState();
                            if (dropState instanceof TabData) {
                                int fromIndex = tabsData.indexOf(dropState);
                                int toIndex = tabsData.indexOf(tab);
                                if (fromIndex != toIndex && fromIndex >= 0 && toIndex >= 0) {
                                    TabData temp = tabsData.remove(fromIndex);
                                    tabsData.add(toIndex, temp);
                                    // 延迟重建UI，防止拖拽卡死
                                    v.postDelayed(() -> buildUI(), 100);
                                }
                            }
                            return true;
                        case DragEvent.ACTION_DRAG_ENDED:
                            v.setAlpha(1.0f);
                            v.setBackgroundColor(Color.WHITE);
                            return true;
                    }
                    return false;
                });
            }

            // 更多选项按钮
            if (tabsData.size() > 2) {
                btnDeleteTab.setVisibility(View.VISIBLE);
                btnDeleteTab.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(this, v);
                    popup.getMenu().add(0, 1, 0, "编辑工作区");
                    popup.getMenu().add(0, 2, 0, "删除工作区");
                    popup.setOnMenuItemClickListener(item -> {
                        if (item.getItemId() == 1) {
                            // 进入编辑模式
                            toggleEditMode(tab, tvTabIcon, tvTabTitle, etTabIcon, etTabTitle,
                                    linksContainer, btnAddLink, tvArrow, btnDeleteTab, btnConfirmEdit, true);
                        } else if (item.getItemId() == 2) {
                            new AlertDialog.Builder(this)
                                    .setTitle("删除工作区")
                                    .setMessage("确定删除「" + tab.title + "」？\n删除后无法恢复。")
                                    .setPositiveButton("删除", (d, w) -> {
                                        tabsData.remove(tab);
                                        buildUI();
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                        }
                        return true;
                    });
                    popup.show();
                });
            } else {
                btnDeleteTab.setVisibility(View.GONE);
            }

            // 点击标题栏：展开/收起链接列表（不进入编辑模式）
            tabView.findViewById(R.id.btnToggle).setOnClickListener(v -> {
                tab.isExpanded = !tab.isExpanded;
                linksContainer.setVisibility(tab.isExpanded ? View.VISIBLE : View.GONE);
                btnAddLink.setVisibility(tab.isExpanded ? View.VISIBLE : View.GONE);
                tvArrow.setText(tab.isExpanded ? "▼" : "▶");
                // 展开时隐藏拖动按钮，折叠时显示
                if (btnDrag != null) {
                    btnDrag.setVisibility(tab.isExpanded ? View.GONE : View.VISIBLE);
                }
            });

            // 添加链接
            btnAddLink.setOnClickListener(v -> {
                LinkData newLink = new LinkData();
                newLink.title = "";
                newLink.url = "";
                tab.links.add(newLink);
                addLinkCard(tab, newLink);
            });

            // 添加已有链接卡片
            for (LinkData link : tab.links) {
                addLinkCard(tab, link);
            }

            settingsContainer.addView(tabView);
        }

        // 添加工作区按钮
        if (tabsData.size() < 8) {
            TextView btnAddTab = new TextView(this);
            btnAddTab.setText("＋ 添加工作区");
            btnAddTab.setTextSize(14);
            btnAddTab.setTextColor(Color.parseColor("#1976D2"));
            btnAddTab.setGravity(Gravity.CENTER);
            btnAddTab.setPadding(0, dpToPx(16), 0, dpToPx(16));
            btnAddTab.setBackgroundResource(R.drawable.btn_add_link);
            btnAddTab.setOnClickListener(v -> {
                TabData newTab = new TabData();
                newTab.icon = "📌";
                newTab.title = "新工作区";
                tabsData.add(newTab);
                buildUI();
            });
            settingsContainer.addView(btnAddTab);
        }
    }



    private void toggleEditMode(TabData tab, TextView tvTabIcon, TextView tvTabTitle, EditText etTabIcon, EditText etTabTitle,
                                LinearLayout linksContainer, TextView btnAddLink, TextView tvArrow,
                                TextView btnDeleteTab, TextView btnConfirmEdit, boolean editMode) {
        if (editMode) {
            // 进入编辑模式
            tvTabIcon.setVisibility(View.GONE);
            tvTabTitle.setVisibility(View.GONE);
            etTabIcon.setVisibility(View.VISIBLE);
            etTabTitle.setVisibility(View.VISIBLE);
            btnDeleteTab.setVisibility(View.GONE);
            btnConfirmEdit.setVisibility(View.VISIBLE);
        } else {
            // 退出编辑模式
            String icon = etTabIcon.getText().toString().trim();
            String title = etTabTitle.getText().toString().trim();
            if (!icon.isEmpty()) tab.icon = icon;
            if (!title.isEmpty()) tab.title = title;

            tvTabIcon.setText(tab.icon);
            tvTabTitle.setText(tab.title);

            tvTabIcon.setVisibility(View.VISIBLE);
            tvTabTitle.setVisibility(View.VISIBLE);
            etTabIcon.setVisibility(View.GONE);
            etTabTitle.setVisibility(View.GONE);
            btnDeleteTab.setVisibility(tabsData.size() > 2 ? View.VISIBLE : View.GONE);
            btnConfirmEdit.setVisibility(View.GONE);
        }

        // 展开链接列表
        tab.isExpanded = editMode;
        linksContainer.setVisibility(editMode ? View.VISIBLE : View.GONE);
        btnAddLink.setVisibility(editMode ? View.VISIBLE : View.GONE);
        tvArrow.setText(editMode ? "▼" : "▶");

        // 编辑模式时隐藏拖动按钮
        View sectionView = tab.sectionView;
        if (sectionView != null) {
            TextView btnDrag = sectionView.findViewById(R.id.btnDrag);
            if (btnDrag != null) {
                btnDrag.setVisibility(editMode ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void addLinkCard(TabData tab, LinkData link) {
        // ========== 第二级：链接 ==========
        View cardView = LayoutInflater.from(this).inflate(R.layout.item_tab_level2, tab.linksContainer, false);
        link.cardView = cardView;

        EditText etLinkTitle = cardView.findViewById(R.id.etLinkTitle);
        EditText etLinkUrl = cardView.findViewById(R.id.etLinkUrl);
        TextView btnDeleteLink = cardView.findViewById(R.id.btnDeleteLink);
        LinearLayout actionsContainer = cardView.findViewById(R.id.actionsContainer);
        TextView btnAddAction = cardView.findViewById(R.id.btnAddAction);
        Switch switchDesktopMode = cardView.findViewById(R.id.switchDesktopMode);
        LinearLayout actionHeader = cardView.findViewById(R.id.actionHeader);
        TextView tvActionArrow = cardView.findViewById(R.id.tvActionArrow);

        etLinkTitle.setText(link.title);
        etLinkUrl.setText(link.url);
        link.actionsContainer = actionsContainer;
        link.switchDesktopMode = switchDesktopMode;
        switchDesktopMode.setChecked(link.desktopMode);

        // 操作折叠/展开（默认折叠）
        tvActionArrow.setText("▶");
        actionsContainer.setVisibility(View.GONE);
        actionHeader.setOnClickListener(v -> {
            if (actionsContainer.getVisibility() == View.VISIBLE) {
                actionsContainer.setVisibility(View.GONE);
                tvActionArrow.setText("▶");
            } else {
                actionsContainer.setVisibility(View.VISIBLE);
                tvActionArrow.setText("▼");
            }
        });

        // 生效范围
        Spinner spinnerScope = cardView.findViewById(R.id.spinnerScope);
        String[] scopeOptions = {"仅此链接", "相似域名", "当前工作区", "所有工作区"};
        ArrayAdapter<String> scopeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, scopeOptions);
        scopeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerScope.setAdapter(scopeAdapter);

        // 设置当前选中的生效范围
        int scopeIndex = 0;
        if ("domain".equals(link.scope)) scopeIndex = 1;
        else if ("tab".equals(link.scope)) scopeIndex = 2;
        else if ("all".equals(link.scope)) scopeIndex = 3;
        spinnerScope.setSelection(scopeIndex);

        // 监听生效范围变化
        spinnerScope.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: link.scope = "link"; break;
                    case 1: link.scope = "domain"; break;
                    case 2: link.scope = "tab"; break;
                    case 3: link.scope = "all"; break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 拖动排序（链接）
        TextView btnDragLink = cardView.findViewById(R.id.btnDragLink);
        if (btnDragLink != null) {
            btnDragLink.setOnLongClickListener(v -> {
                // 创建拖动阴影（蓝色底色更醒目）
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(cardView) {
                    @Override
                    public void onDrawShadow(android.graphics.Canvas canvas) {
                        // 画蓝色背景
                        android.graphics.Paint paint = new android.graphics.Paint();
                        paint.setColor(Color.parseColor("#1976D2"));
                        paint.setAlpha(180);
                        canvas.drawRect(0, 0, cardView.getWidth(), cardView.getHeight(), paint);
                        // 画原始内容
                        cardView.draw(canvas);
                    }

                    @Override
                    public void onProvideShadowMetrics(android.graphics.Point outShadowSize, android.graphics.Point outShadowTouchPoint) {
                        outShadowSize.set(cardView.getWidth(), cardView.getHeight());
                        outShadowTouchPoint.set(cardView.getWidth() / 2, cardView.getHeight() / 2);
                    }
                };

                cardView.startDrag(null, shadow, link, 0);
                cardView.setAlpha(0.3f);
                return true;
            });

            cardView.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        // 只接受链接类型的拖动
                        return event.getLocalState() instanceof LinkData;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        v.setBackgroundColor(Color.parseColor("#BBDEFB"));
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        v.setBackgroundColor(Color.TRANSPARENT);
                        return true;
                    case DragEvent.ACTION_DROP:
                        v.setBackgroundColor(Color.TRANSPARENT);
                        Object dropState = event.getLocalState();
                        if (dropState instanceof LinkData) {
                            int fromLinkIndex = tab.links.indexOf(dropState);
                            int toLinkIndex = tab.links.indexOf(link);
                            if (fromLinkIndex != toLinkIndex && fromLinkIndex >= 0 && toLinkIndex >= 0) {
                                LinkData temp = tab.links.remove(fromLinkIndex);
                                tab.links.add(toLinkIndex, temp);
                                // 延迟重建链接列表，防止拖拽卡死
                                v.postDelayed(() -> {
                                    tab.linksContainer.removeAllViews();
                                    for (LinkData l : tab.links) {
                                        addLinkCard(tab, l);
                                    }
                                }, 100);
                            }
                        }
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        v.setAlpha(1.0f);
                        v.setBackgroundColor(Color.TRANSPARENT);
                        return true;
                }
                return false;
            });
        }

        // 更多选项按钮 (替代直接删除)
        btnDeleteLink.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, "删除此链接");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    new AlertDialog.Builder(this)
                            .setTitle("删除链接")
                            .setMessage("确定删除「" + (link.title.isEmpty() ? "未命名链接" : link.title) + "」？\n关联的操作也会被删除。")
                            .setPositiveButton("删除", (d, w) -> {
                                tab.links.remove(link);
                                tab.linksContainer.removeView(cardView);
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }
                return true;
            });
            popup.show();
        });

        // 添加操作
        btnAddAction.setOnClickListener(v -> {
            ActionData newAction = new ActionData();
            newAction.type = "hide";
            newAction.selector = "";
            newAction.value = "";
            link.actions.add(newAction);
            addActionRow(link, newAction);
        });

        // 添加已有操作
        for (ActionData action : link.actions) {
            addActionRow(link, action);
        }

        tab.linksContainer.addView(cardView);
    }

    private void updateActionFields(int position, LinearLayout layoutDelay, EditText etValue) {
        String[] types = {"hide", "click", "modify", "script"};
        String type = (position >= 0 && position < types.length) ? types[position] : "hide";

        switch (type) {
            case "hide":
                layoutDelay.setVisibility(View.GONE);
                etValue.setVisibility(View.GONE);
                break;
            case "click":
                layoutDelay.setVisibility(View.VISIBLE);
                etValue.setVisibility(View.GONE);
                break;
            case "modify":
                layoutDelay.setVisibility(View.GONE);
                etValue.setVisibility(View.VISIBLE);
                etValue.setHint("新值");
                etValue.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                etValue.setMaxLines(1);
                etValue.setMinLines(1);
                break;
            case "script":
                layoutDelay.setVisibility(View.VISIBLE);
                etValue.setVisibility(View.VISIBLE);
                etValue.setHint("输入JS代码");
                etValue.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                etValue.setMaxLines(6);
                etValue.setMinLines(3);
                break;
        }
    }

    private void addActionRow(LinkData link, ActionData action) {
        // ========== 第三级：操作 ==========
        View row = LayoutInflater.from(this).inflate(R.layout.item_tab_level3, link.actionsContainer, false);
        action.actionView = row;

        Spinner spinnerAction = row.findViewById(R.id.spinnerAction);
        EditText etSelector = row.findViewById(R.id.etSelector);
        EditText etRemark = row.findViewById(R.id.etRemark);
        EditText etValue = row.findViewById(R.id.etValue);
        LinearLayout layoutDelay = row.findViewById(R.id.layoutDelay);
        EditText etDelay = row.findViewById(R.id.etDelay);
        TextView btnDelete = row.findViewById(R.id.btnDelete);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ACTION_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAction.setAdapter(adapter);

        int actionIndex = 0;
        if ("hide".equals(action.type)) actionIndex = 0;
        else if ("click".equals(action.type)) actionIndex = 1;
        else if ("modify".equals(action.type)) actionIndex = 2;
        else if ("script".equals(action.type)) actionIndex = 3;
        spinnerAction.setSelection(actionIndex);

        etSelector.setText(action.selector);
        etRemark.setText(action.remark);
        etValue.setText(action.value);
        etDelay.setText(String.valueOf(action.delay));

        // 脚本类型：选择器可选，提示可为空
        if ("script".equals(action.type)) {
            etSelector.setHint("选择器（可选）");
        }

        // 根据类型显示/隐藏相关字段
        updateActionFields(actionIndex, layoutDelay, etValue);

        spinnerAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateActionFields(position, layoutDelay, etValue);
                // 脚本类型时选择器可选
                if (position == 3) {
                    etSelector.setHint("选择器（可选）");
                } else {
                    etSelector.setHint("选择器");
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 删除操作（直接删除，无需确认）
        btnDelete.setOnClickListener(v -> {
            link.actions.remove(action);
            link.actionsContainer.removeView(row);
        });

        link.actionsContainer.addView(row);
    }

    private void saveConfig() {
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray tabsJson = buildTabsJsonFromUi();

        for (int i = 0; i < tabsData.size(); i++) {
            TabData tab = tabsData.get(i);

            // 使用 tab 对象中的值（可能已被编辑）
            String icon = tab.icon;
            String title = tab.title;

            if (icon.isEmpty()) icon = DEFAULT_TAB_ICONS[i];
            if (title.isEmpty()) title = DEFAULT_TAB_TITLES[i];

            editor.putString("icon" + (i + 1), icon);
            editor.putString("title" + (i + 1), title);


            StringBuilder linksStr = new StringBuilder();
            for (LinkData link : tab.links) {
                EditText etLinkTitle = link.cardView.findViewById(R.id.etLinkTitle);
                EditText etLinkUrl = link.cardView.findViewById(R.id.etLinkUrl);

                String linkTitle = etLinkTitle.getText().toString().trim();
                String linkUrl = etLinkUrl.getText().toString().trim();

                if (linkTitle.isEmpty() || linkUrl.isEmpty()) continue;

                if (linksStr.length() > 0) linksStr.append("\n");
                linksStr.append(linkTitle).append(",").append(linkUrl).append(",").append(link.scope);
                if (link.desktopMode) {
                    linksStr.append(",1");
                }

                StringBuilder actionsStr = new StringBuilder();
                boolean hasActions = false;
                for (ActionData action : link.actions) {
                    if (action.actionView == null) continue;

                    Spinner spinner = action.actionView.findViewById(R.id.spinnerAction);
                    EditText etSelector = action.actionView.findViewById(R.id.etSelector);
                    EditText etRemark = action.actionView.findViewById(R.id.etRemark);
                    EditText etValue = action.actionView.findViewById(R.id.etValue);
                    EditText etDelay = action.actionView.findViewById(R.id.etDelay);

                    String selector = etSelector.getText().toString().trim();
                    if (selector.isEmpty()) continue;

                    String remark = etRemark.getText().toString().trim();

                    String type;
                    int pos = spinner.getSelectedItemPosition();
                    if (pos == 0) type = "hide";
                    else if (pos == 1) type = "click";
                    else if (pos == 3) type = "script";
                    else type = "modify";

                    String value = etValue.getText().toString().trim();
                    int delay = 0;
                    try { delay = Integer.parseInt(etDelay.getText().toString().trim()); } catch (Exception e) {}

                    // 脚本类型：selector可为空
                    if ("script".equals(type) && selector.isEmpty()) {
                        // 允许空选择器
                    } else if (selector.isEmpty()) {
                        continue;
                    }

                    if (actionsStr.length() > 0) actionsStr.append(";");
                    actionsStr.append(type).append("|").append(selector);
                    if ("click".equals(type) || "script".equals(type)) {
                        if (delay > 0) actionsStr.append("|").append(delay);
                    }
                    if (("modify".equals(type) || "script".equals(type)) && !value.isEmpty()) {
                        actionsStr.append("|").append(value);
                    }
                    if (!remark.isEmpty()) {
                        // 转义备注中的特殊字符
                        String safeRemark = remark.replace("|", "｜").replace(";", "；");
                        actionsStr.append("|@").append(safeRemark);
                    }
                    hasActions = true;
                }

                if (hasActions) {
                    linksStr.append("|").append(actionsStr);
                }
            }

            editor.putString("links" + (i + 1), linksStr.toString());
        }

        editor.putInt("tab_count", tabsData.size());
        editor.putString("tabs_config", tabsJson.toString());
        editor.putBoolean("kdocs_optimize", switchKdocsOptimize.isChecked());
        editor.putBoolean("night_mode_css", switchNightModeCSS.isChecked());
        editor.putBoolean("page_actions_enabled", switchPageActions.isChecked());

        // 保存 UA
        int uaPos = spinnerUA.getSelectedItemPosition();
        editor.putString("user_agent", uaPos >= 0 && uaPos < UA_VALUES.length ? UA_VALUES[uaPos] : "");

        editor.apply();
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        // 通知 MainActivity 设置已变更（Feature 7）
        setResult(RESULT_OK, new Intent().putExtra("settings_changed", true));
        finish();
    }

    private JSONArray buildTabsJsonFromUi() {
        syncUiToData();
        return buildTabsJson(tabsData);
    }

    private JSONArray buildTabsJsonFromPrefs() {
        List<TabData> data = new ArrayList<>();
        int tabCount = prefs.getInt("tab_count", 3);
        if (tabCount < 2) tabCount = 2;
        if (tabCount > 8) tabCount = 8;

        for (int i = 0; i < tabCount; i++) {
            TabData tab = new TabData();
            tab.icon = prefs.getString("icon" + (i + 1), DEFAULT_TAB_ICONS[i]);
            tab.title = prefs.getString("title" + (i + 1), DEFAULT_TAB_TITLES[i]);

            String linksStr = prefs.getString("links" + (i + 1), "");
            if (!linksStr.isEmpty()) {
                parseLegacyLinks(tab, linksStr);
            }
            if (tab.links.isEmpty()) {
                LinkData link = new LinkData();
                link.title = tab.title;
                link.url = "about:blank";
                tab.links.add(link);
            }
            data.add(tab);
        }
        return buildTabsJson(data);
    }

    private JSONArray buildTabsJson(List<TabData> data) {
        JSONArray tabsArray = new JSONArray();
        try {
            for (TabData tab : data) {
                JSONObject tabJson = new JSONObject();
                tabJson.put("icon", tab.icon);
                tabJson.put("title", tab.title);


                JSONArray linksArray = new JSONArray();
                for (LinkData link : tab.links) {
                    if (link.title == null || link.title.isEmpty() || link.url == null || link.url.isEmpty()) continue;

                    JSONObject linkJson = new JSONObject();
                    linkJson.put("title", link.title);
                    linkJson.put("url", link.url);
                    linkJson.put("scope", link.scope == null || link.scope.isEmpty() ? "link" : link.scope);
                    linkJson.put("desktopMode", link.desktopMode);

                    JSONArray actionsArray = new JSONArray();
                    for (ActionData action : link.actions) {
                        if (action.selector == null || action.selector.isEmpty()) continue;

                        JSONObject actionJson = new JSONObject();
                        actionJson.put("type", action.type == null || action.type.isEmpty() ? "hide" : action.type);
                        actionJson.put("selector", action.selector);
                        actionJson.put("value", action.value == null ? "" : action.value);
                        actionJson.put("remark", action.remark == null ? "" : action.remark);
                        actionJson.put("delay", Math.max(0, action.delay));
                        actionsArray.put(actionJson);
                    }
                    linkJson.put("actions", actionsArray);
                    linksArray.put(linkJson);
                }
                tabJson.put("links", linksArray);
                tabsArray.put(tabJson);
            }
        } catch (Exception e) {}
        return tabsArray;
    }

    private void syncUiToData() {
        for (TabData tab : tabsData) {
            if (tab.sectionView != null) {
                EditText etTabIcon = tab.sectionView.findViewById(R.id.etTabIcon);
                EditText etTabTitle = tab.sectionView.findViewById(R.id.etTabTitle);
                String icon = etTabIcon.getText().toString().trim();
                String title = etTabTitle.getText().toString().trim();
                if (!icon.isEmpty()) tab.icon = icon;
                if (!title.isEmpty()) tab.title = title;
            }

            for (LinkData link : tab.links) {
                if (link.cardView == null) continue;
                EditText etLinkTitle = link.cardView.findViewById(R.id.etLinkTitle);
                EditText etLinkUrl = link.cardView.findViewById(R.id.etLinkUrl);
                link.title = etLinkTitle.getText().toString().trim();
                link.url = etLinkUrl.getText().toString().trim();
                if (link.switchDesktopMode != null) {
                    link.desktopMode = link.switchDesktopMode.isChecked();
                }

                for (ActionData action : link.actions) {
                    if (action.actionView == null) continue;
                    Spinner spinner = action.actionView.findViewById(R.id.spinnerAction);
                    EditText etSelector = action.actionView.findViewById(R.id.etSelector);
                    EditText etRemark = action.actionView.findViewById(R.id.etRemark);
                    EditText etValue = action.actionView.findViewById(R.id.etValue);
                    EditText etDelay = action.actionView.findViewById(R.id.etDelay);

                    int pos = spinner.getSelectedItemPosition();
                    if (pos == 0) action.type = "hide";
                    else if (pos == 1) action.type = "click";
                    else if (pos == 3) action.type = "script";
                    else action.type = "modify";

                    action.selector = etSelector.getText().toString().trim();
                    action.remark = etRemark.getText().toString().trim();
                    action.value = etValue.getText().toString().trim();
                    try {
                        action.delay = Integer.parseInt(etDelay.getText().toString().trim());
                    } catch (Exception e) {
                        action.delay = 0;
                    }
                }
            }
        }
    }

    private void parseLegacyLinks(TabData tab, String linksStr) {
        String[] lines = linksStr.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            LinkData link = new LinkData();
            String[] parts = line.split("\\|", 2);
            String titleUrl = parts[0];
            String actionsStr = parts.length > 1 ? parts[1] : "";

            String[] titleUrlParts = titleUrl.split(",", 4);
            if (titleUrlParts.length >= 2) {
                link.title = titleUrlParts[0].trim();
                link.url = titleUrlParts[1].trim();
                link.scope = titleUrlParts.length > 2 ? titleUrlParts[2].trim() : "link";
                // 第4个字段为桌面模式标记
                if (titleUrlParts.length > 3 && "1".equals(titleUrlParts[3].trim())) {
                    link.desktopMode = true;
                }
            }
            parseLegacyActions(link, actionsStr);
            if (link.title != null && !link.title.isEmpty() && link.url != null && !link.url.isEmpty()) {
                tab.links.add(link);
            }
        }
    }

    private void parseLegacyActions(LinkData link, String actionsStr) {
        if (actionsStr == null || actionsStr.isEmpty()) return;

        String[] actionGroups = actionsStr.split(";");
        for (String group : actionGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;

            String[] actionParts = group.split("\\|");
            if (actionParts.length < 2) continue;

            ActionData action = new ActionData();
            action.type = actionParts[0];
            action.selector = actionParts[1];
            action.value = "";
            action.remark = "";
            action.delay = 0;

            for (int k = 2; k < actionParts.length; k++) {
                String part = actionParts[k];
                if (part.startsWith("@")) {
                    action.remark = part.substring(1)
                            .replace("｜", "|")
                            .replace("；", ";");
                } else if ("click".equals(action.type)) {
                    try { action.delay = Integer.parseInt(part); } catch (Exception e) {}
                } else if ("modify".equals(action.type)) {
                    action.value = part;
                }
            }
            link.actions.add(action);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
