package com.fongmi.android.tv.utils;

import android.content.pm.PackageManager;
import android.webkit.CookieManager;

import com.fongmi.android.tv.App;

import java.util.Set;

public class WebViewUtil {

    private static final String SYSTEM_SETTINGS_PACKAGE = "com.android.settings";

    private static final Set<String> BROWSER_PACKAGES = Set.of(
            "com.android.chrome",
            "com.mi.globalbrowser",
            "com.huawei.browser",
            "com.heytap.browser",
            "com.vivo.browser"
    );

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public static String spoof() {
        PackageManager pm = App.get().getPackageManager();
        return BROWSER_PACKAGES.stream().filter(packageName -> installed(pm, packageName)).findFirst().orElse(SYSTEM_SETTINGS_PACKAGE);
    }

    public static boolean support() {
        try {
            CookieManager.getInstance();
            return App.get().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WEBVIEW);
        } catch (Throwable e) {
            return false;
        }
    }
}
