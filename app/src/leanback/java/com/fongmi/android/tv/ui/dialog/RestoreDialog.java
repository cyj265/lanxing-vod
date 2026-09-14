package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.cyj265.lanxingvod.databinding.DialogRestoreBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.RestoreAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class RestoreDialog implements RestoreAdapter.OnClickListener {

    private final DialogRestoreBinding binding;
    private final RestoreAdapter adapter;
    private final AlertDialog dialog;
    private Callback callback;

    public static RestoreDialog create(Activity activity) {
        return new RestoreDialog(activity);
    }

    public RestoreDialog(Activity activity) {
        this.binding = DialogRestoreBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.adapter = new RestoreAdapter(this);
    }

    public void show(Callback callback) {
        this.callback = callback;
        setRecyclerView();
        setDialog();
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
    }

    private void setDialog() {
        if (adapter.getItemCount() == 0) return;
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.4f);
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    @Override
    public void onItemClick(File item) {
        AppDatabase.restore(item, callback);
        dialog.dismiss();
    }

    @Override
    public void onDeleteClick(File item) {
        if (adapter.remove(item) == 0) dialog.dismiss();
    }
}