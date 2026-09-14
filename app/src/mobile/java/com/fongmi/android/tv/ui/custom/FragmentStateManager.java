package com.fongmi.android.tv.ui.custom;

import static androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN;

import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.fongmi.android.tv.ui.base.BaseFragment;

public abstract class FragmentStateManager {

    private final FragmentManager fm;
    private final ViewGroup container;

    public FragmentStateManager(ViewGroup container, FragmentManager fm) {
        this.container = container;
        this.fm = fm;
    }

    public abstract Fragment getItem(int position);

    public boolean change(int position) {
        String tag = getTag(position);
        Fragment fragment = fm.findFragmentByTag(tag);
        fragment = (fragment == null) ? getItem(position) : fragment;
        FragmentTransaction ft = fm.beginTransaction().setTransition(TRANSIT_FRAGMENT_OPEN);
        if (fm.findFragmentByTag(tag) == null) ft.add(container.getId(), fragment, tag);
        Fragment current = fm.getPrimaryNavigationFragment();
        if (current != null && current != fragment) ft.hide(current);
        ft.show(fragment).setPrimaryNavigationFragment(fragment).setReorderingAllowed(true).commitNowAllowingStateLoss();
        return true;
    }

    private String getTag(int position) {
        return "android:switcher:" + position;
    }

    public BaseFragment getFragment(int position) {
        return (BaseFragment) fm.findFragmentByTag(getTag(position));
    }

    public boolean isVisible(int position) {
        Fragment fragment = getFragment(position);
        return fragment != null && fragment.isVisible();
    }

    public boolean canBack(int position) {
        BaseFragment fragment = getFragment(position);
        return fragment != null && (fragment.canBack() || fragment.isHidden());
    }
}
