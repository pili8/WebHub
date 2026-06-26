package com.crm.webview.config;

import android.content.SharedPreferences;

import com.crm.webview.model.AppConfig;
import com.crm.webview.model.AppConfig.ActionData;
import com.crm.webview.model.AppConfig.ActionItem;
import com.crm.webview.model.AppConfig.LinkData;
import com.crm.webview.model.AppConfig.LinkItem;
import com.crm.webview.model.AppConfig.TabData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置管理器。
 * 从 MainActivity 和 SettingsActivity 中提取配置读写逻辑。
 */
public class ConfigManager {

    private ConfigManager() {} // 不可实例化

    // ==================== 内部数据类 ====================

    /**
     * 承载 loadConfig 返回的多值数据（MainActivity 侧）。
     */
    public static class ConfigData {
        public int tabCount;
        public String[] tabIcons = new String[AppConfig.MAX_TABS];
        public String[] tabTitles = new String[AppConfig.MAX_TABS];
        public String[] tabActions = new String[AppConfig.MAX_TABS];
        public String[] tabColors = new String[AppConfig.MAX_TABS];
        public List<List<LinkItem>> tabLinks = new ArrayList<>();
    }

    // ==================== MainActivity 侧配置方法 ====================

    /**
     * 加载完整配置（MainActivity 侧）。
     * 原 MainActivity.loadConfig()
     */
    public static ConfigData loadConfig(SharedPreferences prefs) {
        ConfigData data = new ConfigData();

        data.tabCount = prefs.getInt("tab_count", 3);
        if (data.tabCount < 2) data.tabCount = 2;
        if (data.tabCount > AppConfig.MAX_TABS) data.tabCount = AppConfig.MAX_TABS;

        String[] defaultIcons = AppConfig.DEFAULT_TAB_ICONS.clone();
        String[] defaultTitles = AppConfig.DEFAULT_TAB_TITLES.clone();
        String[] defaultUrls = {
            "about:blank", "about:blank", "about:blank", "about:blank",
            "about:blank", "about:blank", "about:blank", "about:blank"
        };

        // 初始化默认颜色
        for (int i = 0; i < AppConfig.MAX_TABS; i++) {
            data.tabColors[i] = AppConfig.DEFAULT_TAB_COLORS[i % AppConfig.DEFAULT_TAB_COLORS.length];
        }

        String tabsJson = prefs.getString("tabs_config", "");

        if (!tabsJson.isEmpty() && loadConfigFromJson(data, tabsJson, defaultIcons, defaultTitles, defaultUrls)) {
            return data;
        }

        for (int i = 0; i < data.tabCount; i++) {
            data.tabIcons[i] = prefs.getString("icon" + (i + 1), defaultIcons[i]);
            data.tabTitles[i] = prefs.getString("title" + (i + 1), defaultTitles[i]);
            data.tabActions[i] = prefs.getString("actions" + (i + 1), "");

            List<LinkItem> links = new ArrayList<>();
            String linksStr = prefs.getString("links" + (i + 1), "");

            if (linksStr.isEmpty()) {
                links.add(new LinkItem(data.tabTitles[i], defaultUrls[i]));
            } else {
                String[] lines = linksStr.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\|", 2);
                    String titleUrl = parts[0];
                    String actions = parts.length > 1 ? parts[1] : "";

                    String[] titleUrlParts = titleUrl.split(",", 4);
                    if (titleUrlParts.length >= 2) {
                        String scope = titleUrlParts.length > 2 ? titleUrlParts[2].trim() : "link";
                        boolean desktopMode = titleUrlParts.length > 3 && "1".equals(titleUrlParts[3].trim());
                        links.add(new LinkItem(titleUrlParts[0].trim(), titleUrlParts[1].trim(),
                                scope, parseLegacyActions(actions), desktopMode));
                    }
                }
            }

            if (links.isEmpty()) {
                links.add(new LinkItem(data.tabTitles[i], "about:blank"));
            }

            if (data.tabLinks.size() > i) {
                data.tabLinks.set(i, links);
            } else {
                data.tabLinks.add(links);
            }
        }

        while (data.tabLinks.size() > data.tabCount) {
            data.tabLinks.remove(data.tabLinks.size() - 1);
        }

