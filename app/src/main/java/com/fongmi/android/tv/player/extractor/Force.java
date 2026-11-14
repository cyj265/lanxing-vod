package com.fongmi.android.tv.player.extractor;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.SystemClock;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.UrlUtil;
import com.forcetech.Util;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

public class Force implements Source.Extractor, ServiceConnection {

    private static final Pattern PATTERN = Pattern.compile("(?i)(p[2-9]p|mitv)");
    private static volatile Boolean sNativeAvailable;
    private final HashSet<String> set = new HashSet<>();

    /**
     * forcetech-release.aar 的 jni/ 只有 armeabi-v7a 的 libmitv.so / libp2p.so / libp3p.so，
     * 而本工程只出 arm64_v8a 包（abiFilters "arm64-v8a"），这些 32 位库不会进 APK 的 lib/。
     * SDK 内部把 System.loadLibrary 的失败静默吞掉，服务照常绑定；等退出播放时
     * PxPService / MainActivity 的 onUnbind 无条件调 ForceTV.stop()，native 实现缺失
     * 直接抛 UnsatisfiedLinkError（Bugly #7016 / #7018）。checkLibrary() 把 assets 里的
     * libmitv.so 拷到 cache 也救不了：System.loadLibrary 不查 cache，且那是 32 位 ELF。
     * <p>
     * 与其改二进制 aar，不如在入口判断 native 库是否真的可用：不可用时压根不绑定服务，
     * 让这些源按普通 URL 交给播放器（原本也播不了），把「无效且闪退」变成「无效但不崩」。
     * 若日后 aar 换成 arm64-v8a 的 so，去掉这里的 is64Bit 判断即可自动重新启用。
     */
    private static boolean nativeAvailable() {
        if (sNativeAvailable != null) return sNativeAvailable;
        String dir = App.get().getApplicationInfo().nativeLibraryDir;
        sNativeAvailable = dir != null
                && new File(dir, System.mapLibraryName("mitv")).isFile()
                && !android.os.Process.is64Bit();
        return sNativeAvailable;
    }

    @Override
    public boolean match(Uri uri) {
        return nativeAvailable() && PATTERN.matcher(UrlUtil.scheme(uri)).find();
    }

    private void init(String scheme) {
        App.get().bindService(Util.intent(App.get(), scheme), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public String fetch(String url) throws Exception {
        String scheme = Util.scheme(url);
        if (!set.contains(scheme)) init(scheme);
        long deadline = SystemClock.elapsedRealtime() + 8000;
        while (!set.contains(scheme)) {
            if (SystemClock.elapsedRealtime() >= deadline) throw new IllegalStateException("Force service not connected: " + scheme);
            SystemClock.sleep(10);
        }
        Uri uri = UrlUtil.uri(url);
        int port = Util.port(scheme);
        String id = uri.getLastPathSegment();
        String cmd = "http://127.0.0.1:" + port + "/cmd.xml?cmd=switch_chan&server=" + uri.getHost() + ":" + uri.getPort() + "&id=" + id;
        OkHttp.string(cmd, Map.of(HttpHeaders.USER_AGENT, "MTV"));
        return "http://127.0.0.1:" + port + "/" + id;
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
        try {
            App.get().unbindService(this);
        } catch (Exception ignored) {
        } finally {
            set.clear();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        set.add(Util.trans(name));
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        set.remove(Util.trans(name));
    }
}
