package com.fongmi.android.tv.model;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Trans;

import java.util.concurrent.Callable;

public class SearchTask implements Callable<Result> {

    private final SiteViewModel model;
    private final String keyword;
    private final boolean quick;
    private final String page;
    private final Site site;

    public static SearchTask create(SiteViewModel model, Site site, String keyword, boolean quick) {
        return new SearchTask(model, site, keyword, quick, "1");
    }

    public static SearchTask create(SiteViewModel model, Site site, String keyword, boolean quick, String page) {
        return new SearchTask(model, site, keyword, quick, page);
    }

    private SearchTask(SiteViewModel model, Site site, String keyword, boolean quick, String page) {
        this.keyword = Trans.t2s(keyword);
        this.model = model;
        this.quick = quick;
        this.page = page;
        this.site = site;
    }

    public Runnable run() {
        return () -> {
            try {
                model.search.postValue(call());
            } catch (Throwable ignored) {
            }
        };
    }

    @Override
    public Result call() throws Exception {
        if (quick && !site.isQuickSearch()) return Result.empty();
        boolean hasPage = !page.equals("1");
        if (site.getType() == 3) {
            String searchContent = hasPage ? site.spider().searchContent(keyword, quick, page) : site.spider().searchContent(keyword, quick);
            SpiderDebug.log("search", "site=%s,keyword=%s,quick=%s,page=%s\n%s", site.getName(), keyword, quick, page, searchContent.trim());
            Result result = Result.fromJson(searchContent);
            for (Vod vod : result.getList()) vod.setSite(site);
            return result;
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("wd", keyword);
            params.put("quick", String.valueOf(quick));
            params.put("extend", "");
            if (hasPage) params.put("pg", page);
            String searchContent = model.call(site, params);
            SpiderDebug.log("search", "site=%s,keyword=%s,quick=%s,page=%s\n%s", site.getName(), keyword, quick, page, searchContent.trim());
            Result result = model.fetchPic(site, Result.fromType(site.getType(), searchContent));
            for (Vod vod : result.getList()) vod.setSite(site);
            return result;
        }
    }
}
