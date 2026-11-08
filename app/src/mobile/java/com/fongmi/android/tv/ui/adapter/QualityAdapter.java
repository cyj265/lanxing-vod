package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.AdapterQualityBinding;
import com.fongmi.android.tv.utils.ResUtil;

public class QualityAdapter extends RecyclerView.Adapter<QualityAdapter.ViewHolder> {

    private final OnClickListener listener;
    private Result result;
    private int position;

    public QualityAdapter(OnClickListener listener) {
        this.listener = listener;
        this.result = Result.empty();
    }

    public interface OnClickListener {

        void onItemClick(Result result);
    }

    public void addAll(Result result) {
        this.result = result;
        notifyDataSetChanged();
    }

    public int getPosition() {
        return position;
    }

    @Override
    public int getItemCount() {
        return result.getUrl().getValues().isEmpty() ? 0 : result.getUrl().getValues().size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterQualityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        boolean multiple = result.getUrl().isMulti();
        String name = result.getUrl().n(position);
        if (TextUtils.isEmpty(name)) name = multiple ? ResUtil.getString(R.string.quality_option, position + 1) : ResUtil.getString(R.string.quality_original);
        holder.binding.text.setText(name);
        holder.binding.text.setSelected(!multiple || result.getUrl().getPosition() == position);
        holder.binding.text.setClickable(multiple);
        holder.binding.text.setOnClickListener(multiple ? v -> onItemClick(position) : null);
    }

    private void onItemClick(int position) {
        this.position = position;
        result.getUrl().set(position);
        listener.onItemClick(result);
        notifyItemRangeChanged(0, getItemCount());
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterQualityBinding binding;

        ViewHolder(@NonNull AdapterQualityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}