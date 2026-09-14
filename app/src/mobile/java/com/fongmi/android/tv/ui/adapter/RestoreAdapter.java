package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cyj265.lanxingvod.databinding.AdapterRestoreBinding;
import com.github.catvod.utils.Path;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RestoreAdapter extends RecyclerView.Adapter<RestoreAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final SimpleDateFormat format;
    private final List<File> mItems;

    public RestoreAdapter(OnClickListener listener) {
        this.format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        this.mItems = new ArrayList<>();
        this.listener = listener;
        this.addAll();
    }

    public interface OnClickListener {

        void onItemClick(File item);

        void onDeleteClick(File item);
    }

    private void addAll() {
        File[] files = Path.tv().listFiles();
        if (files == null) files = new File[0];
        for (File file : files) if (file.getName().startsWith("tv") && file.getName().endsWith(".bk.gz")) mItems.add(file);
        if (!mItems.isEmpty()) mItems.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        notifyDataSetChanged();
    }

    public int remove(File item) {
        int position = mItems.indexOf(item);
        if (position == -1) return -1;
        Path.clear(item);
        mItems.remove(position);
        notifyItemRemoved(position);
        return getItemCount();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterRestoreBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.time.setText(format.format(item.lastModified()));
        holder.binding.delete.setOnClickListener(v -> listener.onDeleteClick(item));
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterRestoreBinding binding;

        ViewHolder(@NonNull AdapterRestoreBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
