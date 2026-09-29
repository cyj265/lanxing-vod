package com.fongmi.android.tv.dlna;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.RandomAccessFile;
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
