package com.eafnv.vibrationblocker;

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

    private final List<AppItem> allApps = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();

    private final ExecutorService iconExecutor = Executors.newFixedThreadPool(4);
    private final LruCache<String, Drawable> iconCache = new LruCache<>(300);

    private Drawable defaultIcon;

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

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextInputEditText etSearch = findViewById(R.id.et_search);
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
            finish();
        });

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            selected.clear();
            adapter.notifyDataSetChanged();
        });

        loadAppsAsync();
    }

    private void loadAppsAsync() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> list = pm.getInstalledApplications(0);
            String selfPkg = getPackageName();

            final List<AppItem> items = new ArrayList<>();
            for (ApplicationInfo ai : list) {
                if (ai.packageName.equals(selfPkg)) continue;
                AppItem it = new AppItem();
                it.pkg = ai.packageName;
                it.label = ai.loadLabel(pm).toString();
                it.isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                items.add(it);
            }
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
            if (q.isEmpty()) {
                shown.addAll(allApps);
            } else {
                String lower = q.toLowerCase();
                for (AppItem a : allApps) {
                    if (a.label.toLowerCase().contains(lower)
                            || a.pkg.toLowerCase().contains(lower)) {
                        shown.add(a);
                    }
                }
            }
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
            final String pkg = item.pkg;   // ← final 捕获，稳定

            h.tvName.setText(item.label);
            h.tvPkg.setText(item.pkg);
            h.cb.setChecked(selected.contains(pkg));

            // ★ 关键：在 Java 里显式禁用 checkbox 的点击和焦点
            //   这样它的所有触摸都会冒泡到父级 row
            h.cb.setClickable(false);
            h.cb.setFocusable(false);
            h.cb.setFocusableInTouchMode(false);

            h.ivIcon.setImageDrawable(defaultIcon);
            h.ivIcon.setTag(pkg);
            Drawable cached = iconCache.get(pkg);
            if (cached != null) {
                h.ivIcon.setImageDrawable(cached);
            } else {
                loadIcon(pkg, h.ivIcon);
            }

            // 点击绑定在 row 上，切换 selected + 更新 checkbox
            h.row.setOnClickListener(v -> {
                boolean now = !selected.contains(pkg);
                if (now) selected.add(pkg);
                else     selected.remove(pkg);
                h.cb.setChecked(now);
            });
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