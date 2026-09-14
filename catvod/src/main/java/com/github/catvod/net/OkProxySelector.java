package com.github.catvod.net;

import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OkProxySelector extends ProxySelector {

    private final List<Proxy> proxy;
    private final ProxySelector system;

    public OkProxySelector() {
        proxy = new CopyOnWriteArrayList<>();
        system = ProxySelector.getDefault();
    }

    public synchronized void addAll(List<Proxy> items) {
        items.forEach(Proxy::init);
        List<Proxy> newList = Stream.concat(proxy.stream(), items.stream()).sorted().toList();
        proxy.clear();
        proxy.addAll(newList);
    }

    public void clear() {
        proxy.clear();
    }

    private List<java.net.Proxy> fallback(URI uri) {
        return system != null ? system.select(uri) : List.of(java.net.Proxy.NO_PROXY);
    }

    @Override
    public List<java.net.Proxy> select(URI uri) {
        if (proxy.isEmpty() || uri.getHost() == null || "127.0.0.1".equals(uri.getHost())) return fallback(uri);
        for (Proxy item : proxy) for (String host : item.getHosts()) if (Util.containOrMatch(uri.getHost(), host)) return !item.getProxies().isEmpty() ? item.getProxies() : fallback(uri);
        return fallback(uri);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
        if (system != null) system.connectFailed(uri, socketAddress, e);
    }
}
