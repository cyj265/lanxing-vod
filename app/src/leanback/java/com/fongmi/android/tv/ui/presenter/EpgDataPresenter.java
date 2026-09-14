package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.EpgData;
import com.cyj265.lanxingvod.databinding.AdapterEpgDataBinding;

public class EpgDataPresenter extends Presenter {

    private final OnClickListener listener;

    public EpgDataPresenter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void hideEpg();

        void onItemClick(EpgData item);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterEpgDataBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        EpgData item = (EpgData) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.time.setText(item.getTime());
        holder.binding.title.setText(item.getTitle());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setLeftListener(listener::hideEpg);
        setOnClickListener(holder, view -> {
            if (!item.isFuture()) listener.onItemClick(item);
        });
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterEpgDataBinding binding;

        public ViewHolder(@NonNull AdapterEpgDataBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}