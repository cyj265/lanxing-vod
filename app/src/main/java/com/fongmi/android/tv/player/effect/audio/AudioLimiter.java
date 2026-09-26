package com.fongmi.android.tv.player.effect.audio;

final class AudioLimiter {

    private static final float LIMIT = 0.98f;
    private static final float RELEASE_SECONDS = 0.05f;

    private float envelope;
    private float releaseStep = 1.0f;

    void configure(int sampleRate) {
        releaseStep = smoothingStep(sampleRate, RELEASE_SECONDS);
    }

    float getGain(float peak, float gain) {
        float amplifiedPeak = peak * gain;
        if (amplifiedPeak > envelope) envelope = amplifiedPeak;
        else envelope += (amplifiedPeak - envelope) * releaseStep;
        return envelope > LIMIT ? gain * LIMIT / envelope : gain;
    }

    float limit(float sample) {
        return Math.clamp(sample, -LIMIT, LIMIT);
    }

    void reset() {
        envelope = 0.0f;
    }

    private static float smoothingStep(int sampleRate, float seconds) {
        return 1.0f - (float) Math.exp(-1.0f / (sampleRate * seconds));
    }
}
