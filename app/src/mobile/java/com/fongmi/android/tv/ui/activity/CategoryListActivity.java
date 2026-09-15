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
import java.util.List;

public class CategoryListActivity extends BaseActivity {

    private ActivityCategoryListBinding mBinding;
    private SiteViewModel mViewModel;
    private CategoryListAdapter mAdapter;
    private String mTypeId;
    private List<Vod> mCurrentList;

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

        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);

        mViewModel.result.observe(this, result -> {
            mBinding.loading.setVisibility(View.GONE);
            if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                mCurrentList = result.getList();
                mAdapter.addAll(mCurrentList);
                fetchMissingContent();
            }
        });

        mViewModel.detailList.observe(this, result -> {
            if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                mergeContent(result.getList());
            }
        });

        ArrayList<Vod> initialList = getIntent().getParcelableArrayListExtra("initialList");
        if (initialList != null && !initialList.isEmpty()) {
            mCurrentList = new ArrayList<>(initialList);
            mAdapter.addAll(mCurrentList);
            mBinding.loading.setVisibility(View.GONE);
            fetchMissingContent();
        } else {
            mViewModel.categoryContent(VodConfig.get().getHome().getKey(), mTypeId, "1", false, new HashMap<>());
        }
    }

    private void fetchMissingContent() {
        if (mCurrentList == null || mCurrentList.isEmpty()) return;
        StringBuilder ids = new StringBuilder();
        int count = 0;
        for (Vod vod : mCurrentList) {
            String content = vod.getContent();
            if (content == null || content.isEmpty()) {
                if (ids.length() > 0) ids.append(",");
                ids.append(vod.getId());
                count++;
            }
        }
        if (count > 0 && ids.length() > 0) {
            mViewModel.detailContentBatch(VodConfig.get().getHome().getKey(), ids.toString());
        }
    }

    private void mergeContent(List<Vod> details) {
        if (mCurrentList == null || details == null) return;
        HashMap<String, String> contentMap = new HashMap<>();
        for (Vod detail : details) {
            if (detail.getContent() != null && !detail.getContent().isEmpty()) {
                contentMap.put(detail.getId(), detail.getContent());
            }
        }
        boolean changed = false;
        for (Vod vod : mCurrentList) {
            String content = contentMap.get(vod.getId());
            if (content != null && !content.isEmpty()) {
                vod.setContent(content);
                changed = true;
            }
        }
        if (changed) mAdapter.notifyDataSetChanged();
    }

    private void onItemClick(Vod item) {
        VideoActivity.start(this, VodConfig.get().getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }
}
