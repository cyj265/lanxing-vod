package com.fongmi.hook;

import android.os.Looper;

import java.util.Arrays;

public class Chromium {

    private static final String CLASS_NAME = "org.chromium.base.buildinfo";
    private static final String METHOD_NAME = "getall";

    public static boolean find() {
        try {
            return Arrays.stream(Looper.getMainLooper().getThread().getStackTrace()).anyMatch(trace -> CLASS_NAME.equalsIgnoreCase(trace.getClassName()) && METHOD_NAME.equalsIgnoreCase(trace.getMethodName()));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
