package com.fongmi.android.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterVodListBinding;
import com.fongmi.android.tv.ui.holder.VodListHolder;

/**
 * Presenter for Spider action entries returned on the TV home page.
 *
 * Action items are still Vod objects; they use the existing Vod click contract so
 * HomeActivity can dispatch item.getAction() through SiteViewModel without any
 * shell-specific or built-in source dependency.
 */
public class ActionPresenter extends Presenter {

    private final VodPresenter.OnClickListener listener;

    public ActionPresenter(VodPresenter.OnClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new VodListHolder(AdapterVodListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ((VodListHolder) viewHolder).initView((Vod) object);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ((VodListHolder) viewHolder).unbind();
    }
}
