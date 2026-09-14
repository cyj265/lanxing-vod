package com.fongmi.android.tv.bean;

import androidx.media3.common.C;

import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

public class CastVideo {

    private final long position;
    private final String name;
    private final String url;

    public static CastVideo get(String name, String url) {
        return new CastVideo(name, url, C.TIME_UNSET);
    }

    public static CastVideo get(String name, String url, long position) {
        return new CastVideo(name, url, position);
    }

    private CastVideo(String name, String url, long position) {
        if (url.startsWith("file")) url = Server.get().getAddress() + "/" + url.replace(Path.rootPath(), "").replace("://", "");
        if (url.contains("127.0.0.1")) url = url.replace("127.0.0.1", Util.getIp());
        this.position = position;
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public long getPosition() {
        return position;
    }
}