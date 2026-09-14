package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.cyj265.lanxingvod.R;
import com.fongmi.android.tv.bean.Flag;
import com.cyj265.lanxingvod.databinding.AdapterFlagBinding;

public class FlagPresenter extends Presenter {

    private final OnClickListener listener;
    private int nextFocusDown;

    public FlagPresenter(OnClickListener listener) {
        this.listener = listener;
        this.nextFocusDown = R.id.episode;
    }

    public interface OnClickListener {
        void onItemClick(Flag item);
    }

    public void setNextFocusDown(int nextFocusDown) {
        this.nextFocusDown = nextFocusDown;
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterFlagBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Flag item = (Flag) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.text.setText(item.getShow());
        holder.binding.text.setActivated(item.isActivated());
        holder.binding.text.setNextFocusDownId(nextFocusDown);
        setOnClickListener(holder, view -> listener.onItemClick(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterFlagBinding binding;

        public ViewHolder(@NonNull AdapterFlagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}