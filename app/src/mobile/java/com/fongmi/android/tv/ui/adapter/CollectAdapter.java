package com.fongmi.android.tv.ui.adapter;

import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.databinding.AdapterCollectBinding;

import java.util.HashSet;
import java.util.Set;

public class CollectAdapter extends BaseDiffAdapter<Collect, CollectAdapter.ViewHolder> {

    public interface OnClickListener {
        void onItemClick(int position, Collect item);
        void onItemDoubleClick(int position, Collect item);
    }

    private static final long DOUBLE_CLICK_MS = 520L;

    private final OnClickListener listener;
    private final Set<String> blocked = new HashSet<>();
    private long lastClickTime;
    private String lastClickKey = "";

    public CollectAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public int getPosition() {
        for (int i = 0; i < getItemCount(); i++) if (getItem(i).isSelected()) return i;
        return 0;
    }

    public Collect getActivated() {
        return getItems().get(getPosition());
    }

    public void setSelected(int position) {
        for (int i = 0; i < getItemCount(); i++) getItem(i).setSelected(i == position);
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setBlocked(Set<String> values) {
        blocked.clear();
        if (values != null) blocked.addAll(values);
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(
                AdapterCollectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false)
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collect item = getItem(position);
        String key = item.getSite().getKey();
        boolean isAll = "all".equals(key);
        boolean isBlocked = !isAll && blocked.contains(key);

        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setText(
                isBlocked ? "⛔ " + item.getSite().getName() : item.getSite().getName()
        );
        holder.binding.text.setAlpha(isBlocked ? 0.55f : 1.0f);

        holder.binding.text.setOnClickListener(v -> {
            int p = holder.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION) return;

            Collect clicked = getItem(p);
            String clickedKey = clicked.getSite().getKey();
            long now = SystemClock.elapsedRealtime();

            boolean doubleClick = !"all".equals(clickedKey)
                    && clickedKey.equals(lastClickKey)
                    && now - lastClickTime <= DOUBLE_CLICK_MS;

            if (doubleClick) {
                lastClickTime = 0L;
                lastClickKey = "";
                listener.onItemDoubleClick(p, clicked);
            } else {
                lastClickTime = now;
                lastClickKey = clickedKey;
                listener.onItemClick(p, clicked);
            }
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterCollectBinding binding;

        ViewHolder(@NonNull AdapterCollectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
