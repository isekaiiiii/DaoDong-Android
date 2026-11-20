# Đối chiếu Droidtools cho Ayane 2.6

Nguồn công khai: https://github.com/Velocity081/droidtools

Ayane tham khảo tính năng và luồng thiết kế được công khai trong README Droidtools/Droidbubble: media now-playing (ảnh bìa, tiêu đề, thanh tua, nút điều khiển, nền album mờ), thông báo heads-up và trả lời nhanh, sự kiện hệ thống, trạng thái rảnh, tùy chỉnh vị trí/kích thước/hiệu ứng/phông chữ/thứ tự lớp; cùng Droidbar dạng thanh công cụ cạnh với lối tắt hệ thống và ứng dụng ghim.

Phần Trợ năng được viết độc lập bằng Java. Cấu hình của Ayane:
- Chỉ nhận `TYPE_WINDOW_STATE_CHANGED`.
- `canRetrieveWindowContent=false`.
- `canPerformGestures=false`.
- `canTakeScreenshot=true` chỉ để hỗ trợ nút chụp màn hình do người dùng chủ động nhấn trong Droidbar; không chụp tự động.

Khi kết nối, Ayane:
- Lưu trạng thái khóa thiết bị.
- Yêu cầu làm mới MediaSession khi ứng dụng/cửa sổ đổi.
- Chuyển đảo chính, thẻ nổi và thanh công cụ sang `TYPE_ACCESSIBILITY_OVERLAY` với `FLAG_LAYOUT_NO_LIMITS` và chế độ camera cutout.
- Khi dịch vụ ngắt kết nối, tháo cửa sổ cũ và dựng lại bằng `TYPE_APPLICATION_OVERLAY`.

Trợ năng không thay thế `SYSTEM_ALERT_WINDOW`: quyền overlay vẫn là quyền cơ bản cho cửa sổ nổi và thao tác kéo; Trợ năng mở rộng kiểu cửa sổ và khả năng nhận sự kiện hệ thống. Ayane không đọc cây giao diện, văn bản hoặc trường nhập của ứng dụng khác, không chép mã Kotlin/Compose hoặc thư viện Shizuku của Droidtools.
