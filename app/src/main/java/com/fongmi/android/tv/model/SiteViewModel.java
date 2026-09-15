package com.fongmi.android.tv.model;

import android.text.TextUtils;

import androidx.collection.ArrayMap;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.cyj265.lanxingvod.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Response;

public class SiteViewModel extends ViewModel {

    private final List<Future<?>> searchFuture;
    private final ExecutorService executor;
    private final AtomicInteger taskId;
    private Future<Result> future;

    public final MutableLiveData<Result> result;
    public final MutableLiveData<Result> player;
    public final MutableLiveData<Result> search;
    public final MutableLiveData<Result> action;
    public final MutableLiveData<Result> detailList;

    public SiteViewModel() {
        taskId = new AtomicInteger(0);
        result = new MutableLiveData<>();
        player = new MutableLiveData<>();
        search = new MutableLiveData<>();
        action = new MutableLiveData<>();
        detailList = new MutableLiveData<>();
        searchFuture = new ArrayList<>();
        executor = Executors.newSingleThreadExecutor();
    }

    public SiteViewModel init() {
        search.setValue(null);
        result.setValue(null);
        player.setValue(null);
        action.setValue(null);
        return this;
    }

    public void homeContent() {
        execute(result, () -> {
            Site site = VodConfig.get().getHome();
            if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                boolean crash = Prefers.getBoolean("crash");
                String homeContent = crash ? "" : spider.homeContent(true);
                String homeVideoContent = crash ? "" : spider.homeVideoContent();
                Prefers.put("crash", false);
                SpiderDebug.log("home", homeContent);
                SpiderDebug.log("homeVideo", homeVideoContent);
                Result result = Result.fromJson(homeContent);
                List<Vod> list = Result.fromJson(homeVideoContent).getList();
                if (!list.isEmpty()) result.setList(list);
                setTypes(site, result);
                return result;
            } else if (site.getType() == 4) {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("filter", "true");
                String homeContent = call(site.fetchExt(), params);
                SpiderDebug.log("home", homeContent);
                Result result = Result.fromJson(homeContent);
                setTypes(site, result);
                return result;
            } else {
                try (Response response = OkHttp.newCall(site.getApi(), site.getHeader()).execute()) {
                    String homeContent = response.body().string();
                    SpiderDebug.log("home", homeContent);
                    Result result = Result.fromType(site.getType(), homeContent);
                    setTypes(site, result);
                    fetchPic(site, result);
                    return result;
                }
            }
        });
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        execute(result, () -> {
            Site site = VodConfig.get().getSite(key);
            SpiderDebug.log("category", "key=%s,tid=%s,page=%s,filter=%s,extend=%s", key, tid, page, filter, extend);
            if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String categoryContent = spider.categoryContent(tid, page, filter, extend);
                SpiderDebug.log("category", categoryContent);
                return Result.fromJson(categoryContent);
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                if (site.getType() == 1 && !extend.isEmpty()) params.put("f", App.gson().toJson(extend));
                if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(extend), Util.URL_SAFE));
                params.put("ac", site.getType() == 0 ? "videolist" : "detail");
                params.put("t", tid);
                params.put("pg", page);
                String categoryContent = call(site, params);
                SpiderDebug.log("category", categoryContent);
                return Result.fromType(site.getType(), categoryContent);
            }
        });
    }

    public void detailContent(String key, String id) {
        execute(result, () -> {
            Site site = VodConfig.get().getSite(key);
            SpiderDebug.log("detail", "key=%s,id=%s", key, id);
            if (site.isEmpty() && "push_agent".equals(key)) {
                Vod vod = new Vod();
                vod.setId(id);
                vod.setName(id);
                vod.setPlayUrl(id);
                vod.setPlayFrom(ResUtil.getString(R.string.push));
                vod.setPic(ResUtil.getString(R.string.push_image));
                Source.get().parse(vod.setFlags());
                return Result.vod(vod);
            } else if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String detailContent = spider.detailContent(Arrays.asList(id));
                SpiderDebug.log("detail", detailContent);
                Result result = Result.fromJson(detailContent);
                Source.get().parse(result.getVod().setFlags());
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("ac", site.getType() == 0 ? "videolist" : "detail");
                params.put("ids", id);
                String detailContent = call(site, params);
                SpiderDebug.log("detail", detailContent);
                Result result = Result.fromType(site.getType(), detailContent);
                Source.get().parse(result.getVod().setFlags());
                return result;
            }
        });
    }

    public void detailContentBatch(String key, String ids) {
        execute(detailList, () -> {
            Site site = VodConfig.get().getSite(key);
            SpiderDebug.log("detailBatch", "key=%s,ids=%s", key, ids);
            if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String detailContent = spider.detailContent(Arrays.asList(ids.split(",")));
                SpiderDebug.log("detailBatch", detailContent);
                return Result.fromJson(detailContent);
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("ac", "detail");
                params.put("ids", ids);
                String detailContent = call(site, params);
                SpiderDebug.log("detailBatch", detailContent);
                return Result.fromType(site.getType(), detailContent);
            }
        });
    }

    public void playerContent(String key, String flag, String id) {
        execute(player, () -> {
            Source.get().stop();
            Site site = VodConfig.get().getSite(key);
            SpiderDebug.log("player", "key=%s,flag=%s,id=%s", key, flag, id);
            if (site.getType() == 3) {
                Spider spider = site.recent().spider();
                String playerContent = spider.playerContent(flag, id, VodConfig.get().getFlags());
                SpiderDebug.log("player", playerContent);
                Result result = Result.fromJson(playerContent);
                if (result.getFlag().isEmpty()) result.setFlag(flag);
                result.setUrl(Source.get().fetch(result));
                result.setHeader(site.getHeader());
                result.setKey(key);
                return result;
            } else if (site.getType() == 4) {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("play", id);
                params.put("flag", flag);
                String playerContent = call(site, params);
                SpiderDebug.log("player", playerContent);
                Result result = Result.fromJson(playerContent);
                if (result.getFlag().isEmpty()) result.setFlag(flag);
                result.setUrl(Source.get().fetch(result));
                result.setHeader(site.getHeader());
                return result;
            } else if (site.isEmpty() && "push_agent".equals(key)) {
                Result result = new Result();
                result.setUrl(id);
                result.setParse(0);
                result.setFlag(flag);
                result.setUrl(Source.get().fetch(result));
                SpiderDebug.log("player", result.toString());
                return result;
            } else {
                Result result = new Result();
                result.setUrl(id);
                result.setFlag(flag);
                result.setHeader(site.getHeader());
                result.setPlayUrl(site.getPlayUrl());
                result.setParse(Sniffer.isVideoFormat(id) && result.getPlayUrl().isEmpty() ? 0 : 1);
                result.setUrl(Source.get().fetch(result));
                SpiderDebug.log("player", result.toString());
                return result;
            }
        });
    }

    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        stopSearch();
        sites.forEach(site -> searchFuture.add(App.submitSearch(SearchTask.create(this, site, keyword, quick).run())));
    }

    public void searchContent(Site site, String keyword, boolean quick, String page) {
        execute(result, SearchTask.create(this, site, keyword, quick, page));
    }

    public void action(String key, String action) {
        execute(this.action, () -> {
            Site site = VodConfig.get().getSite(key);
            SpiderDebug.log("action", "key=%s,action=%s", key, action);
            if (site.getType() == 3) return Result.fromJson(site.recent().spider().action(action));
            if (site.getType() == 4) return Result.fromJson(OkHttp.string(action));
            return Result.empty();
        });
    }

    public String call(Site site, ArrayMap<String, String> params) throws IOException {
        if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
        Call get = OkHttp.newCall(site.getApi(), site.getHeader(), params);
        Call post = OkHttp.newCall(site.getApi(), site.getHeader(), OkHttp.toBody(params));
        try (Response response = (site.getExt().length() <= 1000 ? get : post).execute()) {
            return response.body().string();
        }
    }

    public Result fetchPic(Site site, Result result) throws Exception {
        if (site.getType() > 2 || result.getList().isEmpty() || !result.getVod().getPic().isEmpty()) return result;
        ArrayList<String> ids = new ArrayList<>();
        boolean empty = site.getCategories().isEmpty();
        for (Vod item : result.getList()) if (empty || site.getCategories().contains(item.getTypeName())) ids.add(item.getId());
        if (ids.isEmpty()) return result.clear();
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("ac", site.getType() == 0 ? "videolist" : "detail");
        params.put("ids", TextUtils.join(",", ids));
        try (Response response = OkHttp.newCall(site.getApi(), site.getHeader(), params).execute()) {
            result.setList(Result.fromType(site.getType(), response.body().string()).getList());
            return result;
        }
    }

    private void setTypes(Site site, Result result) {
        result.getTypes().stream().filter(type -> result.getFilters().containsKey(type.getTypeId())).forEach(type -> type.setFilters(result.getFilters().get(type.getTypeId())));
        List<Class> types = site.getCategories().stream().flatMap(cate -> result.getTypes().stream().filter(type -> cate.equals(type.getTypeName()))).toList();
        if (!types.isEmpty()) result.setTypes(types);
    }

    private void execute(MutableLiveData<Result> result, Callable<Result> callable) {
        int currentId = taskId.incrementAndGet();
        if (future != null && !future.isDone()) future.cancel(true);
        if (executor.isShutdown()) return;
        future = App.submit(callable);
        executor.execute(() -> {
            try {
                Result taskResult = future.get(Constant.TIMEOUT_VOD, TimeUnit.MILLISECONDS);
                if (taskId.get() != currentId) return;
                result.postValue(taskResult);
            } catch (CancellationException ignored) {
            } catch (Throwable e) {
                if (taskId.get() != currentId) return;
                if (e.getCause() instanceof ExtractException) result.postValue(Result.error(e.getCause().getMessage()));
                else result.postValue(Result.empty());
                e.printStackTrace();
            }
        });
    }

    public void stopSearch() {
        searchFuture.forEach(future -> future.cancel(true));
        searchFuture.clear();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (future != null) future.cancel(true);
        if (executor != null) executor.shutdownNow();
    }
}
