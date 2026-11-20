# Đảo động Ultra Android — phiên bản 3.1

Ứng dụng Android tạo lớp phủ nổi lấy cảm hứng từ Dynamic Island. Đây là ứng dụng độc lập cho Android, không phải tính năng Dynamic Island chính thức của Apple.

## Điểm mới trong v3.1

### Kích thước & vị trí
- Chiều rộng thu gọn: 140–400 dp.
- Chiều cao thu gọn: 38–180 dp.
- Chiều rộng khi mở rộng: 180–620 dp (tự giới hạn theo bề rộng màn hình).
- Chiều cao khi mở rộng: 130–900 dp.
- Vị trí ngang: -360 đến +360 dp.
- Vị trí dọc: -900 đến +900 dp; giá trị âm đưa đảo lên cao hơn mép trên mặc định.
- Độ bo góc, độ dày viền có thể tinh chỉnh.
- Khi ra ngoài ứng dụng, thao tác kéo bị tắt mặc định; chạm để mở/thu gọn. Có thể vuốt ngang để chuyển thông báo khi có nhiều thông báo.

### Thông báo nhiều ứng dụng
- Lưu danh sách tối đa 12 thông báo đang hoạt động có nội dung hiển thị.
- Tự luân phiên thông báo sau khoảng 4,2 giây; có thể vuốt trái/phải để chuyển thủ công.
- Hiệu ứng Trượt, Mờ dần hoặc Nảy.
- Hiển thị tên ứng dụng, tiêu đề, nội dung và tên người gửi khi nguồn thông báo có cung cấp.
- Hiển thị riêng icon ứng dụng và ảnh đại diện người gửi nếu thông báo MessagingStyle cung cấp Person/icon; nếu không có avatar sẽ dùng biểu tượng người gửi mặc định.
- Chọn icon bên trái hoặc bên phải; có thể dùng ảnh icon tùy chỉnh.
- Trả lời nhanh chỉ hoạt động nếu ứng dụng nguồn gửi RemoteInput. Bỏ qua sẽ hủy thông báo được chọn. Có nút xóa toàn bộ thông báo tin nhắn mà Đảo động xử lý; nút này không dùng cancelAllNotifications và không xóa tất cả thông báo hệ thống.

### Nhạc
- Hiển thị tên bài, nghệ sĩ và ảnh album nếu MediaSession của ứng dụng nhạc cung cấp metadata/artwork.
- Điều khiển bài trước, phát/tạm dừng, bài tiếp.
- Hiệu ứng waveform đơn giản và màu trình phát riêng.

### Cuộc gọi
- Nhận diện thông báo Android có `CATEGORY_CALL`/CallStyle.
- Hiện nút Nghe/Từ chối khi nguồn thông báo cung cấp PendingIntent tương ứng.
- Bộ đếm bắt đầu khi người dùng bấm Nghe; đây là thời gian kể từ thao tác bấm, không phải trạng thái cuộc gọi được xác minh bởi nhà mạng.
- Không phải mọi ứng dụng gọi hoặc giao diện cuộc gọi toàn màn hình đều cung cấp các hành động trong thông báo. Nếu không có action, nút sẽ không xuất hiện.

### Pin & màu sắc
- Giao diện cảnh báo pin yếu riêng, có thể chỉnh ngưỡng 5–35%.
- Khi pin yếu, đảo chuyển viền theo màu cảnh báo đã chọn và có hiệu ứng nhấn nhẹ.
- Màu riêng cho nền đảo, nền bảng mở rộng, chữ, viền, nền/chữ thông báo, trình phát nhạc, cảnh báo pin yếu và cuộc gọi.

### Chế độ chơi game
- Tùy chọn ẩn đảo khi ứng dụng game nằm ở foreground.
- Cần bật quyền Truy cập dữ liệu sử dụng ứng dụng trong Cài đặt Android.
- Android chỉ cho biết ứng dụng đang hoạt động; nhận diện dựa trên `ApplicationInfo.CATEGORY_GAME`. Một số game không khai báo đúng category có thể không được nhận diện.

