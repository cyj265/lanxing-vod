package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.cyj265.lanxingvod.R;
import com.cyj265.lanxingvod.databinding.AdapterArrayBinding;
import com.fongmi.android.tv.utils.ResUtil;

public class ArrayPresenter extends Presenter {

    private final OnClickListener listener;
    private final String backward;
    private final String forward;
    private final String reverse;

    public ArrayPresenter(OnClickListener listener) {
        this.listener = listener;
        this.backward = ResUtil.getString(R.string.play_backward);
        this.forward = ResUtil.getString(R.string.play_forward);
        this.reverse = ResUtil.getString(R.string.play_reverse);
    }

    public interface OnClickListener {

        void onRevSort();

        void onRevPlay(TextView view);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterArrayBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ViewHolder holder = (ViewHolder) viewHolder;
        String text = object.toString();
        holder.binding.text.setText(text);
        if (text.equals(reverse)) setOnClickListener(holder, view -> listener.onRevSort());
        else if (text.equals(backward) || text.equals(forward)) setOnClickListener(holder, view -> listener.onRevPlay(holder.binding.text));
        else setOnClickListener(holder, null);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterArrayBinding binding;

        public ViewHolder(@NonNull AdapterArrayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}