package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.PlaybackAutoContext.Confidence;
import com.fongmi.android.tv.player.PlaybackAutoContext.ManifestFacts;
import com.fongmi.android.tv.player.PlaybackAutoContext.ManifestKind;
import com.fongmi.android.tv.player.PlaybackAutoContext.Protocol;
import com.fongmi.android.tv.player.PlaybackAutoContext.RangeSupport;
import com.fongmi.android.tv.player.PlaybackAutoContext.StreamKind;
import com.fongmi.android.tv.player.PlaybackAutoContext.TransferUnit;
import com.fongmi.android.tv.player.PlaybackResourceClassifier.Classification;
import com.fongmi.android.tv.player.PlaybackResourceClassifier.Evidence;
import com.fongmi.android.tv.player.PlaybackResourceClassifier.ValueSource;

import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * 播放资源识别的纯 JVM 回归网。识别结果决定后续档位选择，一旦把 HLS 误判成
 * 点播 MP4、或把 LL-HLS 当成普通直播，播放参数会整体走偏，且这类问题只在
 * 特定源上复现。这里锁的是「输入事实 → 分类结论」的映射，不测 Android 播放器。
 */
public class PlaybackResourceClassifierTest {

    private static final String REMOTE = "https://cdn.example.com/";

    private record RequestCase(String label, String uri, String mime, String format,
                               Protocol protocol, StreamKind stream, Confidence confidence) {
    }

    /** URI 优先于 MIME / format；冲突时宁可 UNKNOWN，不猜协议 */
    @Test
    public void requestFactsMapToProtocol() {
        RequestCase[] cases = {
                new RequestCase("hls uri", REMOTE + "live.m3u8", null, null, Protocol.HLS, StreamKind.UNKNOWN, Confidence.MEDIUM),
                new RequestCase("hls uri with query", REMOTE + "live.m3u8?token=abc", null, null, Protocol.HLS, StreamKind.UNKNOWN, Confidence.MEDIUM),
                new RequestCase("hls uri uppercase", REMOTE + "LIVE.M3U8", null, null, Protocol.HLS, StreamKind.UNKNOWN, Confidence.MEDIUM),
                new RequestCase("dash uri", REMOTE + "a.mpd", null, null, Protocol.DASH, StreamKind.UNKNOWN, Confidence.MEDIUM),
                new RequestCase("dash token", REMOTE + "a", null, "dash", Protocol.DASH, StreamKind.UNKNOWN, Confidence.HIGH),
                new RequestCase("mime wins when uri silent", REMOTE + "a", "application/vnd.apple.mpegurl; charset=utf-8", null, Protocol.HLS, StreamKind.UNKNOWN, Confidence.HIGH),
                new RequestCase("format agrees with uri", REMOTE + "a.m3u8", null, "hls", Protocol.HLS, StreamKind.UNKNOWN, Confidence.HIGH),
                new RequestCase("progressive mp4", REMOTE + "movie.mp4", null, null, Protocol.PROGRESSIVE_HTTP, StreamKind.UNKNOWN, Confidence.MEDIUM),
                new RequestCase("progressive mkv with query", REMOTE + "movie.mkv?x=1", null, null, Protocol.PROGRESSIVE_HTTP, StreamKind.UNKNOWN, Confidence.MEDIUM),
                new RequestCase("progressive mime", REMOTE + "a", "video/x-matroska", null, Protocol.PROGRESSIVE_HTTP, StreamKind.UNKNOWN, Confidence.HIGH),
                new RequestCase("local file", "file:///sdcard/movie.mp4", null, null, Protocol.LOCAL, StreamKind.UNKNOWN, Confidence.MEDIUM),
                new RequestCase("rtsp is live", "rtsp://cam.example.com/stream", null, null, Protocol.RTSP, StreamKind.LIVE, Confidence.MEDIUM),
                new RequestCase("rtmp is live", "rtmp://live.example.com/app/key", null, null, Protocol.RTMP, StreamKind.LIVE, Confidence.MEDIUM),
                new RequestCase("conflicting hints stay unknown", REMOTE + "a.m3u8", "application/dash+xml", null, Protocol.UNKNOWN, StreamKind.UNKNOWN, Confidence.UNKNOWN),
                new RequestCase("no fact at all", null, null, null, Protocol.UNKNOWN, StreamKind.UNKNOWN, Confidence.UNKNOWN),
        };
        for (RequestCase item : cases) {
            Classification result = PlaybackResourceClassifier.classifyRequest(item.uri(), item.mime(), item.format());
            assertEquals(item.label() + " protocol", item.protocol(), result.protocol());
            assertEquals(item.label() + " stream", item.stream(), result.streamKind());
            assertEquals(item.label() + " confidence", item.confidence(), result.confidence());
            if (item.protocol() == Protocol.UNKNOWN && item.mime() != null && item.uri() != null) {
                assertTrue(item.label() + " conflict evidence", result.evidence().contains(Evidence.CONFLICT));
            }
        }
    }

