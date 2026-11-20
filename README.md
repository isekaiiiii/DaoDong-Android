# Ayane Ultimate — Dynamic Island cho Android

**Bản mã nguồn độc lập, không cần key.** Ayane được viết mới bằng Java/Android SDK, lấy nhóm chức năng hiển thị của NotifyIOS27 làm tài liệu tham khảo. Đây không phải bản chỉnh sửa, giải mã, mở khóa hoặc đóng gói lại APK tham khảo.

## Có gì trong bản Ultimate 2.0

### Đảo động và giao diện
- Cửa sổ nổi có chế độ thu gọn/mở rộng, chạm để chuyển trạng thái.
- Chỉnh độc lập chiều rộng/chiều cao thu gọn và chiều rộng/chiều cao mở rộng.
- Chỉnh vị trí ngang trái/phải và vị trí dọc âm/dương; mặc định khóa kéo khi đang ở ngoài màn hình cài đặt.
- Chỉnh bo góc, viền, hiệu ứng thông báo: mờ, trượt, nảy hoặc tắt; chỉnh tốc độ hiệu ứng.
- Tùy chọn blur nền trên Android 12+ và thiết bị hỗ trợ. Một số ROM/nhà sản xuất có thể tắt blur ở cấp hệ thống.
- Bố cục bảng cài đặt tiếng Việt, điều hướng nhanh theo nhóm tính năng, chạy thử giao diện.

### Thông báo
- Nhận thông báo đang hoạt động thông qua Android Notification Listener.
- Hiển thị tên ứng dụng nguồn, icon ứng dụng, tiêu đề/nội dung.
- Hiện tên người gửi và avatar nếu thông báo hội thoại cung cấp dữ liệu MessagingStyle/Person; nếu thiếu avatar người gửi, Ayane dùng icon app làm ảnh thay thế.
- Vuốt trái/phải hoặc dùng nút điều hướng để chuyển giữa tối đa 30 thông báo đang hoạt động được lưu tạm trong bộ nhớ.
- Trả lời nhanh qua RemoteInput nếu ứng dụng nguồn cung cấp RemoteInput tương thích.
- Bỏ qua thông báo hiện tại; nút “Xóa tất cả” hủy các thông báo mà Ayane đang theo dõi bằng API NotificationListenerService. Nó không xóa lịch sử thông báo hệ thống đã lưu hoặc thông báo không nằm trong danh sách Ayane theo dõi.
- Lọc app theo danh sách cho phép/chặn. Chặn app chỉ ẩn thông báo trong Ayane, không hủy thông báo gốc của ứng dụng.

### Nhạc và trạng thái thiết bị
- Đọc phiên media đang hoạt động: bài hát, nghệ sĩ, ảnh bìa nếu có.
- Điều khiển bài trước, phát/tạm dừng và bài tiếp qua MediaSession.
- Hiển thị phần trăm pin, trạng thái sạc, màu sạc tùy chỉnh và cảnh báo pin yếu có hiệu ứng nhịp nhẹ.
- Hiển thị tốc độ nhận dữ liệu gần đúng từ TrafficStats và bộ đếm ngược cục bộ.

### Tùy biến thương hiệu và màu
- Đổi tên thương hiệu hiện trên đảo động.
- Đổi nhãn app nguồn hiển thị trong đảo và chọn ảnh làm icon thông báo.
- Chọn vị trí icon app bên trái/bên phải.
- Tạo shortcut màn hình chính có tên và icon do người dùng chọn.
- Mã màu HEX riêng cho màu nhấn, nền đảo, chữ, nền bảng mở rộng, viền, chữ thông báo, cảnh báo pin, chữ phụ, nút trả lời, nút nhạc, trạng thái sạc và nền avatar/icon.
- Bộ màu mẫu tím, xanh, lục, hồng, vàng.

## Điều Ayane không làm / giới hạn kỹ thuật
- Android không cho ứng dụng tự thay tên/icon gốc của package trong mọi launcher. Ayane tạo shortcut riêng có tên và icon tùy chỉnh; launcher có thể cần xác nhận ghim shortcut.
- Trả lời nhanh chỉ hoạt động nếu thông báo gốc cung cấp RemoteInput. Với thông báo chỉ đọc, nút trả lời sẽ không xuất hiện.
- Avatar người gửi chỉ có khi ứng dụng gửi thông báo cung cấp avatar người gửi. Không thể tự suy ra hoặc tải ảnh đại diện từ tài khoản.
- Media controls chỉ hoạt động khi app nhạc phát một MediaSession mà Android cho phép truy cập qua Notification Access.
- Blur phụ thuộc Android 12+, GPU/ROM và chính sách của hệ thống.
- Dịch vụ nền dùng foreground service kiểu `specialUse`. Android/One UI có thể dừng tiến trình khi người dùng force-stop, tắt quyền, khởi động lại hoặc khi chính sách pin/hệ thống áp dụng; không thể cam kết 24/7 tuyệt đối.
- Ayane không có nhận diện game trong bản này; không hiển thị tùy chọn giả cho chức năng chưa làm.
- Không có Shizuku, Accessibility service, điều khiển cuộc gọi, SMS, danh bạ, vị trí, micro hoặc camera trong project hiện tại.

