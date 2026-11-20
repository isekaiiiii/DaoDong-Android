# Lấy log khi Ayane tự thoát trên Android 14+

1. Trong Termux cài công cụ: `pkg install android-tools`.
2. Trên điện thoại mở Tùy chọn nhà phát triển > Gỡ lỗi không dây > Ghép nối thiết bị bằng mã.
3. Trong Termux chạy `adb pair IP:PAIR_PORT`, nhập mã ghép nối hiện trên điện thoại.
4. Chạy `adb connect IP:CONNECT_PORT` (xem địa chỉ và cổng trong trang Gỡ lỗi không dây).
5. Chạy `adb logcat -c`.
6. Mở Ayane và chờ ứng dụng tự thoát.
7. Chạy:

```bash
adb logcat -d -v time | grep -E 'FATAL EXCEPTION|AndroidRuntime|AyaneStartup|AyaneOverlay|AyaneSettings' > ~/storage/downloads/ayane-crash.txt
```

8. Gửi file `Download/ayane-crash.txt` để xác định đúng lớp và dòng gây lỗi.

Không gửi mã ghép nối ADB hoặc thông tin cá nhân trong log. Có thể rà log và xóa nội dung riêng tư trước khi chia sẻ.
