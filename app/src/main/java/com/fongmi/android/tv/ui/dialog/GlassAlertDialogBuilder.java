package com.fongmi.android.tv.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 统一版本的 MaterialAlertDialogBuilder。
 * <p>
 * 设置窗口底色必须等 dialog 真正 show 之后（onCreateDialog / initView 阶段 getDialog()
 * 仍为 null），所以在 create() 里挂 OnShowListener，让所有散装弹窗和 BaseAlertDialog
 * 子类都自动套上同一套玻璃底板与背景模糊，调用方无需逐个处理。
 */
public class GlassAlertDialogBuilder extends MaterialAlertDialogBuilder {

    public GlassAlertDialogBuilder(@NonNull Context context) {
        super(context);
    }

    @NonNull
    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        dialog.setOnShowListener(d -> DialogGlass.apply(dialog));
        return dialog;
    }
}
