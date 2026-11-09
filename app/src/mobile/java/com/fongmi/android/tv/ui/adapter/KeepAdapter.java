package com.fongmi.android.tv.ui.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.AdapterHistoryCardBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.VodTheme;

public class KeepAdapter extends BaseDiffAdapter<Keep, KeepAdapter.ViewHolder> {

    private final OnClickListener listener;
    private boolean delete;
    private VodTheme theme;

    public KeepAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Keep item);

        void onItemDelete(Keep item);

        boolean onLongClick();
    }

    public void setTheme(VodTheme theme) {
        this.theme = theme;
        notifyDataSetChanged();
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void clear() {
        super.clear();
        setDelete(false);
        Keep.deleteAll();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterHistoryCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Keep item = getItem(position);
        holder.binding.name.setText(item.getVodName());
        holder.binding.remark.setVisibility(View.GONE);
        holder.binding.progress.setVisibility(View.GONE);
        CharSequence ago = DateUtils.getRelativeTimeSpanString(item.getCreateTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        holder.binding.meta.setText(ago + " · " + item.getSiteName());
        holder.binding.play.setVisibility(!delete ? View.VISIBLE : View.GONE);
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        if (theme != null) {
            holder.binding.getRoot().setBackground(theme.card(false, ResUtil.dp2px(16)));
            holder.binding.name.setTextColor(theme.onSurface);
            holder.binding.meta.setTextColor(ColorUtils.setAlphaComponent(theme.onSurface, 0x80));
            holder.binding.play.setBackground(theme.circle(theme.accent));
            holder.binding.play.setImageTintList(android.content.res.ColorStateList.valueOf(theme.onAccent));
        }
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
        holder.binding.play.setOnClickListener(v -> {
            if (!isDelete()) listener.onItemClick(item);
        });
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick());
        holder.binding.getRoot().setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHistoryCardBinding binding;

        ViewHolder(@NonNull AdapterHistoryCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}