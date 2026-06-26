package com.crm.webview.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.crm.webview.R;

/**
 * WebView 创建和配置工厂。
 * 从 MainActivity 中提取 WebView 相关逻辑。
 * 所有方法变为 static，通过参数传入所需上下文。
 */
public class WebViewFactory {

    private WebViewFactory() {} // 不可实例化

    private static final String DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    // ==================== 回调接口 ====================

    /**
     * WebView 事件回调接口，用于解耦 WebViewClient/WebChromeClient 与 Activity。
     */
    public interface WebViewCallbacks {
        /** 页面操作是否启用 */
        boolean isPageActionsEnabled();
        /** 是否处于查找元素模式 */
        boolean isInspectMode();
        /** 是否处于夜间模式 */
        boolean isNightMode();
        /** 夜间模式 CSS 是否启用 */
        boolean isNightModeCSS();
        /** 文件选择回调持有者 */
        ValueCallback<Uri[]> getFilePathCallback();
        /** 设置文件选择回调 */
        void setFilePathCallback(ValueCallback<Uri[]> callback);
        /** 执行自定义脚本 */
        void onExecuteCustomScript(WebView webView);
        /** 注入夜间模式 CSS */
        void onInjectNightModeCSS(WebView webView);
        /** 移除夜间模式 CSS */
        void onRemoveNightModeCSS(WebView webView);
        /** 桌面模式缩放 */
        void onApplyDesktopZoom(WebView webView);
        /** 查找元素 */
        void onInspectElementAt(float x, float y);
        /** 显示错误页面 */
        void onShowErrorPage(WebView webView, String errorMsg);
        /** 获取 Activity（用于 startActivityForResult 等） */
        Context getContext();
        /** 启动 Activity for result */
        void startActivityForResult(Intent intent, int requestCode);
    }

    // ==================== 创建和配置 ====================

    /**
     * 创建 WebView。
     * 原 MainActivity.createWebView() 中的 WebView 创建部分。
     */
    public static WebView createWebView(Context context) {
        return new WebView(context);
    }

    /**
     * 配置 WebView（设置、WebViewClient、WebChromeClient）。
     * 原 MainActivity.setupWebView()
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static void setupWebView(WebView webView, SharedPreferences prefs,
            WebViewCallbacks callbacks, ProgressBar progressBar, View progressOverlay) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        String savedUA = prefs.getString("user_agent", "");
        if (!savedUA.isEmpty()) {
            settings.setUserAgentString(savedUA);
        } else {
            settings.setUserAgentString("Mozilla/5.0 (Linux; Android " + android.os.Build.VERSION.RELEASE + "; " + android.os.Build.MODEL + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + getChromeVersion(callbacks.getContext()) + " Mobile Safari/537.36");
        }
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setGeolocationEnabled(true);

        // 注册 JS 接口，用于 SPA 导航回调（防止重复注册）
        if (webView.getTag() == null) {
            webView.setTag("_webhub_registered");
            webView.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void onSpaNavigate() {
                    webView.post(() -> {
                        com.crm.webview.engine.PageActionEngine.stopMutationObserver(webView);
                        callbacks.onExecuteCustomScript(webView);
                    });
                }
            }, "_webhub");
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setOnTouchListener((v, event) -> {
            if (callbacks.isInspectMode() && event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                float density = callbacks.getContext().getResources().getDisplayMetrics().density;
                callbacks.onInspectElementAt(x / density, y / density);
                return true;
            }
            return false;
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowedUrl(request.getUrl().toString(), callbacks.getContext());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (webView != null && view == webView) {
                    progressBar.setVisibility(View.VISIBLE);
                    if (progressOverlay != null) {
                        progressOverlay.setVisibility(View.VISIBLE);
                        progressOverlay.setScaleX(0f);
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                if (progressOverlay != null && webView != null && view == webView) {
                    progressOverlay.animate()
                            .scaleX(1f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                progressOverlay.animate()
                                        .alpha(0f)
                                        .setDuration(300)
                                        .setStartDelay(100)
                                        .withEndAction(() -> {
                                            progressOverlay.setVisibility(View.GONE);
                                            progressOverlay.setAlpha(1f);
                                            progressOverlay.setScaleX(0f);
                                        })
                                        .start();
                            })
                            .start();
                }
                callbacks.onExecuteCustomScript(view);
                if (callbacks.isNightMode() && callbacks.isNightModeCSS()) {
                    callbacks.onInjectNightModeCSS(view);
                }
                if (view.getTag(R.id._webhub_desktop_mode) instanceof Boolean
                        && (Boolean) view.getTag(R.id._webhub_desktop_mode)) {
                    callbacks.onApplyDesktopZoom(view);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    callbacks.onShowErrorPage(view, error.getDescription() != null ? error.getDescription().toString() : "页面加载失败");
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (request.isForMainFrame() && errorResponse != null) {
                    int statusCode = errorResponse.getStatusCode();
                    if (statusCode >= 500) {
                        callbacks.onShowErrorPage(view, "服务器错误: " + statusCode);
                    }
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(callbacks.getContext(), "证书错误，已停止加载", Toast.LENGTH_SHORT).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (webView != null && view == webView) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    }
                    if (progressOverlay != null && progressOverlay.getVisibility() == View.VISIBLE) {
                        progressOverlay.setScaleX(newProgress / 100f);
                    }
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams fileChooserParams) {
                ValueCallback<Uri[]> existing = callbacks.getFilePathCallback();
                if (existing != null) {
                    existing.onReceiveValue(null);
                }
                callbacks.setFilePathCallback(callback);

                Intent intent = fileChooserParams.createIntent();
                try {
                    callbacks.startActivityForResult(intent, 1001);
                } catch (Exception e) {
                    callbacks.setFilePathCallback(null);
                    Toast.makeText(callbacks.getContext(), "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
    }

    // ==================== UA 和模式切换 ====================

    /**
     * 根据链接的桌面模式开关切换 WebView 渲染模式。
     * 原 MainActivity.applyDesktopModeUA()
     */
    public static void applyDesktopModeUA(WebView webView, boolean desktopMode) {
        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        if (desktopMode) {
            webView.setTag(R.id._webhub_saved_ua, settings.getUserAgentString());
            settings.setUserAgentString(DESKTOP_UA);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(false);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            webView.setTag(R.id._webhub_desktop_mode, true);
        } else {
            webView.setTag(R.id._webhub_desktop_mode, null);
            Object savedUA = webView.getTag(R.id._webhub_saved_ua);
            if (savedUA instanceof String && !((String) savedUA).isEmpty()) {
                settings.setUserAgentString((String) savedUA);
            }
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setSupportZoom(false);
            settings.setBuiltInZoomControls(false);
        }
    }

