package com.fongmi.android.tv.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.fongmi.android.tv.dlna.DlnaDiag;
import com.fongmi.android.tv.dlna.DlnaNet;
import com.fongmi.android.tv.dlna.DLNAServiceConfiguration;

import java.net.NetworkInterface;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;

public class DLNACastService extends AndroidUpnpServiceImpl {

    private static final String TAG = "DLNACast";
    private WifiManager.MulticastLock multicastLock;

    @Override
    public void onCreate() {
        DlnaDiag.init(getApplicationContext());
        DlnaDiag.logNetwork(getApplicationContext());
        // 必须在 super.onCreate() 之前绑定：Android 11+ 按网络隔离路由，组播不发往 WiFi 接口会被内核 EPERM 掐死。
        // 把进程路由绑到活动 WiFi 网络，jupnp 后续创建的组播 socket 才会从 WiFi 发出去。
        bindWifiNetwork();
        // 显式再拿一次 MulticastLock（jupnp 内部也会拿，这里 double-acquire 仅用于诊断可见性）
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("lanxing-dlna");
                multicastLock.setReferenceCounted(true);
                multicastLock.acquire();
                DlnaDiag.log("multicast lock acquired held=" + multicastLock.isHeld());
            } else {
                DlnaDiag.log("multicast lock: WifiManager=null");
            }
        } catch (Throwable t) {
            DlnaDiag.log("multicast lock acquire FAILED");
            DlnaDiag.log(t);
        }
        try {
            DlnaDiag.log("service onCreate: super.create");
            super.onCreate();
            DlnaDiag.log("service super.create done, upnpService=" + (upnpService != null));
            if (upnpService != null) {
                DlnaDiag.log("service starting upnp");
                upnpService.startup();
                DlnaDiag.log("service upnp started");
            }
            // 决定性探针：独立验证「进程绑 WiFi + Network.bindSocket(MulticastSocket)」在本机是否让组播通畅
            DlnaDiag.probeMulticast(getApplicationContext());
        } catch (Throwable t) {
            DlnaDiag.log("service onCreate FAILED");
            DlnaDiag.log(t);
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Throwable ignore) {
        }
        super.onDestroy();
        // 恢复进程默认路由（投屏结束，不再劫持全局网络到 WiFi）
        unbindWifiNetwork();
        DlnaNet.clear();
    }

    /** 把进程网络路由绑到活动 WiFi 网络，使 jupnp 的 SSDP 组播能从 WiFi 接口发出（根治 Android 11+ 的 EPERM）。 */
    private void bindWifiNetwork() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            DlnaDiag.log("bindWifi: SDK<23, skip (no bindProcessToNetwork)");
            return;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                DlnaDiag.log("bindWifi: ConnectivityManager=null");
                return;
            }
            Network net = cm.getActiveNetwork();
            if (net == null) {
                DlnaDiag.log("bindWifi: activeNetwork=null");
                return;
            }
            NetworkCapabilities cap = cm.getNetworkCapabilities(net);
            if (cap == null || !cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                DlnaDiag.log("bindWifi: active network is NOT wifi (cellular/other), skip");
                return;
            }
            boolean ok = cm.bindProcessToNetwork(net);
            DlnaDiag.log("bindProcessToNetwork(wifi)=" + ok + " net=" + net);
            // 决定组播出接口：优先枚举真实 WiFi 物理接口(wlan0)，避免 ColorOS 在 VPN/网络加速下
            // 把"WiFi 网络"的链路接口错报成 tun1 导致组播发进隧道、搜不到局域网设备。
            NetworkInterface ni = DlnaNet.pickRealWifiInterface();
            if (ni == null) {
                try {
                    LinkProperties lp = cm.getLinkProperties(net);
                    if (lp != null && lp.getInterfaceName() != null) {
                        ni = NetworkInterface.getByName(lp.getInterfaceName());
                    }
                } catch (Throwable ignore) {
                }
            }
            DlnaNet.set(net, ni);
            DlnaDiag.log("DlnaNet set: wifiInterface=" + (ni != null ? ni.getDisplayName() : "null")
                    + (ni != null && !ni.getName().matches("wlan\\d+") ? " (WARN: not wlan0, VPN/tunnel may be active)" : ""));
        } catch (Throwable t) {
            DlnaDiag.log("bindWifi FAILED");
            DlnaDiag.log(t);
        }
    }

    private void unbindWifiNetwork() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.bindProcessToNetwork(null);
            DlnaDiag.log("bindProcessToNetwork(null) restored");
        } catch (Throwable ignore) {
        }
    }

    @Override
    protected UpnpServiceConfiguration createConfiguration() {
        return new DLNAServiceConfiguration() {
            @Override
            public ServiceType[] getExclusiveServiceTypes() {
                return new ServiceType[]{new UDAServiceType("AVTransport", 1)};
            }
        };
    }
}
