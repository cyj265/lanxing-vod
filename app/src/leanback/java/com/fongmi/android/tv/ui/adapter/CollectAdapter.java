package com.fongmi.android.tv.ui.adapter;

import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.databinding.AdapterTypeBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CollectAdapter extends RecyclerView.Adapter<CollectAdapter.ViewHolder> {

    public interface OnClickListener {
        void onItemClick(int position, Collect item);
        void onItemDoubleClick(int position, Collect item);
    }

    private static final long DOUBLE_CLICK_MS = 520L;

    private final List<Collect> mItems = new ArrayList<>();
    private final Set<String> mBlocked = new HashSet<>();
    private final OnClickListener mListener;

    private long mLastClickTime;
    private String mLastClickKey = "";

    public CollectAdapter(OnClickListener listener) {
        mListener = listener;
    }

    public void add(Collect item) {
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
    }

    public void addAll(List<Collect> items) {
        mItems.addAll(items);
        notifyItemRangeInserted(mItems.size() - items.size(), items.size());
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public Collect get(int position) {
        return mItems.get(position);
    }

    public void setBlocked(Set<String> blocked) {
        mBlocked.clear();
        if (blocked != null) mBlocked.addAll(blocked);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    private boolean isBlocked(Collect item) {
        String key = item.getSite().getKey();
        return !"all".equals(key) && mBlocked.contains(key);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(
                AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false)
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collect item = mItems.get(position);
        boolean blocked = isBlocked(item);

        holder.binding.text.setText(
                blocked ? "⛔ " + item.getSite().getName() : item.getSite().getName()
        );
        holder.binding.getRoot().setAlpha(blocked ? 0.55f : 1.0f);

        holder.binding.getRoot().setOnClickListener(v -> {
            int p = holder.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION) return;

            Collect clicked = mItems.get(p);
            String key = clicked.getSite().getKey();
            long now = SystemClock.elapsedRealtime();

            boolean doubleClick = !"all".equals(key)
                    && key.equals(mLastClickKey)
                    && now - mLastClickTime <= DOUBLE_CLICK_MS;

            if (doubleClick) {
                mLastClickTime = 0L;
                mLastClickKey = "";
                mListener.onItemDoubleClick(p, clicked);
            } else {
                mLastClickTime = now;
                mLastClickKey = key;
                mListener.onItemClick(p, clicked);
            }
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTypeBinding binding;

        ViewHolder(@NonNull AdapterTypeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
