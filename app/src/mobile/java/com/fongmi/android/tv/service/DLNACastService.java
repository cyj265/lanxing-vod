package com.fongmi.android.tv.service;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.fongmi.android.tv.dlna.DlnaDiag;
import com.fongmi.android.tv.dlna.DLNAServiceConfiguration;

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
