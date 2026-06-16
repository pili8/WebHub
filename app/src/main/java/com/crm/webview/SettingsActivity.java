package com.crm.webview;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout settingsContainer;
    private SharedPreferences prefs;

    private static final String[] ACTION_TYPES = {"隐藏", "点击", "修改"};
    private static final String[] DEFAULT_TAB_ICONS = {"📊", "📋", "➕"};
    private static final String[] DEFAULT_TAB_TITLES = {"销售机会", "最近新增", "录入线索"};
    private static final String[] DEFAULT_LINK_TITLES = {"销售机会", "最近新增", "录入线索"};
    private static final String[] DEFAULT_LINK_URLS = {
            "https://www.kdocs.cn/wo/sl/v12CEOZt",
            "https://www.kdocs.cn/wo/sl/v14T2gpD",
            "https://www.kdocs.cn/wo/sl/v13iHfr4"
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
        List<ActionData> actions = new ArrayList<>();
        View cardView;
        LinearLayout actionsContainer;
    }

    static class ActionData {
        String type; // hide, click, modify
        String selector;
        String value;
        View actionView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        settingsContainer = findViewById(R.id.settingsContainer);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveConfig());

        loadConfig();
        buildUI();
    }

    private void loadConfig() {
        tabsData.clear();

        for (int i = 0; i < 3; i++) {
            TabData tab = new TabData();
            tab.icon = prefs.getString("icon" + (i + 1), DEFAULT_TAB_ICONS[i]);
            tab.title = prefs.getString("title" + (i + 1), DEFAULT_TAB_TITLES[i]);

            // 加载链接
            String linksStr = prefs.getString("links" + (i + 1), "");
            if (linksStr.isEmpty()) {
                LinkData link = new LinkData();
                link.title = DEFAULT_LINK_TITLES[i];
                link.url = DEFAULT_LINK_URLS[i];
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

                    // 解析标题和URL
                    String[] titleUrlParts = titleUrl.split(",", 2);
                    if (titleUrlParts.length == 2) {
                        link.title = titleUrlParts[0].trim();
                        link.url = titleUrlParts[1].trim();
                    }

                    // 解析操作
                    if (!actionsStr.isEmpty()) {
                        String[] actionParts = actionsStr.split("\\|");
                        for (int j = 0; j < actionParts.length; j += 3) {
                            if (j + 2 < actionParts.length) {
                                ActionData action = new ActionData();
                                action.type = actionParts[j];
                                action.selector = actionParts[j + 1];
                                action.value = actionParts[j + 2];
                                link.actions.add(action);
                            } else if (j + 1 < actionParts.length) {
                                ActionData action = new ActionData();
                                action.type = actionParts[j];
                                action.selector = actionParts[j + 1];
                                action.value = "";
                                link.actions.add(action);
                            }
                        }
                    }

                    tab.links.add(link);
                }
            }

            tabsData.add(tab);
        }
    }

    private void buildUI() {
        settingsContainer.removeAllViews();

        for (int i = 0; i < tabsData.size(); i++) {
            TabData tab = tabsData.get(i);
            View sectionView = LayoutInflater.from(this).inflate(R.layout.item_tab_section, settingsContainer, false);
            tab.sectionView = sectionView;

            // 设置选项卡标题
            TextView tvTabIcon = sectionView.findViewById(R.id.tvTabIcon);
            TextView tvTabTitle = sectionView.findViewById(R.id.tvTabTitle);
            EditText etTabIcon = sectionView.findViewById(R.id.etTabIcon);
            EditText etTabTitle = sectionView.findViewById(R.id.etTabTitle);
            TextView tvArrow = sectionView.findViewById(R.id.tvArrow);
            LinearLayout linksContainer = sectionView.findViewById(R.id.linksContainer);
            TextView btnAddLink = sectionView.findViewById(R.id.btnAddLink);

            tvTabIcon.setText(tab.icon);
            tvTabTitle.setText("选项卡 " + (i + 1));
            etTabIcon.setText(tab.icon);
            etTabTitle.setText(tab.title);

            tab.linksContainer = linksContainer;

            // 展开/收起
            LinearLayout btnToggle = sectionView.findViewById(R.id.btnToggle);
            btnToggle.setOnClickListener(v -> {
                tab.isExpanded = !tab.isExpanded;
                linksContainer.setVisibility(tab.isExpanded ? View.VISIBLE : View.GONE);
                btnAddLink.setVisibility(tab.isExpanded ? View.VISIBLE : View.GONE);
                tvArrow.setText(tab.isExpanded ? "▲" : "▼");
            });

            // 添加链接按钮
            int tabIndex = i;
            btnAddLink.setOnClickListener(v -> {
                LinkData newLink = new LinkData();
                newLink.title = "";
                newLink.url = "";
                tab.links.add(newLink);
                addLinkCard(tab, newLink, tabIndex);
            });

            // 添加链接卡片
            for (LinkData link : tab.links) {
                addLinkCard(tab, link, i);
            }

            settingsContainer.addView(sectionView);
        }
    }

    private void addLinkCard(TabData tab, LinkData link, int tabIndex) {
        View cardView = LayoutInflater.from(this).inflate(R.layout.item_link_card, tab.linksContainer, false);
        link.cardView = cardView;

        EditText etLinkTitle = cardView.findViewById(R.id.etLinkTitle);
        EditText etLinkUrl = cardView.findViewById(R.id.etLinkUrl);
        TextView btnDeleteLink = cardView.findViewById(R.id.btnDeleteLink);
        LinearLayout actionsContainer = cardView.findViewById(R.id.actionsContainer);
        TextView btnAddAction = cardView.findViewById(R.id.btnAddAction);

        etLinkTitle.setText(link.title);
        etLinkUrl.setText(link.url);

        link.actionsContainer = actionsContainer;

        // 删除链接
        btnDeleteLink.setOnClickListener(v -> {
            tab.links.remove(link);
            tab.linksContainer.removeView(cardView);
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
        View row = LayoutInflater.from(this).inflate(R.layout.item_script_action, link.actionsContainer, false);
        action.actionView = row;

        Spinner spinnerAction = row.findViewById(R.id.spinnerAction);
        EditText etSelector = row.findViewById(R.id.etSelector);
        EditText etValue = row.findViewById(R.id.etValue);
        TextView btnDelete = row.findViewById(R.id.btnDelete);

        // 设置操作类型下拉
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ACTION_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAction.setAdapter(adapter);

        // 设置当前值
        int actionIndex = 0;
        if ("hide".equals(action.type)) actionIndex = 0;
        else if ("click".equals(action.type)) actionIndex = 1;
        else if ("modify".equals(action.type)) actionIndex = 2;
        spinnerAction.setSelection(actionIndex);

        etSelector.setText(action.selector);
        etValue.setText(action.value);

        // 根据操作类型显示/隐藏值输入框
        etValue.setVisibility(actionIndex == 2 ? View.VISIBLE : View.GONE);

        spinnerAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                etValue.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 删除按钮
        btnDelete.setOnClickListener(v -> {
            link.actions.remove(action);
            link.actionsContainer.removeView(row);
        });

        link.actionsContainer.addView(row);
    }

    private void saveConfig() {
        SharedPreferences.Editor editor = prefs.edit();

        for (int i = 0; i < tabsData.size(); i++) {
            TabData tab = tabsData.get(i);
            View sectionView = tab.sectionView;

            EditText etTabIcon = sectionView.findViewById(R.id.etTabIcon);
            EditText etTabTitle = sectionView.findViewById(R.id.etTabTitle);

            String icon = etTabIcon.getText().toString().trim();
            String title = etTabTitle.getText().toString().trim();

            if (icon.isEmpty()) icon = DEFAULT_TAB_ICONS[i];
            if (title.isEmpty()) title = DEFAULT_TAB_TITLES[i];

            editor.putString("icon" + (i + 1), icon);
            editor.putString("title" + (i + 1), title);

            // 构建链接字符串
            StringBuilder linksStr = new StringBuilder();
            for (LinkData link : tab.links) {
                EditText etLinkTitle = link.cardView.findViewById(R.id.etLinkTitle);
                EditText etLinkUrl = link.cardView.findViewById(R.id.etLinkUrl);

                String linkTitle = etLinkTitle.getText().toString().trim();
                String linkUrl = etLinkUrl.getText().toString().trim();

                if (linkTitle.isEmpty() || linkUrl.isEmpty()) continue;

                if (linksStr.length() > 0) linksStr.append("\n");
                linksStr.append(linkTitle).append(",").append(linkUrl);

                // 构建操作字符串
                StringBuilder actionsStr = new StringBuilder();
                for (ActionData action : link.actions) {
                    if (action.actionView == null) continue;

                    Spinner spinner = action.actionView.findViewById(R.id.spinnerAction);
                    EditText etSelector = action.actionView.findViewById(R.id.etSelector);
                    EditText etValue = action.actionView.findViewById(R.id.etValue);

                    String selector = etSelector.getText().toString().trim();
                    if (selector.isEmpty()) continue;

                    String type;
                    int pos = spinner.getSelectedItemPosition();
                    if (pos == 0) type = "hide";
                    else if (pos == 1) type = "click";
                    else type = "modify";

                    String value = etValue.getText().toString().trim();

                    actionsStr.append("|").append(type).append("|").append(selector);
                    if (pos == 2 && !value.isEmpty()) {
                        actionsStr.append("|").append(value);
                    }
                }

                linksStr.append(actionsStr);
            }

            editor.putString("links" + (i + 1), linksStr.toString());
        }

        editor.apply();
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
