package com.fongmi.android.tv.player.exo;

import androidx.media3.common.Format;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.effect.PlayerEffect;
import com.fongmi.android.tv.player.effect.audio.AudioEffectBands;
import com.fongmi.android.tv.player.effect.audio.AudioEffectConfig;
import com.fongmi.android.tv.player.effect.audio.ExoAudioEffectController;
import com.fongmi.android.tv.player.effect.video.ExoVideoEffectController;
import com.fongmi.android.tv.player.effect.video.VideoEffectProfile;
import com.fongmi.android.tv.setting.VideoSetting;

public final class ExoPlayerEffect implements PlayerEffect {

    private final ExoAudioEffectController audioEffectController;
    private final ExoVideoEffectController videoEffectController;

    private boolean previewVideoEffect;
    private boolean previewAudioEffect;
    private boolean audioEffectFailed;
    private ExoPlayer player;

    public ExoPlayerEffect() {
        this.audioEffectController = new ExoAudioEffectController();
        this.videoEffectController = new ExoVideoEffectController();
    }

    public AudioProcessor getAudioProcessor() {
        return audioEffectController.getProcessor();
    }

    public void setPlayer(ExoPlayer player) {
        this.player = player;
    }

    public void release() {
        audioEffectController.release();
    }

    @Override
    public boolean supportsVideoEffect() {
        // WebHTV-pinned Media3 1.11 fork supports setVideoEffects(),
        // but no longer exposes the older getVideoEffectsSupport() helper.
        return player != null;
    }

    @Override
    public int getVideoEffectError() {
        return player == null ? R.string.error_video_effect_unsupported : 0;
    }

    @Override
    public void applyVideoEffect() {
        if (supportsVideoEffect() && (previewVideoEffect || VideoSetting.isEnabled())) videoEffectController.apply(player, getVideoProfile());
        else videoEffectController.clear(player);
    }

    @Override
    public void previewVideoEffect(boolean original) {
        if (previewVideoEffect == original) return;
        previewVideoEffect = original;
        applyVideoEffect();
    }

    @Override
    public boolean supportsAudioEffect() {
        return !audioEffectFailed;
    }

    @Override
    public AudioEffectBands getAudioEffectBands() {
        return AudioEffectBands.STANDARD;
    }

    @Override
    public int getAudioEffectError() {
        return audioEffectFailed ? R.string.error_audio_effect_apply : 0;
    }

    @Override
    public void applyAudioEffect() {
        applyAudioConfig(getAudioChannelCount());
    }

    public void clearAudioEffect() {
        audioEffectController.release();
        audioEffectFailed = false;
    }

    @Override
    public void previewAudioEffect(boolean original) {
        if (previewAudioEffect == original) return;
        previewAudioEffect = original;
        applyAudioEffect();
    }

    @Override
    public boolean supportsSkipSilence() {
        return true;
    }

    @Override
    public void setSkipSilenceEnabled(boolean enabled) {
        player.setSkipSilenceEnabled(enabled);
    }

    private VideoEffectProfile getVideoProfile() {
        return previewVideoEffect ? VideoEffectProfile.off() : VideoSetting.getAppliedProfile();
    }

    private AudioEffectConfig getAudioConfig(int channelCount) {
        return previewAudioEffect ? AudioEffectConfig.disabled() : AudioEffectConfig.from(getAudioEffectBands(), channelCount);
    }

    private int getAudioChannelCount() {
        Format format = player.getAudioFormat();
        return format == null ? Format.NO_VALUE : format.channelCount;
    }

    private void applyAudioConfig(int channelCount) {
        if (channelCount == Format.NO_VALUE) return;
        AudioEffectConfig config = getAudioConfig(channelCount);
        audioEffectFailed = !audioEffectController.apply(player, config);
    }
}
