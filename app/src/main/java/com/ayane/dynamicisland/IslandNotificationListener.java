package com.ayane.dynamicisland;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Local-only notification bridge for Ayane.
 * It stores only the active notification snapshot in memory and never uploads it.
 */
public class IslandNotificationListener extends NotificationListenerService {
    public static final String ACTION_DATA_CHANGED = "com.ayane.dynamicisland.DATA_CHANGED";
    private static final int MAX_ITEMS = 30;
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, NotificationItem> ITEMS = new LinkedHashMap<>();

    private static volatile IslandNotificationListener instance;
    private static volatile String selectedKey = "";
    private static volatile String mediaTitle = "";
    private static volatile String mediaArtist = "";
    private static volatile String mediaPackage = "";
    private static volatile boolean mediaPlaying = false;
    private static volatile Bitmap mediaArtwork;
    private static volatile long mediaDuration = 0L;
    private static volatile long mediaPosition = 0L;
    private static volatile long mediaPositionUpdatedAt = 0L;
    private static volatile float mediaPlaybackSpeed = 0f;

    public static final class NotificationItem {
        public final String key;
        public final String packageName;
        public final String appName;
        public final String title;
        public final String text;
        public final String senderName;
        public final long postTime;
        public final Icon avatarIcon;
        public final PendingIntent contentIntent;
        public final PendingIntent replyIntent;
        public final RemoteInput[] remoteInputs;
        public final String replyResultKey;
        public final boolean ongoing;
        public final boolean autoCancel;
        public final String category;
        public final boolean incomingCall;
        public final boolean ongoingCall;
        public final boolean timerOrAlarm;
        public final boolean showChronometer;
        public final boolean chronometerCountDown;
        public final long chronometerWhen;
        public final PendingIntent answerIntent;
        public final PendingIntent declineIntent;
        public final PendingIntent hangUpIntent;
        public final PendingIntent timerActionIntent;
        public final String timerActionTitle;

        NotificationItem(String key, String packageName, String appName, String title,
                         String text, String senderName, long postTime, Icon avatarIcon,
                         PendingIntent contentIntent, PendingIntent replyIntent,
                         RemoteInput[] remoteInputs, String replyResultKey, boolean ongoing,
                         boolean autoCancel, String category, boolean incomingCall,
                         boolean ongoingCall, boolean timerOrAlarm, boolean showChronometer,
                         boolean chronometerCountDown, long chronometerWhen,
                         PendingIntent answerIntent, PendingIntent declineIntent,
                         PendingIntent hangUpIntent, PendingIntent timerActionIntent,
                         String timerActionTitle) {
            this.key = key;
            this.packageName = packageName;
            this.appName = appName;
            this.title = title;
            this.text = text;
            this.senderName = senderName;
            this.postTime = postTime;
            this.avatarIcon = avatarIcon;
            this.contentIntent = contentIntent;
            this.replyIntent = replyIntent;
            this.remoteInputs = remoteInputs;
            this.replyResultKey = replyResultKey;
            this.ongoing = ongoing;
            this.autoCancel = autoCancel;
            this.category = category;
            this.incomingCall = incomingCall;
            this.ongoingCall = ongoingCall;
            this.timerOrAlarm = timerOrAlarm;
            this.showChronometer = showChronometer;
            this.chronometerCountDown = chronometerCountDown;
            this.chronometerWhen = chronometerWhen;
            this.answerIntent = answerIntent;
            this.declineIntent = declineIntent;
            this.hangUpIntent = hangUpIntent;
            this.timerActionIntent = timerActionIntent;
            this.timerActionTitle = timerActionTitle;
        }

