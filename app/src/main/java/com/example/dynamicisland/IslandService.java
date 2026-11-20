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
import android.animation.ObjectAnimator;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.os.Process;
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
    private TextView mediaWave;
    private TextView batteryAlertText;
    private TextView callStatusText;
    private LinearLayout callActions;
    private Button answerCallButton;
    private Button declineCallButton;
    private ImageView mediaArtworkView;
    private ImageView appIconView;
    private LinearLayout notificationIdentityRow;
    private ImageView notificationAppIconView;
    private ImageView senderAvatarView;
    private TextView notificationIdentityText;
    private Button clearAllButton;
    private EditText replyInput;
    private Button replyButton;
    private Button dismissButton;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean expanded = false;
    private boolean receiverRegistered = false;
    private boolean keyboardMode = false;
    private String lastIconPackage = "";
    private String lastNotificationIconKey = "";
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
    private float swipeDeltaX;
    private String lastAnimatedNotificationKey = "";
    private long lastAutoCycleAt = 0L;
    private boolean wasLowBattery = false;
    private int waveformFrame = 0;
    private boolean gameHidden = false;
    private long lastGameCheckAt = 0L;
    private boolean cachedGameForeground = false;

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
        detailsText.setMaxLines(10);
        detailsText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detailsText.setLineSpacing(dp(2), 1f);
        detailsText.setPadding(dp(10), dp(8), dp(10), dp(8));

        notificationIdentityRow = new LinearLayout(this);
        notificationIdentityRow.setOrientation(LinearLayout.HORIZONTAL);
        notificationIdentityRow.setGravity(Gravity.CENTER_VERTICAL);
        notificationIdentityRow.setPadding(dp(4), dp(3), dp(4), dp(7));
        notificationAppIconView = new ImageView(this);
        notificationAppIconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        notificationAppIconView.setBackground(makeRoundDrawable(0xFF252938, dp(9), 0, 0));
        notificationAppIconView.setClipToOutline(true);
        notificationIdentityRow.addView(notificationAppIconView, new LinearLayout.LayoutParams(dp(30), dp(30)));
        senderAvatarView = new ImageView(this);
        senderAvatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        senderAvatarView.setBackground(makeRoundDrawable(0xFF343044, dp(16), 0, 0));
        senderAvatarView.setClipToOutline(true);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        avatarLp.setMargins(dp(7), 0, dp(8), 0);
        notificationIdentityRow.addView(senderAvatarView, avatarLp);
        notificationIdentityText = makeText("", 11, 0xFFE9E7F2, true);
        notificationIdentityText.setMaxLines(2);
        notificationIdentityText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        notificationIdentityRow.addView(notificationIdentityText, new LinearLayout.LayoutParams(0, -2, 1));
        notificationIdentityRow.setVisibility(View.GONE);
        expandedPanel.addView(notificationIdentityRow, new LinearLayout.LayoutParams(-1, -2));
        expandedPanel.addView(detailsText, new LinearLayout.LayoutParams(-1, -2));

        batteryAlertText = makeText("", 12, Color.WHITE, true);
        batteryAlertText.setPadding(dp(12), dp(9), dp(12), dp(9));
        expandedPanel.addView(batteryAlertText, new LinearLayout.LayoutParams(-1, -2));
        batteryAlertText.setVisibility(View.GONE);

        LinearLayout mediaHeader = new LinearLayout(this);
        mediaHeader.setOrientation(LinearLayout.HORIZONTAL);
        mediaHeader.setGravity(Gravity.CENTER_VERTICAL);
        mediaHeader.setPadding(dp(2), dp(8), dp(2), dp(2));
        mediaArtworkView = new ImageView(this);
        mediaArtworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaArtworkView.setBackground(makeRoundDrawable(0xFF252938, dp(12), 0, 0));
        mediaHeader.addView(mediaArtworkView, new LinearLayout.LayoutParams(dp(42), dp(42)));
        mediaHeader.addView(makeText("  ĐANG PHÁT", 10, prefs.getInt("media_color", 0xFFA680FF), true),
                new LinearLayout.LayoutParams(0, dp(42), 1));
        mediaHeader.setTag("media_header");
        expandedPanel.addView(mediaHeader, new LinearLayout.LayoutParams(-1, -2));
        mediaHeader.setVisibility(View.GONE);

        mediaControls = new LinearLayout(this);
        mediaControls.setOrientation(LinearLayout.HORIZONTAL);
        mediaControls.setGravity(Gravity.CENTER_VERTICAL);
        mediaControls.setPadding(0, dp(6), 0, dp(2));
        previousButton = makeText("⏮", 19, Color.WHITE, true);
        previousButton.setGravity(Gravity.CENTER);
        previousButton.setOnClickListener(v -> {
            IslandNotificationListener.skipToPrevious(this);
            handler.postDelayed(this::updateContent, 350);
        });
        mediaButton = makeText("▶", 19, prefs.getInt("media_color", 0xFFA680FF), true);
        mediaButton.setGravity(Gravity.CENTER);
        mediaButton.setOnClickListener(v -> {
            IslandNotificationListener.togglePlayback(this);
            handler.postDelayed(this::updateContent, 350);
        });
        nextButton = makeText("⏭", 19, Color.WHITE, true);
        nextButton.setGravity(Gravity.CENTER);
        nextButton.setOnClickListener(v -> {
            IslandNotificationListener.skipToNext(this);
            handler.postDelayed(this::updateContent, 350);
        });
        mediaControls.addView(previousButton, new LinearLayout.LayoutParams(0, dp(36), 1));
        mediaControls.addView(mediaButton, new LinearLayout.LayoutParams(0, dp(36), 2));
        mediaControls.addView(nextButton, new LinearLayout.LayoutParams(0, dp(36), 1));
        expandedPanel.addView(mediaControls, new LinearLayout.LayoutParams(-1, -2));
        mediaWave = makeText("▂  ▅  ▃  ▇  ▄  ▆  ▂", 15, prefs.getInt("media_color", 0xFFA680FF), true);
        mediaWave.setGravity(Gravity.CENTER);
        expandedPanel.addView(mediaWave, new LinearLayout.LayoutParams(-1, dp(27)));

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

        clearAllButton = actionButton("Xóa tất cả tin nhắn / thông báo", 0xFF492A38);
        clearAllButton.setOnClickListener(v -> {
            int removed = IslandNotificationListener.dismissAllTrackedNotifications();
            replyInput.setText("");
            toast(removed > 0 ? "Đã xóa " + removed + " thông báo đang hiển thị trên Đảo động" : "Không có thông báo để xóa");
            updateContent();
        });
        expandedPanel.addView(clearAllButton, new LinearLayout.LayoutParams(-1, dp(38)));

        callActions = new LinearLayout(this);
        callActions.setOrientation(LinearLayout.VERTICAL);
        callActions.setPadding(0, dp(8), 0, dp(2));
        callStatusText = makeText("Cuộc gọi đến", 12, Color.WHITE, true);
        callActions.addView(callStatusText, new LinearLayout.LayoutParams(-1, dp(30)));
        LinearLayout callButtons = new LinearLayout(this);
        callButtons.setOrientation(LinearLayout.HORIZONTAL);
        callButtons.setGravity(Gravity.CENTER_VERTICAL);
        answerCallButton = actionButton("● Nghe", 0xFF20A66A);
        answerCallButton.setOnClickListener(v -> {
            if (IslandNotificationListener.sendCallAction(true)) {
                callStatusText.setText("Đã nhấn nghe · " + formatCallDuration());
                updateContent();
            } else toast("Ứng dụng gọi không cung cấp thao tác nghe ở thông báo này");
        });
        declineCallButton = actionButton("✕ Tắt", 0xFFB94355);
        declineCallButton.setOnClickListener(v -> {
            if (!IslandNotificationListener.sendCallAction(false)) toast("Ứng dụng gọi không cung cấp thao tác từ chối ở thông báo này");
            updateContent();
        });
        callButtons.addView(answerCallButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        callButtons.addView(declineCallButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        callActions.addView(callButtons, new LinearLayout.LayoutParams(-1, -2));
        expandedPanel.addView(callActions, new LinearLayout.LayoutParams(-1, -2));
        callActions.setVisibility(View.GONE);

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
                swipeDeltaX = 0f;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - lastTouchX);
                int dy = Math.round(event.getRawY() - lastTouchY);
                if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) dragged = true;
                swipeDeltaX = dx;
                boolean horizontalNotificationSwipe = IslandNotificationListener.getNotificationCount() > 1
                        && Math.abs(dx) > Math.abs(dy) * 1.25f;
                if (allowDrag && dragged && !horizontalNotificationSwipe && windowManager != null) {
                    android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                    windowManager.getDefaultDisplay().getMetrics(metrics);
                    int maxOffsetX = Math.max(0, (metrics.widthPixels - windowParams.width) / 2);
                    int maxOffsetY = Math.max(0, metrics.heightPixels - windowParams.height);
                    windowParams.x = Math.max(-maxOffsetX, Math.min(maxOffsetX, startWindowX + dx));
                    windowParams.y = Math.max(-dp(900), Math.min(maxOffsetY, startWindowY + dy));
                    try { windowManager.updateViewLayout(islandRoot, windowParams); } catch (Exception ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragged && IslandNotificationListener.getNotificationCount() > 1
                        && Math.abs(swipeDeltaX) > dp(35)) {
                    IslandNotificationListener.cycleNotification(swipeDeltaX < 0 ? 1 : -1);
                    updateContent();
                } else if (allowDrag && dragged) {
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
        int compactWidth = dp(prefs.getInt("width", 235));
        int expandedWidth = dp(prefs.getInt("expanded_width", 330));
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int widthLimit = Math.max(dp(180), displayMetrics.widthPixels - dp(16));
        int width = Math.min(expanded ? expandedWidth : compactWidth, widthLimit);
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
        if (mediaButton != null) mediaButton.setTextColor(prefs.getInt("media_color", prefs.getInt("accent_color", 0xFFA680FF)));
        if (mediaWave != null) mediaWave.setTextColor(prefs.getInt("media_color", prefs.getInt("accent_color", 0xFFA680FF)));
        if (replyButton != null) replyButton.setBackground(makeRoundDrawable(prefs.getInt("accent_color", 0xFFA680FF), dp(10), 0, 0));
        lastNotificationIconKey = "";
        rebuildTopRow();
        applyBackground();
        try { windowManager.updateViewLayout(islandRoot, windowParams); } catch (Exception ignored) { }
        updateContent();
    }

    private void applyBackground() {
        if (islandRoot == null) return;
        int bgColor = prefs.getInt("island_bg_color", 0xFF050507);
        int panelColor = prefs.getInt("panel_bg_color", 0xFF171923);
        int textColor = prefs.getInt("text_color", Color.WHITE);
        int radius = prefs.getInt("corner_radius", 38);
        int border = prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFA680FF));
        int threshold = prefs.getInt("low_battery_threshold", 20);
        if (batteryPercent >= 0 && batteryPercent <= threshold && !charging) border = prefs.getInt("low_battery_color", 0xFFFF5D73);
        else if (charging) border = 0xFF65DCA5;
        int borderWidth = prefs.getInt("border_width", 1);
        GradientDrawable islandDrawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{bgColor, blendColors(bgColor, prefs.getInt("accent_color", 0xFFA680FF), 0.10f)});
        islandDrawable.setCornerRadius(dp(expanded ? Math.max(18, radius - 4) : radius));
        if (borderWidth > 0) islandDrawable.setStroke(dp(borderWidth), border);
        islandRoot.setBackground(islandDrawable);
        if (expandedPanel != null) expandedPanel.setBackground(makeRoundDrawable(panelColor, dp(20), 0x00000000, 0));
        if (compactText != null) compactText.setTextColor(textColor);
        if (detailsText != null) {
            detailsText.setTextColor(prefs.getInt("notification_text_color", textColor));
            detailsText.setBackground(makeRoundDrawable(prefs.getInt("notification_bg_color", 0xFF202431), dp(13), 0, 0));
        }
        if (replyInput != null) replyInput.setTextColor(textColor);
    }

    private void updateContent() {
        if (islandRoot == null) return;
        boolean hideForGame = prefs.getBoolean("hide_in_game", false) && isGameInForeground();
        gameHidden = hideForGame;
        islandRoot.setVisibility(hideForGame ? View.INVISIBLE : View.VISIBLE);
        if (hideForGame) return;
        if (IslandNotificationListener.getNotificationCount() > 1) {
            long now = System.currentTimeMillis();
            if (prefs.getBoolean("auto_cycle_notifications", true) && now - lastAutoCycleAt > 4200L) {
                IslandNotificationListener.cycleNotification(1);
                lastAutoCycleAt = now;
            }
        }
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
        String notificationSender = IslandNotificationListener.getLatestSender();
        String sourceAppName = resolveAppName(notificationPackage);
        String customName = prefs.getString("custom_app_name", "").trim();
        long timerEnd = prefs.getLong("timer_end", 0L);
        long remaining = Math.max(0L, timerEnd - System.currentTimeMillis());
        long demoUntil = prefs.getLong("demo_until", 0L);
        boolean demoActive = demoUntil > System.currentTimeMillis();
        int accent = prefs.getInt("accent_color", 0xFFA680FF);
        int textColor = prefs.getInt("text_color", Color.WHITE);
        int lowBatteryThreshold = prefs.getInt("low_battery_threshold", 20);
        boolean lowBattery = showBattery && batteryPercent >= 0 && batteryPercent <= lowBatteryThreshold && !charging;

        boolean hasNotification = showNotif && !notification.isEmpty();
        boolean hasMedia = showMedia && !media.isEmpty();
        String customIconUri = prefs.getString("custom_icon_uri", "");
        boolean hasIconPackage = showAppIcon && hasNotification
                && (!notificationPackage.isEmpty() || !customIconUri.isEmpty());
        String currentIconKey = IslandNotificationListener.getLatestKey();
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
                lastNotificationIconKey = currentIconKey;
            } else if (hasIconPackage && customIconUri.isEmpty() && !currentIconKey.equals(lastNotificationIconKey)) {
                try {
                    Drawable icon = getPackageManager().getApplicationIcon(notificationPackage);
                    appIconView.setImageDrawable(icon);
                } catch (Exception e) {
                    appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                lastIconPackage = notificationPackage;
                lastCustomIconUri = "";
                lastNotificationIconKey = currentIconKey;
            } else if (!hasIconPackage) {
                lastIconPackage = "";
                lastCustomIconUri = "";
                lastNotificationIconKey = "";
            }
            appIconView.setOnClickListener(v -> IslandNotificationListener.openLatest());
        }

        if (notificationIdentityRow != null) {
            notificationIdentityRow.setVisibility(hasNotification ? View.VISIBLE : View.GONE);
            if (hasNotification) {
                notificationAppIconView.setVisibility(showAppIcon ? View.VISIBLE : View.GONE);
                try {
                    notificationAppIconView.setImageDrawable(getPackageManager().getApplicationIcon(notificationPackage));
                } catch (Exception ignored) {
                    notificationAppIconView.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                android.graphics.drawable.Icon avatar = IslandNotificationListener.getLatestSenderIcon();
                boolean showAvatar = prefs.getBoolean("prefer_sender_avatar", true);
                senderAvatarView.setVisibility(showAvatar ? View.VISIBLE : View.GONE);
                if (showAvatar && avatar != null) {
                    try { senderAvatarView.setImageIcon(avatar); }
                    catch (RuntimeException ignored) { senderAvatarView.setImageResource(android.R.drawable.ic_menu_myplaces); }
                } else if (showAvatar) {
                    senderAvatarView.setImageResource(android.R.drawable.ic_menu_myplaces);
                }
                String appLabel = customName.isEmpty() ? sourceAppName : customName;
                String senderLabel = notificationSender == null || notificationSender.trim().isEmpty()
                        ? "Người gửi không được cung cấp" : notificationSender;
                notificationIdentityText.setText(trim(senderLabel + "\n" + appLabel, 90));
            }
        }

        View mediaHeader = expandedPanel.findViewWithTag("media_header");
        if (mediaHeader != null) mediaHeader.setVisibility(hasMedia ? View.VISIBLE : View.GONE);
        Bitmap artwork = IslandNotificationListener.getMediaArtwork();
        if (mediaArtworkView != null) {
            if (artwork != null) mediaArtworkView.setImageBitmap(artwork);
            else mediaArtworkView.setImageResource(android.R.drawable.ic_media_play);
        }
        if (mediaWave != null) {
            String[] waves = {"▂  ▅  ▃  ▇  ▄  ▆  ▂", "▃  ▇  ▄  ▅  ▂  ▆  ▃", "▅  ▃  ▆  ▂  ▇  ▄  ▅", "▇  ▄  ▅  ▃  ▆  ▂  ▄"};
            boolean playing = IslandNotificationListener.isMediaPlaying();
            mediaWave.setText(playing ? waves[(waveformFrame++ % waves.length)] : "▁  ▁  ▂  ▁  ▁  ▂  ▁");
            mediaWave.setTextColor(prefs.getInt("media_color", accent));
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
        rightText.setTextColor(lowBattery ? prefs.getInt("low_battery_color", 0xFFFF5D73)
                : (charging ? 0xFF79DFAE : accent));

        StringBuilder details = new StringBuilder();
        if (demoActive) details.append("Đảo động đang hoạt động\nChạm để mở rộng hoặc thu gọn. Vị trí được chỉnh trong ứng dụng.");
        if (hasMedia) {
            appendLine(details, "♫  " + media + (mediaArtist.isEmpty() ? "" : " — " + mediaArtist));
            appendLine(details, IslandNotificationListener.isMediaPlaying() ? "Đang phát nhạc" : "Đang tạm dừng");
        }
        if (hasNotification) {
            appendLine(details, notification);
            if (!notificationText.isEmpty()) appendLine(details, notificationText);
            int count = IslandNotificationListener.getNotificationCount();
            if (count > 1) appendLine(details, "Thông báo " + (IslandNotificationListener.getSelectedNotificationIndex() + 1) + "/" + count + " · Vuốt trái/phải để chuyển");
        }
        if (showBattery && batteryPercent >= 0) appendLine(details, (charging ? "Đang sạc · " : "Pin · ") + batteryPercent + "%");
        if (lowBattery) appendLine(details, "⚡ Pin yếu · hãy kết nối bộ sạc");
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
        mediaButton.setTextColor(prefs.getInt("media_color", accent));
        mediaButton.setText(IslandNotificationListener.isMediaPlaying() ? "Ⅱ" : "▶");

        batteryAlertText.setVisibility(lowBattery ? View.VISIBLE : View.GONE);
        if (lowBattery) {
            batteryAlertText.setText("⚡ PIN YẾU  ·  " + batteryPercent + "%  ·  Hãy kết nối bộ sạc");
            int lowColor = prefs.getInt("low_battery_color", 0xFFFF5D73);
            batteryAlertText.setTextColor(Color.WHITE);
            batteryAlertText.setBackground(makeRoundDrawable(lowColor, dp(12), 0, 0));
            if (!wasLowBattery) {
                islandRoot.animate().cancel();
                islandRoot.setScaleX(0.985f);
                islandRoot.setScaleY(0.985f);
                islandRoot.animate().scaleX(1f).scaleY(1f).setDuration(360).start();
            }
        }
        wasLowBattery = lowBattery;

        boolean incomingCall = prefs.getBoolean("show_calls", true) && IslandNotificationListener.isIncomingCall();
        callActions.setVisibility(incomingCall ? View.VISIBLE : View.GONE);
        if (incomingCall) {
            long elapsed = IslandNotificationListener.getCallElapsedSeconds();
            callStatusText.setText(elapsed > 0 ? "Đang gọi · " + formatSeconds(elapsed) : "Cuộc gọi đến");
            answerCallButton.setVisibility(IslandNotificationListener.hasAnswerCallAction() ? View.VISIBLE : View.GONE);
            declineCallButton.setVisibility(IslandNotificationListener.hasDeclineCallAction() ? View.VISIBLE : View.GONE);
            answerCallButton.setEnabled(IslandNotificationListener.hasAnswerCallAction());
            declineCallButton.setEnabled(IslandNotificationListener.hasDeclineCallAction());
            callStatusText.setTextColor(prefs.getInt("call_color", 0xFF76DDAA));
        }

        boolean hasReply = hasNotification && showReply && IslandNotificationListener.hasReplyAction();
        boolean hasDismiss = hasNotification && showDismiss && IslandNotificationListener.hasLatestNotification();
        replyInput.setVisibility(hasReply ? View.VISIBLE : View.GONE);
        replyButton.setVisibility(hasReply ? View.VISIBLE : View.GONE);
        dismissButton.setVisibility(hasDismiss ? View.VISIBLE : View.GONE);
        notificationActions.setVisibility(hasReply || hasDismiss ? View.VISIBLE : View.GONE);
        boolean hasTrackedNotifications = hasNotification && IslandNotificationListener.getNotificationCount() > 0;
        if (clearAllButton != null) clearAllButton.setVisibility(hasTrackedNotifications ? View.VISIBLE : View.GONE);
        if (hasReply) {
            replyButton.setTextColor(Color.WHITE);
            replyButton.setBackground(makeRoundDrawable(accent, dp(10), 0, 0));
        }
        if (hasDismiss) dismissButton.setTextColor(textColor);
        String currentKey = IslandNotificationListener.getLatestKey() + "|" + notification + "|" + notificationText;
        if (!notification.isEmpty() && !currentKey.equals(lastAnimatedNotificationKey)) {
            lastAnimatedNotificationKey = currentKey;
            animateNotificationChange();
        }
        rebuildTopRow();
        applyBackground();
    }

    private void animateNotificationChange() {
        if (topRow == null || !prefs.getBoolean("animate_notifications", true)) return;
        String effect = prefs.getString("notification_effect", "slide");
        topRow.animate().cancel();
        if ("fade".equals(effect)) {
            topRow.setAlpha(0f);
            topRow.animate().alpha(1f).setDuration(260).start();
        } else if ("pop".equals(effect)) {
            topRow.setScaleX(0.88f);
            topRow.setScaleY(0.88f);
            topRow.animate().scaleX(1f).scaleY(1f).setDuration(280).start();
        } else {
            int direction = (IslandNotificationListener.getSelectedNotificationIndex() % 2 == 0) ? 1 : -1;
            topRow.setTranslationX(dp(34 * direction));
            topRow.setAlpha(0.55f);
            topRow.animate().translationX(0f).alpha(1f).setDuration(320).start();
        }
    }

    private String formatCallDuration() {
        return formatSeconds(IslandNotificationListener.getCallElapsedSeconds());
    }

    private String formatSeconds(long seconds) {
        long m = seconds / 60L;
        long s = seconds % 60L;
        long h = m / 60L;
        m %= 60L;
        return h > 0 ? String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
                : String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    private boolean isGameInForeground() {
        long nowCheck = System.currentTimeMillis();
        if (nowCheck - lastGameCheckAt < 5000L) return cachedGameForeground;
        lastGameCheckAt = nowCheck;
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
            if (mode != AppOpsManager.MODE_ALLOWED) return false;
            UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (manager == null) return false;
            long now = System.currentTimeMillis();
            UsageEvents events = manager.queryEvents(now - 60L * 60L * 1000L, now);
            UsageEvents.Event event = new UsageEvents.Event();
            String foregroundPackage = "";
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                boolean foreground = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
                if (Build.VERSION.SDK_INT >= 29) foreground = foreground || type == UsageEvents.Event.ACTIVITY_RESUMED;
                boolean background = type == UsageEvents.Event.MOVE_TO_BACKGROUND;
                if (Build.VERSION.SDK_INT >= 29) background = background || type == UsageEvents.Event.ACTIVITY_PAUSED
                        || type == UsageEvents.Event.ACTIVITY_STOPPED;
                if (foreground) foregroundPackage = event.getPackageName();
                else if (background && event.getPackageName().equals(foregroundPackage)) foregroundPackage = "";
            }
            if (foregroundPackage.isEmpty() || foregroundPackage.equals(getPackageName())) return false;
            android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(foregroundPackage, 0);
            cachedGameForeground = Build.VERSION.SDK_INT >= 26 && info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME;
            return cachedGameForeground;
        } catch (Exception ignored) { cachedGameForeground = false; return false; }
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

    private int blendColors(int base, int overlay, float ratio) {
        float inverse = 1f - ratio;
        int r = Math.round(Color.red(base) * inverse + Color.red(overlay) * ratio);
        int g = Math.round(Color.green(base) * inverse + Color.green(overlay) * ratio);
        int b = Math.round(Color.blue(base) * inverse + Color.blue(overlay) * ratio);
        return Color.rgb(r, g, b);
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
