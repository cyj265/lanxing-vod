package com.fongmi.android.tv.utils;

/** 把播放器吐出的像素尺寸归成用户看得懂的档位名。 */
public final class MediaQuality {

    private MediaQuality() {
    }

    public static String label(int width, int height) {
        if (width <= 0 || height <= 0) return "";
        int shortSide = Math.min(width, height);
        int longSide = Math.max(width, height);
        // 超宽屏按长边算，避免 1920x800 被标成 720P。
        if (longSide >= shortSide * 2) {
            if (longSide >= 3840) return "4K";
            if (longSide >= 2560) return "2K";
            if (longSide >= 1920) return "1080P";
            if (longSide >= 1280) return "720P";
            if (longSide >= 854) return "480P";
            return longSide + "P";
        }
        if (shortSide >= 2160) return "4K";
        if (shortSide >= 1440) return "2K";
        if (shortSide >= 1080) return "1080P";
        if (shortSide >= 720) return "720P";
        if (shortSide >= 576) return "576P";
        if (shortSide >= 480) return "480P";
        if (shortSide >= 360) return "360P";
        if (shortSide >= 240) return "240P";
        return shortSide + "P";
    }
}
