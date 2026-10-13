package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * 版本发现逻辑单测。数据形态取自 GitHub / Gitee releases 接口的真实返回结构，
 * 重点锁死一件事：结果不得依赖列表顺序（两个源的返回顺序是相反的）。
 */
public class ReleaseSourceTest {

    private static JSONObject release(String tag, String... assetNames) throws Exception {
        JSONObject json = new JSONObject();
        json.put("tag_name", tag);
        json.put("body", "changelog of " + tag);
        JSONArray assets = new JSONArray();
        for (String name : assetNames) {
            JSONObject a = new JSONObject();
            a.put("name", name);
            a.put("browser_download_url", "https://example.com/" + name);
            assets.put(a);
        }
        if (assetNames.length > 0) json.put("assets", assets);
        return json;
    }

    private static String apk(String tag) {
        return "lanxing-arm64_v8a-" + tag + ".apk";
    }

    /** Gitee 真实行为：接口按创建时间升序返回（旧→新），必须返回版本号最大的一条 */
    @Test
    public void ascendingListPicksHighestVersion() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(release("v5.6.82", apk("v5.6.82")));
        arr.put(release("v5.6.83", apk("v5.6.83")));
        JSONObject best = ReleaseSource.pickLatest(arr, "5.6.81");
        assertNotNull(best);
        assertEquals("5.6.83", best.getString("version"));
    }

    /** GitHub 真实行为：接口按创建时间倒序返回（新→旧），结果必须与升序时一致 */
    @Test
    public void descendingListPicksSameVersion() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(release("v5.6.83", apk("v5.6.83")));
        arr.put(release("v5.6.82", apk("v5.6.82")));
        JSONObject best = ReleaseSource.pickLatest(arr, "5.6.81");
        assertNotNull(best);
        assertEquals("5.6.83", best.getString("version"));
    }

    /** 乱序 + 另一条线的包混在最前，也不能串线或被顺序带偏 */
    @Test
    public void shuffledListAcrossLinesStaysOnOwnLine() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(release("v5.4.45", apk("v5.4.45")));
        arr.put(release("v5.6.81", apk("v5.6.81")));
        arr.put(release("v5.6.83", apk("v5.6.83")));
        arr.put(release("v5.6.82", apk("v5.6.82")));
        JSONObject best = ReleaseSource.pickLatest(arr, "5.6.81");
        assertNotNull(best);
        assertEquals("5.6.83", best.getString("version"));
    }

    /** 最新一条是空壳（无 APK 附件）：判源不可用抛异常，绝不返回旧版本冒充最新 */
    @Test
    public void emptyShellOnNewestThrowsInsteadOfOlder() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(release("v5.6.83"));
        arr.put(release("v5.6.82", apk("v5.6.82")));
        try {
            JSONObject best = ReleaseSource.pickLatest(arr, "5.6.81");
            fail("空壳最新条目应判源不可用，实际返回：" + best);
        } catch (IllegalStateException expected) {
            // 正确：交由调用方换源兜底
        }
    }

    /** 只有更旧的带附件、更新的没附件，也仍应判不可用（线上 5.6.82 就是那个未修复的旧构建） */
    @Test
    public void newerShellBlocksDowngradeToUnfixedBuild() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(release("v5.6.82", apk("v5.6.82")));
        arr.put(release("v5.6.83"));
        try {
            JSONObject best = ReleaseSource.pickLatest(arr, "5.6.81");
            fail("不应把 5.6.82 冒充最新引导用户，实际返回：" + best);
        } catch (IllegalStateException expected) {
            // 正确：不降级引导
        }
    }

    /** 手机版优先：电视版包排在前面也不能选它（电视版已停发） */
    @Test
    public void mobileApkWinsOverTvApk() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(release("v5.6.83", "lanxing-tv-v5.6.83.apk", apk("v5.6.83")));
        JSONObject best = ReleaseSource.pickLatest(arr, "5.6.82");
        assertNotNull(best);
        assertEquals("https://example.com/" + apk("v5.6.83"), best.getString("apk_url"));
    }

    /** 主版本不同（本机 6.x，列表全 5.x）：本线无任何 release，返回 null 交下一个源 */
    @Test
    public void otherLineReturnsNull() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(release("v5.4.45", apk("v5.4.45")));
        arr.put(release("v5.4.44", apk("v5.4.44")));
        assertNull(ReleaseSource.pickLatest(arr, "6.0.0"));
    }

    /**
     * 同主版本即同线：5.4.44 的用户应拿到列表里版本号最大的 5.6.83。
     * 这是既有设计意图（isNewer 允许跨次/修正号升级），锁住它防止被误改成「按系列过滤」——
     * 那会把 5.4.x 老用户永久挡在 5.6.x 门外。
     */
    @Test
    public void crossMinorUpgradeIsAllowed() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(release("v5.4.45", apk("v5.4.45")));
        arr.put(release("v5.6.83", apk("v5.6.83")));
        JSONObject best = ReleaseSource.pickLatest(arr, "5.4.44");
        assertNotNull(best);
        assertEquals("5.6.83", best.getString("version"));
    }
}
