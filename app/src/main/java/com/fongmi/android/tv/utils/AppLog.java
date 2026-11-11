package com.fongmi.android.tv.utils;

import android.util.Log;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 普通异常日志，与 CrashLog 的崩溃取证分开。
 * <p>
 * 项目里不少 catch 只调 printStackTrace()，栈只进 logcat，本地没有留底，
 * 用户报「加载源失败」时拿不到现场。这里统一收口：logcat 照常打一份，
 * 同时追加到应用私有目录的 app-exceptions.log。刻意不写 CrashLog 的
 * crash/ 目录——设置页「查看日志」读的是最近一次崩溃，混进普通异常会
 * 把取证语义冲掉。文件超过 512KB 从头重写，避免长期累积占空间。
 */
public final class AppLog {

    private static final String TAG = "AppLog";
    private static final String FILE = "app-exceptions.log";
    private static final long MAX_BYTES = 512L * 1024L;
    private static final Object LOCK = new Object();

    private AppLog() {
    }

    /** 最简形式：栈本身已包含调用点，logcat 过滤 AppLog 即可定位 */
    public static void e(Throwable throwable) {
        e(null, throwable == null ? "" : throwable.toString(), throwable);
    }

    public static void e(String message, Throwable throwable) {
        e(null, message, throwable);
    }

    private static void e(String tag, String message, Throwable throwable) {
        String logTag = tag == null ? TAG : tag;
        Log.e(logTag, message, throwable);
        App app = App.get();
        if (app == null) return;
        synchronized (LOCK) {
            try {
                File file = new File(app.getFilesDir(), FILE);
                if (file.exists() && file.length() > MAX_BYTES && !file.delete()) return;
                StringWriter trace = new StringWriter();
                if (throwable != null) throwable.printStackTrace(new PrintWriter(trace));
                String entry = timestamp() + " E/" + logTag + " " + message + "\n" + trace + "\n";
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(entry.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) {
                // 记日志失败不能影响业务，更不能把异常抛回调用方
            }
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
