package com.fongmi.android.tv.event;

import com.cyj265.lanxingvod.BuildConfig;

import org.greenrobot.eventbus.EventBus;

public record ActionEvent(String action) {

    public static String STOP = BuildConfig.APPLICATION_ID.concat(".stop");
    public static String PREV = BuildConfig.APPLICATION_ID.concat(".prev");
    public static String NEXT = BuildConfig.APPLICATION_ID.concat(".next");
    public static String LOOP = BuildConfig.APPLICATION_ID.concat(".loop");
    public static String PLAY = BuildConfig.APPLICATION_ID.concat(".play");
    public static String PAUSE = BuildConfig.APPLICATION_ID.concat(".pause");
    public static String AUDIO = BuildConfig.APPLICATION_ID.concat(".audio");
    public static String REPLAY = BuildConfig.APPLICATION_ID.concat(".replay");
    public static String UPDATE = BuildConfig.APPLICATION_ID.concat(".update");

    public static void send(String action) {
        EventBus.getDefault().post(new ActionEvent(action));
    }

    public static void stop() {
        send(STOP);
    }

    public static void prev() {
        send(PREV);
    }

    public static void next() {
        send(NEXT);
    }

    public static void loop() {
        send(LOOP);
    }

    public static void play() {
        send(PLAY);
    }

    public static void pause() {
        send(PAUSE);
    }

    public static void replay() {
        send(REPLAY);
    }

    public static void update() {
        send(UPDATE);
    }

    public boolean isUpdate() {
        return UPDATE.equals(action());
    }
}
