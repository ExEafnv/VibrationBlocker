package com.eafnv.vibrationblocker;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class ScheduleStore {

    private static final String KEY = "schedules";

    // 从 SharedPreferences 读出时段列表
    public static List<Schedule> load(SharedPreferences prefs) {
        String s = prefs.getString(KEY, "");
        List<Schedule> list = new ArrayList<>();
        if (s.isEmpty()) return list;
        // 用 ; 分隔不同时段，避免和时段内部的 , 冲突
        for (String one : s.split(";")) {
            one = one.trim();
            if (one.isEmpty()) continue;
            Schedule sc = Schedule.parse(one);
            if (sc != null) list.add(sc);
        }
        return list;
    }

    // 保存时段列表到 SharedPreferences
    public static void save(SharedPreferences prefs, List<Schedule> list) {
        StringBuilder sb = new StringBuilder();
        for (Schedule sc : list) {
            if (sb.length() > 0) sb.append(";");
            sb.append(sc.serialize());
        }
        prefs.edit().putString(KEY, sb.toString()).apply();
    }

    // 从配置文件内容里解析出所有 schedule 行
    public static List<Schedule> parseFromFile(String content) {
        List<Schedule> list = new ArrayList<>();
        if (content == null) return list;
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("schedule=")) {
                Schedule sc = Schedule.parse(line.substring("schedule=".length()).trim());
                if (sc != null) list.add(sc);
            }
        }
        return list;
    }
}