package com.fongmi.android.tv.player.effect.audio;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.mpvplayer.audio.AudioChannelMix;

import java.nio.ByteBuffer;

public final class AudioEffectProcessor extends BaseAudioProcessor {

    private final AudioSoftwareEqualizer equalizer = new AudioSoftwareEqualizer();
    private final AudioLimiter limiter = new AudioLimiter();
    private final AudioLoudnessNormalizer loudness = new AudioLoudnessNormalizer();
    private final AudioStabilizer stabilizer = new AudioStabilizer();
    private ProcessorSettings settings = new ProcessorSettings(AudioEffectConfig.disabled(), false);
    private float[] frame = new float[0];
    private boolean resetRequested;

    private static boolean shouldResetState(AudioEffectConfig current, AudioEffectConfig next) {
        return current.getStability() != next.getStability() || current.getBoost() != next.getBoost() || current.getPreamp() != next.getPreamp() || current.isLoudnessEnabled() != next.isLoudnessEnabled() || current.getCenterGain() != next.getCenterGain() || current.getBalance() != next.getBalance() || current.getChannelMode() != next.getChannelMode();
    }

    private static float getPeak(float[] samples) {
        float peak = 0.0f;
        for (float sample : samples) peak = Math.max(peak, Math.abs(sample));
        return peak;
    }

    private static float sanitize(float sample) {
        return Float.isFinite(sample) ? sample : 0.0f;
    }

    private static short toPcm16(float sample) {
        sample = Math.clamp(sample, -1.0f, 1.0f);
        return sample <= -1.0f ? Short.MIN_VALUE : (short) Math.round(sample * Short.MAX_VALUE);
    }

    public void resetSettings() {
        setConfig(AudioEffectConfig.disabled(), false);
    }

    public synchronized void setConfig(AudioEffectConfig config, boolean softwareEqualizer) {
        if (shouldResetState(settings.config, config) || settings.softwareEqualizer != softwareEqualizer) resetRequested = true;
        settings = new ProcessorSettings(config, softwareEqualizer);
    }

