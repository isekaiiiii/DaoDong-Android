# Đối chiếu hành vi Droidtools cho Ayane 2.7

Nguồn được tham khảo: https://github.com/Velocity081/droidtools

README công khai của Droidtools mô tả Droidbubble như một Dynamic Island nằm yên quanh vùng camera khi không có hoạt động, sau đó bật ra khi có sự kiện. Các điểm được README nêu gồm:

- Nội dung đang phát: ảnh bìa, tên bản nhạc, thanh tua và điều khiển phát; nền mờ lấy màu từ ảnh bìa.
- Sự kiện hệ thống: công suất sạc, thay đổi chế độ chuông và các trạng thái khác.
- Thông báo: heads-up alert có thể mở rộng và trả lời mà không cần mở ứng dụng nguồn.
- Trạng thái rảnh: đảo thu về vùng camera và không chiếm màn hình.
- Tùy chỉnh: vị trí, kích thước, độ nảy, phông chữ và thứ tự lớp giao diện.
- Droidbar: thanh cạnh màn hình với đèn pin, chuông/rung, Wi-Fi, dữ liệu di động, chụp màn hình và ứng dụng ghim.

Trong Ayane 2.7, phần được sửa trực tiếp theo hành vi heads-up là: **thông báo mới chỉ hiển thị tạm thời trong chính đảo động, có thể tự mở rộng, sau đó tự thu gọn**. Không còn tạo danh sách thẻ tin nhắn ghim liên tục trên màn hình. Thông báo gốc vẫn ở khay thông báo Android. Các sự kiện cuộc gọi/báo thức và trình phát media vẫn có luồng riêng.

Ayane không chép mã Kotlin/Jetpack Compose của Droidtools. Ayane giữ cấu trúc Java/Android SDK hiện có và chỉ tham khảo hành vi/thiết kế được mô tả công khai.

Các giới hạn Android vẫn áp dụng: trả lời nhanh cần RemoteInput; điều khiển nhạc cần MediaSession; nút thao tác cuộc gọi/báo thức cần PendingIntent tương ứng từ ứng dụng nguồn. Quyền Trợ năng không tự cấp quyền thay đổi mọi cài đặt hệ thống.
