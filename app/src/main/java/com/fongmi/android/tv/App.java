package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.utils.Notify;
import com.tencent.bugly.crashreport.CrashReport;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.github.catvod.utils.Path;
import com.google.gson.Gson;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private final ExecutorService searchExecutor;
    private final ExecutorService executor;
    private final Handler handler;
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private final long time;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        executor = Executors.newFixedThreadPool(5);
        searchExecutor = Executors.newFixedThreadPool(20);
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CrashReport.initCrashReport(getApplicationContext(), "69e90fd596", false);
        CrashReport.setUserId(Build.BRAND + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");
        Notify.createChannel();
        registerActivityLifecycleCallbacks(this);
        executor.execute(this::clearApkCache);
        installNativeCrashGuard();
    }

    /**
     * 捕获 GoProxy 等 native so 加载失败（UnsatisfiedLinkError / bad ELF），
     * 避免单个源的 so 损坏导致整个 APP 崩溃。
     * 仅吞掉 GoProxy/wexproxy 相关错误，其他异常照常走原处理器。
     */
    private void installNativeCrashGuard() {
        final Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (isGoProxyLinkError(throwable)) {
                CrashReport.postCatchedException(throwable);
                return;
            }
            if (original != null) original.uncaughtException(thread, throwable);
        });
    }

    private boolean isGoProxyLinkError(Throwable t) {
        if (t == null) return false;
        if (t instanceof UnsatisfiedLinkError) {
            String msg = t.getMessage();
            if (msg != null && (msg.contains("GoProxy") || msg.contains("wexproxy") || msg.contains("bad ELF"))) return true;
        }
        for (StackTraceElement e : t.getStackTrace()) {
            String cls = e.getClassName();
            if (cls != null && cls.contains("GoProxy")) return true;
        }
        return t.getCause() != null && isGoProxyLinkError(t.getCause());
    }

    private void clearApkCache() {
        try {
            File dir = new File(Path.cache(), "apk");
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) file.delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return get().executor.submit(task);
    }

    public static Future<?> submit(Runnable task) {
        return get().executor.submit(task);
    }

    public static Future<?> submitSearch(Runnable task) {
        return get().searchExecutor.submit(task);
    }

    public static void execute(Runnable runnable) {
        get().executor.execute(runnable);
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }
}