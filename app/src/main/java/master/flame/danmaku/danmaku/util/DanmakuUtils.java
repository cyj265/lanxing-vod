/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package master.flame.danmaku.danmaku.util;

import android.text.TextUtils;
import android.util.Log;

import master.flame.danmaku.danmaku.model.AbsDisplayer;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.IDrawingCache;
import master.flame.danmaku.danmaku.model.R2LDanmaku;
import master.flame.danmaku.danmaku.model.android.DrawingCache;
import master.flame.danmaku.danmaku.model.android.DrawingCacheHolder;

public class DanmakuUtils {

    /**
     * 检测两个弹幕是否会碰撞
     * 允许不同类型弹幕的碰撞
     * @param d1
     * @param d2
     * @return
     */
    public static boolean willHitInDuration(IDisplayer disp, BaseDanmaku d1, BaseDanmaku d2,
            long duration, long currTime) {
//        Log.d("DanmakuUtils", "冲突判定 对比 d1=" + d1.toString() + ", outTimeout=" + d1.isTimeOut() + ", d2=" + d2.toString() + ", outTimeout=" + d2.isTimeOut());
        final int type1 = d1.getType();
        final int type2 = d2.getType();
        // allow hit if different type
        if(type1 != type2)
            return false;

        // 允许还未出现的弹幕
//        if(d1.isOutside()){
//            return false;
//        }
        long dTime = d2.getActualTime() - d1.getActualTime();
        if (dTime <= 0)
            return true;
        if (Math.abs(dTime) >= duration || d1.isTimeOut() || d2.isTimeOut()) {
            return false;
        }

        if (type1 == BaseDanmaku.TYPE_FIX_TOP || type1 == BaseDanmaku.TYPE_FIX_BOTTOM) {
            return true;
        }

        // d1已经开始显示，计算剩余显示时间
        if (d1.isDrawn()) {
            duration = duration - (currTime - d1.getFirstShowTime());
        }
//        boolean currentHit = checkHitAtTime(disp, d1, d2, 0, currTime);
//        boolean afterHit = checkHitAtTime(disp, d1, d2, duration, currTime);
//
//        Log.d("DanmakuUtils", "冲突判定 当前=" + currentHit + ", afterHit=" + afterHit + ", text1=" + d1.text + ", text2=" + d2.text);
        return checkHitAtTime(disp, d1, d2, 0, currTime) || checkHitAtTime(disp, d1, d2, duration, currTime);
    }

    private static boolean checkHitAtTime(IDisplayer disp, BaseDanmaku d1, BaseDanmaku d2, long durationTime, long tempBaseTime) {
        if (d1.getType() != d2.getType()) {
            return false;
        }

        // 使用当前时间为d1的时间， d2 为当前时间再减去 d2-d1的时间
        long realTime = DanmuSystemTimer.getDanmuRealTime() + durationTime;
        final float[] rectArr1 = d1.getRectAtTime(disp, realTime, d1.isDrawn() ? d1.getFirstShowTime() : tempBaseTime);
        final float[] rectArr2 = d2.getRectAtTime(disp, realTime, d2.isDrawn() ? d2.getFirstShowTime() : tempBaseTime);
        if (rectArr1 == null || rectArr2 == null)
            return false;

        boolean checkHit = checkHit(d1.getType(), d2.getType(), rectArr1, rectArr2);
//        Log.d("DanmakuUtils", "冲突判定1 结果=" + checkHit + ", 时间=" + durationTime + ", left1=" + rectArr1[0] + ", right1=" + rectArr1[2] + ", left2=" + rectArr2[0] + ", step1=" + ((R2LDanmaku) d1).mStepX + ", step2=" + ((R2LDanmaku) d2).mStepX + ", text1=" + d1.text + ", text2=" + d2.text);
        return checkHit;
    }
    
