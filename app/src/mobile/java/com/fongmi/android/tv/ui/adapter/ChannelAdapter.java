package com.fongmi.android.tv.ui.adapter;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.databinding.AdapterChannelBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Channel> mItems;

    public ChannelAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Channel item);

        boolean onLongClick(Channel item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int position = mItems.indexOf(item);
        if (position == -1) return;
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    public void setSelected(int position) {
        if (position == -1) return;
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == position);
        notifyItemRangeChanged(0, getItemCount());
    }

    public int setSelected(Channel channel) {
        int position = mItems.indexOf(channel);
        setSelected(position);
        return position;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ChannelAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelAdapter.ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        loadLogo(item, holder.binding.logo);
        holder.binding.name.setText(item.getShow());
        holder.binding.number.setText(item.getNumber());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(item));
    }

    /**
     * 频道行台标：加载失败就收起，不再回退成「频道名首字」的色块——
     * 央视系列首字都是 C，一排一模一样的方块反而盖住了台名。
     */
    private void loadLogo(Channel item, ImageView view) {
        view.setVisibility(View.GONE);
        view.setImageDrawable(null);
        if (item.getLogo().isEmpty()) return;
        Glide.with(view).load(ImgUtil.getUrl(item.getLogo())).fitCenter().listener(new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                view.setVisibility(View.GONE);
                return true;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                view.setVisibility(View.VISIBLE);
                return false;
            }
        }).into(view);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.logo).clear(holder.binding.logo);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelBinding binding;

        ViewHolder(@NonNull AdapterChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}