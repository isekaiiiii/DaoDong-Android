package com.example.dynamicisland;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {
    private static final String PREFS = "island_settings";
    private static final int PURPLE = Color.rgb(166, 128, 255);
    private static final int BLUE = Color.rgb(91, 170, 255);
    private static final int GREEN = Color.rgb(93, 220, 167);
    private static final int PINK = Color.rgb(255, 126, 183);

    private SharedPreferences prefs;
    private TextView permissionStatus;
    private TextView runningStatus;
    private LinearLayout page;
    private int accent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        accent = prefs.getInt("accent_color", PURPLE);
        buildScreen();
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionStatus != null) updatePermissionStatus();
        if (runningStatus != null) updateRunningStatus();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12, 13, 18));

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(36));
        scroll.addView(page);

        TextView eyebrow = text("TÙY BIẾN GIAO DIỆN ANDROID", 10, accent, true);
        eyebrow.setLetterSpacing(0.12f);
        page.addView(eyebrow);

        LinearLayout titleRow = row();
        TextView title = text("Đảo động", 32, Color.WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView version = text("BẢN 1.1", 10, 0xFFD2C7FF, true);
        version.setPadding(dp(10), dp(7), dp(10), dp(7));
        version.setBackground(roundRect(0x332A2540, dp(30), 0x665A4C7E, dp(1)));
        titleRow.addView(version);
        addTop(page, titleRow, 2);

        TextView subtitle = text("Dynamic Island nổi • Gọn đẹp • Tùy chỉnh theo ý bạn", 13, 0xFFAAAAB8, false);
        addTop(page, subtitle, 3);

        LinearLayout hero = card();
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout heroTop = row();
        TextView heroLabel = text("BẢN XEM TRƯỚC", 10, 0xFFB7B3C7, true);
        heroTop.addView(heroLabel, new LinearLayout.LayoutParams(0, -2, 1));
        runningStatus = text("● ĐANG TẮT", 10, 0xFFFFC66D, true);
        heroTop.addView(runningStatus);
        hero.addView(heroTop);

        LinearLayout previewArea = new LinearLayout(this);
        previewArea.setGravity(Gravity.CENTER);
        previewArea.setPadding(0, dp(18), 0, dp(14));
        TextView previewPill = text("♫   Đảo động của bạn     ●", 12, Color.WHITE, true);
        previewPill.setGravity(Gravity.CENTER);
        previewPill.setPadding(dp(20), dp(13), dp(20), dp(13));
        previewPill.setBackground(roundRect(Color.BLACK, dp(80), accent, dp(1)));
        previewArea.addView(previewPill, new LinearLayout.LayoutParams(-1, -2));
        hero.addView(previewArea);

        Button enable = button("BẬT ĐẢO ĐỘNG", accent);
        enable.setOnClickListener(v -> enableIsland());
        hero.addView(enable, fullWidthTop(48, 8));
        Button stop = outlineButton("TẮT ĐẢO ĐỘNG");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, IslandService.class));
            updateRunningStatus();
            toast("Đã tắt Đảo động");
        });
        hero.addView(stop, fullWidthTop(44, 8));
        page.addView(hero, fullWidthTop(-2, 20));

        addSectionTitle(page, "01", "Quyền cần thiết", "Cấp quyền để đảo động hiển thị trên mọi ứng dụng.");
        LinearLayout permissions = card();
        permissions.setOrientation(LinearLayout.VERTICAL);
        permissions.setPadding(dp(14), dp(8), dp(14), dp(8));
        addPermissionRow(permissions, "Hiển thị trên ứng dụng khác", "Cho phép hiển thị cửa sổ nổi", true);
        addDivider(permissions);
        addPermissionRow(permissions, "Truy cập thông báo", "Đọc thông báo để hiện nội dung trên đảo", false);
        permissionStatus = text("Đang kiểm tra quyền…", 11, 0xFFFFC66D, false);
        permissions.addView(permissionStatus, fullWidthTop(-2, 10));
        page.addView(permissions, fullWidthTop(-2, 8));

        addSectionTitle(page, "02", "Kích thước & vị trí", "Kéo trực tiếp đảo động hoặc tinh chỉnh bằng thanh trượt.");
        LinearLayout dimensions = card();
        dimensions.setOrientation(LinearLayout.VERTICAL);
        dimensions.setPadding(dp(15), dp(12), dp(15), dp(14));
        addSlider(dimensions, "Chiều rộng", "width", 140, 340, 220, " dp");
        addSlider(dimensions, "Chiều cao", "height", 38, 96, 58, " dp");
        addSlider(dimensions, "Vị trí ngang · trái / phải", "x", -260, 260, 0, " dp");
        addSlider(dimensions, "Vị trí dọc · lên / xuống", "y", 0, 520, 12, " dp");
        addSlider(dimensions, "Độ bo góc", "corner_radius", 18, 80, 32, " dp");
        Button center = outlineButton("CĂN GIỮA ĐẢO ĐỘNG");
        center.setOnClickListener(v -> {
            prefs.edit().putInt("x", 0).putInt("y", 12).apply();
            refreshIsland();
            buildScreen();
            toast("Đã đưa đảo động về vị trí mặc định");
        });
        dimensions.addView(center, fullWidthTop(44, 14));
        page.addView(dimensions, fullWidthTop(-2, 8));

        addSectionTitle(page, "03", "Màu sắc", "Chọn màu viền và điểm nhấn cho đảo động.");
        LinearLayout colors = card();
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setGravity(Gravity.CENTER_VERTICAL);
        colors.setPadding(dp(8), dp(12), dp(8), dp(12));
        addColorChoice(colors, "Tím", PURPLE);
        addColorChoice(colors, "Xanh", BLUE);
        addColorChoice(colors, "Lục", GREEN);
        addColorChoice(colors, "Hồng", PINK);
        page.addView(colors, fullWidthTop(-2, 8));

        addSectionTitle(page, "04", "Thông tin hiển thị", "Bật những nội dung bạn muốn xem trên đảo động.");
        LinearLayout features = card();
        features.setOrientation(LinearLayout.VERTICAL);
        features.setPadding(dp(14), dp(6), dp(14), dp(6));
        addSwitch(features, "Thông báo mới", "Hiện tiêu đề và nội dung thông báo", "show_notifications", true);
        addDivider(features);
        addSwitch(features, "Trình phát nhạc", "Tên bài hát, nghệ sĩ và trạng thái phát", "show_media", true);
        addDivider(features);
        addSwitch(features, "Pin & trạng thái sạc", "Phần trăm pin và biểu tượng sạc", "show_battery", true);
        addDivider(features);
        addSwitch(features, "Tốc độ mạng", "Tốc độ dữ liệu tải xuống gần đúng", "show_network", true);
        page.addView(features, fullWidthTop(-2, 8));

        addSectionTitle(page, "05", "Hẹn giờ thông minh", "Đếm ngược và theo dõi ngay trên đảo động.");
        LinearLayout timer = card();
        timer.setOrientation(LinearLayout.VERTICAL);
        timer.setPadding(dp(14), dp(14), dp(14), dp(14));
        TextView timerHint = text("Nhập thời gian theo phút (1 đến 1.440 phút).", 12, 0xFFB6B6C3, false);
        timer.addView(timerHint);
        EditText minutes = new EditText(this);
        minutes.setSingleLine(true);
        minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutes.setHint("Ví dụ: 25");
        minutes.setHintTextColor(0xFF777784);
        minutes.setTextColor(Color.WHITE);
        minutes.setTextSize(16);
        minutes.setPadding(dp(12), 0, dp(12), 0);
        minutes.setBackground(roundRect(0xFF20212A, dp(12), 0xFF383946, dp(1)));
        timer.addView(minutes, fullWidthTop(50, 10));
        Button startTimer = button("BẮT ĐẦU ĐẾM NGƯỢC", accent);
        startTimer.setOnClickListener(v -> {
            try {
                int mins = Integer.parseInt(minutes.getText().toString().trim());
                if (mins < 1 || mins > 1440) throw new NumberFormatException();
                prefs.edit().putLong("timer_end", System.currentTimeMillis() + mins * 60_000L).apply();
                refreshIsland();
                toast("Đã bắt đầu đếm ngược " + mins + " phút");
            } catch (NumberFormatException e) {
                toast("Vui lòng nhập từ 1 đến 1.440 phút");
            }
        });
        timer.addView(startTimer, fullWidthTop(46, 10));
        Button clearTimer = outlineButton("HỦY BỘ ĐẾM");
        clearTimer.setOnClickListener(v -> {
            prefs.edit().remove("timer_end").apply();
            refreshIsland();
            toast("Đã hủy bộ đếm");
        });
        timer.addView(clearTimer, fullWidthTop(42, 8));
        page.addView(timer, fullWidthTop(-2, 8));

        addSectionTitle(page, "06", "Thử nhanh", "Xem thử hoạt ảnh mà không cần chờ thông báo mới.");
        Button demo = button("CHẠY THỬ HIỆU ỨNG 5 GIÂY", accent);
        demo.setOnClickListener(v -> {
            prefs.edit().putLong("demo_until", System.currentTimeMillis() + 5000L).apply();
            enableIsland();
            refreshIsland();
        });
        page.addView(demo, fullWidthTop(48, 8));

        TextView footer = text("LƯU Ý BẢO MẬT\nQuyền truy cập thông báo có thể cho phép ứng dụng đọc nội dung thông báo. Hãy chỉ bật khi bạn đồng ý. Bản này xử lý dữ liệu thông báo trên thiết bị, không gửi nội dung lên máy chủ.", 11, 0xFF858592, false);
        footer.setLineSpacing(dp(3), 1f);
        page.addView(footer, fullWidthTop(-2, 24));
        setContentView(scroll);
        updatePermissionStatus();
        updateRunningStatus();
    }

    private void addPermissionRow(LinearLayout parent, String title, String subtitle, boolean overlay) {
        LinearLayout r = row();
        r.setPadding(0, dp(10), 0, dp(10));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 13, Color.WHITE, true));
        TextView sub = text(subtitle, 10, 0xFF9999A7, false);
        labels.addView(sub, fullWidthTop(-2, 3));
        r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Button grant = smallButton("CẤP QUYỀN");
        grant.setOnClickListener(v -> {
            if (overlay) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });
        r.addView(grant);
        parent.addView(r);
    }

    private void addSlider(LinearLayout parent, String label, String key,
                           int min, int max, int defaultValue, String suffix) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, dp(8), 0, dp(4));
        LinearLayout labelRow = row();
        TextView name = text(label, 12, 0xFFE8E8F0, false);
        labelRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView valueText = text("", 11, accent, true);
        labelRow.addView(valueText);
        group.addView(labelRow);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        int current = Math.max(min, Math.min(max, prefs.getInt(key, defaultValue)));
        seek.setProgress(current - min);
        seek.setProgressTintList(ColorStateList.valueOf(accent));
        seek.setThumbTintList(ColorStateList.valueOf(accent));
        valueText.setText(current + suffix);
        group.addView(seek, fullWidthTop(34, 2));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = progress + min;
                valueText.setText(value + suffix);
                if (fromUser) {
                    prefs.edit().putInt(key, value).apply();
                    refreshIsland();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(group);
    }

    private void addColorChoice(LinearLayout parent, String label, int color) {
        LinearLayout choice = new LinearLayout(this);
        choice.setOrientation(LinearLayout.VERTICAL);
        choice.setGravity(Gravity.CENTER);
        choice.setPadding(dp(4), dp(4), dp(4), dp(4));
        TextView dot = text("●", 29, color, true);
        dot.setGravity(Gravity.CENTER);
        choice.addView(dot);
        TextView name = text(label, 10, color == accent ? Color.WHITE : 0xFF9E9EAC, true);
        name.setGravity(Gravity.CENTER);
        choice.addView(name, fullWidthTop(-2, 2));
        choice.setOnClickListener(v -> {
            accent = color;
            prefs.edit().putInt("accent_color", color).apply();
            refreshIsland();
            buildScreen();
            toast("Đã đổi màu giao diện");
        });
        parent.addView(choice, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void addSwitch(LinearLayout parent, String title, String subtitle,
                           String key, boolean defaultValue) {
        LinearLayout r = row();
        r.setPadding(0, dp(9), 0, dp(9));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 13, Color.WHITE, true));
        labels.addView(text(subtitle, 10, 0xFF9999A7, false), fullWidthTop(-2, 3));
        r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setButtonTintList(ColorStateList.valueOf(accent));
        toggle.setThumbTintList(ColorStateList.valueOf(accent));
        toggle.setTrackTintList(ColorStateList.valueOf((accent & 0x00FFFFFF) | 0x55000000));
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            refreshIsland();
        });
        r.addView(toggle);
        parent.addView(r);
    }

    private void addSectionTitle(LinearLayout parent, String number, String title, String subtitle) {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView num = text(number, 11, accent, true);
        num.setGravity(Gravity.CENTER);
        num.setBackground(roundRect(0x332A2540, dp(9), 0x554C3D70, dp(1)));
        num.setPadding(dp(9), dp(7), dp(9), dp(7));
        header.addView(num);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, 0, 0);
        labels.addView(text(title, 17, Color.WHITE, true));
        labels.addView(text(subtitle, 10, 0xFF9797A5, false), fullWidthTop(-2, 2));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        addTop(parent, header, 22);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(0xFF30313B);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void enableIsland() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Hãy cấp quyền hiển thị trên ứng dụng khác trước");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent service = new Intent(this, IslandService.class);
        service.setAction(IslandService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        updateRunningStatus();
        toast("Đảo động đã được bật");
    }

    private void refreshIsland() {
        if (!IslandService.running) return;
        Intent service = new Intent(this, IslandService.class);
        service.setAction(IslandService.ACTION_REFRESH);
        startService(service);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
        }
    }

    private void updatePermissionStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean listener = IslandNotificationListener.isEnabled(this);
        String state = "Hiển thị nổi: " + (overlay ? "ĐÃ CẤP" : "CHƯA CẤP")
                + "   •   Thông báo: " + (listener ? "ĐÃ CẤP" : "CHƯA CẤP");
        permissionStatus.setText(state);
        permissionStatus.setTextColor(overlay && listener ? 0xFF79DFAE : 0xFFFFC66D);
    }

    private void updateRunningStatus() {
        if (runningStatus == null) return;
        boolean isRunning = IslandService.running;
        runningStatus.setText(isRunning ? "● ĐANG HOẠT ĐỘNG" : "● ĐANG TẮT");
        runningStatus.setTextColor(isRunning ? 0xFF79DFAE : 0xFFFFC66D);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setBackground(roundRect(0xFF191A23, dp(18), 0xFF2B2C37, dp(1)));
        return box;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(roundRect(color, dp(13), color, 0));
        return b;
    }

    private Button outlineButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(0xFFE8E3F5);
        b.setAllCaps(false);
        b.setBackground(roundRect(0xFF22232D, dp(12), 0xFF444552, dp(1)));
        return b;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(9);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(roundRect(0xFF41345F, dp(9), 0xFF65518F, dp(1)));
        return b;
    }

    private GradientDrawable roundRect(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private LinearLayout.LayoutParams fullWidthTop(int height, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, height);
        lp.topMargin = dp(top);
        return lp;
    }

    private void addTop(LinearLayout parent, View child, int top) {
        parent.addView(child, fullWidthTop(-2, top));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
