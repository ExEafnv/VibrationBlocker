package com.eafnv.vibrationblocker;

import android.content.SharedPreferences;
import android.util.Base64;

import com.topjohnwu.superuser.Shell;

import java.nio.charset.StandardCharsets;

public class ConfigWriter {

    private static final String SYS_CONFIG_PATH = "/data/system/vib_blocker_config";

    public static void write(SharedPreferences prefs) {
        int sh = prefs.getInt("start_hour", 22);
        int sm = prefs.getInt("start_min", 0);
        int eh = prefs.getInt("end_hour", 7);
        int em = prefs.getInt("end_min", 0);
        boolean manual   = prefs.getBoolean("manual_mode", false);
        boolean blockAll = prefs.getBoolean("manual_block_all", true);
        String whitelist = prefs.getString("whitelist", "");

        StringBuilder sb = new StringBuilder();
        sb.append("start_hour=").append(sh).append("\n");
        sb.append("start_min=").append(sm).append("\n");
        sb.append("end_hour=").append(eh).append("\n");
        sb.append("end_min=").append(em).append("\n");
        sb.append("manual_mode=").append(manual ? 1 : 0).append("\n");
        sb.append("manual_block_all=").append(blockAll ? 1 : 0).append("\n");
        sb.append("whitelist=").append(whitelist).append("\n");

        String b64 = Base64.encodeToString(
                sb.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

        Shell.cmd("echo " + b64 + " | base64 -d > " + SYS_CONFIG_PATH
                + " && chmod 644 " + SYS_CONFIG_PATH).submit();
    }
}