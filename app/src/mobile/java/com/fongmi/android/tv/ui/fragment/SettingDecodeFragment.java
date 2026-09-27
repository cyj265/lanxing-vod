package com.fongmi.android.tv.ui.fragment;

import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.setting.DecodeSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.DecodeDialog;
import com.fongmi.android.tv.utils.ResUtil;

public class SettingDecodeFragment extends BaseFragment {

    private SettingDecodeBinding mBinding;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = SettingDecodeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        mBinding.aac.setOnClickListener(this::setPreferAAC);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.audioPrefer.setOnClickListener(this::setAudioPrefer);
        mBinding.videoPrefer.setOnClickListener(this::setVideoPrefer);
        mBinding.audioPassThrough.setOnClickListener(this::setAudioPassThrough);
        mBinding.dolbyVisionOutput.setOnClickListener(this::setDolbyVisionOutput);
    }

    @Override
    protected void initEvent() {
        mBinding.aacText.setOnClickListener(this::setPreferAAC);
        mBinding.tunnelText.setOnClickListener(this::setTunnel);
        mBinding.audioPreferText.setOnClickListener(this::setAudioPrefer);
        mBinding.videoPreferText.setOnClickListener(this::setVideoPrefer);
        mBinding.audioPassThroughText.setOnClickListener(this::setAudioPassThrough);
        mBinding.dolbyVisionOutputText.setOnClickListener(this::setDolbyVisionOutput);
    }

    @Override
    protected void initData() {
    }

    @Override
    public void onRefresh() {
        refresh();
    }

    @Override
    protected void onRecycled() {
        mBinding = null;
    }

    private void refresh() {
        mBinding.aacText.setText(Setting.getSwitch(DecodeSetting.isPreferAAC()));
        mBinding.tunnelText.setText(Setting.getSwitch(DecodeSetting.isTunnel()));
        mBinding.audioPreferText.setText(Setting.getSwitch(DecodeSetting.isAudioPrefer()));
        mBinding.videoPreferText.setText(Setting.getSwitch(DecodeSetting.isVideoPrefer()));
        mBinding.dolbyVisionOutputText.setText(ResUtil.getStringArray(R.array.select_dolby_vision_output)[DecodeSetting.getDolbyVisionOutputPolicy()]);
        mBinding.audioPassThroughText.setText(Setting.getSwitch(DecodeSetting.isAudioPassThrough()));
    }

    private void setTunnel(View view) {
        DecodeSetting.putTunnel(!DecodeSetting.isTunnel());
        mBinding.tunnelText.setText(Setting.getSwitch(DecodeSetting.isTunnel()));
    }

    private void setAudioPassThrough(View view) {
        DecodeSetting.putAudioPassThrough(!DecodeSetting.isAudioPassThrough());
        mBinding.audioPassThroughText.setText(Setting.getSwitch(DecodeSetting.isAudioPassThrough()));
    }

    private void setAudioPrefer(View view) {
        DecodeSetting.putAudioPrefer(!DecodeSetting.isAudioPrefer());
        mBinding.audioPreferText.setText(Setting.getSwitch(DecodeSetting.isAudioPrefer()));
    }

    private void setVideoPrefer(View view) {
        DecodeSetting.putVideoPrefer(!DecodeSetting.isVideoPrefer());
        mBinding.videoPreferText.setText(Setting.getSwitch(DecodeSetting.isVideoPrefer()));
    }

    private void setDolbyVisionOutput(View view) {
        int mode = (DecodeSetting.getDolbyVisionOutputPolicy() + 1) % (DolbyVisionOutputPolicy.ASSUME_UNSUPPORTED + 1);
        DecodeSetting.putDolbyVisionOutputPolicy(mode);
        mBinding.dolbyVisionOutputText.setText(ResUtil.getStringArray(R.array.select_dolby_vision_output)[DecodeSetting.getDolbyVisionOutputPolicy()]);
    }
}
