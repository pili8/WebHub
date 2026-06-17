package com.crm.webview;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class HttpConfigActivity extends AppCompatActivity {

    private EditText etRequestName, etUrl, etBody;
    private RadioGroup rgMethod;
    private RadioButton rbGet, rbPost;
    private LinearLayout headersContainer;
    private SharedPreferences prefs;

    private List<View> headerViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_http_config);

        prefs = getSharedPreferences("http_config", MODE_PRIVATE);

        initViews();
        loadConfig();
    }

    private void initViews() {
        etRequestName = findViewById(R.id.etRequestName);
        etUrl = findViewById(R.id.etUrl);
        etBody = findViewById(R.id.etBody);
        rgMethod = findViewById(R.id.rgMethod);
        rbGet = findViewById(R.id.rbGet);
        rbPost = findViewById(R.id.rbPost);
        headersContainer = findViewById(R.id.headersContainer);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnSave).setOnClickListener(v -> saveConfig());

        findViewById(R.id.btnAddHeader).setOnClickListener(v -> addHeader("", ""));

        findViewById(R.id.btnTest).setOnClickListener(v -> testRequest());
    }

    private void loadConfig() {
        etRequestName.setText(prefs.getString("request_name", "HTTP 请求"));
        etUrl.setText(prefs.getString("request_url", ""));
        etBody.setText(prefs.getString("request_body", ""));

        String method = prefs.getString("request_method", "POST");
        if ("GET".equals(method)) {
            rbGet.setChecked(true);
        } else {
            rbPost.setChecked(true);
        }

        // 加载请求头
        String headers = prefs.getString("request_headers", "");
        headersContainer.removeAllViews();
        headerViews.clear();

        if (headers.isEmpty()) {
            addHeader("Content-Type", "application/json");
        } else {
            String[] lines = headers.split("\n");
            for (String line : lines) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    addHeader(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

    private void addHeader(String key, String value) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_header, headersContainer, false);
        EditText etKey = itemView.findViewById(R.id.etKey);
        EditText etValue = itemView.findViewById(R.id.etValue);
        TextView btnDelete = itemView.findViewById(R.id.btnDelete);

        etKey.setText(key);
        etValue.setText(value);

        btnDelete.setOnClickListener(v -> {
            headersContainer.removeView(itemView);
            headerViews.remove(itemView);
        });

        headerViews.add(itemView);
        headersContainer.addView(itemView);
    }

    private void saveConfig() {
        String name = etRequestName.getText().toString().trim();
        String url = etUrl.getText().toString().trim();
        String body = etBody.getText().toString().trim();
        String method = rbGet.isChecked() ? "GET" : "POST";

        if (name.isEmpty()) {
            Toast.makeText(this, "请输入请求名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (url.isEmpty()) {
            Toast.makeText(this, "请输入请求 URL", Toast.LENGTH_SHORT).show();
            return;
        }

        // 收集请求头
        StringBuilder headers = new StringBuilder();
        for (View view : headerViews) {
            EditText etKey = view.findViewById(R.id.etKey);
            EditText etValue = view.findViewById(R.id.etValue);
            String key = etKey.getText().toString().trim();
            String value = etValue.getText().toString().trim();
            if (!key.isEmpty()) {
                if (headers.length() > 0) headers.append("\n");
                headers.append(key).append(": ").append(value);
            }
        }

        // 保存配置
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("request_name", name);
        editor.putString("request_url", url);
        editor.putString("request_method", method);
        editor.putString("request_headers", headers.toString());
        editor.putString("request_body", body);
        editor.apply();

        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void testRequest() {
        String url = etUrl.getText().toString().trim();
        String body = etBody.getText().toString().trim();
        String method = rbGet.isChecked() ? "GET" : "POST";

        if (url.isEmpty()) {
            Toast.makeText(this, "请输入请求 URL", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "发送测试请求...", Toast.LENGTH_SHORT).show();

        // 收集请求头
        StringBuilder headers = new StringBuilder();
        for (View view : headerViews) {
            EditText etKey = view.findViewById(R.id.etKey);
            EditText etValue = view.findViewById(R.id.etValue);
            String key = etKey.getText().toString().trim();
            String value = etValue.getText().toString().trim();
            if (!key.isEmpty()) {
                if (headers.length() > 0) headers.append("\n");
                headers.append(key).append(": ").append(value);
            }
        }

        String finalHeaders = headers.toString();

        new Thread(() -> {
            try {
                String response = makeRequest(url, method, finalHeaders, body);
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "请求成功: " + response.substring(0, Math.min(100, response.length())), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "请求失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
}
