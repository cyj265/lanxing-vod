package com.fongmi.android.tv.setting;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class SearchBlockManager {

    private static final String PREF_NAME = "ok_multi_config_search";
    private static final String KEY_BLOCKED = "blocked_config_keys";

    private SearchBlockManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static Set<String> getBlocked(Context context) {
        Set<String> saved = prefs(context).getStringSet(KEY_BLOCKED, null);
        return saved == null ? new HashSet<>() : new HashSet<>(saved);
    }

    public static boolean toggle(Context context, String key) {
        if (key == null || key.isEmpty() || "all".equals(key)) return false;

        Set<String> blocked = getBlocked(context);
        boolean nowBlocked;

        if (blocked.contains(key)) {
            blocked.remove(key);
            nowBlocked = false;
        } else {
            blocked.add(key);
            nowBlocked = true;
        }

        prefs(context)
                .edit()
                .putStringSet(KEY_BLOCKED, new HashSet<>(blocked))
                .apply();

        return nowBlocked;
    }
}
