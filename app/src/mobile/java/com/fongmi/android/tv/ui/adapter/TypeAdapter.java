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
    /** 是否展示虚拟的「首页」tab（豆瓣精选页）。关掉后 tab 列表从「推荐」开始 */
    private boolean showDiscover = true;

    public TypeAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public boolean isShowDiscover() {
        return showDiscover;
    }

    public void setShowDiscover(boolean show) {
        if (showDiscover == show) return;
        showDiscover = show;
        notifyDataSetChanged();
    }

    private int offset() {
        return showDiscover ? 0 : 1;
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
        for (Class item : mItems) item.setSelected(false);
        int first = offset();
        if (first < mItems.size()) mItems.get(first).setSelected(true);
        notifyDataSetChanged();
    }

    /** position 是「可见列表」的下标（与 ViewPager 的 page 一致），内部自动跳过被隐藏的虚拟首页 */
    public void setSelected(int position) {
        for (Class item : mItems) item.setSelected(false);
        int index = position + offset();
        if (index >= 0 && index < mItems.size()) mItems.get(index).setSelected(true);
        notifyItemRangeChanged(0, getItemCount());
    }

    public Class get(int position) {
        return mItems.get(position + offset());
    }

    @Override
    public int getItemCount() {
        return Math.max(0, mItems.size() - offset());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Class item = get(position);
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
