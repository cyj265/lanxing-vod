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

package master.flame.danmaku.controller;

import android.graphics.Canvas;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import master.flame.danmaku.danmaku.model.AbsDisplayer;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.DanmakuContext.ConfigChangedCallback;
import master.flame.danmaku.danmaku.model.android.DanmakuContext.DanmakuConfigTag;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.renderer.IRenderer;
import master.flame.danmaku.danmaku.renderer.IRenderer.RenderingState;
import master.flame.danmaku.danmaku.renderer.android.DanmakuRenderer;
import master.flame.danmaku.danmaku.util.SystemClock;

public class DrawTask implements IDrawTask {

    protected final DanmakuContext mContext;
    
    protected final AbsDisplayer mDisp;

    protected IDanmakus danmakuList;

    protected BaseDanmakuParser mParser;

    TaskListener mTaskListener;

    protected final IRenderer mRenderer;

    protected DanmakuTimer mTimer;

    private final IDanmakus[] danmakusContainer;
    private final TimeOutRemover timeOutRemover;
    private AtomicInteger showIndex;
    private AtomicInteger bufferShowIndex;
    private AtomicBoolean bufferCalculated;

    protected boolean clearRetainerFlag;

    private long mStartRenderTime = 0;

    private final RenderingState mRenderingState = new RenderingState();

    protected boolean mReadyState;

    protected int mPlayState;

    private boolean mIsHidden;

    private Danmakus mLiveDanmakus = new Danmakus(Danmakus.ST_BY_LIST);

    private IDanmakus mRunningDanmakus;

    private boolean mRequestRender;

    private final Handler bufferCalculatorHandler;
    private final AtomicBoolean bufferCalculatorRunning;
    private static final long BUFFER_CALCULATOR_TIME_GAP = 300L;
    private final Runnable bufferCalculator;
    private boolean resetCalculator;
    private final AtomicBoolean drawing;

    private ConfigChangedCallback mConfigChangedCallback = new ConfigChangedCallback() {
        @Override
        public boolean onDanmakuConfigChanged(DanmakuContext config, DanmakuConfigTag tag, Object... values) {
            return DrawTask.this.onDanmakuConfigChanged(config, tag, values);
        }
    };

    public DrawTask(DanmakuTimer timer, DanmakuContext context,
            TaskListener taskListener) {
        if (context == null) {
            throw new IllegalArgumentException("context is null");
        }
        mContext = context;
        mDisp = context.getDisplayer();
        mTaskListener = taskListener;
        mRenderer = new DanmakuRenderer(context);
        mRenderer.setOnDanmakuShownListener(new IRenderer.OnDanmakuShownListener() {

            @Override
            public void onDanmakuShown(BaseDanmaku danmaku) {
                if (mTaskListener != null) {
                    mTaskListener.onDanmakuShown(danmaku);
                }
            }
        });
        mRenderer.setVerifierEnabled(mContext.isPreventOverlappingEnabled() || mContext.isMaxLinesLimited());
        initTimer(timer);
        boolean enable = mContext.isDuplicateMergingEnabled();
        if(enable) {
            mContext.mDanmakuFilters.registerFilter(DanmakuFilters.TAG_DUPLICATE_FILTER);
        } else {
            mContext.mDanmakuFilters.unregisterFilter(DanmakuFilters.TAG_DUPLICATE_FILTER);
        }

        timeOutRemover = new TimeOutRemover();
        bufferCalculatorRunning = new AtomicBoolean(false);
        drawing = new AtomicBoolean(false);
        danmakusContainer = new Danmakus[2];
        danmakusContainerReset();

        bufferCalculatorHandler = new Handler();
        bufferCalculator = new Runnable() {
            @Override
            public void run() {
                // 已经停止 无需执行
                if (!bufferCalculatorRunning.get()) {
                    return;
                }

                if (mReadyState) {
                    try {
                        autoCalcNeedShowDanmakus(timer.getCurrMillisecond());
                    } catch (Exception e) {
                        Log.e("autoCalc", "计算缓存失败, 跳过本次计算 , e=", e);
                    }
                }
                bufferCalculatorHandler.postDelayed(this, BUFFER_CALCULATOR_TIME_GAP);
            }
        };
    }

    private void danmakusContainerReset() {
        for (int i = 0; i < danmakusContainer.length; i++) {
            danmakusContainer[i] = new Danmakus(Danmakus.ST_BY_LIST, false, null, false);
        }
        showIndex = new AtomicInteger(0);
        bufferShowIndex = new AtomicInteger(0);
        bufferCalculated = new AtomicBoolean(false);
        bufferCalculateStop();
    }

