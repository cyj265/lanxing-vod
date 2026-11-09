package com.fongmi.android.tv.ui.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterHistoryCardBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.VodTheme;

public class HistoryAdapter extends BaseDiffAdapter<History, HistoryAdapter.ViewHolder> {

    private final OnClickListener listener;
    private boolean animate;
    private boolean delete;
    private VodTheme theme;

    public HistoryAdapter(OnClickListener listener) {
        this.listener = listener;
        this.animate = true;
    }

    public interface OnClickListener {

        void onItemClick(History item);

        void onItemDelete(History item);

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
        this.animate = false;
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void clear() {
        super.clear();
        setDelete(false);
        History.clear(VodConfig.getCid());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterHistoryCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        History item = getItem(position);
        holder.binding.name.setText(item.getVodName());
        boolean same = item.getVodName().equals(item.getVodRemarks());
        int percent = item.getDuration() > 0 ? (int) (item.getPosition() * 100 / item.getDuration()) : 0;
        if (!delete && !same) {
            String remark = item.getVodRemarks();
            if (percent > 0) remark = remark.concat(" · ").concat(ResUtil.getString(R.string.history_watched, percent));
            holder.binding.remark.setText(remark);
            holder.binding.remark.setVisibility(View.VISIBLE);
        } else {
            holder.binding.remark.setVisibility(View.GONE);
        }
        holder.binding.progress.setMax((int) item.getDuration());
        holder.binding.progress.setProgressCompat((int) item.getPosition(), animate);
        holder.binding.progress.setVisibility(item.getDuration() > 0 ? View.VISIBLE : View.GONE);
        CharSequence ago = DateUtils.getRelativeTimeSpanString(item.getCreateTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        holder.binding.meta.setText(item.getSiteVisible() == View.VISIBLE ? ago + " · " + item.getSiteName() : ago);
        holder.binding.play.setVisibility(!delete ? View.VISIBLE : View.GONE);
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        if (theme != null) {
            holder.binding.getRoot().setBackground(theme.card(false, ResUtil.dp2px(16)));
            holder.binding.name.setTextColor(theme.onSurface);
            holder.binding.remark.setTextColor(theme.onVariant);
            holder.binding.meta.setTextColor(ColorUtils.setAlphaComponent(theme.onSurface, 0x80));
            holder.binding.play.setBackground(theme.circle(theme.accent));
            holder.binding.play.setImageTintList(android.content.res.ColorStateList.valueOf(theme.onAccent));
            holder.binding.progress.setIndicatorColor(theme.accent);
            holder.binding.progress.setTrackColor(ColorUtils.setAlphaComponent(theme.onSurface, 0x22));
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