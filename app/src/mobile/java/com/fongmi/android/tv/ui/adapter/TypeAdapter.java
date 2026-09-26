package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.AdapterTypeBinding;

import java.util.ArrayList;
import java.util.List;

public class TypeAdapter extends RecyclerView.Adapter<TypeAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Class> mItems;

    public TypeAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(int position, Class item);
    }

    private Class discover() {
        Class type = new Class();
        type.setTypeName("首页");
        type.setTypeId("discover");
        return type;
    }

    private Class home() {
        Class type = new Class();
        type.setTypeName("推荐");
        type.setTypeId("home");
        return type;
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(Result result) {
        mItems.add(discover());
        if (!result.getList().isEmpty()) mItems.add(home());
        mItems.addAll(result.getTypes());
        if (!mItems.isEmpty()) mItems.get(0).setSelected(true);
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        for (Class item : mItems) item.setSelected(false);
        if (position >= 0 && position < mItems.size()) mItems.get(position).setSelected(true);
        notifyItemRangeChanged(0, mItems.size());
    }

    public Class get(int position) {
        return mItems.get(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Class item = mItems.get(position);
        holder.binding.text.setText(item.getTypeName());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(position, item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTypeBinding binding;

        ViewHolder(@NonNull AdapterTypeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
