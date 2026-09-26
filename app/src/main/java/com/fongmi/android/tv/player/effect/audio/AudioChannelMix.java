package com.fongmi.android.tv.player.effect.audio;

public final class AudioChannelMix {

    private AudioChannelMix() {
    }

    public static float mixStereoLeft(float[] samples) {
        if (samples.length == 0) return 0f;
        if (samples.length == 1) return samples[0];
        return samples[0];
    }

    public static float mixStereoRight(float[] samples) {
        if (samples.length == 0) return 0f;
        if (samples.length == 1) return samples[0];
        return samples[1];
    }

    public static float mixMono(float[] samples) {
        if (samples.length == 0) return 0f;
        float sum = 0f;
        for (float sample : samples) sum += sample;
        return sum / samples.length;
    }
}
