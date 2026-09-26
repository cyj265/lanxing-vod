package com.fongmi.android.tv.player.mpv;

import androidx.media3.common.Player;
import androidx.media3.mpvplayer.MpvPlayer;
import androidx.media3.mpvplayer.MpvPlayerConfig;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SubtitleSetting;

import is.xyz.mpv.MPVLib;

public final class MpvUtil {

    private MpvUtil() {
    }

    public static boolean isAvailable() {
        try {
            return MPVLib.ensureLoaded(App.get());
        } catch (Throwable e) {
            return false;
        }
    }

    public static MpvPlayer buildPlayer(int decode, Player.Listener listener) {
        boolean hard = decode == PlayerEngine.HARD;
        boolean vulkan = PlayerSetting.isMpvVulkan() && MPVLib.isVulkanRendererAvailable(App.get());
        int bufferSeconds = Math.max(15, PlayerSetting.getBuffer() * 5);
        long demuxerBytes = 64L * 1024L * 1024L;

        MpvPlayerConfig.Builder builder = MpvPlayerConfig.builder(App.get())
                .hwdec(hard ? "mediacodec,mediacodec-copy" : "no")
                .cache(true)
                .demuxerMaxBytes(demuxerBytes)
                .demuxerMaxBackBytes(demuxerBytes)
                .cacheSeconds(bufferSeconds)
                .demuxerReadaheadSeconds(bufferSeconds)
                .logLevel(PlayerSetting.isDebug() ? "all=v" : "all=warn");

        if (vulkan) {
            builder.vo("gpu-next").gpuContext("androidvk").gpuApi("vulkan").openglEs(false);
        } else {
            builder.vo(PlayerSetting.isMpvGpuNext() ? "gpu-next" : "gpu")
                    .gpuContext("android")
                    .gpuApi("opengl")
                    .openglEs(true);
        }

        MpvPlayer player = new MpvPlayer(App.get(), builder.build());
        if (listener != null) player.addListener(listener);
        setSubtitleStyle(player);
        return player;
    }

    public static void setSubtitleStyle(MpvPlayer player) {
        if (player == null) return;
        float textSize = SubtitleSetting.getScale(App.get());
        float position = SubtitleSetting.getPosition();
        player.setSubtitleStyle(textSize, position);
    }

    public static void setDecode(int decode) {
        try {
            MPVLib.setPropertyString("hwdec", decode == PlayerEngine.HARD ? "mediacodec,mediacodec-copy" : "no");
        } catch (Throwable ignored) {
        }
    }
}