    /** 段传输与连续传输不能混：transfer 随协议走 */
    @Test
    public void transferUnitFollowsProtocol() {
        assertEquals(TransferUnit.SEGMENT,
                PlaybackResourceClassifier.classifyRequest(REMOTE + "a.m3u8", null, null).transferUnit());
        assertEquals(TransferUnit.SEGMENT,
                PlaybackResourceClassifier.classifyRequest(REMOTE + "a.mpd", null, null).transferUnit());
        assertEquals(TransferUnit.CONTINUOUS,
                PlaybackResourceClassifier.classifyRequest(REMOTE + "a.mp4", null, null).transferUnit());
        assertEquals(TransferUnit.UNKNOWN,
                PlaybackResourceClassifier.classifyRequest(null, null, null).transferUnit());
    }

    private record RangeCase(String label, Map<String, List<String>> headers, RangeSupport expected) {
    }

    /** Range 只看响应头，不做 DNS、不猜；非法 Content-Range 必须判为不支持 */
    @Test
    public void rangeHeadersAreParsedConservatively() {
        RangeCase[] cases = {
                new RangeCase("accept bytes", Map.of("Accept-Ranges", List.of("bytes")), RangeSupport.SUPPORTED),
                new RangeCase("accept bytes case insensitive", Map.of("accept-ranges", List.of(" BYTES ")), RangeSupport.SUPPORTED),
                new RangeCase("accept none", Map.of("Accept-Ranges", List.of("none")), RangeSupport.UNSUPPORTED),
                new RangeCase("content range", Map.of("Content-Range", List.of("bytes 0-99/100")), RangeSupport.SUPPORTED),
                new RangeCase("content range wildcard total", Map.of("Content-Range", List.of("bytes 0-99/*")), RangeSupport.SUPPORTED),
                new RangeCase("reversed content range", Map.of("Content-Range", List.of("bytes 99-0/100")), RangeSupport.UNSUPPORTED),
                new RangeCase("malformed content range", Map.of("Content-Range", List.of("bytes abc-def/100")), RangeSupport.UNSUPPORTED),
                new RangeCase("irrelevant header", Map.of("X-Test", List.of("1")), RangeSupport.UNKNOWN),
                new RangeCase("empty headers", Map.of(), RangeSupport.UNKNOWN),
        };
        for (RangeCase item : cases) {
            Classification result = PlaybackResourceClassifier.classifyRequest(
                    REMOTE + "a.mp4", null, null, item.headers());
            assertEquals(item.label(), item.expected(), result.rangeSupport());
            if (item.expected() != RangeSupport.UNKNOWN) {
                assertTrue(item.label() + " evidence", result.evidence().contains(Evidence.HTTP_HEADERS));
            }
        }
    }

    private static final String HLS_MASTER = "#EXTM3U\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720\n720.m3u8\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080\n1080.m3u8\n";

    private static final String HLS_VOD = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-VERSION:3\n"
            + "#EXTINF:4.0,\nseg0.ts\n#EXTINF:4.0,\nseg1.ts\n#EXT-X-ENDLIST\n";

    private static final String HLS_LIVE = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n"
            + "#EXTINF:4.0,\nseg0.ts\n#EXTINF:4.0,\nseg1.ts\n";

    private static final String HLS_LL = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-PART-INF:PART-TARGET=0.5\n"
            + "#EXT-X-PART:DURATION=0.5,URI=\"p0.m4s\"\n"
            + "#EXTINF:4.0,\nseg0.m4s\n";

    private static final String HLS_VOD_TYPE_ONLY = "#EXTM3U\n#EXT-X-PLAYLIST-TYPE:VOD\n"
            + "#EXT-X-TARGETDURATION:4\n#EXTINF:4.0,\nseg0.ts\n";

    private static final String HLS_BYTE_RANGE = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-BYTERANGE:1000@0\n#EXTINF:4.0,\nseg0.ts\n#EXT-X-ENDLIST\n";

