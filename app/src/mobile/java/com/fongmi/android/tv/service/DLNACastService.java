package com.fongmi.android.tv.service;

import android.util.Log;

import com.fongmi.android.tv.dlna.DLNAServiceConfiguration;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;

public class DLNACastService extends AndroidUpnpServiceImpl {

    private static final String TAG = "DLNACast";

    @Override
    public void onCreate() {
        try {
            Log.d(TAG, "service onCreate: super.create");
            super.onCreate();
            Log.d(TAG, "service super.create done, upnpService=" + (upnpService != null));
            if (upnpService != null) {
                Log.d(TAG, "service starting upnp");
                upnpService.startup();
                Log.d(TAG, "service upnp started");
            }
        } catch (Throwable t) {
            Log.e(TAG, "service onCreate FAILED", t);
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
