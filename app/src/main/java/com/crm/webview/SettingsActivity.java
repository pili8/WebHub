package com.crm.webview;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private EditText etIcon1, etTitle1, etLinks1;
    private EditText etIcon2, etTitle2, etLinks2;
    private EditText etIcon3, etTitle3, etLinks3;

    private LinearLayout actionsContainer1, actionsContainer2, actionsContainer3;
    private TextView btnAddAction1, btnAddAction2, btnAddAction3;

    private SharedPreferences prefs;

    private static final String[] ACTION_TYPES = {"隐藏", "点击", "修改"};

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

        actionsContainer1 = findViewById(R.id.actionsContainer1);
        actionsContainer2 = findViewById(R.id.actionsContainer2);
        actionsContainer3 = findViewById(R.id.actionsContainer3);

        btnAddAction1 = findViewById(R.id.btnAddAction1);
        btnAddAction2 = findViewById(R.id.btnAddAction2);
        btnAddAction3 = findViewById(R.id.btnAddAction3);
    }

    private void setupListeners() {
        btnAddAction1.setOnClickListener(v -> addActionRow(actionsContainer1));
        btnAddAction2.setOnClickListener(v -> addActionRow(actionsContainer2));
        btnAddAction3.setOnClickListener(v -> addActionRow(actionsContainer3));

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void addActionRow(LinearLayout container) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_script_action, container, false);

        Spinner spinnerAction = row.findViewById(R.id.spinnerAction);
        EditText etSelector = row.findViewById(R.id.etSelector);
        EditText etValue = row.findViewById(R.id.etValue);
        TextView btnDelete = row.findViewById(R.id.btnDelete);

        // 设置操作类型下拉
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ACTION_TYPES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAction.setAdapter(adapter);

        // 根据操作类型显示/隐藏值输入框
        spinnerAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                etValue.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 删除按钮
        btnDelete.setOnClickListener(v -> container.removeView(row));

        container.addView(row);
    }

    private void loadConfig() {
        etIcon1.setText(prefs.getString("icon1", "📊"));
        etTitle1.setText(prefs.getString("title1", "销售机会"));
        etLinks1.setText(prefs.getString("links1", "销售机会,https://www.kdocs.cn/wo/sl/v12CEOZt"));

        etIcon2.setText(prefs.getString("icon2", "📋"));
        etTitle2.setText(prefs.getString("title2", "最近新增"));
        etLinks2.setText(prefs.getString("links2", "最近新增,https://www.kdocs.cn/wo/sl/v14T2gpD"));

        etIcon3.setText(prefs.getString("icon3", "➕"));
        etTitle3.setText(prefs.getString("title3", "录入线索"));
        etLinks3.setText(prefs.getString("links3", "录入线索,https://www.kdocs.cn/wo/sl/v13iHfr4"));

        // 加载操作列表
        loadActions(actionsContainer1, prefs.getString("actions1", ""));
        loadActions(actionsContainer2, prefs.getString("actions2", ""));
        loadActions(actionsContainer3, prefs.getString("actions3", ""));
    }

    private void loadActions(LinearLayout container, String actionsStr) {
        container.removeAllViews();
        if (actionsStr == null || actionsStr.isEmpty()) return;

        String[] lines = actionsStr.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\\|", 3);
            if (parts.length >= 2) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_script_action, container, false);
                Spinner spinner = row.findViewById(R.id.spinnerAction);
                EditText etSelector = row.findViewById(R.id.etSelector);
                EditText etValue = row.findViewById(R.id.etValue);
                TextView btnDelete = row.findViewById(R.id.btnDelete);

                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, ACTION_TYPES);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);

                // 设置操作类型
                int actionIndex = 0;
                if ("hide".equals(parts[0])) actionIndex = 0;
                else if ("click".equals(parts[0])) actionIndex = 1;
                else if ("modify".equals(parts[0])) actionIndex = 2;
                spinner.setSelection(actionIndex);

                // 设置选择器
                etSelector.setText(parts[1]);

                // 设置值（如果有）
                if (parts.length > 2 && !parts[2].isEmpty()) {
                    etValue.setText(parts[2]);
                    etValue.setVisibility(View.VISIBLE);
                }

                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        etValue.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                btnDelete.setOnClickListener(v -> container.removeView(row));

                container.addView(row);
            }
        }
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

        // 保存操作列表
        String actions1 = getActionsString(actionsContainer1);
        String actions2 = getActionsString(actionsContainer2);
        String actions3 = getActionsString(actionsContainer3);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("icon1", icon1);
        editor.putString("title1", title1);
        editor.putString("links1", links1);
        editor.putString("actions1", actions1);

        editor.putString("icon2", icon2);
        editor.putString("title2", title2);
        editor.putString("links2", links2);
        editor.putString("actions2", actions2);

        editor.putString("icon3", icon3);
        editor.putString("title3", title3);
        editor.putString("links3", links3);
        editor.putString("actions3", actions3);

        editor.apply();

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String getActionsString(LinearLayout container) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            Spinner spinner = row.findViewById(R.id.spinnerAction);
            EditText etSelector = row.findViewById(R.id.etSelector);
            EditText etValue = row.findViewById(R.id.etValue);

            String selector = etSelector.getText().toString().trim();
            if (selector.isEmpty()) continue;

            String action;
            int pos = spinner.getSelectedItemPosition();
            if (pos == 0) action = "hide";
            else if (pos == 1) action = "click";
            else action = "modify";

            String value = etValue.getText().toString().trim();

            if (sb.length() > 0) sb.append("\n");
            sb.append(action).append("|").append(selector);
            if (pos == 2 && !value.isEmpty()) {
                sb.append("|").append(value);
            }
        }
        return sb.toString();
    }
}
