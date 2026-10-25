package com.eafnv.vibrationblocker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WhitelistActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "vib_blocker_prefs";

    private SharedPreferences prefs;
    private RecyclerView rv;
    private View pbLoading;
    private TextView tvEmpty;
    private Adapter adapter;
    private TextInputEditText etSearch; // 提取为全局变量，方便清空后刷新

    private final List<AppItem> allApps = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();

    private final ExecutorService iconExecutor = Executors.newFixedThreadPool(4);
    private final LruCache<String, Drawable> iconCache = new LruCache<>(300);

    private Drawable defaultIcon;

    private int currentFilterMode = 0; // 0=全部, 1=用户, 2=系统

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whitelist);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        defaultIcon = getPackageManager().getDefaultActivityIcon();

        String s = prefs.getString("whitelist", "");
        if (!s.isEmpty()) {
            for (String p : s.split(",")) {
                if (!p.trim().isEmpty()) selected.add(p.trim());
            }
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // ★ 新增：进入震动记录页
        findViewById(R.id.btn_vib_log).setOnClickListener(v ->
                startActivity(new Intent(this, VibLogActivity.class)));

        etSearch = findViewById(R.id.et_search);
        rv = findViewById(R.id.rv_apps);
        pbLoading = findViewById(R.id.pb_loading);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new Adapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.filter(s == null ? "" : s.toString().trim());
            }
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            prefs.edit().putString("whitelist", TextUtils.join(",", selected)).apply();
            ConfigWriter.write(prefs);
            // 改为弹窗提示
            android.widget.Toast.makeText(this, "已保存", android.widget.Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            selected.clear();
            // ★ 清空后触发一次过滤，让列表恢复原始排序
            adapter.filter(etSearch.getText() != null ? etSearch.getText().toString() : "");
        });

        // ★ 新增：系统 / 用户过滤 ChipGroup
        com.google.android.material.chip.ChipGroup chipGroup = findViewById(R.id.chip_group_filter);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_user) {
                currentFilterMode = 1;
            } else if (checkedId == R.id.chip_system) {
                currentFilterMode = 2;
            } else {
                currentFilterMode = 0;
            }
            // 重新触发搜索过滤
            adapter.filter(etSearch.getText() != null ? etSearch.getText().toString() : "");
        });

        loadAppsAsync();
    }

    // ★ 新增 onResume，确保从记录页返回时，白名单列表状态同步刷新
    @Override
    protected void onResume() {
        super.onResume();
        // 重新读取配置，因为可能在 VibLogActivity 中加了白名单
        selected.clear();
        String s = prefs.getString("whitelist", "");
        if (!s.isEmpty()) {
            for (String p : s.split(",")) {
                if (!p.trim().isEmpty()) selected.add(p.trim());
            }
        }
        // 刷新列表的勾选状态
        if (adapter != null) {
            adapter.filter(etSearch.getText() != null ? etSearch.getText().toString() : "");
        }
    }

    private void loadAppsAsync() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> list = pm.getInstalledApplications(0);
            String selfPkg = getPackageName();

            final List<AppItem> items = new ArrayList<>();
            for (ApplicationInfo ai : list) {
                AppItem it = new AppItem();
                it.pkg = ai.packageName;
                it.label = ai.loadLabel(pm).toString();
                it.isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                items.add(it);
            }

            // 初始按 系统优先 + 字母排序
            Collections.sort(items, new Comparator<AppItem>() {
                @Override
                public int compare(AppItem a, AppItem b) {
                    if (a.isSystem != b.isSystem) return a.isSystem ? -1 : 1;
                    return a.label.compareToIgnoreCase(b.label);
                }
            });

            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(items);
                pbLoading.setVisibility(View.GONE);
                adapter.filter("");
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        iconExecutor.shutdown();
    }

    static class AppItem {
        String pkg;
        String label;
        boolean isSystem;
    }

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        private final List<AppItem> shown = new ArrayList<>();

        void filter(String q) {
            shown.clear();
            String lower = q == null ? "" : q.toLowerCase();

            for (AppItem a : allApps) {
                // ★ 1. 先按系统/用户类型过滤
                if (currentFilterMode == 1 && a.isSystem) continue;
                if (currentFilterMode == 2 && !a.isSystem) continue;

                // ★ 2. 再按搜索词过滤
                if (lower.isEmpty()) {
                    shown.add(a);
                } else {
                    if (a.label.toLowerCase().contains(lower)
                            || a.pkg.toLowerCase().contains(lower)) {
                        shown.add(a);
                    }
                }
            }

            // ★ 核心排序：已选中的置顶 -> 系统应用置顶 -> 字母排序
            Collections.sort(shown, new Comparator<AppItem>() {
                @Override
                public int compare(AppItem a, AppItem b) {
                    boolean aSel = selected.contains(a.pkg);
                    boolean bSel = selected.contains(b.pkg);

                    // 1. 是否已勾选
                    if (aSel != bSel) return aSel ? -1 : 1;

                    // 2. 是否系统应用
                    if (a.isSystem != b.isSystem) return a.isSystem ? -1 : 1;

                    // 3. 字母顺序
                    return a.label.compareToIgnoreCase(b.label);
                }
            });

            tvEmpty.setVisibility(shown.isEmpty() && !allApps.isEmpty()
                    ? View.VISIBLE : View.GONE);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            AppItem item = shown.get(position);
            final String pkg = item.pkg;

            h.tvName.setText(item.label);
            h.tvPkg.setText(item.pkg);

            // 先清掉旧监听，避免 RecyclerView 复用时错乱
            h.cb.setOnCheckedChangeListener(null);
            h.cb.setChecked(selected.contains(pkg));

            // 不再禁用 cb 的点击，让 row 和 cb 都能触发切换
            h.cb.setClickable(true);
            h.cb.setFocusable(false);
            h.cb.setFocusableInTouchMode(false);

            // 显式打开触感反馈（部分 ROM 默认可能被关掉）
            h.row.setHapticFeedbackEnabled(true);
            h.cb.setHapticFeedbackEnabled(true);

            h.ivIcon.setImageDrawable(defaultIcon);
            h.ivIcon.setTag(pkg);
            Drawable cached = iconCache.get(pkg);
            if (cached != null) {
                h.ivIcon.setImageDrawable(cached);
            } else {
                loadIcon(pkg, h.ivIcon);
            }

            // 统一的切换逻辑：row 和 cb 都调它
            View.OnClickListener toggle = v -> {
                android.util.Log.d("VibBlocker", "whitelist toggle, pkg=" + pkg);
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                boolean now = !selected.contains(pkg);
                if (now) selected.add(pkg);
                else     selected.remove(pkg);
                h.cb.setChecked(now);
                // 不立刻 filter()，避免列表跳动导致点错
            };

            h.row.setOnClickListener(toggle);
            h.cb.setOnClickListener(toggle);
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        class VH extends RecyclerView.ViewHolder {
            LinearLayout row;
            ImageView ivIcon;
            TextView tvName, tvPkg;
            MaterialCheckBox cb;

            VH(@NonNull View v) {
                super(v);
                row    = v.findViewById(R.id.row);
                ivIcon = v.findViewById(R.id.iv_icon);
                tvName = v.findViewById(R.id.tv_name);
                tvPkg  = v.findViewById(R.id.tv_pkg);
                cb     = v.findViewById(R.id.cb);
            }
        }
    }

    private void loadIcon(String pkg, ImageView iv) {
        iconExecutor.execute(() -> {
            try {
                Drawable d = getPackageManager().getApplicationIcon(pkg);
                iconCache.put(pkg, d);
                runOnUiThread(() -> {
                    if (pkg.equals(iv.getTag())) {
                        iv.setImageDrawable(d);
                    }
                });
            } catch (Throwable ignored) {}
        });
    }
}