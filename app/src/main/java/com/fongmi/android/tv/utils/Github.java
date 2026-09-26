package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

public class Github {

    public static final String RELEASES = "https://api.github.com/repos/cyj265/lanxing-vod/releases?per_page=100";
    public static final String MIRROR = "https://gh-proxy.com/";

    /**
     * 返回与当前版本同系列（如 v5.6.x）的最新 release，包含预发布；无匹配返回 null。
     * 这样 5.6.x 测试版只认本系列更新，不会与 5.4.x 正式版互相干扰。
     */
    public static JSONObject getLatestRelease() throws Exception {
        String body;
        try {
            body = OkHttp.string(RELEASES);
        } catch (Exception e) {
            body = OkHttp.string(MIRROR + RELEASES);
        }
        JSONArray releases = new JSONArray(body);
        String prefix = getPrefix();
        JSONObject target = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            String tag = release == null ? "" : release.optString("tag_name", "");
            if (tag.startsWith("v" + prefix) && getApkUrl(release) != null) {
                if (target == null || compare(tag, target.optString("tag_name")) > 0) target = release;
            }
        }
        return target;
    }

    /** 当前版本系列前缀，如 5.6.4 -> "5.6." */
    private static String getPrefix() {
        String version = BuildConfig.VERSION_NAME;
        int i = version.lastIndexOf('.');
        return i > 0 ? version.substring(0, i + 1) : version + ".";
    }

    private static int compare(String a, String b) {
        String[] x = stripV(a).split("\\.");
        String[] y = stripV(b).split("\\.");
        int len = Math.max(x.length, y.length);
        for (int i = 0; i < len; i++) {
            int p = i < x.length ? parseInt(x[i]) : 0;
            int q = i < y.length ? parseInt(y[i]) : 0;
            if (p != q) return p - q;
        }
        return 0;
    }

    private static String stripV(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getApkUrl(JSONObject release) {
        JSONArray assets = release == null ? null : release.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            String name = asset == null ? "" : asset.optString("name", "");
            if (name.endsWith(".apk") && name.contains("arm64_v8a")) return asset.optString("browser_download_url");
        }
        return null;
    }

    public static String getMirrorUrl(String url) {
        return MIRROR + url;
    }
}