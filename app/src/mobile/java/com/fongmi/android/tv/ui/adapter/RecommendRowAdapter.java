package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cyj265.lanxingvod.databinding.ItemRecommendRowBinding;
import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.List;

public class RecommendRowAdapter extends RecyclerView.Adapter<RecommendRowAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Row> mItems;

    public interface OnClickListener {
        void onItemClick(Vod item);
        void onMoreClick(int categoryIndex);
    }

    public static class Row {
        public String title;
        public List<Vod> list;
        public int categoryIndex;
        public Row(String title, List<Vod> list, int categoryIndex) {
            this.title = title;
            this.list = list;
            this.categoryIndex = categoryIndex;
        }
    }

    public RecommendRowAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public void addRow(String title, List<Vod> list, int categoryIndex) {
        if (list == null || list.isEmpty()) return;
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).title.equals(title)) {
                mItems.get(i).list = list;
                notifyItemChanged(i);
                return;
            }
        }
        mItems.add(new Row(title, list, categoryIndex));
        notifyItemInserted(mItems.size() - 1);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemRecommendRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = mItems.get(position);
        holder.binding.title.setText(row.title);
        holder.binding.more.setOnClickListener(v -> mListener.onMoreClick(row.categoryIndex));
        RecommendVodAdapter adapter = new RecommendVodAdapter(mListener::onItemClick);
        adapter.addAll(row.list);
        holder.binding.recycler.setAdapter(adapter);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemRecommendRowBinding binding;

        ViewHolder(ItemRecommendRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.recycler.setLayoutManager(new LinearLayoutManager(binding.getRoot().getContext(), LinearLayoutManager.HORIZONTAL, false));
            binding.recycler.setHasFixedSize(true);
        }
    }
}
