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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.TrafficStats;
import android.net.Uri;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
    private LinearLayout topRow;
    private LinearLayout expandedPanel;
    private LinearLayout mediaControls;
    private LinearLayout notificationActions;
    private TextView compactText;
    private TextView rightText;
    private TextView detailsText;
    private TextView mediaButton;
    private TextView previousButton;
    private TextView nextButton;
    private ImageView appIconView;
    private EditText replyInput;
    private Button replyButton;
    private Button dismissButton;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean expanded = false;
    private boolean receiverRegistered = false;
    private boolean keyboardMode = false;
    private String lastIconPackage = "";
    private String lastCustomIconUri = "";
    private int batteryPercent = -1;
    private boolean charging = false;
    private long lastRx = TrafficStats.getTotalRxBytes();
    private long lastTick = System.currentTimeMillis();
    private float lastTouchX;
    private float lastTouchY;
    private int startWindowX;
    private int startWindowY;
    private boolean dragged;

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateContent();
        }
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
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(dataReceiver, dataFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(dataReceiver, dataFilter);
            }
            receiverRegistered = true;
        } catch (Exception ignored) { }
        handler.post(ticker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            prefs.edit().putBoolean("user_stopped", true).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        prefs.edit().putBoolean("user_stopped", false).apply();
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
                .setCategory(Notification.CATEGORY_SERVICE)
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
            channel.setDescription("Thông báo thường trực cho dịch vụ Đảo động");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void createIsland() {
        islandRoot = new LinearLayout(this);
        islandRoot.setOrientation(LinearLayout.VERTICAL);
        islandRoot.setGravity(Gravity.CENTER_VERTICAL);
        islandRoot.setPadding(dp(13), dp(4), dp(13), dp(4));
        islandRoot.setElevation(dp(12));
        islandRoot.setClickable(true);

        topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        compactText = makeText("Đảo động", 13, Color.WHITE, true);
        compactText.setSingleLine(true);
        compactText.setMaxLines(1);
        compactText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        compactText.setPadding(dp(3), 0, dp(3), 0);
        rightText = makeText("●", 11, 0xFF8CE6AE, true);
        rightText.setGravity(Gravity.CENTER);
        appIconView = new ImageView(this);
        appIconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        appIconView.setBackground(makeRoundDrawable(0xFF222530, dp(8), 0x00000000, 0));
        appIconView.setClipToOutline(true);
        appIconView.setOnClickListener(v -> IslandNotificationListener.openLatest());
        rebuildTopRow();
        islandRoot.addView(topRow, new LinearLayout.LayoutParams(-1, dp(prefs.getInt("height", 62))));

        expandedPanel = new LinearLayout(this);
        expandedPanel.setOrientation(LinearLayout.VERTICAL);
        expandedPanel.setPadding(dp(8), dp(10), dp(8), dp(5));
        detailsText = makeText("", 12, Color.WHITE, false);
        detailsText.setGravity(Gravity.CENTER_VERTICAL);
        detailsText.setMaxLines(5);
        detailsText.setLineSpacing(dp(2), 1f);
        expandedPanel.addView(detailsText, new LinearLayout.LayoutParams(-1, -2));

        mediaControls = new LinearLayout(this);
        mediaControls.setOrientation(LinearLayout.HORIZONTAL);
        mediaControls.setGravity(Gravity.CENTER_VERTICAL);
        mediaControls.setPadding(0, dp(6), 0, dp(2));
        previousButton = makeText("|◀", 15, Color.WHITE, true);
        previousButton.setGravity(Gravity.CENTER);
        previousButton.setOnClickListener(v -> {
            IslandNotificationListener.skipToPrevious(this);
            handler.postDelayed(this::updateContent, 350);
        });
        mediaButton = makeText("▶  /  ⏸", 12, prefs.getInt("accent_color", 0xFFA680FF), true);
        mediaButton.setGravity(Gravity.CENTER);
        mediaButton.setOnClickListener(v -> {
            IslandNotificationListener.togglePlayback(this);
            handler.postDelayed(this::updateContent, 350);
        });
        nextButton = makeText("▶|", 15, Color.WHITE, true);
        nextButton.setGravity(Gravity.CENTER);
        nextButton.setOnClickListener(v -> {
            IslandNotificationListener.skipToNext(this);
            handler.postDelayed(this::updateContent, 350);
        });
        mediaControls.addView(previousButton, new LinearLayout.LayoutParams(0, dp(36), 1));
        mediaControls.addView(mediaButton, new LinearLayout.LayoutParams(0, dp(36), 2));
        mediaControls.addView(nextButton, new LinearLayout.LayoutParams(0, dp(36), 1));
        expandedPanel.addView(mediaControls, new LinearLayout.LayoutParams(-1, -2));

        replyInput = new EditText(this);
        replyInput.setSingleLine(true);
        replyInput.setTextSize(12);
        replyInput.setTextColor(prefs.getInt("text_color", Color.WHITE));
        replyInput.setHintTextColor(0xFF9296A3);
        replyInput.setHint("Nhập tin nhắn trả lời…");
        replyInput.setPadding(dp(10), 0, dp(10), 0);
        replyInput.setBackground(makeRoundDrawable(0xFF242936, dp(10), 0xFF454C5E, dp(1)));
        replyInput.setOnClickListener(v -> {
            enableKeyboardMode();
            replyInput.requestFocus();
        });
        replyInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) enableKeyboardMode();
        });
        notificationActions = new LinearLayout(this);
        notificationActions.setOrientation(LinearLayout.HORIZONTAL);
        notificationActions.setGravity(Gravity.CENTER_VERTICAL);
        notificationActions.setPadding(0, dp(7), 0, dp(2));
        notificationActions.addView(replyInput, new LinearLayout.LayoutParams(0, dp(42), 1));
        replyButton = actionButton("Trả lời", prefs.getInt("accent_color", 0xFFA680FF));
        replyButton.setOnClickListener(v -> sendReply());
        notificationActions.addView(replyButton, new LinearLayout.LayoutParams(-2, dp(42)));
        dismissButton = actionButton("Bỏ qua", 0xFF343946);
        dismissButton.setOnClickListener(v -> {
            if (IslandNotificationListener.dismissLatest()) {
                replyInput.setText("");
                updateContent();
            }
        });
        notificationActions.addView(dismissButton, new LinearLayout.LayoutParams(-2, dp(42)));
        expandedPanel.addView(notificationActions, new LinearLayout.LayoutParams(-1, -2));

        expandedPanel.setVisibility(View.GONE);
        islandRoot.addView(expandedPanel, new LinearLayout.LayoutParams(-1, -2));
        islandRoot.setOnTouchListener(this::handleTouch);
        applyBackground();

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        windowParams = new WindowManager.LayoutParams(
                dp(prefs.getInt("width", 235)), dp(prefs.getInt("height", 62)), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        windowParams.x = dp(prefs.getInt("x", 0));
        windowParams.y = dp(prefs.getInt("y", 12));
        windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try {
            windowManager.addView(islandRoot, windowParams);
        } catch (Exception e) {
            islandRoot = null;
            stopSelf();
        }
    }

    private void rebuildTopRow() {
        if (topRow == null) return;
        topRow.removeAllViews();
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(27), dp(27));
        iconParams.setMargins(dp(2), 0, dp(7), 0);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -1, 1);
        boolean iconLeft = prefs.getInt("icon_position", 0) == 0;
        if (iconLeft) topRow.addView(appIconView, iconParams);
        topRow.addView(compactText, textParams);
        if (!iconLeft) {
            LinearLayout.LayoutParams rightIconParams = new LinearLayout.LayoutParams(dp(27), dp(27));
            rightIconParams.setMargins(dp(6), 0, dp(3), 0);
            topRow.addView(appIconView, rightIconParams);
        }
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -1);
        statusParams.setMargins(dp(5), 0, 0, 0);
        topRow.addView(rightText, statusParams);
    }

    private boolean handleTouch(View view, MotionEvent event) {
        boolean allowDrag = prefs.getBoolean("allow_drag", false);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                startWindowX = windowParams.x;
                startWindowY = windowParams.y;
                dragged = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - lastTouchX);
                int dy = Math.round(event.getRawY() - lastTouchY);
                if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) dragged = true;
                if (allowDrag && dragged && windowManager != null) {
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
                if (allowDrag && dragged) {
                    prefs.edit().putInt("x", Math.round(windowParams.x / getResources().getDisplayMetrics().density))
                            .putInt("y", Math.round(windowParams.y / getResources().getDisplayMetrics().density)).apply();
                } else if (!dragged) {
                    expanded = !expanded;
                    applySettings();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return true;
    }

    private void enableKeyboardMode() {
        if (keyboardMode || windowParams == null || islandRoot == null) return;
        keyboardMode = true;
        windowParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { windowManager.updateViewLayout(islandRoot, windowParams); } catch (Exception ignored) { }
        replyInput.requestFocus();
        replyInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(replyInput, InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }

    private void sendReply() {
        String message = replyInput.getText().toString();
        if (!IslandNotificationListener.replyToLatest(this, message)) {
            toast("Thông báo này không hỗ trợ trả lời nhanh hoặc đã hết hạn");
            return;
        }
        replyInput.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(replyInput.getWindowToken(), 0);
        keyboardMode = false;
        windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { windowManager.updateViewLayout(islandRoot, windowParams); } catch (Exception ignored) { }
        updateContent();
    }

    private void applySettings() {
        if (islandRoot == null || windowParams == null) return;
        int width = dp(prefs.getInt("width", 235));
        int height = dp(prefs.getInt("height", 62));
        int expandedHeight = dp(prefs.getInt("expanded_height", 260));
        windowParams.width = width;
        windowParams.height = expanded ? height + expandedHeight : height;
        windowParams.x = dp(prefs.getInt("x", 0));
        windowParams.y = dp(prefs.getInt("y", 12));
        View top = islandRoot.getChildAt(0);
        android.view.ViewGroup.LayoutParams topParams = top.getLayoutParams();
        topParams.height = height;
        top.setLayoutParams(topParams);
        expandedPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expandedPanel != null) {
            android.view.ViewGroup.LayoutParams panelParams = expandedPanel.getLayoutParams();
            panelParams.height = expanded ? expandedHeight : -2;
            expandedPanel.setLayoutParams(panelParams);
        }
        if (mediaButton != null) mediaButton.setTextColor(prefs.getInt("accent_color", 0xFFA680FF));
        if (replyButton != null) replyButton.setBackground(makeRoundDrawable(prefs.getInt("accent_color", 0xFFA680FF), dp(10), 0, 0));
        rebuildTopRow();
        applyBackground();
        try { windowManager.updateViewLayout(islandRoot, windowParams); } catch (Exception ignored) { }
    }

    private void applyBackground() {
        if (islandRoot == null) return;
        int bgColor = prefs.getInt("island_bg_color", 0xFF050507);
        int panelColor = prefs.getInt("panel_bg_color", 0xFF171923);
        int textColor = prefs.getInt("text_color", Color.WHITE);
        int radius = prefs.getInt("corner_radius", 38);
        int border = prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFA680FF));
        int borderWidth = prefs.getInt("border_width", 1);
        islandRoot.setBackground(makeRoundDrawable(bgColor, dp(expanded ? Math.max(18, radius - 4) : radius), border, dp(borderWidth)));
        if (expandedPanel != null) expandedPanel.setBackground(makeRoundDrawable(panelColor, dp(20), 0x00000000, 0));
        if (compactText != null) compactText.setTextColor(textColor);
        if (detailsText != null) detailsText.setTextColor(textColor);
        if (replyInput != null) replyInput.setTextColor(textColor);
    }

    private void updateContent() {
        if (islandRoot == null) return;
        boolean showMedia = prefs.getBoolean("show_media", true);
        boolean showNotif = prefs.getBoolean("show_notifications", true);
        boolean showBattery = prefs.getBoolean("show_battery", true);
        boolean showNetwork = prefs.getBoolean("show_network", true);
        boolean showAppIcon = prefs.getBoolean("show_source_icon", true);
        boolean showAppName = prefs.getBoolean("show_source_app_name", true);
        boolean showReply = prefs.getBoolean("show_reply_actions", true);
        boolean showDismiss = prefs.getBoolean("show_dismiss_action", true);

        String media = IslandNotificationListener.getMediaTitle();
        String mediaArtist = IslandNotificationListener.getMediaArtist();
        String notification = IslandNotificationListener.getLatestTitle();
        String notificationText = IslandNotificationListener.getLatestText();
        String notificationPackage = IslandNotificationListener.getLatestPackage();
        String sourceAppName = resolveAppName(notificationPackage);
        String customName = prefs.getString("custom_app_name", "").trim();
        long timerEnd = prefs.getLong("timer_end", 0L);
        long remaining = Math.max(0L, timerEnd - System.currentTimeMillis());
        long demoUntil = prefs.getLong("demo_until", 0L);
        boolean demoActive = demoUntil > System.currentTimeMillis();
        int accent = prefs.getInt("accent_color", 0xFFA680FF);
        int textColor = prefs.getInt("text_color", Color.WHITE);

        boolean hasNotification = showNotif && !notification.isEmpty();
        boolean hasMedia = showMedia && !media.isEmpty();
        String customIconUri = prefs.getString("custom_icon_uri", "");
        boolean hasIconPackage = showAppIcon && hasNotification
                && (!notificationPackage.isEmpty() || !customIconUri.isEmpty());
        if (appIconView != null) {
            appIconView.setVisibility(hasIconPackage ? View.VISIBLE : View.GONE);
            if (hasIconPackage && !customIconUri.isEmpty() && !customIconUri.equals(lastCustomIconUri)) {
                try (java.io.InputStream input = getContentResolver().openInputStream(Uri.parse(customIconUri))) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) appIconView.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
                    else appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
                } catch (Exception e) {
                    appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                lastCustomIconUri = customIconUri;
                lastIconPackage = notificationPackage;
            } else if (hasIconPackage && customIconUri.isEmpty() && !notificationPackage.equals(lastIconPackage)) {
                try {
                    Drawable icon = getPackageManager().getApplicationIcon(notificationPackage);
                    appIconView.setImageDrawable(icon);
                } catch (Exception e) {
                    appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                lastIconPackage = notificationPackage;
                lastCustomIconUri = "";
            } else if (!hasIconPackage) {
                lastIconPackage = "";
                lastCustomIconUri = "";
            }
            appIconView.setOnClickListener(v -> IslandNotificationListener.openLatest());
        }

        String headline = "Đảo động";
        if (demoActive) headline = "Đảo động đã sẵn sàng";
        else if (hasMedia) headline = media;
        else if (hasNotification) {
            String appPart = customName.isEmpty() ? (showAppName ? sourceAppName : "") : customName;
            headline = appPart.isEmpty() ? notification : appPart + " · " + notification;
        } else if (timerEnd > 0 && remaining > 0) headline = "Hẹn giờ " + formatTime(remaining);
        else if (showBattery && batteryPercent >= 0) headline = (charging ? "Đang sạc · " : "Pin · ") + batteryPercent + "%";
        else if (!customName.isEmpty()) headline = customName;
        compactText.setText(trim(headline, 42));
        compactText.setTextColor(textColor);
        rightText.setText(showBattery && batteryPercent >= 0 ? batteryPercent + "%" : "●");
        rightText.setTextColor(charging ? 0xFF79DFAE : accent);

        StringBuilder details = new StringBuilder();
        if (demoActive) details.append("Đảo động đang hoạt động\nChạm để mở rộng hoặc thu gọn. Vị trí được chỉnh trong ứng dụng.");
        if (hasMedia) {
            appendLine(details, "♫  " + media + (mediaArtist.isEmpty() ? "" : " — " + mediaArtist));
            appendLine(details, IslandNotificationListener.isMediaPlaying() ? "Đang phát nhạc" : "Đang tạm dừng");
        }
        if (hasNotification) {
            String appPart = customName.isEmpty() ? (showAppName ? sourceAppName : "Thông báo") : customName;
            appendLine(details, "✦  " + (appPart.isEmpty() ? "Thông báo" : appPart) + ": " + notification);
            if (!notificationText.isEmpty()) appendLine(details, notificationText);
        }
        if (showBattery && batteryPercent >= 0) appendLine(details, (charging ? "Đang sạc · " : "Pin · ") + batteryPercent + "%");
        if (showNetwork) appendLine(details, "Tốc độ tải xuống: " + currentNetworkSpeed());
        if (timerEnd > 0) {
            if (remaining == 0) {
                appendLine(details, "Hẹn giờ đã kết thúc");
                prefs.edit().remove("timer_end").apply();
            } else appendLine(details, "Đếm ngược: " + formatTime(remaining));
        }
        if (details.length() == 0) details.append("Chưa có hoạt động mới. Chạm để xem thêm.");
        detailsText.setText(details.toString());

        int mediaVisibility = hasMedia ? View.VISIBLE : View.GONE;
        mediaControls.setVisibility(mediaVisibility);
        previousButton.setTextColor(textColor);
        nextButton.setTextColor(textColor);
        mediaButton.setTextColor(accent);

        boolean hasReply = hasNotification && showReply && IslandNotificationListener.hasReplyAction();
        boolean hasDismiss = hasNotification && showDismiss && IslandNotificationListener.hasLatestNotification();
        replyInput.setVisibility(hasReply ? View.VISIBLE : View.GONE);
        replyButton.setVisibility(hasReply ? View.VISIBLE : View.GONE);
        dismissButton.setVisibility(hasDismiss ? View.VISIBLE : View.GONE);
        notificationActions.setVisibility(hasReply || hasDismiss ? View.VISIBLE : View.GONE);
        if (hasReply) {
            replyButton.setTextColor(Color.WHITE);
            replyButton.setBackground(makeRoundDrawable(accent, dp(10), 0, 0));
        }
        if (hasDismiss) dismissButton.setTextColor(textColor);
        rebuildTopRow();
        applyBackground();
    }

    private String resolveAppName(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "";
        try {
            android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private void appendLine(StringBuilder builder, String line) {
        if (line == null || line.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append('\n');
        builder.append(line);
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

    private Button actionButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(makeRoundDrawable(color, dp(10), 0, 0));
        return button;
    }

    private GradientDrawable makeRoundDrawable(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
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
