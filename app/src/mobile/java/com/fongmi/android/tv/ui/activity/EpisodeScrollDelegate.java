package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;

/**
 * 选集列表的滚动定位：退出全屏后把列表滚回当前集。
 * <p>
 * 单独抽出来是因为它有一份必须成对管理的延时任务：既 post 到 episode 的
 * View 队列，也在销毁时从 App 的主线程队列移除。放在 Activity 里容易被
 * onDestroy 漏掉，销毁后仍会操作已回收的 RecyclerView。
 */
final class EpisodeScrollDelegate {

    private final VideoActivity activity;
    private final ActivityVideoBinding binding;
    private final Runnable task = this::scroll;
    private EpisodeAdapter adapter;

    EpisodeScrollDelegate(VideoActivity activity, ActivityVideoBinding binding) {
        this.activity = activity;
        this.binding = binding;
    }

    void setAdapter(EpisodeAdapter adapter) {
        this.adapter = adapter;
    }

    /** 退出全屏后等布局稳定再滚，立即滚会停在旧位置 */
    void schedule() {
        binding.episode.postDelayed(task, 100);
    }

    private void scroll() {
        if (activity.isDestroyed() || adapter == null) return;
        binding.episode.scrollToPosition(adapter.getPosition());
    }

    void release() {
        binding.episode.removeCallbacks(task);
        App.removeCallbacks(task);
    }
}
