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

import com.crm.webview.config.ConfigManager;
import com.crm.webview.model.AppConfig;
import com.crm.webview.model.AppConfig.ActionData;
import com.crm.webview.model.AppConfig.LinkData;
import com.crm.webview.model.AppConfig.TabData;
import com.crm.webview.util.AliasManager;
import com.crm.webview.util.CacheManager;
import com.crm.webview.util.UIHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout settingsContainer;
    private Switch switchKdocsOptimize;
    private Switch switchNightModeCSS;
    private Switch switchPageActions;
    private android.widget.Spinner spinnerUA;
    private ImageView ivPresetPreview;
    private AlertDialog presetDialog;
    private int pendingPresetIndex = -1;
    private int currentPresetIndex = -1;
    private SharedPreferences prefs;

    // 预设图标资源 ID（与 AliasManager.ALL_NAMES 顺序一致）
    private static final int[] PRESET_ICONS = {
        R.mipmap.ic_launcher,    // 0  WebHub1
        R.mipmap.ic_webhub2,     // 1  WebHub2
        R.mipmap.ic_webhub3,     // 2  WebHub3
        R.mipmap.ic_webhub4,     // 3  WebHub4
        R.mipmap.ic_webhub5,     // 4  WebHub5
        R.mipmap.ic_webhub6,     // 5  WebHub6
        R.mipmap.ic_lanhub1,     // 6  LanHub1
        R.mipmap.ic_lanhub2,     // 7  LanHub2
        R.mipmap.ic_ecr1,        // 8  ECR1
        R.mipmap.ic_ecr2,        // 9  ECR2
        R.mipmap.ic_ecr_cn,      // 10 ECR中文
        R.mipmap.ic_gming1,      // 11 Gming1
        R.mipmap.ic_gming2,      // 12 Gming2
        R.mipmap.ic_gming3,      // 13 Gming3
        R.mipmap.ic_ppl,         // 14 澎湃浪
        R.mipmap.ic_pili,        // 15 Pili
        R.mipmap.ic_pili_dy      // 16 Pili抖音版
    };

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

    // 常量已迁移至 AppConfig / AliasManager，通过静态引用访问

    private List<TabData> tabsData = new ArrayList<>();

    // 数据模型已迁移至 AppConfig（TabData, LinkData, ActionData）

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
            UIHelper.applyDarkTheme(this);
        }

        // 应用名称和图标预设切换
        setupPresetSwitcher();

        setupCache();
        setupExportImport();
        UIHelper.setupAbout(this);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveConfig());

        tabsData = ConfigManager.loadConfigAsTabs(prefs);
        buildUI();
    }

    private void setupCache() {
        TextView tvCacheSize = findViewById(R.id.tvCacheSize);
        TextView btnClearCache = findViewById(R.id.btnClearCache);

        CacheManager.updateCacheSize(this, tvCacheSize);

        btnClearCache.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("清除缓存")
                    .setMessage("确定要清除所有缓存吗？\n这会清除登录状态。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        CacheManager.clearAllCache(this);
                        CacheManager.updateCacheSize(this, tvCacheSize);
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

    private void setupPresetSwitcher() {
        currentPresetIndex = AliasManager.getCurrentIndex(getPackageManager(), getPackageName());
        pendingPresetIndex = currentPresetIndex;

        ivPresetPreview = findViewById(R.id.ivPresetPreview);
        updatePresetInfo();

        // 点击打开分组选择对话框
        findViewById(R.id.btnPresetPicker).setOnClickListener(v -> showPresetDialog());
    }

    private AlertDialog presetDialog;

    private void showPresetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择名称和图标");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(8, 8, 8, 8);

        addGroupHeader(root, "WebHub / LanHub / ECR", 0, AliasManager.G1_COUNT);
        addGroupItems(root, 0, AliasManager.G1_COUNT);

        addGroupHeader(root, "Gming / 澎湃浪 / Pili", AliasManager.G1_COUNT, AliasManager.G2_COUNT);
        addGroupItems(root, AliasManager.G1_COUNT, AliasManager.G2_COUNT);

        builder.setView(root);
        builder.setNegativeButton("取消", null);
        presetDialog = builder.create();
        presetDialog.show();
    }

    private void addGroupHeader(LinearLayout root, String title, int start, int count) {
        // 判断当前选中项是否在这个分组
        boolean active = pendingPresetIndex >= start && pendingPresetIndex < start + count;
        TextView header = new TextView(this);
        header.setText(title + (active ? "  ◀" : ""));
        header.setTextSize(13);
        header.setTextColor(active ? 0xFF1976D2 : 0xFF888888);
        header.setPadding(8, 12, 8, 4);
        root.addView(header);
    }

    private void addGroupItems(LinearLayout root, int start, int count) {
        LinearLayout scroll = new LinearLayout(this);
        scroll.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < count; i++) {
            int index = start + i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(8, 4, 8, 8);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(true);

            // 图标
            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(52), dp(52));
            icon.setLayoutParams(ip);
            icon.setImageResource(PRESET_ICONS[index]);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

            // 选中高亮
            if (index == pendingPresetIndex) {
                icon.setBackgroundColor(0x331976D2);
            }
            item.addView(icon);

            // 名称
            TextView label = new TextView(this);
            label.setText(AliasManager.getLabelByIndex(index));
            label.setTextSize(10);
            label.setTextColor(index == pendingPresetIndex ? 0xFF1976D2 : 0xFF666666);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setLayoutParams(new LinearLayout.LayoutParams(dp(56), LayoutParams.WRAP_CONTENT));
            item.addView(label);

            final int idx = index;
            item.setOnClickListener(v2 -> {
                pendingPresetIndex = idx;
                updatePresetInfo();
                if (presetDialog != null) presetDialog.dismiss();
            });

            scroll.addView(item);
        }
        root.addView(scroll);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void updatePresetInfo() {
        TextView tv = findViewById(R.id.tvPresetInfo);
        String label = AliasManager.getLabelByIndex(currentPresetIndex);
        if (pendingPresetIndex != currentPresetIndex) {
            tv.setText("当前: " + label + "  →  待保存: " + AliasManager.getLabelByIndex(pendingPresetIndex));
            tv.setTextColor(0xFFFF9800);
        } else {
            tv.setText(label);
            tv.setTextColor(0xFF999999);
        }
        ivPresetPreview.setImageResource(PRESET_ICONS[pendingPresetIndex]);
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
            JSONObject json = ConfigManager.buildExportJson(prefs);
            if (ConfigManager.writeJsonToUri(this, uri, json.toString(2))) {
                Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show();

                // 分享文件
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/json");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(Intent.createChooser(shareIntent, "分享配置文件"));
            } else {
                Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
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
            String jsonStr = ConfigManager.readJsonFromUri(this, uri);
            if (ConfigManager.applyImportJson(prefs, jsonStr)) {
                tabsData = ConfigManager.loadConfigAsTabs(prefs);
                buildUI();
                switchKdocsOptimize.setChecked(prefs.getBoolean("kdocs_optimize", true));
                switchNightModeCSS.setChecked(prefs.getBoolean("night_mode_css", false));
                switchPageActions.setChecked(prefs.getBoolean("page_actions_enabled", true));
                Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "导入失败: JSON 解析错误", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            btnAddTab.setPadding(0, UIHelper.dpToPx(this, 16), 0, UIHelper.dpToPx(this, 16));
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
                android.R.layout.simple_spinner_item, AppConfig.ACTION_TYPES);
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
        // 应用预设切换（仅在选择变更时）
        if (pendingPresetIndex >= 0 && pendingPresetIndex != currentPresetIndex) {
            AliasManager.switchPreset(getPackageManager(), getPackageName(), pendingPresetIndex);
            currentPresetIndex = pendingPresetIndex;
            updatePresetInfo();
            Toast.makeText(this, "图标和名称已更新，返回桌面查看", Toast.LENGTH_LONG).show();
        }

        SharedPreferences.Editor editor = prefs.edit();
        JSONArray tabsJson = ConfigManager.buildTabsJson(tabsData);

        for (int i = 0; i < tabsData.size(); i++) {
            TabData tab = tabsData.get(i);

            // 使用 tab 对象中的值（可能已被编辑）
            String icon = tab.icon;
            String title = tab.title;

            if (icon.isEmpty()) icon = AppConfig.DEFAULT_TAB_ICONS[i];
            if (title.isEmpty()) title = AppConfig.DEFAULT_TAB_TITLES[i];

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
        return ConfigManager.buildTabsJson(tabsData);
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
}
