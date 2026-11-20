package com.ayane.dynamicisland;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.media.AudioManager;
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
import android.view.animation.DecelerateInterpolator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
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
    public static final String ACTION_START = "com.ayane.dynamicisland.START";
    public static final String ACTION_REFRESH = "com.ayane.dynamicisland.REFRESH";
    public static final String ACTION_STOP = "com.ayane.dynamicisland.STOP";
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
    private ImageView senderAvatarView;
    private ImageView mediaArtView;
    private TextView mediaInfoText;
    private TextView notificationCountText;
    private TextView clearAllText;
    private TextView previousNotificationText;
    private TextView nextNotificationText;
    private LinearLayout notificationNavigation;
    private EditText replyInput;
    private Button replyButton;
    private Button dismissButton;
    private SharedPreferences prefs;
    private FloatingOverlayManager floatingOverlayManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean expanded = false;
    private boolean receiverRegistered = false;
    private boolean systemReceiverRegistered = false;
    private boolean keyboardMode = false;
    private boolean islandIdle = false;
    private String transientSystemEvent = "";
    private long systemEventExpiresAt = 0L;
    private String lastIconPackage = "";
    private String lastCustomIconUri = "";
    private float lastTouchX;
    private float lastTouchY;
    private int startWindowX;
    private int startWindowY;
    private boolean dragged;
    private String lastContentSignature = "";

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateContent();
        }
    };

    private final BroadcastReceiver systemEventReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !AudioManager.RINGER_MODE_CHANGED_ACTION.equals(intent.getAction())) return;
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            int mode = audio == null ? AudioManager.RINGER_MODE_NORMAL : audio.getRingerMode();
            String label = mode == AudioManager.RINGER_MODE_SILENT ? "Im lặng"
                    : mode == AudioManager.RINGER_MODE_VIBRATE ? "Rung" : "Chuông";
            transientSystemEvent = "Chế độ âm thanh · " + label;
            systemEventExpiresAt = System.currentTimeMillis() + 4200L;
            updateContent();
            animateContentChange();
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
        floatingOverlayManager = new FloatingOverlayManager(this, windowManager, prefs);
        createNotificationChannel();
        try {
            IntentFilter dataFilter = new IntentFilter(IslandNotificationListener.ACTION_DATA_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(dataReceiver, dataFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(dataReceiver, dataFilter);
            }
            receiverRegistered = true;
        } catch (Exception ignored) { }
        try {
            IntentFilter systemFilter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(systemEventReceiver, systemFilter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(systemEventReceiver, systemFilter);
            systemReceiverRegistered = true;
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
                .setSmallIcon(R.drawable.ic_ayane)
                .setContentTitle("Ayane đang hoạt động")
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
                    "Dịch vụ Ayane", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Thông báo thường trực cho dịch vụ Ayane");
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
        compactText = makeText("Ayane", 13, Color.WHITE, true);
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
        appIconView.setContentDescription("Mở thông báo hiện tại");
        appIconView.setOnClickListener(v -> IslandNotificationListener.openLatest());
        rebuildTopRow();
        islandRoot.addView(topRow, new LinearLayout.LayoutParams(-1, dp(prefs.getInt("height", 62))));

        expandedPanel = new LinearLayout(this);
        expandedPanel.setOrientation(LinearLayout.VERTICAL);
        expandedPanel.setPadding(dp(8), dp(10), dp(8), dp(5));
        LinearLayout notificationHeader = new LinearLayout(this);
        notificationHeader.setOrientation(LinearLayout.HORIZONTAL);
        notificationHeader.setGravity(Gravity.CENTER_VERTICAL);
        senderAvatarView = new ImageView(this);
        senderAvatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        senderAvatarView.setBackground(makeRoundDrawable(0xFF252A36, dp(13), 0x00000000, 0));
        senderAvatarView.setClipToOutline(true);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        avatarLp.setMargins(0, 0, dp(10), 0);
        notificationHeader.addView(senderAvatarView, avatarLp);
        detailsText = makeText("", 12, Color.WHITE, false);
        detailsText.setGravity(Gravity.CENTER_VERTICAL);
        detailsText.setMaxLines(6);
        detailsText.setLineSpacing(dp(2), 1f);
        notificationHeader.addView(detailsText, new LinearLayout.LayoutParams(0, -2, 1));
        expandedPanel.addView(notificationHeader, new LinearLayout.LayoutParams(-1, -2));

        notificationNavigation = new LinearLayout(this);
        notificationNavigation.setOrientation(LinearLayout.HORIZONTAL);
        notificationNavigation.setGravity(Gravity.CENTER_VERTICAL);
        notificationNavigation.setPadding(0, dp(4), 0, dp(2));
        previousNotificationText = makeText("‹", 22, Color.WHITE, true);
        previousNotificationText.setGravity(Gravity.CENTER);
        previousNotificationText.setOnClickListener(v -> {
            IslandNotificationListener.moveSelection(1);
            updateContent();
            animateContentChange();
        });
        notificationCountText = makeText("0 / 0", 10, 0xFFB9BDCA, true);
        notificationCountText.setGravity(Gravity.CENTER);
        clearAllText = makeText("XÓA TẤT CẢ", 9, 0xFFFF8E9B, true);
        clearAllText.setGravity(Gravity.CENTER);
        clearAllText.setPadding(dp(9), dp(7), dp(9), dp(7));
        clearAllText.setBackground(makeRoundDrawable(0x22FF7187, dp(9), 0x55FF7187, dp(1)));
        clearAllText.setOnClickListener(v -> {
            boolean ok = IslandNotificationListener.clearAllNotifications();
            toast(ok ? "Đã xóa các thông báo Ayane đang theo dõi" : "Hãy cấp quyền Truy cập thông báo trước");
            updateContent();
        });
        nextNotificationText = makeText("›", 22, Color.WHITE, true);
        nextNotificationText.setGravity(Gravity.CENTER);
        nextNotificationText.setOnClickListener(v -> {
            IslandNotificationListener.moveSelection(-1);
            updateContent();
            animateContentChange();
        });
        notificationNavigation.addView(previousNotificationText, new LinearLayout.LayoutParams(dp(34), dp(34)));
        notificationNavigation.addView(notificationCountText, new LinearLayout.LayoutParams(0, dp(34), 1));
        notificationNavigation.addView(clearAllText, new LinearLayout.LayoutParams(-2, dp(34)));
        notificationNavigation.addView(nextNotificationText, new LinearLayout.LayoutParams(dp(34), dp(34)));
        expandedPanel.addView(notificationNavigation, new LinearLayout.LayoutParams(-1, -2));

        mediaControls = new LinearLayout(this);
        mediaControls.setOrientation(LinearLayout.HORIZONTAL);
        mediaControls.setGravity(Gravity.CENTER_VERTICAL);
        mediaControls.setPadding(0, dp(6), 0, dp(2));
        mediaArtView = new ImageView(this);
        mediaArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaArtView.setBackground(makeRoundDrawable(0xFF252A36, dp(10), 0x00000000, 0));
        mediaArtView.setClipToOutline(true);
        mediaControls.addView(mediaArtView, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout mediaMeta = new LinearLayout(this);
        mediaMeta.setOrientation(LinearLayout.VERTICAL);
        mediaMeta.setGravity(Gravity.CENTER_VERTICAL);
        mediaMeta.setPadding(dp(8), 0, dp(5), 0);
        mediaInfoText = makeText("Đang phát nhạc", 11, Color.WHITE, true);
        mediaInfoText.setMaxLines(2);
        mediaMeta.addView(mediaInfoText, new LinearLayout.LayoutParams(-1, -2));
        mediaControls.addView(mediaMeta, new LinearLayout.LayoutParams(0, -2, 1));
        previousButton = makeText("|◀", 15, Color.WHITE, true);
        previousButton.setGravity(Gravity.CENTER);
        previousButton.setOnClickListener(v -> {
            IslandNotificationListener.skipToPrevious(this);
            handler.postDelayed(this::updateContent, 350);
        });
        mediaButton = makeText("▶  /  ⏸", 12, prefs.getInt("accent_color", 0xFFD6A3FF), true);
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
        mediaControls.addView(previousButton, new LinearLayout.LayoutParams(dp(34), dp(40)));
        mediaControls.addView(mediaButton, new LinearLayout.LayoutParams(dp(48), dp(40)));
        mediaControls.addView(nextButton, new LinearLayout.LayoutParams(dp(34), dp(40)));
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
        replyButton = actionButton("Trả lời", prefs.getInt("accent_color", 0xFFD6A3FF));
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
                dp(prefs.getInt("width", 250)), dp(prefs.getInt("height", 64)), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        windowParams.x = dp(prefs.getInt("x", 0));
        windowParams.y = dp(prefs.getInt("y", -4));
        windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        applyGlassEffect();
        try {
            windowManager.addView(islandRoot, windowParams);
            applySettings();
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
        boolean allowDrag = prefs.getBoolean("allow_drag", true);
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
                    int minOffsetY = -Math.min(dp(900), metrics.heightPixels / 2);
                    windowParams.x = Math.max(-maxOffsetX, Math.min(maxOffsetX, startWindowX + dx));
                    windowParams.y = Math.max(minOffsetY, Math.min(maxOffsetY, startWindowY + dy));
                    try { windowManager.updateViewLayout(islandRoot, windowParams); } catch (Exception ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                int totalDx = Math.round(event.getRawX() - lastTouchX);
                int totalDy = Math.round(event.getRawY() - lastTouchY);
                if (allowDrag && dragged && Math.abs(totalDy) + Math.abs(totalDx) > dp(18)) {
                    prefs.edit().putInt("x", Math.round(windowParams.x / getResources().getDisplayMetrics().density))
                            .putInt("y", Math.round(windowParams.y / getResources().getDisplayMetrics().density)).apply();
                } else if (Math.abs(totalDx) > dp(38) && IslandNotificationListener.getNotificationCount() > 1) {
                    IslandNotificationListener.moveSelection(totalDx < 0 ? -1 : 1);
                    updateContent();
                    animateContentChange();
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
        int requestedWidth = dp(prefs.getInt("width", 250));
        int requestedExpandedWidth = dp(prefs.getInt("expanded_width", 420));
        int height = dp(prefs.getInt("height", 64));
        int expandedHeight = dp(prefs.getInt("expanded_height", 330));
        boolean idleCompact = islandIdle && prefs.getBoolean("idle_compact", true) && !expanded;
        int effectiveHeight = idleCompact ? dp(Math.max(32, Math.min(72, prefs.getInt("idle_height", 42)))) : height;
        int effectiveWidth = idleCompact ? Math.min(requestedWidth, dp(Math.max(72, Math.min(180, prefs.getInt("idle_width", 104))))) : requestedWidth;
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int maxScreenWidth = Math.max(dp(180), metrics.widthPixels - dp(12));
        windowParams.width = Math.min(expanded ? requestedExpandedWidth : effectiveWidth, maxScreenWidth);
        windowParams.height = expanded ? Math.min(height + expandedHeight, Math.max(height + dp(120), metrics.heightPixels - dp(24))) : effectiveHeight;
        int maxOffsetX = Math.max(0, (metrics.widthPixels - windowParams.width) / 2);
        int minOffsetY = -Math.min(dp(900), metrics.heightPixels / 2);
        int maxOffsetY = Math.max(0, metrics.heightPixels - windowParams.height);
        windowParams.x = Math.max(-maxOffsetX, Math.min(maxOffsetX, dp(prefs.getInt("x", 0))));
        windowParams.y = Math.max(minOffsetY, Math.min(maxOffsetY, dp(prefs.getInt("y", -4))));
        View top = islandRoot.getChildAt(0);
        android.view.ViewGroup.LayoutParams topParams = top.getLayoutParams();
        topParams.height = expanded ? height : effectiveHeight;
        top.setLayoutParams(topParams);
        expandedPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expandedPanel != null) {
            android.view.ViewGroup.LayoutParams panelParams = expandedPanel.getLayoutParams();
            panelParams.height = expanded ? Math.max(0, windowParams.height - dp(prefs.getInt("height", 64))) : -2;
            expandedPanel.setLayoutParams(panelParams);
        }
        if (mediaButton != null) mediaButton.setTextColor(prefs.getInt("media_color", prefs.getInt("accent_color", 0xFFD6A3FF)));
        if (replyButton != null) replyButton.setBackground(makeRoundDrawable(prefs.getInt("reply_color", prefs.getInt("accent_color", 0xFFD6A3FF)), dp(10), 0, 0));
        rebuildTopRow();
        applyTypography(islandRoot);
        applyGlassEffect();
        applyBackground();
        try { windowManager.updateViewLayout(islandRoot, windowParams); } catch (Exception ignored) { }
    }

    private void applyTypography(View view) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView label = (TextView) view;
            label.setTypeface(AyaneTypography.resolve(this, label.getTypeface() != null && label.getTypeface().isBold()));
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTypography(group.getChildAt(i));
        }
    }

    private void applyGlassEffect() {
        if (windowParams == null || Build.VERSION.SDK_INT < 31) return;
        boolean enabled = prefs.getBoolean("glass_effect", true);
        try {
            if (enabled) {
                windowParams.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                int radius = dp(Math.max(0, Math.min(80, prefs.getInt("glass_blur", 26))));
                windowParams.setBlurBehindRadius(radius);
            } else {
                windowParams.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                windowParams.setBlurBehindRadius(0);
            }
        } catch (RuntimeException ignored) { }
    }

    private void applyBackground() {
        if (islandRoot == null) return;
        int bgColor = prefs.getInt("island_bg_color", 0xE605070E);
        int panelColor = prefs.getInt("panel_bg_color", 0xEE171923);
        int textColor = prefs.getInt("text_color", Color.WHITE);
        int radius = prefs.getInt("corner_radius", 38);
        int border = prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFD6A3FF));
        int borderWidth = prefs.getInt("border_width", 1);
        GradientDrawable glassSurface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{blendAlpha(bgColor, 0xFF30384A, 35), bgColor, blendAlpha(bgColor, 0xFF090B12, 12)});
        glassSurface.setCornerRadius(dp(expanded ? Math.max(18, radius - 4) : radius));
        if (borderWidth > 0) glassSurface.setStroke(dp(borderWidth), border);
        islandRoot.setBackground(glassSurface);
        if (expandedPanel != null) {
            GradientDrawable panelSurface = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{blendAlpha(panelColor, Color.WHITE, 8), panelColor});
            panelSurface.setCornerRadius(dp(22));
            panelSurface.setStroke(dp(1), 0x22FFFFFF);
            expandedPanel.setBackground(panelSurface);
        }
        if (compactText != null) compactText.setTextColor(textColor);
        if (detailsText != null) detailsText.setTextColor(textColor);
        if (replyInput != null) replyInput.setTextColor(textColor);
        if (mediaInfoText != null) mediaInfoText.setTextColor(textColor);
        if (senderAvatarView != null) senderAvatarView.setBackground(makeRoundDrawable(
                prefs.getInt("avatar_bg_color", 0xFF252A36), dp(13), 0x00000000, 0));
        if (appIconView != null) appIconView.setBackground(makeRoundDrawable(
                prefs.getInt("avatar_bg_color", 0xFF252A36), dp(8), 0x00000000, 0));
        if (mediaButton != null) mediaButton.setTextColor(prefs.getInt("media_color", prefs.getInt("accent_color", 0xFFD6A3FF)));
    }

    private int blendAlpha(int base, int overlay, int amount) {
        int a = Math.max(0, Math.min(100, amount));
        int r = (Color.red(base) * (100 - a) + Color.red(overlay) * a) / 100;
        int g = (Color.green(base) * (100 - a) + Color.green(overlay) * a) / 100;
        int b = (Color.blue(base) * (100 - a) + Color.blue(overlay) * a) / 100;
        return Color.argb(Color.alpha(base), r, g, b);
    }

    private void updateContent() {
        if (islandRoot == null) return;
        boolean showNotif = prefs.getBoolean("show_notifications", true);
        boolean showAppIcon = prefs.getBoolean("show_source_icon", true);
        boolean showAppName = prefs.getBoolean("show_source_app_name", true);
        boolean showSenderName = prefs.getBoolean("show_sender_name", true);
        boolean showSenderAvatar = prefs.getBoolean("show_sender_avatar", true);
        boolean showReply = prefs.getBoolean("show_reply_actions", true);
        boolean showDismiss = prefs.getBoolean("show_dismiss_action", true);

        IslandNotificationListener.NotificationItem selected = IslandNotificationListener.getSelectedNotification();
        String notification = selected == null ? "" : selected.title;
        String notificationText = selected == null ? "" : selected.text;
        String notificationSender = selected == null ? "" : selected.senderName;
        String notificationPackage = selected == null ? "" : selected.packageName;
        String sourceAppName = selected == null ? "" : selected.appName;
        String customName = prefs.getString("custom_app_name", "").trim();
        long timerEnd = prefs.getLong("timer_end", 0L);
        long remaining = Math.max(0L, timerEnd - System.currentTimeMillis());
        long demoUntil = prefs.getLong("demo_until", 0L);
        boolean demoActive = demoUntil > System.currentTimeMillis();
        int accent = prefs.getInt("accent_color", 0xFFD6A3FF);
        int textColor = prefs.getInt("text_color", Color.WHITE);

        boolean hasCallActivity = showNotif && selected != null && (selected.incomingCall || selected.ongoingCall);
        boolean hasTimerActivity = showNotif && selected != null && selected.timerOrAlarm;
        boolean hasNotification = showNotif && selected != null && !hasCallActivity && !hasTimerActivity;
        IslandNotificationListener.refreshMediaState();
        String activeMediaTitle = IslandNotificationListener.getMediaTitle();
        String activeMediaArtist = IslandNotificationListener.getMediaArtist();
        String activeMediaPackage = IslandNotificationListener.getMediaPackage();
        boolean hasMedia = activeMediaTitle != null && !activeMediaTitle.isEmpty();
        String customIconUri = prefs.getString("custom_icon_uri", "");
        String iconPackage = hasCallActivity || hasTimerActivity ? notificationPackage
                : (hasNotification ? notificationPackage : activeMediaPackage);
        boolean hasIconPackage = showAppIcon && ((!iconPackage.isEmpty()) || (!customIconUri.isEmpty()))
                && (hasNotification || hasCallActivity || hasTimerActivity || hasMedia);
        if (appIconView != null) {
            appIconView.setVisibility(hasIconPackage ? View.VISIBLE : View.GONE);
            if (hasIconPackage && !customIconUri.isEmpty() && !customIconUri.equals(lastCustomIconUri)) {
                Bitmap bitmap = decodeSmallIcon(Uri.parse(customIconUri));
                if (bitmap != null) appIconView.setImageDrawable(new BitmapDrawable(getResources(), bitmap));
                else appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
                lastCustomIconUri = customIconUri;
                lastIconPackage = iconPackage;
            } else if (hasIconPackage && customIconUri.isEmpty()
                    && (!iconPackage.equals(lastIconPackage) || !lastCustomIconUri.isEmpty())) {
                try {
                    Drawable icon = getPackageManager().getApplicationIcon(iconPackage);
                    appIconView.setImageDrawable(icon);
                } catch (Exception e) {
                    appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                lastIconPackage = iconPackage;
                lastCustomIconUri = "";
            } else if (!hasIconPackage) {
                lastIconPackage = "";
                lastCustomIconUri = "";
            }
            appIconView.setContentDescription("Mở thông báo hiện tại");
        appIconView.setOnClickListener(v -> IslandNotificationListener.openLatest());
        }

        String islandLabel = prefs.getString("island_brand_name", "Ayane").trim();
        if (islandLabel.isEmpty()) islandLabel = "Ayane";
        String headline = islandLabel;
        if (demoActive) headline = "Ayane đã sẵn sàng";
        else if (hasCallActivity) headline = (selected.ongoingCall ? "Đang gọi · " : "Cuộc gọi · ")
                + (notificationSender.isEmpty() ? notification : notificationSender);
        else if (hasTimerActivity && selected != null) headline = "⌛ " + trim(notification, 28);
        else if (timerEnd > 0 && remaining > 0) headline = "⌛ " + formatTime(remaining);
        else if (hasMedia) headline = "♫  " + activeMediaTitle;
        else if (hasNotification) {
            String appPart = customName.isEmpty() ? (showAppName ? sourceAppName : "") : customName;
            headline = appPart.isEmpty() ? notification : appPart + " · " + notification;
        } else if (systemEventExpiresAt > System.currentTimeMillis()) headline = transientSystemEvent;
        else if (!customName.isEmpty()) headline = customName;

        boolean idleNow = !demoActive && !hasCallActivity && !hasTimerActivity
                && !(timerEnd > 0 && remaining > 0) && !hasMedia && !hasNotification
                && systemEventExpiresAt <= System.currentTimeMillis();
        boolean idleCompact = idleNow && prefs.getBoolean("idle_compact", true);
        compactText.setVisibility(idleCompact ? View.GONE : View.VISIBLE);
        rightText.setVisibility(idleCompact ? View.GONE : View.VISIBLE);
        if (idleCompact) {
            appIconView.setImageResource(R.mipmap.ic_launcher_round);
            appIconView.setVisibility(View.VISIBLE);
        }
        if (islandIdle != idleNow) {
            islandIdle = idleNow;
            applySettings();
        }
        compactText.setText(trim(headline, 42));
        compactText.setTextColor(textColor);
        if (hasCallActivity) { rightText.setText("☎"); rightText.setTextColor(0xFFFF8798); }
        else if (hasTimerActivity || (timerEnd > 0 && remaining > 0)) { rightText.setText("⌛"); rightText.setTextColor(0xFFFFC46B); }
        else if (hasMedia) { rightText.setText("♫"); rightText.setTextColor(prefs.getInt("media_color", accent)); }
        else if (systemEventExpiresAt > System.currentTimeMillis()) { rightText.setText("◖"); rightText.setTextColor(0xFFFFC2DA); }
        else { rightText.setText("●"); rightText.setTextColor(accent); }

        StringBuilder details = new StringBuilder();
        if (demoActive) details.append("Ayane đang hoạt động\nChạm để mở rộng hoặc thu gọn. Vị trí được chỉnh trong ứng dụng.");
        if (hasCallActivity) appendLine(details,
                (selected != null && selected.ongoingCall ? "Đang gọi · " : "Cuộc gọi đến · ") + notification);
        if (hasTimerActivity && selected != null) {
            if (selected.showChronometer && selected.chronometerWhen > 0) {
                long chrono = selected.chronometerCountDown
                        ? Math.max(0L, selected.chronometerWhen - System.currentTimeMillis())
                        : Math.max(0L, System.currentTimeMillis() - selected.chronometerWhen);
                appendLine(details, (selected.chronometerCountDown ? "Còn " : "Đã chạy ") + formatTime(chrono));
            } else appendLine(details, "Hẹn giờ / báo thức · " + notificationText);
        }
        if (hasMedia) appendLine(details, "♫  " + activeMediaTitle + (activeMediaArtist.isEmpty() ? "" : " · " + activeMediaArtist));
        if (hasNotification) {
            String appPart = customName.isEmpty() ? (showAppName ? sourceAppName : "Thông báo") : customName;
            appendLine(details, "✦  " + (appPart.isEmpty() ? "Thông báo" : appPart));
            if (showSenderName && !notificationSender.isEmpty()) appendLine(details, notificationSender + " · " + notification);
            else appendLine(details, notification);
            if (!notificationText.isEmpty()) appendLine(details, notificationText);
        }
        if (systemEventExpiresAt > System.currentTimeMillis()) appendLine(details, transientSystemEvent);
        else if (!transientSystemEvent.isEmpty()) transientSystemEvent = "";
        if (timerEnd > 0) {
            if (remaining == 0) {
                appendLine(details, "Hẹn giờ đã kết thúc");
                prefs.edit().remove("timer_end").apply();
            } else appendLine(details, "Đếm ngược: " + formatTime(remaining));
        }
        if (details.length() == 0) details.append("Chưa có hoạt động mới. Chạm để xem thêm.");
        detailsText.setText(details.toString());
        detailsText.setTextColor(hasNotification
                ? prefs.getInt("notification_color", prefs.getInt("text_color", Color.WHITE))
                : prefs.getInt("text_color", Color.WHITE));

        if (senderAvatarView != null) {
            boolean hasAvatar = false;
            if (selected != null && selected.avatarIcon != null) {
                try {
                    Drawable avatar = selected.avatarIcon.loadDrawable(this);
                    if (avatar != null) { senderAvatarView.setImageDrawable(avatar); hasAvatar = true; }
                } catch (RuntimeException ignored) { }
            }
            if (!hasAvatar && hasNotification && !notificationPackage.isEmpty()) {
                try { senderAvatarView.setImageDrawable(getPackageManager().getApplicationIcon(notificationPackage)); hasAvatar = true; }
                catch (Exception ignored) { }
            }
            senderAvatarView.setVisibility(showSenderAvatar && hasAvatar ? View.VISIBLE : View.GONE);
        }
        int notificationCount = IslandNotificationListener.getNotificationCount();
        if (notificationNavigation != null) notificationNavigation.setVisibility(hasNotification ? View.VISIBLE : View.GONE);
        if (notificationCountText != null) notificationCountText.setText(notificationCount == 0 ? "0 / 0" : "Thông báo  ·  " + notificationCount);
        if (previousNotificationText != null) previousNotificationText.setVisibility(notificationCount > 1 ? View.VISIBLE : View.GONE);
        if (nextNotificationText != null) nextNotificationText.setVisibility(notificationCount > 1 ? View.VISIBLE : View.GONE);
        if (clearAllText != null) clearAllText.setVisibility(notificationCount > 0 ? View.VISIBLE : View.GONE);

        mediaControls.setVisibility(View.GONE);
        if (mediaArtView != null) {
            Bitmap art = IslandNotificationListener.getMediaArtwork();
            if (art != null) mediaArtView.setImageBitmap(art);
            else {
                try { mediaArtView.setImageDrawable(getPackageManager().getApplicationIcon(IslandNotificationListener.getMediaPackage())); }
                catch (Exception ignored) { mediaArtView.setImageResource(android.R.drawable.ic_media_play); }
            }
        }
        previousButton.setTextColor(textColor);
        nextButton.setTextColor(textColor);
        mediaButton.setTextColor(prefs.getInt("media_color", accent));
        mediaButton.setText(IslandNotificationListener.isMediaPlaying() ? "Ⅱ" : "▶");

        boolean hasReply = hasNotification && showReply && IslandNotificationListener.hasReplyAction();
        boolean hasDismiss = hasNotification && showDismiss && IslandNotificationListener.hasLatestNotification();
        replyInput.setVisibility(hasReply ? View.VISIBLE : View.GONE);
        replyButton.setVisibility(hasReply ? View.VISIBLE : View.GONE);
        dismissButton.setVisibility(hasDismiss ? View.VISIBLE : View.GONE);
        notificationActions.setVisibility(hasReply || hasDismiss ? View.VISIBLE : View.GONE);
        if (hasReply) {
            replyButton.setTextColor(Color.WHITE);
            replyButton.setBackground(makeRoundDrawable(prefs.getInt("reply_color", accent), dp(10), 0, 0));
        }
        if (hasDismiss) dismissButton.setTextColor(textColor);
        rebuildTopRow();
        applyBackground();
        String signature = (notificationPackage + "|" + notification + "|" + notificationText
                + "|" + notificationSender);
        if (!signature.equals(lastContentSignature)) {
            lastContentSignature = signature;
            animateContentChange();
        }
        if (floatingOverlayManager != null) floatingOverlayManager.update();
    }

    private void animateContentChange() {
        if (islandRoot == null || !prefs.getBoolean("animate_notifications", true)) return;
        int mode = prefs.getInt("animation_mode", 1);
        long duration = Math.max(100, Math.min(900, prefs.getInt("animation_duration", 320)));
        islandRoot.animate().cancel();
        islandRoot.setAlpha(1f);
        islandRoot.setTranslationX(0f);
        islandRoot.setScaleX(1f);
        islandRoot.setScaleY(1f);
        if (mode == 0) {
            islandRoot.setAlpha(0.25f);
            islandRoot.animate().alpha(1f).setDuration(duration).setInterpolator(new DecelerateInterpolator()).start();
        } else if (mode == 1) {
            islandRoot.setTranslationX(dp(18));
            islandRoot.setAlpha(0.4f);
            islandRoot.animate().translationX(0f).alpha(1f).setDuration(duration).setInterpolator(new DecelerateInterpolator()).start();
        } else if (mode == 2) {
            islandRoot.setScaleX(0.94f);
            islandRoot.setScaleY(0.94f);
            float tension = Math.max(0.4f, Math.min(2.2f, prefs.getInt("spring_tension", 12) / 10f));
            islandRoot.animate().scaleX(1f).scaleY(1f).setDuration(duration)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(tension)).start();
        }
        if (prefs.getBoolean("haptic_feedback", true)) {
            islandRoot.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private Bitmap decodeSmallIcon(Uri uri) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (Exception ignored) { return null; }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 256) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (Exception ignored) { return null; }
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

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
    }

    private String trim(String value, int max) {
        if (value == null || value.isEmpty()) return prefs.getString("island_brand_name", "Ayane");
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setTypeface(AyaneTypography.resolve(this, bold));
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
        if (floatingOverlayManager != null) {
            floatingOverlayManager.close();
            floatingOverlayManager = null;
        }
        if (receiverRegistered) {
            try { unregisterReceiver(dataReceiver); } catch (Exception ignored) { }
        }
        if (systemReceiverRegistered) {
            try { unregisterReceiver(systemEventReceiver); } catch (Exception ignored) { }
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
