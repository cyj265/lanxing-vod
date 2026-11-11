package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.PlaybackAutoContext.DecodeMode;
import com.fongmi.android.tv.player.PlaybackAutoContext.HdrType;
import com.fongmi.android.tv.player.PlaybackAutoContext.Kernel;
import com.fongmi.android.tv.player.PlaybackAutoContext.PathKind;
import com.fongmi.android.tv.player.PlaybackAutoContext.Protocol;
import com.fongmi.android.tv.player.PlaybackAutoContext.StreamKind;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.Arm;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.EnrollmentResolution;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.EnrollmentStatus;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.GroupKey;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.GroupReason;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.GroupResolution;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.RawEnrollment;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.VideoMimeClass;

import org.junit.Test;

/**
 * 播放档位 A/B 的准入与分组策略回归网。这里锁的是「配置/设备事实是否被保守地
 * 拒绝」——出错的代价是拿错档位或把设备标识带进分组，而不是简单显示异常。
 */
public class PlaybackProfileAbPolicyTest {

    /** 24 位小写十六进制，满足 PlaybackProfileAbIdentity.validDigest */
    private static final String DIGEST = "0123456789abcdef01234567";
    private static final String OTHER_DIGEST = "89abcdef0123456701234567";

    private record ArmCase(String label, int profile, Arm expected) {
    }

    /** PROFILE_AUTO=4 / PROFILE_RECOMMENDED=0，其余档位不参与实验 */
    @Test
    public void armForProfileOnlyAdmitsTwoProfiles() {
        ArmCase[] cases = {
                new ArmCase("auto", 4, Arm.AUTO),
                new ArmCase("recommended", 0, Arm.RECOMMENDED),
                new ArmCase("compatible", 1, null),
                new ArmCase("custom", 2, null),
                new ArmCase("lightweight", 3, null),
                new ArmCase("invalid", -1, null),
        };
        for (ArmCase item : cases) {
            assertEquals(item.label(), item.expected(), PlaybackProfileAbPolicy.armForProfile(item.profile()));
        }
    }

    private record EnrollmentCase(String label, Object version, Object digest, Object enabled,
                                  String currentDigest, EnrollmentStatus status,
                                  boolean sourceValid, boolean active, boolean failClosed) {
    }

    /**  enrollment 必须 fail-closed：损坏、未来 schema、设备不匹配一律不启用实验 */
    @Test
    public void enrollmentFailsClosedOnAnyDoubt() {
        EnrollmentCase[] cases = {
                new EnrollmentCase("missing record", null, null, null, DIGEST,
                        EnrollmentStatus.NOT_ENROLLED, true, false, false),
                new EnrollmentCase("current enabled", 1, DIGEST, true, DIGEST,
                        EnrollmentStatus.CURRENT, true, true, false),
                new EnrollmentCase("current disabled", 1, DIGEST, false, DIGEST,
                        EnrollmentStatus.CURRENT, true, false, false),
                new EnrollmentCase("future schema", 2, DIGEST, true, DIGEST,
                        EnrollmentStatus.FUTURE_SCHEMA, false, false, true),
                new EnrollmentCase("negative version", -1, DIGEST, true, DIGEST,
                        EnrollmentStatus.CORRUPT, false, false, true),
                new EnrollmentCase("old schema", 0, DIGEST, true, DIGEST,
                        EnrollmentStatus.CORRUPT, false, false, true),
                new EnrollmentCase("string version", "1", DIGEST, true, DIGEST,
                        EnrollmentStatus.CORRUPT, false, false, true),
                new EnrollmentCase("non hex digest", 1, "zzzzzzzzzzzzzzzzzzzzzzzz", true, DIGEST,
                        EnrollmentStatus.CORRUPT, false, false, true),
                new EnrollmentCase("short digest", 1, "abcdef", true, DIGEST,
                        EnrollmentStatus.CORRUPT, false, false, true),
                new EnrollmentCase("enabled not boolean", 1, DIGEST, "true", DIGEST,
                        EnrollmentStatus.CORRUPT, false, false, true),
                new EnrollmentCase("stale device", 1, OTHER_DIGEST, true, DIGEST,
                        EnrollmentStatus.STALE_DEVICE, false, false, true),
                new EnrollmentCase("invalid current device", 1, DIGEST, true, "bad",
                        EnrollmentStatus.STALE_DEVICE, false, false, true),
        };
        for (EnrollmentCase item : cases) {
            EnrollmentResolution result = PlaybackProfileAbPolicy.resolveEnrollment(
                    new RawEnrollment(item.version(), item.digest(), item.enabled()), item.currentDigest());
            assertEquals(item.label() + " status", item.status(), result.status());
            assertEquals(item.label() + " sourceValid", item.sourceValid(), result.sourceValid());
            assertEquals(item.label() + " active", item.active(), result.active());
            assertEquals(item.label() + " failClosed", item.failClosed(), result.failClosed());
        }
        /** 缺失记录时返回 none()，不把设备标识带进结果 */
        assertTrue(PlaybackProfileAbPolicy.resolveEnrollment(null, DIGEST)
                .enrollment().deviceDigest().isEmpty());
    }

    private record MimeCase(String label, String mime, VideoMimeClass expected) {
    }

