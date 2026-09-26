package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityVodBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.fragment.FolderFragment;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class VodActivity extends BaseActivity implements TypeAdapter.OnClickListener, ConfigListener, SiteListener {

    private ActivityVodBinding mBinding;
    private TypeAdapter mAdapter;
    private SiteViewModel mViewModel;
    private Result mResult;
    private String mKey;
    private Config mPreviousConfig;
    private Site mPreviousSite;
    private boolean mSwitchingConfig;
    private boolean mSwitchingSite;
    private View mOldView;
    private Clock mClock;

    public static void start(Activity activity, Result result) {
        start(activity, VodConfig.get().getHome().getKey(), result);
    }

    public static void start(Activity activity, String key, Result result) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, VodActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        activity.startActivity(intent);
    }

    private String getKey() {
        return TextUtils.isEmpty(mKey) ? getIntent().getStringExtra("key") : mKey;
    }

    private Result getResult() {
        return mResult == null ? getIntent().getParcelableExtra("result") : mResult;
    }

    private Class getType() {
        return mAdapter.get(mBinding.pager.getCurrentItem());
    }

    private FolderFragment getFragment() {
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVodBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.clock);
        mKey = getIntent().getStringExtra("key");
        mResult = getIntent().getParcelableExtra("result");
        setViewModel();
        setTitle();
        ImgUtil.logo(mBinding.logo);
        setRecyclerView();
        setTypes();
        setPager();
    }

    @Override
    protected void initEvent() {
        // 左上角图标：选择顶层 Config / 总线路。
        mBinding.logo.setClickable(true);
        mBinding.logo.setOnClickListener(v -> HistoryDialog.create().vod().readOnly().show(this));

        // 标题文字：选择“当前 Config 里面”的真实 Site / 直线路。
        // 例如当前 Config 是 yyy 接口，这里会自然列出腾讯备、爱奇艺、猎手、袋鼠等。
        mBinding.title.setClickable(true);
        mBinding.title.setOnClickListener(v -> SiteDialog.create().show(this));

        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.recycler.setSelectedPosition(position);
                mBinding.recycler.requestFocus();
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            if (!mSwitchingSite || result == null) return;

            if (result.getTypes().isEmpty() && result.getList().isEmpty()) {
                if (mSwitchingConfig) restorePreviousConfig("线路首页初始化失败");
                else restorePreviousSite("当前直线路首页初始化失败");
                return;
            }

            // 关键修复：
            // FongMi 的 VodActivity / FolderFragment 原本不是为“同一 Activity 热换 Config”
            // 设计的。PageAdapter.destroyItem() 也不会真正销毁旧 Fragment。
            // 内置线路常常 siteKey 相同所以看不出问题；外部线路换了 siteKey 后，
            // 旧 Fragment 仍抓着旧 key，就会出现“线路切了但海报墙/详情进不去”。
            //
            // 因此加载新 Config 成功后，自动启动一个全新的 VodActivity，
            // 用户不需要手工返回一次，但 Fragment / siteKey / Spider 全部是新环境。
            mSwitchingSite = false;
            mSwitchingConfig = false;
            mPreviousSite = null;
            mKey = VodConfig.get().getHome().getKey();
            mResult = result;

            Intent intent = new Intent(this, VodActivity.class);
            intent.putExtra("key", mKey);
            intent.putExtra("result", mResult);
            startActivity(intent);
            finish();
        });
    }


    @Override
    public void setConfig(Config config) {
        if (config == null || config.getUrl().isEmpty()) return;

        Config current = VodConfig.get().getConfig();
        if (current != null && config.getId() == current.getId()) return;

        mPreviousConfig = current;
        mPreviousSite = null;
        mSwitchingConfig = true;
        mSwitchingSite = true;

        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                // Config / Spider / Site 全部加载完后，再拉新线路首页。
                mViewModel.homeContent();
            }

            @Override
            public void error(String msg) {
                restorePreviousConfig(msg);
            }
        });
    }

    private void restorePreviousConfig(String msg) {
        Config previous = mPreviousConfig;
        mPreviousConfig = null;
        mSwitchingConfig = false;
        mSwitchingSite = false;

        if (previous == null || previous.getUrl().isEmpty()) {
            Notify.show(msg);
            return;
        }

        Config current = VodConfig.get().getConfig();
        if (current != null && current.getId() == previous.getId()) {
            Notify.show(msg);
            return;
        }

        // 外部线路初始化失败时恢复原线路，避免 VodConfig 留在半初始化状态。
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

    @Override
    public void setSite(Site item) {
        if (item == null || item.isEmpty()) return;
        if (item.equals(VodConfig.get().getHome())) return;

        // 记录当前 Site，失败时可以恢复。
        mPreviousSite = VodConfig.get().getHome();
        mPreviousConfig = null;
        mSwitchingConfig = false;
        mSwitchingSite = true;

        // setHome 会保存当前直线路，并发 RefreshEvent.HOME。
        // 因为 mSwitchingSite=true，当前旧 Fragment 的 HOME 刷新会被 onRefreshEvent 忽略；
        // 我们主动拉新 Site 首页，成功后自动创建全新的 VodActivity。
        VodConfig.get().setHome(item);
        mViewModel.homeContent();
    }

    private void restorePreviousSite(String msg) {
        Site previous = mPreviousSite;
        mPreviousSite = null;
        mSwitchingConfig = false;
        mSwitchingSite = false;

        if (previous != null && !previous.isEmpty()) {
            VodConfig.get().setHome(previous);
        }
        Notify.show(msg);
    }

    private void setRecyclerView() {
        mBinding.recycler.requestFocus();
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(mAdapter = new TypeAdapter(this));
    }

    private void setTypes() {
        List<Class> items = new ArrayList<>();
        Class home = new Class();
        home.setTypeId("home");
        home.setTypeName(getString(R.string.vod_home));
        items.add(home);
        items.addAll(getResult().getTypes());
        mAdapter.addAll(items);
    }

    private void setTitle() {
        Site home = VodConfig.get().getHome();
        List<String> items = Arrays.asList(home.getName(), VodConfig.get().getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
    }

    private void setPager() {
        mBinding.pager.setAdapter(null);
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mBinding.pager.setCurrentItem(mBinding.recycler.getSelectedPosition());
        }
    };

    private boolean isFilterVisible() {
        return Optional.ofNullable(getType()).map(Class::getFilter).orElse(false);
    }

    private void updateFilter() {
        Optional.ofNullable(getType()).ifPresent(this::updateFilter);
    }

    private void updateFilter(Class item) {
        item.setFilter(!item.getFilter());
        getFragment().toggleFilter(item.getFilter());
        mAdapter.notifyItemRangeChanged(mAdapter.indexOf(item), 1);
    }

    public void closeFilter() {
        if (isFilterVisible()) updateFilter();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (mSwitchingSite && event.getType() == RefreshEvent.Type.HOME) return;
        if (event.getType() == RefreshEvent.Type.CATEGORY) getFragment().onRefresh();
        if (event.getType() == RefreshEvent.Type.HOME && getType().isHome()) getFragment().onRefresh();
    }

    @Override
    public void onItemClick(Class item) {
        updateFilter(item);
    }

    @Override
    public void onRefresh(Class item) {
        getFragment().onRefresh();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) updateFilter();
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onBackInvoked() {
        if (isFilterVisible()) updateFilter();
        else if (getFragment().moveToTop()) return;
        else if (getFragment().canBack()) getFragment().goBack();
        else super.onBackInvoked();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return FolderFragment.newInstance(getKey(), type);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
