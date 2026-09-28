package com.fongmi.android.tv.ui.custom;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.R;

/**
 * 底部导航毛玻璃背景（无第三方依赖）：
 * 每帧采样导航背后的内容，缩小后再放大绘制产生模糊，
 * 叠加统一渐变底色与顶部高光线，形成"玻璃"观感。
 * 仅由 HomeActivity 的底部导航使用。
 */
public class GlassDrawable extends Drawable {

    private static final int SCALE = 4;

    private final View mContent;
    private final Paint mPaint;
    private final Choreographer mChoreographer;
    private final Choreographer.FrameCallback mFrame;
    private final Drawable mBase;
    private Bitmap mCache;
    private Canvas mCacheCanvas;
    private boolean mActive;

    public GlassDrawable(View content) {
        mContent = content;
        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        mBase = content.getContext().getDrawable(R.drawable.bg_global);
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

    /** 页面可见时开启帧刷新，不可见时停止，避免空转。 */
    public void setActive(boolean active) {
        if (mActive == active) return;
        mActive = active;
        if (active) mChoreographer.postFrameCallback(mFrame);
        else mChoreographer.removeFrameCallback(mFrame);
    }

    private void ensureCache(int w, int h) {
        if (mCache != null && mCache.getWidth() >= w && mCache.getHeight() >= h) return;
        if (mCache != null) mCache.recycle();
        mCache = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
        mCacheCanvas = new Canvas(mCache);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        int w = mContent.getWidth();
        int h = mContent.getHeight();
        if (w <= 0 || h <= 0) return;
        int cw = w / SCALE;
        int ch = h / SCALE;
        ensureCache(cw, ch);

        // 采样背后内容：先铺统一渐变底，再绘制内容，缩放到小缓存
        mCacheCanvas.save();
        mCacheCanvas.scale(1f / SCALE, 1f / SCALE);
        if (mBase != null) {
            mBase.setBounds(0, 0, w, h);
            mBase.draw(mCacheCanvas);
        }
        mContent.draw(mCacheCanvas);
        mCacheCanvas.restore();

        // 取导航区域对应部分放大绘制（缩小放大即模糊）
        int srcH = Math.min(mCache.getHeight(), bounds.height() / SCALE + 2);
        Rect src = new Rect(0, mCache.getHeight() - srcH, mCache.getWidth(), mCache.getHeight());
        canvas.drawBitmap(mCache, src, bounds, mPaint);

        // 顶部高光线
        Paint line = new Paint();
        line.setColor(0x2EFFFFFF);
        canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + 1, line);
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