    protected void bufferCalculateStart() {
        if (bufferCalculatorRunning.compareAndSet(false, true)) {
            bufferCalculatorHandler.post(bufferCalculator);
        }
    }
    protected void bufferCalculateStop() {
        if (bufferCalculatorRunning.compareAndSet(true, false)) {
            bufferCalculatorHandler.removeCallbacks(bufferCalculator);
        }
    }

    protected void initTimer(DanmakuTimer timer) {
        mTimer = timer;
    }

    @Override
    public synchronized void addDanmaku(BaseDanmaku item) {
        if (danmakuList == null)
            return;
        if (item.isLive) {
            mLiveDanmakus.addItem(item);
            removeUnusedLiveDanmakusIn(10);
        }
        item.index = danmakuList.size();
        boolean added;
        synchronized (danmakuList) {
            added = danmakuList.addItem(item);
        }
        if (added && mTaskListener != null) {
            mTaskListener.onDanmakuAdd(item);
        }
    }

    @Override
    public void invalidateDanmaku(BaseDanmaku item, boolean remeasure) {
        mContext.getDisplayer().getCacheStuffer().clearCache(item);
        item.requestFlags |= BaseDanmaku.FLAG_REQUEST_INVALIDATE;
        if (remeasure) {
            item.paintWidth = -1;
            item.paintHeight = -1;
            item.requestFlags |= BaseDanmaku.FLAG_REQUEST_REMEASURE;
            item.measureResetFlag++;
        }
    }

    @Override
    public synchronized void removeAllDanmakus(boolean isClearDanmakusOnScreen) {
        if (danmakuList == null || danmakuList.isEmpty())
            return;
        synchronized (danmakuList) {
            // 清除屏幕弹幕
            if (isClearDanmakusOnScreen) {
                danmakusContainerReset();
            }
            danmakuList.clear();
        }
    }

    protected void onDanmakuRemoved(BaseDanmaku danmaku) {
        // override by CacheManagingDrawTask
    }

    @Override
    public synchronized void removeAllLiveDanmakus() {
        if (mLiveDanmakus == null || mLiveDanmakus.isEmpty())
            return;
        synchronized (mLiveDanmakus) {
            mLiveDanmakus.forEachSync(new IDanmakus.DefaultConsumer<BaseDanmaku>() {
                @Override
                public int accept(BaseDanmaku danmaku) {
                    if (danmaku.isLive) {
                        onDanmakuRemoved(danmaku);
                        return ACTION_REMOVE;
                    }
                    return ACTION_CONTINUE;
                }
            });
        }
    }

    protected synchronized void removeUnusedLiveDanmakusIn(final int msec) {
        if (danmakuList == null || danmakuList.isEmpty() || mLiveDanmakus.isEmpty())
            return;
        mLiveDanmakus.forEachSync(new IDanmakus.DefaultConsumer<BaseDanmaku>() {
            long startTime = SystemClock.uptimeMillis();

            @Override
            public int accept(BaseDanmaku danmaku) {
                boolean isTimeout = danmaku.isTimeOut();
                if (SystemClock.uptimeMillis() - startTime > msec) {
                    return ACTION_BREAK;
                }
                if (isTimeout) {
                    danmakuList.removeItem(danmaku);
                    onDanmakuRemoved(danmaku);
                    return ACTION_REMOVE;
                } else {
                    return ACTION_BREAK;
                }

            }
        });
    }

    @Override
    public IDanmakus getVisibleDanmakusOnTime(long time) {
        IDanmakus currentShowDanmakus = danmakusContainer[showIndex.get() % danmakusContainer.length];
        final IDanmakus visibleDanmakus = new Danmakus();
        if (null != currentShowDanmakus && !currentShowDanmakus.isEmpty()) {
            currentShowDanmakus.forEachSync(new IDanmakus.DefaultConsumer<BaseDanmaku>() {
                @Override
                public int accept(BaseDanmaku danmaku) {
                    if (danmaku.isShown() && !danmaku.isOutside()) {
                        visibleDanmakus.addItem(danmaku);
                    }
                    return ACTION_CONTINUE;
                }
            });
        }

        return visibleDanmakus;
    }

