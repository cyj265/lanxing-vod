package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.databinding.FragmentSettingUiBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.ui.dialog.GlassAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SettingUiFragment extends BaseFragment {

    private FragmentSettingUiBinding mBinding;
    private String[] walls;

    private final ActivityResultLauncher<Intent> wallLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onWallPicked);

    public static SettingUiFragment newInstance() {
        return new SettingUiFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingUiBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        walls = ResUtil.getStringArray(R.array.select_wall);
        mBinding.autoPlayText.setText(Setting.getSwitch(PlayerSetting.isAutoPlay()));
        mBinding.homeDiscoverText.setText(Setting.getSwitch(Setting.isHomeDiscover()));
        mBinding.homeContinueText.setText(Setting.getSwitch(Setting.isHomeContinue()));
        setWallText();
    }

    @Override
    protected void initEvent() {
        mBinding.autoPlay.setOnClickListener(this::setAutoPlay);
        mBinding.homeDiscover.setOnClickListener(this::setHomeDiscover);
        mBinding.homeContinue.setOnClickListener(this::setHomeContinue);
        mBinding.wall.setOnClickListener(this::onWall);
    }

    private void setAutoPlay(View view) {
        PlayerSetting.putAutoPlay(!PlayerSetting.isAutoPlay());
        mBinding.autoPlayText.setText(Setting.getSwitch(PlayerSetting.isAutoPlay()));
    }

    private void setHomeDiscover(View view) {
        Setting.putHomeDiscover(!Setting.isHomeDiscover());
        mBinding.homeDiscoverText.setText(Setting.getSwitch(Setting.isHomeDiscover()));
        com.fongmi.android.tv.event.RefreshEvent.setting();
    }

    private void setHomeContinue(View view) {
        Setting.putHomeContinue(!Setting.isHomeContinue());
        mBinding.homeContinueText.setText(Setting.getSwitch(Setting.isHomeContinue()));
        com.fongmi.android.tv.event.RefreshEvent.setting();
    }

    private void setWallText() {
        if (Setting.isWallAuto()) {
            mBinding.wallText.setText(R.string.setting_wall_auto);
        } else if (Setting.getWall() <= 0) {
            mBinding.wallText.setText(R.string.wall_custom);
        } else {
            int index = Math.max(0, Math.min(Setting.getWall() - 1, walls.length - 1));
            mBinding.wallText.setText(walls[index]);
        }
    }

    /** 壁纸：内置多套 / 每日自动 / 本地图片 / 订阅壁纸地址 */
    private void onWall(View view) {
        List<String> items = new ArrayList<>();
        items.addAll(java.util.Arrays.asList(walls));
        items.add(getString(R.string.setting_wall_auto));
        items.add(getString(R.string.wall_local));
        items.add(getString(R.string.wall_remote));
        int autoIndex = walls.length;
        int checked = Setting.isWallAuto() ? autoIndex : (Setting.getWall() > 0 ? Math.min(Setting.getWall() - 1, walls.length - 1) : -1);
        new GlassAlertDialogBuilder(requireActivity())
                .setTitle(R.string.setting_wall)
                .setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(items.toArray(new String[0]), checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which < walls.length) {
                        Setting.putWallAuto(false);
                        Setting.putWall(which + 1);
                        Setting.putWallType(0);
                        setWallText();
                        ConfigEvent.wall();
                    } else if (which == autoIndex) {
                        Setting.putWallAuto(true);
                        setWallText();
                        ConfigEvent.wall();
                    } else if (which == autoIndex + 1) {
                        pickWall();
                    } else {
                        ConfigDialog.create().wall().show(this);
                    }
                }).show();
    }

    private void pickWall() {
        PermissionUtil.requestFile(this, granted -> FileChooser.from(wallLauncher).show("image/*", new String[]{"image/*", "image/gif", "video/*"}));
    }

    private void onWallPicked(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (path.isEmpty()) {
            Notify.show(R.string.wall_local_fail);
            return;
        }
        Notify.progress(requireActivity());
        WallConfig.setLocal(path, new Callback() {
            @Override
            public void success() {
                Notify.dismiss();
                setWallText();
                Notify.show(R.string.wall_local_done);
            }

            @Override
            public void error() {
                Notify.dismiss();
                Notify.show(R.string.wall_local_fail);
            }
        });
    }
}
