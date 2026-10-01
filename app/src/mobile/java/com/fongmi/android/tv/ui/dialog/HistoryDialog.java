package com.fongmi.android.tv.ui.dialog;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.databinding.DialogHistoryBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.adapter.ConfigAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.tabs.TabLayout;

public class HistoryDialog extends BaseBottomSheetDialog implements ConfigAdapter.OnClickListener, ConfigListener {

    private DialogHistoryBinding binding;
    private ConfigListener listener;
    private ConfigAdapter adapter;

    private int type;
    private boolean readOnly;

    public static HistoryDialog create() {
        return new HistoryDialog();
    }

    public HistoryDialog vod() {
        type = 0;
        return this;
    }

    public HistoryDialog live() {
        type = 1;
        return this;
    }

    public HistoryDialog wall() {
        type = 2;
        return this;
    }

    public HistoryDialog readOnly() {
        readOnly = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    private boolean isFull() {
        return getParentFragment() == null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        listener = isFull() ? (ConfigListener) context : (ConfigListener) getParentFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogHistoryBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (type > 1) type = 0;
        adapter = new ConfigAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter.readOnly(readOnly).addAll(type));
        setupTabs();
        setupSwipe();
        binding.refresh.setOnClickListener(v -> refresh());
        binding.add.setOnClickListener(v -> add());
    }

    private void setupTabs() {
        binding.tab.addTab(binding.tab.newTab().setText(R.string.tab_vod));
        binding.tab.addTab(binding.tab.newTab().setText(R.string.tab_live));
        binding.tab.selectTab(binding.tab.getTabAt(type));
        binding.tab.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                type = tab.getPosition();
                reloadList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void setupSwipe() {
        if (readOnly) return;
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getBindingAdapterPosition();
                if (pos < 0) return;
                Config item = adapter.get(pos);
                if (adapter.remove(item) == 0) dismiss();
            }
        }).attachToRecyclerView(binding.recycler);
    }

    private void reloadList() {
        adapter.setType(type);
    }

    private void refresh() {
        Config config = type == 0 ? VodConfig.get().getConfig() : LiveConfig.get().getConfig();
        Callback callback = new Callback() {
            @Override
            public void success() {
                Notify.show(getString(R.string.refresh_success));
                reloadList();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
        if (type == 0) VodConfig.load(config, callback);
        else LiveConfig.load(config, callback);
    }

    private void add() {
        ConfigDialog dialog = ConfigDialog.create();
        if (type == 1) dialog.live();
        else dialog.vod();
        dialog.show(this);
    }

    @Override
    public void onTextClick(Config item) {
        if (listener != null) listener.setConfig(item);
        dismiss();
    }

    @Override
    public void onDeleteClick(Config item) {
        if (adapter.remove(item) == 0) dismiss();
    }

    @Override
    public void onCopyClick(Config item) {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", item.getUrl()));
        Notify.show(getString(R.string.copy_success));
    }

    @Override
    public void setConfig(Config config) {
        Callback callback = new Callback() {
            @Override
            public void success() {
                reloadList();
            }
        };
        if (type == 0) VodConfig.load(config, callback);
        else LiveConfig.load(config, callback);
        if (listener != null) listener.setConfig(config);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
    }
}
