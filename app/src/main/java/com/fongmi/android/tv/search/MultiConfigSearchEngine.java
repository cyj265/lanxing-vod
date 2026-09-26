package com.fongmi.android.tv.search;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MultiConfigSearchEngine {

    public interface Callback {
        // Config 已初始化，告诉 UI 它下面有哪些真实 Site。
        void onSites(Config config, List<Site> sites);

        // 某一个 Site 搜到一批结果，立即推海报。
        void onSiteResult(Config config, Site site, List<Vod> items);

        void onLineError(Config config, Throwable error);
        void onFinish();
    }

    private static final String TAG = "MultiConfigSearch";
    private static final String RESTORE_TAG = "MultiConfigRestore";
    private static final long MIN_LINE_TIMEOUT_MS = 12000L;
    private static final long MAX_LINE_TIMEOUT_MS = 45000L;

    private final AtomicInteger token = new AtomicInteger(0);
    private volatile ExecutorService sitePool;
    private volatile Future<?> worker;
    private volatile boolean running;
    private volatile Runnable afterStop;

    public synchronized void start(List<Config> configs, String keyword, Callback callback) {
        stop(null);
        final int current = token.incrementAndGet();
        final Config original = VodConfig.get().getConfig();
        final String originalUrl = original == null ? "" : original.getUrl();
        final String preferredSiteKey = VodConfig.get().getHome().getKey();
        running = true;

        worker = Task.submit(() -> {
            try {
                for (Config config : configs) {
                    if (!alive(current)) break;
                    try {
                        // 关键修复：完整初始化“当前 Config”，不再只装 spider/sites。
                        // 某些 CSP/JS/PY 搜索期间会反查 VodConfig.get()，所以必须让
                        // 全局 VodConfig 在这一条线路搜索期间与该线路保持一致。
                        VodConfig.get().loadForSearch(config, TAG);
                        if (!alive(current)) break;

                        List<Site> sites = new ArrayList<>(VodConfig.get().getSites().stream()
                                // 只有 searchable=0 才明确禁止搜索。
                                // searchable=2 不能因此整批漏掉。
                                .filter(site -> !site.isHide() && site.getSearchable() != 0)
                                .toList());

                        // 从主页海报跳进搜索时，当前正在看的真实 Site 最先搜。
                        if (config.getUrl().equals(originalUrl) && !preferredSiteKey.isEmpty()) {
                            for (int i = 0; i < sites.size(); i++) {
                                if (preferredSiteKey.equals(sites.get(i).getKey())) {
                                    Site preferred = sites.remove(i);
                                    sites.add(0, preferred);
                                    break;
                                }
                            }
                        }

                        App.post(() -> {
                            if (alive(current)) callback.onSites(config, sites);
                        });

                        // 海报墙必须即时出来：哪个 Site 先搜到结果就先推给 UI，
                        // 不再等待整条 Config 的全部 Site 完成/超时。
                        searchSites(current, config, sites, keyword, callback);
                        if (!alive(current)) break;
                    } catch (Throwable error) {
                        App.post(() -> {
                            if (alive(current)) callback.onLineError(config, error);
                        });
                    }
                }
            } finally {
                restore(original);
                Runnable next;
                synchronized (MultiConfigSearchEngine.this) {
                    running = false;
                    next = afterStop;
                    afterStop = null;
                }
                if (alive(current)) App.post(callback::onFinish);
                if (next != null) App.post(next);
            }
        });
    }

    public synchronized void stop() {
        stop(null);
    }

    /**
     * 停止搜索并等内部 finally 把原 Config/Spider 恢复后再执行 after。
     * 点击搜索结果时必须走这里，避免“恢复旧 Spider”和“加载目标 Config”并发打架。
     */
    public synchronized void stop(Runnable after) {
        token.incrementAndGet();
        afterStop = after;
        OkHttp.cancel(TAG);

        ExecutorService pool = sitePool;
        sitePool = null;
        if (pool != null) pool.shutdownNow();

        if (!running && afterStop != null) {
            Runnable next = afterStop;
            afterStop = null;
            App.post(next);
        }
    }

    private boolean alive(int current) {
        return current == token.get() && !Thread.currentThread().isInterrupted();
    }

    private void searchSites(
            int current,
            Config config,
            List<Site> sites,
            String keyword,
            Callback callback) throws Exception {

        if (sites.isEmpty()) return;

        // yyy 这类聚合接口 Site 很多，6 线程会导致后面的源长时间排队。
        int threads = Math.min(12, Math.max(1, sites.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        sitePool = pool;
        CompletionService<SearchBatch> completion = new ExecutorCompletionService<>(pool);
        List<Future<SearchBatch>> futures = new ArrayList<>();

        try {
            for (Site site : sites) {
                futures.add(completion.submit(
                        () -> new SearchBatch(site, SiteApi.searchContent(site, keyword, false, "1"))
                ));
            }

            int remaining = futures.size();

            // 大接口按 Site 数量自动增加搜索时间，最多 45 秒。
            // 结果仍然是流式返回，有结果马上出海报。
            long dynamicTimeout = Math.max(
                    MIN_LINE_TIMEOUT_MS,
                    Math.min(MAX_LINE_TIMEOUT_MS, 8000L + sites.size() * 700L)
            );
            long deadline = System.currentTimeMillis() + dynamicTimeout;

            while (remaining > 0 && alive(current)) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) break;

                Future<SearchBatch> future = completion.poll(Math.min(left, 400), TimeUnit.MILLISECONDS);
                if (future == null) continue;
                remaining--;

                try {
                    SearchBatch found = future.get();
                    Result result = found.result();
                    if (result == null || result.getList().isEmpty()) continue;

                    List<Vod> batch = new ArrayList<>();
                    for (Vod vod : result.getList()) {
                        vod.setSearchCid(config.getId());
                        batch.add(vod);
                    }

                    // 关键：保留真实 Site 身份。
                    // “聚合 Config”不再只显示一个固定入口，而是可以看到它下面
                    // 腾讯备 / 爱奇艺 / 猎手 / 袋鼠 ... 等真实子源。
                    App.post(() -> {
                        if (alive(current)) callback.onSiteResult(config, found.site(), batch);
                    });
                } catch (Throwable ignored) {
                }
            }
        } finally {
            for (Future<SearchBatch> future : futures) {
                if (!future.isDone()) future.cancel(true);
            }
            pool.shutdownNow();
            if (sitePool == pool) sitePool = null;
        }
    }

    private record SearchBatch(Site site, Result result) {
    }

    private void restore(Config original) {
        if (original == null || original.getUrl().isEmpty()) return;
        try {
            VodConfig.get().loadForSearch(original, RESTORE_TAG);
        } catch (Throwable ignored) {
        }
    }
}
