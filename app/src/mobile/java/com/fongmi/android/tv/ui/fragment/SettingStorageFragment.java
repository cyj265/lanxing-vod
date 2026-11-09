package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.databinding.FragmentSettingStorageBinding;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;

public class SettingStorageFragment extends BaseFragment {

    private FragmentSettingStorageBinding mBinding;

    public static SettingStorageFragment newInstance() {
        return new SettingStorageFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingStorageBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setCacheText();
    }

    @Override
    protected void initEvent() {
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.restore.setOnClickListener(this::onRestore);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    private void onCache(View view) {
        try {
            // 主线程释放 Glide 内存缓存, 否则刚清完又被复用填充
            Glide.get(requireActivity()).clearMemory();
        } catch (Throwable ignored) {
        }
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> BackupManager.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().show(requireActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    /** 恢复备份后重新加载源与壁纸，避免界面上还是旧数据 */
    private void initConfig() {
        VodConfig.get().init().load(new Callback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }
}
