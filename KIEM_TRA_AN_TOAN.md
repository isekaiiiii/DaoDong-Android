# Báo cáo rà soát tĩnh Ayane

Ngày rà soát: 20/11/2026  
Tên ứng dụng: Ayane  
Mã ứng dụng: `com.ayane.blossom`

## Đã kiểm tra

- Manifest và các quyền được khai báo.
- URL HTTP/HTTPS trong mã nguồn và trình cập nhật APK.
- Thành phần MediaProjection/ghi màn hình.
- Dịch vụ Trợ năng và cấu hình `canRetrieveWindowContent=false`.
- Shizuku và các lệnh shell cố định.
- Trình cập nhật APK, nguồn tải và giới hạn redirect.
- Chuỗi nhận diện tải mã động, WebView, DexClassLoader và thư viện quảng cáo/analytics phổ biến.
- Nội dung ZIP và các tệp nhị phân được đóng gói.

## Kết quả tĩnh

- Không tìm thấy tệp APK/DEX/EXE/SO được nhúng trong project; chỉ có Gradle Wrapper JAR chuẩn của dự án và các tài nguyên ảnh/phông chữ.
- Không thấy `DexClassLoader`, `InMemoryDexClassLoader`, `PathClassLoader`, `WebView` tải mã từ xa hoặc lệnh `Runtime.exec`/`ProcessBuilder` trong mã nguồn ứng dụng.
- Không thấy SDK quảng cáo/analytics phổ biến trong danh sách phụ thuộc đã kiểm tra.
- Các endpoint ứng dụng sử dụng: GitHub Releases của `isekaiiiii/DaoDong-Android`, Open-Meteo và trang tải Shizuku được mở khi người dùng yêu cầu.
- Trình cập nhật chỉ chấp nhận APK Ayane từ đường dẫn release HTTPS thuộc repository nêu trên và giới hạn host redirect.
- Đã bỏ quyền `RECORD_AUDIO` vì dịch vụ ghi màn hình không dùng microphone.
- Đã bỏ cơ chế tự gọi `su -c`; lệnh hệ thống nâng cao còn lại chạy qua Shizuku sau khi người dùng cấp quyền và là các câu lệnh `settings` cố định.
- Trợ năng chỉ nhận sự kiện thay đổi cửa sổ; `canRetrieveWindowContent=false`; không đọc văn bản/cây giao diện.
- Ghi màn hình sử dụng MediaProjection, cần người dùng xác nhận hộp thoại hệ thống.

## Quyền đáng chú ý (không phải quyền ẩn)

- `SYSTEM_ALERT_WINDOW`: cửa sổ đảo động.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: đọc thông báo khi người dùng bật quyền trong Cài đặt.
- `BIND_ACCESSIBILITY_SERVICE`: nhận sự kiện đổi cửa sổ khi người dùng tự bật dịch vụ; không đọc nội dung cửa sổ.
- `FOREGROUND_SERVICE*` và `POST_NOTIFICATIONS`: dịch vụ nền/ghi màn hình.
- `INTERNET`: thời tiết và cập nhật.
- `REQUEST_INSTALL_PACKAGES`: mở trình cài đặt Android sau khi người dùng chọn cài bản cập nhật.
- `PACKAGE_USAGE_STATS`: danh sách ứng dụng dùng nhiều (quyền đặc biệt, người dùng tự cấp).
- `WRITE_SECURE_SETTINGS`: một số tùy chỉnh thanh trạng thái qua Shizuku; không tự được cấp.
- `BLUETOOTH_CONNECT`: trạng thái thiết bị Bluetooth trên Android mới.

## Giới hạn kiểm tra

Không có ClamAV/VirusTotal hoặc công cụ antivirus độc lập trong môi trường này. Không thể kết luận APK sạch virus tuyệt đối chỉ từ rà soát mã nguồn. Android Gradle build không chạy được tại đây vì môi trường không phân giải được `services.gradle.org` để tải Gradle 9.1.0 và không có Android SDK (`ANDROID_HOME` chưa được cấu hình). Cần chạy GitHub Actions/Android Studio, cài thử trên thiết bị và quét APK tạo ra bằng công cụ bảo mật độc lập.
