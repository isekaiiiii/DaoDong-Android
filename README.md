# Ayane Ultimate — v2.2

Ứng dụng Dynamic Island/notification overlay độc lập cho Android, viết bằng Java và Android SDK. Ayane không cần key, không sửa đổi hay mở khóa APK bên thứ ba.

## Điểm mới của v2.2

### Notification Hub — thẻ thông báo nổi độc lập
- Mỗi thông báo đang hoạt động có một thẻ riêng hiển thị trực tiếp trên màn hình, không cần chạm viên thuốc mới xem được.
- Thẻ có icon ứng dụng, avatar người gửi nếu nguồn cung cấp, tên ứng dụng, tên người gửi/tiêu đề và nội dung tin nhắn.
- Có nút Trả lời nhanh khi notification action của ứng dụng nguồn có `RemoteInput`; có nút Bỏ qua để đóng từng thông báo.
- Nút XÓA TẤT CẢ nằm trên thanh quản lý riêng, gọi API hủy các notification Ayane đang theo dõi.
- Kéo phần đầu thẻ để di chuyển ngay trên màn hình. Ayane tìm vị trí trống gần điểm thả; nếu không còn vị trí hợp lệ thì giữ lại vị trí cũ, không cố tình xếp chồng.
- Kéo thanh tiêu đề AYANE để di chuyển nhóm thông báo; có thanh chỉnh vị trí ngang/dọc trong ứng dụng.
- Hiển thị tối đa 5 thẻ cùng lúc (có thể chọn 1–5); nút ‹ / › chuyển trang để xem các thông báo khác. Bộ nhớ tiến trình theo dõi tối đa 30 notification đang hoạt động.
- Bộ lọc ứng dụng cho phép chọn app nào được phép hiện trên Ayane. Bộ lọc chỉ tác động đến thẻ nổi Ayane, không tắt notification gốc của Android/app nguồn.

### Trình phát nhạc riêng
- Trình phát nhạc là cửa sổ nổi độc lập, không ghép vào thẻ thông báo.
- Hiển thị ảnh album, tên bài và nghệ sĩ; điều khiển bài trước, phát/tạm dừng và bài tiếp qua MediaSession.
- Kéo phần tên bài/nghệ sĩ để di chuyển. Tự tìm vị trí không chồng lên các thẻ thông báo/thanh quản lý; nếu không có chỗ phù hợp, thẻ nhạc tạm ẩn thay vì che nội dung.

### Gọn và tùy chỉnh
- Viên thuốc chính không hiển thị phần trăm pin hoặc tốc độ tải xuống.
- Chỉnh chiều rộng/chiều cao khi thu gọn và mở rộng, vị trí ngang/dọc, độ bo góc, độ dày viền, hiệu ứng, tốc độ hiệu ứng và độ mờ kính.
- Chỉnh màu HEX cho nền, viền, chữ, chữ phụ, thông báo, avatar, nút trả lời, điều khiển nhạc và màu nhấn.
- Đổi tên thương hiệu hiển thị, ảnh icon tùy chỉnh cho thông báo Ayane và tạo shortcut màn hình chính có tên/icon riêng.

## Quyền Android cần bật
1. **Hiển thị trên ứng dụng khác** (`SYSTEM_ALERT_WINDOW`): cho phép Ayane vẽ thẻ nổi và nhận thao tác chạm/kéo.
2. **Truy cập thông báo**: đọc notification được Android gửi tới NotificationListenerService để hiển thị tên, nội dung, ảnh và hành động nguồn cung cấp.
3. **Thông báo ứng dụng** trên Android 13+ (`POST_NOTIFICATIONS`): cho thông báo thường trực của Foreground Service.
4. Tùy chọn mở cài đặt pin/chạy nền để giảm khả năng hệ thống hạn chế dịch vụ.

Ayane **không yêu cầu Trợ năng (Accessibility)**. Android không có một quyền chuẩn chung tên “thao tác trên màn hình”; quyền overlay chính thức là quyền phù hợp cho cửa sổ nổi của Ayane.

## Giới hạn thực tế
- Trả lời nhanh chỉ hoạt động nếu ứng dụng nguồn cung cấp `RemoteInput` tương thích; Ayane không thể tự gửi tin nhắn thay cho ứng dụng không hỗ trợ.
- Avatar chỉ hiển thị khi ứng dụng nguồn đính kèm `Person/Icon` hoặc large icon. Nếu không có, Ayane dùng icon app làm ảnh thay thế.
- Điều khiển nhạc phụ thuộc MediaSession và quyền Truy cập thông báo.
- Số thẻ có thể nhìn cùng lúc phụ thuộc kích thước màn hình và vị trí các cửa sổ. Khi không đủ chỗ, Ayane phân trang hoặc tạm ẩn thẻ nhạc, không chủ động xếp chồng.
- Android/One UI có thể dừng tiến trình do force-stop, tiết kiệm pin, khởi động lại hoặc chính sách hệ thống. Không thể bảo đảm chạy tuyệt đối 24/7.

## Quyền riêng tư và bảo mật
- Manifest không khai báo `INTERNET`; `usesCleartextTraffic=false`.
- Không có máy chủ key, quảng cáo, analytics hoặc SDK Gradle bên thứ ba.
- Không dùng Accessibility Service, Shizuku, SMS, danh bạ, vị trí, micro hoặc camera.
- Notification được xử lý cục bộ trong bộ nhớ tiến trình để hiển thị/thực hiện hành động; cài đặt và vị trí được lưu cục bộ trong SharedPreferences.
- Đây là rà soát cấu hình mã nguồn, không phải chứng nhận antivirus độc lập. Chỉ cài APK sau khi workflow build thành công và nên quét bằng Play Protect/công cụ bảo mật tin cậy.

## Build bằng GitHub Actions
1. Giải nén ZIP. Thư mục gốc phải có `app/`, `.github/`, `build.gradle`, `settings.gradle`, `gradle.properties`.
2. Đưa nội dung bên trong thư mục project lên gốc repository GitHub.
3. Push nhánh `main` hoặc vào Actions chạy workflow **Build Ayane Ultimate APK v2.2**.
4. Khi job có dấu tick xanh, tải artifact **Ayane-Ultimate-APK-v2.2**.
5. Giải nén artifact để lấy `app-debug.apk`.

## Build bằng Android Studio
- JDK 17.
- Android SDK Platform 35 và Build Tools 35.0.0.
- Android Gradle Plugin 8.7.3, Gradle 8.9.
- Mở thư mục project gốc, Sync Gradle, chọn Build → Build Bundle(s) / APK(s) → Build APK(s).
- APK debug thường nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Thông số
- Package: `com.ayane.dynamicisland`
- Version: `2.2.0` (versionCode 4)
- Min SDK 26 (Android 8.0), target SDK 35.
- Java; không có dependency Gradle bên thứ ba.
