# Đảo động Android — phiên bản 2.0

Ứng dụng Android tạo lớp phủ nổi phong cách Dynamic Island. Dự án dùng Java, không phải ứng dụng iOS hay Dynamic Island chính thức của Apple.

## Tính năng v2.0

- Giao diện cài đặt tiếng Việt, nền tối và các thẻ điều khiển mới.
- Điều chỉnh chiều rộng 150–380 dp; chiều cao thu gọn 38–160 dp; chiều cao bảng mở rộng 130–480 dp.
- Tinh chỉnh vị trí ngang/dọc, độ bo góc và độ dày viền.
- Tùy chỉnh riêng màu nhấn, nền đảo, chữ, nền bảng mở rộng và màu viền bằng mã HEX; có bộ màu mẫu.
- Đọc tên và biểu tượng ứng dụng gửi thông báo; chọn icon nằm bên trái hoặc bên phải.
- Tên ứng dụng hiển thị có thể dùng tên app tự nhận hoặc tên tùy chỉnh.
- Chọn ảnh từ điện thoại để dùng làm icon tùy chỉnh cho thông báo; có thể khôi phục icon ứng dụng gốc.
- Nếu thông báo của ứng dụng nguồn cung cấp RemoteInput, hiển thị ô nhập và nút **Trả lời**; có nút **Bỏ qua** để đóng thông báo.
- Điều khiển phiên phát nhạc Android: bài trước, phát/tạm dừng, bài tiếp.
- Hiển thị pin, trạng thái sạc, tốc độ tải xuống ước tính và bộ đếm ngược.
- Foreground service có thông báo thường trực; `START_STICKY` để Android có thể khởi động lại service sau khi thu hồi tiến trình.
- Tùy chọn tự khởi động khi máy khởi động hoặc khi app được cập nhật (nếu Android/OEM cho phép).
- Mặc định tắt kéo trực tiếp ở lớp phủ. Người dùng có thể bật kéo trong cài đặt; vị trí vẫn có thể chỉnh bằng thanh trượt.

## Quyền được khai báo

- `SYSTEM_ALERT_WINDOW`: hiển thị lớp phủ nổi.
- Notification access: đọc thông báo, tên gói ứng dụng, icon và gửi thao tác trả lời/đóng thông báo được hỗ trợ.
- `POST_NOTIFICATIONS`: hiển thị thông báo dịch vụ nền trên Android 13+.
- `FOREGROUND_SERVICE` và `FOREGROUND_SERVICE_SPECIAL_USE`: giữ dịch vụ hiển thị cho người dùng.
- `RECEIVE_BOOT_COMPLETED`: thử khôi phục sau khi khởi động lại.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: mở yêu cầu loại ứng dụng khỏi tối ưu pin.
- `QUERY_ALL_PACKAGES`: đọc tên/icon các ứng dụng gửi thông báo trên thiết bị Android 11+.

**Riêng tư:** danh sách ứng dụng và nội dung thông báo được xử lý trên thiết bị; dự án không gửi dữ liệu đến máy chủ. Quyền truy cập thông báo có thể cho ứng dụng thấy nội dung riêng tư. Chỉ cấp quyền nếu bạn đồng ý.

## Cài đặt

1. Cài APK.
2. Mở Đảo động và cấp quyền **Hiển thị trên ứng dụng khác**.
3. Bật **Truy cập thông báo** trong cài đặt Android để nhận thông báo, icon ứng dụng và điều khiển nhạc.
4. Cho phép thông báo của ứng dụng nếu Android 13+ hỏi.
5. Bật đảo động trong ứng dụng.
6. Trong ứng dụng, mở phần **Chế độ nền & thao tác**, bật tự khởi động nếu cần.
7. Trên Samsung/Xiaomi/Oppo, mở cài đặt Pin của Android và chọn chế độ **Không hạn chế** hoặc cho phép hoạt động nền nếu có.

## Trả lời thông báo

Nút Trả lời chỉ xuất hiện nếu ứng dụng nguồn cung cấp hành động trả lời bằng `RemoteInput`. Không phải ứng dụng nào cũng hỗ trợ trả lời nhanh từ thông báo. Nút Bỏ qua gọi API của Android để hủy thông báo đang hiển thị.

## Chạy nền và giới hạn 24/7

Ứng dụng dùng foreground service, thông báo thường trực, `START_STICKY` và receiver khởi động lại. Tuy vậy, không thể đảm bảo chạy tuyệt đối 24/7: người dùng có thể buộc dừng ứng dụng, Android/OEM có thể giới hạn nền, tối ưu pin, giới hạn khởi chạy sau khởi động hoặc thu hồi quyền lớp phủ. Không có cách hợp lệ để vượt qua các kiểm soát hệ thống này. Hãy bật Không hạn chế pin nếu muốn tăng độ ổn định.

## Lưu ý quyền xem danh sách ứng dụng

`QUERY_ALL_PACKAGES` là quyền truy vấn danh sách ứng dụng được khai báo trong manifest, không phải hộp thoại cấp quyền runtime thông thường. Google Play hạn chế quyền này và có thể yêu cầu khai báo/đánh giá; bản build sideload dùng riêng có thể cài trực tiếp. Nếu phát hành qua Play, phải xem xét chính sách hiện hành và chỉ giữ quyền khi chức năng cốt lõi đủ điều kiện.

## Build APK bằng GitHub Actions

1. Đẩy toàn bộ nội dung thư mục dự án này lên repository GitHub, giữ `.github/workflows/build-apk.yml`.
2. Mở tab **Actions** và chọn **Tạo tệp APK Android**.
3. Nhấn **Run workflow** hoặc push commit lên nhánh `main`.
4. Khi workflow hoàn tất, mở lần chạy mới nhất và tải artifact `Dao-dong-Android-APK-v2`.
5. Giải nén file ZIP để lấy `app-debug.apk`.

## Build bằng Android Studio

- Mở thư mục gốc `DynamicIslandAndroid`.
- Cài JDK 17 và Android SDK Platform 35.
- Chạy Gradle sync.
- Chọn **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
- APK debug thường nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Thông số

- Java
- Minimum SDK 26 (Android 8.0)
- Compile/Target SDK 35
- Android Gradle Plugin 8.7.3
- Gradle 8.9 trong GitHub Actions
- Package: `com.example.dynamicisland`
- Version: 2.0 (versionCode 3)
