package master.flame.danmaku.danmaku.util;

import android.util.Log;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.DanmakuTimer;

/**
 * 支持使用视频大致时间，开始渲染弹幕后使用该类获取相对时间
 *
 * 弹幕系统相对时间(平滑)
 */
public class DanmuSystemTimer {
    /**
     * 弹幕系统真实时间基础值
     */
    private static long danmuRealBaseTime;

    /**
     * 弹幕系统重置计算时间点
     */
    private static long danmuBaseTime;
    private static volatile float speed = 1.0f;
    private static int status = DrawHandler.PREPARE;


    /**
     * 获取弹幕系统真实时间
     * @return 真实时间
     */
    public static long getDanmuRealTime() {
        // 暂停返回记录时间
        if (status == DrawHandler.PAUSE) {
            return danmuRealBaseTime;
        }

        long newDanmuBaseTime = SystemClock.uptimeMillis();
        long timeGap = newDanmuBaseTime - danmuBaseTime;
        return (long) (danmuRealBaseTime + timeGap * speed);
    }

    /**
     * 获取当前速度
     * @return 当前速度
     */
    public static float getSpeed() {
        return speed;
    }

    /**
     * 修改弹幕系统速度
     * @param newSpeed 新速度
     */
    public static void changeSpeed(float newSpeed) {
        if (speed == newSpeed) {
            return;
        }

//        String originDanmuRealBaseTimeStr = DanmakuTimer.formatTime(danmuRealBaseTime);
//        long originDanmuBaseTime = danmuBaseTime;

        resetBaseTime(false, 0);
        SystemClock.setVideoSpeed();
//        Log.d("DanmuSystemTimer", "弹幕系统时间统计 newSpeed=" + newSpeed + ", originDanmuRealBaseTime=" + originDanmuRealBaseTimeStr + ", originDanmuBaseTime=" + originDanmuBaseTime + ", DanmuRealBaseTime=" + DanmakuTimer.formatTime(danmuRealBaseTime) + ", danmuBaseTime=" + danmuBaseTime + ", speed=" + speed);
        speed = newSpeed;
    }

    /**
     * 弹幕系统时间
     * @param newStatus 弹幕系统状态
     * @param time 弹幕系统绝对时间
     */
    public synchronized static void changeStatus(int newStatus, long time) {
//        String originDanmuRealBaseTimeStr = DanmakuTimer.formatTime(danmuRealBaseTime);
//        long originDanmuBaseTime = danmuBaseTime;

        switch (newStatus) {
            case DrawHandler.PAUSE:
                // 1. 暂停
                resetBaseTime(false, 0);
                SystemClock.setPlaying(false);
                break;
            case DrawHandler.RESUME:
                // 2. 恢复
                resetBaseTime(true, danmuRealBaseTime);
                SystemClock.setPlaying(true);
                break;
            case DrawHandler.PREPARE:
                // 3. 新启动
                resetBaseTime(true, 0);
                break;
            case DrawHandler.SEEK_POS:
                // 4. 跳转
                resetBaseTime(true, time);
                break;
        }

//        Log.d("DanmuSystemTimer", "弹幕系统时间统计 status=" + newStatus + ", originDanmuRealBaseTime=" + originDanmuRealBaseTimeStr + ", originDanmuBaseTime=" + originDanmuBaseTime + ", DanmuRealBaseTime=" + DanmakuTimer.formatTime(danmuRealBaseTime) + ", danmuBaseTime=" + danmuBaseTime + ", speed=" + speed);
        status = newStatus;
    }

    /**
     * 重置当前弹幕系统基础时间
     */
    private static void resetBaseTime(boolean reset, long currentTime) {
        long newDanmuBaseTime = SystemClock.uptimeMillis();
        if (reset) {
            danmuRealBaseTime = currentTime;
        } else {
            long timeGap = newDanmuBaseTime - danmuBaseTime;
            danmuRealBaseTime = (long) (danmuRealBaseTime + timeGap * speed);
        }
        danmuBaseTime = newDanmuBaseTime;
    }


//    static {
//        new Thread(() -> {
//            while (true) {
//                Log.i("DanmuSystemTimer", "时间输出 realTime=" + DanmakuTimer.formatTime(DanmuSystemTimer.getDanmuRealTime()) + ", speed=" + DanmuSystemTimer.getSpeed() + ", status=" + DanmuSystemTimer.status);
//                try {
//                    Thread.sleep(1000L);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }).start();
//    }
}
