package com.eafnv.vibrationblocker;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.topjohnwu.superuser.Shell;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "vib_blocker_prefs";

    static {
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(30));
    }

    private SharedPreferences prefs;

    // UI 控件
    private TextView tvStart, tvEnd, tvStatus, tvWhitelistSummary, tvModuleStatus;
    private MaterialSwitch switchManual;
    private RadioGroup rgManual;
    private RadioButton rbBlockAll, rbUnblockAll;
    private View timeSection;
    private View statusDot;
    private View statusDotModule;

    // 状态
    private boolean rootReady = false;    // root 是否就绪
    private boolean moduleActive = false; // LSPosed 模块是否已激活

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 绑定控件
        tvStart            = findViewById(R.id.tv_start_time);
        tvEnd              = findViewById(R.id.tv_end_time);
        tvStatus           = findViewById(R.id.tv_status);
        tvWhitelistSummary = findViewById(R.id.tv_whitelist_summary);
        tvModuleStatus     = findViewById(R.id.tv_module_status);
        statusDot          = findViewById(R.id.status_dot);
        statusDotModule    = findViewById(R.id.status_dot_module);
        switchManual       = findViewById(R.id.switch_manual);
        rgManual           = findViewById(R.id.rg_manual);
        rbBlockAll         = findViewById(R.id.rb_block_all);
        rbUnblockAll       = findViewById(R.id.rb_unblock_all);
        timeSection        = findViewById(R.id.time_section);

        // 等待 root，期间禁用交互
        setUiEnabled(false);

        Shell.getShell(shell -> {
            if (!shell.isRoot()) {
                runOnUiThread(this::showNoRootAndExit);
                return;
            }
            runOnUiThread(this::onRootReady);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从白名单页返回时刷新摘要与配置
        if (rootReady) {
            updateWhitelistSummary();
            ConfigWriter.write(prefs);
            checkModuleActive();
        }
    }

    // ============================================================
    // 初始化
    // ============================================================

    private void onRootReady() {
        rootReady = true;
        setUiEnabled(true);

        boolean manual   = prefs.getBoolean("manual_mode", false);
        boolean blockAll = prefs.getBoolean("manual_block_all", true);

        switchManual.setChecked(manual);
        if (blockAll) rbBlockAll.setChecked(true);
        else          rbUnblockAll.setChecked(true);

        applyUiState(manual);
        updateTimeDisplay();
        updateWhitelistSummary();
        updateStatus();

        // 手动模式开关
        switchManual.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("manual_mode", isChecked).apply();
            applyUiState(isChecked);
            ConfigWriter.write(prefs);
            updateStatus();
        });

        // 手动模式下二选一
        rgManual.setOnCheckedChangeListener((group, checkedId) -> {
            boolean ba = (checkedId == R.id.rb_block_all);
            prefs.edit().putBoolean("manual_block_all", ba).apply();
            ConfigWriter.write(prefs);
            updateStatus();
        });

        // 时间行点击
        findViewById(R.id.row_start_time).setOnClickListener(v -> pickTime(true));
        findViewById(R.id.row_end_time).setOnClickListener(v -> pickTime(false));

        // 白名单入口
        findViewById(R.id.row_whitelist).setOnClickListener(v ->
                startActivity(new Intent(this, WhitelistActivity.class)));

        // 首次写入配置 + 检测激活状态
        ConfigWriter.write(prefs);
        checkModuleActive();
    }

    // ============================================================
    // 模块激活检测
    // ============================================================

    /** 模块进程被 LSPosed 注入时，ModuleActive.isActive() 会被 hook 成 true */
    private void checkModuleActive() {
        boolean active = false;
        try {
            active = ModuleActive.isActive();
        } catch (Throwable ignored) {}
        updateModuleStatus(active);
    }

    private void updateModuleStatus(boolean active) {
        this.moduleActive = active;

        if (active) {
            tvModuleStatus.setText("模块已激活");
            statusDotModule.setBackgroundResource(R.drawable.status_dot_on);
        } else {
            tvModuleStatus.setText("模块未激活");
            statusDotModule.setBackgroundResource(R.drawable.status_dot_off);
        }

        // 未激活时同步刷新下方状态卡
        updateStatus();
    }

    // ============================================================
    // 状态刷新
    // ============================================================

    private void updateStatus() {
        // 模块未激活，直接显示未激活状态
        if (!moduleActive) {
            tvStatus.setText("模块未激活");
            statusDot.setBackgroundResource(R.drawable.status_dot_off);
            return;
        }

        boolean manual   = prefs.getBoolean("manual_mode", false);
        boolean blockAll = prefs.getBoolean("manual_block_all", true);

        boolean blocked;
        if (manual) {
            blocked = blockAll;
        } else {
            int sh = prefs.getInt("start_hour", 22);
            int sm = prefs.getInt("start_min", 0);
            int eh = prefs.getInt("end_hour", 7);
            int em = prefs.getInt("end_min", 0);
            int startMin = sh * 60 + sm;
            int endMin   = eh * 60 + em;

            Calendar cal = Calendar.getInstance();
            int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

            if (startMin == endMin) {
                blocked = false;
            } else if (startMin < endMin) {
                blocked = now >= startMin && now < endMin;
            } else {
                blocked = now >= startMin || now < endMin;
            }
        }

        if (blocked) {
            tvStatus.setText("正在屏蔽所有振动");
            statusDot.setBackgroundResource(R.drawable.status_dot_on);
        } else {
            tvStatus.setText("未在屏蔽时段");
            statusDot.setBackgroundResource(R.drawable.status_dot_off);
        }
    }

    private void updateWhitelistSummary() {
        String s = prefs.getString("whitelist", "");
        if (s.isEmpty()) {
            tvWhitelistSummary.setText("未设置");
        } else {
            int n = 0;
            for (String p : s.split(",")) if (!p.trim().isEmpty()) n++;
            tvWhitelistSummary.setText("已选 " + n + " 个应用");
        }
    }

    private void updateTimeDisplay() {
        int sh = prefs.getInt("start_hour", 22);
        int sm = prefs.getInt("start_min", 0);
        int eh = prefs.getInt("end_hour", 7);
        int em = prefs.getInt("end_min", 0);
        tvStart.setText(String.format("%02d:%02d", sh, sm));
        tvEnd.setText(String.format("%02d:%02d", eh, em));
    }

    // ============================================================
    // 界面交互
    // ============================================================

    private void setUiEnabled(boolean enabled) {
        switchManual.setEnabled(enabled);
        rbBlockAll.setEnabled(enabled);
        rbUnblockAll.setEnabled(enabled);
        timeSection.setAlpha(enabled ? 1f : 0.4f);
    }

    private void applyUiState(boolean manual) {
        if (manual) {
            rgManual.setVisibility(View.VISIBLE);
            timeSection.setVisibility(View.GONE);
        } else {
            rgManual.setVisibility(View.GONE);
            timeSection.setVisibility(View.VISIBLE);
        }
    }

    private void pickTime(boolean isStart) {
        int h = prefs.getInt(isStart ? "start_hour" : "end_hour", isStart ? 22 : 7);
        int m = prefs.getInt(isStart ? "start_min"  : "end_min",  0);

        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(isStart ? "start_hour" : "end_hour", hourOfDay);
            editor.putInt(isStart ? "start_min"  : "end_min",  minute);
            editor.commit();
            updateTimeDisplay();
            ConfigWriter.write(prefs);
            updateStatus();
        }, h, m, true).show();
    }

    // ============================================================
    // 无 root 处理
    // ============================================================

    private void showNoRootAndExit() {
        View content = getLayoutInflater().inflate(R.layout.dialog_no_root, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(false)
                .create();

        dialog.setCanceledOnTouchOutside(false);
        content.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            dialog.dismiss();
            exitApp();
        });

        dialog.show();

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(R.drawable.bg_dialog);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
            w.setAttributes(lp);
        }
    }

    /** 彻底退出 */
    private void exitApp() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            }
            finishAffinity();
        } catch (Throwable ignored) {}

        Process.killProcess(Process.myPid());
        System.exit(0);
    }
}