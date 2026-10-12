package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.tv.R;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 带当前时间/总时长文本的进度条。
 * 与 media3 的 PlayerSeekView（纯进度条、无时间文本）不同，本控件左侧显示已播放时间、
 * 右侧显示总时长，拖动时实时更新当前时间文本。
 * API 与 PlayerSeekView 对齐：setPlayer(Player)/getTimeBar()，供 PlaybackActivity 统一绑定。
 */
public class CustomSeekView extends FrameLayout implements TimeBar.OnScrubListener {

    private static final int MAX_UPDATE_INTERVAL_MS = 1000;
    private static final int MIN_UPDATE_INTERVAL_MS = 200;

    private final TextView positionView;
    private final TextView durationView;
    private final DefaultTimeBar timeBar;
    private final Runnable runnable;

    private long currentDuration;
    private long currentPosition;
    private long currentBuffered;
    private boolean scrubbing;
    private boolean isAttached;
    private Player player;

    public CustomSeekView(Context context) {
        this(context, null);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_control_seek, this);
        positionView = findViewById(R.id.position);
        durationView = findViewById(R.id.duration);
        timeBar = findViewById(R.id.timeBar);
        timeBar.addListener(this);
        runnable = this::updateProgress;
    }

    public void setPlayer(@Nullable Player player) {
        if (this.player == player) return;
        this.player = player;
        updateTimeline();
    }

    public TimeBar getTimeBar() {
        return timeBar;
    }

    private void updateTimeline() {
        if (!isAttached || player == null) return;
        long duration = player.getDuration();
        currentDuration = duration;
        setKeyTimeIncrement(duration);
        timeBar.setDuration(duration);
        durationView.setText(formatTime(Math.max(0, duration)));
        updateProgress();
    }

    private void updateProgress() {
        removeCallbacks(runnable);
        if (!isAttached || player == null) return;
        if (player.getPlaybackState() == Player.STATE_IDLE) {
            resetView();
            post(runnable);
            return;
        }
        long position = player.getCurrentPosition();
        long buffered = player.getBufferedPosition();
        long duration = player.getDuration();
        if (duration != currentDuration) {
            currentDuration = duration;
            setKeyTimeIncrement(duration);
            timeBar.setDuration(duration);
            durationView.setText(formatTime(Math.max(0, duration)));
        }
        if (position != currentPosition) {
            currentPosition = position;
            if (!scrubbing) {
                timeBar.setPosition(position);
                positionView.setText(formatTime(Math.max(0, position)));
            }
        }
        if (buffered != currentBuffered) {
            currentBuffered = buffered;
            timeBar.setBufferedPosition(buffered);
        }
        postDelayed(runnable, player.isPlaying() ? delayMs(position) : MAX_UPDATE_INTERVAL_MS);
    }

    private void resetView() {
        positionView.setText("00:00");
        durationView.setText("00:00");
        timeBar.setPosition(currentPosition = 0);
        timeBar.setDuration(currentDuration = 0);
        timeBar.setBufferedPosition(currentBuffered = 0);
    }

    private void setKeyTimeIncrement(long duration) {
        if (duration > TimeUnit.HOURS.toMillis(3)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(5));
        } else if (duration > TimeUnit.MINUTES.toMillis(30)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
        } else if (duration > TimeUnit.MINUTES.toMillis(15)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(30));
        } else if (duration > TimeUnit.MINUTES.toMillis(10)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(15));
        } else if (duration > 0) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private long delayMs(long position) {
        long mediaTimeUntilNextFullSecondMs = 1000 - position % 1000;
        long mediaTimeDelayMs = Math.min(timeBar.getPreferredUpdateDelay(), mediaTimeUntilNextFullSecondMs);
        return Util.constrainValue(mediaTimeDelayMs, MIN_UPDATE_INTERVAL_MS, MAX_UPDATE_INTERVAL_MS);
    }

    private String formatTime(long timeMs) {
        long totalSeconds = (timeMs + 500) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttached = true;
        updateTimeline();
    }

    @Override
    protected void onDetachedFromWindow() {
        isAttached = false;
        removeCallbacks(runnable);
        super.onDetachedFromWindow();
    }

    @Override
    public void onScrubStart(@NonNull TimeBar timeBar, long position) {
        scrubbing = true;
        if (player != null) positionView.setText(formatTime(position));
    }

    @Override
    public void onScrubMove(@NonNull TimeBar timeBar, long position) {
        if (player != null) positionView.setText(formatTime(position));
    }

    @Override
    public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
        scrubbing = false;
        // seek 统一由 PlaybackActivity 的 TimeBar listener 处理（含 seekTo + play），这里只恢复文本刷新
    }
}
