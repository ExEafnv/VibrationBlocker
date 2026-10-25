package com.eafnv.vibrationblocker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

public class ConfigReader {

    private static final String TAG = "VibBlocker";
    private static final String SYS_CONFIG_PATH = "/data/system/vib_blocker_config";

    private static volatile int[] sCfg = {0, 1};
    private static volatile Set<String> sWhitelist = Collections.emptySet();
    private static volatile List<Schedule> sSchedules = new ArrayList<>();

    public static boolean shouldBlock(String opPkg) {
        int[] cfg = read();
        if (opPkg != null && sWhitelist.contains(opPkg)) return false;

        boolean manual   = cfg[0] == 1;
        boolean blockAll = cfg[1] == 1;
        if (manual) return blockAll;

        Calendar cal = Calendar.getInstance();
        int curDay = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        for (Schedule sc : sSchedules) {
            if (ScheduleJudge.hit(sc, curDay, now)) return true;
        }
        return false;
    }

    private static int[] read() {
        boolean manual = false, blockAll = true;
        String whitelistStr = "";
        List<Schedule> newSchedules = new ArrayList<>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(SYS_CONFIG_PATH));
            String line;
            while ((line = br.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                switch (k) {
                    case "manual_mode":      manual   = "1".equals(v); break;
                    case "manual_block_all": blockAll = "1".equals(v); break;
                    case "whitelist":        whitelistStr = v; break;
                    case "schedule":
                        Schedule sc = Schedule.parse(v);
                        if (sc != null) newSchedules.add(sc);
                        break;
                }
            }
            br.close();
        } catch (Throwable ignored) {}

        Set<String> newWhitelist;
        if (whitelistStr.isEmpty()) {
            newWhitelist = Collections.emptySet();
        } else {
            Set<String> tmp = new HashSet<>();
            for (String s : whitelistStr.split(",")) {
                if (!s.trim().isEmpty()) tmp.add(s.trim());
            }
            newWhitelist = tmp;
        }

        int[] result = {manual ? 1 : 0, blockAll ? 1 : 0};
        int[] prev = sCfg;
        boolean changed = prev[0] != result[0] || prev[1] != result[1]
                || !sWhitelist.equals(newWhitelist)
                || !sSchedules.equals(newSchedules);
        if (changed) {
            XposedBridge.log(TAG + ": cfg manual=" + result[0] + " blockAll=" + result[1]
                    + " whitelist=" + newWhitelist.size()
                    + " schedules=" + newSchedules.size());
            sCfg = result;
            sWhitelist = newWhitelist;
            sSchedules = newSchedules;
        }
        return result;
    }
}