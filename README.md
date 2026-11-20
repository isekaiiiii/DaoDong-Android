# AYANE DYNAMIC ISLAND — ULTIMATE 2.7.0

Ayane là ứng dụng Dynamic Island độc lập cho Android, viết bằng Java và Android SDK. Bản 2.7 sửa luồng thông báo theo kiểu heads-up của Droidbubble: đảo động chỉ bật ra khi có thông báo mới, tự thu gọn sau thời gian xem trước và không còn ghim danh sách thẻ tin nhắn trên toàn màn hình.

## Điểm mới trong 2.7

- Thông báo mới xuất hiện ngay trong đảo động quanh vùng camera; không tạo bảng thông báo riêng cố định trên màn hình.
- Có thể tự mở rộng để xem người gửi, ứng dụng, nội dung và thao tác trả lời nhanh/bỏ qua.
- Mặc định tự thu gọn sau 5 giây; trong ứng dụng có thể chỉnh 3–12 giây.
- Thông báo cũ vẫn nằm trong khay thông báo Android. Ayane không tự hủy thông báo khi hết thời gian xem trước.
- Thông báo mới hơn thay thế phần xem trước cũ để tránh chồng lấp.
- Khi không có hoạt động, đảo trở về trạng thái nhỏ gọn ở vùng camera.
- Cuộc gọi và hẹn giờ/báo thức vẫn dùng thẻ hoạt động riêng; nhạc/video vẫn có trình phát riêng với ảnh bìa, nền mờ, thanh tua và nút điều khiển.
- Sửa lỗi biên dịch của AccessibilityService: đổi helper tĩnh thành `invokeGlobalAction()` và sửa cú pháp thao tác chia đôi màn hình.

## Tính năng giữ lại

- Nhận thông báo bằng `NotificationListenerService`, hiển thị tên ứng dụng, tên người gửi/ảnh đại diện nếu ứng dụng nguồn cung cấp.
- Trả lời nhanh qua `RemoteInput` khi thông báo nguồn hỗ trợ.
- Mở hoặc bỏ qua thông báo; xóa thông báo vẫn dùng thao tác Android do người dùng chủ động.
- MediaSession: album art, tên nội dung, nghệ sĩ, trạng thái phát, tua, bài trước/bài tiếp khi phiên phát hỗ trợ.
- Thẻ riêng cho cuộc gọi và hẹn giờ/báo thức khi thông báo nguồn có hành động thích hợp.
- Trạng thái sạc, công suất/dòng điện/điện áp/nhiệt độ nếu thiết bị cung cấp; sự kiện chế độ chuông/rung.
- Droidbar: đèn pin, bảng Internet của Android, chế độ chuông/rung, chụp màn hình sau thao tác chủ động, ứng dụng ghim.
- Tùy chỉnh kích thước, vị trí, màu HEX, phông chữ và hiệu ứng.

## Cách thông báo hoạt động

1. Khi `NotificationListenerService` nhận thông báo mới hợp lệ, Ayane đánh dấu thông báo đó là heads-up trong thời lượng đã đặt.
2. `IslandService` lấy thông báo heads-up để bật đảo và có thể tự mở rộng bảng nội dung.
3. Khi hết thời gian, thông báo heads-up hết hiệu lực và đảo thu về trạng thái chờ. Bản thông báo gốc vẫn ở Notification Drawer.
4. Nếu người dùng chạm mở rộng hoặc bắt đầu trả lời, Ayane hủy bộ đếm tự thu gọn để không đóng giao diện giữa lúc tương tác.
5. Cuộc gọi/báo thức được loại khỏi luồng tin nhắn thường và tiếp tục đi qua thẻ hoạt động riêng.

## Quyền Android

- **Hiển thị trên ứng dụng khác**: tạo cửa sổ Dynamic Island nổi.
- **Truy cập thông báo**: nhận nội dung/biểu tượng và hành động trả lời do ứng dụng nguồn cung cấp.
- **Trợ năng Ayane (tùy chọn)**: nhận sự kiện đổi cửa sổ và dùng loại cửa sổ `TYPE_ACCESSIBILITY_OVERLAY` cho chế độ mở rộng; cấu hình không đọc cây giao diện hoặc văn bản của ứng dụng khác. Chụp màn hình chỉ khi người dùng chủ động bấm nút.
- **Thông báo dịch vụ nền**: hiển thị thông báo thường trực của foreground service.
- **Camera**: chỉ cần nếu người dùng bật đèn pin trong Droidbar.

Không có quyền Internet được khai báo và không có máy chủ xác thực key. Việc không có quyền Internet giảm bề mặt truy cập mạng nhưng không tự chứng minh APK sạch tuyệt đối; cần quét file APK bằng công cụ bảo mật độc lập trước khi cài nếu cần xác minh.

## Build APK bằng GitHub Actions

1. Đưa toàn bộ nội dung project vào repository.
2. Push nhánh `main` hoặc mở Actions và chạy workflow **Tạo APK Ayane Dynamic Island 2.7 · Heads-up**.
3. Khi workflow có dấu tích xanh, tải artifact **Ayane-Dynamic-Island-APK-v2.7**.
4. Giải nén artifact và cài `app-debug.apk`.

## Thông tin kỹ thuật

- Java / Android SDK
- `compileSdk 35`, `targetSdk 35`, `minSdk 26`
- Version: `2.7.0` (`versionCode 10`)
- Application ID: `com.ayane.dynamicisland`
- Không thêm thư viện runtime bên thứ ba.

## Ghi chú tương thích

- Trả lời nhanh chỉ hoạt động khi thông báo gốc có `RemoteInput`.
- Tên người gửi/avatar phụ thuộc dữ liệu mà ứng dụng gửi thông báo cung cấp.
- Điều khiển nhạc/tua phụ thuộc MediaSession và hành động mà trình phát cung cấp.
- Một số thông số sạc phụ thuộc phần cứng/firmware của thiết bị.
- Android/OEM có thể giới hạn foreground service hoặc cửa sổ nổi theo chính sách pin và quyền riêng.

## Tham khảo hành vi

Xem `DROIDTOOLS_REFERENCE.md` để biết các tính năng công khai của Droidtools được dùng làm tham chiếu. Ayane được triển khai độc lập bằng Java/Android SDK; không sao chép mã nguồn Kotlin/Compose của dự án tham khảo.
