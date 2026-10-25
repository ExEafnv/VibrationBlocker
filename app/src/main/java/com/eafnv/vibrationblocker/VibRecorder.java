package com.eafnv.vibrationblocker;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class VibRecorder {
    private static final int CAPACITY = 200;
    private static final long REPEAT_WINDOW_MS = 10_000L; // 10秒内算重复
    private static final String LOG_FILE_PATH = "/data/system/vib_blocker_logs";

    private static final Object lock = new Object();
    private static final LinkedList<VibRecord> queue = new LinkedList<>();
    private static volatile boolean isFlushScheduled = false;

    public static class VibRecord {
        public String pkg;
        public int usage;
        public long startTime;
        public int repeatCount;

        VibRecord(String pkg, int usage, long time) {
            this.pkg = pkg;
            this.usage = usage;
            this.startTime = time;
            this.repeatCount = 1;
        }
    }

    public static void onVibration(String pkg, int usage) {
        if (pkg == null || pkg.isEmpty()) return;
        long now = System.currentTimeMillis();

        synchronized (lock) {
            VibRecord last = queue.peekLast();
            if (last != null && last.pkg.equals(pkg) && last.usage == usage
                    && (now - last.startTime) <= REPEAT_WINDOW_MS) {
                // 合并到上一条
                last.repeatCount++;
                last.startTime = now; // 或者保留首次时间，看 UI 语义
            } else {
                if (queue.size() >= CAPACITY) {
                    queue.removeFirst();
                }
                queue.addLast(new VibRecord(pkg, usage, now));
            }
        }

        // 限制写盘频率，最多每 2 秒写一次
        if (!isFlushScheduled) {
            isFlushScheduled = true;
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    flushToFile();
                } catch (Exception ignored) {
                } finally {
                    isFlushScheduled = false;
                }
            }).start();
        }
    }

    private static void flushToFile() {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        synchronized (lock) {
            // 倒序输出，最新的在最上面
            for (int i = queue.size() - 1; i >= 0; i--) {
                VibRecord r = queue.get(i);
                String timeStr = sdf.format(new Date(r.startTime));
                // 格式: 包名|usage|时间|重复次数
                sb.append(r.pkg).append("|")
                        .append(r.usage).append("|")
                        .append(timeStr).append("|")
                        .append(r.repeatCount).append("\n");
            }
        }

        try {
            File f = new File(LOG_FILE_PATH);
            FileWriter fw = new FileWriter(f);
            fw.write(sb.toString());
            fw.close();
            // 确保 Root/Shell 可读
            Runtime.getRuntime().exec("chmod 644 " + LOG_FILE_PATH);
        } catch (Throwable ignored) {}
    }
}