package com.fongmi.android.tv.event;

import com.fongmi.android.tv.Setting;

import org.greenrobot.eventbus.EventBus;

public record ConfigEvent(Type type) {

    public static void common() {
        EventBus.getDefault().post(new ConfigEvent(Type.COMMON));
    }

    public static void vod() {
        EventBus.getDefault().post(new ConfigEvent(Type.VOD));
    }

    public static void live() {
        EventBus.getDefault().post(new ConfigEvent(Type.LIVE));
    }

    public static void wall() {
        EventBus.getDefault().post(new ConfigEvent(Type.WALL));
    }

    public static void boot() {
        EventBus.getDefault().post(new ConfigEvent(Type.BOOT));
        Setting.putBootLive(false);
    }

    public enum Type {
        COMMON, VOD, LIVE, WALL, BOOT
    }
}
