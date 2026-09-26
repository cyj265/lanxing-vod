package com.fongmi.android.tv.player.effect.audio;

import java.util.Arrays;

final class AudioSoftwareEqualizer {

    private static final double Q = 1.0;

    private final double[] a1;
    private final double[] a2;
    private final double[] b0;
    private final double[] b1;
    private final double[] b2;
    private final boolean[] active;
    private AudioEffectConfig config;
    private short[] levels;
    private double[][] x1;
    private double[][] x2;
    private double[][] y1;
    private double[][] y2;
    private int sampleRate;
    private int channelCount;

    AudioSoftwareEqualizer() {
        int count = AudioEffectBands.STANDARD.getCount();
        this.a1 = new double[count];
        this.a2 = new double[count];
        this.b0 = new double[count];
        this.b1 = new double[count];
        this.b2 = new double[count];
        this.active = new boolean[count];
    }

    void configure(int sampleRate, int channelCount, AudioEffectConfig config) {
        if (sampleRate <= 0 || channelCount <= 0) return;
        boolean rateChanged = this.sampleRate != sampleRate;
        boolean channelsChanged = this.channelCount != channelCount;
        boolean configChanged = this.config != config;
        if (!rateChanged && !channelsChanged && !configChanged) return;
        updateConfiguration(sampleRate, channelCount, config, rateChanged, channelsChanged);
    }

    private void updateConfiguration(int sampleRate, int channelCount, AudioEffectConfig config, boolean rateChanged, boolean channelsChanged) {
        if (channelsChanged) allocateState(channelCount);
        short[] nextLevels = Arrays.copyOf(config.getLevels(), active.length);
        updateBands(nextLevels, sampleRate, rateChanged);
        if (rateChanged && !channelsChanged) clearState();
        setConfiguration(nextLevels, sampleRate, config);
    }

    private void updateBands(short[] nextLevels, int sampleRate, boolean rateChanged) {
        for (int index = 0; index < nextLevels.length; index++) {
            if (rateChanged || levels == null || levels[index] != nextLevels[index]) setBand(index, nextLevels[index], sampleRate);
        }
    }

    private void setConfiguration(short[] levels, int sampleRate, AudioEffectConfig config) {
        this.levels = levels;
        this.sampleRate = sampleRate;
        this.config = config;
    }

    void process(float[] samples) {
        int channels = Math.min(samples.length, channelCount);
        for (int channel = 0; channel < channels; channel++) samples[channel] = processChannel(samples[channel], channel);
    }

    private float processChannel(float sample, int channel) {
        double value = sample;
        for (int band = 0; band < active.length; band++) {
            if (active[band]) value = processBand(value, band, channel);
        }
        return Double.isFinite(value) ? (float) value : 0.0f;
    }

    private double processBand(double value, int band, int channel) {
        double output = b0[band] * value + b1[band] * x1[band][channel] + b2[band] * x2[band][channel] - a1[band] * y1[band][channel] - a2[band] * y2[band][channel];
        x2[band][channel] = x1[band][channel];
        x1[band][channel] = value;
        y2[band][channel] = y1[band][channel];
        y1[band][channel] = output;
        return output;
    }

    void reset() {
        clearState();
    }

    private void setBand(int index, short level, int sampleRate) {
        double frequency = AudioEffectBands.STANDARD.getCenterFrequency(index) / 1000.0;
        if (level == 0 || frequency >= sampleRate * 0.5) {
            active[index] = false;
            clearBandState(index);
        } else {
            double amplitude = Math.pow(10.0, level / 4000.0);
            double omega = 2.0 * Math.PI * frequency / sampleRate;
            double alpha = Math.sin(omega) / (2.0 * Q);
            double a0 = 1.0 + alpha / amplitude;
            b0[index] = (1.0 + alpha * amplitude) / a0;
            b1[index] = -2.0 * Math.cos(omega) / a0;
            b2[index] = (1.0 - alpha * amplitude) / a0;
            a1[index] = -2.0 * Math.cos(omega) / a0;
            a2[index] = (1.0 - alpha / amplitude) / a0;
            active[index] = true;
        }
    }

    private void allocateState(int channelCount) {
        this.channelCount = channelCount;
        this.x1 = new double[active.length][channelCount];
        this.x2 = new double[active.length][channelCount];
        this.y1 = new double[active.length][channelCount];
        this.y2 = new double[active.length][channelCount];
    }

    private void clearState() {
        if (x1 != null) for (int band = 0; band < active.length; band++) clearBandState(band);
    }

    private void clearBandState(int band) {
        Arrays.fill(x1[band], 0.0);
        Arrays.fill(x2[band], 0.0);
        Arrays.fill(y1[band], 0.0);
        Arrays.fill(y2[band], 0.0);
    }
}
