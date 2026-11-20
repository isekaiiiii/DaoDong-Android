package com.ayane.dynamicisland;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Dịch vụ Trợ năng tùy chọn của Ayane.
 * Chỉ nhận sự kiện thay đổi cửa sổ (TYPE_WINDOW_STATE_CHANGED), không truy xuất
 * cây giao diện, nội dung, văn bản hoặc trường nhập của ứng dụng khác.
 * Chụp màn hình chỉ thực hiện sau thao tác bấm rõ ràng của người dùng trong Droidbar.
 */
public final class AyaneAccessibilityService extends AccessibilityService {
    static volatile AyaneAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String lastPackageName = "";
    private final Runnable refreshMediaRunnable = IslandNotificationListener::refreshMediaState;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        updateDeviceState();
        IslandService.onAccessibilityServiceChanged(this);
        mainHandler.removeCallbacks(refreshMediaRunnable);
        mainHandler.postDelayed(refreshMediaRunnable, 180L);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence packageName = event.getPackageName();
        String currentPackage = packageName == null ? "" : packageName.toString();
        if (currentPackage.equals(lastPackageName)) return;
        lastPackageName = currentPackage;
        updateDeviceState();
        // Giống luồng Droidbubble: cập nhật MediaSession khi người dùng chuyển ứng dụng.
        mainHandler.removeCallbacks(refreshMediaRunnable);
        mainHandler.postDelayed(refreshMediaRunnable, 180L);
        IslandService.onAccessibleWindowChanged();
    }

    @Override
    public void onInterrupt() {
        // Không can thiệp thao tác chạm hoặc phát âm thanh.
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        disconnectService();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        disconnectService();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void disconnectService() {
        if (instance == this) {
            instance = null;
            IslandService.onAccessibilityServiceChanged(null);
        }
    }

    private void updateDeviceState() {
        try {
            KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            boolean locked = keyguard != null && keyguard.isKeyguardLocked();
            getSharedPreferences("island_settings", MODE_PRIVATE).edit()
                    .putBoolean("device_locked", locked)
                    .apply();
        } catch (RuntimeException ignored) { }
    }

    public static boolean isEnabled(Context context) {
        try {
            String enabled = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || enabled.isEmpty()) return false;
            ComponentName expected = new ComponentName(context, AyaneAccessibilityService.class);
            for (String item : enabled.split(":")) {
                ComponentName actual = ComponentName.unflattenFromString(item);
                if (expected.equals(actual)) return true;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    private static boolean invokeGlobalAction(int action) {
        AyaneAccessibilityService service = instance;
        if (service == null) return false;
        try {
            return service.performGlobalAction(action);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean openNotificationShade() {
        return invokeGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public static boolean openQuickSettings() {
        return invokeGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    public static void openQuickSettings(Context context) {
        if (!openQuickSettings()) {
            Toast.makeText(context, "Hãy bật Trợ năng Ayane để mở Cài đặt nhanh", Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean openRecentApps() {
        return invokeGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public static boolean toggleSplitScreen() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && performGlobalAction(&& performGlobalAction( invokeGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
    }

    /** Chỉ được gọi khi người dùng chủ động chạm nút Chụp màn hình trong thanh công cụ. */
    public static void captureScreenshot(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(context, "Chụp màn hình từ Ayane cần Android 11 trở lên", Toast.LENGTH_SHORT).show();
            return;
        }
        AyaneAccessibilityService service = instance;
        if (service == null) {
            Toast.makeText(context, "Hãy bật Trợ năng Ayane trước khi chụp màn hình", Toast.LENGTH_LONG).show();
            try {
                android.content.Intent intent = new android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (RuntimeException ignored) { }
            return;
        }
        service.captureScreenAfterTap();
    }

    private void captureScreenAfterTap() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Executor executor = getMainExecutor();
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, executor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    HardwareBuffer buffer = null;
                    Bitmap wrapped = null;
                    Bitmap copy = null;
                    try {
                        buffer = result.getHardwareBuffer();
                        ColorSpace colorSpace = result.getColorSpace();
                        if (buffer == null) throw new IllegalStateException("Không nhận được dữ liệu ảnh");
                        if (colorSpace == null) colorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
                        wrapped = Bitmap.wrapHardwareBuffer(buffer, colorSpace);
                        if (wrapped == null) throw new IllegalStateException("Không đọc được ảnh màn hình");
                        copy = wrapped.copy(Bitmap.Config.ARGB_8888, false);
                        if (copy == null) throw new IllegalStateException("Không tạo được bản sao ảnh");
                    } catch (RuntimeException error) {
                        Toast.makeText(AyaneAccessibilityService.this,
                                "Không thể chụp màn hình trên thiết bị này", Toast.LENGTH_SHORT).show();
                    } finally {
                        if (wrapped != null && !wrapped.isRecycled()) {
                            try { wrapped.recycle(); } catch (RuntimeException ignored) { }
                        }
                        if (buffer != null) {
                            try { buffer.close(); } catch (RuntimeException ignored) { }
                        }
                    }
                    if (copy != null) saveScreenshot(copy);
                }

                @Override
                public void onFailure(int errorCode) {
                    Toast.makeText(AyaneAccessibilityService.this,
                            "Chụp màn hình thất bại · mã " + errorCode, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (RuntimeException error) {
            Toast.makeText(this, "Ayane chưa thể chụp màn hình qua Trợ năng trên thiết bị này", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveScreenshot(Bitmap bitmap) {
        new Thread(() -> {
            Uri uri = null;
            try {
                ContentResolver resolver = getContentResolver();
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "Ayane_Screenshot_" + stamp + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Ayane");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Không tạo được tệp ảnh");
                try (OutputStream output = resolver.openOutputStream(uri)) {
                    if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new IllegalStateException("Không lưu được ảnh");
                    }
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, ready, null, null);
                runOnUiThreadToast("Đã lưu ảnh vào Ảnh/Pictures/Ayane");
            } catch (Exception e) {
                if (uri != null) {
                    try { getContentResolver().delete(uri, null, null); } catch (RuntimeException ignored) { }
                }
                runOnUiThreadToast("Không thể lưu ảnh màn hình");
            } finally {
                if (!bitmap.isRecycled()) bitmap.recycle();
            }
        }, "Ayane-Screenshot-Saver").start();
    }

    private void runOnUiThreadToast(String message) {
        new Handler(getMainLooper()).post(() ->
                Toast.makeText(AyaneAccessibilityService.this, message, Toast.LENGTH_SHORT).show());
    }
}
