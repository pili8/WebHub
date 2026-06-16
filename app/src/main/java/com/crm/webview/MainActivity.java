package com.crm.webview;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // 默认值
    private static final String DEFAULT_URL_1 = "https://www.kdocs.cn/wo/sl/v12CEOZt";
    private static final String DEFAULT_URL_2 = "https://www.kdocs.cn/wo/sl/v14T2gpD";
    private static final String DEFAULT_URL_3 = "https://www.kdocs.cn/wo/sl/v13iHfr4";
    private static final String DEFAULT_TITLE_1 = "销售机会";
    private static final String DEFAULT_TITLE_2 = "最近新增";
    private static final String DEFAULT_TITLE_3 = "录入线索";

    private WebView webViewCard, webViewTable, webViewInput;
    private WebView[] webViews;
    private ProgressBar progressBar;
    private TextView tvTitle;
    private ImageView btnRefresh, btnSettings;
    private LinearLayout tabCard, tabTable, tabInput;
    private LinearLayout[] tabs;
    private ImageView iconCard, iconTable, iconInput;
    private ImageView[] icons;
    private TextView textCard, textTable, textInput;
    private TextView[] texts;
    private int currentTab = 0;
    private boolean[] webViewInitialized = {false, false, false};

    // 配置
    private String[] urls = new String[3];
    private String[] titles = new String[3];

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        loadConfig();

        initViews();
        setupTabClickListeners();
        setupButtons();

        setupWebView(webViewCard, 0);
        setupWebView(webViewTable, 1);
        setupWebView(webViewInput, 2);

        switchTab(0);
    }

    private void loadConfig() {
        urls[0] = prefs.getString("url1", DEFAULT_URL_1);
        urls[1] = prefs.getString("url2", DEFAULT_URL_2);
        urls[2] = prefs.getString("url3", DEFAULT_URL_3);
        titles[0] = prefs.getString("title1", DEFAULT_TITLE_1);
        titles[1] = prefs.getString("title2", DEFAULT_TITLE_2);
        titles[2] = prefs.getString("title3", DEFAULT_TITLE_3);
    }

    private void saveConfig() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("url1", urls[0]);
        editor.putString("url2", urls[1]);
        editor.putString("url3", urls[2]);
        editor.putString("title1", titles[0]);
        editor.putString("title2", titles[1]);
        editor.putString("title3", titles[2]);
        editor.apply();
    }

    private void initViews() {
        webViewCard = findViewById(R.id.webview_card);
        webViewTable = findViewById(R.id.webview_table);
        webViewInput = findViewById(R.id.webview_input);
        webViews = new WebView[]{webViewCard, webViewTable, webViewInput};

        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnSettings = findViewById(R.id.btnSettings);

        tabCard = findViewById(R.id.tab_card);
        tabTable = findViewById(R.id.tab_table);
        tabInput = findViewById(R.id.tab_input);
        tabs = new LinearLayout[]{tabCard, tabTable, tabInput};

        iconCard = findViewById(R.id.icon_card);
        iconTable = findViewById(R.id.icon_table);
        iconInput = findViewById(R.id.icon_input);
        icons = new ImageView[]{iconCard, iconTable, iconInput};

        textCard = findViewById(R.id.text_card);
        textTable = findViewById(R.id.text_table);
        textInput = findViewById(R.id.text_input);
        texts = new TextView[]{textCard, textTable, textInput};
    }

    private void setupTabClickListeners() {
        tabCard.setOnClickListener(v -> switchTab(0));
        tabTable.setOnClickListener(v -> switchTab(1));
        tabInput.setOnClickListener(v -> switchTab(2));
    }

    private void setupButtons() {
        btnRefresh.setOnClickListener(v -> refreshCurrentPage());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void refreshCurrentPage() {
        webViews[currentTab].reload();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);

        EditText etTitle1 = dialogView.findViewById(R.id.et_title1);
        EditText etUrl1 = dialogView.findViewById(R.id.et_url1);
        EditText etTitle2 = dialogView.findViewById(R.id.et_title2);
        EditText etUrl2 = dialogView.findViewById(R.id.et_url2);
        EditText etTitle3 = dialogView.findViewById(R.id.et_title3);
        EditText etUrl3 = dialogView.findViewById(R.id.et_url3);

        etTitle1.setText(titles[0]);
        etUrl1.setText(urls[0]);
        etTitle2.setText(titles[1]);
        etUrl2.setText(urls[1]);
        etTitle3.setText(titles[2]);
        etUrl3.setText(urls[2]);

        builder.setView(dialogView)
                .setTitle("视图配置")
                .setPositiveButton("保存", (dialog, which) -> {
                    titles[0] = etTitle1.getText().toString().trim();
                    urls[0] = etUrl1.getText().toString().trim();
                    titles[1] = etTitle2.getText().toString().trim();
                    urls[1] = etUrl2.getText().toString().trim();
                    titles[2] = etTitle3.getText().toString().trim();
                    urls[2] = etUrl3.getText().toString().trim();

                    saveConfig();
                    updateTabTitles();

                    // 重新加载当前选项卡
                    webViewInitialized[currentTab] = false;
                    webViews[currentTab].loadUrl(urls[currentTab]);
                    webViewInitialized[currentTab] = true;
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateTabTitles() {
        for (int i = 0; i < texts.length; i++) {
            texts[i].setText(titles[i]);
        }
        tvTitle.setText(titles[currentTab]);
    }

    private void switchTab(int index) {
        currentTab = index;
        tvTitle.setText(titles[index]);

        for (int i = 0; i < tabs.length; i++) {
            if (i == index) {
                icons[i].setColorFilter(Color.parseColor("#1976D2"));
                texts[i].setTextColor(Color.parseColor("#1976D2"));
            } else {
                icons[i].setColorFilter(Color.parseColor("#666666"));
                texts[i].setTextColor(Color.parseColor("#666666"));
            }
        }

        for (int i = 0; i < webViews.length; i++) {
            webViews[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }

        if (!webViewInitialized[index]) {
            webViews[index].loadUrl(urls[index]);
            webViewInitialized[index] = true;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(WebView webView, int index) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUserAgentString(settings.getUserAgentString().replace("; wv", ""));
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowedUrl(request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (view.getVisibility() == View.VISIBLE) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view.getVisibility() == View.VISIBLE) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    private boolean isAllowedUrl(String url) {
        if (url == null) return false;
        if (url.contains("kdocs.cn")) return true;
        if (url.contains("wps.cn")) return true;
        if (url.contains("wps.com")) return true;
        if (url.contains("klcdn.com")) return true;
        if (url.contains("wpscdn.com")) return true;
        if (url.contains("account.")) return true;
        if (url.startsWith("javascript:") || url.startsWith("about:blank")) return true;
        if (url.startsWith("data:")) return true;
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            WebView currentWebView = webViews[currentTab];
            closePopup(currentWebView);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void closePopup(WebView webView) {
        int[] location = new int[2];
        webView.getLocationOnScreen(location);

        float x = webView.getWidth() / 2f;
        float y = 15f;

        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        webView.dispatchTouchEvent(downEvent);
        downEvent.recycle();

        webView.postDelayed(() -> {
            MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
            webView.dispatchTouchEvent(upEvent);
            upEvent.recycle();
        }, 50);
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (WebView webView : webViews) {
            webView.onPause();
        }
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webViews[currentTab].onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (WebView webView : webViews) {
            if (webView != null) {
                webView.destroy();
            }
        }
    }
}
