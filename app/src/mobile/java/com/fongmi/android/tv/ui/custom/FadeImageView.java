package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.fongmi.android.tv.utils.AppLog;

/**
 * 底部渐隐的 ImageView。
 * <p>
 * 详情页用剧照当背景时，硬切会像「半张图 + 一块底色」；这里用 DST_IN 遮罩把图片下半段
 * 渐隐到真正的透明，露出下面的壁纸，而不是用一块固定色去盖（壁纸可能是任意颜色或图片）。
 */
public class FadeImageView extends AppCompatImageView {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fadeStart = 0.35f;
    private boolean fadeEnabled = true;

    public FadeImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** 开始淡出的高度比例（0~1），越大留白越多 */
    public void setFadeStart(float ratio) {
        this.fadeStart = Math.max(0f, Math.min(0.95f, ratio));
        invalidate();
    }

    /** 详情态剧照要整屏不透明铺底，渐隐改由上层 scrim 完成，这里可关掉 */
    public void setFadeEnabled(boolean enabled) {
        this.fadeEnabled = enabled;
        // DST_IN 渐隐遮罩必须走软件层；详情态不需要遮罩时切回硬件层，
        // 大图交给 GPU 采样绘制，避免软件层位图缩放带来的额外插值损失
        setLayerType(enabled ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_HARDWARE, null);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!fadeEnabled) {
            try {
                super.onDraw(canvas);
            } catch (RuntimeException e) {
                dropBrokenDrawable(e);
            }
            return;
        }
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        int save = canvas.saveLayer(0, 0, width, height, null);
        try {
            super.onDraw(canvas);
            int start = (int) (height * fadeStart);
            paint.setShader(new LinearGradient(0, start, 0, height, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
        } catch (RuntimeException e) {
            dropBrokenDrawable(e);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    /**
     * Glide 新旧请求交接时可能回收 View 仍在绘制的位图（Canvas: trying to use a recycled
     * bitmap / GifDrawable 空帧 NPE）。ImgUtil 已改为独立快照占位，这里再兜一层：
     * 摘掉失效画面让下一帧重绘，避免整页崩溃（Bugly #7014 / #3035 / #3037）。
     */
    private void dropBrokenDrawable(RuntimeException e) {
        AppLog.e(e);
        Drawable broken = getDrawable();
        // 只在坏 Drawable 还没被 Glide 的新资源替换掉时才摘除，避免误清新图
        post(() -> {
            if (getDrawable() == broken) setImageDrawable(null);
        });
    }
}