### Chạy nền
- Foreground Service, thông báo thường trực, START_STICKY và thử khôi phục sau khởi động máy/cập nhật.
- Có thể mở cài đặt pin để chọn Không hạn chế.
- Không thể bảo đảm chạy tuyệt đối 24/7: Android/OEM có thể giới hạn nền, người dùng có thể buộc dừng ứng dụng hoặc thu hồi quyền.

## Quyền
- Hiển thị trên ứng dụng khác (`SYSTEM_ALERT_WINDOW`).
- Truy cập thông báo (bật trong Notification access).
- Hiển thị thông báo dịch vụ nền (`POST_NOTIFICATIONS` Android 13+).
- Danh sách ứng dụng (`QUERY_ALL_PACKAGES`) để lấy tên/icon ứng dụng thông báo. Đây là quyền nhạy cảm và bị Google Play hạn chế.
- Truy cập dữ liệu sử dụng ứng dụng (`PACKAGE_USAGE_STATS`) chỉ để nhận diện game ở foreground; phải bật thủ công trong Cài đặt truy cập đặc biệt.
- Foreground service và nhận broadcast khởi động máy.

**Riêng tư:** nội dung thông báo, tên người gửi, icon và danh sách ứng dụng được xử lý cục bộ. Dự án không gửi chúng đến máy chủ. Chỉ cấp các quyền bạn hiểu và đồng ý sử dụng.

## Cài đặt trên Android
1. Cài `app-debug.apk`.
2. Mở ứng dụng, cấp quyền Hiển thị trên ứng dụng khác.
3. Bật Truy cập thông báo cho Đảo động.
4. Cho phép thông báo dịch vụ nền nếu Android hỏi.
5. Muốn dùng chế độ game: mở quyền Truy cập dữ liệu sử dụng ứng dụng và bật cho Đảo động.
6. Mở phần Pin/Chạy nền và chọn Không hạn chế nếu thiết bị hỗ trợ.
7. Nhấn Bật Đảo động. Chỉnh kích thước, vị trí, màu và hiệu ứng trong ứng dụng.

## Build APK bằng GitHub Actions
1. Giải nén ZIP này vào thư mục project (giữ nguyên thư mục `.github`).
2. Đẩy các thay đổi lên nhánh `main`.
3. Mở Actions → workflow **Tạo APK Đảo động v3.1**.
4. Chờ job có dấu tích xanh.
5. Tải artifact `Dao-dong-Android-APK-v3.1` và giải nén để lấy `app-debug.apk`.

## Build bằng Android Studio
- Mở thư mục gốc project.
- Cài JDK 17 và Android SDK Platform 35.
- Sync Gradle.
- Chọn Build → Build Bundle(s) / APK(s) → Build APK(s).
- File APK debug nằm trong `app/build/outputs/apk/debug/app-debug.apk`.

## Thông số
- Java, Minimum SDK 26 (Android 8.0), Compile/Target SDK 35.
- Android Gradle Plugin 8.7.3, Gradle 8.9.
- Package: `com.example.dynamicisland`.
- Version: 3.1 (versionCode 5).


## Ghi chú cho v3.1

- Tăng giới hạn vị trí dọc lên trên từ -240 dp thành -900 dp; khi kéo trực tiếp, giới hạn trên cũng được cập nhật tương ứng.
- Tăng phạm vi chiều rộng bảng mở rộng lên 620 dp và chiều cao lên 900 dp; cửa sổ vẫn bị giới hạn theo chiều rộng màn hình.
- Bảng thông báo hiển thị icon ứng dụng, avatar người gửi khi được cung cấp, tên người gửi và nội dung.
- Nút “Xóa tất cả tin nhắn / thông báo” dò các thông báo đang hoạt động mà Đảo động có thể xử lý rồi chỉ hủy các thông báo đó; không gọi API xóa tất cả thông báo hệ thống.
- Trợ năng không được khai báo vì ứng dụng không cần đọc nội dung màn hình hay tự thao tác thay người dùng.
