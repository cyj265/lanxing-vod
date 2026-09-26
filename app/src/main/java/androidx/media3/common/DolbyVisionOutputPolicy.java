package androidx.media3.common;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class DolbyVisionOutputPolicy {
    public static final int AUTO = 0;
    public static final int FORCE_SUPPORTED = 1;
    public static final int ASSUME_UNSUPPORTED = 2;

    @IntDef({AUTO, FORCE_SUPPORTED, ASSUME_UNSUPPORTED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {}

    private DolbyVisionOutputPolicy() {}
}
