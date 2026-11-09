package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

/**
 * 海报取色的 MD3 tonal 主题：深底 + 浅字 + 同源强调色。
 * App 本体是 Material3 Dark，这里必须同样落在深色区间，否则详情页会在整站深色里
 * 突然变成米白/薄荷浅底，既刺眼又和首页、设置、记录页对不上。
 */
public class VodTheme {

    public static final int NEUTRAL_SEED = 0xFF8A7A6E;

    public final int surface;
    public final int card;
    public final int accent;
    public final int onAccent;
    public final int onSurface;
    public final int onVariant;
    private final boolean glass;

    private VodTheme(int seed) {
        glass = false;
        surface = mix(0xFF121110, seed, 0.10f);
        card = mix(surface, Color.WHITE, 0.06f);
        onSurface = mix(0xFFF3EFEB, seed, 0.06f);
        onVariant = ColorUtils.setAlphaComponent(onSurface, 0xB3);
        accent = vivid(seed);
        onAccent = ColorUtils.calculateLuminance(accent) > 0.45 ? 0xFF14110F : Color.WHITE;
    }

    /** 记录页等「透出壁纸」的页面：玻璃卡片 + 白字，和首页/设置同一套材质 */
    private VodTheme(boolean glass) {
        this.glass = glass;
        surface = Color.TRANSPARENT;
        card = 0x26000000;
        onSurface = 0xFFF5F2EE;
        onVariant = ColorUtils.setAlphaComponent(onSurface, 0xB3);
        accent = vivid(0xFF1FD6A6);
        onAccent = ColorUtils.calculateLuminance(accent) > 0.45 ? 0xFF14110F : Color.WHITE;
    }

    public static VodTheme from(int seed) {
        return new VodTheme(seed);
    }

    public static VodTheme glass() {
        return new VodTheme(true);
    }

    /** 取海报色相，把饱和度和亮度拉满：深色/灰海报也能得到可用的强调色 */
    private static int vivid(int seed) {
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        hsv[1] = Math.max(hsv[1], 0.55f);
        hsv[2] = Math.max(hsv[2], 0.80f);
        return Color.HSVToColor(hsv);
    }

    public interface Callback {
        void onTheme(VodTheme theme);
    }

    public static void extract(Bitmap bitmap, Callback callback) {
        Palette.from(bitmap).maximumColorCount(16).generate(palette -> {
            Palette.Swatch swatch = palette.getVibrantSwatch();
            if (swatch == null) swatch = palette.getDominantSwatch();
            callback.onTheme(from(swatch != null ? swatch.getRgb() : NEUTRAL_SEED));
        });
    }

    public static int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** 圆角卡片：选中时主题色描边 */
    public GradientDrawable card(boolean selected, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radiusPx);
        d.setColor(selected ? mix(card, accent, 0.20f) : card);
        int stroke = selected ? accent : (glass ? Color.TRANSPARENT : 0x1FFFFFFF);
        d.setStroke(ResUtil.dp2px(selected ? 2 : 1), stroke);
        return d;
    }

    public GradientDrawable circle(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    /** 详情页操作圆钮：10% 白底 + 细描边，深底上不抢戏 */
    public GradientDrawable actionCircle() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0x1AFFFFFF);
        d.setStroke(ResUtil.dp2px(1), 0x29FFFFFF);
        return d;
    }

    /** 压在剧照上的悬浮圆钮：半透明黑底，任何亮度的图都能看清图标 */
    public GradientDrawable floatingCircle() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0x73000000);
        d.setStroke(ResUtil.dp2px(1), 0x33FFFFFF);
        return d;
    }

    /** 头图底部收敛到页面底色：上半透明、66% 处基本不透明，文字区落在纯深底上 */
    public GradientDrawable headerScrim() {
        int transparent = ColorUtils.setAlphaComponent(surface, 0);
        int half = ColorUtils.setAlphaComponent(surface, 0x66);
        int mostly = ColorUtils.setAlphaComponent(surface, 0xB3);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{transparent, half, mostly, surface});
        return d;
    }
}
