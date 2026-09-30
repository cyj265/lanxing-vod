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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Updater implements Download.Callback, UpdateListener {

    // 与揽星TV 一致的更新逻辑：查 GitHub Releases，用构建号(BUILD_NUMBER)判断是否有新版本。
    // 注意：不能用 /releases/latest —— 该接口默认排除 prerelease，而 dev 构建发布为预发布版会永远返回 404。
    // 用列表接口(含 prerelease)取最新一条即可。
    private static final String RELEASES_URL = "https://api.github.com/repos/cyj265/lanxing-vod/releases?per_page=1";
    private static final Pattern APK_BUILD = Pattern.compile("v(\\d+)\\.apk$");
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
            if (release == null) return;
            apkUrl = release.optString("apk_url", null);
            version = release.optString("version", "");
            String desc = release.optString("desc", "");
            if (apkUrl == null || apkUrl.isEmpty() || version.isEmpty()) return;
            if (!isNewer(release.optInt("build", 0))) return;
            App.post(() -> show(activity, version, desc));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 读取最新 Release，解析出版本号/构建号/APK 下载地址；无更新或异常返回 null */
    private JSONObject getLatestRelease() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "LanXingVod");
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            body = sb.toString();
        }
        JSONArray arr = new JSONArray(body);
        if (arr.length() == 0) return null;
        JSONObject json = arr.getJSONObject(0);
        String tag = json.optString("tag_name", "").replaceFirst("^v", "");
        JSONArray assets = json.optJSONArray("assets");
        if (assets == null) return null;
        String url = null;
        String name = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if (a.optString("name", "").endsWith(".apk")) {
                url = a.optString("browser_download_url", "");
                name = a.optString("name", "");
                break;
            }
        }
        if (url.isEmpty()) return null;
        int build = extractBuildNumber(name);
        JSONObject release = new JSONObject();
        release.put("version", tag.isEmpty() ? version : tag);
        release.put("build", build);
        release.put("apk_url", url);
        release.put("desc", json.optString("body", ""));
        return release;
    }

    /** APK 文件名形如 lanxing-v12.apk（与揽星TV 的 iptv-player-v<run>.apk 一致）→ 取 v12.apk 中的 12 */
    private int extractBuildNumber(String apkName) {
        if (apkName == null) return 0;
        Matcher m = APK_BUILD.matcher(apkName);
        return m.find() ? parseInt(m.group(1)) : 0;
    }

    private boolean isNewer(int remoteBuild) {
        // 用构建号判断：即使版本号相同，构建号增加也提示更新
        return remoteBuild > BuildConfig.BUILD_NUMBER;
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
        mirrorIndex = 0;
        startNext();
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
        return idx == 0 ? "直连" : "加速" + idx;
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
