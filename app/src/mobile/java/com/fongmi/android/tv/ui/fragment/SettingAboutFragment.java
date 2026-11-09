package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.databinding.FragmentSettingAboutBinding;
import com.fongmi.android.tv.ui.base.BaseFragment;

public class SettingAboutFragment extends BaseFragment {

    private FragmentSettingAboutBinding mBinding;

    public static SettingAboutFragment newInstance() {
        return new SettingAboutFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingAboutBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
    }

    @Override
    protected void initEvent() {
        mBinding.version.setOnClickListener(v -> Updater.create().force().start(requireActivity()));
    }
}