    /** 播放单类型决定点播/直播/低延迟，这是自适应档位的输入 */
    @Test
    public void hlsManifestDecidesStreamKind() {
        Classification master = PlaybackResourceClassifier.classifyHls(REMOTE + "master.m3u8", null, HLS_MASTER);
        assertEquals(ManifestKind.HLS_MASTER, master.manifest().kind());
        assertEquals(StreamKind.UNKNOWN, master.streamKind());
        assertEquals(TransferUnit.SEGMENT, master.transferUnit());
        assertEquals(Integer.valueOf(2), master.manifest().variantCount());
        assertTrue(master.actualManifest());
        assertEquals(ValueSource.MANIFEST, master.source());
        assertTrue(master.evidence().contains(Evidence.HLS_TAG));

        Classification vod = PlaybackResourceClassifier.classifyHls(REMOTE + "vod.m3u8", null, HLS_VOD);
        assertEquals(ManifestKind.HLS_MEDIA, vod.manifest().kind());
        assertEquals(StreamKind.VOD, vod.streamKind());
        assertEquals(TransferUnit.SEGMENT, vod.transferUnit());
        assertEquals(Long.valueOf(4000), vod.manifest().targetDurationMs());
        assertEquals(Boolean.TRUE, vod.manifest().endList());

        Classification live = PlaybackResourceClassifier.classifyHls(REMOTE + "live.m3u8", null, HLS_LIVE);
        assertEquals(StreamKind.LIVE, live.streamKind());
        assertEquals(Boolean.FALSE, live.manifest().endList());

        Classification lowLatency = PlaybackResourceClassifier.classifyHls(REMOTE + "ll.m3u8", null, HLS_LL);
        assertEquals(StreamKind.LOW_LATENCY_LIVE, lowLatency.streamKind());
        assertEquals(TransferUnit.PART, lowLatency.transferUnit());
        assertEquals(Long.valueOf(500), lowLatency.manifest().partDurationMs());
        assertEquals(Boolean.TRUE, lowLatency.manifest().lowLatency());

        /** PLAYLIST-TYPE:VOD 但缺 ENDLIST：按设计保持 UNKNOWN，不猜成点播 */
        assertEquals(StreamKind.UNKNOWN,
                PlaybackResourceClassifier.classifyHls(REMOTE + "type.m3u8", null, HLS_VOD_TYPE_ONLY).streamKind());

        assertEquals(RangeSupport.SUPPORTED,
                PlaybackResourceClassifier.classifyHls(REMOTE + "br.m3u8", null, HLS_BYTE_RANGE).rangeSupport());
    }

    @Test
    public void dashManifestDecidesStreamKind() {
        Classification dynamic = PlaybackResourceClassifier.classifyDash(REMOTE + "a.mpd", null,
                "<MPD type=\"dynamic\" minimumUpdatePeriod=\"PT2S\"></MPD>");
        assertEquals(Protocol.DASH, dynamic.protocol());
        assertEquals(ManifestKind.DASH_DYNAMIC, dynamic.manifest().kind());
        assertEquals(StreamKind.LIVE, dynamic.streamKind());
        assertEquals(TransferUnit.SEGMENT, dynamic.transferUnit());

        Classification staticMpd = PlaybackResourceClassifier.classifyDash(REMOTE + "a.mpd", null,
                "<MPD type=\"static\"></MPD>");
        assertEquals(ManifestKind.DASH_STATIC, staticMpd.manifest().kind());
        assertEquals(StreamKind.VOD, staticMpd.streamKind());

        Classification lowLatency = PlaybackResourceClassifier.classifyDash(REMOTE + "a.mpd", null,
                "<MPD type=\"dynamic\" availabilityTimeOffset=\"1.5\"></MPD>");
        assertEquals(StreamKind.LOW_LATENCY_LIVE, lowLatency.streamKind());
        assertEquals(TransferUnit.PART, lowLatency.transferUnit());

        Classification ranged = PlaybackResourceClassifier.classifyDash(REMOTE + "a.mpd", null,
                "<MPD type=\"static\"><SegmentBase indexRange=\"0-999\"/></MPD>");
        assertEquals(RangeSupport.SUPPORTED, ranged.rangeSupport());
    }

