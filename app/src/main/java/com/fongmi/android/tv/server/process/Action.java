package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.FormBody;

public class Action implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/action");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        Map<String, String> params = session.getParms();
        String param = params.get("do");
        if ("file".equals(param)) onFile(params);
        else if ("push".equals(param)) onPush(params);
        else if ("cast".equals(param)) onCast(params);
        else if ("sync".equals(param)) onSync(params);
        else if ("search".equals(param)) onSearch(params);
        else if ("setting".equals(param)) onSetting(params);
        else if ("refresh".equals(param)) onRefresh(params);
        else if ("control".equals(param)) onControl(params);
        return Nano.ok();
    }

    private void onFile(Map<String, String> params) {
        String path = params.get("path");
        if (TextUtils.isEmpty(path)) return;
        if (path.endsWith(".apk")) FileUtil.openFile(Path.local(path));
        else if (path.endsWith(".srt") || path.endsWith(".ssa") || path.endsWith(".ass")) RefreshEvent.subtitle(path);
        else ServerEvent.setting(path);
    }

    private void onPush(Map<String, String> params) {
        String url = params.get("url");
        if (TextUtils.isEmpty(url)) return;
        ServerEvent.push(url);
    }

    private void onSearch(Map<String, String> params) {
        String word = params.get("word");
        if (TextUtils.isEmpty(word)) return;
        ServerEvent.search(word);
    }

    private void onSetting(Map<String, String> params) {
        String text = params.get("text");
        String name = params.get("name");
        if (TextUtils.isEmpty(text)) return;
        ServerEvent.setting(text, name);
    }

    private void onRefresh(Map<String, String> params) {
        String type = params.get("type");
        String path = params.get("path");
        String json = params.get("json");
        if ("live".equals(type)) RefreshEvent.live();
        else if ("detail".equals(type)) RefreshEvent.detail();
        else if ("player".equals(type)) RefreshEvent.player();
        else if ("danmaku".equals(type)) RefreshEvent.danmaku(path);
        else if ("subtitle".equals(type)) RefreshEvent.subtitle(path);
        else if ("vod".equals(type)) RefreshEvent.vod(Vod.objectFrom(json));
    }

    private void onControl(Map<String, String> params) {
        String type = params.get("type");
        if ("stop".equals(type)) ActionEvent.stop();
        else if ("prev".equals(type)) ActionEvent.prev();
        else if ("next".equals(type)) ActionEvent.next();
        else if ("loop".equals(type)) ActionEvent.loop();
        else if ("play".equals(type)) ActionEvent.play();
        else if ("pause".equals(type)) ActionEvent.pause();
        else if ("replay".equals(type)) ActionEvent.replay();
    }

    private void onCast(Map<String, String> params) {
        Config config = Config.objectFrom(params.get("config"));
        Device device = Device.objectFrom(params.get("device"));
        History history = History.objectFrom(params.get("history"));
        CastEvent.post(Config.find(config), device, history);
    }

    private void onSync(Map<String, String> params) {
        String type = params.get("type");
        boolean force = Objects.equals(params.get("force"), "true");
        String mode = Objects.requireNonNullElse(params.get("mode"), "0");
        if (params.get("device") != null && (mode.equals("0") || mode.equals("2"))) {
            Device device = Device.objectFrom(params.get("device"));
            if ("history".equals(type)) sendHistory(device, params);
            else if ("keep".equals(type)) sendKeep(device);
        }
        if (mode.equals("0") || mode.equals("1")) {
            if ("history".equals(type)) syncHistory(params, force);
            else if ("keep".equals(type)) syncKeep(params, force);
        }
    }

    private void post(Device device, String type, FormBody.Builder body) {
        try {
            OkHttp.newCall(OkHttp.client(Constant.TIMEOUT_SYNC), device.getIp().concat("/action?do=sync&mode=0&type=" + type), body.build()).execute();
        } catch (Exception e) {
            App.post(() -> Notify.show(e.getMessage()));
        }
    }

    private void sendHistory(Device device, Map<String, String> params) {
        try {
            Config config = Config.find(Config.objectFrom(params.get("config")));
            if (config.getUrl() == null) config = Config.vod();
            FormBody.Builder body = new FormBody.Builder();
            body.add("config", config.toString());
            body.add("targets", App.gson().toJson(History.get(config.getId())));
            post(device, "history", body);
        } catch (Exception e) {
            App.post(() -> Notify.show(e.getMessage()));
        }
    }

    private void sendKeep(Device device) {
        try {
            FormBody.Builder body = new FormBody.Builder();
            body.add("targets", App.gson().toJson(Keep.getVod()));
            body.add("configs", App.gson().toJson(Config.findUrls()));
            post(device, "keep", body);
        } catch (Exception e) {
            App.post(() -> Notify.show(e.getMessage()));
        }
    }

    public void syncHistory(Map<String, String> params, boolean force) {
        Config config = Config.find(Config.objectFrom(params.get("config")));
        List<History> targets = History.arrayFrom(params.get("targets"));
        if (config.getUrl() == null) return;
        if (config.getUrl().equals(VodConfig.getUrl())) {
            if (force) History.delete(config.getId());
            History.sync(targets);
            RefreshEvent.history();
        } else {
            VodConfig.load(config, getCallback(targets, force, config.getId()));
        }
    }

    private Callback getCallback(List<History> targets, boolean force, int cid) {
        return new Callback() {
            @Override
            public void success() {
                if (force) History.delete(cid);
                History.sync(targets);
                RefreshEvent.history();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private void syncKeep(Map<String, String> params, boolean force) {
        List<Keep> targets = Keep.arrayFrom(params.get("targets"));
        List<Config> configs = Config.arrayFrom(params.get("configs"));
        if (TextUtils.isEmpty(VodConfig.getUrl()) && !configs.isEmpty()) {
            VodConfig.load(Config.find(configs.get(0)), getCallback(configs, targets, force));
        } else {
            if (force) Keep.deleteAll();
            Keep.sync(configs, targets);
            RefreshEvent.keep();
        }
    }

    private Callback getCallback(List<Config> configs, List<Keep> targets, boolean force) {
        return new Callback() {
            @Override
            public void success() {
                if (force) Keep.deleteAll();
                Keep.sync(configs, targets);
                RefreshEvent.keep();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }
}
