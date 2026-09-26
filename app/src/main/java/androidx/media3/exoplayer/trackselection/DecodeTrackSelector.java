package androidx.media3.exoplayer.trackselection;

import android.content.Context;

/**
 * Compatibility selector for the older FM source API.
 * WebHTV's pinned Media3 no longer exposes the old DecodeTrackSelector class.
 */
public class DecodeTrackSelector extends DefaultTrackSelector {

    private int audioDecode;
    private int videoDecode;

    public DecodeTrackSelector(Context context) {
        super(context);
    }

    public void setRendererDecodePreferences(int audioDecode, int videoDecode) {
        this.audioDecode = audioDecode;
        this.videoDecode = videoDecode;
        invalidate();
    }

    public int getAudioDecodePreference() {
        return audioDecode;
    }

    public int getVideoDecodePreference() {
        return videoDecode;
    }
}
