package com.eafnv.vibrationblocker;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.topjohnwu.superuser.Shell;

import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "vib_blocker_prefs";

    static {
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(30));
    }

    private SharedPreferences prefs;

    // UI 控件
    private TextView tvStatus, tvWhitelistSummary, tvScheduleSummary, tvModuleStatus;
    private MaterialSwitch switchManual;
    private RadioGroup rgManual;
    private RadioButton rbBlockAll, rbUnblockAll;
    private View timeSection;
    private View statusDot;
    private View statusDotModule;

    // 状态
    private boolean rootReady = false;
    private boolean moduleActive = false;

    // ============================================================
    // 震动测试逻辑
    // ============================================================

    private static final long START_INTERVAL_MS = 1100L;   // 1. 起步间隔
    private static final long ACCEL_TOTAL_TIME_MS = 10000L; // 2. 加速总时长
    private static final long SPRINT_THRESHOLD_MS = 80L;   // 3. 衔接门槛
    private static final long SPRINT_TIME_MS = 3000L;      // 4. 衔接段长度
    private static final long FINAL_INTERVAL_MS = 40L;     // 5. 最终震动间隔
    private static final long VIBRATE_DURATION_MS = 15L;   // 6. 单次震动时长

    private Handler hapticHandler;
    private Vibrator mVibrator;
    private long hapticStartTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        hapticHandler = new Handler(Looper.getMainLooper());

        // 绑定控件
        tvStatus           = findViewById(R.id.tv_status);
        tvWhitelistSummary = findViewById(R.id.tv_whitelist_summary);
        tvScheduleSummary  = findViewById(R.id.tv_schedule_summary);
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

        // 界面淡入动画
        View rootView = findViewById(android.R.id.content);
        rootView.setAlpha(0f);
        rootView.animate().alpha(1f).setDuration(300).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从白名单页 / 时段页返回时刷新摘要与配置
        if (rootReady) {
            updateWhitelistSummary();
            updateScheduleSummary();
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
        updateWhitelistSummary();
        updateScheduleSummary();
        updateStatus();

        // 手动模式开关
        switchManual.setOnCheckedChangeListener((btn, isChecked) -> {
            btn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
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

        // 时段设置入口
        findViewById(R.id.row_schedule).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleActivity.class)));

        // 白名单入口
        findViewById(R.id.row_whitelist).setOnClickListener(v ->
                startActivity(new Intent(this, WhitelistActivity.class)));

        // 震动测试入口
        findViewById(R.id.row_vibration_test).setOnClickListener(v ->
                showVibrationTestDialog());

        // 首次写入配置，检测激活状态
        ConfigWriter.write(prefs);
        checkModuleActive();
    }

    // ============================================================
    // 模块激活检测
    // ============================================================

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
            blocked = false;
            List<Schedule> schedules = ScheduleStore.load(prefs);
            Calendar cal = Calendar.getInstance();
            int curDay = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            for (Schedule sc : schedules) {
                if (ScheduleJudge.hit(sc, curDay, now)) {
                    blocked = true;
                    break;
                }
            }
        }

        if (blocked) {
            tvStatus.setText("正在屏蔽振动");
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

    private void updateScheduleSummary() {
        List<Schedule> list = ScheduleStore.load(prefs);
        if (list.isEmpty()) {
            tvScheduleSummary.setText("未设置");
        } else {
            tvScheduleSummary.setText("已设置 " + list.size() + " 个时段");
        }
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

    // ============================================================
    // 震动测试
    // ============================================================

    private void showVibrationTestDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_vibration_test, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        }

        View btn = content.findViewById(R.id.btn_vibrate);
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startRepeatingHaptic();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopRepeatingHaptic();
                    return true;
            }
            return false;
        });

        dialog.setOnDismissListener(d -> stopRepeatingHaptic());
        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // ============================================================
    // 震动测试逻辑
    // ============================================================

    private void startRepeatingHaptic() {
        stopRepeatingHaptic();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                mVibrator = vm.getDefaultVibrator();
            } else {
                mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable ignored) {}

        if (mVibrator == null || !mVibrator.hasVibrator()) return;

        // 记录按下瞬间的时间戳
        hapticStartTime = System.currentTimeMillis();
        doShortVibrate();
        hapticHandler.postDelayed(hapticRunnable, START_INTERVAL_MS);
    }

    private void stopRepeatingHaptic() {
        if (hapticHandler != null) {
            hapticHandler.removeCallbacks(hapticRunnable);
        }
        if (mVibrator != null) {
            try {
                mVibrator.cancel();
            } catch (Throwable ignored) {}
        }
        hapticStartTime = 0L;
    }

    private final Runnable hapticRunnable = new Runnable() {
        @Override
        public void run() {
            // 计算从按下到现在流逝了多少时间
            long elapsed = System.currentTimeMillis() - hapticStartTime;

            if (elapsed < ACCEL_TOTAL_TIME_MS) {
                // 使用指数曲线计算进度
                double progress = (double) elapsed / ACCEL_TOTAL_TIME_MS;
                long currentInterval = (long) (START_INTERVAL_MS * Math.pow((double) SPRINT_THRESHOLD_MS / START_INTERVAL_MS, progress));

                doShortVibrate();
                hapticHandler.postDelayed(this, currentInterval);

            } else if (elapsed < ACCEL_TOTAL_TIME_MS + SPRINT_TIME_MS) {
                // 从 门槛 压到 最终的极限间隔
                double progress = (double) (elapsed - ACCEL_TOTAL_TIME_MS) / SPRINT_TIME_MS;
                long currentInterval = (long) (SPRINT_THRESHOLD_MS * Math.pow((double) FINAL_INTERVAL_MS / SPRINT_THRESHOLD_MS, progress));

                doShortVibrate();
                hapticHandler.postDelayed(this, currentInterval);

            } else {
                // 长震
                startSustainVibrate();
            }
        }
    };

    private void doShortVibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mVibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                mVibrator.vibrate(VIBRATE_DURATION_MS);
            }
        } catch (Throwable ignored) {}
    }

    private void startSustainVibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mVibrator.vibrate(VibrationEffect.createOneShot(60000, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                mVibrator.vibrate(60000);
            }
        } catch (Throwable ignored) {}
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

    // 彻底退出
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