    /**
     * 桌面模式 viewport 缩放。
     * 原 MainActivity.applyDesktopZoom(WebView)
     */
    public static void applyDesktopZoom(WebView webView, float density, int screenWidthPx) {
        if (webView == null) return;
        int screenW = (int) (screenWidthPx / density);
        float scale = Math.max(0.2f, (float) screenW / 980f);
        String scaleStr = String.format("%.3f", scale);
        webView.evaluateJavascript(
            "(function(){" +
            "var m=document.querySelector('meta[name=viewport]');" +
            "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
            "m.content='width=980,initial-scale=" + scaleStr + ",minimum-scale=" + scaleStr + ",maximum-scale=3.0';" +
            "})()", null);
    }

    // ==================== 夜间模式 CSS ====================

    /**
     * 注入夜间模式 CSS。
     * 原 MainActivity.injectNightModeCSS()
     */
    public static void injectNightModeCSS(WebView webView) {
        String css = "var s=document.getElementById('wh-nm');" +
                "if(!s){s=document.createElement('style');s.id='wh-nm';document.head.appendChild(s);}" +
                "s.textContent='*{background-color:#1a1a1a!important;color:#ccc!important;border-color:#333!important}" +
                "a{color:#6db3f2!important}" +
                "input,textarea,select,button{background:#2a2a2a!important;color:#ccc!important}" +
                "img,video{opacity:.85}';";
        webView.evaluateJavascript("(function(){" + css + "})()", null);
    }

    /**
     * 移除夜间模式 CSS。
     * 原 MainActivity.removeNightModeCSS()
     */
    public static void removeNightModeCSS(WebView webView) {
        webView.evaluateJavascript("(function(){var s=document.getElementById('wh-nm');if(s)s.remove();})()", null);
    }

    // ==================== 工具方法 ====================

    /**
     * 获取 WebView 内置的 Chrome 版本号。
     * 原 MainActivity.getChromeVersion()
     */
    public static String getChromeVersion(Context context) {
        String ua = WebSettings.getDefaultUserAgent(context);
        int idx = ua.indexOf("Chrome/");
        if (idx >= 0) {
            int end = ua.indexOf(" ", idx);
            if (end > idx) return ua.substring(idx + 7, end);
        }
        return "125.0.0.0";
    }

    /**
     * 检查 URL 是否允许加载（处理 tel:, mailto:, sms: 等 scheme）。
     * 原 MainActivity.isAllowedUrl()
     */
    public static boolean isAllowedUrl(String url, Context context) {
        if (url == null) return false;

        if (url.startsWith("tel:")) {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
            if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(intent);
            } else {
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                context.startActivity(dial);
            }
            return false;
        }
        if (url.startsWith("mailto:")) {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
            context.startActivity(intent);
            return false;
        }
        if (url.startsWith("sms:")) {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
            context.startActivity(intent);
            return false;
        }

        if (url.startsWith("https://") || url.startsWith("http://")) return true;
        if (url.startsWith("javascript:") || url.startsWith("about:blank") || url.startsWith("data:")) return true;

        return false;
    }
}
