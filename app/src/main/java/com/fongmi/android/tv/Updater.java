package com.fongmi.android.tv;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
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

    // 更新逻辑：查 GitHub Releases，用版本号(versionName)判断是否有新版本（分段数值比较）。
    // 注：构建号(BUILD_NUMBER)方案已废弃——安装包的 BUILD_NUMBER 取自 CI run_number，与线上
    //     release 的构建号恒相等，无法触发更新；改用版本号后，用户装到更高版本号才会提示更新。
    // 注意：不能用 /releases/latest —— 该接口默认排除 prerelease，而 dev 构建发布为预发布版会永远返回 404。
    // 用列表接口(含 prerelease)即可。per_page 取 10 而非 1：main(5.4.x) 与 dev(5.6.x) 共用同一仓库的
    // Releases，列表按创建时间倒序，main 线若最近发过包会排到最前，取 1 条会让 dev 线用户
    // 被引导去装另一个分支的包。CI 的清理步骤已把 Release 压到 3 条以内，取 10 条足够覆盖。
    private static final String RELEASES_URL = "https://api.github.com/repos/cyj265/lanxing-vod/releases?per_page=10";
    // 加速节点：直连优先，失败依次回退到多个主流 GitHub 加速中转（国内常见可用节点）
    private static final String[] PROXIES = {
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "https://ghfast.top/",
            "https://mirror.ghproxy.com/",
            "https://gh.idayer.com/",
            "https://ghproxy.cfd/"
    };
    // 单节点无数据超过该时长(毫秒)则自动切换下一个加速节点，防止卡死在假死节点
    private static final long STALL_TIMEOUT_MS = 15000;

    private Download download;
    private UpdateDialog dialog;
    private String apkUrl;
    private String version;
    private List<String> candidates = new ArrayList<>();
    private int mirrorIndex = 0;
    private boolean forced = false;
    private final Runnable stallTask = this::onStall;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
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
            if (forced) App.post(() -> Notify.show("检查更新失败，请稍后重试"));
        }
    }

    /**
     * 读取本线（主版本相同）最新的 Release，解析出版本号 / APK 下载地址；无更新或异常返回 null。
     * 国内直连 api.github.com 基本拿不到响应，这里和下载一样做多节点回退：
     * 先直连，不通就依次换加速节点的 API 前缀代理（只换列表入口，APK 链接本身仍走 buildCandidates 那套）。
     */
    private JSONObject getLatestRelease() throws Exception {
        Exception last = null;
        for (String url : releaseCandidates()) {
            try {
                JSONObject found = matchOwnLine(fetchReleases(new URL(url), 4000, 5000));
                if (found != null) return found;
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return null;
    }

    /** 检查更新的候选入口：API 直连 + 各加速节点前缀代理 */
    private List<String> releaseCandidates() {
        List<String> list = new ArrayList<>();
        list.add(RELEASES_URL);
        for (String p : PROXIES) list.add(p + RELEASES_URL);
        return list;
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
     * 在 Release 列表里挑出主版本与本机相同（本线）的最新一条。
     * 列表按创建时间倒序，逐条往下找，main(5.4.x) / dev(5.6.x) 共用一个 Releases 频道也不串线。
     */
    private JSONObject matchOwnLine(JSONArray arr) throws Exception {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject json = arr.getJSONObject(i);
            // tag 形如 v5.6.67-r124：去掉前缀 v，再去掉 -r124 等后缀，只留版本号 5.6.67
            String tag = json.optString("tag_name", "").replaceFirst("^v", "").replaceFirst("-.*$", "");
            if (tag.isEmpty() || !sameMajor(tag, BuildConfig.VERSION_NAME)) continue;
            String url = pickApk(json);
            if (url == null) continue;
            JSONObject release = new JSONObject();
            release.put("version", tag);
            release.put("apk_url", url);
            release.put("desc", json.optString("body", ""));
            return release;
        }
        return null;
    }

    /** 从一条 Release 的附件里挑手机版 APK 下载地址；挑不到返回 null，宁可不更新也不下错包 */
    private String pickApk(JSONObject release) throws Exception {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;
        String url = null;
        String fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name", "");
            if (!name.endsWith(".apk")) continue;
            if (fallback == null) fallback = a.optString("browser_download_url", "");
            // 历史 Release 长期同时挂着手机版(lanxing-v*.apk)与电视版(lanxing-tv-v*.apk)两个包，
            // 直接取首个 .apk 会命中电视版（"lanxing-tv" 排在 "lanxing-v" 前），导致手机端总更新成电视包；
            // 电视版已停发，这里始终优先选不含 tv 的手机版，万一只剩电视包也宁可不更新也不下错。
            if (!name.contains("tv")) {
                url = a.optString("browser_download_url", "");
                break;
            }
        }
        if (url == null || url.isEmpty()) url = fallback;
        return url == null || url.isEmpty() ? null : url;
    }

    private boolean isNewer(String remoteVersion) {
        // 用版本号(版本号)判断是否有新版本：分段数值比较，如 5.6.9 < 5.6.10
        if (compareVersion(remoteVersion, BuildConfig.VERSION_NAME) <= 0) return false;
        // 只限制「主版本必须一致」，允许跨次/修正号升级（如 5.4.44 用户可直接升到 5.6.69）。
        // 不能再用「主+次都相同」的系列过滤：那会把 5.4.x 老用户永久挡在 5.6.x 门外。
        // 至于两个分支共用 Releases 频道，靠版本号大小比较天然隔离——低版本分支发了新包也不会
        // 对本机更高版本的用户提示更新（数字更小），无需再靠系列过滤兜底。
        return sameMajor(remoteVersion, BuildConfig.VERSION_NAME);
    }

    /** 判断两个版本号主版本是否一致（只比第一段，如 5.4.44 与 5.6.69 同为主版本 5） */
    private boolean sameMajor(String a, String b) {
        return majorOf(a) == majorOf(b);
    }

    private int majorOf(String v) {
        try {
            return Integer.parseInt(((v == null ? "" : v).split("\\."))[0].trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** 分段数值比较：a>b 返回正数，相等返回 0，a<b 返回负数；任一为空视为相等(不提示) */
    private int compareVersion(String a, String b) {
        if (a == null || b == null) return 0;
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? parseInt(pa[i]) : 0;
            int y = i < pb.length ? parseInt(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, version)).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        buildCandidates();
        // 1) 缓存命中：上次成功下载的镜像在候选列表里，直接 0 延迟开下（不弹 Toast，避免和 startNext 的提示重复）
        String cached = Setting.getFastMirrorUrl();
        if (cached != null && !cached.isEmpty() && candidates.contains(cached)) {
            mirrorIndex = candidates.indexOf(cached);
            startNext();
            return;
        }
        // 2) 未命中：并发探测 32KB 选最快节点（3.5s 预算，先到先得）
        probeAndStart(view);
    }

    /** 并发探测各候选节点前 32KB 速率，选最快的开下；全部失败则跳过直连从第一个代理起步 */
    private void probeAndStart(View view) {
        if (candidates.size() < 2) {
            mirrorIndex = 0;
            startNext();
            return;
        }
        Task.submitLarge(() -> {
            String best;
            try {
                best = probeBestMirror();
            } catch (Exception e) {
                best = null;
            }
            final String b = best;
            App.post(() -> {
                try {
                    if (b != null) {
                        mirrorIndex = candidates.indexOf(b);
                        if (mirrorIndex < 0) mirrorIndex = firstProxyIndex();
                    } else {
                        // 全失败：跳过国内必挂的直连，从第一个代理起步
                        mirrorIndex = firstProxyIndex();
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

    /** 第一个非 github.com 直连的候选索引（国内直连基本不通，失败时跳过它） */
    private int firstProxyIndex() {
        for (int i = 0; i < candidates.size(); i++) {
            String u = candidates.get(i);
            if (u != null && !u.startsWith("https://github.com/")) return i;
        }
        return 0;
    }

    /** 并发探测，返回第一个收满 32KB 的 URL；3.5s 内无人收满则按字节数/耗时得分选最快的；全失败返回 null */
    private String probeBestMirror() {
        final int PIECE = 32 * 1024;
        final long BUDGET_MS = 3500;
        final Object lock = new Object();
        final String[] firstFull = new String[1];   // 第一个收满 32KB 的 URL
        final String[] bestPartial = new String[1];  // 未收满但得分最高的 URL
        final long[] bestScore = new long[1];
        final long start = System.currentTimeMillis();

        List<Thread> threads = new ArrayList<>();
        for (final String url : candidates) {
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
                    int total = 0, n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total >= PIECE) break;
                        if (System.currentTimeMillis() - start > BUDGET_MS) break;
                    }
                    in.close();
                    if (total <= 0) return;
                    long cost = System.currentTimeMillis() - t0;
                    if (cost <= 0) cost = 1;
                    long score = total * 1000 / cost;
                    synchronized (lock) {
                        if (total >= PIECE && firstFull[0] == null) {
                            firstFull[0] = url; // 先到先得，立刻胜出
                        }
                        if (score > bestScore[0]) {
                            bestScore[0] = score;
                            bestPartial[0] = url;
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
        // 等先到先得胜出，或预算到期
        while (System.currentTimeMillis() - start < BUDGET_MS) {
            synchronized (lock) { if (firstFull[0] != null) break; }
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        }
        synchronized (lock) {
            if (firstFull[0] != null) return firstFull[0];
            return bestPartial[0]; // 没人收满，退而求其次选得分最高的
        }
    }

    /** 构建下载候选：GitHub 链接 = 直连 + 多个加速中转 + kkgithub 域名替换；其它链接只有一条 */
    private void buildCandidates() {
        candidates.clear();
        if (apkUrl != null && apkUrl.startsWith("https://github.com/")) {
            candidates.add(apkUrl); // 直连
            for (String p : PROXIES) candidates.add(p + apkUrl);
            // kkgithub 域名替换（独立加速线路，非前缀）
            candidates.add(apkUrl.replaceFirst("https://github\\.com/", "https://kgithub.com/"));
        } else {
            candidates.add(apkUrl);
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
        download = Download.create(url, getFile()).tag(nameOf(mirrorIndex));
        download.start(this);
        armWatchdog();
    }

    /** 重新计时无数据超时（开始下载或收到进度时调用） */
    private void armWatchdog() {
        App.removeCallbacks(stallTask);
        App.post(stallTask, STALL_TIMEOUT_MS);
    }

    private void disarmWatchdog() {
        App.removeCallbacks(stallTask);
    }

    /** 单节点长时间无数据，自动切换下一个候选；已是最后一个则提示失败 */
    private void onStall() {
        if (apkUrl == null || !apkUrl.startsWith("https://github.com/") || mirrorIndex >= candidates.size() - 1) {
            Notify.show("更新下载超时，请稍后重试");
            dismiss();
            return;
        }
        Notify.show(nameOf(mirrorIndex) + "无数据，切换" + nameOf(mirrorIndex + 1) + "下载…");
        if (download != null) download.cancel();
        mirrorIndex++;
        startNext();
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    private void dismiss() {
        disarmWatchdog();
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        armWatchdog(); // 收到进度即重置无数据超时
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        disarmWatchdog();
        // 直连/某加速节点失败，自动切换下一个候选；全部失败才提示
        if (apkUrl != null && apkUrl.startsWith("https://github.com/") && mirrorIndex < candidates.size() - 1) {
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
        if (!isValidApk(file)) {
            Path.clear(file);
            Notify.show("安装包校验失败，已清理，请重试");
            dismiss();
            return;
        }
        if (isInstalledAbnormal()) {
            Path.clear(file);
            Notify.show("检测到本地安装版本异常（版本号高于线上），请卸载后重新安装");
            dismiss();
            return;
        }
        // 记录这次成功下载用的镜像，24h 内下次更新可 0 延迟直通
        try {
            if (mirrorIndex >= 0 && mirrorIndex < candidates.size()) {
                Setting.putFastMirror(candidates.get(mirrorIndex));
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

    /** 下载完成后校验安装包：包名一致且版本号不低于当前版本，避免误装旧包/错包 */
    private boolean isValidApk(File file) {
        try {
            PackageManager pm = App.get().getBaseContext().getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
            if (info == null) return false;
            if (info.versionCode < BuildConfig.VERSION_CODE) return false;
            return info.packageName != null && info.packageName.equals(BuildConfig.APPLICATION_ID);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
