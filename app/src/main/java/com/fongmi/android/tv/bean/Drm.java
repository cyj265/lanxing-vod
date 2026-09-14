package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.gson.HeaderAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Drm {

    @SerializedName("key")
    private String key;
    @SerializedName("type")
    private String type;
    @SerializedName("forceKey")
    private boolean forceKey;
    @SerializedName("header")
    @JsonAdapter(HeaderAdapter.class)
    private Map<String, String> header;

    public static Drm create(String key, String type, Map<String, String> header, boolean forceKey) {
        return new Drm(key, type, header, forceKey);
    }

    private Drm(String key, String type, Map<String, String> header, boolean forceKey) {
        this.key = key;
        this.type = type;
        this.header = header;
        this.forceKey = forceKey;
    }

    private String getKey() {
        return TextUtils.isEmpty(key) ? "" : key;
    }

    private String getType() {
        return TextUtils.isEmpty(type) ? "" : type;
    }

    public boolean isForceKey() {
        return forceKey;
    }

    private Map<String, String> getHeader() {
        return header == null ? new HashMap<>() : header;
    }

    public UUID getUUID() {
        if (getType().contains("playready")) return C.PLAYREADY_UUID;
        if (getType().contains("widevine")) return C.WIDEVINE_UUID;
        if (getType().contains("clearkey")) return C.CLEARKEY_UUID;
        return C.UUID_NIL;
    }

    public MediaItem.DrmConfiguration get() {
        MediaItem.DrmConfiguration.Builder builder = new MediaItem.DrmConfiguration.Builder(getUUID());
        builder.setMultiSession(!C.CLEARKEY_UUID.equals(getUUID()));
        builder.setForceDefaultLicenseUri(isForceKey());
        builder.setLicenseRequestHeaders(getHeader());
        builder.setLicenseUri(getKey());
        return builder.build();
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
