package com.eafnv.vibrationblocker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

public class ConfigReader {

    private static final String TAG = "VibBlocker";
    private static final String SYS_CONFIG_PATH = "/data/system/vib_blocker_config";

    private static volatile int[] sCfg = {22 * 60, 7 * 60, 0, 1};
    private static volatile Set<String> sWhitelist = Collections.emptySet();

    public static boolean shouldBlock(String opPkg) {
        int[] cfg = read();   // 先读，保证 sWhitelist 是最新的

        if (opPkg != null && sWhitelist.contains(opPkg)) {
            return false;
        }

        boolean manual   = cfg[2] == 1;
        boolean blockAll = cfg[3] == 1;
        if (manual) return blockAll;

        int startMin = cfg[0];
        int endMin   = cfg[1];
        Calendar cal = Calendar.getInstance();
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        if (startMin == endMin) return false;
        if (startMin < endMin)  return now >= startMin && now < endMin;
        return now >= startMin || now < endMin;
    }

    private static int[] read() {
        int sh = 22, sm = 0, eh = 7, em = 0;
        boolean manual = false, blockAll = true;
        String whitelistStr = "";

        try {
            BufferedReader br = new BufferedReader(new FileReader(SYS_CONFIG_PATH));
            String line;
            while ((line = br.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                switch (k) {
                    case "start_hour":       sh = Integer.parseInt(v); break;
                    case "start_min":        sm = Integer.parseInt(v); break;
                    case "end_hour":         eh = Integer.parseInt(v); break;
                    case "end_min":          em = Integer.parseInt(v); break;
                    case "manual_mode":      manual   = "1".equals(v); break;
                    case "manual_block_all": blockAll = "1".equals(v); break;
                    case "whitelist":        whitelistStr = v; break;
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

        int[] result = {sh * 60 + sm, eh * 60 + em, manual ? 1 : 0, blockAll ? 1 : 0};

        int[] prev = sCfg;
        boolean changed = prev[0] != result[0] || prev[1] != result[1]
                || prev[2] != result[2] || prev[3] != result[3]
                || !sWhitelist.equals(newWhitelist);
        if (changed) {
            XposedBridge.log(TAG + ": cfg manual=" + result[2] + " blockAll=" + result[3]
                    + " " + fmt(result[0]) + "-" + fmt(result[1])
                    + " whitelist=" + newWhitelist);
            sCfg = result;
            sWhitelist = newWhitelist;
        }

        return result;
    }

    private static String fmt(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}