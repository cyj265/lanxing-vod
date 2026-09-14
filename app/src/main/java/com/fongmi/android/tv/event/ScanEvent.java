package com.fongmi.android.tv.event;

import org.greenrobot.eventbus.EventBus;

public record ScanEvent(String address) {

    public static void post(String address) {
        EventBus.getDefault().post(new ScanEvent(address));
    }
}
