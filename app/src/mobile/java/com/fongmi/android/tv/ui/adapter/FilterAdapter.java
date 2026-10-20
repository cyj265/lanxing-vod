package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.databinding.AdapterFilterBinding;
import com.fongmi.android.tv.impl.FilterListener;

import java.util.List;

public class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.ViewHolder> {

    private final FilterListener listener;
    private final List<Filter> mItems;

    public FilterAdapter(FilterListener listener, List<Filter> items) {
        this.listener = listener;
        this.mItems = items;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterFilterBinding binding = AdapterFilterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.recycler.setLayoutManager(new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Filter item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.name.setVisibility(item.getName().isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.recycler.setItemAnimator(null);
        holder.binding.recycler.setAdapter(new ValueAdapter(listener, item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterFilterBinding binding;

        ViewHolder(@NonNull AdapterFilterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}