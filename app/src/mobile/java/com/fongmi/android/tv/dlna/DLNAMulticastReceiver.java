package com.fongmi.android.tv.dlna;

import org.jupnp.transport.Router;
import org.jupnp.transport.impl.MulticastReceiverConfigurationImpl;
import org.jupnp.transport.impl.MulticastReceiverImpl;
import org.jupnp.transport.spi.DatagramProcessor;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.NetworkAddressFactory;

import java.net.NetworkInterface;

/**
 * jupnp {@link MulticastReceiverImpl} 的子类：SSDP 入站(设备响应接收)走这里。
 * 默认实现创建 MulticastSocket 后立刻 joinGroup 却没把 socket 绑到 WiFi 网络，
 * 在 Android 11+ 上收不到任何设备响应。这里在 super.init() 之后把 socket fd 钉到 WiFi 网络，
 * 并显式 setNetworkInterface(wifi)，使组播成员关系落在 WiFi 接口、能收到回包。
 */
public class DLNAMulticastReceiver extends MulticastReceiverImpl {

    public DLNAMulticastReceiver(MulticastReceiverConfigurationImpl configuration) {
        super(configuration);
    }

    @Override
    public synchronized void init(NetworkInterface multicastInterface, Router router, NetworkAddressFactory networkAddressFactory, DatagramProcessor datagramProcessor) throws InitializationException {
        super.init(multicastInterface, router, networkAddressFactory, datagramProcessor);
        DlnaDiag.bindSocketToWifi(socket, "MulticastReceiver(recv)");
    }
}
