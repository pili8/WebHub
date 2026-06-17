package com.crm.webview;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpActivity extends AppCompatActivity {

    private TextView tvTitle, tvRequestName, tvResponse;
    private LinearLayout paramsContainer;
    private SharedPreferences prefs;

    private String requestName = "";
    private String requestUrl = "";
    private String requestMethod = "POST";
    private String requestHeaders = "";
    private String requestBody = "";

    private List<String> paramNames = new ArrayList<>();
    private List<EditText> paramInputs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_http);

        prefs = getSharedPreferences("http_config", MODE_PRIVATE);

        initViews();
        loadConfig();
        setupParamInputs();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvRequestName = findViewById(R.id.tvRequestName);
        tvResponse = findViewById(R.id.tvResponse);
        paramsContainer = findViewById(R.id.paramsContainer);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        ImageView btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, HttpConfigActivity.class));
        });

        findViewById(R.id.btnSend).setOnClickListener(v -> sendRequest());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfig();
        setupParamInputs();
    }

    private void loadConfig() {
        requestName = prefs.getString("request_name", "HTTP 请求");
        requestUrl = prefs.getString("request_url", "");
        requestMethod = prefs.getString("request_method", "POST");
        requestHeaders = prefs.getString("request_headers", "");
        requestBody = prefs.getString("request_body", "");

        tvTitle.setText(requestName);
        tvRequestName.setText(requestName);
    }

    private void setupParamInputs() {
        paramsContainer.removeAllViews();
        paramNames.clear();
        paramInputs.clear();

        // 从请求体中提取 {{参数名}} 占位符
        Pattern pattern = Pattern.compile("\\{\\{(.*?)\\}\\}");
        Matcher matcher = pattern.matcher(requestBody);

        while (matcher.find()) {
            String paramName = matcher.group(1);
            if (!paramNames.contains(paramName)) {
                paramNames.add(paramName);
            }
        }

        if (paramNames.isEmpty()) {
            paramsContainer.setVisibility(View.GONE);
            return;
        }

        paramsContainer.setVisibility(View.VISIBLE);

        for (String paramName : paramNames) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_param, paramsContainer, false);
            TextView tvParamName = itemView.findViewById(R.id.tvParamName);
            EditText etParamValue = itemView.findViewById(R.id.etParamValue);

            tvParamName.setText(paramName);
            paramInputs.add(etParamValue);

            paramsContainer.addView(itemView);
        }
    }

    private void sendRequest() {
        if (requestUrl.isEmpty()) {
            Toast.makeText(this, "请先配置请求 URL", Toast.LENGTH_SHORT).show();
            return;
        }

        tvResponse.setText("请求中...");
        tvResponse.setTextColor(Color.parseColor("#666666"));

        // 替换参数
        String finalBody = requestBody;
        for (int i = 0; i < paramNames.size(); i++) {
            if (i < paramInputs.size()) {
                String value = paramInputs.get(i).getText().toString().trim();
                finalBody = finalBody.replace("{{" + paramNames.get(i) + "}}", value);
            }
        }

        String finalUrl = requestUrl;
        String finalMethod = requestMethod;
        String finalHeaders = requestHeaders;

        new Thread(() -> {
            try {
                String response = makeRequest(finalUrl, finalMethod, finalHeaders, finalBody);
                String formatted = formatJson(response);

                new Handler(Looper.getMainLooper()).post(() -> {
                    tvResponse.setText(formatted);
                    tvResponse.setTextColor(Color.parseColor("#333333"));
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    tvResponse.setText("请求失败: " + e.getMessage());
                    tvResponse.setTextColor(Color.parseColor("#F44336"));
                });
            }
        }).start();
    }

    private String makeRequest(String urlStr, String method, String headers, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        // 设置请求头
        if (headers != null && !headers.isEmpty()) {
            String[] lines = headers.split("\n");
            for (String line : lines) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    conn.setRequestProperty(parts[0].trim(), parts[1].trim());
                }
            }
        }

        // 发送请求体
        if ("POST".equals(method) && body != null && !body.isEmpty()) {
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();
        }

        // 读取响应
        int responseCode = conn.getResponseCode();
        BufferedReader reader;
        if (responseCode >= 200 && responseCode < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();

        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception("HTTP " + responseCode + ": " + response.toString());
        }

        return response.toString();
    }

    private String formatJson(String json) {
        try {
            if (json.startsWith("{")) {
                JSONObject obj = new JSONObject(json);
                return obj.toString(2);
            } else if (json.startsWith("[")) {
                JSONArray arr = new JSONArray(json);
                return arr.toString(2);
            }
        } catch (Exception e) {
            // 解析失败，返回原始字符串
        }
        return json;
    }
}
