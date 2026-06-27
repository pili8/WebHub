package com.crm.webview.util;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.TextView;

import java.io.File;

/**
 * 缓存管理工具。
 * 从 SettingsActivity 中提取缓存相关逻辑。
 */
public class CacheManager {

    private CacheManager() {} // 不可实例化

    /**
     * 格式化并显示缓存大小到 TextView。
     * 原 SettingsActivity.updateCacheSize()
     */
    public static void updateCacheSize(Context context, TextView textView) {
        long size = getCacheSize(context);
        textView.setText("当前缓存: " + UIHelper.formatBytes(size));
    }

    /**
     * 获取应用总缓存大小（字节）。
     * 原 SettingsActivity.getCacheSize()
     */
    public static long getCacheSize(Context context) {
        long size = 0;
        try {
            size += getDirSize(context.getCacheDir());
            File webviewDir = new File(context.getFilesDir(), "../app_webview");
            if (webviewDir.exists()) size += getDirSize(webviewDir);
        } catch (Exception e) {}
        return size;
    }

    /**
     * 递归计算目录大小。
     * 原 SettingsActivity.getDirSize()
     */
    public static long getDirSize(File dir) {
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

    /**
     * 清除所有缓存（WebView、Cookie、WebStorage、文件缓存）。
     * 原 SettingsActivity.clearAllCache()
     */
    public static void clearAllCache(Context context) {
        try {
            android.webkit.WebView webView = new android.webkit.WebView(context);
            webView.clearCache(true);
            webView.clearHistory();
            webView.destroy();
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            WebStorage.getInstance().deleteAllData();
            deleteDir(context.getCacheDir());
            File webviewDir = new File(context.getFilesDir(), "../app_webview");
            if (webviewDir.exists()) deleteDir(webviewDir);
        } catch (Exception e) {}
    }

    /**
     * 递归删除目录。
     * 原 SettingsActivity.deleteDir()
     */
    public static boolean deleteDir(File dir) {
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
}
