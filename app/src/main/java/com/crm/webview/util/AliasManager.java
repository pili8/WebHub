package com.crm.webview.util;

import android.content.ComponentName;
import android.content.pm.PackageManager;

/**
 * 启动图标（应用别名）管理（全部 static 方法）。
 * 从 SettingsActivity 和 MainActivity 中提取。
 */
public class AliasManager {

    private AliasManager() {} // 不可实例化

    public static final String[] ALIAS_NAMES = {
        "com.crm.webview.AliasWebHub",
        "com.crm.webview.AliasPengPai",
        "com.crm.webview.AliasLanHub",
        "com.crm.webview.AliasECM",
        "com.crm.webview.AliasGming",
        "com.crm.webview.AliasPili",
        "com.crm.webview.AliasPiliDouyin",
        "com.crm.webview.AliasWebHub3",
        "com.crm.webview.AliasWebHub4",
        "com.crm.webview.AliasECR1"
    };

    public static final String[] PRESET_LABELS = {
        "WebHub 1",
        "澎湃",
        "LanHub",
        "ECR2",
        "Gming",
        "Pili",
        "PILI",
        "WebHub 3",
        "WebHub 4",
        "ECR1"
    };

    /**
     * 确保至少有一个启动入口别名可用（修复更新后图标消失）。
     * 原 MainActivity.ensureLauncherAlias()
     */
    public static void ensureLauncherAlias(PackageManager pm, String packageName) {
        // 检查是否有任何别名已启用
        for (String name : ALIAS_NAMES) {
            ComponentName cn = new ComponentName(packageName, name);
            int state = pm.getComponentEnabledSetting(cn);
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                return; // 已有可用别名，无需修复
            }
        }

        // 没有别名可用（覆盖安装后状态丢失），重置为默认
        for (String name : ALIAS_NAMES) {
            ComponentName cn = new ComponentName(packageName, name);
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
        ComponentName defaultCn = new ComponentName(packageName, ALIAS_NAMES[0]);
        pm.setComponentEnabledSetting(defaultCn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    /**
     * 切换应用预设（禁用所有别名，启用选中的）。
     * 原 SettingsActivity.switchAppPreset()
     */
    public static void switchAppPreset(PackageManager pm, String packageName, int index) {
        // 禁用所有别名
        for (String name : ALIAS_NAMES) {
            ComponentName cn = new ComponentName(packageName, name);
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }

        // 启用选中的别名
        ComponentName selected = new ComponentName(packageName, ALIAS_NAMES[index]);
        pm.setComponentEnabledSetting(selected,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    /**
     * 获取当前启用的预设索引。
     * 原 SettingsActivity.setupPresetSwitcher() 中的查找逻辑。
     *
     * @return 当前预设索引，如果没有找到则返回 0
     */
    public static int getCurrentPresetIndex(PackageManager pm, String packageName) {
        for (int i = 0; i < ALIAS_NAMES.length; i++) {
            try {
                ComponentName cn = new ComponentName(packageName, ALIAS_NAMES[i]);
                int state = pm.getComponentEnabledSetting(cn);
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                    return i;
                }
            } catch (Exception e) {}
        }
        return 0;
    }
}
