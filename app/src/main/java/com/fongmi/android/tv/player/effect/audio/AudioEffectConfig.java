package com.fongmi.android.tv.player.effect.audio;

import com.fongmi.android.tv.setting.AudioSetting;

import java.util.Arrays;

public final class AudioEffectConfig {

    private static final AudioEffectConfig DISABLED = new AudioEffectConfig(new short[0], 0, 0, 0, false, 0, 0, AudioChannelMode.AUTO);

    private final short[] levels;
    private final int stability;
    private final int boost;
    private final int preamp;
    private final int centerGain;
    private final int balance;
    private final int channelMode;
    private final float boostGain;
    private final float preampGain;
    private final float centerGainFactor;
    private final boolean loudness;
    private final boolean positiveBandGain;

    AudioEffectConfig(short[] levels, int stability, int boost, int preamp, boolean loudness, int centerGain, int balance, int channelMode) {
        this.levels = Arrays.copyOf(levels, levels.length);
        this.stability = Math.clamp(stability, AudioSetting.MIN_STABILITY, AudioSetting.MAX_STABILITY);
        this.boost = Math.clamp(boost, AudioSetting.MIN_BOOST, AudioSetting.MAX_BOOST);
        this.preamp = getEffectivePreamp(this.levels, preamp);
        this.centerGain = Math.clamp(centerGain, AudioSetting.MIN_CENTER_GAIN, AudioSetting.MAX_CENTER_GAIN);
        this.channelMode = AudioChannelMode.clamp(channelMode);
        this.balance = getEffectiveBalance(balance, this.channelMode);
        this.boostGain = getGain(this.boost);
        this.preampGain = getGain(this.preamp);
        this.centerGainFactor = getGain(this.centerGain);
        this.loudness = loudness;
        this.positiveBandGain = hasPositiveBandGain(this.levels);
    }

    public static AudioEffectConfig disabled() {
        return DISABLED;
    }

    public static AudioEffectConfig from(AudioEffectBands bands, int channelCount) {
        int channelMode = AudioChannelMode.resolve(AudioSetting.getChannelMode(), channelCount);
        int centerGain = isCenterGainAvailable(channelCount) ? AudioSetting.getCenterGain() : 0;
        int balance = AudioChannelMode.isAvailable(channelMode, channelCount) ? AudioSetting.getBalance() : 0;
        boolean hasEqualizer = AudioSetting.isEnabled() || AudioSetting.getDialogue() != 0;
        short[] levels = hasEqualizer && !bands.isEmpty() ? AudioSetting.getAppliedLevels(bands) : new short[0];
        AudioEffectConfig config = new AudioEffectConfig(levels, AudioSetting.getStability(), AudioSetting.getBoost(), AudioSetting.getPreamp(), AudioSetting.isLoudnessEnabled(), centerGain, balance, channelMode);
        return config.hasEffect() ? config : DISABLED;
    }

    public static boolean isCenterGainAvailable(int channelCount) {
        return channelCount == 6 || channelCount == 8;
    }

    public short[] getLevels() {
        return Arrays.copyOf(levels, levels.length);
    }

    public int getStability() {
        return stability;
    }

    public float getStabilityAmount() {
        return stability / (float) AudioSetting.MAX_STABILITY;
    }

    public int getBoost() {
        return boost;
    }

    float getBoostGain() {
        return boostGain;
    }

    public int getPreamp() {
        return preamp;
    }

    float getPreampGain() {
        return preampGain;
    }

    public boolean isLoudnessEnabled() {
        return loudness;
    }

    public int getCenterGain() {
        return centerGain;
    }

    float getCenterGainFactor() {
        return centerGainFactor;
    }

    public int getBalance() {
        return balance;
    }

    public int getChannelMode() {
        return channelMode;
    }

    public boolean hasEffect() {
        return hasBands() || hasProcessorEffect();
    }

    public boolean hasBands() {
        return levels.length > 0;
    }

    public boolean hasProcessorEffect() {
        return hasProcessorEffectWithoutCenter() || centerGain > 0;
    }

    boolean hasProcessorEffect(int channelCount) {
        return hasAmplifyingProcessorEffect() || preamp != 0 || hasChannelControls(channelCount) || hasCenterGain(channelCount);
    }

    boolean hasCenterGain(int channelCount) {
        return centerGain > 0 && isCenterGainAvailable(channelCount);
    }

    public boolean shouldLimitProcessor(int channelCount) {
        return hasAmplifyingProcessorEffect() || hasCenterGain(channelCount) || AudioChannelMode.canClip(channelMode, channelCount);
    }

    public boolean shouldLimitOutput(int channelCount) {
        return hasAmplifyingProcessorEffect() || hasCenterGain(channelCount) || positiveBandGain || AudioChannelMode.canClip(channelMode, channelCount);
    }

    private static boolean hasPositiveBandGain(short[] levels) {
        for (short level : levels) if (level > 0) return true;
        return false;
    }

    private static int getEffectivePreamp(short[] levels, int preamp) {
        int value = Math.clamp(preamp, AudioSetting.MIN_PREAMP, AudioSetting.MAX_PREAMP);
        int maxLevel = 0;
        for (short level : levels) maxLevel = Math.max(maxLevel, level);
        return value - maxLevel;
    }

    private static int getEffectiveBalance(int balance, int channelMode) {
        if (!AudioChannelMode.canBalance(channelMode)) return 0;
        return Math.clamp(balance, AudioSetting.MIN_BALANCE, AudioSetting.MAX_BALANCE);
    }

    private static float getGain(int level) {
        return level == 0 ? 1.0f : (float) Math.pow(10.0, level / 2000.0);
    }

    private boolean hasAmplifyingProcessorEffect() {
        return stability > 0 || boost > 0 || loudness;
    }

    private boolean hasProcessorEffectWithoutCenter() {
        return hasAmplifyingProcessorEffect() || preamp != 0 || balance != 0 || !AudioChannelMode.isAuto(channelMode);
    }

    private boolean hasChannelControls(int channelCount) {
        return AudioChannelMode.isAvailable(channelMode, channelCount) && (balance != 0 || !AudioChannelMode.isAuto(channelMode));
    }
}
