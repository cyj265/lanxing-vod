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

package master.flame.danmaku.danmaku.model;


public class L2RDanmaku extends R2LDanmaku {

    public L2RDanmaku(Duration duration) {
        super(duration); 
    }

    @Override
    public float[] getRectAtTime(IDisplayer displayer, long time, long tempBaseTime) {
        if (!isMeasured())
            return null;
        float left = getAccurateLeft(displayer, time, tempBaseTime);
        if (RECT == null) {
            RECT = new float[4];
        }
        RECT[0] = left;
        RECT[1] = y;
        RECT[2] = left + paintWidth;
        RECT[3] = y + paintHeight;
        return RECT;
    }

    protected float getAccurateLeft(IDisplayer displayer, long currTime, long tempBaseTime) {
        long actualBaseTime = getActualTime();
        if (tempBaseTime > 0) {
            actualBaseTime = tempBaseTime;
        }

        long elapsedTime = currTime - actualBaseTime;
        if (elapsedTime >= duration.value) {
            return displayer.getWidth();
        }
        return mStepX * elapsedTime - paintWidth;
    }

    @Override
    public int getType() {
        return TYPE_SCROLL_LR;
    }

    @Override
    public void measure(IDisplayer displayer, boolean fromWorkerThread) {
        super.measure(displayer, fromWorkerThread);
        x = -paintWidth;
    }
}
