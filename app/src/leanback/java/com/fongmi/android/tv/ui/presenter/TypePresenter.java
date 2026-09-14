package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.cyj265.lanxingvod.R;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.cyj265.lanxingvod.databinding.AdapterTypeBinding;
import com.fongmi.android.tv.utils.ResUtil;

public class TypePresenter extends Presenter {

    private final OnClickListener listener;

    public TypePresenter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Class item);

        void onRefresh(Class item);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Class item = (Class) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.text.setText(item.getTypeName());
        holder.binding.text.setCompoundDrawablePadding(ResUtil.dp2px(4));
        holder.binding.text.setCompoundDrawablesWithIntrinsicBounds(0, 0, getIcon(item), 0);
        holder.binding.text.setListener(() -> listener.onRefresh(item));
        setOnClickListener(holder, view -> listener.onItemClick(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    private int getIcon(Class item) {
        return Cache.get(item).isEmpty() ? 0 : item.getFilter() ? R.drawable.ic_vod_filter_off : R.drawable.ic_vod_filter_on;
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterTypeBinding binding;

        public ViewHolder(@NonNull AdapterTypeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}