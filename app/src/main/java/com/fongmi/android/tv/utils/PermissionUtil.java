package com.fongmi.android.tv.utils;

import android.Manifest;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.PermissionCallback;
import com.permissionx.guolindev.PermissionX;

import java.util.function.Consumer;

public class PermissionUtil {

    public static void requestAudio(FragmentActivity activity, Consumer<Boolean> callback) {
        PermissionX.init(activity).permissions(Manifest.permission.RECORD_AUDIO).request(new PermissionCallback(callback));
    }

    public static void requestFile(FragmentActivity activity, Consumer<Boolean> callback) {
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(callback));
    }

    public static void requestFile(Fragment fragment, Consumer<Boolean> callback) {
        PermissionX.init(fragment).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(callback));
    }

    /**
     * DLNA 投屏发现权限：SSDP 组播发现必需定位权限（Android 6~12）。
     * Android 13+ 的 NEARBY_WIFI_DEVICES 仅对 targetSdk>=33 生效，
     * 本项目 targetSdk=30，系统将其映射为定位权限代理，故统一申请定位权限。
     */
    public static void requestCast(FragmentActivity activity, Consumer<Boolean> callback) {
        boolean nearby = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.TIRAMISU;
        PermissionX.init(activity).permissions(nearby ? Manifest.permission.NEARBY_WIFI_DEVICES : Manifest.permission.ACCESS_COARSE_LOCATION).request(new PermissionCallback(callback));
    }
}
