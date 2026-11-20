package com.ayane.dynamicisland;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Handler;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays each notification as its own movable overlay card and the media player
 * as a separate movable overlay. All data remains on-device.
 */
public final class FloatingOverlayManager {
    private static final int MAX_VISIBLE_NOTIFICATIONS = 5;
    private static final int NOTIFICATION_CARD_HEIGHT_DP = 142;
    private static final int NOTIFICATION_GAP_DP = 9;
    private static final String PREFS = "island_settings";

    private final Service service;
    private final Context overlayContext;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, NotificationCard> cards = new LinkedHashMap<>();

    private View headerRoot;
    private WindowManager.LayoutParams headerParams;
    private TextView headerTitle;
    private TextView pagePrevButton;
    private TextView pageNextButton;
    private TextView clearAllButton;
    private int pageOffset = 0;
    private int pageSize = 4;
    private boolean stackAboveHeader = false;
    private String lastNewestKey = "";

    private FrameLayout mediaRoot;
    private ImageView mediaBackdrop;
    private LinearLayout mediaControlsRow;
    private LinearLayout mediaProgressRow;
    private TextView mediaCollapseLabel;
    private WindowManager.LayoutParams mediaParams;
    private ImageView mediaArtwork;
    private TextView mediaTitle;
    private TextView mediaArtist;
    private TextView mediaPlay;
    private TextView mediaOpenApp;
    private LinearLayout mediaExpandedPanel;
    private SeekBar mediaSeekBar;
    private TextView mediaElapsed;
    private TextView mediaTotal;
    private boolean mediaExpanded;
    private boolean mediaSeeking;
    private String lastMediaSignature = "";
    private boolean mediaUserMoved;

    private ActivityOverlay callOverlay;
    private ActivityOverlay timerOverlay;
    private LinearLayout chargingRoot;
    private WindowManager.LayoutParams chargingParams;
    private TextView chargingSummary;
    private TextView chargingDetails;
    private TextView chargingExpand;
    private boolean chargingExpanded;
    private boolean chargingUserMoved;
    private String lastChargingSignature = "";
    private String lastNotificationSignature = "";
    private boolean closed;

    public FloatingOverlayManager(Service service, WindowManager windowManager, SharedPreferences prefs) {
        this.service = service;
        this.overlayContext = AyaneAccessibilityService.instance != null
                ? AyaneAccessibilityService.instance : service;
        this.windowManager = windowManager;
        this.prefs = prefs;
    }

    public void update() {
        if (closed || !android.provider.Settings.canDrawOverlays(service)) {
            removeAll();
            return;
        }
        syncNotifications();
        syncMedia();
        syncCharging();
        applyTypography(headerRoot);
        applyTypography(mediaRoot);
        applyTypography(chargingRoot);
        for (NotificationCard card : cards.values()) applyTypography(card.root);
        if (callOverlay != null) applyTypography(callOverlay.root);
        if (timerOverlay != null) applyTypography(timerOverlay.root);
    }

    private void syncNotifications() {
        // Hành vi mới: thông báo tin nhắn không còn tạo danh sách thẻ ghim trên màn hình.
        // IslandService chỉ bật đảo động trong thời gian heads-up; sau đó tự thu gọn về camera.
        // Cuộc gọi và báo thức vẫn có thẻ hoạt động riêng vì chúng cần tồn tại đến khi kết thúc.
        List<IslandNotificationListener.NotificationItem> snapshot =
                IslandNotificationListener.getNotificationsSnapshot();
        syncSpecialActivities(snapshot);
        if (!cards.isEmpty() || headerRoot != null) removeNotificationOverlays();
    }

