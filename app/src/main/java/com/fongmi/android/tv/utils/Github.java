package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

public class Github {

    /**
     * 更新清单地址：raw.githubusercontent 上的 update.json。
     * 不用 api.github.com 的原因是：国内网络直连与常见镜像均不稳定，会导致“检查不到更新”；
     * raw 域名的 gh-proxy 等镜像加速稳定（已在真实网络验证）。
     */
    public static final String UPDATE = "https://raw.githubusercontent.com/cyj265/lanxing-vod/dev-5.6.1/update.json";
    private static final String[] MIRRORS = {"https://gh-proxy.com/", "https://ghfast.top/", "https://ghproxy.net/"};

    /**
     * 读取 update.json 清单，只认版本号高于当前安装版本的；无更新或清单异常返回 null。
     */
    public static JSONObject getLatestRelease() throws Exception {
        String body = fetch(UPDATE);
        JSONObject json = new JSONObject(body);
        String version = json.optString("version", "");
        String url = json.optString("url", "");
        String current = BuildConfig.VERSION_NAME;
        if (version.isEmpty() || url.isEmpty()) return null;
        if (compare(version, current) <= 0) return null;
        JSONObject release = new JSONObject();
        release.put("tag_name", "v" + version);
        release.put("apk_url", url);
        return release;
    }

    /** 依次尝试直连与多个镜像，任一成功即返回 */
    private static String fetch(String url) throws Exception {
        Exception last = null;
        try {
            return OkHttp.string(url);
        } catch (Exception e) {
            last = e;
        }
        for (String mirror : MIRRORS) {
            try {
                return OkHttp.string(mirror + url);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last == null ? new Exception("all fetch failed") : last;
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
        return release == null ? null : release.optString("apk_url", null);
    }

    /** 依次尝试各镜像的下载加速地址 */
    public static String getMirrorUrl(String url) {
        return MIRRORS[0] + url;
    }
}
