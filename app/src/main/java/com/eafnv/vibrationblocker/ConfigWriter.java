package com.eafnv.vibrationblocker;

import android.content.SharedPreferences;
import android.util.Base64;

import com.topjohnwu.superuser.Shell;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ConfigWriter {

    private static final String SYS_CONFIG_PATH = "/data/system/vib_blocker_config";

    public static void write(SharedPreferences prefs) {
        boolean manual   = prefs.getBoolean("manual_mode", false);
        boolean blockAll = prefs.getBoolean("manual_block_all", true);
        String whitelist = prefs.getString("whitelist", "");
        List<Schedule> schedules = ScheduleStore.load(prefs);

        StringBuilder sb = new StringBuilder();
        sb.append("manual_mode=").append(manual ? 1 : 0).append("\n");
        sb.append("manual_block_all=").append(blockAll ? 1 : 0).append("\n");
        sb.append("whitelist=").append(whitelist).append("\n");
        for (Schedule sc : schedules) {
            sb.append("schedule=").append(sc.serialize()).append("\n");
        }

        String b64 = Base64.encodeToString(
                sb.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

        Shell.cmd("echo " + b64 + " | base64 -d > " + SYS_CONFIG_PATH
                + " && chmod 644 " + SYS_CONFIG_PATH).submit();
    }
}