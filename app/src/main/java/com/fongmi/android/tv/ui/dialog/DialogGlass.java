package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;

import com.fongmi.android.tv.utils.ResUtil;

/**
 * 全 App 弹窗统一视觉：半透明深色玻璃底板 + 细描边，Android 12+ 叠系统背后模糊。
 * <p>
 * 底板用代码构造而不是引用 mobile 的 drawable，保证 main / mobile / leanback 三个
 * source set 都能共用同一套数值，也不会被各布局 XML 里写死的 @color/bg 之类的底色盖掉。
 */
public final class DialogGlass {

    private static final int GLASS_COLOR = 0x991B1E26;
    private static final int STROKE_COLOR = 0x33FFFFFF;
    private static final int RADIUS_DP = 16;
    // 24dp 会把背后的海报糊成一片色块，收敛到 12dp：仍是毛玻璃，但背后画面可辨
    private static final int BLUR_DP = 12;

    private DialogGlass() {
    }

    /** 居中 AlertDialog：窗口底色换成玻璃卡片，旧设备退化为半透明卡片 */
    public static void apply(Dialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(background(false));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(ResUtil.dp2px(BLUR_DP));
        }
    }

    /** 底部弹窗：把内容根的背景统一替换掉，忽略各布局自己写的不透明底色 */
    public static void applySheet(View content) {
        if (content == null) return;
        content.setBackground(background(true));
    }

    private static GradientDrawable background(boolean bottomSheet) {
        float radius = ResUtil.dp2px(RADIUS_DP);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(GLASS_COLOR);
        if (bottomSheet) {
            drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        } else {
            drawable.setCornerRadius(radius);
        }
        drawable.setStroke(Math.max(1, ResUtil.dp2px(1)), STROKE_COLOR);
        return drawable;
    }
}
