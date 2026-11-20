package com.example.dynamicisland;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
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
    private static volatile IslandNotificationListener instance;
    private static volatile String latestTitle = "";
    private static volatile String latestText = "";
    private static volatile String latestPackage = "";
    private static volatile String latestNotificationKey = "";
    private static volatile PendingIntent latestContentIntent;
    private static volatile PendingIntent latestReplyIntent;
    private static volatile RemoteInput[] latestRemoteInputs;
    private static volatile String latestReplyResultKey = "";
    private static volatile String mediaTitle = "";
    private static volatile String mediaArtist = "";
    private static volatile boolean mediaPlaying = false;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        refreshMedia();
        notifyIsland();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        if (notification == null || (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;

        Bundle extras = notification.extras;
        if (extras != null) {
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (body == null) body = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (title != null || body != null) {
                latestTitle = title == null ? "Thông báo mới" : title.toString();
                latestText = body == null ? "" : body.toString();
                latestPackage = sbn.getPackageName();
                latestNotificationKey = sbn.getKey();
                latestContentIntent = notification.contentIntent;
                findReplyAction(notification);
            }
        }
        refreshMedia();
        notifyIsland();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && sbn.getKey().equals(latestNotificationKey)) {
            clearLatestNotification();
        }
        refreshMedia();
        notifyIsland();
    }

    private void findReplyAction(Notification notification) {
        latestReplyIntent = null;
        latestRemoteInputs = null;
        latestReplyResultKey = "";
        Notification.Action[] actions = notification.actions;
        if (actions == null) return;
        for (Notification.Action action : actions) {
            RemoteInput[] inputs = action.getRemoteInputs();
            if (inputs == null || inputs.length == 0 || action.actionIntent == null) continue;
            for (RemoteInput input : inputs) {
                if (input != null && input.getAllowFreeFormInput()) {
                    latestReplyIntent = action.actionIntent;
                    latestRemoteInputs = inputs;
                    latestReplyResultKey = input.getResultKey();
                    return;
                }
            }
        }
    }

    private static void clearLatestNotification() {
        latestTitle = "";
        latestText = "";
        latestPackage = "";
        latestNotificationKey = "";
        latestContentIntent = null;
        latestReplyIntent = null;
        latestRemoteInputs = null;
        latestReplyResultKey = "";
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
    public static String getLatestPackage() { return latestPackage; }
    public static String getMediaTitle() { return mediaTitle; }
    public static String getMediaArtist() { return mediaArtist; }
    public static boolean isMediaPlaying() { return mediaPlaying; }
    public static boolean hasReplyAction() { return latestReplyIntent != null && latestRemoteInputs != null; }
    public static boolean hasLatestNotification() { return !latestNotificationKey.isEmpty(); }

    public static boolean replyToLatest(Context context, String message) {
        PendingIntent intent = latestReplyIntent;
        RemoteInput[] inputs = latestRemoteInputs;
        String resultKey = latestReplyResultKey;
        if (intent == null || inputs == null || resultKey.isEmpty() || message == null || message.trim().isEmpty()) {
            return false;
        }
        try {
            Intent fillIn = new Intent();
            Bundle results = new Bundle();
            results.putCharSequence(resultKey, message.trim());
            RemoteInput.addResultsToIntent(inputs, fillIn, results);
            intent.send(context, 0, fillIn);
            return true;
        } catch (PendingIntent.CanceledException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean dismissLatest() {
        IslandNotificationListener service = instance;
        String key = latestNotificationKey;
        if (service == null || key.isEmpty()) return false;
        try {
            service.cancelNotification(key);
            clearLatestNotification();
            service.notifyIsland();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void openLatest() {
        PendingIntent intent = latestContentIntent;
        if (intent == null) return;
        try { intent.send(); } catch (PendingIntent.CanceledException ignored) { }
    }

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
        } catch (SecurityException ignored) { }
    }

    public static void skipToPrevious(Context context) { sendTransportCommand(context, true); }
    public static void skipToNext(Context context) { sendTransportCommand(context, false); }

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
        } catch (SecurityException ignored) { }
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
        } catch (SecurityException ignored) { }
    }

    private void notifyIsland() {
        Intent intent = new Intent(ACTION_DATA_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
