package com.eafnv.vibrationblocker;

public class ScheduleJudge {

// @param sc      时段
// @param curDay  当前星期（0=周一 ... 6=周日）
// @param nowMin  当前时间（0..1439）

    public static boolean hit(Schedule sc, int curDay, int nowMin) {
        if (!sc.enabled) return false;             // 未启用直接返回 false
        if (sc.startMin == sc.endMin) return false;

        boolean todayOn = (sc.daysMask & (1 << curDay)) != 0;
        int prevDay = (curDay + 6) % 7;
        boolean prevOn = (sc.daysMask & (1 << prevDay)) != 0;

        if (!sc.isCrossDay()) {
            return todayOn && nowMin >= sc.startMin && nowMin < sc.endMin;
        } else {
            if (todayOn && nowMin >= sc.startMin) return true;
            if (prevOn && nowMin < sc.endMin) return true;
            return false;
        }
    }
}