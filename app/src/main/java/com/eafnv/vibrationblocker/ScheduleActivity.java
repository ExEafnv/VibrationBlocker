package com.eafnv.vibrationblocker;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

public class ScheduleActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "vib_blocker_prefs";

    private SharedPreferences prefs;
    private RecyclerView rv;
    private TextView tvEmpty;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rv = findViewById(R.id.rv_schedules);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new Adapter(ScheduleStore.load(prefs));
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        refreshEmptyView();
        findViewById(R.id.fab_add).setOnClickListener(v -> showEditDialog(-1));

        // 界面淡入动画
        View rootView = findViewById(android.R.id.content);
        rootView.setAlpha(0f);
        rootView.animate().alpha(1f).setDuration(300).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ScheduleStore.save(prefs, adapter.list);
        ConfigWriter.write(prefs);
    }

    private void refreshEmptyView() {
        boolean isEmpty = adapter == null || adapter.list.isEmpty();
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // ============================================================
    // 编辑对话框
    // ============================================================

    private void showEditDialog(int position) {
        Schedule editing = position >= 0 ? adapter.list.get(position) : null;

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_edit_schedule, null);

        TextView tvTitle = content.findViewById(R.id.tv_dialog_title);
        TextView tvStart = content.findViewById(R.id.tv_start);
        TextView tvEnd = content.findViewById(R.id.tv_end);
        TextView tvHint = content.findViewById(R.id.tv_crossday_hint);
        LinearLayout llDays = content.findViewById(R.id.ll_days);

        MaterialButton btnWeekend = content.findViewById(R.id.btn_weekend);
        MaterialButton btnEveryday = content.findViewById(R.id.btn_everyday);
        MaterialButton btnInvert = content.findViewById(R.id.btn_invert);

        MaterialButton btnCancel = content.findViewById(R.id.btn_cancel);
        MaterialButton btnSave = content.findViewById(R.id.btn_save);

        tvTitle.setText(editing == null ? "添加时段" : "编辑时段");

        final int[] start = { editing != null ? editing.startMin : 22 * 60 };
        final int[] end   = { editing != null ? editing.endMin   : 7 * 60 };
        final int[] mask  = { editing != null ? editing.daysMask : 0x7F };

        TextView[] dayViews = new TextView[7];
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) {
            TextView tv = new TextView(this);
            tv.setText(names[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (36 * getResources().getDisplayMetrics().density),
                    (int) (36 * getResources().getDisplayMetrics().density));
            lp.weight = 1f;
            lp.setMarginStart((int) (2 * getResources().getDisplayMetrics().density));
            lp.setMarginEnd((int) (2 * getResources().getDisplayMetrics().density));
            tv.setLayoutParams(lp);

            final int bit = i;
            tv.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                mask[0] ^= (1 << bit);
                updateDaysView(dayViews, mask[0]);
            });

            llDays.addView(tv);
            dayViews[i] = tv;
        }

        Runnable refresh = () -> {
            tvStart.setText(fmt(start[0]));
            tvEnd.setText(fmt(end[0]));
            tvHint.setVisibility(start[0] > end[0] ? View.VISIBLE : View.GONE);
            updateDaysView(dayViews, mask[0]);
        };
        refresh.run();

        content.findViewById(R.id.ll_start_time).setOnClickListener(v -> new TimePickerDialog(this,
                (view, h, m) -> { start[0] = h * 60 + m; refresh.run(); },
                start[0] / 60, start[0] % 60, true).show());

        content.findViewById(R.id.ll_end_time).setOnClickListener(v -> new TimePickerDialog(this,
                (view, h, m) -> { end[0] = h * 60 + m; refresh.run(); },
                end[0] / 60, end[0] % 60, true).show());

        btnWeekend.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            mask[0] = 0x60; // 周六、周日
            refresh.run();
        });
        btnEveryday.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            mask[0] = 0x7F; // 每天
            refresh.run();
        });
        btnInvert.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            mask[0] = (~mask[0]) & 0x7F; // 按位取反，并保证只有低 7 位有效
            refresh.run();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            if (mask[0] == 0) {
                Toast.makeText(this, "请至少选择一天", Toast.LENGTH_SHORT).show();
                return;
            }
            if (start[0] == end[0]) {
                Toast.makeText(this, "开始时间和结束时间不能相同", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean isEnabled = editing != null ? editing.enabled : true;
            Schedule sc = new Schedule(start[0], end[0], mask[0], isEnabled);

            if (editing == null) {
                adapter.list.add(sc);
            } else {
                adapter.list.set(position, sc);
            }
            adapter.notifyDataSetChanged();
            refreshEmptyView();
            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // 弹出删除确认框
    private void showDeleteConfirmDialog(int position) {
        if (position < 0 || position >= adapter.list.size()) return;

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        }

        content.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        content.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            adapter.list.remove(position);
            adapter.notifyItemRemoved(position);
            // 修复索引错位
            adapter.notifyItemRangeChanged(position, adapter.list.size() - position);

            refreshEmptyView();
            ScheduleStore.save(prefs, adapter.list);
            ConfigWriter.write(prefs);

            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void updateDaysView(TextView[] views, int mask) {
        int colorPrimary = getThemeColor(android.R.attr.colorPrimary);
        int colorOnPrimary = Color.WHITE;
        int colorOutline = getThemeColor(android.R.attr.colorControlNormal);
        int colorOnSurface = getThemeColor(android.R.attr.textColorPrimary);

        for (int i = 0; i < 7; i++) {
            boolean selected = (mask & (1 << i)) != 0;
            TextView tv = views[i];
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);

            if (selected) {
                bg.setColor(colorPrimary);
                tv.setTextColor(colorOnPrimary);
            } else {
                bg.setColor(Color.TRANSPARENT);
                bg.setStroke((int) (1.5 * getResources().getDisplayMetrics().density), colorOutline);
                tv.setTextColor(colorOnSurface);
            }
            tv.setBackground(bg);
        }
    }

    private int getThemeColor(int attrId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrId, typedValue, true);
        if (typedValue.resourceId != 0) {
            return androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId);
        }
        return typedValue.data;
    }

    private static String fmt(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    // ============================================================
    // 列表适配器
    // ============================================================

    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        final List<Schedule> list;

        Adapter(List<Schedule> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_schedule, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Schedule sc = list.get(position);
            h.tvTime.setText(sc.getTimeSummary());
            h.tvDays.setText(sc.getDaysSummary());

            // 避免复用导致 Switch 触发多余的监听
            h.switchEnable.setOnCheckedChangeListener(null);
            h.switchEnable.setChecked(sc.enabled);

            // 控制置灰效果
            h.row.setAlpha(sc.enabled ? 1f : 0.5f);

            h.switchEnable.setOnCheckedChangeListener((btn, isChecked) -> {
                btn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                list.get(pos).enabled = isChecked;
                h.row.setAlpha(isChecked ? 1f : 0.5f);
                ScheduleStore.save(prefs, list);
                ConfigWriter.write(prefs);
            });

            h.row.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                showEditDialog(pos);
            });

            // 长按弹出删除确认
            h.row.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;
                showDeleteConfirmDialog(pos);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends RecyclerView.ViewHolder {
            View row;
            TextView tvTime, tvDays;
            MaterialSwitch switchEnable;

            VH(@NonNull View v) {
                super(v);
                row = v.findViewById(R.id.row);
                tvTime = v.findViewById(R.id.tv_time);
                tvDays = v.findViewById(R.id.tv_days);
                switchEnable = v.findViewById(R.id.switch_enable);
                // btnDelete 已经移除
            }
        }
    }
}