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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Build;
import android.os.Parcelable;
import android.app.Person;
import android.graphics.drawable.Icon;
import java.util.ArrayList;
import java.util.Collections;
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
    private static volatile String latestSender = "";
    private static volatile Icon latestSenderIcon;
    private static volatile boolean latestIsCall = false;
    private static volatile PendingIntent answerCallIntent;
    private static volatile PendingIntent declineCallIntent;
    private static volatile long callAcceptedAt = 0L;
    private static final ArrayList<NotificationItem> notificationItems = new ArrayList<>();
    private static int selectedIndex = 0;
    private static volatile String mediaTitle = "";
    private static volatile String mediaArtist = "";
    private static volatile boolean mediaPlaying = false;
    private static volatile Bitmap mediaArtwork;

    private static final class NotificationItem {
        String key, title, text, packageName, sender;
        Icon senderIcon;
        PendingIntent contentIntent, replyIntent, answerIntent, declineIntent;
        RemoteInput[] remoteInputs;
        String replyResultKey = "";
        boolean isCall;
        long postedAt;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        rebuildNotificationQueue();
        refreshMedia();
        notifyIsland();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        super.onListenerDisconnected();
    }

    private void rebuildNotificationQueue() {
        synchronized (notificationItems) {
            notificationItems.clear();
            try {
                StatusBarNotification[] active = getActiveNotifications();
                if (active != null) {
                    for (StatusBarNotification sbn : active) {
                        NotificationItem item = makeItem(sbn);
                        if (item != null) notificationItems.add(item);
                    }
                }
                Collections.sort(notificationItems, (a, b) -> Long.compare(b.postedAt, a.postedAt));
                if (notificationItems.size() > 12) notificationItems.subList(12, notificationItems.size()).clear();
                selectedIndex = 0;
                applySelectedItemLocked();
            } catch (RuntimeException ignored) { }
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        NotificationItem item = makeItem(sbn);
        if (item != null) {
            synchronized (notificationItems) {
                for (int i = notificationItems.size() - 1; i >= 0; i--) {
                    if (notificationItems.get(i).key.equals(item.key)) notificationItems.remove(i);
                }
                notificationItems.add(0, item);
                if (notificationItems.size() > 12) notificationItems.remove(notificationItems.size() - 1);
                selectedIndex = 0;
                applySelectedItemLocked();
            }
        }
        refreshMedia();
        notifyIsland();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) {
            synchronized (notificationItems) {
                for (int i = notificationItems.size() - 1; i >= 0; i--) {
                    if (notificationItems.get(i).key.equals(sbn.getKey())) notificationItems.remove(i);
                }
                if (selectedIndex >= notificationItems.size()) selectedIndex = Math.max(0, notificationItems.size() - 1);
                applySelectedItemLocked();
            }
        }
        refreshMedia();
        notifyIsland();
    }

    private NotificationItem makeItem(StatusBarNotification sbn) {
        if (sbn == null) return null;
        Notification notification = sbn.getNotification();
        if (notification == null) return null;
        boolean callCategory = Notification.CATEGORY_CALL.equals(notification.category);
        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0 && !callCategory) return null;
        Bundle extras = notification.extras;
        if (extras == null) return null;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (body == null) body = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && body == null && !callCategory) return null;

        NotificationItem item = new NotificationItem();
        item.key = sbn.getKey();
        item.title = title == null ? "Thông báo mới" : title.toString();
        item.text = body == null ? "" : body.toString();
        item.packageName = sbn.getPackageName();
        item.contentIntent = notification.contentIntent;
        item.postedAt = sbn.getPostTime();
        CharSequence conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        item.sender = extractSender(extras, item);
        if ((item.sender == null || item.sender.trim().isEmpty()) && Build.VERSION.SDK_INT >= 31) {
            try {
                Object callPersonValue = extras.get(Notification.EXTRA_CALL_PERSON);
                if (callPersonValue instanceof Person) {
                    Person callPerson = (Person) callPersonValue;
                    item.sender = callPerson.getName() == null ? "" : callPerson.getName().toString();
                    item.senderIcon = callPerson.getIcon();
                }
            } catch (RuntimeException ignored) { }
        }
        if (item.sender == null || item.sender.trim().isEmpty()) {
            item.sender = conversation == null ? item.title : conversation.toString();
        }
        item.isCall = callCategory;
        if (Build.VERSION.SDK_INT >= 31 && callCategory) {
            Object answer = extras.get(Notification.EXTRA_ANSWER_INTENT);
            Object decline = extras.get(Notification.EXTRA_DECLINE_INTENT);
            Object hangUp = extras.get(Notification.EXTRA_HANG_UP_INTENT);
            if (answer instanceof PendingIntent) item.answerIntent = (PendingIntent) answer;
            if (decline instanceof PendingIntent) item.declineIntent = (PendingIntent) decline;
            else if (hangUp instanceof PendingIntent) item.declineIntent = (PendingIntent) hangUp;
        }
        findActions(notification, item);
        return item;
    }

    private String extractSender(Bundle extras, NotificationItem item) {
        try {
            Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages != null) {
                for (int i = messages.length - 1; i >= 0; i--) {
                    if (!(messages[i] instanceof Bundle)) continue;
                    Bundle message = (Bundle) messages[i];
                    CharSequence name = message.getCharSequence("sender");
                    if (name != null && name.length() > 0) return name.toString();
                    if (Build.VERSION.SDK_INT >= 28) {
                        Parcelable personValue = message.getParcelable("sender_person");
                        if (personValue instanceof Person) {
                            Person person = (Person) personValue;
                            item.senderIcon = person.getIcon();
                            CharSequence personName = person.getName();
                            if (personName != null) return personName.toString();
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) { }
        return "";
    }

    private void findActions(Notification notification, NotificationItem item) {
        Notification.Action[] actions = notification.actions;
        if (actions == null) return;
        for (Notification.Action action : actions) {
            if (action == null || action.actionIntent == null) continue;
            String label = action.title == null ? "" : action.title.toString().toLowerCase(java.util.Locale.ROOT);
            if (label.contains("answer") || label.contains("accept") || label.contains("nghe")
                    || label.contains("nhấc") || label.contains("trả lời")) item.answerIntent = action.actionIntent;
            if (label.contains("decline") || label.contains("reject") || label.contains("từ chối")
                    || label.contains("gác") || label.contains("end call")) item.declineIntent = action.actionIntent;
            RemoteInput[] inputs = action.getRemoteInputs();
            if (inputs == null || inputs.length == 0) continue;
            for (RemoteInput input : inputs) {
                if (input != null && input.getAllowFreeFormInput()) {
                    item.replyIntent = action.actionIntent;
                    item.remoteInputs = inputs;
                    item.replyResultKey = input.getResultKey();
                    break;
                }
            }
        }
    }

    private static void applySelectedItemLocked() {
        if (notificationItems.isEmpty()) {
            clearLatestNotification();
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, notificationItems.size() - 1));
        NotificationItem item = notificationItems.get(selectedIndex);
        latestTitle = item.title;
        latestText = item.text;
        latestPackage = item.packageName;
        latestNotificationKey = item.key;
        latestSender = item.sender;
        latestSenderIcon = item.senderIcon;
        latestContentIntent = item.contentIntent;
        latestReplyIntent = item.replyIntent;
        latestRemoteInputs = item.remoteInputs;
        latestReplyResultKey = item.replyResultKey;
        latestIsCall = item.isCall;
        answerCallIntent = item.answerIntent;
        declineCallIntent = item.declineIntent;
    }

    private static void clearLatestNotification() {
        latestTitle = "";
        latestText = "";
        latestPackage = "";
        latestNotificationKey = "";
        latestSender = "";
        latestSenderIcon = null;
        latestContentIntent = null;
        latestReplyIntent = null;
        latestRemoteInputs = null;
        latestReplyResultKey = "";
        latestIsCall = false;
        if (notificationItems.isEmpty()) callAcceptedAt = 0L;
        answerCallIntent = null;
        declineCallIntent = null;
    }

    public static void cycleNotification(int direction) {
        synchronized (notificationItems) {
            if (notificationItems.size() < 2) return;
            selectedIndex = (selectedIndex + direction + notificationItems.size()) % notificationItems.size();
            applySelectedItemLocked();
        }
    }

    public static int getNotificationCount() {
        synchronized (notificationItems) { return notificationItems.size(); }
    }

    public static int getSelectedNotificationIndex() {
        synchronized (notificationItems) { return notificationItems.isEmpty() ? 0 : selectedIndex; }
    }

    public static String getLatestKey() { return latestNotificationKey; }
    public static String getLatestSender() { return latestSender; }
    public static Icon getLatestSenderIcon() { return latestSenderIcon; }
    public static boolean isIncomingCall() { return latestIsCall; }
    public static boolean hasAnswerCallAction() { return answerCallIntent != null; }
    public static boolean hasDeclineCallAction() { return declineCallIntent != null; }

    public static boolean sendCallAction(boolean answer) {
        PendingIntent action = answer ? answerCallIntent : declineCallIntent;
        if (action == null) return false;
        try {
            action.send();
            if (answer) callAcceptedAt = System.currentTimeMillis();
            else callAcceptedAt = 0L;
            return true;
        } catch (PendingIntent.CanceledException | RuntimeException ignored) { return false; }
    }

    public static long getCallElapsedSeconds() {
        return callAcceptedAt <= 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - callAcceptedAt) / 1000L);
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
    public static Bitmap getMediaArtwork() { return mediaArtwork; }

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
            synchronized (notificationItems) {
                for (int i = notificationItems.size() - 1; i >= 0; i--) {
                    if (notificationItems.get(i).key.equals(key)) notificationItems.remove(i);
                }
                selectedIndex = 0;
                applySelectedItemLocked();
            }
            service.notifyIsland();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static int dismissAllTrackedNotifications() {
        IslandNotificationListener service = instance;
        if (service == null) return 0;
        ArrayList<String> keys = new ArrayList<>();
        try {
            StatusBarNotification[] active = service.getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    if (sbn == null || service.getPackageName().equals(sbn.getPackageName())) continue;
                    NotificationItem item = service.makeItem(sbn);
                    if (item != null && item.key != null && !item.key.isEmpty()) keys.add(item.key);
                }
            }
        } catch (RuntimeException ignored) { }
        if (keys.isEmpty()) {
            synchronized (notificationItems) {
                for (NotificationItem item : notificationItems) {
                    if (item != null && item.key != null && !item.key.isEmpty()) keys.add(item.key);
                }
            }
        }
        int removed = 0;
        for (String key : keys) {
            try {
                service.cancelNotification(key);
                removed++;
            } catch (RuntimeException ignored) { }
        }
        synchronized (notificationItems) {
            notificationItems.clear();
            selectedIndex = 0;
            applySelectedItemLocked();
        }
        service.notifyIsland();
        return removed;
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
        mediaArtwork = null;
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
                Bitmap art = controller.getMetadata().getBitmap("android.media.metadata.ART");
                if (art == null) art = controller.getMetadata().getBitmap("android.media.metadata.ALBUM_ART");
                mediaArtwork = art;
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
