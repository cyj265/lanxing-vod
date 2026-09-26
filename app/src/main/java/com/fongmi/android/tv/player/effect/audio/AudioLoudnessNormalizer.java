package com.fongmi.android.tv.player.effect.audio;

final class AudioLoudnessNormalizer {

    private static final float TARGET = 0.125f;
    private static final float GATE_POWER = 0.000004f;
    private static final float MIN_GAIN = 0.5f;
    private static final float MAX_GAIN = 4.0f;

    private float power = TARGET * TARGET;
    private float gain = 1.0f;
    private float powerStep = 1.0f;
    private float attackStep = 1.0f;
    private float releaseStep = 1.0f;

    static float getLoudnessPower(float[] samples, int channelMode) {
        int channelCount = getLoudnessChannelCount(samples.length, channelMode);
        return sumLoudnessPower(samples, channelCount) / Math.min(2, channelCount);
    }

    private static float sumLoudnessPower(float[] samples, int channelCount) {
        float power = 0.0f;
        for (int channel = 0; channel < channelCount; channel++) if (!isLfeChannel(channel, samples.length)) power += samples[channel] * samples[channel];
        return power;
    }

    private static int getLoudnessChannelCount(int channelCount, int channelMode) {
        int mode = AudioChannelMode.resolve(channelMode, channelCount);
        return AudioChannelMode.isAuto(mode) ? channelCount : Math.min(2, channelCount);
    }

    private static boolean isLfeChannel(int channel, int channelCount) {
        return channel == 3 && channelCount >= 6 && channelCount <= 8;
    }

    private static float smoothingStep(int sampleRate, float seconds) {
        return 1.0f - (float) Math.exp(-1.0f / (sampleRate * seconds));
    }

    void configure(int sampleRate) {
        powerStep = smoothingStep(sampleRate, 10.0f);
        attackStep = smoothingStep(sampleRate, 1.0f);
        releaseStep = smoothingStep(sampleRate, 5.0f);
    }

    float getGain(float[] samples, AudioEffectConfig config) {
        if (!config.isLoudnessEnabled()) return 1.0f;
        updateGain(getLoudnessPower(samples, config.getChannelMode()));
        return gain;
    }

    void reset() {
        power = TARGET * TARGET;
        gain = 1.0f;
    }

    private void updateGain(float currentPower) {
        if (currentPower < GATE_POWER) return;
        power += (currentPower - power) * powerStep;
        float targetGain = TARGET / (float) Math.sqrt(Math.max(power, GATE_POWER));
        targetGain = Math.clamp(targetGain, MIN_GAIN, MAX_GAIN);
        float step = targetGain < gain ? attackStep : releaseStep;
        gain += (targetGain - gain) * step;
    }
}
