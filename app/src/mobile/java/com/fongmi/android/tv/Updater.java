package com.fongmi.android.tv;

import com.cyj265.lanxingvod.R;

import com.cyj265.lanxingvod.BuildConfig;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.cyj265.lanxingvod.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

public class Updater implements Download.Callback {

    private DialogUpdateBinding binding;
    private AlertDialog dialog;

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getJson() {
        return Github.RELEASE;
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
            for (String part : parts) code = code * 100 + Integer.parseInt(part);
            return code;
        } catch (Exception e) {
            return 0;
        }
    }

    public static Updater create() {
        return new Updater();
    }

    private Download download;

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
        try {
            JSONObject object = new JSONObject(OkHttp.string(getJson()));
            String name = object.optString("tag_name");
            String desc = object.optString("body");
            String apk = getApk(object);
            int code = getCode(name);
            if (code > BuildConfig.VERSION_CODE && apk != null) App.post(() -> show(activity, name, desc, apk));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void show(Activity activity, String version, String desc, String apk) {
        this.download = Download.create(apk, getFile());
        binding = DialogUpdateBinding.inflate(LayoutInflater.from(activity));
        check().create(activity, ResUtil.getString(R.string.update_version, version)).show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this::confirm);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(this::cancel);
        binding.desc.setText(desc);
    }

    private AlertDialog create(Activity activity, String title) {
        return dialog = new MaterialAlertDialogBuilder(activity).setTitle(title).setView(binding.getRoot()).setPositiveButton(R.string.update_confirm, null).setNegativeButton(R.string.dialog_negative, null).setCancelable(false).create();
    }

    private void cancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    private void confirm(View view) {
        view.setEnabled(false);
        download.start(this);
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.getButton(DialogInterface.BUTTON_POSITIVE).setText(String.format(Locale.getDefault(), "%1$d%%", progress));
    }

    @Override
    public void error(String msg) {
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}
