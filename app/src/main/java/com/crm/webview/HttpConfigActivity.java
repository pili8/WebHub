package com.crm.webview;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class HttpConfigActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HttpAdapter adapter;
    private SharedPreferences httpPrefs;
    private List<HttpConfig> configs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_http_config);

        httpPrefs = getSharedPreferences("http_configs", MODE_PRIVATE);

        initViews();
        loadConfigs();
        setupRecyclerView();
    }

    private void initViews() {
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v -> addNewConfig());

        Button btnTestAll = findViewById(R.id.btnTestAll);
        btnTestAll.setOnClickListener(v -> testAllRequests());
    }

    private void loadConfigs() {
        configs.clear();
        int count = httpPrefs.getInt("count", 0);

        // 如果没有配置，添加测试数据
        if (count == 0) {
            addTestConfigs();
            return;
        }

        for (int i = 0; i < count; i++) {
            HttpConfig config = new HttpConfig();
            config.name = httpPrefs.getString("name_" + i, "");
            config.url = httpPrefs.getString("url_" + i, "");
            config.method = httpPrefs.getString("method_" + i, "POST");
            config.headers = httpPrefs.getString("headers_" + i, "");
            config.body = httpPrefs.getString("body_" + i, "");
            config.response = "";
            configs.add(config);
        }
    }

    private void addTestConfigs() {
        // 测试配置1：GET 请求
        HttpConfig config1 = new HttpConfig();
        config1.name = "获取用户信息";
        config1.url = "https://jsonplaceholder.typicode.com/users/1";
        config1.method = "GET";
        config1.headers = "Content-Type: application/json";
        config1.body = "";
        configs.add(config1);

        // 测试配置2：POST 请求
        HttpConfig config2 = new HttpConfig();
        config2.name = "创建帖子";
        config2.url = "https://jsonplaceholder.typicode.com/posts";
        config2.method = "POST";
        config2.headers = "Content-Type: application/json";
        config2.body = "{\n  \"title\": \"{{title}}\",\n  \"body\": \"{{body}}\",\n  \"userId\": {{userId}}\n}";
        configs.add(config2);

        // 测试配置3：带认证的请求
        HttpConfig config3 = new HttpConfig();
        config3.name = "获取飞书数据";
        config3.url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
        config3.method = "POST";
        config3.headers = "Content-Type: application/json";
        config3.body = "{\n  \"app_id\": \"{{app_id}}\",\n  \"app_secret\": \"{{app_secret}}\"\n}";
        configs.add(config3);

        // 保存测试配置
        saveConfigs();
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HttpAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void addNewConfig() {
        HttpConfig config = new HttpConfig();
        config.name = "新请求";
        config.url = "";
        config.method = "POST";
        config.headers = "Content-Type: application/json";
        config.body = "{\n  \"key\": \"{{value}}\"\n}";
        configs.add(config);
        saveConfigs();
        adapter.notifyItemInserted(configs.size() - 1);
        recyclerView.scrollToPosition(configs.size() - 1);
    }

    private void saveConfigs() {
        SharedPreferences.Editor editor = httpPrefs.edit();
        editor.putInt("count", configs.size());

        for (int i = 0; i < configs.size(); i++) {
            HttpConfig config = configs.get(i);
            editor.putString("name_" + i, config.name);
            editor.putString("url_" + i, config.url);
            editor.putString("method_" + i, config.method);
            editor.putString("headers_" + i, config.headers);
            editor.putString("body_" + i, config.body);
        }

        editor.apply();
    }

    private void testAllRequests() {
        Toast.makeText(this, "测试所有请求...", Toast.LENGTH_SHORT).show();
        for (int i = 0; i < configs.size(); i++) {
            testRequest(i);
        }
    }

    private void testRequest(int index) {
        HttpConfig config = configs.get(index);
        if (config.url.isEmpty()) {
            config.response = "错误: URL 为空";
            adapter.notifyItemChanged(index);
            return;
        }

        config.response = "请求中...";
        adapter.notifyItemChanged(index);

        new Thread(() -> {
            try {
                String response = makeRequest(config.url, config.method, config.headers, config.body);
                config.response = formatJson(response);
            } catch (Exception e) {
                config.response = "错误: " + e.getMessage();
            }

            runOnUiThread(() -> adapter.notifyItemChanged(index));
        }).start();
    }

    private String makeRequest(String urlStr, String method, String headers, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        if (headers != null && !headers.isEmpty()) {
            String[] lines = headers.split("\n");
            for (String line : lines) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    conn.setRequestProperty(parts[0].trim(), parts[1].trim());
                }
            }
        }

        if ("POST".equals(method) && body != null && !body.isEmpty()) {
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();
        }

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
            throw new Exception("HTTP " + responseCode);
        }

        return response.toString();
    }

    private String formatJson(String json) {
        try {
            if (json.startsWith("{")) {
                org.json.JSONObject obj = new org.json.JSONObject(json);
                return obj.toString(2);
            } else if (json.startsWith("[")) {
                org.json.JSONArray arr = new org.json.JSONArray(json);
                return arr.toString(2);
            }
        } catch (Exception e) {
            // 解析失败
        }
        return json;
    }

    // 配置数据类
    static class HttpConfig {
        String name = "";
        String url = "";
        String method = "POST";
        String headers = "";
        String body = "";
        String response = "";
    }

    // RecyclerView Adapter
    class HttpAdapter extends RecyclerView.Adapter<HttpAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_http_config, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            HttpConfig config = configs.get(position);
            holder.bind(config, position);
        }

        @Override
        public int getItemCount() {
            return configs.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            EditText etName, etUrl, etHeaders, etBody;
            RadioGroup rgMethod;
            RadioButton rbGet, rbPost;
            Button btnTest, btnSave, btnDelete;
            TextView tvResponse;

            ViewHolder(View itemView) {
                super(itemView);
                etName = itemView.findViewById(R.id.etName);
                etUrl = itemView.findViewById(R.id.etUrl);
                etHeaders = itemView.findViewById(R.id.etHeaders);
                etBody = itemView.findViewById(R.id.etBody);
                rgMethod = itemView.findViewById(R.id.rgMethod);
                rbGet = itemView.findViewById(R.id.rbGet);
                rbPost = itemView.findViewById(R.id.rbPost);
                btnTest = itemView.findViewById(R.id.btnTest);
                btnSave = itemView.findViewById(R.id.btnSave);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                tvResponse = itemView.findViewById(R.id.tvResponse);
            }

            void bind(HttpConfig config, int position) {
                etName.setText(config.name);
                etUrl.setText(config.url);
                etHeaders.setText(config.headers);
                etBody.setText(config.body);
                tvResponse.setText(config.response);

                if ("GET".equals(config.method)) {
                    rbGet.setChecked(true);
                } else {
                    rbPost.setChecked(true);
                }

                // 测试按钮
                btnTest.setOnClickListener(v -> {
                    updateConfigFromView(config, position);
                    testRequest(position);
                });

                // 保存按钮
                btnSave.setOnClickListener(v -> {
                    updateConfigFromView(config, position);
                    saveConfigs();
                    Toast.makeText(itemView.getContext(), "已保存", Toast.LENGTH_SHORT).show();
                });

                // 删除按钮
                btnDelete.setOnClickListener(v -> {
                    configs.remove(position);
                    saveConfigs();
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, configs.size());
                });

                // 响应颜色
                if (config.response.startsWith("错误")) {
                    tvResponse.setTextColor(Color.parseColor("#F44336"));
                } else if (config.response.equals("请求中...")) {
                    tvResponse.setTextColor(Color.parseColor("#FF9800"));
                } else {
                    tvResponse.setTextColor(Color.parseColor("#4CAF50"));
                }
            }

            void updateConfigFromView(HttpConfig config, int position) {
                config.name = etName.getText().toString().trim();
                config.url = etUrl.getText().toString().trim();
                config.method = rbGet.isChecked() ? "GET" : "POST";
                config.headers = etHeaders.getText().toString().trim();
                config.body = etBody.getText().toString().trim();
            }
        }
    }
}
