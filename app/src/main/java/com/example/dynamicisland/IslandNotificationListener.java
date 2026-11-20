package com.example.dynamicisland;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.List;

public class IslandNotificationListener extends NotificationListenerService {
    public static final String ACTION_DATA_CHANGED = "com.example.dynamicisland.DATA_CHANGED";
    private static volatile String latestTitle = "";
    private static volatile String latestText = "";
    private static volatile String mediaTitle = "";
    private static volatile String mediaArtist = "";
    private static volatile boolean mediaPlaying = false;
    private static volatile String latestNotificationKey = "";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        refreshMedia();
        notifyIsland();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras != null && (notification.flags & Notification.FLAG_ONGOING_EVENT) == 0) {
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (body == null) body = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (title != null || body != null) {
                latestTitle = title == null ? "Thông báo mới" : title.toString();
                latestText = body == null ? "" : body.toString();
                latestNotificationKey = sbn.getKey();
            }
        }
        refreshMedia();
        notifyIsland();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && sbn.getKey().equals(latestNotificationKey)) {
            latestTitle = "";
            latestText = "";
            latestNotificationKey = "";
        }
        refreshMedia();
        notifyIsland();
    }

    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName component = new ComponentName(context, IslandNotificationListener.class);
        for (String item : enabled.split(":")) {
            ComponentName parsed = ComponentName.unflattenFromString(item);
            if (component.equals(parsed)) return true;
        }
        return false;
    }

    public static String getLatestTitle() { return latestTitle; }
    public static String getLatestText() { return latestText; }
    public static String getMediaTitle() { return mediaTitle; }
    public static String getMediaArtist() { return mediaArtist; }
    public static boolean isMediaPlaying() { return mediaPlaying; }

    public static void togglePlayback(Context context) {
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(context, IslandNotificationListener.class));
            if (sessions == null || sessions.isEmpty()) return;
            MediaController controller = sessions.get(0);
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                controller.getTransportControls().pause();
                mediaPlaying = false;
            } else {
                controller.getTransportControls().play();
                mediaPlaying = true;
            }
        } catch (SecurityException ignored) {
            // The user has not granted notification access yet.
        }
    }


    public static void skipToPrevious(Context context) {
        sendTransportCommand(context, true);
    }

    public static void skipToNext(Context context) {
        sendTransportCommand(context, false);
    }

    private static void sendTransportCommand(Context context, boolean previous) {
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(context, IslandNotificationListener.class));
            if (sessions == null || sessions.isEmpty()) return;
            MediaController.TransportControls controls = sessions.get(0).getTransportControls();
            if (previous) controls.skipToPrevious(); else controls.skipToNext();
        } catch (SecurityException ignored) {
            // Người dùng chưa cấp quyền truy cập thông báo.
        }
    }

    private void refreshMedia() {
        mediaTitle = "";
        mediaArtist = "";
        mediaPlaying = false;
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(this, IslandNotificationListener.class));
            if (sessions == null || sessions.isEmpty()) return;
            MediaController controller = sessions.get(0);
            if (controller.getMetadata() != null) {
                CharSequence title = controller.getMetadata().getText("android.media.metadata.TITLE");
                CharSequence artist = controller.getMetadata().getText("android.media.metadata.ARTIST");
                mediaTitle = title == null ? "Đang phát nhạc" : title.toString();
                mediaArtist = artist == null ? "" : artist.toString();
            }
            PlaybackState state = controller.getPlaybackState();
            mediaPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        } catch (SecurityException ignored) {
            // Notification access is optional.
        }
    }

    private void notifyIsland() {
        Intent intent = new Intent(ACTION_DATA_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
