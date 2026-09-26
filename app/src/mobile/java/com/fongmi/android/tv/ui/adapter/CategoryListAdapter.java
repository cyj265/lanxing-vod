package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.ItemCategoryListBinding;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class CategoryListAdapter extends RecyclerView.Adapter<CategoryListAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Vod> mItems;

    public interface OnClickListener {
        void onItemClick(Vod item);
    }

    public CategoryListAdapter(OnClickListener listener) {
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
        return new ViewHolder(ItemCategoryListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vod item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        ImgUtil.load(item.getName(), item.getPic(), holder.binding.pic);

        String remark = item.getRemarks();
        if (remark != null && !remark.isEmpty()) {
            holder.binding.remark.setText(remark);
            holder.binding.remark.setVisibility(View.VISIBLE);
        } else {
            holder.binding.remark.setVisibility(View.GONE);
        }

        String typeName = item.getTypeName();
        if (typeName != null && !typeName.isEmpty()) {
            holder.binding.type.setText("类型：" + typeName.replace(",", "、").replace("/", "、"));
            holder.binding.type.setVisibility(View.VISIBLE);
        } else {
            holder.binding.type.setVisibility(View.GONE);
        }

        String content = item.getContent();
        if (content != null && !content.isEmpty()) {
            holder.binding.desc.setText(content);
        } else {
            holder.binding.desc.setText("暂无简介");
        }

        holder.itemView.setOnClickListener(v -> mListener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryListBinding binding;

        ViewHolder(ItemCategoryListBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
