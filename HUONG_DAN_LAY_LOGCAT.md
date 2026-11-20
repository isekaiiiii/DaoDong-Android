# Lấy log khi Ayane vẫn tự thoát

Nếu bản 1.0.1 vẫn bị đóng ngay khi mở, cần logcat để biết đúng ngoại lệ. Trên Android 11 trở lên có thể dùng ADB không dây.

1. Cài `android-tools` trong Termux: `pkg install android-tools`.
2. Mở Cài đặt Android → Tùy chọn nhà phát triển → Gỡ lỗi không dây → Ghép nối thiết bị bằng mã. Điện thoại và Termux phải cùng mạng Wi-Fi.
3. Trong Termux chạy `adb pair IP:PORT`, nhập mã ghép nối.
4. Quay lại màn hình Gỡ lỗi không dây, lấy địa chỉ/port kết nối và chạy `adb connect IP:PORT`.
5. Chạy `adb logcat -c`.
6. Mở Ayane để tái hiện lỗi, rồi quay lại Termux và chạy:

```bash
adb logcat -d -v time | grep -A80 -B10 -E 'FATAL EXCEPTION|AndroidRuntime|Process: com.ayane.blossom|AyaneOverlay'
```

Gửi toàn bộ kết quả bắt đầu từ `FATAL EXCEPTION` (che dữ liệu cá nhân nếu có). Không cần bật root hoặc cấp Trợ năng để lấy log.
