package com.fongmi.android.tv.ui.fragment;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.FragmentHistoryBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.SyncDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.VodTheme;
import com.fongmi.android.tv.ui.dialog.GlassAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HistoryFragment extends BaseFragment implements HistoryAdapter.OnClickListener, KeepAdapter.OnClickListener, MenuProvider {

    private FragmentHistoryBinding mBinding;
    private HistoryAdapter mAdapter;
    private KeepAdapter mKeepAdapter;
    private VodTheme mTheme;
    private boolean mKeep;

    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle(getString(mKeep ? R.string.app_keep : R.string.app_history));
        if (mTheme != null) tintMenu(mBinding.toolbar.getMenu());
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mTheme = VodTheme.glass();
        setRecyclerView();
        applyTheme();
        setTab(false);
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new HistoryAdapter(this);
        mKeepAdapter = new KeepAdapter(this);
        mBinding.tabHistory.setOnClickListener(v -> setTab(false));
        mBinding.tabKeep.setOnClickListener(v -> setTab(true));
    }

    private void applyTheme() {
        mBinding.historyRoot.setBackgroundColor(Color.TRANSPARENT);
        mBinding.toolbar.setTitleTextColor(mTheme.onSurface);
        mBinding.toolbar.setNavigationIconTint(mTheme.onSurface);
        mBinding.progressLayout.setEmptyTextColor(mTheme.onVariant);
        tintMenu(mBinding.toolbar.getMenu());
        mAdapter.setTheme(mTheme);
        mKeepAdapter.setTheme(mTheme);
    }

    private void tintMenu(Menu menu) {
        if (mTheme == null) return;
        for (int i = 0; i < menu.size(); i++) {
            Drawable icon = menu.getItem(i).getIcon();
            if (icon != null) icon.setTint(mTheme.onSurface);
        }
    }

    private void setTab(boolean keep) {
        mKeep = keep;
        mAdapter.setDelete(false);
        mKeepAdapter.setDelete(false);
        mBinding.recycler.setAdapter(keep ? mKeepAdapter : mAdapter);
        styleTab(mBinding.tabHistory, !keep);
        styleTab(mBinding.tabKeep, keep);
        // 直接设 toolbar 标题：activity.setTitle 在这条 tab 链路里会被首页框架覆盖
        mBinding.toolbar.setTitle(keep ? R.string.app_keep : R.string.app_history);
        if (keep) getKeep();
        else getHistory();
    }

    private void styleTab(MaterialTextView tab, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ResUtil.dp2px(20));
        bg.setColor(selected ? mTheme.accent : Color.TRANSPARENT);
        tab.setBackground(bg);
        tab.setTextColor(selected ? mTheme.onAccent : mTheme.onSurface);
    }

    private void getHistory() {
        mAdapter.setItems(History.get(), (hasChange) -> {
            if (mKeep) return;
            mBinding.progressLayout.showContent(true, mAdapter.getItemCount());
            if (hasChange) mBinding.recycler.scrollToPosition(0);
        });
    }

    private void getKeep() {
        mKeepAdapter.setItems(Keep.getVod(), () -> {
            if (!mKeep) return;
            mBinding.progressLayout.showContent(true, mKeepAdapter.getItemCount());
        });
    }

    private void onSync() {
        if (mKeep) SyncDialog.create().keep().show(requireActivity());
        else SyncDialog.create().history().show(requireActivity());
    }

    private void onDelete() {
        if (mKeep) {
            if (mKeepAdapter.isDelete()) {
                new GlassAlertDialogBuilder(requireContext()).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_keep).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> mKeepAdapter.clear()).show();
            } else if (mKeepAdapter.getItemCount() > 0) {
                mKeepAdapter.setDelete(true);
            }
        } else {
            if (mAdapter.isDelete()) {
                new GlassAlertDialogBuilder(requireContext()).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_history).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> mAdapter.clear()).show();
            } else if (mAdapter.getItemCount() > 0) {
                mAdapter.setDelete(true);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType().equals(RefreshEvent.Type.HISTORY)) getHistory();
        else if (event.getType().equals(RefreshEvent.Type.KEEP)) getKeep();
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mAdapter.remove(item.delete(), () -> {
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        });
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) SearchActivity.start(requireActivity(), item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        mKeepAdapter.remove(item.delete(), () -> {
            if (mKeepAdapter.getItemCount() == 0) mKeepAdapter.setDelete(false);
        });
    }

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Override
    public boolean onLongClick() {
        if (mKeep) mKeepAdapter.setDelete(!mKeepAdapter.isDelete());
        else mAdapter.setDelete(!mAdapter.isDelete());
        return true;
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_history, menu);
        tintMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) requireActivity().getOnBackPressedDispatcher().onBackPressed();
        else if (item.getItemId() == R.id.delete) onDelete();
        else if (item.getItemId() == R.id.sync) onSync();
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        requireActivity().removeMenuProvider(this);
        super.onDestroyView();
    }
}