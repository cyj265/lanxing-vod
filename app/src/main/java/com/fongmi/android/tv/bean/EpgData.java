package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.cyj265.lanxingvod.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import java.util.Calendar;
import java.util.Objects;

public class EpgData {

    @SerializedName("title")
    private String title;
    @SerializedName("start")
    private String start;
    @SerializedName("end")
    private String end;

    private boolean selected;
    private long startTime;
    private long endTime;

    public String getTitle() {
        return TextUtils.isEmpty(title) ? "" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStart() {
        return TextUtils.isEmpty(start) ? "" : start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return TextUtils.isEmpty(end) ? "" : end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setSelected(EpgData item) {
        this.selected = item.equals(this);
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean isInRange() {
        return getStartTime() <= System.currentTimeMillis() && System.currentTimeMillis() <= getEndTime();
    }

    public boolean isFuture() {
        return getStartTime() > System.currentTimeMillis();
    }

    public String format() {
        if (getTitle().isEmpty()) return "";
        if (getStart().isEmpty() && getEnd().isEmpty()) return ResUtil.getString(R.string.play_now, getTitle());
        return getStart() + " ~ " + getEnd() + "  " + getTitle();
    }

    public String getTime() {
        if (getStart().isEmpty() && getEnd().isEmpty()) return "";
        return getStart() + " ~ " + getEnd();
    }

    public void checkDay() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(getEndTime());
        cal.add(Calendar.DAY_OF_MONTH, 1);
        setEndTime(cal.getTimeInMillis());
    }

    public void trans() {
        if (Trans.pass()) return;
        this.title = Trans.s2t(title);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EpgData it)) return false;
        return Objects.equals(getTitle(), it.getTitle()) && Objects.equals(getEnd(), it.getEnd()) && Objects.equals(getStart(), it.getStart());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTitle(), getEnd(), getStart());
    }
}
