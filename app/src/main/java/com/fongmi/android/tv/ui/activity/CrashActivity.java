package com.fongmi.android.tv.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityCrashBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.CrashLog;
import com.github.catvod.utils.Prefers;

import java.util.Objects;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;

public class CrashActivity extends BaseActivity {

    private ActivityCrashBinding mBinding;

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCrash();
    }

    @Override
    protected void initEvent() {
        mBinding.details.setOnClickListener(v -> showError());
        mBinding.share.setOnClickListener(v -> share());
        mBinding.restart.setOnClickListener(v -> CustomActivityOnCrash.restartApplication(this, Objects.requireNonNull(CustomActivityOnCrash.getConfigFromIntent(getIntent()))));
    }

    private void setCrash() {
        // 这里的信息比 handler 里那份更全（含设备信息和操作记录），覆盖写回去
        CrashLog.saveDetails(this, getDetails());
        String log = CustomActivityOnCrash.getActivityLogFromIntent(getIntent());
        if (TextUtils.isEmpty(log)) return;
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isEmpty()) continue;
            if (lines[i].contains(HomeActivity.class.getSimpleName())) {
                Prefers.put("crash", true);
                break;
            }
        }
    }

    private String getDetails() {
        return CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent());
    }

    private void showError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_details_title)
                .setMessage(getDetails())
                .setPositiveButton(R.string.crash_details_close, null)
                .setNeutralButton(R.string.crash_copy, (dialog, which) -> copy())
                .show();
    }

    /**
     * 先复制到剪贴板再尝试系统分享：电视盒子上通常没有分享目标，至少保证日志能复制走。
     */
    private void share() {
        String details = getDetails();
        copy(details);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_details_title));
        intent.putExtra(Intent.EXTRA_TEXT, details);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.crash_share)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void copy() {
        copy(getDetails());
        Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show();
    }

    private void copy(String details) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_details_title), details));
    }
}
