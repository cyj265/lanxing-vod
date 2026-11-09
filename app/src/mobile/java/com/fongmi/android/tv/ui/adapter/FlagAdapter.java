package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.AdapterFlagBinding;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.VodTheme;

import java.util.ArrayList;
import java.util.List;

public class FlagAdapter extends RecyclerView.Adapter<FlagAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Flag> mItems;
    private VodTheme theme;

    public FlagAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Flag item);
    }

    public void addAll(List<Flag> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public Flag get(int position) {
        return mItems.get(position);
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterFlagBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    public void setTheme(VodTheme theme) {
        this.theme = theme;
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Flag item = mItems.get(position);
        holder.binding.text.setText(item.getShow());
        holder.binding.text.setSelected(item.isSelected());
        int count = item.getEpisodes() != null ? item.getEpisodes().size() : 0;
        holder.binding.count.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        holder.binding.count.setText(ResUtil.getString(R.string.detail_flag_count, count));
        if (theme != null) {
            holder.binding.getRoot().setBackground(theme.card(item.isSelected(), ResUtil.dp2px(16)));
            holder.binding.text.setTextColor(item.isSelected() ? theme.accent : theme.onSurface);
            holder.binding.count.setTextColor(theme.onVariant);
        }
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterFlagBinding binding;

        ViewHolder(@NonNull AdapterFlagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
