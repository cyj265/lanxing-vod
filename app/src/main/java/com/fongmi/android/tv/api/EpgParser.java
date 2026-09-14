package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Tv;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EpgParser {

    private static final SimpleDateFormat formatTime = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat formatFull = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault());

    public static boolean start(Live live, String url) throws Exception {
        File file = Path.epg(UrlUtil.path(url));
        boolean refresh = shouldRefresh(file);
        if (refresh) Download.create(url, file).get();
        if (isGzip(file)) readGzip(live, file, refresh);
        else readXml(live, file);
        return true;
    }

    public static Epg getEpg(String xml, String key) throws Exception {
        Tv tv = new Persister().read(Tv.class, xml, false);
        Epg epg = Epg.create(key, formatDate.format(Util.parse(formatFull, tv.getDate())));
        tv.getProgramme().forEach(programme -> epg.getList().add(getEpgData(programme)));
        return epg;
    }

    private static boolean shouldRefresh(File file) {
        return !Path.exists(file) || !isToday(file.lastModified()) || System.currentTimeMillis() - file.lastModified() > TimeUnit.HOURS.toMillis(6);
    }

    private static boolean isGzip(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return (fis.read() | (fis.read() << 8)) == 0x8B1F;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isToday(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return calendar.get(Calendar.DAY_OF_MONTH) == Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    }

    private static void readGzip(Live live, File file, boolean refresh) throws Exception {
        File xml = Path.epg(file.getName() + ".xml");
        if (!Path.exists(xml) || refresh) FileUtil.gzipDecompress(file, xml);
        readXml(live, xml);
    }

    private static void readXml(Live live, File file) throws Exception {
        Map<String, Channel> liveChannelMap = prepareLiveChannels(live);
        XmlData xmlData = parseXmlData(file);
        String today = formatDate.format(new Date());
        bindResultsToLive(live, processProgramme(xmlData, liveChannelMap, today));
    }

    private static Map<String, Channel> prepareLiveChannels(Live live) {
        return live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .flatMap(channel -> Stream.of(channel.getTvgId(), channel.getTvgName(), channel.getName())
                        .filter(key -> !key.isEmpty())
                        .map(key -> new AbstractMap.SimpleEntry<>(key, channel)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue,
                        HashMap::new
                ));
    }

    private static XmlData parseXmlData(File file) throws Exception {
        Tv tv = new Persister().read(Tv.class, file, false);
        Map<String, List<Tv.Channel>> map = tv.getChannel().stream().collect(Collectors.groupingBy(Tv.Channel::getId));
        return new XmlData(tv, map);
    }

    private static ProgrammeResult processProgramme(XmlData data, Map<String, Channel> liveChannelMap, String today) {
        Map<String, Epg> epgMap = new HashMap<>();
        Map<String, String> srcMap = new HashMap<>();
        for (Tv.Programme programme : data.tv.getProgramme()) {
            String xmlChannelId = programme.getChannel();
            Channel targetChannel = findTargetChannel(xmlChannelId, liveChannelMap, data.map);
            if (targetChannel == null) continue;
            Date startDate = Util.parse(formatFull, programme.getStart());
            if (!isToday(startDate.getTime())) continue;
            String liveTvgId = targetChannel.getTvgId();
            Date endDate = Util.parse(formatFull, programme.getStop());
            epgMap.computeIfAbsent(liveTvgId, key -> Epg.create(key, today)).getList().add(getEpgData(startDate, endDate, programme));
            Optional.ofNullable(data.map.get(xmlChannelId)).flatMap(list -> list.stream().filter(Tv.Channel::hasSrc).findFirst()).ifPresent(ch -> srcMap.putIfAbsent(liveTvgId, ch.getSrc()));
        }
        return new ProgrammeResult(epgMap, srcMap);
    }

    private static Channel findTargetChannel(String xmlChannelId, Map<String, Channel> liveChannelMap, Map<String, List<Tv.Channel>> xmlChannelIdMap) {
        Channel targetChannel = liveChannelMap.get(xmlChannelId);
        if (targetChannel != null) return targetChannel;
        List<Tv.Channel> channels = xmlChannelIdMap.get(xmlChannelId);
        if (channels == null) return null;
        return channels.stream()
                .flatMap(xmlChannel -> xmlChannel.getDisplayName().stream())
                .map(Tv.DisplayName::getText)
                .filter(name -> !name.isEmpty())
                .filter(liveChannelMap::containsKey)
                .findFirst()
                .map(liveChannelMap::get)
                .orElse(null);
    }

    private static void bindResultsToLive(Live live, ProgrammeResult result) {
        live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .forEach(channel -> {
                    String tvgId = channel.getTvgId();
                    Optional.ofNullable(result.epgMap.get(tvgId)).ifPresent(channel::setData);
                    Optional.ofNullable(result.srcMap.get(tvgId)).ifPresent(channel::setLogo);
                });
    }

    private static EpgData getEpgData(Tv.Programme programme) {
        Date startDate = Util.parse(formatFull, programme.getStart());
        Date endDate = Util.parse(formatFull, programme.getStop());
        return getEpgData(startDate, endDate, programme);
    }

    private static EpgData getEpgData(Date startDate, Date endDate, Tv.Programme programme) {
        try {
            EpgData epgData = new EpgData();
            epgData.setTitle(programme.getTitle());
            epgData.setStart(formatTime.format(startDate));
            epgData.setEnd(formatTime.format(endDate));
            epgData.setStartTime(startDate.getTime());
            epgData.setEndTime(endDate.getTime());
            epgData.trans();
            return epgData;
        } catch (Exception e) {
            return new EpgData();
        }
    }

    private static class XmlData {

        Tv tv;
        Map<String, List<Tv.Channel>> map;

        public XmlData(Tv tv, Map<String, List<Tv.Channel>> map) {
            this.tv = tv;
            this.map = map;
        }
    }

    private static class ProgrammeResult {

        Map<String, Epg> epgMap;
        Map<String, String> srcMap;

        public ProgrammeResult(Map<String, Epg> epgMap, Map<String, String> srcMap) {
            this.epgMap = epgMap;
            this.srcMap = srcMap;
        }
    }
}