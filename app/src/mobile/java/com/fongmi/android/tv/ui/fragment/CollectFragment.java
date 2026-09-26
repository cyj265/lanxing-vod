package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.BuiltinDepot;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentCollectBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.search.MultiConfigSearchEngine;
import com.fongmi.android.tv.setting.SearchBlockManager;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectFragment extends BaseFragment implements
        MenuProvider,
        CollectAdapter.OnClickListener,
        SearchAdapter.OnClickListener,
        CustomScroller.Callback {

    private FragmentCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private MultiConfigSearchEngine mSearch;

    private final List<Config> mLines = new ArrayList<>();
    private final Map<Integer, Collect> mCollects = new HashMap<>();
    private final Map<String, Collect> mSiteCollects = new HashMap<>();
    private final Map<String, Config> mSiteConfigs = new HashMap<>();
    private final Set<Integer> mExpandableConfigs = new HashSet<>();
    private final Map<Integer, Vod> mVodByIdentity = new HashMap<>();

    private Set<String> mBlocked = new HashSet<>();

    public static CollectFragment newInstance(String keyword) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private String lineKey(Config config) {
        return "config:" + config.getUrl();
    }

    private String siteLineKey(Config config, Site site) {
        return "site:" + config.getUrl() + "#" + site.getKey();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle(getKeyword());
    }

    @Override
    protected void initView() {
        setRecyclerView();
        loadBlocked();
        loadLines();
        setWidth();
        buildLineList();
        startSearch();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putBoolean("edit", true);
            getParentFragmentManager().setFragmentResult("result", result);
            getParentFragmentManager().popBackStack();
        });
    }

    private void setRecyclerView() {
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));

        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        ((GridLayoutManager) mBinding.recycler.getLayoutManager()).setSpanCount(getCount());
    }

    private void loadBlocked() {
        mBlocked = SearchBlockManager.getBlocked(requireContext());
    }

    private void loadLines() {
        mLines.clear();
        // 左侧显示顺序保持原样。
        mLines.addAll(BuiltinDepot.getConfigs());
    }

    private List<Config> getSearchOrder() {
        List<Config> source = new ArrayList<>(mLines);
        List<Config> order = new ArrayList<>();

        Config current = VodConfig.get().getConfig();
        if (current != null && !current.getUrl().isEmpty()) {
            moveConfig(source, order, current.getUrl());
        }

        order.addAll(source);
        return order;
    }

    private void moveConfig(List<Config> source, List<Config> target, String url) {
        if (url == null || url.isEmpty()) return;
        for (int i = 0; i < source.size(); i++) {
            if (url.equals(source.get(i).getUrl())) {
                target.add(source.remove(i));
                return;
            }
        }
    }

    private void setWidth() {
        int width = 0;
        int space = ResUtil.dp2px(48);
        int maxWidth = ResUtil.getScreenWidth() / (getCount() + 1) - ResUtil.dp2px(40);

        for (Config config : mLines) {
            width = Math.max(width, ResUtil.getTextWidth(config.getDesc(), 14));
        }

        int contentWidth = width + space;
        int minWidth = ResUtil.dp2px(120);
        int finalWidth = Math.clamp(contentWidth, minWidth, Math.max(minWidth, maxWidth));

        ViewGroup.LayoutParams params = mBinding.collect.getLayoutParams();
        params.width = finalWidth;
        mBinding.collect.setLayoutParams(params);
    }

    private int getCount() {
        int count = ResUtil.isLand(requireActivity()) ? 2 : 1;
        if (ResUtil.isPad()) count++;
        return count;
    }

    private void buildLineList() {
        mCollects.clear();
        mSiteCollects.clear();
        mSiteConfigs.clear();
        mExpandableConfigs.clear();

        for (Config config : mLines) {
            Site fake = Site.get(lineKey(config), config.getDesc());
            mCollects.put(config.getId(), new Collect(fake, new ArrayList<>()));
        }

        refreshLineList(true);
        mSearchAdapter.setItems(new ArrayList<>());
    }

    private void refreshLineList(boolean first) {
        List<Collect> items = new ArrayList<>();
        Collect all = mCollectAdapter.getItemCount() > 0
                ? findExisting("all")
                : null;
        if (all == null) all = Collect.all();
        items.add(all);

        for (Config config : mLines) {
            Collect group = mCollects.get(config.getId());
            if (group != null) items.add(group);

            if (mExpandableConfigs.contains(config.getId())) {
                for (Map.Entry<String, Config> entry : mSiteConfigs.entrySet()) {
                    if (entry.getValue().getId() != config.getId()) continue;
                    Collect child = mSiteCollects.get(entry.getKey());
                    if (child != null) items.add(child);
                }
            }
        }

        mCollectAdapter.setItems(items, () -> {
            if (first) mCollectAdapter.setSelected(0);
            mCollectAdapter.setBlocked(mBlocked);
        });
    }

    private Collect findExisting(String key) {
        for (Collect item : mCollectAdapter.getItems()) {
            if (item.getSite().getKey().equals(key)) return item;
        }
        return null;
    }

    private void ensureSiteLine(Config config, Site site) {
        if (!mExpandableConfigs.contains(config.getId()) || site == null || site.isEmpty()) return;

        String key = siteLineKey(config, site);
        if (mSiteCollects.containsKey(key)) return;

        Site fake = Site.get(key, "↳ " + site.getName());
        mSiteCollects.put(key, new Collect(fake, new ArrayList<>()));
        mSiteConfigs.put(key, config);
        refreshLineList(false);
    }

    private void startSearch() {
        if (mLines.isEmpty() || getKeyword().isEmpty()) return;

        if (mSearch != null) mSearch.stop();
        mSearch = new MultiConfigSearchEngine();

        mSearch.start(getSearchOrder(), getKeyword(), new MultiConfigSearchEngine.Callback() {
            @Override
            public void onSites(Config config, List<Site> sites) {
                // 空壳版对所有用户添加的 Config 一视同仁：
                // 单 Site 不重复展开；多 Site 自动显示为子线路。
                if (sites == null || sites.size() <= 1) return;
                mExpandableConfigs.add(config.getId());
                for (Site site : sites) ensureSiteLine(config, site);
            }

            @Override
            public void onSiteResult(Config config, Site site, List<Vod> items) {
                addSiteResult(config, site, items);
            }

            @Override
            public void onLineError(Config config, Throwable error) {
            }

            @Override
            public void onFinish() {
            }
        });
    }

    private void addSiteResult(Config config, Site site, List<Vod> items) {
        if (items == null || items.isEmpty() || site == null) return;

        Collect group = mCollects.get(config.getId());
        if (group == null) return;

        boolean total = mExpandableConfigs.contains(config.getId());
        String childKey = siteLineKey(config, site);
        boolean childBlocked = total && mBlocked.contains(childKey);

        if (!childBlocked) group.getList().addAll(items);

        if (total) {
            ensureSiteLine(config, site);
            Collect child = mSiteCollects.get(childKey);
            if (child != null) child.getList().addAll(items);
        }

        Collect all = mCollectAdapter.getItem(0);
        if (!mBlocked.contains(lineKey(config)) && !childBlocked) {
            all.getList().addAll(items);
        }

        Collect active = mCollectAdapter.getActivated();
        String activeKey = active.getSite().getKey();

        if ("all".equals(activeKey)) {
            if (!mBlocked.contains(lineKey(config)) && !childBlocked) mSearchAdapter.addAll(items);
        } else if (activeKey.equals(lineKey(config))) {
            if (!childBlocked) mSearchAdapter.addAll(items);
        } else if (activeKey.equals(childKey)) {
            mSearchAdapter.addAll(items);
        }
    }

    private void rebuildAll() {
        if (mCollectAdapter.getItemCount() == 0) return;

        Collect all = mCollectAdapter.getItem(0);
        all.getList().clear();

        for (Config config : mLines) {
            Collect group = mCollects.get(config.getId());
            if (group == null) continue;

            if (mExpandableConfigs.contains(config.getId())) {
                group.getList().clear();

                for (Map.Entry<String, Config> entry : mSiteConfigs.entrySet()) {
                    if (entry.getValue().getId() != config.getId()) continue;
                    String childKey = entry.getKey();
                    Collect child = mSiteCollects.get(childKey);
                    if (child == null || mBlocked.contains(childKey)) continue;
                    group.getList().addAll(child.getList());
                }
            }

            if (!mBlocked.contains(lineKey(config))) all.getList().addAll(group.getList());
        }

        Collect active = mCollectAdapter.getActivated();
        String activeKey = active.getSite().getKey();

        if ("all".equals(activeKey)) {
            mSearchAdapter.setItems(all.getList(), () -> mBinding.recycler.scrollToPosition(0));
        } else {
            // 当前如果正好是被改变的多 Site Config/子源，也立即刷新。
            mSearchAdapter.setItems(active.getList(), () -> mBinding.recycler.scrollToPosition(0));
        }
    }

    @Override
    public void onItemClick(int position, Collect item) {
        mCollectAdapter.setSelected(position);
        mSearchAdapter.setItems(item.getList(), () -> mBinding.recycler.scrollToPosition(0));
    }

    @Override
    public void onItemDoubleClick(int position, Collect item) {
        if (item == null || item.getSite() == null) return;

        String key = item.getSite().getKey();
        if ("all".equals(key) || key.isEmpty()) return;

        boolean blocked = SearchBlockManager.toggle(requireContext(), key);
        mBlocked = SearchBlockManager.getBlocked(requireContext());
        mCollectAdapter.setBlocked(mBlocked);
        rebuildAll();

        String text = blocked
                ? "已永久屏蔽：" + item.getSite().getName()
                : "已永久解除：" + item.getSite().getName();

        android.widget.Toast.makeText(requireContext(), text, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onItemClick(Vod item) {
        Runnable action = () -> openAfterSearch(item);
        if (mSearch != null) mSearch.stop(action);
        else action.run();
    }

    private void openAfterSearch(Vod item) {
        Config config = item.getSearchCid() <= 0 ? null : Config.find(item.getSearchCid());

        if (config != null && config.getId() != VodConfig.getCid()) {
            Config previous = VodConfig.get().getConfig();

            VodConfig.load(config, new Callback() {
                @Override
                public void success() {
                    if (VodConfig.get().getSite(item.getSiteKey()).isEmpty()) {
                        restoreAfterOpenFail(previous, "线路站点初始化失败");
                        return;
                    }
                    openVideo(item);
                }

                @Override
                public void error(String msg) {
                    restoreAfterOpenFail(previous, msg);
                }
            });
        } else {
            openVideo(item);
        }
    }

    private void restoreAfterOpenFail(Config previous, String msg) {
        if (previous == null || previous.getUrl().isEmpty()) {
            Notify.show(msg);
            return;
        }

        VodConfig.load(previous, new Callback() {
            @Override
            public void success() {
                Notify.show(msg);
            }

            @Override
            public void error(String ignored) {
                Notify.show(msg);
            }
        });
    }

    private void openVideo(Vod item) {
        if (item.isFolder()) {
            FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        } else {
            VideoActivity.collect(
                    requireActivity(),
                    item.getSiteKey(),
                    item.getId(),
                    item.getName(),
                    item.getPic()
            );
        }
    }

    @Override
    public boolean onLoadMore(String page) {
        return false;
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        }
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        if (mSearch != null) mSearch.stop();
        requireActivity().removeMenuProvider(this);
        super.onDestroyView();
    }
}
