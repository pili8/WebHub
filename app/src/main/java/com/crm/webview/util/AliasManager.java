package com.crm.webview.util;

import android.content.ComponentName;
import android.content.pm.PackageManager;

/**
 * 启动图标（应用别名）管理。
 * v2.9.2: 17 套预设分两组，Spinner 选择，保存时才切换。
 */
public class AliasManager {

    private AliasManager() {}

    // === 分组1: WebHub / LanHub / ECR ===
    public static final int G1_COUNT = 11;
    public static final String[] G1_NAMES = {
        "com.crm.webview.AliasWH1",
        "com.crm.webview.AliasWH2",
        "com.crm.webview.AliasWH3",
        "com.crm.webview.AliasWH4",
        "com.crm.webview.AliasWH5",
        "com.crm.webview.AliasWH6",
        "com.crm.webview.AliasLH1",
        "com.crm.webview.AliasLH2",
        "com.crm.webview.AliasECR1",
        "com.crm.webview.AliasECR2",
        "com.crm.webview.AliasECRcn"
    };
    public static final String[] G1_LABELS = {
        "WebHub1", "WebHub2", "WebHub3", "WebHub4", "WebHub5", "WebHub6",
        "LanHub1", "LanHub2", "ECR1", "ECR2", "ECR中文"
    };

    // === 分组2: Gming / 澎湃浪 / Pili ===
    public static final int G2_COUNT = 6;
    public static final String[] G2_NAMES = {
        "com.crm.webview.AliasGM1",
        "com.crm.webview.AliasGM2",
        "com.crm.webview.AliasGM3",
        "com.crm.webview.AliasPPL",
        "com.crm.webview.AliasPL1",
        "com.crm.webview.AliasPLdy"
    };
    public static final String[] G2_LABELS = {
        "Gming1", "Gming2", "Gming3", "澎湃浪", "Pili", "Pili抖音版"
    };

    /** 合并的完整别名列表（用于 ensureLauncherAlias 批量操作） */
    public static final String[] ALL_NAMES;

    static {
        ALL_NAMES = new String[G1_COUNT + G2_COUNT];
        System.arraycopy(G1_NAMES, 0, ALL_NAMES, 0, G1_COUNT);
        System.arraycopy(G2_NAMES, 0, ALL_NAMES, G1_COUNT, G2_COUNT);
    }

    /** 根据组号 (1 或 2) 和组内索引返回别名 */
    public static String getAliasName(int group, int pos) {
        return group == 1 ? G1_NAMES[pos] : G2_NAMES[pos];
    }

    /** 根据 presetIndex (0-16, G1 first G2 after) 返回别名 */
    public static String getAliasNameByIndex(int index) {
        if (index < G1_COUNT) return G1_NAMES[index];
        return G2_NAMES[index - G1_COUNT];
    }

    public static String getLabelByIndex(int index) {
        if (index < G1_COUNT) return G1_LABELS[index];
        return G2_LABELS[index - G1_COUNT];
    }

    public static int getGroup(int index) {
        return index < G1_COUNT ? 1 : 2;
    }

    public static int getGroupPos(int index) {
        return index < G1_COUNT ? index : index - G1_COUNT;
    }

    /** 在 ALL_NAMES 中查找匹配的索引 */
    public static int findIndex(String aliasFullName) {
        for (int i = 0; i < ALL_NAMES.length; i++) {
            if (ALL_NAMES[i].equals(aliasFullName)) return i;
        }
        return -1;
    }

    /**
     * 确保至少有一个启动入口别名可用。
     */
    public static void ensureLauncherAlias(PackageManager pm, String pkg) {
        for (String name : ALL_NAMES) {
            int state = pm.getComponentEnabledSetting(new ComponentName(pkg, name));
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return;
        }
        // 全部不可用 → 启用默认
        for (String name : ALL_NAMES) {
            pm.setComponentEnabledSetting(new ComponentName(pkg, name),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
        pm.setComponentEnabledSetting(new ComponentName(pkg, ALL_NAMES[0]),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    /**
     * 切换预设（保存时调用）。
     */
    public static void switchPreset(PackageManager pm, String pkg, int index) {
        for (String name : ALL_NAMES) {
            pm.setComponentEnabledSetting(new ComponentName(pkg, name),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
        pm.setComponentEnabledSetting(new ComponentName(pkg, ALL_NAMES[index]),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** 找到当前启用的预设索引，无则返回 0 */
    public static int getCurrentIndex(PackageManager pm, String pkg) {
        for (int i = 0; i < ALL_NAMES.length; i++) {
            try {
                int s = pm.getComponentEnabledSetting(new ComponentName(pkg, ALL_NAMES[i]));
                if (s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        || s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return i;
            } catch (Exception e) {}
        }
        return 0;
    }
}
