package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ReleaseTrust;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.google.gson.Gson;
import com.tencent.bugly.crashreport.CrashReport;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private Activity activity;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
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
        Notify.createChannel();
        registerActivityLifecycleCallbacks(this);
        if (!BuildConfig.BUGLY_APP_ID.isEmpty() && isOfficialSignature()) initBugly();
    }

    /**
     * 只有官方证书签名的包才上报崩溃。
     * <p>
     * 5.4 线把 Bugly AppID 写死在代码里，任何人 fork 或二次打包出来的包都会往同一个
     * Bugly 产品灌崩溃；重签名的包不再上报，官方包签名固定，不受影响。
     */
    private boolean isOfficialSignature() {
        try {
            PackageManager pm = getPackageManager();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo info = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                signatures = info == null ? null : info.getApkContentsSigners();
            } else {
                signatures = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            }
            if (signatures == null) return false;
            for (Signature signature : signatures) {
                if (ReleaseTrust.isTrustedCert(signature.toByteArray())) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /** 初始化 Bugly 崩溃自动上报（逻辑与揽星TV一致：Android ID 作设备标识，不取 IMEI 等敏感信息） */
    private void initBugly() {
        try {
            String deviceId;
            try {
                deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Exception ignored) {
                deviceId = "unknown";
            }
            CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(getApplicationContext());
            strategy.setDeviceID(deviceId);
            strategy.setAppChannel("github");
            strategy.setAppVersion(BuildConfig.VERSION_NAME);
            strategy.setAppPackageName(getPackageName());
            CrashReport.initCrashReport(getApplicationContext(), BuildConfig.BUGLY_APP_ID, false, strategy);
        } catch (Exception e) {
            // 初始化失败不影响 App 正常运行
            e.printStackTrace();
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
}