    private void ensureHeader() {
        if (headerRoot != null) return;
        LinearLayout row = new LinearLayout(overlayContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), 0, dp(7), 0);
        row.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(22),
                prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFD6A3FF)), dp(1)));
        row.setElevation(dp(10));

        headerTitle = text("AYANE  ·  THÔNG BÁO", 11, prefs.getInt("text_color", Color.WHITE), true);
        headerTitle.setSingleLine(true);
        headerTitle.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(headerTitle, new LinearLayout.LayoutParams(0, -1, 1));

        pagePrevButton = text("‹", 19, prefs.getInt("text_color", Color.WHITE), true);
        pagePrevButton.setGravity(Gravity.CENTER);
        pagePrevButton.setOnClickListener(v -> {
            pageOffset = Math.max(0, pageOffset - pageSize);
            lastNotificationSignature = "";
            update();
        });
        row.addView(pagePrevButton, new LinearLayout.LayoutParams(dp(24), -1));

        pageNextButton = text("›", 19, prefs.getInt("text_color", Color.WHITE), true);
        pageNextButton.setGravity(Gravity.CENTER);
        pageNextButton.setOnClickListener(v -> {
            int total = IslandNotificationListener.getNotificationCount();
            if (pageOffset + pageSize < total) pageOffset += pageSize;
            else pageOffset = 0;
            lastNotificationSignature = "";
            update();
        });
        row.addView(pageNextButton, new LinearLayout.LayoutParams(dp(24), -1));

        clearAllButton = text("XÓA TẤT CẢ", 9, 0xFFFFA1AF, true);
        clearAllButton.setGravity(Gravity.CENTER);
        clearAllButton.setPadding(dp(11), dp(8), dp(11), dp(8));
        clearAllButton.setBackground(surface(0x33FF7187, dp(16), 0x66FF7187, dp(1)));
        headerTitle.setOnClickListener(v -> {
            android.content.Intent open = new android.content.Intent(service, MainActivity.class);
            open.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            try { service.startActivity(open); } catch (RuntimeException ignored) { }
        });
        clearAllButton.setOnClickListener(v -> {
            boolean ok = IslandNotificationListener.clearAllNotifications();
            toast(ok ? "Đã xóa các thông báo Ayane đang hiển thị" : "Hãy cấp quyền Truy cập thông báo");
            update();
        });
        row.addView(clearAllButton, new LinearLayout.LayoutParams(-2, dp(34)));
        row.setOnTouchListener((v, event) -> handleHeaderTouch(event));

        headerRoot = row;
        headerParams = overlayParams(cardWidth(), dp(42));
        headerParams.x = dp(prefs.getInt("floating_x", 12));
        headerParams.y = dp(prefs.getInt("floating_y", 96));
        try {
            windowManager.addView(headerRoot, headerParams);
        } catch (RuntimeException error) {
            headerRoot = null;
            headerParams = null;
        }
    }

    private void updateHeaderCount(int total) {
        if (headerTitle != null) {
            int start = total == 0 ? 0 : Math.min(pageOffset + 1, total);
            int end = Math.min(pageOffset + pageSize, total);
            headerTitle.setText(total == 0 ? "AYANE  ·  THÔNG BÁO" :
                    "AYANE  ·  " + start + "–" + end + " / " + total);
            headerTitle.setTextColor(prefs.getInt("text_color", Color.WHITE));
        }
        if (pagePrevButton != null) pagePrevButton.setVisibility(pageOffset > 0 ? View.VISIBLE : View.GONE);
        if (pageNextButton != null) pageNextButton.setVisibility(pageOffset + pageSize < total ? View.VISIBLE : View.GONE);
        if (clearAllButton != null) {
            clearAllButton.setVisibility(prefs.getBoolean("floating_clear_button", true) ? View.VISIBLE : View.GONE);
        }
    }

    private boolean handleHeaderTouch(MotionEvent event) {
        if (headerParams == null || headerRoot == null) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragDownX = event.getRawX();
                dragDownY = event.getRawY();
                dragStartX = headerParams.x;
                dragStartY = headerParams.y;
                dragMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - dragDownX);
                int dy = Math.round(event.getRawY() - dragDownY);
                if (Math.abs(dx) + Math.abs(dy) > dp(6)) dragMoved = true;
                if (dragMoved) {
                    headerParams.x = clamp(dragStartX + dx, 0, screenWidth() - headerParams.width);
                    headerParams.y = clamp(dragStartY + dy, dp(24), screenHeight() - dp(48));
                    try { windowManager.updateViewLayout(headerRoot, headerParams); } catch (RuntimeException ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragMoved) {
                    prefs.edit().putInt("floating_x", px(headerParams.x))
                            .putInt("floating_y", px(headerParams.y)).apply();
                    lastNotificationSignature = "";
                    update();
                    updateMediaAvoidingCards();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return true;
    }

    private float dragDownX;
    private float dragDownY;
    private int dragStartX;
    private int dragStartY;
    private boolean dragMoved;

    private void repositionUnmovedCards() {
        int index = 0;
        for (NotificationCard card : cards.values()) {
            if (!card.userMoved) card.placeAtIndex(index);
            index++;
        }
    }

    private void removeNotificationOverlays() {
        for (NotificationCard card : cards.values()) card.detach();
        cards.clear();
        if (headerRoot != null) {
            try { windowManager.removeView(headerRoot); } catch (RuntimeException ignored) { }
            headerRoot = null;
            headerParams = null;
        }
        lastNotificationSignature = "";
    }

    private void syncMedia() {
        boolean enabled = prefs.getBoolean("floating_media_card", true)
                && prefs.getBoolean("show_media", true);
        String title = IslandNotificationListener.getMediaTitle();
        String artist = IslandNotificationListener.getMediaArtist();
        String pkg = IslandNotificationListener.getMediaPackage();
        boolean playing = IslandNotificationListener.isMediaPlaying();
        Bitmap artwork = IslandNotificationListener.getMediaArtwork();
        long duration = IslandNotificationListener.getMediaDuration();
        if (!enabled || TextUtils.isEmpty(title)) {
            removeMedia();
            lastMediaSignature = "";
            return;
        }
        boolean expandedEnabled = prefs.getBoolean("expanded_media_card_enabled", true);
        if (!expandedEnabled) mediaExpanded = false;
        String sig = title + "|" + artist + "|" + pkg + "|" + playing
                + "|expanded=" + expandedEnabled
                + "|duration=" + duration
                + "|art=" + (artwork == null ? 0 : artwork.getGenerationId())
                + "|pos=" + prefs.getInt("media_floating_x", 12) + ":" + prefs.getInt("media_floating_y", -1)
                + "|avoid=" + prefs.getBoolean("auto_avoid_overlap", true)
                + "|blur=" + prefs.getBoolean("media_blur_enabled", true) + ":" + prefs.getInt("media_blur_radius", 22)
                + "|layers=" + prefs.getInt("media_layout_order", 0)
                + "|font=" + prefs.getString("font_family", "rounded")
                + "|colors=" + prefs.getInt("panel_bg_color", 0xF0181C27) + ":"
                + prefs.getInt("border_color", 0xFFD6A3FF) + ":"
                + prefs.getInt("text_color", Color.WHITE) + ":"
                + prefs.getInt("secondary_text_color", 0xFFAAAEBB) + ":"
                + prefs.getInt("media_color", 0xFFD6A3FF) + ":"
                + prefs.getInt("avatar_bg_color", 0xFF252A36) + ":"
                + prefs.getBoolean("expanded_media_card_enabled", true);
        if (sig.equals(lastMediaSignature) && mediaRoot != null) {
            updateMediaProgress();
            updateMediaAvoidingCards();
            return;
        }
        lastMediaSignature = sig;
        ensureMediaCard();
        if (mediaRoot == null) return;
        mediaRoot.setBackground(mediaCardBackground());
        reorderMediaLayers();
        applyMediaBackdropEffect();
        mediaTitle.setText(title);
        mediaTitle.setTextColor(prefs.getInt("text_color", Color.WHITE));
        mediaArtist.setText(artist.isEmpty()
                ? (pkg.isEmpty() ? "ĐANG PHÁT" : "ĐANG PHÁT · " + resolveMediaAppName(pkg))
                : artist);
        mediaArtist.setTextColor(prefs.getInt("secondary_text_color", 0xFFAAAEBB));
        mediaPlay.setText(playing ? "Ⅱ" : "▶");
        mediaPlay.setTextColor(prefs.getInt("media_color", prefs.getInt("accent_color", 0xFFD6A3FF)));
        if (mediaSeekBar != null) {
            int mediaTint = prefs.getInt("media_color", prefs.getInt("accent_color", 0xFFD6A3FF));
            mediaSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(mediaTint));
            mediaSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(mediaTint));
        }
        if (mediaOpenApp != null) mediaOpenApp.setTextColor(prefs.getInt("text_color", Color.WHITE));
        if (artwork != null) mediaArtwork.setImageBitmap(artwork);
        else {
            try { mediaArtwork.setImageDrawable(service.getPackageManager().getApplicationIcon(pkg)); }
            catch (Exception ignored) { mediaArtwork.setImageResource(android.R.drawable.ic_media_play); }
        }
        if (mediaBackdrop != null) {
            if (artwork != null) mediaBackdrop.setImageBitmap(artwork);
            else if (mediaArtwork.getDrawable() != null) mediaBackdrop.setImageDrawable(mediaArtwork.getDrawable());
            applyMediaBackdropEffect();
        }
        if (mediaOpenApp != null) {
            mediaOpenApp.setVisibility(pkg.isEmpty() ? View.GONE : View.VISIBLE);
            mediaOpenApp.setOnClickListener(v -> openMediaApp(pkg));
        }
        updateMediaLayout();
        updateMediaProgress();
        updateMediaAvoidingCards();
    }

    private void ensureMediaCard() {
        if (mediaRoot != null) return;
        FrameLayout root = new FrameLayout(overlayContext);
        root.setElevation(dp(18));
        root.setClipToOutline(true);
        root.setBackground(mediaCardBackground());

        mediaBackdrop = new ImageView(overlayContext);
        mediaBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaBackdrop.setAlpha(0.36f);
        FrameLayout.LayoutParams backdropLp = new FrameLayout.LayoutParams(-1, -1);
        root.addView(mediaBackdrop, backdropLp);
        View tint = new View(overlayContext);
        GradientDrawable tintGradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99070A14, 0xBB0B1424, 0xE6101B2C});
        tintGradient.setCornerRadius(dp(26));
        tint.setBackground(tintGradient);
        root.addView(tint, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(overlayContext);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(11));
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(-1, -1);
        root.addView(content, contentLp);

        LinearLayout header = new LinearLayout(overlayContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        mediaArtwork = new ImageView(overlayContext);
        mediaArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaArtwork.setBackground(surface(prefs.getInt("avatar_bg_color", 0xFF252A36), dp(18), 0, 0));
        mediaArtwork.setClipToOutline(true);
        header.addView(mediaArtwork, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout labels = new LinearLayout(overlayContext);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        mediaTitle = text("Đang phát", 18, Color.WHITE, true);
        mediaTitle.setSingleLine(true);
        mediaTitle.setEllipsize(TextUtils.TruncateAt.END);
        mediaArtist = text("ĐANG PHÁT", 12, 0xFFDDD6E7, false);
        mediaArtist.setSingleLine(true);
        mediaArtist.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(mediaTitle);
        labels.addView(mediaArtist, new LinearLayout.LayoutParams(-1, -2));
        labels.setOnTouchListener((v, e) -> handleMediaDrag(e));
        header.addView(labels, new LinearLayout.LayoutParams(0, dp(60), 1));
        mediaOpenApp = text("⤴", 23, 0xFFD9DEEA, true);
        mediaOpenApp.setGravity(Gravity.CENTER);
        mediaOpenApp.setContentDescription("Mở ứng dụng đang phát");
        mediaOpenApp.setBackground(surface(0x332C3545, dp(18), 0x667C7F9C, dp(1)));
        mediaOpenApp.setOnClickListener(v -> openMediaApp(IslandNotificationListener.getMediaPackage()));
        header.addView(mediaOpenApp, new LinearLayout.LayoutParams(dp(42), dp(42)));
        mediaArtwork.setOnClickListener(v -> toggleMediaExpanded());
        header.setOnClickListener(v -> toggleMediaExpanded());
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));

        mediaExpandedPanel = new LinearLayout(overlayContext);
        mediaExpandedPanel.setOrientation(LinearLayout.VERTICAL);
        mediaExpandedPanel.setPadding(0, dp(8), 0, 0);

        mediaControlsRow = new LinearLayout(overlayContext);
        mediaControlsRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaControlsRow.setGravity(Gravity.CENTER);
        TextView previous = text("|◀", 20, Color.WHITE, true);
        previous.setGravity(Gravity.CENTER);
        previous.setOnClickListener(v -> {
            IslandNotificationListener.skipToPrevious(service);
            handler.postDelayed(this::syncMedia, 300);
        });
        mediaPlay = text("Ⅱ", 27, prefs.getInt("media_color", 0xFFD6A3FF), true);
        mediaPlay.setGravity(Gravity.CENTER);
        mediaPlay.setBackground(surface(0x332B2640, dp(22), 0x665F4C83, dp(1)));
        mediaPlay.setOnClickListener(v -> {
            IslandNotificationListener.togglePlayback(service);
            handler.postDelayed(this::syncMedia, 250);
        });
        TextView next = text("▶|", 20, Color.WHITE, true);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(v -> {
            IslandNotificationListener.skipToNext(service);
            handler.postDelayed(this::syncMedia, 300);
        });
        mediaControlsRow.addView(previous, new LinearLayout.LayoutParams(dp(76), dp(52)));
        mediaControlsRow.addView(mediaPlay, new LinearLayout.LayoutParams(dp(64), dp(48)));
        mediaControlsRow.addView(next, new LinearLayout.LayoutParams(dp(76), dp(52)));

        mediaProgressRow = new LinearLayout(overlayContext);
        mediaProgressRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaProgressRow.setGravity(Gravity.CENTER_VERTICAL);
        mediaElapsed = text("00:00", 9, 0xFFE0DCEC, true);
        mediaTotal = text("00:00", 9, 0xFFE0DCEC, true);
        mediaProgressRow.addView(mediaElapsed, new LinearLayout.LayoutParams(dp(42), dp(24)));
        mediaSeekBar = new SeekBar(overlayContext);
        mediaSeekBar.setMax(1000);
        mediaSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(
                prefs.getInt("media_color", prefs.getInt("accent_color", 0xFFD6A3FF))));
        mediaSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(
                prefs.getInt("media_color", prefs.getInt("accent_color", 0xFFD6A3FF))));
        mediaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaTotal != null) {
                    long duration = IslandNotificationListener.getMediaDuration();
                    mediaElapsed.setText(formatMediaTime(duration * progress / 1000L));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { mediaSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                mediaSeeking = false;
                long duration = IslandNotificationListener.getMediaDuration();
                if (duration > 0) IslandNotificationListener.seekMedia(service, duration * seekBar.getProgress() / 1000L);
            }
        });
        mediaProgressRow.addView(mediaSeekBar, new LinearLayout.LayoutParams(0, dp(24), 1));
        mediaProgressRow.addView(mediaTotal, new LinearLayout.LayoutParams(dp(42), dp(24)));
        mediaCollapseLabel = text("⌄  THU GỌN", 9, prefs.getInt("media_color", 0xFFD6A3FF), true);
        mediaCollapseLabel.setGravity(Gravity.CENTER);
        mediaCollapseLabel.setOnClickListener(v -> toggleMediaExpanded());
        reorderMediaLayers();
        content.addView(mediaExpandedPanel, new LinearLayout.LayoutParams(-1, -2));

        mediaRoot = root;
        mediaParams = overlayParams(mediaExpanded ? mediaExpandedWidth() : mediaCompactWidth(), dp(mediaCardHeightDp()));
        mediaParams.x = (screenWidth() - mediaParams.width) / 2;
        mediaParams.y = clamp(dp(prefs.getInt("media_floating_y", px(screenHeight() - dp(130)))),
                dp(24), Math.max(dp(24), screenHeight() - mediaParams.height - dp(24)));
        try { windowManager.addView(mediaRoot, mediaParams); }
        catch (RuntimeException error) { mediaRoot = null; mediaParams = null; }
        applyMediaBackdropEffect();
        updateMediaLayout();
    }

    private void reorderMediaLayers() {
        if (mediaExpandedPanel == null || mediaControlsRow == null || mediaProgressRow == null || mediaCollapseLabel == null) return;
        mediaExpandedPanel.removeAllViews();
        if (prefs.getInt("media_layout_order", 0) == 1) {
            mediaExpandedPanel.addView(mediaProgressRow, new LinearLayout.LayoutParams(-1, dp(30)));
            mediaExpandedPanel.addView(mediaControlsRow, new LinearLayout.LayoutParams(-1, dp(56)));
        } else {
            mediaExpandedPanel.addView(mediaControlsRow, new LinearLayout.LayoutParams(-1, dp(56)));
            mediaExpandedPanel.addView(mediaProgressRow, new LinearLayout.LayoutParams(-1, dp(30)));
        }
        mediaExpandedPanel.addView(mediaCollapseLabel, new LinearLayout.LayoutParams(-1, dp(26)));
    }

    private void applyMediaBackdropEffect() {
        if (mediaBackdrop == null) return;
        boolean blur = prefs.getBoolean("media_blur_enabled", true);
        int radius = Math.max(0, Math.min(48, prefs.getInt("media_blur_radius", 22)));
        if (Build.VERSION.SDK_INT >= 31) {
            if (blur && radius > 0) {
                mediaBackdrop.setRenderEffect(RenderEffect.createBlurEffect(dp(radius), dp(radius), Shader.TileMode.CLAMP));
            } else {
                mediaBackdrop.setRenderEffect(null);
            }
        }
        mediaBackdrop.setAlpha(blur ? 0.42f : 0.10f);
    }

    private void toggleMediaExpanded() {
        if (!prefs.getBoolean("expanded_media_card_enabled", true)) return;
        mediaExpanded = !mediaExpanded;
        updateMediaLayout();
        updateMediaProgress();
        updateMediaAvoidingCards();
    }

    private int mediaCompactWidth() { return Math.min(dp(340), Math.max(dp(240), screenWidth() - dp(24))); }
    private int mediaExpandedWidth() { return Math.min(dp(620), Math.max(dp(260), screenWidth() - dp(24))); }
    private int mediaCardHeightDp() {
        return mediaExpanded && prefs.getBoolean("expanded_media_card_enabled", true) ? 258 : 82;
    }

    private void updateMediaLayout() {
        if (mediaRoot == null || mediaParams == null) return;
        boolean canExpand = prefs.getBoolean("expanded_media_card_enabled", true);
        mediaExpandedPanel.setVisibility(mediaExpanded && canExpand ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams artLp = (LinearLayout.LayoutParams) mediaArtwork.getLayoutParams();
        int artSize = mediaExpanded && canExpand ? 92 : 52;
        artLp.width = dp(artSize);
        artLp.height = dp(artSize);
        mediaArtwork.setLayoutParams(artLp);
        LinearLayout header = (LinearLayout) mediaArtwork.getParent();
        View labels = header.getChildAt(1);
        LinearLayout.LayoutParams labelsLp = (LinearLayout.LayoutParams) labels.getLayoutParams();
        labelsLp.height = dp(artSize);
        labels.setLayoutParams(labelsLp);
        int oldWidth = mediaParams.width;
        mediaParams.width = mediaExpanded && canExpand ? mediaExpandedWidth() : mediaCompactWidth();
        mediaParams.height = dp(mediaCardHeightDp());
        if (!mediaUserMoved || mediaParams.width != oldWidth) {
            mediaParams.x = (screenWidth() - mediaParams.width) / 2;
        }
        mediaParams.x = clamp(mediaParams.x, 0, Math.max(0, screenWidth() - mediaParams.width));
        mediaParams.y = clamp(mediaParams.y, dp(24), Math.max(dp(24), screenHeight() - mediaParams.height - dp(24)));
        try { windowManager.updateViewLayout(mediaRoot, mediaParams); } catch (RuntimeException ignored) { }
    }

    private GradientDrawable mediaCardBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF03050B, 0xFF07182B, 0xFF102F50});
        drawable.setCornerRadius(dp(26));
        drawable.setStroke(dp(1), prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFD6A3FF)));
        return drawable;
    }

    private String resolveMediaAppName(String packageName) {
        try {
            CharSequence label = service.getPackageManager().getApplicationLabel(
                    service.getPackageManager().getApplicationInfo(packageName, 0));
            return label == null ? packageName : label.toString();
        } catch (Exception ignored) { return packageName; }
    }

    private void openMediaApp(String packageName) {
        if (TextUtils.isEmpty(packageName)) return;
        try {
            Intent launch = service.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) { toast("Ứng dụng phát không cung cấp màn hình mở"); return; }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            service.startActivity(launch);
        } catch (RuntimeException error) {
            toast("Không thể mở ứng dụng phát");
        }
    }

    private void updateMediaProgress() {
        if (mediaSeekBar == null) return;
        long duration = IslandNotificationListener.getMediaDuration();
        long position = IslandNotificationListener.getMediaPosition();
        boolean seekable = duration > 0;
        mediaSeekBar.setEnabled(seekable);
        mediaSeekBar.setVisibility(seekable && mediaExpanded ? View.VISIBLE : View.GONE);
        if (mediaProgressRow != null) mediaProgressRow.setVisibility(seekable ? View.VISIBLE : View.GONE);
        if (seekable && !mediaSeeking) {
            mediaSeekBar.setProgress((int) Math.min(1000L, position * 1000L / duration));
        }
        if (mediaElapsed != null && !mediaSeeking) mediaElapsed.setText(formatMediaTime(position));
        if (mediaTotal != null) mediaTotal.setText(formatMediaTime(duration));
    }

    private String formatMediaTime(long ms) {
        long seconds = Math.max(0, ms) / 1000L;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private boolean handleMediaDrag(MotionEvent event) {
        if (mediaParams == null || mediaRoot == null) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragDownX = event.getRawX();
                dragDownY = event.getRawY();
                dragStartX = mediaParams.x;
                dragStartY = mediaParams.y;
                dragMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - dragDownX);
                int dy = Math.round(event.getRawY() - dragDownY);
                if (Math.abs(dx) + Math.abs(dy) > dp(7)) dragMoved = true;
                if (dragMoved) {
                    mediaParams.x = clamp(dragStartX + dx, 0, screenWidth() - mediaParams.width);
                    mediaParams.y = clamp(dragStartY + dy, dp(24), Math.max(dp(24), screenHeight() - mediaParams.height));
                    try { windowManager.updateViewLayout(mediaRoot, mediaParams); } catch (RuntimeException ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragMoved) {
                    mediaUserMoved = true;
                    updateMediaAvoidingCards();
                    prefs.edit().putInt("media_floating_x", px(mediaParams.x))
                            .putInt("media_floating_y", px(mediaParams.y)).apply();
                } else {
                    toggleMediaExpanded();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return true;
    }

    private void updateMediaAvoidingCards() {
        if (mediaRoot == null || mediaParams == null) return;
        if (!prefs.getBoolean("auto_avoid_overlap", true)) {
            mediaRoot.setVisibility(View.VISIBLE);
            return;
        }

        int preferredX = clamp(mediaParams.x, 0, screenWidth() - mediaParams.width);
        int preferredY = mediaUserMoved ? mediaParams.y : screenHeight() - dp(mediaCardHeightDp() + 18);
        int minY = dp(24);
        int maxY = Math.max(minY, screenHeight() - dp(mediaCardHeightDp()));
        int step = Math.max(dp(12), 1);
        boolean placed = false;
        int chosenX = preferredX;
        int chosenY = clamp(preferredY, minY, maxY);

        for (int y = chosenY; y >= minY && !placed; y -= step) {
            if (isMediaPositionFree(preferredX, y)) { chosenY = y; placed = true; }
        }
        for (int y = chosenY + step; y <= maxY && !placed; y += step) {
            if (isMediaPositionFree(preferredX, y)) { chosenY = y; placed = true; }
        }
        if (!placed) {
            int maxX = Math.max(0, screenWidth() - mediaParams.width);
            for (int x = 0; x <= maxX && !placed; x += Math.max(dp(16), 1)) {
                for (int y = minY; y <= maxY && !placed; y += step) {
                    if (isMediaPositionFree(x, y)) { chosenX = x; chosenY = y; placed = true; }
                }
            }
        }
        mediaRoot.setVisibility(placed ? View.VISIBLE : View.GONE);
        if (!placed) return;
        mediaParams.x = chosenX;
        mediaParams.y = chosenY;
        try { windowManager.updateViewLayout(mediaRoot, mediaParams); } catch (RuntimeException ignored) { }
    }

    private boolean isMediaPositionFree(int x, int y) {
        int height = dp(mediaCardHeightDp());
        int maxY = screenHeight() - height;
        if (x < 0 || x + mediaParams.width > screenWidth() || y < dp(24) || y > maxY) return false;
        for (NotificationCard card : cards.values()) {
            if (overlap(x, y, mediaParams.width, height,
                    card.params.x, card.params.y, card.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP))) return false;
        }
        if (headerParams != null && overlap(x, y, mediaParams.width, height,
                headerParams.x, headerParams.y, headerParams.width, dp(42))) return false;
        if (callOverlay != null && callOverlay.isVisible() && overlap(x, y, mediaParams.width, height,
                callOverlay.params.x, callOverlay.params.y, callOverlay.params.width, callOverlay.params.height)) return false;
        if (timerOverlay != null && timerOverlay.isVisible() && overlap(x, y, mediaParams.width, height,
                timerOverlay.params.x, timerOverlay.params.y, timerOverlay.params.width, timerOverlay.params.height)) return false;
        if (chargingRoot != null && chargingRoot.getVisibility() == View.VISIBLE && chargingParams != null
                && overlap(x, y, mediaParams.width, height, chargingParams.x, chargingParams.y,
                chargingParams.width, chargingParams.height)) return false;
        return true;
    }

    private void removeMedia() {
        if (mediaRoot != null) {
            try { windowManager.removeView(mediaRoot); } catch (RuntimeException ignored) { }
        }
        mediaRoot = null;
        mediaParams = null;
        mediaBackdrop = null;
        mediaControlsRow = null;
        mediaProgressRow = null;
        mediaCollapseLabel = null;
        mediaArtwork = null;
        mediaTitle = null;
        mediaArtist = null;
        mediaPlay = null;
        mediaOpenApp = null;
        mediaExpandedPanel = null;
        mediaSeekBar = null;
        mediaElapsed = null;
        mediaTotal = null;
        mediaExpanded = false;
        mediaSeeking = false;
        mediaUserMoved = false;
    }

    private void syncSpecialActivities(List<IslandNotificationListener.NotificationItem> snapshot) {
        IslandNotificationListener.NotificationItem call = null;
        IslandNotificationListener.NotificationItem timer = null;
        for (IslandNotificationListener.NotificationItem item : snapshot) {
            if (call == null && (item.incomingCall || item.ongoingCall)
                    && prefs.getBoolean("show_call_activity", true)) call = item;
            if (timer == null && item.timerOrAlarm && prefs.getBoolean("show_timer_activity", true)) timer = item;
            if (call != null && timer != null) break;
        }
        syncActivityOverlay(true, call);
        syncActivityOverlay(false, timer);
    }

    private void syncActivityOverlay(boolean isCall, IslandNotificationListener.NotificationItem item) {
        ActivityOverlay overlay = isCall ? callOverlay : timerOverlay;
        if (item == null) {
            if (overlay != null) overlay.detach();
            if (isCall) callOverlay = null; else timerOverlay = null;
            return;
        }
        if (overlay == null || !overlay.item.key.equals(item.key)) {
            if (overlay != null) overlay.detach();
            overlay = new ActivityOverlay(item, isCall);
            if (isCall) callOverlay = overlay; else timerOverlay = overlay;
            overlay.attach();
        } else {
            overlay.bind(item);
        }
        overlay.updateTimerText();
        if (!overlay.userMoved) overlay.place();
    }

    private class ActivityOverlay {
        IslandNotificationListener.NotificationItem item;
        final boolean isCall;
        final LinearLayout root;
        final ImageView avatar;
        final TextView overline;
        final TextView title;
        final TextView body;
        final LinearLayout actions;
        final WindowManager.LayoutParams params;
        boolean userMoved;
        float downX, downY;
        int startX, startY;
        boolean moved;

        ActivityOverlay(IslandNotificationListener.NotificationItem item, boolean isCall) {
            this.item = item;
            this.isCall = isCall;
            root = new LinearLayout(overlayContext);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(13), dp(10), dp(13), dp(10));
            root.setElevation(dp(16));
            root.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(22),
                    prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFD6A3FF)), dp(1)));
            LinearLayout header = new LinearLayout(overlayContext);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            avatar = new ImageView(overlayContext);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackground(surface(prefs.getInt("avatar_bg_color", 0xFF252A36), dp(18), 0, 0));
            avatar.setClipToOutline(true);
            header.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout labels = new LinearLayout(overlayContext);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            labels.setPadding(dp(10), 0, 0, 0);
            overline = text(isCall ? "CUỘC GỌI ĐẾN" : "HẸN GIỜ / BÁO THỨC", 9,
                    prefs.getInt("accent_color", 0xFFD6A3FF), true);
            title = text("", 15, Color.WHITE, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            body = text("", 11, 0xFFB9BECA, false);
            body.setMaxLines(2);
            body.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(overline);
            labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
            labels.addView(body, new LinearLayout.LayoutParams(-1, -2));
            header.addView(labels, new LinearLayout.LayoutParams(0, dp(48), 1));
            header.setOnTouchListener(this::handleTouch);
            root.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));

            actions = new LinearLayout(overlayContext);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            if (isCall) {
                TextView decline = button("TỪ CHỐI", 0xFFE05267);
                decline.setOnClickListener(v -> {
                    boolean sent = IslandNotificationListener.declineCall(this.item.key);
                    if (!sent) toast("Ứng dụng gọi chưa cung cấp thao tác từ chối cuộc gọi");
                    handler.postDelayed(FloatingOverlayManager.this::update, 450);
                });
                TextView answer = button("NGHE", 0xFF2CBF86);
                answer.setOnClickListener(v -> {
                    boolean sent = IslandNotificationListener.answerCall(this.item.key);
                    if (!sent) toast("Ứng dụng gọi chưa cung cấp thao tác nghe cuộc gọi");
                    handler.postDelayed(FloatingOverlayManager.this::update, 450);
                });
                TextView hangUp = button("KẾT THÚC", 0xFFE05267);
                hangUp.setOnClickListener(v -> {
                    boolean sent = IslandNotificationListener.hangUpCall(this.item.key);
                    if (!sent) toast("Ứng dụng gọi chưa cung cấp thao tác kết thúc cuộc gọi");
                    handler.postDelayed(FloatingOverlayManager.this::update, 450);
                });
                actions.addView(decline, new LinearLayout.LayoutParams(0, dp(34), 1));
                LinearLayout.LayoutParams answerLp = new LinearLayout.LayoutParams(0, dp(34), 1);
                answerLp.setMargins(dp(8), 0, 0, 0);
                actions.addView(answer, answerLp);
                LinearLayout.LayoutParams hangUpLp = new LinearLayout.LayoutParams(0, dp(34), 1);
                hangUpLp.setMargins(dp(8), 0, 0, 0);
                actions.addView(hangUp, hangUpLp);
            } else {
                TextView open = button("MỞ ĐỒNG HỒ", prefs.getInt("accent_color", 0xFFD6A3FF));
                open.setOnClickListener(v -> IslandNotificationListener.openNotification(this.item.key));
                actions.addView(open, new LinearLayout.LayoutParams(-2, dp(32)));
                TextView timerAction = button(item.timerActionTitle.isEmpty() ? "BỎ QUA" : item.timerActionTitle,
                        0xFF3A4150);
                timerAction.setOnClickListener(v -> {
                    if (!IslandNotificationListener.performTimerAction(this.item.key)) {
                        toast("Thông báo đồng hồ không cung cấp thao tác này");
                    }
                    handler.postDelayed(FloatingOverlayManager.this::update, 400);
                });
                if (item.timerActionIntent != null) {
                    LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-2, dp(32));
                    actionLp.setMargins(dp(7), 0, 0, 0);
                    actions.addView(timerAction, actionLp);
                }
            }
            root.addView(actions, new LinearLayout.LayoutParams(-1, dp(36)));
            int height = isCall ? 108 : 110;
            params = overlayParams(cardWidth(), dp(height));
            bind(item);
        }

        void attach() {
            place();
        }

        void detach() {
            try { windowManager.removeView(root); } catch (RuntimeException ignored) { }
        }

        boolean isVisible() { return root.getVisibility() == View.VISIBLE && root.getParent() != null; }

        void bind(IslandNotificationListener.NotificationItem next) {
            item = next;
            title.setText(isCall
                    ? (!next.senderName.isEmpty() ? next.senderName : next.title)
                    : (next.title == null || next.title.isEmpty() ? "Đang đếm giờ" : next.title));
            body.setText(isCall
                    ? (next.text == null || next.text.isEmpty() ? next.appName : next.text)
                    : timerBody(next));
            overline.setText(isCall
                    ? (next.ongoingCall ? "ĐANG GỌI" : "CUỘC GỌI ĐẾN")
                    : ("alarm".equals(next.category) ? "BÁO THỨC" : "HẸN GIỜ"));
            boolean loaded = false;
            if (next.avatarIcon != null) {
                try {
                    Drawable icon = next.avatarIcon.loadDrawable(service);
                    if (icon != null) { avatar.setImageDrawable(icon); loaded = true; }
                } catch (RuntimeException ignored) { }
            }
            if (!loaded) {
                try { avatar.setImageDrawable(service.getPackageManager().getApplicationIcon(next.packageName)); }
                catch (Exception ignored) { avatar.setImageResource(isCall
                        ? android.R.drawable.sym_call_incoming : android.R.drawable.sym_def_app_icon); }
            }
            overline.setTextColor(isCall ? 0xFFFF8897 : prefs.getInt("accent_color", 0xFFD6A3FF));
            title.setTextColor(prefs.getInt("text_color", Color.WHITE));
            body.setTextColor(prefs.getInt("secondary_text_color", 0xFFAAAEBB));
            if (isCall && actions.getChildCount() >= 3) {
                actions.getChildAt(0).setVisibility(!next.ongoingCall && next.declineIntent != null ? View.VISIBLE : View.GONE);
                actions.getChildAt(1).setVisibility(!next.ongoingCall && next.answerIntent != null ? View.VISIBLE : View.GONE);
                actions.getChildAt(2).setVisibility(next.ongoingCall && next.hangUpIntent != null ? View.VISIBLE : View.GONE);
                boolean visible = next.ongoingCall ? next.hangUpIntent != null
                        : (next.declineIntent != null || next.answerIntent != null);
                actions.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
            root.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(22),
                    prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFD6A3FF)), dp(1)));
        }

        void updateTimerText() {
            if (!isCall || item.ongoingCall) body.setText(timerBody(item));
        }

        private String timerBody(IslandNotificationListener.NotificationItem value) {
            if (value.showChronometer && value.chronometerWhen > 0L) {
                long delta = value.chronometerCountDown
                        ? value.chronometerWhen - System.currentTimeMillis()
                        : System.currentTimeMillis() - value.chronometerWhen;
                delta = Math.max(0L, delta);
                String prefix = value.ongoingCall ? "Đang gọi · "
                        : (value.chronometerCountDown ? "Còn " : "Đã chạy ");
                return prefix + formatDuration(delta)
                        + (value.text == null || value.text.isEmpty() ? "" : "  ·  " + value.text);
            }
            if (value.ongoingCall) return value.text == null || value.text.isEmpty()
                    ? "Cuộc gọi đang diễn ra" : value.text;
            return value.text == null || value.text.isEmpty() ? value.appName : value.text;
        }

        void place() {
            if (userMoved) return;
            String prefix = isCall ? "activity_call" : "activity_timer";
            if (prefs.getBoolean(prefix + "_moved", false)) {
                params.x = clamp(dp(prefs.getInt(prefix + "_x", 12)), 0, screenWidth() - params.width);
                params.y = clamp(dp(prefs.getInt(prefix + "_y", 120)), dp(24), screenHeight() - params.height);
                userMoved = true;
            } else {
                int desiredX = (screenWidth() - params.width) / 2;
                int desiredY = dp(104);
                if (!isCall) desiredY += dp(116);
                int[] pos = findFreePosition(this, desiredX, desiredY, params.width, params.height);
                if (pos == null) {
                    root.setVisibility(View.GONE);
                    return;
                }
                params.x = pos[0];
                params.y = pos[1];
            }
            root.setVisibility(View.VISIBLE);
            try {
                if (root.getParent() == null) windowManager.addView(root, params);
                else windowManager.updateViewLayout(root, params);
            } catch (RuntimeException ignored) { }
        }

        private boolean handleTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    startX = params.x; startY = params.y; moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downX);
                    int dy = Math.round(event.getRawY() - downY);
                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) moved = true;
                    if (moved) {
                        params.x = clamp(startX + dx, 0, screenWidth() - params.width);
                        params.y = clamp(startY + dy, dp(24), screenHeight() - params.height);
                        try { windowManager.updateViewLayout(root, params); } catch (RuntimeException ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        int[] pos = findFreePosition(this, params.x, params.y, params.width, params.height);
                        if (pos == null) { params.x = startX; params.y = startY; }
                        else { params.x = pos[0]; params.y = pos[1]; userMoved = true; }
                        String prefix = isCall ? "activity_call" : "activity_timer";
                        prefs.edit().putBoolean(prefix + "_moved", userMoved)
                                .putInt(prefix + "_x", px(params.x)).putInt(prefix + "_y", px(params.y)).apply();
                        try { windowManager.updateViewLayout(root, params); } catch (RuntimeException ignored) { }
                    } else {
                        IslandNotificationListener.openNotification(item.key);
                    }
                    return true;
            }
            return true;
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return hours > 0 ? String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
                : String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, secs);
    }

    private int[] findFreePosition(ActivityOverlay excluded, int desiredX, int desiredY, int width, int height) {
        int minY = dp(24);
        int maxY = Math.max(minY, screenHeight() - height - dp(24));
        int maxX = Math.max(0, screenWidth() - width);
        int baseX = clamp(desiredX, 0, maxX);
        int baseY = clamp(desiredY, minY, maxY);
        int step = Math.max(dp(12), dp(1));
        for (int y = baseY; y <= maxY; y += step) if (isFree(excluded, baseX, y, width, height)) return new int[]{baseX, y};
        for (int y = baseY - step; y >= minY; y -= step) if (isFree(excluded, baseX, y, width, height)) return new int[]{baseX, y};
        for (int x = 0; x <= maxX; x += Math.max(dp(14), 1)) {
            for (int y = minY; y <= maxY; y += step) {
                if (isFree(excluded, x, y, width, height)) return new int[]{x, y};
            }
        }
        return null;
    }

    private boolean isFree(ActivityOverlay excluded, int x, int y, int width, int height) {
        if (headerParams != null && overlap(x, y, width, height, headerParams.x, headerParams.y, headerParams.width, dp(42))) return false;
        for (NotificationCard card : cards.values()) {
            if (overlap(x, y, width, height, card.params.x, card.params.y, card.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP))) return false;
        }
        if (mediaRoot != null && mediaRoot.getVisibility() == View.VISIBLE && mediaParams != null
                && overlap(x, y, width, height, mediaParams.x, mediaParams.y, mediaParams.width, mediaParams.height)) return false;
        if (excluded != null && chargingRoot != null && chargingRoot.getParent() != null
                && chargingRoot.getVisibility() == View.VISIBLE && chargingParams != null
                && overlap(x, y, width, height, chargingParams.x, chargingParams.y, chargingParams.width, chargingParams.height)) return false;
        if (callOverlay != null && callOverlay != excluded && callOverlay.isVisible()
                && overlap(x, y, width, height, callOverlay.params.x, callOverlay.params.y, callOverlay.params.width, callOverlay.params.height)) return false;
        if (timerOverlay != null && timerOverlay != excluded && timerOverlay.isVisible()
                && overlap(x, y, width, height, timerOverlay.params.x, timerOverlay.params.y, timerOverlay.params.width, timerOverlay.params.height)) return false;
        return true;
    }

    private void syncCharging() {
        if (!prefs.getBoolean("show_charging_stats", true)) { removeCharging(); return; }
        Intent battery = service.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) { removeCharging(); return; }
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        if (plugged == 0 || (status != BatteryManager.BATTERY_STATUS_CHARGING
                && status != BatteryManager.BATTERY_STATUS_FULL)) {
            removeCharging();
            return;
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = scale > 0 ? Math.max(0, level * 100 / scale) : -1;
        float voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f;
        float temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        BatteryManager bm = (BatteryManager) service.getSystemService(Context.BATTERY_SERVICE);
        int currentUa = Integer.MIN_VALUE;
        long remaining = -1L;
        if (bm != null) {
            try { currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW); } catch (RuntimeException ignored) { }
            if (Build.VERSION.SDK_INT >= 28) {
                try { remaining = bm.computeChargeTimeRemaining(); } catch (RuntimeException ignored) { }
            }
        }
        boolean currentKnown = currentUa != Integer.MIN_VALUE && currentUa != 0;
        float currentA = currentKnown ? Math.abs(currentUa) / 1_000_000f : -1f;
        float watts = voltage > 0f && currentA > 0f ? voltage * currentA : -1f;
        String currentText = currentA > 0f ? String.format(java.util.Locale.getDefault(), "%.2f A", currentA) : "— A";
        String voltageText = voltage > 0f ? String.format(java.util.Locale.getDefault(), "%.2f V", voltage) : "— V";
        String tempText = temperature > 0f ? String.format(java.util.Locale.getDefault(), "%.1f°C", temperature) : "—°C";
        String powerText = watts > 0f ? String.format(java.util.Locale.getDefault(), "%.2f W", watts) : "— W";
        String percentText = percent >= 0 ? percent + "%" : "—";
        String remainText = remaining > 0 ? "Còn " + formatDuration(remaining) : (status == BatteryManager.BATTERY_STATUS_FULL ? "Đã sạc đầy" : "Đang nạp năng lượng");
        String signature = plugged + "|" + status + "|" + percentText + "|" + voltageText + "|" + currentText + "|" + tempText + "|" + powerText + "|" + remainText;
        if (signature.equals(lastChargingSignature) && chargingRoot != null) return;
        lastChargingSignature = signature;
        ensureChargingCard();
        if (chargingRoot == null) return;
        chargingSummary.setText(status == BatteryManager.BATTERY_STATUS_FULL
                ? "ĐÃ SẠC ĐẦY" : "ĐANG SẠC");
        chargingDetails.setText("PIN  " + percentText + "     " + remainText + "\n"
                + "ĐIỆN ÁP  " + voltageText + "     DÒNG  " + currentText + "\n"
                + "NHIỆT ĐỘ  " + tempText + "     CÔNG SUẤT  " + powerText);
        chargingDetails.setVisibility(chargingExpanded ? View.VISIBLE : View.GONE);
        chargingExpand.setText(chargingExpanded ? "⌃" : "⌄");
        chargingRoot.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(20),
                prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFD6A3FF)), dp(1)));
        int desiredHeight = dp(chargingExpanded ? 132 : 58);
        chargingParams.height = desiredHeight;
        if (chargingParams.x < 0 || chargingParams.x + chargingParams.width > screenWidth()) {
            chargingParams.x = (screenWidth() - chargingParams.width) / 2;
        }
        int desiredX = chargingUserMoved ? chargingParams.x : (screenWidth() - chargingParams.width) / 2;
        int desiredY = chargingUserMoved ? chargingParams.y : screenHeight() - desiredHeight - dp(36);
        int[] pos = findFreePosition(null, desiredX, desiredY, chargingParams.width, desiredHeight);
        if (pos == null) chargingRoot.setVisibility(View.GONE);
        else {
            chargingRoot.setVisibility(View.VISIBLE);
            chargingParams.x = pos[0];
            chargingParams.y = pos[1];
        }
        try { if (chargingRoot.getParent() == null) windowManager.addView(chargingRoot, chargingParams);
              else windowManager.updateViewLayout(chargingRoot, chargingParams); } catch (RuntimeException ignored) { }
        updateMediaAvoidingCards();
    }

    private void ensureChargingCard() {
        if (chargingRoot != null) return;
        LinearLayout root = new LinearLayout(overlayContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(13), dp(9), dp(12), dp(9));
        root.setElevation(dp(14));
        root.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(20),
                prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFD6A3FF)), dp(1)));
        LinearLayout header = new LinearLayout(overlayContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView bolt = text("ϟ", 24, 0xFFFFC46B, true);
        bolt.setGravity(Gravity.CENTER);
        header.addView(bolt, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout labels = new LinearLayout(overlayContext);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(8), 0, 0, 0);
        chargingSummary = text("ĐANG SẠC", 11, prefs.getInt("text_color", Color.WHITE), true);
        TextView sub = text("Thông tin sạc trực tiếp", 9, prefs.getInt("secondary_text_color", 0xFFAAAEBB), false);
        labels.addView(chargingSummary);
        labels.addView(sub);
        header.addView(labels, new LinearLayout.LayoutParams(0, dp(36), 1));
        chargingExpand = text("⌄", 20, prefs.getInt("accent_color", 0xFFD6A3FF), true);
        chargingExpand.setGravity(Gravity.CENTER);
        chargingExpand.setOnClickListener(v -> toggleChargingExpanded());
        header.addView(chargingExpand, new LinearLayout.LayoutParams(dp(34), dp(36)));
        header.setOnTouchListener(this::handleChargingDrag);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));
        chargingDetails = text("", 10, prefs.getInt("secondary_text_color", 0xFFAAAEBB), false);
        chargingDetails.setLineSpacing(dp(3), 1.0f);
        chargingDetails.setPadding(dp(44), dp(6), 0, 0);
        root.addView(chargingDetails, new LinearLayout.LayoutParams(-1, -2));
        root.setOnClickListener(v -> toggleChargingExpanded());
        chargingRoot = root;
        chargingParams = overlayParams(Math.min(dp(330), screenWidth() - dp(24)), dp(58));
        chargingParams.x = (screenWidth() - chargingParams.width) / 2;
        chargingParams.y = screenHeight() - dp(100);
    }

    private void toggleChargingExpanded() {
        chargingExpanded = !chargingExpanded;
        if (chargingDetails != null) chargingDetails.setVisibility(chargingExpanded ? View.VISIBLE : View.GONE);
        if (chargingExpand != null) chargingExpand.setText(chargingExpanded ? "⌃" : "⌄");
        if (chargingParams != null && chargingRoot != null) {
            chargingParams.height = dp(chargingExpanded ? 132 : 58);
            int[] pos = findFreePosition(null, chargingParams.x, chargingParams.y,
                    chargingParams.width, chargingParams.height);
            if (pos == null) chargingRoot.setVisibility(View.GONE);
            else {
                chargingRoot.setVisibility(View.VISIBLE);
                chargingParams.x = pos[0];
                chargingParams.y = pos[1];
            }
            try { windowManager.updateViewLayout(chargingRoot, chargingParams); } catch (RuntimeException ignored) { }
            updateMediaAvoidingCards();
        }
    }

    private boolean handleChargingDrag(View view, MotionEvent event) {
        if (chargingParams == null || chargingRoot == null) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragDownX = event.getRawX(); dragDownY = event.getRawY();
                dragStartX = chargingParams.x; dragStartY = chargingParams.y; dragMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - dragDownX);
                int dy = Math.round(event.getRawY() - dragDownY);
                if (Math.abs(dx) + Math.abs(dy) > dp(6)) dragMoved = true;
                if (dragMoved) {
                    chargingParams.x = clamp(dragStartX + dx, 0, screenWidth() - chargingParams.width);
                    chargingParams.y = clamp(dragStartY + dy, dp(24), screenHeight() - chargingParams.height);
                    try { windowManager.updateViewLayout(chargingRoot, chargingParams); } catch (RuntimeException ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragMoved) {
                    int[] pos = findFreePosition(null, chargingParams.x, chargingParams.y,
                            chargingParams.width, chargingParams.height);
                    if (pos == null) {
                        chargingParams.x = dragStartX;
                        chargingParams.y = dragStartY;
                    } else {
                        chargingParams.x = pos[0];
                        chargingParams.y = pos[1];
                        chargingUserMoved = true;
                        prefs.edit().putInt("charging_x", px(chargingParams.x))
                                .putInt("charging_y", px(chargingParams.y)).apply();
                    }
                    try { windowManager.updateViewLayout(chargingRoot, chargingParams); } catch (RuntimeException ignored) { }
                    updateMediaAvoidingCards();
                } else toggleChargingExpanded();
                return true;
        }
        return true;
    }

    private void removeCharging() {
        if (chargingRoot != null) {
            try { windowManager.removeView(chargingRoot); } catch (RuntimeException ignored) { }
        }
        chargingRoot = null;
        chargingParams = null;
        chargingSummary = null;
        chargingDetails = null;
        chargingExpand = null;
        chargingExpanded = false;
        chargingUserMoved = false;
        lastChargingSignature = "";
    }

    private void removeAll() {
        removeNotificationOverlays();
        removeMedia();
        if (callOverlay != null) callOverlay.detach();
        if (timerOverlay != null) timerOverlay.detach();
        callOverlay = null;
        timerOverlay = null;
        removeCharging();
        lastMediaSignature = "";
    }

    private class NotificationCard {
        IslandNotificationListener.NotificationItem item;
        final LinearLayout root;
        final ImageView senderAvatar;
        final ImageView appIcon;
        final TextView appLabel;
        final TextView senderLabel;
        final TextView bodyLabel;
        final LinearLayout actions;
        final LinearLayout replyRow;
        final EditText replyInput;
        final WindowManager.LayoutParams params;
        boolean userMoved;
        float downX;
        float downY;
        int startX;
        int startY;
        boolean moved;

        NotificationCard(IslandNotificationListener.NotificationItem item, int index) {
            this.item = item;
            root = new LinearLayout(overlayContext);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(11), dp(8), dp(11), dp(8));
            root.setElevation(dp(10));
            root.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(18),
                    prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFD6A3FF)), dp(1)));

            LinearLayout top = new LinearLayout(overlayContext);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            senderAvatar = new ImageView(overlayContext);
            senderAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            senderAvatar.setClipToOutline(true);
            senderAvatar.setBackground(surface(prefs.getInt("avatar_bg_color", 0xFF252A36), dp(12), 0, 0));
            top.addView(senderAvatar, new LinearLayout.LayoutParams(dp(36), dp(36)));

            LinearLayout labels = new LinearLayout(overlayContext);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            labels.setPadding(dp(8), 0, dp(5), 0);
            appLabel = text("Ứng dụng", 9, prefs.getInt("secondary_text_color", 0xFFAAAEBB), true);
            senderLabel = text("Thông báo", 12, prefs.getInt("text_color", Color.WHITE), true);
            senderLabel.setSingleLine(true);
            senderLabel.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(appLabel);
            labels.addView(senderLabel, new LinearLayout.LayoutParams(-1, -2));
            top.addView(labels, new LinearLayout.LayoutParams(0, dp(38), 1));

            appIcon = new ImageView(overlayContext);
            appIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            appIcon.setBackground(surface(prefs.getInt("avatar_bg_color", 0xFF252A36), dp(8), 0, 0));
            top.addView(appIcon, new LinearLayout.LayoutParams(dp(25), dp(25)));
            top.setOnTouchListener((v, event) -> handleDrag(event));
            root.addView(top, new LinearLayout.LayoutParams(-1, dp(40)));

            bodyLabel = text("", 11, prefs.getInt("notification_color", Color.WHITE), false);
            bodyLabel.setMaxLines(2);
            bodyLabel.setEllipsize(TextUtils.TruncateAt.END);
            bodyLabel.setLineSpacing(dp(1), 1f);
            bodyLabel.setOnClickListener(v -> IslandNotificationListener.openNotification(this.item.key));
            root.addView(bodyLabel, new LinearLayout.LayoutParams(-1, dp(34)));

            actions = new LinearLayout(overlayContext);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            TextView reply = button("Trả lời", prefs.getInt("reply_color", prefs.getInt("accent_color", 0xFFD6A3FF)));
            reply.setOnClickListener(v -> showReplyInput());
            TextView dismiss = button("Bỏ qua", 0xFF353B49);
            dismiss.setOnClickListener(v -> IslandNotificationListener.dismissNotification(this.item.key));
            TextView open = button("Mở", 0x223D8EFF);
            open.setTextColor(prefs.getInt("accent_color", 0xFFD6A3FF));
            open.setOnClickListener(v -> IslandNotificationListener.openNotification(this.item.key));
            actions.addView(reply, new LinearLayout.LayoutParams(-2, dp(30)));
            LinearLayout.LayoutParams dismissLp = new LinearLayout.LayoutParams(-2, dp(30));
            dismissLp.setMargins(dp(6), 0, 0, 0);
            actions.addView(dismiss, dismissLp);
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-2, dp(30));
            openLp.setMargins(dp(6), 0, 0, 0);
            actions.addView(open, openLp);
            root.addView(actions, new LinearLayout.LayoutParams(-1, dp(32)));

            replyRow = new LinearLayout(overlayContext);
            replyRow.setOrientation(LinearLayout.HORIZONTAL);
            replyRow.setGravity(Gravity.CENTER_VERTICAL);
            replyInput = new EditText(overlayContext);
            replyInput.setSingleLine(true);
            replyInput.setTextSize(11);
            replyInput.setTextColor(prefs.getInt("text_color", Color.WHITE));
            replyInput.setHintTextColor(0xFF8E94A4);
            replyInput.setHint("Nhập nội dung trả lời…");
            replyInput.setPadding(dp(9), 0, dp(8), 0);
            replyInput.setBackground(surface(0xFF252B38, dp(10), 0x554F5A70, dp(1)));
            replyRow.addView(replyInput, new LinearLayout.LayoutParams(0, dp(38), 1));
            TextView send = button("Gửi", prefs.getInt("reply_color", prefs.getInt("accent_color", 0xFFD6A3FF)));
            LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(-2, dp(34));
            sendLp.setMargins(dp(6), 0, 0, 0);
            replyRow.addView(send, sendLp);
            send.setOnClickListener(v -> sendReply());
            TextView cancel = button("Hủy", 0xFF343A48);
            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-2, dp(34));
            cancelLp.setMargins(dp(5), 0, 0, 0);
            replyRow.addView(cancel, cancelLp);
            cancel.setOnClickListener(v -> closeReplyInput());
            replyRow.setVisibility(View.GONE);
            root.addView(replyRow, new LinearLayout.LayoutParams(-1, dp(40)));

            params = overlayParams(cardWidth(), dp(NOTIFICATION_CARD_HEIGHT_DP));
            placeAtIndex(index);
            bind(item);
        }

        void attach() {
            try { windowManager.addView(root, params); }
            catch (RuntimeException ignored) { }
        }

        void detach() {
            try { windowManager.removeView(root); } catch (RuntimeException ignored) { }
        }

        void bind(IslandNotificationListener.NotificationItem next) {
            item = next;
            String customName = prefs.getString("custom_app_name", "").trim();
            String resolvedAppName = customName.isEmpty() ? next.appName : customName;
            if (!prefs.getBoolean("show_source_app_name", true)) resolvedAppName = "Thông báo";
            appLabel.setText(resolvedAppName == null || resolvedAppName.isEmpty() ? "Thông báo" : resolvedAppName);
            String sender = prefs.getBoolean("show_sender_name", true)
                    && next.senderName != null && !next.senderName.trim().isEmpty()
                    ? next.senderName : next.title;
            senderLabel.setText(sender == null ? "Thông báo mới" : sender);
            bodyLabel.setText(next.text == null || next.text.isEmpty() ? next.title : next.text);
            boolean avatarLoaded = false;
            if (next.avatarIcon != null) {
                try {
                    Drawable d = next.avatarIcon.loadDrawable(service);
                    if (d != null) { senderAvatar.setImageDrawable(d); avatarLoaded = true; }
                } catch (RuntimeException ignored) { }
            }
            if (!avatarLoaded) {
                try { senderAvatar.setImageDrawable(service.getPackageManager().getApplicationIcon(next.packageName)); }
                catch (Exception ignored) { senderAvatar.setImageResource(android.R.drawable.sym_def_app_icon); }
            }
            senderAvatar.setVisibility(prefs.getBoolean("show_sender_avatar", true) ? View.VISIBLE : View.GONE);
            appIcon.setVisibility(prefs.getBoolean("show_source_icon", true) ? View.VISIBLE : View.GONE);
            try { appIcon.setImageDrawable(service.getPackageManager().getApplicationIcon(next.packageName)); }
            catch (Exception ignored) { appIcon.setImageResource(android.R.drawable.sym_def_app_icon); }
            actions.getChildAt(0).setVisibility(next.canReply() && prefs.getBoolean("show_reply_actions", true) ? View.VISIBLE : View.GONE);
            actions.getChildAt(1).setVisibility(prefs.getBoolean("show_dismiss_action", true) ? View.VISIBLE : View.GONE);
            replyRow.setVisibility(View.GONE);
            actions.setVisibility(View.VISIBLE);
            root.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(18),
                    prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFD6A3FF)), dp(1)));
            senderLabel.setTextColor(prefs.getInt("text_color", Color.WHITE));
            appLabel.setTextColor(prefs.getInt("secondary_text_color", 0xFFAAAEBB));
            bodyLabel.setTextColor(prefs.getInt("notification_color", Color.WHITE));
            replyInput.setTextColor(prefs.getInt("text_color", Color.WHITE));
            replyInput.setBackground(surface(0xFF252B38, dp(10), 0x554F5A70, dp(1)));
            ((TextView) actions.getChildAt(0)).setBackground(surface(
                    prefs.getInt("reply_color", prefs.getInt("accent_color", 0xFFD6A3FF)), dp(10), 0, 0));
        }

        private String positionPrefix() { return "floating_card_" + Integer.toHexString(item.key.hashCode()); }

        void placeAtIndex(int index) {
            if (userMoved) return;
            String prefix = positionPrefix();
            if (prefs.getBoolean(prefix + "_moved", false)) {
                userMoved = true;
                params.x = clamp(dp(prefs.getInt(prefix + "_x", 12)), 0, screenWidth() - params.width);
                params.y = clamp(dp(prefs.getInt(prefix + "_y", 150)), dp(24), screenHeight() - dp(NOTIFICATION_CARD_HEIGHT_DP));
                if (root.getParent() != null) {
                    try { windowManager.updateViewLayout(root, params); } catch (RuntimeException ignored) { }
                }
                return;
            }
            int baseX = dp(prefs.getInt("floating_x", 12));
            int headerY = dp(prefs.getInt("floating_y", 96));
            int step = dp(NOTIFICATION_CARD_HEIGHT_DP + NOTIFICATION_GAP_DP);
            int baseY = stackAboveHeader
                    ? headerY - dp(10 + NOTIFICATION_CARD_HEIGHT_DP) - index * step
                    : headerY + dp(50) + index * step;
            params.x = clamp(baseX, 0, screenWidth() - params.width);
            params.y = clamp(baseY, dp(24), screenHeight() - dp(NOTIFICATION_CARD_HEIGHT_DP));
            if (root.getParent() != null) {
                try { windowManager.updateViewLayout(root, params); } catch (RuntimeException ignored) { }
            }
        }

        private boolean handleDrag(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    startX = params.x; startY = params.y; moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = Math.round(event.getRawX() - downX);
                    int dy = Math.round(event.getRawY() - downY);
                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) moved = true;
                    if (moved) {
                        params.x = clamp(startX + dx, 0, screenWidth() - params.width);
                        params.y = clamp(startY + dy, dp(24), screenHeight() - dp(NOTIFICATION_CARD_HEIGHT_DP));
                        try { windowManager.updateViewLayout(root, params); } catch (RuntimeException ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        if (resolveCollision(this)) {
                            userMoved = true;
                            String prefix = positionPrefix();
                            prefs.edit().putBoolean(prefix + "_moved", true)
                                    .putInt(prefix + "_x", px(params.x))
                                    .putInt(prefix + "_y", px(params.y)).apply();
                            updateMediaAvoidingCards();
                        } else {
                            params.x = startX;
                            params.y = startY;
                            try { windowManager.updateViewLayout(root, params); } catch (RuntimeException ignored) { }
                            toast("Không còn vị trí trống cho thẻ này; Ayane đã giữ vị trí cũ");
                        }
                    } else {
                        IslandNotificationListener.openNotification(item.key);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return true;
        }

        private void showReplyInput() {
            if (!item.canReply()) return;
            actions.setVisibility(View.GONE);
            replyRow.setVisibility(View.VISIBLE);
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            try { windowManager.updateViewLayout(root, params); } catch (RuntimeException ignored) { }
            replyInput.setFocusableInTouchMode(true);
            replyInput.requestFocus();
            replyInput.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) service.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(replyInput, InputMethodManager.SHOW_IMPLICIT);
            }, 120);
        }

        private void closeReplyInput() {
            replyInput.setText("");
            replyRow.setVisibility(View.GONE);
            actions.setVisibility(View.VISIBLE);
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            try { windowManager.updateViewLayout(root, params); } catch (RuntimeException ignored) { }
            InputMethodManager imm = (InputMethodManager) service.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(replyInput.getWindowToken(), 0);
        }

        private void sendReply() {
            String message = replyInput.getText().toString();
            boolean sent = IslandNotificationListener.replyToNotification(service, item.key, message);
            if (!sent) {
                toast("Ứng dụng gửi thông báo không hỗ trợ trả lời nhanh hoặc thông báo đã hết hạn");
                return;
            }
            closeReplyInput();
            handler.postDelayed(FloatingOverlayManager.this::update, 500);
        }
    }

    private boolean resolveCollision(NotificationCard moved) {
        int originalX = moved.params.x;
        int originalY = moved.params.y;
        int maxX = Math.max(0, screenWidth() - moved.params.width);
        int minY = dp(24);
        int maxY = Math.max(minY, screenHeight() - dp(NOTIFICATION_CARD_HEIGHT_DP));
        int startX = clamp(originalX, 0, maxX);
        int startY = clamp(originalY, minY, maxY);
        int step = Math.max(dp(12), 1);

        // Keep the drop point as close as possible: search down, then up.
        for (int y = startY; y <= maxY; y += step) {
            if (isNotificationPositionFree(moved, startX, y)) {
                moved.params.x = startX;
                moved.params.y = y;
                try { windowManager.updateViewLayout(moved.root, moved.params); } catch (RuntimeException ignored) { }
                return true;
            }
        }
        for (int y = startY - step; y >= minY; y -= step) {
            if (isNotificationPositionFree(moved, startX, y)) {
                moved.params.x = startX;
                moved.params.y = y;
                try { windowManager.updateViewLayout(moved.root, moved.params); } catch (RuntimeException ignored) { }
                return true;
            }
        }

        // If there is no vertical gap at the current x, look for another column.
        for (int x = 0; x <= maxX; x += step) {
            if (x == startX) continue;
            for (int y = minY; y <= maxY; y += step) {
                if (isNotificationPositionFree(moved, x, y)) {
                    moved.params.x = x;
                    moved.params.y = y;
                    try { windowManager.updateViewLayout(moved.root, moved.params); } catch (RuntimeException ignored) { }
                    return true;
                }
            }
        }

        moved.params.x = originalX;
        moved.params.y = originalY;
        return false;
    }

    private boolean isNotificationPositionFree(NotificationCard moved, int x, int y) {
        int maxY = screenHeight() - dp(NOTIFICATION_CARD_HEIGHT_DP);
        if (x < 0 || x + moved.params.width > screenWidth() || y < dp(24) || y > maxY) return false;
        for (NotificationCard other : cards.values()) {
            if (other == moved) continue;
            if (overlap(x, y, moved.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP),
                    other.params.x, other.params.y, other.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP))) return false;
        }
        if (headerParams != null && overlap(x, y, moved.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP),
                headerParams.x, headerParams.y, headerParams.width, dp(42))) return false;
        if (mediaRoot != null && mediaRoot.getVisibility() == View.VISIBLE && mediaParams != null
                && overlap(x, y, moved.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP), mediaParams.x,
                mediaParams.y, mediaParams.width, mediaParams.height)) return false;
        if (chargingRoot != null && chargingRoot.getVisibility() == View.VISIBLE && chargingParams != null
                && overlap(x, y, moved.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP), chargingParams.x,
                chargingParams.y, chargingParams.width, chargingParams.height)) return false;
        if (callOverlay != null && callOverlay.isVisible()
                && overlap(x, y, moved.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP), callOverlay.params.x,
                callOverlay.params.y, callOverlay.params.width, callOverlay.params.height)) return false;
        if (timerOverlay != null && timerOverlay.isVisible()
                && overlap(x, y, moved.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP), timerOverlay.params.x,
                timerOverlay.params.y, timerOverlay.params.width, timerOverlay.params.height)) return false;
        return true;
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        boolean accessibilityOverlay = overlayContext instanceof AyaneAccessibilityService;
        int type = accessibilityOverlay
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (accessibilityOverlay) flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height, type, flags,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        return p;
    }

    private int cardWidth() { return Math.min(dp(332), Math.max(dp(260), screenWidth() - dp(24))); }
    private int screenWidth() { return service.getResources().getDisplayMetrics().widthPixels; }
    private int screenHeight() { return service.getResources().getDisplayMetrics().heightPixels; }
    private int dp(float value) { return (int) (value * service.getResources().getDisplayMetrics().density + 0.5f); }
    private int px(int value) { return Math.round(value / service.getResources().getDisplayMetrics().density); }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(Math.max(min, max), value)); }

    private boolean overlap(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private GradientDrawable surface(int color, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{blend(color, 0xFF343B4B, 12), color});
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private int blend(int base, int overlay, int amount) {
        int a = Math.max(0, Math.min(100, amount));
        return Color.argb(Color.alpha(base),
                (Color.red(base) * (100 - a) + Color.red(overlay) * a) / 100,
                (Color.green(base) * (100 - a) + Color.green(overlay) * a) / 100,
                (Color.blue(base) * (100 - a) + Color.blue(overlay) * a) / 100);
    }

    private void applyTypography(View view) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView label = (TextView) view;
            label.setTypeface(AyaneTypography.resolve(service, label.getTypeface() != null
                    && label.getTypeface().isBold()));
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTypography(group.getChildAt(i));
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(overlayContext);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(AyaneTypography.resolve(service, bold));
        return v;
    }

    private TextView button(String value, int color) {
        TextView v = text(value, 10, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), 0, dp(10), 0);
        v.setBackground(surface(color, dp(10), 0, 0));
        return v;
    }

    private void toast(String message) {
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show();
    }

    public void close() {
        closed = true;
        removeAll();
        handler.removeCallbacksAndMessages(null);
    }
}
