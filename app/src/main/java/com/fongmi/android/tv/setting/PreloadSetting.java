package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class PreloadSetting {

    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 10;
    public static final int DEFAULT_THREADS = 1;
    public static final int MIN_SIZE_MB = 128;
    public static final int MAX_SIZE_MB = 4096;
    public static final int STEP_SIZE_MB = 128;
    public static final int MIN_TIME_SECONDS = 20;
    public static final int MAX_TIME_SECONDS = 120;
    public static final int DEFAULT_TIME_SECONDS = 20;
    public static final int STEP_TIME_SECONDS = 10;
    public static final int WHOLE_MEDIA_AHEAD_SECONDS = 0;
    public static final int DEFAULT_AHEAD_SECONDS = 300;
    public static final int PAUSE_PRELOAD_LEGACY_OFF = 0;
    public static final int PAUSE_PRELOAD_WIFI = 1;
    public static final int PAUSE_PRELOAD_ALWAYS = 2;
    public static final int DEFAULT_PAUSE_PRELOAD = PAUSE_PRELOAD_ALWAYS;

    public static boolean isEnabled() {
        return Prefers.getBoolean("preload");
    }

    public static void putEnabled(boolean preload) {
        Prefers.put("preload", preload);
    }

    public static boolean isNextEpisodeEnabled() {
        return Prefers.getBoolean("preload_next_episode");
    }

    public static void putNextEpisodeEnabled(boolean enabled) {
        Prefers.put("preload_next_episode", enabled);
    }

    public static int getThreads() {
        return Math.clamp(Prefers.getInt("preload_threads", MIN_THREADS), MIN_THREADS, MAX_THREADS);
    }

    public static void putThreads(int threads) {
        Prefers.put("preload_threads", Math.clamp(threads, MIN_THREADS, MAX_THREADS));
    }

    public static int getSizeMb() {
        int size = Math.clamp(Prefers.getInt("preload_size", MIN_SIZE_MB), MIN_SIZE_MB, MAX_SIZE_MB);
        return Math.clamp(MIN_SIZE_MB + (long) Math.round((float) (size - MIN_SIZE_MB) / STEP_SIZE_MB) * STEP_SIZE_MB, MIN_SIZE_MB, MAX_SIZE_MB);
    }

    public static void putSizeMb(int size) {
        Prefers.put("preload_size", Math.clamp(size, MIN_SIZE_MB, MAX_SIZE_MB));
    }

    public static long getSizeBytes() {
        return getSizeMb() * 1024L * 1024L;
    }

    public static int getTimeSeconds() {
        int seconds = Math.clamp(Prefers.getInt("preload_time", MAX_TIME_SECONDS), MIN_TIME_SECONDS, MAX_TIME_SECONDS);
        return Math.clamp(MIN_TIME_SECONDS + (long) Math.round((float) (seconds - MIN_TIME_SECONDS) / STEP_TIME_SECONDS) * STEP_TIME_SECONDS, MIN_TIME_SECONDS, MAX_TIME_SECONDS);
    }

    public static void putTimeSeconds(int seconds) {
        Prefers.put("preload_time", Math.clamp(seconds, MIN_TIME_SECONDS, MAX_TIME_SECONDS));
    }

    public static long getDurationMs() {
        return getTimeSeconds() * 1000L;
    }
    // ---- FongMi MPV compatibility API ----
    public static boolean isPreload() {
        return isEnabled();
    }

    public static boolean isPreload(int kernel) {
        return isEnabled();
    }

    public static int getPreloadThreads() {
        return getThreads();
    }

    public static int getPreloadThreads(int kernel) {
        return getThreads();
    }

    public static int getPreloadSizeMb() {
        return getSizeMb();
    }

    public static int getPreloadSizeMb(int kernel) {
        return getSizeMb();
    }

    public static long getPreloadSizeBytes() {
        return getSizeBytes();
    }

    public static long getPreloadSizeBytes(int kernel) {
        return getSizeBytes();
    }

    public static int getPreloadTimeSeconds() {
        return getTimeSeconds();
    }

    public static int getPreloadTimeSeconds(int kernel) {
        return getTimeSeconds();
    }

    public static int getPreloadAheadSeconds() {
        return WHOLE_MEDIA_AHEAD_SECONDS;
    }

    public static int getPreloadAheadSeconds(int kernel) {
        return WHOLE_MEDIA_AHEAD_SECONDS;
    }

    public static int getPausePreloadPolicy() {
        return PAUSE_PRELOAD_ALWAYS;
    }

    public static int getPausePreloadPolicy(int kernel) {
        return PAUSE_PRELOAD_ALWAYS;
    }

    static int normalizePausePreloadPolicy(int policy) {
        return policy == PAUSE_PRELOAD_ALWAYS ? PAUSE_PRELOAD_ALWAYS : PAUSE_PRELOAD_WIFI;
    }

}
