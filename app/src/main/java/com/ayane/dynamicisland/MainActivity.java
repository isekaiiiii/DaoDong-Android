package com.ayane.dynamicisland;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String PREFS = "island_settings";
    private static final int PURPLE = Color.rgb(166, 128, 255);
    private static final int BLUE = Color.rgb(91, 170, 255);
    private static final int GREEN = Color.rgb(93, 220, 167);
    private static final int PINK = Color.rgb(255, 126, 183);
    private static final int GOLD = Color.rgb(255, 195, 105);
    private static final int PICK_ISLAND_ICON = 42;
    private static final int PICK_SHORTCUT_ICON = 43;

    private SharedPreferences prefs;
    private TextView permissionStatus;
    private TextView runningStatus;
    private LinearLayout page;
    private int accent;
    private ScrollView mainScroll;
    private View homeAnchor;
    private View displayAnchor;
    private View notificationAnchor;
    private View themeAnchor;

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

        TextView eyebrow = text("AYANE  /  DYNAMIC ISLAND", 10, accent, true);
        eyebrow.setLetterSpacing(0.16f);
        page.addView(eyebrow);

        LinearLayout titleRow = row();
        TextView title = text("Ayane", 32, Color.WHITE, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView version = text("ULTIMATE 2.2", 10, 0xFFD9CEFF, true);
        version.setPadding(dp(11), dp(7), dp(11), dp(7));
        version.setBackground(roundRect(0x332A2540, dp(30), 0x775A4C7E, dp(1)));
        titleRow.addView(version);
        addTop(page, titleRow, 3);
        addTop(page, text("Premium island experience  •  Notification intelligence", 13, 0xFFAAAEBB, false), 4);

        LinearLayout hero = card();
        GradientDrawable heroGradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF30245A, 0xFF191A2B, 0xFF121520});
        heroGradient.setCornerRadius(dp(24));
        heroGradient.setStroke(dp(1), 0x775D4C8F);
        hero.setBackground(heroGradient);
        homeAnchor = hero;
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
        previewPill.setBackground(roundRect(prefs.getInt("island_bg_color", 0xE605070E), dp(70),
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
            toast("Đã tắt Ayane và chế độ tự khởi động.");
        });
        hero.addView(stop, fullWidthTop(44, 8));
        page.addView(hero, fullWidthTop(-2, 20));

        addSectionTitle(page, "01", "Quyền & hoạt động nền", "Cấp quyền để đảo động hiển thị và nhận thông báo khi bạn rời ứng dụng.");
        LinearLayout permissions = card();
        permissions.setOrientation(LinearLayout.VERTICAL);
        permissions.setPadding(dp(14), dp(8), dp(14), dp(12));
        addPermissionRow(permissions, "Cho phép Ayane hiển thị nổi & thao tác trên màn hình", "Mở Quyền truy cập đặc biệt → Hiển thị trên ứng dụng khác → bật Cho phép hiển thị trên ứng dụng khác cho Ayane. Không cần bật Trợ năng.", 0);
        addDivider(permissions);
        addPermissionRow(permissions, "Truy cập thông báo", "Cho phép Ayane nhận từng thông báo, icon ứng dụng, tên người gửi và nút trả lời nếu ứng dụng nguồn hỗ trợ", 1);
        addDivider(permissions);
        addPermissionRow(permissions, "Thông báo dịch vụ nền", "Cho Android biết đảo động đang hoạt động", 2);
        addDivider(permissions);
        addPermissionRow(permissions, "Chọn ứng dụng được phép hiển thị", "Bộ lọc chỉ áp dụng cho thẻ nổi Ayane; không tắt thông báo gốc của ứng dụng", 3);
        permissionStatus = text("Đang kiểm tra quyền…", 11, GOLD, false);
        permissions.addView(permissionStatus, fullWidthTop(-2, 10));
        Button battery = smallButton("MỞ CÀI ĐẶT PIN / CHẠY NỀN");
        battery.setOnClickListener(v -> openBatterySettings());
        permissions.addView(battery, fullWidthTop(44, 10));
        page.addView(permissions, fullWidthTop(-2, 8));

        addSectionTitle(page, "02", "Kích thước, vị trí & hiệu ứng", "Chỉnh riêng kích thước thu gọn / mở rộng, căn dọc âm-dương và hiệu ứng kính.");
        LinearLayout dimensions = card();
        dimensions.setOrientation(LinearLayout.VERTICAL);
        dimensions.setPadding(dp(15), dp(12), dp(15), dp(14));
        displayAnchor = dimensions;
        addSlider(dimensions, "Chiều rộng đảo thu gọn", "width", 140, 420, 250, " dp");
        addSlider(dimensions, "Chiều cao đảo thu gọn", "height", 38, 180, 64, " dp");
        addSlider(dimensions, "Chiều rộng khi mở rộng", "expanded_width", 220, 620, 420, " dp");
        addSlider(dimensions, "Chiều cao khi mở rộng", "expanded_height", 150, 700, 330, " dp");
        addSlider(dimensions, "Vị trí ngang · trái (−) / phải (+)", "x", -420, 420, 0, " dp");
        addSlider(dimensions, "Vị trí dọc · lên (−) / xuống (+)", "y", -900, 900, -4, " dp");
        addSlider(dimensions, "Độ bo góc", "corner_radius", 12, 88, 38, " dp");
        addSlider(dimensions, "Độ dày viền", "border_width", 0, 5, 1, " dp");
        Button center = outlineButton("CĂN GIỮA ĐẢO ĐỘNG");
        center.setOnClickListener(v -> {
            prefs.edit().putInt("x", 0).putInt("y", -4).apply();
            refreshIsland();
            buildScreen();
            toast("Đã căn lại vị trí");
        });
        dimensions.addView(center, fullWidthTop(44, 14));
        addDivider(dimensions);
        addSwitch(dimensions, "Hiệu ứng thông báo", "Chạy chuyển cảnh khi có nội dung mới", "animate_notifications", true);
        TextView animationLabel = text("Kiểu chuyển động", 12, 0xFFE8E8F0, true);
        dimensions.addView(animationLabel, fullWidthTop(-2, 10));
        RadioGroup animationGroup = new RadioGroup(this);
        animationGroup.setOrientation(RadioGroup.HORIZONTAL);
        String[] animationNames = {"Mờ", "Trượt", "Nảy", "Tắt"};
        int savedAnimation = prefs.getInt("animation_mode", 1);
        for (int i = 0; i < animationNames.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setText(animationNames[i]);
            option.setTextSize(11);
            option.setTextColor(Color.WHITE);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
            option.setId(100 + i);
            animationGroup.addView(option);
        }
        animationGroup.check(100 + Math.max(0, Math.min(3, savedAnimation)));
        animationGroup.setOnCheckedChangeListener((group, checkedId) -> {
            prefs.edit().putInt("animation_mode", Math.max(0, Math.min(3, checkedId - 100))).apply();
            refreshIsland();
        });
        dimensions.addView(animationGroup, fullWidthTop(-2, 2));
        addSlider(dimensions, "Tốc độ hiệu ứng", "animation_duration", 120, 900, 320, " ms");
        addSwitch(dimensions, "Hiệu ứng kính trong suốt", "Dùng blur nền trên thiết bị hỗ trợ Android 12+", "glass_effect", true);
        addSlider(dimensions, "Độ mờ kính", "glass_blur", 0, 80, 26, " px");
        page.addView(dimensions, fullWidthTop(-2, 8));

        addSectionTitle(page, "03", "Bộ màu giao diện", "Chọn màu mẫu hoặc tự nhập mã HEX cho từng thành phần.");
        LinearLayout presets = card();
        themeAnchor = presets;
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
        EditText backgroundHex = colorInput("Màu nền đảo", prefs.getInt("island_bg_color", 0xE605070E));
        EditText textHex = colorInput("Màu chữ", prefs.getInt("text_color", Color.WHITE));
        EditText panelHex = colorInput("Màu nền bảng mở rộng", prefs.getInt("panel_bg_color", 0xE8171924));
        EditText borderHex = colorInput("Màu viền", prefs.getInt("border_color", PURPLE));
        EditText notifHex = colorInput("Màu thông báo", prefs.getInt("notification_color", 0xFFBFD0FF));
        EditText secondaryHex = colorInput("Màu chữ phụ", prefs.getInt("secondary_text_color", 0xFFAAAEBB));
        EditText replyHex = colorInput("Màu nút trả lời", prefs.getInt("reply_color", PURPLE));
        EditText mediaHex = colorInput("Màu nút điều khiển nhạc", prefs.getInt("media_color", PURPLE));
        EditText avatarBgHex = colorInput("Màu nền avatar / icon", prefs.getInt("avatar_bg_color", 0xFF252A36));
        colorEditor.addView(accentHex, fullWidthTop(48, 6));
        colorEditor.addView(backgroundHex, fullWidthTop(48, 8));
        colorEditor.addView(textHex, fullWidthTop(48, 8));
        colorEditor.addView(panelHex, fullWidthTop(48, 8));
        colorEditor.addView(borderHex, fullWidthTop(48, 8));
        colorEditor.addView(notifHex, fullWidthTop(48, 8));
        colorEditor.addView(secondaryHex, fullWidthTop(48, 8));
        colorEditor.addView(replyHex, fullWidthTop(48, 8));
        colorEditor.addView(mediaHex, fullWidthTop(48, 8));
        colorEditor.addView(avatarBgHex, fullWidthTop(48, 8));
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
                        .putInt("border_color", border)
                        .putInt("notification_color", Color.parseColor(notifHex.getText().toString().trim()))
                        .putInt("secondary_text_color", Color.parseColor(secondaryHex.getText().toString().trim()))
                        .putInt("reply_color", Color.parseColor(replyHex.getText().toString().trim()))
                        .putInt("media_color", Color.parseColor(mediaHex.getText().toString().trim()))
                        .putInt("avatar_bg_color", Color.parseColor(avatarBgHex.getText().toString().trim()))
                        .apply();
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
        TextView colorHint = text("Dùng #RRGGBB hoặc #AARRGGBB nếu cần chỉnh độ trong suốt.", 10, 0xFF9295A4, false);
        colorEditor.addView(colorHint, fullWidthTop(-2, 8));
        page.addView(colorEditor, fullWidthTop(-2, 8));

        addSectionTitle(page, "04", "Tên & biểu tượng ứng dụng", "Tự lấy tên và icon của ứng dụng gửi thông báo, hoặc đặt tên hiển thị riêng.");
        LinearLayout appCard = card();
        appCard.setOrientation(LinearLayout.VERTICAL);
        appCard.setPadding(dp(14), dp(12), dp(14), dp(14));
        EditText islandBrandName = input("Tên thương hiệu trên đảo động, ví dụ: Ayane");
        islandBrandName.setText(prefs.getString("island_brand_name", "Ayane"));
        appCard.addView(islandBrandName, fullWidthTop(50, 2));
        Button saveBrandName = button("LƯU TÊN ĐẢO ĐỘNG", accent);
        saveBrandName.setOnClickListener(v -> {
            String value = islandBrandName.getText().toString().trim();
            if (value.isEmpty()) value = "Ayane";
            prefs.edit().putString("island_brand_name", value).apply();
            hideKeyboard();
            refreshIsland();
            toast("Đã lưu tên đảo động");
        });
        appCard.addView(saveBrandName, fullWidthTop(44, 8));

        EditText customName = input("Tên thay thế cho app gửi thông báo (tùy chọn)");
        customName.setText(prefs.getString("custom_app_name", ""));
        appCard.addView(customName, fullWidthTop(50, 8));
        Button saveName = outlineButton("LƯU TÊN ỨNG DỤNG NGUỒN");
        saveName.setOnClickListener(v -> {
            prefs.edit().putString("custom_app_name", customName.getText().toString().trim()).apply();
            hideKeyboard();
            refreshIsland();
            toast("Đã lưu tên ứng dụng nguồn");
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

        addDivider(appCard);
        TextView shortcutTitle = text("TÙY CHỈNH BIỂU TƯỢNG AYANE TRÊN MÀN HÌNH CHÍNH", 11, accent, true);
        shortcutTitle.setPadding(0, dp(12), 0, dp(5));
        appCard.addView(shortcutTitle);
        EditText shortcutName = input("Tên icon trên màn hình chính, ví dụ: Ayane");
        shortcutName.setText(prefs.getString("home_shortcut_name", "Ayane"));
        appCard.addView(shortcutName, fullWidthTop(48, 5));
        Button saveShortcutName = outlineButton("LƯU TÊN BIỂU TƯỢNG");
        saveShortcutName.setOnClickListener(v -> {
            String value = shortcutName.getText().toString().trim();
            if (value.isEmpty()) value = "Ayane";
            prefs.edit().putString("home_shortcut_name", value).apply();
            hideKeyboard();
            toast("Đã lưu tên biểu tượng Ayane");
        });
        appCard.addView(saveShortcutName, fullWidthTop(42, 7));
        Button chooseShortcutIcon = outlineButton("CHỌN ẢNH LÀM ICON MÀN HÌNH CHÍNH");
        chooseShortcutIcon.setOnClickListener(v -> chooseShortcutIcon());
        appCard.addView(chooseShortcutIcon, fullWidthTop(42, 7));
        Button resetShortcutIcon = outlineButton("DÙNG ICON AYANE MẶC ĐỊNH");
        resetShortcutIcon.setOnClickListener(v -> {
            prefs.edit().remove("home_shortcut_icon_uri").apply();
            toast("Đã chọn lại icon Ayane mặc định");
        });
        appCard.addView(resetShortcutIcon, fullWidthTop(40, 7));
        Button createShortcut = button("TẠO BIỂU TƯỢNG TRÊN MÀN HÌNH CHÍNH", accent);
        createShortcut.setOnClickListener(v -> {
            prefs.edit().putString("home_shortcut_name",
                    shortcutName.getText().toString().trim().isEmpty()
                            ? "Ayane" : shortcutName.getText().toString().trim()).apply();
            hideKeyboard();
            createHomeShortcut();
        });
        appCard.addView(createShortcut, fullWidthTop(48, 10));
        TextView shortcutNote = text("Android không cho ứng dụng tự thay tên/icon gốc trong ngăn ứng dụng. Ayane tạo một lối tắt riêng với tên và ảnh bạn chọn; launcher có thể yêu cầu xác nhận ghim.", 10, 0xFFAAAEBB, false);
        shortcutNote.setLineSpacing(dp(3), 1f);
        appCard.addView(shortcutNote, fullWidthTop(-2, 6));

        page.addView(appCard, fullWidthTop(-2, 8));

        addSectionTitle(page, "05", "Thông báo & thao tác nhanh", "Thông báo nổi riêng, nhạc riêng và thao tác trực tiếp trên màn hình.");
        LinearLayout features = card();
        notificationAnchor = features;
        features.setOrientation(LinearLayout.VERTICAL);
        features.setPadding(dp(14), dp(6), dp(14), dp(6));
        addSwitch(features, "Thông báo mới", "Nhận thông báo được phép trong bộ lọc ứng dụng", "show_notifications", true);
        addDivider(features);
        addSwitch(features, "Thẻ thông báo nổi riêng", "Mỗi thông báo có một thẻ độc lập, không xếp đè; kéo phần đầu thẻ để di chuyển", "floating_notifications", true);
        addDivider(features);
        addSwitch(features, "Nút Xóa tất cả", "Hiện nút gọn trong thanh quản lý phía trên các thẻ", "floating_clear_button", true);
        addDivider(features);
        addSlider(features, "Số thẻ nổi hiển thị cùng lúc", "floating_max_cards", 1, 5, 4, " thẻ");
        addSlider(features, "Vị trí ngang nhóm thông báo", "floating_x", 0, 420, 12, " dp");
        addSlider(features, "Vị trí dọc nhóm thông báo", "floating_y", 24, 900, 96, " dp");
        addDivider(features);
        addSwitch(features, "Nút trả lời nhanh", "Chỉ xuất hiện khi thông báo nguồn hỗ trợ trả lời", "show_reply_actions", true);
        addDivider(features);
        addSwitch(features, "Nút bỏ qua thông báo", "Đóng thông báo đang được xem", "show_dismiss_action", true);
        addDivider(features);
        addSwitch(features, "Hiện tên người gửi", "Đọc tên người gửi nếu ứng dụng nguồn cung cấp", "show_sender_name", true);
        addDivider(features);
        addSwitch(features, "Hiện avatar người gửi", "Lấy ảnh người gửi từ thông báo hội thoại nếu có; nếu thiếu sẽ dùng icon app", "show_sender_avatar", true);
        addDivider(features);
        addSwitch(features, "Hiện thông báo đang chạy", "Bao gồm một số thông báo cuộc gọi / hoạt động liên tục", "show_ongoing_notifications", true);
        addDivider(features);
        addSwitch(features, "Trình phát nhạc riêng", "Hiện một thẻ nhạc độc lập, có thể kéo tới vị trí bất kỳ", "show_media", true);
        addDivider(features);
        addSwitch(features, "Thẻ nhạc nổi", "Bật/tắt trình phát nhạc nổi riêng, tách khỏi thông báo", "floating_media_card", true);
        addSlider(features, "Vị trí ngang thẻ nhạc", "media_floating_x", 0, 420, 12, " dp");
        addSlider(features, "Vị trí dọc thẻ nhạc", "media_floating_y", 24, 900, 650, " dp");
        addDivider(features);
        addSwitch(features, "Tự tránh chồng lấp", "Tự tìm vị trí trống cho thẻ nhạc khi gần thẻ thông báo", "auto_avoid_overlap", true);
        page.addView(features, fullWidthTop(-2, 8));
        Button appFilter = outlineButton("CHỌN ỨNG DỤNG ĐƯỢC PHÉP GỬI THÔNG BÁO");
        appFilter.setOnClickListener(v -> showAppFilterDialog());
        page.addView(appFilter, fullWidthTop(48, 8));
        TextView filterNote = text("Các thẻ thông báo nổi được xếp riêng theo chiều dọc để không đè nhau. Kéo thanh đầu của từng thẻ để di chuyển; kéo thanh đầu AYANE để thay đổi vị trí nhóm. Dùng nút ‹ / › để chuyển trang xem thêm thông báo; Ayane hiển thị tối đa số thẻ bạn chọn.", 10, 0xFF999EAE, false);
        page.addView(filterNote, fullWidthTop(-2, 4));

        addSectionTitle(page, "06", "Chế độ nền & thao tác", "Giữ dịch vụ hoạt động, tự khởi động lại sau khi bật máy nếu Android cho phép.");
        LinearLayout background = card();
        background.setOrientation(LinearLayout.VERTICAL);
        background.setPadding(dp(14), dp(6), dp(14), dp(10));
        addSwitch(background, "Tự bật sau khi khởi động máy", "Yêu cầu quyền hiển thị nổi đã được cấp", "auto_start", true);
        addDivider(background);
        addSwitch(background, "Cho phép kéo đảo động", "Mặc định tắt: khi ra ngoài app chỉ chạm để mở / thu gọn", "allow_drag", false);
        addDivider(background);
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

        TextView footer = text("QUYỀN RIÊNG TƯ\nAyane xử lý nội dung thông báo ngay trên thiết bị để hiển thị và thực hiện thao tác mà ứng dụng gửi hỗ trợ. Bản này không khai báo quyền Internet và không gửi thông báo lên máy chủ. Quyền Truy cập thông báo cho phép đọc nội dung thông báo; chỉ bật khi bạn đồng ý. Ayane không dùng Trợ năng (Accessibility).", 10, 0xFF858A99, false);
        footer.setLineSpacing(dp(3), 1f);
        page.addView(footer, fullWidthTop(-2, 22));

        mainScroll = scroll;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF080A11);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(8), dp(7), dp(8), dp(8));
        bottomNav.setBackground(roundRect(0xFF10131D, dp(18), 0xFF2A3040, dp(1)));
        bottomNav.addView(navButton("⌂", "Trang chủ", () -> scroll.smoothScrollTo(0, 0)), new LinearLayout.LayoutParams(0, dp(54), 1));
        bottomNav.addView(navButton("◉", "Đảo động", () -> scrollToAnchor(displayAnchor)), new LinearLayout.LayoutParams(0, dp(54), 1));
        bottomNav.addView(navButton("▤", "Thông báo", () -> scrollToAnchor(notificationAnchor)), new LinearLayout.LayoutParams(0, dp(54), 1));
        bottomNav.addView(navButton("✧", "Giao diện", () -> scrollToAnchor(themeAnchor)), new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(bottomNav, fullWidthTop(66, 0));
        setContentView(root);
        updatePermissionStatus();
        updateRunningStatus();
    }


    private View navButton(String symbol, String label, Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView icon = text(symbol, 19, accent, true);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);
        TextView caption = text(label, 9, 0xFFB8BDCB, true);
        caption.setGravity(Gravity.CENTER);
        box.addView(caption, fullWidthTop(-2, 1));
        box.setOnClickListener(v -> action.run());
        return box;
    }

    private void scrollToAnchor(View anchor) {
        if (mainScroll == null || anchor == null) return;
        mainScroll.post(() -> mainScroll.smoothScrollTo(0, Math.max(0, anchor.getTop() - dp(18))));
    }

    private void showAppFilterDialog() {
        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> results;
        try { results = pm.queryIntentActivities(launcher, 0); }
        catch (RuntimeException e) { toast("Không thể tải danh sách ứng dụng"); return; }
        ArrayList<ResolveInfo> apps = new ArrayList<>(results);
        Collections.sort(apps, (a, b) -> String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> packages = new ArrayList<>();
        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo == null ? "" : info.activityInfo.packageName;
            if (pkg.isEmpty() || pkg.equals(getPackageName()) || packages.contains(pkg)) continue;
            packages.add(pkg);
            labels.add(String.valueOf(info.loadLabel(pm)));
        }
        if (labels.isEmpty()) { toast("Không tìm thấy ứng dụng có biểu tượng khởi chạy"); return; }
        Set<String> blockedNow = new HashSet<>(prefs.getStringSet("blocked_apps", Collections.<String>emptySet()));
        boolean[] checked = new boolean[labels.size()];
        String[] labelArray = labels.toArray(new String[0]);
        for (int i = 0; i < packages.size(); i++) checked[i] = !blockedNow.contains(packages.get(i));
        new AlertDialog.Builder(this)
                .setTitle("Ứng dụng hiển thị trong Ayane")
                .setMultiChoiceItems(labelArray, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    HashSet<String> blocked = new HashSet<>();
                    for (int i = 0; i < packages.size(); i++) if (!checked[i]) blocked.add(packages.get(i));
                    prefs.edit().putStringSet("blocked_apps", blocked).apply();
                    IslandNotificationListener.refreshAppFilters(this);
                    refreshIsland();
                    toast("Đã lưu bộ lọc ứng dụng");
                })
                .setNegativeButton("Hủy", null)
                .setNeutralButton("Cho phép tất cả", (dialog, which) -> {
                    prefs.edit().putStringSet("blocked_apps", new HashSet<String>()).apply();
                    IslandNotificationListener.refreshAppFilters(this);
                    refreshIsland();
                    toast("Đã cho phép tất cả ứng dụng");
                }).show();
    }

    private void addPermissionRow(LinearLayout parent, String title, String subtitle, int type) {
        LinearLayout r = row();
        r.setPadding(0, dp(10), 0, dp(10));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 13, Color.WHITE, true));
        labels.addView(text(subtitle, 10, 0xFF9999A7, false), fullWidthTop(-2, 3));
        r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Button grant = smallButton(type == 3 ? "CHỌN" : type == 2 ? "CẤP" : "MỞ");
        grant.setOnClickListener(v -> {
            if (type == 0) {
                new AlertDialog.Builder(this)
                        .setTitle("Cho phép thao tác nổi trên màn hình")
                        .setMessage("Android sẽ mở phần Quyền truy cập đặc biệt. Chọn Ayane và bật Cho phép hiển thị trên ứng dụng khác. Quyền này giúp Ayane vẽ thẻ nổi và nhận thao tác chạm/kéo trực tiếp. Ayane không yêu cầu quyền Trợ năng.")
                        .setPositiveButton("MỞ CÀI ĐẶT", (dialog, which) -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))))
                        .setNegativeButton("HỦY", null)
                        .show();
            } else if (type == 1) {
                new AlertDialog.Builder(this)
                        .setTitle("Cho phép Ayane đọc thông báo")
                        .setMessage("Ayane cần quyền này để hiển thị thông báo nổi và gửi thao tác trả lời nhanh mà ứng dụng gốc cho phép. Nội dung được xử lý trên thiết bị, không tải lên máy chủ. Hãy chọn Ayane trong danh sách rồi bật quyền.")
                        .setPositiveButton("MỞ CÀI ĐẶT", (dialog, which) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                        .setNegativeButton("ĐỂ SAU", null)
                        .show();
            } else if (type == 2) {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 21);
                } else {
                    toast("Quyền thông báo đã được cấp hoặc không cần hỏi lại");
                }
            } else {
                showAppFilterDialog();
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
            prefs.edit().putInt("accent_color", color).putInt("border_color", color)
                    .putInt("reply_color", color).putInt("media_color", color).apply();
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
        EditText e = input(label + " · #RRGGBB / #AARRGGBB");
        e.setSingleLine(true);
        if (Color.alpha(color) < 255) e.setText(String.format(Locale.US, "#%08X", color));
        else e.setText(String.format(Locale.US, "#%06X", 0xFFFFFF & color));
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
            new AlertDialog.Builder(this)
                    .setTitle("Ayane cần quyền hiển thị nổi")
                    .setMessage("Chọn Ayane trong danh sách ứng dụng rồi bật Cho phép hiển thị trên ứng dụng khác. Đây là quyền hiển thị cửa sổ nổi của Android, không phải quyền Trợ năng.")
                    .setPositiveButton("MỞ CÀI ĐẶT", (dialog, which) -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("ĐỂ SAU", null)
                    .show();
            return;
        }
        if (!IslandNotificationListener.isEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Bật quyền thông báo")
                    .setMessage("Để Ayane nhận từng thông báo, hiện icon/tên người gửi và cung cấp nút trả lời nhanh, hãy bật quyền Truy cập thông báo cho Ayane.")
                    .setPositiveButton("MỞ CÀI ĐẶT", (dialog, which) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                    .setNegativeButton("CHẠY KHÔNG CÓ THÔNG BÁO", (dialog, which) -> startIslandService())
                    .show();
            return;
        }
        startIslandService();
    }

    private void startIslandService() {
        Intent service = new Intent(this, IslandService.class);
        service.setAction(IslandService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
            else startService(service);
            updateRunningStatus();
            toast("Ayane đã được bật");
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

    private void chooseShortcutIcon() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(pick, PICK_SHORTCUT_ICON);
        } catch (Exception e) {
            toast("Không mở được trình chọn ảnh trên thiết bị");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }

        if (requestCode == PICK_ISLAND_ICON) {
            prefs.edit().putString("custom_icon_uri", uri.toString()).apply();
            refreshIsland();
            toast("Đã lưu icon thông báo tùy chỉnh");
        } else if (requestCode == PICK_SHORTCUT_ICON) {
            prefs.edit().putString("home_shortcut_icon_uri", uri.toString()).apply();
            toast("Đã lưu ảnh icon Ayane. Nhấn Tạo biểu tượng để ghim lên màn hình chính.");
        }
    }

    private void createHomeShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast("Tính năng lối tắt tùy chỉnh cần Android 8.0 trở lên.");
            return;
        }
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported()) {
            toast("Trình khởi chạy trên điện thoại không hỗ trợ ghim lối tắt.");
            return;
        }

        String label = prefs.getString("home_shortcut_name", "Ayane").trim();
        if (label.isEmpty()) label = "Ayane";
        if (label.length() > 25) label = label.substring(0, 25);

        Icon icon = Icon.createWithResource(this, R.drawable.ic_ayane);
        String iconUri = prefs.getString("home_shortcut_icon_uri", "");
        if (!iconUri.isEmpty()) {
            Bitmap bitmap = decodeShortcutBitmap(Uri.parse(iconUri));
            if (bitmap != null) icon = Icon.createWithBitmap(bitmap);
        }

        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setAction("com.ayane.dynamicisland.OPEN_FROM_SHORTCUT");
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "ayane-home-shortcut")
                .setShortLabel(label)
                .setLongLabel(label + " · Dynamic Island")
                .setIcon(icon)
                .setIntent(launchIntent)
                .build();

        try {
            boolean accepted = shortcutManager.requestPinShortcut(shortcut, null);
            toast(accepted
                    ? "Đã gửi yêu cầu ghim lối tắt Ayane. Hãy xác nhận trên màn hình chính nếu được hỏi."
                    : "Launcher chưa nhận yêu cầu ghim lối tắt.");
        } catch (RuntimeException e) {
            toast("Không thể tạo lối tắt trên launcher này.");
        }
    }

    private Bitmap decodeShortcutBitmap(Uri uri) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Exception e) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > 512) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;

        Bitmap source;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(in, null, options);
        } catch (Exception e) {
            return null;
        }
        if (source == null) return null;
        int side = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        Bitmap square = Bitmap.createBitmap(source, left, top, side, side);
        Bitmap result = Bitmap.createScaledBitmap(square, 256, 256, true);
        if (square != source && square != result && !square.isRecycled()) square.recycle();
        if (source != result && source != square && !source.isRecycled()) source.recycle();
        return result;
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

    private int getLauncherAppCount() {
        try {
            Intent launcher = new Intent(Intent.ACTION_MAIN, null);
            launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> activities = getPackageManager().queryIntentActivities(launcher, 0);
            HashSet<String> packages = new HashSet<>();
            for (ResolveInfo info : activities) {
                if (info.activityInfo != null) packages.add(info.activityInfo.packageName);
            }
            return packages.size();
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
        int appCount = getLauncherAppCount();
        String state = "Hiển thị nổi: " + (overlay ? "ĐÃ CẤP" : "CHƯA CẤP")
                + "  •  Thông báo: " + (listener ? "ĐÃ CẤP" : "CHƯA CẤP")
                + "  •  App có biểu tượng: " + appCount
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
