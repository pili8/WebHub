package com.crm.webview.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.crm.webview.R;

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

    // ==================== 暗色主题 ====================

    /**
     * 应用暗色主题到 SettingsActivity。
     * 原 SettingsActivity.applyDarkTheme()
     */
    public static void applyDarkTheme(Activity activity) {
        View rootView = activity.findViewById(android.R.id.content);
        rootView.setBackgroundColor(Color.parseColor("#121212"));

        activity.findViewById(R.id.settingsToolbar).setBackgroundColor(Color.parseColor("#1E1E1E"));

        TextView tvAboutInfo = activity.findViewById(R.id.tvAboutInfo);
        TextView tvAboutChangelog = activity.findViewById(R.id.tvAboutChangelog);
        TextView tvAboutArrow = activity.findViewById(R.id.tvAboutArrow);
        if (tvAboutInfo != null) tvAboutInfo.setTextColor(Color.parseColor("#AAAAAA"));
        if (tvAboutChangelog != null) tvAboutChangelog.setTextColor(Color.parseColor("#777777"));
        if (tvAboutArrow != null) tvAboutArrow.setTextColor(Color.parseColor("#666666"));

        LinearLayout aboutHeader = activity.findViewById(R.id.aboutHeader);
        if (aboutHeader != null && aboutHeader.getChildCount() > 0) {
            View firstChild = aboutHeader.getChildAt(0);
            if (firstChild instanceof TextView) {
                ((TextView) firstChild).setTextColor(Color.parseColor("#E0E0E0"));
            }
        }
    }

    // ==================== 关于页面 ====================

    /**
     * 设置关于页面（版本信息、更新日志、GitHub 链接、折叠切换）。
     * 原 SettingsActivity.setupAbout()
     */
    public static void setupAbout(Activity activity) {
        TextView tvAboutInfo = activity.findViewById(R.id.tvAboutInfo);
        TextView tvAboutChangelog = activity.findViewById(R.id.tvAboutChangelog);
        TextView tvAboutGithub = activity.findViewById(R.id.tvAboutGithub);
        LinearLayout aboutHeader = activity.findViewById(R.id.aboutHeader);
        LinearLayout aboutContent = activity.findViewById(R.id.aboutContent);
        TextView tvAboutArrow = activity.findViewById(R.id.tvAboutArrow);

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
            versionName = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception e) {}

        tvAboutInfo.setText(
                "📱 WebHub v" + versionName + "\n" +
                "把常用网页变成 APP，支持自定义外观和自动化操作。\n" +
                "开发者: pili8 | 开源协议: MIT License");

        // 更新日志（发版时同步更新）
        tvAboutChangelog.setText(
                "📋 最近更新:\n" +
                "v2.9.0 - 架构重构、ECR预设名称图标\n" +
                "v2.8.0 - 应用名称图标切换、桌面模式优化、Bug修复\n" +
                "v2.7.6 - 设置页样式恢复、操作项折叠、桌面模式缩放优化\n" +
                "v2.7.5 - 桌面模式开关、设置页重排、Bug修复\n" +
                "v2.7.4 - 修复定时刷新闪退、工作区上限8个、支持HTTP、页面操作优化\n" +
                "v2.7.3 - 工作区自定义颜色、浏览历史、定时刷新、自定义脚本\n" +
                "v2.7.2 - 菜单重构、搜索优化");

        // 跳转 GitHub
        tvAboutGithub.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pili8/WebHub/releases"));
            activity.startActivity(intent);
        });
    }
}
