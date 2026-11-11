package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.ActivityFolderBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.FolderFragment;

public class FolderActivity extends BaseActivity {

    private static final String FRAGMENT_TAG = "folder";

    private ActivityFolderBinding mBinding;

    public static void start(Activity activity, String key, Result result) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, FolderActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        activity.startActivity(intent);
    }

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private Result getResult() {
        return getIntent().getParcelableExtra("result");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityFolderBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        // 进程被回收后恢复任务栈、或 Intent extras 被系统/部分 ROM 丢弃时，result 可能为 null；
        // 此时直接关闭页面回到上一页，避免启动即崩（Bugly #7010）
        Result result = getResult();
        if (result == null || result.getTypes().isEmpty()) {
            finish();
            return;
        }
        Class type = result.getTypes().get(0);
        setTitle(type.getTypeName());
        getSupportFragmentManager().beginTransaction().replace(R.id.container, FolderFragment.newInstance(getKey(), type, 8), FRAGMENT_TAG).commit();
    }

    private FolderFragment getFragment() {
        return (FolderFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        FolderFragment fragment = getFragment();
        if (fragment != null && fragment.canBack()) fragment.goBack();
        else super.onBackInvoked();
    }
}
