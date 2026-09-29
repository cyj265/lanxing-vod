package com.fongmi.android.tv.player.effect.audio;

/**
 * Channel mixing math helper used by the software audio processor.
 * <p>
 * Originally lived under the MPV package, but it has no MPV dependency and is
 * still used by the EXO audio effect chain, so it was moved here when the MPV
 * backend was removed.
 */
public final class AudioChannelMix {

    private AudioChannelMix() {
    }

    public static float mixMono(float[] samples) {
        if (samples == null || samples.length == 0) return 0f;
        float sum = 0f;
        for (float sample : samples) sum += sample;
        return sum / samples.length;
    }

    public static float mixStereoLeft(float[] samples) {
        if (samples == null || samples.length == 0) return 0f;
        return samples[0];
    }

    public static float mixStereoRight(float[] samples) {
        if (samples == null || samples.length == 0) return 0f;
        if (samples.length == 1) return samples[0];
        return samples[1];
    }
}
