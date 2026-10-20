package com.fongmi.android.tv;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ReleaseSource;
import com.fongmi.android.tv.utils.ReleaseTrust;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Updater implements Download.Callback, UpdateListener {

    // 更新逻辑：查 GitHub Releases（主源：直连不通再逐个换加速前缀），失败回退 Gitee Releases，
    // 用版本号(versionName)判断是否有新版本（分段数值比较，选版本交给 ReleaseSource）。
    // 注：构建号(BUILD_NUMBER)方案已废弃——安装包的 BUILD_NUMBER 取自 CI run_number，与线上
    //     release 的构建号恒相等，无法触发更新；改用版本号后，用户装到更高版本号才会提示更新。
    // 注意：不能用 /releases/latest —— 该接口默认排除 prerelease，而 dev 构建发布为预发布版会永远返回 404。
    // 用列表接口(含 prerelease)即可。per_page 取 10 而非 1：main(5.4.x) 与 dev(5.6.x) 共用同一仓库，
    // 只取 1 条会让 dev 线用户被引导去装另一条线的包。CI 的清理步骤已把 Release 压到 3 条以内，取 10 条足够覆盖。
    // ⚠️ 接口列表顺序不可信，且两个源并不一致（2026-10-13 实测本仓库）：
    // Gitee v5 releases 为创建时间升序——v5.6.82(10-12 19:46) 排在第 1 位、v5.6.83(10-13 00:03) 排在第 2 位，
    // 即旧的在前。而旧实现是「返回第一条命中」，走 Gitee 时拿到的正是本线最旧的一条：后果不是收不到更新，
    // 而是把 5.6.81 的用户引导去装 5.6.82（恰好是缺 Updater 修复的那个旧构建）。
    // 旧注释写的"列表按创建时间倒序，逐条往下找"只对 GitHub 成立，这个假设就是本次事故的源头。
    // 现在选版本一律交给 ReleaseSource.pickLatest 自己比版本号大小，不依赖任何接口的默认顺序。
    // GitHub 与 Gitee 的 API 返回结构（tag_name / assets[].browser_download_url）一致，解析逻辑完全复用。
    // 顺序上把 GitHub 放前面：它由 CI 直接发布、附件完整性由回读校验保证，Gitee 依赖本机 runner 上传，
    // runner 离线时 Gitee 会落后于 GitHub（5.6.83 双源字节分叉即此因），故不宜再作主源。
    private static final String RELEASES_URL = "https://api.github.com/repos/cyj265/lanxing-vod/releases?per_page=10";
    // Gitee 更新源（兜底）：国内直连，CI 发版时由 runner 上传 APK 附件；GitHub 各节点都拿不到时才用它。
    private static final String GITEE_API_URL = "https://gitee.com/api/v5/repos/cyj265/lanxing-vod/releases?per_page=10";
    // GitHub 加速节点：仅用于「版本发现」阶段直连 api.github.com 失败时的换址（那里只会拿到一段
    // release JSON）。实测速度不理想（gh-proxy ~19KB/s）但比完全不可达强。
    // 注意：它们不再承载 APK 字节——buildCandidates() 只允许官方域递包，理由见那里的注释。
    private static final String[] PROXIES = {
            "https://gh-proxy.com/",
            "https://ghfast.top/"
    };
    // 单节点连续该时长(毫秒)零字节则自动切换下一个候选，防止卡死在假死节点
    private static final long STALL_TIMEOUT_MS = 15000;
    // 看门狗检查间隔：判活依据是「累计字节是否还在涨」，不是「百分比是否变化」
    private static final long WATCHDOG_TICK_MS = 3000;

    // 同一时刻只允许一个更新会话：两处入口（首页自动检查、设置页手动检查）各自 create()，
    // 若同时确认就会有两个 Download 写同一份缓存文件、并按同一个 tag 互相取消（见 dismiss/clearStaleFiles）。
    // 在 show() 抢占、在 dismiss() 释放，不做「已忙即拒绝」的闸门——那种写法一旦某条路径漏释放就永久收不到更新。
    private static Updater session;

    private Download download;
    private UpdateDialog dialog;
    private String apkUrl;
    private String version;
    private List<String> candidates = new ArrayList<>();
    private int mirrorIndex = 0;
    private boolean forced = false;
    private long lastBytes;
    private long lastMoveAt;
    private final Runnable stallTask = this::tickWatchdog;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    /**
     * 每个候选各写自己的文件：切源时上一个 Download 线程未必已退出（cancel 到 IOException 抛出有一个
     * 很短的窗口），共用一个路径会让两次写交织出半个包，最终表现为一句看不懂的「校验失败」。
     */
    private File getFile() {
        return Path.cache("update-" + mirrorIndex + ".apk");
    }

    /** 清掉上次会话留下的分片文件；在确认更新时调用，此刻本会话还没有在飞的下载 */
    private void clearStaleFiles() {
        File[] files = Path.cache().listFiles((dir, name) -> name.startsWith("update-") && name.endsWith(".apk"));
        if (files == null) return;
        for (File file : files) Path.clear(file);
    }

    public Updater force() {
        forced = true;
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            JSONObject release = getLatestRelease();
            if (release == null) {
                if (forced) App.post(() -> Notify.show("检查更新失败，请稍后重试"));
                return;
            }
            apkUrl = release.optString("apk_url", null);
            version = release.optString("version", "");
            String desc = release.optString("desc", "");
            if (apkUrl == null || apkUrl.isEmpty() || version.isEmpty()) return;
            if (!isNewer(version)) return;
            App.post(() -> show(activity, version, desc));
        } catch (Exception e) {
            // 自动检查路径(非 forced)静默，但必须留日志，否则"为什么没提示更新"无从排查
            e.printStackTrace();
            if (forced) App.post(() -> Notify.show("检查更新失败，请稍后重试"));
        }
    }

    /**
     * 读取本线（主版本相同）最新的 Release，解析出版本号 / APK 下载地址；无更新或异常返回 null。
     * 源顺序：GitHub（直连 + 逐个加速前缀）为主，全部拿不到才回退 Gitee。
     * GitHub 优先的理由不在速度而在权威性：它由 CI 直接发布并做附件回读校验，
     * Gitee 一侧依赖本机 runner 补传，runner 离线时内容会落后（5.6.83 双源字节分叉即此因），
     * 作主源会把用户引向缺修复的旧构建。
     */
    private JSONObject getLatestRelease() throws Exception {
        // 1) GitHub 主源：先直连 api.github.com，不通再逐个换加速前缀（只换列表入口，
        //    APK 链接本身仍交给 buildCandidates() 那套候选）。
        //    实测并非所有代理都转发 api.*：gh-proxy.com 返回真 JSON(200)，ghfast.top 对 api
        //    路径直接 403。坏前缀会因 getInputStream 抛 IOException(4xx) 或 JSON 解析失败被
        //    自动跳过，所以这里全量遍历 PROXIES 是安全的，不必维护「API 专用代理」清单。
        Exception last = null;
        try {
            JSONObject found = matchOwnLine(fetchReleases(new URL(RELEASES_URL), 4000, 5000));
            if (found != null) return found;
        } catch (Exception e) {
            last = e;
        }
        for (String proxy : PROXIES) {
            try {
                JSONObject found = matchOwnLine(fetchReleases(new URL(proxy + RELEASES_URL), 4000, 8000));
                if (found != null) return found;
            } catch (Exception e) {
                last = e;
            }
        }
        // 2) Gitee 兜底：国内直连拉 Releases 列表。任一 GitHub 节点抛出的异常（含本线最新一条
        //    空壳被判源不可用）到这里都不阻断，仍继续尝试；任何异常都不让整条链路失败。
        try {
            JSONObject found = fetchGiteeRelease();
            if (found != null) return found;
        } catch (Exception e) {
            e.printStackTrace(); // Gitee 兜底失败：沿用 GitHub 侧最后一个异常
            if (last != null) throw last;
        }
        if (last != null) throw last;
        return null;
    }

    /** Gitee 兜底源：拉 Releases 列表（CI 发版时由 runner 上传 APK 附件），挑出本线最新一条 */
    private JSONObject fetchGiteeRelease() throws Exception {
        return matchOwnLine(fetchReleases(new URL(GITEE_API_URL), 4000, 5000));
    }

    private JSONArray fetchReleases(URL url, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestProperty("User-Agent", "LanXingVod");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return new JSONArray(sb.toString());
        }
    }

    /**
     * 在 Release 列表里挑出本线（主版本与本机相同）版本号最大的一条。
     * 顺序无关性、空壳判定、手机版优先等规则全部由 ReleaseSource 承载（纯逻辑，有 JVM 单测覆盖），
     * 这里只负责注入本机版本号。
     */
    private JSONObject matchOwnLine(JSONArray arr) throws Exception {
        return ReleaseSource.pickLatest(arr, BuildConfig.VERSION_NAME);
    }

    private boolean isNewer(String remoteVersion) {
        // 用版本号判断是否有新版本：分段数值比较，如 5.6.9 < 5.6.10
        if (ReleaseSource.compare(remoteVersion, BuildConfig.VERSION_NAME) <= 0) return false;
        // 只限制「主版本必须一致」，允许跨次/修正号升级（如 5.4.44 用户可直接升到 5.6.69）。
        // 不能再用「主+次都相同」的系列过滤：那会把 5.4.x 老用户永久挡在 5.6.x 门外。
        // 至于两个分支共用 Releases 频道，靠版本号大小比较天然隔离——低版本分支发了新包也不会
        // 对本机更高版本的用户提示更新（数字更小），无需再靠系列过滤兜底。
        return ReleaseSource.sameMajor(remoteVersion, BuildConfig.VERSION_NAME);
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        // 抢占会话：上一个还开着的更新弹窗连同它的下载一起收掉，避免两会话抢同一 tag/同一文件
        if (session != null && session != this) session.dismiss();
        session = this;
        dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, version)).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        buildCandidates();
        clearStaleFiles();
        // 1) 缓存命中：上次成功下载的镜像在候选列表里，直接 0 延迟开下（不弹 Toast，避免和 startNext 的提示重复）
        // 缓存按 host 匹配，不能比整条 URL：apkUrl 每次发版都变（文件名与 tag 都带版本号），
        // 整条 URL 比对会导致升级到新版本时缓存永远命中不了，每次都要白等一轮探测。
        String cachedHost = Setting.getFastMirrorUrl();
        if (cachedHost != null && !cachedHost.isEmpty()) {
            int hit = indexOfHost(cachedHost);
            if (hit >= 0) {
                // 命中缓存不探测（省下 3.5s），但顺序仍是 buildCandidates 的原始顺序，
                // 中途卡死时会盲切到"下一位"。这里零成本地重排一次：把国内基本必挂的
                // github.com 直连挪到末尾，好让切换先落到 Gitee 上。
                // 注：旧版本可能把加速节点 host 写进了缓存，那时 indexOfHost 匹配不到任何候选
                // （候选已不含代理），自然走探测，不会把包递给第三方站。
                reorderAround(candidates.get(hit));
                startNext();
                return;
            }
        }
        // 2) 未命中：并发探测 32KB 选最快节点（3.5s 预算，先到先得）
        probeAndStart(view);
    }

    /**
     * 并发探测各候选节点前 32KB 速率，把候选按实测速度重排后从最快的开下；
     * 全部失败则跳过国内必挂的 GitHub 直连、从 Gitee 起步。
     * 重排的意义不只是"本次用最快的"：下载中途卡死(onStall)或报错(error)时，
     * 顺序切换到的就是实测第二快的，而不是原来那个凭运气排在下一位的节点。
     */
    private void probeAndStart(View view) {
        if (candidates.size() < 2) {
            mirrorIndex = 0;
            startNext();
            return;
        }
        Notify.show("正在测速选择最快线路…");
        Task.submitLarge(() -> {
            List<String> ranked;
            try {
                ranked = probeRanked();
            } catch (Exception e) {
                e.printStackTrace();
                ranked = null;
            }
            final List<String> r = ranked;
            App.post(() -> {
                try {
                    if (r != null && !r.isEmpty()) {
                        candidates = new ArrayList<>(r);
                        mirrorIndex = 0;
                    } else {
                        // 全失败：跳过国内必挂的 GitHub 直连，从兜底源起步
                        mirrorIndex = firstFallbackIndex();
                    }
                    startNext();
                } catch (Exception e) {
                    // 最后兜底：防止按钮永久锁死
                    try { view.setEnabled(true); } catch (Exception ignored) {}
                    dismiss();
                }
            });
        });
    }

    /** 在候选里找 host 与缓存一致的那条；没命中返回 -1。缓存只存 host，跨版本升级才依然有效 */
    private int indexOfHost(String host) {
        if (host == null || host.isEmpty()) return -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (host.equals(hostOf(candidates.get(i)))) return i;
        }
        return -1;
    }

    /**
     * 无实测数据时的保守重排：把当前要用的节点放第一，其余非直连节点保持原顺序，
     * github.com 直连挪到末尾（国内基本连不通，别让切换先落到它身上）。
     */
    private void reorderAround(String current) {
        List<String> out = new ArrayList<>();
        if (current != null) out.add(current);
        for (String u : candidates) {
            if (u.equals(current) || u.startsWith("https://github.com/")) continue;
            out.add(u);
        }
        for (String u : candidates) {
            if (u.equals(current) || !u.startsWith("https://github.com/")) continue;
            out.add(u);
        }
        candidates = out;
        mirrorIndex = 0;
    }

    /** 第一个非 github.com 直连的候选索引（国内直连基本不通，失败时跳过它；候选里它就是 Gitee 那条） */
    private int firstFallbackIndex() {
        for (int i = 0; i < candidates.size(); i++) {
            String u = candidates.get(i);
            if (u != null && !u.startsWith("https://github.com/")) return i;
        }
        return 0;
    }

    /**
     * 并发探测所有候选节点的前 32KB，返回按实测速度降序排列的完整候选列表（失败节点排末尾）；
     * 一个节点都没通则返回 null。
     * 探测结果同时打进 logcat，日后调整下载候选有据可依。
     */
    private List<String> probeRanked() {
        final int PIECE = 32 * 1024;
        final long BUDGET_MS = 3500;
        // 首个节点收满后再多等一会儿，让其余节点有机会上报得分。没有这段宽限，最快的节点
        // 常在 200ms 内就胜出，其余节点全部来不及上报，排出来的顺序几乎等于原顺序，
        // 后续 onStall 切换就又变回盲切了。
        final long GRACE_MS = 800;
        final int n = candidates.size();
        final List<String> order = new ArrayList<>(candidates);
        final long[] bytes = new long[n];
        final long[] cost = new long[n];
        final boolean[] done = new boolean[n];
        final Object lock = new Object();
        final String[] winner = new String[1];  // 第一个收满 32KB 的 URL
        final long[] winnerAt = new long[1];
        final long start = System.currentTimeMillis();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final String url = order.get(i);
            Thread t = new Thread(() -> {
                HttpURLConnection conn = null;
                try {
                    long t0 = System.currentTimeMillis();
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(3000);
                    conn.setRequestProperty("User-Agent", "LanXingVod");
                    conn.setRequestProperty("Range", "bytes=0-" + (PIECE - 1));
                    java.io.InputStream in = conn.getInputStream();
                    byte[] buf = new byte[4096];
                    int total = 0, r;
                    while ((r = in.read(buf)) != -1) {
                        total += r;
                        if (total >= PIECE) break;
                        if (System.currentTimeMillis() - start > BUDGET_MS) break;
                    }
                    in.close();
                    synchronized (lock) {
                        bytes[idx] = total;
                        cost[idx] = Math.max(1, System.currentTimeMillis() - t0);
                        done[idx] = true;
                        if (total >= PIECE && winner[0] == null) {
                            winner[0] = url; // 先到先得
                            winnerAt[0] = System.currentTimeMillis();
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
            });
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        // 等胜出者出现并过完宽限期，或预算到期
        while (System.currentTimeMillis() - start < BUDGET_MS) {
            synchronized (lock) {
                if (winner[0] != null && System.currentTimeMillis() - winnerAt[0] >= GRACE_MS) break;
            }
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        }
        synchronized (lock) {
            boolean any = false;
            for (int i = 0; i < n; i++) if (done[i] && bytes[i] > 0) { any = true; break; }
            if (!any) return null;
            return rank(order, bytes, cost, done, winner[0]);
        }
    }

    /** 排序：胜出节点第一，其余按 字节数/耗时 降序，失败或无数据的按原顺序排末尾 */
    private List<String> rank(List<String> order, long[] bytes, long[] cost, boolean[] done, String winner) {
        List<Integer> scored = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            if (done[i] && bytes[i] > 0) scored.add(i);
            else failed.add(i);
        }
        scored.sort((a, b) -> Long.compare(bytes[b] * 1000 / cost[b], bytes[a] * 1000 / cost[a]));
        List<String> out = new ArrayList<>();
        for (int i : scored) {
            android.util.Log.d("Updater", "probe " + hostOf(order.get(i)) + " " + bytes[i] + "B/" + cost[i] + "ms");
            if (order.get(i).equals(winner)) continue;
            out.add(order.get(i));
        }
        if (winner != null) out.add(0, winner); // 先到先得的胜出者置顶
        for (int i : failed) {
            android.util.Log.d("Updater", "probe " + hostOf(order.get(i)) + " unreachable");
            out.add(order.get(i));
        }
        return out;
    }

    /**
     * 构建下载候选：发现到的那一条优先，再拼出另一侧同构链接。
     * 两源的文件名与 tag 完全一致（vX.Y.Z + lanxing-arm64_v8a-vX.Y.Z.apk），
     * 从任一侧 URL 解析出 tag+文件名即可拼出另一侧同构链接；解析失败时退化为单候选。
     * <p>
     * 候选一律先过 ReleaseTrust 域白名单，且不再挂 GitHub 加速节点前缀。理由是职责不同：
     * PROXIES 仍参与「版本发现」（那里只会拿到一段 release JSON），但不再承载 APK 字节。
     * gh-proxy.com / ghfast.top 属第三方，作为包体来源时既能替换内容，也能对单个用户
     * 反复返回当前版本实现定向冻结更新——后者签名校验治不了，只能靠不让它递包。
     * <p>
     * 可用性上不亏：GitHub 与 Gitee 两源永远同时在候选里（twinUrl 保证），
     * 国内直连 GitHub 不通时仍有 Gitee 兜底，这一条本来就是国内路径。
     */
    private void buildCandidates() {
        candidates.clear();
        if (ReleaseTrust.isTrustedDownloadUrl(apkUrl)) candidates.add(apkUrl);
        String twin = twinUrl(apkUrl);
        if (twin != null && ReleaseTrust.isTrustedDownloadUrl(twin) && !candidates.contains(twin)) candidates.add(twin);
        if (candidates.isEmpty()) {
            android.util.Log.w("Updater", "untrusted apk url dropped: " + apkUrl);
            Notify.show("更新源返回了不可信的下载链接，已拒绝");
        }
    }

    /** 从任意一侧的 releases/download 链接，拼出另一侧的同构链接；解析失败返回 null */
    private String twinUrl(String url) {
        if (url == null) return null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("https?://[^/]+/([^/]+)/([^/]+)/releases/download/([^/]+)/([^/]+)")
                    .matcher(url);
            if (!m.matches()) return null;
            String owner = m.group(1);
            String repo = m.group(2);
            String tag = m.group(3);
            String name = m.group(4);
            String base = url.startsWith("https://gitee.com/") ? "https://github.com/" : "https://gitee.com/";
            return base + owner + "/" + repo + "/releases/download/" + tag + "/" + name;
        } catch (Exception e) {
            return null;
        }
    }

    private String nameOf(int idx) {
        if (idx < 0 || idx >= candidates.size()) return "节点" + idx;
        return hostOf(candidates.get(idx));
    }

    /** 从 URL 提取 host 作为节点名（避免重排后"加速N"索引错乱） */
    private String hostOf(String url) {
        try {
            String s = url.replaceFirst("^https?://", "");
            int slash = s.indexOf('/');
            if (slash > 0) s = s.substring(0, slash);
            if (s.startsWith("github.com")) return "直连";
            return s;
        } catch (Exception e) {
            return "节点";
        }
    }

    private void startNext() {
        if (mirrorIndex >= candidates.size()) {
            dismiss();
            return;
        }
        String url = candidates.get(mirrorIndex);
        if (candidates.size() > 1) Notify.show("正在" + nameOf(mirrorIndex) + "下载更新…");
        // 安装包走独立客户端（默认信任链 + 默认主机名校验）：catvod 的 OkHttp.client()
        // 为兼容爬虫源配了 trust-all + hostnameVerifier 恒真，复用它的话这条 HTTPS
        // 连接可被任意证书冒充，buildCandidates 的域白名单与下面的签名固定都会被绕过。
        // 其它下载（jar / EPG / 字幕 / 壁纸 / so）仍用原客户端，不改它们的可达性。
        download = Download.create(url, getFile()).tag(nameOf(mirrorIndex)).client(OkHttp.secureClient());
        download.start(this);
        armWatchdog();
    }

    /**
     * 起一个新候选的看门狗：按固定间隔自检「累计字节是否还在涨」，不再依赖 progress() 回调。
     * 进度回调按整数百分比触发，33MB 包的 1% 就是 338KB——持续速率低于约 22KB/s 的正常连接
     * 会在 15s 内一次回调都不发，被误判成卡死而切源；切源又是从头重下（Gitee 侧实测不支持 Range），
     * 慢网用户于是永远下不完，最后只剩一句「更新下载超时」。
     */
    private void armWatchdog() {
        App.removeCallbacks(stallTask);
        lastBytes = 0;
        lastMoveAt = System.currentTimeMillis();
        App.post(stallTask, WATCHDOG_TICK_MS);
    }

    private void disarmWatchdog() {
        App.removeCallbacks(stallTask);
    }

    private void tickWatchdog() {
        long bytes = download != null ? download.bytesRead() : lastBytes;
        if (bytes > lastBytes) {
            lastBytes = bytes;
            lastMoveAt = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - lastMoveAt >= STALL_TIMEOUT_MS) {
            onStall();
            return;
        }
        App.post(stallTask, WATCHDOG_TICK_MS);
    }

    /** 单节点连续 STALL_TIMEOUT_MS 零字节，自动切换下一个候选；已是最后一个则提示失败 */
    private void onStall() {
        if (mirrorIndex >= candidates.size() - 1) {
            Notify.show("更新下载超时，请稍后重试");
            dismiss();
            return;
        }
        Notify.show(nameOf(mirrorIndex) + "无数据，切换" + nameOf(mirrorIndex + 1) + "下载…");
        if (download != null) download.cancel();
        // 取消路径上 Download 不会自己清文件（handleError 见 canceled 就跳过），
        // 33MB 的半成品留在缓存目录里既占空间也没人再用，这里主动删掉。
        Path.clear(getFile());
        mirrorIndex++;
        startNext();
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        dismiss();
    }

    /**
     * 收场必须连同在飞的下载一起取消：只关弹窗不管下载的话，被判超时的那个慢节点稍后仍会跑完，
     * 然后自己调 success() → 拉起安装界面，用户看到的是「刚提示超时，怎么突然要安装」。
     */
    private void dismiss() {
        disarmWatchdog();
        if (download != null) download.cancel();
        if (session == this) session = null;
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        disarmWatchdog();
        // 当前候选失败，自动切换下一个候选；全部失败才提示
        // （候选只有 GitHub / Gitee 两源，任一条不通都会自动换另一条，不再包含第三方加速前缀）
        if (mirrorIndex < candidates.size() - 1) {
            int failed = mirrorIndex;
            mirrorIndex++;
            Notify.show(nameOf(failed) + "失败，切换" + nameOf(mirrorIndex) + "下载…");
            startNext();
            return;
        }
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        disarmWatchdog();
        ApkCheck check = checkApk(file);
        if (check != ApkCheck.OK) {
            Path.clear(file);
            Notify.show(messageOf(check));
            dismiss();
            return;
        }
        if (isInstalledAbnormal()) {
            Path.clear(file);
            Notify.show("检测到本地安装版本异常（版本号高于线上），请卸载后重新安装");
            dismiss();
            return;
        }
        // 记录这次成功下载用的镜像，24h 内下次更新可 0 延迟直通。
        // 只存 host 而非完整 URL：完整 URL 含版本号，下次升级就对不上了。
        try {
            if (mirrorIndex >= 0 && mirrorIndex < candidates.size()) {
                Setting.putFastMirror(hostOf(candidates.get(mirrorIndex)));
            }
        } catch (Exception ignored) {}
        FileUtil.openFile(file);
        dismiss();
    }

    /** 检测本地已安装包版本号是否异常高于当前版本（常见于装过非正规构建），此时安装任何低版本都会失败 */
    private boolean isInstalledAbnormal() {
        try {
            PackageInfo installed = App.get().getBaseContext().getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            return installed != null && installed.versionCode > BuildConfig.VERSION_CODE;
        } catch (Exception e) {
            return false;
        }
    }

    /** 校验结果分级：签名问题与包体问题分开报，前者重试无意义，后者可能是网络截断 */
    private enum ApkCheck {
        OK, INVALID, UNSIGNED, MISMATCH
    }

    private String messageOf(ApkCheck check) {
        if (check == ApkCheck.MISMATCH) return "安装包签名与官方发布证书不一致，已拒绝安装";
        if (check == ApkCheck.UNSIGNED) return "无法读取安装包签名，可能不是有效安装包，已拒绝安装";
        return "安装包校验失败，已清理，请重试";
    }

    /**
     * 下载完成后校验安装包：包名一致、版本号不低于当前版本，且签名必须是 ReleaseTrust 固定的发布证书。
     * <p>
     * 为什么要自己查：Android 安装器同样会拒绝异签名覆盖安装（INSTALL_FAILED_UPDATE_INCOMPATIBLE），
     * 但那意味着用户只看到一个看不懂的失败码，接着被搜索结果引导去「卸载重装」——而卸载重装会让
     * 同包名的假包以全新安装直接装上。这里提前拒绝并说明原因，就是不给这条路留机会。
     * <p>
     * 线上两个包（v5.6.85 / v5.6.87）都没有 v1(JAR) 签名：META-INF 下没有任何 .RSA/.SF，
     * 签名只在 APK Signing Block 的 v2 块里。查 AOSP 确认这样仍然读得到证书——
     * PackageManager.getPackageArchiveInfo 在 7.0 起就是先跑 ApkSignatureSchemeV2Verifier
     * 再回退 JAR 校验，所以 24~27 用 GET_SIGNATURES 拿到的是 v2 signer 的证书。
     * 两条读取路径都取值并合并，任一环节被厂商 ROM 改动也不至于把合法包判死。
     * 两路都拿不到时判 UNSIGNED（拒绝），绝不把"读不到"当成"通过"。
     * <p>
     * 顺带的好处：collectCertificates 会真跑一遍 v2 校验（对全文件算摘要），下载被截断或篡改时
     * 平台多半直接返回 null，落到 INVALID，不会走到安装器那一步。
     */
    private ApkCheck checkApk(File file) {
        try {
            PackageManager pm = App.get().getBaseContext().getPackageManager();
            // GET_SIGNATURES 必须始终带上，不只是「旧接口」：AOSP 的 getPackageArchiveInfo 在
            // Android 9/10 里只在 (flags & GET_SIGNATURES) != 0 时才调 collectCertificates，
            // 到 Android 11 才改成两个 flag 任一即可。所以 28/29 上若只传
            // GET_SIGNING_CERTIFICATES，归档根本不会被解析签名，signingInfo 恒为 null，
            // 合法更新会被全部判成 UNSIGNED。这里两个 flag 一起传，兼容各版本触发条件。
            int flags = PackageManager.GET_SIGNATURES;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) flags |= PackageManager.GET_SIGNING_CERTIFICATES;
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
            if (info == null) return ApkCheck.INVALID;
            if (info.versionCode < BuildConfig.VERSION_CODE) return ApkCheck.INVALID;
            if (info.packageName == null || !info.packageName.equals(BuildConfig.APPLICATION_ID)) return ApkCheck.INVALID;
            List<byte[]> certs = signerCerts(info);
            if (certs.isEmpty()) {
                android.util.Log.w("Updater", "no signer cert readable from archive: " + file.getName());
                return ApkCheck.UNSIGNED;
            }
            for (byte[] cert : certs) {
                if (ReleaseTrust.isTrustedCert(cert)) return ApkCheck.OK;
            }
            android.util.Log.w("Updater", "signature mismatch: " + file.getName() + " from " + nameOf(mirrorIndex));
            return ApkCheck.MISMATCH;
        } catch (Exception e) {
            e.printStackTrace();
            return ApkCheck.INVALID;
        }
    }

    /**
     * 取归档的签名者证书 DER，两条路径都取并合并（去重后交给 ReleaseTrust 逐枚比对）。
     * signingInfo 是首选：GET_SIGNATURES 在存在轮转历史时只返回「最旧的一枚」，
     * 单靠它会漏掉当前 signer；反之个别厂商 ROM 对归档不填 signingInfo，单靠它又会读空。
     * 任一命中固定指纹即视为可信——多 signer（轮转窗口）场景下这正是我们要接受的形态。
     */
    private List<byte[]> signerCerts(PackageInfo info) {
        List<byte[]> out = new ArrayList<>();
        addAll(out, info.signatures);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            try {
                SigningInfo signing = info.signingInfo;
                // 多 signer 时 getSigningCertificateHistory() 按契约返回 null，改用 contents signers
                addAll(out, signing.hasMultipleSigners() ? signing.getApkContentsSigners() : signing.getSigningCertificateHistory());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return out;
    }

    private void addAll(List<byte[]> out, Signature[] signatures) {
        if (signatures == null) return;
        for (Signature item : signatures) {
            if (item == null) continue;
            byte[] der = item.toByteArray();
            if (der == null || der.length == 0) continue;
            boolean seen = false;
            for (byte[] exist : out) if (java.security.MessageDigest.isEqual(exist, der)) {
                seen = true;
                break;
            }
            if (!seen) out.add(der);
        }
    }
}
