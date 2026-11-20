# AYANE BLOSSOM — ULTIMATE 2.4.0

**Ayane** là ứng dụng Dynamic Island độc lập cho Android, viết bằng Java và Android SDK. Bản 2.4 làm mới nhận diện thương hiệu bằng ảnh biểu tượng do người dùng cung cấp, tinh chỉnh chuyển động và giao diện media, bổ sung trạng thái chờ gọn, chọn kiểu chữ và hiệu ứng nền album.

> Bản này tham khảo ý tưởng trải nghiệm từ dự án Droidtools (Droidbubble): đảo nổi, trạng thái rảnh, thông tin đa phương tiện, hiệu ứng chuyển động, vị trí/kích thước và thứ tự lớp. Ayane được giữ bằng mã Java riêng; không sao chép mã nguồn hay nhập thư viện của Droidtools. Không triển khai thanh công cụ cạnh Droidbar trong bản này.

## Điểm mới trong 2.4

- Biểu tượng ứng dụng, biểu tượng tròn và biểu tượng trong màn hình Ayane sử dụng hình minh họa do người dùng gửi.
- Nhận diện thương hiệu “Ayane Blossom”, bảng màu tím hoa anh đào / hồng dịu và nền tối AMOLED.
- Ba kiểu chữ cài sẵn của Android: bo tròn, hiện đại và gọn; không tải font từ mạng.
- Có thể thu gọn đảo thành viên thuốc nhỏ khi không có hoạt động; chỉnh riêng chiều rộng và chiều cao ở trạng thái chờ.
- Tinh chỉnh độ nảy khi chuyển cảnh và bật/tắt phản hồi rung nhẹ.
- Thẻ nhạc/video có nền ảnh bìa mờ, lớp phủ chuyển sắc, tùy chỉnh độ mờ và chọn thứ tự điều khiển / thanh tiến trình.
- Kiểu chữ được áp dụng thống nhất lên đảo chính, thẻ thông báo, thẻ cuộc gọi, hẹn giờ, sạc và trình phát.
- Bổ sung thông báo tạm thời khi đổi chế độ âm thanh: im lặng, rung hoặc chuông.

## Chức năng nền tảng

### Đảo động và giao diện
- Viên thuốc chính chỉnh được rộng/cao khi thu gọn và khi mở rộng, bo góc, viền và vị trí ngang/dọc.
- Tùy chỉnh màu HEX cho nền, viền, chữ, chữ phụ, thông báo, avatar, nút trả lời và điều khiển nhạc.
- Tùy chỉnh tên thương hiệu trên đảo, icon thông báo và shortcut có tên / icon riêng.
- Chạm để mở rộng/thu gọn; có thể kéo theo vùng điều khiển được thiết kế cho thao tác nổi.

### Nhạc và video
- Hiển thị ảnh bìa/thumbnail, tên nội dung, nghệ sĩ hoặc ứng dụng phát.
- Điều khiển bài trước, phát/tạm dừng, bài tiếp và tua khi MediaSession nguồn cung cấp dữ liệu tương ứng.
- Thẻ mở rộng có nền lấy từ ảnh bìa, làm mờ trên Android 12 trở lên, lớp phủ chuyển sắc.
- Tùy chọn thứ tự điều khiển trước hoặc thanh tiến trình trước.

### Thông báo và tin nhắn
- Hiển thị icon ứng dụng, tên ứng dụng, người gửi / avatar nếu thông báo nguồn có cung cấp và nội dung.
- Mở thông báo, bỏ qua và trả lời nhanh khi thông báo nguồn hỗ trợ RemoteInput.
- Từng thông báo được hiển thị bằng thẻ riêng; có chuyển trang và thanh quản lý thông báo.
- “Xóa tất cả” chỉ hủy các thông báo Ayane đang theo dõi; không xóa tin nhắn hoặc dữ liệu trong ứng dụng nguồn.
- Bộ lọc ứng dụng quyết định thẻ nào xuất hiện trong Ayane, không tắt thông báo gốc của Android.

