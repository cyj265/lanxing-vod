package com.fongmi.android.tv.player.effect.audio;

public final class AudioChannelMode {

    public static final int AUTO = 0;
    public static final int STEREO = 1;
    public static final int MONO = 2;
    public static final int REVERSE = 3;

    public static int clamp(int mode) {
        return Math.clamp(mode, AUTO, REVERSE);
    }

    public static boolean isAuto(int mode) {
        return clamp(mode) == AUTO;
    }

    public static boolean canBalance(int mode) {
        return clamp(mode) != MONO;
    }

    public static boolean isAvailable(int channelCount) {
        return channelCount >= 2 && channelCount <= 8;
    }

    public static boolean isAvailable(int mode, int channelCount) {
        if (!isAvailable(channelCount)) return false;
        return switch (clamp(mode)) {
            case STEREO -> channelCount > 2;
            case AUTO, MONO, REVERSE -> true;
            default -> false;
        };
    }

    public static int resolve(int mode, int channelCount) {
        int resolved = clamp(mode);
        return isAvailable(resolved, channelCount) ? resolved : AUTO;
    }

    public static boolean canClip(int mode, int channelCount) {
        int resolved = clamp(mode);
        if (!isAvailable(resolved, channelCount)) return false;
        return switch (resolved) {
            case MONO -> channelCount > 1;
            case STEREO, REVERSE -> channelCount > 2;
            default -> false;
        };
    }
}
