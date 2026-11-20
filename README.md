# Ayane — Đảo động cho Android

**Ayane** là bản đổi thương hiệu và Việt hóa từ mã nguồn Droidtools do người dùng cung cấp. Ứng dụng dùng biểu tượng anime hoa anh đào do người dùng gửi, tên hiển thị `Ayane` và mã ứng dụng riêng `com.ayane.blossom` để có thể cài cạnh các bản Ayane cũ.

## Tính năng chính

- **Đảo động:** hiển thị gần vùng camera, thu gọn/mở rộng theo hoạt động, có thể tùy chỉnh kích thước, vị trí và hiệu ứng.
- **Nhạc và video:** ảnh bìa, tên nội dung, thanh tiến trình và điều khiển MediaSession.
- **Thông báo:** xem trước, mở rộng, mở ứng dụng và trả lời nhanh khi ứng dụng gửi thông báo hỗ trợ thao tác trả lời.
- **Cuộc gọi, báo thức và hoạt động hệ thống:** hiển thị các trạng thái do Android và ứng dụng nguồn cung cấp.
- **Thanh công cụ cạnh màn hình:** ứng dụng ghim, thao tác nhanh và phím tắt.
- **Tác vụ tùy chỉnh:** tạo lối tắt để mở ứng dụng, điều khiển phương tiện và thực hiện các thao tác được hỗ trợ.
- **Giao diện:** Material 3, phông chữ, kích thước, vị trí, hiệu ứng và thứ tự lớp có thể tùy chỉnh.
- **Ghi màn hình:** chỉ bắt đầu sau khi người dùng xác nhận hộp thoại MediaProjection của Android.

## Yêu cầu

- Android 10 trở lên (minSdk 29; một số khả năng phụ thuộc phiên bản Android và giao diện của nhà sản xuất).
- **Hiển thị trên ứng dụng khác:** để hiển thị cửa sổ đảo động.
- **Quyền truy cập thông báo:** để đọc bản xem trước thông báo và trạng thái MediaSession.
- **Quyền gửi thông báo:** để hiển thị thông báo dịch vụ nền trên Android 13 trở lên.
- **Trợ năng (tùy chọn):** dùng để nhận biết thay đổi cửa sổ ứng dụng và cập nhật đảo động; cấu hình `canRetrieveWindowContent=false`, không đọc văn bản/cây giao diện.
- **Shizuku (tùy chọn):** chỉ cho một số thao tác hệ thống nâng cao; người dùng phải tự cài và cấp quyền trong Shizuku.
- **Bỏ qua tối ưu pin (tùy chọn):** giúp giảm khả năng Android dừng dịch vụ nền; không đảm bảo chạy liên tục tuyệt đối.

## Quyền riêng tư và kết nối mạng

- Không khai báo quyền ghi âm microphone. Ghi màn hình dùng MediaProjection và cần người dùng xác nhận bằng hộp thoại hệ thống.
- Không thấy SDK quảng cáo hoặc analytics phổ biến trong danh sách phụ thuộc đã kiểm tra.
- Kết nối mạng được dùng cho **Open-Meteo** (tra cứu địa danh/thời tiết khi bật tiện ích) và **GitHub Releases** (kiểm tra/tải bản cập nhật từ `isekaiiiii/DaoDong-Android`).
- Trình cập nhật chỉ nhận APK tên `Ayane.apk` hoặc `Ayane-*.apk` từ đường dẫn phát hành của repository Ayane; chuyển hướng tải xuống chỉ được chấp nhận qua HTTPS trên các máy chủ GitHub Assets đã chỉ định. Android vẫn yêu cầu người dùng xác nhận cài đặt.
- Không thấy luồng gửi SMS, danh bạ, thông báo hoặc nội dung màn hình đến máy chủ trong mã nguồn được rà soát.
- Một số thao tác Shizuku chạy các lệnh `settings` cố định do người dùng chủ động bật; không còn cơ chế tự gọi `su -c`.

## Xây dựng APK

### Android Studio

1. Giải nén ZIP.
2. Android Studio → **Open** → chọn thư mục có `settings.gradle.kts`.
3. Chờ Gradle Sync. Cài Android SDK Platform 36 và JDK 21 nếu Android Studio yêu cầu.
4. Chọn **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

APK debug đầu ra: `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions

Workflow `.github/workflows/build-ayane.yml` tự chạy khi push lên nhánh `main` hoặc khi chọn chạy thủ công. Khi thành công, tải Artifact **Ayane-APK**, bên trong có tệp `Ayane.apk`.

## Kiểm tra an toàn

Đã rà soát tĩnh Manifest, quyền, URL mạng, lệnh Shizuku, dịch vụ Trợ năng, MediaProjection và luồng cập nhật. Không có công cụ antivirus như ClamAV/VirusTotal trong môi trường tạo mã nguồn; do đó **không thể chứng nhận tuyệt đối không có virus**. Bản này cũng chưa được build/chạy trên thiết bị trong môi trường hiện tại. Hãy build bằng GitHub Actions hoặc Android Studio, sau đó quét APK thành phẩm bằng công cụ bảo mật độc lập trước khi cài.

## Nguồn gốc và giấy phép

Mã nguồn nền được lấy từ ZIP `droidtools-main.zip` người dùng cung cấp, dự án gốc: https://github.com/Velocity081/droidtools. README dự án gốc tuyên bố MIT, nhưng gói ZIP không chứa tệp `LICENSE` và đường dẫn LICENSE ở repository trả về 404 khi rà soát. Vì vậy không tự gắn giấy phép MIT cho bản Ayane; hãy xác minh với tác giả trước khi công khai phân phối hoặc thương mại hóa. Giữ ghi công nguồn gốc khi sử dụng.


## Khắc phục lỗi ứng dụng tự thoát khi mở (1.0.1)

Bản 1.0.1 không tự khởi động cửa sổ nổi ngay trong `MainActivity.onCreate()` hoặc khi Activity quay lại `onResume()`. Người dùng mở Ayane trước, cấp quyền cần thiết, sau đó bật công tắc **Bật đảo động** trong giao diện. Mặc định cài mới để đảo động tắt cho đến khi người dùng chủ động bật.

Việc này cô lập lỗi khởi chạy giao diện chính khỏi dịch vụ overlay/foreground service. Dịch vụ được bọc xử lý lỗi khởi tạo và `onStartCommand`; nếu không thể khởi động, Ayane ghi log `AyaneOverlay` và dừng dịch vụ thay vì chủ động khởi chạy lặp lại khi mở ứng dụng.

Nếu ứng dụng vẫn tự thoát, hãy lấy logcat có dòng `FATAL EXCEPTION` và `Process: com.ayane.blossom`; bản vá an toàn không thay thế logcat trên thiết bị thật.
