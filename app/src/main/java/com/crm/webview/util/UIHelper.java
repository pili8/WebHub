package com.crm.webview.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

/**
 * UI 工具方法（全部 static）。
 * 从 MainActivity 和 SettingsActivity 中提取。
 */
public class UIHelper {

    private UIHelper() {} // 不可实例化

    /**
     * dp 转 px。
     */
    public static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 格式化字节数为人类可读字符串。
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        else return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 格式化秒数为人类可读间隔。
     */
    public static String formatInterval(int seconds) {
        if (seconds < 60) return seconds + "秒";
        if (seconds < 3600) return (seconds / 60) + "分钟";
        return (seconds / 3600) + "小时";
    }

    /**
     * 复制文本到剪贴板。
     */
    public static void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("element", text);
        clipboard.setPrimaryClip(clip);
    }

    /**
     * 从 JSON 字符串中提取指定 key 的值（简单解析，不依赖 JSON 库）。
     */
    public static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }
}
