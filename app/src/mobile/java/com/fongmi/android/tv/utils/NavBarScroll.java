package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.ui.activity.HomeActivity;

/**
 * 把页面里的竖向滚动容器统一接到首页底部导航：
 * 手指上滑（看下面）收起、下滑（回上面）展开、滚到顶部一定展开。
 * <p>
 * 同时支持 NestedScrollView 与 RecyclerView，并递归遍历子 View，
 * 这样首页（ViewPager2 里各页）、历史、设置都能生效，新增页面自动继承。
 * 横向容器（榜单横滑、TabLayout）的 dy 恒为 0，绑定无副作用。
 */
public final class NavBarScroll {

    private static final int THRESHOLD = 8;

    private NavBarScroll() {
    }

    public static void bind(View root, Activity activity) {
        if (root == null || !(activity instanceof HomeActivity)) return;
        collect(root, (HomeActivity) activity);
    }

    private static void collect(View view, HomeActivity host) {
        if (view instanceof NestedScrollView) {
            bindNested((NestedScrollView) view, host);
        } else if (view instanceof RecyclerView) {
            bindRecycler((RecyclerView) view, host);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), host);
    }

    private static void bindNested(NestedScrollView scroll, HomeActivity host) {
        scroll.setOnScrollChangeListener((NestedScrollView v, int x, int y, int oldX, int oldY) -> {
            if (y <= 0) {
                host.setNavBarVisible(true);
                return;
            }
            int dy = y - oldY;
            if (dy > THRESHOLD) host.setNavBarVisible(false);
            else if (dy < -THRESHOLD) host.setNavBarVisible(true);
        });
    }

    private static void bindRecycler(RecyclerView recycler, HomeActivity host) {
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!rv.canScrollVertically(-1)) {
                    host.setNavBarVisible(true);
                } else if (dy > THRESHOLD) {
                    host.setNavBarVisible(false);
                } else if (dy < -THRESHOLD) {
                    host.setNavBarVisible(true);
                }
            }
        });
    }
}
