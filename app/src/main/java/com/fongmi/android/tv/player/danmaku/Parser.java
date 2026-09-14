package com.fongmi.android.tv.player.danmaku;

import com.fongmi.android.tv.bean.DanmakuData;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.android.AndroidFileSource;
import master.flame.danmaku.danmaku.util.DanmakuUtils;

public class Parser extends BaseDanmakuParser {

    private static final Pattern XML = Pattern.compile("p=\"([^\"]+)\"[^>]*>([^<]+)<");
    private static final Pattern TXT = Pattern.compile("\\[(.*?)\\](.*)");

    @Override
    public Danmakus parse() {
        if (mDataSource == null) return null;
        String line;
        int index = 0;
        Pattern pattern = null;
        Danmakus result = new Danmakus(IDanmakus.ST_BY_TIME);
        AndroidFileSource source = (AndroidFileSource) mDataSource;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(source.data()))) {
            while ((line = br.readLine()) != null) {
                if (Thread.interrupted()) return result;
                if (pattern == null) pattern = line.startsWith("<") ? XML : TXT;
                Matcher matcher = pattern.matcher(line);
                while (matcher.find() && matcher.groupCount() == 2) {
                    BaseDanmaku item = createDanmakuItem(matcher, index++);
                    if (item != null) synchronized (result) {
                        result.addItem(item);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private BaseDanmaku createDanmakuItem(Matcher matcher, int index) {
        try {
            DanmakuData data = new DanmakuData(matcher, mDispDensity);
            int type = data.getType();
            if (type == 2 || type == 3) type = BaseDanmaku.TYPE_SCROLL_RL;
            BaseDanmaku item = mContext.mDanmakuFactory.createDanmaku(type, mContext);
            if (item == null || item.getType() == BaseDanmaku.TYPE_SPECIAL) return null;
            DanmakuUtils.fillText(item, data.getText());
            item.textShadowColor = data.getShadow();
            item.textColor = data.getColor();
            item.flags = mContext.mGlobalFlagValues;
            item.textSize = data.getSize();
            item.setTime(data.getTime());
            item.setTimer(mTimer);
            item.index = index;
            return item;
        } catch (Exception ignored) {
            return null;
        }
    }
}