    private static boolean checkHit(int type1, int type2, float[] rectArr1,
            float[] rectArr2) {
        if (type1 == BaseDanmaku.TYPE_SCROLL_RL) {
            // hit if left2 < right1
            return rectArr2[0] < rectArr1[2];
        }
        
        if (type1 == BaseDanmaku.TYPE_SCROLL_LR){
            // hit if right2 > left1
            return rectArr2[2] > rectArr1[0];
        }
        
        return false;
    }

    public static DrawingCache buildDanmakuDrawingCache(BaseDanmaku danmaku, IDisplayer disp,
            DrawingCache cache, int bitsPerPixel) {
        if (cache == null)
            cache = new DrawingCache();

        cache.build((int) Math.ceil(danmaku.paintWidth), (int) Math.ceil(danmaku.paintHeight), disp.getDensityDpi(), false, bitsPerPixel);
        DrawingCacheHolder holder = cache.get();
        if (holder != null) {
            ((AbsDisplayer) disp).drawDanmaku(danmaku, holder.canvas, 0, 0, true);
            if(disp.isHardwareAccelerated()) {
                holder.splitWith(disp.getWidth(), disp.getHeight(), disp.getMaximumCacheWidth(),
                        disp.getMaximumCacheHeight());
            }
        }
        return cache;
    }

    public static boolean isCacheOk(BaseDanmaku danmaku) {
        if (danmaku == null) {
            return false;
        }
        IDrawingCache<?> cache = danmaku.cache;
        if (cache == null) {
            return false;
        }
        DrawingCacheHolder holder = (DrawingCacheHolder) cache.get();
        if (holder == null) {
            return false;
        }
        if (holder.bitmap == null || holder.bitmap.isRecycled()) {
            return false;
        }
        return true;
    }

    public static int getCacheSize(int w, int h, int bytesPerPixel) {
        return (w) * (h) * bytesPerPixel;
    }
    
    public final static boolean isDuplicate(BaseDanmaku obj1, BaseDanmaku obj2) {
        if(obj1 == obj2) {
            return false;
        }
//        if(obj1.isTimeOut() || obj2.isTimeOut()) {
//            return false;
//        }
//        long dtime = Math.abs(obj1.time - obj2.time);
//        if(dtime > obj1.getDuration()) {
//            return false;
//        }
        if (obj1.text == obj2.text) {
            return true;
        }
        if (obj1.text != null && obj1.text.equals(obj2.text)) {
            return true;
        }
        return false;
    }

    public final static int compare(BaseDanmaku obj1, BaseDanmaku obj2) {
        
        if (obj1 == obj2) {
            return 0;
        }

        if (obj1 == null) {
            return -1;
        }

        if (obj2 == null) {
            return 1;
        }

        long val = obj1.getTime() - obj2.getTime();
        if (val > 0) {
            return 1;
        } else if (val < 0) {
            return -1;
        }
        int r = obj1.index - obj2.index;
        if (r != 0)
            return r < 0 ? -1 : 1;

        r = obj1.hashCode() - obj1.hashCode();
        return r;
    }

    public final static boolean isOverSize(IDisplayer disp, BaseDanmaku item) {
        return disp.isHardwareAccelerated() && (item.paintWidth > disp.getMaximumCacheWidth() || item.paintHeight > disp.getMaximumCacheHeight());
    }

    public static void fillText(BaseDanmaku danmaku, CharSequence text) {
        danmaku.text = text;
        if (TextUtils.isEmpty(text)) {
            return;
        }
        // 调试显示弹幕时间
        if (DanmakuTimer.debug) {
            danmaku.text = DanmakuTimer.formatTime(danmaku.getActualTime()) + " " + danmaku.text;
        }

        if (!text.toString().contains(BaseDanmaku.DANMAKU_BR_CHAR)) {
            return;
        }

        String[] lines = String.valueOf(danmaku.text).split(BaseDanmaku.DANMAKU_BR_CHAR, -1);
        if (lines.length > 1) {
            danmaku.lines = lines;
        }
    }
}
