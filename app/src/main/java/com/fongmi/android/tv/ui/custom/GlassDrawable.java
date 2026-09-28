package com.fongmi.android.tv.ui.custom;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.fongmi.android.tv.R;

/**
 * 底部导航玻璃背景（液态玻璃观感）：
 * - Android 12+：RenderNode 离屏采样背后内容 + RenderEffect 高斯模糊（系统级实时模糊，性能优）
 * - 旧版本：缩小放大采样模糊降级
 * 统一叠加深色半透明玻璃底、顶部高光线与圆角描边，形成悬浮玻璃质感。
 * 仅由 HomeActivity 的底部导航使用。
 */
public class GlassDrawable extends Drawable {

    private static final int SCALE = 4;
    private static final float BLUR_RADIUS_DP = 30f;

    private final View mContent;
    private final Paint mPaint;
    private final Choreographer mChoreographer;
    private final Choreographer.FrameCallback mFrame;
    private final float mRadiusPx;
    private final boolean mV31;

    private Bitmap mCache;
    private Canvas mCacheCanvas;
    private RenderNode mNode;
    private RenderEffect mBlur;
    private boolean mActive;

    public GlassDrawable(View content) {
        mContent = content;
        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        mRadiusPx = dp(BLUR_RADIUS_DP);
        mV31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        if (mV31) initV31();
        mChoreographer = Choreographer.getInstance();
        mFrame = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!mActive) return;
                invalidateSelf();
                mChoreographer.postFrameCallback(this);
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void initV31() {
        mNode = new RenderNode("glass-blur");
        mBlur = RenderEffect.createBlurEffect(mRadiusPx, mRadiusPx, Shader.TileMode.CLAMP);
        mNode.setRenderEffect(mBlur);
    }

    /** 页面可见时开启帧刷新，不可见时停止，避免空转。 */
    public void setActive(boolean active) {
        if (mActive == active) return;
        mActive = active;
        if (active) mChoreographer.postFrameCallback(mFrame);
        else mChoreographer.removeFrameCallback(mFrame);
    }

    private float dp(float value) {
        return value * mContent.getResources().getDisplayMetrics().density;
    }

    private void ensureCache(int w, int h) {
        if (mCache != null && mCache.getWidth() >= w && mCache.getHeight() >= h) return;
        if (mCache != null) mCache.recycle();
        mCache = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
        mCacheCanvas = new Canvas(mCache);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        if (mContent.getWidth() <= 0 || mContent.getHeight() <= 0) return;

        canvas.save();
        Path clip = new Path();
        clip.addRoundRect(new RectF(b.left, b.top, b.right, b.bottom), mRadiusPx, mRadiusPx, Path.Direction.CW);
        canvas.clipPath(clip);
        if (mV31) {
            drawV31(canvas, b);
        } else {
            drawLegacy(canvas, b);
        }
        drawGlassLayer(canvas, b);
        canvas.restore();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void drawV31(Canvas canvas, Rect b) {
        int w = b.width();
        int h = b.height();
        RecordingCanvas rc = mNode.beginRecording(w, h);
        try {
            rc.translate(-b.left, -b.top);
            mContent.draw(rc);
        } finally {
            mNode.endRecording();
        }
        canvas.drawRenderNode(mNode);
    }

    private void drawLegacy(Canvas canvas, Rect b) {
        int w = mContent.getWidth();
        int h = mContent.getHeight();
        int cw = w / SCALE;
        int ch = h / SCALE;
        ensureCache(cw, ch);

        mCacheCanvas.save();
        mCacheCanvas.scale(1f / SCALE, 1f / SCALE);
        mContent.draw(mCacheCanvas);
        mCacheCanvas.restore();

        int srcH = Math.min(mCache.getHeight(), b.height() / SCALE + 2);
        Rect src = new Rect(0, mCache.getHeight() - srcH, mCache.getWidth(), mCache.getHeight());
        canvas.drawBitmap(mCache, src, b, mPaint);
    }

    /** 玻璃质感层：半透明底色 + 顶部高光线 + 圆角描边。 */
    private void drawGlassLayer(Canvas canvas, Rect b) {
        Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
        glass.setColor(0x40121212);
        canvas.drawRoundRect(new RectF(b.left, b.top, b.right, b.bottom), mRadiusPx, mRadiusPx, glass);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0x55FFFFFF);
        canvas.drawRoundRect(new RectF(b.left + dp(4), b.top + dp(1), b.right - dp(4), b.top + dp(3)), dp(1.5f), dp(1.5f), line);

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1));
        line.setColor(0x33FFFFFF);
        canvas.drawRoundRect(new RectF(b.left + dp(0.5f), b.top + dp(0.5f), b.right - dp(0.5f), b.bottom - dp(0.5f)), mRadiusPx, mRadiusPx, line);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        outline.setRoundRect(b, mRadiusPx);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
