package com.fongmi.android.tv.utils;

import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.appcompat.widget.LinearLayoutCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;

/**
 * 播放页「网速 / 电量」屏显的位置。
 * <p>
 * 0 = 右上（默认，留在顶栏内）；1 = 左下（移到控制栏根布局，贴底栏上方）；2 = 隐藏。
 * 分辨率 resolution 已固定在顶栏右上角，不在 osd 容器内，不受此开关影响。
 * 搬动的是同一个 osd 容器，ViewBinding 中的 speed / battery 引用始终有效。
 * 该方法幂等，可在每秒刷新的 setOsd() 里直接调用。
 */
public final class OsdMode {

    private OsdMode() {
    }

    public static void apply(View osd, ViewGroup topHost, View root) {
        if (osd == null || topHost == null || root == null) return;
        int mode = Setting.getOsdMode();
        if (mode == 2) {
            osd.setVisibility(View.GONE);
            return;
        }
        osd.setVisibility(View.VISIBLE);
        ViewGroup parent = (ViewGroup) osd.getParent();
        if (mode == 0) {
            if (parent == topHost) return;
            if (parent != null) parent.removeView(osd);
            // osdHost 是横向 LinearLayoutCompat，靠父 gravity=end|center_vertical 贴右上角；
            // 追加到末尾可保持「标题 | 分辨率 | 网速电量」的原有顺序。
            LinearLayoutCompat.LayoutParams lp = new LinearLayoutCompat.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            topHost.addView(osd, lp);
        } else {
            if (!(root instanceof RelativeLayout)) return;
            RelativeLayout layout = (RelativeLayout) root;
            if (parent == layout) return;
            if (parent != null) parent.removeView(osd);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
            lp.addRule(RelativeLayout.ABOVE, R.id.bottom);
            int margin = ResUtil.dp2px(16);
            lp.setMargins(margin, 0, 0, margin);
            layout.addView(osd, lp);
        }
    }
}
