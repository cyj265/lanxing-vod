package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cyj265.lanxingvod.databinding.ActivityCategoryListBinding;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.CategoryListAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Douban;
import com.github.catvod.crawler.Spider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CategoryListActivity extends BaseActivity {

    private static final int MAX_FETCH = 10;

    private ActivityCategoryListBinding mBinding;
    private SiteViewModel mViewModel;
    private CategoryListAdapter mAdapter;
    private ExecutorService mDetailExecutor;
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
        mDetailExecutor = Executors.newSingleThreadExecutor();

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
        Site site = VodConfig.get().getHome();
        if (site.getType() == 3) {
            fetchSingleContents(site);
        } else {
            fetchBatchContents(site);
        }
    }

    // type=3 (JS/Python 爬虫)：detailContent 只处理单个 id，必须逐个请求
    private void fetchSingleContents(Site site) {
        int count = 0;
        for (Vod vod : mCurrentList) {
            if (count >= MAX_FETCH) break;
            String content = vod.getContent();
            if (content == null || content.isEmpty()) {
                count++;
                fetchSingle(site, vod);
            }
        }
    }

    private void fetchSingle(Site site, Vod vod) {
        mDetailExecutor.execute(() -> {
            String content = "";
            try {
                Spider spider = site.recent().spider();
                String detail = spider.detailContent(java.util.Collections.singletonList(vod.getId()));
                Result result = Result.fromJson(detail);
                if (result.getList() != null && !result.getList().isEmpty()) {
                    content = result.getList().get(0).getContent();
                }
            } catch (Exception ignored) {
            }
            if (content == null || content.isEmpty()) {
                content = Douban.getIntro(vod.getName());
            }
            if (content == null || content.isEmpty()) return;
            vod.setContent(content);
            int index = mCurrentList.indexOf(vod);
            if (index >= 0) {
                int pos = index;
                runOnUiThread(() -> mAdapter.notifyItemChanged(pos));
            }
        });
    }

    // type=0/1 (标准接口)：批量 ac=detail&ids= 获取
    private void fetchBatchContents(Site site) {
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
            mViewModel.detailContentBatch(site.getKey(), ids.toString());
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

    @Override
    protected void onDestroy() {
        if (mDetailExecutor != null) mDetailExecutor.shutdownNow();
        super.onDestroy();
    }
}
