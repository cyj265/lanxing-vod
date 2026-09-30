package com.fongmi.android.tv.dlna;

import android.net.Network;
import android.os.Build;

import java.net.NetworkInterface;
import java.util.Enumeration;

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

    /**
     * 枚举本机真实 WiFi 物理接口（优先 wlan0），避免 ColorOS 在开启 VPN/网络加速时把
     * "WiFi 网络"的链路接口错报成 tun1 导致组播发到隧道里、搜不到局域网设备。
     * 返回 null 时调用方应回退到 link properties 里的接口名。
     */
    public static NetworkInterface pickRealWifiInterface() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            if (nis == null) return null;
            NetworkInterface fallback = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName();
                if (name == null) continue;
                if (name.matches("wlan\\d+")) return ni;            // 真实 WiFi 物理接口
                if (fallback == null && (name.matches("eth\\d+") || name.matches("ap\\d+"))) {
                    fallback = ni;                                  // 以太网 / 软 AP，次选
                }
            }
            return fallback;
        } catch (Throwable ignore) {
            return null;
        }
    }
}
