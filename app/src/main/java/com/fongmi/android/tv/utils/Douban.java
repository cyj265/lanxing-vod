package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class Douban {

    private static final String API_KEY = "0ac44ae016490db2204ce0a042db2916";
    private static final String SUGGEST = "https://movie.douban.com/j/subject_suggest?q=%s";
    private static final String DETAIL = "https://frodo.douban.com/api/v2/movie/%s?apikey=" + API_KEY;
    private static final String UA = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat";
    private static final String REFERER = "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html";

    // 豆瓣请求限速锁，防止并发触发限流
    private static final Object sLock = new Object();
    private static long sLastRequest = 0;

    private static void throttle() {
        synchronized (sLock) {
            long now = System.currentTimeMillis();
            long wait = 120 - (now - sLastRequest);
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
            }
            sLastRequest = System.currentTimeMillis();
        }
    }

    public static String getIntro(String title) {
        if (TextUtils.isEmpty(title)) return "";
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", UA);
            headers.put("Referer", REFERER);
            throttle();
            String suggest = OkHttp.string(String.format(SUGGEST, URLEncoder.encode(title, "UTF-8")), headers);
            JSONArray arr = new JSONArray(suggest);
            if (arr.length() == 0) return "";
            String id = arr.getJSONObject(0).optString("id");
            if (TextUtils.isEmpty(id)) return "";
            throttle();
            String detail = OkHttp.string(String.format(DETAIL, id), headers);
            JSONObject obj = new JSONObject(detail);
            String intro = obj.optString("intro", "");
            if (intro == null) intro = "";
            return intro.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
