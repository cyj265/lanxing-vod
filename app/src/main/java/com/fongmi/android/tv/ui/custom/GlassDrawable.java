package com.fongmi.android.tv.ui.custom;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
    private static final float BLUR_RADIUS_DP = 52f;

    /**
     * 玻璃背景背后的内容变化并不频繁，没必要跟着 60fps 重绘整棵内容树。
     * 这里节流到 8fps（125ms 一帧），观感足够，开销约为原来的 1/7。
     */
    private static final long FRAME_INTERVAL_NS = 125_000_000L;

    private final View mContent;
    private final Paint mPaint;
    private final Paint mGlassPaint;
    private final Paint mLinePaint;
    private final Path mClipPath;
    private final RectF mClipRectF;
    private final RectF mGlassRectF;
    private final RectF mHighlightRectF;
    private final RectF mStrokeRectF;
    private final int[] mContentLoc;
    private final int[] mSelfLoc;
    private final Choreographer mChoreographer;
    private final Choreographer.FrameCallback mFrame;
    private final float mRadiusPx;
    private final boolean mV31;

    private Bitmap mCache;
    private Canvas mCacheCanvas;
    private RenderNode mNode;
    private RenderEffect mBlur;
    private boolean mActive;
    private long mLastFrameTimeNs;

    public GlassDrawable(View content) {
        mContent = content;
        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        mGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGlassPaint.setColor(0xA6121212);
        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mClipPath = new Path();
        mClipRectF = new RectF();
        mGlassRectF = new RectF();
        mHighlightRectF = new RectF();
        mStrokeRectF = new RectF();
        mContentLoc = new int[2];
        mSelfLoc = new int[2];
        mRadiusPx = dp(BLUR_RADIUS_DP);
        mV31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        if (mV31) initV31();
        mChoreographer = Choreographer.getInstance();
        mFrame = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!mActive) return;
                if (frameTimeNanos - mLastFrameTimeNs >= FRAME_INTERVAL_NS) {
                    mLastFrameTimeNs = frameTimeNanos;
                    invalidateSelf();
                }
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
        if (active) {
            mLastFrameTimeNs = 0;
            mChoreographer.postFrameCallback(mFrame);
        } else {
            mChoreographer.removeFrameCallback(mFrame);
        }
    }

    /** 页面销毁时调用，释放离屏缓存。 */
    public void release() {
        setActive(false);
        if (mCache != null) {
            mCache.recycle();
            mCache = null;
            mCacheCanvas = null;
        }
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

    /** 取本 Drawable 所依附的 View：作为背景时 Callback 就是宿主 View。 */
    private View getHost() {
        Callback callback = getCallback();
        return callback instanceof View ? (View) callback : null;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        if (mContent.getWidth() <= 0 || mContent.getHeight() <= 0) return;

        canvas.save();
        mClipRectF.set(b.left, b.top, b.right, b.bottom);
        mClipPath.rewind();
        mClipPath.addRoundRect(mClipRectF, mRadiusPx, mRadiusPx, Path.Direction.CW);
        canvas.clipPath(mClipPath);
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
        // Drawable 的 bounds 是宿主 View 的局部坐标（通常是 0,0,w,h），
        // 直接 translate(-b.left, -b.top) 等于没平移，采到的是内容区左上角而不是底栏背后的内容。
        // 必须用窗口坐标算出「底栏相对内容容器」的真实偏移。
        int offsetX = 0;
        int offsetY = 0;
        View host = getHost();
        if (host != null) {
            mContent.getLocationInWindow(mContentLoc);
            host.getLocationInWindow(mSelfLoc);
            offsetX = mSelfLoc[0] - mContentLoc[0];
            offsetY = mSelfLoc[1] - mContentLoc[1];
        }

        RecordingCanvas rc = mNode.beginRecording(b.width(), b.height());
        try {
            rc.save();
            rc.translate(-offsetX, -offsetY);
            mContent.draw(rc);
            rc.restore();
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
        // 复用缓存前必须擦除，否则半透明内容会逐帧叠加出残影
        mCache.eraseColor(Color.TRANSPARENT);

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
        mGlassRectF.set(b.left, b.top, b.right, b.bottom);
        canvas.drawRoundRect(mGlassRectF, mRadiusPx, mRadiusPx, mGlassPaint);

        mHighlightRectF.set(b.left + dp(4), b.top + dp(1), b.right - dp(4), b.top + dp(3));
        mLinePaint.setStyle(Paint.Style.FILL);
        mLinePaint.setColor(0x55FFFFFF);
        canvas.drawRoundRect(mHighlightRectF, dp(1.5f), dp(1.5f), mLinePaint);

        mStrokeRectF.set(b.left + dp(0.5f), b.top + dp(0.5f), b.right - dp(0.5f), b.bottom - dp(0.5f));
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(dp(1));
        mLinePaint.setColor(0x33FFFFFF);
        canvas.drawRoundRect(mStrokeRectF, mRadiusPx, mRadiusPx, mLinePaint);
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
