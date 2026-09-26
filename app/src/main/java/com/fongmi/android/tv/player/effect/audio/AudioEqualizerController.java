package com.fongmi.android.tv.player.effect.audio;

import android.media.audiofx.DynamicsProcessing;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.App;

import java.util.Arrays;

final class AudioEqualizerController {

    private static final float MAX_CUTOFF_FREQUENCY_HZ = 20_000.0f;

    private DynamicsController dynamics;

    boolean apply(ExoPlayer player, AudioEffectConfig config) {
        boolean success = Build.VERSION.SDK_INT >= 28 && applyDynamics(player, config, getChannelCount(player));
        if (!success) releaseDynamics();
        return success;
    }

    void release() {
        releaseDynamics();
    }

    private static float getCutoffFrequencyHz(int index) {
        int lastIndex = AudioEffectBands.STANDARD.getCount() - 1;
        if (index < 0 || index > lastIndex) throw new IndexOutOfBoundsException();
        return index == lastIndex ? MAX_CUTOFF_FREQUENCY_HZ : getMidpointFrequencyHz(index);
    }

    private static float getMidpointFrequencyHz(int index) {
        double current = AudioEffectBands.STANDARD.getCenterFrequency(index) / 1000.0;
        double next = AudioEffectBands.STANDARD.getCenterFrequency(index + 1) / 1000.0;
        return (float) Math.sqrt(current * next);
    }

    @RequiresApi(28)
    private boolean applyDynamics(ExoPlayer player, AudioEffectConfig config, int channelCount) {
        return channelCount > 0 && applyConfig(player, config, channelCount);
    }

    @RequiresApi(28)
    private boolean applyConfig(ExoPlayer player, AudioEffectConfig config, int channelCount) {
        boolean limiter = config.shouldLimitOutput(channelCount);
        if (config.hasBands() || limiter) return applySession(player, config.getLevels(), limiter, channelCount);
        releaseDynamics();
        return true;
    }

    @RequiresApi(28)
    private boolean applySession(ExoPlayer player, short[] levels, boolean limiter, int channelCount) {
        int sessionId = getSessionId(player);
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return false;
        ensureDynamics(sessionId, channelCount);
        return dynamics != null && dynamics.apply(levels, limiter);
    }

    @RequiresApi(28)
    private void ensureDynamics(int sessionId, int channelCount) {
        if (dynamics == null || !dynamics.matches(sessionId, channelCount)) {
            releaseDynamics();
            dynamics = DynamicsController.create(sessionId, channelCount);
        }
    }

    private int getSessionId(ExoPlayer player) {
        int sessionId = player.getAudioSessionId();
        return sessionId == C.AUDIO_SESSION_ID_UNSET ? createAudioSessionId(player) : sessionId;
    }

    private int createAudioSessionId(ExoPlayer player) {
        try {
            return createAndSetAudioSessionId(player);
        } catch (RuntimeException ignored) {
            return C.AUDIO_SESSION_ID_UNSET;
        }
    }

    private int createAndSetAudioSessionId(ExoPlayer player) {
        int sessionId = Util.generateAudioSessionIdV21(App.get());
        player.setAudioSessionId(sessionId);
        return sessionId;
    }

    private int getChannelCount(ExoPlayer player) {
        Format format = player.getAudioFormat();
        return format == null ? Format.NO_VALUE : format.channelCount;
    }

    private void releaseDynamics() {
        if (Build.VERSION.SDK_INT >= 28 && dynamics != null) dynamics.release();
        dynamics = null;
    }

    @RequiresApi(28)
    private static final class DynamicsController {

        private static final int BAND_COUNT = AudioEffectBands.STANDARD.getCount();

        private final DynamicsProcessing effect;
        private final int sessionId;
        private final int channelCount;
        private short[] levels = new short[BAND_COUNT];
        private boolean limiter;

        static @Nullable DynamicsController create(int sessionId, int channelCount) {
            try {
                return createController(sessionId, channelCount);
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private static DynamicsController createController(int sessionId, int channelCount) {
            DynamicsProcessing effect = new DynamicsProcessing(0, sessionId, createConfig(channelCount));
            effect.setEnabled(true);
            return new DynamicsController(effect, sessionId, channelCount);
        }

        private static DynamicsProcessing.Config createConfig(int channelCount) {
            return new DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, channelCount, false, 0, false, 0, true, BAND_COUNT, true).setPostEqAllChannelsTo(createEqualizer(new short[BAND_COUNT])).setLimiterAllChannelsTo(createLimiter(false)).build();
        }

        private DynamicsController(DynamicsProcessing effect, int sessionId, int channelCount) {
            this.effect = effect;
            this.sessionId = sessionId;
            this.channelCount = channelCount;
        }

        boolean matches(int sessionId, int channelCount) {
            return this.sessionId == sessionId && this.channelCount == channelCount;
        }

        boolean apply(short[] sourceLevels, boolean limiter) {
            short[] targetLevels = Arrays.copyOf(sourceLevels, BAND_COUNT);
            boolean success = applyChanges(targetLevels, limiter);
            if (success) setState(targetLevels, limiter);
            return success;
        }

        private boolean applyChanges(short[] targetLevels, boolean limiter) {
            try {
                applyBands(targetLevels);
                applyLimiter(limiter);
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private void applyBands(short[] targetLevels) {
            for (int index = 0; index < BAND_COUNT; index++) {
                if (levels[index] != targetLevels[index]) effect.setPostEqBandAllChannelsTo(index, createBand(index, targetLevels[index]));
            }
        }

        private void applyLimiter(boolean limiter) {
            if (this.limiter != limiter) effect.setLimiterAllChannelsTo(createLimiter(limiter));
        }

        private void setState(short[] levels, boolean limiter) {
            this.levels = levels;
            this.limiter = limiter;
        }

        void release() {
            try {
                effect.release();
            } catch (RuntimeException ignored) {
            }
        }

        private static DynamicsProcessing.Eq createEqualizer(short[] levels) {
            DynamicsProcessing.Eq equalizer = new DynamicsProcessing.Eq(true, true, BAND_COUNT);
            for (int i = 0; i < BAND_COUNT; i++) equalizer.setBand(i, createBand(i, levels[i]));
            return equalizer;
        }

        private static DynamicsProcessing.EqBand createBand(int index, short level) {
            return new DynamicsProcessing.EqBand(true, getCutoffFrequencyHz(index), level / 100.0f);
        }

        private static DynamicsProcessing.Limiter createLimiter(boolean enabled) {
            return new DynamicsProcessing.Limiter(true, enabled, 0, 1.0f, 60.0f, 20.0f, -0.2f, 0.0f);
        }
    }
}
