package com.fongmi.android.tv.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.fongmi.android.tv.service.PlaybackService;

public class ActionReceiver extends BroadcastReceiver {

    public static PendingIntent getPendingIntent(Context context, String action) {
        return PendingIntent.getBroadcast(context, 0, new Intent(action).setPackage(context.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!PlaybackService.isRunning() || intent.getAction() == null) return;
        Intent serviceIntent = new Intent(context, PlaybackService.class);
        serviceIntent.setAction(intent.getAction());
        // 广播触发时 App 可能在后台，Android 12+ 禁止后台启动前台服务（Bugly #7021）
        try {
            context.startService(serviceIntent);
        } catch (RuntimeException e) {
            // 只吞后台启动前台服务的系统限制（API31+，按类名判断避免 minSdk 24 直接引用）
            boolean denied = e.getClass().getName().contains("ForegroundServiceStartNotAllowedException");
            if (!denied) throw e;
        }
    }
}