    @NonNull
    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        boolean supported = inputAudioFormat.channelCount > 0 && (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT);
        if (!supported) return AudioFormat.NOT_SET;
        int sampleRate = Math.max(1, inputAudioFormat.sampleRate);
        limiter.configure(sampleRate);
        loudness.configure(sampleRate);
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) return;
        int remaining = inputBuffer.remaining();
        checkCompleteFrames(remaining);
        ByteBuffer outputBuffer = replaceOutputBuffer(remaining);
        ProcessorSettings settings = getCurrentSettings();
        if (settings.isActive(inputAudioFormat.channelCount)) processInput(inputBuffer, outputBuffer, settings);
        else outputBuffer.put(inputBuffer);
        outputBuffer.flip();
    }

    @Override
    protected void onFlush(@NonNull StreamMetadata streamMetadata) {
        resetState();
    }

    @Override
    protected void onReset() {
        resetState();
    }

    private void checkCompleteFrames(int bytes) {
        if (bytes % inputAudioFormat.bytesPerFrame != 0) throw new IllegalStateException("Queued an incomplete frame.");
    }

    private synchronized ProcessorSettings getCurrentSettings() {
        ProcessorSettings current = settings;
        if (resetRequested) resetState();
        resetRequested = false;
        return current;
    }

    private void processInput(ByteBuffer inputBuffer, ByteBuffer outputBuffer, ProcessorSettings settings) {
        if (settings.softwareEqualizer) equalizer.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, settings.config);
        float[] samples = getFrame(inputAudioFormat.channelCount);
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) processFloatInput(inputBuffer, outputBuffer, samples, settings);
        else processPcm16Input(inputBuffer, outputBuffer, samples, settings);
    }

    private float[] getFrame(int channelCount) {
        if (frame.length != channelCount) frame = new float[channelCount];
        return frame;
    }

    private void processPcm16Input(ByteBuffer inputBuffer, ByteBuffer outputBuffer, float[] samples, ProcessorSettings settings) {
        while (inputBuffer.hasRemaining()) {
            for (int channel = 0; channel < samples.length; channel++) samples[channel] = inputBuffer.getShort() / 32768.0f;
            processFrame(samples, settings);
            for (float sample : samples) outputBuffer.putShort(toPcm16(sample));
        }
    }

    private void processFloatInput(ByteBuffer inputBuffer, ByteBuffer outputBuffer, float[] samples, ProcessorSettings settings) {
        while (inputBuffer.hasRemaining()) {
            for (int channel = 0; channel < samples.length; channel++) samples[channel] = sanitize(inputBuffer.getFloat());
            processFrame(samples, settings);
            for (float sample : samples) outputBuffer.putFloat(sample);
        }
    }

    private void processFrame(float[] samples, ProcessorSettings settings) {
        AudioEffectConfig config = settings.config;
        applyCenterGain(samples, config);
        applyChannelMode(samples, config.getChannelMode());
        applyBalance(samples, config.getBalance(), config.getChannelMode());
        applyGain(samples, config, !settings.softwareEqualizer);
        if (settings.softwareEqualizer) applySoftwareEqualizer(samples, config);
    }

    private void applyGain(float[] samples, AudioEffectConfig config, boolean applyLimiter) {
        float peak = getPeak(samples);
        float gain = config.getPreampGain() * loudness.getGain(samples, config) * stabilizer.getGain(peak, config) * config.getBoostGain();
        boolean limit = applyLimiter && config.shouldLimitProcessor(samples.length);
        if (limit) gain = limiter.getGain(peak, gain);
        for (int i = 0; i < samples.length; i++) {
            float sample = sanitize(samples[i] * gain);
            samples[i] = limit ? limiter.limit(sample) : sample;
        }
    }

    private void applySoftwareEqualizer(float[] samples, AudioEffectConfig config) {
        equalizer.process(samples);
        applyOutputLimiter(samples, config.shouldLimitOutput(samples.length));
    }

    private void applyOutputLimiter(float[] samples, boolean enabled) {
        if (!enabled) return;
        float gain = limiter.getGain(getPeak(samples), 1.0f);
        for (int i = 0; i < samples.length; i++) samples[i] = limiter.limit(sanitize(samples[i] * gain));
    }

    private void applyCenterGain(float[] samples, AudioEffectConfig settings) {
        if (settings.hasCenterGain(samples.length)) samples[2] = sanitize(samples[2] * settings.getCenterGainFactor());
    }

    private void applyChannelMode(float[] samples, int mode) {
        switch (AudioChannelMode.resolve(mode, samples.length)) {
            case AudioChannelMode.STEREO -> applyStereo(samples);
            case AudioChannelMode.MONO -> applyMono(samples);
            case AudioChannelMode.REVERSE -> applyReverse(samples);
        }
    }

    private void applyStereo(float[] samples) {
        setStereo(samples, AudioChannelMix.mixStereoLeft(samples), AudioChannelMix.mixStereoRight(samples));
    }

    private void setStereo(float[] samples, float left, float right) {
        samples[0] = sanitize(left);
        samples[1] = sanitize(right);
        clearExtraChannels(samples);
    }

    private void applyMono(float[] samples) {
        float mono = sanitize(AudioChannelMix.mixMono(samples));
        samples[0] = mono;
        samples[1] = mono;
        clearExtraChannels(samples);
    }

    private void applyReverse(float[] samples) {
        float left = AudioChannelMix.mixStereoLeft(samples);
        float right = AudioChannelMix.mixStereoRight(samples);
        setStereo(samples, right, left);
    }

    private void clearExtraChannels(float[] samples) {
        for (int i = 2; i < samples.length; i++) samples[i] = 0.0f;
    }

    private void applyBalance(float[] samples, int balance, int mode) {
        if (balance == 0 || !AudioChannelMode.isAvailable(mode, samples.length)) return;
        samples[0] *= balance > 0 ? 1.0f - balance / 100.0f : 1.0f;
        samples[1] *= balance < 0 ? 1.0f + balance / 100.0f : 1.0f;
    }

    private void resetState() {
        limiter.reset();
        loudness.reset();
        stabilizer.reset();
        equalizer.reset();
    }

    private record ProcessorSettings(AudioEffectConfig config, boolean softwareEqualizer) {

        private boolean isActive(int channelCount) {
            return config.hasProcessorEffect(channelCount) || softwareEqualizer;
        }
    }
}
