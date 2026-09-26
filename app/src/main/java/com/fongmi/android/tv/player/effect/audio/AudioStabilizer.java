package com.fongmi.android.tv.player.effect.audio;

final class AudioStabilizer {

    private static final float INITIAL_ENVELOPE = 0.08f;
    private static final float MIN_ENVELOPE = 0.02f;

    private float envelope = INITIAL_ENVELOPE;
    private float gain = 1.0f;

    float getGain(float peak, AudioEffectConfig config) {
        if (config.getStability() <= 0) return 1.0f;
        updateGain(peak, config.getStabilityAmount());
        return gain;
    }

    void reset() {
        envelope = INITIAL_ENVELOPE;
        gain = 1.0f;
    }

    private void updateGain(float peak, float intensity) {
        float targetEnvelope = Math.max(peak, MIN_ENVELOPE);
        float envelopeStep = targetEnvelope > envelope ? 0.08f : 0.002f;
        envelope += (targetEnvelope - envelope) * envelopeStep;
        float targetGain = 0.22f / Math.max(envelope, MIN_ENVELOPE);
        float maxGain = 1.0f + 2.2f * intensity;
        float minGain = 1.0f - 0.65f * intensity;
        targetGain = Math.clamp(targetGain, minGain, maxGain);
        targetGain = 1.0f + (targetGain - 1.0f) * intensity;
        float gainStep = targetGain < gain ? 0.025f : 0.001f;
        gain += (targetGain - gain) * gainStep;
    }
}
