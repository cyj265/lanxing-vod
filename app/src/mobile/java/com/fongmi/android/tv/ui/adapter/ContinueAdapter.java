package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterContinueBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.List;

public class ContinueAdapter extends RecyclerView.Adapter<ContinueAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<History> mItems;

    public ContinueAdapter(List<History> items, OnClickListener listener) {
        this.mItems = items;
        this.listener = listener;
    }

    public interface OnClickListener {
        void onItemClick(History item);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterContinueBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        History item = mItems.get(position);
        holder.binding.name.setText(item.getVodName());
        holder.binding.remark.setText(item.getVodRemarks());
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterContinueBinding binding;

        public ViewHolder(@NonNull AdapterContinueBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
