package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class ScanTask {

    private final CopyOnWriteArrayList<Future<?>> future;
    private final List<Device> devices = new ArrayList<>();
    private final OkHttpClient client;
    private volatile Listener listener;
    private volatile boolean replaceCache;

    public ScanTask(Listener listener) {
        this.client = OkHttp.client(1000);
        this.future = new CopyOnWriteArrayList<>();
        this.listener = listener;
    }

    public void start() {
        Task.execute(() -> run(getUrl(), true));
    }

    public void start(String url) {
        Task.execute(() -> run(List.of(url), false));
    }

    public void stop() {
        listener = null;
        synchronized (devices) {
            devices.clear();
        }
        OkHttp.cancel(client, "scan");
        future.forEach(f -> f.cancel(true));
        future.clear();
    }

    private void run(List<String> urls, boolean replace) {
        replaceCache = replace && !urls.isEmpty();
        AtomicInteger remaining = new AtomicInteger(urls.size());
        for (String url : urls) future.add(Task.submitLarge(() -> findDevice(url, remaining)));
    }

    private List<String> getUrl() {
        String local = Server.get().getAddress();
        String base = local.substring(0, local.lastIndexOf(".") + 1);
        return IntStream.range(1, 256).mapToObj(i -> base + i + ":9978").toList();
    }

    private void findDevice(String url, AtomicInteger remaining) {
        try {
            if (url.equals(Server.get().getAddress())) return;
            try (Response res = OkHttp.newCall(client, url.concat("/device"), "scan").execute()) {
                Device device = Device.objectFrom(res.body().string());
                if (device != null) publish(device);
            }
        } catch (Exception ignored) {
        } finally {
            if (remaining.decrementAndGet() == 0) finish();
        }
    }

    private void publish(Device device) {
        Listener listener = this.listener;
        if (listener == null) return;
        synchronized (devices) {
            devices.removeIf(item -> item.isSameItem(device));
            devices.add(device);
        }
        App.post(() -> {
            if (this.listener != null) this.listener.onFind(replaceCache ? device : device.save());
        });
    }

    private void finish() {
        Listener listener = this.listener;
        if (listener == null || !replaceCache) return;
        App.post(() -> {
            if (this.listener == null) return;
            synchronized (devices) {
                // 一台都没扫到通常是自己没联网，此时保留旧缓存比清空更有用。
                if (!devices.isEmpty()) Device.replaceAppDevices(new ArrayList<>(devices));
            }
        });
    }

    public interface Listener {

        void onFind(Device device);
    }
}
