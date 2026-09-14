package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.Diffable;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class Word {

    @SerializedName("data")
    private List<Data> data;

    public static Word objectFrom(String str) {
        Word word = App.gson().fromJson(str, Word.class);
        return word == null ? new Word() : word.trans();
    }

    public Word trans() {
        if (Trans.pass()) return this;
        getData().forEach(Data::trans);
        return this;
    }

    public List<Data> getData() {
        return data == null ? Collections.emptyList() : data;
    }

    public static class Data implements Diffable<Data> {

        @SerializedName(value = "title", alternate = "name")
        private String title;

        public String getTitle() {
            return TextUtils.isEmpty(title) ? "" : title;
        }

        @Override
        public boolean isSameItem(Data other) {
            return getTitle().equals(other.getTitle());
        }

        @Override
        public boolean isSameContent(Data other) {
            return getTitle().equals(other.getTitle());
        }

        public Data trans() {
            this.title = Trans.s2t(title);
            return this;
        }
    }
}
