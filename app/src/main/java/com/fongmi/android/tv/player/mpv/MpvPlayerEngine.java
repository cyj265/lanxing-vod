package com.fongmi.android.tv.player.mpv;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.mpvplayer.MpvPlayer;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;

public class MpvPlayerEngine implements PlayerEngine {

    private final MpvErrorMessageProvider provider;
    private final Player.Listener externalListener;
    private final MpvPlayer player;
    private PlaySpec spec;
    private int decode;

    public MpvPlayerEngine(int decode, Player.Listener listener) {
        this.decode = decode;
        this.externalListener = listener;
        this.provider = new MpvErrorMessageProvider();
        this.player = MpvUtil.buildPlayer(decode, listener);
    }

    public static boolean isAvailable() {
        return MpvUtil.isAvailable();
    }

    @Override
    public Type getType() {
        return Type.MPV;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public int getAudioChannelCount() {
        return Format.NO_VALUE;
    }

    @Override
    public void release() {
        try {
            if (externalListener != null) player.removeListener(externalListener);
        } catch (Throwable ignored) {
        }
        player.release();
    }

    @Override
    public void setSubtitleStyle() {
        MpvUtil.setSubtitleStyle(player);
    }

    @Override
    public boolean addSubtitle(Sub sub) {
        // The bundled MPV implementation reads subtitle configurations from the MediaItem.
        // Returning false asks PlayerManager to rebuild the current item with the new subtitle.
        return false;
    }

    @Override
    public void setDecode(int decode) {
        this.decode = decode;
        MpvUtil.setDecode(decode);
    }

    @Override
    public void start(PlaySpec spec, long startPositionMs) {
        this.spec = spec;
        startInternal(startPositionMs);
    }

    private void startInternal(long startPositionMs) {
        player.setMediaItem(MediaItemFactory.from(spec), Math.max(0L, startPositionMs));
        player.prepare();
        player.play();
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return provider.get(e);
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        return switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED -> ErrorAction.DECODE;
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> retryHls();
            default -> ErrorAction.FATAL;
        };
    }

    private ErrorAction retryHls() {
        if (spec == null || MimeTypes.APPLICATION_M3U8.equals(spec.getFormat())) return ErrorAction.FATAL;
        spec.setFormat(MimeTypes.APPLICATION_M3U8);
        startInternal(player.getCurrentPosition());
        return ErrorAction.RECOVERED;
    }
}
