package com.fongmi.android.tv.dlna;

import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 投屏诊断：关键事件同时写 (1) logcat(tag=DLNACast) (2) 内存环形缓冲 (3) app 私有目录文件。
 * 内存缓冲用于"重放"——ColorOS 的 logcat 环形缓冲会被系统日志冲掉早期行，
 * 看门狗触发时 replay() 把内存里的全部诊断一次性重发到 logcat 末尾，保证抓日志不丢关键信息。
 */
public final class DlnaDiag {

    private static final String TAG = "DLNACast";
    private static final String NAME = "dlna_diag.log";
    private static final long MAX_BYTES = 80_000;
    private static final int MEM_CAP = 400;
    private static File sFile;
    /** 探针发 M-SEARCH 是否被内核 EPERM 拦截（ColorOS/Android 11+ 的组播权限闸门）。供看门狗判定并弹用户提示。 */
    public static volatile boolean probeBlockedByEperm = false;
    private static final List<String> MEM = new ArrayList<>();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    public static void init(Context context) {
        if (sFile != null) return;
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir != null) sFile = new File(dir, NAME);
        } catch (Throwable ignore) {
        }
    }

    /** Android 13+ 的「附近的设备」权限(NEARBY_WIFI_DEVICES)是否已授予。
     *  这是判断 EPERM 到底是"权限没给"还是"给了仍被 ROM 硬拦"的决定性开关。
     *  pre-13 不需要该权限，视为已满足。 */
    public static boolean isNearbyGranted(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        try {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.NEARBY_WIFI_DEVICES)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static synchronized void log(String msg) {
        String line = SDF.format(new Date()) + "  " + msg;
        Log.d(TAG, msg);
        append(line);
        mem(line);
    }

    public static synchronized void log(Throwable t) {
        StringBuilder sb = new StringBuilder(SDF.format(new Date())).append("  EXC ")
                .append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
        StackTraceElement[] st = t.getStackTrace();
        int n = Math.min(st.length, 10);
        for (int i = 0; i < n; i++) sb.append("    at ").append(st[i]).append("\n");
        Log.e(TAG, "EXC", t);
        append(sb.toString());
        mem(sb.toString().trim());
    }

    /** 把内存环形缓冲全部重放到 logcat 末尾（看门狗调用，对抗 logcat 环形缓冲冲掉早期行） */
    public static synchronized void replay() {
        Log.d(TAG, "===== DLNA DIAG REPLAY (" + MEM.size() + " lines) =====");
        StringBuilder chunk = new StringBuilder();
        for (String s : MEM) {
            if (chunk.length() + s.length() + 1 > 3800) {
                Log.d(TAG, chunk.toString());
                chunk.setLength(0);
            }
            chunk.append(s).append("\n");
        }
        if (chunk.length() > 0) Log.d(TAG, chunk.toString());
        Log.d(TAG, "===== DLNA DIAG REPLAY END =====");
    }

    /** 当前网络 + 进程绑网状态快照，看门狗自带结论用 */
    public static void logState(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                log("state: ConnectivityManager=null");
                return;
            }
            Network net = cm.getActiveNetwork();
            String active = "null";
            if (net != null) {
                NetworkCapabilities cap = cm.getNetworkCapabilities(net);
                boolean wifi = cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                boolean cellular = cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                active = "wifi=" + wifi + ",cellular=" + cellular;
            }
            Network bound = cm.getBoundNetworkForProcess();
            log("state: activeNet=[" + active + "] processBoundTo=" + (bound == null ? "default" : bound.toString()));
        } catch (Throwable t) {
            log(t);
        }
    }

    /** 抓取关键网络状态，便于判断组播失败的环境原因（Android 17 按网络隔离路由） */
    public static void logNetwork(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                log("net: ConnectivityManager=null");
                return;
            }
            Network net = cm.getActiveNetwork();
            if (net == null) {
                log("net: activeNetwork=null (无活动网络)");
                return;
            }
            NetworkCapabilities cap = cm.getNetworkCapabilities(net);
            boolean wifi = cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean cellular = cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            boolean eth = cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            Network bound = cm.getBoundNetworkForProcess();
            log("net: active transport wifi=" + wifi + " cellular=" + cellular + " ethernet=" + eth
                    + " | processBoundTo=" + (bound == null ? "default" : bound.toString()));
        } catch (Throwable t) {
            log(t);
        }
    }

    public static File file(Context context) {
        init(context);
        return sFile;
    }

    /**
     * 把 jupnp 内部创建的 MulticastSocket 钉到活动 WiFi 网络（Android 11+ 根治组播 EPERM 的关键）。
     * 在 socket 已创建后调用 Network.bindSocket + setNetworkInterface，任何异常都被吞掉，
     * 绝不因为我们这层增强导致 jupnp 本身的发送/接收失败。
     */
    public static void bindSocketToWifi(DatagramSocket socket, String tag) {
        if (socket == null) {
            log(tag + ": socket=null, skip bindSocket");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && DlnaNet.wifiNetwork != null) {
            try {
                DlnaNet.wifiNetwork.bindSocket(socket);
                log(tag + ": Network.bindSocket(wifi) OK");
            } catch (Throwable t) {
                log(tag + ": Network.bindSocket(wifi) FAILED");
                log(t);
            }
        } else {
            log(tag + ": bindSocket skipped (wifiNetwork=" + (DlnaNet.wifiNetwork != null) + " SDK>=M=" + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) + ")");
        }
        if (DlnaNet.wifiInterface != null) {
            try {
                ((MulticastSocket) socket).setNetworkInterface(DlnaNet.wifiInterface);
                log(tag + ": setNetworkInterface(" + DlnaNet.wifiInterface.getDisplayName() + ") OK");
            } catch (Throwable t) {
                log(tag + ": setNetworkInterface FAILED");
                log(t);
            }
        }
    }

    /**
     * 决定性探针：完全绕开 jupnp，自己用 WiFi 网络创建一个 MulticastSocket 发 SSDP M-SEARCH 并等回包。
     * 用于一锤定音判断——在本机/本 ROM 上，「进程绑 WiFi + Network.bindSocket(MulticastSocket)」这套修法
     * 到底让组播通不通。通 -> 说明 jupnp 子类化修法成立；不通 -> 说明 ROM 级拦了 app 组播(需 appops/NsdManager)。
     */
    public static void probeMulticast(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            log("probe: SDK<23, skip");
            return;
        }
        if (!DlnaNet.ready()) {
            log("probe: DlnaNet not ready (wifiNetwork/Interface null) -> 无法独立判定");
            return;
        }
        log("probe: NEARBY_WIFI_DEVICES granted=" + isNearbyGranted(context));
        new Thread(() -> {
            boolean sentOk = false;
            try {
                InetAddress group = InetAddress.getByName("239.255.255.250");
                MulticastSocket s = new MulticastSocket(null);
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(0));
                DlnaNet.wifiNetwork.bindSocket(s);
                if (DlnaNet.wifiInterface != null) s.setNetworkInterface(DlnaNet.wifiInterface);
                s.setTimeToLive(4);
                s.joinGroup(new InetSocketAddress(group, 1900), DlnaNet.wifiInterface);
                log("probe: socket ready, joined group on " + DlnaNet.wifiInterface.getDisplayName());

                String msearch = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: ssdp:all\r\n\r\n";
                byte[] data = msearch.getBytes(StandardCharsets.UTF_8);
                s.send(new DatagramPacket(data, data.length, group, 1900));
                sentOk = true;
                log("probe: M-SEARCH sent OK (group=239.255.255.250:1900)");

                s.setSoTimeout(4000);
                byte[] buf = new byte[4096];
                int got = 0;
                long t0 = System.currentTimeMillis();
                while (System.currentTimeMillis() - t0 < 4000) {
                    try {
                        DatagramPacket rp = new DatagramPacket(buf, buf.length);
                        s.receive(rp);
                        got++;
                        String head = new String(buf, 0, Math.min(rp.getLength(), 80), StandardCharsets.UTF_8).replace("\r", " ").replace("\n", " ");
                        log("probe: GOT RESPONSE #" + got + " from " + rp.getAddress() + " :: " + head);
                    } catch (SocketTimeoutException e) {
                        break;
                    }
                }
                s.close();
                if (got > 0) {
                    log("probe VERDICT: sent=OK gotResponses=" + got + " -> LINK OK, jupnp subclass fix should work");
                } else {
                    log("probe VERDICT: sent=OK gotResponses=0 -> no reply in 4s (no DLNA device, or router AP-isolation/IGMP blocks, or ROM still filters)");
                }
            } catch (Throwable t) {
                boolean eperm = (t instanceof IOException) && t.getMessage() != null && t.getMessage().contains("EPERM");
                if (eperm) {
                    probeBlockedByEperm = true;
                    log("probe FAILED: EPERM on SSDP multicast SEND -> ROM/ColorOS blocks app multicast send");
                } else {
                    log("probe FAILED" + (sentOk ? " (sent ok, but receive/other exc)" : ""));
                }
                log(t);
            }
        }, "dlna-probe").start();
    }

    private static void mem(String text) {
        synchronized (MEM) {
            MEM.add(text);
            if (MEM.size() > MEM_CAP) MEM.remove(0);
        }
    }

    private static void append(String text) {
        if (sFile == null) return;
        try {
            if (sFile.exists() && sFile.length() > MAX_BYTES) {
                RandomAccessFile raf = new RandomAccessFile(sFile, "r");
                long skip = sFile.length() - MAX_BYTES / 2;
                if (skip < 0) skip = 0;
                raf.seek(skip);
                byte[] b = new byte[(int) (sFile.length() - skip)];
                raf.readFully(b);
                raf.close();
                try (FileWriter w = new FileWriter(sFile, false)) {
                    w.write(new String(b));
                }
            }
            try (FileWriter w = new FileWriter(sFile, true)) {
                w.write(text);
                w.write("\n");
            }
        } catch (Throwable ignore) {
        }
    }
}