        return data;
    }

    /**
     * 从 JSON 字符串加载配置（MainActivity 侧）。
     * 原 MainActivity.loadConfigFromJson()
     *
     * @return true 如果成功解析 JSON
     */
    public static boolean loadConfigFromJson(ConfigData data, String tabsJson,
            String[] defaultIcons, String[] defaultTitles, String[] defaultUrls) {
        try {
            JSONArray tabsArray = new JSONArray(tabsJson);
            data.tabCount = Math.max(2, Math.min(AppConfig.MAX_TABS, tabsArray.length()));

            for (int i = 0; i < data.tabCount; i++) {
                JSONObject tab = tabsArray.getJSONObject(i);
                data.tabIcons[i] = tab.optString("icon", defaultIcons[i]);
                data.tabTitles[i] = tab.optString("title", defaultTitles[i]);
                data.tabColors[i] = tab.optString("color", AppConfig.DEFAULT_TAB_COLORS[i % AppConfig.DEFAULT_TAB_COLORS.length]);
                data.tabActions[i] = "";

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
                                        actionJson.optInt("delay", 0),
                                        actionJson.optString("remark", "")
                                ));
                            }
                        }

                        links.add(new LinkItem(title, url,
                                linkJson.optString("scope", "link"), actions,
                                linkJson.optBoolean("desktopMode", false)));
                    }
                }

                if (links.isEmpty()) {
                    links.add(new LinkItem(data.tabTitles[i], defaultUrls[i]));
                }

                if (data.tabLinks.size() > i) {
                    data.tabLinks.set(i, links);
                } else {
                    data.tabLinks.add(links);
                }
            }

            while (data.tabLinks.size() > data.tabCount) {
                data.tabLinks.remove(data.tabLinks.size() - 1);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== SettingsActivity 侧配置方法 ====================

    /**
     * 加载配置到 TabData 列表（SettingsActivity 侧）。
     * 原 SettingsActivity.loadConfig()
     */
    public static List<TabData> loadConfigAsTabs(SharedPreferences prefs) {
        List<TabData> tabsData = new ArrayList<>();

        int tabCount = prefs.getInt("tab_count", 3);
        if (tabCount < 2) tabCount = 2;
        if (tabCount > 8) tabCount = 8;

        String tabsJson = prefs.getString("tabs_config", "");
        if (!tabsJson.isEmpty() && loadConfigFromJsonAsTabs(tabsData, tabsJson, tabCount)) {
            return tabsData;
        }

        for (int i = 0; i < tabCount; i++) {
            TabData tab = new TabData();
            tab.icon = prefs.getString("icon" + (i + 1), AppConfig.DEFAULT_TAB_ICONS[i]);
            tab.title = prefs.getString("title" + (i + 1), AppConfig.DEFAULT_TAB_TITLES[i]);

            String linksStr = prefs.getString("links" + (i + 1), "");
            if (linksStr.isEmpty()) {
                LinkData link = new LinkData();
                link.title = AppConfig.DEFAULT_TAB_TITLES[i];
                link.url = "about:blank";
                tab.links.add(link);
            } else {
                parseLegacyLinks(tab, linksStr);
            }

            tabsData.add(tab);
        }
        return tabsData;
    }

    /**
     * 从 JSON 加载 TabData 列表（SettingsActivity 侧）。
     * 原 SettingsActivity.loadConfigFromJson()
     */
    public static boolean loadConfigFromJsonAsTabs(List<TabData> tabsData, String tabsJson, int tabCount) {
        try {
            JSONArray tabsArray = new JSONArray(tabsJson);
            int count = Math.max(2, Math.min(8, Math.min(tabCount, tabsArray.length())));
            for (int i = 0; i < count; i++) {
                JSONObject tabJson = tabsArray.getJSONObject(i);
                TabData tab = new TabData();
                tab.icon = tabJson.optString("icon", AppConfig.DEFAULT_TAB_ICONS[i]);
                tab.title = tabJson.optString("title", AppConfig.DEFAULT_TAB_TITLES[i]);

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

    /**
     * 解析旧格式链接字符串到 LinkData 列表（SettingsActivity 侧）。
     * 原 SettingsActivity.parseLegacyLinks()
     */
    public static void parseLegacyLinks(TabData tab, String linksStr) {
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
                if (titleUrlParts.length > 3 && "1".equals(titleUrlParts[3].trim())) {
                    link.desktopMode = true;
                }
            }
            parseLegacyActionsForLinkData(link, actionsStr);
            if (link.title != null && !link.title.isEmpty() && link.url != null && !link.url.isEmpty()) {
                tab.links.add(link);
            }
        }
    }

    /**
     * 解析旧格式操作字符串到 LinkData.actions（SettingsActivity 侧）。
     * 原 SettingsActivity.parseLegacyActions(LinkData, String)
     */
    public static void parseLegacyActionsForLinkData(LinkData link, String actionsStr) {
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

    // ==================== 通用方法 ====================

    /**
     * 解析旧格式操作字符串（MainActivity 侧）。
     * 原 MainActivity.parseLegacyActions()
     */
    public static List<ActionItem> parseLegacyActions(String actions) {
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
            String remark = "";
            for (int k = 2; k < parts.length; k++) {
                String part = parts[k];
                if (part.startsWith("@")) {
                    remark = part.substring(1)
                        .replace("｜", "|")
                        .replace("；", ";");
                } else if ("click".equals(type)) {
                    try { delay = Integer.parseInt(part); } catch (Exception e) {}
                } else if ("modify".equals(type) && value.isEmpty()) {
                    value = part;
                }
            }
            items.add(new ActionItem(type, selector, value, delay, remark));
        }
        return items;
    }

    // ==================== 定时刷新配置 ====================

    /**
     * 加载每个工作区的刷新间隔配置。
     * 原 MainActivity.loadTabAutoRefresh()
     */
    public static int[] loadTabAutoRefresh(SharedPreferences prefs) {
        int[] tabAutoRefresh = new int[AppConfig.MAX_TABS];
        for (int i = 0; i < AppConfig.MAX_TABS; i++) {
            tabAutoRefresh[i] = prefs.getInt("auto_refresh_tab_" + i, 0);
        }
        return tabAutoRefresh;
    }

    /**
     * 加载并迁移旧的全局自动刷新配置。
     * 原 MainActivity.loadTabAutoRefresh() 中的迁移逻辑。
     */
    public static int[] loadTabAutoRefreshWithMigration(SharedPreferences prefs, int currentTab) {
        int[] tabAutoRefresh = loadTabAutoRefresh(prefs);

        // 兼容旧的全局配置
        int oldGlobal = prefs.getInt("auto_refresh_interval", 0);
        if (oldGlobal > 0) {
            boolean anySet = false;
            for (int i = 0; i < AppConfig.MAX_TABS; i++) {
                if (tabAutoRefresh[i] > 0) { anySet = true; break; }
            }
            if (!anySet) {
                tabAutoRefresh[0] = oldGlobal;
                prefs.edit().putInt("auto_refresh_tab_0", oldGlobal).remove("auto_refresh_interval").apply();
            }
        }
        return tabAutoRefresh;
    }

    /**
     * 保存指定工作区的刷新间隔。
     * 原 MainActivity.saveTabAutoRefresh()
     */
    public static void saveTabAutoRefresh(SharedPreferences prefs, int tabIndex, int interval) {
        prefs.edit().putInt("auto_refresh_tab_" + tabIndex, interval).apply();
    }

    // ==================== JSON 构建 ====================

    /**
     * 从 SharedPreferences 构建 tabs JSON 数组（SettingsActivity 侧）。
     * 原 SettingsActivity.buildTabsJsonFromPrefs()
     */
    public static JSONArray buildTabsJsonFromPrefs(SharedPreferences prefs) {
        List<TabData> data = new ArrayList<>();
        int tabCount = prefs.getInt("tab_count", 3);
        if (tabCount < 2) tabCount = 2;
        if (tabCount > 8) tabCount = 8;

        for (int i = 0; i < tabCount; i++) {
            TabData tab = new TabData();
            tab.icon = prefs.getString("icon" + (i + 1), AppConfig.DEFAULT_TAB_ICONS[i]);
            tab.title = prefs.getString("title" + (i + 1), AppConfig.DEFAULT_TAB_TITLES[i]);

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

    /**
     * 从 TabData 列表构建 JSON 数组。
     * 原 SettingsActivity.buildTabsJson()
     */
    public static JSONArray buildTabsJson(List<TabData> data) {
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

    /**
     * 获取配置快照字符串，用于 onResume 变化检测。
     * 原 MainActivity.onResume() 中的 lastTabsConfig 比对逻辑。
     */
    public static String getTabsConfigSnapshot(SharedPreferences prefs) {
        return prefs.getInt("tab_count", 3) + "|" + prefs.getString("tabs_config", "");
    }
}
