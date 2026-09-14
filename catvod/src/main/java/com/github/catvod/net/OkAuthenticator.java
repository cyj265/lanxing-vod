package com.github.catvod.net;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class OkAuthenticator implements Authenticator {

    private final List<Proxy> proxy;

    public OkAuthenticator() {
        proxy = new CopyOnWriteArrayList<>();
    }

    public void addAll(List<Proxy> items) {
        proxy.addAll(items);
    }

    public void clear() {
        proxy.clear();
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) {
        if (route == null || response.request().header(HttpHeaders.PROXY_AUTHORIZATION) != null) return null;
        if (!(route.proxy().address() instanceof InetSocketAddress proxyAddress)) return null;
        String requestHost = response.request().url().host();
        String proxyHost = proxyAddress.getHostName();
        for (Proxy item : proxy) {
            for (String host : item.getHosts()) {
                if (Util.containOrMatch(requestHost, host)) {
                    for (String url : item.getUrls()) {
                        if (url.contains(proxyHost)) {
                            String userInfo = Uri.parse(url).getUserInfo();
                            if (userInfo != null) {
                                return response.request().newBuilder().header(HttpHeaders.PROXY_AUTHORIZATION, Util.basic(userInfo)).build();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
