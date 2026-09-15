package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cyj265.lanxingvod.databinding.FragmentHomeBinding;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.ui.activity.CategoryListActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.adapter.ContinueAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendRowAdapter;
import com.fongmi.android.tv.utils.Douban;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内置精选首页：豆瓣榜单数据，不依赖用户配置的源
 * 参照影视仓首页布局：多个榜单横滑 + 更多跳转
 */
public class DiscoverFragment extends Fragment {

    private FragmentHomeBinding mBinding;
    private RecommendRowAdapter mRecommendAdapter;
    private ExecutorService mExecutor;
    private boolean mLoading;

    // 榜单配置：显示名 -> 豆瓣 collection_id
    private static final List<String[]> RANKS = Arrays.asList(
            new String[]{"豆瓣推荐", "movie_weekly_best"},
            new String[]{"电视榜", "tv_hot"},
            new String[]{"电影榜", "movie_hot"},
            new String[]{"正在热映", "movie_showing"},
            new String[]{"热门动漫", "tv_animation"},
            new String[]{"热门韩剧", "tv_korean"},
            new String[]{"电影Top250", "movie_top250"}
    );

    public static DiscoverFragment newInstance() {
        return new DiscoverFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentHomeBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
        setContinue();
        loadRanks();
    }

    private void initView() {
        mBinding.continueRecycler.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        mBinding.continueMore.setOnClickListener(v -> HistoryActivity.start(requireActivity()));
        mBinding.emptyText.setOnClickListener(v -> {
            mBinding.emptyText.setVisibility(View.GONE);
            loadRanks();
        });

        mRecommendAdapter = new RecommendRowAdapter(new RecommendRowAdapter.OnClickListener() {
            @Override
            public void onItemClick(Vod item) {
                // 豆瓣榜单没有源vodId，用片名搜索当前源
                SearchActivity.start(requireActivity(), item.getName());
            }

            @Override
            public void onMoreClick(int categoryIndex) {
                if (categoryIndex >= 0 && categoryIndex < RANKS.size()) {
                    String title = RANKS.get(categoryIndex)[0];
                    List<Vod> list = mRecommendAdapter.getListByCategoryIndex(categoryIndex);
                    if (list != null && !list.isEmpty()) {
                        CategoryListActivity.start(requireActivity(), "", title, new ArrayList<>(list));
                    }
                }
            }
        });
        mBinding.recommendRecycler.setAdapter(mRecommendAdapter);
        mBinding.recommendRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.recommendRecycler.setHasFixedSize(true);
        mBinding.recommendRecycler.setItemViewCacheSize(8);
    }

    private void setContinue() {
        List<History> items = History.get();
        if (items == null || items.isEmpty()) {
            mBinding.continueLayout.setVisibility(View.GONE);
            return;
        }
        mBinding.continueLayout.setVisibility(View.VISIBLE);
        int count = Math.min(items.size(), 10);
        List<History> list = items.subList(0, count);
        mBinding.continueRecycler.setAdapter(new ContinueAdapter(list, item ->
                com.fongmi.android.tv.ui.activity.VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic())
        ));
    }

    private synchronized void loadRanks() {
        if (mLoading) return;
        mLoading = true;
        mRecommendAdapter.clear();
        mBinding.loading.setVisibility(View.VISIBLE);
        mBinding.emptyText.setVisibility(View.GONE);
        mExecutor = Executors.newFixedThreadPool(3);
        final AtomicInteger done = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(RANKS.size());
        for (int i = 0; i < RANKS.size(); i++) {
            final int index = i;
            final String title = RANKS.get(i)[0];
            final String collectionId = RANKS.get(i)[1];
            mExecutor.submit(() -> {
                try {
                    List<Vod> list = Douban.getRank(collectionId, 12);
                    if (list != null && !list.isEmpty()) {
                        App.post(() -> mRecommendAdapter.addRow(title, list, index));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.incrementAndGet();
                    latch.countDown();
                }
            });
        }
        mExecutor.submit(() -> {
            try {
                latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            App.post(() -> {
                mLoading = false;
                mBinding.loading.setVisibility(View.GONE);
                if (mRecommendAdapter.getItemCount() == 0) {
                    mBinding.emptyText.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mExecutor != null) mExecutor.shutdownNow();
        mBinding = null;
    }
}
