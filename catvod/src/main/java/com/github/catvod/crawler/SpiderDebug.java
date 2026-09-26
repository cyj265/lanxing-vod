package com.github.catvod.crawler;

import android.text.TextUtils;

import com.orhanobut.logger.Logger;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();

    // New MPV diagnostics use this gate. The 5.6.3 catvod base did not expose it.
    // Keep it off by default so release playback is not burdened by debug-only watchdogs.
    public static boolean isEnabled() {
        return false;
    }

    public static void log(Throwable th) {
        if (th != null) th.printStackTrace();
    }

    public static void log(String msg) {
        if (!TextUtils.isEmpty(msg)) Logger.t(TAG).d(msg);
    }

    public static void log(String tag, String msg, Object... args) {
        if (!TextUtils.isEmpty(msg)) Logger.t(tag).d(msg, args);
    }
}
