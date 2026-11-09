package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 播放页电量屏显：手机状态栏样式的电池图标，替代原来的「88%」纯文字。
 * 只画图标不画数字，外框 + 正极帽 + 按电量比例填充，低于 20% 转红。
 */
public class BatteryView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int level = -1;

    public BatteryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setLevel(int percent) {
        int next = Math.max(0, Math.min(100, percent));
        if (next == level) return;
        level = next;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float d = getResources().getDisplayMetrics().density;
        setMeasuredDimension(Math.round(24 * d), Math.round(12 * d));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float bodyW = 20 * d;
        float bodyH = 10 * d;
        float capW = 2 * d;
        float capH = 4 * d;
        float pad = 1.8f * d;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * d);
        paint.setColor(Color.argb(190, 255, 255, 255));
        rect.set(0, 0, bodyW, bodyH);
        canvas.drawRoundRect(rect, 1.5f * d, 1.5f * d, paint);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(bodyW, (bodyH - capH) / 2f, bodyW + capW, (bodyH + capH) / 2f, paint);

        paint.setColor(level <= 20 ? Color.rgb(0xE5, 0x53, 0x53) : Color.argb(225, 255, 255, 255));
        float innerW = (bodyW - pad * 2) * Math.max(0, level) / 100f;
        if (innerW > 0) canvas.drawRect(pad, pad, pad + innerW, bodyH - pad, paint);
    }
}