    @Override
    public RenderingState draw(AbsDisplayer displayer) {
        RenderingState renderingState = mRenderingState;
        // 保证单任务进入执行
        if (drawing.compareAndSet(false, true)) {
            try {
                renderingState = drawDanmakus(displayer, mTimer);
            } finally {
                drawing.compareAndSet(true, false);
            }
        }
        return renderingState;
    }

    @Override
    public void reset() {
        danmakusContainerReset();
        if (mRenderer != null)
            mRenderer.clear();
    }

    @Override
    public void seek(long mills) {
        reset();
        mContext.mGlobalFlagValues.updateVisibleFlag();
        mContext.mGlobalFlagValues.updateFirstShownFlag();
        mContext.mGlobalFlagValues.updateSyncOffsetTimeFlag();
        mContext.mGlobalFlagValues.updatePrepareFlag();
        mRunningDanmakus = new Danmakus(Danmakus.ST_BY_LIST);
        mStartRenderTime = mills < 1000 ? 0 : mills;
        mRenderingState.reset();
        mRenderingState.endTime = mStartRenderTime;

        // 直接更新一次
        DanmakuTimer.videoTime = mStartRenderTime;
        resetCalculator = true;
    }

    @Override
    public void clearDanmakusOnScreen(long currMillis) {
        reset();
        mContext.mGlobalFlagValues.updateVisibleFlag();
        mContext.mGlobalFlagValues.updateFirstShownFlag();
        mStartRenderTime = currMillis;
    }

    @Override
    public void start() {
        mContext.registerConfigChangedCallback(mConfigChangedCallback);
        bufferCalculateStart();
    }

    @Override
    public void quit() {
        mContext.unregisterAllConfigChangedCallbacks();
        if (mRenderer != null)
            mRenderer.release();
        bufferCalculateStop();
    }

    public void prepare() {
        if (mParser == null) {
            return;
        }
        loadDanmakus(mParser);
        if (mTaskListener != null) {
            mTaskListener.ready();
            mReadyState = true;
        }
    }

    @Override
    public void onPlayStateChanged(int state) {
        mPlayState = state;
        if (state == IDrawTask.PLAY_STATE_PAUSE) {
            bufferCalculateStop();
        } else if (state == IDrawTask.PLAY_STATE_PLAYING) {
            bufferCalculateStart();
        }
    }

    @Override
    public int getPlayState() {
        return mPlayState;
    }

    protected void loadDanmakus(BaseDanmakuParser parser) {
        danmakuList = parser.setConfig(mContext).setDisplayer(mDisp).setTimer(mTimer).setListener(new BaseDanmakuParser.Listener() {
            @Override
            public void onDanmakuAdd(BaseDanmaku danmaku) {
                if (mTaskListener != null) {
                    mTaskListener.onDanmakuAdd(danmaku);
                }
            }
        }).getDanmakus();
        mContext.mGlobalFlagValues.resetAll();
    }

    /**
     * 计算需要显示弹幕数据
     * @param currentTime 当前时间
     */
    protected void autoCalcNeedShowDanmakus(long currentTime) {
//        long startTime = System.currentTimeMillis();
        // prepare screenDanmakus
        long beginMills = currentTime - mContext.mDanmakuFactory.MAX_DANMAKU_DURATION - 100;
        long endMills = currentTime + mContext.mDanmakuFactory.MAX_DANMAKU_DURATION;

        // 获取当前显示中的弹幕列表
        int currentShowIndex = this.showIndex.get();
        IDanmakus currentShowDanmakus = danmakusContainer[currentShowIndex % danmakusContainer.length];
        // 只捞取当前最后一个弹幕之后的时间
        if (!resetCalculator) {
            BaseDanmaku last = currentShowDanmakus.last();
            if (last != null) {
                beginMills = Math.max(beginMills, last.getActualTime() + 1);
            }
        }

        if (beginMills > endMills) {
            return;
        }

        // 缓存区未被使用 是否需要重新计算
//        if (bufferCalculated.get()) {
//
//        }

        // 捞取新的需要展示的弹幕
        IDanmakus newNeedAddItems = danmakuList.sub(beginMills, endMills);
        if (newNeedAddItems == null || newNeedAddItems.isEmpty()) {
            return;
        }

        // 下次要显示的容器
        int calcNextShowIndex = currentShowIndex + 1;
        IDanmakus nextNeedShowDanmakus = danmakusContainer[calcNextShowIndex % danmakusContainer.length];

        /*
         * 1. 清空原容器
         * 2. 添加上次展示的弹幕 // 如果不需要重置数据的话就添加之前显示过的，否则不添加历史数据
         * 3. 删除过期的数据
         * 4. 加入本次新增的数据
         */
        nextNeedShowDanmakus.clear();
        if (resetCalculator) {
            resetCalculator = false;
        } else {
            nextNeedShowDanmakus.addAllItem(currentShowDanmakus.getCollection());
        }
        nextNeedShowDanmakus.forEach(timeOutRemover);
        nextNeedShowDanmakus.addAllItem(newNeedAddItems.getCollection());

//        BaseDanmaku nextFirst = nextNeedShowDanmakus.first();
//        BaseDanmaku nextLast = nextNeedShowDanmakus.last();
//        Log.d("autoCalc", "缓冲区计算完成 耗时=" + (System.currentTimeMillis() - startTime) + ", 计算前数量=" + currentShowDanmakus.size() + ", 计算后数量=" + nextNeedShowDanmakus.size() + ", 计算时间=" + DanmakuTimer.formatTime(currentTime) + ", 下次时间范围[" + (nextFirst == null ? "无" : DanmakuTimer.formatTime(nextFirst.getActualTime())) + "," + (nextLast == null ? "无" : DanmakuTimer.formatTime(nextLast.getActualTime())) + "]");
        // 标记缓存计算完成
        if (bufferCalculated.compareAndSet(false, true)) {
            // 标记需要展示的容器为新容器
            bufferShowIndex.set(calcNextShowIndex);
        }

    }

