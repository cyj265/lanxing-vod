package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.impl.Diffable;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class Value implements Parcelable, Diffable<Value> {

    @SerializedName("n")
    private String n;
    @SerializedName("v")
    private String v;

    private transient boolean activated;

    public static Value create(String v) {
        return new Value(v);
    }

    public static Value create(String n, String v) {
        return new Value(n, v).trans();
    }

    private Value(String v) {
        this.v = v;
    }

    private Value(String n, String v) {
        this.n = n;
        this.v = v;
    }

    public String getN() {
        return TextUtils.isEmpty(n) ? "" : n;
    }

    public String getV() {
        return TextUtils.isEmpty(v) ? "" : v;
    }

    public void setV(String v) {
        this.v = v;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public void setActivated(Value item) {
        boolean equal = item.equals(this);
        if (activated && equal) activated = false;
        else activated = equal;
    }

    public Value copy() {
        Value copy = new Value(n, v);
        copy.activated = this.activated;
        return copy;
    }

    public Value trans() {
        this.n = Trans.s2t(n);
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Value it)) return false;
        return Objects.equals(getV(), it.getV());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getV());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.n);
        dest.writeString(this.v);
        dest.writeByte(this.activated ? (byte) 1 : (byte) 0);
    }

    protected Value(Parcel in) {
        this.n = in.readString();
        this.v = in.readString();
        this.activated = in.readByte() != 0;
    }

    public static final Creator<Value> CREATOR = new Creator<>() {
        @Override
        public Value createFromParcel(Parcel source) {
            return new Value(source);
        }

        @Override
        public Value[] newArray(int size) {
            return new Value[size];
        }
    };

    @Override
    public boolean isSameItem(Value other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Value other) {
        return equals(other);
    }
}
