package com.fongmi.android.tv.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.github.catvod.net.OkHttp;

import java.io.InputStream;

@GlideModule
public class OkGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setLogLevel(Log.ERROR);
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, "image_cache", 50 * 1024 * 1024));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, Registry registry) {
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(OkHttp.client()));
    }
}