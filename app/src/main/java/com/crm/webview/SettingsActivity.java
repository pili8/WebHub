package com.crm.webview;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import java.io.FileWriter;
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
    private SharedPreferences prefs;

    private static final String[] ACTION_TYPES = {"隐藏", "点击", "修改"};
    private static final String[] DEFAULT_TAB_ICONS = {"📊", "📋", "➕", "📁", "👤"};
    private static final String[] DEFAULT_TAB_TITLES = {"销售机会", "最近新增", "录入线索", "选项卡4", "选项卡5"};

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
        List<ActionData> actions = new ArrayList<>();
        View cardView;
        LinearLayout actionsContainer;
    }

    static class ActionData {
        String type;
        String selector;
        String value;
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
        switchPageActionsAll = findViewById(R.id.switchPageActionsAll);

        switchKdocsOptimize.setChecked(prefs.getBoolean("kdocs_optimize", true));
        switchNightModeCSS.setChecked(prefs.getBoolean("night_mode_css", false));
        switchPageActions.setChecked(prefs.getBoolean("page_actions_enabled", true));

        // 检查是否夜间模式
        boolean isNightMode = prefs.getBoolean("night_mode", false);
        if (isNightMode) {
            applyDarkTheme();
        }

        setupCache();
        setupExportImport();

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

            // 导出通用设置
            json.put("kdocs_optimize", prefs.getBoolean("kdocs_optimize", true));
            json.put("tab_count", prefs.getInt("tab_count", 3));

            // 导出选项卡配置
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
            if (json.has("tab_count")) {
                editor.putInt("tab_count", json.getInt("tab_count"));
            }
            if (json.has("tabs")) {
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
        if (tabCount > 5) tabCount = 5;

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

                    String[] titleUrlParts = titleUrl.split(",", 3);
                    if (titleUrlParts.length >= 2) {
                        link.title = titleUrlParts[0].trim();
                        link.url = titleUrlParts[1].trim();
                        link.scope = titleUrlParts.length > 2 ? titleUrlParts[2].trim() : "link";
                    }

                    if (!actionsStr.isEmpty()) {
                        String[] actionParts = actionsStr.split("\\|");
                        for (int j = 0; j < actionParts.length; ) {
                            if (j + 1 >= actionParts.length) break;

                            ActionData action = new ActionData();
                            action.type = actionParts[j];
                            action.selector = actionParts[j + 1];
                            action.value = "";
                            action.delay = 0;

                            if ("click".equals(action.type)) {
                                if (j + 2 < actionParts.length) {
                                    try { action.delay = Integer.parseInt(actionParts[j + 2]); } catch (Exception e) {}
                                }
                                j += 3;
                            } else if ("modify".equals(action.type)) {
                                if (j + 2 < actionParts.length) action.value = actionParts[j + 2];
                                j += 3;
                            } else {
                                j += 2;
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

    private boolean isEditMode = false;

    private void buildUI() {
        settingsContainer.removeAllViews();
        isEditMode = false;

        for (int i = 0; i < tabsData.size(); i++) {
            TabData tab = tabsData.get(i);
            final int tabIndex = i;

            // ========== 第一级：选项卡 ==========
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

            // 拖动排序（选项卡）
            TextView btnDrag = tabView.findViewById(R.id.btnDrag);
            if (btnDrag != null) {
                btnDrag.setOnLongClickListener(v -> {
                    // 创建拖动阴影
                    View.DragShadowBuilder shadow = new View.DragShadowBuilder(tabView) {
                        @Override
                        public void onDrawShadow(android.graphics.Canvas canvas) {
                            tabView.draw(canvas);
                        }

                        @Override
                        public void onProvideShadowMetrics(android.graphics.Point outShadowSize, android.graphics.Point outShadowTouchPoint) {
                            outShadowSize.set(tabView.getWidth(), tabView.getHeight());
                            outShadowTouchPoint.set(tabView.getWidth() / 2, tabView.getHeight() / 2);
                        }
                    };

                    // 开始拖动
                    tabView.startDrag(null, shadow, new int[]{tabIndex, 0}, 0);
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
                            // 只接受选项卡类型的拖动
                            int[] state = (int[]) event.getLocalState();
                            return state != null && state.length > 1 && state[1] == 0;
                        case DragEvent.ACTION_DRAG_ENTERED:
                            v.setBackgroundColor(Color.parseColor("#E3F2FD"));
                            return true;
                        case DragEvent.ACTION_DRAG_EXITED:
                            v.setBackgroundColor(Color.WHITE);
                            return true;
                        case DragEvent.ACTION_DROP:
                            v.setBackgroundColor(Color.WHITE);
                            int[] dropState = (int[]) event.getLocalState();
                            if (dropState != null && dropState[1] == 0) {
                                int fromIndex = dropState[0];
                                int toIndex = tabIndex;
                                if (fromIndex != toIndex && fromIndex >= 0 && toIndex >= 0) {
                                    TabData temp = tabsData.remove(fromIndex);
                                    tabsData.add(toIndex, temp);
                                    buildUI();
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
                    popup.getMenu().add(0, 1, 0, "编辑选项卡");
                    popup.getMenu().add(0, 2, 0, "删除选项卡");
                    popup.setOnMenuItemClickListener(item -> {
                        if (item.getItemId() == 1) {
                            // 进入编辑模式
                            toggleEditMode(tab, tvTabIcon, tvTabTitle, etTabIcon, etTabTitle,
                                    linksContainer, btnAddLink, tvArrow, btnDeleteTab, btnConfirmEdit, true);
                        } else if (item.getItemId() == 2) {
                            new AlertDialog.Builder(this)
                                    .setTitle("删除选项卡")
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

        // 添加选项卡按钮
        if (tabsData.size() < 5) {
            TextView btnAddTab = new TextView(this);
            btnAddTab.setText("＋ 添加选项卡");
            btnAddTab.setTextSize(14);
            btnAddTab.setTextColor(Color.parseColor("#1976D2"));
            btnAddTab.setGravity(Gravity.CENTER);
            btnAddTab.setPadding(0, dpToPx(16), 0, dpToPx(16));
            btnAddTab.setBackgroundResource(R.drawable.btn_add_link);
            btnAddTab.setOnClickListener(v -> {
                TabData newTab = new TabData();
                newTab.icon = "📌";
                newTab.title = "新选项卡";
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

        etLinkTitle.setText(link.title);
        etLinkUrl.setText(link.url);
        link.actionsContainer = actionsContainer;

        // 生效范围
        Spinner spinnerScope = cardView.findViewById(R.id.spinnerScope);
        String[] scopeOptions = {"仅此链接", "相似域名", "当前选项卡", "所有选项卡"};
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
            final int linkIndex = tab.links.indexOf(link);

            btnDragLink.setOnLongClickListener(v -> {
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(cardView) {
                    @Override
                    public void onDrawShadow(android.graphics.Canvas canvas) {
                        cardView.draw(canvas);
                    }

                    @Override
                    public void onProvideShadowMetrics(android.graphics.Point outShadowSize, android.graphics.Point outShadowTouchPoint) {
                        outShadowSize.set(cardView.getWidth(), cardView.getHeight());
                        outShadowTouchPoint.set(cardView.getWidth() / 2, cardView.getHeight() / 2);
                    }
                };

                cardView.startDrag(null, shadow, new int[]{linkIndex, 1}, 0);
                cardView.setAlpha(0.3f);
                return true;
            });

            cardView.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        // 只接受链接类型的拖动
                        int[] state = (int[]) event.getLocalState();
                        return state != null && state.length > 1 && state[1] == 1;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        v.setBackgroundColor(Color.parseColor("#E3F2FD"));
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        v.setBackgroundColor(Color.TRANSPARENT);
                        return true;
                    case DragEvent.ACTION_DROP:
                        v.setBackgroundColor(Color.TRANSPARENT);
                        int[] dropState = (int[]) event.getLocalState();
                        if (dropState != null && dropState[1] == 1) {
                            int fromLinkIndex = dropState[0];
                            int toLinkIndex = linkIndex;
                            if (fromLinkIndex != toLinkIndex && fromLinkIndex >= 0 && toLinkIndex >= 0) {
                                LinkData temp = tab.links.remove(fromLinkIndex);
                                tab.links.add(toLinkIndex, temp);
                                // 重建链接列表
                                tab.linksContainer.removeAllViews();
                                for (LinkData l : tab.links) {
                                    addLinkCard(tab, l);
                                }
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

    private void addActionRow(LinkData link, ActionData action) {
        // ========== 第三级：操作 ==========
        View row = LayoutInflater.from(this).inflate(R.layout.item_tab_level3, link.actionsContainer, false);
        action.actionView = row;

        Spinner spinnerAction = row.findViewById(R.id.spinnerAction);
        EditText etSelector = row.findViewById(R.id.etSelector);
        EditText etValue = row.findViewById(R.id.etValue);
        LinearLayout layoutDelay = row.findViewById(R.id.layoutDelay);
        LinearLayout layoutValue = row.findViewById(R.id.layoutValue);
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
        spinnerAction.setSelection(actionIndex);

        etSelector.setText(action.selector);
        etValue.setText(action.value);
        etDelay.setText(String.valueOf(action.delay));

        // 根据类型显示/隐藏相关字段
        layoutDelay.setVisibility(actionIndex == 1 ? View.VISIBLE : View.GONE);
        layoutValue.setVisibility(actionIndex == 2 ? View.VISIBLE : View.GONE);

        spinnerAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutDelay.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                layoutValue.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 删除操作 (需要确认)
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(SettingsActivity.this)
                    .setTitle("删除操作")
                    .setMessage("确定删除此操作？")
                    .setPositiveButton("删除", (d, w) -> {
                        link.actions.remove(action);
                        link.actionsContainer.removeView(row);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        link.actionsContainer.addView(row);
    }

    private void saveConfig() {
        SharedPreferences.Editor editor = prefs.edit();

        for (int i = 0; i < tabsData.size(); i++) {
            TabData tab = tabsData.get(i);
            View sectionView = tab.sectionView;

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

                StringBuilder actionsStr = new StringBuilder();
                for (ActionData action : link.actions) {
                    if (action.actionView == null) continue;

                    Spinner spinner = action.actionView.findViewById(R.id.spinnerAction);
                    EditText etSelector = action.actionView.findViewById(R.id.etSelector);
                    EditText etValue = action.actionView.findViewById(R.id.etValue);
                    EditText etDelay = action.actionView.findViewById(R.id.etDelay);

                    String selector = etSelector.getText().toString().trim();
                    if (selector.isEmpty()) continue;

                    String type;
                    int pos = spinner.getSelectedItemPosition();
                    if (pos == 0) type = "hide";
                    else if (pos == 1) type = "click";
                    else type = "modify";

                    String value = etValue.getText().toString().trim();
                    int delay = 0;
                    try { delay = Integer.parseInt(etDelay.getText().toString().trim()); } catch (Exception e) {}

                    actionsStr.append("|").append(type).append("|").append(selector);
                    if (pos == 1) actionsStr.append("|").append(delay);
                    else if (pos == 2 && !value.isEmpty()) actionsStr.append("|").append(value);
                }

                linksStr.append(actionsStr);
            }

            editor.putString("links" + (i + 1), linksStr.toString());
        }

        editor.putInt("tab_count", tabsData.size());
        editor.putBoolean("kdocs_optimize", switchKdocsOptimize.isChecked());
        editor.putBoolean("night_mode_css", switchNightModeCSS.isChecked());
        editor.putBoolean("page_actions_enabled", switchPageActions.isChecked());

        editor.apply();
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
