package com.fongmi.android.tv.dlna;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

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
    private Context appCtx;
    /** 广播兜底发现节流：避免 onRefresh 连发 3 次 search 起 3 个 UDP 监听线程 */
    private long lastBcast;

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
            DlnaDiag.log("device found: " + bean.getName() + ", avt=" + url);
        } else {
            DlnaDiag.log("device found(no AVT): " + bean.getName());
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
        DlnaDiag.log("onServiceConnected: " + name);
        if (!bound) {
            DlnaDiag.log("onServiceConnected but not bound, ignore");
            return;
        }
        try {
            attach((AndroidUpnpService) binder);
        } catch (Throwable t) {
            DlnaDiag.log("onServiceConnected attach FAILED");
            DlnaDiag.log(t);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        DlnaDiag.log("onServiceDisconnected: " + name);
        detach();
    }

    @Override
    public void onNullBinding(ComponentName name) {
        // API 26+：服务 onCreate 抛异常/返回 null binder 时触发，说明服务根本没起来
        DlnaDiag.log("onNullBinding: service returned null binder (service likely crashed in onCreate?)");
        bound = false;
    }

    @Override
    public void onBindingDied(ComponentName name) {
        DlnaDiag.log("onBindingDied: " + name);
        detach();
        bound = false;
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
        DlnaDiag.init(context);
        appCtx = context.getApplicationContext();
        DlnaDiag.log("init bound=" + bound + " attached=" + (upnpService != null));
        DlnaDiag.logNetwork(context);
        if (upnpService != null) {
            search();
            return;
        }
        if (bound) {
            // 之前 bind 返回了 true 但 onServiceConnected 始终没来（服务起不来），先解绑再重试
            DlnaDiag.log("init: previous bind stale (service never attached), rebinding");
            try {
                unbind(context.getApplicationContext());
            } catch (Throwable ignore) {
            }
        }
        bind(context.getApplicationContext());
        App.post(this::checkAttached, 5000);
        App.post(this::checkDevices, 12000);
    }

    private void checkAttached() {
        if (upnpService == null) DlnaDiag.log("WATCHDOG: service NOT attached after 5s (bound=" + bound + ")");
        else DlnaDiag.log("WATCHDOG: attached ok");
    }

    private void checkDevices() {
        // 把内存缓冲里的全部诊断重放到 logcat 末尾，对抗 ColorOS 把早期行冲掉导致抓不到 bindProcessToNetwork 等关键信息
        DlnaDiag.replay();
        if (upnpService == null) {
            DlnaDiag.logState(appCtx);
            DlnaDiag.log("WATCHDOG VERDICT: upnpAttached=false -> 服务未连接，组播发现不可能进行（看 REPLAY 中 onNullBinding/onCreate FAILED）");
            return;
        }
        int n = upnpService.getRegistry().getDevices(RENDERER_TYPE).size();
        DlnaDiag.logState(appCtx);
        if (n > 0) {
            DlnaDiag.log("WATCHDOG VERDICT: upnpAttached=true foundMediaRenderer=" + n + " -> OK, cast list should have devices");
        } else if (DlnaDiag.probeBlockedByEperm) {
            if (DlnaDiag.isNearbyGranted(appCtx)) {
                DlnaDiag.log("WATCHDOG VERDICT: upnpAttached=true foundMediaRenderer=0 -> PROBE EPERM EVEN THOUGH 'Nearby devices' perm GRANTED. This is a HARD ROM/ColorOS block on app multicast SEND (not fixable by app perm). Options: (1) ColorOS Settings > grant app 'WLAN multicast' sub-perm if present; (2) root + iptables; (3) switch to NsdManager/unicast discovery");
            } else {
                DlnaDiag.log("WATCHDOG VERDICT: upnpAttached=true foundMediaRenderer=0 -> PROBE EPERM and 'Nearby devices' perm NOT granted. Grant it: Settings > Apps > 揽星影视 > Permissions > 附近的设备 = Allow, then retry");
            }
            showEpermToast();
        } else {
            DlnaDiag.log("WATCHDOG VERDICT: upnpAttached=true foundMediaRenderer=0 -> multicast not EPERM-blocked; no device replied (router AP-isolation/IGMP, or no renderer on network)");
        }
    }

    private void showEpermToast() {
        try {
            Toast.makeText(appCtx, "系统拦截了投屏组播发送(EPERM)。已自动改用广播兜底发现，若仍搜不到：设置→应用→揽星影视→权限→开启「附近的设备」，并到系统设置允许 WLAN 多播", Toast.LENGTH_LONG).show();
        } catch (Throwable ignore) {
        }
    }

    public void search() {
        DlnaDiag.log("search");
        if (upnpService != null) upnpService.getControlPoint().search(new STAllHeader());
        // 并行跑广播兜底发现：当 ROM 硬拦组播发送时，这是唯一能主动搜到设备的应用层路径
        if (appCtx != null) broadcastDiscover(appCtx);
    }

    /** 广播兜底发现(绕开组播 EPERM)：收到设备后直接喂进现有列表监听。带 3s 节流。 */
    private void broadcastDiscover(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastBcast < 3000) return;
        lastBcast = now;
        DlnaBroadcastDiscovery.run(context.getApplicationContext(), new DlnaBroadcastDiscovery.Callback() {
            @Override
            public void onDevice(Device device) {
                notifyAdded(device);
            }

            @Override
            public void onLog(String msg) {
                DlnaDiag.log(msg);
            }
        });
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
        DlnaDiag.log("bind() returned " + bound);
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
        DlnaDiag.log("upnp service attached");
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
