package com.fongmi.android.tv.player.effect.video;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;

final class ColorToneAdjustEffect implements GlEffect {

    private static final float LUMA_R = 0.2126f;
    private static final float LUMA_G = 0.7152f;
    private static final float LUMA_B = 0.0722f;

    private volatile Parameters parameters;

    ColorToneAdjustEffect() {
        setProfile(VideoEffectProfile.off());
    }

    private static float[] createColorMatrix(VideoEffectProfile profile) {
        float saturation = profile.saturation;
        float contrast = profile.contrast;
        float brightness = profile.brightness;
        float redGain = profile.redGain();
        float blueGain = profile.blueGain();
        float invSat = 1.0f - saturation;
        float offset = brightness + 0.5f * (1.0f - contrast);
        float rr = (LUMA_R * invSat + saturation) * contrast * redGain;
        float rg = (LUMA_G * invSat) * contrast * redGain;
        float rb = (LUMA_B * invSat) * contrast * redGain;
        float gr = (LUMA_R * invSat) * contrast;
        float gg = (LUMA_G * invSat + saturation) * contrast;
        float gb = (LUMA_B * invSat) * contrast;
        float br = (LUMA_R * invSat) * contrast * blueGain;
        float bg = (LUMA_G * invSat) * contrast * blueGain;
        float bb = (LUMA_B * invSat + saturation) * contrast * blueGain;
        return new float[]{rr, gr, br, 0.0f, rg, gg, bg, 0.0f, rb, gb, bb, 0.0f, offset * redGain, offset, offset * blueGain, 1.0f};
    }

    void setProfile(VideoEffectProfile profile) {
        parameters = new Parameters(profile, createColorMatrix(profile));
    }

    Parameters getParameters() {
        return parameters;
    }

    @NonNull
    @Override
    public GlShaderProgram toGlShaderProgram(@NonNull Context context, boolean useHdr) throws VideoFrameProcessingException {
        return new ColorToneAdjustShaderProgram(useHdr, this);
    }

    @Override
    public boolean isNoOp(int inputWidth, int inputHeight) {
        return parameters.noOp;
    }

    static final class Parameters {

        final float[] colorMatrix;
        final float gamma;
        final float hue;
        final boolean noOp;

        Parameters(VideoEffectProfile profile, float[] colorMatrix) {
            this.colorMatrix = colorMatrix;
            this.gamma = profile.gamma;
            this.hue = profile.hue;
            this.noOp = profile.isColorNoOp() && profile.isToneNoOp();
        }
    }
}
