package com.fongmi.android.tv.service;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.fongmi.android.tv.dlna.DLNAServiceConfiguration;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;

public class DLNACastService extends AndroidUpnpServiceImpl {

    private WifiManager.MulticastLock multicastLock;

    @Override
    public void onCreate() {
        super.onCreate();
        // Android 上接收入站 SSDP 组播响应需要 Wifi MulticastLock，否则部分 ROM/省电策略会丢弃组播包，
        // 表现为「M-SEARCH 发出成功、但永远收不到设备响应(foundMediaRenderer=0)」。仅影响接收，不影响发送。
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("lanxing-dlna");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (Throwable ignore) {
            // 拿不到锁也不阻断发送，仅接收可能受限
        }
        upnpService.startup();
    }

    @Override
    public void onDestroy() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Throwable ignore) {
        }
        super.onDestroy();
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
