package com.fongmi.android.tv;

import com.cyj265.lanxingvod.R;
import com.cyj265.lanxingvod.BuildConfig;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;

import com.cyj265.lanxingvod.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class Updater {

    private static final String CHANNEL_ID = "lanxing_update";
    private static final int NOTIFICATION_ID = 1001;

    private static final String[] MIRROR_PREFIXES = {
        "",
        "https://gh-proxy.com/",
        "https://mirror.ghproxy.com/"
    };
    private static final String[] MIRROR_NAMES = {"直连", "加速", "备用"};

    private DialogUpdateBinding binding;
    private AlertDialog dialog;
    private String apkUrl;
    private String versionName;
    private int mirrorIndex;
    private boolean cancelled;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private File getFile() {
        File dir = new File(Path.cache(), "apk");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "update.apk");
    }

    private String getApk(JSONObject object) {
        JSONArray assets = object.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            String name = asset == null ? "" : asset.optString("name");
            if (name.contains("arm64") && name.endsWith(".apk")) return asset.optString("browser_download_url");
        }
        return null;
    }

    private int getCode(String version) {
        try {
            String[] parts = version.replace("v", "").split("\\.");
            int code = 0;
            for (String part : parts) code = code * 10 + Integer.parseInt(part);
            return code;
        } catch (Exception e) {
            return 0;
        }
    }

    public static Updater create() {
        return new Updater();
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    private Updater check() {
        dismiss();
        return this;
    }

    public void start(Activity activity) {
        if (!Setting.getUpdate()) return;
        App.execute(() -> doInBackground(activity));
    }

    private void doInBackground(Activity activity) {
        JSONObject object = null;
        try {
            object = new JSONObject(OkHttp.string(Github.RELEASE));
        } catch (Exception e) {
            try {
                object = new JSONObject(OkHttp.string(Github.RELEASE_MIRROR));
            } catch (Exception e2) {
                e2.printStackTrace();
                return;
            }
        }
        try {
            String name = object.optString("tag_name");
            String desc = object.optString("body");
            String apk = getApk(object);
            int code = getCode(name);
            if (code > BuildConfig.VERSION_CODE && apk != null) {
                App.post(() -> show(activity, name, desc, apk));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void show(Activity activity, String version, String desc, String apk) {
        File oldFile = getFile();
        if (oldFile.exists()) oldFile.delete();
        this.apkUrl = apk;
        this.versionName = version;
        this.mirrorIndex = 0;
        this.cancelled = false;
        binding = DialogUpdateBinding.inflate(LayoutInflater.from(activity));
        check().create(activity, ResUtil.getString(R.string.update_version, version)).show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this::confirm);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(this::cancel);
        binding.desc.setText(desc);
        initNotification(activity);
    }

    private void initNotification(Activity activity) {
        notificationManager = (NotificationManager) activity.getSystemService(Activity.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "应用更新", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("下载应用更新");
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }
        notificationBuilder = new NotificationCompat.Builder(activity, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("揽星影视 " + versionName)
            .setContentText("准备下载...")
            .setProgress(100, 0, false)
            .setOngoing(true)
            .setAutoCancel(false);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private AlertDialog create(Activity activity, String title) {
        return dialog = new MaterialAlertDialogBuilder(activity).setTitle(title).setView(binding.getRoot()).setPositiveButton(R.string.update_confirm, null).setNegativeButton(R.string.dialog_negative, null).setCancelable(false).create();
    }

    private void cancel(View view) {
        cancelled = true;
        Setting.putUpdate(false);
        cancelNotification();
        dismiss();
    }

    private void confirm(View view) {
        view.setEnabled(false);
        App.execute(this::downloadLoop);
    }

    private void downloadLoop() {
        while (mirrorIndex < MIRROR_PREFIXES.length && !cancelled) {
            String urlName = MIRROR_NAMES[mirrorIndex];
            try {
                updateStatus("正在" + urlName + "下载...");
                File apk = downloadFile(MIRROR_PREFIXES[mirrorIndex] + apkUrl);
                if (apk == null) {
                    mirrorIndex++;
                    if (mirrorIndex < MIRROR_PREFIXES.length) {
                        updateStatus(urlName + "失败，切换" + MIRROR_NAMES[mirrorIndex] + "...");
                        Thread.sleep(500);
                    }
                    continue;
                }
                if (!verifyApk(apk)) {
                    apk.delete();
                    mirrorIndex++;
                    if (mirrorIndex < MIRROR_PREFIXES.length) {
                        updateStatus(urlName + "版本异常，切换" + MIRROR_NAMES[mirrorIndex] + "...");
                        Thread.sleep(500);
                    }
                    continue;
                }
                App.post(() -> installApk(apk));
                return;
            } catch (Exception e) {
                mirrorIndex++;
                if (mirrorIndex < MIRROR_PREFIXES.length) {
                    updateStatus(urlName + "失败，切换" + MIRROR_NAMES[mirrorIndex] + "...");
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
        }
        if (!cancelled) {
            App.post(() -> {
                Notify.show(R.string.update_download_failed);
                cancelNotification();
                dismiss();
            });
        }
    }

    private File downloadFile(String urlStr) throws Exception {
        String urlWithTs = urlStr + (urlStr.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
        URL url = new URL(urlWithTs);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "LanXingVod");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 400) {
            conn.disconnect();
            return null;
        }
        long total = conn.getContentLengthLong();
        if (total < 5 * 1024 * 1024) {
            conn.disconnect();
            return null;
        }
        File apk = getFile();
        if (apk.exists()) apk.delete();
        InputStream input = conn.getInputStream();
        FileOutputStream output = new FileOutputStream(apk);
        byte[] buf = new byte[64 * 1024];
        int read;
        long done = 0;
        int lastPercent = -1;
        while ((read = input.read(buf)) > 0 && !cancelled) {
            output.write(buf, 0, read);
            done += read;
            if (total > 0) {
                int pct = (int) (done * 100 / total);
                if (pct != lastPercent) {
                    lastPercent = pct;
                    updateProgress(pct);
                }
            }
        }
        output.flush();
        output.close();
        input.close();
        conn.disconnect();
        if (cancelled) {
            apk.delete();
            return null;
        }
        return apk;
    }

    private boolean verifyApk(File apk) {
        try {
            PackageManager pm = App.get().getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return info != null && info.versionCode > BuildConfig.VERSION_CODE;
        } catch (Exception e) {
            return false;
        }
    }

    private void installApk(File apk) {
        try {
            if (notificationBuilder != null && notificationManager != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri apkUri = com.fongmi.android.tv.utils.FileUtil.getShareUri(apk);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                PendingIntent pi = PendingIntent.getActivity(App.get(), 0, intent, PendingIntent.FLAG_IMMUTABLE);
                notificationBuilder.setContentText("下载完成，点击安装")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentIntent(pi);
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            }
            com.fongmi.android.tv.utils.FileUtil.openFile(apk);
            Notify.show(R.string.update_install_hint);
        } catch (Exception e) {
            Notify.show(R.string.update_install_failed);
        }
        dismiss();
    }

    private void updateProgress(int progress) {
        App.post(() -> {
            if (dialog != null) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setText(String.format(Locale.getDefault(), "%1$d%%", progress));
            }
            if (notificationBuilder != null && notificationManager != null) {
                notificationBuilder.setProgress(100, progress, false);
                notificationBuilder.setContentText("下载中 " + progress + "%");
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            }
        });
    }

    private void updateStatus(String text) {
        App.post(() -> {
            if (notificationBuilder != null && notificationManager != null) {
                notificationBuilder.setContentText(text);
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            }
        });
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    private void cancelNotification() {
        try {
            if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {
        }
    }
}
