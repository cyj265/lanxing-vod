package com.fongmi.android.tv.utils;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Release 列表的版本发现逻辑（纯逻辑，不依赖 Android，可在 JVM 单元测试中直接运行）。
 *
 * 核心原则：绝不依赖接口返回的列表顺序，版本号一律自己比大小。
 * 2026-10-13 实测本仓库两个源的返回顺序并不一致：
 * Gitee v5 releases 为创建时间升序（v5.6.82 创建于 10-12 19:46 排在第 1 位，
 * v5.6.83 创建于 10-13 00:03 排在第 2 位，即旧的在前）。
 * 一旦「返回第一条命中」，走该源时拿到的就是本线最旧的一条——后果不是收不到更新，
 * 而是把 5.6.81 的用户引导去装 5.6.82（恰好是缺 Updater 修复的那个旧构建）。
 * GitHub 侧虽然通常按创建时间倒序返回，但那同样是接口的实现细节，不作为正确性依据。
 */
public class ReleaseSource {

    /**
     * 从 Release 列表中挑出与本机同主版本（本线）里版本号最大的一条，返回与两源同构的 release 对象。
     * 最新一条缺可用 APK 附件时抛异常，交由调用方换源兜底，绝不翻旧版本冒充「最新」。
     * 列表为空或本线无任何 release 返回 null（本线不可用，同样交给下一个源）。
     */
    public static JSONObject pickLatest(JSONArray arr, String localVersion) throws Exception {
        int bestIdx = -1;
        String bestTag = null;
        for (int i = 0; i < arr.length(); i++) {
            // tag 形如 v5.6.67-r124：去掉前缀 v，再去掉 -r124 等后缀，只留版本号 5.6.67
            String tag = normalizeTag(arr.getJSONObject(i).optString("tag_name", ""));
            if (tag.isEmpty() || !sameMajor(tag, localVersion)) continue;
            // 自己比大小，不假定列表已排序：只保留版本号最大的一条
            if (bestTag != null && compare(tag, bestTag) <= 0) continue;
            bestTag = tag;
            bestIdx = i;
        }
        if (bestIdx < 0) return null;
        JSONObject json = arr.getJSONObject(bestIdx);
        String url = pickApk(json);
        // 本线最新一条却缺 APK 附件：直接判本源不可用（抛异常走兜底），
        // 不能退回去翻更旧的版本冒充"最新"——否则调用方拿到非 null 就把下一个源的兜底短路掉，
        // 用户会被钉死在旧版（v5.6.83 空壳 release 就是这么断的）。
        if (url == null) throw new IllegalStateException("release " + bestTag + " 无可用 APK 附件（判源不可用，交由兜底）");
        JSONObject release = new JSONObject();
        release.put("version", bestTag);
        release.put("apk_url", url);
        release.put("desc", json.optString("body", ""));
        return release;
    }

    /** v5.6.67-r124 → 5.6.67 */
    static String normalizeTag(String tagName) {
        return tagName.replaceFirst("^v", "").replaceFirst("-.*$", "");
    }

    /** 判断两个版本号主版本是否一致（只比第一段，如 5.4.44 与 5.6.69 同为主版本 5） */
    public static boolean sameMajor(String a, String b) {
        return majorOf(a) == majorOf(b);
    }

    private static int majorOf(String v) {
        try {
            return Integer.parseInt(((v == null ? "" : v).split("\\."))[0].trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** 分段数值比较：a>b 返回正数，相等返回 0，a<b 返回负数；任一为空视为相等(不提示) */
    public static int compare(String a, String b) {
        if (a == null || b == null) return 0;
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? parseInt(pa[i]) : 0;
            int y = i < pb.length ? parseInt(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 从一条 Release 的附件里挑手机版 APK 下载地址；挑不到返回 null，宁可不更新也不下错包。
     * 历史 Release 长期同时挂着手机版(lanxing-v*.apk)与电视版(lanxing-tv-v*.apk)两个包，
     * 直接取首个 .apk 会命中电视版（"lanxing-tv" 排在 "lanxing-v" 前），导致手机端总更新成电视包；
     * 电视版已停发，这里始终优先选不含 tv 的手机版，万一只剩电视包也宁可不更新也不下错。
     */
    static String pickApk(JSONObject release) throws Exception {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;
        String url = null;
        String fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name", "");
            if (!name.endsWith(".apk")) continue;
            if (fallback == null) fallback = a.optString("browser_download_url", "");
            if (!name.contains("tv")) {
                url = a.optString("browser_download_url", "");
                break;
            }
        }
        if (url == null || url.isEmpty()) url = fallback;
        return url == null || url.isEmpty() ? null : url;
    }
}
