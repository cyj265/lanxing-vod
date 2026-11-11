package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.PlaybackAutoContext.DecodeMode;
import com.fongmi.android.tv.player.PlaybackAutoContext.HdrType;
import com.fongmi.android.tv.player.PlaybackAutoContext.Kernel;
import com.fongmi.android.tv.player.PlaybackAutoContext.PathKind;
import com.fongmi.android.tv.player.PlaybackAutoContext.Protocol;
import com.fongmi.android.tv.player.PlaybackAutoContext.StreamKind;
import com.fongmi.android.tv.player.PlaybackProfileAbAcceptancePolicy.Report;
import com.fongmi.android.tv.player.PlaybackProfileAbAcceptancePolicy.Status;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.Arm;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.GroupKey;
import com.fongmi.android.tv.player.PlaybackProfileAbPolicy.VideoMimeClass;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * A/B 结论的准入网：样本不足或分组无效时一律不得进入「可合并」状态。
 * 这里不构造完整指标样本，只锁准入边界，避免把阈值回归混进指标计算用例。
 */
public class PlaybackProfileAbAcceptancePolicyTest {

    private static final String DIGEST = "0123456789abcdef01234567";

    private static GroupKey validKey() {
        return new GroupKey(DIGEST, Kernel.EXO, DecodeMode.HARDWARE, DIGEST, Protocol.HLS,
                StreamKind.VOD, VideoMimeClass.AVC, HdrType.SDR, PathKind.REMOTE);
    }

    private static PlaybackProfileAbStore.Sample sample(GroupKey key, Arm arm) {
        return new PlaybackProfileAbStore.Sample(1, key, arm, 100L, 0, 0L, 60_000L,
                0L, 0L, 0L, false, 1L);
    }

    private static PlaybackProfileAbStore.Snapshot snapshot(GroupKey key, int automatic, int recommended) {
        List<PlaybackProfileAbStore.Sample> a = Collections.nCopies(automatic, sample(key, Arm.AUTO));
        List<PlaybackProfileAbStore.Sample> r = Collections.nCopies(recommended, sample(key, Arm.RECOMMENDED));
        return new PlaybackProfileAbStore.Snapshot(List.of(
                new PlaybackProfileAbStore.GroupSamples(key, a, r)));
    }

    @Test
    public void noDataNeverBecomesMergeEligible() {
        for (Report report : List.of(
                PlaybackProfileAbAcceptancePolicy.evaluate(null),
                PlaybackProfileAbAcceptancePolicy.evaluate(new PlaybackProfileAbStore.Snapshot(List.of())),
                PlaybackProfileAbAcceptancePolicy.evaluate(new PlaybackProfileAbStore.Snapshot(null)))) {
            assertEquals(Status.NO_DATA, report.status());
            assertEquals(0, report.totalGroups());
            assertFalse(report.mergeEligible());
            assertFalse(report.comparisonEligible());
        }
    }

    @Test
    public void comparisonArmNormalizesAutoToRecommended() {
        PlaybackProfileAbStore.Snapshot empty = new PlaybackProfileAbStore.Snapshot(List.of());
        assertEquals(Arm.RECOMMENDED,
                PlaybackProfileAbAcceptancePolicy.evaluate(empty, null).comparisonArm());
        assertEquals(Arm.RECOMMENDED,
                PlaybackProfileAbAcceptancePolicy.evaluate(empty, Arm.AUTO).comparisonArm());
        assertEquals(Arm.LIGHTWEIGHT,
                PlaybackProfileAbAcceptancePolicy.evaluate(empty, Arm.LIGHTWEIGHT).comparisonArm());
    }

    private record SampleCase(String label, int automatic, int recommended, Status expected) {
    }

    /** 每臂至少 20 条；任何一臂不足都不得给出结论 */
    @Test
    public void insufficientSamplesBlockAcceptance() {
        SampleCase[] cases = {
                new SampleCase("empty arms", 0, 0, Status.INSUFFICIENT_SAMPLES),
                new SampleCase("one arm short", 19, 20, Status.INSUFFICIENT_SAMPLES),
                new SampleCase("other arm short", 20, 19, Status.INSUFFICIENT_SAMPLES),
                new SampleCase("both just short", 19, 19, Status.INSUFFICIENT_SAMPLES),
        };
        for (SampleCase item : cases) {
            Report report = PlaybackProfileAbAcceptancePolicy.evaluate(
                    snapshot(validKey(), item.automatic(), item.recommended()));
            assertEquals(item.label(), item.expected(), report.status());
            assertFalse(item.label(), report.mergeEligible());
        }
        assertEquals(20, PlaybackProfileAbAcceptancePolicy.MIN_SAMPLES_PER_ARM);
    }

    @Test
    public void invalidGroupIsNotEvaluated() {
        PlaybackProfileAbStore.Snapshot invalid = new PlaybackProfileAbStore.Snapshot(List.of(
                new PlaybackProfileAbStore.GroupSamples(null, List.of(), List.of())));
        Report report = PlaybackProfileAbAcceptancePolicy.evaluate(invalid);
        assertEquals(Status.INSUFFICIENT_SAMPLES, report.status());
        assertEquals(1, report.totalGroups());
        assertEquals(0, report.evaluableGroups());
        assertFalse(report.mergeEligible());
    }

    @Test
    public void reportRecordNormalizesNulls() {
        Report report = new Report(null, Arm.AUTO, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(Status.NO_DATA, report.status());
        assertEquals(Arm.RECOMMENDED, report.comparisonArm());
        assertTrue(report.groups().isEmpty());
    }
}
