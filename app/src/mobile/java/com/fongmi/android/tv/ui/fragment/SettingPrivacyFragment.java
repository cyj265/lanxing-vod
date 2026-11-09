package com.fongmi.android.tv.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.databinding.FragmentSettingPrivacyBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.utils.CrashLog;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.fongmi.android.tv.ui.dialog.GlassAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SettingPrivacyFragment extends BaseFragment {

    private FragmentSettingPrivacyBinding mBinding;

    public static SettingPrivacyFragment newInstance() {
        return new SettingPrivacyFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPrivacyBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
    }

    @Override
    protected void initEvent() {
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.viewLog.setOnClickListener(this::onViewLog);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void setDoh(View view) {
        new GlassAlertDialogBuilder(requireActivity())
                .setTitle(R.string.setting_doh)
                .setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(getDohList(), getDohIndex(), (dialog, which) -> {
                    setDoh(VodConfig.get().getDoh().get(which));
                    dialog.dismiss();
                }).show();
    }

    private void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    /** 查看最近一次崩溃日志：复用 CrashLog 落盘的文件，支持一键复制出去反馈 */
    private void onViewLog(View view) {
        String log = CrashLog.readLatest(requireContext());
        if (log.isEmpty()) {
            Notify.show(R.string.log_empty);
            return;
        }
        new GlassAlertDialogBuilder(requireActivity())
                .setTitle(R.string.log_title)
                .setMessage(log)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.log_copy, (dialog, which) -> {
                    ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("crash-log", log));
                    Notify.show(R.string.log_copied);
                }).show();
    }
}
