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
import java.util.Date;
import java.util.Locale;

/**
 * 投屏诊断：把关键事件写到 app 私有目录的文件（/sdcard/Android/data/com.fongmi.android.tv/files/dlna_diag.log），
 * 避免 ColorOS 上 logcat 被系统日志淹没、且命令本身被跨屏剪贴板回显污染的问题。
 * 用户只需 adb pull 该文件即可，无需 logcat 过滤。
 */
public final class DlnaDiag {

    private static final String TAG = "DLNACast";
    private static final String NAME = "dlna_diag.log";
    private static final long MAX_BYTES = 80_000;
    private static File sFile;
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
    }

    public static synchronized void log(Throwable t) {
        StringBuilder sb = new StringBuilder(SDF.format(new Date())).append("  EXC ")
                .append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
        StackTraceElement[] st = t.getStackTrace();
        int n = Math.min(st.length, 10);
        for (int i = 0; i < n; i++) sb.append("    at ").append(st[i]).append("\n");
        Log.e(TAG, "EXC", t);
        append(sb.toString());
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
            log("net: active transport wifi=" + wifi + " cellular=" + cellular + " ethernet=" + eth);
        } catch (Throwable t) {
            log(t);
        }
    }

    public static File file(Context context) {
        init(context);
        return sFile;
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
