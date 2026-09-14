package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.cyj265.lanxingvod.R;
import com.fongmi.android.tv.Setting;
import com.cyj265.lanxingvod.databinding.DialogSpeedBinding;
import com.fongmi.android.tv.impl.SpeedCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SpeedDialog {

    private final DialogSpeedBinding binding;
    private final SpeedCallback callback;
    private float value;

    public static SpeedDialog create(Fragment fragment) {
        return new SpeedDialog(fragment);
    }

    public SpeedDialog(Fragment fragment) {
        this.callback = (SpeedCallback) fragment;
        this.binding = DialogSpeedBinding.inflate(LayoutInflater.from(fragment.getContext()));
    }

    public void show() {
        initDialog();
        initView();
    }

    private void initDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext()).setTitle(R.string.player_speed).setView(binding.getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, this::onNegative).create();
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void initView() {
        binding.slider.setValue(value = Setting.getSpeed());
    }

    private void onPositive(DialogInterface dialog, int which) {
        callback.setSpeed(binding.slider.getValue());
        dialog.dismiss();
    }

    private void onNegative(DialogInterface dialog, int which) {
        callback.setSpeed(value);
        dialog.dismiss();
    }
}
