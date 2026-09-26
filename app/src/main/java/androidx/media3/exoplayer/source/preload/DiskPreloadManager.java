package androidx.media3.exoplayer.source.preload;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;

/**
 * Compatibility facade for the older FM DiskPreloadManager API.
 * The WebHTV 5.6.0 Media3 fork uses a different preload path.
 * Normal playback is unaffected; disk-preload becomes a no-op.
 */
public final class DiskPreloadManager {

    public void start(ExoPlayer player, MediaItem item, Options options) {
    }

    public void release() {
    }

    public static final class Builder {
        public Builder(Cache cache, DataSource.Factory upstream, RenderersFactory renderersFactory) {
        }

        public Builder setPriorityTaskManager(PriorityTaskManager manager) {
            return this;
        }

        public DiskPreloadManager build() {
            return new DiskPreloadManager();
        }
    }

    public static final class Options {
        private final long durationMs;
        private final int maxThreads;

        private Options(long durationMs, int maxThreads) {
            this.durationMs = durationMs;
            this.maxThreads = maxThreads;
        }

        public long durationMs() {
            return durationMs;
        }

        public int maxThreads() {
            return maxThreads;
        }

        public static OptionsBuilder builder() {
            return new OptionsBuilder();
        }
    }

    public static final class OptionsBuilder {
        private long durationMs;
        private int maxThreads = 1;

        public OptionsBuilder setDurationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public OptionsBuilder setMaxThreads(int maxThreads) {
            this.maxThreads = maxThreads;
            return this;
        }

        public Options build() {
            return new Options(durationMs, maxThreads);
        }
    }
}
