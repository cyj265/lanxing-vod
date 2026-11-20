package com.fongmi.android.tv.service;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

/**
 * 用 ViewModel 持有播放服务的绑定状态，使其跨越 Activity 重建（旋转/切台）存活。
 * 移植自上游 FongMi/TV 4cf3a25b0：重建后 attach 可立即拿回已连接的 binder，
 * 消除 PlaybackActivity.mService 在连接建立前的 null 窗口（Bugly #2038 / #4034）。
 */
public final class PlaybackServiceConnection extends AndroidViewModel implements ServiceConnection {

    private ServiceConnection listener;
    private ConnectedService connection;
    private boolean bound;

    public PlaybackServiceConnection(@NonNull Application application) {
        super(application);
    }

    public void attach(ServiceConnection listener) {
        this.listener = listener;
        if (connection != null) {
            listener.onServiceConnected(connection.name(), connection.binder());
        } else if (!bound) {
            Application application = getApplication();
            // Android 12+ 后台启动前台服务会被系统拒绝：bindService 仍会拉起服务，故吞掉该异常（Bugly #7021）
            try {
                application.startService(new Intent(application, PlaybackService.class));
            } catch (RuntimeException e) {
                // 只吞后台启动前台服务的系统限制（API31+，按类名判断避免 minSdk 24 直接引用）
                boolean denied = e.getClass().getName().contains("ForegroundServiceStartNotAllowedException");
                if (!denied) throw e;
            }
            Intent intent = new Intent(application, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION);
            application.bindService(intent, this, Context.BIND_AUTO_CREATE);
            bound = true;
        }
    }

    public void detach(ServiceConnection listener) {
        if (this.listener == listener) this.listener = null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        connection = new ConnectedService(name, binder);
        if (listener != null) listener.onServiceConnected(name, binder);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        connection = null;
        if (listener != null) listener.onServiceDisconnected(name);
    }

    @Override
    protected void onCleared() {
        listener = null;
        connection = null;
        if (bound) {
            getApplication().unbindService(this);
            bound = false;
        }
    }

    private record ConnectedService(ComponentName name, IBinder binder) {}
}
