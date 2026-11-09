package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.Vod;

import java.util.List;
import java.util.Locale;

/**
 * 搜索结果相关性排序。
 * <p>
 * 多站点搜索是流式返回的，谁先回谁排前面，会出现「名字相近的短剧/衍生片盖过正片」。
 * 这里按片名与关键词的匹配程度重排：
 * 完全同名 &gt; 以关键词开头（越短越靠前）&gt; 包含关键词（越靠前、越短越优先）。
 * 同分时短名优先，其次按名称字典序，保证顺序稳定、同名结果聚在一起。
 */
public final class SearchRanker {

    /** 空白与中英文标点：参与匹配前先剔除，避免「庆余年 第二季」匹配不上「庆余年第二季」 */
    private static final String NOISE = "[\\s　·・:：,，.。、\\-—_()（）\\[\\]【】《》\"'!！?？/\\\\|+&]";

    private SearchRanker() {
    }

    public static void sort(List<Vod> items, String keyword) {
        if (items == null || items.size() < 2) return;
        String key = normalize(keyword);
        if (key.isEmpty()) return;
        items.sort((a, b) -> {
            int sa = score(a == null ? null : a.getName(), key);
            int sb = score(b == null ? null : b.getName(), key);
            if (sa != sb) return Integer.compare(sb, sa);
            String na = normalize(a == null ? null : a.getName());
            String nb = normalize(b == null ? null : b.getName());
            if (na.length() != nb.length()) return Integer.compare(na.length(), nb.length());
            return na.compareTo(nb);
        });
    }

    private static int score(String name, String key) {
        String n = normalize(name);
        if (n.isEmpty()) return 0;
        if (n.equals(key)) return 1000;
        int extra = n.length() - key.length();
        if (n.startsWith(key)) return 800 - Math.min(150, Math.max(0, extra) * 2);
        int idx = n.indexOf(key);
        if (idx > 0) return 600 - Math.min(150, idx * 8 + Math.max(0, extra) * 2);
        return 0;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT).replaceAll(NOISE, "");
    }
}