    private static class TimeOutRemover extends IDanmakus.Consumer<BaseDanmaku, Object>{
        @Override
        public int accept(BaseDanmaku t) {
            if (t.isTimeOut()) {
                return IDanmakus.DefaultConsumer.ACTION_REMOVE;
            }

            return IDanmakus.DefaultConsumer.ACTION_CONTINUE;
        }
    }

    public void setParser(BaseDanmakuParser parser) {
        mParser = parser;
        mReadyState = false;
    }

    protected RenderingState drawDanmakus(AbsDisplayer disp, DanmakuTimer timer) {
        if (clearRetainerFlag) {
            mRenderer.clearRetainer();
            clearRetainerFlag = false;
        }

        if (danmakuList == null) {
            return null;
        }

        long start = System.currentTimeMillis();
        Canvas canvas = (Canvas) disp.getExtraData();
        DrawHelper.clearCanvas(canvas);
        if (mIsHidden && !mRequestRender) {
            return mRenderingState;
        }

        mRequestRender = false;
        RenderingState renderingState = mRenderingState;

        int showIndex = this.showIndex.get();
        int needShowIndex = this.bufferShowIndex.get();
        // 缓存区已经准备完成，标记缓存区已被使用
        if (showIndex != needShowIndex && bufferCalculated.compareAndSet(true, false)) {
            this.showIndex.compareAndSet(showIndex, needShowIndex);
            showIndex = this.showIndex.get();
        }

        IDanmakus screenDanmakus = danmakusContainer[showIndex % danmakusContainer.length];
        // prepare runningDanmakus to draw (in sync-mode)
        IDanmakus runningDanmakus = mRunningDanmakus;
        beginTracing(renderingState, runningDanmakus, screenDanmakus);
        if (runningDanmakus != null && !runningDanmakus.isEmpty()) {
            mRenderingState.isRunningDanmakus = true;
            mRenderer.draw(disp, runningDanmakus, 0, mRenderingState);
        }

        // draw screenDanmakus
        mRenderingState.isRunningDanmakus = false;
        if (screenDanmakus != null && !screenDanmakus.isEmpty()) {
            mRenderer.draw(mDisp, screenDanmakus, mStartRenderTime, renderingState);
            endTracing(renderingState);
            if (renderingState.nothingRendered) {
                BaseDanmaku first = screenDanmakus.first();
                BaseDanmaku last = screenDanmakus.last();
                if(last != null && last.isTimeOut()) {
                    if (mTaskListener != null) {
                        mTaskListener.onDanmakusDrawingFinished();
                    }
                }
                if (renderingState.beginTime == RenderingState.UNKNOWN_TIME) {
                    renderingState.beginTime = first == null ? 0 : first.getActualTime();
                }
                if (renderingState.endTime == RenderingState.UNKNOWN_TIME) {
                    renderingState.endTime = last == null ? 0 : last.getActualTime();;
                }
            }

//            Log.d("drawDanmakus", "渲染完成 数量=" + screenDanmakus.size() + ", 耗时=" + (System.currentTimeMillis() - start) + "ms");
        } else {
            renderingState.nothingRendered = true;
        }
        return renderingState;
    }

