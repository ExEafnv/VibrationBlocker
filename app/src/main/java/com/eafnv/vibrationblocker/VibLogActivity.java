package com.eafnv.vibrationblocker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VibLogActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "vib_blocker_prefs";
    private static final String LOG_FILE_PATH = "/data/system/vib_blocker_logs";

    private SharedPreferences prefs;
    private Set<String> whitelist;
    private RecyclerView rv;
    private TextView tvEmpty;
    private Adapter adapter;
    private final List<LogItem> logs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vib_log);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadWhitelist();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        rv = findViewById(R.id.rv_logs);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new Adapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // ★ 新增：系统 / 用户过滤 ChipGroup
        com.google.android.material.chip.ChipGroup chipGroup = findViewById(R.id.chip_group_filter);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_user) {
                adapter.setFilterMode(1);
            } else if (checkedId == R.id.chip_system) {
                adapter.setFilterMode(2);
            } else {
                adapter.setFilterMode(0);
            }
        });

        loadLogsAsync();
    }

    private void loadWhitelist() {
        String s = prefs.getString("whitelist", "");
        whitelist = new HashSet<>();
        if (!s.isEmpty()) {
            whitelist.addAll(Arrays.asList(s.split(",")));
        }
    }

    private void loadLogsAsync() {
        Shell.cmd("cat " + LOG_FILE_PATH).submit(result -> {
            logs.clear();
            android.content.pm.PackageManager pm = getPackageManager();

            if (result.isSuccess()) {
                for (String line : result.getOut()) {
                    // 格式: 包名|usage|时间|重复次数
                    String[] parts = line.split("\\|");
                    if (parts.length >= 4) {
                        LogItem item = new LogItem();
                        item.pkg = parts[0];
                        item.usage = parts[1];
                        item.time = parts[2];
                        item.repeat = Integer.parseInt(parts[3]);

                        // ★ 判断是否为系统应用
                        try {
                            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(item.pkg, 0);
                            item.isSystem = (ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                        } catch (Exception e) {
                            // 如果找不到包名（可能被卸载了或者是个特殊UID），默认按名称猜
                            item.isSystem = item.pkg.contains("android") || item.pkg.contains("system");
                        }

                        logs.add(item);
                    }
                }
            }
            runOnUiThread(() -> {
                // ★ 让 Adapter 重新计算过滤
                adapter.updateData();
            });
        });
    }

    private void addToWhitelist(String pkg) {
        if (whitelist.contains(pkg)) return;
        whitelist.add(pkg);
        prefs.edit().putString("whitelist", TextUtils.join(",", whitelist)).apply();
        ConfigWriter.write(prefs);
        adapter.notifyDataSetChanged(); // 刷新按钮状态
        Toast.makeText(this, "已加入白名单", Toast.LENGTH_SHORT).show();
    }

    static class LogItem {
        String pkg;
        String usage;
        String time;
        int repeat;
        boolean isSystem; // ★ 新增
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        private final List<LogItem> shownLogs = new ArrayList<>();
        private int filterMode = 0; // 0=全部, 1=用户, 2=系统

        public void setFilterMode(int mode) {
            this.filterMode = mode;
            applyFilter();
        }

        public void updateData() {
            applyFilter();
        }

        private void applyFilter() {
            shownLogs.clear();
            for (LogItem item : logs) {
                if (filterMode == 1 && item.isSystem) continue;
                if (filterMode == 2 && !item.isSystem) continue;
                shownLogs.add(item);
            }
            tvEmpty.setVisibility(shownLogs.isEmpty() ? View.VISIBLE : View.GONE);
            rv.setVisibility(shownLogs.isEmpty() ? View.GONE : View.VISIBLE);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_vib_log, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            LogItem item = shownLogs.get(position); // ★ 使用 shownLogs
            h.tvPkg.setText(item.pkg);

            // 直接拼接原始数字，不再翻译
            String infoText = item.time + "  |  类型: " + item.usage;
            if (item.repeat > 1) {
                infoText += "  |  重复: " + item.repeat + "次";
            }
            h.tvInfo.setText(infoText);

            // 不显式 setTextColor，交给 XML 里的 ?attr/colorOnSurfaceVariant 自动适配深浅色

            // 白名单按钮逻辑不变
            if (whitelist.contains(item.pkg)) {
                h.btnAdd.setText("已加白");
                h.btnAdd.setEnabled(false);
                h.btnAdd.setAlpha(0.5f);
            } else {
                h.btnAdd.setText("加白");
                h.btnAdd.setEnabled(true);
                h.btnAdd.setAlpha(1f);
                h.btnAdd.setOnClickListener(v -> addToWhitelist(item.pkg));
            }
        }

        @Override
        public int getItemCount() {
            return shownLogs.size(); // ★ 返回 shownLogs 的大小
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvPkg, tvInfo;
            MaterialButton btnAdd;

            VH(@NonNull View v) {
                super(v);
                tvPkg = v.findViewById(R.id.tv_pkg);
                tvInfo = v.findViewById(R.id.tv_info);
                btnAdd = v.findViewById(R.id.btn_add);
            }
        }
    }
}