package com.fongmi.android.tv.utils;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.FIT_CENTER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Json;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.HttpHeaders;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import jahirfiquitiva.libs.textdrawable.TextDrawable;

public class ImgUtil {

    private static final int MAX_CACHE_SIZE = 16 * 1024 * 1024;
    private static final int MAX_DATA_URI_LENGTH = 8 * 1024 * 1024;
    private static final long FAILED_RETRY_MS = 5 * 60 * 1000L;
    private static final Pattern IMAGE_MIME = Pattern.compile("image/[a-z0-9.+-]+");
    // 失败 URL -> 失败时间戳；超时后允许重试，避免弱网/临时故障导致海报永久不显示
    private static final Map<String, Long> failed = new ConcurrentHashMap<>();
    private static final Cache<String, Image> CACHE = CacheBuilder.newBuilder().maximumWeight(MAX_CACHE_SIZE).weigher((String key, Image value) -> value.data().length).build();

    public static void logo(ImageView view) {
        try {
            Glide.with(view).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String url, CustomTarget<Bitmap> target) {
        try {
            Glide.with(App.get()).asBitmap().load(getUrl(url)).override(ResUtil.dp2px(96), ResUtil.dp2px(96)).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(Context context, String url, CustomTarget<Drawable> target) {
        try {
            Glide.with(context).load(getUrl(url)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String text, String url, ImageView view) {
        load(text, url, view, true);
    }

    public static void load(String text, String url, ImageView view, boolean vod) {
        if (view == null) return;
        view.setScaleType(vod ? CENTER_CROP : FIT_CENTER);
        if (!vod) view.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(url) || isFailed(url)) view.setImageDrawable(getTextDrawable(text, vod));
        else try {
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url)).listener(getListener(text, url, view, vod));
            if (vod) builder.centerCrop().into(view);
            else builder.fitCenter().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 按候选顺序加载，任一级失败自动回退到下一级，全部失败才显示文字占位。
     * 用于详情页整屏背景：豆瓣大图 → 站源原图 → 站源原 URL。
     * 刻意不写 failed 缓存：详情页的一次失败不该让列表页同一张海报在 5 分钟内变成色块。
     */
    public static void loadWithFallback(String text, List<String> urls, ImageView view) {
        if (view == null || urls == null) return;
        loadStep(text, urls, 0, view);
    }

    private static void loadStep(String text, List<String> urls, int index, ImageView view) {
        if (index >= urls.size() || TextUtils.isEmpty(urls.get(index))) {
            view.setScaleType(CENTER_CROP);
            view.setImageDrawable(getTextDrawable(text, true));
            return;
        }
        String url = urls.get(index);
        view.setScaleType(CENTER_CROP);
        try {
            Drawable current = view.getDrawable();
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url)).listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                    // 回到主线程再发起下一级，避免在 Glide 回调里重入同一个 target
                    App.post(() -> loadStep(text, urls, index + 1, view));
                    return true;
                }

                @Override
                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                    return false;
                }
            });
            // 详情剧照会从列表小图换成豆瓣大图，用当前画面做占位淡入，避免硬切一下
            if (current != null) builder = builder.placeholder(current);
            builder.transition(DrawableTransitionOptions.withCrossFade(220)).centerCrop().into(view);
        } catch (Throwable e) {
            App.post(() -> loadStep(text, urls, index + 1, view));
        }
    }

    /** 文件名里的 WordPress 式尺寸后缀，如 xxx-300x450.jpg / xxx_400x600.webp */
    private static final Pattern SIZE_SUFFIX = Pattern.compile("(?i)[-_]\\d{2,4}x\\d{2,4}(?=\\.(?:jpg|jpeg|png|webp|gif|bmp)$)");
    /** 又拍云图片处理分隔符，如 xxx.jpg!w300 */
    private static final Pattern UPAI_SUFFIX = Pattern.compile("(?i)(\\.(?:jpg|jpeg|png|webp|gif))![^/]*$");

    /**
     * 站源图片 URL 归一化：剥掉常见的缩略/缩放参数，尝试取原图。
     * <p>
     * 只改 URL 字符串，不改 {@link #getUrl(String)} 的公共行为 —— 列表页本来就显示小图，
     * 换原图会白耗流量并改变 Glide 缓存键。详情页整屏背景才会用本方法。
     * 规则保守：只删明确的缩放参数和尺寸后缀，token / sign / 其它业务参数一律保留。
     */
    public static String original(String url) {
        if (TextUtils.isEmpty(url) || url.startsWith("data:")) return url;
        // @Referer= / @Cookie= / @Headers= 是本地图片语法，必须原样保留
        int at = url.indexOf('@');
        String body = at >= 0 ? url.substring(0, at) : url;
        String tail = at >= 0 ? url.substring(at) : "";
        return normalize(body) + tail;
    }

    private static String normalize(String body) {
        int q = body.indexOf('?');
        String path = q >= 0 ? body.substring(0, q) : body;
        String query = q >= 0 ? body.substring(q + 1) : "";
        path = SIZE_SUFFIX.matcher(path).replaceAll("");
        path = UPAI_SUFFIX.matcher(path).replaceAll("$1");
        if (!query.isEmpty()) {
            StringBuilder kept = new StringBuilder();
            for (String pair : query.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                String key = (eq >= 0 ? pair.substring(0, eq) : pair).toLowerCase(Locale.ROOT);
                String value = eq >= 0 ? pair.substring(eq + 1) : "";
                if (isScaleParam(key, value)) continue;
                if (kept.length() > 0) kept.append('&');
                kept.append(pair);
            }
            query = kept.toString();
        }
        return query.isEmpty() ? path : path + "?" + query;
    }

    private static boolean isScaleParam(String key, String value) {
        String v = value.toLowerCase(Locale.ROOT);
        switch (key) {
            case "x-oss-process":
                return v.contains("resize") || v.contains("thumbnail") || v.contains("imageview");
            case "imagemogr2":
                return v.contains("thumbnail") || v.contains("imageview");
            case "w":
            case "h":
            case "width":
            case "height":
                return v.matches("\\d{1,4}");
            case "thumb":
            case "thumbnail":
            case "size":
                return v.isEmpty() || v.matches("(?i)(small|thumb|s|m|\\d{1,4})");
            default:
                return false;
        }
    }

    public static Object getUrl(String url) {
        String param = null;
        url = UrlUtil.convert(url);
        if (url.startsWith("data:")) return url;
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        if (url.contains("@Headers=")) addHeader(builder, param = url.split("@Headers=")[1].split("@")[0]);
        if (url.contains("@Cookie=")) builder.addHeader(HttpHeaders.COOKIE, param = url.split("@Cookie=")[1].split("@")[0]);
        if (url.contains("@Referer=")) builder.addHeader(HttpHeaders.REFERER, param = url.split("@Referer=")[1].split("@")[0]);
        if (url.contains("@User-Agent=")) builder.addHeader(HttpHeaders.USER_AGENT, param = url.split("@User-Agent=")[1].split("@")[0]);
        url = param == null ? url : url.split("@")[0];
        return TextUtils.isEmpty(url) ? null : new GlideUrl(url, builder.build());
    }

    public static String cache(String url) {
        if (!isData(url)) return url;
        if (url.length() > MAX_DATA_URI_LENGTH) return "";
        String key = Crypto.md5(url);
        if (TextUtils.isEmpty(key)) return "";
        if (CACHE.asMap().computeIfAbsent(key, ignored -> decode(url)) == null) return "";
        String address = Server.get().getAddress("/image/" + key);
        failed.remove(address);
        return address;
    }

    private static boolean isData(String url) {
        return !TextUtils.isEmpty(url) && url.regionMatches(true, 0, "data:", 0, 5);
    }

    private static Image decode(String url) {
        try {
            int comma = url.indexOf(',');
            if (comma == -1) return null;
            String metadata = url.substring(5, comma).toLowerCase(Locale.ROOT);
            String mime = metadata.split(";", 2)[0];
            if (!IMAGE_MIME.matcher(mime).matches() || !metadata.endsWith(";base64")) return null;
            byte[] data = Base64.decode(url.substring(comma + 1), Base64.DEFAULT);
            return data.length == 0 ? null : new Image(mime, data);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Image getImage(String key) {
        return CACHE.getIfPresent(key);
    }

    public record Image(String mime, byte[] data) {
    }

    private static void addHeader(LazyHeaders.Builder builder, String header) {
        Map<String, String> map = Json.toMap(Json.parse(header));
        for (Map.Entry<String, String> entry : map.entrySet()) builder.addHeader(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
    }

    private static Drawable getTextDrawable(String text, boolean vod) {
        TextDrawable.Builder builder = new TextDrawable.Builder();
        text = TextUtils.isEmpty(text) ? "！" : text.substring(0, 1);
        if (vod) builder.buildRect(text, ColorGenerator.get400(text));
        return builder.buildRoundRect(text, ColorGenerator.get400(text), ResUtil.dp2px(4));
    }

    private static boolean isFailed(String url) {
        Long time = failed.get(url);
        if (time == null) return false;
        if (System.currentTimeMillis() - time > FAILED_RETRY_MS) {
            failed.remove(url);
            return false;
        }
        return true;
    }

    private static RequestListener<Drawable> getListener(String text, String url, ImageView view, boolean vod) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                view.setImageDrawable(getTextDrawable(text, vod));
                failed.put(url, System.currentTimeMillis());
                return true;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                return false;
            }
        };
    }
}
