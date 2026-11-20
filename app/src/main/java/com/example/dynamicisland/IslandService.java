package com.example.dynamicisland;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class IslandService extends Service {
    public static volatile boolean running = false;
    public static final String ACTION_START = "com.example.dynamicisland.START";
    public static final String ACTION_REFRESH = "com.example.dynamicisland.REFRESH";
    public static final String ACTION_STOP = "com.example.dynamicisland.STOP";
    private static final String CHANNEL_ID = "dynamic_island_running";
    private static final int NOTIFICATION_ID = 7124;
    private static final String PREFS = "island_settings";

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private LinearLayout islandRoot;
    private LinearLayout expandedPanel;
    private TextView compactText;
    private TextView rightText;
    private TextView detailsText;
    private TextView mediaButton;
    private TextView previousButton;
    private TextView nextButton;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean expanded = false;
    private boolean receiverRegistered = false;
    private int batteryPercent = -1;
    private boolean charging = false;
    private long lastRx = TrafficStats.getTotalRxBytes();
    private long lastTick = System.currentTimeMillis();
    private long lastTouchX;
    private long lastTouchY;
    private int startWindowX;
    private int startWindowY;
    private boolean dragged;

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateContent(); }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) batteryPercent = Math.round(level * 100f / scale);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            updateContent();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateContent();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        try {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            IntentFilter dataFilter = new IntentFilter(IslandNotificationListener.ACTION_DATA_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(dataReceiver, dataFilter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(dataReceiver, dataFilter);
            receiverRegistered = true;
        } catch (Exception ignored) { }
        handler.post(ticker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startAsForeground();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (islandRoot == null) createIsland();
        else applySettings();
        updateContent();
        return START_STICKY;
    }

    private void startAsForeground() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Đảo động đang hoạt động")
                .setContentText("Chạm để mở bảng tùy chỉnh")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Dịch vụ Đảo động", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Thông báo cho dịch vụ Đảo động nổi");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void createIsland() {
        islandRoot = new LinearLayout(this);
        islandRoot.setOrientation(LinearLayout.VERTICAL);
        islandRoot.setGravity(Gravity.CENTER_VERTICAL);
        islandRoot.setPadding(dp(14), dp(4), dp(14), dp(4));
        islandRoot.setElevation(dp(10));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        compactText = makeText("Đảo động", 13, Color.WHITE, true);
        rightText = makeText("●", 11, 0xFF8CE6AE, true);
        topRow.addView(compactText, new LinearLayout.LayoutParams(0, -1, 1));
        topRow.addView(rightText, new LinearLayout.LayoutParams(-2, -1));
        islandRoot.addView(topRow, new LinearLayout.LayoutParams(-1, dp(prefs.getInt("height", 58))));

        expandedPanel = new LinearLayout(this);
        expandedPanel.setOrientation(LinearLayout.VERTICAL);
        expandedPanel.setPadding(dp(2), dp(8), dp(2), dp(4));
        detailsText = makeText("", 12, 0xFFE8E8F0, false);
        expandedPanel.addView(detailsText, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(7), 0, dp(2));
        previousButton = makeText("|◀", 15, 0xFFE9E6F1, true);
        previousButton.setGravity(Gravity.CENTER);
        previousButton.setOnClickListener(v -> {
            IslandNotificationListener.skipToPrevious(this);
            handler.postDelayed(this::updateContent, 350);
        });
        mediaButton = makeText("▶ / ⏸", 12, prefs.getInt("accent_color", 0xFFA680FF), true);
        mediaButton.setGravity(Gravity.CENTER);
        mediaButton.setOnClickListener(v -> {
            IslandNotificationListener.togglePlayback(this);
            handler.postDelayed(this::updateContent, 350);
        });
        nextButton = makeText("▶|", 15, 0xFFE9E6F1, true);
        nextButton.setGravity(Gravity.CENTER);
        nextButton.setOnClickListener(v -> {
            IslandNotificationListener.skipToNext(this);
            handler.postDelayed(this::updateContent, 350);
        });
        controls.addView(previousButton, new LinearLayout.LayoutParams(0, dp(36), 1));
        controls.addView(mediaButton, new LinearLayout.LayoutParams(0, dp(36), 2));
        controls.addView(nextButton, new LinearLayout.LayoutParams(0, dp(36), 1));
        expandedPanel.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        expandedPanel.setVisibility(View.GONE);
        islandRoot.addView(expandedPanel, new LinearLayout.LayoutParams(-1, -2));

        islandRoot.setOnTouchListener(this::handleTouch);
        applyBackground();

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        windowParams = new WindowManager.LayoutParams(
                dp(prefs.getInt("width", 220)), dp(prefs.getInt("height", 58)), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        windowParams.x = dp(prefs.getInt("x", 0));
        windowParams.y = dp(prefs.getInt("y", 12));
        try {
            windowManager.addView(islandRoot, windowParams);
        } catch (Exception e) {
            islandRoot = null;
            stopSelf();
        }
    }

    private boolean handleTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = (long) event.getRawX();
                lastTouchY = (long) event.getRawY();
                startWindowX = windowParams.x;
                startWindowY = windowParams.y;
                dragged = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = (int) (event.getRawX() - lastTouchX);
                int dy = (int) (event.getRawY() - lastTouchY);
                if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) dragged = true;
                if (dragged) {
                    android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                    windowManager.getDefaultDisplay().getMetrics(metrics);
                    int maxOffsetX = Math.max(0, (metrics.widthPixels - windowParams.width) / 2);
                    int maxOffsetY = Math.max(0, metrics.heightPixels - windowParams.height);
                    windowParams.x = Math.max(-maxOffsetX, Math.min(maxOffsetX, startWindowX + dx));
                    windowParams.y = Math.max(0, Math.min(maxOffsetY, startWindowY + dy));
                    try { windowManager.updateViewLayout(islandRoot, windowParams); } catch (Exception ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragged) {
                    prefs.edit().putInt("x", Math.round(windowParams.x / getResources().getDisplayMetrics().density))
                            .putInt("y", Math.round(windowParams.y / getResources().getDisplayMetrics().density)).apply();
                } else {
                    expanded = !expanded;
                    applySettings();
                }
                return true;
        }
        return true;
    }

    private void applySettings() {
        if (islandRoot == null || windowParams == null) return;
        int width = dp(prefs.getInt("width", 220));
        int height = dp(prefs.getInt("height", 58));
        int x = dp(prefs.getInt("x", 0));
        int y = dp(prefs.getInt("y", 12));
        windowParams.width = width;
        windowParams.height = expanded ? height + dp(176) : height;
        windowParams.x = x;
        windowParams.y = y;
        View top = islandRoot.getChildAt(0);
        android.view.ViewGroup.LayoutParams topParams = top.getLayoutParams();
        topParams.height = height;
        top.setLayoutParams(topParams);
        expandedPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (mediaButton != null) mediaButton.setTextColor(prefs.getInt("accent_color", 0xFFA680FF));
        applyBackground();
        try { windowManager.updateViewLayout(islandRoot, windowParams); } catch (Exception ignored) { }
    }

    private void applyBackground() {
        if (islandRoot == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(5, 5, 7));
        int userRadius = prefs == null ? 32 : prefs.getInt("corner_radius", 32);
        bg.setCornerRadius(dp(expanded ? Math.max(22, userRadius - 4) : userRadius));
        int accent = prefs == null ? 0xFFA680FF : prefs.getInt("accent_color", 0xFFA680FF);
        bg.setStroke(dp(1), (accent & 0x00FFFFFF) | 0x66000000);
        islandRoot.setBackground(bg);
    }

    private void updateContent() {
        if (islandRoot == null) return;
        boolean showMedia = prefs.getBoolean("show_media", true);
        boolean showNotif = prefs.getBoolean("show_notifications", true);
        boolean showBattery = prefs.getBoolean("show_battery", true);
        boolean showNetwork = prefs.getBoolean("show_network", true);

        String media = IslandNotificationListener.getMediaTitle();
        String mediaArtist = IslandNotificationListener.getMediaArtist();
        String notification = IslandNotificationListener.getLatestTitle();
        String notificationText = IslandNotificationListener.getLatestText();
        long timerEnd = prefs.getLong("timer_end", 0L);
        long remaining = Math.max(0L, timerEnd - System.currentTimeMillis());
        long demoUntil = prefs.getLong("demo_until", 0L);
        boolean demoActive = demoUntil > System.currentTimeMillis();
        int accent = prefs.getInt("accent_color", 0xFFA680FF);

        String headline = "Đảo động";
        if (demoActive) headline = "Xin chào! Đảo động đã sẵn sàng";
        else if (showMedia && !media.isEmpty()) headline = media;
        else if (showNotif && !notification.isEmpty()) headline = notification;
        else if (timerEnd > 0 && remaining > 0) headline = "Hẹn giờ " + formatTime(remaining);
        else if (showBattery && batteryPercent >= 0) headline = (charging ? "Đang sạc · " : "Pin · ") + batteryPercent + "%";
        compactText.setText(trim(headline, 28));
        rightText.setText(showBattery && batteryPercent >= 0 ? batteryPercent + "%" : "●");
        rightText.setTextColor(accent);

        StringBuilder details = new StringBuilder();
        if (demoActive) {
            details.append("Đảo động đang hoạt động\nVuốt hoặc kéo để di chuyển vị trí. Chạm để mở rộng.");
        }
        if (showMedia && !media.isEmpty()) {
            if (details.length() > 0) details.append("\n\n");
            details.append("♫  ").append(media);
            if (!mediaArtist.isEmpty()) details.append(" — ").append(mediaArtist);
            details.append(IslandNotificationListener.isMediaPlaying() ? "\nĐang phát" : "\nĐang tạm dừng");
        }
        if (showNotif && !notification.isEmpty()) {
            if (details.length() > 0) details.append("\n\n");
            details.append("Thông báo: ").append(notification);
            if (!notificationText.isEmpty()) details.append("\n").append(notificationText);
        }
        if (showBattery && batteryPercent >= 0) {
            if (details.length() > 0) details.append("\n\n");
            details.append(charging ? "Đang sạc · " : "Pin · ").append(batteryPercent).append('%');
        }
        if (showNetwork) {
            if (details.length() > 0) details.append("\n");
            details.append("Tốc độ tải xuống: ").append(currentNetworkSpeed());
        }
        if (timerEnd > 0) {
            if (remaining == 0) {
                details.append("\nHẹn giờ đã kết thúc");
                prefs.edit().remove("timer_end").apply();
            } else {
                details.append("\nĐếm ngược: ").append(formatTime(remaining));
            }
        }
        if (details.length() == 0) details.append("Chưa có hoạt động mới. Chạm để mở rộng, kéo để di chuyển.");
        detailsText.setText(details.toString());
        int mediaVisibility = showMedia && !media.isEmpty() ? View.VISIBLE : View.GONE;
        mediaButton.setVisibility(mediaVisibility);
        previousButton.setVisibility(mediaVisibility);
        nextButton.setVisibility(mediaVisibility);
    }

    private String currentNetworkSpeed() {
        long now = System.currentTimeMillis();
        long currentRx = TrafficStats.getTotalRxBytes();
        long elapsed = Math.max(1, now - lastTick);
        long delta = currentRx >= lastRx ? currentRx - lastRx : 0;
        lastRx = currentRx;
        lastTick = now;
        if (currentRx == TrafficStats.UNSUPPORTED) return "không khả dụng";
        double bytesPerSecond = delta * 1000.0 / elapsed;
        if (bytesPerSecond >= 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSecond / (1024 * 1024));
        return String.format(Locale.getDefault(), "%.0f KB/s", bytesPerSecond / 1024);
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
    }

    private String trim(String value, int max) {
        if (value == null || value.isEmpty()) return "Đảo động";
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(ticker);
        if (receiverRegistered) {
            try { unregisterReceiver(dataReceiver); } catch (Exception ignored) { }
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) { }
        }
        if (islandRoot != null && windowManager != null) {
            try { windowManager.removeView(islandRoot); } catch (Exception ignored) { }
            islandRoot = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
