package com.crm.webview;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private static final String DEFAULT_CONFIG =
        "tab1|📊|销售机会|销售机会,https://www.kdocs.cn/wo/sl/v12CEOZt\n" +
        "tab2|📋|最近新增|最近新增,https://www.kdocs.cn/wo/sl/v14T2gpD\n" +
        "tab3|➕|录入线索|录入线索,https://www.kdocs.cn/wo/sl/v13iHfr4";

    private EditText etIcon1, etTitle1, etLinks1;
    private EditText etIcon2, etTitle2, etLinks2;
    private EditText etIcon3, etTitle3, etLinks3;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("app_config", MODE_PRIVATE);

        initViews();
        loadConfig();
        setupListeners();
    }

    private void initViews() {
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        etIcon1 = findViewById(R.id.et_icon1);
        etTitle1 = findViewById(R.id.et_title1);
        etLinks1 = findViewById(R.id.et_links1);

        etIcon2 = findViewById(R.id.et_icon2);
        etTitle2 = findViewById(R.id.et_title2);
        etLinks2 = findViewById(R.id.et_links2);

        etIcon3 = findViewById(R.id.et_icon3);
        etTitle3 = findViewById(R.id.et_title3);
        etLinks3 = findViewById(R.id.et_links3);
    }

    private void loadConfig() {
        String config = prefs.getString("config", DEFAULT_CONFIG);
        String[] lines = config.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\|", 4);
            if (parts.length < 4) continue;

            int tabIndex;
            try {
                tabIndex = Integer.parseInt(parts[0].replace("tab", ""));
            } catch (Exception e) {
                continue;
            }

            String icon = parts[1];
            String title = parts[2];
            String links = parts[3].replace("\\n", "\n");

            switch (tabIndex) {
                case 1:
                    etIcon1.setText(icon);
                    etTitle1.setText(title);
                    etLinks1.setText(links);
                    break;
                case 2:
                    etIcon2.setText(icon);
                    etTitle2.setText(title);
                    etLinks2.setText(links);
                    break;
                case 3:
                    etIcon3.setText(icon);
                    etTitle3.setText(title);
                    etLinks3.setText(links);
                    break;
            }
        }
    }

    private void setupListeners() {
        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void saveConfig() {
        String icon1 = etIcon1.getText().toString().trim();
        String title1 = etTitle1.getText().toString().trim();
        String links1 = etLinks1.getText().toString().trim();

        String icon2 = etIcon2.getText().toString().trim();
        String title2 = etTitle2.getText().toString().trim();
        String links2 = etLinks2.getText().toString().trim();

        String icon3 = etIcon3.getText().toString().trim();
        String title3 = etTitle3.getText().toString().trim();
        String links3 = etLinks3.getText().toString().trim();

        if (icon1.isEmpty()) icon1 = "📊";
        if (icon2.isEmpty()) icon2 = "📋";
        if (icon3.isEmpty()) icon3 = "➕";

        if (title1.isEmpty() || title2.isEmpty() || title3.isEmpty()) {
            Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (links1.isEmpty() || links2.isEmpty() || links3.isEmpty()) {
            Toast.makeText(this, "链接不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建配置字符串
        StringBuilder config = new StringBuilder();
        config.append("tab1|").append(icon1).append("|").append(title1).append("|").append(links1);
        config.append("\n");
        config.append("tab2|").append(icon2).append("|").append(title2).append("|").append(links2);
        config.append("\n");
        config.append("tab3|").append(icon3).append("|").append(title3).append("|").append(links3);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("config", config.toString());
        editor.apply();

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
