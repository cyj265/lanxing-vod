package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cyj265.lanxingvod.databinding.ItemCategoryListBinding;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.ImgUtil;
import com.google.android.material.chip.Chip;

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
        holder.binding.remark.setText(item.getRemarks());
        holder.binding.desc.setText(item.getContent());
        ImgUtil.load(item.getName(), item.getPic(), holder.binding.pic);

        holder.binding.tags.removeAllViews();
        String typeName = item.getTypeName();
        if (typeName != null && !typeName.isEmpty()) {
            String[] types = typeName.split("[,，/\\s]+");
            for (String t : types) {
                if (!t.isEmpty()) {
                    Chip chip = new Chip(holder.binding.tags.getContext());
                    chip.setText(t);
                    chip.setTextSize(10);
                    chip.setCheckable(false);
                    chip.setChipMinHeight(24);
                    holder.binding.tags.addView(chip);
                }
            }
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