    @Test
    public void playerCallbackOnlyFillsUnknownStream() {
        Classification base = PlaybackResourceClassifier.classifyRequest(REMOTE + "plain", null, null);
        assertEquals(StreamKind.UNKNOWN, base.streamKind());

        Classification live = PlaybackResourceClassifier.observePlayer(base, true, 0);
        assertEquals(StreamKind.LIVE, live.streamKind());
        assertEquals(ValueSource.PLAYER_CALLBACK, live.source());
        assertTrue(live.evidence().contains(Evidence.PLAYER_CALLBACK));

        Classification vod = PlaybackResourceClassifier.observePlayer(base, false, 120_000L);
        assertEquals(StreamKind.VOD, vod.streamKind());

        assertSame("无有效时长时保持原对象", base,
                PlaybackResourceClassifier.observePlayer(base, false, 0));

        Classification manifest = PlaybackResourceClassifier.classifyHls(REMOTE + "vod.m3u8", null, HLS_VOD);
        assertSame("播放单结论优先于播放器回调", manifest,
                PlaybackResourceClassifier.observePlayer(manifest, true, 0));

        assertNotNull(PlaybackResourceClassifier.observePlayer(null, false, 0));
    }

    @Test
    public void mergeKeepsStrongerFactsAndSegmentedProtocol() {
        Classification hls = PlaybackResourceClassifier.classifyRequest(REMOTE + "a.m3u8", null, null);
        Classification unknown = PlaybackResourceClassifier.classifyRequest(null, null, null);

        Classification retained = PlaybackResourceClassifier.merge(hls, unknown);
        assertEquals(Protocol.HLS, retained.protocol());
        assertEquals(TransferUnit.SEGMENT, retained.transferUnit());
        assertEquals(ValueSource.PLAYBACK_REQUEST, retained.source());

        Classification upgraded = PlaybackResourceClassifier.merge(unknown, hls);
        assertEquals(Protocol.HLS, upgraded.protocol());
        assertEquals(ValueSource.PLAYBACK_REQUEST, upgraded.source());

        /** 关键回归：HLS 播放单之后 DataSource 报一个 .mp4 分片，不能把整段协议改写成 PROGRESSIVE_HTTP */
        Classification segment = PlaybackResourceClassifier.classifyDataSource(
                REMOTE + "seg.mp4", REMOTE + "seg.mp4", null, Map.of());
        assertEquals(Protocol.PROGRESSIVE_HTTP, segment.protocol());
        Classification merged = PlaybackResourceClassifier.merge(hls, segment);
        assertEquals(Protocol.HLS, merged.protocol());
        assertEquals(TransferUnit.SEGMENT, merged.transferUnit());

        Classification manifest = PlaybackResourceClassifier.classifyHls(REMOTE + "vod.m3u8", null, HLS_VOD);
        Classification mergedManifest = PlaybackResourceClassifier.merge(manifest, unknown);
        assertTrue(mergedManifest.actualManifest());
        assertEquals(ManifestKind.HLS_MEDIA, mergedManifest.manifest().kind());
        assertEquals(ValueSource.MANIFEST, mergedManifest.source());
    }

    @Test
    public void classificationRecordNormalizesMissingFacts() {
        Classification blank = new Classification(null, null, null, null, null, null, null,
                null, null, null, null, false, -5);
        assertEquals(Protocol.UNKNOWN, blank.protocol());
        assertEquals(StreamKind.UNKNOWN, blank.streamKind());
        assertEquals(RangeSupport.UNKNOWN, blank.rangeSupport());
        assertEquals(TransferUnit.UNKNOWN, blank.transferUnit());
        assertEquals(ManifestKind.UNKNOWN, blank.manifest().kind());
        assertEquals(Confidence.UNKNOWN, blank.confidence());
        assertEquals(ValueSource.UNKNOWN, blank.source());
        assertTrue(blank.evidence().isEmpty());
        assertEquals(-1, blank.observedAtElapsedMs());
        assertFalse(blank.known());

        assertTrue(PlaybackResourceClassifier.classifyRequest(REMOTE + "a.m3u8", null, null).known());
        assertFalse(PlaybackResourceClassifier.classifyRequest(null, null, null).known());
    }

    @Test
    public void manifestTtlStaysWithinBounds() {
        assertEquals(30_000L, PlaybackResourceClassifier.manifestTtlMs(null));
        assertEquals(12_000L, PlaybackResourceClassifier.manifestTtlMs(
                new ManifestFacts(ManifestKind.HLS_MEDIA, false, 4_000L, null, null, null, false, false)));
        assertEquals(60_000L, PlaybackResourceClassifier.manifestTtlMs(
                new ManifestFacts(ManifestKind.HLS_MEDIA, false, 30_000L, null, null, null, false, false)));
        assertEquals(5_000L, PlaybackResourceClassifier.manifestTtlMs(
                new ManifestFacts(ManifestKind.HLS_MEDIA, false, null, 500L, null, null, false, true)));
    }
}
