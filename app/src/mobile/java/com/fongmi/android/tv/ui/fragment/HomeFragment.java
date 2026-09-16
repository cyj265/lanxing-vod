package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cyj265.lanxingvod.databinding.FragmentHomeBinding;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.CategoryListActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.ContinueAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendRowAdapter;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Response;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding mBinding;
    private RecommendRowAdapter mRecommendAdapter;
    private SiteViewModel mViewModel;
    private List<Class> mTypes;
    private final ExecutorService mRecommendExecutor = Executors.newFixedThreadPool(2);

    public static HomeFragment newInstance() {
        return new HomeFragment();
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
        setViewModel();
    }

    private void initView() {
        mBinding.continueRecycler.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        mBinding.continueMore.setOnClickListener(v -> HistoryActivity.start(requireActivity()));

        mRecommendAdapter = new RecommendRowAdapter(new RecommendRowAdapter.OnClickListener() {
            @Override
            public void onItemClick(Vod item) {
                VideoActivity.start(requireActivity(), VodConfig.get().getHome().getKey(), item.getId(), item.getName(), item.getPic());
            }
            @Override
            public void onMoreClick(int categoryIndex) {
                if (mTypes != null && categoryIndex >= 0 && categoryIndex < mTypes.size()) {
                    Class type = mTypes.get(categoryIndex);
                    java.util.List<Vod> cachedList = mRecommendAdapter.getListByCategoryIndex(categoryIndex);
                    if (cachedList != null && !cachedList.isEmpty()) {
                        CategoryListActivity.start(requireActivity(), type.getTypeId(), type.getTypeName(), new ArrayList<>(cachedList));
                    } else {
                        CategoryListActivity.start(requireActivity(), type.getTypeId(), type.getTypeName());
                    }
                }
            }
        });
        mBinding.recommendRecycler.setAdapter(mRecommendAdapter);
        mBinding.recommendRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.recommendRecycler.setHasFixedSize(true);
        mBinding.recommendRecycler.setItemViewCacheSize(6);
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
            VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic())
        ));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(requireActivity()).get(SiteViewModel.class);
        mViewModel.result.observe(getViewLifecycleOwner(), this::onHomeResult);
    }

    private void onHomeResult(Result result) {
        if (result == null || result.getTypes() == null || result.getTypes().isEmpty()) {
            mBinding.loading.setVisibility(View.GONE);
            return;
        }
        List<Class> types = result.getTypes();
        mTypes = types;
        int count = Math.min(6, types.size());
        mBinding.loading.setVisibility(View.VISIBLE);
        for (int i = 0; i < count; i++) {
            final Class type = types.get(i);
            final int index = i;
            mRecommendExecutor.submit(() -> {
                try {
                    List<Vod> list = loadCategory(type.getTypeId());
                    if (list != null && !list.isEmpty()) {
                        App.post(() -> {
                            if (mBinding == null || mRecommendAdapter == null) return;
                            mRecommendAdapter.addRow(type.getTypeName(), new ArrayList<>(list), index);
                            mBinding.loading.setVisibility(View.GONE);
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private List<Vod> loadCategory(String tid) throws Exception {
        Site site = VodConfig.get().getHome();
        if (site.getType() == 3) {
            Spider spider = site.recent().spider();
            String json = spider.categoryContent(tid, "1", false, new HashMap<>());
            return Result.fromJson(json).getList();
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("ac", site.getType() == 0 ? "videolist" : "detail");
            params.put("t", tid);
            params.put("pg", "1");
            if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
            String json = callApi(site, params);
            return Result.fromType(site.getType(), json).getList();
        }
    }

    private String callApi(Site site, ArrayMap<String, String> params) throws IOException {
        okhttp3.Call call = OkHttp.newCall(site.getApi(), site.getHeader(), params);
        try (Response response = call.execute()) {
            return response.body().string();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRecommendExecutor.shutdownNow();
        mBinding = null;
    }
}
