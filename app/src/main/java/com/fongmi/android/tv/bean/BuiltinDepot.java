package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BuiltinDepot {

    public static final String URL = null;
    public static final String NAME = "";
    public static final String EXTRA_YL_URL = "";

    private BuiltinDepot() {
    }

    /**
     * chenlong.jpg 是伪装后缀，实际为：
     * {"urls":[{"name":"...","url":"..."}, ...]}
     *
     * 返回顺序严格跟 chenlong.jpg 的 urls[] 一致。
     * 搜索页只使用这里返回的关联线路，避免把数据库里以前手工添加的 URL、
     * 老线路、测试线路也混进“多线路搜索”。
     */
    public static List<Config> getConfigs() {
        List<Config> configs = new ArrayList<>();
        try {
            String raw = readAsset("clys/chenlong.jpg");
            JsonObject object = Json.parse(raw).getAsJsonObject();
            if (!object.has("urls")) return configs;

            List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
            for (Depot item : items) {
                if (item == null || item.getUrl().isEmpty()) continue;

                Config saved = AppDatabase.get().getConfigDao().find(item.getUrl(), 0);
                if (saved == null) {
                    saved = Config.create(0, item.getUrl(), item.getName());
                } else if (!item.getName().isEmpty() && !item.getName().equals(saved.getName())) {
                    saved.setName(item.getName());
                    saved.save();
                }
                addUnique(configs, saved);
            }

            // yyy 本身是一条 Config，下面会自己解析出很多 Site。
            // 不再人为定义成“总线路”或“永乐线路”。
            // 没有名称时 Config.getDesc() 会自然显示 URL；
            // 首页标题则自然显示当前选中的 Site 名称。
            Config total = AppDatabase.get().getConfigDao().find(EXTRA_YL_URL, 0);
            if (total == null) {
                total = Config.create(0, EXTRA_YL_URL);
            } else if ("总线路".equals(total.getName()) || "永乐线路".equals(total.getName())) {
                // 只清理我们前面版本人为写入的两个名字，用户自己的名称不动。
                total.setName("");
                total.save();
            }
            addUnique(configs, total);
        } catch (Throwable ignored) {
        }
        return configs;
    }

    public static void ensure() {
        getConfigs();
    }

    public static boolean isTotal(Config config) {
        return config != null && EXTRA_YL_URL.equals(config.getUrl());
    }

    public static boolean contains(Config config) {
        if (config == null || config.getUrl().isEmpty()) return false;
        for (Config item : getConfigs()) {
            if (item.getUrl().equals(config.getUrl())) return true;
        }
        return false;
    }

    private static void addUnique(List<Config> configs, Config config) {
        if (config == null || config.getUrl().isEmpty()) return;
        for (Config item : configs) {
            if (item.getUrl().equals(config.getUrl())) return;
        }
        configs.add(config);
    }

    private static String readAsset(String path) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                App.get().getAssets().open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        }
        String text = builder.toString();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