### Hẹn giờ, báo thức, cuộc gọi và sạc
- Đọc dữ liệu hẹn giờ / báo thức khi ứng dụng Đồng hồ phát thông báo có thông tin chronometer hoặc hành động tương ứng.
- Cuộc gọi đến / đang gọi nhận diện tên, avatar và hành động nghe / từ chối / kết thúc nếu thông báo nguồn cung cấp PendingIntent hợp lệ.
- Thẻ sạc chỉ hiện khi đang sạc hoặc đầy; thẻ thu gọn không ghim phần trăm pin hay tốc độ tải xuống. Chi tiết điện áp, dòng điện, nhiệt độ, công suất và thời gian còn lại chỉ hiện khi mở rộng và thiết bị cung cấp dữ liệu.

## Quyền Android cần cấp

1. **Hiển thị trên ứng dụng khác** — quyền cửa sổ nổi (`SYSTEM_ALERT_WINDOW`), cho phép Ayane hiển thị và nhận thao tác chạm/kéo trên thẻ nổi. Đây không phải quyền Trợ năng.
2. **Truy cập thông báo** — để nhận nội dung thông báo, icon, avatar và các hành động do ứng dụng nguồn công bố.
3. **Thông báo Ayane** trên Android 13 trở lên — để hiển thị thông báo thường trực của dịch vụ nền.
4. **Không hạn chế tối ưu pin** là lựa chọn bổ sung để giảm việc One UI dừng dịch vụ nền; Android vẫn có thể dừng tiến trình khi force-stop, tiết kiệm pin hoặc khởi động lại.

## Rà soát an toàn và quyền riêng tư

- Manifest Ayane **không khai báo quyền INTERNET**; `usesCleartextTraffic=false`.
- Không có máy chủ key, quảng cáo, analytics hoặc thư viện runtime bên thứ ba.
- Không dùng Accessibility Service, Shizuku, cài APK, SMS, danh bạ, vị trí, microphone hoặc camera.
- Chỉ truy vấn ứng dụng có hoạt động khởi chạy thông qua `<queries>`; không yêu cầu `QUERY_ALL_PACKAGES`.
- Xử lý dữ liệu thông báo cục bộ trong tiến trình để hiển thị và thực thi hành động do ứng dụng nguồn cung cấp.
- Đây là rà soát cấu hình và mã nguồn tĩnh, **không phải chứng nhận không virus hoặc báo cáo antivirus độc lập**. Hãy chỉ cài APK từ workflow bạn tự kiểm soát và quét bằng Play Protect / công cụ bảo mật đáng tin cậy.

## Build bằng GitHub Actions

1. Giải nén ZIP. Thư mục gốc phải có `app/`, `.github/`, `build.gradle`, `settings.gradle`, `gradle.properties`.
2. Đưa toàn bộ nội dung ở thư mục gốc lên repository GitHub.
3. Push nhánh `main` hoặc mở Actions và chạy workflow **Tạo APK Ayane Blossom 2.4**.
4. Khi workflow có dấu tích xanh, tải artifact **Ayane-Blossom-APK-v2.4**.
5. Giải nén artifact để lấy `app-debug.apk`.

## Build bằng Android Studio

- JDK 17.
- Android SDK Platform 35 và Build Tools 35.0.0.
- Android Gradle Plugin 8.7.3, Gradle 8.9.
- Mở thư mục project gốc, Sync Gradle, chọn Build → Build Bundle(s) / APK(s) → Build APK(s).
- APK debug thường nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Thông số

- Tên: Ayane Blossom
- Package: `com.ayane.dynamicisland`
- Version: `2.4.0` (versionCode 7)
- Min SDK 26 (Android 8.0), target SDK 35.
- Java / Android SDK; không có dependency Gradle runtime bên thứ ba.
