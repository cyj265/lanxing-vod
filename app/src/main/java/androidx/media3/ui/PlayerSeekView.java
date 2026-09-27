package androidx.media3.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;

/**
 * Compatibility implementation of FM's older PlayerSeekView.
 * Keeps all existing FM XML/data-binding layouts unchanged.
 */
public class PlayerSeekView extends FrameLayout {

    private final DefaultTimeBar timeBar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private @Nullable Player player;
    private boolean scrubbing;

    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (player != null) handler.postDelayed(this, 500L);
        }
    };

    public PlayerSeekView(Context context) {
        this(context, null);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        timeBar = new DefaultTimeBar(context, attrs);
        timeBar.setId(R.id.exo_progress);
        timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                scrubbing = true;
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                // 拖动中只预览位置，不打断播放
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                scrubbing = false;
                Player p = player;
                if (p != null && !canceled && position >= 0) {
                    p.seekTo(position);
                }
                updateProgress();
            }
        });
        addView(timeBar, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void setPlayer(@Nullable Player player) {
        this.player = player;
        handler.removeCallbacks(updater);
        updateProgress();
        if (player != null) handler.post(updater);
    }

    public TimeBar getTimeBar() {
        return timeBar;
    }

    private void updateProgress() {
        Player p = player;
        if (p == null) {
            timeBar.setDuration(0);
            timeBar.setPosition(0);
            timeBar.setBufferedPosition(0);
            return;
        }
        // 拖动中不要用定时刷新覆盖用户正在拖动的位置
        if (scrubbing) return;
        long duration = p.getDuration();
        if (duration == C.TIME_UNSET || duration < 0) duration = 0;
        timeBar.setDuration(duration);
        timeBar.setPosition(Math.max(0L, p.getCurrentPosition()));
        timeBar.setBufferedPosition(Math.max(0L, p.getBufferedPosition()));
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(updater);
        super.onDetachedFromWindow();
    }
}
