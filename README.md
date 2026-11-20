# Đảo động — Dynamic Island cho Android

Ứng dụng tạo một vùng nổi kiểu Dynamic Island trên Android. Giao diện và phần cài đặt đã được Việt hóa; thiết kế tối màu, bo góc và có bốn màu điểm nhấn.

## Tính năng trong bản 1.1

- Bật/tắt đảo động bằng nút trong ứng dụng.
- Kéo đảo động trực tiếp theo chiều ngang và dọc; vị trí được lưu lại.
- Chỉnh chiều rộng, chiều cao, vị trí ngang, vị trí dọc và độ bo góc.
- Căn đảo động về vị trí mặc định.
- Chọn màu tím, xanh dương, xanh lá hoặc hồng.
- Chạm để mở rộng/thu gọn bảng thông tin.
- Hiển thị pin và trạng thái sạc.
- Hiển thị tốc độ tải xuống gần đúng từ số liệu lưu lượng mạng Android.
- Hiển thị tiêu đề và nội dung thông báo mới sau khi người dùng cấp quyền truy cập thông báo.
- Nhận thông tin phiên phát nhạc Android; hỗ trợ bài trước, phát/tạm dừng và bài tiếp khi ứng dụng phát nhạc cho phép.
- Bộ đếm ngược tối đa 1.440 phút, cập nhật mỗi giây trên đảo động.
- Nút chạy thử hoạt ảnh 5 giây.
- Dịch vụ nền có thông báo thường trực để Android hiển thị trạng thái ứng dụng đang chạy.

## Yêu cầu

- Android 8.0 trở lên (API 26+).
- Quyền hiển thị trên ứng dụng khác để tạo cửa sổ nổi.
- Quyền truy cập thông báo là tùy chọn, cần bật để đọc thông báo và điều khiển phiên nhạc.

## Cài đặt trên điện thoại

1. Cài APK đã build.
2. Mở ứng dụng **Đảo động**.
3. Nhấn **Cấp quyền** ở mục hiển thị trên ứng dụng khác và bật quyền cho Đảo động.
4. Muốn hiển thị thông báo và điều khiển nhạc, nhấn **Cấp quyền** ở mục truy cập thông báo rồi bật Đảo động trong danh sách.
5. Trở lại ứng dụng, nhấn **Bật Đảo động**.
6. Dùng thanh trượt để chỉnh vị trí/kích thước hoặc kéo trực tiếp vùng nổi.

Trên một số máy Samsung/Xiaomi/Oppo, hãy đặt mức sử dụng pin của ứng dụng thành **Không hạn chế** nếu hệ thống tự dừng dịch vụ nền.

## Tạo APK bằng GitHub Actions (có thể thao tác bằng trình duyệt điện thoại)

1. Giải nén toàn bộ thư mục dự án và tải các tệp bên trong lên một repository GitHub mới. Giữ nguyên thư mục `.github/workflows`.
2. Mở repository, chọn tab **Actions**.
3. Chọn workflow **Build Android APK** rồi nhấn **Run workflow** (hoặc đẩy mã lên nhánh `main` để workflow tự chạy).
4. Khi workflow hoàn tất, mở lần chạy mới nhất.
5. Ở phần **Artifacts**, tải `Dao-dong-Android-APK` và giải nén để lấy `app-debug.apk`.

APK debug là bản cài thử. Muốn phát hành rộng rãi cần tạo khóa ký riêng và build bản release đã ký.

## Build bằng Android Studio

- Mở thư mục `DynamicIslandAndroid` bằng Android Studio.
- Cài Android SDK Platform 35 nếu Android Studio yêu cầu.
- Chọn **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
- APK debug nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Giới hạn kỹ thuật

- Đây là ứng dụng Android độc lập mô phỏng kiểu hiển thị Dynamic Island, không phải tính năng Dynamic Island gốc của Apple.
- Nội dung thông báo phụ thuộc thông tin mà ứng dụng nguồn cung cấp. Cuộc gọi, bản đồ, giao hàng hoặc hoạt động trực tiếp chỉ hiện được khi hệ thống/ứng dụng liên quan gửi thông báo hoặc phiên hoạt động tương thích.
- Không thể đọc dữ liệu riêng bên trong mọi ứng dụng; không thể tự lấy tỷ số, thông tin giao hàng hoặc chỉ đường nếu ứng dụng nguồn không cung cấp.
- Android không cho ứng dụng thông thường biết và điều khiển quy trình Face ID của hệ thống; hoạt ảnh Face ID thật không được hỗ trợ.
- Tốc độ mạng là ước tính từ bộ đếm lưu lượng tải xuống, không phải phép đo tốc độ Internet chuẩn.
- Việc chạy nền và mức tiêu thụ pin phụ thuộc phiên bản Android và chính sách quản lý pin của nhà sản xuất.
- Quyền truy cập thông báo có thể để ứng dụng đọc nội dung thông báo. Hãy chỉ bật nếu bạn đồng ý. Bản này không có mã gửi thông báo lên máy chủ.

## Cấu hình dự án

- Ngôn ngữ: Java
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9 (trong workflow)
- Compile SDK / Target SDK: 35
- Minimum SDK: 26
- Phiên bản ứng dụng: 1.1
