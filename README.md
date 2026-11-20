# AYANE DYNAMIC ISLAND — ULTIMATE 2.6.0

Ayane là ứng dụng Dynamic Island độc lập cho Android, viết bằng Java và Android SDK. Bản 2.6 đối chiếu các hành vi công khai của Droidtools/Droidbubble và cập nhật cầu nối Trợ năng, chuyển loại cửa sổ nổi, đồng bộ MediaSession khi đổi ứng dụng và tăng phạm vi kéo đảo lên vùng camera/thanh trạng thái.

## Điểm mới trong 2.6

- Dịch vụ Trợ năng tùy chọn nhận `TYPE_WINDOW_STATE_CHANGED` để biết khi người dùng chuyển ứng dụng; Ayane làm mới MediaSession sau sự kiện.
- Khi Trợ năng kết nối, đảo chính, thẻ nổi nhạc/thông báo và thanh công cụ dùng `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_LAYOUT_NO_LIMITS` và chế độ hiển thị camera cutout. Khi tắt, Ayane quay lại `TYPE_APPLICATION_OVERLAY`.
- Tăng giới hạn kéo dọc lên tới khoảng `-1400 dp` ở chế độ Trợ năng; thanh chỉnh vị trí dọc trong ứng dụng cũng được mở rộng tới `-1400 dp`.
- Sửa vòng đời: khi Trợ năng bật/tắt, Ayane tháo và tạo lại cả đảo, thẻ nổi và thanh công cụ với đúng `WindowManager`/context; tránh giữ cửa sổ cũ.
- Bổ sung thao tác hệ thống: mở bảng thông báo, Cài đặt nhanh, Ứng dụng gần đây và chia đôi màn hình (phụ thuộc phiên bản Android/thiết bị).
- Giữ chụp màn hình trong Droidbar chỉ khi người dùng chủ động nhấn nút; ảnh được lưu cục bộ vào `Pictures/Ayane`.
- Sau khi bật Trợ năng trong Cài đặt Android và quay lại Ayane, ứng dụng tự tiếp tục khởi chạy chế độ mở rộng nếu người dùng đã chọn bật.

## Các chức năng Dynamic Island

### Nhạc và video
- Hiện ảnh bìa/thumbnail, tiêu đề, nghệ sĩ và ứng dụng phát.
- Phát/tạm dừng, bài trước, bài tiếp và tua khi MediaSession của ứng dụng nguồn hỗ trợ.
- Thẻ media riêng với nền ảnh bìa làm mờ, lớp phủ và vị trí/kích thước có thể chỉnh.
- Khi đổi ứng dụng, Trợ năng phát hiện sự kiện đổi cửa sổ rồi yêu cầu làm mới MediaSession.

### Thông báo
- Thẻ nổi riêng theo từng ứng dụng, biểu tượng ứng dụng, tên ứng dụng, người gửi/avatar khi nguồn cung cấp và nội dung.
- Mở thông báo, bỏ qua, trả lời nhanh khi ứng dụng nguồn có RemoteInput.
- Chuyển thẻ và xóa các thông báo Ayane đang theo dõi; không xóa tin nhắn hoặc dữ liệu trong ứng dụng gốc.

### Hoạt động và trạng thái hệ thống
- Cuộc gọi đến/đang gọi với nút nghe/từ chối/kết thúc nếu thông báo nguồn cung cấp PendingIntent phù hợp.
- Hẹn giờ/báo thức khi ứng dụng Đồng hồ cung cấp thông báo có chronometer hoặc thao tác tương ứng.
- Trạng thái sạc và thông tin pin mở rộng; chế độ chuông/rung/im lặng.
- Trạng thái rảnh: viên thuốc thu gọn khi không có hoạt động.

### Thanh công cụ cạnh Droidbar
- Lối tắt đèn pin, Internet, âm thanh/rung, Cài đặt nhanh, chụp màn hình.
- Ghim ứng dụng và mở nhanh ứng dụng do người dùng chọn.
- Có thể kéo thanh công cụ trên màn hình; các thao tác hệ thống dùng Trợ năng nếu cần.

### Cá nhân hóa
- Chỉnh rộng/cao khi thu gọn và mở rộng, vị trí ngang/dọc, bo góc, độ dày viền.
- Màu HEX riêng cho nền, viền, chữ, thông báo, avatar, nút trả lời và media.
- Chỉnh phông chữ, hiệu ứng chuyển động, rung nhẹ, nền album mờ và thứ tự lớp media.
- Đổi tên thương hiệu và icon thông báo/shortcut.

## Hai quyền nổi khác nhau

