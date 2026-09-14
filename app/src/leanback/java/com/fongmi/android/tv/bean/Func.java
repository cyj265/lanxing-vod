package com.fongmi.android.tv.bean;

import android.annotation.SuppressLint;

import androidx.annotation.Nullable;

import com.cyj265.lanxingvod.R;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.utils.ResUtil;

public class Func implements Diffable<Func> {

    private final int resId;
    private int drawable;

    public static Func create(int resId) {
        return new Func(resId);
    }

    public Func(int resId) {
        this.resId = resId;
        this.setDrawable();
    }

    public int getResId() {
        return resId;
    }

    public int getDrawable() {
        return drawable;
    }

    public String getText() {
        return ResUtil.getString(resId);
    }

    @SuppressLint("NonConstantResourceId")
    public void setDrawable() {
        switch (resId) {
            case R.string.home_vod:
                this.drawable = R.drawable.ic_home_vod;
                break;
            case R.string.home_live:
                this.drawable = R.drawable.ic_home_live;
                break;
            case R.string.home_keep:
                this.drawable = R.drawable.ic_home_keep;
                break;
            case R.string.home_push:
                this.drawable = R.drawable.ic_home_push;
                break;
            case R.string.home_cast:
                this.drawable = R.drawable.ic_home_cast;
                break;
            case R.string.home_search:
                this.drawable = R.drawable.ic_home_search;
                break;
            case R.string.home_setting:
                this.drawable = R.drawable.ic_home_setting;
                break;
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Func it)) return false;
        return getResId() == it.getResId();
    }

    @Override
    public boolean isSameItem(Func other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Func other) {
        return equals(other);
    }
}
