package com.fongmi.android.tv.dlna;

import org.jupnp.transport.Router;
import org.jupnp.transport.impl.DatagramIOConfigurationImpl;
import org.jupnp.transport.impl.DatagramIOImpl;
import org.jupnp.transport.spi.DatagramProcessor;
import org.jupnp.transport.spi.InitializationException;

import java.net.InetAddress;

/**
 * jupnp {@link DatagramIOImpl} 的子类：SSDP 出站(M-SEARCH 发送)走这里。
 * 默认实现把 MulticastSocket 绑到本地地址却不设网络，导致 Android 11+ 上组播被内核 EPERM 掐死。
 * 这里在 super.init() 之后把 socket fd 通过 {@link android.net.Network#bindSocket} 钉到 WiFi 网络，
 * 并显式 setNetworkInterface(wifi)，使发送从 WiFi 接口出去。
 */
public class DLNADatagramIO extends DatagramIOImpl {

    public DLNADatagramIO(DatagramIOConfigurationImpl configuration) {
        super(configuration);
    }

    @Override
    public synchronized void init(InetAddress bindAddress, int bindPort, Router router, DatagramProcessor datagramProcessor) throws InitializationException {
        super.init(bindAddress, bindPort, router, datagramProcessor);
        DlnaDiag.bindSocketToWifi(socket, "DatagramIO(send)");
    }
}
