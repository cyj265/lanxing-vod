package com.fongmi.android.tv.player.effect.video;

import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;

public final class ExoVideoEffectController {

    /** 超过此宽度跳过 GL 后处理特效：4K 逐帧 OpenGL 处理会触发 VIDEO_FRAME_PROCESSING_FAILED */
    private static final int MAX_EFFECT_WIDTH = 1920;

    private final ColorToneAdjustEffect colorTone;
    private final DetailAdjustEffect detail;
    private final List<Effect> effects;
    private boolean configured;
    private boolean profileApplied;

    public ExoVideoEffectController() {
        this.colorTone = new ColorToneAdjustEffect();
        this.detail = new DetailAdjustEffect();
        this.effects = List.of(colorTone, detail);
    }

    public void apply(ExoPlayer player, VideoEffectProfile profile) {
        // 记录用户期望：有非 no-op 特效才需要在格式就绪后重新判定
        profileApplied = !profile.isNoOp();
        applyInternal(player, profile);
    }

    /** 视频输入格式确定/切换后重新判定：4K 自动撤掉 GL 特效，回到低分辨率时恢复 */
    public void onVideoFormatChanged(ExoPlayer player, VideoEffectProfile profile, Format format) {
        if (!profileApplied) return;
        applyInternal(player, profile, format != null ? format.width : Format.NO_VALUE);
    }

    private void applyInternal(ExoPlayer player, VideoEffectProfile profile) {
        Format format = player.getVideoFormat();
        applyInternal(player, profile, format == null ? Format.NO_VALUE : format.width);
    }

    private void applyInternal(ExoPlayer player, VideoEffectProfile profile, int width) {
        if (width > MAX_EFFECT_WIDTH) {
            if (configured) {
                player.setVideoEffects(List.of());
                configured = false;
            }
            return;
        }
        if (!configured && profile.isNoOp()) return;
        colorTone.setProfile(profile);
        detail.setProfile(profile);
        player.setVideoEffects(effects);
        configured = true;
    }

    public void clear(ExoPlayer player) {
        profileApplied = false;
        if (!configured) return;
        player.setVideoEffects(List.of());
        configured = false;
    }

}
