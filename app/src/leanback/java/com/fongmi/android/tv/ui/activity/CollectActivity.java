package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.BuiltinDepot;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityCollectBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.search.MultiConfigSearchEngine;
import com.fongmi.android.tv.setting.SearchBlockManager;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.CollectFragment;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectActivity extends BaseActivity implements CollectAdapter.OnClickListener {

    private ActivityCollectBinding mBinding;
    private CollectAdapter mAdapter;
    private PageAdapter mPageAdapter;
    private MultiConfigSearchEngine mSearch;

    private final List<Config> mLines = new ArrayList<>();
    private final Map<String, Collect> mSiteCollects = new LinkedHashMap<>();
    private final Map<String, Integer> mSitePositions = new LinkedHashMap<>();

    private Collect mAll;
    private Set<String> mBlocked;
    private View mOldView;

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        String value = getIntent().getStringExtra("keyword");
        return value == null ? "" : value;
    }

    private String siteLineKey(Config config, Site site) {
        return "site:" + config.getUrl() + "#" + site.getKey();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        saveKeyword();
        loadBlocked();
        loadLines();
        buildLineList();
        setPager();
        startSearch();
    }

    @Override
    protected void initEvent() {
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (position >= 0 && position < mAdapter.getItemCount()) {
                    mBinding.recycler.setSelectedPosition(position);
                    mBinding.recycler.requestFocus();
                }
            }
        });

        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void setRecyclerView() {
        // 原版 FM：搜索源标签横向排列在海报墙上方。
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(mAdapter = new CollectAdapter(this));
    }

    private void saveKeyword() {
        if (getKeyword().isEmpty()) return;
        List<String> items = Setting.getKeyword().isEmpty()
                ? new ArrayList<>()
                : App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void loadBlocked() {
        mBlocked = SearchBlockManager.getBlocked(this);
    }

    private void loadLines() {
        mLines.clear();
        mLines.addAll(BuiltinDepot.getConfigs());
    }

    private List<Config> getSearchOrder() {
        List<Config> source = new ArrayList<>(mLines);
        List<Config> order = new ArrayList<>();

        // 当前正在看的 Config 最先搜。
        Config current = VodConfig.get().getConfig();
        if (current != null && !current.getUrl().isEmpty()) moveConfig(source, order, current.getUrl());

        // OK影视的 yyy 聚合接口第二优先，但 UI 仍按“谁先返回谁先出现”。
        moveConfig(source, order, BuiltinDepot.EXTRA_YL_URL);
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

    private void buildLineList() {
        mAdapter.clear();
        mSiteCollects.clear();
        mSitePositions.clear();

        // 原版 FM 的关键：开始搜索时只有“全部”，绝不提前把线路定死。
        mAll = Collect.all();
        mAdapter.add(mAll);
        mAdapter.setBlocked(mBlocked);
    }

    private void setPager() {
        mBinding.pager.setAdapter(null);
        mBinding.pager.setAdapter(mPageAdapter = new PageAdapter(getSupportFragmentManager()));
    }

    private void startSearch() {
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        if (mLines.isEmpty() || getKeyword().isEmpty()) return;

        if (mSearch != null) mSearch.stop();
        mSearch = new MultiConfigSearchEngine();
        mSearch.start(getSearchOrder(), getKeyword(), new MultiConfigSearchEngine.Callback() {
            @Override
            public void onSites(Config config, List<Site> sites) {
                // 原版 FM 不在“拿到站点清单”时先画空标签。
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

    private Collect ensureSiteLine(Config config, Site site) {
        if (config == null || site == null || site.isEmpty()) return null;
        String key = siteLineKey(config, site);
        Collect collect = mSiteCollects.get(key);
        if (collect != null) return collect;

        // 标签显示真实 Site 名称；key 带 Config URL，避免不同 Config 同名 Site 冲突。
        String name = site.getName().isEmpty() ? config.getDesc() : site.getName();
        Site display = Site.get(key, name);
        collect = new Collect(display, new ArrayList<>());

        int position = mAdapter.getItemCount();
        mSiteCollects.put(key, collect);
        mSitePositions.put(key, position);
        mAdapter.add(collect);
        mAdapter.setBlocked(mBlocked);
        if (mPageAdapter != null) mPageAdapter.notifyDataSetChanged();
        return collect;
    }

    private void addSiteResult(Config config, Site site, List<Vod> items) {
        if (items == null || items.isEmpty() || config == null || site == null) return;

        // 哪个 Site 先返回，就先创建哪个标签并立即出这一批海报。
        Collect collect = ensureSiteLine(config, site);
        if (collect == null) return;

        String key = siteLineKey(config, site);
        collect.getList().addAll(items);
        Integer position = mSitePositions.get(key);
        if (position != null) pushToFragment(position, items);

        // “全部”页同样流式追加：不等待其它线路完成。
        if (!mBlocked.contains(key)) {
            mAll.getList().addAll(items);
            pushToFragment(0, items);
        }
    }

    private void pushToFragment(int position, List<Vod> items) {
        if (mPageAdapter == null) return;
        CollectFragment fragment = mPageAdapter.getCreated(position);
        if (fragment != null && fragment.isReady()) fragment.addVideo(items);
    }

    private void rebuildAll() {
        if (mAll == null) return;
        mAll.getList().clear();
        for (Map.Entry<String, Collect> entry : mSiteCollects.entrySet()) {
            if (!mBlocked.contains(entry.getKey())) mAll.getList().addAll(entry.getValue().getList());
        }
        CollectFragment all = mPageAdapter == null ? null : mPageAdapter.getCreated(0);
        if (all != null && all.isReady()) all.replaceVideo(mAll.getList());
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        mOldView = child == null ? null : child.itemView;
        if (mOldView == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = () -> {
        int position = mBinding.recycler.getSelectedPosition();
        if (position >= 0 && position < mAdapter.getItemCount()) mBinding.pager.setCurrentItem(position);
    };

    @Override
    public void onItemClick(int position, Collect item) {
        mBinding.recycler.setSelectedPosition(position);
        mBinding.pager.setCurrentItem(position);
    }

    @Override
    public void onItemDoubleClick(int position, Collect item) {
        if (item == null || item.getSite() == null) return;
        String key = item.getSite().getKey();
        if ("all".equals(key) || key.isEmpty()) return;

        boolean blocked = SearchBlockManager.toggle(this, key);
        mBlocked = SearchBlockManager.getBlocked(this);
        mAdapter.setBlocked(mBlocked);
        rebuildAll();

        String text = blocked ? "已永久屏蔽：" + item.getSite().getName() : "已永久解除：" + item.getSite().getName();
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }

    public void openVideo(Vod item) {
        if (item == null) return;
        Runnable action = () -> openVideoAfterSearch(item);
        if (mSearch != null) mSearch.stop(action);
        else action.run();
    }

    private void openVideoAfterSearch(Vod item) {
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
                    openAfterConfig(item);
                }

                @Override
                public void error(String msg) {
                    restoreAfterOpenFail(previous, msg);
                }
            });
        } else {
            openAfterConfig(item);
        }
    }

    private void restoreAfterOpenFail(Config previous, String msg) {
        if (previous == null || previous.getUrl().isEmpty()) {
            Notify.show(msg);
            return;
        }
        VodConfig.load(previous, new Callback() {
            @Override
            public void success() { Notify.show(msg); }
            @Override
            public void error(String ignored) { Notify.show(msg); }
        });
    }

    private void openAfterConfig(Vod item) {
        setResult(Activity.RESULT_OK);
        if (item.isFolder()) {
            VodActivity.start(this, item.getSiteKey(), Result.folder(item));
        } else {
            VideoActivity.collect(this, item.getSiteKey(), item.getId(), item.getName(), item.getPic());
        }
    }

    @Override
    protected void onDestroy() {
        if (mSearch != null) mSearch.stop();
        super.onDestroy();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        private final SparseArray<CollectFragment> fragments = new SparseArray<>();

        PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return CollectFragment.newInstance(getKeyword(), mAdapter.get(position));
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Object object = super.instantiateItem(container, position);
            if (object instanceof CollectFragment) fragments.put(position, (CollectFragment) object);
            return object;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            fragments.remove(position);
            super.destroyItem(container, position, object);
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
        }

        CollectFragment getCreated(int position) {
            return fragments.get(position);
        }
    }
}
