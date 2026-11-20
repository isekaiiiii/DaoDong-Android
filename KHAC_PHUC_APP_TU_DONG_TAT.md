# Khắc phục Ayane tự thoát khi mở – bản 1.0.1

## Thay đổi
- Không khởi động cửa sổ nổi/dịch vụ nền ngay trong `MainActivity.onCreate()` hoặc `onResume()`.
- Không tự khởi động dịch vụ khi quay lại ứng dụng từ màn hình cấp quyền; chỉ cập nhật trạng thái quyền.
- Mặc định đảo động tắt sau khi cài mới; người dùng bật bằng công tắc trong ứng dụng sau khi cấp quyền.
- Bọc điểm khởi động dịch vụ và `onStartCommand()` để lỗi dịch vụ được ghi log và dừng an toàn thay vì làm đứt luồng mở giao diện.
- Chuyển `windowSoftInputMode` vào đúng khai báo Activity.
- Xóa đường dẫn Java Windows khỏi `gradle.properties`.

## Giới hạn kiểm thử
Đây là bản vá phòng ngừa cho luồng tự khởi động overlay. Chưa có logcat từ thiết bị gặp lỗi nên chưa thể kết luận đây là nguyên nhân duy nhất. Nếu vẫn văng, cần lấy log `FATAL EXCEPTION` từ AndroidRuntime để xác định chính xác.