## Quyền Android cần cấp

1. **Hiển thị trên ứng dụng khác** (`SYSTEM_ALERT_WINDOW`): vẽ đảo động nổi.
2. **Truy cập thông báo** (`NotificationListenerService`): đọc nội dung/metadata thông báo và thực hiện thao tác trả lời/đóng do app nguồn cho phép. Đây là quyền nhạy cảm; chỉ bật khi bạn đồng ý.
3. **Thông báo Ayane** (`POST_NOTIFICATIONS` trên Android 13+): hiển thị foreground-service notification.
4. **Bỏ tối ưu pin**: tùy chọn do người dùng chủ động mở cài đặt; giúp giảm khả năng bị hệ thống dừng, không bảo đảm chạy mãi.
5. **Tự khởi động**: Ayane đăng ký nhận BOOT_COMPLETED và thử bật lại nếu người dùng bật lựa chọn này và Android cho phép.

Danh sách ứng dụng được truy vấn giới hạn ở những app có activity LAUNCHER qua `<queries>`. Không dùng `QUERY_ALL_PACKAGES`.

## Riêng tư và “không virus”
- Manifest **không khai báo quyền `INTERNET`** và đặt `usesCleartextTraffic=false`.
- Không có endpoint/backend/key server, quảng cáo, SDK bên thứ ba hoặc thư viện Gradle bên ngoài.
- Không có Accessibility service, Shizuku, quyền SMS, danh bạ, vị trí, micro hoặc camera.
- Nội dung thông báo đang hoạt động được giữ trong bộ nhớ trong tiến trình, không gửi lên máy chủ và không tạo lịch sử đám mây. SharedPreferences chỉ lưu cài đặt, tên, URI icon và bộ lọc app.
- Các điểm trên làm giảm bề mặt rủi ro nhưng không phải chứng nhận “sạch virus” độc lập. Sau khi tải APK, kiểm tra Play Protect hoặc công cụ bảo mật tin cậy trước khi cài; không cài APK từ nguồn không rõ.

## Build APK bằng GitHub Actions

1. Giải nén ZIP. Thư mục gốc project phải có `app/`, `.github/`, `build.gradle`, `settings.gradle` và `gradle.properties`.
2. Đưa **nội dung bên trong ZIP** lên thư mục gốc một repository GitHub mới/riêng. Không để nguyên một thư mục con lồng bên trong repository.
3. Push lên nhánh `main` hoặc mở tab Actions và chạy workflow **Build Ayane Ultimate APK**.
4. Khi run có dấu tick xanh, mở run đó và tải artifact **Ayane-Ultimate-APK-v2**.
5. Giải nén artifact để lấy `app-debug.apk`.

### Termux (đang dùng repository `isekaiiiii/DaoDong-Android`)

> Tạo bản clone làm việc mới để không đụng vào thư mục cũ. Đặt ZIP tải về trong Download.

```bash
cd ~/storage/downloads

git clone https://github.com/isekaiiiii/DaoDong-Android.git Ayane-Ultimate
cd Ayane-Ultimate

git config --global --add safe.directory "$PWD"

# Xóa project Android cũ, giữ nguyên thư mục .git và lịch sử Git
rm -rf app .github
rm -f build.gradle settings.gradle gradle.properties README.md UPDATE_TERMUX.txt .gitignore

# ZIP được đóng gói với các file project ở ngay cấp gốc
unzip -o ~/storage/downloads/Ayane_Ultimate_v2_Source.zip

git add -A
git commit -m "Build Ayane Ultimate 2.0"
git push origin main
```

Nếu repository có branch protection hoặc commit bị từ chối, đọc thông báo GitHub trước khi thử lại. GitHub HTTPS yêu cầu username và Personal Access Token; không gửi token cho người khác.

## Build bằng Android Studio

- Mở thư mục project gốc.
- Cài JDK 17 và Android SDK Platform 35 / Build Tools 35.0.0.
- Sync Gradle với Android Gradle Plugin 8.7.3, Gradle 8.9.
- Chọn **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
- APK debug thường nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Thông số project

- Java, không dùng thư viện phụ thuộc Gradle bên ngoài.
- Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17.
- minSdk 26 (Android 8.0), compileSdk/targetSdk 35.
- Application ID: `com.ayane.dynamicisland`.
- Version: `2.0.0`.
