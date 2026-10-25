package com.eafnv.vibrationblocker;

// 单个定时时段
public class Schedule {
    public int startMin;    // 0..1439
    public int endMin;      // 0..1439
    public int daysMask;    // bit0=周一 ... bit6=周日
    public boolean enabled; // 是否启用

    public Schedule(int startMin, int endMin, int daysMask) {
        this(startMin, endMin, daysMask, true);
    }

    public Schedule(int startMin, int endMin, int daysMask, boolean enabled) {
        this.startMin = startMin;
        this.endMin = endMin;
        this.daysMask = daysMask;
        this.enabled = enabled;
    }

    public boolean isCrossDay() {
        return startMin > endMin;
    }

    // 序列化为 "start,end,mask,enabled"
    public String serialize() {
        return startMin + "," + endMin + "," + daysMask + "," + (enabled ? 1 : 0);
    }

    // 反序列化，兼容旧版没有 enabled 的格式
    public static Schedule parse(String s) {
        try {
            String[] p = s.split(",");
            if (p.length == 3) {
                return new Schedule(
                        Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()),
                        true);
            } else if (p.length >= 4) {
                return new Schedule(
                        Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()),
                        "1".equals(p[3].trim()));
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    // 星期摘要
    public String getDaysSummary() {
        if (daysMask == 0x7F) return "每天";
        if (daysMask == 0x1F) return "周一至周五";
        if (daysMask == 0x60) return "周末";
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((daysMask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("周").append(names[i]);
            }
        }
        return sb.length() == 0 ? "未选择" : sb.toString();
    }

    /** 时间摘要 */
    public String getTimeSummary() {
        String s = fmt(startMin) + " - " + fmt(endMin);
        if (isCrossDay()) s += " (次日)";
        return s;
    }

    private static String fmt(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schedule schedule = (Schedule) o;
        return startMin == schedule.startMin &&
                endMin == schedule.endMin &&
                daysMask == schedule.daysMask &&
                enabled == schedule.enabled;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(startMin, endMin, daysMask, enabled);
    }
}