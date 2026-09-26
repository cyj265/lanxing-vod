package com.fongmi.android.tv.utils;

import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

public class Github {

    public static final String RELEASE = "https://api.github.com/repos/cyj265/lanxing-vod/releases/latest";
    public static final String MIRROR = "https://gh-proxy.com/";

    public static JSONObject getLatestRelease() throws Exception {
        try {
            return new JSONObject(OkHttp.string(RELEASE));
        } catch (Exception e) {
            return new JSONObject(OkHttp.string(MIRROR + RELEASE));
        }
    }

    public static String getApkUrl(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
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
