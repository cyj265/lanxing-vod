package com.fongmi.android.tv.playback.vod;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Url;
import com.fongmi.android.tv.bean.Value;
import com.github.catvod.utils.Prefers;

import java.util.List;

/**
 * 按「站点 + 线路」记住用户上次选的画质名称。
 * <p>
 * 记住名称而不是下标：同一部剧不同集、不同影片的画质列表顺序可能不同，
 * 只有名称（原画、720P…）才是稳定的；源没给名称时才退回下标。
 */
public final class QualityMemory {

    private static final String PREFIX = "playback.quality.";

    private QualityMemory() {
    }

    public static int resolve(String site, String flag, Url url, int fallback) {
        List<Value> values = url.getValues();
        if (values.isEmpty()) return 0;
        String saved = Prefers.getString(key(site, flag));
        if (!saved.isEmpty()) {
            if (saved.charAt(0) == '#') {
                try {
                    int index = Integer.parseInt(saved.substring(1));
                    if (index >= 0 && index < values.size()) return index;
                } catch (NumberFormatException ignored) {
                }
            }
            for (int i = 0; i < values.size(); i++) {
                if (TextUtils.equals(saved, values.get(i).getN())) return i;
            }
        }
        return Math.max(0, Math.min(fallback, values.size() - 1));
    }

    public static void save(String site, String flag, String name, int position) {
        if (TextUtils.isEmpty(site)) return;
        Prefers.put(key(site, flag), TextUtils.isEmpty(name) ? "#" + position : name);
    }

    private static String key(String site, String flag) {
        return PREFIX + (site == null ? "" : site) + "|" + (flag == null ? "" : flag);
    }
}
