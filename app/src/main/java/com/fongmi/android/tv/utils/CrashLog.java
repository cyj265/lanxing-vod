package com.fongmi.android.tv.utils;

import android.content.Context;
import android.os.Build;

import com.fongmi.android.tv.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃日志落盘。
 * <p>
 * 崩溃时先写一份到应用私有目录，再交给 CustomActivityOnCrash 弹错误页；这样即使
 * Bugly 控制台打不开或者没来得及上报，日志也还在本地，可以从崩溃页复制/分享出去。
 */
public final class CrashLog {

    private static final String DIR = "crash";
    private static final int MAX_FILES = 10;
    private static final long MERGE_WINDOW = 60_000L;

    private static boolean installed;

    private CrashLog() {
    }

    /**
     * 在 CustomActivityOnCrash 安装好自己的 handler 之后调用，包装一层先落盘。
     */
    public static void install(Context context) {
        if (installed) return;
        installed = true;
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                save(app, thread, throwable);
            } catch (Throwable ignored) {
                // 落盘失败不能影响原来的崩溃处理
            }
            if (previous != null) previous.uncaughtException(thread, throwable);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    /**
     * 崩溃页拿到更完整的现场信息后，覆盖刚写的那份日志，避免一次崩溃留下两个文件。
     */
    public static File saveDetails(Context context, String details) {
        File dir = dir(context);
        File latest = latest(dir);
        if (latest != null && System.currentTimeMillis() - latest.lastModified() < MERGE_WINDOW) {
            write(latest, details);
            return latest;
        }
        return write(context, details);
    }

    public static File latest(Context context) {
        return latest(dir(context));
    }

    private static File save(Context context, Thread thread, Throwable throwable) {
        StringWriter trace = new StringWriter();
        if (throwable != null) throwable.printStackTrace(new PrintWriter(trace));
        String content = "time: " + timestamp() + "\n"
                + "version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "thread: " + (thread == null ? "unknown" : thread.getName()) + "\n\n"
                + trace;
        return write(context, content);
    }

    private static File write(Context context, String content) {
        File file = new File(dir(context), "crash-" + fileTime() + ".log");
        write(file, content);
        prune(dir(context));
        return file;
    }

    private static void write(File file, String content) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static File dir(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File latest(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files[0];
    }

    private static void prune(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null || files.length <= MAX_FILES) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = MAX_FILES; i < files.length; i++) files[i].delete();
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String fileTime() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
    }
}
