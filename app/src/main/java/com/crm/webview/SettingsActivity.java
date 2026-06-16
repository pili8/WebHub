package com.crm.webview;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText etTitle1, etUrl1, etTitle2, etUrl2, etTitle3, etUrl3;
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

        etTitle1 = findViewById(R.id.et_title1);
        etUrl1 = findViewById(R.id.et_url1);
        etTitle2 = findViewById(R.id.et_title2);
        etUrl2 = findViewById(R.id.et_url2);
        etTitle3 = findViewById(R.id.et_title3);
        etUrl3 = findViewById(R.id.et_url3);
    }

    private void loadConfig() {
        etTitle1.setText(prefs.getString("title1", "销售机会"));
        etUrl1.setText(prefs.getString("url1", "https://www.kdocs.cn/wo/sl/v12CEOZt"));
        etTitle2.setText(prefs.getString("title2", "最近新增"));
        etUrl2.setText(prefs.getString("url2", "https://www.kdocs.cn/wo/sl/v14T2gpD"));
        etTitle3.setText(prefs.getString("title3", "录入线索"));
        etUrl3.setText(prefs.getString("url3", "https://www.kdocs.cn/wo/sl/v13iHfr4"));
    }

    private void setupListeners() {
        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void saveConfig() {
        String title1 = etTitle1.getText().toString().trim();
        String url1 = etUrl1.getText().toString().trim();
        String title2 = etTitle2.getText().toString().trim();
        String url2 = etUrl2.getText().toString().trim();
        String title3 = etTitle3.getText().toString().trim();
        String url3 = etUrl3.getText().toString().trim();

        if (url1.isEmpty() || url2.isEmpty() || url3.isEmpty()) {
            Toast.makeText(this, "网址不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("title1", title1);
        editor.putString("url1", url1);
        editor.putString("title2", title2);
        editor.putString("url2", url2);
        editor.putString("title3", title3);
        editor.putString("url3", url3);
        editor.apply();

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
