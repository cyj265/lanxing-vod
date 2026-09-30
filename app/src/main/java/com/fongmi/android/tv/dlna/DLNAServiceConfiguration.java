package com.fongmi.android.tv.dlna;

import android.os.Build;

import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.model.ServerClientTokens;
import org.jupnp.transport.impl.DatagramIOConfigurationImpl;
import org.jupnp.transport.impl.MulticastReceiverConfigurationImpl;
import org.jupnp.transport.spi.DatagramIO;
import org.jupnp.transport.spi.MulticastReceiver;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

public class DLNAServiceConfiguration extends AndroidUpnpServiceConfiguration {

    @Override
    @SuppressWarnings("rawtypes")
    public StreamClient createStreamClient() {
        return new OkHttpStreamClient(new OkHttpStreamClient.Configuration(getSyncProtocolExecutorService()) {
            @Override
            public String getUserAgentValue(int majorVersion, int minorVersion) {
                ServerClientTokens tokens = new ServerClientTokens(majorVersion, minorVersion);
                tokens.setOsVersion(Build.VERSION.RELEASE);
                tokens.setOsName("Android");
                return tokens.toString();
            }
        });
    }

    @Override
    @SuppressWarnings("rawtypes")
    public StreamServer createStreamServer(NetworkAddressFactory networkAddressFactory) {
        return new SocketHttpStreamServer(new SocketHttpStreamServer.Configuration(networkAddressFactory.getStreamListenPort()));
    }

    /**
     * 覆写 jupnp 的 SSDP 收发 socket 工厂，返回会主动把 MulticastSocket 钉到 WiFi 网络的子类，
     * 根治 Android 11+(ColorOS/一加) 上组播被内核 EPERM 掐死、搜不到设备的问题。
     */
    @Override
    public DatagramIO createDatagramIO(NetworkAddressFactory networkAddressFactory) {
        return new DLNADatagramIO(new DatagramIOConfigurationImpl());
    }

    @Override
    public MulticastReceiver createMulticastReceiver(NetworkAddressFactory networkAddressFactory) {
        return new DLNAMulticastReceiver(new MulticastReceiverConfigurationImpl(
                networkAddressFactory.getMulticastGroup(), networkAddressFactory.getMulticastPort()));
    }
}
