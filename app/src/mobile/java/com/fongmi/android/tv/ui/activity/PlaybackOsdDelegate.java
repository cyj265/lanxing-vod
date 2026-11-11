package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.view.View;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.utils.Traffic;

import java.util.function.BooleanSupplier;

/**
 * 播放页屏显刷新：缓冲网速、横屏全屏时的网速与电量。
 * <p>
 * 原先两组 Runnable 的启停散落在 showProgress / hideProgress / showControl /
 * hideControl / onDestroy 五处，漏掉任一处都会在控制栏收起或 Activity 销毁后
 * 继续空转。这里收口成四个入口，销毁时由 release 一次性移除，避免再靠人工
 * 记住「onDestroy 里哪几个 Runnable 要删」。
 */
final class PlaybackOsdDelegate {

    private final VideoActivity activity;
    private final ActivityVideoBinding binding;
    private final BooleanSupplier fullscreen;
    private final Runnable trafficTask = this::refreshTraffic;
    private final Runnable osdTask = this::refreshOsd;

    PlaybackOsdDelegate(VideoActivity activity, ActivityVideoBinding binding, BooleanSupplier fullscreen) {
        this.activity = activity;
        this.binding = binding;
        this.fullscreen = fullscreen;
    }

    /** 缓冲开始：立刻起一轮网速采样 */
    void startTraffic() {
        App.post(trafficTask, 0);
    }

    /** 缓冲结束：停采样并清零，避免下一集沿用上一集的速率 */
    void stopTraffic() {
        App.removeCallbacks(trafficTask);
        Traffic.reset();
    }

    private void refreshTraffic() {
        Traffic.setSpeed(binding.progress.traffic);
        App.post(trafficTask, 1000);
    }

    /** 控制栏弹出时调用；只有横屏全屏且控制栏可见才刷新，否则不启动定时任务 */
    void refreshOsd() {
        App.removeCallbacks(osdTask);
        if (!fullscreen.getAsBoolean() || binding.control.getRoot().getVisibility() != View.VISIBLE) return;
        Traffic.setSpeed(binding.control.speed);
        updateBattery();
        App.post(osdTask, 1000);
    }

    /** 控制栏收起：停掉刷新，避免后台空转 */
    void stopOsd() {
        App.removeCallbacks(osdTask);
    }

    void release() {
        App.removeCallbacks(trafficTask, osdTask);
    }

    private void updateBattery() {
        Intent intent = activity.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = intent == null ? -1 : intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent == null ? -1 : intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            binding.control.battery.setVisibility(View.GONE);
            return;
        }
        binding.control.battery.setVisibility(View.VISIBLE);
        binding.control.battery.setLevel(level * 100 / scale);
    }
}
