package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class LiveSetting {

    public static boolean isBoot() {
        return Prefers.getBoolean("boot_live");
    }

    public static void putBoot(boolean boot) {
        Prefers.put("boot_live", boot);
    }

    public static boolean isAcross() {
        return Prefers.getBoolean("across", true);
    }

    public static void putAcross(boolean across) {
        Prefers.put("across", across);
    }

    public static boolean isChange() {
        return Prefers.getBoolean("change", true);
    }

    public static void putChange(boolean change) {
        Prefers.put("change", change);
    }

    public static boolean isInvert() {
        return Prefers.getBoolean("invert");
    }

    public static void putInvert(boolean invert) {
        Prefers.put("invert", invert);
    }

    public static int getScale() {
        return Math.clamp(Prefers.getInt("scale_live", PlayerSetting.getScale()), PlayerSetting.MIN_SCALE, PlayerSetting.MAX_SCALE);
    }

    public static void putScale(int scale) {
        Prefers.put("scale_live", Math.clamp(scale, PlayerSetting.MIN_SCALE, PlayerSetting.MAX_SCALE));
    }

    /** 超时换源秒数，0 = 跟随直播源配置 */
    public static int getTimeout() {
        return Math.max(Prefers.getInt("live_timeout", 0), 0);
    }

    public static void putTimeout(int timeout) {
        Prefers.put("live_timeout", Math.max(timeout, 0));
    }

    /** 自定义 EPG 地址，空 = 使用直播源自带 */
    public static String getEpg() {
        return Prefers.getString("live_epg").trim();
    }

    public static void putEpg(String epg) {
        Prefers.put("live_epg", epg == null ? "" : epg.trim());
    }
}
