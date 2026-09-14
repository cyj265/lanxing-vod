package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.Parse;
import com.cyj265.lanxingvod.databinding.AdapterParseBinding;

public class ParsePresenter extends Presenter {

    private final OnClickListener listener;

    public ParsePresenter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onItemClick(Parse parse);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterParseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Parse item = (Parse) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.text.setText(item.getName());
        holder.binding.text.setActivated(item.isActivated());
        setOnClickListener(holder, view -> listener.onItemClick(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterParseBinding binding;

        public ViewHolder(@NonNull AdapterParseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}