    @Override
    public void requestClear() {
        mIsHidden = false;
    }

    @Override
    public void requestClearRetainer() {
        clearRetainerFlag = true;
    }

    @Override
    public void requestSync(long fromTimeMills, long toTimeMills, final long offsetMills) {
        // obtain the running-danmakus which was drawn on screen
        IDanmakus runningDanmakus = mRenderingState.obtainRunningDanmakus();
        mRunningDanmakus = runningDanmakus;
        // set offset time for each running-danmakus
        runningDanmakus.forEachSync(new IDanmakus.DefaultConsumer<BaseDanmaku>() {
            @Override
            public int accept(BaseDanmaku danmaku) {
                if (danmaku.isOutside()) {
                    return ACTION_REMOVE;
                }
                danmaku.setTimeOffset(offsetMills + danmaku.timeOffset);
                if (danmaku.timeOffset == 0) {
                    return ACTION_REMOVE;
                }
                return ACTION_CONTINUE;
            }
        });
        mStartRenderTime = toTimeMills;
    }

    public boolean onDanmakuConfigChanged(DanmakuContext config, DanmakuConfigTag tag,
            Object... values) {
        boolean handled = handleOnDanmakuConfigChanged(config, tag, values);
        if (mTaskListener != null) {
            mTaskListener.onDanmakuConfigChanged();
        }
        return handled;
    }

    protected boolean handleOnDanmakuConfigChanged(DanmakuContext config, DanmakuConfigTag tag, Object[] values) {
        boolean handled = false;
        if (tag == null || DanmakuConfigTag.MAXIMUM_NUMS_IN_SCREEN.equals(tag)) {
            handled = true;
        } else if (DanmakuConfigTag.DUPLICATE_MERGING_ENABLED.equals(tag)) {
            Boolean enable = (Boolean) values[0];
            if (enable != null) {
                if (enable) {
                    mContext.mDanmakuFilters.registerFilter(DanmakuFilters.TAG_DUPLICATE_FILTER);
                } else {
                    mContext.mDanmakuFilters.unregisterFilter(DanmakuFilters.TAG_DUPLICATE_FILTER);
                }
                handled = true;
            }
        } else if (DanmakuConfigTag.SCALE_TEXTSIZE.equals(tag) || DanmakuConfigTag.SCROLL_SPEED_FACTOR.equals(tag) || DanmakuConfigTag.DANMAKU_MARGIN.equals(tag)) {
            requestClearRetainer();
            handled = false;
        } else if (DanmakuConfigTag.MAXIMUN_LINES.equals(tag) || DanmakuConfigTag.OVERLAPPING_ENABLE.equals(tag)) {
            if (mRenderer != null) {
                mRenderer.setVerifierEnabled(mContext.isPreventOverlappingEnabled() || mContext.isMaxLinesLimited());
            }
            handled = true;
        } else if (DanmakuConfigTag.ALIGN_BOTTOM.equals(tag)) {
            Boolean enable = (Boolean) values[0];
            if (enable != null) {
                if (mRenderer != null) {
                    mRenderer.alignBottom(enable);
                }
                handled = true;
            }
        }
        return handled;
    }

    @Override
    public void requestHide() {
        mIsHidden = true;
    }

    @Override
    public void requestRender() {
        this.mRequestRender = true;
    }

    private void beginTracing(RenderingState renderingState, IDanmakus runningDanmakus, IDanmakus screenDanmakus) {
        renderingState.reset();
        renderingState.timer.update(SystemClock.uptimeMillis());
        renderingState.indexInScreen = 0;
        renderingState.totalSizeInScreen = (runningDanmakus != null ? runningDanmakus.size() : 0) + (screenDanmakus != null ? screenDanmakus.size() : 0);
    }

    private void endTracing(RenderingState renderingState) {
        renderingState.nothingRendered = (renderingState.totalDanmakuCount == 0);
        if (renderingState.nothingRendered) {
            renderingState.beginTime = RenderingState.UNKNOWN_TIME;
        }
        BaseDanmaku lastDanmaku = renderingState.lastDanmaku;
        renderingState.lastDanmaku = null;
        renderingState.endTime = lastDanmaku != null ? lastDanmaku.getActualTime() : RenderingState.UNKNOWN_TIME;
        renderingState.consumingTime = renderingState.timer.update(SystemClock.uptimeMillis());
    }
}
