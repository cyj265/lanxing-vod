package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cyj265.lanxingvod.databinding.ItemRecommendVodBinding;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class RecommendVodAdapter extends RecyclerView.Adapter<RecommendVodAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Vod> mItems;

    public interface OnClickListener {
        void onItemClick(Vod item);
    }

    public RecommendVodAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public void addAll(List<Vod> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemRecommendVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vod item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.remark.setText(item.getRemarks());
        ImgUtil.load(item.getName(), item.getPic(), holder.binding.pic);
        holder.itemView.setOnClickListener(v -> mListener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemRecommendVodBinding binding;

        ViewHolder(ItemRecommendVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
