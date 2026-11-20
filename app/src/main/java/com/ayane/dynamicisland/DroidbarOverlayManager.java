package com.ayane.dynamicisland;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Thanh công cụ cạnh màn hình lấy cảm hứng từ Droidbar, viết độc lập cho Ayane. */
public final class DroidbarOverlayManager {
    private final Service service;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private final Context overlayContext;
    private View handle;
    private WindowManager.LayoutParams handleParams;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private boolean expanded;
    private float downRawY;
    private int startY;
    private boolean moved;
    private boolean closed;

    public DroidbarOverlayManager(Service service, WindowManager windowManager, SharedPreferences prefs) {
        this.service = service;
        this.windowManager = windowManager;
        this.prefs = prefs;
        this.overlayContext = AyaneAccessibilityService.instance != null
                ? AyaneAccessibilityService.instance : service;
    }

    public void update() {
        if (closed || !Settings.canDrawOverlays(service)
                || !prefs.getBoolean("droidbar_enabled", false)) {
            removeAll();
            return;
        }
        ensureHandle();
    }

    private void ensureHandle() {
        if (handle != null) return;
        TextView pill = new TextView(overlayContext);
        pill.setText("✦");
        pill.setTextSize(17);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setTextColor(Color.WHITE);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(background(0xEE15131F, 0xFFD6A3FF, dp(18), dp(1)));
        pill.setElevation(dp(10));
        pill.setContentDescription("Mở thanh công cụ Ayane");
        pill.setOnTouchListener(this::onHandleTouch);
        boolean accessibilityOverlay = overlayContext instanceof AyaneAccessibilityService;
        int type = accessibilityOverlay ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : (Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (accessibilityOverlay) flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        handleParams = new WindowManager.LayoutParams(dp(28), dp(76), type, flags, PixelFormat.TRANSLUCENT);
        if (accessibilityOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            handleParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        handleParams.gravity = Gravity.TOP | Gravity.RIGHT;
        handleParams.x = 0;
        handleParams.y = Math.max(dp(90), prefs.getInt("droidbar_y", 520));
        try {
            windowManager.addView(pill, handleParams);
            handle = pill;
        } catch (RuntimeException error) {
            handle = null;
            handleParams = null;
        }
    }

    private boolean onHandleTouch(View view, MotionEvent event) {
        if (handleParams == null) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downRawY = event.getRawY();
                startY = handleParams.y;
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dy = Math.round(event.getRawY() - downRawY);
                if (Math.abs(dy) > dp(5)) moved = true;
                if (moved) {
                    handleParams.y = clamp(startY + dy, dp(30), screenHeight() - dp(100));
                    try { windowManager.updateViewLayout(handle, handleParams); } catch (RuntimeException ignored) { }
                    if (expanded) positionPanel();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (moved) prefs.edit().putInt("droidbar_y", handleParams.y).apply();
                else {
                    if (prefs.getBoolean("droidbar_haptics", true)) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    togglePanel();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return true;
    }

    private void togglePanel() {
        if (expanded) hidePanel(); else showPanel();
    }

    private void showPanel() {
        if (panel != null) { expanded = true; panel.setVisibility(View.VISIBLE); return; }
        LinearLayout root = new LinearLayout(overlayContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(13), dp(14), dp(14));
        root.setBackground(background(0xF2181A27, prefs.getInt("accent_color", 0xFFD6A3FF), dp(22), dp(1)));
        root.setElevation(dp(18));

        LinearLayout header = new LinearLayout(overlayContext);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("AYANE · CÔNG CỤ", 11, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView close = label("×", 24, 0xFFCCC5D8, true);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> hidePanel());
        header.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        root.addView(header);

        TextView hint = label("Lối tắt hệ thống · chạm để thực hiện", 9, 0xFF9EA3B6, false);
        root.addView(hint, top(-2, 1));

        LinearLayout row1 = actionRow();
        row1.addView(action("ϟ", "Đèn pin", this::toggleTorch), weightCell());
        row1.addView(action("⌁", "Internet", this::openInternetPanel), weightCell());
        root.addView(row1, top(-2, 10));

        LinearLayout row2 = actionRow();
        row2.addView(action("◖", "Âm thanh", this::toggleRinger), weightCell());
        row2.addView(action("▣", "Chụp màn hình", () -> AyaneAccessibilityService.captureScreenshot(service)), weightCell());
        root.addView(row2, top(-2, 7));

        LinearLayout row3 = actionRow();
        row3.addView(action("☷", "Cài đặt nhanh", () -> AyaneAccessibilityService.openQuickSettings(service)), weightCell());
        row3.addView(action("✿", "Mở Ayane", this::openAyane), weightCell());
        root.addView(row3, top(-2, 7));

        TextView appTitle = label("ỨNG DỤNG GHIM", 9, 0xFFA9A4B8, true);
        root.addView(appTitle, top(-2, 14));
        addPinnedApps(root);
        TextView chooseApps = label("＋  CHỌN ỨNG DỤNG", 10, prefs.getInt("accent_color", 0xFFD6A3FF), true);
        chooseApps.setGravity(Gravity.CENTER);
        chooseApps.setPadding(dp(8), dp(10), dp(8), dp(10));
        chooseApps.setBackground(background(0x22202030, 0x44D6A3FF, dp(12), dp(1)));
        chooseApps.setOnClickListener(v -> openAppPicker());
        root.addView(chooseApps, top(-2, 9));

        boolean accessibilityOverlay = overlayContext instanceof AyaneAccessibilityService;
        int type = accessibilityOverlay ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : (Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (accessibilityOverlay) flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        panelParams = new WindowManager.LayoutParams(dp(238), -2, type, flags, PixelFormat.TRANSLUCENT);
        if (accessibilityOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            panelParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        panelParams.gravity = Gravity.TOP | Gravity.RIGHT;
        panelParams.x = dp(34);
        panel = root;
        expanded = true;
        positionPanel();
        try { windowManager.addView(panel, panelParams); }
        catch (RuntimeException error) { panel = null; panelParams = null; expanded = false; }
    }

    private void addPinnedApps(LinearLayout parent) {
        Set<String> pinned = prefs.getStringSet("droidbar_pinned_apps", Collections.<String>emptySet());
        LinearLayout row = actionRow();
        int count = 0;
        for (String packageName : pinned) {
            if (count >= 4) break;
            try {
                PackageManager pm = service.getPackageManager();
                Intent launch = pm.getLaunchIntentForPackage(packageName);
                if (launch == null) continue;
                LinearLayout item = new LinearLayout(overlayContext);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);
                ImageView icon = new ImageView(overlayContext);
                icon.setImageDrawable(pm.getApplicationIcon(packageName));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                item.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
                CharSequence appName = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0));
                TextView name = label(String.valueOf(appName), 8, 0xFFE6E3ED, false);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(1);
                item.addView(name, top(-2, 4));
                item.setOnClickListener(v -> {
                    try {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        service.startActivity(launch);
                        hidePanel();
                    } catch (RuntimeException ignored) { }
                });
                row.addView(item, weightCell());
                count++;
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
        }
        if (count == 0) {
            TextView empty = label("Chưa ghim ứng dụng nào", 9, 0xFF9EA3B6, false);
            row.addView(empty, new LinearLayout.LayoutParams(-1, dp(32)));
        }
        parent.addView(row, top(-2, 7));
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(overlayContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams weightCell() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private View action(String glyph, String title, Runnable callback) {
        LinearLayout cell = new LinearLayout(overlayContext);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(4), dp(9), dp(4), dp(9));
        cell.setBackground(background(0xFF252737, 0x332F3147, dp(14), dp(1)));
        TextView symbol = label(glyph, 19, prefs.getInt("accent_color", 0xFFD6A3FF), true);
        symbol.setGravity(Gravity.CENTER);
        cell.addView(symbol, new LinearLayout.LayoutParams(-1, dp(25)));
        TextView name = label(title, 9, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        cell.addView(name, top(-2, 4));
        cell.setOnClickListener(v -> callback.run());
        return cell;
    }

    private void toggleTorch() {
        if (Build.VERSION.SDK_INT >= 23
                && service.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Intent permission = new Intent(service, MainActivity.class);
            permission.setAction(MainActivity.ACTION_REQUEST_TORCH_PERMISSION);
            permission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try { service.startActivity(permission); } catch (RuntimeException ignored) { }
            Toast.makeText(service, "Ayane chỉ dùng quyền camera để điều khiển đèn pin", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            CameraManager manager = (CameraManager) service.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) throw new IllegalStateException();
            String selected = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Boolean flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) { selected = id; break; }
            }
            if (selected == null) throw new IllegalStateException();
            boolean next = !prefs.getBoolean("droidbar_torch_on", false);
            manager.setTorchMode(selected, next);
            prefs.edit().putBoolean("droidbar_torch_on", next).apply();
            Toast.makeText(service, next ? "Đã bật đèn pin" : "Đã tắt đèn pin", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(service, "Không thể điều khiển đèn pin trên thiết bị này", Toast.LENGTH_SHORT).show();
        }
    }

    public static void toggleTorchAfterPermission(Context context) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return;
            SharedPreferences prefs = context.getSharedPreferences("island_settings", Context.MODE_PRIVATE);
            String selected = null;
            for (String id : manager.getCameraIdList()) {
                Boolean flash = manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) { selected = id; break; }
            }
            if (selected == null) throw new IllegalStateException();
            boolean next = !prefs.getBoolean("droidbar_torch_on", false);
            manager.setTorchMode(selected, next);
            prefs.edit().putBoolean("droidbar_torch_on", next).apply();
            Toast.makeText(context, next ? "Đã bật đèn pin" : "Đã tắt đèn pin", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(context, "Không thể bật đèn pin", Toast.LENGTH_SHORT).show();
        }
    }

    private void openInternetPanel() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= 29) {
                intent = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            } else {
                intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            service.startActivity(intent);
        } catch (RuntimeException e) {
            try { service.startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (RuntimeException ignored) { }
        }
    }

    private void toggleRinger() {
        try {
            AudioManager audio = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            int current = audio.getRingerMode();
            int next = current == AudioManager.RINGER_MODE_NORMAL
                    ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL;
            audio.setRingerMode(next);
            Toast.makeText(service, next == AudioManager.RINGER_MODE_VIBRATE
                    ? "Đã chuyển sang rung" : "Đã bật chuông", Toast.LENGTH_SHORT).show();
        } catch (SecurityException error) {
            Toast.makeText(service, "Android yêu cầu quyền quản lý chế độ Không làm phiền", Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                service.startActivity(intent);
            } catch (RuntimeException ignored) { }
        }
    }

    private void openAppPicker() {
        Intent intent = new Intent(service, MainActivity.class);
        intent.setAction(MainActivity.ACTION_PICK_DROIDBAR_APPS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { service.startActivity(intent); } catch (RuntimeException ignored) { }
        hidePanel();
    }

    private void openAyane() {
        Intent intent = new Intent(service, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { service.startActivity(intent); } catch (RuntimeException ignored) { }
        hidePanel();
    }

    private void positionPanel() {
        if (panelParams == null) return;
        int y = handleParams == null ? prefs.getInt("droidbar_y", screenHeight() / 2) : handleParams.y;
        int heightEstimate = dp(300);
        panelParams.y = clamp(y - dp(110), dp(26), Math.max(dp(26), screenHeight() - heightEstimate));
        try { if (panel != null && panel.getParent() != null) windowManager.updateViewLayout(panel, panelParams); }
        catch (RuntimeException ignored) { }
    }

    private void hidePanel() {
        expanded = false;
        if (panel != null) {
            try { windowManager.removeView(panel); } catch (RuntimeException ignored) { }
        }
        panel = null;
        panelParams = null;
    }

    private void removeAll() {
        hidePanel();
        if (handle != null) {
            try { windowManager.removeView(handle); } catch (RuntimeException ignored) { }
        }
        handle = null;
        handleParams = null;
    }

    public void close() {
        closed = true;
        removeAll();
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(overlayContext);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable background(int fill, int stroke, int radius, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{fill, blend(fill, prefs.getInt("accent_color", 0xFFD6A3FF), 18)});
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private int blend(int base, int accent, int amount) {
        int a = Math.max(0, Math.min(100, amount));
        return Color.rgb((Color.red(base) * (100-a) + Color.red(accent) * a) / 100,
                (Color.green(base) * (100-a) + Color.green(accent) * a) / 100,
                (Color.blue(base) * (100-a) + Color.blue(accent) * a) / 100);
    }

    private LinearLayout.LayoutParams top(int height, int marginTop) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, height);
        lp.topMargin = dp(marginTop);
        return lp;
    }

    private int dp(float value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private int screenHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        return metrics.heightPixels;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
