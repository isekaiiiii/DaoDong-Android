package com.ayane.dynamicisland;

import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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

    private LinearLayout mediaRoot;
    private WindowManager.LayoutParams mediaParams;
    private ImageView mediaArtwork;
    private TextView mediaTitle;
    private TextView mediaArtist;
    private TextView mediaPlay;
    private String lastMediaSignature = "";
    private boolean mediaUserMoved;
    private String lastNotificationSignature = "";
    private boolean closed;

    public FloatingOverlayManager(Service service, WindowManager windowManager, SharedPreferences prefs) {
        this.service = service;
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
    }

    private void syncNotifications() {
        boolean enabled = prefs.getBoolean("floating_notifications", true)
                && prefs.getBoolean("show_notifications", true);
        List<IslandNotificationListener.NotificationItem> all = enabled
                ? IslandNotificationListener.getNotificationsSnapshot()
                : new ArrayList<IslandNotificationListener.NotificationItem>();

        int requested = Math.max(1, Math.min(MAX_VISIBLE_NOTIFICATIONS,
                prefs.getInt("floating_max_cards", 4)));
        int headerY = dp(prefs.getInt("floating_y", 96));
        boolean mediaWillShow = prefs.getBoolean("floating_media_card", true)
                && prefs.getBoolean("show_media", true)
                && !TextUtils.isEmpty(IslandNotificationListener.getMediaTitle());
        int mediaReserve = mediaWillShow ? dp(84) : 0;
        int availableBelow = screenHeight() - headerY - dp(50 + 24) - mediaReserve;
        int availableAbove = headerY - dp(24 + 10);
        stackAboveHeader = availableBelow < dp(NOTIFICATION_CARD_HEIGHT_DP + NOTIFICATION_GAP_DP);
        int available = Math.max(availableBelow, availableAbove);
        int fitOnScreen = Math.max(1, available / dp(NOTIFICATION_CARD_HEIGHT_DP + NOTIFICATION_GAP_DP));
        pageSize = Math.min(requested, fitOnScreen);
        String newest = all.isEmpty() ? "" : all.get(0).key;
        if (!newest.equals(lastNewestKey)) {
            lastNewestKey = newest;
            pageOffset = 0;
        }
        int maxOffset = all.isEmpty() ? 0 : ((all.size() - 1) / pageSize) * pageSize;
        if (pageOffset > maxOffset) pageOffset = maxOffset;
        int count = Math.min(pageSize, Math.max(0, all.size() - pageOffset));
        StringBuilder signature = new StringBuilder(enabled ? "1" : "0");
        signature.append("|offset=").append(pageOffset).append("|size=").append(pageSize);
        for (int i = pageOffset; i < pageOffset + count; i++) {
            IslandNotificationListener.NotificationItem item = all.get(i);
            signature.append('|').append(item.key).append(':').append(item.title)
                    .append(':').append(item.text).append(':').append(item.senderName)
                    .append(':').append(item.canReply());
        }
        signature.append("|total=").append(all.size())
                .append("|clear=").append(prefs.getBoolean("floating_clear_button", true))
                .append("|reply=").append(prefs.getBoolean("show_reply_actions", true))
                .append("|dismiss=").append(prefs.getBoolean("show_dismiss_action", true))
                .append("|sender=").append(prefs.getBoolean("show_sender_name", true))
                .append(':').append(prefs.getBoolean("show_sender_avatar", true))
                .append(':').append(prefs.getBoolean("show_source_icon", true))
                .append(':').append(prefs.getString("custom_app_name", ""))
                .append("|colors=").append(prefs.getInt("panel_bg_color", 0xF0181C27))
                .append(':').append(prefs.getInt("border_color", 0xFFA680FF))
                .append(':').append(prefs.getInt("text_color", Color.WHITE))
                .append(':').append(prefs.getInt("notification_color", Color.WHITE))
                .append(':').append(prefs.getInt("reply_color", 0xFFA680FF))
                .append(':').append(prefs.getInt("secondary_text_color", 0xFFAAAEBB))
                .append(':').append(prefs.getInt("avatar_bg_color", 0xFF252A36))
                .append(':').append(prefs.getInt("accent_color", 0xFFA680FF))
                .append("|style=").append(prefs.getInt("floating_max_cards", 4))
                .append(':').append(prefs.getInt("floating_x", 12))
                .append(':').append(prefs.getInt("floating_y", 96));
        String sig = signature.toString();
        if (sig.equals(lastNotificationSignature)) {
            updateHeaderCount(all.size());
            return;
        }
        lastNotificationSignature = sig;

        if (count == 0) {
            removeNotificationOverlays();
            lastNotificationSignature = sig;
            return;
        }

        ensureHeader();
        if (headerParams != null && headerRoot != null) {
            headerParams.x = clamp(dp(prefs.getInt("floating_x", 12)), 0, screenWidth() - headerParams.width);
            headerParams.y = clamp(dp(prefs.getInt("floating_y", 96)), dp(24), screenHeight() - dp(48));
            try { windowManager.updateViewLayout(headerRoot, headerParams); } catch (RuntimeException ignored) { }
        }
        updateHeaderCount(all.size());
        if (headerRoot != null) {
            headerRoot.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(22),
                    prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFA680FF)), dp(1)));
        }
        if (clearAllButton != null) clearAllButton.setBackground(surface(0x33FF7187, dp(16), 0x66FF7187, dp(1)));

        ArrayList<String> wanted = new ArrayList<>();
        for (int i = pageOffset; i < pageOffset + count; i++) {
            IslandNotificationListener.NotificationItem item = all.get(i);
            wanted.add(item.key);
            NotificationCard card = cards.get(item.key);
            if (card == null) {
                card = new NotificationCard(item, i - pageOffset);
                cards.put(item.key, card);
                card.attach();
                if (!resolveCollision(card)) {
                    card.detach();
                    cards.remove(item.key);
                }
            } else {
                card.bind(item);
                if (!card.userMoved) card.placeAtIndex(i - pageOffset);
            }
        }

        ArrayList<String> remove = new ArrayList<>();
        for (String key : cards.keySet()) if (!wanted.contains(key)) remove.add(key);
        for (String key : remove) {
            NotificationCard card = cards.remove(key);
            if (card != null) card.detach();
        }
        ArrayList<String> collisionKeys = new ArrayList<>();
        for (Map.Entry<String, NotificationCard> entry : cards.entrySet()) {
            if (!resolveCollision(entry.getValue())) collisionKeys.add(entry.getKey());
        }
        for (String key : collisionKeys) {
            NotificationCard card = cards.remove(key);
            if (card != null) card.detach();
        }
        updateMediaAvoidingCards();
    }

    private void ensureHeader() {
        if (headerRoot != null) return;
        LinearLayout row = new LinearLayout(service);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), 0, dp(7), 0);
        row.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(22),
                prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFA680FF)), dp(1)));
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
        if (!enabled || TextUtils.isEmpty(title)) {
            removeMedia();
            lastMediaSignature = "";
            return;
        }
        String sig = title + "|" + artist + "|" + pkg + "|" + playing
                + "|art=" + (artwork == null ? 0 : artwork.getGenerationId())
                + "|pos=" + prefs.getInt("media_floating_x", 12) + ":" + prefs.getInt("media_floating_y", -1)
                + "|avoid=" + prefs.getBoolean("auto_avoid_overlap", true)
                + "|colors=" + prefs.getInt("panel_bg_color", 0xF0181C27) + ":"
                + prefs.getInt("border_color", 0xFFA680FF) + ":"
                + prefs.getInt("text_color", Color.WHITE) + ":"
                + prefs.getInt("secondary_text_color", 0xFFAAAEBB) + ":"
                + prefs.getInt("media_color", 0xFFA680FF) + ":"
                + prefs.getInt("avatar_bg_color", 0xFF252A36);
        if (sig.equals(lastMediaSignature) && mediaRoot != null) {
            updateMediaAvoidingCards();
            return;
        }
        lastMediaSignature = sig;
        ensureMediaCard();
        if (mediaRoot == null) return;
        mediaRoot.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(20),
                prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFA680FF)), dp(1)));
        mediaTitle.setText(title);
        mediaTitle.setTextColor(prefs.getInt("text_color", Color.WHITE));
        mediaArtist.setText(artist.isEmpty() ? "Đang phát" : artist);
        mediaArtist.setTextColor(prefs.getInt("secondary_text_color", 0xFFAAAEBB));
        mediaPlay.setText(playing ? "Ⅱ" : "▶");
        mediaPlay.setTextColor(prefs.getInt("media_color", prefs.getInt("accent_color", 0xFFA680FF)));
        if (mediaParams != null) {
            mediaParams.x = clamp(dp(prefs.getInt("media_floating_x", 12)), 0, screenWidth() - mediaParams.width);
            int defaultMediaY = px(screenHeight() - dp(100));
            mediaParams.y = clamp(dp(prefs.getInt("media_floating_y", defaultMediaY)), dp(24), screenHeight() - dp(80));
            try { windowManager.updateViewLayout(mediaRoot, mediaParams); } catch (RuntimeException ignored) { }
        }
        if (artwork != null) mediaArtwork.setImageBitmap(artwork);
        else {
            try { mediaArtwork.setImageDrawable(service.getPackageManager().getApplicationIcon(pkg)); }
            catch (Exception ignored) { mediaArtwork.setImageResource(android.R.drawable.ic_media_play); }
        }
        updateMediaAvoidingCards();
    }

    private void ensureMediaCard() {
        if (mediaRoot != null) return;
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(11), dp(9), dp(9), dp(9));
        root.setElevation(dp(12));
        root.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(20),
                prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFA680FF)), dp(1)));

        mediaArtwork = new ImageView(service);
        mediaArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaArtwork.setBackground(surface(prefs.getInt("avatar_bg_color", 0xFF252A36), dp(12), 0, 0));
        mediaArtwork.setClipToOutline(true);
        root.addView(mediaArtwork, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(service);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(10), 0, dp(5), 0);
        mediaTitle = text("Đang phát nhạc", 12, prefs.getInt("text_color", Color.WHITE), true);
        mediaTitle.setSingleLine(true);
        mediaTitle.setEllipsize(TextUtils.TruncateAt.END);
        mediaArtist = text("Ayane Music", 10, prefs.getInt("secondary_text_color", 0xFFAAAEBB), false);
        mediaArtist.setSingleLine(true);
        mediaArtist.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(mediaTitle);
        labels.addView(mediaArtist, new LinearLayout.LayoutParams(-1, -2));
        labels.setOnTouchListener((v, e) -> handleMediaDrag(e));
        root.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));

        TextView previous = text("|◀", 15, prefs.getInt("text_color", Color.WHITE), true);
        previous.setGravity(Gravity.CENTER);
        previous.setOnClickListener(v -> {
            IslandNotificationListener.skipToPrevious(service);
            handler.postDelayed(this::syncMedia, 350);
        });
        mediaPlay = text("▶", 18, prefs.getInt("media_color", 0xFFA680FF), true);
        mediaPlay.setGravity(Gravity.CENTER);
        mediaPlay.setOnClickListener(v -> {
            IslandNotificationListener.togglePlayback(service);
            handler.postDelayed(this::syncMedia, 350);
        });
        TextView next = text("▶|", 15, prefs.getInt("text_color", Color.WHITE), true);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(v -> {
            IslandNotificationListener.skipToNext(service);
            handler.postDelayed(this::syncMedia, 350);
        });
        root.addView(previous, new LinearLayout.LayoutParams(dp(34), dp(48)));
        root.addView(mediaPlay, new LinearLayout.LayoutParams(dp(38), dp(48)));
        root.addView(next, new LinearLayout.LayoutParams(dp(34), dp(48)));

        mediaRoot = root;
        mediaParams = overlayParams(Math.min(dp(340), screenWidth() - dp(24)), dp(68));
        mediaParams.x = dp(prefs.getInt("media_floating_x", 12));
        mediaParams.y = clamp(dp(prefs.getInt("media_floating_y", px(screenHeight() - dp(100)))),
                dp(24), screenHeight() - dp(80));
        try { windowManager.addView(mediaRoot, mediaParams); }
        catch (RuntimeException error) { mediaRoot = null; mediaParams = null; }
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
                if (Math.abs(dx) + Math.abs(dy) > dp(6)) dragMoved = true;
                if (dragMoved) {
                    mediaParams.x = clamp(dragStartX + dx, 0, screenWidth() - mediaParams.width);
                    mediaParams.y = clamp(dragStartY + dy, dp(24), screenHeight() - dp(80));
                    try { windowManager.updateViewLayout(mediaRoot, mediaParams); } catch (RuntimeException ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragMoved) {
                    mediaUserMoved = true;
                    updateMediaAvoidingCards();
                    prefs.edit().putInt("media_floating_x", px(mediaParams.x))
                            .putInt("media_floating_y", px(mediaParams.y)).apply();
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
        int preferredY = mediaUserMoved ? mediaParams.y : screenHeight() - dp(90);
        int minY = dp(24);
        int maxY = Math.max(minY, screenHeight() - dp(80));
        int step = Math.max(dp(12), 1);
        boolean placed = false;
        int chosenX = preferredX;
        int chosenY = clamp(preferredY, minY, maxY);

        // First preserve the user's horizontal position and search vertically.
        for (int y = chosenY; y >= minY && !placed; y -= step) {
            if (isMediaPositionFree(preferredX, y)) { chosenY = y; placed = true; }
        }
        for (int y = chosenY + step; y <= maxY && !placed; y += step) {
            if (isMediaPositionFree(preferredX, y)) { chosenY = y; placed = true; }
        }

        // If vertical space at this x is exhausted, try the other side of the screen.
        if (!placed) {
            int maxX = Math.max(0, screenWidth() - mediaParams.width);
            for (int x = 0; x <= maxX && !placed; x += Math.max(dp(16), 1)) {
                for (int y = minY; y <= maxY && !placed; y += step) {
                    if (isMediaPositionFree(x, y)) { chosenX = x; chosenY = y; placed = true; }
                }
            }
        }

        mediaRoot.setVisibility(placed ? View.VISIBLE : View.GONE);
        if (!placed) return; // Hide it temporarily instead of drawing over a notification.
        mediaParams.x = chosenX;
        mediaParams.y = chosenY;
        try { windowManager.updateViewLayout(mediaRoot, mediaParams); } catch (RuntimeException ignored) { }
    }

    private boolean isMediaPositionFree(int x, int y) {
        int maxY = screenHeight() - dp(80);
        if (x < 0 || x + mediaParams.width > screenWidth() || y < dp(24) || y > maxY) return false;
        for (NotificationCard card : cards.values()) {
            if (overlap(x, y, mediaParams.width, dp(68),
                    card.params.x, card.params.y, card.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP))) return false;
        }
        return headerParams == null || !overlap(x, y, mediaParams.width, dp(68),
                headerParams.x, headerParams.y, headerParams.width, dp(42));
    }

    private void removeMedia() {
        if (mediaRoot != null) {
            try { windowManager.removeView(mediaRoot); } catch (RuntimeException ignored) { }
        }
        mediaRoot = null;
        mediaParams = null;
        mediaArtwork = null;
        mediaTitle = null;
        mediaArtist = null;
        mediaPlay = null;
        mediaUserMoved = false;
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
            root = new LinearLayout(service);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(11), dp(8), dp(11), dp(8));
            root.setElevation(dp(10));
            root.setBackground(surface(prefs.getInt("panel_bg_color", 0xF0181C27), dp(18),
                    prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFA680FF)), dp(1)));

            LinearLayout top = new LinearLayout(service);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            senderAvatar = new ImageView(service);
            senderAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            senderAvatar.setClipToOutline(true);
            senderAvatar.setBackground(surface(prefs.getInt("avatar_bg_color", 0xFF252A36), dp(12), 0, 0));
            top.addView(senderAvatar, new LinearLayout.LayoutParams(dp(36), dp(36)));

            LinearLayout labels = new LinearLayout(service);
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

            appIcon = new ImageView(service);
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

            actions = new LinearLayout(service);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            TextView reply = button("Trả lời", prefs.getInt("reply_color", prefs.getInt("accent_color", 0xFFA680FF)));
            reply.setOnClickListener(v -> showReplyInput());
            TextView dismiss = button("Bỏ qua", 0xFF353B49);
            dismiss.setOnClickListener(v -> IslandNotificationListener.dismissNotification(this.item.key));
            TextView open = button("Mở", 0x223D8EFF);
            open.setTextColor(prefs.getInt("accent_color", 0xFFA680FF));
            open.setOnClickListener(v -> IslandNotificationListener.openNotification(this.item.key));
            actions.addView(reply, new LinearLayout.LayoutParams(-2, dp(30)));
            LinearLayout.LayoutParams dismissLp = new LinearLayout.LayoutParams(-2, dp(30));
            dismissLp.setMargins(dp(6), 0, 0, 0);
            actions.addView(dismiss, dismissLp);
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-2, dp(30));
            openLp.setMargins(dp(6), 0, 0, 0);
            actions.addView(open, openLp);
            root.addView(actions, new LinearLayout.LayoutParams(-1, dp(32)));

            replyRow = new LinearLayout(service);
            replyRow.setOrientation(LinearLayout.HORIZONTAL);
            replyRow.setGravity(Gravity.CENTER_VERTICAL);
            replyInput = new EditText(service);
            replyInput.setSingleLine(true);
            replyInput.setTextSize(11);
            replyInput.setTextColor(prefs.getInt("text_color", Color.WHITE));
            replyInput.setHintTextColor(0xFF8E94A4);
            replyInput.setHint("Nhập nội dung trả lời…");
            replyInput.setPadding(dp(9), 0, dp(8), 0);
            replyInput.setBackground(surface(0xFF252B38, dp(10), 0x554F5A70, dp(1)));
            replyRow.addView(replyInput, new LinearLayout.LayoutParams(0, dp(38), 1));
            TextView send = button("Gửi", prefs.getInt("reply_color", prefs.getInt("accent_color", 0xFFA680FF)));
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
                    prefs.getInt("border_color", prefs.getInt("accent_color", 0xFFA680FF)), dp(1)));
            senderLabel.setTextColor(prefs.getInt("text_color", Color.WHITE));
            appLabel.setTextColor(prefs.getInt("secondary_text_color", 0xFFAAAEBB));
            bodyLabel.setTextColor(prefs.getInt("notification_color", Color.WHITE));
            replyInput.setTextColor(prefs.getInt("text_color", Color.WHITE));
            replyInput.setBackground(surface(0xFF252B38, dp(10), 0x554F5A70, dp(1)));
            ((TextView) actions.getChildAt(0)).setBackground(surface(
                    prefs.getInt("reply_color", prefs.getInt("accent_color", 0xFFA680FF)), dp(10), 0, 0));
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
        return headerParams == null || !overlap(x, y, moved.params.width, dp(NOTIFICATION_CARD_HEIGHT_DP),
                headerParams.x, headerParams.y, headerParams.width, dp(42));
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
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

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(service);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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

    private void removeAll() {
        removeNotificationOverlays();
        removeMedia();
        lastMediaSignature = "";
    }

    public void close() {
        closed = true;
        removeAll();
        handler.removeCallbacksAndMessages(null);
    }
}
