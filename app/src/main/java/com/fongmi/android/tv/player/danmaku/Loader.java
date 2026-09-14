package com.fongmi.android.tv.player.danmaku;

import com.fongmi.android.tv.bean.Danmaku;
import com.github.catvod.net.OkHttp;

import java.io.InputStream;

import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.parser.android.AndroidFileSource;

public class Loader implements ILoader {

    private AndroidFileSource source;

    public Loader load(Danmaku item) {
        load(item.getRealUrl());
        return this;
    }

    @Override
    public void load(String url) {
        try {
            load(OkHttp.newCall(url, "danmaku").execute().body().byteStream());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void load(InputStream stream) {
        source = new AndroidFileSource(stream);
    }

    @Override
    public AndroidFileSource getDataSource() {
        return source;
    }
}