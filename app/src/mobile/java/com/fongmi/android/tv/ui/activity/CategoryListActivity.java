package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCategoryListBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.CategoryListAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Douban;
import com.github.catvod.crawler.Spider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CategoryListActivity extends BaseActivity {

    private static final int MAX_FETCH = 30;
    private static final int SPIDER_TIMEOUT = 5;
    private static final int CONCURRENCY = 3;

    // 进程内简介缓存：片名 -> 简介，退出页面再进直接命中
    private static final ConcurrentHashMap<String, String> sIntroCache = new ConcurrentHashMap<>();
    // 标记 detailContent 不可用的源 key，避免每次都等超时
    private static final Set<String> sSpiderBroken = ConcurrentHashMap.newKeySet();

    private ActivityCategoryListBinding mBinding;
    private SiteViewModel mViewModel;
    private CategoryListAdapter mAdapter;
    private ExecutorService mDetailExecutor;
    private ExecutorService mSpiderExecutor;
    private String mTypeId;
    private List<Vod> mCurrentList;
    // 榜单更多页：vodId 是豆瓣 id，直接用豆瓣 detail 补简介，跳过源 detailContent
    private boolean mFromDiscover;

    public static void start(Activity activity, String typeId, String typeName) {
        start(activity, typeId, typeName, null, false);
    }

    public static void start(Activity activity, String typeId, String typeName, ArrayList<Vod> initialList) {
        start(activity, typeId, typeName, initialList, false);
    }

    public static void start(Activity activity, String typeId, String typeName, ArrayList<Vod> initialList, boolean fromDiscover) {
        Intent intent = new Intent(activity, CategoryListActivity.class);
        intent.putExtra("typeId", typeId);
        intent.putExtra("typeName", typeName);
        intent.putExtra("fromDiscover", fromDiscover);
        if (initialList != null) intent.putParcelableArrayListExtra("initialList", initialList);
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
        mFromDiscover = getIntent().getBooleanExtra("fromDiscover", false);
        mBinding.toolbar.setTitle(typeName);
        mBinding.toolbar.setNavigationOnClickListener(v -> finish());

        mAdapter = new CategoryListAdapter(this::onItemClick);
        mBinding.recycler.setAdapter(mAdapter);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));

        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mDetailExecutor = Executors.newFixedThreadPool(CONCURRENCY);
        mSpiderExecutor = Executors.newSingleThreadExecutor();

        mViewModel.getResult().observe(this, result -> {
            mBinding.loading.setVisibility(View.GONE);
            if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                mCurrentList = result.getList();
                mAdapter.addAll(mCurrentList);
                fetchMissingContent();
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

    private boolean isEmptyContent(Vod vod) {
        return vod == null || TextUtils.isEmpty(vod.getContent());
    }

    private void fetchMissingContent() {
        if (mCurrentList == null || mCurrentList.isEmpty()) return;
        Site site = VodConfig.get().getHome();
        int count = 0;
        for (Vod vod : mCurrentList) {
            if (count >= MAX_FETCH) break;
            if (isEmptyContent(vod)) {
                count++;
                mDetailExecutor.execute(() -> fetchDetail(site, vod));
            }
        }
    }

    private void fetchDetail(Site site, Vod vod) {
        // 先查缓存，命中则瞬间显示
        String cached = sIntroCache.get(vod.getName());
        if (cached != null && !cached.isEmpty()) {
            updateContent(vod, cached);
            return;
        }
        String content = "";
        if (mFromDiscover) {
            // 榜单页：vodId 是豆瓣 id，直接凭 id 拿简介，快且命中率高
            content = Douban.getIntroById(vod.getId());
            if (TextUtils.isEmpty(content)) content = Douban.getIntro(vod.getName());
        } else {
            // 源分类页：先查源 detailContent（已标记不可用则跳过）
            if (!sSpiderBroken.contains(site.getKey())) {
                content = site.getType() == 3 ? fetchSourceContent(site, vod) : fetchApiContent(site, vod);
            }
            if (TextUtils.isEmpty(content)) {
                content = Douban.getIntroById(vod.getId());
            }
            if (TextUtils.isEmpty(content)) {
                content = Douban.getIntro(vod.getName());
            }
        }
        if (!TextUtils.isEmpty(content)) {
            sIntroCache.put(vod.getName(), content);
            updateContent(vod, content);
        }
    }

    private String fetchSourceContent(Site site, Vod vod) {
        try {
            Future<String> future = mSpiderExecutor.submit(() -> {
                try {
                    Spider spider = site.recent().spider();
                    String detail = spider.detailContent(java.util.Collections.singletonList(vod.getId()));
                    Result result = Result.fromJson(detail);
                    if (result.getList() != null && !result.getList().isEmpty()) {
                        String content = result.getList().get(0).getContent();
                        return content == null ? "" : content;
                    }
                } catch (Exception ignored) {
                }
                return "";
            });
            String detail = future.get(SPIDER_TIMEOUT, TimeUnit.SECONDS);
            if (TextUtils.isEmpty(detail)) {
                sSpiderBroken.add(site.getKey());
            }
            return detail == null ? "" : detail;
        } catch (Exception e) {
            sSpiderBroken.add(site.getKey());
            return "";
        }
    }

    private String fetchApiContent(Site site, Vod vod) {
        try {
            Future<String> future = mSpiderExecutor.submit(() -> {
                try {
                    Result result = SiteApi.detailContent(site.getKey(), vod.getId());
                    Vod detail = result.getVod();
                    String content = detail == null ? "" : detail.getContent();
                    return content == null ? "" : content;
                } catch (Exception ignored) {
                }
                return "";
            });
            String detail = future.get(SPIDER_TIMEOUT, TimeUnit.SECONDS);
            if (TextUtils.isEmpty(detail)) {
                sSpiderBroken.add(site.getKey());
            }
            return detail == null ? "" : detail;
        } catch (Exception e) {
            sSpiderBroken.add(site.getKey());
            return "";
        }
    }

    private void updateContent(Vod vod, String content) {
        if (vod == null || TextUtils.isEmpty(content)) return;
        vod.setContent(content);
        int index = mCurrentList.indexOf(vod);
        if (index >= 0) {
            int pos = index;
            runOnUiThread(() -> mAdapter.notifyItemChanged(pos));
        }
    }

    private void onItemClick(Vod item) {
        VideoActivity.start(this, VodConfig.get().getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    protected void onDestroy() {
        if (mDetailExecutor != null) mDetailExecutor.shutdownNow();
        if (mSpiderExecutor != null) mSpiderExecutor.shutdownNow();
        super.onDestroy();
    }
}
