package com.github.catvod.bean;

import android.net.Uri;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Proxy implements Comparable<Proxy> {

    @SerializedName("name")
    private String name;
    @SerializedName("hosts")
    private List<String> hosts;
    @SerializedName("urls")
    private List<String> urls;

    private List<java.net.Proxy> proxies;
    private boolean wildcard;

    public static List<Proxy> arrayFrom(JsonElement element) {
        try {
            Type listType = new TypeToken<List<Proxy>>() {}.getType();
            List<Proxy> items = new Gson().fromJson(element, listType);
            return items == null ? Collections.emptyList() : items;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void init() {
        wildcard = getHosts().stream().anyMatch(host -> host.contains("*"));
        proxies = getUrls().stream().map(this::create).filter(Objects::nonNull).toList();
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public List<String> getHosts() {
        return hosts == null ? Collections.emptyList() : hosts;
    }

    public List<String> getUrls() {
        return urls == null ? Collections.emptyList() : urls;
    }

    public List<java.net.Proxy> getProxies() {
        return proxies == null ? Collections.emptyList() : proxies;
    }

    private java.net.Proxy create(String url) {
        Uri uri = Uri.parse(url);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getPort() <= 0) return null;
        if (uri.getScheme().startsWith("http")) return new java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        if (uri.getScheme().startsWith("socks")) return new java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        return null;
    }

    @Override
    public int compareTo(Proxy other) {
        return Boolean.compare(this.wildcard, other.wildcard);
    }
}