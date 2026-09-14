package com.fongmi.android.tv.service;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;
import androidx.palette.graphics.Palette;

import com.fongmi.android.tv.App;
import com.cyj265.lanxingvod.R;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.receiver.ActionReceiver;
import com.fongmi.android.tv.utils.Notify;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.Objects;

public class PlaybackService extends Service {

    private static Players player;

    public static void start(Players player) {
        ContextCompat.startForegroundService(App.get(), new Intent(App.get(), PlaybackService.class));
        PlaybackService.player = player;
    }

    public static void stop() {
        App.get().stopService(new Intent(App.get(), PlaybackService.class));
    }

    private boolean isNull() {
        return Objects.isNull(player) || Objects.isNull(player.getSession());
    }

    private boolean nonNull() {
        return Objects.nonNull(player) && Objects.nonNull(player.getSession());
    }

    private NotificationManagerCompat getManager() {
        return NotificationManagerCompat.from(this);
    }

    private NotificationCompat.Action buildNotificationAction(@DrawableRes int icon, @StringRes int title, String action) {
        return new NotificationCompat.Action(icon, getString(title), ActionReceiver.getPendingIntent(this, action));
    }

    private NotificationCompat.Action getPlayPauseAction() {
        if (nonNull() && player.isPlaying()) return buildNotificationAction(androidx.media3.ui.R.drawable.exo_icon_pause, androidx.media3.ui.R.string.exo_controls_pause_description, ActionEvent.PAUSE);
        return buildNotificationAction(androidx.media3.ui.R.drawable.exo_icon_play, androidx.media3.ui.R.string.exo_controls_play_description, ActionEvent.PLAY);
    }

    private MediaMetadataCompat getMetadata() {
        return isNull() ? null : player.getSession().getController().getMetadata();
    }

    private String getTitle() {
        return getMetadata() == null || getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE).isEmpty() ? null : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE);
    }

    private String getArtist() {
        return getMetadata() == null || getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST).isEmpty() ? null : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
    }

    private Bitmap getArt() {
        return getMetadata() == null ? null : getMetadata().getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
    }

    private void addAction(NotificationCompat.Builder builder) {
        builder.addAction(buildNotificationAction(androidx.media3.ui.R.drawable.exo_icon_previous, androidx.media3.ui.R.string.exo_controls_previous_description, ActionEvent.PREV));
        builder.addAction(getPlayPauseAction());
        builder.addAction(buildNotificationAction(androidx.media3.ui.R.drawable.exo_icon_next, androidx.media3.ui.R.string.exo_controls_next_description, ActionEvent.NEXT));
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Notify.DEFAULT);
        builder.setOngoing(false);
        builder.setColorized(true);
        builder.setOnlyAlertOnce(true);
        builder.setContentText(getArtist());
        builder.setContentTitle(getTitle());
        builder.setSmallIcon(R.drawable.ic_notification);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setDeleteIntent(ActionReceiver.getPendingIntent(this, ActionEvent.STOP));
        if (nonNull()) builder.setContentIntent(player.getSession().getController().getSessionActivity());
        if (nonNull()) builder.setStyle(new MediaStyle().setMediaSession(player.getSession().getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        if (getArt() != null) setIconColor(builder, getArt());
        addAction(builder);
        return builder.build();
    }

    private void setIconColor(NotificationCompat.Builder builder, Bitmap art) {
        builder.setLargeIcon(art);
        Palette palette = Palette.from(art).generate();
        int white = ContextCompat.getColor(this, R.color.white);
        builder.setColor(palette.getMutedColor(palette.getVibrantColor(white)));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (event.isUpdate()) Notify.show(buildNotification());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
    }

    @Override
    @SuppressLint("ForegroundServiceType")
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (nonNull()) MediaButtonReceiver.handleIntent(player.getSession(), intent);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, Notify.ID, buildNotification(), type);
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        getManager().cancel(Notify.ID);
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isRunning() {
        ActivityManager manager = (ActivityManager) App.get().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
        if (services == null || services.isEmpty()) return false;
        String clz = PlaybackService.class.getName();
        return services.stream().anyMatch(serviceInfo -> clz.equals(serviceInfo.service.getClassName()));
    }
}
