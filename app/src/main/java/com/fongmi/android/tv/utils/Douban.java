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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Douban {

    private static final String API_KEY = "0ac44ae016490db2204ce0a042db2916";
    /** 主路径：豆瓣网页搜索建议，历史上出现过整段返回 200 + [] 的偶发故障 */
    private static final String SUGGEST = "https://movie.douban.com/j/subject_suggest?q=%s";
    /** 备用路径 1：www 搜索建议，独立端点，零 apikey */
    private static final String WWW_SUGGEST = "https://www.douban.com/j/search_suggest?q=%s";
    /** 备用路径 2：frodo 搜索，与主路径不同域、不同后端 */
    private static final String SEARCH = "https://frodo.douban.com/api/v2/search/movie?q=%s&apikey=" + API_KEY + "&start=0&count=8";
    /** 备用路径 3：已知 subject id 时直接取详情 cover，与前三条搜索链路完全独立 */
    private static final String DETAIL = "https://frodo.douban.com/api/v2/movie/%s?apikey=" + API_KEY;
    /** 图库：单剧目竖版海报/宣传图，用于详情页多图轮播（横版一律丢弃） */
    private static final String PHOTOS = "https://frodo.douban.com/api/v2/%s/%s/photos?apikey=" + API_KEY + "&start=0&count=%d";
    private static final String RANK = "https://frodo.douban.com/api/v2/subject_collection/%s/items?apikey=" + API_KEY + "&start=0&count=%d";

    private static final String UA = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat";
    private static final String REFERER = "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html";
    private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String MOVIE_REFERER = "https://movie.douban.com/";
    private static final String WWW_REFERER = "https://www.douban.com/";

    private static final String PIC_REFERER = "@Referer=" + MOVIE_REFERER;
    private static final String LARGE_PATH = "/view/photo/l/public/";
    private static final String DEFAULT_IMG_HOST = "img3.doubanio.com";
    /** 豆瓣所有图片 URL 里都带全局唯一的 pXXXXXXXX.jpg，抽出来即可拼任意尺寸 */
    private static final Pattern PHOTO_ID = Pattern.compile("(p\\d+\\.(?:jpg|jpeg|png|webp))", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_HOST = Pattern.compile("https?://(img\\d+\\.doubanio\\.com)/");
    private static final Pattern SUBJECT_ID = Pattern.compile("/subject/(\\d+)");

    /** 命中结果缓存 24h；失败只缓存 10 分钟，避免服务端偶发故障被永久记住 */
    private static final long POSITIVE_TTL = 24 * 60 * 60 * 1000L;
    private static final long NEGATIVE_TTL = 10 * 60 * 1000L;
    private static final int MAX_BACKDROPS = 8;
    /** 图库里竖版只占两三成，一页取 50 张才够凑出多张候选供每次进入随机挑 */
    private static final int PHOTO_PAGE_SIZE = 50;
    /** 严格相等(10) + 有海报(4)，达到这个分就不必再打备用接口 */
    private static final int GOOD_ENOUGH = 14;

    // 豆瓣请求限速锁，防止并发触发限流
    private static final Object sLock = new Object();
    private static long sLastRequest = 0;

    private static final ExecutorService PIC_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ConcurrentHashMap<String, Entry<String>> PIC_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Entry<Subject>> SUBJECT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Entry<List<String>>> BACKDROP_CACHE = new ConcurrentHashMap<>();

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

    private static String fetch(String url, String ua, String referer) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", ua);
        headers.put("Referer", referer);
        throttle();
        return OkHttp.string(url, headers);
    }

    /** 统一把任意来源的图片 URL 转成带 Referer 的 /l/ 大图 */
    private static String toLarge(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Matcher id = PHOTO_ID.matcher(url);
        if (!id.find()) return "";
        String host = DEFAULT_IMG_HOST;
        Matcher h = IMG_HOST.matcher(url);
        // qnmob3-sign.doubanio.com 是带签名的临时域名，改尺寸会 403，统一换公开 CDN
        if (h.find()) host = h.group(1);
        return "https://" + host + LARGE_PATH + id.group(1) + PIC_REFERER;
    }

    private static class Subject {
        String id = "";
        String title = "";
        String year = "";
        String pic = "";
        String kind = "";
    }

    private static class Entry<T> {
        final T value;
        final long expireAt;

        Entry(T value, long ttl) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttl;
        }
    }

    private static <T> boolean valid(ConcurrentHashMap<String, Entry<T>> cache, String key) {
        Entry<T> entry = cache.get(key);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expireAt) {
            cache.remove(key);
            return false;
        }
        return true;
    }

    private static <T> T cached(ConcurrentHashMap<String, Entry<T>> cache, String key) {
        Entry<T> entry = cache.get(key);
        return entry == null ? null : entry.value;
    }

    private static <T> void store(ConcurrentHashMap<String, Entry<T>> cache, String key, T value, long ttl) {
        cache.put(key, new Entry<>(value, ttl));
    }

    // ---------------------------------------------------------------- 搜索：多路径故障转移

    /** 主路径：movie.douban.com/j/subject_suggest */
    private static List<Subject> fromSuggest(String clean) {
        List<Subject> result = new ArrayList<>();
        try {
            String json = fetch(String.format(SUGGEST, URLEncoder.encode(clean, "UTF-8")), BROWSER_UA, MOVIE_REFERER);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                Subject s = new Subject();
                s.title = item.optString("title", "");
                s.year = item.optString("year", "");
                s.pic = toLarge(item.optString("img", ""));
                s.id = item.optString("id", "");
                result.add(s);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /** 备用 1：www.douban.com/j/search_suggest */
    private static List<Subject> fromWwwSuggest(String clean) {
        List<Subject> result = new ArrayList<>();
        try {
            String json = fetch(String.format(WWW_SUGGEST, URLEncoder.encode(clean, "UTF-8")), BROWSER_UA, WWW_REFERER);
            JSONObject obj = new JSONObject(json);
            JSONArray cards = obj.optJSONArray("cards");
            if (cards == null) return result;
            for (int i = 0; i < cards.length(); i++) {
                JSONObject item = cards.optJSONObject(i);
                if (item == null) continue;
                Subject s = new Subject();
                s.title = item.optString("title", "");
                s.year = item.optString("year", "");
                s.pic = toLarge(item.optString("cover_url", ""));
                Matcher m = SUBJECT_ID.matcher(item.optString("url", ""));
                if (m.find()) s.id = m.group(1);
                result.add(s);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /** 备用 2：frodo search/movie，cover_url 带签名，靠 toLarge 抽 pXXXX 重拼 */
    private static List<Subject> fromFrodoSearch(String clean) {
        List<Subject> result = new ArrayList<>();
        try {
            String json = fetch(String.format(SEARCH, URLEncoder.encode(clean, "UTF-8")), UA, REFERER);
            JSONObject obj = new JSONObject(json);
            JSONArray items = obj.optJSONArray("items");
            if (items == null) return result;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                JSONObject target = item.optJSONObject("target");
                if (target == null) continue;
                Subject s = new Subject();
                s.title = target.optString("title", "");
                s.year = target.optString("year", "");
                s.pic = toLarge(target.optString("cover_url", ""));
                s.id = target.optString("id", "");
                s.kind = target.optString("uri", "").contains("/tv/") ? "tv" : "movie";
                result.add(s);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /** 简介用：只要拿到非空结果即可，不要求片名严格相等 */
    private static List<Subject> findSubjects(String clean) {
        List<Subject> list = fromSuggest(clean);
        if (!list.isEmpty()) return list;
        list = fromWwwSuggest(clean);
        if (!list.isEmpty()) return list;
        return fromFrodoSearch(clean);
    }

    /** 海报用：逐条路径尝试；主路径整段故障或没拿到好结果时自动落到下一条 */
    private static Subject findBest(String clean, String year) {
        Subject best = null;
        int bestScore = -1;
        Subject hit = pickBest(fromSuggest(clean), clean, year);
        int score = hit == null ? -1 : matchScore(hit, clean, year);
        if (score > bestScore) { bestScore = score; best = hit; }
        if (bestScore >= GOOD_ENOUGH) return best;
        hit = pickBest(fromWwwSuggest(clean), clean, year);
        score = hit == null ? -1 : matchScore(hit, clean, year);
        if (score > bestScore) { bestScore = score; best = hit; }
        if (bestScore >= GOOD_ENOUGH) return best;
        hit = pickBest(fromFrodoSearch(clean), clean, year);
        score = hit == null ? -1 : matchScore(hit, clean, year);
        if (score > bestScore) best = hit;
        return best;
    }

    private static Subject findBestCached(String clean, String year) {
        String key = clean + "|" + year;
        if (valid(SUBJECT_CACHE, key)) return cached(SUBJECT_CACHE, key);
        Subject best = findBest(clean, year);
        store(SUBJECT_CACHE, key, best, best == null ? NEGATIVE_TTL : POSITIVE_TTL);
        return best;
    }

    /** 明确可接受的系列后缀，避免为了凑图把「无名」贴成「无名之辈」 */
    private static final Pattern SEASON_SUFFIX = Pattern.compile("(?i)^\\s*(第[一二三四五六七八九十\\d]+季|season\\s*\\d+|\\d+|剧场版|合集|篇)\\s*$");

    private static Subject pickBest(List<Subject> list, String clean, String year) {
        Subject best = null;
        int bestScore = -1;
        for (Subject s : list) {
            int score = matchScore(s, clean, year);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /**
     * 片名完全一致优先；站源常省略季名（「庆余年」vs「庆余年 第一季」），
     * 只在后缀是明确的季/剧场版/合集时才放宽，其余一律不匹配，宁可回退站源图。
     * 另外把「有没有图」计入评分：备用搜索常夹带同名空壳词条（未上映条目、舞台剧），
     * 它们标题严丝合缝却既无海报也无剧照，不能挤掉有图的季名条目。
     */
    private static int matchScore(Subject s, String clean, String year) {
        if (s == null || TextUtils.isEmpty(s.title)) return -1;
        boolean hasPic = !TextUtils.isEmpty(s.pic);
        boolean hasId = !TextUtils.isEmpty(s.id);
        if (!hasPic && !hasId) return -1;
        int base;
        if (clean.equals(s.title)) base = 10;
        else if (s.title.startsWith(clean) && SEASON_SUFFIX.matcher(s.title.substring(clean.length())).matches()) base = 5;
        else return -1;
        if (!TextUtils.isEmpty(year) && year.equals(s.year)) base += 3;
        else if (TextUtils.isEmpty(s.year)) base += 1;
        // 有可直接下载的海报最优先；只有 id、得再打一次详情接口的候选降权，
        // 免得「同名空壳词条」把有图的季名条目挤掉
        return hasPic ? base + 4 : base - 5;
    }

    /** 备用 3：已知 id 直接取详情 cover.image.large */
    private static String fromDetail(String id) {
        try {
            String json = fetch(String.format(DETAIL, id), UA, REFERER);
            JSONObject obj = new JSONObject(json);
            JSONObject cover = obj.optJSONObject("cover");
            if (cover != null) {
                JSONObject image = cover.optJSONObject("image");
                if (image != null) {
                    JSONObject large = image.optJSONObject("large");
                    if (large != null) {
                        String url = toLarge(large.optString("url", ""));
                        if (!TextUtils.isEmpty(url)) return url;
                    }
                }
            }
            return toLarge(obj.optString("cover_url", ""));
        } catch (Exception ignored) {
            return "";
        }
    }

    // ---------------------------------------------------------------- 简介

    public static String getIntro(String title) {
        if (TextUtils.isEmpty(title)) return "";
        String clean = cleanName(title);
        if (TextUtils.isEmpty(clean)) return "";
        for (Subject s : findSubjects(clean)) {
            if (!TextUtils.isEmpty(s.id)) return getIntroById(s.id);
        }
        return "";
    }

    /** 榜单条目直接凭豆瓣 id 取简介（跳过 suggest 搜索，快且命中率高） */
    public static String getIntroById(String id) {
        if (TextUtils.isEmpty(id)) return "";
        try {
            String detail = fetch(String.format(DETAIL, id), UA, REFERER);
            JSONObject obj = new JSONObject(detail);
            String intro = obj.optString("intro", "");
            if (intro == null) intro = "";
            return intro.trim();
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- 高清海报（兼容原调用）

    public interface PicCallback {
        void onResult(String url);
    }

    public interface PicsCallback {
        void onResult(List<String> urls);
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
        if (valid(PIC_CACHE, key)) {
            String hit = cached(PIC_CACHE, key);
            return hit == null ? "" : hit;
        }
        String result = queryLargePic(name, year);
        store(PIC_CACHE, key, result.isEmpty() ? null : result, result.isEmpty() ? NEGATIVE_TTL : POSITIVE_TTL);
        return result;
    }

    private static String queryLargePic(String name, String year) {
        String clean = cleanName(name);
        if (TextUtils.isEmpty(clean)) return "";
        Subject best = findBestCached(clean, year);
        if (best == null) return "";
        // 解析阶段已统一转成带 Referer 的 /l/ 大图；取不到时再用 id 走详情接口补
        String large = best.pic;
        if (TextUtils.isEmpty(large) && !TextUtils.isEmpty(best.id)) large = fromDetail(best.id);
        return large;
    }

    // ---------------------------------------------------------------- 多张剧照

    /**
     * 详情页背景图列表：第 0 张为高清海报（保证图库挂了也不退化），其后为竖版海报/宣传图。
     * 详情页是竖屏整屏背景，横版图 centerCrop 会被裁成竖条，一律不发。
     * 只在进入详情页时随机取一张展示，不做定时轮播、不重复解码。
     */
    public static void getBackdropsAsync(String name, String year, PicsCallback callback) {
        PIC_EXECUTOR.execute(() -> {
            List<String> urls = getBackdrops(name, year);
            if (callback != null) App.post(() -> callback.onResult(urls));
        });
    }

    public static List<String> getBackdrops(String name, String year) {
        String key = name + "|" + year;
        if (valid(BACKDROP_CACHE, key)) {
            List<String> hit = cached(BACKDROP_CACHE, key);
            return hit == null ? new ArrayList<>() : new ArrayList<>(hit);
        }
        List<String> result = queryBackdrops(name, year);
        store(BACKDROP_CACHE, key, result.isEmpty() ? null : result, result.isEmpty() ? NEGATIVE_TTL : POSITIVE_TTL);
        return result;
    }

    private static List<String> queryBackdrops(String name, String year) {
        List<String> result = new ArrayList<>();
        String clean = cleanName(name);
        if (TextUtils.isEmpty(clean)) return result;
        String poster = getLargePic(name, year);
        if (!TextUtils.isEmpty(poster)) result.add(poster);
        Subject best = findBestCached(clean, year);
        if (best == null || TextUtils.isEmpty(best.id)) return result;
        for (String url : queryStills(best.id, best.kind)) {
            if (!result.contains(url)) result.add(url);
            if (result.size() >= MAX_BACKDROPS) break;
        }
        return result;
    }

    /** 图库只收竖版（高 >= 宽*1.25 且高 >= 900）：横版直接 pass，低分辨率用户上传图同样过滤 */
    private static List<String> queryStills(String id, String kind) {
        List<String> result = new ArrayList<>();
        String[] kinds = TextUtils.isEmpty(kind)
                ? new String[]{"movie", "tv"}
                : new String[]{kind, "movie".equals(kind) ? "tv" : "movie"};
        for (String type : kinds) {
            try {
                String json = fetch(String.format(PHOTOS, type, id, PHOTO_PAGE_SIZE), UA, REFERER);
                JSONObject obj = new JSONObject(json);
                JSONArray photos = obj.optJSONArray("photos");
                if (photos == null) continue;
                for (int i = 0; i < photos.length(); i++) {
                    JSONObject photo = photos.optJSONObject(i);
                    if (photo == null) continue;
                    JSONObject image = photo.optJSONObject("image");
                    if (image == null) continue;
                    JSONObject large = image.optJSONObject("large");
                    if (large == null) continue;
                    int width = large.optInt("width", 0);
                    int height = large.optInt("height", 0);
                    if (height < 900 || width <= 0 || height < width * 1.25f) continue;
                    String url = toLarge(large.optString("url", ""));
                    if (!TextUtils.isEmpty(url) && !result.contains(url)) result.add(url);
                    if (result.size() >= MAX_BACKDROPS) break;
                }
                if (!result.isEmpty()) break;
            } catch (Exception ignored) {
            }
        }
        return result;
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
            String json = fetch(String.format(RANK, collectionId, count), UA, REFERER);
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
                        vod.setPic(pic + "@Referer=" + MOVIE_REFERER);
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
