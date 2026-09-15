package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Douban {

    private static final String API_KEY = "0ac44ae016490db2204ce0a042db2916";
    private static final String SUGGEST = "https://movie.douban.com/j/subject_suggest?q=%s";
    private static final String DETAIL = "https://frodo.douban.com/api/v2/movie/%s?apikey=" + API_KEY;
    private static final String RANK = "https://frodo.douban.com/api/v2/subject_collection/%s/items?apikey=" + API_KEY + "&start=0&count=%d";
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

    /**
     * 获取豆瓣榜单，返回 Vod 列表（vodId=豆瓣id, vodRemarks=评分, vodContent=空需详情补）
     * @param collectionId 榜单id，如 movie_hot / tv_hot / movie_weekly_best / tv_animation / tv_korean / movie_top250
     * @param count 条数
     */
    public static List<Vod> getRank(String collectionId, int count) {
        List<Vod> result = new ArrayList<>();
        if (TextUtils.isEmpty(collectionId)) return result;
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", UA);
            headers.put("Referer", REFERER);
            throttle();
            String json = OkHttp.string(String.format(RANK, collectionId, count), headers);
            JSONObject obj = new JSONObject(json);
            JSONArray items = obj.optJSONArray("subject_collection_items");
            if (items == null) return result;
            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    Vod vod = new Vod();
                    vod.setId(item.optString("id", ""));
                    vod.setName(item.optString("title", ""));
                    JSONObject cover = item.optJSONObject("cover");
                    if (cover != null) vod.setPic(cover.optString("url", ""));
                    JSONObject rating = item.optJSONObject("rating");
                    if (rating != null && rating.has("value")) {
                        double val = rating.optDouble("value", 0);
                        if (val > 0) vod.setRemarks(val + "分");
                    }
                    if (!TextUtils.isEmpty(vod.getName())) result.add(vod);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return result;
    }
}
