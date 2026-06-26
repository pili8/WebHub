package com.crm.webview.model;

import com.crm.webview.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一数据模型和常量定义。
 * 从 MainActivity 和 SettingsActivity 中提取的内部类合并到此处。
 */
public class AppConfig {

    // ==================== 常量 ====================

    public static final int MAX_TABS = 8;

    /** 工作区自定义颜色（默认预设） */
    public static final String[] DEFAULT_TAB_COLORS = {
        "#1976D2", "#4CAF50", "#FF9800", "#9C27B0", "#F44336", "#00BCD4", "#E91E63", "#607D8B"
    };

    /** 预设颜色列表（供选择） */
    public static final String[] PRESET_COLORS = {
        "#1976D2", "#4CAF50", "#FF9800", "#9C27B0", "#F44336", "#00BCD4",
        "#E91E63", "#607D8B", "#795548", "#FF5722"
    };

    public static final String[] DEFAULT_TAB_ICONS = {"📊", "📋", "➕", "📁", "👤", "📌", "⭐", "🔧"};
    public static final String[] DEFAULT_TAB_TITLES = {"工作区1", "工作区2", "工作区3", "工作区4", "工作区5", "工作区6", "工作区7", "工作区8"};
    public static final String[] ACTION_TYPES = {"隐藏", "点击", "修改", "自定义脚本"};

    // ==================== 数据模型 ====================

    /**
     * 链接项（MainActivity 使用）。
     */
    public static class LinkItem {
        public String title;
        public String url;
        public String actions;
        public String scope; // link/domain/tab/all
        public boolean desktopMode = false;
        public List<ActionItem> actionItems = new ArrayList<>();

        public LinkItem(String title, String url) {
            this.title = title;
            this.url = url;
            this.actions = "";
            this.scope = "link";
        }

        public LinkItem(String title, String url, String actions) {
            this.title = title;
            this.url = url;
            this.actions = actions;
            this.scope = "link";
            this.actionItems = ConfigManager.parseLegacyActions(actions);
        }

        public LinkItem(String title, String url, String actions, String scope) {
            this.title = title;
            this.url = url;
            this.actions = actions;
            this.scope = scope;
            this.actionItems = ConfigManager.parseLegacyActions(actions);
        }

        public LinkItem(String title, String url, String scope, List<ActionItem> actionItems) {
            this.title = title;
            this.url = url;
            this.scope = scope;
            this.actionItems = actionItems != null ? actionItems : new ArrayList<>();
            this.actions = "";
        }

        public LinkItem(String title, String url, String scope, List<ActionItem> actionItems, boolean desktopMode) {
            this.title = title;
            this.url = url;
            this.scope = scope;
            this.actionItems = actionItems != null ? actionItems : new ArrayList<>();
            this.actions = "";
            this.desktopMode = desktopMode;
        }
    }

    /**
     * 操作项（MainActivity 使用）。
     */
    public static class ActionItem {
        public String type;
        public String selector;
        public String value;
        public int delay;
        public String remark;

        public ActionItem(String type, String selector, String value, int delay) {
            this.type = type;
            this.selector = selector;
            this.value = value;
            this.delay = delay;
            this.remark = "";
        }

        public ActionItem(String type, String selector, String value, int delay, String remark) {
            this.type = type;
            this.selector = selector;
            this.value = value;
            this.delay = delay;
            this.remark = remark != null ? remark : "";
        }
    }

    /**
     * 搜索结果（MainActivity 使用）。
     */
    public static class SearchResult {
        public int tabIndex;
        public int linkIndex;
        public String title;
        public String url;
        public String source;

        public SearchResult(int tabIndex, int linkIndex, String title, String url, String source) {
            this.tabIndex = tabIndex;
            this.linkIndex = linkIndex;
            this.title = title;
            this.url = url;
            this.source = source;
        }
    }

    /**
     * 工作区数据（SettingsActivity 使用）。
     */
    public static class TabData {
        public String icon;
        public String title;
        public List<LinkData> links = new ArrayList<>();
        public android.view.View sectionView;
        public android.widget.LinearLayout linksContainer;
        public boolean isExpanded = false;
    }

    /**
     * 链接数据（SettingsActivity 使用）。
     */
    public static class LinkData {
        public String title;
        public String url;
        public String scope = "link"; // link/domain/tab/all
        public boolean desktopMode = false;
        public List<ActionData> actions = new ArrayList<>();
        public android.view.View cardView;
        public android.widget.LinearLayout actionsContainer;
        public android.widget.Switch switchDesktopMode;
    }

    /**
     * 操作数据（SettingsActivity 使用）。
     */
    public static class ActionData {
        public String type;
        public String selector;
        public String value;
        public String remark = "";
        public int delay = 0;
        public android.view.View actionView;
    }
}
