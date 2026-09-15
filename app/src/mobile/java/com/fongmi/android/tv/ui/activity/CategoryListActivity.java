package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cyj265.lanxingvod.databinding.ActivityCategoryListBinding;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.CategoryListAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class CategoryListActivity extends BaseActivity {

    private ActivityCategoryListBinding mBinding;
    private SiteViewModel mViewModel;
    private CategoryListAdapter mAdapter;
    private String mTypeId;

    public static void start(Activity activity, String typeId, String typeName) {
        Intent intent = new Intent(activity, CategoryListActivity.class);
        intent.putExtra("typeId", typeId);
        intent.putExtra("typeName", typeName);
        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public static void start(Activity activity, String typeId, String typeName, ArrayList<Vod> initialList) {
        Intent intent = new Intent(activity, CategoryListActivity.class);
        intent.putExtra("typeId", typeId);
        intent.putExtra("typeName", typeName);
        intent.putParcelableArrayListExtra("initialList", initialList);
        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected ActivityCategoryListBinding getBinding() {
        return mBinding = ActivityCategoryListBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        String typeName = getIntent().getStringExtra("typeName");
        mTypeId = getIntent().getStringExtra("typeId");
        mBinding.toolbar.setTitle(typeName);
        mBinding.toolbar.setNavigationOnClickListener(v -> finish());

        mAdapter = new CategoryListAdapter(this::onItemClick);
        mBinding.recycler.setAdapter(mAdapter);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));

        ArrayList<Vod> initialList = getIntent().getParcelableArrayListExtra("initialList");
        if (initialList != null && !initialList.isEmpty()) {
            mAdapter.addAll(initialList);
            mBinding.loading.setVisibility(View.GONE);
        } else {
            mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
            mViewModel.result.observe(this, result -> {
                mBinding.loading.setVisibility(View.GONE);
                if (result != null && result.getList() != null) {
                    mAdapter.addAll(result.getList());
                }
            });
            mViewModel.categoryContent(VodConfig.get().getHome().getKey(), mTypeId, "1", false, new HashMap<>());
        }
    }

    private void onItemClick(Vod item) {
        VideoActivity.start(this, VodConfig.get().getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }
}
