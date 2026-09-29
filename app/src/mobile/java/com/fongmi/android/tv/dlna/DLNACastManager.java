package com.fongmi.android.tv.dlna;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.service.DLNACastService;

import org.jupnp.android.AndroidUpnpService;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.message.header.STAllHeader;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceType;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * DLNA 发送端发现：基于 jupnp 的 UpnpService（与影视仓同款网络栈，在一加等机型上可正常收发组播）。
 * 由 DLNACastService 承载 AndroidUpnpService，本类 bind 后监听 Registry 发现 MediaRenderer。
 * 设备控制仍走 DLNACast（SOAP SetAVTransportURI），故发现时把 AVTransport 控制地址写入 Device.url/ip。
 */
public class DLNACastManager extends DefaultRegistryListener implements ServiceConnection {

    private static final String TAG = "DLNACast";
    private static final UDADeviceType RENDERER_TYPE = new UDADeviceType("MediaRenderer", 1);
    private static final UDAServiceType AVT_TYPE = new UDAServiceType("AVTransport", 1);

    private AndroidUpnpService upnpService;
    private DeviceListener deviceListener;
    private boolean bound;

    public static DLNACastManager get() {
        return Loader.INSTANCE;
    }

    @Override
    public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
        if (device.getType().implementsVersion(RENDERER_TYPE)) notifyAdded(buildDevice(device));
    }

    @Override
    public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
        if (device.getType().implementsVersion(RENDERER_TYPE)) notifyRemoved(buildDevice(device));
    }

    private Device buildDevice(RemoteDevice device) {
        Device bean = Device.get(device);
        RemoteService avt = device.findService(AVT_TYPE);
        if (avt != null && avt.getControlURI() != null) {
            String url = avt.getControlURI().toString();
            bean.setUrl(url);
            bean.setIp(host(url));
            Log.d(TAG, "device found: " + bean.getName() + ", avt=" + url);
        } else {
            Log.d(TAG, "device found(no AVT): " + bean.getName());
        }
        return bean;
    }

    private String host(String url) {
        try {
            URI parsed = new URI(url);
            return parsed.getPort() > 0 ? parsed.getHost() + ":" + parsed.getPort() : parsed.getHost();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        Log.d(TAG, "onServiceConnected: " + name);
        if (!bound) {
            Log.w(TAG, "onServiceConnected but not bound, ignore");
            return;
        }
        try {
            attach((AndroidUpnpService) binder);
        } catch (Throwable t) {
            Log.e(TAG, "onServiceConnected attach FAILED", t);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        detach();
    }

    public void setDeviceListener(DeviceListener listener) {
        deviceListener = listener;
    }

    private void notifyAdded(Device bean) {
        if (deviceListener != null) App.post(() -> deviceListener.onDeviceAdded(bean));
    }

    private void notifyRemoved(Device bean) {
        if (deviceListener != null) App.post(() -> deviceListener.onDeviceRemoved(bean));
    }

    public void init(Context context) {
        Log.d(TAG, "init bound=" + bound);
        if (bound) {
            search();
        } else {
            bind(context.getApplicationContext());
            App.post(this::checkAttached, 5000);
        }
    }

    private void checkAttached() {
        if (upnpService == null) Log.w(TAG, "WATCHDOG: service NOT attached after 5s (bound=" + bound + ")");
        else Log.d(TAG, "WATCHDOG: attached ok");
    }

    public void search() {
        Log.d(TAG, "search");
        if (upnpService != null) upnpService.getControlPoint().search(new STAllHeader());
    }

    public List<Device> getRegistered() {
        List<Device> result = new ArrayList<>();
        if (upnpService == null) return result;
        for (org.jupnp.model.meta.Device d : upnpService.getRegistry().getDevices(RENDERER_TYPE)) {
            result.add(buildDevice((RemoteDevice) d));
        }
        return result;
    }

    public RemoteDevice findDevice(Device bean) {
        if (upnpService == null) return null;
        for (org.jupnp.model.meta.Device d : upnpService.getRegistry().getDevices(RENDERER_TYPE)) {
            if (d.getIdentity().getUdn().getIdentifierString().equals(bean.getUuid())) return (RemoteDevice) d;
        }
        return null;
    }

    public RemoteService findAVTransport(Device bean) {
        RemoteDevice rd = findDevice(bean);
        return rd != null ? rd.findService(AVT_TYPE) : null;
    }

    public ControlPoint getControlPoint() {
        return upnpService != null ? upnpService.getControlPoint() : null;
    }

    public void release(Context context) {
        detach();
        unbind(context.getApplicationContext());
    }

    private void bind(Context context) {
        bound = context.bindService(new Intent(context, DLNACastService.class), this, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "bind() returned " + bound);
    }

    private void unbind(Context context) {
        if (!bound) return;
        context.unbindService(this);
        bound = false;
    }

    private void attach(AndroidUpnpService service) {
        detach();
        upnpService = service;
        upnpService.getRegistry().addListener(this);
        Log.d(TAG, "upnp service attached");
        search();
    }

    private void detach() {
        if (upnpService != null) upnpService.getRegistry().removeListener(this);
        upnpService = null;
    }

    public interface DeviceListener {

        void onDeviceAdded(Device device);

        void onDeviceRemoved(Device device);
    }

    private static class Loader {
        static final DLNACastManager INSTANCE = new DLNACastManager();
    }
}