1. **Hiển thị trên ứng dụng khác** (`SYSTEM_ALERT_WINDOW`): quyền cơ bản để Ayane tạo cửa sổ nổi, nhận chạm và kéo thả. Việc kéo được thực hiện bằng bộ xử lý chạm của Ayane (`MotionEvent`), không phải bằng quyền Trợ năng.
2. **Trợ năng Ayane** (tùy chọn nâng cao): theo mô hình Droidtools, cho phép Ayane dùng `TYPE_ACCESSIBILITY_OVERLAY` thay vì overlay thông thường, đặt cửa sổ sát camera cutout/thanh trạng thái và nhận sự kiện đổi ứng dụng để làm mới media. Chỉ nhận `TYPE_WINDOW_STATE_CHANGED`, đặt `canRetrieveWindowContent=false` và `canPerformGestures=false`; dịch vụ không đọc cây giao diện, văn bản, mật khẩu hay trường nhập. Trợ năng không tự thay thế quyền overlay.
3. **Truy cập thông báo**: cần để nhận thông báo, metadata và các hành động mà ứng dụng nguồn công bố.
4. **Thông báo Ayane** trên Android 13 trở lên: cho phép thông báo thường trực của dịch vụ nền.
5. **Camera**: chỉ dùng điều khiển đèn pin trong Droidbar.
6. **Chụp màn hình**: cấu hình Trợ năng khai báo `canTakeScreenshot=true` để nút Chụp màn hình hoạt động trên Android 11 trở lên. Ayane chỉ gọi API chụp sau thao tác nhấn nút rõ ràng trong Droidbar; ảnh được lưu trong `Pictures/Ayane`, không tự chụp nền. Nếu không cần chức năng này, người dùng có thể không sử dụng nút đó.

> Trợ năng là quyền mạnh. Chỉ bật nếu bạn muốn chế độ cửa sổ mở rộng/thao tác nhanh. Để kéo đảo trong vùng ứng dụng thông thường, quyền Hiển thị trên ứng dụng khác là quyền trực tiếp. Với Samsung, mở **Cài đặt → Hỗ trợ → Ứng dụng đã cài đặt → Trợ năng Ayane** để bật/tắt.

## Quyền riêng tư và an toàn

- Không khai báo quyền `INTERNET`; `usesCleartextTraffic=false`.
- Không có máy chủ key, quảng cáo, analytics hoặc thư viện runtime bên thứ ba.
- Không yêu cầu `canRetrieveWindowContent`, không đọc văn bản UI, không dùng Shizuku, microphone, SMS, danh bạ hoặc vị trí.
- Quyền `CAMERA` chỉ phục vụ đèn pin; chụp màn hình dùng API Trợ năng và chỉ chạy khi người dùng bấm nút.
- Thông báo, dữ liệu media và tùy chỉnh được xử lý cục bộ trên thiết bị.
- Đây là rà soát cấu hình/mã nguồn tĩnh, không phải chứng nhận sạch virus hoặc quét antivirus độc lập. Hãy quét APK bằng Play Protect hoặc công cụ bảo mật tin cậy trước khi cài.

## Tham khảo Droidtools

Ayane tham khảo các hành vi công khai của Droidtools/Droidbubble: media art/title/seekbar/controls, thông báo dạng heads-up và trả lời nhanh, sự kiện hệ thống, trạng thái rảnh và tùy chỉnh vị trí/kích thước/chuyển động. Cấu hình Trợ năng tham khảo cách dùng `TYPE_WINDOW_STATE_CHANGED`, `canRetrieveWindowContent=false`, cập nhật media khi đổi ứng dụng và đổi loại cửa sổ thành `TYPE_ACCESSIBILITY_OVERLAY` khi dịch vụ kết nối. Ayane là bản triển khai Java độc lập, không chép nguyên mã Kotlin/Compose hoặc thư viện Shizuku.

Nguồn tham khảo: https://github.com/Velocity081/droidtools

## Build bằng GitHub Actions

1. Giải nén ZIP; thư mục gốc phải có `app/`, `.github/`, `build.gradle`, `settings.gradle`, `gradle.properties`.
2. Đưa toàn bộ nội dung bên trong thư mục gốc lên repository GitHub.
3. Push nhánh `main` hoặc mở Actions và chạy workflow **Tạo APK Ayane Dynamic Island 2.6**.
4. Khi workflow có dấu tích xanh, tải artifact **Ayane-Dynamic-Island-APK-v2.6**.
5. Giải nén artifact để lấy `app-debug.apk`.

## Build bằng Android Studio

- JDK 17.
- Android SDK Platform 35 và Build Tools 35.0.0.
- Android Gradle Plugin 8.7.3, Gradle 8.9.
- Mở thư mục project gốc, Sync Gradle, chọn Build → Build Bundle(s) / APK(s) → Build APK(s).
- APK debug thường nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Thông số

- Tên: Ayane Dynamic Island
- Package: `com.ayane.dynamicisland`
- Version: `2.6.0` (versionCode 9)
- Min SDK 26 (Android 8.0), target SDK 35.
- Java / Android SDK; không có dependency Gradle runtime bên thứ ba.
