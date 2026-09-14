package master.flame.danmaku.danmaku.util;

/**
 * Created by ch on 15-12-9.
 */
public class SystemClock {
    /**
     * 使用系统时间类计算流逝时间
     */
    public static boolean useSystemClock = false;

    /**
     * 基础时间
     */
    private static long baseTime = baseUptimeMillis();

    /**
     * 上次最后时间
     */
    private static long lastSystemClockTimeMillis = baseTime;

    public static long uptimeMillis() {
        return calcVideoBaseTime();
    }

    private static long baseUptimeMillis() {
        if (useSystemClock) {
            return android.os.SystemClock.elapsedRealtime();
        }
        return System.currentTimeMillis();
    }

    public static void sleep(long mills) {
        android.os.SystemClock.sleep(mills);
    }

    private static long index = 0;

    /**
     * 根据视频时间计算流逝时间
     * @return 流逝时间
     */
    private static long calcVideoBaseTime() {
        long gap = baseUptimeMillis() - lastSystemClockTimeMillis;
        long a = gap;
//        if (SystemClock.playing && DanmuSystemTimer.getSpeed() != 1.0f) {
//            a = (long) ((gap) * DanmuSystemTimer.getSpeed());
//        }

        long real = baseTime + a;
//        if (DanmakuTimer.debug && baseUptimeMillis() / 1_000 != index) {
//            index = baseUptimeMillis() / 1_000;
//            Log.d("SystemClock", "基础时间=" + baseTime + ", gap=" + gap + " * " + DanmuSystemTimer.getSpeed() + " 计算后gap=" + a + ", 实际=" + real + ", 弹幕时间 " + DanmakuTimer.formatTime(real) + ", 视频时间 " + DanmakuTimer.formatTime(DanmakuTimer.videoTime));
//        }
        return real;
    }

    /**
     * 标记当前播放器状态
     * @param playing 是否播放中
     */
    public static void setPlaying(boolean playing) {
        reset();
    }

    /**
     * 重新记录当前时间为基础时间
     */
    private static void reset() {
        baseTime = calcVideoBaseTime();
        lastSystemClockTimeMillis = baseTime;
    }

    /**
     * 修改视频速度
     */
    public static void setVideoSpeed() {
        reset();
    }
}
