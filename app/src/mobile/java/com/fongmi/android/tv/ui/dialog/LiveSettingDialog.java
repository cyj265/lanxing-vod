package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.widget.LinearLayoutCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogLiveSettingBinding;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

/**
 * 直播专属设置（对齐影视仓胶囊直选风格）：解码 / 画面缩放 / 超时换源 / EPG地址。
 * 每项一排胶囊选项，点选即生效，无二级菜单。
 */
public class LiveSettingDialog extends BaseBottomSheetDialog {

    private static final int[] TIMEOUTS = {5, 10, 15, 20, 25, 30};
    private static final int[] DECODE_VALUES = {PlayerEngine.HARD, PlayerEngine.SOFT};

    private DialogLiveSettingBinding binding;
    private PlayerManager player;

    public static LiveSettingDialog create() {
        return new LiveSettingDialog();
    }

    public LiveSettingDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public LiveSettingDialog show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof LiveSettingDialog) return this;
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogLiveSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        String[] scales = ResUtil.getStringArray(R.array.select_scale);
        LinearLayoutCompat rowScale = binding.rowScale;
        for (int i = 0; i < rowScale.getChildCount(); i++) {
            if (i < scales.length) ((android.widget.TextView) rowScale.getChildAt(i)).setText(scales[i]);
            rowScale.getChildAt(i).setSelected(i == LiveSetting.getScale());
        }
        LinearLayoutCompat rowDecode = binding.rowDecode;
        for (int i = 0; i < rowDecode.getChildCount(); i++) {
            rowDecode.getChildAt(i).setSelected(DECODE_VALUES[i] == player.getDecode());
        }
        syncTimeout();
        binding.epg.setText(LiveSetting.getEpg());
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(v -> dismiss());
        setRowClick(binding.rowDecode, this::onDecode);
        setRowClick(binding.rowScale, this::onScale);
        setRowClick(binding.rowTimeout, this::onTimeout);
        binding.reset.setOnClickListener(v -> onReset());
        binding.save.setOnClickListener(v -> onSave());
    }

    private void setRowClick(LinearLayoutCompat row, OnClickListener listener) {
        for (int i = 0; i < row.getChildCount(); i++) {
            int index = i;
            row.getChildAt(i).setOnClickListener(v -> listener.onClick(row, index));
        }
    }

    private interface OnClickListener {

        void onClick(LinearLayoutCompat row, int index);
    }

    private void onDecode(LinearLayoutCompat row, int index) {
        if (DECODE_VALUES[index] == player.getDecode()) return;
        player.setDecode(DECODE_VALUES[index]);
        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setSelected(i == index);
    }

    private void onScale(LinearLayoutCompat row, int index) {
        if (index == LiveSetting.getScale()) return;
        if (getActivity() instanceof Listener listener) listener.onScale(index);
        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setSelected(i == index);
    }

    private void onTimeout(LinearLayoutCompat row, int index) {
        if (index == getTimeoutIndex()) return;
        if (index == 0) {
            LiveSetting.putChange(false);
        } else {
            LiveSetting.putChange(true);
            LiveSetting.putTimeout(index == 1 ? 0 : TIMEOUTS[index - 2]);
        }
        syncTimeout();
    }

    private void syncTimeout() {
        LinearLayoutCompat row = binding.rowTimeout;
        int checked = getTimeoutIndex();
        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setSelected(i == checked);
    }

    private int getTimeoutIndex() {
        if (!LiveSetting.isChange()) return 0;
        int timeout = LiveSetting.getTimeout();
        if (timeout == 0) return 1;
        for (int i = 0; i < TIMEOUTS.length; i++) if (TIMEOUTS[i] == timeout) return i + 2;
        return 1;
    }

    private void onReset() {
        binding.epg.setText("");
        saveEpg("");
    }

    private void onSave() {
        saveEpg(binding.epg.getText() == null ? "" : binding.epg.getText().toString().trim());
        dismiss();
    }

    private void saveEpg(String epg) {
        LiveSetting.putEpg(epg);
        Notify.show(R.string.live_epg_address);
        if (getActivity() instanceof Listener listener) listener.onEpgSaved();
    }

    public interface Listener {

        void onScale(int scale);

        void onEpgSaved();
    }
}
