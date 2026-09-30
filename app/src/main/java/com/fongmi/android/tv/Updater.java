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

    // 与揽星TV 一致的更新逻辑：查 GitHub Releases(latest)，用构建号(BUILD_NUMBER)判断是否有新版本
    private static final String RELEASES_URL = "https://api.github.com/repos/cyj265/lanxing-vod/releases/latest";
    private static final Pattern APK_BUILD = Pattern.compile("v(\\d+)\\.apk$");
    // 加速节点：与揽星TV 一致，仅用 gh-proxy.com（直连失败时自动回退到该加速中转）
    private static final String PROXY = "https://gh-proxy.com/";

    private Download download;
    private UpdateDialog dialog;
    private String apkUrl;
    private String version;
    private List<String> candidates = new ArrayList<>();
    private int mirrorIndex = 0;

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
        JSONObject json = new JSONObject(body);
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

    /** APK 文件名形如 lanxing-5.6.66-123-mobile-arm64_v8a.apk → 取 v123.apk 中的 123 */
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

    /** 构建下载候选：GitHub 链接 = 直连 + gh-proxy 加速；其它链接只有一条 */
    private void buildCandidates() {
        candidates.clear();
        if (apkUrl != null && apkUrl.startsWith("https://github.com/")) {
            candidates.add(apkUrl);          // 直连
            candidates.add(PROXY + apkUrl);   // 加速（gh-proxy.com）
        } else {
            candidates.add(apkUrl);
        }
    }

    private void startNext() {
        if (mirrorIndex >= candidates.size()) {
            dismiss();
            return;
        }
        String url = candidates.get(mirrorIndex);
        String name = mirrorIndex == 0 ? "直连" : "加速";
        if (candidates.size() > 1) Notify.show("正在" + name + "下载更新…");
        download = Download.create(url, getFile()).tag(name);
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    private void dismiss() {
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
        // 与揽星TV 一致：直连失败自动切换下一个候选（加速节点），全部失败才提示
        if (apkUrl != null && apkUrl.startsWith("https://github.com/") && mirrorIndex < candidates.size() - 1) {
            mirrorIndex++;
            Notify.show("直连失败，切换加速节点下载…");
            startNext();
            return;
        }
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
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
