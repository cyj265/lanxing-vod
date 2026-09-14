package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import okhttp3.OkHttpClient;
import okhttp3.Response;

public class ScanTask {

    private final List<Future<?>> future;
    private final OkHttpClient client;
    private Listener listener;

    public ScanTask(Listener listener) {
        this.client = OkHttp.client(1000);
        this.future = new ArrayList<>();
        this.listener = listener;
    }

    public void start() {
        App.execute(() -> run(getUrl()));
    }

    public void start(String url) {
        App.execute(() -> run(List.of(url)));
    }

    public void stop() {
        listener = null;
        OkHttp.cancel(client, "scan");
        future.forEach(f -> f.cancel(true));
        future.clear();
    }

    private void run(List<String> urls) {
        for (String url : urls) future.add(App.submitSearch(() -> findDevice(url)));
    }

    private List<String> getUrl() {
        String local = Server.get().getAddress();
        String base = local.substring(0, local.lastIndexOf(".") + 1);
        return IntStream.range(1, 256).mapToObj(i -> base + i + ":9978").toList();
    }

    private void findDevice(String url) {
        if (url.equals(Server.get().getAddress())) return;
        try (Response res = OkHttp.newCall(client, url.concat("/device"), "scan").execute()) {
            Device device = Device.objectFrom(res.body().string());
            if (device != null) App.post(() -> {
                if (listener != null) listener.onFind(device.save());
            });
        } catch (Exception ignored) {
        }
    }

    public interface Listener {

        void onFind(Device device);
    }
}
