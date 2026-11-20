package com.example.dynamicisland;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "island_settings";
    private static final int PURPLE = Color.rgb(166, 128, 255);
    private static final int BLUE = Color.rgb(91, 170, 255);
    private static final int GREEN = Color.rgb(93, 220, 167);
    private static final int PINK = Color.rgb(255, 126, 183);
    private static final int GOLD = Color.rgb(255, 195, 105);
    private static final int PICK_ISLAND_ICON = 42;

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
        scroll.setBackgroundColor(0xFF080A11);

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(38));
        scroll.addView(page);

        TextView eyebrow = text("DYNAMIC ISLAND • ANDROID", 10, accent, true);
        eyebrow.setLetterSpacing(0.16f);
        page.addView(eyebrow);

        LinearLayout titleRow = row();
        TextView title = text("Đảo động", 32, Color.WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView version = text("V2.0 STUDIO", 10, 0xFFD9CEFF, true);
        version.setPadding(dp(11), dp(7), dp(11), dp(7));
        version.setBackground(roundRect(0x332A2540, dp(30), 0x775A4C7E, dp(1)));
        titleRow.addView(version);
        addTop(page, titleRow, 3);
        addTop(page, text("Phong cách iPhone • Điều khiển thông minh • Tùy biến sâu", 13, 0xFFAAAEBB, false), 4);

        LinearLayout hero = card();
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout heroTop = row();
        heroTop.addView(text("BẢN XEM TRƯỚC", 10, 0xFFB7B3C7, true), new LinearLayout.LayoutParams(0, -2, 1));
        runningStatus = text("● ĐANG TẮT", 10, GOLD, true);
        heroTop.addView(runningStatus);
        hero.addView(heroTop);

        LinearLayout previewArea = new LinearLayout(this);
        previewArea.setGravity(Gravity.CENTER);
        previewArea.setPadding(0, dp(22), 0, dp(18));
        TextView previewPill = text("◉   Đang phát   ᴗ", 13, Color.WHITE, true);
        previewPill.setGravity(Gravity.CENTER);
        previewPill.setPadding(dp(28), dp(14), dp(28), dp(14));
        previewPill.setBackground(roundRect(prefs.getInt("island_bg_color", 0xFF050507), dp(70),
                prefs.getInt("border_color", accent), dp(1)));
        previewArea.addView(previewPill, new LinearLayout.LayoutParams(-1, -2));
        hero.addView(previewArea);

        Button enable = button("BẬT ĐẢO ĐỘNG", accent);
        enable.setOnClickListener(v -> enableIsland());
        hero.addView(enable, fullWidthTop(50, 8));
        Button stop = outlineButton("TẮT ĐẢO ĐỘNG");
        stop.setOnClickListener(v -> {
            prefs.edit().putBoolean("auto_start", false).putBoolean("user_stopped", true).apply();
            stopService(new Intent(this, IslandService.class));
            buildScreen();
            updateRunningStatus();
            toast("Đã tắt Đảo động và tắt tự khởi động.");
        });
        hero.addView(stop, fullWidthTop(44, 8));
        page.addView(hero, fullWidthTop(-2, 20));

        addSectionTitle(page, "01", "Quyền & hoạt động nền", "Cấp quyền để đảo động hiển thị và nhận thông báo khi bạn rời ứng dụng.");
        LinearLayout permissions = card();
        permissions.setOrientation(LinearLayout.VERTICAL);
        permissions.setPadding(dp(14), dp(8), dp(14), dp(12));
        addPermissionRow(permissions, "Hiển thị trên ứng dụng khác", "Bắt buộc để tạo đảo động nổi", 0);
        addDivider(permissions);
        addPermissionRow(permissions, "Truy cập thông báo", "Đọc thông báo trên thiết bị để hiển thị và thao tác", 1);
        addDivider(permissions);
        addPermissionRow(permissions, "Thông báo dịch vụ nền", "Cho Android biết đảo động đang hoạt động", 2);
        addDivider(permissions);
        addPermissionRow(permissions, "Danh sách ứng dụng đã cài", "Dùng để lấy tên và biểu tượng ứng dụng gửi thông báo", 3);
        permissionStatus = text("Đang kiểm tra quyền…", 11, GOLD, false);
        permissions.addView(permissionStatus, fullWidthTop(-2, 10));
        Button battery = smallButton("MỞ CÀI ĐẶT PIN / CHẠY NỀN");
        battery.setOnClickListener(v -> openBatterySettings());
        permissions.addView(battery, fullWidthTop(44, 10));
        page.addView(permissions, fullWidthTop(-2, 8));

        addSectionTitle(page, "02", "Kích thước & vị trí", "Chiều cao đảo thu gọn và chiều cao bảng mở rộng có thể chỉnh riêng.");
        LinearLayout dimensions = card();
        dimensions.setOrientation(LinearLayout.VERTICAL);
        dimensions.setPadding(dp(15), dp(12), dp(15), dp(14));
        addSlider(dimensions, "Chiều rộng đảo", "width", 150, 380, 235, " dp");
        addSlider(dimensions, "Chiều cao đảo thu gọn", "height", 38, 160, 62, " dp");
        addSlider(dimensions, "Chiều cao khi mở rộng", "expanded_height", 130, 480, 260, " dp");
        addSlider(dimensions, "Vị trí ngang · trái / phải", "x", -300, 300, 0, " dp");
        addSlider(dimensions, "Vị trí dọc · lên / xuống", "y", 0, 800, 12, " dp");
        addSlider(dimensions, "Độ bo góc", "corner_radius", 12, 88, 38, " dp");
        addSlider(dimensions, "Độ dày viền", "border_width", 0, 5, 1, " dp");
        Button center = outlineButton("CĂN GIỮA ĐẢO ĐỘNG");
        center.setOnClickListener(v -> {
            prefs.edit().putInt("x", 0).putInt("y", 12).apply();
            refreshIsland();
            buildScreen();
            toast("Đã căn lại vị trí");
        });
        dimensions.addView(center, fullWidthTop(44, 14));
        page.addView(dimensions, fullWidthTop(-2, 8));

        addSectionTitle(page, "03", "Bộ màu giao diện", "Chọn màu mẫu hoặc tự nhập mã HEX cho từng thành phần.");
        LinearLayout presets = card();
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(Gravity.CENTER_VERTICAL);
        presets.setPadding(dp(8), dp(12), dp(8), dp(12));
        addColorChoice(presets, "Tím", PURPLE);
        addColorChoice(presets, "Xanh", BLUE);
        addColorChoice(presets, "Lục", GREEN);
        addColorChoice(presets, "Hồng", PINK);
        addColorChoice(presets, "Vàng", GOLD);
        page.addView(presets, fullWidthTop(-2, 8));

        LinearLayout colorEditor = card();
        colorEditor.setOrientation(LinearLayout.VERTICAL);
        colorEditor.setPadding(dp(14), dp(12), dp(14), dp(14));
        EditText accentHex = colorInput("Màu nhấn", prefs.getInt("accent_color", PURPLE));
        EditText backgroundHex = colorInput("Màu nền đảo", prefs.getInt("island_bg_color", 0xFF050507));
        EditText textHex = colorInput("Màu chữ", prefs.getInt("text_color", Color.WHITE));
        EditText panelHex = colorInput("Màu nền bảng mở rộng", prefs.getInt("panel_bg_color", 0xFF171923));
        EditText borderHex = colorInput("Màu viền", prefs.getInt("border_color", PURPLE));
        colorEditor.addView(accentHex, fullWidthTop(48, 6));
        colorEditor.addView(backgroundHex, fullWidthTop(48, 8));
        colorEditor.addView(textHex, fullWidthTop(48, 8));
        colorEditor.addView(panelHex, fullWidthTop(48, 8));
        colorEditor.addView(borderHex, fullWidthTop(48, 8));
        Button applyColors = button("ÁP DỤNG TOÀN BỘ MÀU", accent);
        applyColors.setOnClickListener(v -> {
            try {
                int a = Color.parseColor(accentHex.getText().toString().trim());
                int bg = Color.parseColor(backgroundHex.getText().toString().trim());
                int tx = Color.parseColor(textHex.getText().toString().trim());
                int panel = Color.parseColor(panelHex.getText().toString().trim());
                int border = Color.parseColor(borderHex.getText().toString().trim());
                prefs.edit().putInt("accent_color", a).putInt("island_bg_color", bg)
                        .putInt("text_color", tx).putInt("panel_bg_color", panel)
                        .putInt("border_color", border).apply();
                accent = a;
                hideKeyboard();
                refreshIsland();
                buildScreen();
                toast("Đã áp dụng bộ màu mới");
            } catch (IllegalArgumentException e) {
                toast("Mã màu không hợp lệ. Hãy dùng dạng #RRGGBB hoặc #AARRGGBB");
            }
        });
        colorEditor.addView(applyColors, fullWidthTop(48, 12));
        TextView colorHint = text("Ví dụ: #A680FF · #050507 · #FFFFFF", 10, 0xFF9295A4, false);
        colorEditor.addView(colorHint, fullWidthTop(-2, 8));
        page.addView(colorEditor, fullWidthTop(-2, 8));

        addSectionTitle(page, "04", "Tên & biểu tượng ứng dụng", "Tự lấy tên và icon của ứng dụng gửi thông báo, hoặc đặt tên hiển thị riêng.");
        LinearLayout appCard = card();
        appCard.setOrientation(LinearLayout.VERTICAL);
        appCard.setPadding(dp(14), dp(12), dp(14), dp(14));
        EditText customName = input("Tên hiển thị tùy chỉnh (để trống = tên ứng dụng gửi)");
        customName.setText(prefs.getString("custom_app_name", ""));
        appCard.addView(customName, fullWidthTop(50, 2));
        Button saveName = button("LƯU TÊN HIỂN THỊ", accent);
        saveName.setOnClickListener(v -> {
            prefs.edit().putString("custom_app_name", customName.getText().toString().trim()).apply();
            hideKeyboard();
            refreshIsland();
            toast("Đã lưu tên hiển thị");
        });
        appCard.addView(saveName, fullWidthTop(44, 8));
        Button chooseIcon = outlineButton("CHỌN ẢNH LÀM ICON THÔNG BÁO");
        chooseIcon.setOnClickListener(v -> chooseCustomIcon());
        appCard.addView(chooseIcon, fullWidthTop(44, 8));
        Button resetIcon = outlineButton("DÙNG LẠI ICON ỨNG DỤNG GỐC");
        resetIcon.setOnClickListener(v -> {
            prefs.edit().remove("custom_icon_uri").apply();
            refreshIsland();
            toast("Đã khôi phục icon ứng dụng gốc");
        });
        appCard.addView(resetIcon, fullWidthTop(42, 7));
        addSwitch(appCard, "Hiển thị icon ứng dụng", "Dùng icon gốc hoặc ảnh tùy chỉnh ở trên", "show_source_icon", true);
        addDivider(appCard);
        addSwitch(appCard, "Hiển thị tên ứng dụng", "Hiện tên ứng dụng cạnh nội dung", "show_source_app_name", true);
        addDivider(appCard);
        TextView sideLabel = text("Vị trí icon ứng dụng", 12, 0xFFE8E8F0, true);
        appCard.addView(sideLabel, fullWidthTop(-2, 10));
        RadioGroup iconSide = new RadioGroup(this);
        iconSide.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton left = new RadioButton(this);
        left.setText("Bên trái");
        left.setTextColor(Color.WHITE);
        left.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
        left.setId(View.generateViewId());
        RadioButton right = new RadioButton(this);
        right.setText("Bên phải");
        right.setTextColor(Color.WHITE);
        right.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
        right.setId(View.generateViewId());
        iconSide.addView(left);
        iconSide.addView(right);
        iconSide.check(prefs.getInt("icon_position", 0) == 1 ? right.getId() : left.getId());
        iconSide.setOnCheckedChangeListener((group, checkedId) -> {
            prefs.edit().putInt("icon_position", checkedId == right.getId() ? 1 : 0).apply();
            refreshIsland();
        });
        appCard.addView(iconSide);
        page.addView(appCard, fullWidthTop(-2, 8));

        addSectionTitle(page, "05", "Thông báo & thao tác nhanh", "Hiện thông báo, điều khiển nhạc và phản hồi ngay trên đảo động.");
        LinearLayout features = card();
        features.setOrientation(LinearLayout.VERTICAL);
        features.setPadding(dp(14), dp(6), dp(14), dp(6));
        addSwitch(features, "Thông báo mới", "Hiện nội dung thông báo mới nhất", "show_notifications", true);
        addDivider(features);
        addSwitch(features, "Nút trả lời nhanh", "Chỉ xuất hiện khi thông báo hỗ trợ trả lời", "show_reply_actions", true);
        addDivider(features);
        addSwitch(features, "Nút bỏ qua thông báo", "Cho phép đóng thông báo đang hiển thị", "show_dismiss_action", true);
        addDivider(features);
        addSwitch(features, "Trình phát nhạc", "Bài trước, phát / tạm dừng, bài tiếp", "show_media", true);
        addDivider(features);
        addSwitch(features, "Pin & trạng thái sạc", "Phần trăm pin và trạng thái sạc", "show_battery", true);
        addDivider(features);
        addSwitch(features, "Tốc độ mạng", "Tốc độ tải xuống gần đúng", "show_network", true);
        page.addView(features, fullWidthTop(-2, 8));

        addSectionTitle(page, "06", "Chế độ nền & thao tác", "Giữ dịch vụ hoạt động, tự khởi động lại sau khi bật máy nếu Android cho phép.");
        LinearLayout background = card();
        background.setOrientation(LinearLayout.VERTICAL);
        background.setPadding(dp(14), dp(6), dp(14), dp(10));
        addSwitch(background, "Tự bật sau khi khởi động máy", "Yêu cầu quyền hiển thị nổi đã được cấp", "auto_start", true);
        addDivider(background);
        addSwitch(background, "Cho phép kéo đảo động", "Mặc định tắt: khi ra ngoài app chỉ chạm để mở / thu gọn", "allow_drag", false);
        TextView note = text("Dịch vụ nền dùng thông báo thường trực. Android hoặc chế độ tiết kiệm pin của hãng vẫn có thể dừng ứng dụng; không ứng dụng bên thứ ba nào bảo đảm chạy tuyệt đối 24/7.", 10, 0xFFFFC783, false);
        note.setLineSpacing(dp(3), 1f);
        background.addView(note, fullWidthTop(-2, 10));
        page.addView(background, fullWidthTop(-2, 8));

        addSectionTitle(page, "07", "Hẹn giờ thông minh", "Đếm ngược ngay trên đảo động.");
        LinearLayout timer = card();
        timer.setOrientation(LinearLayout.VERTICAL);
        timer.setPadding(dp(14), dp(14), dp(14), dp(14));
        timer.addView(text("Nhập thời gian theo phút (1 đến 1.440 phút).", 12, 0xFFB6B6C3, false));
        EditText minutes = input("Ví dụ: 25");
        minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
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
                toast("Nhập từ 1 đến 1.440 phút");
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

        Button demo = button("CHẠY THỬ GIAO DIỆN", accent);
        demo.setOnClickListener(v -> {
            prefs.edit().putLong("demo_until", System.currentTimeMillis() + 8000L).apply();
            enableIsland();
        });
        page.addView(demo, fullWidthTop(50, 20));

        TextView footer = text("QUYỀN RIÊNG TƯ\nĐảo động đọc thông báo tại chỗ để hiển thị nội dung và thực hiện thao tác mà app gửi hỗ trợ. Thông báo và danh sách ứng dụng không được gửi lên máy chủ. Quyền xem ứng dụng đã cài là quyền nhạy cảm; chỉ bật nếu bạn đồng ý.", 10, 0xFF858A99, false);
        footer.setLineSpacing(dp(3), 1f);
        page.addView(footer, fullWidthTop(-2, 22));

        setContentView(scroll);
        updatePermissionStatus();
        updateRunningStatus();
    }

    private void addPermissionRow(LinearLayout parent, String title, String subtitle, int type) {
        LinearLayout r = row();
        r.setPadding(0, dp(10), 0, dp(10));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 13, Color.WHITE, true));
        labels.addView(text(subtitle, 10, 0xFF9999A7, false), fullWidthTop(-2, 3));
        r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Button grant = smallButton(type == 3 ? "KIỂM TRA" : type == 2 ? "CẤP" : "MỞ");
        grant.setOnClickListener(v -> {
            if (type == 0) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } else if (type == 1) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } else if (type == 2) {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 21);
                } else {
                    toast("Quyền thông báo đã được cấp hoặc không cần hỏi lại");
                }
            } else {
                int count = getInstalledAppCount();
                toast("Đã đọc được " + count + " ứng dụng trên thiết bị");
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
        labelRow.addView(text(label, 12, 0xFFE8E8F0, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView valueText = text("", 11, accent, true);
        labelRow.addView(valueText);
        group.addView(labelRow);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        int current = Math.max(min, Math.min(max, prefs.getInt(key, defaultValue)));
        seek.setProgress(current - min);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
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
        choice.setPadding(dp(2), dp(4), dp(2), dp(4));
        TextView dot = text("●", 25, color, true);
        dot.setGravity(Gravity.CENTER);
        choice.addView(dot);
        TextView name = text(label, 9, 0xFFE4E4EC, true);
        name.setGravity(Gravity.CENTER);
        choice.addView(name, fullWidthTop(-2, 2));
        choice.setOnClickListener(v -> {
            accent = color;
            prefs.edit().putInt("accent_color", color).putInt("border_color", color).apply();
            refreshIsland();
            buildScreen();
            toast("Đã đổi màu nhấn và màu viền");
        });
        parent.addView(choice, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void addSwitch(LinearLayout parent, String title, String subtitle, String key, boolean defaultValue) {
        LinearLayout r = row();
        r.setPadding(0, dp(9), 0, dp(9));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 13, Color.WHITE, true));
        labels.addView(text(subtitle, 10, 0xFF9999A7, false), fullWidthTop(-2, 3));
        r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        toggle.setTrackTintList(android.content.res.ColorStateList.valueOf((accent & 0x00FFFFFF) | 0x55000000));
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            SharedPreferences.Editor editor = prefs.edit().putBoolean(key, checked);
            if ("auto_start".equals(key)) editor.putBoolean("user_stopped", !checked);
            editor.apply();
            refreshIsland();
        });
        r.addView(toggle);
        parent.addView(r);
    }

    private EditText colorInput(String label, int color) {
        EditText e = input(label + " · #RRGGBB");
        e.setSingleLine(true);
        e.setText(String.format(Locale.US, "#%06X", 0xFFFFFF & color));
        return e;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setHintTextColor(0xFF777D8D);
        e.setTextColor(Color.WHITE);
        e.setTextSize(13);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(roundRect(0xFF20232E, dp(12), 0xFF393D4A, dp(1)));
        return e;
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
        divider.setBackgroundColor(0xFF303440);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void enableIsland() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Hãy cấp quyền hiển thị trên ứng dụng khác trước");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent service = new Intent(this, IslandService.class);
        service.setAction(IslandService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
            else startService(service);
            updateRunningStatus();
            toast("Đảo động đã được bật");
        } catch (RuntimeException e) {
            toast("Android đang chặn chạy nền. Hãy mở ứng dụng rồi thử lại.");
        }
    }

    private void refreshIsland() {
        if (!IslandService.running) return;
        Intent service = new Intent(this, IslandService.class);
        service.setAction(IslandService.ACTION_REFRESH);
        try { startService(service); } catch (RuntimeException ignored) { }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
        }
    }


    private void chooseCustomIcon() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(pick, PICK_ISLAND_ICON);
        } catch (Exception e) {
            toast("Không mở được trình chọn ảnh trên thiết bị");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ISLAND_ICON || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        prefs.edit().putString("custom_icon_uri", uri.toString()).apply();
        refreshIsland();
        toast("Đã lưu icon tùy chỉnh");
    }

    private void openBatterySettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(request);
                    return;
                }
            }
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { }
        }
    }

    private int getInstalledAppCount() {
        try {
            return getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA).size();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private void updatePermissionStatus() {
        if (permissionStatus == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        boolean listener = IslandNotificationListener.isEnabled(this);
        boolean ignoringBattery = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            ignoringBattery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        int appCount = getInstalledAppCount();
        String state = "Hiển thị nổi: " + (overlay ? "ĐÃ CẤP" : "CHƯA CẤP")
                + "  •  Thông báo: " + (listener ? "ĐÃ CẤP" : "CHƯA CẤP")
                + "  •  Ứng dụng: " + appCount
                + "  •  Pin: " + (ignoringBattery ? "Không hạn chế" : "Theo hệ thống");
        permissionStatus.setText(state);
        permissionStatus.setTextColor(overlay && listener ? 0xFF79DFAE : 0xFFFFC66D);
    }

    private void updateRunningStatus() {
        if (runningStatus == null) return;
        boolean isRunning = IslandService.running;
        runningStatus.setText(isRunning ? "● ĐANG HOẠT ĐỘNG" : "● ĐANG TẮT");
        runningStatus.setTextColor(isRunning ? 0xFF79DFAE : 0xFFFFC66D);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setBackground(roundRect(0xFF151923, dp(19), 0xFF2A3040, dp(1)));
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
        b.setBackground(roundRect(0xFF222632, dp(12), 0xFF444B5A, dp(1)));
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
