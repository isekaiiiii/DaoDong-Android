# Ayane 1.0.2 – bản vá ổn định khởi chạy

## Những thay đổi
- Bổ sung `ayane_accessibility_description` để AAPT không còn lỗi resource khi XML cũ còn trong repo.
- Thêm kiểm tra tài nguyên trước khi Gradle build để phát hiện sớm tham chiếu `@string` / `@xml` bị thiếu.
- Không tự bật đảo động theo mặc định trong DataStore; không tự bật khởi động cùng máy theo mặc định.
- Màn hình chính không khởi động dịch vụ nổi trong `onCreate()` hoặc `onResume()`.
- Dời truy vấn quyền khỏi luồng dựng khung hình đầu; kiểm tra quyền được bọc lỗi.
- Tránh gọi kiểm tra quyền hai lần từ Activity và Compose lifecycle.
- Thêm `ReplaceFileCorruptionHandler` và fallback mặc định cho DataStore khi dữ liệu cài đặt bị lỗi.
- Bọc lưu cài đặt và khôi phục sau khởi động máy bằng xử lý lỗi.
- Tăng versionCode lên 3, versionName lên 1.0.2.

## Cách áp dụng vào repository hiện tại
Giải nén ZIP này tại thư mục gốc của repository (nơi có `settings.gradle.kts`). Sau đó chạy:

```bash
python3 scripts/check_android_resources.py
git diff --check
git add -A
git commit -m "Fix Ayane startup stability 1.0.2"
git push origin main
```

Sau đó chạy workflow `Tao APK Ayane` trong GitHub Actions.

## Giới hạn xác minh
Đã kiểm tra tĩnh XML, tham chiếu tài nguyên, workflow và các điểm tự khởi động. Môi trường đóng gói không có quyền truy cập `services.gradle.org` và không có Android SDK; vì vậy chưa chạy được Gradle build hoặc cài thử APK. Nếu ứng dụng vẫn tự thoát, cần lấy logcat `FATAL EXCEPTION` từ chính điện thoại để xác định lỗi runtime còn lại.