        public boolean canReply() {
            return replyIntent != null && remoteInputs != null && replyResultKey != null
                    && !replyResultKey.isEmpty();
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) putNotification(sbn);
            }
        } catch (RuntimeException ignored) { }
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
        putNotification(sbn);
        refreshMedia();
        notifyIsland();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) {
            synchronized (LOCK) {
                ITEMS.remove(sbn.getKey());
                if (sbn.getKey().equals(selectedKey)) selectedKey = newestKeyLocked();
            }
        }
        refreshMedia();
        notifyIsland();
    }

    private void putNotification(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        Notification notification = sbn.getNotification();
        String pkg = sbn.getPackageName();
        if (getPackageName().equals(pkg)) return;

        SharedPreferences prefs = getSharedPreferences("island_settings", MODE_PRIVATE);
        Set<String> blocked = prefs.getStringSet("blocked_apps", Collections.<String>emptySet());
        boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        if (!prefs.getBoolean("show_notifications", true)
                || (blocked != null && blocked.contains(pkg))
                || (ongoing && !prefs.getBoolean("show_ongoing_notifications", true))) {
            synchronized (LOCK) {
                ITEMS.remove(sbn.getKey());
                if (sbn.getKey().equals(selectedKey)) selectedKey = newestKeyLocked();
            }
            return;
        }

        Bundle extras = notification.extras;
        if (extras == null) return;
        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence bodyCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (bodyCs == null) bodyCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (bodyCs == null) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null && lines.length > 0) bodyCs = lines[lines.length - 1];
        }

        SenderInfo sender = readLatestSender(extras);
        String title = clean(titleCs == null ? "Thông báo mới" : titleCs.toString());
        String body = clean(bodyCs == null ? "" : bodyCs.toString());
        if (body.isEmpty() && sender.message != null) body = clean(sender.message);
        String senderName = clean(sender.name == null ? "" : sender.name);
        Icon callerAvatar = sender.icon;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                android.app.Person callPerson = extras.getParcelable(Notification.EXTRA_CALL_PERSON);
                if (callPerson != null) {
                    if (senderName.isEmpty() && callPerson.getName() != null) {
                        senderName = clean(callPerson.getName().toString());
                    }
                    if (callerAvatar == null) callerAvatar = callPerson.getIcon();
                }
            } catch (RuntimeException ignored) { }
        }
        String appName = resolveAppName(pkg);

        PendingIntent replyIntent = null;
        PendingIntent answerIntent = null;
        PendingIntent declineIntent = null;
        PendingIntent hangUpIntent = null;
        PendingIntent timerActionIntent = null;
        String timerActionTitle = "";
        RemoteInput[] remoteInputs = null;
        String replyResultKey = "";
        Notification.Action[] actions = notification.actions;
        if (actions != null) {
            for (Notification.Action action : actions) {
                if (action == null || action.actionIntent == null) continue;
                String actionTitle = action.title == null ? "" : action.title.toString().trim();
                String lower = actionTitle.toLowerCase(Locale.ROOT);
                int semantic = Build.VERSION.SDK_INT >= 28 ? action.getSemanticAction() : 0;
                if (answerIntent == null && (semantic == Notification.Action.SEMANTIC_ACTION_CALL
                        || lower.contains("answer") || lower.contains("accept")
                        || lower.contains("nghe") || lower.contains("nhận cuộc"))) {
                    answerIntent = action.actionIntent;
                }
                if (declineIntent == null && (lower.contains("decline") || lower.contains("reject")
                        || lower.contains("end call") || lower.contains("từ chối")
                        || lower.contains("gác máy"))) {
                    declineIntent = action.actionIntent;
                }
                if (timerActionIntent == null && (lower.contains("snooze") || lower.contains("dismiss")
                        || lower.contains("stop") || lower.contains("tắt") || lower.contains("hoãn"))) {
                    timerActionIntent = action.actionIntent;
                    timerActionTitle = actionTitle;
                }
                RemoteInput[] inputs = action.getRemoteInputs();
                if (replyIntent == null && inputs != null && inputs.length > 0) {
                    for (RemoteInput input : inputs) {
                        if (input != null && input.getAllowFreeFormInput()) {
                            replyIntent = action.actionIntent;
                            remoteInputs = inputs;
                            replyResultKey = input.getResultKey();
                            break;
                        }
                    }
                }
            }
        }
        String category = notification.category == null ? "" : notification.category;
        if (Build.VERSION.SDK_INT >= 31) {
            if (answerIntent == null) answerIntent = readPendingIntentExtra(extras, Notification.EXTRA_ANSWER_INTENT);
            if (declineIntent == null) declineIntent = readPendingIntentExtra(extras, Notification.EXTRA_DECLINE_INTENT);
            hangUpIntent = readPendingIntentExtra(extras, Notification.EXTRA_HANG_UP_INTENT);
        }
        boolean showChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        boolean chronometerCountDown = Build.VERSION.SDK_INT >= 24
                && extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false);
        long chronometerWhen = notification.when;
        String packageLower = pkg.toLowerCase(Locale.ROOT);
        boolean timerPackage = packageLower.contains("deskclock") || packageLower.contains("clockpackage")
                || packageLower.endsWith(".clock") || packageLower.contains("alarmclock");
        boolean isCallCategory = Notification.CATEGORY_CALL.equals(category);
        int callType = -1;
        if (Build.VERSION.SDK_INT >= 34) {
            callType = extras.getInt(Notification.EXTRA_CALL_TYPE, -1);
        }
        boolean ongoingCall = isCallCategory && (callType == Notification.CallStyle.CALL_TYPE_ONGOING
                || (hangUpIntent != null && answerIntent == null));
        boolean timerOrAlarm = !isCallCategory && (Notification.CATEGORY_ALARM.equals(category)
                || "stopwatch".equals(category) || showChronometer || timerPackage);
        boolean incomingCall = (isCallCategory && !ongoingCall)
                || (answerIntent != null && declineIntent != null);

        Icon avatar = callerAvatar;
        if (avatar == null) {
            try { avatar = notification.getLargeIcon(); } catch (RuntimeException ignored) { }
        }

        NotificationItem item = new NotificationItem(
                sbn.getKey(), pkg, appName, title, body, senderName,
                sbn.getPostTime(), avatar, notification.contentIntent,
                replyIntent, remoteInputs, replyResultKey, ongoing,
                (notification.flags & Notification.FLAG_AUTO_CANCEL) != 0,
                category, incomingCall, ongoingCall, timerOrAlarm, showChronometer,
                chronometerCountDown, chronometerWhen, answerIntent, declineIntent,
                hangUpIntent, timerActionIntent, timerActionTitle);

        synchronized (LOCK) {
            ITEMS.remove(item.key);
            ITEMS.put(item.key, item);
            while (ITEMS.size() > MAX_ITEMS) {
                String oldest = ITEMS.keySet().iterator().next();
                ITEMS.remove(oldest);
            }
            selectedKey = item.key;
        }
    }

    private static final class SenderInfo {
        String name;
        String message;
        Icon icon;
    }

    private SenderInfo readLatestSender(Bundle extras) {
        SenderInfo result = new SenderInfo();
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null || messages.length == 0) return result;
        for (int i = messages.length - 1; i >= 0; i--) {
            Parcelable parcelable = messages[i];
            if (!(parcelable instanceof Bundle)) continue;
            Bundle message = (Bundle) parcelable;
            CharSequence text = message.getCharSequence("text");
            if (text == null || text.length() == 0) continue;
            result.message = text.toString();
            CharSequence sender = message.getCharSequence("sender");
            if (sender != null) result.name = sender.toString();
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    android.app.Person person = message.getParcelable("sender_person");
                    if (person != null) {
                        if (person.getName() != null) result.name = person.getName().toString();
                        result.icon = person.getIcon();
                    }
                } catch (RuntimeException ignored) { }
            }
            break;
        }
        return result;
    }

    private String resolveAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (Exception ignored) {
            return packageName;
        }
    }

    private PendingIntent readPendingIntentExtra(Bundle extras, String key) {
        try {
            Parcelable value = extras.getParcelable(key);
            return value instanceof PendingIntent ? (PendingIntent) value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        if (value == null) return "";
        String result = value.replace('\u0000', ' ').trim();
        return result.length() > 500 ? result.substring(0, 500) : result;
    }

    private static String newestKeyLocked() {
        String value = "";
        for (String key : ITEMS.keySet()) value = key;
        return value;
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

    public static void refreshMediaState() {
        IslandNotificationListener service = instance;
        if (service != null) service.refreshMedia();
    }

    public static void refreshAppFilters(Context context) {
        if (context == null) return;
        Set<String> blocked = context.getSharedPreferences("island_settings", Context.MODE_PRIVATE)
                .getStringSet("blocked_apps", Collections.<String>emptySet());
        synchronized (LOCK) {
            ArrayList<String> remove = new ArrayList<>();
            for (Map.Entry<String, NotificationItem> entry : ITEMS.entrySet()) {
                if (blocked != null && blocked.contains(entry.getValue().packageName)) {
                    remove.add(entry.getKey());
                }
            }
            for (String key : remove) ITEMS.remove(key);
            if (!ITEMS.containsKey(selectedKey)) selectedKey = newestKeyLocked();
        }
        notifyIslandStatic();
    }

    public static List<NotificationItem> getNotificationsSnapshot() {
        synchronized (LOCK) {
            ArrayList<NotificationItem> result = new ArrayList<>(ITEMS.values());
            Collections.reverse(result);
            return result;
        }
    }

    public static NotificationItem getSelectedNotification() {
        synchronized (LOCK) {
            NotificationItem selected = ITEMS.get(selectedKey);
            if (selected != null) return selected;
            String newest = newestKeyLocked();
            selectedKey = newest;
            return ITEMS.get(newest);
        }
    }

    public static int getNotificationCount() {
        synchronized (LOCK) { return ITEMS.size(); }
    }

    public static void moveSelection(int direction) {
        synchronized (LOCK) {
            if (ITEMS.isEmpty()) return;
            ArrayList<String> keys = new ArrayList<>(ITEMS.keySet());
            Collections.reverse(keys);
            int index = keys.indexOf(selectedKey);
            if (index < 0) index = 0;
            index = (index + direction) % keys.size();
            if (index < 0) index += keys.size();
            selectedKey = keys.get(index);
        }
        notifyIslandStatic();
    }

    public static String getLatestTitle() {
        NotificationItem item = getSelectedNotification();
        return item == null ? "" : item.title;
    }

    public static String getLatestText() {
        NotificationItem item = getSelectedNotification();
        return item == null ? "" : item.text;
    }

    public static String getLatestPackage() {
        NotificationItem item = getSelectedNotification();
        return item == null ? "" : item.packageName;
    }

    public static String getLatestSender() {
        NotificationItem item = getSelectedNotification();
        return item == null ? "" : item.senderName;
    }

    public static String getMediaTitle() { return mediaTitle; }
    public static String getMediaArtist() { return mediaArtist; }
    public static String getMediaPackage() { return mediaPackage; }
    public static boolean isMediaPlaying() { return mediaPlaying; }
    public static Bitmap getMediaArtwork() { return mediaArtwork; }
    public static long getMediaDuration() { return mediaDuration; }
    public static long getMediaPosition() {
        long position = mediaPosition;
        if (mediaPlaying && mediaPositionUpdatedAt > 0L) {
            long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - mediaPositionUpdatedAt);
            position += (long) (elapsed * mediaPlaybackSpeed);
        }
        if (mediaDuration > 0L) position = Math.min(position, mediaDuration);
        return Math.max(0L, position);
    }
    public static void seekMedia(Context context, long positionMs) {
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(context, IslandNotificationListener.class));
            if (sessions == null || sessions.isEmpty()) return;
            MediaController controller = selectActiveMediaController(sessions);
            if (controller == null) return;
            controller.getTransportControls().seekTo(Math.max(0L, positionMs));
            refreshMediaState();
        } catch (RuntimeException ignored) { }
    }
    public static boolean answerCall(String key) { return sendSpecialAction(key, 1); }
    public static boolean declineCall(String key) { return sendSpecialAction(key, 2); }
    public static boolean hangUpCall(String key) { return sendSpecialAction(key, 4); }
    public static boolean performTimerAction(String key) { return sendSpecialAction(key, 3); }
    private static boolean sendSpecialAction(String key, int type) {
        NotificationItem item;
        synchronized (LOCK) { item = ITEMS.get(key); }
        if (item == null) return false;
        PendingIntent action = type == 1 ? item.answerIntent
                : type == 2 ? item.declineIntent
                : type == 4 ? item.hangUpIntent : item.timerActionIntent;
        if (action == null) return false;
        try {
            action.send();
            return true;
        } catch (PendingIntent.CanceledException | RuntimeException ignored) {
            return false;
        }
    }
    public static boolean hasReplyAction() {
        NotificationItem item = getSelectedNotification();
        return item != null && item.canReply();
    }
    public static boolean hasLatestNotification() { return getSelectedNotification() != null; }

    public static boolean replyToLatest(Context context, String message) {
        NotificationItem item = getSelectedNotification();
        return item != null && replyToNotification(context, item.key, message);
    }

    public static boolean replyToNotification(Context context, String key, String message) {
        NotificationItem item;
        synchronized (LOCK) { item = ITEMS.get(key); }
        if (item == null || !item.canReply() || message == null || message.trim().isEmpty()) return false;
        try {
            Intent fillIn = new Intent();
            Bundle results = new Bundle();
            results.putCharSequence(item.replyResultKey, message.trim());
            RemoteInput.addResultsToIntent(item.remoteInputs, fillIn, results);
            item.replyIntent.send(context, 0, fillIn);
            return true;
        } catch (PendingIntent.CanceledException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean dismissLatest() {
        NotificationItem item = getSelectedNotification();
        return item != null && dismissNotification(item.key);
    }

    public static boolean dismissNotification(String key) {
        IslandNotificationListener service = instance;
        if (service == null || key == null || key.isEmpty()) return false;
        try {
            service.cancelNotification(key);
            synchronized (LOCK) {
                ITEMS.remove(key);
                if (key.equals(selectedKey)) selectedKey = newestKeyLocked();
            }
            notifyIslandStatic();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean clearAllNotifications() {
        IslandNotificationListener service = instance;
        if (service == null) return false;
        try {
            String[] keys;
            synchronized (LOCK) { keys = ITEMS.keySet().toArray(new String[0]); }
            if (keys.length == 0) return true;
            service.cancelNotifications(keys);
            synchronized (LOCK) {
                for (String key : keys) ITEMS.remove(key);
                if (keysContains(keys, selectedKey)) selectedKey = newestKeyLocked();
            }
            notifyIslandStatic();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean keysContains(String[] keys, String selected) {
        if (selected == null) return false;
        for (String key : keys) if (selected.equals(key)) return true;
        return false;
    }

    public static void openLatest() {
        NotificationItem item = getSelectedNotification();
        if (item == null || item.contentIntent == null) return;
        try {
            item.contentIntent.send();
            if (item.autoCancel) dismissNotification(item.key);
        } catch (PendingIntent.CanceledException ignored) { }
    }

    public static void openNotification(String key) {
        NotificationItem item;
        synchronized (LOCK) { item = ITEMS.get(key); }
        if (item == null || item.contentIntent == null) return;
        try {
            item.contentIntent.send();
            if (item.autoCancel) dismissNotification(item.key);
        } catch (PendingIntent.CanceledException ignored) { }
    }

    public static void togglePlayback(Context context) { sendMediaCommand(context, 0); }
    public static void skipToPrevious(Context context) { sendMediaCommand(context, -1); }
    public static void skipToNext(Context context) { sendMediaCommand(context, 1); }

    private static void sendMediaCommand(Context context, int command) {
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(context, IslandNotificationListener.class));
            if (sessions == null || sessions.isEmpty()) return;
            MediaController controller = selectActiveMediaController(sessions);
            if (controller == null) return;
            MediaController.TransportControls controls = controller.getTransportControls();
            if (command < 0) controls.skipToPrevious();
            else if (command > 0) controls.skipToNext();
            else {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) controls.pause();
                else controls.play();
            }
        } catch (RuntimeException ignored) { }
    }

    private static MediaController selectActiveMediaController(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) return null;
        for (MediaController controller : sessions) {
            if (controller == null) continue;
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) return controller;
        }
        return sessions.get(0);
    }

    private void refreshMedia() {
        mediaTitle = "";
        mediaArtist = "";
        mediaPackage = "";
        mediaPlaying = false;
        mediaArtwork = null;
        mediaDuration = 0L;
        mediaPosition = 0L;
        mediaPositionUpdatedAt = 0L;
        mediaPlaybackSpeed = 0f;
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(this, IslandNotificationListener.class));
            if (sessions == null || sessions.isEmpty()) return;
            MediaController controller = selectActiveMediaController(sessions);
            if (controller == null) return;
            mediaPackage = controller.getPackageName() == null ? "" : controller.getPackageName();
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                CharSequence title = metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                if (title == null || title.length() == 0) title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.length() == 0) title = metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                CharSequence artist = metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                if (artist == null || artist.length() == 0) artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null || artist.length() == 0) artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                mediaTitle = title == null || title.length() == 0 ? "Đang phát nội dung" : title.toString();
                mediaArtist = artist == null ? "" : artist.toString();
                mediaDuration = Math.max(0L, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
                Bitmap artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (artwork == null) artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (artwork == null) artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (artwork != null) {
                    int size = Math.min(256, Math.max(artwork.getWidth(), artwork.getHeight()));
                    float ratio = Math.min(256f / Math.max(1, artwork.getWidth()), 256f / Math.max(1, artwork.getHeight()));
                    int w = Math.max(1, Math.round(artwork.getWidth() * ratio));
                    int h = Math.max(1, Math.round(artwork.getHeight() * ratio));
                    mediaArtwork = Bitmap.createScaledBitmap(artwork, w, h, true);
                }
            }
            PlaybackState state = controller.getPlaybackState();
            mediaPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            if (state != null) {
                mediaPosition = Math.max(0L, state.getPosition());
                mediaPositionUpdatedAt = state.getLastPositionUpdateTime();
                if (mediaPositionUpdatedAt <= 0L) mediaPositionUpdatedAt = SystemClock.elapsedRealtime();
                mediaPlaybackSpeed = mediaPlaying ? state.getPlaybackSpeed() : 0f;
            }
        } catch (RuntimeException ignored) { }
    }

    private void notifyIsland() { notifyIslandStatic(); }

    private static void notifyIslandStatic() {
        IslandNotificationListener service = instance;
        if (service == null) return;
        try {
            Intent intent = new Intent(ACTION_DATA_CHANGED);
            intent.setPackage(service.getPackageName());
            service.sendBroadcast(intent);
        } catch (RuntimeException ignored) { }
    }
}
