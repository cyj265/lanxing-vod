package com.fongmi.android.tv.player.danmaku;

import com.fongmi.android.tv.player.Players;

import master.flame.danmaku.danmaku.model.AbsDanmakuSync;

public class Sync extends AbsDanmakuSync {

    private final Players player;

    public Sync(Players player) {
        this.player = player;
    }

    @Override
    public long getUptimeMillis() {
        return player.getPosition();
    }

    @Override
    public int getSyncState() {
        return player.isPlaying() ? SYNC_STATE_PLAYING : SYNC_STATE_HALT;
    }

    @Override
    public long getThresholdTimeMills() {
        return 1000L;
    }

    @Override
    public boolean isSyncPlayingState() {
        return true;
    }
}
