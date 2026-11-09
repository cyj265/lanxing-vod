package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

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
            return getIntroById(id);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 榜单条目直接凭豆瓣 id 取简介（跳过 suggest 搜索，快且命中率高）
     */
    public static String getIntroById(String id) {
        if (TextUtils.isEmpty(id)) return "";
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", UA);
            headers.put("Referer", REFERER);
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
     * 详情页整屏背景用的高清海报。
     * <p>
     * 豆瓣 subject_suggest 返回的 img 是 s_ratio_poster（实测 270×404），把 URL 里的尺寸段
     * 换成 /l/ 即可拿到原尺寸大图（实测 1080×1914，≈ 手机全屏，无需插值放大）。
     * 图片 CDN 必须带 Referer，否则 418；ImgUtil 原生支持 @Referer= 后缀。
     */
    private static final Pattern PHOTO_SIZE = Pattern.compile("/view/photo/[^/]+/");
    private static final String PHOTO_LARGE = "/view/photo/l/";
    private static final String PIC_REFERER = "@Referer=https://movie.douban.com/";
    private static final ConcurrentHashMap<String, String> PIC_CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService PIC_EXECUTOR = Executors.newSingleThreadExecutor();

    public interface PicCallback {
        void onResult(String url);
    }

    /** 后台按片名查高清海报，结果回到主线程；空串表示未命中，调用方应回退站源图 */
    public static void getLargePicAsync(String name, String year, PicCallback callback) {
        PIC_EXECUTOR.execute(() -> {
            String url = getLargePic(name, year);
            if (callback != null) App.post(() -> callback.onResult(url));
        });
    }

    public static String getLargePic(String name, String year) {
        String key = name + "|" + year;
        String cached = PIC_CACHE.get(key);
        if (cached != null) return cached;
        String result = queryLargePic(name, year);
        PIC_CACHE.put(key, result);
        return result;
    }

    private static String queryLargePic(String name, String year) {
        String clean = cleanName(name);
        if (TextUtils.isEmpty(clean)) return "";
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", UA);
            headers.put("Referer", REFERER);
            throttle();
            String json = OkHttp.string(String.format(SUGGEST, URLEncoder.encode(clean, "UTF-8")), headers);
            JSONArray arr = new JSONArray(json);
            JSONObject best = null;
            int bestScore = -1;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                // 只接受片名完全一致；同名续作/系列片宁可回退站源图，也不贴错海报
                if (!clean.equals(item.optString("title", ""))) continue;
                int score = 1;
                String y = item.optString("year", "");
                if (!TextUtils.isEmpty(year) && year.equals(y)) score = 3;
                else if (TextUtils.isEmpty(y)) score = 2;
                if (score > bestScore) {
                    bestScore = score;
                    best = item;
                }
            }
            if (best == null) return "";
            String img = best.optString("img", "");
            if (TextUtils.isEmpty(img) || !img.contains("/view/photo/")) return "";
            return PHOTO_SIZE.matcher(img).replaceAll(PHOTO_LARGE) + PIC_REFERER;
        } catch (Exception e) {
            return "";
        }
    }

    /** 站点片名常带「更新至 40 集」「HD」等尾巴，先清洗再去 suggest */
    private static String cleanName(String name) {
        if (name == null) return "";
        return name.trim()
                .replaceAll("(?i)\\s*(更新至\\s*\\d+\\s*集|共\\s*\\d+\\s*集|全\\s*\\d+\\s*集|HD|BD|4K|1080P|720P|完结|全集)\\s*$", "")
                .trim();
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
                    // 豆瓣榜单图片：部分榜单在 cover.url，部分在 pic.large
                    String pic = "";
                    JSONObject cover = item.optJSONObject("cover");
                    if (cover != null) pic = cover.optString("url", "");
                    if (TextUtils.isEmpty(pic)) {
                        JSONObject picObj = item.optJSONObject("pic");
                        if (picObj != null) pic = picObj.optString("large", "");
                    }
                    if (!TextUtils.isEmpty(pic)) {
                        // 豆瓣图片防盗链，必须带 Referer 否则 418
                        vod.setPic(pic + "@Referer=https://movie.douban.com/");
                    }
                    // 部分榜单(电视/动漫/韩剧)自带简介
                    String comment = item.optString("comment", "");
                    if (!TextUtils.isEmpty(comment)) vod.setContent(comment.trim());
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
