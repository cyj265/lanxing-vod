package com.fongmi.android.tv.dlna;

import android.net.Network;
import android.os.Build;

import java.net.NetworkInterface;

/**
 * 持有当前活动 WiFi 的 {@link Network} 与 {@link NetworkInterface}，供 jupnp 的 MulticastSocket 子类
 * 在创建后调用 {@link Network#bindSocket(java.net.DatagramSocket)} 把 socket fd 钉死到 WiFi 网络，
 * 根治 Android 11+（尤其 ColorOS/一加）上投屏 SSDP 组播被内核 EPERM 掐死的问题。
 *
 * 由 {@code DLNACastService.onCreate()} 在 super.onCreate() 之前写入，onDestroy() 清空。
 */
public final class DlnaNet {

    public static Network wifiNetwork;
    public static NetworkInterface wifiInterface;

    private DlnaNet() {
    }

    public static boolean ready() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && wifiNetwork != null && wifiInterface != null;
    }

    public static void set(Network net, NetworkInterface ni) {
        wifiNetwork = net;
        wifiInterface = ni;
    }

    public static void clear() {
        wifiNetwork = null;
        wifiInterface = null;
    }
}
