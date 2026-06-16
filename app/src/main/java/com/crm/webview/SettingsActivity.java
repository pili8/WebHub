package com.crm.webview;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText etIcon1, etTitle1, etLinks1, etScript1;
    private EditText etIcon2, etTitle2, etLinks2, etScript2;
    private EditText etIcon3, etTitle3, etLinks3, etScript3;
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
        etScript1 = findViewById(R.id.et_script1);

        etIcon2 = findViewById(R.id.et_icon2);
        etTitle2 = findViewById(R.id.et_title2);
        etLinks2 = findViewById(R.id.et_links2);
        etScript2 = findViewById(R.id.et_script2);

        etIcon3 = findViewById(R.id.et_icon3);
        etTitle3 = findViewById(R.id.et_title3);
        etLinks3 = findViewById(R.id.et_links3);
        etScript3 = findViewById(R.id.et_script3);
    }

    private void loadConfig() {
        etIcon1.setText(prefs.getString("icon1", "📊"));
        etTitle1.setText(prefs.getString("title1", "销售机会"));
        etLinks1.setText(prefs.getString("links1", "销售机会,https://www.kdocs.cn/wo/sl/v12CEOZt"));
        etScript1.setText(prefs.getString("script1", ""));

        etIcon2.setText(prefs.getString("icon2", "📋"));
        etTitle2.setText(prefs.getString("title2", "最近新增"));
        etLinks2.setText(prefs.getString("links2", "最近新增,https://www.kdocs.cn/wo/sl/v14T2gpD"));
        etScript2.setText(prefs.getString("script2", ""));

        etIcon3.setText(prefs.getString("icon3", "➕"));
        etTitle3.setText(prefs.getString("title3", "录入线索"));
        etLinks3.setText(prefs.getString("links3", "录入线索,https://www.kdocs.cn/wo/sl/v13iHfr4"));
        etScript3.setText(prefs.getString("script3", ""));
    }

    private void setupListeners() {
        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void saveConfig() {
        String icon1 = etIcon1.getText().toString().trim();
        String title1 = etTitle1.getText().toString().trim();
        String links1 = etLinks1.getText().toString().trim();
        String script1 = etScript1.getText().toString().trim();

        String icon2 = etIcon2.getText().toString().trim();
        String title2 = etTitle2.getText().toString().trim();
        String links2 = etLinks2.getText().toString().trim();
        String script2 = etScript2.getText().toString().trim();

        String icon3 = etIcon3.getText().toString().trim();
        String title3 = etTitle3.getText().toString().trim();
        String links3 = etLinks3.getText().toString().trim();
        String script3 = etScript3.getText().toString().trim();

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

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("icon1", icon1);
        editor.putString("title1", title1);
        editor.putString("links1", links1);
        editor.putString("script1", script1);

        editor.putString("icon2", icon2);
        editor.putString("title2", title2);
        editor.putString("links2", links2);
        editor.putString("script2", script2);

        editor.putString("icon3", icon3);
        editor.putString("title3", title3);
        editor.putString("links3", links3);
        editor.putString("script3", script3);

        editor.apply();

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