    /** 视频编码类别决定同组内是否可比；非 video/ 一律 UNKNOWN */
    @Test
    public void videoMimeClassifiesCodecs() {
        MimeCase[] cases = {
                new MimeCase("avc", "video/avc", VideoMimeClass.AVC),
                new MimeCase("h264", "video/h264", VideoMimeClass.AVC),
                new MimeCase("hevc", "video/hevc", VideoMimeClass.HEVC),
                new MimeCase("h265", "video/h265", VideoMimeClass.HEVC),
                new MimeCase("vp9", "video/x-vnd.on2.vp9", VideoMimeClass.VP9),
                new MimeCase("av01", "video/av01", VideoMimeClass.AV1),
                new MimeCase("dolby vision", "video/dolby-vision", VideoMimeClass.DOLBY_VISION),
                new MimeCase("other video", "video/mp4", VideoMimeClass.OTHER_VIDEO),
                new MimeCase("uppercase", "VIDEO/H264", VideoMimeClass.AVC),
                new MimeCase("audio", "audio/aac", VideoMimeClass.UNKNOWN),
                new MimeCase("null", null, VideoMimeClass.UNKNOWN),
                new MimeCase("empty", "", VideoMimeClass.UNKNOWN),
        };
        for (MimeCase item : cases) {
            assertEquals(item.label(), item.expected(), VideoMimeClass.fromMime(item.mime()));
        }
    }

    private static GroupKey key(Kernel kernel, DecodeMode mode, String decoder,
                                Protocol protocol, StreamKind stream, VideoMimeClass mime,
                                HdrType hdr, PathKind path) {
        return new GroupKey(DIGEST, kernel, mode, decoder, protocol, stream, mime, hdr, path);
    }

    private static GroupKey validKey() {
        return key(Kernel.EXO, DecodeMode.HARDWARE, DIGEST, Protocol.HLS,
                StreamKind.VOD, VideoMimeClass.AVC, HdrType.SDR, PathKind.REMOTE);
    }

    /** 分组键九个维度缺一不可；只有直播态才纳入直播延迟指标 */
    @Test
    public void groupKeyRequiresEveryDimension() {
        assertTrue(validKey().valid());
        assertFalse(validKey().live());

        GroupKey[] incomplete = {
                key(null, DecodeMode.HARDWARE, DIGEST, Protocol.HLS, StreamKind.VOD, VideoMimeClass.AVC, HdrType.SDR, PathKind.REMOTE),
                key(Kernel.EXO, null, DIGEST, Protocol.HLS, StreamKind.VOD, VideoMimeClass.AVC, HdrType.SDR, PathKind.REMOTE),
                key(Kernel.EXO, DecodeMode.HARDWARE, "bad", Protocol.HLS, StreamKind.VOD, VideoMimeClass.AVC, HdrType.SDR, PathKind.REMOTE),
                key(Kernel.EXO, DecodeMode.HARDWARE, DIGEST, null, StreamKind.VOD, VideoMimeClass.AVC, HdrType.SDR, PathKind.REMOTE),
                key(Kernel.EXO, DecodeMode.HARDWARE, DIGEST, Protocol.HLS, null, VideoMimeClass.AVC, HdrType.SDR, PathKind.REMOTE),
                key(Kernel.EXO, DecodeMode.HARDWARE, DIGEST, Protocol.HLS, StreamKind.VOD, null, HdrType.SDR, PathKind.REMOTE),
                key(Kernel.EXO, DecodeMode.HARDWARE, DIGEST, Protocol.HLS, StreamKind.VOD, VideoMimeClass.AVC, null, PathKind.REMOTE),
                key(Kernel.EXO, DecodeMode.HARDWARE, DIGEST, Protocol.HLS, StreamKind.VOD, VideoMimeClass.AVC, HdrType.SDR, null),
        };
        for (int i = 0; i < incomplete.length; i++) {
            assertFalse("dimension " + i + " must invalidate the key", incomplete[i].valid());
        }

        assertTrue(key(Kernel.EXO, DecodeMode.HARDWARE, DIGEST, Protocol.HLS,
                StreamKind.LIVE, VideoMimeClass.AVC, HdrType.SDR, PathKind.REMOTE).live());
        assertTrue(key(Kernel.EXO, DecodeMode.HARDWARE, DIGEST, Protocol.HLS,
                StreamKind.LOW_LATENCY_LIVE, VideoMimeClass.AVC, HdrType.SDR, PathKind.REMOTE).live());
    }

    @Test
    public void groupResolutionAndKernelDomainFailClosed() {
        GroupResolution invalid = GroupResolution.invalid(GroupReason.NO_SESSION);
        assertFalse(invalid.ready());
        assertNull(invalid.key());
        assertEquals(GroupReason.NO_SESSION, invalid.reason());

        assertEquals(GroupReason.NO_SESSION,
                PlaybackProfileAbPolicy.resolveGroup(null, DIGEST, 1000L).reason());
        /** 会话检查先于设备校验：无有效会话时不能泄露任何分组判定 */
        assertEquals(GroupReason.NO_SESSION,
                PlaybackProfileAbPolicy.resolveGroup(PlaybackAutoContext.empty(), "bad", 1000L).reason());

        assertEquals("EXO", PlaybackProfileAbPolicy.domainForKernel(Kernel.EXO).name());
        assertEquals("IJK", PlaybackProfileAbPolicy.domainForKernel(Kernel.IJK).name());
        assertNull(PlaybackProfileAbPolicy.domainForKernel(Kernel.UNKNOWN));
        assertNull(PlaybackProfileAbPolicy.domainForKernel(null));
    }
}
